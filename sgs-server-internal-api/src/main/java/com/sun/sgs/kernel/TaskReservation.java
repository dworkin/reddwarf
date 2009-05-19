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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.kernel;


/**
 * This interface manages task reservations. Reservations are used to
 * guarantee space in a scheduler for tasks. Once a reservation is acquired,
 * the associated tasks will always have space to run. Acquiring a reservation
 * does not actually schedule the tasks, so until <code>use</code> is called,
 * the tasks will never run. The reservation may be cancelled if it has not
 * yet been run, and no cost will be charged to the owner.
 * <p>
 * If this reservation includes tasks scheduled to be run at a specified time,
 * and that time has already passed when <code>use</code> is called, then
 * the tasks are run immediately.
 */
public interface TaskReservation
{

    /**
     * Cancels this reservation, releaseing the reserved space in the
     * scheduler for the associated task or tasks.
     *
     * @throws IllegalStateException if the reservation has already been
     *                               used or cancelled
     */
    void cancel();

    /**
     * Uses the reservation, scheduling all associated tasks to run.
     *
     * @throws IllegalStateException if the reservation has already been
     *                               used or cancelled
     */
    void use();

}
