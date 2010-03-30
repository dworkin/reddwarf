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

import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.service.TransactionInterruptedException;
import java.util.EnumSet;

/**
 * The base class for all entries stored in a node's data store cache.  Each
 * entry stores a key that identifies the entry, the value associated with that
 * key, a context ID that identifies the most recent transaction that has
 * accessed the entry, and information about the current state of the entry.
 * Only the {@link #key} field may be accessed without holding the associated
 * lock (see {@link Cache#getBindingLock} and {@link Cache#getObjectLock}.  For
 * all other fields and methods, the lock must be held. <p>
 *
 * A given entry maintains information for the associated object or binding
 * only so long as that item remains in the cache.  Once an item is removed
 * from the cache, a new entry will be created to represent that item the next
 * time that it enters the cache.
 *
 * @param	<K> the key type
 * @param	<V> the value type
 * @see		Cache
 */
abstract class BasicCacheEntry<K, V> {

    /**
     * The various status values that can be associated with the state of a
     * cache entry.
     */
    enum Status {
    
	/** The entry is being made readable. */
	READING,

	/** The entry is readable. */
	READABLE,

	/** The entry is being made writable. */
	UPGRADING,

	/** The entry is writable. */
	WRITABLE,

	/** The entry contains data modified by a single transaction. */
	MODIFIED,

	/** The entry is being made not writable. */
	DOWNGRADING,

	/** The entry is being removed from the cache. */
	DECACHING,

	/** The entry is not cached. */
	NOT_CACHED;
    };

    /**
     * The possible entry states. <p>
     *
     * Here are the permitted transitions: <ul>
     *
     * <li>Fetching for read: <ul>
     * <li>{@code FETCHING_READ} &rArr; {@code CACHED_READ}
     * </ul>
     *
     * <li>Upgrading to write: <ul>
     * <li>{@code CACHED_READ} &rArr; {@code FETCHING_UPGRADE}
     * <li>{@code FETCHING_UPGRADE} &rArr; {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Fetching for write: <ul>
     * <li>{@code FETCHING_WRITE} &rArr; {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Modifying: <ul>
     * <li>{@code CACHED_WRITE} &rArr; {@code CACHED_DIRTY}
     * </ul>
     *
     * <li>Flushing modifications: <ul>
     * <li>{@code CACHED_DIRTY} &rArr; {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Downgrading: <ul>
     * <li>{@code CACHED_WRITE} &rArr; {@code EVICTING_DOWNGRADE}
     * <li>{@code EVICTING_DOWNGRADE} &rArr; {@code CACHED_READ}
     * </ul>
     *
     * <li>Evicting read: <ul>
     * <li>{@code CACHED_READ} &rArr; {@code EVICTING_READ}
     * <li>{@code EVICTING_READ} &rArr; {@code NOT_CACHED}
     * </ul>
     *
     * <li>Evicting write: <ul>
     * <li>{@code CACHED_WRITE} &rArr; {@code EVICTING_WRITE}
     * <li>{@code EVICTING_WRITE} &rArr; {@code NOT_CACHED}
     * </ul>
     * </ul> <p>
     *
     * If an access coordinator read or write lock is held on an entry's key,
     * and the entry is currently in use, then eviction of that entry cannot be
     * initiated by another thread.  In addition, if the write lock is held,
     * then fetch for write operations cannot be started or completed.
     */
    /* Here's an ASCII diagram of the permitted state transitions:
     *
     *  Fetching-->
     *
     * |     READING         READABLE  UPGRADING    WRITABLE       MODIFIED
     * |
     * |>---------------FETCHING_WRITE------------->|
     * |                                            |
     * |>---FETCHING_READ--->|>--FETCHING_UPGRADE-->|
     *                       |                      |
     *                       CACHED_READ            CACHED_WRITE<->CACHED_DIRTY
     *                       |                      |
     * |<---EVICTING_READ---<|<-EVICTING_DOWNGRADE-<|
     * |                                            |
     * |<---------------EVICTING_WRITE-------------<|
     * |
     * DECACHED
     *        DECACHING      READABLE  DOWNGRADING  WRITABLE     (commit/abort)
     *
     *  <--Evicting
     */
    enum State {

