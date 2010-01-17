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
 * A histogram that bins elements based on provided bounds and a step
 * size.  This class is useful for viewing a particular range of data
 * and specifying how the distribution is grouped.  The lower and
 * upper bounds should be used to filter out all extraneous data
 * points.
 */
public class LinearHistogram implements Histogram {

    /**
     * the longest bar in the histogram
     */
    private static final int MAX_HIST_LENGTH = 40;	

    /**
     * The inclusive lower bound for the histogram
     */
    private final int lBound;

    /**
     * The inclusive upper bound for the histogram
     */
    private final int uBound;

    /**
     * The integer range between buckets
     */
    private final long stepSize;

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
     * Creates a linear frequency histogram with the provided lower
     * bound, upper bound and step size.  
     *
     * @param lBound the lower bound for the histogram; values
     *        strictly less than this will not be included
     * @param uBound the upper bound for the histogram; values
     *        strictly greater than this will not be included
     * @param stepSize the maximum range for each bin between {@code
     *        lBound} and {@code uBound}; if {@code stepSize} does not
     *        evenly divide the difference, the last bin will be
     *        truncated in size
     *
     * @throws IllegalArgumentException if {@code lBound} is not less
     *         than {@code uBound}, or if {@code stepSize} is not
     *         strictly positive     
     */
    public LinearHistogram(int lBound, int uBound, long stepSize) {
	if (lBound >= uBound) {
	    throw new IllegalArgumentException("Lower bound must be less " +
					       "than upper bound");
        }
	if (stepSize <= 0) {
	    throw new IllegalArgumentException("Step size must be " +
					       "strictly positive");
        }

	this.lBound = lBound;
	this.uBound = uBound;
	this.stepSize = stepSize;

	bins = new int[(int) ((uBound - lBound) / stepSize) + 1];

	maxIndex = Integer.MIN_VALUE;
	minIndex = Integer.MAX_VALUE;
	maxCount = 0;
	size = 0;
    }

    /**
     * {@inheritDoc}
     *
     * Values are binned in the largest bin that is <i>less than</i>
     * the element is incremented.  Values less than the lower bound
     * or values that are greater than the upper bound are not counted.
     */ 
    public void bin(long value) {
	if (value < lBound || value > uBound) {
	    return;
        }
	
	// find the appropriate bin for this value, starting at the
	// lower bound and increasing by the provided step size
	int bin = 0;
        long i = lBound + stepSize;
        while (i < uBound) {
            i += stepSize;
            if (value > i) {
                bin++;
            } else {
                break;
            }
        }
		
	maxIndex = Math.max(maxIndex, bin);
	minIndex = Math.min(minIndex, bin);
	
	// keep track of which bin has the most number of elements,
	// after incrementing the bin's count
	int count = bins[bin]++;
	if (count  > maxCount) {
	    maxCount = count;
        }
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
     * Generates a text representation of this linear frequency
     * histogram.  The histogram is presented similar to the following
     * example:
     *
     * <pre>
     *    [lower-bound] |***
     *    [l.b. + step] |
     *  [l.b. + 2*step] |*
     *    [upper-bound] |****
     * </pre>
     *
     * Bins that have at least one sample will have a bar displayed.
     * All leading and trailing empty bins are truncated, which may
     * result in the lower and upper bound bins not being displayed.
     * The final line will have a newline at the end.
     *
     * @return a string representation of this histogram
     */
    public String toString() {
	return toString("");
    }

    /**
     * Generates a text representation of this linear frequency
     * histogram similar to {@link #toString()} but with labels on
     * bins.  For example, a bin would appear as:
     *
     * <pre>
     * 200ms |***
     * 400ms |*
     * 600ms |*****
     * </pre>
     * 
     * @param binLabel the label to append to each of the bins
     *
     * @return a string representation of this histogram
     */
    public String toString(String binLabel) {
	// get the length of the longest string version of the integer
	// to make the histogram line up correctly
	int maxLength = 
	    Long.toString(lBound + (maxIndex * stepSize)).length();
	
	StringBuilder b = new StringBuilder(128);

	for (int i = minIndex; i <= maxIndex; ++i) {
	    
	    String binName = 
		Long.toString(Math.min(lBound + (i * stepSize), uBound));

	    // make the bars all line up evenly by padding with spaces	    
	    for (int j = binName.length(); j < maxLength; ++j) {
		b.append(" ");
            }
	    b.append(binName).append("binLabel").append(" |");

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
