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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
    private final ClassLoader loader;
    final SGSUUID txnID;

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
    final long initialAttemptTime;

    final long tiebreaker;

    long currentTransactionDeadline;

    private final Map<Long, Serializable> lockedObjectsMap;
    private final Map<Long, Serializable> peekedObjectsMap;
    private final Set<Long> createdIDs;
    private final Set<Long> deletedIDs;

    private final DataSpace dataSpace;
    private DataSpaceTransaction trans;

    private volatile boolean timestampInterrupted;

    private static final boolean DEBUG = true;

    private static long TIMEOUT =
        Integer.parseInt(System.getProperty("sgs.objectstore.timeout",
                "10000" /* "120000" */ /* millisecs */));

    TSOTransaction(TSOObjectStore ostore, ClassLoader loader,
	    long creationTime, long tiebreaker, DataSpace dataSpace)
    {
        this.ostore = ostore;
        this.txnID = new StatisticalUUID();
        this.loader = loader;
        this.initialAttemptTime = creationTime;
        this.tiebreaker = tiebreaker;
        this.currentTransactionDeadline = 1;
        this.dataSpace = dataSpace;
	this.lockedObjectsMap= new HashMap<Long, Serializable>();
	this.peekedObjectsMap= new HashMap<Long, Serializable>();
	this.createdIDs = new HashSet<Long>();
	this.deletedIDs = new HashSet<Long>();
    }

    public SGSUUID getUUID() {
        return txnID;
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
        trans = new DataSpaceTransactionImpl(loader, dataSpace);
	currentTransactionDeadline = System.currentTimeMillis() + TIMEOUT;
        timestampInterrupted = false;
        ostore.registerActiveTransaction(this);
    }

    public long lookup(String name) {
        return trans.lookupName(name);
    }

    public long create(Serializable object, String name) {

        TSODataHeader hdr = new TSODataHeader(this);

        long headerID = trans.create(hdr, name);

	if (DEBUG) {
	    if (log.isLoggable(Level.FINER)) {
		if (headerID != DataSpace.INVALID_ID) {
		    log.fine("txn " + txnID + " won create of " + name +
			" with hdrID " + headerID);
		} else {
		    log.fine("txn " + txnID + " lost create of " + name);
		}
	    }
	}

        while (headerID == DataSpace.INVALID_ID) {

	    // Someone else beat us to the create()
            headerID = lookup(name);
            try {
                lock(headerID); // Note: TSO lock, not dataspace lock

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

		if (DEBUG) {
		    log.finer("txn " + txnID +
			" former loser wins create of " + name);
		}

                headerID = trans.create(hdr, name);
            }

	    // loop until we can acquire a lock
        }

	try {
	    trans.lock(headerID);
	} catch (NonExistantObjectIDException e) {
	    log.severe("Create failed -- can't trans.lock " + headerID);
	    abort();
	    throw new IllegalStateException("TSOTransaction.create failed");
	}

	hdr.hdrID = headerID; // (LOG) set if using hdrID in TSODataHeader

	// XXX Later, we can just get the next sequence number
	// and store that in the header, and defer the actual
	// write of the object data until commit time.
        long id = trans.create(object, null);
        hdr.objectID = id;


	// lockedObjectsMap maps headerIDs to the *real objects*
	// to which they refer
        lockedObjectsMap.put(headerID, object);
        createdIDs.add(headerID);

	// With the headerID lock held, commit the partial-create
	// header so that other attempts to create will wait for
	// our overall commit or abort later.
        trans.write(headerID, hdr);
        trans.commit();

	if (DEBUG) {
	    log.finer("txn " + txnID +
		    " trans for " + name + " commit " + hdr);
	}

        return headerID;
    }

    public void destroy(long objectID)
	    throws DeadlockException, NonExistantObjectIDException
    {
	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs.

	if (objectID == DataSpace.INVALID_ID) {
	    throw new NonExistantObjectIDException();
	}

        TSODataHeader hdr = (TSODataHeader) trans.read(objectID);
        if ((hdr.createNotCommitted) && (!hdr.owner.equals(txnID))) {
	    // It's only paritally created, so we don't see it.
            return;
        }
	deletedIDs.add(objectID);
    }

    public Serializable peek(long objectID)
	    throws NonExistantObjectIDException
    {
	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs,
	// unbeknownst to them.

	if (objectID == DataSpace.INVALID_ID) {
	    throw new NonExistantObjectIDException();
	}

	// Was it deleted in this transaction? If so, show it as gone.
	if (deletedIDs.contains(objectID)) {
	    return null;
	}

	// Is there a GET lock on it already from an earlier lock/create?
        Serializable obj = lockedObjectsMap.get(objectID);
        if (obj != null) {
	    // We've got a locked copy, so return it.
            return obj;
        }

	// Have we PEEKed at it before?
        obj = peekedObjectsMap.get(objectID);
        if (obj != null) {
            return obj;
        }

        TSODataHeader hdr = (TSODataHeader) trans.read(objectID);
        if ((hdr.createNotCommitted) && (!hdr.owner.equals(txnID))) {
	    // It's only paritally created, so we don't see it.
	    if (DEBUG) {
		log.fine("Someone else has partially created " + objectID);
	    }
            return null;
        }

	// Go get it, and save it so that future peek() calls in 
	// the context of this TSOTransaction return the same object.
	// However, note well that any time a get() is done, any calls
	// to peek() or get() afterwards always return the object
	// obtained by get(), and objects peeked() before a get()
	// will not reflect changes made after the call to get().

	obj = trans.read(hdr.objectID);
        peekedObjectsMap.put(objectID, obj);

        return obj;
    }

    public Serializable lock(long objectID)
	    throws DeadlockException, NonExistantObjectIDException
    {
        return lock(objectID, true);
    }

    public Serializable lock(long objectID, boolean shouldBlock)
            throws DeadlockException, NonExistantObjectIDException
    {
	if (objectID == DataSpace.INVALID_ID) {
	    throw new NonExistantObjectIDException();
	}

	// It was deleted in this transaction, show it as gone.
	if (deletedIDs.contains(objectID)) {
	    return null;
	}

        Serializable obj = lockedObjectsMap.get(objectID);
        if (obj != null) {
	    // We've already locked it -- return the cached copy.
            return obj;
        }

	// Note that 'objectID' is actually the id of the object's
	// *TSODataHeader* in the database.  The header has a field
	// named objectID which is the 'real' objectID of its contents.
	// Users of TSOTransaction refer to objects by their headerIDs.

        trans.lock(objectID);
        TSODataHeader hdr = (TSODataHeader) trans.read(objectID);
	for (;;) {
	    while (!hdr.free && !hdr.owner.equals(txnID)) {

		long now = System.currentTimeMillis();

		if (now > currentTransactionDeadline) {
		    // We're out of time; abort and see if we make it
		    // on the next try (when we're requeued due to
		    // DeadlockException)

		    log.warning("txn " + txnID + " out of time for " + hdr);

		    abort();
		    throw new DeadlockException();
		}

		if (now > hdr.currentTransactionDeadline) {
		    // The lock is stale, grab it ourselves
		    ostore.requestTimeoutInterrupt(hdr.owner);
		    // We'll be taking the lock, so break out of this loop.
		    // Do *not* update hdr.free, in case we need to
		    // do a deadline-abort (look for deadline-abort below).

		    log.warning("txn " + txnID + " grabbing stale lock " + hdr);

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
		    trans.release(objectID);

		    if (DEBUG) {
			log.fine("txn " + txnID +
				" would block, returning null " + hdr);
		    }

		    return null;
		}

		if (timestampInterrupted) {
		    // We were interrupted and are about to block, so
		    // honor the interruption and abort.

		    if (DEBUG) {
			log.fine("txn " + txnID + " interrupted, abort " + hdr);
		    }

		    abort();
		    throw new DeadlockException();
		}

		if (hdr.youngerThan(initialAttemptTime, tiebreaker)) {
		    // We are more senior than the current owner
		    // of the lock; tell him to give it up!

		    if (DEBUG) {
			log.fine("txn " + txnID + " pulling seniority on " + hdr);
		    }

		    ostore.requestTimestampInterrupt(hdr.owner);
		}

		synchronized (this) {
		    // Synchronize early so we have a chance to add ourselves
		    // as a listener -- we must be sure to get notifyAll'd
		    // if an interrupt comes in from our objectStore.
		    if (!hdr.availabilityListeners.contains(txnID)) {
			hdr.availabilityListeners.add(txnID);
			trans.write(objectID, hdr);

			if (DEBUG) {
			    log.finer("txn " + txnID +
				    " adding self as availListener to " + hdr);
			}

			trans.commit();
		    } else {
			trans.abort();
		    }

		    if (DEBUG) {
			if (log.isLoggable(Level.FINER)) {
			    SGSUUID[] listeners =
				new SGSUUID[hdr.availabilityListeners.size()];
			    hdr.availabilityListeners.toArray(listeners);
			    log.finer("txn " + txnID +
				" about to wait for " + hdr);
			}
		    }

		    waitForWakeup(hdr.currentTransactionDeadline);
		}

		trans.lock(objectID);
		trans.forget(objectID);

		if (DEBUG) {
		    log.finer("txn " + txnID + " re-reading " + objectID);
		}

		hdr = (TSODataHeader) trans.read(objectID);

		if (DEBUG) {
		    log.finer("txn " + txnID + " read hdr " + hdr);
		}
	    }

	    // Next, we sleep on the object until awakened.  But there
	    // might be many sleepers; who should we get it?  Let's see
	    // who was assigned this lock, and who else is waiting for
	    // this lock.  If there's someone senior to us, give it to
	    // him.  -DJE

	    /*
	     * It's our baby.  So it's up to us to do the assignment, or
	     * just run ourself.
	     */

	    Set<TSOTransaction> xactions = new HashSet<TSOTransaction>();

	    // assertion:  we don't need to make a copy of the
	    // listeners list.  They won't change out from
	    // under us, at least not in the single-stack
	    // case. -DJE

	    // assume that we're going to win, then try to disprove
	    TSOTransaction senior = this;

	    Iterator<SGSUUID> it;
	    for (it = hdr.availabilityListeners.iterator(); it.hasNext();) {
		SGSUUID uid = it.next();
		TSOTransaction tsot = ostore.sgsuuid2transaction(uid);

		if (tsot == null) {
		    // that transaction is gone, skip it
		    log.warning("txn " + uid +
			" is null, but still a listener for " + objectID);
		    it.remove();
		    continue;
		}

		if ((tsot.initialAttemptTime < senior.initialAttemptTime) ||
			((tsot.initialAttemptTime == senior.initialAttemptTime) &&
			    (tsot.tiebreaker < senior.tiebreaker))) {
		    senior = tsot;
		}
	    }

	    if (senior.txnID.equals(txnID)) {
		if (hdr.availabilityListeners.isEmpty()) {
		    log.finer("nobody wanted the object: I'm grabbing it: " +
			    objectID);
		} else {
		    log.finer("I am the senior waiter: " + objectID);
		}
		break;
	    } else {

		// making sure that we get woken up when it is our
		// turn, at some distant point in the future.

		hdr.availabilityListeners.add(txnID);

		log.finer("handing from " + txnID + " to " + senior.txnID);

		// XXX hand the lock to that guy.
		
		hdr.free = false;
		hdr.owner = senior.txnID;
		hdr.initialAttemptTime = senior.initialAttemptTime;
		hdr.tiebreaker = senior.tiebreaker;
		hdr.currentTransactionDeadline =
			senior.currentTransactionDeadline;
		trans.write(objectID, hdr);
		trans.commit();

		ostore.notifyAvailabilityListeners(hdr.availabilityListeners);
	    }
	}

        if (hdr.createNotCommitted) {
	    log.warning("txn " + txnID +
		    " found partial create for " + objectID +
		    " -- scrubbing");

	    // A create has partially aborted, leaving some junk behind.
	    // Clean it out and let our caller do the right thing.
	    // Our caller may be create(), in which case he will catch
	    // this exception and create the object.
            trans.destroy(hdr.objectID);
            trans.destroy(objectID);
	    trans.commit();
	    throw new NonExistantObjectIDException();
        }

	if (System.currentTimeMillis() > currentTransactionDeadline) {
	    // We've run past our deadline: do a deadline-abort.

	    log.warning("txn " + txnID +
		    " is past its deadline for " + objectID +
		    " -- throwing a DeadlockException");

	    if (!hdr.free) {
		// We stole the lock from someone and broke out of
		// the loop above.  We need to mark it as "free" and
		// write the header so everyone else knows it's unlocked.
		hdr.free = true;
		List<SGSUUID> listeners =
			new ArrayList<SGSUUID>(hdr.availabilityListeners);
		hdr.availabilityListeners.clear();
		trans.write(objectID, hdr);

		if (DEBUG) {
		    log.fine("txn " + txnID + " stale-abort update " + hdr);
		}

		trans.commit();

		listeners.remove(txnID);
		ostore.notifyAvailabilityListeners(listeners);
	    }

	    abort();
	    throw new DeadlockException();
	}

	// Take ownership of this header
        hdr.free = false;
        hdr.owner = txnID;
        hdr.initialAttemptTime = initialAttemptTime;
        hdr.tiebreaker = tiebreaker;
        hdr.currentTransactionDeadline = currentTransactionDeadline;
        hdr.availabilityListeners.remove(txnID);
        trans.write(objectID, hdr);

        obj = trans.read(hdr.objectID);
        trans.commit();

	if (DEBUG) {
	    log.finest("got get-lock, wrote " + hdr);
	}

	// GET supersedes PEEK
	if (DEBUG && peekedObjectsMap.containsKey(objectID)) {
	    log.finest("txn " + txnID + " GET superseding PEEK for " + hdr);
	}
	peekedObjectsMap.remove(objectID);

        lockedObjectsMap.put(objectID, obj);
        return obj;
    }

    /**
     * @param deadline the absolute time at which to wake up
     * if we have not yet been notified, in milliseconds since
     * the Unix epoch.
     */
    private void waitForWakeup(long deadline) {

	// DJE:  why don't we loop if interrupted?  Doesn't seem to be
	// any way to distinguish waking up because have the lock
	// versus just waking up?

        synchronized (this) {
            try {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime > 0) {
                    this.wait(waitTime);
                }
            } catch (InterruptedException e) {
                //e.printStackTrace();
		log.fine("txn " + txnID + " interrupted");
            }
        }
    }

    public void abort() {

	if (DEBUG) {
	    if (log.isLoggable(Level.FINEST)) {
		long[] abortCreateIDs = new long[createdIDs.size()];
		int i = 0;
		for (long key : createdIDs) {
		    abortCreateIDs[i++] = key;
		}
		Arrays.sort(abortCreateIDs);
		log.finest("trans nuking " + createdIDs.size() +
			" partial creates: " +
			Arrays.toString(abortCreateIDs));
	    } else {
		log.finer(txnID + " trans nuking " +
			createdIDs.size() + " partial creates");
	    }
	}

        Set<SGSUUID> listeners = new HashSet<SGSUUID>();
        Set<Long> dataspaceLocks = new HashSet<Long>();

	for (Long l : createdIDs) {
	    try {
		if (! dataspaceLocks.contains(l)) {
		    trans.lock(l);
		    dataspaceLocks.add(l);
		}
		TSODataHeader hdr = (TSODataHeader) trans.read(l);
		listeners.addAll(hdr.availabilityListeners);

		if (DEBUG) {
		    log.finest("destroy invalid " + hdr);
		}

		trans.destroy(hdr.objectID);
		trans.destroy(l);
		lockedObjectsMap.remove(l);
	    } catch (Exception e) {
		// XXX Remember to throw something at the end
		e.printStackTrace();
	    }
	}

	for (Long l : deletedIDs) {
	    try {
		if (! dataspaceLocks.contains(l)) {
		    trans.lock(l);
		    dataspaceLocks.add(l);
		}
		TSODataHeader hdr = (TSODataHeader) trans.read(l);
		listeners.addAll(hdr.availabilityListeners);
		hdr.free = true;
		trans.write(l, hdr);
		lockedObjectsMap.remove(l);
		if (DEBUG) {
		    log.finest("abort-delete " + hdr);
		}
	    } catch (Exception e) {
		// XXX Remember to throw something at the end
		e.printStackTrace();
	    }
	}

	for (Entry<Long, Serializable> entry : lockedObjectsMap.entrySet()) {
	    Long l = entry.getKey();
	    try {
		if (! dataspaceLocks.contains(l)) {
		    trans.lock(l);
		    dataspaceLocks.add(l);
		}
		TSODataHeader hdr = (TSODataHeader) trans.read(l);
		listeners.addAll(hdr.availabilityListeners);
		hdr.free = true;
		//hdr.createNotCommitted = false; // not needed for everyone
		trans.write(l, hdr);
		if (DEBUG) {
		    log.finest("abort-update " + hdr);
		}
	    } catch (NonExistantObjectIDException e) {
		e.printStackTrace();
	    }
	}

	if (DEBUG) {
	    log.finest("abort commiting txn " + txnID);
	}
	trans.commit();

        peekedObjectsMap.clear();
        lockedObjectsMap.clear();
        createdIDs.clear();
        deletedIDs.clear();
        ostore.notifyAvailabilityListeners(new ArrayList<SGSUUID>(listeners));
        ostore.deregisterActiveTransaction(this);
    }

    public void commit() {

	if (DEBUG) {
	    if (log.isLoggable(Level.FINEST)) {
		int i;

		long[] dbgDeleted = new long[deletedIDs.size()];
		i = 0;
		for (long key : deletedIDs) {
		    dbgDeleted[i++] = key;
		}
		Arrays.sort(dbgDeleted);
		log.finest("trans in txn " + txnID +
			" deleting " + Arrays.toString(dbgDeleted));

		long[] dbgLocked = new long[lockedObjectsMap.size()];
		i = 0;
		for (long key : lockedObjectsMap.keySet()) {
		    dbgLocked[i++] = key;
		}
		Arrays.sort(dbgLocked);
		log.finest("trans in txn " + txnID +
			" updating/locked " + Arrays.toString(dbgLocked));
	    }
	}

        Set<SGSUUID> listeners = new HashSet<SGSUUID>();
        Set<Long> dataspaceLocks = new HashSet<Long>();

	for (Long l : deletedIDs) {
	    try {
		if (! dataspaceLocks.contains(l)) {
		    trans.lock(l);
		    dataspaceLocks.add(l);
		}
		TSODataHeader hdr = (TSODataHeader) trans.read(l);
		listeners.addAll(hdr.availabilityListeners);
		log.finest("commit-delete " + hdr);
		trans.destroy(hdr.objectID);
		trans.destroy(l);
		lockedObjectsMap.remove(l);
	    } catch (Exception e) {
		// XXX Remember to throw something at the end
		e.printStackTrace();
	    }
	}

	for (Entry<Long, Serializable> entry : lockedObjectsMap.entrySet())
	{
	    Long l = entry.getKey();
	    try {
		if (! dataspaceLocks.contains(l)) {
		    trans.lock(l);
		    dataspaceLocks.add(l);
		}
		TSODataHeader hdr = (TSODataHeader) trans.read(l);
		listeners.addAll(hdr.availabilityListeners);

		if (DEBUG) {
		    log.finest("commit-update hdr before (" + l + ") " + hdr);
		}

		hdr.free = true;
		hdr.createNotCommitted = false; // not needed for everyone
		trans.write(l, hdr);
		trans.write(hdr.objectID, entry.getValue());

		if (DEBUG) {
		    log.finest("commit-update hdr after " + hdr);
		}

	    } catch (NonExistantObjectIDException e) {
		e.printStackTrace();
	    }
	}

	if (DEBUG) {
	    log.finest("commit commiting txn " + txnID);
	}

	trans.commit();

        peekedObjectsMap.clear();
        lockedObjectsMap.clear();
        createdIDs.clear();
        deletedIDs.clear();
        ostore.notifyAvailabilityListeners(new ArrayList<SGSUUID>(listeners));
        ostore.deregisterActiveTransaction(this);

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
