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
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.management.TaskAggregateMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * This implementation of {@code ProfileListener} shows how to
 * use JMX MBeans from within a listener.
 * <p>
 * Every 5 seconds, this listener reports the total number of
 * successful tasks, the average lag time for successful tasks,
 * and the number of successful tasks which were owned by 
 * the kernel.
 * <p>
 * It is an example of using information about particular tasks, which can only
 * be gathered through a ProfileListener, as well as using aggregated
 * information about all tasks, which is available through a registered JMX
 * MBean.  
 */
public class SampleJMXListener implements ProfileListener {
    // the MBean for task aggregated statistics
    private final TaskAggregateMXBean taskBean;
    // the handle for the recurring reporting task
    private final RecurringTaskHandle handle;
    // the number of successful tasks that have been run which are
    // not run for the kernel
    private volatile long numSuccessfulNonKernelTasks = 0;
    
    /**
     * Creates an instance of {@code SampleJMXListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     */
    public SampleJMXListener(Properties properties, Identity owner,
                                 ComponentRegistry registry)
    {
        // Find the MBean which collects our overall task statistics
        ProfileCollector collector = 
                registry.getComponent(ProfileCollector.class);
        taskBean = (TaskAggregateMXBean) 
                collector.getRegisteredMBean(TaskAggregateMXBean.MXBEAN_NAME);
        if (taskBean == null) {
            // The bean hasn't been registerd yet.  This is unexpected,
            // so throw an exception to indicate we're in a bad state.
            throw 
               new IllegalStateException("Could not find task aggregate mbean");
        }
        
        // Ensure that the taskBean statistics are being generated.
        // This means we don't have to enable a default profiling level
        // through a property at start-up.
        // Because consumer names aren't publically declared in a way that
        // makes them easy to find programmatically, we make a best guess
        // search here.
        Map<String, ProfileConsumer> consumers = collector.getConsumers();
        for (Map.Entry<String, ProfileConsumer> entry : consumers.entrySet()) {
            if (entry.getKey().contains("TaskAggregate")) {
                ProfileConsumer taskConsumer = entry.getValue();
                taskConsumer.setProfileLevel(ProfileLevel.MAX);
                break;
            }
        }
        
        // Schedule a periodic task to print reports
        long reportPeriod = 1000 * 5;  // 5 seconds between reports
        handle = registry.getComponent(TaskScheduler.class).
            scheduleRecurringTask(new TaskRunnable(), owner, 
                                  System.currentTimeMillis() + reportPeriod,
                                  reportPeriod);
        handle.start();
    }
    
    /**
     * Update statistics about particular tasks.  In this case, we
     * are reporting the number of successful tasks of a particular type.
     *
     * @param profileReport the summary for the finished {@code Task}
     */
    public void report(ProfileReport profileReport) {
        if (profileReport.wasTaskSuccessful()) {
            String name = profileReport.getTask().getBaseTaskType();
            if (!name.startsWith("com.sun.sgs.impl.kernel")) {
                numSuccessfulNonKernelTasks++;
            }
        }
    }

    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }
    
    /** {@inheritDoc} */
    public void shutdown() {
        handle.cancel();
    }
    
    /**
     * A runnable for periodically reporting task summaries to the
     * network.
     */
    private class TaskRunnable implements KernelRunnable {
        public String getBaseTaskType() {
            return TaskRunnable.class.getName();
        }
        public void run() throws Exception {
            System.out.println("Time:  " + 
                               new Date(System.currentTimeMillis()));
            System.out.printf("  Total tasks run: %d%n" +
                              "  Successful tasks: %d%n" +
                              "  Successful non-kernel tasks: %d%n" +
                              "  Average lag time: %.2fms%n",
                              taskBean.getTaskCount(),
                              (taskBean.getTaskCount() - 
                                  taskBean.getTaskFailureCount()),
                              numSuccessfulNonKernelTasks,
                              taskBean.getSuccessfulLagTimeAvg()
                              );
        }
    }
}
