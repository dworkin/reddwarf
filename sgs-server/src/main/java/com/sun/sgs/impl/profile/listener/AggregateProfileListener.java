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

import com.sun.sgs.impl.profile.util.NetworkReporter;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;


/**
 * This implementation of <code>ProfileListener</code> aggregates
 * profiling data over the lifetime of the system, and reports at fixed
 * intervals. By default the time interval is 5 seconds.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43005.
 * <p>
 * The <code>com.sun.sgs.impl.profile.listener.AggregateProfileListener.</code>
 * root is used for all properties in this class. The <code>report.port</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>report.period</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 */
public class AggregateProfileListener implements ProfileListener {

    private Map<String, Long> sOpCounts =
            new HashMap<String, Long>();
    private Map<String, Long> fOpCounts =
            new HashMap<String, Long>();
    
    // the task and time counts for successful and failed tasks
    private volatile long sTaskCount = 0;
    private volatile long sRunTime = 0;
    private volatile long sTaskOpCount = 0;
    private volatile long fTaskCount = 0;
    private volatile long fRunTime = 0;
    private volatile long fTaskOpCount = 0;

    // the delay and re-try counts for successful tasks only
    private volatile long delayTime = 0;
    private volatile long tryCount = 0;

    // the count and running time for transactional tasks only
    private volatile long tTaskCount = 0;
    private volatile long tRunTime = 0;

    // the aggregate counters for task-local counters
    private Map<String, Long> localCounters;

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        AggregateProfileListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".report.port";
    private static final int DEFAULT_PORT = 43005;
    private static final String PERIOD_PROPERTY = PROP_BASE + ".report.period";
    private static final long DEFAULT_PERIOD = 5000;

    /**
     * Creates an instance of <code>AggregateProfileListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>Identity</code> to use for all tasks run by
     *              this listener
     * @param registry the {@code ComponentRegistry} containing the
     *                 available system components
     *
     * @throws IOException if the server socket cannot be created
     */
    public AggregateProfileListener(Properties properties, Identity owner,
                                    ComponentRegistry registry)
        throws IOException
    {
	localCounters = new ConcurrentHashMap<String, Long>();

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = registry.getComponent(TaskScheduler.class).
            scheduleRecurringTask(new AggregatingRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
	if (event.getPropertyName().
                equals("com.sun.sgs.profile.newop")) 
        {          
	    ProfileOperation op = (ProfileOperation) (event.getNewValue());
	    sOpCounts.put(op.getName(), 0L);
	    fOpCounts.put(op.getName(), 0L);
	}
    }

    /** {@inheritDoc}*/
    public void report(ProfileReport profileReport) {
        List<String> ops = profileReport.getReportedOperations();
        if (profileReport.wasTaskSuccessful()) {
            sTaskCount++;
            sTaskOpCount += ops.size();
            sRunTime += profileReport.getRunningTime();
            delayTime += (profileReport.getActualStartTime() -
                          profileReport.getScheduledStartTime());
            tryCount += profileReport.getRetryCount();
            for (String op : ops) {
                Long i = sOpCounts.get(op);
		sOpCounts.put(op, Long.valueOf(i == null ? 1 : i + 1));
	    }
        } else {
            fTaskCount++;
            fTaskOpCount += ops.size();
            fRunTime += profileReport.getRunningTime();
            for (String op : ops) {
                Long i = fOpCounts.get(op);
		fOpCounts.put(op, Long.valueOf(i == null ? 1 : i + 1));
	    }
        }

        if (profileReport.wasTaskTransactional()) {
            tTaskCount++;
            tRunTime += profileReport.getRunningTime();
        }

	Map<String, Long> map = profileReport.getUpdatedTaskCounters();
	if (map != null) {
	    for (Entry<String, Long> entry : map.entrySet()) {
		String key = entry.getKey();
		long value = 0;
		if (localCounters.containsKey(key)) {
		    value = localCounters.get(key);
                }
		localCounters.put(key, entry.getValue() + value);
	    }
	}
    }

    /** {@inheritDoc} */
    public void shutdown() {
        handle.cancel();
        networkReporter.shutdown();
    }

    /**
     * Private internal class that is used to run a recurring task that
     * reports on the collected data.
     */
    private class AggregatingRunnable implements KernelRunnable {
        public String getBaseTaskType() {
            return AggregatingRunnable.class.getName();
        }
        public void run() throws Exception {
            // calculate totals across categories
            long totalTasks = sTaskCount + fTaskCount;
            double totalTime = sRunTime + fRunTime;
            double totalAvgLength = totalTime / (double) totalTasks;

            // average just for transactions
            double transactionalAvgLength =
                (double) tRunTime / (double) tTaskCount;

            // averages just for successes
            double avgSuccessfulLength = totalTime / (double) sTaskCount;
            double avgSuccessfulDelay = 
                (double) delayTime / (double) sTaskCount;
            double avgSuccessfulOps =
                (double) sTaskOpCount / (double) sTaskCount;

            // average for failures
            double avgRetries = (double) tryCount / (double) sTaskCount;
            double avgFailedOps = (fTaskCount == 0) ? 0 :
                (double) fTaskOpCount / (double) fTaskCount;

	    Formatter reportStr = new Formatter();
	    reportStr.format("TaskCounts:%n");
            reportStr.format("  TotalTasks=%d", totalTasks);
	    reportStr.format("  AvgLength=%2.2fms%n", totalAvgLength);
            reportStr.format("  Transactional=%d", tTaskCount);
	    reportStr.format("  AvgLength=%2.2fms%n", transactionalAvgLength);
            reportStr.format("  Successful=%d", sTaskCount);
	    reportStr.format("  AvgLength=%2.2fms", avgSuccessfulLength);
	    reportStr.format("  AvgStartDelay=%2.2fms", avgSuccessfulDelay);
	    reportStr.format("  AvgRetries=%2.2f%n", avgRetries);
            reportStr.format("  AvgOpCountOnSuccess=%2.2f", avgSuccessfulOps);
	    reportStr.format("  AvgOpCountOnFailure=%2.2f%n", avgFailedOps);

            reportStr.format("OpCounts:%n");
	    int j, k = 0;
	    for (String op : sOpCounts.keySet()) {
		j = sOpCounts.get(op).intValue();
                reportStr.format("   %s=%d/%d", op, j, j + fOpCounts.get(op));
		if (++k % 3 == 0) {
                    reportStr.format("%n");
                }
            }
	    reportStr.format("%n");

	    if (!localCounters.isEmpty()) {
		reportStr.format("LocalCounters (avg per task):%n");
		for (Entry<String, Long> entry : localCounters.entrySet()) {
		    reportStr.format(
			"  %s=%2.2f%n", entry.getKey(),
			entry.getValue() / (double) totalTasks);
                }
	    }

            reportStr.format("%n");

            networkReporter.report(reportStr.toString());
        }
    }

}