	/** The entry is being fetched for read. */
	FETCHING_READ(Status.READING),

	/** The entry is available for read. */
	CACHED_READ(Status.READABLE),

	/** The entry is available for read and is being upgraded to write. */
	FETCHING_UPGRADE(Status.READABLE, Status.UPGRADING),

	/** The entry value is being fetched for read and write. */
	FETCHING_WRITE(Status.READING, Status.UPGRADING),

	/** The entry is available for read and write. */
	CACHED_WRITE(Status.READABLE, Status.WRITABLE),

	/**
	 * The entry is available for read and write, and contains data that
	 * has been modified by a single transaction.
	 */
	CACHED_DIRTY(Status.READABLE, Status.WRITABLE, Status.MODIFIED),

	/**
	 * The entry is available for read and is being downgraded from write.
	 */
	EVICTING_DOWNGRADE(Status.READABLE, Status.DOWNGRADING),

	/** The entry is being evicted after being readable. */
	EVICTING_READ(Status.DECACHING),

	/** The entry is being evicted after being writable. */
	EVICTING_WRITE(Status.DOWNGRADING, Status.DECACHING),

	/** The entry has been removed from the cache. */
	DECACHED(Status.NOT_CACHED);

	/**
	 * Creates an instance with the specified status values.
	 *
	 * @param	firstStatus the first status value
	 * @param	moreStatus remaining status values
	 */
	private State(Status firstStatus, Status... moreStatus) {
	    statusSet = EnumSet.of(firstStatus, moreStatus);
	}

	/** The status values for this state. */
	final EnumSet<Status> statusSet;
    };

    /** The key for this entry. */
    final K key;

    /**
     * The current value of this entry, which may be {@code null}, and is also
     * {@code null} if the entry's value is not valid because it is decached or
     * being fetched.
     */
    private V value;

    /**
     * The context ID of the transaction with the highest context ID that has
     * accessed this entry.
     */
    private long contextId;

    /**
     * The state of this entry.  Make sure to notify the associated lock
     * whenever the value of this field is changed.
     */
    private State state;

    /**
     * Creates a cache entry with the specified key, context ID, and state.  No
     * value is being specified, so the state must be {@link
     * State#FETCHING_READ} or {@link State#FETCHING_WRITE}.
     *
     * @param	key the key
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     * @throws	IllegalArgumentException if {@code state} is not {@code
     *		FETCHING_READ} or {@code FETCHING_WRITE}
     */
    BasicCacheEntry(K key, long contextId, State state) {
	if (state != State.FETCHING_READ && state != State.FETCHING_WRITE) {
	    throw new IllegalArgumentException(
		"State must be FETCHING_READ or FETCHING_WRITE: " + state);
	}
	this.key = key;
	this.contextId = contextId;
	this.state = state;
    }

    /**
     * Creates a cache entry with the specified key, value, context ID, and
     * state.  A value is being specified, so the state must be not be {@link
     * State#FETCHING_READ}, {@link State#FETCHING_WRITE}, or {@link
     * State#DECACHED}.
     *
     * @param	key the key
     * @param	value the value
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     * @throws	IllegalArgumentException if {@code state} is {@code
     *		FETCHING_READ}, {@code FETCHING_WRITE}, or {@code DECACHED}
     */
    BasicCacheEntry(K key, V value, long contextId, State state) {
	if (state == State.FETCHING_READ ||
	    state == State.FETCHING_WRITE ||
	    state == State.DECACHED)
	{
	    throw new IllegalArgumentException(
		"State must not be FETCHING_READ, FETCHING_WRITE," +
		" or DECACHED: " + state);
	}
	this.key = key;
	setValue(value);
	this.contextId = contextId;
	this.state = state;
    }

