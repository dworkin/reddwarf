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

package com.sun.sgs.test.impl.service.watchdog;

import static com.sun.sgs.test.util.UtilProperties.createProperties;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collection;
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

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.impl.service.nodemap.EvilProxy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;

public class TestWatchdogServiceImpl extends TestCase {
    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
	"com.sun.sgs.impl.service.watchdog.server";

    /* The number of additional nodes to create if tests need them */
    private static final int NUM_WATCHDOGS = 5;

    /** The node that creates the servers */
    private SgsTestNode serverNode;

    /** Version information from WatchdogServiceImpl class. */
    private final String VERSION_KEY;
    private final int MAJOR_VERSION;
    private final int MINOR_VERSION;
    
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];

    /** System components found from the serverNode */
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
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

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }

    /** Constructs a test instance. */
    public TestWatchdogServiceImpl(String name) throws Exception {
	super(name);
	Class cl = WatchdogServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Test setup. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        setUp(null, true);
    }

    protected void setUp(Properties props, boolean clean) throws Exception {
        serverNode = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, clean);
        System.err.println("instantiated server node");
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        serviceProps.setProperty(StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()));
        renewTime = Integer.valueOf(
            serviceProps.getProperty(
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
            System.err.println("watchdog service id: " +
                                   node.getWatchdogService().getLocalNodeId());

        }
    }

    /** Shut down the nodes. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(clean);
	/* Wait for sockets to close down. */
	Thread.sleep(100);
    }

    /* -- Test constructor -- */

    public void testConstructor() throws Exception {
        WatchdogServiceImpl watchdog = null;
        try {
            watchdog = new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, null);  
            WatchdogServerImpl server = watchdog.getServer();
            System.err.println("watchdog server: " + server);
            server.shutdown();
        } finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorNullProperties() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(null, systemRegistry, txnProxy, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorNullRegistry() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(serviceProps, null, txnProxy, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorNullProxy() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog =
                    new WatchdogServiceImpl(serviceProps, systemRegistry, null, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorNoAppName() throws Exception {
        WatchdogServiceImpl watchdog = null;
        Properties properties = createProperties(
            StandardProperties.APP_PORT, "20000",
            WatchdogServerPropertyPrefix + ".port", "0");
        try {
            new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
    }

    public void testConstructorNoAppPort() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
            StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", "0");
	try {
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorAppButNoServerHost() throws Exception {
        // Server start is false but we didn't specify a server host
        int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()),
            WatchdogServerPropertyPrefix + ".start", "false",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
        try {
            WatchdogServiceImpl watchdog =
                new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
    }
    
    public void testConstructorNegativePort() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(-1));
	try {
	    watchdog = 
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorPortTooLarge() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(65536));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorStartServerRenewIntervalTooSmall()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "0");
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorStartServerWithLargeRenewInterval()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval",
		Integer.toString(Integer.MAX_VALUE));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	} catch (IllegalArgumentException e) {
	    fail("Unexpected IllegalArgumentException");
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructedVersion() throws Exception {
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
    
    public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, null);  
	watchdog.shutdown();
    }

    public void testConstructorWithMajorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy, null);  
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorWithMinorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy, null);  
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
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
            StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()),
            WatchdogServerPropertyPrefix + ".start", "false",
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
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
	    new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, null);
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
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                if (! watchdogService.isLocalNodeAlive()) {
                    fail("Expected watchdogService.isLocalNodeAlive() " +
                          "to return true");
                }
            }
        }, taskOwner);

	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()),
            WatchdogServerPropertyPrefix + ".start", "false",
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
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
	}
    }

    public void testIsLocalNodeAliveServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
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
            StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()),
	    "com.sun.sgs.impl.service.nodemap.client.port",
	        String.valueOf(SgsTestNode.getNextUniquePort()),
	    "com.sun.sgs.impl.service.watchdog.client.port",
	        String.valueOf(SgsTestNode.getNextUniquePort()),
            WatchdogServerPropertyPrefix + ".start", "false",
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
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
	    watchdog.shutdown();
	}
    }

    public void testIsLocalNodeAliveNonTransactionalServiceShuttingDown()
	throws Exception
    {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
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


    public void testGetNodesServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
	watchdog.shutdown();

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        watchdog.getNodes();
                        fail("Expected IllegalStateException");
                    } catch (IllegalStateException e) {
                        System.err.println(e);
                    }
                }
            }, taskOwner);
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
        addNodes(null, NUM_WATCHDOGS);

        for (SgsTestNode node : additionalNodes) {
            WatchdogService watchdog = node.getWatchdogService();
            final long id  = watchdog.getLocalNodeId();
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

    public void testGetNodeServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        watchdog.getNode(0);
                        fail("Expected IllegalStateException");
                    } catch (IllegalStateException e) {
                        System.err.println(e);
                    }
                }
            }, taskOwner);
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

    public void testAddNodeListenerServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                try {
                    watchdog.addNodeListener(new DummyNodeListener());
                    fail("Expected IllegalStateException");
                } catch (IllegalStateException e) {
                    System.err.println(e);
                }
            }
        }, taskOwner);
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

    public void testAddNodeListenerNodeFailed() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        addNodes(null, NUM_WATCHDOGS);
        for (SgsTestNode node : additionalNodes) {
            WatchdogService watchdog = node.getWatchdogService();
            final long id  = watchdog.getLocalNodeId();
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

    /* -- test shutdown -- */

    public void testShutdownAndNotifyFailedNodes() throws Exception {
	Map<WatchdogServiceImpl, DummyNodeListener> watchdogMap =
	    new HashMap<WatchdogServiceImpl, DummyNodeListener>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            WatchdogServerPropertyPrefix + ".start", "false",
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	try {
	    for (int i = 0; i < 5; i++) {
                props.put(StandardProperties.APP_PORT,
                          Integer.toString(SgsTestNode.getNextAppPort()));
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
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
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, null);
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

    /* -- test recovery -- */

    public void testRecovery() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();

	int totalWatchdogs = 5;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
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

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    checkNodesFailed(shutdownIds, true);
	    listener.notifyFutures();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    public void testRecoveryWithBackupFailureDuringRecovery() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 8;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
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

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    Set<Node> backups = checkNodesFailed(shutdownIds, true);

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

	    Thread.sleep(4 * renewTime);
	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyFutures();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    public void testRecoveryWithDelayedBackupAssignment() throws Exception {
	List<Long> shutdownIds = new ArrayList<Long>();
	long serverNodeId = serverNode.getWatchdogService().getLocalNodeId();
	crashAndRestartServer();
	shutdownIds.add(serverNodeId);
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
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
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener);
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyFutures();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    public void testRecoveryAfterServerCrash() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;
	WatchdogServiceImpl newWatchdog = null;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }
	    
	    // simulate crash
	    crashAndRestartServer();

	    checkNodesFailed(watchdogs.keySet(), false);
	    
	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    newWatchdog = createWatchdog(listener);

	    listener.checkRecoveryNotifications(totalWatchdogs + 1);
	    listener.notifyFutures();
	    checkNodesRemoved(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	    if (newWatchdog != null) {
		newWatchdog.shutdown();
	    }
	}
    }

    public void testRecoveryAfterAllNodesAndServerCrash() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
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

	    // simulate crash
	    crashAndRestartServer();

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener); 
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size() + 1);
	    listener.notifyFutures();

	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }
    
    /** Test creating two nodes at the same host and port  */
    public void testReuseHostPort() throws Exception {
        addNodes(null, 1);
        Properties props = additionalNodes[0].getServiceProperties();
	props.setProperty("com.sun.sgs.impl.service.nodemap.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
	props.setProperty("com.sun.sgs.impl.service.watchdog.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
        SgsTestNode node = null;
        try {
            node = new SgsTestNode(serverNode, null, props);
            fail("Expected IllegalArgumentException");
        } catch (InvocationTargetException e) {
            System.err.println(e);
            Throwable target = e.getTargetException();
            // The kernel constructs the services through reflection, and the
            // SgsTestNode creates the kernel through reflection - burrow down
            // to the root cause to be sure it's of the expected type.
            while (target instanceof InvocationTargetException) {
                System.err.println("unwrapping target exception");
                target = ((InvocationTargetException) target).getTargetException();
            }
            if (!(target instanceof IllegalArgumentException)) {
                fail("Expected IllegalArgumentException");
            }
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
        }
    }

    /** Test creating two single nodes at the same host and port  */
    public void testReuseHostPortSingleNode() throws Exception {
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
 	    props1.setProperty(StandardProperties.APP_PORT,
 			       props.getProperty(StandardProperties.APP_PORT));
	    node1 = new SgsTestNode(appName, null, props1, true);
            fail ("Expected BindException");
        } catch (InvocationTargetException e) {
            System.err.println(e);
            Throwable target = e.getTargetException();
            // We wrap our exceptions a bit in the kernel....
            while (target instanceof InvocationTargetException) {
                System.err.println("unwrapping target exception");
                target = ((InvocationTargetException) target).getTargetException();
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
    public void testNodeCrashAndRestart() throws Exception {
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
            Thread.sleep(renewTime * 2);

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
    public void testSingleNodeServerCrashAndRestart() throws Exception {
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
    
    /** Creates node properties with a db directory based on the app name. */
    private Properties getPropsForShutdownTests(String appName)
	throws Exception
    {
        String dir = System.getProperty("java.io.tmpdir") +
            File.separator + appName + ".db";
        Properties props =
	    SgsTestNode.getDefaultProperties(appName, null, null);
        props.setProperty(
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
            dir);
        props.setProperty(
                StandardProperties.WATCHDOG_SERVICE,
                WatchdogServiceImpl.class.getName());
        
        System.err.println("::"+props.getProperty(StandardProperties.WATCHDOG_SERVICE));
        return props;
    }
    
    /** 
     * Check that a node can report a failure and become
     * shutdown
     */
    public void testReportFailure() throws Exception {
        final String appName = "TestReportFailure";
        SgsTestNode node = null;
        try {
            node = new SgsTestNode(appName, null,
                                   getPropsForShutdownTests(appName), true);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            WatchdogService wdsvc = node.getWatchdogService();
            wdsvc.reportFailure(node.getNodeId(), 
        	    this.getName(), WatchdogService.FailureLevel.FATAL);
            
            // Node should be shut down since we reported a failure
            try {
        	wdsvc.isLocalNodeAlive();
        	fail("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
        	// expected
            } catch (Exception e) {
        	fail ("Not expecting exception: " + e.getLocalizedMessage());  
            }
            
        } finally {
            if (node != null) {
                node.shutdown(true);
            }
        }
    }
    
    
    /** Check that a node can report a failure and become
     * shutdown
     */
    public void testFailureDueToNoRenewal() throws Exception {
        final String appName = "testFailureDueToNoRenewal";

        // Make a watchdog service with a very small renew interval;
        // this should cause the renew process to cause a shutdown
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, appName,
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "100");
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy, null);
	} catch (IllegalArgumentException e) {
	    fail("Unexpected IllegalArgumentException: " + e.getLocalizedMessage());
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
	
	// wait for the renew thread to fail, and check if
	// the node is alive. Since the renew should fail,
	// checking if it is alive should throw an IllegalStateException
	Thread.sleep(1000);
	try {
	    watchdog.isLocalNodeAlive();
	    fail("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	    // Expected
	} catch (Exception e) {
	    fail("Not expecting Exception:" + e.getLocalizedMessage());
	}
	
    }
    
    
    /** 
     * Check that a node can report a failure and become
     * shutdown
     */
    public void testReportRemoteFailure() throws Exception {
        final String appName = "TestReportRemoteFailure_node";
        final String appName2 = "TestReportRemoteFailure_node2";
        SgsTestNode node = null;
        SgsTestNode node2 = null;
        try {
            node = new SgsTestNode(appName, null,
                                   getPropsForApplication(appName), true);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            // instantiate the second node
            node2 = new SgsTestNode(appName2, null,
                    getPropsForApplication(appName2), true);
            props = node2.getServiceProperties();
            System.err.println("node properties are " + props);
            WatchdogService node2WatchdogService = node2.getWatchdogService();
            
            // report that the second node failed
            WatchdogService nodeWatchdogService = node.getWatchdogService();
            nodeWatchdogService.reportFailure(node2.getNodeId(), 
        	    this.getName(), WatchdogService.FailureLevel.FATAL);
            
            // This node should be unaffected
            assertTrue(nodeWatchdogService.isLocalNodeAlive());
            // The other node should have failed
            try {
        	node2WatchdogService.isLocalNodeAlive();
        	fail("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
        	// expected
            } catch (Exception e) {
        	fail ("Not expecting exception: " + e.getLocalizedMessage());  
            }
            
        } finally {
            if (node != null) {
                node.shutdown(true);
            }
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
    private WatchdogServiceImpl createWatchdog(RecoveryListener listener)
	throws Exception
    {
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, 
                Integer.toString(SgsTestNode.getNextAppPort()),
            WatchdogServerPropertyPrefix + ".start", "false",
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port",
	    Integer.toString(watchdogService.getServer().getPort()));
	WatchdogServiceImpl watchdog = 
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, null);
	watchdog.addRecoveryListener(listener);
	watchdog.ready();
	System.err.println("Created node (" + watchdog.getLocalNodeId() + ")");
	return watchdog;
    }
    
    /** Tears down the server node and restarts it as a server-only stack. */
    private void crashAndRestartServer() throws Exception {
	System.err.println("simulate watchdog server crash...");
	tearDown(false);
	Properties props =
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null);
	props.setProperty(
	    StandardProperties.FINAL_SERVICE,
	    StandardProperties.StandardService.NodeMappingService.toString());
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

    private static class DummyRecoveryListener implements RecoveryListener {

	private final Map<Node, RecoveryCompleteFuture> nodes =
	    Collections.synchronizedMap(
		new HashMap<Node, RecoveryCompleteFuture>());

	DummyRecoveryListener() {}

	public void recover(Node node, RecoveryCompleteFuture future) {
            assert(node != null);
            assert(future != null);

	    synchronized (nodes) {
		if (nodes.get(node) == null) {
		    System.err.println(
			"DummyRecoveryListener.recover: adding node: " + node);
		} else {
		    System.err.println(
			"DummyRecoveryListener.recover: REPLACING node: " + node);
		}
		nodes.put(node, future);
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

	void notifyFutures() {
	    for (RecoveryCompleteFuture future : nodes.values()) {
		future.done();
	    }
	}
    }

    /* -- other methods -- */

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
