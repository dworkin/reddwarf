/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ScalableHashMap;
import com.sun.sgs.app.util.ScalableHashMapTestable;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.NameRunner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link ScalableHashMap} class.
 */
@RunWith(NameRunner.class)
public class TestScalableHashMap extends TestCase {

    // the location for the database files
    private static final String DB_DIRECTORY =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestScalableHashMap.db";

    // state variables that are needed for the infrastructure but should
    // not be accessed directly by any of the individual tests
    private static final DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private static final Random RANDOM = new Random(1337);

    private DummyAbstractKernelAppContext appContext;
    private DataServiceImpl dataService;

    // the transaction used, which is class state so that it can be aborted
    // (if it's still active) at teardown
    private DummyTransaction txn;

    /**
     * Test management.
     */

    public TestScalableHashMap(String name) {
	super(name);
    }

    @Before public void setUp() throws Exception {
	appContext = MinimalTestKernel.createContext();
	DummyComponentRegistry serviceRegistry =
	    MinimalTestKernel.getServiceRegistry(appContext);
	serviceRegistry.setComponent(
	    TaskScheduler.class,
	    MinimalTestKernel.getSystemRegistry(
		appContext).getComponent(TaskScheduler.class));
	deleteDirectory();
	createDataService(serviceRegistry);
	txnProxy.setComponent(DataService.class, dataService);
	txnProxy.setComponent(DataServiceImpl.class, dataService);
	serviceRegistry.setComponent(DataManager.class, dataService);
	serviceRegistry.setComponent(DataService.class, dataService);
	serviceRegistry.setComponent(DataServiceImpl.class, dataService);
    }

    @After public void tearDown() {
	if (txn != null && txn.getState() == DummyTransaction.State.ACTIVE) {
	    System.err.println("had to abort txn for test: " + getName());
	    txn.abort(null);
	}
	if (dataService != null) {
	    dataService.shutdown();
	}
	deleteDirectory();
	MinimalTestKernel.destroyContext(appContext);
    }

    /*
     * Test no arg constructor
     */

