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
     * <li>A return value of {@link SchedulerRetryAction#HANDOFF} means that the
     * the task should be handed off by the caller to be run by another
     * usable thread.  The caller may choose to run the task itself if
     * resource limitations require it.</li>
     * <li>A return value of {@link SchedulerRetryAction#RETRY} means that
     * the task should not be handed off, and the caller is responsible for
     * for executing it immediately.</li>
     * </ul>
     * Note: This method may modify the given {@code task} in order to affect
     * how the scheduler behaves when re-executing it.
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
