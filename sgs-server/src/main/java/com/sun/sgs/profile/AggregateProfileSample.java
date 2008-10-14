/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 * A profile sample which is aggregated until explicitly cleared.
 * Some simple statistics are gathered about the samples.
 */
public interface AggregateProfileSample extends ProfileSample {
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
    
    /**
     * Returns the exponentaially smoothed average of the samples.
     * 
     * @return the average of the samples
     */
    double getAverage();
    
    /**
     * Gets the maximum sample value.
     * 
     * @return the maximum sample
     */
    long getMaxSample();
    
    /**
     * Gets the minimum sample value.
     * 
     * @return the minimum sample
     */
    long getMinSample();
}
