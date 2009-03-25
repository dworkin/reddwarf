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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Set;

/**
 * An implementation of a persistent {@code Map} that uses service
 * bindings in the data service to store key/value pairs.  This map does
 * not permit {@code null} keys or values.
 *
 * <p>Note: This map is parameterized by value type only.  A {@code String}
 * is the only valid key type for a {@code BindingKeyedMap}.
 *
 * <p>A value is stored in the data service using its associated key (a
 * String) as a suffix to the {@code keyPrefix} specified during
 * construction.  All values must implement {@code Serializable}.  If a
 * value implements {@code Serializable}, but does not implement {@link
 * ManagedObject}, the value will be wrapped in an instance of {@code
 * ManagedSerializable} when storing it in the data service.
 *
 * <p>Instances of {@code BindingKeyedMap} as well as its associated
 * iterators are serializable, but not managed, objects.
 *
 * @param	V the type for the map's values
 */
public class BindingKeyedMapImpl<V>
    extends AbstractMap<String, V>
    implements BindingKeyedMap<V>, Serializable
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The key prefix. */
    private final String keyPrefix;

    /**
     * Constructs an instance with the specified {@code keyPrefix}.
     *
     * @param	keyPrefix a key prefix
     * @throws	IllegalArgumentException if {@code keyPrefix}is empty
     */
    BindingKeyedMapImpl(String keyPrefix) {
	if (keyPrefix == null) {
	    throw new NullPointerException("null keyPrefix");
	} else if (keyPrefix.isEmpty()) {
	    throw new IllegalArgumentException("empty keyPrefix");
	}
	this.keyPrefix = keyPrefix;
    }

    /* -- Override AbstractMap methods -- */

    /** {@inheritDoc} */
    public boolean isEmpty() {
	return isEmptyInternal(keyPrefix);
    }
    
    /** {@inheritDoc} */
    public V put(String key, V value) {
	checkNull("key", key);
	checkSerializable("value", value);

	// Get previous value while removing entry.
	// This is inefficient if there is a previous entry.
	V previousValue = remove(key);

	// Store key/value pair.
	putInternal(key, value);
	
	return previousValue;
    }

    /**
     * Puts the specified {@code key}/{@code value} pair in this map,
     * wrapping the value if the value does not implement {@code
     * ManagedObject}.
     *
     * @param	key a key
     * @param	value a value
     */
    private void putInternal(String key, V value) {
	ManagedObject v =
	    value instanceof ManagedObject ?
	    (ManagedObject) value :
	    new Wrapper<V>(value);
	BindingKeyedCollectionsImpl.getDataService().
	    setServiceBinding(getBindingName(key), v);
    }

    /** {@inheritDoc} */
    public V get(Object key) {
	checkKey("key", key);
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    value = getValue(bindingName);
	} catch (NameNotBoundException e) {
	}
	return value;
    }

    /** {@inheritDoc} */
    public boolean containsKey(Object key) {
	checkKey("key", key);
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	String bindingName = getBindingName((String) key);
	boolean containsKey = false;
	try {
	    dataService.getServiceBinding(bindingName);
	    containsKey = true;
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	}
	return containsKey;
    }

    /** {@inheritDoc} */
    public boolean containsValue(Object value) {
	checkNull("value", value);
	Iterator iter = new ValueIterator<V>(keyPrefix);
	while (iter.hasNext()) {
	    try {
		if (value.equals(iter.next())) {
		    return true;
		}
	    } catch (ObjectNotFoundException e) {
	    }
	}
	return false;
    }
    
    /** {@inheritDoc} */
    public V remove(Object key) {
	checkKey("key", key);
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    value = removeValue(bindingName);
	    dataService.removeServiceBinding(bindingName);
	} catch (NameNotBoundException e) {
	}
	return value;
    }

    /** {@inheritDoc} */
    public void clear() {
	clearInternal(keyPrefix);
    }

    /** {@inheritDoc} */
    public int size() {
	return sizeInternal(keyPrefix);
    }

    /** {@inheritDoc} */
    public Set<Entry<String, V>> entrySet() {
	return new EntrySet<V>(keyPrefix);
    }

    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class EntrySet<V>
	extends AbstractSet<Entry<String, V>>
	implements Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The key prefix. */
        private final String keyPrefix;

	/**
	 * Constructs an instance with the specified {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix
	 */
        EntrySet(String keyPrefix) {
	    this.keyPrefix = keyPrefix;
	}

	/** {@inheritDoc} */
        public Iterator<Entry<String, V>> iterator() {
            return new EntryIterator<V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    return sizeInternal(keyPrefix);
	}

	/** {@inheritDoc} */	
	public void clear() {
	    clearInternal(keyPrefix);
	}
    }

    /** {@inheritDoc} */
    public Set<String> keySet() {
        return new KeySet<V>(keyPrefix);
    }
    
    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class KeySet<V>
	extends AbstractSet<String>
	implements Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The key prefix. */
        private final String keyPrefix;

	/**
	 * Constructs an instance with the specified {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix
	 */
        KeySet(String keyPrefix) {
	    this.keyPrefix = keyPrefix;
	}

	/** {@inheritDoc} */
        public Iterator<String> iterator() {
            return new KeyIterator<V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    return sizeInternal(keyPrefix);
	}

	/** {@inheritDoc} */	
	public void clear() {
	    clearInternal(keyPrefix);
	}
    }

    /** {@inheritDoc} */
    public Collection<V> values() {
        return new Values<V>(keyPrefix);
    }
    
    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class Values<V>
	extends AbstractCollection<V>
	implements Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The key prefix. */
        private final String keyPrefix;

	/**
	 * Constructs an instance with the specified {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix
	 */
        Values(String keyPrefix) {
	    this.keyPrefix = keyPrefix;
	}

	/** {@inheritDoc} */
        public Iterator<V> iterator() {
            return new ValueIterator<V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    return sizeInternal(keyPrefix);
	}

	/** {@inheritDoc} */	
	public void clear() {
	    clearInternal(keyPrefix);
	}
    }
    /**
     * An abstract iterator for obtaining this map's entries.
     * values.
     */
    private abstract static class AbstractIterator<E, V>
	implements Iterator<E>, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The data service. */
	private transient DataService dataService;
	
	/** The prefix for keys. */
	private final String prefix;

	/** The key used to look up the next service bound name; or null. */
	private String key;

	/** The key returned by {@code next}, or null. */
	private String keyReturnedByNext;
	
	/** The name fetched in the {@code hasNext} method, which
	 * is only valid if {@code hasNext} returns {@code true}. */
	private String nextName;
	
	/**
	 * Constructs an instance of this class with the specified
	 * {@code keyPrefix}.
	 */
	AbstractIterator(String prefix) {
	    this.prefix = prefix;
	    this.key = prefix;
	    dataService = BindingKeyedCollectionsImpl.getDataService();
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (key == null) {
		return false;
	    } 
	    if (nextName != null) {
		return true;
	    }
	    String name = dataService.nextServiceBoundName(key);
	    if (name != null && name.startsWith(prefix)) {
		nextName = name;
		return true;
	    } else {
		key = null;
		return false;
	    }
	}
	
	/** {@inheritDoc} */
	public abstract E next();
	
	/** {@inheritDoc} */
	public void remove() {
	    if (keyReturnedByNext == null) {
		throw new IllegalStateException();
	    }

	    removeValue(keyReturnedByNext);
	    dataService.removeServiceBinding(keyReturnedByNext);
	    keyReturnedByNext = null;
	}
	
	public Entry<String, V> nextEntry() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return new KeyValuePair<String, V>(
		    keyReturnedByNext.substring(prefix.length()),
		    getValue(keyReturnedByNext));
	    } finally {
		nextName = null;
	    }
	}

	public String nextKey() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return keyReturnedByNext.substring(prefix.length());
	    } finally {
		nextName = null;
	    }
	}
	public V nextValue() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return getValue(keyReturnedByNext);
	    } finally {
		nextName = null;
	    }
	}

	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    dataService = BindingKeyedCollectionsImpl.getDataService();
	}
	
	@SuppressWarnings("unchecked")
	private V getValue(String bindingName) {
	    ManagedObject v = dataService.getServiceBinding(bindingName);
	    return
		v instanceof Wrapper ?
		(V) ((Wrapper) v).get() :
		(V) v;
	}

	private void removeValue(String bindingName) {
	    ManagedObject v = null;
	    try {
		v = dataService.getServiceBinding(bindingName);
		if (v instanceof Wrapper) {
		    dataService.removeObject(v);
		}
	    } catch (ObjectNotFoundException ignore) {
		// object has been removed already.
	    }
	}
    }

    /**
     * An iterator over the entry set
     */
    private static final class EntryIterator<V>
            extends AbstractIterator<Entry<String, V>, V>
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an instance with the given {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix.
	 */
        EntryIterator(String keyPrefix) {
	    super(keyPrefix);
	}

	/**
	 * {@inheritDoc}
	 */
        public Entry<String, V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map.
     */
    private static final class KeyIterator<V>
            extends AbstractIterator<String, V>
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an instance with the given {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix.
	 */
        KeyIterator(String keyPrefix) {
	    super(keyPrefix);
	}

	/**
	 * {@inheritDoc}
	 */
	public String next() {



	    return nextKey();
	}
    }

    /**
     * An iterator over the values in the tree.
     */
    static final class ValueIterator<V>
            extends AbstractIterator<V, V>
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an instance with the given {@code keyPrefix}.
	 *
	 * @param keyPrefix a key prefix.
	 */
        ValueIterator(String keyPrefix) {
	    super(keyPrefix);
	}

	/**
	 * {@inheritDoc}
	 */
	public V next() {
	    return nextValue();
	}
    }

    /* -- Implement BindingKeyedMap -- */

    /** {@inheritDoc} */
    public String getKeyPrefix() {
	return keyPrefix;
    }

    /** {@inheritDoc} */
    public boolean putOverride(String key, V value) {
	checkSerializable("key", key);
	checkSerializable("value", value);
	boolean previouslyMapped = containsKey(key);
	putInternal(key, value);
	return previouslyMapped;
    }

    /** {@inheritDoc} */
    public boolean removeOverride(String key) {
	checkNull("key", key);
	boolean previouslyMapped = containsKey(key);
	if (previouslyMapped) {
	    try {
		DataService dataService =
		    BindingKeyedCollectionsImpl.getDataService();
		String bindingName = getBindingName(key);
		ManagedObject v = dataService.getServiceBinding(bindingName);
		if (v instanceof Wrapper) {
		    dataService.removeObject(v);
		}
		dataService.removeServiceBinding(bindingName);
	    } catch (ObjectNotFoundException ignore) {
	    }
	}
	return previouslyMapped;
    }
    
    /* -- Private classes and methods. -- */

    /**
     * A wrapper for a serializable, but not managed, object.
     */
    private static final class Wrapper<V> extends ManagedSerializable<V> {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	Wrapper(V obj) {
	    super(obj);
	}
    }

    /**
     * A serializable {@code Entry} used in entry sets for this map.
     */
    private static final class KeyValuePair<K, V>
	implements Entry<K, V>, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final K k;
	private V v;

	KeyValuePair(K key, V value) {
	    this.k = key;
	    this.v = value;
	}

	
	/** {@inheritDoc} */
	public K getKey() {
	    return k;
	}

	/** {@inheritDoc} */
	public V getValue() {
	    return v;
	}

	/** {@inheritDoc} */
	public V setValue(V value) {
	    checkSerializable("value", value);
	    V previousValue = this.v;
	    this.v = value;
	    return previousValue;
	}

	/** {@inheritDoc} */
	public int hashCode() {
            return getKey().hashCode() ^ getValue().hashCode();
	}

	/** {@inheritDoc} */
	public boolean equals(Object o) {
	    if (o instanceof Entry) {
		Entry entry = (Entry) o;
		Object entryKey = entry.getKey();
		Object entryValue = entry.getValue();
		return
		    entryKey != null && getKey().equals(entryKey) &&
		    entryValue != null && getValue().equals(entryValue);
	    } else {
		return false;
	    }
	}

	/** {@inheritDoc} */
	public String toString() {
	    return k.toString() + "=" + v.toString();
	}
    }

    /**
     * Returns the binding name for the specified key name (i.e.,
     * adds the key prefix to the key name).
     *
     * @param	keyName a key name
     * @return	a binding name
     */
    private String getBindingName(String keyName) {
	return keyPrefix + keyName;
    }
	
    /**
     * Returns {@code true} if there is no key with the given {@code keyPrefix},
     * or the first key with the {@code keyPrefix} is the key stop.
     *
     * @param	keyPrefix a key prefix
     * @returns	{@code true} if the {@code keyPrefix} corresponds to an
     *		empty map 
     */
    private static boolean isEmptyInternal(String keyPrefix) {
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	String key = dataService.nextServiceBoundName(keyPrefix);
	return key == null || !key.startsWith(keyPrefix);
    }

    /**
     * Returns the size of the map with the specified {@code keyPrefix}.
     *
     * @param	keyPrefix a key prefix
     * @return	the size of the map
     */
    private static int sizeInternal(String keyPrefix) {
	int size = 0;
	Iterator<String> iter = new KeyIterator<Object>(keyPrefix);
	while (iter.hasNext()) {
	    iter.next();
	    size++;
	}
	return size;	
    }

    /**
     * Clears the map with the specified {@code keyPrefix}.
     */
    private static void clearInternal(String keyPrefix) {
	Iterator<String> iter = new KeyIterator<Object>(keyPrefix);
	while (iter.hasNext()) {
	    iter.next();
	    iter.remove();
	}
    }

    /**
     * Returns the value associated with the specified {@code bindingName}.
     * removing the wrapper if applicable.
     */
    @SuppressWarnings("unchecked")
    private V getValue(String bindingName) {
	ManagedObject v =
	    BindingKeyedCollectionsImpl.getDataService().
	        getServiceBinding(bindingName);
	return
	    v instanceof Wrapper ?
	    (V) ((Wrapper) v).get() :
	    (V) v;
    }

    /**
     * Returns the value associated with the specified {@code bindingName},
     * removing the wrapper if applicable.
     */
    @SuppressWarnings("unchecked")
    private V removeValue(String bindingName) {
	V value = null;
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	ManagedObject v = dataService.getServiceBinding(bindingName);
	if (v instanceof Wrapper) {
	    value = (V) ((Wrapper) v).get();
	    dataService.removeObject(v);
	} else {
	    value = (V) v;
	}
	return value;
    }

    /**
     * Throws {@code NullPointerException} of {@code obj} is {@code
     * null}
     */
    private static void checkNull(String name, Object obj) {
	if (obj == null) {
	    throw new NullPointerException("null " + name);
	}
    }

    /**
     * Throws {@code IllegalArgumentException} of {@code obj} is not
     * serializable.
     */
    private static void checkSerializable(String name, Object obj) {
	checkNull(name, obj);
	if (!(obj instanceof Serializable)) {
	    throw new IllegalArgumentException(name + " not serializable");
	}
    }

    /**
     * Throws {@code ClassCastException} if (@code key) is not an instance
     * of {@code String}.
     */
    private static void checkKey(String keyName, Object key) {
	checkNull(keyName, key);
	if (!(key instanceof String)) {
	    throw new ClassCastException(
		"key is not an instance of String: " +
		key.getClass().getName());
	}
    }
}
