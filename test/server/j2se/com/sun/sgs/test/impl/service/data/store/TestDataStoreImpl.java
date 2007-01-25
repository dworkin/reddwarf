package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import junit.framework.TestCase;

/*
 * XXX: Test recovery of prepared transactions after a crash
 * XXX: Test concurrent access
 */

/** Test the DataStoreImpl class */
public class TestDataStoreImpl extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    protected static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /** Make sure an empty version of the directory exists. */
    static {
	cleanDirectory(dbDirectory);
    }

    /** Set when the test passes. */
    protected boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Properties for creating the DataStore. */
    protected Properties props;

    /** An instance of the data store, to test. */
    DataStore store;

    /** An initial, open transaction. */
    DummyTransaction txn;

    /** The object ID of a newly created object. */
    long id;

    /** Creates the test. */
    public TestDataStoreImpl(String name) {
	super(name);
    }

    /** Prints the test case, and creates the data store and an object. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	props = createProperties(
	    DataStoreImplClassName + ".directory", dbDirectory);
	store = getDataStore();
	id = store.createObject(txn);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /** Abort the current transaction, if non-null, and shutdown the store. */
    protected void tearDown() throws Exception {
	try {
	    if (txn != null) {
		txn.abort();
	    }
	    if (store != null) {
		new ShutdownAction().waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	}
	txn = null;
	store = null;
    }

    /* -- Test constructor -- */

    public void testConstructorNullArg() throws Exception {
	try {
	    createDataStore(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoDirectory() throws Exception {
	props.remove(DataStoreImplClassName + ".directory");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreImplClassName + ".allocationBlockSize", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativeAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreImplClassName + ".allocationBlockSize", "-3");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNonexistentDirectory() throws Exception {
	props.setProperty(
	    DataStoreImplClassName + ".directory",
	    "/this-is-a-non-existent-directory/yup");
	try {
	    createDataStore(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);	    
	}
    }

    public void testConstructorDirectoryIsFile() throws Exception {
	String file = File.createTempFile("existing", "db").getPath();
	props.setProperty(
	    DataStoreImplClassName + ".directory",
	    file);
	try {
	    createDataStore(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDirectoryNotWritable() throws Exception {
        String osName = System.getProperty("os.name", "unknown");
	/*
	 * Can't seem to create a non-writable directory on Windows.
	 * -tjb@sun.com (01/09/2007)
	 */
        if (osName.startsWith("Windows")) {
            System.err.println("Skipping on " + osName);
            return;
        }
	props.setProperty(
	    DataStoreImplClassName + ".directory", createDirectory());
	new File(directory).setReadOnly();
	try {
	    createDataStore(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
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

    /* -- Unusual states -- */
    private final Action createObject = new Action() {
	void run() { store.createObject(txn); };
    };
    public void testCreateObjectAborted() throws Exception {
	testAborted(createObject);
    }
    public void testCreateObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(createObject);
    }
    public void testCreateObjectPreparedModified() throws Exception {
	testPreparedModified(createObject);
    }
    public void testCreateObjectCommitted() throws Exception {
	testCommitted(createObject);
    }
    public void testCreateObjectWrongTxn() throws Exception {
	testWrongTxn(createObject);
    }
    public void testCreateObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(createObject);
    }
    public void testCreateObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createObject);
    }
    public void testCreateObjectShutdown() throws Exception {
	testShutdown(createObject);
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
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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

    /* -- Unusual states -- */
    private final Action markForUpdate = new Action() {
	void setUp() { store.setObject(txn, id, new byte[] { 0 }); }
	void run() { store.markForUpdate(txn, id); }
    };
    public void testMarkForUpdateAborted() throws Exception {
	testAborted(markForUpdate);
    }
    public void testMarkForUpdatePreparedReadOnly() throws Exception {
	testPreparedReadOnly(markForUpdate);
    }
    public void testMarkForUpdatePreparedModified() throws Exception {
	testPreparedModified(markForUpdate);
    }
    public void testMarkForUpdateCommitted() throws Exception {
	testCommitted(markForUpdate);
    }
    public void testMarkForUpdateWrongTxn() throws Exception {
	testWrongTxn(markForUpdate);
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
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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

    /* -- Unusual states -- */
    private final Action getObject = new Action() {
	void setUp() { store.setObject(txn, id, new byte[] { 0 }); }
	void run() { store.getObject(txn, id, false); };
    };
    public void testGetObjectAborted() throws Exception {
	testAborted(getObject);
    }
    public void testGetObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getObject);
    }
    public void testGetObjectPreparedModified() throws Exception {
	testPreparedModified(getObject);
    }
    public void testGetObjectCommitted() throws Exception {
	testCommitted(getObject);
    }
    public void testGetObjectWrongTxn() throws Exception {
	testWrongTxn(getObject);
    }
    public void testGetObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getObject);
    }
    public void testGetObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getObject);
    }
    public void testGetObjectShutdown() throws Exception {
	testShutdown(getObject);
    }

    public void testGetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
    }

    /* -- Unusual states -- */
    private final Action setObject = new Action() {
	void run() { store.setObject(txn, id, new byte[] { 0 }); }
    };
    public void testSetObjectAborted() throws Exception {
	testAborted(setObject);
    }
    public void testSetObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setObject);
    }
    public void testSetObjectPreparedModified() throws Exception {
	testPreparedModified(setObject);
    }
    public void testSetObjectCommitted() throws Exception {
	testCommitted(setObject);
    }
    public void testSetObjectWrongTxn() throws Exception {
	testWrongTxn(setObject);
    }
    public void testSetObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setObject);
    }
    public void testSetObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setObject);
    }
    public void testSetObjectShutdown() throws Exception {
	testShutdown(setObject);
    }

    public void testSetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	assertFalse(txn.prepare());
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(data, result));
	byte[] newData = new byte[] { 3 };
	store.setObject(txn, id, newData);
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	store.setObject(txn, id, newData);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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

    /* -- Unusual states -- */
    private final Action removeObject = new Action() {
	void setUp() { store.setObject(txn, id, new byte[] { 0 }); }
	void run() { store.removeObject(txn, id); }
    };
    public void testRemoveObjectAborted() throws Exception {
	testAborted(removeObject);
    }
    public void testRemoveObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(removeObject);
    }
    public void testRemoveObjectPreparedModified() throws Exception {
	testPreparedModified(removeObject);
    }
    public void testRemoveObjectCommitted() throws Exception {
	testCommitted(removeObject);
    }
    public void testRemoveObjectWrongTxn() throws Exception {
	testWrongTxn(removeObject);
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
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeObject(txn, id);
	assertFalse(txn.prepare());
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeObject(txn, id);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
    }

    /* -- Unusual states -- */
    private final Action getBinding = new Action() {
	void setUp() { store.setBinding(txn, "foo", id); }
	void run() { store.getBinding(txn, "foo"); }
    };
    public void testGetBindingAborted() throws Exception {
	testAborted(getBinding);
    }
    public void testGetBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getBinding);
    }
    public void testGetBindingPreparedModified() throws Exception {
	testPreparedModified(getBinding);
    }
    public void testGetBindingCommitted() throws Exception {
	testCommitted(getBinding);
    }
    public void testGetBindingWrongTxn() throws Exception {
	testWrongTxn(getBinding);
    }
    public void testGetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getBinding);
    }
    public void testGetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getBinding);
    }
    public void testGetBindingShutdown() throws Exception {
	testShutdown(getBinding);
    }

    public void testGetBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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

    /* -- Unusual states -- */
    private final Action setBinding = new Action() {
	void run() { store.setBinding(txn, "foo", id); }
    };
    public void testSetBindingAborted() throws Exception {
	testAborted(setBinding);
    }
    public void testSetBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setBinding);
    }
    public void testSetBindingPreparedModified() throws Exception {
	testPreparedModified(setBinding);
    }
    public void testSetBindingCommitted() throws Exception {
	testCommitted(setBinding);
    }
    public void testSetBindingWrongTxn() throws Exception {
	testWrongTxn(setBinding);
    }
    public void testSetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setBinding);
    }
    public void testSetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setBinding);
    }
    public void testSetBindingShutdown() throws Exception {
	testShutdown(setBinding);
    }

    public void testSetBindingSuccess() throws Exception {
	long newId = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	assertFalse(txn.prepare());
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(id, store.getBinding(txn, "foo"));
	store.setBinding(txn, "foo", newId);
	assertEquals(newId, store.getBinding(txn, "foo"));
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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

    /* -- Unusual states -- */
    private final Action removeBinding = new Action() {
	void setUp() { store.setBinding(txn, "foo", id); }
	void run() { store.removeBinding(txn, "foo"); }
    };
    public void testRemoveBindingAborted() throws Exception {
	testAborted(removeBinding);
    }
    public void testRemoveBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(removeBinding);
    }
    public void testRemoveBindingPreparedModified() throws Exception {
	testPreparedModified(removeBinding);
    }
    public void testRemoveBindingCommitted() throws Exception {
	testCommitted(removeBinding);
    }
    public void testRemoveBindingWrongTxn() throws Exception {
	testWrongTxn(removeBinding);
    }
    public void testRemoveBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeBinding);
    }
    public void testRemoveBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeBinding);
    }
    public void testRemoveBindingShutdown() throws Exception {
	testShutdown(removeBinding);
    }

    public void testRemoveBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeBinding(txn, "foo");
	assertFalse(txn.prepare());
	txn.abort();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(id, store.getBinding(txn, "foo"));
	store.removeBinding(txn, "foo");
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Test nextBoundName -- */

    public void testNextBoundNameNullTxn() {
	try {
	    store.nextBoundName(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testNextBoundNameEmpty() {
	assertEquals(null, store.nextBoundName(txn, ""));
	store.setBinding(txn, "", id);
	assertEquals("", store.nextBoundName(txn, null));
	assertEquals(null, store.nextBoundName(txn, ""));
    }

    /* -- Unusual states -- */
    private final Action nextBoundName = new Action() {
	void run() { store.nextBoundName(txn, null); }
    };
    public void testNextBoundNameAborted() throws Exception {
	testAborted(nextBoundName);
    }
    public void testNextBoundNamePreparedReadOnly() throws Exception {
	testPreparedReadOnly(nextBoundName);
    }
    public void testNextBoundNamePreparedModified() throws Exception {
	testPreparedModified(nextBoundName);
    }
    public void testNextBoundNameCommitted() throws Exception {
	testCommitted(nextBoundName);
    }
    public void testNextBoundNameWrongTxn() throws Exception {
	testWrongTxn(nextBoundName);
    }
    public void testNextBoundNameShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextBoundName);
    }
    public void testNextBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextBoundName);
    }
    public void testNextBoundNameShutdown() throws Exception {
	testShutdown(nextBoundName);
    }

    public void testNextBoundNameSuccess() throws Exception {
	for (String name = null;
	     (name = store.nextBoundName(txn, name)) != null; )
	{
	    store.removeBinding(txn, name);
	}
	assertNull(store.nextBoundName(txn, null));
	assertNull(store.nextBoundName(txn, "name-1"));
	assertNull(store.nextBoundName(txn, ""));
	store.setBinding(txn, "name-1", id);
	assertEquals("name-1", store.nextBoundName(txn, null));
	assertEquals(null, store.nextBoundName(txn, "name-1"));
	assertEquals(null, store.nextBoundName(txn, "name-2"));
	assertEquals(null, store.nextBoundName(txn, "name-1"));
	assertEquals("name-1", store.nextBoundName(txn, "name-0"));
	assertEquals("name-1", store.nextBoundName(txn, null));
	assertEquals("name-1", store.nextBoundName(txn, "name-0"));
	store.setBinding(txn, "name-2", id);
	txn.commit();
	txn = new DummyTransaction();
	assertEquals("name-1", store.nextBoundName(txn, null));
	assertEquals("name-2", store.nextBoundName(txn, "name-1"));
	assertNull(store.nextBoundName(txn, "name-2"));
	assertNull(store.nextBoundName(txn, "name-3"));
	assertEquals("name-1", store.nextBoundName(txn, "name-0"));
	assertEquals("name-2", store.nextBoundName(txn, "name-1"));
	assertEquals("name-1", store.nextBoundName(txn, null));
	store.removeBinding(txn, "name-1");
	assertEquals("name-2", store.nextBoundName(txn, null));
	assertEquals("name-2", store.nextBoundName(txn, "name-1"));
	assertEquals(null, store.nextBoundName(txn, "name-2"));
	assertEquals(null, store.nextBoundName(txn, "name-3"));
	assertEquals("name-2", store.nextBoundName(txn, "name-0"));
	store.removeBinding(txn, "name-2");
	assertNull(store.nextBoundName(txn, "name-2"));
	assertNull(store.nextBoundName(txn, null));
	store.setBinding(txn, "name-1", id);
	store.setBinding(txn, "name-2", id);
	txn.commit();
	txn = new DummyTransaction();
	assertEquals("name-1", store.nextBoundName(txn, null));
	assertEquals("name-1", store.nextBoundName(txn, null));
	store.removeBinding(txn, "name-1");
	assertEquals("name-2", store.nextBoundName(txn, null));
	store.removeBinding(txn, "name-2");
	assertNull(store.nextBoundName(txn, null));
	txn.abort();
	txn = new DummyTransaction();
	assertEquals("name-1", store.nextBoundName(txn, null));
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

    /* -- Unusual states -- */
    private final Action abort = new Action() {
	TransactionParticipant participant;
	void setUp() { participant = txn.participants.iterator().next(); }
	void run() {
	    participant.abort(txn);
	    txn = null;
	}
    };
    public void testAbortAborted() throws Exception {
	testAborted(abort);
    }
    public void testAbortPreparedReadOnly() throws Exception {
	testPreparedReadOnly(abort);
    }
    public void testAbortPreparedModified() throws Exception {
	abort.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	/* Aborting a prepared, modified transaction is OK. */
	abort.run();
    }
    public void testAbortCommitted() throws Exception {
	testCommitted(abort);
    }
    public void testAbortWrongTxn() throws Exception {
	testWrongTxn(abort);
    }
    public void testAbortShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(abort);
    }
    public void testAbortShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(abort);
    }
    public void testAbortShutdown() throws Exception {
	testShutdown(abort);
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

    /* -- Unusual states -- */
    private final Action prepare = new Action() {
	private TransactionParticipant participant;
	void setUp() { participant = txn.participants.iterator().next(); }
	void run() throws Exception {
	    assertTrue(participant.prepare(txn));
	    txn = null;
	}
    };
    public void testPrepareAborted() throws Exception {
	testAborted(prepare);
    }
    public void testPreparePreparedReadOnly() throws Exception {
	testPreparedReadOnly(prepare);
    }
    public void testPreparePreparedModified() throws Exception {
	testPreparedModified(prepare);
    }
    public void testPrepareCommitted() throws Exception {
	testCommitted(prepare);
    }
    public void testPrepareWrongTxn() throws Exception {
	testWrongTxn(prepare);
    }
    public void testPrepareShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(prepare);
    }
    public void testPrepareShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(prepare);
    }
    public void testPrepareShutdown() throws Exception {
	testShutdown(prepare);
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

    /* -- Unusual states -- */
    private final Action prepareAndCommit = new Action() {
	private TransactionParticipant participant;
	void setUp() { participant = txn.participants.iterator().next(); }
	void run() throws Exception {
	    participant.prepareAndCommit(txn);
	    txn = null;
	}
    };
    public void testPrepareAndCommitAborted() throws Exception {
	testAborted(prepareAndCommit);
    }
    public void testPrepareAndCommitPrepareAndCommitdReadOnly()
	throws Exception
    {
	testPreparedReadOnly(prepareAndCommit);
    }
    public void testPrepareAndCommitPrepareAndCommitdModified()
	throws Exception
    {
	testPreparedModified(prepareAndCommit);
    }
    public void testPrepareAndCommitCommitted() throws Exception {
	testCommitted(prepareAndCommit);
    }
    public void testPrepareAndCommitWrongTxn() throws Exception {
	testWrongTxn(prepareAndCommit);
    }
    public void testPrepareAndCommitShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(prepareAndCommit);
    }
    public void testPrepareAndCommitShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(prepareAndCommit);
    }
    public void testPrepareAndCommitShutdown() throws Exception {
	testShutdown(prepareAndCommit);
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

    /* -- Unusual states -- */
    class CommitAction extends Action {
	private TransactionParticipant participant;
	void setUp() { participant = txn.participants.iterator().next(); }
	void run() throws Exception {
	    participant.commit(txn);
	    txn = null;
	}
    }
    private final CommitAction commit = new CommitAction();
    public void testCommitAborted() throws Exception {
	testAborted(commit);
    }
    public void testCommitPreparedReadOnly() throws Exception {
	testPreparedReadOnly(commit);
    }
    public void testCommitPreparedModified() throws Exception {
	commit.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	assertFalse(txn.prepare());
	/* Committing a prepared, modified transaction is OK. */
	commit.run();
    }
    public void testCommitCommitted() throws Exception {
	testCommitted(commit);
    }
    public void testCommitWrongTxn() throws Exception {
	testWrongTxn(commit);
    }
    public void testCommitShuttingDownExistingTxn() throws Exception {
	commit.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	assertFalse(txn.prepare());
	/* Committing a prepared, modified transaction is OK. */
	testShuttingDownExistingTxn(commit);
    }
    public void testCommitShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(commit);
    }
    public void testCommitShutdown() throws Exception {
	testShutdown(commit);
    }

    /* -- Test shutdown -- */

    public void testShutdownAgain() throws Exception {
	txn.abort();
	txn = null;
	store.shutdown();
	ShutdownAction action = new ShutdownAction();
	try {
	    action.waitForDone();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    store = null;
	}
    }

    public void testShutdownInterrupt() throws Exception {
	ShutdownAction action = new ShutdownAction();
	action.assertBlocked();
	action.interrupt();
	action.assertResult(false);
	store.setBinding(txn, "foo", id);
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
	txn.abort();
	action2.assertResult(true);
	txn = null;
	store = null;
    }

    public void testConcurrentShutdownRace() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	txn.abort();
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
	store = null;
    }

    public void testShutdownRestart() throws Exception {
	store.setBinding(txn, "foo", id);
	byte[] bytes = { 1 };
	store.setObject(txn, id, bytes);
	txn.commit();
	store.shutdown();
	store = getDataStore();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	id = store.getBinding(txn, "foo");
	byte[] value = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(bytes, value));
    }

    /* -- Test deadlock -- */
    @SuppressWarnings("hiding")
    public void testDeadlock() throws Exception {
	for (int i = 0; i < 5; i++) {
	    if (i > 0) {
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    final long id = store.createObject(txn);
	    store.setObject(txn, id, new byte[] { 0 });
	    final long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getObject(txn, id, false);
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
			store.getObject(txn2, id2, false);
			flag.release();
			store.getObject(txn2, id, true);
			System.err.println(finalI + " txn2: commit");
			txn2.commit();
		    } catch (TransactionAbortedException e) {
			System.err.println(finalI + " txn2: " + e);
			exception2 = e;
		    } catch (Exception e) {
			System.err.println(finalI + " txn2: " + e);
			exception2 = e;
			if (txn2 != null) {
			    txn2.abort();
			}
		    }
		}
	    }
	    MyRunnable myRunnable = new MyRunnable();
	    Thread thread = new Thread(myRunnable, "testDeadlockThread");
	    thread.start();
	    Thread.sleep(i * 500);
	    flag.acquire();
	    TransactionException exception = null;
	    try {
		store.getObject(txn, id2, true);
		System.err.println(i + " txn1: commit");
		txn.commit();
	    } catch (TransactionAbortedException e) {
		System.err.println(i + " txn1: " + e);
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

    /** Gets a DataStore using the default properties. */
    protected DataStore getDataStore() throws Exception {
	return createDataStore(props);
    }

    /** Creates a DataStore using the specified properties. */
    protected DataStore createDataStore(Properties props) throws Exception {
	return new DataStoreImpl(props);
    }

    /* -- Support for testing unusual states -- */

    /**
     * An action, with an optional setup step, to be run in the context of an
     * unusual state.
     */
    abstract class Action {
	void setUp() throws Exception { };
	abstract void run() throws Exception;
    }

    /** Tests running the action after abort. */
    void testAborted(Action action) throws Exception {
	action.setUp();
	txn.abort();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /** Tests running the action after prepare returns read-only. */
    void testPreparedReadOnly(Action action) throws Exception {
	action.setUp();
	txn.prepare();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /** Tests running the action after prepare returns modified. */
    void testPreparedModified(Action action) throws Exception {
	action.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /** Tests running the action after commit. */
    void testCommitted(Action action) throws Exception {
	action.setUp();
	txn.commit();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /** Tests running the action in the wrong transaction. */
    void testWrongTxn(Action action) throws Exception {
	action.setUp();
	store.createObject(txn);
	DummyTransaction originalTxn = txn;
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    originalTxn.abort();
	}
    }

    /**
     * Tests running the action in an existing transaction while shutting down.
     */
    void testShuttingDownExistingTxn(Action action) throws Exception {
	action.setUp();
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	action.run();
	if (txn != null) {
	    txn.commit();
	    txn = null;
	}
	shutdownAction.assertResult(true);
	store = null;
    }

    /** Tests running the action in a new transaction while shutting down. */
    void testShuttingDownNewTxn(Action action) throws Exception {
	action.setUp();
	DummyTransaction originalTxn = txn;
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	txn.abort();
	txn = null;
	originalTxn.abort();
	shutdownAction.assertResult(true);
	store = null;
    }

    /** Tests running the action after shutdown. */
    void testShutdown(Action action) throws Exception {
	action.setUp();
	txn.abort();
	store.shutdown();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	txn = null;
	store = null;
    }

    /** Use this thread to control a call to shutdown that may block. */
    protected class ShutdownAction extends Thread {
	private boolean done;
	private Throwable exception;
	private boolean result;

	/** Creates an instance of this class and starts the thread. */
	protected ShutdownAction() {
	    start();
	}

	/** Performs the shutdown and collects the results. */
	public void run() {
	    try {
		result = shutdown();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	protected boolean shutdown() {
	    return store.shutdown();
	}

	/** Asserts that the shutdown call is blocked. */
	public synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(5);
	    assertEquals("Expected no exception", null, exception);
	    assertFalse("Expected shutdown to be blocked", done);
	}
	
	/** Waits a while for the shutdown call to complete. */
	public synchronized boolean waitForDone() throws Exception {
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
	public synchronized void assertResult(boolean expectedResult)
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
}
