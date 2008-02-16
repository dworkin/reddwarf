/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;


/**
 * This extension of {@code Scheduler} is used to schedule transactional
 * tasks. These are tasks that run in the context of a {code Transaction}.
 * Transactional tasks are expected to be short-lived (typically on the
 * order of 10s of milliseconds). All tasks run through an implementaion
 * of {@code TransactionScheduler} will run transactionally, and may be
 * re-tried in the event of failure.
 * <p>
 * If the result of running a task via the {@code reserveTask} or
 * {@code scheduleTask} methods is an {@code Exception} which implements
 * {@code ExceptionRetryStatus}, then its {@code shouldRetry} method is
 * called to decide if the task should be re-tried. It is up to the scheduler
 * implementation's policy to decide how and when tasks are re-run, but all
 * failing tasks run through a {@code TransactionScheduler} that wish to be
 * re-tried will eventually be re-run given available resources.
 * <p>
 * Note that re-try is handled slightly differently for {@code runTask}. See
 * the documentation on that method for more details.
 */
public interface TransactionScheduler extends Scheduler {

    /**
     * Runs the given task synchronously, returning when the task has
     * completed or throwing an exception if the task fails. It is up to the
     * {@code TransactionScheduler} implementation to decide when to run this
     * task, so the task may be run immediately or it might be queued behind
     * waiting tasks. The task may be handed off to another thread of control
     * for execution. In all cases, the caller will block until the task
     * completes or fails permanently.
     * <p>
     * As with all methods of {@code TransactionScheduler}, tasks run with
     * {@code runTask} will be run transactionally. If the caller is not
     * in an active transaction, then a transaction is created to run the
     * task. If the caller is already part of an active transaction, then
     * the task is run as part of that transaction, and the {@code owner}
     * paramater is ignored.
     * <p>
     * When the caller is not part of an active transaction, then when the
     * given task completes it will also attempt to commit. If committing
     * the transaction fails, normal re-try behavior is applied. If the
     * task requests to be re-tried, then it will be re-run according to the
     * scheduler implementation's policy. In this case, {@code runTask}
     * will not return until the task finally succeeds, or is no longer
     * re-tried.
     * <p>
     * In the event that the caller is part of an active transaction, then
     * there is no re-try applied in the case of a failure, and the
     * transaction is not committed if the task completes successfully. This
     * is because the system does not support nested transactions, and so
     * the decision to commit or re-try is left to the active transaction.
     *
     * @throws TaskRejectedException if the given task is not accepted
     * @throws Exception if the task fails and is not re-tried
     */
    public void runTask(KernelRunnable task, Identity owner) throws Exception;

    /**
     * Creates a new {@code TaskQueue} to use in scheduling dependent
     * tasks. Each task added to the queue will be run in a separate
     * transaction. Re-try is applied to each transaction, and the next
     * task in the queue is run only after the current task either
     * completes successfully or fails permanently.
     *
     * @return a new {@code TaskQueue}
     */
    public TaskQueue createTaskQueue();

}
