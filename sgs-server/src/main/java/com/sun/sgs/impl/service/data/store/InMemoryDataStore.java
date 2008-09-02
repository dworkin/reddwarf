/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;

import java.io.ObjectStreamClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Defines the interface to the underlying persistence mechanism that {@link
 * DataServiceImpl} uses to store byte data. <p>
 *
 * Objects are identified by object IDs, which are positive
 * <code>long</code>s.  Names are mapped to object IDs.
 */
public class InMemoryDataStore implements DataStore, TransactionParticipant {

    private static final Logger logger = 
	Logger.getLogger(InMemoryDataStore.class.getName());

    private final AtomicLong oidCounter;

    private final ConcurrentMap<Long,byte[]> oidMap;

    /*
     * Implementation Note: use a sorted list to support searching by
     * name ordering using nextBoundName
     */
    private final ConcurrentNavigableMap<String,Long> boundNameMap;

    private final ConcurrentMap<Transaction,TxnInfo> openTxns;

    private final ConcurrentMap<ByteArrayWrapper,Integer> classInfoToId;

    private final ConcurrentMap<Integer,ByteArrayWrapper> classIdToInfo;

    private final AtomicInteger classIdCounter;

    public InMemoryDataStore(Properties properties) {
	oidCounter = new AtomicLong(0);
	oidMap = new ConcurrentHashMap<Long,byte[]>();
	boundNameMap = new ConcurrentSkipListMap<String,Long>();
	openTxns = new ConcurrentHashMap<Transaction,TxnInfo>();

	classInfoToId = new ConcurrentHashMap<ByteArrayWrapper,Integer>();
	classIdToInfo = new ConcurrentHashMap<Integer,ByteArrayWrapper>();
	classIdCounter = new AtomicInteger(1);
    }
    

    public long createObject(Transaction txn) {
	TxnInfo info = getInfo(txn);

	// This knowingly could waste an Oid should the calling
	// transaction abort.  We allow this to improve concurrency.
	return oidCounter.getAndIncrement();
    }


