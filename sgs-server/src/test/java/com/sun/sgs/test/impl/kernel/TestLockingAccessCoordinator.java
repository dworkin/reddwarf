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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator.LockConflict;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator.LockConflictType;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionInterruptedException;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link LockingAccessCoordinator} class. */
@RunWith(FilteredNameRunner.class)
public class TestLockingAccessCoordinator
    extends BasicAccessCoordinatorTest<LockingAccessCoordinator>
{
    /** Override for the lock timeout. */
    private static long lockTimeout;

    /** Override for the number of key maps. */
    private static int numKeyMaps;

    /** Update the lock timeout and number of key maps. */
    @BeforeClass
    public static void beforeClass() {
	lockTimeout = Long.getLong("test.lockTimeout", -1);
	numKeyMaps = Integer.getInteger("test.numKeyMaps", -1);
    }

    /** Initialize fields for test methods. */
    protected void init() {
	if (lockTimeout > 0 &&
	    properties.getProperty(
		LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY) == null)
	{
	    properties.setProperty(
		LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
		String.valueOf(lockTimeout));
	}
	if (numKeyMaps > 0 &&
	    properties.getProperty(
		LockingAccessCoordinator.NUM_KEY_MAPS_PROPERTY) == null)
	{
	    properties.setProperty(
		LockingAccessCoordinator.NUM_KEY_MAPS_PROPERTY,
		String.valueOf(numKeyMaps));
	}
	super.init();
    }

    /** Creates a {@code LockingAccessCoordinator}. */
    protected LockingAccessCoordinator createAccessCoordinator() {
	return new LockingAccessCoordinator(
	    properties, txnProxy, profileCollector);
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
	String[] values = { "0", "-37" };
	for (String value : values) {
	    properties.setProperty(
		LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY, value);
	    try {
		new LockingAccessCoordinator(
		    properties, txnProxy, profileCollector);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
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

    /* -- Test AccessedObjectsDetail more -- */

    @Test
    public void testAccessedObjectsDetailTimeout() throws Exception {
	reporter.reportObjectAccess(txn, "o1", AccessType.WRITE);
	DummyTransaction txn2 = new DummyTransaction(1);
	coordinator.notifyNewTransaction(txn2, 0, 1);
	Thread.sleep(2);
	try {
	    reporter.reportObjectAccess(
		txn2, "o1", AccessType.WRITE, "Object 1");
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	}
	AccessedObjectsDetail detail =
	    profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail, "s", "o1", AccessType.WRITE, "Object 1");
	assertEquals(ConflictType.ACCESS_NOT_GRANTED,
		     detail.getConflictType());
	assertArrayEquals(txn.getId(), detail.getConflictingId());
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	detail = profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail, "s", "o1", AccessType.WRITE, null);
	assertEquals(ConflictType.NONE, detail.getConflictType());
	assertEquals(null, detail.getConflictingId());
    }

    @Test
    public void testAccessedObjectsDetailTimeoutDescriptionFails()
	throws Exception
    {
	reporter.reportObjectAccess(txn, "o1", AccessType.WRITE);
	DummyTransaction txn2 = new DummyTransaction(1);
	coordinator.notifyNewTransaction(txn2, 0, 1);
	Thread.sleep(2);
	try {
	    reporter.reportObjectAccess(
		txn2, "o1", AccessType.WRITE,
		new Object() {
		    public String toString() {
			throw new RuntimeException();
		    }
		});
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAccessedObjectsDetailDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	reporter.reportObjectAccess(txn, "o1", AccessType.READ);
	reporter.reportObjectAccess(txn2, "o2", AccessType.READ);
	AcquireLock locker = new AcquireLock(txn, "s", "o2", true);
	locker.assertBlocked();
	try {
	    reporter.reportObjectAccess(txn2, "o1", AccessType.WRITE);
	    fail("Expected TransactionConflictException");
	} catch (TransactionConflictException e) {
	    System.err.println(e);
	}
	assertGranted(locker.getResult());
	AccessedObjectsDetail detail =
	    profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail,
			    "s", "o2", AccessType.READ, null,
			    "s", "o1", AccessType.WRITE, null);
	assertEquals(ConflictType.DEADLOCK, detail.getConflictType());
	assertArrayEquals(txn.getId(), detail.getConflictingId());
	txn.commit();
	txn = null;
	detail = profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail,
			    "s", "o1", AccessType.READ, null,
			    "s", "o2", AccessType.WRITE, null);
	assertEquals(ConflictType.NONE, detail.getConflictType());
	assertEquals(null, detail.getConflictingId());
    }

    /* -- Test lock -- */

    @Test
    public void testLockGranted() {
	assertGranted(coordinator.lock(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lock(txn, "s1", "o1", false, null));
    }

    @Test
    public void testLockWhileWaiting() {
	assertGranted(acquireLock(txn, "s1", "o1", true));
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	try {
	    coordinator.lock(txn2, "s1", "o2", false, null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockAfterDeadlock() {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(acquireLock(txn2, "s1", "o1", true), txn);
	try {
	    coordinator.lock(txn2, "s1", "o3", false, null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /**
     * Test what happens if we abandon the attempt to acquire a lock that ends
     * in a timeout, and then try again after the conflicting transaction
     * completes.  We don't currently provide an API for doing this in
     * Darkstar, but it should be possible using the lower level API.
     */
    @Test
    public void testLockAfterTimeout() throws Exception {
	properties.setProperty(
	    LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY, String.valueOf(1));
	init();
	assertGranted(acquireLock(txn, "s1", "o1", true));
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	Thread.sleep(2);
	assertTimeout(locker2.getResult(), txn);
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	assertGranted(acquireLock(txn2, "s1", "o1", true));
    }

    @Test
    public void testLockInterrupt() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn2, "s1", "o1", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o1", true);
	locker.assertBlocked();
	locker.interruptThread();
	assertInterrupted(locker.getResult(), txn2);
	txn2.abort(ABORT_EXCEPTION);
    }

    /* -- Test lockNoWait -- */

    @Test
    public void testLockNoWaitGranted() {
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
    }

    @Test
    public void testLockNoWaitWhileWaiting() {
	assertGranted(acquireLock(txn, "s1", "o1", true));
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	try {
	    coordinator.lockNoWait(txn2, "s1", "o2", false, null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockNoWaitAfterDeadlock() {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(acquireLock(txn2, "s1", "o1", true), txn);
	try {
	    coordinator.lockNoWait(txn2, "s1", "o3", false, null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test timeouts -- */

    @Test
    public void testLockTimeout() throws Exception {
	properties.setProperty(LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
			       String.valueOf(20));
	init();
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn, "s1", "o1", true));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(locker2.getResult(), txn);
    }

    @Test
    public void testMaxLockTimeout() throws Exception {
	properties.setProperty(LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
			       String.valueOf(Long.MAX_VALUE));
	init();
	DummyTransaction txn2 = new DummyTransaction(40);
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn, "s1", "o1", true));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	Thread.sleep(20);
	locker2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(locker2.getResult(), txn);
    }

    @Test
    public void testTxnTimeout() throws Exception {
	DummyTransaction txn2 = new DummyTransaction(20);
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn, "s1", "o1", true));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(locker2.getResult(), txn);
    }

    @Test
    public void testMaxTxnTimeout() throws Exception {
	properties.setProperty(LockingAccessCoordinator.LOCK_TIMEOUT_PROPERTY,
			       String.valueOf(40));
	init();
	DummyTransaction txn2 = new DummyTransaction(Long.MAX_VALUE);
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(acquireLock(txn, "s1", "o1", true));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	Thread.sleep(20);
	locker2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(locker2.getResult(), txn);
    }

    /* -- Test lock conflicts -- */

    /**
     * Test read/write conflict
     *
     * txn2: read o1	=> granted
     * txn:  write o1	=> blocked
     * txn2: commit
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

    /**
     * Test case with two deadlocks.
     *
     * txn is oldest, txn2 in the middle, txn3 is youngest
     *
     * txn:  read o1	=> granted
     * txn2: read o2	=> granted
     * txn3: read o2	=> granted
     * txn2: write o1	=> blocked
     * txn3: write o1	=> blocked
     * txn:  write o2	=> blocked
     * txn3:		=> deadlock
     * txn3: abort
     * txn2:		=> deadlock
     * txn2: abort
     * txn:		=> granted
     */
    @Test
    public void testDoubleDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", false));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	assertGranted(acquireLock(txn3, "s1", "o2", false));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	AcquireLock locker3 = new AcquireLock(txn3, "s1", "o1", true);
	locker3.assertBlocked();
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(locker3.getResult(), txn, txn2);
	txn3.abort(ABORT_EXCEPTION);
	locker.assertBlocked();
	assertDeadlock(locker2.getResult(), txn, txn3);
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /**
     * Test case with three deadlocks
     *
     * txn is oldest, txn2 & txn3 are in the middle, txn4 is youngest
     *
     * txn:  write o1	=> granted
     * txn2: read o2	=> granted
     * txn3: read o2	=> granted
     * txn4: read o2	=> granted
     * txn2: write o1	=> blocked
     * txn3: write o1	=> blocked
     * txn4: write o1	=> blocked
     * txn:  write o2	=> blocked
     * txn2:		=> deadlock
     * txn2: abort
     * txn3:		=> deadlock
     * txn3: abort
     * txn4:		=> deadlock
     * txn4: abort
     * txn:		=> granted
     */
    @Test
    public void testTripleReadWriteDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	DummyTransaction txn4 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn4, 3000, 1);
	assertGranted(acquireLock(txn, "s1", "o1", true));
	assertGranted(acquireLock(txn2, "s1", "o2", false));
	assertGranted(acquireLock(txn3, "s1", "o2", false));
	assertGranted(acquireLock(txn4, "s1", "o2", false));
	AcquireLock locker2 = new AcquireLock(txn2, "s1", "o1", true);
	locker2.assertBlocked();
	AcquireLock locker3 = new AcquireLock(txn3, "s1", "o1", true);
	locker3.assertBlocked();
	AcquireLock locker4 = new AcquireLock(txn4, "s1", "o1", true);
	locker4.assertBlocked();
	AcquireLock locker = new AcquireLock(txn, "s1", "o2", true);
	locker.assertBlocked();
	assertDeadlock(locker2.getResult(), txn, txn3, txn4);
	txn2.abort(ABORT_EXCEPTION);
	assertDeadlock(locker3.getResult(), txn, txn2, txn4);
	txn3.abort(ABORT_EXCEPTION);
	assertDeadlock(locker4.getResult(), txn, txn2, txn3);
	txn4.abort(ABORT_EXCEPTION);
	assertGranted(locker.getResult());
    }

    /* -- Other tests -- */

    /**
     * Tests requesting shared locks simultaneously from multiple threads, to
     * measure performance for what should be the fastest case.
     */
    @Test
    public void testPerformance() throws Exception {
	int repeat = Integer.getInteger("test.repeat", 4);
	int threads = Integer.getInteger("test.threads", 4);
	/* Use 5000 for a good stress test */
	final int count = Integer.getInteger("test.count", 100);
	final int locks = Integer.getInteger("test.locks", 100);
	System.err.println("repeat: " + repeat +
			   "\nthreads: " + threads +
			   "\ncount: " + count +
			   "\nlocks: " + locks);
	for (int r = 0; r < repeat; r++) {
	    final CountDownLatch counter = new CountDownLatch(threads);
	    long start = System.currentTimeMillis();
	    for (int i = 0; i < threads; i++) {
		new Thread() {
		    public void run() {
			for (int c = 0; c < count; c++) {
			    DummyTransaction txn = new DummyTransaction();
			    coordinator.notifyNewTransaction(txn, 0, 1);
			    for (int i = 0; i < locks; i++) {
				reporter.reportObjectAccess(
				    txn, "o" + i, AccessType.READ);
			    }
			    txn.abort(ABORT_EXCEPTION);
			}
			counter.countDown();
		    }
		}.start();
	    }
	    counter.await();
	    long time = System.currentTimeMillis() - start;
	    System.err.println(
		time + " ms" +
		", " + ((double) time / (count * locks)) + " ms/lock");
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
     * Asserts that the request resulted in an interrupt. The conflictingTxns
     * argument specifies all of the other transactions that may have been
     * involved in the conflict.
     */
    static void assertInterrupted(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.INTERRUPTED, conflict, conflictingTxns);
    }


    /**
     * Asserts that the request resulted in a deadlock. The conflictingTxns
     * argument specifies all of the other transactions that may have been
     * involved in the conflict.
     */
    static void assertDeadlock(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.DEADLOCK, conflict, conflictingTxns);
    }

    /**
     * Asserts that the request resulted in a timeout. The conflictingTxns
     * argument specifies all of the other transactions that may have been
     * involved in the conflict.
     */
    static void assertTimeout(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.TIMEOUT, conflict, conflictingTxns);
    }

    /**
     * Asserts that the request was denied, with the specified type of
     * conflict. The conflictingTxns argument specifies all of the other
     * transactions that may have been involved in the conflict.
     */
    static void assertDenied(LockConflictType type,
			     LockConflict conflict,
			     Transaction... conflictingTxns)
    {
	if (conflict == null || conflict.getType() != type) {
	    fail("Expected " + type + ": " + conflict);
	}
	assertMember(conflictingTxns, conflict.getConflictingTransaction());
    }

    /**
     * Asserts that the item is one of the elements of the array, which should
     * not be empty.
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
    class AcquireLock implements Callable<LockConflict> {
	private final DummyTransaction txn;
	private final String source;
	private final Object objectId;
	private final boolean forWrite;
	private final FutureTask<LockConflict> task;
	private final Thread thread;

	/**
	 * Set to true when the initial attempt to acquire the lock is
	 * complete, so we know whether the attempt blocked.
	 */
	private boolean started = false;

	/** Set to true if the initial attempt to acquire the lock blocked. */
	private boolean blocked = false;

	/**
	 * Creates an instance that starts a thread to acquire a lock on behalf
	 * of a transaction.
	 */
	AcquireLock(DummyTransaction txn,
		    String source, Object objectId, boolean forWrite)
	{
	    this.txn = txn;
	    this.source = source;
	    this.objectId = objectId;
	    this.forWrite = forWrite;
	    task = new FutureTask<LockConflict>(this);
	    thread = new Thread(task);
	    thread.start();
	}

	public LockConflict call() {
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
	    return conflict;
	}

	/**
	 * Checks if the initial attempt to obtain the lock blocked and the
	 * result is not yet available.
	 */
	boolean blocked() {
	    synchronized (this) {
		while (!started) {
		    try {
			wait();
		    } catch (InterruptedException e) {
			break;
		    }
		}
		if (!blocked) {
		    return false;
		}
		try {
		    task.get(0, TimeUnit.SECONDS);
		    return false;
		} catch (TimeoutException e) {
		    return true;
		} catch (RuntimeException e) {
		    throw e;
		} catch (Exception e) {
		    throw new RuntimeException(
			"Unexpected exception: " + e, e);
		}
	    }
	}

	/** Interrupts the waiting thread. */
	void interruptThread() {
	    thread.interrupt();
	}

	/**
	 * Asserts that the initial attempt to obtain the lock should have
	 * blocked.
	 */
	void assertBlocked() {
	    assertTrue("The lock attempt did not block", blocked());
	}

	/** Returns the result of attempting to obtain the lock. */
	LockConflict getResult() {
	    try {
		return task.get(1, TimeUnit.SECONDS);
	    } catch (RuntimeException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }
}
