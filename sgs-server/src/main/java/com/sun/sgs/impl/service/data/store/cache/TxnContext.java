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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.util.Numbers.addCheckOverflow;
import com.sun.sgs.service.Transaction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/** Maintains state associated with a transaction. */
class TxnContext {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(TxnContext.class.getName()));

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
     * A list of information about objects modified in this transaction and
     * their previous values, or {@code null} if no objects have been modified.
     */
    private List<SavedObjectValue> modifiedObjects = null;

    /**
     * A map from binding keys to information about their previous values for
     * bindings modified in this transaction, or {@code null} if no bindings
     * have been modified.
     */
    private Map<BindingKey, SavedBindingValue> modifiedBindings = null;

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
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "Created " + this);
	}
    }

    @Override
    public String toString() {
	return "TxnContext[contextId:" + contextId + ", txn:" + txn + "]";
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
	    store.getUpdateQueue().prepare(
		addCheckOverflow(txn.getCreationTime(), txn.getTimeout()));
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
	if (!prepare()) {
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
	    for (SavedObjectValue saved : modifiedObjects) {
		Object lock = cache.getObjectLock(saved.key);
		synchronized (lock) {
		    ObjectCacheEntry entry = cache.getObjectEntry(saved.key);
		    entry.setValue(saved.restoreValue);
		    if (entry.getModified()) {
			entry.setNotModified(lock);
		    }
		    if (saved.restoreValue == null) {
			entry.setEvictedImmediate(lock);
			cache.removeObjectEntry(saved.key);
		    }
		}
	    }
	}
	if (modifiedBindings != null) {
	    for (Entry<BindingKey, SavedBindingValue> savedEntry :
		     modifiedBindings.entrySet())
	    {
		BindingKey key = savedEntry.getKey();
		SavedBindingValue saved = savedEntry.getValue();
		Object lock = cache.getBindingLock(key);
		ReserveCache reserve = new ReserveCache(cache);
		try {
		    synchronized (lock) {
			BindingCacheEntry entry = cache.getBindingEntry(key);
			if (entry == null && saved.restoreValue != -1) {
			    entry = noteCachedBinding(
				key, saved.restoreValue, true, reserve);
			}
			if (entry != null) {
			    entry.setValue(saved.restoreValue);
			    if (entry.getModified()) {
				entry.setNotModified(lock);
			    }
			    entry.setPreviousKey(
				saved.restorePreviousKey,
				saved.restorePreviousKeyUnbound);
			    if (saved.restoreValue == -1) {
				entry.setEvictedImmediate(lock);
				cache.removeBindingEntry(key);
			    }
			}
		    }
		} finally {
		    reserve.done();
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

    /**
     * Returns the next object ID for this transaction of a newly created
     * object, or {@code -1} if none is found.	Does not return IDs for removed
     * objects.	 Specifying {@code -1} requests the first ID.
     *
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	the identifier of the next newly created object following the
     *		object with identifier {@code oid}, or {@code -1} if there are
     *		no more objects
     */
    long nextNewObjectId(long oid) {
	Cache cache = store.getCache();
	long result = -1;
	if (modifiedObjects != null) {
	    for (SavedObjectValue saved : modifiedObjects) {
		if (saved.key > oid &&
		    (result == -1 || saved.key < result) &&
		    saved.restoreValue == null)
		{
		    synchronized (cache.getObjectLock(saved.key)) {
			ObjectCacheEntry entry =
			    cache.getObjectEntry(saved.key);
			if (entry.getValue() != null) {
			    result = saved.key;
			}
		    }
		}
	    }
	}
	return result;
    }

    /* -- Entry methods -- */

    /**
     * Adds an entry to the cache for a newly allocated object.  The associated
     * lock should be held, and the caller should have reserved space in the
     * cache.
     *
     * @param	oid the object ID of the new object
     * @param	reserve for tracking cache reservations
     */
    void noteNewObject(long oid, ReserveCache reserve) {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry = ObjectCacheEntry.createNew(oid, contextId);
	store.getCache().addObjectEntry(entry, reserve);
    }

    /**
     * Adds an entry to the cache for an object that is being fetched from the
     * server.  The associated lock should be held, and the caller should have
     * reserved space in the cache.
     *
     * @param	oid the object ID of the object
     * @param	forUpdate whether the object is being fetched for update
     * @param	reserve for tracking cache reservations
     */
    ObjectCacheEntry noteFetchingObject(
	long oid, boolean forUpdate, ReserveCache reserve)
    {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createFetching(oid, contextId, forUpdate);
	store.getCache().addObjectEntry(entry, reserve);
	return entry;
    }

    /**
     * Adds an entry to the cache for an object that is being cached for read
     * after being fetched from the server.  The associated lock should be
     * held, and the caller should have reserved space in the cache.
     *
     * @param	oid the object ID of the object
     * @param	data the data for the object
     * @param	reserve for tracking cache reservations
     */
    ObjectCacheEntry noteCachedObject(
	long oid, byte[] data, ReserveCache reserve)
    {
	assert Thread.holdsLock(store.getCache().getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createCached(oid, contextId, data);
	store.getCache().addObjectEntry(entry, reserve);
	return entry;
    }

    /**
     * Adds an entry to the cache that represents the last bound name in the
     * cache, but returning {@code null} if the last entry is found to be
     * already present.  The newly created entry will be marked as being
     * fetched.  The associated lock should be held, and the caller should have
     * reserved space in the cache.
     *
     * @param	reserve for tracking cache reservations
     * @return	the new cache entry or {@code null}
     */
    BindingCacheEntry noteLastBinding(ReserveCache reserve) {
	Cache cache = store.getCache();
	assert Thread.holdsLock(cache.getBindingLock(BindingKey.LAST));
	if (cache.getBindingEntry(BindingKey.LAST) != null) {
	    return null;
	}
	BindingCacheEntry entry = BindingCacheEntry.createLast(contextId);
	cache.addBindingEntry(entry, reserve);
	return entry;
    }

    /**
     * Adds an entry to the cache for a name binding.  The associated lock
     * should be held, and the caller should have reserved space in the cache.
     *
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	forUpdate whether the entry is cached for update
     * @param	reserve for tracking cache reservations
     */
    BindingCacheEntry noteCachedBinding(
	BindingKey key, long value, boolean forUpdate, ReserveCache reserve)
    {
	assert Thread.holdsLock(store.getCache().getBindingLock(key));
	BindingCacheEntry entry =
	    BindingCacheEntry.createCached(key, contextId, value, forUpdate);
	store.getCache().addBindingEntry(entry, reserve);
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
	Object lock = store.getCache().getObjectLock(entry.key);
	assert Thread.holdsLock(lock);
	if (!entry.getModified()) {
	    if (modifiedObjects == null) {
		modifiedObjects = new LinkedList<SavedObjectValue>();
	    }
	    SavedObjectValue saved = new SavedObjectValue(entry);
	    assert !modifiedObjects.contains(saved);
	    modifiedObjects.add(saved);
	    entry.setCachedDirty(lock);
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
	Object lock = store.getCache().getBindingLock(entry.key);
	assert Thread.holdsLock(lock);
	if (!entry.getModified()) {
	    if (modifiedBindings == null ||
		!modifiedBindings.containsKey(entry.key))
	    {
		if (modifiedBindings == null) {
		    modifiedBindings =
			new HashMap<BindingKey, SavedBindingValue>();
		}
		modifiedBindings.put(
		    entry.key, new SavedBindingValue(entry));
	    }
	    entry.setCachedDirty(lock);
	}
	entry.setValue(oid);
	entry.noteAccess(contextId);
	if (oid == -1) {
	    entry.setNotModified(lock);
	    entry.setEvictedImmediate(lock);
	    store.getCache().removeBindingEntry(entry.key);
	}
    }

    /** Checks the consistency of modified objects and bindings. */
    void checkModified() {
	Cache cache = store.getCache();
	if (modifiedObjects != null) {
	    for (SavedObjectValue saved : modifiedObjects) {
		synchronized (cache.getObjectLock(saved.key)) {
		    ObjectCacheEntry entry = cache.getObjectEntry(saved.key);
		    if (entry == null) {
			if (logger.isLoggable(WARNING)) {
			    logger.log(WARNING,
				       "Saved binding has no entry:" +
				       "\n  key: " + saved.key);
			}
		    } else if (!entry.getModified()) {
			if (logger.isLoggable(WARNING)) {
			    logger.log(WARNING,
				       "Saved entry is not modified:" +
				       "\n  entry: " + entry);
			}
		    }
		}
	    }
	}
	if (modifiedBindings != null) {
	    for (Entry<BindingKey, SavedBindingValue> mapEntry :
		     modifiedBindings.entrySet())
	    {
		BindingKey key = mapEntry.getKey();
		synchronized (cache.getBindingLock(key)) {
		    BindingCacheEntry entry = cache.getBindingEntry(key);
		    if (entry != null && !entry.getModified()) {
			if (logger.isLoggable(WARNING)) {
			    logger.log(WARNING,
				       "Saved entry is not modified:" +
				       "\n  entry: " + entry);
			}
		    }
		}
	    }
	}
    }

    /* -- Private methods and classes -- */

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
		for (SavedObjectValue saved : modifiedObjects) {
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
			    entry.setNotModified(lock);
			}
			i++;
		    }
		}
	    }
	}
	int numNames = 0;
	if (modifiedBindings != null) {
	    numNames = modifiedBindings.size();
	    if (modifiedBindings.containsKey(BindingKey.LAST)) {
		numNames--;
	    }
	}
	String[] names = new String[numNames];
	long[] nameValues = new long[numNames];
	int newNames = 0;
	if (modifiedBindings != null) {
	    int i = 0;
	    for (int pass = 1; pass <= 2; pass++) {
		boolean includeNew = (pass == 1);
		for (Entry<BindingKey, SavedBindingValue> mapEntry :
			 modifiedBindings.entrySet())
		{
		    SavedBindingValue saved = mapEntry.getValue();
		    boolean isNew = (saved.restoreValue == -1);
		    if (includeNew == isNew) {
			BindingKey key = mapEntry.getKey();
			String name = key.getNameAllowLast();
			if (name != null) {
			    if (isNew) {
				newNames++;
			    }
			    names[i] = name;
			}
			Object lock = cache.getBindingLock(key);
			synchronized (lock) {
			    BindingCacheEntry entry =
				cache.getBindingEntry(key);
			    if (name != null) {
				nameValues[i] =
				    (entry == null) ? -1 : entry.getValue();
			    }
			    if (entry != null) {
				entry.setNotModified(lock);
			    }
			}
			if (name != null) {
			    i++;
			}
		    }
		}
	    }
	}
	store.getUpdateQueue().commit(
	    contextId, oids, oidValues, newOids, names, nameValues, newNames);
    }

    /**
     * Returns whether there are any modified objects or bindings in this
     * transaction.
     */
    private boolean getModified() {
	return modifiedObjects != null || modifiedBindings != null;
    }

    /**
     * Stores an OID and the data value that was formerly cached for that OID,
     * for use in commit and abort.
     */
    private static class SavedObjectValue {

	/** The object ID. */
	final long key;

	/** The data value to restore on abort. */
	final byte[] restoreValue;

	/**
	 * Creates an instance with the key and value from a cache entry.
	 *
	 * @param	entry the cache entry
	 */
	SavedObjectValue(ObjectCacheEntry entry) {
	    key = entry.key;
	    restoreValue = entry.getValue();
	}

	/**
	 * Checks if the argument is an instance of {@code SavedValue} with an
	 * equal key.
	 */
	@Override
	public boolean equals(Object object) {
	    return object instanceof SavedObjectValue &&
		key == ((SavedObjectValue) object).key;
	}

	@Override
	public int hashCode() {
	    return ObjectCacheEntry.keyHashCode(key);
	}
    }

    /**
     * Stores a binding entry key, the value that was formerly cached for that
     * entry, and the formerly cached previous key information, for use in
     * commit and abort.
     */
    private static class SavedBindingValue {

	/** The value to restore on abort. */
	final long restoreValue;

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
	    restoreValue = entry.getValue();
	    restorePreviousKey = entry.getPreviousKey();
	    restorePreviousKeyUnbound = entry.getPreviousKeyUnbound();
	}

	@Override
	public String toString() {
	    return "SavedBindingValue[" +
		"restoreValue:" + restoreValue +
		", restorePreviousKey:" + restorePreviousKey +
		", restorePreviousKeyUnbound:" + restorePreviousKeyUnbound +
		"]";
	}
    }
}
