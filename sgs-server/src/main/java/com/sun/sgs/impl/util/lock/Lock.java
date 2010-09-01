/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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
     * Otherwise, returns a {@code LockAttemptResult} containing the {@link
     * LockRequest}, with its {@code conflict} field set to {@code null} if the
     * lock was acquired or a conflicting transaction if the lock could not be
     * obtained.  Adds the locker to the waiters list if the lock was not
     * obtained and the conflict type was {@link LockConflictType#BLOCKED
     * BLOCKED}.
     *
     * @param	locker the locker requesting the lock
     * @param	forWrite whether a write lock is requested
     * @param	waiting whether the locker is known to be a waiter
     * @return	a {@code LockAttemptResult} or {@code null}
     */
    LockAttemptResult<K> lock(
	Locker<K> locker, boolean forWrite, boolean waiting)
    {
	assert locker.lockManager.checkKeySync(key);
	boolean upgrade = false;
	Locker<K> conflict = null;
	LockConflictType conflictType = null;
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
		    conflictType = LockConflictType.BLOCKED;
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
		    if (forWrite != waiterRequest.getForWrite()) {
			/*
			 * Using the multi-lock manager, the locker has already
			 * made a blocked request for the same lock, but one
			 * request is for read and the other for write.  This
			 * case probably only happens in practice due to
			 * network failures since the caching data store does
			 * not make such requests otherwise.  This case is
			 * obscure enough that it is not worth supporting more
			 * complex waiters cases -- just reject the new
			 * request.
			 */
			conflict = locker;
			conflictType = LockConflictType.DENIED;
		    } else {
			request = waiterRequest;
			if (conflict == null) {
			    i.remove();
			}
		    }
		    break;
		} else if (conflict == null &&
			   (forWrite || waiterRequest.getForWrite()))
		{
		    /* Found a conflicting waiter */
		    conflict = waiterRequest.getLocker();
		    conflictType = LockConflictType.BLOCKED;
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
	    if (conflictType == LockConflictType.BLOCKED) {
		addWaiter(request);
	    }
	}
	assert validateInUse();
	return new LockAttemptResult<K>(request, conflict, conflictType);
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
     * Releases the ownership of this lock by the locker.  Attempts to acquire
     * locks for current waiters, removing the ones that acquire the lock from
     * the waiters list.  Also removes the current locker from the waiters list
     * if it was waiting to upgrade -- only happens in the multi-locker case.
     * Returns a list of lockers that should be notified, including the new
     * owners and the current locker if it was upgrading.
     *
     * @param	locker the locker whose ownership will be released
     * @param	downgrade whether to downgrade ownership from write to read
     *		rather than releasing all ownership
     * @return	the lockers that should be notified
     */
    List<Locker<K>> release(Locker<K> locker, boolean downgrade) {
	assert locker.lockManager.checkKeySync(key);
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
	List<Locker<K>> lockersToNotify = null;
	if (owned && !waiters.isEmpty()) {
	    boolean conflict = false;
	    for (int i = 0; i < waiters.size(); i++) {
		LockRequest<K> waiter = waiters.get(i);
		if (waiter.getLocker() == locker) {
		    /*
		     * The owner was also waiting to upgrade -- only happens in
		     * for a multi-locker where waiting and releasing can
		     * happen concurrently.
		     */
		    assert waiter.getUpgrade();
		    waiters.remove(i);
		    i--;
		    if (lockersToNotify == null) {
			lockersToNotify = new ArrayList<Locker<K> >();
		    }
		    lockersToNotify.add(locker);
		} else if (!conflict) {
		    LockAttemptResult<K> result =
			lock(waiter.getLocker(), waiter.getForWrite(), true);
		    if (logger.isLoggable(FINEST)) {
			logger.log(FINEST,
				   "attempt to lock waiter {0} returns {1}",
				   waiter, result);
		    }
		    /*
		     * Stop granting locks when the first conflict is detected.
		     */
		    if (result != null && result.conflict != null) {
			conflict = true;
		    } else {
			/* Back up because this waiter was removed */
			i--;
			if (lockersToNotify == null) {
			    lockersToNotify = new ArrayList<Locker<K>>();
			}
			lockersToNotify.add(waiter.getLocker());
		    }
		}
	    }
	}
	assert !inUse(locker.lockManager) || validateInUse();
	if (lockersToNotify == null) {
	    lockersToNotify = Collections.emptyList();
	}
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
     * <li>if more than one owner, they are only read owners
     * <li>all upgrade waiters precede other waiters
     * <li>locker appears once in owners and waiters <em>except</em> that
     *     an upgrade request can be a waiter if a read request is an owner
     * <li>no upgrade waiters if the locker is not a read owner
     * </ul>
     *
     * @return	{@code true} if the state is valid, otherwise throws
     *		{@link AssertionError}
     */
    private boolean validateInUse() {
	int numWaiters = waiters.size();
	int numOwners = owners.size();
	if (numOwners == 0) {
	    throw new AssertionError("No owners: " + this);
	}
	if (numOwners > 1) {
	    for (LockRequest<K> owner : owners) {
		if (owner.getForWrite()) {
		    throw new AssertionError(
			"Owned for write with more than one owner: " + this +
			", owners:" + owners);
		}
	    }
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
	assert lockManager.checkKeySync(key);
	return !owners.isEmpty() || !waiters.isEmpty();
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the owners.
     */
    List<LockRequest<K>> copyOwners(LockManager<K> lockManager) {
	assert lockManager.checkKeySync(key);
	return copyList(owners);
    }

    /**
     * Returns a possibly read-only copy of the lock requests for the
     * waiters.
     */
    List<LockRequest<K>> copyWaiters(LockManager<K> lockManager) {
	assert lockManager.checkKeySync(key);
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

    /** Removes a locker from the list of waiters for this lock, if present. */
    void flushWaiter(Locker<K> locker) {
	assert locker.lockManager.checkKeySync(key);
	for (Iterator<LockRequest<K>> iter = waiters.iterator();
	     iter.hasNext(); )
	{
	    LockRequest<K> request = iter.next();
	    if (request.getLocker() == locker) {
		iter.remove();
		return;
	    }
	}
    }

    /**
     * Returns the request with the specified locker that owns the lock, or
     * {@code null} if none is found.
     *
     * @param	locker the locker
     * @return	the owner with the specified locker, or {@code null}
     */
    LockRequest<K> getOwner(Locker<K> locker) {
	assert locker.lockManager.checkKeySync(key);
	for (LockRequest<K> owner : owners) {
	    if (locker == owner.getLocker()) {
		return owner;
	    }
	}
	return null;
    }

    /** Print fields, for debugging. */
    @Override
    public String toString() {
	return "Lock[" + key + "]";
    }
}
