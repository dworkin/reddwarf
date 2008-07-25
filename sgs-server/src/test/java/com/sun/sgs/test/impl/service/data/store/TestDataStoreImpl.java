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

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.ClassInfoNotFoundException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.test.util.UtilReflection;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/*
 * XXX: Test recovery of prepared transactions after a crash
 * XXX: Test concurrent access
 */

/** Test the DataStoreImpl class */
public class TestDataStoreImpl extends TestCase {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() {
	if (testMethod == null) {
	    return new TestSuite(TestDataStoreImpl.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestDataStoreImpl(testMethod));
	return suite;
    }

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /** The DataStoreImpl.setObjectRaw(Transaction,long,byte[]) method. */
    private static final Method setObjectRaw =
	UtilReflection.getMethod(DataStoreImpl.class, "setObjectRaw",
				 Transaction.class, long.class, byte[].class);

    /** The DataStoreImpl.getObjectRaw(Transaction,long) method. */
    private static final Method getObjectRaw =
	UtilReflection.getMethod(DataStoreImpl.class, "getObjectRaw",
				 Transaction.class, long.class);

    /** The DataStoreImpl.nextObjectIdRaw(Transaction,long) method. */
    private static final Method nextObjectIdRaw =
	UtilReflection.getMethod(DataStoreImpl.class, "nextObjectIdRaw",
				 Transaction.class, long.class);

    /** The value of the DataStoreHeader.PLACEHOLDER_OBJ_VALUE field. */
    private static final byte PLACEHOLDER_OBJ_VALUE = 3;

    /** An instance of the data store, to test. */
    protected static DataStore store;

    /** Make sure an empty version of the directory exists. */
    static {
	cleanDirectory(dbDirectory);
    }

    /** Set when the test passes. */
    protected boolean passed;

    /** Default properties for creating the DataStore. */
    protected Properties props;

    /** An initial, open transaction. */
    protected DummyTransaction txn;

    /** The object ID of a newly created object. */
    protected long id;

    /** Creates the test. */
    public TestDataStoreImpl(String name) {
	super(name);
    }

    /** Prints the test case, and creates the data store and an object. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY, 10000);
	props = getProperties();
	if (store == null) {
	    store = createDataStore();
	}
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
		txn.abort(new RuntimeException("abort"));
	    }
	    if (!passed && store != null) {
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
		store = null;
	    }
	}
    }

    /* -- Tests -- */

    /* -- Test constructor -- */

