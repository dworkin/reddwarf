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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

/**
 * A scalable {@code Deque} implementation.  This implementation
 * supports concurrent writers at each of the deque's ends.  The
 * internal representation of the deque is segmented so that
 * operations do not access the data of entire deque.  Note that
 * concurrent read and write operations on the <i>same</i> end of the
 * deque will still cause contention.
 *
 * <p>
 *
 * Developers should use this as a drop-in replaced for any {@code
 * Deque} or {@link Queue} implementation 
 * if the size of the data
 * structure will be more than a small number of elements, or if the
 * data structure needs to support concurrent writers for putting and
 * removing.  A standard {@code Deque} or {@code Queue} implementation
 * will likely perform better that an instance of this class if the
 * number of elements is small, or if the usage instance happens
 * entirely during the lifetime of a task and the instance is never
 * persisted.
 *
 * <p> 
 *
 * This class provides one parameter for tuning performance.  The
 * {@code supportsConcurrentIterators} constructor parameter
 * determines how a deque's iterators will behavior when the deque is
 * concurrently modified during traversal.  If the parameter is set to
 * {@code true}, iterators will correctly traverse all elements of the
 * deque, even if the iterator's next element is removed between
 * tasks.  Also, the iterator will never throw a {@link
 * ConcurrentModificationException}.  However, in this case, using
 * multiple iterators at the same time will cause contention.  In
 * addition each mutating operation incurs a small performance penalty
 * for keeping the iterators consistent with the state of the deque.
 * Developers should use this feature if they will need to iterator
 * over the deque at the same time it is being modified and want to
 * ensure that the iterator will traverse all the elements.
 *
 * <p>
 *
 * If the {@code supportsConcurrentIterators} parameter is {@code
 * false}, the iterator will only make a best-effort to reflect any
 * concurrent change to the deque.  If the next element that the
 * iterator is to return was been removed during a separate task, the
 * iterator <i>will</i> throw a {@code
 * ConcurrentModificationException}.  In this behavior, not supporting
 * concurrent updates incur no performance overhead for mutating
 * operations.  Furthermore, multiple iterators will not cause any
 * addtional contention.
 *
 * <p>
 *
 * Most operations run in constant time.  However, there are several
 * key difference in operation cost between this class and other
 * {@code Deque} implementations.  The operations {@link
 * ScalableDeque#remove(Object) remove}, {@link
 * ScalableDeque#removeAll(Collection) removeAll}, {@link
 * ScalableDeque#removeFirstOccurrence(Object) removeFirstOccurrence},
 * and {@link ScalableDeque#removeLastOccurrence(Object)
 * removeLastOccurrence} run in time linear to the number of instances
 * of that object in the deque, not the number of elements in the
 * deque.  In addition, the {@link ScalableDeque#contains(Object)
 * contains} operation is constant time.  Due to the distribute nature
 * of the deque, the {@link ScalableDeque#size() size} operation takes
 * time linear to the size of the deque.  For large deques, this
 * method should not be called, as it would take longer than the time
 * available to a {@code Task}.  Similarly, the {@link
 * ScalableDeque#toArray() toArray} and {@link
 * ScalableDeque#toArray(Object[]) toArray(T[])} operations should not
 * be used for anything but small deques.  The {@link
 * ScalableDeque#isEmpty() isEmpty} and {@link ScalableDeque#clear()
 * clear} operations however are still constant time.
 *
 * <p>
 *
 * All elements stored by this deque must be instances of {@link
 * Serializable}.  This class supports additionally supports elements
 * that are instances of {@code ManagedObject}.  If a {@code
 * ManagedObject} is stored within this deque, the developer is still
 * responsible for removing it from the data store at the end of its
 * lifetime.  Removing a {@code ManagedObject} from the deque by any
 * operation, including {@code clear}, <i>will not</i> remove that
 * object from the data store.  These objects should be removed from
 * the data store after they have been removed from the deque.
 *
 * <p>
 *
 * This class requires that all elements have a meaningful {@code
 * hashCode} and {@code equals} operation.  Failure to do so will
 * result in the {@code contains}, {@code remove}, {@code
 * removeFirstOccurrrence}, {@code removeLastOccurrence} and {@code
 * removeAll} operations not working.  The remaining operations will
 * still operate as normal, however.
 *
 * <p>
 *
 * This class and its iterator mark themselves for update as
 * necessary; no additional calls to the {@link DataManager} are
 * necessary when modifying the deque.  Developers should not need to
 * call {@code markForUpdate} or {@code getForUpdate} on an deque
 * instance, as this will eliminate all the concurrency benefits of
 * this class.  However, calling {@code getForUpdate} or {@code
 * markForUpdate} can be used if a operation needs to prevent all
 * access to the map.  
 *
 * <p>
 * 
 * This class's {@link Iterator} implements {@link ManagedObject} and
 * may be persisted.  A single {@code Iterator} instance should not be
 * shared between mutliple {@link Task} instances.  When an iterator
 * is done being used it should be removed from the data store using
 * {@link DataManager#removeObject(Object) removeObject}.  If using
 * concurrent iterators, failure to remove unused iterators will
 * result in degrated performance linear to the number of excess
 * iterator instances.
 *
 * <p>
 *
 * This class and its iterator support all the optional {@link
 * Collection} and {@code Iterator} operations.  This class does not
 * support {@code null} elements.
 *
 * @param <E> the type of elements held by this deque
 *
 * @see Deque
 * @see ManagedObject
 * @see Serializable
 */
