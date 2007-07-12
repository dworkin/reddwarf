/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/** Provide a transaction implementation using Berkeley DB. */
class BdbJeTransaction implements DbTransaction {

    /** The Berkeley DB environment. */
    private final XAEnvironment env;

    /** The Berkeley DB transaction. */
    final Transaction txn;

    final Map<BdbJeDatabase, Database> inUseDbs =
	new HashMap<BdbJeDatabase, Database>();

    /**
     * Implement a Xid whose format is always 1 and with a null branch
     * qualifier.
     */
    private final class SimpleXid implements Xid {
	/** The global transaction ID. */
	private byte[] gid;

	/** Creates an instance with the specified global transaction ID. */
	SimpleXid(byte[] gid) {
	    this.gid = gid;
	}

	/* -- Implement Xid -- */

	public int getFormatId() {
	    return 1;
	}

	public byte[] getGlobalTransactionId() {
	    return gid;
	}

	public byte[] getBranchQualifier() {
	    return null;
	}

	public int hashCode() {
	    return Arrays.hashCode(gid);
	}

	public boolean equals(Object object) {
	    if (object instanceof Xid) {
		Xid xid = (Xid) object;
		return xid.getFormatId() == 1 &&
		    Arrays.equals(xid.getGlobalTransactionId(), gid) &&
		    xid.getBranchQualifier() == null;
	    } else {
		return false;
	    }
	}
    }

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
    BdbJeTransaction(XAEnvironment env, long timeout) {
	this.env = env;
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
	return coerce(dbTxn).txn;
    }

    static BdbJeTransaction coerce(DbTransaction dbTxn) {
	if (dbTxn instanceof BdbJeTransaction) {
	    return (BdbJeTransaction) dbTxn;
	} else {
	    throw new IllegalArgumentException(
		"Transaction must be an instance of BdbJeTransaction");
	}
    }

    /* -- Implement DbTransaction -- */

    /** {@inheritDoc} */
    public void prepare(byte[] gid) {
	try {
	    Xid xid = new SimpleXid(gid);
	    env.setXATransaction(xid, txn);
 	    env.prepare(xid);
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	} catch (XAException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void commit() {
	makeDbsIdle();
	try {
	    txn.commit();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    public void abort() {
	makeDbsIdle();
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    private void makeDbsIdle() {
	for (Entry<BdbJeDatabase, Database> entry : inUseDbs.entrySet()) {
	    entry.getKey().addIdleDatabase(entry.getValue());
	}
	inUseDbs.clear();
    }
}
