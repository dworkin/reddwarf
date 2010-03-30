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

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.app.TransactionTimeoutException;
import static com.sun.sgs.impl.service.data.store.cache.BindingState.BOUND;
import static com.sun.sgs.impl.service.data.store.cache.BindingState.UNBOUND;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.TransactionInterruptedException;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * A cache entry for a name binding.  Entries only appear in the cache for
 * bound names; information about unbound names is represented using the {@link
 * #previousKey} and {@link #previousKeyUnbound} fields.  Only the {@link #key}
 * field may be accessed without holding the associated lock (see {@link
 * Cache#getBindingLock} and {@link Cache#getObjectLock}.  For all other fields
 * and methods, the lock must be held. <p>
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
 * The way that binding entries are marked to indicate that they are being
 * fetched from the server is different from object entries.  Unless an entry
 * for a particular binding is already cached, binding requests to the server
 * typically ask for information about the next available binding, and the key
 * of that next binding is not known in advance.  To avoid making simultaneous
 * requests to the server for the same or possibly overlapping information, the
 * next entry present in the cache after the one for a requested name is used
 * to represent the pending operation, and is marked "pending previous" when in
 * use.  It is this pending previous state that is used to mark binding entries
 * as in use for server requests.  In particular, the <tt>FETCHING_UPGRADE</tt>
 * and <tt>FETCHING_WRITE</tt> states should not be used.  The only exception
 * to this rule occurs when there is no next entry in the cache to represent a
 * request being made to the server.  This situation can occur either when a
 * request is made for an entry beyond the last entry currently in the cache,
 * or when the next entry in the cache is one that was created for a
 * transaction that aborts while the operation using the entry is in progress.
 * In these cases, the binding entry is marked <tt>FETCHING_READ</tt> as a way
 * of noting that the entry should be removed from the cache if the server
 * request does not return an entry with that same key.
 *
 * @see	Cache#getBindingLock Cache.getBindingLock
 */
final class BindingCacheEntry extends BasicCacheEntry<BindingKey, Long> {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(BindingCacheEntry.class.getName()));

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
     * Creates a binding cache entry whose value is being fetched.
     *
     * @param	key the key
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     * @throw	IllegalArgumentException if {@code key} is {@link
     *		BindingKey#FIRST}
     */
    private BindingCacheEntry(BindingKey key, long contextId, State state) {
	super(key, contextId, state);
	if (key == BindingKey.FIRST) {
	    throw new IllegalArgumentException(
		"The key must not be BindingKey.FIRST");
	}
    }

    /**
     * Creates a binding cache entry with a cached value.
     *
     * @param	key the key
     * @param	value the value
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     * @throws	IllegalArgumentException if {@code key} is {@link
     *		BindingKey#FIRST}, or if {@code value} is negative
     */
    private BindingCacheEntry(
	BindingKey key, long value, long contextId, State state)
    {
	super(key, value, contextId, state);
	if (key == BindingKey.FIRST) {
	    throw new IllegalArgumentException(
		"The key must not be BindingKey.FIRST");
	} else if (value < 0) {
	    throw new IllegalArgumentException("Value must not be negative");
	}
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
	return new BindingCacheEntry(
	    key, value,
	    contextId, forUpdate ? State.CACHED_WRITE : State.CACHED_READ);
    }

    /**
     * Creates a binding cache entry to represent the last name binding that is
     * being fetched from the server on behalf of a transaction.
     *
     * @param	contextId the context ID associated with the transaction
     */
    static BindingCacheEntry createLast(long contextId) {
	return new BindingCacheEntry(
	    BindingKey.LAST, contextId, State.FETCHING_READ);
    }

    @Override
    public String toString() {
	return "BindingCacheEntry[" +
	    "name:" + key +
	    (!hasValue() ? "" :
	     ", value:" + getValue()) +
	    ", contextId:" + getContextId() +
	    ", state:" + getState() +
	    ", previousKey:" + previousKey +
	    (previousKey == null ? "" :
	     ", previousKeyUnbound:" + previousKeyUnbound) +
	    ", pendingPrevious:" + pendingPrevious +
	    "]";
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation also returns {@code false} if the key is {@link
     * BindingKey#LAST}.
     */
    @Override
    boolean hasValue() {
	return (key != BindingKey.LAST) && super.hasValue();
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation also checks to see that {@code newValue} is not
     * negative, unless this entry's key is {@link BindingKey#LAST}, in which
     * case only {@code -1} is allowed.
     *
     * @throws	IllegalArgumentException if {@code newValue} is negative and
     *		this entry's key is not {@link BindingKey#LAST}, or if it isn't
     *		{@code -1} if the key is {@code LAST}
     */
    @Override
    void setValue(Long newValue) {
	if (newValue == null) {
	    throw new NullPointerException(
		"New value must not be null");
	} else if (key == BindingKey.LAST) {
	    if (newValue != -1) {
		throw new IllegalStateException(
		    "New value must be -1 for LAST entry");
	    }
	    /* Store null, since the LAST entry doesn't really have a value */
	    newValue = null;
	} else if (newValue < 0) {
	    throw new IllegalArgumentException(
		"New value must not be negative");
	}
	super.setValue(newValue);
    }

    /**
     * Updates the information stored in this entry about previous names that
     * are known to be unbound.  The {@code newPreviousKey} represents a name
     * for which all names between that name and this entry's name are known to
     * be unbound.  The {@code newPreviousKeyState} specifies what is known
     * about the binding of {@code newPreviousKey} itself.
     *
     * @param	newPreviousKey the new previous key
     * @param	newPreviousKeyState the binding state of the new previous key
     * @throws	IllegalArgumentException if {@code newPreviousKey} is greater
     *		than or equal to the key for this entry
     */
    void updatePreviousKey(BindingKey newPreviousKey,
			   BindingState newPreviousKeyState)
    {
	assert newPreviousKeyState != null;
	if (newPreviousKey.compareTo(key) >= 0) {
	    throw new IllegalArgumentException(
		"New previous key is too large");
	} else if (previousKey == null) {
	    /* No previous key was known */
	    previousKey = newPreviousKey;
	    previousKeyUnbound = (newPreviousKeyState == UNBOUND);
	    return;
	}
	int compareTo = newPreviousKey.compareTo(previousKey);
	if (compareTo < 0) {
	    /* New previous key is earlier than previous one */
	    previousKey = newPreviousKey;
	    previousKeyUnbound = (newPreviousKeyState == UNBOUND);
	} else if (compareTo == 0) {
	    /* Same previous key */
	    if (!previousKeyUnbound && newPreviousKeyState == UNBOUND) {
		/* Now know previous key is unbound */
		previousKeyUnbound = true;
	    } else if (previousKeyUnbound && newPreviousKeyState == BOUND) {
		/* No longer know that previous key is unbound */
		previousKeyUnbound = false;
	    }
	} else if (newPreviousKeyState == BOUND) {
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
     * an inconsistency is found.  This method should only be called for
     * entries that are present in the cache.  This method is intended to be
     * used for testing only.
     *
     * @param	cache the data store cache
     * @param	lockTimeout the lock timeout for waiting for the entry to not
     *		be pending previous
     */
    void checkState(Cache cache, long lockTimeout) {
	Object lock = cache.getEntryLock(this);
	synchronized (lock) {
	    if (getDecached()) {
		throw new AssertionError(
		    "Decached binding entry found: " + this);
	    }
	    if (key == BindingKey.FIRST) {
		throw new AssertionError(
		    "No binding entry should be present for first key: "
		    + this);
	    }
	    /*
	     * Check for permitting fetching, evicting, and pending previous
	     * states
	     */
	    if (getState() == State.FETCHING_READ) {
		if (!pendingPrevious) {
		    throw new AssertionError(
			"Binding entry that is FETCHING_READ should also be" +
			" pending previous: " + this);
		}
	    } else if (getState() == State.FETCHING_UPGRADE) {
		throw new AssertionError(
		    "Binding entries should not be FETCHING_UPGRADE: " + this);
	    } else if (getState() == State.FETCHING_WRITE) {
		throw new AssertionError(
		    "Binding entries should not be FETCHING_WRITE: " + this);
	    } else if (getDecaching()) {
		if (pendingPrevious) {
		    throw new AssertionError(
			"Binding entries that are being evicted should not" +
			" be pending previous: " + this);
		}
	    } else if (getDowngrading()) {
		if (pendingPrevious) {
		    throw new AssertionError(
			"Binding entries that are being downgraded should" +
			" not be pending previous: " + this);
		}
	    }
	    try {
		if (!awaitNotPendingPrevious(
			lock, System.currentTimeMillis() + lockTimeout))
		{
		    /*
		     * We can't check the state of this entry because it
		     * remained pending previous for too long.  Since the cache
		     * state is changing all of the time, though, checking the
		     * entire cache is already somewhat approximate, so its OK
		     * to abandon checking this entry and move on to the next.
		     */
		    return;
		}
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
			"Binding entry's previous key should not be lower" +
			" than previous entry's key: " + this +
			", previous entry key: " + previousEntryKey);
		} else if (compareTo == 0 && previousKeyUnbound) {
		    throw new AssertionError(
			"Binding entry should not record that the previous " +
			" key entry is unbound when that key is bound: " +
			this + ", previous entry key: " + previousEntryKey);
		}
	    } else if (previousEntryKey.compareTo(key) >= 0) {
		throw new AssertionError(
		    "Binding entry's key should be greater than the previous" +
		    " entry's key: " + this +
		    ", previous entry key: " + previousEntryKey);
	    }
	}
    }

    /**
     * Returns whether this entry is known to be the next entry in the cache
     * after the specified key.
     *
     * @param	forKey the key to check
     * @return	whether this entry is known to be the next entry in the cache
     *		after {@code forKey}
     */
    boolean isNextEntry(BindingKey forKey) {
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
    boolean isPreviousKeyUnbound() {
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
	if (previousKey != null && previousKey.compareTo(key) >= 0) {
	    throw new IllegalArgumentException("Previous key is too large");
	}
	this.previousKey = previousKey;
	this.previousKeyUnbound = previousKeyUnbound;
    }

    /**
     * Waits for the pending operation, if any, for an entry immediately
     * previous to this entry in the cache to complete.  Also waits for the
     * entry to not be downgrading, and assures that it is in the cache.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    boolean awaitNotPendingPrevious(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (getReadable() && !pendingPrevious && !getDowngrading()) {
	    return true;
	}
	long start = System.currentTimeMillis();
	long now = start;
	while (now < stop) {
	    if (!awaitReadable(lock, stop)) {
		return false;
	    } else if (!pendingPrevious && !getDowngrading()) {
		return true;
	    }
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for lock: " + this, e);
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
	    throw new IllegalStateException(
		"Already pending previous: " + this);
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
