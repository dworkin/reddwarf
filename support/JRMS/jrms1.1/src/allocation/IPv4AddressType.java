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
 * IPv4AddressType.java
 */

package com.sun.multicast.allocation;

/**
 * The IPv4 address type. Addresses are 32-bit unsigned integers.
 *
 * <P>There is just one singleton object of this class per JVM.
 * This object can be accessed with the static getAddressType method.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public class IPv4AddressType implements AddressType {

    /**
     * Creates an <code>IPv4AddressType</code>.
     * @return an <code>IPv4AddressType</code>
     */
    private IPv4AddressType() {
    }

    /**
     * Gets the singleton <code>IPv4AddressType</code> object.
     * @return the singleton <code>IPv4AddressType</code> object
     */
    public static IPv4AddressType getAddressType() {
        if (addrType == null)
            addrType = new IPv4AddressType();
        return (addrType);
    }

    /**
     * Returns the IANA-assigned address family code for this address type.
     *
     * @return the IANA-assigned address family code for this address type
     */
    public int getIANAID() {
        return (1);
    }

    /**
     * Singleton IPv4AddressType
     */
    private static IPv4AddressType addrType = null;

}
