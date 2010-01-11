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

package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Provides facilities for managing a {@link Task} scheduled with the {@link
 * TaskManager} to run periodically.  Classes that implement
 * <code>PeriodicTaskHandle</code> must also implement {@link Serializable}.
 *
 * @see		TaskManager#schedulePeriodicTask 
 *		TaskManager.schedulePeriodicTask
 */
public interface PeriodicTaskHandle {

    /**
     * Cancels attempts to run the associated task in future periods.  Calling
     * this method has no effect on runs of the task for the current period if
     * an attempt to run the task for that period has already begun.
     * Cancelling a periodic task may involve removing an associated managed
     * object maintained internally by the <code>TaskManager</code>.  The
     * system will make an effort to flag subsequent references to the removed
     * object by throwing {@link ObjectNotFoundException} when this method is
     * called, although that behavior is not guaranteed.
     *
     * @throws	ObjectNotFoundException if the task has already been cancelled
     *		and removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void cancel();
}
