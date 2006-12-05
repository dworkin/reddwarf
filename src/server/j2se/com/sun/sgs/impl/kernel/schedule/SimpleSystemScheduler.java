
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a very simple implementation of <code>SystemScheduler</code> that
 * does nothing more intelligent than simply iterating through its
 * <code>ApplicationScheduler</code>s for the next task. This is provided
 * for initial testing purposes.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class SimpleSystemScheduler implements SystemScheduler, ProfilingConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SimpleSystemScheduler.
                                           class.getName()));

    // the application schedulers
    private ConcurrentHashMap<KernelAppContext,ApplicationScheduler>
        appSchedulers;

    // the ready queue of tasks
    private LinkedBlockingQueue<ScheduledTask> queue;

    /**
     * Creates a new instance of <code>SimpleSystemScheduler</code>.
     */
    public SimpleSystemScheduler(Properties properties) {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Creating a Simple System Scheduler");

        appSchedulers =
            new ConcurrentHashMap<KernelAppContext,ApplicationScheduler>();
        queue = new LinkedBlockingQueue<ScheduledTask>();
    } 

    /**
     * {@inheritDoc}
     */
    public void registerApplication(KernelAppContext context) {
        // TEST: for now we'll only have one kind of scheduler, but when
        // we start profiling work, this will become a configuration issue
        appSchedulers.put(context, new FIFOApplicationScheduler(this));
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask() throws InterruptedException {
        return queue.take();
    }

    /**
     * {@inheritDoc}
     */
    public void giveReadyTask(ScheduledTask task) throws InterruptedException {
        queue.put(task);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        return appSchedulers.get(task.getOwner().getContext()).
            reserveTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addReadyTask(ScheduledTask task) {
        appSchedulers.get(task.getOwner().getContext()).addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addFutureTask(ScheduledTask task) {
        appSchedulers.get(task.getOwner().getContext()).addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        return appSchedulers.get(task.getOwner().getContext()).
            addRecurringTask(task);
    }

}
