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
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;

public class TestNodeMappingServiceImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
        DataStoreImpl.class.getName();

    /** The name of the NodeMappingServiceImpl class. */
    private static final String NodeMappingServiceClassName =
        NodeMappingServiceImpl.class.getName();
   
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestNodeMappingServiceImpl.db";

    /** The port for the server. */
    private static int SERVER_PORT = 0;

    /** Properties for the nodemap server and data service. */
    private static Properties serviceProps = createProperties(
        StandardProperties.APP_NAME, "TestNodeMappingServiceImpl",
        DataStoreImplClassName + ".directory", DB_DIRECTORY,
        NodeMappingServiceImpl.START_SERVER_PROPERTY, "true",
        NodeMappingServerImpl.SERVER_PORT_PROPERTY, Integer.toString(SERVER_PORT));
    
    /** Properties for creating the shared database. */
//    private static Properties dbProps = createProperties(
//	DataStoreImplClassName + ".directory",
//	DB_DIRECTORY,
//	StandardProperties.APP_NAME, "TestClientSessionServiceImpl");
    
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();
            


    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;
    
    private DataServiceImpl dataService;

    private NodeMappingServiceImpl nodeMappingService; 
    
    private boolean passed;
    
    /** Number of other services we'll start up */
    private final int NUM_NODES = 5;
    
    /** A mapping of node id -> services, used for remove tests */
    private Map<Long, NodeMappingService> nodemap;
    
    /** Reflective stuff, for non-public members. */
    private Field serverImplField;
    private Field localNodeIdField;
    
    /** Constructs a test instance. */
    public TestNodeMappingServiceImpl(String name) throws Exception {
        super(name);
        

        serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);

        localNodeIdField = 
                NodeMappingServiceImpl.class.getDeclaredField("localNodeId");
        localNodeIdField.setAccessible(true);
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
        
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
        
	// create services
	dataService = createDataService(systemRegistry);	     
        nodeMappingService = 
                new NodeMappingServiceImpl(serviceProps, systemRegistry);

	createTransaction(10000);

	// configure data service
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);
	
	serviceRegistry.registerAppContext();

        nodeMappingService.configure(serviceRegistry, txnProxy);
	

	commitTransaction();
        
        // Create a few services, so we have more than one node to
        // assign to.  For the remove tests, we also need to be careful
        // to call setStatus from the correct service, as setStatus tracks
        // status by node.
        NodeMappingServerImpl server = 
                (NodeMappingServerImpl)serverImplField.get(nodeMappingService);
        
        /** Properties for the nodemap server and data service. */
        Properties props = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServiceImpl",
            DataStoreImplClassName + ".directory", DB_DIRECTORY,
            NodeMappingServerImpl.SERVER_PORT_PROPERTY, 
                Integer.toString(server.getPort()));
        nodemap = new HashMap<Long, NodeMappingService>();
        for (int i = 0; i < NUM_NODES; i++) {
            createTransaction();
            NodeMappingService service = 
                    new NodeMappingServiceImpl(props, systemRegistry);
            service.configure(serviceRegistry, txnProxy);
            commitTransaction();
            
            Long id = (Long)localNodeIdField.get(service);
            nodemap.put(id, service);
        }
        
        
	createTransaction();
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
        
        if (nodeMappingService != null) {
            nodeMappingService.shutdown();
            nodeMappingService = null;
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

    ////////     The tests     /////////
    public void testConstructor() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = new NodeMappingServiceImpl(serviceProps, systemRegistry);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }

    public void testConstructorNullProperties() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = new NodeMappingServiceImpl(null, systemRegistry);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    /* -- Test configure -- */

    public void testConfigureNullRegistry() throws Exception {
        NodeMappingService nodemap = null;
	try {
            nodemap = new NodeMappingServiceImpl(serviceProps, systemRegistry);
            nodemap.configure(null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    public void testConfigureNullProxy() throws Exception {
	NodeMappingService nodemap = null;
        
	try {
            nodemap =
                new NodeMappingServiceImpl(serviceProps, systemRegistry);
            nodemap.configure(serviceRegistry, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    public void testConfigure() throws Exception {
        NodeMappingService nodemap = null;
        try {
            nodemap = new NodeMappingServiceImpl(serviceProps, systemRegistry);
            nodemap.configure(serviceRegistry, txnProxy);
        } finally {
            if (nodemap != null) { nodemap.shutdown(); }
        }
    }
    
    public void testConfigureTwice() throws Exception {
	try {
            nodeMappingService.configure(serviceRegistry, txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException ex) {
	    System.err.println(ex);
        }
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
        
        final int MAX = 4;
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
        // JANE should API specify a transaction exception will be thrown?
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
        System.out.println("Node id is " + node.getId());
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
             
            System.out.println("Node id is " + node.getId());
            int index = 0;
            while (idIter.hasNext()) {
                Identity ident = idIter.next();
                System.out.println(" Found id: " + ident);
                
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
        try {        
            Node node = nodeMappingService.getNode(id);
            service = nodemap.get(node.getId());
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
        commitTransaction();
        
        assertNotNull(service);
        service.setStatus(NodeMappingService.class, id, false);
        
        // This should be something * property for removewait
        Thread.sleep(10000);
        // Identity should now be gone
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
            fail("Expected UnknownIdentityException");
        } catch (UnknownIdentityException e) {

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
        // This should be something * property for removewait
        Thread.sleep(10000);
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
        // Assign outside a transaction
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
        Thread.sleep(10*1000);
        // Error if we cannot find the identity!
        createTransaction();
        try {
            Node node = nodeMappingService.getNode(id);
        } catch (UnknownIdentityException e) {
            fail("Unexpected UnknownIdentityException");
        }
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
	return new DataServiceImpl(serviceProps, registry);
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
        assertTrue(nodeMappingService.assertValid(id));
        commitTransaction();
    }  
}
