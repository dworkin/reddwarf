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

import com.sun.sgs.service.TaskService;

/**
 * The management interface for the task service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface TaskServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=TaskService";
    
    /**
     * Returns the number of times 
     * {@link TaskService#scheduleNonDurableTask(KernelRunnable, boolean) 
     * scheduleNonDurableTask} has been called.
     * @return the number of times {@code scheduleNonDurableTask} 
     *         has been called
     */
    long getScheduleNonDurableTaskCalls();
    
    /**
     * Returns the number of times 
     * {@link TaskService#scheduleNonDurableTask(KernelRunnable, long, boolean) 
     * scheduleNonDurableTask} has been called with a delay.
     * @return the number of times {@code scheduleNonDurableTask} 
     *         has been called with a delay
     */
    long getScheduleNonDurableTaskDelayedCalls();
    
    /**
     * Returns the number of times 
     * {@link TaskService#scheduleTask(Task) scheduleTask} has been called.
     * @return the number of times {@code scheduleTask} has been called
     */
    long getScheduleTaskCalls();
    
    /**
     * Returns the number of times 
     * {@link TaskService#scheduleTask(Task, long) scheduleTask} 
     * has been called with a delay.
     * @return the number of times {@code scheduleTask} has been called
     *         with a delay.
     */
    long getScheduleDelayedTaskCalls();
    
    /**
     * Returns the number of times
     * {@link TaskService#schedulePeriodicTask(Task, long, long)
     * schedulePeriodicTask} has been called.
     * @return the number of times {@code schedulPeriodicTask} has been called
     */
    long getSchedulePeriodicTaskCalls();
}
