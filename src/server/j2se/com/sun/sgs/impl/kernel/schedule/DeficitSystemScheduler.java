
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>SystemScheduler</code> does Round-Robin
 * scheduling, but does so using deficit-balanced batches of tasks.
 * Essentially, a local queue of tasks is consumed, and when it empties
 * one consumer thread will fetch some number of tasks from each of the
 * applications and put them, maintaining order, into the local queue while
 * all the other consumers spin waiting for tasks to become available.
 * <p>
 * The number of tasks fetched from each applications varies based on the
 * application's deficit. The goal is to keep the number of tasks per
 * application balanced in aggregate, but to allow applications to exceed
 * or fall behind the average on occasion. Note that this class assumes
 * (roughly) equal cost for all tasks.
 */
class DeficitSystemScheduler implements SystemScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(DeficitSystemScheduler.
                                           class.getName()));

    // NOTE: This should (probably) be based on the number of threads,
    // but as a starting point it just uses David's constant
    private static final int TASKS_PER_ROUND = 120;

    // the average, defined as TASKS_PER_ROUND / number-of-applications
    private int tasksPerApp;

    // the application schedulers
    private ConcurrentHashMap<KernelAppContext,ApplicationScheduler>
        appSchedulers;

    // the deficit values for each application
    private HashMap<KernelAppContext,Integer> appDeficits;

    // the local queue from which threads consume tasks
    private ConcurrentLinkedQueue<ScheduledTask> currentRoundQueue;

    // a flag that ensures that only one thread will re-fill the queue
    private AtomicBoolean isBeingFilled;

    /**
     * Creates an instance of <code>DeficitSystemScheduler</code>.
     *
     * @param properties the application <code>Properties</code>
     */
    public DeficitSystemScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a Deficit System Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        appSchedulers =
            new ConcurrentHashMap<KernelAppContext,ApplicationScheduler>();
        appDeficits = new HashMap<KernelAppContext,Integer>();

        currentRoundQueue = new ConcurrentLinkedQueue<ScheduledTask>();
        isBeingFilled = new AtomicBoolean(false);
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
        tasksPerApp = TASKS_PER_ROUND / appSchedulers.size();
        appDeficits.put(context, tasksPerApp);
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask() throws InterruptedException {
        // see if we can get a task, in which case we're done
        ScheduledTask task = currentRoundQueue.poll();
        while (task == null) {
            // no tasks are available, so see if anyone is already trying
            // to re-fill the queue
            if (isBeingFilled.compareAndSet(false, true)) {
                try {
                    // keep a count of how many tasks we fetch, and iterate
                    // through the applications, re-calculating each deficit
                    // based on the tasks fetched against the previous deficit
                    // and allocation per-round
                    int filled = 0;
                    for (Entry<KernelAppContext,ApplicationScheduler>
                             entry : appSchedulers.entrySet()) {
                        int deficit =
                            Math.min(appDeficits.get(entry.getKey()),
                                     TASKS_PER_ROUND);
                        int fetched =
                            entry.getValue().getNextTasks(currentRoundQueue,
                                                          deficit);
                        filled += fetched;
                        appDeficits.put(entry.getKey(),
                                        (deficit - fetched) + tasksPerApp);
                    }

                    // NOTE: this is here mostly to see if the constant value
                    // needs to be changed, or needs to be dynamic, by looking
                    // to see if we're not getting enough tasks
                    if (logger.isLoggable(Level.CONFIG)) {
                        if ((filled > 0) && (currentRoundQueue.size() == 0)) {
                            logger.log(Level.CONFIG, "Deficit Rounds are " +
                                       "not keeping up; increase size!");
                        }
                    }
                } finally {
                    isBeingFilled.set(false);
                }

                task = currentRoundQueue.poll();
            }
        }
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

}
