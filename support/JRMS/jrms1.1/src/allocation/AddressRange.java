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
 * AddressRange.java
 */

package com.sun.multicast.allocation;

import java.util.ResourceBundle;

/**
 * A range of network addresses.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public class AddressRange {

    /**
     * Creates an <code>AddressRange</code> with the specified addresses.
     * @param startAddress the first address in the range
     * @param endAddress the last address in the range
     * @return an <code>AddressRange</code> with the specified addresses
     */
    public AddressRange(Address firstAddress, Address lastAddress) {
        ResourceBundle myResources = ResourceBundle.
        getBundle("com.sun.multicast.allocation.resources.AllocationResources");
        if (!(firstAddress instanceof IPv4Address) ||
	    !(lastAddress instanceof IPv4Address)) {

	    throw new IllegalArgumentException(
		myResources.getString("onlyIPv4"));
	}
        first = (IPv4Address) firstAddress;
        last = (IPv4Address) lastAddress;
        // if (!addrType.equals(last.getAddressType()))
        //   throw new IllegalArgumentException(myResources.getString("mixed"));
	if (first.compareTo(last) > 0) {
	    throw new IllegalArgumentException(
		myResources.getString("firstGTlast"));
	}
	addrCount = last.difference(first) + 1;
    }

    /**
     * Gets the first address in this <code>AddressRange</code>.
     * @return the first address in this <code>AddressRange</code>
     */
    public Address getFirstAddress() {
        return (first);
    }

    /**
     * Gets the last address in this <code>AddressRange</code>.
     * @return the last address in this <code>AddressRange</code>
     */
    public Address getLastAddress() {
        return (last);
    }

    /**
     * Gets the number of addresses in this <code>AddressRange</code>.
     * @return the number of addresses in this <code>AddressRange</code>
     */
    public long getAddressCount() {
        return (addrCount);
    }

    /**
     * Gets the type of addresses in this <code>AddressRange</code>.
     * @return the type of addresses in this <code>AddressRange</code>
     */
    public AddressType getAddressType() {
        return (addrType);
    }

    /**
     * Compares this <code>AddressRange</code> with the specified
     * object for order. Returns a negative integer, zero, or
     * a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     *
     * <P><code>AddressRanges</code> are ordered first on the basis
     * of first address and then on the basis of last address.
     * That is, the first addresses in the ranges are compared. If they
     * are not equal, this is the result of the range comparison. If
     * they are equal, the last address ranges are compared and this
     * is the result of the comparison. If the two <code>AddressRanges</code>
     * cannot be compared (usually because they are of different
     * <code>AddressTypes</code>), a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on addresses of the same
     * <code>AddressRange</code>.
     *
     * @param o the <code>Object</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the objects cannot be compared
     */
    public int compareTo(Object o) throws ClassCastException {
        AddressRange otherRange = (AddressRange) o;
        int firstResult = first.compareTo(otherRange.getFirstAddress());
        if (firstResult != 0)
            return (firstResult);
        else
            return (last.compareTo(otherRange.getLastAddress()));
    }

    /**
     * Checks whether this <code>AddressRange</code> overlaps with another.
     *
     * <P>If the two <code>AddressRanges</code> are of different
     * <code>AddressTypes</code>, a <code>ClassCastException</code> is thrown.
     *
     * @param otherRange the <code>AddressRange</code> to check for overlap with
     * @return <code>true</code> if this <code>AddressRange</code>
     *         overlaps with <code>otherRange</code>,
     *         <code>false</code> otherwise.
     */
    public boolean overlaps(AddressRange otherRange) throws ClassCastException {
        return ((first.compareTo(otherRange.getLastAddress()) <= 0) &&
        (last.compareTo(otherRange.getFirstAddress()) >= 0));
    }

    /**
     * Checks whether this <code>AddressRange</code> contains a given
     * <code>Address</code>.
     *
     * <P>If the <code>Address</code> is not of the same
     * <code>AddressType</code> as this <code>AddressRange</code>,
     * a <code>ClassCastException</code> is thrown.
     *
     * @param address the <code>Address</code> to check for
     * @return <code>true</code> if this <code>AddressRange</code>
     *         contains the <code>Address</code>,
     *         <code>false</code> otherwise.
     */
    public boolean contains(Address address) throws ClassCastException {
        return first.compareTo(address) <= 0 && last.compareTo(address) >= 0;
    }

    /**
     * Returns an <code>AddressRange</code> represents the merger of this
     * <code>AddressRange</code> with <code>otherRange</code>. If the two ranges
     * do not overlap, the result will also include addresses that were not in
     * either of the ranges. This method does not affect the values of either
     * this <code>AddressRange</code> or <code>otherRange</code>.
     *
     * <P>If the two <code>AddressRanges</code> are of different
     * <code>AddressTypes</code>, a <code>ClassCastException</code> is thrown.
     *
     * @param otherRange the <code>AddressRange</code> to merge with
     * @return an <code>AddressRange</code> that represents the merger of this
     *         <code>AddressRange</code> with <code>otherRange</code>
     */
    public AddressRange merge(AddressRange otherRange) throws 
	ClassCastException {

        Address otherFirst = otherRange.getFirstAddress();
        Address otherLast = otherRange.getLastAddress();
        Address newFirst;
        Address newLast;

        if (first.compareTo(otherFirst) <= 0)
            newFirst = first;
        else
            newFirst = otherFirst;
        if (last.compareTo(otherLast) >= 0)
            newLast = last;
        else
            newLast = otherLast;

        return new AddressRange(newFirst, newLast);
    }

    /**
     * Indicates whether some other object is "equal to" this one. Two
     * <code>AddressRanges</code> are equal if and only if they have the same
     * first and last addresses.
     *
     * @param obj the object with which to compare
     * @return <code>true</code> if this object is the same as the
     *         reference object, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof AddressRange) {
            AddressRange otherRange = (AddressRange) obj;
            return (first.equals(otherRange.getFirstAddress())) &&
                (last.equals(otherRange.getLastAddress()));
	} else
	    return (false);
    }

    /**
     * Returns a hash code value for the <code>AddressRange</code>.
     * The hash code values for two <code>AddressRanges</code> are equal
     * if they are equal. However, it may be possible for two unequal
     * <code>AddressRanges</code> to have the same hash code.
     *
     * @return a hash code value for this <code>AddressRange</code>
     */
    public int hashCode() {
        return (first.hashCode() + last.hashCode());
    }

    /**
     * Returns a string representation of this <code>AddressRange</code>.
     *
     * @return a string representation of this <code>AddressRange</code>
     */
    public String toString() {
        return ("AddressRange " + first + " - " + last + "\n");
    }

    /**
     * First address
     */
    private IPv4Address first;

    /**
     * Last address
     */
    private IPv4Address last;

    /**
     * <code>AddressType</code>
     */
    private AddressType addrType = null;

    /**
     * Number of addresses
     */
    private long addrCount = 0;
  
    /**
     * Internationalized strings
     */
    private static ResourceBundle myResources;
    
}
