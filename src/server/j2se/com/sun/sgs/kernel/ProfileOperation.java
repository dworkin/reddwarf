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

package com.sun.sgs.kernel;


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
     * Tells this operation to report that it is happening. This may be
     * called any number of times during a single task.
     *
     * @throws IllegalStateException if this is called outside the scope
     *                               of a task run through the scheduler
     */
    public void report();

}
