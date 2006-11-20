package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.util.ManagedHashMap;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/** Test the ManagedHashMap class */
public class TestManagedHashMap extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataServiceImpl.db";
    
    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory,
	"com.sun.sgs.appName", "TestDataServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "0");

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    String directory;

    /** A transaction proxy. */
    DummyTransactionProxy txnProxy = new DummyTransactionProxy();

    /** A component registry. */
    DummyComponentRegistry componentRegistry = new DummyComponentRegistry();

    /** An initial, open transaction. */
    DummyTransaction txn;

    /** A data manager. */
    DataManager dataManager;

    /** A managed object. */
    DummyManagedObject dummy;

    /** A managed hash map to test. */
    ManagedHashMap<String, Object> map;

    /** Creates the test. */
    public TestManagedHashMap(String name) {
	super(name);
    }

    /**
     * Prints the test case, initializes the data manager, and creates and
     * binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	createTransaction();
	DataServiceImpl service = getDataServiceImpl();
	service.configure(componentRegistry, txnProxy);
	txn.commit();
	dataManager = service;
	ManagedHashMap.setDataManager(dataManager);
	createTransaction();
	dummy = new DummyManagedObject(service);
	dataManager.setBinding("dummy", dummy);
	map = new ManagedHashMap<String, Object>();
	dataManager.setBinding("map", map);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Deletes the directory if the test passes and the directory was
     * created, and aborts the current transaction.
     */
    protected void tearDown() throws Exception {
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
	if (txn != null) {
	    txn.abort();
	    txn = null;
	}
    }

    /* -- Test get -- */

    public void testGetNullKey() throws Exception {
	map.put(null, new Integer(3));
	assertEquals(new Integer(3), map.get(null));
	txn.commit();
	createTransaction();
	map = dataManager.getBinding("map", ManagedHashMap.class);
	assertEquals(new Integer(3), map.get(null));	
	map.put(null, dummy);
	assertEquals(dummy, map.get(null));
	txn.commit();
	createTransaction();
	map = dataManager.getBinding("map", ManagedHashMap.class);
	assertEquals(dummy, map.get(null));	
    }

    public void testGetNonNullKey() throws Exception {
	map.put("k", new Integer(3));
	assertEquals(new Integer(3), map.get("k"));
	txn.commit();
	createTransaction();
	map = dataManager.getBinding("map", ManagedHashMap.class);
	assertEquals(new Integer(3), map.get("k"));
	map.put("k", dummy);
	assertEquals(dummy, map.get("k"));
	txn.commit();
	createTransaction();
	map = dataManager.getBinding("map", ManagedHashMap.class);
	assertEquals(dummy, map.get("k"));	
    }

    /* -- Other tests -- */

    /* -- Other methods and classes -- */

    /** Creates a per-test directory. */
    String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	directory = dir.getPath();
	return directory;
    }

    /** Deletes the specified directory, if it exists. */
    static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }

    /** Creates a property list with the specified keys and values. */
    static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Returns a DataServiceImpl for the shared database. */
    DataServiceImpl getDataServiceImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, componentRegistry);
    }

    /** Creates a new transaction. */
    DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
}
