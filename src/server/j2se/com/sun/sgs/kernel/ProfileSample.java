/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * A profile sample is a list of data points that are accumulated
 * during the lifetime of a task.  A profile sample may be for either
 * a single task or span multiple tasks.  A profile sample allows for
 * different aggregations on data at a later time.
 *
 * @see ProfileCounter
 * @see ProfileOperation
 */
public interface ProfileSample {

    /**
     * Returns the name of this list of samples.
     *
     * @return the counter's name
     */
    public String getSampleName();

    /**
     * Returns whether this is a task-local list of samples.
     *
     * @return <code>true</code> if this counter is task-local,
     *         <code>false</code> if this counter is aggregated
     */
    public boolean isTaskLocal();

    /**
     * Adds a new sample to the end of the current list of samples.
     *
     * @param value the amount to increment the counter
     */
    public void addSample(long value);
    
}
