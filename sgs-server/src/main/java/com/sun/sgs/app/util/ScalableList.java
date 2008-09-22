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
import java.util.List;
import java.util.ListIterator;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

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
		this.clusterSize = clusterSize; 
	}
	
	
	/**
	 * Appends an {@code Object} to the {@code ScalableList} by first
	 * creating an {@code Object} object.
	 * @param obj The {@code Object} to be appended to the list.
	 */
	public boolean add(Object obj){
		if (obj == null){
			throw new IllegalArgumentException("Element cannot be null");
		}
		
		// first item to add into the list
		if (tailRef == null){
			return addFirstEntry(obj);
		}
		// otherwise, add it at the end since no index
		return tailRef.get().get().append(obj);
	}
	
	
	/**
	 * Converts the {@code Object} into an {@code Object} and
	 * prepends it to the {@code ScalableList}. That is, the
	 * {@code Object} is added to the front of the list.
	 * @param obj The {@code Object} to be added to the beginning 
	 * of the list.
	 */
	public void prepend(Object obj){
		// first item to add into the list
		if (headRef == null){
			addFirstEntry(obj);
			return;
		}
		// otherwise, add it to the beginning
		headRef.get().get().prepend(obj);
	}
	
	
	/**
	 * Adds the supplied {@code Object} to a specified location
	 * in the list, provided by the index. If the index is
	 * out of bounds, then an {@code IndexOutOfBoundsException}
	 * is thrown. 
	 * @param index The location to add the {@code Object}.
	 * @param obj The {@code Object} to add.
	 */
	public void add(int index, Object obj){
		isValidIndex(index);
		if (obj == null){
			throw new IllegalArgumentException("Element cannot be null");
		}
		if (headRef == null){
			addFirstEntry(obj);
			return;
		}
		
		// otherwise, add it to the specified index.
		// This requires a search of the list nodes.
		ListNode n = getNode(index);
		n.insert(index - n.getSubList().getOffset(), obj);
	}
	
	/**
	 * Adds all the elements from the {@code Collection} into the
	 * list at the specified index.
	 * @param index The index which to start adding the elements.
	 * @param c the collection of elements to add.
	 * @return True if the operation occurred successfully, and
	 * false otherwise.
	 */
	public boolean addAll(int index, Collection<?> c){
		Iterator<?> iter = c.iterator();
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
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n;
		int index = -1;
		
		while (iter.hasNext()){
			n = iter.next();
			listIndex += iter.getAbsoluteLowerIndex();
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
	public int lastIndexOf(Object o){
		int listIndex = -1;
		int absIndex = -1;
		int index = -1;
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n = null;
		
		while (iter.hasNext()){
			n = iter.next();
			listIndex += iter.getAbsoluteLowerIndex();
			index = n.getSubList().lastIndexOf(o);
			
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
		
		SubList list = n.getSubList();
		Object obj = list.remove(index - list.getOffset());

		// Adjust the head or tail if necessary. If the element
		// has no siblings, then we simply leave it.
		if (n.size() == 0){
			if (n.getPrev() == null && n.getNext() != null){
				
				// update the head
				headRefLink = n.getNext();
			} else if (n.getPrev() != null && n.getNext() == null){
				
				// update the tail
				tailRefLink = n.getPrev();
			}
		}
		return obj;
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
		SubList tail = tailRefLink.get().getSubList();
		if (tail == null){
			return null;
		}
		return tail.removeLast(); 
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
	 * Returns the number of intermediate nodes which contain a
	 * segment of the collection. Each node's size is determined
	 * by the {@code clusterSize} argument supplied in the
	 * {@code ScalableList} constructor.
	 * @return the number of nodes (partitions) of the list.
	 */
	public int getListNodeCount(){
		int count = 0;
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		
		while (iter.hasNext()){
			iter.next();
			count++;
		}
		return count;
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
	 * Removes the elements between the provided indices 
	 * (inclusive) of the list.
	 * 
	 * @param fromIndex
	 * @param toIndex
	 */
	public void removeRange(int fromIndex, int toIndex){
		if (fromIndex > toIndex){
			throw new IllegalArgumentException(
					"Lower index cannot be greater than upper index");
		}
		
		/*
			Since the elements may not be continuous,
		 	i.e. they exist in different nodes,
			we should instead remove them consecutively.
		 	Since a remove automatically shifts the
		 	index, we remove at the same index for
		 	(toIndex - fromIndex) times.
		 */
		int times = toIndex - fromIndex;
		for (int i = 0 ; i <= times ; i++){
			remove(fromIndex);
		}
	}
	
	/**
	 * Obtains a list of the elements between the specified
	 * indices. This operation requires that all elements
	 * are collected and the sublist generated thereafter.
	 * The indices of the elements in the resulting {@code List} 
	 * will be from 0 to the length of the list. 
	 * @param fromIndex the starting index.
	 * @param toIndex the ending index.
	 * @return the {@code List} containing the specified range
	 * of elements.
	 */
	public List<E> subList(int fromIndex, int toIndex){
		return getAllContents().subList(fromIndex, toIndex);
	}
	
	/**
	 * Retrieves the {@code Iterator} for the list.
	 */
	public Iterator<E> iterator(){
		return getAllContents().iterator();
	}
	
	/**
	 * Retrieves a {@code ListIterator} for the list.
	 * @return a {@code ListIterator} for the list.
	 */
	public ListIterator<E> listIterator(){
		return getAllContents().listIterator();
	}
	
	/**
	 * Retrieves a {@code ListIterator} for the list, starting
	 * at the provided index.
	 * @param index the index to start the iterator
	 * @return a {@code ListIterator} with a starting index
	 * as the supplied index value.
	 */
	public ListIterator<E> listIterator(int index){
		return getAllContents().listIterator(index);
	}
	
	/**
	 * This method traverses through the list and
	 * returns a list of all the contents. Since the
	 * elements are partitioned into nodes, an
	 * iterator needs to go through and collect each
	 * sub-list.
	 * @return A list of the contents in the ScalableList
	 */
	private ArrayList<E> getAllContents(){
		ArrayList<ManagedReference<?>> list = 
			new ArrayList<ManagedReference<?>>();
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		
		while (iter.hasNext()){
			list.addAll(iter.next().getSubList().getElements());
		}
		return (ArrayList<E>) list;
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
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n = null;
		boolean removed = false;
		
		while (iter.hasNext()){
			n = iter.next();
			removed = n.getSubList().remove(obj);
			if (removed){
				break;
			}
		}
		
		return removed;
	}
	
	/**
	 * Removes all entries of the supplied argument from
	 * the list.
	 * @param obj the entry to remove.
	 * @return whether any such entry was removed; true if so, and
	 * false otherwise.
	 */
	public boolean removeAll(Object obj){
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n = null;
		boolean removed = false;
		
		while (iter.hasNext()){
			n = iter.next();
			removed = n.getSubList().remove(obj);
		}
		
		return removed;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isEmpty(){
		return (this.size() == 0);
	}
	
	
	/**
	 * Does a quick check to see if a new parent is required,
	 * and then performs the search for the desired ListNode.
	 * @param index
	 * @return
	 */
	private ListNode getNode(int index){
		TreeNode current = headTreeNodeRef.get();
		
		// Check ancestors in case there is a more current parent node
		// to establish as the head node
		while (current.getParent() != null){
			current = current.getParent();
		}
		
		// Recursive method to eventually return ListNode
		// containing the desired index.
		return search(current, 0, index);
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
	private boolean addFirstEntry(Object obj){
		if (obj == null){
			throw new IllegalArgumentException("Element cannot be null");
		}
		TreeNode t = new TreeNode(null, maxChildSize, clusterSize, obj);
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
	public ManagedReference<ListNode> getHeadRef(){
		return headRefLink;
	}
	
	/**
	 * Returns a reference to the tail {@code ListNode} of the list, which
	 * represents a segment of the overall list.
	 * @return the tail node
	 */
	public ManagedReference<ListNode> getTailRef(){
		return tailRefLink;
	}
	
	/**
	 * Retrieves the size of the list.
	 * @return the size of the list.
	 */
	public int size(){
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n = headRefLink.get();
		int count = 0;
		
		// Iterate through the list of ListNodes
		while (iter.hasNext()){
			n = iter.next();
			count += n.size();
		}
		return count;
	}
	
	
//.......................................................................................	
	
	static class TreeNode
	implements ManagedObject, Serializable {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<TreeNode> nextRef;
		private ManagedReference<TreeNode> prevRef;
		private ManagedReference<ManagedObject> childRef;
		private ManagedReference<TreeNode> parentRef;
		
		private int size = 0;
		private int MAX_NUMBER_CHILDREN = 5;
		private int clusterSize = 0;
		private int childrenCount = 0;
		
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
		private TreeNode(TreeNode parent, int maxNumChildren, int clusterSize, ListNode child){
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
			
			while (child != null){
				size += child.size();
				childrenCount++;
				child = child.getNext().get();
			}
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
		private TreeNode(TreeNode parent, int maxNumChildren, int clusterSize, TreeNode child){
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
			
			while (child != null){
				size += child.size();
				childrenCount++;
				child = child.next();
			}
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
			MAX_NUMBER_CHILDREN = maxNumChildren;
			nextRef = null;
			this.clusterSize = clusterSize;
			ListNode n = new ListNode(this, clusterSize, obj);
			size = n.size();
			DataManager dm = AppContext.getDataManager();
			childRef = dm.createReference((ManagedObject) n);
			parentRef = dm.createReference(parent);
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
		
		public int size(){
			return size;
		}
		
		public void incrementSize(){
			size++;
		}
		
		public TreeNode getParent(){
			return parentRef.get();
		}
		
		public void setNext(TreeNode t){
			nextRef = AppContext.getDataManager().createReference(t);
		}
		
		public void performSplitIfNecessary(){
			TreeNode newNode = null;
			DataManager dm = AppContext.getDataManager();
			ManagedObject tmp = (ManagedObject) getChild();
			ManagedObject prev = null;
			
			// Increment size, because if we are calling this method, then
			// we were guaranteed to have added a new element
			childrenCount++;
			
			// Check if we need to split, and if so, do it based
			// on the type of element contained (ListNode or TreeNode)
			if (childrenCount > MAX_NUMBER_CHILDREN){
				generateParentIfNecessary(this);
				
				int halfway = Math.round(childrenCount / 2);
				childrenCount = halfway;
				
				newNode = createSibling(tmp, prev, halfway);
				
				// Create the new tree node and link accordingly
				newNode.setNext(this.nextRef.get());
				newNode.setPrev(this);
				nextRef = dm.createReference(newNode);
				
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
		 * @param current the {@code ManagedObject} which needs to
		 * be split and placed under a new parent.
		 * @param prev the element previous to the current element
		 * @param halfway a value representing an index approximately
		 * half the length of the contents
		 * @return
		 */
		private TreeNode createSibling(ManagedObject current, ManagedObject prev, int halfway){
			TreeNode newNode = null;
			
			if (current instanceof ListNode){
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = current;
					current = (ManagedObject) ((ListNode)current).getNext();
				}
				newNode = new TreeNode(this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(ListNode) current);
				((ListNode)prev).setNext(null);
				
			} else if (current instanceof TreeNode){
				
				// Get the approximate middle element
				for (int i=0 ; i<halfway ; i++){
					prev = current;
					current = ((TreeNode)current).next();
				}
				newNode = new TreeNode( this.getParent(), 
										MAX_NUMBER_CHILDREN, 
										clusterSize, 
										(TreeNode) current);
				((TreeNode)prev).setNext(null);
				
			} else {
				throw new IllegalStateException("Attempting to split a node that is neither "+
						"a TreeNode or ListNode");
			}
			
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
			if (t.prev() == null){
				TreeNode grandparent = new TreeNode(null, 
													MAX_NUMBER_CHILDREN, 
													clusterSize, 
													t);
				ManagedReference<TreeNode> parent = 
					AppContext.getDataManager().createReference(grandparent);
				t.setParent(parent);
				
				// The list's pointer to the parent will be
				// updated when a traversal is issued.
				result = true;
			}
			return result;
		}
		
		public void setParent(ManagedReference<TreeNode> ref){
			parentRef = ref;
		}
		
		public void clear(){
			DataManager dm = AppContext.getDataManager();
			
			if (getChild() instanceof TreeNode){
				TreeNode current = (TreeNode) getChild();
				TreeNode next = current.next();
				
				while (current != null){
					current.clear();
					dm.removeObject(current);
					current = next;
					next = next.next();
				}
				
			} else if (getChild() instanceof ListNode) {
				ListNode current = (ListNode) getChild();
				ListNode next = current.getNext().get();
				
				while (current != null){
					current.clear();
					dm.removeObject(current);
					current = next;
					next = next.getNext().get();
				}
				
			}
			// self destruct
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
	

	
	static class ConcurrentListIterator
	implements Serializable, Iterator<ListNode> {
		
		private ListNode current;
		private ManagedReference<ListNode> head;
		private int upperIndex;
		private int lowerIndex;
		private long listNodeReferenceValue;
		
		public static final long serialVersionUID = 1L;
		
		public ConcurrentListIterator(ScalableList<?> list){
			upperIndex = 0;
			lowerIndex = 0;
			current = null;
			head = list.getHeadRef();
		}
		
		public ListNode next(){
			// get the first element
			if (current == null && head != null){
				current = head.get();
				head = null;
				
			} else if (current == null && head == null){ 
				return null;
				
			} else {
				current = current.getNext().get();
			}
			// store the data integrity value to detect changes
			listNodeReferenceValue = current.getDataIntegrityValue();
			
			// update the index information based on where we are.
			lowerIndex = upperIndex;
			upperIndex += current.size();
			return current;
		}
		
		public boolean hasNext(){
			return (current.getNext() != null);
		}
		
		public int getAbsoluteUpperIndex(){
			return upperIndex;
		}
		
		public int getAbsoluteLowerIndex(){
			return lowerIndex;
		}
		
		public ListNode previous(){
			if (current == null){
				return null;
			}
			current = current.getPrev().get();
			
			listNodeReferenceValue = System.nanoTime(); 
			upperIndex -= lowerIndex - 1;
			lowerIndex -= current.size();
			return current;
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
		
		public void setNext(ManagedReference<ListNode> ref){
			nextRef = ref;
		}
		
		public ManagedReference<ListNode> getNext(){
			return nextRef;
		}
		
		public void setPrev(ManagedReference<ListNode> ref){
			prevRef = ref;
		}
		
		public ManagedReference<ListNode> getPrev(){
			return prevRef;
		}
		
		public int size(){
			return count;
		}
		
		public SubList getSubList(){
			return subListRef.get().get();
		}
		
		public boolean append(Object obj){
			getParent().incrementSize();
			
			boolean result = getSubList().append(obj); 
			if (result){
				count++;
				dataIntegrityVal = System.nanoTime();
			}
			
			// check if we need to split; i.e. if we have exceeded
			// the specified list size.
			if (count > getSubList().MAX_CHILDREN){
				split();
			}
			
			return result;
		}
		
		public void insert(int index, Object obj){
			getSubList().insert(index, obj);
			count++;
			dataIntegrityVal = System.nanoTime();
			
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
			}
			
			// remove list node if necessary
			checkRemoveListNode(this);
			
			return obj;
		}
		
		
		public boolean remove(Object obj){
			boolean result = getSubList().remove(obj);
			if (result){
				count--;
				dataIntegrityVal = System.nanoTime();
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
				if (this.getNext() != null && this.getPrev() != null){
					this.getPrev().get().setNext(this.getNext());
					this.getNext().get().setPrev(this.getPrev());
				}
				
				// This is an empty node, so remove it from Data Store.
				AppContext.getDataManager().removeObject(this);
			}
		}
		
		public TreeNode getParent(){
			return parentRef.get();
		}
		
		public void prepend(Object obj){
			getParent().incrementSize();
			
			// If the sub list is full, then we create a new ListNode
			// in front of ourselves and add to it. Otherwise,
			// simply add to index 0 of the current (unfilled) list
			if (count == getSubList().MAX_CHILDREN){
				ListNode newNode = new ListNode(getParent(), obj);
				DataManager dm = AppContext.getDataManager();
				newNode.setNext(dm.createReference(this));
				this.setPrev(dm.createReference(newNode));
				
				// Check with the parent to see if splitting is necessary
				getParent().performSplitIfNecessary();
			} else {
				insert(0, obj);
			}
		}
		
		
		private void split(){
			ArrayList<ManagedReference<?>> contents = getSubList().getElements();
			AbstractList<ManagedReference<?>> spawned = 
				new ArrayList<ManagedReference<?>>();
			getParent().incrementSize();
			
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
			
			// Create a new node
			ListNode spawnedNode = new ListNode(getParent(), spawned);
			DataManager dm = AppContext.getDataManager();
			spawnedNode.setNext(dm.createReference(this.getNext().get()));
			spawnedNode.setPrev(dm.createReference(this));
			this.setNext(dm.createReference(spawnedNode));
			
			// Check with parent to see if more splitting is necessary
			getParent().performSplitIfNecessary();
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
			
			if (size == MAX_CHILDREN_APPEND){
				SubList newNode = new SubList(obj);
				this.setNext(newNode);
				newNode.setPrev(this);
				result = true;
			} else {
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
			}
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
