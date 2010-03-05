/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
