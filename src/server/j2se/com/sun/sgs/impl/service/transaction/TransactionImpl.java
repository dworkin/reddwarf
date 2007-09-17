/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.MaybeRetryableTransactionAbortedException;
import com.sun.sgs.impl.util.MaybeRetryableTransactionNotActiveException;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides an implementation of Transaction. */
final class TransactionImpl implements Transaction {

    /** Logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(TransactionImpl.class.getName()));

    /** The possible states of a transaction. */
    private static enum State {
	/** In progress */
	ACTIVE,
	/** Begun preparation */
	PREPARING,
	/** Begun aborting */
	ABORTING,
	/** Completed aborting */
	ABORTED,
	/** Begun committing */
	COMMITTING,
	/** Completed committing */
	COMMITTED
    }

    /** The transaction ID. */
    private final long tid;

    /** The time the transaction was created. */
    private final long creationTime;

    /** The length of time that this transaction is allowed to run.*/
    private final long timeout;

    /** The thread associated with this transaction. */
    private final Thread owner;

    /** The state of the transaction. */
    private State state;

    /**
     * The transaction participants.  If there is a durable participant, it
     * will be listed last.  Participants whose prepare method returns true
     * (read-only) are removed from this list.
     */
    private final List<TransactionParticipant> participants =
	new ArrayList<TransactionParticipant>();

    /** Whether this transaction has a durable participant. */
    private boolean hasDurableParticipant = false;

    /**
     * The exception that caused the transaction to be aborted, or null if no
     * cause was provided or if no abort occurred.
     */
    private Throwable abortCause = null;

    /** The optional collector used to report participant detail. */
    private final ProfileCollector collector;

    /** Collected profiling data on each participant. */
    private final HashMap<String,ProfileParticipantDetailImpl> detailMap;

    /**
     * Creates an instance with the specified transaction ID, timeout, and
     * collector.
     */
    TransactionImpl(long tid, long timeout, ProfileCollector collector) {
	this.tid = tid;
	this.timeout = timeout;
	this.collector = collector;
	creationTime = System.currentTimeMillis();
	owner = Thread.currentThread();
	state = State.ACTIVE;
	if (collector != null) {
	    detailMap = new HashMap<String,ProfileParticipantDetailImpl>();
	} else {
	    detailMap = null;
	}
	logger.log(Level.FINER, "create {0}", this);
    }

    /* -- Implement Transaction -- */

    /** {@inheritDoc} */
    public byte[] getId() {
	return longToBytes(tid);
    }

    /** {@inheritDoc} */
    public long getCreationTime() {
	return creationTime;
    }

    /** {@inheritDoc} */
    public long getTimeout() {
	return timeout;
    }

    /** {@inheritDoc} */
    public void checkTimeout() {
	assert Thread.currentThread() == owner : "Wrong thread";
	logger.log(Level.FINEST, "checkTimeout {0}", this);
	switch (state) {
	case ABORTED:
	case COMMITTED:
	    throw new TransactionNotActiveException(
		"Transaction is not active: " + state);
	case ABORTING:
	case COMMITTING:
	    return;
	case ACTIVE:
	case PREPARING:
	    break;
	default:
	    throw new AssertionError();
	}
	long runningTime = System.currentTimeMillis() - getCreationTime();
	if (runningTime > getTimeout()) {
	    TransactionTimeoutException exception =
		new TransactionTimeoutException(
		    "transaction timed out: " + runningTime + " ms");
	    if (state != State.ABORTING) {
		abort(exception);
	    }
	    throw exception;
	}
    }

