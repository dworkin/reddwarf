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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.schedule.ScheduledTask;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskQueue;


/**
 * Package-private implementation of {@code ScheduledTask} that is used to
 * maintain state about tasks accepted by {@code TransactionScheduler} and
 * passed on its backing {@code SchedulerQueue}.
 * <p>
 * This class implements some of the {@code Future} interface so that the
 * associated task can be cancelled or waited on to finish. The resulting
 * value provided by a call to {@code get} is {@code null} if the task
 * completed successfully, or the {@code Throwable} that caused the task
 * to fail permanently in the case of failure. See the documentation on
 * the associated methods for more details on how interruption is handled.
 * <p>
 * Note: This class is located in the {@code com.sun.sgs.impl.kernel} package
 * instead of the {@code com.sun.sgs.impl.kernel.schedule} package because
 * it needs to be a package-private implementation for use by the schedulers.
 */
final class ScheduledTaskImpl implements ScheduledTask {

    // the common, immutable aspects of a task
    private final KernelRunnable task;
    private final Identity owner;
    private final long period;

    // the common, mutable aspects of a task
    private volatile Priority priority;
    private volatile long startTime;
    private RecurringTaskHandle recurringTaskHandle = null;
    private int tryCount = 0;
    private TaskQueue queue = null;
    private volatile long timeout;
    private volatile Throwable lastFailure = null;

    // state associated with the lifetime of the task
    private enum State {
        /* The task can be started running. */
        RUNNABLE,
        /* The task is currently running. */
        RUNNING,
        /* The task was interrupted while running but can be tried again. */
        INTERRUPTED,
        /* The task has completed. */
        COMPLETED,
        /* The task was cancelled. */
        CANCELLED
    }
    private State state = State.RUNNABLE;

    // the result of the task, if the state is COMPLETED; null if the task
    // completed successfully, or the cause of a permanent failure
    private Throwable result = null;

    /**
     * We use the builder pattern here to avoid constructor explosion.
     * This builder takes three required parameters, {@code task},
     * {@code owner}, and {@code priority} and then provides setter methods
     * for each of the remaining optional parameters for building a
     * {@code ScheduledTaskImpl} object.
     * <p>
     * Example usage:
     * <p>
     * <pre>
     * ScheduledTaskImpl task = new ScheduledTaskImpl.Builder(
     *     task, owner, priority).period(1000).build();
     * </pre>
     */
    static class Builder {
        // attributes used to build the ScheduledTaskImpl
        private KernelRunnable task;
        private Identity owner;
        private Priority priority;
        private long startTime = System.currentTimeMillis();
        private long period = NON_RECURRING;
        private long timeout = defaultTimeout;
        private RecurringTaskHandle recurringTaskHandle = null;

        // default values
        private static long defaultTimeout = -2;

        /**
         * Constructs a {@code Builder} object that takes three required
         * parameters for building a {@code ScheduledTaskImpl}.
         *
         * @param task the <code>KernelRunnable</code> to run
         * @param owner the <code>Identity</code> of the task owner
         * @param priority the <code>Priority</code> of the task
         */
        Builder(KernelRunnable task, Identity owner, Priority priority) {
            this.task = task;
            this.owner = owner;
            this.priority = priority;
        }

        /**
         * Constructs a {@code Builder} object to build a
         * {@code ScheduledTaskImpl} object with all of the same values as the
         * given task except for the {@code startTime}, {@code timeout}, and
         * {@code maxConcurrency} which are set to their default values.
         *
         * @param task existing {@code ScheduledTaskImpl}
         */
        Builder(ScheduledTaskImpl task) {
            this(task.task, task.owner, task.priority);
            this.period = task.period;
            this.recurringTaskHandle = task.recurringTaskHandle;
        }

