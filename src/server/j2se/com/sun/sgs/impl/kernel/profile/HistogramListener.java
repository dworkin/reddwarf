
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

public class HistogramListener implements ProfileOperationListener {

    private static final int WINDOW_SIZE = 5000;

    private static final int NUM_BUCKETS = 15;

    private final AtomicLong[] buckets;

    private final AtomicLong[] lifetimeBuckets;

    private final AtomicLong taskCount;
    private final AtomicLong lifetimeCount;

    public HistogramListener(Properties properties, TaskOwner owner,
			     TaskScheduler taskScheduler,
			     ResourceCoordinator resourceCoord) {

	buckets = new AtomicLong[NUM_BUCKETS];
	lifetimeBuckets = new AtomicLong[NUM_BUCKETS];
	for (int i = 0; i < NUM_BUCKETS; ++i) {
	    buckets[i] = new AtomicLong(0);
	    lifetimeBuckets[i] = new AtomicLong(0);
	}
	taskCount = new AtomicLong(0);
	lifetimeCount = new AtomicLong(0);
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

	long count = taskCount.incrementAndGet();

	long runTime = profileReport.getRunningTime();

	// find the appropriate bucket;
	for (int i = 0; i < buckets.length; i++) {
	    if (runTime < 1 << (i+1)) {
		buckets[i].incrementAndGet();
		break;
	    }
	}

	if (count % WINDOW_SIZE == 0) {
	    // increment the lifetime buckets
	    for (int i = 0; i < buckets.length; ++i) {
		lifetimeBuckets[i].addAndGet(buckets[i].get());
	    }

	    long total = lifetimeCount.addAndGet(count);
	    
	    // print out the results
	    System.out.printf("past %d tasks:\n%s", count,
			      getStringHist(buckets));
	    System.out.printf("lifetime of %d tasks:\n%s", total,
			      getStringHist(lifetimeBuckets));
	    

	    // NOTE: we may lose a few entries by not locking, but in
	    //       the interest of minimal performance impact, we
	    //       don't care.
	    for (AtomicLong l : buckets)
		l.set(0); // reset the window values.
	    taskCount.set(0); // reset the counter
	}	
    }

    static String getStringHist(AtomicLong[] arr) {
	// the longest bar in the histogram
	int MAX_HIST_LENGTH = 40;	

	// make a quick copy of the array
	long[] a = new long[arr.length];

	double max = 0;
	int first = arr.length, last = 0;
	for (int i = 0; i < arr.length; i++) {
	    a[i] = arr[i].get();
	    // find the max, to scale the rest
	    if (a[i] > max)
		max = a[i];
	    // find the first and last occurance of non-zero values
	    if (a[i] != 0) {
		if (i < first)
		    first = i;
		if (i > last)
		    last = i;
	    }
	}
	
	// get the length of the longest string version of the integer
	// to make the histogram line up correctly
	String longest = Integer.toString(1 << last);
	
	StringBuilder b = new StringBuilder(128);
	for (int i = first; i <= last; ++i) {
	    String n = Integer.toString(1 << i);
	    // make the bars all line up evenly by padding with spaces
	    for (int j = n.length(); j < longest.length(); ++j)
		b.append(" ");
	    b.append(n).append("ms |");
	    // scale the bar length relative to the max
	    int bars = (int)((a[i] / max) * MAX_HIST_LENGTH);
	    // bump all non-empty buckets by one, so we can tell the
	    // difference
	    if (a[i] > 0) bars++;
	    for (int j = 0; j < bars; ++j)
		b.append("*");
	    b.append("\n");
	}
	
	return b.toString();
    }


    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// don't care
    }

}