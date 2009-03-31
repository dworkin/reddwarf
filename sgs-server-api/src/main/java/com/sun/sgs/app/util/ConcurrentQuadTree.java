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
 * Therefore, each {@code Point} inserted into the {@code ConcurrentQuadTree}
 * will be located in a region containing no more than the specified number of
 * points for each leaf node. This parameter is defined in the constructor of
 * the {@code ConcurrentQuadTree}, and is referred to as the {@code bucketSize}.
 * <p>
 * This type of organization is best interpreted as a tree whereby "deeper"
 * nodes correspond to smaller regions. Elements can only exist at the leaf
 * nodes; if a node overflows its bucket size, then it splits into smaller
 * regions at an incremented depth and the elements are reallocated.
 * <p>
 * A {@code ConcurrentQuadTree} enables a two-dimensional space to efficiently
 * hold onto a certain number of {@code Point}s using a relatively simple and
 * low-cost scheme. The {@code ConcurrentQuadTree} does support null elements;
 * that is, calling {@code put(x, y, null)} is permitted. The
 * {@code ConcurrentQuadTree} also supports multiple elements at the same
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
 * sub-region (also known as an {@code boundingBox}) of the tree. Therefore,
 * the order of elements in the iteration is not guaranteed to be
 * the same for each iterator constructed since a different {@code boundingBox}
 * may be used for each iterator. 
 *
 * <p>
 * The iterator used to go through elements in the tree is serializable but
 * not a ManagedObject. There may be many elements that will need to be
 * iterated over which could take more time than the minimum allowed for a
 * single {@link Task}. As a result, several tasks may be needed to iterate
 * over all the necessary elements, requiring the iterator to be serialized
 * between each task. 
 *
 * <p>
 * To allow for concurrency, the iterator throws
 * {@link ConcurrentModificationException} if the current leaf it was on
 * has split or was replaced by a new leaf because the iterator will no
 * longer be able to accurately determine its position in the tree. The iterator
 * will also throw {@link CurrentConcurrentRemovedException} if the current
 * element no longer exists and it was not removed by the iterator through
 * a call to {@code remove()}. Whenever an iterator has just been deserialized,
 * it is recommended that {@code hasCurrent()} and {@code hasNext()} be called
 * before a call to {@code current()} and {@code next()} or 
 * {@code nextNoReturn()} respectively since both the current or next element
 * may have been removed concurrently while the iterator was serialized. This
 * will allow for more concurrency by avoiding exceptions when iterating
 * through the {@code ConcurrentQuadTree} if it is being modified.
 *
 *
 *
 * @param <E> the type of element the {@code ConcurrentQuadTree} is to hold
 */