    /** {@inheritDoc} */
    public void join(TransactionParticipant participant) {
	assert Thread.currentThread() == owner : "Wrong thread";
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "join {0} participant:{1}", this,
		       participant);
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	} else if (state == State.ABORTED) {
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction is not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction is not active: " + state);
	}
	if (!participants.contains(participant)) {
	    if (participant instanceof NonDurableTransactionParticipant) {
		if (hasDurableParticipant) {
		    participants.add(participants.size() - 1, participant);
		} else {
		    participants.add(participant);
		}
	    } else if (!hasDurableParticipant) {
		hasDurableParticipant = true;
		participants.add(participant);
	    } else {
		throw new UnsupportedOperationException(
		    "Attempt to add multiple durable participants");
	    }
	    if (collector != null) {
		String name = participant.getClass().getName();
		detailMap.put(name, new ProfileParticipantDetailImpl(name));
	    }
	}
    }

    /** {@inheritDoc} */
    public void abort(Throwable cause) {
	assert Thread.currentThread() == owner : "Wrong thread";
	logger.log(Level.FINER, "abort {0}", this);
	switch (state) {
	case ACTIVE:
	case PREPARING:
	    break;
	case ABORTING:
	    return;
	case ABORTED:
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction is not active", abortCause);
	case COMMITTING:
	case COMMITTED:
	    throw new IllegalStateException(
		"Transaction is not active: " + state);
	default:
	    throw new AssertionError();
	}
	state = State.ABORTING;
	abortCause = cause;
	long startTime = 0;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "abort {0} participant:{1}",
			   this, participant);
	    }
	    if (collector != null) {
		startTime = System.currentTimeMillis();
	    }
	    try {
		participant.abort(this);
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "abort {0} participant:{1} failed",
			this, participant);
		}
	    }
	    if (collector != null) {
		long finishTime = System.currentTimeMillis();
		ProfileParticipantDetailImpl detail =
		    detailMap.get(participant.getClass().getName());
		detail.setAborted(finishTime - startTime);
		collector.addParticipant(detail);
	    }
	}
	state = State.ABORTED;
    }

    /** {@inheritDoc} */
    public boolean isAborted() {
	return state == State.ABORTED || state == State.ABORTING;
    }

    /** {@inheritDoc} */
    public Throwable getAbortCause() {
	return abortCause;
    }

    /* -- Object methods -- */

    /**
     * Returns a string representation of this instance.
     *
     * @return	a string representation of this instance
     */
    public String toString() {
	return "TransactionImpl[tid:" + tid +
	    ", creationTime:" + creationTime +
	    ", timeout:" + timeout +
	    ", state:" + state + "]";
    }

    /**
     * Returns <code>true</code> if the argument is an instance of the same
     * class with the same transaction ID.
     *
     * @return <code>true</code> if the argument equals this instance,
     *	       otherwise <code>false</code>
     */
    public boolean equals(Object object) {
	return (object instanceof TransactionImpl) &&
	    tid == ((TransactionImpl) object).tid;
    }

    /**
     * Returns a hash code value for this object.
     *
     * @return	a hash code value for this object.
     */
    public int hashCode() {
	return (int) (tid >>> 32) ^ (int) tid;
    }

    /* -- Other methods -- */

    /**
     * Commits this transaction
     *
     * @throws	TransactionNotActiveException if the transaction has been
     *		aborted
     * @throws	TransactionAbortedException if a call to {@link
     *		TransactionParticipant#prepare prepare} on a transaction
     *		participant aborts the transaction but does not throw an
     *		exception
     * @throws	IllegalStateException if {@code prepare} has been called on any
     *		transaction participant and {@link Transaction#abort abort} has
     *		not been called on the transaction
     * @throws	Exception any exception thrown when calling {@code prepare} on
     *		a participant
     * @see	TransactionHandle#commit TransactionHandle.commit
     */
    void commit() throws Exception {
	assert Thread.currentThread() == owner : "Wrong thread";
	logger.log(Level.FINER, "commit {0}", this);
	if (state == State.ABORTED) {
	    throw new MaybeRetryableTransactionNotActiveException(
		"Transaction is not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction is not active: " + state);
	}
	state = State.PREPARING;
	long startTime = 0;
	ProfileParticipantDetailImpl detail = null;
	for (Iterator<TransactionParticipant> iter = participants.iterator();
	     iter.hasNext(); )
	{
	    TransactionParticipant participant = iter.next();
	    if (collector != null) {
		detail = detailMap.get(participant.getClass().getName());
		startTime = System.currentTimeMillis();
	    }
	    try {
		if (iter.hasNext()) {
		    boolean readOnly = participant.prepare(this);
		    if (collector != null) {
			detail.setPrepared(System.currentTimeMillis() -
					   startTime, readOnly);
		    }
		    if (readOnly) {
			iter.remove();
			if (collector != null)
			    collector.addParticipant(detail);
		    }
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "prepare {0} participant:{1} returns {2}",
				   this, participant, readOnly);
		    }
		} else {
		    participant.prepareAndCommit(this);
		    if (collector != null) {
			detail.
			    setCommittedDirectly(System.currentTimeMillis() -
						 startTime);
			collector.addParticipant(detail);
		    }
		    iter.remove();
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "prepareAndCommit {0} participant:{1} returns",
			    this, participant);
		    }
		}
	    } catch (Exception e) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.logThrow(
			Level.FINEST, e, "{0} {1} participant:{1} throws",
			iter.hasNext() ? "prepare" : "prepareAndCommit",
			this, participant);
		}
		if (state != State.ABORTED) {
		    abort(e);
		}
		throw e;
	    }
	    if (state == State.ABORTED) {
		throw new MaybeRetryableTransactionAbortedException(
		    "Transaction has been aborted", abortCause);
	    }
	}
	state = State.COMMITTING;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "commit {0} participant:{1}",
			   this, participant);
	    }
	    if (collector != null) {
		detail = detailMap.get(participant.getClass().getName());
		startTime = System.currentTimeMillis();
	    }
	    try {
		participant.commit(this);
		if (collector != null) {
		    detail.setCommitted(System.currentTimeMillis() -
					startTime);
		    collector.addParticipant(detail);
		}
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "commit {0} participant:{1} failed",
			this, participant);
		}
	    }
	}
	state = State.COMMITTED;
    }

    /** Returns a byte array that represents the specified long. */
    private byte[] longToBytes(long l) {
	return new byte[] {
	    (byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40),
	    (byte) (l >>> 32), (byte) (l >>> 24), (byte) (l >>> 16),
	    (byte) (l >>> 8), (byte) l };
    }
}
