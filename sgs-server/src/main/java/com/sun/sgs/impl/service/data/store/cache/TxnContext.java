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

/**
 * Maintains the state that the {@link CachingDataStore} associates with a
 * transaction.
 */
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
     * Records the number of newly created objects that have not yet been
     * provided with data.  This number is important because these objects will
     * have entries in modifiedObjects but do not represent modifications made
     * in the transaction until the data is provided.
     */
    private int newlyCreatedObjects = 0;

    /**
     * A map from binding keys to information about their previous values for
     * bindings modified in this transaction, or {@code null} if no bindings
     * have been modified.
     */
    private Map<BindingKey, SavedBindingValue> modifiedBindings = null;

    /** Whether the transaction has been prepared. */
    private boolean prepared;

    /** Whether the transaction has been committed or aborted. */
    private boolean finished;

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
	} else if (finished) {
	    throw new IllegalStateException(
		"Transaction has already finished: " + txn);
	} else if (!getModified()) {
	    store.getUpdateQueue().abort(contextId, false);
	    finished = true;
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
	if (finished) {
	    throw new IllegalStateException(
		"Transaction has already finished: " + txn);
	} else if (!prepare()) {
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
	} else if (finished) {
	    throw new IllegalStateException(
		"Transaction has already finished: " + txn);
	} else if (getModified()) {
	    commitInternal();
	}
    }

    /**
     * Aborts the transaction, removing entries from the update queue, and
     * rolling back changes made to the cache.
     */
    void abort() {
	store.getUpdateQueue().abort(contextId, prepared);
	Cache cache = store.getCache();
	if (modifiedObjects != null) {
	    for (SavedObjectValue saved : modifiedObjects) {
		saved.abort(cache);
	    }
	}
	if (modifiedBindings != null) {
	    for (SavedBindingValue saved : modifiedBindings.values()) {
		saved.abort(cache, contextId);
	    }
	}
	finished = true;
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
     * Returns the next object ID of an object newly created by this
     * transaction, which must be active, or {@code -1} if none is found.  Does
     * not return IDs for removed objects.  Specifying {@code -1} requests the
     * first such ID.
     *
     * @param	oid the identifier of the object to search after, or
     *		{@code -1} to request the first object
     * @return	the identifier of the next newly created object following the
     *		object with identifier {@code oid}, or {@code -1} if there are
     *		no more objects
     */
    long nextNewObjectId(long oid) {
	assert !finished;
	long result = -1;
	if (modifiedObjects != null) {
	    Cache cache = store.getCache();
	    for (SavedObjectValue saved : modifiedObjects) {
		if (saved.key > oid &&
		    (result == -1 || saved.key < result) &&
		    saved.restoreValue == null)
		{
		    synchronized (cache.getObjectLock(saved.key)) {
			ObjectCacheEntry entry =
			    cache.getObjectEntry(saved.key);
			if (!entry.isNewlyCreated() &&
			    entry.getValue() != null)
			{
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
     * Notes that an entry has been accessed by the associated transaction,
     * which does not need to be active.  The associated lock must be held.
     *
     * @param	entry the cache entry
     */
    void noteAccess(BasicCacheEntry<?, ?> entry) {
	assert Thread.holdsLock(store.getCache().getEntryLock(entry));
	entry.noteAccess(contextId);
    }

    /**
     * Adds an entry to the cache for a newly allocated object on behalf of the
     * associated transaction, which must be active.  The associated lock must
     * be held, and the caller must have reserved space in the cache.
     *
     * @param	oid the object ID of the new object
     * @param	reserve for tracking cache reservations
     */
    void createNewObjectEntry(long oid, Cache.Reservation reserve) {
	assert !finished;
	Cache cache = store.getCache();
	assert Thread.holdsLock(cache.getObjectLock(oid));
	ObjectCacheEntry entry = ObjectCacheEntry.createNew(oid, contextId);
	cache.addObjectEntry(entry, reserve);
	if (modifiedObjects == null) {
	    modifiedObjects = new LinkedList<SavedObjectValue>();
	}
	modifiedObjects.add(new SavedObjectValue(entry));
	newlyCreatedObjects++;
    }

    /**
     * Adds an entry to the cache for an object that is being fetched from the
     * server on behalf of the associated transaction, which does not need to
     * be active.  The associated lock must be held, and the caller must have
     * reserved space in the cache.
     *
     * @param	oid the object ID of the object
     * @param	forUpdate whether the object is being fetched for update
     * @param	reserve for tracking cache reservations
     * @return	the new object entry
     */
    ObjectCacheEntry createFetchingObjectEntry(
	long oid, boolean forUpdate, Cache.Reservation reserve)
    {
	Cache cache = store.getCache();
	assert Thread.holdsLock(cache.getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createFetching(oid, contextId, forUpdate);
	cache.addObjectEntry(entry, reserve);
	return entry;
    }

    /**
     * Adds an entry to the cache for an object that is being cached for read
     * on behalf of the associated transaction, which does not need to be
     * active, after being fetched from the server, but for which there is no
     * entry already present.  This situation occurs when requesting the next
     * object since the ID of that object is not known in advance.  The
     * associated lock must be held, and the caller must have reserved space in
     * the cache.
     *
     * @param	oid the object ID of the object
     * @param	data the data for the object
     * @param	reserve for tracking cache reservations
     */
    void createCachedImmediateObjectEntry(
	long oid, byte[] data, Cache.Reservation reserve)
    {
	Cache cache = store.getCache();
	assert Thread.holdsLock(cache.getObjectLock(oid));
	ObjectCacheEntry entry =
	    ObjectCacheEntry.createCached(oid, data, contextId);
	cache.addObjectEntry(entry, reserve);
    }

    /**
     * Notes that an object entry has been cached after fetching from the
     * server on behalf of the associated transaction, which does not need to
     * be active.  The associated lock must be held.
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
	entry.noteAccess(contextId);
	if (forUpdate) {
	    entry.setCachedWrite(lock, data);
	} else {
	    entry.setCachedRead(lock, data);
	}
    }

    /**
     * Notes that an object has been modified by the associated transaction,
     * which must be active.  The associated lock must be held.
     *
     * @param	entry the cache entry
     * @param	data the new data value
     */
    void noteModifiedObject(ObjectCacheEntry entry, byte[] data) {
	assert !finished;
	Object lock = store.getCache().getObjectLock(entry.key);
	assert Thread.holdsLock(lock);
	if (!entry.getModified()) {
	    if (entry.isNewlyCreated()) {
		newlyCreatedObjects--;
	    } else {
		if (modifiedObjects == null) {
		    modifiedObjects = new LinkedList<SavedObjectValue>();
		}
		SavedObjectValue saved = new SavedObjectValue(entry);
		assert !modifiedObjects.contains(saved);
		modifiedObjects.add(saved);
	    }
	    entry.setCachedDirty(lock);
	}
	entry.setValue(data);
	entry.noteAccess(contextId);
    }

    /**
     * Adds an entry to the cache for a name binding on behalf of the
     * associated transaction, which does not need to be active.  The
     * associated lock must be held, and the caller must have reserved space in
     * the cache.
     *
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	forUpdate whether the entry is cached for update
     * @param	reserve for tracking cache reservations
     * @return	the new entry
     */
    BindingCacheEntry createCachedBindingEntry(BindingKey key,
					       long value,
					       boolean forUpdate,
					       Cache.Reservation reserve)
    {
	return createCachedBindingEntry(
	    store.getCache(), contextId, key, value, forUpdate, reserve);
    }

    /**
     * Adds an entry to the cache for a newly created name binding on behalf of
     * the associated transaction, which must be active.  The associated lock
     * must be held, and the caller must have reserved space in the cache.
     *
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	reserve for tracking cache reservations
     * @return	the new entry
     */
    BindingCacheEntry createNewBindingEntry(
	BindingKey key, long value, Cache.Reservation reserve)
    {
	assert !finished;
	Cache cache = store.getCache();
	Object lock = cache.getBindingLock(key);
	assert Thread.holdsLock(lock);
	if (!modifiedBindingNoted(key)) {
	    modifiedBindings.put(key, new SavedBindingValue(key));
	}
	BindingCacheEntry entry =
	    BindingCacheEntry.createCached(key, contextId, value, true);
	entry.setCachedDirty(lock);
	cache.addBindingEntry(entry, reserve);
	return entry;
    }

    /**
     * Adds an entry to the cache that represents the last bound name in the
     * cache on behalf of the associated transaction, which does not need to be
     * active, and returns the new entry, or else returns {@code null} and
     * making no modification if a last entry is already present.  The newly
     * created entry will be marked as being fetched.  The associated lock must
     * be held, and the caller must have reserved space in the cache.
     *
     * @param	reserve for tracking cache reservations
     * @return	the new cache entry or {@code null}
     */
    BindingCacheEntry createLastBindingEntry(Cache.Reservation reserve) {
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
     * Notes that a binding has been modified by the associated transaction,
     * which must be active.  The associated lock must be held.
     *
     * @param	entry the binding entry
     * @param	oid the new object ID
     */
    void noteModifiedBinding(BindingCacheEntry entry, long oid) {
	assert !finished;
	Object lock = store.getCache().getBindingLock(entry.key);
	assert Thread.holdsLock(lock);
	if (!entry.getModified()) {
	    if (!modifiedBindingNoted(entry.key)) {
		modifiedBindings.put(entry.key, new SavedBindingValue(entry));
	    }
	    entry.setCachedDirty(lock);
	}
	entry.setValue(oid);
	entry.noteAccess(contextId);
    }

    /**
     * Notes that a binding has been removed by the associated transaction,
     * which must be active.  The associated lock must be held.
     *
     * @param	entry the binding entry
     */
    void noteRemovedBinding(BindingCacheEntry entry) {
	assert !finished;
	Cache cache = store.getCache();
	Object lock = cache.getBindingLock(entry.key);
	assert Thread.holdsLock(lock);
	if (entry.getModified()) {
	    entry.setNotModified(lock);
	} else if (!modifiedBindingNoted(entry.key)) {
	    modifiedBindings.put(entry.key, new SavedBindingValue(entry));
	}
	entry.setEvictedImmediate(lock);
	cache.removeBindingEntry(entry.key);
    }

    /**
     * Updates the information stored in the entry about previous names that
     * are known to be unbound, and saves the previous values for use in
     * aborts.  The {@code newPreviousKey} represents a name for which all
     * names between that name and this entry's name are known to be unbound.
     * The {@code newPreviousKeyState} specifies what is known about the
     * binding of {@code newPreviousKey} itself.
     *
     * @param	entry the entry to update
     * @param	newPreviousKey the new previous key
     * @param	newPreviousKeyState the binding state of the new previous key
     * @throws	IllegalArgumentException if {@code newPreviousKey} is greater
     *		than or equal to the key for this entry
     */
    void updatePreviousKey(BindingCacheEntry entry,
			   BindingKey newPreviousKey,
			   BindingState newPreviousKeyState)
    {
	noteModifiedBinding(
	    entry, (entry.key == BindingKey.LAST) ? -1 : entry.getValue());
	entry.updatePreviousKey(newPreviousKey, newPreviousKeyState);
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

    /**
     * Adds an entry to the cache for a name binding.  The associated lock must
     * be held, and the caller must have reserved space in the cache.
     *
     * @param	cache the cache
     * @param	contextId the identifier for the transaction
     * @param	key the key for the binding
     * @param	value the value of the binding
     * @param	forUpdate whether the entry is cached for update
     * @param	reserve for tracking cache reservations
     * @return	the new entry
     */
    private static BindingCacheEntry createCachedBindingEntry(
	Cache cache,
	long contextId,
	BindingKey key,
	long value,
	boolean forUpdate,
	Cache.Reservation reserve)
    {
	assert Thread.holdsLock(cache.getBindingLock(key));
	BindingCacheEntry entry =
	    BindingCacheEntry.createCached(key, contextId, value, forUpdate);
	cache.addBindingEntry(entry, reserve);
	return entry;
    }

    /** Adds the commit request to the update queue. */
    private void commitInternal() {
	Cache cache = store.getCache();
	/* Collect modified objects */
	int numObjects = (modifiedObjects == null)
	    ? 0 : modifiedObjects.size() - newlyCreatedObjects;
	long[] oids = new long[numObjects];
	byte[][] oidValues = new byte[numObjects][];
	int newOids = 0;
	if (modifiedObjects != null) {
	    int i = 0;
	    /* Use two passes so new objects are put first */
	    for (int pass = 1; pass <= 2; pass++) {
		boolean includeNew = (pass == 1);
		for (SavedObjectValue saved : modifiedObjects) {
		    if (includeNew == saved.isNew()) {
			long oid = saved.key;
			Object lock = cache.getObjectLock(oid);
			synchronized (lock) {
			    ObjectCacheEntry entry = cache.getObjectEntry(oid);
			    if (entry.isNewlyCreated()) {
				/* Object was never used */
				entry.setEvictedImmediate(lock);
				cache.removeObjectEntry(oid);
				continue;
			    }
			    oids[i] = oid;
			    oidValues[i] = entry.getValue();
			    entry.setNotModified(lock);
			}
			if (saved.isNew()) {
			    newOids++;
			}
			i++;
		    }
		}
	    }
	}
	/* Collect modified bindings */
	int numNames = 0;
	if (modifiedBindings != null) {
	    numNames = modifiedBindings.size();
	    if (modifiedBindings.containsKey(BindingKey.LAST)) {
		numNames--;
	    }
	}
	String[] names = new String[numNames];
	long[] nameValues = new long[numNames];
	if (modifiedBindings != null) {
	    int i = 0;
	    for (BindingKey key : modifiedBindings.keySet()) {
		String name = key.getNameAllowLast();
		Object lock = cache.getBindingLock(key);
		synchronized (lock) {
		    BindingCacheEntry entry = cache.getBindingEntry(key);
		    if (name != null) {
			names[i] = name;
			nameValues[i] =
			    (entry == null) ? -1 : entry.getValue();
			i++;
		    }
		    if (entry != null) {
			entry.setNotModified(lock);
		    }
		}
	    }
	}
	/* Commit updates to server */
	store.getUpdateQueue().commit(
	    contextId, oids, oidValues, newOids, names, nameValues);
	finished = true;
    }

    /**
     * Returns whether there are any modified objects or bindings in this
     * transaction.
     */
    private boolean getModified() {
	return modifiedBindings != null ||
	    (modifiedObjects != null &&
	     modifiedObjects.size() > newlyCreatedObjects);
    }

    /**
     * Checks whether modifications to the binding with the specified key have
     * already been noted.  Returns true if modifications have been noted,
     * otherwise makes sure that modifiedBindings is not null and returns
     * false.
     */
    private boolean modifiedBindingNoted(BindingKey key) {
	if (modifiedBindings != null &&
	    modifiedBindings.containsKey(key))
	{
	    return true;
	} else {
	    if (modifiedBindings == null) {
		modifiedBindings =
		    new HashMap<BindingKey, SavedBindingValue>();
	    }
	    return false;
	}
    }

    /**
     * Stores an OID and the data value that was formerly cached for that OID,
     * for use in commit and abort.
     */
    private static class SavedObjectValue {

	/** The object ID. */
	final long key;

	/**
	 * The data value to restore on abort.  If the value is null, then
	 * object was newly created.
	 */
	private final byte[] restoreValue;

	/**
	 * Creates an instance with the key and value from a cache entry.
	 *
	 * @param	entry the cache entry
	 */
	SavedObjectValue(ObjectCacheEntry entry) {
	    key = entry.key;
	    restoreValue = !entry.isNewlyCreated() ? entry.getValue() : null;
	}

	/**
	 * Returns whether this object was newly created in this transaction.
	 *
	 * @return	whether this object is newly created
	 */
	boolean isNew() {
	    return restoreValue == null;
	}

	/**
	 * Rolls back the modifications made to this object in the cache.
	 *
	 * @param	cache the cache
	 */
	void abort(Cache cache) {
	    Object lock = cache.getObjectLock(key);
	    synchronized (lock) {
		ObjectCacheEntry entry = cache.getObjectEntry(key);
		if (restoreValue == null) {
		    /* Roll back object creation */
		    if (!entry.isNewlyCreated()) {
			entry.setNotModified(lock);
		    }
		    entry.setEvictedImmediate(lock);
		    cache.removeObjectEntry(key);
		} else {
		    entry.setValue(restoreValue);
		    entry.setNotModified(lock);
		}
	    }
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

	/**
	 * The value used to represent a name that was not previously bound.
	 */
	static final long NEWLY_BOUND = -2;

	/** The bound name. */
	final BindingKey key;

	/**
	 * The value to restore on abort.  If the value is {@link
	 * #NEWLY_BOUND}, then the name was newly bound.
	 */
	private final long restoreValue;

	/** The previous key to restore on abort. */
	private final BindingKey restorePreviousKey;

	/** Whether the previous key was unbound, to restore on abort. */
	private final boolean restorePreviousKeyUnbound;

	/**
	 * Creates an instance with information from a binding cache entry.
	 *
	 * @param	entry the binding cache entry
	 */
	SavedBindingValue(BindingCacheEntry entry) {
	    key = entry.key;
	    restoreValue = (key == BindingKey.LAST) ? -1 : entry.getValue();
	    restorePreviousKey = entry.getPreviousKey();
	    restorePreviousKeyUnbound = entry.isPreviousKeyUnbound();
	}

	/** Creates an instance that represents a newly created binding. */
	SavedBindingValue(BindingKey key) {
	    this.key = key;
	    restoreValue = NEWLY_BOUND;
	    restorePreviousKey = null;
	    restorePreviousKeyUnbound = false;
	}

	/**
	 * Rolls back the modifications made to this binding in the cache.
	 *
	 * @param	cache the cache
	 * @param	contextId the transaction identifier
	 */
	void abort(Cache cache, long contextId) {
	    Object lock = cache.getBindingLock(key);
	    Cache.Reservation reserve = cache.getReservation(1);
	    try {
		synchronized (lock) {
		    BindingCacheEntry entry = cache.getBindingEntry(key);
		    if (entry == null) {
			/*
			 * Reinstate removed binding if it was not created in
			 * this transaction.
			 */
			if (restoreValue != NEWLY_BOUND) {
			    createCachedBindingEntry(
				cache, contextId, key, restoreValue, true,
				reserve);
			}
		    } else if (restoreValue != NEWLY_BOUND) {
			/* Reinstate original values */
			entry.setValue(restoreValue);
			entry.setNotModified(lock);
			entry.setPreviousKey(
			    restorePreviousKey, restorePreviousKeyUnbound);
		    } else if (entry.getPendingPrevious()) {
			entry.setNotModified(lock);
			entry.setPreviousKey(
			    restorePreviousKey, restorePreviousKeyUnbound);
			/*
			 * Mark the entry as fetching so that the pending
			 * server request will know that it should remove the
			 * entry if it ends up not being used.
			 */
			entry.setReadingTemporarily(lock);
		    } else {
			/* Roll back new binding */
			entry.setNotModified(lock);
			entry.setEvictedImmediate(lock);
			cache.removeBindingEntry(key);
		    }
		}
	    } finally {
		reserve.done();
	    }
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
