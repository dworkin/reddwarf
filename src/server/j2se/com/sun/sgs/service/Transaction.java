/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;


/**
 * This interface represents a single transaction. It is used by
 * participants to join a transaction and manage state associated with
 * a transaction.
 * <p>
 * Note that some transaction implementations may only support transactions
 * with at most one durable transaction participant, because of the need to
 * communicate the outcome of prepared transactions to transaction participants
 * following a crash when there are multiple durable participants.
 * <p>
 * All implementations of <code>Transaction</code> must implement
 * <code>equals</code> and <code>hashCode</code>. Two
 * <code>Transaction</code>s are equal if and only if they represent
 * the same transaction.
 */
public interface Transaction {

    /**
     * Returns the unique identifier for this <code>Transaction</code>. If
     * two <code>Transaction</code>s have the same identifier then they
     * represent the same transaction. This will always return a unique
     * copy of the identifier.
     *
     * @return the transaction's identifier
     */
    public byte [] getId();

    /**
     * Returns the time at which this <code>Transaction</code> was created.
     * This is a value in milliseconds measured from 1/1/1970. This is
     * typically used for determining whether a <code>Transaction</code>
     * has run too long, or how it should be re-scheduled, but in
     * practice may be used as a participant sees fit.
     *
     * @return the creation time-stamp
     */
    public long getCreationTime();

    /**
     * Returns the length of time in milliseconds that this
     * <code>Transaction</code> is allowed to run before it should timeout.
     *
     * @return the timeout length
     */
    public long getTimeout();

    /**
     * Checks if this <code>Transaction</code> has timed out, throwing a
     * <code>TransactionTimeoutException</code> if it has.
     *
     * @throws TransactionTimeoutException if the transaction has timed out
     */
    public void checkTimeout();

    /**
     * Tells the <code>Transaction</code> that the given
     * <code>TransactionParticipant</code> is participating in the
     * transaction. A <code>TransactionParticipant</code> is allowed to
     * join a <code>Transaction</code> more than once, but will only
     * be registered as a single participant.
     * <p>
     * If the transaction has been aborted, then the exception thrown will have
     * as its cause the value provided in the first call to {@link #abort
     * abort}, if any.  If the cause implements {@link ExceptionRetryStatus},
     * then the exception thrown will, too, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method will return the
     * value returned by calling that method on the cause.  If no cause was
     * supplied, then the exception will either not implement {@code
     * ExceptionRetryStatus} or its {@code shouldRetry} method will return
     * {@code false}.
     *
     * @param participant the <code>TransactionParticipant</code> joining
     *                    the transaction
     *
     * @throws TransactionNotActiveException if the transaction has been
     *                                       aborted
     *
     * @throws IllegalStateException if {@link TransactionParticipant#prepare
     *				     prepare} has been called on any
     *				     transaction participant and the
     *				     transaction has not been aborted
     *
     * @throws UnsupportedOperationException if <code>participant</code> does
     *         not implement {@link NonDurableTransactionParticipant} and the
     *         implementation cannot support an additional durable transaction
     *         participant
     */
    public void join(TransactionParticipant participant);

    /**
     * Aborts the transaction, optionally supplying the exception that caused
     * the abort. This notifies all participants that the transaction has
     * aborted, and invalidates all future use of this transaction. The caller
     * should always follow a call to <code>abort</code> by throwing an
     * exception that details why the transaction was aborted. This is needed
     * not only to communicate the cause of the abort and whether to retry the
     * exception, but also because the application code associated with this
     * transaction will continue to execute normally unless an exception is
     * raised. Supplying the cause to this method allows future calls to the
     * transaction to include the cause to explain why the transaction is no
     * longer active.
     * <p>
     * If the transaction has been aborted, then the exception thrown will have
     * as its cause the value provided in the first call to {@link #abort
     * abort}, if any.  If the cause implements {@link ExceptionRetryStatus},
     * then the exception thrown will, too, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method will return the
     * value returned by calling that method on the cause.  If no cause was
     * supplied, then the exception will either not implement {@code
     * ExceptionRetryStatus} or its {@code shouldRetry} method will return
     * {@code false}.
     *
     * @param cause the exception that caused the abort, or <code>null</code>
     *		    if the cause is not known or the abort was not caused by an
     *		    exception
     *
     * @throws TransactionNotActiveException if the transaction has been
     *					     aborted
     *
     * @throws IllegalStateException if all transaction participants have been
     *                               prepared and {@link #abort abort} has not
     *                               been called
     */
    public void abort(Throwable cause);

    /**
     * Returns information about whether {@link #abort abort} has been called
     * on this transaction.
     *
     * @return {@code true} if {@code abort} has been called on this
     *         transaction, else {@code false}
     *
     */
    public boolean isAborted();

    /**
     * Returns the cause supplied in the first call to {@link #abort abort} on
     * this transaction, or {@code null} if {@code abort} has not been called.
     *
     * @return the exception that caused the abort or {@code null}
     */
    public Throwable getAbortCause();
}
