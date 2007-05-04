/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.service.Transaction;

/**
 * An abstract implementation of {@code TransactionContext} that
 * provides implementations of the {@code getTransaction} and {@code
 * getParticipant} methods.
 */
public abstract class AbstractTransactionContext
    implements TransactionContext
{
    /** The participant. */
    protected final AbstractNonDurableTransactionParticipant participant;

    /** The transaction. */
    protected final Transaction txn;
    
    /**
     * Constructs a context with the specified participant and transaction.
     *
     * @param	participant a transaction participant
     * @param	txn a transaction
     */
    protected AbstractTransactionContext(
	AbstractNonDurableTransactionParticipant participant, Transaction txn)
    {
	assert txn != null && participant != null;
	this.participant = participant;
	this.txn = txn;
    }
    
    /**
     * {@inheritDoc}
     */
    public AbstractNonDurableTransactionParticipant getParticipant() {
	return participant;
    }
	
    /** {@inheritDoc} */
    public Transaction getTransaction() {
	return txn;
    }
}
