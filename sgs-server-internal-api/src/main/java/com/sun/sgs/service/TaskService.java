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
