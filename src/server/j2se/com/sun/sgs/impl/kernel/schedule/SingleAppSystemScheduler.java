/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Properties;

import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a very simple implementation of <code>SystemScheduler</code> that
 * is backed by a single <code>ApplicationScheduler</code>. All tasks are
 * ordered through that scheduler. The common use case is when there is only
 * a single application being supported, but in practice this may also be
 * useful for supporting multiple applications, depending on the task
 * characteristics of those applications.
 */
class SingleAppSystemScheduler implements SystemScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SingleAppSystemScheduler.
                                           class.getName()));

    // the single application scheduler used to handle all tasks
    private ApplicationScheduler appScheduler;

    // the set of registered contexts
    // FIXME: use a concurrent set when we move to 1.6
    private ConcurrentLinkedQueue<KernelAppContext> registeredContexts;

    /**
     * Creates a new instance of <code>SingleAppSystemScheduler</code>.
     *
     * @param properties the <code>Properties</code> of the system
     */
    public SingleAppSystemScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a Single App System Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        // try to get the single scheduler, falling back on the default
        // if the specified one isn't available, or if none was specified
        appScheduler = LoaderUtil.getScheduler(properties);
        if (appScheduler == null)
            appScheduler = new FIFOApplicationScheduler(properties);

        registeredContexts = new ConcurrentLinkedQueue<KernelAppContext>();
    } 

    /**
     * {@inheritDoc}
     */
    public synchronized void registerApplication(KernelAppContext context,
                                                 Properties properties) {
        if (context == null)
            throw new NullPointerException("Context cannot be null");
        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        // this scheduler lumps all applications into the same queue, so
        // there's no need to keep track of any specific context except
        // to make sure that nothing is re-registered
        if (registeredContexts.contains(context))
            throw new IllegalArgumentException("Context already registered");
        registeredContexts.add(context);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that, because all applications are lumped into the same
     * scheduler, this actually reports the same count for any context.
     */
    public int getReadyCount(KernelAppContext context) {
        if (! registeredContexts.contains(context))
            throw new IllegalArgumentException("Unknown context");
        return appScheduler.getReadyCount();
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask() throws InterruptedException {
        return appScheduler.getNextTask(true);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        if (! registeredContexts.contains(task.getOwner().getContext()))
            throw new TaskRejectedException("Unknown context");
        return appScheduler.reserveTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        if (! registeredContexts.contains(task.getOwner().getContext()))
            throw new TaskRejectedException("Unknown context");
        appScheduler.addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        if (! registeredContexts.contains(task.getOwner().getContext()))
            throw new TaskRejectedException("Unknown context");
        return appScheduler.addRecurringTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        appScheduler.shutdown();
    }

}
