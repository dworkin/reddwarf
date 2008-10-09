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

import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

/**
 * This class represents a {@code java.util.List} which supports a concurrent
 * and scalable behavior. In particular, this List allows for arbitrary
 * insertions and removals, arbitrary searches for values, and a number of
 * other methods which are also defined in the {@code java.util.List}
 * interface. This data structure builds upon the {@code AbstractList} class
 * by implementing methods specific to concurrent and scalable operations. As
 * an implementation decision, this data structure does not support
 * {@code null} insertions but does implement all optional operations defined
 * in the {@code java.util.List} interface.
 * <p>
 * Those who are interested in using a simple data structure that is not
 * intended to grow very large, contains simple elements, but offers decent
 * concurrency should use the
 * {@code java.util.concurrent.CopyOnWriteArrayList<E>} type. However, if
 * lists are intended to be very large and cloning the list is infeasible due
 * to its size or the complexity of its contents, then the
 * {@code ScalableList} is a better tool as it does not require cloning and it
 * scales reasonably well.
 * <p>
 * This class actually stores a collection of {@code ManagedReference}s,
 * which in turn point to {@code ManagedObject}s. Therefore, each element
 * stored is converted into a {@code ManagedObject} if it is not one already,
 * and a {@code ManagedReference} is created to reference it. This "wrapper"
 * is detected during an element removal: if the element is an instance of the
 * wrapper {@code ManagedObject} class, then it is removed from the data
 * store. Conversely, if the element had no wrapper (but by definition is
 * still a {@code ManagedObject}, then it remains in the data store until the
 * user explicitly removes it.
 * <p>
 * The class achieves scalability and concurrency by partitioning an ordinary
 * list into a number of smaller lists contained in {@code ListNode} objects,
 * and joining the nodes in a tree format. This implementation bears
 * similarity to a skip-list in that access to arbitrary elements occurs
 * through initially large jumps, and then through a finer iteration of the
 * contained list. To allow for this behaviour, each {@code ListNode} holds a
 * subset of the contents so that changes in the entries need not propagate to
 * all elements at once. In fact, each {@code ListNode} only holds onto the
 * size of its {@code SubList} (its children) and not a cumulative total of
 * all its previous siblings. This enables intermediate changes to have no
 * effect on neighbouring nodes, such as re-indexing.
 * <p>
 * The {@code branchingFactor} is a user-defined parameter which describes how
 * the underlying tree is organized. A large {@code branchingFactor} means
 * that each node in the tree contains a large number of children, providing
 * for a shallower tree, but many more sibling traversals. Concurrency is
 * somewhat compromised since parent nodes containing a large number of
 * children are locked during modification. A smaller branching factor reduces
 * the sibling traversals, but makes the tree deeper, somewhat affecting
 * performance during split operations. Depending on the use of the list, it
 * may be desirable to have a large {@code branchingFactor}, such as for
 * improved scalability, or a smaller {@code branchingFactor}, such as for
 * better concurrency.
 * <p>
 * Performing splits and removing unused nodes can be somewhat expensive,
 * depending on the values set for the {@code branchingFactor} and
 * {@code bucketSize}. This is because changes that occur at the leaf level
 * need to propagate to the parents; for a deep tree, this can affect a number
 * of different nodes. However, it is seen that the benefits provided by the
 * partitioning of the list that enable concurrency outweigh the performance
 * hit for these operations.
 * <p>
 * As mentioned, {@code ListNode}s contain a subset of the total number of
 * elements in the {@code ScalableList}. When an iterator is referencing an
 * element, the iterator is not affected by changes that occur in prior
 * {@code ListNode}s. However, if modifications happen after the current
 * {@code ListNode} being examined, they will be incorporated in the
 * iteration, suggesting that the element may not be the same one as when the
 * call was initially made. However, if a modification in the form of an
 * addition or removal happens on the same {@code ListNode}, then the
 * iterator will throw a {@code ConcurrentModificationException} as a result
 * of a compromise to the integrity of the node. This exception is not thrown
 * if an element is replaced using the {@code set()} method because there
 * would be no change in index to prompt the exception.
 * <p>
 * Since the {@code ScalableList} type is a {@code ManagedObject}, it is
 * important to note that applications which instantiate it should be
 * responsible for removing it from the data manager. This can be done by
 * statically obtaining the {@code DataManager} through the {@code AppContext}
 * via the call
 * {@code AppContext.getDataManager().removeObject(ManagedObject)}.
 * <p>
 * Contrary to the {@code ScalableList}, iterators both within and provided
 * by the {@code ScalableList} type are not {@code ManagedObject}s.
 * Therefore, they are not stored within the data manager and do not need to
 * be removed using the {@code DataManager}.
 * <p>
 * Since the list is capable of containing many elements, applications which
 * use iterators to traverse elements should be aware that prolonged iterative
 * tasks have the potential to lock out other concurrent tasks on the list.
 * Therefore, it is highly recommended that potentially long iterations be
 * broken up into smaller tasks. This strategy improves the concurrency of the
 * {@code ScalableList} as it reduces the locked elements owned by any one
 * task.
 * <p>
 * 
 * @param <E>
 *                the type of the elements stored in the {@code ScalableList}
 */
