/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.kernel.schedule;

/**
 * Enumeration of possible retry actions that a scheduler can use to
 * retry a failed task.
 */
public enum SchedulerRetryAction {

    /**
     * Indicates that a task should be dropped.
     */
    DROP,

    /**
     * Indicates that a task should be retried at some point in the future.
     */
    RETRY_LATER,

    /**
     * Indicates that a task should be retried immediately.
     */
    RETRY_NOW;

}
