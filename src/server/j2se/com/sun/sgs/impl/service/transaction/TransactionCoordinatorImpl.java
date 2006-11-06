package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides an implementation of <code>TransactionCoordinator</code>.  This
 * class is thread safe, but the {@link TransactionHandle} returned by the
 * {@link #createTransaction createTransaction} method, and the {@link
 * Transaction} associated with the handle, are not thread safe.  Callers
 * should provide their own synchronization to insure that those objects are
 * not accessed concurrently from multiple threads. <p>
 *
 * Transactions created by this class can only support at most a single durable
 * participant &mdash; one that does not implement {@link
 * NonDurableTransactionParticipant}.  The {@link Transaction#join join} method
 * on transactions created using this class will throw {@link
 * UnsupportedOperationException} if more than one durable participant attempts
 * to join the transaction. <p>
 *
 * The <code>Transaction</code> and <code>TransactionHandle</code> instances
 * returned by this class are not synchronized.  If multiple threads access
 * these instances concurrently, then synchronization must be performed by the
 * caller.  Note that calls to the {@link #createTransaction createTransaction}
 * method itself are synchronized.
 */
public final class TransactionCoordinatorImpl
    implements TransactionCoordinator
{
    /** The next transaction ID. */
    private AtomicLong nextTid = new AtomicLong(1);

    /** An implementation of TransactionHandle. */
    private static final class TransactionHandleImpl
	implements TransactionHandle
    {
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
	    if (txn.isActive()) {
		return txn;
	    } else {
		throw new TransactionNotActiveException(
		    "No transaction is active");
	    }
	}

	public void commit() throws Exception {
	    txn.commit();
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  No properties are currently supported.
     *
     * @param	properties the properties for configuring this service
     */
    public TransactionCoordinatorImpl(Properties properties) {
	if (properties == null) {
	    throw new NullPointerException("Properties must not be null");
	}
    }

    /** {@inheritDoc} */
    public TransactionHandle createTransaction() {
	return new TransactionHandleImpl(nextTid.getAndIncrement());
    }
}
