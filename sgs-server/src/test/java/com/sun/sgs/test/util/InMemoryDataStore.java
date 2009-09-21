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

package com.sun.sgs.test.util;

import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.service.data.store.AbstractDataStore;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * An implementation of {@link DataStore} that stores data in memory and relies
 * on the access coordinator for locking.  Does minimal error checking.
 */
public class InMemoryDataStore extends AbstractDataStore {

    /** The logger to pass to AbstractDataStore. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(InMemoryDataStore.class.getName()));

    /** Stores the next node ID. */
    private static final AtomicLong nextNodeId = new AtomicLong(1);

    /** Stores the local node ID. */
    private final long nodeId = nextNodeId.getAndIncrement();

    /** Maps object IDs to data. */
    private final NavigableMap<Long, byte[]> oids =
	new TreeMap<Long, byte[]>();

    /** Maps names to object IDs. */
    private final NavigableMap<String, Long> names =
	new TreeMap<String, Long>();

    /**
     * Maps transactions to lists of key/value pairs for undoing the operations
     * performed by the transaction.
     */
    private final Map<Transaction, List<Object>> txnEntries =
	new HashMap<Transaction, List<Object>>();

    /** Stores the next object ID. */
    private long nextOid = 1;

    /** Maps class IDs to class info arrays. */
    private final Map<Integer, byte[]> classIdMap =
	new HashMap<Integer, byte[]>();

    /** Maps class info arrays to class IDs. */
    private final Map<BigInteger, Integer> classInfoMap =
	new HashMap<BigInteger, Integer>();

    /** Stores the next class ID. */
    private int nextClassId = 1;

    /** Creates an instance of this class. */
    public InMemoryDataStore(Properties properties,
			     ComponentRegistry systemRegistry,
			     TransactionProxy txnProxy)
    {
	super(systemRegistry, logger, logger);
    }

    /* -- Implement AbstractDataStore methods -- */

    protected long getLocalNodeIdInternal() {
	return nodeId;
    }

    protected synchronized long createObjectInternal(Transaction txn) {
	return nextOid++;
    }

    protected void markForUpdateInternal(Transaction txn, long oid) { }

    protected synchronized byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate)
    {
	txn.join(this);
	byte[] value = oids.get(oid);
	if (value != null) {
	    return value;
	} else {
	    throw new ObjectNotFoundException("");
	}
    }

    protected synchronized void setObjectInternal(
	Transaction txn, long oid, byte[] data)
    {
	txn.join(this);
	byte[] oldValue = oids.put(oid, data);
	List<Object> txnEntry = getTxnEntry(txn);
	txnEntry.add(oid);
	txnEntry.add(oldValue);
    }

    protected synchronized void setObjectsInternal(
	Transaction txn, long[] oids, byte[][] dataArray)
    {
	txn.join(this);
	List<Object> txnEntry = getTxnEntry(txn);
	for (int i = 0; i < oids.length; i++) {
	    byte[] oldValue = this.oids.put(oids[i], dataArray[i]);
	    txnEntry.add(oids[i]);
	    txnEntry.add(oldValue);
	}
    }

    protected synchronized void removeObjectInternal(
	Transaction txn, long oid)
    {
	txn.join(this);
	byte[] oldValue = oids.remove(oid);
	if (oldValue != null) {
	    List<Object> txnEntry = getTxnEntry(txn);
	    txnEntry.add(oid);
	    txnEntry.add(oldValue);
	} else {
	    throw new ObjectNotFoundException("");
	}
    }

    protected synchronized BindingValue getBindingInternal(
	Transaction txn, String name)
    {
	txn.join(this);
	if (names.containsKey(name)) {
	    return new BindingValue(names.get(name), null);
	} else {
	    return new BindingValue(-1, names.higherKey(name));
	}
    }

    protected synchronized BindingValue setBindingInternal(
	Transaction txn, String name, long oid)
    {
	txn.join(this);
	Long oldValue = names.put(name, oid);
	List<Object> txnEntry = getTxnEntry(txn);
	txnEntry.add(name);
	txnEntry.add(oldValue);
	if (oldValue != null) {
	    return new BindingValue(1, null);
	} else {
	    return new BindingValue(-1, names.higherKey(name));
	}
    }

    protected synchronized BindingValue removeBindingInternal(
	Transaction txn, String name)
    {
	txn.join(this);
	Long oldValue = names.remove(name);
	if (oldValue != null) {
	    List<Object> txnEntry = getTxnEntry(txn);
	    txnEntry.add(name);
	    txnEntry.add(oldValue);
	    return new BindingValue(1, names.higherKey(name));
	} else {
	    return new BindingValue(-1, names.higherKey(name));
	}
    }

    protected synchronized String nextBoundNameInternal(
	Transaction txn, String name)
    {
	txn.join(this);
	if (name != null) {
	    return names.higherKey(name);
	} else if (!names.isEmpty()) {
	    return names.firstKey();
	} else {
	    return null;
	}
    }

    protected void shutdownInternal() { }

    protected synchronized int getClassIdInternal(
	Transaction txn, byte[] classInfo)
    {
	BigInteger key = new BigInteger(classInfo);
	Integer classId = classInfoMap.get(key);
	if (classId == null) {
	    classId = nextClassId++;
	    classInfoMap.put(key, classId);
	    classIdMap.put(classId, classInfo);
	}
	return classId;
    }

    protected synchronized byte[] getClassInfoInternal(
	Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	byte[] classInfo = classIdMap.get(classId);
	if (classInfo != null) {
	    return classInfo;
	} else {
	    throw new ClassInfoNotFoundException("");
	}
    }

    protected synchronized long nextObjectIdInternal(
	Transaction txn, long oid)
    {
	txn.join(this);
	Long higherKey = oids.higherKey(oid);
	return (higherKey != null) ? higherKey.longValue() : -1;
    }

    protected boolean prepareInternal(Transaction txn) {
	return false;
    }

    protected synchronized void commitInternal(Transaction txn) {
	removeTxnEntry(txn);
    }

    protected synchronized void prepareAndCommitInternal(Transaction txn) {
	removeTxnEntry(txn);
    }

    protected synchronized void abortInternal(Transaction txn) {
	List<Object> txnEntry = getTxnEntry(txn);
	for (int i = txnEntry.size() - 2; i >= 0; i -= 2) {
	    Object key = txnEntry.get(i);
	    Object value = txnEntry.get(i + 1);
	    if (key instanceof Long) {
		if (value != null) {
		    oids.put((Long) key, (byte[]) value);
		} else {
		    oids.remove((Long) key);
		}
	    } else if (value != null) {
		names.put((String) key, (Long) value);
	    } else {
		names.remove((String) key);
	    }
	}
	removeTxnEntry(txn);
    }

    /* -- Other methods -- */

    /** Returns the undo list for the specified transaction. */
    private List<Object> getTxnEntry(Transaction txn) {
	List<Object> result = txnEntries.get(txn);
	if (result == null) {
	    result = new ArrayList<Object>();
	    txnEntries.put(txn, result);
	}
	return result;
    }

    /** Removes the undo list for the specified transaction. */
    private void removeTxnEntry(Transaction txn) {
	txnEntries.remove(txn);
    }
}
