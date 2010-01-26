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
 * --
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.RecurringTaskHandle;

import java.util.TimerTask;


/**
 * Simple implementation of <code>RecurringTaskHandle</code> that lets
 * the handle be associated with a <code>TimerTask</code> so that cancelling
 * the handle also cancels the associated <code>TimerTask</code>.
 */
class RecurringTaskHandleImpl implements RecurringTaskHandle {

    // the queue using this handle
    private final SchedulerQueue queue;

    // the actual task to run
    private ScheduledTask task;

    // the associated timer task
    private TimerTask currentTimerTask = null;

    // whether or not this task has been cancelled;  synchronize on this
    // handle before using this field
    private boolean cancelled = false;

    // whether or not this task has been started
    private boolean started = false;

    /**
     * Creates an instance of <code>RecurringTaskHandleImpl</code>.
     *
     * @param queue the <code>SchedulerQueue</code> that is using
     *              this handle
     * @param task the task for this handle
     *
     * @throws IllegalArgumentException if the task is not recurring
     */
    public RecurringTaskHandleImpl(SchedulerQueue queue,
                                   ScheduledTask task) {
        if (queue == null) {
            throw new NullPointerException("Queue cannot be null");
        }
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (!task.isRecurring()) {
            throw new IllegalArgumentException("Task must be recurring");
        }

        this.queue = queue;
        this.task = task;
    }

    /**
     * Sets the associated <code>TimerTask</code> for this handle. This
     * method may be called any number of times on a handle. Typically
     * a recurring task will re-set the associated <code>TimerTask</code>
     * with each recurrence of execution.
     *
     * @param timerTask the associated <code>TimerTask</code>
     */
    synchronized void setTimerTask(TimerTask timerTask) {
        if (timerTask == null) {
            throw new NullPointerException("TimerTask cannot be null");
        }

        currentTimerTask = timerTask;
    }

    /**
     * Returns whether this handle has been cancelled. This does not say
     * anything about the state of any associated <code>TimerTask</code>.
     *
     * @return <code>true</code> if this handle has been cancelled,
     *         <code>false</code> otherwise
     */
    synchronized boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancels this handle, which will also cancel the associated
     * <code>TimerTask</code> and notify the <code>SchedulerQueue</code>
     * about the task being cancelled. If this handle has already been
     * cancelled, then an exception is thrown.
     *
     * @throws IllegalStateException if this handle has already been cancelled
     */
    public void cancel() {
        synchronized (this) {
            if (cancelled) {
                throw new IllegalStateException("cannot cancel task");
            }
            cancelled = true;
        }
        try {
            if (task.cancel(false)) {
                queue.notifyCancelled(task);
            }
        } catch (InterruptedException ie) {
            // this will never happen because false was passed to cancel()
        }
        if (currentTimerTask != null) {
            currentTimerTask.cancel();
        }
    }

    /**
     * Starts the associated task running by passing the task to the
     * <code>SchedulerQueue</code> via a call to <code>addTask</code>.
     * If this handle has already been started or cancelled, then an
     * exception is thrown.
     *
     * @throws IllegalStateException if the handle has already been started
     *                               or cancelled
     */
    public void start() {
        synchronized (this) {
            if ((cancelled) || (started)) {
                throw new IllegalStateException("cannot start task");
            }
            started = true;
        }
        queue.addTask(task);
    }

}
