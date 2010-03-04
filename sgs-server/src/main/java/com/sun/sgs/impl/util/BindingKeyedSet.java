/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of {@link Set} backed by a {@link BindingKeyedMap}.
 * Elements stored in this map must implement {@link Serializable}, and may
 * (but are not required to) implement {@link ManagedObject}.  The elements
 * stored in this map must have a unique string representation (returned by
 * the {@link Object#toString toString} method), which is used as the
 * element's key in the backing map.
 *
 * @param	<E> the element type
 */
public interface BindingKeyedSet<E>
    extends Set<E>
{
    /**
     * Returns the key prefix for this set.
     *
     * @return	the key prefix for this set
     */
    String getKeyPrefix();

    /**
     * Adds the specified element to this set if it was not already present.
     *
     * @param e the element to be added
     *
     * @return {@code true} if the set did not already contain the specified
     *         element
     *
     * @throws	IllegalArgumentException if the argument does not
     *		implement {@code Serializable}
     * @throws	ObjectNotFoundException if {@code value} is a managed
     *		object that has been removed from the {@code DataManager}
     */
    boolean add(E e);
    
    /**
     * Returns {@code true} if this set contains the specified element.
     *
     * @param o the element whose presence in the set is to be tested
     *
     * @return	{@code true} if this set contains the specified element, and
     *		{@code false} otherwise
     * @throws	ObjectNotFoundException if the specified object is a
     *		managed object that has been removed from the
     *		{@link DataManager}, or is equal to an element in the
     *		collection that has been removed from the {@link DataManager}
     */
    boolean contains(Object o);

    /**
     * Returns a {@code Serializable} {@code Iterator} over the
     * elements in this set.  The {@link Iterator#next next} method of the
     * returned {@code Iterator} throws {@code ObjectNotFoundException} if
     * the next element in the set is a managed object that has been
     * removed from the {@code DataManager}.
     *
     * @return an iterator over the elements in this set
     */
    Iterator<E> iterator();
    
    /**
     * Removes the specified element from this set if it was present.
     *
     * @param o the element that should be removed from the set, if present
     *
     * @return {@code true} if the element was initially present in this set
     * @throws	ObjectNotFoundException if the specified object is a
     *		managed object that has been removed from the
     *		{@link DataManager}, or is equal to an element in the
     *		collection that has been removed from the {@link DataManager}
     */
    boolean remove(Object o);
}
