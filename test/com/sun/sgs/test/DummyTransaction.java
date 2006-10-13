package com.sun.sgs.test;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.HashSet;
import java.util.Set;

public class DummyTransaction implements Transaction {
    public enum State { ACTIVE, PREPARING, PREPARED, COMMITTED, ABORTED };
    private static long nextId = 1;
    private static boolean nextUsePrepareAndCommit;
    private final boolean usePrepareAndCommit;
    private final long id = nextId++;
    private final long timestamp = System.currentTimeMillis();
    private State state = State.ACTIVE;
    public final Set<TransactionParticipant> participants =
	new HashSet<TransactionParticipant>();
    public DummyTransaction() {
	usePrepareAndCommit = nextUsePrepareAndCommit;
	nextUsePrepareAndCommit = !nextUsePrepareAndCommit;
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
    public long getTimeStamp() { return timestamp; }
    public void join(TransactionParticipant participant) {
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	participants.add(participant);
    }
    public void abort() {
	if (state != State.ACTIVE && state != State.PREPARING) {
	    throw new IllegalStateException(
		"Transaction not active or preparing");
	}
	state = State.ABORTED;
	for (TransactionParticipant participant : participants) {
	    try {
		participant.abort(this);
	    } catch (Exception e) {
	    }
	}
    }
    public boolean prepare() {
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	state = State.PREPARING;
	for (TransactionParticipant participant : participants) {
	    try {
		participant.prepare(this);
	    } catch (Exception e) {
		if (state != State.ABORTED) {
		    throw new RuntimeException(
			"Participant failed to abort when prepare failed: " +
			participant + "\nException: " + e);
		}
		return false;
	    }
	}
	state = State.PREPARED;
	return true;
    }
    public void commit() {
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	if (usePrepareAndCommit && participants.size() == 1) {
	    participants.iterator().next().prepareAndCommit(this);
	} else {
	    prepare();
	    for (TransactionParticipant participant : participants) {
		participant.commit(this);
	    }
	}
    }
    public State getState() {
	return state;
    }
}
