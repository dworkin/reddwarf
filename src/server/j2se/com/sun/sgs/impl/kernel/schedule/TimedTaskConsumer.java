/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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


/**
 * Simple interface used by classes that want to consume delayed tasks managed
 * by a <code>TimedTaskHandler</code>
 */
interface TimedTaskConsumer {

    /**
     * Called when a delayed task has reached its time to run.
     *
     * @param task the <code>ScheduledTask</code> that is ready to run
     */
    public void timedTaskReady(ScheduledTask task);

}
