/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.util.lock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;

/**
 * A class for managing lock conflicts as part of a transaction.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 */
public final class TxnLockManager<K, L extends TxnLocker<K, L>>
    extends LockManager<K, L>
{
    /* -- Public constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	lockTimeout the maximum number of milliseconds to acquire a
     *		lock
     * @param	numKeyMaps the number of separate maps to use for storing keys
     * @throws	IllegalArgumentException if {@code lockTimeout} or {@code
     *		numKeyMaps} is less than {@code 1}
     */
    public TxnLockManager(long lockTimeout, int numKeyMaps) {
	super(lockTimeout, numKeyMaps);
    }

    /* -- Public methods -- */

    /**
     * Attempts to acquire a lock, waiting if needed.  Returns information
     * about conflicts that occurred while attempting to acquire the lock that
     * prevented the lock from being acquired, or else {@code null} if the lock
     * was acquired.  If the {@code type} field of the return value is {@link
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should abort the
     * transaction, and any subsequent lock or wait requests will throw {@code
     * IllegalStateException}.
     *
     * @param	locker the locker requesting the lock
     * @param	key the key identifying the lock
     * @param	forWrite whether to request a write lock
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalArgumentException if {@code locker} has a different lock
     *		manager 
     * @throws	IllegalStateException if an earlier lock attempt for this
     *		transaction produced a deadlock, or if still waiting for an
     *		earlier attempt to complete
     */
    public LockConflict<K, L> lock(L locker, K key, boolean forWrite) {
	return lock(locker, key, forWrite, -1);
    }

    /**
     * Attempts to acquire a lock, returning immediately.  Returns information
     * about any conflict that occurred while attempting to acquire the lock,
     * or else {@code null} if the lock was acquired.  If the attempt to
     * acquire the lock was blocked, returns a value with a {@code type} field
     * of {@link LockConflictType#BLOCKED BLOCKED} rather than waiting.  If the
     * {@code type} field of the return value is {@link
     * LockConflictType#DEADLOCK DEADLOCK}, then the caller should abort the
     * transaction, and any subsequent lock or wait requests will throw {@code
     * IllegalStateException}.
     *
     * @param	locker the locker requesting the lock
     * @param	key the key identifying the lock
     * @param	forWrite whether to request a write lock
     * @return	lock conflict information, or {@code null} if there was no
     *		conflict
     * @throws	IllegalArgumentException if {@code locker} has a different lock
     *		manager 
     * @throws	IllegalStateException if an earlier lock attempt for this
     *		transaction produced a deadlock, or if still waiting for an
     *		earlier attempt to complete
     */
    public LockConflict<K, L> lockNoWait(L locker, K key, boolean forWrite) {
	return lockNoWait(locker, key, forWrite, -1);
    }

    /* -- Other methods -- */

    /**
     * This implementation calls the deadlock checker if the request blocks.
     */
    @Override
    LockConflict<K, L> lockNoWaitInternal(
	L locker, K key, boolean forWrite, long requestedStartTime)
    {
	LockConflict<K, L> conflict = super.lockNoWaitInternal(
	    locker, key, forWrite, requestedStartTime);
	if (conflict != null) {
	    LockConflict<K, L> deadlockConflict =
		new DeadlockChecker(locker.getWaitingFor().request).check();
	    if (deadlockConflict != null) {
		conflict = deadlockConflict;
	    }
	}
	return conflict;
    }

    /* -- Other classes -- */

    /** Utility class for detecting deadlocks. */
    private class DeadlockChecker {

	/**
	 * Maps lockers to information about requests they are waiting for.
	 * This map serves as a cache for information about lock owners, to
	 * avoid the synchronization needed to retrieve it again when checking
	 * for multiple deadlocks.
	 */
	private final Map<L, WaiterInfo<K, L>> waiterMap =
	    new HashMap<L, WaiterInfo<K, L>>();

	/** The top level request we are checking for deadlocks. */
	private final LockRequest<K, L> rootRequest;

	/**
	 * The pass number of the current deadlock check.  There could be
	 * multiple deadlocks active simultaneously, so deadlock checking is
	 * repeated until no deadlocks are found.
	 */
	private int pass;

	/** The request that was found in a circular reference. */
	private LockRequest<K, L> cycleBoundary;

	/** The current choice of a request to abort. */
	private LockRequest<K, L> victim;

	/** Another request in the deadlock. */
	private LockRequest<K, L> conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	request the request to check
	 */
	DeadlockChecker(LockRequest<K, L> request) {
	    assert request != null;
	    rootRequest = request;
	}

	/**
	 * Checks for a deadlock starting with the root locker.
	 *
	 * @return	a lock conflict if a deadlock was found, else {@code
	 *		null}
	 */
	LockConflict<K, L> check() {
	    LockConflict<K, L> result = null;
	    for (pass = 1; true; pass++) {
		if (!checkInternal(
			rootRequest, getWaiterInfo(rootRequest.getLocker())))
		{
		    if (result == null) {
			logger.log(FINEST, "check deadlock {0}: no deadlock",
				   rootRequest);
			return null;
		    } else {
			return result;
		    }
		}
		if (logger.isLoggable(FINER)) {
		    logger.log(FINER, "check deadlock {0}: victim {1}",
			       rootRequest, victim);
		}
		LockConflict<K, L> deadlock = new LockConflict<K, L>(
		    rootRequest, LockConflictType.DEADLOCK,
		    conflict.getLocker());
		getWaiterInfo(victim.getLocker()).waitingFor = null;
		victim.getLocker().setConflict(deadlock);
		if (victim.getLocker() == rootRequest.getLocker()) {
		    return deadlock;
		} else {
		    result = new LockConflict<K, L>(
			rootRequest, LockConflictType.BLOCKED,
			conflict.getLocker());
		}
	    }
	}

	/**
	 * Checks for deadlock starting with the specified request and
	 * information about its waiters.  Returns whether a deadlock was
	 * found.
	 */
	private boolean checkInternal(
	    LockRequest<K, L> request, WaiterInfo<K, L> waiterInfo)
	{
	    L locker = request.getLocker();
	    waiterInfo.pass = pass;
	    for (LockRequest<K, L> ownerRequest : waiterInfo.waitingFor) {
		L owner = ownerRequest.locker;
		if (owner == locker) {
		    if (logger.isLoggable(FINEST)) {
			logger.log(FINEST,
				   "checking deadlock {0}, pass {1}:" +
				   " locker {2}, waiting for {3}:" +
				   " ignore self-reference",
				   rootRequest, pass, locker, ownerRequest);
		    }
		} else {
		    WaiterInfo<K, L> ownerInfo = getWaiterInfo(owner);
		    if (ownerInfo.waitingFor == null) {
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " ignore not waiting",
				       rootRequest, pass, locker,
				       ownerRequest);
			}
		    } else if (ownerInfo.pass == pass) {
			/* Found a deadlock! */
			cycleBoundary = ownerRequest;
			victim = ownerRequest;
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " deadlock",
				       rootRequest, pass, locker,
				       ownerRequest);
			}
			return true;
		    } else {
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " recurse",
				       rootRequest, pass, locker,
				       ownerRequest);
			}
			if (checkInternal(ownerRequest, ownerInfo)) {
			    maybeUpdateVictim(ownerRequest);
			    return true;
			}
		    }
		}
	    }
	    return false;
	}

	/**
	 * Returns information about the lockers that the specified locker is
	 * waiting for.
	 */
	private WaiterInfo<K, L> getWaiterInfo(L locker) {
	    WaiterInfo<K, L> waiterInfo = waiterMap.get(locker);
	    if (waiterInfo == null) {
		List<LockRequest<K, L>> waitingFor;
		LockAttemptResult<K, L> result = locker.getWaitingFor();
		if (result == null || locker.getConflict() != null) {
		    waitingFor = null;
		} else {
		    K key = result.request.key;
		    Map<K, Lock<K, L>> keyMap = getKeyMap(key);
		    assert Lock.noteSync(TxnLockManager.this, key);
		    try {
			synchronized (keyMap) {
			    waitingFor = getLock(key, keyMap).copyOwners(
				TxnLockManager.this);
			}
		    } finally {
			assert Lock.noteUnsync(TxnLockManager.this, key);
		    }
		}
		waiterInfo = new WaiterInfo<K, L>(waitingFor);
		waiterMap.put(locker, waiterInfo);
	    }
	    return waiterInfo;
	}

	/**
	 * Updates the victim and conflict fields to reflect an additional
	 * request in the deadlock chain.  Use the argument as the victim if it
	 * has a newer requested start time than the previously selected
	 * victim.
	 */
	private void maybeUpdateVictim(LockRequest<K, L> request) {
	    assert request != null;
	    if (conflict == null) {
		conflict = request;
	    }
	    if (request == cycleBoundary) {
		/* We've gone all the way around the circle, so we're done */
		cycleBoundary = null;
	    } else if (cycleBoundary != null &&
		       (request.getRequestedStartTime() >
			victim.getRequestedStartTime()))
	    {
		/*
		 * We're still within the cycle and this request started later
		 * than the current victim, so use it instead.
		 */
		if (conflict == request) {
		    conflict = victim;
		}
		victim = request;
		logger.log(FINEST,
			   "checking deadlock {0}, pass {1}: new victim: {2}",
			   rootRequest, pass, victim);
	    }
	}
    }

    /**
     * Provides information about the requests a locker is waiting for.  Used
     * in deadlock detection.
     */
    private static class WaiterInfo<K, L extends Locker<K, L>> {

	/**
	 * The requests the locker is waiting for, or {@code null} if not
	 * waiting.
	 */
	List<LockRequest<K, L>> waitingFor;

	/**
	 * The pass in which the locker was checked.  If we encounter an
	 * instance with the current pass number, then we've found a cycle.
	 */
	int pass = 0;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	waitingFor the requests the locker is waiting for
	 */
	WaiterInfo(List<LockRequest<K, L>> waitingFor) {
	    this.waitingFor = waitingFor;
	}
    }
}
