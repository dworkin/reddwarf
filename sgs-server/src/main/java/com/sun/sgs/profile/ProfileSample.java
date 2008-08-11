/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.profile;


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
    String getSampleName();

    /**
     * Returns whether this is a task-local list of samples.
     *
     * @return <code>true</code> if this counter is task-local,
     *         <code>false</code> if this counter is aggregated
     */
    boolean isTaskLocal();

    /**
     * Adds a new sample to the end of the current list of samples.
     *
     * @param value the amount to increment the counter
     */
    void addSample(long value);
    
}
