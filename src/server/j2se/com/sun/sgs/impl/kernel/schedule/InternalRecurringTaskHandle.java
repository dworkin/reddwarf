/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.kernel.RecurringTaskHandle;

import java.util.TimerTask;


/**
 * This interface is the specific type of <code>RecurringTaskHandle</code>
 * used by all schedulers in this package. It adds some internal-use methods
 * to help manage the task and its relationship with schedulers and timers.
 */
interface InternalRecurringTaskHandle extends RecurringTaskHandle {

    /**
     * Called when the system thinks that the associated task should schedule
     * its next recurrence. This typically happens after the task has just
     * been executed, and is therefore ready to think about its next run.
     */
    public void scheduleNextRecurrence();

    /**
     * Sets the associated <code>TimerTask</code> for this handle. This
     * method may be called any number of times on a handle. Typically
     * a recurring task will re-set the associated <code>TimerTask</code>
     * with each recurrence of execution.
     *
     * @param timerTask the associated <code>TimerTask</code>
     */
    public void setTimerTask(TimerTask timerTask);

    /**
     * Returns whether this handle has been cancelled. This does not say
     * anything about the state of any associated <code>TimerTask</code>.
     *
     * @return <code>true</code> if this handle has been cancelled,
     *         <code>false</code> otherwise
     */
    public boolean isCancelled();

}
