
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
    private static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SimpleSystemScheduler.
                                           class.getName()));

    // the application schedulers
    private ConcurrentHashMap<KernelAppContext,ApplicationScheduler>
        appSchedulers;

    // TEST: this is the app scheduler for system tasks...we don't have the
    // system identity yet, so we can't put this in the map, but when the
    // identity piece is ready, this will just be another element of the map
    private ApplicationScheduler systemAppScheduler;

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

        // TEST: see comment above about why this will be removed
        systemAppScheduler = new FIFOApplicationScheduler(this);
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
    public ScheduledTask getNextTask() {
        while (true) {
            try {
                return queue.take();
            } catch (InterruptedException ie) {}
        }
    }

    /**
     * {@inheritDoc}
     */
    public void giveReadyTask(ScheduledTask task) {
        boolean given = false;
        while (! given) {
            try {
                queue.put(task);
                given = true;
            } catch (InterruptedException ie) {}
        }
    }

    /**
     * TEST: This is a private helper used to look up the appropriate
     * scheduler for the given identity. When we have the system identity,
     * then everything will be in the map and a simple map lookup can
     * replace this method
     */
    private ApplicationScheduler getScheduler(ScheduledTask task) {
        TaskOwner owner = task.getOwner();
        if (owner == null)
            return systemAppScheduler;
        return appSchedulers.get(owner.getContext());
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        return getScheduler(task).reserveTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addReadyTask(ScheduledTask task) {
        getScheduler(task).addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addFutureTask(ScheduledTask task) {
        getScheduler(task).addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        return getScheduler(task).addRecurringTask(task);
    }

}
