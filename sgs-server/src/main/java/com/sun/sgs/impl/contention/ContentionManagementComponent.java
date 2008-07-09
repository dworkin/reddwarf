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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
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

import com.sun.sgs.contention.ContentionReport;
import com.sun.sgs.contention.ContentionReport.ConflictType;
import com.sun.sgs.contention.LockInfo;
import com.sun.sgs.contention.LockInfo.LockType;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.profile.ProfileContention;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

/**
 * TODO.
 *
 * This class detects contention by maintaining a backlog of recently
 * committed transactions.  When a running transaction aborts, this
 * backlog is searched to find the most recent transaction that has
 * any overlapping source of contention.  This class supports two
 * configurable properties with respect to this backlog:
 *
 * <p><dl style="margin-left: 1em">                                                             
 *                                                                                              
 * <dt> <i>Property:</i> <b>                                                                    
 *      {@code com.sun.sgs.impl.contention.txn.buffer.size}                                               
 *      </b><br>                                                                                
 *      <i>Default:</i> 200.                                                     
 *                                                                                              
 * <dd style="padding-top: .5em"> This properties specifies the
 * initial buffer size for transactions to keep in the backlog.  The
 * actual size of the buffer may increase over time; a high system
 * throughput requires that a larger number of transaction be stored
 * at one time.<p></dd></dl>
 *                                                                                              
 * <p><dl style="margin-left: 1em">                                                             
 *                                                                                              
 * <dt> <i>Property:</i> <b>                                                                    
 *      {@code com.sun.sgs.impl.contention.max.txn.buffer.size}                                               
 *      </b><br>                                                                                
 *      <i>Default:</i> 1000.
 *                                                                                              
 * <dd style="padding-top: .5em"> This property specifies the absolute
 * maximum number of transactions to keep in the backlog.  Setting
 * this number too high will cause an increased search time for any
 * spurious aborts for whom a source of contention cannot be found.
 * Setting the bound too low can cause the contention manager to be
 * unable to find the conflicting transaction during a high-throughput
 * processing period.  If this property is set lower than {@code
 * com.sun.sgs.impl.contention.txn.buffer.size} then the larger value
 * is used as the maximum.<p></dd></dl>
 *                                                                                              
 * @see ProfileReport#getContentionReport()
 * @see ContentionReport
 */
