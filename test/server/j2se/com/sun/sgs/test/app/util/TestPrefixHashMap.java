
package com.sun.sgs.test.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;

import com.sun.sgs.app.util.PrefixHashMap;

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

/**  Test the PrefixHashMap class. */
@RunWith(NameRunner.class)
//public class TestPrefixHashMap {
public class TestPrefixHashMap extends TestCase {


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

    public TestPrefixHashMap(String name) {
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	txn.commit();
    }

    @Test public void testConstructorOneArg() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1);
	txn.commit();
    }

    @Test (expected=IllegalArgumentException.class)
    public void testConstructorOneArgException() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(-1);
	}
	catch(IllegalArgumentException iae) { 
	    txn.commit();
	    return;
	}
	assertTrue(false);
	txn.commit();
    }


    @Test (expected=IllegalArgumentException.class)
    public void testConstructorOneArgException1() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(0);
	}
	catch(IllegalArgumentException iae) { 
	    txn.commit();
	    return;
	}
	assertTrue(false);
	txn.commit();
    }


    @Test public void testCopyConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();
	for (int i = 0; i < 32; ++i) 
	    control.put(i,i);
	Map<Integer,Integer> test =
	    new PrefixHashMap<Integer,Integer>(control);
	assertEquals(control, test);
	txn.commit();
    }

    
    @Test (expected=NullPointerException.class)
 	public void testNullCopyConstructor() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
 	try {
 	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(null);
 	}
 	catch(NullPointerException npe) { 
	    txn.commit();
	    return;
	}
	assertTrue(false);
	txn.commit();
    }
    

    /*
     * test putting and getting
     */

    @Test public void testPutAndGet() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int count = 0; count < 64; ++count) {
	    int i = RANDOM.nextInt();
	    test.put(i, i);
	    test.put(~i, ~i);
	    control.put(i,i);
	    control.put(~i, ~i);
	}

	for (Integer k : control.keySet()) {
	    if(!control.get(k).equals(test.get(k))) {
		throw new Exception("error in PrefixHashMap.get()");
	    }
	}

        txn.commit();
    }

    @Test public void testPutAndGetOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int count = 0; count < 32; ++count) {
	    int i = RANDOM.nextInt();
	    test.put(i, i);
	    control.put(i,i);
	}

	assertEquals(control, test);    

        txn.commit();
    }


    @Test public void testPutAndRemoveSingleLeaf() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();	

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
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {
	    
	    test.put(i, i);
	    control.put(i, i);
	}

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
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {
	    
	    test.put(-i, i);
	    control.put(-i, i);
	}

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
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	for (int i = 0; i < 96; ++i) {
	    
	    test.put(i, i);
	    test.put(-i, -i);
	    control.put(i, i);
	    control.put(-i, -i);
	}

	for (int i = 0; i < 127; i += 2) {
	    assertEquals(control.remove(i), test.remove(i));
	}
	
	assertEquals(control.size(), test.size());

	assertTrue(control.keySet().containsAll(test.keySet()));
	       
	assertEquals(control, test);
	
        txn.commit();
    }

    @Test public void testPutAndRemoveRandomKeys() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	int[] vals = new int[128];

	for (int i = 0; i < 128; ++i) {
	    int j = (i < 64) ? -RANDOM.nextInt() : RANDOM.nextInt();
	    vals[i] = j;
	    test.put(j, i);
	    control.put(j, i);	    
	}

	for (int i = 0; i < 128; i += 2) {
	    test.remove(vals[i]);
	    control.remove(vals[i]);
	}
	
	assertEquals(control.size(), test.size());

	assertTrue(control.keySet().containsAll(test.keySet()));
	       
	assertEquals(control, test);
	
        txn.commit();
    }

    

    @Test public void testPutAndRemoveNegative() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new LinkedHashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {
	    
	    test.put(-i, -i);
	    control.put(-i, -i);
	}

	for (int i = 0; i < 128; i += 2) {
	    test.remove(-i);
	    control.remove(-i);
	}
	
	assertEquals(control.size(), test.size());

	assertTrue(control.keySet().containsAll(test.keySet()));
	       
	assertEquals(control, test);
	
        txn.commit();
    }



    @Test public void testPutAndRemoveOnSplitTree0() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	PrefixHashMap<Integer,Integer> test = 
	    new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] a = new int[12];

	for (int i = 0; i < 12; ++i) {
	    int j = RANDOM.nextInt();
	    a[i] = j;
	    test.put(j, i);
	    control.put(j, i);
	}

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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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

    @Test public void testPutAndRemoveOnSplitTree2() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[100];

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

    @Test public void testPutAndRemoveOnSplitTree3() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[25];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	    assertEquals(control, test);
	}

	for (int i = 0; i < inputs.length; i += 2) {
	    test.remove(inputs[i]);
	    control.remove(inputs[i]);
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testPutAndRemoveOnSplitTree4() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[50];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	    assertEquals(control, test);
	}

	for (int i = 0; i < inputs.length; i += 2) {
	    int j = RANDOM.nextInt(inputs.length);
	    test.remove(inputs[j]);
	    control.remove(inputs[j]);
	    assertEquals(control, test);
	}

	txn.commit();
    }

    @Test public void testRepeatedPutAndRemove() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[400];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	}

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

    @Test public void testNumerousPutAndRemove() throws Exception {
        txn = createTransaction(10000000);

        DataManager dataManager = AppContext.getDataManager();
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	int[] inputs = new int[4 * 128];

	for (int i = 0; i < inputs.length; ++i) {
	    int j = RANDOM.nextInt();
	    inputs[i] = j;
	    test.put(j,j);
	    control.put(j,j);
	    assertEquals(control.size(), test.size());
	    assertEquals(control, test);
	}

 	for (int i = 0; i < inputs.length; i++) {
 	    test.remove(inputs[i]);
 	    control.remove(inputs[i]);
	    assertEquals(control, test);
 	}

 	assertEquals(control, test);

 	for (int i = 0; i < inputs.length; i++) {
 	    test.put(inputs[i],inputs[i]);
 	    control.put(inputs[i],inputs[i]);

	    assertEquals(control, test);
 	}

 	assertEquals(control, test);


 	for (int i = 0; i < inputs.length; i += 2) {
 	    test.remove(inputs[i]);
 	    control.remove(inputs[i]);

	    assertEquals(control, test);
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
	 Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	 test.putAll(control);
	 assertEquals(control, test);
	 txn.commit();
     }


    @Test public void testNullPut() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(16);
	Map<String,Integer> control = new HashMap<String,Integer>();

	test.put(null, 0);
	control.put(null, 0);
	
	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testNullGet() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(16);

	test.put(null, 0);
	assertEquals(new Integer(0), test.get(null));
	
	txn.commit();
    }

    @Test public void testNullContainsKey() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(16);

	test.put(null, 0);
	assertTrue(test.containsKey(null));
	
	txn.commit();
    }

    @Test public void testNullContainsKeyOnEmptyMap() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(16);

	assertFalse(test.containsKey(null));
	
	txn.commit();
    }

    @Test public void testNullContainsValue() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);

	test.put(0, null);
	assertTrue(test.containsValue(null));
	
	txn.commit();
    }

    @Test public void testContainsKeyOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(16);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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


    @Test public void testPutAndRemoveOnSplitTree5() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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



    @Test public void testLeafSize() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();

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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();

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

    @Test public void testClearVariations() throws Exception {
        txn = createTransaction(1000000);
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
    

    @Test public void testIteratorOnSplitTree() throws Exception {
        txn = createTransaction();

        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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

    @Test public void testTreeSizeOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);

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
	PrefixHashMap<Integer,Integer> test = 
	    new PrefixHashMap<Integer,Integer>(16);

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

    @Test public void testIteratorOnSplitTreeWithRemovals() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	PrefixHashMap<Integer,Integer> test = 
	    new PrefixHashMap<Integer,Integer>(16);
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



    @Test public void testInvalidGet() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();

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

    @Test public void testKeyIterator() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
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
	
	PrefixHashMap<Integer,Integer> m = 
	    (PrefixHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);
	
	txn.commit();
    }
   


    @SuppressWarnings({"unchecked"})
    @Test public void testSplitTreeSerialization() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(16);
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
	
	PrefixHashMap<Integer,Integer> m = 
	    (PrefixHashMap<Integer,Integer>) ois.readObject();

	assertEquals(control, m);
	
	txn.commit();
    }



    /**
     * Utility routines.
     */

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

     /**
      * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
      */

//     public static junit.framework.Test suite() {
// 	return new JUnit4TestAdapter(TestPrefixHashMap.class);
//     }


}
