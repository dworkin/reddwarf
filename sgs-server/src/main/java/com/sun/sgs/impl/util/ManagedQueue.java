/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.Objects;
import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Iterator;

/**
 * A simple implementation of a persistent queue.  When an element is
 * removed from the queue, that element is also removed from the data
 * store.
 *
 * <p>Note: The {@code size} and {@code iterator} methods are not
 * supported.
 *
 * <p>Note: If a given instance of {@code ManagedQueue} contains a large
 * number of elements, invoking the {@code clear} method on the queue may
 * be a lengthy operation.  Therefore, the queue should either contain a
 * smaller number of elements, or elements should be removed from the queue
 * a few at at time.
 *
 * @param <E>	the type for elements in the queue
 *
 * TODO: The element type should not be required to be a managed object,
 * but should just be serializable.  Should it handle both?
 */
public class ManagedQueue<E>
    extends AbstractQueue<E>
    implements Serializable, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The head of the queue, or null. */
    private ManagedReference<Entry<E>> headRef = null;
    /** The tail of the queue, or null. */
    private ManagedReference<Entry<E>> tailRef = null;

    /**
     * A queue entry consisting of a reference to the element object
     * and a reference to the next entry in the queue.
     */
    private static class Entry<E> implements ManagedObject, Serializable {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	/** The element. */
	final ManagedReference<E> elementRef;
	/** The reference to the next queue entry, or null. */
	ManagedReference<Entry<E>> nextEntryRef = null;

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
	    elementRef = AppContext.getDataManager().createReference(element);
	}

	/**
	 * Returns the element associated with this entry.
	 */
	E getElement() {
	    return elementRef.get();
	}
   }

    /** {@inheritDoc} */
    @Override
    public boolean offer(E o) {
	if (o == null) {
	    throw new NullPointerException("null element");
	}
	Entry<E> entry = new Entry<E>(o);
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);
	ManagedReference<Entry<E>> entryRef =
	    dataManager.createReference(entry);
	if (tailRef == null) {
	    headRef = entryRef;
            tailRef = entryRef;
	} else {
	    Entry<E> tail = tailRef.getForUpdate();
	    tail.nextEntryRef = entryRef;
	    tailRef = entryRef;
	}
	return true;
    }

    /** {@inheritDoc} */
    @Override
    public E peek() {
	return
	    (headRef != null) ?
	    getHead().getElement() :
	    null;
    }

    /** {@inheritDoc} */
    @Override
    public E poll() {
	E element = null;
	if (headRef != null) {
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.markForUpdate(this);
	    Entry<E> head = getHeadForUpdate();
	    element = head.getElement();
	    headRef = head.nextEntryRef;
	    if (headRef == null) {
		// last element removed
		tailRef = null;
	    }
	    dataManager.removeObject(head);
	    dataManager.removeObject(element);
	}
	return element;
    }

    /** {@inheritDoc}
     *
     * <p> This method is not supported.
     */
    @Override
    public int size() {
	throw new UnsupportedOperationException("size not supported");
    }

    /** {@inheritDoc} */ 
    @Override
    public boolean isEmpty() {
	return headRef == null;
    }
    
    /** {@inheritDoc}
     *
     * <p> This method is not supported.
     */
    @Override
    public Iterator<E> iterator() {
	throw new UnsupportedOperationException("iterator not supported");
    }

    /* -- Implement Object.hashCode -- */

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	DataManager dataManager = AppContext.getDataManager();
	return dataManager.createReference(this).getId().hashCode();
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof ManagedQueue)) {
            return false;
        }
        ManagedQueue<E> d = Objects.uncheckedCast(o);
        DataManager dm = AppContext.getDataManager();
        return dm.createReference(this).getId().equals(
                dm.createReference(d).getId());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	try {
	    return getClass().getName() + '@' + Integer.toHexString(hashCode());
	} catch (TransactionNotActiveException e) {
	    return super.toString();
	}
    }

    /* -- Implement ManagedObjectRemoval -- */

    /**
     * {@inheritDoc}
     *
     * <p>This implementation clears all elements from the queue,
     * thus removing all elements from the data store.
     *
     * TODO: For queues with a large number of elements, removing the
     * enqueued elements should be performed in a separate task (or
     * tasks).
     */
    public void removingObject() {
	clear();
    }

    /* -- Other methods -- */
    
    private Entry<E> getHead() {
	return headRef.get();
    }
    
    private Entry<E> getHeadForUpdate() {
	return headRef.getForUpdate();
    }
}
