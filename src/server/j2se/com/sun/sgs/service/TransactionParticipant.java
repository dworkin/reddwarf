/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;


/**
 * This interface is used by participants in transactions. Typically, each
 * implementation of <code>Service</code> will either implement
 * <code>TransactionParticipant</code> directly, or use some proxy as
 * their participant. Classes that implement
 * <code>TransactionParticipant</code> must also implement
 * <code>Serializable</code>.
 * <p>
 * Note that the general model assumes that <code>Service</code>s will use
 * each other during a transaction. For instance, most <code>Service</code>s
 * will use the <code>DataService</code> to persist data. However once the
 * transaction begins to prepare or is aborted (i.e., once any of the methods
 * defined here are called on a participant), a <code>Service</code> may
 * not interact with any other <code>Service</code> in the context of that
 * transaction. Doing so results in unspecified behavior.
 * <p>
 * This interface does not specify how transaction participants learn the
 * outcome of prepared transactions following a crash.  Doing so requires a
 * separate interaction between the participant and the transaction coordinator
 * that is not specified by this interface.  Without that additional
 * communication, this interface is sufficient to support transactions with at
 * most one durable transaction participant.
 *
 * @since 1.0
 * @author Seth Proctor
 * @see NonDurableTransactionParticipant
 */
public interface TransactionParticipant {

    /**
     * Tells the participant to prepare for commiting its state associated
     * with the given transaction. This method returns a <code>boolean</code>
     * flag stating whether the prepared state is read-only, meaning that no
     * external state is modified by this participant. If this method
     * returns true, then neither <code>commit</code> nor <code>abort</code>
     * will be called.
     * <p>
     * If this method throws an exception, then the preparation failed, and
     * the transaction will be aborted. If this method completes successfully,
     * then the participant is required to be able to commit the transaction
     * without failure.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @return true if this participant is read-only, false otherwise
     *
     * @throws Exception if there are any failures in preparing
     * @throws IllegalStateException if this participant has already been
     *                               prepared, committed, or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    public boolean prepare(Transaction txn) throws Exception;

    /**
     * Tells the participant to commit its state associated with the given
     * transaction.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws IllegalStateException if this participant was not previously
     *                               prepared, or if this participant has
     *                               already committed or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    public void commit(Transaction txn);

    /**
     * Tells the participant to both prepare and commit its state associated
     * with the given transaction. 
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws Exception if there are any failures in preparing
     * @throws IllegalStateException if this participant has already been
     *                               prepared, committed, or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    public void prepareAndCommit(Transaction txn) throws Exception;

    /**
     * Tells the participant to abort its involvement with the given
     * transaction.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws IllegalStateException if this participant has already been
     *                               aborted or committed, or if this
     *                               participant is not participating in
     *                               the given transaction
     */
    public void abort(Transaction txn);

}
