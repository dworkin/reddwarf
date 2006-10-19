
package com.sun.sgs.service;


/**
 * This interface represents a single transaction. It is used by
 * participants to join a transaction and manage state associated with
 * a transaction.
 * <p>
 * All implementations of <code>Transaction</code> must implement
 * <code>equals</code> and <code>hashCode</code>. Two
 * <code>Transaction</code>s are equal if and only if they represent
 * the same transaction.
 *
 * @since 1.0
 * @author Seth Proctor
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
     * Tells the <code>Transaction</code> that the given
     * <code>TransactionParticipant</code> is participating in the
     * transaction. A <code>TransactionParticipant</code> is allowed to
     * join a <code>Transaction</code> more than once, but will only
     * be registered as a single participant.
     *
     * @param participant the <code>TransactionParticipant</code> joining
     *                    the transaction
     *
     * @throws IllegalStateException if the transaction has committed, is
     *                               already committing, or has been
     *                               aborted
     */
    public void join(TransactionParticipant participant);

    /**
     * Aborts the transaction. This notifies all participants that the
     * transaction has aborted, and invalidates all future use of
     * this transaction. The caller should always follow a call to
     * <code>abort</code> by throwing an exception that details why
     * the transaction was aborted. This is needed not only to
     * communicate the cause of the abort and whether to retry the
     * exception, but also because the application code associated with
     * this transaction will continue to execute normally unless an
     * exception is raised.
     *
     * @throws IllegalStateException if the transaction has committed, is
     *                               already committing, or has already been
     *                               aborted
     */
    public void abort();

}
