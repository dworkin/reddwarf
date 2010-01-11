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

import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.kernel.NullAccessCoordinator;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.tools.test.FilteredNameRunner;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.test.util.UtilReflection;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the use of placeholders in the DataStoreImpl class */
@RunWith(FilteredNameRunner.class)
public class TestDataStoreImplPlaceholders extends Assert {

    /** The basic test environment. */
    private static final BasicDataStoreTestEnv env =
	new BasicDataStoreTestEnv(System.getProperties());

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImplPlaceholders.db";

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

    /** Properties for creating the DataStore. */
    private static Properties props = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory);

    /** The transaction proxy. */
    protected static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** The access coordinator. */
    protected static final AccessCoordinatorHandle accessCoordinator =
	new NullAccessCoordinator(System.getProperties(), txnProxy,
				  new DummyProfileCollectorHandle());

    /** The data store to test. */
    private static DataStoreImpl store;

    /** An initial, open transaction. */
    private DummyTransaction txn;

    /** The object ID of a newly created object. */
    private long id;

    /** Clean the database directory and create the data store. */
    @BeforeClass
    public static void initialize() throws Exception {
	cleanDirectory(dbDirectory);
	store = new DataStoreImpl(props, env.systemRegistry, env.txnProxy);
    }

    /** Create a transaction and an object in the data store. */
    @Before
    public void setUp() throws Exception {
	txn = createTransaction(UsePrepareAndCommit.ARBITRARY, 10000);
	id = store.createObject(txn);
    }

    /** Commit the current transaction, if non-null. */
    @After
    public void tearDown() throws Exception {
	try {
	    if (txn != null) {
		txn.commit();
	    }
	} finally {
	    txn = null;
	}
    }

    /* -- Test APIs with placeholders -- */

    @Test
    public void testMarkForUpdatePlaceholder() throws Exception {
	createPlaceholder(txn, id);
	for (int i = 0; i < 2; i++) {
	    if (i == 1) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.markForUpdate(txn, id);
	}
	store.setObject(txn, id, new byte[0]);
    }

    @Test
    public void testGetObjectPlaceholder() throws Exception {
	createPlaceholder(txn, id);
	for (int i = 0; i < 2; i++) {
	    if (i == 1) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
    }

    @Test
    public void testSetObjectPlaceholder() throws Exception {
	for (int i = 0; i < 2; i++) {
	    id = store.createObject(txn);
	    createPlaceholder(txn, id);
	    if (i == 1) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    byte[] value = { 1, 2 };
	    store.setObject(txn, id, value);
	    assertSameBytes(value, store.getObject(txn, id, false));
	}
    }

    @Test
    public void testSetObjectsPlaceholder() throws Exception {
	for (int i = 0; i < 2; i++) {
	    long[] ids = { store.createObject(txn), store.createObject(txn) };
	    byte[][] dataArray = { { 1, 2 }, { 3, 4, 5 } };
	    createPlaceholder(txn, ids[0]);
	    createPlaceholder(txn, ids[1]);
	    if (i == 1) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObjects(txn, ids, dataArray);
	    assertSameBytes(dataArray[0], store.getObject(txn, ids[0], false));
	    assertSameBytes(dataArray[1], store.getObject(txn, ids[1], false));
	}
    }

    @Test
    public void testRemoveObjectPlaceholder() throws Exception {
	for (int i = 0; i < 2; i++) {
	    createPlaceholder(txn, id);
	    if (i == 1) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.removeObject(txn, id);
	}
	store.setObject(txn, id, new byte[0]);
    }

    /* -- Other tests -- */

    /** Test that allocation block placeholders get removed at startup. */
    @Test
    public void testRemoveAllocationPlaceholders() throws Exception {
	/* Create objects but don't create data */
	for (int i = 0; i < 1025; i++) {
	    if (i % 25 == 0) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObject(
		txn, store.createObject(txn), new byte[] { (byte) i });
	}
	/* Create objects */
	for (int i = 2050; i < 3075; i++) {
	    if (i % 25 == 0) {
		txn.commit();
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
	    }
	    store.setObject(
		txn, store.createObject(txn), new byte[] { (byte) i });
	}
	txn.commit();
	txn = null;
	store.shutdown();
	store = new DataStoreImpl(props, env.systemRegistry, env.txnProxy);
	long nextId = -1;
	for (int i = 0; true; i++) {
	    if (i % 40 == 0) {
		if (txn != null) {
		    txn.commit();
		}
		txn = createTransaction(UsePrepareAndCommit.ARBITRARY);
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
	    setObjectRaw.invoke(store, txn, oid, data);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }

    /** Calls DataStoreImpl.getObjectRaw. */
    private static byte[] getObjectRaw(Transaction txn, long oid) {
	try {
	    return (byte[]) getObjectRaw.invoke(store, txn, oid);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }
	
    /** Calls DataStoreImpl.nextObjectIdRaw. */
    private static long nextObjectIdRaw(Transaction txn, long oid) {
	try {
	    return (Long) nextObjectIdRaw.invoke(store, txn, oid);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
    }

    /** Creates a transaction with explicit use of prepareAndCommit. */
    static DummyTransaction createTransaction(
	UsePrepareAndCommit usePrepareAndCommit)
    {
	return initTransaction(new DummyTransaction(usePrepareAndCommit));
    }

    /**
     * Creates a transaction with explicit use of prepareAndCommit and a
     * non-standard timeout.
     */
    static DummyTransaction createTransaction(
	UsePrepareAndCommit usePrepareAndCommit, long timeout)
    {
	return initTransaction(
	    new DummyTransaction(usePrepareAndCommit, timeout));
    }

    /** Initializes a transaction. */
    static DummyTransaction initTransaction(DummyTransaction txn) {
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
	return txn;
    }
}
