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

package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.service.DataService;

import java.io.Serializable;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Utility, package-private class for managing durable implementations of
 * {@code Task} that have been scheduled through the {@code TaskService} but
 * have not yet run to completion or been dropped. This class maintains all
 * of the meta-data associated with these pending tasks, and if needed, makes
 * sure that the {@code Task} itself is persisted.
 */
class PendingTask implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    // the task that is pending, one of which is null based on whether the
    // task was managed by the caller (see docs on constructor)
    private final Task task;
    private final ManagedReference<Task> taskRef;

    // the task's meta-data
    private final String taskType;
    private final long startTime;
    private final long period;
    private final Identity identity;

    // whether this task has been marked as cancelled
    private boolean cancelled = false;

    // if this is a periodic task, where it's currently running
    private long runningNode = -1;

    /**
     * Creates an instance of {@code PendingTask}, handling the task
     * reference correctly. As described on {@code TaskManager}, any
     * {@code Task} may or may not implement {@code ManagedObject} and
     * this affects how the task is handled by the system. {@code PendingTask}
     * takes care of the task's durability, and how the task is eventually
     * run, in both situations.
     * <p>
     * Note that the {@code DataService} parameter is not kept as state. It
     * is just used (if needed) to resolve a reference in the constructor.
     * This does mean that this constructor should always be invoked in a
     * valid transactional context.
     *
     * @param task the {@code Task} that is pending
     * @param startTime the starting time for the task, in milliseconds
     *                  since January 1, 1970, or
     *                  {@code TaskServiceImpl.START_NOW}
     * @param period the period between recurrences, or
     *               {@code TaskServiceImpl.PERIOD_NONE}
     * @param identity the {@code Identity} of the owner of the task
     * @param dataService the {@code DataService} used to manage the task
     */
    PendingTask(Task task, long startTime, long period, Identity identity,
                DataService dataService) {
        // If the Task is also a ManagedObject then the assumption is
        // that the object was already managed by the application so we
        // just keep a reference...otherwise, we make it part of our
        // state, which has the effect of persisting the task. In either
        // case we set one of the two fields to null to disambiguate
        // what the situation is.
        if (task instanceof ManagedObject) {
            taskRef = dataService.createReference(task); 
            this.task = null;
        } else {
            this.task = task;
            taskRef = null;
        }

        // keep track of the meta-data
        this.taskType = task.getClass().getName();
        this.startTime = startTime;
        this.period = period;
        this.identity = identity;
    }

    /** Returns the type of the pending task. */
    String getBaseTaskType() {
        return taskType;
    }

    /**
     * Returns the time when this task is supposed to start, or
     * {@code TaskServiceImpl.START_NOW} if the task is not delayed.
     */
    long getStartTime() {
        return startTime;
    }

    /**
     * Returns the periodicity of this task, or
     * {@code TaskServiceImpl.PERIOD_NONE} if the task is not periodic.
     */
    long getPeriod() {
        return period;
    }

    /** Marks this task as cancelled. Must be called in a transaction. */
    void markCanceled() {
        AppContext.getDataManager().markForUpdate(this);
        cancelled = true;
    }

    /**
     * Returns the node where the associated task is running if the task
     * is periodic, or -1 if the task is non-periodic or the node hasn't
     * been assigned.
     */
    long getRunningNode() {
        return runningNode;
    }

    /**
     * Sets the node where the associated task is running if the task is
     * periodic. If the task is not periodic, {@code IllegalStateException}
     * is thrown.
     */
    void setRunningNode(long nodeId) {
        if (! isPeriodic())
            throw new IllegalStateException("Cannot assign running node " +
                                            "for a non-periodic task");
        runningNode = nodeId;
    }

    /** Checks if this task has been marked as cancelled. */
    boolean isCancelled() {
        return cancelled;
    }

    /** Checks if this is a periodic task. */
    boolean isPeriodic() {
        return (period != TaskServiceImpl.PERIOD_NONE);
    }

    /** Returns the identity that owns this task. */
    Identity getIdentity() {
        return identity;
    }

    /**
     * Checks that the underlying task is available, which is only at
     * question if the task was managed by the application and therefore
     * could have been removed by the application. Must be called in a
     * transaction.
     */
    boolean isTaskAvailable() {
        if (task != null)
            return true;
        try {
            taskRef.get();
            return true;
        } catch (ObjectNotFoundException onfe) {
            return false;
        }
    }

    /**
     * This is a convenience method used to correctly resolve the pending
     * task and run it, throwing any exceptions raised by the task. Note
     * that if {@code isTaskAvailable} returns {@code false} then this
     * method will simply return without running anything.
     */
    void run() throws Exception {
        Task actualTask = null;
        try {
            actualTask = (task != null) ? task : taskRef.get();
        } catch (ObjectNotFoundException onfe) {
            // This only happens when the application removed the task
            // object but didn't cancel the task, so we're done
            return;
        }
        actualTask.run();
    }

}