    @Test public void testConstructorNoArg() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable test =
	    new ScalableHashMapTestable<Integer,Integer>();
	assertEquals(6, test.getMaxTreeDepth());
	assertEquals(6, test.getMinTreeDepth());
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	test = dataService.getBinding("test", ScalableHashMapTestable.class);
	assertEquals(6, test.getMaxTreeDepth());
	assertEquals(6, test.getMinTreeDepth());
	txn.commit();
    }

    /*
     * Test minimum concurrency constructor
     */

    @Test public void testConstructorOneArgDepth() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(1);
	assertEquals(1, test.getMaxTreeDepth());
	assertEquals(1, test.getMinTreeDepth());
	txn.commit();
    }

    @Test public void testConstructorOneArgDepth3() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(3);
	assertEquals(3, test.getMaxTreeDepth());
	assertEquals(3, test.getMinTreeDepth());
	txn.commit();
    }

    @Test public void testConstructorOneArgDepth4() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(5);
	assertEquals(4, test.getMaxTreeDepth());
	assertEquals(4, test.getMinTreeDepth());
	txn.commit();
    }

    @Test public void testConstructorOneArgWithZeroMaxConcurrencyException()
	throws Exception
    {
	txn = createTransaction();
	try {
	    new ScalableHashMap<Integer,Integer>(0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	txn.commit();
    }

    // NOTE: we do not test the maximum concurrency in the
    // constructor, as this would take far too long to test (hours).

    /*
     * Test copy constructor
     */

    @Test public void testCopyConstructor() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	for (int i = 0; i < 32; i++) {
	    control.put(i,i);
	}
	ScalableHashMap test = new ScalableHashMap<Integer,Integer>(control);
	assertEquals(control, test);
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	test = dataService.getBinding("test", ScalableHashMap.class);
	assertEquals(control, test);
	txn.commit();
    }

    @Test public void testNullCopyConstructor() throws Exception {
	txn = createTransaction();
	try {
	    new ScalableHashMap<Integer,Integer>(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException npe) {
	}
	txn.commit();
    }

    @Test public void testMultiParamConstructor() throws Exception {
	txn = createTransaction();
	new ScalableHashMapTestable<Integer,Integer>(1, 32, 5);
	new ScalableHashMapTestable<Integer,Integer>(1, 32, 4);
	txn.commit();
    }

    @Test public void testMultiParamConstructorBadMinConcurrency()
	throws Exception
    {
	txn = createTransaction();
	try {
	    new ScalableHashMapTestable<Integer,Integer>(0, 1, 5);
	    fail("Expected IllegalArgumentException");
	} catch(IllegalArgumentException iae) {
	}
	txn.commit();
    }

    @Test public void testMultiParamConstructorBadSplitThreshold()
	throws Exception
    {
	txn = createTransaction();
	try {
	    new ScalableHashMapTestable<Integer,Integer>(1, 0, 5);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	txn.commit();

    }

    @Test public void testMultiParamConstructorBadDirectorySize()
	throws Exception
    {
	txn = createTransaction();
	try {
	    new ScalableHashMapTestable<Integer,Integer>(1, 32, -1);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	txn.commit();
    }

    /*
     * Test miscellaneous putting and getting
     */

    @Test public void testPutAndGetOnSingleLeaf() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int count = 0; count < 64; ++count) {
	    int i = RANDOM.nextInt();
	    test.put(i, i);
	    test.put(~i, ~i);
	    control.put(i,i);
	    control.put(~i, ~i);
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testPutAndGetOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int count = 0; count < 32; ++count) {
	    int i = RANDOM.nextInt();
	    test.put(i, i);
	    control.put(i,i);
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testPutAndRemoveSingleLeaf() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 54; ++i) {

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

	txn.commit();
    }

    @Test public void testPutAndRemoveLopsidedPositiveKeys() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {

	    test.put(i, i);
	    control.put(i, i);
	}

	assertEquals(control, test);

	for (int i = 0; i < 128; i += 2) {
	    test.remove(i);
	    control.remove(i);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testPutAndRemoveLopsidedNegativeKeys() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {

	    test.put(-i, i);
	    control.put(-i, i);
	}

	assertEquals(control, test);

	for (int i = 0; i < 128; i += 2) {
	    test.remove(-i);
	    control.remove(-i);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testPutAndRemoveDoublyLopsided() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 96; ++i) {

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

	txn.commit();
    }

    @Test public void testPutAndRemoveHalfRandomKeys() throws Exception {
	txn = createTransaction(100000);
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	int[] vals = new int[128];

	for (int i = 0; i < 128; ++i) {
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

	txn.commit();
    }

    @Test public void testPutAndRemoveHalfNegativeKeys() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {

	    test.put(-i, -i);
	    control.put(-i, -i);
	}

	assertEquals(control, test);

	for (int i = 0; i < 128; i += 2) {
	    test.remove(-i);
	    control.remove(-i);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testPutAndRemoveOnSplitTree0() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[12];

	for (int i = 0; i < 12; ++i) {
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

	txn.commit();
    }

    @Test public void testPutAndRemoveOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 24; ++i) {

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

	txn.commit();
    }

    @Test public void testPutAndRemoveOnNoMergeTreeWithNoCollapse()
	throws Exception {

	txn = createTransaction(100000);
	Map<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(1, 8, 2);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[1024];

	for (int i = 0; i < inputs.length; ++i) {
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

	txn.commit();
    }

    @Test public void testPutAndRemoveOnNoMergeTreeWithColllapse()
	throws Exception {

	txn = createTransaction(100000);
	Map<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(1, 8, 4);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[1024];

	for (int i = 0; i < inputs.length; ++i) {
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

	txn.commit();
    }

    @Test public void testRepeatedPutAndRemove() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(1);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[400];

	for (int i = 0; i < inputs.length; ++i) {
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


	txn.commit();
    }

    @Test public void testRepeatedPutAndRemoveWithNoMergeAndNoCollapse()
	throws Exception {

	txn = createTransaction(100000);
	Map<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(1,32, 2);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[1024];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	}

	equals(control, test);
	assertEquals(control, test);

	for (int i = 0; i < inputs.length; i += 4) {
	    test.remove(inputs[i]);
	    control.remove(inputs[i]);
	}

	equals(control, test);
	assertEquals(control, test);

	for (int i = 0; i < inputs.length; i += 3) {
	    test.put(inputs[i],inputs[i]);
	    control.put(inputs[i],inputs[i]);
	}

	equals(control, test);
	assertEquals(control, test);


	for (int i = 0; i < inputs.length; i += 2) {
	    test.remove(inputs[i]);
	    control.remove(inputs[i]);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testRepeatedPutAndRemoveWithNoMergeAndCollapse()
	throws Exception {

	txn = createTransaction(100000);
	Map<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(1,32,4);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[400];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	    // assertTrue("put #" + i, equals(control, test))
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

	txn.commit();
    }

    /*
     * Test putAll
     */

    @Test public void testPutAll() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	 for (int i = 0; i < 32; ++i)
	     control.put(i,i);
	 Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	 test.putAll(control);
	 assertEquals(control, test);
	 txn.commit();
     }

    /*
     * Test put
     */

    @Test public void testPutNotSerializable() throws Exception {
	txn = createTransaction();
	Map<Object,Object> test = new ScalableHashMap<Object,Object>();
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
	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testPutOldValueNotFound() throws Exception {
	txn = createTransaction();
	ScalableHashMap test = new ScalableHashMap();
	dataService.setBinding("test", test);
	Bar value = new Bar(33);
	dataService.setBinding("value", value);
	test.put(Boolean.TRUE, value);
	txn.commit();
	txn = createTransaction();
	dataService.removeObject(
	    dataService.getBinding("value", ManagedObject.class));
	test = dataService.getBinding("test", ScalableHashMap.class);
	try {
	    test.put(Boolean.TRUE, Boolean.FALSE);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    assertEquals(1, test.size());
	}
	txn.commit();
	txn = createTransaction();
	test = dataService.getBinding("test", ScalableHashMap.class);
	try {
	    test.put(Boolean.TRUE, Boolean.FALSE);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    assertEquals(1, test.size());
	}
	txn.commit();
    }

    @Test public void testPutNull() throws Exception {
	txn = createTransaction();
	ScalableHashMap<String,Integer> test =
	    new ScalableHashMap<String,Integer>(16);
	Map<String,Integer> control = new HashMap<String,Integer>();
	test.put(null, 0);
	control.put(null, 0);
	assertEquals(control, test);
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	assertEquals(control,
		     dataService.getBinding("test", ScalableHashMap.class));
	txn.commit();
    }

    /*
     * Test get
     */

    @SuppressWarnings("unchecked")
    @Test public void testGetValueNotFound() throws Exception {
	txn = createTransaction();
	ScalableHashMap test = new ScalableHashMap();
	dataService.setBinding("test", test);
	Bar value = new Bar(33);
	dataService.setBinding("value", value);
	test.put(Boolean.TRUE, value);
	txn.commit();
	txn = createTransaction();
	dataService.removeObject(
	    dataService.getBinding("value", ManagedObject.class));
	test = dataService.getBinding("test", ScalableHashMap.class);
	try {
	    test.get(Boolean.TRUE);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    assertEquals(1, test.size());
	}
	txn.commit();
	txn = createTransaction();
	test = dataService.getBinding("test", ScalableHashMap.class);
	try {
	    test.get(Boolean.TRUE);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    assertEquals(1, test.size());
	}
	txn.commit();
    }

    @Test public void testGetNullKey() throws Exception {
	txn = createTransaction();
	ScalableHashMap<String,Integer> test =
	    new ScalableHashMap<String,Integer>(16);
	test.put(null, 0);
	assertEquals(new Integer(0), test.get(null));
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	assertEquals(
	    new Integer(0),
	    dataService.getBinding("test", ScalableHashMap.class).get(null));
	txn.commit();
    }

    @Test public void testGetNullValue() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,String> test =
	    new ScalableHashMap<Integer,String>(16);
	test.put(0, null);
	assertEquals(null, test.get(0));
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	assertEquals(
	    null,
	    dataService.getBinding("test", ScalableHashMap.class).get(0));
	txn.commit();
    }

    /*
     * Test containsKey
     */

    @Test public void testContainsKeyNull() throws Exception {
	txn = createTransaction();
	ScalableHashMap<String,Integer> test =
	    new ScalableHashMap<String,Integer>(16);
	test.put(null, 0);
	assertTrue(test.containsKey(null));
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	assertTrue(
	    dataService.getBinding(
		"test", ScalableHashMap.class).containsKey(null));
	txn.commit();
    }

    @Test public void testContainsKeyNullOnEmptyMap() throws Exception {
	txn = createTransaction();
	ScalableHashMap<String,Integer> test =
	    new ScalableHashMap<String,Integer>(16);
	assertFalse(test.containsKey(null));
	dataService.setBinding("test", test);
	txn.commit();
	txn = createTransaction();	
	assertFalse(
	    dataService.getBinding(
		"test", ScalableHashMap.class).containsKey(null));
	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsKeyNotFound() throws Exception {
	txn = createTransaction();
	ScalableHashMap test = new ScalableHashMap(16);
	dataService.setBinding("test", test);
	Bar bar = new Bar(1);
	dataService.setBinding("bar", bar);
	test.put(bar, 1);
	test.put(new Bar(2), 2);
	txn.commit();
	txn = createTransaction();	
	dataService.removeObject(dataService.getBinding("bar", Bar.class));
	test = dataService.getBinding("test", ScalableHashMap.class);
	assertFalse(test.containsKey(new Bar(1)));
	assertEquals(2, test.size());
	txn.commit();
	txn = createTransaction();
	test = dataService.getBinding("test", ScalableHashMap.class);
	assertFalse(test.containsKey(new Bar(1)));
	assertEquals(2, test.size());	
	txn.commit();
    }

    /*
     * Test containsValue
     */

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueNull() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	test.put(0, null);
	dataService.setBinding("test", test);
	assertTrue(test.containsValue(null));
	txn.commit();
	txn = createTransaction();	
	test = dataService.getBinding("test", ScalableHashMap.class);
	assertTrue(test.containsValue(null));	
	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testContainsValueNullEmptyMap() throws Exception {
	txn = createTransaction();
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>();
	dataService.setBinding("test", test);
	assertFalse(test.containsValue(null));
	txn.commit();
	txn = createTransaction();	
	test = dataService.getBinding("test", ScalableHashMap.class);
	assertFalse(test.containsValue(null));	
	txn.commit();
    }

    @Test public void testContainsValueNullOnSplitMap() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	test.put(0, null);
	assertTrue(test.containsValue(null));
	txn.commit();
    }

    @Test public void testContainsKeyOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,-j);
	}

	for (int i = 0; i < inputs.length; i++) {
	    assertTrue(test.containsKey(inputs[i]));
	}

	txn.commit();
    }

    @Test public void testValues() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Collection<Integer> control = new ArrayList<Integer>(50);

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,-j);
	    control.add(-j);
	}

	assertTrue(control.containsAll(test.values()));

	txn.commit();
    }

    @Test public void testValuesOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Collection<Integer> control = new ArrayList<Integer>(50);

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,-j);
	    control.add(-j);
	}

	assertTrue(control.containsAll(test.values()));

	txn.commit();
    }

    @Test public void testContainsValue() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,-j);
	}

	for (int i = 0; i < inputs.length; i++) {
	    assertTrue(test.containsValue(-inputs[i]));
	}

	txn.commit();
    }

    @Test public void testContainsValueOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,-j);
	}

	for (int i = 0; i < inputs.length; i++) {
	    assertTrue(test.containsValue(-inputs[i]));
	}

	txn.commit();
    }

    @Test public void testNullRemove() throws Exception {
	txn = createTransaction();
	Map<String,Integer> test = new ScalableHashMap<String,Integer>(16);
	Map<String,Integer> control = new HashMap<String,Integer>();

	test.put(null, 0);
	control.put(null, 0);
	assertEquals(control, test);

	test.remove(null);
	control.remove(null);

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testClear() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	}
	assertEquals(control, test);

	test.clear();
	control.clear();

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testMultipleClearOperations() throws Exception {
	txn = createTransaction(1000000);
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	test.clear();
	assertEquals(control, test);

	// add just a few elements
	for (int i = 0; i < 33; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j,j);
	}

	test.clear();
	assertEquals(control, test);

	// add just enough elements to force a split
	for (int i = 0; i < 1024; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j,j);
	}

	test.clear();
	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testPutAndRemoveOnSplitTree5() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i)
	    inputs[i] = RANDOM.nextInt();

	for (int i = 0; i < inputs.length; ++i)	{
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

	txn.commit();
    }

    @Test public void testInvalidGet() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();

	// put in numbers
	for (int i = 4000; i < 4100; ++i) {
	    test.put(i, i);
	}

	// get from outside the range of the put
	for (int i = 0; i < 100; ++i) {

	    assertEquals(null,test.get(i));
	}

	txn.commit();
    }

    /*
     * Size Tests
     */

    @Test public void testLeafSize() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();

	assertEquals(0, test.size());

	for (int i = 0; i < 128; ++i) {
	    test.put(i, i);
	}

	assertEquals(128, test.size());

	// remove the evens
	for (int i = 0; i < 128; i += 2) {
	    test.remove(i);
	}

	assertEquals(64, test.size());

	// remove the odds
	for (int i = 1; i < 128; i += 2) {
	    test.remove(i);
	}

	assertEquals(0, test.size());

	txn.commit();
    }

    @Test public void testLeafSizeAfterRemove() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();

	int SAMPLE_SIZE = 10;

	int[] inputs1 = new int[SAMPLE_SIZE];
	int[] inputs2 = new int[SAMPLE_SIZE];
	int[] inputs3 = new int[SAMPLE_SIZE];

	for (int i = 0; i < inputs1.length; ++i) {
	    inputs1[i] = RANDOM.nextInt();
	    inputs2[i] = RANDOM.nextInt();
	    inputs3[i] = RANDOM.nextInt();
	}

	for (int i = 0; i < inputs1.length; ++i) {
	    test.put(inputs1[i], inputs1[i]);
	    test.put(inputs2[i], inputs2[i]);
	    assertEquals(test.size(), (i+1)*2);
	}

	for (int i = 0; i < inputs1.length; ++i) {
	    int beforeSize = test.size();
	    test.put(inputs3[i], inputs3[i]);
	    test.remove(inputs2[i]);
	    assertEquals(beforeSize, test.size());
	}

	txn.commit();
    }

    @Test public void testTreeSizeOnSplitTree() throws Exception {
	txn = createTransaction();
	// create a tree with an artificially small leaf size
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);

	assertEquals(0, test.size());

	for (int i = 0; i < 5; ++i) {
	    test.put(i, i);
	}

	assertEquals(5, test.size());

	for (int i = 5; i < 15; ++i) {
	    test.put(i,i);
	}

	assertEquals(15, test.size());

	for (int i = 15; i < 31; ++i) {
	    test.put(i,i);
	}

	assertEquals(31, test.size());

	txn.commit();
    }

    @Test public void testTreeSizeOnSplitTreeWithRemovals() throws Exception {
	txn = createTransaction();
	// create a tree with an artificially small leaf size
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>(16);

	assertEquals(0, test.size());


	int[] inserts = new int[128];
	for (int i = 0; i < inserts.length; ++i) {
	    inserts[i] = RANDOM.nextInt();
	}

	// add 32
	for (int i = 0; i < 32; ++i) {
	    test.put(inserts[i], inserts[i]);
	}

	assertEquals(32, test.size());

	// remove 10
	for (int i = 0; i < 10; ++i) {
	    test.remove(inserts[i]);
	}

	assertEquals(22, test.size());

	// add 32
	for (int i = 32; i < 64; ++i) {
	    test.put(inserts[i],inserts[i]);
	}

	assertEquals(54, test.size());

	// remove 10
	for (int i = 32; i < 42; ++i) {
	    test.remove(inserts[i]);
	}

	// add 64
	for (int i = 64; i < 128; ++i) {
	    test.put(inserts[i],inserts[i]);
	}

	assertEquals(108, test.size());

	// remove 5
	for (int i = 64; i < 69; ++i) {
	    test.remove(inserts[i]);
	}
	assertEquals(103, test.size());

	txn.commit();
    }

    /*
     * Iterator Tests
     */

    @Test public void testIteratorOnSplitTree() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Set<Integer> control = new HashSet<Integer>();

	// get from outside the range of the put
	for (int i = 0; i < 33; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j,j);
	    control.add(j);
	}

	for (Integer i : test.keySet()) {
	    control.remove(i);
	}

	assertEquals(0, control.size());
	txn.commit();
    }

    @Test public void testIteratorOnSplitTreeWithRemovals() throws Exception {
	txn = createTransaction();
	// create a tree with an artificially small leaf size
	ScalableHashMap<Integer,Integer> test =
	    new ScalableHashMap<Integer,Integer>(16);
	HashMap<Integer,Integer> control = new HashMap<Integer,Integer>();

	assertEquals(0, test.size());

	int[] inserts = new int[128];
	for (int i = 0; i < inserts.length; ++i) {
	    inserts[i] = RANDOM.nextInt();
	}

	// add 32
	for (int i = 0; i < 32; ++i) {
	    test.put(inserts[i], inserts[i]);
	    control.put(inserts[i], inserts[i]);
	}

	assertEquals(control, test);

	// remove 10
	for (int i = 0; i < 10; ++i) {
	    test.remove(inserts[i]);
	    control.remove(inserts[i]);
	}

	assertEquals(control, test);

	// add 32
	for (int i = 32; i < 64; ++i) {
	    test.put(inserts[i],inserts[i]);
	    control.put(inserts[i],inserts[i]);
	}

	assertEquals(control, test);

	// remove 10
	for (int i = 32; i < 42; ++i) {
	    test.remove(inserts[i]);
	    control.remove(inserts[i]);
	}

	assertEquals(control, test);

	// add 64
	for (int i = 64; i < 128; ++i) {
	    test.put(inserts[i],inserts[i]);
	    control.put(inserts[i],inserts[i]);
	}

	assertEquals(control, test);

	// remove 5
	for (int i = 64; i < 69; ++i) {
	    test.remove(inserts[i]);
	    control.remove(inserts[i]);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testKeyIterator() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Set<Integer> control = new HashSet<Integer>();

	// get from outside the range of the put
	for (int i = 0; i < 100; ++i) {
	    test.put(i,i);
	    control.add(i);
	}

	for (Integer i : test.keySet()) {
	    control.remove(i);
	}

	assertEquals(0, control.size());

	txn.commit();
    }

    @Test public void testKeyIteratorOnSplitMap() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Set<Integer> control = new HashSet<Integer>();

	// get from outside the range of the put
	for (int i = 0; i < 33; ++i) {
	    test.put(i,i);
	    control.add(i);
	}

	for (Integer i : test.keySet()) {
	    control.remove(i);
	}

	assertEquals(0, control.size());

	txn.commit();
    }

    @Test public void testValuesIterator() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Set<Integer> control = new HashSet<Integer>();

	// get from outside the range of the put
	for (int i = 0; i < 100; ++i) {
	    test.put(i,i);
	    control.add(i);
	}

	for (Integer i : test.values()) {
	    control.remove(i);
	}

	assertEquals(0, control.size());

	txn.commit();
    }

    @Test public void testValuesIteratorOnSplitMap() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Set<Integer> control = new HashSet<Integer>();

	// get from outside the range of the put
	for (int i = 0; i < 33; ++i) {
	    test.put(i,i);
	    control.add(i);
	}

	for (Integer i : test.values()) {
	    control.remove(i);
	}

	assertEquals(0, control.size());

	txn.commit();
    }

    @Test public void testInvalidRemove() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();

	// put in numbers
	for (int i = 4000; i < 4100; ++i) {
	    test.put(i, i);
	}

	// get from outside the range of the put
	for (int i = 0; i < 100; ++i) {

	    assertEquals(null, test.remove(i));
	}

	txn.commit();
    }

    @SuppressWarnings("unchecked")
	@Test public void testLeafSerialization() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[100];

	for (int i = 0; i < a.length; ++i) {
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

	ScalableHashMap<Integer,Integer> m =
	    (ScalableHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testSplitTreeSerialization() throws Exception {
	txn = createTransaction();
	Map<Integer,Integer> test = new ScalableHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[100];

	for (int i = 0; i < a.length; ++i) {
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

	ScalableHashMap<Integer,Integer> m =
	    (ScalableHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);

	txn.commit();
    }

    /*
     * Tests on ManagedObject vs. Serializable object keys
     *
     * These tests should expose any bugs in the ScalableHashMap.PrefixEntry
     * class, especially in the setValue() method.  These should also expose
     * any bugs in the KeyValuePair class
     */

    @Test public void testOnManagedObjectKeys() throws Exception {
	txn = createTransaction();
	Map<Bar,Foo> test = new ScalableHashMap<Bar,Foo>();
	Map<Bar,Foo> control = new HashMap<Bar,Foo>();

	for (int i = 0; i < 64; ++i) {

	    test.put(new Bar(i), new Foo(i));
	    control.put(new Bar(i), new Foo(i));
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testOnManagedObjectValues() throws Exception {
	txn = createTransaction();
	Map<Foo,Bar> test = new ScalableHashMap<Foo,Bar>();
	Map<Foo,Bar> control = new HashMap<Foo,Bar>();

	for (int i = 0; i < 64; ++i) {

	    test.put(new Foo(i), new Bar(i));
	    control.put(new Foo(i), new Bar(i));
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testOnManagedObjectKeysAndValues() throws Exception {
	txn = createTransaction();
	Map<Bar,Bar> test =
	    new ScalableHashMapTestable<Bar,Bar>();
	Map<Bar,Bar> control = new HashMap<Bar,Bar>();

	for (int i = 0; i < 64; ++i) {

	    test.put(new Bar(i), new Bar(i));
	    control.put(new Bar(i), new Bar(i));
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testSerializableKeysReplacedWithManagedObjects()
	throws Exception {

	txn = createTransaction();
	Map<Foo,Foo> test =
	    new ScalableHashMapTestable<Foo,Foo>();
	Map<Foo,Foo> control = new HashMap<Foo,Foo>();

	for (int i = 0; i < 64; ++i) {
	    test.put(new Foo(i), new Foo(i));
	    control.put(new Foo(i), new Foo(i));
	    assertEquals(control, test);
	}

	for (int i = 0; i < 64; ++i) {
	    test.put(new Bar(i), new Foo(i));
	    control.put(new Bar(i), new Foo(i));
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testSerializableValuesReplacedWithManagedObjects()
	throws Exception {

	txn = createTransaction();
	Map<Foo,Foo> test =
	    new ScalableHashMapTestable<Foo,Foo>();
	Map<Foo,Foo> control = new HashMap<Foo,Foo>();

	for (int i = 0; i < 64; ++i) {
	    test.put(new Foo(i), new Foo(i));
	    control.put(new Foo(i), new Foo(i));
	    assertEquals(control, test);
	}

	for (int i = 0; i < 64; ++i) {
	    test.put(new Foo(i), new Bar(i));
	    control.put(new Foo(i), new Bar(i));
	    assertEquals(control, test);
	}

	txn.commit();
    }

    /*
     * Concurrent Iterator tests
     *
     * These tests should expose any problems when the
     * ScalableHashMap.ConcurrentIterator class is serialized and modifications
     * are made to the map before it is deserialized.  This should simulate the
     * conditions between transactions where the map might be modified
     */

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIterator() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j, j);
	    control.put(j, j);
	    a[i] = j;
	}

	Set<Map.Entry<Integer,Integer>> entrySet = control.entrySet();
	int entries = 0;

	for (Iterator<Map.Entry<Integer,Integer>> it =
		 test.entrySet().iterator();
	     it.hasNext(); ) {

	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}
	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorSerialization() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[256];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j, j);
	    control.put(j, j);
	    a[i] = j;
	}

	Set<Map.Entry<Integer,Integer>> entrySet = control.entrySet();
	int entries = 0;

	Iterator<Map.Entry<Integer,Integer>> it = test.entrySet().iterator();
	for (int i = 0; i < a.length / 2; ++i) {
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

	it = (Iterator<Map.Entry<Integer,Integer>>)(ois.readObject());

	while(it.hasNext()) {
	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithRemovals() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[1024];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j, j);
	    control.put(j, j);
	    a[i] = j;
	}

	Set<Map.Entry<Integer,Integer>> entrySet = control.entrySet();
	int entries = 0;

	Iterator<Map.Entry<Integer,Integer>> it = test.entrySet().iterator();

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

	it = (Iterator<Map.Entry<Integer,Integer>>)(ois.readObject());

	// ensure that the deserialized iterator reads the remaining
	// elements
	while(it.hasNext()) {
	    Map.Entry<Integer,Integer> e = it.next();
	    e.getKey();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithAdditions() throws Exception {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	// immediately get the iterator while the map size is zero
	Iterator<Map.Entry<Integer,Integer>> it = test.entrySet().iterator();

	// serialize the iterator
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new ObjectOutputStream(baos);
	oos.writeObject(it);

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j, j);
	    control.put(j, j);
	    a[i] = j;
	}

	Set<Map.Entry<Integer,Integer>> entrySet = control.entrySet();
	int entries = 0;

	byte[] serializedForm = baos.toByteArray();

	ByteArrayInputStream bais =
	    new ByteArrayInputStream(serializedForm);
	ObjectInputStream ois = new ObjectInputStream(bais);

	it = (Iterator<Map.Entry<Integer,Integer>>)(ois.readObject());

	// ensure that the deserialized iterator reads the all the new
	// elements
	while(it.hasNext()) {
	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithReplacements()
	throws Exception
    {
	txn = createTransaction();
	ScalableHashMapTestable<Integer,Integer> test =
	    new ScalableHashMapTestable<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(j, j);
	    control.put(j, j);
	    a[i] = j;
	}

	Set<Map.Entry<Integer,Integer>> entrySet = control.entrySet();
	int entries = 0;

	Iterator<Map.Entry<Integer,Integer>> it = test.entrySet().iterator();
	for (int i = 0; i < a.length / 2; ++i) {
	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(a.length / 2, entries);

	// serialize the iterator
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new ObjectOutputStream(baos);
	oos.writeObject(it);

	// now repalce all th elements in the map
	test.clear();
	control.clear();
	for (int i = 0; i < a.length; ++i) {
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

	it = (Iterator<Map.Entry<Integer,Integer>>)(ois.readObject());

	while(it.hasNext()) {
	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	// due to the random nature of the entries, we can't check
	// that it read in another half other elements.  However this
	// should still check that no execptions were thrown.

	txn.commit();
    }

    /*
     * Tests on concurrent iterator edge cases
     */

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorSerializationEqualHashCodes()
	throws Exception
    {
	txn = createTransaction();
	ScalableHashMapTestable<Equals,Integer> test =
	    new ScalableHashMapTestable<Equals,Integer>(16);
	Map<Equals,Integer> control = new HashMap<Equals,Integer>();

	int[] a = new int[256];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(new Equals(j), j);
	    control.put(new Equals(j), j);
	    a[i] = j;
	}

	Iterator<Map.Entry<Equals,Integer>> it = test.entrySet().iterator();
	for (int i = 0; i < a.length / 2; ++i) {
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

	it = (Iterator<Map.Entry<Equals,Integer>>)(ois.readObject());

	while(it.hasNext()) {
	    Map.Entry<Equals,Integer> e = it.next();
	    assertTrue(control.remove(e.getKey()) != null);
	}

	assertEquals(0, control.size());

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithRemovalsEqualHashCodes()
	throws Exception
    {
	txn = createTransaction();
	ScalableHashMapTestable<Equals,Integer> test =
	    new ScalableHashMapTestable<Equals,Integer>(16);
	Map<Equals,Integer> control = new HashMap<Equals,Integer>();

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(new Equals(j), j);
	    control.put(new Equals(j), j);
	    a[i] = j;
	}

	Set<Map.Entry<Equals,Integer>> entrySet = control.entrySet();
	int entries = 0;

	Iterator<Map.Entry<Equals,Integer>> it = test.entrySet().iterator();

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

	it = (Iterator<Map.Entry<Equals,Integer>>)(ois.readObject());

	// ensure that the deserialized iterator reads the remaining
	// elements
	while(it.hasNext()) {
	    Map.Entry<Equals,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings("unchecked")
    @Test public void testConcurrentIteratorWithAdditionsEqualHashCodes()
	throws Exception {

	txn = createTransaction();
	ScalableHashMapTestable<Equals,Integer> test =
	    new ScalableHashMapTestable<Equals,Integer>(16);
	Map<Equals,Integer> control = new HashMap<Equals,Integer>();

	// immediately get the iterator while the map size is zero
	Iterator<Map.Entry<Equals,Integer>> it = test.entrySet().iterator();

	// serialize the iterator
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new ObjectOutputStream(baos);
	oos.writeObject(it);

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
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

	it = (Iterator<Map.Entry<Equals,Integer>>)(ois.readObject());

	// ensure that the deserialized iterator reads the all the new
	// elements
	while(it.hasNext()) {
	    Map.Entry<Equals,Integer> e = it.next();
	    control.remove(e.getKey());
	}

	assertEquals(0, control.size());

	txn.commit();
    }

     @SuppressWarnings("unchecked")
     @Test public void testConcurrentIteratorWithReplacementsOnEqualHashCodes()
	 throws Exception {

	txn = createTransaction();
	ScalableHashMapTestable<Equals,Integer> test =
	    new ScalableHashMapTestable<Equals,Integer>(16);
	Map<Equals,Integer> control = new HashMap<Equals,Integer>();

	int[] a = new int[128];

	for (int i = 0; i < a.length; ++i) {
	    int j = RANDOM.nextInt();
	    test.put(new Equals(j), j);
	    control.put(new Equals(j), j);
	    a[i] = j;
	}

	Set<Map.Entry<Equals,Integer>> entrySet = control.entrySet();
	int entries = 0;

	Iterator<Map.Entry<Equals,Integer>> it = test.entrySet().iterator();
	for (int i = 0; i < a.length / 2; ++i) {
	    Map.Entry<Equals,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	assertEquals(a.length / 2, entries);

	// serialize the iterator
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream oos = new ObjectOutputStream(baos);
	oos.writeObject(it);

	// now repalce all the elements in the map
	test.clear();
	control.clear();

	for (int i = 0; i < a.length; ++i) {
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

	it = (Iterator<Map.Entry<Equals,Integer>>)(ois.readObject());

	while(it.hasNext()) {
	    Map.Entry<Equals,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}

	// due to the random nature of the entries, we can't check
	// that it read in another half other elements.  However this
	// should still check that no exceptions were thrown.

	txn.commit();
    }

    /*
     * Utility routines.
     */

    public boolean equals(Map<Integer,Integer> m1, Map<Integer,Integer> m2) {

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

    /** Creates a transaction with the default timeout. */
    private DummyTransaction createTransaction() {
	DummyTransaction txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** Creates a transacction with the specified timeout. */
    private DummyTransaction createTransaction(long timeout) {
	DummyTransaction txn = new DummyTransaction(timeout);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** Creates the data service. */
    private void createDataService(DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(DB_DIRECTORY);
	if (! dir.exists()) {
	    if (! dir.mkdir()) {
		throw new RuntimeException(
		    "couldn't create db directory: " + DB_DIRECTORY);
	    }
	}
	Properties properties = new Properties();
	properties.setProperty(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    DB_DIRECTORY);
	properties.setProperty(StandardProperties.APP_NAME,
			       "TestScalableHashMap");
	dataService = new DataServiceImpl(properties, registry, txnProxy);
    }

    private void deleteDirectory() {
	File dir = new File(DB_DIRECTORY);
	if (dir.exists()) {
	    for (File file : dir.listFiles())
		if (! file.delete())
		    throw new RuntimeException("couldn't delete: " + file);
	    if (! dir.delete())
		throw new RuntimeException("couldn't remove: " + dir);
	}
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

	public Foo(int i) {
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
    }

    /**
     * A managed object that is equal to objects of the same type with the
     * same value.
     */
    static class Bar extends Foo implements ManagedObject {
	private static final long serialVersionUID = 1L;

	public Bar(int i) {
	    super(i);
	}
    }

    /**
     * A serializable object that is equal to objects of the same type with the
     * type, but whose hashCode method always returns zero.
     */
    static class Equals extends Foo {
	private static final long serialVersionUID = 1L;

	public Equals(int i) {
	    super(i);
	}

	public int hashCode() {
	    return 0;
	}
    }
}
