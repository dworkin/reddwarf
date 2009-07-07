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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.kernel.schedule;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;


/**
 * This interface represents a single task that has been accepted by a
 * scheduler. It is used by implementations of {@code SchedulerQueue}
 * to manage the tasks in the queue.
 */
public interface ScheduledTask {

    /** Identifier that represents a non-recurring task. */
    int NON_RECURRING = -1;

    /**
     * Returns the task.
     *
     * @return the {@code KernelRunnable} to run
     */
    KernelRunnable getTask();

    /**
     * Returns the owner.
     *
     * @return the {@code Identity} that owns the task
     */
    Identity getOwner();

    /**
     * Returns the priority.
     *
     * @return the {@code Priority}
     */
    Priority getPriority();

    /**
     * Returns the time at which this task is scheduled to start.
     *
     * @return the scheduled run time for the task
     */
    long getStartTime();

    /**
     * Returns the period for the task if it's recurring, or
     * {@code NON_RECURRING} if this is not a recurring task.
     *
     * @return the period between recurring executions.
     */
    long getPeriod();

    /**
     * Returns the try count (the number of times that this task has been
     * attempted).
     */
    int getTryCount();

    /**
     * Returns {@code null} if the task completed successfully, or the
     * {@code Throwable} that caused the task to fail permanently. If the
     * task has not yet completed then this will block until the result
     * is known or the caller is interrupted. An {@code InterruptedException}
     * is thrown either if the calling thread is interrupted before a result
     * is known or if the task is cancelled meaning that no result is known.
     */
    Throwable get() throws InterruptedException;

    /**
     * Returns the transaction timeout.  A Scheduler may or may not choose
     * to use this timeout value.
     *
     * @return the transaction timeout to use for this task.
     */
    long getTimeout();

    /**
     * Sets the transaction timeout for this task.
     */
    void setTimeout(long timeout);

    /**
     * Returns whether this is a recurring task. If this is not a recurring
     * task then {@code getPeriod} should always return {@code NON_RECURRING}.
     *
     * @return {@code true} if this task is a recurring task,
     *         {@code false} otherwise.
     */
    boolean isRecurring();

    /**
     * Returns the {@code RecurringTaskHandle} associated with this task if
     * this task is recurring, or {@code null} if this is not recurring.
     *
     * @return the associated {@code RecurringTaskHandle} or {@code null}
     */
    RecurringTaskHandle getRecurringTaskHandle();

    /**
     * Returns whether this task has been cancelled.
     *
     * @return {@code true} if this {@code ScheduledTask} has been cancelled,
     *         {@code false} otherwise
     */
    boolean isCancelled();

    /**
     * Cancel this {@code ScheduledTask}. Note that if the task is already
     * running then calling this method may not have any affect.
     *
     * @param allowInterrupt {@code true} if this call is allowed to throw
     *                       {@code InterruptedException} if the calling
     *                       thread is interrupted, {@code false} if the
     *                       call should always block until the task is
     *                       cancelled or has completed
     *
     * @return {@code true} if the task was cancelled, {@code false} if
     *         the task was already cancelled or has completed
     *
     * @throws InterruptedException if the caller is interrupted while
     *                              waiting to learn if the cancellation
     *                              succeeded and {@code allowInterrupt}
     *                              is {@code true}
     */
    boolean cancel(boolean allowInterrupt) throws InterruptedException;

}
