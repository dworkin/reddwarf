/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;


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
    public void bin(long value);

    /**
     * Clears the internal state of the histogram, removing all
     * collected samples.
     */
    public void clear();
    
    /**
     * Returns the number of samples represented by this histogram.
     *
     * @return the number of samples represented by this hisogram
     */
    public int size();    

    /**
     * Returns a text-based representation of this histogram.
     *
     * @return a text-based representation of this histogram
     */
    public String toString();

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
    public String toString(String binLabel);

}