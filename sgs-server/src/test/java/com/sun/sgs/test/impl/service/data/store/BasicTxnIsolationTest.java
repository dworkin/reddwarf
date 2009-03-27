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
import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.UtilProperties;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A superclass for tests of the isolation that the data store enforces between
 * transactions.
 */
public abstract class BasicTxnIsolationTest extends Assert {

    /** The configuration properties. */
    protected static Properties props;

    /**
     * The number of milliseconds to wait to see if an operation is blocked.
     */
    protected static long timeoutBlock;

    /**
     * The number of milliseconds to wait to see if an operation is successful.
     */
    protected static long timeoutSuccess;

    /** A test value for an object ID. */
    private static final byte[] value = { 1 };

    /** Another test value for an object ID. */
    private static final byte[] secondValue = { 2 };

    /** Another test value for an object ID. */
    private static final byte[] thirdValue = { 3 };

    /** The transaction proxy. */
    protected static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** The access coordinator to test. */
    protected static AccessCoordinatorHandle accessCoordinator;

    /** The data store to test. */
    private static DataStore store;

    /** An initial, open transaction. */
    private DummyTransaction txn;
    
    /** The object ID of a new object created in the transaction. */
    private long id;

    /** The runner used in the test, or {@code null} if not used or done. */
    private Runner runner;

