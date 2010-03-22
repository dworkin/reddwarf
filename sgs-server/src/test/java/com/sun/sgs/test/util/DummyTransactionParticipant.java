/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.util;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides a testing implementation of a transaction participant. */
public class DummyTransactionParticipant implements TransactionParticipant {

    /** The logger for this class. */
    private static final Logger logger =
	Logger.getLogger(DummyTransactionParticipant.class.getName());

    /** The possible states for a participant. */
    public static enum State { ACTIVE, PREPARED, COMMITTED, ABORTED }

    /** The state of this participant. */
    private State state = State.ACTIVE;

    /** Whether prepare was called and returned true. */
    private boolean prepareReturnedTrue;

    /** Creates an instance of this class. */
    public DummyTransactionParticipant() { }

    /* -- Implement TransactionParticipant -- */

    public boolean prepare(Transaction txn) throws Exception {
	logger.log(Level.FINE, "prepare");
	if (state != State.ACTIVE) {
	    throw new IllegalStateException("Not active");
	}
	state = State.PREPARED;
	boolean result = prepareResult();
	if (result) {
	    prepareReturnedTrue = true;
	}
	return result;
    }

    public void commit(Transaction txn) {
	logger.log(Level.FINE, "commit");
	if (state != State.PREPARED) {
	    throw new IllegalStateException("Not prepared");
	} else if (prepareReturnedTrue) {
	    throw new RuntimeException(
		"Committing read-only participant");
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
	} else if (prepareReturnedTrue) {
	    throw new RuntimeException(
		"Aborting read-only participant");
	}
	state = State.ABORTED;
    }
    
    public String getTypeName() { return "DummyTransactionParticipant"; }

    /* -- Other methods -- */

    /** Returns the current state. */
    public State getState() { return state; }

    /** Returns true if prepare was called and returned true. */
    public boolean prepareReturnedTrue() { return prepareReturnedTrue; }

    /** Returns the value to be returned by prepare. */
    protected boolean prepareResult() { return false; }
}
