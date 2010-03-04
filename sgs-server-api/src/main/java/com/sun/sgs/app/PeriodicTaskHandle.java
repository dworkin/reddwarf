/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
