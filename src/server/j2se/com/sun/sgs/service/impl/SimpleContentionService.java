
package com.sun.sgs.service.impl;

import com.sun.sgs.kernel.AppContext;
import com.sun.sgs.kernel.TransactionProxy;

import com.sun.sgs.service.ContentionHandler;
import com.sun.sgs.service.ContentionService;
import com.sun.sgs.service.NotPreparedException;
import com.sun.sgs.service.Transaction;

import java.util.concurrent.ConcurrentHashMap;


/**
 *
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleContentionService implements ContentionService {

    /**
     * The identifier for this <code>Service</code>.
     */
    public static final String IDENTIFIER =
        SimpleContentionService.class.getName();

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy = null;

    // the context in which this instance runs
    private AppContext appContext;

    // the map of active transactions being handled
    private ConcurrentHashMap<Long,ContentionHandler> txnMap;

    /**
     *
     */
    public SimpleContentionService() {
        txnMap = new ConcurrentHashMap<Long,ContentionHandler>();
    }

    /**
     * Returns the identifier used to identify this service.
     *
     * @return the service's identifier
     */
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * Configures this <code>Service</code>.
     *
     * @param appContext this <code>Service</code>'s <code>AppContext</code>
     * @param transactionProxy a non-null proxy that provides access to the
     *                         current <code>Transaction</code>
     */
    public void configure(AppContext appContext,
                          TransactionProxy transactionProxy) {
        this.appContext = appContext;
        this.transactionProxy = transactionProxy;
    }

    /**
     * Tells the <code>Service</code> to prepare for commiting its
     * state assciated with the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void prepare(Transaction txn) throws Exception {
        
    }

    /**
     * Tells the <code>Service</code> to commit its state associated
     * with the previously prepared transaction.
     *
     * @param txn the <code>Transaction</code> state
     *
     * @throws NotPreparedException if prepare wasn't previously called
     *                              on this service for this transaction
     */
    public void commit(Transaction txn) throws NotPreparedException {
        // make sure there was a handler for this transaction, removing
        // it from the map
        ContentionHandler handler = txnMap.remove(txn.getId());
    }

    /**
     * Tells the <code>Service</code> to both prepare and commit its
     * state associated with the given transaction. This is provided as
     * an optimization for cases where the sysem knows that a given
     * transaction cannot fail, or can be partially backed out.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        prepare(txn);
        commit(txn);
    }

    /**
     * Tells the <code>Service</code> to abort its involvement with
     * the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void abort(Transaction txn) {
        // remove the state from the map
        txnMap.remove(txn.getId());
    }

    /**
     * Returns the <code>ContentionHandler</code> used for the given
     * <code>Transaction</code>.
     *
     * @param txn the transaction state
     *
     * @return the handler for this transaction
     */
    public ContentionHandler getContentionHandler(Transaction txn) {
        // see if there's already a handler set for this transaction
        ContentionHandler handler = txnMap.get(txn.getId());
        if (handler != null)
            return handler;

        // create the handler, and join the transaction
        handler = new SimpleContentionHandler(this, txn);
        txnMap.put(txn.getId(), handler);
        txn.join(this);

        return handler;
    }

}
