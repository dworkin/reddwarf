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
 * A {@code SchedulerRetryPolicy} that always causes a task that throws a
 * retryable exception to retry later.
 */
public class RequeueRetryPolicy implements SchedulerRetryPolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(RequeueRetryPolicy.
                                           class.getName()));

    /**
     * Constructs a {@code ReqeueRetryPolicy}.
     *
     * @param properties the system properties available
     */
    public RequeueRetryPolicy(Properties properties) {
        logger.log(Level.INFO, "Creating RequeueRetryPolicy");
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

        if ((result instanceof ExceptionRetryStatus) &&
            (((ExceptionRetryStatus) result).shouldRetry())) {

            // NOTE: this is a very simple initial policy that always causes
            // tasks to re-try later
            return SchedulerRetryAction.RETRY_LATER;

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
