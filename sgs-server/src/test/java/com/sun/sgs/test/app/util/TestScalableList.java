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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.app.util.ScalableList;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;

/**
 * Test the {@link ScalableList} class.
 */
@RunWith(FilteredNameRunner.class)
public class TestScalableList extends Assert {

    private static long randomSeed;
    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    /**
     * Test management.
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
	serverNode =
		new SgsTestNode("TestScalableList", null,
			createProps("TestScalableList"));
	txnScheduler =
		serverNode.getSystemRegistry().getComponent(
			TransactionScheduler.class);
	taskOwner = serverNode.getProxy().getCurrentOwner();
	dataService = serverNode.getDataService();
        randomSeed = Long.getLong("test.seed",
            System.currentTimeMillis());
        System.err.println("test.seed=" + randomSeed);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
	serverNode.shutdown(true);
    }

    private static Properties createProps(String appName) throws Exception {
	Properties props =
		SgsTestNode.getDefaultProperties(appName, null,
			SgsTestNode.DummyAppListener.class);
	props.setProperty("com.sun.sgs.txn.timeout", "10000000");
	return props;
    }

    /**
     * Tests instantiating a ScalableList using the copy constructor.
     * 
     * @throws Exception
     */
    @Test
    public void testCopyConstructor() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = null;
		Collection<String> c = new ArrayList<String>();
		c.add("A");

