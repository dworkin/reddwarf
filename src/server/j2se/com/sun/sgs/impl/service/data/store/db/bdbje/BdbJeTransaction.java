/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;

/** Provide a transaction implementation using Berkeley DB. */
class BdbJeTransaction implements DbTransaction {

    /** The Berkeley DB transacction. */
    private final Transaction txn;

    /**
     * Creates an instance of this class.
     *
     * @param	env the Berkeley DB environment
     * @param	timeout the number of milliseconds the transaction should be
     *		allowed to run
     * @return	the transaction
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    BdbJeTransaction(Environment env, long timeout) {
	if (timeout <= 0) {
	    throw new IllegalArgumentException(
		"Timeout must be greater than 0");
	}
	try {
	    txn = env.beginTransaction(null, null);
	    long timeoutMicros = 1000 * timeout;
	    if (timeoutMicros < 0) {
		/* Berkeley DB treats a zero timeout as unlimited */
		timeoutMicros = 0;
	    }
	    txn.setLockTimeout(timeoutMicros);
	    txn.setTxnTimeout(timeoutMicros);
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    /** Converts the argument to a Berkeley DB transaction. */
    static Transaction getBdbTxn(DbTransaction dbTxn) {
	if (dbTxn instanceof BdbJeTransaction) {
	    return ((BdbJeTransaction) dbTxn).txn;
	} else {
	    throw new IllegalArgumentException(
		"Transaction must be an instance of BdbJeTransaction");
	}
    }

    /* -- Implement DbTransaction -- */

    /** {@inheritDoc} */
    public void prepare(byte[] gid) {
	/* XXX: Does nothing for now. */
    }

    /** {@inheritDoc} */
    public void commit() {
	try {
	    txn.commit();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    public void abort() {
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }
}
