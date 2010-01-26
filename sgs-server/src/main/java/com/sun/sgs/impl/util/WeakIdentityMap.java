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
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A map that with weak keys that are compared for identity.
 *
 * @param	<K> the key type
 * @param	<V> the value type
 */
public class WeakIdentityMap<K, V> {

    /** The underlying map. */
    private final Map<Key<K>, V> map = new HashMap<Key<K>, V>();

    /** The reference queue, for detecting weak references removals. */
    private final ReferenceQueue<K> queue = new ReferenceQueue<K>();

    /** Creates an instance of this class. */
    public WeakIdentityMap() { }

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
	return map.put(Key.create(key, queue), value);
    }

    /**
     * Checks if the map contains the specified key.
     *
     * @param	key the key
     * @return	{@code true} if the map contains the key, else {@code false}
     */
    public boolean containsKey(K key) {
	processQueue();
	return map.containsKey(Key.create(key, null));
    }

    /**
     * Returns the value associated with given key, or {@code null} if the key
     * is not found.
     *
     * @param	key the key
     * @return	the value associated with the key or {@code null}	
     */
    public V get(Object key) {
	processQueue();
	return map.get(Key.create(key, null));
    }

    /**
     * Removes the association for the given key, returning the value
     * previously associated with the key, or {@code null} if the key is not
     * found.
     *
     * @param	key the key
     * @return	the value previously associated with the key or {@code null}
     */
    public V remove(Object key) {
	processQueue();
	return map.remove(Key.create(key, null));
    }

    /**
     * Returns collection containing all values currently held in this map.
     *
     * @return	a collection containing all values
     */
    public Collection<V> values() {
	processQueue();
	return map.values();
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
	    /* No way to say that the queue holds Keys */
	    @SuppressWarnings("unchecked")
	    Key<K> k = (Key<K>) queue.poll();
	    if (k != null) {
		map.remove(k);
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
    private static final class Key<K> extends WeakReference<K> {

	/** The identity hash code of the referenced object. */
	private final int hash;

	/**
	 * Returns a new instance of this class, or {@code null} if the key is
	 * {@code null}.
	 *
	 * @param	<K> the type of the referenced object
	 * @param	key the referenced object
	 * @param	queue the reference queue or {@code null}
	 * @return	the new instance or {@code null}
	 */
	static <K> Key<K> create(K key, ReferenceQueue<K> queue) {
	    if (key == null) {
		return null;
	    } else if (queue == null) {
		return new Key<K>(key);
	    } else {
		return new Key<K>(key, queue);
	    }
	}

	/**
	 * Creates an instance of this class without registering it with a
	 * reference queue.
	 *
	 * @param	key the referenced object
	 */
	private Key(K key) {
	    super(key);
	    hash = System.identityHashCode(key);
	}

	/**
	 * Creates an instance of this class and registers it with the
	 * specified reference queue.
	 *
	 * @param	key the referenced object
	 * @param	queue the reference queue
	 */
	private Key(K key, ReferenceQueue<K> queue) {
	    super(key, queue);
	    hash = System.identityHashCode(key);
	}

	/**
	 * Returns {@code true} if the argument is an instance of {@link Key}
	 * that refers to the same object.
	 *
	 * @param	o the object to compare
	 * @return	{@code true} if the argument is an instance of {@code
	 *		Key} that refers to the same object, else {@code false}
	 */
	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    } else if (!(o instanceof Key)) {
		return false;
	    }
	    Object k1 = get();
	    Object k2 = ((Key) o).get();
	    return (k1 != null && k2 != null && k1 == k2);
	}

	/**
	 * Returns the identity hash code of the referenced object.
	 *
	 * @return	the identity hash code of the referenced object
	 */
	public int hashCode() {
	    return hash;
	}
    }
}
