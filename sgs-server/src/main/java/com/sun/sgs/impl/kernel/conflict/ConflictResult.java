
package com.sun.sgs.impl.kernel.conflict;

import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;

import com.sun.sgs.service.Transaction;


/** Experimental class that tracks conflict checking results. */
public final class ConflictResult {

    /** The transaction that conflicted. */
    public final Transaction conflictingTxn;

    /** The id of the object that was the cause of conflict. */
    public final Object conflictingObjId;

    /** The type of conflict noted. */
    public final ConflictType conflictType;

    /** Create an instance that represents no conflict. */
    public ConflictResult() {
        this.conflictingTxn = null;
        this.conflictingObjId = null;
        this.conflictType = ConflictType.NONE;
    }

    /** Creates an instance respresenting the given conflict. */
    public ConflictResult(Transaction conflictingTxn, Object conflictingObjId) {
        this.conflictingTxn = conflictingTxn;
        this.conflictingObjId = conflictingObjId;
        this.conflictType = ConflictType.ACCESS_NOT_GRANTED;
    }

}
