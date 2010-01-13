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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** A superclass for tests of {@link AccessCoordinator} implementations. */
public abstract class BasicAccessCoordinatorTest
    <T extends AccessCoordinatorHandle>
    extends Assert
{
    /** The transaction proxy, for creating transactions. */
    protected static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** A profile collector, for reporting accesses. */
    protected static final DummyProfileCollectorHandle profileCollector =
	new DummyProfileCollectorHandle();

    /** An exception to use for aborting transactions. */
    protected static final Exception ABORT_EXCEPTION = new Exception();

    /** The configuration properties. */
    protected Properties properties;

    /** The access coordinator to test. */
    protected T coordinator;

    /** An active transaction. */
    protected DummyTransaction txn;

    /** An access reporter obtained from the coordinator. */
    protected AccessReporter<String> reporter;

    /** Creates an instance of this class. */
    protected BasicAccessCoordinatorTest() { }

    /** Create properties and initialize fields for test methods. */
    @Before
    public void before() throws Exception {
	properties = new Properties();
	init();
    }

    /** Initialize fields using the current value of properties. */
    protected void init() {
	coordinator = createAccessCoordinator();
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

    /** Creates an instance of the access coordinator to test. */
    protected abstract T createAccessCoordinator();

    /* -- Tests -- */

    /* -- Test registerAccessSource -- */

    @Test(expected=NullPointerException.class)
    public void testRegisterAccessSourceNullSourceName() {
	coordinator.registerAccessSource(null, Object.class);
    }

    @Test(expected=NullPointerException.class)
    public void testRegisterAccessSourceNullObjectIdType() {
	coordinator.registerAccessSource("a", null);
    }

    @Test
    public void testRegisterAccessSourceSuccess() {
	AccessReporter<Integer> reporter =
	    coordinator.registerAccessSource("test", Integer.class);
	reporter.reportObjectAccess(1, AccessType.READ);
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "test", 1, AccessType.READ, null);
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

    @Test(expected=IllegalStateException.class)
    public void testNotifyNewTransactionAgain() {
	coordinator.notifyNewTransaction(txn, 0, 1);
    }

    @Test
    public void testNotifyNewTransactionAborted() {
	txn.abort(ABORT_EXCEPTION);
	try {
	    coordinator.notifyNewTransaction(txn, 0, 1);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	txn = null;
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
    public void testReportObjectAccessTransactionNotActive() {
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	try {
	    reporter.reportObjectAccess("o1", AccessType.READ);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	try {
	    reporter.reportObjectAccess("o1", AccessType.READ, null);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testReportObjectAccessTransactionNotKnown() {
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    reporter.reportObjectAccess(txn2, "o1", AccessType.READ);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    reporter.reportObjectAccess(txn2, "o1", AccessType.READ, null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	txn2.abort(ABORT_EXCEPTION);
    }

    @Test
    public void testReportObjectAccessWithTxnAndNonNullDesc()
	throws Exception
    {
	reporter.reportObjectAccess(txn, "o1", AccessType.READ, "desc");
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "desc");
    }

    @Test
    public void testReportObjectAccessDescription() throws Exception {
	reporter.reportObjectAccess("o1", AccessType.READ, "object 1 (a)");
	reporter.reportObjectAccess("o1", AccessType.WRITE, "object 1 (b)");
	reporter.reportObjectAccess("o1", AccessType.WRITE, null);
	reporter.reportObjectAccess("o1", AccessType.WRITE);
	reporter.reportObjectAccess("o2", AccessType.WRITE, null);
	reporter.reportObjectAccess("o2", AccessType.WRITE, "object 2");
	reporter.reportObjectAccess("o3", AccessType.READ);
	reporter.reportObjectAccess("o4", AccessType.READ, null);
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "object 1 (a)",
			    "s", "o1", AccessType.WRITE, "object 1 (a)",
			    "s", "o2", AccessType.WRITE, "object 2",
			    "s", "o3", AccessType.READ, null,
			    "s", "o4", AccessType.READ, null);
    }

    @Test
    public void testReportObjectAccessMultipleReporters() {
	DummyTransaction txn2 = new DummyTransaction();
	coordinator.notifyNewTransaction(txn2, 0, 1);
	AccessReporter<String> reporterPrime =
	    coordinator.registerAccessSource("s", String.class);
	AccessReporter<String> reporter2 =
	    coordinator.registerAccessSource("s2", String.class);
	AccessReporter<String> reporter2Prime =
	    coordinator.registerAccessSource("s2", String.class);
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporterPrime.reportObjectAccess("o1", AccessType.READ);
	reporter.reportObjectAccess("o1", AccessType.WRITE);
	reporterPrime.reportObjectAccess("o1", AccessType.WRITE);
	reporter2.reportObjectAccess(txn2, "o1", AccessType.READ);
	reporter2Prime.reportObjectAccess(txn2, "o1", AccessType.READ);
	reporter2.reportObjectAccess(txn2, "o1", AccessType.WRITE);
	reporter2Prime.reportObjectAccess(txn2, "o1", AccessType.WRITE);
    }

    @Test
    public void testReportObjectAccessMisc() throws Exception {
	AccessReporter<String> reporter2 =
	    coordinator.registerAccessSource("s2", String.class);
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
	assertObjectDetails(
	    profileCollector.getAccessedObjectsDetail(),
	    "s", "read1", AccessType.READ, null,
	    "s2", "read1", AccessType.READ, null,
	    "s", "upgrade", AccessType.READ, null,
	    "s", "write1", AccessType.WRITE, null,
	    "s", "upgrade", AccessType.WRITE, null);
    }

    /* -- Test AccessReporter.setObjectDescription -- */

    @Test
    public void testSetObjectDescriptionNullTxn() {
	try {
	    reporter.setObjectDescription(null, "id", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.setObjectDescription(null, "id", "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testSetObjectDescriptionNullObjId() {
	try {
	    reporter.setObjectDescription(null, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.setObjectDescription(null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.setObjectDescription(txn, null, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	try {
	    reporter.setObjectDescription(txn, null, "desc");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testSetObjectDescriptionTransactionNotActive() {
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	try {
	    reporter.setObjectDescription("o1", null);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	try {
	    reporter.setObjectDescription("o1", "description");
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testSetObjectDescriptionTransactionNotKnown() {
	DummyTransaction txn2 = new DummyTransaction();
	try {
	    reporter.setObjectDescription(txn2, "o1", null);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    reporter.setObjectDescription(txn2, "o1", "description");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	txn2.abort(ABORT_EXCEPTION);
    }

    @Test
    public void testSetObjectDescriptionNullAfterReport() throws Exception {
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporter.setObjectDescription("o1", null);
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, null);
    }

    @Test
    public void testSetObjectDescriptionNonNullAfterReport() throws Exception {
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporter.setObjectDescription("o1", "desc");
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "desc");
    }

    @Test
    public void testSetObjectDescriptionMultipleDescriptions() {
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporter.setObjectDescription("o1", "desc1");
	reporter.setObjectDescription("o1", "desc2");
	txn.abort(ABORT_EXCEPTION);
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "desc1");
    }

    @Test
    public void testSetObjectDescriptionMultipleAccesses() throws Exception {
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporter.setObjectDescription("o1", "desc1");
	reporter.reportObjectAccess("o2", AccessType.READ);
	reporter.setObjectDescription("o2", "desc2");
	reporter.reportObjectAccess("o3", AccessType.READ);
	reporter.setObjectDescription("o3", "desc3");
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "desc1",
			    "s", "o2", AccessType.READ, "desc2",
			    "s", "o3", AccessType.READ, "desc3");
    }

    @Test
    public void testSetObjectDescriptionBeforeAccess() throws Exception {
	reporter.setObjectDescription("o1", "desc1");
	reporter.reportObjectAccess("o1", AccessType.READ);
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "desc1");
    }

    @Test
    public void testSetObjectDescriptionNoAccess() throws Exception {
	reporter.setObjectDescription("o1", "desc1");
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail());
    }

    @Test
    public void testSetObjectDescriptionMisc() throws Exception {
	reporter.setObjectDescription("o1", "object 1 (a)");
	reporter.setObjectDescription("o1", "object 1 (b)");
	reporter.reportObjectAccess("o1", AccessType.READ, "object 1 (c)");
	reporter.setObjectDescription("o2", "object 2 (a)");
	reporter.reportObjectAccess("o2", AccessType.READ, "object 2 (b)");
	reporter.setObjectDescription("o2", "object 2 (c)");
	reporter.reportObjectAccess("o3", AccessType.READ);
	reporter.setObjectDescription("o3", "object 3 (a)");
	reporter.setObjectDescription("o3", "object 3 (b)");
	txn.commit();
	txn = null;
	assertObjectDetails(profileCollector.getAccessedObjectsDetail(),
			    "s", "o1", AccessType.READ, "object 1 (a)",
			    "s", "o2", AccessType.READ, "object 2 (a)",
			    "s", "o3", AccessType.READ, "object 3 (a)");
    }

    /* -- Test AccessedObjectsDetail -- */

    @Test
    public void testAccessedObjectsDetailNone() throws Exception {
	txn.commit();
	txn = null;
	AccessedObjectsDetail detail =
	    profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail);
	assertEquals(ConflictType.NONE, detail.getConflictType());
	assertEquals(null, detail.getConflictingId());
    }

    @Test
    public void testAccessedObjectsDetailNoConflict() throws Exception {
	reporter.reportObjectAccess("o1", AccessType.READ);
	reporter.reportObjectAccess("o2", AccessType.WRITE);
	txn.commit();
	txn = null;
	AccessedObjectsDetail detail =
	    profileCollector.getAccessedObjectsDetail();
	assertObjectDetails(detail,
			    "s", "o1", AccessType.READ, null,
			    "s", "o2", AccessType.WRITE, null);
	assertEquals(ConflictType.NONE, detail.getConflictType());
	assertEquals(null, detail.getConflictingId());
    }

    /* -- Other methods and classes -- */

    /**
     * Checks that the proper object accesses were reported.  The expected
     * argument should provide groups of 4 items: the source, the object ID,
     * the access type, and the description.
     */
    protected static void assertObjectDetails(
	AccessedObjectsDetail detail, Object... expected)
    {
	assertTrue("The expected argument must provide groups of four",
		   expected.length % 4 == 0);
	int numExpected = expected.length / 4;
	List<AccessedObject> accesses = detail.getAccessedObjects();
	assertTrue("Expected " + numExpected + " accesses, found " +
		   accesses.size(),
		   numExpected == accesses.size());
	for (int i = 0; i < expected.length; i += 4) {
	    AccessedObject result = accesses.get(i / 4);
	    assertEquals(expected[i], result.getSource());
	    assertEquals(expected[i + 1], result.getObjectId());
	    assertEquals(expected[i + 2], result.getAccessType());
	    assertEquals(expected[i + 3], result.getDescription());
	}
    }
}
