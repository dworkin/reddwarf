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

package com.sun.sgs.test.impl.service.transaction;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.profile.ProfileCollectorHandleImpl;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.test.util.DummyNonDurableTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionParticipant.State;
import com.sun.sgs.test.util.NameRunner;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(NameRunner.class)
/** Test TransactionCoordinatorImpl */
@SuppressWarnings("hiding")
public class TestTransactionCoordinatorImpl {

    /** The default transaction timeout. */
    private static final long TIMEOUT =
	Long.getLong(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
		     TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT);

    /** Long enough for a transaction to timeout. */
    private static final long TIMED_OUT = TIMEOUT + 5;

    /** Transaction coordinator properties. */
    private static final Properties coordinatorProps = new Properties();
    static {
	coordinatorProps.setProperty(
	    TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
	    String.valueOf(TIMEOUT));
    }

    /** A profile collector handle. */
    private static ProfileCollectorHandle collectorHandle;
    /** The collector backing the handle. */
    private static ProfileCollectorImpl collector;
    
    /** The instance to test. */
    private final TransactionCoordinator coordinator =
	new TransactionCoordinatorImpl(coordinatorProps, collectorHandle);
    
    /** The handle to test. */
    private TransactionHandle handle;

    /** The transaction associated with the handle. */
    private Transaction txn;

    /** A common exception to throw when aborting. */
    private final RuntimeException abortXcp = new RuntimeException("abort");

    @BeforeClass
    public static void first() throws Exception {
        collector = new ProfileCollectorImpl(ProfileLevel.MIN, null, null);
        collectorHandle =new ProfileCollectorHandleImpl(collector);
    }
    
    @AfterClass
    public static void last() throws Exception {     
        collector.shutdown();
    }
    
    /** Prints the test case, sets handle and txn */
    @Before
    public void setUp() {
	handle = coordinator.createTransaction(false);
	txn = handle.getTransaction();
    }

    /* -- Test TransactionCoordinatorImpl constructor -- */

