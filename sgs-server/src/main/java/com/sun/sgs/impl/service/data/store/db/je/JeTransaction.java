/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.impl.service.data.store.db.je;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.XAEnvironment;
import com.sun.sgs.service.store.db.DbTransaction;
import java.util.Arrays;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/** Provides a transaction implementation using Berkeley DB Java Edition. */
class JeTransaction implements DbTransaction {

    /** The Berkeley DB environment. */
    private final XAEnvironment env;

    /** The Berkeley DB transaction. */
    private final Transaction txn;

    /** The XID if the transaction was prepared, else null. */
    private Xid xid = null;

    /**
     * Implement an Xid whose format is 1, and whose branch qualifier is null.
     */
    private static final class SimpleXid implements Xid {
	/** The global transaction ID. */
	private final byte[] gid;

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
     * @param	txnConfig the Berkeley DB transaction configuration, or {@code
     *		null} for the default
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    JeTransaction(
	XAEnvironment env, long timeout, TransactionConfig txnConfig)
    {
	this.env = env;
	if (timeout <= 0) {
	    throw new IllegalArgumentException(
		"Timeout must be greater than 0");
	}
	try {
	    txn = env.beginTransaction(null, txnConfig);
	    /* Avoid overflow -- BDB treats 0 as unlimited */
	    long timeoutMicros =
		(timeout < (Long.MAX_VALUE / 1000)) ? timeout * 1000 : 0;
	    txn.setTxnTimeout(timeoutMicros);
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }

    /** Converts the argument to a Berkeley DB transaction. */
    static Transaction getJeTxn(DbTransaction dbTxn) {
	if (dbTxn instanceof JeTransaction) {
	    return ((JeTransaction) dbTxn).txn;
	} else {
	    throw new IllegalArgumentException(
		"Transaction must be an instance of JeTransaction");
	}
    }

    /* -- Implement DbTransaction -- */

    /** {@inheritDoc} */
    public void prepare(byte[] gid) {
	try {
	    xid = new SimpleXid(gid);
	    env.setXATransaction(xid, txn);
 	    env.prepare(xid);
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	} catch (XAException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void commit() {
	try {
	    if (xid != null) {
		env.commit(xid, true /* ignored */);
	    } else {
		txn.commit();
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	} catch (XAException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	try {
	    if (xid != null) {
		env.rollback(xid);
	    } else {
		txn.abort();
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	} catch (XAException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }
}
