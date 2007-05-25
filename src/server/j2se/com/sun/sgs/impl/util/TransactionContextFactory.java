/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for participating in a transaction, parameterized by
 * a type of {@link TransactionContext} to be used with a participant.
 *
 * @param	<T> a type of transaction context
 */
public abstract class TransactionContextFactory<T extends TransactionContext> {
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(
		TransactionContextFactory.class.getName()));
    
    /** Provides transaction and other information for the current thread. */
    private final ThreadLocal<T> currentContext = new ThreadLocal<T>();

    /** The transaction proxy. */
    private final TransactionProxy txnProxy;

    /** Lock for access to the participant field. */
    private final Object lock = new Object();

    /** The transaction participant. */
    private TransactionParticipant participant;

    /**
     * Constructs an instance of this class with the given {@code
     * txnProxy}.
     *
     * @param	txnProxy the transaction proxy
     */
    protected TransactionContextFactory(TransactionProxy txnProxy) {
	if (txnProxy ==  null) {
	    throw new NullPointerException("null txnProxy");
	}
	this.txnProxy = txnProxy;
    }

   /**
     * If this participant has not yet joined the current transaction,
     * creates a new context by invoking {@link #createContext
     * createContext} passing the current transaction, sets that context
     * as the current context for the current thread, and joins the
     * transaction.  Otherwise, if this participant has already joined
     * the current transaction, returns the current transaction context.
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
            context = createContext(txn);
            currentContext.set(context);
	    synchronized (lock) {
		if (participant == null) {
		    participant = createParticipant();
		}
	    }
            txn.join(participant);
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
		threadContext.getTransaction() + ", found " +
		context.getTransaction());
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
    protected abstract T createContext(Transaction txn);

    /**
     * Returns a new transaction participant that operates on the
     * currently active transaction context. <p>
     *
     * The default implementation of this method returns a transaction
     * participant that implements {@link
     * NonDurableTransactionParticipant}.
     *
     * @return	a transaction participant
     */
    protected TransactionParticipant createParticipant() {
	return new NonDurableParticipant();
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
    protected T checkTransaction(Transaction txn) {
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

    /* -- Implement TransactionParticipant -- */

    /** Provides a durable transaction participant. */
    protected class Participant implements TransactionParticipant {

	/** Creates an instance of this class. */
	public Participant() { }

	/** {@inheritDoc} */
	public boolean prepare(Transaction txn) throws Exception {
	    try {
		T context = checkTransaction(txn);
		if (context.isPrepared()) {
		    throw new TransactionNotActiveException("Already prepared");
		}
		boolean readOnly = context.prepare();
		if (readOnly) {
		    currentContext.set(null);
		}
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			       txn, readOnly);
		}
            
		return readOnly;
	    
	    } catch (Exception e) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(Level.FINER, e,
				    "prepare txn:{0} throws", txn);
		}
		throw e;
	    }
	}

	/** {@inheritDoc} */
	public void commit(Transaction txn) {
	    try {
		T context = checkTransaction(txn);
		if (! context.isPrepared()) {
		    RuntimeException e = 
			new IllegalStateException("transaction not prepared");
		    if (logger.isLoggable(Level.WARNING)) {
			logger.logThrow(
			    Level.WARNING, e,
			    "commit: not yet prepared txn:{0}",
			    txn);
		    }
		    throw e;
		}
		currentContext.set(null);
		context.commit();
		logger.log(Level.FINER, "commit txn:{0} returns", txn);

	    } catch (RuntimeException e) {
		logger.logThrow(
		    Level.WARNING, e, "commit txn:{0} throws", txn);
		throw e;
	    }
	}

	/** {@inheritDoc} */
	public void prepareAndCommit(Transaction txn) throws Exception {
	    try {
		T context = checkTransaction(txn);
		if (context.isPrepared()) {
		    throw new TransactionNotActiveException("Already prepared");
		}
		context.prepareAndCommit();
		currentContext.set(null);
		logger.log(Level.FINER, "prepareAndCommit txn:{0} returns",
			   txn);
	    
	    } catch (Exception e) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(Level.FINER, e,
				    "prepareAndCommit txn:{0} throws", txn);
		}
		throw e;
	    }
	}

	/** {@inheritDoc} */
	public void abort(Transaction txn) {
	    try {
		T context = checkTransaction(txn);
		currentContext.set(null);
		context.abort(isRetryable(txn.getAbortCause()));
		logger.log(Level.FINER, "abort txn:{0} returns", txn);

	    } catch (RuntimeException e) {
		logger.logThrow(Level.WARNING, e, "abort txn:{0} throws", txn);
		throw e;
	    }
	}
    }

    /** Provides a non-durable transaction participant. */
    protected class NonDurableParticipant extends Participant
	implements NonDurableTransactionParticipant
    {
	/** Creates an instance of this class. */
	public NonDurableParticipant() { }
    }

    /* -- Other methods -- */

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
    private static boolean isRetryable(Throwable t) {
	return
	    t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }
}
