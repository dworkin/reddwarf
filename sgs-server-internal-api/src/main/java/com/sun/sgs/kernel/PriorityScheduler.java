/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;


/**
 * This is an experimental interface to provide a way to test different
 * thoughts on priority through a few basic scheduling methods that accept
 * priority levels.
 */
public interface PriorityScheduler {

    /**
     * Reserves the ability to run the given task. The scheduler will make
     * a best effort to honor the requested priority.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param priority the requested {@code Priority}
     *
     * @return a {@code TaskReservation} for the task
     *
     * @throws TaskRejectedException if a reservation cannot be made
     */
    TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                Priority priority);

    /**
     * Schedules a task to run as soon as possible based on the specific
     * scheduler implementation. The scheduler will make a best effort
     * to honor the requested priority.
     *
     * @param task the {@code KernelRunnable} to execute
     * @param owner the entity on who's behalf this task is run
     * @param priority the requested {@code Priority}
     *
     * @throws TaskRejectedException if the given task is not accepted
     */
    void scheduleTask(KernelRunnable task, Identity owner, Priority priority);

}
