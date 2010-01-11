/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.app.profile.ProfileDataManager;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.KernelShutdownController;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.Node.Health;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.Constants;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import static com.sun.sgs.test.util.UtilProperties.createProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link WatchdogServiceImpl} class. */
@RunWith(FilteredNameRunner.class)
public class TestWatchdogServiceImpl extends Assert {

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
	"com.sun.sgs.impl.service.watchdog.server";

    /* The number of additional nodes to create if tests need them */
    private static final int NUM_WATCHDOGS = 5;

    /** The KernelContext class. */
    private static final Class<?> kernelContextClass =
	UtilReflection.getClass("com.sun.sgs.impl.kernel.KernelContext");

    /** The three argument KernelContext constructor. */
    private static final Constructor<?> kernelContextConstructor =
	UtilReflection.getConstructor(
	    kernelContextClass,
	    String.class, ComponentRegistry.class, ComponentRegistry.class);

    /** The ContextResolver.setTaskState method. */
    private static final Method contextResolverSetTaskState =
	UtilReflection.getMethod(
	    UtilReflection.getClass(
		"com.sun.sgs.impl.kernel.ContextResolver"),
	    "setTaskState", kernelContextClass, Identity.class);

    /** The node that creates the servers */
    private SgsTestNode serverNode;

