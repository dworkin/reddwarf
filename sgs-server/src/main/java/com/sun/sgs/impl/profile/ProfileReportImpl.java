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

package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.profile.TransactionListenerDetail;

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
    private static final Map<String, Long> EMPTY_COUNTER_MAP = 
	Collections.emptyMap();
    
    /**
     * An empty list for returning when no operations have been updated.
     */
    private static final List<String> EMPTY_OPS = Collections.emptyList();
    /**
     * An empty map for returning when no profile samples have been
     * updated.  We need this map as well because typing issues
     * prevent us from using {@link Collections#emptyMap()}.
    */
    private static final Map<String, List<Long>> EMPTY_SAMPLE_MAP = 
	Collections.unmodifiableMap(new HashMap<String, List<Long>>());

    // the final fields, set by the constructor
    final KernelRunnable task;
    final Identity owner;
    final long scheduledStartTime;
    final int readyCount;
    final long actualStartTime;

    // the other fields, set directly by the ProfileCollectorImpl
    byte [] transactionId = null;
    boolean succeeded = false;
    long runningTime = 0;
    int tryCount = 0;
    Throwable throwable = null;
    private AccessedObjectsDetail accessedObjectsDetail = null;

    private Set<ProfileParticipantDetail> participants;
    private Set<TransactionListenerDetail> txnListeners;

    // counters that are updated through methods on this class
    private Map<String, Long> taskCounters;

    // a list of operations performed, which is updated through
    // methods on this class
    private List<String>ops;

    // samples that are added through methods on this class
    private Map<String, List<Long>> taskSamples;

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
                      long scheduledStartTime, int readyCount)
    {
        this.task = task;
        this.owner = owner;
        this.scheduledStartTime = scheduledStartTime;
        this.readyCount = readyCount;
        this.actualStartTime = System.currentTimeMillis();

	participants = new HashSet<ProfileParticipantDetail>();
        txnListeners = new HashSet<TransactionListenerDetail>();

	taskCounters = null;
        ops = null;
	taskSamples = null;
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
            taskCounters = new HashMap<String, Long>();
        } else {
            if (taskCounters.containsKey(counter)) {
                currentValue = taskCounters.get(counter);
            }
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
    void addTaskSample(String sampleName, long value) {
	List<Long> samples;
        if (taskSamples == null) {
            taskSamples = new HashMap<String, List<Long>>();
	    samples = new LinkedList<Long>();
	    taskSamples.put(sampleName, samples);
        } else {
            if (taskSamples.containsKey(sampleName)) {
		samples = taskSamples.get(sampleName);
            } else {
		samples = new LinkedList<Long>();
		taskSamples.put(sampleName, samples);		
	    }
        }
	samples.add(value);
    }
    
    /**
     * Package-private method used to add an operation.
     * 
     * @param operationName name of the operation
     */
    void addOperation(String operationName) {
        if (ops == null) {
            ops = new ArrayList<String>(); 
        }
        ops.add(operationName);
    }

    void addParticipant(ProfileParticipantDetail participantDetail) {
        participants.add(participantDetail);
    }
    
    void addListener(TransactionListenerDetail listenerDetail) {
        txnListeners.add(listenerDetail);
    }

    void setAccessedObjectsDetail(AccessedObjectsDetail detail) {
        accessedObjectsDetail = detail;
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
        return transactionId != null;
    }

    /**
     * {@inheritDoc}
     */
    public byte [] getTransactionId() {
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
    public Set<TransactionListenerDetail> getListenerDetails() {
        return txnListeners;
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
    public List<String> getReportedOperations() {
        return (ops == null) ? EMPTY_OPS : ops;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Long> getUpdatedTaskCounters() {
        return (taskCounters == null) ? EMPTY_COUNTER_MAP : taskCounters;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, List<Long>> getUpdatedTaskSamples() {
	return (taskSamples == null) ? EMPTY_SAMPLE_MAP : taskSamples;
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
		taskCounters = new HashMap<String, Long>(report.taskCounters);
	    } else {
		for (Map.Entry<String, Long> e : 
                    report.taskCounters.entrySet()) 
                {
		    Long curCount = taskCounters.get(e.getKey());
		    taskCounters.put(e.getKey(),
				     (curCount == null) 
				     ? e.getValue()
				     : curCount + e.getValue());
		}
	    }
	}

	if (report.taskSamples != null) {
	    if (taskSamples == null) {
		taskSamples = new HashMap<String, List<Long>>();
		for (Map.Entry<String, List<Long>> e : 
			 report.taskSamples.entrySet()) 
                {
		    // make a copy of the child task's samples
		    List<Long> samples = new LinkedList<Long>(e.getValue());
		    taskSamples.put(e.getKey(), samples);
		}
	    } else {
		for (Map.Entry<String, List<Long>> e : 
			 report.taskSamples.entrySet()) 
                {
		    List<Long> samples = taskSamples.get(e.getKey());
		    if (samples == null) {
			// make a copy of the child task's samples
			taskSamples.put(e.getKey(),
					 new LinkedList<Long>(e.getValue()));
                    } else {
			samples.addAll(e.getValue());
                    }
		}
	    }
	}

	if (report.ops != null) {
	    if (ops == null) {
                ops = new LinkedList<String>(report.ops);
	    } else {
		ops.addAll(report.ops);
	    }
	}

	// NOTE: we do not include the the participant or listener detail
	//       since this is specific to a task and not to its
	//       children.
    }

    /**
     * Package-private method to note that a task has finished running.
     * All collections are modified to be immutable.
     */
    void finish() {
        if (ops != null) {
            ops = Collections.unmodifiableList(ops);
        }
        if (taskCounters != null) {
            taskCounters = Collections.unmodifiableMap(taskCounters);
        }
        if (taskSamples != null) {
            taskSamples = Collections.unmodifiableMap(taskSamples);
        }
        
        participants = Collections.unmodifiableSet(participants);
        txnListeners = Collections.unmodifiableSet(txnListeners);
    }

}
