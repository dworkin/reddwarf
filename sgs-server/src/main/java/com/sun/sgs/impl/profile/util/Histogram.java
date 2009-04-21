/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.profile.util;


/**
 * A data aggregation with utility methods for converting the internal
 * representations to text.  A histogram takes in arbitrary data
 * samples and keeps track of the distributions and count of samples
 * seen.  Each histogram should be considered a view on the data
 * itself, not a collection of the data.
 */
public interface Histogram {

    /**
     * Updates the state of the histogram by finding the appropriate
     * bin for the provided value and then incrementing the number of
     * samples seen for that bin.
     *
     * @param value the value to be aggregated
     */
    void bin(long value);

    /**
     * Clears the internal state of the histogram, removing all
     * collected samples.
     */
    void clear();
    
    /**
     * Returns the number of samples represented by this histogram.
     *
     * @return the number of samples represented by this hisogram
     */
    int size();    

    /**
     * Returns a text-based representation of this histogram.
     *
     * @return a text-based representation of this histogram
     */
    String toString();

    /**
     * Returns a text-based representation of this histogram where
     * each of the bins has the provided label.  This method allows
     * for attaching a name such as "bytes" or "ms" to the number
     * values on each of the bins.
     *
     * @param binLabel the label to be appended to the name of each of
     *        the bins.
     * 
     * @return a text based representation of this histogram
     */
    String toString(String binLabel);

}
