/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;
import java.util.Properties;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a simple implemenation of <code>ApplicationScheduler</code> that
 * accepts tasks and runs them in the order that they are ready. No attempt
 * is made to support priority, or to provide any degree of fairness
 * between users. This scheduler uses an un-bounded queue. Unless the system
 * runs out of memory, this should always accept any tasks from any user.
 */
class FIFOApplicationScheduler
    implements ApplicationScheduler, TimedTaskConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(FIFOApplicationScheduler.
                                           class.getName()));

    // the single queue of tasks
    private LinkedBlockingQueue<ScheduledTask> queue;

    // the handler for all delayed tasks
    private final TimedTaskHandler timedTaskHandler;

    /**
     * Creates an instance of <code>FIFOApplicationScheduler</code>.
     *
     * @param properties the available system properties
     */
    public FIFOApplicationScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a FIFO Application Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        queue = new LinkedBlockingQueue<ScheduledTask>();
        timedTaskHandler = new TimedTaskHandler(this);
    }

    /**
     * {@inheritDoc}
     */
    public int getReadyCount() {
        return queue.size();
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask(boolean wait)
        throws InterruptedException
    {
        ScheduledTask task = queue.poll();
        if ((task != null) || (! wait))
            return task;
        return queue.take();
    }

    /**
     * {@inheritDoc}
     */
    public int getNextTasks(Collection<ScheduledTask> tasks, int max) {
        return queue.drainTo(tasks, max);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        if (task.isRecurring())
            throw new TaskRejectedException("Recurring tasks cannot get " +
                                            "reservations");

        return new SimpleTaskReservation(this, task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        if (task == null)
            throw new NullPointerException("Task cannot be null");

        if (! timedTaskHandler.runDelayed(task)) {
            // check if the task is recurring, because we're not allowed to
            // reject those tasks
            if (! task.isRecurring()) {
                if (! queue.offer(task))
                    throw new TaskRejectedException("Request was rejected");
            } else {
                timedTaskReady(task);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        if (task == null)
            throw new NullPointerException("Task cannot be null");
        if (! task.isRecurring())
            throw new IllegalArgumentException("Not a recurring task");

        InternalRecurringTaskHandle handle =
            new RecurringTaskHandleImpl(this, task);
        if (! task.setRecurringTaskHandle(handle)) {
            logger.log(Level.SEVERE, "a scheduled task was given a new " +
                       "RecurringTaskHandle");
            throw new IllegalArgumentException("cannot re-assign handle");
        }
        return handle;
    }

    /**
     * {@inheritDoc}
     */
    public void notifyCancelled(ScheduledTask task) {
        // FIXME: do we want to pull the task out of the queue?
    }

    /**
     * {@inheritDoc}
     */
    public void timedTaskReady(ScheduledTask task) {
        while (! queue.offer(task));
    }

    public void shutdown() {
        timedTaskHandler.shutdown();
    }

}
