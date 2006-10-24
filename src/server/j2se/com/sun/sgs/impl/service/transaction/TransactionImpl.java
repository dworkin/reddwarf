package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides an implementation of Transaction. */
final class TransactionImpl implements Transaction {

    /** Logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(TransactionImpl.class.getName()));

    /** The possible states of the transaction. */
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
    };

    /** The transaction ID. */
    private final long tid;

    /** The time the transaction was created. */
    private final long creationTime;

    /** The state of the transaction. */
    private State state;

    /**
     * The transaction participants.  Individual participants are set to null
     * if they return true (read-only) when prepared, to mark that they should
     * not be committed or aborted.
     */
    private final List<TransactionParticipant> participants =
	new ArrayList<TransactionParticipant>();

    /** Whether this transaction has a durable participant. */
    private boolean hasDurableParticipant;

    /** Creates an instance with the specified transaction ID. */
    TransactionImpl(long tid) {
	this.tid = tid;
	creationTime = System.currentTimeMillis();
	state = State.ACTIVE;
	logger.log(Level.FINER, "created {0}", this);
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
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "join {0} participant:{1}", this,
		       participant);
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	}
	switch (state) {
	case ACTIVE:
	    break;
	case PREPARING:
	case ABORTING:
	case ABORTED:
	case COMMITTING:
	case COMMITTED:
	    throw new IllegalStateException("Transaction is not active");
	default:
	    throw new AssertionError();
	}
	if (!participants.contains(participant)) {
	    if (participant instanceof NonDurableTransactionParticipant) {
		participants.add(0, participant);
	    } else if (hasDurableParticipant) {
		throw new UnsupportedOperationException(
		    "Attempt to add multiple durable participants");
	    } else {
		hasDurableParticipant = true;
		participants.add(participant);
	    }
	}
    }

    /** {@inheritDoc} */
    public void abort() {
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
	    if (participant != null) {
		try {
		    participant.abort(this);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.logThrow(
			    Level.SEVERE,
			    "abort failed for txn:{0}, participant:{1}",
			    e, this, participant);
		    }
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
     * @throws	Exception if any participant throws an exception while
     *		preparing the transaction
     */
    void commit() throws Exception {
	logger.log(Level.FINER, "commit {0}", this);
	switch (state) {
	case ACTIVE:
	    break;
	case PREPARING:
	case ABORTING:
	case ABORTED:
	case COMMITTING:
	case COMMITTED:
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	default:
	    throw new AssertionError();
	}
	state = State.PREPARING;
	int last = participants.size() - 1;
	for (int i = 0; i <= last; i++) {
	    TransactionParticipant participant = participants.get(i);
	    try {
		if (i < last) {
		    boolean readOnly = participant.prepare(this);
		    if (readOnly) {
			participants.set(i, null);
		    }
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "prepare {0} participant:{1} returns {2}",
				   this, participant, readOnly);
		    }
		} else {
		    participant.prepareAndCommit(this);
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
			Level.FINEST, "{0} {1} participant:{1} throws",
			e, i < last ? "prepare" : "prepareAndCommit",
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
	for (int i = 0; i < last; i++) {
	    TransactionParticipant participant = participants.get(i);
	    if (participant != null) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "commit {0} participant:{1}",
			       this, participant);
		}
		try {
		    participant.commit(this);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.logThrow(
			    Level.SEVERE,
			    "commit failed for txn:{0}, participant:{1}",
			    e, this, participant);
		    }
		}
	    }
	}
	state = State.COMMITTED;
    }

    /** Returns whether this transaction is currently active. */
    boolean isActive() {
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
