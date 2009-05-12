/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.service.Transaction;


/**
 * This class is used to resolve the state associated with the current task's
 * context, including its owner and active transaction.
 */
final class ContextResolver {

    // the context of any current thread, needed only so that we can run
    // multiple stacks in the same VM
    private static ThreadLocal<KernelContext> context =
            new ThreadLocal<KernelContext>();

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
    private ContextResolver() { }

    /**
     * Returns the {@code ChannelManager} used in this context.
     *
     * @return the context's {@code ChannelManager}.
     *
     * @throws IllegalStateException if there is no available
     *                               {@code ChannelManager} in this
     *                               context
     */
    public static ChannelManager getChannelManager() {
        return context.get().getChannelManager();
    }

    /**
     * Returns the {@code DataManager} used in this context.
     *
     * @return the context's {@code DataManager}.
     *
     * @throws IllegalStateException if there is no available
     *                               {@code DataManager} in this
     *                               context
     */
    public static DataManager getDataManager() {
        return context.get().getDataManager();
    }

    /**
     * Returns the {@code TaskManager} used in this context.
     *
     * @return the context's {@code TaskManager}.
     *
     * @throws IllegalStateException if there is no available
     *                               {@code TaskManager} in this
     *                               context
     */
    public static TaskManager getTaskManager() {
        return context.get().getTaskManager();
    }

    /**
     * Returns the manager in this context that matches the given type.
     *
     * @param <T> the type of the manager
     * @param type the {@code Class} of the requested manager
     *
     * @return the matching manager
     *
     * @throws ManagerNotFoundException if no manager is found that matches
     *                                  the given type
     * @throws IllegalStateException if there are no available managers
     *                               in this context
     */
    public static <T> T getManager(Class<T> type) {
        return context.get().getManager(type);
    }

    /**
     * Package-private method used to set task state. This is called each
     * time a task is run through a {@code Scheduler}
     *
     * @param ctx the {@code KernelContext} for the current task
     * @param owner the {@code Identity} that owns the current task
     */
    static void setTaskState(KernelContext ctx, Identity owner) {
        context.set(ctx);
        currentOwner.set(owner);
    }

    /**
     * Package-private method used to get the current context.
     *
     * @return the current {@code AppKernelAppContext}
     */
    static KernelContext getContext() {
        return context.get();
    }

    /**
     * Returns the current owner of the work being done by this thread.
     *
     * @return the {@code Identity} that owns the current task
     */
    static Identity getCurrentOwner() {
        return currentOwner.get();
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

        if (txn == null) {
            throw new TransactionNotActiveException("no current transaction");
        }

        if (txn.isAborted()) {
            throw new TransactionNotActiveException("Transaction is aborted",
                                                    txn.getAbortCause());
        }

        txn.checkTimeout();

        return txn;
    }

    /**
     * Returns {@code true} if there is a current transaction, no matter
     * the state of that transaction. 
     * 
     * @return {@code true} if there is a current transaction
     */
    static boolean inTransaction() {
        Transaction txn = currentTransaction.get();
        return (txn != null);
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
        if (transaction == null) {
            throw new NullPointerException("null transactions not allowed");
        }

        Transaction txn = currentTransaction.get();
        if (txn != null) {
            throw new IllegalStateException("an active transaction is " +
                                            "currently running");
        }

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
        if (transaction == null) {
            throw new NullPointerException("transaction cannot be null");
        }

        Transaction txn = currentTransaction.get();
        if (txn == null) {
            throw new IllegalStateException("no active transaction to clear");
        }
        if (!txn.equals(transaction)) {
            throw new IllegalArgumentException("provided transaction is " +
                                               "not currently active");
        }

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
