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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

/**
 * The {@code ConcurrentQuadTree} is a data structure which organizes a
 * defined type and its Cartesian position in a rectangular, two-dimensional
 * region. More specifically, the data structure subdivides existing regions
 * into four, equally-sized regions in order to allot one region for a desired
 * number of elements. Therefore, each {@code Point} inserted into the
 * {@code ConcurrentQuadTree} will be located in a region containing no more
 * than the specified number of elements for each leaf node. This parameter is
 * defined in the constructor of the quadtree, and is referred to as the
 * {@code bucketSize}. A quadtree enables a two-dimensional space to
 * efficiently hold onto a certain number of {@code Point}s using a
 * relatively simple and low-cost scheme. The quadtree does not support null
 * elements; that is, calling {@code add(x, y, null)} is not permitted.
 * <p>
 * This type of organization is best interpreted as a tree whereby "deeper"
 * nodes correspond to smaller regions. Elements can only exist at the leaf
 * nodes; if a node overflows its bucket size, then it splits into smaller
 * regions and the elements are reallocated. The depth of leaf nodes depends
 * on the maximum depth of the tree provided during instantiation: a large
 * depth limit allows for more entries and for them to exist within very small
 * regions, whereas a smaller depth limit can support fewer entries but they
 * exist in larger regions. The depth limit should be proportional to the
 * number of entries which need to be stored and the minimum region size you
 * wish to support. If not specified (by using the five-argument constructor),
 * the quadtree will have a default maximum depth of 10. Therefore, any
 * element additions that would require the tree to grow deeper would not be
 * applied to the quadtree.
 * <p>
 * Overall, many of the methods occur in logarithmic time because the tree has
 * to be walked in order to locate the correct region to manipulate. This is
 * not often very costly because the quadtree has a tendency to grow
 * horizontally, especially if values are spaced far enough apart and if the
 * tree has a shallow depth.
 * <p>
 * To allow for concurrency, this data structure does not propagate changes
 * from leaf nodes toward the root node. Since the tree does not grow upwards,
 * nodes that have been created maintain their tree depth permanently unless
 * they are removed. As mentioned above, a tree depth of 0 corresponds to a
 * single node (the root), without any children. Each subsequent level
 * increments the depth. Nodes are removed when there are no children
 * containing elements. By definition, this also means that the size of the
 * node and all its children are 0. This measure is taken to improve the
 * performance of walking the tree in the future and reduces memory
 * requirements.
 * <p>
 * Iteration of the tree is achieved by defining an optional region within
 * which to search. Therefore, the order of elements in the iteration is not
 * guaranteed to be the same for each iterator constructed. An iterator scheme
 * is used to retrieve all elements in a particular sub-region (also known as
 * an {@code boundingBox}) of the tree. Since there may be many elements in
 * the tree, this approach removes the need to walk through the tree to
 * collect and return all elements in an otherwise lengthy process.
 * 
 * @param <T> the type the quadtree is to hold
 */
public class ConcurrentQuadTree<T> implements QuadTree<T>, Serializable {
    private static final long serialVersionUID = 1L;
    
    /*
     * These fields must start at 0 and ascend continuously since they
     * represent the indices of an array of type {@code double} of size 4,
     * which holds onto a pair of coordinates
     */
    /** The index intended for the minimum x-coordinate */
    public static final int X_MIN = 0;
    /** The index intended for the minimum y-coordinate */
    public static final int Y_MIN = 1;
    /** The index intended for the maximum x-coordinate */
    public static final int X_MAX = 2;
    /** The index intended for the maximum y-coordinate */
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
    private Node<T> root;


