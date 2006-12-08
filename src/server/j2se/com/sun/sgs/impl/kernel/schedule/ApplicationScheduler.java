
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;


/**
 * This interface is used to define a scheduler that is tied to a single
 * application. It is used by a <code>SystemScheduler</code> to handle
 * the tasks it recieves that are associated with a given application.
 * Implementations of this interface are essentially just responsible for
 * ordering the tasks associated with a given application.
 *
 * @since 1.0
 * @author Seth Proctor
 */
interface ApplicationScheduler
{

    /**
     * Reserves a space for a task.
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is executed only once, but
     * may be executed immediately or in the future.
     *
     * @param task the <code>ScheduledTask</code> to add
     *
     * @throws TaskRejectedException if the task cannot be added
     */
    public void addTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is a recurring task that is
     * scheduled to start at some point in the future. The task will not
     * actually start executing until <code>start</code> is called on the
     * returned handle. The <code>ScheduledTask</code> instance must never
     * have been previously used to schedule a recurring task.
     *
     * @param task the <code>ScheduledTask</code> to run recurringly
     *
     * @return a <code>RecurringTaskHandle</code> that manages the task
     *
     * @throws IllegalArgumentException if the task has already been scheduled
     *                                  as a recurring task
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task);

}
