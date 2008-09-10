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

package com.sun.sgs.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.impl.service.data.store.db.DataEncoding;

import com.sun.sgs.impl.util.ManagedSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.math.BigInteger;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import static com.sun.sgs.app.util.ScalableHashMap.checkSerializable;


/**
 * A scalable implementation of {@link Map}, which provides a predictable
 * iteration ordering like {@link LinkedHashMap}.  This implementation differs
 * from {@link ScalabeLinkedHashmap} in that it maintains a doubly-linked list
 * of all the entries according to their insertion order.  Therefore, the
 * iteration ordering is equivalent to the insertion ordering.  As with {@code
 * LinkedHashMap} if a key is re-inserted into a map, it will not change the
 * order iteration.  Unlike the {@code LinkedHashMap} class this implementation
 * does not support iterating by access order.
 *
 * <p>
 *
 * The internal structure of the map is separated into distributed pieces, which
 * reduces the amount of data any one operation needs to access.  Due to the
 * nature of maintaining the doubly-linked list of entries, this implementation
 * does not support concurrent operation that change the insertion order: put,
 * when the key is not already present, and remove.  However, this
 * implementation does support concurrent re-insertion operations where the key
 * is already present, as these do not change the insertion order.
 *
 * <p>
 *
 * Uncontended peformance is likely to be just below {@code ScalableHashMap} due
 * to added expense of maintaining the doubly-linked list, with one exception.
 * Iteration over the views of a {@code ScalableLinkedHashMap} will be faster
 * due to fewer object accesses from the data store, and will cause less
 * contention.
 *
 * <p>
 *
 * This class provides two parameters for tuning the behavior of the map.  The
 * first parameter {@code accessOrder} is provided to create a linked hash map
 * whose order of iteration is the order in which its entries were last
 * accessed, from least-recently accessed to most-recently
 * (access-order). Invoking the put or get method results in an access to the
 * corresponding entry (assuming it exists after the invocation completes). The
 * putAll method generates one entry access for each mapping in the specified
 * map, in the order that key-value mappings are provided by the specified map's
 * entry set iterator. No other methods generate entry accesses. In particular,
 * operations on collection-views do not affect the order of iteration of the
 * backing map.
 *
 * <p>
 *
 * The second parameter {@code supportsConcurrentIterators} is used to tune the
 * {@code Iterator} behavior for this class.  If the parameter is set to {@code
 * true}, iterators will correctly traverse all entries of the map, even if the
 * iterator's next entry is removed between tasks.  Also, the iterator will
 * never throw a {@link ConcurrentModificationException}.  However, in this
 * case, using multiple iterators at the same time will cause contention.  In
 * addition each mutating operation to the map incurs a small performance
 * penalty for keeping the iterators consistent with the state of the map.
 * Developers should use this feature if they will need to iterate over a view
 * of the map at the same time it is being modified and want to ensure that the
 * iterator will traverse all the entries.
 *
 * <p>
 *
 * If the {@code supportsConcurrentIterators} parameter is {@code false}, the
 * iterator will only make a best-effort to reflect any concurrent change to the
 * map.  If the next entry that the iterator is to return was been removed
 * during a separate task, the iterator <i>will</i> throw a {@code
 * ConcurrentModificationException}.  In this behavior, mutating operations
 * incur no performance overhead.  Furthermore, multiple iterators will not
 * cause any addtional contention.
 *
 * <p>
 *
 * Developers may override {@link #removeEldestEntry(Map.Entry)} to impose a
 * policy for removing stale mappings automatically when new mappings are added
 * to the map.  See {@link java.util.LinkedHashMap#removeEldestEntry(Map.Entry)}
 * for an example
 *
 * <p>
 *
 * Developers may use this class as a drop-in replacement for the {@link
 * java.util.LinkedHashMap} class.  A {@code LinkedHashMap} will typically
 * perform better than an instance of this class when the number of mappings is
 * small, the objects being stored are small, and minimal concurrency is
 * required.  This class will significantly outperform {@code LinkedHashMap} as
 * the size of the map increases.  Developers are encouraged to profile the
 * serialized size of their map to determine which implementation will perform
 * better.  Note that {@code LinkedHashMap} does not provide any concurrency for
 * {@code Task}s running in parallel that attempt to modify the map at the same
 * time, so this class may perform better in situations where multiple tasks
 * need to modify the map concurrently, even if the total number of mappings is
 * small.  Also note that, unlike {@code LinkedHashMap}, this class can be used
 * to store {@code ManagedObject} instances directly.
 *
 * <p>
 *
 * This implementation requires that all non-{@code null} keys and values
 * implement {@link Serializable}.  Attempting to add keys or values to the map
 * that do not implement {@code Serializable} will result in an {@link
 * IllegalArgumentException} being thrown.  If a key or value is an instance of
 * {@code Serializable} but does not implement {@code ManagedObject}, this class
 * will persist the object as necessary; when such an object is removed from the
 * map, it is also removed from the {@code DataManager}.  If a key or value is
 * an instance of {@code ManagedObject}, the developer will be responsible for
 * removing these objects from the {@code DataManager} when done with them.
 * Developers should not remove these object from the {@code DataManager} prior
 * to removing them from the map.
 *
 * <p>
 *
 * Applications must make sure that objects used as keys in maps of this class
 * have {@code equals} and {@code hashCode} methods that return the same values
 * after the keys have been serialized and deserialized.  In particular, keys
 * that use {@link Object#equals Object.equals} and {@link Object#hashCode
 * Object.hashCode} will typically not be equal, and will have different hash
 * codes, each time they are deserialized, and so are probably not suitable for
 * use with this class.  The same requirements apply to objects used as values
 * if the application intends to use {@link #containsValue containsValue} or to
 * compare map entries.
 *
 * <p>
 *
 * This class marks itself for update as necessary; no additional calls to the
 * {@link DataManager} are necessary when modifying the map.  Developers do not
 * need to call {@code markForUpdate} or {@code getForUpdate} on this map, as
 * this will eliminate all the concurrency benefits of this class.  However,
 * calling {@code getForUpdate} or {@code markForUpdate} can be used if a
 * operation needs to prevent all access to the map.
 *
 * <p>
 *
 * An instance of {@code ScalableLinkedHashMap} offers one parameter for
 * performance tuning: {@code minConcurrency}, which specifies the minimum
 * number of re-insertion operations to support in parallel.  This paramenter
 * will not improve the performance of operations that modify the doubly-linked
 * list of entries.  The {@code minConcurrency} parameter acts as a hint to the
 * map on how to perform internal resizing.  As the map grows, the number of
 * supported parallel operations will also grow beyond the specified minimum.
 * Setting the minimum concurrency too high will waste space and time, while
 * setting it too low will cause conflicts until the map grows sufficiently to
 * support more concurrent operations.
 *
 * <p>
 *
 * Since the expected distribution of objects in the map is essentially random,
 * the actual concurrency will vary.  Developers are strongly encouraged to use
 * hash codes that provide a normal distribution; a large number of collisions
 * will likely reduce the performance.
 *
 * <p>
 *
 * This class provides {@code Serializable} views from the {@link #entrySet
 * entrySet}, {@link #keySet keySet} and {@link #values values} methods.  These
 * views may be shared and persisted by multiple {@code ManagedObject}
 * instances.
 *
 * <p>
 *
 * <a name="iterator"></a> The {@code Iterator} for each view implements {@code
 * Serializable} and {@code ManagedObject}, and therefore an application
 * <i>must</i> remove them from the data store when finished using them.  A
 * single iterator should only be used by a single {@code ManagedObject}
 * instance at a time.  Multiple {@code ManagedObject} instances may have the
 * same iterator as a part of their state, but concurrent traversal of the
 * elements will result in a corrupted ordering.
 *
 * <p>
 *
 * The iterators do not throw {@link java.util.ConcurrentModificationException}.
 * The iterators are stable with respect to concurrent changes to the associated
 * collection.  Attempting to use an iterator when the associated map has been
 * removed from the {@code DataManager} will result in an {@code
 * ObjectNotFoundException} being thrown, although the {@link Iterator#remove
 * remove} method may throw {@code IllegalStateException} instead if that is
 * appropriate.
 *
 * <p>
 *
 * If a call to the {@link Iterator#next next} method on the iterators causes an
 * {@code ObjectNotFoundException} to be thrown because the return value has
 * been removed from the {@code DataManager}, the iterator will still have
 * successfully moved to the next entry in its iteration.  In this case, the
 * {@link Iterator#remove remove} method may be called on the iterator to remove
 * the current object even though that object could not be returned.
 *
 * <p>
 *
 * This class and its iterator implement all optional operations and support
 * both {@code null} keys and values.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @see Object#hashCode Object.hashCode
 * @see java.util.Map
 * @see java.util.LinkedHashMap
 * @see ScalableHashMap
 * @see ScalableDeque
 * @see Serializable
 * @see ManagedObject
 */
