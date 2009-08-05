/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.UtilProperties;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
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
    protected static final Properties props =
	UtilProperties.createProperties();

    /**
     * The number of milliseconds to wait to see if an operation is successful.
     */
    protected static final long timeoutSuccess =
	Long.parseLong(props.getProperty("test.timeout.success", "1000"));

    /* Set the access coordinator's lock timeout */
    static {
	props.setProperty(LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
			  String.valueOf(timeoutSuccess));
    }

    /**
     * The number of milliseconds to wait to see if an operation is blocked.
     */
    protected static final long timeoutBlock =
	Long.parseLong(
	    props.getProperty(
		"test.timeout.block", String.valueOf(timeoutSuccess / 10)));

    /** The test environment, using a locking access coordinator. */
    protected static final BasicDataStoreTestEnv env =
	new BasicDataStoreTestEnv(
	    props, LockingAccessCoordinator.class.getName());

    /** The transaction proxy. */
    protected static final DummyTransactionProxy txnProxy = env.txnProxy;

    /** The access coordinator to test. */
    protected static final AccessCoordinatorHandle accessCoordinator =
	env.accessCoordinator;

    /** The data store to test. */
    protected static DataStore store;

    /** A test value for an object ID. */
    private static final byte[] value = { 1 };

    /** Another test value for an object ID. */
    private static final byte[] secondValue = { 2 };

    /** An initial, open transaction. */
    protected DummyTransaction txn;
    
    /** The object ID of a new object created in the transaction. */
    private long id;

    /**
     * Create the properties, access coordinator, and data store if needed;
     * then create a transaction, create an object, and clear existing
     * bindings.
     */
    @Before
    public void before() {
	if (store == null) {
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

    /** Abort the transaction and the runners, if not null. */
    @After
    public void after() throws Exception {
	if (txn != null) {
	    abortTransaction();
	}
	txnProxy.setCurrentTransaction(null);
	Runner.abortAllOpen();
    }

    /**
     * Creates the data store.
     *
     * @return	the data store
     */
    protected abstract DataStore createDataStore();

    /* -- Tests -- */

    /* -- Test object access -- */

    /*
     * Operations to test:
     *
     * Operation			Lock
     * ---------			----
     * markForUpdate			WRITE
     * getObject forUpdate=false	READ
     * getObject forUpdate=true		WRITE
     * setObject			WRITE
     * setObjects			WRITE
     * removeObject			WRITE
     *
     * Use getObject with forUpdate=false or true as the probes for checking
     * for conflicts.
     */

    /* -- Test markForUpdate -- */

    @Test
    public void testMarkForUpdateRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(new MarkForUpdate(id));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    @Test
    public void testMarkForUpdateWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(new MarkForUpdate(id));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    /* -- Test getObject forUpdate=false -- */

    @Test
    public void testGetObjectRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(new GetObject(id, false));
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    @Test
    public void testGetObjectWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(new GetObject(id, false));
	runner.assertBlocked();
	commitTransaction();
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    /* -- Test getObject forUpdate=true -- */

    @Test
    public void testGetObjectForUpdateRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(new GetObject(id, true));
	runner.assertBlocked();
	commitTransaction();
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    @Test
    public void testGetObjectForUpdateWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(new GetObject(id, true));
	runner.assertBlocked();
	commitTransaction();
	assertArrayEquals(value, (byte[]) runner.getResult());
    }

    /* -- Test setObject -- */

    @Test
    public void testSetObjectRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(new SetObject(id, secondValue));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    @Test
    public void testSetObjectWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(new SetObject(id, secondValue));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    /* -- Test setObjects -- */

    @Test
    public void testSetObjectsRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    @Test
    public void testSetObjectsWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(
	    new SetObjects(new long[] { id }, new byte[][] { secondValue }));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
    }

    /* -- Test removeObject -- */

    @Test
    public void testRemoveObjectRead() throws Exception {
	newTransaction();
	store.getObject(txn, id, false);
	Runner runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveObjectWrite() throws Exception {
	newTransaction();
	store.getObject(txn, id, true);
	Runner runner = new Runner(new RemoveObject(id));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    /* -- Test name access -- */

    /*
     * Operations to test:
     *
     * Operation			Lock	Lock next
     * ---------			----	---------
     *
     * getBinding notFound		READ	READ
     * getBinding found			READ
     * setBinding create		WRITE	WRITE
     * setBinding existing		WRITE
     * removeBinding notFound		WRITE	READ
     * removeBinding found		WRITE	WRITE
     * nextBoundName				READ
     *
     * Use getBinding and setBinding to check for conflicts, probing keys
     * before and after as needed to check locks on the key and the next key.
     */

    /* -- Test getBinding notFound -- */

    @Test
    public void testGetBindingNotFoundRead() throws Exception {
	newTransaction();
	getBindingNotFound("a");
	Runner runner = new Runner(new GetBinding("a"));
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingNotFoundWritePrev() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new GetBinding("b"));
	runner.assertBlocked();
	commitTransaction();
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingNotFoundWriteNext() throws Exception {
	newTransaction();
	store.setBinding(txn, "b", 100);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertSame(null, runner.getResult());
    }

    /* -- Test getBinding found -- */

    @Test
    public void testGetBindingFoundRead() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.getBinding(txn, "a");
	Runner runner = new Runner(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testGetBindingFoundWrite() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testGetBindingFoundWriteNext() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "b", 200);
	Runner runner = new Runner(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    /* -- Test setBinding create -- */

    @Test
    public void testSetBindingCreateReadPrev() throws Exception {
	newTransaction();
	getBindingNotFound("a");
	Runner runner = new Runner(new SetBinding("b", 100));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("b"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testSetBindingCreateReadNext() throws Exception {
	newTransaction();
	getBindingNotFound("b");
	Runner runner = new Runner(new SetBinding("a", 100));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testSetBindingCreateWritePrev() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new SetBinding("b", 200));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("b"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testSetBindingCreateWriteNext() throws Exception {
	newTransaction();
	store.setBinding(txn, "b", 200);
	Runner runner = new Runner(new SetBinding("a", 100));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    /* -- Test setBinding existing -- */

    @Test
    public void testSetBindingExistingRead() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.getBinding(txn, "a");
	Runner runner = new Runner(new SetBinding("a", 200));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testSetBindingExistingReadNext() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	getBindingNotFound("b");
	Runner runner = new Runner(new SetBinding("a", 200));
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testSetBindingExistingWrite() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new SetBinding("a", 300));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(300), runner.getResult());
    }
   
    @Test
    public void testSetBindingExistingWriteNext() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "b", 200);
	Runner runner = new Runner(new SetBinding("a", 300));
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(300), runner.getResult());
    }

    /* -- Test removeBinding notFound -- */

    @Test
    public void testRemoveBindingNotFoundReadPrev() throws Exception {
	/*
	 * Note that "b" is not bound, so the next key for "a" is null (end),
	 * not "b".  The fact that removing "b" write locks "b" does not block
	 * against a read lock on "a" or null.
	 */
	newTransaction();
	getBindingNotFound("a");
	Runner runner = new Runner(new RemoveBinding("b"));
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingNotFoundReadNext() throws Exception {
	store.setBinding(txn, "b", 200);
	newTransaction();
	store.getBinding(txn, "b");
	Runner runner = new Runner(new RemoveBinding("a"));
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingNotFoundWritePrev() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new RemoveBinding("b"));
	runner.assertBlocked();
	commitTransaction();
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingNotFoundWriteNext() throws Exception {
	store.setBinding(txn, "b", 100);
	newTransaction();
	store.setBinding(txn, "b", 200);
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertFalse((Boolean) runner.getResult());
    }

    /* -- Test removeBinding found -- */

    @Test
    public void testRemoveBindingFoundRead() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.getBinding(txn, "a");
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingFoundReadNext() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 100);
	newTransaction();
	store.getBinding(txn, "b");
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingFoundWrite() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingFoundWriteNext() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 100);
	newTransaction();
	store.setBinding(txn, "b", 200);
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    /* -- Test nextBoundName -- */

    @Test
    public void testNextBoundNameRead() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.getBinding(txn, "a");
	Runner runner = new Runner(new NextBoundName("a"));
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameReadNext() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 100);
	newTransaction();
	store.getBinding(txn, "b");
	Runner runner = new Runner(new NextBoundName("a"));
	assertEquals("b", runner.getResult());
    }

    @Test
    public void testNextBoundNameWrite() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new NextBoundName("a"));
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameWriteNext() throws Exception {
	store.setBinding(txn, "a", 100);
	store.setBinding(txn, "b", 100);
	newTransaction();
	store.setBinding(txn, "b", 100);
	Runner runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	commitTransaction();
	assertEquals("b", runner.getResult());
    }

    /* -- Tests for phantom bindings -- */

    /**
     * Test that removing a binding locks the next name even when the identity
     * of that name changes because a conflicting transaction aborts.
     *
     * Here's the blow-by-blow:
     *
     * tid:1 create a
     *	     create b
     *	     commit
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
	newTransaction();					// tid:2
	store.setBinding(txn, "c", 300);
	Runner runner3 = new Runner(new RemoveBinding("b"));	// tid:3
	runner3.assertBlocked();
	abortTransaction();
	assertTrue((Boolean) runner3.getResult());
	Runner runner4 = new Runner(new NextBoundName("a"));	// tid:4
	runner4.assertBlocked();
	runner3.commit();
	assertSame(null, runner4.getResult());
    }

    /**
     * A similar test, but this one requiring that removeBinding check
     * repeatedly for the latest next key.
     *
     * tid:1 create a
     *	     create b
     *	     commit
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
	newTransaction();					// tid:2
	store.setBinding(txn, "c", 300);
	Runner runner3 = new Runner(new SetBinding("d", 400));	// tid:3
	runner3.assertBlocked();
	Runner runner4 = new Runner(new RemoveBinding("b"));	// tid:4
	runner4.assertBlocked();
	abortTransaction();
	runner3.getResult();
	runner4.assertBlocked();
	runner3.abort();
	assertTrue((Boolean) runner4.getResult());
	Runner runner5 = new Runner(new NextBoundName("a"));	// tid:5
	runner5.assertBlocked();
	runner4.commit();
	assertSame(null, runner5.getResult());
    }

    /**
     * Test that nextBoundName checks repeatedly to be sure that the next name
     * exists.
     *
     * tid:1 create a
     *       commit
     *
     * tid:2 create b
     * 	     write lock b
     *	     write lock end
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
	newTransaction();					// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner3 = new Runner(new SetBinding("c", 300));	// tid:3
	runner3.assertBlocked();
	Runner runner4 = new Runner(new NextBoundName("a"));	// tid:4
	runner4.assertBlocked();
	abortTransaction();
	runner3.getResult();
	runner4.assertBlocked();
	runner3.abort();
	assertSame(null, runner4.getResult());
    }

    /**
     * Test that getBinding resamples the next key properly.
     *
     * tid:2 create b
     * 	     write lock b
     *	     write lock end
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
     */
    @Test
    public void testGetBindingPhantom() throws Exception {
	newTransaction();					// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner3 = new Runner(new SetBinding("c", 300));	// tid:3
	runner3.assertBlocked();
	Runner runner4 = new Runner(new GetBinding("a"));	// tid:4
	runner4.assertBlocked();
	abortTransaction();
	runner3.getResult();
	runner4.assertBlocked();
	runner3.abort();
	assertSame(null, runner4.getResult());
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
     */
    @Test
    public void testSettingBindingPhantom() throws Exception {
	newTransaction();					// tid:2
	store.setBinding(txn, "b", 200);
	Runner runner3 = new Runner(new SetBinding("c", 300));	// tid:3
	runner3.assertBlocked();
	Runner runner4 = new Runner(new SetBinding("a", 100));	// tid:4
	runner4.assertBlocked();
	abortTransaction();
	runner3.getResult();
	runner4.assertBlocked();
	runner3.abort();
	assertSame(null, runner4.getResult());
    }

    /* -- Tests for isolation with multiple binding operations -- */

    @Test
    public void testGetBindingCreateSetBinding() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	commitTransaction();
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testGetBindingSetBindingTwice() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 300);
	commitTransaction();
	assertEquals(Long.valueOf(300), runner.getResult());
    }

    @Test
    public void testGetBindingCreateRemoveBinding() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	commitTransaction();
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	commitTransaction();
	assertEquals(Long.valueOf(200), runner.getResult());
    }

    @Test
    public void testRemoveBindingCreateRemoveBinding() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	commitTransaction();
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	commitTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testNextBoundNameCreateRemoveBinding() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	store.removeBinding(txn, "a");
	commitTransaction();
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveCreateBinding() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");
	Runner runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	store.setBinding(txn, "a", 200);
	commitTransaction();
	assertEquals("a", runner.getResult());
    }

    /* -- Test for isolation with aborted binding operations -- */

    @Test
    public void testGetBindingCreateBindingAborted() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	abortTransaction();
	assertSame(null, runner.getResult());
    }

    @Test
    public void testGetBindingSetBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	abortTransaction();
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testGetBindingRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");
	Runner runner = new Runner(new GetBinding("a"));
	runner.assertBlocked();
	abortTransaction();
	assertEquals(Long.valueOf(100), runner.getResult());
    }

    @Test
    public void testRemoveBindingCreateBindingAborted() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	abortTransaction();
	assertFalse((Boolean) runner.getResult());
    }

    @Test
    public void testRemoveBindingRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	abortTransaction();
	assertTrue((Boolean) runner.getResult());
    }

    @Test
    public void testNextBoundNameCreateBindingAborted() throws Exception {
	newTransaction();
	store.setBinding(txn, "a", 100);
	Runner runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	abortTransaction();
	assertSame(null, runner.getResult());
    }

    @Test
    public void testNextBoundNameRemoveBindingAborted() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.removeBinding(txn, "a");	
	Runner runner = new Runner(new NextBoundName(null));
	runner.assertBlocked();
	abortTransaction();
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

    /** Commits the current transaction and starts a new one. */
    protected void newTransaction() throws Exception {
	txn.commit();
	txn = createTransaction();
    }

    /** Commits the current transaction and sets it to null. */
    protected void commitTransaction() throws Exception {
	txn.commit();
	txn = null;
    }

    /** Aborts the current transaction and sets it to null. */
    protected void abortTransaction() {
	txn.abort(new RuntimeException());
	txn = null;
    }

    /** Gets a binding that is expected to not be present. */
    protected void getBindingNotFound(String name) {
	try {
	    store.getBinding(txn, name);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
    }

    /** Removes a binding that is expected to not be present. */
    protected void removeBindingNotFound(String name) {
	try {
	    store.removeBinding(txn, name);
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	}
    }

    /**
     * A utility class for running a sequence of actions under a new
     * transaction in another thread.  A newly created {@code Runner} starts a
     * thread and a new transaction.  It passes the transaction to the initial
     * action, using {@link TxnCallable#setTransaction}, so that the action can
     * use it as part of its operations.  When the runner is done running the
     * first action, it then waits for additional actions to be specified with
     * calls to {@link #setAction}, or until the transaction and thread are
     * ended by calling {@link #commit} or {@link #abort}. <p>
     *
     * Callers can use the {@link #blocked}, {@link #assertBlocked}, or {@link
     * #getResult} methods to interrogate the state of the runner's activities
     * from another thread.
     */
    static class Runner {

	/** All runners that have not been committed or aborted. */
	private static final Set<Runner> openRunners = new HashSet<Runner>();

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
	    openRunners.add(this);
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

	/** Aborts all open runners. */
	static void abortAllOpen()
	    throws InterruptedException, TimeoutException
	{
	    for (Runner runner : openRunners) {
		runner.abort();
	    }
	    openRunners.clear();
	}

	/* -- Private methods -- */

	/** The main method to run in the thread. */
	private void runInternal() {
	    FutureTask<?> t;
	    synchronized (this) {
		txn = createTransaction();
		action.setTransaction(txn);
		t = task;
		notifyAll();
	    }
	    while (true) {
		t.run();
		synchronized (this) {
		    try {
			while (task == t) {
			    wait();
			}
		    } catch (InterruptedException e) {
			break;
		    }
		    if (task == null) {
			break;
		    }
		    action.setTransaction(txn);
		    t = task;
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
	private void setDone() throws InterruptedException {
	    openRunners.remove(this);
	    FutureTask<Object> t = getTask();
	    if (!task.isDone()) {
		throw new RuntimeException("Task is not done");
	    }
	    synchronized (this) {
		if (task != t) {
		    throw new RuntimeException("Task changed");
		}
		task = null;
		notifyAll();
	    }
	}
    }

    /**
     * A {@code Callable} that can be supplied to a {@link Runner} to perform
     * an action in the context of a transaction, which the runner will supply
     * through a call to {@link #setTransaction}.
     */
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
