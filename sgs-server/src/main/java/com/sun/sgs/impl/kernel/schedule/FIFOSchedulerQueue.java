/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;
import java.util.Properties;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a simple implemenation of <code>SchedulerQueue</code> that
 * accepts tasks and runs them in the order that they are ready. No attempt
 * is made to support priority, or to provide any degree of fairness
 * between users. This class uses an un-bounded queue. Unless the system
 * runs out of memory, this should always accept any tasks from any user.
 */
public class FIFOSchedulerQueue implements SchedulerQueue, TimedTaskListener {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(FIFOSchedulerQueue.
                                           class.getName()));

    // the single queue of tasks
    private LinkedBlockingQueue<ScheduledTask> queue;

    // the handler for all delayed tasks
    private final TimedTaskHandler timedTaskHandler;

    /**
     * Creates an instance of <code>FIFOSchedulerQueue</code>.
     *
     * @param properties the available system properties
     */
    public FIFOSchedulerQueue(Properties properties) {
        logger.log(Level.CONFIG, "Creating a FIFO Scheduler Queue");

        if (properties == null) {
            throw new NullPointerException("Properties cannot be null");
        }

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
        if ((task != null) || (!wait)) {
            return task;
        }
        return queue.take();
    }

    /**
     * {@inheritDoc}
     */
    public int getNextTasks(Collection<? super ScheduledTask> tasks, int max) {
        return queue.drainTo(tasks, max);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        if (task.isRecurring()) {
            throw new TaskRejectedException("Recurring tasks cannot get " +
                                            "reservations");
        }

        return new SimpleTaskReservation(this, task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        if (!timedTaskHandler.runDelayed(task)) {
            // check if the task is recurring, because we're not allowed to
            // reject those tasks
            if (!task.isRecurring()) {
                if (!queue.offer(task)) {
                    throw new TaskRejectedException("Request was rejected");
                }
            } else {
                timedTaskReady(task);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle createRecurringTaskHandle(ScheduledTask task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (!task.isRecurring()) {
            throw new IllegalArgumentException("Not a recurring task");
        }

        return new RecurringTaskHandleImpl(this, task);
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
        boolean success;
        do {
            success = queue.offer(task);
        } while(!success);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        timedTaskHandler.shutdown();
    }

}
