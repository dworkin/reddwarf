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

import com.sun.sgs.impl.profile.util.TransactionId;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import com.sun.sgs.impl.util.BoundedLinkedHashMap;

import java.beans.PropertyChangeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An implementation of {@code ProfileListener} that periodically prints a
 * histogram showing the distribution of task types that were aborted due to
 * contention.
 */
public class ContendingTaskTypeListener implements ProfileListener {

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

    /**
     * The length of {@code *} characters for the longest histogram bar
     */
    private static final int MAX_HIST_LENGTH = 40;

    /**
     * The number of tasks seen by this listener
     */
    private long taskCount;

    /**
     * A mapping from the fully qualified name of a {@code Task} class to the
     * number of times that a task of that class has been aborted due to
     * contention.
     */
    private Map<String,Integer> conflictingTaskCounts;

    /**
     * Creates an instance of {@code ContendingTaskTypeListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     *
     * @throws IllegalArgumentException if either of the backlog or count
     *                                  properties is provided but invalid
     */
    public ContendingTaskTypeListener(Properties properties, Identity owner,
				    ComponentRegistry registry) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	windowSize = wrappedProps.getIntProperty(
	    ProfileListener.WINDOW_SIZE_PROPERTY, DEFAULT_WINDOW_SIZE);

	conflictingTaskCounts = new TreeMap<String,Integer>();
	taskCount = 0;
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
	
        // get the access detail, or return if there is none available
	AccessedObjectsDetail detail = profileReport.getAccessedObjectsDetail();
	if (detail == null)
            return;
        
        // if there was conflict, then figure out what to display
        if (detail.getConflictType() != ConflictType.NONE) {
	    String taskType = profileReport.getTask().getBaseTaskType();
	    Integer count = conflictingTaskCounts.get(taskType);
	    conflictingTaskCounts.put(taskType, (count == null) 
				      ? Integer.valueOf(1) 
				      : Integer.valueOf(count.intValue() + 1));
	}

	// base on the window size, print out the histogram of the conflicted
	if (taskCount % windowSize == 0) {
	    int longestNameLength = 0;
	    double largestValue = 0;
	    int sumFailed = 0;

	    // find the longest name and largest value for formatting the
	    // histogram
	    for (Map.Entry<String,Integer> e : 
		     conflictingTaskCounts.entrySet()) {
		int nameLength = e.getKey().length();
		if (nameLength > longestNameLength)
		    longestNameLength = nameLength;
		int value = e.getValue().intValue();
		sumFailed += value;
		if (value > largestValue)
		    largestValue = value;
	    }
	    
	    StringBuilder b = new StringBuilder(1024);
	    for (Map.Entry<String,Integer> e : 
		     conflictingTaskCounts.entrySet()) {
		
		String name = e.getKey();
		for (int i = 0; i < longestNameLength - name.length(); ++i)
		    b.append(" ");
		b.append(name);
		b.append(" |");
		
		int value = e.getValue().intValue();
		int bars = (largestValue == 0) 
		    ? 0 : (int)((value / largestValue) * MAX_HIST_LENGTH) + 1;
		for (int j = 0; j < bars; ++j)
		    b.append("*");
		b.append("\n");
	    }	   
	    System.out.printf("Task types for %d/%d conflicted tasks:%n%s%n", 
			      sumFailed, windowSize, b);
	    conflictingTaskCounts.clear();
	}
    }

    /**
     * Returns {@true} if these accesses for these two objects would conflict.
     *
     * @param o1 the first access
     * @param o2 the second access
     *
     * @return {@true} if these accesses for these two objects would conflict.
     */
    private static boolean conflicts(AccessedObject o1, AccessedObject o2) {
	if (o1.getSource().equals(o2.getSource())) {
	    AccessType a1 = o1.getAccessType();
	    AccessType a2 = o2.getAccessType();
	    return !(a1.equals(AccessType.READ) && a2.equals(AccessType.READ));
	}
	return false;
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// unused
    }

}
