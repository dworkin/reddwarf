/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An object cache for reusing objects from previous transactions, to help
 * avoid the cost of object serialization. <p>
 *
 * The object cache holds recently used objects, keyed by the bytes used to
 * create the object.  The object cache itself does not remove the need for the
 * data service to continue to read data from the underlying data store &mdash;
 * it is not a data cache &mdash; but it does permit reusing available objects
 * previously created for the same data. <p>
 *
 * Objects saved in the cache should only be used by a single transaction at a
 * time.  This restriction prevents an object from being modified by two tasks
 * at once, as well as the possibility of navigating from an object used by one
 * task to one currently being used by another task. <p>
 *
 * Only objects that have been obtained by deserialization should be cached.
 * This restriction insures that cached objects do not share any non-managed
 * objects with other cached objects.  Using deserialized objects avoids
 * sharing because each serialized object contains the full object graph.  The
 * result is that, after an object is modified in one task, it cannot be cached
 * until it is first successfully read by another task. <p>
 *
 * To allow objects to be reused in new transactions while still permitting the
 * data service to safely detect access to objects used in another transaction,
 * managed references are wrapped in an indirection object &mdash; a {@link
 * ManagedReferenceWrapper} &mdash; that delegates to an associated {@link
 * ManagedReferenceImpl}.  Each time a cached object is put into use for a new
 * transaction, the old {@code ManagedReferenceImpl} object needs to be
 * replaced with a reference appropriate to the new transaction. <p>
 *
 * Switching reference implementation objects efficiently is accomplished
 * through a wrapper around the context object that references use to represent
 * the current transaction &mdash; a {@link ContextWrapper}.  During
 * deserialization, each {@code ManagedReferenceWrapper} object within the
 * object graph for a managed object is created using the same {@code
 * ContextWrapper} object.  When a cached object is used for a new transaction,
 * the implementation modifies the {@code ContextWrapper} for the cached object
 * to refer to the context for the new transaction.  This scheme avoids the
 * need to update each reference separately; updating the {@code
 * ContextWrapper} has the effect of decaching all of the references. <p>
 *
 * Object caching means that there are cases where a managed objects is used in
 * a new transaction without being serialized or deserialized since its last
 * use.  Applications can use {@link TaskLocalReference} instances, created
 * using {@link DataManager#createTaskLocalReference
 * DataManager.createTaskLocalReference}, to help decache transient fields. <p>
 *
 * The object cache itself stores objects in lists that are the values stored
 * in linked hash maps.  The lists permit the cache to store multiple objects
 * associated with the same bytes, to permit the cache to supply objects for
 * simultaneous use in different transactions.  The linked hash map uses access
 * ordering to insure that old items are removed from the cache.
 */
