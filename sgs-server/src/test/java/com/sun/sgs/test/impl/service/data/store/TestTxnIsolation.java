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
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import com.sun.sgs.impl.service.data.store.AbstractDataStore;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.service.data.store.db.je.JeEnvironment;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the isolation that the data store enforces between transactions. */
@RunWith(FilteredNameRunner.class)
public class TestTxnIsolation extends Assert {

    /**
     * The number of milliseconds to wait to see if an operation is blocked.
     */
    protected static final long BLOCK_TIMEOUT = 4;

    /**
     * The number of milliseconds to wait to see if an operation was
     * successful.
     */
    protected static final long SUCCESS_TIMEOUT = 200;

    /**
     * The number of milliseconds to wait until a lock times out.  For this
     * test, set this number to the transaction timeout to make sure it has
     * plenty of time to perform operations.
     */
    protected static final long LOCK_TIMEOUT = 200;

    protected static final long TXN_TIMEOUT = 400;

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The directory used for the database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestTxnIsolation.db";

    /** The configuration properties. */
    private static final Properties props = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory,
	LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
	String.valueOf(LOCK_TIMEOUT),
	"com.sun.sgs.txn.timeout", String.valueOf(TXN_TIMEOUT),
	BdbEnvironment.LOCK_TIMEOUT_PROPERTY, String.valueOf(LOCK_TIMEOUT),
	JeEnvironment.LOCK_TIMEOUT_PROPERTY, String.valueOf(LOCK_TIMEOUT),
	BdbEnvironment.TXN_ISOLATION_PROPERTY, "READ_UNCOMMITTED",
	JeEnvironment.TXN_ISOLATION_PROPERTY, "READ_UNCOMMITTED");

    private static final byte[] value = { 1 };

    private static final byte[] secondValue = { 2 };

    private static final byte[] thirdValue = { 3 };

    /** The transaction proxy. */
    protected static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** The access coordinator to test. */
    protected static AccessCoordinatorHandle accessCoordinator;

    /** The data store to test. */
    protected static DataStore store;

    /** An initial, open transaction. */
    protected DummyTransaction txn;
    
    /** The object ID of a new object created in the transaction. */
    protected long id;

    protected Runner runner;

    @BeforeClass
    public static void beforeClass() {
	cleanDirectory(dbDirectory);
    }

    @Before
    public void before() {
	if (store == null) {
	    accessCoordinator = createAccessCoordinator();
	    store = createDataStore();
	}
	txn = createTransaction();
	id = store.createObject(txn);
	store.setObject(txn, id, value);
    }

    @After
    public void after() throws Exception {
	if (txn != null) {
	    txn.commit();
	    txn = null;
	}
	txnProxy.setCurrentTransaction(null);
	if (runner != null) {
	    runner.commit();
	    runner = null;
	}
    }

    protected AccessCoordinatorHandle createAccessCoordinator() {
	return new LockingAccessCoordinator(
	    props, txnProxy, new DummyProfileCollectorHandle());
    }

    protected DataStore createDataStore() {
	return new DummyDataStore(accessCoordinator);
	//return new DataStoreImpl(props, accessCoordinator);
    }

    /* -- Tests -- */

    /* -- Test object access -- */

    /* Operations -- perform unordered pairs:
       markForUpdate
       getObject forUpdate=false
       getObject forUpdate=true
       setObject
       setObjects
       removeObject
    */

    /* -- Test markForUpdate -- */

    @Test
    public void testMarkForUpdateMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(new MarkForUpdate(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    /* -- Test getObject forUpdate=false -- */

    @Test
    public void testGetObjectMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(new GetObject(id, false));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    @Test
    public void testGetObjectGetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, false);
	runner = new Runner(new GetObject(id, false));
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    /* -- Test getObject forUpdate=true -- */

    @Test
    public void testGetObjectForUpdateMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(new GetObject(id, true));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    @Test
    public void testGetObjectForUpdateGetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, false);
	runner = new Runner(new GetObject(id, true));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    @Test
    public void testGetObjectForUpdateGetObjectForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, true);
	runner = new Runner(new GetObject(id, true));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    /* -- Test setObject -- */

    @Test
    public void testSetObjectMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(new SetObject(id, secondValue));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectGetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, false);
	runner = new Runner(new SetObject(id, secondValue));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectGetObjectForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, true);
	runner = new Runner(new SetObject(id, secondValue));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectSetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setObject(txn, id, secondValue);
	runner = new Runner(new SetObject(id, thirdValue));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    /* -- Test setObjects -- */

    @Test
    public void testSetObjectsMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectsGetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, false);
	runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectsGetObjectForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, true);
	runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    @Test
    public void testSetObjectsSetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setObject(txn, id, secondValue);
	runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
    }

    /* -- Test removeObject -- */

    @Test
    public void testRemoveObjectMarkForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.markForUpdate(txn, id);
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectGetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, false);
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectGetObjectForUpdate() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.getObject(txn, id, true);
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectSetObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setObject(txn, id, secondValue);
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectSetObjects() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setObjects(txn, new long[] { id }, new byte[][] { secondValue });
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectRemoveObject() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.removeObject(txn, id);
	runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertFalse((Boolean) runner.getResult());
    }

    /* -- Test name access -- */

    /* Operations -- perform in unordered pairs, as appropriate:
       getBinding notFound
       getBinding found
       setBinding create
       setBinding existing
       removeBinding notFound
       removeBinding found
       nextBoundName
       nextBoundName last
     */

    /* -- Test getBinding -- */

    @Test
    public void testGetBindingNotFoundGetBindingNotFound() throws Exception {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.getBinding(txn, "a");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new GetBinding("a"));
	assertSame(null, runner.getResult());
    }

    /* -- Test getBinding found -- */

    /* getBindingFound vs. getBindingNotFound is not possible */

    @Test
    public void testGetBindingFoundGetBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.getBinding(txn, "a");
	runner = new Runner(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    /* -- Test setBinding create -- */

    @Test
    public void testSetBindingCreateGetBindingNotFound() throws Exception {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.getBinding(txn, "a");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new SetBinding("a", 100));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    /* setBindingCreate vs. getBindingFound is not possible */

    /* -- Test setBinding existing -- */

    /* setBinding existing vs. getBinding not found is not possible */

    @Test
    public void testSetBindingExistingGetBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.getBinding(txn, "a");
	runner = new Runner(new SetBinding("a", 200));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }
   
    @Test
    public void testSetBindingFoundSetBindingCreate() throws Exception {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new SetBinding("a", 200));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testSetBindingFoundSetBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 200);
	runner = new Runner(new SetBinding("a", 300));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(300), runner.getResult());
    }

    /* -- Test removeBinding notFound -- */

    @Test
    public void testRemoveBindingNotFoundGetBindingNotFound() throws Exception {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.getBinding(txn, "a");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertFalse((Boolean) runner.getResult());
    }

    /* removeBinding notFound vs. getBinding found is not possible */

    @Test
    public void testRemoveBindingNotFoundSetBindingCreate() throws Exception {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingNotFoundRemoveBindingNotFound()
	throws Exception
    {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.removeBinding(txn, "a");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertFalse((Boolean) runner.getResult());
    }

    /* -- Test removeBinding found -- */

    /* removeBinding found vs. getBinding notFound is not possible */

    @Test
    public void testRemoveBindingFoundGetBindingFound()
	throws Exception
    {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.getBinding(txn, "a");
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingFoundSetBindingCreate()
	throws Exception
    {
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    /* removeBinding found vs. removeBinding notFound is not possible */

    /* -- Test nextBoundName -- */

    @Test
    public void testNextBoundNameGetBindingNotFound() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	store.setBinding(txn, "c", 300);
	txn.commit();
	txn = createTransaction();
	try {
	    store.getBinding(txn, "b");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new NextBoundName("a"));
	assertEquals("c", runner.getResult());
    }

    @Test
    public void testNextBoundNameGetBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 200);
	txn.commit();
	txn = createTransaction();
	store.getBinding(txn, "b");
	runner = new Runner(new NextBoundName("a"));
	assertEquals("b", runner.getResult());
    }

    @Test
    public void testNextBoundNameSetBindingCreate() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "b", 200);
	runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertEquals("b", runner.getResult());
    }

    @Test
    public void testNextBoundNameSetBindingExisting() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 200);
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "b", 300);
	runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertEquals("b", runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveBindingNotFound() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	store.setBinding(txn, "c", 300);
	txn.commit();
	txn = createTransaction();
	try {
	    store.removeBinding(txn, "b");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new NextBoundName("a"));
	/* This operation may or may not block */
	txn.commit();
	txn = null;
	assertEquals("c", runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 200);
	store.setBinding(txn, "c", 300);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "b");
	runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertEquals("c", runner.getResult());
    }

    @Test
    public void testNextBoundNameNextBoundName() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 200);
	txn.commit();
	txn = createTransaction();
	store.nextBoundName(txn, "a");
	runner = new Runner(new NextBoundName("a"));
	assertEquals("b", runner.getResult());
    }

    /* -- Test nextBoundName last -- */

    @Test
    public void testNextBoundNameLastGetBindingNotFound() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.getBinding(txn, "b");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new NextBoundName("a"));
	assertSame(null, runner.getResult());
    }

    /* nextBoundName last vs. getBinding found is not possible */

    @Test
    public void testNextBoundNameLastSetBindingCreate() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "b", 200);
	runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertSame(null, runner.getResult());
    }

    /* nextBoundName last vs. setBinding existing is not possible */

    @Test
    public void testNextBoundNameLastRemoveBindingNotFound() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	try {
	    store.removeBinding(txn, "b");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
	runner = new Runner(new NextBoundName("a"));
	/* This operation may or may not block */
	txn.commit();
	txn = null;
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameLastRemoveBindingFound() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 200);
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "b");
	runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	txn.commit();
	txn = null;
	assertSame(null, runner.getResult());
    }

    /* nextBoundName last vs. nextBoundName is not possible */

    @Test
    public void testNextBoundNameLastNextBoundNameLast() throws Exception {
	store.setBinding(txn, "a", 100);
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	txn.commit();
	txn = createTransaction();
	store.nextBoundName(txn, "a");
	runner = new Runner(new NextBoundName("a"));
	assertSame(null, runner.getResult());
    }

    /* -- Other methods -- */

    /** Creates a transaction. */
    protected static DummyTransaction createTransaction() {
	DummyTransaction txn = new DummyTransaction(TXN_TIMEOUT);
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
	return txn;
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
    static class Runner {
	private TxnCallable<Object> action;
	private FutureTask<Object> task;
	private DummyTransaction txn;
	private final Thread thread =
	    new Thread() {
		public void run() {
		    runInternal();
		}
	    };
	Runner(TxnCallable<? extends Object> action) {
	    @SuppressWarnings("unchecked")
	    TxnCallable<Object> a = (TxnCallable<Object>) action;
	    synchronized (this) {
		this.action = a;
		task = new FutureTask<Object>(a);
	    };
	    thread.start();
	}
	void setAction(TxnCallable<? extends Object> action) {
	    @SuppressWarnings("unchecked")
	    TxnCallable<Object> a = (TxnCallable<Object>) action;
	    synchronized (this) {
		if (!task.isDone()) {
		    throw new RuntimeException("Task is not done");
		}
		this.action = a;
		task = new FutureTask<Object>(a);
		notifyAll();
	    }
	}
	void setDone() {
	    synchronized (this) {
		if (!task.isDone()) {
		    throw new RuntimeException("Task is not done");
		}
		task = null;
		notifyAll();
	    }
	}
	void commit() throws InterruptedException, TimeoutException {
	    setAction(new Commit());
	    getResult();
	    setDone();
	}
// 	void abort() throws InterruptedException, TimeoutException {
// 	    setAction(new Abort());
// 	    getResult();
// 	    setDone();
// 	}
	void runInternal() {
	    TxnCallable a;
	    FutureTask<?> t;
	    synchronized (this) {
		txn = createTransaction();
		a = action;
		t = task;
		notifyAll();
	    }
	    while (true) {
		if (t == null) {
		    break;
		} else if (!t.isDone()) {
		    a.setTransaction(txn);
		    t.run();
		    continue;
		}
		try {
		    synchronized (this) {
			wait();
			a = action;
			t = task;
			continue;
		    }
		} catch (InterruptedException e) {
		    break;
		}
	    }
	}
	private synchronized FutureTask<Object> getTask()
	    throws InterruptedException
	{
	    while (txn == null) {
		wait();
	    }
	    return task;
	}
	boolean blocked() throws InterruptedException {
	    try {
		getTask().get(BLOCK_TIMEOUT, TimeUnit.MILLISECONDS);
		return false;
	    } catch (TimeoutException e) {
		return true;
	    } catch (RuntimeException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
	void assertBlocked() throws InterruptedException {
	    assertTrue("The operation did not block", blocked());
	}
	Object getResult()
	    throws InterruptedException, TimeoutException
	{
	    try {
		return getTask().get(SUCCESS_TIMEOUT, TimeUnit.MILLISECONDS);
	    } catch (RuntimeException e) {
		throw e;
// 	    } catch (ExecutionException e) {
// 		if (e.getCause() instanceof RuntimeException) {
// 		    throw (RuntimeException) e.getCause();
// 		} else {
// 		    throw new RuntimeException(
// 			"Unexpected exception: " + e, e);
// 		}
	    } catch (TimeoutException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    /** A {@code Callable} that can be supplied a transaction. */
    abstract static class TxnCallable<T> implements Callable<T> {

	/** The transaction. */
	private DummyTransaction txn;

	/** Creates an instance of this class. */
	TxnCallable() { }

	/** Sets the transaction. */
	synchronized void setTransaction(DummyTransaction txn) {
	    this.txn = txn;
	}

	/** Gets the transaction. */
	synchronized DummyTransaction getTransaction() {
	    return txn;
	}
    }

    /** Calls {@link DataStore#markForUpdate}. */
    static class MarkForUpdate extends TxnCallable<Void> {
	private final long oid;
	MarkForUpdate(long oid) {
	    this.oid = oid;
	}
	public Void call() {
	    store.markForUpdate(getTransaction(), oid);
	    return null;
	}
    }

    /**
     * Calls {@link DataStore#getObject}, returning {@code null} if the object
     * is not found.
     */
    static class GetObject extends TxnCallable<byte[]> {
	private final long oid;
	private final boolean forUpdate;
	GetObject(long oid, boolean forUpdate) {
	    this.oid = oid;
	    this.forUpdate = forUpdate;
	}
	public byte[] call() {
	    try {
		return store.getObject(getTransaction(), oid, forUpdate);
	    } catch (ObjectNotFoundException e) {
		return null;
	    }
	}
    }

    /** Calls {@link DataStore#setObject}. */
    static class SetObject extends TxnCallable<Void> {
	private final long oid;
	private final byte[] data;
	SetObject(long oid, byte[] data) {
	    this.oid = oid;
	    this.data = data;
	}
	public Void call() {
	    store.setObject(getTransaction(), oid, data);
	    return null;
	}
    }

    /** Calls {@link DataStore#setObjects}. */
    static class SetObjects extends TxnCallable<Void> {
	private final long[] oids;
	private final byte[][] dataArray;
	SetObjects(long[] oids, byte[][] dataArray) {
	    this.oids = oids;
	    this.dataArray = dataArray;
	}
	public Void call() {
	    store.setObjects(getTransaction(), oids, dataArray);
	    return null;
	}
    }

    /**
     * Calls {@link DataStore#removeObject}, returning {@code true} if
     * successful, and {@code false} if the object is not found.
     */
    static class RemoveObject extends TxnCallable<Boolean> {
	private final long oid;
	RemoveObject(long oid) {
	    this.oid = oid;
	}
	public Boolean call() {
	    try {
		store.removeObject(getTransaction(), oid);
		return Boolean.TRUE;
	    } catch (ObjectNotFoundException e) {
		return Boolean.FALSE;
	    }
	}
    }

    /**
     * Calls {@link DataStore#getBinding}, returning {@code null} if the name
     * is not bound.
     */
    static class GetBinding extends TxnCallable<Long> {
	private final String name;
	GetBinding(String name) {
	    this.name = name;
	}
	public Long call() {
	    try {
		return store.getBinding(getTransaction(), name);
	    } catch (NameNotBoundException e) {
		return null;
	    }
	}
    }

    /** Calls {@link DataStore#setBinding}. */
    static class SetBinding extends TxnCallable<Void> {
	private final String name;
	private final long oid;
	SetBinding(String name, long oid) {
	    this.name = name;
	    this.oid = oid;
	}
	public Void call() {
	    store.setBinding(getTransaction(), name, oid);
	    return null;
	}
    }

    /**
     * Calls {@link DataStore#removeBinding}, returning {@code true} if the
     * name was bound, otherwise {@code false}.
     */
    static class RemoveBinding extends TxnCallable<Boolean> {
	private final String name;
	RemoveBinding(String name) {
	    this.name = name;
	}
	public Boolean call() {
	    try {
		store.removeBinding(getTransaction(), name);
		return Boolean.TRUE;
	    } catch (NameNotBoundException e) {
		return Boolean.FALSE;
	    }
	}
    }

    /** Calls {@link DataStore#nextBoundName}. */
    static class NextBoundName extends TxnCallable<String> {
	private final String name;
	NextBoundName(String name) {
	    this.name = name;
	}
	public String call() {
	    return store.nextBoundName(getTransaction(), name);
	}
    }

    /** Calls {@link DummyTransaction#commit}. */
    static class Commit extends TxnCallable<Void> {
	Commit() { }
	public Void call() throws Exception {
	    getTransaction().commit();
	    return null;
	}
    }

//     /** Calls {@link DummyTransaction#abort}. */
//     static class Abort extends TxnCallable<Void> {
// 	Abort() { }
// 	public Void call() {
// 	    getTransaction().abort(new RuntimeException());
// 	    return null;
// 	}
//     }

    /**
     * An implementation of {@link DataStore} that does no locking, to check
     * that the access coordinator is doing all of the locking correctly.
     */
    static class DummyDataStore extends AbstractDataStore {

	/** The logger to pass to AbstractDataStore. */
	private static final LoggerWrapper logger =
	    new LoggerWrapper(Logger.getLogger(DummyDataStore.class.getName()));

	/** Maps object IDs to data. */
	private final NavigableMap<Long, byte[]> oids =
	    new TreeMap<Long, byte[]>();

	/** Maps names to object IDs. */
	private final NavigableMap<String, Long> names =
	    new TreeMap<String, Long>();

	/** Maps transactions to their undo lists. */
	private final Map<Transaction, List<Object>> txnEntries =
	    new HashMap<Transaction, List<Object>>();

	/** Stores the next object ID. */
	private final AtomicLong nextOid = new AtomicLong(1);

	/** Creates an instance of this class. */
	DummyDataStore(AccessCoordinator accessCoordinator) {
	    super(accessCoordinator, logger, logger);
	}

	/* -- Implement AbstractDataStore methods -- */

	protected long createObjectInternal(Transaction txn) {
	    txn.join(this);
	    return nextOid.getAndIncrement();
	}

	protected void markForUpdateInternal(Transaction txn, long oid) { 
	    txn.join(this);
	}

	protected synchronized byte[] getObjectInternal(
	    Transaction txn, long oid, boolean forUpdate)
	{
	    txn.join(this);
	    if (oids.containsKey(oid)) {
		return oids.get(oid);
	    } else {
		throw new ObjectNotFoundException("");
	    }
	}

	protected synchronized void setObjectInternal(
	    Transaction txn, long oid, byte[] data)
	{
	    txn.join(this);
	    List<Object> txnEntry = getTxnEntry(txn);
	    txnEntry.add(oid);
	    txnEntry.add(oids.get(oid));
	    oids.put(oid, data);
	}

	protected synchronized void setObjectsInternal(
	    Transaction txn, long[] oids, byte[][] dataArray)
	{
	    txn.join(this);
	    List<Object> txnEntry = getTxnEntry(txn);
	    for (int i = 0; i < oids.length; i++) {
		byte[] oldValue = this.oids.put(oids[i], dataArray[i]);
		txnEntry.add(oids[i]);
		txnEntry.add(oldValue);
	    }
	}

	protected synchronized void removeObjectInternal(
	    Transaction txn, long oid)
	{
	    txn.join(this);
	    byte[] oldValue = oids.remove(oid);
	    if (oldValue != null) {
		List<Object> txnEntry = getTxnEntry(txn);
		txnEntry.add(oid);
		txnEntry.add(oldValue);
	    } else {
		throw new ObjectNotFoundException("");
	    }
	}

	protected synchronized BindingValue getBindingInternal(
	    Transaction txn, String name)
	{
	    txn.join(this);
	    if (names.containsKey(name)) {
		return new BindingValue(names.get(name), null);
	    } else {
		return new BindingValue(-1, names.higherKey(name));
	    }
	}

	protected synchronized BindingValue setBindingInternal(
	    Transaction txn, String name, long oid)
	{
	    txn.join(this);
	    Long oldValue = names.put(name, oid);
	    List<Object> txnEntry = getTxnEntry(txn);
	    txnEntry.add(name);
	    txnEntry.add(oldValue);
	    if (oldValue != null) {
		return new BindingValue(1, null);
	    } else {
		return new BindingValue(-1, names.higherKey(name));
	    }
	}

	protected synchronized BindingValue removeBindingInternal(
	    Transaction txn, String name)
	{
	    System.err.println("removeBindingInternal " +
			       "name:" + name + ", names:" + names);
	    txn.join(this);
	    Long oldValue = names.remove(name);
	    if (oldValue != null) {
		List<Object> txnEntry = getTxnEntry(txn);
		txnEntry.add(name);
		txnEntry.add(oldValue);
		return new BindingValue(1, names.higherKey(name));
	    } else {
		return new BindingValue(-1, names.higherKey(name));
	    }
	}
	    
	protected synchronized String nextBoundNameInternal(
	    Transaction txn, String name)
	{
	    txn.join(this);
	    return names.higherKey(name);
	}

	protected void shutdownInternal() { }

	protected int getClassIdInternal(Transaction txn, byte[] classInfo) {
	    throw new UnsupportedOperationException();
	}

	protected byte[] getClassInfoInternal(Transaction txn, int classId) {
	    throw new UnsupportedOperationException();	    
	}

	protected synchronized long nextObjectIdInternal(
	    Transaction txn, long oid)
	{
	    txn.join(this);
	    Long higherKey = oids.higherKey(oid);
	    return (higherKey != null) ? higherKey.longValue() : -1;
	}

	protected boolean prepareInternal(Transaction txn) {
	    return false;
	}

	protected void commitInternal(Transaction txn) {
	    removeTxnEntry(txn);
	}

	protected void prepareAndCommitInternal(Transaction txn) {
	    commitInternal(txn);
	}

	protected void abortInternal(Transaction txn) {
	    System.err.println("Entering abortInternal");
	    List<Object> txnEntry = getTxnEntry(txn);
	    for (int i = txnEntry.size() - 2; i >= 0; i -= 2) {
		Object key = txnEntry.get(i);
		Object value = txnEntry.get(i + 1);
		if (key instanceof Long) {
		    if (value != null) {
			oids.put((Long) key, (byte[]) value);
		    } else {
			oids.remove((Long) key);
		    }
		} else if (value != null) {
		    names.put((String) key, (Long) value);
		} else {
		    names.remove((String) key);
		}
	    }
	    removeTxnEntry(txn);
	    System.err.println("After abort: names: " + names);
	}

	/* -- Other methods -- */

	/** Returns the undo list for the specified transaction. */
	private List<Object> getTxnEntry(Transaction txn) {
	    List<Object> result = txnEntries.get(txn);
	    if (result == null) {
		result = new ArrayList<Object>();
		txnEntries.put(txn, result);
	    }
	    return result;
	}

	/** Removes the undo list for the specified transaction. */
	private void removeTxnEntry(Transaction txn) {
	    txnEntries.remove(txn);
	}
    }
}
