
package com.sun.sgs.test.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.app.util.DistributedHashMap;
import com.sun.sgs.app.util.TestableDistributedHashMap;

import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;

import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.service.DataService;

import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import junit.framework.TestCase;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import junit.framework.JUnit4TestAdapter;
import org.junit.runner.RunWith;

import com.sun.sgs.test.util.NameRunner;

/**  
 * Test the {@link DistributedHashMap} class. 
 */
@RunWith(NameRunner.class)
public class TestDistributedHashMap extends TestCase {


    // the location for the database files
    private static String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestDistributedHashMap.db";

    // state variables that are needed for the infrastructure but should
    // not be accessed directly by any of the individual tests
    private static DummyTransactionProxy txnProxy =
        MinimalTestKernel.getTransactionProxy();
    private DummyAbstractKernelAppContext appContext;
    private DataServiceImpl dataService;

    // the transaction used, which is class state so that it can be aborted
    // (if it's still active) at teardown
    private DummyTransaction txn;


    public static final Random RANDOM = new Random(1337);

    /**
     * Test management.
     */

    public TestDistributedHashMap(String name) {
	        super(name);
    }

    @Before public void setUp() {
//         System.err.println("Testcase: " );
	try {
	    appContext = MinimalTestKernel.createContext();
	    DummyComponentRegistry serviceRegistry =
		MinimalTestKernel.getServiceRegistry(appContext);
	    deleteDirectory();
	    createDataService(MinimalTestKernel.getSystemRegistry(appContext));
	    
	    txn = createTransaction(10000);
	    dataService.configure(serviceRegistry, txnProxy);
	    txnProxy.setComponent(DataService.class, dataService);
	    txnProxy.setComponent(DataServiceImpl.class, dataService);
	    serviceRegistry.setComponent(DataManager.class, dataService);
	    serviceRegistry.setComponent(DataService.class, dataService);
	    serviceRegistry.setComponent(DataServiceImpl.class, dataService);
	    txn.commit();
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }

    @After public void tearDown() {
        if ((txn != null) &&
            (txn.getState() == DummyTransaction.State.ACTIVE)) {
            System.err.println("had to abort txn for test: " + getName());
            txn.abort(null);
        }
        if (dataService != null)
            dataService.shutdown();
        deleteDirectory();
        MinimalTestKernel.destroyContext(appContext);
    }

    
    /* Constructor tests */
    
    @Test public void testConstructorNoArg() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
	txn.commit();
    }

    @Test public void testConstructorNoArgDepth() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
	assertEquals(6, test.getMaxTreeDepth());
	assertEquals(6, test.getMinTreeDepth());
	txn.commit();
    }


    @Test public void testConstructorOneArgDepth() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1);
	assertEquals(1, test.getMaxTreeDepth());
	assertEquals(1, test.getMinTreeDepth());
	txn.commit();
    }

    @Test public void testConstructorOneArgDepth3() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(3);
	assertEquals(3, test.getMaxTreeDepth());
	assertEquals(3, test.getMinTreeDepth());
	txn.commit();
    }


    @Test public void testConstructorOneArgDepth4() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(5);
	assertEquals(4, test.getMaxTreeDepth());
	assertEquals(4, test.getMinTreeDepth());
	txn.commit();
    }


    @Test (expected=IllegalArgumentException.class)
    public void testConstructorOneArgWithZeroMaxConcurrencyException() 
	throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(0);
	}
	catch(IllegalArgumentException iae) { 
	    txn.commit();
	    return;
	}
	txn.commit();
	assertTrue(false);
    }

    // NOTE: we do not test the maximum concurrency in the
    // constructor, as this would take far too long to test (hours).


    @Test public void testCopyConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	for (int i = 0; i < 32; ++i) 
	    control.put(i,i);
	Map<Integer,Integer> test =
	    new TestableDistributedHashMap<Integer,Integer>(control);
	assertEquals(control, test);
	txn.commit();
    }

    
    @Test (expected=NullPointerException.class)
 	public void testNullCopyConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
 	try {
 	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(null);
 	}
 	catch(NullPointerException npe) { 
	    txn.commit();
	    return;
	}
	assertTrue(false);
	txn.commit();
    }
    
    @Test public void testMuiltParameterConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(1, 32, 5);
	}
	catch(IllegalArgumentException iae) { 
	    txn.commit();
	    assertTrue(false);
	    return;
	}
	txn.commit();
    }

    @Test public void testPreSplitWithMultipleLevelsConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(1, 32, 4);
	}
	catch(IllegalArgumentException iae) { 
	    txn.commit();
	    assertTrue(false);
	    return;
	}
	txn.commit();
    }


    @Test public void testConstructorSplitThesholdException() 
	throws Exception {

        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(1, 0, 5);
	}
	catch(IllegalArgumentException iae) { 	    
	    txn.commit();
	    return;
	}
	txn.commit();
	assertTrue(false);
    }

    @Test public void testConstructorInvalidTableSize() 
	throws Exception {

        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = 
		new TestableDistributedHashMap<Integer,Integer>(1, 32, -1);
	}
	catch(IllegalArgumentException iae) { 	    
	    txn.commit();
	    return;
	}
	txn.commit();
	assertTrue(false);
    }
    

    /*
     * test putting and getting
     */

    @Test public void testPutAndGetOnSingleLeaf() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1, 8, 2);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1, 8, 4);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[400];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	}
	assertEquals(control, test);
