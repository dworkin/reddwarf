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
 * A cache entry.  Only the {@code key} field may be accessed without holding
 * the associated lock.  For all other fields and methods, the lock should be
 * held.
 *
 * @param	<K> the key type
 * @param	<V> the value type
 * @see		Cache#getBindingLock
 * @see		Cache#getObjectLock
 */
abstract class BasicCacheEntry<K, V> {

    /** The entry is not cached. */
    private static final int NOT_CACHED			= 0x01;

    /** The entry is readable. */
    private static final int READABLE			= 0x02;

    /** The entry is writable. */
    private static final int WRITABLE			= 0x04;

    /** The entry is modified. */
    private static final int MODIFIED			= 0x08;

    /** The entry is being read. */
    private static final int READING			= 0x10;

    /** The entry is being made writable. */
    private static final int UPGRADING			= 0x20;

    /** The entry is being made not writable. */
    private static final int DOWNGRADING		= 0x40;

    /** The entry is being removed from the cache. */
    private static final int DECACHING			= 0x80;

    /**
     * The possible entry states. <p>
     *
     * Here are the permitted transitions: <ul>
     *
     * <li>Fetching for read, then upgrading to write: <ul>
     * <li>{@code FETCHING_READ} => {@code CACHED_READ}
     * <li>{@code CACHED_READ} => {@code FETCHING_UPGRADE}
     * <li>{@code FETCHING_UPGRADE} => {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Fetching for write: <ul>
     * <li>{@code FETCHING_WRITE} => {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Modifying: <ul>
     * <li>{@code CACHED_WRITE} => {@code CACHED_DIRTY}
     * </ul>
     *
     * <li>Flushing modifications: <ul>
     * <li>{@code CACHED_DIRTY} => {@code CACHED_WRITE}
     * </ul>
     *
     * <li>Downgrading, then evicting read: <ul>
     * <li>{@code CACHED_WRITE} => {@code EVICTING_DOWNGRADE}
     * <li>{@code EVICTING_DOWNGRADE} => {@code CACHED_READ}
     * <li>{@code CACHED_READ} => {@code EVICTING_READ}
     * <li>{@code EVICTING_READ} => {@code NOT_CACHED}
     * </ul>
     *
     * <li>Evicting write: <ul>
     * <li>{@code CACHED_WRITE} => {@code EVICTING_WRITE}
     * <li>{@code EVICTING_WRITE} => {@code NOT_CACHED}
     * </ul>
     * </ul>
     *
     * If an access coordinator read or write lock is held on an item, then
     * there can be no initiation of eviction by another thread.  If the write
     * lock is held, then there can be no fetch for write operations starting
     * or completing.
     */
    /* Here's an ASCII diagram of the state transitions:
     *
     * |     READING         READABLE  UPGRADING    WRITABLE  MODIFYING
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
     *        DECACHING      READABLE  DOWNGRADING  WRITABLE  COMMIT/ABORT
     */
    enum State {

	/** The entry is being fetched for read. */
	FETCHING_READ(READING),

	/** The entry is available for read. */
	CACHED_READ(READABLE),

	/** The entry is available for read and is being upgraded to write. */
	FETCHING_UPGRADE(READABLE | UPGRADING),

	/** The entry value is being fetched for read and write. */
	FETCHING_WRITE(READING | UPGRADING),

	/** The entry is available for read and write. */
	CACHED_WRITE(WRITABLE),

	/**
	 * The entry is available for read and write in the cache, and has been
	 * modified.
	 */
	CACHED_DIRTY(WRITABLE | MODIFIED),

	/**
	 * The entry is available for read and is being downgraded from write.
	 */
	EVICTING_DOWNGRADE(READABLE | DOWNGRADING),

	/** The entry is being evicted after being readable. */
	EVICTING_READ(DECACHING),

	/** The entry is being evicted after being writable. */
	EVICTING_WRITE(DOWNGRADING | DECACHING),

	/** The entry has been removed from the cache. */
	DECACHED(NOT_CACHED);

	/**
	 * Creates an instance with the specified value.
	 *
	 * @param	value the value
	 */
	private State(int value) {
	    this.value = value;
	}

