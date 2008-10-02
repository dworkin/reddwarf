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
 * This class represents an {@code AbstractCollection} which supports a
 * concurrent and scalable behavior. This data structure builds upon the
 * AbstractList class by implementing methods specific to concurrent and
 * scalable operations. As an implementation decision, this data structure does
 * not support {@code null} insertions.
 * <p>
 * 
 * Those who are interested in using a simple data structure that is not
 * intended to grow very large, contains simple elements, but offers decent
 * concurrency should use the
 * {@code java.util.concurrent.CopyOnWriteArrayList<E>} type. However, if lists
 * are intended to be very large and cloning the list is infeasible due to its
 * size or the complexity of its contents, then the {@code ScalableList} is a
 * better tool as it does not require cloning and it scales reasonably well.
 * <p>
 * 
 * The class achieves scalability and concurrency by partitioning an ordinary
 * list into a number of smaller lists contained in {@code ListNode} objects,
 * and joining the nodes in a tree format. This implementation bears similarity
 * to a skip-list in that access to arbitrary elements occurs through initially
 * large jumps, and then through a finer iteration of the contained list. To
 * allow for this behaviour, each {@code ListNode} holds a subset of the
 * contents so that changes in the entries need not propagate to all elements at
 * once. In fact, each {@code ListNode} only holds onto the size of its sublist
 * (its children) and not a cumulative total of all its previous siblings. This
 * enables intermediate changes to have no effect on neighbouring nodes, such as
 * re-indexing.
 * <p>
 * 
 * The {@code branchingFactor} is a user-defined parameter which describes how
 * the underlying tree is organized. A large {@code branchingFactor} means that
 * each node in the tree contains a large number of children, providing for a
 * shallower tree, but many more sibling traversals. Concurrency is somewhat
 * compromised since parent nodes containing a large number of children are
 * locked during modification. A smaller branching factor reduces the sibling
 * traversals, but makes the tree deeper, somewhat affecting performance during
 * split operations. Depending on the use of the list, it may be desirable to
 * have a large {@code branchingFactor}, such as for improved scalability, or a
 * smaller {@code branchingFactor}, such as for better concurrency.
 * <p>
 * 
 * When the nodes require modification, iterators are responsible for
 * interpreting the node sizes and dealing with changes. Iterations are also
 * responsible for determining attributes of the list, like size, in roughly
 * O(1/n) time. As mentioned earlier, performing splits and removing unused
 * nodes can be somewhat expensive, depending on the values set for the
 * {@code branchingFactor} and {@code bucketSize}. This is because changes that
 * occur at the leaf level need to propagate to the parents; for a deep tree,
 * this can affect a number of different nodes. However, it is seen that the
 * benefits provided by the partitioning of the list that enable concurrency
 * outweigh the performance hit for these operations.
 * <p>
 * 
 * When an element is requested from the data structure, the iterator's position
 * is not affected by modifications to the list prior to its current location.
 * However, if modifications happen after the current {@code ListNode} being
 * examined and the destination, they will be involved in the iteration.
 * However, if a modification in the form of an addition or removal happens on
 * the same {@code ListNode}, then the iterator will throw a
 * {@code ConcurrentModificationException} as a result of a compromise to the
 * integrity of the node. This exception is not thrown if an element is replaced
 * using the {@code set()} method because there would be no change in index to
 * prompt the exception.
 * <p>
 * 
 * Since the {@code ScalableList} type is a {@code ManagedObject}, it is
 * important to note that applications which instantiate it should be
 * responsible for removing it from the Darkstar data store. This can be done by
 * statically obtaining the {@code DataManager} through the {@code AppContext}
 * via the call {@code AppContext.getDataManager().removeObject(ManagedObject)}.
 * <p>
 * 
 * Contrary to the {@code ScalableList}, iterators both within and provided by
 * the {@code ScalableList} type are not {@code ManagedObject)s.  Therefore,
 * they are not stored within the data manager and do not need to be removed
 * using the {@code DataManager}.
 * <p>
 * 
 * Since the list is capable of containing many elements, applications which use
 * iterators to traverse elements should be aware that prolonged iterative tasks
 * have the potential to lock out other concurrent tasks on the list. Therefore,
 * it is highly recommended that these tasks be recurring and scheduled so that
 * locks on visited elements are released when the task is temporarily inactive.
 * This strategy improves the concurrency of the {@code ScalableList} as it
 * reduces the locked elements owned by any one task.
 * <p>
 * 
 * @param E
 *            the type of the elements stored in the {@code ScalableList}
 */
