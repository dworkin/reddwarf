/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

/**
 * A scalable implementation of {@code Set} backed by a {@link
 * ScalableHashMap}.  The internal structure of the set is separated into
 * distributed pieces, which reduces the amount of data any one operation needs
 * to access.  The distributed structure increases the concurrency and allows
 * for parallel write operations to successfully complete.
 *
 * <p>
 *
 * Developers may use this class as a drop-in replacement for the {@link
 * java.util.HashSet} class for use in a {@link ManagedObject}.  A {@code
 * HashSet} will typically perform better than this class when the number of
 * mappings is small, the objects being stored are small, and minimal
 * concurrency is required.  As the size of the serialized {@code HashSet}
 * increases, this class will perform significantly better.  Developers are
 * encouraged to profile the serialized size of their set to determine which
 * implementation will perform better.  Note that {@code HashSet} does not
 * provide any concurrency for {@code Task}s running in parallel that attempt
 * to modify the set at the same time, so this class may perform better in
 * situations where multiple tasks need to modify the set concurrently, even if
 * the total number of elements is small.  Also note that, unlike {@code
 * HashSet}, this class can be used to store {@code ManagedObject} instances
 * directly.
 *
 * <p>
 *
 * This implementation requires that all non-{@code null} elements implement
 * {@link Serializable}.  Attempting to add elements to the set that do not
 * implement {@code Serializable} will result in an {@link
 * IllegalArgumentException} being thrown.  If an element is an instance of
 * {@code Serializable} but does not implement {@code ManagedObject}, this
 * class will persist the element as necessary; when such an element is removed
 * from the set, it is also removed from the {@code DataManager}.  If an
 * element is an instance of {@code ManagedObject}, the developer will be
 * responsible for removing these objects from the {@code DataManager} when
 * done with them.  Developers should not remove these object from the {@code
 * DataManager} prior to removing them from the set.
 *
 * <p>
 *
 * Applications must make sure that objects used as elements in sets of this
 * class have {@code equals} and {@code hashCode} methods that return the same
 * values after the elements have been serialized and deserialized.  In
 * particular, elements that use {@link Object#equals Object.equals} and {@link
 * Object#hashCode Object.hashCode} will typically not be equal, and will have
 * different hash codes, each time they are deserialized, and so are probably
 * not suitable for use with this class.
 *
 * <p>
 *
 * This class marks itself for update as necessary; no additional calls to the
 * {@link DataManager} are necessary when modifying the map.  Developers do not
 * need to call {@code markForUpdate} or {@code getForUpdate} on this set, as
 * this will eliminate all the concurrency benefits of this class.  However,
 * calling {@code getForUpdate} or {@code markForUpdate} can be used if a
 * operation needs to prevent all access to the set.
 *
 * <p>
 *
 * This class offers constant-time implementations of the {@code add}, {@code
 * remove} and {@code contains} methods.  Note that, unlike most collections,
 * the {@code size} and {@code isEmpty} methods for this class are <u>not</u>
 * constant-time operations.  Because of the asynchronous nature of the set,
 * these operations may require accessing all of the entries in the set.
 *
 * <p>
 *
 * An instance of {@code ScalableHashSet} offers one parameter for performance
 * tuning: {@code minConcurrency}, which specifies the minimum number of write
 * operations to support in parallel.  This parameter acts as a hint to the
 * backing map on how to perform resizing.  As the set grows, the number of
 * supported parallel operations will also grow beyond the specified minimum.
 * Setting the minimum concurrency too high will waste space and time, while
 * setting it too low will cause conflicts until the map grows sufficiently to
 * support more concurrent operations.
 *
 * <p>
 *
 * Since the expected distribution of objects in the set is essentially random,
 * the actual concurrency will vary.  Developers are strongly encouraged to use
 * hash codes that provide a normal distribution; a large number of collisions
 * will likely reduce the performance.
 *
 * <p>
 *
 * <a name="iterator"></a> The {@code Iterator} for this class implements
 * {@code Serializable}.  A single iterator may be saved by different {@code
 * ManagedObject} instances, which will create distinct copies of the original
 * iterator.  A copy starts its iteration from where the state of the original
 * was at the time of the copy.  However, each copy maintains a separate,
 * independent state from the original and will therefore not reflect any
 * changes to the original iterator.  To share a single {@code Iterator}
 * between multiple {@code ManagedObject} <i>and</i> have the iterator use a
 * consistent view for each, the iterator should be contained within a shared
 * {@code ManagedObject}.
 *
 * <p>
 *
 * The iterator do not throw {@link java.util.ConcurrentModificationException}.
 * The iterator for this class is stable with respect to the concurrent changes
 * to the associated collection, but may ignore additions and removals made to
 * the set during iteration.
 *
 * <p>
 *
 * If a call to the {@link Iterator#next next} method on the iterator causes a
 * {@link ObjectNotFoundException} to be thrown because the return value has
 * been removed from the {@code DataManager}, the iterator will still have
 * successfully moved to the next entry in its iteration.  In this case, the
 * {@link Iterator#remove remove} method may be called on the iterator to
 * remove the current object even though that object could not be returned.
 *
 * <p>
 *
 * This class and its iterator implement all optional operations and support
 * {@code null} elements.  This set provides no guarantees on the order of
 * elements when iterating.
 *
 * @param <E> the type of elements maintained by this set
 *
 * @see Object#hashCode Object.hashCode
 * @see java.util.Set
 * @see java.util.HashSet
 * @see ScalableHashMap
 * @see Serializable
 * @see ManagedObject
 */
