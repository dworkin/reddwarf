/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;

public class JRMSSharedData implements SharedData, JRMSSharedObjectBase
{
    private JRMSSharedDataManager mgr;
    private JRMSSharedMutex mutex;
    private Serializable value;
    private String name;
    private boolean initialized = false;

    public JRMSSharedData(JRMSSharedDataManager mgr, String varname) {
        this.mgr = mgr;
        this.name = varname;
        mutex = (JRMSSharedMutex) mgr.getSharedMutex(this.name + "_MUTEX");
        initialized = false;
    }

    public synchronized void initialize() {
        // System.out.println("JRMSSharedData.initialize");
        // create packet data
        try {
            long start = System.currentTimeMillis();
            // get starting value
            mgr.requestData(name);
            while (!initialized) {
                Set roster = mgr.getRoster();
                if (roster.size() == 0) { // we're first
                    value = null;
                    initialized = true;
                } else {
                    try {
                        wait(mgr.getRosterTimeout());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if ((System.currentTimeMillis() - start) >= mgr.getRosterTimeout()) {
                        // noone else has one of these
                        value = null;
                        initialized = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        mutex.lock();
    }

    public Serializable getValue() {
        return value;
    }

    public void release() {
        mutex.release();
    }

    public void setValue(Serializable newvalue) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(newvalue);
            oos.flush();
            byte[] buff = baos.toByteArray();
            mgr.sendData(name, buff);
            ByteArrayInputStream bais = new ByteArrayInputStream(buff);
            ObjectInputStream ois = new ObjectInputStream(bais);
            value = (Serializable) ois.readObject();
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void sendCurrentValue() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            oos.flush();
            byte[] buff = baos.toByteArray();
            oos.close();
            mgr.sendData(name, buff);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dataRequest(SGSUUID uuid) {
        // System.out.println("Data requested, sending data.");
        sendCurrentValue();
    }

    public synchronized void dataAssertion(SGSUUID uuid, byte[] data) {
        // System.out.println("Recieved data assertion");
        if (mutex.getState() == JRMSSharedMutex.STATE_LOCKED) {
            // we have our own idea of the value
            return;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            value = (Serializable) ois.readObject();
            // System.out.println("asserted value = "+value);
            ois.close();
            if (!initialized) {
                initialized = true;
                notifyAll();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void lockAck(SGSUUID uuid) {
        System.err.println("ERROR:  Data recieved a mutex ack!");
    }

    public void lockNak(SGSUUID uuid) {
        System.err.println("ERROR:  Data recieved a mutex nak!");
    }

    public void lockReq(SGSUUID uuid) {
        System.err.println("ERROR:  Data recieved a mutex req!");
    }

    public void lockRelease(SGSUUID uuid) {
        System.err.println("ERROR:  Data recieved a mutex release!");
    }
}
