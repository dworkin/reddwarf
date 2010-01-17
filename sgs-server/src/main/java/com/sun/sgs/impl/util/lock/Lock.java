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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static java.util.logging.Level.FINEST;
import java.util.logging.Logger;

/**
 * A class used to represent locks. <p>
 *
 * Callers should only call non-{@code Object} methods on instances of this
 * class if they hold the lock on the key map associated with the instance.
 *
 * @param	<K> the type of key
 * @see		LockManager
 */
final class Lock<K> {

    /** The logger for this class. */
    private static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(LockManager.class.getName()));

    /** An empty array of lock requests. */
    private static final LockRequest<?>[] NO_LOCK_REQUESTS = { };

    /** The key that identifies this lock. */
    final K key;

    /**
     * The requests that currently own this lock.  Use a small initial
     * size, since the number of owners is typically small.
     */
    private final List<LockRequest<K>> owners =
	new ArrayList<LockRequest<K>>(2);

    /**
     * The requests that are waiting for this lock.  Use a small initial
     * size, since the number of owners is typically small.
     */
    private final List<LockRequest<K>> waiters =
	new ArrayList<LockRequest<K>>(2);

    /**
     * Creates a lock.
     *
     * @param	key the key that identifies this lock
     */
    Lock(K key) {
	checkNull("key", key);
	this.key = key;
    }

    /**
     * Attempts to obtain this lock.  If {@code waiting} is {@code true}, the
     * locker is known to be waiting for the lock, although it may also be
     * waiting otherwise.  Adds the locker as an owner of the lock if the lock
     * was obtained, and removes the locker from the waiters list if it was
     * waiting.  Returns {@code null} if the locker already owned this lock.
     * Otherwise, adds the locker to the waiters list, and returns a {@code
     * LockAttemptResult} containing the {@link LockRequest}, with its {@code
     * conflict} field set to {@code null} if the lock was acquired or a
     * conflicting transaction if the lock could not be obtained.
     *
     * @param	locker the locker requesting the lock
     * @param	forWrite whether a write lock is requested
     * @param	waiting whether the locker is known to be a waiter
     * @return	a {@code LockAttemptResult} or {@code null}
     */
    LockAttemptResult<K> lock(
	Locker<K> locker, boolean forWrite, boolean waiting)
    {
	assert checkSync(locker.lockManager);
	boolean upgrade = false;
	Locker<K> conflict = null;
	if (!owners.isEmpty()) {
	    /* Check conflicting owners */
	    for (LockRequest<K> ownerRequest : owners) {
		if (locker == ownerRequest.getLocker()) {
		    if (!forWrite || ownerRequest.getForWrite()) {
			/* Already locked */
			assert validateInUse();
			return null;
		    } else {
			/* Upgrade */
			upgrade = true;
		    }
		} else if (forWrite || ownerRequest.getForWrite()) {
		    /* Found conflict */
		    conflict = ownerRequest.getLocker();
		}
	    }
	}
	LockRequest<K> request = null;
	if (!waiters.isEmpty()) {
	    /* Check waiters for conflicts or already waiting */
	    for (Iterator<LockRequest<K>> i = waiters.iterator();
		 i.hasNext(); )
	    {
		LockRequest<K> waiterRequest = i.next();
		if (locker == waiterRequest.getLocker()) {
		    assert forWrite == waiterRequest.getForWrite();
		    request = waiterRequest;
		    if (conflict == null) {
			i.remove();
		    }
		    break;
		} else if (conflict == null &&
			   (forWrite || waiterRequest.getForWrite()))
		{
		    /* Found a conflicting waiter */
		    conflict = waiterRequest.getLocker();
		}
	    }
	}
	assert !waiting || request != null : "Should have found waiter";
	if (conflict == null && upgrade) {
	    /* Upgrading -- remove the read lock request from owners */
	    assert owners.size() == 1 && owners.get(0).getLocker() == locker;
	    owners.remove(0);
	}
	if (conflict == null) {
	    if (request == null) {
		request = locker.newLockRequest(key, forWrite, upgrade);
	    }
	    owners.add(request);
	} else if (request == null) {
	    request = locker.newLockRequest(key, forWrite, upgrade);
	    addWaiter(request);
	}
	assert validateInUse();
	return new LockAttemptResult<K>(request, conflict);
    }

    /**
     * Adds a lock request to the list of requests waiting for this lock.
     * If this is an upgrade request, puts the request after any other
     * upgrade requests, but before other requests.  Otherwise, puts the
     * request at the end of the list.
     */
    private void addWaiter(LockRequest<K> request) {
	if (waiters.isEmpty() || !request.getUpgrade()) {
	    waiters.add(request);
	} else {
	    /*
	     * Add upgrade requests after any existing ones, but before
	     * other requests.
	     */
	    boolean added = false;
	    for (int i = 0; i < waiters.size(); i++) {
		if (!waiters.get(i).getUpgrade()) {
		    waiters.add(i, request);
		    added = true;
		    break;
		}
	    }
	    if (!added) {
		waiters.add(request);
	    }
	}
    }

    /**
     * Releases the ownership of this lock by the locker.  Attempts to
     * acquire locks for current waiters, removing the ones that acquire
     * the lock from the waiters list, adding them to the owners, and
     * returning them.
     *
     * @param	locker the locker whose ownership will be released
     * @param	downgrade whether to downgrade ownership from write to read
     *		rather than releasing all ownership
     * @return	the newly added owners
     */
    List<Locker<K>> release(Locker<K> locker, boolean downgrade) {
	assert checkSync(locker.lockManager);
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "release {0}, downgrade:{1}, {2}",
		       locker, downgrade, this);
	}
	boolean owned = false;
	for (Iterator<LockRequest<K>> i = owners.iterator(); i.hasNext(); ) {
	    LockRequest<K> ownerRequest = i.next();
	    if (locker == ownerRequest.getLocker()) {
		if (!downgrade || ownerRequest.getForWrite()) {
		    i.remove();
		    owned = true;
		    if (downgrade) {
			owners.add(
			    locker.newLockRequest(
				ownerRequest.getKey(), false, false));
		    }
		}
		break;
	    }
	}
	List<Locker<K>> lockersToNotify = Collections.emptyList();
	if (owned && !waiters.isEmpty()) {
	    boolean found = false;
	    for (int i = 0; i < waiters.size(); i++) {
		LockRequest<K> waiter = waiters.get(i);
		LockAttemptResult<K> result =
		    lock(waiter.getLocker(), waiter.getForWrite(), true);
		if (logger.isLoggable(FINEST)) {
		    logger.log(FINEST,
			       "attempt to lock waiter {0} returns {1}",
			       waiter, result);
		}
		/*
		 * Stop when the first conflict is detected.  This
		 * implementation always services waiters in order, so
		 * there is no reason to look further after we've found a
		 * waiter that is still blocked.
		 */
		if (result != null && result.conflict != null) {
		    break;
		}
		/* Back up because this waiter was removed */
		i--;
		if (!found) {
		    found = true;
		    lockersToNotify = new ArrayList<Locker<K>>();
		}
		lockersToNotify.add(waiter.getLocker());
	    }
	}
	assert !inUse(locker.lockManager) || validateInUse();
	return lockersToNotify;
    }

    /**
     * Validates the state of this lock, given that it is known to be in
     * use. <p>
     *
     * Locks that are in use must satisfy the following consistency
     * constraints:
     * <ul>
     * <li>at least one owner
     * <li>all upgrade waiters precede other waiters
     * <li>locker appears once in owners and waiters <em>except</em> that
     *     an upgrade request can be a waiter if a read request is an owner
     * <li>no upgrade waiters if the locker is not a read owner
     * </ul>
     *
     * @return	{@code true} if the state is valid, otherwise throws
     *		{@link AssertionError}
     */
    /** {@inheritDoc} */
    private boolean validateInUse() {
	int numWaiters = waiters.size();
	int numOwners = owners.size();
	if (numOwners == 0) {
	    throw new AssertionError("No owners: " + this);
	}
	boolean seenNonUpgrade = false;
	for (int i = 0; i < numWaiters - 1; i++) {
	    LockRequest<K> waiter = waiters.get(i);
	    if (!waiter.getUpgrade()) {
		seenNonUpgrade = true;
	    } else if (seenNonUpgrade) {
		throw new AssertionError(
		    "Upgrade waiter follows non-upgrade: " + this +
		    ", waiters: " + waiters);
	    }
	    for (int j = i + 1; j < numWaiters; j++) {
		LockRequest<K> waiter2 = waiters.get(j);
		if (waiter.getLocker() == waiter2.getLocker()) {
		    throw new AssertionError(
			"Locker waits twice: " + this + ", " +
			waiter + ", " + waiter2);
		}
	    }
	    boolean foundOwner = false;
	    for (int j = 0; j < numOwners; j++) {
		LockRequest<K> owner = owners.get(j);
		if (waiter.getLocker() == owner.getLocker()) {
		    foundOwner = true;
		    if (!waiter.getUpgrade()) {
			throw new AssertionError(
			    "Locker owns and waits, but not for" +
			    " upgrade: " + this + ", owner:" + owner +
			    ", waiter:" + waiter);
		    } else if (owner.getForWrite()) {
			throw new AssertionError(
			    "Locker owns for write but waits for" +
			    " upgrade: " + this + ", owner:" + owner +
			    ", waiter:" + waiter);
		    }
		}
	    }
	    if (waiter.getUpgrade() && !foundOwner) {
		throw new AssertionError(
		    "Waiting for upgrade but not owner: " + this +
		    ", waiter: " + waiter);
	    }
	}
	return true;
    }

    /**
     * Returns whether this lock is currently in use, or whether it is not
     * in use and can be removed from the lock table.  Locks are in use if
     * they have any owners or waiters.
     */
    boolean inUse(LockManager<K> lockManager) {
	assert checkSync(lockManager);
	return !owners.isEmpty() || !waiters.isEmpty();
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the owners.
     */
    List<LockRequest<K>> copyOwners(LockManager<K> lockManager) {
	assert checkSync(lockManager);
	return copyList(owners);
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the
     * waiters.
     */
    List<LockRequest<K>> copyWaiters(LockManager<K> lockManager) {
	assert checkSync(lockManager);
	return copyList(waiters);
    }

    /**
     * Returns a possibly read-only copy of a list, optimized to save space in
     * the empty and single-element cases.
     */
    private static <E> List<E> copyList(List<E> list) {
	if (list.isEmpty()) {
	    return Collections.emptyList();
	} else if (list.size() == 1) {
	    return Collections.singletonList(list.get(0));
	} else {
	    return new ArrayList<E>(list);
	}
    }

    /**
     * Removes a locker from the list of waiters for this lock.  The locker
     * must be present in the list.
     */
    void flushWaiter(Locker<K> locker) {
	assert checkSync(locker.lockManager);
	for (Iterator<LockRequest<K>> iter = waiters.iterator();
	     iter.hasNext(); )
	{
	    LockRequest<K> request = iter.next();
	    if (request.getLocker() == locker) {
		iter.remove();
		return;
	    }
	}
	throw new AssertionError("Waiter was not found: " + locker);
    }

    /**
     * Checks if the lock is already owned in a way the satisfies the
     * specified request.
     */
    boolean isOwner(LockRequest<K> request) {
	assert checkSync(request.getLocker().lockManager);
	for (LockRequest<K> owner : owners) {
	    if (request.getLocker() == owner.getLocker()) {
		return !request.getForWrite() || owner.getForWrite();
	    }
	}
	return false;
    }

    /**
     * Notes the start of synchronization on the map associated with {@code
     * key}.  Throws {@link AssertionError} if already synchronized on a
     * key, otherwise returns {@code true}.
     */
    static <K> boolean noteSync(LockManager<K> lockManager, K key) {
	K currentKey = lockManager.currentKeySync.get();
	if (currentKey != null) {
	    throw new AssertionError(
		"Attempt to synchronize on map for key " + key +
		", but already synchronized on " + currentKey);
	}
	lockManager.currentKeySync.set(key);
	return true;
    }

    /**
     * Notes the end of synchronization on the map associated with {@code
     * key}.  Throws {@link AssertionError} if not already synchronized on
     * {@code key}, otherwise returns {@code true}.
     */
    static <K> boolean noteUnsync(LockManager<K> lockManager, K key) {
	K currentKey = lockManager.currentKeySync.get();
	if (currentKey == null) {
	    throw new AssertionError(
		"Attempt to unsynchronize on map for key " + key +
		", but not currently synchronized on a key");
	} else if (!currentKey.equals(key)) {
	    throw new AssertionError(
		"Attempt to unsynchronize on map for key " + key +
		", but currently synchronized on " + currentKey);
	}
	lockManager.currentKeySync.remove();
	return true;
    }

    /**
     * Checks that the current thread is not synchronized on the map
     * associated with a key, throwing {@link AssertionError} if it is, and
     * otherwise returning {@code true}.
     */
    static <K> boolean checkNoSync(LockManager<K> lockManager) {
	K currentKey = lockManager.currentKeySync.get();
	if (currentKey != null) {
	    throw new AssertionError(
		"Currently synchronized on key " + currentKey);
	}
	return true;
    }

    /**
     * Checks that the current thread is synchronized on the map associated
     * with this lock, throwing {@link AssertionError} if it is not, and
     * otherwise returning {@code true}.
     */
    boolean checkSync(LockManager<K> lockManager) {
	K currentKey = lockManager.currentKeySync.get();
	if (currentKey == null) {
	    throw new AssertionError("Currently not synchronized on a key");
	} else if (!currentKey.equals(key)) {
	    throw new AssertionError(
		"Should be synchronized on " + key +
		", but currently synchronized on " + currentKey);
	}
	return true;
    }

    /** Print fields, for debugging. */
    @Override
    public String toString() {
	return "Lock[" + key + "]";
    }
}
