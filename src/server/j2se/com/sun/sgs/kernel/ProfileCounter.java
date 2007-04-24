/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * Interface that represents a counter used in profiling tasks run through
 * the scheduler. All counters have a name associated with them, and start
 * at zero. Counters can only be incremented. Counters are either aggregate,
 * where all increments are aggregated over the lifetime of the system, or
 * task-local, in which each counter is effectively first set to zero for each
 * task where that counter is modified.
 */
public interface ProfileCounter {

    /**
     * Returns the name of this counter.
     *
     * @return the counter's name
     */
    public String getCounterName();

    /**
     * Returns whether this is a task-local counter.
     *
     * @return <code>true</code> if this counter is task-local,
     *         <code>false</code if this counter is aggregated
     */
    public boolean isTaskLocal();

    /**
     * Increments the counter by <code>1</code>.
     */
    public void incrementCount();

    /**
     * Increments the counter by the given value.
     *
     * @param value the amount to increment the counter
     */
    public void incrementCount(long value);

}
