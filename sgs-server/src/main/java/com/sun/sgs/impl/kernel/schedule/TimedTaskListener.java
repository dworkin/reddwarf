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
 * --
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;


/** Package-private interface for notifying when delayed tasks are ready. */
interface TimedTaskListener {

    /**
     * Called when a delayed task has reached its time to run.
     *
     * @param task the {@code ScheduledTask} that is ready to run
     */
    void timedTaskReady(ScheduledTask task);

}
