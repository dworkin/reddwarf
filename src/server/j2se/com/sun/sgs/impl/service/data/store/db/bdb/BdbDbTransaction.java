/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbdb;

import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.Transaction;

/** Provide a transaction implementation using Berkeley DB. */
class BdbDbTransaction implements DbTransaction {

    /** The Berkeley DB transaction. */
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
    BdbDbTransaction(Environment env, long timeout) {
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
	    throw BdbDbEnvironment.convertException(e, false);
	}
    }

    /** Converts the argument to a Berkeley DB transaction. */
    static Transaction getBdbTxn(DbTransaction dbTxn) {
	if (dbTxn instanceof BdbDbTransaction) {
	    return ((BdbDbTransaction) dbTxn).txn;
	} else {
	    throw new IllegalArgumentException(
		"Transaction must be an instance of BdbDbTransaction");
	}
    }

    /* -- Implement DbTransaction -- */

    /** {@inheritDoc} */
    public void prepare(byte[] gid) {
	try {
	    txn.prepare(gid);
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void commit() {
	try {
	    txn.commit();
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, false);
	}
    }
}
