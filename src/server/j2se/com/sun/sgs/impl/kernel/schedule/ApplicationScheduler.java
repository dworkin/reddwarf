/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;


/**
 * This interface is used to define a scheduler that is tied to a single
 * application. It is used by a <code>SystemScheduler</code> to handle
 * the tasks it recieves that are associated with a given application.
 * Implementations of this interface are essentially just responsible for
 * ordering the tasks associated with a given application.  All
 * implementations must implement a constructor of the form
 * <code>(java.util.Properties)</code>.
 */
interface ApplicationScheduler {

    /**
     * The default location to specify an application's scheduler.
     */
    public final String APPLICATION_SCHEDULER_PROPERTY =
        "com.sun.sgs.ApplicationScheduler";

    /**
     * Returns the number of tasks that are ready for the scheduler to
     * run. This does not include any pending or recurring tasks that
     * have not come time to run.
     *
     * @return the number of tasks ready to run
     */
    public int getReadyCount();

    /**
     * Returns the next task to run. Unlike the <code>getNextTask</code>
     * call on <code>SystemScheduler</code>, this method only blocks if
     * <code>wait</code> is <code>true</code>. If not waiting, it returns
     * <code>null</code> if nothing is available.
     *
     * @param wait whether to wait for a task to become available
     *
     * @return the next <code>ScheduledTask</code> or <code>null</code>
     *
     * @throws InterruptedException if the thread is interrupted while
     *                              waiting for a task
     */
    public ScheduledTask getNextTask(boolean wait) throws InterruptedException;

    /**
     * Places at most the next <code>max</code> tasks available into
     * the provided <code>Collection</code>. This method does not block,
     * so it may not provide any tasks. The number of tasks provided
     * is returned.
     * <p>
     * Note that there is no guarentee that the tasks provided are all
     * contiguous. That is, if two calls are made at the same time to this
     * method, or a call to <code>getNextTask</code> is made at the same
     * time, the scheduler does not have to provide the next <code>max</code>
     * tasks to one consumer before servicing others. If this behavior is
     * needed, the calling <code>SystemScheduler</code> needs to synchronize
     * access. The tasks are guaranteed to be in correct order, if the
     * provided <code>Collection</code> is ordered.
     *
     * @param tasks the <code>Collection</code> into which the tasks are put
     * @param max the maximum number of tasks to get
     *
     * @return the number of tasks provided
     */
    public int getNextTasks(Collection<ScheduledTask> tasks, int max);

    /**
     * Reserves a space for a task.
     *
     * @param task the <code>ScheduledTask</code> to reserve
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made, or if
     *                               the task is recurring
     */
    public TaskReservation reserveTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is executed only once, but
     * may be executed immediately or in the future.
     * <p>
     * Note that while recurring tasks are initially added to the scheduler
     * via <code>addRecurringTask</code>, each recurrence of that task
     * (including the first one) is added through this method, though a
     * unique instance of <code>ScheduledTask</code> must be provided each
     * time.
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

    /**
     * Notifies the scheduler that the given task has been cancelled. This
     * typically happens with recurring tasks when their associated handles
     * are cancelled. The scheduler does not need to do anything in reaction
     * to this call, as long as the given task will not run. If this task
     * is unknown, or has already run, this call may be ignored.
     *
     * @param task the <code>ScheduledTask</code> that has been cancelled
     */
    public void notifyCancelled(ScheduledTask task);

    /**
     * Testing method.
     */
    public void shutdown();

}
