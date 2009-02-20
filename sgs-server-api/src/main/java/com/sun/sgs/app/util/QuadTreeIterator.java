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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An interface which defines some common functionality for all
 * <code>QuadTree</code> iterators, used to iterate through
 * elements of a <code>QuadTree</code>.
 *
 * <p>
 * When the iterator is first initialized, the first element can be obtained
 * by calling {@code next()} or, {@link #nextNoReturn nextNoReturn()} and then
 * {@link #current current()} immediately afterwards. Either {@code next()} or
 * {@code nextNoReturn()} should be called first before {@link #currentX currentX()},
 * {@link #currentY currentY()} or {@code current()} can be called, otherwise an
 * {@link IllegalStateException} will be thrown. An
 * {@code IllegalStateException} is also thrown in the case when a call
 * to {@code current()}, {@code currentX()} or {@code currentY()} is made
 * after the element has been removed due to a call to {@code remove()}.
 *
 * @param <E> the element stored in the quadtree's leaves and returned
 * by this iterator
 * @see Iterator
 * @see QuadTree
 */
public interface QuadTreeIterator<E> extends Iterator<E> {

    /**
     * Returns the x-coordinate of the element currently pointed
     * to by the iterator.
     * @return the x-coordinate of the element currently pointed
     * to by the iterator
     * @throws IllegalStateException if neither the <tt>next()</tt> nor
     * <tt>nextNoReturn()</tt> method has yet been called or if the
     * current element has been removed
     */
    double currentX();

    /**
     * Returns the y-coordinate of the element currently pointed
     * to by the iterator.
     * @return the y-coordinate of the element currently pointed
     * to by the iterator
     * @throws IllegalStateException if neither the <tt>next()</tt> nor
     * <tt>nextNoReturn()</tt> method has yet been called or if the
     * current element has been removed
     */
    double currentY();

    /**
     * Advances the iterator without returning the next element in the
     * sequence. If the element is missing, the iterator does not remove it 
     * from the tree and it can still be removed through a call to the
     * <tt>remove()</tt> method
     * @throws NoSuchElementException iteration has no more elements.
     */
    void nextNoReturn();

    /**
     * Returns the element the cursor is pointing to. This should be used
     * in conjunction with {@code nextNoReturn()} in order
     * to retrieve the element. If this method is called
     * after {@code next()}, the same element will be returned. Multiple
     * calls to this method will return the same element.
     *
     * @return the element that the cursor is pointing to
     * @throws IllegalStateException if neither the <tt>next()</tt> nor
     * <tt>nextNoReturn()</tt> method has yet been called or if the
     * current element has been removed
     */
    E current();
}
