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

package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Provides facilities for scheduling tasks.  Each task is a serializable
 * object that can be scheduled to be run now, at some time in the future, or
 * periodically. For the methods that run at some delayed point in the
 * future, the delay is taken from the moment when the scheduling method
 * was called.
 * <p>
 * For all methods on <code>TaskManager</code>, if the instance of
 * <code>Task</code> provided does not implement <code>ManagedObject</code>
 * then the <code>TaskManager</code> will persist the <code>Task</code>
 * until it finishes. This provides durability, and is particularly
 * convenient for simple tasks that the developer doesn't wish to manage
 * and remove manually. However, if the <code>Task</code> does implement
 * <code>ManagedObject</code>, then it's assumed that the <code>Task</code>
 * is already managed, and it is up to the developer to remove it from
 * the <code>DataManager</code> when finished.
 * <p>
 * If the instance of <code>Task</code> provided to any of these methods
 * is an instance of a class that has the <code>RunWithNewIdentity</code>
 * annotation then that task will be run with a new owning identity. Periodic
 * tasks will use this same owning identity for all recurrences. In
 * practical terms, this means that the system will be able to recognize
 * these tasks as distinct behavior from other tasks in the system.
 * <p>
 * Note that there is no assumed ordering provided by implementations of
 * this interface. If two tasks are scheduled in a given transaction, it is
 * undefined which task will run or complete first. Likewise, if a task
 * is scheduled and then a second scheduled in a later transaction, there
 * is no guarantee that the task scheduled in the previous transaction
 * will complete first. If any ordering or dependency is required, this
 * should be implemented within the tasks themselves.
 * 
 * @see AppContext#getTaskManager
 * @see Task
 * @see RunWithNewIdentity
 */
public interface TaskManager {

    /**
     * Schedules a task to run now.  The <code>TaskManager</code> will call the
     * task's {@link Task#run run} method as soon as possible after the
     * completion of the task in which this method is called, according to its
     * scheduling algorithm.  <p>
     *
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  The <code>TaskManager</code> is permitted to treat a
     * non-retryable exception as a hint.  In particular, a task that throws a
     * non-retryable exception may be retried if the node running the task
     * crashes.
     *
     * @param	task the task to run
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void scheduleTask(Task task);

    /**
     * Schedules a task to run after a delay.  The <code>TaskManager</code>
     * will wait for the specified number of milliseconds, and then call the
     * task's {@link Task#run run} method as soon as possible after the
     * completion of the task in which this method is called, according to its
     * scheduling algorithm. <p>
     *
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  The <code>TaskManager</code> is permitted to treat a
     * non-retryable exception as a hint.  In particular, a task that throws a
     * non-retryable exception may be retried if the node running the task
     * crashes.
     *
     * @param	task the task to run
     * @param	delay the number of milliseconds to delay before running the
     *		task
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}, or if delay is less than
     *		<code>0</code>
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void scheduleTask(Task task, long delay);

    /**
     * Schedules a task to run periodically after a delay.  The
     * <code>TaskManager</code> will wait for the specified number of
     * milliseconds, and then call the task's {@link Task#run run} method as
     * soon as possible after the completion of the task in which this method
     * is called, according to its scheduling algorithm.  It will also arrange
     * to run the task periodically at the specified interval following the
     * delay until the {@link PeriodicTaskHandle#cancel
     * PeriodicTaskHandle.cancel} method is called on the associated handle.
     * At the start of each period, which occurs <code>period</code>
     * milliseconds after the scheduled start of the previous period, a new
     * task will be scheduled to run. The <code>TaskManager</code> will make
     * a best effort to run a new task in each period, but even if the task
     * cannot be run in one period, a new task will always be scheduled for
     * the following period. The <code>TaskManager</code> will wait until
     * the current attempt to run the task has ended before making another
     * attempt to run it, regardless of whether the attempts are for the same
     * or different periods.
     * <p>
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  Note that calls to <code>PeriodicTaskHandle.cancel</code>
     * have no effect on attempts to retry a task after the first attempt.  The
     * <code>TaskManager</code> is permitted to treat a non-retryable exception
     * as a hint.  In particular, a task that throws a non-retryable exception
     * may be retried if the node running the task crashes.
     *
     * @param	task the task to run
     * @param	delay the number of milliseconds to delay before running the
     *		task
     * @param	period the number of milliseconds that should elapse between
     *		the starts of periodic attempts to run the task
     * @return	a handle for managing the scheduling of the task
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}, if delay is less than
     *		<code>0</code>, or if period is less than <code>0</code>
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                            long period);

    /**
     * Returns {@code true} if the currently running task should do more work
     * if it is available.  Otherwise, returns {@code false}.  This method
     * should always return {@code true} until the current task has done enough
     * work such that the work required to reschedule the task is negligible in
     * comparison to the work already done.
     *
     * @return {@code true} if the currently running task should do more work
     *         if possible; otherwise {@code false}
     * @throws TransactionException if the operation failed because of a
     *	       problem with the current transaction
     */
    boolean shouldContinue();
}
