
package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;

import com.sun.sgs.service.Transaction;

import java.util.HashSet;
import java.util.Map.Entry;

import java.util.concurrent.ConcurrentHashMap;


/** A simple test class that does optimistic forward-validation. */
// NOTE: we should partition based on source, but just to get things going..
class ConflictChecker {

    private final ConcurrentHashMap<Transaction,AccessInfo> txnMap;

    private static final ConflictResult NO_CONFLICT = new ConflictResult();

    ConflictChecker() {
        txnMap = new ConcurrentHashMap<Transaction,AccessInfo>();
    }

    /** Notify the checker of a new transaction for the active set. */
    void started(Transaction txn) {
        txnMap.put(txn, new AccessInfo());
    }

    /** Checks if the requested access causes conflict. */
    synchronized ConflictResult checkAccess(Transaction txn, Object objId,
                                            AccessType type, String source) {
        AccessInfo info = txnMap.get(txn);
        //if (info == null)
        //return null;

        if (type == AccessType.READ)
            info.readSet.add(objId);
        else
            info.writeSet.add(objId);
        return NO_CONFLICT;
    }

    /** Validates the given transaction and removes it from the active set. */
    synchronized ConflictResult validate(Transaction txn) {
        AccessInfo info = txnMap.remove(txn);

        // see if this transaction's write-set intersects with the read-set
        // of any active transaction
        for (Entry<Transaction,AccessInfo> e : txnMap.entrySet()) {
            for (Object id : e.getValue().readSet) {
                if (info.writeSet.contains(id)) {
                    return new ConflictResult(e.getKey(), id);
                }
            }
        }

        return NO_CONFLICT;
    }

    /** Try to remove the transaction from the active set. */
    void finished(Transaction txn) {
        txnMap.remove(txn);
    }

    /** Simple class that tracks read and write sets. */
    private static class AccessInfo {
        final HashSet<Object> readSet = new HashSet<Object>();
        final HashSet<Object> writeSet = new HashSet<Object>();
    }

    /** Testing class that tracks conflict checking results. */
    static class ConflictResult {
        final Transaction conflictingTxn;
        final Object conflictingObjId;
        final ConflictType conflictType;
        /** Create an instance that represents no conflict. */
        ConflictResult() {
            this.conflictingTxn = null;
            this.conflictingObjId = null;
            this.conflictType = ConflictType.NONE;
        }
        /** Creates an instance respresenting the given conflict. */
        ConflictResult(Transaction conflictingTxn, Object conflictingObjId) {
            this.conflictingTxn = conflictingTxn;
            this.conflictingObjId = conflictingObjId;
            this.conflictType = ConflictType.ACCESS_NOT_GRANTED;
        }
    }

}
