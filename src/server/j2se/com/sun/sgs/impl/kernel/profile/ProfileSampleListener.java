
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

public class ProfileSampleListener implements ProfileOperationListener {


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

    // statistics updated for the aggregate window
    private int taskCount;

    private final Map<String,Histogram> profileSamples;


    public ProfileSampleListener(Properties properties, TaskOwner owner,
				 TaskScheduler taskScheduler,
				 ResourceCoordinator resourceCoord) {

	taskCount = 0;
	profileSamples = new HashMap<String,Histogram>();

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
	
	Map<String,List<Long>> m = profileReport.getUpdatedTaskSamples();

	if (m == null)
	    return;

	for (String name : m.keySet()) {

	    Histogram hist = profileSamples.get(name);
	    if (hist == null) {
		// REMINDER: this should configurable
		hist = new PowerOfTwoHistogram();
		profileSamples.put(name, hist);
	    }

	    List<Long> samples =  m.get(name);
	    for (Long l : samples) 
		hist.bin(l.longValue());	   
	}
	
	if (taskCount % windowSize == 0) {
	    // reset the counter for the next entry

	    if (profileSamples.size() > 0) {
		System.out.printf("Profile samples for the past %d tasks:\n", 
				  taskCount);
		
		for (String name : profileSamples.keySet()) {
		    
		    Histogram hist = profileSamples.get(name);
		    System.out.printf("%s: \n%s\n", name, hist.toString());
		}

		profileSamples.clear();
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