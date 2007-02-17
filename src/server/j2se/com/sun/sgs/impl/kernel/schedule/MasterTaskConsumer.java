
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.ExceptionRetryStatus;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.impl.kernel.profile.ProfilingCollector;

import com.sun.sgs.impl.util.LoggerWrapper;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This <code>Runnable</code> is used by the <code>MasterTaskScheduler</code>
 * as the long-running task that consumes scheduled tasks as they are ready.
 */
class MasterTaskConsumer implements Runnable {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(MasterTaskConsumer.
                                           class.getName()));

    // the master scheduler that created this consumer
    private final MasterTaskScheduler masterScheduler;

    // the system scheduler that provides tasks
    private final SystemScheduler scheduler;

    // the collector used to report tasks for profiling
    private final ProfilingCollector profilingCollector;

    // the task handler used to run each task
    private final TaskHandler taskHandler;

    /**
     * Creates an instance of <code>MasterTaskConsumer</code>.
     *
     * @param masterScheduler the <code>MasterTaskScheduler</code> that
     *                        created this consumer
     * @param scheduler the <code>SystemScheduler</code> that provides tasks
     * @param profilingCollector the <code>ProfilingCollector</code> that
     *                           is used to report tasks, or <code>null</code>
     *                           if tasks should not be reported
     * @param taskHandler the system's <code>TaskHandler</code>
     */
    MasterTaskConsumer(MasterTaskScheduler masterScheduler,
                       SystemScheduler scheduler,
                       ProfilingCollector profilingCollector,
                       TaskHandler taskHandler) {
        logger.log(Level.CONFIG, "Creating a new Master Task Consumer");

        this.masterScheduler = masterScheduler;
        this.scheduler = scheduler;
        this.profilingCollector = profilingCollector;
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
                int tryCount = 1;

                // run the task to completion
                while (! taskFinished) {
                    try {
                        if (profilingCollector != null)
                            profilingCollector.startTask(task.getTask(),
                                                         task.getOwner(),
                                                         task.getStartTime());
                        taskHandler.runTaskAsOwner(task.getTask(),
                                                   task.getOwner());
                        if (profilingCollector != null)
                            profilingCollector.finishTask(tryCount, true);
                        taskFinished = true;
                    } catch (Exception e) {
                        if (profilingCollector != null)
                            profilingCollector.finishTask(tryCount++, false);

                        if ((e instanceof ExceptionRetryStatus) &&
                            (((ExceptionRetryStatus)e).shouldRetry())) {
                            // NOTE: we're not doing anything fancy here to
                            // guess about re-scheduling, adding right to the
                            // ready queue, etc...this is just re-running in
                            // place until the task is done, but this is one
                            // of the first issues that will be investigated
                            // for optimization
                        } else {
                            if (logger.isLoggable(Level.WARNING)) {
                                if (task.isRecurring()) {
                                    logger.logThrow(Level.WARNING, e,
                                                    "skipping a recurrence " +
                                                    "of a task that failed " +
                                                    "with a non-retryable " +
                                                    "exception: {0}", task);
                                } else {
                                    logger.logThrow(Level.WARNING, e,
                                                    "dropping a task that " +
                                                    "failed with a non-" +
                                                    "retryable exception: {0}",
                                                    task);
                                }
                            }
                            taskFinished = true;
                        }
                    }
                }

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
