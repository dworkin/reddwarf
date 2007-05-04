/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.service.Transaction;

/**
 * A context to hold state associated with a transaction.  A {@code
 * TransactionContext} is created by the {@link
 * AbstractNonDurableTransactionParticipant#newContext} method.
 */
public interface TransactionContext {

    /** The state of a transaction in a context. */
    static enum State {
	/** Active transaction. */
	ACTIVE,
	/** Prepared transaction. */
	PREPARED,
	/** Committed transaction. */
	COMMITTED,
	/** Aborted transaction. */
	ABORTED };
	
    /**
     * Prepares state for the transaction associated with this
     * context, and returns {@code true} if this context's state
     * is read-only, otherwise returns {@code false}.
     *
     * @return	{@code true} if this context's state is read-only,
     * 		{@code false} otherwise
     * @throws	Exception if there is a problem preparing this
     *		context's state
     */ 
    boolean prepare() throws Exception;

    /**
     * Aborts this context's involvement with the transaction
     * associated with this context.
     *
     * @param 	retryable if {@code true}, the transaction's abort cause
     *		is a retryable exception
     */
    void abort(boolean retryable);

    /**
     * Commits this context's state.
     */
    void commit();

    /**
     * Returns the participant associated with this context.
     *
     * @return	the participant associated with this context
     */
    AbstractNonDurableTransactionParticipant getParticipant();

    /**
     * Returns the transaction associated with this context.
     *
     * @return	the transaction associated with this context
     */
    Transaction getTransaction();
}
