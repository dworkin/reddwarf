package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.test.util.DummyTransaction;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/*
 * XXX: Test recovery of prepared transactions after a crash
 * XXX: Test concurrent access
 */

/** Test the DataStoreImpl class */
public class TestDataStoreImpl extends TestCase {

    /** The test suite, to use for adding additional tests. */
    private static final TestSuite suite =
	new TestSuite(TestDataStoreImpl.class);

    /** Provides the test suite to the test runner. */
    public static Test suite() { return suite; }

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory);

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** An instance of the data store, to test. */
    DataStoreImpl store;

    /** An initial, open transaction. */
    DummyTransaction txn = new DummyTransaction();

    /** The object ID of a newly created object. */
    long id;

    /** Creates the test. */
    public TestDataStoreImpl(String name) {
	super(name);
    }

    /** Prints the test case, and creates the data store and an object. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	store = getDataStoreImpl();
	id = store.createObject(txn);
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
	if (txn != null) {
	    txn.abort();
	    txn = null;
	}
	if (passed && directory != null) {
	    store.shutdown();
	    deleteDirectory(directory);
	}
    }

    /* -- Test constructor -- */

    public void testConstructorNullArg() {
	try {
	    new DataStoreImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoDirectory() {
	Properties props = new Properties();
	try {
	    new DataStoreImpl(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadAllocationBlockSize() {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", "foo",
	    DataStoreImplClassName + ".allocationBlockSize", "gorp");
	try {
	    new DataStoreImpl(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativeAllocationBlockSize() {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory", "foo",
	    DataStoreImplClassName + ".allocationBlockSize", "-3");
	try {
	    new DataStoreImpl(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNonexistentDirectory() {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory",
	    "/this-is-a-non-existent-directory/yup");
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);	    
	}
    }

    public void testConstructorDirectoryIsFile() throws Exception {
	String file = File.createTempFile("existing", "db").getPath();
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory",
	    file);
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDirectoryNotWritable() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory",
	    createDirectory());
	File f = new File(directory);
	assertTrue("set readonly " + f, f.setReadOnly());
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorSuccess() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClassName + ".directory",
	    createDirectory());
	new DataStoreImpl(props);
    }

    /* -- Test createObject -- */

    public void testCreateObjectNullTxn() {
	try {
	    store.createObject(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testCreateObject", state) {
		void action() {
		    store.createObject(txn);
		}
	    };
	}
    }

    public void testCreateObjectSuccess() throws Exception {
	assertTrue(id >= 0);
	assertTrue(txn.participants.contains(store));
	long id2 = store.createObject(txn);
	assertTrue(id2 >= 0);
	assertTrue(id != id2);
	/*
	 * Only setting the object causes the current transaction to contain
	 * modifications!  -tjb@sun.com (10/18/2006)
	 */
	assertTrue(txn.prepare());
    }

    public void testCreateObjectMany() throws Exception {
	for (int i = 0; i < 10; i++) {
	    if (i != 0) {
		txn = new DummyTransaction();
	    }
	    for (int j = 0; j < 200; j++) {
		store.createObject(txn);
	    }
	    txn.commit();
	}
	txn = null;
    }

    /* -- Test markForUpdate -- */

    public void testMarkForUpdateNullTxn() {
	try {
	    store.markForUpdate(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateBadId() {
	try {
	    store.markForUpdate(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateNotFound() throws Exception {
	try {
	    store.markForUpdate(txn, id);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testMarkForUpdate", state) {
		protected void setUp() {
		    super.setUp();
		    store.setObject(txn, id, new byte[] { 0 });
		}
		void action() {
		    store.markForUpdate(txn, id);
		}
	    };
	}
    }

    public void testMarkForUpdateSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.markForUpdate(txn, id);
	store.markForUpdate(txn, id);
	assertTrue(txn.participants.contains(store));
	/* Marking for update is not an update! */
	assertTrue(txn.prepare());
    }

    /* -- Test getObject -- */

    public void testGetObjectNullTxn() {
	try {
	    store.getObject(null, 3, false);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetObjectBadId() {
	try {
	    store.getObject(txn, -3, false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testGetObjectNotFound() throws Exception {
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testGetObject", state) {
		protected void setUp() {
		    super.setUp();
		    store.setObject(txn, id, new byte[] { 0 });
		}
		void action() {
		    store.getObject(txn, id, false);
		}
	    };
	}
    }

    public void testGetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	txn.commit();
	txn = new DummyTransaction();
	byte[] result =	store.getObject(txn, id, false);
	assertTrue(txn.participants.contains(store));
	assertTrue(Arrays.equals(data, result));
	store.getObject(txn, id, false);
	/* Getting for update is not an update! */
	assertTrue(txn.prepare());
    }

    /* -- Test setObject -- */

    public void testSetObjectNullTxn() {
	byte[] data = { 0 };
	try {
	    store.setObject(null, 3, data);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectBadId() {
	byte[] data = { 0 };
	try {
	    store.setObject(txn, -3, data);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectNullData() {
	try {
	    store.setObject(txn, id, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectEmptyData() throws Exception {
	byte[] data = { };
	store.setObject(txn, id, data);
	txn.commit();
	txn = new DummyTransaction();
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testSetObject", state) {
		void action() {
		    store.setObject(txn, id, new byte[] { 0 });
		}
	    };
	}
    }

    public void testSetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	assertFalse(txn.prepare());
	txn.commit();
	txn = new DummyTransaction();
	byte[] result = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(data, result));
	byte[] newData = new byte[] { 3 };
	store.setObject(txn, id, newData);
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	txn.abort();
	txn = new DummyTransaction();
	assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	store.setObject(txn, id, newData);
	txn.commit();
	txn = new DummyTransaction();
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
    }

    /* -- Test removeObject -- */

    public void testRemoveObjectNullTxn() {
	try {
	    store.removeObject(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectBadId() {
	try {
	    store.removeObject(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectNotFound() {
	try {
	    store.removeObject(txn, id);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testRemoveObject", state) {
		protected void setUp() {
		    super.setUp();
		    store.setObject(txn, id, new byte[] { 0 });
		}
		void action() {
		    store.removeObject(txn, id);
		}
	    };
	}
    }

    public void testRemoveObjectSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.removeObject(txn, id);
	assertFalse(txn.prepare());
	txn.abort();
	txn = new DummyTransaction();
	store.removeObject(txn, id);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
	txn.commit();
	txn = new DummyTransaction();
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
    }

    /* -- Test getBinding -- */

    public void testGetBindingNullTxn() {
	try {
	    store.getBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingNullName() {
	try {
	    store.getBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingEmptyName() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "", id);
	txn.commit();
	txn = new DummyTransaction();
	long result = store.getBinding(txn, "");
	assertEquals(id, result);
    }

    public void testGetBindingNotFound() throws Exception {
	try {
	    store.getBinding(txn, "unknown");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingObjectNotFound() throws Exception {
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testGetBinding", state) {
		protected void setUp() {
		    super.setUp();
		    store.setBinding(txn, "foo", id);
		}
		void action() {
		    store.getBinding(txn, "foo");
		}
	    };
	}
    }

    public void testGetBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
	assertTrue(txn.prepare());
    }

    /* -- Test setBinding -- */

    public void testSetBindingNullTxn() {
	try {
	    store.setBinding(null, "foo", 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNullName() {
	try {
	    store.setBinding(txn, null, id);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testSetBinding", state) {
		void action() {
		    store.setBinding(txn, "foo", id);
		}
	    };
	}
    }

    public void testSetBindingSuccess() throws Exception {
	long newId = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	assertFalse(txn.prepare());
	txn.commit();
	txn = new DummyTransaction();
	assertEquals(id, store.getBinding(txn, "foo"));
	store.setBinding(txn, "foo", newId);
	assertEquals(newId, store.getBinding(txn, "foo"));
	txn.abort();
	txn = new DummyTransaction();
	assertEquals(id, store.getBinding(txn, "foo"));
    }

    /* -- Test removeBinding -- */

    public void testRemoveBindingNullTxn() {
	try {
	    store.removeBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingNullName() {
	try {
	    store.removeBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingNotFound() {
	try {
	    store.removeBinding(txn, "unknown");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testRemoveBinding", state) {
		protected void setUp() {
		    super.setUp();
		    store.setBinding(txn, "foo", id);
		}
		void action() {
		    store.removeBinding(txn, "foo");
		}
	    };
	}
    }

    public void testRemoveBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	store.removeBinding(txn, "foo");
	assertFalse(txn.prepare());
	txn.abort();
	txn = new DummyTransaction();
	assertEquals(id, store.getBinding(txn, "foo"));
	store.removeBinding(txn, "foo");
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	txn = new DummyTransaction();
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Test abort -- */

    public void testAbortNullTxn() throws Exception {
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.abort(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testAbort", state) {
		TransactionParticipant participant;
		protected void setUp() {
		    super.setUp();
		    participant = txn.participants.iterator().next();
		}
		void action() throws Exception {
		    participant.abort(txn);
		}
		void preparedModifiedTest() throws Exception {
		    store.setObject(txn, id, new byte[] { 0 });
		    txn.prepare();
		    /* Aborting a prepared, modified transaction is OK. */
		    action();
		}
	    };
	}
    }

    /* -- Test prepare -- */

    public void testPrepareNullTxn() throws Exception {
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.prepare(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testPrepare", state) {
		TransactionParticipant participant;
		protected void setUp() {
		    super.setUp();
		    participant = txn.participants.iterator().next();
		}
		void action() throws Exception {
		    participant.prepare(txn);
		}
	    };
	}
    }

    /* -- Test prepareAndCommit -- */

    public void testPrepareAndCommitNullTxn() throws Exception {
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.prepareAndCommit(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testPrepare", state) {
		TransactionParticipant participant;
		protected void setUp() {
		    super.setUp();
		    participant = txn.participants.iterator().next();
		}
		void action() throws Exception {
		    participant.prepareAndCommit(txn);
		}
	    };
	}
    }

    /* -- Test commit -- */

    public void testCommitNullTxn() throws Exception {
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.commit(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    static {
	for (BadTxnState state : BadTxnState.values()) {
	    new BadTxnTest("testCommit", state) {
		TransactionParticipant participant;
		protected void setUp() {
		    super.setUp();
		    participant = txn.participants.iterator().next();
		}
		void action() throws Exception {
		    participant.commit(txn);
		}
		void preparedModifiedTest() throws Exception {
		    store.setObject(txn, id, new byte[] { 0 });
		    txn.prepare();
		    /* Committing a prepared, modified transaction is OK. */
		    action();
		}
	    };
	}
    }

    /* -- Test deadlock -- */

    public void testDeadlock() throws Exception {
	txn.abort();
	txn = null;
	for (int i = 0; i < 5; i++) {
	    final int j = i;
	    final DataStoreImpl store = getDataStoreImpl();
	    DummyTransaction txn = new DummyTransaction();
	    final long id = store.createObject(txn);
	    store.setObject(txn, id, new byte[] { 0 });
	    final long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = new DummyTransaction();
	    store.getObject(txn, id, false);
	    final Semaphore flag = new Semaphore(1);
	    flag.acquire();
	    class MyRunnable implements Runnable {
		Exception exception2;
		public void run() {
		    DummyTransaction txn2 = null;
		    try {
			txn2 = new DummyTransaction();
			store.getObject(txn2, id2, false);
			flag.release();
			store.getObject(txn2, id, true);
			System.err.println(j + " txn2: commit");
			txn2.commit();
		    } catch (Exception e) {
			System.err.println(j + " txn2: " + e);
			exception2 = e;
			if (txn2 != null) {
			    txn2.abort();
			}
		    }
		}
	    }
	    MyRunnable myRunnable = new MyRunnable();
	    Thread thread = new Thread(myRunnable);
	    thread.start();
	    Thread.sleep(i * 500);
	    flag.acquire();
	    TransactionException exception = null;
	    try {
		store.getObject(txn, id2, true);
		System.err.println(i + " txn1: commit");
		txn.commit();
	    } catch (TransactionConflictException e) {
		System.err.println(i + " txn1: " + e);
		exception = e;
		txn.abort();
	    } catch (TransactionTimeoutException e) {
		System.err.println(i + " txn1: " + e);
		exception = e;
		txn.abort();
	    }
	    thread.join();
	    if (myRunnable.exception2 != null &&
		!(myRunnable.exception2
		  instanceof TransactionConflictException ||
		  myRunnable.exception2
		  instanceof TransactionTimeoutException))
	    {
		throw myRunnable.exception2;
	    } else if (exception == null && myRunnable.exception2 == null) {
		fail("Expected TransactionConflictException");
	    }
	}
    }

    /* -- Other methods and classes -- */

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

    /** Returns a DataStoreImpl for the shared database. */
    private DataStoreImpl getDataStoreImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataStoreImpl(dbProps);
    }

    /** The set of bad transaction states */
    static enum BadTxnState {
	Aborted, PreparedReadOnly, PreparedModified, Committed, Wrong
    };

    /** Defines a abstract class for testing bad transaction states. */
    static abstract class BadTxnTest extends TestDataStoreImpl {

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
	abstract void action() throws Exception;

	/** Runs the test for the bad transaction state. */
	protected void runTest() throws Exception {
	    switch (state) {
	    case Aborted:
		abortedTest();
		break;
	    case PreparedReadOnly:
		preparedReadOnlyTest();
		break;
	    case PreparedModified:
		preparedModifiedTest();
		break;
	    case Committed:
		committedTest();
		break;
	    case Wrong:
		wrongTest();
		break;
	    default:
		throw new AssertionError();
	    }
	}

	/** Runs the test for the aborted case. */
	void abortedTest() throws Exception {
	    txn.abort();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    } finally {
		txn = null;
	    }
	}

	/** Runs the test for the prepared returns read-only case. */
	void preparedReadOnlyTest() throws Exception {
	    txn.prepare();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    }
	}

	/** Runs the test for the prepared returns modified case. */
	void preparedModifiedTest() throws Exception {
	    store.setObject(txn, id, new byte[] { 0 });
	    txn.prepare();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    }
	}

	/** Runs the test for the committed case. */
	void committedTest() throws Exception {
	    txn.commit();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    } finally {
		txn = null;
	    }
	}

	/** Runs the test for the wrong transaction case. */
	void wrongTest() throws Exception {
	    store.createObject(txn);
	    DummyTransaction originalTxn = txn;
	    txn = new DummyTransaction();
	    try {
		action();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
		System.err.println(e);
	    } finally {
		originalTxn.abort();
	    }
	}
    }
}
