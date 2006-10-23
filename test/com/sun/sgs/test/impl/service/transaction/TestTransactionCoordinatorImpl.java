package com.sun.sgs.test.impl.service.transaction;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.test.DummyNonDurableTransactionParticipant;
import com.sun.sgs.test.DummyTransactionParticipant;
import java.io.IOException;
import java.util.Arrays;
import junit.framework.TestCase;

public class TestTransactionCoordinatorImpl extends TestCase {
    private final TransactionCoordinator coordinator =
	new TransactionCoordinatorImpl();
    
    /** Creates the test. */
    public TestTransactionCoordinatorImpl(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
    }

    /* -- Test TransactionHandle.commit -- */

    public void testCommitActiveEmpty() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	handle.commit();
	assertFalse(handle.isActive());
    }

    public void testCommitActive() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant(),
	    new DummyTransactionParticipant() };
	Transaction txn = handle.getTransaction();
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	handle.commit();
	for (DummyTransactionParticipant participant : participants) {
	    assertEquals(DummyTransactionParticipant.State.COMMITTED,
			 participant.getState());
	}
	assertFalse(handle.isActive());
    }

    public void testCommitAborted() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCommitCommitted() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	try {
	    handle.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCommitFailsMiddle() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    throw new IOException("Prepare failed");
		}
	    },
	    new DummyTransactionParticipant() };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    public void testCommitFailsLast() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant(),
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
		    throw new IOException("Prepare failed");
		}
	    } };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    public void testCommitAbortsMiddle() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    txn.abort();
		    return false;
		}
	    },
	    new DummyTransactionParticipant() };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    public void testCommitAbortsLast() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant(),
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
		    txn.abort();
		}
	    } };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    public void testCommitAbortsAndFailsMiddle() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    txn.abort();
		    throw new IOException("Prepare failed");
		}
	    },
	    new DummyTransactionParticipant() };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    public void testCommitAbortsAndFailsLast() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant(),
	    new DummyTransactionParticipant() {
		public void prepareAndCommit(Transaction txn) throws Exception {
		    txn.abort();
		    throw new IOException("Prepare failed");
		}
	    } };
	Transaction txn = handle.getTransaction();
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
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
    }

    /* -- Test TransactionHandle.isActive -- */

    public void testIsActiveActive() {
	TransactionHandle handle = coordinator.createTransaction();
	assertTrue(handle.isActive());
    }

    public void testIsActivePreparing() {
	final TransactionHandle handle = coordinator.createTransaction();
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public boolean prepare(Transaction txn) throws Exception {
		    assertFalse(handle.isActive());
		    return super.prepare(txn);
		}
	    };
	handle.getTransaction().join(participant);
    }

    public void testIsActiveAborting() {
	final TransactionHandle handle = coordinator.createTransaction();
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void abort(Transaction txn) {
		    assertFalse(handle.isActive());
		    super.abort(txn);
		}
	    };
	Transaction txn = handle.getTransaction();
	txn.join(participant);
	txn.abort();
    }

    public void testIsActiveAborted() {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	assertFalse(handle.isActive());
    }

    public void testIsActiveCommitting() throws Exception {
	final TransactionHandle handle = coordinator.createTransaction();
	TransactionParticipant participant =
	    new DummyTransactionParticipant() {
		public void commit(Transaction txn) {
		    assertFalse(handle.isActive());
		    super.commit(txn);
		}
	    };
	handle.getTransaction().join(participant);
	handle.commit();
    }

    public void testIsActiveCommitted() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	handle.getTransaction().join(new DummyTransactionParticipant());
	handle.commit();
	assertFalse(handle.isActive());
    }

    /* -- Test Transaction.getId -- */

    public void testGetId() {
	Transaction txn1 = coordinator.createTransaction().getTransaction();
	Transaction txn2 = coordinator.createTransaction().getTransaction();
	assertNotNull(txn1.getId());
	assertNotNull(txn2.getId());
	assertFalse(Arrays.equals(txn1.getId(), txn2.getId()));
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
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	try {
	    txn.join(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testJoinMultipleDurable() {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	try {
	    txn.join(new DummyTransactionParticipant());
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    /* -- Test Transaction.abort -- */

    public void testAbortActiveEmpty() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	handle.getTransaction().abort();
	assertFalse(handle.isActive());
    }

    public void testAbortActive() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	DummyTransactionParticipant[] participants = {
	    new DummyNonDurableTransactionParticipant(),
	    new DummyNonDurableTransactionParticipant(),
	    new DummyTransactionParticipant() };
	Transaction txn = handle.getTransaction();
	for (TransactionParticipant participant : participants) {
	    txn.join(participant);
	}
	txn.abort();
	for (DummyTransactionParticipant participant : participants) {
	    assertEquals(DummyTransactionParticipant.State.ABORTED,
			 participant.getState());
	}
	assertFalse(handle.isActive());
    }

    public void testAbortAborted() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	txn.abort();
	try {
	    txn.abort();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    public void testAbortCommitted() throws Exception {
	TransactionHandle handle = coordinator.createTransaction();
	Transaction txn = handle.getTransaction();
	txn.join(new DummyTransactionParticipant());
	handle.commit();
	try {
	    txn.abort();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test equals -- */

    public void testEquals() throws Exception {
	Transaction txn1 = coordinator.createTransaction().getTransaction();
	Transaction txn2 = coordinator.createTransaction().getTransaction();
	assertFalse(txn1.equals(null));
	assertTrue(txn1.equals(txn1));
	assertFalse(txn1.equals(txn2));
    }
}
