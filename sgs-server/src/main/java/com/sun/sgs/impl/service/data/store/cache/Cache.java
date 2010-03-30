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

import static com.sun.sgs.impl.service.data.store.AbstractDataStore.checkOid;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The cache of objects and bindings. <p>
 *
 * The implementation uses concurrent skip lists so that cache eviction can
 * walk over cache entries concurrently with other operations.  The weak
 * consistency of the skip list's iterator is well suited to this purpose
 * because it is OK for cache eviction to miss entries during eviction -- they
 * can always be evicted the next time. <p>
 *
 * The implementation also uses a set of locks that should be held when
 * manipulating the entry for an associated key.  Since the lock to use is
 * determined by the key, it can be used to lock access to the entry for a
 * particular key even if the entry is not currently present in the cache. <p>
 *
 * Note that operations to add a cache entry are performed with the lock
 * associated with the entry already held.  To avoid deadlock, the callers of
 * these operations need to reserve space in the cache before the lock is
 * acquired. <p>
 *
 * If this approach were not used, consider the following situation, which
 * would in deadlock:
 *
 * <ul>
 * <li> Task:
 *   <ol>
 *   <li> Grab lock A for key X
 *   <li> Attempt to reserve space in cache in order to create an entry for X
 *     and find that cache is full
 *   <li> Wait for space to become available
 *   </ol>
 * <li> Eviction thread:
 *   <ol>
 *   <li> Attempt to grab lock A to determine if some cache entry protected by
 *     that lock is available for eviction
 *   <li> Wait for lock to become available
 *   </ol>
 * </ul>
 */
class Cache {

    /** The data store. */
    private final CachingDataStore store;

    /*
     * TODO: Support modifying the cache size on the fly.
     * -tjb@sun.com (01/12/2010)
     */
    /** The maximum number of entries that can be stored in the cache. */
    private final int cacheSize;

    /** The number of open slots available in the cache. */
    private final Semaphore available;

    /**
     * The locks for objects and bindings in the cache -- the contents should
     * not be modified.
     */
    private final Object[] locks;

    /** The object to notify when the cache becomes full. */
    private final CacheFullNotifier cacheFullNotify;

    /** Maps object IDs to object cache entries. */
    private final ConcurrentMap<Long, ObjectCacheEntry> objectMap =
	new ConcurrentSkipListMap<Long, ObjectCacheEntry>();

    /** The number of objects in the cache. */
    private final AtomicInteger objectCount = new AtomicInteger(0);

    /** Maps binding keys to binding cache entries. */
    private final ConcurrentNavigableMap<BindingKey, BindingCacheEntry>
	bindingMap =
	    new ConcurrentSkipListMap<BindingKey, BindingCacheEntry>();

    /** The number of bindings in the cache. */
    private final AtomicInteger bindingCount = new AtomicInteger(0);

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store, for detecting shutdown
     * @param	cacheSize the maximum number of entries permitted in the cache
     * @param	numLocks the number of locks to use for entries in the cache
     * @param	cacheFullNotify the object to notify when the cache becomes
     *		full
     */
    Cache(CachingDataStore store,
	  int cacheSize,
	  int numLocks,
	  CacheFullNotifier cacheFullNotify)
    {
	this.store = store;
	this.cacheSize = cacheSize;
	checkNull("cacheFullNotify", cacheFullNotify);
	available = new Semaphore(cacheSize);
	locks = new Object[numLocks];
	for (int i = 0; i < numLocks; i++) {
	    locks[i] = new Object();
	}
	this.cacheFullNotify = cacheFullNotify;
    }

    /**
     * Returns the object to use for locking the cache entry of the object with
     * the specified ID.
     *
     * @param	oid the object ID
     * @return	the associated lock
     */
    Object getObjectLock(long oid) {
	/* Use the same value as new Long(oid).hashCode() */
	return getEntryLock(ObjectCacheEntry.keyHashCode(oid));
    }

    /**
     * Returns the object to use for locking the cache entry of the binding with
     * the specified binding key.
     *
     * @param	key the binding key
     * @return	the associated lock
     */
    Object getBindingLock(BindingKey key) {
	return getEntryLock(key.hashCode());
    }

    /**
     * Returns the object to use for locking the specified cache entry.
     *
     * @param	entry the cache entry
     * @return	the associated lock
     */
    Object getEntryLock(BasicCacheEntry<?, ?> entry) {
	return getEntryLock(entry.key.hashCode());
    }

