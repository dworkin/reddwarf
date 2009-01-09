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

package com.sun.sgs.test.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import com.sun.sgs.impl.service.channel.BindingKeyedHashMap;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
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
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link BindingKeyedHashMap} class.
 */
public class TestBindingKeyedHashMap extends TestCase {

    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    /** A fixed random number generator for use in the test. */
    private static final Random RANDOM = new Random(1337);

    /**
     * Test management.
     */

   protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	serverNode = new SgsTestNode("TestBindingKeyedHashMap", null,
				     createProps("TestBindingKeyedHashMap"));
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    protected void tearDown() throws Exception {
	serverNode.shutdown(true);
    }

    /*
     * Test putAll
     */

    @Test public void testPutAllMisc() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();
		    for (int i = 0; i < 32; i++) {
			control.put(i,i);
		    }
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
		    test.putAll(control);
		    assertEquals(control, test);
		}
	    }, taskOwner);
     }

    @Test public void testPutAllNullArg() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new BindingKeyedHashMap<Object,Object>();
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

    @Test public void testPutAllNullKeys() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new BindingKeyedHashMap<Object,Object>();
		    Map<Object,Object> other = new HashMap<Object,Object>();
		    other.put(null, Boolean.TRUE);
		    try {
			test.putAll(other);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		    other.clear();
		    other.put(Boolean.TRUE, null);
		    try {
			test.putAll(other);
			fail("Expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test put
     */

    @Test public void testPutMisc() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
		    Foo result = test.put(1, new Foo(1));
		    assertEquals(null, result);
		    result = test.put(1, new Foo(1));
		    assertEquals(new Foo(1), result);
		    result = test.put(1, new Foo(37));
		    assertEquals(new Foo(1), result);
		}
	    }, taskOwner);
    }

    @Test public void testPutNotSerializable() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Object,Object> test =
			new BindingKeyedHashMap<Object,Object>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(Boolean.TRUE, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    try {
			test.put(Boolean.TRUE, Boolean.FALSE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, Boolean.TRUE);
		}
	    }, taskOwner);
	try {
	    txnScheduler.runTask(
	        new TestAbstractKernelRunnable() {
		    public void run() throws Exception {
			dataService.removeObject(
			    dataService.getBinding("bar"));
			BindingKeyedHashMap test =
			    (BindingKeyedHashMap) dataService.getBinding("test");
			assertEquals(null, test.put(new Bar(1), Boolean.FALSE));
			assertEquals(Boolean.FALSE, test.get(new Bar(1)));
			throw new RuntimeException("Intentional Abort");
		    }
		}, taskOwner);
	} catch (RuntimeException re) {}
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.put(new Bar(1), Boolean.FALSE));
		    assertEquals(Boolean.FALSE, test.get(new Bar(1)));
		}
	    }, taskOwner);
    }

    @Test public void testPutNullKey() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<String,Integer> test =
			new BindingKeyedHashMap<String,Integer>();
		    try {
			test.put(null, 0);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    @Test public void testPutNullValue() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,String> test =
			new BindingKeyedHashMap<Integer,String>();
		    try {
			test.put(0, null);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test get
     */

    @Test public void testGetMisc() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
		    assertEquals(null, test.get(1));
		    test.put(1, new Foo(1));
		    assertEquals(new Foo(1), test.get(1));
		    assertEquals(null, test.get(new Foo(1)));
		    assertEquals(null, test.get(2));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testGetValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(Boolean.TRUE, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    try {
			test.get(Boolean.TRUE);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
			assertEquals(1, test.size());
		    }
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, Boolean.TRUE);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.get(new Bar(1)));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.get(new Bar(1)));
		}
	    }, taskOwner);
    }

    @Test public void testGetNullKey() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<String,Integer> test =
			new BindingKeyedHashMap<String,Integer>();
		    try {
			test.get(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test containsKey
     */

    @Test public void testContainsKeyMisc() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
		    assertFalse(test.containsKey(1));
		    test.put(1, new Foo(1));
		    assertTrue(test.containsKey(1));
		    assertFalse(test.containsKey(new Foo(1)));
		    assertFalse(test.containsKey(2));
		}
	    }, taskOwner);
    }

    @Test public void testContainsKeyNull() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<String,Integer> test =
			new BindingKeyedHashMap<String,Integer>();
		    try {
			test.containsKey(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsKeyKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		    test.put(new Bar(2), 2);
		    }
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsKey(new Bar(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsKey(new Bar(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsKeyValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new Bar(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsKey(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsKey(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @Test public void testContainsKeyOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[50];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,-j);
		    }

		    for (int i = 0; i < inputs.length; i++) {
			assertTrue(test.containsKey(inputs[i]));
		    }
		}
	    }, taskOwner);
    }

    /*
     * Test containsValue
     */

    @Test public void testContainsValueMisc() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
		    assertFalse(test.containsValue(new Foo(1)));
		    test.put(1, new Foo(1));
		    assertTrue(test.containsValue(new Foo(1)));
		    assertFalse(test.containsValue(1));
		    assertFalse(test.containsValue(new Foo(2)));
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueNull() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
		    try {
			test.containsValue(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }


    @SuppressWarnings("unchecked")
    @Test public void testContainsValueValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new Bar(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsValue(new Bar(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertFalse(test.containsValue(new Bar(1)));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueKeyNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		    test.put(new Bar(2), 2);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsValue(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertTrue(test.containsValue(1));
		    assertEquals(2, test.size());
		}
	    }, taskOwner);
    }

    @Test public void testContainsValue() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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

    @Test public void testContainsValueOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
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

    @Test public void testValuesOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Collection<Integer> control = new ArrayList<Integer>(50);

		    int[] inputs = new int[50];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,-j);
			control.add(-j);
		    }

		    assertTrue(control.containsAll(test.values()));
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map test = new BindingKeyedHashMap();
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
	    new TestAbstractKernelRunnable() {
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map test = new BindingKeyedHashMap();
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
	    new TestAbstractKernelRunnable() {
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
    @Test public void testEquals() throws Exception {
	final Map control = new HashMap();
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    assertFalse(test.equals(null));
		    assertFalse(test.equals(1));
		    assertTrue(control.equals(test));
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
		    assertEquals(null, test.remove(1));
		    test.put(1, new Foo(1));
		    assertEquals(null, test.remove(2));
		    assertEquals(null, test.remove(new Foo(1)));
		    assertEquals(new Foo(1), test.remove(1));
		    assertTrue(test.isEmpty());
		    assertEquals(null, test.remove(1));
		}
	    }, taskOwner);
    }

    @Test public void testRemoveNullKey() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<String,Integer> test =
			new BindingKeyedHashMap<String,Integer>();
		    try {
			test.remove(null);
			fail("expected NullPointerException");
		    } catch (NullPointerException e) {
		    }
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testRemoveValueNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    try {
			test.remove(1);
			fail("Expected ObjectNotFoundException");
		    } catch (ObjectNotFoundException e) {
		    }
		    assertEquals(null, test.remove(2));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(bar, 1);
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.removeObject(dataService.getBinding("bar"));
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.remove(new Bar(1)));
		    assertEquals(null, test.remove(1));
		    assertEquals(null, test.remove(new Bar(2)));
		}
	    }, taskOwner);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test =
			(BindingKeyedHashMap) dataService.getBinding("test");
		    assertEquals(null, test.remove(new Bar(1)));
		    assertEquals(null, test.remove(1));
		    assertEquals(null, test.remove(new Bar(2)));
		}
	    }, taskOwner);
    }

    /*
     * Test clear
     */

    @Test public void testClear() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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

		    /*
		     * XXX: Test that clear does not change the minimum depth.
		     * -tjb@sun.com (10/04/2007)
		     */

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testMultipleClearOperations() throws Exception {
	final Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
		    test.clear();
		    assertEquals(control, test);

		    dataService.setBinding("test", test);
		    }
	    }, taskOwner);

	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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

    @Test public void testPutAndGetOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    for (int count = 0; count < 32; ++count) {
			int i = RANDOM.nextInt();
			test.put(i, i);
			control.put(i,i);
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testPutAndRemoveSingleLeaf() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[1024];

		    for (int i = 0; i < inputs.length; i++) {
			int j = RANDOM.nextInt();
			inputs[i] = j;
			test.put(j,j);
			control.put(j,j);
		    }

		    checkEquals(control, test);
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 4) {
			test.remove(inputs[i]);
			control.remove(inputs[i]);
		    }

		    checkEquals(control, test);
		    assertEquals(control, test);

		    for (int i = 0; i < inputs.length; i += 3) {
			test.put(inputs[i],inputs[i]);
			control.put(inputs[i],inputs[i]);
		    }

		    checkEquals(control, test);
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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

    @Test public void testPutAndRemoveOnSplitTree5() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] inputs = new int[50];

		    for (int i = 0; i < inputs.length; i++)
			inputs[i] = RANDOM.nextInt();

		    for (int i = 0; i < inputs.length; i++)	{
			int j = RANDOM.nextInt(inputs.length);
			test.put(inputs[j], inputs[j]);
			control.put(inputs[j], inputs[j]);
			assertEquals(control, test);

			int k = RANDOM.nextInt(inputs.length);
			test.remove(inputs[k]);
			control.remove(inputs[k]);
			assertEquals(control, test);

			int m = RANDOM.nextInt(inputs.length);
			test.put(inputs[m], inputs[m]);
			control.put(inputs[m], inputs[m]);
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testInvalidGet() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();

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

    @Test public void testLeafSize() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();

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

    @Test public void testLeafSizeAfterRemove() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();

		    int SAMPLE_SIZE = 10;

		    int[] inputs1 = new int[SAMPLE_SIZE];
		    int[] inputs2 = new int[SAMPLE_SIZE];
		    int[] inputs3 = new int[SAMPLE_SIZE];

		    for (int i = 0; i < inputs1.length; i++) {
			inputs1[i] = RANDOM.nextInt();
			inputs2[i] = RANDOM.nextInt();
			inputs3[i] = RANDOM.nextInt();
		    }

		    for (int i = 0; i < inputs1.length; i++) {
			test.put(inputs1[i], inputs1[i]);
			test.put(inputs2[i], inputs2[i]);
			assertEquals(test.size(), (i+1)*2);
		    }

		    for (int i = 0; i < inputs1.length; i++) {
			int beforeSize = test.size();
			test.put(inputs3[i], inputs3[i]);
			test.remove(inputs2[i]);
			assertEquals(beforeSize, test.size());
		    }
		}
	    }, taskOwner);
    }

    @Test public void testTreeSizeOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    // create a tree with an artificially small leaf size
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();

		    assertEquals(0, test.size());

		    for (int i = 0; i < 5; i++) {
			test.put(i, i);
		    }

		    assertEquals(5, test.size());

		    for (int i = 5; i < 15; i++) {
			test.put(i,i);
		    }

		    assertEquals(15, test.size());

		    for (int i = 15; i < 31; i++) {
			test.put(i,i);
		    }

		    assertEquals(31, test.size());
		}
	    }, taskOwner);
    }

    @Test public void testTreeSizeOnSplitTreeWithRemovals() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    // create a tree with an artificially small leaf size
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();

		    assertEquals(0, test.size());

		    int[] inserts = new int[128];
		    for (int i = 0; i < inserts.length; i++) {
			inserts[i] = RANDOM.nextInt();
		    }

		    // add 32
		    for (int i = 0; i < 32; i++) {
			test.put(inserts[i], inserts[i]);
		    }

		    assertEquals(32, test.size());

		    // remove 10
		    for (int i = 0; i < 10; i++) {
			test.remove(inserts[i]);
		    }

		    assertEquals(22, test.size());

		    // add 32
		    for (int i = 32; i < 64; i++) {
			test.put(inserts[i],inserts[i]);
		    }

		    assertEquals(54, test.size());

		    // remove 10
		    for (int i = 32; i < 42; i++) {
			test.remove(inserts[i]);
		    }

		    // add 64
		    for (int i = 64; i < 128; i++) {
			test.put(inserts[i],inserts[i]);
		    }

		    assertEquals(108, test.size());

		    // remove 5
		    for (int i = 64; i < 69; i++) {
			test.remove(inserts[i]);
		    }
		    assertEquals(103, test.size());
		}
	    }, taskOwner);
    }

    /*
     * Test iterators
     */

    @Test public void testIteratorRemove() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Foo> test =
			new BindingKeyedHashMap<Integer,Foo>();
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
		    test.put(1, new Foo(1));
		    test.put(2, new Foo(2));
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
    @Test public void testIteratorNotFound() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap test = new BindingKeyedHashMap();
		    dataService.setBinding("test", test);
		    Bar bar = new Bar(1);
		    dataService.setBinding("bar", bar);
		    test.put(1, bar);
		    test.put(2, new Bar(2));
		}
	    }, taskOwner);
	for (int i = 0; i < 2; i++) {
	    final int local = i;
	    txnScheduler.runTask(
	        new TestAbstractKernelRunnable() {
		    public void run() throws Exception {
			BindingKeyedHashMap test =
			    (BindingKeyedHashMap) dataService.getBinding("test");
			dataService.setBinding("valuesIter",
			    new ManagedSerializable(test.values().iterator()));
		    }
		}, taskOwner);
	    txnScheduler.runTask(
	        new TestAbstractKernelRunnable() {
		    public void run() throws Exception {
			if (local == 0) {
			    dataService.removeObject(
				dataService.getBinding("bar"));
			}
			ManagedSerializable<Iterator> ms = uncheckedCast(
			    dataService.getBinding("valuesIter"));
			dataService.markForUpdate(ms);
			Iterator valuesIter = ms.get();
			int count = 0;
			while (valuesIter.hasNext()) {
			    count++;
			    try {
				assertEquals(new Bar(2), valuesIter.next());
			    } catch (ObjectNotFoundException e) {
			    }
			}
			assertEquals(2, count);
		    }
		}, taskOwner);
	}
    }

    @Test public void testIteratorOnSplitTree() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Set<Integer> control = new HashSet<Integer>();

		    // get from outside the range of the put
		    for (int i = 0; i < 33; i++) {
			int j = RANDOM.nextInt();
			test.put(j,j);
			control.add(j);
		    }

		    for (Integer i : test.keySet()) {
			control.remove(i);
		    }

		    assertEquals(0, control.size());
		}
	    }, taskOwner);
    }

    @Test public void testIteratorOnSplitTreeWithRemovals() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    // create a tree with an artificially small leaf size
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    HashMap<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    assertEquals(0, test.size());

		    int[] inserts = new int[128];
		    for (int i = 0; i < inserts.length; i++) {
			inserts[i] = RANDOM.nextInt();
		    }

		    // add 32
		    for (int i = 0; i < 32; i++) {
			test.put(inserts[i], inserts[i]);
			control.put(inserts[i], inserts[i]);
		    }

		    assertEquals(control, test);

		    // remove 10
		    for (int i = 0; i < 10; i++) {
			test.remove(inserts[i]);
			control.remove(inserts[i]);
		    }

		    assertEquals(control, test);

		    // add 32
		    for (int i = 32; i < 64; i++) {
			test.put(inserts[i],inserts[i]);
			control.put(inserts[i],inserts[i]);
		    }

		    assertEquals(control, test);

		    // remove 10
		    for (int i = 32; i < 42; i++) {
			test.remove(inserts[i]);
			control.remove(inserts[i]);
		    }

		    assertEquals(control, test);

		    // add 64
		    for (int i = 64; i < 128; i++) {
			test.put(inserts[i],inserts[i]);
			control.put(inserts[i],inserts[i]);
		    }

		    assertEquals(control, test);

		    // remove 5
		    for (int i = 64; i < 69; i++) {
			test.remove(inserts[i]);
			control.remove(inserts[i]);
		    }

		    assertEquals(control, test);
		}
	    }, taskOwner);
    }

    @Test public void testKeyIterator() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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

    @Test public void testKeyIteratorOnSplitMap() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Set<Integer> control = new HashSet<Integer>();

		    // get from outside the range of the put
		    for (int i = 0; i < 33; i++) {
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
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

    @Test public void testValuesIteratorOnSplitMap() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Set<Integer> control = new HashSet<Integer>();

		    // get from outside the range of the put
		    for (int i = 0; i < 33; i++) {
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
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();

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

    @SuppressWarnings("unchecked")
	@Test public void testLeafSerialization() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer, Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[100];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(test);

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    BindingKeyedHashMap<Integer,Integer> m =
			(BindingKeyedHashMap<Integer,Integer>) ois.readObject();

		    assertEquals(control, m);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testSplitTreeSerialization() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[100];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(test);

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    BindingKeyedHashMap<Integer,Integer> m =
			(BindingKeyedHashMap<Integer,Integer>) ois.readObject();

		    assertEquals(control, m);
		}
	    }, taskOwner);
    }

    /*
     * Tests on ManagedObject vs. Serializable object keys
     *
     * These tests should expose any bugs in the BindingKeyedHashMap.PrefixEntry
     * class, especially in the setValue() method.  These should also expose
     * any bugs in the KeyValuePair class
     */

    @Test public void testOnManagedObjectKeys() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Bar,Foo> test = new BindingKeyedHashMap<Bar,Foo>();
		    Map<Bar,Foo> control = new HashMap<Bar,Foo>();

		    for (int i = 0; i < 64; i++) {
			test.put(new Bar(i), new Foo(i));
			control.put(new Bar(i), new Foo(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testOnManagedObjectValues() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Foo,Bar> test = new BindingKeyedHashMap<Foo,Bar>();
		    Map<Foo,Bar> control = new HashMap<Foo,Bar>();

		    for (int i = 0; i < 64; i++) {
			test.put(new Foo(i), new Bar(i));
			control.put(new Foo(i), new Bar(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testOnManagedObjectKeysAndValues() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Bar,Bar> test = new BindingKeyedHashMap<Bar,Bar>();
		    Map<Bar,Bar> control = new HashMap<Bar,Bar>();

		    for (int i = 0; i < 64; i++) {
			test.put(new Bar(i), new Bar(i));
			control.put(new Bar(i), new Bar(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    @Test public void testSerializableKeysReplacedWithManagedObjects()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Foo,Foo> test = new BindingKeyedHashMap<Foo,Foo>();
		    Map<Foo,Foo> control = new HashMap<Foo,Foo>();

		    for (int i = 0; i < 64; i++) {
			test.put(new Foo(i), new Foo(i));
			control.put(new Foo(i), new Foo(i));
			assertEquals(control, test);
		    }
		    for (int i = 0; i < 64; i++) {
			test.put(new Bar(i), new Foo(i));
			control.put(new Bar(i), new Foo(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);	    
    }

    @Test public void testSerializableValuesReplacedWithManagedObjects()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    Map<Foo,Foo> test = new BindingKeyedHashMap<Foo,Foo>();
		    Map<Foo,Foo> control = new HashMap<Foo,Foo>();

		    for (int i = 0; i < 64; i++) {
			test.put(new Foo(i), new Foo(i));
			control.put(new Foo(i), new Foo(i));
			assertEquals(control, test);
		    }

		    for (int i = 0; i < 64; i++) {
			test.put(new Foo(i), new Bar(i));
			control.put(new Foo(i), new Bar(i));
			assertEquals(control, test);
		    }
		}
	    }, taskOwner);
    }

    /*
     * Concurrent Iterator tests
     *
     * These tests should expose any problems when the
     * BindingKeyedHashMap.ConcurrentIterator class is serialized and modifications
     * are made to the map before it is deserialized.  This should simulate the
     * conditions between transactions where the map might be modified
     */

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIterator() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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
    @Test public void testConcurrentIteratorSerialization() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[256];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    Set<Map.Entry<Integer,Integer>> entrySet =
			control.entrySet();
		    int entries = 0;

		    Iterator<Map.Entry<Integer,Integer>> it =
			test.entrySet().iterator();
		    for (int i = 0; i < a.length / 2; i++) {
			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Integer,Integer>>)
			ois.readObject();

		    while(it.hasNext()) {
			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(entrySet.size(), entries);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithRemovals() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    int[] a = new int[1024];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    Set<Map.Entry<Integer,Integer>> entrySet =
			control.entrySet();
		    int entries = 0;

		    Iterator<Map.Entry<Integer,Integer>> it =
			test.entrySet().iterator();

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    // then remove half of the entries
		    for (int i = 0; i < a.length; i += 2) {
			test.remove(a[i]);
			control.remove(a[i]);
		    }

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Integer,Integer>>)
			ois.readObject();

		    // ensure that the deserialized iterator reads the
		    // remaining elements
		    while(it.hasNext()) {
			Map.Entry<Integer,Integer> e = it.next();
			e.getKey();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(entrySet.size(), entries);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithAdditions() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    Map<Integer,Integer> control =
			new HashMap<Integer,Integer>();

		    // immediately get the iterator while the map size is zero
		    Iterator<Map.Entry<Integer,Integer>> it =
			test.entrySet().iterator();

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

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

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Integer,Integer>>)
			ois.readObject();

		    // ensure that the deserialized iterator reads all of
		    // the new elements
		    while(it.hasNext()) {
			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(entrySet.size(), entries);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithReplacements()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
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

		    Iterator<Map.Entry<Integer,Integer>> it =
			test.entrySet().iterator();
		    for (int i = 0; i < a.length / 2; i++) {
			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(a.length / 2, entries);

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    // now replace all the elements in the map
		    test.clear();
		    control.clear();
		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(j, j);
			control.put(j, j);
			a[i] = j;
		    }

		    // reserialize the iterator
		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Integer,Integer>>)
			ois.readObject();

		    while(it.hasNext()) {
			Map.Entry<Integer,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    // due to the random nature of the entries, we can't check
		    // that it read in another half other elements.  However
		    // this should still check that no execptions were thrown.
		}
	    }, taskOwner);
    }

    /*
     * Tests on concurrent iterator edge cases
     */

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorSerializationEqualHashCodes()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Equals,Integer> test =
			new BindingKeyedHashMap<Equals,Integer>();
		    Map<Equals,Integer> control =
			new HashMap<Equals,Integer>();

		    int[] a = new int[256];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(new Equals(j), j);
			control.put(new Equals(j), j);
			a[i] = j;
		    }

		    Iterator<Map.Entry<Equals,Integer>> it =
			test.entrySet().iterator();
		    for (int i = 0; i < a.length / 2; i++) {
			Map.Entry<Equals,Integer> e = it.next();
			assertTrue(control.remove(e.getKey()) != null);
		    }

		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Equals,Integer>>)
			ois.readObject();

		    while(it.hasNext()) {
			Map.Entry<Equals,Integer> e = it.next();
			assertTrue(control.remove(e.getKey()) != null);
		    }

		    assertEquals(0, control.size());
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithRemovalsEqualHashCodes()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Equals,Integer> test =
			new BindingKeyedHashMap<Equals,Integer>();
		    Map<Equals,Integer> control = new HashMap<Equals,Integer>();

		    int[] a = new int[128];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(new Equals(j), j);
			control.put(new Equals(j), j);
			a[i] = j;
		    }

		    Set<Map.Entry<Equals,Integer>> entrySet =
			control.entrySet();
		    int entries = 0;

		    Iterator<Map.Entry<Equals,Integer>> it =
			test.entrySet().iterator();

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    // then remove half of the entries
		    for (int i = 0; i < a.length; i += 2) {
			test.remove(a[i]);
			control.remove(a[i]);
		    }

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Equals,Integer>>) ois.readObject();

		    // ensure that the deserialized iterator reads the
		    // remaining elements
		    while(it.hasNext()) {
			Map.Entry<Equals,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(entrySet.size(), entries);
		}
	    }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithAdditionsEqualHashCodes()
	throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Equals,Integer> test =
			new BindingKeyedHashMap<Equals,Integer>();
		    Map<Equals,Integer> control = new HashMap<Equals,Integer>();

		    // immediately get the iterator while the map size is zero
		    Iterator<Map.Entry<Equals,Integer>> it =
			test.entrySet().iterator();

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    int[] a = new int[128];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(new Equals(j), j);
			control.put(new Equals(j), j);
			a[i] = j;
		    }

		    int entries = 0;

		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Equals,Integer>>) ois.readObject();

		    // ensure that the deserialized iterator reads all of
		    // the new elements
		    while(it.hasNext()) {
			Map.Entry<Equals,Integer> e = it.next();
			control.remove(e.getKey());
		    }

		    assertEquals(0, control.size());
		}
	    }, taskOwner);
    }

     @SuppressWarnings("unchecked")
     @Test public void testConcurrentIteratorWithReplacementsOnEqualHashCodes()
	 throws Exception
    {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Equals,Integer> test =
			new BindingKeyedHashMap<Equals,Integer>();
		    Map<Equals,Integer> control = new HashMap<Equals,Integer>();

		    int[] a = new int[128];

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(new Equals(j), j);
			control.put(new Equals(j), j);
			a[i] = j;
		    }

		    Set<Map.Entry<Equals,Integer>> entrySet =
			control.entrySet();
		    int entries = 0;

		    Iterator<Map.Entry<Equals,Integer>> it =
			test.entrySet().iterator();
		    for (int i = 0; i < a.length / 2; i++) {
			Map.Entry<Equals,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    assertEquals(a.length / 2, entries);

		    // serialize the iterator
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(it);

		    // now replace all the elements in the map
		    test.clear();
		    control.clear();

		    for (int i = 0; i < a.length; i++) {
			int j = RANDOM.nextInt();
			test.put(new Equals(j), j);
			control.put(new Equals(j), j);
			a[i] = j;
		    }

		    assertEquals(control.size(), test.size());

		    // reserialize the iterator
		    byte[] serializedForm = baos.toByteArray();

		    ByteArrayInputStream bais =
			new ByteArrayInputStream(serializedForm);
		    ObjectInputStream ois = new ObjectInputStream(bais);

		    it = (Iterator<Map.Entry<Equals,Integer>>) ois.readObject();

		    while (it.hasNext()) {
			Map.Entry<Equals,Integer> e = it.next();
			assertTrue(entrySet.contains(e));
			entries++;
		    }

		    // due to the random nature of the entries, we can't check
		    // that it read in another half other elements.  However
		    // this should still check that no exceptions were thrown.
		}
	    }, taskOwner);
    }

    public void testRemoveHashMap() throws Exception {
	int initialCount = getObjectCount();
	System.err.println("initialCount: " + initialCount);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    BindingKeyedHashMap<Integer,Integer> test =
			new BindingKeyedHashMap<Integer,Integer>();
		    for (int i =0; i < 10; i++) {
			test.put(i, i);
		    }
		    AppContext.getDataManager().setBinding("test", test);
		} },
	    taskOwner);
	int countAfterCreation = getObjectCount();
	System.err.println("countAfterCreation: " + countAfterCreation);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    DataManager dm = AppContext.getDataManager();
		    ManagedObject obj = dm.getBinding("test");
		    dm.removeObject(obj);
		} },
	    taskOwner);
	assertEquals(initialCount, getObjectCount());
    }

    /*
     * Utility routines.
     */

    public boolean checkEquals(Map<Integer,Integer> m1,
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
	    Object next = iterator.next();
	    assertTrue(set.remove(next));
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
    static class Foo implements Serializable {
	private static final long serialVersionUID = 1L;
	private final int i;

	Foo(int i) {
	    this.i = i;
	}

	public int hashCode() {
	    return i;
	}

	public boolean equals(Object o) {
	    return o != null &&
		getClass() == o.getClass() &&
		((Foo) o).i == i;
	}
	
	public String toString() {
	    return getClass().getName() + ":" + Integer.toString(i);
	}
    }

    /**
     * A managed object that is equal to objects of the same type with the
     * same value.
     */
    static class Bar extends Foo implements ManagedObject {
	private static final long serialVersionUID = 1L;

	Bar(int i) {
	    super(i);
	}

    }

    /**
     * A serializable object that is equal to objects of the same type with the
     * type, but whose hashCode method always returns zero.
     */
    static class Equals extends Foo {
	private static final long serialVersionUID = 1L;

	Equals(int i) {
	    super(i);
	}

	public int hashCode() {
	    return 0;
	}
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
                // NOTE: this count is used at the end of the test to make sure
                // that no objects were leaked in stressing the structure but
                // any given service (e.g., the task service) may accumulate
                // managed objects, so a more general way to exclude these from
                // the count would be nice but for now the specific types that
                // are accumulated get excluded from the count
                String name = dataService.createReferenceForId(next).get().
		    getClass().getName();
                if (!name.equals("com.sun.sgs.impl.service.task.PendingTask")) {
                    count++;
		}
                last = next;
	    }
	}
    }
}
