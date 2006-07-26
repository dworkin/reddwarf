
package com.sun.sgs.kernel;

import com.sun.sgs.service.Transaction;


/**
 * This is a proxy that provides access to the current transaction's
 * state. Because only the <code>Service</code>s should have visibility
 * into this state, they are the only components in the system that
 * are provided references to a proxy. This class cannot be instantiated
 * by code outside of the <code>kernel</code> package.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public final class TransactionProxy {

    /**
     * This package-private constructor creates a new instance of
     * <code>TransactionProxy</code>. It is package-private so that only
     * the <code>kernel</code> components can create instances for access
     * to the current <code>Transaction</code> state.
     */
    TransactionProxy() {
        
    }

    /**
     * Returns the current transaction state from the running task.
     *
     * @return the current <code>Transaction</code>
     */
    public Transaction getCurrentTransaction() {
        return ((TransactionalTaskThread)(Thread.currentThread())).
            getTransaction();
    }

}
