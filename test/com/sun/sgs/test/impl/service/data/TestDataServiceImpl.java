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
import com.sun.sgs.test.DummyManagedObject;
import com.sun.sgs.test.DummyTransaction;
import com.sun.sgs.test.DummyTransactionParticipant;
import com.sun.sgs.test.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/** Test the DataServiceImpl class */
public class TestDataServiceImpl extends TestCase {

    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

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
    private String directory;

    private DataServiceImpl service;

    private DummyTransactionProxy txnProxy;

    private DummyTransaction txn;

    /** Creates the test. */
    public TestDataServiceImpl(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	service = getDataServiceImpl();
	txnProxy = new DummyTransactionProxy();
	service.configure(txnProxy);
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
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
    private String createDirectory() throws IOException {
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

    /** Returns a DataServiceImpl for the shared database. */
    private DataServiceImpl getDataServiceImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps);
    }

    private DummyTransaction createTransaction() {
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
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
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
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
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
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
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
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
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

    public void testGetBindingAborting() throws Exception {
	testGetBindingAborting(true);
    }
    public void testGetServiceBindingAborting() throws Exception {
	testGetBindingAborting(false);
    }
    private void testGetBindingAborting(final boolean app) throws Exception {
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    getBinding(
			app, service, "dummy", DummyManagedObject.class);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testGetBindingAborted() throws Exception {
	testGetBindingAborted(true);
    }
    public void testGetServiceBindingAborted() throws Exception {
	testGetBindingAborted(false);
    }
    private void testGetBindingAborted(boolean app) throws Exception {
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	createTransaction();
	txn.abort();
	try {
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingPreparing() throws Exception {
	testGetBindingPreparing(true);
    }
    public void testGetServiceBindingPreparing() throws Exception {
	testGetBindingPreparing(false);
    }
    private void testGetBindingPreparing(final boolean app) throws Exception {
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) {
		try {
		    getBinding(
			app, service, "dummy", DummyManagedObject.class);
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testGetBindingCommitting() throws Exception {
	testGetBindingCommitting(true);
    }
    public void testGetServiceBindingCommitting() throws Exception {
	testGetBindingCommitting(false);
    }
    private void testGetBindingCommitting(final boolean app) throws Exception {
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    getBinding(
			app, service, "dummy", DummyManagedObject.class);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testGetBindingCommitted() throws Exception {
	testGetBindingCommitted(true);
    }
    public void testGetServiceBindingCommitted() throws Exception {
	testGetBindingCommitted(false);
    }
    private void testGetBindingCommitted(boolean app) throws Exception {
	setBinding(
	    app, service, "dummy", new DummyManagedObject(service, "dummy"));
	txn.commit();
	try {
	    getBinding(app, service, "dummy", DummyManagedObject.class);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
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
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
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
	DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");
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

    public void testSetBindingAborting() throws Exception {
	testSetBindingAborting(true);
    }
    public void testSetServiceBindingAborting() throws Exception {
	testSetBindingAborting(false);
    }
    private void testSetBindingAborting(final boolean app) throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    setBinding(app, service, "dummy", dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testSetBindingAborted() throws Exception {
	testSetBindingAborted(true);
    }
    public void testSetServiceBindingAborted() throws Exception {
	testSetBindingAborted(false);
    }
    private void testSetBindingAborted(boolean app) throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	createTransaction();
	txn.abort();
	try {
	    setBinding(app, service, "dummy", dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingPreparing() throws Exception {
	testSetBindingPreparing(true);
    }
    public void testSetServiceBindingPreparing() throws Exception {
	testSetBindingPreparing(false);
    }
    private void testSetBindingPreparing(final boolean app) throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");
	setBinding(app, service, "dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) {
		try {
		    setBinding(app, service, "dummy", dummy);
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testSetBindingCommitting() throws Exception {
	testSetBindingCommitting(true);
    }
    public void testSetServiceBindingCommitting() throws Exception {
	testSetBindingCommitting(false);
    }
    private void testSetBindingCommitting(final boolean app) throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");
	setBinding(app, service, "dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    setBinding(app, service, "dummy", dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testSetBindingCommitted() throws Exception {
	testSetBindingCommitted(true);
    }
    public void testSetServiceBindingCommitted() throws Exception {
	testSetBindingCommitted(false);
    }
    private void testSetBindingCommitted(boolean app) throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	txn.commit();
	createTransaction();
	txn.commit();
	try {
	    setBinding(app, service, "dummy", dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
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

    public void testRemoveBindingAborting() throws Exception {
	testRemoveBindingAborting(true);
    }
    public void testRemoveServiceBindingAborting() throws Exception {
	testRemoveBindingAborting(false);
    }
    private void testRemoveBindingAborting(final boolean app) throws Exception {
	setBinding(app, service, "dummy",
		   new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    removeBinding(app, service, "dummy");
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testRemoveBindingAborted() throws Exception {
	testRemoveBindingAborted(true);
    }
    public void testRemoveServiceBindingAborted() throws Exception {
	testRemoveBindingAborted(false);
    }
    private void testRemoveBindingAborted(boolean app) throws Exception {
	setBinding(app, service, "dummy",
		   new DummyManagedObject(service, "dummy"));
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	createTransaction();
	txn.abort();
	try {
	    removeBinding(app, service, "dummy");
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingPreparing() throws Exception {
	testRemoveBindingPreparing(true);
    }
    public void testRemoveServiceBindingPreparing() throws Exception {
	testRemoveBindingPreparing(false);
    }
    private void testRemoveBindingPreparing(final boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy",
		   new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) {
		try {
		    removeBinding(app, service, "dummy");
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testRemoveBindingCommitting() throws Exception {
	testRemoveBindingCommitting(true);
    }
    public void testRemoveServiceBindingCommitting() throws Exception {
	testRemoveBindingCommitting(false);
    }
    private void testRemoveBindingCommitting(final boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy",
		   new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    removeBinding(app, service, "dummy");
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testRemoveBindingCommitted() throws Exception {
	testRemoveBindingCommitted(true);
    }
    public void testRemoveServiceBindingCommitted() throws Exception {
	testRemoveBindingCommitted(false);
    }
    private void testRemoveBindingCommitted(boolean app) throws Exception {
	setBinding(app, service, "dummy",
		   new DummyManagedObject(service, "dummy"));
	txn.commit();
	createTransaction();
	txn.commit();
	try {
	    removeBinding(app, service, "dummy");
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(true);
    }
    public void testRemoveServiceBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(false);
    }
    private void testRemoveBindingRemovedObject(boolean app) throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
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

    public void testRemoveObjectAborting() {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    service.removeObject(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testRemoveObjectAborted() {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.abort();
	try {
	    service.removeObject(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectPreparing() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	service.setBinding("dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) throws Exception {
		try {
		    service.removeObject(dummy);
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testRemoveObjectCommitting() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    service.removeObject(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testRemoveObjectCommitted() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.commit();
	try {
	    service.removeObject(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectSuccess() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
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
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
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
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
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

    public void testMarkForUpdateAborting() {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    service.markForUpdate(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testMarkForUpdateAborted() {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.abort();
	try {
	    service.markForUpdate(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdatePreparing() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	service.setBinding("dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) throws Exception {
		try {
		    service.markForUpdate(dummy);
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testMarkForUpdateCommitting() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	service.setBinding("dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    service.markForUpdate(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testMarkForUpdateCommitted() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.commit();
	try {
	    service.markForUpdate(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
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

    public void testCreateReferenceAborting() {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    service.createReference(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.abort();
	assertTrue(participant.ok);
    }

    public void testCreateReferenceAborted() {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.abort();
	try {
	    service.createReference(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferencePreparing() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	service.setBinding("dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) throws Exception {
		try {
		    service.createReference(dummy);
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertTrue(participant.ok);
    }

    public void testCreateReferenceCommitting() throws Exception {
	final DummyManagedObject dummy =
	    new DummyManagedObject(service, "dummy");	
	service.setBinding("dummy", dummy);
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    service.createReference(dummy);
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	};
	MyParticipant participant = new MyParticipant();
	txn.join(participant);
	txn.commit();
	assertTrue(participant.ok);
    }

    public void testCreateReferenceCommitted() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");	
	txn.commit();
	try {
	    service.createReference(dummy);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceNew() {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceExisting() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	txn.commit();
	createTransaction();
	DummyManagedObject dummy =
	    service.getBinding("dummy", DummyManagedObject.class);
	ManagedReference ref = service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceRemoved() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
	service.removeObject(dummy);
	assertEquals(dummy, service.createReference(dummy).get());
    }

    public void testCreateReferencePreviousTxn() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
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

    /* -- Test ManagedReference.getForUpdate -- */

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
}
