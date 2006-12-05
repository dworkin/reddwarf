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
 * Defines a simple managed object {@link Map} that maps serializable keys to
 * serializable values that may also be managed objects.  The keys must not be
 * managed objects.  Both keys and values can be <code>null</code>. <p>
 *
 * Calls to the {@link #put put} and {@link #putAll putAll} methods will throw
 * an {@link IllegalArgumentException} if the keys or values are not
 * <code>null</code> and do not implement {@link Serializable}, or if the keys
 * implement {@link ManagedObject}. <p>
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

    /** Creates an instance of this class with no entries. */
    public SimpleManagedHashMap() { }

    /**
     * Creates an instance with the specified mappings.
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

    /** {@inheritDoc} */
    public int size() {
	return map.size();
    }

    /* Inherit AbstractMap.isEmpty */

    /** {@inheritDoc} */
    public boolean containsKey(Object key) {
	return map.containsKey(key);
    }

    /** {@inheritDoc} */
    public boolean containsValue(Object value) {
	/*
	 * It's OK to do this cast because the result will just be false if the
	 * cast is wrong.
	 */
	@SuppressWarnings("unchecked")
	    V v = (V) value;
	return map.containsValue(new Value<V>(v));
    }

    /** {@inheritDoc} */
    public V get(Object key) {
	return dereference(map.get(key));
    }

    /* -- Modification operations -- */

    /**
     * {@inheritDoc} <p>
     *
     * @throws	IllegalArgumentException if <code>key</code> or
     *		<code>value</code> are not <code>null</code> and do not
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

    /** {@inheritDoc} */
    public V remove(Object key) {
	getDataManager().markForUpdate(this);
	return dereference(map.remove(key));
    }

    /* -- Bulk operations -- */

    /**
     * {@inheritDoc} <p>
     *
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

    /** {@inheritDoc} */
    public void clear() {
	getDataManager().markForUpdate(this);
	map.clear();
    }

    /* -- Views -- */

    /* Inherit AbstractMap.keySet */

    /* Inherit AbstractMap.values */

    /** {@inheritDoc} */
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
