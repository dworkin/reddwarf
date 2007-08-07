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


    static final String WINDOW_SIZE_PROPERTY =
	"com.sun.sgs.impl.kernel.Kernel.profile.listener.window.size";

    /**
     * The window of tasks that are aggregated before the next text
     * output when none is provided
     */
    private static final int DEFAULT_WINDOW_SIZE = 5000;

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private final int windowSize;


    // long wall-clock time
    private long lastWindowStart;

    // statistics updated for the aggregate window
     private long taskCount;
     private long failedCount;
     private long maxRunTime;
     private long runTime;   
     private long lagTimeSum;
     private long readyCountSum;   


    // statistics for the lifetime of the program
     private long lifetimeCount;
     private long lifetimeMax;
     private long lifetimeFailed;
     private long lifetimeRunTime;
     private long lifetimeLagTime;
     private long lifetimeReadyCountSum;



    public ProfileSummaryListener(Properties properties, TaskOwner owner,
				  TaskScheduler taskScheduler,
				  ResourceCoordinator resourceCoord) {

	lastWindowStart = System.currentTimeMillis();

	taskCount   = 0;
	failedCount = 0;
	runTime     = 0;
	lagTimeSum     = 0;
	maxRunTime  = 0;
	readyCountSum = 0;

	lifetimeCount   = 0;
	lifetimeFailed  = 0;
	lifetimeRunTime = 0;
	lifetimeLagTime = 0;
	lifetimeMax     = 0;
	lifetimeReadyCountSum = 0;

	windowSize = new PropertiesWrapper(properties).
	    getIntProperty(WINDOW_SIZE_PROPERTY, DEFAULT_WINDOW_SIZE);
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
	

	taskCount++;
	readyCountSum += profileReport.getReadyCount();
	lagTimeSum += (profileReport.getActualStartTime() -
		    profileReport.getScheduledStartTime());


	// calculate the run-time only if it was successful
	if (profileReport.wasTaskSuccessful()) {

	    long r = profileReport.getRunningTime();

	    runTime += r;

	    if (r > maxRunTime)
		maxRunTime = r;
	}
	else {
	    failedCount++;
	}
	
	if (taskCount % windowSize == 0) {

	    long windowEndTime = System.currentTimeMillis();	    

	    lifetimeCount += taskCount;
	    lifetimeFailed += failedCount;
	    lifetimeRunTime += runTime;
	    lifetimeLagTime += lagTimeSum;
	    lifetimeReadyCountSum += readyCountSum;
	    
	    if (maxRunTime > lifetimeMax)
		lifetimeMax = maxRunTime;
	    
	    double successful = taskCount - failedCount;
	    double lifetimeSuccessful = lifetimeCount - lifetimeFailed;

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
			      taskCount, 
			      runTime/ successful,
			      maxRunTime, 
			      failedCount, 
			      (failedCount * 100) / (double)taskCount,
			      readyCountSum / (double)taskCount,
			      lagTimeSum / successful,
			      ((double)runTime / 
			       (double)(windowEndTime - lastWindowStart)),	
			      lifetimeCount, 
			      lifetimeRunTime / lifetimeSuccessful,
			      lifetimeMax, 
			      lifetimeFailed, 
			      (lifetimeFailed*100) / (double)lifetimeCount,
			      lifetimeReadyCountSum / (double)lifetimeCount,
			      lifetimeLagTime / lifetimeSuccessful);

 	    maxRunTime = 0;
 	    failedCount = 0;
 	    runTime = 0;	   
 	    lagTimeSum = 0;	
 	    taskCount = 0;
 	    readyCountSum = 0;	
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