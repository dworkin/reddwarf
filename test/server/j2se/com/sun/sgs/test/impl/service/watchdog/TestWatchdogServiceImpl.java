/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

public class TestWatchdogServiceImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerClassName =
	WatchdogServerImpl.class.getName();
    
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
	"TestWatchdogServiceImpl.db";

    /** The port for the watchdog server. */
    private static int WATCHDOG_PORT = 0;

    /** Properties for the watchdog server and data service. */
    private static Properties serviceProps = createProperties(
	StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	WatchdogServerClassName + ".start", "true",
	WatchdogServerClassName + ".port", Integer.toString(WATCHDOG_PORT),
	WatchdogServerClassName + ".ping.interval", "500");

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	DB_DIRECTORY,
	StandardProperties.APP_NAME, "TestWatchdogServiceImpl");

    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;
    private DataServiceImpl dataService;
    private TaskServiceImpl taskService;
    private WatchdogServiceImpl watchdogService;

    /** True if test passes. */
    private boolean passed;

    /** Constructs a test instance. */
    public TestWatchdogServiceImpl(String name) {
	super(name);
    }

    /** Test setup. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
	createDirectory(DB_DIRECTORY);
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
	    
	// create services
	dataService = createDataService(systemRegistry);
	taskService = new TaskServiceImpl(new Properties(), systemRegistry);
	watchdogService = new WatchdogServiceImpl(serviceProps, systemRegistry);
	//identityManager = new DummyIdentityManager();
	//systemRegistry.setComponent(IdentityManager.class, identityManager);
	createTransaction(10000);

	// configure data service
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// configure task service
        taskService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);

	// configure watchdog service
	watchdogService.configure(serviceRegistry, txnProxy);
	txnProxy.setComponent(WatchdogService.class, watchdogService);
	txnProxy.setComponent(WatchdogServiceImpl.class, watchdogService);
	serviceRegistry.setComponent(WatchdogService.class, watchdogService);
	serviceRegistry.setComponent(
	    WatchdogServiceImpl.class, watchdogService);
	commitTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
	passed = true;
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (txn != null) {
            try {
                txn.abort(null);
            } catch (IllegalStateException e) {
            }
            txn = null;
        }
        if (taskService != null) {
            taskService.shutdown();
            taskService = null;
        }
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
	if (watchdogService != null) {
	    watchdogService.shutdown();
	    watchdogService = null;
	}
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
        MinimalTestKernel.destroyContext(appContext);
    }

    /* -- Test constructor -- */
    
    public void testConstructor() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry);
	WatchdogServerImpl server = watchdog.getServer();
	System.err.println("watchdog server: " + server);
	server.shutdown();
    }

    public void testConstructorNullProperties() throws Exception {
	try {
	    new WatchdogServiceImpl(null, systemRegistry);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullRegistry() throws Exception {
	try {
	    new WatchdogServiceImpl(serviceProps, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	Properties properties = createProperties(
	    WatchdogServerClassName + ".port", Integer.toString(WATCHDOG_PORT));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorZeroPort() throws Exception {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerClassName + ".port", Integer.toString(0));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorPortTooLarge() throws Exception {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerClassName + ".port", Integer.toString(65536));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorStartServerPingIntervalTooSmall()
	throws Exception
    {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerClassName + ".start", "true",
	    WatchdogServerClassName + ".port", "0",
	    WatchdogServerClassName + ".ping.interval", "0");
	try {
	    new WatchdogServiceImpl(properties, systemRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorStartServerPingIntervalTooLarge()
	throws Exception
    {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerClassName + ".start", "true",
	    WatchdogServerClassName + ".port", "0",
	    WatchdogServerClassName + ".ping.interval", "10001");
	try {
	    new WatchdogServiceImpl(properties, systemRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test configure -- */

    public void testConfigureNullRegistry() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry);
	createTransaction();
	try {
	    watchdog.configure(null, new DummyTransactionProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureNullTransactionProxy() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry);
	createTransaction();
	try {
	    watchdog.configure(new DummyComponentRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureTwice() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry);
	createTransaction();
	watchdog.configure(serviceRegistry, txnProxy);
	try {
	    watchdog.configure(serviceRegistry, txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureAbortConfigure() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry);
	createTransaction();
	watchdog.configure(serviceRegistry, txnProxy);
	abortTransaction(null);
	createTransaction();
	watchdog.configure(serviceRegistry, txnProxy);
	commitTransaction();
    }

    /* -- other methods -- */
    
    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Creates the specified directory, if it does not already exist. */
    private static void createDirectory(String directory) {
	File dir = new File(directory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
    }
    
    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
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

    /**
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(
	DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(DB_DIRECTORY);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, registry);
    }

    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    private DummyTransaction createTransaction() {
	if (txn == null) {
	    txn = new DummyTransaction();
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    /**
     * Creates a new transaction with the specified timeout, and sets
     * transaction proxy's current transaction.
     */
    private DummyTransaction createTransaction(long timeout) {
	if (txn == null) {
	    txn = new DummyTransaction(timeout);
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    private void abortTransaction(Exception e) {
	if (txn != null) {
	    txn.abort(e);
	    txn = null;
	    txnProxy.setCurrentTransaction(null);
	} else {
	    throw new TransactionNotActiveException("txn:" + txn);
	}
    }

    private void commitTransaction() throws Exception {
	if (txn != null) {
	    txn.commit();
	    txn = null;
	    txnProxy.setCurrentTransaction(null);
	} else {
	    throw new TransactionNotActiveException("txn:" + txn);
	}
    }    
}
