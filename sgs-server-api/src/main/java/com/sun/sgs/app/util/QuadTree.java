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

import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.util.ConcurrentQuadTree.BoundingBox;
import com.sun.sgs.app.util.ConcurrentQuadTree.ElementIterator;
import com.sun.sgs.app.util.ConcurrentQuadTree.Point;

public interface QuadTree<T> extends ManagedObjectRemoval {

    /**
     * Adds the element to the quadtree given the coordinate values.
     * 
     * @param x the x-coordinate of the element
     * @param y the y-coordinate of the element
     * @param element the element to store
     * @return {@code true} if the element was successfully added and
     * {@code false} otherwise
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    boolean put(double x, double y, T element);
    
    /**
     * Returns an iterator for the elements which are contained within the the
     * bounding box created by the two given coordinates. The elements which can
     * be iterated are in no particular order, and there may not be any
     * elements to iterate over (empty iterator).
     * 
     * @param x1 the x-coordinate of the first corner
     * @param y1 the y-coordinate of the first corner
     * @param x2 the x-coordinate of the second corner
     * @param y2 the y-coordinate of the second corner
     * @return an iterator which can traverse over the entries within the
     * coordinates representing the bounding box
     */
    QuadTreeIterator<T> boundingBoxIterator(double x1, double y1, double x2,
	    double y2);
    
    
    /**
     * Returns an iterator for the elements which exist at the point
     * defined by the {@code x} and {@code y} parameters. The elements which
     * can be iterated are in no particular order, and there may not be any
     * elements to iterate over (empty iterator).
     * 
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return an iterator which can traverse over the entries that exist
     * at ({@code x}, {@code y})
     */
    QuadTreeIterator<T> pointIterator(double x, double y);
    
    /**
     * Asynchronously clears the tree and replaces it with an empty
     * implementation.
     */
    void clear();
    
    /**
     * Returns {@code true} if there is an element at the given coordinates,
     * or {@code false} otherwise.
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return {@code true} if there is an element at the given coordinates,
     * or {@code false} otherwise
     */
    boolean contains(double x, double y);
    
    
    /**
     * Returns an array of four coordinates which represent the individual x
     * and y- coordinates specifying the bounding box of the quadtree.
     * In order to properly extract the values from the array, it is advised
     * to use the integer fields within the {@code Coordinate} enumeration.
     * For example, to obtain the smallest x-coordinate, the call should be:
     * <p>
     * {@code getDirectionalBoundingBox()[MIN_X_INT]}
     *  
     * 
     * @param direction the direction of interest for the bounding box
     * @return a double value representing the bounding box border for the
     * given direction, or {@code NaN} if the direction is invalid
     */
    double[] getDirectionalBoundingBox();
    
    /**
     * Determines if the tree is empty.
     * 
     * @return {@code true} if the tree is empty, and {@code false} otherwise
     */
    boolean isEmpty();
    
    /**
     * Returns an iterator over the elements contained in the
     * {@code backingMap}. The {@code backingMap} corresponds to all the
     * elements in the tree.
     * 
     * @return an {@code Iterator} over all the elements in the map
     */
    QuadTreeIterator<T> iterator();
    
    /**
     * Removes all elements from the quadtree corresponding to the provided
     * coordinate.
     * 
     * @param x the x-coordinate of the element to remove
     * @param y the y-coordinate of the element to remove
     * @return {@code true} if there was at least one element removed, and
     * {@code false} otherwise
     */
    boolean removeAll(double x, double y);
    
}
