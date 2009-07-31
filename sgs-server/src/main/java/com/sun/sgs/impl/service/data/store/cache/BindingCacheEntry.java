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

/**
 * A cache entry for a name binding.  Only the {@link #key} field may be
 * accessed without holding the associated lock.  For all other fields and
 * methods, the lock should be held.
 */
class BindingCacheEntry extends BasicCacheEntry<BindingKey, Long> {

    /**
     * The earliest previous key such that names between that key and this
     * entry's key are known to be unbound, else {@code null} if no information
     * about previous keys is known.
     */
    private BindingKey previousKey;

    /**
     * Whether the name specified by {@link #previousKey}, if not {@code null},
     * is known to be unbound.
     */
    private boolean previousKeyUnbound;

    /**
     * Whether there is an operation pending for an entry immediately previous
     * to this entry in the cache.
     */
    private boolean pendingPrevious;

    /**
     * Creates a binding cache entry with the specified key and state.
     *
     * @param	key the key
     * @param	state the state
     */
    private BindingCacheEntry(BindingKey key, State state) {
	super(key, state);
    }

    /**
     * Creates a binding cache entry to represent a name binding being cached
     * on behalf of a transaction.
     *
     * @param	key the key
     * @param	value the cached value
     * @param	forUpdate whether the value is cached for update
     * @param	contextId the context ID associated with the transaction
     */
    static BindingCacheEntry createCached(
	BindingKey key, long value, boolean forUpdate, long contextId)
    {
	BindingCacheEntry entry = new BindingCacheEntry(
	    key, forUpdate ? State.CACHED_WRITE : State.CACHED_READ);
	entry.setValue(value);
	entry.noteAccess(contextId);
	return entry;
    }

    /**
     * Creates a binding cache entry to represent the last name binding that is
     * being fetched from the server.
     *
     * @param	forUpdate whether the last name is being fetched for update
     */
    static BindingCacheEntry createLast(boolean forUpdate) {
	return new BindingCacheEntry(
	    BindingKey.LAST,
	    forUpdate ? State.FETCHING_WRITE : State.FETCHING_READ);
    }

    @Override
    public String toString() {
	return "BindingCacheEntry[" +
	    "name:" + key +
	    ", value:" + getValue() +
	    ", contextId:" + getContextId() +
	    ", state:" + getState() +
	    ", previousKey:" + previousKey +
	    (previousKey == null ? "" :
	     ", previousKeyUnbound:" + previousKeyUnbound) +
	    ", pendingPrevious:" + pendingPrevious +
	    "]";
    }

    /**
     * Updates information about previous keys that are known to be unbound.
     * The {@code newPreviousKey} argument specifies a name for which it is
     * known that all names between that name and the one associated with this
     * entry are known to be unbound.  If the {@code newPreviousKeyUnbound} is
     * {@code true}, then that name is also known to be unbound.
     *
     * @param	newPreviousKey the new previous key
     * @param	newPreviousKeyUnbound whether {@code newPreviousKey} is known
     *		to be unbound
     * @return	whether information stored about the previous key was changed
     */
    boolean updatePreviousKey(
	BindingKey newPreviousKey, boolean newPreviousKeyUnbound)
    {
	if (previousKey == null) {
	    if (newPreviousKey.compareTo(key) < 0) {
		/*
		 * No previous key was known, and the argument is before this
		 * entry's key.
		 */
		previousKey = newPreviousKey;
		previousKeyUnbound = newPreviousKeyUnbound;
		return true;
	    }
	} else {
	    int compareTo = newPreviousKey.compareTo(previousKey);
	    if (compareTo < 0) {
		/* New previous key is earlier than previous one */
		previousKey = newPreviousKey;
		previousKeyUnbound = newPreviousKeyUnbound;
		return true;
	    } else if (compareTo == 0 &&
		       !previousKeyUnbound &&
		       newPreviousKeyUnbound)
	    {
		/*
		 * Previous key is the same, but it was not previously known
		 * that the previous key itself was unbound
		 */
		previousKeyUnbound = true;
		return true;
	    }
	}
	/* No change */
	return false;
    }

    /**
     * Returns whether the specified key is known to be unbound.
     *
     * @return	whether the specified key is known to be unbound
     */
    boolean getKnownUnbound(BindingKey forKey) {
	if (previousKey == null) {
	    /* No information about previous keys */
	    return false;
	} else if (key.compareTo(forKey) <= 0) {
	    /* Requested key is greater than or equal to this entry */
	    return false;
	}
	int compare = previousKey.compareTo(forKey);
	/*
	 * Check if requested key is greater than the previous key, or equal
	 * and we know that key is unbound
	 */
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
	    previousKey.compareTo(forKey) <= 0;
    }

    /**
     * Returns the earliest previous key such that names between that key and
     * this entry's key are known to be unbound, else {@code null} if no
     * information about previous keys is known.
     *
     * @return	the previous key or {@code null}
     */
    BindingKey getPreviousKey() {
	return previousKey;
    }

    /**
     * Returns whether the name for the key returned by {@link
     * #getPreviousKey}, if not {@code null}, is known to be unbound.
     *
     * @return	whether the previous key is known to be unbound
     */
    boolean getPreviousKeyUnbound() {
	return previousKeyUnbound;
    }

    /**
     * Sets information about previous known unbound keys, ignoring any
     * currently stored information.
     *
     * @param	previousKey the new previous key
     * @param	previousKeyUnbound whether the new previous key is known to be
     *		unbound
     */
    void setPreviousKey(BindingKey previousKey, boolean previousKeyUnbound) {
	this.previousKey = previousKey;
	this.previousKeyUnbound = previousKeyUnbound;
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
	long start = System.currentTimeMillis();
	long now = start;
	while (now < stop) {
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
	}
	throw new TransactionTimeoutException(
	    "Timeout after " + (now - start) +
	    " ms waiting for lock: " + this);
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
