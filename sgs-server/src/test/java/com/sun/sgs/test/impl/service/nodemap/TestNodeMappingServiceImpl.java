/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.LocalNodePolicy;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.IdentityRelocationListener;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredNameRunner.class)
public class TestNodeMappingServiceImpl {

    /** Number of additional nodes to create for selected tests */
    private static final int NUM_NODES = 3;
    
    /** Reflective stuff */
    private static Method assertValidMethod;
    private static Method moveMethod;
    private static Field serverImplField;
    private static String VERSION_KEY;
    private static int MAJOR_VERSION;
    private static int MINOR_VERSION;
    static {
        try {
            Class nmsImpl = NodeMappingServiceImpl.class;
            moveMethod = UtilReflection.getMethod(NodeMappingServerImpl.class,
                    "mapToNewNode", Identity.class, String.class,
                     Node.class, long.class);
            assertValidMethod = 
                UtilReflection.getMethod(nmsImpl,
                                         "assertValid", Identity.class);
            serverImplField = UtilReflection.getField(nmsImpl, "serverImpl");
            
            Class nodeMapUtilClass = 
                Class.forName("com.sun.sgs.impl.service.nodemap.NodeMapUtil");
            
            VERSION_KEY = (String) 
                    getField(nodeMapUtilClass, "VERSION_KEY").get(null);
            MAJOR_VERSION = 
                    getField(nodeMapUtilClass, "MAJOR_VERSION").getInt(null);
            MINOR_VERSION =
                    getField(nodeMapUtilClass, "MINOR_VERSION").getInt(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /** The node that creates the servers */
    private SgsTestNode serverNode;
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];
    
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;
    
    /** A specific property we started with, for remove tests */
    private int removeTime;

    /** The renew interval for the watchdog service */
    private int renewTime;
    
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;
    
    /** The owner for tasks I initiate. */
    private Identity taskOwner;
    
    private NodeMappingService nodeMappingService;
    
    /** A mapping of node id ->NodeMappingListener, for listener checks */
    private Map<Long, TestListener> nodeListenerMap;
 
    private static Field getField(Class cl, String name) throws Exception {
        return UtilReflection.getField(cl, name);
    }

    @Before
    public void setUp() throws Exception {
        setUp(null);
    }

    protected void setUp(Properties props) throws Exception {
        nodeListenerMap = new HashMap<Long, TestListener>();
        
        serverNode = new SgsTestNode("TestNodeMappingServiceImpl", null, props);
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        removeTime = Integer.valueOf(
            serviceProps.getProperty(
                "com.sun.sgs.impl.service.nodemap.remove.expire.time"));
	renewTime = Integer.valueOf(
	    serviceProps.getProperty(
		"com.sun.sgs.impl.service.watchdog.server.renew.interval"));
        
        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
        
        nodeMappingService = serverNode.getNodeMappingService();
        
        // Add to our test data structures, so we can find these nodes
        // and listeners.
        TestListener listener = new TestListener();        
        nodeMappingService.addNodeMappingListener(listener);
        nodeListenerMap.put(serverNode.getNodeId(), listener);
    }
    
   
    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     */
    private void addNodes(Properties props) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[NUM_NODES];
        
        for (int i = 0; i < NUM_NODES; i++) {
            SgsTestNode node =  new SgsTestNode(serverNode, null, props);
            additionalNodes[i] = node;
        
            NodeMappingService nmap = node.getNodeMappingService();

            // Add to our test data structures, so we can find these nodes
            // and listeners.
            TestListener listener = new TestListener();        
            nmap.addNodeMappingListener(listener);
            nodeListenerMap.put(node.getNodeId(), listener);
        }
    }
        
    /** Shut down the nodes. */
    @After
    public void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(true);
    }

    
        ////////     The tests     /////////
    @Test
    public void testConstructor() {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
                new NodeMappingServiceImpl(
                            serviceProps, systemRegistry, txnProxy);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullProperties() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
                new NodeMappingServiceImpl(null, systemRegistry, txnProxy);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    @Test(expected = NullPointerException.class)
    public void testConstructorNullProxy() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
              new NodeMappingServiceImpl(serviceProps, systemRegistry, null);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorAppButNoServerHost() throws Exception {
        // Server start is false but we didn't specify a server host
        Properties props = 
                SgsTestNode.getDefaultProperties(
                    "TestNodeMappingServiceImpl", 
                    serverNode, 
                    SgsTestNode.DummyAppListener.class);
        props.remove(StandardProperties.SERVER_HOST);
	
        NodeMappingService nmap =
            new NodeMappingServiceImpl(props, systemRegistry, txnProxy);
    }
    
    @Test
    public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			serverNode.getDataService()
                        .getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }
    
