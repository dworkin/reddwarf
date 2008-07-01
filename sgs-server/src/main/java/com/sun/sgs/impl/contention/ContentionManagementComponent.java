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
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.db.DeadlockException;
import com.sleepycat.db.LockNotGrantedException;

import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;


import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;



public class ContentionManagementComponent 
    implements NonDurableTransactionParticipant {

    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ContentionManagementComponent.
					   class.getName()));

    public static final int DEFAULT_TXNS_TO_KEEP = 200;

    private final long txnTimeout;
    
    Map<Transaction,TxnInfo> openTxns;

    ConcurrentBoundedMap<Transaction,TxnInfo> finishedTxns;
    

    TransactionProxy txnProxy;
    
    public ContentionManagementComponent(Properties appProperties,
					 TransactionProxy txnProxy) {
	this.txnProxy = txnProxy;
	PropertiesWrapper props = new PropertiesWrapper(appProperties);
	txnTimeout = props.getLongProperty(TransactionCoordinator.
					   TXN_TIMEOUT_PROPERTY,
					   TransactionCoordinatorImpl.
					   BOUNDED_TIMEOUT_DEFAULT);

	openTxns = new ConcurrentHashMap<Transaction,TxnInfo>();
	finishedTxns = 
	    new ConcurrentBoundedMap<Transaction,TxnInfo>(DEFAULT_TXNS_TO_KEEP);

	System.out.println(this + " created!");
    }

    public void abort(Transaction txn) { 
	// determin why
	
	//System.out.println(txn + " aborted");

	TxnInfo info = openTxns.remove(txn);
	info.end();

	String reason = null;

	if (info.getRunTime() > txnTimeout) {
	    reason = String.format("%s probably timed out for running %dms\n",
				   txn, info.getRunTime());
	}

	
	// keep a reference to the last transaction we saw in case we
	// need to determine the lag between the current transaction
	// and the newest one in the queue
	TxnInfo otherInfo = null;

	// see if any transactions intersect with this one
	for (Map.Entry<Transaction,TxnInfo> other : finishedTxns.entrySet()) {
	    // did any of them have shared resources?
	    
	    otherInfo = other.getValue();

	    if (info.contendsWith(otherInfo)) {

		reason = String.format("Task type %s with Transaction (id: %d) "
				       + "(try count %d) details:\n%s" 
				       + "was ABORTED due to conflicts with:\n"
				       + "Task type %s Transaction (id: %d) "
				       + "(try count: %d)\n%s\n",
				       info.taskType,
				       getID(txn), 
				       info.tryCount, 
				       info, 
				       otherInfo.taskType,
				       getID(other.getKey()), 
				       otherInfo.tryCount, 
				       otherInfo);
		break;
	    }
	}
	
	if (reason == null) {
	    Throwable t = txn.getAbortCause();
	    boolean wasLockRelated = false;
	    do {

		wasLockRelated =
		    t instanceof TransactionConflictException ||
		    t instanceof DeadlockException ||
		    t instanceof LockNotGrantedException ||

		    // NOTE: due a the contents of the LNGE containing
		    // a non-serializable Environment object, the TTE
		    // does not report it as the cause.  Therefore, we
		    // have to search the string for its name as a
		    // kludge.  See BdbEnvironment.convertException()
		    // for details.
		    (t instanceof TransactionTimeoutException &&
		     t.getMessage().contains("LockNotGrantedException"));

		t = t.getCause();
	    } while (!wasLockRelated && t != null);


	    if (wasLockRelated) {
		// if we saw a lock-related exception but somehow missed
		// finding out what other transaction caused it, then we
		// probably need to increase the bound on the number of
		// finished transactions.
		boolean increased = finishedTxns.adjustBound(10);

		reason = String.format("Task type %s with Transaction (id: %d) "
				       + "(try count %d) details:\n%swas "
				       + "aborted due to "
				       + "a data conflict with another " 
				       + "undetermined transaction%s\n",
				       info.taskType,
				       getID(txn), 
				       info.tryCount,
				       info,
				       (increased) ? 
				       " (increased txn backlog)" : "");
	    }
	    else {		
		reason = String.format("Task type %s with Transaction (id: %d) "
				       + "(try count %d) was aborted with "
				       + "exception:\n\t%s\n",
				       info.taskType,
				       getID(txn), 
				       info.tryCount, 				   
				       txn.getAbortCause());
	    }				   
	}

	// NOTE: It might be good to throw in some adaptive code here
	// to adjust the size of the bounded queue.  When the
	// application isn't doing much, 200 might be a waste.
	// However, when the application is overloaded, 200 might be
	// far to few to find the actual source of contention

	if (reason == null) {
	    System.out.printf("Task type %s with Transaction (id: %d) "
			      + "(try count %d) was aborted for reasons "
			      + "unknown to this component.\n",
			      info.taskType,
			      getID(txn), 
			      info.tryCount);
	}
	else {
	    // NOTE: perhaps decrease the bound by something related
	    //       to the difference in time between the last
	    //       transactionad this one?
	    System.out.println(reason);
	}

    
    
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

    public void registerTransaction(Transaction txn, String taskType,
				    int tryCount) {
	txn.join(this);
	// System.out.println(txn + " joined");
	openTxns.put(txn, new TxnInfo(taskType, tryCount));
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

    // NOTE: This class probably isn't completely thread safe anymore,
    //       but still works for the purposes of this component
    //       regardless
    private static class ConcurrentBoundedMap<K,V> {

	Queue<K> keys;
	
	Map<K,V> backingMap;

	int sizeBound;

	AtomicInteger size;

	static final int ABSOLUTE_MAX_SIZE = 1000; 

	public ConcurrentBoundedMap(int sizeBound) {
	    this.sizeBound = sizeBound;
	    size = new AtomicInteger(0);
	    keys = new ConcurrentLinkedQueue<K>();
	    backingMap = new ConcurrentHashMap<K,V>();
	}

	public boolean adjustBound(int delta) {
	    int i = sizeBound + delta;
	    if (i >= 0 && i <= ABSOLUTE_MAX_SIZE) {
		sizeBound = i;
		return true;
	    }
	    return false;
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

	int tryCount;

	public TxnInfo(String taskType, int tryCount) {
	    this.taskType = taskType;
	    this.tryCount = tryCount;

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
	    String objs = (oids.isEmpty()) ? "\n   oids: [NONE]\n" : "\n";

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
		    toString = ".toString() threw " + t.getClass().getName();
		}
		
		objs += 
		    String.format("%6d: %-30s desc: %-30s\n",
				  oid,
				  o.getClass().getSimpleName(),
				  toString);
	    }
	    String nms = (names.isEmpty()) ? "  names: [NONE]\n" : "";
	    if (names.size () > 20) {
		nms = names.size() + " names (elided)";
	    }
	    else {
		for (String name : names) {
		    nms += "  name: " + name + "\n";
		}
	    }

	    return objs + nms;
	}

	public String toString() {
	    return "Read Locks Held:" + readLockedObjects() 
		+ "Write Locks Held:" + writeLockedObjects(); 

	}

    }

}