/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Iterator;

/**
 * A simple implementation of a persistent queue.  When an element is
 * removed from the queue, that element is also removed from the data
 * store.
 */
public class ManagedQueue<E>
    extends AbstractQueue<E>
    implements ManagedObject, Serializable //, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The head of the queue, or null. */
    private ManagedReference headRef;
    /** The tail of the queue, or null. */
    private ManagedReference tailRef;

    /**
     * A queue entry consisting of a reference to the element object
     * and a reference to the next entry in the queue.
     */
    private static class Entry<E> implements ManagedObject, Serializable {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	/** The element. */
	ManagedReference elementRef;
	/** The reference to the next queue entry, or null. */
	ManagedReference nextEntryRef = null;

	/** Constructs an entry with the specified element. */
	Entry(E element) {
	    // TBD: allow Serializable, but non-ManagedObjects as elements?
	    if (!(element instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "element does not implement Serializable");
	    } else if (!(element instanceof ManagedObject)) {
		throw new IllegalArgumentException(
		    "element does not implement ManagedObject");
	    }
	    elementRef = AppContext.getDataManager().
		createReference((ManagedObject) element);
	}

	@SuppressWarnings("unchecked")
	/**
	 * Returns the element associated with this entry.
	 */
	E getElement() {
	    return (E) elementRef.get(ManagedObject.class);
	}
   }

    /** {@inheritDoc} */
    public boolean offer(E o) {
	if (o == null) {
	    throw new NullPointerException("null element");
	}
	Entry<E> entry = new Entry<E>(o);
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);
	ManagedReference entryRef = dataManager.createReference(entry);
	if (tailRef == null) {
	    headRef = tailRef = entryRef;
	} else {
	    Entry tail = tailRef.getForUpdate(Entry.class);
	    tail.nextEntryRef = entryRef;
	    tailRef = entryRef;
	}
	return true;
    }

    /** {@inheritDoc} */
    public E peek() {
	return
	    (headRef != null) ?
	    getHead().getElement() :
	    null;
    }

    /** {@inheritDoc} */
    public E poll() {
	E element = null;
	if (headRef != null) {
	    Entry<E> head = getHead();
	    element = head.getElement();
	    headRef = head.nextEntryRef;
	    if (headRef == null) {
		// last element removed
		tailRef = null;
	    }
	    head.removeObject();
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.removeObject(head);
	    dataManager.removeObject(element);
	}
	return element;
    }

    /** {@inheritDoc} */
    public int size() {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */ 
    public boolean isEmpty() {
	return headRef == null;
    }
    
    /** {@inheritDoc} */
    public Iterator<E> iterator() {
	throw new AssertionError("not implemented");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation clears all elements from the queue,
     * thus removing all elements from the data store.
     */
    public void removingObject() {
	clear();
    }

    @SuppressWarnings("unchecked")
    private Entry<E> getHead() {
	return headRef.get(Entry.class);
    }
}
