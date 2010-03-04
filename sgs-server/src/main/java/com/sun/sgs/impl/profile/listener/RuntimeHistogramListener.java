/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.profile.util.Histogram;
import com.sun.sgs.impl.profile.util.PowerOfTwoHistogram;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 * A text-output listener that displays the distribution of successful
 * task execution times, for both a recent fixed-size window of tasks
 * and the lifetime of the application.  This class uses a {@link
 * PowerOfTwoHistogram} to display the distribution of execution
 * times.
 *
 * <p>
 *
 * Note that this class uses a fixed number of tasks between outputs,
 * rather than a period of time.  The number of tasks can be
 * configured by defining the {@code
 * com.sun.sgs.profile.listener.window.size} property in the
 * application properties file.  The default window size for this
 * class is {@code 5000}.
 *
 */
public class RuntimeHistogramListener implements ProfileListener {

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

    /**
     * Creates an instance of {@code RuntimeHistogramListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     */
    public RuntimeHistogramListener(Properties properties, Identity owner,
                                    ComponentRegistry registry) 
    {
  	taskCount = 0;
	lifetimeHistogram = new PowerOfTwoHistogram();
	windowHistogram = new PowerOfTwoHistogram();

	windowSize = new PropertiesWrapper(properties).
	    getIntProperty(ProfileListener.WINDOW_SIZE_PROPERTY, 
                           DEFAULT_WINDOW_SIZE);
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /**
     * Aggregates the task execution times for sucessful tasks and
     * when the number of tasks reaches the windowed limit, outputs a
     * histogram for the execution times for the window and also the
     * lifetime of the application.
     *
     * @param profileReport the summary for the finished {@code Task}
     */
    public void report(ProfileReport profileReport) {

	if (!profileReport.wasTaskSuccessful()) {
	    return;
        }

	long count = ++taskCount;

	long runTime = profileReport.getRunningTime();

	windowHistogram.bin(runTime);
	lifetimeHistogram.bin(runTime);

	if (count % windowSize == 0) {
	    
	    // print out the results
	    System.out.printf("past %d tasks:%n%s", windowSize,
			      windowHistogram.toString("ms"));
	    System.out.printf("lifetime of %d tasks:%n%s", count,
			      lifetimeHistogram.toString("ms"));

	    windowHistogram.clear();
	}	
    }


    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }

}
