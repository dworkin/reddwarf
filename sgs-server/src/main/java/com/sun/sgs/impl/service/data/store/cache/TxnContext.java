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

import com.sun.sgs.service.Transaction;
import java.util.LinkedList;
import java.util.List;

/** Maintains state associated with a transaction. */
class TxnContext {

    /** The associated transaction. */
    final Transaction txn;

    /** The data store. */
    final CachingDataStore store;

    /**
     * The identifier for this transaction, which is assigned in order at the
     * time that the data store joins the transaction.
     */
    private final long contextId;

    /**
     * A list of the objects modified in this transaction and their previous
     * values, or {@code null} if no objects have been modified.
     */
    private List<SavedValue<Long, byte[]>> modifiedObjects = null;

    /**
     * A list of the bindings modified in this transaction and their previous
     * values, or {@code null} if no bindings have been modified.
     */
    private List<SavedBindingValue> modifiedBindings = null;

    /** Whether the transaction has been prepared. */
    private boolean prepared;

    /**
     * Creates an instance of this class.
     *
     * @param	txn the associated transaction
     * @param	store the data store
     */
    TxnContext(Transaction txn, CachingDataStore store) {
	this.txn = txn;
	this.store = store;
	contextId = store.getUpdateQueue().beginTxn();
    }

    @Override
    public String toString() {
	return "TxnContext[txn:" + txn + "]";
    }

    /**
     * Returns whether the transaction has been prepared.
     *
     * @return	whether the transaction has been prepared
     */
    boolean getPrepared() {
	return prepared;
    }

    /**
     * Prepares for committing the transaction.
     *
     * @return	whether there were no modifications made in this transaction
     * @throws	IllegalStateException if there is a problem with the
     *		transaction 
     */
    boolean prepare() {
	if (prepared) {
	    throw new IllegalStateException(
		"Transaction has already been prepared: " + txn);
	} else if (!getModified()) {
	    store.getUpdateQueue().abort(contextId, false);
	    return true;
	} else {
	    store.getUpdateQueue().prepare(getStopTime());
	    prepared = true;
	    return false;
	}
    }

    /**
     * Prepares and commits the transaction.
     *
     * @throws	IllegalStateException if there is a problem with the
     *		transaction 
     */
    void prepareAndCommit() {
	if (prepared) {
	    throw new IllegalStateException(
		"Transaction has already been prepared: " + txn);
	} else if (!getModified()) {
	    store.getUpdateQueue().abort(contextId, false);
	} else {
	    store.getUpdateQueue().prepare(getStopTime());
	    prepared = true;
	    commitInternal();
	}
    }

    /**
     * Commits the transaction.
     *
     * @throws	IllegalStateException if there is a problem with the
     *		transaction 
     */
    void commit() {
	if (!prepared) {
	    throw new IllegalStateException(
		"Transaction has not been prepared: " + txn);
	} else if (!getModified()) {
	    throw new IllegalStateException(
		"Transaction is not modified: " + txn);
	}
	commitInternal();
    }

    /** Aborts the transaction. */
    void abort() {
	store.getUpdateQueue().abort(contextId, prepared);
	Cache cache = store.getCache();
	if (modifiedObjects != null) {
	    for (SavedValue<Long, byte[]> saved : modifiedObjects) {
		synchronized (cache.getObjectLock(saved.key)) {
		    ObjectCacheEntry entry = cache.getObjectEntry(saved.key);
		    entry.setValue(saved.restoreValue);
		    entry.setNotModified();
		}
	    }
	}
	if (modifiedBindings != null) {
	    for (SavedBindingValue saved : modifiedBindings) {
		synchronized (cache.getBindingLock(saved.key)) {
		    BindingCacheEntry entry = cache.getBindingEntry(saved.key);
		    entry.setValue(saved.restoreValue);
		    entry.setNotModified();
		    entry.setPreviousKey(saved.restorePreviousKey,
					 saved.restorePreviousKeyUnbound);
		}
	    }
	}
    }

    /**
     * Returns the time that a blocking operation starting now on behalf of
     * the specified transaction should timeout.
     */
    long getStopTime() {
	return Math.min(
	    addCheckOverflow(System.currentTimeMillis(),
			     store.getLockTimeout()),
	    addCheckOverflow(txn.getCreationTime(), txn.getTimeout()));
    }

    /**
     * Returns the context ID for this transaction.
     *
     * @return	the context ID
     */
    long getContextId() {
	return contextId;
    }

    /* -- Entry methods -- */

