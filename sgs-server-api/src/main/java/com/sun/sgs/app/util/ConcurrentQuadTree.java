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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

/**
 * The {@code ConcurrentQuadTree} is a data structure which organizes a
 * defined type and its Cartesian position in a rectangular, two-dimensional
 * region. More specifically, the data structure subdivides existing regions
 * into four, equally-sized regions in order to allot one region for a desired
 * number of elements. These sub-regions are also referred to as quadrants,
 * and are represented by leaf nodes in the tree. Therefore, each
 * {@code Point} inserted into the {@code ConcurrentQuadTree} will be located
 * in a region containing no more than the specified number of elements for
 * each leaf node. This parameter is defined in the constructor of the
 * quadtree, and is referred to as the {@code bucketSize}. A quadtree enables
 * a two-dimensional space to efficiently hold onto a certain number of
 * {@code Point}s using a relatively simple and low-cost scheme. The quadtree
 * does not support null elements; that is, calling {@code add(x, y, null)} is
 * not permitted. The quadtree does, however, support multiple elements at the
 * same coordinate location. In other words, it is legal to do the following:
 * <p>
 * {@code add(1, 1, object1);}<br>
 * {@code add(1, 1, object2);}, etc.
 * <p>
 * In order to determine the number of elements stored at a given location, it
 * is necessary to create an iterator with a bounding box encompassing the
 * coordinate ({@code boundingBoxIterator(double, double, double, double)}),
 * or alternately, an iterator for the point itself ({@code pointIterator(double, double)}).
 * <p>
 * This type of organization is best interpreted as a tree whereby "deeper"
 * nodes correspond to smaller regions. Elements can only exist at the leaf
 * nodes; if a node overflows its bucket size, then it splits into smaller
 * regions at an incremented depth and the elements are reallocated.
 * <p>
 * Overall, many of the methods occur in logarithmic time because the tree has
 * to be walked in order to locate the correct region to manipulate. This is
 * not very costly because the quadtree has a tendency to grow horizontally,
 * especially if values are spaced far enough apart.
 * <p>
 * To allow concurrency, this data structure does not propagate changes from
 * leaf nodes toward the root node. Since the tree does not grow upwards,
 * nodes that have been created permanently maintain their tree depth, unless
 * they are removed (in other words, collapsed or pruned). As suggested above,
 * a tree depth of 0 corresponds to a single node (the root), without any
 * children. Each subsequent level incurs its parent's incremented depth.
 * Nodes are removed from the tree (and thus, data manager) when there are
 * neither children containing elements, nor children containing children. By
 * definition, this also means that the size of the node and all its children
 * are 0. This measure is taken to improve the performance of walking the tree
 * in the future and reduces memory requirements.
 * <p>
 * Iteration of the tree is achieved by defining an optional region within
 * which to search. Therefore, the order of elements in the iteration is not
 * guaranteed to be the same for each iterator constructed. An iterator scheme
 * is used to retrieve all elements in a particular sub-region (also known as
 * an {@code boundingBox}) of the tree. Since there may be many elements in
 * the tree, this approach removes the need to walk through the tree to
 * collect and return all elements in an otherwise lengthy process.
 * <p>
 * As suggested above, accessing elements in the quadtree is achieved by using
 * an iterator over a given rectangular region. Since there may be more than
 * one element at a given location, it is infeasible to specify the
 * characteristics of the object to be removed or retrieved. Instead,
 * individual removals can be achieved by using the iterator's
 * {@code remove()} method.
 * 
 * @param <T> the type the quadtree is to hold
 */