public class ScalableDeque<E> extends AbstractCollection<E>
        implements Deque<E>, Serializable, ManagedObjectRemoval {

    /*
     * IMPLEMENTATION NOTES:
     *
     * The deque is implemented as a doubly-linked list of Element
     * objects, which are stored in a ScalableHashMap.  We use a
     * ScalableHashMap instead of a ScalableHashSet, as the Set
     * implementations is wrapper around Map (just like this class), and
     * therefore would cause an extra data access penalty.  Currently, the
     * value stored in the Map is not meaningful.
     *
     * A deque may have multiple elements with the same value.  Since a
     * Map is used to store the Element instances, we have to impose an
     * additional factor to ensure each Element's uniqueness.  Thefefore
     * each Element has an associated Id based on when it was inserted
     * into the deque.
     * 
     * Ids are generated by keeping two counters, one for the head of the
     * list and one for the tail.  The head counter is decremented when an
     * item is added to the head of the list, whilst the tail counter is
     * incremented.  This invariant ensures a total monotonic ordering of
     * Ids in the deque.  Furthermore, these counters do not impose any
     * additional contention, since only one is updated depending on which
     * end of the deque the element is inserted.
     *
     * The Element Ids allow this class to support the removal operations
     * defined in the Deque interface.  For example, removeFirst(Object)
     * would look for all elements that have that same value as Object and
     * choose the one with the lowest Id.
     *
     * In general, a deque need not support random access to the elements
     * other than the head and tail.  However, to properly support the
     * removal operations, we cannot traverse the entire map's keyset to
     * find all the elements with the provided value.  Therefore, we use
     * an indexing technique to locate the Elements based on using a
     * substitute class, ElementMatcher that is equivalent to an Element
     * that is being searched for.
     *
     * Random access to the elements occurs as follows.  Each Element the
     * of map uses the hashCode of the value that it stores.  Multiple
     * Element instances with the same value will still be unique by way
     * of their Ids.  The Map.Entry instances that contain the Element can
     * be obtained from the backing map using the package-private
     * ScalableMap.getEntry(Object key) method.
     *
     * To locate an Element, we use the ScalableDeque-internal class
     * ElementMatcher as the key for getEntry().  The ElementMatcher
     * constructor takes in the value of the Element that we are trying to
     * find and the class's hashCode method returns the hashCode of that
     * value.  This enables us to find all the Element instances with that
     * value's hashCode in the backing map.  When the map is comparing the
     * ElementMatcher instance to the Element instances (by doing its
     * key-comparisons), the ElementMatcher will return true if the Element
     * has the same value as the one stored in the ElementMatcher.  This
     * basic behavior is all that is necessary to support
     * Deque.contains(Object)
     *
     * To support the removal operations, we impose one additional
     * complexity, by allowing ElementMatcher instances to filter their
     * comparisons based on the Id of the Element.  For example, an
     * ElementMatcher would not return true for equals() when compared to
     * an Element that had an Id it had already seen.  This behavior lets
     * us find *all* the Element instances with the provided Id.  Since
     * the Ids are monotonically ordered, we can then do Id comparisons to
     * determine the first and last occurrences for the respective remove
     * operations.
     */
    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    /**
     * A reference to the element at the head of the list.
     */
    private final 
            ManagedReference<ManagedSerializable<ManagedReference<Element<E>>>>
            headElement;
    /**
     * A counter for the unique Id assigned to elements at the head of
     * the deque.  When a new head element is added, this counter is
     * decremented for the next element.  In doing so, this ensures
     * that a monotonic ordering exists for all elements, where the
     * element at the head of the deque has a numerically smaller id
     * than all other elements.
     *
     * @see ScalableDeque#addToHead(Element)
     */
    private final ManagedReference<ManagedSerializable<Long>> headCounter;
    /**
     * A reference to the element at the tail end of the list.
     */
    private final 
            ManagedReference<ManagedSerializable<ManagedReference<Element<E>>>>
            tailElement;
    /**
     * A counter for the unique Id assigned to elements at the tail
     * end of the deque.  When a new tail element is added, this
     * counter is incremented for the next element.  In doing so, this
     * ensures that a monotonic ordering exists for all elements,
     * where the element at the tail end of the list has a numerically
     * larger id than all other elements.
     *
     * @see ScalableDeque#addToTail(Element)
     */
    private final ManagedReference<ManagedSerializable<Long>> tailCounter;
    /**
     * A mapping from the unique Id of each outstanding {@code
     * BidirectionalDequeIterator} to the marker that holds the next
     * element the iterator will return, or {@code null} if this deque
     * does not support concurrent iteration.  This mapping is used by
     * each iterator to update its state before being serialized.  In
     * doing so, this allows the deque to notify each iterator of a
     * change to the backing deque that would result in the iterator's
     * next element no longer being present.
     *
     * @see ScalableDeque#checkIterators(Element)
     */
    private final 
            ManagedReference<ManagedSerializable<Map<
            BigInteger, IteratorMarker<E>>>> serializedIteratorsNextElementsRef;
    /**
     * A reference to the {@code ScalableHashMap} that will store all
     * the mappings
     */
    private final 
            ManagedReference<ScalableHashMap<Element<E>, Long>> backingMapRef;
    /**
     * A transient Java reference to the {@code ScalableHashMap}
     * refered to by {@link #backingMapRef}.  This field is set by a
     * task's first access to the backing map.
     *
     * @see ScalableDeque#map()
     */
    private transient ScalableHashMap<Element<E>, Long> backingMap;

    /**
     * Creates a new empty {@code ScalableDeque} that does not support
     * concurrent iterators.
     */
    public ScalableDeque() {
        this(false);
    }

    /**
     * Creates a new empty {@code ScalableDeque} that does supports
     * concurrent iterators if the the parameter is {@code true}.
     * Users of this constructor should refer to the class javadoc
     * regarding the performance behavior of concurrent iterators.
     *
     * @param supportsConcurrentIterators whether this deque should
     *        support concurrent iterators
     */
    public ScalableDeque(boolean supportsConcurrentIterators) {
        backingMap = new ScalableHashMap<Element<E>, Long>();

        DataManager dm = AppContext.getDataManager();
        backingMapRef = dm.createReference(backingMap);

        // initialize the pointers to the front and end of the deque
        // to null.  However, the reference to these pointers will
        // always be non-null
        ManagedSerializable<ManagedReference<Element<E>>> head =
                new ManagedSerializable<ManagedReference<Element<E>>>(null);
        ManagedSerializable<Long> headCount =
                new ManagedSerializable<Long>(-1L);

        ManagedSerializable<ManagedReference<Element<E>>> tail =
                new ManagedSerializable<ManagedReference<Element<E>>>(null);
        ManagedSerializable<Long> tailCount =
                new ManagedSerializable<Long>(0L);

        headElement = dm.createReference(head);
        headCounter = dm.createReference(headCount);
        tailElement = dm.createReference(tail);
        tailCounter = dm.createReference(tailCount);

        // If this deque supports concurrent iterators then initialize
        // a mapping that this deque will use to notify iterators of
        // any changes to the backing map
        if (supportsConcurrentIterators) {
            ManagedSerializable<Map<BigInteger, IteratorMarker<E>>> 
                    serializedIteratorsNextElements =
                    new ManagedSerializable<Map<BigInteger, IteratorMarker<E>>>(
                    new HashMap<BigInteger, IteratorMarker<E>>());

            serializedIteratorsNextElementsRef =
                    dm.createReference(serializedIteratorsNextElements);
        } else {
            serializedIteratorsNextElementsRef = null;
        }
    }

    /**
     * Creates a {@code ScalableDeque} without support for concurrent
     * iterators and adds all the elements in the provided collection
     * according to their traversal ordering.
     *
     * @param c the collection of elements the deque will initially
     *        contain
     */
    public ScalableDeque(Collection<? extends E> c) {
        this();
        if (c == null) {
            throw new NullPointerException("The provided collection " +
                                           "cannot be null");
        }
        addAll(c);
    }

    /**
     * Returns the {@link ScalableHashMap} used to store entries.
     *
     * @return the backing map.
     */
    private ScalableHashMap<Element<E>, Long> map() {
        if (backingMap == null) {
            backingMap = backingMapRef.get();
        }
        return backingMap;
    }

    /**
     * {@inheritDoc}
     */
    public boolean add(E e) {
        if (e == null) {
            throw new NullPointerException("cannot add null elements");
        }

        // get the counter for the tail and mark this element with the
        // next value
        Long tailCount = getAndIncrementTailCounter();
        Element<E> element = new Element<E>(e, tailCount);

        // add the element to the backing map so its persisted
        map().put(element, tailCount);

        // then link after the last element
        addToTail(element);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void addFirst(E e) {
        if (e == null) {
            throw new NullPointerException("cannot add null elements");
        }

        // get the counter for the head element and mark this element
        // with the next value
        Long headCount = getAndDecrementHeadCounter();
        Element<E> element = new Element<E>(e, headCount);

        // add it to the backing map
        map().put(element, headCount);

        // link it before the first element
        addToHead(element);
    }

    /**
     * {@inheritDoc}
     */
    public void addLast(E e) {
        add(e);
    }

    /**
     * Adds the provided {@code Element} to the head of the deque
     * and updates the head reference.
     *
     * @param e the new head {@code Element}.
     */
    private void addToHead(Element<E> e) {

        DataManager dm = AppContext.getDataManager();

        // short-circuit case if this element is the only element in
        // the deque (i.e. the deque was empty)
        if (headElement.get().get() == null) {
            ManagedReference<Element<E>> ref = dm.createReference(e);
            headElement.get().set(ref);
            tailElement.get().set(ref);
            return;
        }

        Element<E> oldHead = headElement();
        headElement.get().set(dm.createReference(e));
        e.setPrev(null);
        e.setNext(oldHead);
        oldHead.setPrev(e);
    }

    /**
     * Adds the provided {@code Element} to the tail end of the deque
     * and updates the tail reference.
     *
     * @param e the new tail {@code Element}.
     */
    private void addToTail(Element<E> e) {

        DataManager dm = AppContext.getDataManager();

        // short-circuit case if this element is the only element in
        // the deque (i.e. the deque was empty)
        if (tailElement.get().get() == null) {
            ManagedReference<Element<E>> ref = dm.createReference(e);
            headElement.get().set(ref);
            tailElement.get().set(ref);
            return;
        }

        Element<E> oldTail = tailElement();
        tailElement.get().set(dm.createReference(e));
        e.setPrev(oldTail);
        e.setNext(null);
        oldTail.setNext(e);
    }

    /**
     * Examines the next {@code Element} for all outsanding {@code
     * BidirectionalDequeIterator} instances to see if the provided,
     * removed {@code Element} would have been their next element, and
     * if so updates the iterator's state accordingly.
     *
     * @param removed the {@code Element} that is being removed
     */
    private void checkIterators(Element<E> removed) {
        // If this map does not support concurrent iterators, the this
        // method becomes a no-op
        if (!supportsConcurrentIterators()) {
            return;
        }

        Map<BigInteger, IteratorMarker<E>> iteratorToCurrentElement =
                serializedIteratorsNextElementsRef.get().get();

        DataManager dm = AppContext.getDataManager();
        ManagedReference elementRef = dm.createReference(removed);

        // examine each iterator's next element and see if it is the one
        // we have just removed
        for (Map.Entry<BigInteger, IteratorMarker<E>> e : 
            iteratorToCurrentElement.entrySet()) {

            IteratorMarker<E> marker = e.getValue();
            ManagedReference<Element<E>> nextElement = marker.nextElement;

            // if the iterator was going to return the removed element
            // next, then we need to update it with the nextInsert
            // value from the removed element
            if (nextElement != null && nextElement.equals(elementRef)) {

                // mark that the map has changed
                dm.markForUpdate(serializedIteratorsNextElementsRef.get());

                // then update the iterators next value based on which
                // direction it was iterating		
                marker.nextElement = (marker.isReverse)
                        ? removed.prevElement : removed.nextElement;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        map().clear();
	
        ManagedReference<Element<E>> headRef = headElement.get().get();

        // if the deque was already empty, we have no clean up to do
        if (headRef == null) {
            return;
        }
        
        // otherwise the deque wasn't already empty, so start a task
        // to remove all the elements starting at the head
        AppContext.getTaskManager().
                scheduleTask(new AsynchronousClearTask<E>(headRef));

        // reset the head and tail pointers
        headElement.get().set(null);
        tailElement.get().set(null);

        if (supportsConcurrentIterators()) {
            // update all the iterator's states by setting their next
            // element to null since there are no longer any elements in
            // the deque
            Map<BigInteger, IteratorMarker<E>> iteratorToNextElement =
                    serializedIteratorsNextElementsRef.getForUpdate().get();

            for (Map.Entry<BigInteger, IteratorMarker<E>> e : 
                iteratorToNextElement.entrySet()) {
                e.getValue().nextElement = null;
            }
        }
    }

    /**
     * {@inheritDoc}.  This operation runs in constant time.
     */
    public boolean contains(Object o) {
        return map().containsKey(new ElementMatcher(o));
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<E> descendingIterator() {
        return new BidirectionalDequeIterator<E>(this, true);
    }

    /**
     * {@inheritDoc}
     */
    public E element() {
        E e = getFirst();
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        /*
         * IMPLEMENTATION NOTE: The Java general contract for hashCode
         * requires that if two objects are equal, that they have the
         * same hash code.  However, there is not standard hashCode
         * algorithm for Deque classes.  Therefore, we make a
         * best-effort to at least check for identity-equality within
         * members of this class.
         */

        if (o == null || !(o instanceof ScalableDeque)) {
            return false;
        }
        ScalableDeque d = (ScalableDeque) o;
        DataManager dm = AppContext.getDataManager();
        return dm.createReference(this).equals(dm.createReference(d));
    }

    /**
     * Returns the next unique identifier for an {@code Element} that
     * is being added to the head of the deque.
     *
     * @return the next unique identifer for the head of the deque.
     */
    private Long getAndDecrementHeadCounter() {
        ManagedSerializable<Long> headVal = headCounter.getForUpdate();
        Long counter = headVal.get();
        // decrement it for the next one
        headVal.set(counter.longValue() - 1);
        return counter;
    }

    /**
     * Returns the next unique identifier for an {@code Element} that
     * is being added to the tail of the deque.
     *
     * @return the next unique identifer for the tail of the deque.
     */
    private Long getAndIncrementTailCounter() {
        ManagedSerializable<Long> tailVal = tailCounter.getForUpdate();
        Long counter = tailVal.get();
        // increment it for the next one
        tailVal.set(counter.longValue() + 1);
        return counter;
    }

    /**
     * {@inheritDoc}
     */
    public E getFirst() {
        Element<E> e = headElement();
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public E getLast() {
        Element<E> e = tailElement();
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return AppContext.getDataManager().createReference(this).hashCode();
    }

    /**
     * Returns the {@code Element} at the front of this deque, or
     * {@code null} if this deque is empty.
     *
     * @return the element at the front of this deque or {@code null}
     *         if the deque is empty
     */
    private Element<E> headElement() {
        ManagedReference<Element<E>> headRef = headElement.get().get();
        return (headRef == null) ? null : headRef.get();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return headElement() == null;
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<E> iterator() {
        return new BidirectionalDequeIterator<E>(this, false);
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@code true}
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@code true}
     */
    public boolean offerFirst(E e) {
        if (e == null) {
            throw new NullPointerException("cannot add null elements");
        }
        addFirst(e);
        return true;
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@code true}
     */
    public boolean offerLast(E e) {
        if (e == null) {
            throw new NullPointerException("cannot add null elements");
        }
        addLast(e);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public E peek() {
        return peekFirst();
    }

    /**
     * {@inheritDoc}
     */
    public E peekFirst() {
        Element<E> e = headElement();
        return (e == null) ? null : e.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public E peekLast() {
        Element<E> e = tailElement();
        return (e == null) ? null : e.getValue();
    }

    /**
     * {@inheritDoc}
     */
    public E poll() {
        return pollFirst();
    }

    /**
     * {@inheritDoc}
     */
    public E pollFirst() {
        return removeElement(headElement());
    }

    /**
     * {@inheritDoc}
     */
    public E pollLast() {
        return removeElement(tailElement());
    }

    /**
     * {@inheritDoc}
     */
    public E pop() {
        Element<E> head = headElement();
        if (head == null) {
            throw new NoSuchElementException();
        }
        return removeElement(head);
    }

    /**
     * {@inheritDoc}
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * {@inheritDoc}
     */
    public E remove() {
        E e = removeFirst();
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e;
    }

    /**
     * {@inheritDoc} Note that this implementation takes time
     * proportinal to the number of instances of {@code o} in the
     * deque, <i>not</i> the number of elements.
     *
     * @param o {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        if (c == null) {
            throw new NullPointerException("the collection cannot be null");
        }

        boolean updated = false;
        for (Object o : c) {
            boolean b = removeAllOccurrences(o);
            if (b) {
                updated = true;
            }
        }
        return updated;
    }

    /**
     * Removes all occurrences of the provided object from the deque.
     * Note that this implementation takes time proportinal to the
     * number of instances of {@code o} in the deque, <i>not</i> the
     * number of elements.
     *
     * @param o the object to to be removed
     *
     * @return {@code true} if the deque was modified as a result of
     *         this operation
     */
    public boolean removeAllOccurrences(Object o) {
        if (o == null) {
            throw new NullPointerException("this deque does not support " +
                                           "null elements");
        }

        // repeatedly use the same matcher to look for elements with
        // the provided object in the backing map
        ElementMatcher matcher = new ElementMatcher(o);
        ScalableHashMap.PrefixEntry<Element<E>, Long> entry =
                map().getEntry(matcher);
        Element<E> e = (entry == null) ? null : entry.getKey();

        while (e != null) {

            // each time we find an object, we first remove it from
            // the map, then we add its id to the matcher and continue
            // looking
            removeElement(e);
            matcher.addId(e.getId());

            entry = map().getEntry(matcher);
            e = (entry == null) ? null : entry.getKey();
        }

        return !(matcher.alreadySeen.isEmpty());
    }

    /**
     * Removes the provided {@code Element} from the deque and updates
     * all the deque-internal structures as necesary.  This method
     * will remove the element from the backing map, patch the element
     * doubly-linked list, and then if necessary, update head and tail
     * references.  Lastly, this method will update any {@code
     * BidirectionalDequeIterator} instances whose next element would
     * have been the removed element.
     *
     * @param e the {@code Element} to be removed from the deque
     *
     * @return the value contained by the removed {@code Element}
     */
    private E removeElement(Element<E> e) {
        if (e == null) {
            return null;
        }
        E value = e.getValue();
        map().remove(e);

        // remove e from the deque's backing list
        Element<E> prev = e.prev();
        Element<E> next = e.next();
        if (prev != null) {
            prev.setNext(next);
        }
        if (next != null) {
            next.setPrev(prev);
        }
        
        // update the references to the front and back of the list, if
        // necessary.  We rely on the invariants that the previous and
        // next element references will only be null if an element is
        // at an end of the list.
        DataManager dm = AppContext.getDataManager();
        if (e.prevElement == null) {
            headElement.get().set((next == null)
                                  ? null : dm.createReference(next));
        }
        if (e.nextElement == null) {
            tailElement.get().set((prev == null)
                                  ? null : dm.createReference(prev));
        }
        
        // check to see if the remove of this object affects any of
        // the currently serialized iterators
        checkIterators(e);

        AppContext.getDataManager().removeObject(e);

        return value;
    }

    /**
     * {@inheritDoc}
     */
    public E removeFirst() {
        Element<E> head = headElement();
        if (head == null) {
            throw new NoSuchElementException();
        }
        return removeElement(head);
    }

    /**
     * {@inheritDoc} Note that this implementation takes time
     * proportinal to the number of instances of {@code o} in the
     * deque, <i>not</i> the number of elements.
     *
     * @param o {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public boolean removeFirstOccurrence(Object o) {
        if (o == null) {
            throw new NullPointerException("this deque does not support " +
                                           "null elements");
        }

        // repeatedly use the same matcher to look for elements with
        // the provided object in the backing map
        ElementMatcher matcher = new ElementMatcher<E>(o);

        ScalableHashMap.PrefixEntry<Element<E>, Long> entry =
                map().getEntry(matcher);
        Element<E> e = (entry == null) ? null : entry.getKey();

        // this value represents the highest value any element could
        // have so any element present will be less than this value
        long leftMost = Long.MAX_VALUE;
        Element<E> firstOccurrence = null;

        while (e != null) {

            // each time we find an element, we check to see if its id
            // is lower than the left most we've seen so far.  If it
            // is closer to the head, then we mark that as the first
            // occurrence and keep searching for any additional
            // elements in the deque that match the provided object.
            Long id = e.getId();
            if (id.longValue() < leftMost) {
                leftMost = id.longValue();
                firstOccurrence = e;
            }

            matcher.addId(id);
            entry = map().getEntry(matcher);
            e = (entry == null) ? null : entry.getKey();
        }

        // once we've searched the map for all elements matching the
        // provided object, remove the first occurrence (the one with
        // the lowest id) if it was found
        if (firstOccurrence != null) {
            removeElement(firstOccurrence);
            return true;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public E removeLast() {
        Element<E> tail = tailElement();
        if (tail == null) {
            throw new NoSuchElementException();
        }
        return removeElement(tail);
    }

    /**
     * {@inheritDoc} Note that this implementation takes time
     * proportinal to the number of instances of {@code o} in the
     * deque, <i>not</i> the number of elements.
     *
     * @param o {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            throw new NullPointerException("this deque does not support " +
                                           "null elements");
        }
        // repeatedly use the same matcher to look for elements with
        // the provided object in the backing map
        ElementMatcher matcher = new ElementMatcher<E>(o);

        ScalableHashMap.PrefixEntry<Element<E>, Long> entry =
                map().getEntry(matcher);
        Element<E> e = (entry == null) ? null : entry.getKey();

        // this value represents the lowest value any element could
        // have, so any element present will be greater than this
        // value
        long rightMost = Long.MIN_VALUE;
        Element<E> lastOccurrence = null;

        while (e != null) {

            // each time we find an element, we check to see if its id
            // is higher than the right most we've seen so far.  If it
            // is closer to the tail, then we mark that as the last
            // occurrence and keep searching for any additional
            // elements in the deque that match the provided object.
            Long id = e.getId();
            if (id.longValue() > rightMost) {
                rightMost = id.longValue();
                lastOccurrence = e;
            }

            matcher.addId(id);
            entry = map().getEntry(matcher);
            e = (entry == null) ? null : entry.getKey();
        }

        // once we've searched the map for all elements matching the
        // provided object, remove the last occurrence (the one with
        // the highest id) if it was found
        if (lastOccurrence != null) {
            removeElement(lastOccurrence);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Clears the backing map, then removes all the {@code Element}
     * instances created by this deque.
     */
    public void removingObject() {
        clear();
        
        // Remove the ManagedSerializable objects as well
        AppContext.getDataManager().removeObject(backingMap);
        AppContext.getDataManager().removeObject(headElement.get());
        AppContext.getDataManager().removeObject(headCounter.get());
        AppContext.getDataManager().removeObject(tailElement.get());
        AppContext.getDataManager().removeObject(tailCounter.get());
    }

    /**
     * {@inheritDoc}.  This operation runs in time proportinal to the
     * length of this deque.
     *
     * @return the size of this deque
     */
    public int size() {
        int size = 0;
        Element<E> n = headElement();
        while (n != null) {
            size++;
            n = n.next();
        }
        return size;
    }
    

    /**
     * Returns whether this deque will support concurrent iterators.
     * If {@code false}, the iterators of this deque will not receive
     * updates regarding any changes to the deque and will throw
     * {@link ConcurrentModificationException}s if next element was
     * removed while the iterator was serialized.
     *
     * @see ScalableDeque$BidirectionalDequeIterator
     * @see ScalableDeque#checkIterators(Element)
     */
    private boolean supportsConcurrentIterators() {
        return serializedIteratorsNextElementsRef != null;
    }

    /**
     * Returns the {@code Element} at the end of this deque, or
     * {@code null} if this deque is empty.
     *
     * @return the element at the end of this deque or {@code null}
     *         if the deque is empty
     */
    private Element<E> tailElement() {
        ManagedReference<Element<E>> tailRef = tailElement.get().get();
        return (tailRef == null) ? null : tailRef.get();
    }

    /**
     * Reads in all state for the deque and initializes the transient
     * {@code backingMap} field to {@code null}
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {

        // read in all the non-transient state
        s.defaultReadObject();

        // set the state of the transient backing map to null
        backingMap = null;
    }

    /**
     * A deque-internal class for wrapping elements within the deque.
     * This class maintains a doubly-linked list of all the elements
     * in the deque.  This class relies on {@code ScalableDeque} to
     * maintain correct references to to the head and tail of this
     * list, and also to update it accordingly on additions and
     * removals.
     *
     * <p>
     *
     * The value of the elements themselves are refered to by {@code
     * ManagedReference} instances if they implement {@code
     * ManagedObject} or are refered to using a standard Java
     * reference.
     *
     * <p>
     *
     * {@code Element} instances use their value's hash code, but can
     * be distinguished by their {@code id}, which corresponds to the
     * id assigned to them at the time of their addition to the deque.
     * This method of hashing has the effect that when an {@code
     * Element} is added to the deque, and subsequently added to the
     * backing map, the deque can locate all instances of the element
     * by using the element's hash code.  This behavior is required to
     * correctly support the random access behavior of {@link
     * ScalableDeque#remove(Object) remove}, {@link
     * ScalableDeque#removeFirstOccurrence(Object)
     * removeFirstOccurrence}, {@link
     * ScalableDeque#removeLastOccurrence(Object)
     * removeLastOccurrence}, and {@link
     * ScalableDeque#removeAll(Collection) removeAll}.
     *
     * @see ScalableDeque$ElementMatcher
     *
     * @param <E> The type of element held by the deque
     */
    static class Element<E> implements Serializable, ManagedObject {

        /**
         * {@inheritDoc}
         */
        private static final long serialVersionUID = 1L;
        /**
         * A {@code ManagedReference} to value of this element if the
         * elemented implements {@code ManagedObject}, or {@code null}
         * otherwise.  The value of this variable is conditionally set
         * during object deserialization based on {@code useRef}
         *
         * @serial
         */
        private transient ManagedReference<E> valueRef;
        /**
         * A Java reference to the value of this element if the
         * element does not implement {@code ManagedObject}, , or
         * {@code null} otherwise.  The value of this variable is
         * conditionally set during object deserialization based on
         * {@code sueRef}
         *
         * @serial
         */
        private transient E value;
        /**
         * The flag that determines whether the value contained by
         * this element should be accessed by a {@code
         * ManagedReference} or a Java reference.
         */
        private final boolean useRef;
        /**
         * The Id assigned to this {@code Element} at the time of its
         * creation.  {@code Element} instances are numerically
         * ordered where the head element will have the numerically
         * smallest Id, and the tail element will have the numerically
         * largest Id.
         *
         * @see ScalableDeque#add(Object)
         * @see ScalableDeque#addFirst(Object)
         */
        private final Long id;
        /**
         * A reference to the previous {@code Element} in the deque,
         * or {@code null} if this element is the head of the deque.
         */
        private ManagedReference<Element<E>> prevElement;
        /**
         * A reference to the next {@code Element} in the deque,
         * or {@code null} if this element is the tail of the deque.
         */
        private ManagedReference<Element<E>> nextElement;

        /**
         * Constructs a new {@code Element} with the provided {@code
         * id} to contain the value.
         *
         * @param value the value held by this {@code Element}
         * @param id the id of this element
         */
        public Element(E value, Long id) {
            if (value == null) {
                throw new NullPointerException("cannot create Element with " +
                                                              "null value");
            }
            if (value instanceof ManagedObject) {
                valueRef = AppContext.getDataManager().createReference(value);
                this.value = null;
                useRef = true;
            } else {
                this.value = value;
                valueRef = null;
                useRef = false;
            }
            this.id = id;
            prevElement = null;
            nextElement = null;
        }

        /**
         * Returns {@code true} if {@code o} is an instance of {@code
         * Element}, contains the same value as this instance and has
         * the same id.
         *
         * @param o {@inheritDoc}
         *
         * @return {@code true} if {@code o} is an instance of {@code
         * Element}, contains the same value as this instance and has
         * the same id.
         * 
         * @see ScalableDeque$ElementMatcher
         */
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            } else if (o instanceof Element) {
                Element<E> e = uncheckedCast(o);
                E v1 = e.getValue();
                E v2 = getValue();
                return (v1 == v2 || (v1 != null && v1.equals(v2))) &&
                        e.id.equals(id);
            } else if (o instanceof ElementMatcher) {
                // This case occurs when an ElementMatcher is used by the
                // ScalableDeque to locate an element in the backing map.
                // In this case, we use the ElementMatcher's equal
                // function as it contains more state about the particular
                // element for which the deque is looking.
                return ((ElementMatcher) o).equals(this);
            }
            return false;
        }

        /**
         * Returns the hash code provided by the value contained by
         * this element.
         *
         * @return the hash code of the value contained by this
         *         element
         */
        public int hashCode() {
            E e = getValue();
            return e.hashCode();
        }

        /**
         * Returns the unique id of this {@code Element}.
         *
         * @return the id of this instance
         */
        long getId() {
            return id;
        }

        /**
         * Returns the value contained by this element.
         *
         * @return the value contained by this element
         */
        public E getValue() {
            return (useRef) ? valueRef.get() : value;
        }

        /**
         * Returns the {@code Element} after this instance in the
         * deque, or {@code null} if this element is the tail of the
         * deque.
         *
         * @return the {@code Element} after this instance in the
         *         deque, or {@code null} if this element is the tail
         *         of the deque.
         */
        Element<E> next() {
            return (nextElement == null) ? null : nextElement.get();
        }

        /**
         * Returns the {@code Element} before this instance in the
         * deque, or {@code null} if this element is the head of the
         * deque.
         *
         * @return the {@code Element} before this instance in the
         *         deque, or {@code null} if this element is the head
         *         of the deque.
         */
        Element<E> prev() {
            return (prevElement == null) ? null : prevElement.get();
        }

        /**
         * Sets the link from this {@code Element} to the next
         * {@code Element} in the deque to {@code next}.
         *
         * @code prev the {@code Element} after this {@code Element}
         *       in the deque
         */
        void setNext(Element<E> next) {
            DataManager dm = AppContext.getDataManager();
            ManagedReference<Element<E>> ref =
                    (next == null) ? null : dm.createReference(next);

            dm.markForUpdate(this);
            nextElement = ref;
        }

        /**
         * Sets the link from this {@code Element} to the previous
         * {@code Element} in the deque to {@code prev}.
         *
         * @code prev the {@code Element} before this {@code Element}
         *       in the deque
         */
        void setPrev(Element<E> prev) {
            DataManager dm = AppContext.getDataManager();
            ManagedReference<Element<E>> ref =
                    (prev == null) ? null : dm.createReference(prev);

            dm.markForUpdate(this);
            prevElement = ref;
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return getValue() + "~(" + id + ")";
        }

        /**
         * Writes out all non-transient state and then conditionally
         * writes either {@code valueRef} or {@code value} depending
         * on whether this element is supposed to use a {@code
         * ManagedReference} to access its value.
         *
         * @param s {@inheritDoc}
         */
        private void writeObject(ObjectOutputStream s)
                throws IOException {
            // write out all the non-transient state
            s.defaultWriteObject();

            // conditionally write either the ManagedReference to the
            // value or the value itself, if it did not implement
            // ManagedObject
            s.writeObject((useRef) ? valueRef : value);
        }

        /**
         * Reconstructs the {@code Element} and initializes {@code
         * valueRef} or {@code value} depending on whether this
         * element is supposed to use a {@code ManagedReference} to
         * access its value.
         *
         * @param s {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {

            // read in all the non-transient state
            s.defaultReadObject();

            if (useRef) {
                valueRef = uncheckedCast(s.readObject());
            } else {
                // note that this is the line with the unchecked cast
                value = (E) (s.readObject());
            }
        }
    }

    /**
     * A utility class for locating {@code Element} instances in the
     * backing map.  This class hashes to the same value as the {@code
     * toFind} object provided to the constructor.  This class uses an
     * implementation of {@code hashCode} and {@code equals} that will
     * return true if an {@code Element} is found that has the same
     * {@code hashCode}, {@code toFind} is equal to the value of the
     * {@code Element}, and where the Id of the {@code Element} has
     * not already been seen.  This allows us to use a {@code
     * ElementMatcher} that wraps an object with {@code
     * ScalableHashMap#getEntry(Object)} to return the actual {@code
     * Element} contained within the map.  This functionality is
     * required to support the random access required for {@link
     * ScalableDeque#remove(Object) remove}, {@link
     * ScalableDeque#removeFirstOccurrence(Object)
     * removeFirstOccurrence}, {@link
     * ScalableDeque#removeLastOccurrence(Object)
     * removeLastOccurrence}, and {@link
     * ScalableDeque#removeAll(Collection) removeAll}.
     *
     * <p>
     *
     * This class also supports repeated use in searching for elements
     * by way of its {@code alreadySeen} field.  A single {@code
     * ElementMatcher} instance maybe be updated with the {@code
     * Element} Ids already seen from past searches.  For example,
     * this allows us to search for all {@code Element} instances with
     * a certain value.
     *
     * @see ScalableDeque$Element
     *
     * @param <T> the type of element to be matched
     */
    /*
     * IMPLEMENTATION NOTE: this class does not implement Serializable
     * to ensure that it can never be accidentally stored in the
     * backing map.  (Or that if it was, an error would occur such
     * that the programmer would know)
     */
    private static class ElementMatcher<T> {

        /**
         * The set of {@code Element} Id values already seen.  This
         * set is empty on construction.
         */
        private Set<Long> alreadySeen;
        /**
         * The value of the {@code Element} that this matcher should
         * find.
         */
        private Object toFind;

        /**
         * Constructs an {@code ElementMatcher} to match all {@code
         * Element} instances that have the same value as {@code
         * toFind}.
         *
         * @param toFind the value of the {@code Element} that this
         *        instance should match
         */
        ElementMatcher(Object toFind) {
            this.toFind = toFind;
            alreadySeen = new HashSet<Long>();
        }

        /**
         * Adds the provided {@code ElementId} to the set of ids that
         * have already been seen, which has the effect this instances
         * will no longer match any {@code Element} instances with
         * the provided id.
         *
         * @param id an {@code Element} id that has already been seen
         */
        public void addId(Long id) {
            alreadySeen.add(id);
        }

        /**
         * Returns {@true} if {@code o} is an instance of {@code
         * Element}, the element has the same value as the {@code
         * toFind} value provided to this instance, and the Id of the
         * {@code Element} is not in the {@code alreadySeen} set.
         *
         * @param o {@code inheritDoc}
         *
         * @return {@code true} if {@code o} is an {@code Element}
         *         that this instance should match
         */
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            } else if (o instanceof ElementMatcher) {
                ElementMatcher<T> m = uncheckedCast(o);
                return (m.equals(toFind)) && alreadySeen.equals(m.alreadySeen);
            } else if (o instanceof Element) {
                Element<T> e = uncheckedCast(o);
                T value = e.getValue();
                return (value == toFind || (value != null &&
                        value.equals(toFind))) &&
                        !alreadySeen.contains(e.getId());
            }
            return false;
        }

        /**
         * Returns the hash code of the value provided as {@code
         * toFind}.
         *
         * @return {@inheritDoc}
         */
        public int hashCode() {
            return (toFind == null) ? 0 : toFind.hashCode();
        }
    }

    /**
     * A concurrent, persistable {@code Iterator} implementation for
     * the {@code ScalableDeque} that allows element traverse from
     * head-to-tail or tail-to-head as required.
     *
     * <p>
     *
     * If an iterator is created for an empty deque, and then
     * serialized, it will remain valid upon any subsequent
     * deserialization.  An iterator in this state, where it has been
     * created but {@code next} has never been called, will always
     * begin an the first entry in the map, if any, since its
     * deserialization.
     *
     * <p> 
     *
     * Instance of this class are <i>not</i> designed to be shared
     * between concurrent tasks.
     *
     * @param <E> the type of elements returned by the iterator
     */
    static class BidirectionalDequeIterator<E>
            implements Iterator<E>, Serializable, ManagedObjectRemoval {

        /** 
         * The version of the serialized form. 
         */
        private static final long serialVersionUID = 3;
        /**
         * Whether the current entry has already been removed
         */
        private boolean currentRemoved;
        /**
         * A reference to the next entry that this iterator will
         * return
         */
        private ManagedReference<Element<E>> nextElement;
        /**
         * A reference to the current entry
         */
        // We need this reference to support removal immediately after
        // serialization.
        private ManagedReference<Element<E>> curElement;
        /**
         * A reference to the backing deque
         */
        private final ManagedReference<ScalableDeque<E>> dequeRef;
        /**
         * A reference to the map where {@code DequeIterator}
         * instances register their next elements so that upon
         * deserialization, the iterator exhibits correct behavior, or
         * {@code null} if the backing deque does not support
         * concurrent iteration.
         *
         * @see ScalableDeque(boolean)
         */
        private final ManagedReference<ManagedSerializable<Map<BigInteger, 
                IteratorMarker<E>>>> serializedIteratorsNextElementsRef;
        /**
         * {@code true} if this iterator was created with an empty
         * deque.  In this case the iterator will remain at the head
         * of the head and valid until {@code next} is called.
         */
        private boolean nextElementWasNullOnCreation;
        /**
         * {@code true} if this iterator has just been deserialized
         * and needs to recheck whether its next elemente is still
         * valid.
         *
         * @see #checkForNextElementUpdates()
         */
        private transient boolean recheckNextElement;
        /**
         * The id of this iterator that will be used in the {@code
         * serializedIteratorsNextElementsRef} map.
         */
        private final BigInteger iteratorId;
        /**
         * Whether this iterator is traversing the deque in reverse
         */
        private final boolean isReverse;

        /**
         * Constructs a new {@code OrderedIterator}.
         *
         * @param deque the deque that will be iterated over
         * @param isReverse whether this iterator should traverse the
         *        provided deque from tail to head
         */
        BidirectionalDequeIterator(ScalableDeque<E> deque, boolean isReverse) {

            currentRemoved = false;
            curElement = null;
            this.isReverse = isReverse;

            DataManager dm = AppContext.getDataManager();

            if (isReverse) {
                // set the value of nextElement to be the last
                // element in the deque
                Element<E> tail = deque.tailElement();
                nextElement = (tail == null) ? null : dm.createReference(tail);
            } else {
                // set the value of nextElement to be the first
                // element in the deque
                Element<E> head = deque.headElement();
                nextElement = (head == null) ? null : dm.createReference(head);
            }

            // mark if the next element was null.  If so, if we
            // serialize this iterator and then deserialize it, we
            // should refresh the first element in the map	   
            nextElementWasNullOnCreation = nextElement == null;

            // note that this field may be null, in which case, we
            // don't update our state and will throw a concurrent
            // modification exception
            serializedIteratorsNextElementsRef =
                    deque.serializedIteratorsNextElementsRef;

            dequeRef = dm.createReference(deque);
            iteratorId = dm.createReference(this).getId();

            recheckNextElement = false;

            // last, mark in the shared map what the next element will
            // be for this iterator.  This ensures that if the
            // iterator is left unused and serialized after
            // construction that the the next element will be
            // correctly updated if any future modifications occur
            updatePersistentNextElement();
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasNext() {
            if (recheckNextElement) {
                checkForNextElementUpdates();
            }
            return nextElement != null;
        }

        /**
         * After deserialization, this iterator should check that its
         * reference to the next element is still valid.  Two cases
         * exist to check.
         *
         * <p>
         *
         * First, if this iterator was created based on an empty deque,
         * and has never iterated over the first element, the iterator
         * must check whether any new elements exist in the deque.  Once
         * an element exists, the iterator updates its nextElement
         * reference and is no longer in the "empty map" state.
         *
         * <p>
         *
         * In the second case, while serialized, the next element
         * could have been removed from the deque.  Should it have
         * been removed, the deque will have updated the shared
         * mapping from iterator to next element, with a reference as
         * to what this iterator's new next element should.
         *
         * @see ScalableDeque#checkIterators(Element)
         */
        private void checkForNextElementUpdates() {

            // check to see if this iterator was created with a null
            // next element.  This flag will only be true if this
            // iterator has never seen a next element
            if (nextElementWasNullOnCreation) {

                if (isReverse) {
                    // see if the last element in the deque is now
                    // non-null
                    Element<E> tail = dequeRef.get().tailElement();
                    nextElement = (tail == null) ? null
                            : AppContext.getDataManager().createReference(tail);
                } else {
                    // see if the first element in the deque is now
                    // non-null
                    Element<E> head = dequeRef.get().headElement();
                    nextElement = (head == null) ? null
                            : AppContext.getDataManager().createReference(head);
                }
                // mark if the next element is now non-null.  If so, if
                // we unset the flag and the iterator, which will
                // never be set to true again.
                if (nextElement != null) {
                    nextElementWasNullOnCreation = false;
                    AppContext.getDataManager().markForUpdate(this);
                }
            } else if (serializedIteratorsNextElementsRef != null) {
                // Check whether this iterator has support for concurrent
                // updates.
                
                // If so, this iterator has seen at least one element
                // and had updated the shared itereator-to-element map
                // prior to serialization with what its next element
                // was.  In this case, the iterator should simply use
                // the reference mapped to its iteratorId, as this
                // will have been updated based on any changes to the
                // backing map.

                Map<BigInteger, IteratorMarker<E>> iteratorToNextElement =
                        serializedIteratorsNextElementsRef.getForUpdate().get();

                ManagedReference<Element<E>> oldNext = nextElement;

                // Assign whatever is listed as the next element for
                // us.  We don't remove ourselves here in case next()
                // is never called, in which case the next element in
                // the map would still be valid.
                nextElement = iteratorToNextElement.get(iteratorId).nextElement;

                // the next element has changed, mark the iterator for
                // update
                if (!(nextElement == oldNext ||
                        (nextElement != null && nextElement.equals(oldNext)))) {
                    AppContext.getDataManager().markForUpdate(this);
                }
            }

            recheckNextElement = false;
        }

        /**
         * Returns the next element in the {@code ScalableDeque}.  Note that
         * due to the concurrent nature of this iterator, this method may skip
         * elements that have been added after the iterator was constructed.
         * Likewise, it may return new elements that have been added.  This
         * implementation is guaranteed never to return an element more than
         * once.
         *
         * <p>
         *
         * If the backing deque does not support concurrent iterators,
         * this method will throw a {@link
         * java.util.ConcurrentModificationException} if the next
         * element that the iterator was set to return has been
         * removed from the deque, and {@code hasNext()} as not been
         * called first.
         *
         * @return the next element in the {@code ScalableDeque}
         *
         * @throws NoSuchElementException if no further entries exist
         * @throws ConcurrentModificationException if the next element
         *         of this iterator has been removed from the deque
         *         and {@code hasNext()} had not been called prior to
         *         this method.
         */
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Element<E> element = null;
            try {
                element = nextElement.get();
            } catch (ObjectNotFoundException onfe) {
                throw new ConcurrentModificationException(
                        "next element was removed from the deque: " + 
                        nextElement);
            }

            currentRemoved = false;

            // update the iterator state
            curElement = nextElement;
            nextElement = (isReverse) 
                    ? element.prevElement : element.nextElement;

            // mark the iterator as updated
            AppContext.getDataManager().markForUpdate(this);

            // save the next element that we're going to return in case
            // we're serialized after this call
            updatePersistentNextElement();

            return element.getValue();
        }

        /**
         * Removes this iterator from the registry of active
         * iterators.
         */
        public void removingObject() {            
            // check if this iterator has been configured to receive
            // concurrent updates, and if so remove its id.
            if (serializedIteratorsNextElementsRef != null) {
                Map<BigInteger, IteratorMarker<E>> iteratorToNextElement =
                        serializedIteratorsNextElementsRef.getForUpdate().get();

                // remove this iterator's Id from the shared mapping
                // to ensure that any future modifications don't
                // require an unnecessary check for this iterator's
                // next element.
                iteratorToNextElement.remove(iteratorId);
            }
        }

        /**
         * If the backing deque is configured to notify iterators of
         * concurrent updates, this saves the {@code ManagedReference}
         * of the next element that this iterator is going to return
         * to a peristant state.  This enables the iterator to receive
         * updates from the deque while serialized if the next element
         * that it should return was removed.
         *
         * @see ScalableDeque#checkIterators(Element)
         */
        private void updatePersistentNextElement() {

            if (serializedIteratorsNextElementsRef != null) {
                Map<BigInteger, IteratorMarker<E>> iteratorToNextElement =
                        serializedIteratorsNextElementsRef.getForUpdate().get();

                iteratorToNextElement.put(iteratorId, 
                        new IteratorMarker<E>(isReverse, nextElement));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void remove() {
            if (currentRemoved) {
                throw new IllegalStateException(
                        "The current element has already been removed");
            } else if (curElement == null) {
                throw new IllegalStateException("No current element");
            }
            try {
                dequeRef.get().removeElement(curElement.get());
            } catch (ObjectNotFoundException onfe) {
                // this happens if the value of the current element
                // was removed from the datastore while this iterator
                // was serialized.  (The element itself is still
                // present.)  We could check for this upon
                // deserialization, but instead we rely on this lazy
                // check at call-time here to avoid doing any
                // unnecessary work.
            }
            currentRemoved = true;
            AppContext.getDataManager().markForUpdate(this);
        }

        /**
         * {@inheritDoc}
         */
        private void writeObject(ObjectOutputStream s)
                throws IOException {
            // write out all the non-transient state
            s.defaultWriteObject();
        }

        /**
         * Reconstructs the {@code BidirectionalDequeIterator} from
         * the provided stream and marks that this iterator should
         * check that its next element is still valid
         *
         * @see BidirectionalDequeIterator#checkForNextElementUpdates()
         */
        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {

            // read in all the non-transient state
            s.defaultReadObject();

            // mark that the iterator should recheck what its next
            // element is prior to returning any next element.
            recheckNextElement = true;
        }
    }

    /**
     * An internal marker class for persistently storing the state of
     * the iterator and the direction in which it is going while the
     * iterator has been serialized.  When an element is removed, this
     * class is used by the deque to determine which of the
     * neighboring elements of the newly removed should be set as the
     * serialized iterator's next element.  This class is used if the
     * deque supports concurrent iterators.
     * 
     * @see ScalableDeque#checkIterators(Element)
     * @see ScalableDeque#supportsConcurrentIterators()
     *
     * @param <E> the type of elements returned by the deque's
     *        iterator
     */
    private static class IteratorMarker<E> implements Serializable {

        /**
         * {@inheritDoc}
         */
        private static final long serialVersionUID = 1L;
        /**
         * Whether the iterator that has marked the element as its
         * next element is going in reverse
         */
        final boolean isReverse;
        /**
         * The next element that the iterator will visit
         */
        ManagedReference<Element<E>> nextElement;

        /**
         * Constructs a new {@code IteratorMarker} to represent the
         * next element to be visited by the iterator
         *
         * @param isReverse whether the iterator is traversing the
         *        deque from tail to head
         * @param nextElement the next element in the deque that the
         *        iterator will visit
         */
        public IteratorMarker(boolean isReverse,
                              ManagedReference<Element<E>> nextElement) {
            this.isReverse = isReverse;
            this.nextElement = nextElement;
        }
    }

    /**
     * A helper task that will remove the {@code ManagedObject} node
     * instances of the dequeue after a {@code clear} operation has
     * been performed.
     *
     * @see ScalableDeque#clear()
     * @see ScalableDeque#removingObject()
     *
     * @param <E> the type of elements being removed.
     */
    private static class AsynchronousClearTask<E>
            implements ManagedObject, Serializable, Task {

        /**
         * {@inheritDoc}
         */
        private static final long serialVersionUID = 1L;
        /**
         * The maximum number of entries to remove in a single run of
         * tasks that asynchronously remove nodes and entries.
         */
        private static final int MAX_REMOVE_ELEMENTS = 50;
        /**
         * A reference to the node that should be next removed
         */
        private ManagedReference<Element<E>> curElement;

        /**
         * Constructs the task with the first node in the list of
         * entries to be removed
         * 
         * @param headElement the element at the head of the deque at
         *        the time of the {@code clear} operation
         */
        public AsynchronousClearTask(ManagedReference<Element<E>> headElement) {
            this.curElement = headElement;
        }

        /**
         * Clears a finite number of entries and re-enqueues this task
         * if more entries remain.
         */
        public void run() {
            int removed = 0;
            while (removed < MAX_REMOVE_ELEMENTS && curElement != null) {
                // remove the current node
                Element<E> e = curElement.get();
                AppContext.getDataManager().removeObject(e);
                curElement = e.nextElement;
                removed++;
            }

            // if there are still have more nodes to clean up, then
            // re-enqueue this task
            if (curElement != null) {
                // mark that the task has updated its state
                AppContext.getDataManager().markForUpdate(this);
                AppContext.getTaskManager().scheduleTask(this);
            } else {
                // otherwise, this has has finished, so remove it from the
                // data store
                AppContext.getDataManager().removeObject(this);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
        return (T) object;
    }
}

