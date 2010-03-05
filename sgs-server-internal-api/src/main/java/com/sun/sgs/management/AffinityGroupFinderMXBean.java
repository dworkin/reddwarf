/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
