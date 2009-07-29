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
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerRetryAction;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple retry "in place" {@code SchedulerRetryPolicy}.  This
 * {@code SchedulerRetryPolicy} always causes a task that throws a
 * retryable exception to retry immediately.
 */
public class ImmediateRetryPolicy implements SchedulerRetryPolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ImmediateRetryPolicy.
                                           class.getName()));

    /**
     * Constructs an {@code ImmediateRetryPolicy}.
     *
     * @param properties the system properties available
     */
    public ImmediateRetryPolicy(Properties properties) {

    }

    /** {@inheritDoc} */
    public SchedulerRetryAction getRetryAction(ScheduledTask task) {
        // null task is not allowed
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }

        // result cannot be null
        Throwable result = task.getLastFailure();
        if (result == null) {
            throw new IllegalStateException("task's last failure " +
                                            "cannot be null");
        }

        // NOTE: as a first-pass implementation this simply instructs the
        // caller to try again if retry is requested, but other strategies
        // (like the number of times re-tried) might be considered later
        if ((result instanceof ExceptionRetryStatus) &&
            (((ExceptionRetryStatus) result).shouldRetry())) {

            // NOTE: this is a very simple initial policy that always causes
            // tasks to re-try "in place"
            return SchedulerRetryAction.RETRY_NOW;
            
        } else {

            // we're not re-trying the task, so specify reason for dropping it
            if (logger.isLoggable(Level.FINE)) {
                if (task.isRecurring()) {
                    logger.log(Level.FINE,
                               "skipping a recurrence of a task because it " +
                               "failed with a non-retryable exception: {0}",
                               task);
                } else {
                    logger.log(Level.FINE,
                               "dropping a task because it failed with a " +
                               "non-retryable exception: {0}", task);
                }
            }

            return SchedulerRetryAction.DROP;
        }
    }

}
