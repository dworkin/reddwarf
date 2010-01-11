/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.app.util;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
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
 * stored is converted into a {@code ManagedObject} in the form of an
 * {@code Element} wrapper if it is not one already, and a
 * {@code ManagedReference} is created to reference it. The wrapper is
 * detected during an element removal: if the element is an instance of the
 * wrapper {@code Element} class, then it is removed from the data manager.
 * Conversely, if the element had no wrapper (but by definition is still a
 * {@code ManagedObject}), then it remains within the data manager until the
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
 * iteration, suggesting that the target element may not be the same one as
 * when the call was initially made. However, if a modification in the form of
 * an addition or removal happens on the same {@code ListNode}, then the
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
 * Therefore, it is highly recommended (and considered good practice) that
 * potentially long iterations be broken up into smaller tasks. This strategy
 * improves the concurrency of the {@code ScalableList} as it reduces the
 * locked elements owned by any one task.
 * <p>
 * 
 * @param <E> the type of the elements stored in the {@code ScalableList}
 */
public class ScalableList<E> extends AbstractList<E> implements Serializable,
	ManagedObjectRemoval {

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

    /*
     * IMPLEMENTATION
     */

    /**
     * Constructor which creates a {@code ScalableList} object with default
     * values for the {@code bucketSize} and {@code branchingFactor}.
     */
    public ScalableList() {
	TreeNode<E> t = new TreeNode<E>(this, null, false);
	headRef =
		AppContext.getDataManager().createReference(
			new DummyConnector<E>(t.getChild()));
	tailRef =
		AppContext.getDataManager().createReference(
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
     * @param branchingFactor the number of children each node should have. A
     * {@code branchingFactor} of 2 means that the tree structure is binary.
     * @param bucketSize the size of each partitioned list. This value must be
     * a positive integer (larger than 0).
     * @throws IllegalArgumentException when the arguments are too small
     */
    public ScalableList(int branchingFactor, int bucketSize) {
	isLegal(branchingFactor, bucketSize);
	this.bucketSize = bucketSize;
	this.branchingFactor = branchingFactor;
	TreeNode<E> t = new TreeNode<E>(this, null, false);
	headRef =
		AppContext.getDataManager().createReference(
			new DummyConnector<E>(t.getChild()));
	tailRef =
		AppContext.getDataManager().createReference(
			new DummyConnector<E>(t.getChild()));
	setRoot(t);
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
     * @param branchingFactor the number of children each node should have. A
     * {@code branchingFactor} of 2 means that the tree structure is binary.
     * @param bucketSize the size of each partitioned list. This value must be
     * a positive integer (larger than 0).
     * @param collection a collection of objects to initially populate the
     * {@code ScalableList}
     * @throws IllegalArgumentException if the collection is invalid (contains
     * {@code null} elements), or if the {@code branchingFactor} or
     * {@code bucketSize} are not within their respective ranges
     */
    public ScalableList(int branchingFactor, int bucketSize,
	    Collection<E> collection) {

	this(branchingFactor, bucketSize);
	this.addAll(collection);
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
     * Returns the value stored within the {@code ManagedReference},
     * depending on whether it is an {@code Element} or {@code ManagedObject}
     * 
     * @param <E> the type of element
     * @param ref the {@code ManagedReference} storing the value
     * @param delete {@code true} if the {@code Element} object should be
     * deleted, and {@code false} otherwise
     * @return the value stored in the reference
     */
    @SuppressWarnings("unchecked")
    static <E> E getValueFromReference(ManagedReference<ManagedObject> ref,
	    boolean delete) {
	E obj = (E) ref.get();

	// In case we wrapped the item with an
	// Element object, fetch the value. In here,
	// check if the Element is to be deleted
	if (obj instanceof Element) {
	    Element<E> tmp = uncheckedCast(obj);
	    if (delete) {
		AppContext.getDataManager().removeObject(obj);
	    }
	    return tmp.getValue();
	}
	return obj;
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
     * @param branchingFactor the branching factor
     * @param bucketSize the maximum number of elements in a {@code ListNode}
     * @throws IllegalArgumentException if one of the values is invalid
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
     * Appends the specified element to the end of this list. This
     * implementation accepts all elements except for {@code null}.
     * 
     * @param e element to add
     * @return this method will always return {@code true}
     */
    public boolean add(E e) {
	if (e == null) {
	    throw new NullPointerException("Element cannot be null");
	}

	// add it at the end and propagate change to parents
	getTail().append(e);

	// update the tail in case it has changed.
	ListNode<E> next = getTail().next();
	if (next != null) {
	    setTail(next);
	}

	return true;
    }

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any
     * subsequent elements to the right (adds one to their indices).
     * 
     * @param index the index
     * @param e the element to add
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public void add(int index, E e) {
	isNotNegative(index);

	// Check for the different boundary cases
	if (e == null) {
	    throw new NullPointerException("Element cannot be null");

	} else if (getHead().equals(getTail()) && getHead().size() == 0) {
	    getTail().append(e);
	    return;

	} else if (getHead() == null) {
	    throw new IndexOutOfBoundsException("Cannot add to index " +
		    index + " on an empty list");
	}

	// otherwise, add it to the specified index.
	// This requires a search of the list nodes.
	SearchResult<E> sr;
	if (index != 0) {
	    sr = getNode(--index);
	    sr.node.insert(sr.offset + 1, e);
	} else {
	    getHead().insert(0, e);
	}

	// update the tail in case it has changed.
	ListNode<E> next = getTail().next();
	if (next != null) {
	    setTail(next);
	}
    }

    /**
     * Removes all of the elements referred to in the list, but retains a
     * basic structure that consists of a {@code SubList}, {@code ListNode},
     * and a {@code TreeNode}, along with {@code DummyConnector}s
     * representing the head and tail of the list. The call to
     * {@code removingObject()} is asynchronous, meaning the objects are
     * deleted from the data manager in a scheduled task.
     */
    public void clear() {

	// If the head is null, then the ScalableList is
	// not initialized; therefore, no work to do.
	if (getHead() == null) {
	    return;
	}

	// Otherwise, remove the entire object..
	removingObject();

	// ..and then supply a new empty structure
	TreeNode<E> t = new TreeNode<E>(this, null, false);
	headRef =
		AppContext.getDataManager().createReference(
			new DummyConnector<E>(t.getChild()));
	tailRef =
		AppContext.getDataManager().createReference(
			new DummyConnector<E>(t.getChild()));
	setRoot(t);
    }

    /**
     * Returns {@code true} if this list contains the specified element. More
     * formally, returns {@code true} if and only if this list contains at
     * least one element e such that
     * <p>
     * {@code (o==null ? e==null : o.equals(e))}.
     * 
     * @param o the object which may be in the list
     * @return whether the object is contained in the list; {@code true} if
     * so, {@code false} otherwise
     */
    public boolean contains(Object o) {
	if (o == null) {
	    return false;
	}
	return (indexOf(o) != -1);
    }

    /**
     * Returns the index in this list of the first occurrence of the specified
     * element, or -1 if this list does not contain this element. More
     * formally, returns the lowest index i such that
     * <p>
     * {@code (o==null ? get(i)==null : o.equals(get(i)))},
     * <p>
     * or -1 if there is no such index.
     * 
     * @param o the element to search for
     * @return the index in this list of the first occurrence of the specified
     * element, or -1 if this list does not contain this element.
     */
    public int indexOf(Object o) {
	int listIndex = 0;
	ScalableListNodeIterator<E> iter =
		new ScalableListNodeIterator<E>(getHead());

	while (iter.hasNext()) {
	    ListNode<E> n = (ListNode<E>) iter.next();
	    int index = n.getSubList().indexOf(o);

	    if (index != -1) {
		return listIndex + index;
	    }
	    listIndex += n.size();
	}
	return -1;
    }

    /**
     * Returns the index in this list of the last occurrence of the specified
     * element, or -1 if this list does not contain this element. More
     * formally, returns the highest index i such that (o==null ? get(i)==null :
     * o.equals(get(i))), or -1 if there is no such index.
     * 
     * @param obj element to search for
     * @return the index in this list of the last occurrence of the specified
     * element, or -1 if this list does not contain this element.
     */
    public int lastIndexOf(Object obj) {
	int listIndex = 0;
	int absIndex = -1;
	ScalableListNodeIterator<E> iter =
		new ScalableListNodeIterator<E>(getHead());

	// For every ListNode encountered, check for an
	// instance of the supplied object
	while (iter.hasNext()) {
	    ListNode<E> n = iter.next();
	    int index = n.getSubList().lastIndexOf(obj);

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
     * Determines if the provided index is positive (not negative). If the
     * index is negative, then an {@code IndexOutOfBoundsException} is thrown.
     * 
     * @param index the index to check for validity
     * @throws IndexOutOfBoundsException if the index is negative
     */
    private void isNotNegative(int index) {
	if (index < 0) {
	    throw new IndexOutOfBoundsException("Index " + index +
		    " cannot be negative");
	}
    }

    /**
     * Removes the element at the specified position in this list. Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the list.
     * 
     * @param index the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public E remove(int index) {
	isNotNegative(index);
	SearchResult<E> sr = getNode(index);
	ListNode<E> n = sr.node;

	return n.remove(this, sr.offset);
    }

    /**
     * This operation preserves the order of the elements in the list and
     * keeps multiple copies of the same elements if they exist.
     * 
     * @param c the collection of elements to keep
     * @return {@code true} if this list changed as a result of the call
     */
    public boolean retainAll(Collection<?> c) {
	Iterator<E> iter = new ScalableIterator<E>(this);
	ArrayList<E> newList = new ArrayList<E>();
	boolean changed = false;

	// For each element in the overall list,
	// if it is contained in the supplied
	// collection, then we add it to a
	// new list instance. Once finished, we
	// will add everything from this into
	// an emptied list (this).
	while (iter.hasNext()) {
	    E e = iter.next();

	    // Add the node to the new collection
	    if (c.contains(e)) {
		newList.add(e);
	    } else {
		changed = true;
	    }
	}

	// Clear the current list and add all
	// elements from the temporary collection.
	clear();
	addAll(newList);
	return changed;
    }

    /**
     * Retrieves the head {@code ListNode}.
     * 
     * @return the head {@code ListNode} if it exists
     * @throws ObjectNotFoundException if no reference exists
     */
    ListNode<E> getHead() {
	return headRef.get().getRefAsListNode();
    }

    /**
     * Retrieves the tail {@code ListNode}.
     * 
     * @return the tail {@code ListNode} if it exists
     * @throws ObjectNotFoundException if there is no reference
     */
    ListNode<E> getTail() {
	return tailRef.get().getRefAsListNode();
    }

    /**
     * Sets the tail of the {@code ListNode} linked-list
     * 
     * @param newTail the tail
     */
    void setTail(ListNode<E> newTail) {
	tailRef.getForUpdate().setRef(newTail);
    }

    /**
     * Sets the head of the {@code ListNode} linked-list
     * 
     * @param newHead the head
     */
    void setHead(ListNode<E> newHead) {
	headRef.getForUpdate().setRef(newHead);
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     * 
     * @param index the index to set
     * @param obj the object to replace the existing value
     * @return the old value which was replaced
     */
    @SuppressWarnings("unchecked")
    public E set(int index, Object obj) {
	if (obj == null) {
	    throw new NullPointerException(
		    "Value for set operation cannot be null");
	}
	SearchResult<E> sr = getNode(index);
	return sr.node.set(sr.offset, obj);
    }

    /**
     * Returns the element at the specified position in this list.
     * 
     * @param index the index to retrieve
     * @return the element at the supplied index
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    public E get(int index) {

	// Iterate through using the count. Use a get()
	// iterator because we are not modifying anything; hence, false.
	SearchResult<E> sr = getNode(index);
	SubList<E> sublist = sr.node.getSubList();
	if (sublist == null) {
	    return null;
	}
	return sublist.get(sr.offset);
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     * 
     * @return an {@code Iterator} over the elements in the list
     */
    public Iterator<E> iterator() {
	return new ScalableIterator<E>(this);
    }

    /**
     * Returns a {@code ListIterator} over the elements in this list in proper
     * sequence.
     * 
     * @return a {@code ListIterator} over the elements in the list
     */
    public ListIterator<E> listIterator() {
	return new ScalableListIterator<E>(this);
    }

    /**
     * Returns a {@code ListIterator} over the elements in this list, starting
     * from the element at the given index.
     * 
     * @return a {@code ListIterator} over the elements in the list
     * @param index the index
     * @throws IndexOutOfBoundsException if the index is out of bounds:
     * {@code index < 0 || index > size()}
     */
    public ListIterator<E> listIterator(int index) {
	// Special case: if the index provided equals the size,
	// then we must manually supply the starting node because
	// a search would throw an IndexOutOfBoundsException
	if (index == this.size()) {
	    ListNode<E> tail = getTail();
	    return new ScalableListIterator<E>(this, tail, tail.size());
	}
	return new ScalableListIterator<E>(this, getNode(index));
    }

    /**
     * Removes the first occurrence in this list of the specified element. If
     * this list does not contain the element, it is unchanged. More formally,
     * removes the element with the lowest index i such that
     * <p>
     * {@code (o==null ? get(i)==null : o.equals(get(i)))} (if such an element
     * exists).
     * 
     * @param obj element to be removed from the list, if present
     * @return the element previously at the specified position
     */
    public boolean remove(Object obj) {
	ScalableListNodeIterator<E> iter =
		new ScalableListNodeIterator<E>(getHead());
	boolean removed = false;

	// Find and remove the object in the ListNode<E> that contains it
	while (iter.hasNext()) {
	    ListNode<E> n = iter.next();
	    removed = n.remove(this, obj);
	    if (removed) {
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
    TreeNode<E> getRoot() {
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
     * @param newRoot the {@code TreeNode} which is to be the new root
     */
    void setRoot(TreeNode<E> newRoot) {
	AppContext.getDataManager().markForUpdate(this);
	root = createReferenceIfNecessary(newRoot);
    }

    /**
     * Returns {@code true} if this list contains no elements.
     * 
     * @return {@code true} if the list is empty, and {@code false} otherwise
     */
    public boolean isEmpty() {
	return (getHead().size() == 0);
    }

    /**
     * Performs the search for the desired {@code ListNode}. This method will
     * locate the {@code ListNode} using a recursive search process and return
     * a {@code SearchResult} which contains both the {@code ListNode} and the
     * numeric offset corresponding to the provided index.
     * 
     * @param index the absolute index in the entire list to search for the
     * entry
     * @return the {@code SearchResult} which contains the element at the
     * specified {@code index}
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    private SearchResult<E> getNode(int index) {
	if (index < 0) {
	    throw new IndexOutOfBoundsException("Index cannot be negative");
	}
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
	    size += current.size();
	    current = current.next();
	}
	return size;
    }

    /**
     * {@inheritDoc}
     */
    public void removingObject() {

	AsynchronousClearTask<E> clearTask =
		new AsynchronousClearTask<E>(this);

	// Schedule asynchronous task here
	// which will delete the list
	AppContext.getTaskManager().scheduleTask(clearTask);

	// Remove the dummy connectors
	DataManager dm = AppContext.getDataManager();
	dm.removeObject(headRef.get());
	dm.removeObject(tailRef.get());
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
     * @param <E> the type of element stored in the {@code ScalableList}
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
	 * @param reference the element which this object is to point to
	 */
	DummyConnector(Node<E> reference) {
	    if (reference != null) {
		ref = AppContext.getDataManager().createReference(reference);
	    }
	}

	/**
	 * Sets the reference for this object.
	 * 
	 * @param newRef the intended new reference
	 */
	void setRef(Node<E> newRef) {
	    AppContext.getDataManager().markForUpdate(this);
	    ref = createReferenceIfNecessary(newRef);
	}

	/**
	 * Retrieves the reference as a {@code ListNode}.
	 * 
	 * @return the reference as a {@code ListNode}, or null if it does
	 * not exist
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
     * @param <E> the type of element stored in the {@code ScalableList}
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
	private final ManagedReference<ScalableList<E>> owner;

	/**
	 * The maximum number of children this node can contain
	 */
	private final int branchingFactor;

	/**
	 * The maximum number of elements which the underlying
	 * {@code ListNode} can store
	 */
	private final int bucketSize;

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
	 * @param list a reference to the owning {@code ScalableList}
	 * @param parent the intended parent
	 * @param child the child to be added underneath the new
	 * {@code TreeNode}
	 * @param numberChildren the total number of children that will exist
	 * once the {@code TreeNode} is instantiated. This is based on the
	 * nature of the linked list of {@code ListNodes} attached to the
	 * {@code child}.
	 * @param size the total number of elements that will exist under the
	 * {@code TreeNode}, based on the elements that already exist among
	 * the {@code child} and its siblings.
	 */
	private TreeNode(ScalableList<E> list, TreeNode<E> parent,
		Node<E> child, int numberChildren, int size) {
	    this(list);

	    // Set up links
	    assert (child != null);
	    childRef = AppContext.getDataManager().createReference(child);

	    // this might be the root so parent could be null
	    parentRef = createReferenceIfNecessary(parent);

	    childrenCount = numberChildren;
	    this.size = size;
	}

	/**
	 * Constructor which is called by public constructors.
	 * 
	 * @param list the {@code ScalableList} which is the owner of this
	 * structure
	 */
	private TreeNode(ScalableList<E> list) {
	    owner = AppContext.getDataManager().createReference(list);
	    this.branchingFactor = list.getBranchingFactor();
	    nextRef = null;
	    this.bucketSize = list.getBucketSize();
	}

	/**
	 * Create a new TreeNode on account of a new leaf ({@code ListNode})
	 * being created.
	 * 
	 * @param list the {@code ScalableList} which is the owner of this
	 * structure
	 * @param parent the intended parent
	 * @param e an element to add into the empty {@code ListNode}
	 */
	TreeNode(ScalableList<E> list, TreeNode<E> parent, E e) {
	    this(list);

	    assert (e != null);

	    ListNode<E> n = new ListNode<E>(this, bucketSize, e);
	    size = n.size();
	    childrenCount = 1;
	    DataManager dm = AppContext.getDataManager();
	    childRef = dm.createReference((Node<E>) n);
	    parentRef = createReferenceIfNecessary(parent);
	}

	/**
	 * Constructor which creates a {@code TreeNode} while specifying
	 * parameters for the node characteristics
	 * 
	 * @param list the {@code ScalableList} owner of this structure
	 * @param parent the intended parent {@code ListNode}
	 * @param isSplit {@code true} if the {@code TreeNode} is to be
	 * created due to a {@code split} operation, and {@code false}
	 * otherwise.
	 */
	TreeNode(ScalableList<E> list, TreeNode<E> parent, boolean isSplit) {
	    this(list);

	    if (!isSplit) {
		ListNode<E> n = new ListNode<E>(this, bucketSize);
		DataManager dm = AppContext.getDataManager();
		size = n.size();
		childRef = dm.createReference((Node<E>) n);
	    } else {
		size = 0;
		childRef = null;
	    }
	    parentRef = createReferenceIfNecessary(parent);
	}

	/**
	 * Returns a {@code String} representation of this object.
	 * 
	 * @return a {@code String} representation of this object.
	 */
	public String toString() {
	    StringBuilder s = new StringBuilder();
	    int size = 0;
	    int children = getChildCount();
	    Node<E> node = getChild();

	    // Add information for each child
	    for (int i = 0; i < children; i++) {
		if (i > 0) {
		    s.append(";");
		}
		s.append(node.toString());
		node = node.next();
		size += node.size();
	    }

	    // Add list metrics
	    s.append("size: ");
	    s.append(size);
	    s.append(", children: ");
	    s.append(children);
	    return s.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	public TreeNode<E> prev() {
	    if (prevRef == null) {
		return null;
	    }
	    return prevRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setPrev(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    TreeNode<E> t = uncheckedCast(ref);
	    prevRef = createReferenceIfNecessary(t);
	}

	/**
	 * {@inheritDoc}
	 */
	public TreeNode<E> next() {
	    if (nextRef == null) {
		return null;
	    }
	    return nextRef.get();
	}

	/**
	 * Obtains the child of the current node. The child represents the
	 * head of the list of children. The {@code TreeNode} does not have
	 * references to all the children, so it uses knowledge of the head to
	 * iterate through them.
	 * 
	 * @return the child, which can be either a {@code TreeNode} or
	 * {@code ListNode}, depending on the position of the current node in
	 * the tree.
	 */
	Node<E> getChild() {
	    if (childRef == null) {
		return null;
	    }
	    return childRef.get();
	}

	/**
	 * Sets the child to be the supplied parameter as long as it is not
	 * null. This method also updates the size of the parent because
	 * changing the child suggests that a new size exists.
	 * 
	 * @param child the new child
	 */
	void setChild(Node<E> child, int size, int numberOfChildren) {
	    AppContext.getDataManager().markForUpdate(this);
	    this.size = size;
	    this.childrenCount = numberOfChildren;
	    childRef = createReferenceIfNecessary(child);
	}

	/**
	 * Sets the child to null for the relinking process.
	 */
	void setChildToNull() {
	    AppContext.getDataManager().markForUpdate(this);
	    childRef = null;
	}

	/**
	 * {@inheritDoc}
	 */
	public int size() {
	    return size;
	}

	/**
	 * Recursively increments the node's size until reaching the root. The
	 * root is not updated to enable some degree of concurrency.
	 */
	void increment() {
	    if (getParent() == null) {
		return;
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    size++;
	    getParent().increment();
	}

	/**
	 * Recursively decrements the node's size until reaching the root. The
	 * root is not updated to enable some degree of concurrency.
	 */
	void decrement() {
	    if (getParent() == null) {
		return;
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    if (--size == 0) {
		getParent().decrementChildrenAndSize();
	    } else {
		getParent().decrement();
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void clear() {
	    TreeNode<E> parent = getParent();
	    AppContext.getDataManager().removeObject(this);
	    if (parent != null) {
		parent.clear();
	    }
	}

	/**
	 * Retrieves the number of immediate children beneath this node.
	 * 
	 * @return the number of immediate children
	 */
	int getChildCount() {
	    // If this is the root, then we have to iterate and
	    // obtain the actual value.
	    if (getParent() == null) {
		return getNumberOfChildrenUsingIteration();
	    }
	    return childrenCount;
	}

	/**
	 * {@inheritDoc}
	 */
	public TreeNode<E> getParent() {
	    if (parentRef == null) {
		return null;
	    }
	    return parentRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setNext(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    TreeNode<E> t = uncheckedCast(ref);
	    nextRef = createReferenceIfNecessary(t);
	}

	/**
	 * Unlinks itself from the tree without performing a recursive
	 * deletion. This method is guaranteed to delete itself. This method
	 * re-links references that are dangling as a result of this node's
	 * removal. There are four conditions:
	 * <p>
	 * The first condition is if the {@code TreeNode} is an intermediate
	 * node. If so, it is necessary to link the left and right siblings
	 * together before the node is pruned.
	 * <p>
	 * The second condition is if the {@code TreeNode} is the tail node.
	 * If so, then the previous element's next reference is set to null
	 * before the node is pruned.
	 * <p>
	 * The third condition is if the {@code TreeNode} is the head node. If
	 * so, then the next element's previous reference is set to null
	 * before the node is pruned.
	 * <p>
	 * The last condition is if the {@code TreeNode} is an only- child. If
	 * so, then the parent's child reference is set to null before the
	 * node is pruned.
	 */
	void prune() {

	    // Decide how to perform re-linking of the nodes,
	    if (prevRef != null) {
		// interior TreeNode
		if (nextRef != null) {
		    prevRef.getForUpdate().setNext(this.next());
		    nextRef.getForUpdate().setPrev(this.prev());
		} else {
		    // relink tail
		    prevRef.getForUpdate().setNext(null);
		}
	    } else if (nextRef != null) {
		// relink head and temporarily point to the next sibling.
		// If the next sibling didn't belong to the parent, then
		// the parent will be removed anyway since its child
		// count will have reached 0.
		nextRef.getForUpdate().setPrev(null);
		parentRef.getForUpdate().setChild(nextRef.get(),
			parentRef.get().size(),
			parentRef.get().getChildCount());

	    } else {
		TreeNode<E> parent = parentRef.getForUpdate();
		parent.setChildToNull();
	    }
	    AppContext.getDataManager().removeObject(this);
	}

	/**
	 * Determines where to split a list of children based on the list
	 * size. By default, this is set to {@code numberOfChildren}/2.
	 * 
	 * @param numberOfChildren the number of children to split
	 * @return the index corresponding to the location where to split the
	 * linked list
	 */
	private static int calculateSplitSize(int numberOfChildren) {
	    return numberOfChildren / 2;
	}

	/**
	 * This method walks up the tree and performs any {@code TreeNode<E>}
	 * splits if the children sizes have been exceeded.
	 */
	private boolean performSplitIfNecessary() {
	    // Check if we need to split
	    if (childrenCount > branchingFactor) {
		split();
		return true;
	    }
	    return false;
	}

	/**
	 * Splits the children into two roughly equal groups, and generates a
	 * sibling to parent one of the groups.
	 */
	private void split() {
	    TreeNode<E> newNode = null;
	    Node<E> tmp = (Node<E>) this.getChild();
	    AppContext.getDataManager().markForUpdate(this);

	    // Updates the reference of the new root, if created
	    generateParentIfNecessary();

	    // Perform split by creating and linking new sibling
	    newNode = createAndLinkSibling(tmp, childrenCount);

	    // Link the new sibling to the tree
	    newNode.setPrev(this);
	    newNode.setNext(this.next());
	    this.setNext(newNode);
	    if (newNode.next() != null) {
		newNode.next().setPrev(newNode);
	    }
	}

	/**
	 * Determines the nature of the sibling to be created. The Node
	 * created will always be a TreeNode<E>, but the type of child
	 * depends on whether it parents ListNodes, or other TreeNodes.
	 * 
	 * @param child the {@code ManagedObject} which needs to be split and
	 * placed under a new parent
	 * @param numberOfChildren the number of children which the new
	 * sibling will parent
	 * @return the {@TreeNode<E>} that not represents a sibling which is
	 * connected to the tree
	 */
	private TreeNode<E> createAndLinkSibling(Node<E> child,
		int numberOfChildren) {
	    int halfway = calculateSplitSize(numberOfChildren);
	    int size = 0;

	    // Create the new sibling
	    TreeNode<E> newNode =
		    new TreeNode<E>(owner.get(), getParent(), true);

	    // Iterate through the children and update references
	    Node<E> newChild = child;
	    int i = 0;
	    int children = 0;
	    while (child != null && i < numberOfChildren) {

		// When we reach halfway, save the child as it
		// will be the child of the new TreeNode. When
		// we pass halfway, update the parents and
		// decrease the size.
		if (i == halfway) {
		    newChild = child;
		    if (child instanceof TreeNode) {
			child.prev().setNext(null);
			child.setPrev(null);
		    }
		}
		if (i++ >= halfway) {
		    size += child.size();
		    children++;
		    child.setParent(newNode);
		}
		child = child.next();
	    }

	    // Update the new parent's child, its size,
	    // and our own size
	    newNode.setChild(newChild, size, children);
	    AppContext.getDataManager().markForUpdate(this);
	    this.childrenCount -= children;
	    this.size -= size;

	    return newNode;
	}

	/**
	 * This method creates a parent above the provided node so that any
	 * new siblings resulting from a split can be joined to the new
	 * parent. The decision to create a new parent depends on whether the
	 * supplied node is the root; only these nodes are orphaned while
	 * siblings have parents assigned to them.
	 */
	private void generateParentIfNecessary() {
	    // If this is the currently existing root, make a new one
	    // and set the this node as the child
	    if (getParent() == null) {
		TreeNode<E> grandparent =
			new TreeNode<E>(owner.get(), null, this, 1, size());

		// Link the node to its new parent
		setParent(grandparent);
		owner.getForUpdate().setRoot(grandparent);
	    }
	}

	/**
	 * Sets the parent for the node.
	 * 
	 * @param parent the intended parent
	 */
	public void setParent(TreeNode<E> parent) {
	    AppContext.getDataManager().markForUpdate(this);
	    parentRef = createReferenceIfNecessary(parent);
	}

	/**
	 * Increments the number of children and size, and determines whether
	 * the parent should do both, or just perform an increment of the
	 * size.
	 */
	void incrementChildrenAndSize() {
	    AppContext.getDataManager().markForUpdate(this);
	    TreeNode<E> parent = getParent();

	    // If we are at the root, then check if we need to perform a
	    // split. This will generate a new root.
	    if (parent == null) {
		int numberOfRootChildren =
			getNumberOfChildrenUsingIteration();

		// Split if too many root children. Here we get
		// the real size since we don't update the root's
		// size to allow for concurrency. Since it will
		// no longer be the root, we need to find and set
		// its value.
		if (numberOfRootChildren > branchingFactor) {
		    size = owner.get().size();
		    childrenCount = numberOfRootChildren;
		    split();
		}
		return;
	    }

	    size++;
	    childrenCount++;

	    // If it is not the root node, then
	    // see if we need to split it normally.
	    if (!performSplitIfNecessary()) {
		parent.increment();
		return;
	    }
	    parent.incrementChildrenAndSize();
	}

	/**
	 * Decrements the number of children and size, and determines whether
	 * the parent should do both again, or just decrement the size.
	 */
	void decrementChildrenAndSize() {
	    TreeNode<E> parent = getParent();

	    // We are done when we reach the root
	    if (parent == null) {
		return;
	    }

	    AppContext.getDataManager().markForUpdate(this);
	    size--;
	    childrenCount--;

	    /*
	     * If there are no children, then prune this node accordingly. If
	     * the parent has only one child (this), then link the parent to
	     * the children and prune this node. For both these cases, we
	     * propagate the decrement of size and children to the parent.
	     * Otherwise, we only propagate the decrement the size.
	     */
	    if (childrenCount == 0 && size() == 0) {
		prune();
		parent.decrementChildrenAndSize();
		return;
	    } else if (parent.getChildCount() == 1) {
		pruneIntermediate();
	    }
	    parent.decrement();
	}

	/**
	 * Removes a {@code TreeNode} that is the only child of a parent. This
	 * requires that all children are updated of its new parent.
	 */
	private void pruneIntermediate() {
	    TreeNode<E> parent = getParent();
	    Node<E> child = childRef.getForUpdate();
	    parent.setChild(child, parent.size(), childrenCount);

	    for (int i = 0; i < childrenCount; i++) {
		child.setParent(parent);
		child = child.next();
	    }
	    AppContext.getDataManager().removeObject(this);
	}

	/**
	 * This method retrieves the number of children by iterating through
	 * them one by one. This method is used when the current node is the
	 * root, since the field {@code childrenCount} does not get updated.
	 * 
	 * @return the number of children
	 */
	private int getNumberOfChildrenUsingIteration() {
	    int total = 0;
	    Node<E> child = getChild();
	    while (child != null) {
		total++;
		child = child.next();
	    }
	    return total;
	}

	/**
	 * {@inheritDoc}
	 */
	public SearchResult<E> search(int currentValue, int destIndex) {
	    TreeNode<E> t = this;

	    // Iterate through siblings
	    while ((currentValue + t.size) < destIndex) {
		currentValue += t.size;
		t = t.next();

		// Check if the specified index was too large;
		if (t == null) {
		    throw new IndexOutOfBoundsException("The index " +
			    destIndex + " is out of range.");
		}
	    }
	    return t.getChild().search(currentValue, destIndex);
	}
    }

    /**
     * An asynchronous task which iterates through the tree and removes all
     * children. This task is instantiated when a {@code clear()} command is
     * issued from the {@code ScalableList}. The success of this operation is
     * dependent on implemented {@code clear()} methods on each of the
     * underlying components.
     * 
     * @param <E> the type of element stored in the {@code ScalableList}
     */
    private static class AsynchronousClearTask<E> implements Serializable,
	    Task, ManagedObject {

	private static final long serialVersionUID = 4L;

	/**
	 * A reference to the current {@code Node} whose children are being
	 * deleted
	 */
	private ManagedReference<ListNode<E>> current;

	/**
	 * Constructor for the asynchronous task
	 * 
	 * @param root the root node of the entire tree structure
	 */
	AsynchronousClearTask(ScalableList<E> list) {
	    assert (list != null);
	    DataManager dm = AppContext.getDataManager();
	    current = dm.createReference(list.getHead());
	}

	/**
	 * The entry point of the task to perform the clear.
	 */
	public void run() {
	    // Perform some work and check if we need to reschedule
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);

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
	 * Removes some number of elements from the ScalableList and
	 * returns {@code true} if there is more work to be done. If there are
	 * no more elements to remove, then it will return {@code false},
	 * signifying to the {@code AsynchronousClearTask} to start taking
	 * values from the queue
	 * 
	 * @return {@code true} if more work needs to be done, and
	 * {@code false} if there are no more elements to remove.
	 */
	private boolean doWork() {
	    ListNode<E> currentListNode = current.get();
	    AppContext.getDataManager().markForUpdate(currentListNode);
	    ListNode<E> next;
	    int size = currentListNode.size();
	    E entry = null;

	    // Perform some removals
	    while (size > 0 && currentListNode != null &&
                   AppContext.getTaskManager().shouldContinue()) {

		// Repeatedly remove the head element in the
		// ListNode as long as one exists.
		next = currentListNode.next();

		entry = currentListNode.remove(null, 0);
		if (--size == 0 && next != null) {
		    currentListNode = next;
		    size = currentListNode.size();
		}

		if (entry instanceof Element) {
		    AppContext.getDataManager().removeObject(entry);
		}
	    }

	    // If we exit and the size is reported to be 0, then we
	    // call the clear() method to delete the empty tree.
	    // Otherwise, we will set up to re-execute the task
	    if (size == 0) {
		currentListNode.clear();
		return false;

	    } else {
		current =
			AppContext.getDataManager().createReference(
				currentListNode);
	    }
	    return true;
	}

    }

    /**
     * This class represents a stored entity of the list. It is a wrapper for
     * any object that is stored in the list so that the list can refer to it
     * by using a ManagedReference, rather than the actual object itself. This
     * makes managing sublists less intensive as each reference to an
     * underlying entity is a fixed size.
     * 
     * @param <E> the type of element stored in the {@code ScalableList}
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
	 * @param e the element to store within the {@code Element}
	 * {@code ManagedObject}
	 */
	Element(E e) {
	    value = e;
	}

	/**
	 * Retrieves the element.
	 * 
	 * @return the element stored by this object
	 */
	E getValue() {
	    return value;
	}

	/**
	 * Sets the value of the element
	 * 
	 * @param e the new value to set
	 */
	void setValue(E e) {
	    assert (e != null);
	    AppContext.getDataManager().markForUpdate(this);
	    value = e;
	}

	/**
	 * Returns a {@code String} representation
	 * 
	 * @return the value in {@code String} form
	 */
	public String toString() {
	    return value.toString();
	}
    }

    /**
     * This iterator walks through the {@code ListNode<E>}s which parent the
     * {@code SubList}s. An iterator for this type of element is necessary to
     * support the other element iterator as well as {@code indexOf()}
     * operations.
     * 
     * @param <E> the type of element stored in the {@code ScalableList}
     */
    static class ScalableListNodeIterator<E> implements Serializable,
	    Iterator<ListNode<E>> {

	/**
	 * The current position of the iterator. This is initialized as
	 * {@code null} and is set to null when there are no more elements.
	 */
	private ListNode<E> current;

	/**
	 * The next position of the iterator. This is initialized as the head
	 * element provided in the constructor
	 */
	private ListNode<E> next;

	/**
	 * The previous position of the iterator. This is initialized as the
	 * head element provided in the constructor
	 */
	private ListNode<E> prev;

	private static final long serialVersionUID = 7L;

	/**
	 * Constructor to create a new iterator.
	 * 
	 * @param head the head {@code ListNode} from which to begin iterating
	 */
	ScalableListNodeIterator(ListNode<E> head) {
	    prev = null;
	    current = null;
	    next = head;
	}

	/**
	 * Determines if there is a next element to iterate over; that is, if
	 * the next {@code ListNode} is not null.
	 * 
	 * @return {@code true} if the next element is not null, and
	 * {@code false} otherwise
	 */
	public boolean hasNext() {
	    return (next != null);
	}

	/**
	 * Returns the next {@code ListNode<E>} in order from the list
	 * 
	 * @return the next {@code ListNode}
	 * @throws NoSuchElementException if there exists no next sibling
	 */
	public ListNode<E> next() {
	    if (current == null && next == null) {
		throw new NoSuchElementException("There is no next element");
	    }

	    prev = current;
	    current = next;
	    if (next != null) {
		next = next.next();
	    }
	    return current;
	}

	/**
	 * Determines if there is a previous element to iterate over; that is,
	 * if the previous {@code ListNode} is not null.
	 * 
	 * @return {@code true} if the previous element is not null, and
	 * {@code false} otherwise
	 */
	public boolean hasPrev() {
	    return (prev != null);
	}

	/**
	 * Returns the previous {@code ListNode<E>} in order from the list
	 * 
	 * @return the previous {@code ListNode}
	 * @throws NoSuchElementException if there exists no previous sibling
	 */
	public ListNode<E> prev() {
	    if (current == null && prev == null) {
		throw new NoSuchElementException("There is no prev element");
	    }

	    next = current;
	    current = prev;
	    if (prev != null) {
		prev = prev.prev();
	    }
	    return current;
	}

	/**
	 * @deprecated this operation is not supported because the
	 * {@code ScalableList} only removes elements explicitly, not
	 * {@code ListNodes}.
	 * @throws UnsupportedOperationException the operation is not
	 * supported
	 */
	public void remove() {
	    throw new UnsupportedOperationException(
		    "This method is not supported");
	}
    }

    /**
     * This class represents an iterator of the elements of the
     * {@code ScalableList}.
     * 
     * @param <E> the type of element stored in the {@code ScalableList}
     */
    static class ScalableIterator<E> implements Serializable, Iterator<E> {

	/**
	 * A reference to the {@code ScalableList} which this iterator is
	 * referring to
	 */
	final ManagedReference<ScalableList<E>> owner;

	/**
	 * The current {@code ListNode} of the iterative process
	 */
	protected ManagedReference<ListNode<E>> currentNode;

	/**
	 * The iteration location of the list
	 */
	protected int cursor;

	/**
	 * Flag which only lets one removal happen per call to {@code next()}
	 */
	protected boolean wasNextCalled;

	/**
	 * The value for the current {@code ListNode} to determine if any
	 * changes have taken place since the last time it was accessed
	 */
	protected long listNodeReferenceValue = -1;

	private static final long serialVersionUID = 8L;

	/**
	 * Performs a check to see that the index for {@code next()} is still
	 * within range of the sub list.
	 * 
	 * @param <E> the type of element stored
	 * @param offset the offset
	 * @param currentListNode the current {@code ListNode} being examined
	 * @return {@code true} if the offset exists in the sub list and
	 * {@code false} otherwise
	 */
	static <E> boolean isNextWithinRange(int offset,
		ListNode<E> currentListNode) {
	    return (offset < currentListNode.size());
	}

	/**
	 * Constructor used to create a {@code ScalableListIterator} for the
	 * underlying elements in the {@code ScalableList}.
	 * 
	 * @param list the {@code ScalableList} over which to iterate
	 */
	ScalableIterator(ScalableList<E> list) {
	    this(list, list.getHead());
	}

	ScalableIterator(ScalableList<E> list, ListNode<E> startingNode) {
	    owner = AppContext.getDataManager().createReference(list);
	    currentNode =
		    AppContext.getDataManager().createReference(startingNode);

	    cursor = 0;
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();
	    wasNextCalled = false;
	}

	/**
	 * Retrieves the index of the current element by walking up the tree
	 * to the root and aggregating the counts of each {@code ListNode} and
	 * {@code TreeNode}. This operation is slightly expensive because of
	 * the required percolation up the tree.
	 * 
	 * @return the absolute index of the current element being examined
	 */
	int getCurrentIndex() {
	    return getAbsoluteIndex(currentNode.get(), 1) + cursor;
	}

	/**
	 * Walks up the tree and collects the sizes to produce an absolute
	 * index.
	 * 
	 * @return the absolute index of the given node
	 */
	int getAbsoluteIndex(Node<E> node, int size) {
	    TreeNode<E> parent = node.getParent();

	    // We reached the root; return the size we have.
	    if (parent == null) {
		return size - 1;
	    }
	    Node<E> firstChild = parent.getChild();

	    // Iterate through siblings. We don't
	    // aggregate the first node's size because
	    // we already found the offset from the
	    // caller.
	    while (!node.equals(firstChild)) {
		node = node.prev();
		size += node.size();
	    }
	    return getAbsoluteIndex(parent, size);
	}

	/**
	 * Retrieves the next element.
	 * 
	 * @throws ConcurrentModificationException if the {@code ListNode}
	 * that the iterator is pointing to has been modified to (addition or
	 * removal) by someone else
	 * @throws NoSuchElementException if there is no next element
	 * @return the next element
	 */
	@SuppressWarnings("unchecked")
	public E next() {
	    // Check the integrity of the ListNode to see if
	    // any changes were made since we last operated.
	    // Throw a ConcurrentModificationException if so.
	    checkDataIntegrity();

	    List<ManagedReference<ManagedObject>> elements =
		    currentNode.get().getSubList().getElements();

	    // Retrieve the next value from the list; we may have to load
	    // up a new ListNode
	    int index = getCursorBasedOnPreviousAction(true);
	    if (!isNextWithinRange(index, currentNode.get())) {
		if (loadNextListNode()) {
		    elements = currentNode.get().getSubList().getElements();
		    index = 0;
		} else {
		    throw new NoSuchElementException(
			    "There is no next element");
		}
	    }
	    cursor = index;
	    wasNextCalled = true;

	    // Once we have located the next node,
	    // update the reference values
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();

	    // In case we wrapped the item with an
	    // Element object, fetch the value.
	    return (E) getValueFromReference(elements.get(cursor), false);
	}

	/**
	 * Retrieve the index of interest, based on our previous direction and
	 * intended direction
	 * 
	 * @param next whether we intend on travelling in the {@code next}
	 * direction; {@code true} if so, and {@code false} otherwise
	 * @return the index corresponding to the cursor
	 */
	protected int getCursorBasedOnPreviousAction(boolean next) {
	    if (next) {
		return (wasNextCalled ? cursor + 1 : cursor);
	    } else {
		return (wasNextCalled ? cursor : cursor - 1);
	    }
	}

	/**
	 * Get the next {@code ListNode} for the
	 * {@code ScalableListNodeIterator}.
	 * 
	 * @return {@code true} if there was a next {@code ListNode}, and
	 * {@code false} otherwise
	 */
	private boolean loadNextListNode() {
	    ListNode<E> next = currentNode.get().next();
	    if (next != null) {
		currentNode =
			AppContext.getDataManager().createReference(next);
		cursor = 0;
		return true;
	    }
	    return false;
	}

	/**
	 * Checks whether the data integrity value has changed, and throws a
	 * {@code ConcurrentModificationException} if so.
	 * 
	 * @throws ConcurrentModificationException if the data integrity value
	 * has changed or if it cannot be verified
	 */
	void checkDataIntegrity() {
	    try {
		if (currentNode.get().getDataIntegrityValue() == 
		    	listNodeReferenceValue) {
		    return;
		}
	    } catch (ObjectNotFoundException onfe) {
	    }
	    throw new ConcurrentModificationException(
		    "The ListNode has been modified or removed");

	}

	/**
	 * Returns whether there is a next element to iterate over.
	 * 
	 * @return {@code true} if there is a next element, or {@code false}
	 * otherwise
	 * @throws ConcurrentModificationException if the {@code ListNode} has
	 * been modified or removed
	 */
	public boolean hasNext() {

	    checkDataIntegrity();

	    // If there is an element in the iterator still,
	    // then simply return true since it will be the
	    // next element to be returned.
	    if (isNextWithinRange(getCursorBasedOnPreviousAction(true),
		    currentNode.get())) {
		return true;
	    }

	    // Try seeing if there is a next ListNode
	    return (currentNode.get().next() != null);
	}

	/**
	 * Removes from the underlying collection the last element returned by
	 * the iterator. This can only be called once per call to {@code next}.
	 */
	public void remove() {
	    if (!wasNextCalled) {
		throw new IllegalStateException(
			"remove() must follow next() or previous()");
	    }
	    doRemove();
	    wasNextCalled = false;
	}

	/**
	 * Performs the remove and updates the references if necessary
	 */
	void doRemove() {
	    ListNode<E> prev = currentNode.get().prev();
	    ListNode<E> next = currentNode.get().next();
	    int size = currentNode.get().size();
	    currentNode.get().remove(owner.get(), cursor);

	    // if the removal caused a ListNode to be removed,
	    // then we need to replace the currentNode with a
	    // valid one.
	    if (size == 1 || cursor == 0) {
		if (prev != null) {
		    currentNode =
			    AppContext.getDataManager().createReference(prev);
		    cursor = currentNode.get().size();
		} else if (next != null) {
		    currentNode =
			    AppContext.getDataManager().createReference(next);
		    cursor = 0;
		} else {
		    cursor = 0;
		}
	    } else {
		cursor--;
	    }

	    // update the data integrity value
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();
	}
    }

    /**
     * A class which implements a {@code ListIterator} for the
     * {@code ScalableList} data structure. This iterator allows
     * bi-directional traversal and other operations native to the
     * {@code ListIterator} interface.
     * 
     * @param <E> the type of element
     */
    static class ScalableListIterator<E> extends ScalableIterator<E>
	    implements ListIterator<E> {

	private static final long serialVersionUID = 9L;

	/**
	 * A flag which records whether a removal or set is an invalid
	 * operation. {@code True} if invalid, and {@code false} otherwise.
	 */
	private boolean cannotRemoveOrSet = true;

	/**
	 * Constructor which starts the iterations at the specified
	 * {@code ListNode}.
	 * 
	 * @param list the {@code ScalableList} over which to iterate
	 */
	ScalableListIterator(ScalableList<E> list) {
	    super(list);
	}

	/**
	 * Constructor which creates a {@code ScalableListIterator} given the
	 * list, a {@code startingNode} and a {@code startingIndex} denoting
	 * the starting point. This constructor is used primarily for when the
	 * user specifies an index that is one larger than the highest index
	 * value.
	 * 
	 * @param list a reference to the {@code ScalableList}
	 * @param startingIndex the starting index (relative)
	 * @param startingNode the starting node
	 */
	ScalableListIterator(ScalableList<E> list, ListNode<E> startingNode,
		int startingIndex) {
	    super(list, startingNode);
	    wasNextCalled = false;
	    cursor = startingIndex;
	}

	/**
	 * Constructor which creates a {@code ScalableListIterator} given the
	 * list and a {@code searchResult} denoting the starting point.
	 * 
	 * @param list
	 * @param searchResult
	 */
	ScalableListIterator(ScalableList<E> list,
		SearchResult<E> searchResult) {
	    super(list, searchResult.node);
	    cursor = searchResult.offset;
	}

	/**
	 * Performs a check to see that the index for {@code prev()} is still
	 * within range of the sub list.
	 * 
	 * @param <E> the type of element stored
	 * @param offset the offset
	 * @param currentListNode the current {@code ListNode} being examined
	 * @return {@code true} if the offset exists in the sub list and
	 * {@code false} otherwise
	 */
	static <E> boolean isPrevWithinRange(int offset,
		ListNode<E> currentListNode) {
	    return (offset >= 0);
	}

	/**
	 * Get the previous {@code ListNode} for the
	 * {@code ScalableListNodeIterator}.
	 * 
	 * @return {@code true} if there was a previous {@code ListNode}, and
	 * {@code false} otherwise
	 */
	private boolean loadPrevListNode() {
	    ListNode<E> prev = currentNode.get().prev();
	    if (prev != null) {
		currentNode =
			AppContext.getDataManager().createReference(prev);
		return true;
	    }
	    return false;
	}

	/**
	 * Returns the index of the element that would be returned by a
	 * subsequent call to <tt>next</tt>. (Returns list size if the list
	 * iterator is at the end of the list.)
	 * 
	 * @return the index of the element that would be returned by a
	 * subsequent call to <tt>next</tt>, or list size if list iterator
	 * is at end of list.
	 */
	public int nextIndex() {
	    int nextIndex = getCurrentIndex();
	    if (wasNextCalled) {
		nextIndex++;
	    }
	    return nextIndex;
	}

	/**
	 * Returns the index of the element that would be returned by a
	 * subsequent call to <tt>previous</tt>. (Returns -1 if the list
	 * iterator is at the beginning of the list.)
	 * 
	 * @return the index of the element that would be returned by a
	 * subsequent call to <tt>previous</tt>, or -1 if list iterator is
	 * at beginning of list.
	 */
	public int previousIndex() {
	    int previousIndex = getCurrentIndex();
	    if (!wasNextCalled) {
		previousIndex--;
	    }
	    return previousIndex;
	}

	/**
	 * Returns <tt>true</tt> if this list iterator has more elements
	 * when traversing the list in the reverse direction. (In other words,
	 * returns <tt>true</tt> if <tt>previous</tt> would return an
	 * element rather than throwing an exception.)
	 * 
	 * @return <tt>true</tt> if the list iterator has more elements when
	 * traversing the list in the reverse direction.
	 * @throws ConcurrentModificationException if the {@code ListNode} has
	 * been modified or removed
	 */
	public boolean hasPrevious() {
	    checkDataIntegrity();

	    // If the future index will be equal to or larger than 0,
	    // then another element exists
	    if (isPrevWithinRange(getCursorBasedOnPreviousAction(false),
		    currentNode.get())) {
		return true;
	    }

	    // Otherwise, try loading the next ListNode
	    return (currentNode.get().prev() != null);
	}

	/**
	 * Returns the previous element in the list. This method may be called
	 * repeatedly to iterate through the list backwards, or intermixed
	 * with calls to <tt>next</tt> to go back and forth. (Note that
	 * alternating calls to <tt>next</tt> and <tt>previous</tt> will
	 * return the same element repeatedly.)
	 * 
	 * @return the previous element in the list
	 * @throws NoSuchElementException if the iteration has no previous
	 * element
	 * @throws ConcurrentModificationException if the {@code ListNode} has
	 * been modified or removed
	 */
	@SuppressWarnings("unchecked")
	public E previous() {

	    // Check the integrity of the ListNode to see if
	    // any changes were made since we last operated.
	    // Throw a ConcurrentModificationException if so.
	    checkDataIntegrity();

	    List<ManagedReference<ManagedObject>> elements =
		    currentNode.get().getSubList().getElements();

	    // Retrieve the value from the list; we may have to load
	    // up a new ListNode
	    int index = getCursorBasedOnPreviousAction(false);
	    if (!isPrevWithinRange(index, currentNode.get())) {
		if (loadPrevListNode()) {
		    elements = currentNode.get().getSubList().getElements();
		    index = currentNode.get().size() - 1;
		} else {
		    throw new NoSuchElementException(
			    "The previous element does not exist");
		}
	    }
            cannotRemoveOrSet = false;
	    cursor = index;
	    wasNextCalled = false;

	    // Once we have found the previous node,
	    // update the reference values
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();

	    return (E) getValueFromReference(elements.get(cursor), false);

	}

	/**
	 * Replaces the last element returned by <tt>next</tt> or
	 * <tt>previous</tt> with the specified element (optional
	 * operation). This process will automatically remove the old element
	 * from the data manager if it was not a {@code ManagedObject}.
	 * 
	 * @param o the element with which to replace the last element
	 * returned by <tt>next</tt> or <tt>previous</tt>.
	 * @throws IllegalStateException if the operation is called without
	 * {@code next} or {@code previous} being called
	 */
	public void set(E o) {
	    if (cannotRemoveOrSet) {
		throw new IllegalStateException(
			"set() must follow next() or previous()");
	    }
	    currentNode.get().set(cursor, o);
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();
	}

	/**
	 * Removes from the underlying collection the last element returned by
	 * the iterator. This can only be called once per call to {@code next}.
	 * 
	 * @throws IllegalStateException if the method is not preceded by
	 * {@code next()} or {@code prev()}
	 */
	public void remove() {
	    if (cannotRemoveOrSet) {
		throw new IllegalStateException(
			"remove() must follow next() or previous()");
	    }

	    if (!wasNextCalled) {
		this.doRemove();
	    } else {
		super.remove();
	    }
	    cannotRemoveOrSet = true;
	}

	/**
	 * {@inheritDoc}
	 */
	void doRemove() {
	    ListNode<E> prev = currentNode.get().prev();
	    ListNode<E> next = currentNode.get().next();
	    int size = currentNode.get().size();
	    currentNode.get().remove(owner.get(), cursor);

	    // if the removal caused a ListNode to be removed,
	    // then we need to replace the currentNode with a
	    // valid one.
	    if (size == 1 || cursor == 0) {
		if (next != null) {
		    currentNode =
			    AppContext.getDataManager().createReference(next);
		    cursor = 0;
		} else if (prev != null) {
		    currentNode =
			    AppContext.getDataManager().createReference(prev);
		    cursor = currentNode.get().size();
		} else {
		    cursor = 0;
		}
	    } else {
		cursor++;
	    }

	    // update the data integrity value
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();
	}

	/**
	 * {@inheritDoc}
	 */
	public E next() {
            E result = super.next();
            cannotRemoveOrSet = false;
            return result;
	}

	/**
	 * Inserts the specified element into the list. The element is
	 * inserted immediately before the next element that would be returned
	 * by <tt>next</tt>, if any, and after the next element that would
	 * be returned by <tt>previous</tt>, if any. (If the list contains
	 * no elements, the new element becomes the sole element on the list.)
	 * The new element is inserted before the implicit cursor: a
	 * subsequent call to <tt>next</tt> would be unaffected, and a
	 * subsequent call to <tt>previous</tt> would return the new
	 * element. (This call increases by one the value that would be
	 * returned by a call to <tt>nextIndex</tt> or
	 * <tt>previousIndex</tt>.)
	 * 
	 * @param o the element to insert.
	 * @throws IndexOutOfBoundsException if the index which the element is
	 * to be added is out of bounds
	 */
	public void add(E o) {
	    // Add element to what will be the next index
	    if (wasNextCalled) {
		currentNode.get().insert(++cursor, o);
	    } else {
		currentNode.get().insert(cursor++, o);
	    }

	    // If the addition added a new list node,
	    // then we need to point to it
	    if (cursor > currentNode.get().size() - 1) {
		currentNode =
			AppContext.getDataManager().createReference(
				currentNode.get().next());
		cursor = 0;
	    }

	    cannotRemoveOrSet = true;
	    listNodeReferenceValue =
		    currentNode.get().getDataIntegrityValue();
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
     * @param <E> the type of element stored in the {@code ScalableList}
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
	 * Private constructor which the public constructors will call.
	 * 
	 * @param parent the intended parent
	 */
	private ListNode(TreeNode<E> parent) {
	    nextRef = null;
	    prevRef = null;
	    dataIntegrityVal = DATA_INTEGRITY_STARTING_VALUE;
	    parentRef = AppContext.getDataManager().createReference(parent);
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent the intended parent
	 * @param maxSize the maximum number of elements that can be stored
	 */
	ListNode(TreeNode<E> parent, int maxSize) {
	    this(parent);

	    SubList<E> sublist = new SubList<E>(maxSize);
	    count = sublist.size();
	    subListRef = AppContext.getDataManager().createReference(sublist);
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent the intended parent
	 * @param maxSize the maximum number of elements that can be stored
	 * @param e an element which is to be stored as the first item in the
	 * list
	 */
	ListNode(TreeNode<E> parent, int maxSize, E e) {
	    this(parent);

	    SubList<E> sublist = new SubList<E>(maxSize, e);
	    count = sublist.size();
	    subListRef = AppContext.getDataManager().createReference(sublist);
	}

	/**
	 * Constructor which uses knowledge of a parent and maximum list size.
	 * A {@code ListNode} that exceeds {@code maxSize} will be subject to
	 * splitting.
	 * 
	 * @param parent the intended parent
	 * @param maxSize the maximum number of elements that can be stored
	 * @param list a list of items which are to be added into the empty
	 * list
	 */
	ListNode(TreeNode<E> parent, int maxSize,
		List<ManagedReference<ManagedObject>> list) {
	    this(parent);

	    SubList<E> sublist = new SubList<E>(maxSize, list);
	    count = sublist.size();
	    subListRef = AppContext.getDataManager().createReference(sublist);
	}

	/**
	 * Returns the data integrity value of the {@code ListNode}
	 * 
	 * @return the current data integrity value
	 */
	int getDataIntegrityValue() {
	    return dataIntegrityVal;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setNext(Node<E> ref) {
	    AppContext.getDataManager().markForUpdate(this);
	    ListNode<E> node = uncheckedCast(ref);
	    nextRef = createReferenceIfNecessary(node);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setParent(TreeNode<E> parent) {
	    AppContext.getDataManager().markForUpdate(this);
	    parentRef = createReferenceIfNecessary(parent);
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
	    prevRef = createReferenceIfNecessary(n);
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
	 * one has not yet been instantiated
	 */
	SubList<E> getSubList() {
	    if (subListRef == null) {
		return null;
	    }
	    return subListRef.get();
	}

	/**
	 * Appends the supplied object to the list and performs a split if
	 * necessary.
	 * 
	 * @param e the element to append
	 */
	void append(E e) {
	    getSubList().append(e);
	    AppContext.getDataManager().markForUpdate(this);
	    count++;
	    dataIntegrityVal++;

	    // check if we need to split; i.e. if we have exceeded
	    // the specified list size.
	    if (count > getSubList().maxChildren) {
		split();
	    } else {
		this.getParent().increment();
	    }
	}

	/**
	 * Inserts the supplied value at the given index. The index is
	 * relative to the current list and not the global collection.
	 * 
	 * @param index the index to insert the value, relative to the current
	 * {@code SubList}
	 * @param e the value to insert
	 */
	void insert(int index, E e) {
	    getSubList().insert(index, e);
	    AppContext.getDataManager().markForUpdate(this);
	    count++;
	    dataIntegrityVal++;

	    // check if we need to split; i.e. if we have exceeded
	    // the specified list size.
	    if (count > getSubList().maxChildren) {
		split();
	    } else {
		this.getParent().increment();
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
	 * @param list a reference to the {@code ScalableList}; this argument
	 * should only ever be null during the {@code AsynchronousClearTask}
	 * operation
	 * @param index the index corresponding to an element in the list (not
	 * an absolute index with respect to the {@code ScalableList} object
	 * @return the element that was removed
	 */
	E remove(ScalableList<E> list, int index) {
	    E old = getSubList().remove(index);
	    if (old != null) {
		doRemoveWork(list);
	    }
	    return old;
	}

	/**
	 * {@inheritDoc}
	 */
	public void clear() {
	    TreeNode<E> parent = getParent();
	    DataManager dm = AppContext.getDataManager();
	    dm.removeObject(getSubList());
	    dm.removeObject(this);
	    parent.clear();
	}

	/**
	 * A {@code String} representation of the {@code ListNode}.
	 * 
	 * @return a {@code String} representing the contents of the
	 * {@code ListNode}
	 */
	public String toString() {
	    return subListRef.get().toString();
	}

	/**
	 * Performs the work of calling the recursive update methods
	 * 
	 * @param list a reference to the {@code ScalableList}
	 */
	private void doRemoveWork(ScalableList<E> list) {
	    AppContext.getDataManager().markForUpdate(this);
	    count--;
	    dataIntegrityVal++;

	    TreeNode<E> parent = getParent();
	    if (count == 0) {
		checkRemoveListNode(list);
		parent.decrementChildrenAndSize();
	    } else {
		parent.decrement();
	    }
	}

	/**
	 * Removes the {@code Object} from the {@code SubList<E>}, if it
	 * exists.
	 * 
	 * @param list a reference to the {@code ScalableList}; since this
	 * method is not called by the {@code AsynchronousClearTask}, this
	 * parameter must not be null.
	 * @param obj the {@code Object} to remove
	 * @return whether the object was removed or not; {@code true} if so,
	 * {@code false} otherwise
	 */
	boolean remove(ScalableList<E> list, Object obj) {
	    assert (list != null);
	    boolean result = getSubList().remove(obj);

	    // If a removal took place, then update
	    // count information accordingly
	    if (result) {
		doRemoveWork(list);
	    }
	    return result;
	}

	/**
	 * Sets the element at the supplied index with the provided value.
	 * 
	 * @param index the index to set the value
	 * @param obj the value to replace the existing one
	 * @return the old value
	 */
	E set(int index, Object obj) {
	    return subListRef.getForUpdate().set(index, obj);
	}

	/**
	 * A method that determines how to remove an empty {@code ListNode<E>}
	 * from a list of other {@code ListNode<E>}s. Update previous to
	 * point to next (doubly) and remove this object from Data Store
	 */
	private void checkRemoveListNode(ScalableList<E> list) {

	    // If the size is not zero, then we
	    // will not be removing any nodes,
	    // so no relinking; just return.
	    if (size() != 0) {
		return;
	    }

	    // If this is an only child and its size is 0,
	    // we will keep it so that we can make future
	    // appends rather than creating a new ListNode.
	    if (next() == null && prev() == null) {
		return;
	    }

	    // Link the parent to the next child before
	    // we attempt to remove the current node
	    linkParentToNextIfNecessary();

	    // Now we need to remove the list
	    // node and relink accordingly.
	    // First, we determine the type
	    // of list node: there are three possibilities:
	    // 1) interior node; connect prev to next
	    // 2) head node. Update child pointer from parent
	    // 3) tail node
	    if (next() != null && prev() != null) {
		prev().setNext(next());
		next().setPrev(prev());
	    } else if (next() != null) {
		next().setPrev(null);
		// The clear task will cause the list parameter
		// to be null because we don't need to be
		// updating the head; if we did, then we would
		// asynchronously be updating the empty list's
		// head.
		if (list != null) {
		    list.setHead(next());
		}
	    } else {
		prev().setNext(null);
		// The clear task will cause the list parameter
		// to be null because we don't need to be
		// updating the head; if we did, then we would
		// asynchronously be updating the empty list's
		// head.
		if (list != null) {
		    list.setTail(prev());
		}
	    }

	    // This is an empty node, so remove it from Data Store.
	    AppContext.getDataManager().removeObject(getSubList());
	    AppContext.getDataManager().removeObject(this);
	}

	/**
	 * Determines if the parent should be connected to the next sibling
	 * after a removal has taken place. This will occur as long as there
	 * is a next sibling, the next sibling has the same parent as
	 * {@code this}, the parent has sufficient children, and if the
	 * parent was not yet removed. Otherwise, if the parent has no
	 * children, then there is no child to update the child reference to,
	 * so we set it to null and prune the node during the propagation.
	 */
	private void linkParentToNextIfNecessary() {
	    TreeNode<E> parent = getParent();

	    // We decrement the child count because this method is
	    // called after a removal has taken place
	    int children = parent.getChildCount() - 1;

	    if (next() != null && children > 0 &&
		    parent.getChild().equals(this) &&
		    parent.equals(next().getParent())) {
		parent
			.setChild(next(), parent.size(), parent
				.getChildCount());
	    } else if (children == 0) {
		parent.setChildToNull();
	    }
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
	 * Splits the children into two roughly equal groups, and generates a
	 * sibling to parent one of the groups.
	 */
	private void split() {
	    List<ManagedReference<ManagedObject>> contents =
		    getSubList().getElements();
	    List<ManagedReference<ManagedObject>> spawned =
		    new ArrayList<ManagedReference<ManagedObject>>();

	    // move last half of list into a new child
	    int sublistSize = getSubList().size();
	    int lower = sublistSize / 2;
	    for (int index = lower; index < sublistSize; index++) {
		ManagedReference<ManagedObject> temp = contents.get(index);
		spawned.add(temp);
	    }

	    // remove the relocated nodes from the current list
	    // and mark that the list has changed
	    contents.removeAll(spawned);
	    this.count = contents.size();

	    // Create a new ListNode<E> for the moved contents
	    ListNode<E> spawnedNode =
		    new ListNode<E>(getParent(),
			    this.getSubList().maxChildren, spawned);

	    // Perform necessarily linking
	    spawnedNode.setNext(this.next());
	    spawnedNode.setPrev(this);
	    if (next() != null) {
		this.next().setPrev(spawnedNode);
	    }
	    this.setNext(spawnedNode);

	    // Walks up the tree to increment the new number of children
	    this.getParent().incrementChildrenAndSize();
	}

	/**
	 * {@inheritDoc}
	 */
	public SearchResult<E> search(int currentValue, int destIndex) {
	    ListNode<E> n = this;

	    while ((currentValue + n.size()) < destIndex) {
		currentValue += n.size();
		n = n.next();

		if (n == null) {
		    throw new IndexOutOfBoundsException(
			    "The index is out of range.");
		}
	    }

	    return new SearchResult<E>(n, destIndex - currentValue - 1);
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
     * @param <E> the type of element stored in the {@code ScalableList}
     */
    static class SubList<E> implements ManagedObject, Serializable {

	private static final long serialVersionUID = 10L;

	/**
	 * A reference to the list of elements
	 */
	private ArrayList<ManagedReference<ManagedObject>> contents;

	/**
	 * The maximum number of children which can be contained, previously
	 * addressed as the {@code bucketSize}
	 */
	private final int maxChildren;

	/**
	 * Performs a quick check to see if the argument is a legal parameter;
	 * that is, larger than 0.
	 */
	public static boolean isLegal(int maxSize) {
	    return (maxSize > 0);
	}

	/**
	 * Constructor which creates a {@code SubList}.
	 * 
	 * @param maxSize the maximum number of elements which can be stored
	 * @param collection the elements to add to the empty list
	 */
	SubList(int maxSize, List<ManagedReference<ManagedObject>> collection) {
	    this(maxSize);
	    ManagedReference<ManagedObject> tmp = null;

	    for (int i = 0; i < collection.size(); i++) {
		tmp = collection.get(i);
		contents.add(tmp);
	    }
	}

	/**
	 * Constructor which creates a {@code SubList}
	 * 
	 * @param maxSize the maximum number of elements which can be stored
	 */
	SubList(int maxSize) {
	    assert (isLegal(maxSize));
	    maxChildren = maxSize;
	    contents = new ArrayList<ManagedReference<ManagedObject>>();
	}

	/**
	 * Constructor to create a {@code SubList}
	 * 
	 * @param maxSize the maximum number of elements which can be stored
	 * @param e an element to add to the empty list, at the first index
	 */
	SubList(int maxSize, E e) {
	    this(maxSize);
	    append(e);
	}

	/**
	 * Returns a {@code String} representation of this object.
	 * 
	 * @return a {@code String} representation of this object
	 */
	public String toString() {
	    StringBuilder s = new StringBuilder("{");
	    for (int i = 0; i < contents.size(); i++) {
		if (i > 0) {
		    s.append(",");
		}
		s.append(contents.get(i).toString());
	    }
	    s.append("}");
	    return s.toString();
	}

	/**
	 * Returns the maximum number of children for this structure.
	 * 
	 * @return the maximum number of children
	 */
	int getMaxChildren() {
	    return maxChildren;
	}

	/**
	 * Returns the size of the collection.
	 * 
	 * @return the size
	 */
	int size() {
	    return contents.size();
	}

	/**
	 * Returns the elements contained in the {@code SubList} as an
	 * {@code ArrayList}.
	 * 
	 * @return the elements contained in the {@code SubList}
	 */
	List<ManagedReference<ManagedObject>> getElements() {
	    return contents;
	}

	/**
	 * Since the list is a collection of ManagedReferences, we are
	 * interested in retrieving the value it points to.
	 * 
	 * @param index the index to retrieve
	 * @return the element, if it exists, or null otherwise
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 * (less than 0 or larger than the {@code SubList} size)
	 */
	@SuppressWarnings("unchecked")
	E get(int index) {
	    return (E) getValueFromReference(contents.get(index), false);
	}

	/**
	 * Sets the value at the index provided. The index is not an absolute
	 * index; rather, it is relative to the current list. If the index
	 * does not correspond to a valid index in the underlying list, an
	 * {@code IndexOutOfBoundsException} will be thrown.
	 * 
	 * @param index the index to add the element
	 * @param obj the element to be added
	 * @return the old element that was replaced
	 * @throws IndexOutOfBoundsException if the index is outside the range
	 * of the underlying list
	 */
	@SuppressWarnings("unchecked")
	E set(int index, Object obj) {
	    assert (obj != null);

	    AppContext.getDataManager().markForUpdate(this);

	    // Wrap the element as an Element if is not already
	    // a ManagedObject
	    ManagedReference<ManagedObject> ref = createRefForAdd((E) obj);

	    // Get the stored element value, depending on
	    // what kind of value it was (ManagedObject or Element)
	    return (E) getValueFromReference(contents.set(index, ref), true);
	}

	/**
	 * Appends the supplied argument to the list.
	 * 
	 * @param e the element to add to append
	 * @return whether the operation was successful; {@code true} if so,
	 * {@code false} otherwise
	 */
	boolean append(E e) {
	    assert (e != null);

	    // If it is not yet a ManagedObject, then
	    // create a new Element to make it a ManagedObject
	    ManagedReference<ManagedObject> ref = createRefForAdd(e);
	    AppContext.getDataManager().markForUpdate(this);
	    return contents.add(ref);
	}

	/**
	 * Returns the index of the element inside the {@code SubList<E>}. If
	 * the element does not exist, then -1 is returned.
	 * 
	 * @param o the element whose last index is to be found
	 * @return the index of the element, or -1 if it does not exist
	 */
	int lastIndexOf(Object o) {
	    assert (o != null);
	    Iterator<ManagedReference<ManagedObject>> iter =
		    contents.iterator();
	    int index = 0;
	    int lastIndex = -1;

	    // Iterate through all contents,
	    // checking for equality
	    while (iter.hasNext()) {
		ManagedReference<ManagedObject> ref = iter.next();
		Object obj = getValueFromReference(ref, false);
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
	 * @param index the index to add the new element.
	 * @param e the object which is to be inserted at the specified
	 * {@code index}
	 * @throws IndexOutOfBoundsException if the supplied index is outside
	 * of the range of the underlying list
	 */
	void insert(int index, E e) {
	    AppContext.getDataManager().markForUpdate(this);
	    assert (e != null);
	    if (index < 0) {
		throw new IndexOutOfBoundsException(
			"Supplied index cannot be less than 0");
	    }
	    ManagedReference<ManagedObject> ref = createRefForAdd(e);

	    contents.add(index, ref);
	}

	/**
	 * Sets up the ref {@code ManagedReference} so that it contains a
	 * serialized {@code ManagedObject}.
	 * 
	 * @param e the element to potentially wrap in an {@code Element}
	 * @return a reference to the wrapped element, ready to be stored into
	 * a {@code SubList}
	 * @throws IllegalArgumentException if the element does not implement
	 * the {@code Serializable} interface
	 */
	private ManagedReference<ManagedObject> createRefForAdd(E e) {
	    assert (e != null);
	    ManagedReference<ManagedObject> ref = null;

	    if (!(e instanceof Serializable)) {
		throw new IllegalArgumentException(
			"The element does not implement "
				+ "the Serializable interface");
	    }

	    // Determine if we need to wrap the parameter or not.
	    if (e instanceof ManagedObject) {
		ref =
			AppContext.getDataManager().createReference(
				(ManagedObject) e);
	    } else {
		Element<E> element = new Element<E>(e);
		ref =
			AppContext.getDataManager().createReference(
				(ManagedObject) element);
	    }
	    return ref;
	}

	/**
	 * Determines the index of the first occurrence of the supplied
	 * argument. If the element does not exist, then -1 is returned.
	 * 
	 * @param o the element whose index is to be searched
	 * @return the first index of the supplied element, or -1 if it does
	 * not exist in the list
	 */
	int indexOf(Object o) {
	    assert (o != null);
	    Iterator<ManagedReference<ManagedObject>> iter =
		    contents.iterator();
	    int index = 0;

	    // Iterate through all the list
	    // contents until we find a match
	    while (iter.hasNext()) {
		ManagedReference<ManagedObject> ref = iter.next();
		Object obj = getValueFromReference(ref, false);
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
	 * @param index the index to remove
	 * @return the object removed from the index
	 * @throws IndexOutOfBoundsException if the index is outside of the
	 * range of the underlying list
	 */
	@SuppressWarnings("unchecked")
	E remove(int index) {
	    if (index > contents.size() - 1) {
		throw new IndexOutOfBoundsException(
			"The index is out of bounds");
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    return (E) getValueFromReference(contents.remove(index), true);
	}

	/**
	 * Removes the supplied object from the underlying list, if it exists.
	 * 
	 * @param obj the element to remove from the list
	 * @return whether the operation was successful; {@code true} if so,
	 * {@code false} otherwise
	 */
	boolean remove(Object obj) {

	    Iterator<ManagedReference<ManagedObject>> iter =
		    contents.iterator();

	    // go through all the elements in this collection (a sublist)
	    while (iter.hasNext()) {
		ManagedReference<ManagedObject> current = iter.next();
		Object object = getValueFromReference(current, false);

		if (obj.equals(object)) {
		    // remove the ManagedReference from the sub list.
		    // If the object that the ManagedReference was
		    // pointing to is an Element object, remove it
		    // from the data manager.
		    AppContext.getDataManager().markForUpdate(this);
		    iter.remove();
		    Object stored = current.get();
		    if (stored instanceof Element) {
			AppContext.getDataManager().removeObject(stored);
		    }
		    return true;
		}
	    }
	    return false;
	}
    }

    /**
     * An interface which unifies the concept of a {@code ListNode} and
     * {@code TreeNode} as elements within a {@code ScalableList}. This
     * interface avoids cast warnings when the identity of a given element is
     * not immediately known.
     * 
     * @param <E> the type of element stored in the {@code ScalableList}
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
	 * Walks up the tree and removes the object and any of its parents.
	 * This method is intended to be called during the
	 * {@code AsynchronousClearTask} operation.
	 */
	void clear();

	/**
	 * Traverses the tree (recursively) in search of the ListNode<E>
	 * which contains the index provided. If no ListNode<E> can be found,
	 * then null is returned.
	 * 
	 * @param currentValue the current index value at the beginning of
	 * this current search
	 * @param destIndex the absolute index of the desired element
	 * @return the {@code ListNode} containing the absolute
	 * {@code destIndex}
	 * @throws IndexOutOfBoundsException if the index is out of bounds
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
	 * @param next the next {@code Node}
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
	 * @param prev the previous {@code Node}
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

	/**
	 * The target node where the element lives
	 */
	final ListNode<E> node;

	/**
	 * The offset of the element within the {@code ListNode}'s
	 * {@code SubList}
	 */
	final int offset;

	/**
	 * Constructor which creates a new SearchResult based on the target
	 * node and the calculated offset. The offset is not an absolute
	 * index, but rather the index of the element with respect to the
	 * {@code SubList} of the {@code ListNode}.
	 * 
	 * @param node the target node
	 * @param offset the index of the element in this {@code ListNode}'s
	 * {@code SubList}.
	 */
	SearchResult(ListNode<E> node, int offset) {
	    this.node = node;
	    this.offset = offset;
	}
    }
}
