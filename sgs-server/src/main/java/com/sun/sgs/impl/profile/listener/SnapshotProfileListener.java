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
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.util.Formatter;
import java.util.Properties;


/**
 * This implementation of <code>ProfileListener</code> takes
 * snapshots at fixed intervals. It provides a very simple view of what
 * the system has done over the last interval. By default the time
 * interval is 10 seconds.
 * <p>
 * Note that this is still a work in progress. It doesn't report anything
 * except the number of threads and the succeeded/total number of tasks. To
 * get a better view of the system, we probably need things like the time
 * threads are waiting for tasks, the size of the queues, etc., but those
 * haven't been added to the interfaces yet.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43007.
 * <p>
 * The <code>com.sun.sgs.impl.profile.listener.SnapshotProfileListener.</code>
 * root is used for all properties in this class. The <code>report.port</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>report.period</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 *
 * @see AggregateTaskListener
 */
public class SnapshotProfileListener implements ProfileListener {

    // the number of successful tasks and the total number of tasks
    private long successCount = 0;
    private long totalCount = 0;

    // the total number of ready tasks observed
    private int readyCount = 0;

    // lock object for data above, used to ensure that during reporting no
    // one is looking at the data, since it gets cleared after the report
    // is made
    private final Object lock = new Object();
    
    // the number of threads running through the scheduler
    private volatile int threadCount;

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        SnapshotProfileListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".report.port";
    private static final int DEFAULT_PORT = 43007;
    private static final String PERIOD_PROPERTY = PROP_BASE + ".report.period";
    private static final long DEFAULT_PERIOD = 10000;

    /**
     * Creates an instance of <code>SnapshotProfileListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>Identity</code> to use for all tasks run by
     *              this listener
     * @param registry the {@code ComponentRegistry} containing the
     *                 available system components
     *
     * @throws IOException if the server socket cannot be created
     */
    public SnapshotProfileListener(Properties properties, Identity owner,
                                   ComponentRegistry registry)
        throws IOException
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = registry.getComponent(TaskScheduler.class).
            scheduleRecurringTask(new SnapshotRunnable(reportPeriod), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	if (event.getPropertyName().equals("com.sun.sgs.profile.threadcount")) {
	    this.threadCount = ((Integer) event.getNewValue()).intValue();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        synchronized (lock) {
            if (profileReport.wasTaskSuccessful()) {
                successCount++;
            }
            totalCount++;
            readyCount += profileReport.getReadyCount();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        handle.cancel();
        networkReporter.shutdown();
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
            Formatter reportStr = new Formatter();
	    reportStr.format("Snapshot[period=%dms]:%n", reportPeriod);
            synchronized (lock) {
                try {
                    reportStr.format("  Threads=%d", threadCount);
                    reportStr.format("  Tasks=%d/%d%n", 
                                     successCount, totalCount);
                    reportStr.format("  AverageQueueSize=%2.2f tasks%n%n",
				 ((double) readyCount / (double) totalCount));
                } finally {
                    successCount = 0;
                    totalCount = 0;
                    readyCount = 0;
                }
            }

            networkReporter.report(reportStr.toString());
        }
    }

}
