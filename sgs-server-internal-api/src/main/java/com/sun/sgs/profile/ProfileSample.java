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
