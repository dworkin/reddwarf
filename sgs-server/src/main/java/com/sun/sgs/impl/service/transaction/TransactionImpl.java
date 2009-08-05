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

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionListener;
import com.sun.sgs.service.TransactionParticipant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of Transaction. <p>
 *
 * Note that this implementation does not check that each joining
 * {@code TransactionParticipant} has a unique value for {@code getTypeName}.
 * Nor is this check done for {@code TransactionListener}s. If two
 * participants or listeners have the same type name then their
 * profiling data will be aggregated and reported as a single result.
 */
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

    /** Whether the prepareAndCommit optimization should be used. */
    private final boolean disablePrepareAndCommitOpt;
    
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
     * abort occurred.  Callers should synchronize on the current instance when
     * accessing this field unless they have checked that they are being called
     * from the creating thread.
     */
    private Throwable abortCause = null;

    /** The collectorHandle used to report participant detail. */
    private final ProfileCollectorHandle collectorHandle;

    /** Collected profiling data on each participant, created only if
     *  global profiling is set to MEDIUM at the start of the transaction.
     */
    private final HashMap<String, ProfileParticipantDetailImpl>
        participantDetailMap;

    /** Collected profiling data on each listener, created only if
     *  global profiling is set to MEDIUM at the start of the transaction.
     */
    private final HashMap<String, TransactionListenerDetailImpl>
        listenerDetailMap;

    /**
     * The registered {@code TransactionListener}s, or {@code null}.  The
     * listeners are stored and called in the order registered, to simplify
     * testing.
     */
    private List<TransactionListener> listeners = null;

    /**
     * Creates an instance with the specified transaction ID, timeout, 
     * prepare and commit optimization flag, and collectorHandle.
     */
    TransactionImpl(long tid, long timeout, boolean usePrepareAndCommitOpt,
                    ProfileCollectorHandle collectorHandle) 
    {
	this.tid = tid;
	this.timeout = timeout;
        this.disablePrepareAndCommitOpt = usePrepareAndCommitOpt;
	this.collectorHandle = collectorHandle;
	creationTime = System.currentTimeMillis();
	owner = Thread.currentThread();
	state = State.ACTIVE;
	if (collectorHandle.getCollector().
                getDefaultProfileLevel().ordinal() >= 
                ProfileLevel.MEDIUM.ordinal()) 
        {
	    participantDetailMap =
                new HashMap<String, ProfileParticipantDetailImpl>();
            listenerDetailMap =
                new HashMap<String, TransactionListenerDetailImpl>();
	} else {
	    participantDetailMap = null;
            listenerDetailMap = null;
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
	checkThread("checkTimeout");
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
	    abort(exception);
	    throw exception;
	}
    }

    /** {@inheritDoc} */
    public void join(TransactionParticipant participant) {
	checkThread("join");
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "join {0} participant:{1}", this,
		       getParticipantInfo(participant));
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	} else if (state == State.ABORTED) {
	    throw new TransactionNotActiveException(
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
	    if (participantDetailMap != null) {
		String name = participant.getTypeName();
		participantDetailMap.
                    put(name, new ProfileParticipantDetailImpl(name));
	    }
	}
    }

    /** {@inheritDoc} */
    public void abort(Throwable cause) {
	checkThread("abort");
	if (cause == null) {
	    throw new NullPointerException("The cause cannot be null");
	}
	logger.log(Level.FINER, "abort {0}", this);
	switch (state) {
	case ACTIVE:
	case PREPARING:
	    break;
	case ABORTING:
	    return;
	case ABORTED:
	    throw new TransactionNotActiveException(
		"Transaction is not active", abortCause);
	case COMMITTING:
	case COMMITTED:
	    throw new IllegalStateException(
		"Transaction is not active: " + state, cause);
	default:
	    throw new AssertionError();
	}
	state = State.ABORTING;
	synchronized (this) {
	    abortCause = cause;
	}
	long startTime = 0;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "abort {0} participant:{1}",
			   this, getParticipantInfo(participant));
	    }
	    if (participantDetailMap != null) {
		startTime = System.currentTimeMillis();
	    }
	    try {
		participant.abort(this);
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"abort {0} participant:{1} failed",
			this, getParticipantInfo(participant));
		}
	    }
	    if (participantDetailMap != null) {
		long finishTime = System.currentTimeMillis();
		ProfileParticipantDetailImpl detail =
		    participantDetailMap.get(participant.getTypeName());
		detail.setAborted(finishTime - startTime);
		collectorHandle.addParticipant(detail);
	    }
	}
	state = State.ABORTED;
	notifyListenersAfter(false);
    }

    /** {@inheritDoc} */
    public synchronized boolean isAborted() {
	return abortCause != null;
    }

    /** {@inheritDoc} */
    public synchronized Throwable getAbortCause() {
	return abortCause;
    }

    /** {@inheritDoc} */
    public void registerListener(TransactionListener listener) {
	checkThread("registerListener");
	if (listener == null) {
	    throw new NullPointerException("The listener must not be null");
	} else if (state != State.ACTIVE) {
	    throw new TransactionNotActiveException(
		"Transaction is not active: " + state);
	}
	if (listeners == null) {
	    listeners = new ArrayList<TransactionListener>();
	    listeners.add(listener);
	} else if (!listeners.contains(listener)) {
	    listeners.add(listener);
	}
        if (listenerDetailMap != null) {
            String name = listener.getTypeName();
            listenerDetailMap.put(name,
                                  new TransactionListenerDetailImpl(name));
        }
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
     *		participant or to {@link TransactionListener#beforeCompletion
     *		beforeCompletion} on a transaction listener aborts the
     *		transaction but does not throw an exception
     * @throws	IllegalStateException if {@code prepare} has been called on any
     *		transaction participant and {@link Transaction#abort abort} has
     *		not been called on the transaction, or if called from a thread
     *		that is not the thread that created this transaction
     * @throws	Exception any exception thrown when calling {@code prepare} on
     *		a participant or {@code beforeCompletion} on a listener
     * @see	TransactionHandle#commit TransactionHandle.commit
     */
    void commit() throws Exception {
	checkThread("commit");
	logger.log(Level.FINER, "commit {0}", this);
	if (state == State.ABORTED) {
	    throw new TransactionNotActiveException(
		"Transaction is not active", abortCause);
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException(
		"Transaction is not active: " + state);
	}
	notifyListenersBefore();
	state = State.PREPARING;
	long startTime = 0;
	ProfileParticipantDetailImpl detail = null;
	for (Iterator<TransactionParticipant> iter = participants.iterator();
	     iter.hasNext(); )
	{
	    TransactionParticipant participant = iter.next();
	    if (participantDetailMap != null) {
		detail = participantDetailMap.get(participant.getTypeName());
		startTime = System.currentTimeMillis();
	    }
	    try {
		if (iter.hasNext() || disablePrepareAndCommitOpt) {
		    boolean readOnly = participant.prepare(this);
		    if (detail != null) {
			detail.setPrepared(System.currentTimeMillis() -
					   startTime, readOnly);
		    }
		    if (readOnly) {
			iter.remove();
			if (detail != null) {
			    collectorHandle.addParticipant(detail);
			}
		    }
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "prepare {0} participant:{1} returns {2}",
				   this, getParticipantInfo(participant),
				   readOnly);
		    }
		} else {
		    participant.prepareAndCommit(this);
		    if (detail != null) {
			detail.
			    setCommittedDirectly(System.currentTimeMillis() -
						 startTime);
			collectorHandle.addParticipant(detail);
		    }
		    iter.remove();
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "prepareAndCommit {0} participant:{1} returns",
			    this, getParticipantInfo(participant));
		    }
		}
	    } catch (Exception e) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.logThrow(
			Level.FINEST, e, "{0} {1} participant:{1} throws",
			iter.hasNext() ? "prepare" : "prepareAndCommit",
			this, getParticipantInfo(participant));
		}
		if (state != State.ABORTED) {
		    abort(e);
		}
		throw e;
	    }
	    if (state == State.ABORTED) {
		throw new TransactionAbortedException(
		    "Transaction has been aborted: " + abortCause, abortCause);
	    }
	}
	state = State.COMMITTING;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "commit {0} participant:{1}",
			   this, getParticipantInfo(participant));
	    }
	    if (participantDetailMap != null) {
		detail = participantDetailMap.get(participant.getTypeName());
		startTime = System.currentTimeMillis();
	    }
	    try {
		participant.commit(this);
		if (detail != null) {
		    detail.setCommitted(System.currentTimeMillis() -
					startTime);
		    collectorHandle.addParticipant(detail);
		}
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "commit {0} participant:{1} failed",
			this, getParticipantInfo(participant));
		}
	    }
	}
	state = State.COMMITTED;
	notifyListenersAfter(true);
    }

    /** Returns a byte array that represents the specified long. */
    private byte[] longToBytes(long l) {
	return new byte[] {
	    (byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40),
	    (byte) (l >>> 32), (byte) (l >>> 24), (byte) (l >>> 16),
	    (byte) (l >>> 8), (byte) l };
    }

    /** Notify any listeners before preparing the transaction. */
    private void notifyListenersBefore() {
        TransactionListenerDetailImpl detail = null;
        long startTime = 0;
	if (listeners != null) {
	    /*
	     * Don't use foreach iteration here, so that we can handle the
	     * possibility that a beforeCompletion call adds another listener.
	     */
	    for (int i = 0; i < listeners.size(); i++) {
		TransactionListener listener = listeners.get(i);
		try {
                    if (listenerDetailMap != null) {
                        detail = listenerDetailMap.get(listener.getTypeName());
                        startTime = System.currentTimeMillis();
                    }
		    listener.beforeCompletion();
                    if (detail != null) {
                        long time = System.currentTimeMillis() - startTime;
                        detail.setCalledBeforeCompletion(false, time);
                    }
		} catch (RuntimeException e) {
                    if (detail != null) {
                        long time = System.currentTimeMillis() - startTime;
                        detail.setCalledBeforeCompletion(true, time);
                    }
		    if (logger.isLoggable(Level.FINEST)) {
			logger.logThrow(
			    Level.FINEST, e,
			    "beforeCompletion {0} listener:{1} failed",
			    this, listener);
		    }
		    if (state != State.ABORTED) {
			abort(e);
		    }
		    throw e;
		}
		if (state == State.ABORTED) {
		    throw new TransactionAbortedException(
			"Transaction has been aborted: " + abortCause,
			abortCause);
		}
	    }
	}
    }

    /** Notify any listeners after completing the transaction. */
    private void notifyListenersAfter(boolean commited) {
        TransactionListenerDetailImpl detail = null;
        long startTime = 0;
	if (listeners != null) {
	    for (TransactionListener listener : listeners) {
		try {
                    if (listenerDetailMap != null) {
                        detail = listenerDetailMap.get(listener.getTypeName());
                        startTime = System.currentTimeMillis();
                    }
		    listener.afterCompletion(commited);
                    if (detail != null) {
                        long time = System.currentTimeMillis() - startTime;
                        detail.setCalledAfterCompletion(time);
                        collectorHandle.addListener(detail);
                    }
		} catch (RuntimeException e) {
		    if (logger.isLoggable(Level.WARNING)) {
			logger.logThrow(
			    Level.WARNING, e,
			    "afterCompletion {0} listener:{1} failed",
			    this, listener);
		    }
		}
	    }
	}
    }

    /** Checks that current thread is the one that created this transaction. */
    private void checkThread(String methodName) {
	if (Thread.currentThread() != owner) {
	    throw new IllegalStateException(
		"The " + methodName + " method must be called from the" +
		" thread that created the transaction");
	}
    }

    /**
     * Returns a string that describes the participant.  Returns null if the
     * participant is null.
     */
    private static String getParticipantInfo(
	TransactionParticipant participant)
    {
	return participant == null ? null
	    : (participant.getTypeName() + " (" + participant + ")");
    }
}
