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
 * Lease.java
 */

package com.sun.multicast.allocation;

import java.util.Date;

/**
 * A multicast address lease. This object represents a lease that allows
 * a set of multicast addresses to be used for a period of time.
 */
public interface Lease {

    /**
     * Gets the multicast addresses associated with this lease.
     * @return an <code>AddressSet</code> representing the multicast
     *         addresses associated with this lease (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    AddressSet getAddresses() throws AddressAllocationException;

    /**
     * Gets the start time of this lease.
     * @return the start time of this lease (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    Date getStartTime() throws AddressAllocationException;

    /**
     * Gets the duration of this lease.
     *
     * @return the duration of this lease in seconds (-1 if indefinite,
     *                 -2 for unknown)
     * @exception AddressAllocationException if an error occurs
     */
    int getDuration() throws AddressAllocationException;

    /**
     * Gets the Scope associated with this lease.
     * @return the <code>Scope</code> associated with this lease
     *         (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    Scope getScope() throws AddressAllocationException;

    /**
     * Requests a change in the start time of this lease.
     *
     * @param requestedStartTime the requested start time (null if now)
     * @param requiredStartTime the latest acceptable start time (null if now)
     * @exception AddressAllocationException if the request could not 
     * be satisfied
     */
    void setStartTime(Date requestedStartTime, Date requiredStartTime)
        throws AddressAllocationException;

    /**
     * Requests a change in the duration of this lease.
     *
     * @param requestedDuration the requested duration in seconds 
     * (-1 if indefinite)
     * @param requiredDuration the required duration in seconds 
     * (-1 if indefinite)
     * @exception AddressAllocationException if the request could not 
     * be satisfied
     */
    void setDuration(int requestedDuration, int requiredDuration)
        throws AddressAllocationException;

    /**
     * Returns <code>true</code> if the lease has been released.
     *
     * @return <code>true</code> if the lease has been released,
     *         <code>false</code> otherwise
     * @exception AddressAllocationException if an error occurs
     */
    boolean getReleased() throws AddressAllocationException;

    /**
     * Releases this lease. If this method succeeds, the address allocation
     * represented by this lease will no longer be valid. The addresses
     * may be used for other leases, so they should no longer be used in
     * relation to this lease. The <code>getReleased</code> method will
     * return <code>true</code>. Other methods may throw exceptions.
     *
     * @exception AddressAllocationException if the request could not 
     * be satisfied
     */
    void release()
        throws AddressAllocationException;

}
