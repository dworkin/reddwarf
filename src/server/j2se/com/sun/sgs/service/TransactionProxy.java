
package com.sun.sgs.service;

import com.sun.sgs.kernel.TaskOwner;


/**
 * This is a proxy that provides access to the current transaction and
 * its owner.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface TransactionProxy {

    /**
     * Returns the current transaction state.
     *
     * @return the current <code>Transaction</code>
     */
    public Transaction getCurrentTransaction();

    /**
     * Returns the owner of the task that is executing the current
     * transaction.
     *
     * @return the current transaction's <code>TaskOwner</code>
     */
    public TaskOwner getCurrentOwner();

}
