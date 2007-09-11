/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import java.io.Serializable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;


/**
 * A concurrent, distributed implementation of {@code Set} backed by a
 * {@link DistributedHashSet}.  The internal structure of the set is
 * separated into distributed pieces, which reduces the amount of data
 * any one operation needs to access.  The distributed structure
 * increases the concurrency and allows for parallel write operations
 * to successfully complete.
 * 
 * <p>
 *
 * Developers may use this class as a drop-in replacement for the
 * {@link java.util.HashSet} class for use in a {@link ManagedObject}.
 * A {@code HashSet} will typically perform better than this class
 * when the number of mappings is small and the objects being stored
 * are small, and when minimal concurrency is required.  As the size
 * of the serialized {@code HashSet} increases, this class will
 * perform significantly better.  Developers are encouraged to profile
 * the serialized size of their set to determine which implementation
 * will perform better.  Note that {@code HashSet} has no implicit
 * concurrency, so this class may perform better in situations where
 * multiple tasks need to modify the set concurrently, even if the
 * total number of mappings is small.  Also note that this class
 * should be used instead of other {@code Set} implementations to
 * store {@code ManagedObject} instances.
 *
 * <p>
 *
 * This class marks itself for update as necessary; no additional
 * calls to the {@link DataManager} are necessary when modifying the
 * map.  Developers do not need to call {@code markForUpdate} or
 * {@code getForUpdate} on this set, as this will eliminate all the
 * concurrency benefits of this class.  However, calling {@code
 * getForUpdate} or {@code markForUpdate} can be used if a operation
 * needs to prevent all access to the set.
 *
 * <p>
 *
 * This implementation requires that all elements must be {@link
 * Serializable}.  If an element is an instance of {@code
 * Serializable} but does not implement {@code ManagedObject}, this
 * class will persist the element as necessary; when such an element
 * is removed from the set, it is also removed from the {@code
 * DataManager}.  If an element is an instance of {@code
 * ManagedObject}, the developer will be responsible for removing
 * these objects from the {@code DataManager} when done with them.
 * Developers should not remove these object from the {@code
 * DataManger} prior to removing them from the set.
 *
 * <p>
 *
 * This class offers constant time operations for {@code add}, {@code
 * remove}, {@code contains}, and {@code isEmpty}.  Unlike {@code
 * HashSet}, the {@code size} and {@code clear} operations are not
 * constant time; these two operations reqiuire a traversal of all the
 * elements in the set.  Like {@code HashSet}, this class permits the
 * {@code null} element.
 *
 * <p>
 *
 * <a name="iterator"></a>
 * The {@code Iterator} for this class implements {@code
 * Serializable}.  An single iterator may be saved by a different
 * {@code ManagedObject} instances, which create a distinct copy of
 * the original iterator.  A copy starts its iteration from where the
 * state of the original was at the time of the copy.  However each
 * copy maintains a separate, independent state from the original will
 * therefore not reflect any changes to the original iterator.  These
 * iterators do not throw {@link
 * java.util.ConcurrentModificationException}.  The iterator for this
 * class is stable with respect to the concurrent changes to the
 * associated collection; an iterator will not the same object twice
 * after a change is made.  An iterator may ignore additions and
 * removals to the associated collection that occur before the
 * iteration site.  This set provides no guarantees on the order of
 * elements when iterating.
 *
 * <p>
 *
 * An instance of {@code DistributedHashSet} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  This
 * parameter acts as a hint to the backing qmap on how to perform
 * resizing.  As the set grows, the number of supported parallel
 * operations will also grow beyond the specified minimum.  Setting
 * the minimum concurrency too high will waste space and time, while
 * setting it too low will cause conflicts until the map grows
 * sufficiently to support more concurrent operations.
 *
 * <p>
 *
 * Since the expected distribution of objects in the set is
 * essentially random, the actual concurrency will vary.  Developers
 * are stronly encouraged to use hash codes that provide a normal
 * distribution; a large number of collisions will likely reduce the
 * performance.
 *
 * @see Object#hashCode()
 * @see java.util.Set
 * @see java.util.HashSet
 * @see DistributedHashMap
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class DistributedHashSet<E>
    extends AbstractSet<E>
    implements Serializable, ManagedObject {

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
     * has the default minimum concurrency (32).
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
     * Creates a new set containing the elements in the specified
     * collection.  The new set will have the default minimum
     * concurrency (32).
     *
     * @param c the collection of elements to be added to the set     
     */
    public DistributedHashSet(Collection<? extends E> c) {
	this();
	addAll(c);
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
     * {@inheritDoc}
     */
    public int hashCode() {
	return AppContext.getDataManager().
	    createReference(this).getId().intValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
	if (o instanceof DistributedHashSet) {
	    DataManager dm = AppContext.getDataManager();
	    return dm.createReference(this).
		equals(dm.createReference((DistributedHashSet)o));
	}
	return super.equals(o);
    }

    /**
     * Returns {@code true} if this set contains the specified
     * element.
     *
     * @param o the element whose presence in the set is to be tested
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
     * @param o the element that should be removed from the set, if
     *          present
     *
     * @return {@code true} if the element was initially present in
     *         this set
     */
    public boolean remove(Object o) {
	return map.get(DistributedHashMap.class).remove(o) == PRESENT;
    }

    /**
     * Returns the number of elements in this set.  Note that unlike
     * {@code HashMap}, this is <i>not</i> a constant time operation.
     * Determining the size of a set takes {@code n log(n)}.
     *
     * @return the number of elements in this set
     */
    public int size() {
	return map.get(DistributedHashMap.class).size();
    }

    /**
     * An internal marker class for storing as the value in the
     * backing map.  All marker objects are equivalent to ensure
     * equality across JVMs.
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