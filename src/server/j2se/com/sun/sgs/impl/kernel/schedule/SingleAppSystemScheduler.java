
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.HashSet;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a very simple implementation of <code>SystemScheduler</code> that
 * is backed by a single <code>ApplicationScheduler</code>. All tasks are
 * ordered through that scheduler. The common use case is when there is only
 * a single application being supported, but in practice this may also be
 * useful for supporting multiple applications, depending on the task
 * characteristics of those applications.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class SingleAppSystemScheduler implements SystemScheduler, ProfilingConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SingleAppSystemScheduler.
                                           class.getName()));

    // the single application scheduler used to handle all tasks
    private ApplicationScheduler appScheduler;

    // the set of registered contexts
    private HashSet<KernelAppContext> registeredContexts;

    /**
     * Creates a new instance of <code>SingleAppSystemScheduler</code>.
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

        registeredContexts = new HashSet<KernelAppContext>();
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

}