public class ConcurrentQuadTree<T> implements QuadTree<T>, Serializable,
	ManagedObjectRemoval {
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
    private final ManagedReference<BoundingBox> boundingBox;

    /** The root element of the quadtree */
    private ManagedReference<Node<T>> root;

    /**
     * The five-argument constructor which defines a quadtree with a depth
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
     */
    public ConcurrentQuadTree(int bucketSize, double x1, double y1,
	    double x2, double y2) {

	if (bucketSize < 0) {
	    throw new IllegalArgumentException(
		    "Bucket size cannot be negative");
	}
	this.bucketSize = bucketSize;

	DataManager dm = AppContext.getDataManager();
	BoundingBox box =
		new BoundingBox(new Point(x1, y1), new Point(x2, y2));
	boundingBox = dm.createReference(box);
	Node<T> node = new Node<T>(boundingBox, bucketSize);
	root = dm.createReference(node);
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
	    if (iter.nextNoReturn()) {
		element = iter.current();
		double x = iter.currentX();
		double y = iter.currentY();
		put(x, y, element);
	    }
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
	return (root.get().getChildren() == null) &&
		(root.get().getValues() == null);
    }

    /**
     * {@inheritDoc}
     */
    public boolean put(double x, double y, T element) {
	// Check to see that the node is within bounds since
	// the returned quadrant could be null if the point is
	// out of bounds
	Point point = new Point(x, y);
	Object quadrant =
		Node.Quadrant.determineQuadrant(boundingBox.get(), point);
	if (!(quadrant instanceof Node.Quadrant)) {
	    throw new IllegalArgumentException(
		    "The coordinates are not contained "
			    + "within the bounding box");

	}

	Node<T> leaf = Node.getLeafNode(root.get(), point);
	return leaf.add(point, element, true);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(double x, double y) {
	boolean result = false;
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root.get(), point);

	System.err.println(" ~~~ a");

	while (leaf.remove(point) != null) {
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
    static Object getReferenceValue(ManagedReference<?> ref) {
	if (ref != null) {
	    try {
		return ref.get();
	    } catch (ObjectNotFoundException onfe) {
	    }
	}
	return null;
    }

    /**
     * Creates a reference to the supplied argument if it is not {@code null}.
     * 
     * @param <T> the type of the object
     * @param t the object to reference
     * @return a {@code ManagedReference} of the object, or {@code null} if
     * the argument is null.
     */
    static <T> ManagedReference<T> createReferenceIfNecessary(T t) {
	if (t == null) {
	    return null;
	}
	return AppContext.getDataManager().createReference(t);
    }

    /**
     * Casts the object to the desired type in order to avoid unchecked cast
     * warnings
     * 
     * @param <T> the type to cast to
     * @param object the object to cast
     * @return the casted version of the object
     */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
	return (T) object;
    }

    /**
     * Removes an element from the quadtree corresponding to the provided
     * coordinate and returns the result of the operation.
     * 
     * @param x the x-coordinate of the element to remove
     * @param y the y-coordinate of the element to remove
     * @return {@code true} if the object was removed, and {@code false}
     * otherwise
     */
    public boolean removeNoReturn(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root.get(), point);
	return leaf.delete(point);
    }

    /**
     * {@inheritDoc}
     */
    public double[] getDirectionalBoundingBox() {
	return BoundingBox.organizeCoordinates(boundingBox.get());
    }

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<T> boundingBoxIterator(double x1, double y1,
	    double x2, double y2) {
	Point corner1 = new Point(x1, y1);
	Point corner2 = new Point(x2, y2);
	BoundingBox box = new BoundingBox(corner1, corner2);

	return new ElementIterator<T>(root.get(), box);
    }
    
    
    /**
     * {@inheritDoc}
     **/
    public QuadTreeIterator<T> pointIterator(double x, double y) {
	Point point = new Point(x, y);
	BoundingBox box = new BoundingBox(point, point);
	return new ElementIterator<T>(root.get(), box);
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
	Node<T> newRoot = new Node<T>(boundingBox, bucketSize);
	AppContext.getDataManager().markForUpdate(this);
	root = AppContext.getDataManager().createReference(newRoot);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root.get(), point);
	return leaf.contains(point);
    }

    /**
     * {@inheritDoc}
     */
    public void removingObject() {
	AsynchronousClearTask<T> clearTask =
		new AsynchronousClearTask<T>(root.get());

	// Schedule asynchronous task here
	// which will delete the list
	AppContext.getTaskManager().scheduleTask(clearTask);
    }

    /**
     * {@inheritDoc}
     */
    public QuadTreeIterator<T> iterator() {
	return new ElementIterator<T>(root.get(), boundingBox.get());
    }

    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;; Nested Class Definitions ;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
    static class ElementIterator<T> implements QuadTreeIterator<T>,
	    Serializable {
	private static final long serialVersionUID = 4L;

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
	private Iterator<Point> entryIterator;

	/**
	 * An iterator for the values of a list storing the elements at a
	 * given coordinate
	 */
	private Iterator<ManagedReference<
		ManagedSerializable<T>>> valueIterator;

	/**
	 * A {@code Point} object representing the current point being
	 * examined in a node's underlying {@code Map}
	 */
	private Point currentPoint;

	/**
	 * A flag denoting whether the current node is fully contained by the
	 * iterator's region. If {@code true}, this flag removes the need to
	 * check each entry for containment
	 */
	private boolean isFullyContained;

	/**
	 * One-argument constructor which assumes there is no bounding box
	 * associated with the iterator.
	 * 
	 * @param root the root node of the quadtree, used to locate the first
	 * child
	 */
	ElementIterator(Node<T> root) {
	    this(root, null);
	}

	/**
	 * Two-argument constructor which permits specification of a bounding
	 * box specific to the iterator.
	 * 
	 * @param root the root node of the quadtree, used to locate the first
	 * child
	 * @param box the region which specifies the qualified entries that
	 * this iterator is to iterate over; a value of {@code null} means all
	 * entries are valid (no bounding box)
	 */
	ElementIterator(Node<T> root, BoundingBox box) {
	    this.box = box;
	    current =
		    uncheckedCast(getReferenceValue(
			    getFirstQualifiedLeafNode(root)));
	    currentPoint = null;
	    valueIterator = null;

	    // Check for edge case where there are no qualified leaf nodes
	    if (current == null) {
		current = root;
		entryIterator = null;
	    } else {
		entryIterator = current.getValues().keySet().iterator();
	    }
	    dataIntegrityValue = current.getDataIntegrityValue();
	    next = current;
	    nextEntry = getNextQualifiedElement();

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
	 * Returns the next element which is contained in the bounding box
	 * defined at the iterator's instantiation. If there are no more
	 * qualified elements, then {@code null} is returned.
	 * 
	 * @return the next entry located in the specified bounding box (if an
	 * bounding box is defined) or {@code null} if no next qualified entry
	 * exists
	 */
	private Entry<T> getNextQualifiedElement() {
	    if (entryIterator == null) {
		return null;
	    }

	    // Return the next qualified elements in the list
	    T value;
	    if ((value = getNextElement()) != null) {
		return new Entry<T>(currentPoint, value);
	    }

	    // If there are no more qualified elements contained at
	    // the current point, fetch the next point and try again
	    while (entryIterator.hasNext()) {
		currentPoint = entryIterator.next();

		// Check if the next point sits inside the bounding box
		if (isFullyContained || box.contains(currentPoint)) {
		    List<ManagedReference<ManagedSerializable<T>>> list =
			    next.getValues().get(currentPoint);

		    // Go to next map entry if the list is null
		    if (list == null) {
			continue;
		    }

		    valueIterator = list.iterator();
		    if ((value = getNextElement()) != null) {
			return new Entry<T>(currentPoint, value);
		    }
		}
	    }

	    // If there are no more points, we need to fetch a new node
	    // and try again
	    next =
		    uncheckedCast(getReferenceValue(
			    getNextQualifiedLeafNode(next)));
	    if (next != null) {
		entryIterator = next.getValues().keySet().iterator();
		return getNextQualifiedElement();
	    }
	    return null;
	}

	/**
	 * Returns the next element in the iteration.
	 * 
	 * @return the next element in the iteration, or {@code null} if no
	 * more exist
	 */
	private T getNextElement() {
	    if (valueIterator != null) {

		// If there are elements remaining, return the next one
		while (valueIterator.hasNext()) {
		    return valueIterator.next().get().get();
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
	    loadNext();
	    return entry.getValue();
	}

	
	/**
	 * Sets up the next element without returning it. The two
	 * "next" methods will decide whether they want to return
	 * the value or not
	 */
	private void loadNext() {
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
	    nextEntry = getNextQualifiedElement();
	    dataIntegrityValue = current.getDataIntegrityValue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean nextNoReturn() {
	    try {
		loadNext();
	    } catch (ObjectNotFoundException onfe) {
		/*
		 * If we get here, then the element in the data manager was
		 * removed without updating the quadtree. Notify that no true
		 * reference exists, even though the element does.
		 */ 
		entry = null;
		return false;
	    }
	    return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public T current() {
	    if (entry == null) {
		return null;
	    }
	    return entry.getValue();
	}

	/**
	 * {@inheritDoc}
	 */
	public double currentX() {
	    if (entry == null) {
		return Double.NaN;
	    }
	    return entry.coordinate.x;
	}

	/**
	 * {@inheritDoc}
	 */
	public double currentY() {
	    if (entry == null) {
		return Double.NaN;
	    }
	    return entry.coordinate.y;
	}

	/**
	 * Retrieves the first leaf node in a tree, rooted by {@code node}.
	 * This method can never return null.
	 * 
	 * @param node the root of the tree or subtree
	 * @return the first child that is a leaf, which may not be parented
	 * by {@code node}
	 * @throws IllegalStateException if a leaf node could not be found
	 */
	static <T> ManagedReference<Node<T>> getFirstLeafNode(Node<T> node) {
	    // If the given node is a leaf with values, we are done
	    if (node.isLeaf()) {
		return AppContext.getDataManager().createReference(node);
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
	 * Returns the next node using a depth-first traversal scheme. If we
	 * try to retrieve the next node while on the root, {@code null} is
	 * returned, specifying our exit condition.
	 * 
	 * @param node the current node, whose next element we are interested
	 * in finding
	 * @return the next node in the depth-first traversal, or {@code null}
	 * if none exists
	 */
	static <T> ManagedReference<Node<T>> getNextLeafNode(Node<T> node) {
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
		    return AppContext.getDataManager().createReference(child);
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
	private ManagedReference<Node<T>> getFirstQualifiedLeafNode(
		Node<T> node) {

	    // Return the leaf if no region was specified, or if the
	    // region intersects with the leaf node
	    ManagedReference<Node<T>> leaf = getFirstLeafNode(node);
	    if (isQualified(leaf)) {
		return leaf;
	    }

	    // Otherwise, try getting the next qualified node
	    return getNextQualifiedLeafNode(leaf.get());
	}

	/**
	 * Given the node parameter, return the next leaf node in succession
	 * (using a depth-first search) which has entries in the defined
	 * region. A leaf node is "qualified" as long as its bounding box
	 * intersects the bounding box of the iterator (if defined), and if
	 * the node has values.
	 * 
	 * @param node the current node being examined
	 * @return the next node containing qualified entries, or {@code null}
	 * if none exists
	 */
	private ManagedReference<Node<T>> getNextQualifiedLeafNode(
		Node<T> node) {
	    ManagedReference<Node<T>> child = getNextLeafNode(node);

	    // Check if this child is "qualified"
	    while (child != null) {

		// Skip over nodes whose bounding boxes do not intersect
		// the iterator's defined bounding box
		if (isQualified(child) && child.get().getValues() != null) {
		    BoundingBox box = child.get().getBoundingBox();
		    isFullyContained =
			    (this.box.getContainment(box) == 
				BoundingBox.Containment.FULL);
		    return child;
		}
		// get the next node
		child = getNextLeafNode(child.get());
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
	private boolean isQualified(ManagedReference<Node<T>> node) {
	    if (node == null) {
		return false;
	    }

	    // The node is not qualified if it doesn't have values to
	    // iterate over
	    if (node.get().getValues() == null) {
		return false;
	    }
	    // Otherwise, check that the node intersects the region
	    BoundingBox box = node.get().getBoundingBox();
	    return (box == null || this.box.getContainment(box) != 
		BoundingBox.Containment.NONE);
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
     * An inner class which is responsible for clearing the tree from the data
     * manager by performing a depth-first traversal.
     * 
     * @param <T> the type of object stored in the tree
     */
    private static class AsynchronousClearTask<T> implements Task,
	    Serializable, ManagedObject {
	private static final long serialVersionUID = 3L;

	/** The node currently being examined */
	private ManagedReference<Node<T>> current;

	/** The total number of elements to remove for each task iteration */
	private final int MAX_OPERATIONS = 50;

	/** The iterator which traverses the unique coordinates in a map */
	private ManagedReference<
		ManagedSerializable<Iterator<Point>>> keyIterator;

	/** The point currently being examined */
	private Point currentPoint;

	/**
	 * The constructor of the clear task, which requires the root element
	 * of the tree to begin the traversal
	 * 
	 * @param root the root of the tree, which must not be {@code null}
	 */
	AsynchronousClearTask(Node<T> root) {
	    assert (root != null) : "The root parameter must not be null";

	    current = ElementIterator.getFirstLeafNode(root);
	    ManagedSerializable<Iterator<Point>> iter =
		    new ManagedSerializable<Iterator<Point>>(
			    setupKeyIterator(current));
	    keyIterator = createReferenceIfNecessary(iter);
	    currentPoint =
		    (keyIterator == null ||
			    !keyIterator.get().get().hasNext() ? null
			    : keyIterator.get().get().next());
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
	    // end or if we max out our operations, which ever comes first
	    while (current != null && ++count < MAX_OPERATIONS) {

		// If we ran out of elements to remove, fetch the next
		// Point to try and remove.
		if (!removeElement()) {
		    currentPoint =
			    (keyIterator == null ||
				    !keyIterator.get().get().hasNext() ? null
				    : keyIterator.get().get().next());

		    // We are done with this node; delete it from
		    // the data manager and then get the next one
		    if (currentPoint == null) {
			ManagedReference<Node<T>> nextNode =
				ElementIterator
					.getNextLeafNode(current.get());
			AppContext.getDataManager().removeObject(
				current.get());
			current = nextNode;
			ManagedSerializable<Iterator<Point>> iter =
				new ManagedSerializable<Iterator<Point>>(
					setupKeyIterator(current));
			keyIterator = createReferenceIfNecessary(iter);
			currentPoint =
				(keyIterator == null ||
					!keyIterator.get().get().hasNext()
					? null : keyIterator.get().get()
						.next());
		    }
		}
	    }
	    return (current != null);
	}

	/**
	 * Returns the iterator over the {@code Points} belonging to the node,
	 * or {@code null} if there are no {@code Points} to examine.
	 * 
	 * @param <T> the type of elements stored
	 * @param node the current node being examined
	 * @return an iterator over the points in the bucket, or {@code null}
	 * if there are none
	 */
	private static <T> Iterator<Point> setupKeyIterator(
		ManagedReference<Node<T>> node) {
	    Map<Point, List<ManagedReference<ManagedSerializable<T>>>> values =
		    node.get().getValues();
	    if (values == null || values.isEmpty()) {
		return null;
	    }
	    return values.keySet().iterator();
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
	    // No elements to iterate; return false indicating
	    // no elements were removed
	    if (currentPoint == null) {
		return false;
	    }

	    // Deletes the next element located at currentPoint
	    return (current.get().delete(currentPoint));
	}
    }

    /**
     * A region, defined by two {@code Points}, which represents the area
     * belonging to a certain object. The two {@code Point}s representing the
     * bounding box are Cartesian points which correspond to corner points of
     * an imaginary box. Each x and y coordinate for both points represent the
     * bounds of this box, and therefore, the bounds of the
     * {@code BoundingBox}. For simplicity, the {@code BoundingBox}'s edges
     * are only allowed to be parallel or perpendicular to the Cartesian axes,
     * meaning the {@code BoundingBox} edges either intersect the axes at
     * right angles or coincide with them.
     */
    static class BoundingBox implements Serializable, ManagedObject {
	private static final long serialVersionUID = 3L;

	public static final byte TOTAL_CORNERS = 4;

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
	     * bounding box
	     */
	    PARTIAL,
	    /** Denotes total containment (or domination) of the bounding box */
	    FULL;
	}

	/** An array of two points to represent the bounding box area */
	final Point[] bounds;

	/**
	 * Converts the {@code BoundingBox} instance to a string
	 * representation.
	 * 
	 * @return a string representation of the {@code BoundingBox} public
	 * String toString() { StringBuilder sb = new StringBuilder();
	 * sb.append("<("); sb.append(bounds.get()[0].x); sb.append(", ");
	 * sb.append(bounds.get()[0].y); sb.append(") "); sb.append("(");
	 * sb.append(bounds.get()[1].x); sb.append(", ");
	 * sb.append(bounds.get()[1].y); sb.append(")>"); return
	 * sb.toString(); }
	 */

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
	static BoundingBox createBoundingBox(BoundingBox parentBoundingBox,
		Node.Quadrant quadrant) {
	    // get the individual coordinates
	    double[] coords = organizeCoordinates(parentBoundingBox);

	    // Create the middle of the region, which is guaranteed to be a
	    // corner of the node's bounds. Time to find the other corner
	    Point middle = calculateMiddle(parentBoundingBox);

	    Point corner;
	    switch (quadrant) {
		case NW:
		    corner = new Point(coords[X_MIN], coords[Y_MAX]);
		    break;
		case NE:
		    corner = new Point(coords[X_MAX], coords[Y_MAX]);
		    break;
		case SW:
		    corner = new Point(coords[X_MIN], coords[Y_MIN]);
		    break;
		case SE:
		default:
		    corner = new Point(coords[X_MAX], coords[Y_MIN]);
	    }
	    return new BoundingBox(middle, corner);
	}

	/**
	 * Organizes the coordinates into an array so that the minimum and
	 * maximum values can be obtained easily. The array's values are best
	 * accessed using the fields {@code X_MIN}, {@code X_MAX},
	 * {@code Y_MIN}, or {@code Y_MAX} as array indices.
	 * 
	 * @param box the region, represented as an array of two
	 * {@code Points}
	 * @return an array which contains individual coordinates
	 */
	static double[] organizeCoordinates(BoundingBox box) {
	    Point[] bounds = box.bounds;

	    // Accumulate max and min values
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
	    double[] coords = organizeCoordinates(this);
	    double[] arg = organizeCoordinates(anotherBoundingBox);

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
	    double[] d = organizeCoordinates(box);
	    return new Point(d[X_MIN] + ((d[X_MAX] - d[X_MIN]) / 2),
		    d[Y_MIN] + ((d[Y_MAX] - d[Y_MIN]) / 2));
	}
    }

    /**
     * Represents an entry in the quadtree by maintaining a reference to the
     * stored object and its coordinates in the form of a {@code Point}
     * object.
     */
    static class Entry<T> implements Serializable, ManagedObject {
	private static final long serialVersionUID = 5L;

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
	    AppContext.getDataManager().markForUpdate(this);
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

    @SuppressWarnings("unchecked")
    private static class ReferenceContainer<T> implements ManagedObject,
	    Serializable {
	private static final long serialVersionUID = 9L;

	/**
	 * The array of references which will refer to the array of
	 * {@code ManagedObject}s
	 */
	private final ManagedReference<T>[] container;

	/**
	 * Creates a {@code ReferenceContainer} to encapsulate the supplied
	 * object array. The supplied object array must implement the
	 * {@code ManagedObject} interface because
	 * 
	 * @param obj the array which is to be contained
	 * @throws IllegalArgumentException if the parameter is {@code null},
	 * or if the parameter does not implement the {@code ManagedObject}
	 * interface
	 */
	ReferenceContainer(T[] obj) {
	    if (obj == null) {
		throw new IllegalArgumentException("Argument cannot be null");
	    }
	    if (!(obj instanceof ManagedObject[])) {
		throw new IllegalArgumentException(
			"Argument must implement ManagedObject");
	    }
	    DataManager dm = AppContext.getDataManager();
	    container = new ManagedReference[obj.length];

	    for (int i = 0; i < obj.length; i++) {
		container[i] = dm.createReference(obj[i]);
	    }
	}

	/**
	 * Returns the value at the given index of the array
	 * 
	 * @param index the object's index
	 * @return the value at the given index
	 * @throws IndexOutOfBoundsException if the index is out of bounds of
	 * the underlying array
	 * @throws ObjectNotFoundException if the underlying object in the
	 * array was removed without updating this reference
	 */
	T get(int index) throws ObjectNotFoundException {
	    if (index < 0 || index > container.length - 1) {
		throw new IndexOutOfBoundsException("The index " + index +
			" is out of bounds");
	    }
	    return container[index].get();
	}

    } // end container class

    /**
     * A class that represents a point as an ({@code x}, {@code y})
     * coordinate pair.
     */
    public static class Point implements Serializable {
	private static final long serialVersionUID = 7L;

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
	 * Rounds the parameter to a reasonable number of decimal spaces. For
	 * now, the number of decimal spaces is set at 8.
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
	 * Calculates the distance between two points using the Pythagorean
	 * Theorem
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
	 * Returns a hash code for the object
	 * 
	 * @return a hash code for the object
	 */
	public int hashCode() {
	    return (int) (x + y);
	}

    } // end Point class

    /**
     * A class that represents a general-purpose node in the
     * {@code ConcurrentTree}. This class can either represent an
     * intermediate node or a leaf node
     * 
     * @param <T> the type of coordinates to store
     */
    static class Node<T> implements Serializable, ManagedObject {
	private static final long serialVersionUID = 6L;

	/** Enumeration representing the different quadrants for each node */
	public static enum Quadrant {
	    /**
	     * North-West quadrant (top-left, if "north" is "up")
	     */
	    NW,
	    /**
	     * North-East quadrant (top-right, if "north" is "up")
	     */
	    NE,
	    /**
	     * South-West quadrant (bottom-left, if "north" is "up")
	     */
	    SW,
	    /**
	     * South-East quadrant (bottom-right, if "north" is "up")
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
	     * @return an integer representing the {@code Quadrant}, or -1 if
	     * the conversion is invalid
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
	     * @return the corresponding {@code Quadrant}, or {@code null} if
	     * the conversion is invalid
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
		if (point.x < coords[X_MIN] || point.x > coords[X_MAX] ||
			point.y < coords[Y_MIN] || point.y > coords[Y_MAX]) {
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
	private final ManagedReference<Node<T>> parent;

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
	private final ManagedReference<BoundingBox> boundingBox;

	/** the quadrant this node belongs to */
	private final Quadrant myQuadrant;

	/**
	 * the map of entries, with the value being a list of
	 * {@code ManagedReference}s which point to
	 * {@code ManagedSerializable} objects containing the stored entries.
	 */
	private Map<Point, List<ManagedReference<
		ManagedSerializable<T>>>> values;

	/** references to the children */
	private ManagedReference<ReferenceContainer<Node<T>>> children;

	/**
	 * Constructor to be used when instantiating the root. If children
	 * need to be instantiated, call the four-argument constructor.
	 * 
	 * @param box the region corresponding to this node's bounding box
	 * @param bucketSize the maximum capacity of a leaf node
	 */
	Node(ManagedReference<BoundingBox> box, int bucketSize) {
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
	    DataManager dm = AppContext.getDataManager();

	    BoundingBox box =
		    BoundingBox.createBoundingBox(parent.getBoundingBox(),
			    quadrant);
	    boundingBox = dm.createReference(box);

	    this.parent = dm.createReference(parent);
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
			Node.Quadrant.determineQuadrant(
				node.getBoundingBox(), point);
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
		sb.append(children.get().get(i).toString());
	    }
	    sb.append("]");
	    return sb.toString();
	}

	/**
	 * Returns the children of this node. This is intended to be used by
	 * the {@code isEmpty} method.
	 * 
	 * @return the children of this node, or {@code null} if no children
	 * exist
	 */
	ReferenceContainer<Node<T>> getChildren() {
	    if (children == null) {
		return null;
	    }
	    return children.get();
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
	    return getChild(index);
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
	Node<T> getChild(int index) {
	    assert (!isLeaf()) : "The node is a leaf node";
	    if (children == null) {
		return null;
	    }
	    return children.get().get(index);
	}

	/**
	 * Returns the corner points of the region corresponding to this node
	 * 
	 * @return the corner points of the region corresponding to this node
	 */
	BoundingBox getBoundingBox() {
	    return boundingBox.get();
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
	Node<T> getParent() {
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
	Map<Point, List<ManagedReference<ManagedSerializable<T>>>> getValues() {
	    return values;
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
	 * Adds the element to the node. If the node is already populated with
	 * the maximum number of values, then a split operation occurs which
	 * generates children and converts this node from a leaf into an
	 * intermediate node.
	 * 
	 * @param point the coordinate to add the element
	 * @param element the element to add
	 * @param allowSplit {@code true} if a split should be allowed, and
	 * {@code false} otherwise
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	boolean add(Point point, T element, boolean allowSplit) {

	    /*
	     * If there aren't any values yet, a new list is instantiated. If
	     * we are at capacity, perform a split and try adding again.
	     * Otherwise, append normally.
	     */
	    if (values == null) {
		values =
			new HashMap<Point, 
			List<ManagedReference<ManagedSerializable<T>>>>();

	    } else if (size(this) == bucketSize) {
		if (!allowSplit) {
		    return false;
		}
		return splitThenAdd(point, element);
	    }
	    insert(point, element);
	    return true;
	}

	/**
	 * Calculates the size of the leaf node by walking through the values.
	 * This method is necessary to determine when a split is to occur.
	 * 
	 * @param <T> the type of element stored in the node
	 * @param node the node of which to get the size
	 * @return the size of the node
	 */
	private static <T> int size(Node<T> node) {
	    assert (node.isLeaf()) : "The node must be a leaf";
	    int size = 0;

	    // Check for a valid map entry
	    Map<Point, List<ManagedReference<ManagedSerializable<T>>>> values =
		    node.getValues();
	    if (values != null) {

		// For all the coordinates in this node,
		// get the size of the lists
		Iterator<Point> keyIterator = values.keySet().iterator();
		while (keyIterator.hasNext()) {
		    List<ManagedReference<ManagedSerializable<T>>> list =
			    values.get(keyIterator.next());
		    size += list.size();
		}
	    }
	    return size;
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
	    Map<Point, List<ManagedReference<
	    	ManagedSerializable<T>>>> existingValues =
		    values;
	    AppContext.getDataManager().markForUpdate(this);
	    initializeNewChildren();

	    // Add back the old elements to the appropriate new leaves.
	    // Since we have four new quadrants, we have to add each
	    // one individually to allocate it in the correct quadrant.
	    Iterator<Point> keyIter = existingValues.keySet().iterator();
	    Point key;
	    List<ManagedReference<ManagedSerializable<T>>> list;

	    // iterate through all the keys. They hold onto lists
	    // which each contain at least one element
	    while (keyIter.hasNext()) {
		key = keyIter.next();
		list = existingValues.get(key);
		quadrant =
			Quadrant.determineQuadrantAsInt(boundingBox.get(),
				key);

		// add all the items in the list at the given key
		Iterator<ManagedReference<ManagedSerializable<T>>> iter =
			list.iterator();
		T value;
		while (iter.hasNext()) {
		    value = iter.next().get().get();
		    children.getForUpdate().get(quadrant).add(key, value,
			    false);
		}
	    }

	    // add in the new value
	    quadrant =
		    Quadrant.determineQuadrantAsInt(boundingBox.get(), point);
	    return children.getForUpdate().get(quadrant).add(point, element,
		    false);
	}

	/**
	 * Appends the entry to the leaf node's list
	 * 
	 * @param entry the new entry to append
	 */
	private void insert(Point point, T value) {
	    assert (isLeaf()) : "The node is not a leaf";
	    assert (value != null) : "The value cannot be null";
	    List<ManagedReference<ManagedSerializable<T>>> list =
		    values.get(point);

	    // Decide if we need to make a new instance or not
	    if (list == null) {
		list = new ArrayList<
			ManagedReference<ManagedSerializable<T>>>();
		values.put(point, list);
	    }
	    ManagedSerializable<T> ms = new ManagedSerializable<T>(value);
	    ManagedReference<ManagedSerializable<T>> ref =
		    AppContext.getDataManager().createReference(ms);
	    list.add(ref);

	    AppContext.getDataManager().markForUpdate(this);
	    dataIntegrityValue++;
	}

	/**
	 * Initializes the children so that new elements can be added. This
	 * process sets the value of the current node to {@code null} in
	 * anticipation of new children to be instantiated.
	 */
	@SuppressWarnings("unchecked")
	private void initializeNewChildren() {
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);
	    values = null;

	    Node<T>[] newChildren = new Node[numChildren];

	    // Initialize each direction separately
	    newChildren[Quadrant.iNE] =
		    new Node<T>(this, Quadrant.NE, bucketSize);
	    newChildren[Quadrant.iNW] =
		    new Node<T>(this, Quadrant.NW, bucketSize);
	    newChildren[Quadrant.iSE] =
		    new Node<T>(this, Quadrant.SE, bucketSize);
	    newChildren[Quadrant.iSW] =
		    new Node<T>(this, Quadrant.SW, bucketSize);

	    // Create the container where the children are to reside
	    ReferenceContainer<Node<T>> container =
		    new ReferenceContainer(newChildren);
	    children = dm.createReference(container);
	}

	/**
	 * Determines if the node should collapse, and propagates the changes
	 * if so (recursively).
	 */
	void doRemoveWork() {
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);

	    // If we reach here, then we are empty. If we
	    // have populated children, then we just return
	    if (!canPrune()) {
		dataIntegrityValue++;
		return;
	    }

	    // Remove the children from the data manager if they exist
	    if (children != null) {
		for (int i = 0; i < numChildren; i++) {
		    dm.removeObject(children.get().get(i));
		}
		values = null;
		children = null;
	    }

	    // we are at the root; do not delete ourselves.
	    if (parent != null) {
		// TODO: remove ourselves from data store
		parent.getForUpdate().doRemoveWork();
	    }
	}

	/**
	 * Returns whether or not this node has empty children. Empty is
	 * defined as containing no elements.
	 * 
	 * @return {@code true} if the node contains empty children, and
	 * {@code false} otherwise
	 */
	private boolean canPrune() {
	    // If the children are empty, then we are done
	    if (children == null) {
		// If there are no values, then we can prune
		return (values == null);
	    }

	    // Otherwise, check each node
	    for (int i = 0; i < numChildren; i++) {
		Node<T> child = children.get().get(i);

		// There exists elements as long as a child is
		// not null and:
		// a child is not a leaf or if the
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
	    T old = removeEntry(coordinate, this);

	    // If we found an entry, remove it and return it
	    if (old != null) {
		if (values.size() == 0) {
		    values = null;
		}
		doRemoveWork();
		return old;
	    }
	    return null;
	}

	/**
	 * Retrieves the {@code entry} from the quadtree that corresponds to
	 * the given point. Since there may be more than one element stored at
	 * the given coordinate, this method pops the head element, which does
	 * not guarantee the resulting element.
	 * 
	 * @param <T> the type of object stored
	 * @param point the coordinate of the {@code entry}
	 * @param node the node to search within
	 * @return the entry, or {@code null} if none matches the given
	 * coordinate
	 */
	private static <T> T removeEntry(Point point, Node<T> node) {
	    assert (node.isLeaf()) : "The node is not a leaf";
	    Map<Point, List<ManagedReference<ManagedSerializable<T>>>> values =
		    node.getValues();

	    // If there was no value stored, or if the node has children,
	    // return null since we cannot remove anything from this node
	    if (values == null) {
		return null;
	    }
	    List<ManagedReference<ManagedSerializable<T>>> list =
		    values.get(point);

	    System.err.println("lalalala");

	    if (list == null) {
		return null;
	    }
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(node);

	    System.err.println("~~ test: " + list.size());

	    // extract the element from the list, and delete the wrapper
	    ManagedReference<ManagedSerializable<T>> ref = list.remove(0);
	    T old = ref.get().get();
	    dm.removeObject(ref.get());

	    System.err.println("list size is now: " + list.size());

	    // if list is now empty, remove the key
	    if (list.isEmpty()) {
		System.err.println("list is considered empty");
		values.remove(point);

		// set the map to null if we removed the last entry
		if (values.isEmpty()) {
		    System.err.println("map is considered empty");
		    values = null;
		}
	    }
	    return old;
	}

	/**
	 * Removes the element at the given point without returning it. This
	 * method is used to avoid any {@code ObjectNotFoundException}s in
	 * the event that the underlying element was removed from the data
	 * manager without updating the quadtree.
	 * 
	 * @param point the coordinate of the element to remove
	 * @return {@code true} if an element was removed, and {@code false}
	 * otherwise
	 */
	boolean delete(Point point) {
	    T old = removeEntry(point, this);
	    if (old != null) {
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
