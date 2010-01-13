/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
    
    /** The transaction context map. */
    private final TransactionContextMap<T> contextMap;

    /** The type name of the participants, used for profiling. */
    private final String participantName;
    
    /** Lock for access to the participant field. */
    private final Object lock = new Object();

    /** The transaction participant. */
    private TransactionParticipant participant;

    /**
     * Constructs an instance of this class with the given {@code
     * TransactionContextMap}.
     *
     * @param	contextMap the transaction context map
     * @param   participantName  the type name of the transaction participants
     */
    protected TransactionContextFactory(TransactionContextMap<T> contextMap,
                                        String participantName) 
    {
	if (contextMap == null) {
	    throw new NullPointerException("null contextMap");
	}
	this.contextMap = contextMap;
        this.participantName = participantName;
    }

    /**
     * Constructs an instance of this class, using the specified {@code
     * TransactionProxy} to create the {@link TransactionContextMap}.  Use this
     * constructor if access to the {@code TransactionContextMap} is not
     * needed.
     *
     * @param	txnProxy the transaction proxy
     * @param   participantName  the type name of the transaction participants
     */
    protected TransactionContextFactory(TransactionProxy txnProxy,
                                        String participantName) 
    {
	contextMap = new TransactionContextMap<T>(txnProxy);
        this.participantName = participantName;
    }

   /**
    * Makes sure the participant is joined to the current transaction and
    * returns the associated context. <p>
    *
    * The default implementation calls {@link
    * TransactionContextMap#joinTransaction joinTransaction} on the {@link
    * TransactionContextMap} supplied to, or created by, the constructor,
    * passing this instance as the argument.
    *
    * @return 	the context for the current transaction
    * @throws	TransactionNotActiveException if no transaction is active
    * @throws	IllegalStateException if there is a problem with the
    *		state of the transaction.
    */
    public T joinTransaction() {
	return contextMap.joinTransaction(this);
    }

    /**
     * Returns the transaction participant for use with this factory. <p>
     *
     * The default implementation creates a new transaction participant by
     * calling {@link #createParticipant createParticipant} the first time the
     * participant is requested.
     *
     * @return	the transaction participant
     */
    public TransactionParticipant getParticipant() {
	synchronized (lock) {
	    if (participant == null) {
		participant = createParticipant();
	    }
	    return participant;
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
    
    /* -- Implement TransactionParticipant -- */

    /** Provides a durable transaction participant. */
    protected class Participant implements TransactionParticipant {

	/** Creates an instance of this class. */
	public Participant() { }

	/** {@inheritDoc} */
	public boolean prepare(Transaction txn) throws Exception {
	    try {
		T context = contextMap.checkTransaction(txn);
		if (context.isPrepared()) {
		    throw new TransactionNotActiveException("Already prepared");
		}
		boolean readOnly = context.prepare();
		if (readOnly) {
		    contextMap.clearContext();
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
		T context = contextMap.checkTransaction(txn);
		try {
		    if (!context.isPrepared()) {
			RuntimeException e = 
			    new IllegalStateException(
				"transaction not prepared");
			if (logger.isLoggable(Level.WARNING)) {
			    logger.logThrow(
				Level.WARNING, e,
				"commit: not yet prepared txn:{0}",
				txn);
			}
			throw e;
		    }
		} finally {
		    contextMap.clearContext();
		}
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
		T context = contextMap.checkTransaction(txn);
		if (context.isPrepared()) {
		    throw new TransactionNotActiveException("Already prepared");
		}
		context.prepareAndCommit();
		contextMap.clearContext();
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
		T context = contextMap.checkTransaction(txn);
		contextMap.clearContext();
		context.abort(isRetryable(txn.getAbortCause()));
		logger.log(Level.FINER, "abort txn:{0} returns", txn);

	    } catch (RuntimeException e) {
		logger.logThrow(Level.WARNING, e, "abort txn:{0} throws", txn);
		throw e;
	    }
	}
        
        /** {@inheritDoc} */
        public String getTypeName() {
            return participantName;
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
