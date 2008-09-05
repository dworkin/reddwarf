
package com.sun.sgs.impl.kernel.conflict;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


// NOTE: in the current implementation transaction abort can't happen
// after validation because of how the Data Service orders events, but
// to be defensive this is a participant to make sure we can remove
// and mark entries as needed...this might also be done with a ref-count
// scheme so that we don't have to participate
public class OptimisticBackwardChecker
    implements ConflictChecker, NonDurableTransactionParticipant
{
    private final Map<Transaction,AccessInfo> txnMap;

    private static final ConflictResult NO_CONFLICT = new ConflictResult();

    public OptimisticBackwardChecker() {
        txnMap = new LinkedHashMap<Transaction,AccessInfo>();
    }

    /** {@inheritDoc} */
    public synchronized void started(Transaction txn,
                                     ConflictResolver resolver) {
        txnMap.put(txn, new AccessInfo(txn));
        txn.join(this);
    }

    /** {@inheritDoc} */
    public synchronized ConflictResult checkAccess(Transaction txn,
                                                   Object objId,
                                                   AccessType type,
                                                   String source) {
        AccessInfo info = txnMap.get(txn);
        Set<Object> writeSet = info.writeMap.get(source);

        if (type == AccessType.READ) {
            // if we've already written this object, then don't add to
            // the read set
            if ((writeSet == null) || (! writeSet.contains(objId))) {
                Set<Object> readSet = info.readMap.get(source);
                if (readSet == null) {
                    readSet = new HashSet<Object>();
                    info.readMap.put(source, readSet);
                }
                readSet.add(objId);
            }
        } else {
            if (writeSet == null) {
                writeSet = new HashSet<Object>();
                info.writeMap.put(source, writeSet);
            }
            writeSet.add(objId);
        }

        return NO_CONFLICT;
    }

    /** {@inheritDoc} */
    public synchronized ConflictResult validate(Transaction txn) {
        AccessInfo localInfo = txnMap.get(txn);

        // if we didn't read anything, then we can't have caused conflict
        if (! localInfo.readMap.isEmpty()) {
            // see if this transaction's read-set intersects with the
            // write-set of any overlapped transactions
            final Set<String> sources = localInfo.readMap.keySet();
            for (AccessInfo overlappingInfo : localInfo.overlappingInfoSet) {
                if (! overlappingInfo.aborted) {
                    for (String source : sources) {
                        Set<Object> overlappingWriteSet =
                            overlappingInfo.writeMap.get(source);
                        if (overlappingWriteSet != null) {
                            Set<Object> localReadSet =
                                localInfo.readMap.get(source);
                            for (Object id : overlappingWriteSet) {
                                if (localReadSet.contains(id)) {
                                    localInfo.aborted = true;
                                    return new ConflictResult(overlappingInfo.
                                                              txn,
                                                              id);
                                }
                            }
                        }
                    }
                }
            }
        }

        // the transaction was validated, so note it for all currently
        // active transactions as a possible source of conflict
        for (AccessInfo activeInfo : txnMap.values())
            activeInfo.overlappingInfoSet.add(localInfo);

        return NO_CONFLICT;
    }

    /** {@inheritDoc} */
    public synchronized void abort(Transaction txn) {
        AccessInfo info = txnMap.remove(txn);
        if (info != null)
            info.aborted = true;
    }

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
        return false;
    }
    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
        commit(txn);
    }
    /** {@inheritDoc} */
    public synchronized void commit(Transaction txn) {
        txnMap.remove(txn);
    }
    /** {@inheritDoc} */
    public String getTypeName() {
        return getClass().getName();
    }

    private static class AccessInfo {
        final Transaction txn;
        boolean aborted = false;
        final Map<String,Set<Object>> readMap =
            new HashMap<String,Set<Object>>();
        final Map<String,Set<Object>> writeMap =
            new HashMap<String,Set<Object>>();
        final Set<AccessInfo> overlappingInfoSet = new HashSet<AccessInfo>();
        AccessInfo(Transaction txn) {
            this.txn = txn;
        }
    }

}
