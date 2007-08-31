/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.Serializable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;


/**
 * A concurrent, scalable {@code Set} implementation backed by a
 * {@link DistributedHashMap}.  This class is intended as a drop-in
 * replacement for {@code java.util.HashSet}.  This class is designed
 * to mark itself for update as necessary; no additional calls to the
 * {@code DataManager} are necessary when using the map.  Beware that
 * developers should <i>not</i> call {@code markForUpdate} or {@code
 * getForUpdate} on an instance, as this will eliminate all the
 * concurrency benefits of this class.
 *
 * <p>
 *
 * This class offers constant time operations for {@code add}, {@code
 * remove}, {@code contains}, and {@code isEmpty}.  Unlike {@code
 * HashSet}, the {@code size} and {@code clear} operations are not
 * constant time; these two operations reqiuire a travesal of all the
 * elements in the set.  Like {@code HashSet}, this class permits the
 * {@code null} element.
 *
 * <p>
 *
 * This implementation supports the contract that all elements must be
 * {@link Serializable}.  If a developer provides a {@code
 * Serializable} element that is <i>not</i> a {@link ManagedObject},
 * this implementation will take responsibility for the lifetime of
 * that object in the datastore.  The developer will be responsible
 * for the lifetime of all {@code ManagedObject} elements stored in
 * this set.
 *
 * <p>
 *
 * The {@code Iterator} returned by this class implements {@code
 * Serializable} and may be used concurrently.  Due to the
 * asynchronous nature of this class, the iterator reflects the
 * current state of the set at the time of its creation, or if
 * persisted, its deserialization.
 *
 * <p>
 *
 * An instance of {@code DistributedHashSet} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  This
 * parameter acts as a hint to the map on how to perform resizing.  As
 * the map grows, the number of supported parallel operations will
 * also grow beyond the specified minimum; specifying a minimum
 * concurrency ensures that the map will resize correctly.  Since the
 * distribution of objects in the map is essentially random, the
 * actual concurrency will vary.  Setting the minimum concurrency too
 * high will waste space and time, while setting it too low will cause
 * conflicts until the map grows sufficiently to support more
 * concurrent operations.
 *
 * @version 1.1
 *
 * @see Object#hashCode()
 * @see Set
 * @see java.util.HashSet
 * @see DistributedHashMap
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class DistributedHashSet<E>
    extends AbstractSet<E>
    implements Set<E>, Serializable, ManagedObject {

    private static final long serialVersionUID = 1230892300L;

    /**
     * The internal marker for whether an object is present in the set
     */
    private static final Marker PRESENT = new Marker();

    /**
     * The reference to the backing map for this set.
     */
    private final ManagedReference map;

    /**
     * Creates a new empty set; the backing {@code DistributedHashMap}
     * has the default minimum concurrency.
     *
     * @see DistributedHashMap#DistributedHashMap()
     */
    public DistributedHashSet() {
	map = AppContext.getDataManager().
	    createReference(new DistributedHashMap<E,Marker>());	
    }

    /**
     * Creates a new empty set; the backing {@code DistributedHashMap}
     * has the specified minimum concurrency.
     * 
     * @param minConcurrency the minimum number of write operations to
     *        support in parallel
     *
     * @throws IllegalArgumentException if minConcurrency is
     *         non-positive
     *
     * @see DistributedHashMap#DistributedHashMap(int)
     */
    public DistributedHashSet(int minConcurrency) {
	map = AppContext.getDataManager().
	    createReference(new DistributedHashMap<E,Marker>(minConcurrency));
	
    }
    
    /**
     * Adds the specified element to this set if was not already
     * present.
     *
     * @param e the element to be added
     * @return {@code true} if the set did not already contain the
     *         specified element
     */
    public boolean add(E e) {
	return map.get(DistributedHashMap.class).put(e, PRESENT) == null;
    }

    /**
     * Removes all the elements in this set.  Note that unlike {@code
     * HashSet}, this operation is <i>not</i> constant time.  Clearing
     * a set takes {@code n log(n)} time.
     */ 
    public void clear() {
	map.get(DistributedHashMap.class).clear();
    }


    /**
     * Returns {@code true} if this set contains the specified
     * element.
     *
     * @return {@code true} if this set contains the specified
     *         element.
     */
    public boolean contains(Object o) {
	return map.get(DistributedHashMap.class).containsKey(o);
    }


    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements.
     */
    public boolean isEmpty() {
	return map.get(DistributedHashMap.class).isEmpty();
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Iterator}
     * over the elements in this set.
     *
     * @return an iterator over the elements in this set
     */
    public Iterator<E> iterator() {
	return map.get(DistributedHashMap.class).keySet().iterator();
    }
   

    /**
     * Removes the specified element from this set if it was present.
     *
     * @return {@code true} if the element was initially present in
     *         this set
     */
    public boolean remove(Object o) {
	return map.get(DistributedHashMap.class).remove(o) == PRESENT;
    }

    /**
     * Returns the number of elements in this set.  Note that unlike
     * {@code HashMap}, this is <i>not<i> a constant time operation.
     * Determining the size of a set takes {@code n log(n)}.
     *
     * @return the number of elements in thus set
     */
    public int size() {
	return map.get(DistributedHashMap.class).size();
    }

    /**
     * An internal marker class for storing as the value in the
     * backing map.  All marker objects are equivalent.
     */
    private static class Marker implements Serializable { 
	private static final long serialVersionUID = 3;
	
	public int hashCode() {
	    return 0;
	}

	public boolean equals(Object o) {
	    return o instanceof Marker;
	}
    }   
}