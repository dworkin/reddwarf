/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import java.util.Collection;


/**
 * This interface is used to schedule tasks to run. Unlike the
 * <code>TaskManager</code> interface used by applications, or the
 * <code>TaskService</code> interface used by <code>Service</code>s,
 * <code>TaskScheduler</code> is not transactional, and does not
 * persist tasks. To make a task transactional, you should wrap it
 * in <code>TransactionRunner</code>. To make a task persist, you
 * should use the <code>DataService</code>.
 * <p>
 * Note that while <code>TaskScheduler</code> is not aware of transactions
 * (with the exception of <code>runTransactionalTask</code>), it does
 * handle re-trying tasks submitted via the <code>reserveTask</code> and
 * <code>scheduleTask</code> methods based on <code>Exception</code>s thrown
 * from the given <code>KernelRunnable</code>. If the <code>Exception</code>
 * thrown implements <code>ExceptionRetryStatus</code> then the
 * <code>TaskScheduler</code> will consult the <code>shouldRetry</code>
 * method to decide if the task should be re-tried. It is up to the
 * scheduler implementation to decide if tasks are re-tried immediately,
 * or re-scheduled in some manner (for instance, scheduled at a higher
 * priority or put on the front of the queue).
 * <p>
 * The <code>runTask</code> and <code>runTransactionalTask</code> methods
 * also support re-trying tasks in certain cases based on the rules described
 * above. Refer to the method documentation for detail on how re-try
 * decisions are made. Note that in the specific case of
 * <code>runTransactionalTask</code> a scheduled task can effectively
 * create a nested task, in which case re-try is skipped for the nested
 * task to be handled for the calling task.
 * <p>
 * The <code>scheduleTask</code> methods will make a best effort to schedule
 * the task provided, but based on the policy of the scheduler, this may not
 * be possible. To ensure that a task will have space in the scheduler,
 * methods are provided to get a <code>TaskReservation</code>. This is
 * especially useful for transactional <code>Service</code>s that need to
 * assure space to schedule a task before they can actually commit the
 * task to be scheduled.
 * <p>
 * In addition to individual tasks, the scheduler also supports recurring
 * tasks through the <code>scheduleRecurringTask</code> method. 
 * <p>
 * Tasks run on the <code>TaskScheduler</code> are expected to be short-lived
 * (on the order of 10s of milliseconds). To run a long-lived task, see
 * <code>ResourceCoordinator</code>.
 */
public interface TaskScheduler
{

    /**
     * Reserves the ability to run the given task.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner);

    /**
     * Reserves the ability to run the given task. The scheduler will make
     * a best effort to honor the requested priority.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     * @param priority the requested <code>Priority</code>
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       Priority priority);

    /**
     * Reserves the ability to run the given task at a specified point in
     * the future. The <code>startTime</code> is a value in milliseconds
     * measured from 1/1/1970.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     *
     * @return a <code>TaskReservation</code> for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       long startTime);

    /**
     * Reserves the ability to run the given collection of tasks. The
     * reservation is used to run all or none of these tasks.
     *
     * @param tasks a <code>Collection</code> of <code>KernelRunnable</code>s
     *              to execute
     * @param owner the entity on who's behalf these tasks are run
     *
     * @return a <code>TaskReservation</code> for the tasks
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
                                        tasks, TaskOwner owner);

    /**
     * Schedules a task to run as soon as possible based on the specific
     * scheduler implementation.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner);

    /**
     * Schedules a task to run as soon as possible based on the specific
     * scheduler implementation. The scheduler will make a best effort
     * to honor the requested priority.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     * @param priority the requested <code>Priority</code>
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             Priority priority);

    /**
     * Schedules a task to run at a specified point in the future. The
     * <code>startTime</code> is a value in milliseconds measured from
     * 1/1/1970. If the starting time has already passed, then the task is
     * run immediately.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             long startTime);

    /**
     * Schedules a task to start running at a specified point in the future,
     * and continuing running on a regular period starting from that
     * initial point. Unlike the other <code>scheduleTask</code> methods,
     * this method will never fail to accept to the task so there is no
     * need for a reservation. Note, however, that the task will not actually
     * start executing until <code>start</code> is called on the returned
     * <code>RecurringTaskHandle</code>.
     * <p>
     * At each execution point the scheduler will make a best effort to run
     * the task, but based on available resources scheduling the task may
     * fail. Regardless, the scheduler will always try again at the next
     * execution time.
     *
     * @param task the <code>KernelRunnable</code> to execute
     * @param owner the entity on who's behalf this task is run
     * @param startTime the time at which to start the task
     * @param period the length of time in milliseconds between each
     *               recurring task execution
     *
     * @return a <code>RecurringTaskHandle</code> used to manage the
     *         recurring task
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     TaskOwner owner,
                                                     long startTime,
                                                     long period);

    /**
     * Runs the task synchronously, returning when the task has completed
     * or throwing an exception if the task fails. The scheduler decides
     * when to run this task, so the task may be run immediately or it may
     * be queued behind waiting tasks to maintain fairness, and it may be
     * handed off to another thread of control for execution. In any case,
     * the caller will block until the task completes or fails permanently.
     * <p>
     * Note that a task run through this method may not in turn call
     * {@code runTask}, nor may a task scheduled through this scheduler call
     * this method. Doing so will cause an {@code IllegalStateException}
     * to be thrown.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param retry {@code true} if the task should be re-tried when it
     *              fails with {@code ExceptionRetryStatus} requesting the
     *              task be re-tried, {@code false} otherwise
     *
     * @throws TaskRejectedException if the given task is not accepted
     * @throws IllegalStateException if {@code runTask} is being invoked by
     *                               a task that was run through this scheduler
     * @throws Exception if the task fails and is not re-tried
     */
    public void runTask(KernelRunnable task, TaskOwner owner, boolean retry)
        throws Exception;

    /**
     * Runs the task synchronously and in a transactional context, returning
     * when the task has completed or throwing an exception if the task
     * fails. The provided {@code KernelRunnable} should not be an instance
     * of or invoke an instance of {@code TransactionRunner}. This will cause
     * an {@code IllegalStateException} to be thrown since transactions
     * cannot be nested.
     * <p>
     * Unlike {@code runTask}, this method may be called from any thread
     * of control regardless of its current context. Specifically, this may
     * be called directly by a task run through this scheduler, a thread
     * independent of this scheduler, even by an already active transaction.
     * Note that in the latter case, rather than creating a transactional
     * context, the existing transaction will be used, meaning that this
     * method will return without committing the transactional task.
     * <p>
     * Note that when this method is called from the context of a task that
     * was started through this scheduler, then there is already an
     * associated owner. In this case, the provided {@code TaskOwner} will
     * be ignored and the owner will remain unchanged.
     * <p>
     * Note also that when this method is called from the context of a
     * transactional task that was started through this scheduler, then 
     * retry handling is already being applied to the calling task. 
     * This means that the provided {@code KernelRunnable} will only be retried
     * directly if this method is called from the context of a thread that
     * isn't running a transactional task executed through this
     * scheduler.  Otherwise, the retry will be performed by the top-level task.
     *
     * @param task the {@code KernelRunnable} to execute transactionally
     * @param owner the requested entity on who's behalf this task may be run
     *
     * @throws TaskRejectedException if the given task is not accepted
     * @throws IllegalStateException if the task is or invokes an instance
     *                               of {@code TransactionRunner}
     * @throws Exception if the task fails and is not re-tried
     */
    public void runTransactionalTask(KernelRunnable task, TaskOwner owner)
        throws Exception;

}
