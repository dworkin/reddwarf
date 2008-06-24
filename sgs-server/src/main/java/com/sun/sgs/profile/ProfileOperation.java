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

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;


/**
 * This interface represents a single operation that can be reported as
 * happening during the life of a task running through the scheduler.
 */
public interface ProfileOperation {

    /**
     * Returns the name of this operation.
     *
     * @return the name
     */
    public String getOperationName();

    /**
     * Returns the identifier for this operation.
     *
     * @return the identifier
     */
    public int getId();

    /**
     * Tells this operation to report that it is happening if profiling is
     * enabled at the given level. This may be called any number of times 
     * during a single task.
     *
     * @param level the profiling level
     * @throws IllegalStateException if this is called outside the scope
     *                               of a task run through the scheduler
     */
    public void report(ProfileLevel level);

}
