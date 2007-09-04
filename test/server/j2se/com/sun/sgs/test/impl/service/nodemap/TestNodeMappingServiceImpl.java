/**
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
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

import junit.framework.TestCase;

public class TestNodeMappingServiceImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
        DataStoreImpl.class.getName();

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
        "com.sun.sgs.impl.service.watchdog.server";
    
    /** The name of the NodeMappingServiceImpl class. */
    private static final String NodeMappingServiceClassName =
        NodeMappingServiceImpl.class.getName();
   
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestNodeMappingServiceImpl.db";

    /** The port for the watchdog */
    private static final int WATCHDOG_PORT = 0;

    /** The watchdog renew interval */
    private static long RENEW_INTERVAL = 1000;
    
    /** The port for the server. */
    private static final int SERVER_PORT = 0;

    /** Amount of time to wait before something might be removed. */
    private static final int REMOVE_TIME = 250;

    /** Properties for the servers. */
    private static Properties serviceProps;
    
    private static final DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();
          
    /** Reflective stuff, for non-public members. */
    // TODO consider making these fields static final
    private Field serverImplField;
    private Field localNodeIdField;
    private Method assertValidMethod;
    private Method getPortMethod;
    
    /** Number of other services we'll start up */
    private final int NUM_NODES = 3;

    private DummyAbstractKernelAppContext[] appContext = 
            new DummyAbstractKernelAppContext[NUM_NODES];
    private DummyComponentRegistry[] systemRegistry =
            new DummyComponentRegistry[NUM_NODES];
    private DummyComponentRegistry[] serviceRegistry =
            new DummyComponentRegistry[NUM_NODES];
    private DummyTransaction txn;
    
    /** Last ones created */
    private DataServiceImpl dataService;
    private WatchdogServiceImpl watchdogService;
    private NodeMappingServiceImpl nodeMappingService; 
    
    /** Ports actually used by initial services */
    private int serverPort;
    private int watchdogPort;
    
    private NodeMappingServerImpl server;
    
    private boolean passed;
    
    /** A mapping of node id -> services, used for remove tests */
    private Map<Long, NodeMappingService> nodemap;
    
    /** A mapping of node id ->NodeMappingListener, for listener checks */
    private Map<Long, TestListener> nodeListenerMap;
    
    private String serverPortPropertyName;
    
    /** Constructs a test instance. */
    public TestNodeMappingServiceImpl(String name) throws Exception {
        super(name);

        // Get all the things we need to find through reflection.
        serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);

        localNodeIdField = 
                NodeMappingServiceImpl.class.getDeclaredField("localNodeId");
        localNodeIdField.setAccessible(true);
        
        assertValidMethod =
                NodeMappingServiceImpl.class.getDeclaredMethod(
                    "assertValid",
                    new Class[] {Identity.class});
        assertValidMethod.setAccessible(true);
        
        getPortMethod = 
                NodeMappingServerImpl.class.getDeclaredMethod("getPort");
        getPortMethod.setAccessible(true);
        
        Field serverPortPropertyField = 
           NodeMappingServerImpl.class.getDeclaredField("SERVER_PORT_PROPERTY");
        serverPortPropertyField.setAccessible(true);
        serverPortPropertyName = (String) serverPortPropertyField.get(null);
        
        Field removeExpireField =
         NodeMappingServerImpl.class.getDeclaredField("REMOVE_EXPIRE_PROPERTY");
        removeExpireField.setAccessible(true);
        String removeExpireName = (String) removeExpireField.get(null);
        
        Field startServiceField =
         NodeMappingServiceImpl.class.getDeclaredField("SERVER_START_PROPERTY");
        startServiceField.setAccessible(true);
        String startServiceName = (String) startServiceField.get(null);
        
        serviceProps = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServerImpl",
            DataStoreImplClassName + ".directory", DB_DIRECTORY,
            WatchdogServerPropertyPrefix + ".start", "true",
            WatchdogServerPropertyPrefix + ".port", Integer.toString(WATCHDOG_PORT),
            WatchdogServerPropertyPrefix + ".renew.interval",
                Long.toString(RENEW_INTERVAL),
            startServiceName, "true",
            removeExpireName, Integer.toString(REMOVE_TIME),
            serverPortPropertyName, Integer.toString(SERVER_PORT));
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
        
        nodemap = new HashMap<Long, NodeMappingService>();
        nodeListenerMap = new HashMap<Long, TestListener>();
        
	appContext[0] = MinimalTestKernel.createContext();
	systemRegistry[0] = MinimalTestKernel.getSystemRegistry(appContext[0]);
	serviceRegistry[0] = MinimalTestKernel.getServiceRegistry(appContext[0]);
        // only one data service for now: not configured correctly
        dataService = createDataService(systemRegistry[0]);
        
        // Create the initial stack and grab our server field.
        createStack(appContext[0], systemRegistry[0], serviceRegistry[0], 
                    serviceProps, true);
        server = 
            (NodeMappingServerImpl)serverImplField.get(nodeMappingService);        
               
	createTransaction();
    }
    
    private void createStack(DummyAbstractKernelAppContext appContext,
                             DummyComponentRegistry systemRegistry,
                             DummyComponentRegistry serviceRegistry,
                             Properties props, boolean special) 
                 throws Exception
    {
        
	// create services
        // We are running with a non-multinode data service.
        // set data service classes in serviceRegistry
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

        watchdogService = 
                new WatchdogServiceImpl(props, systemRegistry, txnProxy);
        txnProxy.setComponent(WatchdogService.class, watchdogService);
        txnProxy.setComponent(WatchdogServiceImpl.class, watchdogService);
        serviceRegistry.setComponent(WatchdogService.class, watchdogService);
        serviceRegistry.setComponent(WatchdogServiceImpl.class, watchdogService);
        
        nodeMappingService = 
                new NodeMappingServiceImpl(props, systemRegistry, txnProxy);
        txnProxy.setComponent(NodeMappingService.class, nodeMappingService);
        txnProxy.setComponent(NodeMappingServiceImpl.class, nodeMappingService);
        serviceRegistry.setComponent(NodeMappingService.class, nodeMappingService);
        serviceRegistry.setComponent(NodeMappingServiceImpl.class, nodeMappingService);

	serviceRegistry.registerAppContext();
        
        // services ready
	dataService.ready();
	watchdogService.ready();
        nodeMappingService.ready();
        
        if (special) {
            NodeMappingServerImpl server = 
                (NodeMappingServerImpl)serverImplField.get(nodeMappingService);
            serverPort = (Integer) getPortMethod.invoke(server); 
            watchdogPort = watchdogService.getServer().getPort();
        }
        
        // Add to our test data structures, so we can find these nodes
        // and listeners.
        Long id = (Long) localNodeIdField.get(nodeMappingService);
        nodemap.put(id, nodeMappingService);

        TestListener listener = new TestListener();        
        nodeMappingService.addNodeMappingListener(listener);
        nodeListenerMap.put(id, listener);
    }
    
    /** Add additional nodes.  We only do this as required by the tests. */
    private void addNodes() throws Exception {
        /** Properties for the full stacks, don't start servers. */
        Properties props = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServiceImpl",
            DataStoreImplClassName + ".directory", DB_DIRECTORY,
            WatchdogServerPropertyPrefix + ".renew.interval",
                Long.toString(RENEW_INTERVAL),
            WatchdogServerPropertyPrefix + ".port", Integer.toString(watchdogPort),
            serverPortPropertyName, Integer.toString(serverPort));

        // Create the other nodes
        for (int i = 1; i < NUM_NODES; i++) {
            appContext[i] = MinimalTestKernel.createContext();
            systemRegistry[i] = 
                    MinimalTestKernel.getSystemRegistry(appContext[i]);
            serviceRegistry[i] = 
                    MinimalTestKernel.getServiceRegistry(appContext[i]);
            createStack(appContext[i], systemRegistry[i], serviceRegistry[i], 
                    props, false);
        }
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
            } catch (RuntimeException e) {
                if ((! clean) || passed) {
                    // ignore
                } else {
                    e.printStackTrace();
                }
            } finally {
                txn = null;
            }
        }
        
        // Shut them down in backwards order:  we want the servers shut
        // down last
        for (int i = NUM_NODES - 1; i >= 0; i--) {
            if (serviceRegistry[i] == null) {
                // we didn't add any additional nodes
                break;
            }
            
            watchdogService = serviceRegistry[i].getComponent(
                    WatchdogServiceImpl.class);
            nodeMappingService = serviceRegistry[i].getComponent(
                    NodeMappingServiceImpl.class);

            if (nodeMappingService != null) {
                nodeMappingService.shutdown();
                nodeMappingService = null;
            }
            
            if (watchdogService != null) {
                watchdogService.shutdown();
                watchdogService = null;
            }
        }
        // Finally, shut down our data service.
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
        // Static class: there is really only one context maintained.
        MinimalTestKernel.destroyContext(appContext[0]);
        
    }

    ////////     The tests     /////////
    public void testConstructor() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
                new NodeMappingServiceImpl(
                            serviceProps, systemRegistry[0], txnProxy);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }

    public void testConstructorNullProperties() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
                new NodeMappingServiceImpl(null, systemRegistry[0], txnProxy);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    public void testConstructorNullProxy() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = 
              new NodeMappingServiceImpl(serviceProps, systemRegistry[0], null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    public void testReady() throws Exception {
        NodeMappingService nodemap = null;
        try {
            commitTransaction();
            nodemap = 
                new NodeMappingServiceImpl(
                            serviceProps, systemRegistry[0], txnProxy);
            TestListener listener = new TestListener();        
            nodemap.addNodeMappingListener(listener);
            
            // We have NOT called ready yet.
            Identity id = new DummyIdentity();
            nodemap.assignNode(NodeMappingService.class, id);
            createTransaction();
            Node node = nodeMappingService.getNode(id);
            commitTransaction();
            
            // Ensure the listeners have not been called yet.
            List<Identity> addedIds = listener.getAddedIds();
            List<Node> addedNodes = listener.getAddedNodes();
            assertEquals(0, addedIds.size());
            assertEquals(0, addedNodes.size());
            assertEquals(0, listener.getRemovedIds().size());
            assertEquals(0, listener.getRemovedNodes().size());
            
            nodemap.ready();
            
            // Listeners should be notified.
            Thread.sleep(500);
            
            addedIds = listener.getAddedIds();
            addedNodes = listener.getAddedNodes();
            assertEquals(1, addedIds.size());
            assertEquals(1, addedNodes.size());
            assertTrue(addedIds.contains(id));
            // no old node
            assertTrue(addedNodes.contains(null));

            assertEquals(0, listener.getRemovedIds().size());
            assertEquals(0, listener.getRemovedNodes().size());
            
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    /* -- Test Service -- */
    public void testgetName() {
        System.out.println(nodeMappingService.getName());
    }
    
    /* -- Test NodeMappingService -- */    
    public void testAssignNode() throws Exception {
        commitTransaction();
        
        // Assign outside a transaction
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        verifyMapCorrect(id);
       
        // Now expect to be able to find the identity
        createTransaction();
        Node node = nodeMappingService.getNode(id);
        
        // Make sure we got a notification
        TestListener listener = nodeListenerMap.get(node.getId());
        List<Identity> addedIds = listener.getAddedIds();
        List<Node> addedNodes = listener.getAddedNodes();
        assertEquals(1, addedIds.size());
        assertEquals(1, addedNodes.size());
        assertTrue(addedIds.contains(id));
        // no old node
        assertTrue(addedNodes.contains(null));
        
        assertEquals(0, listener.getRemovedIds().size());
        assertEquals(0, listener.getRemovedNodes().size());
    }
    
    public void testAssignNodeNullServer() throws Exception {
        commitTransaction();
        try {
            nodeMappingService.assignNode(null, new DummyIdentity());
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            System.err.println(ex);  
        } 
    }
    
    public void testAssignNodeNullIdentity() throws Exception {
        commitTransaction();
        try {
            nodeMappingService.assignNode(NodeMappingService.class, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            System.err.println(ex);  
        } 
    }
    
    public void testAssignNodeTwice() throws Exception {
        // Assign outside a transaction
        commitTransaction();
        
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        // Now expect to be able to find the identity
        createTransaction();
        Node node1 = nodeMappingService.getNode(id);
        commitTransaction();
        
        // There shouldn't be a problem if we assign it twice;  as an 
        // optimization we shouldn't call out to the server
        nodeMappingService.assignNode(NodeMappingService.class, id);
        verifyMapCorrect(id);
        
        // Now expect to be able to find the identity
        createTransaction();
        Node node2 = nodeMappingService.getNode(id);
        assertEquals(node1, node2);
    }
    
    public void testAssignFourNodes() throws Exception {
        // This test is partly so I can compare the time it takes to
        // assign one node, or the same node twice
        commitTransaction();
        
        addNodes();
        
        final int MAX = 25;
        Identity ids[] = new Identity[MAX];
        for (int i = 0; i < MAX; i++) {
            ids[i] = new DummyIdentity("identity" + i);
            nodeMappingService.assignNode(NodeMappingService.class, ids[i]);
            verifyMapCorrect(ids[i]);
        }

        for (int j = 0; j < MAX; j++) {
            createTransaction();
            nodeMappingService.getNode(ids[j]);
        }
    }
    
    public void testAssignNodeInTransaction() throws Exception {
        // TODO should API specify a transaction exception will be thrown?
        nodeMappingService.assignNode(NodeMappingService.class, new DummyIdentity());
    }
    
    public void testGetNodeNullIdentity() throws Exception {
        try {
            nodeMappingService.getNode(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            System.err.println(ex);  
        }
    } 
    
    public void testGetNodeBadIdentity() {
        try {
            nodeMappingService.getNode(new DummyIdentity());
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException ex) {
            System.err.println(ex);
        }
    }
   
    public void testGetNode() throws Exception {
        // put an identity in with a node
        // try to getNode that identity.
        Identity id = new DummyIdentity();
        commitTransaction();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        Node node = nodeMappingService.getNode(id);
    }
    
    // Check to see if identities are changing in a transaction
    // and that any caching of identities in transaction works.
    public void testGetNodeMultiple() throws Exception {
        // JANE a better test would have another thread racing to change
        // the identity.
        Identity id = new DummyIdentity();
        commitTransaction();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        Node node1 = nodeMappingService.getNode(id);
        Node node2 = nodeMappingService.getNode(id);
        Node node3 = nodeMappingService.getNode(id);
        commitTransaction();
        assertEquals(node1, node2);
        assertEquals(node1, node3);
        assertEquals(node2, node3);
    }
    
    public void testGetIdentitiesBadNode() {
        try {
            nodeMappingService.getIdentities(999L);
            fail("Expected UnknownNodeException");
        } catch (UnknownNodeException ex) {
            System.err.println(ex);
        }
    }
   
    public void testGetIdentities() throws Exception {
        commitTransaction();
        // put an identity in with a node
        // try to getNode that identity.
        Identity id1 = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id1);
        
        createTransaction();
        
        Node node = nodeMappingService.getNode(id1);
        Iterator<Identity> ids = nodeMappingService.getIdentities(node.getId());
        while (ids.hasNext()) {
            Identity id = ids.next();
            assertEquals(id, id1);
        }
    }
    
    public void testGetIdentitiesMultiple() throws Exception {
        commitTransaction();
        
        addNodes();
        
        final int MAX = 8;
        Identity ids[] = new Identity[MAX];
        for (int i = 0; i < MAX; i++ ) {
            ids[i] = new DummyIdentity("dummy" + i);
            nodeMappingService.assignNode(NodeMappingService.class, ids[i]);
        }
            
        Set<Node> nodeset = new HashSet<Node>();
        Node nodes[] = new Node[MAX];
        createTransaction(MAX * 20);
          
        for (int j = 0; j < MAX; j++) {
            Node n = nodeMappingService.getNode(ids[j]);
            nodes[j] = n;
            nodeset.add(n);
        }
        
        commitTransaction();
        
        
        // Set up our own internal node map based on the info above
        Map<Node, Set<Identity>> nodemap = new HashMap<Node, Set<Identity>>();
        for (Node n : nodeset) {
            nodemap.put(n, new HashSet<Identity>());
        }
        for (int k = 0; k < MAX; k++) {
            Set<Identity> s = nodemap.get(nodes[k]);
            s.add(ids[k]);
        }
        
        for (Node node : nodeset) {
            Set s = nodemap.get(node);
            
            createTransaction(1000);
           
            Iterator<Identity> idIter = 
                    nodeMappingService.getIdentities(node.getId());   
            while (idIter.hasNext()) {
                Identity ident = idIter.next();         
                assertTrue(s.contains(ident));
            }
            commitTransaction();
        }
        
    }
   
    public void testSetStatusNullService() throws Exception {
        commitTransaction();
        try {
            nodeMappingService.setStatus(null, new DummyIdentity(), true);
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            System.err.println(ex);  
        }
    }
    
    public void testSetStatusNullIdentity() throws Exception {
        commitTransaction();
        try {
            nodeMappingService.setStatus(NodeMappingService.class, null, true);
            fail("Expected NullPointerException");
        } catch (NullPointerException ex) {
            System.err.println(ex);  
        }
    }
    
    public void testSetStatusRemove() throws Exception {
        commitTransaction();
        // If we simply call assignNode, then setStatus with false using
        // the nodeMappingService, the object won't be removed:  assignNode
        // will set the status for our service on the assigned node. 
        // We're not running in a truely multi-node manner, so we don't
        // have multiple services running.
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        
        // Find the node that we assigned to.  That's the one who needs
        // to set the status to false!
        NodeMappingService service = null;
        Node node = null;
        try {        
            node = nodeMappingService.getNode(id);
            service = nodemap.get(node.getId());     
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
        commitTransaction();
        // clear out the listener
        TestListener listener = nodeListenerMap.get(node.getId());
        listener.clear();
        
        assertNotNull(service);
        

        service.setStatus(NodeMappingService.class, id, false);

        Thread.sleep(REMOVE_TIME * 4);
        // Identity should now be gone
        createTransaction();
        try {
            node = nodeMappingService.getNode(id);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {
            // Make sure we got a notification
            assertEquals(0, listener.getAddedIds().size());
            assertEquals(0, listener.getAddedNodes().size());
            
            List<Identity> removedIds = listener.getRemovedIds();
            List<Node> removedNodes = listener.getRemovedNodes();
            assertEquals(1, removedIds.size());
            assertEquals(1, removedNodes.size());
            assertTrue(removedIds.contains(id));
            // no new node
            assertTrue(removedNodes.contains(null));
    
        }
    }
    
    public void testSetStatusMultRemove() throws Exception {
        commitTransaction();
        // Assign outside a transaction
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        NodeMappingService service = null;
        try {
            Node node = nodeMappingService.getNode(id);
            service = nodemap.get(node.getId());
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
        commitTransaction();
        
        assertNotNull(service);
        // SetStatus is idempotent:  it doesn't matter how often a particular
        // service says an id is active.
        service.setStatus(NodeMappingService.class, id, true);
        service.setStatus(NodeMappingService.class, id, true);
        // Likewise, it should be OK to make multiple "false" calls.
        service.setStatus(NodeMappingService.class, id, false);
        service.setStatus(NodeMappingService.class, id, false);

        Thread.sleep(REMOVE_TIME * 4);
        // Identity should now be gone
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {

        }
    }
        
    public void testSetStatusNoRemove() throws Exception {
        commitTransaction();

        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
        } catch (UnknownIdentityException e) {
            fail("Expected UnknownIdentityException");
        }
        commitTransaction();
        
        nodeMappingService.setStatus(NodeMappingService.class, id, false);
        nodeMappingService.setStatus(NodeMappingService.class, id, true);
        Thread.sleep(REMOVE_TIME * 4);
        // Error if we cannot find the identity!
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
    }
    
    public void testListenersOnMove() throws Exception {   
        commitTransaction();
        // We need some additional nodes for this test to work correctly.
        addNodes();
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);

        createTransaction();
        Node firstNode = nodeMappingService.getNode(id);
        commitTransaction();
        TestListener firstNodeListener = nodeListenerMap.get(firstNode.getId());
        
        // Get the method, as it's not public
        Method moveMethod = 
                (NodeMappingServerImpl.class).getDeclaredMethod("mapToNewNode", 
                        new Class[]{Identity.class, String.class, Node.class});
        moveMethod.setAccessible(true);
        
        // clear out the listeners
        for (TestListener lis : nodeListenerMap.values()) {
            lis.clear();
        }
        // ... and invoke the method
        moveMethod.invoke(server, id, null, firstNode);
        
        createTransaction();
        Node secondNode = nodeMappingService.getNode(id);
        commitTransaction();
        TestListener secondNodeListener = 
                nodeListenerMap.get(secondNode.getId());
        
        // The id was removed from the first node
        assertEquals(0, firstNodeListener.getAddedIds().size());
        assertEquals(0, firstNodeListener.getAddedNodes().size());

        List<Identity> removedIds = firstNodeListener.getRemovedIds();
        List<Node> removedNodes = firstNodeListener.getRemovedNodes();
        assertEquals(1, removedIds.size());
        assertEquals(1, removedNodes.size());
        assertTrue(removedIds.contains(id));
        // It moved to secondNode
        assertTrue(removedNodes.contains(secondNode));
        
        // Check the other node's listener
        assertEquals(0, secondNodeListener.getRemovedIds().size());
        assertEquals(0, secondNodeListener.getRemovedNodes().size());
        
        List<Identity> addedIds = secondNodeListener.getAddedIds();
        List<Node> addedNodes = secondNodeListener.getAddedNodes();
        assertEquals(1, addedIds.size());
        assertEquals(1, addedNodes.size());
        assertTrue(addedIds.contains(id));
        // firstNode was old node
        assertTrue(addedNodes.contains(firstNode));
        
        // Make sure no other listeners were affected
        for (TestListener listener : nodeListenerMap.values()) {
            if (listener != firstNodeListener && 
                listener != secondNodeListener) 
            {
                assertEquals(0, listener.getAddedIds().size());
                assertEquals(0, listener.getAddedNodes().size());
                assertEquals(0, listener.getRemovedIds().size());
                assertEquals(0, listener.getRemovedNodes().size());
            }
        }
    }
        
    /* -- Tests to see what happens if the server isn't available --*/
    public void testEvilServerAssignNode() throws Exception {
        // replace the serverimpl with our evil proxy
        commitTransaction();
        swapToEvilServer(nodeMappingService);
        
        Identity id = new DummyIdentity();

        // Nothing much will happen. Eventually, we'll cause the
        // stack to shut down.
        nodeMappingService.assignNode(NodeMappingService.class, id);        
    }
    
    public void testEvilServerGetNode() throws Exception {
        // replace the serverimpl with our evil proxy
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        swapToEvilServer(nodeMappingService);
        
        createTransaction();
        // Reads should cause no trouble
        Node node = nodeMappingService.getNode(id);
      
    }
    
    public void testEvilServerGetIdentities() throws Exception {
        commitTransaction();
        // put an identity in with a node
        // try to getNode that identity.
        Identity id1 = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id1);
        
        createTransaction();
        
        swapToEvilServer(nodeMappingService);
        
        // Reads should cause no trouble
        Node node = nodeMappingService.getNode(id1);
        Iterator<Identity> ids = nodeMappingService.getIdentities(node.getId());
        while (ids.hasNext()) {
            Identity id = ids.next();
            assertEquals(id, id1);
        }
    }
    
    public void testEvilServerSetStatus() throws Exception {
        commitTransaction();
        // If we simply call assignNode, then setStatus with false using
        // the nodeMappingService, the object won't be removed:  assignNode
        // will set the status for our service on the assigned node. 
        // We're not running in a truely multi-node manner, so we don't
        // have multiple services running.
        Identity id = new DummyIdentity();
        nodeMappingService.assignNode(NodeMappingService.class, id);
        
        createTransaction();
        
        // Find the node that we assigned to.  That's the one who needs
        // to set the status to false!
        NodeMappingService service = null;
        try {        
            Node node = nodeMappingService.getNode(id);
            service = nodemap.get(node.getId());
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
        commitTransaction();
        
        assertNotNull(service);
        swapToEvilServer(service);

        // JANE this test needs work

        service.setStatus(NodeMappingService.class, id, false);

        Thread.sleep(REMOVE_TIME * 4);
        // Identity should now be gone... this is a hole in the
        // implementation, currently.  It won't be removed.
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
            // This line should be uncommented if we want to support
            // disconnected servers.
//            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {

        }
    }
    
    private void swapToEvilServer(NodeMappingService service) throws Exception {
        Field serverField = 
            NodeMappingServiceImpl.class.getDeclaredField("server");
        serverField.setAccessible(true);
        
        Object server = serverField.get(service);
        Object proxy = EvilProxy.proxyFor(server);
        serverField.set(service,proxy);
    }
    
//    public void testShutdown() {
//        // queue up a bunch of removes with very long timeouts
//        // make sure we terminate them early
//    }
    
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
	return new DataServiceImpl(serviceProps, registry, txnProxy);
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
    
    /** Use the invariant checking method */
    private void verifyMapCorrect(Identity id) throws Exception {  
        createTransaction();
        boolean valid = 
                (Boolean) assertValidMethod.invoke(nodeMappingService, id);
        assertTrue(valid);
        commitTransaction();
    }  
    
    /** A test node mapping listener */
    private class TestListener implements NodeMappingListener {
        private final List<Identity> addedIds = new ArrayList<Identity>();
        private final List<Node> addedNodes = new ArrayList<Node>();
        private final List<Identity> removedIds = new ArrayList<Identity>();
        private final List<Node> removedNodes = new ArrayList<Node>();
        
        public void mappingAdded(Identity identity, Node node) {
            addedIds.add(identity);
            addedNodes.add(node);
        }

        public void mappingRemoved(Identity identity, Node node) {
            removedIds.add(identity);
            removedNodes.add(node);
        }
        
        public void clear() {
            addedIds.clear();
            addedNodes.clear();
            removedIds.clear();
            removedNodes.clear();
        }
        
        public List<Identity> getAddedIds()   { return addedIds; }
        public List<Node> getAddedNodes()     { return addedNodes; }
        public List<Identity> getRemovedIds() { return removedIds; }
        public List<Node> getRemovedNodes()   { return removedNodes; }
    }
}
