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

import java.math.BigInteger;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

/**
 * The {@code ConcurrentQuadTree} is a data structure which is used to represent
 * elements and their respective Cartesian positions in rectangular,
 * two-dimensional regions. More specifically, the data structure subdivides
 * existing regions into four, equally-sized regions in order to allot one
 * region for a desired number of elements. These sub-regions are also
 * referred to as quadrants, and are represented by leaf nodes in the tree.
 * Therefore, each point in the region represented by the
 * {@code ConcurrentQuadTree} will be located in a region containing
 * no more than the specified number of points for each leaf node. This
 * parameter is defined in the constructor of the {@code ConcurrentQuadTree},
 * and is referred to as the {@code bucketSize}.
 * <p>
 * This type of organization is best interpreted as a tree whereby "deeper"
 * nodes correspond to smaller regions. Elements can only exist at the leaf
 * nodes; if a node overflows its bucket size, then it splits into smaller
 * regions at an incremented depth and the elements are reallocated.
 * <p>
 * A {@code ConcurrentQuadTree} enables a two-dimensional space to efficiently
 * hold onto a certain number of points using a relatively simple and
 * low-cost scheme. The {@code ConcurrentQuadTree} does not support {@code null}
 * elements; that is, calling {@code put(x, y, null)} is not permitted. The
 * {@code ConcurrentQuadTree} does support multiple elements at the same
 * coordinate location. In other words, it is legal to do the following:
 * <p>
 * {@code put(1, 1, object1);}<br>
 * {@code put(1, 1, object2);}, etc.
 *
 * <p>
 * Although it is legal to place multiple elements at the same coordinate
 * location, placing a large number of elements at single point is discouraged
 * because  too many elements at a single coordinate location might also be too
 * much to read in a single task.
 *
 * <p>
 * Overall, many of the methods occur in logarithmic time because the tree has
 * to be walked in order to locate the correct region to manipulate. This is
 * not very costly because the {@code ConcurrentQuadTree} has a tendency to
 * grow horizontally, especially if values are spaced far enough apart.
 * 
 * <p>
 * An iterator scheme is used to retrieve all elements in a particular 
 * sub-region (also known as an bounding box}) of the tree. Therefore,
 * the order of elements in the iteration is not guaranteed to be
 * the same for each iterator constructed since a different bounding box}
 * may be used for each iterator. 
 *
 * <p>
 * The iterator used to go through elements in the tree is serializable but
 * not a {@link ManagedObject}. There may be many elements that will need to be
 * iterated over which could take more time than the minimum allowed for a
 * single {@link Task}. As a result, several tasks may be needed to iterate
 * over all the necessary elements, requiring the iterator to be serialized
 * between each task. 
 *
 * <p>
 * The iterator throws {@link ConcurrentModificationException} if the current 
 * leaf it was on has split or was replaced by a new leaf because the iterator
 * will no longer be able to accurately determine its position in the tree.
 * The iterator will also throw {@link CurrentConcurrentRemovedException} if
 * the current element was removed from the {@code ConcurrentQuadTree}
 * and it was not removed by the iterator through a call to {@code remove()}.
 * Whenever an iterator has just been deserialized, it is recommended that
 * {@code hasCurrent()} and {@code hasNext()} be called before a call to
 * {@code current()} and {@code next()} or {@code nextNoReturn()} respectively
 * since the current or next element may have been removed concurrently
 * while the iterator was serialized. This will allow for more concurrency by
 * avoiding exceptions when iterating through the {@code ConcurrentQuadTree} if
 * it is being modified simultaneously.
 *
 *
 *
 * @param <E> the type of element the {@code ConcurrentQuadTree} is to hold
 */
