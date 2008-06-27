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

package com.sun.sgs.impl.contention;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;


public class ContentionManagementComponent 
    implements NonDurableTransactionParticipant {

    public static final int OLD_TXNS_TO_KEEP = 200;

    Map<Transaction,TxnInfo> openTxns;

    ConcurrentBoundedMap<Transaction,TxnInfo> finishedTxns;
    
    TransactionProxy txnProxy;
    
    public ContentionManagementComponent(TransactionProxy txnProxy) {
	this.txnProxy = txnProxy;
		
	openTxns = new ConcurrentHashMap<Transaction,TxnInfo>();
	finishedTxns = 
	    new ConcurrentBoundedMap<Transaction,TxnInfo>(OLD_TXNS_TO_KEEP);

	System.out.println(this + " created!");
    }

    public void abort(Transaction txn) { 
	// determin why
	
	//System.out.println(txn + " aborted");

	TxnInfo info = openTxns.remove(txn);
	info.end();

	if (info.getRunTime() > 100) {
	    System.out.printf("%s probably timed out for running %dms\n",
			      txn, info.getRunTime());
	}

	HashMap<Transaction,TxnInfo> openAtAbort = 
	    new HashMap<Transaction,TxnInfo>(openTxns);

	

	// see if any transactions intersect with this one
	for (Map.Entry<Transaction,TxnInfo> other : finishedTxns.entrySet()) {
	    // did any of them have shared resources?
	    
	    TxnInfo otherInfo = other.getValue();

	    if (info.contendsWith(otherInfo)) {

		System.out.printf("Task type %s with Transaction (id: %d) \n%scontended with with" +
				  " was ABORTED due to conflicts with\nTask type %s Transaction (id: %d) \n%s\n",
				  info.taskType,
				  getID(txn), info, 
				  otherInfo.taskType,
				  getID(other.getKey()), otherInfo);
		break;
	    }
	}
	
	finishedTxns.put(txn, info);
    }

    public void bind(Long oid, Object o) {
	Transaction txn = getCurrentTransaction();
	if (txn == null)
	    return;
	TxnInfo info = openTxns.get(txn);
	info.bind(oid, o);
    }
       
    public void commit(Transaction txn) { 
	// remove from current listing of transactions
	TxnInfo info = openTxns.remove(txn);
	info.end();
	finishedTxns.put(txn, info);
    }

    private Transaction getCurrentTransaction() {
	try {
	    Transaction txn = txnProxy.getCurrentTransaction();
	    return txn;
	}
	catch (TransactionNotActiveException tnae) {
	    return null;
	}
    }

    private static long getID(Transaction txn) {
	return new BigInteger(txn.getId()).longValue();
    }    

    public String getTypeName() { return "mgmt"; } 

    public boolean prepare(Transaction txn) {
	return false;
    }

    public void prepareAndCommit(Transaction txn) {
	commit(txn);
    }

    public void registerTransaction(Transaction txn, String taskType) {
	txn.join(this);
	// System.out.println(txn + " joined");
	openTxns.put(txn, new TxnInfo(taskType));
    }

    public void registerReadLock(long oid) {
	Transaction txn = getCurrentTransaction();
	if (txn == null)
	    return;
	TxnInfo info = openTxns.get(txn);
	if (info == null) {
	    throw new IllegalStateException("Unregistered transaction " + 
					    "providing information: " + txn);
	}

	info.addReadLock(oid);
    }

     public void registerReadLockOnName(String name) {
 	Transaction txn = getCurrentTransaction();
 	if (txn == null)
 	    return;
	TxnInfo info = openTxns.get(txn);
	if (info == null) {
	    throw new IllegalStateException("Unregistered transaction " + 
					    "providing information: " + txn);
 	}

 	info.addReadLockOnName(name);
     }    

    public void registerWriteLock(long oid) {
	Transaction txn = getCurrentTransaction();
	if (txn == null)
	    return;
	TxnInfo info = openTxns.get(txn);
	if (info == null) {
	    throw new IllegalStateException("Unregistered transaction " + 
					    "providing information: " + txn);
	}

	info.addWriteLock(oid);
    }

     public void registerWriteLockOnName(String name) {
 	Transaction txn = getCurrentTransaction();
 	if (txn == null)
 	    return;
	TxnInfo info = openTxns.get(txn);
	if (info == null) {
	    throw new IllegalStateException("Unregistered transaction " + 
					    "providing information: " + txn);
 	}

 	info.addWriteLockOnName(name);
     }    

    private static class ConcurrentBoundedMap<K,V> {

	Queue<K> keys;
	
	Map<K,V> backingMap;

	final int sizeBound;

	AtomicInteger size;

	public ConcurrentBoundedMap(int sizeBound) {
	    this.sizeBound = sizeBound;
	    size = new AtomicInteger(0);
	    keys = new ConcurrentLinkedQueue<K>();
	    backingMap = new ConcurrentHashMap<K,V>();
	}

	public V get(Object o) {
	    return backingMap.get(o);
	}

	public void put(K key, V value) {
	    if (size.get() > sizeBound) 
		backingMap.remove(keys.poll());
	    else 
		size.incrementAndGet();
	    
	    keys.offer(key);
	    backingMap.put(key, value);
	    
	}

	/**
	 * Returns a <i>copy</i> of the entry set for this map at the
	 * time of the call.
	 */
	public Set<Entry<K,V>> entrySet() {
	    return new HashSet<Entry<K,V>>(backingMap.entrySet());
	}
	
	public int size() {
	    return size.get();
	}
	
    }

    private static class TxnInfo {

	private long startTime;
	
	private long endTime;

	Set<Long> readLocks;
	
	Set<Long> writeLocks;

	Set<String> nameReadLocks;

	Set<String> nameWriteLocks;

	Map<Long,Object> oidsToManagedObjects;

	String taskType;

	public TxnInfo(String taskType) {
	    this.taskType = taskType;
	    startTime = System.currentTimeMillis();
	    endTime = -1;
	    
	    readLocks = new HashSet<Long>();
	    writeLocks = new HashSet<Long>();
	    nameReadLocks = new HashSet<String>();
	    nameWriteLocks = new HashSet<String>();
	    oidsToManagedObjects = new HashMap<Long,Object>();
	}

	public void addReadLock(Long oid) {
	    readLocks.add(oid);
	}

	public void addWriteLock(Long oid) {
	    writeLocks.add(oid);
	}

	public void addReadLockOnName(String name) {
	    nameReadLocks.add(name);
	}

	public void addWriteLockOnName(String name) {
	    nameWriteLocks.add(name);
	}

	public void bind(Long oid, Object o) {
	    oidsToManagedObjects.put(oid, o);
	}

	public void end() {
	    endTime = System.currentTimeMillis();
	}

	public long getRunTime() {
	    return endTime - startTime;
	}
	
	public boolean contendsWith(TxnInfo other) {

	    for (Long oid : readLocks) {
		if (other.writeLocks.contains(oid))
		    return true;
	    }
	    for (Long oid : other.readLocks) {
		if (writeLocks.contains(oid))
		    return true;
	    }
	    for (Long oid : writeLocks) {
		if (other.writeLocks.contains(oid))
		    return true;
	    }

	    for (String name : nameReadLocks) {
		if (other.nameWriteLocks.contains(name))
		    return true;
	    }

	    for (String name : other.nameReadLocks) {
		if (nameWriteLocks.contains(name))
		    return true;
	    }

	    return false;
	}

	private String readLockedObjects() {
	    return setToString(readLocks, nameReadLocks);
	}

	private String writeLockedObjects() {
	    return setToString(writeLocks, nameWriteLocks);
	}

	private String setToString(Set<Long> oids, Set<String> names) {
	    String objs = (oids.isEmpty()) ? "\n\toids: [NONE]\n" : "\n";

	    for (Long oid : oids) {
		Object o = oidsToManagedObjects.get(oid);
		String toString = "";
		try {
		    toString = o.toString();
		}
		// We know that o cannot be null.  However its
		// toString might cause some exception, which we want
		// to avoid propagating.  For example, this case
		// happens in a ClientSessionWrapper, whose toString
		// relies on the transaction being active and
		// therefore throws a TransactionNotActiveException
		// when called.
		catch (Throwable t) {
		    toString = ".toString() threw " + t;
		}
		
		objs += "\t" + oid +  ":" +  o.getClass().getName() + " -> " 
		    + toString + "\n";
	    }
	    String nms = (names.isEmpty()) ? "\tnames: [NONE]" : "";
	    for (String name : names) {
		nms += "\tname: " + name + "\n";
	    } 

	    return objs + nms;
	}

	public String toString() {
	    return "Read Locks Held:" + readLockedObjects() +
		//"\tnames: " + nameReadLocks +"\n" +
		"Write Locks Held:" + writeLockedObjects() + "\n"; // +
	    //"\tnames: " + nameWriteLocks +"\n";
	}

    }

}