    /** Returns the lock for the entry whose key has the specified hashcode. */
    private Object getEntryLock(int hashCode) {
	/* Mask off the sign bit to get a positive value */
	int index = (hashCode & Integer.MAX_VALUE) % locks.length;
	return locks[index];
    }

    /**
     * Returns the cache entry for the object with the specified object ID, or
     * {@code null} if it is not found.
     *
     * @param	oid the object ID
     * @return	the associated cache entry or {@code null}
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    ObjectCacheEntry getObjectEntry(long oid) {
	checkOid(oid);
	return objectMap.get(oid);
    }

    /**
     * Returns the cache entry for the binding with the specified key, or
     * {@code null} if it is not found.
     *
     * @param	key the binding key
     * @return	the associated cache entry or {@code null}
     */
    BindingCacheEntry getBindingEntry(BindingKey key) {
	checkNull("key", key);
	return bindingMap.get(key);
    }

    /**
     * Returns the cache entry for the binding with the lowest key that is
     * equal to or higher than the one specified, or {@code null} if none is
     * found.
     *
     * @param	key the binding key
     * @return	the associated or next higher cache entry, or {@code null}
     */
    BindingCacheEntry getCeilingBindingEntry(BindingKey key) {
	checkNull("key", key);
	Entry<BindingKey, BindingCacheEntry> entry =
	    bindingMap.ceilingEntry(key);
	return (entry == null) ? null : entry.getValue();
    }

    /**
     * Returns the cache entry for the binding with the lowest key that is
     * higher than the one specified, or {@code null} if none is found.
     *
     * @param	key the binding key
     * @return	the next higher cache entry or {@code null}
     */
    BindingCacheEntry getHigherBindingEntry(BindingKey key) {
	checkNull("key", key);
	Entry<BindingKey, BindingCacheEntry> entry =
	    bindingMap.higherEntry(key);
	return (entry == null) ? null : entry.getValue();
    }

    /**
     * Returns the cache entry for the binding with a key that is lower than
     * the one specified, or {@code null} if none is found.
     *
     * @param	key the binding key
     * @return	the next lower cache entry or {@code null}
     */
    BindingCacheEntry getLowerBindingEntry(BindingKey key) {
	checkNull("key", key);
	Entry<BindingKey, BindingCacheEntry> entry =
	    bindingMap.lowerEntry(key);
	return (entry == null) ? null : entry.getValue();
    }

    /**
     * Returns a new cache reservation that can be passed in calls to {@link
     * #addObjectEntry} or {@link #addBindingEntry} to add new entries to the
     * cache.  Callers should call {@link Reservation#done} to release any
     * unused space after they are done using the reservation.  Callers should
     * make sure that they are not holding locks on the cache when calling this
     * method to avoid deadlocks with eviction.
     *
     * @param	numCacheEntries the number of cache entries to reserve
     * @return	the cache reservation
     * @throws	IllegalArgumentException if the argument is less than {@code 1}
     */
    Reservation getReservation(int numCacheEntries) {
	return new Reservation(numCacheEntries);
    }

    /**
     * Returns a cache reservation that transfers its reservations from another
     * instance, and that can be passed in calls to {@link #addObjectEntry} or
     * {@link #addBindingEntry} to add new entries to the cache.  Callers
     * should call {@link Reservation#done} to release any unused space after
     * they are done using the reservation.  Callers should make sure that they
     * are not holding locks on the cache when calling this method to avoid
     * deadlocks with eviction.
     *
     * @param	otherReserve the existing reservation
     * @param	numCacheEntries the number of cache entries to transfer
     * @return	the cache reservation
     * @throws	IllegalArgumentException if the argument is less than {@code 1}
     *		or if it is larger than the number of entries reserved by
     *		{@code otherReserve}
     */
    Reservation getReservation(Reservation otherReserve, int numCacheEntries) {
	return new Reservation(otherReserve, numCacheEntries);
    }

    /** Keeps track of reserving and releasing space in the cache. */
    final class Reservation {

	/** The number of reserved cache entries that have not been used. */
	private int unusedCacheEntries;

	/**
	 * Creates an instance that reserves the specified number of cache
	 * entries.
	 *
	 * @param	numCacheEntries the number of cache entries to reserve
	 * @throws	IllegalArgumentException if the argument is less than 
	 *		{@code 1}
	 */
	private Reservation(int numCacheEntries) {
	    if (numCacheEntries < 1) {
		throw new IllegalArgumentException(
		    "The number of cache entries must be at least 1");
	    }
	    unusedCacheEntries = numCacheEntries;
	    reserve(numCacheEntries);
	}

