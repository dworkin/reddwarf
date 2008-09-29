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

/*
 * TODO: 	- Iterators to check for dirty nodes
 * 			- Iterators to stop during iterations
 */

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.util.RobTest.ListNode;
import com.sun.sgs.app.util.RobTest.TreeNode;

/**
 *  This class represents an {@code AbstractCollection} which supports 
 *  a concurrent and scalable behavior. This data structure builds 
 *  upon the AbstractList class by implementing methods specific to 
 *  concurrent and scalable operations.<p>
 *  
 *  The class achieves scalability and concurrency by partitioning 
 *  an ordinary list into a number of smaller lists contained in 
 *  {@code ListNode} objects, and joining the nodes in a tree format. 
 *  This implementation bears similarity to a skip-list in 
 *  that access to arbitrary elements occurs through initially
 *  large jumps, and then through a finer iteration of the
 *  contained list. To allow for this behaviour, each {@code ListNode} 
 *  holds a subset of the contents so that changes in the entries 
 *  need not propagate to all elements at once. In fact, each 
 *  {@code ListNode} only holds onto the size of its sublist 
 *  (its children) and not a cumulative total of all its previous
 *  siblings. This enables intermediate changes to have no effect 
 *  on neighbouring nodes, such as re-indexing. <p>
 *  
 *  The {@code branchingFactor} is a user-defined parameter which
 *  describes how the underlying tree is organized. A large
 *  {@code branchingFactor} means that each node in the tree
 *  contains a large number of children, providing for a shallower
 *  tree, but many more sibling traversals. Concurrency is somewhat
 *  compromised since parent nodes containing a large number of
 *  children are locked during modification. A smaller branching
 *  factor reduces the sibling traversals, but makes the tree
 *  deeper, somewhat affecting performance during split operations.
 *  Depending on the use of the list, it may be desirable to have
 *  a large {@code branchingFactor}, such as for improved scalability,
 *  or a smaller {@code branchingFactor}, such as for better
 *  concurrency. <p>
 *  
 *  When the nodes require modification, iterators are responsible
 *  for interpreting the node sizes and dealing with changes. 
 *  Iterators are responsible for determining attributes of the
 *  list, like size, in roughly O(1) time.
 *  As mentioned earlier, performing splits and removing unused nodes
 *  can be somewhat expensive, depending on the values set for
 *  the {@code branchingFactor} and {@code clusterSize}. However, it is 
 *  seen that the benefits provided by the partitioning of the 
 *  list that enable concurrency outweigh the performance hit for 
 *  these operations. <p>
 *  
 *  When an element is requested from the data structure, the
 *  iterator's position is not affected by modifications to the
 *  list prior to its current location. However, if modifications
 *  happen between the current position and the destination, they
 *  will be involved in the iteration. Therefore, in the event
 *  that such a modification takes place, this
 *  implementation does not guarantee that the element at the
 *  specified index will be the one accessed.
 */
