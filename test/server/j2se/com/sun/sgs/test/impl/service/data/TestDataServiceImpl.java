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
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
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

    /** The name of the DataServiceImpl class. */
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
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	dbDirectory,
	"com.sun.sgs.appName", "TestDataServiceImpl",
	DataServiceImplClassName + ".debugCheckInterval", "1");

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    String directory;

    DataServiceImpl service;

    DummyTransactionProxy txnProxy;

    DummyTransaction txn;

    DummyManagedObject dummy;

    /** Creates the test. */
    public TestDataServiceImpl(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	service = getDataServiceImpl();
	txnProxy = new DummyTransactionProxy();
	service.configure(new DummyComponentRegistry(), txnProxy);
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Deletes the directory if the test passes and the directory was
     * created.
     */
    protected void tearDown() throws Exception {
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
	if (txn != null) {
	    try {
		txn.abort();
	    } catch (IllegalStateException e) {
	    }
	    txn = null;
	}
    }

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
	return new DataServiceImpl(dbProps);
    }

    DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /* -- Test constructor -- */

    public void testConstructorNullArg() {
	try {
	    new DataServiceImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory",
	    createDirectory());
	try {
	    new DataServiceImpl(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test configure -- */

    public void testConfigureNullArgs() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "Foo");
	DataService service = new DataServiceImpl(props);
	ComponentRegistry components = new DummyComponentRegistry();
	try {
	    service.configure(null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    service.configure(components, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConfigureAgain() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "Foo");
	DataService service = new DataServiceImpl(props);
	ComponentRegistry components = new DummyComponentRegistry();
	service.configure(components, txnProxy);
	try {
	    service.configure(components, txnProxy);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getBinding and getServiceBinding -- */

    public void testGetBindingNullName() {
	testGetBindingNullName(true);
    }
    public void testGetServiceBindingNullName() {
	testGetBindingNullName(false);
    }
    private void testGetBindingNullName(boolean app) {
	try {
	    getBinding(app, service, null, ManagedObject.class);
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

    public void testGetBindingNullType() throws Exception {
	testGetBindingNullType(true);
    }
    public void testGetServiceBindingNullType() throws Exception {
	testGetBindingNullType(false);
    }
    private void testGetBindingNullType(boolean app) throws Exception {
	try {
	    getBinding(app, service, "dummy", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
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
	/* Binding removed in this transaction */
	setBinding(app, service, "testGetBindingNotFound",
		   new DummyManagedObject(service, "dummy"));
	removeBinding(app, service, "testGetBindingNotFound");
	try {
	    getBinding(app, service, "testGetBindingNotFound",
		       ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* Binding removed in last transaction */
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
		   new DummyManagedObject(service, "dummy"));
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

    public void testGetBindingSuccess() throws Exception {
	testGetBindingSuccess(true);
    }
    public void testGetServiceBindingSuccess() throws Exception {
	testGetBindingSuccess(false);
    }
    private void testGetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "newDummy", dummy);
	DummyManagedObject result =
	    getBinding(app, service, "newDummy", DummyManagedObject.class);
	assertEquals(dummy, result);
	txn.commit();
	createTransaction();
	result = getBinding(
	    app, service, "newDummy", DummyManagedObject.class);
	assertEquals(dummy, result);
    }

    public void testGetBindingsDifferent() throws Exception {
	DummyManagedObject appDummy =
	    new DummyManagedObject(service, "appDummy");
	DummyManagedObject serviceDummy =
	    new DummyManagedObject(service, "serviceDummy");
	service.setBinding("dummy", appDummy);
	service.setServiceBinding("dummy", serviceDummy);
	txn.commit();
	createTransaction();
	DummyManagedObject appResult =
	    service.getBinding("dummy", DummyManagedObject.class);
	assertEquals(appDummy, appResult);
	DummyManagedObject serviceResult =
	    service.getServiceBinding("dummy", DummyManagedObject.class);
	assertEquals(serviceDummy, serviceResult);
    }

    /* -- Test setBinding and setServiceBinding -- */

    public void testSetBindingNullName() {
	testSetBindingNullName(true);
    }
    public void testSetServiceBindingNullName() {
	testSetBindingNullName(false);
    }
    private void testSetBindingNullName(boolean app) {
	try {
	    setBinding(app, service, null, dummy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNullObject() throws Exception {
	testSetBindingNullObject(true);
    }
    public void testSetServiceBindingNullObject() throws Exception {
	testSetBindingNullObject(false);
    }
    private void testSetBindingNullObject(boolean app) throws Exception {
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
	try {
	    setBinding(app, service, "dummy", new ManagedObject() { });
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNoReference() throws Exception {
	testSetBindingNoReference(true);
    }
    public void testSetServiceBindingNoReference() throws Exception {
	testSetBindingNoReference(false);
    }
    private void testSetBindingNoReference(boolean app) throws Exception {
	DummyManagedObject dummy = new AnotherManagedObject(
	    service, new DummyManagedObject(service, "dummy"));
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
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
	setBinding(app, service, "", new DummyManagedObject(service, "dummy"));
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
	setBinding(app, service, "dummy", dummy);
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

    public void testRemoveBindingsDifferent() throws Exception {
	DummyManagedObject appDummy =
	    new DummyManagedObject(service, "appDummy");
	DummyManagedObject serviceDummy =
	    new DummyManagedObject(service, "serviceDummy");
	service.setBinding("dummy", appDummy);
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
	DummyManagedObject appResult =
	    service.getBinding("dummy", DummyManagedObject.class);
	assertEquals(appDummy, appResult);
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

    private static class TestRemoveObjectBadTxn extends BadTxnTest {
	TestRemoveObjectBadTxn(BadTxnState state) {
	    super("testRemoveObject", state);
	}
	void action() {
	    service.removeObject(dummy);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestRemoveObjectBadTxn(state);
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
	service.removeObject(dummy);
	service.setBinding("dummy", dummy);
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	service.removeObject(dummy);
	service.removeObject(dummy);
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

    private static class TestMarkForUpdateBadTxn extends BadTxnTest {
	TestMarkForUpdateBadTxn(BadTxnState state) {
	    super("testMarkForUpdate", state);
	}
	void action() {
	    service.markForUpdate(dummy);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestMarkForUpdateBadTxn(state);
	}
    }

    public void testMarkForUpdateSuccess() throws Exception {
	service.setBinding(
	    "dummy", new AnotherManagedObject(service, "a"));
	txn.commit();
	service.setDetectModifications(false);
	createTransaction();
	AnotherManagedObject dummy =
	    service.getBinding("dummy", AnotherManagedObject.class);
	service.markForUpdate(dummy);
	dummy.object = "b";
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", AnotherManagedObject.class);
	assertEquals("b", dummy.object);
    }

    public void testMarkForUpdateLocking() throws Exception {
	service.setBinding(
	    "dummy", new AnotherManagedObject(service, "a"));
	txn.commit();
	createTransaction();
	AnotherManagedObject dummy =
	    service.getBinding("dummy", AnotherManagedObject.class);
	assertEquals("a", dummy.object);
	final Semaphore mainFlag = new Semaphore(0);
	final Semaphore threadFlag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		try {
		    DummyTransaction txn2 = new DummyTransaction();
		    txnProxy.setCurrentTransaction(txn2);
		    AnotherManagedObject dummy2 = service.getBinding(
			"dummy", AnotherManagedObject.class);
		    assertEquals("a", dummy2.object);		
		    threadFlag.release();
		    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
		    service.markForUpdate(dummy2);
		    threadFlag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(1, TimeUnit.SECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(1, TimeUnit.SECONDS));	
	txn.commit();
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

    private static class TestCreateReferenceBadTxn extends BadTxnTest {
	TestCreateReferenceBadTxn(BadTxnState state) {
	    super("testCreateReference", state);
	}

	void action() {
	    service.createReference(dummy);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestCreateReferenceBadTxn(state);
	}
    }

    public void testCreateReferenceNew() {
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceExisting() throws Exception {
	txn.commit();
	createTransaction();
	DummyManagedObject dummy =
	    service.getBinding("dummy", DummyManagedObject.class);
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceRemoved() throws Exception {
	service.removeObject(dummy);
	assertEquals(dummy, service.createReference(dummy).get());
    }

    public void testCreateReferencePreviousTxn() throws Exception {
	txn.commit();
	createTransaction();
	assertEquals(dummy, service.createReference(dummy).get());
    }

    public void testCreateReferenceTwoObjects() throws Exception {
	DummyManagedObject x = new DummyManagedObject(service, "x");
	DummyManagedObject y = new DummyManagedObject(service, "y");
	assertFalse(
	    service.createReference(x).equals(service.createReference(y)));
    }

    /* -- Test ManagedReference.get -- */

    public void testGetReferenceNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject(service, "dummy2"));
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

    private static class TestGetReferenceBadTxn extends BadTxnTest {
	private ManagedReference<DummyManagedObject> ref;

	TestGetReferenceBadTxn(BadTxnState state) {
	    super("testGetReference", state);
	}

	protected void setUp() {
	    super.setUp();
	    ref = service.createReference(dummy);
	}

	void action() {
	    ref.get();
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestGetReferenceBadTxn(state);
	}
    }

    /* -- Test ManagedReference.getForUpdate -- */

    public void testGetReferenceUpdateNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject(service, "dummy2"));
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

    public void testGetReferenceUpdateSuccess() throws Exception {
	dummy.setNext(new AnotherManagedObject(service, "A"));
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	AnotherManagedObject amo =
	    (AnotherManagedObject) dummy.getNextForUpdate();
	amo.object = "B";
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	amo = (AnotherManagedObject) dummy.getNext();
	assertEquals("B", amo.object);
    }

    private static class TestGetReferenceUpdateBadTxn extends BadTxnTest {
	private ManagedReference<DummyManagedObject> ref;

	TestGetReferenceUpdateBadTxn(BadTxnState state) {
	    super("testGetReferenceUpdate", state);
	}

	protected void setUp() {
	    super.setUp();
	    ref = service.createReference(dummy);
	}

	void action() {
	    ref.getForUpdate();
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new TestGetReferenceUpdateBadTxn(state);
	}
    }

    /* -- Test ManagedReference.equals -- */

    /* -- App and service binding methods -- */

    <T extends ManagedObject> T getBinding(
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

    /* -- Other methods and classes -- */

    static class AnotherManagedObject extends DummyManagedObject {
	private static final long serialVersionUID = 1;
	Object object;
	AnotherManagedObject(DataManager dataManager, Object object) {
	    super(dataManager, "another");
	    this.object = object;
	}
    }

    /** The set of bad transaction states */
    static enum BadTxnState {
	Aborting, Aborted, Preparing, Committing, Committed
    };

    /** Defines a abstract class for testing bad transaction states. */
    static abstract class BadTxnTest extends TestDataServiceImpl {

	/** The bad state to test for this instance. */
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
	    }
	}
    }
}
