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

package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.ProfileReport;

import java.math.BigInteger;

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
    private static final Map<String,Long> EMPTY_COUNTER_MAP = 
	Collections.emptyMap();

    /**
     * An empty map for returning when no profile samples have been
     * updated.  We need this map as well because typing issues
     * prevent us from using {@link Collections#emptyMap()}.
    */
    private static final Map<String,List<Long>> EMPTY_SAMPLE_MAP = 
	Collections.unmodifiableMap(new HashMap<String,List<Long>>());

    // the final fields, set by the constructor
    final KernelRunnable task;
    final Identity owner;
    final long scheduledStartTime;
    final int readyCount;
    final long actualStartTime;

    // the other fields, set directly by the ProfileCollectorImpl
    boolean transactional = false;
    BigInteger transactionId;
    boolean succeeded = false;
    long runningTime = 0;
    int tryCount = 0;
    Throwable throwable = null;
    AccessedObjectsDetail accessedObjectsDetail = null;

    Set<ProfileParticipantDetail> participants;

    // counters that are updated through methods on this class
    Map<String,Long> aggCounters;
    Map<String,Long> taskCounters;

    // a list of operations performed, which is updated through
    // methods on this class
    List<ProfileOperation> ops;

    // samples that are aggregated through methods on this class
    Map<String,List<Long>> localSamples;
    Map<String,List<Long>> aggregateSamples;

    /**
     * Creates an instance of <code>ProfileReportImpl</code> with the
     * actual starting time being set to the current time.
     *
     * @param task the <code>KernelRunnable</code> being reported on
     * @param owner the <code>Identity</code> that owns the task
     * @param scheduledStartTime the time the task was scheduled to run
     * @param readyCount the number of tasks in the scheduler, ready to run,
     *                   that are associated with the same context as the task
     */
    ProfileReportImpl(KernelRunnable task, Identity owner,
                      long scheduledStartTime, int readyCount) {
        this.task = task;
        this.owner = owner;
        this.scheduledStartTime = scheduledStartTime;
        this.readyCount = readyCount;
        this.actualStartTime = System.currentTimeMillis();

	ops = new ArrayList<ProfileOperation>();
	participants = new HashSet<ProfileParticipantDetail>();
	aggCounters = null;
	taskCounters = null;
	localSamples = null;
	aggregateSamples = null;
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
     * Package-private method used to add to a task-local sample. If
     * this sample hasn't had a value reported yet for this task, then
     * a new list is made and the the provided value is added to it.
     *
     * @param sampleName the name of the sample
     * @param value the latest value for the sample
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
     * Package-private method used to add to an aggregate sample. If
     * this sample hasn't had a value reported yet for this task, then
     * a new list is made and the the provided value is added to it.
     *
     * @param sampleName the name of the sample
     * @param samples the list of all samples for this name
     */
    void registerAggregateSamples(String sampleName, 
				 List<Long> samples) {
	// NOTE: we make the list unmodifiable so that the user cannot
	// alter any of the samples.  This is important since the same
	// list is used for the lifetime of the application.
        if (aggregateSamples == null) {
            aggregateSamples = new HashMap<String,List<Long>>();
	    aggregateSamples.put(sampleName, 
				Collections.unmodifiableList(samples));
        } 
	else if (!aggregateSamples.containsKey(sampleName))
	    aggregateSamples.put(sampleName, 
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
    public Identity getTaskOwner() {
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
    public BigInteger getTransactionId() {
        return transactionId;
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
        return (aggCounters == null) ? EMPTY_COUNTER_MAP : aggCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,Long> getUpdatedTaskCounters() {
        return (taskCounters == null) ? EMPTY_COUNTER_MAP : taskCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String,List<Long>> getUpdatedAggregateSamples() {
	return (aggregateSamples == null) ? EMPTY_SAMPLE_MAP : aggregateSamples;
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
    public AccessedObjectsDetail getAccessedObjectsDetail() {
        return accessedObjectsDetail;
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
    public Throwable getFailureCause() {
	return throwable;
    }

    /**
     * Package-private method used to merge the state of one report into
     * another. This is typically used when a nested, profiled task
     * completes and needs to share its data with its parent.
     */
    void merge(ProfileReportImpl report) {
	
	// for each of the child task counters and samples, we first
	// check whether the task recorded any data.  If so, then we
	// copy the data to this report.      

	if (report.taskCounters != null) {
	    if (taskCounters == null) {
		taskCounters = new HashMap<String,Long>(report.taskCounters);
	    }
	    else {
		for (Map.Entry<String,Long> e : 
			 report.taskCounters.entrySet()) {
		    Long curCount = taskCounters.get(e.getKey());
		    taskCounters.put(e.getKey(),
				     (curCount == null) 
				     ? e.getValue()
				     : curCount + e.getValue());
		}
	    }
	}

	if (report.localSamples != null) {
	    if (localSamples == null) {
		localSamples = new HashMap<String,List<Long>>();
		for (Map.Entry<String,List<Long>> e : 
			 report.localSamples.entrySet()) {
		    
		    // make a copy of the child task's samples
		    List<Long> samples = new LinkedList<Long>(e.getValue());
		    localSamples.put(e.getKey(), samples);
		}
	    }
	    else {
		for (Map.Entry<String,List<Long>> e : 
			 report.localSamples.entrySet()) {
		    
		    List<Long> samples = localSamples.get(e.getKey());
		    if (samples == null) 
			// make a copy of the child task's samples
			localSamples.put(e.getKey(),
					 new LinkedList<Long>(e.getValue()));
		    else
			samples.addAll(e.getValue());
		}
	    }
	}

	if (report.ops != null) {
	    if (ops == null) {
		ops = new LinkedList<ProfileOperation>(report.ops);
	    }
	    else {
		ops.addAll(report.ops);
	    }
	}

	// NOTE: we do not need to update the aggregateSamples and
	//       aggregateCounters, as this is being collected across
	//       all tasks, and so by updating we would really be
	//       double counting the data

	// NOTE: we do not include the the participants information
	//       since this is specific to a task and not to its
	//       children.
    }

}