    /** Version information from WatchdogServiceImpl class. */
    private static String VERSION_KEY;
    private static int MAJOR_VERSION;
    private static int MINOR_VERSION;
    
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];

    /** System components found from the serverNode */
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;

    /** Properties for creating a new node. */
    private Properties serviceProps;

    /** A specific property we started with */
    private int renewTime;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The data service for serverNode. */
    private DataService dataService;
    
    /** The watchdog service for serverNode */
    private WatchdogServiceImpl watchdogService;
    
    /** A dummy shutdown controller */
    private static DummyKernelShutdownController dummyShutdownCtrl = 
            new DummyKernelShutdownController();

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }

    /** Constructs a test instance. */
    @BeforeClass public static void setUpClass() throws Exception {
	Class cl = WatchdogServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Test setup. */
    @Before public void setUp() throws Exception {
        dummyShutdownCtrl.reset();
        Properties props = new Properties();
        setUp(null, true);
    }

    protected void setUp(Properties props, boolean clean) throws Exception {
	
        serverNode = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, clean);
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
	serviceProps = SgsTestNode.getDefaultProperties(
	    "TestWatchdogServiceImpl", serverNode, null);
        renewTime = Integer.valueOf(
            serverNode.getServiceProperties().getProperty(
                "com.sun.sgs.impl.service.watchdog.server.renew.interval"));

        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
	dataService = serverNode.getDataService();
        watchdogService = (WatchdogServiceImpl) serverNode.getWatchdogService();
    }

    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     * @parm num the number of nodes to add
     */
    private void addNodes(Properties props, int num) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[num];

        for (int i = 0; i < num; i++) {
            SgsTestNode node = new SgsTestNode(serverNode, null, props); 
            additionalNodes[i] = node;
            System.err.println("watchdog service id: " + node.getNodeId());
        }
    }

    /** Shut down the nodes. */
    @After public void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        if (serverNode != null)
            serverNode.shutdown(clean);
	/* Wait for sockets to close down. */
	Thread.sleep(100);
    }

    /* -- Test constructor -- */

    @Test public void testConstructor() throws Exception {
	DataService dataService = null;
        WatchdogServiceImpl watchdog = null;
        try {
	    dataService = createDataService(serviceProps);
            watchdog = new WatchdogServiceImpl(
		serviceProps, systemRegistry, txnProxy, dummyShutdownCtrl);  
        } finally {
	    if (dataService != null) dataService.shutdown();
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullProperties() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(null, systemRegistry, txnProxy,
					       dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRegistry() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(serviceProps, null, txnProxy,
					       dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullProxy() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog =
                    new WatchdogServiceImpl(serviceProps, systemRegistry, null,
					    dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }
    
    @Test(expected = NullPointerException.class)
    public void testConstructorNullShutdownCtrl() throws Exception {
        WatchdogServiceImpl watchdog = null;
        try {
            watchdog = new WatchdogServiceImpl(serviceProps, systemRegistry,
					       txnProxy, null);
        } finally {
            if (watchdog != null) {
                watchdog.shutdown();
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNoAppName() throws Exception {
        Properties properties = createProperties(
            WatchdogServerPropertyPrefix + ".port", "0");
	new WatchdogServiceImpl(properties, systemRegistry, txnProxy, 
				dummyShutdownCtrl);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorAppButNoServerHost() throws Exception {
        // Server start is false but we didn't specify a server host
        int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	new WatchdogServiceImpl(props, systemRegistry, txnProxy,
				dummyShutdownCtrl);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNegativePort() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(-1));
	try {
	    watchdog = 
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorPortTooLarge() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(65536));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorStartServerRenewIntervalTooSmall()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.coreServerNode.name(),
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "0");
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test public void testConstructorStartServerWithLargeRenewInterval()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.coreServerNode.name(),
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval",
		Integer.toString(Integer.MAX_VALUE));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			dataService.getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }
    
    @Test public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, dummyShutdownCtrl);  
	watchdog.shutdown();
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMajorVersionMismatch()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				dummyShutdownCtrl);  
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMinorVersionMismatch()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				dummyShutdownCtrl);  
    }

    /* -- Test getLocalNodeHealth -- */

    @Test public void testGetLocalNodeHealth() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                checkHealth(watchdogService, Health.GREEN);
            }
        }, taskOwner);

	DataService dataService = createDataService(serviceProps);
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    checkHealth(watchdogService, Health.GREEN);
                }
            }, taskOwner);

	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.sleep(renewTime * 4);

            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    checkHealth(watchdog, Health.RED);
                }
            }, taskOwner);

	} finally {
	    watchdog.shutdown();
	    dataService.shutdown();
	}
    }

    @Test(expected = IllegalStateException.class)
    public void testGetLocalNodeHealthServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.getLocalNodeHealth();
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testGetLocalNodeHealthNoTransaction() throws Exception {
	watchdogService.getLocalNodeHealth();
    }

    /* -- Test isLocalNodeAlive -- */

    @Test public void testIsLocalNodeAlive() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                if (! watchdogService.isLocalNodeAlive()) {
                    fail("Expected watchdogService.isLocalNodeAlive() " +
                          "to return true");
                }
            }
        }, taskOwner);

	DataService dataService = createDataService(serviceProps);
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    if (! watchdogService.isLocalNodeAlive()) {
                        fail("Expected watchdogService.isLocalNodeAlive() " +
                             "to return true");
                    }
                }
            }, taskOwner);

	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.sleep(renewTime * 4);

            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    if (watchdog.isLocalNodeAlive()) {
                        fail("Expected watchdogService.isLocalNodeAlive() " +
                             "to return false");
                    }
                }
            }, taskOwner);
	    
	} finally {
	    watchdog.shutdown();
	    dataService.shutdown();
	}
    }

    @Test(expected = IllegalStateException.class)
    public void testIsLocalNodeAliveServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.isLocalNodeAlive();
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testIsLocalNodeAliveNoTransaction() throws Exception {
	watchdogService.isLocalNodeAlive();
    }

    /* -- Test isLocalNodeAliveNonTransactional -- */

    @Test public void testIsLocalNodeAliveNonTransactional() throws Exception {
	if (! watchdogService.isLocalNodeAliveNonTransactional()) {
	    fail("Expected watchdogService.isLocalNodeAlive" +
		 "NonTransactional() to return true");
	}

	int port = watchdogService.getServer().getPort();
	DataService dataService = createDataService(serviceProps);
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
	    if (! watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return true");
	    }
	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.sleep(renewTime * 4);
	    if (watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return false");
	    }
	    
	} finally {
	    dataService.shutdown();
	    watchdog.shutdown();
	}
    }

    @Test(expected = IllegalStateException.class)
    public void testIsLocalNodeAliveNonTransactionalServiceShuttingDown()
	throws Exception
    {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.isLocalNodeAliveNonTransactional();
    }

    @Test public void testIsLocalNodeAliveNonTransactionalNoTransaction() {
	assertTrue(watchdogService.isLocalNodeAliveNonTransactional());
    }
    
    @Test public void testIsLocalNodeAliveNonTransactionalInTransaction()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		watchdogService.isLocalNodeAliveNonTransactional();
	    }}, taskOwner);
    }

    /* -- Test reportHealth -- */

    @Test public void testReportHealth() throws Exception {
        final long nodeId = serverNode.getDataService().getLocalNodeId();
        
        checkHealth(watchdogService, Health.GREEN);
        watchdogService.reportHealth(nodeId, Health.YELLOW, "A");
        checkHealth(watchdogService, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.ORANGE, "B");
        checkHealth(watchdogService, Health.ORANGE);
        watchdogService.reportHealth(nodeId, Health.ORANGE, "C");
        checkHealth(watchdogService, Health.ORANGE);
        watchdogService.reportHealth(nodeId, Health.GREEN, "B");
        checkHealth(watchdogService, Health.ORANGE);
        watchdogService.reportHealth(nodeId, Health.YELLOW, "C");
        checkHealth(watchdogService, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        checkHealth(watchdogService, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.GREEN, "C");
        checkHealth(watchdogService, Health.GREEN);
    }

    @Test public void testReportFailedHealth() throws Exception {
        final long nodeId = serverNode.getDataService().getLocalNodeId();

        checkHealth(watchdogService, Health.GREEN);
        watchdogService.reportHealth(nodeId, Health.RED, "A");
        checkHealth(watchdogService, Health.RED);
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        checkHealth(watchdogService, Health.RED);
    }

    // Utility method to check that that the reported health matches the
    // expected health value
    private void checkHealth(final WatchdogService watchdog,
                             final Health expected)
        throws Exception
    {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                Health health = watchdog.getLocalNodeHealth();

                if (health == null) {
                    fail("Expected WatchdogService.getLocalNodeHealth() " +
                          "to return non-null health");
                }
                if (!health.equals(expected)) {
                    fail("Expected WatchdogService.getLocalNodeHealth() " +
                          "to return: " + expected +
                          ", instead received: " + health);
                }
            }
        }, taskOwner);
    }

    /* -- Test getNodes -- */

    @IntegrationTest
    @Test public void testGetNodes() throws Exception {
        addNodes(null, NUM_WATCHDOGS);
        Thread.sleep(renewTime);
        CountNodesTask task = new CountNodesTask();
        txnScheduler.runTask(task, taskOwner);
        int numNodes = task.numNodes;

        int expectedNodes = NUM_WATCHDOGS + 1;
        if (numNodes != expectedNodes) {
            fail("Expected " + expectedNodes +
                 " watchdogs, got " + numNodes);
        }
    }

    /** 
     * Task to count the number of nodes.
     */
    private class CountNodesTask extends TestAbstractKernelRunnable {
        int numNodes;
        public void run() {
            Iterator<Node> iter = watchdogService.getNodes();
            numNodes = 0;
            while (iter.hasNext()) {
                Node node = iter.next();
                System.err.println(node);
                numNodes++;
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNodesServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
		    watchdog.getNodes();
                }
            }, taskOwner);
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testGetNodesNoTransaction() throws Exception {
	watchdogService.getNodes();
    }

    /* -- Test getNode -- */

    @IntegrationTest
    @Test public void testGetNode() throws Exception {
        addNodes(null, NUM_WATCHDOGS);

        for (SgsTestNode node : additionalNodes) {
            final long id = node.getDataService().getLocalNodeId();
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = watchdogService.getNode(id);
                    if (node == null) {
                        fail("Expected node for ID " + id + " got " +  node);
                    }
                    System.err.println(node);
                    if (id != node.getId()) {
                        fail("Expected node ID " + id + 
                                " got, " + node.getId());
                    } else if (! node.isAlive()) {
                        fail("Node " + id + " is not alive!");
                    }
                }
            }, taskOwner);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNodeServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
		    watchdog.getNode(0);
                }
            }, taskOwner);
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testGetNodeNoTransaction() throws Exception {
	watchdogService.getNode(0);
    }

    @Test public void testGetNodeNonexistentNode() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                Node node = watchdogService.getNode(29);
                System.err.println(node);
                if (node != null) {
                    fail("Expected null node, got " + node);
                }
            }
        }, taskOwner);
    }

    /* -- Test addNodeListener -- */

    @Test(expected = IllegalStateException.class)
    public void testAddNodeListenerServiceShuttingDown()
	throws Exception
    {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdog.addNodeListener(new DummyNodeListener());
            }
        }, taskOwner);
    }

    @Test(expected = NullPointerException.class)
    public void testAddNodeListenerNullListener() throws Exception {
	watchdogService.addNodeListener(null);
    }

    @Test(expected = IllegalStateException.class)
    public void TestAddNodeListenerInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdogService.addNodeListener(new DummyNodeListener());
            } }, taskOwner);
    }

    @IntegrationTest
    @Test public void testAddNodeListenerNodeStarted() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        addNodes(null, NUM_WATCHDOGS);

        // wait for all nodes to get notified...
        Thread.sleep(renewTime * 4);

        Set<Node> nodes = listener.getStartedNodes();
        System.err.println("startedNodes: " + nodes);
        if (nodes.size() != NUM_WATCHDOGS) {
            fail("Expected " + NUM_WATCHDOGS + " started nodes, got " + 
                    nodes.size());
        }
        for (Node node : nodes) {
            System.err.println(node);
            if (!node.isAlive()) {
                fail("Node " + node.getId() + " is not alive!");
            }
        }
    }

    @IntegrationTest
    @Test public void testAddNodeListenerNodeFailed() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        addNodes(null, NUM_WATCHDOGS);
        for (SgsTestNode node : additionalNodes) {
            final long id = node.getDataService().getLocalNodeId();
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = watchdogService.getNode(id);
                    if (node == null) {
                        fail("Expected node for ID " + id + " got " +  node);
                    }
                    System.err.println(node);
                    if (id != node.getId()) {
                        fail("Expected node ID " + id + 
                                " got, " + node.getId());
                    } else if (! node.isAlive()) {
                        fail("Node " + id + " is not alive!");
                    }
                }
            }, taskOwner);
        }
        // shutdown nodes...
	if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }

	// wait for all nodes to fail...
	Thread.sleep(renewTime * 4);
        
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

    @Test public void testNodeHealthNotification() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        final long nodeId = serverNode.getDataService().getLocalNodeId();
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        checkNotification(listener, Health.GREEN);
        watchdogService.reportHealth(nodeId, Health.GREEN, "B");
        checkNotification(listener, Health.GREEN);
        watchdogService.reportHealth(nodeId, Health.YELLOW, "A");
        checkNotification(listener, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.YELLOW, "C");
        checkNotification(listener, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        checkNotification(listener, Health.YELLOW);
        watchdogService.reportHealth(nodeId, Health.GREEN, "C");
        checkNotification(listener, Health.GREEN);

        if (listener.getNumNotifications() != 6) {
            fail("Expected 6 notifications, got " +
                 listener.getNumNotifications());
        }
        Set<Node> nodes = listener.getStartedNodes();

        if (nodes.size() != 1) {
            fail("Expected 1 started node, got " + nodes.size());
        }
    }

    @Test public void testNodeHealthFailNotification() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        final long nodeId = serverNode.getDataService().getLocalNodeId();
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        checkNotification(listener, Health.GREEN);
        watchdogService.reportHealth(nodeId, Health.RED, "A");
        Thread.sleep(2000);
        watchdogService.reportHealth(nodeId, Health.GREEN, "A");
        Thread.sleep(2000);

        if (listener.getNumNotifications() != 1) {
            fail("Expected 1 notifications, got " +
                 listener.getNumNotifications());
        }
        Set<Node> nodes = listener.getStartedNodes();

        if (nodes.size() != 1) {
            fail("Expected 1 started node, got " + nodes.size());
        }
    }

    private void checkNotification(DummyNodeListener listener, Health expected)
        throws InterruptedException
    {
        // Wait for notification to work its way through
        Thread.sleep(2000);
        Node node = listener.getLastNotification();
        if (node == null) {
            fail("Expected a notification, did not get any");
        }
        if (node.getHealth() != expected) {
            fail("Expected " + expected + ", got " + node.getHealth());
        }
    }

    /* -- test shutdown -- */

    @IntegrationTest
    @Test public void testShutdownAndNotifyFailedNodes() throws Exception {
	class WatchdogInfo {
	    final DummyNodeListener listener;
	    final DataService dataService;
	    WatchdogInfo(DummyNodeListener listener, DataService dataService) {
		this.listener = listener;
		this.dataService = dataService;
	    }
	};
	Map<WatchdogServiceImpl, WatchdogInfo> watchdogMap =
	    new HashMap<WatchdogServiceImpl, WatchdogInfo>();
	try {
	    for (int i = 0; i < 5; i++) {
		Properties props = SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", serverNode, null);
		DataService dataService = createDataService(props);
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy,
					    dummyShutdownCtrl);
		DummyNodeListener listener = new DummyNodeListener();
		watchdog.addNodeListener(listener);
		watchdogMap.put(
		    watchdog, new WatchdogInfo(listener, dataService));
	    }
	
	    // shutdown watchdog server
	    watchdogService.shutdown();

	    Thread.sleep(renewTime * 4);

	    for (WatchdogServiceImpl watchdog : watchdogMap.keySet()) {
		WatchdogInfo info = watchdogMap.get(watchdog);
		DummyNodeListener listener = info.listener;
		DataService dataService = info.dataService;
		Set<Node> nodes = listener.getFailedNodes();
		System.err.println(
		    "failedNodes for " + dataService.getLocalNodeId() +
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
		watchdogMap.get(watchdog).dataService.shutdown();
	    }
	}
    }

    /* -- test addRecoveryListener -- */

    @Test(expected = IllegalStateException.class)
    public void testAddRecoveryListenerServiceShuttingDown()
	throws Exception
    {
	DataService dataService = createDataService(serviceProps);
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    serviceProps, systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.addRecoveryListener(new DummyRecoveryListener());
	dataService.shutdown();
    }

    @Test(expected = NullPointerException.class)
    public void testAddRecoveryListenerNullListener() throws Exception {
	watchdogService.addRecoveryListener(null);
    }

    @Test(expected = IllegalStateException.class)
    public void TestAddRecoveryListenerInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdogService.
		    addRecoveryListener(new DummyRecoveryListener());
            } }, taskOwner);
    }
    
    /* -- test recovery -- */

    @IntegrationTest
    @Test public void testRecovery() throws Exception {
	Map<Long, WatchdogAndData> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogAndData>();
	List<Long> shutdownIds = new ArrayList<Long>();

	int totalWatchdogs = 5;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogAndData watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down a few watchdog services
	    for (WatchdogAndData watchdog : watchdogs.values()) {
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

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    checkNodesFailed(shutdownIds, true);
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @IntegrationTest
    @Test public void testRecoveryWithBackupFailureDuringRecovery()
	throws Exception
    {
	Map<Long, WatchdogAndData> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogAndData>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 8;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogAndData watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down a few watchdog services
	    for (WatchdogAndData watchdog : watchdogs.values()) {
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

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    Set<Node> backups = checkNodesFailed(shutdownIds, true);

	    // shutdown backups
	    for (Node backup : backups) {
		long backupId = backup.getId();
		WatchdogAndData watchdog = watchdogs.get(backupId);
		if (watchdog != null) {
		    System.err.println("shutting down backup: " + backupId);
		    shutdownIds.add(backupId);
		    watchdog.shutdown();
		    watchdogs.remove(backupId);
		}
	    }

	    Thread.sleep(4 * renewTime);
	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @IntegrationTest
    @Test public void testRecoveryWithDelayedBackupAssignment()
	throws Exception
    {
	List<Long> shutdownIds = new ArrayList<Long>();
	long serverNodeId = serverNode.getDataService().getLocalNodeId();
	crashAndRestartServer(false);
	shutdownIds.add(serverNodeId);
	Map<Long, WatchdogAndData> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogAndData>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogAndData watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down all watchdog services.
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
	    }

	    watchdogs.clear();

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogAndData watchdog = createWatchdog(listener);
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @IntegrationTest
    @Test public void testRecoveryAfterServerCrash() throws Exception {
	Map<Long, WatchdogAndData> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogAndData>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;
	WatchdogAndData newWatchdog = null;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogAndData watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }
	    
	    // simulate crash
	    crashAndRestartServer(false);

	    checkNodesFailed(watchdogs.keySet(), false);
	    
	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    newWatchdog = createWatchdog(listener);

	    listener.checkRecoveryNotifications(totalWatchdogs + 1);
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(watchdogs.keySet());

	} finally {
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	    if (newWatchdog != null) {
		newWatchdog.shutdown();
	    }
	}
    }

    @IntegrationTest
    @Test public void testRecoveryAfterAllNodesAndServerCrash()
	throws Exception
    {
	Map<Long, WatchdogAndData> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogAndData>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogAndData watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down all watchdog services.
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
	    }

	    watchdogs.clear();

	    // simulate crash
	    crashAndRestartServer(false);

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogAndData watchdog = createWatchdog(listener); 
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size() + 1);
	    listener.notifyCompletionHandlers();

	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogAndData watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }
    
    /** Test creating two nodes at the same host and port  */
    @IntegrationTest
    @Test public void testReuseHostPort() throws Exception {
        addNodes(null, 1);
        Properties props = additionalNodes[0].getServiceProperties();
	props.setProperty("com.sun.sgs.impl.service.nodemap.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
	props.setProperty("com.sun.sgs.impl.service.watchdog.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
        props.setProperty("com.sun.sgs.impl.service.session.server.port",
                          String.valueOf(SgsTestNode.getNextUniquePort()));
        SgsTestNode node = null;
        try {
            node = new SgsTestNode(serverNode, null, props);
            fail("Expected BindException");
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            // The kernel constructs the services through reflection, and the
            // SgsTestNode creates the kernel through reflection - burrow down
            // to the root cause to be sure it's of the expected type.
            while ((target instanceof InvocationTargetException) ||
                   (target instanceof RuntimeException)) {
                System.err.println("unwrapping target exception");
                target = target.getCause();
            }
            if (!(target instanceof BindException)) {
                fail("Expected BindException, got " + target);
            }
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
        }
    }

    /** Test creating two single nodes at the same host and port  */
    @IntegrationTest
    @Test public void testReuseHostPortSingleNode() throws Exception {
        final String appName = "ReuseHostPort";
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
 	    Properties props = getPropsForApplication(appName);
	    node = new SgsTestNode(appName, null, props, true);
            
            // This node is independent of the one above;  it'll have a new
            // server.  We expect to see a socket BindException rather
            // than an IllegalArgumentException.
 	    Properties props1 = getPropsForApplication(appName + "1");
 	    props1.setProperty(
                com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY,
                props.getProperty(
                    com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY));
	    node1 = new SgsTestNode(appName, null, props1, true);
            fail ("Expected BindException");
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            // We wrap our exceptions a bit in the kernel....
            while ((target instanceof InvocationTargetException) ||
                   (target instanceof RuntimeException)) {
                System.err.println("unwrapping target exception");
                target = target.getCause();
            }
            if (!(target instanceof BindException)) {
                fail("Expected BindException, got " + target);
            }
        } finally {
            if (node != null) {
                node.shutdown(true);
            }
            if (node1 != null) {
                node1.shutdown(true);
            }
        }
    }

    /** Test that an application node can be restarted on the same host
     *  and port after a crash.
     */
    @IntegrationTest
    @Test public void testNodeCrashAndRestart() throws Exception {
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
            node = new SgsTestNode(serverNode, null, null);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            System.err.println("shutting down node");
            node.shutdown(false);
            node = null;
            // Note that we need to wait for the system to detect the
            // failed node.
            Thread.sleep(renewTime * 4);

            System.err.println("attempting to restart failed node");
            node1 = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, false);
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
            if (node1 != null) {
                node1.shutdown(false);
            }
        }
    }       
    
    /** Check that we can restart a single node system on the same
     *  host and port after a crash.
     */
    @IntegrationTest
    @Test public void testSingleNodeServerCrashAndRestart() throws Exception {
        final String appName = "TestServerCrash";
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
            node = new SgsTestNode(appName, null,
                                   getPropsForApplication(appName), true);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            System.err.println("shutting down single node");
            node.shutdown(false);
            node = null;

            // Note that in this case we don't have to wait for the system
            // to see the failed node - the entire system crashed, and the
            // check for reuse is implemented with a transient data structure.
            System.err.println("attempting to restart failed single node");
	    props.setProperty(
		"com.sun.sgs.impl.service.data.store.net.server.port",
		String.valueOf(SgsTestNode.getNextUniquePort()));
	    props.setProperty(
		"com.sun.sgs.impl.service.nodemap.server.port",
		String.valueOf(SgsTestNode.getNextUniquePort()));
	    props.setProperty(
		"com.sun.sgs.impl.service.watchdog.server.port",
		String.valueOf(SgsTestNode.getNextUniquePort()));
            node1 = new SgsTestNode(appName, null, null, props, false);
        } finally {
            if (node1 != null) {
                node1.shutdown(false);
            }
            if (node != null) {
                node.shutdown(true);
            }
        }
    }

    /* --- test shutdown procedures --- */

    @Test(expected = NullPointerException.class)
    public void testReportFailureNullClassName() {
	watchdogService.reportFailure(
	    serverNode.getDataService().getLocalNodeId(), null);
    }

    @Test(expected = IllegalStateException.class)
    public void testReportFailureInTransaction() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		watchdogService.reportFailure(1, getClass().getName());
	    }}, taskOwner);
    }
    
    /** 
     * Check that a node can report a failure and shutdown itself down by
     * notifying the watchdog service
     */
    @Test public void testReportLocalFailure() throws Exception {
	final String appName = "TestReportFailure";

	// Create a dummy shutdown controller to log calls to the shutdown
	// method. NOTE: The controller does not actually shutdown the node
	DataService dataService = createDataService(serviceProps);
	WatchdogServiceImpl watchdogService = 
	    new WatchdogServiceImpl(serviceProps, systemRegistry, 
				    txnProxy, dummyShutdownCtrl);

	// Report a failure, which should shutdown the node
	watchdogService.reportFailure(dataService.getLocalNodeId(), 
				      appName);

	// Node should not be alive since we reported a failure
	try {
	    assertFalse(watchdogService.isLocalNodeAliveNonTransactional());
	} catch (Exception e) {
	    fail("Not expecting an Exception: " + e.getLocalizedMessage());
	}
            
	// The shutdown controller should be incremented as a result of the 
	// failure being reported
	assertEquals(1, dummyShutdownCtrl.getShutdownCount());
	watchdogService.shutdown();
	dataService.shutdown();
    }

    /**
     * Check that a node can shutdown and report a failure when it detects 
     * that the renew process from the watchdog service fails
     */
    @Test public void testReportFailureDueToNoRenewal() throws Exception {        
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        
        // Shutdown the server 
        serverNode.shutdown(true);
        serverNode = null;

        // Wait for the renew to fail and the shutdown to begin
        Thread.sleep(renewTime*4);

        try {
            // The node should be shut down
            assertFalse(node.getWatchdogService().isLocalNodeAliveNonTransactional());
        } catch (IllegalStateException ise) {
            // May happen if service is shutting down.
        } catch (Exception e) {
            fail ("Not expecting an Exception: " + e.getLocalizedMessage());
        }
    }

    /**
     * Check that a node can report a failure in a remote node and 
     * the failed node should shutdown accordingly
     */
    @IntegrationTest
    @Test public void testReportRemoteFailure() throws Exception {
        final String appName = "TestReportRemoteFailure_node";
        try {
            // Instantiate two nodes
            final SgsTestNode server = new SgsTestNode(appName, null, 
                    getPropsForApplication(appName));
            final SgsTestNode node = new SgsTestNode(server, null, null);

            // Report that the second node failed
            System.err.println("server node id: " + server.getNodeId());
            System.err.println("   new node id: " + node.getNodeId());

            server.getWatchdogService().reportFailure(node.getNodeId(), 
                    WatchdogService.class.getName());

            // The server node that reported the remote 
            // failure should be unaffected
            TransactionScheduler sched = server.getSystemRegistry().
                    getComponent(TransactionScheduler.class);
            Identity own = server.getProxy().getCurrentOwner();
            sched.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    assertTrue(server.getWatchdogService().isLocalNodeAlive());
                }
            }, own);

            try {
                // The node should have failed
                sched = node.getSystemRegistry().
                        getComponent(TransactionScheduler.class);
                own = node.getProxy().getCurrentOwner();
                sched.runTask(new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        if (node.getWatchdogService().isLocalNodeAlive()) {
                            fail("Expected watchdogService.isLocalNodeAlive() " +
                                    "to return false");
                        }
                    }
                }, own);
            } catch (IllegalStateException ise) {
                // Expected
            } catch (Exception e) {
                fail("Not expecting an Exception (1)");
            }
        } catch (Exception e) {
            fail("Not expecting an Exception (2)");
        }
    }

    /**
     * Check that a server that has lost communication with it's service will
     * issue a shutdown of the node with the failed service
     */
    @IntegrationTest
    @Test public void testReportFailureServerSide() {
        final String appName = "TestFailureServerSide";
        try {
             final SgsTestNode appNode = new SgsTestNode(serverNode, null, null);
            
            // Find the node mapping server
            Field mapServer =
                    NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
            mapServer.setAccessible(true);
            final NodeMappingService nodeMappingService = 
                    serverNode.getNodeMappingService();
            NodeMappingServerImpl nodeMappingServer =
                    (NodeMappingServerImpl) mapServer.get(nodeMappingService);
            
            // Create a new identity and assign it to a node
            // Since there is only 1 app node, it will be assigned to that one
            final Identity id = new IdentityImpl(appName + "_identity");
            nodeMappingService.assignNode(NodeMappingService.class, id);
            System.err.println("AppNode id: "+appNode.getNodeId());

            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    // See if the right node has the identity
                    long nodeid = nodeMappingService.getNode(id).getId();
                    System.err.println("Identity is on node: "+nodeid);
                    if (nodeid != appNode.getNodeId())
                        fail("Identity is on the wrong node");
                }
            }, taskOwner);

            // Convince the Node Mapping server that the identity 
            // has been removed. This ensures that rtask.isDead() is true
            appNode.getNodeMappingService().setStatus(
                    NodeMappingService.class, id, false);

            // Unexport the NodeMappingService on the appNode to shutdown the
            // service without removing the node listener. This should
            // cause an IOException in the RemoveTask of the server when 
            // removing the identity.
            Field privateField =
                    NodeMappingServiceImpl.class.getDeclaredField("exporter");
            privateField.setAccessible(true);
            Exporter<?> exporter = (Exporter<?>) privateField.get(
                    appNode.getNodeMappingService());
            exporter.unexport();
            
            Thread.sleep(renewTime); // Let it shutdown
            nodeMappingServer.canRemove(id); // Remove the identity
            Thread.sleep(renewTime); // Wait for RemoveThread to run on server
            
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        // The appNode should be shutting down or shut down
                        appNode.getWatchdogService().isLocalNodeAlive();
                        fail("Expected IllegalStateException");
                    } catch (IllegalStateException ise) {
                        // Expected
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Unexpected Exception");
                    }
                }
            }, taskOwner);
            
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected Exception");
        }
    }
    
    /**
     * Check that if two concurrent shutdowns are issued for a node, the second 
     * shutdown will fail quietly without throwing any exceptions.
     */
    @IntegrationTest
    @Test public void testConcurrentShutdowns() throws Exception {
        final SgsTestNode appNode = new SgsTestNode(serverNode, null, null);
        // issue a shutdown; this shutdown runs in a seperate thread
        appNode.getWatchdogService().reportFailure(appNode.getNodeId(),
                appNode.getClass().getName());
        // issue another shutdown; set clean = false since we do not want this
        // test case to fail due to an error trying to delete a missing file
        appNode.shutdown(false);
    }
    
    /**
     * Check if a node shutdown can be issued from a component successfully
     */
    @IntegrationTest
    @Test(expected = IllegalStateException.class)
    public void testComponentShutdown() throws Exception {
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        
        // Simulate shutdown being called from a component by passing a
        // a component object
        node.getShutdownCtrl().shutdownNode(node.getSystemRegistry().
                getComponent(TransactionScheduler.class));
        Thread.sleep(renewTime); // let it shutdown
        
	// The node should be shutting down or shut down
	node.getWatchdogService().isLocalNodeAliveNonTransactional();
    }

    /* -- test currentAppTimeMillis() -- */

    @Test public void testCurrentAppTimeMillisInit() throws Exception {
        assertTrue(watchdogService.currentAppTimeMillis() < 100);
        Thread.sleep(1000);
        assertTrue(watchdogService.currentAppTimeMillis() > 
                   1000 - Constants.MAX_CLOCK_GRANULARITY);
    }

    @Test public void testCurrentAppTimeMillisAfterShutdown() throws Exception {
        Thread.sleep(1000);
        assertTrue(watchdogService.currentAppTimeMillis() >
                   1000 - Constants.MAX_CLOCK_GRANULARITY);

        long beforeTime = watchdogService.currentAppTimeMillis();
        crashAndRestartServer(false);
        long afterTime = watchdogService.currentAppTimeMillis();

        assertTrue(afterTime >= beforeTime);
        assertTrue(afterTime < beforeTime + 100);
    }

    @Test public void testCurrentAppTimeMillisAfterShutdownAndCleanDatabase()
            throws Exception {
        Thread.sleep(1000);
        assertTrue(watchdogService.currentAppTimeMillis() >
                   1000 - Constants.MAX_CLOCK_GRANULARITY);

        long beforeTime = watchdogService.currentAppTimeMillis();
        crashAndRestartServer(true);
        long afterTime = watchdogService.currentAppTimeMillis();

        assertTrue(afterTime < 100);
        assertTrue(afterTime < beforeTime);
    }

    @Test public void testCurrentAppTimeMillisSync() throws Exception {
        // setup an app node with a small timesync interval
        Properties appProps = SgsTestNode.getDefaultProperties(
                "TestWatchdogServiceImpl", serverNode, null);
        appProps.setProperty(
                "com.sun.sgs.impl.service.watchdog.timesync.interval",
                "1000");
        addNodes(appProps, 1);

        WatchdogServerImpl watchdogServer = watchdogService.getServer();
        WatchdogService remoteWatchdogService =
                        additionalNodes[0].getWatchdogService();

        // watchdog server and service should initially be sync'd
        long serverTime = watchdogServer.currentAppTimeMillis();
        long serviceTime = remoteWatchdogService.currentAppTimeMillis();
        assertTrue(checkInRange(serverTime,
                                serviceTime,
                                Constants.MAX_CLOCK_GRANULARITY));

        // force watchdog service out of sync
        Field serviceTimeOffset = getField(WatchdogServiceImpl.class,
                                           "timeOffset");
        serviceTimeOffset.set(remoteWatchdogService, 0);
        serverTime = watchdogServer.currentAppTimeMillis();
        serviceTime = remoteWatchdogService.currentAppTimeMillis();
        assertFalse(checkInRange(serverTime,
                                 serviceTime,
                                 Constants.MAX_CLOCK_GRANULARITY));

        // wait for time sync interval and verify service syncs back with server
        Thread.sleep(1000 + Constants.MAX_CLOCK_GRANULARITY);
        serverTime = watchdogServer.currentAppTimeMillis();
        serviceTime = remoteWatchdogService.currentAppTimeMillis();
        assertTrue(checkInRange(serverTime,
                                serviceTime,
                                Constants.MAX_CLOCK_GRANULARITY));
    }
    
    
    /**
     * Fakes out a KernelShutdownController for test purposes
     */
    private static class DummyKernelShutdownController implements
	    KernelShutdownController {
	private int shutdownCount = 0;

	public void shutdownNode(Object caller) {
	    shutdownCount++;
	}

	int getShutdownCount() {
	    return shutdownCount;
	}
        
        void reset() {
            shutdownCount = 0;
        }
    }

    /** Creates node properties with a db directory based on the app name. */
    private Properties getPropsForApplication(String appName)
	throws Exception
    {
        String dir = System.getProperty("java.io.tmpdir") +
            File.separator + appName + ".db";
        Properties props =
	    SgsTestNode.getDefaultProperties(appName, null, null);
        props.setProperty(
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
            dir);
        return props;
    }

    /** Creates a watchdog service with the specified recovery listener. */
    private WatchdogAndData createWatchdog(RecoveryListener listener)
	throws Exception
    {
	Properties props = SgsTestNode.getDefaultProperties(
	    "TestWatchdogServiceImpl", serverNode, null);
	DataService data = createDataService(props);
	WatchdogServiceImpl watchdog = 
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, 
            dummyShutdownCtrl);
	watchdog.addRecoveryListener(listener);
	watchdog.ready();
	System.err.println("Created node (" + data.getLocalNodeId() + ")");
	return new WatchdogAndData(watchdog, data);
    }
    
    /** Stores a pair of associated watchdog and data services. */
    private static class WatchdogAndData {
	final WatchdogService watchdog;
	final DataService data;
	WatchdogAndData(WatchdogService watchdog, DataService data) {
	    this.watchdog = watchdog;
	    this.data = data;
	}
	long getLocalNodeId() {
	    return data.getLocalNodeId();
	}
	void shutdown() {
	    watchdog.shutdown();
	    data.shutdown();
	}
    }

    /** Tears down the server node and restarts it as a server-only stack. */
    private void crashAndRestartServer(boolean clean) throws Exception {
	System.err.println("simulate watchdog server crash...");
	tearDown(clean);
	Properties props =
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null);
        props.setProperty(
            StandardProperties.NODE_TYPE,
            NodeType.coreServerNode.name());
	setUp(props, false);
    }
    
    private Set<Node> checkNodesFailed(Collection<Long> ids, boolean hasBackup)
	throws Exception
    {
        CheckNodesFailedTask task = new CheckNodesFailedTask(ids, hasBackup);
        txnScheduler.runTask(task, taskOwner);
        return task.backups;
    }

    private class CheckNodesFailedTask extends TestAbstractKernelRunnable {
	Set<Node> backups = new HashSet<Node>();
        Collection<Long> ids;
        boolean hasBackup;

        CheckNodesFailedTask(Collection<Long> ids, boolean hasBackup) {
            this.ids = ids;
            this.hasBackup = hasBackup;
        }
        public void run() {
	    System.err.println("Get shutdown nodes (should be marked failed)");
	    for (Long longId : ids) {
	        long id = longId.longValue();
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
	        if (hasBackup) {
		    if (backup == null) {
		        fail("failed node (" + id + ") has no backup");
		    } else {
		        backups.add(backup);
		    }
	        } else if (!hasBackup && backup != null) {
		    fail("failed node (" + id + ") assigned backup: " +
		         backup);
	        }
	    }
        }
    }

    private void checkNodesRemoved(final Collection<Long> ids) throws Exception {
	Thread.sleep(250);
	System.err.println("Get shutdown nodes (should be removed)...");
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
	        for (Long longId : ids) {
	            long id = longId.longValue();
	            Node node = watchdogService.getNode(id);
	            System.err.println("node (" + id + "):" +
			               (node == null ? "(removed)" : node));
	            if (node != null) {
		        fail("Expected node to be removed: " + node);
	            }
	        }
            }
        }, taskOwner);
    }

    private void checkNodesAlive(final Collection<Long> ids) throws Exception {
	System.err.println("Get live nodes...");
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
	        for (Long longId : ids) {
	            long id = longId.longValue();
	            Node node = watchdogService.getNode(id);
	            System.err.println("node (" + id + "): " + node);
	            if (node == null || !node.isAlive()) {
		        fail("Expected alive node");
	            }
	        }
            }
        }, taskOwner);
    }

    /**
     * returns {@code true} if val1 and val2 are within range of eachother
     */
    private boolean checkInRange(long val1, long val2, long range) {
        long diff = Math.abs(val1 - val2);
        return diff <= range;
    }

    private static class DummyRecoveryListener implements RecoveryListener {

	private final Map<Node, SimpleCompletionHandler> nodes =
	    Collections.synchronizedMap(
		new HashMap<Node, SimpleCompletionHandler>());

	DummyRecoveryListener() {}

	public void recover(Node node, SimpleCompletionHandler handler) {
            assert(node != null);
            assert(handler != null);

	    synchronized (nodes) {
		if (nodes.get(node) == null) {
		    System.err.println(
			"DummyRecoveryListener.recover: adding node: " + node);
		} else {
		    System.err.println(
			"DummyRecoveryListener.recover: REPLACING node: " + node);
		}
		nodes.put(node, handler);
		nodes.notifyAll();
	    }
	    
	}

	void checkRecoveryNotifications(int expectedSize) {
	    long endTime = System.currentTimeMillis() + 5000;
	    synchronized (nodes) {
		while (nodes.size() != expectedSize &&
		       System.currentTimeMillis() < endTime)
		{
		    try {
			nodes.wait(500);
		    } catch (InterruptedException e) {
		    }
		}
		if (nodes.size() != expectedSize) {
		    fail("Expected " + expectedSize + " recover requests, " +
			 "received: " + nodes.size());
		}
	    }
	}

	void notifyCompletionHandlers() {
	    for (SimpleCompletionHandler handler : nodes.values()) {
		handler.completed();
	    }
	}
    }

    /* -- other methods -- */

    private static class DummyNodeListener implements NodeListener {

        private int notifications = 0;
        private Node lastNode = null;
	private final Set<Node> failedNodes = new HashSet<Node>();
	private final Set<Node> startedNodes = new HashSet<Node>();

	public void nodeHealthUpdate(Node node) {
            notifications++;
            lastNode = node;
	    if (node.isAlive()) {
                startedNodes.add(node);
            } else {
                failedNodes.add(node);
            }
	}

	Set<Node> getFailedNodes() {
	    return failedNodes;
	}

	Set<Node> getStartedNodes() {
	    return startedNodes;
	}

        int getNumNotifications() {
            return notifications;
        }

        Node getLastNotification() {
            Node node = lastNode;
            lastNode = null;
            return node;
        }
    }

    /** Define a {@code ComponentRegistry} that holds a single component. */
    private static class SingletonComponentRegistry
	implements ComponentRegistry
    {
	private final Object component;
	SingletonComponentRegistry(Object component) {
	    this.component = component;
	}
	public <T> T getComponent(Class<T> type) {
	    if (type.isAssignableFrom(component.getClass())) {
		return type.cast(component);
	    } else {
		throw new MissingResourceException(
		    "No matching components", type.getName(), null);
	    }
	}
	public Iterator<Object> iterator() {
	    return Collections.singleton(component).iterator();
	}
    }

    /**
     * Creates a new data service and installs it, and the associated data
     * manager, in the kernel context.
     *
     * @param	props the configuration properties for creating the service
     * @return	the new data service
     * @throws	Exception if a problem occurs when creating the service
     */
    private DataService createDataService(Properties props) throws Exception {
	DataService dataService =
	    new DataServiceImpl(props, systemRegistry, txnProxy);
	ComponentRegistry services =
	    new SingletonComponentRegistry(dataService);
	ComponentRegistry managers =
	    new SingletonComponentRegistry(
		new ProfileDataManager(dataService));
	Object newKernelContext = kernelContextConstructor.newInstance(
	    "TestWatchdogServiceImpl", services, managers);
	contextResolverSetTaskState.invoke(null, newKernelContext, taskOwner);
	return dataService;
    }
}
