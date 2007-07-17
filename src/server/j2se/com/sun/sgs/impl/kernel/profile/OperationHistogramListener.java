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

public class OperationHistogramListener implements ProfileOperationListener {

    private static final int WINDOW_SIZE = 100;

    private static final int NUM_BUCKETS = 15;
    
    private final  Map<String, AtomicLong[]> operationHistograms;
    private final Map<String, AtomicLong> operationCounts;
    
    private final Map<String, AtomicLong[]> lifetimeOperationHistograms;
    private final Map<String, AtomicLong> lifetimeOperationCounts;

    public OperationHistogramListener(Properties properties, TaskOwner owner,
				      TaskScheduler taskScheduler,
				      ResourceCoordinator resourceCoord) {
	operationHistograms = new HashMap<String, AtomicLong[]>();
	operationCounts = new HashMap<String, AtomicLong>();
	
	lifetimeOperationHistograms = new HashMap<String, AtomicLong[]>();
	lifetimeOperationCounts = new HashMap<String, AtomicLong>();
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

	//	for (ProfileOperation op : profileReport.getReportedOperations()) {
	//	    String operation = op.getOperationName();
	String operation = profileReport.getReportedOperations().toString();
	    
	    AtomicLong opCount = null;
	    AtomicLong[] hist = null;

	    if (!operationHistograms.containsKey(operation)) {
		synchronized(operationHistograms) {
		    // check that another thread hasn't already
		    // updated the map
		    if (operationHistograms.containsKey(operation)) {
			opCount = operationCounts.get(operation);
			hist = operationHistograms.get(operation);
		    }
		    // no other thread has put in the variables and
		    // this thread has the lock, so update
		    else {
			opCount = new AtomicLong(0);
			operationCounts.put(operation, opCount);
			hist = new AtomicLong[NUM_BUCKETS];
			for (int i = 0; i < NUM_BUCKETS; ++i) {
			    hist[i] = new AtomicLong(0);
			}
			operationHistograms.put(operation, hist);
		    }
		}
	    }
	    else {
		opCount = operationCounts.get(operation);
		hist = operationHistograms.get(operation);
	    }
	    
	    long count = opCount.incrementAndGet();
	    long runTime = profileReport.getRunningTime();

	    // find the appropriate bucket;
	    for (int i = 0; i < NUM_BUCKETS; i++) {
		if (runTime < 1 << (i+1)) {
		    hist[i].incrementAndGet();
		    break;
		}
	    }

	    if (count % WINDOW_SIZE == 0) {

		AtomicLong[] lifetimeHist = null;
		AtomicLong lifetimeCount = null;

		if (lifetimeOperationCounts.containsKey(operation)) {
		    lifetimeHist = lifetimeOperationHistograms.get(operation);
		    lifetimeCount = lifetimeOperationCounts.get(operation);
		}
		else {
		    // NOTE: not thread safe, since this race case is
		    // very rare (due to the window size) and this
		    // updating case is expensive enough as is
		    lifetimeCount = new AtomicLong(0);
		    lifetimeOperationCounts.put(operation, lifetimeCount);
		    lifetimeHist = new AtomicLong[NUM_BUCKETS];
		    for (int i = 0; i < NUM_BUCKETS; ++i) {
			lifetimeHist[i] = new AtomicLong(0);
		    }
		    lifetimeOperationHistograms.put(operation, lifetimeHist);
		}

		// increment the lifetime buckets
		for (int i = 0; i < NUM_BUCKETS; ++i) {
		    lifetimeHist[i].addAndGet(hist[i].get());
		}
		
		long total = lifetimeCount.addAndGet(count);
		
		// print out the results
		System.out.printf("For operation %s, past %d tasks:\n%s", 
				  operation, count,
				  HistogramListener.getStringHist(hist));
		System.out.printf("For operation %s, lifetime of %d tasks:\n%s", 
				  operation, total,
				  HistogramListener.getStringHist(lifetimeHist));
		
		
		// NOTE: we may lose a few entries by not locking, but in
		//       the interest of minimal performance impact, we
		//       don't care.
		for (AtomicLong l : hist)
		    l.set(0); // reset the window values.
		opCount.set(0);
	    }	
	    //	} // end for(operation : getOperations)
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	// don't care
    }


}