	/**
	 * Creates an instance that transfers its reservations from another
	 * instance.
	 *
	 * @param	otherReserve the existing reserve
	 * @param	numCacheEntries the number of cache entries to transfer
	 * @throws	IllegalArgumentException if the argument is less than
	 *		{@code 1} or if it is larger than the number of entries
	 *		reserved by {@code otherReserve}
	 */
	private Reservation(Reservation otherReserve, int numCacheEntries) {
	    if (numCacheEntries < 1) {
		throw new IllegalArgumentException(
		    "The number of cache entries must be at least 1");
	    } else if (numCacheEntries > otherReserve.unusedCacheEntries) {
		throw new IllegalArgumentException(
		    "Other reserve doesn't have enough entries");
	    }
	    unusedCacheEntries = numCacheEntries;
	    otherReserve.unusedCacheEntries -= numCacheEntries;
	}

	/** Releases any unused cache entries. */
	void done() {
	    if (unusedCacheEntries > 0) {
		release(unusedCacheEntries);
	    }
	}

	/**
	 * Notes that the specified number of cache entries have been used.
	 *
	 * @param	numCacheEntries the number of entries used
	 * @throws	IllegalStateException if there are not enough unused
	 *		entries
	 */
	private void used(int numCacheEntries) {
	    if (unusedCacheEntries < numCacheEntries) {
		throw new IllegalStateException("Not enough unused entries");
	    }
	    unusedCacheEntries -= numCacheEntries;
	}
    }

    /**
     * Adds an entry for a previously uncached object, using space that the
     * caller has already reserved in the cache.
     *
     * @param	entry the new cache entry
     * @param	reserve for tracking space reserved in the cache
     * @throws	IllegalArgumentException if an entry with the same key is
     *		already present
     */
    void addObjectEntry(ObjectCacheEntry entry, Reservation reserve) {
	ObjectCacheEntry existing = objectMap.putIfAbsent(entry.key, entry);
	if (existing != null) {
	    throw new IllegalArgumentException(
		"Entry is already present: " + existing);
	}
	objectCount.incrementAndGet();
	reserve.used(1);
    }

    /**
     * Adds an entry for a previously uncached binding, using space that the
     * caller has already reserved in the cache.
     *
     * @param	entry the new cache entry
     * @param	reserve for tracking space reserved in the cache
     * @throws	IllegalArgumentException if an entry with the same key is
     *		already present
     */
    void addBindingEntry(BindingCacheEntry entry, Reservation reserve) {
	BindingCacheEntry existing = bindingMap.putIfAbsent(entry.key, entry);
	if (existing != null) {
	    throw new IllegalArgumentException(
		"Entry is already present: " + existing);
	}
	bindingCount.incrementAndGet();
	reserve.used(1);
    }

