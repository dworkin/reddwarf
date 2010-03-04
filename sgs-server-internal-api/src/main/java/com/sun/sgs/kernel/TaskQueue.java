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
