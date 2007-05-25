/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

/** 
 * A convenience utility for performing an action on abort to clean up
 * transient state.  Joins a non-durable transaction participant to the current
 * transaction that calls the onAbort method if the transaction aborts.
 */
public abstract class OnAbort {

    /**
     * Creates an instance of this class.
     *
     * @param	txnProxy used to obtain the current transaction
     * @throws	TransactionNotActiveException if there is no currently active
     *		transaction 
     * @throws	IllegalStateException if there is a problem with the
     *		state of the transaction
     */
    public OnAbort(TransactionProxy txnProxy) {
	new TransactionContextFactory<TransactionContext>(txnProxy) {
	    protected TransactionContext createContext(Transaction txn) {
		return new TransactionContext(txn) {
		    public void abort(boolean retryable) {
			onAbort(retryable);
		    }
		    public void commit() {
			isCommitted = true;
		    }
		};
	    }
	}.joinTransaction();
    }

    /**
     * Called if the current transaction is aborted.
     *
     * @param 	retryable whether the transaction's abort cause was a
     *		retryable exception
     */
    abstract protected void onAbort(boolean retryable);
}
