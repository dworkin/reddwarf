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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
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
 * avoid object serialization.  Objects are stored in lists that are the values
 * stored in linked hash maps.
 */
final class ObjectCache {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(ObjectCache.class.getName()));

    /** The number of cache operations between calls to log the hit rate. */
    private static final int LOGGING_INTERVAL = 100000;

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
    private int success;

    /**
     * The total number of cache requests.  Synchronize on this instance when
     * accessing.
     */
    private int total;

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
	double usage = 0;
	synchronized (this) {
	    if (total % LOGGING_INTERVAL == (LOGGING_INTERVAL - 1)) {
		logUsage = true;
		usage = success / (double) total;
	    }
	    total++;
	}
	if (logUsage && logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "Object cache hit rate: {0}", usage);
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
		    success++;
		}
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			       "ObjectCache.get oid:{0} returns {1}",
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
	if (disabled || notClasses.contains(object.getClass().getName())) {
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
    }

    /**
     * A {@code LinkedHashMap} that uses access ordering, tracks the number of
     * entries, and removes the eldest entry if the map contains more than
     * {@code maxSize} entries.
     */
    private static class Cache extends LinkedHashMap<Key, List<Value>> {

	/** The maximum number of entries in this cache. */
	private final int maxSize;

	/** The number of entries in this cache. */
	private int size;

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
	    int l = Math.max(h, 16);
	    for (byte element : bytes) {
		h = (31 * h) + element;
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
	    return "Value[" + contextWrapper + ", " + object + "]";
	}
    }
}
