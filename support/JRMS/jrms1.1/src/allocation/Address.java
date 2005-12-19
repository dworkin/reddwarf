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
 * Address.java
 */

package com.sun.multicast.allocation;

/**
 * A network address.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public interface Address {
    
    /**
     * Compares this <code>Address</code> with another
     * <code>Object</code> for order. Returns a negative integer, zero, or
     * a positive integer depending on whether this
     * <code>Address</code> is less than, equal
     * to, or greater than the other <code>Object</code>.
     *
     * <P>If this address is less than the other one, an integer less
     * than zero is returned. If this address is greater than the
     * other one, an integer greater than zero is returned. If the
     * two addresses are equal, zero is returned. If the two addresses
     * cannot be compared because they are of different
     * <code>AddressTypes</code>, a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on addresses of the same
     * <code>AddressType</code>.
     *
     * @param otherAddress the <code>Address</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the <code>Addresses</code> 
     * are of different types
     */
    int compareTo(Object o) throws ClassCastException;

    /**
     * Gets a byte array representing this <code>Address</code>.
     * @return a byte array representing this <code>Address</code>
     */
    byte [] getBytes();

    /**
     * Gets the <code>AddressType</code> of this <code>Address</code>.
     * @return the <code>AddressType</code> of this <code>Address</code>
     */
    AddressType getAddressType();

}
