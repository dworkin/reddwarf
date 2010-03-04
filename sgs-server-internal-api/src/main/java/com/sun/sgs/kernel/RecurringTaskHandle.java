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
