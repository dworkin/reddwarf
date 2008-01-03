/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileProperties;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicLong;


/**
 * A text-output listener that generates task-periodic summary reports
 * about the performance of tasks being executed by a server.  Each
 * report summaries a fixed number of tasks.  The number of tasks
 * summarized can be modified by setting the {@code
 * com.sun.sgs.profile.listener.window.size} property in the system
 * properties.  The default number of tasks is {@code 5000}.
 *
 * <p>
 * 
 * Each report is structured like the following example:
 * <p><pre>
 * past 5000 tasks:
 *   mean: 2.94ms,  max: 21ms,  failed 25 (0.50%),
 *   mean ready count: 4.09,  mean lag time: 11.87ms, 
 *   parallelism factor: 0.99
 *   mean throughput: 88 txn/sec,  mean latency: 32.14 ms/txn
 * all 104302000 tasks:
 *   mean: 2.64ms,  max: 1010ms,  failed 1713 (0.62%),
 *   mean ready count: 3.98,  mean lag time: 9.75ms
 *   mean throughput: 88 txn/sec,  mean latency: 32.14 ms/txn
 * </pre>
 *
 * <p>
 *
 * Note that the mean, max, mean throughput, and mean latency reports only
 * apply to successful tasks.
 *
 * @see ProfileProperties
 */
public class ProfileSummaryListener implements ProfileListener {

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
     private long lifetimeWindowTime;

    /**
     * Creates an instance of {@code ProfileSummaryListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code TaskOwner} to use for all tasks run by
     *        this listener
     * @param taskScheduler the {@code TaskScheduler} to use for
     *        running short-lived or recurring tasks
     * @param resourceCoord the {@code ResourceCoordinator} used to
     *        run any long-lived tasks
     *
     */
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
	lifetimeWindowTime = 0;

	windowSize = new PropertiesWrapper(properties).
	    getIntProperty(ProfileProperties.WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
	

	taskCount++;
	readyCountSum += profileReport.getReadyCount();


	// calculate the run-time and lag-time only if it was successful
	if (profileReport.wasTaskSuccessful()) {

	    long r = profileReport.getRunningTime();

	    runTime += r;

	    if (r > maxRunTime)
		maxRunTime = r;

	    lagTimeSum += (profileReport.getActualStartTime() -
			   profileReport.getScheduledStartTime());
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
	    lifetimeWindowTime += (windowEndTime - lastWindowStart);
	    
	    if (maxRunTime > lifetimeMax)
		lifetimeMax = maxRunTime;
	    
	    double successful = taskCount - failedCount;
	    double lifetimeSuccessful = lifetimeCount - lifetimeFailed;

	    System.out.printf("past %d tasks:%n"
			      + "  mean: %4.2fms,"
			      + "  max: %6dms,"
			      + "  failed: %d (%2.2f%%)," 
			      + "%n  mean ready count: %.2f,"
			      + "  mean lag time: %.2fms,"
			      + "%n  parallelism factor: %.2f"
			      + "%n  mean throughput: %.2f txn/sec,"
			      + "  mean latency: %.2f ms/txn%n"
			      + "all %d tasks:%n"
			      + "  mean: %4.2fms,"
			      + "  max: %6dms,"
			      + "  failed: %d (%2.2f%%),"
			      + "%n  mean ready count: %.2f,"
			      + "  mean lag time: %.2fms"
			      + "%n  mean throughput: %.2f txn/sec,"
			      + "  mean latency: %.2f ms/txn%n",
			      taskCount, 
			      runTime/ successful,
			      maxRunTime, 
			      failedCount, 
			      (failedCount * 100) / (double)taskCount,
			      readyCountSum / (double)taskCount,
			      lagTimeSum / successful,
			      ((double)runTime / 
			       (double)(windowEndTime - lastWindowStart)),	
			      ((successful*1000) /
			       (double)(windowEndTime - lastWindowStart)),
			      (runTime + lagTimeSum) / successful,
			      lifetimeCount, 
			      lifetimeRunTime / lifetimeSuccessful,
			      lifetimeMax, 
			      lifetimeFailed, 
			      (lifetimeFailed*100) / (double)lifetimeCount,
			      lifetimeReadyCountSum / (double)lifetimeCount,
			      lifetimeLagTime / lifetimeSuccessful,
			      ((lifetimeSuccessful*1000) /
			       (double)lifetimeWindowTime),
			      ((lifetimeRunTime + lifetimeLagTime) /
			       lifetimeSuccessful)
		);

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
	// unused
    }
    
    

}
