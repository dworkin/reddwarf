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
