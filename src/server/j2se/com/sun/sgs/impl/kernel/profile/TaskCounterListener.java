
/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.ProfileReport;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicLong;

public class TaskCounterListener implements ProfileOperationListener {

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private static final int WINDOW_SIZE = 500;

    // statistics updated for the aggregate window
    private final AtomicLong taskCount;
    private final Map<String, AtomicLong> taskCounters;


    // statistics for the lifetime of the program
    private final AtomicLong lifetimeCount;
    private final Map<String, AtomicLong> lifetimeTaskCounters;



    public TaskCounterListener(Properties properties, TaskOwner owner,
			   TaskScheduler taskScheduler,
			   ResourceCoordinator resourceCoord) {

	taskCount   = new AtomicLong(0);
	taskCounters = new HashMap<String, AtomicLong>();

	lifetimeCount   = new AtomicLong(0);
	lifetimeTaskCounters = new HashMap<String, AtomicLong>();

    }

    /**
     * {@inheritDoc}
     */
    public void notifyNewOp(ProfileOperation op) {
	// don't care
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadCount(int schedulerThreadCount) {
	// don't care
    }


    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
	
	long count = taskCount.incrementAndGet();
	Map<String,Long> m = profileReport.getUpdatedTaskCounters();
	if (m == null)
	    return;
	for (String name : m.keySet()) {
	    AtomicLong counter = taskCounters.get(name);	    
	    
	    if (counter == null) {
		synchronized(taskCounters) {
		    if (taskCounters.containsKey(name)) {
			counter = taskCounters.get(name);
		    }
		    else {
			counter = new AtomicLong(0);
			taskCounters.put(name, counter);
		    }
		}
	    }
	    
	    counter.addAndGet(m.get(name).longValue());
	}
	
	if (count % WINDOW_SIZE == 0) {
	    // reset the counter for the next entry
	    taskCount.set(0);
	    long lCount = lifetimeCount.addAndGet(count);

	    // conditionally load these
	    String stats = null, lStats = null;
	    stats = getCounterString(taskCounters, count);
	    updateLifetimeCounters();
	    taskCounters.clear();
	    lStats = getCounterString(lifetimeTaskCounters, lCount);
	

	    // reset the windowed variables	    
	    System.out.printf("past %d tasks averages: \n%s"
			      + "all %d tasks averages: \n%s",
			      count, stats, lCount, lStats);
	}	
    }

    /**
     * Returns a string representation of the average of each task
     * counter for the number of tasks executed.
     */
    private static String getCounterString(Map<String,AtomicLong> counters,
					   long tasks) {
	StringBuilder ret = new StringBuilder(128);
	for (String name : counters.keySet()) {
	    ret.append("\t").append(name).append(": ").
		append(String.format("%.2f",(counters.get(name).get() / (double)tasks))).
		append("\n");
	}
	return ret.toString();
    }

    /**
     * Updates the counter statistics from the window map to the
     * lifetime map.
     */
    // note that this method has been pulled out to ease inlining.
    private void updateLifetimeCounters() {
	for (String s : taskCounters.keySet()) {	    
	    if (lifetimeTaskCounters.containsKey(s)) {
		lifetimeTaskCounters.get(s).
		    addAndGet(taskCounters.get(s).get());
	    }
	    else {
		lifetimeTaskCounters.
		    put(s, new AtomicLong(taskCounters.get(s).get()));
	    }
	}
    }


    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// don't care
    }
    
    

}