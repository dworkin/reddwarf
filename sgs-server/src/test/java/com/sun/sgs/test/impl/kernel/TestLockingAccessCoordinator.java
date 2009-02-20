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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator.LockConflict;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator.LockConflictType;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.NameRunner;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link LockingAccessCoordinator} class. */
@RunWith(NameRunner.class)
public class TestLockingAccessCoordinator extends Assert {

    /** The transaction proxy, for creating transactions. */
    private static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** A profile collector, for reporting accesses. */
    private static final DummyProfileCollectorHandle profileCollector =
	new DummyProfileCollectorHandle();

    /** An exception to use for aborting transactions. */
    private static final Exception ABORT_EXCEPTION = new Exception();

    /** Override for the lock timeout. */
    private static long lockTimeout;

    /** Override for the number of key maps. */
    private static int numKeyMaps;

    /** The configuration properties. */
    private Properties properties;

    /** The access coordinator to test. */
    private LockingAccessCoordinator coordinator;

    /** An active transaction. */
    private DummyTransaction txn;

    /** An access reporter obtained from the coordinator. */
    private AccessReporter<String> reporter;

    /** Update the lock timeout and number of key maps. */
    @BeforeClass
    public static void beforeClass() {
	lockTimeout = Long.getLong("test.lockTimeout", -1);
	numKeyMaps = Integer.getInteger("test.numKeyMaps", -1);
    }

    /** Initialize fields for test methods. */
    @Before
    public void before() throws Exception {
        properties = new Properties();
	if (lockTimeout > 0) {
	    properties.setProperty(
		LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
		String.valueOf(lockTimeout));
	}
	if (numKeyMaps > 0) {
	    properties.setProperty(
		LockingAccessCoordinator.NUM_KEY_MAPS_PROPERTY,
		String.valueOf(numKeyMaps));
	}
	coordinator = new LockingAccessCoordinator(
	    properties, txnProxy, profileCollector);
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	coordinator.notifyNewTransaction(txn, 0, 1);
	reporter = coordinator.registerAccessSource("s", String.class);
    }

    /** Clear transaction state. */
    @After
    public void after() throws Exception {
	if (txn != null) {
	    txn.commit();
	    txn = null;
	}
	txnProxy.setCurrentTransaction(null);
    }

    /* -- Tests -- */

