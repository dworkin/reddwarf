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

import java.util.List;

/**
 * A profile sample is a list of data points that are accumulated
 * during the lifetime of the system.
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
     * Adds a new sample to the end of the current list of samples.
     *
     * @param value the amount to increment the counter
     */
    void addSample(long value);
    
    /**
     * Returns a list of all added samples in the order they are added.
     * 
     * @return all the samples
     */
    List<Long> getSamples();
    
    /**
     * Clear the accumulated samples.
     */
    void clearSamples();
}