public class ConcurrentQuadTree<E> implements QuadTree<E>, Serializable,
        ManagedObjectRemoval {

    /**
     * The leaves of the tree are either a node or null. When a leaf is null,
     * it is a leaf which contains no values. To save space, a leaf with
     * no values is itself null rather than a node with null values, which would
     * take up more space. The only exception to this rule is a tree with a 
     * single leaf that has no values in which case the leaf (root) has null
     * values.
     *
     * To allow concurrency, this data structure does not propagate changes from
     * leaf nodes toward the root node. Since the tree does not grow upwards,
     * nodes that have been created permanently maintain their tree depth,
     * unless they are removed (in other words, collapsed or pruned). 
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
    /**
     * If non-null, a runnable to call when a task that asynchronously removes
     * nodes is done -- used for testing. Note that this method is called
     * during the transaction that completes the removal.
     */
    private static volatile Runnable noteDoneRemoving = null;
    /** The maximum capacity of a node */
    private final int bucketSize;
    /**
     * An object consisting of two corners that comprise the box representing
     * the sample space
     */
    private final BoundingBox boundingBox;
    /** The root element of the quadtree */
    private ManagedReference<Node<E>> root;

    /**
     * A five-argument constructor which defines a Quadtree with a bucketSize
     * supplied as a parameter. The area corresponding to this instance is
     * defined by the supplied coordinates whereby ({@code x1}, {@code y1})
     * represent the first {@code Point} and ({@code x2}, {@code y2})
     * represents the second {@code Point} of the defining {@code BoundingBox}.
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
     * parameter into the new {@code ConcurrentQuadTree}. For trees with many elements,
     * this operation can take a long time since all elements need to be
     * iterated through. If the tree is very large, it may take more time than
     * the maximum allowed for a single task to copy the tree.
     *
     * @param tree the {@code ConcurrentQuadTree} whose elements are to be added into the new
     * {@code ConcurrentQuadTree}
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
        return (root.get().getChildren().isEmpty() &&
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

        Node<E> leaf = root.get().getLeafNode(point);

        //Keep removing elements from point while there are still elements
        //left
        while (leaf.isLeaf() && leaf.remove(point, 0) != null) {
            result = true;
        }
        return result;
    }

    /**
     * Returns the {@code ManagedObject} which is being referenced by the
     * argument
     *
     * @param ref the reference whose value to return
     * @return the value of the reference, or null if either the reference is
     * {@code null} or the object no longer exists
     */
    private static Object getReferenceValue(ManagedReference<?> ref) {
        if (ref != null) {
            return ref.get();
        }
        return null;
    }

    /**
     * Casts the object to the desired type in order to avoid unchecked cast
     * warnings
     *
     * @param <E> the type to cast to
     * @param object the object to cast
     * @return the casted version of the object
     */
    @SuppressWarnings("unchecked")
    private static <E> E uncheckedCast(Object object) {
        return (E) object;
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
        return BoundingBox.getCornerValues(boundingBox);
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

    /* Returns a new {@code ManagedReferenceComparator}, used to avoid
     * unchecked conversion warnings
     * */
    @SuppressWarnings("unchecked")
    private static ManagedReferenceComparator getComparator() {
        return new ManagedReferenceComparator();
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
     * tree because the structure of the tree may have significantly changed.
     * 
     * If {@code current()} is called and the current element the iterator
     * was on no longer exists but the element was not removed by the iterator,
     * then a {@code CurrentConcurrentRemovedException} will be thrown instead
     * of an {@code IllegalStateException}. This is to let the user know the
     * difference between the iterator itself removing the element and the
     * element being removed by a different thread or while the iterator was
     * serialized.
     *
     * The iterator is optimized to not search needlessly down a particular 
     * branch if the {@code boundingBox} specified by the iterator does not
     * partially or fully contain leaves of that particular branch.
     * The iterator simply stops at the root of the branch after comparing the
     * root's {@code boundingBox} with the {@code boundingBox} given to the
     * iterator.
     *
     */
    static class ElementIterator<E> implements QuadTreeIterator<E>,
            Serializable {

        private static final long serialVersionUID = 4L;
        /** A region specific to this iterator */
        private final BoundingBox box;
        /** Whether the current entry exists */
        private boolean currExists;
        /** The current node being examined */
        private ManagedReference<Node<E>> current;
        /**
         * The next node to be examined. This can be equal to {@code current}
         * if the next entry is located in the same node.
         */
        private ManagedReference<Node<E>> next;
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
         * The next entry (the entry to be returned from a future call to
         * {@code next})
         */
        private Entry<E> nextEntry;
        /**
         * A {@code Point} object representing the next point about to be
         * examined in a node's underlying {@code Map}
         */
        private Point nextPoint;
        /**
         * A {@code Point} object representing the current point being
         * examined in a node's underlying {@code Map}
         */
        private Point currentPoint;
        /**
         * Index of the next element in the list
         */
        private int nextIndex;

        /**
         * Two-argument constructor which permits specification of a bounding
         * box specific to the iterator.
         *
         * @param root the root node of the {@code ConcurrentQuadTree}, used to locate the first
         * child
         * @param box the region which specifies the qualified entries that
         * this iterator is to iterate over; a value of {@code null} means all
         * entries are valid (no bounding box)
         */
        ElementIterator(ManagedReference<Node<E>> root, BoundingBox box) {
            this.box = box;
            this.root = root;
            current = null;
            currExists = false;
            entry = null;
            nextPoint = null;
            nextIndex = 0;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasCurrent() {

            //Check if the original leaf, point and finally element still exists
            try {
                Node<E> currentNode = current.get();
                TreeMap<Point, List<ManagedReference<ManagedSerializable<E>>>>
                        map = currentNode.getValues();
                if (map != null && map.containsKey(currentPoint) &&
                        map.get(currentPoint).contains(entry.getValue())) {
                    return true;
                }
            } catch (ObjectNotFoundException onfe) {
            }
            return false;
        }

        /**
         * Returns <tt>true</tt> if the iteration has more elements. (In
         * other words, returns <tt>true</tt> if <tt>next</tt> would
         * return an element rather than throwing an exception.)
         *
         * @return <tt>true</tt> if the iterator has more elements.
         */
        public boolean hasNext() {
            //Try to reload the next element, in case it has changed
            reload();
            return (nextEntry != null);
        }

        /**
         * Used to reload the next entry, in case the next element has changed.
         */
        private Entry<E> reloadNext() {
            Node<E> currentNode;
            try {
                currentNode = current.get();

                //If the current leaf the iterator is on has not split
                //to become a non-leaf node
                if (currentNode.isLeaf()) {

                    TreeMap<Point, 
                            List<ManagedReference<ManagedSerializable<E>>>> map
                            = currentNode.getValues();

                    //Assume there are still elements left to be iterated over
                    //on the current leaf and at the current point
                    next = current;
                    nextPoint = currentPoint;

                    //currentNode is empty root, there is no nextEntry
                    if (map == null) {
                        return null;
                    }

                    //If the current point still exists
                    if (map.containsKey(currentPoint)) {
                        List<ManagedReference<ManagedSerializable<E>>> list =
                                map.get(currentPoint);
                        nextIndex =
                                Collections.binarySearch(list, entry.getValue(),
                                getComparator());
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
                    } else {
                        //If currentPoint has been removed, find the next
                        //valid point. If there are no more valid points left
                        //return null
                        nextPoint = map.higherKey(currentPoint);
                        getNextPoint();
                        if (nextPoint == null) {
                            return null;
                        }
                    }
                    //Get the next element
                    return getNextElement();
                } else {
                    //Throw ConcurrentModificationException if the current
                    //leaf has split
                    throw new ConcurrentModificationException("Current node " +
                            "had split!");
                }
            } catch (ObjectNotFoundException onfe) {

                //If current leaf the iterator was on has been removed,
                //get the parent of the node that should contain currentPoint
                currentNode = root.get().getLeafNode(currentPoint);

                //If the parent is a root and also a leaf
                if (currentNode.getParent() == null && currentNode.isLeaf()) {
                    //If the root is empty, return null, there are no more
                    //elements to iterate over
                    if (currentNode.getValues() == null) {
                        return null;
                    } else {
                        //If the root still contains values, try to find the 
                        //next element in root's values
                        next = root;
                        nextPoint = currentPoint;
                        getNextPoint();
                        return getNextElement();
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
                        currentPoint));
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
                    next = getNextQualifiedLeafNode(currentNode);
                }

                //If a qualified leaf was found, look through it for a
                //qualified point, findNextQualifiedPoint also
                //looks for a qualified point in other leaves if one isn't found
                //in this qulaified leaf
                nextPoint = null;
                if (next != null) {
                    findNextQualifiedPoint();
                    nextIndex = 0;
                }

                //If no qualified point was found in the entire tree,
                //there is no more elements to iterate over
                if (nextPoint == null) {
                    return null;
                }
                //Get the next element if a qualified next point was found
                return getNextElement();
            }
        }

        /**
         * Sets nextPoint to the next qualified point by first trying to
         * find it in the next qualified leaf node, then other qualified
         * leaf nodes if one cannot be found in the next qualified leaf node
         */
        private void getNextPoint() {
            assert (next != null) : "Next leaf node cannot be null";
            Node<E> nextNode = next.get();
            //Search through the next leaf node, trying to find a
            //qualified point
            while (nextPoint != null && !box.contains(nextPoint)) {
                nextPoint = nextNode.getValues().higherKey(nextPoint);
            }

            //If next leaf node does not contain a qulaified point, get
            //the next qualified leaf node and try to find a qualified point
            //again
            if (nextPoint == null) {
                next = getNextQualifiedLeafNode(nextNode);
                findNextQualifiedPoint();
            }
            //Reset next index of the iterator's position in the list
            //of elements corresponding to the next point
            nextIndex = 0;
        }

        /**
         * Loads the next element when next() or nextNoReturn() is called
         * for the very first time after this iterator has been initialized.
         */
        private void loadFirst() {

            //Get the first qualified leaf node
            next = getFirstQualifiedLeafNode(root.get());
            current = next;

            //find the first qualified point
            findNextQualifiedPoint();
            Node<E> node = uncheckedCast(getReferenceValue(
                    next));

            //Stop, if there are no qualified leaf nodes
            //set nextEntry to null
            if (node == null) {
                nextEntry = null;
                return;
            }

            
            //Get the first element
            nextEntry = getNextElement();
            current = null;
        }

        /**
         * Starting at the leaf node "next", search through the entire tree
         * for the next qualified point
         */
        private void findNextQualifiedPoint() {
            Node<E> node = uncheckedCast(getReferenceValue(
                    next));

            //Keep looping until a qualified point is found or there are no
            //more qualified leaf nodes left
            while (nextPoint == null && node != null) {
                nextPoint = node.getValues().firstKey();
                //Go through the points in the leaf node lookin for
                //qualified points
                while (nextPoint != null && !box.contains(nextPoint)) {
                    nextPoint = node.getValues().higherKey(nextPoint);
                }

                //Get the next qualified leaf node if the previous leaf node
                //did not contain any qualified points
                if (nextPoint == null) {
                    next = getNextQualifiedLeafNode(next.get());
                    node = uncheckedCast(getReferenceValue(
                            next));
                }
            }
        }

        /** Gets the next qualified element
         * @return An Entry containing the next element
         */
        private Entry<E> getNextElement() {
            assert (next != null) : "Next leaf node cannot be null";
            Node<E> nextNode = next.get();
            List<ManagedReference<ManagedSerializable<E>>> list =
                    nextNode.getValues().get(nextPoint);

            //Get the next element in the list if there are any elements left
            if (nextIndex < list.size()) {
                return new Entry(nextPoint, list.get(nextIndex));
            } else {

                //Get the next qualified point if there are no more elements
                //leftin the list
                nextPoint = nextNode.getValues().higherKey(nextPoint);
                getNextPoint();
                if (nextPoint != null) {
                    list = next.get().getValues().get(nextPoint);
                    return new Entry(currentPoint, list.get(nextIndex));
                }
            }
            return null;
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
            loadNext();
            return entry.getValue().get().get();
        }

        /**
         * Reloads the next element depending on whether it is the very first
         * element to be loaded
         * */
        private void reload() {
            //If the first element has not loaded yet, call loadFirst() again
            //to reload it
            if (current == null) {
                nextPoint = null;
                loadFirst();
            } else {
                nextEntry = reloadNext();
            }
        }

        /**
         * Sets up the next element without returning it. The two
         * "next" methods will decide whether they want to return
         * the value or not
         */
        private void loadNext() {
            //Reload next element in case it has changed
            reload();

            //Check if there are any elements left to iterate over
            if (nextEntry == null) {
                throw new NoSuchElementException();
            }

            currExists = true;

            // fetch the next element and adjust the
            // references accordingly.
            entry = nextEntry;
            currentPoint = nextPoint;
            current = next;
        }

        /**
         * {@inheritDoc}
         * @throws ConcurrentModificationException if the next node in the
         * sequence was removed, or after deserialization, if this node was
         * modified or removed.
         */
        public void nextNoReturn() {
            loadNext();
        }

        /**
         * {@inheritDoc}
         * @throws CurrentConcurrentRemovedException if the current element
         *         no longer exists but was not removed by the iterator
         *  
         */
        public E current() {

            // Check if current element has been removed or next() method has
            // not been called
            checkCurrentEntry();
            return entry.getValue().get().get();
        }

        /**
         * {@inheritDoc}
         */
        public double currentX() {
            return currentPoint.getX();
        }

        /**
         * {@inheritDoc}
         */
        public double currentY() {
            return currentPoint.getY();
        }

        /**
         * Checks if the current entry exists.
         */
        private void checkCurrentEntry() {

            //If the iterator has removed the current element or the very first
            //element has never been loaded
            if (!currExists) {
                throw new IllegalStateException("There is no current" +
                        "element.");
            }

            //If currExists is still set to true, but the current element
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
         * no longer exists and was no removed by the iterator
         * @throws IllegalStateException if the <tt>next</tt> method has
         * not yet been called or if the current element has been removed
         * already
         */
        public void remove() {
            checkCurrentEntry();
            currExists = false;
            List<ManagedReference<ManagedSerializable<E>>> list =
                    current.get().getValues().get(currentPoint);

            //Find the index of the element last returned and remove it
            int index =
                    Collections.binarySearch(list, entry.getValue(),
                    getComparator());
            current.get().remove(currentPoint, index);
        }

        /**
         * Retrieves the first non-null leaf node in a tree, rooted
         * by {@code node}. If the box
         * parameter is null, then it is assumed that there is no BoundingBox
         * limiting the area of search.
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
            if (node.isLeaf()) {
                return AppContext.getDataManager().createReference(node);
            }

            // Iterate through all the children in a depth-first
            // search looking for the first encountered non-null leaf,
            // only searching in children whose bounding box intersects with
            // the specified bounding box
            for (int i = 0; i < Node.NUM_CHILDREN; i++) {
                Node<E> child = node.getChild(i);
                if (child != null && (box == null || checkBoxRegion(box,
                        child.getBoundingBox()))) {
                    return getFirstLeafNode(child, box);
                }
            }
            return null;
        }

        /**
         * Returns the next node using a depth-first traversal scheme. If we
         * try to retrieve the next node while on the root, {@code null} is
         * returned, specifying our exit condition. If the box parameter is
         * null, then it is assumed that there is no BoundingBox limiting the
         * area of search.
         *
         * @param node the current node, whose next element we are interested
         * in finding
         * @param box  the boundingBox to search in for the leaf node
         * @return the next node in the depth-first traversal, or {@code null}
         * if none exists
         */
        static <E> ManagedReference<Node<E>> getNextLeafNode(Node<E> node,
                BoundingBox box) {
            Node<E> parent = node.getParent();

            // End condition: we reached the root; there is no next leaf node
            if (parent == null) {
                return null;
            }

            // Try and fetch siblings. If all the siblings have been fetched,
            // go upwards and fetch the siblings of parent nodes
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
                            checkBoxRegion(box, child.getBoundingBox()))) {

                        // Dig deeper if child is not a leaf,
                        // or if it is a leaf return it. Otherwise,
                        // keep searching this level.
                        if (!child.isLeaf()) {
                            ManagedReference<Node<E>> possibleNode =
                                    getFirstLeafNode(child, box);
                            if (possibleNode != null)
                            {
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
            // Go back up a level and search parent's siblings
            return getNextLeafNode(parent, box);
        }

        /**
         * Given the node parameter, return the first non-null leaf
         * which contains values in the defined region.
         *
         * @param node the node to start the search
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
                return getNextQualifiedLeafNode(leaf.get());
            }
            return null;
        }

        /**
         * Given the node parameter, return the next leaf node in succession
         * (using a depth-first search) which has values in the defined
         * region. A leaf node is "qualified" as long as the node has values
         * to iterate over (non-null leaf)
         *
         * @param node the current node being examined
         * @return the next node containing "qualified" entries, or
         * {@code null}
         * if none exists
         */
        private ManagedReference<Node<E>> getNextQualifiedLeafNode(Node<E> node)
        {
            ManagedReference<Node<E>> child = getNextLeafNode(node, this.box);

            // Go through all the nodes of the tree, child is only null if
            // the entire tree has been searched
            while (child != null) {

                // Only return non-null leaves
                if (child.get() != null) {
                    return child;
                }

                // Get the next node
                child = getNextLeafNode(child.get(), this.box);
            }
            return null;
        }

        /**
         * Checks if a part of or all of boxTwo is contained within boxOne.
         *
         * @param boxOne The first {@code BoundingBox} to be examined
         * @param boxTwo The second {@code BoundingBox} to be examined
         * @return true if a part of or all of boxTwo is contained within
         * boxOne and false if not
         */
        private static boolean checkBoxRegion(BoundingBox boxOne,
                BoundingBox boxTwo) {
            return (boxOne != null &&
                    boxOne.getContainment(boxTwo) !=
                    BoundingBox.Containment.NONE);
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
        private boolean firstTime;

        /**
         * The constructor of the clear task, which requires the root element
         * of the tree to begin the traversal
         *
         * @param root the root of the tree, which must not be {@code null}
         */
        AsynchronousClearTask(ManagedReference<Node<E>> root) {
            assert (root != null) : "The root parameter must not be null";
            current = ElementIterator.getFirstLeafNode(root.get(), null);
            currentPoint = null;
            firstTime = true;
            this.root = root;
        }

        /**
         * The entry point into the task
         */
        public void run() {

            // Mark task for update
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);
            //If the task is run for the first time, setup first currentPoint
            if (firstTime) {
                currentPoint = getFirstPoint(current);
                firstTime = false;
            }

            // Check if there is more work to be done. If so, reschedule.
            if (doWork()) {
                AppContext.getTaskManager().scheduleTask(this);
            } else {
                // If all the clearing work has been done, remove the root
                // and then this task
                dm.removeObject(root.get());
                dm.removeObject(this);

                Runnable r = noteDoneRemoving;
                if (r != null) {
                    r.run();
                }
            }
        }

        /**
         * Removes MAX_OPERATION number of elements from the
         * {@code ConcurrentQuadTree} using a post-order depth-first traversal
         * and returns {@code true} if there is more work to be done. If
         * there are no more elements to remove, then it will
         * return {@code false}.
         *
         * @return {@code true} if more work needs to be done, and
         * {@code false} if there are no more elements to remove.
         */
        private boolean doWork() {
            int count = 0;
             TreeMap<Point, List<ManagedReference<ManagedSerializable<E>>>>
                     values;
            // Loop to remove elements. We'll stop when we reach the
            // end, if we max out our operations or if
            // currentPoint is null meaning there are no
            // elements to iterate over, whichever comes first
            while (current != null && ++count < MAX_OPERATIONS &&
                    currentPoint != null) {
                
                // If we ran out of elements to remove, fetch the next
                // Point to try and remove.
                if (removeElement()) {
                    try {
                        values = current.get().getValues();
                        //If the node is the root and its values is null,
                        //there are no more elements to remove
                        if (root.getId().equals(current.getId()) &&
                                values == null) {
                            return false;
                        }
                        //If all the elements in the list associated with
                        //the currentPoint have been removed, then currentPoint
                        //must have been removed, fetch next point
                        if (!values.containsKey(currentPoint)) {
                            currentPoint = values.higherKey(currentPoint);
                        }
                    } catch (ObjectNotFoundException onfe) {
                        //If current leaf no longer exists, it must have been
                        //removed after all of its elements were cleared,
                        //fetch next leaf
                        current = ElementIterator.getFirstLeafNode(root.get(),
                                null);

                        //If only leaf left is the root, there are no more
                        //leaves left, set current to null, otherwise get the
                        //first point of the next leaf
                        if (!root.getId().equals(current.getId())) {
                            currentPoint = getFirstPoint(current);
                         } else {
                            current = null;
                        }
                    }
                }
            }
            //Return whether or not there are more leaves left to be cleared
            return (current != null);
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
            TreeMap<Point, List<ManagedReference<ManagedSerializable<E>>>>
                    values = node.get().getValues();

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
            return (current.get().delete(currentPoint));
        }
    }

    /**
     * A comparator used to define behaviour when comparing two
     * ManagedObjects. Used to keep list of elements corresponding to a point
     * sorted.
     */
    static class ManagedReferenceComparator implements Comparator,
    Serializable {

        private static final long serialVersionUID = 2L;
        /**
         * Compares the {@code ManagedObject} Id of two {@code ManagedObject}s
         * @param obj1 a {@code ManagedReference}
         * @param obj2 another {@code ManagedReference}
         * @return positive integer if obj1 has a {@code ManagedObject} Id
         * greater than obj2, 0 if the two {@code ManagedObject}s have the
         * same Id and a negative integer otherwise
         */
        public int compare(Object obj1, Object obj2) {
            ManagedReference mr1 = (ManagedReference) obj1;
            ManagedReference mr2 = (ManagedReference) obj2;
            return mr1.getId().compareTo(mr2.getId());
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
        public static final byte TOTAL_CORNERS = 4;

        //Indices of the bounds array with bounds[MIN_POINT] point
        //having the smaller x and y values, while bounds[MAX_POINT] point
        //has the larger x and y values
        private static final int MIN_POINT = 0;
        private static final int MAX_POINT = 1;
        private static final int TOTAL_X = 0;
        private static final int TOTAL_Y = 1;

        /**
         * Specifies the degree of containment of another object, usually a
         * {@code Point} or another {@code BoundingBox}. This is used
         * primarily when comparing two bounding boxes with one another.
         */
        static enum Containment {

            /** Denotes no containment in the bounding box (disjoint) */
            NONE,
            /**
             * Denotes partial containment (or an intersection) of the
             * bounding box or the other bounding box is a subset of
             * this bounding box
             */
            PARTIAL,
            /** Denotes total containment (or domination) of the bounding box */
            FULL;
        }
        /** An array of two points to represent the bounding box area */
        Point[] bounds;

        /**
         * Constructs a new {@code BoundingBox} given two points representing
         * diagonal corners
         *
         * @param a one of the corners of the {@code BoundingBox}
         * @param b the other (diagonal) corner of the {@code BoundingBox}
         */
        BoundingBox(Point a, Point b) {
            bounds = new Point[]{a, b};
            organizeCoordinates();
        }

        /**
         * Creates the bounds given the parent's bounds and our intended
         * quadrant
         *
         * @param parentBoundingBox the parent's bounding box
         * @param quadrant the quadrant to determine
         * @return the bounds for this node
         */
        static BoundingBox createBoundingBox(BoundingBox parentBoundingBox,
                int quadrant) {
            // get the individual coordinates
            double[] coords = getCornerValues(parentBoundingBox);

            // Create the middle of the region, which is guaranteed to be a
            // corner of the node's bounds. Time to find the other corner
            Point middle = calculateMiddle(parentBoundingBox);

            Point corner;
            switch (quadrant) {
                case Node.NW_QUADRANT:
                    corner = new Point(coords[X_MIN], coords[Y_MAX]);
                    break;
                case Node.NE_QUADRANT:
                    corner = new Point(coords[X_MAX], coords[Y_MAX]);
                    break;
                case Node.SW_QUADRANT:
                    corner = new Point(coords[X_MIN], coords[Y_MIN]);
                    break;
                case Node.SE_QUADRANT:
                default:
                    corner = new Point(coords[X_MAX], coords[Y_MIN]);
            }
            return new BoundingBox(middle, corner);
        }

        /**
         * Organizes the coordinates of the two Points given to the
         * {@code BoundingBox} constructor such that bounds[MIN_POINT]
         * represents the point with smaller x and y coordinates while
         * bounds[MAX_POINT] represents the point with larger x and y
         * coordinates
         *
         */
        private void organizeCoordinates() {
            Point[] newBounds = {
                new Point(Math.min(bounds[MIN_POINT].x,
                bounds[MAX_POINT].x),
                Math.min(bounds[MIN_POINT].y, bounds[MAX_POINT].y)),
                new Point(Math.max(bounds[MIN_POINT].x,
                bounds[MAX_POINT].x),
                Math.max(bounds[MIN_POINT].y, bounds[MAX_POINT].y))
            };
            bounds = newBounds;
        }

        /**
         * Returns the coordinates of the given {@code BoundingBox} as an
         * array so that the minimum and maximum values can be
         * obtained easily. The array's values are best accessed using
         * the fields {@code X_MIN}, {@code X_MAX}, {@code Y_MIN},
         * or {@code Y_MAX} as array indices.
         *
         * @param box the region, represented as an array of two
         * {@code Points}
         * @return an array which contains individual coordinates
         */
        static double[] getCornerValues(BoundingBox box) {
            double[] values = new double[4];
            values[X_MIN] = box.bounds[MIN_POINT].x;
            values[Y_MIN] = box.bounds[MIN_POINT].y;
            values[X_MAX] = box.bounds[MAX_POINT].x;
            values[Y_MAX] = box.bounds[MAX_POINT].y;
            return values;
        }

        /**
         * Determines the parameter's degree of containment in relation to the
         * region specified by this object. The object either is fully
         * contained, partially contained, or not contained at all.
         *
         * @param anotherBoundingBox the region to check containment
         * @return {@code Containment.FULL} if this region completely contains
         * the parameter region, {@code Containment.PARTIAL} if this region
         * contains a portion of the parameter region, or
         * {@code Containment.NONE} if there are no intersections of the two
         * regions
         */
        public Containment getContainment(BoundingBox anotherBoundingBox) {
            double[] coords = getCornerValues(this);
            double[] arg = getCornerValues(anotherBoundingBox);

            //Calculate values to determine the extent to which this box
            //is contained within anotherBoundingBox and vice versa
            byte[] totalValue = checkBoundsValue(coords, arg);
            byte[] subTotalValue = checkBoundsValue(arg, coords);


            /*
             * For total containment, all points should be contained. For
             * partial containment, at least one X and Y coordinate need to be
             * contained; hence both totals should be larger than 0 or there
             * is the case where this boundingBox is fully
             * contained(subset) within anotherBoundingbox.
             * Otherwise, there is no containment.
             */
            if (totalValue[TOTAL_X] == 2 && totalValue[TOTAL_Y] == 2) {
                return Containment.FULL;
            } else if ((totalValue[TOTAL_X] > 0 && totalValue[TOTAL_Y] > 0) ||
                    (subTotalValue[TOTAL_X] == 2 &&
                    subTotalValue[TOTAL_Y] == 2)) {
                return Containment.PARTIAL;
            } else {
                return Containment.NONE;
            }
        }

        /**
         * Determines a value to represent whether or not the region defined by
         * coordinates of args is within the region defined by coords. Note:
         * this method will not return an appropriate value if the region
         * defined by coords is smaller and contained within (subset of)
         * the region defined by arg.
         *
         * @param coords coordinates representing a region
         * @param arg coordinates representing another region
         * @return a byte array with the appropriate x and y values, if
         * x and y are both 2, then the args region is completely within the
         * coords region, if x and y are both greater than 0, then args is
         * partially contained (intersects) within coords, otherwise args
         * and coords are disjoint
         */
        static byte[] checkBoundsValue(double[] coords, double[] arg) {
            // Increment every time we have a coordinate contained in the
            // bounds of "this" bounding box
            byte totalX = 0;
            byte totalY = 0;
            totalX +=
                    (isContained(arg[X_MIN], coords[X_MIN], coords[X_MAX])
                    ? 1 : 0);
            totalX +=
                    (isContained(arg[X_MAX], coords[X_MIN], coords[X_MAX])
                    ? 1 : 0);
            totalY +=
                    (isContained(arg[Y_MIN], coords[Y_MIN], coords[Y_MAX])
                    ? 1 : 0);
            totalY +=
                    (isContained(arg[Y_MAX], coords[Y_MIN], coords[Y_MAX])
                    ? 1 : 0);
            return new byte[]{totalX, totalY};
        }

        /**
         * Determines if the parameter {@code arg} is contained between the
         * two bounds.
         *
         * @param arg the argument to check for containment
         * @param bound1 one of the coordinate bounds
         * @param bound2 another coordinate bound
         * @return {@code true} if the parameter is contained, and
         * {@code false} otherwise
         */
        static boolean isContained(double arg, double bound1, double bound2) {
            return (Math.min(bound1, bound2) <= arg && Math.max(bound1,
                    bound2) >= arg);
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
            // Since a point cannot be partially contained, if it is not
            // contained, return false; otherwise return true;
            BoundingBox box = new BoundingBox(point, point);
            if (getContainment(box) == Containment.NONE) {
                return false;
            }
            return true;
        }

        /**
         * Returns a Point representing the middle of the region
         *
         * @param box the corner points specifying the region for which to
         * find the middle
         * @return the {@code Point} representing the middle
         */
        static Point calculateMiddle(BoundingBox box) {
            double[] d = getCornerValues(box);
            return new Point(d[X_MIN] + ((d[X_MAX] - d[X_MIN]) / 2),
                    d[Y_MIN] + ((d[Y_MAX] - d[Y_MIN]) / 2));
        }

        /**
         * Returns a Point representing the middle of the region
         *
         * @return the {@code Point} representing the middle
         */
        public String toString() {
            StringBuffer strBuf = new StringBuffer();
            for (int i = 0; i < bounds.length; i++) {
                strBuf.append(bounds[i].toString());
            }
            return strBuf.toString();
        }
    }

    /**
     * Represents an entry in the {@code ConcurrentQuadTree} by maintaining a
     * {@code ManagedReference} to the stored object and its coordinates
     * in the form of a {@code Point} object.
     */
    static class Entry<E> implements Serializable, ManagedObject {

        private static final long serialVersionUID = 5L;
        /** The coordinate of the element */
        final Point coordinate;
        /** The {@code ManagedReference} to the value of the element */
        private ManagedReference<ManagedSerializable<E>> valueRef;

        /**
         * Two-argument constructor which creates an entry given a coordinate
         * and {@code ManagedReference} to the value. The value must not be
         * {@code null}.
         *
         * @param coord the coordinate of the new entry
         * @param valueRef the value of the new entry, which cannot be
         * {@code null}
         */
        Entry(Point coord, ManagedReference<ManagedSerializable<E>> valueRef) {
            assert (valueRef != null) : "Value cannot be null";
            coordinate = coord;
            this.valueRef = valueRef;
        }

        /**
         * Sets the value of the entry, in the event it is changed during a
         * {@code set()} operation.
         *
         * @param valueRef the new value, which must not be {@code null}
         */
        void setValue(ManagedReference<ManagedSerializable<E>> valueRef) {
            assert (valueRef != null) : "Value cannot be null";
            AppContext.getDataManager().markForUpdate(this);
            this.valueRef = valueRef;
        }

        /**
         * Returns the value of the entry which cannot be {@code null}.
         *
         * @return the value of the entry
         */
        ManagedReference<ManagedSerializable<E>> getValue() {
            return valueRef;
        }

        /**
         * Converts the {@code Entry} to a string representation.
         *
         * @return a string representation of the {@code Entry}
         */
        public String toString() {
            return valueRef.get().get().toString();
        }
    }

    /**
     * A class that represents a point as an ({@code x}, {@code y})
     * coordinate pair.
     */
    public static class Point implements Serializable, Comparable<Point> {

        private static final long serialVersionUID = 7L;
        /** the x-coordinate */
        final double x;
        /** the y-coordinate */
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
        public double getX() {
            return this.x;
        }

        /**
         * Returns the y coordinate of this point.
         * @return the y coordinate of this point
         * */
        public double getY() {
            return this.y;
        }

        /**
         * Converts the {@code Point} to a string representation.
         *
         * @return a string representation of the {@code Point}
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            sb.append(x);
            sb.append(", ");
            sb.append(y);
            sb.append(")");
            return sb.toString();
        }

        /**
         * Calculates the distance between two points using the Pythagorean
         * Theorem.
         *
         * @param a a point
         * @param b the other point
         * @return the (non-rounded) distance between the two points
         */
        static double getDistance(Point a, Point b) {
            double deltaX = Math.abs(a.x - b.x);
            double deltaY = Math.abs(a.y - b.y);
            return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
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
        /** the parent of this node */
        private final ManagedReference<Node<E>> parent;
        /** the depth of the node, which will not change */
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
        private TreeMap<Point, List<ManagedReference<ManagedSerializable<E>>>>
                values;
        /** references to the children */
        private ArrayList<ManagedReference<Node<E>>> children;

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
            myQuadrant = INVALID_QUADRANT;
            children = new ArrayList<ManagedReference<Node<E>>>();
            values = null;
        }

        /**
         * Creates a {@code Node} which is to be a child. This constructor
         * increments the depth, whereas the three-argument constructor does
         * not. The {@code quadrant} argument must not be invalid.
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

            boundingBox =
                    BoundingBox.createBoundingBox(parent.getBoundingBox(),
                    quadrant);


            this.parent = dm.createReference(parent);
            //this.depth = parent.depth + 1;
            this.bucketSize = bucketSize;
            myQuadrant = quadrant;
            children = new ArrayList<ManagedReference<Node<E>>>();
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
         * @param box the area encompassing the quadrants
         * @param point the point to check
         * @return the quadrant the point lies within, or
         * {@code INVALID_QUADRANT} if the point is out of bounds
         */
        static int determineQuadrant(BoundingBox box, Point point) {
            double[] coords = BoundingBox.getCornerValues(box);

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
         * null (meaning it has no values), then the parent node will be
         * returned instead.
         *
         * @param point the point belonging to the {@code Node}
         * @return the leaf {@code Node} corresponding to the given
         * {@code Point}, or the parent {@code Node} of the leaf node
         * corresponding to the given {@code Point} if the leaf node is null
         */
        <E> Node<E> getLeafNode(Point point) {
            int quadrant;
            Node node = this;
            Node<E> child;

            //Keep walking down the tree towards the quadrant of point
            //until a leaf is found or a null leaf is found
            while (!node.isLeaf()) {
                quadrant = Node.determineQuadrant(node.getBoundingBox(),
                        point);
                child = node.getChild(quadrant);

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
         * Prints out the values if node is a leaf or if the node is not a leaf
         * prints out the children which are leaves.
         * @return a string representation of the {@code Node}
         */
        public String toString() {
            StringBuilder sb = new StringBuilder("[");

            //Print out values if node is a leaf
            if (isLeaf()) {
                if (values != null) {
                    Iterator iter = values.keySet().iterator();
                    while (iter.hasNext()) {
                        sb.append("[");
                        Point p = (Point) iter.next();
                        sb.append(p + "= ");

                        int size = values.get(p).size();
                        for (int i = 0; i < size; i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(values.get(p).get(i).getId());
                            sb.append(" ");
                            sb.append(values.get(p).get(i).get());
                        }
                        if (iter.hasNext()) {
                            sb.append("] , ");
                        }
                    }
                    sb.append("]");
                    return sb.toString();
                }
                return sb.append("]").toString();
            }

            //Print out children which are leaves if node is not a leaf
            for (int i = 0; i < NUM_CHILDREN; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                if (children.get(i) == null) {
                    sb.append("Null Leaf");
                } else if (children.get(i).get().isLeaf()) {
                    sb.append(children.get(i).get().toString());
                } else {
                    sb.append("Non-Leaf");
                }
            }
            sb.append("]");
            return sb.toString();
        }

        /**
         * Returns the children of this node. This is intended to be used by
         * the {@code isEmpty} method.
         *
         * @return the children of this node
         */
        List<ManagedReference<Node<E>>> getChildren() {
            return children;
        }

        /**
         * Returns the quadrant that this node represents
         *
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
            if (children.isEmpty() || children.get(index) == null) {
                return null;
            }
            return children.get(index).get();
        }

        /**
         * Returns the corner points of the region corresponding to this node
         *
         * @return the corner points of the region corresponding to this node
         */
        BoundingBox getBoundingBox() {
            return boundingBox;
        }

        /**
         * Returns {@code true} if an element exists at the given
         * {@code Point}, and {@code false} otherwise.
         *
         * @param point the coordinate to check
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
         *
         * @return this node's parent, or {@code null} if it is the root
         */
        Node<E> getParent() {
            if (parent == null) {
                return null;
            }
            return parent.get();
        }

        /**
         * Returns this node's values, which consists of a {@code Map} of
         * points and elements
         *
         * @return this node's {@code Map} of values
         */
        TreeMap<Point, List<ManagedReference<ManagedSerializable<E>>>>
                getValues() {
            return values;
        }

        /**
         * Determines if this node is a leaf node, which is true if the
         * children list is empty.
         * @return {@code true} if it is a leaf node, and {@code false}
         * otherwise
         */
        boolean isLeaf() {
            return children.isEmpty();
        }

        /**
         * Adds the element to the node. If the node is already populated with
         * the maximum number of values, then a split operation occurs which
         * generates children and converts this node from a leaf into an
         * intermediate node. If node is not a leaf, it must be the parent
         * of a null leaf. The null leaf will be determined and replaced by a
         * new leaf containing the new element.
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
            if (!isLeaf() && children.get(quadrant) == null) {
                Node<E> child = new Node<E>(this, quadrant, bucketSize);
                child.add(point, element, false);
                children.set(quadrant, AppContext.getDataManager().
                        createReference(child));
            } else if (isLeaf() && values == null) {

                //Initialize leaf's values if it has not been initialized yet
                values = new TreeMap<Point,
                        List<ManagedReference<ManagedSerializable<E>>>>();
                insert(point, element);
            } else if (size(this) == bucketSize && !values.containsKey(point)) {
                //If we are at capacity and values doesn't already contain the
                //given point, perform a split and try adding again.
                splitThenAdd(point, element);
            } else {
                insert(point, element);
            }
        }

        /**
         * Calculates the size (number of points it contains) of the leaf node
         * @param <E> the type of element stored in the node
         * @param node the node of which to get the size
         * @return the size of the node (number of points it contains)
         */
        private static <E> int size(Node<E> node) {
            assert (node.isLeaf()) : "The node must be a leaf";

            Map<Point, List<ManagedReference<ManagedSerializable<E>>>> values =
                    node.getValues();
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
            int quadrant;
            Map<Point, List<ManagedReference<ManagedSerializable<E>>>>
                    existingValues = values;
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);
            prepareNewChildren();

            // Add back the old elements to the appropriate new leaves.
            // Since we have four new quadrants, we have to add each
            // one individually to allocate it in the correct quadrant.
            Iterator<Map.Entry<Point, 
                    List<ManagedReference<ManagedSerializable<E>>>>> keyIter
                    = existingValues.entrySet().iterator();
            Point key;
            List<ManagedReference<ManagedSerializable<E>>> list;

            // Iterate through all the keys. They hold onto lists
            // which each contain at least one element
            while (keyIter.hasNext()) {
                Map.Entry mapEntry = keyIter.next();
                key = (Point) mapEntry.getKey();
                list = (List<ManagedReference<ManagedSerializable<E>>>)
                        mapEntry.getValue();
                quadrant =
                        Node.determineQuadrant(boundingBox,
                        key);

                // Add all the items in the list at the given key
                // to the new children.
                Iterator<ManagedReference<ManagedSerializable<E>>> iter =
                        list.iterator();
                ManagedReference<ManagedSerializable<E>> ref;
                while (iter.hasNext()) {
                    ref = iter.next();
                    addValue(quadrant, key, ref.get().get());
                    dm.removeObject(ref.get());
                }
            }

            // Add in the new value
            quadrant = Node.determineQuadrant(boundingBox, point);
            addValue(quadrant, point, element);
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
            ManagedReference<Node<E>> childRef = children.get(quadrant);
            Node<E> child;
            if (childRef == null) {
                child = new Node<E>(this, quadrant, bucketSize);
                child.add(point, element, false);
                children.set(quadrant, dm.createReference(child));
            } else {
                child = childRef.getForUpdate();
                child.add(point, element, false);
            }
        }

        /**
         * Adds an element to the leaf node's list
         *
         * @param point the point at which the new element will be inserted
         * @param element the new element to be inserted
         */
        private void insert(Point point, E element) {
            assert (isLeaf()) : "The node is a leaf";
            assert (element != null) : "The value cannot be null";
            List<ManagedReference<ManagedSerializable<E>>> list =
                    values.get(point);

            // Decide if we need to make a new instance or not
            if (list == null) {
                list =
                      new ArrayList<ManagedReference<ManagedSerializable<E>>>();
                values.put(point, list);
            }
            ManagedSerializable<E> ms = new ManagedSerializable<E>(element);
            ManagedReference<ManagedSerializable<E>> ref =
                    AppContext.getDataManager().createReference(ms);
            int index = Collections.binarySearch(list, ref,
                    getComparator());
            index = (index + 1) * (-1);
            list.add(index, ref);

            AppContext.getDataManager().markForUpdate(this);

        }

        /**
         * Prepares the children list so that new elements can be added, by
         * giving them a null value first, to make them null leaves. This
         * process sets the value of the current node to {@code null} in
         * anticipation of new children to be instantiated.
         */
        @SuppressWarnings("unchecked")
        private void prepareNewChildren() {
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);
            values = null;

            for (int i = 0; i < NUM_CHILDREN; i++) {
                children.add(i, null);
            }
        }

        /* Starting at the current node, move upwards, removing nodes which
         * have children that are leaves which have null values
         *
         * */
        void doRemoveWork() {
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(this);

            if (isLeaf()) {

                //Stop if this is not a leaf with null values, there are no
                //nodes to remove
                if (values != null) {
                    return;
                }
            } else {
                int numNull = 0;
                for (int i = 0; i < NUM_CHILDREN; i++) {
                    Node<E> child = getChild(i);

                    //Check for the number of null children and remove
                    //any children with null values
                    if (child == null) {
                        numNull++;
                    } else if (child.isLeaf() && child.getValues() == null) {
                        children.set(i, null);
                        dm.removeObject(child);
                        numNull++;

                    }
                }

                //Clear the list of children if all of them are null
                if (numNull == NUM_CHILDREN) {
                    children.clear();
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
         * Removes the element from the tree if it exists.
         *
         * @param coordinate the coordinate of the element to remove
         * @param index index of the element to be removed in the point's list
         * @return the element that was removed, or {@code null} if none was
         * removed
         */
        E remove(Point coordinate, int index) {
            E old = removeEntry(coordinate, this, index);

            // If we found an entry, remove it and return it
            if (old != null) {
                doRemoveWork();
                return old;
            }
            return null;
        }

        /**
         * Retrieves the {@code entry} from the {@code ConcurrentQuadTree} that corresponds to
         * the given point and index.
         *
         * @param <E> the type of object stored
         * @param point the coordinate of the {@code entry}
         * @param node the node to search within
         * @param index index of the element to be removed in the point's list
         * @return the entry, or {@code null} if none matches the given
         * coordinate
         */
        private static <E> E removeEntry(Point point, Node<E> node, int index) {
            assert (node.isLeaf()) : "The node is not a leaf";
            Map<Point, List<ManagedReference<ManagedSerializable<E>>>> values =
                    node.getValues();

            // If there was no value stored, or if the node has children,
            // return null since we cannot remove anything from this node
            if (values == null) {
                return null;
            }
            List<ManagedReference<ManagedSerializable<E>>> list =
                    values.get(point);

            System.err.println("lalalala");

            if (list == null) {
                return null;
            }
            DataManager dm = AppContext.getDataManager();
            dm.markForUpdate(node);

            System.err.println("~~ test: " + list.size());

            // extract the element from the list, and delete the wrapper
            ManagedReference<ManagedSerializable<E>> ref = list.remove(index);
            //If element at the specified index has already been removed
            //(doesn't exist) return null
            if (ref == null) {
                return null;


            }
            E old = ref.get().get();
            dm.removeObject(ref.get());

            System.err.println("list size is now: " + list.size());

            // if list is now empty, remove the key
            if (list.isEmpty()) {
                System.err.println("list is considered empty");
                values.remove(point);

                // set the map to null if we removed the last entry
                if (values.isEmpty()) {
                    System.err.println("map is considered empty");
                    node.values = null;
                }
            }
            return old;
        }

        /**
         * Removes the element at the given point without returning it. Also
         * performs remove work to propagate any changes up the tree.
         *
         * @param point the coordinate of the element to remove
         * @return {@code true} if an element was removed, and {@code false}
         * otherwise
         */
        boolean delete(Point point) {

            E old = removeEntry(point, this, 0);
            if (old != null) {
                doRemoveWork();
                return true;
            }
            return false;
        }
    }
}
