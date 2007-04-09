/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.kernel.ProfileCollector;
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
 * The <code>Transaction</code> instances created by this class are not
 * synchronized; methods on those instances should only be called from a single
 * thread.
 */
public final class TransactionCoordinatorImpl
    implements TransactionCoordinator
{
    /** The next transaction ID. */
    private AtomicLong nextTid = new AtomicLong(1);

    /** The optional collector for reporting participant details. */
    private final ProfileCollector collector;

    /** An implementation of TransactionHandle. */
    private static final class TransactionHandleImpl
	implements TransactionHandle
    {
	/** The transaction. */
	private final TransactionImpl txn;

	/** Creates a transaction with the specified ID and collector. */
	TransactionHandleImpl(long tid, ProfileCollector collector) {
	    txn = new TransactionImpl(tid, collector);
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
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  No properties are currently supported.
     *
     * @param	properties the properties for configuring this service
     * @param	collector the <code>ProfileCollector</code> used to report
     *       	participant detail or <code>null</code> if profiling is
     *       	disabled
     */
    public TransactionCoordinatorImpl(Properties properties,
				      ProfileCollector collector) {
	if (properties == null) {
	    throw new NullPointerException("Properties must not be null");
	}
	this.collector = collector;
    }

    /** {@inheritDoc} */
    public TransactionHandle createTransaction() {
	return new TransactionHandleImpl(nextTid.getAndIncrement(), collector);
    }
}
