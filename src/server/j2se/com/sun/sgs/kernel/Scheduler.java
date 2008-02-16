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

package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;


/**
 * This is the base interface for any {@code Scheduler} provided by the
 * system to {@code Service}s and other components. A {@code Scheduler} is
 * used to execute work. This work can be done immediately or in the future,
 * possibly re-executing at regular intervals. When this work is finally
 * executed is a function of the specific {code Scheduler} being used.
 * <p>
 * Many methods will make a best effort to schedule a given task to run, but
 * based on the policy of that scheduler, the task and its owner, may be
 * unable to accept the given task. In this case {@code TaskRejectedException}
 * is thrown. To ensure that a task will be accepted by a {@code Scheduler},
 * methods are provided to get a {@code TaskReservation}. This is especially
 * useful for transactional {@code Service}s that need to ensure that a task
 * will be accepted before they can commit.
 * <p>
 * Some extensions of {@code Scheduler}, like {@code TransactionScheduler},
 * may support features like transactions or task re-try. Unless otherwise
 * stated, however, none of the methods on {@code Scheduler} supply
 * durability, re-try behavior, or any other capability beyond basic attempts
 * to execute the requested work. Likewise, there are no base assumptions
 * imposed on the work being scheduled, although any extension of this
 * interface may impose its own assumptions or requirements.
 *
 * @see TaskScheduler
 * @see TransactionScheduler
 */
public interface Scheduler {

    /**
     * Reserves the ability to run the given task.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     *
     * @return a {@code TaskReservation} for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner);

    /**
     * Reserves the ability to run the given task at a specified point in
     * the future. The {@code startTime} is a value in milliseconds
     * measured from 1/1/1970.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     *
     * @return a {@code TaskReservation} for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime);

    /**
     * Schedules a task to run as soon as possible based on the specific
     * scheduler implementation.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    public void scheduleTask(KernelRunnable task, Identity owner);

    /**
     * Schedules a task to run at a specified point in the future. The
     * {@code startTime} is a value in milliseconds measured from
     * 1/1/1970. If the starting time has already passed, then the task is
     * run immediately.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime);

    /**
     * Schedules a task to start running at a specified point in the future,
     * and continuing running on a regular period starting from that
     * initial point. Unlike the other {@code scheduleTask} methods, this
     * method will never fail to accept to the task so there is no need for
     * a reservation. Note, however, that the task will not actually start
     * executing until {@code start} is called on the returned
     * {@code RecurringTaskHandle}.
     * <p>
     * At each execution point the scheduler will make a best effort to run
     * the task, but based on available resources scheduling the task may
     * fail. Regardless, the scheduler will always try again at the next
     * execution time.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     * @param period the length of time in milliseconds between each
     *               recurring task execution
     *
     * @return a {@code RecurringTaskHandle} used to manage the
     *         recurring task
     *
     * @throws IllegalArgumentException if {@code period} is less than or
     *                                  equal to zero
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period);

    /**
     * FIXME: add a task dependency method here
     */

}
