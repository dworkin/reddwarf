
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.ExceptionRetryStatus;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.impl.util.LoggerWrapper;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This <code>Runnable</code> is used by the <code>MasterTaskScheduler</code>
 * as the long-running task that consumes scheduled tasks as they are ready.
 * <p>
 * NOTE: As with most classes in the <code>schedule</code> package, this is
 * currently extremely simple. Once profiling work starts in earnest, this
 * class will be updated.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class MasterTaskConsumer implements Runnable {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(MasterTaskConsumer.
                                           class.getName()));

    // the master scheduler that created this consumer
    private MasterTaskScheduler masterScheduler;

    // the system scheduler that provides tasks
    private final SystemScheduler scheduler;

    // the task handler used to run each task
    private final TaskHandler taskHandler;

    /**
     * Creates an instance of <code>MasterTaskConsumer</code>.
     *
     * @param masterScheduler the <code>MasterTaskScheduler</code> that
     *                        created this consumer
     * @param scheduler the <code>SystemScheduler</code> that provides tasks
     * @param taskHandler the system's <code>TaskHandler</code>
     */
    MasterTaskConsumer(MasterTaskScheduler masterScheduler,
                       SystemScheduler scheduler, TaskHandler taskHandler) {
        logger.log(Level.CONFIG, "Creating a new Master Task Consumer");

        this.masterScheduler = masterScheduler;
        this.scheduler = scheduler;
        this.taskHandler = taskHandler;
    }

    /**
     * Runs this <code>Runnable</code>, dropping into an infinite loop which
     * consumes and runs tasks, handling re-try and recurring cases as
     * appropriate.
     */
    public void run() {
        logger.log(Level.FINE, "Starting a Master Task Consumer");

        try {
            while (true) {
                // wait for the next task, which is the only point at which
                // we might get interrupted, which in turn ends execution
                ScheduledTask task = scheduler.getNextTask();
                boolean taskFinished = false;

                // run the task to completion
                while (! taskFinished) {
                    try {
                        taskHandler.runTaskAsOwner(task.getTask(),
                                                   task.getOwner());
                        taskFinished = true;
                    } catch (Exception e) {
                        if ((e instanceof ExceptionRetryStatus) &&
                            (((ExceptionRetryStatus)e).shouldRetry())) {
                            // NOTE: we're not doing anything fancy here to
                            // guess about re-scheduling, adding right to the
                            // ready queue, etc...this is just re-running in
                            // place until the task is done, but this is one
                            // of the first issues that will be investigated
                            // for optimization
                        } else {
                            if (logger.isLoggable(Level.WARNING))
                                logger.logThrow(Level.WARNING, e, "dropping" +
                                                " a failed task that did " +
                                                "not ask to be re-tried: {0}",
                                                task);
                            taskFinished = true;
                        }
                    }
                }

                // NOTE: when we add reporting, at this point we want to
                // report the task time, how many times it was tried, and
                // whether it was dropped

                // if this is a recurring task, schedule the next run
                if (task.isRecurring())
                    task.getRecurringTaskHandle().scheduleNextRecurrence();
            }
        } catch (InterruptedException ie) {
            if (logger.isLoggable(Level.FINE))
                logger.logThrow(Level.FINE, ie, "Consumer thread finishing");
        } finally {
            masterScheduler.notifyThreadLeaving();
        }
    }

}
