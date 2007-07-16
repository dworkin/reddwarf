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

import com.sun.sgs.app.ExceptionRetryStatus;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.ProfileCollector;

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
    private final ProfileCollector profileCollector;

    // the task handler used to run each task
    private final TaskHandler taskHandler;

    /**
     * Creates an instance of <code>MasterTaskConsumer</code>.
     *
     * @param masterScheduler the <code>MasterTaskScheduler</code> that
     *                        created this consumer
     * @param scheduler the <code>SystemScheduler</code> that provides tasks
     * @param profileCollector the <code>ProfileCollector</code> that
     *                         is used to report tasks, or <code>null</code>
     *                         if tasks should not be reported
     * @param taskHandler the system's <code>TaskHandler</code>
     */
    MasterTaskConsumer(MasterTaskScheduler masterScheduler,
                       SystemScheduler scheduler,
                       ProfileCollector profileCollector,
                       TaskHandler taskHandler) {
        logger.log(Level.CONFIG, "Creating a new Master Task Consumer");

        this.masterScheduler = masterScheduler;
        this.scheduler = scheduler;
        this.profileCollector = profileCollector;
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
                        if (profileCollector != null)
                            profileCollector.
                                startTask(task.getTask(), task.getOwner(),
                                          task.getStartTime(),
                                          scheduler.
                                          getReadyCount(task.getOwner().
                                                        getContext()));
                        taskHandler.runTaskAsOwner(task.getTask(),
                                                   task.getOwner());
                        if (profileCollector != null)
                            profileCollector.finishTask(tryCount, true);
                        taskFinished = true;
                    } catch (Exception e) {
                        if (profileCollector != null)
                            profileCollector.finishTask(tryCount++, false);

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
