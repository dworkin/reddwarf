package com.sun.sgs.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Defines a simple managed object {@link Map} that maps serializable keys to
 * serializable values that may also be managed objects.  The keys must not be
 * managed objects.
 */
public class SimpleManagedHashMap<K, V>
    extends AbstractMap<K, V>
    implements ManagedObject, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** FIXME: The data manager. */
    public static DataManager dataManager;

    /**
     * The underlying map of keys to values or references.
     *
     * @serial
     */
    private final Map<K, Value<V>> map = new HashMap<K, Value<V>>();

    /** The entry set, or null if not yet created. */
    private transient Set<Entry<K, V>> entrySet = null;

    /** Creates an instance of this class. */
    public SimpleManagedHashMap() { }

    /**
     * Creates an instance with the specified mappings.
     *
     * @param	map the mappings to place in this map
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

    /** {@inheritDoc} */
    public V put(K key, V value) {
	if (!(key instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The key must implement Serializable");
	} else if (key instanceof ManagedObject) {
	    throw new IllegalArgumentException(
		"The key must not implement ManagedObject");
	} else if (!(value instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The value must implement Serializable");
	}
	modified();
	return (V) dereference(map.put(key, new Value<V>(value)));
    }

    /** {@inheritDoc} */
    public V remove(Object key) {
	modified();
	return (V) dereference(map.remove(key));
    }

    /* -- Bulk operations -- */

    /* Inherit AbstractMap.putAll */

    /** {@inheritDoc} */
    public void clear() {
	modified();
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
	    if ((object instanceof Entry)) {
		Entry entry = (Entry) object;
		if (containsKey(entry.getKey())) {
		    remove(entry.getKey());
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
	    modified();
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

    /* -- Methods and classes for managed objects and references -- */

    /**
     * Returns the object referred to by the reference, or null if the
     * reference is null.
     */
    static <V> V dereference(Value<V> value) {
	return value == null ? null : value.get();
    }

    /** Stores a value that is a managed object or just serializable. */
    private static class Value<V> {
	private final V value;
	private final ManagedReference ref;

	Value(V value) {
	    if (value instanceof ManagedObject) {
		this.value = null;
		ref = getDataManager().createReference((ManagedObject) value);
	    } else {
		this.value = value;
		ref = null;
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
    }

    /** Notes that this object has been modified. */
    void modified() {
	getDataManager().markForUpdate(this);
    }

    /** Returns the current data data manager. */
    static DataManager getDataManager() {
	return dataManager;
    }
}
