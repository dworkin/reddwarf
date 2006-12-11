package com.sun.sgs.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A simple hash table implementation of {@link Map} that implements {@link
 * ManagedObject} and can store managed object values.  The implementation
 * stores the entire hash table structure using a single managed object, so it
 * is best suited to storing small numbers of objects. <p>
 *
 * This class supports all of the optional operations of the <code>Map</code>
 * interface, as well as <code>null</code> keys and values. <p>
 *
 * Non-<code>null</code> keys and values must implement {@link Serializable}.
 * Values may also implement {@link ManagedObject}, but keys are not permitted
 * to do so.  The {@link #put put} and {@link #putAll putAll} methods enforce
 * these restrictions. <p>
 *
 * The values returned by the <code>entrySet</code>, <code>keySet</code>, and
 * <code>values</code> methods, and their associated iterators, are not
 * serializable. <p>
 *
 * This implementation is not synchronized.
 *
 * @param	<K> the type of the keys stored in the map
 * @param	<V> the type of the values stored in the map
 */
public class SimpleManagedHashMap<K, V>
    extends AbstractMap<K, V>
    implements ManagedObject, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** An empty array, for calling Collection.toArray. */
    private static final Serializable[] EMPTY_SERIALIZABLE_ARRAY = { };

    /**
     * The data manager.  FIXME: Remove this when AppContext.getDataManager is
     * implemented.  -tjb@sun.com (12/04/2006)
     */
    public static DataManager dataManager;

    /**
     * The underlying map of keys to values or references.
     *
     * @serial
     */
    private final Map<K, Value<V>> map = new HashMap<K, Value<V>>();

    /** The entry set, or null if not yet created. */
    private transient Set<Entry<K, V>> entrySet = null;

    /** Creates a map with no entries. */
    public SimpleManagedHashMap() { }

    /**
     * Creates a map with the specified mappings.  The keys and values in the
     * map can be <code>null</code>.
     *
     * @param	map the mappings to place in this map
     * @throws	IllegalArgumentException if <code>map</code> contains keys or
     *		values that are not <code>null</code> and do not implement
     *		{@link Serializable}, or keys that implement {@link
     *		ManagedObject}
     */
    public SimpleManagedHashMap(Map<? extends K, ? extends V> map) {
	putAll(map);
    }

    /* -- Query operations -- */

    /**
     * Returns the number of mappings in this map.
     *
     * @return	the number of mappings in this map
     */
    public int size() {
	return map.size();
    }

    /* Inherit AbstractMap.isEmpty */

    /**
     * Returns <code>true</code> if this map contains a mapping for the
     * specified key, which can be <code>null</code>.
     *
     * @param   key the key whose presence in this map is to be tested
     * @return	<code>true</code> if this map contains a mapping for the
     *		specified key, else <code>false</code>
     */
    public boolean containsKey(Object key) {
	return map.containsKey(key);
    }

    /**
     * Returns <code>true</code> if this map maps one or more keys to the
     * specified value, which can be <code>null</code>.
     *
     * @param	value the value whose presence in this map is to be tested
     * @return	<code>true</code> if this map maps one or more keys to the
     *		specified value, else <code>false</code>
     */
    public boolean containsValue(Object value) {
	/*
	 * It's OK to do this cast because the result will just be false if the
	 * cast is wrong.
	 */
	@SuppressWarnings("unchecked")
	    V v = (V) value;
	return map.containsValue(new Value<V>(v));
    }

    /**
     * Returns the value to which the specified key is mapped in this map, or
     * <code>null</code> if the map contains no mapping for this key.  The key
     * can be <code>null</code>.  A return value of <code>null</code> does not
     * <i>necessarily</i> indicate that the map contains no mapping for the
     * key; it is also possible that the map explicitly maps the key to
     * <code>null</code>. The <code>containsKey</code> method may be used to
     * distinguish these two cases.
     *
     * @param   key the key whose associated value is to be returned
     * @return  the value to which this map maps the specified key, or
     *          <code>null</code> if the map contains no mapping for this key
     * @see	#put(Object, Object) put
     */
    public V get(Object key) {
	return dereference(map.get(key));
    }

    /* -- Modification operations -- */

    /**
     * Associates the specified value with the specified key in this map.  If
     * the map previously contained a mapping for this key, the old value is
     * replaced.  The key and the value can be <code>null</code>.
     *
     * @param	key the key with which the specified value is to be associated
     * @param	value the value to be associated with the specified key
     * @return	the previous value associated with specified key, or
     *		<code>null</code> if there was no mapping for key.  A
     *		<code>null</code> return can also indicate that this map
     *		previously associated <code>null</code> with the specified key.
     * @throws	IllegalArgumentException if either <code>key</code> or
     *		<code>value</code> is not <code>null</code> and does not
     *		implement {@link Serializable}, or if <code>key</code>
     *		implements {@link ManagedObject}
     */
    public V put(K key, V value) {
	if (key != null && !(key instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The key must implement Serializable");
	} else if (key instanceof ManagedObject) {
	    throw new IllegalArgumentException(
		"The key must not implement ManagedObject");
	} else if (value != null && !(value instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The value must implement Serializable");
	}
	getDataManager().markForUpdate(this);
	return dereference(map.put(key, new Value<V>(value)));
    }

    /**
     * Removes the mapping for this key from this map if present.  The key can
     * be <code>null</code>.
     *
     * @param	key the key whose mapping is to be removed from the map
     * @return	the previous value associated with specified key, or
     *		<code>null</code> if there was no mapping for key.  A
     *		<code>null</code> return can also indicate that the map
     *		previously associated <code>null</code> with the specified key.
     */
    public V remove(Object key) {
	getDataManager().markForUpdate(this);
	return dereference(map.remove(key));
    }

    /* -- Bulk operations -- */

    /**
     * Copies all of the mappings from the specified map to this map.  These
     * mappings will replace any mappings that this map had for any of the keys
     * currently in the specified map.  The keys and values in the map can be
     * <code>null</code>.
     *
     * @param	map mappings to be stored in this map
     * @throws	IllegalArgumentException if <code>map</code> contains keys or
     *		values that are not <code>null</code> and do not implement
     *		{@link Serializable}, or keys that implement {@link
     *		ManagedObject}
     */
    public void putAll(Map<? extends K, ? extends V> map) {
	if (map == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	Serializable[] keys = null;
	try {
	    keys = map.keySet().toArray(EMPTY_SERIALIZABLE_ARRAY);
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
		"The keys in the map must implement Serializable", e);
	}
	for (Serializable key : keys) {
	    if (key instanceof ManagedObject) {
		throw new IllegalArgumentException(
		    "The keys in the map must not implement ManagedObject");
	    }
	}
	Serializable[] values = null;
	try {
	    values = map.values().toArray(EMPTY_SERIALIZABLE_ARRAY);
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
		"The values in the map must implement Serializable", e);
	}
	if (keys.length != values.length) {
	    throw new ConcurrentModificationException();
	}
	getDataManager().markForUpdate(this);
	for (int i = 0; i < keys.length; i++) {
	    /* The types of the keys and values are otherwise unchecked */
	    @SuppressWarnings("unchecked")
		K key = (K) keys[i];
	    @SuppressWarnings("unchecked")
		V value = (V) values[i];
	    this.map.put(key, new Value<V>(value));
	}
    }

    /** Removes all mappings from this map. */
    public void clear() {
	getDataManager().markForUpdate(this);
	map.clear();
    }

    /* -- Views -- */

    /* Inherit AbstractMap.keySet */

    /* Inherit AbstractMap.values */

    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is an {@link Entry}.  The collection
     * is backed by the map, so changes to the map are reflected in the
     * collection, and vice-versa.  The collection supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <code>Iterator.remove</code>, <code>Collection.remove</code>,
     * <code>removeAll</code>, <code>retainAll</code>, and <code>clear</code>
     * operations.  It does not support the <code>add</code> or
     * <code>addAll</code> operations.
     *
     * @return	a collection view of the mappings contained in this map.
     * @see	Entry
     */
    public Set<Entry<K, V>> entrySet() {
	if (entrySet == null) {
	    entrySet = new EntrySet();
	}
	return entrySet;
    }

    /** Defines a set of the entries in this map. */
    private final class EntrySet extends AbstractSet<Entry<K, V>> {

	EntrySet() { }

	public Iterator<Entry<K, V>> iterator() {
	    return new EntryIterator();
	}

        public boolean contains(Object object) {
	    return object instanceof Entry &&
		containsKey(((Entry) object).getKey());
        }

        public boolean remove(Object object) {
	    if (object instanceof Entry) {
		Entry entry = (Entry) object;
		Object key = entry.getKey();
		if (containsKey(key)) {
		    SimpleManagedHashMap.this.remove(key);
		    return true;
		}
	    }
	    return false;
        }

        public int size() {
            return SimpleManagedHashMap.this.size();
        }

        public void clear() {
            SimpleManagedHashMap.this.clear();
        }
    }
    
    /** Defines an iterator over the entries in this map. */
    private final class EntryIterator implements Iterator<Entry<K, V>> {

	private final Iterator<Entry<K, Value<V>>> iterator =
	    map.entrySet().iterator();

	public boolean hasNext() {
	    return iterator.hasNext();
	}

	public Entry<K, V> next() {
	    return new SimpleEntry<K, V>(iterator.next());
	}

	public void remove() {
	    getDataManager().markForUpdate(SimpleManagedHashMap.this);
	    iterator.remove();
	}
    }

    /** Defines an entry in an instance of this class. */
    private static class SimpleEntry<K, V> implements Entry<K, V> {

	private final Entry<K, Value<V>> entry;

	SimpleEntry(Entry<K, Value<V>> entry) {
	    this.entry = entry;
	}

	public K getKey() {
	    return entry.getKey();
	}

	public V getValue() {
	    return dereference(entry.getValue());
	}
	    
	public V setValue(V newValue) {
	    return dereference(entry.setValue(new Value<V>(newValue)));
	}

	public boolean equals(Object object) {
	    return object instanceof SimpleEntry &&
		entry.equals(((SimpleEntry) object).entry);
	}

	public int hashCode() {
	    return entry.hashCode();
	}

	public String toString() {
	    return entry.toString();
	}
    }

    /* -- Comparison and hashing -- */

    /* Inherit AbstractMap.equals */

    /* Inherit AbstractMap.hashCode */

    /* -- Other classes and methods -- */

    /**
     * This method always throws <code>CloneNotSupportedException</code>.
     *
     * @throws	CloneNotSupportedException whenever this method is called
     */
    protected Object clone() throws CloneNotSupportedException {
	throw new CloneNotSupportedException();
    }

    /** Stores a value that is a managed object or is just serializable. */
    private static class Value<V> implements Serializable {
	private static final long serialVersionUID = 1;
	private transient V value;
	private transient ManagedReference ref;
	private transient int refHash;

	Value(V value) {
	    if (value instanceof ManagedObject) {
		ref = getDataManager().createReference((ManagedObject) value);
		refHash = value.hashCode();
	    } else {
		this.value = value;
	    }
	}

	V get() {
	    if (ref != null) {
		/* Parameterized maps are inherently non-typesafe. */
		@SuppressWarnings("unchecked")
		    V result = (V) ref.get(ManagedObject.class);
		return result;
	    } else {
		return value;
	    }
	}

	public boolean equals(Object o) {
	    if (o == this) {
		return true;
	    } else if (o instanceof Value) {
		Value v = (Value) o;
		if (ref != null) {
		    if (ref.equals(v.ref)) {
			return true;
		    } else if (refHash == v.refHash &&
			       ref.get(ManagedObject.class).equals(
				   v.ref.get(ManagedObject.class)))
		    {
			return true;
		    }
		} else if (safeEquals(value, v.value)) {
		    return true;
		}
	    } else if (safeEquals(value, o)) {
		return true;
	    }
	    return false;
	}

	public int hashCode() {
	    return (ref != null) ? refHash :
		(value != null) ? value.hashCode() : 0;
	}

	private void writeObject(ObjectOutputStream s) throws IOException {
	    s.defaultWriteObject();
	    if (ref != null) {
		s.writeBoolean(true);
		s.writeObject(ref);
		s.writeInt(refHash);
	    } else {
		s.writeBoolean(false);
		s.writeObject(value);
	    }
	}

	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    boolean isRef = s.readBoolean();
	    if (isRef) {
		ref = (ManagedReference) s.readObject();
		refHash = s.readInt();
	    } else {
		@SuppressWarnings("unchecked")
		    V v = (V) s.readObject();
		value = v;
	    }
	}
    }

    /**
     * Returns the object referred to by the Value, or null if the reference is
     * null.
     */
    static <V> V dereference(Value<V> value) {
	return value == null ? null : value.get();
    }

    /** Returns the current data data manager. */
    static DataManager getDataManager() {
	return dataManager;
    }

    /* Checks for equality, allowing for null. */
    static boolean safeEquals(Object x, Object y) {
	return x == y || x != null && x.equals(y);
    }
}