    /**
     * Create the properties, access coordinator, and data store if needed;
     * then create a transaction, create an object, and clear existing
     * bindings.
     */
    @Before
    public void before() {
	if (props == null) {
	    props = createProperties();
	}
	if (store == null) {
	    accessCoordinator = createAccessCoordinator();
	    store = createDataStore();
	}
	txn = createTransaction();
	id = store.createObject(txn);
	store.setObject(txn, id, value);
	try {
	    store.removeBinding(txn, "a");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "b");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "c");
	} catch (NameNotBoundException e) {
	}
	try {
	    store.removeBinding(txn, "d");
	} catch (NameNotBoundException e) {
	}
    }

    /** Commit the transaction and the runner, if not null. */
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

    /** Creates the configuration properties. */
    protected Properties createProperties() {
	props = UtilProperties.createProperties();
	PropertiesWrapper wrappedProps = new PropertiesWrapper(props);
	timeoutBlock = wrappedProps.getLongProperty(
	    "test.timeout.block", getDefaultTimeoutBlock());
	timeoutSuccess = wrappedProps.getLongProperty(
	    "com.sun.sgs.txn.timeout", getDefaultTimeoutSuccess());
	props.setProperty(LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
			  String.valueOf(timeoutSuccess));
	return props;
    }

    /**
     * Creates the access coordinator.
     *
     * @return	the access coordinator
     */
    protected AccessCoordinatorHandle createAccessCoordinator() {
	return new LockingAccessCoordinator(
	    props, txnProxy, new DummyProfileCollectorHandle());
    }

    /**
     * Creates the data store.
     *
     * @return	the data store
     */
    protected abstract DataStore createDataStore();

    /**
     * Returns the default number of milliseconds to wait to see if an
     * operation is blocked.  This implementation returns {@code 1/10} of the
     * value of the timeout for a successful operation.
     *
     * @return	the number of milliseconds for a blocked operation
     */
    protected long getDefaultTimeoutBlock() {
	return (long) (getDefaultTimeoutSuccess() * 0.1);
    }

    /**
     * Returns the default number of milliseconds to wait to see if an
     * operation is successful.  This implementation returns twice the default
     * transaction timeout for bounded operations.
     *
     * @return	the number of milliseconds for a successful operation
     */
    protected long getDefaultTimeoutSuccess() {
	return 2 * TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT;
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
    public void testSetBindingExistingSetBindingCreate() throws Exception {
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
    public void testSetBindingExistingSetBindingFound() throws Exception {
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
	txn.commit();
	txn = createTransaction();
	store.nextBoundName(txn, "a");
	runner = new Runner(new NextBoundName("a"));
	assertSame(null, runner.getResult());
    }

    /* -- Tests for phantom bindings -- */

    /**
     * Test that removing a binding locks the next name even when the identity
     * of that name changes because a conflicting transaction aborts.
     *
     * Here's the blow-by-blow:
     *
     * tid:1 create a
     * tid:1 create b
     *
     * tid:2 create c
     * 	     write lock c
     *	     write lock end
     *
     * tid:3 remove b
     *       write lock b
     *       write lock c (blocks)
     *
     * tid:2 abort
     *       release lock c
     *       release lock end
     *
     * tid:3 [remove b]
     *       write lock c (but it is now missing, so check next again)
     *       write lock end
     *
     * tid:4 next a
     *       read lock end (blocks)
     *
     * tid:3 commit
     *       release lock b
     *       release lock end
     *
     * tid:4 [next a]
     *       write lock end
     *       return null
     *
     * So, this test will fail if removeBinding does not make sure to check
     * that the next key exists after it obtains a lock on it.  This test only
     * requires it to check once.
     */
    @Test
    public void testRemoveBindingFoundPhantom() throws Exception {
	store.setBinding(txn, "a", 200);
	store.setBinding(txn, "b", 200);
	txn.commit();
	txn = createTransaction();				// tid:2
	store.setBinding(txn, "c", 300);
	Runner runner2 = new Runner(new RemoveBinding("b"));	// tid:3
	runner2.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertTrue((Boolean) runner2.getResult());
	runner = new Runner(new NextBoundName("a"));		// tid:4
	runner.assertBlocked();
	runner2.commit();
	assertSame(null, runner.getResult());
    }

    /**
     * A similar test, but this one requiring that removeBinding check
     * repeatedly for the latest next key.
     *
     * tid:1 create a
     * tid:1 create b
     *
     * tid:2 create c
     * 	     write lock c
     *	     write lock end
     *
     * tid:3 create d
     *       write lock d
     *       write lock end (blocks)
     *
     * tid:4 remove b
     *       write lock b
     *       write lock c (blocks)
     *
     * tid:2 abort
     *       release lock c
     *       release lock end
     *
     * tid:3 [create d]
     *	     write lock end
     *
     * tid:4 [remove b]
     *       write lock c (but it is now missing, so check next again)
     *       write lock d (blocks)
     *
     * tid:3 abort
     *       release lock d
     *       release lock end
     *
     * tid:4 [remove b]
     *       write lock d
     *       write lock end
     *
     * tid:5 next a
     *       read lock end (blocks)
     *
     * tid:4 commit
     *       release lock b
     *       release lock end
     *
     * tid:5 [next a]
     *       write lock end
     *       return null
     */
    @Test
    public void testRemoveBindingFoundPhantom2() throws Exception {
	store.setBinding(txn, "a", 200);
	store.setBinding(txn, "b", 200);
	txn.commit();
	txn = createTransaction();				// tid:2
	store.setBinding(txn, "c", 300);
	Runner runner2 = new Runner(new SetBinding("d", 400));	// tid:3
	runner2.assertBlocked();
	Runner runner3 = new Runner(new RemoveBinding("b"));	// tid:4
	runner3.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	runner2.getResult();
	runner3.assertBlocked();
	runner2.abort();
	assertTrue((Boolean) runner3.getResult());
	runner = new Runner(new NextBoundName("a"));		// tid:5
	runner.assertBlocked();
	runner3.commit();
	assertSame(null, runner.getResult());
    }

    /**
     * Test that nextBoundName checks repeatedly to be sure that the next name
     * exists.
     *
     * tid:1 create a
     *
     * tid:1 commit
     *
     * tid:2 create b
     * 	     write lock b
     *	     write lock end
     *
     * There are two cases depending on whether tid:2's lock on b blocks
     * tid:3's lock on c, due to a false lock conflict in the data store.
     *
     * False conflict:
     *
     * tid:3 create c
     *       write lock c (blocks in data store)
     *
     * tid:4 next a
     *	     get next a (blocks in data store)
     *
     * tid:2 abort
     *       release lock b
     *       release lock end
     *
     * tid:4 [next a]
     *       read lock end (get next a returned null)
     *	     return null
     *
     * tid:4 commit
     *	     release lock end
     *
     * tid:3 [create c]
     *	     write lock c (returns from data store)
     *       write lock end
     *
     * No false conflict:
     *
     * tid:3 create c
     *       write lock c
     *       write lock end (blocks)
     *
     * tid:4 next a
     *       read lock b (blocks)
     *
     * tid:2 abort
     *       release lock b
     *       release lock end
     *
     * tid:3 [create c]
     *	     write lock end
     *
     * tid:4 [next a]
     *       read lock b (but it is now missing, so check next again)
     *       read lock c (blocks)
     *
     * tid:3 abort
     *       release lock c
     *       release lock end
     *
     * tid:4 [next a]
     *       read lock c (but it is now missing, so check next again)
     *	     read lock end
     *	     return null
     */
    @Test
    public void testNextBoundNameLastPhantom() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();				// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner2 = new Runner(new SetBinding("c", 300));	// tid:3
	runner2.assertBlocked();
	Runner runner3 = new Runner(new NextBoundName("a"));	// tid:4
	runner3.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	if (runner2.blocked()) {
	    /* False lock conflicts might mean that runner3 blocks runner2 */
	    runner3.getResult();
	    runner3.commit();
	    assertSame(null, runner2.getResult());
	    runner2.commit();
	} else {
	    runner2.getResult();
	    runner3.assertBlocked();
	    runner2.abort();
	    assertSame(null, runner3.getResult());
	    runner3.commit();
	}
    }

    /**
     * Test that getBinding resamples the next key properly.
     *
     * tid:2 create b
     * 	     write lock b
     *	     write lock end
     *
     * There are two cases depending on whether tid:2's lock on b blocks
     * tid:3's lock on c, due to a false lock conflict in the data store.
     *
     * False conflict:
     *
     * tid:3 create c
     *       write lock c (blocks in data store)
     *
     * tid:4 get a
     *	     read lock a (blocks in data store)
     *
     * tid:2 abort
     *       release lock b
     *       release lock end
     *
     * tid:4 [get a]
     *       read lock a (get a returned not found)
     *	     read lock end
     *
     * tid:4 commit
     *	     release lock a
     *	     release lock end
     *
     * tid:3 [create c]
     *	     write lock c (returns from data store)
     *       write lock end
     *
     * No false conflict:
     *
     * tid:3 create c
     *       write lock c
     *       write lock end (blocks)
     *
     * tid:4 get a
     *       read lock a
     *       read lock b (blocks)
     *
     * tid:2 abort
     *       release lock b
     *       release lock end
     *
     * tid:3 [create c]
     *	     write lock end
     *
     * tid:4 [get a]
     *       read lock b (but it is now missing, so check next again)
     *       read lock c (blocks)
     *
     * tid:3 abort
     *       release lock c
     *       release lock end
     *
     * tid:4 [get a]
     *       read lock c (but it is now missing, so check again)
     *       read lock end
     *
     * tid:5 next a
     *       read lock end (blocks)
     *
     * tid:4 commit
     *       release lock b
     *       release lock end
     *
     * tid:5 [next a]
     *       write lock end
     *       return null
     */
    @Test
    public void testNextBoundNameLastPhantom2() throws Exception {
	txn.commit();
	txn = createTransaction();				// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner2 = new Runner(new SetBinding("c", 300));	// tid:3
	runner2.assertBlocked();
	Runner runner3 = new Runner(new GetBinding("a"));	// tid:4
	runner3.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	if (runner2.blocked()) {
	    /* False lock conflicts might mean that runner3 blocks runner2 */
	    assertSame(null, runner3.getResult());
	    runner3.commit();
	    runner2.getResult();
	    runner2.commit();
	} else {
	    runner2.getResult();
	    runner3.assertBlocked();
	    runner2.abort();
	    assertSame(null, runner3.getResult());
	    runner3.commit();
	}
    }

    /**
     * Test that setBinding resamples the next key properly.
     *
     * tid:2 create b
     * 	     write lock b
     *	     write lock end
     *
     * tid:3 create c
     *       write lock c
     *       write lock end (blocks)
     *
     * tid:4 create a
     *       write lock a
     *       write lock b (blocks)
     *
     * tid:2 abort
     *       release lock b
     *       release lock end
     *
     * tid:3 [create c]
     *	     write lock end
     *
     * tid:4 [create a]
     *       write lock b (but it is now missing, so check next again)
     *       write lock c (blocks)
     *
     * tid:3 abort
     *       release lock c
     *       release lock end
     *
     * tid:4 [create a]
     *       write lock c (but it is now missing, so check next again)
     *       write lock end
     *
     * tid:5 next a
     *       read lock end (blocks)
     *
     * tid:4 commit
     *       release lock b
     *       release lock end
     *
     * tid:5 [next a]
     *       write lock end
     *       return null
     */
    @Test
    public void testNextBoundNameLastPhantom3() throws Exception {
	txn.commit();
	txn = createTransaction();				// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner2 = new Runner(new SetBinding("c", 300));	// tid:3
	runner2.assertBlocked();
	Runner runner3 = new Runner(new SetBinding("a", 100));	// tid:4
	runner3.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	runner2.getResult();
	runner3.assertBlocked();
	runner2.abort();
	assertSame(null, runner3.getResult());
	runner3.commit();
    }

    /* -- Tests for isolation with multiple binding operations -- */

    @Test
    public void testGetBindingCreateSetBinding() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	txn.commit();
	txn = null;
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testGetBindingSetBindingTwice() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 200);
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 300);
	txn.commit();
	txn = null;
	assertEquals(Long.valueOf(300), runner.getResult());
    }

    @Test
    public void testGetBindingCreateRemoveBinding() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	txn.commit();
	txn = null;
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	txn.commit();
	txn = null;
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testRemoveBindingCreateRemoveBinding() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	txn.commit();
	txn = null;
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	txn.commit();
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testNextBoundNameCreateRemoveBinding() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	txn.commit();
	txn = null;
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");
	runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	txn.commit();
	txn = null;
	assertEquals("a", runner.getResult());
    }

    /* -- Test for isolation with aborted binding operations -- */

    @Test
    public void testGetBindingCreateBindingAborted() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingSetBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 200);
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testGetBindingRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");
	runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testRemoveBindingCreateBindingAborted() throws Exception {
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
    public void testRemoveBindingRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");
	runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testNextBoundNameCreateBindingAborted() throws Exception {
	txn.commit();
	txn = createTransaction();
	store.setBinding(txn, "a", 100);
	runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	txn.commit();
	txn = createTransaction();
	store.removeBinding(txn, "a");	
	runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	txn.abort(new RuntimeException());
	txn = null;
	assertEquals("a", runner.getResult());
    }

    /* -- Other methods and classes -- */

    /** Creates a transaction. */
    protected static DummyTransaction createTransaction() {
	DummyTransaction txn = new DummyTransaction(timeoutSuccess);
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
	return txn;
    }

    /** Runs actions in another thread. */
    static class Runner {

	/** The action to run. */
	private TxnCallable<Object> action;

	/** A task for running the action, or null if the runner is done. */
	private FutureTask<Object> task;

	/** The transaction for this thread. */
	private DummyTransaction txn;

	/** The thread. */
	private final Thread thread =
	    new Thread() {
		public void run() {
		    runInternal();
		}
	    };

	/**
	 * Creates an instance of this class that initially runs the specified
	 * action.
	 *
	 * @param	action the action to run
	 */
	Runner(TxnCallable<? extends Object> action) {
	    @SuppressWarnings("unchecked")
	    TxnCallable<Object> a = (TxnCallable<Object>) action;
	    synchronized (this) {
		this.action = a;
		task = new FutureTask<Object>(a);
	    };
	    thread.start();
	}

	/**
	 * Specifies another action to run.  This method should only be called
	 * if the previous action has completed.
	 *
	 * @param	action the action to run
	 */
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

	/**
	 * Commits the transaction.  This method should only be called if the
	 * previous action has completed.
	 */
	void commit() throws InterruptedException, TimeoutException {
	    setAction(new Commit());
	    getResult();
	    setDone();
	}

	/**
	 * Aborts the transaction.  This method should only be called if the
	 * previous action has completed.
	 */
	void abort() throws InterruptedException, TimeoutException {
	    setAction(new Abort());
	    getResult();
	    setDone();
	}

	/**
	 * Checks if the runner is blocked.
	 *
	 * @return	whether the runner is blocked
	 */
	boolean blocked() throws InterruptedException {
	    try {
		getTask().get(timeoutBlock, TimeUnit.MILLISECONDS);
		return false;
	    } catch (TimeoutException e) {
		return true;
	    } catch (RuntimeException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}

	/** Asserts that the runner is blocked. */
	void assertBlocked() throws InterruptedException {
	    assertTrue("The operation did not block", blocked());
	}

	/**
	 * Returns the result of the last action, throwing an exception if it
	 * is blocked.
	 *
	 * @return	the result of the last action
	 */
	Object getResult()
	    throws InterruptedException, TimeoutException
	{
	    try {
		return getTask().get(timeoutSuccess, TimeUnit.MILLISECONDS);
	    } catch (RuntimeException e) {
		throw e;
	    } catch (TimeoutException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}

	/* -- Private methods -- */

	/** The main method to run in the thread. */
	private void runInternal() {
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

	/**
	 * Returns the current task, waiting for the transaction to start.
	 */
	private synchronized FutureTask<Object> getTask()
	    throws InterruptedException
	{
	    while (txn == null) {
		wait();
	    }
	    return task;
	}

	/** Sets task to null to signify that the runner is done. */
	private synchronized void setDone() {
	    if (!task.isDone()) {
		throw new RuntimeException("Task is not done");
	    }
	    task = null;
	    notifyAll();
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

    /** Calls {@link DummyTransaction#abort}. */
    static class Abort extends TxnCallable<Void> {
	Abort() { }
	public Void call() {
	    getTransaction().abort(new RuntimeException());
	    return null;
	}
    }
}
