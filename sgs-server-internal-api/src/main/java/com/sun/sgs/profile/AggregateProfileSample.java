/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
     * Clears the accumulated samples and their statistics.
     */
    void clearSamples();
    
    /**
     * Returns the exponential weighted average of the added samples, using
     * the smoothing factor as defined in {@link #setSmoothingFactor}. 
     * This value is undefined if no samples have been added.
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
     * Returns the number of samples added, which will be a value between
     * {@code 0} and the current {@code capacity}.
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
     * Sets the maximum number of samples this object can hold.
     * Once the limit of samples has been reached, older samples will 
     * be dropped to make room for the newest samples. Dropping samples
     * due to capacity restrictions has no effect on the other aggregated
     * statistics.
     * <p>
     * A limit of {@code 0} indicates that no samples should be held,
     * but the statistics should be gathered.
     * 
     * @param capacity the maximum number of samples this object can hold
     * @throws IllegalArgumentException if the capacity is negative
     */
    void setCapacity(int capacity);
    
    /**
     * Sets the smoothing factor used for calculating the average of the
     * added samples.  The smoothing factor must be a value between 
     * {@code 0.0} and {@code 1.0}, inclusive.
     * <p>
     * A value closer to {@code 1.0} provides less smoothing of the data, 
     * and more weight to recent data;  a value closer to {@code 0.0} provides
     * more smoothing but is less responsive to recent changes.
     *
     * @param smooth the smoothing factor
     * @throws IllegalArgumentException if {@code smooth} is not between
     *                           {@code 0.0} and {@code 1.0}, inclusive
     */
    void setSmoothingFactor(double smooth);
    
    /**
     * Gets the current smoothing factor in effect for calculating the average
     * of the added samples.
     * @return the current smoothing factor
     */
    double getSmoothingFactor();
}
