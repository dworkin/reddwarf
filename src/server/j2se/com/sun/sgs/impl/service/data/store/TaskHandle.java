/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store;

/** An interface for cancelling a periodic task. */
public interface TaskHandle {

    /**
     * Cancels future runs of the task.
     *
     * @throws	IllegalStateException if the task has already been
     *		cancelled
     */
    void cancel();
}