public class ScalableList<E> extends AbstractList<E> implements
	ManagedObject, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default value for the {@code branchingFactor} if one is not explicitly
     * set.
     */
    public static final int DEFAULT_BRANCHING_FACTOR = 5;

    /**
     * Default value for the {@code bucketSize} if one is not explicitly set.
     */
    public static final int DEFAULT_BUCKET_SIZE = 10;

    /**
     * The top node in the tree
     */
    private ManagedReference<TreeNode<E>> root;

    /**
     * A reference to the head node of the list.
     */
    private ManagedReference<DummyConnector<E>> headRef;
    /**
     * A reference to the tail of the list. This makes appending to the list a
     * constant-time operation.
     */
    private ManagedReference<DummyConnector<E>> tailRef;

    /**
     * If non-null, a runnable to call when a task that asynchronously removes
     * nodes is done -- used for testing. Note that this method is called
     * during the transaction that completes the removal.
     */
    private static volatile Runnable noteDoneRemoving = null;

    /**
     * The maximum number of elements a {@code ListNode} can contain. This
     * number should be small enough to enable concurrency but large enough to
     * contain a reasonable number of nodes. If it is not explicitly set, it
     * defaults to a value of 10.
     */
    private int bucketSize = DEFAULT_BUCKET_SIZE;

    /**
     * The maximum number of children contained in a TreeNode<E>; this
     * parameter is passed to the TreeNode<E> during instantiation. If it is
     * not explicitly set, it defaults to a value of 5.
     */
    private int branchingFactor = DEFAULT_BRANCHING_FACTOR;

    /**
     * Represents the number of elements removed from an
     * {@code AsynchronousClearTask}. This variable is primarily for testing
     * purposes.
     */
    protected int removedElements = 0;

    /**
     * Represents the number of elements removed from an
     * {@code AsynchronousClearTask}. This variable is primarily for testing
     * purposes.
     */
    protected int removedListNodes = 0;

    /**
     * Represents the number of elements removed from an
     * {@code AsynchronousClearTask}. This variable is primarily for testing
     * purposes.
     */
    protected int removedTreeNodes = 0;

    /*
     * IMPLEMENTATION
     */

    /**
     * Constructor which creates a {@code ScalableList} object with default
     * values for the {@code bucketSize} and {@code branchingFactor}.
     */
    public ScalableList() {
	TreeNode<E> t = new TreeNode<E>(this, null);
	headRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	tailRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	setRoot(t);
    }

    /**
     * Constructor which creates a {@code ScalableList} object with the
     * {@code branchingFactor} and {@code bucketSize} supplied as a parameter.
     * The {@code bucketSize} can be any integer larger than 0, however the
     * {@code branchingFactor} must be larger than 1 so that the tree can be
     * meaningful. Otherwise, it would only be able to grow to a maximum size
     * of {@code bucketSize} since branching could not introduce any
     * additional children.
     * 
     * @param branchingFactor
     *                the number of children each node should have. A
     *                {@code branchingFactor} of 2 means that the tree
     *                structure is binary.
     * @param bucketSize
     *                the size of each partitioned list. This value must be a
     *                positive integer (larger than 0).
     * @throws IllegalArgumentException
     *                 when the arguments are too small
     */
    public ScalableList(int branchingFactor, int bucketSize) {
	isLegal(branchingFactor, bucketSize);

	TreeNode<E> t = new TreeNode<E>(this, null);
	headRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	tailRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	setRoot(t);

	this.bucketSize = bucketSize;
	this.branchingFactor = branchingFactor;
    }

    /**
     * Constructor which creates a {@code ScalableList} object with the
     * {@code branchingFactor}, {@code bucketSize}, and a {@code collection}
     * supplied as parameters. The {@code bucketSize} can be any integer
     * larger than 0, however the {@code branchingFactor} must be larger than
     * 1 so that the tree can be meaningful. Otherwise, it would only be able
     * to grow to a maximum size of {@code bucketSize} since branching could
     * not introduce any additional children. The {@code collection}
     * represents a {@code Collection} of elements which will be added to the
     * newly formed {@code ScalableList}.
     * 
     * @param branchingFactor
     *                the number of children each node should have. A
     *                {@code branchingFactor} of 2 means that the tree
     *                structure is binary.
     * @param bucketSize
     *                the size of each partitioned list. This value must be a
     *                positive integer (larger than 0).
     * @param collection
     *                a collection of objects to initially populate the
     *                {@code ScalableList}
     */
    public ScalableList(int branchingFactor, int bucketSize,
	    Collection<E> collection) {

	isLegal(branchingFactor, bucketSize);

	TreeNode<E> t = new TreeNode<E>(this, null);
	headRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	tailRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	setRoot(t);

	this.bucketSize = bucketSize;
	this.branchingFactor = branchingFactor;

	this.addAll(collection);
    }

    ScalableList<E> getScalableList() {
	return this;
    }

    /**
     * Retrieves the {@code branchingFactor} for the list.
     * 
     * @return the {@code branchingFactor}
     */
    int getBranchingFactor() {
	return branchingFactor;
    }

    /**
     * Retrieves the {@code bucketSize} for the {@code ListNode} elements.
     * 
     * @return the {@code bucketSize}
     */
    int getBucketSize() {
	return bucketSize;
    }

    /**
     * Ensure that the parameters are valid.
     * 
     * @param branchingFactor
     *                the branching factor
     * @param bucketSize
     *                the maximum number of elements in a {@code ListNode}
     * @throws IllegalArgumentException
     *                 if one of the values is invalid
     */
    private void isLegal(int branchingFactor, int bucketSize) {
	if (bucketSize < 1) {
	    throw new IllegalArgumentException("Cluster size must "
		    + "be an integer larger than 0");
	}
	if (branchingFactor < 2) {
	    throw new IllegalArgumentException("Max child size must "
		    + "be an integer larger than 1");
	}
    }

    /**
     * Appends the specified element to the end of this list (optional
     * operation). Lists that support this operation may place limitations on
     * what elements may be added to this list. In particular, some lists will
     * refuse to add null elements, and others will impose restrictions on the
     * type of elements that may be added. List classes should clearly specify
     * in their documentation any restrictions on what elements may be added.
     * 
     * @param e
     *                element to add
     * @throws NullPointerException
     *                 if the element is null
     * @return if the operation was successful: true if so, false otherwise
     */
    public boolean add(E e) {
	if (e == null) {
	    throw new NullPointerException("Element cannot be null");
	}

	// first item to add into the list
	if (size() == 0) {
	    return getTail().append(e);
	}

	// otherwise, add it at the end since no index
	// and propagate change to parents
	boolean result = getTail().append(e);

	// update the tail in case it has changed.
	ListNode<E> next = getTail().next();
	if (next != null) {
	    setTail(next);
	}

	return result;
    }

    /**
     * Inserts the specified element at the specified position in this list
     * (optional operation). Shifts the element currently at that position (if
     * any) and any subsequent elements to the right (adds one to their
     * indices).
     * 
     * @param index
     *                the index
     * @param e
     *                the element to add
     * @throws IllegalArgumentException
     *                 if either the index is out of bounds
     * @throws NullPointerException
     *                 if the supplied element is null or if the list's head
     *                 cannot be obtained
     */
    public void add(int index, E e) {
	isValidIndex(index);

	// Check for the different boundary cases
	if (e == null) {
	    throw new NullPointerException("Element cannot be null");

	} else if (size() == 0 && index == 0) {
	    getTail().append(e);
	    return;

	} else if (getHead() == null) {
	    throw new NullPointerException("Cannot add to index " + index
		    + " on an empty list");
	}

	// otherwise, add it to the specified index.
	// This requires a search of the list nodes.
	SearchResult<E> sr = getNode(index);
	sr.getListNode().insert(sr.getOffset(), e);
    }

    /**
     * Removes all of the elements from this list. This list will be empty
     * after this call returns (unless it throws an exception).
     */
    public void clear() {

	// If the head is null, then the ScalableList is
	// not initialized; therefore, no work to do.
	if (getHead() == null) {
	    return;
	}

	AsynchronousClearTask<E> clearTask = new AsynchronousClearTask<E>(
		getHead());

	// Otherwise, schedule asynchronous task here
	// which will delete the list and replace it
	// with an empty instance.
	AppContext.getTaskManager().scheduleTask(clearTask);

	TreeNode<E> t = new TreeNode<E>(this, null);
	headRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	tailRef = AppContext.getDataManager().createReference(
		new DummyConnector<E>(t.getChild()));
	setRoot(t);

	// Collect removal information for testing purposes
	removedElements = clearTask.removedElements;
	removedListNodes = clearTask.removedListNodes;
	removedTreeNodes = clearTask.removedTreeNodes;
    }

    /**
     * Returns true if this list contains the specified element. More
     * formally, returns true if and only if this list contains at least one
     * element e such that (o==null ? e==null : o.equals(e)).
     * 
     * @param o
     *                the object which may be in the list
     * @return whether the object is contained in the list; true if so, false
     *         otherwise
     */
    public boolean contains(Object o) {
	if (o == null) {
	    return false;
	}
	return (indexOf(o) != -1);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsAll(Collection<?> c) {
	Iterator<?> iter = c.iterator();
	Object object = null;

	// Iterate through all elements in the collection
	while (iter.hasNext()) {
	    object = iter.next();

	    // If there is at least one not found, then
	    // return false.
	    if (!contains(object)) {
		return false;
	    }
	}
	return true;
    }

    /**
     * {@inheritDoc}
     */
    public int indexOf(Object o) {
	int listIndex = 0;
	ScalableListNodeIterator<E> iter = new ScalableListNodeIterator<E>(
		getHead());
	ListNode<E> n;
	int index;

	while (iter.hasNext()) {
	    n = (ListNode<E>) iter.next();
	    index = n.getSubList().indexOf(o);

	    if (index != -1) {
		return listIndex + index;
	    }
	    listIndex += n.size();
	}
	return -1;
    }

    /**
     * {@inheritDoc}
     */
    public int lastIndexOf(Object obj) {
	int listIndex = 0;
	int absIndex = -1;
	int index;
	ScalableListNodeIterator<E> iter = new ScalableListNodeIterator<E>(
		getHead());
	ListNode<E> n = null;

	// For every list node encountered, check for an
	// instance of the supplied object
	while (iter.hasNext()) {
	    n = iter.next();
	    index = n.getSubList().lastIndexOf(obj);

	    // Save the most recent occurrence of a matching index
	    // but keep searching in case we find another in another
	    // node.
	    if (index != -1) {
		absIndex = listIndex + index;
	    }
	    listIndex += n.size();
	}
	return absIndex;
    }

    /**
     * Determines if the provided index is valid; that is, whether it is a
     * natural number (not negative). If the index is negative, then an
     * {@code IndexOutOfBoundsException} is thrown.
     * 
     * @param index
     *                the index to check for validity
     * @throws IndexOutOfBoundsException
     *                 if the index is negative
     */
    private void isValidIndex(int index) {
	// set the upper bound for the index
	int upperIndex = size() - 1;
	if (upperIndex < 0) {
	    upperIndex = 0;
	}

	if (index < 0 || index > upperIndex) {
	    throw new IndexOutOfBoundsException("Index " + index
		    + " is out of bounds");
	}
    }

    /**
     * {@inheritDoc}
     */
    public E remove(int index) {
	isValidIndex(index);
	SearchResult<E> sr = getNode(index);
	ListNode<E> n = sr.getListNode();
	if (n == null) {
	    return null;
	}

	// Performs any relinking in case the removed
	// ListNode<E> was the head or tail
	E e = n.remove(sr.getOffset());
	relinkIfNecessary(n);

	return e;
    }

    /**
     * {@inheritDoc} This operation preserves the order of the elements in the
     * list and keeps multiple copies of the same elements if they exist.
     */
    public boolean retainAll(Collection<?> c) {
	Iterator<E> iter = new ScalableListIterator<E>(getHead());
	ArrayList<E> newList = new ArrayList<E>();
	E e = null;

	// For each element in the overall list,
	// if it is contained in the supplied
	// collection, then we add it to a
	// new list instance. Once finished, we
	// will add everything from this into
	// an emptied list (this).
	while (iter.hasNext()) {
	    e = iter.next();

	    // Add the node to the new collection
	    if (c.contains(e)) {
		newList.add(e);
	    }
	}

	// Clear the current list and add all
	// elements from the temporary collection.
	clear();
	return addAll(newList);
    }

    /**
     * Updates the ScalableList's head and/or tail in the event that it was
     * removed.
     * 
     * @param n
     *                the {@code ListNode} which may have been removed
     */
    private void relinkIfNecessary(ListNode<E> n) {
	if (n == null) {
	    return;
	}
	// Store values before they are deleted
	ListNode<E> next = n.next();
	ListNode<E> prev = n.prev();

	// Current values for head and tail
	ListNode<E> head;
	ListNode<E> tail;

	// Try retrieving the head and tail. In the event an
	// ObjectNotFoundException is thrown, then we just removed it.
	// Replace it with null for now.
	try {
	    head = getHead();
	} catch (ObjectNotFoundException onfe) {
	    head = null;
	}
	try {
	    tail = getTail();
	} catch (ObjectNotFoundException onfe) {
	    tail = null;
	}

	// Check whether we need to update the head or tail.
	if (head == null) {
	    // Check if we need to search in another TreeNode<E>
	    if (next != null) {
		setHead(next);
	    } else {
		setHead(null);
	    }
	}
	// Update the tail
	if (tail == null) {
	    // Check if we need to search in another TreeNode<E>
	    if (prev != null) {
		setTail(prev);
	    } else {
		setTail(null);
	    }
	}
    }

    /**
     * Retrieves the head {@code ListNode}.
     * 
     * @return the head {@code ListNode} if it exists
     * @throws ObjectNotFoundException
     *                 if no reference exists
     */
    private ListNode<E> getHead() {
	return headRef.get().getRefAsListNode();
    }

    /**
     * Retrieves the tail {@code ListNode}.
     * 
     * @return the tail {@code ListNode} if it exists
     */
    private ListNode<E> getTail() {
	return tailRef.get().getRefAsListNode();
    }

    /**
     * Sets the tail of the {@code ListNode} linked-list
     * 
     * @param newTail
     *                the tail
     */
    private void setTail(ListNode<E> newTail) {
	tailRef.getForUpdate().setRef(newTail);
    }

    /**
     * Sets the head of the {@code ListNode} linked-list
     * 
     * @param newHead
     *                the head
     */
    private void setHead(ListNode<E> newHead) {
	headRef.getForUpdate().setRef(newHead);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws IndexOutOfBoundsException
     *                 if either of the indices are seen to be less than 0, or
     *                 if the range is out of bounds
     * @throws IllegalArgumentException
     *                 if the {@code toIndex} is less than the
     *                 {@code fromIndex}
     */
    public List<E> subList(int fromIndex, int toIndex) {
	// Check for illegal values
	int maxIndex = size() - 1;
	if (fromIndex < 0 || toIndex < 0 || fromIndex > maxIndex ||
		toIndex > maxIndex) {
	    throw new IndexOutOfBoundsException("The indices " + fromIndex
		    + " and/or " + toIndex + " are invalid");
	} else if (toIndex < fromIndex) {
	    throw new IllegalArgumentException("The toIndex (" + toIndex
		    + ") cannot be less than the fromIndex (" + fromIndex
		    + ")");
	}

	// for each element between the indices (inclusive),
	// add to the new list
	List<E> sublist = new ArrayList<E>();
	for (int i = fromIndex; i <= toIndex; i++) {
	    sublist.add(get(i));
	}

	return sublist;
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     * 
     * @param index
     *                the index to set
     * @param obj
     *                the object to replace the existing value
     * @return the old value which was replaced
     */
    @SuppressWarnings("unchecked")
    public E set(int index, Object obj) {
	Object newValue = obj;
	if (obj == null) {
	    throw new NullPointerException(
		    "Value for set operation cannot be null");
	}
	SearchResult<E> sr = getNode(index);
	SubList<E> sublist = sr.getListNode().getSubList();
	E old = sublist.set(sr.getOffset(), newValue);

	// If the value is wrapped in an Element,
	// extract the element.
	if (old instanceof Element) {
	    old = ((Element<E>) old).getValue();
	}
	return old;
    }

    /**
     * Returns the element at the specified position in this list.
     * 
     * @param index
     *                the index to retrieve
     * @return the element at the supplied index
     */
    public E get(int index) {

	// Iterate through using the count. Use a get()
	// iterator because we are not modifying anything; hence, false.
	SearchResult<E> sr = getNode(index);
	SubList<E> sublist = sr.getListNode().getSubList();
	if (sublist == null) {
	    return null;
	}
	return sublist.get(sr.getOffset());
    }

    /**
     * {@inheritDoc}
     * 
     * @return an {@code Iterator} over the elements in the list
     */
    public Iterator<E> iterator() {
	ListNode<E> ln = getHead();
	return new ScalableListIterator<E>(ln);
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
	if (obj == null || !(obj instanceof ScalableList)) {
	    return false;
	}

	ScalableList<E> list = uncheckedCast(obj);
	return AppContext.getDataManager().createReference(this).equals(
		AppContext.getDataManager().createReference(list));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@code equals()} method requires that this method is defined.
     * 
     * @return the {@code hashCode} for this {@code Object}
     */
    public int hashCode() {

	// Checkstyle complains if this method does not exist
	return super.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object obj) {
	ScalableListNodeIterator<E> iter = new ScalableListNodeIterator<E>(
		getHead());
	ListNode<E> n = null;
	boolean removed = false;

	// Find and remove the object in the ListNode<E> that contains it
	while (iter.hasNext()) {
	    n = iter.next();
	    removed = n.remove(obj);
	    if (removed) {

		// Relink neighboring ListNodes in case this one was
		// removed due to being empty.
		relinkIfNecessary(n);
		break;
	    }

	}
	return removed;
    }

    /**
     * Retrieves the root {@code TreeNode} if it exists or null otherwise.
     * 
     * @return the root node, or null if it does not exist
     */
    private TreeNode<E> getRoot() {
	return this.root.get();
    }

    /**
     * Obtains the child of the root node, since the root node does not get
     * updated unless a split/removal occurs. For most operations, returning
     * the child is sufficient.
     * 
     * @return
     */
    private Node<E> getRootChild() {
	return getRoot().getChild();
    }

    /**
     * Sets the root element of the underlying tree structure. This is
     * necessary during a split or remove.
     * 
     * @param newRoot
     *                the {@code TreeNode} which is to be the new root
     */
    public void setRoot(TreeNode<E> newRoot) {
	AppContext.getDataManager().markForUpdate(this);
	if (newRoot == null) {
	    root = null;
	} else {
	    root = AppContext.getDataManager().createReference(newRoot);
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
	return (this.size() == 0);
    }

    /**
     * Performs the search for the desired ListNode<E>. This method
     * implicitly updates the offset value for the particular node we are
     * finding, meaning when we locate the ListNode<E>, the OffsetNode child
     * will be updated to represent the offset of the index we are searching
     * for.
     * 
     * @param index
     *                the absolute index in the entire list to search for the
     *                entry
     * @return the {@code ListNode<E>} which contains the element at the
     *         specified {@code index}
     */
    private SearchResult<E> getNode(int index) {
	// Recursive method to eventually return ListNode<E>
	// containing the desired index.
	return getRootChild().search(0, index + 1);
    }

    /**
     * Retrieves the size of the list. The size of the list is obtained by
     * performing a traversal of the root's children and adding each of their
     * sizes. For very large lists, this process can be somewhat expensive as
     * it does not occur in constant-time, but rather in a logarithmic time
     * proportional to the {@code branchingFactor}.
     * 
     * @return the number of elements in the list
     */
    public int size() {
	// This gives us a reference to the one and only
	// root TreeNode<E>, which parents the entire tree.
	Node<E> current = getRootChild();
	int size = 0;

	// Iterate through the first level of the tree to get
	// the sizes of each node.
	while (current != null) {
	    if (current instanceof TreeNode) {
		size += ((TreeNode<E>) current).size();
		current = ((TreeNode<E>) current).next();
	    } else {
		size += ((ListNode<E>) current).size();
		current = ((ListNode<E>) current).next();
	    }
	}
	return size;
    }

    /*
     * INLINE CLASSES
     */

    /**
     * The {@code DummyConnector} class is used as a junction point for two
     * {@code ManagedReference}s so that changes to the second
     * {@code ManagedReference} need not affect the top structure. The class
     * is fairly simple, only containing methods which enable the value to be
     * easily set and retrieved.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class DummyConnector<E> implements ManagedObject, Serializable {

	private static final long serialVersionUID = 3L;

	/**
	 * The pointer to the object.
	 */
	private ManagedReference<Node<E>> ref = null;

	/**
	 * Default no-argument constructor.
	 */
	DummyConnector() {
	    ref = null;
	}

	/**
	 * Constructor which accepts the object
	 * 
	 * @param reference
	 *                the element which this object is to point to
	 */
	DummyConnector(Node<E> reference) {
	    if (reference != null) {
		ref = AppContext.getDataManager().createReference(reference);
	    }
	}

	/**
	 * Sets the reference for this object.
	 * 
	 * @param newRef
	 *                the intended new reference
	 */
	void setRef(Node<E> newRef) {
	    AppContext.getDataManager().markForUpdate(this);
	    if (newRef != null) {
		ref = AppContext.getDataManager().createReference(newRef);
	    } else {
		ref = null;
	    }
	}

	/**
	 * Retrieves the reference as a {@code ListNode}.
	 * 
	 * @return the reference as a {@code ListNode}, or null if it does
	 *         not exist
	 */
	ListNode<E> getRefAsListNode() {
	    if (ref == null) {
		return null;
	    }
	    return (ListNode<E>) ref.get();
	}
    }

    /**
     * An object which forms a tree above the {@code ListNode<E>} linked list.
     * Each {@code TreeNode<E>} has a {@code child} reference to either one
     * {@code TreeNode<E>} or a {@code ListNode<E>}. The {@code TreeNode<E>}
     * also has a reference to its next and previous sibling and its parent.
     * <p>
     * The {@code TreeNode<E>} is intended to only track the size of its
     * descendant children and the number of children that it owns.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class TreeNode<E> implements ManagedObject, Serializable, Node<E> {

	// Public fields referenced during propagation
	public static final byte DECREMENT_SIZE = 0;
	public static final byte INCREMENT_SIZE = 1;
	public static final byte INCREMENT_CHILDREN_AND_SIZE = 2;
	public static final byte DECREMENT_CHILDREN_AND_SIZE = 3;

	private static final long serialVersionUID = 1L;

	// References to neighboring elements
	private ManagedReference<TreeNode<E>> nextRef;
	private ManagedReference<TreeNode<E>> prevRef;
	private ManagedReference<Node<E>> childRef;
	private ManagedReference<TreeNode<E>> parentRef;
	private ManagedReference<ScalableList<E>> owner;

	/**
	 * The maximum number of children this node can contain
	 */
	private int branchingFactor = ScalableList.DEFAULT_BRANCHING_FACTOR;

	/**
	 * The maximum number of elements which the underlying
	 * {@code ListNode} can store
	 */
	private int bucketSize = ScalableList.DEFAULT_BUCKET_SIZE;

	/**
	 * The number of elements that exist as descendants of this node
	 */
	private int size = 0;

	/**
	 * The number of immediate children
	 */
	private int childrenCount = 0;

	/**
	 * Constructor which is called on a split. This is used to create a
	 * new sibling and accepts a TreeNode argument which represents the
	 * new child it is to possess.
	 * 
	 * @param list
	 *                a reference to the owning {@code ScalableList}
	 * @param parent
	 *                the intended parent
	 * @param child
	 *                the child to be added underneath the new
	 *                {@code TreeNode}
	 * @param numberChildren
	 *                the total number of children that will exist once
	 *                the {@code TreeNode} is instantiated. This is based
	 *                on the nature of the linked list of
	 *                {@code ListNodes} attached to the {@code child}.
	 * @param size
	 *                the total number of elements that will exist under
	 *                the {@code TreeNode}, based on the elements that
	 *                already exist among the {@code child} and its
	 *                siblings.
	 */
	private TreeNode(ScalableList<E> list, TreeNode<E> parent,
		Node<E> child, int numberChildren, int size) {

	    owner = AppContext.getDataManager().createReference(list);
	    this.branchingFactor = owner.get().getBranchingFactor();
	    nextRef = null;
	    this.bucketSize = owner.get().getBucketSize();

	    // Set up links
	    DataManager dm = AppContext.getDataManager();
	    assert (child != null);
	    childRef = dm.createReference(child);

	    // this might be the root so parent could be null
	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }

	    childrenCount = numberChildren;
	    this.size = size;
	}

	/**
	 * Create a new TreeNode on account of a new leaf ({@code ListNode})
	 * being created.
	 * 
	 * @param list
	 *                the {@code ScalableList} which is the owner of this
	 *                structure
	 * @param parent
	 *                the intended parent
	 * @param e
	 *                an element to add into the empty {@code ListNode}
	 */
	public TreeNode(ScalableList<E> list, TreeNode<E> parent, E e) {
	    assert (e == null);

	    owner = AppContext.getDataManager().createReference(list);
	    this.branchingFactor = owner.get().getBranchingFactor();
	    nextRef = null;
	    this.bucketSize = owner.get().getBucketSize();
	    ListNode<E> n = new ListNode<E>(this, bucketSize, e);
	    size = n.size();
	    childrenCount = 1;
	    DataManager dm = AppContext.getDataManager();
	    childRef = dm.createReference((Node<E>) n);

	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    } else {
		parentRef = null;
	    }
	}

	/**
	 * Constructor which creates a {@code TreeNode} while specifying
	 * parameters for the node characteristics
	 * 
	 * @param list
	 *                the {@code ScalableList} owner of this structure
	 * @param parent
	 *                the intended parent
	 * @param branchingFactor
	 *                the maximum number of children
	 * @param bucketSize
	 *                the maximum number of elements for the underlying
	 *                {@code ListNode}
	 */
	public TreeNode(ScalableList<E> list, TreeNode<E> parent) {
	    owner = AppContext.getDataManager().createReference(list);
	    this.branchingFactor = owner.get().getBranchingFactor();
	    nextRef = null;
	    this.bucketSize = owner.get().getBucketSize();
	    ListNode<E> n = new ListNode<E>(this, bucketSize);
	    size = n.size();
	    DataManager dm = AppContext.getDataManager();
	    childRef = dm.createReference((Node<E>) n);
	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public TreeNode<E> prev() {
	    return prevRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setPrev(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    TreeNode<E> t = uncheckedCast(ref);
	    if (ref != null) {
		prevRef = AppContext.getDataManager().createReference(t);
	    } else {
		prevRef = null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public TreeNode<E> next() {
	    TreeNode<E> next;
	    if (nextRef == null) {
		return null;
	    }

	    try {
		next = nextRef.get();
	    } catch (ObjectNotFoundException onfe) {
		next = null;
	    }
	    return next;
	}

	/**
	 * Obtains the child of the current node. The child represents the
	 * head of the list of children. The {@code TreeNode} does not have
	 * references to all the children, so it uses knowledge of the head to
	 * iterate through them.
	 * 
	 * @return the child, which can be either a {@code TreeNode} or
	 *         {@code ListNode}, depending on the position of the current
	 *         node in the tree.
	 */
	public Node<E> getChild() {
	    Node<E> child;
	    if (childRef == null) {
		return null;
	    }
	    try {
		child = childRef.get();
	    } catch (ObjectNotFoundException onfe) {
		child = null;
	    }
	    return child;
	}

	/**
	 * Sets the child to be the supplied parameter as long as it is not
	 * null. This method also updates the size of the parent because
	 * changing the child suggests that a new size exists.
	 * 
	 * @param child
	 *                the new child
	 */
	public void setChild(Node<E> child, int size) {
	    AppContext.getDataManager().markForUpdate(this);
	    this.size = size;
	    if (child != null) {
		childRef = AppContext.getDataManager().createReference(child);
	    } else {
		childRef = null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public int size() {
	    return size;
	}

	/**
	 * A convenience method to increment the size of the {@code TreeNode}.
	 * Note that this differs from the children count; the latter of which
	 * is a quantity representing the number of children.
	 */
	public void increment() {
	    size++;
	}

	/**
	 * A convenience method to decrement the size of the {@code TreeNode}.
	 * Note that this differs from the children count; the latter of which
	 * is a quantity representing the number of children that are either
	 * {@code ListNode}s or {@code TreeNode}s.
	 */
	public void decrement() {
	    size--;
	}

	/**
	 * A convenience method which increments the number of children
	 * existing under a {@code TreeNode}. Note that this value is
	 * different from the size; the latter of which represents the total
	 * number of elements contained beneath this parent. In other words,
	 * all elements that are neither {@code ListNode}s or
	 * {@code TreeNode}s.
	 */
	public void incrementNumChildren() {
	    childrenCount++;
	}

	/**
	 * A convenience method which decrements the number of children
	 * existing under a {@code TreeNode}. Note that this value is
	 * different from the size; the latter of which represents the total
	 * number of elements contained beneath this parent. In other words,
	 * all elements that are neither {@code ListNode}s or
	 * {@code TreeNode}s.
	 */
	public void decrementNumChildren() {
	    childrenCount--;
	}

	/**
	 * Retrieves the number of immediate children beneath this node.
	 * 
	 * @return the number of immediate children
	 */
	public int getChildCount() {
	    return childrenCount;
	}

	/**
	 * Returns the parent of this node, which is guaranteed to be a
	 * {@code TreeNode}, or null.
	 * 
	 * @return the parent of this {@code TreeNode}, or null if none
	 *         exist.
	 */
	public TreeNode<E> getParent() {
	    TreeNode<E> parent;
	    if (parentRef == null) {
		return null;
	    }
	    try {
		parent = parentRef.get();
	    } catch (ObjectNotFoundException onfe) {
		parent = null;
	    }
	    return parent;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setNext(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    TreeNode<E> t = uncheckedCast(ref);
	    if (t != null) {
		nextRef = AppContext.getDataManager().createReference(t);
	    } else {
		nextRef = null;
	    }
	}

	/**
	 * Unlinks itself from the tree without performing a recursive
	 * deletion.
	 * 
	 * @return itself, after being unlinked
	 */
	public TreeNode<E> remove() {

	    // We may have to re-link siblings
	    if (prevRef != null) {
		if (nextRef != null) {
		    this.prev().setNext(this.next());
		    this.next().setPrev(this.prev());
		} else {
		    // relink tail
		    this.prev().setNext(null);
		}
	    } else if (nextRef != null) {
		// relink head
		this.next().setPrev(null);
	    }
	    AppContext.getDataManager().removeObject(this);
	    return this;
	}

	/**
	 * Determines where to split a list of children based on the list
	 * size. By default, this is set to {@code numberOfChildren}/2.
	 * 
	 * @param numberOfChildren
	 *                the number of children to split
	 * @return the index corresponding to the location where to split the
	 *         linked list
	 */
	private static int calculateSplitSize(int numberOfChildren) {
	    return numberOfChildren / 2;
	}

	/**
	 * This method walks up the tree and performs any {@code TreeNode<E>}
	 * splits if the children sizes have been exceeded.
	 */
	public boolean performSplitIfNecessary() {
	    TreeNode<E> newNode = null;
	    Node<E> tmp = (Node<E>) this.getChild();

	    // Check if we need to split
	    if (childrenCount > branchingFactor) {
		AppContext.getDataManager().markForUpdate(this);
		generateParentIfNecessary();

		// Perform split by creating and linking new sibling
		newNode = createAndLinkSibling(tmp, childrenCount);
		childrenCount = childrenCount -
			calculateSplitSize(childrenCount);

		// Link the new sibling to the tree
		newNode.setPrev(this);
		newNode.setNext(this.next());
		this.setNext(newNode);
		if (newNode.next() != null) {
		    newNode.next().setPrev(newNode);
		}

		return true;
	    }
	    return false;
	}

	/**
	 * Determines the nature of the sibling to be created. The Node
	 * created will always be a TreeNode<E>, but the type of child
	 * depends on whether it parents ListNodes, or other TreeNodes.
	 * 
	 * @param child
	 *                the {@code ManagedObject} which needs to be split
	 *                and placed under a new parent
	 * @param numberOfChildren
	 *                the number of children which the new sibling will
	 *                parent
	 * @return the {@TreeNode<E>} that not represents a sibling which is
	 *         connected to the tree
	 */
	private TreeNode<E> createAndLinkSibling(Node<E> child,
		int numberOfChildren) {
	    int halfway = calculateSplitSize(numberOfChildren);
	    int size = this.size;

	    // Create the new sibling
	    TreeNode<E> newNode = new TreeNode<E>(owner.get(), getParent());

	    // Iterate through the children and update references
	    Node<E> newChild = child;
	    int i = 0;
	    while (child != null) {

		// When we reach halfway, save the child as it
		// will be the child of the new TreeNode. When
		// we pass halfway, update the parents and
		// decrease the size.
		if (i == halfway) {
		    newChild = child;
		} else if (i > halfway) {
		    size -= child.size();
		    child.setParent(newNode);
		}
		child = child.next();
		i++;
	    }

	    // Update the new parent's child, its size,
	    // and our own size
	    newNode.setChild(newChild, size);
	    this.size -= size;

	    // Increment the parent's child count. We are
	    // guaranteed to have a parent because we
	    // either updated its size already or created
	    // a new one above.
	    getParent()
		    .propagateChanges(TreeNode.INCREMENT_CHILDREN_AND_SIZE);

	    return newNode;
	}

	/**
	 * This method creates a parent above the provided node so that any
	 * new siblings resulting from a split can be joined to the new
	 * parent. The decision to create a new parent depends on whether the
	 * supplied node is the root; only these nodes are orphaned while
	 * siblings have parents assigned to them.
	 * 
	 * @param t
	 *                the {@code TreeNode<E>} to check whether it requires
	 *                a parent
	 */
	private void generateParentIfNecessary() {
	    // If this is the currently existing root, make a new one
	    // and set the supplied node as the child
	    if (getParent() == null) {
		TreeNode<E> grandparent = new TreeNode<E>(owner.get(), null,
			this, 1, size());

		// Link the node to its new parent
		setParent(grandparent);
	    }
	}

	/**
	 * Sets the parent for the node.
	 * 
	 * @param parent
	 *                the intended parent
	 */
	public void setParent(TreeNode<E> parent) {
	    AppContext.getDataManager().markForUpdate(this);
	    if (parent != null) {
		parentRef = AppContext.getDataManager().createReference(
			parent);
	    } else {
		parentRef = null;
	    }
	}

	/**
	 * Propagates changes in the form of either an increment or decrement
	 * to the parents of the tree.
	 * 
	 * @param mode
	 *                the type of propagation which is to occur, specified
	 *                by static {@code TreeNode} fields
	 * @throws IllegalArgumentException
	 *                 when the mode is not recognized
	 */
	public void propagateChanges(byte mode) {

	    // Otherwise, make the change, depending on the mode
	    switch (mode) {
		// Increment the size and propagates this to parent
		case TreeNode.INCREMENT_SIZE:
		    this.increment();
		    break;

		// Decrements the size and prepares for removal
		// if necessary; both cases propagate to parent
		case TreeNode.DECREMENT_SIZE:
		    this.decrement();
		    if (size() == 0) {
			mode = TreeNode.DECREMENT_CHILDREN_AND_SIZE;
		    }
		    break;

		// Increments the number of children and size.
		// This case checks if subsequent splits are
		// necessary.
		case TreeNode.INCREMENT_CHILDREN_AND_SIZE:
		    this.increment();
		    this.incrementNumChildren();

		    // if another split took place, check parent
		    // if it needs to split too. Otherwise we are
		    // done.
		    if (performSplitIfNecessary()) {
			break;
		    }
		    return;

		    // Decrements the child size. If the node is
		    // empty, then it removes itself. Both cases
		    // propagate to parent
		case TreeNode.DECREMENT_CHILDREN_AND_SIZE:
		    this.decrement();
		    this.decrementNumChildren();

		    // remove this from the tree if empty, and
		    // decrement parent's number of children and
		    // size; otherwise, only propagate size
		    // decrementing
		    if (size() == 0) {
			remove();
		    } else {
			mode = TreeNode.DECREMENT_SIZE;
		    }
		    break;

		// If the mode was not recognized, throw an exception
		default:
		    throw new IllegalArgumentException(
			    "Supplied mode argument is not recognized; "
				    + "expected " + TreeNode.INCREMENT_SIZE
				    + " or " + TreeNode.DECREMENT_SIZE);
	    }

	    // Recursively call the method on our parent as long as
	    // a parent exists
	    TreeNode<E> parent = getParent();
	    if (parent != null) {
		parent.propagateChanges(mode);
	    } else {
		owner.getForUpdate().setRoot(this);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public SearchResult<E> search(int currentValue, int destIndex) {

	    TreeNode<E> t = this;

	    // iterate through siblings
	    while ((currentValue + t.size) < destIndex) {
		currentValue += t.size;
		t = t.next();

		// The specified index was too large; hence
		// throw an IndexOutOfBoundsException
		if (t == null) {
		    throw new IndexOutOfBoundsException("The index "
			    + destIndex + " is out of range.");
		}
	    }
	    return t.getChild().search(currentValue, destIndex);
	}
    }

    /**
     * An asynchronous task which iterates through the tree and removes all
     * children. This task is instantiated when a clear() command is issued
     * from the {@code ScalableList}. The success of this operation is
     * dependent on implemented {@code clear()} methods on each of the
     * underlying components.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    private static class AsynchronousClearTask<E> implements Serializable,
	    Task, ManagedObject {

	private static final long serialVersionUID = 4L;

	/**
	 * The maximum number of items to remove in a single iteration of the
	 * task
	 */
	private static final int MAX_OPERATIONS = 50;

	/**
	 * A reference to the current {@code TreeNode} whose children are
	 * being deleted
	 */
	private ManagedReference<Node<E>> current;

	/**
	 * A queue to hold onto the parents
	 */
	private ArrayList<TreeNode<E>> queue;

	/**
	 * Counter which holds onto the number of removed {@code TreeNode}s.
	 * Thia variable is for testing purposes.
	 */
	private int removedElements;

	/**
	 * Counter which holds onto the number of removed {@code ListNode}s.
	 * This variable is for testing purposes.
	 */
	private int removedListNodes;

	/**
	 * Counter which holds onto the number of removed {@code TreeNode}s.
	 * Thia variable is for testing purposes.
	 */
	private int removedTreeNodes;

	/**
	 * Constructor for the asynchronous task
	 * 
	 * @param root
	 *                the root node of the entire tree structure
	 */
	public AsynchronousClearTask(Node<E> node) {
	    if (node != null) {
		current = AppContext.getDataManager().createReference(node);
	    } else {
		current = null;
	    }
	    queue = new ArrayList<TreeNode<E>>();
	    removedTreeNodes = 0;
	    removedListNodes = 0;
	    removedElements = 0;
	}

	/**
	 * The entry point of the task to perform the clear.
	 */
	public void run() {
	    // Perform some work and check if we need to reschedule
	    if (doWork()) {
		AppContext.getDataManager().markForUpdate(this);
		AppContext.getTaskManager().scheduleTask(this);

	    } else {
		AppContext.getDataManager().removeObject(this);
		Runnable r = noteDoneRemoving;
		if (r != null) {
		    r.run();
		}
	    }

	}

	/**
	 * Starting at the bottom, remove some nodes but stop so that the task
	 * can reschedule itself.
	 * 
	 * @return
	 */
	private boolean doWork() {
	    // declare that we are done if current is null
	    if (current == null || current.get() == null) {
		return false;
	    }

	    // adds the parent to the queue if it doesn't already exist
	    enqueueIfNecessary(current.get().getParent());

	    // if there are ListNodes, get rid of them first. Otherwise
	    // use the queue to start removing TreeNodes.
	    if (current.get() instanceof ListNode) {

		// remove entries in ListNode, and if false, then we
		// are ready to start removing TreeNodes from queue
		if (!removeSomeElements()) {
		    current = AppContext.getDataManager().createReference(
			    (Node<E>) queue.remove(0));
		}

	    } else {
		// It is a TreeNode; use the queue. In here we will
		// check whether the current node is the root.
		// If it returns false, then we are done
		// (task exit condition).
		if (!removeSomeTreeNodes()) {
		    return false;
		}
	    }

	    // there is more work to be done
	    return true;
	}

	/**
	 * Removes MAX_OPERATION number of elements from the ScalableList and
	 * returns true if there is more work to be done. If there are no more
	 * elements to remove, then it will return false, signifying to the
	 * {@code AsynchronousClearTask} to start taking values from the queue
	 * 
	 * @return true if more work needs to be done, and false if there are
	 *         no more elements to remove.
	 */
	private boolean removeSomeElements() {
	    if (current == null || current.get() == null) {
		return false;
	    }

	    ListNode<E> currentListNode = uncheckedCast(current.get());
	    TreeNode<E> parent;
	    int count = 0;
	    E entry = null;

	    // Add the parent to the queue if it doesn't
	    // already exist
	    parent = currentListNode.getParent();
	    enqueueIfNecessary(parent);

	    // remove elements; delete the ones that
	    // are Element<E> objects
	    while (count < MAX_OPERATIONS) {
		// Exit condition -- declare that all ListNodes have
		// been removed
		if (currentListNode == null) {
		    return false;
		}

		// Repeatedly remove the head element in the
		// ListNode as long as one exists.
		if (currentListNode.size() > 0) {
		    AppContext.getDataManager()
			    .markForUpdate(currentListNode);
		    entry = currentListNode.remove(0);
		    count++;
		    removedElements++;

		    // If the entry was an Element object, delete it
		    // since the user did not have it persist
		    if (entry instanceof Element) {
			AppContext.getDataManager().removeObject(entry);
		    }
		} else {
		    // Remove SubList attached to this ListNode ManagedObject.
		    // We don't need to remove the ListNode as the remove()
		    // method above already takes care of it.
		    AppContext.getDataManager().removeObject(
			    currentListNode.getSubList());
		    currentListNode = currentListNode.next();
		    removedListNodes++;

		    // Add the parent to the queue if it doesn't
		    // already exist
		    if (currentListNode != null) {
			parent = currentListNode.getParent();
			enqueueIfNecessary(parent);
		    }
		}
	    }

	    // If we are leaving this method, then save the
	    // currentTreeNode so we can continue when we come back
	    current = AppContext.getDataManager().createReference(
		    (Node<E>) currentListNode);

	    return true;
	}

	/**
	 * add parent to queue if not already there. The queue will be popped
	 * when we are ready to start removing parents
	 * 
	 * @param parent
	 *                the TreeNode to add if it doesn't already exist.
	 */
	private void enqueueIfNecessary(TreeNode<E> parent) {
	    if (!queue.contains(parent) && parent != null) {
		queue.add(parent);
	    }
	}

	/**
	 * Delete some TreeNodes from the queue
	 * 
	 * @return true if there are more nodes to remove, or false if
	 *         complete
	 */
	private boolean removeSomeTreeNodes() {
	    TreeNode<E> currentTreeNode = null;
	    int count = 0;

	    // Remove as many nodes as the maximum number of
	    // operations allows
	    while (count < MAX_OPERATIONS) {
		if (current == null) {
		    return false;
		}

		currentTreeNode = uncheckedCast(current.get());

		// Add parent to the queue if it doesn't already exist
		enqueueIfNecessary(currentTreeNode.getParent());

		// remove node
		AppContext.getDataManager().markForUpdate(currentTreeNode);
		AppContext.getDataManager().removeObject(currentTreeNode);
		removedTreeNodes++;

		// pop the queue to remove the next TreeNode, or if none,
		// then we are done.
		if (queue.size() > 0) {
		    currentTreeNode = uncheckedCast(queue.remove(0));
		} else {
		    return false;
		}
		count++;
	    }

	    // If we are leaving this method, then save the
	    // currentTreeNode so we can continue when we come back
	    current = AppContext.getDataManager().createReference(
		    (Node<E>) currentTreeNode);

	    return true;
	}

	/**
	 * Returns the number of removed elements from the operation
	 * 
	 * @return number of removed elements
	 */
	public int getNumberOfRemovedElements() {
	    return removedElements;
	}

	/**
	 * Returns the number of removed {@code ListNode}s from the operation
	 * 
	 * @return number of removed {@code ListNodes}
	 */
	public int getNumberOfRemovedListNodes() {
	    return removedListNodes;
	}

	/**
	 * Returns the number of removed {@code TreeNode}s from the operation
	 * 
	 * @return number of removed {@code TreeNodes}
	 */
	public int getNumberOfRemovedTreeNodes() {
	    return removedTreeNodes;
	}
    }

    /**
     * This class represents a stored entity of the list. It is a wrapper for
     * any object that is stored in the list so that the list can refer to it
     * by using a ManagedReference, rather than the actual object itself. This
     * makes managing sublists less intensive as each reference to an
     * underlying entity is a fixed size.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class Element<E> implements Serializable, ManagedObject {

	private static final long serialVersionUID = 5L;

	/**
	 * The stored value which is not a {@code ManagedObject}. If it were
	 * a {@code ManagedObject}, then it would not need to be enveloped by
	 * an {@code Element} wrapper.
	 */
	private E value;

	/**
	 * Constructor for creating an {@code Element}.
	 * 
	 * @param e
	 *                the element to store within the {@code Element}
	 *                {@code ManagedObject}
	 */
	public Element(E e) {
	    value = e;
	}

	/**
	 * Retrieves the element.
	 * 
	 * @return the element stored by this object
	 */
	public E getValue() {
	    return value;
	}

	/**
	 * Sets the value of the element
	 * 
	 * @param e
	 *                the new value to set
	 */
	public void setValue(E e) {
	    assert (e != null);
	    AppContext.getDataManager().markForUpdate(this);
	    value = e;
	}
    }

    /**
     * This iterator walks through the {@code ListNode<E>}s which parent the
     * {@code SubList}s. An iterator for this type of element is necessary to
     * support the other element iterator as well as {@code indexOf()}
     * operations.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class ScalableListNodeIterator<E> implements Serializable,
	    Iterator<ListNode<E>> {

	/**
	 * The current position of the iterator
	 */
	private ListNode<E> current;

	/**
	 * The head of the iterator which represents the starting location of
	 * the iteration process
	 */
	private ListNode<E> head;

	private static final long serialVersionUID = 7L;

	/**
	 * Constructor to create a new iterator.
	 * 
	 * @param head
	 *                the head {@code ListNode} from which to begin
	 *                iterating
	 */
	public ScalableListNodeIterator(ListNode<E> head) {
	    current = null;
	    this.head = head;
	}

	/**
	 * Two cases to check: the most common case is that the next
	 * {@code ListNode<E>} is a sibling of the current {@code ListNode<E>}.
	 * Otherwise, we have to locate a cousin, and return if it exists or
	 * not.
	 */
	public boolean hasNext() {
	    // Check if there was an element to begin with
	    if (current == null && head == null) {
		return false;

	    } else if (head != null) {

		// Return the first element
		current = head;
		head = null;
	    } else {

		// Setup the next element if it exists
		if (current.next() != null) {
		    current = current.next();
		} else {
		    return false;
		}
	    }
	    return true;
	}

	/**
	 * Returns the next {@code ListNode<E>} in order from the list
	 * 
	 * @return the next {@code ListNode}
	 * @throws NoSuchElementException
	 *                 if there exists no next sibling
	 */
	public ListNode<E> next() {
	    if (current == null) {
		throw new NoSuchElementException("There is no next element");
	    }

	    return current;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @deprecated this operation is not supported. Removal of elements
	 *             should be performed using the {@code ScalableList} API.
	 */
	public void remove() {
	    throw new UnsupportedOperationException(
		    "This method is not supported");
	}
    }

    /**
     * This class represents an iterator of the contents of the list.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class ScalableListIterator<E> implements Serializable, Iterator<E> {

	/**
	 * The current {@code ListNode} of the iterative process
	 */
	private ListNode<E> currentNode;

	/**
	 * The iterator responsible for iterating through the
	 * {@code ListNodes}
	 */
	private ScalableListNodeIterator<E> listNodeIter;

	/**
	 * The iterator responsible for iterating through the stored elements
	 * of the {@code ScalableList}
	 */
	private Iterator<ManagedReference<ManagedObject>> iter;

	/**
	 * The value for the current {@code ListNode} to determine if any
	 * changes have taken place since the last time it was accessed
	 */
	private long listNodeReferenceValue = -1;

	private static final long serialVersionUID = 8L;

	/**
	 * Constructor used to create a {@code ScalableListIterator} for the
	 * underlying elements in the {@code ScalableList}.
	 * 
	 * @param head
	 *                the head {@code ListNode} of the collection
	 */
	public ScalableListIterator(ListNode<E> head) {
	    listNodeIter = new ScalableListNodeIterator<E>(head);

	    // Prepare by getting first ListNode
	    if (listNodeIter.hasNext()) {
		currentNode = (ListNode<E>) listNodeIter.next();
		listNodeReferenceValue = currentNode.getDataIntegrityValue();
	    } else {
		currentNode = null;
	    }

	    // Do a last check to make sure we are getting non-null values.
	    // If the value is null, then return an empty iterator.
	    if (currentNode == null || currentNode.getSubList() == null) {
		iter = (new ArrayList<ManagedReference<ManagedObject>>())
			.iterator();
		return;
	    }

	    // Set up the ListIterator, but do not populate
	    // current until hasNext() has been called.
	    iter = currentNode.getSubList().getElements().iterator();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws ConcurrentModificationException
	 *                 if the {@code ListNode} that the iterator is
	 *                 pointing to has been modified to (addition or
	 *                 removal) by someone else.
	 */
	public E next() {

	    // Check the integrity of the ListNode to see if
	    // any changes were made since we last operated.
	    // Throw a ConcurrentModificationException if so.
	    if (currentNode.getDataIntegrityValue() != listNodeReferenceValue) {
		throw new ConcurrentModificationException(
			"The ListNode has been modified.");
	    }

	    // Retrieve the value from the list
	    ManagedReference<E> ref = uncheckedCast(iter.next());
	    if (ref != null) {
		Object obj = ref.get();

		// In case we wrapped the item with an
		// Element object, fetch the value.
		if (obj instanceof Element) {
		    Element<E> tmp = uncheckedCast(obj);
		    return tmp.getValue();
		}
		return ref.get();
	    } else {
		return null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean hasNext() {

	    // If there is an element in the iterator still,
	    // then simply return true since it will be the
	    // next element to be returned.
	    if (iter.hasNext()) {
		// Check the current integrity ListNode value
		listNodeReferenceValue = currentNode.getDataIntegrityValue();

		return true;
	    }

	    // Otherwise, we need to fetch the next ListNode
	    // and construct a list iterator from that
	    if (listNodeIter.hasNext()) {
		currentNode = listNodeIter.next();
		iter = currentNode.getSubList().getElements().iterator();
		return hasNext();
	    }
	    return false;
	}

	/**
	 * @deprecated This operation is not officially supported. Removal of
	 *             nodes is achieved by the {@code ScalableList} API.
	 */
	public void remove() {
	    throw new UnsupportedOperationException(
		    "Removal of nodes is achieved "
			    + "through the ScalableList API");
	}
    }

    /**
     * Node which parents a {@code SubList<E>}. These nodes can be considered
     * as the leaf nodes of the tree and contain references to a portion of
     * the list. A {@code ListNode}'s parent is always a {@code TreeNode}
     * since they are the deepest organizational element of the
     * {@code ScalableList}. {@code ListNode}s are arranged in a
     * doubly-linked list, each having a reference to its parent.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class ListNode<E> implements ManagedObject, Serializable, Node<E> {

	private static final int DATA_INTEGRITY_STARTING_VALUE = 
	    Integer.MIN_VALUE;

	// References to neighbors
	private ManagedReference<SubList<E>> subListRef;
	private ManagedReference<ListNode<E>> nextRef;
	private ManagedReference<ListNode<E>> prevRef;
	private ManagedReference<TreeNode<E>> parentRef;

	private static final long serialVersionUID = 9L;

	/**
	 * A value which increments when changes are made to the node. This is
	 * used by iterators to deal with concurrent modifications
	 */
	private int dataIntegrityVal;

	/**
	 * The number of elements contained in the SubList.
	 */
	private int count;

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent
	 *                the intended parent
	 * @param maxSize
	 *                the maximum number of elements that can be stored
	 */
	public ListNode(TreeNode<E> parent, int maxSize) {
	    SubList<E> sublist = new SubList<E>(maxSize);
	    count = sublist.size();
	    DataManager dm = AppContext.getDataManager();
	    subListRef = dm.createReference(sublist);
	    nextRef = null;
	    prevRef = null;
	    dataIntegrityVal = DATA_INTEGRITY_STARTING_VALUE;

	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent
	 *                the intended parent
	 * @param e
	 *                an element which is to be stored as the first item
	 *                in the list
	 */
	public ListNode(TreeNode<E> parent, E e) {
	    SubList<E> sublist = new SubList<E>(e);
	    count = sublist.size();

	    DataManager dm = AppContext.getDataManager();
	    subListRef = dm.createReference(sublist);
	    nextRef = null;
	    prevRef = null;

	    dataIntegrityVal = DATA_INTEGRITY_STARTING_VALUE;
	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent
	 *                the intended parent
	 * @param maxSize
	 *                the maximum number of elements that can be stored
	 * @param e
	 *                an element which is to be stored as the first item
	 *                in the list
	 */
	public ListNode(TreeNode<E> parent, int maxSize, E e) {
	    SubList<E> sublist = new SubList<E>(maxSize, e);
	    count = sublist.size();
	    DataManager dm = AppContext.getDataManager();
	    subListRef = dm.createReference(sublist);
	    nextRef = null;
	    prevRef = null;
	    dataIntegrityVal = DATA_INTEGRITY_STARTING_VALUE;

	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent
	 *                the intended parent
	 * @param maxSize
	 *                the maximum number of elements that can be stored
	 * @param list
	 *                a list of items which are to be added into the empty
	 *                list
	 */
	public ListNode(TreeNode<E> parent, int maxSize,
		ArrayList<ManagedReference<ManagedObject>> list) {
	    SubList<E> sublist = new SubList<E>(maxSize, list);
	    count = sublist.size();
	    DataManager dm = AppContext.getDataManager();
	    subListRef = dm.createReference(sublist);
	    nextRef = null;
	    prevRef = null;
	    dataIntegrityVal = DATA_INTEGRITY_STARTING_VALUE;

	    if (parent != null) {
		parentRef = dm.createReference(parent);
	    }
	}

	/**
	 * Returns the data integrity value of the {@code ListNode}
	 * 
	 * @return the current data integrity value
	 */
	public int getDataIntegrityValue() {
	    return dataIntegrityVal;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setNext(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    ListNode<E> node = uncheckedCast(ref);
	    if (ref != null) {
		nextRef = AppContext.getDataManager().createReference(node);
	    } else {
		nextRef = null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void setParent(TreeNode<E> parent) {
	    AppContext.getDataManager().markForUpdate(this);
	    if (parent != null) {
		parentRef = AppContext.getDataManager().createReference(
			parent);
	    } else {
		parentRef = null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public ListNode<E> next() {
	    if (nextRef == null) {
		return null;
	    }
	    return nextRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setPrev(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    ListNode<E> n = uncheckedCast(ref);
	    if (ref != null) {
		prevRef = AppContext.getDataManager().createReference(n);
	    } else {
		prevRef = null;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public ListNode<E> prev() {
	    if (prevRef == null) {
		return null;
	    }
	    return prevRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	public int size() {
	    return count;
	}

	/**
	 * Returns the {@code SubList} object which contains a subset of the
	 * elements in the collection.
	 * 
	 * @return the {@code SubList} containing list elements, or null if
	 *         one has not yet been instantiated
	 */
	public SubList<E> getSubList() {
	    SubList<E> list;
	    if (subListRef == null) {
		return null;
	    }
	    try {
		list = subListRef.get();
	    } catch (ObjectNotFoundException onfe) {
		list = null;
	    }
	    return list;
	}

	/**
	 * Appends the supplied object to the list and performs a split if
	 * necessary.
	 * 
	 * @param e
	 *                the element to append
	 * @return whether the operation was successful; true if so, false
	 *         otherwise
	 */
	public boolean append(E e) {
	    boolean result = getSubList().append(e);
	    if (result) {
		count++;
		dataIntegrityVal++;

		// check if we need to split; i.e. if we have exceeded
		// the specified list size.
		if (count > getSubList().getMaxChildrenAppend()) {
		    split();
		} else {
		    updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
		}
	    }
	    return result;
	}

	/**
	 * Inserts the supplied value at the given index. The index is
	 * relative to the current list and not the global collection.
	 * 
	 * @param index
	 *                the index to insert the value, relative to the
	 *                current {@code SubList}
	 * @param e
	 *                the value to insert
	 */
	public void insert(int index, E e) {
	    getSubList().insert(index, e);
	    count++;
	    dataIntegrityVal++;

	    // check if we need to split; i.e. if we have exceeded
	    // the specified list size.
	    if (count > getSubList().maxChildren) {
		split();
	    } else {
		updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
	    }
	}

	/**
	 * Removes the object at the specified index of the sublist. The index
	 * argument is not an absolute index; it is a relative index which
	 * points to a valid index in the list.
	 * <p>
	 * For example, if there are five ListNodes with a cluster size of
	 * five, the item with an absolute index of 16 corresponds to an
	 * element in the fourth ListNode<E>, with a relative offset of 1.
	 * 
	 * @param index
	 *                the index corresponding to an element in the list
	 *                (not an absolute index with respect to the
	 *                {@code ScalableList} object
	 * @return the element that was removed
	 */
	public E remove(int index) {
	    E old = getSubList().remove(index);
	    if (old != null) {
		count--;
		dataIntegrityVal++;
		updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);

		// remove list node if necessary
		checkRemoveListNode();
	    }
	    return old;
	}

	/**
	 * Removes the {@code Object} from the {@code SubList<E>}, if it
	 * exists.
	 * 
	 * @param obj
	 *                the {@code Object} to remove
	 * @return whether the object was removed or not; true if so, false
	 *         otherwise
	 */
	public boolean remove(Object obj) {
	    boolean result = getSubList().remove(obj);

	    // If a removal took place, then update
	    // count information accordingly
	    if (result) {
		count--;
		dataIntegrityVal++;
		updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);

		// remove list node if it is empty
		checkRemoveListNode();
	    }
	    return result;
	}

	/**
	 * Removes the {@code Element} from the {@code SubList<E>}, if it
	 * exists.
	 * 
	 * @param e
	 *                the {@code Element} to remove
	 * @return whether the object was removed or not; true if so, false
	 *         otherwise
	 */
	public boolean remove(Element<E> e) {
	    boolean result = getSubList().remove(e);
	    if (result) {
		count--;
		dataIntegrityVal++;

		// remove list node if necessary
		checkRemoveListNode();
	    }
	    return result;
	}

	/**
	 * A method that determines how to remove an empty {@code ListNode<E>}
	 * from a list of other {@code ListNode<E>}s. Update previous to
	 * point to next (doubly) and remove this object from Data Store
	 */
	private void checkRemoveListNode() {

	    // If the size is not zero, then we
	    // will not be removing any nodes,
	    // so no relinking; just return.
	    if (this.size() != 0) {
		return;
	    }

	    // Otherwise, we need to remove the list
	    // node and relink accordingly.
	    // First, we determine the type
	    // of list node: there are four possibilities:
	    // 1) interior node; connect prev to next
	    // 2) head node. Update child pointer from parent
	    // 3) tail node
	    // 4) only child
	    if (this.next() != null && this.prev() != null) {
		this.prev().setNext(this.next());
		this.next().setPrev(this.prev());
	    } else if (this.next() != null) {
		this.getParent().setChild(this.next(), this.size());
		this.next().setPrev(null);
	    } else if (this.prev() != null) {
		this.prev().setNext(null);
	    } else {
		this.getParent().setChild(null, this.size());
	    }

	    // If we are removing the child of the
	    // TreeNode and the parent has other children,
	    // then we need to give it a substitute
	    if (this.getParent().size() > 0 &&
		    this.getParent().getChild().equals(this)) {
		this.getParent().setChild(this.next(), this.size());
	    }

	    // This is an empty node, so remove it from Data Store.
	    AppContext.getDataManager().removeObject(this);
	}

	/**
	 * Retrieves the parent of the {@code ListNode}
	 * 
	 * @return the parent
	 */
	public TreeNode<E> getParent() {
	    TreeNode<E> parent;
	    if (parentRef == null) {
		return null;
	    }

	    // By definition, there should always be a parent
	    // to a list node. Throw an ObjectNotFoundException
	    // if this call goes bad.
	    parent = parentRef.get();
	    return parent;
	}

	/**
	 * Prepends the supplied object to the list. This may implicitly cause
	 * splitting.
	 * 
	 * @param e
	 *                the element to add to the collection
	 */
	public void prepend(E e) {
	    insert(0, e);
	}

	/**
	 * Splits a linked list of {@code ListNodes} into two smaller lists.
	 * This is necessary when a linked list exceeds the maximum length,
	 * denoted by the cluster size.
	 */
	private void split() {
	    ArrayList<ManagedReference<ManagedObject>> contents = getSubList()
		    .getElements();
	    ArrayList<ManagedReference<ManagedObject>> spawned = 
		new ArrayList<ManagedReference<ManagedObject>>();

	    // move last half of list into a new child
	    int sublistSize = getSubList().size();
	    int lower = sublistSize / 2;
	    for (int index = lower; index < sublistSize; index++) {
		spawned.add((ManagedReference<ManagedObject>) contents
			.get(index));
	    }

	    // remove the relocated nodes from the current list
	    // and mark that the list has changed
	    contents.removeAll(spawned);
	    dataIntegrityVal++;
	    this.count = contents.size();

	    // Create a new ListNode<E> for the moved contents
	    ListNode<E> spawnedNode = new ListNode<E>(getParent(), this
		    .getSubList().maxChildren, spawned);
	    spawnedNode.setNext(this.next());
	    spawnedNode.setPrev(this);
	    this.setNext(spawnedNode);

	    // Walks up the tree to increment the new number of children
	    updateParents(this.getParent(),
		    TreeNode.INCREMENT_CHILDREN_AND_SIZE);
	}

	/**
	 * {@inheritDoc}
	 */
	public SearchResult<E> search(int currentValue, int destIndex) {

	    ListNode<E> n = this;

	    while ((currentValue + n.size()) < destIndex) {
		currentValue += n.size();
		n = n.next();

		// The specified index was too large; hence
		// throw an IndexOutOfBoundsException
		if (n == null) {
		    throw new IndexOutOfBoundsException("The " + "index "
			    + destIndex + " is out of range.");
		}
	    }

	    return new SearchResult<E>(n, destIndex - currentValue - 1);
	}

	/**
	 * Propagates changes in the form of either an increment or decrement
	 * to the parents of the tree. This method calls a recursive method
	 * which traverses up the tree, if necessary.
	 * 
	 * @param parent
	 *                the parent of the current node
	 * @param mode
	 *                the type of update to perform, specified by the
	 *                static final {@code TreeNode} fields.
	 */
	private void updateParents(TreeNode<E> parent, byte mode) {
	    parent.propagateChanges(mode);
	}

    }

    /**
     * This object represents a partition in the list, otherwise denoted as a
     * {@code bucket} (as per {@code bucketSize}). Only one of these
     * {@code SubList} objects lives inside a ListNode<E> object; therefore,
     * there are as many {@code SubList} objects as there are {@code ListNode}s.
     * <p>
     * The separation of the elements from the {@code ListNode}s is to allow
     * iterations and other read operations while modifications to elements
     * occur. This is particularly important for replacements because parent
     * sizes do not need to change since no elements are being added or
     * removed.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static class SubList<E> implements ManagedObject, Serializable {

	private static final long serialVersionUID = 10L;

	/**
	 * A reference to the list of elements
	 */
	private ArrayList<ManagedReference<ManagedObject>> contents;

	/**
	 * The size of the list of elements
	 */
	private int size = 0;

	/**
	 * The maximum number of children which can be contained, previously
	 * addressed as the {@code bucketSize}
	 */
	private int maxChildren = ScalableList.DEFAULT_BUCKET_SIZE;

	/**
	 * Performs a quick check to see if the argument is a legal parameter;
	 * that is, larger than 0.
	 */
	public static boolean isLegal(int maxSize) {
	    return (maxSize > 0);
	}

	/**
	 * Calculates the maximum append size, which is hard-coded as
	 * two-thirds of the maximum children size.
	 * 
	 * @param maxChildren
	 *                the maximum number of children
	 * @return the maximum size for appending purposes
	 */
	public static int calculateMaxAppendSize(int maxChildren) {
	    return maxChildren * 2 / 3;
	}

	/**
	 * Constructor which creates a {@code SubList}.
	 * 
	 * @param maxSize
	 *                the maximum number of elements which can be stored
	 * @param collection
	 *                the elements to add to the empty list
	 */
	public SubList(int maxSize,
		ArrayList<ManagedReference<ManagedObject>> collection) {
	    if (isLegal(maxSize)) {
		maxChildren = maxSize;
	    }

	    contents = new ArrayList<ManagedReference<ManagedObject>>();
	    ManagedReference<ManagedObject> tmp = null;

	    for (int i = 0; i < collection.size(); i++) {
		tmp = collection.get(i);
		contents.add(tmp);
	    }

	    size = contents.size();
	}

	/**
	 * Constructor which creates a {@code SubList}
	 * 
	 * @param maxSize
	 *                the maximum number of elements which can be stored
	 */
	public SubList(int maxSize) {
	    if (isLegal(maxSize)) {
		maxChildren = maxSize;
	    }

	    contents = new ArrayList<ManagedReference<ManagedObject>>();
	    size = contents.size();
	}

	/**
	 * Constructor to create a {@code SubList}
	 * 
	 * @param maxSize
	 *                the maximum number of elements which can be stored
	 * @param e
	 *                an element to add to the empty list, at the first
	 *                index
	 */
	public SubList(int maxSize, E e) {
	    if (isLegal(maxSize)) {
		maxChildren = maxSize;
	    }

	    contents = new ArrayList<ManagedReference<ManagedObject>>();
	    append(e);
	    size = contents.size();
	}

	/**
	 * Constructor to create a {@code SubList}. This constructor assumes
	 * a default children size since one is not provided. To customize
	 * this parameter, refer to one of the other constructors.
	 * 
	 * @param e
	 *                an element to add into the empty list
	 */
	public SubList(E e) {
	    contents = new ArrayList<ManagedReference<ManagedObject>>();
	    append(e);
	    size = contents.size();
	}

	/**
	 * Returns the maximum number of children for this structure.
	 * 
	 * @return the maximum number of children
	 */
	public int getMaxChildren() {
	    return maxChildren;
	}

	/**
	 * Returns the maximum number of children which can be appended. This
	 * value is calculated as two-thirds of the maximum number of
	 * children.
	 * 
	 * @return the maximum number of children which can be appended
	 */
	public int getMaxChildrenAppend() {
	    return calculateMaxAppendSize(maxChildren);
	}

	/**
	 * Returns the size of the collection.
	 * 
	 * @return the size
	 */
	public int size() {
	    return size;
	}

	/**
	 * Returns the elements contained in the {@code SubList} as an
	 * {@code ArrayList}.
	 * 
	 * @return the elements contained in the {@code SubList}
	 */
	public ArrayList<ManagedReference<ManagedObject>> getElements() {
	    return contents;
	}

	/**
	 * Since the list is a collection of ManagedReferences, we are
	 * interested in retrieving the value it points to.
	 * 
	 * @param index
	 *                the index to retrieve
	 * @return the element, if it exists, or null otherwise
	 * @throws IndexOutOfBoundsException
	 *                 if the index is out of bounds (less than 0 or
	 *                 larger than the {@code SubList} size)
	 */
	@SuppressWarnings("unchecked")
	public E get(int index) {
	    E value = null;
	    if (contents != null) {
		value = (E) contents.get(index).get();

		// Check if value is enveloped by an Element
		if (value instanceof Element) {
		    return (E) ((Element<E>) value).getValue();
		}
	    }
	    return value;
	}

	/**
	 * Sets the value at the index provided. The index is not an absolute
	 * index; rather, it is relative to the current list. If the index
	 * does not correspond to a valid index in the underlying list, an
	 * {@code IndexOutOfBoundsException} will be thrown.
	 * 
	 * @param index
	 *                the index to add the element
	 * @param obj
	 *                the element to be added
	 * @return the old element that was replaced
	 * @throws IndexOutOfBoundsException
	 *                 if the index is outside the range of the underlying
	 *                 list
	 */
	@SuppressWarnings("unchecked")
	public E set(int index, Object obj) {
	    assert (obj != null);
	    ManagedReference<ManagedObject> ref = null;
	    ManagedReference<ManagedObject> old = null;
	    Object oldObj = null;

	    // Wrap the element as an Element if is not already
	    // a ManagedObject
	    if (obj instanceof ManagedObject) {
		ref = AppContext.getDataManager().createReference(
			(ManagedObject) obj);
	    } else {
		Element<E> element = new Element<E>((E) obj);
		ref = AppContext.getDataManager().createReference(
			(ManagedObject) element);
	    }

	    // Get the stored element value, depending on
	    // what kind of value it was (ManagedObject or Element)
	    old = contents.set(index, ref);
	    oldObj = old.get();
	    if (oldObj instanceof Element) {
		Element<E> tmp = uncheckedCast(oldObj);
		oldObj = tmp.getValue();
	    }

	    // Delete the old value from data store
	    AppContext.getDataManager().removeObject(
		    (ManagedObject) old.get());
	    return (E) oldObj;
	}

	/**
	 * Appends the supplied argument to the list. It will throw a
	 * {@code NullPointerException} if the supplied object is null.
	 * 
	 * @param e
	 *                the element to add to append
	 * @return whether the operation was successful; true if so, false
	 *         otherwise
	 */
	public boolean append(E e) {
	    assert (e != null);
	    boolean result = false;

	    // If it is not yet a ManagedObject, then
	    // create a new Element to make it a ManagedObject
	    ManagedReference<ManagedObject> ref = createRefForAdd(e);

	    result = contents.add(ref);
	    size = contents.size();

	    return result;
	}

	/**
	 * Returns the index of the element inside the {@code SubList<E>}. If
	 * the element does not exist, then -1 is returned.
	 * 
	 * @param o
	 *                the element whose last index is to be found
	 * @return the index of the element, or -1 if it does not exist
	 */
	public int lastIndexOf(Object o) {
	    assert (o != null);
	    Iterator<ManagedReference<ManagedObject>> iter = contents
		    .iterator();
	    ManagedReference<?> ref = null;
	    Object obj = null;
	    int index = 0;
	    int lastIndex = -1;

	    // Iterate through all contents,
	    // checking for equality
	    while (iter.hasNext()) {
		ref = iter.next();
		obj = ref.get();

		// Retrieve the internal value if
		// it is an Element object
		if (obj instanceof Element) {
		    Element<E> tmp = uncheckedCast(obj);
		    obj = tmp.getValue();
		}
		if (o.equals(obj)) {
		    lastIndex = index;
		}
		index++;
	    }
	    return lastIndex;
	}

	/**
	 * Inserts the element into the list at a specified location. If the
	 * index is not valid, an {@code IndexOutOfBoundsException} is thrown.
	 * 
	 * @param index
	 *                the index to add the new element.
	 * @param e
	 *                the object which is to be inserted at the specified
	 *                {@code index}
	 * @throws IndexOutOfBoundsException
	 *                 if the supplied index is outside of the range of
	 *                 the underlying list
	 */
	public void insert(int index, E e) {
	    assert (e != null);
	    if (index < 0) {
		throw new IndexOutOfBoundsException(
			"Supplied index cannot be less than 0");
	    }
	    ManagedReference<ManagedObject> ref = createRefForAdd(e);

	    contents.add(index, ref);
	    size = contents.size();
	}

	/**
	 * Sets up the ref {@code ManagedReference} so that it contains a
	 * serialized {@code ManagedObject}.
	 * 
	 * @param e
	 *                the element to potentially wrap in an
	 *                {@code Element}
	 * @return a reference to the wrapped element, ready to be stored into
	 *         a {@code SubList}
	 */
	private ManagedReference<ManagedObject> createRefForAdd(E e) {
	    assert (e != null);
	    ManagedReference<ManagedObject> ref = null;

	    // Determine if we need to wrap the parameter or not.
	    if (e instanceof ManagedObject && e instanceof Serializable) {
		ref = AppContext.getDataManager().createReference(
			(ManagedObject) e);
	    } else {
		Element<E> element = new Element<E>(e);
		ref = AppContext.getDataManager().createReference(
			(ManagedObject) element);
	    }
	    return ref;
	}

	/**
	 * Determines the index of the first occurrence of the supplied
	 * argument. If the element does not exist, then -1 is returned.
	 * 
	 * @param o
	 *                the element whose index is to be searched
	 * @return the first index of the supplied element, or -1 if it does
	 *         not exist in the list
	 */
	public int indexOf(Object o) {
	    assert (o != null);
	    Iterator<ManagedReference<ManagedObject>> iter = contents
		    .iterator();
	    ManagedReference<ManagedObject> ref = null;
	    Object obj = null;
	    int index = 0;

	    // Iterate through all the list
	    // contents until we find a match
	    while (iter.hasNext()) {
		ref = iter.next();
		obj = ref.get();

		// Check if we need to extract the
		// internal value contained in an Element object
		if (obj instanceof Element) {
		    Element<E> tmp = uncheckedCast(obj);
		    obj = tmp.getValue();
		}
		if (o.equals(obj)) {
		    return index;
		}
		index++;
	    }
	    return -1;
	}

	/**
	 * Removes the element at the supplied index. This method throws an
	 * {@code IndexOutOfBoundsException} if the index does not exist in
	 * the underlying list.
	 * 
	 * @param index
	 *                the index to remove
	 * @return the object removed from the index
	 * @throws IndexOutOfBoundsException
	 *                 if the index is outside of the range of the
	 *                 underlying list
	 */
	@SuppressWarnings("unchecked")
	public E remove(int index) {
	    if (index > contents.size() - 1) {
		throw new IndexOutOfBoundsException("The index, " + index
			+ ", is out of bounds");
	    }
	    E value = null;
	    ManagedReference<?> removed = contents.remove(index);

	    // Determine how to extract the element, based on whether it
	    // is an instance of Element or not
	    if (removed != null && removed.get() instanceof Element) {
		value = ((Element<E>) removed.get()).getValue();
		AppContext.getDataManager().removeObject(
			removed.getForUpdate());
	    } else {
		value = (E) removed.get();
	    }
	    this.size = contents.size();
	    return value;
	}

	/**
	 * Removes the supplied object from the underlying list, if it exists.
	 * 
	 * @param obj
	 *                the element to remove from the list
	 * @return whether the operation was successful; true if so, false
	 *         otherwise
	 */
	public boolean remove(Object obj) {
	    Iterator<ManagedReference<ManagedObject>> iter = contents
		    .iterator();
	    ManagedReference<ManagedObject> current = null;
	    Object object = null;
	    boolean success = false;

	    // go through all the elements in this collection (a sublist)
	    while (iter.hasNext()) {
		current = iter.next();
		object = current.get();

		// if it is an Element, then extract its value
		if (object instanceof Element) {
		    Element<E> tmp = uncheckedCast(object);
		    object = tmp.getValue();
		}

		if (obj.equals(object)) {
		    // remove the object in the Element wrapper. If
		    // it was a ManagedObject and not an Element,
		    // then we just remove the reference to it.
		    if (obj instanceof Element) {
			AppContext.getDataManager().removeObject(object);
		    }
		    success = contents.remove(current);
		    break;
		}
	    }
	    this.size = contents.size();
	    return success;
	}

    }

    /**
     * An interface which unifies the concept of a {@code ListNode} and
     * {@code TreeNode} as elements within a {@code ScalableList}. This
     * interface avoids cast warnings when the identity of a given element is
     * not immediately known.
     * 
     * @param <E>
     *                the type of element stored in the {@code ScalableList}
     */
    static interface Node<E> {

	/**
	 * Retrieves the node's parent.
	 * 
	 * @return the parent, or null if none exists.
	 */
	TreeNode<E> getParent();

	/**
	 * Sets the {@code Node}'s parent to the supplied argument.
	 */
	void setParent(TreeNode<E> t);

	/**
	 * The size of the node; that is, the sum of the sizes of its
	 * immediate children.
	 * 
	 * @return the size of this node.
	 */
	int size();

	/**
	 * Traverses the tree (recursively) in search of the ListNode<E>
	 * which contains the index provided. If no ListNode<E> can be found,
	 * then null is returned.
	 * 
	 * @param currentValue
	 *                the current index value at the beginning of this
	 *                current search
	 * @param destIndex
	 *                the absolute index of the desired element
	 * @return the {@code ListNode} containing the absolute
	 *         {@code destIndex}
	 */
	SearchResult<E> search(int currentValue, int destIndex);

	/**
	 * Returns the next {@code Node} in sequence, or null if none exists.
	 * 
	 * @return the next node
	 */
	Node<E> next();

	/**
	 * Sets the next element to be the supplied argument. The argument
	 * should be the same type as the variable.
	 * 
	 * @param next
	 *                the next {@code Node}
	 */
	void setNext(Node<E> next);

	/**
	 * Returns the previous {@code Node} in sequence, or null if none
	 * exists.
	 * 
	 * @return the previous node
	 */
	Node<E> prev();

	/**
	 * Sets the previous element to be the supplied argument. The argument
	 * should be the same type as the variable.
	 * 
	 * @param prev
	 *                the previous {@code Node}
	 */
	void setPrev(Node<E> prev);
    }

    /**
     * This class is responsible for returning the results of a search,
     * including the target {@code ListNode} and the offset. Since this object
     * is not Serializable, its intention is to be a temporary object whose
     * scope is simply within the tree search process. Therefore, it is
     * unnecessary to include mutator methods to adjust the entries; only
     * accessor methods are needed to retreive the stored values.
     */
    private static class SearchResult<E> {

	private static final long serialVersionUID = 11L;

	/**
	 * The target node where the element lives
	 */
	private ListNode<E> node;

	/**
	 * The offset of the element within the {@code ListNode}'s
	 * {@code SubList}
	 */
	private int offset;

	/**
	 * Constructor which creates a new SearchResult based on the target
	 * node and the calculated offset. The offset is not an absolute
	 * index, but rather the index of the element with respect to the
	 * {@code SubList} of the {@code ListNode}.
	 * 
	 * @param node
	 *                the target node
	 * @param offset
	 *                the index of the element in this {@code ListNode}'s
	 *                {@code SubList}.
	 */
	public SearchResult(ListNode<E> node, int offset) {
	    this.node = node;
	    this.offset = offset;
	}

	/**
	 * Retrieves the target {@code ListNode}.
	 * 
	 * @return the {@code ListNode} of interest
	 */
	public ListNode<E> getListNode() {
	    return node;
	}

	/**
	 * Retrieves the offset of the element within the {@code ListNode}'s
	 * {@code SubList}.
	 * 
	 * @return the offset
	 */
	public int getOffset() {
	    return offset;
	}
    }
}
