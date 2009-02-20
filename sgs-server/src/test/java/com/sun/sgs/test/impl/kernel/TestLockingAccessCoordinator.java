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
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
    }

    /* -- Test lock conflicts -- */

    @Test
    public void testReadWriteConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o1", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o1", true, null), txn2);
	txn2.commit();
	assertGranted(coordinator.waitForLock(txn));
    }

    @Test
    public void testUpgradeConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o1", true, null), txn2);
	txn2.commit();
	assertGranted(coordinator.waitForLock(txn));
    }

    /* -- Test deadlocks -- */

    @Test
    public void testReadWriteDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o2", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o2", true, null), txn2);
	assertDeadlock(
	    coordinator.lockNoWait(txn2, "s1", "o1", true, null), txn);
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(coordinator.waitForLock(txn));
    }

    @Test
    public void testUpgradeDeadlock() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o1", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o1", true, null), txn2);
	assertDeadlock(
	    coordinator.lockNoWait(txn2, "s1", "o1", true, null), txn);
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(coordinator.waitForLock(txn));
    }

    @Test
    public void testReadWriteLoopDeadlock1() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o2", false, null));
	assertGranted(coordinator.lockNoWait(txn3, "s1", "o3", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o2", true, null), txn2);
	assertBlocked(
	    coordinator.lockNoWait(txn2, "s1", "o3", true, null), txn3);
	assertDeadlock(
	    coordinator.lockNoWait(txn3, "s1", "o1", true, null),
	    txn, txn2);
	assertBlocked(coordinator.checkLock(txn), txn2);
	assertGranted(coordinator.checkLock(txn2));
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(coordinator.checkLock(txn));
    }

    @Test
    public void testReadWriteLoopDeadlock2() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 1000, 1);
	DummyTransaction txn3 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn3, 2000, 1);
	assertGranted(coordinator.lockNoWait(txn, "s1", "o1", false, null));
	assertGranted(coordinator.lockNoWait(txn2, "s1", "o2", false, null));
	assertGranted(coordinator.lockNoWait(txn3, "s1", "o3", false, null));
	assertBlocked(
	    coordinator.lockNoWait(txn2, "s1", "o3", true, null), txn3);
	assertBlocked(
	    coordinator.lockNoWait(txn3, "s1", "o1", true, null), txn);
	assertBlocked(
	    coordinator.lockNoWait(txn, "s1", "o2", true, null), txn2, txn3);
	assertDeadlock(coordinator.checkLock(txn3), txn, txn2);
	assertBlocked(coordinator.checkLock(txn), txn2);
	assertGranted(coordinator.checkLock(txn2));
	txn2.abort(ABORT_EXCEPTION);
	assertGranted(coordinator.checkLock(txn));
    }

    /* -- Other methods and classes -- */

    static class DummyProfileCollectorHandle
	implements ProfileCollectorHandle
    {
	private AccessedObjectsDetail detail;
	DummyProfileCollectorHandle() { }
	public synchronized void setAccessedObjectsDetail(
	    AccessedObjectsDetail detail)
	{
	    this.detail = detail;
	}
	synchronized AccessedObjectsDetail getAccessedObjectsDetail() {
	    AccessedObjectsDetail result = detail;
	    detail = null;
	    return result;
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

    static void assertGranted(LockConflict conflict) {
	if (conflict != null) {
	    fail("Expected no conflict: " + conflict);
	}
    }

    static void assertBlocked(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.BLOCKED, conflict, conflictingTxns);
    }

    static void assertDeadlock(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.DEADLOCK, conflict, conflictingTxns);
    }

    static void assertTimeout(
	LockConflict conflict, Transaction... conflictingTxns)
    {
	assertDenied(LockConflictType.TIMEOUT, conflict, conflictingTxns);
    }

    static void assertDenied(LockConflictType type,
			     LockConflict conflict,
			     Transaction... conflictingTxns)
    {
	if (conflict == null || conflict.getType() != type) {
	    fail("Expected " + type + ": " + conflict);
	}
	assertMember(conflictingTxns, conflict.getConflictingTxn());
    }

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
}
