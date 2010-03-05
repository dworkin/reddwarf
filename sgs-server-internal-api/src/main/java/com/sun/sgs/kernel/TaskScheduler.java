/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;


/**
 * This interface is used to run tasks that may take an arbitrarily long time
 * to complete, but are expected to complete eventually. These tasks may be
 * scheduled to run immediately or after some delay, possibly re-executing at
 * regular intervals.
 * <p>
 * Based on an implementation's policy, a task and its owner, that task may
 * not be accepted for execution. In this case {@code TaskRejectedException}
 * is thrown. To ensure that a task will be accepted methods are provided to
 * get a {@code TaskReservation}. This is especially useful for {@code Service}
 * methods working within a transaction that need to ensure that a task will
 * be accepted before they can commit.
 * <p>
 * Note that, because the tasks submitted through this interface may run
 * any length of time, there are no guarantees about when a given task
 * will start. If a task is scheduled to run immediately, or at some point
 * in the future, then this means that the scheduler will try to acquire
 * resources to run the task at that point. It may still be some indefinite
 * length of time before the task can actually be run. 
 */
public interface TaskScheduler {

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
    TaskReservation reserveTask(KernelRunnable task, Identity owner);

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
    TaskReservation reserveTask(KernelRunnable task, Identity owner,
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
    void scheduleTask(KernelRunnable task, Identity owner);

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
    void scheduleTask(KernelRunnable task, Identity owner, long startTime);

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
    RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                              Identity owner,
                                              long startTime,
                                              long period);

    /**
     * Creates a new {@code TaskQueue} to use in scheduling dependent tasks.
     * Once a given task has completed the next task will be submitted to the
     * scheduler to run.
     *
     * @return a new {@code TaskQueue}
     */
    TaskQueue createTaskQueue();

}
