
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
     * Cancels the associated recurring task. A recurring task may be
     * cancelled before it is started.
     *
     * @throws IllegalStateException if the task has already been cancelled
     */
    public void cancel();

    /**
     * Starts the associated recurring task. A recurring task will not start
     * running until this method is called.
     *
     * @throws IllegalStateException if the task has already been started,
     *                               or has been cancelled
     */
    public void start();

}
