/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.profile;

/**
 * An operation which provides task-local information to {@link ProfileReport}s.
 * <p>
 * If operation occurs during a given task, the {@code ProfileReport}
 * for that task will include the operation.
 */
public interface TaskProfileOperation extends ProfileOperation {

    /**
     * {@inheritDoc} 
     * 
     * @throws IllegalStateException if this is called outside the scope
     *                               of a task run through the scheduler
     */
    void report();
}
