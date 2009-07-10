/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
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
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.schedule.SchedulerRetryAction;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@code SchedulerRetryPolicy} always causes a task that throws a
 * retryable exception to
 * retry immediately unless the task was interrupted in which case it
 * is put back onto the scheduler's standard backing queue.
 */
public class ImmediateRetryPolicy implements SchedulerRetryPolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ExperimentalRetryPolicy.
                                           class.getName()));

    /**
     * Constructs an {@code ImmediateRetryPolicy}
     *
     * @param properties the system properties available
     */
    public ImmediateRetryPolicy(Properties properties) {

    }

    /** {@inheritDoc} */
    public SchedulerRetryAction getRetryAction(ScheduledTask task,
                                               Throwable result,
                                               SchedulerQueue backingQueue,
                                               SchedulerQueue throttleQueue) {
        // An interrupted task must be either handed off or dropped
        if (result instanceof InterruptedException) {
            try {
                backingQueue.addTask(task);
                return SchedulerRetryAction.HANDOFF;
            } catch (TaskRejectedException tre) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.logThrow(Level.WARNING, result,
                                    "dropping an interrupted task: {0}", task);
                }
                return SchedulerRetryAction.DROP;
            }
        }

        // NOTE: as a first-pass implementation this simply instructs the
        // caller to try again if retry is requested, but other strategies
        // (like the number of times re-tried) might be considered later
        if ((result instanceof ExceptionRetryStatus) &&
            (((ExceptionRetryStatus) result).shouldRetry())) {

            // NOTE: this is a very simple initial policy that always causes
            // tasks to re-try "in place"
            return SchedulerRetryAction.RETRY;
            
        } else {

            // we're not re-trying the task, so log that it's being dropped
            if (logger.isLoggable(Level.WARNING)) {
                if (task.isRecurring()) {
                    logger.logThrow(Level.WARNING, result, 
                                    "skipping a recurrence of a task that " +
                                    "failed with a non-retryable " +
                                    "exception: {0}", task);
                } else {
                    logger.logThrow(Level.WARNING, result, 
                                    "dropping a task that failed with a " +
                                    "non-retryable exception: {0}", task);
                }
            }

            return SchedulerRetryAction.DROP;
        }
    }

}