    public void testConstructorNullArg() throws Exception {
	try {
	    createDataStore(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    /**
     * Tests that the {@code DataStore} correctly infers the database
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
        Properties props = createProperties(
            StandardProperties.APP_NAME, "Foo",
            StandardProperties.APP_ROOT, rootDir);
        DataStore testStore = createDataStore(props);
        testStore.shutdown();
    }

    public void testConstructorNoDirectoryNorRoot() throws Exception {
	Properties props = new Properties();
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
	String directory = createDirectory();
	props.setProperty(DataStoreImplClassName + ".directory", directory);
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

    public void testMarkForUpdatePlaceholder() throws Exception {
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	createPlaceholder(txn, id);
	for (int i = 0; i < 2; i++) {
	    if (i == 1) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    try {
		store.markForUpdate(txn, id);
		fail("Expected ObjectNotFoundException");
	    } catch (ObjectNotFoundException e) {
		System.err.println(e);
	    }
	}
	store.setObject(txn, id, new byte[0]);
	txn.commit();
	txn = null;
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

    /**
     * Test that we can store all values for the first data byte, since we're
     * now using that byte to mark placeholders.
     */
    public void testGetObjectFirstByte() throws Exception {
	byte[] value = new byte[0];
	store.setObject(txn, id, value);
	assertSameBytes(value, store.getObject(txn, id, false));
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertSameBytes(value, store.getObject(txn, id, false));
	long id2 = store.createObject(txn);
	for (int i = 0; i < 256; i++) {
	    value = new byte[] { (byte) i };
	    byte[] value2 = new byte[] { (byte) i, (byte) 33 };
	    store.setObject(txn, id, value);
	    store.setObject(txn, id2, value2);
	    assertSameBytes(value, store.getObject(txn, id, false));
	    assertSameBytes(value2, store.getObject(txn, id2, false));
	    txn.commit();
	    txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertSameBytes(value, store.getObject(txn, id, false));
	    assertSameBytes(value2, store.getObject(txn, id2, false));
	}
    }

    public void testGetObjectPlaceholder() throws Exception {
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	createPlaceholder(txn, id);
	for (int i = 0; i < 2; i++) {
	    if (i == 1) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    try {
		store.getObject(txn, id, false);
		fail("Expected ObjectNotFoundException");
	    } catch (ObjectNotFoundException e) {
		System.err.println(e);
	    }
	    try {
		store.getObject(txn, id, true);
		fail("Expected ObjectNotFoundException");
	    } catch (ObjectNotFoundException e) {
		System.err.println(e);
	    }
	}
	store.setObject(txn, id, new byte[0]);
	txn.commit();
	txn = null;
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
	txn.abort(new RuntimeException("abort"));
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	store.setObject(txn, id, newData);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
    }

    public void testSetObjectPlaceholder() throws Exception {
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	for (int i = 0; i < 2; i++) {
	    id = store.createObject(txn);
	    createPlaceholder(txn, id);
	    if (i == 1) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    byte[] value = { 1, 2 };
	    store.setObject(txn, id, value);
	    assertSameBytes(value, store.getObject(txn, id, false));
	}
	txn.commit();
	txn = null;
    }

    /* -- Test setObjects -- */

    public void testSetObjectsNullTxn() {
	long[] ids = { id };
	byte[][] dataArray = { { 0 } };
	try {
	    store.setObjects(null, ids, dataArray);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsBadId() {
	long[] ids = { -3 };
	byte[][] dataArray = { { 0 } };
	try {
	    store.setObjects(txn, ids, dataArray);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsWrongLengths() {
	long[] ids = { id };
	byte[][] dataArray = { { 0 }, { 1 } };
	try {
	    store.setObjects(txn, ids, dataArray);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsNullOids() {
	byte[][] dataArray = { { 0 } };
	try {
	    store.setObjects(txn, null, dataArray);
	    fail("Expected NullArgumentException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsNullDataArray() {
	long[] ids = { id };
	try {
	    store.setObjects(txn, ids, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsNullData() {
	long[] ids = { id };
	byte[][] dataArray = { null };
	try {
	    store.setObjects(txn, ids, dataArray);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectsEmptyData() throws Exception {
	long[] ids = { id };
	byte[][] dataArray = { { } };
	store.setObjects(txn, ids, dataArray);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
    }

    /* -- Unusual states -- */
    private final Action setObjects = new Action() {
	void run() {
	    store.setObjects(txn, new long[] { id }, new byte[][] { { 0 } });
	}
    };
    public void testSetObjectsAborted() throws Exception {
	testAborted(setObjects);
    }
    public void testSetObjectsPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setObjects);
    }
    public void testSetObjectsPreparedModified() throws Exception {
	testPreparedModified(setObjects);
    }
    public void testSetObjectsCommitted() throws Exception {
	testCommitted(setObjects);
    }
    public void testSetObjectsWrongTxn() throws Exception {
	testWrongTxn(setObjects);
    }
    public void testSetObjectsShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setObjects);
    }
    public void testSetObjectsShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setObjects);
    }
    public void testSetObjectsShutdown() throws Exception {
	testShutdown(setObjects);
    }

    public void testSetObjectsSuccess() throws Exception {
	long[] ids = { id, store.createObject(txn) };
	byte[][] dataArray = { { 1, 2 }, { 3, 4, 5 } };
	store.setObjects(txn, ids, dataArray);
	assertFalse(txn.prepare());
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	for (int i = 0; i < ids.length; i++) {
	    long id = ids[i];
	    byte[] data = dataArray[i];
	    byte[] result = store.getObject(txn, id, false);
	    assertTrue(Arrays.equals(data, result));
	    byte[] newData = new byte[] { (byte) i };
	    store.setObjects(txn, new long[] { id }, new byte[][] { newData });
	    assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	    txn.abort(new RuntimeException("abort"));
	    txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	    store.setObjects(txn, new long[] { id }, new byte[][] { newData });
	    txn.commit();
	    txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	}
    }

    public void testSetObjectsPlaceholder() throws Exception {
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	for (int i = 0; i < 2; i++) {
	    long[] ids = { store.createObject(txn), store.createObject(txn) };
	    byte[][] dataArray = { { 1, 2 }, { 3, 4, 5 } };
	    createPlaceholder(txn, ids[0]);
	    createPlaceholder(txn, ids[1]);
	    if (i == 1) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObjects(txn, ids, dataArray);
	    assertSameBytes(dataArray[0], store.getObject(txn, ids[0], false));
	    assertSameBytes(dataArray[1], store.getObject(txn, ids[1], false));
	}
	txn.commit();
	txn = null;
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
	txn.abort(new RuntimeException("abort"));
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

    public void testRemoveObjectPlaceholder() throws Exception {
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	createPlaceholder(txn, id);
	for (int i = 0; i < 2; i++) {
	    if (i == 1) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    try {
		store.removeObject(txn, id);
		fail("Expected ObjectNotFoundException");
	    } catch (ObjectNotFoundException e) {
		System.err.println(e);
	    }
	}
	store.setObject(txn, id, new byte[0]);
	txn.commit();
	txn = null;
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
	txn.abort(new RuntimeException("abort"));
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
	txn.abort(new RuntimeException("abort"));
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
	txn.abort(new RuntimeException("abort"));
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
	testAborted(abort, IllegalStateException.class);
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

    public void testPrepareTimeout() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO, 100);
	store.setObject(txn, id, new byte[] { 1 });
	Thread.sleep(200);
	try {
	    txn.commit();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    txn = null;
	    System.err.println(e);
	}
	/* Wait for transaction to end on the server. */
	Thread.sleep(1000);
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
	testAborted(prepare, IllegalStateException.class);
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

    public void testPrepareAndCommitTimeout() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.YES, 100);
	store.setObject(txn, id, new byte[] { 1 });
	Thread.sleep(200);
	try {
	    txn.commit();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    txn = null;
	    System.err.println(e);
	}
	/* Wait for transaction to end on the server. */
	Thread.sleep(1000);
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
	testAborted(prepareAndCommit, IllegalStateException.class);
    }
    public void testPrepareAndCommitPrepareAndCommitReadOnly()
	throws Exception
    {
	testPreparedReadOnly(prepareAndCommit);
    }
    public void testPrepareAndCommitPrepareAndCommitModified()
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

    /** Make sure that commits don't timeout. */
    public void testCommitNoTimeout() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO, 1000) {
	    public void join(final TransactionParticipant participant) {
		super.join(new TransactionParticipant() {
		    public boolean prepare(Transaction txn) throws Exception {
			return participant.prepare(txn);
		    }
		    public void commit(Transaction txn) {
			try {
			    Thread.sleep(2000);
			    participant.commit(txn);
			} catch (Exception e) {
			    e.printStackTrace();
			    fail("Unexpected exception: " + e);
			}
		    }
		    public void prepareAndCommit(Transaction txn)
			throws Exception
		    {
			participant.prepareAndCommit(txn);
		    }
		    public void abort(Transaction txn) {
			participant.abort(txn);
		    }
                    public String getTypeName() {
                        return "DataStoreDummyParticipant";
                    }
		});
	    }
	};
	store.setBinding(txn, "foo", id);
	try {
	    txn.commit();
	} finally {
	    txn = null;
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
	testAborted(commit, IllegalStateException.class);
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
	txn.abort(new RuntimeException("abort"));
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
	/* Complete the shutdown */
	new ShutdownAction().waitForDone();
	store = null;
    }

    public void testConcurrentShutdownInterrupt() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	action1.interrupt();
	action1.assertResult(false);
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
	action2.assertResult(true);
	txn = null;
	store = null;
    }

    public void testConcurrentShutdownRace() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
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
	store = createDataStore();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	id = store.getBinding(txn, "foo");
	byte[] value = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(bytes, value));
    }

    /* -- Test getClassId -- */

    public void testGetClassIdNullArgs() {
	byte[] bytes = { 0, 1, 2, 3, 4 };
	try {
	    store.getClassId(null, bytes);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    store.getClassId(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetClassIdNonStandardBytes() throws Exception {
	testGetClassIdBytes();
	testGetClassIdBytes((byte) 1);
	testGetClassIdBytes((byte) 2);
	testGetClassIdBytes((byte) 3, (byte) 4);
	testGetClassIdBytes((byte) 5, (byte) 6, (byte) 0xff);
	testGetClassIdBytes((byte) 0xac, (byte) 0xed, (byte) 0, (byte) 5);
	testGetClassIdBytes((byte) 0xac, (byte) 0xed, (byte) 0, (byte) 4);
    }
	
    private void testGetClassIdBytes(byte... bytes) throws Exception {
	int id = store.getClassId(txn, bytes);
	assertTrue(id > 0);
	byte[] storedBytes = store.getClassInfo(txn, id);
	assertEquals(Arrays.toString(bytes), Arrays.toString(storedBytes));
    }

    /* -- Unusual states: getClassId -- */
    private final Action getClassId = new Action() {
	void run() { store.getClassId(txn, new byte[0]); };
    };
    public void testGetClassIdAborted() throws Exception {
	testAborted(getClassId);
    }
    public void testGetClassIdPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getClassId);
    }
    public void testGetClassIdPreparedModified() throws Exception {
	testPreparedModified(getClassId);
    }
    public void testGetClassIdCommitted() throws Exception {
	testCommitted(getClassId);
    }
    public void testGetClassIdWrongTxn() throws Exception {
	testWrongTxn(getClassId);
    }
    public void testGetClassIdShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getClassId);
    }
    public void testGetClassIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getClassId);
    }
    public void testGetClassIdShutdown() throws Exception {
	testShutdown(getClassId);
    }

    /* -- Test getClassInfo -- */

    public void testGetClassInfoBadArgs() throws Exception {
	try {
	    store.getClassInfo(null, 1);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    store.getClassInfo(txn, 0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    store.getClassInfo(txn, -1);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testGetClassInfoNotFound() {
	try {
	    store.getClassInfo(txn, 56789);
	    fail("Expected ClassInfoNotFoundException");
	} catch (ClassInfoNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetClassInfoAfterRestart() throws Exception {
	byte[] bytes = { 1, 2, 3, 4, 5, 6 };
	int id = store.getClassId(txn, bytes);
	txn.commit();
	store.shutdown();
	store = createDataStore();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(Arrays.toString(bytes),
		     Arrays.toString(store.getClassInfo(txn, id)));
    }

    /* -- Unusual states: getClassInfo -- */
    private final Action getClassInfo = new Action() {
	void run() throws Exception { store.getClassInfo(txn, 1); };
    };
    public void testGetClassInfoAborted() throws Exception {
	testAborted(getClassInfo);
    }
    public void testGetClassInfoPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getClassInfo);
    }
    public void testGetClassInfoPreparedModified() throws Exception {
	testPreparedModified(getClassInfo);
    }
    public void testGetClassInfoCommitted() throws Exception {
	testCommitted(getClassInfo);
    }
    public void testGetClassInfoWrongTxn() throws Exception {
	testWrongTxn(getClassInfo);
    }
    public void testGetClassInfoShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getClassInfo);
    }
    public void testGetClassInfoShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getClassInfo);
    }
    public void testGetClassInfoShutdown() throws Exception {
	testShutdown(getClassInfo);
    }

    /* -- Test nextObjectId -- */

    public void testNextObjectIdIllegalIds() {
	long id = Long.MIN_VALUE;
	try {
	    store.nextObjectId(txn, id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(id);
	}
	id = -2;
	try {
	    store.nextObjectId(txn, id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(id);
	}
    }

    public void testNextObjectIdBoundaryIds() {
	long first = store.nextObjectId(txn, -1);
	assertEquals(first, store.nextObjectId(txn, -1));
	assertEquals(first, store.nextObjectId(txn, 0));
	long last = -1;
	while (true) {
	    long id = store.nextObjectId(txn, last);
	    if (id == -1) {
		break;
	    }
	    last = id;
	}
	assertEquals(-1, store.nextObjectId(txn, last));
	assertEquals(-1, store.nextObjectId(txn, Long.MAX_VALUE));
    }

    public void testNextObjectIdRemoved() throws Exception {
	long x = -1;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    if (x == -1) {
		break;
	    }
	    assertFalse("Shouldn't find ID that has been created but not set",
			x == id);
	}
	store.setObject(txn, id, new byte[] { 1, 2, 3 });
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	long id2 = store.createObject(txn);
	store.setObject(txn, id2, new byte[] { 4, 5, 6, 7 });
	if (id > id2) {
	    long tmp = id;
	    id = id2;
	    id2 = tmp;
	}
	x = id;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    assertFalse("Didn't find id2 after id", x == -1);
	    if (x == id2) {
		break;
	    }
	}
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeObject(txn, id);
	x = -1;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    if (x == -1) {
		break;
	    }
	    assertFalse("Shouldn't find ID removed in this txn", x == id);
	}
	x = id;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    assertFalse("Didn't find id2 after removed id", x == -1);
	    if (x == id2) {
		break;
	    }
	}
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	x = -1;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    if (x == -1) {
		break;
	    }
	    assertFalse("Shouldn't find ID removed in last txn", x == id);
	}
	x = id;
	while (true) {
	    x = store.nextObjectId(txn, x);
	    assertFalse("Didn't find id2 after removed id", x == -1);
	    if (x == id2) {
		break;
	    }
	}
    }

    /* -- Unusual states: nextObjectId -- */
    private final Action nextObjectId = new Action() {
	void run() throws Exception { store.nextObjectId(txn, -1); };
    };
    public void testNextObjectIdAborted() throws Exception {
	testAborted(nextObjectId);
    }
    public void testNextObjectIdPreparedReadOnly() throws Exception {
	testPreparedReadOnly(nextObjectId);
    }
    public void testNextObjectIdPreparedModified() throws Exception {
	testPreparedModified(nextObjectId);
    }
    public void testNextObjectIdCommitted() throws Exception {
	testCommitted(nextObjectId);
    }
    public void testNextObjectIdWrongTxn() throws Exception {
	testWrongTxn(nextObjectId);
    }
    public void testNextObjectIdShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextObjectId);
    }
    public void testNextObjectIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextObjectId);
    }
    public void testNextObjectIdShutdown() throws Exception {
	testShutdown(nextObjectId);
    }

    /* -- Test deadlock -- */
    @SuppressWarnings("hiding")
    public void testDeadlock() throws Exception {
	for (int i = 0; i < 5; i++) {
	    if (i > 0) {
		txn = new DummyTransaction(
		    UsePrepareAndCommit.ARBITRARY, 1000);
	    }
	    final long id = store.createObject(txn);
	    store.setObject(txn, id, new byte[] { 0 });
	    final long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = new DummyTransaction(
		UsePrepareAndCommit.ARBITRARY, 1000);
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
			    UsePrepareAndCommit.ARBITRARY, 1000);
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
			    txn2.abort(new RuntimeException("abort"));
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

    /* -- Other tests -- */

    /** Test that allocation block placeholders get removed at startup. */
    public void testRemoveAllocationPlaceholders() throws Exception {
	/*
	 * Only run this test against a local data store because it requires
	 * shutting the data store down, and we can only do that with a local
	 * one.  -tjb@sun.com (07/16/2008)
	 */
	if (!(store instanceof DataStoreImpl)) {
	    return;
	}
	/* Create objects but don't create data */
	for (int i = 0; i < 1025; i++) {
	    if (i % 25 == 0) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.createObject(txn);
	}
	/* Create objects but abort the last ones in the block */
	for (int i = 1025; i < 2050; i++) {
	    if (i % 25 == 0) {
		if (i == 2025) {
		    txn.abort(new RuntimeException("abort"));
		} else {
		    txn.commit();
		}
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObject(
		txn, store.createObject(txn), new byte[] { (byte) i });
	}
	/* Create objects */
	for (int i = 2050; i < 3075; i++) {
	    if (i % 25 == 0) {
		txn.commit();
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObject(
		txn, store.createObject(txn), new byte[] { (byte) i });
	}
	txn.commit();
	txn = null;
	store.shutdown();
	store = createDataStore();
	long nextId = -1;
	for (int i = 0; true; i++) {
	    if (i % 40 == 0) {
		if (txn != null) {
		    txn.commit();
		}
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    nextId = nextObjectIdRaw(txn, nextId);
	    if (nextId < 0) {
		break;
	    }
	    byte[] value = getObjectRaw(txn, nextId);
	    if (value != null &&
		value.length > 0 &&
		value[0] == PLACEHOLDER_OBJ_VALUE)
	    {
		fail("Found placeholder at object ID " + nextId);
	    }
	}
    }

    /* -- Other methods and classes -- */

    /** Creates a unique directory. */
    private String createDirectory() throws IOException {
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

    /** Creates a DataStore using the default properties. */
    protected DataStore createDataStore() throws Exception {
	return createDataStore(props);
    }

    /** Creates a DataStore using the specified properties. */
    protected DataStore createDataStore(Properties props) throws Exception {
	return new DataStoreImpl(props);
    }

    /** Returns the default properties to use for creating data stores. */
    protected Properties getProperties() throws Exception {
	return createProperties(
	    DataStoreImplClassName + ".directory", dbDirectory);
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
	testAborted(action, TransactionNotActiveException.class);
    }

    /**
     * Tests running the action after abort, and expecting to get the
     * specified exception type.
     */
    void testAborted(Action action, Class<? extends Exception> exceptionType)
	throws Exception
    {
	action.setUp();
	txn.abort(new RuntimeException("abort"));
	try {
	    action.run();
	    fail("Expected exception");
	} catch (Exception e) {
	    if (exceptionType.isInstance(e)) {
		System.err.println(e);
	    } else {
		fail("Expected " + exceptionType + ": " + e);
	    }
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
	    fail("Expected exception");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
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
	    fail("Expected exception");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
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
	    originalTxn.abort(new RuntimeException("abort"));
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
    void testShuttingDownNewTxn(final Action action) throws Exception {
	action.setUp();
	DummyTransaction originalTxn = txn;
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	final AtomicReference<Throwable> exceptionHolder =
	    new AtomicReference<Throwable>();
	Thread t = new Thread() {
	    public void run() {
		txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
		try {
		    action.run();
		} catch (Throwable t) {
		    exceptionHolder.set(t);
		}
		txn.abort(new RuntimeException("abort"));
		txn = null;
	    }
	};
	t.start();
	t.join(1000);
	Throwable exception = exceptionHolder.get();
	assertTrue("Expected IllegalStateException: " + exception,
		   exception instanceof IllegalStateException);
	originalTxn.abort(new RuntimeException("abort"));
	shutdownAction.assertResult(true);
	store = null;
    }

    /** Tests running the action after shutdown. */
    void testShutdown(Action action) throws Exception {
	action.setUp();
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	try {
	    action.run();
	    fail("Expected exception");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
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

    /** Assert that the two byte arrays are the same. */
    private static void assertSameBytes(byte[] x, byte[] y) {
	if (!Arrays.equals(x, y)) {
	    fail("Expected " + Arrays.toString(x) + ", got " +
		 Arrays.toString(y));
	}
    }

    /** Creates a placeholder at the specified object ID. */
    private static void createPlaceholder(Transaction txn, long oid) {
	setObjectRaw(txn, oid, new byte[] { PLACEHOLDER_OBJ_VALUE });
    }

    /** Calls DataStoreImpl.setObjectRaw. */
    private static void setObjectRaw(
	Transaction txn, long oid, byte[] data)
    {
	try {
	    setObjectRaw.invoke((DataStoreImpl) store, txn, oid, data);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }

    /** Calls DataStoreImpl.getObjectRaw. */
    private static byte[] getObjectRaw(Transaction txn, long oid) {
	try {
	    return (byte[]) getObjectRaw.invoke(
		(DataStoreImpl) store, txn, oid);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }
	
    /** Calls DataStoreImpl.nextObjectIdRaw. */
    private static long nextObjectIdRaw(Transaction txn, long oid) {
	try {
	    return (Long) nextObjectIdRaw.invoke(
		(DataStoreImpl) store, txn, oid);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }
}
