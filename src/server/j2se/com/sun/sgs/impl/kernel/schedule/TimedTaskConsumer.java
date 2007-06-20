/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;


/**
 * Simple interface used by classes that want to consume delayed tasks managed
 * by a <code>TimedTaskHandler</code>
 */
interface TimedTaskConsumer {

    /**
     * Called when a delayed task has reached its time to run.
     *
     * @param task the <code>ScheduledTask</code> that is ready to run
     */
    public void timedTaskReady(ScheduledTask task);

}