public class ScalableList<E> extends AbstractList<E> implements ManagedObject,
		Serializable {

	private static final long serialVersionUID = 1L;

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
	 * The maximum size of the intermediate lists. This number should be small
	 * enough to enable concurrency but large enough to contain a reasonable
	 * number of nodes. If it is not explicitly set, it defaults to a value of
	 * 10.
	 */
	private int bucketSize = 10;

	/**
	 * The maximum number of children contained in a TreeNode<E>; this
	 * parameter is passed to the TreeNode<E> during instantiation. If it is
	 * not explicitly set, it defaults to a value of 5.
	 */
	private int branchingFactor = 5;

	/*
	 * IMPLEMENTATION
	 */

	/**
	 * Constructor which creates a {@code ScalableList} object with default
	 * values for the {@code bucketSize} and {@code branchingFactor}.
	 */
	public ScalableList() {
	root = null;
	headRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	tailRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	}

	/**
	 * Constructor which creates a {@code ScalableList} object with the
	 * {@branchingFactor} and {@code bucketSize} supplied as a parameter. The
	 * {@code bucketSize} can be any integer larger than 0, however the
	 * {@code branchingFactor} must be larger than 1 so that the tree can be
	 * meaningful. Otherwise, it would only be able to grow to a maximum size of
	 * {@code bucketSize} since branching could not introduce any additional
	 * children.
	 * 
	 * @param branchingFactor
	 *            the number of children each node should have. A
	 *            {@code branchingFactor} of 2 means that the tree structure is
	 *            binary.
	 * @param bucketSize
	 *            the size of each partitioned list. This value must be a
	 *            positive integer (larger than 0).
	 */
	public ScalableList(int branchingFactor, int bucketSize) {
	headRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	tailRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	root = null;

	if (bucketSize < 1) {
	throw new IllegalArgumentException("Cluster size must "
			+ "be an integer larger than 0");
	}
	if (branchingFactor < 2) {
	throw new IllegalArgumentException("Max child size must "
			+ "be an integer larger than 1");
	}
	this.bucketSize = bucketSize;
	this.branchingFactor = branchingFactor;
	}

	/**
	 * Constructor which creates a {@code ScalableList} object with the
	 * {@branchingFactor}, {@code bucketSize}, and a {@code collection}
	 * supplied as parameters. The {@code bucketSize} can be any integer larger
	 * than 0, however the {@code branchingFactor} must be larger than 1 so that
	 * the tree can be meaningful. Otherwise, it would only be able to grow to a
	 * maximum size of {@code bucketSize} since branching could not introduce
	 * any additional children. The {@code collection} represents a
	 * {@code Collection} of elements which will be added to the newly formed
	 * {@code ScalableList}
	 * 
	 * @param branchingFactor
	 *            the number of children each node should have. A
	 *            {@code branchingFactor} of 2 means that the tree structure is
	 *            binary.
	 * @param bucketSize
	 *            the size of each partitioned list. This value must be a
	 *            positive integer (larger than 0).
	 * @param collection
	 *            a collection of objects to initially populate the
	 *            {@code ScalableList}
	 */
	public ScalableList(int branchingFactor, int bucketSize,
			Collection<E> collection) {
	headRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	tailRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	root = null;

	// Ensure that the parameters are valid.
	if (bucketSize < 1) {
	throw new IllegalArgumentException("Cluster size must "
			+ "be an integer larger than 0");
	}
	if (branchingFactor < 2) {
	throw new IllegalArgumentException("Max child size must "
			+ "be an integer larger than 1");
	}
	this.bucketSize = bucketSize;
	this.branchingFactor = branchingFactor;

	this.addAll(collection);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean add(E e) {
	if (e == null) {
	throw new IllegalArgumentException("Element cannot be null");
	}

	// first item to add into the list
	if (getTail() == null) {
	return addFirstEntry(e);
	}

	// otherwise, add it at the end since no index
	// and propagate change to parents
	boolean result = getTail().append(e);

	// update the tail in case it has changed.
	if (getTail().next() != null) {
	setTail(getTail().next());
	}

	return result;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IllegalArgumentException
	 *             if either the index is out of bounds or if the element is
	 *             null
	 */
	public void add(int index, E e) {
	isValidIndex(index);

	// Check for the different boundary cases
	if (e == null) {
	throw new IllegalArgumentException("Element cannot be null");
	} else if (getHead() == null && index == 0) {
	addFirstEntry(e);
	return;
	} else if (getHead() == null) {
	throw new IllegalArgumentException("Cannot add to index " + index
			+ " on an empty list");
	}

	// otherwise, add it to the specified index.
	// This requires a search of the list nodes.
	ListNode<E> n = getNode(index);
	n.insert(n.getSubList().getOffset(), e);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IllegalArgumentException
	 *             if a null element is encountered.
	 */
	public boolean addAll(int index, Collection<? extends E> c) {
	Iterator<? extends E> iter = c.iterator();
	while (iter.hasNext()) {
	add(index, iter.next());
	index++;
	}
	return true;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IllegalArgumentException
	 *             if a null element is encountered.
	 */
	public boolean addAll(Collection<? extends E> c) {
	Iterator<? extends E> iter = c.iterator();
	E e = null;

	// Append each element from the collection.
	while (iter.hasNext()) {
	e = iter.next();

	// if an element was not added for some reason,
	// abort and return false.
	if (!add(e)) {
	return false;
	}
	}
	return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public void clear() {
	if (headRef == null) {
	return;
	}

	// Schedule asynchronous task here which will delete the list.
	AppContext.getTaskManager()
			.scheduleTask(new AsynchronousClearTask<E>(root));

	// Create a new ListNode<E> and link everything to it.
	TreeNode<E> t = new TreeNode<E>(this, null, branchingFactor, bucketSize);
	headRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());
	tailRef = AppContext.getDataManager().createReference(
			new DummyConnector<E>());

	}

	/**
	 * {@inheritDoc}
	 */
	public boolean contains(Object o) {
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
	int index = -1;

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
	int index = -1;
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
	 * natural number and thus not negative. If not, then an
	 * {@code IndexOutOfBoundsException} is thrown.
	 * 
	 * @param index
	 *            the index to check for validity
	 * @throws {@code IndexOutOfBoundsException}
	 *             if the index is out of bounds
	 */
	private void isValidIndex(int index) {
	if (index < 0) {
	throw new IndexOutOfBoundsException("Index cannot be less than 0; was "
			+ index);
	}
	}

	/**
	 * {@inheritDoc}
	 */
	public E remove(int index) {
	isValidIndex(index);
	ListNode<E> n = getNode(index);
	if (n == null) {
	return null;
	}

	// Performs any relinking in case the removed
	// ListNode<E> was the head or tail
	E e = n.remove(n.getSubList().getOffset());
	relinkIfNecessary(n);

	return e;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean removeAll(Collection<?> c) {
	Iterator<?> iter = c.iterator();
	Object obj = null;

	// For each element in the collection,
	// try and remove it, and stop if we run
	// into a problem.
	while (iter.hasNext()) {
	obj = iter.next();

	if (!remove(obj)) {
	return false;
	}
	}
	return true;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * This operation preserves the order of the elements in the list and keeps
	 * multiple copies of the same elements if they exist.
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
	 *            the {@code ListNode} which may have been removed
	 */
	private void relinkIfNecessary(ListNode<E> n) {
	if (n == null) {
	return;
	}
	// Store values before they are deleted
	ListNode<E> next = n.next();
	ListNode<E> prev = n.prev();

	// Check whether we need to update the head or tail.
	if (getHead() == null) {
	// Check if we need to search in another TreeNode<E>
	if (next != null) {
	setHead(next);
	} else {
	setHead(null);
	}
	}
	// Update the tail
	if (getTail() == null) {
	// Check if we need to search in another TreeNode<E>
	if (prev != null) {
	setTail(prev);
	} else {
	setTail(null);
	}
	}
	}

	/**
	 * Retrieves the head {@code ListNode}. In the event that one cannot be
	 * located, it gracefully returns null.
	 * 
	 * @return the head {@code ListNode} if it exists, or null otherwise.
	 */
	private ListNode<E> getHead() {
	ListNode<E> head;
	try {
	head = headRef.get().getRefAsListNode();
	} catch (ObjectNotFoundException onfe) {
	// This is expected if the node has been removed.
	head = null;
	}
	return head;
	}

	/**
	 * Retrieves the tail {@code ListNode}. In the event that one cannot be
	 * located, it gracefully returns null.
	 * 
	 * @return the tail {@code ListNode} if it exists, or null otherwise.
	 */
	private ListNode<E> getTail() {
	ListNode<E> tail;
	try {
	tail = tailRef.get().getRefAsListNode();
	} catch (ObjectNotFoundException onfe) {
	// This is expected if the node has been removed.
	tail = null;
	}
	return tail;
	}

	private void setTail(ListNode<E> newTail) {
	tailRef.get().setRef(newTail);
	}

	private void setHead(ListNode<E> newHead) {
	headRef.get().setRef(newHead);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IndexOutOfBoundsException
	 *             if either of the indices are seen to be less than 0, or if
	 *             the range is out of bounds
	 * @throws IllegalArgumentException
	 *             if the {@code toIndex} is less than the {@code fromIndex}
	 */
	public List<E> subList(int fromIndex, int toIndex) {
	// Check for illegal values
	if (fromIndex < 0 || toIndex < 0) {
	throw new IndexOutOfBoundsException("The indices " + fromIndex + " and/or "
			+ toIndex + " are invalid");
	} else if (toIndex < fromIndex) {
	throw new IllegalArgumentException("The toIndex (" + toIndex
			+ ") cannot be less than the fromIndex (" + fromIndex + ")");
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
	 * {@inheritDoc}
	 */
	public Object[] toArray() {
	List<E> list = subList(0, size() - 1);
	return list.toArray();
	}

	/**
	 * {@inheritDoc}
	 */
	public Object[] toArray(Object[] a) {
	Object[] list = toArray();

	// We need to return the new list if the
	// supplied list is not large enough.
	if (a.length < list.length) {
	return list;
	}

	for (int i = 0; i < list.length; i++) {
	a[i] = list[i];
	}
	return a;
	}

	/**
	 * {@inheritDoc}
	 */
	public E set(int index, Object obj) {
	Object newValue = obj;
	E old = null;
	if (obj == null) {
	throw new NullPointerException("Value for set operation cannot be null");
	}
	SubList<E> n = getNode(index).getSubList();
	old = n.set(n.getOffset(), newValue);

	// If the value is wrapped in an Element,
	// extract the element.
	if (old instanceof Element) {
	old = ((Element<E>) old).getValue();
	}
	return old;
	}

	/**
	 * {@inheritDoc}
	 */
	public E get(int index) {

	// Iterate through using the count. Use a get()
	// iterator because we are not modifying anything; hence, false.
	SubList<E> n = getNode(index).getSubList();
	if (n == null) {
	return null;
	}
	return n.get(n.getOffset());
	}

	/**
	 * {@inheritDoc}
	 */
	public Iterator<E> iterator() {
	ListNode<E> ln = getHead();
	if (ln != null) {
	return new ScalableListIterator<E>(ln);
	} else {
	return null;
	}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean equals(Object obj) {
	if (obj == null || !(obj instanceof ScalableList)) {
	return false;
	}

	ScalableList<E> list = (ScalableList<E>) obj;
	return AppContext.getDataManager().createReference(this).equals(
			AppContext.getDataManager().createReference(list));
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
	break;
	}

	}

	// Relink neighboring ListNodes in case this one was
	// removed due to being empty.
	relinkIfNecessary(n);

	return removed;
	}

	/**
	 * Retrieves the root {@code TreeNode} if it exists or null otherwise.
	 * 
	 * @return the root node, or null if it does not exist
	 */
	private TreeNode<E> getRoot() {
	if (this.root == null) {
	return null;
	}
	TreeNode<E> root;
	try {
	root = this.root.get();
	} catch (ObjectNotFoundException onfe) {
	root = null;
	}
	return root;
	}

	/**
	 * Obtains the child of the root node, since the root node does not get
	 * updated unless a split/removal occurs. For most operations, returning the
	 * child is sufficient.
	 * 
	 * @return
	 */
	private Node<E> getRootChild() {
	if (getRoot() == null) {
	return null;
	}
	return getRoot().getChild();
	}

	/**
	 * Sets the root element of the underlying tree structure. This is necessary
	 * during a split or remove.
	 * 
	 * @param newRoot
	 *            the {@code TreeNode} which is to be the new root
	 */
	public void setRoot(TreeNode<E> newRoot) {
	if (root == null) {
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
	 * Performs the search for the desired ListNode<E>. This method implicitly
	 * updates the offset value for the particular node we are finding, meaning
	 * when we locate the ListNode<E>, the OffsetNode child will be updated to
	 * represent the offset of the index we are searching for.
	 * 
	 * @param index
	 *            the absolute index in the entire list to search for the entry
	 * @return the {@code ListNode<E>} which contains the element at the
	 *         specified {@code index}
	 */
	private ListNode<E> getNode(int index) {
	// Recursive method to eventually return ListNode<E>
	// containing the desired index.
	return search(getRootChild(), 0, index + 1);
	}

	/**
	 * Traverses the tree (recursively) in search of the ListNode<E> which
	 * contains the index provided. If no ListNode<E> can be found, then null
	 * is returned.
	 * 
	 * @param current
	 *            the node from which to start searching
	 * @param currentValue
	 *            the current index value at the beginning of this current
	 *            search
	 * @param destIndex
	 *            the absolute index of the desired element
	 * @return the {@code ListNode} containing the absolute {@code destIndex}
	 */
	private ListNode<E> search(Node<E> current, int currentValue, int destIndex) {
	if (current == null) {
	throw new NullPointerException("Root search node cannot be null");
	}

	if (current instanceof TreeNode) {
	TreeNode<E> t = (TreeNode<E>) current;

	// iterate through siblings
	while ((currentValue += t.size) < destIndex) {
	t = t.next();

	// The specified index was too large; hence
	// throw an IndexOutOfBoundsException
	if (t == null) {
	throw new IndexOutOfBoundsException("The " + "index " + destIndex
			+ " is out of range.");
	}
	}
	currentValue -= t.size();
	return search(t.getChild(), currentValue, destIndex);

	} else if (current instanceof ListNode) {
	ListNode<E> n = (ListNode<E>) current;

	while ((currentValue += n.size()) < destIndex) {
	n = n.next();

	// The specified index was too large; hence
	// throw an IndexOutOfBoundsException
	if (n == null) {
	throw new IndexOutOfBoundsException("The " + "index " + destIndex
			+ " is out of range.");
	}
	}
	currentValue -= n.size();
	n.getSubList().setOffset(destIndex - currentValue - 1);
	return n;

	} else {
	throw new IllegalArgumentException("The instance of the ManagedObject "
			+ "argument is not supported for searching");
	}
	}

	/**
	 * Adds the object as the first entry of the list when the list is not yet
	 * populated.
	 * 
	 * @param e
	 *            the element to add as the first entry into the
	 *            {@code ScalableList}
	 * @return whether the operation was successful; true if so, false otherwise
	 */
	private boolean addFirstEntry(E e) {
	if (e == null) {
	throw new IllegalArgumentException("Element cannot be null");
	}
	TreeNode<E> t = new TreeNode<E>(this, null, branchingFactor, bucketSize, e);

	// Link the pointers, including the ScalableList head and tail
	DataManager dm = AppContext.getDataManager();
	root = dm.createReference(t);
	ListNode<E> n = (ListNode<E>) t.getChild();
	setHead(n);
	setTail(n);
	return true;
	}

	/**
	 * Retrieves the size of the list. The size of the list is obtained by
	 * performing a traversal of the root's children and adding each of their
	 * sizes. For very large lists, this process can be somewhat expensive as it
	 * does not occur in constant-time, but rather in a logarithmic time
	 * proportional to the {@code branchingFactor}.
	 * 
	 * @return the number of elements in the list
	 */
	public int size() {
	// This gives us a reference to the one and only
	// root TreeNode<E>, which parents the entire tree.
	// It should be noted that this object is not the
	// same as the headTreeNodeRef, which returns the
	// first child of the root.
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
	 * {@code ManagedReference} need not affect the top structure. The class is
	 * fairly simple, only containing methods which enable the value to be
	 * easily set and retrieved.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
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
		public DummyConnector() {
		ref = null;
		}

		/**
		 * Constructor which accepts the object
		 * 
		 * @param reference
		 *            the element which this object is to point to
		 */
		public DummyConnector(Node<E> reference) {
		if (reference != null) {
		ref = AppContext.getDataManager().createReference(reference);
		}
		}

		/**
		 * Sets the reference for this object.
		 * 
		 * @param newRef
		 *            the intended new reference
		 */
		public void setRef(Node<E> newRef) {
		if (newRef != null) {
		ref = AppContext.getDataManager().createReference(newRef);
		} else {
		ref = null;
		}
		}

		/**
		 * Retrieves the reference as a {@code ListNode}.
		 * 
		 * @return the reference as a {@code ListNode}, or null if it does not
		 *         exist
		 */
		public ListNode<E> getRefAsListNode() {
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
	 * also has a reference to its sibling and its parent.
	 * <p>
	 * 
	 * The {@code TreeNode<E>} is intended to only track the size of its
	 * descendant children and the number of children that it owns.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class TreeNode<E> implements ManagedObject, Serializable, Node<E> {

		// Public fields referenced during propagation
		public static final byte DECREMENT_SIZE = 0;
		public static final byte INCREMENT_SIZE = 1;
		public static final byte INCREMENT_NUM_CHILDREN = 2;
		public static final byte DECREMENT_NUM_CHILDREN = 3;
		public static final byte DECREMENT_CHILDREN_AND_SIZE = 4;

		private static final long serialVersionUID = 1L;

		// References to neighboring elements
		private ManagedReference<TreeNode<E>> nextRef;
		private ManagedReference<TreeNode<E>> prevRef;
		private ManagedReference<Node<E>> childRef;
		private ManagedReference<Node<E>> tailRef;
		private ManagedReference<TreeNode<E>> parentRef;
		private ManagedReference<ScalableList<E>> owner;

		// Propagated values
		/**
		 * The maximum number of children this node can contain
		 */
		private int BRANCHING_FACTOR = 5;

		/**
		 * The maximum number of elements which the underlying {@code ListNode}
		 * can store
		 */
		private int BUCKET_SIZE = 0;

		/**
		 * The number of elements that exist as descendants of this node
		 */
		private int size = 0;

		/**
		 * The number of immediate children
		 */
		private int childrenCount = 0;

		/**
		 * Constructor which does not link any references.
		 */
		public TreeNode() {
		size = 0;
		nextRef = null;
		childRef = null;
		parentRef = null;
		}

		/**
		 * Constructor which is called on a split. This is used to create a new
		 * sibling and accepts a TreeNode argument which represents the new
		 * child it is to possess.
		 * 
		 * @param list
		 *            the {@code ScalableList} which owns this node
		 * @param parent
		 *            the intended parent
		 * @param branchingFactor
		 *            the maximum number of children
		 * @param bucketSize
		 *            the maximum number of elements which can be stored by the
		 *            underlying {@code ListNode}
		 * @param child
		 *            the element to add as a descendant to this node
		 * @param numberChildren
		 *            the current number of children
		 * @param size
		 *            the current number of descendant elements
		 */
		private TreeNode(ScalableList<E> list, TreeNode<E> parent,
				int branchingFactor, int bucketSize, ListNode<E> child,
				int numberChildren, int size) {

		if (branchingFactor < 0) {
		throw new IllegalArgumentException("Maximum children parameter should "
				+ "not be less than 0");
		}
		BRANCHING_FACTOR = branchingFactor;
		nextRef = null;
		this.BUCKET_SIZE = bucketSize;

		// Set up links
		DataManager dm = AppContext.getDataManager();
		if (child != null) {
		childRef = dm.createReference((Node<E>) child);

		// The tail element is the last linked node in the list
		ListNode<E> n = (ListNode<E>) childRef.get();
		while (n.next() != null) {
		n = (ListNode<E>) n.next();
		}
		tailRef = dm.createReference((Node<E>) n);
		}

		if (parent != null) {
		parentRef = dm.createReference(parent);
		}
		childrenCount = numberChildren;
		this.size = size;
		owner = AppContext.getDataManager().createReference(list);
		}

		/**
		 * Constructor which is called on a split. This is used to create a new
		 * sibling and accepts a TreeNode argument which represents the new
		 * child it is to possess.
		 * 
		 * @param list
		 *            a reference to the owning {@code ScalableList}
		 * @param parent
		 *            the intended parent
		 * @param branchingFactor
		 *            the branching factor of the node
		 * @param bucketSize
		 *            the maximum size of the underlying {@code ListNode}
		 * @param child
		 *            the child to be added underneath the new {@code TreeNode}
		 * @param numberChildren
		 *            the total number of children that will exist once the
		 *            {@code TreeNode} is instantiated. This is based on the
		 *            nature of the linked list of {@code ListNodes} attached to
		 *            the {@code child}.
		 * @param size
		 *            the total number of elements that will exist under the
		 *            {@code TreeNode}, based on the elements that already
		 *            exist among the {@code child} and its siblings.
		 */
		private TreeNode(ScalableList<E> list, TreeNode<E> parent,
				int branchingFactor, int bucketSize, Node<E> child,
				int numberChildren, int size) {

		if (branchingFactor < 0) {
		throw new IllegalArgumentException("Maximum children parameter should "
				+ "not be less than 0");
		}
		BRANCHING_FACTOR = branchingFactor;
		nextRef = null;
		this.BUCKET_SIZE = bucketSize;

		// Set up links
		DataManager dm = AppContext.getDataManager();
		if (child != null) {
		childRef = dm.createReference((Node<E>) child);

		// The tail element is the last linked node in the list
		TreeNode<E> n = (TreeNode<E>) getChild();
		while (n.next() != null) {
		n = (TreeNode<E>) n.next();
		}
		tailRef = dm.createReference((Node<E>) n);
		}

		if (parent != null) {
		parentRef = dm.createReference(parent);
		}

		childrenCount = numberChildren;
		this.size = size;
		owner = AppContext.getDataManager().createReference(list);
		}

		/**
		 * Create a new TreeNode on account of a new leaf ({@code ListNode})
		 * being created.
		 * 
		 * @param list
		 *            the {@code ScalableList} which is the owner of this
		 *            structure
		 * @param parent
		 *            the intended parent
		 * @param branchingFactor
		 *            the maximum number of children of this node
		 * @param bucketSize
		 *            the maximum size of the underlying {@code ListNode}
		 * @param e
		 *            an element to add into the empty {@code ListNode}
		 */
		public TreeNode(ScalableList<E> list, TreeNode<E> parent,
				int branchingFactor, int bucketSize, E e) {
		if (branchingFactor < 0) {
		throw new IllegalArgumentException("Maximum children parameter should "
				+ "not be less than 0");
		}

		if (e == null) {
		throw new NullPointerException("Argument suppled to List is null");
		}

		BRANCHING_FACTOR = branchingFactor;
		nextRef = null;
		this.BUCKET_SIZE = bucketSize;
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

		tailRef = dm.createReference((Node<E>) n);
		owner = AppContext.getDataManager().createReference(list);
		}

		/**
		 * Constructor which creates a {@code TreeNode} while specifying
		 * parameters for the node characteristics
		 * 
		 * @param list
		 *            the {@code ScalableList} owner of this structure
		 * @param parent
		 *            the intended parent
		 * @param branchingFactor
		 *            the maximum number of children
		 * @param bucketSize
		 *            the maximum number of elements for the underlying
		 *            {@code ListNode}
		 */
		public TreeNode(ScalableList<E> list, TreeNode<E> parent,
				int branchingFactor, int bucketSize) {

		if (branchingFactor < 0) {
		throw new IllegalArgumentException("Maximum children parameter should "
				+ "not be less than 0");
		}

		BRANCHING_FACTOR = branchingFactor;
		nextRef = null;
		this.BUCKET_SIZE = bucketSize;
		ListNode<E> n = new ListNode<E>(this, bucketSize);
		size = n.size();
		DataManager dm = AppContext.getDataManager();
		childRef = dm.createReference((Node<E>) n);
		if (parent != null) {
		parentRef = dm.createReference(parent);
		}
		tailRef = dm.createReference((Node<E>) n);
		owner = AppContext.getDataManager().createReference(list);
		}

		/**
		 * Obtains the previous sibling to the current {@code TreeNode}
		 * 
		 * @return the previous sibling, or null if none exist
		 */
		public TreeNode<E> prev() {
		TreeNode<E> prev;
		if (prevRef == null) {
		return null;
		}

		try {
		prev = prevRef.get();
		} catch (ObjectNotFoundException onfe) {
		prev = null;
		}
		return prev;
		}

		/**
		 * Sets the previous sibling to the parameter provided
		 * 
		 * @param ref
		 *            the intended previous sibling
		 */
		public void setPrev(TreeNode<E> ref) {
		if (ref != null) {
		prevRef = AppContext.getDataManager().createReference(ref);
		}
		}

		/**
		 * Obtains the next sibling of the current {@code TreeNode}
		 * 
		 * @return the next sibling, or null if none exist
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
		 * Obtains the child of the current node. The child represents the head
		 * of the list of children. The {@code TreeNode} does not have
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
		 * null.
		 * 
		 * @param child
		 *            the new child
		 */
		public void setChild(Node<E> child) {
		if (child != null) {
		childRef = AppContext.getDataManager().createReference(child);
		}
		}

		/**
		 * {@inheritDoc}
		 */
		public int size() {
		return size;
		}

		/**
		 * Sets the tail of the list to point to the supplied parameter.
		 * 
		 * @param ref
		 *            the new tail
		 */
		public void setTail(Node<E> ref) {
		if (ref != null) {
		tailRef = AppContext.getDataManager().createReference(ref);
		}
		}

		/**
		 * Retrieves the tail element of the list, which is a {@code ListNode}
		 * element.
		 * 
		 * @return the tail element, or null if none exists
		 */
		public Node<E> getTail() {
		Node<E> tail;
		if (tailRef == null) {
		return null;
		}
		try {
		tail = tailRef.get();
		} catch (ObjectNotFoundException onfe) {
		tail = null;
		}
		return tail;
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
		 * A convenience method which increments the number of children existing
		 * under a {@code TreeNode}. Note that this value is different from the
		 * size; the latter of which represents the total number of elements
		 * contained beneath this parent. In other words, all elements that are
		 * neither {@code ListNode}s or {@code TreeNode}s.
		 */
		public void incrementNumChildren() {
		childrenCount++;
		}

		/**
		 * A convenience method which decrements the number of children existing
		 * under a {@code TreeNode}. Note that this value is different from the
		 * size; the latter of which represents the total number of elements
		 * contained beneath this parent. In other words, all elements that are
		 * neither {@code ListNode}s or {@code TreeNode}s.
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
		 * @return the parent of this {@TreeNode}, or null if none exist.
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
		 * Sets the next element in the linked list.
		 * 
		 * @param t
		 *            the next element to link
		 */
		public void setNext(TreeNode<E> t) {
		if (t != null) {
		nextRef = AppContext.getDataManager().createReference(t);
		}
		}

		/**
		 * Unlinks itself from the tree without performing a recursive deletion.
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
		 * Determines where to split a list of children based on the list size.
		 * By default, this is set to {@code numberOfChildren}/2.
		 * 
		 * @param numberOfChildren
		 *            the number of children to split
		 * @return the index corresponding to the location where to split the
		 *         linked list
		 */
		private int calculateSplitSize(int numberOfChildren) {
		return Math.round(numberOfChildren / 2);
		}

		/**
		 * This method walks up the tree and performs any {@code TreeNode<E>}
		 * splits if the children sizes have been exceeded.
		 */
		public void performSplitIfNecessary() {
		TreeNode<E> newNode = null;
		Node<E> tmp = (Node<E>) this.getChild();

		// Check if we need to split, and if so, do it based
		// on the type of element contained (ListNode<E> or TreeNode<E>)
		if (childrenCount > BRANCHING_FACTOR) {
		generateParentIfNecessary(this);

		// Perform split by creating and linking new sibling
		newNode = createAndLinkSibling(tmp, childrenCount);
		childrenCount = childrenCount - calculateSplitSize(childrenCount);

		// Link the new sibling to the tree
		newNode.setPrev(this);
		newNode.setNext(this.next());
		this.setNext(newNode);
		if (newNode.next() != null) {
		newNode.next().setPrev(newNode);
		}

		// Once finished, propagate split to parent;
		// it will decide if further splitting is necessary.
		// If the parent ref is null, then we have reached
		// the root.
		if (getParent() != null) {
		getParent().performSplitIfNecessary();
		}
		}

		// If there is no parent, then this is the
		// new root node; update the list with this info.
		if (getParent() == null) {
		owner.get().setRoot(this);
		}
		}

		/**
		 * Determines the nature of the sibling to be created. The Node created
		 * will always be a TreeNode<E>, but the type of child depends on
		 * whether it parents ListNodes, or other TreeNodes.
		 * 
		 * @param child
		 *            the {@code ManagedObject} which needs to be split and
		 *            placed under a new parent
		 * @param numberOfChildren
		 *            the number of children which the new sibling will parent
		 * @return the {@TreeNode<E>} that not represents a sibling which is
		 *         connected to the tree
		 */
		private TreeNode<E> createAndLinkSibling(Node<E> child,
				int numberOfChildren) {
		Node<E> prev = null;
		TreeNode<E> newNode = null;
		int halfway = calculateSplitSize(numberOfChildren);
		int size = this.size;

		// Determine what kind of sibling to make, since a TreeNode<E>'s
		// child can be either a ListNode<E> or a TreeNode<E>
		if (child instanceof ListNode) {
		ListNode<E> listNodeChild = (ListNode<E>) child;
		ListNode<E> listNodePrev = (ListNode<E>) prev;

		// Get the approximate middle element
		for (int i = 0; i < halfway; i++) {
		listNodePrev = listNodeChild;
		listNodeChild = listNodeChild.next();
		size -= listNodePrev.size();
		}

		// Create the new sibling
		newNode = new TreeNode<E>(owner.get(), this.getParent(),
				BRANCHING_FACTOR, BUCKET_SIZE, listNodeChild, numberOfChildren
						- halfway, size);

		// Update parent references to new node for
		// all subsequent nodes
		listNodeChild.setParent(newNode);
		while (listNodeChild.next() != null) {
		listNodeChild = listNodeChild.next();
		listNodeChild.setParent(newNode);
		}

		} else if (child instanceof TreeNode) {
		TreeNode<E> treeNodeChild = (TreeNode<E>) child;
		TreeNode<E> treeNodePrev = (TreeNode<E>) prev;

		// Get the approximate middle element
		for (int i = 0; i < halfway; i++) {
		treeNodePrev = treeNodeChild;
		treeNodeChild = treeNodeChild.next();
		size -= treeNodePrev.size();
		}

		// Create the new sibling
		newNode = new TreeNode<E>(owner.get(), this.getParent(),
				BRANCHING_FACTOR, BUCKET_SIZE, treeNodeChild, numberOfChildren
						- halfway, size);

		// Update parent references to new node
		treeNodeChild.setParent(newNode);
		while (treeNodeChild.next() != null) {
		treeNodeChild = treeNodeChild.next();
		treeNodeChild.setParent(newNode);
		}

		} else {
		throw new IllegalStateException(
				"Attempting to split a node that is neither "
						+ "a TreeNode nor a ListNode");
		}
		this.size -= size;
		this.setTail(prev);

		// Increment the parent's child count. We are
		// guaranteed to have a parent because we
		// either updated its size already or created
		// a new one above.
		getParent().propagateChanges(TreeNode.INCREMENT_NUM_CHILDREN);

		return newNode;
		}

		/**
		 * This method creates a parent above the provided node so that any new
		 * siblings resulting from a split can be joined to the new parent. The
		 * decision to create a new parent depends on whether the supplied node
		 * is an only-child; only these nodes are orphaned while siblings have
		 * parents assigned to them.
		 * 
		 * @param t
		 *            the {@code TreeNode<E>} to check whether it requires a
		 *            parent
		 */
		private boolean generateParentIfNecessary(TreeNode<E> t) {
		boolean result = false;

		// If this is the currently existing root, make a new one
		// and set the supplied node as the child
		if (t.getParent() == null) {
		TreeNode<E> grandparent = new TreeNode<E>(owner.get(), null,
				BRANCHING_FACTOR, BUCKET_SIZE, t, 1, t.size());

		// Link the node to its new parent
		t.setParent(grandparent);

		// The list's pointer to the parent will be
		// updated when a traversal is issued.
		result = true;
		}
		return result;
		}

		/**
		 * Sets the parent for the node.
		 * 
		 * @param parent
		 *            the intended parent
		 */
		public void setParent(TreeNode<E> parent) {
		if (parent != null) {
		parentRef = AppContext.getDataManager().createReference(parent);
		}
		}

		/**
		 * Removes itself from the Data Store and recursively removes its
		 * children.
		 */
		public void clear() {
		DataManager dm = AppContext.getDataManager();

		// Determine what kind of child it is: TreeNode or ListNode
		if (getChild() != null && getChild() instanceof TreeNode) {
		TreeNode<E> current = (TreeNode<E>) getChild();
		TreeNode<E> next = current.next();

		// For each of its children, call the clear() method
		while (current != null) {
		current.clear();
		dm.removeObject(current);
		current = next;
		next = next.next();
		}

		} else if (getChild() != null && getChild() instanceof ListNode) {
		ListNode<E> current = (ListNode<E>) getChild();
		ListNode<E> next = current.next();

		// For each node, call the clear() method
		while (current != null) {
		current.clear();
		dm.removeObject(current);
		current = next;
		next = next.next();
		}

		}

		// unlink and self-destruct
		dm.removeObject(this);
		}

		/**
		 * Propagates changes in the form of either an increment or decrement to
		 * the parents of the tree.
		 * 
		 * @param mode
		 *            the type of propagation which is to occur, specified by
		 *            static {@code TreeNode} fields
		 * @throws IllegalArgumentException
		 *             when the mode is not recognized
		 */
		public void propagateChanges(byte mode) {

		// No need to make any updates if this is
		// the root node.
		if (this.equals(this.owner.get().getRoot())) {
		return;
		}

		// Otherwise, make the change, depending on the mode
		switch (mode) {
		// Increment the size
		case TreeNode.INCREMENT_SIZE:
		this.increment();
		if (getParent() != null) {
		getParent().propagateChanges(mode);
		}
		break;

		// Decrements the size and prepares for removal
		// if necessary
		case TreeNode.DECREMENT_SIZE:
		this.decrement();
		if (size() == 0) {
		if (getParent() != null) {
		getParent().propagateChanges(TreeNode.DECREMENT_CHILDREN_AND_SIZE);
		break;
		}
		}
		if (getParent() != null) {
		getParent().propagateChanges(mode);
		}
		break;

		// Increments the number of children. This does not
		// need to propagate; split() performs the
		// propagation while this method is called to
		// update the parent nodes in the process.
		case TreeNode.INCREMENT_NUM_CHILDREN:
		this.incrementNumChildren();
		break;

		// Decrements the child size. If the node is
		// empty, then it removes itself and
		// propagates further.
		case TreeNode.DECREMENT_CHILDREN_AND_SIZE:
		this.decrement();
		this.decrementNumChildren();

		// remove this from the tree if empty; otherwise
		// propagate decrementing of the size
		if (size() == 0) {
		remove();

		// since we are removing a TreeNode<E>,
		// check if parent needs to be removed
		// as well.
		if (getParent() != null) {
		getParent().propagateChanges(mode);
		}
		} else {
		if (getParent() != null) {
		getParent().propagateChanges(TreeNode.DECREMENT_SIZE);
		}
		}
		break;

		// If the mode was not recognized, throw an exception
		default:
		throw new IllegalArgumentException(
				"Supplied mode argument is not recognized; " + "expected "
						+ TreeNode.INCREMENT_SIZE + " or "
						+ TreeNode.DECREMENT_SIZE);
		}
		}
	}

	/**
	 * An asynchronous task which iterates through the tree and removes all
	 * children. This task is instantiated when a clear() command is issued from
	 * the {@code ScalableList}. The success of this operation is dependent on
	 * implemented {@code clear()} methods on each of the underlying components.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	private static class AsynchronousClearTask<E> implements Serializable,
			Task, ManagedObject {

		private static final long serialVersionUID = 4L;

		/**
		 * A reference to the current {@code TreeNode} whose children are being
		 * deleted
		 */
		private ManagedReference<TreeNode<E>> current;

		/**
		 * A reference to the next sibling element
		 */
		private ManagedReference<TreeNode<E>> next;

		/**
		 * Constructor for the asynchronous task
		 * 
		 * @param root
		 *            the root node of the entire tree structure
		 */
		public AsynchronousClearTask(ManagedReference<TreeNode<E>> root) {
		if (root != null) {
		current = AppContext.getDataManager().createReference(root.get());
		} else {
		current = null;
		}
		}

		/**
		 * The entry point of the task.
		 */
		public void run() {
		DataManager dm = AppContext.getDataManager();
		if (current == null) {
		return;
		}
		// for each TreeNode<E>, delete its children and move on
		next = dm.createReference(current.get().next());
		current.get().clear();
		current = next;

		// If we have more nodes, reschedule this task;
		// otherwise we can remove it from data store as
		// it is a ManagedObject.
		if (current != null) {
		AppContext.getDataManager().markForUpdate(this);
		AppContext.getTaskManager().scheduleTask(this);
		} else {
		dm.removeObject(this);
		}
		}
	}

	/**
	 * This class represents a stored entity of the list. It is a wrapper for
	 * any object that is stored in the list so that the list can refer to it by
	 * using a ManagedReference, rather than the actual object itself. This
	 * makes managing sublists less intensive as each reference to an underlying
	 * entity is a fixed size.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class Element<E> implements Serializable, ManagedObject {

		private static final long serialVersionUID = 5L;

		/**
		 * The stored value which is not a {@code ManagedObject}. If it were a
		 * {@code ManagedObject}, then it would not need to be enveloped by an
		 * {@code Element} wrapper.
		 */
		private E value;

		/**
		 * Constructor for creating an {@code Element}.
		 * 
		 * @param e
		 *            the element to store within the {@code Element}
		 *            {@code ManagedObject}
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
		 *            the new value to set
		 */
		public void setValue(E e) {
		value = e;
		}
	}

	/**
	 * An {@code Offset} object is a simple container for the offset of a
	 * desired index. An {@code Offset} object is attached to a
	 * {@code SubList<E>} object and is updated whenever a {@code get()}
	 * operation is called so that the index of the item in the
	 * {@code SubList<E>} maps to the absolute index provided by the
	 * {@code ScalableList}
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class Offset implements Serializable, ManagedObject {

		private static final long serialVersionUID = 6L;

		/**
		 * The value representing the relative offset for the {@code SubList}
		 * object. When an absolute index value is searched for, the offset is
		 * the difference between the absolute index and the starting index of
		 * the element's containing {@code ListNode}.
		 */
		private int offset = -1;

		/**
		 * Sets the offset to the supplied value.
		 * 
		 * @param offset
		 *            the new offset
		 */
		public void set(int offset) {
		this.offset = offset;
		}

		/**
		 * Retrieves the stored offset
		 * 
		 * @return the current offset
		 */
		public int get() {
		return offset;
		}
	}

	/**
	 * This iterator walks through the {@code ListNode<E>}s which parent the
	 * {@code SubList}s. An iterator for this type of element is necessary to
	 * support the other element iterator as well as {@code indexOf()}
	 * operations.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
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
		 *            the head {@code ListNode} from which to begin iterating
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
		 *             if there exists no next sibling
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
		throw new UnsupportedOperationException("This method is not supported");
		}
	}

	/**
	 * This class represents an iterator of the contents of the list.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class ScalableListIterator<E> implements Serializable, Iterator<E> {

		/**
		 * The current {@code ListNode} of the iterative process
		 */
		private ListNode<E> currentNode;

		/**
		 * The iterator responsible for iterating through the {@code ListNodes}
		 */
		private ScalableListNodeIterator<E> listNodeIter;

		/**
		 * The iterator responsible for iterating through the stored elements of
		 * the {@code ScalableList}
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
		 *            the head {@code ListNode} of the collection
		 */
		public ScalableListIterator(ListNode<E> head) {
		listNodeIter = new ScalableListNodeIterator<E>(head);

		// If we could not create an iterator of ListNodes,
		// then we return a null iterator.
		if (listNodeIter == null) {
		currentNode = null;
		iter = null;
		return;
		}

		// Prepare by getting first ListNode
		if (listNodeIter.hasNext()) {
		currentNode = (ListNode<E>) listNodeIter.next();
		listNodeReferenceValue = currentNode.getDataIntegrityValue();
		} else {
		currentNode = null;
		}

		// Do a last check to make sure we are getting non-null values
		if (currentNode == null || currentNode.getSubList() == null) {
		iter = null;
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
		 *             if the {@code ListNode} that the iterator is pointing to
		 *             has been modified to (addition or removal) by someone
		 *             else.
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
		ManagedReference<E> ref = ((ManagedReference<E>) iter.next());
		if (ref != null) {
		Object obj = ref.get();

		// In case we wrapped the item with an
		// Element object, fetch the value.
		if (obj instanceof Element) {
		return ((Element<E>) obj).getValue();
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
				"Removal of nodes is achieved through the ScalableList API");
		}
	}

	/**
	 * Node which parents a {@code SubList<E>}. These nodes can be considered
	 * as the leaf nodes of the tree and contain references to a portion of the
	 * list. A {@code ListNode}'s parent is always a {@code TreeNode} since
	 * they are the deepest organizational element of the {@code ScalableList}.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class ListNode<E> implements ManagedObject, Serializable, Node<E> {

		private static final int DATA_INTEGRITY_STARTING_VALUE = Integer.MIN_VALUE;

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
		 * Constructor which uses knowledge of a parent and maximum list size. A
		 * {@code ListNode} that exceeds {@code maxSize} will be subject to
		 * splitting.
		 * 
		 * @param parent
		 *            the intended parent
		 * @param maxSize
		 *            the maximum number of elements that can be stored
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
		 * Constructor which uses knowledge of a parent and maximum list size. A
		 * {@code ListNode} that exceeds {@code maxSize} will be subject to
		 * splitting.
		 * 
		 * @param parent
		 *            the intended parent
		 * @param e
		 *            an element which is to be stored as the first item in the
		 *            list
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
		 * Constructor which uses knowledge of a parent and maximum list size. A
		 * {@code ListNode} that exceeds {@code maxSize} will be subject to
		 * splitting.
		 * 
		 * @param parent
		 *            the intended parent
		 * @param maxSize
		 *            the maximum number of elements that can be stored
		 * @param e
		 *            an element which is to be stored as the first item in the
		 *            list
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
		 * Constructor which uses knowledge of a parent and maximum list size. A
		 * {@code ListNode} that exceeds {@code maxSize} will be subject to
		 * splitting.
		 * 
		 * @param parent
		 *            the intended parent
		 * @param maxSize
		 *            the maximum number of elements that can be stored
		 * @param list
		 *            a list of items which are to be added into the empty list
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
		 * Sets the next value in the {@code ListNode} linked list to be the
		 * supplied parameter.
		 * 
		 * @param ref
		 *            the next item in the {@code ListNode} linked list
		 */
		public void setNext(ListNode<E> ref) {
		if (ref != null) {
		nextRef = AppContext.getDataManager().createReference(ref);
		}
		}

		/**
		 * Sets the parent of the node.
		 * 
		 * @param parent
		 *            the intended parent of the {@code ListNode}
		 */
		public void setParent(TreeNode<E> parent) {
		if (parent != null) {
		parentRef = AppContext.getDataManager().createReference(parent);
		}
		}

		/**
		 * Retrieves the next {@code ListNode} from the linked list, or null if
		 * already at the tail.
		 * 
		 * @return the next {@code ListNode} in the linked list, or null if at
		 *         the tail
		 */
		public ListNode<E> next() {
		ListNode<E> next;
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
		 * Sets the previous element in the {@code ListNode} linked list to be
		 * the supplied parameter.
		 * 
		 * @param ref
		 *            the intended previous element
		 */
		public void setPrev(ListNode<E> ref) {
		if (ref != null) {
		prevRef = AppContext.getDataManager().createReference(ref);
		}
		}

		/**
		 * Retrieves the previous element in the {@code ListNode} linked list,
		 * or null if at the head.
		 * 
		 * @return the previous element in the list, or null if at the head
		 */
		public ListNode<E> prev() {
		ListNode<E> prev;
		if (prevRef == null) {
		return null;
		}
		try {
		prev = prevRef.get();
		} catch (ObjectNotFoundException onfe) {
		prev = null;
		}
		return prev;
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
		 * @return the {@code SubList} containing list elements, or null if one
		 *         has not yet been instantiated
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
		 * @param obj
		 *            the Object to append
		 * @return whether the operation was successful; true if so, false
		 *         otherwise
		 */
		public boolean append(E e) {
		boolean result = getSubList().append(e);
		if (result) {
		count++;
		dataIntegrityVal++;
		updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
		}

		// check if we need to split; i.e. if we have exceeded
		// the specified list size.
		if (count > getSubList().MAX_CHILDREN_APPEND) {
		split();
		}
		return result;
		}

		/**
		 * Inserts the supplied value at the given index. The index is relative
		 * to the current list and not the global collection.
		 * 
		 * @param index
		 *            the index to insert the value, relative to the current
		 *            {@code SubList}
		 * @param e
		 *            the value to insert
		 */
		public void insert(int index, E e) {
		getSubList().insert(index, e);
		count++;
		dataIntegrityVal++;
		updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);

		// check if we need to split; i.e. if we have exceeded
		// the specified list size.
		if (count > getSubList().MAX_CHILDREN) {
		split();
		}
		}

		/**
		 * Recursively removes children from the Data Store.
		 */
		public void clear() {
		count = 0;
		getSubList().clear();
		AppContext.getDataManager().removeObject(this);
		}

		/**
		 * Removes the object at the specified index of the sublist. The index
		 * argument is not an absolute index; it is a relative index which
		 * points to a valid index in the list.
		 * <p>
		 * 
		 * For example, if there are five ListNodes with a cluster size of five,
		 * the item with an absolute index of 16 corresponds to an element in
		 * the fourth ListNode<E>, with a relative offset of 1.
		 * 
		 * @param index
		 *            the index corresponding to an element in the list (not an
		 *            absolute index with respect to the {@code ScalableList}
		 *            object
		 * @return the element that was removed
		 */
		public E remove(int index) {
		E old = getSubList().remove(index);
		if (old != null) {
		count--;
		dataIntegrityVal++;
		updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);
		}

		// remove list node if necessary
		checkRemoveListNode();
		return old;
		}

		/**
		 * Removes the {@code Object} from the {@code SubList<E>}, if it
		 * exists.
		 * 
		 * @param obj
		 *            the {@code Object} to remove
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
		}
		// remove list node if it is empty
		checkRemoveListNode();

		return result;
		}

		/**
		 * Removes the {@code Element} from the {@code SubList<E>}, if it
		 * exists.
		 * 
		 * @param e
		 *            the {@code Element} to remove
		 * @return whether the object was removed or not; true if so, false
		 *         otherwise
		 */
		public boolean remove(Element<E> e) {
		boolean result = getSubList().remove(e);
		if (result) {
		count--;
		dataIntegrityVal++;
		}

		// remove list node if necessary
		checkRemoveListNode();

		return result;
		}

		/**
		 * A method that determines how to remove an empty {@code ListNode<E>}
		 * from a list of other {@code ListNode<E>}s. Update previous to point
		 * to next (doubly) and remove this object from Data Store
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
		if (this.next() != null && this.prev() != null) {
		this.prev().setNext(this.next());
		this.next().setPrev(this.prev());
		}
		// 2) head node. Update child pointer from parent
		else if (this.next() != null) {
		this.getParent().setChild((Node<E>) this.next());
		this.next().setPrev(null);
		}
		// 3) tail node
		else if (this.prev() != null) {
		this.prev().setNext(null);
		}
		// 4) only child
		else {
		this.getParent().setChild(null);
		}

		// If we are removing the child of the
		// TreeNode and the parent has other children,
		// then we need to give it a substitute
		if (this.getParent().size() > 0
				&& this.getParent().getChild().equals(this)) {
		this.getParent().setChild((Node<E>) this.next());
		}

		// This is an empty node, so remove it from Data Store.
		AppContext.getDataManager().removeObject(this);
		}

		/**
		 * Retrieves the parent of the {@code ListNode}
		 * 
		 * @return the parent
		 * @throws
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
		 *            the element to add to the collection
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
		ArrayList<ManagedReference<ManagedObject>> spawned = new ArrayList<ManagedReference<ManagedObject>>();

		// Walks up the tree to increment the new size
		updateParents(this.getParent(), TreeNode.INCREMENT_NUM_CHILDREN);

		// move last half of list into a new child
		int sublistSize = getSubList().size();
		int lower = Math.round(sublistSize / 2);
		for (int index = lower; index < sublistSize; index++) {
		spawned.add((ManagedReference<ManagedObject>) contents.get(index));
		}

		// remove the relocated nodes from the current list
		// and mark that the list has changed
		contents.removeAll(spawned);
		dataIntegrityVal++;
		this.count = contents.size();

		// Create a new ListNode<E> for the moved contents
		ListNode<E> spawnedNode = new ListNode<E>(getParent(), this
				.getSubList().MAX_CHILDREN, spawned);
		spawnedNode.setNext(this.next());
		spawnedNode.setPrev(this);
		this.setNext(spawnedNode);

		// Check with parent to see if more splitting is necessary
		getParent().performSplitIfNecessary();
		}

		/**
		 * Propagates changes in the form of either an increment or decrement to
		 * the parents of the tree.
		 * 
		 * @param parent
		 *            the parent of the current node
		 * @param mode
		 *            the type of update to perform, specified by the static
		 *            final {@code TreeNode} fields.
		 */
		private void updateParents(TreeNode<E> parent, byte mode) {
		parent.propagateChanges(mode);
		}

	}

	/**
	 * This object represents a partition in the list. Each of these objects
	 * lives as a singleton inside a ListNode<E> object.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static class SubList<E> implements ManagedObject, Serializable {

		/**
		 * The maximum number of children this {@code SubList} can contain
		 */
		public static final int DEFAULT_MAX_CHILDREN = 10;

		/**
		 * A reference to the list of elements
		 */
		private ArrayList<ManagedReference<ManagedObject>> contents;

		/**
		 * A reference to the object which contains the offset during list
		 * manipulation operations
		 */
		private ManagedReference<Offset> offsetRef;

		/**
		 * The size of the list of elements
		 */
		private int size = 0;

		/**
		 * The maximum number of children which can be contained, previously
		 * addressed as the {@code bucketSize}
		 */
		public int MAX_CHILDREN = DEFAULT_MAX_CHILDREN;

		/**
		 * A value corresponding to the maximum size of the list during an
		 * append operation. This value ensures that if the list is constantly
		 * being appended to, that the nodes are left partially empty so that
		 * inserts do not immediately cause splitting. As an implementation
		 * decision, the {@code MAX_CHILDREN_APPEND} value is calculated as
		 * {@code Math.round(MAX_CHILDREN * 2 / 3)}.
		 */
		public int MAX_CHILDREN_APPEND;

		private static final long serialVersionUID = 10L;

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
		 * @param maxSize
		 *            the maximum number of elements which can be stored
		 * @param collection
		 *            the elements to add to the empty list
		 */
		public SubList(int maxSize,
				ArrayList<ManagedReference<ManagedObject>> collection) {
		if (isLegal(maxSize)) {
		MAX_CHILDREN = maxSize;
		}

		contents = new ArrayList<ManagedReference<ManagedObject>>();
		// This sets a limit on the number of elements
		// which can be appended to a node. A value of 2/3 times
		// the maximum capacity allows for some insertions
		// without having to split the node.
		MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN) / 3);
		ManagedReference<ManagedObject> tmp = null;

		for (int i = 0; i < collection.size(); i++) {
		tmp = collection.get(i);
		contents.add(tmp);
		}

		size = contents.size();
		Offset offset = new Offset();
		offsetRef = AppContext.getDataManager().createReference(offset);
		}

		/**
		 * Constructor which creates a {@code SubList}
		 * 
		 * @param maxSize
		 *            the maximum number of elements which can be stored
		 */
		public SubList(int maxSize) {
		if (isLegal(maxSize)) {
		MAX_CHILDREN = maxSize;
		}

		// This sets a limit on the number of elements
		// which can be appended to a node. A value of 2/3 times
		// the maximum capacity allows for some insertions
		// without having to split the node.
		MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN) / 3);

		contents = new ArrayList<ManagedReference<ManagedObject>>();
		size = contents.size();
		Offset offset = new Offset();
		offsetRef = AppContext.getDataManager().createReference(offset);
		}

		/**
		 * Constructor to create a {@code SubList}
		 * 
		 * @param maxSize
		 *            the maximum number of elements which can be stored
		 * @param e
		 *            an element to add to the empty list, at the first index
		 */
		public SubList(int maxSize, E e) {
		if (isLegal(maxSize)) {
		MAX_CHILDREN = maxSize;
		}
		// This sets a limit on the number of elements
		// which can be appended to a node. A value of 2/3 times
		// the maximum capacity allows for some insertions
		// without having to split the node.
		MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN) / 3);

		contents = new ArrayList<ManagedReference<ManagedObject>>();
		append(e);
		size = contents.size();
		Offset offset = new Offset();
		offsetRef = AppContext.getDataManager().createReference(offset);
		}

		/**
		 * Constructor to create a {@code SubList}. This constructor assumes a
		 * default children size since one is not provided. To customize this
		 * parameter, refer to one of the other constructors.
		 * 
		 * @param e
		 *            an element to add into the empty list
		 */
		public SubList(E e) {
		MAX_CHILDREN = DEFAULT_MAX_CHILDREN;
		MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN) / 3);

		contents = new ArrayList<ManagedReference<ManagedObject>>();
		append(e);
		size = contents.size();
		Offset offset = new Offset();
		offsetRef = AppContext.getDataManager().createReference(offset);
		}

		/**
		 * Sets the relative offset of a searched element. The search algorithm
		 * will set a value in the {@code Offset} object so that it is there
		 * when it exits the search loop and is ready to retrieve it.
		 * 
		 * @param offset
		 *            the offset of a specified element
		 */
		public void setOffset(int offset) {
		offsetRef.getForUpdate().set(offset);
		}

		/**
		 * Retrieves the offset previously supplied when searching.
		 * 
		 * @return the offset of an element that will soon be retrieved
		 */
		public int getOffset() {
		if (offsetRef != null) {
		return offsetRef.get().get();
		} else {
		return -1;
		}
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
		 * @return the element, if it exists, or null otherwise
		 */
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
		 * Retrieves the first (head) stored element in the {@code SubList}
		 * 
		 * @return the first element
		 */
		public E getFirst() {
		return get(0);
		}

		/**
		 * Retrieves the last (tail) stored element.
		 * 
		 * @return the last element in the {@code SubList}
		 */
		public E getLast() {
		if (contents != null) {
		return get(contents.size() - 1);
		} else {
		return null;
		}
		}

		/**
		 * Removes all elements in the sublist and removes the
		 * {@code SubList<E>} object from the Data Store.
		 */
		public void clear() {
		DataManager dm = AppContext.getDataManager();
		Iterator<ManagedReference<ManagedObject>> iter = contents.iterator();

		// Delete all the elements within the sub list
		while (iter.hasNext()) {
		dm.removeObject(iter.next().get());
		}
		contents.clear();

		// Remove the offset reference
		if (offsetRef != null) {
		dm.removeObject(offsetRef.get());
		}
		}

		/**
		 * Sets the value at the index provided. The index is not an absolute
		 * index; rather, it is relative to the current list. If the index does
		 * not correspond to a valid index in the underlying list, an
		 * {@code IndexOutOfBoundsException} will be thrown.
		 * 
		 * @param index
		 *            the index to add the element
		 * @param obj
		 *            the element to be added
		 * @return the old element that was replaced
		 * @throws IndexOutOfBoundsException
		 *             if the index is outside the range of the underlying list
		 */
		public E set(int index, Object obj) {
		ManagedReference<ManagedObject> ref = null;
		ManagedReference<ManagedObject> old = null;
		Object oldObj = null;

		// Wrap the element as an Element if is not already
		// a ManagedObject
		if (obj instanceof ManagedObject) {
		ref = AppContext.getDataManager().createReference((ManagedObject) obj);
		old = contents.set(index, ref);
		oldObj = ((Element<E>) old.get()).getValue();
		} else {
		Element<E> element = new Element<E>((E) obj);
		ref = AppContext.getDataManager().createReference(
				(ManagedObject) element);
		old = contents.set(index, ref);
		oldObj = old.get();
		}

		// Delete the old value from data store
		AppContext.getDataManager().removeObject((ManagedObject) old.get());
		return (E) oldObj;
		}

		/**
		 * Appends the supplied argument to the list. It will throw a
		 * {@code NullPointerException} if the supplied object is null.
		 * 
		 * @param e
		 *            the element to add to append
		 * @return whether the operation was successful; true if so, false
		 *         otherwise
		 */
		public boolean append(E e) {
		boolean result = false;
		if (e == null) {
		throw new NullPointerException("The appended object cannot be null");
		}

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
		 *            the element whose last index is to be found
		 * @return the index of the element, or -1 if it does not exist
		 */
		public int lastIndexOf(Object o) {
		Iterator<ManagedReference<ManagedObject>> iter = contents.iterator();
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
		obj = ((Element<E>) obj).getValue();
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
		 *            the index to add the new element.
		 * @param e
		 *            the object which is to be inserted at the specified
		 *            {@code index}
		 * @throws IndexOutOfBoundsException
		 *             if the supplied index is outside of the range of the
		 *             underlying list
		 */
		public void insert(int index, E e) {
		if (index < 0) {
		throw new IndexOutOfBoundsException(
				"Supplied index cannot be less than 0");
		}
		if (e == null) {
		throw new IllegalArgumentException("Cannot insert null");
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
		 *            the element to potentially wrap in an {@code Element}
		 * @return a reference to the wrapped element, ready to be stored into a
		 *         {@code SubList}
		 */
		private ManagedReference<ManagedObject> createRefForAdd(E e) {
		ManagedReference<ManagedObject> ref = null;

		// Determine if we need to wrap the parameter or not.
		if (e instanceof ManagedObject && e instanceof Serializable) {
		ref = AppContext.getDataManager().createReference((ManagedObject) e);
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
		 *            the element whose index is to be searched
		 * @return the first index of the supplied element, or -1 if it does not
		 *         exist in the list
		 */
		public int indexOf(Object o) {
		Iterator<ManagedReference<ManagedObject>> iter = contents.iterator();
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
		obj = ((Element<E>) obj).getValue();
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
		 * {@code IndexOutOfBoundsException} if the index does not exist in the
		 * underlying list.
		 * 
		 * @param index
		 *            the index to remove
		 * @return the object removed from the index
		 * @throws IndexOutOfBoundsException
		 *             if the index is outside of the range of the underlying
		 *             list
		 */
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
		AppContext.getDataManager().removeObject(removed.getForUpdate());
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
		 *            the element to remove from the list
		 * @return whether the operation was successful; true if so, false
		 *         otherwise
		 */
		public boolean remove(Object obj) {
		Iterator<ManagedReference<ManagedObject>> iter = contents.iterator();
		ManagedReference<ManagedObject> current = null;
		Object object = null;
		boolean success = false;

		// go through all the elements in this collection (a sublist)
		while (iter.hasNext()) {
		current = iter.next();
		object = current.get();
		if (object instanceof Element) {
		object = ((Element<E>) object).getValue();
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

		/**
		 * Removes the last element from the list.
		 * 
		 * @return the removed element that had existed at the end of the list
		 */
		public Object removeLast() {
		return remove(contents.size() - 1);
		}

	}

	/**
	 * An interface which unifies the concept of a {@code ListNode} and
	 * {@code TreeNode} as elements within a {@code ScalableList}. This
	 * interface avoids cast warnings when the identity of a given element is
	 * not immediately known.
	 * 
	 * @param <E>
	 *            the type of element stored in the {@code ScalableList}
	 */
	static interface Node<E> {

		/**
		 * Retrieves the node's parent.
		 * 
		 * @return the parent, or null if none exists.
		 */
		public TreeNode<E> getParent();

		/**
		 * Removes the {@code ManagedObject} from the Data Store by recursively
		 * calling {@code clear()} on its children.
		 */
		public void clear();

		/**
		 * The size of the node; that is, the sum of the sizes of its immediate
		 * children.
		 * 
		 * @return the size of this node.
		 */
		public int size();
	}
}
