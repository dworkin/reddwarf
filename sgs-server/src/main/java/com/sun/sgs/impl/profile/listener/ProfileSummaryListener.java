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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileListener;
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
 *   mean runtime: 1.21ms,  max:    216ms,  failed: 33 (0.17%)
 *   mean ready count: 1.64,  mean lag time: 0.18ms
 *   mean tasks running concurrently: 1.05
 *   mean throughput: 861.46 txn/sec,  mean latency: 1.39 ms/txn
 * past 25000 tasks:
 *   mean runtime: 1.24ms,  failed: 0.99%
 *   mean ready count: 3.70,  mean lag time: 2.37ms
 *   mean throughput: 804.74 txn/sec,  mean latency: 3.62 ms/txn
 * </pre>
 *
 * <p>
 *
 * Note that the mean runtime, max, mean lag time, mean throughput, and mean
 * latency reports only apply to successful tasks.
 *
 */
public class ProfileSummaryListener implements ProfileListener {

    /**
     * The window of tasks that are aggregated before the next text
     * output when none is provided
     */
    private static final int DEFAULT_WINDOW_SIZE = 5000;

    /**
     * The property for specifying the number of windows to combine for
     * printing averages.
     */
    private static final String AVERAGE_PROPERTY =
	"com.sun.sgs.impl.profile.listener.ProfileSummaryListener.average";

    /**
     * The default number of windows to combine for printing averages.
     */
    private static final int DEFAULT_AVERAGE = 5;

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private final int windowSize;

    /**
     * How many windows to combine for printing averages.
     */
    private final int average;

    // long wall-clock time
    private long lastWindowStart;

    // statistics updated for the aggregate window
    private long taskCount = 0;
    private long failedCount = 0;
    private long maxRunTime = 0;
    private long runTime = 0;
    private long lagTimeSum = 0;
    private long readyCountSum = 0;   

    // Moving averages
    private final MovingAverage maRunTime;
    private final MovingAverage maFailed;
    private final MovingAverage maReadyCount;
    private final MovingAverage maLagTime;
    private final MovingAverage maThroughput;
    private final MovingAverage maLatency;

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
                                  ComponentRegistry registry) 
    {

	lastWindowStart = System.currentTimeMillis();

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	windowSize = wrappedProps.getIntProperty(
	    ProfileListener.WINDOW_SIZE_PROPERTY, DEFAULT_WINDOW_SIZE);
	average = wrappedProps.getIntProperty(
	    AVERAGE_PROPERTY, DEFAULT_AVERAGE);

	maRunTime = new MovingAverage(average);
	maFailed = new MovingAverage(average);
	maReadyCount = new MovingAverage(average);
	maLagTime = new MovingAverage(average);
	maThroughput = new MovingAverage(average);
	maLatency = new MovingAverage(average);
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

	    if (r > maxRunTime) {
		maxRunTime = r;
            }

	    lagTimeSum += (profileReport.getActualStartTime() -
			   profileReport.getScheduledStartTime());
	} else {
	    failedCount++;
	}
	
	if (taskCount % windowSize == 0) {

	    long windowEndTime = System.currentTimeMillis();	    

	    double successful = taskCount - failedCount;

	    double meanRunTime = runTime / successful;
	    double meanFailed = (failedCount * 100) / (double) taskCount;
	    double meanReadyCount = readyCountSum / (double) taskCount;
	    double meanLagTime = lagTimeSum / successful;
	    double meanThroughput = (successful * 1000) / 
                                    (double) (windowEndTime - lastWindowStart);
	    double meanLatency = (runTime + lagTimeSum) / successful;

	    maRunTime.add(meanRunTime);
	    maFailed.add(meanFailed);
	    maReadyCount.add(meanReadyCount);
	    maLagTime.add(meanLagTime);
	    maThroughput.add(meanThroughput);
	    maLatency.add(meanLatency);

	    System.out.printf("past %d tasks:%n" +
			      "  mean runtime: %4.2fms," +
			      "  max: %6dms," +
			      "  failed: %d (%2.2f%%)" +
			      "%n  mean ready count: %.2f," +
			      "  mean lag time: %.2fms" +
			      "%n  mean tasks running concurrently: %.2f" +
			      "%n  mean throughput: %.2f txn/sec," +
			      "  mean latency: %.2f ms/txn%n",
			      taskCount, 
			      meanRunTime,
			      maxRunTime, 
			      failedCount, 
			      meanFailed,
			      meanReadyCount,
			      meanLagTime,
			      ((double) runTime / 
			       (double) (windowEndTime - lastWindowStart)),	
			      meanThroughput,
			      meanLatency
		);
	    System.out.printf("past %d tasks:%n" +
			      "  mean runtime: %4.2fms," +
			      "  failed: %2.2f%%" +
			      "%n  mean ready count: %.2f," +
			      "  mean lag time: %.2fms" +
			      "%n  mean throughput: %.2f txn/sec," +
			      "  mean latency: %.2f ms/txn%n",
			      maRunTime.count() * taskCount, 
			      maRunTime.average(),
			      maFailed.average(),
			      maReadyCount.average(),
			      maLagTime.average(),
			      maThroughput.average(),
			      maLatency.average()
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
    
    /** Tracks a moving average of a specified maximum number of values. */
    private static class MovingAverage {

	/** Holds the values to average. */
	private final double[] values;

	/** The offset in values to store the next value. */
	private int next = 0;

	/** The number of values stored. */
	private int count = 0;
	
	/**
	 * Creates an instance for averaging the specified maximum number of
	 * values.
	 */
	MovingAverage(int maxNumValues) {
	    values = new double[maxNumValues];
	}

	/** Returns the number of values added. */
	int count() {
	    return count;
	}

	/** Adds a value. */
	void add(double value) {
	    values[next++] = value;
	    if (next >= values.length) {
		next = 0;
	    }
	    if (count < values.length) {
		count++;
	    }
	}

	/** Returns the average. */
	double average() {
	    if (count == 0) {
		return 0;
	    }
	    double sum = 0;
	    int pos = next;
	    for (int i = 0; i < count; i++) {
		pos--;
		if (pos < 0) {
		    pos = count - 1;
		}
		sum += values[pos];
	    }
	    return sum / count;
	}
    }
}
