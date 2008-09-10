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

import com.sun.sgs.app.util.ScalableLinkedHashMap;

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

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

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
 * Test the {@link ScalableLinkedHashMap} class.
 */
@RunWith(NameRunner.class)
public class TestScalableLinkedHashMap extends Assert {

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
	serverNode = new SgsTestNode("TestScalableLinkedHashMap", null,
				     createProps("TestScalableLinkedHashMap"));
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    @AfterClass public static void tearDownClass() throws Exception {
	serverNode.shutdown(true);
    }


    // NOTE: we do not test the maximum concurrency in the
    // constructor, as this would take far too long to test (hours).

    /*
     * Test constructors
     */

    @Test public void testNoArgConstructor() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		}
	    }, taskOwner);
    }

    @Test public void testAccessOrderConstructor() 
	throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);
		    ScalableLinkedHashMap<Integer,Integer> test2 =
			new ScalableLinkedHashMap<Integer,Integer>(false);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorTrueFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true, false);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorFalseTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(false, true);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorTrueTrue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true, true);
		}
	    }, taskOwner);
    }

    @Test public void testTwoArgConstructorFalseFalse() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(false, false);
		}
	    }, taskOwner);
    }

    @Test public void testCopyConstructor() throws Exception {
	final Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    for (int i = 0; i < 32; i++) {
			control.put(i,i);
		    }
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(control);
		    assertEquals(control, test);
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testNullCopyConstructor() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    try {
			new ScalableLinkedHashMap<Integer,Integer>(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException npe) {
		    }
		}
	    }, taskOwner);
    }


    /*
     * Test putAll
     */

    @Test public void testPutAllMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();
		    for (int i = 0; i < 32; i++) {
			control.put(i,i);
		    }
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    test.putAll(control);
		    assertEquals(control, test);
		}
	    }, taskOwner);
     }

    @Test public void testPutAllNullArg() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    try {
			test.putAll(null);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    @Test public void testPutAllNotSerializable() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new ScalableLinkedHashMap<Object,Object>();
		    Object nonSerializable = Thread.currentThread();
		    Map<Object,Object> other = new HashMap<Object,Object>();
		    other.put(nonSerializable, Boolean.TRUE);
		    try {
			test.putAll(other);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {
		    }
		    other.clear();
		    other.put(Boolean.TRUE, nonSerializable);
		    try {
			test.putAll(other);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {
		    }
		}
	    }, taskOwner);
    }

    @Test public void testPutAllNullItems() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new ScalableLinkedHashMap<Object,Object>();
		    Object nonSerializable = Thread.currentThread();
		    Map<Object,Object> control = new HashMap<Object,Object>();
		    test.put(0, null);
		    control.put(0, null);
		    test.put(null, 0);
		    control.put(null, 0);
		    assertEquals(test, control);
		}
	    }, taskOwner);
    }

    
    /*
     * Test put()
     */

    @Test public void testPutMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    DummySerializable result = test.put(1, new DummySerializable(1));
		    assertEquals(null, result);
		    result = test.put(1, new DummySerializable(1));
		    assertEquals(new DummySerializable(1), result);
		    result = test.put(1, new DummySerializable(37));
		    assertEquals(new DummySerializable(1), result);
		}
	    }, taskOwner);
    }

    @Test public void testPutNotSerializable() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new ScalableLinkedHashMap<Object,Object>();
		    Object nonSerializable = Thread.currentThread();
		    try {
			test.put(nonSerializable, Boolean.TRUE);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {
			assertTrue(test.isEmpty());
		    }
		    try {
			test.put(Boolean.TRUE, nonSerializable);
			fail("Expected IllegalArgumentException");
		    } catch (IllegalArgumentException e) {
			assertTrue(test.isEmpty());
		    }
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testPutOldValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(Boolean.TRUE, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.put(Boolean.TRUE, Boolean.FALSE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.put(Boolean.TRUE, Boolean.FALSE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testPutOldKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, Boolean.TRUE);
		}
	    }, taskOwner);
	try {
	    txnScheduler.runTask(
	        new AbstractKernelRunnable() {
		    public void run() throws Exception {
			dataService.removeObject(
			    dataService.getBinding("bar"));
			ScalableLinkedHashMap test =
			    (ScalableLinkedHashMap) dataService.getBinding("test");
			assertEquals(null, test.put(new DummyMO(1), Boolean.FALSE));
			assertEquals(Boolean.FALSE, test.get(new DummyMO(1)));
			throw new RuntimeException("Intentional Abort");
		    }
		}, taskOwner);
	} catch (RuntimeException re) {}
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.put(new DummyMO(1), Boolean.FALSE));
		    assertEquals(Boolean.FALSE, test.get(new DummyMO(1)));
		}
	    }, taskOwner);
    }

    @Test public void testPutNullKey() throws Exception {
	final Map<String,Integer> control = new HashMap<String,Integer>();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<String,Integer> test =
			new ScalableLinkedHashMap<String,Integer>();
		    test.put(null, 0);
		    control.put(null, 0);
		    assertEquals(control, test);
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertEquals(control, dataService.getBinding("test"));
		}
	    }, taskOwner);	    
    }

    @Test public void testPutNullValue() throws Exception {
	final Map<Integer,String> control = new HashMap<Integer,String>();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,String> test =
			new ScalableLinkedHashMap<Integer,String>();
		    test.put(0, null);
		    control.put(0, null);
		    assertEquals(control, test);
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertEquals(control, dataService.getBinding("test"));
		}
	    }, taskOwner);
    }

    /*
     * Test get
     */

    @Test public void testGetMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    assertEquals(null, test.get(1));
		    test.put(1, new DummySerializable(1));
		    assertEquals(new DummySerializable(1), test.get(1));
		    assertEquals(null, test.get(new DummySerializable(1)));
		    assertEquals(null, test.get(2));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testGetValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(Boolean.TRUE, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.get(Boolean.TRUE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.get(Boolean.TRUE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testGetKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, Boolean.TRUE);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.get(new DummyMO(1)));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.get(new DummyMO(1)));
		}
	    }, taskOwner);
    }

    @Test public void testGetNullKey() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<String,Integer> test =
			new ScalableLinkedHashMap<String,Integer>();
		    test.put(null, 0);
		    assertEquals(new Integer(0), test.get(null));
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertEquals(
			new Integer(0),
			((Map) dataService.getBinding("test")).get(null));
		}
	    }, taskOwner);
    }

    @Test public void testGetNullValue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,String> test =
			new ScalableLinkedHashMap<Integer,String>();
		    test.put(0, null);
		    assertEquals(null, test.get(0));
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertEquals(null,
				 ((Map) dataService.getBinding(
				     "test")).get(0));
		}
	    }, taskOwner);
    }

    /*
     * Test containsKey
     */

    @Test public void testContainsKeyMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    assertFalse(test.containsKey(1));
		    test.put(1, new DummySerializable(1));
		    assertTrue(test.containsKey(1));
		    assertFalse(test.containsKey(new DummySerializable(1)));
		    assertFalse(test.containsKey(2));
		}
	    }, taskOwner);
    }

    @Test public void testContainsKeyNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<String,Integer> test =
			new ScalableLinkedHashMap<String,Integer>();
		    test.put(null, 0);
		    assertTrue(test.containsKey(null));
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertTrue(((Map) dataService.getBinding(
				    "test")).containsKey(null));
		}
	    }, taskOwner);
    }

    @Test public void testContainsKeyNullOnEmptyMap() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<String,Integer> test =
			new ScalableLinkedHashMap<String,Integer>();
		    assertFalse(test.containsKey(null));
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    assertFalse(((Map) dataService.getBinding(
				     "test")).containsKey(null));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsKeyKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		    test.put(new DummyMO(2), 2);
		    }
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsKey(new DummyMO(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsKey(new DummyMO(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsKeyValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new DummyMO(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsKey(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsKey(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    /*
     * Test containsValue
     */

    @Test public void testContainsValueMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    assertFalse(test.containsValue(new DummySerializable(1)));
		    test.put(1, new DummySerializable(1));
		    assertTrue(test.containsValue(new DummySerializable(1)));
		    assertFalse(test.containsValue(1));
		    assertFalse(test.containsValue(new DummySerializable(2)));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    test.put(0, null);
		    dataService.setBinding("test", test);
		    assertTrue(test.containsValue(null));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			uncheckedCast(dataService.getBinding("test"));
		    assertTrue(test.containsValue(null));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueNullEmptyMap() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    dataService.setBinding("test", test);
		    assertFalse(test.containsValue(null));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			uncheckedCast(dataService.getBinding("test"));
		    assertFalse(test.containsValue(null));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new DummyMO(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsValue(new DummyMO(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsValue(new DummyMO(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		    test.put(new DummyMO(2), 2);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsValue(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsValue(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @Test public void testContainsValue() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[50];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,-j);
		    }

		    for (int i = 0; i < inputs.length; i++) {
			assertTrue(test.containsValue(-inputs[i]));
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test values
     */

    @SuppressWarnings("unchecked")
    @Test public void testValues() throws Exception {
	final Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	final Collection<Integer> controlValues = control.values();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Collection<Integer> values = test.values();
		    
		    assertTrue(values.isEmpty());
		    assertIteratorDone(values.iterator());

		    for (int i = 0; i < 50; i++) {
			int j = RANDOM.nextInt();
			test.put(j,-j);
			control.put(j,-j);
		    }

		    assertEquals(50, values.size());
		    assertTrue(controlValues.containsAll(values));
		    assertIteratorContains(controlValues, values.iterator());

		    dataService.setBinding("values",
					   new ManagedSerializable(values));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ManagedSerializable<Collection<Integer>> ms =
			uncheckedCast(dataService.getBinding("values"));
		    Collection<Integer> values = ms.get();
		    assertEquals(50, values.size());
		    assertTrue(controlValues.containsAll(values));
		    assertIteratorContains(controlValues, values.iterator());
		}
	    }, taskOwner);
    }

    /*
     * Test keySet
     */

    @SuppressWarnings("unchecked")
    @Test public void testKeySet() throws Exception {
	final Map control = new HashMap();
	final Set controlKeys = control.keySet();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map test = new ScalableLinkedHashMap();
		    Set keys = test.keySet();
		    assertEquals(controlKeys, keys);
		    assertIteratorDone(keys.iterator());
		    assertEquals(controlKeys.hashCode(), keys.hashCode());
		    for (int i = 0; i < 50; i++) {
			int j = RANDOM.nextInt();
			test.put(j,-j);
			control.put(j,-j);
		    }
		    assertEquals(controlKeys, keys);
		    assertIteratorContains(controlKeys, keys.iterator());
		    assertEquals(controlKeys.hashCode(), keys.hashCode());
		    dataService.setBinding("keys",
					   new ManagedSerializable(keys));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ManagedSerializable<Set> ms =
			uncheckedCast(dataService.getBinding("keys"));
		    Set keys = ms.get();
		    assertEquals(controlKeys, keys);
		    assertIteratorContains(controlKeys, keys.iterator());
		    assertEquals(controlKeys.hashCode(), keys.hashCode());
		}
	    }, taskOwner);
    }

    /*
     * Test entrySet
     */

    @SuppressWarnings("unchecked")
    @Test public void testEntrySet() throws Exception {
	final Map control = new HashMap();
	final Set controlEntries = control.entrySet();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map test = new ScalableLinkedHashMap();
		    Set entries = test.entrySet();
		    assertEquals(controlEntries, entries);
		    assertIteratorDone(entries.iterator());
		    assertEquals(controlEntries.hashCode(), entries.hashCode());
		    for (int i = 0; i < 50; i++) {
			int j = RANDOM.nextInt();
			test.put(j,-j);
			control.put(j,-j);
		    }
		    assertEquals(controlEntries, entries);
		    assertIteratorContains(controlEntries, entries.iterator());
		    assertEquals(controlEntries.hashCode(), entries.hashCode());
		    dataService.setBinding("entries",
					   new ManagedSerializable(entries));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ManagedSerializable<Set> ms =
			uncheckedCast(dataService.getBinding("entries"));
		    Set entries = ms.get();
		    assertEquals(controlEntries, entries);
		    assertEquals(controlEntries.hashCode(), entries.hashCode());
		}
	    }, taskOwner);
    }

    /*
     * Test equals and hashCode
     */

    @SuppressWarnings("unchecked")
    @Test public void testEqualHashObj() throws Exception {
	final Map control = new HashMap();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    assertFalse(test.equals(null));
		    assertFalse(test.equals(1));
		    assertTrue(test.equals(control));
		    assertEquals(test.hashCode(), control.hashCode());
		    for (int i = 0; i < 50; i++) {
			int j = RANDOM.nextInt();
			test.put(j,-j);
			control.put(j,-j);
		    }
		    assertTrue(test.equals(control));
		    assertEquals(test.hashCode(), control.hashCode());
		    dataService.setBinding("test", test);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertTrue(test.equals(control));
		    assertEquals(test.hashCode(), control.hashCode());
		}
	    }, taskOwner);
    }

    /*
     * Test toString
     */

    @Test public void testToString() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    assertEquals("{}", test.toString());
		    test.put(1, 2);
		    assertEquals("{1=2}", test.toString());
		}
	    }, taskOwner);
    }

    /*
     * Test remove
     */

    @Test public void testRemoveMisc() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    assertEquals(null, test.remove(1));
		    test.put(1, new DummySerializable(1));
		    assertEquals(null, test.remove(2));
		    assertEquals(null, test.remove(new DummySerializable(1)));
		    assertEquals(new DummySerializable(1), test.remove(1));
		    assertTrue(test.isEmpty());
		    assertEquals(null, test.remove(1));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveNullKey() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<String,Integer> test =
			new ScalableLinkedHashMap<String,Integer>();
		    Map<String,Integer> control = new HashMap<String,Integer>();
		    test.put(null, 0);
		    control.put(null, 0);
		    assertEquals(control, test);
		    test.remove(null);
		    control.remove(null);
		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testRemoveValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.remove(1);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
		    }
		    assertEquals(null, test.remove(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    try {
			test.remove(1);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
		    }
		    assertEquals(null, test.remove(2));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testRemoveKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.remove(new DummyMO(1)));
		    assertEquals(null, test.remove(1));
		    assertEquals(null, test.remove(new DummyMO(2)));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test =
			(ScalableLinkedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.remove(new DummyMO(1)));
		    assertEquals(null, test.remove(1));
		    assertEquals(null, test.remove(new DummyMO(2)));
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
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[50];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }
		    
		    assertEquals(control, test);
		    test.clear();
		    control.clear();

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testMultipleClearOperations() throws Exception {
	final Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    test.clear();
		    assertEquals(control, test);

		    dataService.setBinding("test", test);
		    }
	    }, taskOwner);

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			uncheckedCast(dataService.getBinding("test"));
		    // add just a few elements
		    for (int i = 0; i < 33; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
		    }

		    test.clear();
		    assertEquals(control, test);
		}
	    }, taskOwner);

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			uncheckedCast(dataService.getBinding("test"));

		    // add just enough elements to force a split
		    for (int i = 0; i < 1024; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
		    }

		    test.clear();
		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    /*
     * Miscellaneous tests
     */

    @Test public void testPutAndGetOnSingleLeaf() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int count = 0; count < 64; ++count) {
			int i = RANDOM.nextInt();
			test.put(i, i);
			test.put(~i, ~i);
			control.put(i,i);
			control.put(~i, ~i);
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveSingleLeaf() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int i = 0; i < 54; i++) {
			test.put(i, i);
			test.put(~i, ~i);
			control.put(i, i);
			control.put(~i, ~i);
		    }

		    for (int i = 0; i < 54; i += 2) {
			test.remove(i);
			control.remove(i);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveLopsidedPositiveKeys() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int i = 0; i < 128; i++) {
			test.put(i, i);
			control.put(i, i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 128; i += 2) {
			test.remove(i);
			control.remove(i);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveLopsidedNegativeKeys() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int i = 0; i < 128; i++) {
			test.put(-i, i);
			control.put(-i, i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 128; i += 2) {
			test.remove(-i);
			control.remove(-i);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveDoublyLopsided() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int i = 0; i < 96; i++) {
			test.put(i, i);
			test.put(-i, -i);
			control.put(i, i);
			control.put(-i, -i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 127; i += 2) {
			assertEquals(control.remove(i), test.remove(i));
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveHalfRandomKeys() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>();

		    int[] vals = new int[128];

		    for (int i = 0; i < 128; i++) {
			int j = (i < 64) ? -RANDOM.nextInt() : RANDOM.nextInt();
			vals[i] = j;
			test.put(j, i);
			control.put(j, i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 128; i += 2) {
			test.remove(vals[i]);
			control.remove(vals[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveHalfNegativeKeys() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>();

		    for (int i = 0; i < 128; i++) {
			test.put(-i, -i);
			control.put(-i, -i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 128; i += 2) {
			test.remove(-i);
			control.remove(-i);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveOnSplitTree0() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[12];

		    for (int i = 0; i < 12; i++) {
			int j = RANDOM.nextInt();
			a[i] = j;
			test.put(j, i);
			control.put(j, i);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < 12; i += 2) {
			test.remove(a[i]);
			control.remove(a[i]);
		    }

		    for (int i = 0; i < 6; i += 2) {
			test.get(a[i]);
		    }

		    for (int i = 1; i < 6; i += 2) {
			test.get(a[i]);
		    }

		    assertEquals(control, test);

		    for (Integer k : control.keySet()) {
			assertTrue(test.containsKey(k));
			assertTrue(test.containsValue(control.get(k)));
		    }

		    for (Integer k : test.keySet()) {
			assertTrue(control.containsKey(k));
			assertTrue(control.containsValue(test.get(k)));
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int i = 0; i < 24; i++) {
			test.put(i, i);
			test.put(~i, ~i);
			control.put(i,i);
			control.put(~i, ~i);
		    }

		    for (int i = 0; i < 24; i += 2) {
			test.remove(i);
			control.remove(i);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveOnNoMergeTreeWithNoCollapse()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			createScalableLinkedHashMap(Integer.class, Integer.class,
					      1, 8, 2);
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[1024];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }

		    for (int i = 0; i < inputs.length; i += 2) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveOnNoMergeTreeWithCollapse()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			createScalableLinkedHashMap(Integer.class, Integer.class,
					      1, 8, 4);
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[1024];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }

		    for (int i = 0; i < inputs.length; i += 2) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testRepeatedPutAndRemove() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[400];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 4) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 3) {
			test.put(inputs[i],inputs[i]);
			control.put(inputs[i],inputs[i]);
		    }
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 2) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testRepeatedPutAndRemoveWithNoMergeAndNoCollapse()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			createScalableLinkedHashMap(Integer.class, Integer.class,
					      1,32, 2);
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[1024];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }

		    checkEqualHashObj(control, test);
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 4) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    checkEqualHashObj(control, test);
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 3) {
			test.put(inputs[i],inputs[i]);
			control.put(inputs[i],inputs[i]);
		    }

		    checkEqualHashObj(control, test);
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 2) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testRepeatedPutAndRemoveWithNoMergeAndCollapse()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			createScalableLinkedHashMap(Integer.class, Integer.class,
					      1,32,4);
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[400];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }

		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 4) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 3) {
			test.put(inputs[i],inputs[i]);
			control.put(inputs[i],inputs[i]);
		    }
		    assertEquals(control, test);


		    for (int i = 0; i < inputs.length; i += 2) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testInvalidGet() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    // put in numbers
		    for (int i = 4000; i < 4100; i++) {
			test.put(i, i);
		    }

		    // get from outside the range of the put
		    for (int i = 0; i < 100; i++) {
			assertEquals(null,test.get(i));
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test size
     */

    @Test public void testSize() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    assertEquals(0, test.size());
		    assertTrue(test.isEmpty());

		    for (int i = 0; i < 128; i++) {
			test.put(i, i);
		    }

		    assertEquals(128, test.size());
		    assertFalse(test.isEmpty());

		    // remove the evens
		    for (int i = 0; i < 128; i += 2) {
			test.remove(i);
		    }

		    assertEquals(64, test.size());
		    assertFalse(test.isEmpty());

		    // remove the odds
		    for (int i = 1; i < 128; i += 2) {
			test.remove(i);
		    }

		    assertEquals(0, test.size());
		    assertTrue(test.isEmpty());
		}
	    }, taskOwner);
    }

    /*
     * Test isEmpty
     */

    @Test public void testIsEmpty() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    assertTrue(test.isEmpty());

		    test.put(0, 0);

		    assertFalse(test.isEmpty());

		    test.remove(0);

		    assertTrue(test.isEmpty());

		}
	    }, taskOwner);
    }


     /*
      * Test iterators
      */

    @Test public void testIteratorRemove() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,DummySerializable> test = new ScalableLinkedHashMap<Integer,DummySerializable>();
		    Set<Integer> keys = test.keySet();
		    Iterator<Integer> keysIter = keys.iterator();
		    try {
			keysIter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    try {
			keysIter.next();
			fail("Expected NoSuchElementException");
		    } catch (NoSuchElementException e) {
		    }
		    try {
			keysIter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    test.put(1, new DummySerializable(1));
		    test.put(2, new DummySerializable(2));
		    keysIter = keys.iterator();
		    assertEquals(new Integer(1), keysIter.next());
		    keysIter.remove();
		    assertEquals(1, test.size());
		    assertTrue(test.containsKey(2));
		    try {
			keysIter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    assertEquals(new Integer(2), keysIter.next());
		    keysIter.remove();
		    assertTrue(test.isEmpty());
		    try {
			keysIter.remove();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
		    }
		    assertIteratorDone(keysIter);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testMultipleIterators() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap test = new ScalableLinkedHashMap();
		    dataService.setBinding("test", test);
		    DummyMO bar = new DummyMO(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new DummyMO(2));
		}
	    }, taskOwner);
	for (int i = 0; i < 2; i++) {
	    final int local = i;
	    txnScheduler.runTask(
	        new AbstractKernelRunnable() {
		    public void run() throws Exception {
			ScalableLinkedHashMap test =
			    (ScalableLinkedHashMap) dataService.getBinding("test");
			dataService.setBinding("valuesIter",
			    test.values().iterator());
		    }
		}, taskOwner);
	    txnScheduler.runTask(
	        new AbstractKernelRunnable() {
		    public void run() throws Exception {
			if (local == 0) {
			    dataService.removeObject(
				dataService.getBinding("bar"));
			}
			Iterator valuesIter = uncheckedCast(
			    dataService.getBinding("valuesIter"));
			dataService.markForUpdate(valuesIter);
			int count = 0;
			while (valuesIter.hasNext()) {
			    count++;
			    try {
				assertEquals(new DummyMO(2), valuesIter.next());
			    } catch (ObjectNotFoundException e) {
			    }
			}
			assertEquals(2, count);			    
		    }
		}, taskOwner);
	}
    }

    @Test public void testKeyIterator() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Set<Integer> control = new HashSet<Integer>();

		    // get from outside the range of the put
		    for (int i = 0; i < 100; i++) {
			test.put(i,i);
			control.add(i);
		    }

		    for (Integer i : test.keySet()) {
			control.remove(i);
		    }

		    assertEquals(0, control.size());
		}
	    }, taskOwner);
    }

    @Test public void testValuesIterator() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Set<Integer> control = new HashSet<Integer>();

		    // get from outside the range of the put
		    for (int i = 0; i < 100; i++) {
			test.put(i,i);
			control.add(i);
		    }

		    for (Integer i : test.values()) {
			control.remove(i);
		    }

		    assertEquals(0, control.size());
		}
	    }, taskOwner);
    }

    @Test public void testInvalidRemove() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    // put in numbers
		    for (int i = 4000; i < 4100; i++) {
			test.put(i, i);
		    }

		    // get from outside the range of the put
		    for (int i = 0; i < 100; i++) {
			assertEquals(null, test.remove(i));
		    }
		}
	    }, taskOwner);
    }

    /*
     * Serializiable / ManagedObject kev-value tests
     */

    @Test public void testOnManagedObjectKeys() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<DummyMO,DummySerializable> test = new ScalableLinkedHashMap<DummyMO,DummySerializable>();
		    Map<DummyMO,DummySerializable> control = new HashMap<DummyMO,DummySerializable>();

		    for (int i = 0; i < 64; i++) {
			test.put(new DummyMO(i), new DummySerializable(i));
			control.put(new DummyMO(i), new DummySerializable(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testOnManagedObjectValues() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<DummySerializable,DummyMO> test = new ScalableLinkedHashMap<DummySerializable,DummyMO>();
		    Map<DummySerializable,DummyMO> control = new HashMap<DummySerializable,DummyMO>();

		    for (int i = 0; i < 64; i++) {
			test.put(new DummySerializable(i), new DummyMO(i));
			control.put(new DummySerializable(i), new DummyMO(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testOnManagedObjectKeysAndValues() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<DummyMO,DummyMO> test = new ScalableLinkedHashMap<DummyMO,DummyMO>();
		    Map<DummyMO,DummyMO> control = new HashMap<DummyMO,DummyMO>();

		    for (int i = 0; i < 64; i++) {
			test.put(new DummyMO(i), new DummyMO(i));
			control.put(new DummyMO(i), new DummyMO(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testSerializableKeysReplacedWithManagedObjects()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<DummySerializable,DummySerializable> test = new ScalableLinkedHashMap<DummySerializable,DummySerializable>();
		    Map<DummySerializable,DummySerializable> control = new HashMap<DummySerializable,DummySerializable>();

		    for (int i = 0; i < 64; i++) {
			test.put(new DummySerializable(i), new DummySerializable(i));
			control.put(new DummySerializable(i), new DummySerializable(i));
			assertEquals(control, test);
		    }

		    for (int i = 0; i < 64; i++) {
			test.put(new DummyMO(i), new DummySerializable(i));
			control.put(new DummyMO(i), new DummySerializable(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);	    
    }

    @Test public void testSerializableValuesReplacedWithManagedObjects()
	throws Exception
    {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<DummySerializable,DummySerializable> test = new ScalableLinkedHashMap<DummySerializable,DummySerializable>();
		    Map<DummySerializable,DummySerializable> control = new HashMap<DummySerializable,DummySerializable>();

		    for (int i = 0; i < 64; i++) {
			test.put(new DummySerializable(i), new DummySerializable(i));
			control.put(new DummySerializable(i), new DummySerializable(i));
			assertEquals(control, test);
		    }

		    for (int i = 0; i < 64; i++) {
			test.put(new DummySerializable(i), new DummyMO(i));
			control.put(new DummySerializable(i), new DummyMO(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    /*
     * Ordered Iterator tests
     *
     * These tests should expose any problems when the
     * ScalableLinkedHashMap.OrderedIterator class is serialized and
     * modifications are made to the map before it is deserialized.  This should
     * simulate the conditions between transactions where the map might be
     * modified
     */

    @SuppressWarnings("unchecked")
    @Test public void testOrderedIterator() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[128];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    Set<Map.Entry<Integer,Integer>> entrySet =
			control.entrySet();
		    int entries = 0;

		    for (Iterator<Map.Entry<Integer,Integer>> it =
			     test.entrySet().iterator();
			 it.hasNext(); ) {

			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }
		    assertEquals(entrySet.size(), entries);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testOrderedIteratorSerialization() throws Exception {

	final LinkedHashMap<Integer,Integer> control =
	    new LinkedHashMap<Integer,Integer>();

	final int[] a = new int[128];

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();



		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }
		    
		    Iterator<Map.Entry<Integer,Integer>> controlIter =
			control.entrySet().iterator();		   

		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(test.entrySet().iterator());

		    for (int i = 0; i < a.length / 2; ++i) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    AppContext.getDataManager().setBinding("test", testIter);
		}
	    }, taskOwner);

	// then iterate over the second half of the map

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();
		    
		    Iterator<Map.Entry<Integer,Integer>> controlIter =
			control.entrySet().iterator();		   

		    // jump to half way for the control
		    for (int i = 0; i < a.length / 2; ++i) {
			controlIter.next();
		    }

		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(AppContext.getDataManager().getBinding("test"));

		    for (int i = a.length / 2; i < a.length; ++i) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    AppContext.getDataManager().removeBinding("test");
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testOrderedIteratorSerializationWithRemovals() throws Exception {

	final LinkedHashMap<Integer,Integer> control =
	    new LinkedHashMap<Integer,Integer>();

	final int[] a = new int[128];

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }
		    
		    Iterator<Map.Entry<Integer,Integer>> controlIter =
			control.entrySet().iterator();		   

		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(test.entrySet().iterator());

		    for (int i = 0; i < a.length / 4; ++i) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    // remove the middle 1/2 of the map
		    for (int i = a.length / 4; i < (3 * a.length) / 4; ++i) {
			control.remove(a[i]);
			test.remove(a[i]);
		    }

		    
		    AppContext.getDataManager().setBinding("test", testIter);
		}
	    }, taskOwner);

	// then iterate over the second half of the map

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    Iterator<Map.Entry<Integer,Integer>> controlIter =
			control.entrySet().iterator();		   

		    // jump past the element we iterated over in the previous task
		    for (int i = 0; i < a.length / 4; ++i) {
			controlIter.next();
		    }

		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(AppContext.getDataManager().getBinding("test"));

		    // check the remaining elements
		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(controlIter.hasNext());
		    assertFalse(testIter.hasNext());

		    AppContext.getDataManager().removeBinding("test");
		}
	    }, taskOwner);

    }


    @SuppressWarnings("unchecked")
    @Test public void testOrderedIteratorSerializationWithClear() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    ScalableLinkedHashMap<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    int[] a = new int[128];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			a[i] = j;
		    }
		    
		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(test.entrySet().iterator());

		    AppContext.getDataManager().setBinding("test", testIter);
		    test.clear();
		}
	    }, taskOwner);

	// then iterate over the second half of the map

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableLinkedHashMap.EntryIterator<Integer,Integer> testIter =
			uncheckedCast(AppContext.getDataManager().getBinding("test"));

		    assertFalse(testIter.hasNext());

		    AppContext.getDataManager().removeBinding("test");
		}
	    }, taskOwner);
    }

    /*
     * Iterator tests where concurrent updates are not supported
     */


    @Test public void testConcurrentIteratorSeriazableWithRemovalOfNextElements() 
	throws Exception {

	final String name = "test-iterator";
	final String name2 = "test-map";

	// create the map
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableLinkedHashMap<Integer,Integer> d = 
			new ScalableLinkedHashMap<Integer,Integer>(false, false);
		    for (int i = 0; i < 10; ++i) 
			d.put(i,i);
		    AppContext.getDataManager().setBinding(name, d.keySet().iterator());
		    AppContext.getDataManager().setBinding(name2, d);
		}
	    }, taskOwner);

	// remove the iterator's first 5 elements while the iterator is
	// serialized
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    ScalableLinkedHashMap<Integer,Integer> m = 
			uncheckedCast(AppContext.getDataManager().getBinding(name2));
		    for (int i = 0; i < 5; i++) 
			m.remove(i);	    
		    System.out.println("map: " + m);
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
			System.out.println("next: " + it.next());
			fail("expected ConcurrentModificationException");
		    } 
		    catch (ConcurrentModificationException cme) {
			// expected
		    }
		    finally {
			AppContext.getDataManager().removeBinding(name);
			AppContext.getDataManager().removeBinding(name2);
		    }
		}
	    }, taskOwner);
    }

    /*
     * Insertion order tests
     */
    @Test public void testInsertionOrder() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    
		    int[] arr = new int[32];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			arr[i]= j;
		    }

		    // now test the iterator
		    Iterator<Integer> it = test.keySet().iterator();
		    int i = 0;
		    while (it.hasNext()) {
			Integer inMap = it.next();
			Integer inOrder = arr[i];
			assertEquals(inMap, inOrder);
			i++;
		    }
		    
		    assertEquals(i, arr.length);
		}
	    }, taskOwner);
    }


    @Test public void testInsertionOrderWithIntermediateRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			arr[i]= j;
		    }

		    // remove middle elements
		    for (int i = arr.length / 3; i < (2*arr.length) / 3; ++i) {
			test.remove(arr[i]);

			// shift the array down a third
			arr[i] = arr[i + (arr.length / 3)];
		    }
			

		    // now test the iterator
		    Iterator<Integer> it = test.keySet().iterator();
		    int i = 0;
		    while (it.hasNext()) {
			Integer inMap = it.next();
			Integer inOrder = arr[i];
			assertEquals(inMap, inOrder);
			i++;
		    }
		    
		    assertEquals(i, test.size());
		}
	    }, taskOwner);
    }


    @Test public void testInsertionOrderWithFrontRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			arr[i]= j;
		    }

		    // remove first elements
		    for (int i = 0; i < arr.length / 3; ++i) {
			test.remove(arr[i]);
		    }
			

		    // now test the iterator
		    Iterator<Integer> it = test.keySet().iterator();
		    int i = 0; 
		    int j = arr.length / 3;
		    while (it.hasNext()) {
			Integer inMap = it.next();
			Integer inOrder = arr[j];
			assertEquals(inMap, inOrder);
			i++;
			j++;
		    }
		    
		    assertEquals(i, test.size());
		}
	    }, taskOwner);
    }



    @Test public void testInsertionOrderWithEndRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>();

		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			arr[i]= j;
		    }

		    // remove first elements
		    for (int i = (2 * arr.length) / 3; i < arr.length; ++i) {
			test.remove(arr[i]);
		    }
			

		    // now test the iterator
		    Iterator<Integer> it = test.keySet().iterator();
		    int i = 0; 
		    while (it.hasNext()) {
			Integer inMap = it.next();
			Integer inOrder = arr[i];
			assertEquals(inMap, inOrder);
			i++;
		    }
		    
		    assertEquals(i, test.size());
		}
	    }, taskOwner);
    }     


    /*
     * Access order tests
     */
    @Test public void testAccessOrder() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    int[] arr = new int[32];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[i] = j;
		    }

		    // now test the iterator
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }


    @Test public void testAccessOrderWithIntermediateRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[(arr.length - 1) - i] = j;
		    }

		    // remove middle elements
		    for (int i = arr.length / 3; i < (2*arr.length) / 3; ++i) {
			test.remove(arr[i]);
			control.remove(arr[i]);
		    }
			
		    // now test the iterator order
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }


    @Test public void testAccessOrderWithFrontRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[i] = j;
		    }

		    // remove first elements
		    for (int i = 0; i < arr.length / 3; ++i) {
			test.remove(arr[i]);
			control.remove(arr[i]);
		    }
			

		    // now test the iterator order
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }



    @Test public void testAccessOrderWithEndRemovals() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[i] = j;
		    }

		    // remove last third of the elements
		    for (int i = (2 * arr.length) / 3; i < arr.length; ++i) {
			test.remove(arr[i]);
			control.remove(arr[i]);
		    }
			

		    // now test the iterator order
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }     

    @Test public void testAccessOrderWithMiddleAccesses() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    int[] arr = new int[9];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[(arr.length - 1) - i] = j;
		    }

		    // touch middle elements
		    for (int i = (2 * arr.length) / 3; i < arr.length; ++i) {
			test.get(arr[i]);
			control.get(arr[i]);
		    }
			

		    // now test the iterator order
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }     


    @Test public void testAccessOrderWithEndAccesses() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    Map<Integer,Integer> test =
			new ScalableLinkedHashMap<Integer,Integer>(true);

		    LinkedHashMap<Integer,Integer> control =
			new LinkedHashMap<Integer,Integer>(16, .75f, true);
		    
		    int[] arr = new int[36];
		    
		    // insert into the map, keeping track of the order
		    for (int i = 0; i < arr.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.put(j,j);
			arr[(arr.length - 1) - i] = j;
		    }

		    // remove first elements
		    for (int i = (2 * arr.length) / 3; i < arr.length; ++i) {
			test.get(arr[i]);
			control.get(arr[i]);
		    }
			
		    // now test the iterator order
		    Iterator<Integer> testIter = test.keySet().iterator();
		    Iterator<Integer> controlIter = control.keySet().iterator();

		    while (controlIter.hasNext()) {
			assertEquals(controlIter.next(), testIter.next());
		    }
		    
		    assertFalse(testIter.hasNext());
		}
	    }, taskOwner);
    }     


    /*
     * Utility routines.
     */

    public boolean checkEqualHashObj(Map<Integer,Integer> m1,
			       Map<Integer,Integer> m2) {

	if (m1.size() != m2.size()) {
	    System.out.printf("sizes not equal: %d != %d\n",
			      m1.size(), m2.size());
	    return false;
	}

	Iterator<Entry<Integer,Integer>> i = m1.entrySet().iterator();
	while (i.hasNext()) {
	    Entry<Integer,Integer> e = i.next();
	    Integer key = e.getKey();
	    Integer value = e.getValue();
	    if (value == null) {
		if (!(m2.get(key)==null && m2.containsKey(key))) {
		    System.out.printf("keys not equal, m2 has key: %s? %s\n",
				      key, m2.containsKey(key));
		    return false;
		}
	    } else {
		if (!value.equals(m2.get(key))) {
		    System.out.printf("m1.get(%s) not equal: %s: %s\n",
				      key, value, m2.get(key));
		    System.out.println("m2.containsKey() ? " +
				       m2.containsKey(key));
		    return false;
		}
	    }
	}

	return true;
    }

    /**
     * Constructs an empty {@code ScalableLinkedHashMap} by calling the private
     * constructor to supply additional parameters.
     */
    @SuppressWarnings("unchecked")
    private static <K,V> ScalableLinkedHashMap<K,V> createScalableLinkedHashMap(
	Class<K> keyClass, Class<V> valueClass,
	int minConcurrency, int splitThreshold, int directorySize)
    {
	try {
	    return new ScalableLinkedHashMap<K,V>();
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }

    /** Checks that the iterator has no more entries. */
    private static void assertIteratorDone(Iterator<?> iterator) {
	assertFalse(iterator.hasNext());
	try {
	    iterator.next();
	    fail("Expected NoSuchElementException");
	} catch (NoSuchElementException e) {
	}
    }

    /**
     * Checks that the iterator returns objects equal to the contents of the
     * collection.
     */
    private static void assertIteratorContains(
	Collection<?> contents, Iterator<?> iterator)
    {
	Set<?> set = new HashSet<Object>(contents);
	while (iterator.hasNext()) {
	    assertTrue(set.remove(iterator.next()));
	}
	assertTrue(set.isEmpty());
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
        return new JUnit4TestAdapter(TestScalableLinkedHashMap.class);
    }
}
