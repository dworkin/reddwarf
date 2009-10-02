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

package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

import com.sun.sgs.auth.Identity;

import java.io.Serializable;


/**
 * Utility, package-private class for managing durable implementations of
 * {@code Task} that have been scheduled through the {@code TaskService} but
 * have not yet run to completion or been dropped. This class maintains all
 * of the meta-data associated with these pending tasks, and if needed, makes
 * sure that the {@code Task} itself is persisted. This meta-data classs
 * can be flagged as re-usable, so that a single instance can be persisted
 * and then re-used as needed, although a given instance must always be
 * re-used for tasks for the same {@code Identity}.
 */
class PendingTask implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    // the task that is pending, one of which is null based on whether the
    // task was managed by the caller (see docs on constructor)
    private Task task;
    private ManagedReference<Task> taskRef;

    // the owner of this task
    private final Identity identity;

    // the task's re-assignable meta-data
    private String taskType;
    private long startTime;
    private long period;
    private long lastStartTime;
    
    // identifies whether this instance is free for re-use
    private boolean reusable;

    // if this is a periodic task, where it's currently running
    private long runningNode;

    /**
     * Creates an instance of {@code PendingTask}.
     *
     * @param identity the {@code Identity} of the owner of the task
     */
    PendingTask(Identity identity) {
        this.taskRef = null;
        this.identity = identity;

        this.reusable = true;
        this.runningNode = -1;
    }

    /**
     * Re-sets this {@code PendingTask} with the given values, and marks
     * it as unavailable. This can now be used to represent a durable
     * task that needs to be run. As described on {@code TaskManager}, any
     * {@code Task} may optionally implement {@code ManagedObject} and
     * this affects how the task is handled by the system. {@code PendingTask}
     * takes care of the task's durability, and how the task is eventually
     * run, in both situations.
     *
     * @param t the {@code Task} that is pending
     * @param s the starting time for the task, in milliseconds since
     *          January 1, 1970, or {@code TaskServiceImpl.START_NOW}
     * @param p the period between recurrences, or
     *          {@code TaskServiceImpl.PERIOD_NONE}
     */
    void resetValues(Task t, long s, long p) {
        DataManager dm = AppContext.getDataManager();
        dm.markForUpdate(this);

        /* If the Task is also a ManagedObject then the assumption is
           that the object was already managed by the application so we
           just keep a reference...otherwise, we make it part of our
           state, which has the effect of persisting the task. In either
           case we set one of the two fields to null to disambiguate
           what the situation is. */
        if (t instanceof ManagedObject) {
            taskRef = dm.createReference(t); 
            task = null;
        } else {
            task = t;
            taskRef = null;
        }

        this.taskType = t.getClass().getName();
        this.startTime = s;
        this.period = p;
        this.lastStartTime = TaskServiceImpl.NEVER;

        this.reusable = false;
        this.runningNode = -1;
    }

    /** Returns whether this {@code PendingTask} is free to be re-used. */
    boolean isReusable() {
        return reusable;
    }

    /** Re-sets the task state so that the task can be re-used. */
    void setReusable() {
        AppContext.getDataManager().markForUpdate(this);
        reusable = true;
        task = null;
        taskRef = null;
        taskType = null;
        startTime = TaskServiceImpl.START_NOW;
        period = TaskServiceImpl.PERIOD_NONE;
        lastStartTime = TaskServiceImpl.NEVER;
        runningNode = -1;
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

    /**
     * Returns the node where the associated task is running if the task
     * is periodic, or -1 if the task is non-periodic or the node hasn't
     * been assigned.
     */
    long getRunningNode() {
        return runningNode;
    }

    /**
     * Returns the last time when this task actually started, or
     * {@code TaskServiceImpl.NEVER} if the task has never been run.
     */
    long getLastStartTime() {
        return lastStartTime;
    }

    /**
     * Sets the node where the associated task is running if the task is
     * periodic. If the task is not periodic, {@code IllegalStateException}
     * is thrown.
     */
    void setRunningNode(long nodeId) {
        if (!isPeriodic()) {
            throw new IllegalStateException("Cannot assign running node " +
                                            "for a non-periodic task");
        }
        AppContext.getDataManager().markForUpdate(this);
        runningNode = nodeId;
    }

    /**
     * Sets the last start time for this task.
     *
     * @param lastStartTime the new start time
     */
    void setLastStartTime(long lastStartTime) {
        AppContext.getDataManager().markForUpdate(this);
        this.lastStartTime = lastStartTime;
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
        if (task != null) {
            return true;
        }
        if (taskRef == null) {
          return false;
        }
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