public class ScalableLinkedHashMap<K,V>
    extends AbstractMap<K,V>
    implements Serializable, ManagedObjectRemoval {    

    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    
    /**
     * The bound name for the first entry in this map according to the defined
     * iteration order
     */
    private final ManagedReference<ManagedSerializable
	<ManagedReference<LinkedNode<K,V>>>> firstEntry;

    /**
     * The bound name for the last entry in this map according to the defined
     * iteration order
     */
    private final ManagedReference<ManagedSerializable
	<ManagedReference<LinkedNode<K,V>>>> lastEntry;

    /**
     * A mapping from each of the current, usable {@link OrderedIterator}
     * instances to the element that they would next return or {@code null} if
     * this map does not support concurrent iteration.  This mapping is used to
     * update the next element of any iterators that are in a serialized state
     * when their next element is removed.  In keeping a current mapping, the
     * map is able to notify each iterator of a change in the map that would
     * result in the iterator's next element no longer being present.
     *
     * @see ScalableLinkedHashMap#supportsConcurrentIterators
     * @see ScalableLinkedHashMap#checkIterators(LinkedNode)
     * @see OrderedIterator#checkForNextEntryUpdates()
     */
    private ManagedReference<ManagedSerializable<Map<
        BigInteger,ManagedReference<LinkedNode<K,V>>>>>
	serializedIteratorsNextElementsRef;

    /**
     * A reference to the {@code ScalableHashMap} that will store all
     * the mappings
     */
    private final ManagedReference<ScalableHashMap<LinkedNode<K,V>,Marker>>
	backingMapRef;

    /**
     * A transient Java reference to the {@code ScalableHashMap}
     * refered to by {@link #backingMapRef}.
     */
    private transient ScalableHashMap<LinkedNode<K,V>,Marker> backingMap;

    /**
     * If {@code true}, this map should order its entries from least
     * recently accessed to most recently accessed.
     */
    private final boolean accessOrder;

    /**
     * Creates an empty map with the specified minimum concurrency.  Users of
     * this constructor should refer to the class javadoc regarding the
     * performance behavior of concurrent iterators.
     *
     * @param accessOrder whether the iterator order of this deque should be in
     *        order of least recent access
     * @param supportsConcurrentIterators whether this deque should
     *        support concurrent iterators
     *
     * @throws IllegalArgumentException if {@code minConcurrency} is
     *	       not greater than zero
     */
    public ScalableLinkedHashMap(boolean accessOrder, 
				 boolean supportsConcurrentIterators) {


	this.accessOrder = accessOrder;

	backingMap = new ScalableHashMap<LinkedNode<K,V>,Marker>();
	
	DataManager dm = AppContext.getDataManager();
	
	backingMapRef = dm.createReference(backingMap);
	
	// initialize the pointers to the front and end of the entry
	// list to null.  However, the reference to these pointers
	// will always be non-null
	ManagedSerializable<ManagedReference<LinkedNode<K,V>>> first =
	    new ManagedSerializable<ManagedReference<LinkedNode<K,V>>>(null);
	ManagedSerializable<ManagedReference<LinkedNode<K,V>>> last =
	    new ManagedSerializable<ManagedReference<LinkedNode<K,V>>>(null);
	firstEntry = dm.createReference(first);
	lastEntry = dm.createReference(last);	    

	if (supportsConcurrentIterators) {
	    ManagedSerializable<Map<BigInteger,
		ManagedReference<LinkedNode<K,V>>>>
		serializedIteratorsNextElements = new ManagedSerializable<
		Map<BigInteger,ManagedReference<LinkedNode<K,V>>>>(
	        new HashMap<BigInteger,ManagedReference<LinkedNode<K,V>>>());
	    
	    serializedIteratorsNextElementsRef = 
		dm.createReference(serializedIteratorsNextElements);	
	}
	else {
	    serializedIteratorsNextElementsRef = null;
	}
    }

    /**
     * Constructs an empty map with support for concurrent iterators and option
     * access ordering during iteration.
     *
     * @param accessOrder whether the iterator order of this deque should be in
     *        order of least recent access
     */
    public ScalableLinkedHashMap(boolean accessOrder) {
	this(accessOrder, true);
    }

    /**
     * Constructs an empty map with support for concurrent iterators.
     */
    public ScalableLinkedHashMap() {
	this(false, true);
    }

    /**
     * Constructs a new map with the same mappings as the specified {@code Map},
     * and with support for concurrent iterators.
     *
     * @param map the mappings to include
     *
     * @throws IllegalArgumentException if any of the keys or values contained
     *	       in the argument are not {@code null} and do not implement {@code
     *	       Serializable}
     */
    public ScalableLinkedHashMap(Map<? extends K, ? extends V> map) {
	this();
	if (map == null) {
	    throw new NullPointerException(
		"The map argument must not be null");
	}
	putAll(map);
    }

    /**
     * Returns the {@link ScalableHashMap} used to store entries.
     *
     * @return the backing map.
     */
    private ScalableHashMap<LinkedNode<K,V>,Marker> map() {
	if (backingMap == null) 
	    backingMap = backingMapRef.get();
	return backingMap;
    }

    /**
     * Clears the map of all entries.
     */
    public void clear() {

	// clear the backing map
	map().clear();       

	// if we had at least one entry, asychronously remove all the
	// nodes in the entry-list using a dedicated task.
	if (firstEntry != null) {
	    AppContext.getTaskManager().
		scheduleTask(new AsynchronousClearTask<K,V>(firstEntry.
							    get().get()));
	}
	firstEntry.get().set(null);
	lastEntry.get().set(null);
	
	if (supportsConcurrentIterators()) {
	    // let all the iterators know that the map has been cleared by
	    // setting their next element to null
	    Map<BigInteger,ManagedReference<LinkedNode<K,V>>>
		iteratorToCurrentEntry = 
		serializedIteratorsNextElementsRef.getForUpdate().get();
	    
	    // examine each iterator's next entry and set it to null
	    for (Map.Entry<BigInteger,ManagedReference<LinkedNode<K,V>>> e :
		     iteratorToCurrentEntry.entrySet()) {
		e.setValue(null);
	    }
	}
    }

    /**
     * Returns the {@code LinkedNode} mapped to the provided key or {@code null}
     * if none exists.
     *
     * @param key the key used to find the entry in the map
     *
     * @return the entry mapped to the provided key or {@code null} if
     *         none exists.
     */
    LinkedNode<K,V> getEntry(Object key) {
	Entry<LinkedNode<K,V>,Marker> e = map().getEntry(new Finder(key));
	return (e == null) ? null : e.getKey();
    } 
    
    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
	return getEntry(key) != null;
    }

    /**
     * {@inheritDoc} Note that the execution time of this method grows
     * substantially as the map size increases due to the cost of accessing the
     * data manager.
     */
    public boolean containsValue(Object value) {
	for (LinkedNode<K,V> e : map().keySet()) {
	    try {
		V v = e.getValue();
		if (v == value || (v != null && v.equals(value)))
		    return true;
	    } catch (ObjectNotFoundException onfe) {
		// happens if the value was removed out from
		// underneith the map but the key was not removed
	    }
	}
	return false;
    }

    /**
     * Returns the first entry in the map according to iteration order.
     *
     * @return the first entry in the map
     */
    LinkedNode<K,V> firstEntry() {
	ManagedReference<LinkedNode<K,V>> ref = firstEntry.get().get();
	return (ref == null) ? null : ref.get();
    }


    /**
     * Returns the last entry in the map according to iteration order.
     *
     * @return the first entry in the map
     */
    LinkedNode<K,V> lastEntry() {
	ManagedReference<LinkedNode<K,V>> ref = lastEntry.get().get();
	return (ref == null) ? null : ref.get();
    }

    /**
     * Returns the value to which this key is mapped or {@code null} if the map
     * contains no mapping for this key.  Note that the return value of {@code
     * null} does not necessarily imply that no mapping for this key existed
     * since this implementation supports {@code null} values.  The {@link
     * #containsKey containsKey} method can be used to determine whether a
     * mapping exists.
     *
     * @param key the key whose mapped value is to be returned
     *
     * @return the value mapped to the provided key or {@code null} if no such
     *         mapping exists
     *
     * @throws ObjectNotFoundException if the value associated with the key has
     *	       been removed from the {@link DataManager}
     */
    public V get(Object key) {
	LinkedNode<K,V> entry = getEntry(key);
	if (entry != null) {
	    recordAccess(entry);
	    return entry.getValue();
	}
	return null;
    }

    /**
     * Associates the specified key with the provided value and returns the
     * previous value if the key was previous mapped.  This map supports both
     * {@code null} keys and values.
     *
     *<p>
     *
     * If the value currently associated with {@code key} has been removed from
     * the {@link DataManager}, then an {@link ObjectNotFoundException} will be
     * thrown and the mapping will not be changed.
     *
     * @param key the key
     * @param value the value to be mapped to the key
     *
     * @return the previous value mapped to the provided key, if any
     *
     * @throws IllegalArgumentException if either {@code key} or {@code value}
     *	       is not {@code null} and does not implement {@code Serializable}
     * @throws ObjectNotFoundException if the previous value associated with
     *	       the key has been removed from the {@link DataManager}
     */
    public V put(K key, V value) {
	
	LinkedNode<K,V> entry = getEntry(key);
	V old = null;

	// the entry wasn't already present, so put it in the map
	if (entry == null) {
	    entry = new LinkedNode<K,V>(key,value);
	    map().put(entry, Marker.MARKER);
	    
	    // add the entry to the list
	    addLast(entry);	

	    // call the hook after adding the node to the list so any
	    // subclasses can enforce any sizing policies they have
	    LinkedNode<K,V> eldest = firstEntry();
	    if (removeEldestEntry(eldest.toEntry())) {	    

		// the first entry is the least recently access or the
		// last recently added, and therefore the eldest
		removeNode(eldest);
	    }
	}

	// otherwise, the entry was already in the map, so 
	else {
	    old = entry.setValue(value);

	    // if this map is using access order, note that this
	    // operation touched entry.
	    if (accessOrder)
		recordAccess(entry);
	}
	
	return old;
    }


    /**
     * Copies all of the mappings from the provided map into this map.  This
     * operation will replace any mappings for keys currently in the map if they
     * occur in both this map and the provided map.
     *
     * @param m the map to be copied
     *
     * @throws IllegalArgumentException if any of the keys or values contained
     *	       in the argument are not {@code null} and do not implement {@code
     *	       Serializable}.  If this exception is thrown, some of the entries
     *	       from the argument may have already been added to this map.
     */
    public void putAll(Map<? extends K, ? extends V> m) {
	for (Entry<? extends K,? extends V> e : m.entrySet()) {
	    K key = e.getKey();
	    V value = e.getValue();
	    put(key, value);
	}
    }

    /**
     * Adds the provided node to the the first position in the linked entry
     * list.  This method updates the {@link #firstEntry} and {@link #lastEntry}
     * references as necessary.
     *
     * <p>
     *
     * Note that this method is not used by this class but is provided as a
     * routine for subclasses and other classes in this package that may wish to
     * support additional features based on the entry queue.
     *
     * @param e the node to add to the end of the list
     */
    void addFirst(LinkedNode<K,V> e) {
	DataManager dm = AppContext.getDataManager();

	// short-circuit case for adding to a map of which this new
	// entry is the only mapping
	if (firstEntry.get().get() == null) {
	    ManagedReference<LinkedNode<K,V>> ref = dm.createReference(e);
	    firstEntry.get().set(ref);
	    lastEntry.get().set(ref);
	    return;
	}
	    
	LinkedNode<K,V> second = firstEntry();
	firstEntry.get().set(dm.createReference(e));
	e.setPrev(null);
	e.setNext(second);
	second.setPrev(e);
    }


    /**
     * Adds the provided node to the the last position in the linked entry list.
     * This method updates the {@link #firstEntry} and {@link #lastEntry}
     * references as necessary.
     *
     * @param e the node to add to the end of the list
     */
    void addLast(LinkedNode<K,V> e) {
	DataManager dm = AppContext.getDataManager();

	// short-circuit case for adding to a map of which this new
	// entry is the only mapping
	if (firstEntry.get().get() == null) {
	    ManagedReference<LinkedNode<K,V>> ref = dm.createReference(e);
	    firstEntry.get().set(ref);
	    lastEntry.get().set(ref);
	    return;
	}
	    
	LinkedNode<K,V> prev = lastEntry();
	lastEntry.get().set(dm.createReference(e));
	e.setPrev(prev);
	e.setNext(null);
	prev.setNext(e);
    }

    /** 
     * Removes the provided node from the list of entries.  This method updates
     * the {@link #firstEntry} and {@link #lastEntry} references as necessary.
     *
     * @param e the node to remove
     */
    void removeNodeFromList(LinkedNode<K,V> e) {
	LinkedNode<K,V> prev = e.prev();
	LinkedNode<K,V> next = e.next();
	if (prev != null)
	    prev.setNext(next);
	if (next != null)
	    next.setPrev(prev);
	
	// update the references to the front and back of the list, if
	// necessary.  We rely on the invariants that the previous and next
	// entry references will only be null if an entry is at an end of the
	// list.
	DataManager dm = AppContext.getDataManager();
	if (e.prevEntry == null) 
	    firstEntry.get().set((next == null) 
				 ? null : dm.createReference(next));
	if (e.nextEntry == null)
	    lastEntry.get().set((prev == null) 
				? null : dm.createReference(prev));
    }

    /**
     * If {@code accessOrder} is enabled, records the access of the provided
     * node and moves to the the end of the access-order list.
     *
     * @param e the node being accessed
     */
    private void recordAccess(LinkedNode<K,V> e) {
	if (accessOrder) {
	    // remove the entry from its current position
	    removeNodeFromList(e);
	    // put it at the end of the list
 	    addLast(e);
 	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
	return firstEntry.get().get() == null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     *
     * Note that calling this method on a map with more than just a few elements
     * will result in a large execution time.
     */
    public int size() {
	int size = 0;
	LinkedNode n = firstEntry();
	while (n != null) {
	    size++;
	    n = n.next();
	}
	return size;
    }

    /**
     * Removes the mapping for the specified key from this map if present. <p>
     *
     * If the value currently associated with {@code key} has been removed from
     * the {@link DataManager}, then an {@link ObjectNotFoundException} will be
     * thrown and the mapping will not be removed.
     *
     * @param  key key whose mapping is to be removed from the map
     *
     * @return the previous value associated with {@code key}, or {@code null}
     *         if there was no mapping for {@code key}.  (A {@code null} return
     *         can also indicate that the map previously associated {@code
     *         null} with {@code key}.)
     *
     * @throws ObjectNotFoundException if the value associated with the key has
     *	       been removed from the {@link DataManager}
     */
    public V remove(Object key) {
	LinkedNode<K,V> removed = getEntry(key);
	return removeNode(removed);
    }


    /**
     * Removes the provided node from the backing map and the list of entries
     * and then returns the value contained by the node, or {@code null} if the
     * provided node was also {@code null}.
     *
     * @param node the node to be removed from this map
     *
     * @return the contained by the node or {@code null} if the
     *         provided node was also {@code null}
     */
    private V removeNode(LinkedNode<K,V> node) {

	if (node == null)
	    return null;

	V v = node.getValue();
	map().remove(node);	

	// remove the node from the list and update any iterators as
	// necessary
	removeNodeFromList(node);
	checkIterators(node);
	
	AppContext.getDataManager().removeObject(node);
	
	return v;
    }

    /**
     * Returns {@code true} if this map should remove its eldest entry.
     *
     * @param eldest
     *
     * @return {@code true} if this entry should be removed
     *
     * @see java.util.LinkedHashMap.removeEldestEntry(Map.Entry)
     */
    protected boolean removeEldestEntry(Entry<K,V> eldest) {
	return false;
    }

    /**
     * Returns whether this deque will support concurrent iterators.  If {@code
     * false}, the iterators of this map will not receive updates regarding any
     * changes to the map and will throw {@link
     * ConcurrentModificationException}s if next element was removed while the
     * iterator was serialized.
     *
     * @see ScalableLinkedHashMap$OrderedIterator
     * @see ScalableLinkedHashMap#checkIterators(LinkedNode)
     */
    private boolean supportsConcurrentIterators() {
	return serializedIteratorsNextElementsRef != null;
    }

    /**
     * If this map supports concurrent iterators, the methods checks the state
     * of all {@link OrderedIterator} instances to see if the provided entry,
     * which is being removed, is their next entry to return, and updates their
     * state accordingly.
     *
     * <p>
     * 
     * Note that this effect is only meaningful to iterators that are in a
     * serialized state at the time of this call.  This method will never be
     * called if the iterator is currently traversing on the entry prior to the
     * one removed.  This is due to the fact that the {@link
     * ScalableLinkedHashMap#removeEntryFromInsertionList(LinkedNode)} method
     * has to acquire a write lock on the entry the iterator is currently
     * accessing.  Therefore, either the iterator will have to abort and this
     * update will succeed, in which case, the iterator will deserialize again
     * and update to the correct state.  Or, the task doing the removal will
     * abort and the iterator will proceed with its traversal.
     *
     * @param entry the entry being removed
     */
    private void checkIterators(LinkedNode<K,V> entry) {
	// if the map does not support this feature, this method becomes a no-op
	if (!supportsConcurrentIterators())
	    return;

	Map<BigInteger,ManagedReference<LinkedNode<K,V>>>
	    iteratorToCurrentEntry = 
	    serializedIteratorsNextElementsRef.get().get();

	DataManager dm = AppContext.getDataManager();
	ManagedReference entryRef = dm.createReference(entry);
	
	// examine each iterator's next entry and see if it is the one
	// we have just removed
	for (Map.Entry<BigInteger,ManagedReference<LinkedNode<K,V>>> e :
		 iteratorToCurrentEntry.entrySet()) {
	    
	    ManagedReference<LinkedNode<K,V>> nextEntry = e.getValue();

	    // if the iterator was going to return the removed entry
	    // next, then we need to update it with the nextInsert
	    // value from the removed entry
	    if (nextEntry != null && nextEntry.equals(entryRef)) {

		// mark that the map has changed
		dm.markForUpdate(serializedIteratorsNextElementsRef.get());
		
		// then update the iterators next value
		e.setValue(entry.nextEntry);
	    }
	}

    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation removes from the {@code DataManager} all non-{@code
     * ManagedObject} keys and values persisted by this map, as well as objects
     * that make up the internal structure of the map itself.
     */
    public void removingObject() {
	clear();	
    }

    /**
     * A utility class used to find {@code LinkedNode} entries in the backing
     * map.  This class mimics the {@hashCode} and {@code equals} methods of
     * {@code LinkedNode} so that with just the key object, node can be located.
     * This class is necessary because when a user calls {@code
     * ScalableLinkedHashMap#get(Object)} the value is not known, and so a new
     * {@code LinkedNode} cannot be created.  Therefore a {@code Finder} is
     * created to mimic the behavior of the node and locate in the backing map.
     *
     * @see ScalableLinkedHashMap$LinkedNode#equals(Object)
     * @see ScalableLinkedHashMap#get(Object)
     */
    private static class Finder {
	/*
	 * IMPLEMENTATION NOTE: this class does not implement Serializable to
	 * ensure that it can never be accidentally stored in the backing map.
	 * (Or that if it was, an error would occur such that the programmer
	 * would know)
	 */
	
	/**
	 * The key of the node for which this instance will equal.
	 */
	private Object key;

	/**
	 * Constructs a {@code Finder} with the provided key.
	 */
	public Finder(Object key) {
	    this.key = key;
	}
	
	/**
	 * Returns {@code true} if {@code o} is an instance of {@code
	 * LinkedNode} and has the same key as the one provided to
	 * this instance.
	 */
	public boolean equals(Object o) {
	    try {
		if (o == null)
		    return false;
		else if (o instanceof LinkedNode) {
		    LinkedNode e = (LinkedNode)o;
		    Object oKey = e.getKey();
		    return key == oKey || (key != null && key.equals(oKey));
		}
		// this ensures a reflexive equals and ensures that this class
		// does not break the equals and hashCode contract
		else if (o instanceof Finder) {
		    Finder f = (Finder)o;
		    return key == f.key || (key != null && key.equals(f.key));
		}
	    }
	    catch (ObjectNotFoundException onfe) {
		// the entry we are being compared against must have had one of
		// its objects removed, so return false.
		
		// REMINDER: we could do clean-up here to remove the now
		//           unreachable entry?
	    }
	    return false;
	}

	/**
	 * Returns the hash code of the provided key.
	 */
	public int hashCode() {
	    return (key == null) ? 0 : key.hashCode();
	}
    }

    /**
     * A class used to store the key-value mapping in the backing map.  Each
     * {@code LinkedNode} maintains links to its neighbors on each side.  This
     * class relies on the {@code ScalableLinkedHashMap} class to ensure that these
     * neighbors are correct for the state of the map.
     *
     * <p>
     *
     * Notes that this class does not implement {@link Map.Entry} as it cannot
     * support the {@link Map.Entry#hashCode() hashCode} contract.  In order for
     * the map to be able find {@code LinkedNode} instances based on a key, this
     * class is required to use the {@code hashCode} on the key contained
     * within.  Therefore, when a {@code LinkedNode} is stored in the backing
     * map, it can be retrieved by the key alone.  See the {@link
     * ScalableLinkedHashMap$Finder Finder} class for details on how this is
     * done.
     *
     * <p>
     *
     * To support the correct behavior of the entry set, this class provides a
     * {@code Map.Entry} view on its state.  These views are generated from
     * using the {@link ScalableLinkedHashMap$LinkedNode#toEntry() toEntry}
     * method.
     *
     * @see ScalableLinkedHashMap$Finder
     */
    static class LinkedNode<K,V>
	implements ManagedObject, Serializable {

	/**
	 * {@inheritDoc}
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The state bit mask for when the key is accessed by a {@code
	 * ManagedReference}.
	 */
	private static final int USE_KEY_REF = 1;

	/**
	 * The state bit mask for when the key is accessed by a {@code
	 * ManagedReference}.
	 */
	private static final int USE_VALUE_REF = 2;
	
	/**
	 * A reference to the key if the key is an instance of {@code
	 * ManagedObject}, or {@code null} if the key is not an
	 * instance.
	 */
	private transient ManagedReference<K> keyRef;

	/**
	 * A Java reference to the key if the key is <i>not</i> an
	 * instance of {@code ManagedObject}, or {@code null} if the
	 * key is an instance.
	 */
	private transient K key;

	/**
	 * A reference to the key if the value is an instance of {@code
	 * ManagedObject}, or {@code null} if the value is not an
	 * instance.
	 */
	private transient ManagedReference<V> valueRef;

	/**
	 * A reference to the key if the value is <i>not</i> an
	 * instance of {@code ManagedObject}, or {@code null} if the
	 * value is an instance.
	 */
	private transient V value;

	/**
	 * The state of the key and value, which is a combination of the {@code
	 * USE_KEY_REF} and {@code USE_VALUE_REF}.  A byte is used for the state
	 * instead of two {@code boolean} values in order to save serialization
	 * space.
	 *
	 * @serial
	 */
	byte state = 0;

	/**
	 * A reference to the next entry after this entry in the
	 * linked list that represents the iteration order
	 */ 
	private ManagedReference<LinkedNode<K,V>> prevEntry;

	/**
	 * A reference to the previous entry after this entry in the
	 * linked list that represents the iteration order
	 */ 
	private ManagedReference<LinkedNode<K,V>> nextEntry;


	/**
	 * Initializes this {@code LinkedNode} with the provided key
	 * and value.
	 */
	LinkedNode(K k, V v) {

	    this.prevEntry = null;
	    this.nextEntry = null;

	    checkSerializable(k, "k");
	    
	    DataManager dm = AppContext.getDataManager();
	    if (k != null && k instanceof ManagedObject) {
		keyRef = dm.createReference(k);
		state |= USE_KEY_REF;
	    }
	    else {
		key = k;
	    }
	    setValue(v);
	}

	/**
	 * Returns {@code true} if {@code o} either is an isntance of
	 * {@code LinkedNode} with the same key and value, <i>or</i>
	 * if o is an instance of {@code Finder} and has the same key.
	 * This second equals case is necessary to locate this node in
	 * the backing map when the value is not known.
	 */
	public boolean equals(Object o) {
	    if (o instanceof LinkedNode) {
		LinkedNode<K,V> e = uncheckedCast(o);
		K k;
		return ((k = getKey()) == null) ? 
		    e.getKey() == null : k.equals(e.getKey());		
	    }
	    else if (o instanceof Finder) {
		return ((Finder)o).equals(this);
	    }
	    return false;
	}

	public K getKey() {
	    return (useKeyRef()) ? keyRef.get() : key;
	}

	public V getValue() {
	    return (useValueRef()) ? valueRef.get() : value;
	}

	/**
	 * Returns the {@code hashCode} of the key.
	 */
	public final int hashCode() {
	    K k;
	    return ((k = getKey()) == null) ? 0 : k.hashCode();
	}

	
	/**
	 * Returns the {@code LinkedNode} after this instance in the map, or
	 * {@code null} if this element is the end of the iteration order.
	 *
	 * @return the {@code LinkedNode} after this instance in the map, or
	 *         {@code null} if this element is the end iteration order.
	 */
	LinkedNode<K,V> next() {
	    return (nextEntry == null) ? null : nextEntry.get();
	}

	/**
	 * Returns the {@code LinkedNode} before this instance in the map, or
	 * {@code null} if this element is the first element in the iteration
	 * order.
	 *
	 * @return the {@code LinkedNode} before this instance in the map, or
	 *         {@code null} if this element is the first element in the
	 *         iteration order.
	 */
	LinkedNode<K,V> prev() {
	    return (prevEntry == null) ? null : prevEntry.get();
	}

	/**
	 * Sets the link from this {@code LinkedNode} to the next
	 * {@code LinkedNode} in the map to {@code next}.
	 *
	 * @code next the {@code LinkedNode} after this {@code LinkedNode} in
	 *       the map according to the iteration order
	 */
	void setNext(LinkedNode<K,V> next) {
	    DataManager dm = AppContext.getDataManager();
	    ManagedReference<LinkedNode<K,V>> ref = 
		(next == null) ? null : dm.createReference(next);

	    dm.markForUpdate(this);
	    nextEntry = ref;
	}	

	/**
	 * Sets the link from this {@code LinkedNode} to the previous
	 * {@code LinkedNode} in the map to {@code prev}.
	 *
	 * @code prev the {@code LinkedNode} before this {@code LinkedNode} in
	 *       the map according to the iteration order
	 */
	void setPrev(LinkedNode<K,V> prev) {
	    DataManager dm = AppContext.getDataManager();
	    ManagedReference<LinkedNode<K,V>> ref = 
		(prev == null) ? null : dm.createReference(prev);

	    dm.markForUpdate(this);
	    prevEntry = ref;
	}

	/**
	 * Replaces the previous value of this entry with the provided value.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
	public final V setValue(V v) {
	    checkSerializable(v, "v");
	    V old = getValue();
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);
	    if (v != null && v instanceof ManagedObject) {
		valueRef = dm.createReference(v);
		state |= USE_VALUE_REF;
	    }
	    else {
		value = v;
		state &= ~USE_VALUE_REF;
	    }
	    return old;
	}

	/**
	 * Returns a {@link Map.Entry} instance that is backed by this
	 * {@code LinkedNode}.
	 *
	 * @return a {@code Map.Entry} instance
	 */
	Entry<K,V> toEntry() {
	    return new EntryView<K,V>(this);
	}

	/**
	 * Returns the string form of this entry as {@code entry}={@code
	 * value}.
	 */
	public String toString() {
	    return getKey() + "=" + getValue();
	}

	/**
	 * Returns {@code true} if the key should be access using
	 * {@code keyRef}.
	 */
	private boolean useKeyRef() {
	    return (state & USE_KEY_REF) > 0;
	}

	/**
	 * Returns {@code true} if the value should be access using
	 * {@code valueRef}.
	 */
	private boolean useValueRef() {
	    return (state & USE_VALUE_REF) > 0;
	}

	/**
	 * Writes out all non-transient state and then conditionally writes for
	 * both the key and the value either the {@code ManagedReference} that
	 * points to them or the value depending on whether this {@code
	 * LinkedNod} is supposed to use a {@code ManagedReference} to access
	 * its them.
	 *
	 * @param s {@inheritDoc}
	 */
	private void writeObject(ObjectOutputStream s)
	    throws IOException {
	    // write out all the non-transient state
	    s.defaultWriteObject();

	    // conditionally write either the ManagedReference to the key or the
	    // key itself, if it did not implement ManagedObject.  Then do the
	    // same for the value
	    s.writeObject((useKeyRef()) ? keyRef : key);		
	    s.writeObject((useValueRef()) ? valueRef : value);		
	}

	/**
	 * Reconstructs the {@code PrefixEntry} and initializes {@code keyRef}
	 * or {@code key} depending on whether this entry is supposed to use a
	 * {@code ManagedReference} to access its key, and then initializes the
	 * value in the same manner.
	 *
	 * @param s {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException {
	    
	    // read in all the non-transient state
	    s.defaultReadObject();	
	    
	    if (useKeyRef()) {
		keyRef = (ManagedReference<K>)(s.readObject());
	    }
	    else {
		key = (K)(s.readObject());
	    }
	    if (useValueRef()) {
		valueRef = (ManagedReference<V>)(s.readObject());
	    }
	    else {
		value = (V)(s.readObject());
	    }
	}
    }
    
    /**
     * A utility class for wrapping {@code LinkedNode} instances and
     * presenting them as {@code Map.Entry} instances.  This class is
     * necessary for the correct behavior of the entry set.
     */
    // NOTE: we can't extend SimpleEntry<K,V> because it implements
    // Serializable and this class should not
    private static final class EntryView<K,V> 
	implements Entry<K,V> {

	/**
	 * The backing node
	 */
	private final LinkedNode<K,V> e;

	/**
	 * Constructs a new {@code Entry} that is backed by the
	 * provided {@code LinkedNode}.
	 */
	public EntryView(LinkedNode<K,V> e) {
	    this.e = e;
	}

	/**
	 * {@inheritDoc}
	 */ 
	public boolean equals(Object o) {
	    if (!(o instanceof Entry))
		return false;
	    
	    try {
		Entry<K,V> e = uncheckedCast(o);
		K k = getKey();
		K kk = e.getKey();
		if (k == kk || (k != null && k.equals(kk))) {
		    V v = getValue();
		    V vv = e.getValue();
		    return (v == vv || (v != null && v.equals(vv)));
		}
	    }
	    catch (ObjectNotFoundException onfe) {
		// one or more of the objects linked to by either
		// entry has been removed from the backing store, so
		// we are unable to tell if these entries are equal
	    }
	    return false;
	}

	/**
	 * {@inheritDoc}
	 */ 
	public K getKey() {
	    return e.getKey();
	}

	/**
	 * {@inheritDoc}
	 */ 
	public V getValue() {
	    return e.getValue();
	}
	
	/**
	 * {@inheritDoc}
	 */ 
	public int hashCode() {
	    K k; V v;
	    return 
		(((k = getKey()) == null) ? 0 : k.hashCode()) ^
		(((v = getValue()) == null) ? 0 : v.hashCode());
		
	}

	/**
	 * {@inheritDoc}
	 */ 
	public V setValue(V value) {
	    return e.setValue(value);
	}

	/**
	 * {@inheritDoc}
	 */ 
	public String toString() {
	    return getKey() + " = " + getValue();
	}

    }

    /**
     * A concurrent, persistable {@code Iterator} implementation for the {@code
     * ScalableLinkedHashMap}.  If the backing map does not support concurrent
     * updates to all outstanding iterators, then this class may throw a {@link
     * ConcurrentModificationException} if the entry which it would return next
     * has been removed while this iterator was in a serialized state.
     *
     * <p>
     *
     * If an iterator is created for an empty map, and then serialized, it will
     * remain valid upon any subsequent deserialization.  An iterator in this
     * state, where it has been created but {@code next} has never been called,
     * will always begin an the first entry in the map, if any, since its
     * deserialization.
     *
     * <p> 
     *
     * Instance of this class are <i>not</i> designed to be shared between
     * concurrent tasks.
     */
    abstract static class OrderedIterator<E,K,V>
	implements Iterator<E>, Serializable, ManagedObjectRemoval {

	/**
	 * The version of the serialized form. 
	 */
	private static final long serialVersionUID = 2;

	/**
	 * Whether the current entry has already been removed
	 */
	private boolean currentRemoved;

	/**
	 * A reference to the current entry
	 */
	private ManagedReference<LinkedNode<K,V>> nextEntry;

	/**
	 * A reference to the current entry
	 */
	private ManagedReference<LinkedNode<K,V>> curEntry;

	/**
	 * A reference to the backing map
	 */
	private ManagedReference<ScalableLinkedHashMap<K,V>> backingMapRef;

	/**
	 * A reference to the map where {@code OrderedIterator} isntances
	 * register their next entry so that upon deserialization, the iterator
	 * exhibits correct behavior.  This reference will be {@code null} if
	 * the backing map does not support concurrent iteration.
	 */
	private ManagedReference<ManagedSerializable<Map<
	    BigInteger,ManagedReference<LinkedNode<K,V>>>>>
	    serializedIteratorsNextElementsRef;

	/**
	 * {@code true} if this iterator was created with an empty backing map.
	 * In this case the iterator will remain at the head of the list and
	 * valid until {@code next} is called.
	 */
	private boolean nextEntryWasNullOnCreation;

	/**
	 * {@code true} if this iterator has just been deserialized and needs to
	 * recheck whether its next entry is still valid.
	 *
	 * @see #checkForNextEntryUpdates()
	 */
	private transient boolean recheckNextEntry;

	/**
	 * The id of this iterator that will be used in the {@code
	 * serializedIteratorsNextElementsRef} map.
	 */
	private final BigInteger iteratorId;

	/**
	 * Constructs a new {@code OrderedIterator}.
	 *
	 * @param backingMap the root node of the {@code ScalableLinkedHashMap}
	 */
	OrderedIterator(ScalableLinkedHashMap<K,V> backingMap) {

	    currentRemoved = false;
	    curEntry = null;

	    DataManager dm = AppContext.getDataManager();
	    LinkedNode<K,V> first = backingMap.firstEntry();	    
	    nextEntry = (first == null) ? null : dm.createReference(first);

	    // mark if the next entry was null.  If so, if we serialize this
	    // iterator and then deserialize it, we should refresh the first
	    // entry in the map
	    nextEntryWasNullOnCreation = nextEntry == null;

	    // note that this field may be null, in which case, we don't update
	    // our state and will throw a concurrent modification exception
	    serializedIteratorsNextElementsRef = 
		backingMap.serializedIteratorsNextElementsRef;

	    backingMapRef = dm.createReference(backingMap);
	    iteratorId = dm.createReference(this).getId();
	    
	    recheckNextEntry = false;

	    // last, mark in the shared map what the next element will be for
	    // this iterator.  This ensures that if the iterator is left unused
	    // and serialized after construction that the the next element will
	    // be correctly updated if any future modifications occur
	    updatePersistentNextEntry();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean hasNext() {
	    if (recheckNextEntry) {
		checkForNextEntryUpdates();
	    }
	    return nextEntry != null;
	}

	/**
	 * After deserialization, this iterator should check that its reference
	 * to the next entry is still valid.  Two cases exist to check.
	 *
	 * <p>
	 *
	 * First, if this iterator was created based on an empty map, and has
	 * never iterated over the first element, the iterator must check
	 * whether any new elements exist in the map.  Once an element exists,
	 * the iterator updates its nextEntry reference and is no longer in the
	 * "empty map" state.
	 *
	 * <p>
	 *
	 * In the second case, while serialized, the next entry could have been
	 * removed from the backing map.  Should it have been removed, the map
	 * will have updated the shared mapping from iterator to next entry,
	 * with a reference as to what this iterator's new next entry should.
	 *
	 * @see ScalableLinkedHashMap#checkIterators(LinkedNode)
	 */
	private void checkForNextEntryUpdates() {

	    // check to see if this iterator was created with a null first
	    // entry.  This flag will only be true if this iterator has never
	    // seen a first entry
	    if (nextEntryWasNullOnCreation) {

		// see if the first entry in the map is now non-null
		LinkedNode<K,V> first = backingMapRef.get().firstEntry();
		nextEntry = (first == null) ? null : 
		    AppContext.getDataManager().createReference(first);
		
		// mark if the next entry is now non-null.  If so, if we unset
		// the flag and the iterator, which will never be set to true
		// again.
		if (nextEntry != null) {
		    AppContext.getDataManager().markForUpdate(this);
		    nextEntryWasNullOnCreation = false;
		}
	    }
	    // check if this iterator has a shared state with the backing map
	    else if (isConcurrentIterator()) {
		// otherwise, this iterator has seen at least one entry and had
		// updated the map prior to serialization what its next entry
		// was.  In this case, we should check to see if the next entry
		// prior to serialization has been removed.
		Map<BigInteger,ManagedReference<LinkedNode<K,V>>>
		    iteratorToNextEntry = 
		    serializedIteratorsNextElementsRef.getForUpdate().get();
		
		// remove ourselves and assign whatever is listed as the next
		// entry for us
		ManagedReference<LinkedNode<K,V>> oldNext = nextEntry;
		nextEntry = iteratorToNextEntry.remove(iteratorId);

		// if the next entry has changed, mark the iterator for update
		if (!(nextEntry == oldNext || 
		      (nextEntry != null && nextEntry.equals(oldNext)))) {
		    AppContext.getDataManager().markForUpdate(this);
		}
	    }

	    recheckNextEntry = false;
	}

	/**
	 * Returns {@code true} if this iterator does not keep a shared state
	 * with the backing map
	 *
	 * @see ScalalbleLinkedHashMap#supportsConcurrentIterators()
	 */ 
	private boolean isConcurrentIterator() {
	    return serializedIteratorsNextElementsRef != null;
	}

	/**
	 * Returns the next entry in the {@code ScalableLinkedHashMap}.  Note that
	 * due to the concurrent nature of this iterator, this method may skip
	 * elements that have been added after the iterator was constructed.
	 * Likewise, it may return new elements that have been added.  This
	 * implementation is guaranteed never to return an element more than
	 * once.
	 *
	 * <p>
	 *
	 * If this iterator keeps a shared state with the backing map, this
	 * method will never throw a {@link
	 * java.util.ConcurrentModificationException}.  Otherwise it will throw
	 * the exception if the entry that it would have returned next has been
	 * removed while the iterator was in serialized form.
	 *
	 * @return the next entry in the {@code ScalableLinkedHashMap}
	 *
	 * @throws NoSuchElementException if no further entries exist
	 */
	Entry<K,V> nextEntry() {
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }
	    LinkedNode<K,V> entry = null;
	    try {
		entry = nextEntry.get();
	    } catch (ObjectNotFoundException onfe) {
		// This will only happen if this iterator is not sharing state
		// with the backing map and while serialized, the next entry
		// that the iterator was to return was removed from the map.
		throw new ConcurrentModificationException(
		    "next entry was removed from the map: " + nextEntry);
	    }	    

	    // update the iterator state
	    currentRemoved = false;
	    curEntry = nextEntry;
	    nextEntry = entry.nextEntry;
	    AppContext.getDataManager().markForUpdate(this);

	    // save the next entry that we're going to return in case
	    // we're serialized after this call
	    updatePersistentNextEntry();

	    return entry.toEntry();
	}

	/**
	 * Removes this iterator from the registry of active iterators.
	 */
	public void removingObject() {
	    // check if we have any persisted shared state with the backing map
	    // and if so, remove it
	    if (isConcurrentIterator()) {
		Map<BigInteger,ManagedReference<LinkedNode<K,V>>>
		    iteratorToNextEntry = 
		    serializedIteratorsNextElementsRef.getForUpdate().get();

		iteratorToNextEntry.remove(iteratorId);
	    }
	}

	/**
	 * If this instance has shared state with the backing map, saves the
	 * {@code ManagedReference} of the next entry that this iterator is
	 * going to return to a peristant state.  This enables the iterator to
	 * receive updates from the map while serialized if the next element
	 * that it should return was removed.
	 *
	 * @see ScalableLinkedHashMap#checkIterators(LinkedNode)
	 */
	private void updatePersistentNextEntry() {
	    if (isConcurrentIterator()) {
		Map<BigInteger,ManagedReference<LinkedNode<K,V>>>
		    iteratorToNextEntry = 
		    serializedIteratorsNextElementsRef.getForUpdate().get();
		
		iteratorToNextEntry.put(iteratorId, nextEntry);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void remove() {
	    if (currentRemoved) {
		throw new IllegalStateException(
		    "The current element has already been removed");
	    } else if (curEntry == null) {
		throw new IllegalStateException("No current element");
	    }
	    try {
		backingMapRef.get().removeNode(curEntry.get());
	    } catch (ObjectNotFoundException onfe) {
		// this happens if the current entry was removed while
		// this iterator was serialized.  We could check for
		// this upon deserialization, but instead we rely on
		// this lazy check at call-time here to avoid doing
		// any unnecessary work.
	    }
	    currentRemoved = true;
	    AppContext.getDataManager().markForUpdate(this);
	}

	private void writeObject(ObjectOutputStream s)
	    throws IOException {
	    // write out all the non-transient state
	    s.defaultWriteObject();
	}

	/**
	 * Reconstructs the {@code OrderedIterator} from the provided stream and
	 * marks that this iterator should check that its next entry is still
	 * valid
	 *
	 * @see OrderedIterator#checkForNextEntryUpdates()
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException {
	    
	    // read in all the non-transient state
	    s.defaultReadObject();	
	    
	    // mark that the iterator should recheck what its next
	    // element is prior to returning any next entry.
	    recheckNextEntry = true;
	}
    }

    /**
     * An iterator over the entry set
     */
    public static final class EntryIterator<K,V>
	extends OrderedIterator<Entry<K,V>,K,V> {

	private static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	EntryIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
	}

	/**
	 * {@inheritDoc}
	 */
	public Entry<K,V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map.
     */
    private static final class KeyIterator<K,V>
	extends OrderedIterator<K,K,V>
    {
	private static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	KeyIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
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
    private static final class ValueIterator<K,V>
	extends OrderedIterator<V,K,V> {

	public static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	ValueIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
	}

	/**
	 * {@inheritDoc}
	 */
	public V next() {
	    return nextEntry().getValue();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all the
     * mappings contained in this map.  The returned {@code Set} is backed by
     * the map, so changes to the map will be reflected by this view.  Note
     * that the time complexity of the operations on this set will be the same
     * as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the set of all mappings contained in this map
     */
    public Set<Entry<K,V>> entrySet() {
	return new EntrySet<K,V>(this);
    }

    /**
     * An internal-view {@code Set} implementation for viewing all the entries
     * in this map.
     */
    private static final class EntrySet<K,V>
	extends AbstractSet<Entry<K,V>>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the prefix tree.
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> mapRef;

	/**
	 * A cached version of the root node for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> map;

	EntrySet(ScalableLinkedHashMap<K,V> map) {
	    this.map = map;
	     mapRef = AppContext.getDataManager().createReference(map);
	}

	private void checkCache() {
	    if (map == null) {
		map = mapRef.get();
	    }
	}

	public Iterator<Entry<K,V>> iterator() {
	    checkCache();
	    return new EntryIterator<K,V>(map);
	}

	public boolean isEmpty() {
	    checkCache();
	    return map.isEmpty();
	}

	public int size() {
	    checkCache();
	    return map.size();
	}

	public boolean contains(Object o) {
	    if (!(o instanceof Entry)) {
		return false;
	    }
	    checkCache();
	    Entry<K,V> e = uncheckedCast(o);
	    LinkedNode<K,V> ourEntry = map.getEntry(e.getKey());
	    return ourEntry != null && ourEntry.toEntry().equals(e);
	}

	public void clear() {
	    checkCache();
	    map.clear();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all the keys
     * contained in this map.  The returned {@code Set} is backed by the map, so
     * changes to the map will be reflected by this view.  Note that the time
     * complexity of the operations on this set will be the same as those on the
     * map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the set of all keys contained in this map
     */
    public Set<K> keySet() {
	return new KeySet<K,V>(this);
    }

    /**
     * An internal collections view class for viewing the keys in the map.
     */
    private static final class KeySet<K,V>
	extends AbstractSet<K>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the backing map
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> mapRef;

	/**
	 * A cached version of the map node for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> map;

	KeySet(ScalableLinkedHashMap<K,V> map) {
	    this.map = map;
	    mapRef = AppContext.getDataManager().createReference(map);
	}

	private void checkCache() {
	    if (map == null) {
		map = mapRef.get();
	    }
	}

	public Iterator<K> iterator() {
	    checkCache();
	    return new KeyIterator<K,V>(map);
	}

	public boolean isEmpty() {
	    checkCache();
	    return map.isEmpty();
	}

	public int size() {
	    checkCache();
	    return map.size();
	}

	public boolean contains(Object o) {
	    checkCache();
	    return map.containsKey(o);
	}

	public void clear() {
	    checkCache();
	    map.clear();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Collection} of all the
     * values contained in this map.  The returned {@code Collection} is backed
     * by the map, so changes to the map will be reflected by this view.  Note
     * that the time complexity of the operations on this set will be the same
     * as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the collection of all values contained in this map
     */
    public Collection<V> values() {
	return new Values<K,V>(this);
    }

    /**
     * An internal collections-view of all the values contained in this map.
     */
    private static final class Values<K,V>
	extends AbstractCollection<V>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the backing map
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> mapRef;

	/**
	 * A cached version of the map for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> map;

	Values(ScalableLinkedHashMap<K,V> map) {
	    this.map = map;
	    mapRef = AppContext.getDataManager().createReference(map);
	}

	private void checkCache() {
	    if (map == null) {
		map = mapRef.get();
	    }
	}

	public Iterator<V> iterator() {
	    checkCache();
	    return new ValueIterator<K,V>(map);
	}

	public boolean isEmpty() {
	    checkCache();
	    return map.isEmpty();
	}

	public int size() {
	    checkCache();
	    return map.size();
	}

	public boolean contains(Object o) {
	    checkCache();
	    return map.containsValue(o);
	}

	public void clear() {
	    checkCache();
	    map.clear();
	}
    }

    /**
     * A utility class for providing a value object for the {@code
     * LinkedNode} key when a mapping is stored into the backing map.
     */
    private static final class Marker implements Serializable {

	private static final long serialVersionUID = 1;

	/**
	 * The one instance of this class
	 */
	static final Marker MARKER = new Marker();

	/**
	 * Private constructor
	 */
	private Marker() { }

	/**
	 * Returns {@code true} if {@code o} is an instance of this
	 * class
	 */
	public boolean equals(Object o) {
	    return o instanceof Marker;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int hashCode() { return 1; }
    }

    /**
     * A helper taks that will remove the {@code ManagedObject} node
     * instances of the linked entry list after a {@code clear}
     * operation has been performed.
     *
     * @see ScalableLinkedHashMap#clear()
     */
    private static class AsynchronousClearTask<K,V> 
	implements ManagedObject, Serializable, Task {
	
	/**
	 * The maximum number of entries to remove in a single run of
	 * tasks that asynchronously remove nodes and entries.
	 */
	private static final int MAX_REMOVE_ENTRIES = 50;

	/**
	 * A reference to the node that should be next removed
	 */
	private ManagedReference<LinkedNode<K,V>> curNode;
	
	/**
	 * Constructs the task with the first node in the list of
	 * entries to be removed
	 */
	public AsynchronousClearTask(ManagedReference<LinkedNode<K,V>> 
				     firstNodeInList) {
	    this.curNode = firstNodeInList;
	}

	/**
	 * Clears a finite number of entries and re-enqueues this task
	 * if more entries remain.
	 */
	public void run() {
	    int removed = 0;
	    while (removed < MAX_REMOVE_ENTRIES && curNode != null) {
		// remove the current node
		LinkedNode node = curNode.get();
		AppContext.getDataManager().removeObject(node);
		curNode = uncheckedCast(node.nextEntry);
		removed++;
	    }
	    
	    // if there are still have more nodes to clean up, then
	    // re-enqueue this task
	    if (curNode != null) {
		// mark that the task has updated its state
		AppContext.getDataManager().markForUpdate(this);
		AppContext.getTaskManager().scheduleTask(this);
	    }
	    // otherwise, this has has finished, so remove it from the
	    // data store
	    else {
		AppContext.getDataManager().removeObject(this);
	    }
	}
    }       

    /**
     * Saves the state of this {@code ScalableLinkedHashMap} instance
     * to the provided stream.
     */
    private void writeObject(ObjectOutputStream s)
	throws IOException {
	// write out all the non-transient state
	s.defaultWriteObject();

    }

    /**
     * Reconstructs the {@code ScalableLinkedHashMap} from the
     * provided stream.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException {

	// read in all the non-transient state
	s.defaultReadObject();	

    }
}
