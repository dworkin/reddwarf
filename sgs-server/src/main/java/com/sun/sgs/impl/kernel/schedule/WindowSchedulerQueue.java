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
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>SchedulerQueue</code> tries to
 * provide fairness between users without taking too long to service any
 * given user's tasks. This is typified by the phrase "no one gets seconds
 * until everyone who wants them has had firsts."
 * <p>
 * The implementation uses a priority queue, keyed off what is called a
 * window value. Users submit tasks into increasing window values, and
 * may never submit tasks into windows that have already passed.
 */
public class WindowSchedulerQueue implements SchedulerQueue, TimedTaskListener {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(WindowSchedulerQueue.
                                           class.getName()));

    // the priority queue
    private PriorityBlockingQueue<QueueElement> queue;

    // the map of users to their windows
    private ConcurrentHashMap<Identity, QueueUser> userMap;

    // the handler for all delayed tasks
    private final TimedTaskHandler timedTaskHandler;

    /**
     * Creates an instance of <code>WindowSchedulerQueue</code>.
     *
     * @param properties the application <code>Properties</code>
     */
    public WindowSchedulerQueue(Properties properties) {
        logger.log(Level.CONFIG, "Creating a Window Scheduler Queue");

        if (properties == null) {
            throw new NullPointerException("Properties cannot be null");
        }

        queue = new PriorityBlockingQueue<QueueElement>();
        userMap = new ConcurrentHashMap<Identity, QueueUser>();
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
        // try to get the next element, and return the result if we're
        // not waiting, otherwise block
        QueueElement element = queue.poll();
        if (element != null) {
            return element.getTask();
        }
        if (!wait) {
            return null;
        }
        return queue.take().getTask();
    }

    /**
     * {@inheritDoc}
     */
    public int getNextTasks(Collection<? super ScheduledTask> tasks, int max) {
        for (int i = 0; i < max; i++) {
            QueueElement element = queue.poll();
            if (element == null) {
                return i;
            }
            tasks.add(element.getTask());
        }
        return max;
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
            timedTaskReady(task);
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
        // get the user details, creating an entry for the user if it's not
        // there already
        QueueUser user = userMap.get(task.getOwner());
        if (user == null) {
            userMap.putIfAbsent(task.getOwner(), new QueueUser());
            user = userMap.get(task.getOwner());
        }

        QueueElement nextElement = null;
        long scheduledWindow = 0L;

        // make sure that we're only scheduling one task for a given user,
        // so that we get a consistant view on the user's window counter
        assert (user != null);
        synchronized (user) {
            // see what window we're currently on, which will be the user's
            // next counter if there's nothing in the queue...this does
            // break the intent of the counter always going up, but this
            // is also a rare case in active schedulers, and active users
            // should catch-up the window pretty quickly
            long currentWindow = 0L;
            nextElement = queue.peek();
            if (nextElement != null) {
                currentWindow = nextElement.getWindow();
            }
            scheduledWindow = (currentWindow > user.nextWindow) ?
                currentWindow : user.nextWindow;
            user.nextWindow = scheduledWindow + 1;
            user.lastScheduled = task.getStartTime();
        }

        nextElement = new QueueElement(scheduledWindow, task);
        boolean success;
        do {
            success = queue.offer(nextElement);
        } while(!success);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        timedTaskHandler.shutdown();
    }

    // Private class used to manage the priority queue
    private static class QueueElement implements Comparable<QueueElement> {
        private final long window;
        private final ScheduledTask task;
        private final long timestamp;
        QueueElement(long window, ScheduledTask task) {
            this.window = window;
            this.task = task;
            this.timestamp = task.getStartTime();
        }
        long getWindow() {
            return window;
        }
        ScheduledTask getTask() {
            return task;
        }
        /** {@inheritDoc} */
        public int compareTo(QueueElement other) {
            // if the other window is bigger, then their priority is lower
            if (window < other.window) {
                return -1;
            }
            // if the other window is smaller, then their priority is higher
            if (window > other.window) {
                return 1;
            }
            // the windows are the same, so check timestamps, with the
            // same rules as above
            if (timestamp < other.timestamp) {
                return -1;
            }
            if (timestamp > other.timestamp) {
                return 1;
            }
            // NOTE: if the windows and timestamps are the same, here is
            // where we might fall-back on other values, but for now we'll
            // just say they've got the same priority
            return 0;
        }
        /** {@inheritDoc} */
        public boolean equals(Object o) {
            if ((o == null) || (!(o instanceof QueueElement))) {
                return false;
            }

            QueueElement other = (QueueElement) o;

            return ((window == other.window) &&
                    (timestamp == other.timestamp));
        }
        
        /** {@inheritDoc} */
        public int hashCode() {
            // Recipe from Effective Java
            int result = 17;
            result = 37 * result + (int) (window ^ (window >>> 32));
            result = 37 * result + (int) (timestamp ^ (timestamp >>> 32));
            return result;
        }
    }

    // NOTE: for this first-pass implementation, we're not using any
    // notion of priority, so there's just a single window counter
    // NOTE: the lastScheduled field is there so that periodically we can
    // kick off a thread to reap any users that haven't been scheduled
    // within some delta, but for now there won't be enough users to worry
    // about this case, so this feature isn't implemented
    private static class QueueUser {
        long nextWindow = 0L;
        long lastScheduled;
    }

}
