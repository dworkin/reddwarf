/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import java.util.Arrays;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/** Provides a transaction implementation using Berkeley DB, Java Edition. */
class BdbJeTransaction implements DbTransaction {

    /** The Berkeley DB environment. */
    private final XAEnvironment env;

    /** The Berkeley DB transaction. */
    private final Transaction txn;

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
	try {
	    txn.commit();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }
}
