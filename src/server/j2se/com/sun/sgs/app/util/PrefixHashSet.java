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
 * {@link PrefixHashMap}.  This class is intended as a drop-in
 * replacement for {@code java.util.HashSet}.
 *
 * <p>
 *
 * This class offers constant time operations for {@code add}, {@code
 * remove}, {@code contains}, and {@code isEmpty}.  Unlike {@code
 * HashSet}, the {@code size} and {@code clear} operations are not
 * constant time; these two operations take {@code n log(n)} in this
 * implementation.  Like {@code HashSet}, this class permits the
 * {@code null} element.
 *
 * <p>
 *
 * This implementation supports the contract that all elements must be
 * {@link Serializable}.  If a developer provides a {@code
 * Serializable} element that is <i>not</i> a {@code ManagedObject},
 * this implementation will take responsibility for the lifetime of
 * that object in the datastore.  The developer will be responsible
 * for the lifetime of all {@link ManagedObject} stored in this set.
 *
 * <p>
 *
 * An instance of {@code PrefixHashSet} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  As the
 * set grows, the number of supported parallel operations will also
 * grow beyond the specified minimum, but this factor ensures that it
 * will never drop below the provided number.  Setting this value too
 * high will waste space and time, while setting it too low will cause
 * conflicts until the set grows sufficiently to support more
 * concurrent operations.  Furthermore, the efficacy of the
 * concurrency depends on the distribution of hash values; elements with
 * poor hashing will minimize the actual number of possible concurrent
 * writes, regardless of the {@code minConcurrency} value.
 *
 * @version 1.0
 *
 * @see Object#hashCode()
 * @see Set
 * @see java.util.HashSet
 * @see PrefixHashMap
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class PrefixHashSet<E>
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
     * Creates a new empty set; the backing {@code PrefixHashMap} has
     * the default minimum concurrency.
     *
     * @see PrefixHashMap#PrefixHashMap()
     */
    public PrefixHashSet() {
	map = AppContext.getDataManager().
	    createReference(new PrefixHashMap<E,Marker>());	
    }

    /**
     * Creates a new empty set; the backing {@code PrefixHashMap} has
     * the specified minimum concurrency.
     * 
     * @param minConcurrency the minimum number of write operations to
     *        support in parallel
     *
     * @throws IllegalArgumentException if minConcurrency is
     *         non-positive
     *
     * @see PrefixHashMap#PrefixHashMap(int)
     */
    public PrefixHashSet(int minConcurrency) {
	map = AppContext.getDataManager().
	    createReference(new PrefixHashMap<E,Marker>(minConcurrency));
	
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
	return map.get(PrefixHashMap.class).put(e, PRESENT) == null;
    }

    /**
     * Removes all the elements in this set.  Note that unlike {@code
     * HashSet}, this operation is <i>not</i> constant time.  Clearing
     * a set takes {@code n log(n)} time.
     */ 
    public void clear() {
	map.get(PrefixHashMap.class).clear();
    }


    /**
     * Returns {@code true} if this set contains the specified
     * element.
     *
     * @return {@code true} if this set contains the specified
     *         element.
     */
    public boolean contains(Object o) {
	return map.get(PrefixHashMap.class).containsKey(o);
    }


    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements.
     */
    public boolean isEmpty() {
	return map.get(PrefixHashMap.class).isEmpty();
    }

    /**
     * Returns an iterator over the elements in this set
     *
     * @return an iterator over the elements in this set
     */
    public Iterator<E> iterator() {
	return map.get(PrefixHashMap.class).keySet().iterator();
    }
   

    /**
     * Removes the specified element from this set if it was present.
     *
     * @return {@code true} if the element was initially present in
     *         this set
     */
    public boolean remove(Object o) {
	return map.get(PrefixHashMap.class).remove(o) == PRESENT;
    }

    /**
     * Returns the number of elements in this set.  Note that unlike
     * {@code HashMap}, this is <i>not<i> a constant time operation.
     * Determining the size of a set takes {@code n log(n)}.
     *
     * @return the number of elements in thus set
     */
    public int size() {
	return map.get(PrefixHashMap.class).size();
    }

    /**
     * An internal marker class for storing as the value in the
     * backing map.
     */
    private static class Marker implements Serializable { 
	private static final long serialVersionUID = 3;
    }
    
}