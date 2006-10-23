package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;

/**
 * Provides an implementation of <code>TransactionCoordinator</code>.  This
 * class is thread safe, but the {@link TransactionHandle} returned by the
 * {@link #createTransaction createTransaction} method, and the {@link
 * Transaction} associated with the handle are not thread safe.  Callers should
 * provide their own synchronization to insure that those objects are not
 * accessed concurrently from multiple threads. <p>
 *
 * Transactions created by this class can only support at most a single durable
 * participant &emdash; one that does not implement {@link
 * NonDurableTransactionParticipant}.  The {@link Transaction#join join} method
 * on transactions created using this class will throw {@link
 * UnsupportedOperationException} if more than one durable participant attempts
 * to join the transacction.
 */
public final class TransactionCoordinatorImpl
    implements TransactionCoordinator
{
    /** Synchronize on this lock when accessing the nextTid field. */
    private final Object lock = new Object();

    /** The next transaction ID. */
    private long nextTid = 1;

    /** An implementation of TransactionHandle. */
    private final class TransactionHandleImpl implements TransactionHandle {

	/** The transaction. */
	private final TransactionImpl txn;

	/** Creates a transaction with the specified ID. */
	TransactionHandleImpl(long tid) {
	    txn = new TransactionImpl(tid);
	}

	public String toString() {
	    return "TransactionHandle[txn:" + txn + "]";
	}

	/* -- Implement TransactionHandle -- */

	public Transaction getTransaction() {
	    return txn;
	}

	public void commit() throws Exception {
	    txn.commit();
	}

	public boolean isActive() {
	    return txn.isActive();
	}
    }

    /** Creates an instance of this class. */
    public TransactionCoordinatorImpl() { }

    /** {@inheritDoc} */
    public TransactionHandle createTransaction() {
	long tid;
	synchronized (lock) {
	    tid = nextTid++;
	}
	return new TransactionHandleImpl(tid);
    }
}
