/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.app.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.util.ScalableHashSet;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link ScalableHashSet} class. */
@RunWith(FilteredNameRunner.class)
public class TestScalableHashSet extends Assert {

    /** A fixed random number generator. */
    private static final Random random = new Random(1111961);

    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    /** A set to test. */
    private ScalableHashSet<Object> set;

    /** An object to use in tests. */
    private Int one;

    /** Setup */
    @BeforeClass public static void setUpClass() throws Exception {
	serverNode = new SgsTestNode("TestScalableHashSet", null, null);
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    /** Per-test setup */
    @Before public void setUp() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    set = new ScalableHashSet<Object>();
		    one = new Int(1);
		    endTransaction();
		}
	    }, taskOwner);
    }

    /** Teardown. */
    @AfterClass public static void tearDownClass() throws Exception {
        serverNode.shutdown(true);
    }

    /* -- Tests -- */

    /* Test no-arg constructor */

    @Test public void testConstructorNoArg() throws Exception {
	txnScheduler.runTask(
            new TestTask(new TestAbstractKernelRunnable() {
                public void run() {
                    assertTrue(set.isEmpty());
                }
            }), taskOwner);
    }

    /* Test one-arg constructor */

    @Test public void testConstructorOneArg() throws Exception {
	txnScheduler.runTask(
            new TestTask(new TestAbstractKernelRunnable() {
                public void run() {
		    try {
			new ScalableHashSet(-1);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {}
		    try {
			new ScalableHashSet(0);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {}
		    new ScalableHashSet(1);
		    new ScalableHashSet(8);
		}
	    }), taskOwner);
    }

    /* Test copy constructor */

    @Test public void testCopyConstructor() throws Exception {
	final Set<Integer> anotherSet = new HashSet<Integer>();
	txnScheduler.runTask(
            new TestTask(new TestAbstractKernelRunnable() {
                public void run() {
		    try {
			new ScalableHashSet<Object>(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    anotherSet.add(null);
		    anotherSet.add(1);
		    anotherSet.add(2);
		    set = new ScalableHashSet<Object>(anotherSet);
		    assertEquals(anotherSet, set);
		}
	    }), taskOwner);
	txnScheduler.runTask(
            new TestTask(new TestAbstractKernelRunnable() {
                public void run() {
		    assertEquals(anotherSet, set);
		}
	    }), taskOwner);
    }

    @Test public void testCopyConstructorObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    try {
			new ScalableHashSet<Object>(set);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
		    }
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    try {
			new ScalableHashSet<Object>(set);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
		    }
		}
	    }), taskOwner);
    }

    /* Test add */

    @Test public void testAdd() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertTrue(set.add(1));
		    assertFalse(set.add(1));
		    set.remove(1);
		    assertTrue(set.add(1));
		    assertTrue(set.add(null));
		    assertFalse(set.add(null));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertTrue(set.contains(1));
		    assertTrue(set.contains(null));
		    assertEquals(2, set.size());
		}
	    }), taskOwner);
    }

    @Test public void testAddObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    one = new Int(1);
		    assertTrue(set.add(one));
		    dataService.removeObject(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertTrue(set.add(new Int(1)));
		}
	    }), taskOwner);
    }

    /* Test clear */

    @Test public void testClear() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(1);
		    set.add(null);
		    DoneRemoving.init();
		    set.clear();
		    assertTrue(set.isEmpty());
		}
	    }), taskOwner);
	DoneRemoving.await(1);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.clear();
		    assertTrue(set.isEmpty());
		}
	    }), taskOwner);
	DoneRemoving.await(1);
    }

    @Test public void testClearObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    DoneRemoving.init();
		    set.clear();
		    assertTrue(set.isEmpty());
		    one = new Int(1);
		    set.add(one);
		}
	    }), taskOwner);
	DoneRemoving.await(1);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.clear();
		    assertTrue(set.isEmpty());
		}
	    }), taskOwner);
	DoneRemoving.await(1);
    }

    /* Test contains */

    @Test public void testContains() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.contains(1));
		    assertFalse(set.contains(null));
		    set.add(1);
		    set.add(null);
		    assertTrue(set.contains(1));
		    assertTrue(set.contains(null));
		    assertFalse(set.contains(2));
		}
	    }), taskOwner);
    }

    @Test public void testContainsObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    assertFalse(set.contains(one));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.contains(one));
		}
	    }), taskOwner);
    }

    /* Test isEmpty */

    @Test public void testIsEmpty() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertTrue(set.isEmpty());
		    set.add(null);
		    assertFalse(set.isEmpty());
		    set.remove(null);
		    assertTrue(set.isEmpty());
		    set.add(1);
		    assertFalse(set.isEmpty());
		    set.remove(1);
		    assertTrue(set.isEmpty());
		}
	    }), taskOwner);
    }

    @Test public void testIsEmptyObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    assertFalse(set.isEmpty());
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.isEmpty());
		}
	    }), taskOwner);
    }

    /* Test iterator */

    @SuppressWarnings("unchecked")
    @Test public void testIterator() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(null);
		    set.add(1);
		    set.add(2);
		    Iterator<Object> iter = set.iterator();
		    dataService.setBinding("iter",
					   new ManagedSerializable(iter));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ManagedSerializable<Iterator<Object>> msIter =
			uncheckedCast(dataService.getBinding("iter"));
		    dataService.markForUpdate(msIter);
		    Iterator<Object> iter = msIter.get();
		    int count = 0;
		    while (iter.hasNext()) {
			iter.next();
			count++;
		    }
		    assertEquals(3, count);
		}
	    }), taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorCollectionNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		    Iterator<Object> iter = set.iterator();
		    dataService.setBinding("iter",
					   new ManagedSerializable(iter));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    DoneRemoving.init();
		    dataService.removeObject(set);
		}
	    }), taskOwner);
	DoneRemoving.await(1);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ManagedSerializable<Iterator<Object>> msIter =
			uncheckedCast(dataService.getBinding("iter"));
		    dataService.markForUpdate(msIter);
		    Iterator<Object> iter = msIter.get();
		    try {
			iter.next();
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			System.err.println(e);
		    }
		    try {
			iter.hasNext();
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			System.err.println(e);
		    }
		    try {
			iter.remove();
			fail("Expected an exception");
		    } catch (ObjectNotFoundException e) {
			System.err.println(e);
		    } catch (IllegalStateException e) {
			System.err.println(e);
		    }
		}
	    }), taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		    set.add(new Int(2));
		    Iterator<Object> iter = set.iterator();
		    dataService.setBinding("iter",
					   new ManagedSerializable(iter));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ManagedSerializable<Iterator<Object>> msIter =
			uncheckedCast(dataService.getBinding("iter"));
		    dataService.markForUpdate(msIter);
		    Iterator<Object> iter = msIter.get();
		    int count = 0;
		    while (iter.hasNext()) {
			try {
			    assertEquals(new Int(2), iter.next());
			    count++;
			} catch (ObjectNotFoundException e) {
			}
		    }
		    assertEquals(1, count);
		}
	    }), taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorRemove() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Iterator<Object> iter = set.iterator();
		    try {
			iter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    set.add(one);
		    set.add(new Int(2));
		    set.add(new Int(3));
		    try {
			iter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    dataService.setBinding("iter",
					   new ManagedSerializable(iter));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    ManagedSerializable<Iterator<Object>> msIter =
			uncheckedCast(dataService.getBinding("iter"));
		    dataService.markForUpdate(msIter);
		    Iterator<Object> iter = msIter.get();
		    while (iter.hasNext()) {
			Object next = iter.next();
			if (one.equals(next)) {
			    iter.remove();
			    try {
				iter.remove();
				fail("Expected IllegalStateException");
			    } catch (IllegalStateException e) {
			    }
			}
		    }
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Iterator<Object> iter = set.iterator();
		    int count = 0;
		    while (iter.hasNext()) {
			assertFalse(one.equals(iter.next()));
			count++;
		    }
		    assertEquals(2, count);
		}
	    }), taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorRetainAcrossTransactions() throws Exception {
	final AtomicReference<Iterator<Object>> iterRef = new AtomicReference();
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		    Iterator<Object> iter = set.iterator();
		    iterRef.set(iter);
		    dataService.setBinding("iter",
					   new ManagedSerializable(iter));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Iterator<Object> iter = iterRef.get();
		    try {
			iter.hasNext();
			fail("Expected TransactionNotActiveException");
		    } catch (TransactionNotActiveException e) {
		    }
		    try {
			iter.next();
			fail("Expected TransactionNotActiveException");
		    } catch (TransactionNotActiveException e) {
		    }
		    try {
			iter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		}
	    }), taskOwner);
    }

    /* Test remove */

    @Test public void testRemove() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.remove(1));
		    assertFalse(set.remove(null));
		    set.add(1);
		    set.add(null);
		    assertTrue(set.remove(1));
		    assertFalse(set.contains(1));
		    assertFalse(set.remove(1));
		    assertTrue(set.remove(null));
		    assertFalse(set.contains(null));
		    assertFalse(set.remove(null));
		}
	    }), taskOwner);
    }

    @Test public void testRemoveAfterSerialization() throws Exception {
	
	final String SET_NAME = "test.remove.after.serialization";
	
	// store the set in the db

	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.remove(1));
		    assertFalse(set.remove(null));
		    set.add(1);
		    set.add(null);
		    dataService.setBinding(SET_NAME, set);
		}
	    }), taskOwner);

	// next reload the set from the db to test for the correct 
	// handling of the Marker flag.

	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {

		    ScalableHashSet deserialized = 
			(ScalableHashSet)(dataService.getBinding(SET_NAME));
		    
		    assertTrue(deserialized.remove(1));
		    assertFalse(deserialized.contains(1));
		    assertFalse(deserialized.remove(1));
		    assertTrue(deserialized.remove(null));
		    assertFalse(deserialized.contains(null));
		    assertFalse(deserialized.remove(null));
		}
	    }), taskOwner);
    }


    @Test public void testRemoveObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    one = new Int(1);
		    assertFalse(set.remove(one));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.remove(one));
		}
	    }), taskOwner);
    }

    /* Test size */

    @Test public void testSize() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertEquals(0, set.size());
		    set.add(1);
		    assertEquals(1, set.size());
		    set.add(2);
		    assertEquals(2, set.size());
		    set.add(2);
		    assertEquals(2, set.size());
		    DoneRemoving.init();
		    set.clear();
		    assertEquals(0, set.size());
		}
	    }), taskOwner);
	DoneRemoving.await(1);
    }

    @Test public void testSizeObjectNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    assertEquals(1, set.size());
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertEquals(1, set.size());
		}
	    }), taskOwner);
    }

    /* Test equals and hashCode */

    @Test public void testEquals() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Set<Object> control = new HashSet<Object>();
		    assertTrue(set.equals(control));
		    assertEquals(control.hashCode(), set.hashCode());
		    for (int i = 0; i < 50; i++) {
			int n = random.nextInt();
			set.add(n);
			control.add(n);
		    }
		    assertTrue(set.equals(control));
		    assertEquals(control.hashCode(), set.hashCode());
		}
	    }), taskOwner);
    }

    @Test public void testEqualsObjectNotFound() throws Exception {
	final Set<Object> empty = new HashSet<Object>();
	final Set<Object> containsOne = new HashSet<Object>();
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    containsOne.add(one);
		    set.add(one);
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(one);
		    assertFalse(set.equals(empty));
		    assertFalse(set.equals(containsOne));
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertFalse(set.equals(empty));
		    assertFalse(set.equals(containsOne));
		}
	    }), taskOwner);
    }

    /* Test removeAll */
 
    @Test public void testRemoveAll() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    try {
			set.removeAll(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    set.add(null);
		    set.add(1);
		    set.add(2);
		    set.add(3);
		    Set<Object> other = new HashSet<Object>();
		    other.add(null);
		    other.add(2);
		    other.add(6);
		    set.removeAll(other);
		    assertEquals(2, set.size());
		    assertTrue(set.contains(1));
		    assertTrue(set.contains(3));
		}
	    }), taskOwner);
    }

    /* Test addAll */

    @Test public void testAddAll() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    try {
			set.addAll(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    set.add(1);
		    set.add(2);
		    set.add(3);
		    Set<Object> other = new HashSet<Object>();
		    other.add(null);
		    other.add(3);
		    other.add(4);
		    other.add(5);
		    set.addAll(other);
		    assertEquals(6, set.size());
		    set.contains(4);
		    set.contains(5);
		}
	    }), taskOwner);
    }

    /* Test containsAll */

    @Test public void testContainsAll() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    try {
			set.containsAll(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    Set<Object> other = new HashSet<Object>();
		    assertTrue(set.containsAll(other));
		    other.add(1);
		    assertFalse(set.containsAll(other));
		    set.add(null);
		    set.add(1);
		    assertTrue(set.containsAll(other));
		    DoneRemoving.init();
		    set.clear();
		    assertFalse(set.containsAll(other));
		}
	    }), taskOwner);
	DoneRemoving.await(1);
    }

    /* Test retainAll */

    @Test public void testRetainAll() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    try {
			set.retainAll(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    Set<Object> other = new HashSet<Object>();
		    assertFalse(set.retainAll(other));
		    other.add(1);
		    assertFalse(set.retainAll(other));	
		    set.add(1);
		    set.add(2);
		    assertTrue(set.retainAll(other));
		    assertEquals(1, set.size());
		    assertTrue(set.contains(1));
		}
	    }), taskOwner);
    }

    /* Test toArray */

    @Test public void testToArray() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertEquals(0, set.toArray().length);
		    set.add(1);
		    Object[] result = set.toArray();
		    assertEquals(1, result.length);
		    assertEquals(new Integer(1), result[0]);
		    Integer[] intResult = new Integer[1];
		    set.toArray(intResult);
		    assertEquals(new Integer(1), intResult[0]);
		}
	    }), taskOwner);
    }

    /* Test toString */

    @Test public void testToString() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    assertEquals("[]", set.toString());
		    set.add(1);
		    assertEquals("[1]", set.toString());
		}
	    }), taskOwner);
    }

    /* Test calling DataManager.removeObject on the set */

    @Test public void testRemoveObjectSet() throws Exception {
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    DoneRemoving.init();
		    dataService.removeObject(set);
		    set = null;
		}
	    }), taskOwner);
	DoneRemoving.await(1);
	int count = getObjectCount();
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    set = new ScalableHashSet<Object>();
		    for (int i = 0; i < 50; i++) {
			set.add(random.nextInt());
		    }
		}
	    }), taskOwner);
	txnScheduler.runTask(
	    new TestTask(new TestAbstractKernelRunnable() {
		public void run() {
		    dataService.removeObject(set);
		    set = null;
		}
	    }), taskOwner);
	DoneRemoving.await(1);
	assertEquals(count, getObjectCount());
    }

    /* -- Utilities -- */
    
    /**
     * Stores fields, if they are not null, into bindings.
     */
    private void endTransaction() throws Exception {
	if (set != null) {
	    try {
		dataService.setBinding("set", set);
	    } catch (ObjectNotFoundException e) {
	    }
	}
	if (one != null) {
	    try {
		dataService.setBinding("one", one);
	    } catch (ObjectNotFoundException e) {
	    }
	}
    }

    /**
     * Updates fields from bindings, setting the fields to null if the
     * objects are not found.
     */
    @SuppressWarnings("unchecked")
    private void startTransaction() throws Exception {
	try {
	    set = (ScalableHashSet) dataService.getBinding("set");
	} catch (ObjectNotFoundException e) {
	    set = null;
	}
	try {
	    one = (Int) dataService.getBinding("one");
	} catch (ObjectNotFoundException e) {
	    one = null;
	}
    }

    private class TestTask extends TestAbstractKernelRunnable {
        private final TestAbstractKernelRunnable r;
        TestTask(TestAbstractKernelRunnable r) { this.r = r; }
        public void run() throws Exception {
            startTransaction();
            r.run();
            endTransaction();
        }
    }

    /**
     * A managed object that is equal to objects of the same type with the
     * same value.
     */
    static class Int implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1L;
	private final int i;
	Int(int i) {
	    this.i = i;
	}
	public int hashCode() {
	    return i;
	}
	public boolean equals(Object o) {
	    return o instanceof Int && i == ((Int) o).i;
	}
    }

    /** Returns the current number of objects, using its own transactions. */
    private int getObjectCount() throws Exception {
	final AtomicInteger countRef = new AtomicInteger(0);
	final AtomicReference<BigInteger> lastRef =
	    new AtomicReference<BigInteger>();
	final AtomicBoolean done = new AtomicBoolean(false);
	while (!done.get()) {
	    txnScheduler.runTask(
		new TestTask(new TestAbstractKernelRunnable() {
		    public void run() {
			BigInteger last = lastRef.get();
			int count;
			for (count = 0; count < 50; count++) {
			    BigInteger next = dataService.nextObjectId(last);
			    if (next == null) {
				done.set(true);
			    }
			    last = next;
			}
			countRef.addAndGet(count);
			lastRef.set(last);
		    }
		}), taskOwner);
	}
	return countRef.get();
    }

    /** Prints the current objects above the specified value, for debugging. */
    private void printObjects(BigInteger id) {
	while (true) {
	    id = dataService.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    try {
		Object obj = dataService.createReferenceForId(id).get();
		System.err.println(id + ": (" + obj.getClass().getName() +
				   ") " + obj);
	    } catch (Exception e) {
		System.err.println(id + ": " + e);
	    }
	}
    }
}
