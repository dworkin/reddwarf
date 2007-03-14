/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
 *
 * @since 1.0
 * @author Seth Proctor
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
    public void cancel();

    /**
     * Uses the reservation, scheduling all associated tasks to run.
     *
     * @throws IllegalStateException if the reservation has already been
     *                               used or cancelled
     */
    public void use();

}
