/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionAbortedException;
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
 * <p>
 * The implementations of the {@link #join join}, {@link #abort abort}, and
 * {@link #registerListener registerListener} methods of this interface are not
 * thread-safe.  Callers should insure that calls they make to these methods
 * are made from the thread that created the transaction.
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
    byte[] getId();

    /**
     * Returns the time at which this <code>Transaction</code> was created.
     * This is a value in milliseconds measured from 1/1/1970. This is
     * typically used for determining whether a <code>Transaction</code>
     * has run too long, or how it should be re-scheduled, but in
     * practice may be used as a participant sees fit.
     *
     * @return the creation time-stamp
     */
    long getCreationTime();

    /**
     * Returns the length of time in milliseconds that this
     * <code>Transaction</code> is allowed to run before it should timeout.
     *
     * @return the timeout length
     */
    long getTimeout();

    /**
     * Checks if this <code>Transaction</code> has timed out, throwing a
     * <code>TransactionTimeoutException</code> if it has.
     *
     * @throws TransactionNotActiveException if the transaction is not active
     * @throws TransactionTimeoutException if the transaction has timed out
     * @throws IllegalStateException if called from a thread that is not the
     *				     thread that created this transaction
     */
    void checkTimeout();

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
     *				     transaction has not been aborted, or if
     *				     called from a thread that is not the
     *				     thread that created this transaction
     *
     * @throws UnsupportedOperationException if <code>participant</code> does
     *         not implement {@link NonDurableTransactionParticipant} and the
     *         implementation cannot support an additional durable transaction
     *         participant
     */
    void join(TransactionParticipant participant);

    /**
     * Aborts the transaction, specifying the cause. This notifies all
     * participants that the transaction has aborted, and invalidates all
     * future use of this transaction. The caller should always follow a call
     * to <code>abort</code> by throwing an exception that details why the
     * transaction was aborted. If the exception could be caught by application
     * code, then the exception thrown should be a {@link
     * TransactionAbortedException}, created by wrapping the original cause if
     * needed. Throwing an exception is needed not only to communicate the
     * cause of the abort and whether to retry the exception, but also because
     * the application code associated with this transaction will continue to
     * execute normally unless an exception is raised. Supplying the cause to
     * this method allows future calls to the transaction to include the cause
     * to explain why the transaction is no longer active.
     * <p>
     * If the transaction has been aborted, then the exception thrown will have
     * as its cause the value provided in the first call to {@link #abort
     * abort}, if any.  If the cause implements {@link ExceptionRetryStatus},
     * then the exception thrown will, too, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method will return the
     * value returned by calling that method on the cause.
     *
     * @param cause the exception that caused the abort
     *
     * @throws TransactionNotActiveException if the transaction has been
     *					     aborted
     *
     * @throws IllegalStateException if all transaction participants have been
     *                               prepared and {@link #abort abort} has not
     *                               been called, or if called from a thread
     *				     that is not the thread that created this
     *				     transaction
     */
    void abort(Throwable cause);

    /**
     * Returns information about whether {@link #abort abort} has been called
     * on this transaction.
     *
     * @return {@code true} if {@code abort} has been called on this
     *         transaction, else {@code false}
     */
    boolean isAborted();

    /**
     * Returns the cause supplied in the first call to {@link #abort abort} on
     * this transaction, or {@code null} if {@code abort} has not been called.
     *
     * @return the exception that caused the abort or {@code null}
     */
    Throwable getAbortCause();

    /**
     * Registers a listener that will be notified just before this transaction
     * is prepared, and after it commits or aborts. <p>
     *
     * The {@code listener}'s {@link TransactionListener#beforeCompletion
     * beforeCompletion} method will be called after the main work of the
     * transaction is complete, just before the transaction is prepared.  The
     * transaction will still be considered active at the time of the call,
     * although calls should not be made to independent {@link Service}s.  If
     * the call to {@code beforeCompletion} throws an exception, then this
     * transaction will be aborted, and the exception will be treated as if it
     * were thrown by the main body of the transaction.  The {@code listener}'s
     * {@code beforeCompletion} method will not be called if this transaction
     * is aborted before it reaches the preparation stage, including if an
     * earlier call to {@code beforeCompletion} on another listener throws an
     * exception or aborts this transaction. <p>
     *
     * The {@code listener}'s {@link TransactionListener#afterCompletion
     * afterCompletion} method will be called after this transaction is
     * committed or aborted. <p>
     *
     * Any number of listeners can be registered for this transaction by making
     * multiple calls to this method.  If multiple listeners are registered,
     * the order in which the listeners are called is unspecified.
     *
     * @param	listener the listener
     * @throws	TransactionNotActiveException if this transaction is not
     *		active
     * @throws	IllegalStateException if called from a thread that is not the
     *		thread that created this transaction
     */
    void registerListener(TransactionListener listener);
}