public class ScalableHashSet<E>
    extends AbstractSet<E>
    implements Serializable, ManagedObjectRemoval {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The internal marker for whether an object is present in the set.
     */
    private static final Marker PRESENT = new Marker();

    /**
     * The minor version number, which can be modified to note a compatible
     * change to the data structure.  Incompatible changes should be marked by
     * a change to the serialVersionUID.
     *
     * @serial
     */
    private final short minorVersion = 1;

    /**
     * The reference to the backing map for this set.
     *
     * @serial
     */
    private final ManagedReference<ScalableHashMap<E, Marker>> map;

    /**
     * Creates an empty set; the backing {@code ScalableHashMap} has the
     * default minimum concurrency ({@code 32}).
     *
     * @see ScalableHashMap#ScalableHashMap()
     */
    public ScalableHashSet() {
	map = AppContext.getDataManager().
                createReference(new ScalableHashMap<E, Marker>());
    }

    /**
     * Creates an empty set; the backing {@code ScalableHashMap} has the
     * specified minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write operations
     *	      to support
     *
     * @throws IllegalArgumentException if {@code minConcurrency} is
     *	       not greater than zero
     *
     * @see ScalableHashMap#ScalableHashMap(int)
     */
    public ScalableHashSet(int minConcurrency) {
	map = AppContext.getDataManager().
                createReference(new ScalableHashMap<E, Marker>(minConcurrency));
    }

    /**
     * Creates a new set containing the elements in the specified collection.
     * The new set will have the default minimum concurrency ({@code 32}).
     *
     * @param c the collection of elements to be added to the set
     *
     * @throws IllegalArgumentException if any elements contained in the
     *	       argument are not {@code null} and do not implement {@code
     *	       Serializable}
     */
    public ScalableHashSet(Collection<? extends E> c) {
	this();
	addAll(c);
    }

    /**
     * Adds the specified element to this set if it was not already present.
     *
     * @param e the element to be added
     *
     * @return {@code true} if the set did not already contain the specified
     *         element
     *
     * @throws IllegalArgumentException if the argument is not {@code null} and
     *	       does not implement {@code Serializable}
     */
    public boolean add(E e) {
	return map.get().put(e, PRESENT) == null;
    }

    /**
     * Removes all the elements in this set.  Note that unlike {@code HashSet},
     * this operation is <i>not</i> constant time.  Clearing a set takes {@code
     * O(n*log(n))} time.
     */
    public void clear() {
	map.get().clear();
    }

    /**
     * Returns {@code true} if this set contains the specified element.
     *
     * @param o the element whose presence in the set is to be tested
     *
     * @return {@code true} if this set contains the specified element
     */
    public boolean contains(Object o) {
	return map.get().containsKey(o);
    }

    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements
     */
    public boolean isEmpty() {
	return map.get().isEmpty();
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Iterator} over the
     * elements in this set.
     *
     * @return an iterator over the elements in this set
     */
    public Iterator<E> iterator() {
	return map.get().keySet().iterator();
    }

    /**
     * Removes the specified element from this set if it was present.
     *
     * @param o the element that should be removed from the set, if present
     *
     * @return {@code true} if the element was initially present in this set
     */
    public boolean remove(Object o) {
	return PRESENT.equals(map.get().remove(o));
    }

    /**
     * Returns the number of elements in this set.  Note that unlike {@code
     * HashMap}, this is <i>not</i> a constant time operation.  Determining the
     * size of a set takes {@code O(n*log(n))}.
     *
     * @return the number of elements in this set
     */
    public int size() {
	return map.get().size();
    }

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection.  In other words, removes from this collection all
     * of its elements that are not contained in the specified collection. <p>
     *
     * This implementation iterates over this collection, checking each element
     * returned by the iterator in turn to see if it's contained in the
     * specified collection.  If it's not so contained, it's removed from this
     * collection with the iterator's {@code remove} method.
     *
     * @param c elements to be retained in this collection.
     *
     * @return {@code true} if this collection changed as a result of the
     *         call
     * @throws NullPointerException if the specified collection is {@code null}
     *
     * @see #remove
     * @see #contains
     */
    @Override
    public boolean retainAll(Collection<?> c) {
	/*
	 * The AbstractCollection method doesn't currently do this check.
	 * -tjb@sun.com (10/10/2007)
	 */
	if (c == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	return super.retainAll(c);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation removes the underlying {@code ScalableHashMap}.
     */
    public void removingObject() {
	AppContext.getDataManager().removeObject(
	    map.get());
    }

    /**
     * An internal marker class for storing as the value in the backing map.
     * All marker objects are equivalent to ensure equality after
     * deserialization without the added cost of write replacement.
     */
    private static final class Marker implements Serializable {
	private static final long serialVersionUID = 1;

	public int hashCode() {
	    return 0;
	}

	public boolean equals(Object o) {
	    return o != null && o instanceof Marker;
	}
    }
}
