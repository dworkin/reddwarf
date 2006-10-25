package com.sun.sgs.test;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DummyTransaction implements Transaction {
    private static final Logger logger =
	Logger.getLogger(DummyTransaction.class.getName());
    public enum State {
	ACTIVE, PREPARING, PREPARED, COMMITTING, COMMITTED, ABORTING, ABORTED
    };
    private static long nextId = 1;
    private static boolean nextUsePrepareAndCommit;
    private final boolean usePrepareAndCommit;
    private final long id = nextId++;
    private final long creationTime = System.currentTimeMillis();
    private State state = State.ACTIVE;
    DummyTransactionProxy proxy;
    public final Set<TransactionParticipant> participants =
	new HashSet<TransactionParticipant>();
    public DummyTransaction() {
	usePrepareAndCommit = nextUsePrepareAndCommit;
	nextUsePrepareAndCommit = !nextUsePrepareAndCommit;
	logger.log(Level.FINER, "create {0}", this);
    }
    public DummyTransaction(boolean usePrepareAndCommit) {
	this.usePrepareAndCommit = usePrepareAndCommit;
    }
    public byte[] getId() {
	return new byte[] {
	    (byte) (id >>> 56), (byte) (id >>> 48), (byte) (id >>> 40),
	    (byte) (id >>> 32), (byte) (id >>> 24), (byte) (id >>> 16),
	    (byte) (id >>> 8), (byte) id };
    }
    public long getCreationTime() { return creationTime; }
    public void join(TransactionParticipant participant) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINER, "join {0} participant:{1}",
		new Object[] { this, participant });
	}
	if (participant == null) {
	    throw new NullPointerException("Participant must not be null");
	}
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	participants.add(participant);
    }
    public void abort() {
	logger.log(Level.FINER, "abort {0}", this);
	if (state == State.ABORTING) {
	    return;
	} else if (state != State.ACTIVE &&
		   state != State.PREPARING &&
		   state != State.PREPARED)
	{
	    throw new IllegalStateException(
		"Transaction not active or preparing");
	}
	state = State.ABORTING;
	if (proxy != null) {
	    proxy.setCurrentTransaction(null);
	    proxy = null;
	}
	for (TransactionParticipant participant : participants) {
	    try {
		participant.abort(this);
	    } catch (RuntimeException e) {
	    }
	}
	state = State.ABORTED;
    }
    public boolean prepare() throws Exception {
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
		    abort();
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
    public void commit() throws Exception {
	logger.log(Level.FINER, "commit {0}", this);
	if (state == State.PREPARED) {
	    state = State.COMMITTING;
	    for (TransactionParticipant participant : participants) {
		try {
		    participant.commit(this);
		} catch (RuntimeException e) {
		}
	    }
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
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
		    abort();
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
		}
	    }
	}
	state = State.COMMITTED;
    }
    public State getState() {
	return state;
    }
    public String toString() {
	return "DummyTransaction[id:" + id + "]";
    }
}
