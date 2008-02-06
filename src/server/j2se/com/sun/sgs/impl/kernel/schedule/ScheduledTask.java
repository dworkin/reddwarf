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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;


/**
 * This package-private class is a simple container class used to represent
 * the main aspects of a task that has been accepted by the scheduler for
 * running.
 */
class ScheduledTask {

    // the common aspects of a task
    private final KernelRunnable task;
    private final Identity owner;
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
     * @param owner the <code>Identity</code> of the task owner
     * @param priority the <code>Priority</code> of the task
     * @param startTime the time at which to start in milliseconds since
     *                  January 1, 1970
     */
    ScheduledTask(KernelRunnable task, Identity owner, Priority priority,
                  long startTime) {
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
    ScheduledTask(KernelRunnable task, Identity owner, Priority priority,
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
     * @return the <code>Identity</code> that owns the task
     */
    Identity getOwner() {
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
        return task.getBaseTaskType() + "[owner:" + owner.toString() + "]";
    }

}
