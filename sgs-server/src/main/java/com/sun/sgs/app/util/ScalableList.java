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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Particle;

/**
 *  This class represents an {@code AbstractCollection} which supports 
 *  concurrency and introduces tools which achieve this. This data 
 *  structure builds upon the AbstractList class by implementing 
 *  methods specific to concurrent operations.<p>
 *  
 *  The class achieves concurrency by partitioning an ordinary
 *  list into a number of smaller lists contained in {@code ListNode}
 *  objects, and joining the nodes as a linked-list. This 
 *  implementation bears similarity to a shallow skip-list in 
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
 *  When the nodes require modification, iterators are responsible
 *  for interpreting the node sizes and dealing with changes
 *  due to concurrency. When iterators propagate through the list,
 *  they generate the list size based on knowledge of the 
 *  {@code ListNode}'s size, thereby making the {@code size()} 
 *  operation uncharacteristically more expensive. However, it is 
 *  seen that the benefits provided by the partitioning of the 
 *  list that enable concurrency outweigh the performance hit for 
 *  this operation. <p>
 *  
 *  When an element is requested from the data structure, the
 *  iterator's position is not affected by modifications to the
 *  list prior to its current location. However, if modifications
 *  happen between the current position and the destination, they
 *  will be involved in the iteration. Therefore, in the event
 *  that such a modification takes place, this
 *  implementation does not guarantee that the element at the
 *  specified index will be the one accessed. <p>
 */
