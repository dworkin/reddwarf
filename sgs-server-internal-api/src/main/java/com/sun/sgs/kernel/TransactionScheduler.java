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

package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;


/**
 * This interface is used to schedule transactional tasks for immediate,
 * delayed, or periodic execution. Transactional tasks are short-lived:
 * typically on the order of a few 10s of milliseconds) and not longer than
 * the value of the property {@code com.sun.sgs.txn.timeout}. All tasks run
 * through an implementation of {@code TransactionScheduler} will run
 * transactionally, and may be re-tried in the event of failure.
 * <p>
 * Many methods will make a best effort to schedule a given task to run, but
 * based on the policy of the implementation, the task and its owner, may be
 * unable to accept the given task. In this case {@code TaskRejectedException}
 * is thrown. To ensure that a task will be accepted, methods are provided to
 * get a {@code TaskReservation}. This is especially useful for {@code Service}
 * methods working within a transaction that need to ensure that a task will
 * be accepted before they can commit.
 * <p>
 * If the result of running a task via the {@code reserveTask} or
 * {@code scheduleTask} methods is an {@code Exception} which implements
 * {@code ExceptionRetryStatus}, then its {@code shouldRetry} method is
 * called to decide if the task should be re-tried. It is up to the scheduler
 * implementation's policy to decide how and when tasks are re-run, but all
 * failing tasks run through a {@code TransactionScheduler} that wish to be
 * re-tried will eventually be re-run given available resources.
 * <p>
 * Note that re-try is handled slightly differently for {@code runTask}. See
 * the documentation on that method for more details.
 */
public interface TransactionScheduler {

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
     * Runs the given task synchronously, returning when the task has
     * completed or throwing an exception if the task fails. It is up to the
     * {@code TransactionScheduler} implementation to decide when to run this
     * task, so the task may be run immediately or it might be queued behind
     * waiting tasks. The task may be handed off to another thread of control
     * for execution. In all cases, the caller will block until the task
     * completes or fails permanently.
     * <p>
     * As with all methods of {@code TransactionScheduler}, tasks run with
     * {@code runTask} will be run transactionally. If the caller is not
     * in an active transaction, then a transaction is created to run the
     * task. If the caller is already part of an active transaction, then
     * the task is run as part of that transaction, and the {@code owner}
     * paramater is ignored.
     * <p>
     * When the caller is not part of an active transaction, then when the
     * given task completes it will also attempt to commit. If committing
     * the transaction fails, normal re-try behavior is applied. If the
     * task requests to be re-tried, then it will be re-run according to the
     * scheduler implementation's policy. In this case, {@code runTask}
     * will not return until the task finally succeeds, or is no longer
     * re-tried.
     * <p>
     * In the event that the caller is part of an active transaction, then
     * there is no re-try applied in the case of a failure, and the
     * transaction is not committed if the task completes successfully. This
     * is because the system does not support nested transactions, and so
     * the decision to commit or re-try is left to the active transaction.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     *
     * @throws TaskRejectedException if the given task is not accepted
     * @throws InterruptedException if the calling thread is interrupted and
     *                              the associated task does not complete
     * @throws Exception if the task fails and is not re-tried
     */
    void runTask(KernelRunnable task, Identity owner) throws Exception;

    /**
     * Creates a new {@code TaskQueue} to use in scheduling dependent
     * tasks. Each task added to the queue will be run in a separate
     * transaction. Re-try is applied to each transaction, and the next
     * task in the queue is run only after the current task either
     * completes successfully or fails permanently.
     *
     * @return a new {@code TaskQueue}
     */
    TaskQueue createTaskQueue();

}
