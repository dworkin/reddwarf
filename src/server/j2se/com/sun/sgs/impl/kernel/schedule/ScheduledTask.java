/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.TaskOwner;


/**
 * This package-private class is a simple container class used to represent
 * the main aspects of a task that has been accepted by the scheduler for
 * running.
 */
class ScheduledTask {

    // the common aspects of a task
    private final KernelRunnable task;
    private final TaskOwner owner;
    private final Priority priority;
    private final long startTime;
    private final long period;
    private InternalRecurringTaskHandle recurringTaskHandle = null;

    // identifier that represents a non-recurring task.
    static final int NON_RECURRING = -1;

    /**
     * Creates an instance of <code>ScheduledTask</code> with all the same
     * values of an existing <code>ScheduledTask</code> except its starting
     * time, which is set to the new value.
     *
     * @param task existing <code>ScheduledTask</code>
     * @param newStartTime the new starting time for the task in milliseconds
     *                     since January 1, 1970
     */
    ScheduledTask(ScheduledTask task, long newStartTime) {
        this(task.task, task.owner, task.priority, newStartTime, task.period);
        this.recurringTaskHandle = task.recurringTaskHandle;
    }

    /**
     * Creates an instance of <code>ScheduledTask</code> that does not
     * represent a recurring task.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param owner the <code>TaskOwner</code> of the task
     * @param priority the <code>Priority</code> of the task
     * @param startTime the time at which to start in milliseconds since
     *                  January 1, 1970
     */
    ScheduledTask(KernelRunnable task, TaskOwner owner, Priority priority,
                  long startTime) {
        this(task, owner, priority, startTime, NON_RECURRING);
    }

    /**
     * Creates an instance of <code>ScheduledTask</code>.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param owner the <code>TaskOwner</code> of the task
     * @param priority the <code>Priority</code> of the task
     * @param startTime the time at which to start in milliseconds since
     *                  January 1, 1970
     * @param period the delay between recurring executions, or
     *               <code>NON_RECURRING</code>
     */
    ScheduledTask(KernelRunnable task, TaskOwner owner, Priority priority,
                  long startTime, long period) {
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

    /**
     * Returns the task.
     *
     * @return the <code>KernelRunnable</code> to run
     */
    KernelRunnable getTask() {
        return task;
    }

    /**
     * Returns the owner.
     *
     * @return the <code>TaskOwner</code>
     */
    TaskOwner getOwner() {
        return owner;
    }

    /**
     * Returns the priority.
     *
     * @return the <code>Priority</code>
     */
    Priority getPriority() {
        return priority;
    }

    /**
     * Returns the time at which this task is scheduled to start.
     *
     * @return the scheduled run time for the task
     */
    long getStartTime() {
        return startTime;
    }

    /**
     * Returns the period for the task if it's recurring, or
     * <code>NON_RECURRING</code> if this is not a recurring task.
     *
     * @return the period between recurring executions.
     */
    long getPeriod() {
        return period;
    }

    /**
     * Returns whether this is a recurring task.
     *
     * @return <code>true</code> if this task is a recurring task,
     * <code>false</code> otherwise.
     */
    boolean isRecurring() {
        return period != NON_RECURRING;
    }

    /**
     * Sets the <code>InternalRecurringTaskHandle</code> for this task if
     * the task is recurring and if the handle has not already been set.
     *
     * @return <code>true</code> if the task is recurring and the handle
     *         has not already been set, <code>false</code> otherwise
     */
    boolean setRecurringTaskHandle(InternalRecurringTaskHandle handle) {
        if ((! isRecurring()) || (recurringTaskHandle != null))
            return false;
        recurringTaskHandle = handle;
        return true;
    }

    /**
     * Returns the <code>InternalRecurringTaskHandle</code> for this task, or
     * <code>null</code> if this not a recurring task.
     *
     * @return the task's <code>InternalRecurringTaskHandle</code> or
     *         <code>null</code>
     */
    InternalRecurringTaskHandle getRecurringTaskHandle() {
        return recurringTaskHandle;
    }

    /**
     * Provides some diagnostic detail about this task.
     *
     * @return a <code>String</code> representation of the task.
     */
    public String toString() {
        return task.getClass().getName() + "[owner:" + owner.toString() + "]";
    }

}