    /**
     * Adds an entry to the cache for a newly allocated object.  The associated
     * lock should be held.
     *
     * @param	oid the object ID of the new object
     */
    void noteNewObject(long oid) {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry = ObjectCacheEntry.createNew(oid, contextId);
	store.getCache().addObjectEntry(entry);
    }

    /**
     * Adds an entry to the cache for an object that is being fetched from the
     * server.  The associated lock should be held.
     *
     * @param	oid the object ID of the object
     * @param	forUpdate whether the object is being fetched for update
     */
    ObjectCacheEntry noteFetchingObject(long oid, boolean forUpdate) {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createFetching(oid, forUpdate);
	store.getCache().addObjectEntry(entry);
	return entry;
    }

    /**
     * Adds an entry to the cache for an object that is being cached for read
     * after being fetched from the server.  The associated lock should be
     * held.
     *
     * @param	oid the object ID of the object
     * @param	data the data for the object
     */
    ObjectCacheEntry noteCachedObject(long oid, byte[] data) {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createCached(oid, data, contextId);
	store.getCache().addObjectEntry(entry);
	return entry;
    }

    /**
     * Adds an entry to the cache that represents the last bound name in the
     * cache.  The newly created entry will be marked as being fetched.  The
     * associated lock should be held.
     *
     * @param	forUpdate whether the last name is being fetched for update
     * @return	the new cache entry
     */
    BindingCacheEntry noteLastBinding(boolean forUpdate) {
	assert Thread.holdsLock(
	    store.getCache().getBindingLock(BindingKey.LAST));
	BindingCacheEntry entry = BindingCacheEntry.createLast(forUpdate);
	store.getCache().addBindingEntry(entry);
	return entry;
    }

    /**
     * Adds an entry to the cache for a name binding.
     *
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	forUpdate whether the entry is cached for update
     */
    BindingCacheEntry noteCachedBinding(
	BindingKey key, long value, boolean forUpdate)
    {
	assert Thread.holdsLock(store.getCache().getBindingLock(key));
	BindingCacheEntry entry =
	    BindingCacheEntry.createCached(key, value, forUpdate, contextId);
	store.getCache().addBindingEntry(entry);
	return entry;
    }


    /**
     * Adds an entry to the cache for a name binding given that a slot was
     * already reserved.
     *
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	forUpdate whether the entry is cached for update
     */
    BindingCacheEntry noteCachedReservedBinding(
	BindingKey key, long value, boolean forUpdate)
    {
	assert Thread.holdsLock(store.getCache().getBindingLock(key));
	BindingCacheEntry entry =
	    BindingCacheEntry.createCached(key, value, forUpdate, contextId);
	store.getCache().addReservedBindingEntry(entry);
	return entry;
    }

    /**
     * Note that an entry has been accessed by the associated transaction.
     *
     * @param	entry the cache entry
     */
    void noteAccess(BasicCacheEntry<?, ?> entry) {
	assert Thread.holdsLock(store.getCache().getEntryLock(entry));
	entry.noteAccess(contextId);
    }

    /**
     * Note that an object entry has been cached after fetching from the server
     * on behalf of the associated transaction.
     *
     * @param	entry the cache entry
     * @param	data the newly retrieved data
     * @param	forUpdate whether the entry was cached for update
     */
    void noteCachedObject(
	ObjectCacheEntry entry, byte[] data, boolean forUpdate)
    {
	Object lock = store.getCache().getObjectLock(entry.key);
	assert Thread.holdsLock(lock);
	entry.setValue(data);
	entry.noteAccess(contextId);
	if (forUpdate) {
	    entry.setCachedWrite(lock);
	} else {
	    entry.setCachedRead(lock);
	}
    }

    /**
     * Note that an object has been modified by this transaction.
     *
     * @param	entry the cache entry
     * @param	data the new data value
     */
    void noteModifiedObject(ObjectCacheEntry entry, byte[] data) {
	assert Thread.holdsLock(store.getCache().getObjectLock(entry.key));
	if (!entry.getModified()) {
	    if (modifiedObjects == null) {
		modifiedObjects = new LinkedList<SavedValue<Long, byte[]>>();
	    }
	    SavedValue<Long, byte[]> saved =
		new SavedValue<Long, byte[]>(entry);
	    assert !modifiedObjects.contains(saved);
	    modifiedObjects.add(saved);
	    entry.setCachedDirty();
	}
	entry.setValue(data);
	entry.noteAccess(contextId);
    }