// 	System.out.println("puts passed");

	for (int i = 0; i < inputs.length; i += 4) {
	    test.remove(inputs[i]);
	    control.remove(inputs[i]);
	}
	assertEquals(control, test);
// 	System.out.println("removes passed");

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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1,32, 2);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(1,32,4);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[400];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
// 	    if (!equals(control, test))
// 		System.out.println("put #" + i);
// 	    assertEquals(control, test);
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

    @Test public void testPutAll() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	 for (int i = 0; i < 32; ++i) 
	     control.put(i,i);
	 Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
	 test.putAll(control);
	 assertEquals(control, test);
	 txn.commit();
     }


    @Test public void testNullPut() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new TestableDistributedHashMap<String,Integer>(16);
	Map<String,Integer> control = new HashMap<String,Integer>();

	test.put(null, 0);
	control.put(null, 0);
	
	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testNullGet() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new TestableDistributedHashMap<String,Integer>(16);

	test.put(null, 0);
	assertEquals(new Integer(0), test.get(null));
	
	txn.commit();
    }

    @Test public void testNullContainsKey() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new TestableDistributedHashMap<String,Integer>(16);

	test.put(null, 0);
	assertTrue(test.containsKey(null));
	
	txn.commit();
    }

    @Test public void testNullContainsKeyOnEmptyMap() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new TestableDistributedHashMap<String,Integer>(16);

	assertFalse(test.containsKey(null));
	
	txn.commit();
    }

    @Test public void testNullContainsValue() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();

	test.put(0, null);
	assertTrue(test.containsValue(null));
	
	txn.commit();
    }


    @Test public void testNullContainsValueOnSplitMap() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);

	test.put(0, null);

	assertTrue(test.containsValue(null));
	
	txn.commit();
    }

    @Test public void testContainsKeyOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new TestableDistributedHashMap<String,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();

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

