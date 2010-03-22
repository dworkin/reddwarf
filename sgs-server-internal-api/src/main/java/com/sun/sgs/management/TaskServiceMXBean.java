/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
