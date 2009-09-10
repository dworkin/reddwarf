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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.StandardMBean;

/**
 * Management info for the label propagation affinity group finder.
 */
public class AffinityGroupFinderStats extends StandardMBean
        implements AffinityGroupFinderMXBean
{
    private static final String NAME = "com.sun.sgs.AffinityGroupFinder";
    // Configuration info -- what if we allow this be modified?
    private final int stopIteration;
    // JANE?
    private int numGroups;

    //
    private final AggregateProfileSample iterations;
    private final AggregateProfileSample runtime;
    private final AggregateProfileCounter runs;
    private final AggregateProfileCounter failed;
    private final AggregateProfileCounter stopped;

    // The last time {@link #clear} was called, or when this object
    // was created if {@code clear} has not been called.
    private volatile long lastClear = System.currentTimeMillis();

    /**
     * Constructs the MXBean for affinity group finder information.
     * @param collector the profile collector
     * @param stopIter the maximum iterations a run will perform
     */
    public AffinityGroupFinderStats(ProfileCollector collector, int stopIter) {
        super(AffinityGroupFinderMXBean.class, true);
        stopIteration = stopIter;

        ProfileConsumer consumer = collector.getConsumer(NAME);
        ProfileLevel level = ProfileLevel.MIN;
        ProfileDataType type = ProfileDataType.AGGREGATE;

        iterations = (AggregateProfileSample)
                consumer.createSample("iterations", type, level);
        runtime = (AggregateProfileSample)
                consumer.createSample("runtime", type, level);
        runs = (AggregateProfileCounter)
                consumer.createCounter("runs", type, level);
        failed = (AggregateProfileCounter)
                consumer.createCounter("failed", type, level);
        stopped = (AggregateProfileCounter)
                consumer.createCounter("stopped", type, level);
    }
    /** {@inheritDoc} */
    public double getAvgIterations() {
        return iterations.getAverage();
    }

    /** {@inheritDoc} */
    public double getAvgRunTime() {
        return runtime.getAverage();
    }

    /** {@inheritDoc} */
    public int getMaxIterations() {
        return (int) iterations.getMaxSample();
    }

    /** {@inheritDoc} */
    public long getMaxRunTime() {
        return runtime.getMaxSample();
    }

    /** {@inheritDoc} */
    public long getMinRunTime() {
        return runtime.getMinSample();
    }

    /** {@inheritDoc} */
    public long getNumberFailures() {
        return failed.getCount();
    }

    /** {@inheritDoc} */
    public long getNumberGroups() {
        return numGroups;
    }

    /** {@inheritDoc} */
    public long getNumberRuns() {
        return runs.getCount();
    }

    /** {@inheritDoc} */
    public long getNumberStopped() {
        return stopped.getCount();
    }

    /** {@inheritDoc} */
    public int getStopIteration() {
        return stopIteration;
    }

        /** {@inheritDoc} */
    public void clear() {
        lastClear = System.currentTimeMillis();
        runtime.clearSamples();
        iterations.clearSamples();
        runs.clearCount();
        failed.clearCount();
        stopped.clearCount();
    }

    /** {@inheritDoc} */
    public long getLastClearTime() {
        return lastClear;
    }

    // Overrides for StandardMBean information, giving JMX clients
    // (like JConsole) more information for better displays.

    /** {@inheritDoc} */
    protected String getDescription(MBeanInfo info) {
        return "An MXBean for examining the affinity group finder";
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanAttributeInfo info) {
        String description = null;
        if (info.getName().equals("NumberGroups")) {
            description = "The number of groups found in the last run.";
        } else if (info.getName().equals("NumberRuns")) {
            description = "The number of times the algorithm has run.";
        } else if (info.getName().equals("NumberFailures")) {
            description = "The number of runs that failed.";
        } else if (info.getName().equals("NumberStopped")) {
            description = "The number of runs that were stopped.";
        } else if (info.getName().equals("AvgRunTime")) {
            description = "The average time, in milliseconds, for all runs.";
        } else if (info.getName().equals("MainRunTime")) {
            description = "The minimum time, in milliseconds, for any run.";
        } else if (info.getName().equals("MaxRunTime")) {
            description = "The maximum time, in milliseconds, for any run.";
        } else if (info.getName().equals("AvgIterations")) {
            description = "The average number of iterations algorithm runs " +
                   "required.";
        } else if (info.getName().equals("MaxIterations")) {
            description = "The maximum number of iterations for any run.";
        } else if (info.getName().equals("StopIteration")) {
            description = "The static number of iterations allowed before " +
                    "an algorithm run is stopped.";
        } else if (info.getName().equals("LastClearTime")) {
            description = "The last time this bean was cleared.";
        }
        return description;
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanOperationInfo op) {
        if (op.getName().equals("clear")) {
            return "Clears all data in this bean.";
        }
        return null;
    }
    /** {@inheritDoc} */
    protected int getImpact(MBeanOperationInfo op) {
        if (op.getName().equals("clear")) {
            return MBeanOperationInfo.ACTION;
        }
        return MBeanOperationInfo.UNKNOWN;
    }

    // Package updators
    void iterationsSample(long sample) {
        iterations.addSample(sample);
    }
    void runtimeSample(long sample) {
        runtime.addSample(sample);
    }
    void runsCountInc() {
        runs.incrementCount();
    }

    void failedCountInc() {
        failed.incrementCount();
    }

    void stoppedCountInc() {
        stopped.incrementCount();
    }

    void setNumGroups(int value) {
        numGroups = value;
    }
}
