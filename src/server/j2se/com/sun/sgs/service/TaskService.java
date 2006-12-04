
package com.sun.sgs.service;

import com.sun.sgs.app.TaskManager;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;


/**
 * This <code>Service</code> provides facilities for scheduling tasks to
 * run after the current task completes. The methods inherited from
 * <code>TaskManager</code> schedule durable, transactional tasks. The
 * <code>scheduleNonDurableTask</code> methods defined here are used to
 * schedule tasks that are not persisted by the <code>TaskService</code> and
 * not invoked in a transactional context (the caller may still create a
 * transactional context for their task by using a
 * <code>TransactionRunner</code>). All tasks scheduled will be owned by the
 * current task's owner.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface TaskService extends TaskManager, Service {

    /**
     * Shuts down this service (runs in a transaction).
     */
    void shutdownTransactional();

    /**
     * Schedules a single task to run once the current task has finished.
     * The task will not be persisted by the <code>TaskService</code>, and
     * therefore is not guaranteed to run.
     *
     * @param task the <code>KernelRunnable</code> to run
     *
     * @throws TaskRejectedException if the <code>TaskScheduler</code> refuses
     *                               to accept the task
     * @throws TransactionException if the operation failed because of a
     *		                        problem with the current transaction
     */
    public void scheduleNonDurableTask(KernelRunnable task);

    /**
     * Schedules a single task to run, after the given delay, once the
     * current task has finished. The task will not be persisted by the
     * <code>TaskService</code>, and therefore is not guaranteed to run.
     * As described in <code>TaskManager</code>, the delay is from the
     * time of this call, not from the time that the transaction commits.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param delay the number of milliseconds to delay before running the task
     *
     * @throws TaskRejectedException if the <code>TaskScheduler</code> refuses
     *                               to accept the task
     * @throws TransactionException if the operation failed because of a
     *		                        problem with the current transaction
     */
    public void scheduleNonDurableTask(KernelRunnable task, long delay);

    /**
     * Schedules a single task to run, at some requested priority, once the
     * current task has finished. The task will not be persisted  by the
     * <code>TaskService</code>, and therefore is not guaranteed to run.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param priority the requested <code>Priority</code> for the task
     *
     * @throws TaskRejectedException if the <code>TaskScheduler</code> refuses
     *                               to accept the task
     * @throws TransactionException if the operation failed because of a
     *		                        problem with the current transaction
     */
    public void scheduleNonDurableTask(KernelRunnable task, Priority priority);

}