public class ConcurrentQuadTree<E> implements QuadTree<E>, Serializable,
        ManagedObjectRemoval {

    /*
     * The leaves of the tree are either a node or null. When a leaf is null,
     * it is a leaf which contains no values. To save space, a leaf with
     * no values is itself null rather than a node with null values, which would
     * take up more space. The only exception to this rule is a tree with a 
     * single leaf that has no values in which case the leaf (root) has null
     * values.
     *
     * To allow for concurrency, this data structure does not propagate changes
     * from leaf nodes toward the root node. Since the tree does not grow
     * upwards, nodes that have been created permanently maintain their
     * tree depth, unless they are removed (in other words, collapsed or
     * pruned).
     * 
     * As suggested above, a tree depth of 0 corresponds to a single node
     * (the root), without any children. Nodes are removed from the tree (and 
     * thus, DataManager) when they have neither children containing elements,
     * nor children containing children. By definition, this also means that the
     * size of the node and all its children are 0. This measure is taken to
     * improve the performance of walking the tree in the future and reduces
     * memory requirements. 
     **/
    private static final long serialVersionUID = 1L;

    /*
     * These fields must start at 0 and ascend continuously since they
     * represent the indices of an array of type {@code double} of size 4,
     * which holds onto a pair of coordinates
     */
    /** The index intended for the minimum x-coordinate. */
    public static final int X_MIN = 0;
    /** The index intended for the minimum y-coordinate. */
    public static final int Y_MIN = 1;
    /** The index intended for the maximum x-coordinate. */
    public static final int X_MAX = 2;
    /** The index intended for the maximum y-coordinate. */
    public static final int Y_MAX = 3;
    /** The maximum capacity of a node */
    private final int bucketSize;
    /**
     * An object consisting of two corners that comprise the box representing
     * the sample space
     */
    private final BoundingBox boundingBox;
    /** The root element of the Quadtree */
    private ManagedReference<Node<E>> root;

    /**
     * A five-argument constructor which defines a {@code Quadtree} with a 
     * {@code bucketSize} supplied as a parameter. The area corresponding to
     * this instance is defined by the supplied coordinates whereby ({@code x1},
     * {@code y1}) represent the first {@code Point} and ({@code x2},
     * {@code y2}) represents the second {@code Point} of the defining
     * {@code BoundingBox}.
     *
     * @param bucketSize the maximum capacity of a leaf node
     * @param x1 the x-coordinate of the first point defining the tree's
     * bounding box
     * @param y1 the y-coordinate of the first point defining the tree's
     * bounding box
     * @param x2 the x-coordinate of the second point defining the tree's
     * bounding box
     * @param y2 the x-coordinate of the second point defining the tree's
     * bounding box
     * @throws IllegalArgumentException if bucketSize is not positive.
     */
    public ConcurrentQuadTree(int bucketSize, double x1, double y1,
            double x2, double y2) {

        if (bucketSize <= 0) {
            throw new IllegalArgumentException("Bucket size must be " +
                    "positive.");
        }

        this.bucketSize = bucketSize;
        DataManager dm = AppContext.getDataManager();
        boundingBox =
                new BoundingBox(new Point(x1, y1), new Point(x2, y2));
        Node<E> node = new Node<E>(boundingBox, bucketSize);
        root = dm.createReference(node);
    }

    /**
     * A copy constructor which adds all elements within the {@code tree}
     * parameter into the new {@code ConcurrentQuadTree}. For trees with many
     * elements, this operation can take a long time since all elements need to
     * be iterated through. If the tree is very large, it may take more time
     * than the maximum allowed for a single task to copy the tree.
     *
     * @param tree the {@code ConcurrentQuadTree} whose elements are to be added
     * into the new {@code ConcurrentQuadTree}
     */
    public ConcurrentQuadTree(ConcurrentQuadTree<E> tree) {
        boundingBox = tree.boundingBox;
        bucketSize = tree.bucketSize;

        QuadTreeIterator<E> iter = tree.iterator();
        E element;
        while (iter.hasNext()) {
            element = iter.next();
            double x = iter.currentX();
            double y = iter.currentY();
            put(x, y, element);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return (root.get().getChildren() == null &&
                root.get().getValues() == null);
    }

    /**
     * A method which checks if the {@code point} parameter is within this
     * {@code ConcurrentQuadTree}'s bounding box.
     *
     * @param point the {@code Point} which will be examined to see if it
     * falls within the bounds of the bounding box
     * @throws IllegalArgumentException if point is not within the bounding box
     */
    private void checkBounds(Point point) {

        // Check to see that the node is within bounds since
        // the returned quadrant could be invalid if the point is
        // out of bounds
        int quadrant =
                Node.determineQuadrant(boundingBox, point);
        if (quadrant == Node.INVALID_QUADRANT) {
            throw new IllegalArgumentException(
                    "The coordinates are not contained " +
                    "within the bounding box");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void put(double x, double y, E element) {
        Point point = new Point(x, y);

        // Check to see that the point is within bounds
        checkBounds(point);
        Node<E> leaf = root.get().getLeafNode(point);
        leaf.add(point, element, true);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(double x, double y) {
        boolean result = false;
        Point point = new Point(x, y);

        // Check to see that the point is within bounds
        checkBounds(point);

        Node<E> possibleLeaf = root.get().getLeafNode(point);

        //Keep removing elements from point while there are still elements
        //left
        if (possibleLeaf.isLeaf()) {
            while (possibleLeaf.remove(point, 0, true) != null) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Returns the {@code ManagedObject} which is being referenced by the
     * argument
     *
     * @param ref the reference whose value to return
     * @return the value of the reference, or {@code null}
     * if either the reference is {@code null} or the object no longer exists
     */
    private static <E> E getReferenceValue(ManagedReference<E> ref) {
        if (ref != null) {
            return ref.get();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * The values of the array can be easily extracted using the static constant
     * integer fields {@code X_MIN},  {@code Y_MIN}, {@code X_MAX} and
     * {@code Y_MAX} as indices. For example, to obtain the smallest
     * x-coordinate, the call should be:
     * <p>
     * {@code getBoundingBox()[ConcurrentQuadTree.MIN_X_INT]}
     */
    public double[] getBoundingBox() {
        return boundingBox.getCornerValues();
    }

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<E> boundingBoxIterator(double x1, double y1,
            double x2, double y2) {
        Point corner1 = new Point(x1, y1);
        Point corner2 = new Point(x2, y2);

        // Check to see that the corner1 and corner2 are within bounds
        checkBounds(corner1);
        checkBounds(corner2);

        BoundingBox box = new BoundingBox(corner1, corner2);
        return new ElementIterator<E>(root, box);
    }

    /**
     * {@inheritDoc}
     **/
    public QuadTreeIterator<E> pointIterator(double x, double y) {
        Point point = new Point(x, y);

        // Check to see that the point and is within bounds
        checkBounds(point);

        BoundingBox box = new BoundingBox(point, point);
        return new ElementIterator<E>(root, box);
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        // If the tree is empty, there is no work to be done
        if (isEmpty()) {
            return;
        }
        // Delete the old tree completely
        removingObject();
        AppContext.getDataManager().markForUpdate(this);
        // Create a new root for the new tree
        Node<E> newRoot = new Node<E>(boundingBox, bucketSize);
        root = AppContext.getDataManager().createReference(newRoot);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(double x, double y) {
        Point point = new Point(x, y);

        // Check to see that the point is within bounds
        checkBounds(point);

        //If the node returned is not a leaf, it must
        //the parent of the null leaf, meaning there is no leaf
        //which contains the given point
        Node<E> leaf = root.get().getLeafNode(point);
        return (leaf.isLeaf() && leaf.contains(point));
    }

    /**
     * {@inheritDoc}
     */
    public void removingObject() {

        AsynchronousClearTask<E> clearTask =
                new AsynchronousClearTask<E>(root);

        // Schedule asynchronous task here
        // which will delete the tree
        AppContext.getTaskManager().scheduleTask(clearTask);
    }

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<E> iterator() {
        return new ElementIterator<E>(root, boundingBox);
    }

    /** Used to print out the tree for debugging and testing purposes.
     * @param node   The node which will be printed out
     * @param isRoot True if node passed in is the tree root, false otherwise
     * */
    private void printTree(Node node, boolean isRoot) {
        if (isRoot) {
            printTree(root.get(), false);
        } else if (node == null || node.isLeaf()) {
            System.out.println(" leaf " + node);
        } else {
            System.out.println(node);
            System.out.print(" branch1 ");
            printTree(node.getChild(0), false);
            System.out.print(" branch2 ");
            printTree(node.getChild(1), false);
            System.out.print(" branch3 ");
            printTree(node.getChild(2), false);
            System.out.print(" branch4 ");
            printTree(node.getChild(3), false);
        }
    }

    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;; Nested Class Definitions ;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    /**
     * An iterator which walks through the elements stored in 
     * {@code ConcurrentQuadTree}. This implementation allows for
     * serialization, while also reporting consistency problems in the form of
     * {@code ConcurrentModificationException} and 
     * {@code CurrentConcurrentRemovedException}.
     *
     * The next element is reloaded everytime {@code next()}, {@code hasNext()}
     * and {@code nextNoReturn()} is called in case the next element has
     * changed. If the current leaf the iterator was on has split or was
     * removed then replaced by an entirely new leaf node, then
     * {@code ConcurrentModificationException} would be thrown because the
     * iterator would no longer be able guarantee its location in the
     * tree since the structure of the tree may have significantly changed.
     * 
     * If {@code current()} is called and the current element the iterator
     * was on, was removed from the tree but the element was not removed
     * by the iterator, then a {@code CurrentConcurrentRemovedException} will
     * be thrown instead of an {@code IllegalStateException}. This is to
     * let the user know the difference between the iterator itself removing
     * the element and the element being removed by a different thread or while
     * the iterator was serialized.
     *
     * The iterator is optimized to not search needlessly down a particular 
     * branch if the {@code boundingBox} specified by the iterator does not
     * partially or fully contain leaves of that particular branch.
     * The iterator simply stops at the root of the branch after comparing the
     * root's {@code boundingBox} with the {@code boundingBox} given to the
     * iterator.
     *
     * If a {@code null} is passed in as the {@code boundingBox} then there will
     * be no restrictions on the region that the iterator can iterate over and
     * it will iterate over all elements of the {@code Quadtree}.
     *
     * For improved performance, the iterator caches the dataIntegrityValue
     * of the current leaf it is on. As a result, the iterator can quickly
     * check if the current leaf has changed by comparing its cached
     * dataIntegrityValue with the current leaf's dataIntegrity value. This
     * removes the the need for the iterator to find its current position
     * in the tree again for every iterator operation.
     *
     */
    static class ElementIterator<E> implements QuadTreeIterator<E>,
            Serializable {

        private static final long serialVersionUID = 4L;
        /** A region specific to this iterator */
        private final BoundingBox box;
        /** Whether the current element was removed by the iterator */
        private boolean accordingToIteratorCurrExists;
        /** The current node being examined */
        private ManagedReference<Node<E>> current;
        /**
         * Root of the tree being iterated over by this iterator. Used to
         * determine the leaf node containing a point, after deserialization.
         */
        private ManagedReference<Node<E>> root;
        /**
         * The current entry (the last entry returned from a call to
         * {@code next})
         */
        private Entry<E> entry;

        /**
         * dataIntegrityValue of the current leaf node the iterator is on. Used
         * as a quick check to see if modifications have been made to the
         * current leaf node which the iterator is not aware of.
         */
        private int dataIntegrityValue;
        /**
         * index of the current element in the list of elements corresponding
         * to the current point and current leaf node
         */
        private int currentIndex;

        //Used by the iterator to determine whether it should advance to
        //the next element or just to check if the next element has changed
        private boolean isCheckingNext;

        /**
         * Two-argument constructor which permits specification of a bounding
         * box specific to the iterator.
         *
         * @param root the root node of the {@code ConcurrentQuadTree}, used to
         * locate the first child
         * @param box the region which specifies the qualified entries that
         * this iterator is to iterate over; a value of {@code null} means all
         * entries are valid (no bounding box)
         */
        ElementIterator(ManagedReference<Node<E>> root, BoundingBox box) {
            this.box = box;
            this.root = root;
            current = null;
            accordingToIteratorCurrExists = false;
            entry = null;
            currentIndex = 0;
            dataIntegrityValue = 0;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasCurrent() {
            if (current != null) {
                try {
                    Node<E> currentNode = current.get();

                    //Compare the iterator's dataIntegrityValue with the
                    //current leaf's dataIntegrityValue, returning true if
                    //no changes have been made to the current leaf that the
                    //iterator is not already aware of
                    if (currentNode.getIntegrityValue() == dataIntegrityValue) {
                        return true;
                    } else {
                    //Check if the original leaf, point and finally element
                    //is still in the tree
                        TreeMap<Point, List<ManagedWrapper<E>>> map =
                                currentNode.getValues();
                        if (map != null) {
                            List<ManagedWrapper<E>> list =
                                    map.get(entry.getCoordinate());
                            if (list != null &&
                                    Collections.binarySearch(list,
                                    entry.getValue(),
                                    new ManagedReferenceComparator()) >= 0) {
                                return true;
                            }
                        }
                    }
                } catch (ObjectNotFoundException onfe) {
                }
            }
            return false;
        }

        /**
         * Returns {@code true} if the iteration has more elements. (In
         * other words, returns {@code true} if {@code next} would
         * return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iterator has more elements, false
         * otherwise
         */
        public boolean hasNext() {
            //Try to check the next element, in case it has changed
            isCheckingNext = true;
            return reload();
        }


        /**
         * Sets the cached data (leaf node, point, index) regarding the current
         * element to the data regarding the next element if isCheckingNext
         * is {@code false}.
         * 
         * @param nextData NextElementData object containing the data regarding
         * the next element
         * @param valueRef {@code ManagedWrapper} conaining a
         * {@code ManagedReference} to the next element
         */
        private void  setValues(NextElementData nextData,
                ManagedWrapper<E> valueRef) {
            if (!isCheckingNext) {
                current = uncheckedCast(nextData.getNextNode());
                entry = new Entry <E>(nextData.getNextPoint(), valueRef);
                currentIndex = nextData.getNextIndex();
                dataIntegrityValue = current.get().getIntegrityValue();
            }
        }

        /**
         * Used to check if there is a next element left in the tree for the
         * iterator to iterate over. Also advances the iterator to the next
         * element if isCheckingNext is {@code false}.
         * @return {@code true} if there is a next element or {@code false}
         * if there are no more elements left for the iterator to iterate over
         */
        private boolean reloadNext() {
            Node<E> currentNode;
            Point nextPoint;
            NextElementData nextData = null;
            ManagedReference<Node<E>> next = null;
            try {
                currentNode = current.get();
                TreeMap<Point, List<ManagedWrapper<E>>> map =
                        currentNode.getValues();
                int nextIndex;
                //Assume there are still elements left to be iterated over
                //on the current leaf and at the current point
                next = current;
                nextPoint = entry.getCoordinate();

                //Compare dataIntegrityValue of current leaf with the
                //dataIntegrityValue cached by the iterator, if they are the
                //same jump straight to the next element using the currentIndex,
                //current leaf node and current point values cached by the
                //iterator
                if (currentNode.getIntegrityValue() == dataIntegrityValue) {
                    nextIndex = currentIndex;
                    if (map == null) {
                        return false;
                    }
                    nextIndex++;
                    nextData = 
                            new NextElementData <E>(next, nextPoint, nextIndex);
                    return nextElement(nextData);
                }

                //If the current leaf the iterator is on has not split
                //to become a non-leaf node
                if (currentNode.isLeaf()) {
                    //currentNode is empty root, there is no next element
                    if (map == null) {
                        return false;
                    }
                    List<ManagedWrapper<E>> list =
                            map.get(entry.getCoordinate());
                    //If the current point still exists in the current leaf
                    if (list != null) {
                        nextIndex =
                                Collections.binarySearch(list, entry.getValue(),
                                new ManagedReferenceComparator());
                        //If current element still exists in the list of
                        //elements corresponding to currentPoint
                        if (nextIndex >= 0) {
                            nextIndex++;
                        } else {
                            //If the current element no longer exists in the
                            //list, find the index of where the current
                            //element should go if it had been in the list
                            nextIndex = (nextIndex + 1) * (-1);
                        }
                        nextData = new NextElementData <E>(next, nextPoint,
                                nextIndex);
                    } else {
                        //If currentPoint has been removed, find the next
                        //valid point. If there are no more valid points left
                        //return null
                        nextPoint = map.higherKey(entry.getCoordinate());
                        nextData = getNextPoint(next, nextPoint);
                        nextPoint = nextData.getNextPoint();
                        if (nextPoint == null) {
                            return false;
                        }
                    }
                    //Get the next element
                    return nextElement(nextData);

                } else {
                    //Throw ConcurrentModificationException if the current
                    //leaf has split
                    throw new ConcurrentModificationException("Current node " +
                            "had split!");
                }
            } catch (ObjectNotFoundException onfe) {

                //If current leaf the iterator was on has been removed,
                //get the parent of the node that should contain currentPoint
                currentNode = root.get().getLeafNode(entry.getCoordinate());

                //If the parent is a root and also a leaf
                if (currentNode.getParent() == null && currentNode.isLeaf()) {
                    //If the root is empty, return null, there are no more
                    //elements to iterate over
                    if (currentNode.getValues() == null) {
                        return false;
                    } else {
                        //If the root still contains values, try to find the 
                        //next element in root's values
                        next = root;
                        nextPoint = entry.getCoordinate();
                        nextData = getNextPoint(next, nextPoint);
                        return nextElement(nextData);

                    }
                } else if (currentNode.isLeaf()) {
                    //If a leaf was returned while looking for the node 
                    //containing currentPoint, the original current leaf must
                    //have been replaced by a newer leaf, iteration order is no
                    //longer guaranteed
                    throw new ConcurrentModificationException("Current node " +
                            "was removed and replaced with a new node.");
                }

                //Go through the siblings of the removed current node, first,
                //looking for the next element
                int nextQuadrant =
                        Node.nextQuadrant(
                        Node.determineQuadrant(currentNode.getBoundingBox(),
                        entry.getCoordinate()));
                next = null;
                while (nextQuadrant != Node.INVALID_QUADRANT && next == null) {
                    Node<E> child = currentNode.getChild(nextQuadrant);
                    if (child != null) {
                        next = getFirstQualifiedLeafNode(child);
                    }
                    nextQuadrant = Node.nextQuadrant(nextQuadrant);
                }

                //If a qualified leaf node could not be found within the
                //siblings of the removed current node, search for the
                //next qualified leaf through the entire tree
                if (next == null) {
                    next = getNextLeafNode(currentNode, this.box);
                    if (next == null) {
                        return false;
                    }
                }
              
                //If a qualified leaf was found, look through it for a
                //qualified point, findNextQualifiedPoint also
                //looks for a qualified point in other leaves if one isn't found
                //in this qulaified leaf
                nextData = findNextQualifiedPoint(next, null);
                nextPoint = nextData.getNextPoint();

                //If no qualified point was found in the entire tree,
                //there is no more elements to iterate over
                if (nextPoint == null) {
                    return false;
                }
                //Get the next element if a qualified next point was found
                return nextElement(nextData);
            }
        }

        /**
         * Finds the next qualified point by first trying to
         * find it in the next qualified leaf node, then other qualified
         * leaf nodes if one cannot be found in the next qualified leaf node.
         * Note this method differs from findNextQualifiedPoint in that it tries
         * to look in the current leaf the iterator is on first, for the next
         * qualified point before it starts to search the entire tree.
         * @param next the first leaf node which will be searched through for
         * a qualified point
         * @param first point which will be considered when searching for the
         * next qualified point
         * @return data regarding the next possible element
         * including the next leaf node, the next point and the index of the
         * next element
         */
        private NextElementData getNextPoint(ManagedReference<Node<E>> next,
                Point nextPoint) {
            assert (next != null) : "Next leaf node cannot be null";
            Node<E> nextNode = next.get();

            //Search through the next leaf node, trying to find a
            //qualified point
            while (nextPoint != null && !box.contains(nextPoint)) {
                nextPoint = nextNode.getValues().higherKey(nextPoint);
            }

            //If next leaf node does not contain a qualified point, get
            //the next qualified leaf node and try to find a qualified point
            //again
            if (nextPoint == null) {
                next = getNextLeafNode(nextNode, this.box);
                NextElementData nextData =
                        findNextQualifiedPoint(next, null);
                return nextData;
            }
            //Returns data regarding the next possible element with the updated
            //nextPoint and resetting the next index of the iterator's position
            //in the list of elements corresponding to the next point
            return new NextElementData <E>(next, nextPoint, 0);
        }

        /**
         * Checks if there is a next element when {@code next()} or
         * {@code nextNoReturn()} is called for the very first time after this
         * iterator has been initialized. Also advances the iterator, if
         * {@code isCheckingNext} is set to false.
         * @return {@code true} if there is a qualified first element for the 
         * iterator to iterate over or {@code false} if not
         */
        private boolean loadFirst() {

            //Get the first qualified leaf node
            ManagedReference<Node<E>> next =
                    getFirstQualifiedLeafNode(root.get());
            current = next;
            
            //find the first qualified point
            NextElementData nextData = findNextQualifiedPoint(next, null);
            next = uncheckedCast(nextData.getNextNode());

            Node<E> node = (getReferenceValue(next));
            
            //Stop, if there are no qualified leaf nodes returning false
            if (node == null) {
                current = null;
                return false;
            }

            //Get the first element
            boolean nextExists = nextElement(nextData);

            //If iterator is just checking the next element, reset current to
            //null
            if (isCheckingNext) {
                current = null;
            }
            
            return nextExists;
        }

        /**
         * Starting at the leaf node "next" and with the point "nextPoint",
         * search through the entire tree for the next qualified point
         * @param next the first leaf node which will be searched through for
         * a qualified point
         * @param first point which will be considered when searching for the
         * next qualified point
         * @return data regarding the next possible element
         * including the next leaf node, the next point and the index of the
         * next element
         */
        private NextElementData findNextQualifiedPoint(
                ManagedReference<Node<E>> next, Point nextPoint) {
            Node<E> node = getReferenceValue(next);

            //Keep looping until a qualified point is found or there are no
            //more qualified leaf nodes left
            while (nextPoint == null && node != null) {
                nextPoint = node.getValues().firstKey();
                //Go through the points in the leaf node looking for
                //qualified points
                while (nextPoint != null && !box.contains(nextPoint)) {
                    nextPoint = node.getValues().higherKey(nextPoint);
                }

                //Get the next qualified leaf node if the previous leaf node
                //did not contain any qualified points
                if (nextPoint == null) {
                    next = getNextLeafNode(next.get(), this.box);
                    node = getReferenceValue(next);
                }
            }
            return new NextElementData <E>(next, nextPoint, 0);
        }

        /** Determines if there are any elements left for the iterator to
         *  iterate over. Also advances the iterator if {@code isCheckingNext}
         *  is false.
         *  @param  nextData  contains data regarding the next possible element
         *  including the next leaf node, the next point and the index of the
         *  next element
         *  @return {@code true} if there is a next element or {@code false}
         *  if the iterator has gone through all the elements in the tree
         */
        private boolean nextElement(NextElementData nextData) {
            ManagedReference<Node<E>> next = 
                    uncheckedCast(nextData.getNextNode());
            Node<E> nextNode = next.get();
            Point nextPoint = nextData.getNextPoint();
            List<ManagedWrapper<E>> list =
                    nextNode.getValues().get(nextPoint);

            int nextIndex = nextData.getNextIndex();
            //Get the next element in the list if there are any elements left
            if (nextIndex < list.size()) {
                setValues(nextData, list.get(nextIndex));
                return true;
            } else {

                //Get the next qualified point if there are no more elements
                //left in the list
                nextPoint = nextNode.getValues().higherKey(nextPoint);
                nextData = getNextPoint(next, nextPoint);
                nextPoint = nextData.getNextPoint();
                if (nextPoint != null) {
                    next = uncheckedCast(nextData.getNextNode());
                    list = next.get().getValues().get(nextPoint);
                    setValues(nextData, list.get(nextData.getNextIndex()));
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration.
         * @exception NoSuchElementException iteration has no more elements.
         * @throws ConcurrentModificationException if the current leaf was has
         * split or was replaced by a different leaf, iteration order is no
         * longer guaranteed
         */
        public E next() {
            isCheckingNext = false;
            loadNext();
            return entry.getValue().get();
        }

        /**
         * Reloads the next element depending on whether it is the very first
         * element to be loaded
         * */
        private boolean reload() {
            //If the first element has not loaded yet, call loadFirst() again
            //to reload it
            if (current == null) {
                return loadFirst();
            } else {
                return reloadNext();
            }
        }

        /**
         * Sets up the next element without returning it. The two
         * "next" methods will decide whether they want to return
         * the value or not
         */
        private void loadNext() {

            //Check if there are any elements left to iterate over
            if (!reload()) {
                throw new NoSuchElementException();
            }
            accordingToIteratorCurrExists = true;
        }

        /**
         * {@inheritDoc}
         * @throws ConcurrentModificationException if the next node in the
         * sequence was removed, or after deserialization, if this node was
         * modified or removed.
         */
        public void nextNoReturn() {
            isCheckingNext = false;
            loadNext();
        }

        /**
         * {@inheritDoc}
         * @throws CurrentConcurrentRemovedException if the current element
         *         is no longer in the tree but was not removed by the iterator
         *  
         */
        public E current() {

            // Check if current element has been removed or next() method has
            // not been called
            checkCurrentEntry();
            return entry.getValue().get();
        }

        /**
         * {@inheritDoc}
         */
        public double currentX() {
            if (entry == null) {
                throw new IllegalStateException("Neither next() nor " +
                        "nextNoReturn() has been called yet!");
            }
            return entry.getCoordinate().getX();
        }

        /**
         * {@inheritDoc}
         */
        public double currentY() {
            if (entry == null) {
                throw new IllegalStateException("Neither next() nor " +
                        "nextNoReturn() has been called yet!");
            }
            return entry.getCoordinate().getY();
        }

        /**
         * Checks if the current entry is still in the tree.
         */
        private void checkCurrentEntry() {

            //If the iterator has removed the current element or the very first
            //element has never been loaded
            if (!accordingToIteratorCurrExists) {
                throw new IllegalStateException("There is no current" +
                        "element.");
            }

            //If accordingToIteratorCurrExists
            //is still set to true, but the current element
            //is gone, it must have been removed concurrently and not by
            //the iterator
            if (!hasCurrent()) {
                throw new CurrentConcurrentRemovedException("Current" +
                        " element in the tree was removed concurrently.");
            }

        }

        /**
         * Removes from the underlying collection the last element returned by
         * the iterator (optional operation). This method can be called only
         * once per call to <tt>next</tt>. 
         *
         * @throws CurrentConcurrentRemovedException if the current element
         * is no longer in the tree and was not removed by the iterator
         * @throws IllegalStateException if the <tt>next</tt> method has
         * not yet been called or if the current element has been removed
         * already
         */
        public void remove() {
            checkCurrentEntry();
            accordingToIteratorCurrExists = false;
            List<ManagedWrapper<E>> list =
                    current.get().getValues().get(entry.getCoordinate());

            //Find the index of the element last returned and remove it
            int index =
                    Collections.binarySearch(list, entry.getValue(),
                    new ManagedReferenceComparator());
            current.get().remove(entry.getCoordinate(), index, true);
        }

        /**
         * Retrieves the first non-null leaf node in a tree, rooted
         * by {@code node}. If the box
         * parameter is {@code null}, then it is assumed that there is no
         * {@code BoundingBox} limiting the area of search.
         *
         * @param node the root of the tree or subtree
         * @param box  the boundingBox to search in for the leaf node
         * @return the first child of {@code node} that is a leaf or null if
         *          the node does not have any children with leaves in the
         *          boundingBox
         */
        static <E> ManagedReference<Node<E>> getFirstLeafNode(Node<E> node,
                BoundingBox box) {

            // If the given node is a leaf with values, we are done
            if (node.isLeaf() && (box == null ||
                    box.intersect(node.getBoundingBox()))) {
                return AppContext.getDataManager().createReference(node);
            }

            // Iterate through all the children in a depth-first
            // search looking for the first encountered non-null leaf,
            // only searching in children whose bounding box intersects with
            // the specified bounding box
            for (ManagedReference<Node<E>> childRef : node.getChildren()) {
                if (childRef != null && (box == null ||
                        box.intersect(childRef.get().getBoundingBox()))) {
                    ManagedReference<Node<E>> possibleNode =
                            getFirstLeafNode(childRef.get(), box);
                    if (possibleNode != null) {
                        return possibleNode;
                    }
                }
            }
            return null;
        }

        /**
         * Returns the next node using a post-order traversal scheme. If we
         * try to retrieve the next node while on the root, {@code null} is
         * returned, specifying our exit condition. If the box parameter is
         * {@code null}, then it is assumed that there is no BoundingBox
         * limiting the area of search.
         *
         * @param node the current node, whose next element we are interested
         * in finding
         * @param box  the boundingBox to search in for the leaf node
         * @return the next node in the depth-first traversal, or {@code null}
         * if none exists
         */
        static <E> ManagedReference<Node<E>> getNextLeafNode(Node<E> node,
                BoundingBox box) {

            while (true) {
                Node<E> parent = node.getParent();
                // End condition: we reached the root; there is no next leaf
                // node
                if (parent == null) {
                    return null;
                }

                // Try and fetch siblings. If all the siblings have been 
                //fetched, go upwards and fetch the siblings of parent nodes
                int quadrant = Node.nextQuadrant(node.getQuadrant());
                Node<E> child;

                //Check through all the children of parent for a non-null node
                while (quadrant != Node.INVALID_QUADRANT) {
                    child = parent.getChild(quadrant);
                    
                    // Dig deeper only if the child's bounding box
                    // intersects with the specified bounding box to save
                    // time and avoid needless traversals
                    if (child != null &&
                            (box == null ||
                            box.intersect(child.getBoundingBox()))) {

                        // Dig deeper if child is not a leaf,
                        // or if it is a leaf return it. Otherwise,
                        // keep searching this level.
                        if (!child.isLeaf()) {
                            ManagedReference<Node<E>> possibleNode =
                                    getFirstLeafNode(child, box);
                            if (possibleNode != null) {
                                return possibleNode;
                            }
                        } else {
                            return AppContext.getDataManager().
                                    createReference(child);
                        }
                    }
                    //Get quadrant of next child
                    quadrant = Node.nextQuadrant(quadrant);
                }
                node = parent;
            }
        }

        /**
         * Given the node parameter, return the first non-null leaf
         * which contains values in the defined region.
         *
         * @param node the node to start the search from
         * @return the first leaf node containing values, or {@code null} if
         * one does not exist
         */
        private ManagedReference<Node<E>> getFirstQualifiedLeafNode(
                Node<E> node) {

            // Return the leaf if it has values to iterate over (non-null leaf)
            ManagedReference<Node<E>> leaf = getFirstLeafNode(node, this.box);
            if (leaf != null) {

                //Check for case where leaf is the root and has no values
                if (leaf.get().getValues() != null) {
                    return leaf;
                }
                // Otherwise, try getting the next qualified node
                return getNextLeafNode(leaf.get(), this.box);
            }
            return null;
        }
    }

    /**
     * An inner class which is responsible for clearing the tree from the data
     * manager by performing a depth-first traversal.
     *
     * @param <E> the element stored in the tree
     */
    private static class AsynchronousClearTask<E> implements Task,
            Serializable, ManagedObject {

        private static final long serialVersionUID = 3L;
        /** The node currently being examined */
        private ManagedReference<Node<E>> current;
        /** The root of the tree being cleared */
        private ManagedReference<Node<E>> root;
        /** The total number of elements to remove for each task iteration */
        private static final int MAX_OPERATIONS = 50;
        /** The point currently being examined */
        private Point currentPoint;

        /**
         * The constructor of the clear task, which requires the root element
         * of the tree to begin the traversal
         *
         * @param root the root of the tree, which must not be {@code null}
         */
        AsynchronousClearTask(ManagedReference<Node<E>> root) {
            assert (root != null) : "The root parameter must not be null";
            current = ElementIterator.getFirstLeafNode(root.get(), null);
            currentPoint = getFirstPoint(current);
            this.root = root;
        }

        /**
         * The entry point into the task
         */
        public void run() {

            // Mark task for update
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);

            // Check if there is more work to be done. If so, reschedule.
            if (doWork()) {
                AppContext.getTaskManager().scheduleTask(this);
            } else {
                // If all the clearing work has been done, remove this task
                dm.removeObject(this);
            }
        }

        /**
         * Removes MAX_OPERATION number of elements from the
         * {@code ConcurrentQuadTree} using a post-order traversal
         * and returns {@code true} if there is more work to be done. If
         * there are no more elements to remove, then it will
         * return {@code false}.
         *
         * @return {@code true} if more work needs to be done, and
         * {@code false} if there are no more elements to remove.
         */
        private boolean doWork() {
            int count = 0;
            TreeMap<Point, List<ManagedWrapper<E>>> values;
            // Loop to remove elements. We'll stop when we reach the
            // end, if we max out our operations or if
            // currentPoint is null meaning there are no
            // elements to iterate over, whichever comes first
            while (++count < MAX_OPERATIONS) {

                // If we ran out of elements to remove, fetch the next
                // Point to try and remove.
                if (!removeElement()) {
                    DataManager dm = AppContext.getDataManager();
                    values = current.get().getValues();
                    if (values == null) {
                        Node<E> parent = current.get().getParent();
                        
                        //If first leaf is an empty root. remove the root
                        if (parent == null) {
                            dm.removeObject(current.get());
                            return false;
                        }

                        //Fetch next leaf node, then remove the current leaf
                        //node
                        ManagedReference<Node<E>> next =
                                getNextLeafWhileRemovingParents(parent,
                                current.get().getQuadrant());
                        dm.removeObject(current.get());

                        //If next leaf node isn't null, get the first point
                        //of next leaf node and prepare to remove elements,
                        //otherwise there are no more leaves left to be removed
                        if (next != null) {
                            current = next;
                            values = current.get().getValues();
                            currentPoint = values.firstKey();
                        } else {
                            return false;
                        }
                    } else {
                        //If all the elements in the list associated with
                        //the currentPoint have been removed, then currentPoint
                        //must have been removed, fetch next point
                        if (!values.containsKey(currentPoint)) {
                            currentPoint = values.higherKey(currentPoint);
                        }
                    }
                }
            }
            //Return whether or not there are more leaves left to be cleared
            return true;
        }

        /**
         * Returns the next leaf using a post-order traversal scheme while also
         * removing {@code node} (parent of current leaf) if {@code node} has
         * no more children. If we try to retrieve the next leaf while on the
         * root, {@code null} is returned, specifying
         * our exit condition.
         *
         * @param node the current leaf's parent which will be used as a
         * reference to find the next leaf
         * @return the next leaf in the post-order traversal, or {@code null}
         * if none exists
         */
        static <E> ManagedReference<Node<E>> getNextLeafWhileRemovingParents(
                Node<E> node, int lastQuadrant) {
            while (true) {

                int quadrant = Node.nextQuadrant(lastQuadrant);
                Node<E> child;

                // Try and fetch children. If all the siblings have been 
                // fetched, go upwards and fetch the siblings of parent nodes
                // Check through all the children of parent for a non-null node
                while (quadrant != Node.INVALID_QUADRANT) {
                    child = node.getChild(quadrant);

                    // Dig deeper only if the child's bounding box
                    // intersects with the specified bounding box to save
                    // time and avoid needless traversals
                    if (child != null) {

                        // Dig deeper if child is not a leaf,
                        // or if it is a leaf return it. Otherwise,
                        // keep searching this level.
                        if (!child.isLeaf()) {
                            return ElementIterator.getFirstLeafNode(child,
                                    null);
                        } else {
                            return AppContext.getDataManager().
                                    createReference(child);
                        }
                    }

                    //Get quadrant of next child
                    quadrant = Node.nextQuadrant(quadrant);
                }

                Node<E> parent = node.getParent();
                lastQuadrant = node.getQuadrant();

                AppContext.getDataManager().removeObject(node);
                // End condition: we reached the root; there is no next leaf
                //node
                if (parent == null) {
                    return null;
                }
                node = parent;
            }
        }

        /**
         * Returns the first {@code Point} belonging to the node,
         * or {@code null} if there are no {@code Point}s to examine.
         *
         * @param <E> the type of elements stored
         * @param node the current node being examined
         * @return the first point in the leaf node, or {@code null}
         * if there are none
         */
        private static <E> Point getFirstPoint(
                ManagedReference<Node<E>> node) {
            TreeMap<Point, List<ManagedWrapper<E>>> values =
                    node.get().getValues();

            //If the node is an empty root, there are no elements to clear,
            //values will be null
            if (values == null) {
                return null;
            }
            return values.firstKey();
        }

        /**
         * Removes an element from the current node and returns {@code true}
         * if it was removed. {@code False} is returned if no element was
         * removed, indicating that a new node should be fetched.
         *
         * @return {@code true} if an element was removed and {@code false}
         * otherwise
         */
        private boolean removeElement() {
            // Deletes the next element located at currentPoint
            return (current.get().remove(currentPoint, 0, false) != null);
        }
    }

    /**
     * A class used to wrap objects and make them managed. If object is
     * already a {@code ManagedObject}, then a {@code ManagedReference} to it
     * is created. Otherwise, if the object is not already a
     * {@code ManagedObject} then it is wrapped in a {@code ManagedSerializable}
     * and a {@code ManagedReference} to the {@code ManagedSerializable} wrapper
     * is created. The class cannot be used to wrap a {@code null} value.
     */
    static class ManagedWrapper<E> implements Serializable {

        private static final long serialVersionUID = 8L;
        //ManagedRference to the ManagedObject being wrapped by this 
        //ManagedWrapper
        private ManagedReference<?> ref;

       /**
         * Constructs a new {@code ManagedWrapper} given an object which could
         * be managed or not.
         *
         * @param object an {@code Object} to be wrapped by this
         *               {@code ManagedWrapper} which cannot be {@code null}
         */
        ManagedWrapper(E object) {
            if (object == null) {
                throw new NullPointerException("Tryin to wrap a null value");
            } else if (!(object instanceof Serializable)) {
                throw new IllegalArgumentException("Object not serializable");
            }

            //Wrap object in a ManagedSerializable depending on whether it
            //is already a ManagedObject or not
            ManagedObject managedObj =
                    (object instanceof ManagedObject) ? (ManagedObject) object :
                        new ManagedSerializable<E>(object);
            ref = AppContext.getDataManager().createReference(managedObj);
        }

       /**
         * Returns the object wrapped by this {@code ManagedWrapper}.
         *
         * @return  the object wrapped by this {@code ManagedWrapper}
         */
        E get() {
            ManagedObject obj = (ManagedObject) ref.get();
            if (obj instanceof ManagedSerializable) {
                ManagedSerializable<E> serialRef = uncheckedCast(obj);
                return serialRef.get();
            } else {
                @SuppressWarnings("unchecked")
                E result = (E) obj;
                return result;
            }
        }

       /**
         * Removes the object wrapped by this {@code ManagedWrapper} from the
         * data store.
         */
        void remove() {
            ManagedObject obj = (ManagedObject) ref.get();
            AppContext.getDataManager().removeObject(obj);
        }

       /**
         * Returns the {@code ManagedObject} Id of the {@code ManagedObject}
         * wrapped by this wrapper.
         *
         * @return  the {@code ManagedObject} Id of the {@code ManagedObject}
         * wrapped by this wrapper.
         */
        BigInteger getId() {
            return ref.getId();
        }
    }

    /**
     * Casts object into {@code E}. Used to avoid any unchecked warnings.
     * @param <E> the result type
     * @param object the object to cast
     * @return the object cast to type {@code E}
     * */
    @SuppressWarnings("unchecked")
    static <E> E uncheckedCast(Object object) {
        return (E) object;
    }

    /**
     * A comparator used to define behaviour when comparing two
     * ManagedObjects. Used to keep list of elements corresponding to a point
     * sorted.
     */
    static class ManagedReferenceComparator implements
            Comparator<ManagedWrapper>, Serializable {

        private static final long serialVersionUID = 2L;

        /**
         * Compares the {@code ManagedObject} Id of two {@code ManagedObject}s
         * which have each been wrapped using a {@code ManagedWrapper}.
         * @param obj1 a {@code ManagedWrapper}
         * @param obj2 another {@code ManagedWrapper}
         * @return positive integer if obj1 has a {@code ManagedObject} Id
         * greater than obj2, 0 if the two {@code ManagedObject}s have the
         * same Id and a negative integer otherwise
         */
        public int compare(ManagedWrapper obj1, ManagedWrapper obj2) {
            return obj1.getId().compareTo(obj2.getId());
        }
    }

    /**
     * A region, defined by two {@code Point}s, which represents the area
     * belonging to a certain object. The two {@code Point}s representing the
     * bounding box are Cartesian points which correspond to corner points of
     * an imaginary box. Each x and y coordinate for both points represent the
     * bounds of this box, and therefore, the bounds of the
     * {@code BoundingBox}. For simplicity, the {@code BoundingBox}'s edges
     * are only allowed to be parallel or perpendicular to the Cartesian axes,
     * meaning the {@code BoundingBox} edges either intersect the axes at
     * right angles or coincide with them.
     */
    static class BoundingBox implements Serializable {

        private static final long serialVersionUID = 3L;

        //Point representing the coordinate with the greatest x and y
        //values of the box
        Point maxPoint;

        //Point representing the coordinate with the least x and y
        //values of the box
        Point minPoint;

        /**
         * Constructs a new {@code BoundingBox} given two points representing
         * diagonal corners
         *
         * @param a one of the corners of the {@code BoundingBox}
         * @param b the other (diagonal) corner of the {@code BoundingBox}
         */
        BoundingBox(Point a, Point b) {
            organizeCoordinates(a, b);
        }

        /**
         * Creates a new bounding box based on a quadrant of the parent's
         * bounding box
         *
         * @param quadrant the quadrant to determine
         * @return the bounds for this node
         */
        BoundingBox createBoundingBox(int quadrant) {

            // Create the middle of the region, which is guaranteed to be a
            // corner of the node's bounds. Time to find the other corner
            Point middle = calculateMiddle(this);

            Point corner;
            switch (quadrant) {
                case Node.NW_QUADRANT:
                    corner = new Point(minPoint.x, maxPoint.y);
                    break;
                case Node.NE_QUADRANT:
                    corner = new Point(maxPoint.x, maxPoint.y);
                    break;
                case Node.SW_QUADRANT:
                    corner = new Point(minPoint.x, minPoint.y);
                    break;
                case Node.SE_QUADRANT:
                default:
                    corner = new Point(maxPoint.x, minPoint.y);
            }
            return new BoundingBox(middle, corner);
        }

        /**
         * Organizes the coordinates of the two Points given to the
         * {@code BoundingBox} constructor such that {@code minPoint} contains
         * the minimum x and y values of the bounding box while {@code maxPoint}
         * contains the maximum x and y values of the bounding box
         */
        void organizeCoordinates(Point a, Point b) {
            minPoint = new Point(Math.min(a.x, b.x),
                    Math.min(a.y, b.y));

            maxPoint = new Point(Math.max(a.x, b.x),
                    Math.max(a.y, b.y));
        }

        /**
         * Returns the coordinates of the {@code BoundingBox} as an
         * array so that the minimum and maximum values can be
         * obtained easily. The array's values are best accessed using
         * the fields {@code X_MIN}, {@code X_MAX}, {@code Y_MIN},
         * or {@code Y_MAX} as array indices.
         *
         * @return an array which contains max and min,
         *          x and y coordinate values
         */
        double[] getCornerValues() {
            double[] values = new double[4];
            values[X_MIN] = minPoint.x;
            values[Y_MIN] = minPoint.y;
            values[X_MAX] = maxPoint.x;
            values[Y_MAX] = maxPoint.y;
            return values;
        }

        /**
         * Checks whether the parameter otherBox intersects with the
         * region defined by this box or this box is a subset of 
         * {@code otherBox}.
         *
         * @param otherBox the region to check containment with
         * @return {@code true} if this region intersects with {@code otherBox}
         * or is a subset of otherBox, {@code false} otherwise
         */
        boolean getContainment(BoundingBox otherBox) {
            return (horizontalSidePartiallyContained(true, otherBox) ||
                    horizontalSidePartiallyContained(false, otherBox) ||
                    (otherBox.contains(maxPoint) &&
                    otherBox.contains(minPoint)));
        }

        /**
         * Used to check if the left (min) or right (max) side of box
         * intersects with the region defined by this box.
         *
         * @param checkMinSide if {@code true}, method will check the left`(min)
         * side of the box and if {@code false}, method will check the
         * right (max) side of the box
         * @param box the region to check containment with
         * @return {@code true} if this region intersects with otherBox
         * {@code false} otherwise
         */
        boolean horizontalSidePartiallyContained(boolean checkMinSide,
                BoundingBox box) {

            //Used to store the result from checking the x values
            boolean horizontalCheck;
            if (checkMinSide) {
                horizontalCheck =
                        isBetween(box.minPoint.x, maxPoint.x, minPoint.x);
            } else {
                horizontalCheck =
                        isBetween(box.maxPoint.x, maxPoint.x, minPoint.x);
            }
            return (horizontalCheck &&
                    (isBetween(box.minPoint.y, maxPoint.y, minPoint.y) ||
                    isBetween(box.maxPoint.y, maxPoint.y, minPoint.y)));
        }

        /**
         * Determines if the parameter {@code arg} is contained between the
         * two bounds.
         *
         * @param arg the argument to check for containment
         * @param maxBound the maximum coordinate value of the bounds
         * @param minBound the minimum coordinate value of the bounds
         * @return {@code true} if the parameter is between the two bounds, and
         * {@code false} otherwise
         */
        static boolean isBetween(double arg, double maxBound, double minBound) {
            return (minBound <= arg && arg <= maxBound);
        }

        /**
         * Determines if the argument is contained within the bounding region
         * specified by this object.
         *
         * @param point the {@code Point} to check for containment
         * @return {@code true} if the point exists within or on the bounding
         * region, and {@code false} otherwise
         */
        boolean contains(Point point) {
            //Check and see if the point is between the max and min, x and y
            //values defining this box
            return (isBetween(point.x, maxPoint.x, minPoint.x) &&
                    isBetween(point.y, maxPoint.y, minPoint.y));
        }

        /**
         * Returns a Point representing the middle of the region
         *
         * @param box the corner points specifying the region for which to
         * find the middle
         * @return the {@code Point} representing the middle
         */
        static Point calculateMiddle(BoundingBox box) {
            return new Point(box.minPoint.x +
                    ((box.maxPoint.x - box.minPoint.x) / 2),
                    box.minPoint.y + ((box.maxPoint.y - box.minPoint.y) / 2));
        }

        /**
         * Returns a string representing the two corners of the
         * region.
         *
         * @return a string representing the two corners of the region
         */
        public String toString() {
            return maxPoint.toString() + minPoint.toString();
        }

        /**
         * Checks if a part of or all of otherBox is contained within this box.
         *
         * @param otherBox The {@code BoundingBox} to be examined
         * @return {@code true} if a part of or all of boxTwo is contained
         * within boxOne and {@code false} if not
         */
        boolean intersect(BoundingBox otherBox) {
            return (otherBox != null && getContainment(otherBox));
        }
    }

    /**
     * A class containing data regarding the next element to be fetched by the
     * iterator. Used by the iterator to pass next element data
     * between different iterator methods.
     */
    static class NextElementData<E> {

        //The leaf node where the next element is located
        ManagedReference<Node<E>> nextNode;

        //The coordinate of the next element
        Point nextPoint;

        //The index of the next element in the next list of elements
        int nextIndex;

        NextElementData(ManagedReference<Node<E>> nextNode, Point nextPoint,
                int nextIndex) {
            this.nextNode = nextNode;
            this.nextPoint = nextPoint;
            this.nextIndex = nextIndex;
        }

        /**
         * Returns the leaf node where the next element is located.
         *
         * @return the leaf node where the next element is located
         */
        ManagedReference<Node<E>> getNextNode() {
            return nextNode;
        }

        /**
         * Returns coordinate of the next element.
         *
         * @return coordinate of the next element
         */
        Point getNextPoint() {
            return nextPoint;
        }

        /**
         * Returns index of the next element in the next list of elements.
         *
         * @return index of the next element in the next list of elements
         */
        int getNextIndex() {
            return nextIndex;
        }
    }

    /**
     * Represents an entry in the {@code ConcurrentQuadTree} by maintaining a
     * {@code ManagedWrapper} of the stored object and its coordinates
     * in the form of a {@code Point} object. It is only used by the iterator
     * and not used to store or wrap elements in the {@code ConcurrentQuadTree}.
     */
    static class Entry<E> implements Serializable, ManagedObject {

        private static final long serialVersionUID = 5L;
        /** The coordinate of the element */
        final Point coordinate;
        /** The {@code ManagedWrapper} wrapping the value of the element */
        private ManagedWrapper<E> valueRef;

        /**
         * Two-argument constructor which creates an entry given a coordinate
         * and {@code ManagedReference} to the value. The value must not be
         * {@code null}.
         *
         * @param coord the coordinate of the new entry
         * @param valueRef the value of the new entry, which cannot be
         * {@code null}
         */
        Entry(Point coord, ManagedWrapper<E> valueRef) {
            assert (valueRef != null) : "Value cannot be null";
            coordinate = coord;
            this.valueRef = valueRef;
        }

        /**
         * Returns the value of the entry which cannot be {@code null}.
         *
         * @return the value of the entry
         */
        ManagedWrapper<E> getValue() {
            return valueRef;
        }

        /**
         * Returns the coordinate of the entry.
         *
         * @return a Point representing the coordinate of the entry
         */
        Point getCoordinate() {
            return coordinate;
        }

        /**
         * Converts the {@code Entry} to a string representation.
         *
         * @return a string representation of the {@code Entry}
         */
        public String toString() {
            return valueRef.get().toString() + " at: " + coordinate;
        }
    }

    /**
     * A class that represents a point as an ({@code x}, {@code y})
     * coordinate pair.
     */
    static class Point implements Serializable, Comparable<Point> {

        private static final long serialVersionUID = 7L;
        /** the x-coordinate of the point */
        final double x;
        /** the y-coordinate of the point */
        final double y;

        /**
         * Constructor which creates a new {@code Point} instance given an x
         * and y-coordinate.
         *
         * @param x x-coordinate of the point
         * @param y y-coordinate of the point
         */
        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        /**
         * Determines if an object is equal to {@code this}. The method will
         * check that it is an instance of the {@code Point} class and check
         * that the coordinates are identical.
         *
         * @param obj object to check equality to this
         * @return {@code true} if the parameter is equal to this, and
         * {@code false} otherwise
         */
        public boolean equals(Object obj) {
            // If it is not an instance, it is not equal
            if (!(obj instanceof Point)) {
                return false;
            }
            Point param = (Point) obj;
            return (this.x == param.x) && (this.y == param.y);
        }

        /**
         * Returns the x coordinate of this point.
         * @return the x coordinate of this point
         * */
        double getX() {
            return this.x;
        }

        /**
         * Returns the y coordinate of this point.
         * @return the y coordinate of this point
         * */
        double getY() {
            return this.y;
        }

        /**
         * Converts the {@code Point} to a string representation.
         *
         * @return a string representation of the {@code Point}
         */
        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        /**
         * Returns a hash code for the object.
         * @return a hash code for the object
         */
        public int hashCode() {
            return (int) (x + y);
        }

        /**
         * Compares the x and y values of two points using a dictionary order
         * system where the x value is compared first and then the y value
         * if the x values are equal.
         * @param point the point which will be compared with this point
         * @return 0 if two Points are equal, -1 if this Point is less than
         *          the point passed in and 1 if this point is greather than
         *          the poin passed in
         */
        public int compareTo(Point point) {
            if (point.getX() > this.x ||
                    (point.getX() == this.x && point.getY() > this.y)) {
                return -1;
            }
            if (point.getX() == this.x && point.getY() == this.y) {
                return 0;
            }
            return 1;
        }
    } // end Point class

    /**
     * A class that represents a general-purpose node in the
     * {@code ConcurrentTree}. This class can either represent an
     * intermediate node or a leaf node
     *
     * @param <E> the type of coordinates to store
     */
    static class Node<E> implements Serializable, ManagedObject {

        private static final long serialVersionUID = 6L;

        // Integer-representation of the quadrants. It is critical that
        // these values are consecutive integers, starting at 0, because
        // they help index the children arrays for each node.
        private static final int NW_QUADRANT = 0;
        private static final int NE_QUADRANT = 1;
        private static final int SW_QUADRANT = 2;
        private static final int SE_QUADRANT = 3;

        // -1 is used to indicate an invalid quadrant.
        private static final int INVALID_QUADRANT = -1;
        private static final int INITIAL_INTEGRITY_VALUE = Integer.MIN_VALUE;
        /** the parent of this node */
        private final ManagedReference<Node<E>> parent;
        /** the maximum capacity of a leaf */
        private final int bucketSize;
        /** the branching factor for each node */
        static final int NUM_CHILDREN = 4;
        /**
         * the area (determined by two corner points) representing the node's
         * bounds
         */
        private final BoundingBox boundingBox;
        /** the quadrant this node belongs to */
        private final int myQuadrant;
        /**
         * the map of entries, with the value being a list of
         * {@code ManagedReference}s which point to
         * {@code ManagedSerializable} objects containing the stored entries.
         */
        private TreeMap<Point, List<ManagedWrapper<E>>> values;
        /** references to the children */
        private ManagedReference<Node<E>>[] children;
        // Value used by iterator to determine if elements have been removed
        // from the node.
        private int dataIntegrityValue;

        /**
         * Constructor to be used when instantiating the root. If children
         * need to be instantiated, call the four-argument constructor.
         *
         * @param box the region corresponding to this node's bounding box
         * @param bucketSize the maximum capacity of a leaf node
         */
        Node(BoundingBox box, int bucketSize) {
            this.boundingBox = box;
            this.parent = null;
            this.bucketSize = bucketSize;
            this.dataIntegrityValue = INITIAL_INTEGRITY_VALUE;
            myQuadrant = INVALID_QUADRANT;
            children = null;
            values = null;
        }

        /**
         * Creates a {@code Node} which is to be a child.
         * The {@code quadrant} argument must not be invalid.
         *
         * @param parent the parent of the {@code Node}
         * @param quadrant the {@code Quadrant} which this {@code Node}
         * represents
         * @param bucketSize the maximum capacity of a leaf node
         */
        Node(Node<E> parent, int quadrant, int bucketSize) {
            assert (quadrant != INVALID_QUADRANT) :
                    "The quadrant must be valid";
            DataManager dm = AppContext.getDataManager();
            boundingBox = parent.getBoundingBox().createBoundingBox(quadrant);
            this.parent = dm.createReference(parent);
            this.bucketSize = bucketSize;
            this.dataIntegrityValue = INITIAL_INTEGRITY_VALUE;
            myQuadrant = quadrant;
            children = null;
            values = null;
        }

        /**
         * Returns the next quadrant in sequence. This is used during
         * iteration so that the iterator knows how to fetch the next
         * child. This process checks increments quadrant and checks if it is
         * greater than the value of the greatest quadrant integer value
         * {@code SE_QUADRANT} to see if it has gone through all the quadrant
         * values. If it has gone through all the quadrant values, it returns
         * {@code INVALID_QUADRANT}  to indicate there are no more quadrant
         * values left to go through, otherwise it returns the incremented
         * quadrant value.This method assumes that the integer representations
         * of the quadrants are consecutive, starting at value 0.
         *
         * @return the next quadrant to examine, or {@code INVALID_QUADRANT}
         * if there are no more
         */
        static int nextQuadrant(int quadrant) {
            quadrant++;
            if (quadrant > SE_QUADRANT) {
                return INVALID_QUADRANT;
            }
            return quadrant;
        }

        /**
         * Returns the quadrant of the bounds that the point lies within.
         *
         * @param box the bounds encompassing the quadrants
         * @param point the point will be checked for whether it lies within
         *              bounds
         * @return the quadrant the point lies within, or
         * {@code INVALID_QUADRANT} if the point is out of bounds
         */
        static int determineQuadrant(BoundingBox box, Point point) {
            double[] coords = box.getCornerValues();

            // check if it is out of bounds
            if (point.x < coords[X_MIN] || point.x > coords[X_MAX] ||
                    point.y < coords[Y_MIN] || point.y > coords[Y_MAX]) {
                return INVALID_QUADRANT;
            }

            // otherwise, try to locate its quadrant
            Point middle = BoundingBox.calculateMiddle(box);
            if (point.x < middle.x) {
                if (point.y < middle.y) {
                    return SW_QUADRANT;
                } else {
                    return NW_QUADRANT;
                }
            } else {
                if (point.y < middle.y) {
                    return SE_QUADRANT;
                } else {
                    return NE_QUADRANT;
                }
            }
        }

        /**
         * Returns the leaf node associated with the given point by performing
         * a walk of the tree starting at the given root. If the leaf node is
         * {@code null} (meaning it has no values), then the parent node will be
         * returned instead.
         *
         * @param point the point belonging to the {@code Node}
         * @return the leaf {@code Node} corresponding to the given
         * {@code Point}, or the parent {@code Node} of the leaf node
         * corresponding to the given {@code Point} if the leaf node is 
         * {@code null}
         */
        Node<E> getLeafNode(Point point) {
            int quadrant;
            Node <E> node = this;

            //Keep walking down the tree towards the quadrant of point
            //until a leaf is found or a null leaf is found
            while (!node.isLeaf()) {
                quadrant = Node.determineQuadrant(node.getBoundingBox(),
                        point);
                Node<E> child = node.getChild(quadrant);

                //If child node is a null leaf, return the parent
                if (child == null) {
                    return node;
                }
                node = child;
            }
            return node;
        }

        /**
         * Converts the {@code Node} to a string representation.
         * Prints out the values if child is a leaf or if the child
         * is not a leaf prints out the children which are leaves.
         * @return a string representation of the {@code Node}
         */
        public String toString() {
            StringBuilder sb = new StringBuilder("[");

            //Print out values if node is a leaf
            if (isLeaf() && values != null) {
                Iterator<Map.Entry<Point, List<ManagedWrapper<E>>>> iter =
                        values.entrySet().iterator();

                sb.append("[");
                while (iter.hasNext()) {
                    Map.Entry<Point, List<ManagedWrapper<E>>> e =
                            iter.next();
                    sb.append(e.getKey()).append("=");
                    List<ManagedWrapper<E>> list = e.getValue();
                    int size = list.size();
                    for (int i = 0; i < size; i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(list.get(i).get());
                    }
                    if (iter.hasNext()) {
                        sb.append("] , ");
                    }
                }
            } else if (values != null) {

                //Print out children which are leaves if node is not a leaf
                for (int i = 0; i < children.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    if (children[i] == null) {
                        sb.append("Null Leaf");
                    } else if (children[i].get().isLeaf()) {
                        sb.append(children[i].get().toString());
                    } else {
                        sb.append("Non-Leaf");
                    }
                }
            }
            return sb.append("]").toString();
        }

        /**
         * Returns the children array of this node. This is intended to
         * be used by the {@code isEmpty} method.
         * @return the children of this node
         */
        ManagedReference<Node<E>>[] getChildren() {
            return children;
        }

        /**
         * Returns the quadrant that this node represents
         * @return the quadrant that this node represents
         */
        int getQuadrant() {
            return myQuadrant;
        }

        /**
         * Returns the child corresponding to the given index
         *
         * @param index the index of the child
         * @return the child corresponding to the given quadrant, or
         * {@code null} if none exists
         * @throws IndexOutOfBoundsException if the index is out of bounds
         * @throws ObjectNotFoundException if the underlying object was
         * removed without updating the children list
         */
        Node<E> getChild(int index) {
            assert (!isLeaf()) : "The node is a leaf node";

            //Check if there is any children or if the corresponding child
            //is a null leaf
            if (children == null || children[index] == null) {
                return null;
            }
            return children[index].get();
        }

        /**
         * Returns the corner points of the region corresponding to this node
         * @return the corner points of the region corresponding to this node
         */
        BoundingBox getBoundingBox() {
            return boundingBox;
        }

        /**
         * Returns {@code true} if an element exists at the given
         * {@code Point}, and {@code false} otherwise.
         *
         * @param point the coordinate to check for elements
         * @return {@code true} if an element exists at the given
         * {@code Point}, and {@code false} otherwise.
         */
        boolean contains(Point point) {

            /*Non leaf nodes cannot contain
             *any values. Also checks for case where
             *values == null which only occurs if
             *root is the only node and it is also a null leaf
             */
            if (!isLeaf() || values == null) {
                return false;
            }
            return values.containsKey(point);
        }

        /**
         * Returns this node's parent.
         * @return this node's parent, or {@code null} if it is the root
         */
        Node<E> getParent() {
            if (parent == null) {
                return null;
            }
            return parent.get();
        }

        /**
         * Returns the node's dataIntegrityValue, used by the iterator
         * to determine if elements have been added or removed from the node.
         * @return this node's dataIntegrityValue
         */
        int getIntegrityValue() {
            return dataIntegrityValue;
        }

        /**
         * Returns this node's values, which consists of a {@code Map} of
         * points and elements
         *
         * @return this node's {@code Map} of values
         */
        TreeMap<Point, List<ManagedWrapper<E>>> getValues() {
            return values;
        }

        /**
         * Determines if this node is a leaf node, which is true if the
         * children array is {@code null}.
         * @return {@code true} if it is a leaf node, and {@code false}
         * otherwise
         */
        boolean isLeaf() {
            return (children == null);
        }

        /**
         * Adds the element to the node. If the node is already populated with
         * the maximum number of values, then a split operation occurs which
         * generates children and converts this node from a leaf into an
         * intermediate node. If node is not a leaf, it must be the parent
         * of a {@code null} leaf. The {@code null} leaf will be determined and
         * replaced by a new leaf containing the new element.
         *
         * @param point the coordinate to add the element
         * @param element the element to add
         * @param allowSplit {@code true} if a split should be allowed, and
         * {@code false} otherwise
         */
        void add(Point point, E element, boolean allowSplit) {

            /*
             * Determine quadrant of the node which should contain point.
             */
            int quadrant = determineQuadrant(this.boundingBox, point);

            //Node is not a leaf, it must be parent of a null leaf.
            //Make a new leaf with the element and replace the null leaf with
            //it.
            if (!isLeaf() && children[quadrant] == null) {
                Node<E> child = new Node<E>(this, quadrant, bucketSize);
                child.add(point, element, false);
                children[quadrant] =
                        AppContext.getDataManager().createReference(child);
            } else if (isLeaf() && values == null) {

                //Initialize leaf's values if it has not been initialized yet
                values = new TreeMap<Point, List<ManagedWrapper<E>>>();
                insert(point, element);
            } else if (this.size() == bucketSize &&
                    !values.containsKey(point)) {
                //If we are at capacity and values doesn't already contain the
                //given point, perform a split and try adding again.
                splitThenAdd(point, element);
            } else {
                insert(point, element);
            }
        }

        /**
         * Calculates the size (number of points it contains) of this node
         * @return the size of this node (number of points it contains)
         */
        private int size() {
            assert (this.isLeaf()) : "Node must be a leaf";

            if (values != null) {
                return values.keySet().size();
            }
            return 0;
        }

        /**
         * Extract the old values from the node and clear it. Then make new
         * children and add the old values to the appropriate new children.
         * Finally, add the new value to the appropriate new children.
         * extract the old value and clear; it no longer should have a value
         * since this node will soon have children
         *
         * @param point the coordinate to add the element
         * @param element the element to add
         * @return {@code true} if the element was successfully added, and
         * {@code false} otherwise
         */
        private void splitThenAdd(Point point, E element) {
            Map<Point, List<ManagedWrapper<E>>> existingValues = values;
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);
            prepareNewChildren();

            // Add back the old elements to the appropriate new leaves.
            // Since we have four new quadrants, we have to add each
            // one individually to allocate it in the correct quadrant.
            Iterator<Map.Entry<Point, List<ManagedWrapper<E>>>> keyIter =
                    existingValues.entrySet().iterator();
            Point key;
            List<ManagedWrapper<E>> list;
            int quadrant;

            // Iterate through all the keys. They hold onto lists
            // which each contain at least one element
            while (keyIter.hasNext()) {
                Map.Entry mapEntry = keyIter.next();
                key = (Point) mapEntry.getKey();
                list = uncheckedCast(mapEntry.getValue());
                quadrant =
                        Node.determineQuadrant(boundingBox,
                        key);

                // Add all the items in the list at the given key
                // to the new children.
                Iterator<ManagedWrapper<E>> iter =
                        list.iterator();
                ManagedWrapper<E> ref;
                while (iter.hasNext()) {
                    ref = iter.next();
                    addValue(quadrant, key, ref.get());
                    ref.remove();
                }
            }

            // Add in the new value
            quadrant = Node.determineQuadrant(boundingBox, point);
            addValue(quadrant, point, element);
            dataIntegrityValue++;
        }

        /**
         * Add the new point and element to the child at the the given quadrant
         *
         * @param quadrant the quadrant of the child
         *        where the new point and element will be added
         * @param point the coordinate to add the element
         * @param element the element to add
         * @return {@code true} if the element was successfully added, and
         * {@code false} otherwise
         */
        private void addValue(int quadrant, Point point, E element) {
            DataManager dm = AppContext.getDataManager();
            ManagedReference<Node<E>> childRef = children[quadrant];
            Node<E> child;
            if (childRef == null) {
                child = new Node<E>(this, quadrant, bucketSize);
                children[quadrant] = dm.createReference(child);
            } else {
                child = childRef.getForUpdate();
            }
            child.add(point, element, false);
        }

        /**
         * Adds an element to the leaf node's list of elements
         *
         * @param point the point at which the new element will be inserted
         * @param element the new element to be inserted
         */
        private void insert(Point point, E element) {
            assert (isLeaf()) : "The node is a leaf";
            assert (element != null) : "The value cannot be null";
            List<ManagedWrapper<E>> list =
                    values.get(point);

            // Decide if we need to make a new instance or not
            if (list == null) {
                list =
                        new ArrayList<ManagedWrapper<E>>();
                values.put(point, list);
            }
            ManagedWrapper<E> mw = new ManagedWrapper<E>(element);

            //Determine the index(position) in the list of elements where
            //the new element should be inserted
            int index = Collections.binarySearch(list, mw,
                    new ManagedReferenceComparator());
            index = (index + 1) * (-1);
            list.add(index, mw);
            AppContext.getDataManager().markForUpdate(this);
        }

        /**
         * Prepares the children array so that new elements can be added, by
         * giving them a {@code null} value first, to make them {@code null}
         * leaves. This process sets the value of the current node to
         * {@code null} in anticipation of new children to be instantiated.
         */
        @SuppressWarnings("unchecked")
        private void prepareNewChildren() {
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);
            values = null;
            children = new ManagedReference[NUM_CHILDREN];

            for (int i = 0; i < children.length; i++) {
                children[i] = null;
            }
        }

        /* Starting at the current node, move upwards, removing nodes which
         * have children that are leaves with {@code null} values
         *
         * */
        void doRemoveWork() {
            DataManager dm = AppContext.getDataManager();

            if (isLeaf()) {

                //Stop if this is not a leaf with null values, there are no
                //nodes to remove
                if (values != null) {
                    return;
                }
            } else {
                dm.markForUpdate(this);
                int numNull = 0;
                for (int i = 0; i < children.length; i++) {
                    Node<E> child = getChild(i);

                    //Check for the number of null children and remove
                    //any children with null values
                    if (child == null) {
                        numNull++;
                    } else if (child.isLeaf() && child.getValues() == null) {
                        children[i] = null;
                        dm.removeObject(child);
                        numNull++;
                    }
                }

                //Clear the list of children if all of them are null
                if (numNull == NUM_CHILDREN) {
                    children = null;
                } else {
                    //Can stop moving up to remove leaves with null values if
                    //this node does not have all null children, since this
                    //node will not become a leaf with null values.
                    return;
                }
            }

            //Perform remove work upwards until root is reached
            if (parent != null) {
                parent.getForUpdate().doRemoveWork();
            }
        }

        /**
         * Removes the element from the tree if it is in the tree.
         * @param coordinate the coordinate of the element to remove
         * @param index index of the element to be removed in the point's list
         * @param doRemoveWork used to determine whether changes should be
         *                      propagated upwards
         * @return the element that was removed, or {@code null} if none was
         * removed
         */
        E remove(Point coordinate, int index, boolean doRemoveWork) {
            E old = removeEntry(coordinate, index);

            // If we found an entry, remove it and return it
            if (old != null) {
                dataIntegrityValue++;
                if (doRemoveWork) {
                    doRemoveWork();
                }
                return old;
            }
            return null;
        }

        /**
         * Retrieves and removes the {@code entry} from the
         * {@code ConcurrentQuadTree} that corresponds to the
         * given point and index.
         *
         * @param point the coordinate of the {@code entry}
         * @param index index of the element to be removed in the point's list
         * @return the entry, or {@code null} if none matches the given
         * coordinate
         */
        private E removeEntry(Point point, int index) {
            assert (this.isLeaf()) : "The node is not a leaf";

            // If there was no value stored
            // return null since we cannot remove anything from this node
            if (values == null) {
                return null;
            }

            List<ManagedWrapper<E>> list =
                    values.get(point);

            if (list == null) {
                return null;
            }
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);

            // extract the element from the list, and delete the wrapper
            ManagedWrapper<E> ref = list.remove(index);
            E old = ref.get();
            ref.remove();

            // if list is now empty, remove the key
            if (list.isEmpty()) {
                values.remove(point);

                // set the map to null if we removed the last entry
                if (values.isEmpty()) {
                    values = null;
                }
            }
            return old;
        }
    }
}
