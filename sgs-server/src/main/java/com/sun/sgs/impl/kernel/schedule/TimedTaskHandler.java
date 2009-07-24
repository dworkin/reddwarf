/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Package-private utility class that handles timers for tasks that are
 * scheduled to run in the future.
 */
class TimedTaskHandler {

    /**
     * The milliseconds in the future that a task must be to be accepted
     * by the handler.
     */
    static final int FUTURE_THRESHOLD = 15;

    // the listener that will consume ready tasks
    private final TimedTaskListener listener;

    // the timer used for future execution
    private Timer timer;

    /**
     * Creates an instance of <code>TimedTaskHandler</code>. This has the
     * effect of creating a new <code>Timer</code> which involves creating
     * at least one new thread.
     *
     * @param listener the <code>TimedTaskListener</code> that will consume
     *                 the task when its time comes, causing it to be executed
     */
    TimedTaskHandler(TimedTaskListener listener) {
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null");
        }

        this.listener = listener;
        timer = new Timer();
    }

    /**
     * Tries to pass off a task to be run at some point in the future. If
     * the task should be run directly instead of delayed, then it is
     * rejected and the method returns <code>false</code>.
     *
     * @param task the <code>ScheduledTask</code> to run in the future
     *
     * @return <code>true</code> if the task is accepted to run delayed,
     *         <code>false</code> otherwise
     */
    boolean runDelayed(ScheduledTask task) {
        // see if this is far enough in the future that it's worth handling
        if (task.getStartTime() < (System.currentTimeMillis() +
                                   FUTURE_THRESHOLD)) {
            return false;
        }

        TimerTask tt = new TimerTaskImpl(task);

        // if this task is recurring, set the timer task if it's still active
        if (task.isRecurring()) {
            RecurringTaskHandleImpl handle =
                    (RecurringTaskHandleImpl) (task.getRecurringTaskHandle());
            synchronized (handle) {
                // if the recurring task is cancelled, then say that we're
                // handling it, which has the effect of the task being dropped
                if (handle.isCancelled()) {
                    return true;
                }
                handle.setTimerTask(tt);
            }
        }

        timer.schedule(tt, new Date(task.getStartTime()));
        return true;
    }

    /**
     * Shuts down this handler, cancelling the associated timer.
     */
    void shutdown() {
        timer.cancel();
    }

    /**
     * Private inner class implementation of <code>TimerTask</code>. This is
     * used to schedule all delayed tasks.
     */
    private class TimerTaskImpl extends TimerTask {
        private final ScheduledTask task;
        private boolean cancelled = false;
        TimerTaskImpl(ScheduledTask task) {
            this.task = task;
        }
        /** {@inheritDoc} */
        public synchronized boolean cancel() {
            if (!cancelled) {
                cancelled = true;
                return true;
            }
            return false;
        }
        /** {@inheritDoc} */
        public long scheduledExecutionTime() {
            return task.getStartTime();
        }
        /** {@inheritDoc} */
        public synchronized void run() {
            if (!cancelled) {
                listener.timedTaskReady(task);
            }
            cancelled = true;
        }
    }

}
