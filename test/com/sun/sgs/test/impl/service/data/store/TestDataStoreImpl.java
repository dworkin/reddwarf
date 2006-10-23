package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.test.DummyTransaction;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import junit.framework.TestCase;

/*
 * XXX: Test recovery
 * XXX: Test concurrent access
 */

/** Test the DataStoreImpl class */
public class TestDataStoreImpl extends TestCase {

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
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	dbDirectory);

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Creates the test. */
    public TestDataStoreImpl(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
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
	new File(directory).setReadOnly();
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
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.createObject(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateObjectAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	txn.abort();
	try {
	    store.createObject(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testCreateObjectPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	txn.prepare();
	try {
	    store.createObject(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testCreateObjectCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	txn.commit();
	try {
	    store.createObject(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testCreateObjectWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.createObject(txn2);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testCreateObjectSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	txn.abort();
    }

    /* -- Test markForUpdate -- */

    public void testMarkForUpdateNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.markForUpdate(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateBadId() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.markForUpdate(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testMarkForUpdateNotFound() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	try {
	    store.markForUpdate(txn, id);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testMarkForUpdateAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.abort();
	try {
	    store.markForUpdate(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdatePrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	try {
	    store.markForUpdate(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testMarkForUpdateCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	try {
	    store.markForUpdate(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.markForUpdate(txn2, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testMarkForUpdateSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.markForUpdate(txn, id);
	store.markForUpdate(txn, id);
	assertTrue(txn.participants.contains(store));
	/* Marking for update is not an update! */
	assertTrue(txn.prepare());
	txn.abort();
    }

    /* -- Test getObject -- */

    public void testGetObjectNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.getObject(null, 3, false);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetObjectBadId() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.getObject(txn, -3, false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testGetObjectNotFound() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testGetObjectAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.abort();
	try {
	    store.getObject(txn, id, false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetObjectPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	try {
	    store.getObject(txn, id, false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testGetObjectCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	try {
	    store.getObject(txn, id, false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetObjectWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.getObject(txn, id, false);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.getObject(txn2, id, false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testGetObjectSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	txn.abort();
    }

    /* -- Test setObject -- */

    public void testSetObjectNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	byte[] data = { 0 };
	try {
	    store.setObject(null, 3, data);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectBadId() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	byte[] data = { 0 };
	try {
	    store.setObject(txn, -3, data);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testSetObjectNullData() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	try {
	    store.setObject(txn, id, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testSetObjectEmptyData() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	byte[] data = { };
	store.setObject(txn, id, data);
	txn.commit();
	txn = new DummyTransaction();
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
	txn.abort();
    }

    public void testSetObjectAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	byte[] data = { 0 };
	txn.abort();
	try {
	    store.setObject(txn, id, data);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	byte[] data = { 0 };
	long id = store.createObject(txn);
	txn.prepare();
	try {
	    store.setObject(txn, id, data);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testSetObjectCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	byte[] data = { 0 };
	long id = store.createObject(txn);
	txn.commit();
	try {
	    store.setObject(txn, id, data);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	long id2 = store.createObject(txn);
	byte[] data = { 0 };
	try {
	    store.setObject(txn2, id, data);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testSetObjectSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	txn.abort();
    }

    /* -- Test removeObject -- */

    public void testRemoveObjectNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.removeObject(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectBadId() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.removeObject(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveObjectNotFound() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	try {
	    store.removeObject(txn, id);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveObjectAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.abort();
	try {
	    store.removeObject(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	try {
	    store.removeObject(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveObjectCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	try {
	    store.removeObject(txn, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.removeObject(txn2, id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testRemoveObjectSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.getBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingNullName() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.getBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testGetBindingEmptyName() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "", id);
	txn.commit();
	txn = new DummyTransaction();
	try {
	    long result = store.getBinding(txn, "");
	    assertEquals(id, result);
	} finally {
	    txn.abort();
	}
    }

    public void testGetBindingNotFound() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.getBinding(txn, "unknown");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	} finally {
	    txn.abort();
	}
    }

    public void testGetBindingObjectNotFound() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	try {
	    long result = store.getBinding(txn, "foo");
	    assertEquals(id, result);
	} finally {
	    txn.abort();
	}
    }

    public void testGetBindingAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.abort();
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.prepare();
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testGetBindingCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.commit();
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.getBinding(txn2, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testGetBindingSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
	assertTrue(txn.prepare());
	txn.abort();
    }

    /* -- Test setBinding -- */

    public void testSetBindingNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.setBinding(null, "foo", 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNullName() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	try {
	    store.setBinding(txn, null, id);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testSetBindingAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	txn.abort();
	try {
	    store.setBinding(txn, "foo", id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	txn.prepare();
	try {
	    store.setBinding(txn, "foo", id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testSetBindingCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	txn.commit();
	try {
	    store.setBinding(txn, "foo", id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	txn.commit();
	txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.setBinding(txn2, "foo", id);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testSetBindingSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	txn.abort();	
    }

    /* -- Test removeBinding -- */

    public void testRemoveBindingNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.removeBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingNullName() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.removeBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveBindingNotFound() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	try {
	    store.removeBinding(txn, "unknown");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveBindingAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.abort();
	try {
	    store.removeBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.prepare();
	try {
	    store.removeBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testRemoveBindingCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.commit();
	try {
	    store.removeBinding(txn, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = new DummyTransaction();
	store.createObject(txn);
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    store.removeBinding(txn2, "foo");
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    public void testRemoveBindingSuccess() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
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
	} finally {
	    txn.abort();
	}
    }

    /* -- Test abort -- */

    public void testAbortNullTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.abort(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testAbortAborted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.abort();
	try {
	    participant.abort(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAbortPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	participant.prepare(txn);
	participant.abort(txn);
    }

    public void testAbortCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.commit();
	try {
	    participant.abort(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAbortWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    participant.abort(txn2);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    /* -- Test prepare -- */

    public void testPrepareNullTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.prepare(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testPrepareAborted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.abort();
	try {
	    participant.prepare(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testPreparePrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	participant.prepare(txn);
	try {
	    participant.prepare(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testPrepareCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.commit();
	try {
	    participant.prepare(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testPrepareWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    participant.prepare(txn2);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    /* -- Test prepareAndCommit -- */

    public void testPrepareAndCommitNullTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.prepareAndCommit(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testPrepareAndCommitAborted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.abort();
	try {
	    participant.prepareAndCommit(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testPrepareAndCommitPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	participant.prepare(txn);
	try {
	    participant.prepareAndCommit(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testPrepareAndCommitCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.commit();
	try {
	    participant.prepareAndCommit(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testPrepareAndCommitWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    participant.prepareAndCommit(txn2);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    /* -- Test commit -- */

    public void testCommitNullTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	try {
	    participant.commit(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	}
    }

    public void testCommitAborted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.abort();
	try {
	    participant.commit(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testCommitPrepared() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	participant.prepare(txn);
	participant.commit(txn);
    }

    public void testCommitCommitted() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	txn.commit();
	try {
	    participant.commit(txn);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testCommitWrongTxn() throws Exception {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	store.createObject(txn);
	TransactionParticipant participant =
	    txn.participants.iterator().next();
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    participant.commit(txn2);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	} finally {
	    txn.abort();
	    txn2.abort();
	}
    }

    /* -- Test deadlock -- */

    public void testDeadlock() throws Exception {
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
	    FutureTask task = new FutureTask<Object>(myRunnable, null);
	    new Thread(task).start();
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
	    task.get();
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
}
