/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.profile;


/** 
 * A profile sample is a list of {@code long} data points.
 * <p>
 * Profile samples are created with calls to 
 * {@link ProfileConsumer#createSample ProfileConsumer.createSample}.  A 
 * sample's name includes both the {@code name} supplied to {@code createSample}
 * and the value of {@link ProfileConsumer#getName}.
 */
public interface ProfileSample {

    /**
     * Returns the name of this list of samples.
     *
     * @return the sample's name
     */
    String getName();

    /**
     * Adds a new sample to the end of the current list of samples.
     *
     * @param value the data sample to be added
     */
    void addSample(long value);
}