final class ObjectCache {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(ObjectCache.class.getName()));

    /** The number of cache operations between calls to log the hit rate. */
    private static final int LOGGING_INTERVAL =
	Integer.getInteger(ObjectCache.class.getName() + ".logging.interval",
			   100000);

    /** The names of classes whose instances should not be cached. */
    private final Set<String> notClasses;

    /** The underlying map. */
    private final Cache map;

    /** Whether the cache is disabled. */
    private final boolean disabled;

    /**
     * The number of cache hits.  Synchronize on this instance when
     * accessing.
     */
    private int hits;

    /**
     * The total number of cache requests.  Synchronize on this instance when
     * accessing.
     */
    private int accesses;

    /**
     * Creates an instance of this class.  Specifying a maximum size of {@code
     * 0} defeats the cache efficiently.
     *
     * @param	maxSize the maximum size of the cache
     * @param	notClasses the names of classes whose instances should not
     *		be cached
     * @throws	IllegalArgumentException if {@code maxSize} is negative
     */
    ObjectCache(int maxSize, Set<String> notClasses) {
	if (maxSize < 0) {
	    throw new IllegalArgumentException(
		"The maxSize argument must not be negative");
	}
	map = new Cache(maxSize);
	this.notClasses = notClasses;
	disabled = (maxSize == 0);
    }

    /**
     * Attempts to retrieve an object from the cache.  If an object with the
     * same object ID and bytes is found, the object is removed from the cache,
     * it's context is set to the one specified, and the object and the context
     * wrapper are returned.
     *
     * @param	oid the object ID
     * @param	bytes the object bytes
     * @param	context the {@code Context} for updating references within this
     *		object
     * @return	the object and context wrapper, or {@code null} if no matching
     *		object is found
     */
    Value get(long oid, byte[] bytes, Context context) {
	if (disabled) {
	    return null;
	}
	boolean logUsage = false;
	double hitRatio = 0;
	double flushRatio = 0;
	synchronized (this) {
	    if (accesses % LOGGING_INTERVAL == (LOGGING_INTERVAL - 1)) {
		logUsage = true;
		hitRatio = hits / (double) accesses;
		flushRatio = map.flushes / (double) accesses;
	    }
	    accesses++;
	}
	if (logUsage && logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "Object cache hit ratio: {0}, flush ratio: {1}",
		       hitRatio, flushRatio);
	}
	Key key = new Key(oid, bytes);
	List<Value> list;
	synchronized (map) {
	    list = map.get(key);
	}
	if (list != null) {
	    Value value = null;
	    synchronized (list) {
		int size = list.size();
		if (size > 0) {
		    value = list.remove(size - 1);
		}
	    }
	    if (value != null) {
		value.contextWrapper.setContext(context);
		synchronized (this) {
		    hits++;
		}
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			       "ObjectCache.get oid:{0,number,#} returns {1}",
			       oid, value);
		}
		return value;
	    }
	}
	return null;
    }

    /**
     * Stores an object in the cache.
     *
     * @param	oid the object ID
     * @param	bytes the bytes used to construct the object
     * @param	contextWrapper the context wrapper used by references in the
     *		object
     * @param	object the object
     * @param	unmodifiedBytes the bytes created by serializing the object, or
     *		{@code null} if not detecting modifications
     */
    void put(long oid, byte[] bytes, ContextWrapper contextWrapper,
	     ManagedObject object, byte[] unmodifiedBytes)
    {
	if (disabled) {
	    return;
	}
	Class<?> objectClass = object.getClass();
	if (objectClass.isAnnotationPresent(NoObjectCaching.class) ||
	    notClasses.contains(objectClass.getName()))
	{
	    return;
	}
	Key key = new Key(oid, bytes);
	List<Value> list;
	synchronized (map) {
	    list = map.get(key);
	    map.incrementSize();
	}
	if (list == null) {
	    list = new ArrayList<Value>();
	    synchronized (map) {
		List<Value> existingList = map.get(key);
		if (existingList != null) {
		    list = existingList;
		} else {
		    map.put(key, list);
		}
	    }
	}
	Value value = new Value(contextWrapper, object, unmodifiedBytes);
	synchronized (list) {
	    list.add(value);
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "ObjectCache.put oid:{0,number,#} {1}",
		       oid, value);
	}
    }

    /**
     * A {@code LinkedHashMap} that uses access ordering, tracks the number of
     * entries, and removes the eldest entry if the map contains more than
     * {@code maxSize} entries.
     */
    private static class Cache extends LinkedHashMap<Key, List<Value>> {
	private static final long serialVersionUID = 1;

	/** The maximum number of entries in this cache. */
	private final int maxSize;

	/** The number of entries in this cache. */
	private int size;

	/** The number of old items flushed from the cache. */
	int flushes;

	/** Creates an instance of this class. */
	Cache(int maxSize) {
	    super(16, 0.75f, true);
	    this.maxSize = maxSize;
	}

	/** Increments the number of entries. */
	void incrementSize() {
	    size++;
	}

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation returns {@code true} to remove the entry if the
	 * maximum size is exceeded.
	 */
	protected boolean removeEldestEntry(Entry<Key, List<Value>> eldest) {
	    if (size > maxSize) {
		size -= eldest.getValue().size();
		flushes++;
		return true;
	    } else {
		return false;
	    }
	}
    }

    /**
     * The key for looking up an object in the cache.  Includes the object ID
     * because that makes it a quick check that the values are (likely)
     * different.
     */
    private static class Key {

	/** The object ID. */
	private final long oid;

	/** The object bytes. */
	private final byte[] bytes;

	/** This object's hash code. */
	private final int hashCode;

	/**
	 * Creates an instance with the specified values.
	 *
	 * @param	oid the object ID
	 * @param	bytes the object bytes.
	 */
	Key(long oid, byte[] bytes) {
	    this.oid = oid;
	    if (bytes == null) {
		throw new NullPointerException(
		    "The bytes argument must not be null");
	    }
	    this.bytes = bytes;
	    /*
	     * Compute the hash code by adding the two halves of the object ID
	     * and the length of the bytes.  Then hash the first 16 of the
	     * bytes using the algorithm in Arrays.hashCode(byte[]).
	     */
	    int h = (int) (oid >> 32) + (int) oid + bytes.length;
	    int max = Math.min(bytes.length, 16);
	    for (int i = 0; i < max; i++) {
		h = (31 * h) + bytes[i];
	    }
	    hashCode = h;
	}

	/**
	 * Checks if the argument is an instance of {@link Key} with the same
	 * object ID and bytes.
	 *
	 * @param	object the object to compare
	 * @return	whether the object is equal to this object
	 */
	public boolean equals(Object object) {
	    if (object instanceof Key) {
		Key key = (Key) object;
		return oid == key.oid &&
		    hashCode == key.hashCode &&
		    Arrays.equals(bytes, key.bytes);
	    }
	    return false;
	}

	/**
	 * Returns a hash code for this object.
	 *
	 * @return	a hash code for this object.
	 */
	public int hashCode() {
	    return hashCode;
	}
    }

    /** Stores a value stored in the cache. */
    static class Value {

	/** The context wrapper uses by references in the object. */
	final ContextWrapper contextWrapper;

	/** The object. */
	final ManagedObject object;

	/**
	 * The bytes created by serializing this object, or {@code null} if not
	 * detecting modifications.
	 */
	final byte[] unmodifiedBytes;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	contextWrapper the context wrapper
	 * @param	object the object
	 * @param	unmodifiedBytes the bytes created by serializing the
	 *		object, or {@code null}
	 */
	Value(ContextWrapper contextWrapper, ManagedObject object,
	      byte[] unmodifiedBytes)
	{
	    if (contextWrapper == null || object == null) {
		throw new NullPointerException(
		    "The contextWrapper and object must not be null");
	    }
	    this.contextWrapper = contextWrapper;
	    this.object = object;
	    this.unmodifiedBytes = unmodifiedBytes;
	}

	/**
	 * Returns a string representing this object.
	 *
	 * @return	a string representing this object
	 */
	public String toString() {
	    return "Value[" + contextWrapper + ", " +
		Objects.fastToString(object) + "]";
	}
    }
}
