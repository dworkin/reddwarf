/*
 * Copyright 2008 Sun Microsystems, Inc.
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


/**
 * This interface is used to start long-running tasks (for example, a consumer
 * thread or a select loop) that need their own thread of control. Unlike
 * tasks submitted to <code>TaskScheduler</code>, no attempt is made to
 * re-try long-running tasks.
 */
public interface ResourceCoordinator
{

    /**
     * Requests that the given task run in its own thread of control. This
     * should only be done for long-running tasks.
     *
     * @param task the <code>Runnable</code> to start in its own thread
     * @param component the component that manages the task
     *
     * @throws TaskRejectedException if the resources cannot be allocated
     *                               for the component's task
     */
    public void startTask(Runnable task, Manageable component);

}
