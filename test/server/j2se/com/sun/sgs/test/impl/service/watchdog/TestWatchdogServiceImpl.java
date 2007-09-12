/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.TestCase;

public class TestWatchdogServiceImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
	"com.sun.sgs.impl.service.watchdog.server";
    
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
	"TestWatchdogServiceImpl.db";

    /** The port for the watchdog server. */
    private static int WATCHDOG_PORT = 0;

    /** The renew interval for the watchdog server. */
    private static long RENEW_INTERVAL = 500;

    /** Properties for the watchdog server and data service. */
    private static Properties serviceProps = createProperties(
	StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	WatchdogServerPropertyPrefix + ".start", "true",
	WatchdogServerPropertyPrefix + ".port", Integer.toString(WATCHDOG_PORT),
	WatchdogServerPropertyPrefix + ".renew.interval",
	    Long.toString(RENEW_INTERVAL));

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

	//identityManager = new DummyIdentityManager();
	//systemRegistry.setComponent(IdentityManager.class, identityManager);

	// create data service
	dataService = createDataService(systemRegistry);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// create watchdog service
	watchdogService = new WatchdogServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
	txnProxy.setComponent(WatchdogService.class, watchdogService);
	txnProxy.setComponent(WatchdogServiceImpl.class, watchdogService);
	serviceRegistry.setComponent(WatchdogService.class, watchdogService);
	serviceRegistry.setComponent(
	    WatchdogServiceImpl.class, watchdogService);

	// services ready
	dataService.ready();
	watchdogService.ready();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
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
	if (watchdogService != null) {
	    watchdogService.shutdown();
	    watchdogService = null;
	}
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
        MinimalTestKernel.destroyContext(appContext);
    }

    /* -- Test constructor -- */
    
    public void testConstructor() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	WatchdogServerImpl server = watchdog.getServer();
	System.err.println("watchdog server: " + server);
	server.shutdown();
    }

    public void testConstructorNullProperties() throws Exception {
	try {
	    new WatchdogServiceImpl(null, systemRegistry, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullRegistry() throws Exception {
	try {
	    new WatchdogServiceImpl(serviceProps, null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullProxy() throws Exception {
	try {
	    new WatchdogServiceImpl(serviceProps, systemRegistry, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	Properties properties = createProperties(
	    WatchdogServerPropertyPrefix + ".port",
	    Integer.toString(WATCHDOG_PORT));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorNegativePort() throws Exception {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(-1));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorPortTooLarge() throws Exception {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(65536));
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorStartServerRenewIntervalTooSmall()
	throws Exception
    {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "0");
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorStartServerRenewIntervalTooLarge()
	throws Exception
    {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "10001");
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getLocalNodeId -- */

    public void testGetLocalNodeId() throws Exception {
	long id = watchdogService.getLocalNodeId();
	if (id != 1) {
	    fail("Expected id 1, got " + id);
	}
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	try {
	    id = watchdog.getLocalNodeId();
	    if (id != 2) {
		fail("Expected id 2, got " + id);
	    }
	} finally {
	    watchdog.shutdown();
	}
    }

    public void testGetLocalNodeIdServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	try {
	    watchdog.getLocalNodeId();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test isLocalNodeAlive -- */

    public void testIsLocalNodeAlive() throws Exception {
	createTransaction();
	if (! watchdogService.isLocalNodeAlive()) {
	    fail("Expected watchdogService.isLocalNodeAlive() to return true");
	}
	commitTransaction();

	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	try {
	    createTransaction();
	    if (! watchdog.isLocalNodeAlive()) {
		fail("Expected watchdog.isLocalNodeAlive() to return true");
	    }
	    commitTransaction();
	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.currentThread().sleep(RENEW_INTERVAL * 4);
	    createTransaction();
	    if (watchdog.isLocalNodeAlive()) {
		fail("Expected watchdog.isLocalNodeAlive() to return false");
	    }
	    commitTransaction();
	    
	} finally {
	    watchdog.shutdown();
	}
    }

    public void testIsLocalNodeAliveServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	try {
	    watchdog.isLocalNodeAlive();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testIsLocalNodeAliveNoTransaction() throws Exception {
	try {
	    watchdogService.isLocalNodeAlive();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* -- Test isLocalNodeAliveNonTransactional -- */

    public void testIsLocalNodeAliveNonTransactional() throws Exception {
	if (! watchdogService.isLocalNodeAliveNonTransactional()) {
	    fail("Expected watchdogService.isLocalNodeAlive" +
		 "NonTransactional() to return true");
	}

	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	try {
	    if (! watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return true");
	    }
	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.currentThread().sleep(RENEW_INTERVAL * 4);
	    if (watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return false");
	    }
	    
	} finally {
	    watchdog.shutdown();
	}
    }

    public void testIsLocalNodeAliveNonTransactionalServiceShuttingDown()
	throws Exception
    {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	try {
	    watchdog.isLocalNodeAliveNonTransactional();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testIsLocalNodeAliveNonTransactionalNoTransaction()
	throws Exception
    {
	try {
	    watchdogService.isLocalNodeAliveNonTransactional();
	} catch (TransactionNotActiveException e) {
	    fail("caught TransactionNotActiveException!");
	}
    }

    /* -- Test getNodes -- */

    public void testGetNodes() throws Exception {
	Set<WatchdogServiceImpl> watchdogs = new HashSet<WatchdogServiceImpl>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	try {
	    for (int i = 0; i < 5; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdogs.add(watchdog);
		System.err.println("watchdog service id: " +
				   watchdog.getLocalNodeId());
	    }
	    // ensure that watchdogs have a chance to register
	    Thread.currentThread().sleep(RENEW_INTERVAL);
	    createTransaction();
	    Iterator<Node> iter = watchdogService.getNodes();
	    int numNodes = 0;
	    while (iter.hasNext()) {
		Node node = iter.next();
		System.err.println(node);
		numNodes++;
	    }
	    commitTransaction();
	    int expectedNodes = watchdogs.size() + 1;
	    if (numNodes != expectedNodes) {
		fail("Expected " + expectedNodes +
		     " watchdogs, got " + numNodes);
	    }
	} finally {

	    for (WatchdogServiceImpl watchdog : watchdogs) {
		watchdog.shutdown();
	    }
	}
    }

    public void testGetNodesServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	createTransaction();
	try {
	    watchdog.getNodes();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetNodesNoTransaction() throws Exception {
	try {
	    watchdogService.getNodes();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getNode -- */

    public void testGetNode() throws Exception {
	Set<WatchdogServiceImpl> watchdogs = new HashSet<WatchdogServiceImpl>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	try {
	    for (int i = 0; i < 5; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdogs.add(watchdog);
	    }
	    for (WatchdogServiceImpl watchdog : watchdogs) {
		long id = watchdog.getLocalNodeId();
		createTransaction();
		Node node = watchdogService.getNode(id);
		if (node == null) {
		    fail("Expected node for ID " + id + " got " +  node);
		}
		System.err.println(node);
		if (id != node.getId()) {
		    fail("Expected node ID " + id + " got, " + node.getId());
		} else if (! node.isAlive()) {
		    fail("Node " + id + " is not alive!");
		}
		commitTransaction();
	    }
	    
	} finally {

	    for (WatchdogServiceImpl watchdog : watchdogs) {
		watchdog.shutdown();
	    }
	}
    }

    public void testGetNodeServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	createTransaction();
	try {
	    watchdog.getNode(0);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetNodeNoTransaction() throws Exception {
	try {
	    watchdogService.getNode(0);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetNodeNonexistentNode() throws Exception {
	createTransaction();
	Node node = watchdogService.getNode(29);
	System.err.println(node);
	if (node != null) {
	    fail("Expected null node, got " + node);
	}
    }

    /* -- Test addNodeListener -- */
    
    public void testAddNodeListenerServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	try {
	    watchdog.addNodeListener(new DummyNodeListener());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAddNodeListenerNullListener() throws Exception {
	try {
	    watchdogService.addNodeListener(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testAddNodeListenerNodeStarted() throws Exception {
	Set<WatchdogServiceImpl> watchdogs = new HashSet<WatchdogServiceImpl>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);

	try {
	    for (int i = 0; i < 5; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdogs.add(watchdog);
	    }
	    // wait for all nodes to get notified...
	    Thread.currentThread().sleep(RENEW_INTERVAL * 4);

	    Set<Node> nodes = listener.getStartedNodes();
	    System.err.println("startedNodes: " + nodes);
	    if (nodes.size() != 5) {
		fail("Expected 5 started nodes, got " + nodes.size());
	    }
	    for (Node node : nodes) {
		System.err.println(node);
		if (!node.isAlive()) {
		    fail("Node " + node.getId() + " is not alive!");
		}
	    }
	    
	} finally {
	    // shutdown nodes...
	    for (WatchdogServiceImpl watchdog : watchdogs) {
		watchdog.shutdown();
	    }
	}
    }

    public void testAddNodeListenerNodeFailed() throws Exception {
	Set<WatchdogServiceImpl> watchdogs = new HashSet<WatchdogServiceImpl>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
	
	for (int i = 0; i < 5; i++) {
	    WatchdogServiceImpl watchdog =
		new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	    watchdogs.add(watchdog);
	}
	for (WatchdogServiceImpl watchdog : watchdogs) {
	    long id = watchdog.getLocalNodeId();
	    createTransaction();
	    Node node = watchdogService.getNode(id);
	    if (node == null) {
		fail("Expected node for ID " + id + " got " +  node);
	    }
	    System.err.println(node);
	    if (id != node.getId()) {
		fail("Expected node ID " + id + " got, " + node.getId());
	    } else if (! node.isAlive()) {
		fail("Node " + id + " is not alive!");
	    }
	    commitTransaction();
	}
	// shutdown nodes...
	for (WatchdogServiceImpl watchdog : watchdogs) {
	    watchdog.shutdown();
	}

	// wait for all nodes to fail...
	Thread.currentThread().sleep(RENEW_INTERVAL * 4);

	Set<Node> nodes = listener.getFailedNodes();
	System.err.println("failedNodes: " + nodes);
	if (nodes.size() != 5) {
	    fail("Expected 5 failed nodes, got " + nodes.size());
	}
	for (Node node : nodes) {
	    System.err.println(node);
	    if (node.isAlive()) {
		fail("Node " + node.getId() + " is alive!");
	    }
	}
    }

    /* -- test shutdown -- */

    public void testShutdownAndNotifyFailedNodes() throws Exception {
	Map<WatchdogServiceImpl, DummyNodeListener> watchdogMap =
	    new HashMap<WatchdogServiceImpl, DummyNodeListener>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	try {
	    for (int i = 0; i < 5; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		DummyNodeListener listener = new DummyNodeListener();
		watchdog.addNodeListener(listener);
		watchdogMap.put(watchdog, listener);
	    }
	
	    // shutdown watchdog server
	    watchdogService.shutdown();

	    for (WatchdogServiceImpl watchdog : watchdogMap.keySet()) {
		DummyNodeListener listener = watchdogMap.get(watchdog);
		Set<Node> nodes = listener.getFailedNodes();
		System.err.println(
		    "failedNodes for " + watchdog.getLocalNodeId() +
		    ": " + nodes);
		if (nodes.size() != 6) {
		    fail("Expected 6 failed nodes, got " + nodes.size());
		}
		for (Node node : nodes) {
		    System.err.println(node);
		    if (node.isAlive()) {
			fail("Node " + node.getId() + " is alive!");
		    }
		}
	    }
	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogMap.keySet()) {
		watchdog.shutdown();
	    }
	}
    }

    /* -- test addRecoveryListener -- */
    
    public void testAddRecoveryListenerServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
	try {
	    watchdog.addRecoveryListener(new DummyRecoveryListener());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAddRecoveryListenerNullListener() throws Exception {
	try {
	    watchdogService.addRecoveryListener(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRecovery() throws Exception {
	List<WatchdogServiceImpl> watchdogs =
	    new ArrayList<WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	int totalWatchdogs = 5;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdog.addRecoveryListener(listener);
		watchdog.ready();
		watchdogs.add(watchdog);
	    }

	    // shut down a few watchdog services
	    for (int i = 0; i < numWatchdogsToShutdown; i++) {
		WatchdogServiceImpl watchdog = watchdogs.get(i);
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
		watchdogs.remove(watchdog);
	    }

	    // pause for watchdog server to detect failure and
	    // send notifications
	    Thread.sleep(3 * RENEW_INTERVAL);
	    if (listener.nodes.size() != shutdownIds.size()) {
		fail("Expected " + shutdownIds.size() + " recover requests, " +
		     "received: " + listener.nodes.size());
	    }

	    createTransaction();
	    System.err.println("Get shutdown nodes (should be marked failed)");
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node == null) {
		    fail("Node removed before recovery complete: " + id);
		}
		if (node.isAlive()) {
		    fail("Node not marked as failed: " + id);
		}
	    }
	    commitTransaction();

	    for (RecoveryCompleteFuture future : listener.nodes.values()) {
		future.done();
	    }
	    
	    createTransaction();
	    System.err.println("Get shutdown nodes (should be removed)...");
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node != null) {
		    fail("Expected node to be removed: " + node);
		}
	    }

	    System.err.println("Get live nodes...");
	    for (WatchdogServiceImpl watchdog : watchdogs) {
		Node node = watchdogService.getNode(watchdog.getLocalNodeId());
		System.err.println("node: " + node);
		if (node == null || !node.isAlive()) {
		    fail("Expected alive node");
		}
	    }

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs) {
		watchdog.shutdown();
	    }
	}
    }

    public void testRecoveryWithBackupFailureDuringRecovery() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	int totalWatchdogs = 8;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdog.addRecoveryListener(listener);
		watchdog.ready();
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down a few watchdog services
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		if (numWatchdogsToShutdown == 0) {
		    break;
		}
		numWatchdogsToShutdown--;
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
		watchdogs.remove(id);
	    }

	    // pause for watchdog server to detect failure and
	    // send notifications
	    Thread.sleep(3 * RENEW_INTERVAL);
	    if (listener.nodes.size() != shutdownIds.size()) {
		fail("Expected " + shutdownIds.size() + " recover requests, " +
		     "received: " + listener.nodes.size());
	    }

	    System.err.println("Get shutdown nodes (should be marked failed)");
	    List<Node> backups = new ArrayList<Node>();
	    createTransaction();
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node == null) {
		    fail("Node removed before recovery complete: " + id);
		}
		if (node.isAlive()) {
		    fail("Node not marked as failed: " + id);
		}
		Node backup = watchdogService.getBackup(id);
		if (backup == null) {
		    fail("backup not assigned for failed node: " + id);
		} else {
		    backups.add(backup);
		}
	    }
	    commitTransaction();

	    // shutdown backups
	    for (Node backup : backups) {
		long backupId = backup.getId();
		WatchdogServiceImpl watchdog = watchdogs.get(backupId);
		if (watchdog != null) {
		    System.err.println("shutting down backup: " + backupId);
		    shutdownIds.add(backupId);
		    watchdog.shutdown();
		    watchdogs.remove(backupId);
		}
	    }

	    Thread.sleep(3 * RENEW_INTERVAL);
	    if (listener.nodes.size() != shutdownIds.size()) {
		fail("Expected " + shutdownIds.size() + " recover requests, " +
		     "received: " + listener.nodes.size());
	    }
	    
	    for (RecoveryCompleteFuture future : listener.nodes.values()) {
		future.done();
	    }
	    
	    createTransaction();
	    System.err.println("Get shutdown nodes (should be removed)...");
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node != null) {
		    fail("Expected node to be removed: " + node);
		}
	    }

	    System.err.println("Get live nodes...");
	    for (long id : watchdogs.keySet()) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "): " + node);
		if (node == null || !node.isAlive()) {
		    fail("Expected alive node");
		}
	    }

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }
    
    public void testRecoveryWithDelayedBackupAssignment() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
		watchdog.addRecoveryListener(listener);
		watchdog.ready();
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down all watchdog services.
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
	    }

	    watchdogs.clear();

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(3 * RENEW_INTERVAL);
	    if (listener.nodes.size() != shutdownIds.size()) {
		fail("Expected " + shutdownIds.size() + " recover requests, " +
		     "received: " + listener.nodes.size());
	    }

	    System.err.println("Get shutdown nodes (should be marked failed)");
	    createTransaction();
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node == null) {
		    fail("Node removed before recovery complete: " + id);
		}
		if (node.isAlive()) {
		    fail("Node not marked as failed: " + id);
		}
		Node backup = watchdogService.getBackup(id);
		if (backup != null) {
		    fail("failed node (" + id + ") assigned backup: " +
			 backup);
		}
	    }
	    commitTransaction();

	    WatchdogServiceImpl watchdog = 
		new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	    watchdog.addRecoveryListener(listener);
	    watchdog.ready();
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    // pause for watchdog server to reassign new node as
	    // backup to exising nodes.
	    
	    Thread.sleep(3 * RENEW_INTERVAL);
	    if (listener.nodes.size() != shutdownIds.size()) {
		fail("Expected " + shutdownIds.size() + " recover requests, " +
		     "received: " + listener.nodes.size());
	    }
	    
	    for (RecoveryCompleteFuture future : listener.nodes.values()) {
		future.done();
	    }
	    
	    createTransaction();
	    System.err.println("Get shutdown nodes (should be removed)...");
	    for (long id : shutdownIds) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "):" +
				   (node == null ? "(removed)" : node));
		if (node != null) {
		    fail("Expected node to be removed: " + node);
		}
	    }

	    System.err.println("Get live nodes...");
	    for (long id : watchdogs.keySet()) {
		Node node = watchdogService.getNode(id);
		System.err.println("node (" + id + "): " + node);
		if (node == null || !node.isAlive()) {
		    fail("Expected alive node");
		}
	    }

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }
    
    private static class DummyRecoveryListener implements RecoveryListener {

	private final Map<Node, RecoveryCompleteFuture> nodes =
	    Collections.synchronizedMap(
		new HashMap<Node, RecoveryCompleteFuture>());

	DummyRecoveryListener() {}

	public void recover(Node node, RecoveryCompleteFuture future) {
	    if (node == null) {
		System.err.println("DummyRecoveryListener.recover: null node");
		return;
	    } else if (future == null) {
		System.err.println(
		    "DummyRecoveryListener.recover: null future");
		return;
	    }
	    
	    if (nodes.get(node) == null) {
		System.err.println(
		    "DummyRecoveryListener.recover: adding node: " + node);
	    } else {
		System.err.println(
		    "DummyRecoveryListener.recover: REPLACING node: " + node);
	    }
	    nodes.put(node, future);
	}

	void recoverDone(Node node) {
	    nodes.get(node).done();
	}
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
	return new DataServiceImpl(dbProps, registry, txnProxy);
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

    private static class DummyNodeListener implements NodeListener {

	private final Set<Node> failedNodes = new HashSet<Node>();
	private final Set<Node> startedNodes = new HashSet<Node>();
	

	public void nodeStarted(Node node) {
	    startedNodes.add(node);
	}

	public void nodeFailed(Node node) {
	    failedNodes.add(node);
	}

	Set<Node> getFailedNodes() {
	    return failedNodes;
	}

	Set<Node> getStartedNodes() {
	    return startedNodes;
	}
    }
}