public class ScalableList<E> 
extends AbstractCollection<E>
implements 	ManagedObject, Serializable {

	public static final long serialVersionUID = 1L;
	
	/**
	 * The top node in the tree
	 */
	private ManagedReference<TreeNode<E>> root;
	
	/**
	 * A reference to the head node of the list.
	 */
	private ManagedReference<DummyConnector<E>> headRef;
	/**
	 * A reference to the tail of the list. This
	 * makes appending to the list a constant-time
	 * operation.
	 */
	private ManagedReference<DummyConnector<E>> tailRef;
	
	/**
	 * The maximum size of the intermediate lists.
	 * This number should be small enough to enable
	 * concurrency but large enough to contain a
	 * reasonable number of nodes.
	 */
	private int clusterSize = 10;
	
	/**
	 * The maximum number of children contained in a TreeNode<E>;
	 * this parameter is passed to the TreeNode<E> during
	 * instantiation.
	 */
	private int branchingFactor = 5;
	
	
	/*
	 * 	//////////////////////////////
			IMPLEMENTATION	
		/////////////////////////////
	*/
	
	/**
	 * Constructor which creates a {@code ScalableList} object
	 * with the resolution action supplied as a parameter.
	 * @param resolution Whether the {@code ScalableList} should
	 * perform resolution. True if so, and false otherwise. 
	 */
	public ScalableList(){
		root = null;
		headRef = AppContext.getDataManager().createReference(new DummyConnector<E>());
		tailRef = AppContext.getDataManager().createReference(new DummyConnector<E>());
	}
	
	/**
	 * Constructor which creates a {@code ScalableList} object
	 * with the resolution and cluster size supplied as a parameter.
	 * The clusterSize can be any integer larger than 0, however
	 * the {@code branchingFactor} must be larger than 1 so that 
	 * the tree can be meaningful. Otherwise, it would only be 
	 * able to grow to a maximum size of {@code clusterSize} 
	 * since branching could not introduce any additional children. 
	 * @param branchingFactor The number of children each node
	 * should have. A {@code branchingFactor} of 2 means that the
	 * list structure incorporates a binary tree.
	 * @param clusterSize The size of each partitioned list. This
	 * value must be a positive integer (larger than 0).
	 */
	public ScalableList(int branchingFactor, int clusterSize){
		headRef = AppContext.getDataManager().createReference(new DummyConnector<E>());
		tailRef = AppContext.getDataManager().createReference(new DummyConnector<E>());
		root = null;
		
		if (clusterSize < 1){
			throw new IllegalArgumentException("Cluster size must "+
					"be an integer larger than 0");
		}
		if (branchingFactor < 2){
			throw new IllegalArgumentException("Max child size must "+
					"be an integer larger than 1");
		}
		this.clusterSize = clusterSize; 
	}
	
	
	/**
	 * Appends an {@code Object} to the {@code ScalableList} by first
	 * creating an {@code Object} object.
	 * @param obj The {@code Object} to be appended to the list.
	 */
	public boolean add(E e){
		boolean result = false;
		
		if (e == null){
			throw new IllegalArgumentException("Element cannot be null");
		}
		
		// first item to add into the list
		if (getTail() == null){
			return addFirstEntry(e);
		}
		// otherwise, add it at the end since no index
		// and propagate change to parents
		result = getTail().append(e);
		
		// check location of headTreeNodeRef, in case the
		// append introduced a new TreeNode<E> root during a
		// split. The headTreeNodeRef should be the root's 
		// first child.
		updateTreeNodeRefs();
		
		return result;
	}
	
	
	/**
	 * Converts the {@code Object} into an {@code Object} and
	 * prepends it to the {@code ScalableList}. That is, the
	 * {@code Object} is added to the front of the list.
	 * @param obj The {@code Object} to be added to the beginning 
	 * of the list.
	 */
	public void prepend(E e){
		// first item to add into the list
		if (headRef == null || getHead() == null){
			addFirstEntry(e);
			return;
		}
		// otherwise, add it to the beginning
		getHead().prepend(e);
	}
	
	
	/**
	 * Adds the supplied {@code Object} to a specified location
	 * in the list, provided by the index. If the index is
	 * out of bounds, then an {@code IndexOutOfBoundsException}
	 * is thrown. 
	 * @param index The location to add the {@code Object}.
	 * @param obj The {@code Object} to add.
	 */
	public void add(int index, E e){
		isValidIndex(index);
		if (e == null){
			throw new IllegalArgumentException("Element cannot be null");
		} else if (getHead() == null && index == 0){
			addFirstEntry(e);
			return;
		} else if (getHead() == null){
			throw new IllegalArgumentException("Cannot add to index "+
					index + " on an empty list");
		}
		
		// otherwise, add it to the specified index.
		// This requires a search of the list nodes.
		ListNode<E> n = getNode(index);
		n.insert(n.getSubList().getOffset(), e);
		
		updateTreeNodeRefs();
	}
	
	/**
	 * Adds all the elements from the {@code Collection} into the
	 * list at the specified index.
	 * @param index The index which to start adding the elements.
	 * @param c the collection of elements to add.
	 * @return True if the operation occurred successfully, and
	 * false otherwise.
	 */
	public boolean addAll(int index, Collection<E> c){
		Iterator<E> iter = c.iterator();
		while (iter.hasNext()){
			add(index, iter.next());
			index++;
		}
		return true;
	}
	
	/**
	 * Removes all entries from the list.
	 */
	public void clear(){
		if (headRef == null){
			return;
		}
		
		// Schedule asynchronous task here which will delete the list.
		AppContext.getTaskManager().scheduleTask(
				new AsynchronousClearTask<E>(root));
		
		// Create a new ListNode<E> and link everything to it.
		TreeNode<E> t = new TreeNode<E>(this, null, branchingFactor, clusterSize);
		headRef = AppContext.getDataManager().createReference(
				new DummyConnector<E>());
		tailRef = AppContext.getDataManager().createReference(
				new DummyConnector<E>());
		
	}
	
	/**
	 * Locates the first index where the element is located by
	 * traversing the list from beginning to end.
	 * @param o The object to find in the list.
	 * @return The first index where the element is located, or -1 if
	 * not found.
	 */
	public int indexOf(Object o){
		int listIndex = 0;
		ScalableListNodeIterator<E> iter = 
			new ScalableListNodeIterator<E>(getHead());
		ListNode<E> n;
		int index = -1;
		
		while (iter.hasNext()){
			n = (ListNode<E>) iter.next();
			listIndex += n.size();
			index = n.getSubList().indexOf(o);
			
			if (index != -1){
				break;
			}
		}
		return listIndex + index;
	}
	
	/**
	 * Locates the last index where the element is located.
	 * @param o the {@code Object} to locate in the list
	 * @return the index where it is last located
	 */
	public int lastIndexOf(E e){
		int listIndex = 0;
		int absIndex = -1;
		int index = -1;
		ScalableListNodeIterator<E> iter = 
			new ScalableListNodeIterator<E>(getHead());
		ListNode<E> n = null;
		
		// For every list node encountered, check for an
		// instance of the supplied object
		while (iter.hasNext()){
			n = iter.next();
			listIndex += n.size();
			index = n.getSubList().lastIndexOf(e);
			
			// Save the most recent occurrence of a matching index
			// but keep searching in case we find another in another
			// node.
			if (index != -1){
				absIndex = listIndex + index;
			}
		}
		return absIndex;
	}
	
	/**
	 * Determines if the provided index is valid; that is, whether
	 * it is a natural number and thus not negative. If not, then
	 * an {@code IndexOutOfBoundsException} is thrown.
	 * @param index
	 */
	private void isValidIndex(int index){
		if (index < 0){
			throw new IndexOutOfBoundsException("Index cannot be less than 0; was " + index);
		}
	}
	
	/**
	 * Removes the entry at the supplied index from the list.
	 * If the index is invalid, then an 
	 * {@code IndexOutOfBoundsException} is thrown.
	 * @param index The index of the element to remove.
	 * @return the removed element previously at the specified index.
	 */
	public Object remove(int index){
		isValidIndex(index);
		ListNode<E> n = getNode(index);
		if (n == null){
			return null;
		}

		// Performs any relinking in case the removed
		// ListNode<E> was the head or tail
		Object obj = n.remove(n.getSubList().getOffset());
		updateTreeNodeRefs();
		relinkIfNecessary(n);		
		
		return obj;
	}
	
	/**
	 * Updates the ScalableList's head and/or tail in the 
	 * event that it was removed.
	 * @param n
	 */
	private void relinkIfNecessary(ListNode<E> n){
		if (n == null || getRoot() == null){
			return;
		}
		// Store values before they are deleted
		ListNode<E> next = n.getNext();
		ListNode<E> prev = n.getPrev();
		
		// Check whether we need to update the head or tail.
		if (getHead() == null){
			// Check if we need to search in another TreeNode<E>
			if (next != null){
				setHead(next);
			} else {
				setHead(null);
			}
		} 
		// Update the tail 
		if (getTail() == null){
			// Check if we need to search in another TreeNode<E>
			if (prev != null){
				setTail(prev);
			} else {
				setTail(null);
			}
		}
	}
	
	/**
	 * Retrieves the head {@code ListNode}. In the
	 * event that one cannot be located, it
	 * gracefully returns null.
	 * @return the head {@code ListNode} if it
	 * exists, or null otherwise.
	 */
	private ListNode<E> getHead(){
		ListNode<E> head;
		try {
			head = headRef.get().getRefAsListNode();
		} catch (ObjectNotFoundException onfe){
			// This is expected if the node has been removed.
			head = null;
		}
		return head;
	}
	
	/**
	 * Retrieves the tail {@code ListNode}. In the
	 * event that one cannot be located, it
	 * gracefully returns null.
	 * @return the tail {@code ListNode} if it exists,
	 * or null otherwise.
	 */
	private ListNode<E> getTail(){
		ListNode<E> tail;
		try {
			tail = tailRef.get().getRefAsListNode();
		} catch (ObjectNotFoundException onfe){
			// This is expected if the node has been removed.
			tail = null;
		}
		return tail;
	}
	
	private void setTail(ListNode<E> newTail){
		tailRef.get().setRef(newTail); 
	}
	
	private void setHead(ListNode<E> newHead){
		headRef.get().setRef(newHead); 
	}
	
	/**
	 * Locates an element with the supplied index and replaces its
	 * value with the provided {@code Object}.
	 * @param index the index of the {@code Object} to modify
	 * @param obj the {@code Object} to replace with
	 * @return the {@code Object} which previously existed in the list
	 */
	public Object set(int index, E obj){
		Object old = null;
		if (obj == null){
			throw new NullPointerException(
					"Value for set operation cannot be null");
		}
		SubList<E> n = getNode(index).getSubList();
		old = n.set(n.getOffset(), obj);
		return old;
	}
	
	
	/**
	 * Removes the last element from the List. If the element was the
	 * last in the collection, then we leave the ListNode<E> empty in
	 * anticipation of soon writing to it again (appending).
	 * @return The {@code Object} which was removed.
	 */
	public Object removeLast(){
		ListNode<E> tail = getTail(); 
		if (tail == null){
			return null;
		}
		Object obj = tail.remove(tail.size()-1);
		relinkIfNecessary(tail);
		
		return obj; 
	}
	
	/**
	 * Removes the first element from the list. This method calls
	 * {@code remove(0)}, which equivalently removes the element 
	 * at index 0.
	 * @return The first element from the list
	 */
	public Object removeFirst(){
		return remove(0);
	}
	
	
	/**
	 * Retrieves the element at the specified index, without
	 * intention of modification. This method is best used
	 * to read the value at the specified index. To change the
	 * value, please see the {@code set(int, Object)} method.
	 * @param index the index of the element to retrieve.
	 * @return the element at the index.
	 */
	public E get(int index){
		
		// Iterate through using the count. Use a get()
		// iterator because we are not modifying anything; hence, false.
		SubList<E> n = getNode(index).getSubList();
		if (n == null){
			return null;
		}
		return n.get(n.getOffset());
	}
	
	/**
	 * Returns the last (tail) element of the list, for
	 * reading purposes.
	 * @return the tail element of the list.
	 */
	public E getLast(){
		ListNode<E> ln = getTail();
		if (ln == null){
			return null;
		}
		SubList<E> n = ln.getSubList();
		if (n == null){
			return null;
		}
		return n.getLast();
	}
	
	/**
	 * Returns the first (head) element of the list,
	 * for reading purposes
	 * @return the head element of the list.
	 */
	public E getFirst(){
		ListNode<E> ln = getHead();
		SubList<E> sl = ln.getSubList();
		if (sl == null){
			return null;
		}
		return sl.getFirst();
	}
	

	
	/**
	 * Retrieves the {@code Iterator} for the list.
	 */
	public Iterator<E> iterator(){
		ListNode<E> ln = getHead();
		if (ln != null){
			return new ScalableListIterator<E>(ln);
		} else {
			return null;
		}
	}
	

	
	/**
	 * Determines whether two instances are equal; that is,
	 * if they point to the same underlying object.
	 * @param obj the {@code Object} to check for equality
	 * @return true if the {@code Object}s are equal; false otherwise.
	 */
	public boolean equals(Object obj){
		if (obj == null || !(obj instanceof ScalableList)){
			return false;
		}
		
		ScalableList<E> list = (ScalableList<E>) obj;
		return AppContext.getDataManager().createReference(this)
				.equals(AppContext.getDataManager().createReference(list));
	}
	
	/**
	 * Removes the first encountered element from the list.
	 * @param obj the element to remove from the list.
	 * @return whether the element was removed. 
	 */
	public boolean remove(Object obj){
		ScalableListNodeIterator<E> iter = 
			new ScalableListNodeIterator<E>(getHead());
		ListNode<E> n = null;
		boolean removed = false;
		
		// Find and remove the object in the ListNode<E> that contains it
		while (iter.hasNext()){
			n = iter.next();
			removed = n.remove(obj);
			if (removed){
				break;
			}
		}
		
		// Relink neighboring ListNodes in case this one was
		// removed due to being empty.
		relinkIfNecessary(n);
		
		return removed;
	}

	/**
	 * Retrieves the root {@code TreeNode} if it exists
	 * or null otherwise.
	 * @return the root node, or null if it does not
	 * exist.
	 */
	public TreeNode<E> getRoot(){
		if (this.root == null){
			return null;
		}
		TreeNode<E> root;
		try {
			root = this.root.get();
		} catch (ObjectNotFoundException onfe){
			root = null;
		}
		return root;
	}
	
	public void setRoot(TreeNode<E> newRoot){
		if (root == null){
			root = null;
		} else {
			root = AppContext.getDataManager()
			.createReference(newRoot);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isEmpty(){
		return (this.size() == 0);
	}
	
	
	/**
	 * Performs the search for the desired ListNode<E>.
	 * This method implicitly updates the offset value
	 * for the particular node we are finding, meaning
	 * when we locate the ListNode<E>, the OffsetNode
	 * child will be updated to represent the offset
	 * of the index we are searching for.
	 * @param index The absolute index in the entire
	 * list to search for the entry.
	 * @return The {@code ListNode<E>} which contains the
	 * element at the specified {@code index}.
	 */
	private ListNode<E> getNode(int index){
		// Recursive method to eventually return ListNode<E>
		// containing the desired index.
		return search(getRoot(), 0, index + 1);
	}
	
	
	
	/**
	 * Traverses the tree (recursively) in search of the 
	 * ListNode<E> which contains the index provided. If
	 * no ListNode<E> can be found, then null is returned.
	 * @param tn the node from which to start searching.
	 * @param currentValue the current index value at
	 * the beginning of the search.
	 * @param destIndex the index which we want to reach.
	 * @return
	 */
	private ListNode<E> search(
			ManagedObject tn, int currentValue, int destIndex){
		if (tn == null){
			throw new NullPointerException("Root search node cannot be null");
		}
		
		if (tn instanceof TreeNode){
			TreeNode<E> t = (TreeNode<E>) tn;
			
			// iterate through siblings
			while ((currentValue += t.size) < destIndex){
				t = t.next();
				
				// The specified index was too large; hence
				// throw an IndexOutOfBoundsException
				if (t == null){
					throw new IndexOutOfBoundsException("The "+
							"index " + destIndex + " is out of range.");
				}
			}
			currentValue -= t.size();
			return search(t.getChild(), currentValue, destIndex);
			
			
		} else if (tn instanceof ListNode){
			ListNode<E> n = (ListNode<E>)tn;
			
			while ((currentValue += n.size()) < destIndex){
				n = n.getNext();
				
				// The specified index was too large; hence
				// throw an IndexOutOfBoundsException
				if (n == null){
					throw new IndexOutOfBoundsException("The "+
							"index " + destIndex + " is out of range.");
				}
			}
			currentValue -= n.size();
			n.getSubList().setOffset(destIndex - currentValue - 1);
			return n;
			
		} else {
			throw new IllegalArgumentException("The instance of the ManagedObject "+
					"argument is not supported for searching");
		}
	}
	
	
	/**
	 * Adds the object as the first entry of the list when
	 * the list is not yet populated.
	 * @param obj
	 * @return
	 */
	private boolean addFirstEntry(E e){
		if (e == null){
			throw new IllegalArgumentException("Element cannot be null");
		}
		TreeNode<E> t = new TreeNode<E>(this, null, branchingFactor, clusterSize, e);
		DataManager dm = AppContext.getDataManager();
		root = dm.createReference(t);
		ListNode<E> n = (ListNode<E>) t.getChild();
		setHead(n);
		setTail(n);
		return true;
	}
	
	/**
	 * Retrieves the size of the list. The size of the
	 * list is the size of the root {@code TreeNode<E>} of
	 * the list because all of its children represent a
	 * count of their children.
	 * @return the number of elements of the list.
	 */
	public int size(){
		// This gives us a reference to the one and only
		// root TreeNode<E>, which parents the entire tree.
		// It should be noted that this object is not the 
		// same as the headTreeNodeRef, which returns the
		// first child of the root.
		int size = 0;
		if (getRoot() != null){
			size = getRoot().size();
		}
		return size;
	}
	
	
	/**
	 * Given the current head pointer, percolate up
	 * the tree and find the root.
	 * @return the root {@code TreeNode<E>} of the tree.
	 */
	private void updateTreeNodeRefs(){
		TreeNode<E> treeHead = getRoot();
		if (treeHead == null || treeHead.getParent() == null){
			return;
		}
		
		// Percolate up the tree until we are at the root
		// node whose parent is the root.
		while (treeHead.getParent() != null){
			treeHead = treeHead.getParent();
		}
		
		// Update the head to point to the new location
		// this should be the child of the root node
		// as the root is not useful for most list
		// operations.
		root = AppContext.getDataManager()
			.createReference(treeHead);
	}
	
	
	
	/*
	  	////////////////////////////////
			INLINE CLASSES
		///////////////////////////////
	*/
	
	
	static class DummyConnector<E>
	implements ManagedObject, Serializable {
		
		public static final long serialVersionUID = 3L;
		
		private ManagedReference<ManagedObject> ref = null;
		
		public DummyConnector(){
			ref = null;
		}
		
		public DummyConnector(ManagedObject nextRef){
			if (nextRef != null){
				ref = AppContext.getDataManager().createReference(nextRef);
			}
		}
		
		public void setRef(ManagedObject newRef){
			if (newRef != null){
				ref = AppContext.getDataManager().createReference(newRef);
			} else {
				ref = null;
			}
		}
		
		public TreeNode<E> getRefAsTreeNode(){
			if (ref == null){
				return null;
			}
			return (TreeNode<E>) ref.get();
		}
		
		public SubList<E> getRefAsSubList(){
			if (ref == null){
				return null;
			}
			return (SubList<E>) ref.get();
		}
		
		public ListNode<E> getRefAsListNode(){
			if (ref == null){
				return null;
			}
			return (ListNode<E>) ref.get();
		}
	}
	
	
	/**
	 * An object which organizes the {@code ListNode<E>}s
	 * in a tree structure. Each {@code TreeNode<E>} has
	 * a reference to either one {@code TreeNode<E>}
	 * or a {@code ListNode<E>}. The {@code TreeNode<E>} also
	 * has a reference to its sibling and its parent.<p>
	 * 
	 * The {@code TreeNode<E>} is intended to only track
	 * the size of its children and 
	 */
	static class TreeNode<E>
	implements ManagedObject, Serializable {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<TreeNode<E>> nextRef;
		private ManagedReference<TreeNode<E>> prevRef;
		private ManagedReference<ManagedObject> childRef;
		private ManagedReference<ManagedObject> tailRef;
		private ManagedReference<TreeNode<E>> parentRef;
		
		private int size = 0;
		private int MAX_NUMBER_CHILDREN = 5;
		private int clusterSize = 0;
		private int childrenCount = 0;
		private ManagedReference<ScalableList<E>> owner;
		
		public static final byte DECREMENT_SIZE = 0;
		public static final byte INCREMENT_SIZE = 1;
		public static final byte INCREMENT_NUM_CHILDREN = 2;
		public static final byte DECREMENT_NUM_CHILDREN = 3;
		public static final byte DECREMENT_CHILDREN_AND_SIZE = 4;
		
		public void setHead(ListNode<E> ref){
			if (ref != null){
				childRef = AppContext.getDataManager().createReference((ManagedObject) ref);
			}
		}
		
		public TreeNode(){
			size = 0;
			nextRef = null;
			childRef = null;
			parentRef = null;
		}
		
		
		/**
		 * Constructor which is called on a split. This is used to create
		 * a new sibling and accepts a TreeNode<E> argument which represents
		 * the new child it is to possess.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param child
		 */
		private TreeNode(ScalableList<E> list, TreeNode<E> parent, int maxNumChildren, 
				int clusterSize, ListNode<E> child, int numberChildren, int size){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;

			DataManager dm = AppContext.getDataManager();
			if (child != null){
				childRef = dm.createReference((ManagedObject) child);
				
				// The tail element is the last linked node in the list
				ListNode<E> n = (ListNode<E>) childRef.get();
				while (n.getNext() != null){
					n = (ListNode<E>) n.getNext();
				}
				tailRef = dm.createReference((ManagedObject) n);
			}
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
			childrenCount = numberChildren;
			this.size = size;
			owner = AppContext.getDataManager().createReference(list);
		}
		
		
		/**
		 * Constructor which is called on a split. This is used to create
		 * a new sibling and accepts a TreeNode<E> argument which represents
		 * the new child it is to possess.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param child
		 */
		private TreeNode(ScalableList<E> list, TreeNode<E> parent, int maxNumChildren, 
				int clusterSize, TreeNode<E> child, int numberChildren, int size){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;

			DataManager dm = AppContext.getDataManager();
			if (child != null){
				childRef = dm.createReference((ManagedObject) child);
				
				// The tail element is the last linked node in the list
				TreeNode<E> n = (TreeNode<E>) getChild();
				while (n.next() != null){
					n = (TreeNode<E>) n.next();
				}
				tailRef = dm.createReference((ManagedObject) n);
			}
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
			childrenCount = numberChildren;
			this.size = size;
			owner = AppContext.getDataManager().createReference(list);
		}
		
		/**
		 * Create a new TreeNode<E> on account of a new leaf ({@code ListNode<E>}) 
		 * being created.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param obj
		 */
		public TreeNode(ScalableList<E> list, TreeNode<E> parent, int maxNumChildren, int clusterSize, E e){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			if (e == null){
				throw new NullPointerException("Argument suppled to List is null");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;
			ListNode<E> n = new ListNode<E>(this, clusterSize, e);
			size = n.size();
			childrenCount = 1;
			DataManager dm = AppContext.getDataManager();
			childRef = dm.createReference((ManagedObject) n);
			if (parent != null){
				parentRef = dm.createReference(parent);
			} else {
				parentRef = null;
			}
			tailRef = dm.createReference((ManagedObject) n);
			owner = AppContext.getDataManager().createReference(list);
		}
		
		public TreeNode(ScalableList<E> list, TreeNode<E> parent, int maxNumChildren, int clusterSize){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;
			ListNode<E> n = new ListNode<E>(this, clusterSize);
			size = n.size();
			DataManager dm = AppContext.getDataManager();
			childRef= dm.createReference((ManagedObject) n);
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
			tailRef = dm.createReference((ManagedObject) n);
			owner = AppContext.getDataManager().createReference(list);
		}
		
		public TreeNode<E> prev(){
			TreeNode<E> prev;
			if (prevRef == null){
				return null;
			}
			try {
				prev = prevRef.get();
			} catch (ObjectNotFoundException onfe) {
				prev = null;
			}
			return prev;
		}
		public void setPrev(TreeNode<E> ref){
			if (ref != null){
				prevRef = AppContext.getDataManager().createReference(ref);
			}
		}
		public TreeNode<E> next(){
			TreeNode<E> next;
			if (nextRef == null){
				return null;
			}
			try {
				next = nextRef.get();
			} catch (ObjectNotFoundException onfe) {
				next = null;
			}
			return next;
		}
		public ManagedObject getChild(){
			ManagedObject child;
			if (childRef == null){
				return null;
			}
			try {
				child = childRef.get();
			} catch (ObjectNotFoundException onfe){
				child = null;
			}
			return child;
		}
		public void setChild(ManagedObject child){
			if (child != null){
				childRef = AppContext.getDataManager().createReference(child);
			}
		}
		public int size(){
			return size;
		}
		public void setTail(ManagedObject ref){
			if (ref != null){
				tailRef = AppContext.getDataManager().createReference(ref);
			}
		}
		public ManagedObject getTail(){
			ManagedObject tail;
			if (tailRef == null){
				return null;
			}
			try {
				tail = tailRef.get();
			} catch (ObjectNotFoundException onfe) {
				tail = null;
			}
			return tail;
		}
		public void increment(){
			size++;
		}
		public void decrement(){
			size--;
		}
		public void incrementNumChildren(){
			childrenCount++;
		}
		public void decrementNumChildren(){
			childrenCount--;
		}
		public int getChildCount(){
			return childrenCount;
		}
		public TreeNode<E> getParent(){
			TreeNode<E> parent;
			if (parentRef == null){
				return null;
			}
			try {
				parent =  parentRef.get();
			} catch (ObjectNotFoundException onfe){
				parent = null;
			}
			return parent;
		}
		public void setNext(TreeNode<E> t){
			if (t != null){
				nextRef = AppContext.getDataManager().createReference(t);
			}
		}
		
		/**
		 * Unlinks itself from the tree. This can be considered
		 * as a TreeNode<E> remove since 
		 * @return
		 */
		public TreeNode<E> remove(){
			
			// We may have to re-link siblings
			if (prevRef != null){
				if (nextRef != null){
					this.prev().setNext(this.next());
					this.next().setPrev(this.prev());
				} else {
					// relink tail
					this.prev().setNext(null);
				}
			} else if (nextRef != null){
				// relink head
				this.next().setPrev(null);
			}
			AppContext.getDataManager().removeObject(this);
			return this;
		}

		
		/**
		 * Determines where to split a list of
		 * children based on the list size.
		 * @param numberOfChildren the number of children
		 * to split
		 * @return the index corresponding to the location
		 * where to split the linked list.
		 */
		private int calculateSplitSize(int numberOfChildren){
			return Math.round(numberOfChildren/2);
		}
		
		/**
		 * This method walks up the tree and performs any
		 * {@code TreeNode<E>} splits if the children sizes
		 * have been exceeded.
		 */
		public boolean performSplitIfNecessary(){
			boolean generatedNewParent = false;
			TreeNode<E> newNode = null;
			ManagedObject tmp = (ManagedObject) this.getChild();

			
			// Check if we need to split, and if so, do it based
			// on the type of element contained (ListNode<E> or TreeNode<E>)
			if (childrenCount > MAX_NUMBER_CHILDREN){
				generatedNewParent = generateParentIfNecessary(this);
				
				// Perform split by creating and linking new sibling
				newNode = createAndLinkSibling(tmp, childrenCount);
				childrenCount = childrenCount - calculateSplitSize(childrenCount);
				
				// Link the new sibling to the tree
				newNode.setPrev(this);
				newNode.setNext(this.next());
				this.setNext(newNode);
				if (newNode.next() != null){
					newNode.next().setPrev(newNode);
				}
				
				// Once finished, propagate split to parent; 
				// it will decide if further splitting is necessary.
				// If the parent ref is null, then we have reached
				// the root.
				if (getParent() != null){
					getParent().performSplitIfNecessary();
				} 
			}
			
			// If there is no parent, then this is the
			// new root node; update the list with this info.
			if (getParent() == null){
				owner.get().setRoot(this);
			}
			return generatedNewParent;
		}
		
		/**
		 * Determines the nature of the sibling to be created. The
		 * Node created will always be a TreeNode<E>, but the type of
		 * child depends on whether it parents ListNodes, or other
		 * TreeNodes.
		 * @param child the {@code ManagedObject} which needs to
		 * be split and placed under a new parent.
		 * @param prev the element previous to the current element
		 * @param halfway a value representing an index approximately
		 * half the length of the contents
		 * @return the {@TreeNode<E>} that not represents a sibling
		 * which is connected to the tree
		 */
		private TreeNode<E> createAndLinkSibling(
				ManagedObject child, int numberOfChildren){
			ManagedObject prev = null;
			TreeNode<E> newNode = null;
			int halfway = calculateSplitSize(numberOfChildren);
			int size = this.size;
			
			// Determine what kind of sibling to make, since a TreeNode<E>'s
			// child can be either a ListNode<E> or a TreeNode<E>
			if (child instanceof ListNode){
				
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = child;
					child = (ManagedObject) ((ListNode<E>)child).getNext();
					size -= ((ListNode<E>) prev).size();
				}
				
				newNode = new TreeNode<E>(owner.get(),
										this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(ListNode<E>) child,
										numberOfChildren - halfway,
										size);
				// Update parent references to new node for 
				// all subsequent nodes
				((ListNode<E>)child).setParent(newNode);
				while (((ListNode<E>) child).getNext() != null){
					child = ((ListNode<E>) child).getNext();
					((ListNode<E>) child).setParent(newNode);
				}
				
			} else if (child instanceof TreeNode){
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = child;
					child = ((TreeNode<E>)child).next();
					size -= ((TreeNode<E>) prev).size();
				}
				newNode = new TreeNode<E>(owner.get(),
										this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(TreeNode<E>) child,
										numberOfChildren - halfway,
										size);
				// Update parent references to new node
				((TreeNode<E>)child).setParent(newNode);
				while (((TreeNode<E>) child).next() != null){
					child = ((TreeNode<E>) child).next();
					((TreeNode<E>) child).setParent(newNode);
				}
				
			} else {
				throw new IllegalStateException("Attempting to split a node that is neither "+
						"a TreeNode<E> or ListNode<E>");
			}
			getParent().propagateChanges(getParent(), TreeNode.INCREMENT_NUM_CHILDREN);
			this.setTail(prev);			
			return newNode;
		}
		

		/**
		 * This method creates a parent above the provided node so that
		 * any new siblings resulting from a split can be joined to
		 * the new parent. The decision to create a new parent depends
		 * on whether the supplied node is an only-child; only these
		 * nodes are orphaned while siblings have parents assigned to them.
		 * @param t the {@code TreeNode<E>} to check whether it requires
		 * a parent.
		 */
		private boolean generateParentIfNecessary(TreeNode<E> t){
			boolean result = false;
			
			// If this is the currently existing root, make a new one
			// and set the supplied node as the child
			if (t.prev() == null){
				TreeNode<E> grandparent = 
					new TreeNode<E>(
							owner.get(), null, MAX_NUMBER_CHILDREN, 
							clusterSize, t, 1, t.size());
				t.setParent(grandparent);
				
				// The list's pointer to the parent will be
				// updated when a traversal is issued.
				result = true;
			}
			return result;
		}
		
		public void setParent(TreeNode<E> parent){
			if (parent != null){
				parentRef = AppContext.getDataManager().createReference(parent);
			}
		}
		
		/**
		 * Removes itself from the data store and recursively
		 * removes its children. 
		 */
		public void clear(){
			DataManager dm = AppContext.getDataManager();
			
			if (getChild() != null && getChild() instanceof TreeNode){
				TreeNode<E> current = (TreeNode<E>) getChild();
				TreeNode<E> next = current.next();
				
				while (current != null){
					current.clear();
					dm.removeObject(current);
					current = next;
					next = next.next();
				}
				
			} else if (getChild() != null && getChild() instanceof ListNode) {
				ListNode<E> current = (ListNode<E>) getChild();
				ListNode<E> next = current.getNext();
				
				while (current != null){
					current.clear();
					dm.removeObject(current);
					current = next;
					next = next.getNext();
				}
				
			}
			// unlink and self destruct
			
			dm.removeObject(this);
		}
	
		
		/**
		 * Propagates changes in the form of either an increment
		 * or decrement to the parents of the tree.
		 * @param current
		 * @param mode
		 */
		public void propagateChanges(TreeNode<E> current, byte mode){
			// No need to propagate changes if
			// this is the root node.
			if (current == null){
				return;
			}
			
			switch (mode){
			case TreeNode.INCREMENT_SIZE:
				current.increment();
				propagateChanges(current.getParent(), mode);	
				break;
			case TreeNode.DECREMENT_SIZE:
				current.decrement();
				if (size() == 0){
					propagateChanges(current.getParent(),
							TreeNode.DECREMENT_CHILDREN_AND_SIZE);
					break;
				}
				propagateChanges(current.getParent(), 
						mode);
				break;
			case TreeNode.INCREMENT_NUM_CHILDREN:
				current.incrementNumChildren();
				break;
			case TreeNode.DECREMENT_CHILDREN_AND_SIZE:
				current.decrement();
				current.decrementNumChildren();
				
				if (size() == 0){
					// remove this from the tree
					TreeNode<E> parent = current.getParent();
					remove();
					
					// since we are removing a TreeNode<E>,
					// check if parent needs to be removed
					// as well.
					propagateChanges(parent, mode);
				} else {
					propagateChanges(
						current.getParent(), TreeNode.DECREMENT_SIZE);
				}
				break;
			default:
				throw new IllegalArgumentException(
						"Supplied mode argument is not recognized; "+
						"expected "+TreeNode.INCREMENT_SIZE+" or "+
						TreeNode.DECREMENT_SIZE);
			}
		}
		
		
	}
	
	
	/**
	 * A task which iterates through the tree and removes
	 * all children. This task is instantiated when a
	 * clear() command is issed from the 
	 * {@code ScalableList}.
	 */
	private static class AsynchronousClearTask<E>
	implements Serializable, Task, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<TreeNode<E>> current;
		private ManagedReference<TreeNode<E>> next;
		
		public AsynchronousClearTask(ManagedReference<TreeNode<E>> head){
			if (head != null){
				current = AppContext.getDataManager().createReference(head.get());
			} else {
				current = null;
			}
		}
		
		public void run(){
			DataManager dm = AppContext.getDataManager();
			if (current == null){
				return;
			}
			// for each TreeNode<E>, delete its children and move on
			next = dm.createReference(current.get().next());
			current.get().clear();
			current = next;
			
			// If we have more nodes, reschedule this task;
			// otherwise we can remove it from data store as
			// it is a ManagedObject.
			if (current != null){
				AppContext.getDataManager().markForUpdate(this);
				AppContext.getTaskManager().scheduleTask(this);
			} else {
				dm.removeObject(this);
			}
		}
	}
	
	
	/**
	 * This class represents a stored entity of the list. It is
	 * a wrapper for any object that is stored in the list so
	 * that the list can refer to it by using a
	 * ManagedReference, rather than the actual object itself.
	 * This makes managing sublists less intensive as each
	 * reference to an underlying entity is a fixed size.
	 */
	static class Element<E>
	implements Serializable, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private E value;
		
		public Element(E e){
			value = e;
		}
		
		public E getValue(){
			return value;
		}
		
		public void setValue(E e){
			value = e;
		}
	}
	
	
	/**
	 * An {@code Offset} object is a simple container
	 * for the offset of a desired index. An
	 * {@code Offset} object is attached to a
	 * {@code SubList<E>} object and is updated whenever
	 * a {@code get()} operation is called so that
	 * the index of the item in the {@code SubList<E>}
	 * maps to the absolute index provided by the
	 * {@code ScalableList}
	 */
	static class Offset
	implements Serializable, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private int offset = -1;
		
		public void set(int offset){
			this.offset = offset;
		}
		
		public int get(){
			return offset;
		}
	}
	

	/**
	 * This iterator walks through the {@code ListNode<E>}s which
	 * parent the {@code SubList<E>}s. An iterator for this type
	 * of element is necessary to support the other element
	 * iterator as well as {@code indexOf()} operations.
	 */
	static class ScalableListNodeIterator<E>
	implements Serializable, Iterator<ListNode<E>> {
		
		private ListNode<E> current;
		private ListNode<E> head;
		
		public static final long serialVersionUID = 1L;
		
		public ScalableListNodeIterator (ListNode<E> head){
			current = null;
			this.head = head;
		}
		
		/**
		 * Two cases to check: the most common case is that the next
		 * {@code ListNode<E>} is a sibling of the current {@code ListNode<E>}.
		 * Otherwise, we have to locate a cousin, and return if it
		 * exists or not.
		 */
		public boolean hasNext(){
			// Check if there was an element to begin with
			if (current == null && head == null){
				return false;
				
			} else if (head != null){
				
				// Return the first element
				current = head;
				head = null;
			} else {
				
				// Setup the next element if it exists
				if (current.getNext() != null){
					current = current.getNext();
				} else {
					return false;
				}
			}
			return true;
		}
		
		
		/**
		 * Returns the next {@code ListNode<E>} in order
		 * from the list
		 */
		public ListNode<E> next(){
			if (current == null){
				throw new NoSuchElementException("There is no next element");
			}

			return current;
		}
		
		public void remove(){
			throw new UnsupportedOperationException("This method is not supported");
		}
	}
		
	
	/**
	 *	This class represents an iterator of the contents of the
	 *	list.  
	 */
	static class ScalableListIterator<E>
	implements Serializable, Iterator<E> {
		
		private ListNode<E> currentNode;
		private ScalableListNodeIterator<E> listNodeIter;
		private Iterator<ManagedReference<E>> iter;
		
		private long listNodeReferenceValue = -1;
		
		public static final long serialVersionUID = 1L;
		
		public ScalableListIterator(ListNode<E> head){
			listNodeIter = new ScalableListNodeIterator<E>(head);
			
			if (listNodeIter == null){
				currentNode = null;
				iter = null;
				return;
			}
			
			// Prepare by getting first ListNode
			if (listNodeIter.hasNext()){
				currentNode = (ListNode<E>) listNodeIter.next();
			} else {
				currentNode = null;
			}
			
			// Do a last check to make sure we are getting non-null values
			if (currentNode == null || currentNode.getSubList() == null){
				iter = null;
				return;
			}
			
			// Set up the ListIterator, but do not populate
			// current until hasNext() has been called.
			iter = currentNode.getSubList().getElements().iterator();
		}
		
		
		/**
		 * {@inheritDoc}}
		 */
		public E next(){
			
			listNodeReferenceValue = System.nanoTime(); 
			ManagedReference<E> ref = ((ManagedReference<E>) iter.next());
			if (ref != null){
				return ref.get();
			} else {
				return null;
			}
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean hasNext(){
			// If there is an element in the iterator still,
			// then simply return true since it will be the
			// next element to be returned.
			if (iter.hasNext()){
				return true;
			}
			
			// Otherwise, we need to fetch the next ListNode
			// and construct a list iterator from that
			if (listNodeIter.hasNext()){
				currentNode = listNodeIter.next();
				iter = currentNode.getSubList().getElements().iterator();
				return hasNext();
			}
			return false;
		}
		
		
		/**
		 * @deprecated This operation is not officially supported.
		 * Removal of nodes is achieved by the ScalableList API.
		 */
		public void remove(){
			throw new UnsupportedOperationException(
					"Removal of nodes is achieved through the ScalableList API");
		}
	}
	
	
	
	/**
	 * Node which parents a {@code SubList<E>}. These nodes can be
	 * considered as the leaf nodes of the tree and contain 
	 * references to a portion of the list.
	 */
	static class ListNode<E> implements ManagedObject, Serializable {
		private int count;
		private ManagedReference<SubList<E>> subListRef;
		private ManagedReference<ListNode<E>> nextRef;
		private ManagedReference<ListNode<E>> prevRef;
		private ManagedReference<TreeNode<E>> parentRef;
		
		public static final long serialVersionUID = 1L;
		
		private long dataIntegrityVal;
		
		public ListNode(TreeNode<E> parent, int maxSize){
			SubList<E> sublist = new SubList<E>(maxSize);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRef = dm.createReference(sublist);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
		}
		
		public ListNode(TreeNode<E> parent, E e){
			SubList<E> sublist = new SubList<E>(e);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRef = dm.createReference(sublist);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
		}
		
		public ListNode(TreeNode<E> parent, int maxSize, E e){
			SubList<E> sublist = new SubList<E>(maxSize, e);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRef = dm.createReference(sublist);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
		}
		
		public ListNode(TreeNode<E> parent, int maxSize, ArrayList<ManagedReference<E>> list){
			SubList<E> sublist = new SubList<E>(maxSize, list);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRef = dm.createReference(sublist);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			if (parent != null){
				parentRef = dm.createReference(parent);
			}
		}
		
		public long getDataIntegrityValue(){
			return dataIntegrityVal;
		}
		
		public void setNext(ListNode<E> ref){
			if (ref != null){
				nextRef = AppContext.getDataManager().createReference(ref);
			}
		}
		
		public void setParent(TreeNode<E> parent){
			if (parent != null){
				parentRef = AppContext.getDataManager().createReference(parent);
			}
		}
		
		public ListNode<E> getNext(){
			ListNode<E> next;
			if (nextRef == null){
				return null;
			}
			try {
				next = nextRef.get();
			} catch (ObjectNotFoundException onfe) {
				next = null;
			}
			return next;
		}
		
		public void setPrev(ListNode<E> ref){
			if (ref != null){
				prevRef = AppContext.getDataManager().createReference(ref);
			}
		}
		
		public ListNode<E> getPrev(){
			ListNode<E> prev;
			if (prevRef == null){
				return null;
			}
			try {
				prev = prevRef.get();
			} catch (ObjectNotFoundException onfe) {
				prev = null;
			}
			return prev;
		}
		
		public int size(){
			return count;
		}
		
		public SubList<E> getSubList(){
			SubList<E> list;
			if (subListRef == null){
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
		 * Appends the supplied object to the list
		 * and performs a split if necessary.
		 * @param obj the Object to append.
		 * @return whether the operation was successful;
		 * true if so, false otherwise.
		 */
		public boolean append(E e){
			boolean result = getSubList().append(e); 
			if (result){
				count++;
				dataIntegrityVal = System.nanoTime();
				updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
			}
			
			// check if we need to split; i.e. if we have exceeded
			// the specified list size.
			if (count > getSubList().MAX_CHILDREN_APPEND){
				split();
			}
			return result;
		}
		
		public void insert(int index, E e){
			getSubList().insert(index, e);
			count++;
			dataIntegrityVal = System.nanoTime();
			updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
			
			// check if we need to split; i.e. if we have exceeded
			// the specified list size.
			if (count > getSubList().MAX_CHILDREN){
				split();
			}
		}

		
		/**
		 * Recursively removes children from the Data Store.
		 */
		public void clear(){
			count = 0;
			getSubList().clear();
		}
		
		/**
		 * Removes the object at the specified index of the 
		 * sublist. The index argument is not an absolute 
		 * index; it is a relative index which points to a 
		 * valid index in the list.<p>
		 * 
		 * For example, if there are five ListNodes with a 
		 * cluster size of five, the item with an absolute 
		 * index of 16 corresponds to an element in the 
		 * fourth ListNode<E>, with a relative offset of 1.
		 *  
		 * @param index the index corresponding to an element
		 * in the list (not an absolute index with respect
		 * to the {@code ScalableList} object.
		 * @return the element that was removed.
		 */
		public Object remove(int index){
			Object obj = getSubList().remove(index);
			if (obj != null){
				count--;
				dataIntegrityVal = System.nanoTime();
				updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);
			}
			
			// remove list node if necessary
			checkRemoveListNode();
			return obj;
		}
		
		
		/**
		 * Removes the {@code Object} from the {@code SubList<E>}, if it exists.
		 * @param obj the {@code Object} to remove.
		 * @return whether the object was removed or not; true if so,
		 * false otherwise.
		 */
		public boolean remove(Object obj){
			boolean result = getSubList().remove(obj);
			
			// If a removal took place, then update
			// count information accordingly
			if (result){
				count--;
				dataIntegrityVal = System.nanoTime();
				// remove list node if it is empty
				checkRemoveListNode();
			}
			
			
			return result;
		}
		
		public boolean remove(Element<E> e){
			boolean result = getSubList().remove(e);
			if (result){
				count--;
				dataIntegrityVal = System.nanoTime();
			}
			
			// remove list node if necessary
			checkRemoveListNode();
			
			return result;
		}
		
		
		/**
		 * A method that determines how to remove an
		 * empty {@code ListNode<E>} from a list of other 
		 * {@code ListNode<E>}s. Update previous to point 
		 * to next (doubly) and remove
		 * this object from Data Store
		 */
		private void checkRemoveListNode(){
			
			// If the size is not zero, then we
			// will not be removing any nodes,
			// so no relinking; just return.
			if (this.size() != 0){
				return;
			}
			
			// Otherwise, we need to remove the list
			// node and relink accordingly. 
			// First, we determine the type
			// of list node: there are four possibilities:
			// 1) interior node; connect prev to next
			if (this.getNext() != null && this.getPrev() != null){
				this.getPrev().setNext(this.getNext());
				this.getNext().setPrev(this.getPrev());
			}
			// 2) head node. Update child pointer from parent
			else if (this.getNext() != null){
				this.getParent().setChild(this.getNext());
				this.getNext().setPrev(null);
			}
			// 3) tail node
			else if (this.getPrev() != null){
				this.getPrev().setNext(null);
			} 
			// 4) only child
			else {
				this.getParent().setChild(null);
			}
			
			// If we are removing the child of the
			// TreeNode and the parent has other children,
			// then we need to give it a substitute
			if (this.getParent().size() > 0 && 
					this.getParent().getChild().equals(this)){
				this.getParent().setChild(this.getNext());
			}
			
			// This is an empty node, so remove it from Data Store.
			AppContext.getDataManager().removeObject(this);
		}
		
		public TreeNode<E> getParent(){
			TreeNode<E> parent;
			if (parentRef == null){
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
		 * Prepends the supplied object to the list.
		 * This may implicitly cause splitting.
		 * @param obj the Object to add.
		 */
		public void prepend(E e){
			insert(0, e);
		}
		
		/**
		 * Splits a linked list of {@code ListNodes} into
		 * two smaller lists. This is necessary when a
		 * linked list exceeds the maximum length,
		 * denoted by the cluster size.
		 */
		private boolean split(){
			ArrayList<ManagedReference<E>> contents = 
				getSubList().getElements();
			ArrayList<ManagedReference<E>> spawned = 
				new ArrayList<ManagedReference<E>>();
			
			// Walks up the tree to increment the new size
			updateParents(this.getParent(), TreeNode.INCREMENT_NUM_CHILDREN);
			
			// move last half of list into a new child
			int sublistSize = getSubList().size();
			int lower = Math.round(sublistSize/2);
			for (int index = lower;	index < sublistSize ; index++){
				spawned.add(contents.get(index));
			}
			
			// remove the relocated nodes from the current list
			// and mark that the list has changed
			contents.removeAll(spawned);
			dataIntegrityVal = System.nanoTime();
			this.count = contents.size();
			
			// Create a new ListNode<E> for the moved contents
			ListNode<E> spawnedNode = new ListNode<E>(
					getParent(), this.getSubList().MAX_CHILDREN, spawned);
			spawnedNode.setNext(this.getNext());
			spawnedNode.setPrev(this);
			this.setNext(spawnedNode);
			
			// Check with parent to see if more splitting is necessary
			return getParent().performSplitIfNecessary();
		}
		
		
		/**
		 * Propagates changes in the form of either an increment
		 * or decrement to the parents of the tree.
		 * @param parent
		 * @param mode
		 */
		private void updateParents(TreeNode<E> parent, byte mode){
			parent.propagateChanges(parent, mode);
		}
		
	}
	
	
	
	
	/**
	 * This object represents a partition in the list.
	 * Each of these objects lives as a singleton
	 * inside a ListNode<E> object.
	 */
	static class SubList<E> implements ManagedObject, Serializable {
		public static final int SIZE_NOT_SET = -1;
		public static final int DEFAULT_MAX_CHILDREN = 10;
		
		private ArrayList<ManagedReference<E>> contents;
		
		private ManagedReference<Offset> offsetRef;
		
		private int size = SIZE_NOT_SET;
		public int MAX_CHILDREN = DEFAULT_MAX_CHILDREN;
		public int MAX_CHILDREN_APPEND;
		
		public static final long serialVersionUID = 1L;
		
		public static boolean isLegal(int maxSize){
			return (maxSize > 0);
		}
		
		public SubList(int maxSize, ArrayList<ManagedReference<E>> collection){
			if (isLegal(maxSize)){
				MAX_CHILDREN = maxSize;
			}
			contents = new ArrayList<ManagedReference<E>>();
			// This sets a limit on the number of elements
			// which can be appended to a node. A value of 2/3 times
			// the maximum capacity allows for some insertions
			// without having to split the node.
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			ManagedReference<E> tmp = null;
			
			for (int i=0 ; i < collection.size() ; i++){
				tmp = collection.get(i);
				contents.add(tmp);
			}
			
			size = contents.size();
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}
		
		public SubList(int maxSize){
			if (isLegal(maxSize)){
				MAX_CHILDREN = maxSize;
			}
			// This sets a limit on the number of elements
			// which can be appended to a node. A value of 2/3 times
			// the maximum capacity allows for some insertions
			// without having to split the node.
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = new ArrayList<ManagedReference<E>>();
			size = contents.size();
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}
		
		public SubList(int maxSize, E e){
			if (isLegal(maxSize)){
				MAX_CHILDREN = maxSize;
			}
			// This sets a limit on the number of elements
			// which can be appended to a node. A value of 2/3 times
			// the maximum capacity allows for some insertions
			// without having to split the node.
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = new ArrayList<ManagedReference<E>>();
			append(e);
			size = contents.size();
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}
		
		public SubList(E e){
			MAX_CHILDREN = DEFAULT_MAX_CHILDREN;
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = new ArrayList<ManagedReference<E>>();
			append(e);
			size = contents.size();
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}

		
		public void setOffset(int offset){
			offsetRef.getForUpdate().set(offset);
		}
		
		public int getOffset(){
			if (offsetRef != null){
				return offsetRef.get().get();
			} else {
				return -1;
			}
		}
		
		public int size(){
			return size;
		}
		
		public ArrayList<ManagedReference<E>> getElements(){
			return contents;
		}
		
		/**
		 * Since the list is a collection of ManagedReferences,
		 * we are interested in retrieving the value it points
		 * to.
		 * @param index
		 * @return
		 */
		public E get(int index){
			if (contents != null){
				return contents.get(index).get();
			} else {
				return null;
			}
		}
		
		public E getFirst(){
			return get(0);
		}
		
		public E getLast(){
			if (contents != null){
				return get(contents.size() - 1);
			} else {
				return null;
			}
		}
		
		/**
		 * Removes all elements in the sublist and
		 * removes the {@code SubList<E>} object from
		 * the Data Store.
		 */
		public void clear(){
			DataManager dm = AppContext.getDataManager();
			Iterator<ManagedReference<E>> iter = contents.iterator();
			
			// Delete all the elements within the sub list
			while (iter.hasNext()){
				dm.removeObject(iter.next().get());
			}
			contents.clear();
			
			// Remove the offset reference
			if (offsetRef != null){
				dm.removeObject(offsetRef.get());
			}
		}
		

		
		/**
		 * Sets the value at the index provided. The
		 * index is not an absolute index; rather,
		 * it is relative to the current list. If
		 * the index does not correspond to a valid
		 * index in the underlying list, an
		 * {@code IndexOutOfBoundsException} will be
		 * thrown.
		 * @param index the index to add the element
		 * @param e the element to be added.
		 * @return the old element that was replaced.
		 */
		public Object set(int index, E e){
			ManagedReference<E> ref = null;
		
			if (e instanceof ManagedObject){
				ref = AppContext.getDataManager().createReference(e);
			} else {
				Element<E> element = new Element<E>(e);
				ref = AppContext.getDataManager().createReference((E) element);
			}
			ManagedReference<E> old = contents.set(index, ref);
			Object oldObj = old;
			
			// Delete from data store
			AppContext.getDataManager().removeObject((ManagedObject) old.get());
			return oldObj;
		}
		
		/**
		 * Appends the supplied argument to the list. It will
		 * throw a {@code NullPointerException} if the
		 * supplied object is null.
		 * @param obj the element to add to append.
		 * @return whether the operation was successful; true
		 * if so, false otherwise.
		 */
		public boolean append(E e){
			boolean result = false;
			if (e == null){
				throw new NullPointerException(
						"The appended object cannot be null");
			}
			
			// If it is not yet a ManagedObject, then
			// create a new Element to make it a ManagedObject
			ManagedReference<E> ref = null;
			if (!(e instanceof ManagedObject)){
				Element<E> element = new Element<E>(e);
				ref = AppContext.getDataManager().createReference((E) element);
			} else {
				ref = AppContext.getDataManager().createReference(e);
			}
			result = contents.add(ref);
			size = contents.size();
			
			return result;
		}

		/**
		 * Returns the index of the element inside the
		 * {@code SubList<E>}. If the element does not
		 * exist, then -1 is returned.
		 * @param o the element whose last index is
		 * to be found.
		 * @return the index of the element, or -1 if
		 * it does not exist.
		 */
		public int lastIndexOf(Object o){
			Iterator<ManagedReference<E>> iter = contents.iterator();
			ManagedReference<?> ref = null;
			Object obj = null;
			int index = 0;
			int lastIndex = -1;
			
			while (iter.hasNext()){
				ref = iter.next();
				obj = ref.get();
				
				if (obj.equals(o)){
					lastIndex = index;
				}
				index++;
			}
			return lastIndex;
		}

		
		/**
		 * Inserts the element into the list at a specified
		 * location. If the index is not valid, an
		 * {@code IndexOutOfBoundsException} is thrown.
		 * @param index the index to add the new element.
		 * @param obj the object which is to be inserted at
		 * the specified {@code index}.
		 */
		public void insert(int index, E e){
			if (index < 0){
				throw new IndexOutOfBoundsException("Supplied index cannot be less than 0");
			}
			if (e == null){
				throw new IllegalArgumentException("Cannot insert null");
			}
			
			ManagedReference<E> ref = null;
			if (e instanceof ManagedObject){
				ref = AppContext.getDataManager().createReference(e);
			} else {
				Element<E> element = new Element<E>(e);
				ref = AppContext.getDataManager().createReference((E) element);
			}
			contents.add(index, ref);
			size = contents.size();
		}
		
		
		/**
		 * Determines the index of the first occurrence of
		 * the supplied argument. If the element does not
		 * exist, then -1 is returned.
		 * @param o the element whose index is to be searched.
		 * @return the first index of the supplied element,
		 * or -1 if it does not exist in the list.
		 */
		public int indexOf(Object o){
			Iterator<ManagedReference<E>> iter = contents.iterator();
			ManagedReference<?> ref = null;
			Object obj = null;
			int index = 0;
			
			while (iter.hasNext()){
				ref = iter.next();
				obj = ref.get();
				
				if (obj.equals(o)){
					return index;
				}
				index++;
			}
			return -1;
		}
	
		
		/**
		 * Removes the element at the supplied index. This method
		 * throws an {@code IndexOutOfBoundsException} if the
		 * index does not exist in the underlying list.
		 * @param index the index to remove.
		 * @return the object removed from the index.
		 */
		public Object remove(int index){
			if (index > contents.size()-1){
				throw new IndexOutOfBoundsException("The index, "+index+
						", is out of bounds");
			}
			
			ManagedReference<?> removed = contents.remove(index);
			if (removed != null && removed.get() instanceof Element){
				AppContext.getDataManager().removeObject(removed.getForUpdate());
			}
			this.size = contents.size();
			return removed;
		}
		
		
		/**
		 * Removes the supplied object from the underlying list,
		 * if it exists. 
		 * @param obj the element to remove from the list.
		 * @return whether the operation was successful; true if
		 * so, false otherwise.
		 */
		public boolean remove(Object obj){
			Iterator<ManagedReference<E>> iter = contents.iterator();
			ManagedReference<E> current = null;
			Object object = null;
			boolean success = false;
			
			// go through all the elements in this collection (a sublist)
			while (iter.hasNext()){
				current = iter.next();
				object = current.get();
				
				if (obj.equals(object)){
					// remove the object in the Element wrapper. If
					// it was a ManagedObject and not an Element,
					// then we just remove the reference to it.
					if (obj instanceof Element){
						AppContext.getDataManager().removeObject(object);
					}
					success = contents.remove(current);
					break;
				}
			}
			return success;
		}
		
		
		/**
		 * Removes the last element from the list.
		 * @return the removed element that had
		 * existed at the end of the list. 
		 */
		public Object removeLast(){
			return remove(contents.size()-1);
		}
		
	}
	
	
	public static void main(String[] args){
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);
		//assertEquals(-1, list.indexOf("C"));
		
		list.add("C");
		list.remove("C");
		//assertEquals(-1, list.indexOf("C"));
	}
}
