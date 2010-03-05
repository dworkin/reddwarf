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

package com.sun.sgs.service;

import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.app.TransactionException;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * This <code>Service</code> provides facilities for scheduling tasks to
 * run after the current task completes. The methods inherited from
 * <code>TaskManager</code> schedule durable, transactional tasks. The
 * <code>scheduleNonDurableTask</code> methods defined here are used to
 * schedule tasks that are not persisted by the <code>TaskService</code> but
 * optionally invoked in a transactional context.
 * <p>
 * By default all tasks scheduled will be owned by the calling task's
 * owning identity. To create a new owning identity for a task use the
 * <code>RunWithNewIdentity</code> annotation as described in the docs
 * for <code>TaskManager</code>.
 *
 * @see TaskManager
 */
public interface TaskService extends TaskManager, Service {

    /**
     * Schedules a single task to run once the current task has finished.
     * The task will not be persisted by the <code>TaskService</code>, and
     * therefore is not guaranteed to run.
     *
     * @param task the <code>KernelTask</code> to run
     * @param transactional <code>true</code> if the given task should be run
     *                      in a transaction, <code>false</code> otherwise
     *
     * @throws TaskRejectedException if the backing scheduler refuses to
     *                               accept the task
     * @throws TransactionException if the operation failed because of a
     *		                        problem with the current transaction
     */
    void scheduleNonDurableTask(KernelRunnable task, boolean transactional);

    /**
     * Schedules a single task to run, after the given delay, once the
     * current task has finished. The task will not be persisted by the
     * <code>TaskService</code>, and therefore is not guaranteed to run.
     * As described in <code>TaskManager</code>, the delay is from the
     * time of this call, not from the time that the transaction commits.
     *
     * @param task the <code>KernelTask</code> to run
     * @param delay the number of milliseconds to delay before running the task
     * @param transactional <code>true</code> if the given task should be run
     *                      in a transaction, <code>false</code> otherwise
     *
     * @throws TaskRejectedException if the backing scheduler refuses to
     *                               accept the task
     * @throws TransactionException if the operation failed because of a
     *		                        problem with the current transaction
     */
    void scheduleNonDurableTask(KernelRunnable task, long delay, 
                                boolean transactional);

}
