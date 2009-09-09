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

import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.TaskReservation;


/**
 * Package-private utility implementation of <code>TaskReservation</code>.
 * These reservations are fairly light-weight, and assume that the queue
 * is unbounded and therefore doesn't actually track these reservations or
 * use them to actually reserve any space.
 */
class SimpleTaskReservation implements TaskReservation {

    // whether the reservation has been used or cancelled
    private boolean finished = false;

    // the associated queue
    private final SchedulerQueue queue;

    // the actual reserved task
    private final ScheduledTask task;

    /**
     * Creates an instance of <code>SimpleTaskReservation</code>.
     *
     * @param queue the associated <code>SchedulerQueue</code>
     * @param task the <code>ScheduledTask</code> being reserved
     */
    public SimpleTaskReservation(SchedulerQueue queue,
                                 ScheduledTask task) {
        if (queue == null) {
            throw new NullPointerException("Queue cannot be null");
        }
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }

        this.queue = queue;
        this.task = task;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void cancel() {
        if (finished) {
            throw new IllegalStateException("cannot cancel reservation");
        }
        finished = true;
    }

    /**
     * {@inheritDoc}
     */
    public void use() {
        synchronized (this) {
            if (finished) {
                throw new IllegalStateException("cannot use reservation");
            }
            finished = true;
        }
        queue.addTask(task);
    }

}
