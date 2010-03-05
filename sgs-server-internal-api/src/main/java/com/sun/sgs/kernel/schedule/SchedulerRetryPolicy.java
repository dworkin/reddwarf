/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
