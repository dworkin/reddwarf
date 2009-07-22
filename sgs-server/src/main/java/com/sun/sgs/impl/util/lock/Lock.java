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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class used to represent locks. <p>
 *
 * Callers should only call non-{@code Object} methods on instances of this
 * class if they hold the lock on the key map associated with the instance.
 *
 * @param	<K> the type of key
 * @param	<L> the type of locker
 * @see		LockManager
 */
final class Lock<K, L extends Locker<K, L>> {

    /** The logger for this class. */
    private static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(LockManager.class.getName()));

    /** An empty array of lock requests. */
    private static final LockRequest<?, ?>[] NO_LOCK_REQUESTS = { };

    /** The key that identifies this lock. */
    final K key;

    /**
     * The requests that currently own this lock.  Use a small initial
     * size, since the number of owners is typically small.
     */
    private final List<LockRequest<K, L>> owners =
	new ArrayList<LockRequest<K, L>>(2);

    /**
     * The requests that are waiting for this lock.  Use a small initial
     * size, since the number of owners is typically small.
     */
    private final List<LockRequest<K, L>> waiters =
	new ArrayList<LockRequest<K, L>>(2);

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
     * Attempts to obtain this lock.  If {@code waiting} is {@code true},
     * the locker is already waiting for the lock.  Adds the locker as an
     * owner of the lock if the lock was obtained, and removes the locker
     * from the waiters list if it was waiting.  Returns {@code null} if
     * the locker already owned this lock.  Otherwise, returns a {@code
     * LockAttemptResult} containing the {@link LockRequest}, and with the
     * {@code conflict} field set to {@code null} if the lock was acquired,
     * else set to a conflicting transaction if the lock could not be
     * obtained.
     *
     * @param	locker the locker requesting the lock
     * @param	forWrite whether a write lock is requested
     * @param	waiting whether the locker is already a waiter
     * @return	a {@code LockAttemptResult} or {@code null}
     */
    LockAttemptResult<K, L> lock(L locker, boolean forWrite, boolean waiting) {
	assert checkSync(locker.lockManager);
	L conflict = null;
	boolean upgrade = false;
	if (!owners.isEmpty()) {
	    for (LockRequest<K, L> ownerRequest : owners) {
		if (locker == ownerRequest.locker) {
		    if (forWrite && !ownerRequest.getForWrite()) {
			upgrade = true;
		    } else {
			/* Already locked */
			assert validateInUse();
			return null;
		    }
		} else if (forWrite || ownerRequest.getForWrite()) {
		    /* Found conflict */
		    conflict = ownerRequest.locker;
		    break;
		} else if (!waiters.isEmpty()) {
		    /* Reads need to wait for already waiting writes */
		    conflict = waiters.get(0).locker;
		    break;
		}
	    }
	    if (conflict == null && upgrade) {
		/* Remove read lock request */
		boolean found = false;
		for (Iterator<LockRequest<K, L>> i = owners.iterator();
		     i.hasNext(); )
		{
		    LockRequest<K, L> ownerRequest = i.next();
		    if (locker == ownerRequest.locker) {
			assert !ownerRequest.getForWrite()
			    : "Should own for read when upgrading";
			i.remove();
			found = true;
			break;
		    }
		}
		assert found : "Should own when upgrading";
	    }
	}
	LockRequest<K, L> request =
	    (conflict == null && waiting) ? flushWaiter(locker)
	    : locker.newLockRequest(key, forWrite, upgrade);
	if (conflict == null) {
	    owners.add(request);
	} else if (!waiting) {
	    addWaiter(request);
	}
	assert validateInUse();
	return new LockAttemptResult<K, L>(request, conflict);
    }

    /**
     * Adds a lock request to the list of requests waiting for this lock.
     * If this is an upgrade request, puts the request after any other
     * upgrade requests, but before other requests.  Otherwise, puts the
     * request at the end of the list.
     */
    private void addWaiter(LockRequest<K, L> request) {
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
    List<L> release(L locker, boolean downgrade) {
	assert checkSync(locker.lockManager);
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "release {0}, downgrade:{1}, {2}",
		       locker, downgrade, this);
	}
	boolean owned = false;
	for (Iterator<LockRequest<K, L>> i = owners.iterator();
	     i.hasNext(); )
	{
	    LockRequest<K, L> ownerRequest = i.next();
	    if (locker == ownerRequest.locker) {
		if (!downgrade || ownerRequest.getForWrite()) {
		    i.remove();
		    owned = true;
		    if (downgrade) {
			/* FIXME: Note downgrade; what if not supported? */
			owners.add(
			    locker.newLockRequest(
				ownerRequest.key, false, false));
		    }
		}
		break;
	    }
	}
	List<L> lockersToNotify = Collections.emptyList();
	if (owned && !waiters.isEmpty()) {
	    boolean found = false;
	    for (int i = 0; i < waiters.size(); i++) {
		LockRequest<K, L> waiter = waiters.get(i);
		LockAttemptResult<K, L> result =
		    lock(waiter.locker, waiter.getForWrite(), true);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "attempt to lock waiter {0} returns {1}",
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
		    lockersToNotify = new ArrayList<L>();
		}
		lockersToNotify.add(waiter.locker);
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
	    LockRequest<K, L> waiter = waiters.get(i);
	    if (!waiter.getUpgrade()) {
		seenNonUpgrade = true;
	    } else if (seenNonUpgrade) {
		throw new AssertionError(
		    "Upgrade waiter follows non-upgrade: " + this +
		    ", waiters: " + waiters);
	    }
	    for (int j = i + 1; j < numWaiters; j++) {
		LockRequest<K, L> waiter2 = waiters.get(j);
		if (waiter.locker == waiter2.locker) {
		    throw new AssertionError(
			"Locker waits twice: " + this + ", " +
			waiter + ", " + waiter2);
		}
	    }
	    boolean foundOwner = false;
	    for (int j = 0; j < numOwners; j++) {
		LockRequest<K, L> owner = owners.get(j);
		if (waiter.locker == owner.locker) {
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
    boolean inUse(LockManager<K, L> lockManager) {
	assert checkSync(lockManager);
	return !owners.isEmpty() || !waiters.isEmpty();
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the owners.
     */
    List<LockRequest<K, L>> copyOwners(LockManager<K, L> lockManager) {
	assert checkSync(lockManager);
	return copyList(owners);
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the
     * waiters.
     */
    List<LockRequest<K, L>> copyWaiters(LockManager<K, L> lockManager) {
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
     * Removes a locker from the list of waiters for this lock and returns the
     * lock request, which must be present.
     */
    LockRequest<K, L> flushWaiter(L locker) {
	assert checkSync(locker.lockManager);
	for (Iterator<LockRequest<K, L>> iter = waiters.iterator();
	     iter.hasNext(); )
	{
	    LockRequest<K, L> request = iter.next();
	    if (request.locker == locker) {
		iter.remove();
		return request;
	    }
	}
	throw new AssertionError("Waiter was not found: " + locker);
    }

    /**
     * Checks if the lock is already owned in a way the satisfies the
     * specified request.
     */
    boolean isOwner(LockRequest<K, L> request) {
	assert checkSync(request.locker.lockManager);
	for (LockRequest<K, L> owner : owners) {
	    if (request.locker == owner.locker) {
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
    static <K, L extends Locker<K, L>> boolean noteSync(
	LockManager<K, L> lockManager, K key)
    {
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
    static <K, L extends Locker<K, L>> boolean noteUnsync(
	LockManager<K, L> lockManager, K key)
    {
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
    static <K, L extends Locker<K, L>> boolean checkNoSync(
	LockManager<K, L> lockManager)
    {
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
    boolean checkSync(LockManager<K, L> lockManager) {
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
