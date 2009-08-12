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

import static com.sun.sgs.impl.service.data.store.AbstractDataStore.checkOid;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** The cache of objects and bindings. */
class Cache {

    /** The data store. */
    private final CachingDataStore store;

    /** The cache size. */
    private final int cacheSize;

    /** The number of open slots available in the cache. */
    private final Semaphore available;

    /** The locks for objects and bindings in the cache. */
    private final Object[] locks;

    /** The object to notify when the cache becomes full. */
    private final FullNotifier cacheFullNotify;

    /** Maps object IDs to object cache entries. */
    private final Map<Long, ObjectCacheEntry> objectMap =
	new ConcurrentSkipListMap<Long, ObjectCacheEntry>();

    /** The number of objects in the cache. */
    private final AtomicInteger objectCount = new AtomicInteger(0);

    /** Maps binding keys to binding cache entries. */
    private final NavigableMap<BindingKey, BindingCacheEntry> bindingMap =
	new ConcurrentSkipListMap<BindingKey, BindingCacheEntry>();

    /** The number of bindings in the cache. */
    private final AtomicInteger bindingCount = new AtomicInteger(0);

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store, for detecting shutdown
     * @param	cacheSize the maximum number of items permitted in the cache
     * @param	numLocks the number of locks to use for items in the cache
     * @param	cacheFullNotify the object to notify when the cache becomes
     *		full
     */
    Cache(CachingDataStore store,
	  int cacheSize,
	  int numLocks,
	  FullNotifier cacheFullNotify)
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

    /** An interface for receiving notifications that the cache is full. */
    public interface FullNotifier {

	/** Provides notification that the cache is full. */
	void cacheIsFull();
    }

    /**
     * Returns the object to use for locking the cache entry of the object with
     * the specified ID.
     *
     * @param	oid the object ID
     * @return	the associated lock
     */
    Object getObjectLock(Long oid) {
	return getEntryLock(oid.hashCode());
    }

    /**
     * Returns the object to use for locking the cache entry of the object with
     * the specified ID.
     *
     * @param	oid the object ID
     * @return	the associated lock
     */
    Object getObjectLock(long oid) {
	/* Use the same value as new Long(oid).hashCode */
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
	ObjectCacheEntry entry = objectMap.get(oid);
	return entry;
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
	BindingCacheEntry entry = bindingMap.get(key);
	return entry;
    }

    /**
     * Returns the cache entry for the binding with a key that is equal or
     * higher than the one specified, or {@code null} if none is found.
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
     * Returns the cache entry for the binding with a key that is higher than
     * the one specified, or {@code null} if none is found.
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
     * Adds an entry for a previously uncached object.
     *
     * @param	entry the new cache entry
     */
    void addObjectEntry(ObjectCacheEntry entry) {
	assert !objectMap.containsKey(entry.key);
	reserve(1);
	objectCount.incrementAndGet();
	objectMap.put(entry.key, entry);
    }

    /**
     * Adds an entry for a previously uncached binding.
     *
     * @param	entry the new cache entry
     */
    void addBindingEntry(BindingCacheEntry entry) {
	assert !bindingMap.containsKey(entry.key);
	reserve(1);
	bindingCount.incrementAndGet();
	bindingMap.put(entry.key, entry);
    }

    /**
     * Reserves space in the cache for the specified number of new entries.
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
	return available.tryAcquire();
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
     * Adds an entry for a previously uncached object, assuming that a space
     * for the new entry has already been reserved.
     *
     * @param	entry the new cache entry
     */
    void addReservedObjectEntry(ObjectCacheEntry entry) {
	assert !objectMap.containsKey(entry.key);
	objectCount.incrementAndGet();
	objectMap.put(entry.key, entry);
    }

    /**
     * Adds an entry for a previously uncached binding, assuming that a space
     * for the new entry has already been reserved.
     *
     * @param	entry the new cache entry
     */
    void addReservedBindingEntry(BindingCacheEntry entry) {
	assert !bindingMap.containsKey(entry.key)
	    : "Entry already in cache: " + entry;
	bindingCount.incrementAndGet();
	bindingMap.put(entry.key, entry);
    }

    /**
     * Removes the cache entry for the object with the specified object ID.
     *
     * @param	oid the object ID
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    void removeObjectEntry(long oid) {
	checkOid(oid);
	ObjectCacheEntry entry = objectMap.remove(oid);
	assert entry != null;
	assert entry.getDecached();
	available.release();
	objectCount.decrementAndGet();
    }

    /**
     * Removes the cache entry for the binding with the specified binding key.
     *
     * @param	key the binding key
     */
    void removeBindingEntry(BindingKey key) {
	checkNull("key", key);
	BindingCacheEntry entry = bindingMap.remove(key);
	assert entry != null;
	assert entry.getDecached();
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
     * the cache.  The iterator does not support the {@link Iterator#remove}
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
	    resetBatch();
	}
	
	/** Resets remainingObjects and remainingBindings. */
	private void resetBatch() {
	    double total = (double) (cacheSize - available());
	    remainingObjects = (int) (batchSize * (objectCount.get() / total));
	    remainingBindings =
		(int) (batchSize * (bindingCount.get() / total));
	}

	public boolean hasNext() {
	    return objectIterator.hasNext() || bindingIterator.hasNext();
	}

	public BasicCacheEntry<?, ?> next() {
	    boolean repeated = false;
	    while (true) {
		boolean moreObjects = objectIterator.hasNext();
		if (moreObjects && remainingObjects > 0) {
		    remainingObjects--;
		    return objectIterator.next();
		} else if (bindingIterator.hasNext() &&
			   (!moreObjects || remainingBindings > 0))
		{
		    remainingBindings--;
		    return bindingIterator.next();
		} else if (repeated) {
		    throw new NoSuchElementException();
		}
		resetBatch();
		repeated = true;
	    }
	}

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

    /** Checks consistency of previous key fields of bindings. */
    void checkBindings() {
	BindingKey previousKey = null;
	for (BindingCacheEntry entry : bindingMap.values()) {
	    if (previousKey != null) {
		synchronized (getEntryLock(entry)) {
		    entry.checkPreviousKey(previousKey);
		}
	    }
	    previousKey = entry.key;
	}
    }
}
