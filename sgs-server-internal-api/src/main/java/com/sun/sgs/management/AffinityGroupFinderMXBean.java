/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.management;

/**
 *  The management interface for the affinity group finder.
 */
public interface AffinityGroupFinderMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=AffinityGroupFinder";

    /**
     * Returns the number of groups found in the latest run of the
     * affinity group finder.
     * @return the number of groups found in the latest run
     */
    long getNumberGroups();

    /**
     * Returns the number of times the affinity group finder has run,
     * including runs that fail.
     * @return the number of times the affinity group finder has run
     */
    long getNumberRuns();

    /**
     * Returns the number of times the affinity group finder has failed,
     * due to errors or node failures during a run.
     * @return the number of times the affinity group finder has failed
     */
    long getNumberFailures();

    /**
     * Returns the number of times the affinity group finder was stopped
     * due to not converging soon enough.  A stopped run is not a failed
     * run;  valid results are returned.
     * @return the number of times the affinity group finder was stopped
     */
    long getNumberStopped();

    /**
     * Returns the average amount of time, in milliseconds, spent in algorithm
     * runs.
     * @return the average amount of time spent in algorithm runs
     */
    double getAvgRunTime();

    /**
     * Returns the minimum amount of time, in milliseconds, spent in an
     * algorithm run.
     * @return the minimum amount of time spent in an algorithm run
     */
    long getMinRunTime();

    /**
     * Returns the maximum amount of time, in milliseconds, spent in an
     * algorithm run.
     * @return the maximum amount of time spent in an algorithm run
     */
    long getMaxRunTime();

    /**
     * Returns the average number of iterations required for algorithm runs.
     * @return the average number of iterations required for algorithm runs
     */
    double getAvgIterations();

    /**
     * Returns the max number of iterations for any algorithm run.
     * @return the max number of iterations for any algorithm run
     */
    int getMaxIterations();

    /**
     * Returns the configured maximum number of iterations allowed to run
     * before stopping the algorithm and returning the current results.
     * @return the configured maximum number of iterations
     */
    int getStopIteration();

    /**
     * Clears all data values.
     */
    void clear();

    /**
     * Returns the time of the last call to {@link #clear}, or the time
     * this MBean was created if {@code clear} has never been called.  The
     * time is the difference, measured in milliseconds, between the time at
     * which this was last cleared and midnight, January 1, 1970 UTC.
     *
     * @return the time of the last call to {@code clear}
     */
    long getLastClearTime();

    /**
     * Finds affinity groups based on the current graph information.  This
     * will start a new run of the affinity group finder, or wait for the
     * current run to complete.
     * <p>
     * NOTE:  This method is useful for testing but may be removed.
     */
    void findAffinityGroups();
}
