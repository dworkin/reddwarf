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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.sgs.app.util.ScalableHashMap;
import com.sun.sgs.app.util.ScalableList;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;

/**
 * Test the {@link ScalableHashMap} class.
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
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    
		    assertEquals(1, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testAppendingToList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }

    /**
     * 
     * @throws Exception
     */
    @Test public void testPrependToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.prepend(2);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testPrependIntoEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.prepend(1);
		    
		    assertEquals(1, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testInsertIntoList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(1, 999);
		    
		    assertEquals(3, list.size());
		}
	    }, taskOwner);
    }
    
   
    /**
     * 
     * @throws Exception
     */
    @Test public void testAddingAllToEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testAppendingAllToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testAddingAllMiddleToNonEmptyList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
			list.add(1);
			list.add(5);
			
			Collection<Integer> c = new ArrayList<Integer>();
			c.add(2);
			c.add(3);
			c.add(4);
			
			assertTrue(list.addAll(1, c));
		    assertEquals(5, list.size());
		    // get the middle element
		    assertEquals(3, list.get(2));
		}
	    }, taskOwner);
    }
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testGetHeadFromListOfSizeOne()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    
		    assertEquals(1, list.get(0));
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
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals(1, list.get(0));
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testGetMiddleFromListOfArbitrarySize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals(2, list.get(1));
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testGetEndFromListOfArbitrarySize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals(3, list.get(2));
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveHeadFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    list.remove(0);
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveMiddleFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.remove(1);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveEndFromList()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.remove(2);
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.remove(1);
		    
		    assertEquals("2", obj.toString());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveHead()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.removeFirst();
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveHeadAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.removeFirst();
		    
		    assertEquals("1", obj.toString());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveTail()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    list.removeLast();
		    
		    assertEquals(2, list.size());
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveTailAndVerifyResult()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    Object obj = list.removeLast();
		    
		    assertEquals("3", obj.toString());
		}
	    }, taskOwner);
    }
    
    
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testRemoveUsingObjectReference()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Object> list = new ScalableList<Object>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testRemoveUsingObjectReferenceAndVerify()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Object> list = new ScalableList<Object>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testIterator()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testIsEmpty()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    
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
     * 
     * @throws Exception
     */
    @Test public void testIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
		    list.add(1);
		    list.add(2);
		    list.add(3);
		    
		    assertEquals(1, list.indexOf(2));
		}
	    }, taskOwner);
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test public void testLastIndexOf()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<String> list = new ScalableList<String>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testSet()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
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
     * 
     * @throws Exception
     */
    @Test public void testScalablePrependByVerifyingSize()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testScalableAppendByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testScalableInsertByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
     * @throws Exception
     */
    @Test public void testScalablePrependByVerifyingValues()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = new ScalableList<Integer>(5, 5);
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
     * 
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
     * 
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
     * 
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
     * 
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
     * 
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
     * 
     * @throws Exception
     */
    @Test public void testScalableSet()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
			ScalableList<Integer> list = makeList();
			list.set(2, 999);
		    assertEquals("999", list.get(2).toString());
		    list.set(8, 9999);
		    assertEquals("9999", list.get(8).toString());
		    list.set(11, 99999);
		    assertEquals("99999", list.get(11).toString());
		}
	    }, taskOwner);
    }
    
    /**
     * 
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
        
    
}