    /* -- Test constructor -- */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProperties() {
	new LockingAccessCoordinator(null, txnProxy, profileCollector);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullTxnProxy() {
	new LockingAccessCoordinator(properties, null, profileCollector);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProfileCollector() {
	new LockingAccessCoordinator(properties, txnProxy, null);
    }

    @Test
    public void testConstructorIllegalLockTimeout() {
	properties.setProperty(
	    LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY, "-37");
	try {
	    new LockingAccessCoordinator(
		properties, txnProxy, profileCollector);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorIllegalNumKeyMaps() {
	String[] values = { "0", "-50" };
	for (String value : values) {
	    properties.setProperty(
		LockingAccessCoordinator.NUM_KEY_MAPS_PROPERTY, value);
	    try {
		new LockingAccessCoordinator(
		    properties, txnProxy, profileCollector);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    /* -- Test registerAccessSource -- */

    @Test(expected=NullPointerException.class)
    public void testRegisterAccessSourceNullSourceName() {
	coordinator.registerAccessSource(null, Object.class);
    }

    @Test(expected=NullPointerException.class)
    public void testRegisterAccessSourceNullObjectIdType() {
	coordinator.registerAccessSource("a", null);
    }

    /* -- Test getConflictingTransaction -- */

    @Test(expected=NullPointerException.class)
    public void testGetConflictingTransactionNullTxn() {
	coordinator.getConflictingTransaction(null);
    }

    /* -- Test notifyNewTransaction -- */

    @Test(expected=NullPointerException.class)
    public void testNotifyNewTransactionNullTxn() {
	coordinator.notifyNewTransaction(null, 0, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotifyNewTransactionIllegalRequestedStartTime() {
	coordinator.notifyNewTransaction(txn, -1, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotifyNewTransactionIllegalTryCount() {
	coordinator.notifyNewTransaction(txn, 0, 0);
    }

    /* -- Test AccessReporter.reportObjectAccess -- */

    @Test
    public void testReportObjectAccessNullTxn() {
	try {
	    reporter.reportObjectAccess(null, "id", AccessType.READ);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(null, "id", AccessType.READ, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testReportObjectAccessNullObjId() {
	try {
	    reporter.reportObjectAccess(null, AccessType.READ);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(txn, null, AccessType.READ);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(null, AccessType.READ, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(txn, null, AccessType.READ, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testReportObjectAccessNullAccessType() {
	try {
	    reporter.reportObjectAccess("id", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(txn, "id", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess("id", null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.reportObjectAccess(txn, "id", null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testReportObjectAccessMisc() throws Exception {
	AccessReporter<String> reporter2 =
	    coordinator.registerAccessSource("s2", String.class);
	Object[] expected = {
	    "s", "read1", AccessType.READ,
	    "s2", "read1", AccessType.READ,
	    "s", "upgrade", AccessType.READ,
	    "s", "write1", AccessType.WRITE,
	    "s", "upgrade", AccessType.WRITE
	};
	int numExpected = expected.length / 3;
	reporter.reportObjectAccess("read1", AccessType.READ);
	reporter2.reportObjectAccess("read1", AccessType.READ);
	reporter.reportObjectAccess("upgrade", AccessType.READ);
	reporter.reportObjectAccess("upgrade", AccessType.READ);
	reporter.reportObjectAccess("write1", AccessType.WRITE);
	reporter.reportObjectAccess("read1", AccessType.READ);
	reporter.reportObjectAccess("write1", AccessType.WRITE);
	reporter.reportObjectAccess("upgrade", AccessType.WRITE);
	reporter.reportObjectAccess("upgrade", AccessType.WRITE);
	txn.commit();
	txn = null;
	AccessedObjectsDetail detail = 
	    profileCollector.getAccessedObjectsDetail();
	List<? extends AccessedObject> accesses =
	    detail.getAccessedObjects();
	assertSame("Expected " + numExpected + " accesses, found " +
		   accesses.size(),
		   numExpected,
		   accesses.size());
	for (int i = 0; i < expected.length; i += 3) {
	    AccessedObject result = accesses.get(i / 3);
	    assertEquals(expected[i], result.getSource());
	    assertEquals(expected[i + 1], result.getObjectId());
	    assertEquals(expected[i + 2], result.getAccessType());
	}
    }

    /* -- Test AccessReporter.setObjectDescription -- */

    @Test(expected=NullPointerException.class)
    public void testSetObjectDescriptionNullTxn() {
	reporter.setObjectDescription(null, "id", "desc");
    }

    @Test
    public void testSetObjectDescriptionNullObjId() {
	try {
	    reporter.setObjectDescription(null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.setObjectDescription(txn, null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    /* -- Test lockNoWait -- */

    @Test
    public void testLockNoWaitGranted() {
	assertGranted(acquireLock(txn, "s1", "o1", false));
    }

    /* -- Test lock conflicts -- */

    /**
     * Test read/write conflict
     *
     * txn2: read o1	=> granted
     * txn:  write o2	=> blocked
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testReadWriteConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn2, "s1", "o1", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o1", true);
	locker.assertBlocked();
	txn2.commit();
	assertGranted(locker.getResult());
    }

    /**
     * Test read/upgrade conflict
     *
     * txn2: read o1	=> granted
     * txn:  read o1	=> granted
     * txn:  write o1	=> blocked
     * txn2: commit
     * txn:		=> granted
     */
    @Test
    public void testUpgradeConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn2, "s1", "o1", false));
	assertGranted(acquireLock(txn, "s1", "o1", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o1", true);
	locker.assertBlocked();
	txn2.commit();
	assertGranted(locker.getResult());
    }

    /* -- Test deadlocks -- */

    /**
     * Test read/write deadlock
     *
     * txn is older than txn2
     *
     * txn:  read o1	=> granted
     * txn2: read o2	=> granted
     * txn:  write o2	=> blocked
     * txn2: write o1	=> deadlock
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testReadWriteDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(acquireLock(txn2, "s1", "o1", true), txn);
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /**
     * Test upgrade/upgrade deadlock
     *
     * txn is older than txn2
     *
     * txn:  read o1	=> granted
     * txn2: read o1	=> granted
     * txn:  write o1	=> blocked
     * txn2: write o1	=> deadlock
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testUpgradeDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o1", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o1", true);
	locker.assertBlocked();
	assertDeadlock(acquireLock(txn2, "s1", "o1", true), txn);
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /**
     * Test deadlock with three parties in a ring, with last locker the victim.
     *
     * txn is oldest, txn2 in the middle, txn3 is youngest
     *
     * txn:  read o1	=> granted
     * txn2: read o2	=> granted
     * txn3: read o3	=> granted
     * txn:  write o2	=> blocked
     * txn2: write o3	=> blocked
     * txn3: write o1	=> deadlock
     * txn3: abort
     * txn2:		=> granted
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testReadWriteLoopDeadlock1() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	assertGranted(acquireLock(txn3, "s1", "o3", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o3", true);
	locker2.assertBlocked();
	assertDeadlock(acquireLock(txn3, "s1", "o1", true), txn, txn2);
	txn3.abort(ABORT_EXCEPTION);
	locker.assertBlocked();
	assertGranted(locker2.getResult());
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /**
     * Test deadlock with three parties in a ring, with middle locker the
     * victim.
     *
     * txn is oldest, txn2 in the middle, txn3 is youngest
     *
     * txn:  read o1	=> granted
     * txn2: read o2	=> granted
     * txn3: read o3	=> granted
     * txn2: write o3	=> blocked
     * txn3: write o1	=> blocked
     * txn1: write o2	=> blocked
     * txn3:		=> deadlock
     * txn3: abort
     * txn2:		=> granted
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testReadWriteLoopDeadlock2() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	assertGranted(acquireLock(txn3, "s1", "o3", false));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o3", true);
	locker2.assertBlocked();
	AcquireLock locker3 = new AcquireLock(txn3, "s1", "o1", true);
	locker3.assertBlocked();
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(locker3.getResult(), txn, txn2);
	txn3.abort(ABORT_EXCEPTION);
	locker.assertBlocked();
	assertGranted(locker2.getResult());
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /* -- Other methods and classes -- */

    /**
     * A dummy implementation of {@code ProfileCollectorHandle}, just to
     * accept and provide access to the AccessedObjectsDetail.
     */
    static class DummyProfileCollectorHandle
	implements ProfileCollectorHandle
    {
	private AccessedObjectsDetail detail;

	DummyProfileCollectorHandle() { }

	/**
	 * Returns the AccessedObjectsDetail last supplied to a call to
	 * setAccessedObjectsDetail.
	 */
	synchronized AccessedObjectsDetail getAccessedObjectsDetail() {
	    AccessedObjectsDetail result = detail;
	    detail = null;
	    return result;
	}

	/* -- Implement ProfileCollectorHandle -- */

	public synchronized void setAccessedObjectsDetail(
	    AccessedObjectsDetail detail)
	{
	    this.detail = detail;
	}

	/* -- Unsupported methods -- */

	public void notifyThreadAdded() { fail("Not supported"); }
	public void notifyThreadRemoved() { fail("Not supported"); }
	public void startTask(KernelRunnable task, Identity owner,
			      long scheduledStartTime, int readyCount)
	{
	    fail("Not supported");
	}
	public void noteTransactional(byte[] transactionId) {
	    fail("Not supported");
	}
	public void addParticipant(
	    ProfileParticipantDetail participantDetail)
	{
	    fail("Not supported");
	}
	public void finishTask(int tryCount) { fail("Not supported"); }
	public void finishTask(int tryCount, Throwable t) {
	    fail("Not supported");
	}
	public ProfileCollector getCollector() {
	    throw new AssertionError("Not supported");
	}
    }

    /* -- Methods for asserting the lock conflict status -- */

    /** Asserts that the lock was granted. */
    static void assertGranted(LockConflict conflict) {
	if (conflict != null) {
	    fail("Expected no conflict: " + conflict);
	}
    }

    /**
     * Asserts that the request resulted in a deadlock. The conflictingTxns
     * argument specifies all of the other transactions that were involved in
     * the conflict.
     */
    static void assertDeadlock(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.DEADLOCK, conflict, conflictingTxns);
    }

    /**
     * Asserts that the request resulted in a timeout. The conflictingTxns
     * argument specifies all of the other transactions that were involved in
     * the conflict.
     */
    static void assertTimeout(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.TIMEOUT, conflict, conflictingTxns);
    }

    /**
     * Asserts that the request was denied, with the specified type of
     * conflict. The conflictingTxns argument specifies all of the other
     * transactions that were involved in the conflict.
     */
    static void assertDenied(LockConflictType type,
			     LockConflict conflict,
			     Transaction... conflictingTxns)
    {
	if (conflict == null || conflict.getType() != type) {
	    fail("Expected " + type + ": " + conflict);
	}
	assertMember(conflictingTxns, conflict.getConflictingTxn());
    }

    /**
     * Asserts that the element is one of the elements of the array, which
     * should not be empty.
     */
    static <T> void assertMember(T[] array, T item) {
	assertTrue("Must have some members", array.length > 0);
	for (T e : array) {
	    if (item == null ? e == null : item.equals(e)) {
		return;
	    }
	}
	fail("Expected member of " + Arrays.toString(array) +
	     "\n  found " + item);
    }

    /** Attempts to acquire a lock on behalf of a transaction. */
    LockConflict acquireLock(
	DummyTransaction txn, String source, Object objectId, boolean forWrite)
    {
	return new AcquireLock(txn, source, objectId, forWrite).getResult();
    }

    /**
     * A utility class for managing an attempt to acquire a lock.  Use an
     * instance of this class for attempts that block.
     */
    class AcquireLock extends Thread {
	private final DummyTransaction txn;
	private final String source;
	private final Object objectId;
	private final boolean forWrite;

	/**
	 * Set to true when the initial attempt to acquire the lock is
	 * complete, so we know whether the attempt blocked.
	 */
	private boolean started = false;

	/** Set to true if the initial attempt to acquire the lock blocked. */
	private boolean blocked = false;

	/** Set to true when the attempt to acquire the lock is done. */
	private boolean done = false;

	/** Set to the result of the lock attempt. */
	private LockConflict result = null;

	/** Set to any exception thrown during the lock attempt. */
	private Throwable exception = null;

	/**
	 * Creates an instance that starts a thread to acquire a lock on behalf
	 * of a transaction.
	 */
	AcquireLock(DummyTransaction txn,
		    String source, Object objectId, boolean forWrite)
	{
	    setDaemon(true);
	    this.txn = txn;
	    this.source = source;
	    this.objectId = objectId;
	    this.forWrite = forWrite;
	    start();
	}

	public void run() {
	    try {
		LockConflict conflict;
		boolean wait = false;
		synchronized (this) {
		    started = true;
		    notifyAll();
		    conflict = coordinator.lockNoWait(
			txn, source, objectId, forWrite, null);
		    if (conflict != null
			&& conflict.getType() == LockConflictType.BLOCKED)
		    {
			blocked = true;
			wait = true;
		    }
		}
		/*
		 * Don't synchronize on this object while waiting for the lock,
		 * to avoid deadlock.
		 */
		if (wait) {
		    conflict = coordinator.waitForLock(txn);
		}
		synchronized (this) {
		    result = conflict;
		    done = true;
		    notifyAll();
		}
	    } catch (Throwable e) {
		synchronized (this) {
		    exception = e;
		    done = true;
		    notifyAll();
		}
	    }
	}

	/** Checks if the initial attempt to obtain the lock blocked. */
	boolean blocked() {
	    synchronized (this) {
		while (!started) {
		    try {
			wait();
		    } catch (InterruptedException e) {
			break;
		    }
		}
		return blocked;
	    }
	}

	/**
	 * Asserts that the initial attempt to obtain the lock should have
	 * blocked.
	 */
	void assertBlocked() {
	    assertTrue("The lock attempt did not block", blocked());
	}

	/** Returns the result of attempting to obtain the lock. */
	synchronized LockConflict getResult() {
	    long now = System.currentTimeMillis();
	    /* Only wait a second */
	    long stop = now + 1000;
	    while (!done && now < stop) {
		try {
		    wait(stop - now);
		    now = System.currentTimeMillis();
		} catch (InterruptedException e) {
		    break;
		}
	    }
	    assertTrue("The lock attempt is not done", done);
	    if (exception == null) {
		return result;
	    } else if (exception instanceof RuntimeException) {
		throw (RuntimeException) exception;
	    } else if (exception instanceof Error) {
		throw (Error) exception;
	    } else {
		throw new RuntimeException(
		    "Unexpected exception: " + exception, exception);
	    }
	}
    }
}
