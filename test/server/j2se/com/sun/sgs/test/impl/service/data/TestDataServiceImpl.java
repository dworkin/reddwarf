package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
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
import junit.framework.TestSuite;

/** Test the DataServiceImpl class */
public class TestDataServiceImpl extends TestCase {

    /** The test suite, to use for adding additional tests. */
    private static final TestSuite suite =
	new TestSuite(TestDataServiceImpl.class);

    /** Provides the test suite to the test runner. */
    public static Test suite() { return suite; }

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataServiceImpl.db";

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory,
	"com.sun.sgs.appName", "TestDataServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "0");

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    String directory;

    /** A transaction proxy. */
    DummyTransactionProxy txnProxy = new DummyTransactionProxy();

    /** A component registry. */
    DummyComponentRegistry componentRegistry = new DummyComponentRegistry();

    /** An initial, open transaction. */
    DummyTransaction txn;

    /** An instance of the data service, to test. */
    DataServiceImpl service;

    /** A managed object. */
    DummyManagedObject dummy;

    /** Creates the test. */
    public TestDataServiceImpl(String name) {
	super(name);
    }

    /**
     * Prints the test case, initializes the data service, and creates and
     * binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	createTransaction();
	service = getDataServiceImpl();
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	componentRegistry.registerAppContext();
	txn.commit();
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
     * Deletes the directory if the test passes and the directory was
     * created, and aborts the current transaction.
     */
    protected void tearDown() throws Exception {
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
	if (txn != null) {
	    txn.abort();
	    txn = null;
	}
    }

    /* -- Test constructor -- */

