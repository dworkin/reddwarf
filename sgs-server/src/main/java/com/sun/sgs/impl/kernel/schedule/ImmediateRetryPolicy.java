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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import java.util.Properties;

/**
 * This {@code SchedulerRetryPolicy} always causes a task to
 * retry immediately unless the task was interrupted in which case it
 * is put back onto the scheduler's standard backing queue.
 */
public class ImmediateRetryPolicy implements SchedulerRetryPolicy {

    /**
     * Constructs an {@code ImmediateRetryPolicy}
     *
     * @param properties the system properties available
     */
    public ImmediateRetryPolicy(Properties properties) {

    }

    /** {@inheritDoc} */
    public boolean handoffRetry(ScheduledTask task, 
                                Throwable result,
                                SchedulerQueue backingQueue,
                                SchedulerQueue throttleQueue) {
        // NOTE: this is a very simple initial policy that always causes
        // tasks to re-try "in place" unless they were interrupted, in which
        // case there's nothing to do but re-queue the task
        if (result instanceof InterruptedException) {
            try {
                backingQueue.addTask(task);
                return true;
            } catch (TaskRejectedException tre) {
                return false;
            }
        }
        return false;
    }

}
