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

package com.sun.sgs.test.app.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ScalableList;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;

/**
 * Test the {@link ScalableList} class.
 */
@RunWith(NameRunner.class)
public class TestScalableList extends Assert {

	private static SgsTestNode serverNode;
	private static TransactionScheduler txnScheduler;
	private static Identity taskOwner;
	private static DataService dataService;

	/**
	 * Test management.
	 */

	@BeforeClass
	public static void setUpClass() throws Exception {
	serverNode = new SgsTestNode("TestScalableList", null,
			createProps("TestScalableList"));
	txnScheduler = serverNode.getSystemRegistry().getComponent(
			TransactionScheduler.class);
	taskOwner = serverNode.getProxy().getCurrentOwner();
	dataService = serverNode.getDataService();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	serverNode.shutdown(true);
	}

	private static Properties createProps(String appName) throws Exception {
	Properties props = SgsTestNode.getDefaultProperties(appName, null,
			SgsTestNode.DummyAppListener.class);
	props.setProperty("com.sun.sgs.txn.timeout", "10000000");
	return props;
	}

	/**
	 * Tests instantiating a ScalableList with illegal argument(s).
	 * 
	 * @throws Exception
	 */
	@Test
	public void testConstructorWithIllegalArgs() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		try {
		new ScalableList<String>(-1, -1);
		fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

		try {
		new ScalableList<String>(-1, 2);
		fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		new ScalableList<String>(2, -1);
		fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		try {
		new ScalableList<String>(1, 0);
		fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		}
	}, taskOwner);
	}

	/**
	 * Tests instantiating a ScalableList with illegal argument(s).
	 * 
	 * @throws Exception
	 */
	@Test
	public void testConstructorWithLegalArgs() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list;
		try {
		list = new ScalableList<String>(2, 1);
		} catch (Exception e) {
		fail("Did not expect exception: " + e.getLocalizedMessage());
		}

		try {
		list = new ScalableList<String>(99, 99);
		} catch (Exception e) {
		fail("Did not expect exception: " + e.getLocalizedMessage());
		}

		try {
		list = new ScalableList<String>(2, 999);
		} catch (Exception e) {
		fail("Did not expect exception: " + e.getLocalizedMessage());
		}

		try {
		list = new ScalableList<String>(999, 2);
		} catch (Exception e) {
		fail("Did not expect exception: " + e.getLocalizedMessage());
		}

		try {
		Random random = new Random();
		int rand1 = random.nextInt(999) + 2;
		int rand2 = random.nextInt(999) + 1;
		list = new ScalableList<String>(rand1, rand2);
		} catch (Exception e) {
		fail("Did not expect exception: " + e.getLocalizedMessage());
		}
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));

		assertEquals(1, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		assertTrue(list.add("B"));

		assertEquals(2, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add(0, "B");

		assertEquals(2, list.size());
		assertEquals("B", list.get(0));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add(0, "A");

		assertEquals(1, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add(1, "Z");

		assertEquals(3, list.size());
		assertEquals("Z", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		Collection<String> c = new ArrayList<String>();
		c.add("A");
		c.add("B");
		c.add("C");

		assertTrue(list.addAll(c));
		assertEquals(3, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

	/**
	 * Tests whether two lists with identical elements are seen as being equal.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEquals() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(8, 8);
		ManagedReference<ScalableList<String>> list1Ref = dataService
				.createReference(list);
		ManagedReference<ScalableList<String>> list2Ref = dataService
				.createReference(list);

		assertTrue(list1Ref.get().equals(list2Ref.get()));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

	/**
	 * Tests whether the head element of the list can be retrieved when the list
	 * size is unity
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetHeadFromListOfSizeOne() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");

		assertEquals("A", list.get(0));
		}
	}, taskOwner);
	}

	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetFirstElement() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("A", list.get(0));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("C", list.get(2));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("B", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals("C", list.get(2));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(0);
		assertEquals(2, list.size());
		assertEquals("B", list.get(0));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.remove(1);

		assertEquals(2, list.size());
		assertEquals("C", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);

		assertEquals(2, list.size());
		assertEquals("B", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(1);

		assertEquals("B", obj.toString());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.remove(0);

		assertEquals(2, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(0);

		assertEquals("A", obj);
		}
	}, taskOwner);
	}

	/**
	 * Tests the removal of the tail (last) element using the designated method
	 * call.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRemoveTail() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		list.remove(2);

		assertEquals(2, list.size());
		}
	}, taskOwner);
	}

	/**
	 * Tests the removal of the tail using the designated API call and verifies
	 * that the value is the expected one.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRemoveTailAndVerifyResult() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");
		Object obj = list.remove(2);

		assertEquals("C", obj);
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		String obj = new String("abc");

		list.add("A");
		list.add("B");
		list.add(obj);

		list.remove(obj);

		assertEquals(2, list.size());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		String obj = new String("abc");

		list.add("A");
		list.add("B");
		list.add(obj);

		assertTrue(list.remove(obj));
		assertEquals(-1, list.indexOf(obj));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		assertEquals(1, list.indexOf("B"));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");
		list.add("C");

		list.set(1, "Z");

		assertEquals(3, list.size());
		assertEquals("Z", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));

		assertTrue(list.contains("A"));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));

		assertTrue(list.contains("A"));
		assertTrue(list.contains("B"));
		assertTrue(list.contains("C"));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		assertTrue(list.add("A"));
		assertTrue(list.add("B"));
		assertTrue(list.add("C"));
		assertTrue(list.add("D"));

		List<String> newList = list.subList(1, 2);

		assertEquals(2, newList.size());
		assertEquals("B", newList.get(0));
		assertEquals("C", newList.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

	/*
	 * ////////////////////////////////////////////////////////////// SCALABLE
	 * USE CASES (INVOLVING SPLITTING AND DELETING)
	 * //////////////////////////////////////////////////////////////
	 */

	/**
	 * Tests scalability by appending elements exceeding the cluster size
	 * 
	 * @throws Exception
	 */
	@Test
	public void testScalableAppendByVerifyingSize() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("A", list.get(0));
		assertEquals("D", list.get(3));
		assertEquals("G", list.get(6));
		assertEquals("J", list.get(9));
		assertEquals("L", list.get(11));
		}
	}, taskOwner);
	}

	/**
	 * Tests scalability by inserting values until the number of elements in the
	 * list exceeds the cluster size. The test also verifies that the values are
	 * consistent.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testScalableInsertByVerifyingValues() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

	/**
	 * Creates a list of size 12 by appending the values 0 through 11 in order.
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();
		list.remove(5);
		assertEquals("G", list.get(5));
		list.remove(9);
		assertEquals("L", list.get(9));
		list.remove(1);
		assertEquals("C", list.get(1));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {

		ScalableList<String> list = makeList();
		assertEquals(2, list.indexOf("C"));
		assertEquals(8, list.indexOf("I"));
		assertEquals(11, list.indexOf("L"));

		}
	}, taskOwner);
	}

	/**
	 * Tests scalability by assessing the last index of duplicated elements in a
	 * large list.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testScalableLastIndexOf() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {

		ScalableList<String> list = makeList();
		list.add("A");
		assertEquals(12, list.lastIndexOf("A"));

		list.add("A");
		list.add("A");
		assertEquals(14, list.lastIndexOf("A"));

		}
	}, taskOwner);
	}

	/**
	 * Tests scalability by assessing the accuracy of the retrieved element from
	 * a set operation, as well as its value after the operation takes place.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testScalableSet() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();
		assertEquals("C", list.set(2, "Z"));
		assertEquals("Z", list.get(2));
		assertEquals("I", list.set(8, "ZZ"));
		assertEquals("ZZ", list.get(8));
		assertEquals("L", list.set(11, "ZZZ"));
		assertEquals("ZZZ", list.get(11));
		}
	}, taskOwner);
	}

	/**
	 * Tests scalability by verifying an iterator's size and values for a large
	 * list.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testScalableIterator() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();
		Iterator<String> iter = list.iterator();

		assertEquals(true, iter.hasNext());

		// Go through all the elements
		int size = 0;
		String value = null;
		while (iter.hasNext()) {
		value = iter.next();
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("A", list.get(0));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		assertEquals("L", list.get(11));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add("D");
		c.add("I");

		assertTrue(list.containsAll(c));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		List<String> newList = list.subList(3, 7);

		assertEquals(5, newList.size());
		assertEquals("D", newList.get(0));
		assertEquals("H", newList.get(4));
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		Object[] array = list.toArray();

		assertEquals(12, list.size());
		assertEquals(12, array.length);
		assertEquals("A", array[0]);
		assertEquals("D", array[3]);
		assertEquals("G", array[6]);
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = makeList();

		Object[] container = new Object[77];
		Object[] array = list.toArray(container);

		assertEquals(12, list.size());
		assertEquals(77, array.length);
		assertEquals("A", array[0]);
		assertEquals("D", array[3]);
		assertEquals("G", array[6]);
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

	/**
	 * Tests the list's ability to throw an exception when trying to retrieve a
	 * value from an invalid index.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGettingElementFromInvalidUpperIndex() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
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

		}
	}, taskOwner);
	}

	/**
	 * Tests the list's ability to throw an exception when trying to retrieve an
	 * element using a negative index.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGettingElementFromInvalidLowerIndex() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("B");

		try {
		list.add(null);
		fail("Expecting IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}
		}
	}, taskOwner);
	}

	/**
	 * Tests the list's ability to detect a null element inside a collection to
	 * be added.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAddingAllUsingCollectionContainingNull() throws Exception {
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");

		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add(null);

		try {
		list.addAll(c);
		fail("Expecting IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		list.add("C");
		Collection<String> c = new ArrayList<String>();
		c.add("B");
		c.add(null);

		try {
		list.addAll(1, c);
		fail("Expecting IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
		}

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
	txnScheduler.runTask(new AbstractKernelRunnable() {
		public void run() throws Exception {
		ScalableList<String> list = new ScalableList<String>(6, 6);
		list.add("A");
		assertFalse(list.isEmpty());

		list.add("B");
		list.remove(0);
		assertFalse(list.isEmpty());
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
	txnScheduler.runTask(new AbstractKernelRunnable() {
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
		}
	}, taskOwner);
	}

}
