/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.profile.ProfileRegistrar;


/**
 * This is an implementation of <code>TaskManager</code> used to support
 * profiling. It simply calls its backing manager for each manager method
 * after first reporting the call. If no <code>ProfileRegistrar</code> is
 * provided via <code>setProfileRegistrar</code> then this manager does no
 * reporting, and only calls through to the backing manager. If the backing
 * manager is also an instance of <code>ProfileProducer</code> then it too
 * will be supplied with the <code>ProfileRegistrar</code> as described in
 * <code>setProfileRegistrar</code>.
 * <p>
 * All of the standard Manager methods implemented here are profiled
 * directly.
 */
public class ProfileTaskManager implements TaskManager, ProfileProducer {

    // the task manager that this manager calls through to
    private final TaskManager backingManager;

    // the operations being profiled
    private ProfileOperation scheduleTaskOp = null;
    private ProfileOperation scheduleTaskDelayedOp = null;
    private ProfileOperation scheduleTaskPeriodicOp = null;

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
     * <p>
     * Note that if the backing manager supplied to the constructor is also
     * an instance of <code>ProfileProducer</code> then its
     * <code>setProfileRegistrar</code> will be invoked when this method
     * is called.
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        ProfileConsumer consumer =
            profileRegistrar.registerProfileProducer(this);

	if (consumer != null) {
	    scheduleTaskOp = consumer.registerOperation("scheduleTask");
	    scheduleTaskDelayedOp =
		consumer.registerOperation("scheduleDelayedTask");
	    scheduleTaskPeriodicOp =
		consumer.registerOperation("schedulePeriodicTask");
	}

        // call on the backing manager, if it's also profiling
        if (backingManager instanceof ProfileProducer)
            ((ProfileProducer)backingManager).
                setProfileRegistrar(profileRegistrar);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        if (scheduleTaskOp != null)
            scheduleTaskOp.report();
        backingManager.scheduleTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        if (scheduleTaskDelayedOp != null)
            scheduleTaskDelayedOp.report();
        backingManager.scheduleTask(task, delay);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period) {
        if (scheduleTaskPeriodicOp != null)
            scheduleTaskPeriodicOp.report();
        return backingManager.schedulePeriodicTask(task, delay, period);
    }

}
