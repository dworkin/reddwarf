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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
    void cancel();

    /**
     * Starts the associated recurring task. A recurring task will not start
     * running until this method is called.
     *
     * @throws IllegalStateException if the task has already been started,
     *                               or has been cancelled
     */
    void start();

}
