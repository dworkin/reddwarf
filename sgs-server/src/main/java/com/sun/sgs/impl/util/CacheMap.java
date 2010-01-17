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

package com.sun.sgs.impl.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * A map with values that are softly-referenced.  An entry is removed if its
 * associated value gets garbage-collected, or, if the cache was constructed
 * with a timeout, and entry is removed (lazily) when the timeout expires for
 * that entry.  A timeout is typically used to have the implementation
 * remove values that become stale after a certain period of time.  The
 * implementation ensures that a caller will not have access to stale
 * entries (i.e., entries whose timeout has expired).
 *
 * @param	<K> the key type
 * @param	<V> the value type
 */
public class CacheMap<K, V> {

    /** The underlying map. */
    private final Map<K, Value<K, V>> map = new HashMap<K, Value<K, V>>();

    /** The reference queue, for detecting weak references removals. */
    private final ReferenceQueue<V> queue = new ReferenceQueue<V>();

    /** The entry timeout. */
    private final long entryTimeout;

    /** Creates an instance of this class. */
    public CacheMap() {
	this(0);
    }

    /**
     * Creates an instance of this class with the specified {@code
     * entryTimeout}.
     *
     * @param	entryTimeout an entry timeout, in milliseconds
     * @throws	IllegalArgumentException if {@code entryTimeout} is negative
     */
    public CacheMap(long entryTimeout) {
	if (entryTimeout < 0) {
	    throw new IllegalArgumentException("entryTimeout is negative");
	}
	this.entryTimeout = entryTimeout;
    }

    /**
     * Associates a value with given key, returning the value previously
     * associated with key, or {@code null} if none is found.
     *
     * @param	key the key
     * @param	value the value
     * @return	the previous value or {@code null}
     */
    public V put(K key, V value) {
	processQueue();
	Value<K, V> oldValue =
	    map.put(key, new Value<K, V>(key, value, queue, entryTimeout));
	return oldValue != null ? oldValue.get() : null;
    }

    /**
     * Checks if the map contains the specified key.
     *
     * @param	key the key
     * @return	{@code true} if the map contains the key, else {@code false}
     */
    public boolean containsKey(K key) {
	processQueue();
	return get(key) != null;
	
    }

    /**
     * Returns the value associated with given key, or {@code null} if the key
     * is not found.
     *
     * @param	key the key
     * @return	the value associated with the key or {@code null}	
     */
    public V get(K key) {
	processQueue();
	Value<K, V> value = map.get(key);
	if (value == null) {
	    return null;
	} else if (value.isExpired()) {
	    map.remove(key);
	    return null;
	} else {
	    return value.get();
	}
    }

    /**
     * Removes the association for the given key, returning the value
     * previously associated with the key, or {@code null} if the key is not
     * found.
     *
     * @param	key the key
     * @return	the value previously associated with the key or {@code null}
     */
    public V remove(K key) {
	processQueue();
	Value<K, V> oldValue = map.remove(key);
	return oldValue != null ? oldValue.get() : null;
    }

    /** Removes all associations from this map. */
    public void clear() {
	processQueue();
	map.clear();
    }

    /**
     * Removes all entries from the map whose keys have been determined to be
     * no longer referenced.
     */
    private void processQueue() {
	while (true) {
	    /* No way to say that the queue holds Values */
	    @SuppressWarnings("unchecked")
	    Value<K, V> value = (Value<K, V>) queue.poll();
	    if (value != null) {
		map.remove(value.getKey());
	    } else {
		break;
	    }
	}
    }

    /**
     * A key that maintains a weak reference to an object which should be
     * compared by object identity.
     *
     * @param	<K> the type of the referenced object
    */
    private static final class Value<K, V> extends SoftReference<V> {

	/** The value's associated key. */
	private final K key;
	/** The value's expiration time. */
	private final long expirationTime;

	/**
	 * Creates an instance of this class and registers it with the
	 * specified reference queue.
	 *
	 * @param	key the key
	 * @param	value the value (held softly)
	 * @param	queue the reference queue
	 * @param	timeout a timeout (0 = infinite)
	 */
	Value(K key, V value, ReferenceQueue<V> queue, long timeout) {
	    super(value, queue);
	    this.key = key;
	    this.expirationTime =
		timeout > 0 ?
		System.currentTimeMillis() + timeout :
		Long.MAX_VALUE;
	}

	/**
	 * Returns {@code true} if this value has expired, otherwise
	 * returns {@code false}.
	 */
	boolean isExpired() {
	    return expirationTime <= System.currentTimeMillis();
	}

	/**
	 * Returns this value's associated key.
	 */
	K getKey() {
	    return key;
	}
    }
}
