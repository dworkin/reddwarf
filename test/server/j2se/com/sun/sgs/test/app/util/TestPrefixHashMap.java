
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

import java.io.File;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
//             System.err.println("had to abort txn for test: " + getName());
            txn.abort(null);
        }
        if (dataService != null)
            dataService.shutdown();
        deleteDirectory();
        MinimalTestKernel.destroyContext(appContext);
    }

    /**
     * Constructor tests.
     */
    
    @Test public void testConstructorNoArg() {
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
    }

    @Test public void testConstructorOneArg() {
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(.3f);
    }

    @Test (expected=IllegalArgumentException.class)
    public void testConstructorOneArgException() {
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(0.0f);
	}
	catch(IllegalArgumentException iae) { }
    }

    @Test (expected=IllegalArgumentException.class)
    public void testConstructorOneArgException2() {
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(-3f);
	}
	catch(IllegalArgumentException iae) { }

    }

    @Test public void testConstructorTwoArg() {
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(.3f,4);
    }

    // one arg calls the two arg with the error cases above, so no
    // need to recheck
    
    @Test (expected=IllegalArgumentException.class)
	public void testConstructorTwoArgException() {
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(.3f,0);
	}
	catch(IllegalArgumentException iae) { }

    }

    @Test (expected=IllegalArgumentException.class)
	public void testConstructorTwoArgException2() {
	try {
	    Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(.3f,-1);
	}
	catch(IllegalArgumentException iae) { }
	
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int count = 0; count < 32; ++count) {
	    int i = RANDOM.nextInt();
	    test.put(i, i);
	    test.put(~i, ~i);
	    control.put(i,i);
	    control.put(~i, ~i);
	}

// 	System.out.printf("test size: %d, control size: %d\n",
// 			  test.size(), control.size());
	

	for (Integer k : control.keySet()) {
	    assertEquals(control.get(k), test.get(k));
	}

        txn.commit();
    }


    @Test public void testPutAndRemove() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>();
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 128; ++i) {
	    
	    test.put(i, i);
	    test.put(~i, ~i);
	    control.put(i,i);
	    control.put(~i, ~i);
	}

	for (int i = 0; i < 128; i += 2) {
	    test.remove(i);
	    control.remove(i);
	}

	for (Integer k : control.keySet()) {
	    assertEquals(control.get(k), test.get(k));
	}

	assertEquals(control.size(), test.size());
	
        txn.commit();
    }


    @Test public void testPutAndRemoveOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
	Map<Integer,Integer> control = new HashMap<Integer,Integer>();

	for (int i = 0; i < 32; ++i) {

	    test.put(i, i);
	    test.put(~i, ~i);
	    control.put(i,i);
	    control.put(~i, ~i);
	}

	for (int i = 0; i < 128; i += 2) {
	    test.remove(i);
	    control.remove(i);
	}

	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testPutAndRemoveOnSplitTree2() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,8);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,15);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,5);
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

    @Test public void testNullPut() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(1f,15);
	Map<String,Integer> control = new HashMap<String,Integer>();

	test.put(null, 0);
	control.put(null, 0);
	
	assertEquals(control, test);

	txn.commit();
    }

    @Test public void testNullGet() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(1f,15);

	test.put(null, 0);
	assertEquals(new Integer(0), test.get(null));
	
	txn.commit();
    }

    @Test public void testNullRemove() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<String,Integer> test = new PrefixHashMap<String,Integer>(1f,15);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,5);
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
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,5);
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
	
	test.clear();
	assertEquals(0, test.size());

        txn.commit();
    }    


    @Test public void testIteratorOnSplitTree() throws Exception {
        txn = createTransaction();

        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
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
	    
	if (control.size() > 0)
	    throw new Exception("PrefixHashMap.keySet().iterator() failed "
				+ " to enumerate all entries after split()");	
        txn.commit();
    }    

    @Test public void testTreeSizeOnSplitTree() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	// create a tree with an artificially small leaf size
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,5);

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
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);

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
	PrefixHashMap<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
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
	    	    
	    if (test.get(i) != null) 
		throw new Exception("PrefixHashMap.get() returned non-null "
				    + "value for an invalid key");
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
	    
	if (control.size() > 0)
	    throw new Exception("PrefixHashMap.keySet().iterator() failed "
				+ " to enumerate all entries");
	
	txn.commit();
    }

    @Test public void testKeyIteratorOnSplitMap() throws Exception {
        txn = createTransaction();
        DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
	Set<Integer> control = new HashSet<Integer>();


	// get from outside the range of the put
	for (int i = 0; i < 33; ++i) {
	    test.put(i,i);
	    control.add(i);
	}

	for (Integer i : test.keySet()) {
	    control.remove(i);
	}
	    
	if (control.size() > 0)
	    throw new Exception("PrefixHashMap.keySet().iterator() failed "
				+ " to enumerate all entries");
	
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

	if (control.size() > 0)
	    throw new Exception("PrefixHashMap.values().iterator() failed "
				+ " to enumerate all entries");
		
	txn.commit();
    }
    
    @Test public void testValuesIteratorOnSplitMap() throws Exception {
	txn = createTransaction();
	DataManager dataManager = AppContext.getDataManager();
	Map<Integer,Integer> test = new PrefixHashMap<Integer,Integer>(1f,4);
	Set<Integer> control = new HashSet<Integer>();
	
	
	// get from outside the range of the put
	for (int i = 0; i < 33; ++i) {
	    test.put(i,i);
	    control.add(i);
	}
	
	for (Integer i : test.values()) {
	    control.remove(i);
	}	    

	if (control.size() > 0)
	    throw new Exception("PrefixHashMap.values().iterator() failed "
				+ " to enumerate all entries");
		
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
	    
	    if (test.remove(i) != null) 
		throw new Exception("PrefixHashMap.get() returned non-null "
				    + "value for an invalid key");
	}
	
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
