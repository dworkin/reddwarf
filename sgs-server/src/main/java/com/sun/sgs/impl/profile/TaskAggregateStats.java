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

package com.sun.sgs.impl.profile;

import com.sun.sgs.management.TaskAggregateMXBean;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import java.util.logging.Level;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;

/**
 *
 * 
 */
public class TaskAggregateStats extends NotificationBroadcasterSupport
        implements TaskAggregateMXBean
{
    final AggregateProfileCounter numTasks;
    final AggregateProfileCounter numTransactionalTasks;
//    final AggregateProfileCounter numSuccessfulTasks;
    final AggregateProfileCounter numFailedTasks;
    final AggregateProfileCounter numReadyTasks;
    
    /* task ready counts */
    final AggregateProfileSample readyCount;
    /* task runtimes */
    final AggregateProfileSample runtime;
    /* failed tasks / all tasks  JANE ? */
//    final AggregateProfileSample failureRate;
    /* task lag time JANE ? */
    final AggregateProfileSample lagTime;
    
    /** 
     * Smoothing factor for exponential smoothing, between 0 and 1.
     * A value closer to one provides less smoothing of the data, and
     * more weight to recent data;  a value closer to zero provides more
     * smoothing but is less responsive to recent changes.
     */
    // JANE?
    private float smoothingFactor = (float) 0.9;

    private long seqNumber = 1;
    
    TaskAggregateStats(ProfileCollector collector, String name) {
        ProfileConsumer consumer = collector.getConsumer(name);

        ProfileLevel level = ProfileLevel.MIN;
        // These statistics are reported to the profile reports
        // directly, with the ProfileCollector.
        // They should not be reported as TASK_AND_AGGREGATE because,
        // for nested tasks, we don't want to merge their values into
        // the parent's value.
        ProfileDataType type = ProfileDataType.AGGREGATE;
        
        numTasks = (AggregateProfileCounter)
                consumer.createCounter("numTasks", type, level);
        numTransactionalTasks = (AggregateProfileCounter)
                consumer.createCounter("numTransactionalTasks", type, level);
//        numSuccessfulTasks = (AggregateProfileCounter)
//                consumer.createCounter("numSuccessfulTasks", type, level);
        numFailedTasks = (AggregateProfileCounter)
                consumer.createCounter("numFailedTasks", type, level);
        numReadyTasks = (AggregateProfileCounter)
                consumer.createCounter("numReadyTasks", type, level);
        readyCount = (AggregateProfileSample)
                consumer.createSample("readyCount", type, level);
        runtime = (AggregateProfileSample)
                consumer.createSample("runtime", type, level);
//        failureRate = (AggregateProfileSample)
//                consumer.createSample("failureRate", type, level);
        lagTime = (AggregateProfileSample)
                consumer.createSample("lagTime", type, level);
        // Don't actually save any of our sample values.  We expect
        // a great number of them to be generated.
        readyCount.setCapacity(0);
        runtime.setCapacity(0);
//        failureRate.setCapacity(0);
        lagTime.setCapacity(0);
    }
    /** {@inheritDoc} */
    public void notifyTaskQueue() {
        sendNotification(
                new Notification("com.sun.sgs.task.queue.behind",
                                 this,
                                 System.currentTimeMillis(),
                                 seqNumber++));
    }
    
    /*
     * Implement NotificationEmitter.
     */
    
    /** {@inheritDoc} */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
         String[] types = {"com.sun.sgs.task.queue.behind"};
         String name = Notification.class.getName();
         String description = "Task queue is not keeping up";
         MBeanNotificationInfo info =
             new MBeanNotificationInfo(types, name, description);
         return new MBeanNotificationInfo[] {info};
     }

    /*
     * Implement MBean.
     */
    /** {@inheritDoc} */
    public long getTaskCount() {
        return numTasks.getCount();
    }

    /** {@inheritDoc} */
    public long getTransactionalTaskCount() {
        return numTransactionalTasks.getCount();
    }

    /** {@inheritDoc} */
    public long getFailedTaskCount() {
        return numFailedTasks.getCount();
    }

    /** {@inheritDoc} */
    public long getMaxRuntime() {
        return runtime.getMaxSample();
    }

    /** {@inheritDoc} */
    public float getSmoothingFactor() {
        //JANE?
        return smoothingFactor;
    }

    /** {@inheritDoc} */
    public void setSmoothingFactor(float newFactor) {
        smoothingFactor = newFactor;
    }

    /** {@inheritDoc} */
    public double getTaskRuntimeAvg() {
        return runtime.getAverage();
    }

    /** {@inheritDoc} */
    public double getTaskFailureAvg() {
//        return failureRate.getAverage();
//        System.out.println("failed task count is " + getFailedTaskCount());
//        System.out.println("total task count is " +  getTaskCount());
        return (getFailedTaskCount() * 100) / (double) getTaskCount();
    }

    /** {@inheritDoc} */
    public double getTaskReadyCountAvg() {
        return readyCount.getAverage();
    }

    /** {@inheritDoc} */
    public double getTaskLagTimeAvg() {
        return lagTime.getAverage();
    }

    /** {@inheritDoc} */
    public double getTaskLatencyAvg() {
        double successful = getTaskCount() - getFailedTaskCount();
        return (getTaskRuntimeAvg() + getTaskLagTimeAvg()) / successful;
    }

    /** {@inheritDoc} */
    public double getQueueSize() {
        return numReadyTasks.getCount() / numTasks.getCount();
    }

    /** {@inheritDoc} */
    public long getTaskReadyCountTotal() {
        return numReadyTasks.getCount();
    }
}
