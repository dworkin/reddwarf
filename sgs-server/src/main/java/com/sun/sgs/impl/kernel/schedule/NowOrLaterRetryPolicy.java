/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerRetryAction;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@code SchedulerRetryPolicy} that works in a similar fashion as the
 * {@link ImmediateRetryPolicy} except that it also loosens the transaction
 * restrictions after a certain number of task retries in order to push
 * through tasks that are frequently timing out.  This
 * {@code SchedulerRetryPolicy} causes a task that throws a
 * retryable exception to retry immediately.  However, once a task has been
 * retried more than the configurable threshold (see below), then the
 * transaction timeout for the task is doubled, and the task is scheduled to be
 * retried later (rather than immediately).  This class supports the following
 * configuration properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #RETRY_BACKOFF_THRESHOLD_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_RETRY_BACKOFF_THRESHOLD}
 *
 * <dd style="padding-top: .5em">If a task has been retried more than this
 *      number of times, then the task's transaction restrictions are loosened
 *      the next time it runs and the task is scheduled to be retried later.
 *      This value must be greater than or equal to {@code 1}.
 *
 * </dl> <p>
 */
public class NowOrLaterRetryPolicy implements SchedulerRetryPolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(NowOrLaterRetryPolicy.
                                           class.getName()));

    /**
     * The property used to define the retry count threshold to use before
     * loosening the transaction requirements and scheduling a task to be
     * retried later.
     */
    static final String RETRY_BACKOFF_THRESHOLD_PROPERTY =
            "com.sun.sgs.impl.kernel.schedule.retry.backoff.threshold";

    /**
     * The default retry backoff threshold.
     */
    static final int DEFAULT_RETRY_BACKOFF_THRESHOLD = 10;

    // the task retry count at which a backoff should occur
    private final int retryBackoffThreshold;

    /**
     * Constructs a {@code NowOrLaterRetryPolicy}.
     *
     * @param properties the system properties available
     */
    public NowOrLaterRetryPolicy(Properties properties) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        this.retryBackoffThreshold = wrappedProps.getIntProperty(
                RETRY_BACKOFF_THRESHOLD_PROPERTY,
                DEFAULT_RETRY_BACKOFF_THRESHOLD,
                1, Integer.MAX_VALUE);

        logger.log(Level.CONFIG,
                   "Created NowOrLaterRetryPolicy with properties:" +
                   "\n  " + RETRY_BACKOFF_THRESHOLD_PROPERTY + "=" +
                   retryBackoffThreshold);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation normally returns a value of
     * {@link SchedulerRetryAction#RETRY_NOW} for any task that has most
     * recently failed with a retryable exception.  However, if the given task
     * has been retried more than the backoff threshold, then the task's
     * transaction timeout is doubled, and this method returns a value of
     * {@link SchedulerRetryAction#RETRY_LATER}.
     */
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

            // Always retry in place unless we are above the backoff threshold
            // Also note, once a task is retried more than the backoff
            // threshold, each subsequent retry will trigger this condition
            if (task.getTryCount() > retryBackoffThreshold) {
                if (result instanceof TransactionTimeoutException &&
                    task.getTimeout() * 2L < (long) Integer.MAX_VALUE) {
                    // double the timeout, and reschedule for RETRY_LATER
                    // if the timeout has not exceeded max int
                    logger.logThrow(Level.WARNING,
                                    task.getLastFailure(),
                                    "Task has been retried {0} times: {1}\n" +
                                    "Increasing its timeout to {2} ms and " +
                                    "scheduling its retry for later",
                                    task.getTryCount(), task, task.getTimeout() * 2);
                    task.setTimeout(task.getTimeout() * 2);
                } else {
                    // just schedule to retry later if the task failure is
                    // not due to transaction timeout
                    logger.logThrow(Level.WARNING,
                                    task.getLastFailure(),
                                    "Task has been retried {0} times: {1}\n" +
                                    "scheduling its retry for later",
                                    task.getTryCount(), task);
                }
                return SchedulerRetryAction.RETRY_LATER;
            } else {
                return SchedulerRetryAction.RETRY_NOW;
            }
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
