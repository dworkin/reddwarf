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

import java.util.Set;
import java.util.TreeSet;

public class JRMSSharedMutex implements SharedMutex, JRMSSharedObjectBase {

    static final int STATE_UNLOCKED = 0;
    static final int STATE_LOCKING = 1;
    static final int STATE_LOCKED = 2;
    static final int STATE_NAKED = 3;

    private JRMSSharedDataManager mgr;
    private String name;
    private int state = STATE_UNLOCKED;
    private SGSUUID currentOwner = null;
    Set<SGSUUID> acksReceived = new TreeSet<SGSUUID>();

    private static final boolean DEBUG = false;

    public JRMSSharedMutex(JRMSSharedDataManager mgr, String name) {
        this.mgr = mgr;
        this.name = name;
    }

    public int getState() {
        return state;
    }

    public synchronized void lock() {
        if (state == STATE_LOCKED) { // already locked, return
            return;
        } else if (state == STATE_UNLOCKED) {
            state = STATE_LOCKING;
            acksReceived.clear();
            mgr.sendLockReq(name);
            while ((state == STATE_LOCKING) || (state == STATE_NAKED)) {
                if (testAcks()) {
                    break;
                }
                try {
                    wait(mgr.getRosterTimeout());
                    mgr.sendLockReq(name);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // System.out.println("Roster timeout, roster:
            // "+mgr.getRoster()+
            // " received: "+acksReceived);
            state = STATE_LOCKED;
        }
    }

    private synchronized boolean testAcks() {
        Set<SGSUUID> roster = mgr.getRoster();
        if (state == STATE_NAKED) {
            if (!roster.contains(currentOwner)) { // owner died
                doRelease(currentOwner);
            }
        }
        // System.out.println("Roster size = "+roster.size());
        return acksReceived.containsAll(roster);
    }

    private synchronized void addAck(SGSUUID ackingUUID) {
        if (DEBUG) {
            System.out.println("Adding ACK from " + ackingUUID);
        }
        acksReceived.add(ackingUUID);
        if (testAcks()) {
            notifyAll();
        }
    }

    private synchronized void doNak(SGSUUID nakingUID) {
        if (DEBUG) {
            System.out.println("doing Nak from " + nakingUID);
        }
        currentOwner = nakingUID;
        if (state == STATE_LOCKING) {
            state = STATE_NAKED;
        }
    }

    private synchronized void doRelease(SGSUUID nakingUID) {
        if (DEBUG) {
            System.out.println("doing a release from " + nakingUID);
        }
        if ((state == STATE_NAKED) || (state == STATE_LOCKING)) {
            state = STATE_LOCKING;
            acksReceived.clear();
            mgr.sendLockReq(name);
        }
    }

    public synchronized void release() {
        state = STATE_UNLOCKED;
        mgr.sendLockRelease(name);
    }

    public void dataRequest(SGSUUID uuid) {
        System.err.println("ERROR:  Mutex recieved a data request!");
    }

    public void dataAssertion(SGSUUID uuid, byte[] data) {
        System.err.println("ERROR:  Mutex recieved a data assertion");
    }

    public void lockAck(SGSUUID uuid) {
        addAck(uuid);
    }

    public void lockNak(SGSUUID uuid) {
        doNak(uuid);
    }

    public synchronized void lockReq(SGSUUID uuid) {
        if (DEBUG) {
            System.out.println("Lock requested by " + uuid + " our state == "
                    + state + " our UUID = " + mgr.getUUID());
        }
        if (state == STATE_LOCKED) {
            mgr.sendLockNak(name);
        } else if ((state == STATE_UNLOCKED) || (state == STATE_NAKED)) {
            mgr.sendLockAck(name);
        } else if (state == STATE_LOCKING) {
            if (mgr.getUUID().compareTo(uuid) == -1) {
                mgr.sendLockNak(name);
            } else {
                mgr.sendLockAck(name);
                doNak(uuid);
            }
        }
    }

    public void lockRelease(SGSUUID uuid) {
        doRelease(uuid);
    }
}
