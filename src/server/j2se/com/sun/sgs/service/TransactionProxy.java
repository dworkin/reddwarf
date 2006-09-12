
package com.sun.sgs.service;

import com.sun.sgs.service.Transaction;


/**
 * This is a proxy that provides access to the current transaction's
 * state.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TransactionProxy {

    
    /**
     * Returns the current transaction state.
     *
     * @return the current <code>Transaction</code>
     */
    public Transaction getCurrentTransaction();

}
