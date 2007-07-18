/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileParticipantDetail;
import com.sun.sgs.kernel.ProfileReport;
import com.sun.sgs.kernel.TaskOwner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Package-private implementation of <code>ProfileReport</code>.
 */
class ProfileReportImpl implements ProfileReport {

    // the final fields, set by the constructor
    final KernelRunnable task;
    final TaskOwner owner;
    final long scheduledStartTime;
    final int readyCount;
    final long actualStartTime;

    // the other fields, set directly by the ProfileCollectorImpl
    boolean transactional = false;
    boolean succeeded = false;
    long runningTime = 0;
    int tryCount = 0;
    List<ProfileOperation> ops = new ArrayList<ProfileOperation>();
    Set<ProfileParticipantDetail> participants =
        new HashSet<ProfileParticipantDetail>();

    // counters that are updated through methods on this class
    Map<String,Long> aggCounters = null;
    Map<String,Long> taskCounters = null;

    /**
     * Creates an instance of <code>ProfileReportImpl</code> with the
     * actual starting time being set to the current time.
     *
     * @param task the <code>KernelRunnable</code> being reported on
     * @param owner the <code>TaskOwner</code> for the given task
     * @param scheduledStartTime the time the task was scheduled to run
     * @param readyCount the number of tasks in the scheduler, ready to run,
     *                   that are associated with the same context as the task
     */
    ProfileReportImpl(KernelRunnable task, TaskOwner owner,
                      long scheduledStartTime, int readyCount) {
        this.task = task;
        this.owner = owner;
        this.scheduledStartTime = scheduledStartTime;
        this.readyCount = readyCount;
        this.actualStartTime = System.currentTimeMillis();
    }

    /**
     * Package-private method used to update aggregate counters that were
     * changed during this task.
     *
     * @param counter the name of the counter
     * @param value the new value of this counter
     */
    void updateAggregateCounter(String counter, long value) {
        if (aggCounters == null)
            aggCounters = new HashMap<String,Long>();
        aggCounters.put(counter, value);
    }

    /**
     * Package-private method used to increment task-local counters
     * that were changed during this task. If this counter hasn't had a
     * value reported yet for this task, then the provided value is
     * set as the current value for the counter.
     *
     * @param counter the name of the counter
     * @param value the amount to increment the counter
     */
    void incrementTaskCounter(String counter, long value) {
        long currentValue = 0;
        if (taskCounters == null) {
            taskCounters = new HashMap<String,Long>();
        } else {
            if (taskCounters.containsKey(counter))
                currentValue = taskCounters.get(counter);
        }
        taskCounters.put(counter, currentValue + value);
    }

    /**
     * {@inheritDoc}
     */
    public KernelRunnable getTask() {
        return task;
    }

    /**
     * {@inheritDoc}
     */
    public TaskOwner getTaskOwner() {
        return owner;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasTaskTransactional() {
        return transactional;
    }

    /**
     * {@inheritDoc}
     */
    public Set<ProfileParticipantDetail> getParticipantDetails() {
        return participants;
    }

    /**
     * {@inheritDoc}
     */
    public boolean wasTaskSuccessful() {
        return succeeded;
    }

    /**
     * {@inheritDoc}
     */
    public long getScheduledStartTime() {
        return scheduledStartTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getActualStartTime() {
        return actualStartTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getRunningTime() {
        return runningTime;
    }

    /**
     * {@inheritDoc}
     */
    public int getRetryCount() {
        return tryCount;
    }

    /**
     * {@inheritDoc}
     */
    public List<ProfileOperation> getReportedOperations() {
        return ops;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,Long> getUpdatedAggregateCounters() {
        return aggCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,Long> getUpdatedTaskCounters() {
        return taskCounters;
    }

    /**
     * {@inheritDoc}
     */
    public int getReadyCount() {
        return readyCount;
    }

}