    public void markForUpdate(Transaction txn, long oid) {
	TxnInfo info = getInfo(txn);
	info.markForUpdate(oid);
    }

    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	TxnInfo info = getInfo(txn);
	return info.getObject(oid, forUpdate);	
    }


    public void setObject(Transaction txn, long oid, byte[] data) {
	TxnInfo info = getInfo(txn);
	info.setObject(oid, data);
    }


    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	if (oids.length != dataArray.length) {
	    throw new IllegalArgumentException(
		"The oids and dataArray must be the same length");
	}
	TxnInfo info = getInfo(txn);
	for (int i = 0; i < oids.length; ++i) {
	    info.setObject(oids[i], dataArray[i]);
	}
    }


    public void removeObject(Transaction txn, long oid) {
	TxnInfo info = getInfo(txn);
	info.removeObject(oid);	
    }

    public long getBinding(Transaction txn, String name) {
	TxnInfo info = getInfo(txn);
	return info.getBinding(name);	
    }

    public void setBinding(Transaction txn, String name, long oid) {
	TxnInfo info = getInfo(txn);
	info.setBinding(name, oid);	
    }


    public void removeBinding(Transaction txn, String name) {
 	TxnInfo info = getInfo(txn);
	info.removeBinding(name);	
    }

    public String nextBoundName(Transaction txn, String name) {
	TxnInfo info = getInfo(txn);
	return info.nextBoundName(name);
    }


    public boolean shutdown() {
	return true;
    }


    public String getTypeName() {
	return InMemoryDataStore.class.getName();
    }

    /**
     * {@inheritDoc} 
     */
    public void abort(Transaction txn) {
	TxnInfo info = openTxns.remove(txn);
	if (info == null) {
	    throw new IllegalStateException("Transaction is not active");
	}
    }

    /**
     * {@inheritDoc} 
     */
    public void commit(Transaction txn) {
	TxnInfo info = openTxns.remove(txn);
	if (info == null) {
	    throw new IllegalStateException("Transaction is not active");
	}
	info.commit();
    }


    /**
     * {@inheritDoc} 
     */
    public boolean prepare(Transaction txn) {
	// no op
	return false;
    }

    /**
     * {@inheritDoc} 
     */
    public void prepareAndCommit(Transaction txn) {
	commit(txn);
    }


    public int getClassId(Transaction txn, byte[] classInfo) {
	TxnInfo txnInfo = openTxns.get(txn);
	ByteArrayWrapper bytes = new ByteArrayWrapper(classInfo);
	Integer classId = classInfoToId.get(bytes);
	if (classId == null) {
	    classId = classIdCounter.getAndIncrement();
	    classInfoToId.put(bytes, classId);
	    classIdToInfo.put(classId, bytes);
	}
	return classId;
    }


    public byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException {
	TxnInfo txnInfo = openTxns.get(txn);	
	ByteArrayWrapper bytes = classIdToInfo.get(classId);
	if (bytes == null)
	    throw new ClassInfoNotFoundException("No information found for " + 
						 "class ID " + classId);
	return bytes.b;
    }

    public long nextObjectId(Transaction txn, long oid) {
	TxnInfo txnInfo = openTxns.get(txn);

	// nextObjectId is currently only used by the testing
	// framework.  Since we only use a counter for binding names,
	// and do not keep track of which ones are currently used, we
	// use an expensive operation here to sort the active oids.
	TreeSet<Long> sortedOids = new TreeSet<Long>(oidMap.keySet());
	Long higher = sortedOids.higher(oid);
	return (higher == null) ? -1 : higher.longValue();
    }


    /**
     * Checks that the correct transaction is in progress, and join if none is
     * in progress.  The op argument, if non-null, specifies the operation
     * being performed under the specified transaction.
     */
    private TxnInfo getInfo(Transaction txn) {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = openTxns.get(txn);
	if (txnInfo == null) {
	    txnInfo = joinTransaction(txn);
	} 
// 	else if (txnInfo.prepared) {
// 	    throw new IllegalStateException(
// 		"Transaction has been prepared");
// 	}
	return txnInfo;
    }

    /**
     * Joins the specified transaction, checking first to see if the data store
     * is currently shutting down, and returning the new TxnInfo.
     */
    private TxnInfo joinTransaction(Transaction txn) {
	boolean joined = false;
	try {
	    txn.join(this);
	    joined = true;
	} finally {
	    if (!joined) {
		// do something?
	    }
	}
	TxnInfo txnInfo = new TxnInfo(txn);
	openTxns.put(txn, txnInfo);
	return txnInfo;
    }


    /** 
     * Stores transaction information. 
     */
    private class TxnInfo {

	private final AtomicBoolean committed;
	
	private final Set<Long> removedIds;

	private final Set<String> removedNames;

	private final Map<Long,byte[]> oidUpdates;

	private final NavigableMap<String,Long> nameUpdates;

	private final Transaction txn;

	/**
	 *
	 */
	TxnInfo(Transaction txn) {
	    this.txn = txn;
	    committed = new AtomicBoolean(false);
	    removedIds = new HashSet<Long>();
	    removedNames = new HashSet<String>();
	    oidUpdates = new HashMap<Long,byte[]>();
	    nameUpdates = new TreeMap<String,Long>();
	}

	/**
	 * Commits the transaction by updating the store with all the
	 * changes made by this transaction
	 */
	void commit() {
	    if (!committed.compareAndSet(false, true))
		throw new IllegalStateException("tried to commit twice for " +
						"transaction " + txn);
	    for (Long oid : removedIds)
		oidMap.remove(oid);
	    oidMap.putAll(oidUpdates);
	    
            for (String name : removedNames) {
                boundNameMap.remove(name);
	    }

            boundNameMap.putAll(nameUpdates);
	    for (String name : nameUpdates.keySet()) {
	    }

	}

	private void checkOidNotAlreadyRemoved(long oid) {
	    if (removedIds.contains(oid)) {
		throw new ObjectNotFoundException("oid " + oid + " has already "
						  + "been removed in this "
						  + "transaction");
	    }
	}

	private void checkBindingNotAlreadyRemoved(String name) {
	    if (removedNames.contains(name)) {
		throw new NameNotBoundException("name " + name + " has already "
						+ "been removed in this "
						+ "transaction");
	    }
	}

	void markForUpdate(long oid) {
	    checkOidNotAlreadyRemoved(oid);
	    oidUpdates.put(oid, oidMap.get(oid));
	}
	
	byte[] getObject(long oid, boolean forUpdate) {
	    checkOidNotAlreadyRemoved(oid);
	    if (!oidMap.containsKey(oid) &&
		!oidUpdates.containsKey(oid))
		throw new ObjectNotFoundException("Object id " + oid + " is " +
						  "not valid");

	    byte[] b = (oidUpdates.containsKey(oid))
		? oidUpdates.get(oid)
		: oidMap.get(oid);

	    if (forUpdate)
		oidUpdates.put(oid, b);

	    return b;
	}
	
	void setObject(long oid, byte[] data) {
	    checkOidNotAlreadyRemoved(oid);	    
	    oidUpdates.put(oid, data);
	}
	
	void removeObject(long oid) {
	    checkOidNotAlreadyRemoved(oid);
	    if (!oidMap.containsKey(oid) &&
		!oidUpdates.containsKey(oid))
		throw new ObjectNotFoundException("Object id " + oid + " is " +
						  "not valid");
	    removedIds.add(oid);
	    oidUpdates.remove(oid);
	}	

	long getBinding(String name) {
	    checkBindingNotAlreadyRemoved(name);
	    Long oid = (nameUpdates.containsKey(name))
		? nameUpdates.get(name)
		: boundNameMap.get(name);
	    if (oid == null) {
		throw new NameNotBoundException(name + " is not bound");
	    }
	    return oid.longValue();
	}
	
	void setBinding(String name, long oid) {
	    
	    checkBindingNotAlreadyRemoved(name);	 
	    removedNames.remove(name);
	    nameUpdates.put(name, oid);
	}
	
	
	void removeBinding(String name) {
	    checkBindingNotAlreadyRemoved(name);
	    if (!boundNameMap.containsKey(name) &&
		!nameUpdates.containsKey(name))
		throw new NameNotBoundException(name + " is not bound");

	    removedNames.add(name);
	    nameUpdates.remove(name);
	}

	String nextBoundName(String name) {
	    // find the next name in the backing name mapping that
	    // hasn't been removed
	    String next = name;
	    do {
		next = boundNameMap.higherKey(next);
	    } while (next != null && removedNames.contains(next));

	    // then check whether we have a newly-added name that
	    // would have come before this name
	    String recentNext = nameUpdates.higherKey(name);
	    if (next == null)
		return recentNext;
	    else if (recentNext == null)
		return next;
	    else return (next.compareTo(recentNext) < 0) ? next : recentNext;
	}
    }

    private static class ByteArrayWrapper {
	
	final byte[] b;

	final int hash;
	
	ByteArrayWrapper(byte[] b) {
	    this.b = b;

	    int h = 0;
	    for (byte c : b) 
		h += c;
	    hash = h;
	}

	public boolean equals(Object o) {
	    if (o == null || !(o instanceof ByteArrayWrapper))
		return false;
	    ByteArrayWrapper bar = (ByteArrayWrapper)o;
	    return Arrays.equals(b, bar.b);
	}

	public int hashCode() {
	    return hash;
	}
    }

}
