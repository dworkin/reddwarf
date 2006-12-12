package com.sun.sgs.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.DetectChanges;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A scalable hash table implementation of {@link Map} that implements {@link
 * ManagedObject} and can store managed object values.  The implementation uses
 * a separate managed object for each hash bucket chain, so it permits objects
 * to be obtained from the map without needing to fetch the entire map from the
 * {@link DataManager}.  Because the size of the map is stored in a single
 * managed object, this implementation does not support concurrent
 * modifications. <p>
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
 * This implementation is not synchronized. <p>
 *
 * Two parameters control the performance of this implementation: the capacity,
 * which is the number of buckets in the hash table, and the load factor, which
 * measures how full the table can become before its capacity is increased.
 * When the number of entries in the hash table exceeds the product of the load
 * factor and the current capacity, the capacity is roughly doubled. <p>
 *
 * The default load factor (<code>.75</code>) typically offers a good trade-off
 * between time and space costs.  Higher values decrease the space overhead but
 * increase the lookup cost.  The expected number of entries in the map and its
 * load factor should be considered when setting its initial capacity, so as to
 * minimize the number of times its capacity must be increased.  If the initial
 * capacity is greater than the maximum number of entries divided by the load
 * factor, no capacity changes will ever occur.
 *
 * @param	<K> the type of the keys stored in the map
 * @param	<V> the type of the values stored in the map
 */
