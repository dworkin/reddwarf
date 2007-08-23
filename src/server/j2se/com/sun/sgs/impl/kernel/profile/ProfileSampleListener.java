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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A text-output listener that displays the distribution for all
 * {@link ProfileSample}s updated during a fixed-size window of tasks
 * as well as the lifetime of the program.  This class uses a {@link
 * PowerOfTwoHistogram} to display the distribution for each sample
 *
 * Note that this class uses a fixed number of tasks between outputs,
 * rather than a period of time.  The number of tasks can be
 * comfigured by defining the {@code
 * com.sun.sgs.kernel.profile.listener.window.size} property in the
 * application properties file.  The default window size for this
 * class is {@code 5000}.
 *
 * @see ProfileProperties
 * @see ProfileSample
 */
public class ProfileSampleListener implements ProfileOperationListener {

    /**
     * The window of tasks that are aggregated before the next text
     * output when none is provided
     */
    private static final int DEFAULT_WINDOW_SIZE = 5000;

    /**
     * How many tasks are aggregated between status updates.  This is
     * set either by property or the default.
     */
    private final int windowSize;

    // statistics updated for the aggregate window
    private int taskCount;

    private final Map<String,Histogram> profileSamples;

    /**
     * Creates an instance of {@code ProfileSampleListener}.
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
    public ProfileSampleListener(Properties properties, TaskOwner owner,
				 TaskScheduler taskScheduler,
				 ResourceCoordinator resourceCoord) {

	taskCount = 0;
	profileSamples = new HashMap<String,Histogram>();

	windowSize = new PropertiesWrapper(properties).
	    getIntProperty(ProfileProperties.WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadCount(int schedulerThreadCount) {
	// unused
    }

    /**
     * Collects the samples that are updated during this task-window
     * and when the number of tasks reaches the windowed size, outputs
     * a histogram for each sample that has been updated during the
     * window and also a histogram for each sample updated during the
     * lifetime of the application.
     *
     * @param profileReport the summary for the finished {@code Task}
     */
    public void report(ProfileReport profileReport) {
	
	taskCount++;
	
	Map<String,List<Long>> m = profileReport.getUpdatedTaskSamples();

	if (m == null)
	    return;

	for (String name : m.keySet()) {

	    Histogram hist = profileSamples.get(name);
	    if (hist == null) {
		hist = new PowerOfTwoHistogram();
		profileSamples.put(name, hist);
	    }

	    List<Long> samples =  m.get(name);
	    for (Long l : samples) 
		hist.bin(l.longValue());	   
	}
	
	if (taskCount % windowSize == 0) {

	    if (profileSamples.size() > 0) {
		System.out.printf("Profile samples for the past %d tasks:\n", 
				  taskCount);
		
		for (Map.Entry<String,Histogram> e : profileSamples.entrySet()) 
		    System.out.printf("%s: (%d samples)\n%s\n", e.getKey(), 
				      e.getValue().size(), e.getValue());
		

		// reset the samples for the next window
		profileSamples.clear();
	    }
	}	
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }
    
    

}