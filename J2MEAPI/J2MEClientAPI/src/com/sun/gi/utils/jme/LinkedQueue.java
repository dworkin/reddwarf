/*
 * LinkedQueue.java
 *
 * Created on January 31, 2006, 9:39 AM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 * Minimal LinkedList queue implementation
 * @author as93050
 */
public class LinkedQueue {

    private Entry header = new Entry(null, null, null);
    private int size = 0;

    /**
     * Constructs an empty list.
     */
    public LinkedQueue() {
        header.next = header.previous = header;
    }
   
    /**
     * Removes and returns the first element from this list.
     *
     * @return the first element from this list.
     * @throws    NoSuchElementException if this list is empty.
     */
    public synchronized Object dequeue() {
	Object first = header.next.element;
	remove(header.next);
	return first;
    }

    
    /**
     * Appends the given element to the end of this list.  (Identical in
     * function to the <tt>add</tt> method; included only for consistency.)
     * 
     * @param o the element to be inserted at the end of this list.
     */
    public synchronized void enqueue(Object o) {
	Entry newEntry = new Entry(o, header, header.previous);
	newEntry.previous.next = newEntry;
	newEntry.next.previous = newEntry;
	size++;	
    }

    
    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list.
     */
    public int size() {
	return size;
    }

    
    
    /**
     * Removes all of the elements from this list.
     */
    public void clear() {
        header.next = header.previous = header;
	size = 0;
    }

    private void remove(Entry e) {
	if (e == header)
	    return;
	e.previous.next = e.next;
	e.next.previous = e.previous;
	size--;
    }

    private static class Entry {
	Object element;
	Entry next;
	Entry previous;

	Entry(Object element, Entry next, Entry previous) {
	    this.element = element;
	    this.next = next;
	    this.previous = previous;
	}
    }

    
}
