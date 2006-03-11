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

    private final TSOObjectStore ostore;
    private final SGSUUID transactionID;
    private final ClassLoader loader;

    /**
     * initialAttemptTime is the time at which this transaction
     * was created.  If we abort() and requeue due to a
     * DeadlockException, we maintain our initialAttemptTime so
     * that we are more and more certain to win all the locks we
     * need.  If we were to simply reset it on each attempt, we
     * might never make forward progress.
     * <p>
     * A transaction that commits or aborts normally will never
     * run again, and may not be reused or reset.
     */
    private final long initialAttemptTime;

    private final long tiebreaker;

    private long currentTransactionDeadline;

    private final Map<Long, Serializable> lockedObjectsMap;
    private final List<Long> createdIDsList;

    private DataSpaceTransaction mainTrans;
    private DataSpaceTransaction keyTrans;
    private DataSpaceTransaction createTrans;
    private DataSpace mainDataSpace;

    private volatile boolean timestampInterrupted;

    private static long TIMEOUT =
        Integer.parseInt(System.getProperty("sgs.objectstore.timeout",
                "120000" /* millisecs */));

    TSOTransaction(TSOObjectStore ostore, ClassLoader loader,
	    long creationTime, long tiebreaker, DataSpace mainDataSpace)
    {
        this.ostore = ostore;
        this.transactionID = new StatisticalUUID();
        this.loader = loader;
        this.initialAttemptTime = creationTime;
        this.tiebreaker = tiebreaker;
        this.currentTransactionDeadline = 1;
        this.mainDataSpace = mainDataSpace;
	this.lockedObjectsMap= new HashMap<Long, Serializable>();
	this.createdIDsList = new ArrayList<Long>();
    }

    public SGSUUID getUUID() {
        return transactionID;
    }

    /**
     * Acquires database resources needed to begin the transaction.
     * If this transaction aborted due to DeadlockException and has
     * been requeued, start() prepares it for another try.  However,
     * that is the *only* permitted re-use of a TSOTransaction.
     */
    public void start() {
	if (currentTransactionDeadline == 0) {
	    // On commit, we set this to catch invalid reuse situations.
	    // TSOTransaction is intended to be reused only if an abort
	    // occurs and has been re-queued for another attempt.
	    throw new IllegalStateException("Invalid reuse of TSOTransaction");
	}
        mainTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
        keyTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
        createTrans = new DataSpaceTransactionImpl(loader, mainDataSpace);
	currentTransactionDeadline = System.currentTimeMillis() + TIMEOUT;
        timestampInterrupted = false;
        ostore.registerActiveTransaction(this);
    }

    public long create(Serializable object, String name) {

        TSODataHeader hdr = new TSODataHeader(initialAttemptTime,
		tiebreaker, currentTransactionDeadline, transactionID,
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
	    // Someone else beat us to the create()
            headerID = lookup(name);
            try {
                lock(headerID);
		// If the other TSOTransaction (who won the create race)
		// ends up aborting, and if we then end up with the GET
		// lock on this oid, then we'll get thrown
		// a NonExistantObjectID exception, which is handled
		// in the catch block below.
		//
		// If the other transaction committed, though, we'll
		// eventually acquire the lock without an exception.
		// This means we were beaten to a commited create(),
		// so we must return *INVALID_ID* to our caller so
		// he knows he lost the race and can re-get the
		// object committed by the winner.
		//
		// (If we had simply returned the object ID, our
		// caller would have no way of knowing that someone
		// else's object is the one that got created).

                return ObjectStore.INVALID_ID;

            } catch (NonExistantObjectIDException e) {
                // This exception means that we had originally lost
		// the create race, but the winner ended up aborting
		// and the object hasn't really been created.
		//
		// So we try again -- but we loop in order to check
		// for a race on this round.

		log.finer("txn " + transactionID +
			" former loser is new winner for create of " + name);
                headerID = createTrans.create(hdr, name);
            }

	    // loop until we can acquire a lock
        }

        long id = mainTrans.create(object, null);
        hdr.objectID = id;

	// Immediately (with the headerID lock held) commit the
	// partial-create header so that other attempts to create
	// will wait for our main (eventual) commit or abort.
        createTrans.write(headerID, hdr);
	log.finer("txn " + transactionID +
		" createTrans for " + name + " committing");
        createTrans.commit();
        createTrans.release(headerID);

	// Set up the header and objects as they should be if
	// the main transaction commits.
        hdr.free = true;
        hdr.createNotCommitted = false;
        mainTrans.write(headerID, hdr); // will free when mainTrans commits
        lockedObjectsMap.put(headerID, object);
        createdIDsList.add(headerID);
        createdIDsList.add(hdr.objectID);

        return headerID;
    }

    public void destroy(long objectID) throws DeadlockException,
            NonExistantObjectIDException
    {
	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs.

        TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
        if ((hdr.createNotCommitted) && (!hdr.owner.equals(transactionID))) {
            return;
        }
        mainTrans.destroy(hdr.objectID); // destroy object
        mainTrans.destroy(objectID); // destroy header
    }

    public Serializable peek(long objectID) throws NonExistantObjectIDException
    {
	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs.

        TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
        if ((hdr.createNotCommitted) && (!hdr.owner.equals(transactionID))) {
            return null;
        }
        return mainTrans.read(hdr.objectID);
    }

    public Serializable lock(long objectID, boolean shouldBlock)
            throws DeadlockException, NonExistantObjectIDException
    {
        Serializable obj = lockedObjectsMap.get(objectID);
        if (obj != null) {
	    // We've already locked it -- return the cached copy.
            return obj;
        }

	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs.

        keyTrans.lock(objectID);
        TSODataHeader hdr = (TSODataHeader) keyTrans.read(objectID);
        while (!hdr.free) {

            if (System.currentTimeMillis() > hdr.currentTransactionDeadline) {
		// The lock is stale, grab it ourselves
                ostore.requestTimeoutInterrupt(hdr.owner);
		// We'll be taking the lock, so break out of this loop.
		// Do *not* update hdr.free, in case we need to
		// do a deadline-abort (look for deadline-abort below).
		break;
            }

            if (!shouldBlock) {
		// This is an attempt() call, so we should neither
		// block nor steal the lock from younger transactions
		// if they currently hold it.
		// Checked *before* timestampInterrupted, because
		// if we're not going to block we can just keep
		// running along -- we only accept the interrupt
		// if we discover we'll block.
                keyTrans.abort();
                return null;
	    }

            if (timestampInterrupted) {
		// We were interrupted and are about to block, so
		// honor the interruption and abort.
                keyTrans.abort();
                abort();
                throw new DeadlockException();
            }

	    if (hdr.youngerThan(initialAttemptTime, tiebreaker)) {
		// We are more senior than the current owner
		// of the lock; tell him to give it up!
                ostore.requestTimestampInterrupt(hdr.owner);
            }

            synchronized (this) {
		// Synchronize early so we have a chance to add ourselves
		// as a listener -- we must be sure to get notifyAll'd
		// if an interrupt comes in from our objectStore.
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
			" until " +
			String.format("%1$tF %<tT.%<tL",
				hdr.currentTransactionDeadline));
		}
                waitForWakeup(hdr.currentTransactionDeadline);
            }

	    // @@ This abort has a non-obvious use: it clears
	    // the DataTransaction's cache of loaded objects,
	    // so when we read the header again we will see
	    // the updated copy, not a cached copy.
            keyTrans.abort();

            keyTrans.lock(objectID);

            log.finer("txn " + transactionID +
		" about to re-read header id " + objectID);

            hdr = (TSODataHeader) keyTrans.read(objectID);

	    if (hdr.free) {
		log.finer("txn " + transactionID +
		    " got header id " + objectID);
	    }
        }

        if (hdr.createNotCommitted) {
	    // A create has partially aborted, leaving some junk behind.
	    // Clean it out and let our caller do the right thing.
	    // Our caller may be create(), in which case he will catch
	    // this exception and create the object.
            keyTrans.destroy(hdr.objectID);
            keyTrans.destroy(objectID);
	    keyTrans.commit();
	    throw new NonExistantObjectIDException();
        }

	if (System.currentTimeMillis() > currentTransactionDeadline) {
	    // We've run past our deadline: do a deadline-abort.

	    if (!hdr.free) {
		// We stole the lock from someone and broke out of
		// the loop above.  We need to mark it as "free" and
		// write the header so everyone else knows it's unlocked.
		hdr.free = true;
	    }

	    abort();
	    throw new DeadlockException();
	}

	// Take ownership of this header
        hdr.free = false;
        hdr.owner = transactionID;
        hdr.initialAttemptTime = initialAttemptTime;
        hdr.tiebreaker = tiebreaker;
        hdr.currentTransactionDeadline = currentTransactionDeadline;
        hdr.availabilityListeners.remove(transactionID);
        keyTrans.write(objectID, hdr);
        keyTrans.commit();

	// Now that we have the lock, get the object and cache it.
        obj = mainTrans.read(hdr.objectID);
        lockedObjectsMap.put(objectID, obj);

        return obj;
    }

    /**
     * @param deadline the absolute time at which to wake up
     * if we have not yet been notified, in milliseconds since
     * the Unix epoch.
     */
    private void waitForWakeup(long deadline) {
        synchronized (this) {
            try {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime > 0) {
                    this.wait(waitTime);
                }
            } catch (InterruptedException e) {
                //e.printStackTrace();
		log.fine("txn " + transactionID + " interrupted");
            }
        }
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
	    log.finest("keyTrans commit-destroy txn " + transactionID);
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

	// Use the deadline as a sentinel in case someone tries to
	// reuse this transaction.
	currentTransactionDeadline = 0;
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
