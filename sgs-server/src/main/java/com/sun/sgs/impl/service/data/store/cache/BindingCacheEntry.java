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

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.service.TransactionInterruptedException;

/** A cache entry for a name binding. */
class BindingCacheEntry extends BasicCacheEntry<BindingKey, Long> {

    /** The previous key, if known, else {@code null}. */
    private BindingKey previousKey;

    /** Whether the previous known key is unbound. */
    private boolean previousKeyUnbound;

    /**
     * Whether there is an operation pending for an entry immediately previous
     * to this entry in the cache.
     */
    private boolean pendingPrevious;

    /**
     * Creates a binding cache entry to represent a name binding being cached
     * on behalf of a transaction.
     *
     * @param	key the key
     * @param	value the cached value
     * @param	forUpdate whether the value is cached for update
     * @param	contextId the context ID associated with the transaction
     */
    BindingCacheEntry(
	BindingKey key, long value, boolean forUpdate, long contextId)
    {
	super(key, forUpdate ? State.CACHED_WRITE : State.CACHED_READ);
	setValue(value);
	noteAccess(contextId);
    }

    /**
     * Updates information about the previous known key.
     *
     * @param	newPreviousKey the new previous key
     * @param	unbound whether {@code newPreviousKey} is unbound
     * @return	whether information stored about the previous key was changed
     */
    boolean updatePreviousKey(BindingKey newPreviousKey, boolean unbound) {
	if (previousKey == null) {
	    if (newPreviousKey.compareTo(key) < 0) {
		/*
		 * No previous key was known, and the argument is before this
		 * entry's key.
		 */
		previousKey = newPreviousKey;
		previousKeyUnbound = unbound;
		return true;
	    }
	} else {
	    int compareTo = newPreviousKey.compareTo(previousKey);
	    if (compareTo < 0) {
		/* New previous key is earlier than previous one */
		previousKey = newPreviousKey;
		previousKeyUnbound = unbound;
		return true;
	    } else if (compareTo == 0 && previousKeyUnbound != unbound) {
		/*
		 * Previous key is the same, but whether it is unbound has
		 * changed
		 */
		previousKeyUnbound = unbound;
		return true;
	    }
	}
	return false;
    }

    /**
     * Returns whether the specified key is known to be unbound.
     *
     * @return	whether the specified key is known to be unbound
     */
    boolean getKnownUnbound(BindingKey forKey) {
	if (previousKey == null || key.compareTo(forKey) <= 0) {
	    return false;
	}
	int compare = previousKey.compareTo(forKey);
	return compare < 0 || (compare == 0 && previousKeyUnbound);
    }

    /**
     * Returns whether this entry is known to be the next entry in the cache
     * after the specified key.  Note that this entry does not necessarily
     * represent a bound name, in which case this entry would not represent the
     * next bound name after the specified key.
     *
     * @param	forKey the key to check
     * @return	whether this entry is known to be the next entry in the cache
     *		after {@code forKey}
     */
    boolean getIsNextEntry(BindingKey forKey) {
	assert forKey != null;
	return forKey.compareTo(key) < 0 &&
	    previousKey != null &&
	    previousKey.compareTo(forKey) >= 0;
    }

    /**
     * Waits for the pending operation, if any, for an entry immediately
     * previous to this entry in the cache to complete.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if there is no pending operation for a
     *		previous entry
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitNotPendingPrevious(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (!pendingPrevious) {
	    return;
	}
	long now = System.currentTimeMillis();
	while (true) {
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for lock: " + this, e);
	    }
	    if (!pendingPrevious) {
		return;
	    }
	    now = System.currentTimeMillis();
	    if (now >= stop) {
		throw new TransactionTimeoutException(
		    "Timeout waiting for lock: " + this);
	    }
	}
    }

    /**
     * Returns whether there is an operation pending for an entry immediately
     * previous to this entry in the cache.
     *
     * @return	whether there is an operation pending for a previous entry
     */
    boolean getPendingPrevious() {
	return pendingPrevious;
    }

    /**
     * Notes that there is an operation pending for an entry immediately
     * previous to this entry in the cache.
     *
     * @throws	IllegalStateException if there is an already pending operation
     *		for a previous entry
     */
    void setPendingPrevious() {
	if (pendingPrevious) {
	    throw new IllegalStateException("Already pending previous");
	}
	pendingPrevious = true;
    }

    /**
     * Notes that the operation pending for an entry immediately previous to
     * this entry in the cache is complete, and notifies the lock, which should
     * be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if there is no pending operation for a
     *		previous entry
     */
    void setNotPendingPrevious(Object lock) {
	assert Thread.holdsLock(lock);
	if (!pendingPrevious) {
	    throw new IllegalStateException("Not pending previous");
	}
	pendingPrevious = false;
	lock.notifyAll();
    }
}
