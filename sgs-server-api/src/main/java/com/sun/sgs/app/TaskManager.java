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

package com.sun.sgs.app;

import java.io.Serializable;
import java.math.BigInteger;

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
 * Note that there is no assumed ordering provided by implementations of
 * this interface. If two tasks are scheduled in a given transaction, it is
 * undefined which task will run or complete first. Likewise, if a task
 * is scheduled and then a second scheduled in a later transaction, there
 * is no guarantee that the task scheduled in the previous transaction
 * will complete first. If any ordering or dependency is required, this
 * should be implemented within the tasks themselves.
 * 
 * @see AppContext#getTaskManager
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
     * Returns the transaction ID for the currently running task.  Each
     * scheduled run of each task has its own unique transaction ID.
     * Calls to this method from within the same run of a given task will
     * return values that are all equal; calls made from within different tasks
     * or different runs of the same task will return different values. <p>
     *
     * Applications can use this method to cache values transiently in fields
     * that are not part of the serialized state of the object.  Applications
     * should use this method to check for invalid caches rather than depending
     * on serialization hooks since managed objects are not guaranteed to be
     * serialized or deserialized on transaction boundaries. <p>
     *
     * For example, the follow class provides a simple (and probably not very
     * useful) example of a cache for the value of a managed object field:
     *
     * <pre>
     * class CachedField implements ManagedObject, Serializable {
     *     private static final long serialVersionUID = 1;
     *     private ManagedReference<CachedField> nextRef;
     *     private BigInteger txnId;
     *     private transient CachedField next;
     *     CachedField(CachedField next) {
     *         nextRef = AppContext.getDataManager().createReference(next);
     *         txnId = AppContext.getTaskManager().currentTransactionId();
     *         this.next = next;
     *     }
     *     CachedField getNext() {
     *         BigInteger currentTxnId =
     *             AppContext.getTaskManager().currentTransactionId();
     *         if (!txnId.equals(currentTxnId)) {
     *             next = nextRef.get();
     *         }
     *         return next;
     *     }
     *     private void readObject(java.io.ObjectInputStream in)
     *         throws IOException, ClassNotFoundException
     *     {
     *         in.defaultReadObject();
     *         txnId = AppContext.getTaskManager().currentTransactionId();
     *     }
     * }
     * </pre>
     *
     * @return  the current transaction ID
     * @throws  TransactionNotActiveException if there is no transaction active
     *          for the current task
     */
    BigInteger currentTransactionId();
}
