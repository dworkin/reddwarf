/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store;

/** An interface for running periodic tasks. */
public interface Scheduler {
    
    /**
     * Runs the task every period milliseconds, and returns a handle to use to
     * cancel the task.
     *
     * @param	task the task
     * @param	period the period in milliseconds
     * @return	a handle for cancelling future runs
     */
    TaskHandle scheduleRecurringTask(Runnable task, long period);
}
