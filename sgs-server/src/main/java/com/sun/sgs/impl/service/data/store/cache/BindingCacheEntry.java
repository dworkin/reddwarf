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
import static com.sun.sgs.impl.service.data.store.cache.BindingState.BOUND;
import static com.sun.sgs.impl.service.data.store.cache.BindingState.UNBOUND;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.TransactionInterruptedException;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * A cache entry for a name binding.  Only the {@link #key} field may be
 * accessed without holding the associated lock.  For all other fields and
 * methods, the lock should be held. <p>
 *
 * In addition to the value associated with a name, binding cache entries store
 * information about name bindings in the range between this name and an
 * earlier name.  This information is used to cache information about ranges of
 * names that are known to be unbound so that they can be bound without needing
 * to communicate with the central server.  Entries also maintain information
 * about whether there are operations underway for which the entry was the next
 * entry found in the cache.  This facility is the way that the cache avoids
 * performing multiple operations simultaneously that might add or modify the
 * same cache entry. <p>
 *
 * This class is part of the implementation of {@link CachingDataStore}.
 *
 * @see	Cache#getBindingLock Cache.getBindingLock
 */
final class BindingCacheEntry extends BasicCacheEntry<BindingKey, Long> {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(BindingCacheEntry.class.getName()));

    /** The dummy value used for the last key. */
    private static final long LAST_KEY_VALUE = -2;

    /**
     * The lowest-valued previous key such that names between that key and this
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
     * Creates a binding cache entry.
     *
     * @param	key the key
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     */
    private BindingCacheEntry(BindingKey key, long contextId, State state) {
	super(key, contextId, state);
    }

    /**
     * Creates a binding cache entry to represent a name binding being cached
     * on behalf of a transaction.
     *
     * @param	key the key
     * @param	contextId the context ID associated with the transaction
     * @param	value the cached value
     * @param	forUpdate whether the value is cached for update
     */
    static BindingCacheEntry createCached(
	BindingKey key, long contextId, long value, boolean forUpdate)
    {
	BindingCacheEntry entry = new BindingCacheEntry(
	    key, contextId,
	    forUpdate ? State.CACHED_WRITE : State.CACHED_READ);
	entry.setValue(value);
	return entry;
    }

    /**
     * Creates a binding cache entry to represent the last name binding that is
     * being fetched from the server on behalf of a transaction.
     *
     * @param	contextId the context ID associated with the transaction
     */
    static BindingCacheEntry createLast(long contextId) {
	BindingCacheEntry entry = new BindingCacheEntry(
	    BindingKey.LAST, contextId, State.FETCHING_READ);
	/* Give this last entry a numeric value, but an illegal one */
	entry.setValue(LAST_KEY_VALUE);
	return entry;
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
     * Updates information about previous names that are known to be unbound
     * given the status of a particular name and that it is known that all
     * names between that name and the one for this entry are unbound.
     *
     * @param	newPreviousKey the new previous key
     * @param	newPreviousKeyBound the binding state of the new previous key
     * @throws	IllegalArgumentException if {@code newPreviousKey} is greater
     *		than or equal to the key for this entry
     */
    void updatePreviousKey(BindingKey newPreviousKey,
			   BindingState newPreviousKeyBound)
    {
	if (newPreviousKey.compareTo(key) >= 0) {
	    throw new IllegalArgumentException(
		"New previous key is too large");
	} else if (previousKey == null) {
	    /* No previous key was known */
	    previousKey = newPreviousKey;
	    previousKeyUnbound = (newPreviousKeyBound == UNBOUND);
	}
	int compareTo = newPreviousKey.compareTo(previousKey);
	if (compareTo < 0) {
	    /* New previous key is earlier than previous one */
	    previousKey = newPreviousKey;
	    previousKeyUnbound = (newPreviousKeyBound == UNBOUND);
	} else if (compareTo == 0) {
	    /* Same previous key */
	    if (!previousKeyUnbound && newPreviousKeyBound == UNBOUND) {
		/* Now know previous key is unbound */
		previousKeyUnbound = true;
	    } else if (previousKeyUnbound && newPreviousKeyBound == BOUND) {
		/* No longer know that previous key is unbound */
		previousKeyUnbound = false;
	    }
	} else if (newPreviousKeyBound == BOUND) {
	    /* Move up to the new, higher previous key */
	    previousKey = newPreviousKey;
	    previousKeyUnbound = false;
	}
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
	 * Check if previous key is less than the requested key, or equal and
	 * we know that key is unbound
	 */
	return compare < 0 || (compare == 0 && previousKeyUnbound);
    }

    /**
     * Checks whether this entry is consistent, throwing an assertion error if
     * an inconsistency is found.
     *
     * @param	cache the data store cache
     * @param	lockTimeout the lock timeout for waiting for the entry to not
     *		be pending previous
     */
    void checkState(Cache cache, long lockTimeout) {
	Object lock = cache.getEntryLock(this);
	synchronized (lock) {
	    if (getDecached()) {
		if (cache.getBindingLock(key) != null) {
		    throw new AssertionError(
			"Decached binding entry found: " + this);
		}
		return;
	    }
	    if (key == BindingKey.FIRST) {
		throw new AssertionError(
		    "Binding entry for first key: " + this);
	    } else if (key == BindingKey.LAST) {
		if (getValue() != LAST_KEY_VALUE) {
		    throw new AssertionError(
			"Binding entry for last key has wrong value: " + this);
		}
	    } else if (getValue() == -1) {
		throw new AssertionError(
		    "Binding entry for removed binding: " + this);
	    }
	    /*
	     * Check for permitting fetching, evicting, and pending previous
	     * states
	     */
	    if (getState() == State.FETCHING_READ) {
		if (key != BindingKey.LAST) {
		    throw new AssertionError(
			"Fetching non-last binding entry for read: " + this);
		} else if (!pendingPrevious) {
		    throw new AssertionError(
			"Fetching last binding entry for read without" +
			"pending previous: " + this);
		}
	    } else if (getState() == State.FETCHING_UPGRADE) {
		if (key == BindingKey.LAST) {
		    throw new AssertionError(
			"Upgrading last binding entry: " + this);
		} else if (pendingPrevious) {
		    throw new AssertionError(
			"Upgrading binding entry that is pending previous: " +
			this);
		}
	    } else if (getState() == State.FETCHING_WRITE) {
		throw new AssertionError(
		    "Fetching binding entry for write: " + this);
	    } else if (getDecaching()) {
		if (pendingPrevious) {
		    throw new AssertionError(
			"Evicting binding entry that is pending previous: " +
			this);
		}
	    } else if (getDowngrading()) {
		if (pendingPrevious) {
		    throw new AssertionError(
			"Downgrading binding entry that is" +
			" pending previous: " + this);
		}
	    }
	    try {
		awaitNotPendingPrevious(
		    lock, System.currentTimeMillis() + lockTimeout);
	    } catch (TransactionTimeoutException e) {
		if (logger.isLoggable(WARNING)) {
		    logger.log(WARNING,
			       "Unable to check entry's previous key due to" +
			       " timeout on pending previous:" + this);
		}
		return;
	    }
	    BindingCacheEntry previousEntry = cache.getLowerBindingEntry(key);
	    if (previousEntry == null) {
		return;
	    }
	    BindingKey previousEntryKey = previousEntry.key;
	    if (previousKey != null) {
		int compareTo = previousEntryKey.compareTo(previousKey);
		if (compareTo > 0) {
		    throw new AssertionError(
			"Binding entry previous key is lower than previous" +
			" entry key: " + this +
			", previous entry key: " + previousEntryKey);
		} else if (compareTo == 0 && previousKeyUnbound) {
		    throw new AssertionError(
			"Binding entry notes previous key entry is unbound," +
			" but key is bound: " + this +
			", previous entry key: " + previousEntryKey);
		}
	    } else if (previousEntryKey.compareTo(key) >= 0) {
		throw new AssertionError(
		    "Binding entry key is not greater than the previous" +
		    " entry's key: " + this +
		    ", previous entry key: " + previousEntryKey);
	    }
	}
    }

    /**
     * Returns whether this entry is known to be the next entry in the cache
     * after the specified key.  Note that if this entry does not represent a
     * bound name then this entry does not represent the next bound name after
     * the specified key.
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
     * @param	previousKey the new previous key or {@code null}
     * @param	previousKeyUnbound whether the new previous key is known to be
     *		unbound
     * @throws	IllegalArgumentException if {@code newPreviousKey} is greater
     *		than or equal to the key for this entry
     */
    void setPreviousKey(BindingKey previousKey, boolean previousKeyUnbound) {
	if (previousKey.compareTo(key) >= 0) {
	    throw new IllegalArgumentException("Previous key is too large");
	}
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
     * previous to this entry in the cache.  The entry should not be upgrading,
     * downgrading, decaching, or, unless it is the entry for the last binding
     * key, reading.
     *
     * @throws	IllegalStateException if there is an already pending operation
     *		for a previous entry
     */
    void setPendingPrevious() {
	if (pendingPrevious) {
	    throw new IllegalStateException("Already pending previous");
	}
	assert !getUpgrading() && !getDowngrading() && !getDecaching()
	    : "Setting binding entry pending previous while busy: " + this;
	assert key == BindingKey.LAST || !getReading()
	    : "Setting non-last binding entry pending previous while" +
	    " reading: " + this;
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
