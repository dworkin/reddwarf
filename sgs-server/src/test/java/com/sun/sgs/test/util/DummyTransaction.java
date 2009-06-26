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

package com.sun.sgs.test.util;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionListener;
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
	Long.getLong(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, 100);

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
    private final long creationTime;

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

    /** The registered {@code TransactionListener}s. */
    private final Set<TransactionListener> listeners =
	new HashSet<TransactionListener>();

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
        this.creationTime = System.currentTimeMillis();
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
	if (state == State.ABORTED ||
	    state == State.COMMITTED)
	{
	    throw new TransactionNotActiveException(
		"Transaction is not active: " + state);
	} else if (state == State.ABORTING ||
		   state == State.COMMITTING)
	{
	    return;
	}
	long runningTime = System.currentTimeMillis() - creationTime;
	if (runningTime > timeout) {
	    TransactionTimeoutException exception =
		new TransactionTimeoutException(
		    "Transaction timed out after " + runningTime + " ms");
	    abort(exception);
	    throw exception;
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
	    throw new TransactionNotActiveException(
		"Transaction not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction not active: " + state);
	}
	participants.add(participant);
    }

    public synchronized void abort(Throwable cause) {
	if (cause == null)
	    throw new NullPointerException("Cause cannot be null");
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "abort {0} cause:{1}", this, cause);
	}
	if (state == State.ABORTING) {
	    return;
	} else if (state == State.ABORTED) {
	    throw new TransactionNotActiveException(
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
	notifyListenersAfter(false);
        DummyProfileCoordinator.endTask(false);
    }

    public synchronized boolean isAborted() {
	return state == State.ABORTED || state == State.ABORTING;
    }

    public synchronized Throwable getAbortCause() {
	return abortCause;
    }

    /** {@inheritDoc} */
    public void registerListener(TransactionListener listener) {
	if (listener == null) {
	    throw new NullPointerException("The listener must not be null");
	} else if (state != State.ACTIVE) {
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}
	listeners.add(listener);
    }

    /* -- Other methods -- */

    public synchronized boolean prepare() throws Exception {
	logger.log(Level.FINER, "prepare {0}", this);
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	notifyListenersBefore();
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
	    throw new TransactionNotActiveException(
		"Transaction not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction not active: " + state);
	} else if (usePrepareAndCommit && participants.size() == 1) {
	    notifyListenersBefore();
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
	notifyListenersAfter(true);
        DummyProfileCoordinator.endTask(true);
    }

    /** Returns the current state. */
    public synchronized State getState() {
	return state;
    }

    public String toString() {
	return "DummyTransaction[tid:" + id + "]";
    }

    /** Notify any listeners before preparing the transaction. */
    private void notifyListenersBefore() {
	/*
	 * Copy the listeners to avoid problems if a beforeCompletion method
	 * registers another listener.
	 */
	for (TransactionListener listener :
		 listeners.toArray(new TransactionListener[listeners.size()]))
	{
	    try {
		listener.beforeCompletion();
	    } catch (RuntimeException e) {
		if (state != State.ABORTED) {
		    abort(e);
		}
		throw e;
	    }
	}
    }

    /** Notify any listeners after completing the transaction. */
    private void notifyListenersAfter(boolean commited) {
	for (TransactionListener listener : listeners) {
	    try {
		listener.afterCompletion(commited);
	    } catch (RuntimeException e) {
		logger.logThrow(Level.WARNING, e,
				"TransactionListener.afterCompletion failed");
	    }
	}
    }
}
