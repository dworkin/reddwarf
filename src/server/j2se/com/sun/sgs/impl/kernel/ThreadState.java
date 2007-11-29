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

import com.sun.sgs.auth.Identity;

import com.sun.sgs.service.Transaction;


/**
 * Package-private helper class that contains only static methods and is
 * used to maintain per-thread state for transactions and task ownership.
 */
final class ThreadState {

    // the current owner of any given thread
    private static ThreadLocal<Identity> currentOwner = 
        new ThreadLocal<Identity>();

    // the current transaction for any given thread
    private static ThreadLocal<Transaction> currentTransaction =
        new ThreadLocal<Transaction>();

    /**
     * Private constructor used to ensure that no one constructs an instance
     * of this class.
     */
    private ThreadState() { }

    /**
     * Returns the current owner of the work being done by this thread.
     * Depending on what is currently running in this thread, the owner may
     * be the owner of the last <code>Runnable</code> provided to
     * <code>runTask</code>, or the owner of a specific
     * <code>KernelRunnable</code> running in this thread (typically
     * consumed from the <code>TaskScheduler</code>).
     *
     * @return the current owner
     */
    static Identity getCurrentOwner() {
        return currentOwner.get();
    }

    /**
     * Sets the current owner of the work being done by this thread. The only
     * components who have access to this ability are those in the kernel and
     * the <code>TaskScheduler</code> (via the <code>TaskHandler</code>).
     *
     * @param newOwner the new owner
     */
    static void setCurrentOwner(Identity newOwner) {
        currentOwner.set(newOwner);
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
    static Transaction getCurrentTransaction() {
        Transaction txn = currentTransaction.get();

        if (txn == null)
            throw new TransactionNotActiveException("no current transaction");

        txn.checkTimeout();

        return txn;
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
    static void setCurrentTransaction(Transaction transaction) {
        if (transaction == null)
            throw new NullPointerException("null transactions not allowed");

        Transaction txn = currentTransaction.get();
        if (txn != null)
            throw new IllegalStateException("an active transaction is " +
                                            "currently running");

        currentTransaction.set(transaction);
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
    static void clearCurrentTransaction(Transaction transaction) {
        if (transaction == null)
            throw new NullPointerException("transaction cannot be null");

        Transaction txn = currentTransaction.get();
        if (txn == null)
            throw new IllegalStateException("no active transaction to clear");
        if (! txn.equals(transaction))
            throw new IllegalArgumentException("provided transaction is " +
                                               "not currently active");

        currentTransaction.set(null);
    }

    /**
     * Returns whether there is a currently active transaction.
     *
     * @return <code>true</code> if there is currently an active transaction,
     *         <code>false</code> otherwise
     */
    static boolean isCurrentTransaction() {
        return (currentTransaction.get() != null);
    }

}
