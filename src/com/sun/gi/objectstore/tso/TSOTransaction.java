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

import java.io.Serializable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceTransactionImpl;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSOTransaction implements Transaction {

    private static Logger log =
	Logger.getLogger("com.sun.gi.objectstore.tso");

    private TSOObjectStore ostore;
    private SGSUUID transactionID;

    private long time;
    private long tiebreaker;

    private ClassLoader loader;
    private DataSpaceTransaction mainTrans, keyTrans, createTrans;
    private DataSpace mainDataSpace;
    private Map<Long, Serializable> lockedObjectsMap = new HashMap<Long, Serializable>();
    private List<Long> createdIDsList = new ArrayList<Long>();

    // private Map<Long, TSODataHeader> newObjectHeaders = new
    // HashMap<Long,TSODataHeader>();

    private boolean timestampInterrupted;

    private static long TIMEOUT;

    {
        TIMEOUT = 2000 * 60; // default timeout is 2 min
        String toStr = System.getProperty("sgs.objectstore.timeout");
        if (toStr != null) {
            TIMEOUT = Integer.parseInt(toStr);
        }
    }

    // static init

    TSOTransaction(TSOObjectStore ostore, ClassLoader loader, long time,
            long tiebreaker, DataSpace mainDataSpace) {

        this.ostore = ostore;
        this.loader = loader;
        transactionID = new StatisticalUUID();
        this.time = time;
        this.tiebreaker = tiebreaker;
        this.mainDataSpace = mainDataSpace;

    }

    public SGSUUID getUUID() {
        return transactionID;
    }

    public void start() {
        mainTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
        keyTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
        createTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
        timestampInterrupted = false;
	time = System.currentTimeMillis();
        ostore.registerActiveTransaction(this);
    }

    public long create(Serializable object, String name) {
        TSODataHeader hdr = new TSODataHeader(time, tiebreaker,
                System.currentTimeMillis() + TIMEOUT, transactionID,
                ObjectStore.INVALID_ID);
        long headerID = createTrans.create(hdr, name);
	if (log.isLoggable(Level.FINER)) {
	    if (headerID != DataSpace.INVALID_ID) {
		log.fine("Won create of " + name + " with id " + headerID);
	    } else {
		log.fine("Lost create of " + name);
	    }
	}
        while (headerID == DataSpace.INVALID_ID) {
	    // we were beat there
            headerID = lookup(name);
            try {
                lock(headerID);
                return ObjectStore.INVALID_ID;
            } catch (NonExistantObjectIDException e) {
                // means its been removed out from under us, so create
                // is okay try again
            	//System.out.println("Create aborted ina nother trans");
		log.finer("Loser is new winner for create of " + name);
                headerID = createTrans.create(hdr, name);               
                //System.out.println("new hdr id="+headerID);
                //if (headerID<0) {
                //	System.exit(-99);
                //}
            }
	    // loop until we can acquire a lock
        }
        long id = mainTrans.create(object, null);
        hdr.objectID = id;
        createTrans.write(headerID, hdr);
	log.finer("txn " + transactionID + " createTrans for " + name + " committing");
        createTrans.commit();
        createTrans.release(headerID);
        hdr.free = true;
        hdr.createNotCommitted = false;
        mainTrans.write(headerID, hdr); // will free when mainTrans commits
        lockedObjectsMap.put(headerID, object);
        createdIDsList.add(headerID);
        createdIDsList.add(hdr.objectID);
        return headerID;
    }

    public void destroy(long objectID) throws DeadlockException,
            NonExistantObjectIDException {
        TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
        mainTrans.destroy(hdr.objectID); // destroy object
        mainTrans.destroy(objectID); // destroy header
    }

    public Serializable peek(long objectID) throws NonExistantObjectIDException {
        TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
        if ((hdr.createNotCommitted) && (!hdr.uuid.equals(transactionID))) {
            return null;
        }
        return mainTrans.read(hdr.objectID);
    }

    public Serializable lock(long objectID, boolean block)
            throws DeadlockException, NonExistantObjectIDException {
        Serializable obj = lockedObjectsMap.get(objectID);
        if (obj != null) { // already locked
            return obj;
        }
        keyTrans.lock(objectID);
        TSODataHeader hdr = (TSODataHeader) keyTrans.read(objectID);
        while (!hdr.free) {
            if (System.currentTimeMillis() > hdr.timeoutTime) { // timed out
                ostore.requestTimeoutInterrupt(hdr.uuid);
                hdr.free = true;
            }
            if (timestampInterrupted) {
                keyTrans.abort();
                abort();
                throw new DeadlockException();
            } else if (!block) {
                keyTrans.abort();
                return null;
            }
            if ((time < hdr.time)
                    || ((time == hdr.time) && (tiebreaker < hdr.tiebreaker))) {
                ostore.requestTimestampInterrupt(hdr.uuid);
            }
            // System.out.println("Waiting for wakeup "+transactionID);
            synchronized (this) {
                if (!hdr.availabilityListeners.contains(transactionID)) {
                    hdr.availabilityListeners.add(transactionID);
                    keyTrans.write(objectID, hdr);
                    keyTrans.commit();
                } else {
                    keyTrans.abort();
                }
		if (log.isLoggable(Level.FINER)) {
		    log.finer("txn " + transactionID +
			" about to wait for header id " + objectID +
			" until " + String.format("%1$tF %<tT.%<tL", hdr.timeoutTime));
		}
                waitForWakeup(hdr.timeoutTime);
            }
            // System.out.println("wokeup "+transactionID);
            keyTrans.abort();
            keyTrans.lock(objectID);
            log.finer("txn " + transactionID + " about to re-read header id " + objectID);
            hdr = (TSODataHeader) keyTrans.read(objectID);
            //System.out.println("hdr="+hdr);
	    if (hdr.free) {
		log.finer("txn " + transactionID + " got header id " + objectID);
	    }
        }
        if (hdr.createNotCommitted) {
            mainTrans.destroy(hdr.objectID);
            mainTrans.destroy(objectID);
            return null;
        }
        hdr.free = false;
        hdr.time = time;
        hdr.timeoutTime = time + TIMEOUT;
        hdr.tiebreaker = tiebreaker;
        hdr.availabilityListeners.remove(transactionID);
        hdr.uuid = transactionID;
        keyTrans.write(objectID, hdr);
        keyTrans.commit();
        obj = mainTrans.read(hdr.objectID);
        lockedObjectsMap.put(objectID, obj);
        return obj;
    }

    /**
     * @param l
     * 
     */
    private void waitForWakeup(long l) {
        // System.out.println("GOing into wait "+transactionID);
        // System.out.flush();
        synchronized (this) {
            try {
                long now = System.currentTimeMillis();
                if (now < l) {
                    this.wait(l - now);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // System.out.println("Coming out of wait "+transactionID);
        // System.out.flush();

    }

    public Serializable lock(long objectID) throws DeadlockException,
            NonExistantObjectIDException {
        return lock(objectID, true);
    }

    public long lookup(String name) {
        return mainTrans.lookupName(name);
    }

    private void processLockedObjects(boolean commit) {
        List<SGSUUID> listeners = new ArrayList<SGSUUID>();
	log.finer((commit ? "COMMIT " : "ABORT ") + transactionID +
		" releasing " + lockedObjectsMap.size() + " locks");
        synchronized (keyTrans) {
	    if (log.isLoggable(Level.FINEST)) {
		long[] updatedIDs = new long[lockedObjectsMap.size()];
		int i = 0;
		for (long key : lockedObjectsMap.keySet()) {
		    updatedIDs[i++] = key;
		}
		log.finest("keyTrans releasing " + Arrays.toString(updatedIDs));
	    }
            for (Entry<Long, Serializable> entry : lockedObjectsMap.entrySet()) {
                Long l = entry.getKey();
                try {
                    keyTrans.lock(l);
                    TSODataHeader hdr = (TSODataHeader) keyTrans.read(l);
                    hdr.free = true;
                    keyTrans.write(l, hdr);
                    listeners.addAll(hdr.availabilityListeners);
                    if (commit) {
                        mainTrans.write(hdr.objectID, entry.getValue());
                    }
                } catch (NonExistantObjectIDException e) {
                    e.printStackTrace();
                }
            }
	    log.finest("keyTrans commit-1 txn " + transactionID);
            keyTrans.commit();
        }
        if (commit) {
	    if (log.isLoggable(Level.FINEST)) {
		long[] updatedIDs = new long[lockedObjectsMap.size()];
		int i = 0;
		for (long key : lockedObjectsMap.keySet()) {
		    updatedIDs[i++] = key + 1; // NOTE: ObjectID == (HeaderID + 1)
		}
		log.finest("main committing " + Arrays.toString(updatedIDs));
	    }
            mainTrans.commit();
        } else {
	    if (log.isLoggable(Level.FINEST)) {
		long[] abortCreateIDs = new long[createdIDsList.size()];
		int i = 0;
		for (long key : createdIDsList) {
		    abortCreateIDs[i++] = key;
		}
		log.finest("keyTrans nuking creates: " + Arrays.toString(abortCreateIDs));
	    }
            synchronized (keyTrans) {
		log.finer(transactionID + " keyTrans nuking " +
		    createdIDsList.size() + " partial creates");
                for (Long l : createdIDsList) {
                    keyTrans.destroy(l);
                }
            }
	    log.finest("keyTrans commit-2 txn " + transactionID);
            keyTrans.commit();
	    log.finer("mainTrans abort txn " + transactionID);
            mainTrans.abort();
        }
        lockedObjectsMap.clear();
        createdIDsList.clear();
        ostore.notifyAvailabilityListeners(listeners);
        ostore.deregisterActiveTransaction(this);
    }

    public void abort() {
        processLockedObjects(false);
    }

    public void commit() {
        processLockedObjects(true);

    }

    public long getCurrentAppID() {
        return ostore.getAppID();
    }

    public void clear() {
        ostore.clear();
    }

    public void timeStampInterrupt() {
        timestampInterrupted = true;
        synchronized (this) {
            this.notifyAll();
        }
    }
}
