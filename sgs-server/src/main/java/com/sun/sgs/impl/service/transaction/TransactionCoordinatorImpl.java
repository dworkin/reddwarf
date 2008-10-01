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

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
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
 * The <code>Transaction</code> instances created by this class are not
 * synchronized; methods on those instances should only be called from a single
 * thread. <p>
 *
 * The default timeout for bounded transactions created by this class is
 * 100ms. The default timeout for unbounded transactions created by this class
 * is Long.MAX_VALUE. These defaults may be overridden using the properties
 * <code>TransactionCoordinator.TXN_TIMEOUT_PROPERTY</code> and
 * <code>TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY</code>
 * respectively.
 */
public final class TransactionCoordinatorImpl
    implements TransactionCoordinator
{
    /** The next transaction ID. */
    private AtomicLong nextTid = new AtomicLong(1);

    /** The collector handle for reporting participant details. */
    private final ProfileCollectorHandle collectorHandle;
    /** The collector to decide if we want to collect participant details for
     *  a particular transaction.
     */
    private final ProfileCollector collector;

    /** The value for bounded timeout. */
    private final long boundedTimeout;

    /** The default for bounded timeout. */
    public static final long BOUNDED_TIMEOUT_DEFAULT = 100L;

    /** The value for unbounded timeout. */
    private final long unboundedTimeout;

    /** The default for unbounded timeout. */
    public static final long UNBOUNDED_TIMEOUT_DEFAULT = Long.MAX_VALUE;

    /** Should we use prepareAndCommit() or separate calls? */
    private final boolean disablePrepareAndCommitOpt;
    
    /** An implementation of TransactionHandle. */
    private static final class TransactionHandleImpl
	implements TransactionHandle
    {
	/** The transaction. */
	private final TransactionImpl txn;

	/**
	 * Creates a transaction with the specified ID, timeout, 
         * prepareAndCommit optimization boolean, and collector.
	 */
	TransactionHandleImpl(long tid, long timeout,
                              boolean disablePrepareAndCommitOpt,
			      ProfileCollectorHandle collectorHandle) {
	    txn = new TransactionImpl(tid, timeout, 
                                      disablePrepareAndCommitOpt, 
                                      collectorHandle);
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
     * properties.
     *
     * @param	properties the properties for configuring this service
     * @param   collector the {@code ProfileCollector} used to decide if we
     *           want to collect participant data for a particular transaction,
     *           based on the current default collection level
     * @param	collectorHandle the {@code ProfileCollectorHandle} used to 
     *          report participant detail
     * @throws	IllegalArgumentException if the bounded or
     *		unbounded timeout properties are less than {@code 1}
     */
    public TransactionCoordinatorImpl(Properties properties,
                                      ProfileCollector collector,
				      ProfileCollectorHandle collectorHandle) 
    {
	if (properties == null) {
	    throw new NullPointerException("Properties must not be null");
	}
        if (collector == null) {
	    throw new NullPointerException("Collector must not be null");
	}
        if (collectorHandle == null) {
	    throw new NullPointerException("CollectorHandle must not be null");
	}
	
        this.collector = collector;
        this.collectorHandle = collectorHandle;

	PropertiesWrapper props = new PropertiesWrapper(properties);
	this.boundedTimeout =
	    props.getLongProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
				  BOUNDED_TIMEOUT_DEFAULT, 1, Long.MAX_VALUE);
	this.unboundedTimeout =
	    props.getLongProperty(TransactionCoordinator.
				  TXN_UNBOUNDED_TIMEOUT_PROPERTY,
				  UNBOUNDED_TIMEOUT_DEFAULT, 1,
				  Long.MAX_VALUE);
        this.disablePrepareAndCommitOpt =
            props.getBooleanProperty(
                TransactionCoordinator.
                    TXN_DISABLE_PREPAREANDCOMMIT_OPT_PROPERTY,
                false);
    }

    /** {@inheritDoc} */
    public TransactionHandle createTransaction(boolean unbounded) {
        boolean collectData = 
            collector.getDefaultProfileLevel().ordinal() >= 
                ProfileLevel.MEDIUM.ordinal();
	if (unbounded) {
	    return new TransactionHandleImpl(nextTid.getAndIncrement(),
					     unboundedTimeout, 
                                             disablePrepareAndCommitOpt,
                                             collectData ? 
                                                 collectorHandle : null);
	} else {
	    return new TransactionHandleImpl(nextTid.getAndIncrement(),
					     boundedTimeout, 
                                             disablePrepareAndCommitOpt,
                                             collectData ? 
                                                 collectorHandle : null);
	}
    }
}