    /* -- State and Status methods -- */

    /* Status.READING */

    /**
     * Returns whether this entry is being made readable.
     *
     * @return	whether this entry is being made readable
     */
    boolean getReading() {
	return checkStatus(Status.READING);
    }

    /* Status.READABLE */

    /**
     * Returns whether this entry is readable.
     *
     * @return	whether this entry is readable
     */
    boolean getReadable() {
	return checkStatus(Status.READABLE);
    }

    /**
     * Sets this entry's state to {@link State#FETCHING_READ} temporarily for
     * when a transaction that created a binding entry aborts while the entry
     * is marked pending previous for an ongoing server request.  Notifies the
     * lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#CACHED_WRITE}
     */
    void setReadingTemporarily(Object lock) {
	verifyState(State.CACHED_WRITE);
	state = State.FETCHING_READ;
	value = null;
	lock.notifyAll();
    }

    /**
     * Waits for this entry to become readable, also waiting if it is in the
     * process of being evicted.  Returns {@code true} if the entry is
     * readable, else {@code false} if the entry has become decached.
     *
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @return	{@code true} if the entry is readable, else {@code false} if it
     *		is decached
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    boolean awaitReadable(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (checkStatus(Status.READABLE)) {
	    /* Already cached for read */
	    return true;
	} else if (checkStatus(Status.READING)) {
	    /* Already fetching for read */
	    awaitStatusNot(Status.READING, lock, stop);
	    return getReadable();
	} else if (checkStatus(Status.DECACHING)) {
	    /* Evicting from cache -- wait until done, then retry */
	    awaitStatus(Status.NOT_CACHED, lock, stop);
	    return false;
	} else /* state == State.DECACHED */ {
	    /* Entry is not in the cache */
	    return false;
	}
    }

    /* State.CACHED_READ */

    /**
     * Sets this entry's state to {@link State#CACHED_READ} after fetching for
     * read, stores the newly fetched value, and notifies the lock, which must
     * be held.
     *
     * @param	lock the associated lock
     * @param	value the fetched value
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#FETCHING_READ}
     */
    void setCachedRead(Object lock, V value) {
	verifyState(State.FETCHING_READ);
	state = State.CACHED_READ;
	setValue(value);
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_READ} after downgrading,
     * and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#EVICTING_DOWNGRADE}
     */
    void setEvictedDowngrade(Object lock) {
	verifyState(State.EVICTING_DOWNGRADE);
	state = State.CACHED_READ;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_READ} directly from
     * {@link State#CACHED_WRITE} after an immediate downgrade when not in use,
     * and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#CACHED_WRITE}
     */
    void setEvictedDowngradeImmediate(Object lock) {
	verifyState(State.CACHED_WRITE);
	state = State.CACHED_READ;
	lock.notifyAll();
    }

    /* Status.UPGRADING */

    /**
     * Returns whether this entry is being made writable.
     *
     * @return	whether this entry is being made writable
     */
    boolean getUpgrading() {
	return checkStatus(Status.UPGRADING);
    }

    /**
     * Waits for this entry to finish being made writable.
     *
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if the entry is not being upgraded
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitNotUpgrading(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	verifyState(State.FETCHING_UPGRADE, State.FETCHING_WRITE);
	awaitStatusNot(Status.UPGRADING, lock, stop);
    }

    /* State.FETCHING_UPGRADE */

    /**
     * Sets this entry's state to {@link State#FETCHING_UPGRADE}, and notifies
     * the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#CACHED_READ}
     */
    void setFetchingUpgrade(Object lock) {
	verifyState(State.CACHED_READ);
	state = State.FETCHING_UPGRADE;
	lock.notifyAll();
    }

    /* Status.WRITABLE */

    /**
     * Returns whether this entry is available for write.
     *
     * @return	whether this entry is available for write
     */
    boolean getWritable() {
	return checkStatus(Status.WRITABLE);
    }

    /** The possible results of calling {@link #awaitWritable}. */
    enum AwaitWritableResult {

	/** The entry is not cached. */
	NOT_CACHED,

	/** The entry is readable. */
	READABLE,

	/** The entry is writable. */
	WRITABLE;
    }

    /**
     * Waits for this entry to become writable, also waiting if it is in the
     * process of being downgraded or evicted.  Returns {@link
     * AwaitWritableResult#WRITABLE} if the entry is writable, {@link
     * AwaitWritableResult#READABLE} if the entry is readable but not writable,
     * and else {@link AwaitWritableResult#NOT_CACHED} if the entry has become
     * decached.
     *
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @return	the status of the entry
     * @throws	ResourceUnavailableException if the operation does not succeed
     *		in the standard number of cache retries
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    AwaitWritableResult awaitWritable(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	for (int i = 0; true; i++) {
	    if (i >= CachingDataStore.MAX_CACHE_RETRIES) {
		/* TBD: Add profile counter.  -tjb@sun.com (01/25/2010) */
		throw new ResourceUnavailableException("Too many retries");
	    }
	    if (checkStatus(Status.WRITABLE)) {
		/* Already cached for write */
		return AwaitWritableResult.WRITABLE;
	    } else if (checkStatus(Status.UPGRADING)) {
		/* Wait for upgrading to complete, then retry */
		awaitStatusNot(Status.UPGRADING, lock, stop);
		continue;
	    } else if (checkStatus(Status.DOWNGRADING)) {
		/* Wait for downgrading to complete, then retry */
		awaitStatusNot(Status.DOWNGRADING, lock, stop);
		continue;
	    } else if (checkStatus(Status.READABLE)) {
		/* Cached for read and not being upgraded. */
		return AwaitWritableResult.READABLE;
	    } else if (checkStatus(Status.READING)) {
		/* Wait for reading to complete, then retry */
		awaitStatusNot(Status.READING, lock, stop);
		continue;
	    } else if (checkStatus(Status.DECACHING)) {
		/*
		 * Evicting from cache -- wait until done, then caller should
		 * retry.
		 */
		awaitDecached(lock, stop);
		return AwaitWritableResult.NOT_CACHED;
	    } else /* Status.NOT_CACHED */ {
		/* Entry is not in the cache */
		return AwaitWritableResult.NOT_CACHED;
	    }
	}
    }

    /* State.CACHED_WRITE */

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was being
     * fetched for write, stores the newly fetched value, and notifies the
     * associated lock, which must be held.
     *
     * @param	lock the associated lock
     * @param	value the fetched value
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_WRITE}
     */
    void setCachedWrite(Object lock, V value) {
	verifyState(State.FETCHING_WRITE);
	state = State.CACHED_WRITE;
	setValue(value);
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was being
     * fetched for read, stores the newly fetched value, and notifies the
     * associated lock, which must be held.
     *
     * @param	lock the associated lock
     * @param	value the fetched value
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_READ}
     */
    void setCachedWriteUpgrade(Object lock, V value) {
	verifyState(State.FETCHING_READ);
	state = State.CACHED_WRITE;
	setValue(value);
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was being
     * upgraded, and notifies the associated lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_UPGRADE}
     */
    void setUpgraded(Object lock) {
	verifyState(State.FETCHING_UPGRADE);
	state = State.CACHED_WRITE;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was
     * upgraded after receiving information about a binding, and notifies the
     * associated lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#CACHED_READ}
     */
    void setUpgradedImmediate(Object lock) {
	verifyState(State.CACHED_READ);
	state = State.CACHED_WRITE;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} after it was
     * modified during a transaction which is now ending, and notifies the
     * lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the current state is not {@link
     *		State#CACHED_DIRTY}
     */
    void setNotModified(Object lock) {
	verifyState(State.CACHED_DIRTY);
	state = State.CACHED_WRITE;
	lock.notifyAll();
    }

    /* Status.MODIFIED */

    /**
     * Returns whether this entry is modified.
     *
     * @return	whether this entry is modified
     */
    boolean getModified() {
	return checkStatus(Status.MODIFIED);
    }

    /* State.CACHED_DIRTY */

    /**
     * Sets this entry's state to {@link State#CACHED_DIRTY} when it is being
     * modified, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if this entry's current state is not
     *		{@link State#CACHED_WRITE}
     */
    void setCachedDirty(Object lock) {
	verifyState(State.CACHED_WRITE);
	state = State.CACHED_DIRTY;
	lock.notifyAll();
    }

    /* Status.DOWNGRADING */

    /**
     * Returns whether this entry is being downgraded
     *
     * @return	whether this entry is being downgraded
     */
    boolean getDowngrading() {
	return checkStatus(Status.DOWNGRADING);
    }

    /* State.EVICTING_DOWNGRADE */

    /**
     * Sets this entry's state to {@link State#EVICTING_DOWNGRADE} when it is
     * being downgraded, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if this entry's current state is not
     *		{@link State#CACHED_WRITE}
     */
    void setEvictingDowngrade(Object lock) {
	verifyState(State.CACHED_WRITE);
	state = State.EVICTING_DOWNGRADE;
	lock.notifyAll();
    }

    /* Status.DECACHING */

    /**
     * Returns whether this entry is being decached.
     *
     * @return	whether this entry is being decached
     */
    boolean getDecaching() {
	return checkStatus(Status.DECACHING);
    }

    /* State.EVICTING_READ */

    /**
     * Sets this entry's state to {@link State#EVICTING_READ} or {@link
     * State#EVICTING_WRITE}, depending on whether it is cached for read or
     * write, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#CACHED_READ} or {@link State#CACHED_WRITE}
     */
    void setEvicting(Object lock) {
	verifyState(State.CACHED_READ, State.CACHED_WRITE);
	state = (state == State.CACHED_READ)
	    ? State.EVICTING_READ : State.EVICTING_WRITE;
	lock.notifyAll();
    }

    /* State.DECACHED */

    /**
     * Returns whether this entry is in state {@link State#DECACHED}.
     *
     * @return	whether this entry is in state {@code DECACHED}
     */
    boolean getDecached() {
	return state == State.DECACHED;
    }

    /**
     * Returns when this entry is decached.
     *
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#CACHED_READ} or {@link State#CACHED_WRITE}
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitDecached(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (!getDecached()) {
	    verifyState(State.EVICTING_READ, State.EVICTING_WRITE);
	    awaitStatus(Status.NOT_CACHED, lock, stop);
	}
    }

    /**
     * Sets this entry's state to {@link State#DECACHED} after being evicted
     * for read or write, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#EVICTING_READ} or {@link State#EVICTING_WRITE}
     */
    void setEvicted(Object lock) {
	verifyState(State.EVICTING_READ, State.EVICTING_WRITE);
	state = State.DECACHED;
	value = null;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#DECACHED} directly from {@link
     * State#CACHED_READ} or {@link State#CACHED_WRITE} after an immediate
     * eviction when not in use, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#CACHED_READ} or {@link State#CACHED_WRITE}
     */
    void setEvictedImmediate(Object lock) {
	verifyState(State.CACHED_READ, State.CACHED_WRITE);
	state = State.DECACHED;
	value = null;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#DECACHED} directly from {@link
     * State#FETCHING_READ} or {@link State#FETCHING_WRITE} when abandoning the
     * a provisional binding entry if no information about the binding was
     * actually obtained, and notifies the lock, which must be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_READ} or {@link State#FETCHING_WRITE}
     */
    void setEvictedAbandonFetching(Object lock) {
	verifyState(State.FETCHING_READ, State.FETCHING_WRITE);
	state = State.DECACHED;
	value = null;
	lock.notifyAll();
    }

    /* -- Other methods -- */

    /**
     * Returns the state of this entry.
     *
     * @return	the state of this entry
     */
    State getState() {
	return state;
    }

    /**
     * Notes that this entry has been accessed by a transaction with the
     * specified context ID if the supplied ID is greater than the one already
     * noted.
     *
     * @param	contextId the context ID
     */
    void noteAccess(long contextId) {
	if (contextId > this.contextId) {
	    this.contextId = contextId;
	}
    }

    /**
     * Returns the cached value.
     *
     * @return	the cached value
     * @throws	IllegalStateException if the value is not known because the
     *		status is {@link Status#READING} or {@link Status#NOT_CACHED}
     */
    V getValue() {
	if (!hasValue()) {
	    throw new IllegalStateException("Value is not known: " + this);
	}
	return value;
    }

    /**
     * Returns whether the entry has a valid cached value.
     */
    boolean hasValue() {
	return !getReading() && !getDecached();
    }

    /**
     * Updates the value of this entry.
     *
     * @param	newValue the new value
     */
    void setValue(V newValue) {
	value = newValue;
    }

    /**
     * Gets the context ID of the transaction with the highest ID that has
     * accessed this entry.
     *
     * @return	the highest context ID
     */
    long getContextId() {
	return contextId;
    }

    /* -- Private methods -- */

    /**
     * Checks this entry's status includes the specified status value.
     *
     * @param	status the status value to check
     * @return	{@code true} if the status value is present, else {@code false}
     */
    private boolean checkStatus(Status status) {
	return state.statusSet.contains(status);
    }

    /**
     * Verifies that the entry has the expected state.
     *
     * @param	expected the expected state
     * @throws	IllegalStateException if the entry does not have the expected
     *		state
     */
    private void verifyState(State expected) {
	if (state != expected) {
	    throw new IllegalStateException(
		"Invalid state, expected " + expected + ", found " + state +
		", entry:" + this);
	}
    }

    /**
     * Verifies that the entry has one of two expected states.
     *
     * @param	expected1 one expected state
     * @param	expected2 another expected state
     * @throws	IllegalStateException if the entry does not have one of the two
     *		expected states
     */
    private void verifyState(State expected1, State expected2) {
	if (state != expected1 && state != expected2) {
	    throw new IllegalStateException(
		"Invalid state, expected " + expected1 + " or " +
		expected2 + ", found " + state + ", entry:" + this);
	}
    }

    /**
     * Waits for this entry's state value to include the specified status
     * value.
     *
     * @param	status the status value to check
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if desired state value does not
     *		appear before the specified stop time
     */
    private void awaitStatus(Status status, Object lock, long stop) {
	if (checkStatus(status)) {
	    return;
	}
	long start = System.currentTimeMillis();
	long now = start;
	while (now < stop) {
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for entry " + this, e);
	    }
	    if (checkStatus(status)) {
		return;
	    }
	    now = System.currentTimeMillis();
	}
	throw new TransactionTimeoutException(
	    "Timeout after " + (now - start) +
	    " ms waiting for entry " + this);
    }

    /**
     * Waits for this entry's state value to not include the specified status
     * value.
     *
     * @param	status the status value to check
     * @param	lock the associated lock, which must be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionInterruptedException if the current thread is
     *		interrupted while waiting
     * @throws	TransactionTimeoutException if specified state value has not
     *		been cleared before the specified stop time
     */
    private void awaitStatusNot(Status status, Object lock, long stop) {
	if (!state.statusSet.contains(status)) {
	    return;
	}
	long start = System.currentTimeMillis();
	long now = start;
	while (now < stop) {
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for entry " + this, e);
	    }
	    if (!state.statusSet.contains(status)) {
		return;
	    }
	    now = System.currentTimeMillis();
	}
	throw new TransactionTimeoutException(
	    "Timeout after " + (now - start) +
	    " ms waiting for entry " + this);
    }
}