public class ContentionManagementComponent 
    implements NonDurableTransactionParticipant {

    public static final String TXN_BUFFER_SIZE_PROPERTY =
	"com.sun.sgs.impl.contention.txn.buffer.size";

    public static final String MAX_TXN_BUFFER_SIZE_PROPERTY =
	"com.sun.sgs.impl.contention.max.txn.buffer.size";

    private static final Logger logger =
        Logger.getLogger(ContentionManagementComponent.class.getName());

    private static final int DEFAULT_TXN_BUFFER_SIZE = 200;

    private static final int DEFAULT_MAX_TXN_BUFFER_SIZE = 1000;
    
    Map<Transaction,TxnInfo> openTxns;

    ConcurrentBoundedMap<Transaction,TxnInfo> finishedTxns;
    
    ProfileContention contentionReporter;
    

    TransactionProxy txnProxy;
    
    public ContentionManagementComponent(Properties appProperties,
					 TransactionProxy txnProxy,
					 ProfileCollectorImpl profileConsumer) {
	this.txnProxy = txnProxy;
	PropertiesWrapper props = new PropertiesWrapper(appProperties);
	
	int txnBufferSize = props.getIntProperty(TXN_BUFFER_SIZE_PROPERTY,
						 DEFAULT_TXN_BUFFER_SIZE);
	
	// use the larger of the two values for the upper bound
	int maxTxnBufferSize = 
	    Math.max(props.getIntProperty(MAX_TXN_BUFFER_SIZE_PROPERTY,
					  DEFAULT_MAX_TXN_BUFFER_SIZE),
		     txnBufferSize);	       

	openTxns = new ConcurrentHashMap<Transaction,TxnInfo>();
		
	finishedTxns = 
	    new ConcurrentBoundedMap<Transaction,TxnInfo>(txnBufferSize,
							  maxTxnBufferSize);	
	contentionReporter = 
	    profileConsumer.registerContentionReporter(this.getClass());
    }

    public void abort(Transaction txn) { 
	
	TxnInfo info = openTxns.remove(txn);
	info.end();
	
	Transaction conflicting = null;
	TxnInfo otherInfo = null;
	Set<LockInfo> contendedLocks = new HashSet<LockInfo>();

	// see if any of the buffered transactions have locks that
	// overlap with this one
	for (Map.Entry<Transaction,TxnInfo> other : finishedTxns.entrySet()) {
	 
	    otherInfo = other.getValue();
	    if (info.contendsWith(otherInfo)) {
		conflicting = other.getKey();		
		contendedLocks = info.getContendedLocks(otherInfo);
		break;	    
	    }
	}       

	// examine the cause of the abort to see if we can figure out
	// what caused the conflict
	Throwable t = txn.getAbortCause();
	ConflictType abortReason = ConflictType.UNKNOWN;
	    
	// NOTE: due to the nest nature of the transaction exceptions,
	// we need search up the chain them to find the root cause.
	// In practice, this is never more than 2-3 Exceptions
	do {	    	    
	    // NOTE: due a the contents of the LNGE and DE containing
	    // a non-serializable Environment object, the TTE does not
	    // report it as the cause.  Therefore, we have to search
	    // the string for its name as a kludge.  See
	    // BdbEnvironment.convertException() for details.
	    if (t.getMessage().contains("DeadlockException"))
		abortReason = ConflictType.DEADLOCK;
	    else if (t.getMessage().contains("LockNotGrantedException"))
		abortReason = ConflictType.LOCK_NOT_GRANTED;
	    
	    t = t.getCause();
	} while (abortReason.equals(ConflictType.UNKNOWN) && t != null);
    

	// if we saw a lock-related exception but somehow missed
	// finding out what other transaction caused it, then we
	// probably need to increase the bound on the number of
	// finished transactions.
	if (conflicting == null && 
	    !(abortReason.equals(ConflictType.UNKNOWN))) {
	    
	    boolean increased = finishedTxns.adjustBound(10);
	    
	    if (increased)
		logger.fine("increased size of transaction backlog for the "
			    + "contention managament to " 
			    + finishedTxns.getBound());
	}

	// REMINDER: It might be good to throw in some adaptive code
	// here to reduce the size of the bounded queue.  When the
	// application isn't doing much, 200 might be a waste.
	// However, when the application is overloaded, 200 might be
	// far to few to find the actual source of contention

	// If we were able to find a conflicting transaction, report
	// the data about it, otherwise insert dummy data to indicate
	// a lack of information
	ContentionReport report = (conflicting != null) 
	    ? new ContentionReportImpl(getID(txn),
				       info.taskType,
				       info.getAcquiredLocks(),
				       contendedLocks,
				       abortReason,
				       getID(conflicting),
				       otherInfo.taskType)
	    : new ContentionReportImpl(getID(txn),
				       info.taskType,
				       info.getAcquiredLocks(),
				       contendedLocks,
				       abortReason,
				       -1,
				       "Unknown");	

	contentionReporter.addReport(report);    
    }

    public void associate(Long oid, Object o) {
	Transaction txn = getCurrentTransaction();
	if (txn == null)
	    return;
	TxnInfo info = openTxns.get(txn);
	info.associate(oid, o);
    }
       
    public void associate(String name, Object o) {
	Transaction txn = getCurrentTransaction();
	if (txn == null)
	    return;
	TxnInfo info = openTxns.get(txn);
	info.associate(name, o);
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

    public String getTypeName() { 
	return ContentionManagementComponent.class.getName();
    } 

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

	private final Queue<K> keys;
	
	private final Map<K,V> backingMap;

	private final AtomicInteger size;

	private int sizeBound;

	private final int maxSizeBound;

	public ConcurrentBoundedMap(int sizeBound, int maxSizeBound) {
	    this.sizeBound = sizeBound;
	    this.maxSizeBound = maxSizeBound;
	    size = new AtomicInteger(0);
	    keys = new ConcurrentLinkedQueue<K>();
	    backingMap = new ConcurrentHashMap<K,V>();
	}

	public boolean adjustBound(int delta) {
	    int i = sizeBound + delta;
	    if (i >= 0 && i <= maxSizeBound) {
		sizeBound = i;
		return true;
	    }
	    return false;
	}

	public V get(Object o) {
	    return backingMap.get(o);
	}

	public int getBound() {
	    return sizeBound;
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

    /**
     *
     *
     */
    private static class TxnInfo {

	private long startTime;
	
	private long endTime;
	
	private final Set<LockInfoImpl> acquiredLocksInOrder;

	private final List<LockInfoImpl> allLocks;

	private final Map<Long,LockInfoImpl> readLocks;
	
	private final Map<Long,LockInfoImpl> writeLocks;

	private final Map<String,LockInfoImpl> nameReadLocks;

	private final Map<String,LockInfoImpl> nameWriteLocks;
	
	private final Map<Long,Object> oidsToManagedObjects;

	private final Map<String,Object> namesToManagedObjects;

	private final String taskType;

	private final int tryCount;

	public TxnInfo(String taskType, int tryCount) {
	    this.taskType = taskType;
	    this.tryCount = tryCount;

	    startTime = System.currentTimeMillis();
	    endTime = -1;
	    
	    readLocks = new HashMap<Long,LockInfoImpl>();
	    writeLocks = new HashMap<Long,LockInfoImpl>();
	    nameReadLocks = new HashMap<String,LockInfoImpl>();
	    nameWriteLocks = new HashMap<String,LockInfoImpl>();
	    oidsToManagedObjects = new HashMap<Long,Object>();
	    namesToManagedObjects = new HashMap<String,Object>();
	    acquiredLocksInOrder = new LinkedHashSet<LockInfoImpl>();
	    allLocks = new LinkedList<LockInfoImpl>();
	}

	public void addReadLock(Long oid) {
	    LockInfoImpl readLock = new LockInfoImpl(LockType.READ,
						     BigInteger.valueOf(oid));
	    readLocks.put(oid, readLock);
	    acquiredLocksInOrder.add(readLock);
	}

	public void addWriteLock(Long oid) {
	    LockInfoImpl writeLock = new LockInfoImpl(LockType.WRITE, 
						      BigInteger.valueOf(oid));
	    writeLocks.put(oid, writeLock);
	    acquiredLocksInOrder.add(writeLock);
	}

	public void addReadLockOnName(String name) {
	    LockInfoImpl readLock = new LockInfoImpl(LockType.READ, name);
	    nameReadLocks.put(name, readLock);
	    acquiredLocksInOrder.add(readLock);
	}

	public void addWriteLockOnName(String name) {
	    LockInfoImpl writeLock = new LockInfoImpl(LockType.WRITE, name);
	    nameWriteLocks.put(name, writeLock);
	    acquiredLocksInOrder.add(writeLock);
	}

	public void associate(Long oid, Object o) {
	    oidsToManagedObjects.put(oid, o);
	    LockInfoImpl info = readLocks.get(oid);
	    if (info != null)
		info.setObject(o);
	    info = writeLocks.get(oid);
	    if (info != null)
		info.setObject(o);
	}

	public void associate(String name, Object o) {
	    namesToManagedObjects.put(name, o);
	    LockInfoImpl info = nameReadLocks.get(name);
	    if (info != null)
		info.setObject(o);
	    info = nameWriteLocks.get(name);
	    if (info != null)
		info.setObject(o);
	}

	public void end() {
	    endTime = System.currentTimeMillis();
	}

	public long getRunTime() {
	    return endTime - startTime;
	}

	public List<LockInfo> getAcquiredLocks() {	    	    
	    return new ArrayList<LockInfo>(acquiredLocksInOrder);
	}
	
	public boolean contendsWith(TxnInfo other) {

	    // REMINDER: this might be sped up a little by ordering
	    // the sets according to which is smaller
	    for (Long oid : other.writeLocks.keySet()) {
		if (readLocks.containsKey(oid) ||
		    writeLocks.containsKey(oid))
		    return true;
	    }

	    for (Long oid : writeLocks.keySet()) {
		if (other.readLocks.containsKey(oid))
		    return true;
	    }

	    for (String name : other.nameWriteLocks.keySet()) {
		if (nameReadLocks.containsKey(name) ||
		    nameWriteLocks.containsKey(name))
		    return true;
	    }

	    for (String name : nameWriteLocks.keySet()) {
		if (other.nameReadLocks.containsKey(name))
		    return true;
	    }

	    return false;
	}

	public Set<LockInfo> getContendedLocks(TxnInfo other) {

	    Set<LockInfo> contended = new HashSet<LockInfo>();

	    for (Long oid : readLocks.keySet()) {
		if (other.writeLocks.containsKey(oid))
		    contended.add(readLocks.get(oid));
	    }

	    for (Long oid : other.readLocks.keySet()) {
		if (writeLocks.containsKey(oid))
		    contended.add(writeLocks.get(oid));
	    }

	    for (Long oid : writeLocks.keySet()) {
		if (other.writeLocks.containsKey(oid))
		    contended.add(writeLocks.get(oid));
	    }

	    for (String name : nameReadLocks.keySet()) {
		if (other.nameWriteLocks.containsKey(name))
		    contended.add(nameReadLocks.get(name));
	    }

	    for (String name : other.nameReadLocks.keySet()) {
		if (nameWriteLocks.containsKey(name))
		    contended.add(nameWriteLocks.get(name));
	    }

	    for (String name : other.nameWriteLocks.keySet()) {
		if (nameWriteLocks.containsKey(name))
		    contended.add(nameWriteLocks.get(name));
	    }

	    return contended;
	}

	public String toString() {
	    return "TxnInfo(type: " + taskType + ")";

	}

    }

}