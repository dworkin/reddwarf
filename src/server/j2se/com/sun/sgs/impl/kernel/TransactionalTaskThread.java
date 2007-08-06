/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.service.Transaction;


/**
 * This package-private class is the concrete version of <code>Thread</code>
 * used by the system. It is a <code>TaskThread</code> that can manage an
 * active <code>Transaction</code>. This implementation is package-private
 * so that only select consumers can actually access or change the
 * current transaction. Most users interact with the current transaction
 * through <code>TransactionProxy</code>.
 */
final class TransactionalTaskThread extends TaskThread {

    // the current transaction
    private Transaction currentTransaction;

    /**
     * Creates an instance of <code>TransactionalTaskThread</code>.
     *
     * @param r the root <code>Runnable</code> for this <code>Thread</code>
     */
    TransactionalTaskThread(Runnable r) {
        super(r);
        currentTransaction = null;
    }

    /**
     * Returns the current <code>Transaction</code>, throwing an exception
     * if there is no currently active transaction or if the currently
     * active transaction has timed out.
     *
     * @return the currently active <code>Transaction</code>
     *
     * @throws TransactionNotActiveException if there is no currently active
     *                                       transaction
     * @throws TransactionTimeoutException if the transaction has timed out
     */
    Transaction getCurrentTransaction() {
        if (currentTransaction == null)
            throw new TransactionNotActiveException("no current transaction");

	currentTransaction.checkTimeout();

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
        if (Thread.currentThread() != this)
            throw new IllegalStateException("Cannot set the transaction of " +
                                            "a thread other than the " +
                                            "current thread");

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
