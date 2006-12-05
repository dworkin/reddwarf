package com.sun.sgs.test.app.util;

import java.util.Map.Entry;

/**
 * Provides a simple implementation of Entry, which supports null keys and
 * values, but does not support modifications via setValue.
 */
class SimpleEntry<K, V> implements Entry<K, V> {

    /** The key. */
    private final K key;

    /** The value. */
    private final V value;

    /** Creates an instance with the specified key and value. */
    static <K, V> Entry<K, V> create(K key, V value) {
	return new SimpleEntry<K, V>(key, value);
    }

    /** Creates an instance with the specified key and value. */
    SimpleEntry(K key, V value) {
	this.key = key;
	this.value = value;
    }

    /* -- Implement Entry -- */

    public K getKey() {
	return key;
    }

    public V getValue() {
	return value;
    }

    public V setValue(V value) {
	throw new UnsupportedOperationException();
    }

    public int hashCode() {
	return entryHash(key, value);
    }

    public boolean equals(Object o) {
	if (!(o instanceof Entry)) {
	    return false;
	}
	Entry e = (Entry) o;
	return safeEquals(key, e.getKey()) &&
	    safeEquals(value, e.getValue());
    }

    /** Checks for equality, allowing nulls. */
    private static boolean safeEquals(Object x, Object y) {
	return x == y || (x != null && x.equals(y));
    }

    /** Returns the hash code for an entry with the specified key and value. */
    static <K, V> int entryHash(K key, V value) {
	return (key == null ? 0 : key.hashCode()) ^
	    (value == null ? 0 : value.hashCode());
    }
}
