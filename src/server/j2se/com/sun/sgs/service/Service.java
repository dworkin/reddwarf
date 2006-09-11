
package com.sun.sgs.service;

import com.sun.sgs.kernel.AppContext;
import com.sun.sgs.kernel.TransactionProxy;


/**
 * This is the core interface used for all services that participate in
 * transactions. Most implementations of <code>Service</code> will
 * actually implement specific sub-interfaces like <code>DataService</code>.
 * <p>
 * Note that <code>Service</code> instances are created as part of an
 * application context, which in turn is created from the system's
 * configuration data. When this happens, each <code>Service</code> is
 * configured with the application context, a proxy to resolve the
 * current transaction state, and references to all the other
 * <code>Service</code> instance that this <code>Service</code> needs.
 * To accept this configuration, any implementations of <code>Service</code>
 * must include a method named <code>configure</code>. The first parameter
 * is <code>AppContext</code>, the second parameter is
 * <code>TransactionProxy</code>, and then any following parameters are
 * specific types of <code>Service</code>s that will be defined in the
 * application's configuration. For example:
 * <pre>
 *    public void configure(AppContext appContext,
 *                          TransactionProxy transactionProxy,
 *                          DataService dataService,
 *                          ContentionService contentionService);
 * </pre>
 * If an implementation of <code>Service</code> does not implement a
 * <code>configure</code> method then the implementation is considered
 * invalid and will not be loaded.
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
