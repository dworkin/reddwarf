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
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerRetryAction;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple retry "in place" {@code SchedulerRetryPolicy}.  This
 * {@code SchedulerRetryPolicy} always causes a task that throws a
 * retryable exception to retry immediately.  This class supports the following
 * configuration properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #RETRY_WARNING_THRESHOLD_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_RETRY_WARNING_THRESHOLD}
 *
 * <dd style="padding-top: .5em">If a task has been retried a multiple of
 *      times equal to the value of this property, then a {@code WARNING}
 *      message will be logged as feedback to the user.  This value must be
 *      greater than or equal to {@code 1}.
 *
 * </dl> <p>
 */
public class ImmediateRetryPolicy implements SchedulerRetryPolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ImmediateRetryPolicy.
                                           class.getName()));

    /**
     * The property used to define the retry count threshold to use before
     * printing a WARNING message.
     */
    static final String RETRY_WARNING_THRESHOLD_PROPERTY =
            "com.sun.sgs.impl.kernel.schedule.retry.warning.threshold";

    /**
     * The default retry warning threshold
     */
    static final int DEFAULT_RETRY_WARNING_THRESHOLD = 25;

    // the task retry count at which a warning should be printed
    private final int retryWarningThreshold;

    /**
     * Constructs an {@code ImmediateRetryPolicy}.
     *
     * @param properties the system properties available
     */
    public ImmediateRetryPolicy(Properties properties) {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        this.retryWarningThreshold = wrappedProps.getIntProperty(
                RETRY_WARNING_THRESHOLD_PROPERTY,
                DEFAULT_RETRY_WARNING_THRESHOLD,
                1, Integer.MAX_VALUE);

        logger.log(Level.CONFIG,
                   "Created ImmediateRetryPolicy with properties:" +
                   "\n  " + RETRY_WARNING_THRESHOLD_PROPERTY + "=" +
                   retryWarningThreshold);
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

            // Print a WARNING message if a task's retry count is a
            // multiple of a configurable threshold
            if (task.getTryCount() % retryWarningThreshold == 0) {
                logger.logThrow(Level.WARNING,
                                task.getLastFailure(),
                                "Task has been retried {0} times: {1}",
                                task.getTryCount(), task);
            }

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
