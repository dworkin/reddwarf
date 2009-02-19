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
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.NameRunner;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link LockingAccessCoordinator} class. */
@RunWith(NameRunner.class)
public class TestLockingAccessCoordinator extends Assert {
    private static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();
    private static final DummyProfileCollectorHandle profileCollector =
	new DummyProfileCollectorHandle();
    private static long lockTimeout;
    private static int numKeyMaps;
    private Properties properties;
    private LockingAccessCoordinator accessCoordinator;
    private DummyTransaction txn;

    @BeforeClass
    public static void beforeClass() {
	lockTimeout = Long.getLong("test.lockTimeout", -1);
	numKeyMaps = Integer.getInteger("test.numKeyMaps", -1);
    }

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
	accessCoordinator = new LockingAccessCoordinator(
	    properties, txnProxy, profileCollector);
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
    }

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
	accessCoordinator.registerAccessSource(null, Object.class);
    }

    @Test(expected=NullPointerException.class)
    public void testRegisterAccessSourceNullObjectIdType() {
	accessCoordinator.registerAccessSource("a", null);
    }

    /* -- Test getConflictingTransaction -- */

    @Test(expected=NullPointerException.class)
    public void testGetConflictingTransactionNullTxn() {
	accessCoordinator.getConflictingTransaction(null);
    }

    /* -- Test notifyNewTransaction -- */

    @Test(expected=NullPointerException.class)
    public void testNotifyNewTransactionNullTxn() {
	accessCoordinator.notifyNewTransaction(null, 0, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotifyNewTransactionIllegalRequestedStartTime() {
	accessCoordinator.notifyNewTransaction(txn, -1, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNotifyNewTransactionIllegalTryCount() {
	accessCoordinator.notifyNewTransaction(txn, 0, 0);
    }

    /* -- Test AccessReporter.reportObjectAccess -- */

    @Test
    public void testReportObjectAccessNullTxn() {
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
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
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
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
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
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

    //@Test
    public void testReportObjectAccessMisc() {
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
	reporter.reportObjectAccess("read1", AccessType.READ);
	reporter.reportObjectAccess("read2", AccessType.READ);
	reporter.reportObjectAccess("upgrade", AccessType.READ);
	reporter.reportObjectAccess("upgrade", AccessType.READ);
	reporter.reportObjectAccess("write1", AccessType.WRITE);
	reporter.reportObjectAccess("read1", AccessType.READ);
	reporter.reportObjectAccess("write1", AccessType.WRITE);
	reporter.reportObjectAccess("upgrade", AccessType.WRITE);
	reporter.reportObjectAccess("upgrade", AccessType.WRITE);
	AccessedObjectsDetail detail = accessCoordinator.releaseAll(txn);
	List<? extends AccessedObject> accesses = detail.getAccessedObjects();
	System.err.println(accesses);
    }

    /* -- Test AccessReporter.setObjectDescription -- */

    @Test
    public void testSetObjectDescriptionNullTxn() {
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
	try {
	    reporter.setObjectDescription(null, "id", "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testSetObjectDescriptionNullObjId() {
	AccessReporter<String> reporter =
	    accessCoordinator.registerAccessSource("a", String.class);
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
	LockConflict result =
	    accessCoordinator.lockNoWait(txn, "s1", "o1", false, null);
	assertNull(result);
    }

    @Test
    public void testLockNoWaitReadWriteConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	accessCoordinator.notifyNewTransaction(txn2, 0, 1);
	LockConflict result =	
	    accessCoordinator.lockNoWait(txn2, "s1", "o1", false, null);
	assertNull(result);
	result = accessCoordinator.lockNoWait(txn, "s1", "o1", true, null);
	assertNotNull(result);
	assertEquals(LockConflictType.BLOCKED, result.type);
	assertSame(txn2, result.conflictingTxn);
	txn2.commit();
	result = accessCoordinator.waitForLock(txn);
	assertNull(result);
    }

    @Test
    public void testLockNoWaitUpgradeConflict() throws Exception {
	DummyTransaction txn2 = new DummyTransaction();
	accessCoordinator.notifyNewTransaction(txn2, 0, 1);
	LockConflict result =
	    accessCoordinator.lockNoWait(txn2, "s1", "o1", false, null);
	assertNull(result);
	result = accessCoordinator.lockNoWait(txn, "s1", "o1", false, null);
	assertNull(result);
	result = accessCoordinator.lockNoWait(txn, "s1", "o1", true, null);
	assertNotNull(result);
	assertEquals(LockConflictType.BLOCKED, result.type);
	assertSame(txn2, result.conflictingTxn);
	txn2.commit();
	result = accessCoordinator.waitForLock(txn);
	assertNull(result);
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
}
