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

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedObjectValueMap;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
 * <p>A key provided to this map's methods must have a unique and reproducible
 * {@code toString} result because the {@code toString} result is used as a
 * suffix of the binding's key in the data service.
 */
public class BindingKeyedHashMap<K, V>
    extends AbstractMap<K, V>
    implements ManagedObjectValueMap<K, V>, Serializable, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The key prefix's prefix. */
    private static String PREFIX = BindingKeyedHashMap.class.getName() + "_";

    /** The key stop (works for alphanumeric keys). */
    private static final String KEY_STOP = "~";

    /** The key prefix. */
    private final String keyPrefix;

    /**
     * Constructs an instance.
     */
    public BindingKeyedHashMap() {
	DataService dataService = ChannelServiceImpl.getDataService();
	this.keyPrefix = PREFIX + HexDumper.toHexString(
	    dataService.createReference(this).getId().toByteArray()) + ".";
	dataService.setServiceBinding(
	    getBindingName(KEY_STOP), KeyValuePair.createKeyStop());
	
	// TBD: have "key start" too?
	// TBD: should this throw an exception of a map with the same
	// key prefix already exists?
    }

    /* -- Override AbstractMap methods -- */

    /** {@inheritDoc} */
    public boolean isEmpty() {
	return isEmptyInternal(keyPrefix);
    }
    
    /** {@inheritDoc} */
    public V put(K key, V value) {
	checkSerializable("key", key);
	checkSerializable("value", value);
	
	// Get previous value while removing entry.
	// This is inefficient if there is a previous entry.
	V previousValue = remove(key);

	// Store key/value pair.
	putInternal(key, value);
	
	return previousValue;
    }

    private void putInternal(K key, V value) {
	KeyValuePair<K, V> pair = new KeyValuePair<K, V>(key, value);
	ChannelServiceImpl.getDataService().
	    setServiceBinding(getBindingName(key.toString()), pair);
    }

    /** {@inheritDoc} */
    public V get(Object key) {
	checkNull("key", key);
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    KeyValuePair<K, V> pair = uncheckedCast(
 		ChannelServiceImpl.getDataService().
		    getServiceBinding(bindingName));
	    try {
		if (!key.equals(pair.getKey())) {
		    return null;
		}
	    } catch (ObjectNotFoundException e) {
		return null;
	    }
	    value = pair.getValue();
	} catch (NameNotBoundException e) {
	}
	return value;
    }

    /** {@inheritDoc} */
    public boolean containsKey(Object key) {
	checkNull("key", key);
	DataService dataService = ChannelServiceImpl.getDataService();
	String bindingName = getBindingName(key.toString());
	boolean containsKey = false;
	try {
	    KeyValuePair<K, V>  pair = uncheckedCast(
		dataService.getServiceBinding(bindingName));
	    Object currentKey = pair.getKey();
	    containsKey = currentKey.equals(key);
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	}
	return containsKey;
    }

    /** {@inheritDoc} */
    public boolean containsValue(Object value) {
	checkNull("value", value);
	Iterator iter = new ValueIterator(keyPrefix);
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
	checkNull("key", key);
	DataService dataService = ChannelServiceImpl.getDataService();
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    KeyValuePair<K, V> pair = 
		uncheckedCast(dataService.getServiceBinding(bindingName));
	    Object currentKey = null;
	    try {
		currentKey = pair.getKey();
	    } catch (ObjectNotFoundException e) {
	    }
	    // If key matches current key, get value and remove key/value pair.
	    if (currentKey != null && key.equals(currentKey)) {
		// TBD: should this catching ONFE?  If not, how does the
		// binding get removed if the value has been removed?
		value = pair.getValue();
		dataService.removeObject(pair);
		dataService.removeServiceBinding(bindingName);
	    }

	} catch (NameNotBoundException e) {
	}
	return value;
    }

    /** {@inheritDoc} */
    public void clear() {
	Iterator<K> iter = new KeyIterator<K, V>(keyPrefix);
	while (iter.hasNext()) {
	    iter.next();
	    iter.remove();
	}
    }


    /** {@inheritDoc} */
    public Set<Entry<K, V>> entrySet() {
	return new EntrySet<K, V>(keyPrefix);
    }

    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class EntrySet<K, V>
	extends AbstractSet<Entry<K, V>>
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
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator<K, V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    int size = 0;
	    for (Entry<K, V> entry : this) {
		size++;
	    }
	    return size;
	}

	/** {@inheritDoc} */	
	public void clear() {
	    Iterator<Entry<K, V>> iter = iterator();
	    while (iter.hasNext()) {
		iter.next();
		iter.remove();
	    }
	}
    }

    /** {@inheritDoc} */
    public Set<K> keySet() {
        return new KeySet<K, V>(keyPrefix);
    }
    
    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class KeySet<K, V>
	extends AbstractSet<K>
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
        public Iterator<K> iterator() {
            return new KeyIterator<K, V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    int size = 0;
	    for (K key : this) {
		size++;
	    }
	    return size;
	}

	/** {@inheritDoc} */	
	public void clear() {
	    Iterator<K> iter = iterator();
	    while (iter.hasNext()) {
		iter.next();
		iter.remove();
	    }
	}
    }

    /** {@inheritDoc) */
    public Collection<V> values() {
        return new Values<K, V>(keyPrefix);
    }
    
    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class Values<K, V>
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
            return new ValueIterator<K, V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    int size = 0;
	    for (V value : this) {
		size++;
	    }
	    return size;
	}

	/** {@inheritDoc} */	
	public void clear() {
	    Iterator<V> iter = iterator();
	    while (iter.hasNext()) {
		iter.next();
		iter.remove();
	    }
	}
    }
    /**
     * An abstract iterator for obtaining this map's entries.
     * values.
     */
    private abstract static class AbstractIterator<E, K, V>
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
	    dataService = ChannelServiceImpl.getDataService();
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
	    if (name != null && name.startsWith(prefix) &&
		!name.equals(prefix + KEY_STOP))
	    {
		nextName = name;
		return true;
	    } else {
		key = null;
		return false;
	    }
	}
	
	public abstract E next();
	
	/** {@inheritDoc} */
	public Entry<K, V> nextEntry() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return uncheckedCast(
 		    dataService.getServiceBinding(keyReturnedByNext));
	    } finally {
		nextName = null;
	    }
	}

	/** {@inheritDoc} */
	public void remove() {
	    if (keyReturnedByNext == null) {
		throw new IllegalStateException();
	    }
	    ManagedObject keyValuePair =
		dataService.getServiceBinding(keyReturnedByNext);
	    dataService.removeObject(keyValuePair);
	    dataService.removeServiceBinding(keyReturnedByNext);
	    keyReturnedByNext = null;
	}

	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException
	{
	    s.defaultReadObject();
	    dataService = ChannelServiceImpl.getDataService();
	}
    }

    /**
     * An iterator over the entry set
     */
    private static final class EntryIterator<K, V>
            extends AbstractIterator<Entry<K, V>, K, V>
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
        public Entry<K, V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map.
     */
    private static final class KeyIterator<K, V>
            extends AbstractIterator<K, K, V>
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
	public K next() {
	    return nextEntry().getKey();
	}
    }


    /**
     * An iterator over the values in the tree.
     */
    private static final class ValueIterator<K, V>
            extends AbstractIterator<V, K, V>
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
	    return nextEntry().getValue();
	}
    }

    /* -- Implement ManagedObjectValueMap -- */

    /** {@inheritDoc} */
    public boolean putOverride(K key, V value) {
	checkSerializable("key", key);
	checkSerializable("value", value);
	boolean previouslyMapped = containsKey(key);
	putInternal(key, value);
	return previouslyMapped;
    }

    /** {@inheritDoc} */
    public boolean removeOverride(K key) {
	checkNull("key", key);
	boolean previouslyMapped = containsKey(key);
	if (previouslyMapped) {
	    DataService dataService = ChannelServiceImpl.getDataService();
	    String bindingName = getBindingName(key.toString());
	    dataService.removeObject(
		dataService.getServiceBinding(bindingName));
	    dataService.removeServiceBinding(bindingName);
	}
	return previouslyMapped;
    }
    
    /* -- Implement ManagedObjectRemoval -- */

    /** {@inheritDoc} */
    public void removingObject() {
	/*
	 * Clear map and remove key stop.
	 */
	clear();
	DataService dataService = ChannelServiceImpl.getDataService();
	String keyName = getBindingName(KEY_STOP);
	ManagedObject keyStop = dataService.getServiceBinding(keyName);
	dataService.removeObject(keyStop);
	dataService.removeServiceBinding(keyName);
    }

    /* -- Private classes and methods. -- */

    /**
     * A managed object container for a key/value pair.
     */
    private static class KeyValuePair<K, V>
	implements Entry<K, V>, ManagedObject, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The key. */
	private final Wrapper<K> k;
	/** The value. */
	private Wrapper<V> v;

	/**
	 * Constructs an instance with the specified {@code key} and
	 * {@code value}.
	 *
	 * @param key a key
	 * @param value a value
	 */
	KeyValuePair(K key, V value) {
	    checkNull("key", key);
	    k = new Wrapper<K>(key);
	    v = new Wrapper<V>(value);
	}
	
	static KeyValuePair createKeyStop() {
	    return new KeyValuePair();
	}
	
	private KeyValuePair() {
	    k = new Wrapper<K>(null);
	    v = new Wrapper<V>(null);
	}
	
	/** {@inheritDoc} */
	public K getKey() {
	    return k.get();
	}

	/** {@inheritDoc} */
	public V getValue() {
	    return v.get();
	}

	/** {@inheritDoc} */
	public V setValue(V value) {
	    checkSerializable("value", value);
	    V previousValue = v.get();
	    v.set(value);
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
     * A serializable wrapper containing either a serializable
     * object, or a reference to a managed object.
     */
    private static class Wrapper<T> implements Serializable {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private ManagedReference<T> ref;
	private T obj = null;

	Wrapper(T obj) {
	    set(obj);
	}

	T get() {
	    if (ref != null) {
		return  ref.get();
	    } else {
		return obj;
	    }
	}

	void set(T obj) {
	    if (obj != null && !(obj instanceof Serializable)) {
		throw new IllegalArgumentException("obj not serializable");
	    }
	    if (obj instanceof ManagedObject) {
		ref = uncheckedCast(AppContext.getDataManager().
				    createReference((ManagedObject) obj));
	    } else {
		ref = null;
	    }
	    this.obj = obj;
	}

	private void writeObject(ObjectOutputStream s) throws IOException {
	    if (ref != null) {
		obj = null;
	    }
	    s.defaultWriteObject();
	}

	public String toString() {
	    T o = get();
	    return o != null ? o.toString() : "null";
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
	DataService dataService = ChannelServiceImpl.getDataService();
	String key = dataService.nextServiceBoundName(keyPrefix);
	return
	    key == null ||
	    !key.startsWith(keyPrefix) ||
	    key.equals(keyPrefix + KEY_STOP);
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    private static void checkNull(String name, Object obj) {
	if (obj == null) {
	    throw new NullPointerException("null " + name);
	}
    }

    private static void checkSerializable(String name, Object obj) {
	checkNull(name, obj);
	if (!(obj instanceof Serializable)) {
	    throw new IllegalArgumentException(name + " not serializable");
	}
    }
}
