/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sun.sgs.app.ManagedObject;

/**
 * A managed hash map.
 */
public class ManagedHashMap<K,V> implements Map<K,V>, ManagedObject, Serializable {
    private static final long serialVersionUID = 1L;

    /** The internal map. */
    private HashMap<K,V> map;

    /**
     * Creates a new {@code ManagedHashMap}.
     */
    public ManagedHashMap() {
        map = new HashMap<K,V>();
    }

    public void clear() {
        map.clear();
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public Set<Map.Entry<K,V>> entrySet() {
        return map.entrySet();
    }

    public boolean equals(Object o) {
        return map.equals(o);
    }

    public V get(Object key) {
        return map.get(key);
    }

    public int hashCode() {
        return map.hashCode();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public V put(K key, V value) {
        return map.put(key, value);
    }

    public void putAll(Map<? extends K,? extends V> m) {
        map.putAll(m);
    }

    public V remove(Object key) {
        return map.remove(key);
    }

    public int size() {
        return map.size();
    }

    public Collection<V> values() {
        return map.values();
    }
}
