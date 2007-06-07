/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.MaybeRetryableTransactionNotActiveException;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* Provides a simple implementation of Transaction, for testing.
*
* If using this class with DummyTransactionProxy, make sure to call
* DummyTransactionProxy.setCurrentTransaction with the DummyTransaction, so
* that the proxy's transaction is cleared when the transaction is terminated.
*/
public class DummyTransaction implements Transaction {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(DummyTransaction.class.getName()));

    /** The default timeout. */
    private static long DEFAULT_TIMEOUT =
	Long.getLong(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, 1000);

    /** The possible transaction states. */
    public enum State {
	ACTIVE, PREPARING, PREPARED, COMMITTING, COMMITTED, ABORTING, ABORTED
    }

    /** Whether to use prepareAndCommit. */
    public enum UsePrepareAndCommit { YES, NO, ARBITRARY }

    /** The ID for the next transaction. */
    private static AtomicLong nextId = new AtomicLong(1);

    /**
     * Whether commit on this transaction should use prepareAndCommit instead
     * of prepare followed by commit.
     */
    private final boolean usePrepareAndCommit;

    /** The ID for this transaction. */
    private final long id = nextId.getAndIncrement();

    /** The creation time of this transaction. */
    private final long creationTime = System.currentTimeMillis();

    /** The length of time this transaction is allowed to run. */
    private final long timeout;

    /** The state of this transaction. */
    private State state = State.ACTIVE;

    /**
     * The exception that caused the transaction to be aborted, or null if no
     * cause was provided or if no abort occurred.
     */
    private Throwable abortCause = null;

    /** The transaction proxy associated with this transaction, if any. */
    DummyTransactionProxy proxy;

    /** The transaction participants for this transaction. */
    public final Set<TransactionParticipant> participants =
	new HashSet<TransactionParticipant>();

    /** Creates an instance of this class that always uses prepareAndCommit. */
    public DummyTransaction() {
	this(UsePrepareAndCommit.YES, DEFAULT_TIMEOUT);
    }

    /**
     * Creates an instance of this class that always uses prepareAndCommit and
     * uses the specified timeout.
     */
    public DummyTransaction(long timeout) {
	this(UsePrepareAndCommit.YES, timeout);
    }

    /**
     * Creates an instance of this class which uses prepareAndCommit based on
     * the argument.
     */
    public DummyTransaction(UsePrepareAndCommit usePrepareAndCommit) {
	this(usePrepareAndCommit, DEFAULT_TIMEOUT);
    }

    /**
     * Creates an instance of this class that uses prepareAndCommit based on
     * the argument and uses the specified timeout.
     */
    public DummyTransaction(UsePrepareAndCommit usePrepareAndCommit,
			    long timeout)
    {
	switch (usePrepareAndCommit) {
	case YES:
	    this.usePrepareAndCommit = true;
	    break;
	case NO:
	    this.usePrepareAndCommit = false;
	    break;
	case ARBITRARY:
	    this.usePrepareAndCommit = (id % 2 == 0);
	    break;
	default:
	    throw new AssertionError();
	}
	this.timeout = timeout;
	logger.log(Level.FINER, "create {0}", this);
        DummyProfileCoordinator.startTask();
    }

    /* -- Implement Transaction -- */

    public byte[] getId() {
	return new byte[] {
	    (byte) (id >>> 56), (byte) (id >>> 48), (byte) (id >>> 40),
	    (byte) (id >>> 32), (byte) (id >>> 24), (byte) (id >>> 16),
	    (byte) (id >>> 8), (byte) id };
    }

    public long getCreationTime() { return creationTime; }

    public long getTimeout() { return timeout; }

    public void checkTimeout() {
	long runningTime = System.currentTimeMillis() - creationTime;
	if (runningTime > timeout) {
	    throw new TransactionTimeoutException(
		"Transaction timed out after " + runningTime + " ms");
	}
    }

    public synchronized void join(TransactionParticipant participant) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINER, "join {0} participant:{1}", this, participant);
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	} else if (state == State.ABORTED) {
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction not active: " + state);
	}
	participants.add(participant);
    }

    public synchronized void abort(Throwable cause) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "abort {0} cause:{1}", this, cause);
	}
	if (state == State.ABORTING) {
	    return;
	} else if (state == State.ABORTED) {
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction is not active", abortCause);
	} else if (state != State.ACTIVE &&
		   state != State.PREPARING &&
		   state != State.PREPARED)
	{
	    throw new IllegalStateException(
		"Transaction is not active: " + state);
	}
	state = State.ABORTING;
	abortCause = cause;
	if (proxy != null) {
	    proxy.setCurrentTransaction(null);
	    proxy = null;
	}
	for (TransactionParticipant participant : participants) {
	    try {
		participant.abort(this);
	    } catch (RuntimeException e) {
		logger.logThrow(Level.WARNING, e, "Abort failed");
	    }
	}
	state = State.ABORTED;
        DummyProfileCoordinator.endTask(false);
    }

    public synchronized boolean isAborted() {
	return state == State.ABORTED || state == State.ABORTING;
    }

    public synchronized Throwable getAbortCause() {
	return abortCause;
    }

    /* -- Other methods -- */

    public synchronized boolean prepare() throws Exception {
	logger.log(Level.FINER, "prepare {0}", this);
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	state = State.PREPARING;
	if (proxy != null) {
	    proxy.setCurrentTransaction(null);
	    proxy = null;
	}
	boolean result = true;
	for (Iterator<TransactionParticipant> iter = participants.iterator();
	    iter.hasNext(); )
	{
	    TransactionParticipant participant = iter.next();
	    boolean readOnly;
	    try {
		readOnly = participant.prepare(this);
	    } catch (Exception e) {
		if (state != State.ABORTED) {
		    abort(e);
		}
		throw e;
	    }
	    if (readOnly) {
		iter.remove();
	    } else {
		result = false;
	    }
	}
	state = State.PREPARED;
	return result;
    }

    public synchronized void commit() throws Exception {
	logger.log(Level.FINER, "commit {0}", this);
	if (state == State.PREPARED) {
	    state = State.COMMITTING;
	    for (TransactionParticipant participant : participants) {
		try {
		    participant.commit(this);
		} catch (RuntimeException e) {
		    logger.logThrow(Level.WARNING, e, "Commit failed");
		}
	    }
	} else if (state == State.ABORTED) {
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction not active: " + state);
	} else if (usePrepareAndCommit && participants.size() == 1) {
	    state = State.PREPARING;
	    if (proxy != null) {
		proxy.setCurrentTransaction(null);
		proxy = null;
	    }
	    TransactionParticipant participant =
		participants.iterator().next();
	    try {
		participant.prepareAndCommit(this);
	    } catch (Exception e) {
		if (state != State.ABORTED) {
		    abort(e);
		}
		throw e;
	    }
	} else {
	    prepare();
	    state = State.COMMITTING;
	    for (TransactionParticipant participant : participants) {
		try {
		    participant.commit(this);
		} catch (RuntimeException e) {
		    logger.logThrow(Level.WARNING, e, "Commit failed");
		}
	    }
	}
	state = State.COMMITTED;
        DummyProfileCoordinator.endTask(true);
    }

    /** Returns the current state. */
    public synchronized State getState() {
	return state;
    }

    public String toString() {
	return "DummyTransaction[tid:" + id + "]";
    }
}
