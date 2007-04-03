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

import java.util.List;
import java.util.Properties;


/**
 * This implementation of <code>ProfileOperationListener</code> aggregates
 * profiling data over the lifetime of the system, and reports at fixed
 * intervals. By default the time interval is 5 seconds.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43005.
 * <p>
 * The <code>com.sun.sgs.impl.kernel.profile.AggregateProfileOpListener.</code>
 * root is used for all properties in this class. The <code>reportPort</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>reportPeriod</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 */
public class AggregateProfileOpListener implements ProfileOperationListener {

    // NOTE: this only supports MAX_OPS operations, which is fine as long
    // as the collector will never allow more than this number to be
    // registered, but when that changes, so too should this code
    private int maxOp = 0;
    private ProfileOperation [] registeredOps =
        new ProfileOperation[ProfileCollector.MAX_OPS];
    private long [] sOpCounts = new long[ProfileCollector.MAX_OPS];
    private long [] fOpCounts = new long[ProfileCollector.MAX_OPS];
    
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

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        AggregateProfileOpListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".reportPort";
    private static final int DEFAULT_PORT = 43005;
    private static final String PERIOD_PROPERTY = PROP_BASE + "reportPeriod.";
    private static final long DEFAULT_PERIOD = 5000;

    /**
     * Creates an instance of <code>SnapshotProfileOpListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>TaskOwner</code> to use for all tasks run by
     *              this listener
     * @param taskScheduler the <code>TaskScheduler</code> to use for
     *                      running short-lived or recurring tasks
     * @param resourceCoord the <code>ResourceCoordinator</code> used to
     *                      run any long-lived tasks
     *
     * @throws IOException if the server socket cannot be created
     */
    public AggregateProfileOpListener(Properties properties, TaskOwner owner,
                                      TaskScheduler taskScheduler,
                                      ResourceCoordinator resourceCoord)
        throws IOException
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = taskScheduler.
            scheduleRecurringTask(new AggregatingRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void notifyNewOp(ProfileOperation op) {
        int id = op.getId();
        if (id > maxOp)
            maxOp = id;
        registeredOps[id] = op;
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadCount(int schedulerThreadCount) {
        // for now, this is ignored
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        List<ProfileOperation> ops = profileReport.getReportedOperations();
        if (profileReport.wasTaskSuccessful()) {
            sTaskCount++;
            sTaskOpCount += ops.size();
            sRunTime += profileReport.getRunningTime();
            delayTime += (profileReport.getActualStartTime() -
                          profileReport.getScheduledStartTime());
            tryCount += profileReport.getRetryCount();
            for (ProfileOperation op : ops)
                sOpCounts[op.getId()]++;
        } else {
            fTaskCount++;
            fTaskOpCount += ops.size();
            fRunTime += profileReport.getRunningTime();
            for (ProfileOperation op : ops)
                fOpCounts[op.getId()]++;
        }

        if (profileReport.wasTaskTransactional()) {
            tTaskCount++;
            tRunTime += profileReport.getRunningTime();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        handle.cancel();
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
            double totalAvgLength = totalTime / (double)totalTasks;

            // average just for transactions
            double transactionalAvgLength =
                (double)tRunTime / (double)tTaskCount;

            // averages just for successes
            double avgSuccessfulLength = totalTime / (double)sTaskCount;
            double avgSuccessfulDelay = (double)delayTime / (double)sTaskCount;
            double avgSuccessfulOps =
                (double)sTaskOpCount / (double)sTaskCount;

            // average for failures
            double avgRetries = (double)tryCount / (double)sTaskCount;
            double avgFailedOps = (fTaskCount == 0) ? 0 :
                (double)fTaskOpCount / (double)fTaskCount;

            String reportStr = "TaskCounts:\n";
            reportStr += "  TotalTasks=" + totalTasks +
                "  AvgLength=" + totalAvgLength + "ms\n";
            reportStr += "  Transactional=" + tTaskCount +
                "   AvgLength=" + transactionalAvgLength + "\n";
            reportStr += "  Successful=" + sTaskCount +
                "  AvgLength=" + avgSuccessfulLength + "ms" +
                "  AvgStartDelay=" + avgSuccessfulDelay + "ms" +
                "  AvgRetries=" + avgRetries + "\n";
            reportStr += "  AvgOpCountOnSuccess=" + avgSuccessfulOps +
                "  AvgOpCountOnFailure=" + avgFailedOps + "\n";

            reportStr += "OpCounts:\n";
            for (int i = 1; i <= maxOp + 1; i++) {
                reportStr += "   " + registeredOps[i-1] + "=" +
                    sOpCounts[i-1] + "/" + (sOpCounts[i-1] + fOpCounts[i-1]);
                if (((i % 3) == 0) || (i == (maxOp + 1)))
                    reportStr += "\n";
            }
            reportStr += "\n";

            networkReporter.report(reportStr);
        }
    }

}
