
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

    // the system scheduler that provides tasks
    private final SystemScheduler scheduler;

    // the task handler used to run each task
    private final TaskHandler taskHandler;

    /**
     * Creates an instance of <code>MasterTaskConsumer</code>.
     *
     * @param scheduler the <code>SystemScheduler</code> that provides tasks
     * @param taskHandler the system's <code>TaskHandler</code>
     */
    MasterTaskConsumer(SystemScheduler scheduler, TaskHandler taskHandler) {
        logger.log(Level.CONFIG, "Creating a new Master Task Consumer");

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

        while (true) {
            // wait for the next task...if we get interrupted while waiting,
            // then we'll loop back and try again
            // NOTE: when we actually add shutdown and other management
            // facilities, we will probably want to interpret interruption
            // different, perhaps even returning from this thread
            ScheduledTask task = null;
            boolean taskFinished = false;
            try {
                task = scheduler.getNextTask();
            } catch (InterruptedException ie) {
                return;
            }

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
                        if (logger.isLoggable(Level.FINE))
                            logger.logThrow(Level.FINE, e,
                                            "dropping a failed task that " +
                                            "did not ask to be re-tried: {0}",
                                            task);
                        taskFinished = true;
                    }
                }
            }

            // if this is a recurring task, we can now schedule the next run
            if ((task != null) && (task.isRecurring())) {
                long newStartTime = task.getStartTime() + task.getPeriod();
                scheduler.addFutureTask(new ScheduledTask(task, newStartTime));
            }
        }
    }

}
