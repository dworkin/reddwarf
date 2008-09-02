
package com.sun.sgs.impl.kernel.conflict;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.service.Transaction;


/** An implementation that does no checking. */
public final class NullConflictChecker implements ConflictChecker {

    private static final ConflictResult NO_CONFLICT = new ConflictResult();

    public void started(Transaction txn, ConflictResolver resolver) {}

    public ConflictResult checkAccess(Transaction txn, Object objId,
                                      AccessType type, String source) {
        return NO_CONFLICT;
    }

    public ConflictResult validate(Transaction txn) {
        return NO_CONFLICT;
    }

    public void abort(Transaction txn) {}

}
