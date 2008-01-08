/*
 * Copyright 2008 Sun Microsystems, Inc.
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


/** Package-private class that maintains state about threads. */
abstract class ThreadState {

    // a flag that tracks whether the current thread belongs to the scheduler
    private static ThreadLocal<Boolean> schedulerThread =
        new ThreadLocal<Boolean>() {
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };

    /** Sets whether the current thread is working for the scheduler. */
    static void setAsSchedulerThread(boolean workingForScheduler) {
        schedulerThread.set(workingForScheduler);
    }

    /** Returns whether the current thread is working for the scheduler. */
    static boolean isSchedulerThread() {
        return schedulerThread.get();
    }

}
