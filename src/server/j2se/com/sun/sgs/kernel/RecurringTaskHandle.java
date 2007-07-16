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
 * This interface provides a handle to a recurring task in the scheduler.
 */
public interface RecurringTaskHandle
{

    /**
     * Cancels the associated recurring task. A recurring task may be
     * cancelled before it is started.
     *
     * @throws IllegalStateException if the task has already been cancelled
     */
    public void cancel();

    /**
     * Starts the associated recurring task. A recurring task will not start
     * running until this method is called.
     *
     * @throws IllegalStateException if the task has already been started,
     *                               or has been cancelled
     */
    public void start();

}
