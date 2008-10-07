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

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyNonDurableTransactionParticipant;
import com.sun.sgs.test.util.PackageReadResolve;
import com.sun.sgs.test.util.PrivateReadResolve;
import com.sun.sgs.test.util.ProtectedReadResolve;
import com.sun.sgs.test.util.PublicReadResolve;
import com.sun.sgs.test.util.PackageWriteReplace;
import com.sun.sgs.test.util.ParameterizedNameRunner;
import com.sun.sgs.test.util.PrivateWriteReplace;
import com.sun.sgs.test.util.ProtectedWriteReplace;
import com.sun.sgs.test.util.PublicWriteReplace;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutPropertyName;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Test the DataServiceImpl class */
@SuppressWarnings("hiding")
@RunWith(ParameterizedNameRunner.class)
public class TestDataServiceImpl{

    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][] {{true}, {false}});
    }

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    protected static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** The component registry. */
    private static ComponentRegistry componentRegistry;

    /** An instance of the data service, to test. */
    static DataServiceImpl service;

    /**
     * Boolean to say we'll *always* need a new node for each run;  this
     * allows us to ensure that each test is independent.  We don't run in
     * this mode normally for speed (it's quite slow to always recreate
     * the data store) and because it's helpful to have random data in
     * the data store from test to test. <p>
     * However, it's useful to be able to sometimes ensure that the
     * tests are really independent. <p>
     * This boolean overrides cleanup. <p>
     * NOTE:  right now this is final, would be nice to be able to
     * pick it up from the system properties eventually.
     */
    static final boolean alwaysInitializeServerNode = false;

    private static final String APP_NAME = "TestDataServiceImpl";
    private static SgsTestNode serverNode = null;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static TransactionProxy txnProxy;

    /** 
     * Boolean to say if we should run with transaction that disable
     * the prepareAndCommit optimization (in which the last participant
     * prepared has prepareAndCommit called, rather than prepare, and
     * at a later point commit).
     */
    private static boolean disableTxnCommitOpt = false;
    
    /** Boolean to say if this is our first test run in this class. */
    static boolean firstRun = true;
    
    /** Boolean to say we need to shut down the serverNode. */
    boolean cleanup = false;

    /** A managed object. */
    private DummyManagedObject dummy;

    /** A kernel runnable to bind "dummy" to the ManagedObject dummy. */
    class InitialTestRunnable extends TestAbstractKernelRunnable {
        public void run() throws Exception {
            dummy = new DummyManagedObject();
            service.setBinding("dummy", dummy);
        }
    }
    
    /**
     * Create this test class.
     * @param disableTxnCommitOpt if {@true}, don't call prepareAndCommit
     *     on the last transaction participant to be commited.  This parameter
     *     is set by the parameterized test runner, using the {@link data}
     *     method.
     */
    public TestDataServiceImpl(boolean disableTxnCommitOpt) {
        if (disableTxnCommitOpt != TestDataServiceImpl.disableTxnCommitOpt) {
            // Start as if it's the first time, because we must force a
            // new serverNode to be created if the sense of the boolean
            // changes.
            cleanup = true;
            try {
                tearDown();
            } catch (Exception e) {
                System.err.println("Unexpected exception caught" + e);
            }
            firstRun = true;
            TestDataServiceImpl.disableTxnCommitOpt = disableTxnCommitOpt;
        }
    }
    /**
     * Prints the test case, and then sets up the test fixtures.
     */
    @Before
    public void setUp() throws Exception {
        // Insist on a clean data store directory if this is the first
        // run or if we're always using a clean server node.
        setUp(null, firstRun || alwaysInitializeServerNode);
    }

    /**
     * Create a new SgsTestNode for use by this test if {@link serverNode}
     * is {@code null}.  Note that most tests use the same data store (it
     * is not recreated for each run) unless {@link alwaysInitializeServerNode}
     * is set to true.
     * 
     * @param properties the properties used to initialize a 
     *                   {@link SgsTestNode}, or {@code null} to use the
     *                   default properties returned by {@link getProperties}
     * @param clean {@code true} if the data store should be freshly created
     *             
     */
    protected void setUp(Properties properties, boolean clean) 
        throws Exception 
    {
        firstRun = false;
        if (serverNode == null) { 
            if (properties == null) {
                properties = getProperties();
            }
            serverNode = new SgsTestNode(APP_NAME, null, properties, clean);

            txnProxy = serverNode.getProxy();
            componentRegistry = serverNode.getSystemRegistry();
            txnScheduler =
                    componentRegistry.getComponent(TransactionScheduler.class);
            taskOwner = txnProxy.getCurrentOwner();

            service = (DataServiceImpl) serverNode.getDataService();

            cleanup = alwaysInitializeServerNode;
        }
    }

    /**
     * Shut down the serverNode
     */
    @After 
    public void tearDown() throws Exception {
        if (cleanup && serverNode != null) {
            serverNode.shutdown(alwaysInitializeServerNode);
            serverNode = null;
        }
    }

    /**
     * Utility method for shutting down a server node and restarting it,
     * and arrange for the node to be restarted for the next test run.
     */
     private void serverNodeRestart(Properties props, boolean clean)
         throws Exception
     {
         serverNode.shutdown(false);
         serverNode = null;
         setUp(props, clean);
         cleanup = true;
     }

    /* -- Test constructor -- */

    @Test
    public void testConstructorNullArgs() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	try {
	    createDataServiceImpl(null, componentRegistry, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    createDataServiceImpl(props, null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    createDataServiceImpl(props, componentRegistry, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoAppName() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.remove(StandardProperties.APP_NAME);
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorBadDebugCheckInterval() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".debug.check.interval", "gorp");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /**
     * Tests that the {@code DataService} correctly infers the database
     * subdirectory when only the root directory is provided.
     *
     * @throws Exception if an unexpected exception occurs
     */
    @Test
    public void testConstructorNoDirectory() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        String rootDir = createDirectory();
        File dataDir = new File(rootDir, "dsdb");
        if (!dataDir.mkdir()) {
            throw new RuntimeException("Failed to create sub-dir: " + dataDir);
        }
	props.remove(DataStoreImplClassName + ".directory");
	props.setProperty(StandardProperties.APP_ROOT, rootDir);
	DataServiceImpl testSvc =
	    createDataServiceImpl(props, componentRegistry, txnProxy);
        testSvc.shutdown();
    }

    @Test
    public void testConstructorNoDirectoryNorRoot() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.remove(DataStoreImplClassName + ".directory");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorDataStoreClassNotFound() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class", "AnUnknownClass");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorDataStoreClassNotDataStore() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    Object.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorDataStoreClassNoConstructor() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreNoConstructor.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreNoConstructor extends DummyDataStore { }

    @Test
    public void testConstructorDataStoreClassAbstract() throws Exception {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreAbstract.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static abstract class DataStoreAbstract extends DummyDataStore {
	public DataStoreAbstract(Properties props) { }
    }

    @Test
    public void testConstructorDataStoreClassConstructorFails()
	throws Exception
    {
        Properties props =
            SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreConstructorFails.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected DataStoreConstructorException");
	} catch (DataStoreConstructorException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreConstructorFails extends DummyDataStore {
	public DataStoreConstructorFails(Properties props) {
	    throw new DataStoreConstructorException();
	}
    }

    private static class DataStoreConstructorException
	extends RuntimeException
    {
	private static final long serialVersionUID = 1;
    }

    /* -- Test getName -- */

    @Test
    public void testGetName() {
	assertNotNull(service.getName());
    }

    /* -- Test getBinding and getServiceBinding -- */

    @Test
    public void testGetBindingNullArgs() throws Exception {
	testGetBindingNullArgs(true);
    }
    @Test
    public void testGetServiceBindingNullArgs() throws Exception {
	testGetBindingNullArgs(false);
    }
    private void testGetBindingNullArgs(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    getBinding(app, service, null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test
    public void testGetBindingEmptyName() throws Exception {
	testGetBindingEmptyName(true);
    }
    @Test
    public void testGetServiceBindingEmptyName() throws Exception {
	testGetBindingEmptyName(false);
    }
    private void testGetBindingEmptyName(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "", dummy);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DummyManagedObject result =
                    (DummyManagedObject) getBinding(app, service, "");
                assertEquals(dummy, result);
        }}, taskOwner);
    }

    @Test
    public void testGetBindingNotFound() throws Exception {
	testGetBindingNotFound(true);
    }
    @Test
    public void testGetServiceBindingNotFound() throws Exception {
	testGetBindingNotFound(false);
    }
    private void testGetBindingNotFound(final boolean app) throws Exception {
	/* No binding */
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    getBinding(app, service, "testGetBindingNotFound");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
                /* New binding removed in this transaction */
                setBinding(app, service, "testGetBindingNotFound",
                           new DummyManagedObject());
                removeBinding(app, service, "testGetBindingNotFound");
                try {
                    getBinding(app, service, "testGetBindingNotFound");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

	/* New binding removed in last transaction */
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    getBinding(app, service, "testGetBindingNotFound");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
                /* Existing binding removed in this transaction */
                setBinding(app, service, "testGetBindingNotFound",
                           new DummyManagedObject());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                removeBinding(app, service, "testGetBindingNotFound");
                try {
                    getBinding(app, service, "testGetBindingNotFound");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

	/* Existing binding removed in last transaction. */
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    getBinding(app, service, "testGetBindingNotFound");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test
    public void testGetBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(true);
    }
    @Test
    public void testGetServiceBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(false);
    }
    private void testGetBindingObjectNotFound(final boolean app)
        throws Exception
    {
	/* New object removed in this transaction */
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "testGetBindingRemoved", dummy);
                service.removeObject(dummy);
                try {
                    getBinding(app, service, "testGetBindingRemoved");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

	/* New object removed in last transaction */
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    getBinding(app, service, "testGetBindingRemoved");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
                setBinding(app, service, "testGetBindingRemoved",
                           new DummyManagedObject());
        }}, taskOwner);
	/* Existing object removed in this transaction */
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.removeObject(
                    getBinding(app, service, "testGetBindingRemoved"));
                try {
                    getBinding(app, service, "testGetBindingRemoved");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

	/* Existing object removed in last transaction */
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    getBinding(app, service, "testGetBindingRemoved");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action getBinding = new Action() {
	void run() { service.getBinding("dummy"); }
    };
    private final Action getServiceBinding = new Action() {
	void setUp() { service.setServiceBinding("dummy", dummy); }
	void run() {
	    service.getServiceBinding("dummy");
	}
    };
    @Test
    public void testGetBindingAborting() throws Exception {
	testAborting(getBinding);
    }
    @Test 
    public void testGetServiceBindingAborting() throws Exception {
	testAborting(getServiceBinding);
    }
    @Test
    public void testGetBindingAborted() throws Exception {
	testAborted(getBinding);
    }
    @Test 
    public void testGetServiceBindingAborted() throws Exception {
	testAborted(getServiceBinding);
    }
    @Test 
    public void testGetBindingPreparing() throws Exception {
	testPreparing(getBinding);
    }
    @Test 
    public void testGetServiceBindingPreparing() throws Exception {
	testPreparing(getServiceBinding);
    }
    @Test 
    public void testGetBindingCommitting() throws Exception {
	testCommitting(getBinding);
    }
    @Test 
    public void testGetServiceBindingCommitting() throws Exception {
	testCommitting(getServiceBinding);
    }
    @Test 
    public void testGetBindingCommitted() throws Exception {
	testCommitted(getBinding);
    }
    @Test 
    public void testGetServiceBindingCommitted() throws Exception {
	testCommitted(getServiceBinding);
    }
    @Test 
    public void testGetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getBinding);
    }
    @Test 
    public void testGetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getServiceBinding);
    }
    @Test 
    public void testGetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getBinding);
    }
    @Test 
    public void testGetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getServiceBinding);
    }
    @Test 
    public void testGetBindingShutdown() throws Exception {
        testShutdown(getBinding);
    }
    @Test 
    public void testGetServiceBindingShutdown() throws Exception {
        testShutdown(getServiceBinding);
    }

    @Test 
    public void testGetBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(true);
    }
    @Test 
    public void testGetServiceBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(false);
    }
    private void testGetBindingDeserializationFails(final boolean app)
	throws Exception
    {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", new DeserializationFails());
        }}, taskOwner);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    getBinding(app, service, "dummy");
                    fail("Expected ObjectIOException");
                } catch (ObjectIOException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetBindingSuccess() throws Exception {
	testGetBindingSuccess(true);
    }
    @Test 
    public void testGetServiceBindingSuccess() throws Exception {
	testGetBindingSuccess(false);
    }
    private void testGetBindingSuccess(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", dummy);
                DummyManagedObject result =
                    (DummyManagedObject) getBinding(app, service, "dummy");
                assertEquals(dummy, result);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DummyManagedObject result =
                    (DummyManagedObject) getBinding(app, service, "dummy");
                assertEquals(dummy, result);
                getBinding(app, service, "dummy");
            }}, taskOwner);
    }

    @Test 
    public void testGetBindingsDifferent() throws Exception {
        final DummyManagedObject serviceDummy = new DummyManagedObject();
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.setServiceBinding("dummy", serviceDummy);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DummyManagedObject result =
                    (DummyManagedObject) service.getBinding("dummy");
                assertEquals(dummy, result);
                result =
                    (DummyManagedObject) service.getServiceBinding("dummy");
                assertEquals(serviceDummy, result);
        }}, taskOwner);
    }

    @Test 
    public void testGetBindingTimeout() throws Exception {
	testGetBindingTimeout(true);
    }
    @Test 
    public void testGetServiceBindingTimeout() throws Exception {
	testGetBindingTimeout(false);
    }
    private void testGetBindingTimeout(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", dummy);
        }}, taskOwner);

        Properties properties = getProperties();
        properties.setProperty("com.sun.sgs.txn.timeout", "100");
        serverNodeRestart(properties, false);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        Thread.sleep(200);
                        getBinding(app, service, "dummy");
                        fail("Expected TransactionTimeoutException");
                    } catch (TransactionTimeoutException e) {
                        System.err.println(e);
                        throw new TestAbortedTransactionException("abort");
                    }
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }
    }

    /* -- Test setBinding and setServiceBinding -- */

    @Test 
    public void testSetBindingNullArgs() throws Exception {
	testSetBindingNullArgs(true);
    }
    @Test 
    public void testSetServiceBindingNullArgs() throws Exception {
	testSetBindingNullArgs(false);
    }
    private void testSetBindingNullArgs(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    setBinding(app, service, null, dummy);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
                try {
                    setBinding(app, service, "dummy", null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testSetBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(true);
    }
    @Test 
    public void testSetServiceBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(false);
    }
    private void testSetBindingNotSerializable(final boolean app)
        throws Exception
    {
	final ManagedObject mo = new ManagedObject() { };
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    setBinding(app, service, "dummy", mo);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testSetBindingNotManagedObject() throws Exception {
	testSetBindingNotManagedObject(true);
    }
    @Test 
    public void testSetServiceBindingNotManagedObject() throws Exception {
	testSetBindingNotManagedObject(false);
    }
    private void testSetBindingNotManagedObject(final boolean app)
        throws Exception
    {
	final Object object = new Integer(2);
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    setBinding(app, service, "dummy", object);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action setBinding = new Action() {
	void run() { service.setBinding("dummy", dummy); }
    };
    private final Action setServiceBinding = new Action() {
	void run() { service.setServiceBinding("dummy", dummy); }
    };
    @Test 
    public void testSetBindingAborting() throws Exception {
	testAborting(setBinding);
    }
    @Test 
    public void testSetServiceBindingAborting() throws Exception {
	testAborting(setServiceBinding);
    }
    @Test 
    public void testSetBindingAborted() throws Exception {
	testAborted(setBinding);
    }
    @Test 
    public void testSetServiceBindingAborted() throws Exception {
	testAborted(setServiceBinding);
    }
    @Test 
    public void testSetBindingPreparing() throws Exception {
	testPreparing(setBinding);
    }
    @Test 
    public void testSetServiceBindingPreparing() throws Exception {
	testPreparing(setServiceBinding);
    }
    @Test 
    public void testSetBindingCommitting() throws Exception {
	testCommitting(setBinding);
    }
    @Test 
    public void testSetServiceBindingCommitting() throws Exception {
	testCommitting(setServiceBinding);
    }
    @Test 
    public void testSetBindingCommitted() throws Exception {
	testCommitted(setBinding);
    }
    @Test 
    public void testSetServiceBindingCommitted() throws Exception {
	testCommitted(setServiceBinding);
    }
    @Test 
    public void testSetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setBinding);
    }
    @Test 
    public void testSetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(setServiceBinding);
    }
    @Test 
    public void testSetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setBinding);
    }
    @Test 
    public void testSetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setServiceBinding);
    }
    @Test 
    public void testSetBindingShutdown() throws Exception {
	testShutdown(setBinding);
    }
    @Test 
    public void testSetServiceBindingShutdown() throws Exception {
	testShutdown(setServiceBinding);
    }

    @Test 
    public void testSetBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(true);
    }
    @Test 
    public void testSetServiceBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(false);
    }
    private void testSetBindingSerializationFails(final boolean app)
	throws Exception
    {
	try {
	    txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    setBinding(app, service, "dummy", new SerializationFails());
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    @Test 
    public void testSetBindingRemoved() throws Exception {
	testSetBindingRemoved(true);
    }
    @Test 
    public void testSetServiceBindingRemoved() throws Exception {
	testSetBindingRemoved(false);
    }
    private void testSetBindingRemoved(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.removeObject(dummy);
                try {
                    setBinding(app, service, "dummy", dummy);
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testSetBindingManagedObjectNoReference() throws Exception {
	testSetBindingManagedObjectNoReference(true);
    }
    @Test 
    public void testSetServiceBindingManagedObjectNoReference()
	throws Exception
    {
	testSetBindingManagedObjectNoReference(false);
    }
    private void testSetBindingManagedObjectNoReference(final boolean app)
	throws Exception
    {
	try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    dummy.setValue(new DummyManagedObject());
                    setBinding(app, service, "dummy", dummy);
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	}

	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    dummy.setValue(
                        new Object[] {
                            null, new Integer(3),
                            new DummyManagedObject[] {
                                null, new DummyManagedObject()
                            }
                        });
                    setBinding(app, service, "dummy", dummy);
             }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	}
    }

    @Test 
    public void testSetBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(true);
    }
    @Test 
    public void testSetServiceBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(false);
    }
    private void testSetBindingManagedObjectNotSerializableCommit(
            final boolean app)
	throws Exception
    {
	try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    dummy.setValue(Thread.currentThread());
                    setBinding(app, service, "dummy", dummy);
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	}

	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    dummy.setValue(
                        new Object[] {
                            null, new Integer(3),
                            new Thread[] {
                                null, Thread.currentThread()
                            }
                        });
                    setBinding(app, service, "dummy", dummy);
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	}
    }

    @Test 
    public void testSetBindingSuccess() throws Exception {
	testSetBindingSuccess(true);
    }
    @Test 
    public void testSetServiceBindingSuccess() throws Exception {
	testSetBindingSuccess(false);
    }
    private void testSetBindingSuccess(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", dummy);
        }}, taskOwner);

        final DummyManagedObject dummy2 = new DummyManagedObject();
        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                assertEquals(dummy, getBinding(app, service, "dummy"));
                setBinding(app, service, "dummy", dummy2);
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                assertEquals(dummy, getBinding(app, service, "dummy"));
                setBinding(app, service, "dummy", dummy2);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                assertEquals(dummy2, getBinding(app, service, "dummy"));
        }}, taskOwner);
    }

    /* -- Test removeBinding and removeServiceBinding -- */

    @Test 
    public void testRemoveBindingNullName() throws Exception {
	testRemoveBindingNullName(true);
    }
    @Test 
    public void testRemoveServiceBindingNullName() throws Exception {
	testRemoveBindingNullName(false);
    }
    private void testRemoveBindingNullName(final boolean app)
            throws Exception
    {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    removeBinding(app, service, null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveBindingEmptyName() throws Exception {
        testRemoveBindingEmptyName(true);
    }
    @Test
    public void testRemoveServiceBindingEmptyName() throws Exception {
        testRemoveBindingEmptyName(false);
    }
    private void testRemoveBindingEmptyName(final boolean app)
        throws Exception
    {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "", dummy);
                removeBinding(app, service, "");
                try {
                    removeBinding(app, service, "");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action removeBinding = new Action() {
	void run() { service.removeBinding("dummy"); }
    };
    private final Action removeServiceBinding = new Action() {
        void setUp() { service.setServiceBinding("dummy", dummy); }
	void run() { service.removeServiceBinding("dummy"); }
    };
    @Test 
    public void testRemoveBindingAborting() throws Exception {
	testAborting(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingAborting() throws Exception {
	testAborting(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingAborted() throws Exception {
	testAborted(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingAborted() throws Exception {
	testAborted(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingPreparing() throws Exception {
	testPreparing(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingPreparing() throws Exception {
	testPreparing(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingCommitting() throws Exception {
	testCommitting(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingCommitting() throws Exception {
	testCommitting(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingCommitted() throws Exception {
	testCommitted(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingCommitted() throws Exception {
	testCommitted(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeServiceBinding);
    }
    @Test 
    public void testRemoveBindingShutdown() throws Exception {
	testShutdown(removeBinding);
    }
    @Test 
    public void testRemoveServiceBindingShutdown() throws Exception {
	testShutdown(removeServiceBinding);
    }

    @Test 
    public void testRemoveBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(true);
    }
    @Test 
    public void testRemoveServiceBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(false);
    }
    private void testRemoveBindingRemovedObject(final boolean app)
        throws Exception
    {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", dummy);
                service.removeObject(dummy);
                removeBinding(app, service, "dummy");
                try {
                    getBinding(app, service, "dummy");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
                dummy = new DummyManagedObject();
                setBinding(app, service, "dummy", dummy);
                service.removeObject(dummy);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                removeBinding(app, service, "dummy");
                try {
                    getBinding(app, service, "dummy");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveBindingDeserializationFails() throws Exception {
	testRemoveBindingDeserializationFails(true);
    }
    @Test 
    public void testRemoveServiceBindingDeserializationFails()
	throws Exception
    {
	testRemoveBindingDeserializationFails(false);
    }
    private void testRemoveBindingDeserializationFails(final boolean app)
	throws Exception
    {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", new DeserializationFails());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                removeBinding(app, service, "dummy");
                try {
                    getBinding(app, service, "dummy");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveBindingSuccess() throws Exception {
	testRemoveBindingSuccess(true);
    }
    @Test 
    public void testRemoveServiceBindingSuccess() throws Exception {
	testRemoveBindingSuccess(false);
    }
    private void testRemoveBindingSuccess(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                setBinding(app, service, "dummy", dummy);
        }}, taskOwner);
        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    removeBinding(app, service, "dummy");
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                removeBinding(app, service, "dummy");
                try {
                    removeBinding(app, service, "dummy");
                    fail("Expected NameNotBoundException");
                } catch (NameNotBoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveBindingsDifferent() throws Exception {
        final DummyManagedObject serviceDummy = new DummyManagedObject();
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.setServiceBinding("dummy", serviceDummy);
        }}, taskOwner);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.removeBinding("dummy");
                    DummyManagedObject serviceResult =
                        (DummyManagedObject) service.getServiceBinding("dummy");
                    assertEquals(serviceDummy, serviceResult);
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.removeServiceBinding("dummy");
                DummyManagedObject result =
                    (DummyManagedObject) service.getBinding("dummy");
                assertEquals(dummy, result);
        }}, taskOwner);
    }

    /* -- Test nextBoundName and nextServiceBoundName -- */

    @Test 
    public void testNextBoundNameNotFound() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                for (String name = null;
                     (name = service.nextBoundName(name)) != null; )
                {
                    service.removeBinding(name);
                }
                assertNull(service.nextBoundName(null));
                assertNull(service.nextBoundName(""));
                assertNull(service.nextBoundName("whatever"));
        }}, taskOwner);
    }

    @Test 
    public void testNextBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(true);
    }
    @Test 
    public void testNextServiceBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(false);
    }
    private void testNextBoundNameEmpty(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    removeBinding(app, service, "");
                } catch (NameNotBoundException e) {
                }
                String forNull = nextBoundName(app, service, null);
                assertEquals(forNull, nextBoundName(app, service, ""));
                setBinding(app, service, "", dummy);
                assertEquals("", nextBoundName(app, service, null));
                assertEquals(forNull, nextBoundName(app, service, ""));
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action nextBoundName = new Action() {
	void run() { service.nextBoundName(null); }
    };
    private final Action nextServiceBoundName = new Action() {
	void run() { service.nextServiceBoundName(null); }
    };
    @Test 
    public void testNextBoundNameAborting() throws Exception {
	testAborting(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameAborting() throws Exception {
	testAborting(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameAborted() throws Exception {
	testAborted(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameAborted() throws Exception {
	testAborted(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNamePreparing() throws Exception {
	testPreparing(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNamePreparing() throws Exception {
	testPreparing(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameCommitting() throws Exception {
	testCommitting(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameCommitting() throws Exception {
	testCommitting(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameCommitted() throws Exception {
	testCommitted(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameCommitted() throws Exception {
	testCommitted(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextServiceBoundName);
    }
    @Test 
    public void testNextBoundNameShutdown() throws Exception {
	testShutdown(nextBoundName);
    }
    @Test 
    public void testNextServiceBoundNameShutdown() throws Exception {
	testShutdown(nextServiceBoundName);
    }

    @Test 
    public void testNextBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(true);
    }
    @Test 
    public void testNextServiceBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(false);
    }
    private void testNextBoundNameSuccess(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                assertNull(nextBoundName(app, service, "zzz-"));
                setBinding(app, service, "zzz-1", dummy);
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                setBinding(app, service, "zzz-2", dummy);
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                assertNull(nextBoundName(app, service, "zzz-2"));
        }}, taskOwner);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                removeBinding(app, service, "zzz-1");
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                removeBinding(app, service, "zzz-2");
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                removeBinding(app, service, "zzz-1");
                assertNull(nextBoundName(app, service, "zzz-"));
                assertNull(nextBoundName(app, service, "zzz-"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-1"));
                assertNull(nextBoundName(app, service, "zzz-2"));
                assertNull(nextBoundName(app, service, "zzz-2"));
        }}, taskOwner);
    }

    @Test 
    public void testNextBoundNameModify() throws Exception {
	testNextBoundNameModify(true);
    }
    @Test 
    public void testNextServiceBoundNameModify() throws Exception {
	testNextBoundNameModify(false);
    }
    private void testNextBoundNameModify(final boolean app) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                for (String name = "zzz-1";
                     (name = service.nextBoundName(name)) != null; )
                {
                    service.removeBinding(name);
                }
                setBinding(app, service, "zzz-1", dummy);
                assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
                setBinding(app, service, "zzz-2", dummy);
                assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
                removeBinding(app, service, "zzz-2");
                setBinding(app, service, "zzz-3", dummy);
                setBinding(app, service, "zzz-4", dummy);
                assertEquals("zzz-3", nextBoundName(app, service, "zzz-2"));
                removeBinding(app, service, "zzz-4");
                assertNull(nextBoundName(app, service, "zzz-3"));
        }}, taskOwner);
    }

    @Test 
    public void testNextBoundNameDifferent() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                for (String name = null;
                     (name = service.nextBoundName(name)) != null; )
                {
                    service.removeBinding(name);
                }
                for (String name = null;
                     (name = service.nextServiceBoundName(name)) != null; )
                {
                    if (!name.startsWith("com.sun.sgs")) {
                        service.removeServiceBinding(name);
                    }
                }
                String nextService = service.nextServiceBoundName(null);
                String lastService = nextService;
                String name;
                while (
                    (name = service.nextServiceBoundName(lastService)) != null)
                {
                    lastService = name;
                }
                service.setBinding("a-app", dummy);
                service.setServiceBinding("a-service", dummy);
                assertEquals("a-app", service.nextBoundName(null));
                assertEquals("a-app", service.nextBoundName(""));
                assertEquals("a-app", service.nextBoundName("a-"));
                assertEquals(null, service.nextBoundName("a-app"));
                assertEquals("a-service", service.nextServiceBoundName(null));
                assertEquals("a-service", service.nextServiceBoundName(""));
                assertEquals("a-service", service.nextServiceBoundName("a-"));
                assertEquals(nextService,
                             service.nextServiceBoundName("a-service"));
                assertEquals(null, service.nextServiceBoundName(lastService));
        }}, taskOwner);
    }

    /* -- Test removeObject -- */

    @Test 
    public void testRemoveObjectNull() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    service.removeObject(null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectNotSerializable() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                ManagedObject mo = new ManagedObject() { };
                try {
                    service.removeObject(mo);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectNotManagedObject() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                Object object = "Hello";
                try {
                    service.removeObject(object);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action removeObject = new Action() {
	void run() { service.removeObject(dummy); }
    };
    @Test 
    public void testRemoveObjectAborting() throws Exception {
	testAborting(removeObject);
    }
    @Test 
    public void testRemoveObjectAborted() throws Exception {
	testAborted(removeObject);
    }
    @Test 
    public void testRemoveObjectPreparing() throws Exception {
	testPreparing(removeObject);
    }
    @Test 
    public void testRemoveObjectCommitting() throws Exception {
	testCommitting(removeObject);
    }
    @Test 
    public void testRemoveObjectCommitted() throws Exception {
	testCommitted(removeObject);
    }
    @Test 
    public void testRemoveObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeObject);
    }
    @Test 
    public void testRemoveObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeObject);
    }
    @Test 
    public void testRemoveObjectShutdown() throws Exception {
	testShutdown(removeObject);
    }

    @Test 
    public void testRemoveObjectSuccess() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.removeObject(dummy);
                try {
                    service.getBinding("dummy");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    service.getBinding("dummy");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectRemoved() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.removeObject(dummy);
                try {
                    service.removeObject(dummy);
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectPreviousTxn() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.removeObject(dummy);
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectRemoval() throws Exception {
        class TestTask extends InitialTestRunnable {
            int count;
            public void run() throws Exception {
                super.run();
                count = getObjectCount();
                ObjectWithRemoval removal = new ObjectWithRemoval();
                service.removeObject(removal);
                assertFalse("Shouldn't call removingObject for transient objects",
                            removal.removingCalled);
                service.setBinding("removal", removal);
            }
        }
        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                ObjectWithRemoval removal =
                        (ObjectWithRemoval) service.getBinding("removal");
                service.removeObject(removal);
                assertTrue(removal.removingCalled);
                assertEquals(task.count, getObjectCount());
                try {
                    service.removeObject(removal);
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    service.getBinding("removal");
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                }
        }}, taskOwner);
    }

    @Test 
    public void testRemoveObjectRemovalRecurse() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                ObjectWithRemoval x = new ObjectWithRemovalRecurse();
                ObjectWithRemoval y = new ObjectWithRemovalRecurse();
                ObjectWithRemoval z = new ObjectWithRemovalRecurse();
                x.setNext(y);
                y.setNext(z);
                z.setNext(x);
                service.setBinding("x", x);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                ObjectWithRemoval x =
                        (ObjectWithRemoval) service.getBinding("x");
                try {
                    service.removeObject(x);
                    fail("Expected IllegalStateException");
                } catch (IllegalStateException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /**
     * A managed object whose removingObject method calls removeObject on its
     * next field.
     */
    private static class ObjectWithRemovalRecurse extends ObjectWithRemoval {
	private static final long serialVersionUID = 1;
	ObjectWithRemovalRecurse() {
	    super(1);
	}
	public void removingObject() {
	    super.removingObject();
	    DummyManagedObject next = getNext();
	    if (next != null) {
		service.removeObject(next);
	    }
	}
    }

    @Test 
    public void testRemoveObjectRemovalThrows() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                ObjectWithRemoval x = new ObjectWithRemovalThrows();
                service.setBinding("x", x);
                try {
                    service.removeObject(x);
                    fail("Expected ObjectWithRemovalThrows.E");
                } catch (ObjectWithRemovalThrows.E e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /** A managed object whose removingObject method throws an exception. */
    private static class ObjectWithRemovalThrows extends ObjectWithRemoval {
	private static final long serialVersionUID = 1;
	ObjectWithRemovalThrows() {
	    super(1);
	}
	public void removingObject() {
	    throw new E();
	}
	static class E extends RuntimeException {
	    private static final long serialVersionUID = 1;
	}
    }

    /* -- Test markForUpdate -- */

    @Test 
    public void testMarkForUpdateNull() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                try {
                    service.markForUpdate(null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testMarkForUpdateNotSerializable() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                ManagedObject mo = new ManagedObject() { };
                try {
                    service.markForUpdate(mo);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testMarkForUpdateNotManagedObject() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                Object object = new Properties();
                try {
                    service.markForUpdate(object);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testMarkForUpdateRemoved() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.removeObject(dummy);
                try {
                    service.markForUpdate(dummy);
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action markForUpdate = new Action() {
	void run() { service.markForUpdate(dummy); }
    };
    @Test 
    public void testMarkForUpdateAborting() throws Exception {
	testAborting(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateAborted() throws Exception {
	testAborted(markForUpdate);
    }
    @Test 
    public void testMarkForUpdatePreparing() throws Exception {
	testPreparing(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateCommitting() throws Exception {
	testCommitting(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateCommitted() throws Exception {
	testCommitted(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(markForUpdate);
    }
    @Test 
    public void testMarkForUpdateShutdown() throws Exception {
	testShutdown(markForUpdate);
    }

    @Test 
    public void testMarkForUpdateSuccess() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.markForUpdate(dummy);
                service.setBinding("dummy", dummy);
                dummy.setValue("a");
        }}, taskOwner);

	service.setDetectModifications(false);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                service.markForUpdate(dummy);
                dummy.value = "b";
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                assertEquals("b", dummy.value);
        }}, taskOwner);
    }

    @Test 
    public void testMarkForUpdateLocking() throws Exception {
	/*
	 * Create a fresh data service -- BDB Java edition does not permit
	 * changing the lock timeout for an existing database.
	 * -tjb@sun.com (07/22/2008)
	 */
	String dir = getDbDirectory() + "testMarkForUpdateLocking";
        Properties properties = getProperties();
        properties.setProperty(DataStoreImplClassName + ".directory", dir);
	properties.setProperty(getLockTimeoutPropertyName(properties), "500");
        properties.setProperty("com.sun.sgs.txn.timeout", "1000");
        serverNodeRestart(properties, true);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = new DummyManagedObject();
                dummy.setValue("a");
                service.setBinding("dummy", dummy);
        }}, taskOwner);

        final Semaphore mainFlag = new Semaphore(0);
        final Semaphore threadFlag = new Semaphore(0);
        txnScheduler.scheduleTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                assertEquals("a", dummy.value);
                assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
                mainFlag.release();
                assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
        }}, taskOwner);

        txnScheduler.scheduleTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                try {
                    DummyManagedObject dummy2 =
                        (DummyManagedObject) service.getBinding("dummy");
                    assertEquals("a", dummy2.value);
                    threadFlag.release();
                    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
                    service.markForUpdate(dummy2);
                    threadFlag.release();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e);
                }
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new RuntimeException("abort"));
        }}, taskOwner);

        assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
    }

    /* -- Test createReference -- */

    @Test 
    public void testCreateReferenceNull() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    service.createReference(null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceNotSerializable() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                ManagedObject mo = new ManagedObject() { };
                try {
                    service.createReference(mo);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceNotManagedObject() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                Object object = Boolean.TRUE;
                try {
                    service.createReference(object);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action createReference = new Action() {
	void run() { service.createReference(dummy); }
    };
    @Test 
    public void testCreateReferenceAborting() throws Exception {
	testAborting(createReference);
    }
    @Test 
    public void testCreateReferenceAborted() throws Exception {
	testAborted(createReference);
    }
    @Test 
    public void testCreateReferencePreparing() throws Exception {
	testPreparing(createReference);
    }
    @Test 
    public void testCreateReferenceCommitting() throws Exception {
	testCommitting(createReference);
    }
    @Test 
    public void testCreateReferenceCommitted() throws Exception {
	testCommitted(createReference);
    }
    @Test 
    public void testCreateReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(createReference);
    }
    @Test 
    public void testCreateReferenceShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReference);
    }
    @Test 
    public void testCreateReferenceShutdown() throws Exception {
	testShutdown(createReference);
    }

    @Test 
    public void testCreateReferenceNew() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                ManagedReference<DummyManagedObject> ref =
                    service.createReference(dummy);
                assertEquals(dummy, ref.get());
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceExisting() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DummyManagedObject dummy =
                    (DummyManagedObject) service.getBinding("dummy");
                ManagedReference<DummyManagedObject> ref =
                    service.createReference(dummy);
                assertEquals(dummy, ref.get());
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceSerializationFails() throws Exception {
	try {
	    txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    dummy.setNext(new SerializationFails());
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    @Test 
    public void testCreateReferenceRemoved() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.createReference(dummy);
                service.removeObject(dummy);
                try {
                    service.createReference(dummy);
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferencePreviousTxn() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                assertEquals(dummy, service.createReference(dummy).get());
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceTwoObjects() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DummyManagedObject x = new DummyManagedObject();
                DummyManagedObject y = new DummyManagedObject();
                assertFalse(
                    service.createReference(x).equals(
                        service.createReference(y)));
        }}, taskOwner);
    }

    /* -- Test createReferenceForId -- */

    @Test 
    public void testCreateReferenceForIdNullId() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    service.createReferenceForId(null);
                    fail("Expected NullPointerException");
                } catch (NullPointerException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceForIdTooSmallId() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                BigInteger id = new BigInteger("-1");
                try {
                    service.createReferenceForId(id);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
                service.createReferenceForId(BigInteger.ZERO);
        }}, taskOwner);
    }

    @Test 
    public void testCreateReferenceForIdTooBigId() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                BigInteger maxLong =
                        new BigInteger(String.valueOf(Long.MAX_VALUE));
                BigInteger id = maxLong.add(BigInteger.ONE);
                try {
                    service.createReferenceForId(id);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
                service.createReferenceForId(maxLong);
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action createReferenceForId = new Action() {
	private BigInteger id;
	void setUp() { id = service.createReference(dummy).getId(); }
	void run() { service.createReferenceForId(id); }
    };
    @Test 
    public void testCreateReferenceForIdAborting() throws Exception {
	testAborting(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdAborted() throws Exception {
	testAborted(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdPreparing() throws Exception {
	testPreparing(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdCommitting() throws Exception {
	testCommitting(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdCommitted() throws Exception {
	testCommitted(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReferenceForId);
    }
    @Test 
    public void testCreateReferenceForIdShutdown() throws Exception {
	testShutdown(createReferenceForId);
    }

    @Test 
    public void testCreateReferenceForIdSuccess() throws Exception {
        class TestTask extends InitialTestRunnable {
            BigInteger id;
            public void run() throws Exception {
                super.run();
                id = service.createReference(dummy).getId();
                ManagedReference<DummyManagedObject> ref =
                    uncheckedCast(service.createReferenceForId(id));
                assertSame(dummy, ref.get());
            }
        }

        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                ManagedReference<DummyManagedObject> ref =
                    uncheckedCast(service.createReferenceForId(task.id));
                dummy = ref.get();
                assertSame(dummy, service.getBinding("dummy"));
                service.removeObject(dummy);
                try {
                    ref.get();
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
            ManagedReference<DummyManagedObject> ref =
                uncheckedCast(service.createReferenceForId(task.id));
            try {
                ref.get();
                fail("Expected ObjectNotFoundException");
            } catch (ObjectNotFoundException e) {
                System.err.println(e);
            }
        }}, taskOwner);
    }

    /* -- Test getNextId -- */

    @Test 
    public void testNextObjectIdIllegalIds() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                BigInteger id =
                    BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
                try {
                    service.nextObjectId(id);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
                id = BigInteger.valueOf(-1);
                try {
                    service.nextObjectId(id);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
                id = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
                try {
                    service.nextObjectId(id);
                    fail("Expected IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testNextObjectIdBoundaryIds() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                BigInteger first = service.nextObjectId(null);
                assertEquals(first, service.nextObjectId(null));
                assertEquals(first, service.nextObjectId(BigInteger.ZERO));
                BigInteger last = null;
                while (true) {
                    BigInteger id = service.nextObjectId(last);
                    if (id == null) {
                        break;
                    }
                    last = id;
                }
                assertEquals(null, service.nextObjectId(last));
                assertEquals(
                    null,
                    service.nextObjectId(BigInteger.valueOf(Long.MAX_VALUE)));
        }}, taskOwner);
    }

    @Test 
    public void testNextObjectIdRemoved() throws Exception {
        class TestTask extends InitialTestRunnable {
            BigInteger dummyId;
            BigInteger dummy2Id;
            public void run() throws Exception {
                super.run();
                DummyManagedObject dummy2 = new DummyManagedObject();
                dummyId = service.createReference(dummy).getId();
                dummy2Id = service.createReference(dummy2).getId();
                /* Make sure dummyId is smaller than dummy2Id */
                if (dummyId.compareTo(dummy2Id) > 0) {
                    BigInteger temp = dummyId;
                    dummyId = dummy2Id;
                    dummy2Id = temp;
                    DummyManagedObject dummyTemp = dummy;
                    dummy = dummy2;
                    dummy2 = dummyTemp;
                    service.setBinding("dummy", dummy);
                }
                BigInteger id = dummyId;
                while (true) {
                    id = service.nextObjectId(id);
                    assertNotNull("Didn't find dummy2Id after dummyId", id);
                    if (id.equals(dummy2Id)) {
                        break;
                    }
                }
            }
        }

        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                service.removeObject(dummy);
                BigInteger id = null;
                while (true) {
                    id = service.nextObjectId(id);
                    if (id == null) {
                        break;
                    }
                    assertFalse("Shouldn't find ID removed in this txn",
                                task.dummyId.equals(id));
                }
                id = task.dummyId;
                while (true) {
                    id = service.nextObjectId(id);
                    assertNotNull("Didn't find dummy2Id after removed dummyId", id);
                    if (id.equals(task.dummy2Id)) {
                        break;
                    }
                }
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                BigInteger id = null;
                while (true) {
                    id = service.nextObjectId(id);
                    if (id == null) {
                        break;
                    }
                    assertFalse("Shouldn't find ID removed in last txn",
                                task.dummyId.equals(id));
                }

                id = task.dummyId;
                while (true) {
                    id = service.nextObjectId(id);
                    assertNotNull("Didn't find dummy2Id after removed dummyId", id);
                    if (id.equals(task.dummy2Id)) {
                        break;
                    }
                }
        }}, taskOwner);
    }

    /**
     * Test that producing a reference to an object removed in another
     * transaction doesn't cause that object's ID to be returned.
     */
    @Test 
    public void testNextObjectIdRemovedIgnoreRef() throws Exception {
        class TestTask extends InitialTestRunnable {
            BigInteger dummyId;
            BigInteger dummy2Id;
            public void run() throws Exception {
                super.run();
                DummyManagedObject dummy2 = new DummyManagedObject();
                dummyId = service.createReference(dummy).getId();
                dummy2Id = service.createReference(dummy2).getId();
                /* Make sure dummyId is smaller than dummy2Id */
                if (dummyId.compareTo(dummy2Id) > 0) {
                    DummyManagedObject obj = dummy;
                    dummy = dummy2;
                    dummy2 = obj;
                    service.setBinding("dummy", dummy);
                    BigInteger id = dummyId;
                    dummyId = dummy2Id;
                    dummy2Id = id;
                }
                dummy.setNext(dummy2);
            }
        }

        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                service.removeObject(dummy.getNext());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                BigInteger id = task.dummyId;
                while (true) {
                    id = service.nextObjectId(id);
                    if (id == null) {
                        break;
                    }
                    assertFalse("Shouldn't get removed dummy2 ID",
                                id.equals(task.dummy2Id));
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action nextObjectId = new Action() {
	void run() { service.nextObjectId(null); }
    };
    @Test 
    public void testNextObjectIdAborting() throws Exception {
	testAborting(nextObjectId);
    }
    @Test 
    public void testNextObjectIdAborted() throws Exception {
	testAborted(nextObjectId);
    }
    @Test 
    public void testNextObjectIdPreparing() throws Exception {
	testPreparing(nextObjectId);
    }
    @Test 
    public void testNextObjectIdCommitting() throws Exception {
	testCommitting(nextObjectId);
    }
    @Test 
    public void testNextObjectIdCommitted() throws Exception {
	testCommitted(nextObjectId);
    }
    @Test 
    public void testNextObjectIdShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextObjectId);
    }
    @Test 
    public void testNextObjectIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextObjectId);
    }
    @Test 
    public void testNextObjectIdShutdown() throws Exception {
	testShutdown(nextObjectId);
    }

    /* -- Test ManagedReference.get -- */

    @Test 
    public void testGetReferenceNotFound() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DummyManagedObject());
                service.removeObject(dummy.getNext());
                try {
                    dummy.getNext();
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

         txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                try {
                    dummy.getNext();
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action getReference = new Action() {
	private ManagedReference<?> ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.get(); }
    };
    /* Can't get a reference when the service is uninitialized */
    @Test 
    public void testGetReferenceAborting() throws Exception {
	testAborting(getReference);
    }
    @Test 
    public void testGetReferenceAborted() throws Exception {
	testAborted(getReference);
    }
    @Test 
    public void testGetReferencePreparing() throws Exception {
	testPreparing(getReference);
    }
    @Test 
    public void testGetReferenceCommitting() throws Exception {
	testCommitting(getReference);
    }
    @Test 
    public void testGetReferenceCommitted() throws Exception {
	testCommitted(getReference);
    }
    @Test 
    public void testGetReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getReference);
    }
    /* Can't get a reference as the first operation in a new transaction */
    @Test 
    public void testGetReferenceShutdown() throws Exception {
	testShutdown(getReference);
    }

    @Test 
    public void testGetReferenceDeserializationFails() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DeserializationFails());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                try {
                    dummy.getNext();
                    fail("Expected ObjectIOException");
                } catch (ObjectIOException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceOldTxn() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DummyManagedObject());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    dummy.getNext();
                    fail("Expected TransactionNotActiveException");
                } catch (TransactionNotActiveException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceTimeout() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DummyManagedObject());
        }}, taskOwner);

        Properties properties = getProperties();
        properties.setProperty("com.sun.sgs.txn.timeout", "100");
        serverNodeRestart(properties, false);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        dummy =
                            (DummyManagedObject) service.getBinding("dummy");
                        Thread.sleep(200);
                        dummy.getNext();
                        fail("Expected TransactionTimeoutException");
                    } catch (TransactionTimeoutException e) {
                        throw new TestAbortedTransactionException("abort", e);
                    }
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }
    }

    /**
     * Test that deserializing an object which contains a managed reference
     * throws TransactionTimeoutException if the deserialization of the
     * reference occurs after the transaction timeout.
     */
    @Test 
    public void testGetReferenceTimeoutReadResolve() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                DeserializationDelayed dummy = new DeserializationDelayed();
                dummy.setNext(new DummyManagedObject());
                service.setBinding("dummy", dummy);
        }}, taskOwner);

        Properties properties = getProperties();
        properties.setProperty("com.sun.sgs.txn.timeout", "100");
        serverNodeRestart(properties, false);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    try {
                        DeserializationDelayed.delay = 200;
                        DeserializationDelayed dummy =
                            (DeserializationDelayed)
                                service.getBinding("dummy");
                        System.err.println(dummy);
                        fail("Expected TransactionTimeoutException");
                    } catch (TransactionTimeoutException e) {
                        throw new TestAbortedTransactionException("abort", e);
                    }
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        } finally {
            DeserializationDelayed.delay = 0;
        }
    }

    /**
     * Test detecting managed objects with readResolve and writeReplace
     * methods.
     */
    @Test 
    public void testManagedObjectReadResolveWriteReplace() throws Exception {
	objectIOExceptionOnCommit(new MOPublicReadResolve());
	objectIOExceptionOnCommit(new MOPublicWriteReplace());
	objectIOExceptionOnCommit(new MOPublicReadResolveHere());
	objectIOExceptionOnCommit(new MOPublicWriteReplaceHere());
	objectIOExceptionOnCommit(new MOProtectedReadResolve());
	objectIOExceptionOnCommit(new MOProtectedWriteReplace());
	objectIOExceptionOnCommit(new MOProtectedReadResolveHere());
	objectIOExceptionOnCommit(new MOProtectedWriteReplaceHere());
	okOnCommit(new MOPackageReadResolve());
	okOnCommit(new MOPackageWriteReplace());
	objectIOExceptionOnCommit(new MOPackageReadResolveHere());
	objectIOExceptionOnCommit(new MOPackageWriteReplaceHere());
	okOnCommit(new MOPrivateReadResolve());
	okOnCommit(new MOPrivateWriteReplace());
	objectIOExceptionOnCommit(new MOPrivateReadResolveHere());
	objectIOExceptionOnCommit(new MOPrivateWriteReplaceHere());
	okOnCommit(new MOStaticReadResolve());
	okOnCommit(new MOStaticWriteReplace());
	okOnCommit(new MOReadResolveWrongReturn());
	okOnCommit(new MOWriteReplaceWrongReturn());
	objectIOExceptionOnCommit(new MOLocalPackageReadResolve());
	objectIOExceptionOnCommit(new MOLocalPackageWriteReplace());
	okOnCommit(new MOLocalPrivateReadResolve());
	okOnCommit(new MOLocalPrivateWriteReplace());
	okOnCommit(new AbstractReadResolveField());
	okOnCommit(new AbstractWriteReplaceField());
    }

    static class MOPublicReadResolve extends PublicReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPublicWriteReplace extends PublicWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPublicReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public Object readResolve() { return this; }
    }

    static class MOPublicWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public Object writeReplace() { return this; }
    }

    static class MOProtectedReadResolve extends ProtectedReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOProtectedWriteReplace extends ProtectedWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOProtectedReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	protected Object readResolve() { return this; }
    }

    static class MOProtectedWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	protected Object writeReplace() { return this; }
    }

    static class MOPackageReadResolve extends PackageReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPackageWriteReplace extends PackageWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPackageReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Object readResolve() { return this; }
    }

    static class MOPackageWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Object writeReplace() { return this; }
    }

    static class MOPrivateReadResolve extends PrivateReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPrivateWriteReplace extends PrivateWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPrivateReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	private Object readResolve() { return this; }
    }

    static class MOPrivateWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	private Object writeReplace() { return this; }
    }

    static class MOStaticReadResolve implements ManagedObject, Serializable {
	private static final long serialVersionUID = 0;
	public static Object readResolve() { return null; }
    }

    static class MOStaticWriteReplace implements ManagedObject, Serializable {
	private static final long serialVersionUID = 0;
	public static Object writeReplace() { return null; }
    }

    static class MOReadResolveWrongReturn
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public static String readResolve() { return "hi"; }
    }

    static class MOWriteReplaceWrongReturn
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public static String writeReplace() { return "hi"; }
    }

    static class MOLocalPackageReadResolve extends LocalPackageReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPackageReadResolve {
	Object readResolve() { return this; }
    }

    static class MOLocalPackageWriteReplace extends LocalPackageWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPackageWriteReplace {
	Object writeReplace() { return this; }
    }

    static class MOLocalPrivateReadResolve extends LocalPrivateReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPrivateReadResolve {
	private Object readResolve() { return this; }
    }

    static class MOLocalPrivateWriteReplace extends LocalPrivateWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPrivateWriteReplace {
	private Object writeReplace() { return this; }
    }

    static class AbstractReadResolveField
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Class<?> cl = AbstractReadResolve.class;
    }

    abstract static class AbstractReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	abstract Object readResolve();
    }

    static class AbstractWriteReplaceField
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Class<?> cl = AbstractWriteReplace.class;
    }

    abstract static class AbstractWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	abstract Object writeReplace();
    }

    /* -- Test ManagedReference.getForUpdate -- */

    @Test 
    public void testGetReferenceUpdateNotFound() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DummyManagedObject());
                service.removeObject(dummy.getNext());
                try {
                    dummy.getNextForUpdate();
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                try {
                    dummy.getNextForUpdate();
                    fail("Expected ObjectNotFoundException");
                } catch (ObjectNotFoundException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceForUpdateMaybeModified() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                service.createReference(dummy).getForUpdate();
                dummy.value = "B";
                }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                assertEquals("B", dummy.value);
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceUpdateSuccess() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                DummyManagedObject dummy2 = new DummyManagedObject();
                dummy2.setValue("A");
                dummy.setNext(dummy2);
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                DummyManagedObject dummy2 = dummy.getNextForUpdate();
                dummy2.value = "B";
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                DummyManagedObject dummy2 = dummy.getNext();
                assertEquals("B", dummy2.value);
        }}, taskOwner);
    }

    /* -- Unusual states -- */
    private final Action getReferenceUpdate = new Action() {
	private ManagedReference<?> ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.getForUpdate(); }
    };
    /* Can't get a referenceUpdate when the service is uninitialized */
    @Test 
    public void testGetReferenceUpdateAborting() throws Exception {
	testAborting(getReferenceUpdate);
    }
    @Test 
    public void testGetReferenceUpdateAborted() throws Exception {
	testAborted(getReferenceUpdate);
    }
    @Test 
    public void testGetReferenceUpdatePreparing() throws Exception {
	testPreparing(getReferenceUpdate);
    }
    @Test 
    public void testGetReferenceUpdateCommitting() throws Exception {
	testCommitting(getReferenceUpdate);
    }
    @Test 
    public void testGetReferenceUpdateCommitted() throws Exception {
	testCommitted(getReferenceUpdate);
    }
    @Test 
    public void testGetReferenceUpdateShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getReferenceUpdate);
    }
    /* Can't get a reference as the first operation in a new transaction */
    @Test 
    public void testGetReferenceUpdateShutdown() throws Exception {
	testShutdown(getReferenceUpdate);
    }

    @Test 
    public void testGetReferenceUpdateDeserializationFails() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DeserializationFails());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                try {
                    dummy.getNextForUpdate();
                    fail("Expected ObjectIOException");
                } catch (ObjectIOException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceUpdateOldTxn() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.setNext(new DummyManagedObject());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                try {
                    dummy.getNextForUpdate();
                    fail("Expected TransactionNotActiveException");
                } catch (TransactionNotActiveException e) {
                    System.err.println(e);
                }
        }}, taskOwner);
    }

    @Test 
    public void testGetReferenceUpdateLocking() throws Exception {
	/*
	 * Create a fresh data service -- BDB Java edition does not permit
	 * changing the lock timeout for an existing database.
	 * -tjb@sun.com (07/22/2008)
	 */
	String dir = getDbDirectory() + "testGetReferenceUpdateLocking";
        Properties properties = getProperties();
        properties.setProperty(DataStoreImplClassName + ".directory", dir);
	properties.setProperty(getLockTimeoutPropertyName(properties), "500");
        properties.setProperty("com.sun.sgs.txn.timeout", "1000");
        serverNodeRestart(properties, true);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = new DummyManagedObject();
                dummy.setValue("a");
                service.setBinding("dummy", dummy);
                dummy.setNext(new DummyManagedObject());
        }}, taskOwner);

        final Semaphore mainFlag = new Semaphore(0);
        final Semaphore threadFlag = new Semaphore(0);

        txnScheduler.scheduleTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                dummy.getNext();
                assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
                mainFlag.release();
                assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
        }}, taskOwner);

        txnScheduler.scheduleTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                try {

                    DummyManagedObject dummy2 =
                        (DummyManagedObject) service.getBinding("dummy");
                    threadFlag.release();
                    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
                    dummy2.getNextForUpdate();
                    threadFlag.release();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e);
                }
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
        }}, taskOwner);

        assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
    }

    /* -- Test ManagedReference.getId -- */

    @Test 
    public void testReferenceGetId() throws Exception {
        class TestTask extends InitialTestRunnable {
            BigInteger id;
            BigInteger id2;
            public void run() throws Exception {
                super.run();
                id = service.createReference(dummy).getId();
                DummyManagedObject dummy2 = new DummyManagedObject();
                service.setBinding("dummy2", dummy2);
                id2 = service.createReference(dummy2).getId();
                assertFalse(id.equals(id2));
            }
        }

        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                ManagedReference<DummyManagedObject> ref =
                    service.createReference(dummy);
                assertEquals(task.id, ref.getId());
                DummyManagedObject dummy2 = (DummyManagedObject) service.getBinding("dummy2");
                assertEquals(task.id2, service.createReference(dummy2).getId());
        }}, taskOwner);
    }

    /* -- Test ManagedReference.equals -- */

    @Test 
    public void testReferenceEquals() throws Exception {
        class TestTask extends InitialTestRunnable {
            ManagedReference<DummyManagedObject> reference;
            public void run() throws Exception {
                super.run();
                final ManagedReference<DummyManagedObject> ref =
                    service.createReference(dummy);
                reference = ref;
                assertFalse(ref.equals(null));
                assertFalse(ref.equals(Boolean.TRUE));
                assertTrue(ref.equals(ref));
                assertTrue(ref.equals(service.createReference(dummy)));
                DummyManagedObject dummy2 = new DummyManagedObject();
                ManagedReference<DummyManagedObject> ref2 =
                    service.createReference(dummy2);
                assertFalse(ref.equals(ref2));
                ManagedReference<ManagedObject> ref3 =
                    new ManagedReference<ManagedObject>() {
                        public ManagedObject get() { return null; }
                        public ManagedObject getForUpdate() { return null; }
                        public BigInteger getId() { return ref.getId(); }
                };
                assertFalse(ref.equals(ref3));
            }
        }

        final TestTask task = new TestTask();
        txnScheduler.runTask(task, taskOwner);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                dummy = (DummyManagedObject) service.getBinding("dummy");
                ManagedReference<DummyManagedObject> ref4 =
                    service.createReference(dummy);
                assertTrue(task.reference.equals(ref4));
                assertTrue(ref4.equals(task.reference));
                assertEquals(task.reference.hashCode(), ref4.hashCode());
        }}, taskOwner);
    }

    /* -- Test shutdown -- */

    @Test 
    public void testShutdownAgain() throws Exception {
        serverNode.shutdown(false);
        // Note:  do not null out serverNode here;  it will be used
        // again in the shutdown action.
	ShutdownAction action = new ShutdownAction();
        try {
	    action.waitForDone();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException) {
                System.err.println(e);
            } else {
                fail("Expected IllegalStateException");
            }
        } finally {
            // ensure that the next test will run
            serverNode = null;
        }
    }

    @Test 
    public void testShutdownInterrupt() throws Exception {
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    ShutdownServiceAction action =
                            new ShutdownServiceAction(service);
                    action.assertBlocked();
                    action.interrupt();
                    action.assertResult(false);
                    service.setBinding("dummy", new DummyManagedObject());
            }}, taskOwner);
        } finally {
            try {
                serverNode.shutdown(false);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException) {
                    // we expect this:  the data service is known to be shut down
                    System.err.println(e);
                } else {
                    fail("Expected IllegalStateException");
                }
            } finally {
                // we really want the serverNode set to null
                serverNode = null;
            }
        }
    }

    @Test 
    public void testConcurrentShutdownInterrupt() throws Exception {
        class TestTask extends InitialTestRunnable {
            ShutdownServiceAction action2;
            public void run() throws Exception {
                super.run();
                ShutdownServiceAction action1 =
                        new ShutdownServiceAction(service);
                action1.assertBlocked();
                action2 = new ShutdownServiceAction(service);
                action2.assertBlocked();
                action1.interrupt();
                action1.assertResult(false);
                action2.assertBlocked();
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
            }
        }

        try {
            TestTask task = new TestTask();
            txnScheduler.runTask(task, taskOwner);
            task.action2.assertResult(true);
        } catch (TestAbortedTransactionException e) {
            // this is expected:  we threw it above
        } finally {
            try {
                serverNode.shutdown(false);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException) {
                    // we expect this:  the data service is known to be shut down
                    System.err.println(e);
                } else {
                    fail("Expected IllegalStateException");
                }
            } finally {
                // we really want the serverNode set to null
                serverNode = null;
            }
        }
    }

    @Test 
    public void testConcurrentShutdownRace() throws Exception {
        class TestTask extends InitialTestRunnable {
            ShutdownServiceAction action1;
            ShutdownServiceAction action2;
            public void run() throws Exception {
                super.run();
                action1 = new ShutdownServiceAction(service);
                action1.assertBlocked();
                action2 = new ShutdownServiceAction(service);
                action2.assertBlocked();
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
            }
        }

        try {
            TestTask task = new TestTask();
            txnScheduler.runTask(task, taskOwner);
            boolean result1;
            try {
                result1 = task.action1.waitForDone();
            } catch (IllegalStateException e) {
                result1 = false;
            }
            boolean result2;
            try {
                result2 = task.action2.waitForDone();
            } catch (IllegalStateException e) {
                result2 = false;
            }
            assertTrue(result1 || result2);
            assertFalse(result1 && result2);
        } catch (TestAbortedTransactionException e) {
            // this is expected:  we threw it above
        } finally {
            try {
                serverNode.shutdown(false);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException) {
                    // we expect this:  the data service is known to be shut down
                    System.err.println(e);
                } else {
                    fail("Expected IllegalStateException");
                }
            } finally {
                // we really want the serverNode set to null
                serverNode = null;
            }
        }
    }

    @Test 
    public void testShutdownRestart() throws Exception {

        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        serverNodeRestart(null, false);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                assertEquals(dummy, service.getBinding("dummy"));
        }}, taskOwner);
    }

    /* -- Other tests -- */

    @Test 
    public void testCommitNoStoreParticipant() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);
        // We use dummy outside of the transaction - it is transient here.
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.removeObject(dummy);
        }}, taskOwner);
    }

    @Test (expected=TestAbortedTransactionException.class)
    public void testAbortNoStoreParticipant() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);
        // We use dummy outside of the transaction - it is transient here.
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.removeObject(dummy);
                Transaction txn = txnProxy.getCurrentTransaction();
                txn.abort(new TestAbortedTransactionException("abort"));
        }}, taskOwner);
    }

    @Test 
    public void testCommitReadOnly() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.getBinding("dummy");
        }}, taskOwner);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.getBinding("dummy");
        }}, taskOwner);
    }

    @Test
    public void testAbortReadOnly() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);
        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.getBinding("dummy");
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.getBinding("dummy");
        }}, taskOwner);
    }

    @Test 
    public void testContentEquals() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.setBinding("a", new ContentEquals(3));
                service.setBinding("b", new ContentEquals(3));
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
            assertNotSame(service.getBinding("a"), service.getBinding("b"));
        }}, taskOwner);
    }

    @Test 
    public void testSerializeReferenceToEnclosing() throws Exception {
	serializeReferenceToEnclosingInternal();
    }

    @Test 
    public void testSerializeReferenceToEnclosingToStringFails()
	throws Exception
    {
	FailingMethods.failures = Failures.TOSTRING;
	try {
	    serializeReferenceToEnclosingInternal();
	} finally {
	    FailingMethods.failures = Failures.NONE;
	}
    }

    @Test 
    public void testSerializeReferenceToEnclosingHashCodeFails()
	throws Exception
    {
	FailingMethods.failures = Failures.TOSTRING_AND_HASHCODE;
	try {
	    serializeReferenceToEnclosingInternal();
	} finally {
	    FailingMethods.failures = Failures.NONE;
	}
    }

    private void serializeReferenceToEnclosingInternal() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.setBinding("a", NonManaged.staticLocal);
                service.setBinding("b", NonManaged.staticAnonymous);
                service.setBinding("c", new NonManaged().createMember());
                service.setBinding("d", new NonManaged().createInner());
                service.setBinding("e", new NonManaged().createAnonymous());
                service.setBinding("f", new NonManaged().createLocal());
        }}, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.setBinding("a", Managed.staticLocal);
                service.setBinding("b", Managed.staticAnonymous);
                service.setBinding("c", new NonManaged().createMember());
        }}, taskOwner);

	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("a", new Managed().createInner());
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}

	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("b", new Managed().createAnonymous());
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}

	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("b", new Managed().createLocal());
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    /** Which methods should fail. */
    enum Failures {
	NONE, TOSTRING, TOSTRING_AND_HASHCODE;
    }

    /**
     * Defines facilities for creating objects whose toString and hashCode
     * methods will fail on demand.  The toString methods will fail if the
     * failures field is set to Failures.TOSTRING.  Both the toString and
     * hashCode methods will fail if the field is set to
     * Failures.TOSTRING_AND_HASHCODE.
     */
    static class FailingMethods {
	static Failures failures = Failures.NONE;
	public String toString() {
	    return toString(this);
	}
	public int hashCode() {
	    return hashCode(super.hashCode());
	}
	static String toString(Object object) {
	    if (failures != Failures.NONE) {
		throw new RuntimeException("toString fails");
	    }
	    String className = object.getClass().getName();
	    int dot = className.lastIndexOf('.');
	    if (dot > 0) {
		className = className.substring(dot + 1);
	    }
	    return className + "[hashCode=" + object.hashCode() + "]";
	}
	static int hashCode(int hashCode) {
	    if (failures == Failures.TOSTRING_AND_HASHCODE) {
		throw new RuntimeException("hashCode fails");
	    }
	    return hashCode;
	}
    }

    static class DummyManagedObjectFailingMethods extends DummyManagedObject {
	private static final long serialVersionUID = 1;
	public String toString() {
	    return FailingMethods.toString(this);
	}
	public int hashCode() {
	    return FailingMethods.hashCode(super.hashCode());
	}
    }

    static class NonManaged extends FailingMethods implements Serializable {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObjectFailingMethods() {
	        private static final long serialVersionUID = 1L;
	    };
	static class Member extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
	}
	ManagedObject createAnonymous() {
	    return new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    static class Managed extends FailingMethods
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	static class Member extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
        }
	ManagedObject createAnonymous() {
	    return new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    @Test 
    public void testDeadlock() throws Exception {
        Properties properties = getProperties();
        properties.setProperty("com.sun.sgs.txn.timeout", "1000");
        serverNodeRestart(properties, false);

        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.setBinding("dummy2", new DummyManagedObject());
        }}, taskOwner);

        class TestTask1 extends TestAbstractKernelRunnable {
            Exception exception = null;
            private final int runNumber;
            private final Semaphore flag1;
            private final Semaphore flag2;
            private final Semaphore doneFlag;
            private boolean firstTry = true;
            TestTask1(int runNumber, Semaphore flag1, 
                      Semaphore flag2, Semaphore doneFlag) 
            {
                this.runNumber = runNumber;
                this.flag1 = flag1;
                this.flag2 = flag2;
                this.doneFlag = doneFlag;
            }
            public void run() throws Exception {
                Transaction txn = txnProxy.getCurrentTransaction();
                if (!firstTry) {     
                    // Don't want to loop forever with retriable exceptions
                    throw new RuntimeException("just kill it");             
                }

                dummy = (DummyManagedObject) service.getBinding("dummy");

                // First step is done, let the other task proceed
                flag1.release();
                firstTry = false;

                // We can only hope the second task gets a chance
                Thread.sleep(runNumber * 500);
                System.err.println(runNumber + " task 1 ("
                                 + txn + "): woke from sleep, acquiring flag");
                flag2.acquire();
                try {
                    ((DummyManagedObject)
                        service.getBinding("dummy2")).setValue(runNumber);
                    System.err.println(runNumber + " task 1 ("
                                       + txn + "): commit");
                } catch (Exception e) {
                    System.err.println(
                            runNumber + " task 1 (" + txn + "): " + e);
                    exception = e;
                } finally {
                    doneFlag.release();
                }
            }
        }

        class TestTask2 extends TestAbstractKernelRunnable {
            Exception exception = null;
            Transaction txn = null;
            private final int runNumber;
            private final Semaphore flag1;
            private final Semaphore flag2;
            private final Semaphore doneFlag;
            private boolean firstTry = true;
            TestTask2(int runNumber, Semaphore flag1, 
                      Semaphore flag2, Semaphore doneFlag) 
            {
                this.runNumber = runNumber;
                this.flag1 = flag1;
                this.flag2 = flag2;
                this.doneFlag = doneFlag;
            }
            public void run() throws Exception {
                txn = txnProxy.getCurrentTransaction();
                if (!firstTry) {
                    throw new RuntimeException("just kill it");
                }
                flag1.acquire();
                service.getBinding("dummy2");
                // Let the other task proceed
                flag2.release();
                System.err.println(runNumber + " task 2 ("
                                 + txn + "): released flag");
                firstTry = false;
                try {
                    ((DummyManagedObject)
                         service.getBinding("dummy")).setValue(runNumber);
                    System.err.println(runNumber + " task 2 ("
                                       + txn + "): commit");
                } catch (Exception e) {
                    System.err.println(
                        runNumber + " task 2 (" + txn + "): " + e);
                    exception = e;
                } finally {
                    doneFlag.release();
                }
            }
        }

	for (int i = 0; i < 5; i++) {
            final Semaphore flag1 = new Semaphore(1);
            final Semaphore flag2 = new Semaphore(1);
            // Both threads must release before an acquire can occur
            final Semaphore doneFlag = new Semaphore(2);   
            TestTask1 task1 = new TestTask1(i, flag1, flag2, doneFlag);
            TestTask2 task2 = new TestTask2(i, flag1, flag2, doneFlag);
      
            // Note that we're using schedule task here, not run task,
            // which allows the tasks to run concurrently.
            // We should guarantee that these two can run concurrently
            // (default number of consumer threads allows this)

            flag1.acquire();
            flag2.acquire();
            doneFlag.acquire(2);
            System.err.println(i + " main loop, acquired flags");
            txnScheduler.scheduleTask(task1, taskOwner);
            txnScheduler.scheduleTask(task2, taskOwner);

            doneFlag.acquire(2);
            
            if (task1.exception != null &&
                !(task1.exception instanceof TransactionAbortedException))
            {
                throw task1.exception;
            } else if (task2.exception != null &&
                !(task2.exception instanceof TransactionAbortedException))
            {
                throw task2.exception;
            } else if (task1.exception == null && task2.exception == null) {
                fail ("Expected TransactionAbortedException");
            }
	}
    }

    @Test 
    public void testModifiedNotSerializable() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    dummy = (DummyManagedObject) service.getBinding("dummy");
                    dummy.value = Thread.currentThread();
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    @Test 
    public void testNotSerializableAfterDeserialize() throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                dummy.value = new SerializationFailsAfterDeserialize();
        }}, taskOwner);
	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() {
                service.getBinding("dummy");
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    /* -- App and service binding methods -- */

    ManagedObject getBinding(boolean app, DataService service, String name) {
	return app
	    ? service.getBinding(name) : service.getServiceBinding(name);
    }

    void setBinding(
	boolean app, DataService service, String name, Object object)
    {
	if (app) {
	    service.setBinding(name, object);
	} else {
	    service.setServiceBinding(name, object);
	}
    }

    void removeBinding(boolean app, DataService service, String name) {
	if (app) {
	    service.removeBinding(name);
	} else {
	    service.removeServiceBinding(name);
	}
    }

    String nextBoundName(boolean app, DataService service, String name) {
	if (app) {
	    return service.nextBoundName(name);
	} else {
	    return service.nextServiceBoundName(name);
	}
    }

    /* -- Other methods and classes -- */

    /** Creates a unique directory. */
    static AtomicInteger fileNumber = new AtomicInteger();
    String createDirectory() throws IOException {
        String name = "temp" + fileNumber.toString();
        fileNumber.getAndIncrement();
	File dir = File.createTempFile(name, "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	return dir.getPath();
    }

    /**
     * Returns a DataServiceImpl for the shared database using the specified
     * properties and component registry.
     */
    protected DataServiceImpl createDataServiceImpl(
	Properties props,
	ComponentRegistry componentRegistry,
	TransactionProxy txnProxy)
	throws Exception
    {
	File dir = new File(getDbDirectory());
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(props, componentRegistry, txnProxy);
    }

    /** Returns the default properties to use for creating data services. */
    protected Properties getProperties() throws Exception {
        Properties p = SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        p.setProperty("com.sun.sgs.finalService", "DataService");
        p.setProperty(
            DataServiceImplClassName + ".debug.check.interval", "0");
        p.setProperty(
            TransactionCoordinator.TXN_DISABLE_PREPAREANDCOMMIT_OPT_PROPERTY,
            disableTxnCommitOpt ? "true" : "false");
        return p;
    }

    /** Returns the db directory property value. */
    protected String getDbDirectory() throws Exception {
        Properties p = SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        return p.getProperty(DataStoreImplClassName + ".directory");
    }

    /** Another managed object type. */
    static class AnotherManagedObject extends DummyManagedObject {
	private static final long serialVersionUID = 1;
    }

    /** A managed object that fails during serialization. */
    static class SerializationFails extends DummyManagedObject {
        private static final long serialVersionUID = 1L;
	private void writeObject(ObjectOutputStream out)
	    throws IOException
	{
	    throw new IOException("Serialization fails");
	}
    }

    /**
     * A serializable object that fails during serialization after
     * deserialization.
     */
    static class SerializationFailsAfterDeserialize implements Serializable {
        private static final long serialVersionUID = 1L;
	private transient boolean deserialized;
	private void writeObject(ObjectOutputStream out)
	    throws IOException
	{
	    if (deserialized) {
		throw new IOException(
		    "Serialization fails after deserialization");
	    }
	}
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	    deserialized = true;
	}
    }

    /** A managed object that fails during deserialization. */
    static class DeserializationFails extends DummyManagedObject {
        private static final long serialVersionUID = 1L;
	private void readObject(ObjectInputStream in)
	    throws IOException
	{
	    throw new IOException("Deserialization fails");
	}
    }

    /** A managed object whose deserialization is delayed. */
    static class DeserializationDelayed extends DummyManagedObject {
	private static final long serialVersionUID = 1;
	private static long delay = 0;
	private ManagedReference<DummyManagedObject> next = null;
	@Override
	public void setNext(DummyManagedObject next) {
	    service.markForUpdate(this);
	    this.next = service.createReference(next);
	}
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    try {
		Thread.sleep(delay);
	    } catch (InterruptedException e) {
		fail("Unexpected exception: " + e);
	    }
	    in.defaultReadObject();
	}
    }

    /** A managed object that uses content equality. */
    static class ContentEquals implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private final int i;
	ContentEquals(int i) { this.i = i; }
	public String toString() { return "ContentEquals[" + i + "]"; }
	public int hashCode() { return i; }
	public boolean equals(Object o) {
	    return o instanceof ContentEquals && i == ((ContentEquals) o).i;
	}
    }

    /* -- Support for testing unusual states -- */

    /**
     * An action, with an optional setup step, to be run in the context of an
     * unusual state.
     */
    abstract class Action {
	void setUp() { };
	abstract void run();
    }

    /** Tests running the action while aborting. */
    void testAborting(final Action action) throws Exception {
        /* New object removed in this transaction */
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    action.setUp();
                    class Participant
                            extends DummyNonDurableTransactionParticipant
                    {
                        boolean ok;
                        public void abort(Transaction txn) {
                            try {
                                action.run();
                            } catch (TransactionNotActiveException e) {
                                ok = true;
                                throw e;
                            }
                        }
                    }
                    Participant participant = new Participant();
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.join(participant);
                    txn.abort(new TestAbortedTransactionException("abort"));
                    assertTrue("Action should throw", participant.ok);
            }}, taskOwner);

        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }
    }

    /** Tests running the action after abort. */
    private void testAborted(final Action action) throws Exception {
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    action.setUp();
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.abort(new TestAbortedTransactionException("abort"));
                    try {
                        action.run();
                        fail("Expected TransactionNotActiveException");
                    } catch (TransactionNotActiveException e) {
                        System.err.println(e);
                    }
            }}, taskOwner);

        } catch (TestAbortedTransactionException e) {
            System.err.println(e);
        }
    }

    /** Tests running the action while preparing. */
    private void testPreparing(final Action action) throws Exception {
        class Participant extends DummyNonDurableTransactionParticipant {
            boolean ok;
            public boolean prepare(Transaction txn) throws Exception {
                try {
                    action.run();
                    return false;
                } catch (TransactionNotActiveException e) {
                    ok = true;
                    throw e;
                }
            }
        }

        final Participant participant = new Participant();

        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    action.setUp();
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.join(participant);
                }}, taskOwner);
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action while committing. */
    private void testCommitting(final Action action) throws Exception {
	class Participant extends DummyNonDurableTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    action.run();
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	}
	final Participant participant = new Participant();
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    action.setUp();
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.join(participant);
            }}, taskOwner);
        }  catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action after commit. */
    private void testCommitted(final Action action) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                action.setUp();
        }}, taskOwner);

	try {
	    action.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /**
     * Tests running the action with an existing transaction while shutting
     * down.
     */
    private void testShuttingDownExistingTxn(final Action action)
        throws Exception
    {
        class ShutdownTask extends InitialTestRunnable {
            ShutdownAction shutdownAction;
            public void run() throws Exception {
                super.run();
                action.setUp();
                shutdownAction = new ShutdownAction();
                shutdownAction.assertBlocked();
                action.run();
            }
        }
        ShutdownTask task = new ShutdownTask();
        txnScheduler.runTask(task, taskOwner);

        task.shutdownAction.assertResult(true);
    }

    /** Tests running the action with a new transaction while shutting down. */
    private void testShuttingDownNewTxn(final Action action) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable(), taskOwner);

        class ShutdownTask extends TestAbstractKernelRunnable {
            ShutdownAction shutdownAction;
            ThreadAction threadAction;
            public void run() throws Exception {
                service.createReference(new DummyManagedObject());
                action.setUp();
                shutdownAction = new ShutdownAction();
                shutdownAction.assertBlocked();

                threadAction = new ThreadAction<Void>() {
                    protected Void action() {
                        try {
                            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                                public void run() {
                                    try {
                                        action.run();
                                        fail("Expected IllegalStateException");
                                    } catch (IllegalStateException e) {
                                        assertEquals("Service is shutting down",
                                                     e.getMessage());
                                    }
                            }}, taskOwner);
                        } catch (Exception e) {
                            fail("Unexpected exception " + e);
                        }
                        return null;
                    }
                };
            }
        }
        ShutdownTask task = new ShutdownTask();
        txnScheduler.runTask(task, taskOwner);
        task.threadAction.assertDone();
        task.shutdownAction.assertResult(true);
    }

    /** Tests running the action after shutdown. */
    void testShutdown(final Action action) throws Exception {
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    action.setUp();
                    Transaction txn = txnProxy.getCurrentTransaction();
                    txn.abort(new TestAbortedTransactionException("abort"));
            }}, taskOwner);
        } catch (TestAbortedTransactionException e) {
            System.err.println(" Transaction aborted: " + e);
        }

        try {
            // shut down just the data service;  we need the txnScheduler
            // to still be running
            service.shutdown();

            try {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                    public void run() {
                        action.run();
                }}, taskOwner);

                fail("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                System.err.println(e);
            }

        } finally {
            try {
                serverNode.shutdown(false);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalStateException) {
                    // we expect this:  the data service is known to be shut down
                    System.err.println(e);
                } else {
                    fail("Expected IllegalStateException");
                }
            } finally {
                // we really want the serverNode set to null
                serverNode = null;
            }
        }
    }

    /**
     * A utility class for running an operation in a separate thread and
     * insuring that it either completes or blocks.
     *
     * @param	<T> the return type of the operation
     */
    abstract static class ThreadAction<T> extends Thread {

	/**
	 * The number of milliseconds to wait to see if an operation is
	 * blocked.
	 */
	private static final long BLOCKED = 5;

	/**
	 * The number of milliseconds to wait to see if an operation will
	 * complete.
	 */
	private static final long COMPLETED = 2000;

	/** Set to true when the operation is complete. */
	private boolean done = false;

	/**
	 * Set when the operation is complete to the exception thrown by the
	 * operation or null if no exception was thrown.
	 */
	private Throwable exception;

	/**
	 * Set to the result of the operation when the operation is complete.
	 */
	private T result;

	/**
	 * Creates an instance of this class and starts the operation in a
	 * separate thread.
	 */
	ThreadAction() {
	    start();
	}

	/** Performs the operation and collects the results. */
	public void run() {
	    try {
		result = action();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	/**
	 * The operation to be performed.
	 *
	 * @return	the result of the operation
	 * @throws	Exception if the operation fails
	 */
	abstract T action() throws Exception;

	/**
	 * Asserts that the operation is blocked.
	 *
	 * @throws	InterruptedException if the operation is interrupted
	 */
	synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(BLOCKED);
	    assertEquals("Expected no exception", null, exception);
	    assertFalse("Expected operation to be blocked", done);
	}

	/**
	 * Waits for the operation to complete.
	 *
	 * @return	whether the operation completed
	 * @throws	Exception if the operation failed
	 */
	synchronized boolean waitForDone() throws Exception {
	    waitForDoneInternal();
	    if (!done) {
		return false;
	    } else if (exception == null) {
		return true;
	    } else if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
	}

	/**
	 * Asserts that the operation completed with the specified result.
	 *
	 * @param	expectedResult the expected result
	 * @throws	Exception if the operation failed
	 */
	synchronized void assertResult(Object expectedResult)
	    throws Exception
	{
	    assertDone();
	    assertEquals("Unexpected result", expectedResult, result);
	}

	/**
	 * Asserts that the operation completed.
	 *
	 * @throws	Exception if the operation failed
	 */
	synchronized void assertDone() throws Exception {
	    waitForDoneInternal();
	    assertTrue("Expected operation to be done", done);
	    if (exception != null) {
		if (exception instanceof Exception) {
		    throw (Exception) exception;
		} else {
		    throw (Error) exception;
		}
	    }
	}

	/** Wait for the operation to complete. */
	private synchronized void waitForDoneInternal()
	    throws InterruptedException
	{
	    long wait = COMPLETED;
	    long start = System.currentTimeMillis();
	    while (!done && wait > 0) {
		wait(wait);
		long now = System.currentTimeMillis();
		wait -= (now - start);
		start = now;
	    }
	}
    }

    /** Use this thread to control a call to shutdown that may block. */
    class ShutdownAction extends ThreadAction<Boolean> {
	ShutdownAction() { }
	protected Boolean action() throws Exception {
            serverNode.shutdown(false);
            serverNode = null;
            return true;
	}
    }

    /** Use this thread to control a call to shutdown that may block. */
    class ShutdownServiceAction extends ThreadAction<Boolean> {
        final DataService service;
	ShutdownServiceAction(DataService service) {
            this.service = service;
        }
	protected Boolean action() throws Exception {
	    return service.shutdown();
	}
    }

    /**
     * The exception thrown when we abort a transaction;  this gives
     * us a type to test against when catching exceptions thrown by
     * runTask.  This is NOT a retryable exception.
     */
    class TestAbortedTransactionException extends RuntimeException {
        private static final long serialVersionUID = 1;
        TestAbortedTransactionException(String message) {
            super(message);
        }
        TestAbortedTransactionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A dummy implementation of DataStore. */
    static class DummyDataStore implements DataStore {
	public long createObject(Transaction txn) { return 0; }
	public void markForUpdate(Transaction txn, long oid) { }
	public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	    return null;
	}
	public void setObject(Transaction txn, long oid, byte[] data) { }
	public void setObjects(
	    Transaction txn, long[] oids, byte[][] dataArray)
	{ }
	public void removeObject(Transaction txn, long oid) { }
	public long getBinding(Transaction txn, String name) { return 0; }
	public void setBinding(Transaction txn, String name, long oid) { }
	public void removeBinding(Transaction txn, String name) { }
	public String nextBoundName(Transaction txn, String name) {
	    return null;
	}
	public boolean shutdown() { return false; }
	public int getClassId(Transaction txn, byte[] classInfo) { return 0; }
	public byte[] getClassInfo(Transaction txn, int classId) {
	    return null;
	}
	public long nextObjectId(Transaction txn, long oid) { return -1; }
    }

    /**
     * A managed object with subobjects that it removes during removingObject.
     */
    static class ObjectWithRemoval extends DummyManagedObject
	implements ManagedObjectRemoval
    {
	private static final long serialVersionUID = 1;
	private final ManagedReference<ObjectWithRemoval> left;
	private final ManagedReference<ObjectWithRemoval> right;
	transient boolean removingCalled;
	ObjectWithRemoval() {
	    this(3);
	}
	ObjectWithRemoval(int depth) {
	    if (--depth <= 0) {
		left = null;
		right = null;
		return;
	    }
	    left = service.createReference(new ObjectWithRemoval(depth));
	    right = service.createReference(new ObjectWithRemoval(depth));
	}
	public void removingObject() {
	    removingCalled = true;
	    if (left != null) {
		service.removeObject(left.get());
	    }
	    if (right != null) {
		service.removeObject(right.get());
	    }
	}
    }

    /** Returns the current number of objects. */
    private int getObjectCount() {
	int count = 0;
	BigInteger last = null;
	while (true) {
	    BigInteger next = service.nextObjectId(last);
	    if (next == null) {
		break;
	    }
	    last = next;
	    count++;
	}
	return count;
    }

    /**
     * Check that committing throws ObjectIOException after setting a name
     * binding to the specified object.
     */
    private void objectIOExceptionOnCommit(final ManagedObject object)
	throws Exception
    {
        try {
            txnScheduler.runTask(new InitialTestRunnable() {
                public void run() throws Exception {
                    super.run();
                    service.setBinding("foo", object);
            }}, taskOwner);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}

    }

    /**
     * Check that committing succeeds after setting a name binding to the
     * specified object.
     */
    private void okOnCommit(final ManagedObject object) throws Exception {
        txnScheduler.runTask(new InitialTestRunnable() {
            public void run() throws Exception {
                super.run();
                service.setBinding("foo", object);
        }}, taskOwner);
    }
}
