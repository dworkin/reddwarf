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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.sharedutil.Objects;
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
 * ManagedSerializable} when storing it in the data service.  Note: users of
 * this map must use this map's APIs to avoid leaking wrappers for
 * non-managed, serializable objects.
 *
 * <p>Instances of {@code BindingKeyedMap} as well as its associated
 * iterators are serializable, but not managed, objects.
 *
 * @param	<V> the type for the map's values
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
     * @throws	IllegalArgumentException if {@code keyPrefix} is empty
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
	Objects.checkNull("key", key);
	checkSerializable("value", value);

	String bindingName = getBindingName(key);
	V previousValue = get(key);
	if (previousValue != null) {
	    removeValue(bindingName);
	}

	// Store key/value pair.
	putKeyValue(bindingName, value);
	
	return previousValue;
    }

    /** {@inheritDoc} */
    public V get(Object key) {
	checkKey("key", key);
	String bindingName = getBindingName((String) key);
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
	return containsKeyInternal(getBindingName((String) key));
    }

    /** {@inheritDoc} */
    public boolean containsValue(Object value) {
	Objects.checkNull("value", value);
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
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
	checkKey("key", key);
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	String bindingName = getBindingName((String) key);
	V value = null;
	try {
	    ManagedObject v = dataService.getServiceBinding(bindingName);
	    if (v instanceof Wrapper) {
		value = (V) ((Wrapper) v).get();
		dataService.removeObject(v);
	    } else {
		value = (V) v;
	    }
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
	
	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	public boolean contains(Object o) {
	    Entry<String, V> entry = (Entry<String, V>) o;
	    return containsKeyInternal(keyPrefix + entry.getKey());
	}
	
	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	public boolean remove(Object o) {
	    Entry<String, V> entry = (Entry<String, V>) o;
	    return removeOverrideInternal(keyPrefix + entry.getKey());
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
	
	/** {@inheritDoc} */
	public boolean contains(Object o) {
	    return containsKeyInternal(keyPrefix + (String) o);
	}

	/** {@inheritDoc} */
	public boolean remove(Object o) {
	    return removeOverrideInternal(keyPrefix + (String) o);
	}
    }

    /** {@inheritDoc} */
    public Collection<V> values() {
        return new Values<V>(keyPrefix);
    }
    
    /**
     * A serializable {@code Collection} for this map's values.
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
	    removeOverrideInternal(keyReturnedByNext);
	    keyReturnedByNext = null;
	}

	/**
	 * Returns the next entry or throws {@code NoSuchElementException} if
	 * there is no next entry.
	 */
	Entry<String, V> nextEntry() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return new KeyValuePair<V>(
		    prefix,
		    keyReturnedByNext.substring(prefix.length()));
	    } finally {
		nextName = null;
	    }
	}

	/**
	 * Returns the next key or throws {@code NoSuchElementException} if
	 * there is no next key.
	 */
	String nextKey() {
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

	/**
	 * Returns the next value or throws {@code NoSuchElementException} if
	 * there is no next value.
	 */
	V nextValue() {
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
	Objects.checkNull("key", key);
	checkSerializable("value", value);
	String bindingName = getBindingName(key);
	boolean previouslyMapped = containsKeyInternal(bindingName);
	if (previouslyMapped) {
	    try {
		removeValue(bindingName);
	    } catch (ObjectNotFoundException e) {
	    }
	} 
	putKeyValue(getBindingName(key), value);
	return previouslyMapped;
    }

    /** {@inheritDoc} */
    public boolean removeOverride(String key) {
	Objects.checkNull("key", key);
	return removeOverrideInternal(getBindingName(key));
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
    private static final class KeyValuePair<V>
	implements Entry<String, V>, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final String prefix;
	private final String k;

	KeyValuePair(String prefix, String key) {
	    this.prefix = prefix;
	    this.k = key;
	}
	
	/** {@inheritDoc} */
	public String getKey() {
	    return k;
	}

	/** {@inheritDoc} */
	public V getValue() {
	    return getValue(prefix + k);
	}

	/** {@inheritDoc} */
	public V setValue(V value) {
	    checkSerializable("value", value);
	    String bindingName = prefix + k;
	    V previousValue =  getValue(bindingName);
	    if (previousValue != null) {
		removeValue(bindingName);
	    }
	    putKeyValue(bindingName, value);
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
	    return k + "=" + getValue().toString();
	}
	
	@SuppressWarnings("unchecked")
	private V getValue(String bindingName) {
	    try {
		ManagedObject v = BindingKeyedCollectionsImpl.getDataService().
		    getServiceBinding(bindingName);
		return
		    v instanceof Wrapper ?
		    (V) ((Wrapper) v).get() :
		    (V) v;
	    } catch (NameNotBoundException e) {
		throw new IllegalStateException("entry has been removed");
	    }
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
     * Returns {@code true} if a service binding with the specified
     * {@code bindingName} exists.
     */
    private static boolean containsKeyInternal(String bindingName) {
	DataService dataService = BindingKeyedCollectionsImpl.getDataService();
	boolean containsKey = false;
	try {
	    dataService.getServiceBinding(bindingName);
	    containsKey = true;
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	    containsKey = true;
	}
	return containsKey;
    }

    /**
     * If a service binding with the specified {@code bindingName} exists,
     * removes the binding (and the wrapper for the associated value if
     * applicable) and returns {@code true}.  Otherwise, returns
     * {@code false}.
     */
    private static boolean removeOverrideInternal(String bindingName) {
	boolean previouslyMapped = containsKeyInternal(bindingName);
	if (previouslyMapped) {
	    DataService dataService =
		BindingKeyedCollectionsImpl.getDataService();
	    try {
		removeValue(bindingName);
	    } catch (ObjectNotFoundException ignore) {
	    }
	    dataService.removeServiceBinding(bindingName);
	}
	return previouslyMapped;
    }

    /**
     * Removes the wrapper (if applicable) for the value associated with
     * the specified {@code bindingName}.
     *
     * @throws	NameNotBoundException if the service binding does not exist
     * @throws	ObjectNotFoundException if the value associated with the
     *		specified {@code bindingName} has been removed
     */
    private static void removeValue(String bindingName) {
	DataService dataService =
	    BindingKeyedCollectionsImpl.getDataService();
	ManagedObject v = dataService.getServiceBinding(bindingName);
	if (v instanceof Wrapper) {
	    dataService.removeObject(v);
	}
    }
    
    /**
     * Puts the specified {@code key}/{@code value} pair in this map,
     * wrapping the value if the value does not implement {@code
     * ManagedObject}.  The caller is responsible for removing the wrapper
     * for the old value, if applicable.
     *
     * @param	key a key
     * @param	value a value
     */
    private static void putKeyValue(String bindingName, Object value) {
	assert value != null && value instanceof Serializable;
	ManagedObject v =
	    value instanceof ManagedObject ?
	    (ManagedObject) value :
	    new Wrapper<Object>(value);
	BindingKeyedCollectionsImpl.getDataService().
	    setServiceBinding(bindingName, v);
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
     * Throws {@code IllegalArgumentException} of {@code obj} is not
     * serializable.
     */
    private static void checkSerializable(String name, Object obj) {
	Objects.checkNull(name, obj);
	if (!(obj instanceof Serializable)) {
	    throw new IllegalArgumentException(name + " not serializable");
	}
    }

    /**
     * Throws {@code ClassCastException} if (@code key) is not an instance
     * of {@code String}.
     */
    private static void checkKey(String keyName, Object key) {
	Objects.checkNull(keyName, key);
	if (!(key instanceof String)) {
	    throw new ClassCastException(
		"key is not an instance of String: " +
		key.getClass().getName());
	}
    }
}
