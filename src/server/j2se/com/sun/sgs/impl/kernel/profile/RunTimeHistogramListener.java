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
 * A text-output listener that displays the histogram for the task
 * execution times, for both a recent window of tasks and the lifetime
 * of the application.
 */
public class RunTimeHistogramListener implements ProfileOperationListener {

    static final String WINDOW_SIZE_PROPERTY =
	"com.sun.sgs.impl.kernel.Kernel.profile.listener.window.size";

    /**
     * The window of tasks that are aggregated before the next text
     * output when none is provided
     */
    private static final int DEFAULT_WINDOW_SIZE = 5000;
    
    /**
     * The current number of tasks seen
     */
    private long taskCount;

    /**
     * The number of tasks seen at the most recent text-output
     */
    private long lastCount;

    /**
     * The number of tasks aggregated between text outputs
     */
    private final int windowSize;
    
    /**
     * The histogram for the tasks aggregated during the window
     */
    private final Histogram windowHistogram;

    /**
     * The histogram for task runtime during the entire application's
     * lifetime.
     */
    private final Histogram lifetimeHistogram;

    public RunTimeHistogramListener(Properties properties, TaskOwner owner,
			     TaskScheduler taskScheduler,
			     ResourceCoordinator resourceCoord) {
  	taskCount = 0;
	lastCount = 0;
	lifetimeHistogram = new PowerOfTwoHistogram();
	windowHistogram = new PowerOfTwoHistogram();

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
     * 
     */
    public void report(ProfileReport profileReport) {

	if (!profileReport.wasTaskSuccessful())
	    return;

	long count = ++taskCount;

	long runTime = profileReport.getRunningTime();

	windowHistogram.bin(runTime);
	lifetimeHistogram.bin(runTime);

	if (count % windowSize == 0) {
	    
	    // print out the results
	    System.out.printf("past %d tasks:\n%s", count - lastCount,
			      windowHistogram.toString("ms"));
	    System.out.printf("lifetime of %d tasks:\n%s", count,
			      lifetimeHistogram.toString("ms"));

	    lastCount = count;
	    windowHistogram.clear();
	}	
    }


    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// don't care
    }

}