    /**
     * Note that a binding has been modified by this transaction.
     *
     * @param	entry the binding entry
     * @param	oid the new object ID
     */
    void noteModifiedBinding(BindingCacheEntry entry, long oid) {
	assert Thread.holdsLock(store.getCache().getBindingLock(entry.key));
	if (!entry.getModified() && entry.key != BindingKey.LAST) {
	    if (modifiedBindings == null) {
		modifiedBindings = new LinkedList<SavedBindingValue>();
	    }
	    SavedBindingValue saved = new SavedBindingValue(entry);
	    assert !modifiedBindings.contains(saved);
	    modifiedBindings.add(saved);
	    entry.setCachedDirty();
	}
	entry.setValue(oid);
	entry.noteAccess(contextId);
    }

    /* -- Private methods and classes -- */

    private static long addCheckOverflow(long x, long y) {
	assert x >= 0 && y >= 0;
	long result = x + y;
	return (result >= 0) ? result : Long.MAX_VALUE;
    }

    /** Adds the commit request to the update queue. */
    private void commitInternal() {
	Cache cache = store.getCache();
	long[] oids = new long[
	    (modifiedObjects == null) ? 0 : modifiedObjects.size()];
	byte[][] oidValues = new byte[oids.length][];
	int newOids = 0;
	if (modifiedObjects != null) {
	    int i = 0;
	    for (int pass = 1; pass <= 2; pass++) {
		boolean includeNew = (pass == 1);
		for (SavedValue<Long, byte[]> saved : modifiedObjects) {
		    boolean isNew = (saved.restoreValue == null);
		    if (includeNew == isNew) {
			if (isNew) {
			    newOids++;
			}
			long oid = saved.key;
			oids[i] = oid;
			Object lock = cache.getObjectLock(oid);
			synchronized (lock) {
			    ObjectCacheEntry entry = cache.getObjectEntry(oid);
			    oidValues[i] = entry.getValue();
			    entry.setNotModified();
			}
			i++;
		    }
		}
	    }
	}
	String[] names = new String[
	    (modifiedBindings == null) ? 0 : modifiedBindings.size()];
	long[] nameValues = new long[names.length];
	for (int i = 0; i < names.length; i++) {
	    BindingKey key = modifiedBindings.get(i).key;
	    names[i] = key.getName();
	    Object lock = cache.getBindingLock(key);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(key);
		nameValues[i] = entry.getValue();
		entry.setNotModified();
	    }
	}
	store.getUpdateQueue().commit(
	    contextId, oids, oidValues, newOids, names, nameValues);
    }

    /**
     * Returns whether there are any modified objects or bindings in this
     * transaction.
     */
    private boolean getModified() {
	return modifiedObjects != null || modifiedBindings != null;
    }

    /**
     * Stores an entry key and the value that was formerly cached for that
     * entry, for use in commit and abort.
     *
     * @param	<K> the key type
     * @param	<V> the value type
     */
    private static class SavedValue<K, V> {

	/** The key. */
	final K key;

	/** The value to restore on abort. */
	final V restoreValue;

	/**
	 * Creates an instance with the key and value from a cache entry.
	 *
	 * @param	entry the cache entry
	 */
	SavedValue(BasicCacheEntry<K, V> entry) {
	    key = entry.key;
	    restoreValue = entry.getValue();
	}

	/**
	 * Checks if the argument is an instance of {@code SavedValue} with an
	 * equal key.
	 */
	@Override
	public boolean equals(Object object) {
	    return object instanceof SavedValue<?, ?> &&
		key.equals(((SavedValue<?, ?>) object).key);
	}

	@Override
	public int hashCode() {
	    return key.hashCode();
	}
    }

    /**
     * Stores a binding entry key, the value that was formerly cached for that
     * entry, and the formerly cached previous key information, for use in
     * commit and abort.
     */
    private static class SavedBindingValue
	extends SavedValue<BindingKey, Long>
    {
	/** The previous key to restore on abort. */
	final BindingKey restorePreviousKey;

	/** Whether the previous key was unbound, to restore on abort. */
	final boolean restorePreviousKeyUnbound;

	/**
	 * Creates an instance with information from a binding cache entry.
	 *
	 * @param	entry the binding cache entry
	 */
	SavedBindingValue(BindingCacheEntry entry) {
	    super(entry);
	    restorePreviousKey = entry.getPreviousKey();
	    restorePreviousKeyUnbound = entry.getPreviousKeyUnbound();
	}
    }
}