    @Test
    public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new NodeMappingServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestNodeMappingServiceImpl", serverNode, null),
	    systemRegistry, txnProxy);  
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMajorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

        new NodeMappingServiceImpl(serviceProps, systemRegistry, txnProxy);  
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMinorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

        new NodeMappingServiceImpl(serviceProps, systemRegistry, txnProxy);  
    }
    
    @Test
    public void testReady() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
                new NodeMappingServiceImpl(
		    SgsTestNode.getDefaultProperties(
			"TestNodeMappingServiceImpl", serverNode, null),
		    systemRegistry, txnProxy);
            TestListener listener = new TestListener();        
            nodemap.addNodeMappingListener(listener);
            
            // We have NOT called ready yet.
            final Identity id = new IdentityImpl("first");
            nodemap.assignNode(NodeMappingService.class, id);
            
            txnScheduler.runTask(
                new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        nodeMappingService.getNode(id);
                    }
                }, taskOwner);
            
            // Ensure the listener has not been called yet.
            assertTrue(listener.isClear());

            nodemap.ready();
            // Listener should be notified.
            listener.waitForNotification();
            
            // no old node
            checkIdAdded(listener, id, null);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    /* -- Test Service -- */
    @Test
    public void testGetName() {
        System.out.println(nodeMappingService.getName());
    }
    
    /* -- Test assignNode -- */
    @Test
    public void testAssignNode() throws Exception {   
        // Assign outside a transaction
        final Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
                
        verifyMapCorrect(id);
	TestListener l = nodeListenerMap.get(serverNode.getNodeId());
        l.waitForNotification();
       
        // Now expect to be able to find the identity
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = nodeMappingService.getNode(id);
                    // Make sure we got a notification, no old node
                    TestListener listener = nodeListenerMap.get(node.getId());
                    checkIdAdded(listener, id, null);
                }
        }, taskOwner);
    }
    
    @Test(expected = NullPointerException.class)
    public void testAssignNodeNullServer() throws Exception {
        nodeMappingService.assignNode(null, new IdentityImpl("first"));
    }
    
    @Test(expected = NullPointerException.class)
    public void testAssignNodeNullIdentity() throws Exception {
        nodeMappingService.assignNode(NodeMappingService.class, null); 
    }
    
    @Test
    public void testAssignNodeTwice() throws Exception {
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        // Now expect to be able to find the identity
        GetNodeTask task1 = new GetNodeTask(id);
        txnScheduler.runTask(task1, taskOwner);
        Node node1 = task1.getNode();
        
        // There shouldn't be a problem if we assign it twice;  as an 
        // optimization we shouldn't call out to the server
        nodeMappingService.assignNode(NodeMappingService.class, id);
        verifyMapCorrect(id);
        
        // Now expect to be able to find the identity
        GetNodeTask task2 = new GetNodeTask(id);
        txnScheduler.runTask(task2, taskOwner);
        Node node2 = task2.getNode();
        assertEquals(node1, node2);
    }
    
    @Test
    public void testAssignMultNodes() throws Exception {
        // This test is partly so I can compare the time it takes to
        // assign one node, or the same node twice
        addNodes(null);
        
        final int MAX = 25;
        Identity ids[] = new Identity[MAX];
        for (int i = 0; i < MAX; i++) {
            ids[i] = new IdentityImpl("identity" + i);         
            nodeMappingService.assignNode(NodeMappingService.class, ids[i]);
                
            verifyMapCorrect(ids[i]);
        }

        for (int j = 0; j < MAX; j++) {
            final Identity id = ids[j];
            txnScheduler.runTask(
                new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        nodeMappingService.getNode(id);
                    }
            }, taskOwner);
        }
    }
    
    @Test
    public void testRoundRobinAutoMove() throws Exception {
        // Remove what happened at setup().  I know, I know...
        tearDown();
	serviceProps = SgsTestNode.getDefaultProperties(
	    "TestNodeMappingServiceImpl", null, null);
        
        final int MOVE_COUNT = 5;
        // Create a new nodeMappingServer which will move an identity
        // automatically every so often.  
        serviceProps.setProperty(
                "com.sun.sgs.impl.service.nodemap.policy.movecount", 
                String.valueOf(MOVE_COUNT));

        setUp(serviceProps);
        addNodes(null);

        final List<Identity> ids = new ArrayList<Identity>();
        final List<Node> assignments = new ArrayList<Node>();
        
        final WatchdogService watchdog = serverNode.getWatchdogService();
        // First, Gather up any ids assigned by the other services
        // The set of nodes the watchdog knows about
        final Set<Node> nodes = new HashSet<Node>();
        
        // Gather up the nodes
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Iterator<Node> iter = watchdog.getNodes();
                    while (iter.hasNext()) {
                        nodes.add(iter.next());
                    }       

                }
        }, taskOwner);
        
        // For each node, gather up the identities
        for (final Node node : nodes) {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Iterator<Identity> idIter = 
                        nodeMappingService.getIdentities(node.getId());
                    while (idIter.hasNext()) {
                        Identity id = idIter.next();
                        ids.add(id);
                        assignments.add(nodeMappingService.getNode(id));
                    }    
                }
            }, taskOwner);
        }
        
        // Now start adding our identities.  The round robin policy
        // should cause a random identity to move while we do this.
        for (int i = 0; i < MOVE_COUNT; i++) {
            Identity id = new IdentityImpl("identity" + i);
            ids.add(id);
            nodeMappingService.assignNode(DataService.class, id);
            verifyMapCorrect(id);

            GetNodeTask task = new GetNodeTask(id);
            txnScheduler.runTask(task, taskOwner);
            assignments.add(task.getNode());
        }

        // We expected an automatic move to have occurred.
        boolean foundDiff = false;
        final int size = ids.size();
        for (int i = 0; i < size; i++) {
            GetNodeTask task = new GetNodeTask(ids.get(i));
            txnScheduler.runTask(task, taskOwner);
            Node current = task.getNode();
            foundDiff = foundDiff || 
                        (current.getId() != assignments.get(i).getId());
        }

        assertTrue("expected an id to move", foundDiff);
     }
    
    @Test
    public void testLocalNodePolicy() throws Exception {
        // Remove what happened at setup().  I know, I know...
        tearDown();
	serviceProps = SgsTestNode.getDefaultProperties(
	    "TestNodeMappingServiceImpl", null, null);

        // Create a new nodeMappingServer which will move an identity
        // automatically every so often.  
        serviceProps.setProperty(
                "com.sun.sgs.impl.service.nodemap.policy.class", 
                LocalNodePolicy.class.getName());

        setUp(serviceProps);
        addNodes(null);

        Map<Identity, Long> idMap = new HashMap<Identity, Long>();
        // Assign an identity on each of our nodes
        for (int i = 0; i < NUM_NODES; i++) {
            Identity id = new IdentityImpl("Identity" + i);
            additionalNodes[i].getNodeMappingService().
                                        assignNode(DataService.class, id);
            idMap.put(id, additionalNodes[i].getNodeId());
        }
        
        // Now test each identity to see where it was actually assigned
        for (Identity id : idMap.keySet()) {
            GetNodeTask task = new GetNodeTask(id);
            txnScheduler.runTask(task, taskOwner);
            long expectedNodeId = (long) idMap.get(id);
            assertEquals(expectedNodeId, task.getNodeId());
        }

     }
    
    @Test (expected = IllegalStateException.class)
    public void testAssignNodeInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                nodeMappingService.assignNode(NodeMappingService.class, 
                                              new IdentityImpl("first"));
            }
        }, taskOwner);
    }
    
    /* -- Test getNode -- */
    @Test(expected = NullPointerException.class)
    public void testGetNodeNullIdentity() throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        nodeMappingService.getNode(null);
                    }
            }, taskOwner);
    } 
    
    @Test(expected = UnknownIdentityException.class)
    public void testGetNodeBadIdentity() throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        nodeMappingService.getNode(new IdentityImpl("first"));
                    }
            }, taskOwner);
    }
   
    @Test
    public void testGetNode() {
        final Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        try {
            txnScheduler.runTask(
                    new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            nodeMappingService.getNode(id);
                        }
                }, taskOwner);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception");
        }
    }
    

    // Check to see if identities are changing in a transaction
    // and that any caching of identities in transaction works.
    @Test
    public void testGetNodeMultiple() throws Exception {
        // A better test would have another thread racing to change
        // the identity.
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node node1 = task.getNode();
        txnScheduler.runTask(task, taskOwner);
        Node node2 = task.getNode();
        txnScheduler.runTask(task, taskOwner);
        Node node3 = task.getNode();
        assertEquals(node1, node2);
        assertEquals(node1, node3);
        assertEquals(node2, node3);
    }
    
    /*-- Test getIdentities --*/
    @Test(expected = UnknownNodeException.class)
    public void testGetIdentitiesBadNode() throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        nodeMappingService.getIdentities(999L);
                    }
            }, taskOwner);
    }
   
    @Test
    public void testGetIdentities() throws Exception {
        final Identity id1 = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id1);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = nodeMappingService.getNode(id1);
		    Set<Identity> foundSet = new HashSet<Identity>();
                    Iterator<Identity> ids = 
                        nodeMappingService.getIdentities(node.getId());
                    while (ids.hasNext()) {
                        foundSet.add(ids.next());
		    }
		    assertTrue(foundSet.contains(id1));
                }
        }, taskOwner);
    }
    
    @Test
    public void testGetIdentitiesNoIds() throws Exception {
        addNodes(null);
        // This test assumes that we can create a node that has no
        // assignments.  That's currently true (Dec 11 2007).
        final long nodeId = additionalNodes[NUM_NODES - 1].getNodeId();

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Iterator<Identity> ids = 
                        nodeMappingService.getIdentities(nodeId);
                    while (ids.hasNext()) {
                        fail("expected no identities on this node " + 
                             ids.next());
                    }
                }
        }, taskOwner);
    }
    
    @Test
    public void testGetIdentitiesMultiple() throws Exception {
        addNodes(null);
        
        final int MAX = 8;
        Identity ids[] = new Identity[MAX];
        for (int i = 0; i < MAX; i++ ) {
            ids[i] = new IdentityImpl("dummy" + i);
            nodeMappingService.assignNode(NodeMappingService.class, ids[i]);
        }
            
        Set<Node> nodeset = new HashSet<Node>();
        Node nodes[] = new Node[MAX];
          
        for (int j = 0; j < MAX; j++) {
            GetNodeTask task = new GetNodeTask(ids[j]);
            txnScheduler.runTask(task, taskOwner);
            Node n = task.getNode();
            nodes[j] = n;
            nodeset.add(n);
        }
        
        // Set up our own internal node map based on the info above
        Map<Node, Set<Identity>> nodemap = new HashMap<Node, Set<Identity>>();
        for (Node n : nodeset) {
            nodemap.put(n, new HashSet<Identity>());
        }
        for (int k = 0; k < MAX; k++) {
            Set<Identity> s = nodemap.get(nodes[k]);
            s.add(ids[k]);
        }
        
        for (final Node node : nodeset) {
            final Set s = nodemap.get(node);
            
            txnScheduler.runTask(new TestAbstractKernelRunnable(){
                public void run() throws Exception {
		    Set<Identity> foundSet = new HashSet<Identity>();
                    Iterator<Identity> idIter = 
                        nodeMappingService.getIdentities(node.getId());
                    while (idIter.hasNext()) {
                        foundSet.add(idIter.next());
		    }
		    assertTrue(foundSet.containsAll(s));
                }
            }, taskOwner);
        }
        
    }
    
    /* -- Test setStatus -- */
    @Test(expected = NullPointerException.class)
    public void testSetStatusNullService() throws Exception {
        nodeMappingService.setStatus(null, new IdentityImpl("first"), true);
    }
    
    @Test(expected = NullPointerException.class)
    public void testSetStatusNullIdentity() throws Exception {
        nodeMappingService.setStatus(NodeMappingService.class, null, true);
    }
    @Test (expected = IllegalStateException.class)
    public void testSetStatusInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                nodeMappingService.setStatus(NodeMappingService.class, 
                                              new IdentityImpl("first"), true);
            }
        }, taskOwner);
    }
    
    @Test
    public void testSetStatusRemove() throws Exception {
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node node = task.getNode();
        
        // clear out the listener
        TestListener listener = nodeListenerMap.get(node.getId());
	listener.waitForNotification();
        listener.clear();
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        listener.waitForNotification(removeTime * 4);
        
        try {
            txnScheduler.runTask(task, taskOwner);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {
            // Make sure we got a notification
            checkIdRemoved(listener, id, null);
        }
    }
    
    @Test
    public void testSetStatusMultRemove() throws Exception {
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node node = task.getNode();
        
        // clear out the listener
        TestListener listener = nodeListenerMap.get(node.getId());
	listener.waitForNotification();
        listener.clear();
        // SetStatus is idempotent:  it doesn't matter how often a particular
        // service says an id is active.
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        // Likewise, it should be OK to make multiple "false" calls.
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        nodeMappingService.setStatus(NodeMappingService.class, id, false);

        listener.waitForNotification(removeTime * 4);
        
        try {
            txnScheduler.runTask(task, taskOwner);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {
            // Make sure we got a notification
            checkIdRemoved(listener, id, null);
        }
    }
    
    /**
     * Regression test for sgs-server issue #140, node mapping server
     * is logging at too high a level in a particular scenario.
     */
    @Test
    public void testSetStatusQuickMultRemove() throws Exception {  
        Identity id = new IdentityImpl("something");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node node = task.getNode();
        
        // clear out the listener
        TestListener listener = nodeListenerMap.get(node.getId());
	listener.waitForNotification();
        listener.clear();

        // We arrange for the test to fail if there is a WARNING log message
        // in the time period we're interested in.  The threads use an
        // Exchanger to synchronize and get results from the logger.
        final Exchanger<Boolean> errorExchanger = new Exchanger<Boolean>();
        
        Logger logger = 
            Logger.getLogger("com.sun.sgs.impl.service.nodemap.server");
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    // Tell the parent thread we've seen a WARNING message.
                    try { 
                        errorExchanger.exchange(Boolean.TRUE);
                    } catch (InterruptedException ignored) {
                        // do nothing
                    }
                }
                return true;
            }
        });
        
        // Much like testSetStatusMultRemove, but need to check logging
        // output for inappropriate warning message.
        // We're simulating an identity that is logged in, logged out...
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        // ... and then immediately logged in and out again.
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        nodeMappingService.setStatus(NodeMappingService.class, id, false); 
        
        // Wait up to removeTime * 4, and see if we got a WARNING log message
        try {
            Boolean error = errorExchanger.exchange(null, 
                                                    removeTime * 4, 
                                                    TimeUnit.MILLISECONDS);
            if (error) {
                fail(" Got a log record at level WARNING");
            }
        } catch (TimeoutException e) {
            // There might be multiple messages at different levels, or none
            // at all:  we are only looking for confusing WARNING messages
            // when there is actually no error to warn about.
            System.out.println("OK: Time out without a WARNING log message");
        }
        
        // Remove our test filter and reset the logging level.
        logger.setFilter(null);
        logger.setLevel(oldLevel);
        
        // Sanity check, be sure our listener still gets a single notification
        // in this log in, out, in, out scenario.
	listener.waitForNotification();
        try {
            txnScheduler.runTask(task, taskOwner);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {
            // Make sure we got a notification
            checkIdRemoved(listener, id, null);
        }
    }
        
    @Test
    public void testSetStatusNoRemove() throws Exception {
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        GetNodeTask task = new GetNodeTask(id);
        try {
            txnScheduler.runTask(task, taskOwner);
        } catch (UnknownIdentityException e) {
            fail("Expected UnknownIdentityException");
        }
        
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        Thread.sleep(removeTime * 4);
        // Error if we cannot find the identity!
        try {
            txnScheduler.runTask(task, taskOwner);
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
    }
    
    /* -- Test private mapToNewNode -- */
    @Test
    public void testListenersOnMove() throws Exception {   
        // We need some additional nodes for this test to work correctly.
        addNodes(null);
        
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node firstNode = task.getNode();
        long firstNodeId = task.getNodeId();
        TestListener firstNodeListener = nodeListenerMap.get(firstNodeId);
	firstNodeListener.waitForNotification();

        NodeMappingServerImpl server = 
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);
        
        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        // ... and invoke the method
        moveMethod.invoke(server, id, null, firstNode, firstNodeId);
        
        txnScheduler.runTask(task, taskOwner);
        Node secondNode = task.getNode();
        TestListener secondNodeListener = 
                nodeListenerMap.get(secondNode.getId());

	firstNodeListener.waitForNotification();
	secondNodeListener.waitForNotification();

        checkIdMoved(firstNodeListener, firstNode,
                     secondNodeListener, secondNode, id);
    }


    /* -- Test identity relocation listeners -- */
    @Test(expected = NullPointerException.class)
    public void testAddNullIdentityRelocationListener() throws Exception {
        nodeMappingService.addIdentityRelocationListener(null);
    }

    @Test
    public void testIdRelocationListenerNotification() throws Exception {
        addNodes(null);

        // Add id relocation listeners to each node, and keep a map of them.
        Map<Long, TestRelocationListener> moveMap =
                new HashMap<Long, TestRelocationListener>();
        addRelocationListeners(false, moveMap);

        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);
        Node firstNode = task.getNode();
        long firstNodeId = task.getNodeId();
        TestListener firstNodeListener = nodeListenerMap.get(firstNodeId);
        TestRelocationListener idListener = moveMap.get(firstNodeId);

	firstNodeListener.waitForNotification();

        NodeMappingServerImpl server =
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);

        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        for (TestRelocationListener lis : moveMap.values()) {
            lis.clear();
        }
        // ... and invoke the method
        moveMethod.invoke(server, id, null, firstNode, firstNodeId);

        // Give the id relocation listener a chance to finish, and the
        // actual node assignment to complete.
        idListener.waitForNotification();

        txnScheduler.runTask(task, taskOwner);
        Node secondNode = task.getNode();
        long secondNodeId = task.getNodeId();
        TestListener secondNodeListener =
                nodeListenerMap.get(secondNodeId);

	secondNodeListener.waitForNotification();

        checkRelocationNotification(idListener, id, secondNodeId);

        // Make sure no other listeners were affected
        for (TestRelocationListener listener : moveMap.values()) {
            if (listener != idListener) {
                assertTrue(listener.isClear());
            }
        }

        // Make sure the node mapping listeners were correctly updated
        checkIdMoved(firstNodeListener, firstNode,
                     secondNodeListener, secondNode, id);
    }

    @Test
    public void testIdRelocNotificationOldNodeFailed() throws Exception {
        addNodes(null);

        // Add id relocation listeners to each node, and keep a map of them.
        Map<Long, TestRelocationListener> moveMap =
                new HashMap<Long, TestRelocationListener>();
        addRelocationListeners(true, moveMap);

        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);;
        long firstNodeId = task.getNodeId();
        Node firstNode = task.getNode();
        TestListener firstNodeListener = nodeListenerMap.get(firstNodeId);
        TestRelocationListener idListener = moveMap.get(firstNodeId);

	firstNodeListener.waitForNotification();

        NodeMappingServerImpl server =
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);

        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        for (TestRelocationListener lis : moveMap.values()) {
            lis.clear();
        }
        // ... and invoke the method
        Long newNode =
            (Long) moveMethod.invoke(server, id, null, 
                                     task.getNode(), firstNodeId);

        // Give the id relocation listener a chance to finish.
        idListener.waitForNotification();

        txnScheduler.runTask(task, taskOwner);
        long secondNodeId = task.getNodeId();

        // Make sure the node hasn't actually moved yet.
        assertEquals(firstNodeId, secondNodeId);

        // Check the id relocation listener
        checkRelocationNotification(idListener, id, newNode);

        // Make sure no other listeners were affected
        for (TestRelocationListener listener : moveMap.values()) {
            if (listener != idListener) {
                assertTrue(listener.isClear());
            }
        }

        // Ensure that the node mapping listeners haven't been called yet
        for (TestListener listener : nodeListenerMap.values()) {
            assertTrue(listener.isClear());
        }

        // Now, cause the old node to fail.  First we have to find the
        // correct test node.
        for (SgsTestNode sgs : additionalNodes) {
            if (sgs.getNodeId() == firstNodeId) {
                System.out.println("Shutting down node " + firstNodeId);
                sgs.shutdown(false);
                break;
            }
        }

        // Wait for notification.  We expect to be notified on the 
        // newNode assigned above.
        TestListener secondNodeListener = nodeListenerMap.get(newNode);
        secondNodeListener.waitForNotification(renewTime * 2);

        txnScheduler.runTask(task, taskOwner);
        assertEquals((long) newNode, task.getNodeId());

        checkIdAdded(secondNodeListener, id, firstNode);
    }

    @Test
    public void testIdRelocNotificationTwice() throws Exception {
        addNodes(null);

        // Add id relocation listeners to each node, and keep a map of them.
        Map<Long, TestRelocationListener> moveMap =
                new HashMap<Long, TestRelocationListener>();
        addRelocationListeners(true, moveMap);

        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);;
        long firstNodeId = task.getNodeId();
        Node firstNode = task.getNode();
        TestListener firstNodeListener = nodeListenerMap.get(firstNodeId);
        TestRelocationListener idListener = moveMap.get(firstNodeId);

	firstNodeListener.waitForNotification();

        NodeMappingServerImpl server =
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);

        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        for (TestRelocationListener lis : moveMap.values()) {
            lis.clear();
        }
        // ... and invoke the method twice
        Long newNode =
            (Long) moveMethod.invoke(server, id, null,
                                     task.getNode(), firstNodeId);
        Long secondTryNode =
            (Long) moveMethod.invoke(server, id, null,
                                     task.getNode(), newNode);

        assertEquals(newNode, secondTryNode);

        // Give the id relocation listener a chance to finish.
        idListener.waitForNotification();

        txnScheduler.runTask(task, taskOwner);
        long secondNodeId = task.getNodeId();

        // Make sure the node hasn't actually moved yet.
        assertEquals(firstNodeId, secondNodeId);

        // Check the id relocation listener
        checkRelocationNotification(idListener, id, newNode);

        // Make sure no other listeners were affected
        for (TestRelocationListener listener : moveMap.values()) {
            if (listener != idListener) {
                assertTrue(listener.isClear());
            }
        }

        // Ensure that the node mapping listeners haven't been called yet
        for (TestListener listener : nodeListenerMap.values()) {
            assertTrue(listener.isClear());
        }

        // Now, allow the movement to complete.
        idListener.handler.completed();

        // Wait for notification.  We expect to be notified on the
        // newNode assigned above.
        TestListener secondNodeListener = nodeListenerMap.get(newNode);
        secondNodeListener.waitForNotification();
        txnScheduler.runTask(task, taskOwner);
        assertEquals((long) newNode, task.getNodeId());

        checkIdAdded(secondNodeListener, id, firstNode);
    }

    @Test
    public void testIdRelocNotificationNoResponse() throws Exception {
        // Set up using our properties
        tearDown();
	serviceProps = SgsTestNode.getDefaultProperties(
	    "TestNodeMappingServiceImpl", null, null);

        final int RELOCATION_TIME = 20;
        // Create a new nodeMappingServer with a very short timeout for
        // relocation expiration.
        serviceProps.setProperty(
                "com.sun.sgs.impl.service.nodemap.relocation.expire.time",
                String.valueOf(RELOCATION_TIME));

        setUp(serviceProps);
        addNodes(null);

        // Add id relocation listeners to each node, and keep a map of them.
        Map<Long, TestRelocationListener> moveMap =
                new HashMap<Long, TestRelocationListener>();
        addRelocationListeners(true, moveMap);

        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        GetNodeTask task = new GetNodeTask(id);
        txnScheduler.runTask(task, taskOwner);;
        long firstNodeId = task.getNodeId();
        Node firstNode = task.getNode();
        TestRelocationListener idListener = moveMap.get(firstNodeId);

        NodeMappingServerImpl server =
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);

        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        for (TestRelocationListener lis : moveMap.values()) {
            lis.clear();
        }
        // ... and invoke the method
        moveMethod.invoke(server, id, null, firstNode, firstNodeId);

        // Ensure that the idListener has been notified.
        idListener.waitForNotification();

        // ... and wait for the id relocation to expire.
        Thread.sleep(RELOCATION_TIME);

        // The identity should not have moved.
        txnScheduler.runTask(task, taskOwner);
        assertEquals(firstNodeId, task.getNodeId());

        // Try another move
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        for (TestRelocationListener lis : moveMap.values()) {
            lis.clear();
        }
        Long secondTryNode =
            (Long) moveMethod.invoke(server, id, null,
                                     firstNode, firstNodeId);

        // Give the id relocation listener a chance to finish.
        idListener.waitForNotification();
        // This time, allow the movement to complete.
        idListener.handler.completed();

        // Wait for notification.  We expect to be notified on the
        // secondTryNode assigned above.
        TestListener secondNodeListener = nodeListenerMap.get(secondTryNode);
        secondNodeListener.waitForNotification();

        txnScheduler.runTask(task, taskOwner);
        assertEquals((long) secondTryNode, task.getNodeId());

        checkIdAdded(secondNodeListener, id, firstNode);
    }

    /* -- Tests to see what happens if the server isn't available --*/
    @Test
    public void testEvilServerAssignNode() throws Exception {
        // replace the serverimpl with our evil proxy
        Object oldServer = swapToEvilServer(nodeMappingService);
        
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        Thread.sleep(100);
        try {
            assertFalse(serverNode.getWatchdogService().
                        isLocalNodeAliveNonTransactional());
        } catch (IllegalStateException e) {
            // All OK, the server is probably shutting down
        }
        swapToNormalServer(nodeMappingService, oldServer);
    }
    
    @Test
    public void testEvilServerGetNode() throws Exception {
        // replace the serverimpl with our evil proxy
        Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        Object oldServer = swapToEvilServer(nodeMappingService);
        
        GetNodeTask task = new GetNodeTask(id);
        // Reads should cause no trouble
        txnScheduler.runTask(task, taskOwner);
        swapToNormalServer(nodeMappingService, oldServer);
    }
    
    @Test
    public void testEvilServerGetIdentities() throws Exception {
        // put an identity in with a node
        // try to getNode that identity.
        final Identity id1 = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id1);
        
        Object oldServer = swapToEvilServer(nodeMappingService);
        
        txnScheduler.runTask(new TestAbstractKernelRunnable(){
                public void run() throws Exception {
                    Node node = nodeMappingService.getNode(id1);
		    Set<Identity> foundSet = new HashSet<Identity>();
                    Iterator<Identity> idIter = 
                        nodeMappingService.getIdentities(node.getId());   
                    while (idIter.hasNext()) {
                        foundSet.add(idIter.next());
		    }
		    assertTrue(foundSet.contains(id1));
                }
            }, taskOwner);
        swapToNormalServer(nodeMappingService, oldServer);
    }
    
    @Test
    public void testEvilServerSetStatus() throws Exception {
        final Identity id = new IdentityImpl("first");
        nodeMappingService.assignNode(NodeMappingService.class, id);

        Object oldServer = swapToEvilServer(nodeMappingService);
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        
        Thread.sleep(100);
        try {
            assertFalse(serverNode.getWatchdogService().
                        isLocalNodeAliveNonTransactional());
        } catch (IllegalStateException e) {
            // All OK, the server is probably shutting down
        }
        swapToNormalServer(nodeMappingService, oldServer);
    }
    
    private Object swapToEvilServer(NodeMappingService service) throws Exception {
        Field serverField = 
            NodeMappingServiceImpl.class.getDeclaredField("server");
        serverField.setAccessible(true);
        
        Object server = serverField.get(service);
        Object proxy = EvilProxy.proxyFor(server);
        serverField.set(service,proxy);
        return server;
    }
    
    private void swapToNormalServer(NodeMappingService service, Object old) 
        throws Exception 
    {
        Field serverField = 
            NodeMappingServiceImpl.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(service, old);
    }
        
