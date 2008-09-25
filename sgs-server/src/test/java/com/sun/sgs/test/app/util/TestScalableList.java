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

    @BeforeClass public static void setUpClass() throws Exception {
	serverNode = new SgsTestNode("TestScalableList", null,
				     createProps("TestScalableList"));
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    @AfterClass public static void tearDownClass() throws Exception {
	serverNode.shutdown(true);
    }
    
    private static Properties createProps(String appName) throws Exception {
        Properties props = SgsTestNode.getDefaultProperties(appName, null, 
                                           SgsTestNode.DummyAppListener.class);
        props.setProperty("com.sun.sgs.txn.timeout", "1000000");
        return props;
    }

    /**
     * Tests instantiating a ScalableList with illegal
     * argument(s).
     * @throws Exception
     */
    @Test public void testConstructorWithIllegalArgs()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    try {
		    	new ScalableList<Integer>(-1, -1);
		    	fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException iae) {
		    }
		    
		    try {
		    	new ScalableList<Integer>(-1, 2);
		    	fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException iae) {
		    }
		    try {
		    	new ScalableList<Integer>(2, -1);
		    	fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException iae) {
		    }
		    try {
		    	new ScalableList<Integer>(1, 0);
		    	fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException iae) {
		    }
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests instantiating a ScalableList with illegal
     * argument(s).
     * @throws Exception
     */
    @Test public void testConstructorWithLegalArgs()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list;
		    try {
		    	list = new ScalableList<Integer>(2, 1);
		    } catch (Exception e) {
		    	fail("Did not expect exception: " + e.getLocalizedMessage());
		    }
		    
		    try {
		    	list = new ScalableList<Integer>(99, 99);
		    } catch (Exception e) {
		    	fail("Did not expect exception: " + e.getLocalizedMessage());
		    }
		    
		    try {
		    	list = new ScalableList<Integer>(2, 999);
		    } catch (Exception e) {
		    	fail("Did not expect exception: " + e.getLocalizedMessage());
		    }
		    
		    try {
		    	list = new ScalableList<Integer>(999, 2);
		    } catch (Exception e) {
		    	fail("Did not expect exception: " + e.getLocalizedMessage());
		    }
		    
		    try {
		    	Random random = new Random();
		    	int rand1 = random.nextInt(999) + 2;
		    	int rand2 = random.nextInt(999) + 1;
		    	list = new ScalableList<Integer>(rand1, rand2);
		    } catch (Exception e) {
		    	fail("Did not expect exception: " + e.getLocalizedMessage());
		    }
		}
	    }, taskOwner);
    }
    
    /*
     *  ///////////////////////////////////////////////////
     *  	NON-EXCEPTIONAL (NORMAL) USE CASES
     *  ///////////////////////////////////////////////////
     */

    /**
     * Tests adding an item to an empty list.
     * @throws Exception
     */
    @Test public void testAddingToEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    
		    assertEquals(1, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests appending a value to the list
     * @throws Exception
     */
    @Test public void testAppendingToList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }

    /**
     * Tests appending a value to a non-empty list
     * @throws Exception
     */
    @Test public void testPrependToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.prepend(2);
		    
		    assertEquals(2, list.size());
		    assertEquals("2", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests prepending a value into an empty list
     * @throws Exception
     */
    @Test public void testPrependIntoEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.prepend(1);
		    
		    assertEquals(1, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests inserting a value into a populated list
     * @throws Exception
     */
    @Test public void testInsertIntoList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(1, 999);
		    
		    assertEquals(3, list.size());
		    assertEquals("999", list.get(1));
		}
	    }, taskOwner);
    }
    
   
    /**
     * Tests adding all the elements in a collection to an 
     * empty list.
     * @throws Exception
     */
    @Test public void testAddingAllToEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
			Collection<Integer> c = new ArrayList<Integer>();
			c.add(1);
			c.add(2);
			c.add(3);
			
			assertTrue(list.addAll(c));
		    assertEquals(3, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests appending all elements in a collection to a
     * non-empty list.
     * @throws Exception
     */
    @Test public void testAppendingAllToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(8, 8);
			list.add(1);
			list.add(2);
			
			Collection<Integer> c = new ArrayList<Integer>();
			c.add(3);
			c.add(4);
			c.add(5);
			
			assertTrue(list.addAll(c));
		    assertEquals(5, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests whether two lists with identical elements
     * are seen as being equal.
     * @throws Exception
     */
    @Test public void testEquals()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(8, 8);
			ManagedReference<ScalableList<String>> list1Ref = 
				dataService.createReference(list);
			ManagedReference<ScalableList<String>> list2Ref = 
				dataService.createReference(list);
			
			assertTrue(list1Ref.get().equals(list2Ref.get()));
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether a collection of elements can be added
     * successfully to the middle of a populated list
     * @throws Exception
     */
    @Test public void testAddingAllMiddleToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(8, 8);
			list.add(1);
			list.add(5);
			
			Collection<Integer> c = new ArrayList<Integer>();
			c.add(2);
			c.add(3);
			c.add(4);
			
			assertTrue(list.addAll(1, c));
		    assertEquals(5, list.size());
		    // get the middle element
		    assertEquals("3", list.get(2).toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether the head element of the list
     * can be retrieved when the list size is unity
     * @throws Exception
     */
    @Test public void testGetHeadFromListOfSizeOne()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    
		    assertEquals("1", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testGetFirstElement()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals("1", list.getFirst().toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether the last element of the list can be retrieved
     * @throws Exception
     */
    @Test public void testGetLastElement()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals("3", list.getLast().toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether the first and last element in the
     * list are the same when the list size is unity.
     * @throws Exception
     */
    @Test public void testGetFirstAndLastElementWithOneEntry()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(6, 6);
		    list.add("only child");
		    
		    assertEquals(list.getFirst(), list.getLast());
		}
	    }, taskOwner);
    }
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testGetHeadFromListOfArbitrarySize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals("1", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the accuracy of retrieving a value from the middle
     * of the list.
     * @throws Exception
     */
    @Test public void testGetMiddleFromListOfArbitrarySize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals("2", list.get(1).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the list's ability to retrieve the last element
     * using the get method.
     * @throws Exception
     */
    @Test public void testGetEndFromListOfArbitrarySize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals("3", list.get(2).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests if the head can be successfully removed from the list
     * @throws Exception
     */
    @Test public void testRemoveHeadFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    list.remove(0);
		    assertEquals(2, list.size());
		    assertEquals("2", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests if a middle element can be removed successfully from
     * the list
     * @throws Exception
     */
    @Test public void testRemoveMiddleFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.remove(1);
		    
		    assertEquals(2, list.size());
		    assertEquals("3", list.get(1).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests whether the tail (last) element can be removed 
     * from the list
     * @throws Exception
     */
    @Test public void testRemoveEndFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.remove(2);
		    
		    assertEquals(2, list.size());
		    assertEquals("2", list.get(1).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests whether the removed item contains the expected value.
     * @throws Exception
     */
    @Test public void testRemoveAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.remove(1);
		    
		    assertEquals("2", obj.toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the removal of the head using the designated
     * method call.
     * @throws Exception
     */
    @Test public void testRemoveHead()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.removeFirst();
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests that the removed value has an expected value
     * @throws Exception
     */
    @Test public void testRemoveHeadAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.removeFirst();
		    
		    assertEquals("1", obj.toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the removal of the tail (last) element using
     * the designated method call.
     * @throws Exception
     */
    @Test public void testRemoveTail()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.removeLast();
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the removal of the tail using the designated
     * API call and verifies that the value is the expected
     * one.
     * @throws Exception
     */
    @Test public void testRemoveTailAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.removeLast();
		    
		    assertEquals("3", obj.toString());
		}
	    }, taskOwner);
    }
    
    
    
    /**
     * Tests the removal of elements using their reference
     * @throws Exception
     */
    @Test public void testRemoveUsingObjectReference()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Object> list = new ScalableList<Object>(6, 6);
			Object obj = new Object();
			
		    list.add("A");
		    list.add("B");
		    list.add(obj);
		    
		    list.remove(obj);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests the removal of an object by using its reference
     * and testing that the value is equal to the reference
     * @throws Exception
     */
    @Test public void testRemoveUsingObjectReferenceAndVerify()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Object> list = new ScalableList<Object>(6, 6);
			Object obj = new Object();
			
		    list.add("A");
		    list.add("B");
		    list.add(obj);
		    
		    Object o = list.remove(obj);
		   
		    assertTrue(obj.equals(o));
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether the iterator identifies the proper quantity
     * and value for the list's elements.
     * @throws Exception
     */
    @Test public void testIterator()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(0);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Iterator<Integer> iter = list.iterator();
		    assertTrue(iter.hasNext());
		    
		    // Start iterations
		    int size = 0;
		    int value = 0;
		    while (iter.hasNext()){
		    	value = iter.next();
		    	size++;
		    	
		    	// Check a random iteration
		    	if (size == 2){
		    		assertEquals(size, value);
		    	}
		    }
		    // Iteration amount should equal list size
		    assertEquals(size, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests whether the list can determine if it is empty or not.
     * @throws Exception
     */
    @Test public void testIsEmpty()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    
			assertTrue(list.isEmpty());
			
			list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.removeFirst();
		    list.removeFirst();
		    list.removeFirst();
		    
		    assertTrue(list.isEmpty());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests whether a valid index of an object in the list 
     * can be properly retrieved
     * @throws Exception
     */
    @Test public void testIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals(1, list.indexOf(2));
		}
	    }, taskOwner);
    }
    
    /**
     * Tests whether the last index of a valid element can be
     * properly found.
     * @throws Exception
     */
    @Test public void testLastIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
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
     * @throws Exception
     */
    @Test public void testSet()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.set(1, 999);
		    
		    assertEquals(3, list.size());
		    assertEquals("999", list.get(1).toString());
		}
	    }, taskOwner);
    }
    
    
    /*
     *  //////////////////////////////////////////////////////////////
     *  	SCALABLE USE CASES (INVOLVING SPLITTING AND DELETING)
     *  //////////////////////////////////////////////////////////////
     */
    
    
    
    /**
     * Tests scalability by appending elements exceeding the
     * cluster size
     * @throws Exception
     */
    @Test public void testScalableAppendByVerifyingSize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    list.add(4);
		    list.add(5);
		    list.add(6);
		    
		    // this is double the max child size; the
		    // tree should have split to accommodate
		    assertEquals(6, list.size());
		    
		    list.add(7);
		    list.add(8);
		    list.add(9);
		    list.add(10);
		    list.add(11);
		    list.add(12);
		    
		    assertEquals(12, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by prepending elements until the
     * list exceeds its clusterSize
     * @throws Exception
     */
    @Test public void testScalablePrependByVerifyingSize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(12);
		    list.prepend(1);
		    list.add(1,11);
		    list.add(1,10);
		    list.add(1,9);
		    list.add(1,8);
		    
		    assertEquals(6, list.size());
		    
		    list.add(1, 6);
		    list.add(1, 5);
		    list.add(1, 4);
		    list.add(1, 3);
		    list.add(1, 2);
		    
		    assertEquals(12, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests scalability by appending values in excess of its
     * cluster size and checking if the values are consistent.
     * @throws Exception
     */
    @Test public void testScalableAppendByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    list.add(4);
		    list.add(5);
		    list.add(6);
		    list.add(7);
		    list.add(8);
		    list.add(9);
		    list.add(10);
		    list.add(11);
		    list.add(12);
		    
		    assertEquals("3", list.get(2).toString());
		    assertEquals("6", list.get(5).toString());
		    assertEquals("9", list.get(8).toString());
		    assertEquals("10", list.get(9).toString());
		    assertEquals("12", list.get(11).toString());
		    assertEquals("1", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests scalability by inserting values until the
     * number of elements in the list exceeds the 
     * cluster size. The test also verifies that the
     * values are consistent.
     * @throws Exception
     */
    @Test public void testScalableInsertByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(1);
		    list.add(12);
		    list.add(1,11);
		    list.add(1,10);
		    list.add(1,9);
		    list.add(1,8);
		    list.add(1,7);
		    list.add(1,6);
		    list.add(1,5);
		    list.add(1,4);
		    list.add(1,3);
		    list.add(1,2);
		    
		    assertEquals("3", list.get(2).toString());
		    assertEquals("6", list.get(5).toString());
		    assertEquals("9", list.get(8).toString());
		    assertEquals("10", list.get(9).toString());
		    assertEquals("12", list.get(11).toString());
		    assertEquals("1", list.get(0).toString());
		}
	    }, taskOwner);
    }
    
    
    /**
     * Tests scalability by prepending elements in excess
     * of the cluster size, and verifies that the values
     * are consistent.
     * @throws Exception
     */
    @Test public void testScalablePrependByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(12);
		    list.prepend(11);
		    list.prepend(10);
		    list.prepend(9);
		    list.prepend(8);
		    list.prepend(7);
		    list.prepend(6);
		    list.prepend(5);
		    list.prepend(4);
		    list.prepend(3);
		    list.prepend(2);
		    list.prepend(1);
		    
		    
		    assertEquals("3", list.get(2).toString());
		    assertEquals("6", list.get(5).toString());
		    assertEquals("9", list.get(8).toString());
		    assertEquals("10", list.get(9).toString());
		    assertEquals("12", list.get(11).toString());
		    assertEquals("1", list.get(0).toString());
		}
	    }, taskOwner);
    }  
    
    
    
    /**
     * Tests scalability by adding a collection to make the
     * list's size exceed that specified by the cluster
     * size
     * @throws Exception
     */
    @Test public void testScalableAddAll()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.add(1);
		    list.add(12);
		    
		    Collection<Integer> c = new ArrayList<Integer>();
		    c.add(2); 
		    c.add(3);
		    c.add(4);
		    c.add(5);
		    c.add(6);
		    c.add(7);
		    c.add(8);
		    c.add(9);
		    c.add(10);
		    c.add(11);
		    
		    list.addAll(1, c);
		    
		    assertEquals(12, list.size());
		    assertEquals("9", list.get(8).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Creates a list of size 12 by appending the values
     * 0 through 11 in order.
     * @return
     */
    private ScalableList<Integer> makeList(){
    	ScalableList<Integer> list = new ScalableList<Integer>(3,3);
    	list.add(0);
	    list.add(1);
	    list.add(2);
	    list.add(3);
	    list.add(4);
	    list.add(5);
	    list.add(6);
	    list.add(7);
	    list.add(8);
	    list.add(9);
	    list.add(10);
	    list.add(11);
	    return list;
    }
    
    /**
     * Tests scalability by removing elements from a
     * large list and verifying the expected size.
     * @throws Exception
     */
    @Test public void testScalableRemoveByVerifyingSize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
		    list.remove(5);
		    assertEquals("6", list.get(5).toString());
		    list.remove(9);
		    assertEquals("11", list.get(9).toString());
		    list.remove(1);
		    assertEquals("2", list.get(1).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by removing elements from a
     * large list and verifying that the values are
     * consistent.
     * @throws Exception
     */
    @Test public void testScalableRemoveByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(3, 3);
		    list.prepend(12);
		    list.prepend(11);
		    list.prepend(10);
		    list.prepend(9);
		    list.prepend(8);
		    list.prepend(7);
		    
		    assertEquals(6, list.size());
		    
		    list.prepend(6);
		    list.prepend(5);
		    list.prepend(4);
		    list.prepend(3);
		    list.prepend(2);
		    list.prepend(1);
		    
		    assertEquals(12, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by assessing the
     * index of values in a large list
     * @throws Exception
     */
    @Test public void testScalableIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			assertEquals(2, list.indexOf(2));
			assertEquals(8, list.indexOf(8));
			assertEquals(11, list.indexOf(11));
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by assessing the last
     * index of duplicated elements in a large
     * list.
     * @throws Exception
     */
    @Test public void testScalableLastIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			list.add(1);
			assertEquals(12, list.lastIndexOf(1));
			
			list.add(1); 
			list.add(1);
			assertEquals(14, list.lastIndexOf(1));
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by assessing the accuracy
     * of the retrieved element from a set operation,
     * as well as its value after the operation
     * takes place.
     * @throws Exception
     */
    @Test public void testScalableSet()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			assertEquals("2", list.set(2, 999).toString());
		    assertEquals("999", list.get(2).toString());
		    assertEquals("8", list.set(8, 9999).toString());
		    assertEquals("9999", list.get(8).toString());
		    assertEquals("11", list.set(11, 99999).toString());
		    assertEquals("99999", list.get(11).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * Tests scalability by verifying an iterator's
     * size and values for a large list. 
     * @throws Exception
     */
    @Test public void testScalableIterator()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			Iterator<Integer> iter = list.iterator();
			
			assertEquals(true, iter.hasNext());
			
			// Go through all the elements
			int size = 0;
			int value = 0;
			while (iter.hasNext()){
				value = iter.next();
				size++;

				// Randomly check values during the iterations
				if (size == 2 || size == 8 || size == 10){
					assertEquals(size, value);
				}
			}
			assertEquals(list.size(), size);
		}
	    }, taskOwner);
    }    
    
    
    /**
     * Tests scalability by obtaining the head element
     * from a large list.
     * @throws Exception
     */
    @Test public void testScalableGetFirst()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			
			assertEquals("0", list.getFirst().toString());
		}
	    }, taskOwner);
    }    
    
    
    
    /**
     * Tests scalability by retrieving the tail element
     * from a large list.
     * @throws Exception
     */
    @Test public void testScalableGetLast()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			
			assertEquals("11", list.getLast().toString());
		}
	    }, taskOwner);
    }    
    
    
    /*
     *  //////////////////////////////////////////////////////////////
     *  	EXCEPTIONAL USE CASES (THESE SHOULD THROW EXCEPTIONS)
     *  //////////////////////////////////////////////////////////////
     */
    
        
    /**
     * Tests the lists ability to detect when an element is
     * trying to be added to an invalid index.
     * @throws Exception
     */
    @Test public void testAddingToInvalidIndex()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
			list.add(1);
			list.add(2);
			
			try {
				list.add(5, 999);
				fail("Expected an IllegalArgumentException when adding to "+
						"an invalid index");
			} catch (IllegalArgumentException iae ){
			}
			
		}
	    }, taskOwner);
    }    
    
    
    /**
     * Tests the list's ability to throw an exception when
     * trying to retrieve a value from an invalid index.
     * @throws Exception
     */
    @Test public void testGettingElementFromInvalidUpperIndex()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
			list.add(1);
			list.add(2);
			
			try {
				list.get(2);
				fail("Expecting IllegalArgumentException for accessing "+
						"element outside of range.");
			} catch (IllegalArgumentException iae ){
			}
			
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to throw an exception when
     * trying to retrieve an element using a negative
     * index.
     * @throws Exception
     */
    @Test public void testGettingElementFromInvalidLowerIndex()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
			list.add(1);
			list.add(2);
			
			try {
				list.get(-1);
				fail("Expecting IllegalArgumentException for accessing "+
						"element outside of range");
			} catch (IllegalArgumentException iae){
			}
			
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to prevent a null element from being
     * added.
     * @throws Exception
     */
    @Test public void testAddingNullElement()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(6, 6);
			list.add("A");
			list.add("B");
			
			try {
				list.add(null);
				fail("Expecting NullPointerException");
			} catch (NullPointerException npe){
			}
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to detect a null element
     * inside a collection to be added.
     * @throws Exception
     */
    @Test public void testAddingAllUsingCollectionContainingNull()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(6, 6);
			list.add("A");
			
			Collection<String> c = new ArrayList<String>();
			c.add("B");
			c.add(null);
			
			try {
				list.addAll(c);
				fail("Expecting NullPointerException");
			} catch (NullPointerException npe){
			}
			
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to detect a null element
     * in a collection when inserting the collection in 
     * the middle of the list.
     * @throws Exception
     */
    @Test public void testAddingAllInMiddleUsingCollectionContainingNull()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
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
			} catch (NullPointerException npe){
			}
			
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to detect that a list
     * is not empty.
     * @throws Exception
     */
    @Test public void testIsEmptyShouldReturnFalse()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(6, 6);
			list.add(1);
			assertFalse(list.isEmpty());
			
			list.add(2);
			list.removeFirst();
			assertFalse(list.isEmpty());
		}
	    }, taskOwner);
    }    
    
    /**
     * Tests the list's ability to not find a particular
     * entry using {@code indexOf} after it has been removed.
     * @throws Exception
     */
    @Test public void testIndexOfNonExistentUsingRemovals()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
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
     * Tests the list's ability to not find a duplicated
     * object after it has been removed, using the
     * lastIndexOf() method.
     * @throws Exception
     */
    @Test public void testLastIndexOfNonExistentUsingRemovals()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(6, 6);
			list.add("A");
			list.add("B");
			list.add("C");
			list.remove(2);
			assertEquals(-1, list.lastIndexOf("C"));

			list.add("C");
			list.remove("C");
			assertEquals(-1, list.lastIndexOf("C"));
		}
	    }, taskOwner);
    }   
    
    
}
