/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * This is the base interface used for all tasks that can be submitted
 * to the <code>TaskScheduler</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface KernelRunnable {

    /**
     * Runs this <code>KernelRunnable</code>. If this is run by the
     * <code>TaskScheduler</code>, and if an <code>Exception</code> is
     * thrown that implements <code>ExceptionRetryStatus</code> then
     * the <code>TaskScheduler</code> will consult the
     * <code>shouldRetry</code> method to see if this should be re-run.
     *
     * @throws Exception if any error occurs
     */
    public void run() throws Exception;

}
