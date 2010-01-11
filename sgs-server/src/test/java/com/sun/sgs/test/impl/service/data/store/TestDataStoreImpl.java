/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.test.util.DummyTransactionProxy;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/*
 * XXX: Test recovery of prepared transactions after a crash
 * XXX: Test concurrent access
 */

/** Test the DataStoreImpl class */
@RunWith(FilteredNameRunner.class)
public class TestDataStoreImpl extends Assert {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    protected static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /** The default basic test environment, or {@code null} if not set. */
    private static BasicDataStoreTestEnv defaultEnv = null;

    /** The basic test environment. */
    protected final BasicDataStoreTestEnv env;

    /** The transaction proxy. */
    protected final DummyTransactionProxy txnProxy;

    /** The access coordinator. */
    protected final AccessCoordinatorHandle accessCoordinator;

    /** The system registry. */
    protected final ComponentRegistry systemRegistry;

    /** An instance of the data store, to test. */
    protected static DataStore store;

    /** Default properties for creating the DataStore. */
    protected Properties props;

    /** An initial, open transaction. */
    protected DummyTransaction txn;

    /** The object ID of a newly created object. */
    protected long id;

    /** Creates the test. */
    public TestDataStoreImpl() {
	this(defaultEnv == null
	     ? defaultEnv = new BasicDataStoreTestEnv(System.getProperties())
	     : defaultEnv);
    }

    /** Creates the test using the specified basic test environment. */
    protected TestDataStoreImpl(BasicDataStoreTestEnv env) {
	this.env = env;
	txnProxy = env.txnProxy;
	accessCoordinator = env.accessCoordinator;	
	systemRegistry = env.systemRegistry;
    }	

    /** Make sure an empty version of the directory exists. */
    @BeforeClass
    public static void setUpBeforeClass() {
	cleanDirectory(dbDirectory);
    }

