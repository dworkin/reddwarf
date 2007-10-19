/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.profile.util.NetworkReporter;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.util.Properties;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This implementation of <code>ProfileListener</code> takes
 * snapshots at fixed intervals. It provides a very simple view of what
 * the system has done over the last interval.
 * It reports its findings on a TCP server socket. Any number of
 * users may connect to that socket to watch the reports.
 * <p>
 * Note that this is still a work in progress. It doesn't report anything
 * except the number of threads and the succeeded/total number of tasks. To
 * get a better view of the system, we probably need things like the time
 * threads are waiting for tasks, the size of the queues, etc., but those
 * haven't been added to the interfaces yet.
 * <p>
 * The {@value #PROP_BASE} root is used for all properties in this class:
 * <ul>
 * <li>The {@value #PERIOD_PROPERTY_KEY} key is used to set the the
 * reporting time interval, in milliseconds.
 * By default, it is {@value #DEFAULT_PERIOD} milliseconds.
 * <li>
 * The {@value #PORT_PROPERTY_KEY} key is used to specify an alternate TCP
 * port on which to report profiling data.
 * Port {@value #DEFAULT_PORT} is used by default.
 * </ul>
 * 
 * @see AggregateTaskListener
 */
public class SnapshotProfileListener implements ProfileListener {

    // the number of successful tasks and the total number of tasks
    private volatile long successCount = 0;
    private volatile long totalCount = 0;

    // the total number of ready tasks observed
    private volatile int readyCount = 0;

    // the number of threads running through the scheduler
    private volatile int threadCount;

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // a flag used to make sure that during reporting no one is looking
    // at the data, since it gets cleared after the report is made
    private AtomicBoolean flag;

    /** The root for all properties in this class: {@value #PROP_BASE} */
    public static final String PROP_BASE =
        "com.sun.sgs.impl.profile.listener.SnapshotProfileListener.";

    // the supported properties and their default values

    /** The property key to specify the port: {@value #PORT_PROPERTY_KEY} */
    public static final String PORT_PROPERTY_KEY = "report.port";

    /** The default port: {@value #DEFAULT_PORT} */
    public static final int DEFAULT_PORT = 43007;

    /**
     * The property key to specify the period, in milliseconds:
     * {@value #PERIOD_PROPERTY_KEY}
     */
    public static final String PERIOD_PROPERTY_KEY = "report.period";

    /** The default period: {@value #DEFAULT_PERIOD} milliseconds. */
    public static final long DEFAULT_PERIOD = 10000;

    /**
     * Creates an instance of <code>SnapshotProfileListener</code>.
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
    public SnapshotProfileListener(Properties properties, TaskOwner owner,
                                   TaskScheduler taskScheduler,
                                   ResourceCoordinator resourceCoord)
        throws IOException
    {
        flag = new AtomicBoolean(false);

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        int port = wrappedProps.getIntProperty(
                PROP_BASE + PORT_PROPERTY_KEY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod = wrappedProps.getLongProperty(
                PROP_BASE + PERIOD_PROPERTY_KEY, DEFAULT_PERIOD);
        handle = taskScheduler.
            scheduleRecurringTask(new SnapshotRunnable(reportPeriod), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	if (event.getPropertyName().equals("com.sun.sgs.profile.threadcount"))
	    this.threadCount = ((Integer)event.getNewValue()).intValue();
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        while (! flag.compareAndSet(false, true));

        try {
            if (profileReport.wasTaskSuccessful())
                successCount++;
            totalCount++;
            readyCount += profileReport.getReadyCount();
        } finally {
            flag.set(false);
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
    private class SnapshotRunnable implements KernelRunnable {
        private final long reportPeriod;
        SnapshotRunnable(long reportPeriod) {
            this.reportPeriod = reportPeriod;
        }
        public String getBaseTaskType() {
            return SnapshotRunnable.class.getName();
        }
        public void run() throws Exception {
            while (! flag.compareAndSet(false, true));

            String reportStr = "Snapshot[period=" + reportPeriod + "ms]:\n";
            try {
                reportStr += "  Threads=" + threadCount + "  Tasks=" +
                    successCount + "/" + totalCount + "\n";
                reportStr += "  AverageQueueSize=" +
                    ((double)readyCount / (double)totalCount) + " tasks\n\n";
            } finally {
                successCount = 0;
                totalCount = 0;
                readyCount = 0;
                flag.set(false);
            }

            networkReporter.report(reportStr);
        }
    }

}
