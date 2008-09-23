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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * An implementation of {@code ProfileListener} that periodically prints a
 * histogram showing the distribution of object types on which tasks contend.
 */
public class ContendedObjectsListener implements ProfileListener {

    /**
     * The window of tasks that are aggregated before the next text
     * output when none is provided
     */
    private static final int DEFAULT_WINDOW_SIZE = 5000;

    /**
     * A local backlog of the past access detail
     */
    private final BoundedLinkedHashMap<TransactionId,ProfileReport>
        backlogMap;

    /**
     * The number of transactions to keep as a backlog if the backlog size of
     * the {@code AccessCoordinator} has not been specified.
     */
    private static final int TXN_BACKLOG_SIZE = 1000;

    /**
     * The length of {@code *} characters for the longest histogram bar
     */
    private static final int MAX_HIST_LENGTH = 40;

    /**
     * How many tasks are aggregated between status updates.  Note
     * that the update might not occur exactly on window crossing due
     * to concurrent updates.
     */
    private final int windowSize;

    /**
     * The number of tasks seen by this listener
     */
    private long taskCount;

    /**
     * A mapping from the fully qualified name of a class to the number of times
     * two tasks have contended over an object of that type
     */
    private Map<String,Integer> conflictingClassCounts;

    /**
     * Creates an instance of {@code ContendedObjectsListener}.
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
    public ContendedObjectsListener(Properties properties, Identity owner,
				    ComponentRegistry registry) {
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        String backlogProp =
            properties.getProperty("com.sun.sgs.impl.kernel." +
                                   "AccessCoordinatorImpl.queue.size");
        if (backlogProp != null) {
            try {
                backlogMap = new BoundedLinkedHashMap
                    <TransactionId,ProfileReport>(Integer.
                                                       parseInt(backlogProp));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Backlog size must be a " +
                                                   "number: " + backlogProp);
            }
        } else {
	    // if the transaction coordinator isn't keeping a backlog, then we
	    // will keep one as a part of this listener
	    backlogMap = new BoundedLinkedHashMap
		<TransactionId,ProfileReport>(TXN_BACKLOG_SIZE);
	}

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	windowSize = wrappedProps.getIntProperty(
	    ProfileListener.WINDOW_SIZE_PROPERTY, DEFAULT_WINDOW_SIZE);

	conflictingClassCounts = new TreeMap<String,Integer>();
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

        TransactionId txnId =         
            txnId = new TransactionId(profileReport.getTransactionId());
	backlogMap.put(txnId, profileReport);
        
        // if there was conflict, then figure out what to display
        if (detail.getConflictType() != ConflictType.NONE) {
            if (txnId == null)
                txnId = new TransactionId(profileReport.getTransactionId());

            byte [] conflictingBytes = detail.getConflictingId();

	    // if the detail didn't come with the id of what might have caused
	    // the conflict, walk the backlog and see if this listener can tell
	    // what might detail have conflcited
            if (conflictingBytes == null) {	       

		for (ProfileReport pastProfileReport : backlogMap.values()) {

		    long pastEndTime = pastProfileReport.getActualStartTime() +
			pastProfileReport.getRunningTime();

		    // ensure that the lifetime of the two reports overlapped
		    if (profileReport.getActualStartTime() > pastEndTime) {
			continue;
		    }

		    // first check that the two profile reports in question
		    // overlapped in duration.  If we didn't check for this,
		    // then we might show false contention.		    
		    AccessedObjectsDetail aod = 
			pastProfileReport.getAccessedObjectsDetail();

		    // if the two details conflicted then we can stop looking
		    // through the backlog
		    if (getDetailConflict(detail, aod))
			break;
		}
	    }
	    else {
		TransactionId conflictingId = 
		    new TransactionId(conflictingBytes);
		
		ProfileReport pastProfileReport = backlogMap.get(conflictingId);
		
		// if we found the conflicting detail, use it to determine the
		// classes that causes the conflict
		if (pastProfileReport != null) {

		    long pastEndTime = pastProfileReport.getActualStartTime() +
			pastProfileReport.getRunningTime();

		    // ensure that the lifetime of the two reports overlapped
		    if (profileReport.getActualStartTime() <= pastEndTime) {
			// if we're keeping a backlog, look through it to see if
			// we have the detail on the conflicting id
			AccessedObjectsDetail conflictingDetail = 
			    pastProfileReport.getAccessedObjectsDetail();
			getDetailConflict(detail, conflictingDetail);
		    }
		}	
	    }
	}
	// base on the window size, print out the histogram of the conflicted
	if (taskCount % windowSize == 0) {
	    int longestNameLength = 0;
	    double largestValue = 0;
	    int sumConflicted = 0;

	    // find the longest name and largest value for formatting the
	    // histogram
	    for (Map.Entry<String,Integer> e : 
		     conflictingClassCounts.entrySet()) {
		int nameLength = e.getKey().length();
		if (nameLength > longestNameLength)
		    longestNameLength = nameLength;
		int value = e.getValue().intValue();
		sumConflicted += value;
		if (value > largestValue)
		    largestValue = value;
	    }
	    
	    StringBuilder b = new StringBuilder(1024);
	    for (Map.Entry<String,Integer> e : 
		     conflictingClassCounts.entrySet()) {
		
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
	    System.out.printf("Object types for %d contended objects in the " +
			      "past %d tasks%n%s%n", sumConflicted, windowSize,
			      b);
	    conflictingClassCounts.clear();
	}
    }

    /**
     * Checks whether the two details conflicted and then updates the {@code
     * conflictingClassCounts} map based on the sources of confict. Returns
     * {@code true} if the two details conflicts and the object class counts
     * were updated
     *
     * @param d1 the first detail
     * @param d2 the second detail
     * 
     * @return {@code true} if the two details conflicts and the object class
     *         counts were updated
     */
    private boolean getDetailConflict(AccessedObjectsDetail d1, 
				      AccessedObjectsDetail d2) {
	boolean conflicts = false;
	// determine which object conflicted
	loop:
	for (AccessedObject o1 : d1.getAccessedObjects()) {
	    for (AccessedObject o2 : 
		     d2.getAccessedObjects()) {
		if (conflicts(o1, o2)) {

		    // NOTE: this relies on the fact that DataServiceImpl
		    // currently reports the actual object as the description.
		    Object obj = o1.getDescription();
		    if (obj != null) {
			Class conflicted = obj.getClass();
			String name;
			/*
			 * NOTE: the following section is commented out until
			 * future changes can expose PendingTask or some
			 * equivalent detail to show the names of Serializable
			 * tasks
			 *
			// NOTE: PendingTask instances wrap Serializable Tasks.
			// To avoid reporting the PendingTask if these tasks
			// contend, we report the base task type
			if (obj instanceof PendingTask) {
			    name = ((PendingTask)(obj)).getBaseTaskType();
			}
			else {
			*/
			name = conflicted.getName();
			/*
			}
			*/
			Integer count = 
			    conflictingClassCounts.get(name);
			conflictingClassCounts.put(name,
			    (count == null) ? Integer.valueOf(1) :
			    Integer.valueOf(count.intValue() + 1)); 
			conflicts = true;
		    }
		}
	    }
	    if (conflicts)
		break loop;
	}
	return conflicts;
    }

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
