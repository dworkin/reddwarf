
package com.sun.sgs.service;

import com.sun.sgs.kernel.TransactionProxy;


/**
 * This is the core interface used for all services that participate in
 * transactions. Most implementations of <code>Service</code> will
 * actually implement specific sub-interfaces like <code>DataService</code>.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface Service
{

    /**
     * Returns the identifier used to identify this service.
     *
     * @return the service's identifier
     */
    public String getIdentifier();

    /**
     * Provides this <code>Service</code> access to the current transaction
     * state.
     * <p>
     * FIXME: should this be done like this, as a required parameter to
     * the constructor, or through a more structured factory mechanism? The
     * answer is probably the latter, but that won't be designed until some
     * of the configuration code is designed.
     *
     * @param transactionProxy a non-null proxy that provides access to the
     *                         current <code>Transaction</code>
     */
    public void setTransactionProxy(TransactionProxy transactionProxy);

    /**
     * Tells the <code>Service</code> to prepare for commiting its
     * state assciated with the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     *
     * FIXME: what does this throw? For cost reasons, should this actually
     * be some kind of error code instead of an exception?
     */
    public void prepare(Transaction txn) throws Exception;

    /**
     * Tells the <code>Service</code> to commit its state associated
     * with the previously prepared transaction.
     *
     * @param txn the <code>Transaction</code> state
     *
     * @throws NotPreparedException if prepare wasn't previously called
     *                              on this service for this transaction
     */
    public void commit(Transaction txn) throws NotPreparedException;

    /**
     * Tells the <code>Service</code> to both prepare and commit its
     * state associated with the given transaction. This is provided as
     * an optimization for cases where the sysem knows that a given
     * transaction cannot fail, or can be partially backed out.
     *
     * @param txn the <code>Transaction</code> state
     *
     * FIXME: what does this throw? (also, see comments in prepare)
     */
    public void prepareAndCommit(Transaction txn) throws Exception;

    /**
     * Tells the <code>Service</code> to abort its involvement with
     * the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void abort(Transaction txn);

}
