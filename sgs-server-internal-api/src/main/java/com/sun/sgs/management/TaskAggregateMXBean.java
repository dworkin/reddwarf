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
 * The management interface for tasks that have run.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface TaskAggregateMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=TaskAggregate";
    
    // Thruput?  Notifications?  Or maybe monitor service would be used
    // for that?
    
    /**
     * Returns the total number of tasks run.
     * @return the total number of tasks run
     */
    long getTaskCount();

    /**
     * Returns the total number of transactional tasks run.
     * @return the total number of transactional tasks run
     */
    long getTransactionalTaskCount();
    
    /**
     * Returns the total number of tasks which failed.
     * @return the total number of tasks which failed
     */
    long getTaskFailureCount();
    
    /**
     * Returns the percentage of failed tasks to total tasks.
     * @return the percentage of failed tasks to total tasks
     */
    double getTaskFailurePercentage();
      
    /**
     * Returns the average number of tasks which are ready to run in
     * the system.  A large average indicates the task queue is falling 
     * behind.
     * @return the average number of tasks which are ready to run 
     */
    double getReadyCountAvg();
    
    /**
     * Returns the maximum runtime for successful tasks, in milliseconds.
     * @return the maximum runtime for successful tasks
     */
    long getSuccessfulRuntimeMax();
    
    /**
     * Returns the average runtime for successful tasks, in milliseconds.
     * @return the average runtime for successful tasks
     */
    double getSuccessfulRuntimeAvg();

    /**
     * Returns the average lag time for successful tasks, in milliseconds. 
     * Lag time is the amount of time spent on the ready queue before the 
     * task starts.
     * @return the average lag time for successful tasks
     */
    double getSuccessfulLagTimeAvg();
    
    /**
     * Returns the average latency for successful tasks, in milliseconds. 
     * Latency is total amount of time it takes a successful task to run, and 
     * is the sum of lagtime and runtime.
     * @return the average latency for successful tasks.
     */
    double getSuccessfulLatencyAvg();
    
    /**
     * Returns the smoothing factor used for averages in this MBean.
     * @return the smoothing factory used for averages
     */
    double getSmoothingFactor();
    
    /**
     * Set the smoothing factor for all averages in this MBean, between
     * {@code 0.0} and {@code 1.0}, inclusive. A value closer to {@code 1.0} 
     * provides less smoothing of the data, and more weight to recent data; a 
     * value closer to {@code 0.0} provides more smoothing but is less 
     * responsive to recent changes. 
     *
     * @param newFactor the new smoothing factor
     * @throws IllegalArgumentException if the value is not between {@code 0.0} 
     *                                  and {@code 1.0}, inclusive
     */
    void setSmoothingFactor(double newFactor);
    
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
}
