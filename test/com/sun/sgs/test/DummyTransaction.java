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
    private final long creationTime = System.currentTimeMillis();
    private State state = State.ACTIVE;
    DummyTransactionProxy proxy;
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
    public long getCreationTime() { return creationTime; }
    public void join(TransactionParticipant participant) {
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	participants.add(participant);
    }
    public void abort() {
	if (state != State.ACTIVE &&
	    state != State.PREPARING &&
	    state != State.PREPARED)
	{
	    throw new IllegalStateException(
		"Transaction not active or preparing");
	}
	state = State.ABORTED;
	if (proxy != null) {
	    proxy.setCurrentTransaction(null);
	}
	for (TransactionParticipant participant : participants) {
	    try {
		participant.abort(this);
	    } catch (Exception e) {
	    }
	}
    }
    public boolean prepare() throws Exception {
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	}
	state = State.PREPARING;
	if (proxy != null) {
	    proxy.setCurrentTransaction(null);
	}
	boolean result = true;
	for (TransactionParticipant participant : participants) {
	    if (!participant.prepare(this)) {
		result = false;
	    }
	}
	state = State.PREPARED;
	return result;
    }
    public void commit() throws Exception {
	if (state == State.PREPARED) {
	    for (TransactionParticipant participant : participants) {
		participant.commit(this);
	    }
	} else if (state != State.ACTIVE) {
	    throw new IllegalStateException("Transaction not active");
	} else if (usePrepareAndCommit && participants.size() == 1) {
	    state = State.PREPARING;
	    if (proxy != null) {
		proxy.setCurrentTransaction(null);
	    }
	    participants.iterator().next().prepareAndCommit(this);
	} else {
	    prepare();
	    for (TransactionParticipant participant : participants) {
		participant.commit(this);
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
