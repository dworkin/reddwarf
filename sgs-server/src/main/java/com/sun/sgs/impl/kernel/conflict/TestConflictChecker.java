
package com.sun.sgs.impl.kernel.conflict;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;

import com.sun.sgs.service.Transaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;


/** A simple test class that does optimistic forward-validation. */
public class TestConflictChecker implements ConflictChecker {

    private final ConcurrentHashMap<Transaction,AccessInfo> txnMap;

    private static final ConflictResult NO_CONFLICT = new ConflictResult();

    /** Creates an instance of {@code TestConflictChecker}. */
    public TestConflictChecker() {
        txnMap = new ConcurrentHashMap<Transaction,AccessInfo>();
    }

    /** Notify the checker of a new transaction for the active set. */
    public void started(Transaction txn) {
        txnMap.put(txn, new AccessInfo());
    }

    /** Checks if the requested access causes conflict. */
    public synchronized ConflictResult checkAccess(Transaction txn,
                                                   Object objId,
                                                   AccessType type,
                                                   String source) {
        AccessInfo info = txnMap.get(txn);
        Set<Object> writeSet = info.writeMap.get(source);

        if (type == AccessType.READ) {
            // if we've already written this object, then don't add to
            // the read set, since a read won't conflict with a write
            // in another transaction
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

    /** Validates the given transaction and removes it from the active set. */
    public synchronized ConflictResult validate(Transaction txn) {
        AccessInfo localInfo = txnMap.remove(txn);

        // if the transaction didn't write anything, then we're done
        if (localInfo.writeMap.isEmpty())
            return NO_CONFLICT;

        // see if this transaction's write-set intersects with the read-set
        // of any active transaction
        final Set<String> sources = localInfo.writeMap.keySet();
        for (Entry<Transaction,AccessInfo> activeEntry : txnMap.entrySet()) {
            for (String source : sources) {
                Set<Object> otherReadSet =
                    activeEntry.getValue().readMap.get(source);
                if (otherReadSet != null) {
                    for (Object id : localInfo.writeMap.get(source)) {
                        if (otherReadSet.contains(id)) {
                            return new ConflictResult(activeEntry.getKey(), id);
                        }
                    }
                }
            }
        }

        return NO_CONFLICT;
    }

    /** Try to remove the transaction from the active set. */
    public void finished(Transaction txn) {
        txnMap.remove(txn);
    }

    /** Simple class that tracks read and write sets, indexed by the source. */
    private static class AccessInfo {
        final HashMap<String,Set<Object>> readMap =
            new HashMap<String,Set<Object>>();
        final HashMap<String,Set<Object>> writeMap =
            new HashMap<String,Set<Object>>();
    }

}