    /**
     * Reserves space in the cache for the specified number of new entries,
     * blocking until the specified number of entries is available.
     *
     * @param	count the number of entries
     */
    void reserve(int count) {
	if (!tryReserve(count)) {
	    cacheFullNotify.cacheIsFull();
	    while (!store.getShutdownRequested()) {
		try {
		    available.acquire(count);
		    break;
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    /**
     * Attempts to reserve space in the cache for the specified number of new
     * entries, returning immediately whether the attempt was successful.
     *
     * @param	count the number of entries
     * @return	whether the entries were reserved
     */
    boolean tryReserve(int count) {
	return available.tryAcquire(count);
    }

    /**
     * Returns the number of spaces available in the cache for new entries.
     *
     * @return	the number of spaces available
     */
    int available() {
	return available.availablePermits();
    }

    /**
     * Releases the specified number of previously reserved spaces in the
     * cache, making them available for new entries.
     *
     * @param	count the number of spaces to make available
     */
    void release(int count) {
	available.release(count);
    }

    /**
     * Removes the cache entry for the object with the specified object ID,
     * which should already have been marked as decached.
     *
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative, if the
     *		entry is not found, or if the entry is not marked decached
     */
    void removeObjectEntry(long oid) {
	checkOid(oid);
	ObjectCacheEntry entry = objectMap.remove(oid);
	if (entry == null) {
	    throw new IllegalArgumentException(
		"Object entry was not found: " + oid);
	} else if (!entry.getDecached()) {
	    throw new IllegalArgumentException(
		"Entry was not decached: " + entry);
	}
	available.release();
	objectCount.decrementAndGet();
    }

    /**
     * Removes the cache entry for the binding with the specified binding key.
     *
     * @param	key the binding key
     * @throws	IllegalArgumentException if the entry is not found or if the
     *		entry is not marked decached
     */
    void removeBindingEntry(BindingKey key) {
	checkNull("key", key);
	BindingCacheEntry entry = bindingMap.remove(key);
	if (entry == null) {
	    throw new IllegalArgumentException(
		"Binding entry was not found: " + key);
	} else if (!entry.getDecached()) {
	    throw new IllegalArgumentException(
		"Entry was not decached: " + entry);
	}
	available.release();
	bindingCount.decrementAndGet();
    }

    /**
     * Returns an iterator over the entries in the cache.  The iterator is
     * weakly consistent, returning elements reflecting the state of the map at
     * some point at or since the creation of the iterator.  It does not throw
     * {@link ConcurrentModificationException}, and may proceed concurrently
     * with other operations.  The iterator iterates over batches of entries of
     * the specified size where the proportion of object and binding entries in
     * the batch roughly approximates the ratio of those types of entries in
     * the cache.  This iterator does not support the {@link Iterator#remove}
     * method.
     *
     * @param	batchSize the batch size
     * @return	the iterator
     */
    Iterator<BasicCacheEntry<?, ?>> getEntryIterator(int batchSize) {
	return new EntryIterator(batchSize);
    }

    /** Implement iteration over cache entries. */
    private class EntryIterator implements Iterator<BasicCacheEntry<?, ?>> {

	/** The batch size. */
	private final int batchSize;

	/** An iterator over objects in the cache. */
	private final Iterator<ObjectCacheEntry> objectIterator;

	/** An iterator over bindings in the cache. */
	private final Iterator<BindingCacheEntry> bindingIterator;

	/** The number of objects remaining for the current batch. */
	private int remainingObjects;

	/** The number of bindings remaining for the current batch. */
	private int remainingBindings;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	batchSize the batch size.
	 */
	EntryIterator(int batchSize) {
	    this.batchSize = batchSize;
	    objectIterator = objectMap.values().iterator();
	    bindingIterator = bindingMap.values().iterator();
	    computeBatch();
	}

	/** Sets remainingObjects and remainingBindings. */
	private void computeBatch() {
	    double total = (double) (cacheSize - available());
	    /*
	     * Compute the ceiling to insure that, if there are any objects or
	     * bindings left, that each batch includes at least one of them.
	     */
	    remainingObjects = (int) Math.ceil(
		batchSize * (objectCount.get() / total));
	    remainingBindings = (int) Math.ceil(
		batchSize * (bindingCount.get() / total));
	}

	@Override
	public boolean hasNext() {
	    return objectIterator.hasNext() || bindingIterator.hasNext();
	}

	@Override
	public BasicCacheEntry<?, ?> next() {
	    while (true) {
		if (objectIterator.hasNext() && remainingObjects > 0) {
		    remainingObjects--;
		    return objectIterator.next();
		} else if (bindingIterator.hasNext() &&
			   remainingBindings > 0)
		{
		    remainingBindings--;
		    return bindingIterator.next();
		} else if (hasNext()) {
		    computeBatch();
		} else {
		    throw new NoSuchElementException();
		}
	    }
	}

	@Override
	public void remove() {
	    throw new UnsupportedOperationException(
		"This iterator does not support the remove method");
	}
    }

    /** Prints the contents of the cache -- for testing. */
    void printContents() {
	printObjects();
	printBindings();
    }

    /** Prints the objects in the cache -- for testing. */
    void printObjects() {
	System.err.println("Objects:");
	for (ObjectCacheEntry entry : objectMap.values()) {
	    System.err.println("  " + entry);
	}
    }

    /** Prints the bindings in the cache -- for testing. */
    void printBindings() {
	System.err.println("Bindings:");
	for (BindingCacheEntry entry : bindingMap.values()) {
	    System.err.println("  " + entry);
	}
    }

    /**
     * Checks the consistency of fields in bindings, throwing an assertion
     * error if an inconsistency is found.  This method should be used for
     * testing only, and may be quite expensive to call.
     */
    void checkBindings() {
	long lockTimeout = store.getLockTimeout();
	for (BindingCacheEntry entry : bindingMap.values()) {
	    entry.checkState(this, lockTimeout);
	}
    }
}
