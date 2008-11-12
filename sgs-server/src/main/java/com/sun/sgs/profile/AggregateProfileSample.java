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
 * <p>
 * The samples and statistics can be cleared by calling {@link #clearSamples}.
 * <p>
 * The {@code capacity} is the maxiumum number of samples that will
 * be held.  Once this limit is reached, older samples will be dropped
 * to make room for the newest samples.  The statistics are over all the
 * gathered samples since creation or the last {@code clearSamples} call;
 * thus, they might not reflect the current list returned by 
 * {@link #getSamples}.
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
     * Returns the average of the added samples. This value is undefined
     * if no samples have been added.
     * 
     * @return the average of the samples
     */
    double getAverage();
    
    /**
     * Gets the maximum sample value added. This value is undefined
     * if no samples have been added.
     * 
     * @return the maximum sample
     */
    long getMaxSample();
    
    /**
     * Gets the minimum sample value added. This value is undefined
     * if no samples have been added.
     * 
     * @return the minimum sample
     */
    long getMinSample();
    
    /**
     * Returns the number of samples added.
     * 
     * @return the number of samples added
     */
    int getNumSamples();
    
    /**
     * Returns the maximum number of samples this object can hold. 
     * 
     * @return the maximum number of samples
     */
    int getCapacity();
    
    /**
     * Set the maximum number of samples this object can hold.
     * Once the limit of samples has been reached, older samples will 
     * be dropped to make room for the newest samples. Dropping samples
     * due to capacity restrictions has no effect on the other aggregated
     * statistics.
     * 
     * @return the maximum number of samples
     * 
     * @throws IllegalArgumentException if the capacity is not positive
     */
    void setCapacity(int capacity);
}
