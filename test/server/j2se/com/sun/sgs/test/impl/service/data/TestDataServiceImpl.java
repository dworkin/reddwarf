/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import junit.framework.Test;
import junit.framework.TestCase;

/** Test the DataServiceImpl class */
@SuppressWarnings("hiding")
public class TestDataServiceImpl extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    protected static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataServiceImpl.db";

    /** The component registry. */
    private static final DummyComponentRegistry componentRegistry =
	new DummyComponentRegistry();

    /** The transaction proxy. */
    private static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** An instance of the data service, to test. */
    static DataServiceImpl service;

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	cleanDirectory(dbDirectory);
    }

    /** Set when the test passes. */
    protected boolean passed;

    /** Default properties for creating the shared database. */
    protected Properties props;

    /** An initial, open transaction. */
    private DummyTransaction txn;

    /** A managed object. */
    private DummyManagedObject dummy;

    /** Creates the test. */
    public TestDataServiceImpl(String name) {
	super(name);
    }

    /**
     * Prints the test case, initializes the data service if needed, starts a
     * transaction, and creates and binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	props = getProperties();
	if (service == null) {
	    service = createDataServiceImpl();
	    createTransaction();
	    service.configure(componentRegistry, txnProxy);
	    txn.commit();
	    componentRegistry.setComponent(DataManager.class, service);
	}
	componentRegistry.registerAppContext();
	createTransaction();
	dummy = new DummyManagedObject();
	service.setBinding("dummy", dummy);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Aborts the transaction if non-null, and nulls the service field if the
     * test failed.
     */
    protected void tearDown() throws Exception {
	try {
	    if (txn != null) {
		txn.abort(null);
	    }
	    if (!passed && service != null) {
		new ShutdownAction().waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	} finally {
	    txn = null;
	    if (!passed) {
		service = null;
	    }
	}
    }

    /* -- Test constructor -- */

    public void testConstructorNullArgs() throws Exception {
	try {
	    createDataServiceImpl(null, componentRegistry);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    createDataServiceImpl(props, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	props.remove(StandardProperties.APP_NAME);
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadDebugCheckInterval() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".debug.check.interval", "gorp");
	try {
	    createDataServiceImpl(props, componentRegistry);
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
    public void testConstructorNoDirectory() throws Exception {
        String rootDir = createDirectory();
        File dataDir = new File(rootDir, "dsdb");
        if (!dataDir.mkdir()) {
            throw new RuntimeException("Failed to create sub-dir: " + dataDir);
        }
	props.remove(DataStoreImplClassName + ".directory");
	props.setProperty(StandardProperties.APP_ROOT, rootDir);
	DataServiceImpl testSvc =
	    createDataServiceImpl(props, componentRegistry);
        testSvc.shutdown();
    }

    public void testConstructorNoDirectoryNorRoot() throws Exception {
	props.remove(DataStoreImplClassName + ".directory");
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNotFound() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class", "AnUnknownClass");
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNotDataStore() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    Object.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNoConstructor() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreNoConstructor.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreNoConstructor extends DummyDataStore { }

    public void testConstructorDataStoreClassAbstract() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreAbstract.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static abstract class DataStoreAbstract extends DummyDataStore {
	public DataStoreAbstract(Properties props) { }
    }

    public void testConstructorDataStoreClassConstructorFails()
	throws Exception
    {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreConstructorFails.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreConstructorFails extends DummyDataStore {
	public DataStoreConstructorFails(Properties props) {
	    throw new RuntimeException("Constructor fails");
	}
    }

    /* -- Test getName -- */

    public void testGetName() {
	assertNotNull(service.getName());
    }

    /* -- Test configure -- */

    public void testConfigureNullArgs() throws Exception {
	txn.commit();
	txn = null;
	service.shutdown();
	service = createDataServiceImpl();
	try {
	    service.configure(null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    service.configure(componentRegistry, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	service = null;
    }

    public void testConfigureNoTxn() throws Exception {
	txn.commit();
	txn = null;
	service = createDataServiceImpl();
	try {
	    service.configure(componentRegistry, txnProxy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	service = null;
    }

    public void testConfigureAgain() throws Exception {
	try {
	    service.configure(componentRegistry, txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	service = null;
    }

    public void testConfigureAborted() throws Exception {
	txn.commit();
	service = createDataServiceImpl();
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	txn.abort(null);
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	txn.commit();
	txn = null;
	service = null;
    }

    /* -- Test getBinding and getServiceBinding -- */

    public void testGetBindingNullArgs() {
	testGetBindingNullArgs(true);
    }
    public void testGetServiceBindingNullArgs() {
	testGetBindingNullArgs(false);
    }
    private void testGetBindingNullArgs(boolean app) {
	try {
	    getBinding(app, service, null, ManagedObject.class);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    getBinding(app, service, "dummy", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingEmptyName() throws Exception {
	testGetBindingEmptyName(true);
    }
    public void testGetServiceBindingEmptyName() throws Exception {
	testGetBindingEmptyName(false);
    }
    private void testGetBindingEmptyName(boolean app) throws Exception {
	setBinding(app, service, "", dummy);
	txn.commit();
	createTransaction();
	DummyManagedObject result =
	    getBinding(app, service, "", DummyManagedObject.class);
	assertEquals(dummy, result);
    }

    public void testGetBindingWrongType() throws Exception {
	testGetBindingWrongType(true);
    }
    public void testGetServiceBindingWrongType() throws Exception {
	testGetBindingWrongType(false);
    }
    private void testGetBindingWrongType(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	try {
	    getBinding(app, service, "dummy", AnotherManagedObject.class);
	    fail("Expected ClassCastException");
	} catch (ClassCastException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingNotFound() throws Exception {
	testGetBindingNotFound(true);
    }
    public void testGetServiceBindingNotFound() throws Exception {
	testGetBindingNotFound(false);
    }
    private void testGetBindingNotFound(boolean app) throws Exception {
	/* No binding */
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* New binding removed in this transaction */
	setBinding(app, service, "testGetBindingNotFound",
		   new DummyManagedObject());
	removeBinding(app, service, "testGetBindingNotFound");
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* New binding removed in last transaction */
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* Existing binding removed in this transaction */
	setBinding(app, service, "testGetBindingNotFound",
		   new DummyManagedObject());
	txn.commit();
	createTransaction();
	removeBinding(app, service, "testGetBindingNotFound");
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* Existing binding removed in last transaction. */
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(true);
    }
    public void testGetServiceBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(false);
    }
    private void testGetBindingObjectNotFound(boolean app) throws Exception {
	/* New object removed in this transaction */
	setBinding(app, service, "testGetBindingRemoved", dummy);
	service.removeObject(dummy);
	try {
	    getBinding(app, service, "testGetBindingRemoved",
		       DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	/* New object removed in last transaction */
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingRemoved",
		       DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	setBinding(app, service, "testGetBindingRemoved",
		   new DummyManagedObject());
	txn.commit();
	/* Existing object removed in this transaction */
	createTransaction();
	service.removeObject(
	    getBinding(app, service, "testGetBindingRemoved",
		       DummyManagedObject.class));
	try {
	    getBinding(app, service, "testGetBindingRemoved",
		       DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	/* Existing object removed in last transaction */
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingRemoved",
		       DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action getBinding = new Action() {
	void run() { service.getBinding("dummy", DummyManagedObject.class); }
    };
    private final Action getServiceBinding = new Action() {
	void setUp() { service.setServiceBinding("dummy", dummy); }
	void run() {
	    service.getServiceBinding("dummy", DummyManagedObject.class);
	}
    };
    public void testGetBindingUninitialized() throws Exception {
	testUninitialized(getBinding);
    }
    public void testGetServiceBindingUninitialized() throws Exception {
	testUninitialized(getServiceBinding);
    }
    public void testGetBindingAborting() throws Exception {
	testAborting(getBinding);
    }
    public void testGetServiceBindingAborting() throws Exception {
	testAborting(getServiceBinding);
    }
    public void testGetBindingAborted() throws Exception {
	testAborted(getBinding);
    }
    public void testGetServiceBindingAborted() throws Exception {
	testAborted(getServiceBinding);
    }
    public void testGetBindingPreparing() throws Exception {
	testPreparing(getBinding);
    }
    public void testGetServiceBindingPreparing() throws Exception {
	testPreparing(getServiceBinding);
    }
    public void testGetBindingCommitting() throws Exception {
	testCommitting(getBinding);
    }
    public void testGetServiceBindingCommitting() throws Exception {
	testCommitting(getServiceBinding);
    }
    public void testGetBindingCommitted() throws Exception {
	testCommitted(getBinding);
    }
    public void testGetServiceBindingCommitted() throws Exception {
	testCommitted(getServiceBinding);
    }
    public void testGetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getBinding);
    }
    public void testGetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getServiceBinding);
    }
    public void testGetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getBinding);
    }
    public void testGetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getServiceBinding);
    }
    public void testGetBindingShutdown() throws Exception {
	testShutdown(getBinding);
    }
    public void testGetServiceBindingShutdown() throws Exception {
	testShutdown(getServiceBinding);
    }

    public void testGetBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(true);
    }
    public void testGetServiceBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(false);
    }
    private void testGetBindingDeserializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new DeserializationFails());
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "dummy", DeserializationFails.class);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingDeserializeAsNull() throws Exception {
	testGetBindingDeserializeAsNull(true);
    }
    public void testGetServiceBindingDeserializeAsNull() throws Exception {
	testGetBindingDeserializeAsNull(false);
    }
    private void testGetBindingDeserializeAsNull(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new DeserializeAsNull());
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "dummy", DeserializeAsNull.class);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingSuccess() throws Exception {
	testGetBindingSuccess(true);
    }
    public void testGetServiceBindingSuccess() throws Exception {
	testGetBindingSuccess(false);
    }
    private void testGetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	DummyManagedObject result =
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	assertEquals(dummy, result);
	txn.commit();
	createTransaction();
	result = getBinding(app, service, "dummy", DummyManagedObject.class);
	assertEquals(dummy, result);
	getBinding(app, service, "dummy", Object.class);
    }

    public void testGetBindingsDifferent() throws Exception {
	DummyManagedObject serviceDummy = new DummyManagedObject();
	service.setServiceBinding("dummy", serviceDummy);
	txn.commit();
	createTransaction();
	DummyManagedObject result =
	    service.getBinding("dummy", DummyManagedObject.class);
	assertEquals(dummy, result);
	result =
	    service.getServiceBinding("dummy", DummyManagedObject.class);
	assertEquals(serviceDummy, result);
    }

    /* -- Test setBinding and setServiceBinding -- */

    public void testSetBindingNullArgs() {
	testSetBindingNullArgs(true);
    }
    public void testSetServiceBindingNullArgs() {
	testSetBindingNullArgs(false);
    }
    private void testSetBindingNullArgs(boolean app) {
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
    }

    public void testSetBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(true);
    }
    public void testSetServiceBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(false);
    }
    private void testSetBindingNotSerializable(boolean app) throws Exception {
	ManagedObject mo = new ManagedObject() { };
	try {
	    setBinding(app, service, "dummy", mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action setBinding = new Action() {
	void run() { service.setBinding("dummy", dummy); }
    };
    private final Action setServiceBinding = new Action() {
	void run() { service.setServiceBinding("dummy", dummy); }
    };
    public void testSetBindingUninitialized() throws Exception {
	testUninitialized(setBinding);
    }
    public void testSetServiceBindingUninitialized() throws Exception {
	testUninitialized(setServiceBinding);
    }
    public void testSetBindingAborting() throws Exception {
	testAborting(setBinding);
    }
    public void testSetServiceBindingAborting() throws Exception {
	testAborting(setServiceBinding);
    }
    public void testSetBindingAborted() throws Exception {
	testAborted(setBinding);
    }
    public void testSetServiceBindingAborted() throws Exception {
	testAborted(setServiceBinding);
    }
    public void testSetBindingPreparing() throws Exception {
	testPreparing(setBinding);
    }
    public void testSetServiceBindingPreparing() throws Exception {
	testPreparing(setServiceBinding);
    }
    public void testSetBindingCommitting() throws Exception {
	testCommitting(setBinding);
    }
    public void testSetServiceBindingCommitting() throws Exception {
	testCommitting(setServiceBinding);
    }
    public void testSetBindingCommitted() throws Exception {
	testCommitted(setBinding);
    }
    public void testSetServiceBindingCommitted() throws Exception {
	testCommitted(setServiceBinding);
    }
    public void testSetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setBinding);
    }
    public void testSetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(setServiceBinding);
    }
    public void testSetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setBinding);
    }
    public void testSetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setServiceBinding);
    }
    public void testSetBindingShutdown() throws Exception {
	testShutdown(setBinding);
    }
    public void testSetServiceBindingShutdown() throws Exception {
	testShutdown(setServiceBinding);
    }

    public void testSetBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(true);
    }
    public void testSetServiceBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(false);
    }
    private void testSetBindingSerializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
	/* Try again with opposite transaction type. */
	createTransaction();
	setBinding(app, service, "dummy", new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingRemoved() throws Exception {
	testSetBindingRemoved(true);
    }
    public void testSetServiceBindingRemoved() throws Exception {
	testSetBindingRemoved(false);
    }
    private void testSetBindingRemoved(boolean app) throws Exception {
	service.removeObject(dummy);
	try {
	    setBinding(app, service, "dummy", dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingManagedObjectNoReference() throws Exception {
	testSetBindingManagedObjectNoReference(true);
    }
    public void testSetServiceBindingManagedObjectNoReference()
	throws Exception
    {
	testSetBindingManagedObjectNoReference(false);
    }
    private void testSetBindingManagedObjectNoReference(boolean app)
	throws Exception
    {
	dummy.setValue(new DummyManagedObject());
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
	createTransaction();
	dummy.setValue(
	    new Object[] {
		null, new Integer(3),
		new DummyManagedObject[] {
		    null, new DummyManagedObject()
		}
	    });
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(true);
    }
    public void testSetServiceBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(false);
    }
    private void testSetBindingManagedObjectNotSerializableCommit(boolean app)
	throws Exception
    {
	dummy.setValue(Thread.currentThread());
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
	createTransaction();
	dummy.setValue(
	    new Object[] {
		null, new Integer(3),
		new Thread[] {
		    null, Thread.currentThread()
		}
	    });
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingSuccess() throws Exception {
	testSetBindingSuccess(true);
    }
    public void testSetServiceBindingSuccess() throws Exception {
	testSetBindingSuccess(false);
    }
    private void testSetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	txn.commit();
	createTransaction();
	assertEquals(
	    dummy,
	    getBinding(app, service, "dummy", DummyManagedObject.class));
	DummyManagedObject dummy2 = new DummyManagedObject();
	setBinding(app, service, "dummy", dummy2);
	txn.abort(null);
	createTransaction();
	assertEquals(
	    dummy,
	    getBinding(app, service, "dummy", DummyManagedObject.class));
	setBinding(app, service, "dummy", dummy2);
	txn.commit();
	createTransaction();
	assertEquals(
	    dummy2,
	    getBinding(app, service, "dummy", DummyManagedObject.class));
    }

    /* -- Test removeBinding and removeServiceBinding -- */

    public void testRemoveBindingNullName() {
	testRemoveBindingNullName(true);
    }
    public void testRemoveServiceBindingNullName() {
	testRemoveBindingNullName(false);
    }
    private void testRemoveBindingNullName(boolean app) {
	try {
	    removeBinding(app, service, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingEmptyName() {
        testRemoveBindingEmptyName(true);
    }
    public void testRemoveServiceBindingEmptyName() {
        testRemoveBindingEmptyName(false);
    }
    private void testRemoveBindingEmptyName(boolean app) {
	setBinding(app, service, "", dummy);
	removeBinding(app, service, "");
	try {
	    removeBinding(app, service, "");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action removeBinding = new Action() {
	void run() { service.removeBinding("dummy"); }
    };
    private final Action removeServiceBinding = new Action() {
	void run() { service.removeServiceBinding("dummy"); }
    };
    public void testRemoveBindingUninitialized() throws Exception {
	testUninitialized(removeBinding);
    }
    public void testRemoveServiceBindingUninitialized() throws Exception {
	testUninitialized(removeServiceBinding);
    }
    public void testRemoveBindingAborting() throws Exception {
	testAborting(removeBinding);
    }
    public void testRemoveServiceBindingAborting() throws Exception {
	testAborting(removeServiceBinding);
    }
    public void testRemoveBindingAborted() throws Exception {
	testAborted(removeBinding);
    }
    public void testRemoveServiceBindingAborted() throws Exception {
	testAborted(removeServiceBinding);
    }
    public void testRemoveBindingPreparing() throws Exception {
	testPreparing(removeBinding);
    }
    public void testRemoveServiceBindingPreparing() throws Exception {
	testPreparing(removeServiceBinding);
    }
    public void testRemoveBindingCommitting() throws Exception {
	testCommitting(removeBinding);
    }
    public void testRemoveServiceBindingCommitting() throws Exception {
	testCommitting(removeServiceBinding);
    }
    public void testRemoveBindingCommitted() throws Exception {
	testCommitted(removeBinding);
    }
    public void testRemoveServiceBindingCommitted() throws Exception {
	testCommitted(removeServiceBinding);
    }
    public void testRemoveBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeBinding);
    }
    public void testRemoveServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(removeServiceBinding);
    }
    public void testRemoveBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeBinding);
    }
    public void testRemoveServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeServiceBinding);
    }
    public void testRemoveBindingShutdown() throws Exception {
	testShutdown(removeBinding);
    }
    public void testRemoveServiceBindingShutdown() throws Exception {
	testShutdown(removeServiceBinding);
    }

    public void testRemoveBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(true);
    }
    public void testRemoveServiceBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(false);
    }
    private void testRemoveBindingRemovedObject(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	service.removeObject(dummy);
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	dummy = new DummyManagedObject();
	setBinding(app, service, "dummy", dummy);
	service.removeObject(dummy);
	txn.commit();
	createTransaction();
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingDeserializationFails() throws Exception {
	testRemoveBindingDeserializationFails(true);
    }
    public void testRemoveServiceBindingDeserializationFails()
	throws Exception
    {
	testRemoveBindingDeserializationFails(false);
    }
    private void testRemoveBindingDeserializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new DeserializationFails());
	txn.commit();
	createTransaction();
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy", DeserializationFails.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingSuccess() {
	testRemoveBindingSuccess(true);
    }
    public void testRemoveServiceBindingSuccess() {
	testRemoveBindingSuccess(false);
    }
    private void testRemoveBindingSuccess(boolean app) {
	setBinding(app, service, "dummy", dummy);
	removeBinding(app, service, "dummy");
	txn.abort(null);
	createTransaction();
	removeBinding(app, service, "dummy");	
	try {
	    removeBinding(app, service, "dummy");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingsDifferent() throws Exception {
	DummyManagedObject serviceDummy = new DummyManagedObject();
	service.setServiceBinding("dummy", serviceDummy);
	txn.commit();
	createTransaction();
	service.removeBinding("dummy");
	DummyManagedObject serviceResult =
	    service.getServiceBinding("dummy", DummyManagedObject.class);
	assertEquals(serviceDummy, serviceResult);
	txn.abort(null);
	createTransaction();
	service.removeServiceBinding("dummy");
	DummyManagedObject result =
	    service.getBinding("dummy", DummyManagedObject.class);
	assertEquals(dummy, result);
    }

    /* -- Test nextBoundName and nextServiceBoundName -- */

    public void testNextBoundNameNotFound() throws Exception {
	for (String name = null;
	     (name = service.nextBoundName(name)) != null; )
	{
	    service.removeBinding(name);
	}
	assertNull(service.nextBoundName(null));
	assertNull(service.nextBoundName(""));
	assertNull(service.nextBoundName("whatever"));
    }

    public void testNextBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(true);
    }
    public void testNextServiceBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(false);
    }
    private void testNextBoundNameEmpty(boolean app) throws Exception {
	try {
	    removeBinding(app, service, "");
	} catch (NameNotBoundException e) {
	}
	String forNull = nextBoundName(app, service, null);
	assertEquals(forNull, nextBoundName(app, service, ""));
	setBinding(app, service, "", dummy);
	assertEquals("", nextBoundName(app, service, null));
	assertEquals(forNull, nextBoundName(app, service, ""));
    }

    /* -- Unusual states -- */
    private final Action nextBoundName = new Action() {
	void run() { service.nextBoundName(null); }
    };
    private final Action nextServiceBoundName = new Action() {
	void run() { service.nextServiceBoundName(null); }
    };
    public void testNextBoundNameUninitialized() throws Exception {
	testUninitialized(nextBoundName);
    }
    public void testNextServiceBoundNameUninitialized() throws Exception {
	testUninitialized(nextServiceBoundName);
    }
    public void testNextBoundNameAborting() throws Exception {
	testAborting(nextBoundName);
    }
    public void testNextServiceBoundNameAborting() throws Exception {
	testAborting(nextServiceBoundName);
    }
    public void testNextBoundNameAborted() throws Exception {
	testAborted(nextBoundName);
    }
    public void testNextServiceBoundNameAborted() throws Exception {
	testAborted(nextServiceBoundName);
    }
    public void testNextBoundNamePreparing() throws Exception {
	testPreparing(nextBoundName);
    }
    public void testNextServiceBoundNamePreparing() throws Exception {
	testPreparing(nextServiceBoundName);
    }
    public void testNextBoundNameCommitting() throws Exception {
	testCommitting(nextBoundName);
    }
    public void testNextServiceBoundNameCommitting() throws Exception {
	testCommitting(nextServiceBoundName);
    }
    public void testNextBoundNameCommitted() throws Exception {
	testCommitted(nextBoundName);
    }
    public void testNextServiceBoundNameCommitted() throws Exception {
	testCommitted(nextServiceBoundName);
    }
    public void testNextBoundNameShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextBoundName);
    }
    public void testNextServiceBoundNameShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(nextServiceBoundName);
    }
    public void testNextBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextBoundName);
    }
    public void testNextServiceBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextServiceBoundName);
    }
    public void testNextBoundNameShutdown() throws Exception {
	testShutdown(nextBoundName);
    }
    public void testNextServiceBoundNameShutdown() throws Exception {
	testShutdown(nextServiceBoundName);
    }

    public void testNextBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(true);
    }
    public void testNextServiceBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(false);
    }
    private void testNextBoundNameSuccess(boolean app) throws Exception {
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
	txn.commit();
	createTransaction();
	removeBinding(app, service, "zzz-1");
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	txn.abort(null);
	createTransaction();
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
    }

    public void testNextBoundNameModify() throws Exception {
	testNextBoundNameModify(true);
    }
    public void testNextServiceBoundNameModify() throws Exception {
	testNextBoundNameModify(false);
    }
    private void testNextBoundNameModify(boolean app) throws Exception {
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
    }

    public void testNextBoundNameDifferent() throws Exception {
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
	while ((name = service.nextServiceBoundName(lastService)) != null) {
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
	assertEquals(nextService, service.nextServiceBoundName("a-service"));
	assertEquals(null, service.nextServiceBoundName(lastService));
    }

    /* -- Test removeObject -- */

    public void testRemoveObjectNull() {
	try {
	    service.removeObject(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.removeObject(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action removeObject = new Action() {
	void run() { service.removeObject(dummy); }
    };
    public void testRemoveObjectUninitialized() throws Exception {
	testUninitialized(removeObject);
    }
    public void testRemoveObjectAborting() throws Exception {
	testAborting(removeObject);
    }
    public void testRemoveObjectAborted() throws Exception {
	testAborted(removeObject);
    }
    public void testRemoveObjectPreparing() throws Exception {
	testPreparing(removeObject);
    }
    public void testRemoveObjectCommitting() throws Exception {
	testCommitting(removeObject);
    }
    public void testRemoveObjectCommitted() throws Exception {
	testCommitted(removeObject);
    }
    public void testRemoveObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeObject);
    }
    public void testRemoveObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeObject);
    }
    public void testRemoveObjectShutdown() throws Exception {
	testShutdown(removeObject);
    }

    public void testRemoveObjectSuccess() throws Exception {
	service.removeObject(dummy);
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectRemoved() throws Exception {
	service.removeObject(dummy);
	try {
	    service.removeObject(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectPreviousTxn() throws Exception {
	txn.commit();
	createTransaction();
	service.removeObject(dummy);
    }

    /* -- Test markForUpdate -- */

    public void testMarkForUpdateNull() {
	try {
	    service.markForUpdate(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.markForUpdate(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateRemoved() {
	service.removeObject(dummy);
	try {
	    service.markForUpdate(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action markForUpdate = new Action() {
	void run() { service.markForUpdate(dummy); }
    };
    public void testMarkForUpdateUninitialized() throws Exception {
	testUninitialized(markForUpdate);
    }
    public void testMarkForUpdateAborting() throws Exception {
	testAborting(markForUpdate);
    }
    public void testMarkForUpdateAborted() throws Exception {
	testAborted(markForUpdate);
    }
    public void testMarkForUpdatePreparing() throws Exception {
	testPreparing(markForUpdate);
    }
    public void testMarkForUpdateCommitting() throws Exception {
	testCommitting(markForUpdate);
    }
    public void testMarkForUpdateCommitted() throws Exception {
	testCommitted(markForUpdate);
    }
    public void testMarkForUpdateShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(markForUpdate);
    }
    public void testMarkForUpdateShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(markForUpdate);
    }
    public void testMarkForUpdateShutdown() throws Exception {
	testShutdown(markForUpdate);
    }

    public void testMarkForUpdateSuccess() throws Exception {
	service.markForUpdate(dummy);	    
	service.setBinding("dummy", dummy);
	dummy.setValue("a");
	txn.commit();
	service.setDetectModifications(false);
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	service.markForUpdate(dummy);
	dummy.value = "b";
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	assertEquals("b", dummy.value);
    }

    public void testMarkForUpdateLocking() throws Exception {
	dummy.setValue("a");
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	assertEquals("a", dummy.value);
	final Semaphore mainFlag = new Semaphore(0);
	final Semaphore threadFlag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		DummyTransaction txn2 =
		    new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
		try {
		    txnProxy.setCurrentTransaction(txn2);
		    DummyManagedObject dummy2 = service.getBinding(
			"dummy", DummyManagedObject.class);
		    assertEquals("a", dummy2.value);
		    threadFlag.release();
		    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
		    service.markForUpdate(dummy2);
		    threadFlag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		} finally {
		    txn2.abort(null);
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
    }

    /* -- Test createReference -- */

    public void testCreateReferenceNull() {
	try {
	    service.createReference(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.createReference(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action createReference = new Action() {
	void run() { service.createReference(dummy); }
    };
    public void testCreateReferenceUninitialized() throws Exception {
	testUninitialized(createReference);
    }
    public void testCreateReferenceAborting() throws Exception {
	testAborting(createReference);
    }
    public void testCreateReferenceAborted() throws Exception {
	testAborted(createReference);
    }
    public void testCreateReferencePreparing() throws Exception {
	testPreparing(createReference);
    }
    public void testCreateReferenceCommitting() throws Exception {
	testCommitting(createReference);
    }
    public void testCreateReferenceCommitted() throws Exception {
	testCommitted(createReference);
    }
    public void testCreateReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(createReference);
    }
    public void testCreateReferenceShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReference);
    }
    public void testCreateReferenceShutdown() throws Exception {
	testShutdown(createReference);
    }

    public void testCreateReferenceNew() {
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get(DummyManagedObject.class));
    }

    public void testCreateReferenceExisting() throws Exception {
	txn.commit();
	createTransaction();
	DummyManagedObject dummy =
	    service.getBinding("dummy", DummyManagedObject.class);
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get(DummyManagedObject.class));
    }

    public void testCreateReferenceSerializationFails() throws Exception {
	dummy.setNext(new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testCreateReferenceRemoved() throws Exception {
        service.createReference(dummy);
	service.removeObject(dummy);
	try {
	    service.createReference(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferencePreviousTxn() throws Exception {
	txn.commit();
	createTransaction();
	assertEquals(
	    dummy,
	    service.createReference(dummy).get(DummyManagedObject.class));
    }

    public void testCreateReferenceTwoObjects() throws Exception {
	DummyManagedObject x = new DummyManagedObject();
	DummyManagedObject y = new DummyManagedObject();
	assertFalse(
	    service.createReference(x).equals(service.createReference(y)));
    }

    /* -- Test createReferenceForId -- */

    public void testCreateReferenceForIdNullId() throws Exception {
	try {
	    service.createReferenceForId(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceForIdTooSmallId() throws Exception {
	BigInteger id = new BigInteger("-1");
	try {
	    service.createReferenceForId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	service.createReferenceForId(BigInteger.ZERO);
    }

    public void testCreateReferenceForIdTooBigId() throws Exception {
	BigInteger maxLong = new BigInteger(String.valueOf(Long.MAX_VALUE));
	BigInteger id = maxLong.add(BigInteger.ONE);
	try {
	    service.createReferenceForId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	service.createReferenceForId(maxLong);
    }

    /* -- Unusual states -- */
    private final Action createReferenceForId = new Action() {
	private BigInteger id;
	void setUp() { id = service.createReference(dummy).getId(); }
	void run() { service.createReferenceForId(id); }
    };
    public void testCreateReferenceForIdUninitialized() throws Exception {
	testUninitialized(createReferenceForId);
    }
    public void testCreateReferenceForIdAborting() throws Exception {
	testAborting(createReferenceForId);
    }
    public void testCreateReferenceForIdAborted() throws Exception {
	testAborted(createReferenceForId);
    }
    public void testCreateReferenceForIdPreparing() throws Exception {
	testPreparing(createReferenceForId);
    }
    public void testCreateReferenceForIdCommitting() throws Exception {
	testCommitting(createReferenceForId);
    }
    public void testCreateReferenceForIdCommitted() throws Exception {
	testCommitted(createReferenceForId);
    }
    public void testCreateReferenceForIdShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(createReferenceForId);
    }
    public void testCreateReferenceForIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReferenceForId);
    }
    public void testCreateReferenceForIdShutdown() throws Exception {
	testShutdown(createReferenceForId);
    }

    public void testCreateReferenceForIdSuccess() throws Exception {
	BigInteger id = service.createReference(dummy).getId();
	ManagedReference ref = service.createReferenceForId(id);
	assertSame(dummy, ref.get(DummyManagedObject.class));
	txn.commit();
	createTransaction();
	ref = service.createReferenceForId(id);
	dummy = ref.get(DummyManagedObject.class);
	assertSame(
	    dummy, service.getBinding("dummy", DummyManagedObject.class));
	service.removeObject(dummy);
	try {
	    ref.get(DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	ref = service.createReferenceForId(id);
	try {
	    ref.get(DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Test ManagedReference.get -- */

    public void testGetReferenceNullType() throws Exception {
	ManagedReference ref = service.createReference(dummy);
	try {
	    ref.get(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    public void testGetReferenceNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject());
	service.removeObject(dummy.getNext());
	try {
	    dummy.getNext();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNext();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action getReference = new Action() {
	private ManagedReference ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.get(DummyManagedObject.class); }
    };
    /* Can't get a reference when the service is uninitialized */
    public void testGetReferenceAborting() throws Exception {
	testAborting(getReference);
    }
    public void testGetReferenceAborted() throws Exception {
	testAborted(getReference);
    }
    public void testGetReferencePreparing() throws Exception {
	testPreparing(getReference);
    }
    public void testGetReferenceCommitting() throws Exception {
	testCommitting(getReference);
    }
    public void testGetReferenceCommitted() throws Exception {
	testCommitted(getReference);
    }
    public void testGetReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getReference);
    }
    /* Can't get a reference as the first operation in a new transaction */
    public void testGetReferenceShutdown() throws Exception {
	/* Expect TransactionNotActiveException */
	getReference.setUp();
	txn.abort(null);
	service.shutdown();
	try {
	    getReference.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	txn = null;
	service = null;
    }

    public void testGetReferenceDeserializationFails() throws Exception {
	dummy.setNext(new DeserializationFails());
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNext();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceDeserializeAsNull() throws Exception {
	dummy.setNext(new DeserializeAsNull());
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNext();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceOldTxn() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction();
	try {
	    dummy.getNext();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* -- Test ManagedReference.getForUpdate -- */

    public void testGetReferenceUpdateNullType() throws Exception {
	ManagedReference ref = service.createReference(dummy);
	try {
	    ref.getForUpdate(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    public void testGetReferenceUpdateNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject());
	service.removeObject(dummy.getNext());
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceForUpdateMaybeModified() throws Exception {
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	service.createReference(dummy).getForUpdate(DummyManagedObject.class);
	dummy.value = "B";
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	assertEquals("B", dummy.value);
    }

    public void testGetReferenceUpdateSuccess() throws Exception {
	DummyManagedObject dummy2 = new DummyManagedObject();
	dummy2.setValue("A");
	dummy.setNext(dummy2);
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	dummy2 = dummy.getNextForUpdate();
	dummy2.value = "B";
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	dummy2 = dummy.getNext();
	assertEquals("B", dummy2.value);
    }

    /* -- Unusual states -- */
    private final Action getReferenceUpdate = new Action() {
	private ManagedReference ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.getForUpdate(DummyManagedObject.class); }
    };
    /* Can't get a referenceUpdate when the service is uninitialized */
    public void testGetReferenceUpdateAborting() throws Exception {
	testAborting(getReferenceUpdate);
    }
    public void testGetReferenceUpdateAborted() throws Exception {
	testAborted(getReferenceUpdate);
    }
    public void testGetReferenceUpdatePreparing() throws Exception {
	testPreparing(getReferenceUpdate);
    }
    public void testGetReferenceUpdateCommitting() throws Exception {
	testCommitting(getReferenceUpdate);
    }
    public void testGetReferenceUpdateCommitted() throws Exception {
	testCommitted(getReferenceUpdate);
    }
    public void testGetReferenceUpdateShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getReferenceUpdate);
    }
    /* Can't get a reference as the first operation in a new transaction */
    public void testGetReferenceUpdateShutdown() throws Exception {
	/* Expect TransactionNotActiveException */
	getReferenceUpdate.setUp();
	txn.abort(null);
	service.shutdown();
	try {
	    getReferenceUpdate.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	txn = null;
	service = null;
    }

    public void testGetReferenceUpdateDeserializationFails() throws Exception {
	dummy.setNext(new DeserializationFails());
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceUpdateDeserializeAsNull() throws Exception {
	dummy.setNext(new DeserializeAsNull());
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceUpdateOldTxn() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction();
	try {
	    dummy.getNextForUpdate();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceUpdateLocking() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	dummy.getNext();
	final Semaphore mainFlag = new Semaphore(0);
	final Semaphore threadFlag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		DummyTransaction txn2 =
		    new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
		try {
		    txnProxy.setCurrentTransaction(txn2);
		    DummyManagedObject dummy2 = service.getBinding(
			"dummy", DummyManagedObject.class);
		    threadFlag.release();
		    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
		    dummy2.getNextForUpdate();
		    threadFlag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		} finally {
		    txn2.abort(null);
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
    }

    /* -- Test ManagedReference.getId -- */

    public void testReferenceGetId() throws Exception {
	BigInteger id = service.createReference(dummy).getId();
	DummyManagedObject dummy2 = new DummyManagedObject();
	service.setBinding("dummy2", dummy2);
	BigInteger id2 = service.createReference(dummy2).getId();
	assertFalse(id.equals(id2));
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	ManagedReference ref = service.createReference(dummy);
	assertEquals(id, ref.getId());
	dummy2 = service.getBinding("dummy2", DummyManagedObject.class);
	assertEquals(id2, service.createReference(dummy2).getId());
    }

    /* -- Test ManagedReference.equals -- */

    public void testReferenceEquals() throws Exception {
	ManagedReference ref = service.createReference(dummy);
	assertFalse(ref.equals(null));
	assertTrue(ref.equals(ref));
	DummyManagedObject dummy2 = new DummyManagedObject();
	ManagedReference ref2 = service.createReference(dummy2);
	assertFalse(ref.equals(ref2));
	ManagedReference ref3 = new ManagedReference() {
	    public <T> T get(Class<T> type) { return null; }
	    public <T> T getForUpdate(Class<T> type) { return null; }
	    public BigInteger getId() { return null; }
	};
	assertFalse(ref.equals(ref3));
    }

    /* -- Test shutdown -- */

    public void testShutdownAgain() throws Exception {
	txn.abort(null);
	txn = null;
	service.shutdown();
	ShutdownAction action = new ShutdownAction();
	try {
	    action.waitForDone();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	service = null;
    }

    public void testShutdownInterrupt() throws Exception {
	ShutdownAction action = new ShutdownAction();
	action.assertBlocked();
	action.interrupt();
	action.assertResult(false);
	service.setBinding("dummy", new DummyManagedObject());
	txn.commit();
	txn = null;
    }

    public void testConcurrentShutdownInterrupt() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	action1.interrupt();
	action1.assertResult(false);
	action2.assertBlocked();
	txn.abort(null);
	action2.assertResult(true);
	txn = null;
	service = null;
    }

    public void testConcurrentShutdownRace() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	txn.abort(null);
	boolean result1;
	try {
	    result1 = action1.waitForDone();
	} catch (IllegalStateException e) {
	    result1 = false;
	}
	boolean result2;
	try {
	    result2 = action2.waitForDone();
	} catch (IllegalStateException e) {
	    result2 = false;
	}
	assertTrue(result1 || result2);
	assertFalse(result1 && result2);
	txn = null;
	service = null;
    }

    public void testShutdownRestart() throws Exception {
	txn.commit();
	service.shutdown();
	service = createDataServiceImpl();
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	txn.commit();
	createTransaction();
	assertEquals(
	    dummy, service.getBinding("dummy", DummyManagedObject.class));
	service = null;
    }

    /* -- Other tests -- */

    public void testCommitNoStoreParticipant() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.commit();
	txn = null;
    }

    public void testAbortNoStoreParticipant() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.abort(null);
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.abort(null);
	txn = null;
    }

    public void testCommitReadOnly() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy", DummyManagedObject.class);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy", DummyManagedObject.class);
	txn.commit();
	createTransaction();
	service.getBinding("dummy", DummyManagedObject.class);
    }

    public void testAbortReadOnly() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy", DummyManagedObject.class);
	txn.abort(null);
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy", DummyManagedObject.class);
	txn.abort(null);
	createTransaction();
	service.getBinding("dummy", DummyManagedObject.class);
    }

    public void testContentEquals() throws Exception {
	service.setBinding("a", new ContentEquals(3));
	service.setBinding("b", new ContentEquals(3));
	txn.commit();
	createTransaction();
	assertNotSame(service.getBinding("a", ContentEquals.class),
		      service.getBinding("b", ContentEquals.class));
    }

    public void testSerializeReferenceToEnclosing() throws Exception {
	service.setBinding("a", NonManaged.staticLocal);
	service.setBinding("b", NonManaged.staticAnonymous);
	service.setBinding("c", new NonManaged().createMember());
	service.setBinding("d", new NonManaged().createInner());
	service.setBinding("e", new NonManaged().createAnonymous());
	service.setBinding("f", new NonManaged().createLocal());
	txn.commit();
	createTransaction();
	service.setBinding("a", Managed.staticLocal);
	service.setBinding("b", Managed.staticAnonymous);
	service.setBinding("c", new NonManaged().createMember());
	txn.commit();
	createTransaction();
	service.setBinding("a", new Managed().createInner());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	createTransaction();
	service.setBinding("b", new Managed().createAnonymous());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	createTransaction();
	service.setBinding("b", new Managed().createLocal());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    static class NonManaged implements Serializable {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal implements ManagedObject, Serializable {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObject() {
	        private static final long serialVersionUID = 1L;
	    };
	static class Member implements ManagedObject, Serializable {
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner implements ManagedObject, Serializable {
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
	}
	ManagedObject createAnonymous() {
	    return new DummyManagedObject() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local implements ManagedObject, Serializable {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    static class Managed implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal implements ManagedObject, Serializable {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObject() {
                private static final long serialVersionUID = 1L;
            };
	static class Member implements ManagedObject, Serializable {
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner implements ManagedObject, Serializable {
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
        }
	ManagedObject createAnonymous() {
	    return new DummyManagedObject() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local implements ManagedObject, Serializable {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    public void testDeadlock() throws Exception {
	service.setBinding("dummy2", new DummyManagedObject());
	txn.commit();
	for (int i = 0; i < 5; i++) {
	    createTransaction();
	    dummy = service.getBinding("dummy", DummyManagedObject.class);
	    final Semaphore flag = new Semaphore(1);
	    flag.acquire();
	    final int finalI = i;
	    class MyRunnable implements Runnable {
		Exception exception2;
		public void run() {
		    DummyTransaction txn2 = null;
		    try {
			txn2 = new DummyTransaction(
			    UsePrepareAndCommit.ARBITRARY);
			txnProxy.setCurrentTransaction(txn2);
			componentRegistry.registerAppContext();
			service.getBinding("dummy2", DummyManagedObject.class);
			flag.release();
			service.getBinding("dummy", DummyManagedObject.class)
			    .setValue(finalI);
			System.err.println(finalI + " txn2: commit");
			txn2.commit();
		    } catch (TransactionAbortedException e) {
			System.err.println(finalI + " txn2 (" + txn2 + "): " + e);
			exception2 = e;
		    } catch (Exception e) {
			System.err.println(finalI + " txn2 (" + txn2 + "): " + e);
			exception2 = e;
			if (txn2 != null) {
			    txn2.abort(null);
			}
		    }
		}
	    }
	    MyRunnable myRunnable = new MyRunnable();
	    Thread thread = new Thread(myRunnable);
	    thread.start();
	    Thread.sleep(i * 500);
	    flag.acquire();
	    TransactionAbortedException exception = null;
	    try {
		service.getBinding("dummy2", DummyManagedObject.class)
		    .setValue(i);
		System.err.println(i + " txn1 (" + txn + "): commit");
		txn.commit();
	    } catch (TransactionAbortedException e) {
		System.err.println(i + " txn1 (" + txn + "): " + e);
		exception = e;
	    }
	    thread.join();
	    if (myRunnable.exception2 != null &&
		!(myRunnable.exception2
		  instanceof TransactionAbortedException))
	    {
		throw myRunnable.exception2;
	    } else if (exception == null && myRunnable.exception2 == null) {
		fail("Expected TransactionAbortedException");
	    }
	    txn = null;
	}
    }

    public void testModifiedNotSerializable() throws Exception {
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	dummy.value = Thread.currentThread();
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testNotSerializableAfterDeserialize() throws Exception {
	dummy.value = new SerializationFailsAfterDeserialize();
	txn.commit();
	createTransaction();
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    /* -- App and service binding methods -- */

    <T> T getBinding(
	boolean app, DataService service, String name, Class<T> type)
    {
	return app ? service.getBinding(name, type)
	    : service.getServiceBinding(name, type);
    }

    void setBinding(
	boolean app, DataService service, String name, ManagedObject object)
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
    String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	return dir.getPath();
    }

    /** Insures an empty version of the directory exists. */
    private static void cleanDirectory(String directory) {
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
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
    }

    /** Creates a property list with the specified keys and values. */
    static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /**
     * Returns a DataServiceImpl for the shared database using the default
     * properties and the default component registry.
     */
    protected DataServiceImpl createDataServiceImpl() throws Exception {
	return createDataServiceImpl(props, componentRegistry);
    }

    /**
     * Returns a DataServiceImpl for the shared database using the specified
     * properties and component registry.
     */
    protected DataServiceImpl createDataServiceImpl(
	Properties props, ComponentRegistry componentRegistry)
	throws Exception
    {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(props, componentRegistry);
    }

    /** Returns the default properties to use for creating data services. */
    protected Properties getProperties() throws Exception {
	return createProperties(
	    DataStoreImplClassName + ".directory", dbDirectory,
	    StandardProperties.APP_NAME, "TestDataServiceImpl",
	    DataServiceImplClassName + ".debug.check.interval", "0");
    }

    /** Creates a new transaction. */
    DummyTransaction createTransaction() {
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	txnProxy.setCurrentTransaction(txn);
	return txn;
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

    /** A managed object that deserializes as null. */
    static class DeserializeAsNull extends DummyManagedObject {
        private static final long serialVersionUID = 1L;
	private Object readResolve() throws ObjectStreamException {
	    return null;
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

    /** Tests running the action with an uninitialized service. */
    void testUninitialized(Action action) throws Exception {
	action.setUp();
	txn.commit();
	createTransaction();
	service = createDataServiceImpl();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	service = null;
    }

    /** Tests running the action while aborting. */
    void testAborting(final Action action) {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
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
	txn.join(participant);
	txn.abort(null);
	txn = null;
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action after abort. */
    private void testAborted(Action action) {
	action.setUp();
	txn.abort(null);
	try {
	    action.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /** Tests running the action while preparing. */
    private void testPreparing(final Action action) throws Exception {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
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
	Participant participant = new Participant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action while committing. */
    private void testCommitting(final Action action) throws Exception {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
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
	Participant participant = new Participant();
	txn.join(participant);
	txn.commit();
	txn = null;
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action after commit. */
    private void testCommitted(Action action) throws Exception {
	action.setUp();
	txn.commit();
	try {
	    action.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /**
     * Tests running the action with an existing transaction while shutting
     * down.
     */
    private void testShuttingDownExistingTxn(Action action) throws Exception {
	action.setUp();
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	action.run();
	if (txn != null) {
	    txn.commit();
	    txn = null;
	}
	shutdownAction.assertResult(true);
	service = null;
    }

    /** Tests running the action with a new transaction while shutting down. */
    private void testShuttingDownNewTxn(Action action) throws Exception {
	action.setUp();
	DummyTransaction originalTxn = txn;
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	createTransaction();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	txn.abort(null);
	txn = null;
	originalTxn.abort(null);
	shutdownAction.assertResult(true);
	service = null;
    }

    /** Tests running the action after shutdown. */
    void testShutdown(Action action) {
	action.setUp();
	txn.abort(null);
	service.shutdown();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	txn = null;
	service = null;
    }

    /** Use this thread to control a call to shutdown that may block. */
    class ShutdownAction extends Thread {
	private boolean done;
	private Throwable exception;
	private boolean result;

	/** Creates an instance of this class and starts the thread. */
	ShutdownAction() {
	    start();
	}

	/** Performs the shutdown and collects the results. */
	public void run() {
	    try {
		result = service.shutdown();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	/** Asserts that the shutdown call is blocked. */
	synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(5);
	    assertEquals("Expected no exception", null, exception);
	    assertFalse("Expected shutdown to be blocked", done);
	}
	
	/** Waits a while for the shutdown call to complete. */
	synchronized boolean waitForDone() throws Exception {
	    waitForDoneInternal();
	    if (!done) {
		return false;
	    } else if (exception == null) {
		return result;
	    } else if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
	}

	/**
	 * Asserts that the shutdown call has completed with the specified
	 * result.
	 */
	synchronized void assertResult(boolean expectedResult)
	    throws InterruptedException
	{
	    waitForDoneInternal();
	    assertTrue("Expected shutdown to be done", done);
	    assertEquals("Unexpected result", expectedResult, result);
	    assertEquals("Expected no exception", null, exception);
	}

	/** Wait until done, but give up after a while. */
	private synchronized void waitForDoneInternal()
	    throws InterruptedException
	{
	    long wait = 2000;
	    long start = System.currentTimeMillis();
	    while (!done && wait > 0) {
		wait(wait);
		long now = System.currentTimeMillis();
		wait -= (now - start);
		start = now;
	    }
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
    }
}
