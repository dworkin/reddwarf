
package com.sun.sgs.kernel;

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
 * Note that while <code>TaskScheduler</code> is not aware of transactions,
 * it does handle re-trying tasks based on <code>Exception</code>s thrown
 * from the given <code>KernelRunnable</code>. If the <code>Exception</code>
 * thrown implements <code>ExceptionRetryStatus</code> then the
 * <code>TaskScheduler</code> will consult the <code>shouldRetry</code>
 * method to decide if the task should be re-tried. It is up to the
 * scheduler implementation to decide if tasks are re-tried immediately,
 * or re-scheduled in some manner (for instance, scheduled at a higher
 * priority or put on the front of the queue).
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
 *
 * @since 1.0
 * @author Seth Proctor
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

}
