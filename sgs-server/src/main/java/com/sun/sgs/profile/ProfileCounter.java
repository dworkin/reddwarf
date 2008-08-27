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

package com.sun.sgs.profile;


/**
 * Interface that represents a counter used in profiling tasks run through
 * the scheduler. All counters have a name associated with them, and start
 * at zero. Counters can only be incremented. Counters are either aggregate,
 * where all increments are aggregated over the lifetime of the system, or
 * task-local, in which each counter is effectively first set to zero for each
 * task where that counter is modified.
 */
public interface ProfileCounter {

    /**
     * Returns the name of this counter.
     *
     * @return the counter's name
     */
    String getCounterName();

    /**
     * Returns whether this is a task-local counter.
     *
     * @return <code>true</code> if this counter is task-local,
     *         <code>false</code> if this counter is aggregated
     */
    boolean isTaskLocal();

    /**
     * Increments the counter by <code>1</code>.
     */
    void incrementCount();

    /**
     * Increments the counter by the given value.
     *
     * @param value the amount to increment the counter
     */
    void incrementCount(long value);

}
