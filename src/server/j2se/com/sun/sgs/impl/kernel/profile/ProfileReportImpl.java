/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileReport;
import com.sun.sgs.kernel.TaskOwner;

import java.util.ArrayList;
import java.util.List;


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

    // the other fields, set directly by the ProfileCollector
    boolean transactional = false;
    boolean succeeded = false;
    long runningTime = 0;
    int tryCount = 0;
    List<ProfileOperation> ops = new ArrayList<ProfileOperation>();

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
    public int getReadyCount() {
        return readyCount;
    }

}
