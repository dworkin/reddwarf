/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.service.Transaction;

/**
 * A context to hold state associated with a transaction.  A {@code
 * TransactionContext} is created by the method {@link
 * TransactionContextFactory#createContext}.
 */
public abstract class TransactionContext {

    /** The transaction. */
    protected final Transaction txn;

    /** {@code true} if the transaction is prepared. */
    protected boolean isPrepared = false;

    /** {@code true} if the transaction is committed. */
    protected boolean isCommitted = false;

    /**
     * Constructs an instance of this class with the given
     * transaction.
     *
     * @param	txn a transaction
     */
    public TransactionContext(Transaction txn) {
	this.txn = txn;
    }
    
    /**
     * Prepares state for the transaction associated with this
     * context, sets the {@code isPrepared} flag to {@code true}, and
     * returns {@code true} if this context's state is read-only and
     * returns {@code false} otherwise. <p>
     *
     * The default implementation of this method sets the {@code
     * isPrepared} flag to {@code true} and returns {@code false}.
     *
     * @return	{@code true} if this context's state is read-only,
     * 		{@code false} otherwise
     * @throws	Exception if there is a problem preparing this
     *		context's state
     */ 
    public boolean prepare() throws Exception {
	isPrepared = true;
	return false;
    }

    /**
     * Invokes {@link #prepare prepare} to prepare state for the
     * transaction associated with this context, and if {@code
     * prepare} returns {@code false}, then invokes {@link #commit
     * commit} to commit this context's state.
     *
     * @throws	Exception if there is a problem preparing this
     *		context's state
     */
    public void prepareAndCommit() throws Exception {
        if (!prepare()) {
            commit();
        }
    }

    /**
     * Aborts this context's involvement with the transaction
     * associated with this context.
     *
     * @param 	retryable if {@code true}, the transaction's abort cause
     *		is a retryable exception
     */
    public abstract void abort(boolean retryable);

    /**
     * Commits this context's state.  The implementation of this
     * method should set the {@code isCommitted} flag to {@code true}.
     */
    public abstract void commit();

    /**
     * Returns the transaction associated with this context.
     *
     * @return	the transaction associated with this context
     */
    public Transaction getTransaction() {
	return txn;
    }

    /**
     * Returns {@code true} if this context is prepared, otherwise
     * returns {@code false}.
     *
     * @return	{@code true} if this context is prepared, otherwise
     *		returns {@code false}
     */
    public boolean isPrepared() {
	return isPrepared;
    }
}
