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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for maintaining the association between transactions and
 * instances of {@link TransactionContext}.
 *
 * @param	<T> the type of transaction context
 * @see		TransactionContextFactory
 */
public class TransactionContextMap<T extends TransactionContext> {
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(TransactionContextMap.class.getName()));

   /** Provides transaction and other information for the current thread. */
    private final ThreadLocal<T> currentContext = new ThreadLocal<T>();

    /** The transaction proxy. */
    private final TransactionProxy txnProxy;

    /**
     * Constructs an instance of this class with the given {@code
     * TransactionProxy}.
     *
     * @param	txnProxy the transaction proxy
     */
    public TransactionContextMap(TransactionProxy txnProxy) {
	if (txnProxy ==  null) {
	    throw new NullPointerException("null txnProxy");
	}
	this.txnProxy = txnProxy;
    }

   /**
    * Makes sure the participant obtained from {@code contextFactory} is
    * joined to the current transaction and returns the associated
    * context.  If the participant has not yet joined the current
    * transaction, creates a new context by invoking {@link
    * TransactionContextFactory#createContext createContext} on {@code
    * contextFactory}, passing the current transaction, sets that
    * context as the current context for the current thread, and joins
    * the transaction.  Returns the context for the current transaction.
    *
    * @param	contextFactory the context factory
    * @return 	the context for the current transaction
    * @throws	TransactionNotActiveException if no transaction is active
    * @throws	IllegalStateException if there is a problem with the
    *		state of the transaction.
    */
    public T joinTransaction(TransactionContextFactory<T> contextFactory) {
	if (contextFactory == null) {
	    throw new NullPointerException("null contextFactory");
	}
        Transaction txn = txnProxy.getCurrentTransaction();
        if (txn == null) {
            throw new TransactionNotActiveException(
                "No transaction is active");
        }
        T context = currentContext.get();
        if (context == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "join txn:{0}", txn);
            }
            context = contextFactory.createContext(txn);
            currentContext.set(context);
            txn.join(contextFactory.getParticipant());
        } else if (!txn.equals(context.getTransaction())) {
            clearContext();
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.getTransaction() +
                ", found " + txn);
        }
        return context;
    }

    /** Removes the currently active transaction context. */
    public void clearContext() {
	currentContext.set(null);
    }

    /**
     * Returns the context for the current transaction.
     *
     * @return 	the context for the current transaction
     * @throws	TransactionNotActiveException if no transaction is active
     * @throws	IllegalStateException if there is a problem with the
     *		state of the transaction.
     */
    public T getContext() {
        Transaction txn = txnProxy.getCurrentTransaction();
        T context = currentContext.get();
        if (context == null) {
	    throw new IllegalStateException(
		"Not participating in transaction " + txn);
        } else if (!txn.equals(context.getTransaction())) {
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.getTransaction() +
                ", found " + txn);
        }
        return context;
    }

    /**
     * Checks that the specified {@code context} is currently active,
     * throwing {@code TransactionNotActiveException} if it isn't.
     *
     * @param	context a context
     *
     * @throws	TransactionNotActiveException if the specified
     * 		{@code context} is not currently active
     */
    public void checkContext(T context) {
	if (context == null) {
	    throw new NullPointerException("null context");
	}
	/* Make sure the current transaction is active */
	txnProxy.getCurrentTransaction();
	T threadContext = currentContext.get();
	if (threadContext == null) {
	    throw new TransactionNotActiveException(
		"Transaction " + context.getTransaction() +
		" is not the current transaction");
	} else if (context != threadContext) {
	    throw new TransactionNotActiveException(
                "Wrong transaction: Expected " +
		context.getTransaction() + ", found " +
		threadContext.getTransaction());
	}
    }

    /**
     * Checks the specified transaction, throwing {@code
     * IllegalStateException} if the current context is {@code null}
     * or if the specified transaction is not equal to the transaction
     * in the current context.
     *
     * @param	txn a transaction
     * @return	the current transaction context
     */
    public T checkTransaction(Transaction txn) {
        if (txn == null) {
            throw new NullPointerException("null transaction");
        }
        T context = currentContext.get();
        if (context == null) {
            throw new IllegalStateException("null context");
        }
        if (!txn.equals(context.getTransaction())) {
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.getTransaction() +
		", found " + txn);
        }
	return context;
    }
}
