/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * AddressSet.java
 */

package com.sun.multicast.allocation;

import java.util.Enumeration;
import java.util.Vector;
import java.util.ResourceBundle;

/**
 * A set of network addresses. All of the addresses in the
 * <code>AddressSet</code> must be of the same address type.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public class AddressSet {

    /**
     * Creates an <code>AddressSet</code> containing the specified
     * set of <code>AddressRanges</code>.
     *
     * @param ranges an <code>Enumeration</code> of
     *               <code>AddressRanges</code> that
     *               will make up the <code>AddressSet</code>
     * @return an <code>AddressSet</code> containing the specified
     *         <code>AddressRanges</code>. 
     */
    public AddressSet(Enumeration ranges) {
        // Merge overlapping ranges, count addresses, and ensure all ranges
        // are of the same type.
        this.ranges = new Vector();
        ResourceBundle myResources = ResourceBundle.
            getBundle(
		"com.sun.multicast.allocation.resources.AllocationResources");
        while (ranges.hasMoreElements()) {
            AddressRange range = (AddressRange) ranges.nextElement();
            if (addrType == null) {
                addrType = range.getAddressType();
            } else {
                if (!addrType.equals(range.getAddressType())) {
                    throw new IllegalArgumentException(
		        myResources.getString("mixed"));
	        }
            }
            add(range);
        }
        if (addrType == null) {
            addrType = IPv4AddressType.getAddressType();
        }
    }

    /**
     * Adds an <code>AddressRange</code> to this
     * <code>AddressSet</code>.
     *
     * @param range the <code>AddressRange</code> to add
     */
    synchronized void add(AddressRange range) {
        // Merge overlapping ranges and adjust address count.
        int index = find(range);
        if (index > 0) {
            AddressRange previous = (AddressRange) ranges.elementAt(index-1);
            if (range.overlaps(previous)) {
                range = range.merge(previous);
                ranges.removeElementAt(index-1);
                addrCount -= previous.getAddressCount();
                index = index - 1;
            }
        }
        if (index < ranges.size()) {
            AddressRange next = (AddressRange) ranges.elementAt(index);
            if (range.overlaps(next)) {
                range = range.merge(next);
                ranges.removeElementAt(index);
                addrCount -= next.getAddressCount();
            }
        }
        ranges.insertElementAt(range, index);
        addrCount += range.getAddressCount();
    }

    /**
     * Finds the index where an <code>AddressRange</code> belongs
     * in our sorted Vector of <code>AddressRanges</code>. All
     * <code>AddressRanges</code> preceding the returned index
     * will be less than the specified <code>AddressRange</code>.
     * All <code>AddressRanges</code> after the returned index
     * will be greater than or equal to the specified
     * <code>AddressRange</code>.
     *
     * <P>This method is private because it should only be called
     * from the add method.
     *
     * @param range the <code>AddressRange</code> whose proper
     *              location we are going to find
     * @return the index where the <code>AddressRange</code> belongs
     */
    private int find(AddressRange range) {
        // @@@ Should use binary search.
        boolean done = false;
        int i;
        for (i = ranges.size() - 1; (!done) && (i >= 0); i--) {
            if (range.compareTo(ranges.elementAt(i)) >= 0)
            done = true;
        }
        return (i + 1);
    }

    /**
     * Returns an AddressSet that contains the addresses in this set,
     * less the addresses contained in <code>otherSet</code>.
     *
     * <P>If the two <code>AddressSets</code> are of different
     * <code>AddressTypes</code>, a <code>ClassCastException</code> is thrown.
     *
     * @param otherSet the <code>AddressSet</code> containing addresses 
     * to remove
     */
    synchronized AddressSet removeAll(AddressSet otherSet)
        throws ClassCastException {
        AddressSet newSet = new AddressSet(getAddressRanges());

        Enumeration otherRanges = otherSet.getAddressRanges();
        while (otherRanges.hasMoreElements()) {
            AddressRange range = (AddressRange) otherRanges.nextElement();
            newSet.removeAll(range);
        }
        if (newSet.equals(this))
            return (this);
        else
            return (newSet);
    }

    /**
     * Removes the addresses contained in an <code>AddressRange</code> from this
     * <code>AddressSet</code>.
     *
     * <P>If the <code>AddressRange</code> has a different
     * <code>AddressType</code> from this <code>AddressSet</code>,
     * a <code>ClassCastException</code> is thrown.
     *
     * @param range the <code>AddressRange</code> containing addresses to remove
     */
    private void removeAll(AddressRange range)
        throws ClassCastException {
        // Remove range from overlapping ranges and update count.
        int index = find(range);
        if (index > 0)
            index = index - 1;

	boolean done = false;
	boolean firstTime = true;
	while (!done) {
	    AddressRange ourRange = (AddressRange) ranges.elementAt(index);
	    if (ourRange.overlaps(range)) {
		ranges.removeElementAt(index);
		addrCount -= ourRange.getAddressCount();
		Address ourFirst = ourRange.getFirstAddress();
		Address otherFirst = range.getFirstAddress();
		if (ourFirst.compareTo(otherFirst) < 0) {
		    Address newLast = 
			((IPv4Address) otherFirst).previousAddress();
		    AddressRange newRange = 
			new AddressRange(ourFirst, newLast);
		    ranges.insertElementAt(newRange, index);
		    index += 1;
		    addrCount += newRange.getAddressCount();
		}
	        Address ourLast = ourRange.getLastAddress();
	        Address otherLast = range.getLastAddress();
	        if (ourLast.compareTo(otherLast) > 0) {
	            Address newFirst = ((IPv4Address) otherLast).nextAddress();
	  	    AddressRange newRange = new AddressRange(newFirst, ourLast);
	  	    ranges.insertElementAt(newRange, index);
	            index += 1;
	            addrCount += newRange.getAddressCount();
	        }
            } else {
		if (!firstTime)
	  	done = true;
		index += 1;
      	    }
	    if (index == ranges.size())
	        done = true;
      	    firstTime = false;
        }
    }

    /**
     * Returns a string representation of this <code>AddressSet</code>.
     *
     * @return a string representation of this <code>AddressSet</code>
     */
    public String toString() {
        String output = "AddressSet with " + ranges.size() + " ranges:\n";
        Enumeration enum = getAddressRanges();
        while (enum.hasMoreElements()) {
            AddressRange range = (AddressRange) enum.nextElement();
            output = output + range;
        }
        return (output);
    }

    /**
     * Gets an <code>Enumeration</code> of <code>AddressRanges</code>
     * that are contained in this <code>AddressSet</code>.
     *
     * @return an <code>Enumeration</code> of <code>AddressRanges</code>
     *         that are contained in this <code>AddressSet</code>.
     */
    public Enumeration getAddressRanges() {
        return (ranges.elements());
    }

    /**
     * Gets the first <code>Address</code> in this <code>AddressSet</code>.
     *
     * @return the first <code>Address</code> in this <code>AddressSet</code>
     */
    public Address getFirstAddress() {
        return (((AddressRange) ranges.elementAt(0)).getFirstAddress());
    }

    /**
     * Gets the number of addresses in this <code>AddressSet</code>.
     *
     * @return the number of addresses in this <code>AddressSet</code>
     */
    public long getAddressCount() {
        return (addrCount);
    }

    /**
     * Gets the type of addresses in this <code>AddressSet</code>.
     *
     * @return the type of addresses in this <code>AddressSet</code>
     */
    public AddressType getAddressType() {
        return (addrType);
    }

    /**
     * sorted <code>Vector</code> of <code>AddressRanges</code>
     * that are contained in this <code>AddressSet</code>
     */
    private Vector ranges = null;

    /**
     * <code>AddressType</code>
     */
    private AddressType addrType = null;

    /**
     * Number of addresses
     */
    private long addrCount = 0;
    
    /**
     * Internationalization strings
     */
    private static ResourceBundle myResources;

}