        /**
         * Setter for setting the start time of a new {@code ScheduledTaskImpl}.
         *
         * @param startTime the time at which to start in milliseconds since
         *                  January 1, 1970
         * @return this {@code Builder} object
         */
        Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * Setter for setting the period of a new {@code ScheduledTaskImpl}
         *
         * @param period the delay between recurring executions, or
         *               {@code NON_RECURRING}
         * @return this {@code Builder} object
         */
        Builder period(long period) {
            this.period = period;
            return this;
        }

        /**
         * Setter for setting the timeout of a new {@code ScheduledTaskImpl}
         *
         * @param timeout the transaction timeout to use for this task or
         *                {@code UNBOUNDED}
         * @return this {@code Builder} object
         */
        Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set the default value of {@code timeout} for new instances
         * of {@code ScheduledTaskImpl} built with a builder.
         *
         * @param timeout default value of timeout
         */
        static void setDefaultTimeout(long timeout) {
            defaultTimeout = timeout;
        }

        /**
         * Builds a {@code ScheduledTaskImpl} object from the attributes
         * in this builder.
         *
         * @return a {@code ScheduledTaskImpl} object
         */
        ScheduledTaskImpl build() {
            return new ScheduledTaskImpl(this);
        }

    }

    /**
     * Creates an instance of {@code ScheduledTaskImpl} from the given
     * {@code Builder}.
     *
     * @param builder the {@code Builder} object that contains each of the
     *                desired attributes of the new task
     */
    private ScheduledTaskImpl(Builder builder) {
        if (builder.task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (builder.owner == null) {
            throw new NullPointerException("Owner cannot be null");
        }
        if (builder.priority == null) {
            throw new NullPointerException("Priority cannot be null");
        }

        if (builder.timeout < 0 && builder.timeout != ScheduledTask.UNBOUNDED) {
            throw new IllegalStateException("Timeout cannot be negative");
        }

        this.task = builder.task;
        this.owner = builder.owner;
        this.priority = builder.priority;
        this.startTime = builder.startTime;
        this.period = builder.period;
        this.timeout = builder.timeout;
        this.recurringTaskHandle = builder.recurringTaskHandle;
    }

    /** Implementation of ScheduledTask interface. */

    /** {@inheritDoc} */
    public KernelRunnable getTask() {
        return task;
    }

    /** {@inheritDoc} */
    public Identity getOwner() {
        return owner;
    }

    /** {@inheritDoc} */
    public Priority getPriority() {
        return priority;
    }

    /** {@inheritDoc} */
    public long getStartTime() {
        return startTime;
    }

    /** {@inheritDoc} */
    public long getPeriod() {
        return period;
    }

    /** {@inheritDoc} */
    public int getTryCount() {
        return tryCount;
    }

    /** {@inheritDoc} */
    public long getTimeout() {
        return timeout;
    }
    
    /** {@inheritDoc} */
    public Throwable getLastFailure() {
        return lastFailure;
    }

    /** {@inheritDoc} */
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /** {@inheritDoc} */
    public boolean isRecurring() {
        return period != NON_RECURRING;
    }

    /** {@inheritDoc} */
    public RecurringTaskHandle getRecurringTaskHandle() {
        return recurringTaskHandle;
    }

    /** {@inheritDoc} */
    public synchronized boolean isCancelled() {
        return state == State.CANCELLED;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that in the current scheduler implementation this is only
     * called at the point where {@code runTask()} is using its calling
     * thread to run a task. In this case calling cancel() will always
     * return, so no extra logic has been implemented to make sure that
     * canceling the task keeps it from re-trying. Were this method to be
     * used when a task could be handed-off between threads or
     * re-tried many times then this implementation should probably be
     * extended to note the cancelation request and disallow further
     * attempts at execution.
     */
    public synchronized boolean cancel(boolean allowInterrupt)
        throws InterruptedException
    {
        if (isDone()) {
            return false; 
        }
        while (state == State.RUNNING) {
            try {
                wait();
            } catch (InterruptedException ie) {
                if (allowInterrupt) {
                    throw ie;
                }
            }
        }
        if (!isDone()) {
            state = State.CANCELLED;
            notifyAll();
            return true;
        }
        return false;
    }

    /** Package-private utility methods. */

    /**
     * Sets the transaction timeout for this task.
     *
     * @param timeout the new transaction timeout for this task
     */
    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns {@code null} if the task completed successfully, or the
     * {@code Throwable} that caused the task to fail permanently. If the
     * task has not yet completed then this will block until the result
     * is known or the caller is interrupted. An {@code InterruptedException}
     * is thrown either if the calling thread is interrupted before a result
     * is known or if the task is cancelled meaning that no result is known.
     */
    synchronized Throwable get() throws InterruptedException {
        // wait for the task to finish
        while (!isDone()) {
            wait();
        }
        if (state == State.CANCELLED) {
            throw new InterruptedException("interrupted while getting result");
        }
        return result;
    }

    /** Re-sets the starting time to the now. */
    void resetStartTime() {
        startTime = System.currentTimeMillis();
    }

    /** Returns whether the task has finished. */
    synchronized boolean isDone() {
        return ((state == State.COMPLETED) || (state == State.CANCELLED));
    }

    /**
     * Sets the state of this task to {@code running}, returning {@code false}
     * if the task has already been cancelled or has completed, or if the
     * state is not being changed.
     */
    synchronized boolean setRunning(boolean running) {
        if (isDone()) {
            return false;
        }
        if (running) {
            if ((state != State.RUNNABLE) && (state != State.INTERRUPTED)) {
                return false;
            }
            state = State.RUNNING;
        } else {
            if (state != State.RUNNING) {
                return false;
            }
            state = State.RUNNABLE;
            notifyAll();
        }
        return true;
    }

    /**
     * Similar to calling {@code setRunning(false)} except that no waiters
     * are notified of the state change. This is used when the task's thread
     * is interrupted and we don't know if the task is going to be re-runnable
     * in a new thread or has to be dropped.
     */
    synchronized boolean setInterrupted() {
        if (state == State.RUNNING) {
            state = State.INTERRUPTED;
            return true;
        }
        return false;
    }

    /** Returns whether this task is currently running. */
    synchronized boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Sets the state of this task to done, with the result of the task being
     * the provided {@code Throwable} which may be {@code null} to indicate
     * that the task completed successfully. If the task is not currently
     * running or was not previously interrupted then this method has no effect.
     * Otherwise, the result is set and the task is marked as completed.
     */
    synchronized void setDone(Throwable result) {
        if ((state != State.RUNNING) && (state != State.INTERRUPTED)) {
            return;
        }
        state = State.COMPLETED;
        this.result = result;
        notifyAll();
    }

    /**
     * Sets this task's handle or throws {@code IllegalStateException} if
     * the task is not recurring.
     */
    void setRecurringTaskHandle(RecurringTaskHandle handle) {
        if (!isRecurring()) {
            throw new IllegalStateException("Not a recurring task");
        }
        recurringTaskHandle = handle;
    }

    /**
     * Increments the try count (the number of times that this task has been
     * attempted).
     */
    void incrementTryCount() {
        tryCount++;
    }

    /**
     * Sets the {@code Throwable} that caused the last failure of this task.
     *
     * @param lastFailure the last failure
     */
    void setLastFailure(Throwable lastFailure) {
        this.lastFailure = lastFailure;
    }

    /**
     * Sets the {@code TaskQueue} associated with this task. Typically this
     * is used to track dependency, so that when this task is completed,
     * the next task can be fetched from the queue.
     */
    void setTaskQueue(TaskQueue queue) {
        this.queue = queue;
    }

    /**
     * Returns the {@code TaskQueue} associated with this task or {@code null}
     * if no queue was set. This is typically the source of dependent tasks
     * where this task was submitted.
     */
    TaskQueue getTaskQueue() {
        return queue;
    }

    /**
     * Provides some diagnostic detail about this task.
     *
     * @return a <code>String</code> representation of the task.
     */
    public String toString() {
        return task.getBaseTaskType() + "[owner:" + owner.getName() + "]";
    }

}
