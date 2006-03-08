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

package com.sun.gi.objectstore.tso;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceTransactionImpl;
import com.sun.gi.utils.SGSUUID;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSOObjectStore implements ObjectStore {

    DataSpace dataSpace;
    SecureRandom random;
    Map<SGSUUID, TSOTransaction> localTransactionIDMap = new HashMap<SGSUUID, TSOTransaction>();

    public TSOObjectStore(DataSpace space) throws InstantiationException {
        dataSpace = space;

        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new InstantiationException();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.ObjectStore#newTransaction(long,
     * java.lang.ClassLoader)
     */
    public Transaction newTransaction(ClassLoader loader) {
        if (loader == null) {
            loader = this.getClass().getClassLoader();
        }
        TSOTransaction trans = new TSOTransaction(this, loader,
                System.currentTimeMillis(), random.nextLong(), dataSpace);
        return trans;
    }

    DataSpaceTransaction getDataSpaceTransaction(ClassLoader loader) {
        return new DataSpaceTransactionImpl(loader, dataSpace);
    }

    /**
     * @param dsTrans
     */
    void returnDataSpaceTransaction(DataSpaceTransaction dsTrans) {
        ((DataSpaceTransactionImpl) dsTrans).close();
    }

    /**
     * @param transaction
     * @param uuid2
     */
    public void requestCompletionSignal(TSOTransaction transaction,
            SGSUUID uuid2)
    {
        // TODO Auto-generated method stub
    }

    /**
     * @param uuid
     */
    public void requestTimestampInterrupt(SGSUUID uuid) {
        TSOTransaction trans = localTransactionIDMap.get(uuid);
        if (trans != null) {
            trans.timeStampInterrupt();
        }
    }

    public void clear() {
        dataSpace.clear();
    }

    /**
     * @param listeners
     */
    public void notifyAvailabilityListeners(List<SGSUUID> listeners) {
        for (SGSUUID uuid : listeners) {
            TSOTransaction trans = localTransactionIDMap.get(uuid);
            if (trans != null) {
                // System.out.println("Notfying "+uuid);
                synchronized (trans) {
                    trans.notifyAll();
                }
            }
        }
    }

    public long getAppID() {
        return dataSpace.getAppID();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.objectstore.ObjectStore#close()
     */
    public void close() {
        dataSpace.close();
    }

    public void registerActiveTransaction(TSOTransaction trans) {
        localTransactionIDMap.put(trans.getUUID(), trans);
    }

    public void deregisterActiveTransaction(TSOTransaction trans) {
        localTransactionIDMap.remove(trans.getUUID());
    }

    public void requestTimeoutInterrupt(SGSUUID uuid) {
        // TODO Auto-generated method stub
    }
}
