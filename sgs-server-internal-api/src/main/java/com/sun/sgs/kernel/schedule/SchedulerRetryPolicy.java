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
 */
public interface SchedulerRetryPolicy {

    /**
     * Determines how a task should be retried after a failure.  This
     * method should be called by a scheduler if a task aborts with a
     * retryable exception.
     * <p>
     * A return value of {@code true} means that the caller has been relieved of
     * responsibility of executing the task and the task has been handed off
     * to be run by another usable thread.  A return value of false means that
     * the task has not been handed off, and the caller is still responsible
     * for executing it.
     * <p>
     * A task may be handed off by being placed on either the given
     * {@code backingQueue} or the given {@code throttleQueue} but never both.
     * It may also be handed off by some other means specific to the retry
     * policy implementation.
     *
     * @param task the task that has been aborted
     * @param result the {@code Throwable} that was thrown by this task and
     *               caused it to fail
     * @param backingQueue the {@code SchedulerQueue} that backs the standard
     *                     thread pool used by the scheduler to execute tasks
     * @param throttleQueue the {@code SchedulerQueue} that backs a special
     *                      single threaded consumer used by the scheduler
     *                      to execute tasks free of concurrency conflicts
     * @return
     */
    boolean handoffRetry(ScheduledTask task,
                         Throwable result,
                         SchedulerQueue backingQueue,
                         SchedulerQueue throttleQueue);

}
