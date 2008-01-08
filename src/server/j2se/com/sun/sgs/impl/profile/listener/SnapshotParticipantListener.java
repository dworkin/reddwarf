/*
 * Copyright 2008 Sun Microsystems, Inc.
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
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.io.IOException;

import java.text.DecimalFormat;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;


/**
 * This implementation of <code>ProfileListener</code> takes
 * snapshots at fixed intervals. It provides a very simple view of
 * transactional participants that were active over the last interval. By
 * default the time interval is 5 seconds.
 * <p>
 * This listener reports its findings on a server socket. Any number of
 * users may connect to that socket to watch the reports. The default
 * port used is 43012.
 * <p>
 * The
 * <code>com.sun.sgs.impl.profile.listener.SnapshotParticipantListener</code>
 * root is used for all properties in this class. The <code>reportPort</code>
 * key is used to specify an alternate port on which to report profiling
 * data. The <code>reportPeriod</code> key is used to specify the length of
 * time, in milliseconds, between reports.
 */
public class SnapshotParticipantListener implements ProfileListener {

    // the reporter used to publish data
    private NetworkReporter networkReporter;

    // the handle for the recurring reporting task
    private RecurringTaskHandle handle;

    // the base name for properties
    private static final String PROP_BASE =
        SnapshotParticipantListener.class.getName();

    // the supported properties and their default values
    private static final String PORT_PROPERTY = PROP_BASE + ".reportPort";
    private static final int DEFAULT_PORT = 43012;
    private static final String PERIOD_PROPERTY = PROP_BASE + ".reportPeriod";
    private static final long DEFAULT_PERIOD = 5000;

    // the map of all participants in the current snapshot window
    private HashMap<String,ParticipantCounts> participantMap;

    // the number of transactional tasks from the current snapshot window
    private int taskCount = 0;

    // a simple formatter used to make decimal numbers easier to read
    static final DecimalFormat df = new DecimalFormat();
    static {
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);
    }

    /**
     * Creates an instance of <code>SnapshotParticipantListener</code>.
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
    public SnapshotParticipantListener(Properties properties, TaskOwner owner,
				       TaskScheduler taskScheduler,
				       ResourceCoordinator resourceCoord)
	throws IOException
    {
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        participantMap = new HashMap<String,ParticipantCounts>();

        int port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
        networkReporter = new NetworkReporter(port, resourceCoord);

        long reportPeriod =
            wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        handle = taskScheduler.
            scheduleRecurringTask(new ParticipantRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
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
	if (profileReport.wasTaskTransactional()) {
	    synchronized (participantMap) {
		taskCount++;
		for (ProfileParticipantDetail detail :
			 profileReport.getParticipantDetails()) {
		    ParticipantCounts counts =
			participantMap.get(detail.getParticipantName());
		    if (counts == null) {
			counts = new ParticipantCounts();
			participantMap.
			    put(detail.getParticipantName(), counts);
		    }
		    counts.time += detail.getPrepareTime();
		    if (detail.wasCommitted()) {
			if (! detail.wasCommittedDirectly())
			    counts.time += detail.getCommitTime();
			counts.commits++;
		    } else {
			counts.time += detail.getAbortTime();
			counts.aborts++;
		    }
		}
	    }
	}
    }

    /**
     * Private class used to track values for a single participant.
     */
    private class ParticipantCounts {
	long time = 0;
	int commits = 0;
	int aborts = 0;
	public String toString() {
	    double taskTotal = (double)(commits + aborts);
	    double participationPct = (taskTotal / (double)taskCount) * 100.0;
	    double commitPct = ((double)commits / taskTotal) * 100.0;
	    return " participated=" + df.format(participationPct) +
		"% committed=" + df.format(commitPct) + "% avgTime=" +
		df.format((double)time / taskTotal) + "ms";
	}
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        
    }

    /**
     * Private internal class that is used to run a recurring task that
     * reports on the collected data.
     */
    private class ParticipantRunnable implements KernelRunnable {
        public String getBaseTaskType() {
            return ParticipantRunnable.class.getName();
        }
        public void run() throws Exception {
	    String reportStr;
	    synchronized (participantMap) {
		reportStr = "Participants for last " + taskCount +
		    " transactional tasks:\n";
                for (Entry<String,ParticipantCounts> entry :
			 participantMap.entrySet())
		    reportStr += entry.getKey() + entry.getValue() + "\n";
		participantMap.clear();
		taskCount = 0;
	    }
	    reportStr += "\n";
	    networkReporter.report(reportStr);
	}
    }

}
