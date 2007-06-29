/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

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

    // the task executor used to run each task
    private final TaskExecutor taskExecutor;

    /**
     * Creates an instance of <code>MasterTaskConsumer</code>.
     *
     * @param masterScheduler the <code>MasterTaskScheduler</code> that
     *                        created this consumer
     * @param scheduler the <code>SystemScheduler</code> that provides tasks
     * @param taskExecutor the <code>TaskExecutor</code> used to execute tasks
     */
    MasterTaskConsumer(MasterTaskScheduler masterScheduler,
                       SystemScheduler scheduler,
                       TaskExecutor taskExecutor) {
        logger.log(Level.CONFIG, "Creating a new Master Task Consumer");

        this.masterScheduler = masterScheduler;
        this.scheduler = scheduler;
        this.taskExecutor = taskExecutor;
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
                taskExecutor.runTask(task, true);

                // if this is a recurring task, schedule the next run
                if (task.isRecurring())
                    task.getRecurringTaskHandle().scheduleNextRecurrence();
            }
        } catch (InterruptedException ie) {
            if (logger.isLoggable(Level.FINE))
                logger.logThrow(Level.FINE, ie, "Consumer thread finishing");
        } catch (Exception e) {
            // this should never happen, since we're always running the task
            // requesting re-try
            logger.logThrow(Level.SEVERE, e, "Consumer thread fatal error");
        } finally {
            masterScheduler.notifyThreadLeaving();
        }
    }

}
