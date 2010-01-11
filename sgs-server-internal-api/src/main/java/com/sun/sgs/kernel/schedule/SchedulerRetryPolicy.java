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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.kernel.schedule;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TaskManager;

/**
 * This interface is used to define a retry policy for a scheduler when a
 * transactional task fails.
 * <p>
 * All implementations must implement a constructor of the form
 * ({@code java.util.Properties}).
 */
public interface SchedulerRetryPolicy {

    /**
     * Determines if and how a task should be retried after a failure.  This
     * method should be called by a scheduler if a task aborts.
     * <ul>
     * <li>A return value of {@link SchedulerRetryAction#DROP} means that the
     * task should be dropped and not retried.</li>
     * <li>A return value of {@link SchedulerRetryAction#RETRY_LATER} means that
     * the task should be retried by the scheduler at some point in the
     * future.</li>
     * <li>A return value of {@link SchedulerRetryAction#RETRY_NOW} means that
     * the task should be retried immediately.</li>
     * </ul>
     * This method may modify the given {@code task} in order to affect
     * how the scheduler behaves when re-executing.
     * <p>
     * Note: The {@link Throwable} cause of the most recent failure of the given
     * {@code task} should always be accessible via the
     * {@link ScheduledTask#getLastFailure()} method.  This {@code Throwable}
     * <em>may</em> implement {@link ExceptionRetryStatus}.  If it does, care
     * should be taken to abide by the contract specified by the
     * {@link TaskManager} with regard to {@code Throwable}s that implement
     * this interface.
     * <p>
     * Note: The proper way to drop a task with a custom policy is to return
     * {@code DROP} with this method.  Implementations <em>should not</em>
     * use a call to {@link ScheduledTask#cancel(boolean) cancel} on the
     * given task.
     *
     * @param task the task that has been aborted
     * @return the {@code SchedulerRetryAction} that the scheduler should
     *         take with respect to retrying the given task
     * @throws IllegalArgumentException if {@code task} is {@code null}
     * @throws IllegalStateException if the given {@code task}'s most recent
     *                               failure is {@code null}
     */
    SchedulerRetryAction getRetryAction(ScheduledTask task);

}