    /**
     * The five-argument constructor which defines a quadtree with a depth
     * supplied as a parameter. The area corresponding to this instance is
     * defined by the supplied coordinates whereby ({@code x1}, {@code y1})
     * represent the first {@code Point} and ({@code x2}, {@code y2})
     * represents the second {@code Point} of the defining {@code BoundingBox}.
     * 
     * @param maxDepth the maximum depth the tree is permitted to grow; this
     * value cannot be negative
     * @param bucketSize the maximum capacity of a leaf node
     * @param x1 the x-coordinate of the first point defining the tree's
     * bounding box
     * @param y1 the y-coordinate of the first point defining the tree's
     * bounding box
     * @param x2 the x-coordinate of the second point defining the tree's
     * bounding box
     * @param y2 the x-coordinate of the second point defining the tree's
     * bounding box
     */
    public ConcurrentQuadTree(int bucketSize, double x1,
	    double y1, double x2, double y2) {
	
	if (bucketSize < 0) {
	    throw new IllegalArgumentException(
		    "Bucket size cannot be negative");
	}
	this.bucketSize = bucketSize;
	boundingBox = new BoundingBox(new Point(x1, y1), new Point(x2, y2));
	root = new Node<T>(boundingBox, bucketSize);
    }

    
    /**
     * A copy constructor which adds all elements within the {@code tree}
     * parameter into the new quadtree. For trees with very many elements,
     * this operation can take a long time since all elements need to be
     * iterated through. Only elements with valid references are added to the
     * new quadtree. That is, if there are elements whose underlying
     * references are missing from the data manager, they are not added to the
     * new quadtree.
     * 
     * @param tree the quadtree whose elements are to be added into the new
     * quadtree
     */
    public ConcurrentQuadTree(ConcurrentQuadTree<T> tree) {
	boundingBox = tree.boundingBox;
	bucketSize = tree.bucketSize;
	QuadTreeIterator<T> iter = tree.iterator();
	T element;
	while (iter.hasNext()) {
	    
	    // Add element to the tree if a reference exists
	    if (iter.nextWithoutReturningElement()) {
		element = iter.getCurrentElement();
		double x = iter.getX();
		double y = iter.getY();
		put(x, y, element);
	    }
	}
    }
    
    
    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
	return (root.children == null) && (root.values == null);
    }

    /**
     * {@inheritDoc}
     */
    public boolean put(double x, double y, T element) {
	// Check to see that the node is within bounds since
	// the returned quadrant could be null if the point is
	// out of bounds
	Point point = new Point(x, y);
	Object quadrant = Node.Quadrant.determineQuadrant(boundingBox, point);
	if (!(quadrant instanceof Node.Quadrant)) {
	    throw new IllegalArgumentException(
		    "The coordinates are not contained within the bounding box");
	}

	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.add(point, element, true);
    }

    /**
     * {@inheritDoc}
     */
    public T remove(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.remove(point);
    }
    
    
    /**
     * {@inheritDoc}
     */
    public boolean delete(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.delete(point);
    }

    /**
     * {@inheritDoc}
     */
    public T set(double x, double y, T element) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.setValue(point, element);
    }   
    

    /**
     * {@inheritDoc}
     */
    public T get(double x, double y) {
	Point point = new Point(x, y);
	Node<T> node = Node.getLeafNode(root, point);
	Entry<T> entry = node.get(point);
	return (entry == null ? null : entry.getValue());
    }

    /**
     * {@inheritDoc}
     */
    public double[] getDirectionalBoundingBox() {
	return BoundingBox.organizeCoordinates(boundingBox);
    }
    

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<T> boundingBoxIterator(double x1, double y1, double x2,
	    double y2) {
	Point corner1 = new Point(x1, y1);
	Point corner2 = new Point(x2, y2);
	BoundingBox box = new BoundingBox(corner1, corner2);

	return new ElementIterator<T>(root, box);
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
	root = new Node<T>(boundingBox, bucketSize);
    }
    
    
    /**
     * {@inheritDoc}
     */
    public boolean contains(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.contains(point);
    }
    
    /**
     * {@inheritDoc}
     */
    public void removingObject() {
	AsynchronousClearTask<T> clearTask =
		new AsynchronousClearTask<T>(root);

	// Schedule asynchronous task here
	// which will delete the list
	AppContext.getTaskManager().scheduleTask(clearTask);
    }
    

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<T> iterator() {
	return new ElementIterator<T>(root, boundingBox);
    }

    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;;   Nested Class Definitions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    /**
     * An iterator which walks through the entries stored in the quadtree.
     * This implementation allows for serialization, while also reporting
     * consistency problems in the form of a
     * {@code ConcurrentModificationException}. Data integrity checks are
     * performed at the beginning of each iterator operation to ensure that
     * the data integrity is consistent. If changes have occurred, such as a
     * node removed while the iterator was serialized, then a
     * {@code ConcurrentModificationException} is thrown.
     */
    static class ElementIterator<T> implements QuadTreeIterator<T>, Serializable {
	private static final long serialVersionUID = 1L;
	
	/** A region specific to this iterator */
	private final BoundingBox box;
	
	/** The data integrity value of the node examined */
	private int dataIntegrityValue;
	
	/** Whether the iterator is permitted to process a {@code remove} */
	private boolean canRemove;
	
	/** The current node being examined */
	private Node<T> current;
	
	/**
	 * The next node to be examined. This can be equal to {@code current}
	 * if the next entry is located in the same node.
	 */
	private Node<T> next;
	
	/**
	 * The current entry (the last entry returned from a call to
	 * {@code next})
	 */
	private Entry<T> entry;
	
	/**
	 * The next entry (the entry to be returned from a future call to
	 * {@code next})
	 */
	private Entry<T> nextEntry;
	
	/** An iterator over the elements belonging to a node */
	private Iterator<Entry<T>> entryIterator;
	
	/**
	 * A flag denoting whether the current node is fully contained by the
	 * iterator's region. If {@code true}, this flag removes the need to
	 * check each entry for containment
	 */
	private boolean isFullyContained;

	/**
	 * One-argument constructor which assumes there is no bounding box
	 * associated with the iterator.
	 * @param root the root node of the quadtree, used to locate
	 * the first child
	 */
	ElementIterator(Node<T> root) {
	    this(root, null);
	}

	/**
	 * Two-argument constructor which permits specification of a bounding
	 * box specific to the iterator
	 * 
	 * @param root the root node of the quadtree, used to locate the first
	 * child
	 * @param box the region which specifies the qualified entries that
	 * this iterator is to iterate over; a value of {@code null} means
	 * all entries are valid (no bounding box)
	 */
	ElementIterator(Node<T> root, BoundingBox box) {
	    this.box = box;

	    current = getFirstQualifiedLeafNode(root);
	    if (current == null) {
		current = root;
		entryIterator = null;
	    } else {
		entryIterator = current.getValues().iterator();
	    }
	    dataIntegrityValue = current.getDataIntegrityValue();
	    next = current;
	    nextEntry = prepareNextElement();
	    
	    isFullyContained = (box == null);
	    canRemove = false;
	    entry = null;
	}

	/**
	 * Checks whether the node has been modified while the iterator was
	 * serialized. If so, a {@code ConcurrentModificationException} is
	 * thrown
	 * 
	 * @throws ConcurrentModificationException if the node had been
	 * modified while this iterator was serialized
	 */
	private void checkDataIntegrity()
		throws ConcurrentModificationException {
	    try {
		if (current.getDataIntegrityValue() == dataIntegrityValue) {
		    return;
		}
	    } catch (ObjectNotFoundException onfe) {
	    }
	    throw new ConcurrentModificationException(
		    "The Node has been modified or removed");
	}

	/**
	 * Returns <tt>true</tt> if the iteration has more elements. (In
	 * other words, returns <tt>true</tt> if <tt>next</tt> would
	 * return an element rather than throwing an exception.)
	 * 
	 * @return <tt>true</tt> if the iterator has more elements.
	 */
	public boolean hasNext() {
	    return (nextEntry != null);
	}

	/**
	 * Fetches the next entry to be returned while also updating the
	 * {@code next} reference to the node containing the next entry.
	 * 
	 * @return the next entry to be examined, or {@code null} if none
	 * exists
	 */
	private Entry<T> prepareNextElement() {
	    // Try to find the next qualified entry in the current list
	    Entry<T> anEntry = iterateToNextQualifiedElement();
	    if (anEntry != null) {
		return anEntry;
	    }

	    /*
	     * If we didn't find an entry (it was null), locate the next node
	     * which is at least partially contained by the iterator's
	     * bounding box and iterate through it. If it has no valid entries,
	     * keep searching until we run out of nodes.
	     */
	    next = getNextQualifiedLeafNode(current);
	    while (next != null) {
		entryIterator = next.getValues().iterator();
		anEntry = iterateToNextQualifiedElement();
		if (anEntry != null) {
		    return anEntry;
		}
		next = getNextQualifiedLeafNode(next);
	    }
	    return null;
	}

	
	/**
	 * Moves the iterator's cursor to the next qualified entry; that is,
	 * the next entry which is contained within the iterator's bounding box.
	 * If there is no bounding box specified, then the next entry in the list
	 * is returned.
	 * 
	 * @return the next entry located in the specified bounding box (if an
	 * bounding box is defined) or {@code null} if no next qualified entry
	 * exists
	 */
	private Entry<T> iterateToNextQualifiedElement() {
	    if (entryIterator == null) {
		return null;
	    }
	    Entry<T> ent;

	    while (entryIterator.hasNext()) {
		ent = entryIterator.next();
		if (isFullyContained || box.contains(ent.coordinate)) {
		    return ent;
		}
	    }
	    return null;
	}

	/**
	 * Returns the next element in the iteration.
	 * 
	 * @return the next element in the iteration.
	 * @exception NoSuchElementException iteration has no more elements.
	 * @throws ConcurrentModificationException if the next node in the
	 * sequence was removed, or after deserialization, if this node was
	 * modified or removed.
	 */
	public T next() {
	    checkDataIntegrity();
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }

	    // since we called next(), we are now allowed to call
	    // a subsequent Iterator.remove()
	    canRemove = true;

	    // fetch the next element and adjust the
	    // references accordingly.
	    entry = nextEntry;
	    current = next;
	    nextEntry = prepareNextElement();
	    dataIntegrityValue = current.getDataIntegrityValue();
	    return entry.getValue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean nextWithoutReturningElement() {
	    try {
		next();
	    } catch (ObjectNotFoundException onfe) {
		// If we get here, then the element in the data manager was removed without
		// updating the quadtree. Notify that no true reference exists, even though
		// the element does.
		entry = null;
		return false;
	    }
	    return true;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public T getCurrentElement() {
	    if (entry == null) {
		return null;
	    }
	    return entry.getValue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public double getX() {
	    if (entry == null) {
		return Double.NaN;
	    }
	    return entry.coordinate.x;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public double getY() {
	    if (entry == null) {
		return Double.NaN;
	    }
	    return entry.coordinate.y;
	}

	/**
	 * Retrieves the first leaf node in a tree, rooted by
	 * {@code node}. This method can never return null.
	 * 
	 * @param node the root of the tree or subtree
	 * @return the first child that is a leaf, which may not be
	 * parented by {@code node}
	 * @throws IllegalStateException if a leaf node could not
	 * be found
	 */
	static <T> Node<T> getFirstLeafNode(Node<T> node) {
	    // If the given node is a leaf with values, we are done
	    if (node.isLeaf()) {
		return node;
	    }

	    // Iterate through all the children in a depth-first
	    // search looking for the first encountered leaf
	    for (int i = 0; i < Node.numChildren; i++) {
		Node<T> child = node.getChild(i);
		return getFirstLeafNode(child);
	    }
	    
	    // shouldn't get here
	    throw new IllegalStateException(
		    "Problem retrieving first leaf node");
	}

	
	/**
	 * Returns the next node using a depth-first traversal scheme.
	 * If we try to retrieve the next node while on the root, 
	 * {@code null} is returned, specifying our exit condition.
	 * @param node the current node, whose next element we are
	 * interested in finding
	 * @return the next node in the depth-first traversal, or 
	 * {@code null} if none exists
	 */
	static <T> Node<T> getNextLeafNode(Node<T> node) {
	    Node<T> parent = node.getParent();

	    // End condition: we reached the root; there is no next leaf node
	    if (parent == null) {
		return null;
	    }
	    
	    // Try and fetch siblings. If they are not leaves, then
	    // try to retrieve their children
	    Node.Quadrant quadrant = Node.Quadrant.next(node.getQuadrant());
	    if (quadrant != null) {
		Node<T> child = parent.getChild(quadrant);
		
		// Dig deeper if child is not a leaf,
		// or if it is a leaf with stored entries,
		// return it. Otherwise, keep searching this level.
		if (!child.isLeaf()) {
		    return getFirstLeafNode(child);
		} else if (child.getValues() != null) {
		    return child;
		}

		// Get the next sibling
		return getNextLeafNode(child);
	    }
	    return getNextLeafNode(parent);
	}
	
	
	/**
	 * Given the node parameter, return the first leaf which contains
	 * entries in the defined region.
	 *  
	 * @param node the node to start the search
	 * @return the first leaf node containing entries, or {@code null} if
	 * one does not exist
	 */
	private Node<T> getFirstQualifiedLeafNode(Node<T> node) {
	    
	    // Return the leaf if no region was specified, or if the
	    // region intersects with the leaf node
	    Node<T> leaf = getFirstLeafNode(node);
	    if (isQualified(leaf)) {
		return leaf;
	    }
	    
	    // Otherwise, try getting the next qualified node	    
	    return getNextQualifiedLeafNode(leaf);
	}
	
	
	/**
	 * Given the node parameter, return the next leaf node in succession
	 * (using a depth-first search) which has entries in the defined
	 * region.
	 * 
	 * @param node the current node being examined
	 * @return the next node containing entries, or {@code null} if none
	 * exists
	 */
	private Node<T> getNextQualifiedLeafNode(Node<T> node) {
	    Node<T> child = getNextLeafNode(node);
	    
	    // Check if this child is "qualified"
	    while (child != null) {
		
		// Skip over nodes whose bounding boxes do not intersect
		// the iterator's defined bounding box
		if (isQualified(child)) {
		    BoundingBox box = child.getBoundingBox();
		    isFullyContained =
			    (this.box.getContainment(box) == 
				BoundingBox.Containment.FULL);
		    return child;
		}
		// get the next node
		child = getNextLeafNode(child);
	    }
	    return null;
	}

	/**
	 * Determines whether the parameter is "qualified"; that is, whether
	 * it is intersected by the defined region specified during
	 * instantiation of the iterator. If no region was specified, then
	 * this method always returns {@code true}.
	 * 
	 * @param box the bounding box to check qualification
	 * @return {@code true} if an intersection occurs (qualified), and
	 * {@code false} otherwise (disqualified)
	 */
	private boolean isQualified(Node<T> node) {
	    // The node is not qualified if it doesn't have values to
	    // iterate over
	    if (node.getValues() == null) {
		return false;
	    }
	    // Otherwise, check that the node intersects the region
	    BoundingBox box = node.getBoundingBox();
	    return (box == null || 
		    this.box.getContainment(box) != BoundingBox.Containment.NONE);
	}

	/**
	 * Removes from the underlying collection the last element returned by
	 * the iterator (optional operation). This method can be called only
	 * once per call to <tt>next</tt>. The behavior of an iterator is
	 * unspecified if the underlying collection is modified while the
	 * iteration is in progress in any way other than by calling this
	 * method.
	 * 
	 * @exception IllegalStateException if the <tt>next</tt> method has
	 * not yet been called, or the <tt>remove</tt> method has already
	 * been called after the last call to the <tt>next</tt> method.
	 */
	public void remove() {
	    checkDataIntegrity();

	    // We can only remove if we have previously called next()
	    if (!canRemove) {
		throw new IllegalStateException(
			"Remove needs to follow Iterator.next()");
	    }

	    canRemove = false;
	    current.remove(entry.coordinate);
	    dataIntegrityValue = current.getDataIntegrityValue();
	}
    }

    
    
    
    /**
     * An inner class which is responsible for clearing the tree from
     * the data manager by performing a depth-first traversal. 
     *
     * @param <T> the type of object stored in the tree
     */
    private static class AsynchronousClearTask<T> implements Task, Serializable, ManagedObject {
	private static final long serialVersionUID = 3L;
	
	/** The node currently being examined */
	private Node<T> current;
	
	/** The total number of elements to remove for each task iteration */
	private final int MAX_OPERATIONS = 50;
	
	/**
	 * The constructor of the clear task, which requires the root
	 * element of the tree to begin the traversal
	 * @param root the root of the tree, which must not be {@code null}
	 */
	AsynchronousClearTask(Node<T> root) {
	    assert (root != null) : "The root parameter must not be null";
	    current = ElementIterator.getFirstLeafNode(root);
	}
	
	/**
	 * The entry point into the task
	 */
	public void run() {
	    // Perform some work and check if we need to reschedule
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);

	    // Check if there is more work to be done. If so, reschedule.
	    // Otherwise, remove the task object from the data manager.
	    if (doWork()) {
		AppContext.getTaskManager().scheduleTask(this);
	    } else {
		dm.removeObject(this);

		Runnable r = noteDoneRemoving;
		if (r != null) {
		    r.run();
		}
	    }
	}
	
	
	/**
	 * Removes MAX_OPERATION number of elements from the quadtree using a
	 * post-order depth-first traversal and returns {@code true} if there
	 * is more work to be done. If there are no more elements to remove,
	 * then it will return {@code false}.
	 * 
	 * @return {@code true} if more work needs to be done, and
	 * {@code false} if there are no more elements to remove.
	 */
	private boolean doWork() {
	    int count = 0;
	    
	    // Loop to remove elements. We'll stop when we reach the
	    // end or if we max out our operations, which ever first
	    while (current != null && ++count < MAX_OPERATIONS) {
		
		// If we ran out of elements to remove, delete this
		// node and fetch the next one
		if (!removeElement()) {
		    // TODO: delete node from data store and shift reference
		    current = ElementIterator.getNextLeafNode(current);
		}
	    }
	    // TODO: bind current as necessary
	    return (current != null);
	}
	
	/**
	 * Removes an element from the current node and returns {@code true}
	 * if it was performed. {@code False} is returned if no element was
	 * removed, indicating that a new node should be fetched.
	 * 
	 * @return {@code true} if an element was removed and {@code false}
	 * otherwise
	 */
	private boolean removeElement() {
	    List<Entry<T>> values = current.getValues();
	    // if there is nothing to remove, then return false
	    if (values == null || values.isEmpty()) {
		return false;
	    }
	    // otherwise, pop the head and return true
	    current.getValues().remove(0);
	    return true;
	}
    }

    
    
    /**
     * A region, defined by two {@code Points}, which represents the area
     * belonging to a certain object. The two {@code Point}s representing the
     * bounding box are Cartesian points which correspond to corner points of an
     * imaginary box. Each x and y coordinate for both points represent the
     * bounds of this box, and therefore, the bounds of the {@code BoundingBox}.
     * For simplicity, the {@code BoundingBox}'s edges are only allowed to be
     * parallel or perpendicular to the Cartesian axes, meaning the
     * {@code BoundingBox} edges either intersect the axes at right angles or
     * coincide with them.
     */
    static class BoundingBox {
	public static final byte TOTAL_CORNERS = 4;
	
	/**
	 * Specifies the degree of containment of another object, usually a
	 * {@code Point} or another {@code BoundingBox}. This is used primarily
	 * when comparing two bounding boxes with one another.
	 */
	static enum Containment {
	    /** Denotes no containment in the bounding box (disjoint) */
	    NONE,
	    /**
	     * Denotes partial containment (or an intersection) of the
	     * bounding box
	     */
	    PARTIAL,
	    /** Denotes total containment (or domination) of the bounding box */
	    FULL;
	}

	/** An array of two points to represent the bounding box area */
	final Point[] bounds;

	
	/**
	 * Converts the {@code BoundingBox} instance to a string representation.
	 * 
	 * @return a string representation of the {@code BoundingBox}
	 */
	public String toString() {
	    StringBuilder sb = new StringBuilder();
	    sb.append("<(");
	    sb.append(bounds[0].x);
	    sb.append(", ");
	    sb.append(bounds[0].y);
	    sb.append(") ");
	    sb.append("(");
	    sb.append(bounds[1].x);
	    sb.append(", ");
	    sb.append(bounds[1].y);
	    sb.append(")>");
	    return sb.toString();
	}

	/**
	 * Constructs a new {@code BoundingBox} given two points representing
	 * diagonal corners
	 * 
	 * @param a one of the corners of the {@code BoundingBox}
	 * @param b the other (diagonal) corner of the {@code BoundingBox}
	 */
	BoundingBox(Point a, Point b) {
	    bounds = new Point[] { a, b };
	}

	
	/**
	 * Creates the bounds given the parent's bounds and our intended
	 * quadrant
	 * 
	 * @param parentBoundingBox the parent's bounding box
	 * @param quadrant the quadrant to determine
	 * @return the bounds for this node
	 */
	static BoundingBox createBounds(BoundingBox parentBoundingBox,
		Node.Quadrant quadrant) {
	    // get the individual coordinates
	    double[] coords = organizeCoordinates(parentBoundingBox);

	    // Create the middle of the region, which is guaranteed to be a
	    // corner of the node's bounds. Time to find the other corner
	    Point middle = calculateMiddle(parentBoundingBox);

	    Point corner;
	    switch (quadrant) {
		case NW:
		    corner =
			    new Point(coords[X_MIN],
				    coords[Y_MAX]);
		    break;
		case NE:
		    corner =
			    new Point(coords[X_MAX],
				    coords[Y_MAX]);
		    break;
		case SW:
		    corner =
			    new Point(coords[X_MIN],
				    coords[Y_MIN]);
		    break;
		case SE:
		default:
		    corner =
			    new Point(coords[X_MAX],
				    coords[Y_MIN]);
	    }
	    return new BoundingBox(middle, corner);
	}

	/**
	 * Organizes the coordinates into an array so that the minimum and
	 * maximum values can be obtained easily. The array's values are best
	 * accessed using the fields {@code X_MIN}, {@code X_MAX},
	 * {@code Y_MIN}, or {@code Y_MAX} as array indices.
	 * 
	 * @param bounding box the region, represented as an array of two
	 * {@code Points}
	 * @return an array which contains individual coordinates
	 */
	 static double[] organizeCoordinates(BoundingBox box) {
	    Point[] bounds = box.bounds;

	    double xMin = Math.min(bounds[0].x, bounds[1].x);
	    double yMin = Math.min(bounds[0].y, bounds[1].y);
	    double xMax = Math.max(bounds[0].x, bounds[1].x);
	    double yMax = Math.max(bounds[0].y, bounds[1].y);

	    double[] values = new double[4];
	    values[X_MIN] = xMin;
	    values[X_MAX] = xMax;
	    values[Y_MIN] = yMin;
	    values[Y_MAX] = yMax;
	    return values;
	}

	/**
	 * {@inheritDoc}
	 */
	public Containment getContainment(BoundingBox anotherBoundingBox) {
	    double[] coords = organizeCoordinates(this);
	    double[] arg = organizeCoordinates(anotherBoundingBox);

	    // Increment every time we have a coordinate contained in the
	    // bounds of "this" bounding box
	    byte totalX = 0;
	    byte totalY = 0;
	    totalX +=
		    (isContained(arg[X_MIN],
			    coords[X_MIN],
			    coords[X_MAX]) ? 1 : 0);
	    totalX +=
		    (isContained(arg[X_MAX],
			    coords[X_MIN],
			    coords[X_MAX]) ? 1 : 0);
	    totalY +=
		    (isContained(arg[Y_MIN],
			    coords[Y_MIN],
			    coords[Y_MAX]) ? 1 : 0);
	    totalY +=
		    (isContained(arg[Y_MAX],
			    coords[Y_MIN],
			    coords[Y_MAX]) ? 1 : 0);

	    /*
	     * For total containment, all points should be contained. For
	     * partial containment, at least one X and Y coordinate need to be
	     * contained; hence both totals should be larger than 0.
	     * Otherwise, there is no containment.
	     */
	    if (totalX == 2 && totalY == 2) {
		return Containment.FULL;
	    } else if (totalX > 0 && totalY > 0) {
		return Containment.PARTIAL;
	    } else {
		return Containment.NONE;
	    }
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
	 * {@inheritDoc}
	 */
	public boolean contains(Point point) {
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
	    double[] d = organizeCoordinates(box);
	    return new Point(
		    d[X_MIN] +
			    ((d[X_MAX] - 
				    d[X_MIN]) / 2),
		    d[Y_MIN] +
			    ((d[Y_MAX] - 
				    d[Y_MIN]) / 2));
	}
    }

    
    
    /**
     * Represents an entry in the quadtree by maintaining a reference to the
     * stored object and its coordinates in the form of a {@code Point}
     * object.
     */
    static class Entry<T> {
	/** The coordinate of the element */
	final Point coordinate;

	/** The value of the element */
	private T value;

	/**
	 * Two-argument constructor which creates an entry given a coordinate
	 * and value. The value must not be {@code null}.
	 * 
	 * @param coord the coordinate of the new entry
	 * @param value the value of the new entry, which cannot be
	 * {@code null}
	 */
	Entry(Point coord, T value) {
	    assert (value != null) : "Value cannot be null";
	    coordinate = coord;
	    this.value = value;
	}

	/**
	 * Sets the value of the entry, in the event it is changed during a
	 * {@code set()} operation.
	 * 
	 * @param value the new value, which must not be {@code null}
	 */
	void setValue(T value) {
	    assert (value != null) : "Value cannot be null";
	    this.value = value;
	}

	/**
	 * Returns the value of the entry which cannot be {@code null}.
	 * 
	 * @return the value of the entry
	 */
	T getValue() {
	    return value;
	}

	
	/**
	 * Converts the {@code Entry} to a string representation.
	 * 
	 * @return a string representation of the {@code Entry}
	 */
	public String toString() {
	    return value.toString();
	}
    }

    /**
     * A class that represents a point as an ({@code x}, {@code y})
     * coordinate pair.
     */
    public static class Point {
	/** the format for rounded doubles; provides eight decimal spaces */
	private static final String DEFAULT_DECIMAL_FORMAT = "0.########";

	/** the x-coordinate */
	final double x;

	/** the y-coordinate */
	final double y;

	/**
	 * Constructor which creates a new {@code Point} instance given an x
	 * and y-coordinate. The constructor will appropriately round the
	 * parameters if they exceed three decimal spaces.
	 * 
	 * @param x x-coordinate of the point
	 * @param y y-coordinate of the point
	 */
	Point(double x, double y) {
	    this.x = round(x);
	    this.y = round(y);
	}


	/**
	 * Rounds the parameter to a reasonable number of decimal spaces.
	 * For now, the number of decimal spaces is set at 8.
	 * 
	 * @param value the value to round
	 * @return the rounded value, to eight decimal spaces
	 */
	private static double round(double value) {
	    DecimalFormat df = new DecimalFormat(DEFAULT_DECIMAL_FORMAT);
	    return new Double(df.format(value)).doubleValue();
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
	 * Calculates the distance between two points using the
	 * Pythagorean Theorem
	 * @param a a point
	 * @param b the other point
	 * @return the (non-rounded) distance between the two points
	 */
	static double getDistance(Point a, Point b) {
	    double deltaX = Math.abs(a.x - b.x);
	    double deltaY = Math.abs(a.y - b.y);
	    return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
	}
    }

    /**
     * A class that represents a general-purpose node in the
     * {@code ConcurrentTree}. This class can either represent an
     * intermediate node or a leaf node
     * 
     * @param <T> the type of coordinates to store
     */
    static class Node<T> {
	/** Enumeration representing the different quadrants for each node */
	public static enum Quadrant {
	    /**
	     * Corresponds to the North-West quadrant (top-left, if "north" is
	     * "up")
	     */
	    NW,
	    /**
	     * Corresponds to the North-East quadrant (top-right, if "north"
	     * is "up")
	     */
	    NE,
	    /**
	     * Corresponds to the South-West quadrant (bottom-left, if "north"
	     * is "up")
	     */
	    SW,
	    /**
	     * Corresponds to the South-East quadrant (bottom-right, if
	     * "north" is "up")
	     */
	    SE;

	    // Integer-representation of the quadrants. It is critical that 
	    // these values are consecutive integers, starting at 0, because
	    // they help index the children arrays for each node.
	    private static final int iNW = 0;
	    private static final int iNE = 1;
	    private static final int iSW = 2;
	    private static final int iSE = 3;

	    /**
	     * Converts a {@code Quadrant} into an integer representation
	     * 
	     * @param quadrant the value to convert to an integer
	     * @return an integer representing the {@code Quadrant}, or
	     * -1 if the conversion is invalid
	     */
	    public static int toInt(Quadrant quadrant) {
		switch (quadrant) {
		    case NW:
			return iNW;
		    case NE:
			return iNE;
		    case SW:
			return iSW;
		    case SE:
			return iSE;
		    default:
			return -1;
		}
	    }

	    /**
	     * Converts an integer into a {@code Quadrant}
	     * 
	     * @param value the value to convert
	     * @return the corresponding {@code Quadrant}, or {@code null}
	     * if the conversion is invalid
	     */
	    public static Quadrant toQuadrant(int value) {
		switch (value) {
		    case iNW:
			return NW;
		    case iNE:
			return NE;
		    case iSW:
			return SW;
		    case iSE:
			return SE;
		    default:
			return null;
		}
	    }

	    /**
	     * Returns the next quadrant in sequence. This is used during
	     * iteration so that the iterator knows how to fetch the next
	     * child. This process converts the quadrant into a numerical
	     * value, increments it, and returns the {@code Quadrant}
	     * representation. This method assumes that the integer
	     * representations of the quadrants are consecutive, starting at
	     * value 0.
	     * 
	     * @return the next quadrant to examine, or {@code null} if there
	     * are no more
	     */
	    static Quadrant next(Quadrant quadrant) {
		int intQuadrant = toInt(quadrant);
		return toQuadrant(++intQuadrant);
	    }

	    /**
	     * Returns the quadrant of the bounds that the point lies within.
	     * 
	     * @param box the area encompassing the quadrants
	     * @param point the point to check
	     * @return the quadrant the point lies within, or {@code null} if
	     * the point is out of bounds
	     */
	    static Quadrant determineQuadrant(BoundingBox box, Point point) {
		double[] coords = BoundingBox.organizeCoordinates(box);

		// check if it is out of bounds
		if (point.x < coords[X_MIN] ||
			point.x > coords[X_MAX] ||
			point.y < coords[Y_MIN] ||
			point.y > coords[Y_MAX]) {
		    return null;
		}

		// otherwise, try to locate its quadrant
		Point middle = BoundingBox.calculateMiddle(box);
		if (point.x < middle.x) {
		    if (point.y < middle.y) {
			return SW;
		    } else {
			return NW;
		    }
		} else {
		    if (point.y < middle.y) {
			return SE;
		    } else {
			return NE;
		    }
		}
	    }

	    /**
	     * Returns an integer representation of the quadrant of interest.
	     * 
	     * @param box the area encompassing the quadrants
	     * @param point the point to search for
	     * @return the quadrant, or -1 if the point is out of bounds
	     */
	    static int determineQuadrantAsInt(BoundingBox box, Point point) {
		return toInt(determineQuadrant(box, point));
	    }
	} // end Quadrant

	/** the default starting value for the data integrity variable */
	private static final int DEFAULT_INTEGRITY_START_VALUE =
		Integer.MIN_VALUE;

	/** the parent of this node */
	private final Node<T> parent;

	/** the depth of the node, which will not change */
	private final int depth;

	/** the maximum capacity of a leaf */
	private final int bucketSize;

	/**
	 * the integrity value used by the iterators to check for a
	 * {@code ConcurrentModificationException}
	 */
	private int dataIntegrityValue;

	/** the branching factor for each node */
	static final int numChildren = 4;

	/**
	 * the area (determined by two corner points) representing the node's
	 * bounds
	 */
	private final BoundingBox boundingBox;

	/** the quadrant this node belongs to */
	private Quadrant myQuadrant;

	/** the entry of the node if it is a leaf node */
	private List<Entry<T>> values;

	/** references to the children */
	private Node<T>[] children;
	

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
	    dataIntegrityValue = DEFAULT_INTEGRITY_START_VALUE;
	    myQuadrant = null;
	    children = null;
	    values = null;
	    depth = 0;
	}

	/**
	 * Creates a {@code Node} which is to be a child. This constructor
	 * increments the depth, whereas the three-argument constructor does
	 * not. The {@code quadrant} argument must not be null.
	 * 
	 * @param parent the parent of the {@code Node}
	 * @param quadrant the {@code Quadrant} which this {@code Node}
	 * represents
	 * @param bucketSize the maximum capacity of a leaf node
	 */
	Node(Node<T> parent, Quadrant quadrant, int bucketSize) {
	    assert (quadrant != null) : "The quadrant cannot be null";
	    boundingBox = BoundingBox.createBounds(parent.getBoundingBox(), quadrant);

	    this.parent = parent;
	    this.depth = parent.depth + 1;
	    this.bucketSize = bucketSize;
	    dataIntegrityValue = DEFAULT_INTEGRITY_START_VALUE;
	    myQuadrant = quadrant;
	    children = null;
	    values = null;
	}

	/**
	 * Returns the leaf node associated with the given point by performing
	 * a walk of the tree starting at the given root.
	 * 
	 * @param node the {@code Node} to start searching from
	 * @param point the point belonging to the {@code Node}
	 * @return the {@code Node} corresponding to the given {@code Point},
	 * or null if the point is out of bounds
	 */
	static <T> Node<T> getLeafNode(Node<T> node, Point point) {
	    if (!node.isLeaf()) {
		Quadrant q =
			Node.Quadrant.determineQuadrant(node.getBoundingBox(),
				point);
		return getLeafNode(node.getChild(q), point);
	    }
	    return node;
	}

	
	/**
	 * Converts the {@code Node} to a string representation.
	 * 
	 * @return a string representation of the {@code Node}
	 */
	public String toString() {
	    if (isLeaf()) {
		if (values != null) {
		    return values.toString();
		}
		return null;
	    }
	    StringBuilder sb = new StringBuilder("[");
	    for (int i = 0; i < numChildren; i++) {
		if (i > 0) {
		    sb.append(", ");
		}
		sb.append(children[i].toString());
	    }
	    sb.append("]");
	    return sb.toString();
	}

	/**
	 * Retrieves the data integrity value for this node.
	 * 
	 * @return the data integrity value
	 */
	int getDataIntegrityValue() {
	    return dataIntegrityValue;
	}

	/**
	 * Returns the quadrant that this node represents
	 * 
	 * @return the quadrant that this node represents
	 */
	Quadrant getQuadrant() {
	    return myQuadrant;
	}

	/**
	 * Returns the child corresponding to the given quadrant
	 * 
	 * @param quadrant the quadrant of the parent to retrieve
	 * @return the child corresponding to the given quadrant
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 */
	Node<T> getChild(Quadrant quadrant) {
	    assert (!isLeaf()) : "The node is a leaf node";
	    int index = Quadrant.toInt(quadrant);
	    return children[index];
	}

	/**
	 * Returns the child corresponding to the given index
	 * 
	 * @param index the index of the child
	 * @return the child corresponding to the given quadrant
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 */
	Node<T> getChild(int index) {
	    assert (!isLeaf()) : "The node is a leaf node";
	    return children[index];
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
	 * Attempts to retrieve the entry at the given coordinate.
	 * 
	 * @param point the {@code point} to examine for an entry
	 * @return the value at the given coordinate, or {@code null} if none
	 * exist
	 */
	Entry<T> get(Point point) {
	    if (values == null) {
		return null;
	    }
	    Iterator<Entry<T>> iter = values.iterator();
	    Entry<T> entry = null;
	    while (iter.hasNext()) {
		entry = iter.next();

		// Break if we found a matching coordinate
		if (entry.coordinate.equals(point)) {
		    return entry;
		}
	    }
	    return null;
	}
	
	
	/**
	 * Returns {@code true} if an element exists at the given
	 * {@code Point}, and {@code false} otherwise.
	 * @param point the coordinate to check
	 * @return {@code true} if an element exists at the given
	 * {@code Point}, and {@code false} otherwise.
	 */
	boolean contains(Point point) {
	    if (!isLeaf() || values == null) {
		return false;
	    }
	    
	    // TODO: use the new iterator scheme to retrieve the x/y coords
	    Iterator<Entry<T>> iter = values.iterator();
	    Entry<T> entry;
	    while(iter.hasNext()) {
		entry = iter.next();
		if (entry.coordinate.equals(point)) {
		    return true;
		}
	    }
	    return false;
	}

	/**
	 * Returns this node's parent.
	 * 
	 * @return this node's parent, or {@code null} if it is the root
	 */
	Node<T> getParent() {
	    return parent;
	}

	/**
	 * Returns this node's {@code Entry}, which consists of a
	 * {@code Point}-element pair
	 * 
	 * @return this node's {@code Entry}
	 */
	List<Entry<T>> getValues() {
	    return values;
	}

	/**
	 * Attempts to set the value to the given {@code CoordinatePair}.
	 * This will throw an {@code IllegalStateException} if called when
	 * this node is not a leaf node. The value should not be set to null
	 * unless this node is becoming a parent.
	 * 
	 * @param value the value to set this node to
	 * @return the old element, or {@code null} if one didn't exist
	 * @throws IllegalStateException if the node is not a leaf node
	 */
	T setValue(Point coord, T value) throws IllegalStateException {
	    assert (value != null) : "Value cannot be null";

	    if (!isLeaf()) {
		throw new IllegalStateException("The node is not a leaf node");
	    }
	    Entry<T> entry = get(coord);
	    if (entry != null) {
		T old = entry.getValue();
		entry.setValue(value);
		return old;
	    }
	    return null;
	}

	/**
	 * Determines if this node is a leaf node.
	 * 
	 * @return {@code true} if it is a leaf node, and {@code false}
	 * otherwise
	 */
	boolean isLeaf() {
	    return (children == null);
	}

	/**
	 * Adds the entry to the node. This calls
	 * {@code add(entry.coordinate, entry.value, true);}.
	 * 
	 * @param entry the entry to add to the quadtree
	 * @return {@code true} if the element was successfully added
	 * and {@code false} otherwise
	 */
	private boolean add(Entry<T> entry) {
	    return add(entry.coordinate, entry.value, true);
	}

	/**
	 * Adds the element to the node. If the node is already populated with
	 * a value, then a split operation occurs which generates children and
	 * converts this node from a leaf into an intermediate node.
	 * 
	 * @param point the coordinate to add the element
	 * @param element the element to add
	 * @param allowSplit {@code true} if a split should be allowed, and
	 * {@code false} otherwise
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	boolean add(Point point, T element, boolean allowSplit) {
	    Entry<T> newEntry = new Entry<T>(point, element);

	    /*
	     * If there aren't any values yet, a new list is instantiated. If
	     * we are at capacity, perform a split and try adding again.
	     * Otherwise, append normally.
	     */
	    if (values == null) {
		assert (values.size() == 0) : "Size was not zero for Node.add()";
		values = new ArrayList<Entry<T>>(bucketSize);

	    } else if (values.size() == bucketSize){
		if (!allowSplit) {
		    return false;
		}
		return splitThenAdd(point, element);
	    }
	    append(newEntry);
	    return true;
	}

	/**
	 * extract the old value and clear; it no longer should have a value
	 * since this node will soon have children
	 * 
	 * @param leaf the node to split
	 * @param point the coordinate to add the element
	 * @param element the element to add
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	private boolean splitThenAdd(Point point, T element) {
	    int quadrant;
	    List<Entry<T>> existingValues = values;
	    initializeNewChildren();

	    // Add back the old elements to the appropriate new leaves.
	    // Since we have four new quadrants, we have to add each
	    // one individually to allocate it in the correct quadrant.
	    for (int i = 0; i < existingValues.size(); i++) {
		Entry<T> entry = existingValues.get(i);

		quadrant =
			Quadrant.determineQuadrantAsInt(boundingBox,
				entry.coordinate);
		children[quadrant].add(entry);
	    }

	    // add in the new value
	    quadrant = Quadrant.determineQuadrantAsInt(boundingBox, point);
	    return children[quadrant].add(point, element, false);
	}

	/**
	 * Appends the entry to the leaf node's list
	 * 
	 * @param entry the new entry to append
	 */
	private void append(Entry<T> entry) {
	    assert (isLeaf()) : "The node is not a leaf";

	    values.add(entry);
	    dataIntegrityValue++;
	}

	/**
	 * Initializes the children so that new elements can be added. This
	 * process sets the value of the current node to {@code null} in
	 * anticipation of new children to be instantiated.
	 */
	@SuppressWarnings("unchecked")
	private void initializeNewChildren() {
	    values = null;
	    children = new Node[numChildren];

	    // Initialize each direction separately
	    children[Quadrant.iNE] =
		    new Node<T>(this, Quadrant.NE, bucketSize);
	    children[Quadrant.iNW] =
		    new Node<T>(this, Quadrant.NW, bucketSize);
	    children[Quadrant.iSE] =
		    new Node<T>(this, Quadrant.SE, bucketSize);
	    children[Quadrant.iSW] =
		    new Node<T>(this, Quadrant.SW, bucketSize);
	}

	/**
	 * Determines if the node should collapse, 
	 * and propagates the changes if so (recursively).
	 */
	void doRemoveWork() {
	    // If we reach here, then we are empty. If we 
	    // have populated children, then we just return
	    if (!canPrune()) {
		dataIntegrityValue++;
		return;
	    }

	    values = null;
	    children = null;
	    
	    // we are at the root; do not delete ourselves.
	    if (parent != null) {
		// TODO: remove ourselves from data store
		parent.doRemoveWork();
	    }
	}

	
	/**
	 * Returns whether or not this node has empty children. Empty
	 * is defined as containing no elements.
	 * @return {@code true} if the node contains empty children,
	 * and {@code false} otherwise
	 */
	private boolean canPrune() {
	    // If the children are empty, then we are done
	    if (children == null) {
		// If there are no values, then we can prune
		return (values == null);
	    }
	    
	    // Otherwise, check each node
	    for (int i=0 ; i<numChildren ; i++) {
		Node<T> child = children[i];
		
		// There exists elements as long as a child is
		// not null or a child is not a leaf or if the
		// child's values are not null.
		if (child != null &&
			(!child.isLeaf() || child.getValues() != null)) {
		    return false;
		}
	    }
	    return true;
	}
	
	/**
	 * Removes the element from the tree if it exists.
	 * 
	 * @param coordinate the coordinate of the element to remove
	 * @return the element that was removed, or {@code null} if none was
	 * removed
	 */
	T remove(Point coordinate) {
	    Entry<T> entry = removeEntry(coordinate, this);
	    
	    // If we found an entry, remove it and return it
	    if (entry != null) {
		if (values.size() == 0) {
		    values = null;
		}
		doRemoveWork();
		return entry.getValue();
	    }
	    return null;
	}
	
	
	/**
	 * Retrieves the {@code entry} from the quadtree that corresponds
	 * to the given point
	 * @param <T> the type of object stored
	 * @param point the coordinate of the {@code entry}
	 * @param node the node to search within
	 * @return the entry, or {@code null} if none matches the given
	 * coordinate
	 */
	private static <T> Entry<T> removeEntry(Point point, Node<T> node) {
	    assert (node.isLeaf()) : "The node is not a leaf";
	    List<Entry<T>> values = node.getValues();

	    // If there was no value stored, or if the node has children,
	    // return null since we cannot remove anything from this node
	    if (values == null) {
		return null;
	    }

	    Iterator<Entry<T>> iter = values.iterator();
	    Entry<T> entry;
	    while (iter.hasNext()) {
		entry = iter.next();

		// if an entry has a matching coordinate, 
		// we found the entry of interest
		if (entry.coordinate.equals(point)) {
		    iter.remove();
		    return entry;
		}
	    }
	    return null;
	}

	/**
	 * Removes the element at the given point without returning it. This
	 * method is used to avoid any {@code ObjectNotFoundException}s in the
	 * event that the underlying element was removed from the data manager
	 * without updating the quadtree.
	 * 
	 * @param point the coordinate of the element to remove
	 * @return {@code true} if an element was removed, and {@code false}
	 * otherwise
	 */
	boolean delete(Point point) {
	    Entry<T> entry = removeEntry(point, this);
	    if (entry != null) {
		if (values.size() == 0) {
		    values = null;
		}
		doRemoveWork();
		return true;
	    }
	    return false;
	}
    }
    
}
