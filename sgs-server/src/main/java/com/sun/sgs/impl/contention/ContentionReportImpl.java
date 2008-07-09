package com.sun.sgs.impl.contention;

import com.sun.sgs.contention.ContentionReport;
import com.sun.sgs.contention.ContentionReport.ConflictType;
import com.sun.sgs.contention.LockInfo;

import java.util.Collection;
import java.util.List;

public class ContentionReportImpl implements ContentionReport {

    private final List<LockInfo> acquiredLocks;

    private final Collection<LockInfo> contendedLocks;

    private final String abortedTaskType;

    private final String conflictingTaskType;

    private final long abortedID;

    private final long conflictingID;

    private final ConflictType conflictReason;

    ContentionReportImpl(long abortedID,
			 String abortedTaskType,
			 List<LockInfo> acquiredLocks,
			 Collection<LockInfo> contendedLocks,
			 ConflictType conflictReason,
			 long conflictingID,
			 String conflictingTaskType) {
	this.abortedID = abortedID;
	this.abortedTaskType = abortedTaskType;
	this.acquiredLocks = acquiredLocks;
	this.contendedLocks = contendedLocks;
	this.conflictReason = conflictReason;
	this.conflictingID = conflictingID;
	this.conflictingTaskType = conflictingTaskType;
    }

    /**
     * {@inheritDoc}
     */
    public List<LockInfo> getAcquiredLocks() {
	return acquiredLocks;
    }

    /**
     * {@inheritDoc}
     */
    public ConflictType getConflictType() {
	return conflictReason;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<LockInfo> getContendedLocks() {
	return contendedLocks;
    }

    /**
     * {@inheritDoc}
     */
    public long getTransactionID() {
	return abortedID;
    }

    /**
     * {@inheritDoc}
     */
    public long getConflictingTransactionID() {
	return conflictingID;
    }

    /**
     * {@inheritDoc}
     */
    public String getConflictingTaskType() {
	return conflictingTaskType;
    }

    public String toString() {
// 	reason = String.format("Task type %s with Transaction (id: %d) "
// 			       + "(try count %d) details:\n%s" 
// 			       + "was ABORTED due to conflicts with:\n"
// 			       + "Task type %s Transaction (id: %d) "
// 			       + "(try count: %d)\n%s\n",
// 			       info.taskType,
// 			       getID(txn), 
// 			       info.tryCount, 
// 			       info, 
// 			       otherInfo.taskType,
// 			       getID(other.getKey()), 
// 			       otherInfo.tryCount, 
// 			       otherInfo);
	return "FIXME";
    }

}