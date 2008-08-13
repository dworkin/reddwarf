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
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileCollector;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import java.math.BigInteger;

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
 * conflict. This implementation is also responsible for reporting
 * the access detail to the profiling system.
 * <p>
 * This implementation provides the option to keep detail on a backlog of
 * past transactions to discover what may have caused conflict. This is
 * currently only useful for {@code ProfileListener}s that wish to diplay
 * this detail. By default this backlog tracking is disabled. To enable,
 * set the {@code com.sun.sgs.impl.kernel.AccessCoordinatorImpl.queue.size}
 * property to some positive value indicating the length of backlog to use.
 * Note that with each transaction failure this backlog will be scanned
 * to find a conflicting transaction, so a larger backlog may provide more
 * detail about failure but will also be more compute-intensive.
 */
class AccessCoordinatorImpl implements AccessCoordinator,
                                       NonDurableTransactionParticipant {

    // the map from active transactions to associated detail
    private final ConcurrentMap<Transaction,AccessedObjectsDetailImpl>
        txnMap = new ConcurrentHashMap<Transaction,AccessedObjectsDetailImpl>();

    // TODO: there may need to be maps for the active locks...these may
    // eventually move (in whole or in part) to the conflict resolver,
    // but this seems like the right place at least to start them out.
    // At any rate, these aren't needed until conflict is being managed,
    // since active transactions will not be the cause of failure

    // an optional backlog of completed transactions used for guessing at
    // past conflict...the queue is bounded if active, or null if backlog
    // checking is inactive
    // NOTE: this is here as an experiment, as it's not clear if it needs
    // to be in the coordinator, or could be moved to the profiling code,
    // or put somewhere else altogether
    private final Queue<AccessedObjectsDetailImpl> backlog;

    // the property to set the size of the backlog
    static final String BACKLOG_QUEUE_PROPERTY =
        AccessCoordinatorImpl.class.getName() + ".queue.size";

    // system components
    private final TransactionProxy txnProxy;
    private final ProfileCollector profileCollector;
 
    /**
     * Creates an instance of {@code AccessCoordinatorImpl}.
     *
     * @throws IllegalArgumentException if the requested backlog queue size
     *                                  is not a valid number greater than 0
     */
    AccessCoordinatorImpl(Properties properties, TransactionProxy txnProxy,
                          ProfileCollector profileCollector) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (txnProxy == null)
            throw new NullPointerException("Proxy cannot be null");
        if (profileCollector == null)
            throw new NullPointerException("Collector cannot be null");

        String backlogProp = properties.getProperty(BACKLOG_QUEUE_PROPERTY);
        if (backlogProp != null) {
            try {
                backlog = new LinkedBlockingQueue
                    <AccessedObjectsDetailImpl>(Integer.parseInt(backlogProp));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Backlog size must be a " +
                                                   "number: " + backlogProp);
            }
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
    public <T> AccessReporter<T> registerAccessSource
	(String sourceName, Class<T> objectIdType)
    {
        return new AccessReporterImpl<T>(sourceName);
    }

    /**
     * {@inheritDoc} 
     */
    public Transaction getConflictingTransaction(Transaction txn) {
        // until we manage conflict, there aren't really any active
        // transactions to return..
        return null;
    }

    /**
     * TODO: Notes for when we look at conflict reolution...
     * The idea here is that a resolver needs to know who else might be
     * interested in a given object...but is this the right approach? The
     * reasoning here is that the Coodrinator should implement basic
     * conflict and deadlock detection so that each resolver doesn't have
     * to, but deadlock may depend on how resolution is done. So, maybe
     * the resolver is responsible for this, in which case it needs to
     * know about each access, not just possible conclit cases. Does
     * this suggest that the Coordinator just tracks access, reports
     * profiling, etc. but passes on all access requests to the resolver?
     * ...
     * Maybe all requests get pased on, but the Coordinator implements a
     * utility method to see if there is basic conflict happening?
     */
    //public List<> getCurrentReaders(Object obj);
    //public List<> getCurrentWriters(Object obj);

    /*
     * Implement NonDurableTransactionParticipant interface.
     */

    /**
     * {@inheritDoc} 
     */
    public boolean prepare(Transaction txn) {
        AccessedObjectsDetailImpl detail = txnMap.get(txn);
        // TODO: this is the last chance to abort because of conflict,
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
        AccessedObjectsDetailImpl detail = txnMap.remove(txn);

	// if the task failed try to determine why
        if (! succeeded) {
	    // mark the type with an initial guess of unknown and then
	    // try to refine it.
	    detail.conflictType = ConflictType.UNKNOWN;

            // NOTE: in the current system we don't really see transactions
            // fail because of conflict with another active transaction,
            // so currently this is only for looking through a backlog
	    if (backlog != null) {
                // look through the backlog for a conflict
		for (AccessedObjectsDetailImpl oldDetail : backlog) {
		    if (detail.conflictsWith(oldDetail)) {
			detail.setConflict(ConflictType.ACCESS_NOT_GRANTED,
                                           oldDetail);
			break;
		    }
		}
	    }
	}

        // if we're keeping a backlog, then add the reported detail...if
        // the backlog is full, then evict old data until there is room
        if (backlog != null) {
            while (! backlog.offer(detail))
                backlog.poll();
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

    // NOTE: there will be another version of the notifyNewTransaction
    // method that takes a specific resolution policy (once we get that
    // feature implemented)

    /*
     * Class implementations.
     */

    /** Private implementation of {@code AccessedObjectsDetail}. */
    private class AccessedObjectsDetailImpl implements AccessedObjectsDetail
    {
        // the id of the transaction for this detail
        private final BigInteger txnId =
            new BigInteger(txnProxy.getCurrentTransaction().getId());

        // the ordered set of accesses, and a separate set of just the writes
        private final LinkedHashSet<AccessedObject> accessList =
            new LinkedHashSet<AccessedObject>();
	private final Set<AccessedObject> writes =
            new HashSet<AccessedObject>();

        // any provided object descriptions
	private final Map<Object,Object> objIDtoDescription =
            new HashMap<Object,Object>();

        // whether the transaction has already proceeded past prepare
	private final AtomicBoolean committing = new AtomicBoolean(false);

        // information about why the transaction failed
        private boolean failed = false;
	private ConflictType conflictType = ConflictType.NONE;
        private BigInteger idOfConflictingTxn = null;

        /** Implement AccessObjectsDetail. */
	
        /** {@inheritDoc} */
        public List<AccessedObject> getAccessedObjects() {
            return new ArrayList<AccessedObject>(accessList);
        }
        /** {@inheritDoc} */
        public ConflictType getConflictType() {
            return conflictType;
        }
        /** {@inheritDoc} */
        public BigInteger getConflictingId() {
            return idOfConflictingTxn;
        }
	
        /** Adds an {@code AccessedObject} to the list of accessed objects. */
        void addAccess(AccessedObject accessedObject) {
            accessList.add(accessedObject);
	    
	    // keep track of the write accesses in case we later need
	    // to determine whether this detail conflicted with
	    // another.
	    if (accessedObject.getAccessType().equals(AccessType.WRITE))
		writes.add(accessedObject);
        }

        /** Maps the provided object Id to a description. */
        void setDescription(Object objId, Object annotation) {
            objIDtoDescription.put(objId, annotation);
        }

        /** Sets the cause and source of conflict for this access detail. */
	synchronized void setConflict(ConflictType conflictReason, 
                                      AccessedObjectsDetailImpl conflicting) {
	    failed = true;
	    conflictType = conflictReason;
	    this.idOfConflictingTxn = conflicting.txnId;
	}

        /** Returns a given object's annotation or {@code null}. */
	Object getDescription(Object objId) {
            return objIDtoDescription.get(objId);
        }

        /** Marks this detail as having progressed past prepare(). */
	void markCommitting() {
            committing.set(true);
        }

        /** Reports whether the associated transaction has prepared. */
	boolean isCommitting() {
	    return committing.get();
        }

        /** Checks if the given detail conflicts with this detail. */
        boolean conflictsWith(AccessedObjectsDetailImpl other) {

	    if (other == null)
		return false;
	    
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

        /** Creates an instance of {@code AccessedObjectImpl}. */
        AccessedObjectImpl(Object objId, AccessType type, String source,
                           AccessedObjectsDetailImpl parent) {
	    if (objId == null) 
		throw new NullPointerException("objId must not be null");
	    else if (type == null) 
		throw new NullPointerException("type must not be null");
	    else if (source == null) 
		throw new NullPointerException("source must not be null");
	    else if (parent == null) 
		throw new NullPointerException("parent must not be null");
	    
            this.objId = objId;
            this.type = type;
            this.source = source;
            this.parent = parent;
        }

        /** Implement AccessedObject. */

        /** {@inheritDoc} */
        public Object getObjectId() {
            return objId;
        }
        /** {@inheritDoc} */
        public AccessType getAccessType() {
            return type;
        }
        /** {@inheritDoc} */
        public Object getDescription() {
            return parent.getDescription(objId);
        }
        /** {@inheritDoc} */
        public String getSource() {
            return source;
        }

        /** Support comparison checking. */

        /**
	 * Returns {@code true} if the other object is an instance of
	 * {@code AccessedObjectImpl} and has the same object Id and
	 * access type.
	 */
	public boolean equals(Object o) {
	    if ((o != null) && (o instanceof AccessedObjectImpl)) {
		AccessedObjectImpl a = (AccessedObjectImpl)o;
		return objId.equals(a.objId) && type.equals(a.type);
	    }
	    return false;
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
     * Private implementation of {@code AccessNotifier}. Note that once we
     * start managing conflict then the {@code notifyObjectAccess} calls
     * will need to start tracking access and check that this doesn't cause
     * some transaction to fail.
     * <p>
     * TODO: until conflict management is implemented, this only serves
     * to provide detail to the profiling stream, so these methods should
     * probably only keep this data if the profiling level is high enough.
     */
    private class AccessReporterImpl<T> implements AccessReporter<T> {
	private final String source;
	/** Creates an instance of {@code AccessReporter}. */
        AccessReporterImpl(String source) {
            this.source = source;
        }
        /** {@inheritDoc} */
        public void reportObjectAccess(T objId, AccessType type) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.addAccess(new AccessedObjectImpl(objId, type, source,
                                                    detail));
        }
        /** {@inheritDoc} */
	public void reportObjectAccess(T objId, AccessType type, 
				       Object description) {
	    AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
	    detail.addAccess(new AccessedObjectImpl(objId, type, source,
						    detail));
            detail.setDescription(objId, description);
	}
        /** {@inheritDoc} */
	public void setObjectDescription(T objId, Object description) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.setDescription(objId, description);
        }
    }
}
