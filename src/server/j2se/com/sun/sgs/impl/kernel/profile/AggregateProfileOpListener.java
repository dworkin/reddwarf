
package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfiledOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
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

    // NOTE: this will only support 256 operations, which is fine for now
    // and used to make this faster, but may not be desirable ultimately
    private int maxOp = 0;
    private ProfiledOperation [] registeredOps = new ProfiledOperation[256];
    private long [] sOpCounts = new long[256];
    private long [] fOpCounts = new long[256];
    
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
        int port =
            Integer.parseInt(properties.
                             getProperty(getClass().getName() + ".reportPort",
                                         "43005"));
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            Long.parseLong(properties.
                           getProperty(getClass().getName() + ".reportPeriod",
                                       "5000"));
        handle = taskScheduler.
            scheduleRecurringTask(new AggregatingRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void notifyNewOp(ProfiledOperation op) {
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
    public void report(KernelRunnable task, boolean transactional,
                       TaskOwner owner, long scheduledStartTime,
                       long actualStartTime, long runningTime,
                       List<ProfiledOperation> ops, int retryCount,
                       boolean succeeded) {
        if (succeeded) {
            sTaskCount++;
            sTaskOpCount += ops.size();
            sRunTime += runningTime;
            delayTime += (actualStartTime - scheduledStartTime);
            tryCount += retryCount;
            for (ProfiledOperation op : ops)
                sOpCounts[op.getId()]++;
        } else {
            fTaskCount++;
            fTaskOpCount += ops.size();
            fRunTime += runningTime;
            for (ProfiledOperation op : ops)
                fOpCounts[op.getId()]++;
        }

        if (transactional) {
            tTaskCount++;
            tRunTime += runningTime;
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
                "   AvgLength" + transactionalAvgLength + "\n";
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
