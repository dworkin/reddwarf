package com.sun.sgs.test.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.util.ScalableManagedHashMap;
import com.sun.sgs.app.util.SimpleManagedHashMap;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

public class DataMapStressTest extends BasicMapStressTest {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestStress.db";
    
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
	DataServiceImplClassName + ".detectModifications", "true");

    /** Whether to do logging, which is otherwise disabled. */
    private static boolean doLogging = Boolean.getBoolean("test.doLogging");

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

    /** Creates the test. */
    public DataMapStressTest(String name, Map<String, Object> map) {
	super(name, map);
    }

    /**
     * Prints the test case, initializes the data manager, and creates and
     * binds a managed object.
     */
    protected void setUp() throws Exception {
	super.setUp();
	if (!doLogging) {
	    /* Disable logging */
	    for (Enumeration<String> loggerNames =
		     LogManager.getLogManager().getLoggerNames();
		 loggerNames.hasMoreElements(); )
	    {
		String loggerName = loggerNames.nextElement();
		Logger.getLogger(loggerName).setLevel(Level.WARNING);
	    }
	}
	createTransaction(true);
	DataServiceImpl service = getDataServiceImpl();
	service.configure(componentRegistry, txnProxy);
	txn.commit();
	dataManager = service;
	ScalableManagedHashMap.dataManager = dataManager;
	SimpleManagedHashMap.dataManager = dataManager;
	createTransaction();
	dummy = new DummyManagedObject(service);
	dataManager.setBinding("dummy", dummy);
	dataManager.setBinding("map", (ManagedObject) map);
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
	if (!doLogging) {
	    LogManager.getLogManager().readConfiguration();
	}
    }

    /* -- Other tests -- */

    void beforeOp(int i) {
	if (i > 0 && i % 1000 == 0) {
	    System.err.println(i);
	}
	if (random.nextInt(10) == 0) {
	    try {
		txn.commit();
	    } catch (Exception e) {
		fail("Unexpected exception: " + e);
	    }
	    createTransaction(true);
	    @SuppressWarnings("unchecked")
		Map<String, Object> m =
		dataManager.getBinding("map", Map.class);
	    map = m;
	}
    }

    Object createValue(int n) {
	if ((n % 7) > 4) {
	    return new DummyManagedObject(dataManager, n);
	} else {
	    return super.createValue(n);
	}
    }

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

    /** Creates a new transaction. */
    DummyTransaction createTransaction(boolean usePrepareAndCommit) {
	txn = new DummyTransaction(usePrepareAndCommit);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
}
