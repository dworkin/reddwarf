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

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

/**
 * An implementation of {@code BindingKeyedSet} backed by a
 * {@link BindingKeyedMap}.
 *
 * @param	<E> the element type
 */
public class BindingKeyedSetImpl<E>
    extends AbstractSet<E>
    implements BindingKeyedSet<E>, Serializable
{

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The reference to the backing map for this set.
     *
     * @serial
     */
    private final BindingKeyedMapImpl<E> map;

    /**
     * Creates an empty set with the specified {@code keyPrefix}.
     *
     * @param keyPrefix the key prefix for a service binding name
     * @throws	IllegalArgumentException if {@code keyPrefix}is empty
     */
    BindingKeyedSetImpl(String keyPrefix) {
	map = new BindingKeyedMapImpl<E>(keyPrefix);
    }

    /* -- Implement BindingKeyedSet -- */

    /** {@inheritDoc} */
    public String getKeyPrefix() {
	return map.getKeyPrefix();
    }
    
    /* -- Implement AbstractSet overrides -- */
    
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
    @Override
    public boolean add(E e) {
	return map.put(e.toString(), e) == null;
    }

    /**
     * Removes all the elements in this set.
     */
    @Override
    public void clear() {
	map.clear();
    }

    /**
     * Returns {@code true} if this set contains the specified element.
     *
     * @param o the element whose presence in the set is to be tested
     *
     * @return {@code true} if this set contains the specified element
     */
    @Override
    public boolean contains(Object o) {
	return map.containsKey(o.toString());
    }

    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements
     */
    @Override
    public boolean isEmpty() {
	return map.isEmpty();
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Iterator} over the
     * elements in this set.
     *
     * @return an iterator over the elements in this set
     */
    public Iterator<E> iterator() {
	return map.values().iterator();
    }

    /**
     * Removes the specified element from this set if it was present.
     *
     * @param o the element that should be removed from the set, if present
     *
     * @return {@code true} if the element was initially present in this set
     */
    @Override
    public boolean remove(Object o) {
	Object removed = map.remove(o.toString());
	return removed != null &&  o.equals(removed);
    }

    /**
     * Returns the number of elements in this set.
     *
     * @return the number of elements in this set
     */
    public int size() {
	return map.size();
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
}
