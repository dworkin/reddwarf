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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileProperties;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;
import java.util.Properties;

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
 *   runtime mean: 2.94ms,  max: 21ms,  failed 25 (0.50%),
 *   ready count: 4.09,  lag time: 11.87ms, 
 *   parallelism factor: 0.99
 *   throughput: 88 txn/sec,  latency: 32.14 ms/txn
 * all 104302000 tasks, moving averages:
 *   runtime: 2.64ms,  failed: 0.62%,
 *   ready count: 3.98,  lag time: 9.75ms
 *   throughput: 88 txn/sec,  latency: 32.14 ms/txn
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
     * The scaling factor for the current window when computing the exponential
     * moving average.  The value represents the proportion of the value for
     * the current period to include when creating the average, and should be
     * between 0 and 1.
     */
    private static final double EMA_SCALE_FACTOR = 0.25;

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private final int windowSize;

    // long wall-clock time
    private long lastWindowStart;

    // statistics updated for the aggregate window
    private long taskCount = 0;
    private long failedCount = 0;
    private long maxRunTime = 0;
    private long runTime = 0;
    private long lagTimeSum = 0;
    private long readyCountSum = 0;   

    // statistics for the lifetime of the program
    private long lifetimeCount = 0;

    // Exponential moving averages (EMAs)
    private double emaRunTime = 0;
    private double emaFailed = 0;
    private double emaReadyCount = 0;
    private double emaLagTime = 0;
    private double emaThroughput = 0;
    private double emaLatency = 0;

    /**
     * Creates an instance of {@code ProfileSummaryListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     */
    public ProfileSummaryListener(Properties properties, Identity owner,
                                  ComponentRegistry registry) {

	lastWindowStart = System.currentTimeMillis();

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

	    double successful = taskCount - failedCount;

	    double meanRunTime = runTime / successful;
	    double meanFailed = (failedCount * 100) / ((double) taskCount);
	    double meanReadyCount = readyCountSum / ((double) taskCount);
	    double meanLagTime = lagTimeSum / successful;
	    double meanThroughput =
		(successful*1000) / (double) (windowEndTime - lastWindowStart);
	    double meanLatency = (runTime + lagTimeSum) / successful;

	    emaRunTime = ema(meanRunTime, emaRunTime);
	    emaFailed = ema(meanFailed, emaFailed);
	    emaReadyCount = ema(meanReadyCount, emaReadyCount);
	    emaLagTime = ema(meanLagTime, emaLagTime);
	    emaThroughput = ema(meanThroughput, emaThroughput);
	    emaLatency = ema(meanLatency, emaLatency);

	    System.out.printf("past %d tasks:%n"
			      + "  runtime mean: %4.2fms,"
			      + "  max: %6dms,"
			      + "  failed: %d (%2.2f%%)," 
			      + "%n  ready count: %.2f,"
			      + "  lag time: %.2fms,"
			      + "%n  parallelism factor: %.2f"
			      + "%n  throughput: %.2f txn/sec,"
			      + "  latency: %.2f ms/txn%n",
			      taskCount, 
			      meanRunTime,
			      maxRunTime, 
			      failedCount, 
			      meanFailed,
			      meanReadyCount,
			      meanLagTime,
			      ((double)runTime / 
			       (double)(windowEndTime - lastWindowStart)),	
			      meanThroughput,
			      meanLatency);
	    System.out.printf("all %d tasks, moving averages:%n"
			      + "  runtime: %4.2fms,"
			      + "  failed: %2.2f%%,"
			      + "%n  ready count: %.2f,"
			      + "  lag time: %.2fms"
			      + "%n  throughput: %.2f txn/sec,"
			      + "  latency: %.2f ms/txn%n",
			      lifetimeCount, 
			      emaRunTime,
			      emaFailed,
			      emaReadyCount,
			      emaLagTime,
			      emaThroughput,
			      emaLatency);

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
     * Computes the exponential moving average of the value for the current
     * period and the previous average.  If the previous value is 0, then uses
     * the current period value.  Otherwise, creates a weighted average of the
     * two values.
     */
    private static final double ema(double periodValue, double emaValue) {
	return emaValue == 0
	    ? periodValue
	    : (EMA_SCALE_FACTOR * periodValue
	       + (1 - EMA_SCALE_FACTOR) * emaValue);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }
    
    

}
