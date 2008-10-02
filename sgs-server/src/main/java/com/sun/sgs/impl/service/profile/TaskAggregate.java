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
package com.sun.sgs.impl.service.profile;

import com.sun.sgs.management.TaskAggregateMXBean;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;

/**
 * The central location to aggregate information on tasks run through the
 * system.
 */
class TaskAggregate extends NotificationBroadcasterSupport
        implements TaskAggregateMXBean, ProfileListener 
{
    private AtomicLong numTasks = new AtomicLong();
    private AtomicLong numReadyTasks = new AtomicLong();
    private AtomicLong numTransactionalTasks = new AtomicLong();
    private AtomicLong numFailedTasks = new AtomicLong();
    private AtomicLong maxRuntime = new AtomicLong();
    /** 
     * Smoothing factor for exponential smoothing, between 0 and 1.
     * A value closer to one provides less smoothing of the data, and
     * more weight to recent data;  a value closer to zero provides more
     * smoothing but is less responsive to recent changes.
     */
    private float smoothingFactor = (float) 0.9;
    /** Exponential moving average of task runtimes */
    private ExponentialAverage taskRuntime = new ExponentialAverage();
    /** Exponential moving average of failed tasks/ all tasks */
    private ExponentialAverage taskFailure = new ExponentialAverage();
    /** Exponential moving average of task ready counts */
    private ExponentialAverage taskReadyCount = new ExponentialAverage();
    /** Exponential moving average of task lag time */
    private ExponentialAverage taskLagTime = new ExponentialAverage();
    /** Exponential moving average of task latency */
    private ExponentialAverage taskLatency = new ExponentialAverage();

    private long seqNumber = 1;
    
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
     * Implement ProfileListener.
     */
    /** {@inheritDoc} */
    public void propertyChange(PropertyChangeEvent event) {
    }

    /** {@inheritDoc} */
    public synchronized void report(ProfileReport profileReport) {
        // jane - are we comfortable with doing all these updates
        // in a synchronized block?  For *every* task?
        // or should I just have an aggregator and the caller needs
        // to know the number of tasks from the last call?
        numTasks.incrementAndGet();
        if (profileReport.wasTaskTransactional()) {
            numTransactionalTasks.incrementAndGet();
        }

//        System.out.println("ready count...");
        numReadyTasks.addAndGet(profileReport.getReadyCount());
        taskReadyCount.update(profileReport.getReadyCount());

        if (profileReport.wasTaskSuccessful()) {
            long newRunningTime = profileReport.getRunningTime();
            double newLagTime = profileReport.getActualStartTime() -
                    profileReport.getScheduledStartTime();
//            System.out.println("JANE running " + newRunningTime + 
//                                  " lag " + newLagTime);
            if (newRunningTime > maxRuntime.longValue()) {
                maxRuntime.set(newRunningTime);
            }
            taskRuntime.update(newRunningTime);
            taskLagTime.update(newLagTime);
            taskLatency.update(newRunningTime + newLagTime);
        } else {
            numFailedTasks.incrementAndGet();
//            System.out.println("task failed");
        }
        taskFailure.update((numFailedTasks.longValue() * 100) / 
                            numTasks.doubleValue());
//        System.out.println("task failure avg is " + taskFailure.avg);
    }

    /** {@inheritDoc} */
    public void shutdown() {
    }

    /*
     * Implement MBean.
     */
    /** {@inheritDoc} */
    public long getTaskCount() {
        return numTasks.longValue();
    }

    /** {@inheritDoc} */
    public long getTransactionalTaskCount() {
        return numTransactionalTasks.longValue();
    }

    /** {@inheritDoc} */
    public long getFailedTaskCount() {
        return numFailedTasks.longValue();
    }

    /** {@inheritDoc} */
    public long getMaxRuntime() {
        return maxRuntime.longValue();
    }

    /** {@inheritDoc} */
    public float getSmoothingFactor() {
        return smoothingFactor;
    }

    /** {@inheritDoc} */
    public void setSmoothingFactor(float newFactor) {
        smoothingFactor = newFactor;
    }

    /** {@inheritDoc} */
    public double getTaskRuntimeAvg() {
        return taskRuntime.avg;
    }

    /** {@inheritDoc} */
    public double getTaskFailureAvg() {
        return taskFailure.avg;
    }

    /** {@inheritDoc} */
    public double getTaskReadyCountAvg() {
        return taskReadyCount.avg;
    }

    /** {@inheritDoc} */
    public double getTaskLagTimeAvg() {
        return taskLagTime.avg;
    }

    /** {@inheritDoc} */
    public double getTaskLatencyAvg() {
        return taskLatency.avg;
    }

    /** {@inheritDoc} */
    public double getQueueSize() {
        return numReadyTasks.longValue() / numTasks.doubleValue();
    }

    /** {@inheritDoc} */
    public long getTaskReadyCountTotal() {
        return numReadyTasks.longValue();
    }

    private class ExponentialAverage {

        private double last;
        double avg;

        void update(double data) {
            final double old = last;
            last = avg;
//            avg = old + (smoothingFactor * (data - avg));
            avg = (data - old) * smoothingFactor + old;
//            System.out.println("avg: " + avg);
        }
    }
}
