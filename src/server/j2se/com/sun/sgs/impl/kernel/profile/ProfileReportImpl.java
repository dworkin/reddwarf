/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileParticipantDetail;
import com.sun.sgs.kernel.ProfileReport;
import com.sun.sgs.kernel.TaskOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Package-private implementation of <code>ProfileReport</code>.
 */
class ProfileReportImpl implements ProfileReport {

    /**
     * An empty map for returning when no profile counters have been
     * updated.
     */
    private static final Map<String,Long> EMPTY_MAP = 
	Collections.emptyMap();

    /**
     * An empty map for returning when no profile samples have been
     * updated.
     */
    // NOTE: we need this map as well because typing issues prevent us
    // from using Collections.emptyMap()
    private static final Map<String,List<Long>> EMPTY_SAMPLE_MAP = 
	Collections.unmodifiableMap(new HashMap<String,List<Long>>());



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
    Exception exception = null;

    List<ProfileOperation> ops = new ArrayList<ProfileOperation>();
    Set<ProfileParticipantDetail> participants =
        new HashSet<ProfileParticipantDetail>();

    // counters that are updated through methods on this class
    Map<String,Long> aggCounters = null;
    Map<String,Long> taskCounters = null;

    Map<String,List<Long>> localSamples;
    Map<String,List<Long>> lifetimeSamples;

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
	
	localSamples = null;
	lifetimeSamples = null;
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
     *
     */
    void addLocalSample(String sampleName, long value) {
	List<Long> samples;
        if (localSamples == null) {
            localSamples = new HashMap<String,List<Long>>();
	    samples = new LinkedList<Long>();
	    localSamples.put(sampleName, samples);
        } else {
            if (localSamples.containsKey(sampleName))
		samples = localSamples.get(sampleName);
	    else {
		samples = new LinkedList<Long>();
		localSamples.put(sampleName, samples);		
	    }
        }
	samples.add(value);
    }

    /**
     *
     */
    void registerLifetimeSamples(String sampleName, 
				 List<Long> samples) {
        if (lifetimeSamples == null) {
            lifetimeSamples = new HashMap<String,List<Long>>();
	    lifetimeSamples.put(sampleName, 
				Collections.unmodifiableList(samples));
        } 
	else if (!lifetimeSamples.containsKey(sampleName))
	    lifetimeSamples.put(sampleName, 
				Collections.unmodifiableList(samples));
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
        return (aggCounters == null) ? EMPTY_MAP : aggCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,Long> getUpdatedTaskCounters() {
        return (taskCounters == null) ? EMPTY_MAP : taskCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,List<Long>> getUpdatedTaskSamples() {
	return (localSamples == null) ? EMPTY_SAMPLE_MAP : localSamples;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,List<Long>> getUpdatedLifetimeSamples() {
	return (lifetimeSamples == null) ? EMPTY_SAMPLE_MAP : lifetimeSamples;
    }


    /**
     * {@inheritDoc}
     */
    public int getReadyCount() {
        return readyCount;
    }

    /**
     * {@inheritDoc}
     */
    public Exception getException() {
	return exception;
    }

}
