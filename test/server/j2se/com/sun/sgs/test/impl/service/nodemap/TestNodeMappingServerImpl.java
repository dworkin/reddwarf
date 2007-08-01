/**
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.nodemap.NodeImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;

public class TestNodeMappingServerImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
        DataStoreImpl.class.getName();

    /** The name of the NodeMappingServerImpl class. */
    private static final String NodeMappingServerClassName =
        NodeMappingServerImpl.class.getName();
   
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestNodeMappingServerImpl.db";

    /** The port for the server. */
    private static int SERVER_PORT = 0;
    
    /** Amount of time to wait before something might be removed. */
    private static int REMOVE_TIME = 250;

    /** Properties for the nodemap server and data service. */
    private static Properties serviceProps;
    
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;
    
    /** The services. */
    // TODO add watchdog
    private DataServiceImpl dataService;
    private NodeMappingServerImpl nodeMappingServer;
    
    /** The number of nodes we start up for testing. */
    private final int NUM_NODES = 5;
    
    private boolean passed = false;

    /** non-pubic methods in NodeMappingServerImpl */
    private Method configureMethod;
    private Method commitConfigureMethod;
    private Method shutdownMethod;
    private Method addDummyNodeMethod;
    private Method getNodeForIdentityMethod;
    private Method reportFoundKeysMethod;
    private Method getPortMethod;
    
    private String serverPortPropertyName;
    
    /** Constructs a test instance. */
    public TestNodeMappingServerImpl(String name) throws Exception {
        super(name);
        
        configureMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "configure",
                new Class[] {ComponentRegistry.class, TransactionProxy.class});
        configureMethod.setAccessible(true);
        
        commitConfigureMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "commitConfigure",
                new Class[] {});
        commitConfigureMethod.setAccessible(true);
        
        shutdownMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "shutdown",
                new Class[] {});
        shutdownMethod.setAccessible(true);
        
        addDummyNodeMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "addDummyNode",
                new Class[] {long.class});
        addDummyNodeMethod.setAccessible(true);
        
        getNodeForIdentityMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "getNodeForIdentity",
                new Class[] {Identity.class});
        getNodeForIdentityMethod.setAccessible(true);
        
        reportFoundKeysMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "reportFoundKeys",
                new Class[] {Identity.class});
        reportFoundKeysMethod.setAccessible(true);
        
        getPortMethod = NodeMappingServerImpl.class.getDeclaredMethod(
                "getPort",
                new Class[] {});
        getPortMethod.setAccessible(true);
        
        Field serverPortPropertyField = 
           NodeMappingServerImpl.class.getDeclaredField("SERVER_PORT_PROPERTY");
        serverPortPropertyField.setAccessible(true);
        serverPortPropertyName = (String) serverPortPropertyField.get(null);
        
        Field removeExpireField =
         NodeMappingServerImpl.class.getDeclaredField("REMOVE_EXPIRE_PROPERTY");
        removeExpireField.setAccessible(true);
        String removeExpireName = (String) removeExpireField.get(null);
        
        Field removeSleepField =
         NodeMappingServerImpl.class.getDeclaredField("REMOVE_SLEEP_PROPERTY");
        removeSleepField.setAccessible(true);
        String removeSleepName = (String) removeSleepField.get(null);
        
        serviceProps = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServerImpl",
            DataStoreImplClassName + ".directory", DB_DIRECTORY,
            removeExpireName, Integer.toString(REMOVE_TIME),
            removeSleepName, Integer.toString(REMOVE_TIME/2),
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
        
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
        
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
	    
	// create services
	dataService = createDataService(systemRegistry);
       
        nodeMappingServer = new NodeMappingServerImpl(serviceProps, systemRegistry);

	createTransaction(10000);

	// configure data service
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

        configureMethod.invoke(nodeMappingServer, serviceRegistry, txnProxy);

	commitTransaction();
        
        commitConfigureMethod.invoke(nodeMappingServer, (Object[])null);
        
        for (int i = 1; i <= NUM_NODES; i++) {
            addDummyNodeMethod.invoke(nodeMappingServer, i);
        }
	createTransaction();
        
        passed = false;
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
        
        if (nodeMappingServer != null) {
            shutdownMethod.invoke(nodeMappingServer);
            nodeMappingServer = null;
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

    public void testConstructor() throws Exception {
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap = new NodeMappingServerImpl(serviceProps, systemRegistry);
        } finally {
            if (nodemap != null) { 
                shutdownMethod.invoke(nodemap); 
            }
        }
    }

    public void testConstructorNullProperties() throws Exception {
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap = new NodeMappingServerImpl(null, systemRegistry);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) { 
                shutdownMethod.invoke(nodemap); 
            }
        }
    }

    public void testConstructorRequestedPort() throws Exception {
        final int PORT = 5556;
        Properties properties = createProperties(
            DataStoreImplClassName + ".directory", DB_DIRECTORY,
            StandardProperties.APP_NAME, "TestNodeMappingServerImpl",
            serverPortPropertyName, Integer.toString(PORT));
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap = new NodeMappingServerImpl(properties, systemRegistry);
            int nodemapPort = (Integer) getPortMethod.invoke(nodemap);
            assertEquals(PORT, nodemapPort);
        } finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
   
    public void testNegPort() throws Exception {
        Properties properties = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServerImpl",
            serverPortPropertyName, Integer.toString(-1));
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap = new NodeMappingServerImpl(properties, systemRegistry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
    
    public void testBigPort() throws Exception {
        Properties properties = createProperties(
            StandardProperties.APP_NAME, "TestNodeMappingServerImpl",
            serverPortPropertyName, Integer.toString(65536));
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap = new NodeMappingServerImpl(properties, systemRegistry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        } finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
    
    /* -- Test configure -- */

    public void testConfigureNullRegistry() throws Exception {
        NodeMappingServerImpl nodemap = null;	
        try {
            nodemap = 
                new NodeMappingServerImpl(serviceProps, systemRegistry);
            configureMethod.invoke(nodemap, null, new DummyTransactionProxy());
	    fail("Expected NullPointerException");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NullPointerException) {
                System.err.println(cause);
            } else {
                fail("Expected NullPointerException");
            }
	} finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
    
    public void testConfigureNullProxy() throws Exception {
        NodeMappingServerImpl nodemap = null;
	try {
            nodemap =
                new NodeMappingServerImpl(serviceProps, systemRegistry);
            configureMethod.invoke(nodemap, serviceRegistry, null);
	    fail("Expected NullPointerException");
	} catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NullPointerException) {
                System.err.println(cause);
            } else {
                fail("Expected NullPointerException");
            }
	} finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
    
    public void testConfigure() throws Exception {
        NodeMappingServerImpl nodemap = null;
        try {
            nodemap =
                new NodeMappingServerImpl(serviceProps, systemRegistry);
            configureMethod.invoke(nodemap, serviceRegistry, txnProxy);
        } finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap); 
            }
        }
    }
    
    public void testConfigureTwice() throws Exception {
	NodeMappingServerImpl nodemap = null;
        try {   
            nodemap = 
	     new NodeMappingServerImpl(serviceProps, systemRegistry);
            configureMethod.invoke(nodemap, serviceRegistry, txnProxy);
            configureMethod.invoke(nodemap, serviceRegistry, txnProxy);          
	    fail("Expected IllegalStateException");
	} catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException) {
                System.err.println(cause);
            } else {
                fail("Expected IllegalStateException");
            }
	} finally {
            if (nodemap != null) {
                shutdownMethod.invoke(nodemap);
            }
        }
    }
    
    /* -- Test assignNode -- */
    
    public void testAssignNode() throws Exception {
        commitTransaction();
        
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);      
        verifyMapCorrect(id); 
        Set<String> found = getFoundKeys(id);
        assertEquals(3, found.size()); 
    }
    
     public void testAssignNodeTwice() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        verifyMapCorrect(id);
        nodeMappingServer.assignNode(DataService.class, id);
        verifyMapCorrect(id);
        Set<String> found = getFoundKeys(id);
        assertEquals(3, found.size());
    }
     
     public void testAssignNodeTwiceDifferentService() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        verifyMapCorrect(id);
        nodeMappingServer.assignNode(DataServiceImpl.class, id);
        verifyMapCorrect(id);
        Set<String> found = getFoundKeys(id);
        // An additional setting for the DataService should have been added
        assertEquals(4, found.size());
     }
     
     /* -- Test canRemove -- */
     public void testCanRemove() throws Exception {
        commitTransaction();
        // Assign outside a transaction
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(NodeMappingServerImpl.class, id);
        verifyMapCorrect(id);
        Set<String> found = getFoundKeys(id);
        
        // Remove any of the status keys we can find
        createTransaction();
        for (String s : found) {
            if (s.contains(".status.")) {
                dataService.removeServiceBinding(s);
            }
        }
        commitTransaction();
        
        nodeMappingServer.canRemove(id);
        
        Thread.sleep(REMOVE_TIME * 2);

        verifyMapCorrect(id);
        found = getFoundKeys(id);
        assertEquals(0, found.size());
     }

     public void testCanRemoveDoesnt() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(NodeMappingServerImpl.class, id);
        // We can't remove this, as the status is still set.
        nodeMappingServer.canRemove(id);
        Thread.sleep(REMOVE_TIME * 2);

        verifyMapCorrect(id);
        Set<String> found = getFoundKeys(id);
        assertEquals(3, found.size());
    }
     
    /* -- Test moveIdentity -- */
    public void testMoveIdentity() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        
        verifyMapCorrect(id);

        Set<String> foundFirst = getFoundKeys(id);
        // We expect the to see the id, node, and one status key.
        assertEquals(3, foundFirst.size());
       
        // Get the method, as it's not public
        Method moveMethod = 
                (NodeMappingServerImpl.class).getDeclaredMethod("mapToNewNode", 
                        new Class[]{Identity.class, String.class, Node.class});
        moveMethod.setAccessible(true);
        long nodeId = 
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        Node node = new NodeImpl(nodeId);
        
        moveMethod.invoke(nodeMappingServer, id, null, node);

        Set<String> foundSecond = getFoundKeys(id);
        verifyMapCorrect(id);
        // There should now be zero status keys.
        assertEquals(2, foundSecond.size());
    }
    
    /* -- Test node failure -- */
    public void testNodeFailed() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        Set<String> foundFirst = getFoundKeys(id);
        // We expect the to see the id, node, and one status key.
        assertEquals(3, foundFirst.size());
        long firstNodeId = 
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        
        // Not sure how to handle this with the watchdog.
        nodeMappingServer.nodeFailed(new NodeImpl(firstNodeId));
        // Wait for things to settle down, but not enough time for
        // the remover to kick in
        Thread.sleep(REMOVE_TIME/2);
        long secondNodeId = 
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        
        Set<String> foundSecond = getFoundKeys(id);
        verifyMapCorrect(id);
        // There should now be zero status keys.
        assertEquals(2, foundSecond.size());
        assertTrue(firstNodeId != secondNodeId);
    }
    

    public void testNodeFailedRemove() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        Set<String> foundFirst = getFoundKeys(id);
        // We expect the to see the id, node, and one status key.
        assertEquals(3, foundFirst.size());
        long firstNodeId = 
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        
        // Not sure how to handle this with the watchdog.
        nodeMappingServer.nodeFailed(new NodeImpl(firstNodeId));
        // Wait for things to settle down, and this time we let it
        // go ahead and renove the id.
        Thread.sleep(REMOVE_TIME * 2);
        try {
            long secondNodeId = 
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
            fail("Expected NameNotBoundException");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NameNotBoundException) {
                System.err.println(cause);
            } else {
                fail("Expected NameNotBoundException");
            }
        }
    }
    
    public void testAllNodesFailed() throws Exception {
        commitTransaction();
        Identity id = new DummyIdentity();
        nodeMappingServer.assignNode(DataService.class, id);
        Set<String> foundFirst = getFoundKeys(id, "FIRST:");
        // We expect the to see the id, node, and one status key.
        assertEquals(3, foundFirst.size());
        long firstNodeId =
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        
        // Not sure how to test this with watchdog
        for (int i = 1; i <= NUM_NODES; i++) {
            nodeMappingServer.nodeFailed(new NodeImpl(i));
        }
        
        // Wait for things to settle down
        Thread.sleep(REMOVE_TIME/2);
        long secondNodeId =
                (Long) getNodeForIdentityMethod.invoke(nodeMappingServer, id);
        
        Set<String> foundSecond = getFoundKeys(id, "SECOND");
        verifyMapCorrect(id);
        // There should now be zero status keys.
        assertEquals(2, foundSecond.size());
        assertTrue(firstNodeId != secondNodeId);
    }
     
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
    private DummyTransaction createTransaction
            (long timeout) {
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
    
    /** These methods use the server invariant checking methods */
    private void verifyMapCorrect(Identity id) throws Exception {    
        createTransaction();
        assertTrue(nodeMappingServer.assertValid(id));
        commitTransaction();
    }  

    private Set<String> getFoundKeys(Identity id) throws Exception {
        return getFoundKeys(id, null);
    }
    
    private Set<String> getFoundKeys(Identity id, String msg) throws Exception {
        createTransaction();
        try {
            Set f = (Set) reportFoundKeysMethod.invoke(nodeMappingServer, id);
            Set<String> found = new HashSet<String>(f.size());
            for (Object o : f) {
                found.add((String)o);
            }
            if (msg != null) {
                for (String s : found) {
                    System.out.println(msg + ": " + s);
                }
            }
            return found;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                cause.printStackTrace();
                fail("Unexpected exception");
                return null;
            }

        } finally {
            commitTransaction();
        }
    }
    
}