    @Test
    public void testConstructorNullProperties() {
	try {
	    new TransactionCoordinatorImpl(null, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    @Test
    public void testConstructorNullCollector() {
	try {
	    new TransactionCoordinatorImpl(coordinatorProps, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorIllegalPropertyValues() {
	Properties[] allProperties = {
	    createProperties(
		TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "foo"),
	    createProperties(
		TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "0"),
	    createProperties(
		TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "-33"),
	    createProperties(
		TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY, "foo"),
	    createProperties(
		TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY, "0"),
	    createProperties(
		TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY, "-200")
	};
	for (Properties props : allProperties) {
	    try {
		new TransactionCoordinatorImpl(props, collectorHandle);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(props + ": " + e);
	    }
	}
    }

    /* -- Test TransactionHandle.commit -- */

    @Test
    public void testCommitActive() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.COMMITTED, participant.getState());
	    }
	}
	assertCommitted();
    }

    @Test
    public void testCommitActiveEmpty() throws Exception {
	handle.commit();
	assertCommitted();
    }

    @Test
    public void testCommitAborting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    try {
			handle.commit();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			throw e;
		    } catch (Exception e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort(abortXcp);
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitAborted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	txn.abort(abortXcp);
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitPreparing() throws Exception {
	final Exception[] abortCause = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) {
		    try {
			handle.commit();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			abortCause[0] = e;
			throw e;
		    } catch (Exception e) {
			fail("Unexpected exception: " + e);
		    }
		    throw new AssertionError();
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause[0]);
    }

    @Test
    public void testCommitPrepareAndCommitting() throws Exception {
	final Exception[] abortCause = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    try {
			handle.commit();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			abortCause[0] = e;
			throw e;
		    } catch (Exception e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause[0]);
    }

    @Test
    public void testCommitCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    try {
			handle.commit();
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			throw e;
		    } catch (Exception e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.PREPARED : State.COMMITTED,
			     participant.getState());
	    }
	}
	assertCommitted();
    }

    @Test
    public void testCommitCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	try {
	    handle.commit();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	assertCommitted();
    }

    @Test
    public void testCommitPrepareFailsMiddle() throws Exception {
	final Exception abortCause = new IOException("Prepare failed"); 
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    throw abortCause;
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IOException");
	} catch (IOException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause);
    }

    @Test
    public void testCommitPrepareFailsLast() throws Exception {
	final Exception abortCause = new IOException("Prepare failed");
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    throw abortCause;
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IOException");
	} catch (IOException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause);
    }

    @Test
    public void testCommitPrepareAbortsMiddle() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) {
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    return false;
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitPrepareAbortsLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitPrepareAbortsAndFailsMiddle() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    throw new IOException("Prepare failed");
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IOException");
	} catch (IOException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitPrepareAbortsAndFailsLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    throw new IOException("Prepare failed");
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IOException");
	} catch (IOException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testCommitFails() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    throw new RuntimeException("Commit failed");
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(
		    (participant == participants[2]
		     ? State.PREPARED : State.COMMITTED),
		    participant.getState());
	    }
	}
	assertCommitted();
    }

    @Test
    public void testCommitAbortedWithRetryableCause() throws Exception {
	Exception abortCause = new TransactionAbortedException("Aborted");
	txn.abort(abortCause);
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertTrue(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    @Test
    public void testCommitAbortedWithNonRetryableCause() throws Exception {
	Exception abortCause = new IllegalArgumentException();
	txn.abort(abortCause);
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertFalse(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    @Test
    public void testCommitAbortedWithNoCause() throws Exception {
	txn.abort(abortXcp);
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertFalse(retryable(e));
	}
    }

    /* -- Test TransactionHandle.getTransaction -- */

    @Test
    public void testGetTransactionActive() {
	handle.getTransaction();
    }

    @Test
    public void testGetTransactionPreparing() {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    handle.getTransaction();
		    return super.prepare(txn);
		}
	    };
	txn.join(participant);
    }

    @Test
    public void testGetTransactionAborting() {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void abort(Transaction txn) {
		    handle.getTransaction();
		    super.abort(txn);
		}
	    };
	txn.join(participant);
	txn.abort(abortXcp);
    }

    @Test
    public void testGetTransactionAborted() {
	txn.join(new DummyTransactionParticipant());
	txn.abort(abortXcp);
	handle.getTransaction();
    }

    @Test
    public void testGetTransactionCommitting() throws Exception {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void commit(Transaction txn) {
		    handle.getTransaction();
		    super.commit(txn);
		}
	    };
	txn.join(participant);
	handle.commit();
    }

    @Test
    public void testGetTransactionCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	handle.getTransaction();
    }

    /* -- Test Transaction.getId -- */

    @Test
    public void testGetId() {
	txn.abort(abortXcp);
	Transaction txn2 = coordinator.createTransaction(false).
	    getTransaction();
	assertNotNull(txn.getId());
	assertNotNull(txn2.getId());
	assertFalse(Arrays.equals(txn.getId(), txn2.getId()));
    }

    /* -- Test Transaction.getCreationTime -- */

    @Test
    public void testGetCreationTime() throws Exception {
	long now = System.currentTimeMillis();
	Thread.sleep(50);
	Transaction txn1 = coordinator.createTransaction(false).
	    getTransaction();
	Thread.sleep(50);
	Transaction txn2 = coordinator.createTransaction(false).
	    getTransaction();
	assertTrue("Transaction creation time is too early: " +
            txn1.getCreationTime(),
            now < txn1.getCreationTime());
	assertTrue("Transaction creation times out-of-order: " +
            txn1.getCreationTime() + " vs " + txn2.getCreationTime(),
            txn1.getCreationTime() < txn2.getCreationTime());
    }

    /*  -- Test Transaction.getTimeout -- */

    @Test
    public void testGetTimeout() {
	Properties p = new Properties();
	p.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "5000");
	p.setProperty(TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY,
		      "100000");
	TransactionCoordinator coordinator =
	    new TransactionCoordinatorImpl(p, collectorHandle);
	Transaction txn = coordinator.createTransaction(false).
	    getTransaction();
	assertTrue("Incorrect bounded Transaction timeout: " +
		   txn.getTimeout(),
		   txn.getTimeout() == 5000);
	txn = coordinator.createTransaction(true).getTransaction();
	assertTrue("Incorrect unbounded Transaction timeout: " +
		   txn.getTimeout(),
		   txn.getTimeout() == 100000);
    }

    /* -- Test Transaction.join -- */

    @Test
    public void testJoinNull() {
	try {
	    txn.join(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testJoinMultipleDurable() {
	txn.join(new DummyTransactionParticipant());
	try {
	    txn.join(new DummyTransactionParticipant());
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testJoinAborting() {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    try {
			txn.join(this);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			throw e;
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort(abortXcp);
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testJoinPreparing() throws Exception {
	final Exception[] abortCause = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) {
		    try {
			txn.join(this);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			abortCause[0] = e;
			throw e;
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    return false;
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause[0]);
    }

    @Test
    public void testJoinPrepareAndCommitting() throws Exception {
	final Exception[] abortCause = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    try {
			txn.join(this);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			abortCause[0] = e;
			throw e;
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause[0]);
    }

    @Test
    public void testJoinCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    try {
			txn.join(this);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			throw e;
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.PREPARED : State.COMMITTED,
			     participant.getState());
	    }
	}
	assertCommitted();
    }

    @Test
    public void testJoinCommitted() throws Exception {
	handle.commit();
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant();
	try {
	    txn.join(participant);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	assertCommitted();
    }

    @Test
    public void testJoinAbortedWithRetryableCause() throws Exception {
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant();
	Exception abortCause = new TransactionAbortedException("Aborted");
	txn.abort(abortCause);
	try {
	    txn.join(participant);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertTrue(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    @Test
    public void testJoinAbortedWithNonRetryableCause() throws Exception {
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant();
	Exception abortCause = new IllegalArgumentException();
	txn.abort(abortCause);
	try {
	    txn.join(participant);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertFalse(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    /* -- Test Transaction.abort -- */

    @Test
    public void testAbortActive() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	assertNotAborted();
	txn.abort(abortXcp);
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testAbortSupplyCause() throws Exception {
	Exception abortCause = new Exception("The cause");
	txn.abort(abortCause);
	assertAborted(abortCause);
	try {
	    txn.abort(new IllegalArgumentException());
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
	try {
	    txn.abort(abortXcp);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
	handle.getTransaction();
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    @Test
    public void testAbortNoCause() throws Exception {
	try {
	    txn.abort(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAbortActiveEmpty() throws Exception {
	txn.abort(abortXcp);
	assertAborted(abortXcp);
    }

    @Test
    public void testAbortAborting() {
	final Exception abortCause = new Exception("Why we aborted");
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    assertAborted(abortCause);
		    super.abort(txn);
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
		public void abort(Transaction txn) {
		    assertAborted(abortCause);
		    super.abort(txn);
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    assertAborted(abortCause);
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    assertAborted(abortCause);
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
		public void abort(Transaction txn) {
		    assertAborted(abortCause);
		    super.abort(txn);
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort(abortCause);
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertAborted(abortCause);
    }

    @Test
    public void testAbortAborted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	Exception cause = new Exception("Abort cause");
	txn.abort(cause);
	try {
	    txn.abort(new Exception("Another"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	assertAborted(cause);
    }

    @Test
    public void testAbortPreparing() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		    return false;
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant()
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testAbortPreparingLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testAbortPreparingLastNonDurable() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortXcp);
    }

    @Test
    public void testAbortPrepareAndCommitting() throws Exception {
	final Exception abortCause = new IllegalArgumentException();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortCause);
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	    assertEquals(abortCause, e.getCause());
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertAborted(abortCause);
    }

    @Test
    public void testAbortCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    assertNotAborted();
		    try {
			txn.abort(abortXcp);
			fail("Expected IllegalStateException");
		    } catch (IllegalStateException e) {
			System.err.println(e);
			throw e;
		    } catch (RuntimeException e) {
			fail("Unexpected exception: " + e);
		    }
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.PREPARED : State.COMMITTED,
			     participant.getState());
	    }
	}
	assertCommitted();
    }

    @Test
    public void testAbortCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	assertCommitted();
	try {
	    txn.abort(abortXcp);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	assertCommitted();
    }

    @Test
    public void testAbortFails() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    throw new RuntimeException("Abort failed");
		}
	    },
	    new DummyTransactionParticipant() {
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	Exception abortCause = new IllegalArgumentException();
	txn.abort(abortCause);
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(
		    (participant == participants[1]
		     ? State.ACTIVE : State.ABORTED),
		    participant.getState());
	    }
	}
	assertAborted(abortCause);
    }

    @Test
    public void testAbortAbortedWithRetryableCause() throws Exception {
	Exception abortCause = new TransactionAbortedException("Aborted");
	txn.abort(abortCause);
	try {
	    txn.abort(new IllegalArgumentException());
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertTrue(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    @Test
    public void testAbortAbortedWithNonRetryableCause() throws Exception {
	Exception abortCause = new IllegalArgumentException();
	txn.abort(abortCause);
	try {
	    txn.abort(new TransactionAbortedException("Aborted"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertFalse(retryable(e));
	    assertEquals(abortCause, e.getCause());
	}
	assertAborted(abortCause);
    }

    /* -- Test checkTimeout -- */

    @Test
    public void testCheckTimeoutActive() throws Exception {
	txn.checkTimeout();
	Thread.sleep(TIMED_OUT);
	try {
	    txn.checkTimeout();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	    assertTrue(txn.isAborted());
	}
    }

    @Test
    public void testCheckTimeoutAborting() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void abort(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	txn.abort(abortXcp);
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutAbortingTimedOut() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void abort(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	Thread.sleep(TIMED_OUT);
	txn.abort(abortXcp);
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutAborted() throws Exception {
	txn.abort(abortXcp);
	try {
	    txn.checkTimeout();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	handle = coordinator.createTransaction(false);
	txn = handle.getTransaction();
	Thread.sleep(TIMED_OUT);
	txn.abort(abortXcp);
	try {
	    txn.checkTimeout();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testCheckTimeoutPreparing() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    try {
			txn.checkTimeout();
			return super.prepare(txn);
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    },
	    new DummyTransactionParticipant()
	};
	for (DummyTransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutPreparingTimedOut() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    try {
			txn.checkTimeout();
			return super.prepare(txn);
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    },
	    new DummyTransactionParticipant()
	};
	for (DummyTransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	Thread.sleep(TIMED_OUT);
	try {
	    handle.commit();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	    assertEquals(e, checkTimeoutException[0]);
	}
    }

    @Test
    public void testCheckTimeoutPrepareAndCommitting() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	handle.commit();
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutPrepareAndCommittingTimedOut()
	throws Exception
    {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	Thread.sleep(TIMED_OUT);
	try {
	    handle.commit();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	    assertEquals(e, checkTimeoutException[0]);
	}
    }

    @Test
    public void testCheckTimeoutCommitting() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void commit(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	handle.commit();
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutCommittingTimedOut() throws Exception {
	final Exception[] checkTimeoutException = { null };
	DummyTransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void commit(Transaction txn) {
		    try {
			txn.checkTimeout();
		    } catch (RuntimeException e) {
			checkTimeoutException[0] = e;
			throw e;
		    }
		}
	    };
	txn.join(participant);
	Thread.sleep(TIMED_OUT);
	handle.commit();
	assertNull(checkTimeoutException[0]);
    }

    @Test
    public void testCheckTimeoutCommitted() throws Exception {
	handle.commit();
	try {
	    txn.checkTimeout();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	handle = coordinator.createTransaction(false);
	txn = handle.getTransaction();
	Thread.sleep(TIMED_OUT);
	handle.commit();
	try {
	    txn.checkTimeout();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    /* -- Test equals -- */

    @Test
    public void testEquals() throws Exception {
	Transaction txn2 = coordinator.createTransaction(false).
	    getTransaction();
	assertFalse(txn.equals(null));
	assertTrue(txn.equals(txn));
	assertFalse(txn.equals(txn2));
    }

    /* -- Other methods -- */

    void assertAborted(Throwable abortCause) {
	assertTrue(txn.isAborted());
	assertEquals(abortCause, txn.getAbortCause());
    }

    void assertNotAborted() {
	assertFalse(txn.isAborted());
	assertEquals(null, txn.getAbortCause());
    }

    void assertCommitted() {
	assertFalse(txn.isAborted());
	assertEquals(null, txn.getAbortCause());
	try {
	    handle.getTransaction().join(new DummyTransactionParticipant());
	    fail("Transaction was active");
	} catch (IllegalStateException e) {
	} catch (RuntimeException e) {
	    fail("Unexpected exception: " + e);
	}
    }

    /** Checks if the argument is a retryable exception. */
    private static boolean retryable(Throwable t) {
	return t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }
}
