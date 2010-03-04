/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.data.store.db.bdb;

import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.Transaction;
import com.sleepycat.db.TransactionConfig;
import com.sun.sgs.service.store.db.DbTransaction;

/** Provides a transaction implementation using Berkeley DB. */
class BdbTransaction implements DbTransaction {

    /** The Berkeley DB transaction. */
    private final Transaction txn;

    /**
     * Creates an instance of this class.
     *
     * @param	env the Berkeley DB environment
     * @param	timeout the number of milliseconds the transaction should be
     *		allowed to run
     * @param	txnConfig the Berkeley DB transaction configuration, or {@code
     *		null} for the default
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    BdbTransaction(
	Environment env, long timeout, TransactionConfig txnConfig)
    {
	if (timeout <= 0) {
	    throw new IllegalArgumentException(
		"Timeout must be greater than 0");
	}
	try {
	    txn = env.beginTransaction(null, txnConfig);
	    /* Avoid overflow -- BDB treats 0 as unlimited */
	    long timeoutMicros =
		(timeout < (Long.MAX_VALUE / 1000)) ? 1000 * timeout : 0;
	    txn.setTxnTimeout(timeoutMicros);
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, false);
	}
    }

    /** Converts the argument to a Berkeley DB transaction. */
    static Transaction getBdbTxn(DbTransaction dbTxn) {
	if (dbTxn instanceof BdbTransaction) {
	    return ((BdbTransaction) dbTxn).txn;
	} else {
	    throw new IllegalArgumentException(
		"Transaction must be an instance of BdbTransaction");
	}
    }

    /* -- Implement DbTransaction -- */

    /** {@inheritDoc} */
    public void prepare(byte[] gid) {
	try {
	    txn.prepare(gid);
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void commit() {
	try {
	    txn.commit();
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, false);
	}
    }
}