    /** Creates the data store and an object. */
    @Before
    public void setUp() throws Exception {
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY, 10000);
	props = getProperties();
	if (store == null) {
	    store = createDataStore();
	}
	id = store.createObject(txn);
    }

    /** Abort the current transaction, if non-null, and shutdown the store. */
    @After
    public void tearDown() throws Exception {
	try {
	    if (txn != null) {
		txn.abort(new RuntimeException("abort"));
	    }
	} catch (RuntimeException e) {
	    e.printStackTrace();
	    throw e;
	} finally {
            txn = null;
	}
    }

    /* -- Tests -- */

    /* -- Test constructor -- */

    @Test
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
    @Test
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

    @Test
    public void testConstructorNoDirectoryNorRoot() throws Exception {
	Properties props = new Properties();
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNonexistentDirectory() throws Exception {
        String directory = createDirectory();
        File dir = new File(directory);
        if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting directory: " + dir);
	}
	props.setProperty(
                DataStoreImplClassName + ".directory", directory);
        createDataStore(props);
        assertTrue(dir.exists());
    }

    @Test
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

    @Test
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
        File dir = new File(directory);
	dir.setReadOnly();
        if(dir.canWrite()) {
            System.err.println("Skipping with superuser");
            return;
        }
	try {
	    createDataStore(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    /* -- Test createObject -- */

    @Test
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
    @Test
    public void testCreateObjectAborted() throws Exception {
	testAborted(createObject);
    }
    @Test
    public void testCreateObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(createObject);
    }
    @Test
    public void testCreateObjectPreparedModified() throws Exception {
	testPreparedModified(createObject);
    }
    @Test
    public void testCreateObjectCommitted() throws Exception {
	testCommitted(createObject);
    }
    @Test
    public void testCreateObjectWrongTxn() throws Exception {
	testWrongTxn(createObject);
    }
    @Test
    public void testCreateObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(createObject);
    }
    @Test
    public void testCreateObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createObject);
    }
    @Test
    public void testCreateObjectShutdown() throws Exception {
	testShutdown(createObject);
    }

    @Test
    public void testCreateObjectSuccess() throws Exception {
	assertTrue(id >= 0);
	assertTrue(
	    txn.participants.contains(
		((DataStoreProfileProducer) store).getDataStore()));
	long id2 = store.createObject(txn);
	assertTrue(id2 >= 0);
	assertTrue(id != id2);
	/*
	 * Only setting the object causes the current transaction to contain
	 * modifications!  -tjb@sun.com (10/18/2006)
	 */
	assertTrue(txn.prepare());
    }

    @Test
    public void testCreateObjectMany() throws Exception {
	for (int i = 0; i < 10; i++) {
	    if (i != 0) {
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    for (int j = 0; j < 200; j++) {
		store.createObject(txn);
	    }
	    txn.commit();
	}
	txn = null;
    }

    /* -- Test markForUpdate -- */

    @Test
    public void testMarkForUpdateNullTxn() {
	try {
	    store.markForUpdate(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testMarkForUpdateBadId() {
	try {
	    store.markForUpdate(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testMarkForUpdateNotFound() throws Exception {
	store.markForUpdate(txn, id);
    }

    /* -- Unusual states -- */
    private final Action markForUpdate = new Action() {
	void setUp() throws Exception {
	    store.setObject(txn, id, new byte[] { 0 });
	    long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getObject(txn, id2, false);
	}
	void run() { store.markForUpdate(txn, id); }
    };
    @Test
    public void testMarkForUpdateAborted() throws Exception {
	testAborted(markForUpdate);
    }
    @Test
    public void testMarkForUpdatePreparedReadOnly() throws Exception {
	testPreparedReadOnly(markForUpdate);
    }
    @Test
    public void testMarkForUpdatePreparedModified() throws Exception {
	testPreparedModified(markForUpdate);
    }
    @Test
    public void testMarkForUpdateCommitted() throws Exception {
	testCommitted(markForUpdate);
    }
    @Test
    public void testMarkForUpdateWrongTxn() throws Exception {
	testWrongTxn(markForUpdate);
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
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	store.markForUpdate(txn, id);
	store.markForUpdate(txn, id);
	assertTrue(
	    txn.participants.contains(
		((DataStoreProfileProducer) store).getDataStore()));
	/* Marking for update is not an update! */
	assertTrue(txn.prepare());
    }

    /* -- Test getObject -- */

    @Test
    public void testGetObjectNullTxn() {
	try {
	    store.getObject(null, 3, false);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetObjectBadId() {
	try {
	    store.getObject(txn, -3, false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
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
	void setUp() throws Exception {
	    store.setObject(txn, id, new byte[] { 0 });
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getObject(txn, id, false);
	}
	void run() { store.getObject(txn, id, false); };
    };
    @Test
    public void testGetObjectAborted() throws Exception {
	testAborted(getObject);
    }
    @Test
    public void testGetObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getObject);
    }
    @Test
    public void testGetObjectPreparedModified() throws Exception {
	testPreparedModified(getObject);
    }
    @Test
    public void testGetObjectCommitted() throws Exception {
	testCommitted(getObject);
    }
    @Test
    public void testGetObjectWrongTxn() throws Exception {
	testWrongTxn(getObject);
    }
    @Test
    public void testGetObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getObject);
    }
    @Test
    public void testGetObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getObject);
    }
    @Test
    public void testGetObjectShutdown() throws Exception {
	testShutdown(getObject);
    }

    @Test
    public void testGetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result =	store.getObject(txn, id, false);
	assertTrue(
	    txn.participants.contains(
		((DataStoreProfileProducer) store).getDataStore()));
	assertTrue(Arrays.equals(data, result));
	store.getObject(txn, id, false);
	/* Getting for update is not an update! */
	assertTrue(txn.prepare());
    }

    /**
     * Test that we can store all values for the first data byte, since we're
     * now using that byte to mark placeholders.
     */
    @Test
    public void testGetObjectFirstByte() throws Exception {
	byte[] value = new byte[0];
	store.setObject(txn, id, value);
	assertSameBytes(value, store.getObject(txn, id, false));
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertSameBytes(value, store.getObject(txn, id, false));
	    assertSameBytes(value2, store.getObject(txn, id2, false));
	}
    }

    /* -- Test setObject -- */

    @Test
    public void testSetObjectNullTxn() {
	byte[] data = { 0 };
	try {
	    store.setObject(null, 3, data);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testSetObjectBadId() {
	byte[] data = { 0 };
	try {
	    store.setObject(txn, -3, data);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testSetObjectNullData() {
	try {
	    store.setObject(txn, id, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testSetObjectEmptyData() throws Exception {
	byte[] data = { };
	store.setObject(txn, id, data);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
    }

    /* -- Unusual states -- */
    private final Action setObject = new Action() {
	void run() { store.setObject(txn, id, new byte[] { 0 }); }
    };
    @Test
    public void testSetObjectAborted() throws Exception {
	testAborted(setObject);
    }
    @Test
    public void testSetObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setObject);
    }
    @Test
    public void testSetObjectPreparedModified() throws Exception {
	testPreparedModified(setObject);
    }
    @Test
    public void testSetObjectCommitted() throws Exception {
	testCommitted(setObject);
    }
    @Test
    public void testSetObjectWrongTxn() throws Exception {
	testWrongTxn(setObject);
    }
    @Test
    public void testSetObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setObject);
    }
    @Test
    public void testSetObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setObject);
    }
    @Test
    public void testSetObjectShutdown() throws Exception {
	testShutdown(setObject);
    }

    @Test
    public void testSetObjectSuccess() throws Exception {
	byte[] data = { 1, 2 };
	store.setObject(txn, id, data);
	assertFalse(txn.prepare());
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(data, result));
	byte[] newData = new byte[] { 3 };
	store.setObject(txn, id, newData);
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	txn.abort(new RuntimeException("abort"));
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	store.setObject(txn, id, newData);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
    }

    /* -- Test setObjects -- */

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testSetObjectsNullOids() {
	byte[][] dataArray = { { 0 } };
	try {
	    store.setObjects(txn, null, dataArray);
	    fail("Expected NullArgumentException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testSetObjectsNullDataArray() {
	long[] ids = { id };
	try {
	    store.setObjects(txn, ids, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
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

    @Test
    public void testSetObjectsEmptyData() throws Exception {
	long[] ids = { id };
	byte[][] dataArray = { { } };
	store.setObjects(txn, ids, dataArray);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	byte[] result = store.getObject(txn, id, false);
	assertTrue(result.length == 0);
    }

    /* -- Unusual states -- */
    private final Action setObjects = new Action() {
	void run() {
	    store.setObjects(txn, new long[] { id }, new byte[][] { { 0 } });
	}
    };
    @Test
    public void testSetObjectsAborted() throws Exception {
	testAborted(setObjects);
    }
    @Test
    public void testSetObjectsPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setObjects);
    }
    @Test
    public void testSetObjectsPreparedModified() throws Exception {
	testPreparedModified(setObjects);
    }
    @Test
    public void testSetObjectsCommitted() throws Exception {
	testCommitted(setObjects);
    }
    @Test
    public void testSetObjectsWrongTxn() throws Exception {
	testWrongTxn(setObjects);
    }
    @Test
    public void testSetObjectsShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setObjects);
    }
    @Test
    public void testSetObjectsShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setObjects);
    }
    @Test
    public void testSetObjectsShutdown() throws Exception {
	testShutdown(setObjects);
    }

    @Test
    public void testSetObjectsSuccess() throws Exception {
	long[] ids = { id, store.createObject(txn) };
	byte[][] dataArray = { { 1, 2 }, { 3, 4, 5 } };
	store.setObjects(txn, ids, dataArray);
	assertFalse(txn.prepare());
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	for (int i = 0; i < ids.length; i++) {
	    long id = ids[i];
	    byte[] data = dataArray[i];
	    byte[] result = store.getObject(txn, id, false);
	    assertTrue(Arrays.equals(data, result));
	    byte[] newData = new byte[] { (byte) i };
	    store.setObjects(txn, new long[] { id }, new byte[][] { newData });
	    assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	    txn.abort(new RuntimeException("abort"));
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertTrue(Arrays.equals(data, store.getObject(txn, id, true)));
	    store.setObjects(txn, new long[] { id }, new byte[][] { newData });
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    assertTrue(Arrays.equals(newData, store.getObject(txn, id, true)));
	}
    }

    /* -- Test removeObject -- */

    @Test
    public void testRemoveObjectNullTxn() {
	try {
	    store.removeObject(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveObjectBadId() {
	try {
	    store.removeObject(txn, -3);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
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
	void setUp() throws Exception {
	    store.setObject(txn, id, new byte[] { 0 });
	    long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getObject(txn, id2, false);
	}
	void run() { store.removeObject(txn, id); }
    };
    @Test
    public void testRemoveObjectAborted() throws Exception {
	testAborted(removeObject);
    }
    @Test
    public void testRemoveObjectPreparedReadOnly() throws Exception {
	testPreparedReadOnly(removeObject);
    }
    @Test
    public void testRemoveObjectPreparedModified() throws Exception {
	testPreparedModified(removeObject);
    }
    @Test
    public void testRemoveObjectCommitted() throws Exception {
	testCommitted(removeObject);
    }
    @Test
    public void testRemoveObjectWrongTxn() throws Exception {
	testWrongTxn(removeObject);
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
	store.setObject(txn, id, new byte[] { 0 });
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeObject(txn, id);
	assertFalse(txn.prepare());
	txn.abort(new RuntimeException("abort"));
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeObject(txn, id);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
    }

    /* -- Test getBinding -- */

    @Test
    public void testGetBindingNullTxn() {
	try {
	    store.getBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetBindingNullName() {
	try {
	    store.getBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetBindingEmptyName() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "", id);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	long result = store.getBinding(txn, "");
	assertEquals(id, result);
    }

    @Test
    public void testGetBindingNotFound() throws Exception {
	try {
	    store.getBinding(txn, "unknown");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetBindingObjectNotFound() throws Exception {
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
    }

    /* -- Unusual states -- */
    private final Action getBinding = new Action() {
	void setUp() throws Exception {
	    store.setBinding(txn, "foo", id);
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getBinding(txn, "foo");
	}
	void run() { store.getBinding(txn, "foo"); }
    };
    @Test
    public void testGetBindingAborted() throws Exception {
	testAborted(getBinding);
    }
    @Test
    public void testGetBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getBinding);
    }
    @Test
    public void testGetBindingPreparedModified() throws Exception {
	testPreparedModified(getBinding);
    }
    @Test
    public void testGetBindingCommitted() throws Exception {
	testCommitted(getBinding);
    }
    @Test
    public void testGetBindingWrongTxn() throws Exception {
	testWrongTxn(getBinding);
    }
    @Test
    public void testGetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getBinding);
    }
    @Test
    public void testGetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getBinding);
    }
    @Test
    public void testGetBindingShutdown() throws Exception {
	testShutdown(getBinding);
    }

    @Test
    public void testGetBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	long result = store.getBinding(txn, "foo");
	assertEquals(id, result);
	assertTrue(txn.prepare());
    }

    /* -- Test setBinding -- */

    @Test
    public void testSetBindingNullTxn() {
	try {
	    store.setBinding(null, "foo", 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
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
    @Test
    public void testSetBindingAborted() throws Exception {
	testAborted(setBinding);
    }
    @Test
    public void testSetBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(setBinding);
    }
    @Test
    public void testSetBindingPreparedModified() throws Exception {
	testPreparedModified(setBinding);
    }
    @Test
    public void testSetBindingCommitted() throws Exception {
	testCommitted(setBinding);
    }
    @Test
    public void testSetBindingWrongTxn() throws Exception {
	testWrongTxn(setBinding);
    }
    @Test
    public void testSetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setBinding);
    }
    @Test
    public void testSetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setBinding);
    }
    @Test
    public void testSetBindingShutdown() throws Exception {
	testShutdown(setBinding);
    }

    @Test
    public void testSetBindingSuccess() throws Exception {
	long newId = store.createObject(txn);
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	assertFalse(txn.prepare());
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(id, store.getBinding(txn, "foo"));
	store.setBinding(txn, "foo", newId);
	assertEquals(newId, store.getBinding(txn, "foo"));
	txn.abort(new RuntimeException("abort"));
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(id, store.getBinding(txn, "foo"));
    }

    /* -- Test removeBinding -- */

    @Test
    public void testRemoveBindingNullTxn() {
	try {
	    store.removeBinding(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveBindingNullName() {
	try {
	    store.removeBinding(txn, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
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
	void setUp() throws Exception {
	    store.setBinding(txn, "foo", id); 
	    store.setObject(txn, id, new byte[] { 0 });
	    txn.commit();
	    txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    store.getObject(txn, id, false);
	}
	void run() { store.removeBinding(txn, "foo"); }
    };
    @Test
    public void testRemoveBindingAborted() throws Exception {
	testAborted(removeBinding);
    }
    @Test
    public void testRemoveBindingPreparedReadOnly() throws Exception {
	testPreparedReadOnly(removeBinding);
    }
    @Test
    public void testRemoveBindingPreparedModified() throws Exception {
	testPreparedModified(removeBinding);
    }
    @Test
    public void testRemoveBindingCommitted() throws Exception {
	testCommitted(removeBinding);
    }
    @Test
    public void testRemoveBindingWrongTxn() throws Exception {
	testWrongTxn(removeBinding);
    }
    @Test
    public void testRemoveBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeBinding);
    }
    @Test
    public void testRemoveBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeBinding);
    }
    @Test
    public void testRemoveBindingShutdown() throws Exception {
	testShutdown(removeBinding);
    }

    @Test
    public void testRemoveBindingSuccess() throws Exception {
	store.setObject(txn, id, new byte[] { 0 });
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	store.removeBinding(txn, "foo");
	assertFalse(txn.prepare());
	txn.abort(new RuntimeException("abort"));
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(id, store.getBinding(txn, "foo"));
	store.removeBinding(txn, "foo");
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	try {
	    store.getBinding(txn, "foo");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Test nextBoundName -- */

    @Test
    public void testNextBoundNameNullTxn() {
	try {
	    store.nextBoundName(null, "foo");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
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
    @Test
    public void testNextBoundNameAborted() throws Exception {
	testAborted(nextBoundName);
    }
    @Test
    public void testNextBoundNamePreparedReadOnly() throws Exception {
	testPreparedReadOnly(nextBoundName);
    }
    @Test
    public void testNextBoundNamePreparedModified() throws Exception {
	testPreparedModified(nextBoundName);
    }
    @Test
    public void testNextBoundNameCommitted() throws Exception {
	testCommitted(nextBoundName);
    }
    @Test
    public void testNextBoundNameWrongTxn() throws Exception {
	testWrongTxn(nextBoundName);
    }
    @Test
    public void testNextBoundNameShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextBoundName);
    }
    @Test
    public void testNextBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextBoundName);
    }
    @Test
    public void testNextBoundNameShutdown() throws Exception {
	testShutdown(nextBoundName);
    }

    @Test
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
	txn = createTransaction();
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
	txn = createTransaction();
	assertEquals("name-1", store.nextBoundName(txn, null));
	assertEquals("name-1", store.nextBoundName(txn, null));
	store.removeBinding(txn, "name-1");
	assertEquals("name-2", store.nextBoundName(txn, null));
	store.removeBinding(txn, "name-2");
	assertNull(store.nextBoundName(txn, null));
	txn.abort(new RuntimeException("abort"));
	txn = createTransaction();
	assertEquals("name-1", store.nextBoundName(txn, null));
    }

    @Test
    public void testNextBoundNameTimeout() throws Exception {
	final long id2 = store.createObject(txn);
	for (int i = 100; i < 300; i++) {
	    store.setBinding(txn, "name-" + i, id);
	}
	txn.commit();
	final Semaphore flag = new Semaphore(1);
	final Semaphore flag2 = new Semaphore(1);
	flag.acquire();
	flag2.acquire();
	class MyRunnable implements Runnable {
	    Exception exception2;
	    public void run() {
		DummyTransaction txn2 = null;
		try {
		    txn2 = createTransaction(
			UsePrepareAndCommit.ARBITRARY, 20000);
		    /* Get write lock on name-299 and notify txn */
		    store.setBinding(txn2, "name-299", id2);
		    flag.release();
		    /* Wait for txn, then commit */
		    flag2.tryAcquire(10000, TimeUnit.MILLISECONDS);
		    txn2.commit();
		} catch (TransactionAbortedException e) {
		    System.err.println("txn2: " + e);
		    exception2 = e;
		} catch (Exception e) {
		    System.err.println("txn2: " + e);
		    if (txn2 != null) {
			txn2.abort(new RuntimeException("abort txn2"));
		    }
		}
	    }
	}
	MyRunnable runnable = new MyRunnable();
	Thread thread = new Thread(runnable, "testNextBoundNameTimeout");
	/* Start txn2 and wait for it to write lock name-299 */
	thread.start();
	flag.acquire();
	String name = "name-100";
	txn = createTransaction();
	try {
	    /* Walk names, expecting to timeout on name-299 */
	    while (name != null) {
		name = store.nextBoundName(txn, name);
		store.removeBinding(txn, name);
	    }
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println("txn: " + e);
	    txn = null;
	} catch (Exception e) {
	    e.printStackTrace();
	    fail("Unexpected exception: " + e);
	} finally {
	    flag2.release();
	    thread.join(4000);
	    assertFalse("Thread should not be alive", thread.isAlive());
	}
    }

    /* -- Test abort -- */

    @Test
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
    @Test
    public void testAbortAborted() throws Exception {
	testAborted(abort, IllegalStateException.class);
    }
    @Test
    public void testAbortPreparedReadOnly() throws Exception {
	testPreparedReadOnly(abort);
    }
    @Test
    public void testAbortPreparedModified() throws Exception {
	abort.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	txn.prepare();
	/* Aborting a prepared, modified transaction is OK. */
	abort.run();
    }
    @Test
    public void testAbortCommitted() throws Exception {
	testCommitted(abort);
    }
    @Test
    public void testAbortWrongTxn() throws Exception {
	testWrongTxn(abort);
    }
    @Test
    public void testAbortShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(abort);
    }
    @Test
    public void testAbortShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(abort);
    }
    @Test
    public void testAbortShutdown() throws Exception {
	testShutdown(abort);
    }

    /* -- Test prepare -- */

    @Test
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

    @Test
    public void testPrepareTimeout() throws Exception {
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.NO, 100);
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
    @Test
    public void testPrepareAborted() throws Exception {
	testAborted(prepare, IllegalStateException.class);
    }
    @Test
    public void testPreparePreparedReadOnly() throws Exception {
	testPreparedReadOnly(prepare);
    }
    @Test
    public void testPreparePreparedModified() throws Exception {
	testPreparedModified(prepare);
    }
    @Test
    public void testPrepareCommitted() throws Exception {
	testCommitted(prepare);
    }
    @Test
    public void testPrepareWrongTxn() throws Exception {
	testWrongTxn(prepare);
    }
    @Test
    public void testPrepareShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(prepare);
    }
    @Test
    public void testPrepareShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(prepare);
    }
    @Test
    public void testPrepareShutdown() throws Exception {
	testShutdown(prepare);
    }

    /* -- Test prepareAndCommit -- */

    @Test
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

    @Test
    public void testPrepareAndCommitTimeout() throws Exception {
	txn.commit();
	txn = createTransaction(UsePrepareAndCommit.YES, 100);
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
    @Test
    public void testPrepareAndCommitAborted() throws Exception {
	testAborted(prepareAndCommit, IllegalStateException.class);
    }
    @Test
    public void testPrepareAndCommitPrepareAndCommitReadOnly()
	throws Exception
    {
	testPreparedReadOnly(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitPrepareAndCommitModified()
	throws Exception
    {
	testPreparedModified(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitCommitted() throws Exception {
	testCommitted(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitWrongTxn() throws Exception {
	testWrongTxn(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(prepareAndCommit);
    }
    @Test
    public void testPrepareAndCommitShutdown() throws Exception {
	testShutdown(prepareAndCommit);
    }

    /* -- Test commit -- */

    @Test
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
    @Test
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
	initTransaction(txn);
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
	}
    }
    private final CommitAction commit = new CommitAction();
    @Test
    public void testCommitAborted() throws Exception {
	testAborted(commit, IllegalStateException.class);
    }
    @Test
    public void testCommitPreparedReadOnly() throws Exception {
	testPreparedReadOnly(commit);
    }
    @Test
    public void testCommitPreparedModified() throws Exception {
	commit.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	assertFalse(txn.prepare());
	/* Committing a prepared, modified transaction is OK. */
	commit.run();
    }
    @Test
    public void testCommitCommitted() throws Exception {
	testCommitted(commit);
    }
    @Test
    public void testCommitWrongTxn() throws Exception {
	testWrongTxn(commit);
    }
    @Test
    public void testCommitShuttingDownExistingTxn() throws Exception {
	commit.setUp();
	store.setObject(txn, id, new byte[] { 0 });
	assertFalse(txn.prepare());
	/* Committing a prepared, modified transaction is OK. */
	testShuttingDownExistingTxn(commit);
    }
    @Test
    public void testCommitShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(commit);
    }
    @Test
    public void testCommitShutdown() throws Exception {
	testShutdown(commit);
    }

    /* -- Test shutdown -- */

    @Test
    public void testShutdownAgain() throws Exception {
	txn.abort(new RuntimeException("abort"));
	txn = null;
	store.shutdown();
	ShutdownAction action = new ShutdownAction();
	try {
	    assertTrue(action.waitForDone());
	} finally {
	    store = null;
	}
    }

    @Test
    public void testShutdownInterrupt() throws Exception {
	ShutdownAction action = new ShutdownAction();
	action.assertBlocked();
	action.interrupt();
        assertFalse(action.waitForDone());
	store.setBinding(txn, "foo", id);
	txn.commit();
	txn = null;
	/* Complete the shutdown */
	new ShutdownAction().waitForDone();
	store = null;
    }

    @Test
    public void testConcurrentShutdownInterrupt() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	action1.interrupt();
        action1.assertBlocked(); // should not be interrupted
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
        assertTrue(action1.waitForDone());
	assertTrue(action2.waitForDone());
	txn = null;
	store = null;
    }

    @Test
    public void testConcurrentShutdownRace() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
	assertTrue(action1.waitForDone());
	assertTrue(action2.waitForDone());
	txn = null;
	store = null;
    }

    @Test
    public void testShutdownRestart() throws Exception {
	store.setBinding(txn, "foo", id);
	byte[] bytes = { 1 };
	store.setObject(txn, id, bytes);
	txn.commit();
	store.shutdown();
	store = createDataStore();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	id = store.getBinding(txn, "foo");
	byte[] value = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(bytes, value));
    }

    /* -- Test getClassId -- */

    @Test
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

    @Test
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
    @Test
    public void testGetClassIdAborted() throws Exception {
	testAborted(getClassId);
    }
    @Test
    public void testGetClassIdPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getClassId);
    }
    @Test
    public void testGetClassIdPreparedModified() throws Exception {
	testPreparedModified(getClassId);
    }
    @Test
    public void testGetClassIdCommitted() throws Exception {
	testCommitted(getClassId);
    }
    @Test
    public void testGetClassIdWrongTxn() throws Exception {
	testWrongTxn(getClassId);
    }
    @Test
    public void testGetClassIdShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getClassId);
    }
    @Test
    public void testGetClassIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getClassId);
    }
    @Test
    public void testGetClassIdShutdown() throws Exception {
	testShutdown(getClassId);
    }

    /* -- Test getClassInfo -- */

    @Test
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

    @Test
    public void testGetClassInfoNotFound() {
	try {
	    store.getClassInfo(txn, 56789);
	    fail("Expected ClassInfoNotFoundException");
	} catch (ClassInfoNotFoundException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetClassInfoAfterRestart() throws Exception {
	byte[] bytes = { 1, 2, 3, 4, 5, 6 };
	int id = store.getClassId(txn, bytes);
	txn.commit();
	store.shutdown();
	store = createDataStore();
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	assertEquals(Arrays.toString(bytes),
		     Arrays.toString(store.getClassInfo(txn, id)));
    }

    /* -- Unusual states: getClassInfo -- */
    private final Action getClassInfo = new Action() {
	void run() throws Exception { store.getClassInfo(txn, 1); };
    };
    @Test
    public void testGetClassInfoAborted() throws Exception {
	testAborted(getClassInfo);
    }
    @Test
    public void testGetClassInfoPreparedReadOnly() throws Exception {
	testPreparedReadOnly(getClassInfo);
    }
    @Test
    public void testGetClassInfoPreparedModified() throws Exception {
	testPreparedModified(getClassInfo);
    }
    @Test
    public void testGetClassInfoCommitted() throws Exception {
	testCommitted(getClassInfo);
    }
    @Test
    public void testGetClassInfoWrongTxn() throws Exception {
	testWrongTxn(getClassInfo);
    }
    @Test
    public void testGetClassInfoShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getClassInfo);
    }
    @Test
    public void testGetClassInfoShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getClassInfo);
    }
    @Test
    public void testGetClassInfoShutdown() throws Exception {
	testShutdown(getClassInfo);
    }

    /* -- Test nextObjectId -- */

    @Test
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

    @Test
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

    @Test
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
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
    @Test
    public void testNextObjectIdAborted() throws Exception {
	testAborted(nextObjectId);
    }
    @Test
    public void testNextObjectIdPreparedReadOnly() throws Exception {
	testPreparedReadOnly(nextObjectId);
    }
    @Test
    public void testNextObjectIdPreparedModified() throws Exception {
	testPreparedModified(nextObjectId);
    }
    @Test
    public void testNextObjectIdCommitted() throws Exception {
	testCommitted(nextObjectId);
    }
    @Test
    public void testNextObjectIdWrongTxn() throws Exception {
	testWrongTxn(nextObjectId);
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

    /* -- Test deadlock -- */
    @SuppressWarnings("hiding")
    @Test
    public void testDeadlock() throws Exception {
	for (int i = 0; i < 5; i++) {
	    if (i > 0) {
		txn = createTransaction(
		    UsePrepareAndCommit.ARBITRARY, 1000);
	    }
	    final long id = store.createObject(txn);
	    store.setObject(txn, id, new byte[] { 0 });
	    final long id2 = store.createObject(txn);
	    store.setObject(txn, id2, new byte[] { 0 });
	    txn.commit();
	    txn = createTransaction(
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
			txn2 = createTransaction(
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

    /* -- Other methods and classes -- */

    /** Creates a unique directory. */
    private String createDirectory() throws IOException {
	String name = getClass().getName();
	int dot = name.lastIndexOf('.');
	if (dot > 0) {
	    name = name.substring(dot + 1);
	}
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
	DataStore store = new DataStoreProfileProducer(
	    new DataStoreImpl(props, systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
	DummyProfileCoordinator.startProfiling();
	return store;
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

	/**
	 * Perform any setup required for calling the {@link #run} method.
	 * This method needs to put the database in use for the current
	 * transaction, but not in a way that conflicts with the operations
	 * performed by {@code run}.
	 *
	 * @throws	Exception if the setup throws an exception
	 */
	void setUp() throws Exception { };

	/**
	 * Run the test action.
	 *
	 * @throws	Exception if the action throws an exception
	 */
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
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	assertTrue(shutdownAction.waitForDone());
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
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	assertTrue(shutdownAction.waitForDone());
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

	/** Creates an instance of this class and starts the thread. */
	protected ShutdownAction() {
	    start();
	}

	/** Performs the shutdown and collects the results. */
	public void run() {
	    try {
		shutdown();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	protected void shutdown() {
            store.shutdown();
	}

	/** Asserts that the shutdown call is blocked. */
	public synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(5);
	    if (exception != null) {
		exception.printStackTrace();
		fail("Unexpected exception: " + exception);
	    }
	    assertFalse("Expected shutdown to be blocked", done);
	}
	
	/** Waits a while for the shutdown call to complete. */
	public synchronized boolean waitForDone() throws Exception {
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

    /** Creates a transaction. */
    protected DummyTransaction createTransaction() {
	return initTransaction(new DummyTransaction());
    }

    /** Creates a transaction with explicit use of prepareAndCommit. */
    protected DummyTransaction createTransaction(
	UsePrepareAndCommit usePrepareAndCommit)
    {
	return initTransaction(new DummyTransaction(usePrepareAndCommit));
    }

    /**
     * Creates a transaction with explicit use of prepareAndCommit and a
     * non-standard timeout.
     */
    protected DummyTransaction createTransaction(
	UsePrepareAndCommit usePrepareAndCommit, long timeout)
    {
	return initTransaction(
	    new DummyTransaction(usePrepareAndCommit, timeout));
    }

    /** Initializes a new transaction. */
    protected DummyTransaction initTransaction(DummyTransaction txn) {
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
	return txn;
    }
}
