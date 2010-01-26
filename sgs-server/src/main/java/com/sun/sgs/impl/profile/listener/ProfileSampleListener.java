/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * A text-output listener that displays the distribution for all
 * {@link com.sun.sgs.profile.ProfileSample}s updated during a
 * fixed-size window of tasks as well as the lifetime of the program.
 * This class uses a {@link PowerOfTwoHistogram} to display the
 * distribution for each sample
 *
 * Note that this class uses a fixed number of tasks between outputs,
 * rather than a period of time.  The number of tasks can be
 * configured by defining the {@code
 * com.sun.sgs.profile.listener.window.size} property in the
 * application properties file.  The default window size for this
 * class is {@code 5000}.
 *
 * @see com.sun.sgs.profile.ProfileSample
 */
public class ProfileSampleListener implements ProfileListener {

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

    private final Map<String, Histogram> profileSamples;

    /**
     * Creates an instance of {@code ProfileSampleListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     */
    public ProfileSampleListener(Properties properties, Identity owner,
                                 ComponentRegistry registry) 
    {

	taskCount = 0;
	profileSamples = new HashMap<String, Histogram>();

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
	
	Map<String, List<Long>> m = profileReport.getUpdatedTaskSamples();

	if (m == null) {
	    return;
        }

	for (Entry<String, List<Long>> entry : m.entrySet()) {
	    String name = entry.getKey();

	    Histogram hist = profileSamples.get(name);
	    if (hist == null) {
		hist = new PowerOfTwoHistogram();
		profileSamples.put(name, hist);
	    }

	    List<Long> samples = entry.getValue();
	    for (Long l : samples) {
		hist.bin(l.longValue());
            }
	}
	
	if (taskCount % windowSize == 0) {

	    if (profileSamples.size() > 0) {
		System.out.printf("Profile samples for the past %d tasks:%n", 
				  taskCount);
		
		for (Map.Entry<String, Histogram> e : 
                         profileSamples.entrySet()) 
                {
		    System.out.printf("%s: (%d samples)%n%s%n", e.getKey(), 
				      e.getValue().size(), e.getValue());
                }
		

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
