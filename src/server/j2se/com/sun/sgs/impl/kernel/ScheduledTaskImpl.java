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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.schedule.ScheduledTask;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskQueue;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


/**
 * Package-private implementation of {@code ScheduledTask} that is used to
 * maintain state about tasks accepted by {@code TransactionScheduler} and
 * passed on its backing {@code SchedulerQueue}.
 * <p>
 * This class implements the {@code Future} interface so that the
 * associated task can be cancelled or waited on to finish. The resulting
 * value provided by the {@code Future} is {@code null} if the task
 * completed successfully, or the {@code Throwable} that caused the task
 * to fail permanently in the case of failure. See the documentation on
 * the associated methods for more details on how interruption is handled.
 */
class ScheduledTaskImpl implements ScheduledTask, Future<Throwable> {

    // the common, immutable aspects of a task
    private final KernelRunnable task;
    private final Identity owner;
    private final Priority priority;
    private final long startTime;
    private final long period;

    // the common, mutable aspects of a task
    private RecurringTaskHandle recurringTaskHandle = null;
    private int tryCount = 0;
    private TaskQueue queue = null;

    // state associated with the lifetime of the task
    private enum State {
        /* The task can be started running. */
        RUNNABLE,
        /* The task is currently running. */
        RUNNING,
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
     * Creates an instance of <code>ScheduledTask</code> with all the same
     * values of an existing <code>ScheduledTask</code> except its starting
     * time, which is set to the new value.
     *
     * @param task existing <code>ScheduledTask</code>
     * @param newStartTime the new starting time for the task in milliseconds
     *                     since January 1, 1970
     */
    ScheduledTaskImpl(ScheduledTaskImpl task, long newStartTime) {
        this(task.task, task.owner, task.priority, newStartTime, task.period);
        this.recurringTaskHandle = task.recurringTaskHandle;
    }

    /**
     * Creates an instance of <code>ScheduledTask</code> that does not
     * represent a recurring task.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param owner the <code>Identity</code> of the task owner
     * @param priority the <code>Priority</code> of the task
     * @param startTime the time at which to start in milliseconds since
     *                  January 1, 1970
     */
    ScheduledTaskImpl(KernelRunnable task, Identity owner,
                      Priority priority, long startTime) {
        this(task, owner, priority, startTime, NON_RECURRING);
    }

    /**
     * Creates an instance of <code>ScheduledTask</code>.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param owner the <code>Identity</code> of the task owner
     * @param priority the <code>Priority</code> of the task
     * @param startTime the time at which to start in milliseconds since
     *                  January 1, 1970
     * @param period the delay between recurring executions, or
     *               <code>NON_RECURRING</code>
     */
    ScheduledTaskImpl(KernelRunnable task, Identity owner,
                      Priority priority, long startTime, long period) {
        if (task == null)
            throw new NullPointerException("Task cannot be null");
        if (owner == null)
            throw new NullPointerException("Owner cannot be null");
        if (priority == null)
            throw new NullPointerException("Priority cannot be null");

        this.task = task;
        this.owner = owner;
        this.priority = priority;
        this.startTime = startTime;
        this.period = period;
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

    /** {@inheritDoc} */
    public synchronized boolean cancel() {
        if (isFinished())
            return false;
        while (state == State.RUNNING) {
            try {
                wait();
            } catch (InterruptedException ie) {
                // TODO: should this actually just return false?
            }
        }
        if (state != State.CANCELLED) {
            state = State.CANCELLED;
            notifyAll();
            return true;
        }
        return false;
    }

    /** Implementation of Future interface. */

    // TODO: implement this if it proves useful
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** {@inheritDoc} */
    public synchronized Throwable get() throws InterruptedException {
        // if not done, then wait ... return result
        while (! isFinished())
            wait();
        if (state == State.CANCELLED)
            throw new InterruptedException("interrupted while getting result");
        return result;
    }

    // TODO: implement this if it proves useful
    public Throwable get(long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** {@inheritDoc} */
    synchronized public boolean isDone() {
        return (isFinished());
    }

    /** Private utility methods. */

    /** Returns whether the task has finished. */
    private boolean isFinished() {
        return ((state == State.COMPLETED) || (state == State.CANCELLED));
    }

    /** Package-private utility methods. */

    /**
     * Sets the state of this task to running, returning {@code false} if
     * the task has already been cancelled or has completed.
     */
    synchronized boolean setRunning(boolean running) {
        if (isFinished())
            return false;
        state = running ? State.RUNNING : State.RUNNABLE;
        if (! running)
            notifyAll();
        return true;
    }

    /** Returns whether this task is currently running. */
    synchronized boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Sets the state of this task to done, with the result of the task being
     * the provided {@code Throwable} which may be {@code null} to indicate
     * that the task completed successfully. If the task is not currently
     * running, or has already been cancelled then this method does not set
     * the result. Otherwise, the result and state state change are set and
     * the task is set as no longer running.
     */
    synchronized void setDone(Throwable result) {
        if (state != State.RUNNING)
            return;
        state = State.COMPLETED;
        this.result = result;
        notifyAll();
    }

    /**
     * Sets this task's handle or throws {@code IllegalStateException} if
     * the task is not recurring.
     */
    void setRecurringTaskHandle(RecurringTaskHandle handle) {
        if (! isRecurring())
            throw new IllegalStateException("Not a recurring task");
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
     * Returns the try count (the number of times that this task has been
     * attempted).
     */
    int getTryCount() {
        return tryCount;
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
