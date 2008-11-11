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
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Set;

/**
 * An implementation of a persistent {@code Map} that uses service
 * bindings in the data service to store key/value pairs.
 *
 * <p>The {@code keyPrefix} specified during construction is used as a prefix to
 * each binding key the map uses for an entry. A key provided to this map's
 * methods must have a unique and reproducible {@code toString} result because
 * the {@code toString} result is used as a suffix of the binding's key in the
 * data service.
 */
public class BindingKeyedHashMap<K, V>
    extends AbstractMap<K, V>
    implements Serializable, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The key stop (works for alphanumeric keys). */
    private static final String KEY_STOP = "~";

    /** The key prefix. */
    private final String keyPrefix;

    /**
     * Constructs an instance with the specified {@code keyPrefix}.
     *
     * @param	keyPrefix a key prefix
     */
    BindingKeyedHashMap(String keyPrefix) {
	if (keyPrefix == null) {
	    throw new NullPointerException("null keyPrefix");
	}
	this.keyPrefix = keyPrefix + ".";
	ChannelServiceImpl.getDataService().setBinding(
	    keyPrefix + KEY_STOP, new ManagedNothingness());
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
	KeyValuePair<K, V> pair = new KeyValuePair<K, V>(key, value);
	ChannelServiceImpl.getDataService().
	    setServiceBinding(getBindingName(key.toString()), pair);
	
	return previousValue;
    }

    /** {@inheritDoc} */
    public V get(Object key) {
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    KeyValuePair<K, V> pair = uncheckedCast(
 		ChannelServiceImpl.getDataService().
		    getServiceBinding(bindingName));
	    value = pair.getValue();
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	    // TBD: should binding be removed?
	}
	return value;
    }

    /** {@inheritDoc} */
    public boolean containsKey(Object key) {
	return get(uncheckedCast(key)) != null;
    }

    /** {@inheritDoc} */
    public V remove(Object key) {
	DataService dataService = ChannelServiceImpl.getDataService();
	String bindingName = getBindingName(key.toString());
	V value = null;
	try {
	    // Get value and remove key/value pair.
	    KeyValuePair<K, V> pair = 
		uncheckedCast(dataService.getServiceBinding(bindingName));
	    value = pair.getValue();
	    dataService.removeObject(pair);

	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	}
	dataService.removeServiceBinding(bindingName);
	return value;
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
	    throw new UnsupportedOperationException("size not supported");
	}

	/** {@inheritDoc} */	
	public void clear() {
	    clearInternal(keyPrefix);
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
	    throw new UnsupportedOperationException("size not supported");
	}

	/** {@inheritDoc} */	
	public void clear() {
	    clearInternal(keyPrefix);
	}
    }

    /** {@inheritDoc) */
    public Collection<V> values() {
        return new Values<K, V>(this);
    }
    /**
     * A serializable {@code Set} for this map's entries.
     */
    private static final class Values<K, V>
	extends AbstractCollection<V>,
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
        public Iterator<Entry<K, V>> iterator() {
            return new ValueIterator<K, V>(keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean isEmpty() {
	    return isEmptyInternal(keyPrefix);
	}

	/** {@inheritDoc} */
	public int size() {
	    throw new UnsupportedOperationException("size not supported");
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
    
    /* -- Implement ManagedObjectRemoval -- */

    /** {@inheritDoc} */
    public void removingObject() {
	// remove stopper too.
	// TBD
	throw new AssertionError("not yet implemented");
    }

    /* -- Implement Object -- */
    
    /** {@inheritDoc} */
    public int hashCode() {
	return keyPrefix.hashCode();
    }

    /** {@inheritDoc} */
    public boolean equals(Object o) {
	return
	    o instanceof BindingKeyedHashMap &&
	    keyPrefix.equals(((BindingKeyedHashMap) o).keyPrefix);
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
	    k = new Wrapper<K>(key);
	    v = new Wrapper<V>(value);
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
	    return getKey().hashCode();
	}

	/** {@inheritDoc} */
	public boolean equals(Object o) {
	    if (o instanceof KeyValuePair) {
		KeyValuePair<K, V> pair = uncheckedCast(o);
		return
		    getKey().equals(pair.getKey()) &&
		    getValue().equals(pair.getValue());
	    } else {
		return false;
	    }
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
	    if (obj == null) {
		throw new NullPointerException("null obj");
	    } else if (!(obj instanceof Serializable)) {
		throw new IllegalArgumentException("obj not serializable");
	    }
	    set(obj);
	}

	T get() {
	    if (obj != null) {
		return obj;
	    } else {
		obj = ref.get();
		return obj;
	    }
	}

	void set(T obj) {
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

    private static void clearInternal(String keyPrefix) {
	// TBD
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    private static void checkSerializable(String name, Object obj) {
	if (obj == null) {
	    throw new NullPointerException("null " + name);
	} else if (!(obj instanceof Serializable)) {
	    throw new IllegalArgumentException(name + " not serializable");
	}
    }
    
    /**
     * Instances of this class are used as a stopper for iterating over the values
     * in a {@code BindingKeyedHashMap}.
     */
    private static class ManagedNothingness
	implements ManagedObject, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	public ManagedNothingness() {
	}
    }

}
