/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for participating in a transaction, parameterized by
 * a type of {@link TransactionContext} to be used with a participant.
 *
 * @param	<T> a type of transaction context
 */
public abstract class
    AbstractNonDurableTransactionParticipant <T extends TransactionContext>
    implements NonDurableTransactionParticipant
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(
		AbstractNonDurableTransactionParticipant.class.getName()));
    
    /** Provides transaction and other information for the current thread. */
    private final ThreadLocal<T> currentContext = new ThreadLocal<T>();

    /** The transaction proxy. */
    private final TransactionProxy txnProxy;

    /**
     * Constructs an instance of this class with the given {@code
     * txnProxy}.
     *
     * @param	txnProxy the transaction proxy
     */
    protected AbstractNonDurableTransactionParticipant(
	TransactionProxy txnProxy)
    {
	if (txnProxy ==  null) {
	    throw new NullPointerException("null txnProxy");
	}
	this.txnProxy = txnProxy;
    }

   /**
     * If this participant has not yet joined the current transaction,
     * joins the transaction and creates a new context by invoking
     * {@link #newContext newContext} passing the current transaction,
     * and sets that context as the current context for the current
     * thread.  Otherwise, if this participant has already joined the
     * current transaction, returns the current transaction context.
     *
     * @return 	the context for the current transaction
     * @throws	TransactionNotActiveException if no transaction is active
     * @throws	IllegalStateException if there is a problem with the
     *		state of the transaction.
     */
    public T joinTransaction() {
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
            txn.join(this);
            context = newContext(txn);
            currentContext.set(context);
        } else if (!txn.equals(context.getTransaction())) {
            currentContext.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.getTransaction() +
                ", found " + txn);
        }
        return context;
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
        if (txn == null) {
            throw new TransactionNotActiveException(
                "No transaction is active");
        }
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
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
 		"No transaction is active");
	}
    }

    /**
     * Returns a new {@code TransactionContext} to hold state for the
     * specified transaction, {@code txn}.
     *
     * @param	txn a transaction
     *
     * @return	a new transaction context
     */
    protected abstract T newContext(Transaction txn);
    
    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
        try {
	    checkTransaction(txn);
            boolean readOnly = currentContext.get().prepare();
	    if (readOnly) {
		currentContext.set(null);
	    }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINER, "prepare txn:{0} returns {1}",
                           txn, readOnly);
            }
            
            return readOnly;
	    
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
        try {
	    checkTransaction(txn);
	    currentContext.get().commit();
	    currentContext.set(null);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "commit txn:{0} returns", txn);
            }
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "commit txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
        if (!prepare(txn)) {
            commit(txn);
        }
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
        try {
	    checkTransaction(txn);
	    currentContext.get().abort(isRetryable(txn.getAbortCause()));
	    currentContext.set(null);
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "abort txn:{0} returns", txn);
            }
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.logThrow(Level.FINER, e, "abort txn:{0} throws", txn);
            }
            throw e;
        }
    }

    /**
     * Returns {@code true} if the given {@code Throwable} is a
     * "retryable" exception, meaning that it implements {@code
     * ExceptionRetryStatus}, and invoking its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * {@code true}.
     *
     * @param	t   a throwable
     *
     * @return	{@code true} if the given {@code Throwable} is
     *		retryable, and {@code false} otherwise
     */
    public static boolean isRetryable(Throwable t) {
	return
	    t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }

    /* -- Other methods -- */

    /**
     * Checks the specified transaction, throwing {@code
     * IllegalStateException} if the current context is {@code null}
     * or if the specified transaction is not equal to the transaction
     * in the current context.  If the specified transaction does not
     * match the current context's transaction, then sets the current
     * context to {@code null}.
     *
     * @param	txn a transaction
     */
    private void checkTransaction(Transaction txn) {
        if (txn == null) {
            throw new NullPointerException("null transaction");
        }
        T context = currentContext.get();
        if (context == null) {
            throw new IllegalStateException("null context");
        }
        if (!txn.equals(context.getTransaction())) {
            currentContext.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.getTransaction() +
		", found " + txn);
        }
    }
}
