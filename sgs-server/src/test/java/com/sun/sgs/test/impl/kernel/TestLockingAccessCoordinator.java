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
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
	init(lockTimeout, numKeyMaps);
    }

    /**
     * Initialize fields with the specified lock timeout and number of key
     * maps.
     */
    protected void init(long lockTimeout, int numKeyMaps) {
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
	init(100, -1);
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	reporter.reportObjectAccess(txn, "o1", AccessType.READ);
	reporter.reportObjectAccess(txn2, "o2", AccessType.READ);
	FutureTask<Exception> attempt =
	    new FutureTask<Exception>(
		new Callable<Exception>() {
		    public Exception call() throws Exception {
			try {
			    reporter.reportObjectAccess(
				txn, "o2", AccessType.WRITE);
			    return null;
			} catch (Exception e) {
			    e.printStackTrace();
			    return e;
			}
		    }
		});
	new Thread(attempt).start();
	try {
	    attempt.get(20, TimeUnit.MILLISECONDS);
	    fail("Expected timeout");
	} catch (TimeoutException e) {
	}
	try {
	    reporter.reportObjectAccess(txn2, "o1", AccessType.WRITE);
	    fail("Expected TransactionConflictException");
	} catch (TransactionConflictException e) {
	    System.err.println(e);
	}
	assertEquals(null, attempt.get());
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
}
