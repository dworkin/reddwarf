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
     * this MBean was created {@code clear} has never been called.  The
     * time is the difference, measured in milliseconds, between the time at 
     * which this was last cleared and midnight, January 1, 1970 UTC.
     * 
     * @return the time of the last call to {@code clear}
     */
    long getLastClearTime();
}
