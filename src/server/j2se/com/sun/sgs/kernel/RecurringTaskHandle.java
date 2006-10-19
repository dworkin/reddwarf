
package com.sun.sgs.kernel;


/**
 * This interface provides a handle to a recurring task in the scheduler.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface RecurringTaskHandle
{

    /**
     * Cancels the associated recurring task.
     *
     * @throws IllegalStateException if the task has already been cancelled
     */
    public void cancel();

}
