package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.ArrayList;
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

    /** Creates an instance with the specified transaction ID. */
    TransactionImpl(long tid) {
	this.tid = tid;
	creationTime = System.currentTimeMillis();
	owner = Thread.currentThread();
	state = State.ACTIVE;
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
    public void join(TransactionParticipant participant) {
	assert Thread.currentThread() == owner : "Wrong thread";
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "join {0} participant:{1}", this,
		       participant);
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction is not active");
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
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	assert Thread.currentThread() == owner : "Wrong thread";
	logger.log(Level.FINER, "abort {0}", this);
	switch (state) {
	case ACTIVE:
	case PREPARING:
	    break;
	case ABORTING:
	    return;
	case ABORTED:
	case COMMITTING:
	case COMMITTED:
	    throw new IllegalStateException("Transaction is not active");
	default:
	    throw new AssertionError();
	}
	state = State.ABORTING;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "abort {0} participant:{1}",
			   this, participant);
	    }
	    try {
		participant.abort(this);
	    } catch (Exception e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "abort {0} participant:{1} failed",
			this, participant);
		}
	    }
	}
	state = State.ABORTED;
    }

    /* -- Object methods -- */

    /**
     * Returns a string representation of this instance.
     *
     * @return	a string representation of this instance
     */
    public String toString() {
	return "TransactionImpl[tid:" + tid + "]";
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
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	TransactionAbortedException if the transaction was aborted
     *		during preparation without an exception being thrown
     * @throws	Exception if any participant throws an exception while
     *		preparing the transaction
     * @see	TransactionHandle#commit TransactionHandle.commit
     */
    void commit() throws Exception {
	assert Thread.currentThread() == owner : "Wrong thread";
	logger.log(Level.FINER, "commit {0}", this);
	if (state != State.ACTIVE) {
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}
	state = State.PREPARING;
	for (Iterator<TransactionParticipant> iter = participants.iterator();
	     iter.hasNext(); )
	{
	    TransactionParticipant participant = iter.next();
	    try {
		if (iter.hasNext()) {
		    boolean readOnly = participant.prepare(this);
		    if (readOnly) {
			iter.remove();
		    }
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "prepare {0} participant:{1} returns {2}",
				   this, participant, readOnly);
		    }
		} else {
		    participant.prepareAndCommit(this);
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
		    abort();
		}
		throw e;
	    }
	    if (state == State.ABORTED) {
		throw new TransactionAbortedException(
		    "Transaction was aborted");
	    }
	}
	state = State.COMMITTING;
	for (TransactionParticipant participant : participants) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "commit {0} participant:{1}",
			   this, participant);
	    }
	    try {
		participant.commit(this);
	    } catch (Exception e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "commit {0} participant:{1} failed",
			this, participant);
		}
	    }
	}
	state = State.COMMITTED;
    }

    /** Returns whether this transaction is currently active. */
    boolean isActive() {
	assert Thread.currentThread() == owner : "Wrong thread";
	return state == State.ACTIVE;
    }

    /** Returns a byte array that represents the specified long. */
    private byte[] longToBytes(long l) {
	return new byte[] {
	    (byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40),
	    (byte) (l >>> 32), (byte) (l >>> 24), (byte) (l >>> 16),
	    (byte) (l >>> 8), (byte) l };
    }
}
