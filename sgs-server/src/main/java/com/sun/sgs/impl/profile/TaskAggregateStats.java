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
 * --
 */

package com.sun.sgs.impl.profile;

import com.sun.sgs.impl.kernel.ConfigManager;
import com.sun.sgs.management.TaskAggregateMXBean;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;

/**
 * The central location to aggregate information on tasks run through the
 * system.  Only bounded transactions are aggregated.  Successful
 * transactions that take longer than the standard transaction timeout
 * are typically used for application startup tasks, and are not included.
 */
public class TaskAggregateStats extends NotificationBroadcasterSupport
        implements TaskAggregateMXBean
{
    private static final double DEFAULT_SMOOTHING_FACTOR = 0.01;
    
    /* Task counts */
    private final AggregateProfileCounter numTasks;
    private final AggregateProfileCounter numTransactionalTasks;
    private final AggregateProfileCounter numFailedTasks;
    
    /* Statistics for all tasks */
    private final AggregateProfileSample readyCount;
    
    /* Statistics for successful tasks */
    private final AggregateProfileSample runtime;
    private final AggregateProfileSample lagtime;
    private final AggregateProfileSample latency;
    
    /** 
     * Smoothing factor for exponential smoothing, between 0 and 1.
     * A value closer to one provides less smoothing of the data, and
     * more weight to recent data;  a value closer to zero provides more
     * smoothing but is less responsive to recent changes.
     * <p>
     * There's a lot of data coming through here, so we want a lot of smoothing.
     */
    private double smoothingFactor = DEFAULT_SMOOTHING_FACTOR;

    /**
     * The last time {@link #clear} was called, or when this object
     * was created if {@code clear} has not been called.
     */
    private volatile long lastClear = System.currentTimeMillis();

    /**
     * The standard transaction timeout, used to determine if a successful
     * task was unbounded or not.  We don't count the unbounded tasks in
     * our statistics.
     */
    private volatile long standardTimeout;
        
    /** 
     * Our profile collector, used to lazily set the standardTimeout.
     */
    private final ProfileCollector collector;
    
    /**
     * A latch for the first task, so we can lazily retrieve the
     * standard transaction timeout.
     */
    private boolean firstTask = true;
    
    // Notification information - we do not yet emit notifications for 
    // this MBean.  We do not yet document these notification types, as 
    // they aren't used yet.
    
    /** Description of the notifications. */
    private static MBeanNotificationInfo[] notificationInfo =
        new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(
                    new String[] {"com.sun.sgs.task.queue.behind"},
                    Notification.class.getName(),
                    "Task queue is not keeping up") };
    /** The sequence number for notifications */
    private AtomicLong seqNumber = new AtomicLong();
    
    /**
     * Creates an MXBean object for gathering task data in the system.
     * 
     * @param collector the system profile collector
     * @param name the name of the profile consumer created to support this 
     *              object
     */
    TaskAggregateStats(ProfileCollector collector, String name) {
        super(notificationInfo);
        this.collector = collector;
        ProfileConsumer consumer = collector.getConsumer(name);
        
        // We could determine that some of these statistics need to be
        // on all the time (level MIN) so we can use them for load balancing
        // or because they are extremely useful.
        // 
        // Note that if the counters are all on the time, we could just
        // as easily use AtomicLongs rather than AggregateProfileCounters.
        ProfileLevel level = ProfileLevel.MEDIUM;
        
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
        numFailedTasks = (AggregateProfileCounter)
                consumer.createCounter("numFailedTasks", type, level);
        readyCount = (AggregateProfileSample)
                consumer.createSample("readyCount", type, level);
        runtime = (AggregateProfileSample)
                consumer.createSample("runtime", type, level);
        lagtime = (AggregateProfileSample)
                consumer.createSample("lagTime", type, level);
        latency = (AggregateProfileSample)
                consumer.createSample("latency", type, level);
        setSmoothing(smoothingFactor);
    }
    
    
    // This is how we'd send a notification - this is not yet well defined.
    // Need to determine what notifications make sense here and figure out
    // how to decide when they should be sent.
    // Also, perhaps JMX monitors would work better here?
    /**
     * Send a notification that the task queue is falling behind.  This
     * method is not used yet.
     */
    void notifyTaskQueue() {
        sendNotification(
                new Notification("com.sun.sgs.task.queue.behind",
                                 MXBEAN_NAME,
                                 seqNumber.incrementAndGet(),
                                 System.currentTimeMillis(),
                                 "Task queue is behind"));
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
    public long getTaskFailureCount() {
        return numFailedTasks.getCount();
    }

    /** {@inheritDoc} */
    public double getSmoothingFactor() {
        return smoothingFactor;
    }

    /** {@inheritDoc} */
    public void setSmoothingFactor(double newFactor) {
        setSmoothing(newFactor);
    }

    /** {@inheritDoc} */
    public long getSuccessfulRuntimeMax() {
        return runtime.getMaxSample();
    }
    
    /** {@inheritDoc} */
    public double getSuccessfulRuntimeAvg() {
        return runtime.getAverage();
    }

    /** {@inheritDoc} */
    public double getTaskFailurePercentage() {
        return (getTaskFailureCount() * 100) / (double) getTaskCount();
    }

    /** {@inheritDoc} */
    public double getReadyCountAvg() {
        return readyCount.getAverage();
    }

    /** {@inheritDoc} */
    public double getSuccessfulLagTimeAvg() {
        return lagtime.getAverage();
    }

    /** {@inheritDoc} */
    public double getSuccessfulLatencyAvg() {
        return latency.getAverage();
    }

    /** {@inheritDoc} */
    public void clear() {
        lastClear = System.currentTimeMillis();
        numTasks.clearCount();
        numTransactionalTasks.clearCount();
        numFailedTasks.clearCount();
        readyCount.clearSamples();
        runtime.clearSamples();
        lagtime.clearSamples();
        latency.clearSamples();
    }
    
    /** {@inheritDoc} */
    public long getLastClearTime() {
        return lastClear;
    }
    
    // Methods used by ProfileCollector to update our values when
    // tasks complete
    void taskFinishedSuccess(boolean trans, long ready, long run, long lag) {
        // Don't include unbounded tasks in our statistics.
        if (firstTask) {
            firstTask = false;
            ConfigManager config = (ConfigManager) 
                collector.getRegisteredMBean(ConfigManager.MXBEAN_NAME);
            standardTimeout = config.getStandardTxnTimeout();
        }
        if (run > standardTimeout) {
            return;
        }
        
        taskFinishedCommon(trans, ready);
        
        runtime.addSample(run);
        lagtime.addSample(lag);
        latency.addSample(run + lag);
    }
    
    void taskFinishedFail(boolean trans, long ready) {
        taskFinishedCommon(trans, ready);
        numFailedTasks.incrementCount();
    }
    
    void taskFinishedCommon(boolean trans, long ready) {
        numTasks.incrementCount();
        if (trans) {
            numTransactionalTasks.incrementCount();
        }
        readyCount.addSample(ready);
    }
    
    /**
     * Set the smoothing factor for each sample.
     * @param smooth the new smoothing factor
     * @throws IllegalArgumentException if {@code smooth} is not between
     *                                  0.0 and 1.0, inclusive
     */
    private void setSmoothing(double smooth) {
        readyCount.setSmoothingFactor(smooth);
        runtime.setSmoothingFactor(smooth);
        lagtime.setSmoothingFactor(smooth);
        latency.setSmoothingFactor(smooth);
        smoothingFactor = smooth;
    }
}
