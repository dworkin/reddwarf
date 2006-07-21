
package com.sun.sgs.kernel;

import com.sun.sgs.Quality;

import com.sun.sgs.service.Transaction;


/**
 * This is responsible for creating new <code>Transaction</code>s as
 * needed. This involves assigning <code>Service</code>s to
 * the new <code>Transaction</code> and working with other kernel
 * components to choose the correct implementation of
 * <code>Transaction</code> for the provided parameters. Note that
 * there is only one instance of <code>TransactionCoordinator</code>
 * used within a given server instance.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TransactionCoordinator {

    /**
     * Returns new transaction state, appropriate for the given
     * parameters, that can be used to execute a transactional task.
     * <p>
     * FIXME: This also needs to take meta-data about the app, etc.
     *
     * @param quality the Quality of Service parameters
     *
     * @return a new <code>Transaction</code>
     */
    public Transaction getTransaction(Quality quality);

}