//    public void testShutdown() {
//        // queue up a bunch of removes with very long timeouts
//        // make sure we terminate them early
//    }
    
    /** Utilties */
    
    /** Use the invariant checking method */
    private void verifyMapCorrect(final Identity id) throws Exception {  
        txnScheduler.runTask( new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                boolean valid = 
                    (Boolean) assertValidMethod.invoke(nodeMappingService, id);
                assertTrue(valid);
            }
        },taskOwner);
    }  
    
    /** 
     * Simple task to call getNode and return an id 
     */
    private class GetNodeTask extends TestAbstractKernelRunnable {
        /** The identity */
        private Identity id;
        /** The node the identity is assigned to */
        private Node node;
        private long nodeId;
        GetNodeTask(Identity id) {
            this.id = id;
        }
        public void run() throws Exception {
            node = nodeMappingService.getNode(id);
            nodeId = node.getId();
        }
        public Node getNode() { return node; }
        public long getNodeId() { return nodeId; }
    }
    
    /** A test node mapping listener */
    private class TestListener implements NodeMappingListener {
        private final List<Identity> addedIds = new ArrayList<Identity>();
        private final List<Node> addedNodes = new ArrayList<Node>();
        private final List<Identity> removedIds = new ArrayList<Identity>();
        private final List<Node> removedNodes = new ArrayList<Node>();

        // A notificationLock to let us know when the listener has been called.
        private final Object notificationLock = new Object();
        private boolean notified;
        public void mappingAdded(Identity identity, Node node) {
            addedIds.add(identity);
            addedNodes.add(node);
            synchronized (notificationLock) {
                notified = true;
                notificationLock.notifyAll();
            }
        }

        public void mappingRemoved(Identity identity, Node node) {
            removedIds.add(identity);
            removedNodes.add(node);
            synchronized (notificationLock) {
                notified = true;
                notificationLock.notifyAll();
            }
        }
        
        public void clear() {
            addedIds.clear();
            addedNodes.clear();
            removedIds.clear();
            removedNodes.clear();
            notified = false;
        }

        public boolean isClear() {
            return (addedIds.size() == 0) && (addedNodes.size() == 0) &&
                   (removedIds.size() == 0) && (removedNodes.size() == 0);
        }

        public void waitForNotification(long stop) throws InterruptedException {
            long stopTime = System.currentTimeMillis() + stop;
            synchronized (notificationLock) {
                while (!notified &&
                       System.currentTimeMillis() < stopTime)
                {
                    notificationLock.wait(100);
                }
            }
        }
        public void waitForNotification() throws InterruptedException {
            waitForNotification(1000);
        }

        public List<Identity> getAddedIds()   { return addedIds; }
        public List<Node> getAddedNodes()     { return addedNodes; }
        public List<Identity> getRemovedIds() { return removedIds; }
        public List<Node> getRemovedNodes()   { return removedNodes; }

        
        public String toString() {
            return "TestListener: AddedIds size: " + addedIds.size() +
                   " AddedNodes size: " + addedNodes.size() +
                   " removedIds size: " + removedIds.size() +
                   " removedNodes size: " + removedNodes.size();
        }
    }

        /* Check that the listener was notified of id moving from this node,
     * to node "to".
     */
    private void checkIdRemoved(TestListener listener, Identity id, Node to) {
        // Make sure we got a notification
        assertEquals(0, listener.getAddedIds().size());
        assertEquals(0, listener.getAddedNodes().size());

        List<Identity> removedIds = listener.getRemovedIds();
        List<Node> removedNodes = listener.getRemovedNodes();
        assertEquals(1, removedIds.size());
        assertEquals(1, removedNodes.size());
        assertTrue(removedIds.contains(id));
        // no new node
        assertTrue(removedNodes.contains(to));
    }

    /* Check that the listener was notified of id moving to this node,
     * from node "from"
     */
    private void checkIdAdded(TestListener listener, Identity id, Node from) {
        // Make sure we got a notification
        assertEquals(0, listener.getRemovedIds().size());
        assertEquals(0, listener.getRemovedNodes().size());

        List<Identity> addedIds = listener.getAddedIds();
        List<Node> addedNodes = listener.getAddedNodes();
        assertEquals(1, addedIds.size());
        assertEquals(1, addedNodes.size());
        assertTrue(addedIds.contains(id));
        // no new node
        assertTrue(addedNodes.contains(from));
    }

    /* Check that the listeners on each node were correctly notified. */
    private void checkIdMoved(TestListener firstNodeListener, Node firstNode,
                              TestListener secondNodeListener, Node secondNode,
                              Identity id)
    {
        // The id was removed from the first node
        checkIdRemoved(firstNodeListener, id, secondNode);

        // The id was added to the second node
        checkIdAdded(secondNodeListener, id, firstNode);

        // Make sure no other listeners were affected
        for (TestListener listener : nodeListenerMap.values()) {
            if (listener != firstNodeListener &&
                listener != secondNodeListener)
            {
                assertTrue(listener.isClear());
            }
        }
    }

    /** A test identity relocation listener. */
    private class TestRelocationListener implements IdentityRelocationListener {
        private final List<Identity> movingIds = new ArrayList<Identity>();
        private final List<Long> movingNodes = new ArrayList<Long>();

        // The corresponding node mapping listener for this node.
        private final TestListener nodeMapListener;

        // If true, this listener won't call completed right away.
        private final boolean asynch;

        // Set to false if a problem is encountered
        private boolean ok = true;

        // The pending handler, which allows us to control when the
        // complete method is called.
        SimpleCompletionHandler handler;
        // A notificationLock to let us know when the listener has been called.
        private final Object notificationLock = new Object();
        private boolean notified;

        TestRelocationListener(TestListener lis, boolean asynch) {
            nodeMapListener = lis;
            this.asynch = asynch;
        }
        public void prepareToRelocate(Identity id, long newNodeId,
                                      SimpleCompletionHandler handler)
        {
            movingIds.add(id);
            movingNodes.add(newNodeId);

            // Ensure that the nm listener has not been notified yet.
            // This assumes the nm listener was cleared before the move.
            ok = nodeMapListener.isClear();

            if (asynch) {
                this.handler = handler;
            } else {
                handler.completed();
            }
            synchronized (notificationLock) {
                notified = true;
                notificationLock.notifyAll();
            }
        }

        public void clear() {
            movingIds.clear();
            movingNodes.clear();
            handler = null;
            notified = false;
        }

        public boolean isClear() {
            return (movingIds.size() == 0) && (movingNodes.size() == 0) &&
                   (handler == null);
        }

        public void waitForNotification() throws InterruptedException {
            long stopTime = System.currentTimeMillis() + 1000;
            synchronized (notificationLock) {
                while (!notified &&
                       System.currentTimeMillis() < stopTime)
                {
                    notificationLock.wait(100);
                }
            }
        }

        public List<Identity> getIds()  { return movingIds; }
        public List<Long> getNodes()    { return movingNodes; }
        public boolean isOK()           { return ok; }
    }

    /* Add id relocation listeners for each node, putting them in the given
     * map.  If "asynch" is true, construct a listener which won't call
     * the completed method.
     */
    private void addRelocationListeners(boolean asynch,
                                    Map<Long, TestRelocationListener> moveMap)
    {
        long nodeId = serverNode.getNodeId();
        TestRelocationListener idListener =
                new TestRelocationListener(nodeListenerMap.get(nodeId), asynch);
        nodeMappingService.addIdentityRelocationListener(idListener);
        moveMap.put(nodeId, idListener);
        for (SgsTestNode node : additionalNodes) {
            nodeId = node.getNodeId();
            idListener =
                new TestRelocationListener(nodeListenerMap.get(nodeId), asynch);
            node.getNodeMappingService().
                    addIdentityRelocationListener(idListener);
            moveMap.put(nodeId, idListener);
        }
    }
    private void checkRelocationNotification(TestRelocationListener listener,
            Identity id, long toNode)
    {
        // The id is moving from the first node to the second
        assertEquals(1, listener.getIds().size());
        assertTrue(listener.getIds().contains(id));
        assertEquals(1, listener.getNodes().size());
        assertTrue(listener.getNodes().contains(toNode));
        // Make sure the node mapping listener hadn't been notified yet.
        assertTrue(listener.isOK());
    }
}
