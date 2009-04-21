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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileReport;

import java.beans.PropertyChangeEvent;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>ProfileListener</code> aggregates
 * profiling data over a set number of operations, reports the aggregated
 * data when that number of operations have been reported, and then clears
 * its state and starts aggregating again. By default the interval is
 * 100000 tasks.
 * <p>
 * This listener logs its findings at level <code>FINE</code> to the
 * logger named <code>
 * com.sun.sgs.impl.profile.listener.OperationLoggingProfileOpListener</code>.
 * <p>
 * The <code>
 * com.sun.sgs.impl.profile.listener.OperationLoggingProfileOpListener.logOps
 * </code> property may be used to set the interval between reports.
 */
public class OperationLoggingProfileOpListener implements ProfileListener {

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

    // counts of the registered operations
    private Map<String, Long> opCounts =
            new HashMap<String, Long>();


    // the commit/abort total counts, and the reported running time total
    private long commitCount = 0;
    private long abortCount = 0;
    private long totalRunningTime = 0;

    // the last time that we logged an aggregated report
    private long lastReport = System.currentTimeMillis();

    // the number set as the window size for aggregation
    private int logOps;

    // a mapping from local counters to their aggregated counts for the
    // current snapshot
    private Map<String, Long> localCounters;

    /**
     * Creates an instance of <code>OperationLoggingProfileOpListener</code>.
     *
     * @param properties the <code>Properties</code> for this listener
     * @param owner the <code>Identity</code> to use for all tasks run by
     *              this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     */
    public OperationLoggingProfileOpListener(Properties properties,
                                             Identity owner,
                                             ComponentRegistry registry)
    {
        logOps = (new PropertiesWrapper(properties)).
            getIntProperty(LOG_OPS_PROPERTY, DEFAULT_LOG_OPS);
	localCounters = new HashMap<String, Long>();
    }

    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
	if (event.getPropertyName().
                equals("com.sun.sgs.profile.newop")) 
        {        
	    ProfileOperation op = (ProfileOperation) (event.getNewValue());
	    opCounts.put(op.getName(), 0L);
	} else {
	    if (event.getPropertyName().
                    equals("com.sun.sgs.profile.threadcount")) 
            {
		threadCount = ((Integer) (event.getNewValue())).intValue();
            }
	}
    }

    /** {@inheritDoc} */
    public void report(ProfileReport profileReport) {
        if (profileReport.wasTaskSuccessful()) {
            commitCount++;
        } else {
            abortCount++;
        }

        totalRunningTime += profileReport.getRunningTime();

        for (String op : profileReport.getReportedOperations()) {
	    Long i = opCounts.get(op);
	    opCounts.put(op, Long.valueOf(i == null ? 1 : i + 1));
	}

	Map<String, Long> counterMap = profileReport.getUpdatedTaskCounters();
	if (counterMap != null) {
	    for (Entry<String, Long> entry : counterMap.entrySet()) {
		String key = entry.getKey();
		long value = 0;
		if (localCounters.containsKey(key)) {
		    value = localCounters.get(key);
                }
		localCounters.put(key, entry.getValue() + value);
	    }
	}

        if ((commitCount + abortCount) >= logOps) {
            if (logger.isLoggable(Level.FINE)) {
                long now = System.currentTimeMillis();
                Formatter opCountTally = new Formatter();
                boolean first = true;
                for (String op : opCounts.keySet()) {
                    if (!first) {
                        opCountTally.format("%n");
                    }
                    first = false;
		    Long count = opCounts.get(op);
                    opCountTally.format("  %s: %d", op,
			(count == null) ? 0 : count.longValue());
                    opCounts.put(op, 0L);
                }

		Formatter counterTally = new Formatter();
		if (!localCounters.isEmpty()) {
		    counterTally.format("[task counters]%n");
		    for (Entry<String, Long> entry : localCounters.entrySet()) {
			counterTally.format(
			    "  %s: %d%n", entry.getKey(), entry.getValue());
                    }
		}

                logger.log(Level.FINE, "Operations [logOps=" + logOps + "]:\n" +
                           "  succeeded: " + commitCount +
                           "  failed: " + abortCount + "\n" +
                           "  elapsed time: " + (now - lastReport) + " ms\n" +
                           "  running time: " + totalRunningTime + " ms " +
                           "[threads=" + threadCount + "]\n" +
			   opCountTally.toString() + "\n" +
			   counterTally.toString());
            } else {
                for (String op : opCounts.keySet()) {
                    opCounts.put(op, 0L);
                }
            }

            commitCount = 0;
            abortCount = 0;
            totalRunningTime = 0;
	    localCounters.clear();
            lastReport = System.currentTimeMillis();
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        // there is nothing to shutdown on this listener
    }

}
