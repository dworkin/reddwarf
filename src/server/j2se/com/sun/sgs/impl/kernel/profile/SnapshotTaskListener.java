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

import java.text.DecimalFormat;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;


/**
 *
 */
public class SnapshotTaskListener implements ProfileOperationListener {

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        SnapshotTaskListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".report.port";
    private static final int DEFAULT_PORT = 43010;
    private static final String PERIOD_PROPERTY = PROP_BASE + ".report.period";
    private static final long DEFAULT_PERIOD = 5000;

    private HashMap<String,TaskDetail> map;

    private ProfileOperation [] knownOps =
        new ProfileOperation[ProfileCollectorImpl.MAX_OPS];

    static final DecimalFormat df = new DecimalFormat();
    static {
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);
    }

    /**
     *
     */
    public SnapshotTaskListener(Properties properties, TaskOwner owner,
                                TaskScheduler taskScheduler,
                                ResourceCoordinator resourceCoord)
        throws IOException
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        map = new HashMap<String,TaskDetail>();

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = taskScheduler.
            scheduleRecurringTask(new TaskRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void notifyNewOp(ProfileOperation op) {
        knownOps[op.getId()] = op;
    }

    /**
     *
     */
    public void notifyThreadCount(int schedulerThreadCount) {
        // this listener doesn't track thread counts
    }

    /**
     *
     */
    public void report(ProfileReport profileReport) {
        if (profileReport.wasTaskSuccessful()) {
            String name = profileReport.getTask().getBaseTaskType();
            if (! name.startsWith("com.sun.sgs.impl.kernel")) {
                synchronized (map) {
                    TaskDetail detail = map.get(name);
                    if (detail == null) {
                        detail = new TaskDetail();
                        map.put(name, detail);
                    }
                    detail.count++;
                    detail.time += profileReport.getRunningTime();
                    detail.opCount +=
                        profileReport.getReportedOperations().size();
                    detail.retries += profileReport.getRetryCount();
                    for (ProfileOperation op :
                             profileReport.getReportedOperations())
                        detail.ops[op.getId()]++;
                }
            }
        }
    }

    /**
     *
     */
    public void shutdown() {
        
    }

    private class TaskDetail {
        long count = 0;
        long time = 0;
        long opCount = 0;
        long retries = 0;
        long [] ops = new long[ProfileCollectorImpl.MAX_OPS];

        public String toString() {
            double avgTime = (double)time / (double)count;
            double avgOps = (double)opCount / (double)count;
            String str = " avgTime=" + df.format(avgTime) + "ms avgOps=" +
                df.format(avgOps) + " [" + count + "/" + retries + "]";
            if (opCount > 0)
                str += "\n  ";
            for (int i = 0; i < ops.length; i++) {
                if (ops[i] > 0)
                    str += knownOps[i] + "=" +
                        df.format((double)(ops[i]) / (double)count) + "ops ";
            }
            return str;
        }
    }

    /**
     *
     */
    private class TaskRunnable implements KernelRunnable {
        public String getBaseTaskType() {
            return TaskRunnable.class.getName();
        }
        public void run() throws Exception {
            String reportStr = "";
            synchronized (map) {
                for (Entry<String,TaskDetail> entry : map.entrySet())
                    reportStr += entry.getKey() + entry.getValue() + "\n";
                map.clear();
            }
            reportStr += "\n";
            networkReporter.report(reportStr);
        }
    }

}
