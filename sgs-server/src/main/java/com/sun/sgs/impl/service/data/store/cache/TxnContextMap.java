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

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.service.Transaction;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maintains the association between transactions and instances of {@link
 * TxnContext}, which manage per-transaction state.
 */
class TxnContextMap {

    /** The data store. */
    private final CachingDataStore store;

    /** The context for the current thread */
    private final ThreadLocal<TxnContext> currentContext =
	new ThreadLocal<TxnContext>();

    /** The number of active transactions. */
    private final TxnCount txnCount = new TxnCount();

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store
     */
    TxnContextMap(CachingDataStore store) {
	this.store = store;
    }
	
    /**
     * Joins the transaction and returns the associated context.
     *
     * @param	txn the transaction to join
     * @return	the associated context
     * @throws	IllegalStateException if there is a problem with the
     *		transaction or if the node is shut down
     */
    TxnContext join(Transaction txn) {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnContext context = getContext(txn);
	if (context == null) {
	    txnCount.increment();
	    boolean joined = false;
	    try {
		txn.join(store);
		joined = true;
	    } finally {
		if (!joined) {
		    txnCount.decrement();
		}
	    }
	    context = new TxnContext(txn, store);
	    currentContext.set(context);
	    return context;
	} else if (context.getPrepared()) {
	    throw new IllegalStateException(
		"Transaction has been prepared: " + txn);
	} else {
	    return context;
	}
    }

    /**
     * Prepares the transaction, and returns whether the use of the data store
     * in the transaction was read-only.
     *
     * @param	txn the transaction
     * @return	whether the transaction was read-only
     * @throws	IllegalStateException if there is a problem with the
     *		transaction
     */
    boolean prepare(Transaction txn) {
	if (getContextJoined(txn, true).prepare()) {
	    txnCount.decrement();
	    currentContext.set(null);
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * Prepares and commits the transaction.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if there is a problem with the
     *		transaction
     */
    void prepareAndCommit(Transaction txn) {
	try {
	    TxnContext context = getContextJoined(txn, true);
	    context.prepareAndCommit();
	} finally {
	    currentContext.set(null);
	    txnCount.decrement();
	}
    }

    /**
     * Commits the transaction.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if there is a problem with the
     *		transaction
     */
    void commit(Transaction txn) {
	try {
	    TxnContext context = getContextJoined(txn, false);
	    context.commit();
	} finally {
	    currentContext.set(null);
	    txnCount.decrement();
	}
    }

    /**
     * Aborts the transaction.
     *
     * @param	txn the transaction
     * @throws	IllegalStateException if there is a problem with the
     *		transaction
     */
    void abort(Transaction txn) {
	try {
	    getContextJoined(txn, false).abort();
	} finally {
	    currentContext.set(null);
	    txnCount.decrement();
	}
    }

    /**
     * Shuts down this instance after a node shutdown has been requested,
     * waiting for active transactions to complete.
     */
    void shutdown() {
	txnCount.awaitTxnShutdown();
	store.getUpdateQueue().shutdown();
    }

    /* -- Private methods and nested classes -- */

    /**
     * Returns the context for the specified transaction, or {@code null} if no
     * context is found.
     *
     * @param	txn the transaction
     * @return	the associated context or {@code null}
     * @throws	IllegalStateException if the current context is for a different
     *		transaction
     */
    private TxnContext getContext(Transaction txn) {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnContext context = currentContext.get();
	if (context == null) {
	    return null;
	} else if (context.txn.equals(txn)) {
	    return context;
	} else {
	    throw new IllegalStateException(
		"Wrong transaction: expected " + context.txn + ", found " +
		txn);
	}
    }

    /**
     * Returns the context for the specified transaction, which should be found
     * because it should have already been joined.  If {@code checkContinue} is
     * {@code true}, then also check that the node is not shutdown and that the
     * transaction has not timed out.
     *
     * @param	txn the transaction
     * @param	checkContinue whether to check for node shutdown and
     *		transaction timeout
     */
    private TxnContext getContextJoined(
	Transaction txn, boolean checkContinue)
    {
	if (checkContinue) {
	    if (store.getShutdownRequested()) {
		throw new IllegalStateException(
		    "CachingDataStore is shut down");
	    }
	    txn.checkTimeout();
	}
	TxnContext context = getContext(txn);
	if (context == null) {
	    throw new IllegalStateException(
		"Transaction is not active: " + txn);
	}
	return context;
    }

    /** Tracks the number of active transactions. */
    private class TxnCount {

	/** The number of active transactions. */
	private final AtomicInteger count = new AtomicInteger();

	/** Creates an instance of this class. */
	TxnCount() { }

	/**
	 * Increments the number of active transactions.
	 *
	 * @throws	IllegalStateException if the node is shut down
	 */
	void increment() {
	    count.incrementAndGet();
	    if (store.getShutdownRequested()) {
		int result = count.decrementAndGet();
		if (result == 0) {
		    synchronized (this) {
			notifyAll();
		    }
		}
		throw new IllegalStateException("Node is shut down");
	    }
	}
	
	/** Decrements the number of active transactions. */
	void decrement() {
	    int result = count.decrementAndGet();
	    assert result >= 0;
	    if (result == 0) {
		synchronized (this) {
		    notifyAll();
		}
	    }
	}

	/**
	 * Waits for the number of transactions to reach zero after a node
	 * shutdown has been requested.
	 */
	void awaitTxnShutdown() {
	    assert store.getShutdownRequested();
	    synchronized (this) {
		while (count.get() > 0) {
		    try {
			wait();
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}
    }
}