public class ScalableList<E> 
extends AbstractCollection<E>
implements 	ManagedObject, Serializable {

	public static final long serialVersionUID = 1L;
	
	/**
	 * A reference to the head node of the list.
	 */
	private ManagedReference<ManagedReference<ListNode>> headRef;
	private ManagedReference<ListNode> headRefLink;
	private ManagedReference<TreeNode> headTreeNodeRef;
	/**
	 * A reference to the tail of the list. This
	 * makes appending to the list a constant-time
	 * operation.
	 */
	private ManagedReference<ManagedReference<ListNode>> tailRef;
	private ManagedReference<ListNode> tailRefLink;
	
	/**
	 * The maximum size of the intermediate lists.
	 * This number should be small enough to enable
	 * concurrency but large enough to contain a
	 * reasonable number of nodes.
	 */
	private int clusterSize = 10;
	
	/**
	 * The maximum number of children contained in a TreeNode;
	 * this paramter is passed to the TreeNode during
	 * instantiation.
	 */
	private int maxChildSize = 5;
	
	
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
		headRef = null;
		tailRef = null;
		headRefLink = null;
		headTreeNodeRef = null;
		tailRefLink = null;
	}
	
	/**
	 * Constructor which creates a {@code ScalableList} object
	 * with the resolution and cluster size supplied as a parameter.
	 * @param clusterSize The size of each partitioned list. This
	 * value must be a positive integer (larger than 0).
	 * @param resolution Whether the {@code ScalableList} should
	 * perform resolution. True if so, and false otherwise.
	 */
	public ScalableList(int maxChildSize, int clusterSize){
		headRef = null;
		tailRef = null;
		headRefLink = null;
		tailRefLink = null;
		headTreeNodeRef = null;
		
		if (clusterSize < 1){
			throw new IllegalArgumentException("Cluster size must "+
					"be an integer larger than 0");
		}
		if (maxChildSize < 2){
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
		if (tailRef == null){
			return addFirstEntry(e);
		}
		// otherwise, add it at the end since no index
		// and propagate change to parents
		result = tailRef.get().get().append(e);
		
		// check location of headTreeNodeRef, in case the
		// append introduced a new TreeNode root. The
		// headTreeNodeRef should be the root's first child.
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
		if (headRef == null){
			addFirstEntry(e);
			return;
		}
		// otherwise, add it to the beginning
		headRef.get().get().prepend(e);
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
		} else if (headRef == null && index == 0){
			addFirstEntry(e);
			return;
		} else if (headRef == null){
			throw new IllegalArgumentException("Cannot add to index "+
					index + " on an empty list");
		}
		
		// otherwise, add it to the specified index.
		// This requires a search of the list nodes.
		ListNode n = getNode(index);
		n.insert(index - n.getSubList().getOffset(), e);
		
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
		AppContext.getTaskManager().scheduleTask(new AsynchronousClearTask(headTreeNodeRef));
		
		// Create a new ListNode and link everything to it.
		TreeNode t = new TreeNode(null, maxChildSize, clusterSize);
		ListNode n = (ListNode) t.getChild();
		headRefLink = AppContext.getDataManager().createReference((ListNode) n);
		headRef = AppContext.getDataManager().createReference(headRefLink);
		tailRefLink = AppContext.getDataManager().createReference((ListNode) n);
		tailRef = AppContext.getDataManager().createReference(tailRefLink);
		
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
		ScalableListNodeIterator iter = 
			new ScalableListNodeIterator(headTreeNodeRef.get());
		ListNode n;
		int index = -1;
		
		while (iter.hasNext()){
			n = (ListNode) iter.next();
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
		ScalableListNodeIterator iter = 
			new ScalableListNodeIterator(headTreeNodeRef.get());
		ListNode n = null;
		
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
		ListNode n = getNode(index);
		if (n == null){
			return null;
		}

		// Performs any relinking in case the removed
		// ListNode was the head or tail
		Object obj = n.remove(n.getSubList().getOffset());
		relinkIfNecessary(n);		
		
		return obj;
	}
	
	/**
	 * Updates the ScalableList's head and/or tail in the 
	 * event that it was removed.
	 * @param n
	 */
	private void relinkIfNecessary(ListNode n){
		// Store values before they are deleted
		ListNode next = n.getNext();
		ListNode prev = n.getPrev();
		
		// Instantiate a ListNode iterator in the event we
		// need to locate a cousin ListNode to be the head
		// or tail
		ScalableListNodeIterator iter = 
			new ScalableListNodeIterator(headTreeNodeRef.get());
		
		// Check whether we need to update the head or tail.
		if (n.equals(headRefLink) && n.size() == 0){
			// Check if we need to search in another TreeNode
			if (next != null){
				headRefLink = AppContext.getDataManager()
					.createReference(next);
			} else {
				headRefLink = AppContext.getDataManager()
					.createReference(iter.next());
			}
		} 
		// Update the tail 
		if (n.equals(tailRefLink.get()) && n.size() == 0){
			// Check if we need to search in another TreeNode
			if (prev != null){
				tailRefLink = AppContext.getDataManager()
					.createReference(prev);
			} else {
				// get the last element in the iterator
				// which represents the last ListNode; hence
				// tail.
				while (iter.hasNext()){
					prev = iter.next();
				}
				tailRefLink = AppContext.getDataManager()
					.createReference(prev.getPrev());
			}
		}
	}
	
	/**
	 * Locates an element with the supplied index and replaces its
	 * value with the provided {@code Object}.
	 * @param index the index of the {@code Object} to modify
	 * @param obj the {@code Object} to replace with
	 * @return the {@code Object} which previously existed in the list
	 */
	public Object set(int index, Object obj){
		SubList n = getNode(index).getSubList();
		return n.set(index - n.getOffset(), obj);
	}
	
	
	/**
	 * Removes the last element from the List. If the element was the
	 * last in the collection, then we leave the ListNode empty in
	 * anticipation of soon writing to it again (appending).
	 * @return The {@code Object} which was removed.
	 */
	public Object removeLast(){
		ListNode tail = tailRefLink.get(); 
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
	public Object get(int index){
		
		// Iterate through using the count. Use a get()
		// iterator because we are not modifying anything; hence, false.
		SubList n = getNode(index).getSubList();
		if (n == null){
			return null;
		}
		return n.get(index - n.getOffset());
	}
	
	/**
	 * Returns the last (tail) element of the list, for
	 * reading purposes.
	 * @return the tail element of the list.
	 */
	public Object getLast(){
		SubList n = tailRefLink.get().getSubList();
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
	public Object getFirst(){
		SubList n = headRefLink.get().getSubList();
		if (n == null){
			return null;
		}
		return n.getFirst();
	}
	

	
	/**
	 * Retrieves the {@code Iterator} for the list.
	 */
	public Iterator<E> iterator(){
		return new ScalableListIterator<E>(headTreeNodeRef.get());
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
		ScalableListIterator<ListNode> iter = 
			new ScalableListIterator<ListNode>(headTreeNodeRef.get());
		ListNode n = null;
		boolean removed = false;
		
		// Find and remove the object in the ListNode that contains it
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
	 * {@inheritDoc}
	 */
	public boolean isEmpty(){
		return (this.size() == 0);
	}
	
	
	/**
	 * Performs the search for the desired ListNode.
	 * This method implicitly updates the offset value
	 * for the particular node we are finding, meaning
	 * when we locate the ListNode, the OffsetNode
	 * child will be updated to represent the offset
	 * of the index we are searching for.
	 * @param index The absolute index in the entire
	 * list to search for the entry.
	 * @return The {@code ListNode} which contains the
	 * element at the specified {@code index}.
	 */
	private ListNode getNode(int index){
		// Recursive method to eventually return ListNode
		// containing the desired index.
		return search(headTreeNodeRef.get(), 0, index);
	}
	
	
	
	private ListNode search(ManagedObject tn, int currentValue, int destIndex){
		if (tn instanceof TreeNode){
			TreeNode t = (TreeNode)tn;
			currentValue = t.size();
			
			while (currentValue < destIndex){
				tn = t.next();
				
				// If we hit a null, then the index specified was too large
				// for the collection, so return null.
				if (tn == null){
					return null;
				}
				currentValue += t.size();
			}
			currentValue -= t.size();
			return search(t.getChild(), currentValue, destIndex);
			
			
		} else if (tn instanceof ListNode){
			ListNode n = (ListNode)tn;
			n.getSubList().setOffset(destIndex - currentValue);
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
		TreeNode t = new TreeNode(null, maxChildSize, clusterSize, e);
		DataManager dm = AppContext.getDataManager();
		headTreeNodeRef = dm.createReference(t);
		ListNode n = (ListNode) t.getChild();
		headRefLink = dm.createReference(n);
		tailRefLink = dm.createReference(n);
		return true;
	}
	
	/**
	 * Returns a reference to the head {@code ListNode} of the list, which
	 * represents a segment of the overall list.
	 * @return the head node
	 */
	protected ManagedReference<ListNode> getHeadRef(){
		return headRefLink;
	}
	
	/**
	 * Returns a reference to the tail {@code ListNode} of the list, which
	 * represents a segment of the overall list.
	 * @return the tail node
	 */
	protected ManagedReference<ListNode> getTailRef(){
		return tailRefLink;
	}
	
	/**
	 * Retrieves the size of the list. The size of the
	 * list is the size of the root {@code TreeNode} of
	 * the list because all of its children represent a
	 * count of their children.
	 * @return the number of elements of the list.
	 */
	public int size(){
		// This gives us a reference to the one and only
		// root TreeNode, which parents the entire tree.
		// It should be noted that this object is not the 
		// same as the headTreeNodeRef, which returns the
		// first child of the root.
		int size = 0;
		TreeNode t = headTreeNodeRef.get();
		while (t != null){
			size += t.size();
			t = t.next();
		}
		return size;
	}
	
	
	/**
	 * Given the current head pointer, percolate up
	 * the tree and find the root.
	 * @return the root {@code TreeNode} of the tree.
	 */
	protected void updateTreeNodeRefs(){
		TreeNode treeHead = headTreeNodeRef.get();
		
		// Percolate up the tree until we are at the
		// node whose parent is the root.
		while (treeHead.getParent().getParent() != null){
			treeHead = treeHead.getParent();
		}
		
		// Update the head to point to the new location
		// this should be the child of the root node
		// as the root is not useful for most list
		// operations.
		headTreeNodeRef = AppContext.getDataManager()
			.createReference(treeHead);
		
	}
	
	
	
	
	
	/*
	  	////////////////////////////////
			INLINE CLASSES
		///////////////////////////////
	*/
	
	static class TreeNode
	implements ManagedObject, Serializable {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<TreeNode> nextRef;
		private ManagedReference<TreeNode> prevRef;
		private ManagedReference<ManagedObject> childRef;
		private ManagedReference<ManagedObject> tailRef;
		private ManagedReference<TreeNode> parentRef;
		
		private int size = 0;
		private int MAX_NUMBER_CHILDREN = 5;
		private int clusterSize = 0;
		private int childrenCount = 0;
		
		public static final byte DECREMENT_SIZE = 0;
		public static final byte INCREMENT_SIZE = 1;
		public static final byte INCREMENT_NUM_CHILDREN = 2;
		public static final byte DECREMENT_NUM_CHILDREN = 3;
		
		public void setHead(ListNode ref){
			childRef = AppContext.getDataManager().createReference((ManagedObject) ref);
		}
		
		public TreeNode(){
			size = 0;
			nextRef = null;
			childRef = null;
			parentRef = null;
		}
		
		
		/**
		 * Constructor which is called on a split. This is used to create
		 * a new sibling and accepts a TreeNode argument which represents
		 * the new child it is to possess.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param child
		 */
		private TreeNode(TreeNode parent, int maxNumChildren, 
				int clusterSize, ListNode child, int numberChildren){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;

			DataManager dm = AppContext.getDataManager();
			childRef = dm.createReference((ManagedObject) child);
			parentRef = dm.createReference(parent);
			childrenCount = numberChildren;
			
			// The tail element is the last linked node in the list
			ListNode n = (ListNode) childRef.get();
			while (n != null){
				n = (ListNode) n.getNext();
			}
			tailRef = dm.createReference((ManagedObject) n);
		}
		
		
		/**
		 * Constructor which is called on a split. This is used to create
		 * a new sibling and accepts a TreeNode argument which represents
		 * the new child it is to possess.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param child
		 */
		private TreeNode(TreeNode parent, int maxNumChildren, 
				int clusterSize, TreeNode child, int numberChildren){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;

			DataManager dm = AppContext.getDataManager();
			childRef = dm.createReference((ManagedObject) child);
			parentRef = dm.createReference(parent);
			childrenCount = numberChildren;
			
			// The tail element is the last linked node in the list
			TreeNode n = (TreeNode) childRef.get();
			while (n != null){
				n = (TreeNode) n.next();
			}
			tailRef = dm.createReference((ManagedObject) n);
		}
		
		/**
		 * Create a new TreeNode on account of a new leaf ({@code ListNode}) 
		 * being created.
		 * @param parent
		 * @param maxNumChildren
		 * @param clusterSize
		 * @param obj
		 */
		public TreeNode(TreeNode parent, int maxNumChildren, int clusterSize, Object obj){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			if (obj == null){
				throw new NullPointerException("Argument suppled to List is null");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;
			ListNode n = new ListNode(this, clusterSize, obj);
			size = n.size();
			childrenCount = 1;
			DataManager dm = AppContext.getDataManager();
			childRef = dm.createReference((ManagedObject) n);
			parentRef = dm.createReference(parent);
			tailRef = dm.createReference((ManagedObject) n);
		}
		
		public TreeNode(TreeNode parent, int maxNumChildren, int clusterSize){
			if (maxNumChildren < 0){
				throw new IllegalArgumentException("Maximum children parameter should "+
						"not be less than 0");
			}
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;
			ListNode n = new ListNode(this, clusterSize);
			size = n.size();
			DataManager dm = AppContext.getDataManager();
			childRef= dm.createReference((ManagedObject) n);
			parentRef = dm.createReference(parent);
			tailRef = dm.createReference((ManagedObject) n);
		}
		
		public TreeNode prev(){
			return prevRef.get();
		}
		public void setPrev(TreeNode ref){
			prevRef = AppContext.getDataManager().createReference(ref);
		}
		public TreeNode next(){
			return nextRef.get();
		}
		public ManagedObject getChild(){
			return childRef.get();
		}
		public void setChild(ManagedObject child){
			childRef = AppContext.getDataManager().createReference(child);
		}
		public int size(){
			return size;
		}
		public void setTail(ManagedObject ref){
			tailRef = AppContext.getDataManager().createReference(ref);
		}
		public ManagedObject getTail(){
			return tailRef.get();
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
		public TreeNode getParent(){
			return parentRef.get();
		}
		public void setNext(TreeNode t){
			nextRef = AppContext.getDataManager().createReference(t);
		}
		
		/**
		 * Unlinks itself from the tree. This can be considered
		 * as a TreeNode remove since 
		 * @return
		 */
		public TreeNode remove(){
			
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
		 * In the event of a split, we will need to update the
		 * root node in the whole tree since it is not actively
		 * updated to allow for concurrency. If it were updated
		 * after every operation, write locks will prevent
		 * multiple writes to certain parts of the list.
		 */
		private void updateCounts(TreeNode t){
			ManagedObject node = t.getChild();
			int size = 0;
			int childrenCount = 0;
			
			// Update the values, depending on what the children are.
			if (node instanceof ListNode){
				while (node != null){
					childrenCount++;
					size += ((ListNode) node).size();
					node = (ManagedObject) ((ListNode) node).getNext();
				}
			}
			else if (node instanceof TreeNode){
				while (node != null){
					childrenCount++;
					size += ((TreeNode) node).size();
					node = (ManagedObject) ((TreeNode) node).next();
				}
			}
		}
		
		
		private int calculateSplitSize(int numberOfChildren){
			return Math.round(numberOfChildren/2);
		}
		
		/**
		 * This method walks up the tree and performs any
		 * {@code TreeNode} splits if the children sizes
		 * have been exceeded.
		 */
		public void performSplitIfNecessary(){
			TreeNode newNode = null;
			ManagedObject tmp = (ManagedObject) this.getChild();
			
			// If we are at the root, update it
			// with the proper count and childrenCount.
			// We did not do this before in order to keep
			// this node free from modification so that
			// the data structure can be at least somewhat
			// concurrent.
			if (this.getParent() == null){
				updateCounts(this);
			}
			
			// Check if we need to split, and if so, do it based
			// on the type of element contained (ListNode or TreeNode)
			if (childrenCount > MAX_NUMBER_CHILDREN){
				generateParentIfNecessary(this);
				
				// Perform split by creating and linking new sibling
				newNode = createSibling(tmp, childrenCount);
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
				if (parentRef.get() != null){
					parentRef.get().performSplitIfNecessary();
				} 
			}
		}
		
		
		/**
		 * Determines the nature of the sibling to be created. The
		 * Node created will always be a TreeNode, but the type of
		 * child depends on whether it parents ListNodes, or other
		 * TreeNodes.
		 * @param child the {@code ManagedObject} which needs to
		 * be split and placed under a new parent.
		 * @param prev the element previous to the current element
		 * @param halfway a value representing an index approximately
		 * half the length of the contents
		 * @return
		 */
		private TreeNode createSibling(ManagedObject child, int numberOfChildren){
			ManagedObject prev = null;
			TreeNode newNode = null;
			int halfway = calculateSplitSize(numberOfChildren);
			
			// Determine what kind of sibling to make, since a TreeNode's
			// child can be either a ListNode or a TreeNode
			if (child instanceof ListNode){
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = child;
					child = (ManagedObject) ((ListNode)child).getNext();
				}
				newNode = new TreeNode(this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(ListNode) child,
										numberOfChildren - halfway);
				// Update references
				((ListNode)prev).setNext(null);
				((ListNode)child).setPrev(null);
				((ListNode)child).setParent(newNode);
				
			} else if (child instanceof TreeNode){
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = child;
					child = ((TreeNode)child).next();
				}
				newNode = new TreeNode( this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(TreeNode) child,
										numberOfChildren - halfway);
				// Update references
				((TreeNode)prev).setNext(null);
				((TreeNode)child).setPrev(null);
				((TreeNode)child).setParent(newNode);
				
			} else {
				throw new IllegalStateException("Attempting to split a node that is neither "+
						"a TreeNode or ListNode");
			}
			this.setTail(prev);			
			return newNode;
		}
		

		/**
		 * This method creates a parent above the provided node so that
		 * any new siblings resulting from a split can be joined to
		 * the new parent. The decision to create a new parent depends
		 * on whether the supplied node is an only-child; only these
		 * nodes are orphaned while siblings have parents assigned to them.
		 * @param t the {@code TreeNode} to check whether it requires
		 * a parent.
		 */
		private boolean generateParentIfNecessary(TreeNode t){
			boolean result = false;
			
			// If this is the currently existing root, make a new one
			// and set the supplied node as the child
			if (t.prev() == null){
				TreeNode grandparent = new TreeNode(null,  
													MAX_NUMBER_CHILDREN, 
													clusterSize, 
													t);
				t.setParent(grandparent);
				
				// The list's pointer to the parent will be
				// updated when a traversal is issued.
				result = true;
			}
			return result;
		}
		
		public void setParent(TreeNode parent){
			parentRef = AppContext.getDataManager().createReference(parent);
		}
		
		/**
		 * Removes itself from the data store and recursively
		 * removes its children. 
		 */
		public void clear(){
			DataManager dm = AppContext.getDataManager();
			
			if (getChild() != null && getChild() instanceof TreeNode){
				TreeNode current = (TreeNode) getChild();
				TreeNode next = current.next();
				
				while (current != null){
					current.clear();
					dm.removeObject(current);
					current = next;
					next = next.next();
				}
				
			} else if (getChild() != null && getChild() instanceof ListNode) {
				ListNode current = (ListNode) getChild();
				ListNode next = current.getNext();
				
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
	
	}
	
	
	
	private static class AsynchronousClearTask
	implements Serializable, Task, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<TreeNode> current;
		private ManagedReference<TreeNode> next;
		
		public AsynchronousClearTask(ManagedReference<TreeNode> head){
			current = AppContext.getDataManager().createReference(head.get());
		}
		
		public void run(){
			DataManager dm = AppContext.getDataManager();
			
			// for each TreeNode, delete its children and move on
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
	static class Element
	implements Serializable, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private Object value;
		
		public Element(Object obj){
			value = obj;
		}
		
		public Object getValue(){
			return value;
		}
		
		public void setValue(Object obj){
			value = obj;
		}
	}
	
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
	 * This iterator walks through the {@code ListNode}s which
	 * parent the {@code SubList}s. An iterator for this type
	 * of element is necessary to support the other element
	 * iterator as well as {@code indexOf()} operations.
	 */
	static class ScalableListNodeIterator
	implements Serializable, Iterator<ListNode> {
		
		private TreeNode root;
		private ListNode current;
		private TreeNode currentTreeNode;
		
		public static final long serialVersionUID = 1L;
		
		public ScalableListNodeIterator (TreeNode root){
			this.root = root;
			currentTreeNode = null;
			current = null;
		}
		
		/**
		 * Constructor to be used for cloning.
		 * @param iterator
		 */
		private ScalableListNodeIterator (ScalableListNodeIterator iterator){
			this.root = iterator.getRoot();
			this.current = iterator.getCurrent();
			this.currentTreeNode = iterator.getCurrentTreeNode();
		}
		
		public TreeNode getRoot(){
			return root;
		}
		public ListNode getCurrent(){
			return current;
		}
		public TreeNode getCurrentTreeNode(){
			return currentTreeNode;
		}
		
		public ScalableListNodeIterator clone(ScalableListNodeIterator iterator){
			return new ScalableListNodeIterator(iterator);
		}
		
		/**
		 * Two cases to check: the most common case is that the next
		 * {@code ListNode} is a sibling of the current {@code ListNode}.
		 * Otherwise, we have to locate a cousin, and return if it
		 * exists or not.
		 */
		public boolean hasNext(){
			if (current.getNext() != null){
				return true;
			}
			// Create a clone of the iterator so that we do not
			// lose our position.
			ScalableListNodeIterator clone = clone(this);
			return (clone.next() != null);
		}
		
		
		/**
		 * Returns the next {@code ListNode} in order
		 * from the list
		 */
		public ListNode next(){
			// Check if this is the first element we are returning
			if (current == null){
				current = getListNodeChild(root);
				
				// If there is no problem, link the parent;
				// Otherwise we will return null
				if (current != null){
					currentTreeNode = ((ListNode) current).getParent(); 
				}
				return current;
			}
			
			// If not first time, we have to search for the next ListNode.
			// Try getting the next one in the linked list of ListNodes
			if (((ListNode)current).getNext() != null){
				current = ((ListNode)current).getNext();
				return current;
			} 
			
			// If there are no more ListNodes in the linked list,
			// then we need to find the next TreeNode containing
			// the next ListNode in succession.
			boolean found = false;
			while (!found || currentTreeNode != null){
				currentTreeNode = getNextTreeNode(currentTreeNode);
				current = getListNodeChild(currentTreeNode);
				if (current != null){
					found = true;
				}
			}
			
			return current;
		}
		
		/**
		 * Given a {@code TreeNode}, retrieve the first ListNode
		 * @param t The ancestor {@code TreeNode} to look from
		 * @return the {@code ListNode}, if found, or null otherwise.
		 */
		private ListNode getListNodeChild(TreeNode t){
			ManagedObject node = t.getChild();
			
			// Traverse the children until we find a ListNode,
			// or until we hit a null
			while (node != null && !(node instanceof ListNode)){
				node = ((TreeNode)node).getChild();
			}
			return (ListNode) node;
		}
		
		
		/**
		 * Given a {@code TreeNode}, this method finds the
		 * next {@code TreeNode} to search for. In other words,
		 * if there are no TreeNode siblings, then it tries to
		 * retrieve a sibling of its parent.
		 * @param t
		 * @return
		 */
		private TreeNode getNextTreeNode(TreeNode t){
			// We cannot work with null elements
			if (t == null){
				return null;
			}
			
			// Get sibling if it exists
			if (t.next() != null){
				return t.next();
			}
			
			// Otherwise track down a sibling of the parent.
			// If there is no sibling, then we are at the root.
			// Returning root.next() returns null.
			return t.getParent().next();
		}
		
		public void remove(){
			throw new UnsupportedOperationException("This method is not supported");
		}
	}
		
	
	/**
	 *	This class represents an iterator of the contents of the
	 *	list.  
	 */
	static class ScalableListIterator<ManagedObject>
	implements Serializable, Iterator<ManagedObject> {
		
		private ListNode current;
		private ScalableListNodeIterator listNodeIter;
		private Iterator<ManagedReference<?>> iter;
		
		private long listNodeReferenceValue = -1;
		
		public static final long serialVersionUID = 1L;
		
		public ScalableListIterator(TreeNode root){
			listNodeIter = new ScalableListNodeIterator(root);
			current = (ListNode) listNodeIter.next();
			iter = ((ListNode)listNodeIter.next()).getSubList().getElements().iterator();
		}
		
		/**
		 * Constructor used for cloning purposes. Cloning is
		 * used to check for next elements.
		 */
		private ScalableListIterator(ScalableListIterator<ManagedObject> iterator){
			current = iterator.getCurrent();
			listNodeIter = iterator.getListNodeIterator();
			iter = iterator.getIterator();
		}
		public ScalableListNodeIterator getListNodeIterator(){
			return listNodeIter;
		}
		public ListNode getCurrent(){
			return current;
		}
		public Iterator<ManagedReference<?>> getIterator(){
			return iter;
		}
		
		/**
		 * Retrieve the next element in the collection
		 */
		public ManagedObject next(){
			
			// If at the end of the SubList, get the next ListNode
			// in the ScalableList.
			while (!iter.hasNext()){
				
				// If there are no more ListNodes to traverse through,
				// return null. Otherwise, update the references.
				if (!listNodeIter.hasNext()){
					return null;
				}
				current = (ListNode) listNodeIter.next();
				iter = (current.getSubList().getElements()).iterator();
			}
			listNodeReferenceValue = System.nanoTime(); 
			return (ManagedObject) ((ManagedReference<?>) iter.next()).get();
		}

		/**
		 * Determines if there are subsequent elements in the list.
		 */
		public boolean hasNext(){
			if (iter.hasNext()){
				return true;
			}
			
			// In case the next element is under a neighboring
			// ListNode, clone this iterator to check so that we
			// do not lose our place.
			ScalableListIterator<ManagedObject> clone = clone(this);			
			return (clone.next() != null);
		}
		
		private ScalableListIterator<ManagedObject> clone(
				ScalableListIterator<ManagedObject> toClone){
			return new ScalableListIterator<ManagedObject>(toClone);
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
	 * Node which parents a {@code SubList}. These nodes can be
	 * considered as the leaf nodes of the tree
	 */
	static class ListNode implements ManagedObject, Serializable {
		private int count;
		private ManagedReference<ManagedReference<SubList>> subListRef;
		private ManagedReference<SubList> subListRefLink;
		private ManagedReference<ListNode> nextRef;
		private ManagedReference<ListNode> prevRef;
		private ManagedReference<TreeNode> parentRef;
		
		public static final long serialVersionUID = 1L;
		
		private long dataIntegrityVal;
		
		public ListNode(TreeNode parent, int maxSize){
			SubList sublist = new SubList(maxSize);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRefLink = dm.createReference(sublist);
			subListRef = dm.createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			parentRef = dm.createReference(parent);
		}
		
		public ListNode(TreeNode parent, Object obj){
			SubList sublist = new SubList(obj);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRefLink = dm.createReference(sublist);
			subListRef = dm.createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			parentRef = dm.createReference(parent);
		}
		
		public ListNode(TreeNode parent, int maxSize, Object obj){
			SubList sublist = new SubList(maxSize, obj);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRefLink = dm.createReference(sublist);
			subListRef = dm.createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			parentRef = dm.createReference(parent);
		}
		
		public ListNode(TreeNode parent, int maxSize, ArrayList<Element> list){
			SubList sublist = new SubList(maxSize, list);
			count = sublist.size();
			DataManager dm = AppContext.getDataManager();
			subListRefLink = dm.createReference(sublist);
			subListRef = dm.createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
			parentRef = dm.createReference(parent);
		}
		
		public long getDataIntegrityValue(){
			return dataIntegrityVal;
		}
		
		public void setNext(ListNode ref){
			nextRef = AppContext.getDataManager().createReference(ref);
		}
		
		public void setParent(TreeNode parent){
			parentRef = AppContext.getDataManager().createReference(parent);
		}
		
		public ListNode getNext(){
			return nextRef.get();
		}
		
		public void setPrev(ListNode ref){
			prevRef = AppContext.getDataManager().createReference(ref);
		}
		
		public ListNode getPrev(){
			return prevRef.get();
		}
		
		public int size(){
			return count;
		}
		
		public SubList getSubList(){
			return subListRef.get().get();
		}
		
		public boolean append(Object obj){
			
			boolean result = getSubList().append(obj); 
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
		
		public void insert(int index, Object obj){
			getSubList().insert(index, obj);
			count++;
			dataIntegrityVal = System.nanoTime();
			updateParents(this.getParent(), TreeNode.INCREMENT_SIZE);
			
			// check if we need to split; i.e. if we have exceeded
			// the specified list size.
			if (count > getSubList().MAX_CHILDREN){
				split();
			}
		}

		
		public void clear(){
			count = 0;
			getSubList().clear();
		}
		
		public Object remove(int index){
			Object obj = getSubList().remove(index);
			if (obj != null){
				count--;
				dataIntegrityVal = System.nanoTime();
				updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);
			}
			
			// remove list node if necessary
			checkRemoveListNode(this);
			return obj;
		}
		
		
		public boolean remove(Object obj){
			boolean result = getSubList().remove(obj);
			
			// If a removal took place, then update
			// count information accordingly
			if (result){
				count--;
				dataIntegrityVal = System.nanoTime();
				updateParents(this.getParent(), TreeNode.DECREMENT_SIZE);
			}
			
			// remove list node if it is empty
			checkRemoveListNode(this);
			return result;
		}
		
		public boolean remove(Element e){
			boolean result = getSubList().remove(e);
			if (result){
				count--;
				dataIntegrityVal = System.nanoTime();
			}
			
			// remove list node if necessary
			checkRemoveListNode(this);
			
			return result;
		}
		
		public boolean remove(ManagedReference<Element> ref){
			boolean result = getSubList().remove(ref);
			if (result){
				count--;
			}
			
			// remove list node if necessary
			checkRemoveListNode(this);
			
			return result;
		}
		
		
		/**
		 * If the node is an empty intermediate node, then we
		 * will remove it from the linked list.
		 * Update previous to point to next (doubly) and remove
		 * this object from Data Store
		 */
		private void checkRemoveListNode(ListNode n){
			
			if (n.size() == 0){
				// interior node; connect prev to next
				if (this.getNext() != null && this.getPrev() != null){
					this.getPrev().setNext(this.getNext());
					this.getNext().setPrev(this.getPrev());
				}
				// head node. Update child pointer from parent
				else if (this.getNext() != null){
					this.getParent().setChild(this.getNext());
					this.getNext().setPrev(null);
				}
				// tail node
				else if (this.getPrev() != null){
					this.getPrev().setNext(null);
				} 
				// only child
				else {
					this.getParent().setChild(null);
				}
				
				// Walks up the tree to decrement the parent's size
				updateParents(this.getParent(), TreeNode.DECREMENT_NUM_CHILDREN);
				
				// This is an empty node, so remove it from Data Store.
				AppContext.getDataManager().removeObject(this);
			}
		}
		
		public TreeNode getParent(){
			return parentRef.get();
		}
		
		public void prepend(Object obj){
			insert(0, obj);
		}
		
		
		private void split(){
			ArrayList<ManagedReference<?>> contents = 
				getSubList().getElements();
			AbstractList<ManagedReference<?>> spawned = 
				new ArrayList<ManagedReference<?>>();
			
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
			
			// Create a new ListNode for the moved contents
			ListNode spawnedNode = new ListNode(getParent(), spawned);
			spawnedNode.setNext(this.getNext());
			spawnedNode.setPrev(this);
			this.setNext(spawnedNode);
			
			// Check with parent to see if more splitting is necessary
			getParent().performSplitIfNecessary();
		}
		
		
		/**
		 * Propagates changes in the form of either an increment
		 * or decrement to the parents of the tree.
		 * @param parent
		 * @param mode
		 */
		private void updateParents(TreeNode parent, byte mode){
			if (parent == null){
				return;
			}
			
			switch (mode){
			case TreeNode.INCREMENT_SIZE:
				parent.increment();
				updateParents(parent.getParent(), mode);	
				break;
			case TreeNode.DECREMENT_SIZE:
				parent.decrement();
				updateParents(parent.getParent(), mode);
				break;
			case TreeNode.INCREMENT_NUM_CHILDREN:
				parent.incrementNumChildren();
				break;
				
			case TreeNode.DECREMENT_NUM_CHILDREN:
				parent.decrementNumChildren();
				
				if (parent.getChildCount() == 0){
					// remove this from the tree
					parent = parent.remove();
					
					// since we are removing a TreeNode,
					// check if parent needs to be removed
					// as well.
					updateParents(parent.getParent(), mode);
				}
				break;
					
			default:
				throw new IllegalArgumentException("Supplied mode argument is not recognized; "+
						"expected "+TreeNode.INCREMENT_SIZE+" or "+TreeNode.DECREMENT_SIZE);
			}
			
		}
		
	}
	
	
	
	
	/**
	 * This object represents a partition in the list.
	 * It contains an internal linked-list structure
	 * to store the elements
	 */
	static class SubList implements ManagedObject, Serializable {
		public static final int SIZE_NOT_SET = -1;
		public static final int DEFAULT_MAX_CHILDREN = 10;
		
		private ArrayList<ManagedReference<?>> contents;
		
		private ManagedReference<SubList> nextRef;
		private ManagedReference<SubList> prevRef;
		private ManagedReference<Offset> offsetRef;
		
		private int size = SIZE_NOT_SET;
		public int MAX_CHILDREN = DEFAULT_MAX_CHILDREN;
		public int MAX_CHILDREN_APPEND;
		
		public static final long serialVersionUID = 1L;
		
		public static boolean isLegal(int maxSize){
			return (maxSize > 0);
		}
		
		public SubList(int maxSize, ArrayList<ManagedReference<?>> collection){
			if (isLegal(maxSize)){
				MAX_CHILDREN = maxSize;
			}
			// This sets a limit on the number of elements
			// which can be appended to a node. A value of 2/3 times
			// the maximum capacity allows for some insertions
			// without having to split the node.
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = collection;
			size = contents.size();
			nextRef = null;
			prevRef = null;
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
			
			contents = new ArrayList<ManagedReference<?>>();
			size = contents.size();
			nextRef = null;
			prevRef = null;
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}
		
		public SubList(int maxSize, Object obj){
			if (isLegal(maxSize)){
				MAX_CHILDREN = maxSize;
			}
			// This sets a limit on the number of elements
			// which can be appended to a node. A value of 2/3 times
			// the maximum capacity allows for some insertions
			// without having to split the node.
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = new ArrayList<ManagedReference<?>>();
			append(obj);
			size = contents.size();
			nextRef = null;
			prevRef = null;
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}
		
		public SubList(Object obj){
			MAX_CHILDREN = DEFAULT_MAX_CHILDREN;
			MAX_CHILDREN_APPEND = Math.round((2 * MAX_CHILDREN)/3);
			
			contents = new ArrayList<ManagedReference<?>>();
			append(obj);
			size = contents.size();
			nextRef = null;
			prevRef = null;
			Offset offset = new Offset();
			offsetRef = AppContext.getDataManager().createReference(offset);
		}

		
		public void setOffset(int offset){
			offsetRef.getForUpdate().set(offset);
		}
		
		public int getOffset(){
			return offsetRef.get().get();
		}
		
		public int size(){
			return size;
		}
		
		public ArrayList<ManagedReference<?>> getElements(){
			return contents;
		}
		
		public Object get(int index){
			return contents.get(index).get();
		}
		
		public Object getFirst(){
			return get(0);
		}
		
		public Object getLast(){
			return get(contents.size());
		}
		
		public ManagedReference<SubList> getNext(){
			return nextRef;
		}
		
		public void setNext(SubList listNode){
			nextRef = AppContext.getDataManager().createReference(listNode);
		}
		
		public void setPrev(SubList listNode){
			prevRef = AppContext.getDataManager().createReference(listNode);
		}
		
		public void clear(){
			DataManager dm = AppContext.getDataManager();
			Iterator<ManagedReference<?>> iter = contents.iterator();
			
			// Delete all the elements within the sub list
			while (iter.hasNext()){
				dm.removeObject(iter.next().get());
			}
			contents.clear();
			
			// Remove the offset reference
			dm.removeObject(offsetRef.get());
		}
		
		public Object set(int index, Object obj){
			Element e = new Element(obj);
			return set(index, e);
		}
		
		public Object set(int index, Element e){
			ManagedReference<Element> ref = AppContext.getDataManager().createReference(e);
			ManagedReference<?> old = contents.set(index, ref);
			AppContext.getDataManager().removeObject(old);
			AppContext.getDataManager().removeObject(old.get());
			return old.get();
		}
		
		public ManagedReference<SubList> getPrev(){
			return prevRef;
		}
	
		
		public boolean append(Object obj){
			boolean result = false;
			
			// If it is not yet a ManagedObject, then
			// create a new Element to make it a ManagedObject
			ManagedReference<?> ref = null;
			if (! (obj instanceof ManagedObject)){
				Element e = new Element(obj);
				ref = AppContext.getDataManager().createReference(e);
			} else {
				ref = AppContext.getDataManager().createReference(obj);
			}
			result = contents.add(ref);
			size = contents.size();
			
			return result;
		}

		
		public int lastIndexOf(Object o){
			Iterator<ManagedReference<?>> iter = contents.iterator();
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
		
		public int lastIndexOf(ManagedReference<Element> ref){
			return contents.lastIndexOf(ref);
		}

		
		public void insert(int index, Object obj){
			if (index < 0){
				throw new IndexOutOfBoundsException("Supplied index cannot be less than 0");
			}
			ManagedReference<Element> ref = null;
			if (!(ref.get() instanceof ManagedObject)){
				Element e = new Element(obj);
				AppContext.getDataManager().createReference(e);
			} else {
				AppContext.getDataManager().createReference(obj);
			}
			contents.add(index, ref);
			size = contents.size();
		}
		
		
		public int indexOf(Object o){
			Iterator<ManagedReference<?>> iter = contents.iterator();
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
		
		public int indexOf(ManagedReference<Element> ref){
			return contents.indexOf(ref);
		}
		
		public Object remove(int index){
			if (index > contents.size()-1){
				throw new IndexOutOfBoundsException("The index, "+index+
						", is out of bounds");
			}
			
			ManagedReference<?> removed = contents.remove(index);
			if (removed.get() instanceof Element){
				AppContext.getDataManager().removeObject(removed.getForUpdate());
			}
			this.size = contents.size();
			return removed;
		}
		
		
		public boolean remove(ManagedReference<Element> ref){
			boolean success = false;
			success = contents.remove(ref);
			if (success && ref.get() instanceof Element){
				AppContext.getDataManager().removeObject(ref.getForUpdate());
			}
			return success;
		}
	
		
		public boolean remove(Object obj){
			Iterator<ManagedReference<?>> iter = contents.iterator();
			ManagedReference<?> current = null;
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
		
		
		public Object removeLast(){
			return remove(contents.size()-1);
		}
		
	}
}