//     /*
//      *
//      * Size Tests
//      *
//      */


    @Test public void testLeafSize() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();

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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();

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
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);

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
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);

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
     *
     * Iterator Tests
     *
     */

    @Test public void testIteratorOnSplitTree() throws Exception {
        txn = createTransaction();

        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
	
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

    @SuppressWarnings({"unchecked"})
	@Test public void testLeafSerialization() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>();
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
	
	TestableDistributedHashMap<Integer,Integer> m = 
	    (TestableDistributedHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);
	
	txn.commit();
    }
   


    @SuppressWarnings({"unchecked"})
    @Test public void testSplitTreeSerialization() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new TestableDistributedHashMap<Integer,Integer>(16);
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
	
	TestableDistributedHashMap<Integer,Integer> m = 
	    (TestableDistributedHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);
	
	txn.commit();
    }


    /*
     * Tests on ManagedObject vs. Serializable object keys
     *
     * These tests should expose any bugs in the
     * DistributedHashMap.PrefixEntry class, especially in the
     * setValue() method.  These should also expose any bugs in the
     * KeyValuePair class
     */
    @Test public void testOnManagedObjectKeys() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Bar,Foo> test = 
	    new TestableDistributedHashMap<Bar,Foo>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Foo,Bar> test = 
	    new TestableDistributedHashMap<Foo,Bar>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Bar,Bar> test = 
	    new TestableDistributedHashMap<Bar,Bar>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Foo,Foo> test = 
	    new TestableDistributedHashMap<Foo,Foo>();
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
        DataManager dataManager = AppContext.getDataManager();
	Map<Foo,Foo> test = 
	    new TestableDistributedHashMap<Foo,Foo>();
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
     * DistributedHashMap.ConcurrentIterator class is serialized and
     * modifications are made to the map before it is deserialized.
     * This should simulate the conditions between transactions where
     * the map might be modified
     */
     
    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIterator() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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

	for (Iterator<Map.Entry<Integer,Integer>> it = test.entrySet().iterator(); 
	     it.hasNext();) {

	    Map.Entry<Integer,Integer> e = it.next();
	    assertTrue(entrySet.contains(e));
	    entries++;
	}
	assertEquals(entrySet.size(), entries);

	txn.commit();
    }

    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorSerailization() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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


    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorWithRemovals() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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


    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorWithAdditions() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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

    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorWithReplacements() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Integer,Integer> test = 
	    new TestableDistributedHashMap<Integer,Integer>(16);
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

    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorSerailizationEqualHashCodes() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();

	TestableDistributedHashMap<Equals,Integer> test = 
	    new TestableDistributedHashMap<Equals,Integer>(16);
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


    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorWithRemovalsEqualHashCodes() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Equals,Integer> test = 
	    new TestableDistributedHashMap<Equals,Integer>(16);
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

    @SuppressWarnings({"unchecked"})
    @Test public void testConcurrentIteratorWithAdditionsEqualHashCodes()
	throws Exception {

	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Equals,Integer> test = 
	    new TestableDistributedHashMap<Equals,Integer>(16);
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
	
	//Set<Map.Entry<Equals,Integer>> entrySet = control.entrySet();
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

     @SuppressWarnings({"unchecked"})
     @Test public void testConcurrentIteratorWithReplacementsOnEqualHashCodes() 
	 throws Exception {

 	txn = createTransaction();
 	DataManager dataManager = AppContext.getDataManager();
	TestableDistributedHashMap<Equals,Integer> test = 
	    new TestableDistributedHashMap<Equals,Integer>(16);
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
	
	// now repalce all th elements in the map
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
	// should still check that no execptions were thrown.

	txn.commit();
    }













    /**
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
		    System.out.println("m2.containsKey() ? " + m2.containsKey(key));
		    return false;
		}
	    }
	}
	
	return true;
    }
    
    private DummyTransaction createTransaction() {
	DummyTransaction txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    private DummyTransaction createTransaction(long timeout) {
	DummyTransaction txn = new DummyTransaction(timeout);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    private void createDataService(DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(DB_DIRECTORY);
	if (! dir.exists()) {
	    if (! dir.mkdir()) {
		throw new RuntimeException("couldn't create db directory: " +
					   DB_DIRECTORY);
	    }
	}

	Properties properties = new Properties();
	properties.setProperty("com.sun.sgs.impl.service.data.store." +
			       "DataStoreImpl.directory", DB_DIRECTORY);
	properties.setProperty(StandardProperties.APP_NAME,
			       "TestDistributedHashMap");
	dataService = new DataServiceImpl(properties, registry);
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

    static class Foo implements Serializable {

	private static final long serialVersionUID = 1L;

	public int i;

	public Foo(int i) {
	    this.i = i;
	}

	public int hashCode() {
	    return i;
	}

	public boolean equals(Object o) {
	    return (o instanceof Foo) 
		? ((Foo)o).i == i
		: false;
	}
    }

    static class Bar extends Foo implements ManagedObject {

	private static final long serialVersionUID = 1L;
	
	public Bar(int i) {
	    super(i);
	}
	
    }
    

    static class Equals implements Serializable {

	private static final long serialVersionUID = 1L;

	int i;

	public Equals(int i) {
	    this.i = i;
	}

	public boolean equals(Object o) {
	    return (o instanceof Equals)
		? ((Equals)o).i == i : false;
	}

	public int hashCode() {
	    return 0;
	}
	
    }

     /**
      * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
      */

//     public static junit.framework.Test suite() {
// 	return new JUnit4TestAdapter(TestDistributedHashMap.class);
//     }


}
