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
 */

package com.sun.sgs.impl.service.data.store;

import com.sun.sgs.auth.Identity;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;

/**
 * Provides an implementation of {@link Scheduler} that uses {@link
 * TaskScheduler} and {@link Identity}.
 */
public class DelegatingScheduler implements Scheduler {

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task owner. */
    private final Identity taskOwner;

    /**
     * Creates an instance of this class.
     *
     * @param	taskScheduler the task scheduler to delegate to
     * @param	taskOwner the identity under which tasks should be run
     */
    public DelegatingScheduler(
	TaskScheduler taskScheduler, Identity taskOwner)
    {
	checkNull("taskScheduler", taskScheduler);
	checkNull("taskOwner", taskOwner);
	this.taskScheduler = taskScheduler;
	this.taskOwner = taskOwner;
    }

    /* -- Implement Scheduler -- */

    /** {@inheritDoc} */
    public TaskHandle scheduleRecurringTask(Runnable task, long period) {
	return new Handle(task, period);
    }

    /** Implementation of task handle. */
    private class Handle implements TaskHandle, KernelRunnable {
	private final Runnable task;
	private final long period;
	private final RecurringTaskHandle handle;

	Handle(Runnable task, long period) {
	    this.task = task;
	    this.period = period;
	    handle = taskScheduler.scheduleRecurringTask(
		this, taskOwner, System.currentTimeMillis() + period,
		period);
	    handle.start();
	}

	public String toString() {
	    return "Handle[task:" + task + ", period:" + period + "]";
	}

	public String getBaseTaskType() {
	    return task.getClass().getName();
	}

	public void run() {
	    task.run();
	}

	public void cancel() {
	    handle.cancel();
	}
    }
}
