/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Iterator;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>SystemScheduler</code> uses a very simple
 * Round-Robin behavior to provide basic fairness between applications.
 * Essentially, each thread iterates privately over each application,
 * consuming at most one task for each application. If a given application
 * has no ready tasks, the thread moves on to the next application, until
 * a task is available. This means that, in a system with no activity,
 * all consuming threads will continusously spin. In most deployments this
 * should be acceptable, since most schedulers should have continuous
 * if variable activity.
 */
class RoundRobinSystemScheduler implements SystemScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(RoundRobinSystemScheduler.
                                           class.getName()));

    // the thread-local iterator
    private ThreadLocal<Iterator<ApplicationScheduler>> schedulerIter =
        new ThreadLocal<Iterator<ApplicationScheduler>>() {
            protected Iterator<ApplicationScheduler> initialValue() {
                return appSchedulers.values().iterator();
            }
        };

    // the application schedulers
    private ConcurrentHashMap<KernelAppContext,ApplicationScheduler>
        appSchedulers;

    /**
     * Creates an instance of <code>RoundRobinSystemScheduler</code>.
     *
     * @param properties the application <code>Properties</code>
     */
    public RoundRobinSystemScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a Round Robin System Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        appSchedulers =
            new ConcurrentHashMap<KernelAppContext,ApplicationScheduler>();
    }

    /**
     * {@inheritDoc}
     */
    public void registerApplication(KernelAppContext context,
                                    Properties properties) {
        if (context == null)
            throw new NullPointerException("Context cannot be null");
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        if (appSchedulers.containsKey(context))
            throw new IllegalArgumentException("Context already registered");

        // try to get the app's scheduler, falling back on the default
        // if the specified one isn't available, or if none was specified
        ApplicationScheduler scheduler = LoaderUtil.getScheduler(properties);
        if (scheduler == null)
            scheduler = new FIFOApplicationScheduler(properties);

        appSchedulers.put(context, scheduler);
    }

    /**
     * {@inheritDoc}
     */
    public int getReadyCount(KernelAppContext context) {
        ApplicationScheduler scheduler = appSchedulers.get(context);
        if (scheduler == null)
            throw new IllegalArgumentException("Unknown context");
        return scheduler.getReadyCount();
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask() throws InterruptedException {
        Iterator<ApplicationScheduler> iter = schedulerIter.get();
        ScheduledTask task = null;

        // note that when there are no applications or tasks, this results
        // in threads spinning tightly
        while (task == null) {
            while (! iter.hasNext()) {
                if (Thread.interrupted())
                    throw new InterruptedException("Interrupt while spinning");
                iter = appSchedulers.values().iterator();
            }
            task = iter.next().getNextTask(false);
        }
        schedulerIter.set(iter);

        return task;
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        ApplicationScheduler scheduler =
            appSchedulers.get(task.getOwner().getContext());
        if (scheduler == null)
            throw new TaskRejectedException("Unknown context");
        return scheduler.reserveTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        ApplicationScheduler scheduler =
            appSchedulers.get(task.getOwner().getContext());
        if (scheduler == null)
            throw new TaskRejectedException("Unknown context");
        scheduler.addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        ApplicationScheduler scheduler =
            appSchedulers.get(task.getOwner().getContext());
        if (scheduler == null)
            throw new TaskRejectedException("Unknown context");
        return scheduler.addRecurringTask(task);
    }

    public void shutdown() {
        for (ApplicationScheduler scheduler : appSchedulers.values())
            scheduler.shutdown();
    }

}
