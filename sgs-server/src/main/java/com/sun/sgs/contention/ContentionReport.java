package com.sun.sgs.contention;

import java.util.Collection;
import java.util.List;

public interface ContentionReport {

    /**
     * TODo
     */
    public static enum ConflictType {
	DEADLOCK,
	LOCK_NOT_GRANTED,
	UNKNOWN
    }

    /**
     * Returns the locks acquired by the aborted transaction in the
     * order in which they were acquired. 
     *
     * @return the list of locks acquired, in acquisition order
     */
    public List<LockInfo> getAcquiredLocks();

    /**
     * Returns the contended locks that caused the abort, or an empty
     * list if the contention management system was unable to
     * determine what transaction caused the conflict.
     *
     * @return the list of known contended locks
     */
    public Collection<LockInfo> getContendedLocks();

    /**
     * Returns the {@code Class} type of the task that caused this
     * task to abort.
     * 
     * @return TODO
     */
    public String getConflictingTaskType();

    /**
     * TODO
     *
     * @return TODO
     */
    public long getConflictingTransactionID();

    /**
     * TODO
     *
     * @return TODO
     */
    public ConflictType getConflictType();
    
    /**
     * TODO
     *
     * @return TODO
     */
    public long getTransactionID();

}