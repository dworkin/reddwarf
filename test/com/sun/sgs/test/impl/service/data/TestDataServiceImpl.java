package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.DummyManagedObject;
import com.sun.sgs.test.DummyTransaction;
import com.sun.sgs.test.DummyTransactionParticipant;
import com.sun.sgs.test.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;
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
	"com.sun.sgs.appName", "TestDataServiceImpl");

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
	    txn.abort();
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

    /* -- Test DataManager.getBinding -- */

    public void testGetBindingNullName() {
	try {
	    service.getBinding(null, ManagedObject.class);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingEmptyName() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("", dummy);
	txn.commit();
	createTransaction();
	DummyManagedObject result =
	    service.getBinding("", DummyManagedObject.class);
	assertEquals(dummy, result);
    }

    public void testGetBindingNullType() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	try {
	    service.getBinding("dummy", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingWrongType() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	try {
	    service.getBinding("dummy", AnotherManagedObject.class);
	    fail("Expected ClassCastException");
	} catch (ClassCastException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingNotFound() throws Exception {
	try {
	    service.getBinding("unknown", ManagedObject.class);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
    }

    public void testGetBindingObjectNotFound() throws Exception {
	DummyManagedObject dummy = new DummyManagedObject(service, "dummy");
	service.setBinding("dummy", dummy);
	txn.commit();
	createTransaction();
	dummy = service.getBinding("dummy", DummyManagedObject.class);
	service.removeObject(dummy);
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingNoTransaction() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	txn = null;
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingAborted() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	txn.commit();
	txnProxy.setCurrentTransaction(null);
	createTransaction();
	txn.abort();
	txn = null;
	try {
	    service.getBinding("dummy", DummyManagedObject.class);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* XXX: Need to be able to check if the transaction is prepared. */
    public void testGetBindingPreparing() throws Exception {
	service.setBinding("dummy", new DummyManagedObject(service, "dummy"));
	class MyParticipant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) {
		try {
		    service.getBinding("dummy", DummyManagedObject.class);
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
	txn = null;
    }

//     public void testGetBindingCommitted() throws Exception {
// 	DataServiceImpl service = getDataServiceImpl();
// 	DummyTransaction txn = new DummyTransaction();
// 	long id = store.createObject(txn);
// 	store.setBinding(txn, "foo", id);
// 	txn.commit();
// 	try {
// 	    store.getBinding(txn, "foo");
// 	    fail("Expected IllegalStateException");
// 	} catch (IllegalStateException e) {
// 	    System.err.println(e);
// 	}
//     }

//     public void testGetBindingWrongTxn() throws Exception {
// 	DataServiceImpl service = getDataServiceImpl();
// 	DummyTransaction txn = new DummyTransaction();
// 	long id = store.createObject(txn);
// 	store.setBinding(txn, "foo", id);
// 	txn.commit();
// 	txn = new DummyTransaction();
// 	store.createObject(txn);
// 	DummyTransaction txn2 = new DummyTransaction();
// 	try {
// 	    store.getBinding(txn2, "foo");
// 	    fail("Expected IllegalStateException");
// 	} catch (IllegalStateException e) {
// 	    System.err.println(e);
// 	} finally {
// 	    txn.abort();
// 	    txn2.abort();
// 	}
//     }

//     public void testGetBindingSuccess() throws Exception {
// 	DataServiceImpl service = getDataServiceImpl();
// 	DummyTransaction txn = new DummyTransaction();
// 	long id = store.createObject(txn);
// 	store.setObject(txn, id, new byte[] { 0 });
// 	store.setBinding(txn, "foo", id);
// 	txn.commit();
// 	txn = new DummyTransaction();
// 	long result = store.getBinding(txn, "foo");
// 	assertEquals(id, result);
// 	assertTrue(txn.prepare());
// 	txn.abort();
//     }

    /* -- Other methods and classes -- */

    static class AnotherManagedObject implements ManagedObject, Serializable { }
}
