
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.kernel.TaskThread;

import com.sun.sgs.service.Transaction;


/**
 * This package-private class is the concrete version of <code>Thread</code>
 * used by the system. It is a <code>TaskThread</code> that can optionally
 * store transaction state. This implementation is package-private so that
 * only select consumers can actually access the transaction state.
 * <p>
 * FIXME: This has been exposed as a public class to make the initial
 * re-factoring work correctly, however it will be hidden again once the
 * new design is implemented. Basically, it cannot easily be hidden
 * without making several related changes, and those are going to be
 * done after the package layout is fixed.
 * 
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class TransactionalTaskThread extends TaskThread
{

    // the current transaction for this thread
    private Transaction currentTransaction;

    /**
     * Creates an instance of <code>TransactionalTaskThread</code>.
     */
    public TransactionalTaskThread() {
        currentTransaction = null;
    }

    /**
     * Returns the current transaction state, or null if there is none.
     *
     * @return the current <code>Transaction</code> or null
     */
    public Transaction getTransaction() {
        return currentTransaction;
    }

    /**
     * Sets the current transaction.
     *
     * @param transaction the new current transaction
     */
    public void setTransaction(Transaction transaction) {
        // FIXME: it's not clear yet that this should always be an error
        // case, so I'm just noting for now, but this is probably something
        // that should actually be a failure
        if (currentTransaction != null)
            System.err.println("Assigned a transaction to a thread that "+
                               "already has a current transaction");

        this.currentTransaction = transaction;
    }

    /**
     * Clears the transaction state. This is typically done once a given
     * transaction has ended.
     *
     * @param txn the transaction to clear, which must be the current one
     */
    public void clearTransaction(Transaction txn) {
        // FIXME: see comment in setTransaction
        if (currentTransaction.getId() != txn.getId())
            System.err.println("Tried to clear incorrect transaction");

        this.currentTransaction = null;
    }

}
