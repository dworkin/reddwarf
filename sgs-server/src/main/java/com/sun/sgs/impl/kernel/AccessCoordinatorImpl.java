/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.AccessNotificationProxy;
import com.sun.sgs.kernel.AccessNotificationProxy.AccessType;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileCollector;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * A package-private implementation of {@code AccessCoordinator} that is
 * used by the system to track access to objects and handle any possible
 * contention. This implementation is also responsible for reporting
 * the access detail to the profiling system.
 */
class AccessCoordinatorImpl implements AccessCoordinator,
                                       NonDurableTransactionParticipant {

    // the map from active transactions to associated detail
    private final ConcurrentMap<Transaction,AccessedObjectsDetailImpl>
        txnMap = new ConcurrentHashMap<Transaction,AccessedObjectsDetailImpl>();

    // TODO: there may need to be maps for the active locks...these may
    // eventually move (in whole or in part) to the contention resolver,
    // but this seems like the right place at least to start them out.
    // At any rate, these aren't needed until contention is being managed,
    // since active transactions will not be the cause of failure

    // an optional backlog of completed transactions used for guessing at
    // past conflict...the queue is bounded if active, or null if backlog
    // checking is inactive
    // NOTE: this is here as an experiment, as it's not clear if it needs
    // to be in the coordinator, or could be moved to the profiling code,
    // or put somewhere else altogether

    private final Queue<AccessedObjectsDetailImpl> backlog;

    // system components
    private final TransactionProxy txnProxy;
    private final ProfileCollector profileCollector;
 
    /** 
     * Creates an instance of {@code AccessCoordinatorImpl}. 
     *
     * @throws IllegalArgumentException if <ol><li>{@code properties}
     *         is {@code}, or</li><li>{@code txnProxy} is {@code
     *         null}</li></ol>
     */
    AccessCoordinatorImpl(Properties properties, TransactionProxy txnProxy,
                          ProfileCollector profileCollector) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (txnProxy == null)
            throw new NullPointerException("Proxy cannot be null");
        // TODO: when Jane's updates are committed, also make sure that the
        // collector is not null

        String backlogProp = properties.getProperty("prop.name");
        if (backlogProp != null) {
            int size;
            try {
                size = Integer.parseInt(backlogProp);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Backlog size must be a " +
                                                   "number: " + backlogProp);
            }
            backlog = new LinkedBlockingQueue<AccessedObjectsDetailImpl>(size);
        } else {
            backlog = null;
        }

        this.txnProxy = txnProxy;
        this.profileCollector = profileCollector;
    }

    /*
     * Implement AccessCoordinator interface.
     */

    /** 
     * {@inheritDoc} 
     */
    public <T> AccessNotificationProxy<T> registerContentionSource
	(String sourceName, Class<T> contendedType) {

        return new AccessNotificationProxyImpl<T>(sourceName);
    }

    /**
     * {@inheritDoc} 
     */
    public Transaction getConflictingTransaction(Transaction txn) {
        // until we manage contention, there will never be an active
        // transaction to return
        return null;
    }

    /*
     * Implement NonDurableTransactionParticipant interface.
     */

    /**
     * {@inheritDoc} 
     */
    public boolean prepare(Transaction txn) {
        AccessedObjectsDetailImpl detail = txnMap.get(txn);
        // TODO: this is the last chance to abort because of contention,
        // when we're actually managing the contention
        detail.markCommitting();
        return false;
    }

    /**
     * {@inheritDoc} 
     */
    public void commit(Transaction txn) {
        reportDetail(txn, true);
    }

    /**
     * {@inheritDoc} 
     */
    public void prepareAndCommit(Transaction txn) {
        reportDetail(txn, true);
    }

    /**
     * {@inheritDoc} 
     */
    public void abort(Transaction txn) {
        reportDetail(txn, false);
    }

    /**
     * {@inheritDoc} 
     */
    public String getTypeName() {
        return getClass().getName();
    }

    /** 
     * Codifies the results of the provided transaction and reports
     * the information to the profiling system.
     *
     * @param txn a finished transaction
     * @param succeeded whether {@code txn} was successful (i.e. did
     *        not abort).
     */
    private void reportDetail(Transaction txn, boolean succeeded) {
        AccessedObjectsDetailImpl detail =
            txnMap.remove(txnProxy.getCurrentTransaction());

        // TODO: this should be replaced with a level check once Jane's
        // updates are ready
        if (profileCollector == null)
            return;

	// if the task failed try to determine why
        if (!succeeded) {

	    // mark the type with an initial guess of unknown and then
	    // try to refine it.
	    detail.conflictType = ConflictType.UNKNOWN;

	    // Currently, the only way to refine the cause is to
	    // search the transaction back log (if we have one) and
	    // look for any transaction that acquired the same locks.
	    //
            // NOTE: in the current system we'll never see a
            // transaction fail because of contention with another
            // active transaction, so currently this is only for
            // looking through a backlog, if that's enabled
	    if (backlog != null) {

		for (AccessedObjectsDetailImpl oldDetail : backlog) {
		    if (detail.conflictsWith(oldDetail)) {
			detail.setCause(ConflictType.ACCESS_NOT_GRANTED,
					oldDetail.getId());
			break;
		    }
		}
		
		// since we're keeping a backlog of transactions, add
		// this to the backlog.  If the backlog is full,
		// remove some of the oldest transactions until there
		// is room for this transaction.
		if (backlog != null) {
		    while (! backlog.offer(detail))
			backlog.poll();
		}
	    }
	}

        profileCollector.setAccessedObjectsDetail(detail);
    }

    /*
     * Package-private methods.
     */

    /** 
     * Notifies the coordinator that a new transaction is starting. 
     */
    void notifyNewTransaction(long requestedStartTime, int tryCount) {
        // NOTE: the parameters are here for the next step, where we want
        // input to decide how to resolve conflict
        Transaction txn = txnProxy.getCurrentTransaction();
        txn.join(this);
        txnMap.put(txn, new AccessedObjectsDetailImpl());
    }

    // TBD: there should be another version of the notifyNewTransaction
    // method that takes a specific resolution policy (once we get that
    // feature implemented)

    /*
     * Class implementations.
     */

    /** 
     * Private implementation of {@code AccessedObjectsDetail}. 
     */
    private static class AccessedObjectsDetailImpl implements AccessedObjectsDetail {

        private static final AtomicLong DETAIL_ID_COUNTER = new AtomicLong(0);

        private final long id;

        private final LinkedHashSet<AccessedObject> accessList;
	
	/**
	 * A separate set of the write locks obtained during this
	 * Detail's lifetime
	 */
	private final Set<AccessedObject> writes;

	private final Map<Object,Object> objIDtoDescription;

	private final AtomicBoolean committing;

        private boolean failed;

	private ConflictType conflictType;

        private long idOfConflictingTxn;

	AccessedObjectsDetailImpl() {

	    id = DETAIL_ID_COUNTER.incrementAndGet();

	    accessList = new LinkedHashSet<AccessedObject>();

	    objIDtoDescription = new HashMap<Object,Object>();

	    writes = new HashSet<AccessedObject>();

	    committing = new AtomicBoolean(false);

	    failed = false;

	    conflictType = ConflictType.NONE;

	    idOfConflictingTxn = 0;

	}
	
        /** 
	 * {@inheritDoc} 
	 */
        public long getId() {
            return id;
        }

        /** 
	 * {@inheritDoc} 
	 */
        public List<AccessedObject> getAccessedObjects() {
	    // make a copy of the set
            return new ArrayList<AccessedObject>(accessList);
        }

        /** 
	 * {@inheritDoc} 
	 */
        public boolean failedOnContention() {
            return failed;
        }

        /** 
	 * {@inheritDoc} 
	 */
        public ConflictType getConflictType() {
            return conflictType;
        }

        /** 
	 * {@inheritDoc}
	 */
        public long getContendingId() {
            return idOfConflictingTxn;
        }
	
        /** 
	 * Adds a the provided {@code AccessedObject} to the list of
	 * currently accessed objects.	 
	 */
        void addAccess(AccessedObject accessedObject) {
            accessList.add(accessedObject);
	    
	    // keep track of the write accesses in case we later need
	    // to determine whether this detail conflicted with
	    // another.
	    if (accessedObject.getAccessType().equals(AccessType.WRITE))
		writes.add(accessedObject);
        }

        /** 
	 * Maps the provided object Id to an annotation describing
	 * that object.
	 */ 
        void setDescription(Object objId, Object annotation) {
            objIDtoDescription.put(objId, annotation);
        }
	
	synchronized void setCause(ConflictType conflictReason, 
				   long idOfConflictingTxn) {
	    failed = true;
	    conflictType = conflictReason;
	    this.idOfConflictingTxn = idOfConflictingTxn;
	}

        /**
	 * Returns a given object's annotation or {@code null} if none exists.
	 */
	Object getDescription(Object objId) {
            return objIDtoDescription.get(objId);
        }

        /** 
	 * Marks this detail as having progressed past prepare(). 
	 */
	void markCommitting() {
            committing.set(true);
        }

        /** Reports whether the associated transaction has prepared. */
	boolean isCommitting() {
	    return committing.get();
        }

        boolean conflictsWith(AccessedObjectsDetailImpl other) {
	    
	    // A conflict occurs if two details have write sets that
	    // intersect, or if the write set of either set intersects
	    // with the other's read set.
	    //
	    // To minimize the number of operations, we determine
	    // which write set is smaller.  We then use the smaller of
	    // the two for checking item presence in the other's write
	    // and read sets.
	    Set<AccessedObject> fewerWrites = null;
	    Set<AccessedObject> moreWrites  = null;
	    Set<AccessedObject> reads  = null; // read set for fewerWrites
	    Set<AccessedObject> reads2 = null; // read set for moreWrites

	    if (writes.size() < other.writes.size()) {
		fewerWrites = writes;
		moreWrites = other.writes;
		reads = other.accessList;
		reads2 = accessList;
	    }
	    else {
		fewerWrites = other.writes;
		moreWrites = writes;
		reads = accessList;
		reads2 = other.accessList;
	    }
	    
	    for (AccessedObject o : fewerWrites) {
		if (moreWrites.contains(o) || reads.contains(o))
		    return true;		
	    }

	    for (AccessedObject o : moreWrites) {
		if (reads2.contains(o))
		    return true;
	    }
	   

            return false;
        }
    }

    /** 
     * Private implementation of {@code AccessedObject}. 
     */
    private static class AccessedObjectImpl implements AccessedObject {

        private final Object objId;

        private final AccessType type;

        private final String source;

        private final AccessedObjectsDetailImpl parent;

        /** 
	 * Creates an instance of {@code AccessedObjectImpl}. 
	 *
	 * @throws IllegalArgumentException if any of the provided
	 *         parameters are {@code null}.
	 */
        AccessedObjectImpl(Object objId, AccessType type, String source,
                           AccessedObjectsDetailImpl parent) {
	    if (objId == null) 
		throw new IllegalArgumentException("objId must not be null");
	    else if (type == null) 
		throw new IllegalArgumentException("type must not be null");
	    else if (source == null) 
		throw new IllegalArgumentException("source must not be null");
	    else if (parent == null) 
		throw new IllegalArgumentException("parent must not be null");
	    
            this.objId = objId;
            this.type = type;
            this.source = source;
            this.parent = parent;
        }

	/**
	 * Returns {@code true} if the other object is an instance of
	 * {@code AccessedObjectImpl} and has the same object Id and
	 * access type.
	 */
	public boolean equals(Object o) {
	    if (o instanceof AccessedObjectImpl) {
		AccessedObjectImpl a = (AccessedObjectImpl)o;
		return objId.equals(a.objId) && type.equals(a.type);
	    }
	    return false;
	}

        /**
	 * {@inheritDoc} 
	 */
        public Object getObject() {
            return objId;
        }
	
        /** 
	 * {@inheritDoc} 
	 */
        public AccessType getAccessType() {
            return type;
        }

        /** 
	 * {@inheritDoc} 
	 */
        public Object getDescription() {
            return parent.getDescription(objId);
        }
	
        /** 
	 * {@inheritDoc} 
	 */
        public String getSource() {
            return source;
        }

	/**
	 * Returns the hash code of the object Id xor'd with the hash
	 * code of the access type.
	 */
	public int hashCode() {
	    return objId.hashCode() ^ type.hashCode();
	}
    }

    /** 
     * Private implementation of {@code AccessNotifier}. 
     */
    private class AccessNotificationProxyImpl<T> implements AccessNotificationProxy<T> {
     
	private final String source;
        
	/**
	 * Creates an instance of {@code AccessNotificationProxy}. 
	 */
        AccessNotificationProxyImpl(String source) {
            this.source = source;
        }

        /** 
	 * {@inheritDoc} 
	 */
        public void notifyObjectAccess(T objId, AccessType type) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.addAccess(new AccessedObjectImpl(objId, type, source,
                                                    detail));
            // TODO: when we're managing contention, this step should
            // also track the requested access, seeing if this could
            // cause failure to the calling or other transaction
        }

        /** 
	 * {@inheritDoc} 
	 */
	public void notifyObjectAccess(T objId, AccessType type, 
				       Object description) {
	    AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
	    detail.addAccess(new AccessedObjectImpl(objId, type, source,
						    detail));
            detail.setDescription(objId, description);
	}
	

        /** 
	 * {@inheritDoc} 
	 */
	public void setObjectDescription(T objId, Object description) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.setDescription(objId, description);
        }
    }
}
