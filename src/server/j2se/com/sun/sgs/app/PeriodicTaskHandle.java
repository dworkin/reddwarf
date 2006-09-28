package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Provides facilities for managing a {@link Task} scheduled with the {@link
 * TaskManager} to run periodically.  Classes that implement
 * <code>PeriodicTaskHandle</code> must also implement {@link Serializable}.
 *
 * @param	<T> the type of the associated task
 * @see		TaskManager#schedulePeriodicTask 
 *		TaskManager.schedulePeriodicTask
 */
public interface PeriodicTaskHandle<T extends Task> {

    /**
     * Cancels attempts to run the associated task in future periods.  Calling
     * this method has no effect on runs of the task for the current period if
     * an attempt to run the task for that period has already begun.
     * Cancelling a periodic task may involve removing an associated managed
     * object maintained internally by the <code>TaskManager</code>.  The
     * system will make an effort to flag subsequent references to the removed
     * object by throwing {@link ObjectNotFoundException} when this method is
     * called, although that behavior is not guaranteed.
     *
     * @throws	ObjectNotFoundException if the task has already been cancelled
     *		and removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void cancel();
}
