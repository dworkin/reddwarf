
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;


/**
 * This interface is used to define top-level schedulers which are used
 * directly by the <code>MasterScheduler</code>. All implementations must
 * implement a constructor of the form <code>(java.util.Properties)</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
interface SystemScheduler {

    /**
     * Tells the scheduler that a new application will be submitting tasks
     * to be scheduled.
     *
     * @param context the application's context
     *
     * @throws IllegalArgumentException if a scheduler cannot be resolved for
     *                                  the given context
     */
    public void registerApplication(KernelAppContext context);

    /**
     * Returns the next task to run. This call blocks until a task is
     * available to run.
     *
     * @return the next <code>ScheduledTask</code>
     */
    public ScheduledTask getNextTask();

    /**
     * Used by <code>ApplicationScheduler</code>s to give ready tasks to
     * <code>SystemScheduler</code>s. These are tasks that should run
     * as soon as possible, and cannot be cancelled once passed through
     * this interface.
     *
     * @param task the ready <code>ScheduledTask</code>
     */
    public void giveReadyTask(ScheduledTask task);

    /**
     * Reserves a space for a task.
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is ready to execute as soon
     * as there are available resources.
     *
     * @param task the <code>ScheduledTask</code> that is ready to run
     *
     * @throws TaskRejectedException if the task cannot be added
     */
    public void addReadyTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is delayed, and supposed to
     * run at some point in the future.
     *
     * @param task the <code>ScheduledTask</code> to run in the future
     *
     * @throws TaskRejectedException if the task cannot be added
     */
    public void addFutureTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is a recurring task that is
     * scheduled to start at some point in the future. The task will not
     * actually start executing until <code>start</code> is called on the
     * returned handle.
     *
     * @param task the <code>ScheduledTask</code> to run recurringly
     *
     * @return a <code>RecurringTaskHandle</code> that manages the task
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task);

}
