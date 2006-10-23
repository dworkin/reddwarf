package com.sun.sgs.test;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DummyTransactionParticipant implements TransactionParticipant {
    private static final Logger logger =
	Logger.getLogger("com.sun.sgs.test.DummyTransactionParticipant");
    public static enum State { ACTIVE, PREPARED, COMMITTED, ABORTED };
    private State state = State.ACTIVE;
    public DummyTransactionParticipant() { }
    public boolean prepare(Transaction txn) throws Exception {
	logger.log(Level.FINE, "prepare");
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Not active");
	}
	state = State.PREPARED;
	return false;
    }
    public void commit(Transaction txn) {
	logger.log(Level.FINE, "commit");
	if (state != State.PREPARED) {
	    throw new IllegalStateException("Not prepared");
	}
	state = State.COMMITTED;
    }
    public void prepareAndCommit(Transaction txn) throws Exception {
	logger.log(Level.FINE, "prepareAndCommit");
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Not active");
	}
	state = State.COMMITTED;
    }
    public void abort(Transaction txn) {
	logger.log(Level.FINE, "abort");
	if (state != State.ACTIVE && state != State.PREPARED) {
	    throw new IllegalStateException("Not active or prepared");
	}
	state = State.ABORTED;
    }
    public State getState() { return state; }
}
