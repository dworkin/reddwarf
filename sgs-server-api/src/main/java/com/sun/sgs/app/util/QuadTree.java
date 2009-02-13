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


/**
 * An interface which defines some common functionality for all Quadtrees. A
 * <code>Quadtree</code> is a data structure where each node has at most
 * four children.
 *
 * <p>
 * Classes implementing this interface needs access to a class which
 * implements the {@link QuadTreeIterator} to accomodate the return value of
 * {@link boundingBoxIterator()} and {@link pointIterator()} methods. Note, the
 * behaviour of {@link pointIterator()} can be replicated using
 * {@link boundingBoxIterator()} by specifying a bounding box
 * where x1==x2 and y1==y2.
 *
 * <p>
 * Classes implementing <code>QuadTree</code> also need to keep track of a
 * region known as a boundingBox which can be defined in terms of
 * <code>double</code> cartesian coordinate values.
 *
 * @param <E> the element stored in the Quadtree's leaves
 * @see QuadTreeIterator
 */


public interface QuadTree<E> {

    /**
     * Adds the element to the quadtree given the coordinate values.
     * 
     * @param x the x-coordinate of the element
     * @param y the y-coordinate of the element
     * @param element the element to store
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    void put(double x, double y, E element);
    
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
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    QuadTreeIterator<E> boundingBoxIterator(double x1, double y1, double x2,
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
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     * at ({@code x}, {@code y})
     */
    QuadTreeIterator<E> pointIterator(double x, double y);
    
    /**
     * Removes all elements from the tree.
     */
    void clear();
    
    /**
     * Returns {@code true} if there is an element at the given coordinates,
     * or {@code false} otherwise.
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return {@code true} if there is an element at the given coordinates,
     * or {@code false} otherwise
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    boolean contains(double x, double y);
    
    
    /**
     * Returns an array of four coordinates which represent the individual x-
     * and y- coordinates specifying the bounding box of the quadtree.
     * In order to properly extract the values from the array, it is advised
     * to use the static constant integer fields within {@code 
     * ConcurrentQuadTree}.
     * For example, to obtain the smallest x-coordinate, the call should be:
     * <p>
     * {@code getBoundingBox()[ConcurrentQuadTree.MIN_X_INT]}
     *  
     * @return a double array representing the four coordinates
     * of the bounding box's four boundary values
     */
    double[] getBoundingBox();
    
    /**
     * Determines if the tree is empty.
     * 
     * @return {@code true} if the tree is empty, and {@code false} otherwise
     */
    boolean isEmpty();
    
    /**
     * Returns an iterator for all the elements contained in the tree. The
     * elements which can be iterated are in no particular order, and there may
     * not be any elements to iterate over (empty iterator).
     * @return an {@code Iterator} over all the elements in the map
     */
    QuadTreeIterator<E> iterator();
    
    /**
     * Removes all elements from the quadtree corresponding to the provided
     * coordinate.
     * 
     * @param x the x-coordinate of the element to remove
     * @param y the y-coordinate of the element to remove
     * @return {@code true} if there was at least one element removed, and
     * {@code false} otherwise
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    boolean removeAll(double x, double y);
    
}
