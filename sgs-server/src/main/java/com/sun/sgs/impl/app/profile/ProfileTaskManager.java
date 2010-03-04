/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;


/**
 * This is implementation of {@code TaskManager} simply calls its backing 
 * manager for each manager method.
 */
public class ProfileTaskManager implements TaskManager {

    // the task manager that this manager calls through to
    private final TaskManager backingManager;


    /**
     * Creates an instance of <code>ProfileTaskManager</code>.
     *
     * @param backingManager the <code>TaskManager</code> to call through to
     */
    public ProfileTaskManager(TaskManager backingManager) {
        this.backingManager = backingManager;
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        backingManager.scheduleTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        backingManager.scheduleTask(task, delay);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period) 
    {
        return backingManager.schedulePeriodicTask(task, delay, period);
    }

    /**
     * {@inheritDoc}
     */
    public boolean shouldContinue() {
        return backingManager.shouldContinue();
    }

}
