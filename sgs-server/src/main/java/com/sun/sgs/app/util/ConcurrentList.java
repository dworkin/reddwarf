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
import java.util.NoSuchElementException;

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
public class ConcurrentList<E> 
extends AbstractCollection<E>
implements 	ManagedObject, Serializable {

	public static final long serialVersionUID = 1L;
	
	/**
	 * A reference to the head node of the list.
	 */
	private ManagedReference<ManagedReference<ListNode>> headRef;
	private ManagedReference<ListNode> headRefLink;
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
	
	
	/*
	 * 	//////////////////////////////
			IMPLEMENTATION	
		/////////////////////////////
	*/
	
	/**
	 * Constructor which creates a {@code ConcurrentList} object
	 * with the resolution action supplied as a parameter.
	 * @param resolution Whether the {@code ConcurrentList} should
	 * perform resolution. True if so, and false otherwise. 
	 */
	public ConcurrentList(){
		headRef = null;
		tailRef = null;
		headRefLink = null;
		tailRefLink = null;
	}
	
	/**
	 * Constructor which creates a {@code ConcurrentList} object
	 * with the resolution and cluster size supplied as a parameter.
	 * @param clusterSize The size of each partitioned list. This
	 * value must be a positive integer (larger than 0).
	 * @param resolution Whether the {@code ConcurrentList} should
	 * perform resolution. True if so, and false otherwise.
	 */
	public ConcurrentList(int clusterSize){
		headRef = null;
		tailRef = null;
		headRefLink = null;
		tailRefLink = null;
		
		if (clusterSize < 1){
			throw new IllegalArgumentException("Cluster size must "+
					"be an integer larger than 0");
		}
		this.clusterSize = clusterSize; 
	}
	
	
	/**
	 * Appends an {@code Object} to the {@code ConcurrentList} by first
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
	 * prepends it to the {@code ConcurrentList}. That is, the
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
		AppContext.getTaskManager().scheduleTask(new AsynchronousClearTask(headRef));
		
		// Create a new ListNode and link everything to it.
		ListNode n = new ListNode(clusterSize);
		headRefLink = AppContext.getDataManager().createReference(n);
		headRef = AppContext.getDataManager().createReference(headRefLink);
		tailRefLink = AppContext.getDataManager().createReference(n);
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
	 * {@code ConcurrentList} constructor.
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
	 * @return A list of the contents in the ConcurrentList
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
		if (obj == null || !(obj instanceof ConcurrentList)){
			return false;
		}
		
		ConcurrentList<E> list = (ConcurrentList<E>) obj;
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
	 * Uses the ConcurrentListIterator to traverse the list and
	 * return the node referred to by the index parameter.
	 * 
	 * @param index The index of the desired {@code Object}
	 * @return The node containing the provided index, or null if
	 * the index exceeds the node's indices.
	 */
	private ListNode getNode(int index){
		ConcurrentListIterator iter = new ConcurrentListIterator(this);
		ListNode n = null;
		boolean found = false;
		
		// search through the linked list for the node containing
		// the list with the specified index
		while (iter.hasNext()){
			n = iter.next();
			
			if (n == null){
				throw new NullPointerException("Encountered a null ListNode object");
			}
			
			/*
				check if the index is contained within the
			 	upper index of the current node. If so, then
			 	this node contains the child we are looking for,
			 	so we declare we found the node and break.
			 	
			 	We update the node's absolute lower index so that
			 	when we return it, we can use it to obtain a
			 	relative offset of the internal element. 
			*/
			if (index < iter.getAbsoluteUpperIndex()){
				n.getSubList().setOffset(iter.getAbsoluteLowerIndex());
				found = true;
				break;
			}
		}
		if (!found){
			throw new NoSuchElementException("Could not find the node containing "+
					"the specified index.");
		}
		return n;
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
		ListNode n = new ListNode(clusterSize, obj);
		headRefLink = AppContext.getDataManager().createReference(n);
		tailRefLink = AppContext.getDataManager().createReference(n);
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

	private static class AsynchronousClearTask
	implements Serializable, Task, ManagedObject {
		
		public static final long serialVersionUID = 1L;
		
		private ManagedReference<ListNode> current;
		private ManagedReference<ListNode> next;
		
		public AsynchronousClearTask(ManagedReference<ManagedReference<ListNode>> head){
			current = head.get();
		}
		
		public void run(){
			DataManager dm = AppContext.getDataManager();
			
			// for each ListNode, delete its children and move on
			next = current.get().getNext();
			current.get().clear();
			dm.removeObject(current);
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
		
		public ConcurrentListIterator(ConcurrentList<?> list){
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
		 * Removal of nodes is achieved by the ConcurrentList API.
		 */
		public void remove(){
			throw new UnsupportedOperationException(
					"Removal of nodes is achieved through the ConcurrentList API");
		}
	}
	
	
	
	
	static class ListNode implements ManagedObject, Serializable {
		private int count;
		private ManagedReference<ManagedReference<SubList>> subListRef;
		private ManagedReference<SubList> subListRefLink;
		private ManagedReference<ListNode> nextRef;
		private ManagedReference<ListNode> prevRef;
		
		public static final long serialVersionUID = 1L;
		
		private long dataIntegrityVal;
		
		public ListNode(int maxSize){
			SubList sublist = new SubList(maxSize);
			count = sublist.size();
			subListRefLink = AppContext.getDataManager().createReference(sublist);
			subListRef = AppContext.getDataManager().createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
		}
		
		public ListNode(Object obj){
			SubList sublist = new SubList(obj);
			count = sublist.size();
			subListRefLink = AppContext.getDataManager().createReference(sublist);
			subListRef = AppContext.getDataManager().createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
		}
		
		public ListNode(int maxSize, Object obj){
			SubList sublist = new SubList(maxSize, obj);
			count = sublist.size();
			subListRefLink = AppContext.getDataManager().createReference(sublist);
			subListRef = AppContext.getDataManager().createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
		}
		
		public ListNode(int maxSize, ArrayList<Element> list){
			SubList sublist = new SubList(maxSize, list);
			count = sublist.size();
			subListRefLink = AppContext.getDataManager().createReference(sublist);
			subListRef = AppContext.getDataManager().createReference(subListRefLink);
			nextRef = null;
			prevRef = null;
			dataIntegrityVal = System.nanoTime();
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
			boolean result = getSubList().append(obj); 
			if (result){
				count++;
				dataIntegrityVal = System.nanoTime();
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
		
		
		public void prepend(Object obj){
			
			// If the sub list is full, then we create a new ListNode
			// in front of ourselves and add to it. Otherwise,
			// simply add to index 0 of the current (unfilled) list
			if (count == getSubList().MAX_CHILDREN){
				// TODO: eventually support a tree structure instead of
				// simply a two-level skip list.
				ListNode newNode = new ListNode(obj);
				DataManager dm = AppContext.getDataManager();
				newNode.setNext(dm.createReference(this));
				this.setPrev(dm.createReference(newNode));
			} else {
				insert(0, obj);
			}
		}
		
		
		private void split(){
			ArrayList<ManagedReference<?>> contents = getSubList().getElements();
			AbstractList<ManagedReference<?>> spawned = 
				new ArrayList<ManagedReference<?>>();
			
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
			
			// Create a new node
			ListNode spawnedNode = new ListNode(spawned);
			DataManager dm = AppContext.getDataManager();
			spawnedNode.setNext(dm.createReference(this.getNext().get()));
			spawnedNode.setPrev(dm.createReference(this));
			this.setNext(dm.createReference(spawnedNode));
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
				// TODO: eventually support a tree structure instead of
				// simply a two-level skip list.
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
