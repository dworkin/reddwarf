package com.sun.sgs.test.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.test.DummyNonDurableTransactionParticipant;
import com.sun.sgs.test.DummyTransactionParticipant;
import com.sun.sgs.test.DummyTransactionParticipant.State;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import junit.framework.TestCase;

/** Test TransactionCoordinatorImpl */
public class TestTransactionCoordinatorImpl extends TestCase {

    /** The instance to test. */
    private final TransactionCoordinator coordinator =
	new TransactionCoordinatorImpl(new Properties());
    
    /** The handle to test. */
    private TransactionHandle handle;

    /** The transaction associated with the handle. */
    private Transaction txn;

    /** Creates the test. */
    public TestTransactionCoordinatorImpl(String name) {
	super(name);
    }

    /** Prints the test case, sets handle and txn */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	handle = coordinator.createTransaction();
	txn = handle.getTransaction();
    }

    /* -- Test constructor -- */

    public void testConstructorNullProperties() {
	try {
	    new TransactionCoordinatorImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    /* -- Test TransactionHandle.commit -- */

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
	assertHandleNotActive(handle);
    }

    public void testCommitActiveEmpty() throws Exception {
	handle.commit();
	assertHandleNotActive(handle);
    }

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
		    } catch (RuntimeException e) {
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
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testCommitAborted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCommitPreparing() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    handle.commit();
		    return super.prepare(txn);
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
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareAndCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    handle.commit();
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

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
		    } catch (RuntimeException e) {
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
	assertHandleNotActive(handle);
    }

    public void testCommitCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCommitPrepareFailsMiddle() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
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
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareFailsLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
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
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareAbortsMiddle() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareAbortsLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareAbortsAndFailsMiddle() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

    public void testCommitPrepareAbortsAndFailsLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

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
	assertHandleNotActive(handle);
    }

    /* -- Test TransactionHandle.getTransaction -- */

    public void testGetTransactionActive() {
	handle.getTransaction();
    }

    public void testGetTransactionPreparing() {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    assertHandleNotActive(handle);
		    return super.prepare(txn);
		}
	    };
	txn.join(participant);
    }

    public void testGetTransactionAborting() {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void abort(Transaction txn) {
		    assertHandleNotActive(handle);
		    super.abort(txn);
		}
	    };
	txn.join(participant);
	txn.abort();
    }

    public void testGetTransactionAborted() {
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	assertHandleNotActive(handle);
    }

    public void testGetTransactionCommitting() throws Exception {
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void commit(Transaction txn) {
		    assertHandleNotActive(handle);
		    super.commit(txn);
		}
	    };
	txn.join(participant);
	handle.commit();
    }

    public void testGetTransactionCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	assertHandleNotActive(handle);
    }

    /* -- Test Transaction.getId -- */

    public void testGetId() {
	Transaction txn2 = coordinator.createTransaction().getTransaction();
	assertNotNull(txn.getId());
	assertNotNull(txn2.getId());
	assertFalse(Arrays.equals(txn.getId(), txn2.getId()));
    }

    /* -- Test Transaction.getCreationTime -- */

    public void testGetCreationTime() throws Exception {
	long now = System.currentTimeMillis();
	Thread.sleep(5);
	Transaction txn1 = coordinator.createTransaction().getTransaction();
	Thread.sleep(5);
	Transaction txn2 = coordinator.createTransaction().getTransaction();
	assertTrue(now < txn1.getCreationTime());
	assertNotNull(txn1.getCreationTime() < txn2.getCreationTime());
    }

    /* -- Test Transaction.join -- */

    public void testJoinNull() {
	try {
	    txn.join(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testJoinMultipleDurable() {
	txn.join(new DummyTransactionParticipant());
	try {
	    txn.join(new DummyTransactionParticipant());
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    public void testJoinAborting() {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    txn.join(this);
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testJoinPreparing() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) {
		    txn.join(this);
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
	assertHandleNotActive(handle);
    }

    public void testJoinPrepareAndCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) {
		    txn.join(this);
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
	assertHandleNotActive(handle);
    }

    public void testJoinCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    txn.join(this);
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
	assertHandleNotActive(handle);
    }

    /* -- Test Transaction.abort -- */

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
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testAbortActiveEmpty() throws Exception {
	txn.abort();
	assertHandleNotActive(handle);
    }

    public void testAbortAborting() {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    txn.abort();
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(participant == participants[2]
			     ? State.ACTIVE : State.ABORTED,
			     participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testAbortAborted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	try {
	    txn.abort();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAbortPreparing() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    txn.abort();
		    return super.prepare(txn);
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
	    fail("Expected Exception");
	} catch (Exception e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testAbortPreparingLast() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    txn.abort();
		}
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected Exception");
	} catch (Exception e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testAbortPreparingLastNonDurable() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    txn.abort();
		}
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    txn.abort();
		}
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    txn.abort();
		}
		protected boolean prepareResult() { return true; }
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	try {
	    handle.commit();
	    fail("Expected Exception");
	} catch (Exception e) {
	    System.err.println(e);
	}
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(State.ABORTED, participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    public void testAbortPrepareAndCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn)
		    throws Exception
		{
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

    public void testAbortCommitting() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		protected boolean prepareResult() { return true; }
	    },
	    new DummyNonDurableTransactionParticipant() {
		public void commit(Transaction txn) {
		    txn.abort();
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
	assertHandleNotActive(handle);
    }

    public void testAbortCommitted() throws Exception {
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	try {
	    txn.abort();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAbortFails() throws Exception {
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public void abort(Transaction txn) {
		    throw new RuntimeException("Commit failed");
		}
	    },
	    new DummyTransactionParticipant() {
	    }
	};
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    if (!participant.prepareReturnedTrue()) {
		assertEquals(
		    (participant == participants[1]
		     ? State.ACTIVE : State.ABORTED),
		    participant.getState());
	    }
	}
	assertHandleNotActive(handle);
    }

    /* -- Test equals -- */

    public void testEquals() throws Exception {
	Transaction txn2 = coordinator.createTransaction().getTransaction();
	assertFalse(txn.equals(null));
	assertTrue(txn.equals(txn));
	assertFalse(txn.equals(txn2));
    }

    /* -- Other methods -- */

    static void assertHandleNotActive(TransactionHandle handle) {
	try {
	    handle.getTransaction();
	    fail("Transaction was active");
	} catch (TransactionNotActiveException e) {
	}
    }
}
