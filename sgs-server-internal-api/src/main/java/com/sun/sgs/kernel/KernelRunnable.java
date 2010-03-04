/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.kernel;


/**
 * This is the base interface used for all tasks that can be submitted
 * to instances of <code>Scheduler</code>.
 */
public interface KernelRunnable {

    /**
     * Returns the fully qualified type of the base task that is run by this
     * <code>KernelRunnable</code>. Many types of runnables wrap around other
     * instances of <code>KernelRunnable</code> or <code>Task</code>. This
     * method provides the type of the base task that is being wrapped by any
     * number of <code>KernelRunnable</code>s, where a given task that wraps
     * another task will return that other task's base type such that any
     * wrapping task can be queried and will return the same base task type.
     *
     * @return the fully-qualified name of the base task class type
     */
    String getBaseTaskType();

    /**
     * Runs this <code>KernelRunnable</code>. If this is run by a
     * <code>Scheduler</code> that support re-try logic, and if an
     * <code>Exception</code> is thrown that implements 
     * <code>ExceptionRetryStatus</code> then the <code>Scheduler</code>
     * will consult the <code>shouldRetry</code> method of the
     * <code>Exception</code> to see if this task should be re-run.
     *
     * @throws Exception if any error occurs
     */
    void run() throws Exception;

}
