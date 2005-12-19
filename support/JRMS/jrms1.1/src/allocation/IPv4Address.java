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
 * IPv4Address.java
 */

package com.sun.multicast.allocation;

import java.net.InetAddress;
import com.sun.multicast.util.Util;

/**
 * An IPv4 network address.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public class IPv4Address implements Address {

    /**
     * Creates an <code>AddressRange</code> with the specified addresses.
     * @param startAddress the first address in the range
     * @param endAddress the last address in the range
     * @return an <code>AddressRange</code> with the specified addresses
     */
    public IPv4Address(InetAddress inetAddr) {
        addrInt = Util.InetAddressToInt(inetAddr);
        addrLong = addrInt;
        addrBytes = inetAddr.getAddress();
        addrInetAddr = inetAddr;
        addrType = IPv4AddressType.getAddressType();
    }

    private int sign(long value) {
        if (value < 0) {
            return (-1);
        } else {
            if (value > 0)
                return (1);
            else
                return (0);
        }
    }

    /**
     * Compares this <code>Address</code> with the specified
     * object for order. Returns a negative integer, zero, or
     * a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     *
     * <P>If this address is less than the other one, an integer less
     * than zero is returned. If this address is greater than the
     * other one, an integer greater than zero is returned. If the
     * two addresses are equal, zero is returned. If the two addresses
     * cannot be compared (usually because they are of different
     * <code>AddressTypes</code>), a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on addresses of the same
     * <code>AddressType</code>.
     *
     * @param o the <code>Object</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the objects cannot be compared
     */
    public int compareTo(Object o) throws ClassCastException {
        IPv4Address otherAddress = (IPv4Address) o;
        return (sign(addrLong - otherAddress.toLong()));
    }

    /**
     * Indicates whether some other object is "equal to" this one. Two
     * <code>IPv4Addresses</code> are equal if and only if they represent
     * the same address.
     *
     * @param obj the object with which to compare
     * @return <code>true</code> if this object is the same as the
     *         reference object, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof IPv4Address) {
            IPv4Address otherAddr = (IPv4Address) obj;
            return (otherAddr.toInt() == addrInt);
        } else
            return (false);
    }

    /**
     * Returns a hash code value for this object. The hash code values
     * for two <code>IPv4Addresses</code> are equal if and only if they 
     * are equal.
     *
     * @return a hash code value for this <code>IPv4Address</code>
     */
    public int hashCode() {
        return (addrInt);
    }

    /**
     * Returns an int value for the address.
     *
     * @return an int value for this <code>IPv4Address</code>
     */
    public int toInt() {
        return (addrInt);
    }

    /**
     * Returns a long value for the address.
     *
     * @return a long value for this <code>IPv4Address</code>
     */
    public long toLong() {
        return (addrLong);
    }

    /**
     * Returns an InetAddress value for the address.
     *
     * @return an InetAddress value for this <code>IPv4Address</code>
     */
    public InetAddress toInetAddress() {
        return (addrInetAddr);
    }

    /**
     * Returns the previous <code>IPv4Address</code>.
     *
     * @return the previous <code>IPv4Address</code>
     */
    public IPv4Address previousAddress() {
        return (new IPv4Address(Util.intToInetAddress((int) (addrLong-1))));
    }

    /**
     * Returns the next <code>IPv4Address</code>.
     *
     * @return the next <code>IPv4Address</code>
     */
    public IPv4Address nextAddress() {
        return (new IPv4Address(Util.intToInetAddress((int) (addrLong+1))));
    }

    /**
     * Returns the difference between this <code>Address</code>
     * and another one. If this address is less than the other one, a
     * value greater than zero is returned. If this address is greater than
     * the other one, a value less than zero is returned. If the
     * two addresses are equal, zero is returned.
     *
     * @param otherAddress the <code>Address</code> to do a difference with
     * @return the difference between this <code>Address</code> and 
     * the other one
     */
    public long difference(Address otherAddress) {
        return (addrLong - ((IPv4Address) otherAddress).toLong());
    }

    /**
     * Gets a byte array representing this <code>Address</code>.
     * @return a byte array representing this <code>Address</code>
     */
    public byte [] getBytes() {
        return (addrBytes);
    }

    /**
     * Gets the <code>AddressType</code> of this <code>Address</code>.
     * @return the <code>AddressType</code> of this <code>Address</code>
     */
    public AddressType getAddressType() {
        return (addrType);
    }

    /**
     * Returns a string representation of this <code>IPv4Address</code>.
     *
     * @return a string representation of this <code>IPv4Address</code>
     */
    public String toString() {
        return (addrInetAddr.getHostAddress());
    }

    /**
     * The address as an int.
     */
    private int addrInt;

    /**
     * The address as a long.
     */
    private long addrLong;

    /**
     * The address as a byte array.
     */
    private byte [] addrBytes;

    /**
     * The address as an InetAddress.
     */
    private InetAddress addrInetAddr;

    /**
     * The AddressType.
     */
    private AddressType addrType;

}
