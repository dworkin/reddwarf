
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

/**
 * A text-output listener that generates task-periodic summary reports
 * about the performance of tasks being exexcuted by a server.  Each
 * report summaries a fixed number of tasks.  The number of tasks
 * summarized can be modified by setting the {@code
 * com.sun.sgs.kernel.profile.summary.window} property in the system
 * properties.  The default number of tasks is {@code 5000}.
 *
 * <p>
 * 
 * Each report is structed like the following example:
 * <p><pre>
 * past 5000 tasks:
 *   mean: 2.94ms,  max: 21ms,  failed 25 (0.50%),
 *   mean ready count: 4.09,  mean lag time: 11.87ms, 
 *   parallelism factor: 0.99
 * all 104302000 tasks:
 *   mean: 2.64ms,  max: 1010ms,  failed 1713 (0.62%),
 *   mean ready count: 3.98,  mean lag time: 9.75ms
 * </pre>
 *
 * @see HistogramListener
 */
public class ProfileSummaryListener implements ProfileOperationListener {

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private static final int WINDOW_SIZE = 5000;

    // long wall-clock time
    private long lastWindowStart;

    // statistics updated for the aggregate window
    private final AtomicLong taskCount;
    private final AtomicLong failedCount;
    private final AtomicLong maxRunTime;
    private final AtomicLong runTime;   
    private final AtomicLong lagTime;
    private final AtomicLong readyCountSum;   

    // statistics for the lifetime of the program
    private final AtomicLong lifetimeCount;
    private final AtomicLong lifetimeMax;
    private final AtomicLong lifetimeFailed;
    private final AtomicLong lifetimeRunTime;
    private final AtomicLong lifetimeLagTime;
    private final AtomicLong lifetimeReadyCountSum;



    public ProfileSummaryListener(Properties properties, TaskOwner owner,
				  TaskScheduler taskScheduler,
				  ResourceCoordinator resourceCoord) {

	lastWindowStart = System.currentTimeMillis();

	taskCount   = new AtomicLong(0);
	failedCount = new AtomicLong(0);
	runTime     = new AtomicLong(0);
	lagTime     = new AtomicLong(0);
	maxRunTime  = new AtomicLong(0);
	readyCountSum = new AtomicLong(0);

	lifetimeCount   = new AtomicLong(0);
	lifetimeFailed  = new AtomicLong(0);
	lifetimeRunTime = new AtomicLong(0);
	lifetimeLagTime = new AtomicLong(0);
	lifetimeMax     = new AtomicLong(0);
	lifetimeReadyCountSum = new AtomicLong(0);

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
	long readyCount = 
	    readyCountSum.addAndGet(profileReport.getReadyCount());
	long lagTimeSum = lagTime.addAndGet(profileReport.getActualStartTime() -
					    profileReport.getScheduledStartTime());

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

	    long windowEndTime = System.currentTimeMillis();	    

	    // prepare the output and update data 	    
	    long max = maxRunTime.get();
	    long failed = failedCount.get();
	    long time = runTime.get();

	    // update all the lifetime variables
	    long lCount = lifetimeCount.addAndGet(count);
	    long lFailed = lifetimeFailed.addAndGet(failed);
	    long lRuntime = lifetimeRunTime.addAndGet(time);
	    long lLagtime = lifetimeLagTime.addAndGet(lagTimeSum);
	    long lReadyCount = lifetimeReadyCountSum.addAndGet(readyCount);

	    long lMax = lifetimeMax.get();
	    if (lMax < max) {
		lifetimeMax.set(max);
		lMax = max;
	    }


	    // reset the windowed variables
	    maxRunTime.set(0);
	    failedCount.set(0);
	    runTime.set(0);	   
	    lagTime.set(0);	
	    taskCount.set(0);
	    readyCountSum.set(0);

	    System.out.printf("past %d tasks:\n"
			      + "  mean: %4.2fms,"
			      + "  max: %6dms,"
			      + "  failed: %d (%2.2f%%)," 
			      + "\n  mean ready count: %.2f,"
			      + "  mean lag time: %.2fms,"
			      + "\n  parallelism factor: %.2f\n"
			      + "all %d tasks:\n"
			      + "  mean: %4.2fms,"
			      + "  max: %6dms,"
			      + "  failed: %d (%2.2f%%),"
			      + "\n  mean ready count: %.2f,"
			      + "  mean lag time: %.2fms\n",
			      count, 
			      (double)time/ (double)(count - failed),
			      max, 
			      failed, 
			      (failed * 100 / (double)count),
			      (double)readyCount / (double)count,
			      (double)lagTimeSum / (double)(count - failed),
			      ((double)time / 
			       (double)(windowEndTime - lastWindowStart)),	
			      lCount, 
			      (double)lRuntime/(double)(lCount - lFailed),
			      lMax, 
			      lFailed, 
			      (lFailed *100 / (double)(lCount)),
			      (double)lReadyCount / (double)lCount,			      
			      (double)lLagtime / (double)(lCount - lFailed));
	

	    lastWindowStart = System.currentTimeMillis();
	}       
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// don't care
    }
    
    

}