public class ScalableManagedHashMap<K, V>
    extends AbstractMap<K, V>
    implements DetectChanges, ManagedObject, Serializable
{
    /*
     * The implementation of this class is derived from revision 1.63 of the
     * java.util.HashMap class from Java 1.5.06.  -tjb@sun.com (12/05/2006)
     */

    /* -- Fields -- */

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The default initial capacity - MUST be a power of two.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     **/
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** An empty array, for calling Collection.toArray. */
    private static final Serializable[] EMPTY_SERIALIZABLE_ARRAY = { };

    /**
     * FIXME: Store the data manager until the AppContext version works.
     * -tjb@sun.com (11/22/2006)
     */
    public static DataManager dataManager;

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     *
     * @serial
     */
    ManagedReference[] table;

    /**
     * The number of key-value mappings contained in this hash map.
     *
     * @serial
     */
    int size;
  
    /**
     * The next size value at which to resize (capacity * load factor).
     *
     * @serial
     */
    private int threshold;
  
    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    private final float loadFactor;

    /** 
     * The hash code for this instance.
     *
     * @serial
     */
    private int hashCode = 0;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient int modCount;

    /** The value returned by entrySet or null. */
    private transient Set<Entry<K, V>> entrySet = null;

    /** The value returned by keySet or null. */
    private transient Set<K> keySet = null;

    /** The value returned by values or null. */
    private transient Collection<V> values = null;

    /* -- Constructors -- */

    /**
     * Constructs an empty map with the default initial capacity
     * (<code>16</code>) and the default load factor (<code>0.75</code>).
     */
    public ScalableManagedHashMap() {
        loadFactor = DEFAULT_LOAD_FACTOR;
        threshold = (int) (DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR);
        table = new ManagedReference[DEFAULT_INITIAL_CAPACITY];
    }

    /**
     * Constructs an empty map with the specified initial capacity and the
     * default load factor (<code>0.75</code>).
     *
     * @param	initialCapacity the initial capacity
     * @throws	IllegalArgumentException if the initial capacity is negative
     */
    public ScalableManagedHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty map with the specified initial capacity and load
     * factor.
     *
     * @param	initialCapacity the initial capacity
     * @param	loadFactor the load factor
     * @throws	IllegalArgumentException if the initial capacity is negative
     *		or the load factor is nonpositive
     */
    public ScalableManagedHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
		"Illegal initial capacity: " + initialCapacity);
	}
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
	}
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException(
		"Illegal load factor: " + loadFactor);
	}
        /* Find a power of 2 >= initialCapacity */
        int capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1;
	}
	this.loadFactor = loadFactor;
        threshold = (int) (capacity * loadFactor);
        table = new ManagedReference[capacity];
    }
  
    /**
     * Constructs a new map containing the specified mappings, and using the
     * default load factor (<code>0.75</code>) and an initial capacity
     * sufficient to hold the specified mappings.  The keys and values in the
     * map can be <code>null</code>.
     *
     * @param	map the mappings to place in this map
     * @throws  IllegalArgumentException if <code>map</code> contains keys or
     *		values that are not <code>null</code> and do not implement
     *		{@link Serializable}, or keys that implement {@link
     *		ManagedObject}
     */
    public ScalableManagedHashMap(Map<? extends K, ? extends V> map) {
        this(Math.max((int) (map.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
	int h = 0;
        for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
            putForCreate(entry.getKey(), entry.getValue(), true);
	    h += entry.hashCode();
        }
	hashCode = h;
    }

    /* -- Implement DetectChanges -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation always returns <code>null</code>, since this object
     * manages its own modified state directly.
     */
    public Object getChangesState() {
	return null;
    }

    /* -- Implement Map -- */

    /**
     * Returns the number of mappings in this map.
     *
     * @return	the number of mappings in this map
     */
    public int size() {
        return size;
    }
  
    /* Inherit AbstractMap.isEmpty */

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
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (HashEntry<K, V> e = getTableEntry(i); e != null; e = e.next) {
	    if (e.hash == hash && safeEquals(key, e.key)) {
                return e.getValue();
	    }
        }
	return null;
    }

    /**
     * Returns <code>true</code> if this map contains a mapping for the
     * specified key, which can be <code>null</code>.
     *
     * @param   key the key whose presence in this map is to be tested
     * @return	<code>true</code> if this map contains a mapping for the
     *		specified key, else <code>false</code>
     */
    public boolean containsKey(Object key) {
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (HashEntry e = getTableEntry(i); e != null; e = e.next) {
            if (e.hash == hash && safeEquals(key, e.key)) {
                return true;
	    }
        }
        return false;
    }

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
	return putInternal(key, value);
    }

    /* Same as put, but without illegal argument checks. */
    private V putInternal(K key, V value) {
	getDataManager().markForUpdate(this);
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (HashEntry<K, V> e = getTableEntryForUpdate(i);
	     e != null;
	     e = e.next)
	{
            if (e.hash == hash && safeEquals(key, e.key)) {
                V oldValue = e.getValue();
		hashCode -= e.hashCode();
                e.setValue(value);
		hashCode += e.hashCode();
                return oldValue;
            }
        }
        modCount++;
	HashEntry<K, V> e = getTableEntry(i);
	HashEntry<K, V> newEntry = new HashEntry<K, V>(hash, key, value, e);
        setTableEntry(i, newEntry);
	hashCode += newEntry.hashCode();
        if (size++ >= threshold) {
            resize(2 * table.length);
	}
        return null;
    }

    /**
     * Copies all of the mappings from the specified map to this map.  These
     * mappings will replace any mappings that this map had for any of the keys
     * currently in the specified map.  The keys and values in the map can be
     * <code>null</code>.
     *
     * @param	m mappings to be stored in this map
     * @throws	IllegalArgumentException if <code>map</code> contains keys or
     *		values that are not <code>null</code> and do not implement
     *		{@link Serializable}, or keys that implement {@link
     *		ManagedObject}
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0) {
            return;
	}
        /*
         * Expand the map if the map if the number of mappings to be added is
         * greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself to at most
         * one extra resize.
         */
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int) (numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY) {
                targetCapacity = MAXIMUM_CAPACITY;
	    }
            int newCapacity = table.length;
            while (newCapacity < targetCapacity) {
                newCapacity <<= 1;
	    }
            if (newCapacity > table.length) {
                resize(newCapacity);
	    }
        }
	Serializable[] keys = null;
	try {
	    keys = m.keySet().toArray(EMPTY_SERIALIZABLE_ARRAY);
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
	    values = m.values().toArray(EMPTY_SERIALIZABLE_ARRAY);
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
	    putInternal(key, value);
	}
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
        HashEntry<K, V> e = removeEntryForKey(key);
        return e == null ? null : e.getValue();
    }

    /** Removes all mappings from this map. */
    public void clear() {
        modCount++;
	clearTable();
        size = 0;
	hashCode = 0;
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
	ManagedReference[] tab = table;
        for (int i = 0; i < tab.length ; i++) {
            for (HashEntry e = getTableEntry(tab, i); e != null; e = e.next) {
                if (e.ref.valueEquals(value)) {
                    return true;
		}
	    }
	}
	return false;
    }

    /* Map views */

    /**
     * Returns a set view of the keys contained in this map.  The set is backed
     * by the map, so changes to the map are reflected in the set, and
     * vice-versa.  The set supports element removal, which removes the
     * corresponding mapping from this map, via the
     * <code>Iterator.remove</code>, <code>Set.remove</code>,
     * <code>removeAll</code>, <code>retainAll</code>, and <code>clear</code>
     * operations.  It does not support the <code>add</code> or
     * <code>addAll</code> operations.
     *
     * @return	a set view of the keys contained in this map
     */
    public Set<K> keySet() {
	if (keySet == null) {
	    keySet = new KeySet();
	}
	return keySet;
    }

    /**
     * Returns a collection view of the values contained in this map.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  The collection supports element
     * removal, which removes the corresponding mapping from this map, via the
     * <code>Iterator.remove</code>, <code>Collection.remove</code>,
     * <code>removeAll</code>, <code>retainAll</code>, and <code>clear</code>
     * operations.  It does not support the <code>add</code> or
     * <code>addAll</code> operations.
     *
     * @return	a collection view of the values contained in this map
     */
    public Collection<V> values() {
	if (values == null) {
	    values = new Values();
	}
        return values;
    }

    /**
     * Returns a collection view of the mappings contained in this map.  Each
     * element in the returned collection is a {@link Entry}.  The collection
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

    /* Inherit AbstractMap.equals */

    /**
     * Returns the hash code value for this map.  The hash code of a map is
     * defined to be the sum of the hash codes of each entry in the map's
     * <code>entrySet()</code> view.  This ensures that
     * <code>t1.equals(t2)</code> implies that
     * <code>t1.hashCode()==t2.hashCode()</code> for any two maps
     * <code>t1</code> and <code>t2</code>, as required by the general contract
     * of <code>Object.hashCode</code>.<p>
     *
     * @return	the hash code value for this map
     */
    public int hashCode() {
	return hashCode;
    }

    /* -- Disable cloning -- */

    /**
     * This method always throws <code>CloneNotSupportedException</code>.
     *
     * @return	does not return
     * @throws	CloneNotSupportedException whenever this method is called
     */
    protected Object clone() throws CloneNotSupportedException {
	throw new CloneNotSupportedException();
    }

    /* -- Private classes -- */

    /*
     * Define a class that serves as a separate managed object for each bucket
     * chain.
     */
    private static class Bucket<K, V> implements ManagedObject, Serializable {

	/** The serial version of this class. */
	private static final long serialVersionUID = 1;

	/** The first entry in the chain. */
	transient HashEntry<K, V> entry;

	/** Creates an instance with the specified first entry. */
	Bucket(HashEntry<K, V> entry) {
	    this.entry = entry;
	}

	/**
	 * Writes the entries for this bucket, followed by a marker for the
	 * end.
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {
	    for (HashEntry<K, V> e = entry; e != null; e = e.next) {
		e.write(s);
	    }
	    HashEntry.writeDone(s);
	}

	/** Reads the entries for this bucket. */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    HashEntry<K, V> previous = null;
	    while (true) {
		HashEntry<K, V> e = HashEntry.read(s);
		if (e == null) {
		    break;
		}
		if (previous == null) {
		    entry = e;
		} else {
		    previous.next = e;
		}
		previous = e;
	    }
	}
    }

    /** The implementation of Entry for this class. */
    static final class HashEntry<K, V> implements Entry<K, V> {
        final int hash;
        final K key;
	PersistentReference<V> ref;
        HashEntry<K, V> next;

        /** Create new entry. */
        HashEntry(int hash, K key, V value, HashEntry<K, V> next) {
            this.hash = hash;
            this.key = key;
	    ref = PersistentReference.create(value);
            this.next = next;
        }
    
	/** Create a new entry when deserializing. */
	HashEntry(K key, PersistentReference<V> ref) {
	    hash = hash(key);
	    this.key = key;
	    this.ref = ref;
	}

        public K getKey() {
            return key;
        }

	public V getValue() {
	    return ref.get();
	}

	public V setValue(V newValue) {
	    V oldValue = ref.get();
	    ref = PersistentReference.create(newValue);
	    return oldValue;
	}

	public boolean equals(Object o) {
	    if (o == this) {
		return true;
	    } else if (o instanceof HashEntry) {
		HashEntry entry = (HashEntry) o;
		return safeEquals(key, entry.getKey()) &&
		    ref.equals(entry.ref);
	    } else if (o instanceof Entry) {
		Entry entry = (Entry) o;
		return safeEquals(key, entry.getKey()) &&
		    ref.valueEquals(entry.getValue());
	    } else {
		return false;
	    }
	}

	public int hashCode() {
	    return (key == null ? 0 : key.hashCode()) ^ ref.hashCode();
	}

        public String toString() {
            return getKey() + "=" + ref.valueToString();
        }

	/** Serializes this entry to a stream. */
	void write(ObjectOutputStream s) throws IOException {
	    /*
	     * Write the ref first, which is never null, so that we can use
	     * null as the end marker.
	     */
	    s.writeObject(ref);
	    s.writeObject(key);
	}

	/** Marks in the stream that the last entry has been written. */
	static void writeDone(ObjectOutputStream s) throws IOException {
	    s.writeObject(null);
	}

	/**
	 * Reads an entry from a stream, returning null if there are no more
	 * entries.
	 */
	static <K, V> HashEntry<K, V> read(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    /* Deserialization is inherently not typesafe. */
	    @SuppressWarnings("unchecked")
		PersistentReference<V> ref =
		(PersistentReference<V>) s.readObject();
	    /*
	     * The ref should never be null, so, if it is, there are no more
	     * entries.
	     */
	    if (ref == null) {
		return null;
	    }
	    @SuppressWarnings("unchecked")
		K key = (K) s.readObject();
	    return new HashEntry<K, V>(key, ref);
	}
    }

    /** A base class for iterators over items in the map. */
    private abstract class HashIterator<E> implements Iterator<E> {
        private HashEntry<K, V> next;		/* next entry to return */
        private int expectedModCount;		/* for fast-fail */
        private int index;			/* current slot */
        private HashEntry<K, V> current;	/* current entry */

        HashIterator() {
            expectedModCount = modCount;
            ManagedReference[] t = table;
            int i = t.length;
            HashEntry<K, V> n = null;
            if (size != 0) {
		/* Advance to first entry */
                while (i > 0 && (n = getTableEntry(t, --i)) == null) {
                    continue;
		}
            }
            next = n;
            index = i;
        }

        public boolean hasNext() {
            return next != null;
        }

        HashEntry<K, V> nextEntry() { 
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
	    }
            HashEntry<K, V> e = next;
            if (e == null)  {
                throw new NoSuchElementException();
	    }
            HashEntry<K, V> n = e.next;
            ManagedReference[] t = table;
            int i = index;
            while (n == null && i > 0) {
                n = getTableEntry(t, --i);
	    }
            index = i;
            next = n;
            return current = e;
        }

        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
	    if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
	    }
            Object k = current.key;
            current = null;
            ScalableManagedHashMap.this.removeEntryForKey(k);
            expectedModCount = modCount;
        }
    }

    /** Iterates over the values in the map. */
    private class ValueIterator extends HashIterator<V> {
	ValueIterator() { }
        public V next() {
            return nextEntry().getValue();
        }
    }

    /** Iterates over the keys in the map. */
    private class KeyIterator extends HashIterator<K> {
	KeyIterator() { }
        public K next() {
            return nextEntry().getKey();
        }
    }

    /** Iterates over the entries in the map. */
    private class EntryIterator extends HashIterator<Entry<K, V>> {
	EntryIterator() { }
        public Entry<K, V> next() {
            return nextEntry();
        }
    }

    /** The key view of the map. */
    private class KeySet extends AbstractSet<K> {
	KeySet() { }
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return ScalableManagedHashMap.this.removeEntryForKey(o) != null;
        }
        public void clear() {
            ScalableManagedHashMap.this.clear();
        }
    }

    /** The value view of the map. */
    private class Values extends AbstractCollection<V> {
	Values() { }
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            ScalableManagedHashMap.this.clear();
        }
    }

    /** The entry view of the map. */
    private class EntrySet extends AbstractSet<Entry<K, V>> {
	EntrySet() { }
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Entry)) {
                return false;
	    }
            Entry e = (Entry) o;
            Entry<K, V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            return removeEntry(o) != null;
        }
        public int size() {
            return size;
        }
        public void clear() {
            ScalableManagedHashMap.this.clear();
        }
    }

    /* -- Private methods -- */

    /**
     * Returns a hash value for the specified object.  In addition to 
     * the object's own hashCode, this method applies a "supplemental
     * hash function," which defends against poor quality hash functions.
     * This is critical because HashMap uses power-of two length 
     * hash tables.<p>
     *
     * The shift distances in this function were chosen as the result
     * of an automated search over the entire four-dimensional search space.
     */
    static int hash(Object x) {
        int h = (x == null) ? 0 : x.hashCode();
        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);
        return h;
    }

    /** Checks for equality, including null. */
    static boolean safeEquals(Object x, Object y) {
	return x == y || (x != null && x.equals(y));
    }

    /**
     * Returns index for hash code h. 
     */
    private static int indexFor(int h, int length) {
        return h & (length-1);
    }

    /** Gets the first entry from a bucket in the current table. */
    private HashEntry<K, V> getTableEntry(int i) {
	return getTableEntry(table, i);
    }

    /** Gets the first entry from a bucket in a table. */
    static <K, V> HashEntry<K, V> getTableEntry(
	ManagedReference[] table, int i)
    {
	ManagedReference ref = table[i];
	if (ref == null) {
	    return null;
	} else {
	    /* Can't check parameterized types here. */
	    @SuppressWarnings("unchecked")
		Bucket<K, V> bucket = ref.get(Bucket.class);
	    return bucket.entry;
	}
    }

    /**
     * Gets the first entry from a bucket in the current table and marks the
     * bucket for update.
     */
    private HashEntry<K, V> getTableEntryForUpdate(int i) {
	return getTableEntryForUpdate(table, i);
    }

    /**
     * Gets the first entry from a bucket in a table and marks the bucket for
     * update.
     */
    private static <K, V> HashEntry<K, V> getTableEntryForUpdate(
	ManagedReference[] table, int i)
    {
	ManagedReference ref = table[i];
	if (ref == null) {
	    return null;
	} else {
	    /* Can't check parameterized types here. */
	    @SuppressWarnings("unchecked")
		Bucket<K, V> bucket = ref.getForUpdate(Bucket.class);
	    return bucket.entry;
	}
    }

    /** Stores an entry into a bucket in the current table. */
    private void setTableEntry(int i, HashEntry<K, V> entry) {
	setTableEntry(table, i, entry);
    }

    /** Stores an entry into a bucket in a table. */
    private void setTableEntry(
	ManagedReference[] table, int i, HashEntry<K, V> entry)
    {
	ManagedReference ref = table[i];
	if (ref == null) {
	    DataManager dm = getDataManager();
	    dm.markForUpdate(this);
	    table[i] = dm.createReference(new Bucket<K, V>(entry));
	} else {
	    /*
	     * Can't check parameterized types here.  -tjb@sun.com
	     * (11/28/2006)
	     */
	    @SuppressWarnings("unchecked")
		Bucket<K, V> bucket = ref.getForUpdate(Bucket.class);
	    bucket.entry = entry;
	}
    }

    /** Clears all entries from the table, removing the Bucket instances. */
    private void clearTable() {
	DataManager dm = getDataManager();
	dm.markForUpdate(this);
	ManagedReference[] tab = table;
        for (int i = 0; i < tab.length; i++) {
	    if (tab[i] != null) {
		dm.removeObject(tab[i].get(Bucket.class));
		tab[i] = null;
	    }
	}
    }

    /**
     * Returns the data manager.  FIXME: Remove when AppManager.getDataManager
     * is in place.  -tjb@sun.com (11/28/2006)
     */
    static DataManager getDataManager() {
	return dataManager;
    }

    /** Returns a reference to a managed object, or null. */
    static ManagedReference reference(ManagedObject value) {
	return value == null ? null : getDataManager().createReference(value);
    }

    /**
     * Returns the value associated with a managed reference, which may be
     * null.
     */
    static <V> V dereference(ManagedReference value, Class<V> type) {
	return value == null ? null : value.get(type);
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     */
    HashEntry<K, V> getEntry(Object key) {
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (HashEntry<K, V> e = getTableEntry(i); e != null; e = e.next) {
	    if (e.hash == hash && safeEquals(key, e.key)) {
		return e;
	    }
	}
        return null;
    }

    /**
     * This method is used instead of put by constructors and
     * pseudoconstructors (clone, readObject).  It does not resize the table,
     * check for comodification, etc.  It calls createEntry rather than
     * addEntry.
     */
    private void putForCreate(K key, V value, boolean check) {
	if (check) {
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
	}
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        /**
         * Look for preexisting entry for key.  This will never happen for
         * clone or deserialize.  It will only happen for construction if the
         * input Map is a sorted map whose ordering is inconsistent w/ equals.
         */
        for (HashEntry<K, V> e = getTableEntry(i); e != null; e = e.next) {
            if (e.hash == hash && safeEquals(key, e.key)) {
                e.setValue(value);
                return;
            }
        }
        setTableEntry(
	    i, new HashEntry<K, V>(hash, key, value, getTableEntry(i)));
        size++;
    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *        must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value
     *        is irrelevant).
     */
    private void resize(int newCapacity) {
        ManagedReference[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }
        ManagedReference[] newTable = new ManagedReference[newCapacity];
        transfer(newTable);
	clearTable();
        table = newTable;
        threshold = (int) (newCapacity * loadFactor);
    }

    /**
     * Transfers all entries from current table to newTable.  Creates fresh
     * Bucket instances.
     */
    private void transfer(ManagedReference[] newTable) {
        ManagedReference[] src = table;
        int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
            HashEntry<K, V> e = getTableEntry(src, j);
            if (e != null) {
                src[j] = null;
                do {
                    HashEntry<K, V> next = e.next;
                    int i = indexFor(e.hash, newCapacity);  
                    e.next = getTableEntry(newTable, i);
                    setTableEntry(newTable, i, e);
                    e = next;
                } while (e != null);
            }
        }
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     */
    HashEntry<K, V> removeEntryForKey(Object key) {
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        HashEntry<K, V> prev = getTableEntry(i);
        HashEntry<K, V> e = prev;
        while (e != null) {
            HashEntry<K, V> next = e.next;
            if (e.hash == hash && safeEquals(key, e.key)) {
                modCount++;
                if (prev == e) {
                    setTableEntry(i, next);
                } else {
		    getDataManager().markForUpdate(this);
		    table[i].getForUpdate(Bucket.class);
                    prev.next = next;
		}
                size--;
		hashCode -= e.hashCode();
		break;
            }
            prev = e;
            e = next;
        }
        return e;
    }

    /** Removes the specified entry */
    HashEntry<K, V> removeEntry(Object o) {
        if (!(o instanceof Entry)) {
            return null;
	}
        Entry entry = (Entry) o;
        int hash = hash(entry.getKey());
        int i = indexFor(hash, table.length);
        HashEntry<K, V> prev = getTableEntry(i);
        HashEntry<K, V> e = prev;
        while (e != null) {
            HashEntry<K, V> next = e.next;
            if (e.hash == hash && e.equals(entry)) {
                modCount++;
                if (prev == e) {
                    setTableEntry(i, next);
                } else {
		    getDataManager().markForUpdate(this);
		    table[i].getForUpdate(Bucket.class);
                    prev.next = next;
		}
                size--;
		hashCode -= e.hashCode();
		break;
            }
            prev = e;
            e = next;
        }
        return e;
    }
}
