
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

public class RunTimeListener implements ProfileOperationListener {

    /** 
     * Wether to keep per-identity statistics.  These statistics are
     * expensive, but useful in debugging a small number of clients.
     */
    private static final boolean INCLUDE_IDENTITY_STATS = false;

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private static final int WINDOW_SIZE = 500;

    // statistics updated for the aggregate window
    private final AtomicLong taskCount;
    private final AtomicLong failedCount;
    private final AtomicLong maxRunTime;
    private final AtomicLong runTime;   
    private final Map<String, AtomicLong> identityTaskCount;
    private final Map<String, AtomicLong> identityRunTime;

    // statistics for the lifetime of the program
    private final AtomicLong lifetimeCount;
    private final AtomicLong lifetimeMax;
    private final AtomicLong lifetimeFailed;
    private final AtomicLong lifetimeRunTime;
    private final Map<String, AtomicLong> lifetimeIdentityCount;
    private final Map<String, AtomicLong> lifetimeIdentityRunTime;



    public RunTimeListener(Properties properties, TaskOwner owner,
			   TaskScheduler taskScheduler,
			   ResourceCoordinator resourceCoord) {

	taskCount   = new AtomicLong(0);
	failedCount = new AtomicLong(0);
	runTime     = new AtomicLong(0);
	maxRunTime  = new AtomicLong(0);
	identityTaskCount = new HashMap<String, AtomicLong>();
	identityRunTime = new HashMap<String, AtomicLong>();

	lifetimeCount   = new AtomicLong(0);
	lifetimeFailed  = new AtomicLong(0);
	lifetimeRunTime = new AtomicLong(0);
	lifetimeMax     = new AtomicLong(0);
	lifetimeIdentityCount = new HashMap<String, AtomicLong>();
	lifetimeIdentityRunTime = new HashMap<String, AtomicLong>();

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

	if (INCLUDE_IDENTITY_STATS) {
	    // keep per-user statistics
	    String identity = 
		profileReport.getTaskOwner().getIdentity().getName();
	    
	    AtomicLong identityCount = identityTaskCount.get(identity);
	    AtomicLong identityTime = identityRunTime.get(identity);
	    if (identityCount == null) {
		synchronized(identityTaskCount) {
		    if (identityTaskCount.containsKey(identity)) {
			identityCount = identityTaskCount.get(identity);
			identityTime = identityRunTime.get(identity);
		    }
		    else {
			identityCount = new AtomicLong(0);
			identityTaskCount.put(identity,identityCount);
			identityTime = new AtomicLong(0);
			identityRunTime.put(identity,identityTime);
		    }
		}
	    }
	    
	    identityCount.incrementAndGet();
	    identityTime.addAndGet(profileReport.getRunningTime()); 
	}

	// calculate the run-time only if it was successful
	if (profileReport.wasTaskSuccessful()) {
	    runTime.addAndGet(profileReport.getRunningTime());

	    long maxTime = maxRunTime.get();
	    if (maxTime < profileReport.getRunningTime())
		maxRunTime.compareAndSet(maxTime, 
					 profileReport.getRunningTime());
	}
	else {
	    failedCount.incrementAndGet();
	}
	
	if (count % WINDOW_SIZE == 0) {

	    // prepare the output and update data 	    
	    long max = maxRunTime.get();
	    long failed = failedCount.get();
	    long time = runTime.get();

	    // update all the lifetime variables
	    long lCount = lifetimeCount.addAndGet(count);
	    long lFailed = lifetimeFailed.addAndGet(failed);
	    long lRuntime = lifetimeRunTime.addAndGet(time);

	    long lMax = lifetimeMax.get();
	    if (lMax < max) {
		lifetimeMax.set(max);
		lMax = max;
	    }

	    // conditionally load these
	    String stats = null, lStats = null;
	    if (INCLUDE_IDENTITY_STATS) {
		stats = getPerIdentityString(identityTaskCount,
					     identityRunTime);
		updateIdentityStats();
		lStats = getPerIdentityString(lifetimeIdentityCount,
					      lifetimeIdentityRunTime);
	    }

	    // reset the windowed variables
	    maxRunTime.set(0);
	    failedCount.set(0);
	    runTime.set(0);	   
	    if (INCLUDE_IDENTITY_STATS)
		identityTaskCount.clear(); // maybe just clear values?

	    if (INCLUDE_IDENTITY_STATS) {
		System.out.printf("past %d tasks: mean: %fms, max: %dms, "
				  + "# of failed: %d, "
				  + "user statistics: \n%s\n"
				  + "all %d tasks: mean: %fms, max: %dms, "
				  + "# of failed: %d, "
				  + "user statistics: \n%s\n",			      
				  count, (double)time/ (double)count, max, 
				  failed, stats,
				  lCount, (double)lRuntime/(double)lCount,
				  lMax, lFailed, lStats);
	    }
	    else {
		System.out.printf("past %d tasks: mean: %fms, max: %dms, "
				  + "# of failed %d\n"
				  + "all %d tasks: mean: %fms, max: %dms, "
				  + " # of failed %d\n",			      
				  count, (double)time/ (double)count, max, 
				  failed, 
				  lCount, (double)lRuntime/(double)lCount,
				  lMax, lFailed);
	    }
	}
    }

    /**
     * Returns a string representation of task counts and execution
     * times for each name in the map.  Note that the keyset of each
     * map should be identical.
     */
    private static String getPerIdentityString(Map<String,AtomicLong> counts,
					       Map<String,AtomicLong> times) {
	StringBuilder ret = new StringBuilder(48);
	for (String name : counts.keySet()) {
	    long tasks = counts.get(name).get();
	    long time  = times.get(name).get();
	    ret.append("\t").append(name).append(": ").
		append(tasks).append(" tasks, ").
		append(time).append("ms (total), ").
		append((double)time/(double)tasks).append("ms (avg.)\n");
	}
	return ret.toString();
    }

    /**
     * Updates the per-identity statistics from the window map to the
     * lifetime map.
     */
    // note that this method has been pulled out to ease inlining.
    private void updateIdentityStats() {
	for (String id : identityTaskCount.keySet()) {
	    long count = identityTaskCount.get(id).get();
	    long time  = identityRunTime.get(id).get();
	    if (lifetimeIdentityCount.containsKey(id)) {
		lifetimeIdentityCount.get(id).addAndGet(count);
		lifetimeIdentityRunTime.get(id).addAndGet(time);
	    }
	    else {
		lifetimeIdentityCount.put(id, new AtomicLong(count));
		lifetimeIdentityRunTime.put(id, new AtomicLong(time));
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