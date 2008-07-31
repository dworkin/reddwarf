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
import com.sun.sgs.kernel.AccessNotifier;
import com.sun.sgs.kernel.AccessNotifier.AccessType;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileCollector;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
    private final ConcurrentHashMap<Transaction,AccessedObjectsDetailImpl>
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
    private final LinkedBlockingQueue<AccessedObjectsDetailImpl> backlog;

    // system components
    private final TransactionProxy txnProxy;
    private final ProfileCollector profileCollector;
 
    /** Creates an instance of {@code AccessCoordinatorImpl}. */
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
            backlog = new LinkedBlockingQueue<AccessCoordinatorImpl>(size);
        } else {
            backlog = null;
        }

        this.txnProxy = txnProxy;
        this.profileCollector = profileCollector;
    }

    /*
     * Implement AccessCoordinator interface.
     */

    /** {@inheritDoc} */
    public AccessNotifier registerContentionSource(String sourceName) {
        return new AccessNotifierImpl(sourceName);
    }

    /** {@inheritDoc} */
    public Transaction getConflictingTransaction(Transaction txn) {
        // until we manage contention, there will never be an active
        // transaction to return
        return null;
    }

    /*
     * Implement NonDurableTransactionParticipant interface.
     */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
        AccessedObjectsDetailImpl detail = txnMap.get(txn);
        // TODO: this is the last chance to abort because of contention,
        // when we're actually managing the contention
        detail.markCommitting();
        return false;
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
        reportDetail(txn, true);
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
        reportDetail(txn, true);
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
        reportDetail(txn, false);
    }

    /** {@inheritDoc} */
    public String getTypeName() {
        return getClass().getName();
    }

    /** Private helper to used to report access detail. */
    private void reportDetail(Transaction txn, boolean succeeded) {
        AccessedObjectsDetailImpl detail =
            txnMap.remove(txnProxy.getCurrentTransaction());

        // TODO: this should be replaced with a level check once Jane's
        // updates are ready
        if (profileCollector == null)
            return;

        if ((! succeeded) && (backlog != null)) {
            // NOTE: in the current system we'll never see a transaction
            // fail because of contention with another active transaction,
            // so currently this is only for looking through a backlog,
            // if that's enabled

            // FIXME: check for timeout first?

            for (AccessedObjectsDetailImpl oldDetail : backlog) {
                if (detail.conflicts(oldDetail)) {
                    detail.failed = true;
                    detail.conflictType = ConflictType.ACCESS_NOT_GRANTED;
                    detail.contendingId = oldDetail.getId();
                    break;
                }
            }

            detail.conflictType = ConflictType.UNKNOWN;
        } else {
            if (backlog != null) {
                while (! backlog.offer(detail))
                    backlog.poll();
            }
        }

        profileCollector.setAccessedObjectsDetail(detail);
    }

    /*
     * Package-private methods.
     */

    /** Notifies the coordinator that a new transaction is starting. */
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

    /** Private implementation of {@code AccessedObjectsDetail}. */
    private class AccessedObjectsDetailImpl implements AccessedObjectsDetail {
        private static final AtomicLong idGen = new AtomicLong(0);
        private final long id = idGen.incrementAndGet();
        private final ArrayList<AccessedObject> accessList =
            new ArrayList<AccessedObject>();
        private final HashMap<Object,Object> annMap =
            new HashMap<Object,Object>();
        private boolean committing = false;
        volatile boolean failed = false;
        volatile ConflictType conflictType = ConflictType.NONE;
        volatile long conflictingId = 0;
        /** {@inheritDoc} */
        public long getId() {
            return id;
        }
        /** {@inheritDoc} */
        public List<AccessedObject> getAccessedObjects() {
            return accessList;
        }
        /** {@inheritDoc} */
        public boolean failedOnContention() {
            return failed;
        }
        /** {@inheritDoc} */
        public ConflictType getConflictType() {
            return conflictType;
        }
        /** {@inheritDoc} */
        public long getContendingId() {
            return contendingId;
        }
        /** Local method for adding a single object access. */
        void addAccess(AccessedObject accessedObject) {
            accessList.add(accessedObject);
        }
        /** Local method for setting an object's annotation. */
        void setAnnotation(Object objId, Object annotation) {
            annMap.put(objId, annotation);
        }
        /** Local method for getting a given object's annotation (if any). */
        Object getAnnotation(Object objId) {
            return annMap.get(objId);
        }
        /** Marks this detail as having progressed past prepare(). */
        synchronized void markCommitting() {
            committing = true;
        }
        /** Reports whether the associated transaction has prepared. */
        synchronized boolean isCommitting() {
            return committing;
        }
        booolean conflicts(AccessedObjectsDetailImpl other) {
            // FIXME: implement this...probably by keeping some maps of
            // maps over the sources and types of access, so that the right
            // kind of intersection can be calculated quickly
            return false;
        }
    }

    /** Private implementation of {@code AccessedObject}. */
    private class AccessedObjectImpl implements AccessedObject {
        private final Object objId;
        private final AccessType type;
        private final String source;
        private final AccessedObjectsDetailImpl parent;
        /** Creates an instance of {@code AccessedObjectImpl}. */
        AccessedObjectImpl(Object objId, AccessType type, String source,
                           AccessedObjectsDetailImpl parent) {
            this.objId = objId;
            this.type = type;
            this.source = source;
            this.parent = parent;
        }
        /** {@inheritDoc} */
        public Object getObject() {
            return objId;
        }
        /** {@inheritDoc} */
        public AccessType getAccessType() {
            return type;
        }
        /** {@inheritDoc} */
        public Object getAnnotation() {
            return parent.getAnnotation(objId);
        }
        /** {@inheritDoc} */
        public String getSource() {
            return source;
        }
    }

    /** Private implementation of {@code AccessNotifier}. */
    private class AccessNotifierImpl implements AccessNotifier {
        private final String source;
        /** Creates an instance of {@code AccessNotifierImpl}. */
        AccessNotifierImpl(String source) {
            this.source = source;
        }
        /** {@inheritDoc} */
        public void notifyObjectAccess(Object objId, AccessType type) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.addAccess(new AccessedObjectImpl(objId, type, source,
                                                    detail));
            // TODO: when we're managing contention, this step should
            // also track the requested access, seeing if this could
            // cause failure to the calling or other transaction
        }
        /** {@inheritDoc} */
        public void setObjectAnnotation(Object objId, Object annotation) {
            AccessedObjectsDetailImpl detail =
                txnMap.get(txnProxy.getCurrentTransaction());
            detail.setAnnotation(objId, annotation);
        }
    }

}
