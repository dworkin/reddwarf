/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;


/**
 * This interface is used to define the backing queue for a scheduler.
 * Essentially, implementations are used to define some policy for ordering
 * task execution.
 * <p>
 * All implementations must implement a constructor of the form
 * ({@code java.util.Properties}).
 */
public interface SchedulerQueue {

    /**
     * Returns the number of tasks that are ready to run. This does not
     * include any pending or recurring tasks that have not yet come time
     * to run.
     *
     * @return the number of tasks ready to run
     */
    int getReadyCount();

    /**
     * Returns the next task to run, blocking if {@code wait} is {@code true}.
     * If not waiting, it returns {@code null} if nothing is available.
     *
     * @param wait whether to wait for a task to become available
     *
     * @return the next {@code ScheduledTask} or {@code null}
     *
     * @throws InterruptedException if the thread is interrupted while
     *                              waiting for a task
     */
    ScheduledTask getNextTask(boolean wait) throws InterruptedException;

    /**
     * Places at most the next {@code max} tasks available into
     * the provided {@code Collection}. This method does not block,
     * so it may not provide any tasks. The number of tasks provided
     * is returned.
     * <p>
     * Note that there is no guarentee that the tasks provided are all
     * contiguous. That is, if two calls are made at the same time to this
     * method, or a call to {@code getNextTask} is made at the same
     * time, the scheduler does not have to provide the next {@code max}
     * tasks to one consumer before servicing others. If this behavior is
     * needed, the caller needs to synchronize access. The tasks are
     * guaranteed to be in correct order if the provided {@code Collection}
     * is ordered.
     *
     * @param tasks the {@code Collection} into which the tasks are put
     * @param max the maximum number of tasks to get
     *
     * @return the number of tasks provided
     */
    int getNextTasks(Collection<? super ScheduledTask> tasks, int max);

    /**
     * Reserves a space for a task.
     *
     * @param task the {@code ScheduledTask} to reserve
     *
     * @return a {@code TaskReservation} for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made, or if
     *                               the task is recurring
     */
    TaskReservation reserveTask(ScheduledTask task);

    /**
     * Adds a task to the scheduler. This task is executed only once, but
     * may be executed immediately or in the future.
     * <p>
     * Note that if this is a recurring task, then a call to its
     * {@code getRecurringTaskHandle} method must return a handle provided
     * by a call to this implementation's {@code createRecurringTaskHandle}
     * method. Note also that it is up to the caller to re-schedule this
     * task for each recurrence, and that each such call must provide a
     * unique instance of {@code ScheduledTask}.
     *
     * @param task the {@code ScheduledTask} to add
     *
     * @throws TaskRejectedException if the task cannot be added
     */
    void addTask(ScheduledTask task);

    /**
     * Creates a {@code RecurringTaskHandle} for the associated task. The
     * associated task will not actually be available through this queue
     * until {@code start} is called on the returned handle. The
     * {@code ScheduledTask} instance must never have been previously used
     * to schedule a recurring task. Note that after the initial execution
     * of this task, it is up to the caller to schedule each recurrence
     * through a call to {@code addTask}.
     *
     * @param task the {@code ScheduledTask} to run recurringly
     *
     * @return a {@code RecurringTaskHandle} that manages the task
     *
     * @throws IllegalArgumentException if the task has already been scheduled
     *                                  as a recurring task
     */
    RecurringTaskHandle createRecurringTaskHandle(ScheduledTask task);
    
    /**
     * Notifies the scheduler that the given task has been cancelled. An
     * implementation does not need to do anything in reaction to this call
     * as long as the given task will not run. If this task is unknown, or
     * has already run, this call may be ignored.
     *
     * @param task the {@code ScheduledTask} that has been cancelled
     */
    void notifyCancelled(ScheduledTask task);

    /**
     * Tells this {@code SchedulerQueue} to shutdown.
     */
    void shutdown();

}
