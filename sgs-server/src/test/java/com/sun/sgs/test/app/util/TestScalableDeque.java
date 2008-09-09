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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;

import com.sun.sgs.app.util.ScalableDeque;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.ManagedSerializable;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import java.util.Map.Entry;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import junit.framework.JUnit4TestAdapter;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;

import static com.sun.sgs.test.util.UtilReflection.getConstructor;
import static com.sun.sgs.test.util.UtilReflection.getMethod;

/**
 * Test the {@link ScalableDeque} class.
 */
@RunWith(NameRunner.class)
public class TestScalableDeque extends Assert {

    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    /** A fixed random number generator for use in the test. */
    private static final Random RANDOM = new Random(1337);

    /**
     * Test management.
     */

    @BeforeClass public static void setUpClass() throws Exception {
	serverNode = new SgsTestNode("TestScalableDeque", null,
				     createProps("TestScalableDeque"));
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    @AfterClass public static void tearDownClass() throws Exception {
	serverNode.shutdown(true);
    }


    
    /*
     * Test constructors
     */

    @Test public void testNoArgConstructor() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableDeque<Integer> deque = new ScalableDeque<Integer>();
		}
	    }, taskOwner);
    }


    @Test public void testOneArgConstructorTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(true);
		}
	    }, taskOwner);
    }

    @Test public void testOneArgConstructorFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(false);
		}
	    }, taskOwner);
    }


    @Test public void testTwoArgConstructorTrueFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(true, false);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorTrueTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(true, true);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorFalseFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(false, false);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorFalseTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    new ScalableDeque<Integer>(false, true);
		}
	    }, taskOwner);
    }

    @Test public void testCopyConstructor() throws Exception {

	final Deque<Integer> control = new ArrayDeque<Integer>();

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    for (int i = 0; i < 32; i++) {
			control.offer(i);
		    }
		    ScalableDeque<Integer> test =
			new ScalableDeque<Integer>(control);
		    assertTrue(control.containsAll(test));
		    assertTrue(test.containsAll(control));
		}
	    }, taskOwner);
    }

    @Test public void testNullCopyConstructor() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    try {
			new ScalableDeque<Integer>(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException npe) {
		    }
		}
	    }, taskOwner);
    }
    

    /*
     * Test size
     */

    @Test public void testSizeOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertEquals(0, d.size());

		}
	    }, taskOwner);
    }

    @Test public void testSizeOnNonEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    assertEquals(1, d.size());

		}
	    }, taskOwner);
    }

    @Test public void testSizeOnDequeAfterRemoval() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    d.poll();
		    assertEquals(0, d.size());

		}
	    }, taskOwner);
    }

    @Test public void testSizeAfterClear() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    d.clear();
		    assertEquals(0, d.size());

		}
	    }, taskOwner);
    }

    @Test public void testSizeWithMultipleSameElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.offer(1);
		    assertEquals(10, d.size());

		}
	    }, taskOwner);
    }

    /*
     * Test isEmtpy
     */
    @Test public void testIsEmptyTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertTrue(d.isEmpty());

		}
	    }, taskOwner);
    }

    @Test public void testIsEmptyFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    assertFalse(d.isEmpty());

		}
	    }, taskOwner);
    }

    @Test public void testIsEmptyAfterClear() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    d.clear();
		    assertTrue(d.isEmpty());

		}
	    }, taskOwner);
    }

    @Test public void testIsEmptyAfterRemoval() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    d.remove();
		    assertTrue(d.isEmpty());
		}
	    }, taskOwner);
    }
    

    /*
     * Test clear
     */
    @Test public void testClear() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    assertFalse(d.isEmpty());
		    d.clear();
		    assertTrue(d.isEmpty());
		    assertEquals(0, d.size());
		    assertEquals(null, d.poll());
		}
	    }, taskOwner);
    }

    @Test public void testClearOnEmptyMap() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertTrue(d.isEmpty());
		    d.clear();
		    assertTrue(d.isEmpty());
		    assertEquals(0, d.size());
		    assertEquals(null, d.poll());
		}
	    }, taskOwner);
    }

    @Test public void testMultipleClears() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertTrue(d.isEmpty());
		    d.clear();
		    d.clear();
		    d.clear();
		    assertTrue(d.isEmpty());
		    assertEquals(0, d.size());
		    assertEquals(null, d.poll());
		}
	    }, taskOwner);
    }

    @Test public void testClearThenAdd() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertTrue(d.isEmpty());
		    d.clear();
		    d.add(5);
		    assertFalse(d.isEmpty());
		    assertEquals(1, d.size());
		    assertEquals(5, d.getFirst());
		}
	    }, taskOwner);
    }


    /*
     * Test contains
     */

    @Test public void testContains() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.offer(1);
		    assertTrue(d.contains(1));

		}
	    }, taskOwner);
    }

    @Test public void testContainsOnEmptyMap() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertFalse(d.contains(1));

		}
	    }, taskOwner);
    }

    @Test public void testContainsWithMultipleElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.offer(i);
		    
		    for (int i = 0; i < 10; ++i) 
			assertTrue(d.contains(i));

		}
	    }, taskOwner);
    }

    @Test public void testContainsWithMultipleSameElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.offer(1);
		    		   
		    assertTrue(d.contains(1));

		}
	    }, taskOwner);
    }


    @Test public void testSlowContains() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    d.offer(1);
		    assertTrue(d.contains(1));

		}
	    }, taskOwner);
    }

    @Test public void testSlowContainsOnEmptyMap() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    assertFalse(d.contains(1));

		}
	    }, taskOwner);
    }

    @Test public void testSlowContainsWithMultipleElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i) 
			d.offer(i);
		    
		    for (int i = 0; i < 10; ++i) 
			assertTrue(d.contains(i));

		}
	    }, taskOwner);
    }

    @Test public void testSlowContainsWithMultipleSameElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i) 
			d.offer(1);
		    		   
		    assertTrue(d.contains(1));

		}
	    }, taskOwner);
    }


    /*
     * Test add/offer operations
     */
    @Test public void testAdd() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.add(1);		    		   
		    assertEquals(1, d.getFirst());

		}
	    }, taskOwner);
    }


    @Test public void testMultipleAdds() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    		   
		    for (int i = 0; i < 10; ++i) 
			assertEquals(i, d.remove());

		}
	    }, taskOwner);
    }

    @Test public void testAddAll() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    Deque<Integer> control = new ArrayDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			control.add(i);
		    		
		    d.addAll(control);

		    assertEquals(10, d.size());				 
		    for (int i = 0; i < 10; ++i) 
			assertTrue(d.contains(i));

		    assertTrue(d.containsAll(control));
		    assertTrue(control.containsAll(d));
		}
	    }, taskOwner);
    }

    @Test public void testAddFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.addFirst(1);		    		   
		    assertEquals(1, d.getFirst());

		}
	    }, taskOwner);
    }

    @Test public void testMultipleAddFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    for (int i = 0; i < 10; ++i)
			d.addFirst(i);		    		   
		    assertEquals(9, d.getFirst());

		}
	    }, taskOwner);
    }

    @Test public void testAddLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.addLast(1);		    		   
		    assertEquals(1, d.getLast());

		}
	    }, taskOwner);
    }

    @Test public void testMultipleAddLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    for (int i = 0; i < 10; ++i)
			d.addLast(i);		    		   
		    assertEquals(9, d.getLast());
		}
	    }, taskOwner);
    }

    @Test public void testOffer() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.offer(1);		    		   
		    assertEquals(1, d.getFirst());

		}
	    }, taskOwner);
    }


    @Test public void testMultipleOffers() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.offer(i);
		    		   
		    for (int i = 0; i < 10; ++i) 
			assertEquals(i, d.remove());

		}
	    }, taskOwner);
    }

    @Test public void testOfferFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.offerFirst(1);		    		   
		    assertEquals(1, d.getFirst());

		}
	    }, taskOwner);
    }

    @Test public void testMultipleOfferFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    for (int i = 0; i < 10; ++i)
			d.offerFirst(i);		    		   
		    assertEquals(9, d.getFirst());

		}
	    }, taskOwner);
    }

    @Test public void testOfferLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    d.offerLast(1);		    		   
		    assertEquals(1, d.getLast());

		}
	    }, taskOwner);
    }

    @Test public void testMultipleOfferLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    for (int i = 0; i < 10; ++i)
			d.offerLast(i);		    		   
		    assertEquals(9, d.getLast());
		}
	    }, taskOwner);
    }


    @Test public void testAddLastNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    try {
			d.addLast(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {
			
		    }
		}
	    }, taskOwner);
    }

    @Test public void testAddFirstNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();		    
		    try {
			d.addFirst(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {
			
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test peek/get/element access operations
     */
    @Test public void testElement() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);		    		   		    
		    assertEquals(2, d.element());
		}
	    }, taskOwner);
    }

    @Test public void testElementOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.element();
			fail("expected NoSuchElementException");
		    }
		    catch (NoSuchElementException nsee) {

		    }
		}
	    }, taskOwner);
    }



    @Test public void testPeek() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);		    		   		    
		    assertEquals(2, d.peek());
		}
	    }, taskOwner);
    }

    @Test public void testPeekOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertEquals(null, d.peek());
		}
	    }, taskOwner);
    }

    @Test public void testPeekFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);
		    d.add(3);
		    assertEquals(2, d.peekFirst());
		}
	    }, taskOwner);
    }

    @Test public void testPeekLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);
		    d.add(3);
		    assertEquals(3, d.peekLast());
		}
	    }, taskOwner);
    }

    @Test public void testGetFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);
		    d.add(3);
		    assertEquals(2, d.getFirst());
		}
	    }, taskOwner);
    }

    @Test public void testGetLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);
		    d.add(3);
		    assertEquals(3, d.getLast());
		}
	    }, taskOwner);
    }

    @Test public void testGetFirstOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.getFirst();
			fail("expected NoSuchElementException");
		    } catch (NoSuchElementException nsee) {

		    }
		}
	    }, taskOwner);
    }

    @Test public void testGetLastOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.getLast();
			fail("expected NoSuchElementException");
		    } catch (NoSuchElementException nsee) {

		    }
		}
	    }, taskOwner);
    }
  
    /*
     * Test remove operations
     */
    @Test public void testRemove() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);		    		   		    
		    assertEquals(2, d.remove());
		}
	    }, taskOwner);
    }

    @Test public void testRemoveFirst() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);		    		   		    
		    assertEquals(2, d.removeFirst());
		}
	    }, taskOwner);
    }

    @Test public void testRemoveFirstWithMultipleElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);		    		   		    
		    assertEquals(0, d.removeFirst());
		}
	    }, taskOwner);
    }

    @Test public void testRemoveLast() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(2);		    		   		    
		    assertEquals(2, d.removeLast());
		}
	    }, taskOwner);
    }

    @Test public void testRemoveLastWithMultipleElements() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);		    		   		    
		    assertEquals(9, d.removeLast());
		}
	    }, taskOwner);
    }

    @Test public void testRemoveFirstOccurrence() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeFirstOccurrence(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveFirstOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeFirstOccurrence(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveFirstOccurrenceWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeFirstOccurrence(5));		    
		    assertEquals(29, d.size());
		    assertTrue(d.contains(5));

		    // the first instance of 5 should be at element 14 now
		    int iterCount = 0;
		    Iterator<Integer> iter = d.iterator();
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    assertEquals(14, iterCount);
		    iterCount++;

		    // check that we still have the second instance as
		    // well
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    // the second instances is at 24
		    assertEquals(24, iterCount);
		}
	    }, taskOwner);
    }

    @Test public void testRemoveLastOccurrence() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeLastOccurrence(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveLastOccurrenceWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeLastOccurrence(5));		    
		    assertEquals(29, d.size());
		    assertTrue(d.contains(5));

		    // the first instance of 5 should be at element 5 still
		    int iterCount = 0;
		    Iterator<Integer> iter = d.iterator();
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    assertEquals(5, iterCount);
		    iterCount++;

		    // check that we still have the second instance as
		    // well
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    // the second instances is at 15
		    assertEquals(15, iterCount);
		}
	    }, taskOwner);
    }
    
    @Test public void testRemoveLastOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeLastOccurrence(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllOccurrences() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeAllOccurrences(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllOccurrencesWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeAllOccurrences(5));		    
		    assertEquals(27, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }
    
    @Test public void testRemoveAllOccurrencesWhenOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeAllOccurrences(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAll() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);
		    assertTrue(d.removeAll(c));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);		    
		    assertTrue(d.removeAll(c));		    
		    assertEquals(27, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllWithMultipleRemoves() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);		    
		    c.add(6);
		    assertTrue(d.removeAll(c));		    
		    assertEquals(24, d.size());
		    assertFalse(d.contains(5));
		    assertFalse(d.contains(6));
		}
	    }, taskOwner);
    }
    
    @Test public void testRemoveAllWhenOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(10);		    
		    assertFalse(d.removeAll(c));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.remove(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {

		    }		    
		}
	    }, taskOwner);
    }
    
    @Test public void testRemoveFirstOccurrenceNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.removeFirstOccurrence(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {

		    }		    
		}
	    }, taskOwner);
    }

    @Test public void testRemoveLastOccurrenceNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.removeLastOccurrence(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {

		    }		    
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllOccurrencesNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.removeAllOccurrences(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {

		    }		    
		}
	    }, taskOwner);
    }

    @Test public void testRemoveAllNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.removeAll(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException npe) {

		    }		    
		}
	    }, taskOwner);
    }

    /*
     * Test remove operations when fast random access is not supported
     */

    @Test public void testSlowRemoveFirstOccurrence() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeFirstOccurrence(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveFirstOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeFirstOccurrence(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveFirstOccurrenceWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeFirstOccurrence(5));		    
		    assertEquals(29, d.size());
		    assertTrue(d.contains(5));

		    // the first instance of 5 should be at element 14 now
		    int iterCount = 0;
		    Iterator<Integer> iter = d.iterator();
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    assertEquals(14, iterCount);
		    iterCount++;

		    // check that we still have the second instance as
		    // well
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    // the second instances is at 24
		    assertEquals(24, iterCount);
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveLastOccurrence() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeLastOccurrence(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveLastOccurrenceWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeLastOccurrence(5));		    
		    assertEquals(29, d.size());
		    assertTrue(d.contains(5));

		    // the first instance of 5 should be at element 5 still
		    int iterCount = 0;
		    Iterator<Integer> iter = d.iterator();
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    assertEquals(5, iterCount);
		    iterCount++;

		    // check that we still have the second instance as
		    // well
		    while (iter.hasNext()) {
			int i = iter.next();
			if (i == 5)
			    break;
			iterCount++;
		    }
		    // the second instances is at 15
		    assertEquals(15, iterCount);
		}
	    }, taskOwner);
    }
    
    @Test public void testSlowRemoveLastOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeLastOccurrence(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveAllOccurrences() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertTrue(d.removeAllOccurrences(5));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveAllOccurrencesWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    assertTrue(d.removeAllOccurrences(5));		    
		    assertEquals(27, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }
    
    @Test public void testSlowRemoveAllOccurrencesWhenOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    assertFalse(d.removeAllOccurrences(10));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveAll() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);
		    assertTrue(d.removeAll(c));		    
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveAllWithMultipleOccurrences() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);		    
		    assertTrue(d.removeAll(c));		    
		    assertEquals(27, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testSlowRemoveAllWithMultipleRemoves() 
	throws Exception {
	
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int j = 0; j < 3; ++j) {
			for (int i = 0; i < 10; ++i)
			    d.add(i);	
		    }
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(5);		    
		    c.add(6);
		    assertTrue(d.removeAll(c));		    
		    assertEquals(24, d.size());
		    assertFalse(d.contains(5));
		    assertFalse(d.contains(6));
		}
	    }, taskOwner);
    }
    
    @Test public void testSlowRemoveAllWhenOccurrenceNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(false, false);
		    for (int i = 0; i < 10; ++i)
			d.add(i);	
		    Collection<Integer> c = new LinkedList<Integer>();
		    c.add(10);		    
		    assertFalse(d.removeAll(c));  
		    assertEquals(10, d.size());
		    assertFalse(d.contains(10));
		}
	    }, taskOwner);
    }


    /*
     * Test push/pop
     */
    @Test public void testPush() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.push(1);
		    assertEquals(1, d.remove());
		}
	    }, taskOwner);
    }

    @Test public void testPop() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    d.add(1);
		    assertEquals(1, d.pop());
		}
	    }, taskOwner);
    }

    @Test public void testPopOnEmptyDeque() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    try {
			d.pop();
			fail("expected NoSuchElementException");
		    } catch (NoSuchElementException nsee) {
			
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test iterator
     */

    @Test public void testIterator() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);

		    Iterator<Integer> iter = d.iterator();
		    int count = 0;
		    while (iter.hasNext())
			assertEquals(count++, iter.next());
		    assertEquals(10, count);
		}
	    }, taskOwner);
    }

    @Test public void testIteratorHasNext() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    assertFalse(d.iterator().hasNext());
		    d.add(1);
		    assertTrue(d.iterator().hasNext());
		}
	    }, taskOwner);
    }

    @Test public void testIteratorRemoval() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);

		    Iterator<Integer> iter = d.iterator();
		    int count = 0;
		    while (iter.hasNext()) {
			iter.next();
			if (count++ == 5)
			    iter.remove();
		    }
			
		    assertEquals(10, count);
		    assertEquals(9, d.size());
		    assertFalse(d.contains(5));
		}
	    }, taskOwner);
    }

    @Test public void testIteratorRemovalTwice() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);

		    Iterator<Integer> iter = d.iterator();
		    iter.next();
		    iter.remove();
		    try {
			iter.remove();
			fail("expected IllegalStateException");
		    }
		    catch (IllegalStateException ise) {
			
		    }
		}
	    }, taskOwner);
    }

    @Test public void testIteratorRemovalBeforeNext() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);

		    Iterator<Integer> iter = d.iterator();
		    try {
			iter.remove();
			fail("expected IllegalStateException");
		    }
		    catch (IllegalStateException ise) {
			
		    }
		}
	    }, taskOwner);
    }

    @Test public void testIteratorNextNotPresent() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    Iterator<Integer> iter = d.iterator();

		    try {
			iter.next();
			fail("expected NoSuchElementException");
		    }
		    catch (NoSuchElementException nsee) {
			
		    }
		}
	    }, taskOwner);
    }

    @Test public void testDescendingIterator() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);

		    Iterator<Integer> iter = d.descendingIterator();
		    int count = 9;
		    while (iter.hasNext())
			assertEquals(count--, iter.next());
		    // if we've gone through all 10 elements, count
		    // should be 9 - 10 = -1
		    assertEquals(-1, count);
		}
	    }, taskOwner);
    }
    

    /*
     * Test serializability
     */

    @Test public void testDequeSeriazable() throws Exception {
	final String name = "test-deque";

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    AppContext.getDataManager().setBinding(name, d);
		}
	    }, taskOwner);

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    ScalableDeque<Integer> d = 
			uncheckedCast(AppContext.getDataManager().getBinding(name));
		    assertEquals(10, d.size());
		    int i = 0;
		    for (Integer inDeque : d) {
			assertEquals(i++, inDeque);
		    }
		    
		    AppContext.getDataManager().removeBinding(name);
		}
	    }, taskOwner);

    }

    @Test public void testIteratorSeriazable() throws Exception {
	final String name = "test-iterator";

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    AppContext.getDataManager().setBinding(name, d.iterator());
		}
	    }, taskOwner);

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Iterator<Integer> it = 
			uncheckedCast(AppContext.
				      getDataManager().getBinding(name));

		    int i = 0;
		    while (it.hasNext())
			assertEquals(i++, it.next());
		    assertEquals(10, i);

		    AppContext.getDataManager().removeBinding(name);
		}
	    }, taskOwner);
    }

    @Test public void testIteratorSeriazableWithRemovals() throws Exception {
	final String name = "test-iterator";
	final String name2 = "test-deque";

	// create the deque
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    AppContext.getDataManager().setBinding(name, d.iterator());
		    AppContext.getDataManager().setBinding(name2, d);
		}
	    }, taskOwner);

	// remove some elements while the iterator is serialized
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = 
			uncheckedCast(AppContext.getDataManager().getBinding(name2));
		    for (int i = 1; i < 10; i+=2) 
			d.remove(i);		    
		}
	    }, taskOwner);


	// load the iterator back
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Iterator<Integer> it = 
			uncheckedCast(AppContext.
				      getDataManager().getBinding(name));

		    int i = 0;
		    while (it.hasNext()) {
			assertEquals(i, it.next());
			i += 2;
		    }
		    assertEquals(10, i);

		    AppContext.getDataManager().removeBinding(name);
		    AppContext.getDataManager().removeBinding(name2);
		}
	    }, taskOwner);
    }

    @Test public void testConcurrentIteratorSeriazableWithRemovalOfNextElements() 
	throws Exception {

	final String name = "test-iterator";
	final String name2 = "test-deque";

	// create the deque
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>(true);
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    AppContext.getDataManager().setBinding(name, d.iterator());
		    AppContext.getDataManager().setBinding(name2, d);
		}
	    }, taskOwner);

	// remove the iterator's first 5 elements while the
	// iterator is serialized
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = 
			uncheckedCast(AppContext.getDataManager().getBinding(name2));
		    for (int i = 0; i < 5; i++) 
			d.remove(i);		    
		}
	    }, taskOwner);


	// load the iterator back
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Iterator<Integer> it = 
			uncheckedCast(AppContext.
				      getDataManager().getBinding(name));

		    int i = 5;
		    while (it.hasNext()) {
			assertEquals(i, it.next());
			i ++;
		    }
		    assertEquals(10, i);

		    AppContext.getDataManager().removeBinding(name);
		    AppContext.getDataManager().removeBinding(name2);
		}
	    }, taskOwner);
    }

    @Test public void testNonConcurrentIteratorSeriazableWithRemovalOfNextElements() 
	throws Exception {

	final String name = "test-iterator";
	final String name2 = "test-deque";

	// create the deque
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    AppContext.getDataManager().setBinding(name, d.iterator());
		    AppContext.getDataManager().setBinding(name2, d);
		}
	    }, taskOwner);

	// remove the iterator's first 5 elements while the
	// iterator is serialized
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = 
			uncheckedCast(AppContext.getDataManager().getBinding(name2));
		    for (int i = 0; i < 5; i++) 
			d.remove();		    
		}
	    }, taskOwner);


	// load the iterator back
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Iterator<Integer> it = 
			uncheckedCast(AppContext.
				      getDataManager().getBinding(name));

		    try {
			it.next();
			fail("expected ConcurrentModificationException"); 
		    } catch (ConcurrentModificationException cme) {
			// it should throw this
		    } finally {
			AppContext.getDataManager().removeBinding(name);
			AppContext.getDataManager().removeBinding(name2);
		    }
		}
	    }, taskOwner);
    }



    @Test public void testIteratorRemovalWhereCurrentElementWasAlreadyRemoved() 
	throws Exception {

	final String name = "test-iterator";
	final String name2 = "test-deque";

	// create the deque
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = new ScalableDeque<Integer>();
		    for (int i = 0; i < 10; ++i) 
			d.add(i);
		    Iterator<Integer> it = d.iterator();
		    // advance the iterator forward one set
		    it.next();		    
		    AppContext.getDataManager().setBinding(name, it);
		    AppContext.getDataManager().setBinding(name2, d);
		}
	    }, taskOwner);

	// remove the iterator's first 5 elements while the
	// iterator is serialized
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableDeque<Integer> d = 
			uncheckedCast(AppContext.getDataManager().getBinding(name2));
		    // remove the first element, which the iterator
		    // already advanced over
		    d.removeFirst();
		}
	    }, taskOwner);


	// load the iterator back
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Iterator<Integer> it = 
			uncheckedCast(AppContext.
				      getDataManager().getBinding(name));

		    // now try to remove the already-removed element
		    // from the iterator
		    it.remove();
		    
		    // the above call shouldn't throw an object not
		    // found exception
		}
	    }, taskOwner);
    }




    private static Properties createProps(String appName) throws Exception {
        Properties props = SgsTestNode.getDefaultProperties(appName, null, 
                                           SgsTestNode.DummyAppListener.class);
        props.setProperty("com.sun.sgs.txn.timeout", "1000000");
        return props;
    }

    /*
     * Test classes
     */

    /**
     * A serializable object that is equal to objects of the same type with the
     * same value.
     */
    static class DummySerializable implements Serializable {
	private static final long serialVersionUID = 1L;
	private final int i;

	DummySerializable(int i) {
	    this.i = i;
	}

	public int hashCode() {
	    return i;
	}

	public boolean equals(Object o) {
	    return o != null &&
		getClass() == o.getClass() &&
		((DummySerializable) o).i == i;
	}
    }

    /**
     * A managed object that is equal to objects of the same type with the
     * same value.
     */
    static class DummyMO extends DummySerializable implements ManagedObject {
	private static final long serialVersionUID = 1L;

	DummyMO(int i) {
	    super(i);
	}
    }

    /**
     * A serializable object that is equal to objects of the same type with the
     * type, but whose hashCode method always returns zero.
     */
    static class EqualHashObj extends DummySerializable {
	private static final long serialVersionUID = 1L;

	EqualHashObj(int i) {
	    super(i);
	}

	public int hashCode() {
	    return 0;
	}
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestScalableDeque.class);
    }
}
