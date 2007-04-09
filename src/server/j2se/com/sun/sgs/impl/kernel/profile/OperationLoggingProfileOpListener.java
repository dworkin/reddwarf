/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.ProfileReport;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>ProfileOperationListener</code> aggregates
 * profiling data over a set number of operations, reports the aggregated
 * data when that number of operations have been reported, and then clears
 * its state and starts aggregating again. By default the interval is
 * 100000 tasks.
 * <p>
 * This listener logs its findings at level <code>FINE</code> to the
 * logger named <code>
 * com.sun.sgs.impl.kernel.profile.OperationLoggingProfileOpListener</code>.
 * <p>
 * The <code>
 * com.sun.sgs.impl.kernel.profile.OperationLoggingProfileOpListener.logOps
 * </code> property may be used to set the interval between reports.
 */
public class OperationLoggingProfileOpListener
    implements ProfileOperationListener
{

    // the name of the class
    private static final String CLASSNAME =
        OperationLoggingProfileOpListener.class.getName();

    // the logger where all data is reported
    static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(CLASSNAME));

    // the property for setting the operation window, and its default
    private static final String LOG_OPS_PROPERTY = CLASSNAME + ".logOps";
    private static final int DEFAULT_LOG_OPS = 100000;

    // the number of threads reported as running in the scheduler
    private long threadCount = 0;

    // NOTE: this only supports MAX_OPS operations, which is fine as long
    // as the collector will never allow more than this number to be
    // registered, but when that changes, so too should this code
    private int maxOp = 0;
    private ProfileOperation [] registeredOps =
        new ProfileOperation[ProfileCollectorImpl.MAX_OPS];
    private long [] opCounts = new long[ProfileCollectorImpl.MAX_OPS];

    // the commit/abort total counts, and the reported running time total
    private long commitCount = 0;
    private long abortCount = 0;
    private long totalRunningTime = 0;

    // the last time that we logged an aggregated report
    private long lastReport = System.currentTimeMillis();

    // the number set as the window size for aggregation
    private int logOps;

    /**
     * Creates an instance of <code>OperationLoggingProfileOpListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>TaskOwner</code> to use for all tasks run by
     *              this listener
     * @param taskScheduler the <code>TaskScheduler</code> to use for
     *                      running short-lived or recurring tasks
     * @param resourceCoord the <code>ResourceCoordinator</code> used to
     *                      run any long-lived tasks
     */
    public OperationLoggingProfileOpListener(Properties properties,
                                             TaskOwner owner,
                                             TaskScheduler taskScheduler,
                                             ResourceCoordinator resourceCoord)
    {
        logOps = (new PropertiesWrapper(properties)).
            getIntProperty(LOG_OPS_PROPERTY, DEFAULT_LOG_OPS);
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
        threadCount = schedulerThreadCount;
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        if (profileReport.wasTaskSuccessful())
            commitCount++;
        else
            abortCount++;

        totalRunningTime += profileReport.getRunningTime();

        for (ProfileOperation op : profileReport.getReportedOperations())
            opCounts[op.getId()]++;

        if ((commitCount + abortCount) >= logOps) {
            if (logger.isLoggable(Level.FINE)) {
                long now = System.currentTimeMillis();
                String opCountTally = "";
                for (int i = 0; i < maxOp; i++) {
                    if (i != 0)
                        opCountTally += "\n";
                    opCountTally += "  " + registeredOps[i] + ": " +
                        opCounts[i];
                    opCounts[i] = 0;
                }

                logger.log(Level.FINE, "Operations [logOps=" + logOps +"]:\n" +
                           "  succeeded: " + commitCount +
                           "  failed:" + abortCount + "\n" +
                           "  elapsed time: " + (now - lastReport) + " ms\n" +
                           "  running time: " + totalRunningTime + " ms " +
                           "[threads=" + threadCount + "]\n" + opCountTally);
            } else {
                for (int i = 0; i < maxOp; i++)
                    opCounts[i] = 0;
            }

            commitCount = 0;
            abortCount = 0;
            totalRunningTime = 0;
            lastReport = System.currentTimeMillis();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        // there is nothing to shutdown on this listener
    }

}
