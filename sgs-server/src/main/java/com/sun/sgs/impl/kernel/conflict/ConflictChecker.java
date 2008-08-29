
package com.sun.sgs.impl.kernel.conflict;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.service.Transaction;


/** Experimental interface for checking for conflict. */
public interface ConflictChecker {

    /** Starts tracking the given transaction. */
    void started(Transaction txn);

    /** Checks a given access within a started transaction. */
    ConflictResult checkAccess(Transaction txn, Object objId, AccessType type,
                               String source);

    /** Validates and finishes tracking the given transaction. */
    ConflictResult validate(Transaction txn);

    /** Finishes tracking the given transaction. */
    void finished(Transaction txn);

}
