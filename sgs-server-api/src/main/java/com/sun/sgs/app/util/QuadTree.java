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
 * <code>Quadtree</code> is a data structure used to partition 2-Dimensional
 * space where each node has at most four children, dividing a region into
 * four sub-regions.
 *
 * <p>
 * Implementations need to use a "bounding box" to restrict elements to a
 * specific 2-Dimensional region to easily check if an element is within
 * a 2-Dimensional region and also to calculate how the bounding box region
 * should be split up to create new children representing sub-regions. The
 * bounding box is defined in terms of <code>double</code> cartesian
 * coordinate values. Here, an element is defined to "contained" within a
 * bounding box if its corresponding x and y coordinates are within the bounding
 * box's cartesian coordinate values, including if the element lies on the
 * edge of the bounding box. Since there may be many elements in the tree, 
 * defining a bounding box removes the need to walk through the tree to collect
 * and return all elements in an otherwise lengthy process.
 * There may be more than one element at a given location, therefore it is
 * infeasible to specify the characteristics of the object to be removed or
 * retrieved. Instead, individual removals can be achieved by using the
 * iterator's {@code remove()} method.
 *
 * <p>
 * In order to determine the number of elements stored at a given location, it
 * is necessary to create an iterator with a bounding box encompassing the
 * coordinate ({@link #boundingBoxIterator boundingBoxIterator(double, double,
 * double, double)}), or alternately, an iterator for the point itself
 * ({@link #pointIterator pointIterator(double, double)}). Note, the behaviour
 * of {@code pointIterator()} can be replicated using
 * {@code boundingBoxIterator()} by specifying a bounding box where
 * <code>x1==x2</code> and <code>y1==y2</code>.
 *
 * <p> Therefore, classes implementing this interface need access to a
 * class which implements the {@link QuadTreeIterator} to accommodate the
 * return value of {@code boundingBoxIterator()} and {@code pointIterator()}
 * methods.
 *
 *
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
     * Returns an iterator for all the elements contained in the tree. The
     * iterator returns elements in no particular order and it may be
     * emtpy.
     * @return an iterator over all the elements in the tree
     */
    QuadTreeIterator<E> iterator();

    /**
     * Returns an iterator over the entries at the specified point, defined
     * by the {@code x} and {@code y} parameters. The
     * iterator returns elements in no particular order and it may be
     * emtpy.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return an iterator which can traverse over the entries that exist
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the bounding box defined by the quadtree
     */
    QuadTreeIterator<E> pointIterator(double x, double y);

    /**
     * Returns an iterator over the entries contained in the bounding box,
     * defined by {@code x1}, {@code y1}, {@code x2} and {@code y2} parameters.
     * The iterator returns elements in no particular order and it may be
     * emtpy.
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
     * The indices and values of the array are as follows:
     * 
     * Index 0 corresponds to the smallest X coordinate value.
     * Index 1 corresponds to the smallest Y coordinate value.
     * Index 2 corresponds to the largest X coordinate value.
     * Index 3 corresponds to the largest Y coordinate value.
     *
     * For example, getBoundingBox()[0] contains the smallest X coordinate
     * value of the bounding box.
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
