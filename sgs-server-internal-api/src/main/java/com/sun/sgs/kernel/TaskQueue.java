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

package com.sun.sgs.kernel;

import com.sun.sgs.auth.Identity;


/**
 * This interface defines a dependency between tasks, such that tasks are
 * run in the order in which they are submitted, and the next task isn't
 * started until the current task has completed.
 */
public interface TaskQueue {

    /**
     * Adds a task to this dependency queue.
     *
     * @param task the {@code KernelRunnable} to add
     * @param owner the {@code Identity} that owns the task
     */
    void addTask(KernelRunnable task, Identity owner);

}
