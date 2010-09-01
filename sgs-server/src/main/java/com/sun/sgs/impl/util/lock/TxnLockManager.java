/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.util.lock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import java.util.logging.Logger;

/**
 * A class for managing lock conflicts of a transaction.  Calls on behalf of a
 * locker should only be made from a single thread at a time.  All {@link
 * Locker} objects supplied to this class should be instances of {@link
 * TxnLocker}. <p>
 *
 * This implementation checks for deadlock whenever a lock request is blocked
 * due to a conflict.  It selects as the deadlock victim the locker with the
 * latest requested start time.  The implementation does not deny requests that
 * would not result in deadlock.  When requests block, it services the requests
 * in the order that they arrive. <p>
 *
 * This class and its {@linkplain LockManager superclass} use the {@link
 * Logger} named {@code com.sun.sgs.impl.util.lock} to log information at the
 * following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#FINER FINER} - Releasing locks; requesting, waiting for,
 *	and returning from lock requests
 * <li> {@link Level#FINEST FINEST} - Notifying new lock owners, results of
 *	requesting locks before waiting, releasing locks, results of attempting
 *	to assign locks to waiters
 * </ul>
 *
 * @param	<K> the type of key
 */
public final class TxnLockManager<K> extends LockManager<K> {

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
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}, or if {@code locker} is
     *		not an instance of {@link TxnLocker}
     */
    public LockConflict<K> waitForLock(Locker<K> locker) {
	checkTxnLocker(locker);
	return super.waitForLock(locker);
    }

    /* -- Package access methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation calls the deadlock checker if the request blocks.
     *
     * @throws	IllegalStateException {@inheritDoc}
     */
    @Override
    LockConflict<K> lockNoWaitInternal(
	Locker<K> locker, K key, boolean forWrite)
    {
	checkTxnLocker(locker);
	LockConflict<K> conflict =
	    super.lockNoWaitInternal(locker, key, forWrite);
	if (conflict != null) {
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "lock attempt {0}, {1}, forWrite:{2}" +
			   "\n  returns blocked -- checking for deadlocks",
			   locker, key, forWrite);
	    }
	    try {
		LockConflict<K> deadlockConflict =
		    new DeadlockChecker((TxnLocker<K>) locker).check();
		if (deadlockConflict != null) {
		    if (logger.isLoggable(FINER)) {
			logger.log(FINER,
				   "lock {0}, {1}, forWrite:{2} found" +
				   " deadlock with {3}",
				   locker, key, forWrite, deadlockConflict);
		    }
		    conflict = deadlockConflict;
		}
	    } catch (RuntimeException e) {
		if (logger.isLoggable(FINER)) {
		    logger.logThrow(FINER, e,
				    "lock {0}, {1}, forWrite:{2} throws",
				    locker, key, forWrite);
		}
		throw e;
	    } catch (Error e) {
		if (logger.isLoggable(FINER)) {
		    logger.logThrow(FINER, e,
				    "lock {0}, {1}, forWrite:{2} throws",
				    locker, key, forWrite);
		}
		throw e;
	    }
	}
	return conflict;
    }

    /* -- Other methods -- */

    /** Throws IllegalArgumentException if the argument is not a TxnLocker. */
    private static void checkTxnLocker(Locker<?> locker) {
	if (locker != null && !(locker instanceof TxnLocker<?>)) {
	    throw new IllegalArgumentException("Locker is not a TxnLocker");
	}
    }

    /* -- Other classes -- */

    /** Utility class for detecting deadlocks. */
    private class DeadlockChecker {

	/**
	 * Maps lockers to information about lockers they are waiting for.
	 * This map serves as a cache for information about lock owners, to
	 * avoid the synchronization needed to retrieve it again when checking
	 * for multiple deadlocks.
	 */
	private final Map<TxnLocker<K>, WaiterInfo<K>> waiterMap =
	    new HashMap<TxnLocker<K>, WaiterInfo<K>>();

	/** The top level locker we are checking for deadlocks. */
	private final TxnLocker<K> rootLocker;

	/**
	 * The pass number of the current deadlock check.  There could be
	 * multiple deadlocks active simultaneously, so deadlock checking is
	 * repeated until no deadlocks are found.
	 */
	private int pass;

	/** The locker that was found in a circular reference. */
	private TxnLocker<K> cycleBoundary;

	/** The current choice of a locker to abort. */
	private TxnLocker<K> victim;

	/** Another locker in the deadlock. */
	private TxnLocker<K> conflict;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	locker the locker to check
	 */
	DeadlockChecker(TxnLocker<K> locker) {
	    assert locker != null;
	    rootLocker = locker;
	}

	/**
	 * Checks for a deadlock starting with the root locker.
	 *
	 * @return	a lock conflict if a deadlock was found, else {@code
	 *		null}
	 */
	LockConflict<K> check() {
	    LockConflict<K> result = null;
	    for (pass = 1; true; pass++) {
		if (!checkInternal(rootLocker, getWaiterInfo(rootLocker))) {
		    if (result == null) {
			logger.log(FINEST, "check deadlock {0}: no deadlock",
				   rootLocker);
			return null;
		    } else {
			return result;
		    }
		}
		if (logger.isLoggable(FINER)) {
		    logger.log(FINER, "check deadlock {0}: victim {1}",
			       rootLocker, victim);
		}
		LockConflict<K> deadlock =
		    new LockConflict<K>(LockConflictType.DEADLOCK, conflict);
		getWaiterInfo(victim).waitingFor = null;
		victim.setConflict(deadlock);
		if (victim == rootLocker) {
		    return deadlock;
		} else {
		    result = new LockConflict<K>(
			LockConflictType.BLOCKED, conflict);
		}
	    }
	}

	/**
	 * Checks for deadlock starting with the specified locker and
	 * information about its waiters.  Returns whether a deadlock was
	 * found.
	 */
	private boolean checkInternal(
	    TxnLocker<K> locker, WaiterInfo<K> waiterInfo)
	{
	    if (waiterInfo.waitingFor == null) {
		return false;
	    }
	    waiterInfo.pass = pass;
	    for (LockRequest<K> request : waiterInfo.waitingFor) {
		TxnLocker<K> owner = (TxnLocker<K>) request.getLocker();
		if (owner == locker) {
		    if (logger.isLoggable(FINEST)) {
			logger.log(FINEST,
				   "checking deadlock {0}, pass {1}:" +
				   " locker {2}, waiting for {3}:" +
				   " ignore self-reference",
				   rootLocker, pass, locker, request);
		    }
		} else {
		    WaiterInfo<K> ownerInfo = getWaiterInfo(owner);
		    if (ownerInfo.waitingFor == null) {
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " ignore not waiting",
				       rootLocker, pass, locker, request);
			}
		    } else if (ownerInfo.pass == pass) {
			/* Found a deadlock! */
			cycleBoundary = owner;
			victim = owner;
			conflict = locker;
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " deadlock",
				       rootLocker, pass, locker, request);
			}
			return true;
		    } else {
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "checking deadlock {0}, pass {1}:" +
				       " locker {2}, waiting for {3}:" +
				       " recurse",
				       rootLocker, pass, locker, request);
			}
			if (checkInternal(owner, ownerInfo)) {
			    maybeUpdateVictim(owner);
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
	private WaiterInfo<K> getWaiterInfo(TxnLocker<K> locker) {
	    WaiterInfo<K> waiterInfo = waiterMap.get(locker);
	    if (waiterInfo == null) {
		List<LockRequest<K>> waitingFor;
		LockAttemptResult<K> result = locker.getWaitingFor();
		if (result == null || locker.getConflict() != null) {
		    waitingFor = null;
		} else {
		    K key = result.request.getKey();
		    Map<K, Lock<K>> keyMap = getKeyMap(key);
		    assert TxnLockManager.this.noteKeySync(key);
		    try {
			synchronized (keyMap) {
			    waitingFor = getLock(key, keyMap).copyOwners(
				TxnLockManager.this);
			}
		    } finally {
			assert TxnLockManager.this.noteKeyUnsync(key);
		    }
		}
		waiterInfo = new WaiterInfo<K>(waitingFor);
		waiterMap.put(locker, waiterInfo);
	    }
	    return waiterInfo;
	}

	/**
	 * Updates the victim and conflict fields to reflect an additional
	 * locker in the deadlock chain.  Use the argument as the victim if it
	 * has a newer requested start time than the previously selected
	 * victim.
	 */
	private void maybeUpdateVictim(TxnLocker<K> locker) {
	    assert locker != null;
	    if (conflict == null) {
		conflict = locker;
	    }
	    if (locker == cycleBoundary) {
		/* We've gone all the way around the circle, so we're done */
		cycleBoundary = null;
	    } else if (cycleBoundary != null &&
		       (locker.getRequestedStartTime() >
			victim.getRequestedStartTime()))
	    {
		/*
		 * We're still within the cycle and this locker started later
		 * than the current victim, so use it instead.
		 */
		if (conflict == locker) {
		    conflict = victim;
		}
		victim = locker;
		logger.log(FINEST,
			   "checking deadlock {0}, pass {1}: new victim: {2}",
			   rootLocker, pass, victim);
	    }
	}
    }

    /**
     * Provides information about the requests a locker is waiting for.  Used
     * in deadlock detection.
     */
    private static class WaiterInfo<K> {

	/**
	 * The requests the locker is waiting for, or {@code null} if not
	 * waiting.
	 */
	List<LockRequest<K>> waitingFor;

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
	WaiterInfo(List<LockRequest<K>> waitingFor) {
	    this.waitingFor = waitingFor;
	}
    }
}
