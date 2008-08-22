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

    /**
     * The map from active transactions to associated detail
     */
    private final ConcurrentMap<Transaction,AccessedObjectsDetailImpl>
        txnMap = new ConcurrentHashMap<Transaction,AccessedObjectsDetailImpl>();

    // TODO: there may need to be maps for the active locks...these may
    // eventually move (in whole or in part) to the conflict resolver,
    // but this seems like the right place at least to start them out.
    // At any rate, these aren't needed until conflict is being managed,
    // since active transactions will not be the cause of failure

    // NOTE: this is here as an experiment, as it's not clear if it needs
    // to be in the coordinator, or could be moved to the profiling code,
    // or put somewhere else altogether
    /**
     * An optional backlog of completed transactions used for
     * identifying a past conflict.  The queue is bounded if by the
     * value set in the {@link #BACKLOG_QUEUE_PROPERTY}, or {@code
     * null} by default.
     */
    private final Queue<AccessedObjectsDetailImpl> backlog;

    /**
     * The property to set the size of the backlog and enable
     * transaction conflict detail reporting.  The value set by this
     * property must be non-negative.
     */
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
                                                   "positive number: "
						   + backlogProp);
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
	if (sourceName == null)
	    throw new NullPointerException("source name cannot be null");
	if (objectIdType == null)
	    throw new NullPointerException("objectIdType cannot be null");
        return new AccessReporterImpl<T>(sourceName);
    }

    /**
     * {@inheritDoc} 
     */
    public Transaction getConflictingTransaction(Transaction txn) {
	if (txn == null)
	    throw new NullPointerException("txn cannot be null");

        // until we manage conflict, there aren't really any active
        // transactions to return..
        return null;
    }

    /**
     * TODO: Notes for when we look at conflict reolution...
     * The idea here is that a resolver needs to know who else might be
     * interested in a given object...but is this the right approach? The
     * reasoning here is that the Coordinator should implement basic
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
        detail.markPrepared();
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

	/**
	 * The ordered set of accesses for all sources, which includes
	 * both reads and writes
	 */
	private final LinkedHashSet<AccessedObject> accessList =
             new LinkedHashSet<AccessedObject>();
	
	/**
	 * The list of write accesses that occurred during the
	 * transaction.  We use a {@code List} here to improve
	 * iteration efficiency in the {@code conflictsWith} method.
	 * This list is guaranteed not to have any duplicates in it.
	 */
 	private final List<AccessedObject> writes =
             new ArrayList<AccessedObject>();

	/**
	 * The mapping from a source to a second mapping from all of
	 * that sources's Ids to their descriptions.  This secondary
	 * mapping is used to prevent collisions between multiple
	 * sources that use the same Id.
	 */
	private final Map<String,Map<Object,Object>> sourceToObjIdAndDescription
	    = new HashMap<String,Map<Object,Object>>();

        // whether the transaction has already proceeded past prepare
	private final AtomicBoolean prepared = new AtomicBoolean(false);

        // information about why the transaction failed.
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
	    boolean added = accessList.add(accessedObject);

	    // if this is the first time the object has been accessed,
	    // we may need to add its id to the set of descriptions,
	    // and if it was a write, add it to the list.
	    if (added) {
		String source = accessedObject.getSource();
		Map<Object,Object> idToDescription = 
		    sourceToObjIdAndDescription.get(source);
		if (idToDescription == null) {
		    idToDescription = new HashMap<Object,Object>();
		    sourceToObjIdAndDescription.put(source, idToDescription);
		}
		// if we didn't already have a description set for
		// this object, put its Id in the map so we have a
		// recorded access of it.  We use the keyset of this
		// map when checking for conflicts, so every access
		// needs to be recorded in it
		Object objId = accessedObject.getObjectId();
		if (!idToDescription.containsKey(objId)) {
		    idToDescription.put(objId, null);		    
		}
		
		// keep track of the write accesses in case we later
		// need to determine whether this detail conflicted
		// with another.
		if (accessedObject.getAccessType().equals(AccessType.WRITE)) {
		    writes.add(accessedObject);
		}
	    }
	}
	    
        /** Maps the provided object Id to a description. */
        void setDescription(String source, Object objId, Object description) {
	    Map<Object,Object> objIdToDescription = 
		sourceToObjIdAndDescription.get(source);
	    if (objIdToDescription == null) {
		objIdToDescription = new HashMap<Object,Object>();
		sourceToObjIdAndDescription.put(source, objIdToDescription);
	    }

	    // if the Id didn't already have a description, add one
	    if (objIdToDescription.get(objId) == null)
		objIdToDescription.put(objId, description);
        }

        /** Sets the cause and source of conflict for this access detail. */
	synchronized void setConflict(ConflictType conflictReason, 
                                      AccessedObjectsDetailImpl conflicting) {
	    failed = true;
	    conflictType = conflictReason;
	    this.idOfConflictingTxn = conflicting.txnId;
	}

        /** Returns a given object's annotation or {@code null}. */
	Object getDescription(String source, Object objId) {
	    Map<Object,Object> objIdToDescription = 
		sourceToObjIdAndDescription.get(source);
            return (objIdToDescription == null) 
		? null : objIdToDescription.get(objId);
        }

        /** Marks this detail as having progressed past prepare(). */
	void markPrepared() {
            prepared.set(true);
        }

        /** Reports whether the associated transaction has prepared. */
	boolean isPrepared() {
	    return prepared.get();
        }

        /** Checks if the given detail conflicts with this detail. */
        boolean conflictsWith(AccessedObjectsDetailImpl other) {

	    if (other == null)
		return false;

	    // A conflict occurs if two details have write sets that
	    // intersect, or if the write set of either set intersects
	    // with the other's read set.  We therefore iterate over
	    // each detail's write set and check if the second detail
	    // had a source that accessed an object with the same key
	    // as the write access from the first detail.	    
	    for (AccessedObject o : writes) {

		Map<Object,Object> objIdToDescription = 
		    other.sourceToObjIdAndDescription.get(o.getSource());

		if (objIdToDescription != null && 
		    objIdToDescription.containsKey(o.getObjectId()))
		    return true;		
	    }

	    for (AccessedObject o : other.writes) {

		Map<Object,Object> objIdToDescription = 
		    sourceToObjIdAndDescription.get(o.getSource());

		if (objIdToDescription != null && 
		    objIdToDescription.containsKey(o.getObjectId()))
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

        /* Implement AccessedObject. */

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
            return parent.getDescription(source, objId);
        }
        /** {@inheritDoc} */
        public String getSource() {
            return source;
        }

        /* Support comparison checking. */

        /**
	 * Returns {@code true} if the other object is an instance of
	 * {@code AccessedObjectImpl} and has the same object Id and
	 * access type.
	 */
	public boolean equals(Object o) {
	    if ((o != null) && (o instanceof AccessedObjectImpl)) {
		AccessedObjectImpl a = (AccessedObjectImpl)o;
		return objId.equals(a.objId) && type.equals(a.type) &&
		    source.equals(a.source);
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
	    if (source == null)
		throw new NullPointerException("source cannot be null");
            this.source = source;
        }

        /** {@inheritDoc} */
        public void reportObjectAccess(Transaction txn, T objId, AccessType type) {
	    if (txn == null)
		throw new NullPointerException("txn cannot be null");
	    if (objId == null)
		throw new NullPointerException("objId cannot be null");
	    if (type == null)
		throw new NullPointerException("type cannot be null");


            AccessedObjectsDetailImpl detail = txnMap.get(txn);
            detail.addAccess(new AccessedObjectImpl(objId, type, source,
                                                    detail));
        }

        /** {@inheritDoc} */
	public void reportObjectAccess(Transaction txn, T objId, AccessType type, 
				       Object description) {
	    if (txn == null)
		throw new NullPointerException("txn cannot be null");
	    if (objId == null)
		throw new NullPointerException("objId cannot be null");
	    if (type == null)
		throw new NullPointerException("type cannot be null");

	    AccessedObjectsDetailImpl detail = txnMap.get(txn);
	    detail.addAccess(new AccessedObjectImpl(objId, type, source,
						    detail));
            detail.setDescription(source, objId, description);
	}

        /** {@inheritDoc} */
	public void setObjectDescription(Transaction txn, T objId, Object description) {
	    if (txn == null)
		throw new NullPointerException("txn cannot be null");
	    if (objId == null)
		throw new NullPointerException("objId cannot be null");

            AccessedObjectsDetailImpl detail =
                txnMap.get(txn);
            detail.setDescription(source, objId, description);
        }

        /** {@inheritDoc} */
        public void reportObjectAccess(T objId, AccessType type) {
	    reportObjectAccess(txnProxy.getCurrentTransaction(), objId, type);
        }

        /** {@inheritDoc} */
	public void reportObjectAccess(T objId, AccessType type, 
				       Object description) {
	    reportObjectAccess(txnProxy.getCurrentTransaction(), 
			       objId, type, description);
	}

        /** {@inheritDoc} */
	public void setObjectDescription(T objId, Object description) {
	    setObjectDescription(txnProxy.getCurrentTransaction(), 
				 objId, description);
        }
    }
}