    public void testConstructorNullArgs() {
	try {
	    new DataServiceImpl(null, componentRegistry);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    new DataServiceImpl(dbProps, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", createDirectory());
	try {
	    new DataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadDebugCheckInterval() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "Foo",
	    DataServiceImplClassName + ".debugCheckInterval", "gorp");
	try {
	    new DataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoDirectory() throws Exception {
	Properties props = createProperties("com.sun.sgs.appName", "Foo");
	try {
	    new DataServiceImpl(props, componentRegistry);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getName -- */

    public void testGetName() {
	assertNotNull(service.getName());
    }

    /* -- Test configure -- */

    public void testConfigureNullArgs() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "Foo");
	service = new DataServiceImpl(props, componentRegistry);
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
    }

    public void testConfigureNoTxn() throws Exception {
	txn.commit();
	txn = null;
	service = getDataServiceImpl();
	try {
	    service.configure(componentRegistry, txnProxy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureAgain() throws Exception {
	try {
	    service.configure(componentRegistry, txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureAborted() throws Exception {
	txn.commit();
	service = getDataServiceImpl();
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	txn.abort();
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	txn.commit();
	txn = null;
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

    private static class TestGetBindingBadTxn extends BadTxnTest {
	private final boolean app;
	private TestGetBindingBadTxn(boolean app, BadTxnState state) {
	    super(app ? "testGetBinding" : "testGetServiceBinding", state);
	    this.app = app;
	}
	void action() {
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestGetBindingBadTxn(true, state);
	    new TestGetBindingBadTxn(false, state);
	}
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

    private static class TestSetBindingBadTxn extends BadTxnTest {
	private final boolean app;
	private TestSetBindingBadTxn(boolean app, BadTxnState state) {
	    super(app ? "testSetBinding" : "testSetServiceBinding", state);
	    this.app = app;
	}
	void action() {
	    setBinding(app, service, "dummy", dummy);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestSetBindingBadTxn(true, state);
	    new TestSetBindingBadTxn(false, state);
	}
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
	    System.err.println(e);
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
    public void testSetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	txn.commit();
	createTransaction();
	assertEquals(
	    dummy,
	    getBinding(app, service, "dummy", DummyManagedObject.class));
	DummyManagedObject dummy2 = new DummyManagedObject();
	setBinding(app, service, "dummy", dummy2);
	txn.abort();
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
	testRemoveBindingNullName(true);
    }
    public void testRemoveServiceBindingEmptyName() {
	testRemoveBindingNullName(false);
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

    private static class TestRemoveBindingBadTxn extends BadTxnTest {
	private final boolean app;
	private TestRemoveBindingBadTxn(boolean app, BadTxnState state) {
	    super(app ? "testRemoveBinding" : "testRemoveServiceBinding",
		  state);
	    this.app = app;
	}
	void action() {
	    removeBinding(app, service, "dummy");
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestRemoveBindingBadTxn(true, state);
	    new TestRemoveBindingBadTxn(false, state);
	}
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
	txn.abort();
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
	txn.abort();
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

    private static class TestNextBoundNameBadTxn extends BadTxnTest {
	private final boolean app;
	private TestNextBoundNameBadTxn(boolean app, BadTxnState state) {
	    super(app ? "testNextBoundName" : "testNextServiceBoundName",
		  state);
	    this.app = app;
	}
	void action() {
	    nextBoundName(app, service, null);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestNextBoundNameBadTxn(true, state);
	    new TestNextBoundNameBadTxn(false, state);
	}
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
	txn.abort();
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

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testRemoveObject", state) {
		void action() {
		    service.removeObject(dummy);
		}
	    };
	}
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

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testMarkForUpdate", state) {
		void action() {
		    service.markForUpdate(dummy);
		}
	    };
	}
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
		    txn2.abort();
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(1, TimeUnit.SECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(1, TimeUnit.SECONDS));	
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(1, TimeUnit.SECONDS));
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

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testCreateReference", state) {
		void action() {
		    service.createReference(dummy);
		}
	    };
	}
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
	ManagedReference ref = service.createReference(dummy);
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

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    /* Can't get a reference when the service is not initialized */
	    if (state != BadTxnState.Uninitialized) {
		new BadTxnTest("testGetReference", state) {
		    private ManagedReference ref;
		    protected void setUp() throws Exception {
			super.setUp();
			ref = service.createReference(dummy);
		    }
		    void action() {
			ref.get(DummyManagedObject.class);
		    }
		};
	    }
	}
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

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    /* Can't get a reference when the service is not initialized */
	    if (state != BadTxnState.Uninitialized) {
		new BadTxnTest("testGetReferenceUpdate", state) {
		    private ManagedReference ref;
		    protected void setUp() throws Exception {
			super.setUp();
			ref = service.createReference(dummy);
		    }
		    void action() {
			ref.getForUpdate(DummyManagedObject.class);
		    }
		};
	    }
	}
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
		    txn2.abort();
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(1, TimeUnit.SECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(1, TimeUnit.SECONDS));	
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(1, TimeUnit.SECONDS));
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
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.abort();
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
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy", DummyManagedObject.class);
	txn.abort();
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

    /** Creates a per-test directory. */
    String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	directory = dir.getPath();
	return directory;
    }

    /** Deletes the specified directory, if it exists. */
    static void deleteDirectory(String directory) {
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

    /** Returns a DataServiceImpl for the shared database. */
    DataServiceImpl getDataServiceImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, componentRegistry);
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
	private void writeObject(ObjectOutputStream out)
	    throws IOException
	{
	    throw new IOException("Serialization fails");
	}
    }

    /** A managed object that fails during deserialization. */
    static class DeserializationFails extends DummyManagedObject {
	private void readObject(ObjectInputStream in)
	    throws IOException
	{
	    throw new IOException("Deserialization fails");
	}
    }

    /** A managed object that deserializes as null. */
    static class DeserializeAsNull extends DummyManagedObject {
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

    /** The set of bad transaction states */
    static enum BadTxnState {
	Uninitialized, Aborting, Aborted, Preparing, Committing, Committed
    };

    /** Defines a abstract class for testing bad transaction states. */
    static abstract class BadTxnTest extends TestDataServiceImpl {

	/** The bad state to test. */
	private final BadTxnState state;

	/**
	 * Creates an instance with the specified generic name to test the
	 * specified bad transaction state, and adds this test to the test
	 * suite.
	 */
	BadTxnTest(String name, BadTxnState state) {
	    super(name + state);
	    this.state = state;
	    suite.addTest(this);
	}

	/**
	 * Subclasses should implement this method to define the action that
	 * should be tested in a bad transaction state.
	 */
	abstract void action();

	/** Runs the test for the bad transaction state. */
	protected void runTest() throws Exception {
	    switch (state) {
	    case Uninitialized:
		uninitializedTest();
		break;
	    case Aborting:
		abortingTest();
		break;
	    case Aborted:
		abortedTest();
		break;
	    case Preparing:
		preparingTest();
		break;
	    case Committing:
		committingTest();
		break;
	    case Committed:
		committedTest();
		break;
	    default:
		throw new AssertionError();
	    }
	}

	/** Runs the test for the uninitialized case. */
	void uninitializedTest() throws Exception {
	    txn.commit();
	    createTransaction();
	    service = getDataServiceImpl();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    }
	}

	/** Runs the test for the aborting case. */
	void abortingTest() {
	    class Participant extends DummyTransactionParticipant {
		boolean ok;
		public void abort(Transaction txn) {
		    try {
			action();
		    } catch (TransactionNotActiveException e) {
			ok = true;
			throw e;
		    }
		}
	    }
	    Participant participant = new Participant();
	    txn.join(participant);
	    txn.abort();
	    txn = null;
	    assertTrue("Action should throw", participant.ok);
	}

	/** Runs the test for the aborted case. */
	private void abortedTest() {
	    txn.abort();
	    try {
		action();
		fail("Expected TransactionNotActiveException");
	    } catch (TransactionNotActiveException e) {
		System.err.println(e);
	    } finally {
		txn = null;
	    }
	}

	/** Runs the test for the preparing case. */
	private void preparingTest() throws Exception {
	    class Participant extends DummyTransactionParticipant {
		boolean ok;
		public boolean prepare(Transaction txn) throws Exception {
		    try {
			action();
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

	/** Runs the test for the committing case. */
	private void committingTest() throws Exception {
	    class Participant extends DummyTransactionParticipant {
		boolean ok;
		public void commit(Transaction txn) {
		    try {
			action();
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

	/** Runs the test for the committed case. */
	private void committedTest() throws Exception {
	    txn.commit();
	    try {
		action();
		fail("Expected TransactionNotActiveException");
	    } catch (TransactionNotActiveException e) {
		System.err.println(e);
	    } finally {
		txn = null;
	    }
	}
    }
}
