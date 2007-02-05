
package com.sun.sgs.impl.kernel.schedule;

import java.util.TimerTask;


/**
 * Simple implementation of <code>RecurringTaskHandle</code> that lets
 * the handle be associated with a <code>TimerTask</code> so that cancelling
 * the handle also cancels the associated <code>TimerTask</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class RecurringTaskHandleImpl implements InternalRecurringTaskHandle {

    // the scheduler using this handle
    private final ApplicationScheduler scheduler;

    // the actual task to run
    private ScheduledTask task;

    // the associated timer task
    private TimerTask currentTimerTask = null;

    // whether or not this task has been cancelled
    private boolean cancelled = false;

    // whether or not this task has been started
    private boolean started = false;

    /**
     * Creates an instance of <code>RecurringTaskHandleImpl</code>.
     *
     * @param scheduler the <code>ApplicationScheduler</code> that is using
     *                  this handle
     * @param task the task for this handle
     *
     * @throws IllegalArgumentException if the task is not recurring
     */
    public RecurringTaskHandleImpl(ApplicationScheduler scheduler,
                                   ScheduledTask task) {
        if (scheduler == null)
            throw new NullPointerException("Scheduler cannot be null");
        if (task == null)
            throw new NullPointerException("Task cannot be null");
        if (! task.isRecurring())
            throw new IllegalArgumentException("Task must be recurring");

        this.scheduler = scheduler;
        this.task = task;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void scheduleNextRecurrence() {
        if (cancelled)
            return;
        task = new ScheduledTask(task, task.getStartTime() + task.getPeriod());
        scheduler.addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void setTimerTask(TimerTask timerTask) {
        if (timerTask == null)
            throw new NullPointerException("TimerTask cannot be null");

        currentTimerTask = timerTask;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancels this handle, which will also cancel the associated
     * <code>TimerTask</code> and notify the <code>ApplicationScheduler</code>
     * about the task being cancelled. If this handle has already been
     * cancelled, then an exception is thrown.
     *
     * @throws IllegalStateException if this handle has already been cancelled
     */
    public void cancel() {
        synchronized (this) {
            if (cancelled)
                throw new IllegalStateException("cannot cancel task");
            cancelled = true;
        }
        scheduler.notifyCancelled(task);
        if (currentTimerTask != null)
            currentTimerTask.cancel();
    }

    /**
     * Starts the associated task running by passing the task to the
     * <code>ApplicationScheduler</code> via a call to <code>addTask</code>.
     * If this handle has already been started or cancelled, then an
     * exception is thrown.
     *
     * @throws IllegalStateException if the handle has already been started
     *                               or cancelled
     */
    public void start() {
        synchronized (this) {
            if ((cancelled) || (started))
                throw new IllegalStateException("cannot start task");
            started = true;
        }
        scheduler.addTask(task);
    }

}
