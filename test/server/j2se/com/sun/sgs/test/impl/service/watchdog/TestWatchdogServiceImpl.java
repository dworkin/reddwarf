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

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.SgsTestNode;
import static com.sun.sgs.test.util.UtilProperties.createProperties;

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

public class TestWatchdogServiceImpl extends TestCase {
    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
	"com.sun.sgs.impl.service.watchdog.server";

    /* The number of additional nodes to create if tests need them */
    private static final int NUM_WATCHDOGS = 5;

    /** The node that creates the servers */
    private SgsTestNode serverNode;
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];

    /** System components found from the serverNode */
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;

    /** A specific property we started with */
    private int renewTime;

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The watchdog service for serverNode */
    private WatchdogServiceImpl watchdogService;

    /** Constructs a test instance. */
    public TestWatchdogServiceImpl(String name) {
	super(name);
    }

    /** Test setup. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        setUp(null, true);
    }

    protected void setUp(Properties props, boolean clean) throws Exception {
        serverNode = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, clean);
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        renewTime = Integer.valueOf(
            serviceProps.getProperty(
                "com.sun.sgs.impl.service.watchdog.renew.interval"));

        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();

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
    }

    /* -- Test constructor -- */

    public void testConstructor() throws Exception {
        WatchdogServiceImpl watchdog = null;
        try {
            watchdog =
                new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);  
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
	    watchdog = new WatchdogServiceImpl(null, systemRegistry, txnProxy);
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
	    watchdog = new WatchdogServiceImpl(serviceProps, null, txnProxy);
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
                    new WatchdogServiceImpl(serviceProps, systemRegistry, null);
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
            new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
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
	    new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
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
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
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
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
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
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    public void testConstructorStartServerRenewIntervalTooLarge()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
	    WatchdogServerPropertyPrefix + ".start", "true",
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "10001");
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
            if (watchdog != null) watchdog.shutdown();
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
            StandardProperties.APP_PORT, "20000",
            WatchdogServerPropertyPrefix + ".start", "false",
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
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
            StandardProperties.APP_PORT, "20000",
            WatchdogServerPropertyPrefix + ".start", "false",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	try {
            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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

            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
            StandardProperties.APP_PORT, "20000",
            WatchdogServerPropertyPrefix + ".start", "false",
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
        addNodes(null, NUM_WATCHDOGS);
        Thread.sleep(renewTime);
        CountNodesTask task = new CountNodesTask();
        taskScheduler.runTransactionalTask(task, taskOwner);
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
    private class CountNodesTask extends AbstractKernelRunnable {
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
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();

        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	watchdog.shutdown();
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
            taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	try {
	    for (int i = 0; i < 5; i++) {
                props.put(StandardProperties.APP_PORT,
                          Integer.toString(19000 + i));
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

    /* -- test recovery -- */

    public void testRecovery() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();

	int totalWatchdogs = 5;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
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

	    // pause for watchdog server to detect failure and
	    // send notifications
	    Thread.sleep(3 * renewTime);
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

	    // pause for watchdog server to detect failure and
	    // send notifications
	    Thread.sleep(3 * renewTime);
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

	    Thread.sleep(3 * renewTime);
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

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(3 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener);
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    // pause for watchdog server to reassign new node as
	    // backup to exising nodes.
	    
	    Thread.sleep(3 * renewTime);
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
	    System.err.println("simulate watchdog server crash...");
	    tearDown(false);
	    setUp(null, false);

	    checkNodesFailed(watchdogs.keySet(), false);
	    
	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    newWatchdog = createWatchdog(listener);

	    // pause for watchdog server to reassign new node as
	    // backup to exising nodes.
	    
	    Thread.sleep(3 * renewTime);
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
	    System.err.println("simulate watchdog server crash...");
	    tearDown(false);
	    setUp(null, false);

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(3 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener); 
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    // pause for watchdog server to reassign new node as
	    // backup to exising nodes.
	    
	    Thread.sleep(3 * renewTime);
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

    /** Creates a watchdog service with the specified recovery listener. */
    private WatchdogServiceImpl createWatchdog(RecoveryListener listener)
	throws Exception
    {
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.APP_PORT, "20000",
            WatchdogServerPropertyPrefix + ".start", "false",
	    WatchdogServerPropertyPrefix + ".port",
	    Integer.toString(watchdogService.getServer().getPort()));
	WatchdogServiceImpl watchdog = 
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy);
	watchdog.addRecoveryListener(listener);
	watchdog.ready();
	System.err.println("Created node (" + watchdog.getLocalNodeId() + ")");
	return watchdog;
    }

    private Set<Node> checkNodesFailed(Collection<Long> ids, boolean hasBackup)
	throws Exception
    {
        CheckNodesFailedTask task = new CheckNodesFailedTask(ids, hasBackup);
        taskScheduler.runTransactionalTask(task, taskOwner);
        return task.backups;
    }

    private class CheckNodesFailedTask extends AbstractKernelRunnable {
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
	System.err.println("Get shutdown nodes (should be removed)...");
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
        taskScheduler.runTransactionalTask(new AbstractKernelRunnable() {
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
	    
	    if (nodes.get(node) == null) {
		System.err.println(
		    "DummyRecoveryListener.recover: adding node: " + node);
	    } else {
		System.err.println(
		    "DummyRecoveryListener.recover: REPLACING node: " + node);
	    }
	    nodes.put(node, future);
	}

	void checkRecoveryNotifications(int expectedSize) {
	    if (nodes.size() != expectedSize) {
		fail("Expected " + expectedSize + " recover requests, " +
		     "received: " + nodes.size());
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
