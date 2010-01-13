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
 * --
 */

package com.sun.sgs.impl.profile.util;


/**
 * A histogram that bins values according to powers of 2.
 */
public class PowerOfTwoHistogram implements Histogram {

    /**
     * the longest bar in the histogram
     */
    // REMINDER: should this be a configurable option?
    private static final int MAX_HIST_LENGTH = 40;	

    /**
     * The bins for the histogram, which contain the current count
     */
    private final int[] bins;

    /**
     * The maximum index that contains an entry
     */
    private int maxIndex;

    /**
     * The minimum index that contains an entry
     */
    private int minIndex;

    /**
     * The maximum number of entries current in one bin of the
     * histogram.
     */
    private int maxCount;

    /**
     * The number of samples that this histogram represents.
     */
    private int size;

    /**
     * Creates a new power-of-2 frequency histogram.
     */
    public PowerOfTwoHistogram() {

	bins = new int[64]; // buckets for (2^64)+1, plus a zero bucket

	maxIndex = Integer.MIN_VALUE;
	minIndex = Integer.MAX_VALUE;
	maxCount = 0;
	size = 0;
    }

    /**
     * {@inheritDoc}
     *
     * Values are binned in the largest bin that is <i>less than</i>
     * the element is incremented.  Note that this histogram supports
     * only <i>non-negative</i> values; negative values will not be
     * counted.
     */
    public void bin(long value) {
	if (value < 0) {
	    return;
        }
	
	int bin = 0;

	// skip the special cases: bin 0 is for zero values.
 	if (value != 0) {
 	    int i = 0;
 	    while (value > (1 << (i + 1))) {
		++i;
            }
	    bin = i + 1; // 0 index is for 0 values
 	}
	
	maxIndex = Math.max(maxIndex, bin);
	minIndex = Math.min(minIndex, bin);
	
        int count = bins[bin]++;
	if (count > maxCount) {
	    maxCount = count;      
        }

	size++;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
	for (int i = 0; i < bins.length; ++i) {
	    bins[i] = 0;
        }
	maxIndex = Integer.MIN_VALUE;
	minIndex = Integer.MAX_VALUE;
	maxCount = 0;
	size = 0;
    }


    /**
     * {@inheritDoc}
     */
    public int size() {
	return size;
    }

    /**
     * Generates a text representation of this power-of-2 frequency
     * histogram.  The histogram is presented similar to the following
     * example:
     *
     * <pre>
     *  8 |***
     * 16 |
     * 32 |*
     * 64 |*****
     * </pre>
     *
     * Bins that have at least one sample will have a bar displayed.
     * All leading and trailing empty bins are truncated.  The final
     * line will have a newline at the end.
     *
     * @return the histogram
     */
    public String toString() {
	return toString("");
    }

    /**
     * Generates a text representation of this power-of-2 frequency
     * histogram similar to {@link #toString()} but with labels on
     * bins.  For example, a bin would appear as:
     *
     * <pre>
     * 16ms |***
     * </pre>
     * 
     * @param binLabel the label to append to each of the bins
     *
     * @return the histogram
     */
    public String toString(String binLabel) {
	// get the length of the longest string version of the integer
	// to make the histogram line up correctly
	int maxLength = Integer.toString(1 << maxIndex).length();
	
	StringBuilder b = new StringBuilder(128);

	for (int i = minIndex; i <= maxIndex; ++i) {

	    // special case for the 0 index, as it represents values
	    // of 0 and therefore can't be shifted for its real value
	    String n = (i == 0) ? "0" : Integer.toString(1 << (i - 1));

	    // make the bars all line up evenly by padding with spaces	    
	    for (int j = n.length(); j < maxLength; ++j) {
		b.append(" ");
            }
	    b.append(n).append(binLabel).append(" |");

	    // scale the bar length relative to the max
	    int bars = (int) ((bins[i] / (double) maxCount) * MAX_HIST_LENGTH);

	    // bump all non-empty buckets by one, so we can tell the
	    // difference
	    if (bins[i] > 0) {
		bars++;
            }
	    for (int j = 0; j < bars; ++j) {
		b.append("*");
            }
	    b.append("\n");
	}
	return b.toString();
    }

}
