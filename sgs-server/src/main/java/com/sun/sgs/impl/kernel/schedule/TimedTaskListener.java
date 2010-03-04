/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;


/** Package-private interface for notifying when delayed tasks are ready. */
interface TimedTaskListener {

    /**
     * Called when a delayed task has reached its time to run.
     *
     * @param task the {@code ScheduledTask} that is ready to run
     */
    void timedTaskReady(ScheduledTask task);

}