		try {
		    list = new ScalableList<String>(-1, -1, c);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

		try {
		    list = new ScalableList<String>(-1, 2, c);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		    list = new ScalableList<String>(2, -1, c);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		    list = new ScalableList<String>(1, 0, c);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

		try {
		    list = new ScalableList<String>(3, 3, c);
		} catch (Exception e) {
		    fail("Was not expecting an exception: " +
			    e.getLocalizedMessage());
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests creating a new {@code ScalableList} using the empty constructor
     * 
     * @throws Exception
     */
    @Test
    public void testEmptyConstructor() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = null;
		try {
		    list = new ScalableList<String>();
		} catch (Exception e) {
		    fail("Was not expecting an exception: " +
			    e.getLocalizedMessage());
		}
		AppContext.getDataManager().removeObject(list);

	    }
	}, taskOwner);
    }

    /**
     * Tests instantiating a ScalableList with illegal argument(s).
     * 
     * @throws Exception
     */
    @Test
    public void testConstructorWithIllegalArgs() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = null;
		try {
		    list = new ScalableList<String>(-1, -1);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

		try {
		    list = new ScalableList<String>(-1, 2);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		    list = new ScalableList<String>(2, -1);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		    list = new ScalableList<String>(1, 0);
		    fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

	    }
	}, taskOwner);
    }

    /**
     * Tests instantiating a ScalableList with legal argument(s).
     * 
     * @throws Exception
     */
    @Test
    public void testConstructorWithLegalArgs() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = null;
		try {
		    list = new ScalableList<String>(2, 1);
		} catch (Exception e) {
		    fail("Did not expect exception: " +
			    e.getLocalizedMessage());
		}

		try {
		    list = new ScalableList<String>(99, 99);
		} catch (Exception e) {
		    fail("Did not expect exception: " +
			    e.getLocalizedMessage());
		}

		try {
		    list = new ScalableList<String>(2, 999);
		} catch (Exception e) {
		    fail("Did not expect exception: " +
			    e.getLocalizedMessage());
		}

		try {
		    list = new ScalableList<String>(999, 2);
		} catch (Exception e) {
		    fail("Did not expect exception: " +
			    e.getLocalizedMessage());
		}

		try {
		    Random random = new Random(randomSeed);
		    int rand1 = random.nextInt(999) + 2;
		    int rand2 = random.nextInt(999) + 1;
		    list = new ScalableList<String>(rand1, rand2);
		} catch (Exception e) {
		    fail("(test.seed=" + randomSeed + ") Did not expect exception: " +
			    e.getLocalizedMessage());
		}

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /*
     * ///////////// NON-EXCEPTIONAL (NORMAL) USE CASES /////////////////
     */

    /**
     * Tests adding an item to an empty list.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingToEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));

		assertEquals(1, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests appending a value to the list
     * 
     * @throws Exception
     */
    @Test
    public void testAppendingToList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		assertTrue(list.add("B"));

		assertEquals(2, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests appending a value to a non-empty list
     * 
     * @throws Exception
     */
    @Test
    public void testPrependToNonEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add(0, "B");

		assertEquals(2, list.size());
		assertEquals("B", list.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests prepending a value into an empty list
     * 
     * @throws Exception
     */
    @Test
    public void testPrependIntoEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add(0, "A");

		assertEquals(1, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests inserting a value into a populated list
     * 
     * @throws Exception
     */
    @Test
    public void testInsertIntoList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add(1, "Z");

		assertEquals(3, list.size());
		assertEquals("Z", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests adding all the elements in a collection to an empty list.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingAllToEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		Collection<String> c = new ArrayList<String>();
		c.add("A");
		c.add("B");
		c.add("C");

		assertTrue(list.addAll(c));
		assertEquals(3, list.size());

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests appending all elements in a collection to a non-empty list.
     * 
     * @throws Exception
     */
    @Test
    public void testAppendingAllToNonEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(8, 8);
		list.add("A");
		list.add("B");

		Collection<String> c = new ArrayList<String>();
		c.add("C");
		c.add("D");
		c.add("E");

		assertTrue(list.addAll(c));
		assertEquals(5, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether two lists with identical elements are seen as being
     * equal.
     * 
     * @throws Exception
     */
    @Test
    public void testEquals() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(8, 8);
		ManagedReference<ScalableList<String>> list1Ref =
			dataService.createReference(list);
		ManagedReference<ScalableList<String>> list2Ref =
			dataService.createReference(list);

		assertTrue(list1Ref.get().equals(list2Ref.get()));

		// Test two different list implementations
		List<String> ref1 = new ArrayList<String>();
		ref1.add("A");
		ref1.add("B");
		ref1.add("C");
		ref1.add("D");
		ref1.add("E");
		list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.add("E");
		assertTrue(ref1.equals(list));

		// Test two differently-sized branching factors
		ScalableList<String> smallerBranchingFactor =
			new ScalableList<String>(3, 3);
		ScalableList<String> largerBranchingFactor =
			new ScalableList<String>(9, 3);
		smallerBranchingFactor.add("A");
		smallerBranchingFactor.add("B");
		smallerBranchingFactor.add("C");
		smallerBranchingFactor.add("D");
		smallerBranchingFactor.add("E");
		smallerBranchingFactor.add("F");
		smallerBranchingFactor.add("G");
		smallerBranchingFactor.add("H");
		smallerBranchingFactor.add("I");
		smallerBranchingFactor.add("J");
		largerBranchingFactor.add("A");
		largerBranchingFactor.add("B");
		largerBranchingFactor.add("C");
		largerBranchingFactor.add("D");
		largerBranchingFactor.add("E");
		largerBranchingFactor.add("F");
		largerBranchingFactor.add("G");
		largerBranchingFactor.add("H");
		largerBranchingFactor.add("I");
		largerBranchingFactor.add("J");
		assertTrue(smallerBranchingFactor
			.equals(largerBranchingFactor));

		// Test two differently-sized bucket sizes
		ScalableList<String> smallerBucketSize =
			new ScalableList<String>(3, 3);
		ScalableList<String> largerBucketSize =
			new ScalableList<String>(3, 9);
		smallerBucketSize.add("A");
		smallerBucketSize.add("B");
		smallerBucketSize.add("C");
		smallerBucketSize.add("D");
		smallerBucketSize.add("E");
		smallerBucketSize.add("F");
		smallerBucketSize.add("G");
		smallerBucketSize.add("H");
		smallerBucketSize.add("I");
		smallerBucketSize.add("J");
		largerBucketSize.add("A");
		largerBucketSize.add("B");
		largerBucketSize.add("C");
		largerBucketSize.add("D");
		largerBucketSize.add("E");
		largerBucketSize.add("F");
		largerBucketSize.add("G");
		largerBucketSize.add("H");
		largerBucketSize.add("I");
		largerBucketSize.add("J");
		assertTrue(smallerBranchingFactor
			.equals(largerBranchingFactor));

		AppContext.getDataManager().removeObject(list);

		AppContext.getDataManager().removeObject(
			smallerBranchingFactor);
		AppContext.getDataManager().removeObject(
			largerBranchingFactor);
		AppContext.getDataManager().removeObject(smallerBucketSize);
		AppContext.getDataManager().removeObject(largerBucketSize);

	    }
	}, taskOwner);
    }

    /**
     * Tests whether a collection of elements can be added successfully to the
     * middle of a populated list
     * 
     * @throws Exception
     */
    @Test
    public void testAddingAllMiddleToNonEmptyList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(8, 8);
		list.add("A");
		list.add("B");

		Collection<String> c = new ArrayList<String>();
		c.add("C");
		c.add("D");
		c.add("E");

		assertTrue(list.addAll(1, c));
		assertEquals(5, list.size());
		// get the middle element
		assertEquals("D", list.get(2));

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the head element of the list can be retrieved when the
     * list size is unity
     * 
     * @throws Exception
     */
    @Test
    public void testGetHeadFromListOfSizeOne() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");

		assertEquals("A", list.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * @throws Exception
     */
    @Test
    public void testGetFirstElement() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("A", list.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the last element of the list can be retrieved
     * 
     * @throws Exception
     */
    @Test
    public void testGetLastElement() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("C", list.get(2));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the accuracy of retrieving a value from the middle of the list.
     * 
     * @throws Exception
     */
    @Test
    public void testGetMiddleFromListOfArbitrarySize() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("B", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to retrieve the last element using the get
     * method.
     * 
     * @throws Exception
     */
    @Test
    public void testGetEndFromListOfArbitrarySize() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("C", list.get(2));

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests if the head can be successfully removed from the list
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveHeadFromList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(0);
		assertEquals(2, list.size());
		assertEquals("B", list.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests if a middle element can be removed successfully from the list
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveMiddleFromList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.remove(1);

		assertEquals(2, list.size());
		assertEquals("C", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the tail (last) element can be removed from the list
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveEndFromList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);

		assertEquals(2, list.size());
		assertEquals("B", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the removed item contains the expected value.
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveAndVerifyResult() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(1);

		assertEquals("B", obj.toString());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the removal of the head using the designated method call.
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveHead() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.remove(0);

		assertEquals(2, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests that the removed value has an expected value
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveHeadAndVerifyResult() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(0);

		assertEquals("A", obj);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the removal of the tail (last) element using the designated
     * method call.
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveTail() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);

		assertEquals(2, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the removal of the tail using the designated API call and
     * verifies that the value is the expected one.
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveTailAndVerifyResult() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(2);

		assertEquals("C", obj);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the removal of elements using their reference
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveUsingObjectReference() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		String obj = new String("abc");

		list.add("A");
		list.add("B");
		list.add(obj);

		list.remove(obj);

		assertEquals(2, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the removal of an object by using its reference and testing that
     * the value is equal to the reference
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveUsingObjectReferenceAndVerify() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		String obj = new String("abc");

		list.add("A");
		list.add("B");
		list.add(obj);

		assertTrue(list.remove(obj));
		assertEquals(-1, list.indexOf(obj));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the iterator identifies the proper quantity and value for
     * the list's elements.
     * 
     * @throws Exception
     */
    @Test
    public void testIterator() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");

		Iterator<String> iter = list.iterator();
		assertTrue(iter.hasNext());

		// Start iterations
		int size = 0;
		String value = null;
		while (iter.hasNext()) {
		    value = iter.next();
		    size++;

		    // Check a random iteration
		    if (size == 2) {
			assertEquals("B", value);
		    }
		}
		// Iteration amount should equal list size
		assertEquals(size, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the list can determine if it is empty or not.
     * 
     * @throws Exception
     */
    @Test
    public void testIsEmpty() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);

		assertTrue(list.isEmpty());

		list.add("A");
		list.add("B");
		list.add("C");

		list.remove(0);
		list.remove(0);
		list.remove(0);

		assertTrue(list.isEmpty());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether a valid index of an object in the list can be properly
     * retrieved
     * 
     * @throws Exception
     */
    @Test
    public void testIndexOf() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals(1, list.indexOf("B"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the last index of a valid element can be properly found.
     * 
     * @throws Exception
     */
    @Test
    public void testLastIndexOf() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		String s1 = new String("A");
		String s2 = new String("B");

		list.add(s1);
		list.add(s2);
		list.add(s1);
		list.add(s2);

		assertEquals(3, list.lastIndexOf(s2));
		assertEquals(2, list.lastIndexOf(s1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether a valid index can be set
     * 
     * @throws Exception
     */
    @Test
    public void testSet() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.set(1, "Z");

		assertEquals(3, list.size());
		assertEquals("Z", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests adding an item to an empty list.
     * 
     * @throws Exception
     */
    @Test
    public void testContainsWithSingleElement() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));

		assertTrue(list.contains("A"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the contains() method
     * 
     * @throws Exception
     */
    @Test
    public void testContainsWithPopulatedList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));

		assertTrue(list.contains("A"));
		assertTrue(list.contains("B"));
		assertTrue(list.contains("C"));

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether items can be found in a list.
     * 
     * @throws Exception
     */
    @Test
    public void testContainsAllWithPopulatedList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add("C");

		assertTrue(list.containsAll(c));
		assertEquals(3, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether a collection of items can be retained
     * 
     * @throws Exception
     */
    @Test
    public void testRetainAll() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		// test that retainAll returns true if changes are made
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		Collection<String> c = new ArrayList<String>();
		c.add("A");
		c.add("B");
		assertTrue(list.retainAll(c));
		assertEquals(2, list.size());
		assertEquals("A", list.get(0));
		assertEquals("B", list.get(1));

		// check that retainAll returns false if contents
		// do not change
		list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));
		c = new ArrayList<String>();
		c.add("B");
		c.add("D");
		c.add("A");
		c.add("C");
		assertFalse(list.retainAll(c));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving a sublist from two arbitrary indices
     * 
     * @throws Exception
     */
    @Test
    public void testSubList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		List<String> newList = list.subList(1, 2);

		assertEquals(1, newList.size());
		assertEquals("B", newList.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an array of the contents
     * 
     * @throws Exception
     */
    @Test
    public void testToArray() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		Object[] array = list.toArray();

		assertEquals(4, list.size());
		assertEquals(4, array.length);
		assertEquals("A", array[0]);
		assertEquals("B", array[1]);
		assertEquals("C", array[2]);
		assertEquals("D", array[3]);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an array of the contents by providing it an array
     * parameter
     * 
     * @throws Exception
     */
    @Test
    public void testToArrayGivenParam() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		Object[] container = new Object[77];
		Object[] array = list.toArray(container);

		assertEquals(4, list.size());
		assertEquals(77, array.length);
		assertEquals("A", array[0]);
		assertEquals("B", array[1]);
		assertEquals("C", array[2]);
		assertEquals("D", array[3]);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    public void testIteratorHasNext() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));
		Iterator<String> iter = list.iterator();

		iter.hasNext();
		iter.hasNext();
		iter.hasNext();
		iter.hasNext();

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an array of the contents by providing it an array
     * parameter
     * 
     * @throws Exception
     */
    @Test
    public void testIteratorRemove() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		Iterator<String> iter = list.iterator();
		assertTrue(iter.hasNext());

		// This should throw an exception because next() has
		// not yet been called
		try {
		    iter.remove();
		    fail("Expecting an IllegalStateException");
		} catch (IllegalStateException ise) {
		} catch (Exception e) {
		    fail("Not expecting the exception: " +
			    e.getLocalizedMessage());
		}
		assertEquals(4, list.size());
		Random random = new Random(randomSeed);
		int randomIndex = random.nextInt(list.size() - 1);
		String verify = list.get(randomIndex);
		int i = 0;

		while (iter.hasNext()) {
		    iter.next();

		    // perform one removal
		    if (i == randomIndex) {
			iter.remove();
		    }
		    i++;

		}

		assertEquals(3, list.size());
		assertEquals(false, list.contains(verify));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the iterator's ability to add an element to the list.
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratorAdd() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		List<String> shadow = new ArrayList<String>();
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));
		shadow.add("A");
		shadow.add("B");
		shadow.add("C");
		shadow.add("D");
		ListIterator<String> shadowIter = shadow.listIterator();
		ListIterator<String> iter = list.listIterator();
		assertTrue(iter.hasNext());
		int count = 0;
		String valueToAddAndCheck = "E";

		while (iter.hasNext()) {
		    iter.next();
		    shadowIter.next();
		    if (count == 2) {
			iter.add(valueToAddAndCheck);
			shadowIter.add(valueToAddAndCheck);
		    }
		    count++;
		}
		assertEquals(shadow.size(), list.size());
		assertEquals(shadow.contains(valueToAddAndCheck), list
			.contains(valueToAddAndCheck));
		assertEquals(shadow.indexOf(valueToAddAndCheck), list
			.indexOf(valueToAddAndCheck));

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the iterator's ability to add an element to the list.
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratoGetNextAndPrevIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		List<String> shadow = new ArrayList<String>();
		shadow.add("A");
		shadow.add("B");
		shadow.add("C");
		shadow.add("D");
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		ListIterator<String> shadowIter = shadow.listIterator();
		ListIterator<String> iter = list.listIterator();
		assertEquals(shadowIter.hasNext(), iter.hasNext());

		// check the indices
		while (shadowIter.hasNext()) {
		    iter.next();
		    shadowIter.next();
		    assertEquals(shadowIter.previousIndex(), iter
			    .previousIndex());
		    assertEquals(shadowIter.nextIndex(), iter.nextIndex());
		}

		assertEquals(shadow.size(), list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the {@code ListIterator}'s ability to fetch next and previous an
     * element to the list.
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratorNextAndPrev() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		ArrayList<String> shadow = new ArrayList<String>();
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));
		shadow.add("A");
		shadow.add("B");
		shadow.add("C");
		shadow.add("D");

		ListIterator<String> iter = list.listIterator();
		ListIterator<String> shadowIter = shadow.listIterator();
		assertEquals(shadowIter.hasNext(), iter.hasNext());
		int count = 0;

		// test next()
		while (shadowIter.hasNext()) {
		    assertEquals("next index: " + Integer.toString(count++),
			    shadowIter.next(), iter.next());

		}
		assertEquals(shadowIter.hasNext(), iter.hasNext());

		// test previous()
		while (shadowIter.hasPrevious()) {
		    assertEquals("prev index: " + Integer.toString(count--),
			    shadowIter.previous(), iter.previous());
		}
		assertEquals(shadowIter.hasPrevious(), iter.hasPrevious());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the {@code ListIterator}'s ability to fetch next and previous an
     * element to the list.
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratorSet() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		ArrayList<String> shadow = new ArrayList<String>();
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));
		shadow.add("A");
		shadow.add("B");
		shadow.add("C");
		shadow.add("D");

		ListIterator<String> iter = list.listIterator();
		ListIterator<String> shadowIter = shadow.listIterator();
		assertEquals(shadowIter.hasNext(), iter.hasNext());
		Random random = new Random(randomSeed);
		String newValue;
		int count = 0;

		// test set()
		while (shadowIter.hasNext()) {
		    shadowIter.next();
		    iter.next();
		    newValue = Integer.toString(random.nextInt(100));
		    shadowIter.set(newValue);
		    iter.set(newValue);

		    assertEquals(shadow.get(count), list.get(count));
		    count++;
		}
		assertEquals("(test.seed=" + randomSeed + ")", shadow.size(), list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /*
     * ///////////////// SCALABLE USE CASES (INVOLVING SPLITTING AND DELETING)
     * /////////////////
     */

    /**
     * Tests the clearing mechanism using the {@code AsynchronousClearTask}
     * 
     * @throws Exception
     */
    @Test
    public void testClear() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		list.clear();
		assertTrue(list.isEmpty());
		assertEquals(0, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the clearing mechanism using the {@code AsynchronousClearTask}
     * 
     * @throws Exception
     */
    @Test
    public void testConsecutiveClears() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		list.clear();
		list.clear();
		list.clear();
		assertTrue(list.isEmpty());
		assertEquals(0, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the clearing mechanism using the {@code AsynchronousClearTask}
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleClears() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.clear();
		assertTrue(list.isEmpty());
		assertEquals(-1, list.indexOf("A"));
		assertEquals(-1, list.indexOf("B"));
		assertEquals(-1, list.indexOf("C"));
		assertEquals(-1, list.indexOf("D"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by appending elements exceeding the cluster size
     * 
     * @throws Exception
     */
    @Test
    public void testScalableAppendByVerifyingSize() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.add("E");
		list.add("F");
		// this is double the max child size; the
		// tree should have split to accommodate
		assertEquals(6, list.size());

		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.add("E");
		list.add("F");

		assertEquals(12, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by prepending elements until the list exceeds its
     * clusterSize
     * 
     * @throws Exception
     */
    @Test
    public void testScalablePrependByVerifyingSize() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("L");
		list.add(0, "A");
		list.add(1, "K");
		list.add(1, "J");
		list.add(1, "I");
		list.add(1, "H");

		assertEquals(6, list.size());

		list.add(1, "G");
		list.add(1, "F");
		list.add(1, "E");
		list.add(1, "D");
		list.add(1, "C");
		list.add(1, "B");

		assertEquals(12, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by appending values in excess of its cluster size and
     * checking if the values are consistent.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableAppendByVerifyingValues() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("A", list.get(0));
		assertEquals("D", list.get(3));
		assertEquals("G", list.get(6));
		assertEquals("J", list.get(9));
		assertEquals("L", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by inserting values until the number of elements in
     * the list exceeds the cluster size. The test also verifies that the
     * values are consistent.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableInsertByVerifyingValues() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("L");

		list.add(1, "K");
		list.add(1, "J");
		list.add(1, "I");
		list.add(1, "H");
		list.add(1, "G");
		list.add(1, "F");
		list.add(1, "E");
		list.add(1, "D");
		list.add(1, "C");
		list.add(1, "B");

		assertEquals("A", list.get(0));
		assertEquals("D", list.get(3));
		assertEquals("G", list.get(6));
		assertEquals("J", list.get(9));
		assertEquals("L", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by prepending elements in excess of the cluster size,
     * and verifies that the values are consistent.
     * 
     * @throws Exception
     */
    @Test
    public void testScalablePrependByVerifyingValues() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("L");
		list.add(0, "K");
		list.add(0, "J");
		list.add(0, "I");
		list.add(0, "H");
		list.add(0, "G");
		list.add(0, "F");
		list.add(0, "E");
		list.add(0, "D");
		list.add(0, "C");
		list.add(0, "B");
		list.add(0, "A");

		assertEquals("A", list.get(0));
		assertEquals("D", list.get(3));
		assertEquals("G", list.get(6));
		assertEquals("J", list.get(9));
		assertEquals("L", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by adding a collection to make the list's size exceed
     * that specified by the cluster size
     * 
     * @throws Exception
     */
    @Test
    public void testScalableAddAll() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("L");

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add("C");
		c.add("D");
		c.add("E");
		c.add("F");
		c.add("G");
		c.add("H");
		c.add("I");
		c.add("J");
		c.add("K");

		list.addAll(1, c);

		assertEquals("A", list.get(0));
		assertEquals("D", list.get(3));
		assertEquals("G", list.get(6));
		assertEquals("J", list.get(9));
		assertEquals("L", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Creates a list of size 12 by appending the values 0 through 11 in
     * order.
     * 
     * @return
     */
    private ScalableList<String> makeList() {
	ScalableList<String> list = new ScalableList<String>(3, 3);
	list.add("A");
	list.add("B");
	list.add("C");
	list.add("D");
	list.add("E");
	list.add("F");
	list.add("G");
	list.add("H");
	list.add("I");
	list.add("J");
	list.add("K");
	list.add("L");
	return list;
    }

    /**
     * Tests scalability by removing elements from a large list and verifying
     * the expected size.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableRemoveByVerifyingSize() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();
		list.remove(5);
		assertEquals("G", list.get(5));
		list.remove(9);
		assertEquals("L", list.get(9));
		list.remove(1);
		assertEquals("C", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by removing elements from a large list and verifying
     * that the values are consistent.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableRemoveByVerifyingValues() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add(0, "L");
		list.add(0, "K");
		list.add(0, "J");
		list.add(0, "I");
		list.add(0, "H");
		list.add(0, "G");

		assertEquals(6, list.size());
		assertEquals("H", list.get(1));

		list.add(0, "F");
		list.add(0, "E");
		list.add(0, "D");
		list.add(0, "C");
		list.add(0, "B");
		list.add(0, "A");

		assertEquals(12, list.size());
		assertEquals("B", list.get(1));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by assessing the index of values in a large list
     * 
     * @throws Exception
     */
    @Test
    public void testScalableIndexOf() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = makeList();
		assertEquals(2, list.indexOf("C"));
		assertEquals(8, list.indexOf("I"));
		assertEquals(11, list.indexOf("L"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by assessing the last index of duplicated elements in
     * a large list.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableLastIndexOf() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = makeList();
		list.add("A");
		assertEquals(12, list.lastIndexOf("A"));

		list.add("A");
		list.add("A");
		assertEquals(14, list.lastIndexOf("A"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by assessing the accuracy of the retrieved element
     * from a set operation, as well as its value after the operation takes
     * place.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableSet() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();
		assertEquals("C", list.set(2, "Z"));
		assertEquals("Z", list.get(2));
		assertEquals("I", list.set(8, "ZZ"));
		assertEquals("ZZ", list.get(8));
		assertEquals("L", list.set(11, "ZZZ"));
		assertEquals("ZZZ", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by verifying an iterator's size and values for a
     * large list.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableIterator() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();
		Iterator<String> iter = list.iterator();

		assertEquals(true, iter.hasNext());

		// Go through all the elements
		int size = 0;

		while (iter.hasNext()) {
		    iter.next();
		    size++;

		    // Randomly check values during the iterations
		    if (size == 2) {
			assertEquals("C", list.get(size));
		    }
		    if (size == 8) {
			assertEquals("I", list.get(size));
		    }
		    if (size == 10) {
			assertEquals("K", list.get(size));
		    }
		}
		assertEquals(size, list.size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by obtaining the head element from a large list.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableGetFirst() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("A", list.get(0));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests scalability by retrieving the tail element from a large list.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableGetLast() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("L", list.get(11));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether items can be found in a list.
     * 
     * @throws Exception
     */
    @Test
    public void testScalableContainsAllWithPopulatedList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add("D");
		c.add("I");

		assertTrue(list.containsAll(c));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether a collection of items can be retained
     * 
     * @throws Exception
     */
    @Test
    public void testScalableRetainAll() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		Collection<String> c = new ArrayList<String>();
		c.add("A");
		c.add("D");
		c.add("G");

		assertTrue(list.retainAll(c));
		assertEquals(3, list.size());
		assertEquals("A", list.get(0));
		assertEquals("D", list.get(1));
		assertEquals("G", list.get(2));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving a sublist from two arbitrary indices
     * 
     * @throws Exception
     */
    @Test
    public void testScalableSubList() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		List<String> newList = list.subList(3, 7);

		assertEquals(4, newList.size());
		assertEquals("D", newList.get(0));
		assertEquals("G", newList.get(3));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an array of the contents
     * 
     * @throws Exception
     */
    @Test
    public void testScalableToArray() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		Object[] array = list.toArray();

		assertEquals(12, list.size());
		assertEquals(12, array.length);
		assertEquals("A", array[0]);
		assertEquals("D", array[3]);
		assertEquals("G", array[6]);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an array of the contents by providing it an array
     * parameter
     * 
     * @throws Exception
     */
    @Test
    public void testScalableToArrayGivenParam() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		Object[] container = new Object[77];
		Object[] array = list.toArray(container);

		assertEquals(12, list.size());
		assertEquals(77, array.length);
		assertEquals("A", array[0]);
		assertEquals("D", array[3]);
		assertEquals("G", array[6]);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests arbitrary operations in sequence
     * 
     * @throws Exception
     */
    @Test
    public void testScalableArbitraryOperations() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);

		assertTrue(list.isEmpty());
		list.add("A");
		list.add("B");
		assertNotNull(list.remove(0));
		assertEquals(1, list.size());
		assertEquals(-1, list.indexOf("A"));
		assertEquals(0, list.indexOf("B"));

		list.add("C");
		list.add(1, "D");
		list.add(1, "E");
		list.add(1, "F");
		list.add(1, "G");
		list.add(1, "H");
		assertEquals(7, list.size());
		assertNotNull(list.remove(4));
		assertNotNull(list.remove(4));
		assertEquals(5, list.size());
		assertEquals("C", list.get(4));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests random operations in sequence
     * 
     * @throws Exception
     */
    @Test
    public void testScalableRandomOperations() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		ArrayList<String> shadow = new ArrayList<String>();

		Random random = new Random(randomSeed);

		String opList = "";
		String indexList = "";

		try {
		    // perform random operations
		    for (int i = 0; i < 20; i++) {
			int operation = random.nextInt(5);
			opList += Integer.toString(operation) + ",";

			int randomIndex = shadow.size() - 1;
			if (randomIndex <= 0) {
			    randomIndex = 1;
			}

			randomIndex = random.nextInt(randomIndex);
			indexList += Integer.toString(randomIndex) + ",";
			String value = Integer.toString(random.nextInt(999));

			// output
			//System.err.println("operations: " + opList);
			//System.err.println(" indices: " + indexList);

			String listReturned =
				performRandomOperation(list, operation,
					randomIndex, value);
			String shadowReturned =
				performRandomOperation(shadow, operation,
					randomIndex, value);
			assertEquals("Iteration #:" + i + ", Operation #:" +
				operation, listReturned, shadowReturned);
			assertEquals("The list sizes are different", shadow
				.size(), list.size());

			// check integrity
			for (int j = 0; j < shadow.size(); j++) {
			    assertEquals("(test.seed=" + randomSeed + ") iteration #" + i +
				    ": ", shadow.get(j), list.get(j));
			}
		    }
		} catch (Exception e) {
		    fail("Not expecting an exception: " +
			    e.getLocalizedMessage());
		}

		// Check that the list and shadow list match
		assertEquals(shadow.size(), list.size());

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Performs random operations based on the value provided,
     * 
     * @param list the list to modify
     * @param operation the type of operation
     * @param a random index in the list
     * @param value the value to add, if necessary
     * @return a value corresponding to the operation performed
     */
    private String performRandomOperation(List<String> list, int operation,
	    int randomIndex, String value) {
	final String EMPTY_LIST = "List is empty";
	final String TRUE = "True";
	final String INVALID_OPERATION = "Invalid operation";

	switch (operation) {
	    case 0:
		// append to the list
		return Boolean.toString(list.add(value));
	    case 1:
		// remove the head
		if (list.size() == 0) {
		    return EMPTY_LIST;
		}
		return list.remove(0);
	    case 2:
		// add to the head
		list.add(0, value);
		return TRUE;
	    case 3:
		// remove from the tail
		if (list.size() < 1) {
		    return EMPTY_LIST;
		}
		return list.remove(list.size() - 1);
	    case 4:
		// add intermediate
		if (list.size() == 0) {
		    return EMPTY_LIST;
		}
		list.add(randomIndex, value);
		return TRUE;
	    case 5:
		// remove halfway
		if (list.size() == 0) {
		    return EMPTY_LIST;
		}
		return list.remove(randomIndex);
	    case 6:
		// set
		if (list.size() == 0) {
		    return EMPTY_LIST;
		}
		return list.set(randomIndex, value);
	    case 7:
		// test get
		if (list.size() == 0) {
		    return EMPTY_LIST;
		}
		return list.get(randomIndex);
	    default:
		return INVALID_OPERATION;
	}
    }

    /**
     * Tests retrieving an empty sublist
     * 
     * @throws Exception
     */
    @Test
    public void testGetEmptySublist() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals(0, list.subList(2, 2).size());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests retrieving an empty sublist
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratorWithArgument() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = makeList();
		ListIterator<String> iter = null;

		try {
		    iter = list.listIterator(0);
		} catch (IndexOutOfBoundsException ioobe) {
		    fail("Not expecting an IndexOutOfBoundsException");
		}

		try {
		    iter = list.listIterator(list.size() - 3);
		} catch (IndexOutOfBoundsException ioobe) {
		    fail("Not expecting an IndexOutOfBoundsException");
		}

		try {
		    iter = list.listIterator(list.size());
		} catch (IndexOutOfBoundsException ioobe) {
		    fail("Not expecting an IndexOutOfBoundsException");
		}

		try {
		    iter = list.listIterator(-1);
		    fail("Expecting an IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}

		try {
		    iter = list.listIterator(list.size() + 1);
		    fail("Expecting an IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /*
     * //////////////////////////////////////////////////////////////
     * EXCEPTIONAL USE CASES (THESE SHOULD THROW EXCEPTIONS)
     * //////////////////////////////////////////////////////////////
     */

    /**
     * Tests the lists ability to detect when an element is trying to be added
     * to an invalid index.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingToInvalidIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		try {
		    list.add(5, "Z");
		    fail("Expected an IndexOutOfBoundsException when adding to "
			    + "an invalid index");
		} catch (IndexOutOfBoundsException e) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the lists ability to detect when an element is trying to be added
     * to an invalid index.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingAllToInvalidIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		Collection<String> c = new ArrayList<String>();
		c.add("C");
		c.add("D");
		c.add("E");
		c.add("F");

		try {
		    list.addAll(3, c);
		    fail("Expected an IndexOutOfBoundsException when adding to "
			    + "an invalid index");
		} catch (IndexOutOfBoundsException e) {
		}

		try {
		    list.addAll(-1, c);
		    fail("Expected an IndexOutOfBoundsException when adding to "
			    + "an invalid index");
		} catch (IndexOutOfBoundsException e) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to throw an exception when trying to retrieve
     * a value from an invalid index.
     * 
     * @throws Exception
     */
    @Test
    public void testGettingElementFromInvalidUpperIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		try {
		    list.get(2);
		    fail("Expecting IndexOutOfBoundsException for accessing "
			    + "element outside of range.");
		} catch (IndexOutOfBoundsException e) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to throw an exception when trying to retrieve
     * an element using a negative index.
     * 
     * @throws Exception
     */
    @Test
    public void testGettingElementFromInvalidLowerIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		try {
		    list.get(-1);
		    fail("Expecting IndexOutOfBoundsException for accessing "
			    + "element outside of range");
		} catch (IndexOutOfBoundsException e) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to prevent a null element from being added.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingNullElement() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		try {
		    list.add(null);
		    fail("Expecting NullPointerException");
		} catch (NullPointerException npe) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to detect a null element inside a collection
     * to be added.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingAllUsingCollectionContainingNull() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add(null);

		try {
		    list.addAll(c);
		    fail("Expecting NullPointerException");
		} catch (NullPointerException npe) {
		}

		AppContext.getDataManager().removeObject(list);

	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to detect a null element in a collection when
     * inserting the collection in the middle of the list.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingAllInMiddleUsingCollectionContainingNull()
	    throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("C");
		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add(null);

		try {
		    list.addAll(1, c);
		    fail("Expecting NullPointerException");
		} catch (NullPointerException npe) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to detect that a list is not empty.
     * 
     * @throws Exception
     */
    @Test
    public void testIsEmptyShouldReturnFalse() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		assertFalse(list.isEmpty());

		list.add("B");
		list.remove(0);
		assertFalse(list.isEmpty());
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to not find a particular entry using
     * {@code indexOf} after it has been removed.
     * 
     * @throws Exception
     */
    @Test
    public void testIndexOfNonExistentUsingRemovals() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);

		assertEquals(-1, list.indexOf("C"));

		list.add("C");
		list.remove("C");
		assertEquals(-1, list.indexOf("C"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to not find a duplicated object after it has
     * been removed, using the lastIndexOf() method.
     * 
     * @throws Exception
     */
    @Test
    public void testLastIndexOfNonExistentUsingRemovals() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);
		assertEquals(-1, list.lastIndexOf("C"));

		list.add("C");
		list.remove(2);
		assertEquals(-1, list.lastIndexOf("C"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to not find a duplicated object after it has
     * been removed, using the lastIndexOf() method.
     * 
     * @throws Exception
     */
    @Test
    public void testRemoveWithInvalidIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.add("E");

		try {
		    list.remove(-1);
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}

		try {
		    list.remove(5);
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests expected exceptions from the {@code subList()} operation
     * 
     * @throws Exception
     */
    @Test
    public void testSubListExceptions() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("B");
		list.add("C");

		try {
		    list.subList(1, 4);
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException e) {
		}

		try {
		    list.subList(-1, 1);
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException e) {
		}

		try {
		    list.subList(-1, 3);
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException e) {
		}

		try {
		    list.subList(2, 1);
		    fail("Expecting IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests the list's ability to not find a duplicated object after it has
     * been removed, using the lastIndexOf() method.
     * 
     * @throws Exception
     */
    @Test
    public void testSetWithInvalidIndex() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 3);
		list.add("A");
		list.add("B");
		list.add("C");
		list.add("D");
		list.add("E");

		try {
		    list.set(-1, "Z");
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}

		try {
		    list.set(5, "Z");
		    fail("Expecting IndexOutOfBoundsException");
		} catch (IndexOutOfBoundsException ioobe) {
		}
		assertFalse(list.contains("Z"));
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /*
     * //////////////////////////////////////////////////// TEST USING DATA
     * SERVICE ////////////////////////////////////////////////////
     */

    /**
     * Tests retrieving the list and making modifications
     * 
     * @throws Exception
     */
    @Test
    public void testRetrievingAndModifyingListFromDataService()
	    throws Exception {
	final String name = "testScalableList";

	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> d = new ScalableList<String>(3, 3);
		for (int i = 0; i < 10; ++i)
		    d.add(Integer.toString(i));
		AppContext.getDataManager().setBinding(name, d);
	    }
	}, taskOwner);

	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> d =
			uncheckedCast((AppContext.getDataManager()
				.getBinding(name)));
		assertEquals(10, d.size());

		// compare elements
		for (int i = 0; i < d.size(); i++) {
		    assertEquals(Integer.toString(i), d.get(i));
		}

		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(d);
	    }
	}, taskOwner);
    }

    @Test
    public void testSeriazableWithRemovals() throws Exception {
	final String name = "testScalableList";
	final int total = 5;

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>();
		for (int i = 0; i < 10; i++)
		    list.add(Integer.toString(i));
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// remove some elements
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		for (int i = 0; i < 5; i++) {
		    list.remove(0);
		}
	    }
	}, taskOwner);

	// check that the changes were made
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		Iterator<String> iter = list.iterator();
		int count = 0;

		while (iter.hasNext()) {
		    iter.next();
		    count++;
		}

		assertEquals(total, count);
		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    @Test
    public void testSeriazableWithAdditions() throws Exception {
	final String name = "testScalableList";
	final int total = 15;

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>();
		for (int i = 0; i < 10; i++)
		    list.add(Integer.toString(i));
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// remove some elements
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		for (int i = 0; i < 5; i++) {
		    list.add(Integer.toString(10 + i));
		}
	    }
	}, taskOwner);

	// check that the changes were made
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		Iterator<String> iter = list.iterator();
		int count = 0;

		while (iter.hasNext()) {
		    iter.next();
		    count++;
		}

		assertEquals(total, count);
		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the iterator throws a
     * {@code ConcurrentModificationException} if the {@code ListNode} is
     * modified while the iterator is serialized.
     * 
     * @throws Exception
     */
    @Test
    public void testConcurrentModificationException() throws Exception {
	final String name = "testScalableList2";
	final String iterName = "testIterator";

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>(3, 3);
		for (int i = 0; i < 10; i++) {
		    list.add(Integer.toString(i));
		}
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// iterate to a location and serialize
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		ListIterator<String> iter = list.listIterator();
		int count = 0;
		while (count++ < 3) {
		    iter.next();
		}

		ManagedSerializable<ListIterator<String>> mgdIterator =
			new ManagedSerializable<ListIterator<String>>(iter);
		AppContext.getDataManager().setBinding(iterName, mgdIterator);
	    }
	}, taskOwner);

	// see what the iterator does
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));

		list.remove(2);

		ManagedSerializable<ListIterator<String>> mgdIterator =
			uncheckedCast(AppContext.getDataManager().getBinding(
				iterName));
		ListIterator<String> iter = mgdIterator.get();

		try {
		    iter.hasNext();
		    fail("Expecting ConcurrentModificationException");
		} catch (ConcurrentModificationException cme) {
		    // Expected
		} catch (Exception e) {
		    fail("Expecting ConcurrentModificationException: " +
			    e.getLocalizedMessage());
		}

		try {
		    iter.next();
		    fail("Expecting ConcurrentModificationException");
		} catch (ConcurrentModificationException cme) {
		    // Expected
		} catch (Exception e) {
		    fail("Expecting ConcurrentModificationException" +
			    e.getLocalizedMessage());
		}

		try {
		    iter.previous();
		    fail("Expecting ConcurrentModificationException");
		} catch (ConcurrentModificationException cme) {
		    // Expected
		} catch (Exception e) {
		    fail("Expecting ConcurrentModificationException");
		}

		try {
		    iter.hasPrevious();
		    fail("Expecting ConcurrentModificationException");
		} catch (ConcurrentModificationException cme) {
		    // Expected
		} catch (Exception e) {
		    fail("Expecting ConcurrentModificationException");
		}

		AppContext.getDataManager().removeBinding(iterName);
		AppContext.getDataManager().removeObject(mgdIterator);
		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether the iterator throws a
     * {@code ConcurrentModificationException} if the {@code ListNode} is
     * modified while the iterator is serialized.
     * 
     * @throws Exception
     */
    @Test
    public void testNoConcurrentModificationException() throws Exception {
	final String name = "testScalableList3";
	final String iterName = "testIterator3";

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>(3, 1);
		for (int i = 0; i < 10; i++) {
		    list.add(Integer.toString(i));
		}
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// iterate to a location and serialize
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		ListIterator<String> iter = list.listIterator();
		int count = 0;
		while (count++ < 5) {
		    iter.next();
		}

		ManagedSerializable<ListIterator<String>> mgdIterator =
			new ManagedSerializable<ListIterator<String>>(iter);
		AppContext.getDataManager().setBinding(iterName, mgdIterator);
	    }
	}, taskOwner);

	// see what the iterator does
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));

		// remove elements which will not affect the
		// node the iterator is pointing at
		list.remove(6);
		list.remove(2);
		assertEquals(8, list.size());

		// fetch iterator and see what it does
		ManagedSerializable<ListIterator<String>> mgdIterator =
			uncheckedCast(AppContext.getDataManager().getBinding(
				iterName));
		ListIterator<String> iter = mgdIterator.get();

		try {
		    assertTrue(iter.hasNext());
		    assertTrue(iter.hasPrevious());
		} catch (Exception e) {
		    fail("Not expecting an exception: " +
			    e.getLocalizedMessage());
		}

		try {
		    iter.next();
		    iter.previous();
		} catch (Exception e) {
		    fail("Was not expecting an exception: " +
			    e.getLocalizedMessage());
		}

		AppContext.getDataManager().removeBinding(iterName);
		AppContext.getDataManager().removeObject(mgdIterator);
		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests whether an iterator only removes an element once, as defined by
     * the {@code ListIterator.remove()} method.
     * 
     * @throws Exception
     */
    @Test
    public void testEnsureIteratorRemovesOnce() throws Exception {
	final String name = "testScalableList4";

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>(3, 1);
		for (int i = 0; i < 10; i++) {
		    list.add(Integer.toString(i));
		}
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// iterate to a location and remove
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		ListIterator<String> iter = list.listIterator();
		int count = 0;
		while (count++ < 5) {
		    iter.next();
		}
		iter.remove();
		assertEquals(9, list.size());
		try {
		    iter.remove();
		} catch (IllegalStateException ise) {
		} catch (Exception e) {
		    fail("Not expecting the exception: " +
			    e.getLocalizedMessage());
		}
		// Removing a second time should not cause a change
		// unless next() or prev() was called
		assertEquals(9, list.size());

		// test next()
		if (iter.hasNext()) {
		    iter.next();
		    iter.remove();
		    assertEquals(8, list.size());
		}

		// test previous()
		if (iter.hasPrevious()) {
		    iter.previous();
		    iter.remove();
		    assertEquals(7, list.size());
		}

		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);

    }

    /**
     * Tests whether an iterator is able to add to the list multiple times
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleIteratorAdds() throws Exception {
	final String name = "testScalableList5";

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>(3, 1);
		for (int i = 0; i < 10; i++) {
		    list.add(Integer.toString(i));
		}
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// iterate to a location and start adding
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		List<String> shadow = new ArrayList<String>();
		for (int i = 0; i < 10; i++) {
		    shadow.add(Integer.toString(i));
		}
		ListIterator<String> iter = list.listIterator();
		ListIterator<String> shadowIter = shadow.listIterator();
		int count = 0;
		while (count++ < 5) {
		    iter.next();
		    shadowIter.next();
		}
		iter.add("X");
		shadowIter.add("X");
		assertEquals(shadow.size(), list.size());
		iter.add("Y");
		shadowIter.add("Y");
		iter.add("Z");
		shadowIter.add("Z");
		assertEquals(shadow.size(), list.size());

		assertEquals(shadow.indexOf("X"), list.indexOf("X"));
		assertEquals(shadow.indexOf("Y"), list.indexOf("Y"));
		assertEquals(shadow.indexOf("Z"), list.indexOf("Z"));

		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);

    }

    /**
     * Tests whether multiple iterators can coexist on the same list.
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleIteratorOperations() throws Exception {
	final String name = "testScalableList5";
	final String iterName = "testIterator5";
	final String iterName2 = "testIterator6";

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ScalableList<String> list = new ScalableList<String>(3, 1);
		for (int i = 0; i < 10; i++) {
		    list.add(Integer.toString(i));
		}
		AppContext.getDataManager().setBinding(name, list);
	    }
	}, taskOwner);

	// create iterators, iterate to a certain location
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		ListIterator<String> iter = list.listIterator();
		ListIterator<String> iter2 = list.listIterator();

		ManagedSerializable<ListIterator<String>> mgdIterator =
			new ManagedSerializable<ListIterator<String>>(iter);
		AppContext.getDataManager().setBinding(iterName, mgdIterator);
		ManagedSerializable<ListIterator<String>> mgdIterator2 =
			new ManagedSerializable<ListIterator<String>>(iter2);
		AppContext.getDataManager().setBinding(iterName2,
			mgdIterator2);
	    }
	}, taskOwner);

	// try adding/removing
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		Random random = new Random(randomSeed);
		ScalableList<String> list =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		ManagedSerializable<ListIterator<String>> mgdIterator =
			uncheckedCast(AppContext.getDataManager().getBinding(
				iterName));
		ListIterator<String> iter = mgdIterator.get();

		ManagedSerializable<ListIterator<String>> mgdIterator2 =
			uncheckedCast(AppContext.getDataManager().getBinding(
				iterName2));
		ListIterator<String> iter2 = mgdIterator2.get();

		int count = 0;
		int value = random.nextInt(list.size() - 1);
		while (count++ <= value) {
		    iter.next();
		    iter2.next();
		}

		assertEquals(value, iter.nextIndex() - 1);
		assertEquals(value, iter2.nextIndex() - 1);

		// node was removed
		iter.remove();
		try {
		    iter2.next();
		    fail("Expecting a ConcurrentModificationException");
		} catch (ConcurrentModificationException cme) {
		} catch (Exception e) {
		    fail("(seed = " + randomSeed + ") Not expecting exception: " +
			    e.getLocalizedMessage());
		}

		count = 0;
		iter = list.listIterator();
		iter2 = list.listIterator();
		value = random.nextInt(list.size() - 1);
		while (count++ <= value) {
		    iter.next();
		    iter2.next();
		}

		// node was added
		iter.next();
		iter.add("Z");
		try {
		    iter2.hasNext();
		} catch (ConcurrentModificationException cme) {
		    fail("Not expecting a ConcurrentModificationException");
		}

		// test set; it shouldn't throw an exception because the
		// node will remain the same, with the exception of the
		// one element that was changed.
		count = 0;
		iter = list.listIterator();
		iter2 = list.listIterator();
		value = random.nextInt(list.size() - 1);
		while (count++ <= value) {
		    iter.next();
		    iter2.next();
		}

		iter.set("Z");
		try {
		    iter2.hasNext();
		} catch (ConcurrentModificationException cme) {
		    fail("Not expecting a ConcurrentModificationException");
		}

		AppContext.getDataManager().removeBinding(iterName);
		AppContext.getDataManager().removeObject(mgdIterator);
		AppContext.getDataManager().removeBinding(iterName2);
		AppContext.getDataManager().removeObject(mgdIterator2);
		AppContext.getDataManager().removeBinding(name);
		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);
    }

    /**
     * Tests random operations
     * 
     * @throws Exception
     */
    @Test
    public void testListIteratorOperationsAgainstShadow() throws Exception {
	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(4, 9);

		assertEquals(shadow.size(), list.size());
		// Populate the lists
		for (int i = 0; i < 20; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		    assertEquals(Integer.toString(i) + ":", shadow.size(),
			    list.size());
		}
		assertEquals(shadow.size(), list.size());

		// place iterators in the middle
		ListIterator<String> shadowIter = shadow.listIterator();
		ListIterator<String> listIter = list.listIterator();
		for (int i = 0; i < 10; i++) {
		    shadowIter.next();
		    listIter.next();
		}

		assertEquals(shadow.size(), list.size());

		// perform some operations
		listIter.add("A");
		shadowIter.add("A");
		assertEquals(shadow.size(), list.size());
		listIter.add("A");
		shadowIter.add("A");
		assertEquals(shadow.size(), list.size());
		listIter.add("A");
		shadowIter.add("A");
		assertEquals(shadow.size(), list.size());
		listIter.add("A");
		shadowIter.add("A");
		assertEquals(shadow.size(), list.size());
		listIter.add("A");
		shadowIter.add("A");
		assertEquals(shadow.size(), list.size());

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);

    }

    /**
     * Tests random operations
     * 
     * @throws Exception
     */
    @Test
    public void testRandomListIteratorOperations() throws Exception {
	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(3, 1);

		// Populate the lists
		for (int i = 0; i < 20; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		}

		Random random = new Random(randomSeed);
		int startingPoint = random.nextInt(shadow.size() - 1);
                System.err.println("START: " + startingPoint);

		// place iterators in the middle
		ListIterator<String> shadowIter =
			shadow.listIterator(startingPoint);
		ListIterator<String> listIter =
			list.listIterator(startingPoint);
		String opList = "";

		// perform random operations
		for (int i = 0; i < 20; i++) {
		    int operation = random.nextInt(11);
		    opList += Integer.toString(operation) + ",";

		    String value =
			    Integer.toString(random.nextInt() / 100000);
		    Object listResult;
		    Object shadowResult;
		    ReturnObject retObj;

		    // try the ScalableList
		    try {
			retObj = performOperation(listIter, operation, value);
			listIter = retObj.iter;
			listResult = retObj.returnValue;
		    } catch (Exception e) {
			listResult = e.getCause();
		    }

		    // try the shadow list
		    try {
			retObj =
				performOperation(shadowIter, operation, value);
			shadowIter = retObj.iter;
			shadowResult = retObj.returnValue;
		    } catch (Exception e) {
			shadowResult = e.getCause();
		    }
		    assertEquals("(test.seed=" + randomSeed + ") Operations were: " + opList,
                    shadowResult, listResult);
		}
		assertEquals(shadow.size(), list.size());

		// System.err.println(">> Operations: " + opList);
		// ensure that all the entries are the same
		for (int i = 0; i < shadow.size(); i++) {
		    assertEquals("iteration: " + i, shadow.get(i), list
			    .get(i));
		}

		AppContext.getDataManager().removeObject(list);
	    }
	}, taskOwner);

    }
    
    /**
     * Test removing an object with an iterator that is at the end of
     * the list.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testRemoveFromEndOfList() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(3, 1);
                
                // Populate the lists
		for (int i = 0; i < 5; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		}
                
                // place iterators at the end
		ListIterator<String> shadowIter =
			shadow.listIterator(5);
		ListIterator<String> listIter =
			list.listIterator(5);
                
                //get previous item for each list
                String shadowPrev = shadowIter.previous();
                String listPrev = listIter.previous();
                Assert.assertEquals(shadowPrev, listPrev);
                
                //remove item from each iterator
                shadowIter.remove();
                listIter.remove();
                
                //get previous index from each iterator
                int shadowIndex = shadowIter.previousIndex();
                int listIndex = listIter.previousIndex();
                Assert.assertEquals(shadowIndex, listIndex);
            }
        }, taskOwner);
    }
    
    /**
     * Test removing an object with an iterator that is at the beginning of
     * the list.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testRemoveFromBeginningOfList() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(3, 1);
                
                // Populate the lists
		for (int i = 0; i < 5; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		}
                
                // place iterators at the beginning
		ListIterator<String> shadowIter =
			shadow.listIterator();
		ListIterator<String> listIter =
			list.listIterator();
                
                //get next item for each list
                String shadowNext = shadowIter.next();
                String listNext = listIter.next();
                Assert.assertEquals(shadowNext, listNext);
                
                //remove item from each iterator
                shadowIter.remove();
                listIter.remove();
                
                //get next index from each iterator
                int shadowIndex = shadowIter.nextIndex();
                int listIndex = listIter.nextIndex();
                Assert.assertEquals(shadowIndex, listIndex);
            }
        }, taskOwner);
    }
    
    /**
     * Test removing an object with an iterator that is at the end of
     * the list.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testRemoveFromEndOfListWithIterator() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(3, 1);
                
                // Populate the lists
		for (int i = 0; i < 5; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		}
                
                // place iterators at the end
		Iterator<String> shadowIter =
			shadow.iterator();
		Iterator<String> listIter =
			list.iterator();
                Object shadowNext = null;
                Object listNext = null;
                for (int i = 0; i < 5; i++) {
                    shadowNext = shadowIter.next();
                    listNext = listIter.next();
                }
                Assert.assertEquals(shadowNext, listNext);
                
                //remove item from each iterator
                shadowIter.remove();
                listIter.remove();
                
                //iterators should be at end of list
                boolean shadowEnd = shadowIter.hasNext();
                boolean listEnd = listIter.hasNext();
                Assert.assertEquals(shadowEnd, listEnd);

            }
        }, taskOwner);
    }
    
    /**
     * Test removing an object with an iterator that is at the beginning of
     * the list.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testRemoveFromBeginningOfListWithIterator() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		List<String> shadow = new ArrayList<String>();
		ScalableList<String> list = new ScalableList<String>(3, 1);
                
                // Populate the lists
		for (int i = 0; i < 5; i++) {
		    shadow.add(Integer.toString(i));
		    list.add(Integer.toString(i));
		}
                
                // place iterators at the beginning
		Iterator<String> shadowIter =
			shadow.iterator();
		Iterator<String> listIter =
			list.iterator();
                
                //get next item for each list
                String shadowNext = shadowIter.next();
                String listNext = listIter.next();
                Assert.assertEquals(shadowNext, listNext);
                
                //remove item from each iterator
                shadowIter.remove();
                listIter.remove();
                
                //verify next item is the same
                shadowNext = shadowIter.next();
                listNext = listIter.next();
                Assert.assertEquals(shadowNext, listNext);
            }
        }, taskOwner);
    }

    /**
     * Test setting an object with an iterator after a call to
     * previous returns NoSuchElementException.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testSetAfterInvalidPrevious() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 1);

                // Populate the list
		for (int i = 0; i < 5; i++) {
		    list.add(Integer.toString(i));
		}

                // place iterator at the beginning
		ListIterator<String> listIter =
			list.listIterator();

                //attempt to get previous item for the list
                try {
                    listIter.previous();
                    fail("Expected NoSuchElementException");
                } catch (NoSuchElementException e) {

                }

                //attempt to set an item with the iterator
                try {
                    listIter.set("Test");
                    fail("Expected IllegalStateException");
                } catch(IllegalStateException e) {

                }

            }
        }, taskOwner);
    }

    /**
     * Test setting an object with an iterator after a call to
     * next returns NoSuchElementException.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testSetAfterInvalidNext() throws Exception {
        // create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(3, 1);

                // Populate the list
		for (int i = 0; i < 5; i++) {
		    list.add(Integer.toString(i));
		}

                // place iterator at the end
		ListIterator<String> listIter =
			list.listIterator(list.size());

                //attempt to get next item for the list
                try {
                    listIter.next();
                    fail("Expected NoSuchElementException");
                } catch (NoSuchElementException e) {

                }

                //attempt to set an item with the iterator
                try {
                    listIter.set("Test");
                    fail("Expected IllegalStateException");
                } catch(IllegalStateException e) {

                }

            }
        }, taskOwner);
    }

    /**
     * An object to return both the iterator and the result from the
     * {@code performOperation()} method.
     * 
     * @param <E> the type of the objects being iterated over
     */
    static class ReturnObject {
	public final ListIterator<String> iter;
	public final Object returnValue;

	ReturnObject(ListIterator<String> iter, Object returnValue) {
	    this.iter = iter;
	    this.returnValue = returnValue;
	}
    }

    /**
     * Performs the supplied operation
     * 
     * @param list the list to modify
     * @param operation the operation to perform
     * @param value the value to add, if applicable
     * @return the modified list
     */
    private ReturnObject performOperation(ListIterator<String> iter,
	    int operation, String value) throws NoSuchElementException {
	Object returnValue = null;

	switch (operation) {
	    case 0:
	    case 1:
		returnValue = iter.next();
		break;
	    case 2:
	    case 3:
		returnValue = iter.previous();
		break;
	    case 4:
		iter.add(value);
		returnValue = "added";
		break;
	    case 5:
		iter.remove();
		returnValue = "removed";
		break;
	    case 6:
		iter.set(value);
		returnValue = "set";
		break;
	    case 7:
		returnValue = iter.hasNext();
		break;
	    case 8:
		returnValue = iter.hasPrevious();
		break;
	    case 9:
		returnValue = iter.nextIndex();
		break;
	    case 10:
		returnValue = iter.previousIndex();
	    default:
		break;
	}
	return new ReturnObject(iter, returnValue);
    }

    /**
     * Casts the object to the desired type in order to avoid unchecked cast
     * warnings
     * 
     * @param <T> the type to cast to
     * @param object the object to cast
     * @return the casted version of the object
     */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
	return (T) object;
    }

    private int getObjectCount() throws Exception {
	GetObjectCountTask task = new GetObjectCountTask();
	txnScheduler.runTask(task, taskOwner);
	return task.count;
    }

    private class GetObjectCountTask extends TestAbstractKernelRunnable {

	volatile int count = 0;

	GetObjectCountTask() {
	}

	public void run() {
	    count = 0;
	    BigInteger last = null;
	    while (true) {
		BigInteger next = dataService.nextObjectId(last);
		if (next == null) {
		    break;
		}
		// NOTE: this count is used at the end of the test to make
		// sure
		// that no objects were leaked in stressing the structure but
		// any given service (e.g., the task service) may accumulate
		// managed objects, so a more general way to exclude these
		// from
		// the count would be nice but for now the specific types that
		// are accumulated get excluded from the count
		ManagedReference<?> ref =
			dataService.createReferenceForId(next);
		String name = ref.get().getClass().getName();
		if (!name.equals("com.sun.sgs.impl.service.task.PendingTask")) {
		    // System.err.println(count + ": " + name);
		    count++;
		}
		last = next;
	    }
	}
    }

    /**
     * Test clearing and removal {@code ScalableList}
     */
    @Test
    public void testClearLeavesNoArtifacts() throws Exception {
	coreClearTest(10);
    }

    /**
     * Test removal of an empty list
     */
    @Test
    public void testRemovingObjectOnEmptyList() throws Exception {
	coreRemovingObjectTest(0);
    }

    /**
     * Test removal of a partially filled list
     */
    @Test
    public void testRemovingObject() throws Exception {
	coreRemovingObjectTest(10);
    }

    
    /**
     * The core of the removingObjects tests
     * @param elementsToAdd number of elements to add
     * @throws Exception
     */
    private void coreRemovingObjectTest(final int elementsToAdd) throws Exception {
	int originalCount = getObjectCount();
	System.err.println("originalCount: " + originalCount);
	final String name = "list" + Long.toString(System.currentTimeMillis());

	// create the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ScalableList<Integer> d = new ScalableList<Integer>(3, 3);
		AppContext.getDataManager().setBinding(name, d);
	    }
	}, taskOwner);

	int countAfterCreate = getObjectCount();
	System.err.println("countAfterCreate: " + countAfterCreate);

	// add some contents
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ScalableList<Integer> d =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		for (int i = 0; i < elementsToAdd; i++) {
		    d.add(i);
		}
	    }
	}, taskOwner);

	int countAfterAdds = getObjectCount();
	System.err.println("countAfterAdds: " + countAfterAdds);

	// remove object
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		DataManager dm = AppContext.getDataManager();
		ScalableList<Integer> d = uncheckedCast(dm.getBinding(name));
		dm.removeObject(d);
		dm.removeBinding(name);
	    }
	}, taskOwner);

	Thread.sleep(50 * elementsToAdd);
	int countAfterDelete = getObjectCount();
	System.err.println("countAfterDelete: " + countAfterDelete);

	assertEquals(originalCount, countAfterDelete);
    }
    
    
    /**
     * Stress test {@code ScalableList}
     */
    @Test
    public void testScalableListStressTest() throws Exception {
	coreClearTest(100);
    }

    /**
     * Method which can be reused to test clearing of a given number of items
     * 
     * @param elementsToAdd the number of elements to add
     */
    private void coreClearTest(final int elementsToAdd)
	    throws Exception {
	final String name =
		"list" + Long.toString(System.currentTimeMillis());

	// create list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ScalableList<Integer> d = new ScalableList<Integer>();
		AppContext.getDataManager().setBinding(name, d);
	    }
	}, taskOwner);

	int countAfterCreate = getObjectCount();
	System.err.println("countAfterCreate: " + countAfterCreate);

	// add some objects
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		ScalableList<Integer> d =
			uncheckedCast(AppContext.getDataManager().getBinding(
				name));
		for (int i = 0; i < elementsToAdd; i++) {
		    d.add(i);
		}
	    }
	}, taskOwner);

	int countAfterAdds = getObjectCount();
	System.err.println("countAfterAdds: " + countAfterAdds);

	// clear the list
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		DataManager dm = AppContext.getDataManager();
		ScalableList<Integer> d = uncheckedCast(dm.getBinding(name));
		d.clear();
	    }
	}, taskOwner);

	// removal is asynchronous, so wait. When we compare, there should
	// be as many objects as there were immediately after the list
	// was created.
	Thread.sleep(50 * elementsToAdd);
	int countAfterClear = getObjectCount();
	System.err.println("countAfterClear: " + countAfterClear);
	assertEquals(countAfterCreate, countAfterClear);

	// delete object
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		DataManager dm = AppContext.getDataManager();
		ScalableList<Integer> d = uncheckedCast(dm.getBinding(name));
		dm.removeObject(d);
		dm.removeBinding(name);
	    }
	}, taskOwner);

	Thread.sleep(1000);
    }
}