	/**
	 * The value of this state, a combination of the values of {@link
	 * #NOT_CACHED}, {@link #READABLE}, {@link #WRITABLE}, {@link
	 * #MODIFIED}, {@link #READING}, {@link #UPGRADING}, {@link
	 * #DOWNGRADING}, and {@link #DECACHING}.
	 */
	final int value;
    };

    /** The key for this entry. */
    final K key;

    /** The current value of this entry. */
    private V value;

    /**
     * The context ID of the transaction with the highest context ID that has
     * accessed this entry.
     */
    private long contextId = Integer.MIN_VALUE;

    /** The state of this entry. */
    private State state;

    /**
     * Creates a cache entry with the specified key and state.
     *
     * @param	key the key
     * @param	initialState the state
     */
    BasicCacheEntry(K key, State initialState) {
	this.key = key;
	state = initialState;
    }

    /* -- State methods -- */

    /* State.CACHED_READ and READABLE */

    /**
     * Returns whether this entry is available for read.
     *
     * @return	whether this entry is available for read
     */
    boolean getReadable() {
	return checkStateValue(READABLE);
    }

    /**
     * Waits for this entry to become available for read.  Returns {@code true}
     * if the entry is readable, else {@code false} if the entry has become
     * decached.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @return	{@code true} if the entry is readable, else {@code false} if it
     *		is decached
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    boolean awaitReadable(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (checkStateValue(READABLE)) {
	    /* Already cached for read */
	    return true;
	} else if (checkStateValue(READING)) {
	    /* Already fetching for read */
	    awaitNot(READING, lock, stop);
	    return getReadable();
	} else if (checkStateValue(DECACHING)) {
	    /* Evicting from cache -- wait until done, then retry */
	    await(NOT_CACHED, lock, stop);
	    return false;
	} else /* state == State.DECACHED */ {
	    /* Entry is not in the cache */
	    return false;
	}
    }	    

    /**
     * Sets this entry's state to {@link State#CACHED_READ} after fetching for
     * read, and notifies the lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#FETCHING_READ}
     */
    void setCachedRead(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.FETCHING_READ);
	state = State.CACHED_READ;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_READ} after downgrading,
     * and notifies the lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#EVICTING_DOWNGRADE}
     */
    void setEvictedDowngrade(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.EVICTING_DOWNGRADE);
	state = State.CACHED_READ;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_READ} directly from
     * {@link State#CACHED_WRITE} after an immediate downgrade when not in use,
     * and notifies the lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#CACHED_WRITE}
     */
    void setEvictedDowngradeImmediate(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.CACHED_WRITE);
	state = State.CACHED_READ;
	lock.notifyAll();
    }

    /* READING */


    /**
     * Returns whether this entry is being read.
     *
     * @return	whether this entry is being read
     */
    boolean getReading() {
	return checkStateValue(READING);
    }

    /* State.FETCHING_UPGRADE and UPGRADING */

    /**
     * Returns whether this entry is being upgraded.
     *
     * @return	whether this entry is being upgraded
     */
    boolean getUpgrading() {
	return checkStateValue(UPGRADING);
    }

    /**
     * Sets this entry's state to {@link State#FETCHING_UPGRADE}.
     *
     * @throws	IllegalStateException if the entry is not in state {@link
     *		State#CACHED_READ}
     */
    void setFetchingUpgrade() {
	verifyState(State.CACHED_READ);
	state = State.FETCHING_UPGRADE;
    }

    /**
     * Waits for this entry to finish being upgraded.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if the entry is not being upgraded
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitNotUpgrading(Object lock, long stop) {
	verifyState(State.FETCHING_UPGRADE, State.FETCHING_WRITE);
	awaitNot(UPGRADING, lock, stop);
    }

    /* State.CACHED_WRITE and WRITABLE */

    /**
     * Returns whether this entry is available for write.
     *
     * @return	whether this entry is available for write
     */
    boolean getWritable() {
	return checkStateValue(WRITABLE);
    }

    /** The possible results of calling {@link #awaitWritable}. */
    enum AwaitWritableResult {

	/** The entry has been decached. */
	DECACHED,

	/** The entry is readable. */
	READABLE,

	/** The entry is writable. */
	WRITABLE;
    }

    /**
     * Waits for this entry to become available for write.  Returns {@link
     * AwaitWritableResult#WRITABLE} if the entry is writable, {@link
     * AwaitWritableResult#READABLE} if the entry is readable but not writable,
     * and else {@link AwaitWritableResult#DECACHED} if the entry has become
     * decached.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    AwaitWritableResult awaitWritable(Object lock, long stop) {
	while (true) {
	    if (checkStateValue(WRITABLE)) {
		/* Already cached for write */
		return AwaitWritableResult.WRITABLE;
	    } else if (checkStateValue(UPGRADING)) {
		/* Wait for upgrading to complete, then retry */
		awaitNot(UPGRADING, lock, stop);
		continue;
	    } else if (checkStateValue(DOWNGRADING)) {
		/* Wait for downgrading to complete, then retry */
		awaitNot(DOWNGRADING, lock, stop);
		continue;
	    } else if (state == State.CACHED_READ) {
		/* Cached for read and not being upgraded. */
		return AwaitWritableResult.READABLE;
	    } else if (checkStateValue(READING)) {
		/* Wait for reading to complete, then retry */
		awaitNot(READING, lock, stop);
		continue;
	    } else if (checkStateValue(DECACHING)) {
		/* Evicting from cache -- wait until done, then retry */
		awaitDecached(lock, stop);
		return AwaitWritableResult.DECACHED;
	    } else /* state == State.DECACHED */ {
		/* Entry is not in the cache */
		return AwaitWritableResult.DECACHED;
	    }
	}
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was being
     * fetched for write, and notifies the associated lock, which should be
     * held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_WRITE}
     */
    void setCachedWrite(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.FETCHING_WRITE);
	state = State.CACHED_WRITE;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was being
     * upgraded, and notifies the associated lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#FETCHING_UPGRADE}
     */
    void setUpgraded(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.FETCHING_UPGRADE);
	state = State.CACHED_WRITE;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#CACHED_WRITE} when it was
     * modified, for use at the end of a transaction.
     *
     * @throws	IllegalStateException if the current state is not {@link
     *		State#CACHED_DIRTY}
     */
    void setNotModified() {
	verifyState(State.CACHED_DIRTY);
	state = State.CACHED_WRITE;
    }

    /* State.CACHED_DIRTY and MODIFIED */

    /**
     * Returns whether this entry is modified.
     *
     * @return	whether this entry is modified
     */
    boolean getModified() {
	return checkStateValue(MODIFIED);
    }

    /**
     * Sets this entry's state to {@link State#CACHED_DIRTY} when it is
     * modified.
     *
     * @throws	IllegalStateException if this entry's current state is not
     *		{@link State#CACHED_WRITE}
     */
    void setCachedDirty() {
	verifyState(State.CACHED_WRITE);
	state = State.CACHED_DIRTY;
    }

    /* State.EVICTING_DOWNGRADE and DOWNGRADING */

    /**
     * Returns whether this entry is being downgraded
     *
     * @return	whether this entry is being downgraded
     */
    boolean getDowngrading() {
	return checkStateValue(DOWNGRADING);
    }

    /**
     * Sets this entry's state to {@link State#EVICTING_DOWNGRADE}.
     *
     * @throws	IllegalStateException if this entry's current state is not
     *		{@link State#CACHED_WRITE}
     */
    void setEvictingDowngrade() {
	verifyState(State.CACHED_WRITE);
	state = State.EVICTING_DOWNGRADE;
    }

    /**
     * Waits for this entry to finish being downgraded.
     *
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if the entry is not being downgraded
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitNotDowngrading(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	verifyState(State.EVICTING_DOWNGRADE, State.EVICTING_WRITE);
	awaitNot(DOWNGRADING, lock, stop);
    }

    /* State.EVICTING_READ and DECACHING */

    /**
     * Returns whether this entry is being decached.
     *
     * @return	whether this entry is being decached
     */
    boolean getDecaching() {
	return checkStateValue(DECACHING);
    }

    /**
     * Sets this entry's state to {@link State#EVICTING_READ} or {@link
     * State#EVICTING_WRITE}, depending on whether it is cached for read or
     * write.
     *
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#CACHED_READ} or {@link State#CACHED_WRITE}
     */
    void setEvicting() {
	verifyState(State.CACHED_READ, State.CACHED_WRITE);
	state = (state == State.CACHED_READ)
	    ? State.EVICTING_READ : State.EVICTING_WRITE;
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
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	IllegalStateException if the entry is not decaching or decached
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    void awaitDecached(Object lock, long stop) {
	assert Thread.holdsLock(lock);
	if (!getDecached()) {
	    verifyState(State.EVICTING_READ, State.EVICTING_WRITE);
	    await(NOT_CACHED, lock, stop);
	}
    }

    /**
     * Sets this entry's state to {@link State#DECACHED} after being evicted
     * for read or write, and notifies the lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#EVICTING_READ} or {@link State#EVICTING_WRITE}
     */
    void setEvicted(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.EVICTING_READ, State.EVICTING_WRITE);
	state = State.DECACHED;
	lock.notifyAll();
    }

    /**
     * Sets this entry's state to {@link State#DECACHED} directly from {@link
     * State#CACHED_READ} or {@link State#CACHED_WRITE} after an immediate
     * eviction when not in use, and notifies the lock, which should be held.
     *
     * @param	lock the associated lock
     * @throws	IllegalStateException if the entry's current state is not
     *		{@link State#EVICTING_WRITE}
     */
    void setEvictedImmediate(Object lock) {
	assert Thread.holdsLock(lock);
	verifyState(State.CACHED_READ, State.CACHED_WRITE);
	state = State.DECACHED;
	lock.notifyAll();
    }

    /* -- Other methods -- */

    /**
     * Notes that this entry has been accessed by a transaction with the
     * specified context ID.
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
     */
    V getValue() {
	return value;
    }

    /**
     * Updates the value of the entry.
     *
     * @param	newValue the new value of the entry
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
     * Checks if the one bits in the argument are set in this entry's state
     * value.
     *
     * @param	value the value to check
     * @return	{@code true} if the one bits in the argument are set, else
     *		{@code false}
     */
    private boolean checkStateValue(int value) {
	return (state.value & value) == value;
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
		"Invalid state, expected " + expected + ", found " + state);
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
		expected2 + ", found " + state);
	}
    }

    /**
     * Waits for this entry's state value to have all bits set that are set in
     * {@code desiredStateValue}.
     * 
     * @param	value the value to check
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionTimeoutException if desired state value does not
     *		appear before the specified stop time
     */
    private void await(int desiredStateValue, Object lock, long stop) {
	if (checkStateValue(desiredStateValue)) {
	    return;
	}
	long now = System.currentTimeMillis();
	while (true) {
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for entry " + this, e);
	    }
	    if (checkStateValue(desiredStateValue)) {
		return;
	    }
	    now = System.currentTimeMillis();
	    if (now >= stop) {
		throw new TransactionTimeoutException(
		    "Timeout waiting for entry " + this);
	    }
	}
    }

    /**
     * Waits for this entry's state value to have all bits cleared that are set
     * in {@code desiredStateValue}.
     * 
     * @param	value the value to check
     * @param	lock the associated lock, which should be held
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	TransactionTimeoutException if desired state value does not
     *		appear before the specified stop time
     */
    private void awaitNot(int undesiredState, Object lock, long stop) {
	if ((state.value & undesiredState) == 0) {
	    return;
	}
	long now = System.currentTimeMillis();
	while (true) {
	    try {
		lock.wait(stop - now);
	    } catch (InterruptedException e) {
		throw new TransactionInterruptedException(
		    "Interrupt while waiting for entry " + this, e);
	    }
	    if ((state.value & undesiredState) == 0) {
		return;
	    }
	    now = System.currentTimeMillis();
	    if (now >= stop) {
		throw new TransactionTimeoutException(
		    "Timeout waiting for entry " + this);
	    }
	}
    }
}
