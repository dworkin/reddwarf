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

package com.sun.sgs.impl.service.data.store.db.je;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import java.util.Arrays;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

/** Provides a transaction implementation using Berkeley DB, Java Edition. */
class JeTransaction implements DbTransaction {

    /**
     * The maximum number of milliseconds that can be represented in
     * microsystems as a long.
     */
    private static final long MAX_MILLISECONDS = Long.MAX_VALUE / 1000;

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
    JeTransaction(XAEnvironment env, long timeout) {
	this.env = env;
	try {
	    if (timeout <= 0) {
		throw new IllegalArgumentException(
		    "Timeout must be greater than 0");
	    }
	    txn = env.beginTransaction(null, null);
	    /* Berkeley DB treats a zero timeout as unlimited */
	    long timeoutMicros =
		timeout < MAX_MILLISECONDS ? timeout * 1000 : 0;
	    txn.setTxnTimeout(timeoutMicros);
// 	    try {
// 		java.lang.reflect.Field field =
// 		    com.sleepycat.je.Transaction.class.getDeclaredField("txn");
// 		field.setAccessible(true);
// 		com.sleepycat.je.txn.Txn jeTxn = (com.sleepycat.je.txn.Txn)
// 		    field.get(txn);
// 		System.err.println("lock timeout: " + jeTxn.getLockTimeout());
// 		System.err.println("txn timeout: " + jeTxn.getTxnTimeOut());
// 	    } catch (Exception e) {
// 		e.printStackTrace();
// 	    }
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
	    Xid xid = new SimpleXid(gid);
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
	    txn.commit();
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }

    /** {@inheritDoc} */
    public void abort() {
	try {
	    txn.abort();
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }
}
