package com.sun.sgs.impl.kernel.profile;

/**
 * A data aggregation with utility methods for converting the internal
 * representations to text.
 */
public interface Histogram {

    /**
     * Updates the appropriate bin of the histogram based on the
     * provided value.
     *
     * @param value the value to be aggregated
     */
    public void bin(long value);

    /**
     * Clears the internal state of the histogram, removing all collected samples.
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
     * each of the bins has the provided label.
     *
     * @param binLabel the label to be appended to each of the bins
     * 
     * @return a text based representation of this histogram
     */
    public String toString(String binLabel);

}