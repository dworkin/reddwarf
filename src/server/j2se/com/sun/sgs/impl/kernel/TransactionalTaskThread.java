
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.service.Transaction;


/**
 * This package-private class is the concrete version of <code>Thread</code>
 * used by the system. It is a <code>TaskThread</code> that can manage an
 * active <code>Transaction</code>. This implementation is package-private
 * so that only select consumers can actually access or change the
 * current transaction. Most users interact with the current transaction
 * through <code>TransactionProxy</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
final class TransactionalTaskThread extends TaskThread {

    // the current transaction
    private Transaction currentTransaction;

    /**
     * Creates an instance of <code>TransactionalTaskThread</code>.
     *
     * @param resourceCoordinator the <code>ResourceCoordinatorImpl</code>
     *                            that manages this thread
     */
    TransactionalTaskThread(ResourceCoordinatorImpl resourceCoordinator) {
        super(resourceCoordinator);
        currentTransaction = null;
    }

    /**
     * Returns the current <code>Transaction</code>, throwing an exception
     * if there is no currently active transaction.
     *
     * @return the currently active <code>Transaction</code>
     *
     * @throws TransactionNotActiveException if there is no currently active
     *                                       transaction
     */
    Transaction getCurrentTransaction() {
        if (currentTransaction == null)
            throw new TransactionNotActiveException("no current transaction");
        return currentTransaction;
    }

    /**
     * Sets the current <code>Transaction</code>, throwing an exception if
     * there is already an active transaction.
     *
     * @param transaction the <code>Transaction</code> to set as currently
     *                    active
     *
     * @throws IllegalStateException if there is already an active transaction
     */
    void setCurrentTransaction(Transaction transaction) {
        if (transaction == null)
            throw new NullPointerException("null transactions not allowed");
        if (currentTransaction != null)
            throw new IllegalStateException("an active transaction is " +
                                            "currently running");
        currentTransaction = transaction;
    }

    /**
     * Clears the currently active <code>Transaction</code>. This is
     * typically done once a transaction has finished.
     *
     * @param transaction the <code>Transaction</code> to clear, which
     *                    must be the currently active transaction
     *
     * @throws IllegalStateException if there is no currently active
     *                               transaction to clear
     * @throws IllegalArgumentException if the given <code>Transaction</code>
     *                                  is not the active transaction
     */
    void clearCurrentTransaction(Transaction transaction) {
        if (currentTransaction == null)
            throw new IllegalStateException("no active transaction to clear");
        if (! currentTransaction.equals(transaction))
            throw new IllegalArgumentException("provided transaction is " +
                                               "not currently active");
        currentTransaction = null;
    }

}
