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
 * StaticAllocatorLease.java
 */

package com.sun.multicast.allocation;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import com.sun.multicast.util.BASE64Encoder;

/**
 * A Lease to be returned by the StaticAllocator. This is a package-local
 * class, since it should only be created by the StaticAllocator. Other
 * code may call the methods exposed through the Lease interface.
 *
 * @see                         Lease
 */
class StaticAllocatorLease implements Lease {

    /**
     * Create a new StaticAllocatorLease with the specified parameters.
     *
     * @param leaseIdentifier the lease identifier used to allocate
     *                         the lease
     * @param scope            the <code>Scope</code> associated with
     *                         the lease
     * @param addresses an <code>AddressSet</code> representing the multicast
     *         addresses associated with this lease (null if unknown)
     * @param startTime the start time of this lease (null if unknown)
     * @param duration the duration of this lease in seconds (-1 if indefinite,
     *                 -2 for unknown)
     * @param allocator the allocator that allocated this lease
     * @exception AddressAllocationException if the lease
     * could not be created
     */
    StaticAllocatorLease(byte [] leaseIdentifier, Scope scope,
	AddressSet addresses, Date startTime, int duration, 
        StaticAllocator allocator) throws AddressAllocationException {

        leaseID = leaseIdentifier;
        this.scope = scope;
        this.addresses = addresses;
        this.startTime = startTime;
        this.duration = duration;
        this.allocator = allocator;
    }

    /**
     * Gets the multicast addresses associated with this lease.
     * @return an <code>AddressSet</code> representing the multicast
     *         addresses associated with this lease (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    public AddressSet getAddresses()
        throws AddressAllocationException {
        return (addresses);
    }

    /**
     * Gets the start time of this lease.
     * @return the start time of this lease (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    public Date getStartTime()
        throws AddressAllocationException {
        return (startTime);
    }

    /**
     * Gets the duration of this lease.
     * @return the duration of this lease in seconds (-1 if indefinite,
     *                 -2 for unknown)
     * @exception AddressAllocationException if an error occurs
     */
    public int getDuration()
        throws AddressAllocationException {
        return (duration);
    }

    /**
     * Gets the Scope associated with this lease.
     * @return the <code>Scope</code> associated with this lease
     *         (null if unknown)
     * @exception AddressAllocationException if an error occurs
     */
    public Scope getScope()
        throws AddressAllocationException {
        return (scope);
    }

    /**
     * Requests a change in the start time of this lease.
     *
     * @param requestedStartTime the requested start time (null if now)
     * @param requiredStartTime the latest acceptable start time (null if now)
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    public void setStartTime(Date requestedStartTime, Date requiredStartTime)
        throws AddressAllocationException {
        allocator.requestSetStartTime(this, requestedStartTime,
	    requiredStartTime);
    }

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
    public void setDuration(int requestedDuration, int requiredDuration)
        throws AddressAllocationException {
        allocator.requestSetDuration(this, requestedDuration, requiredDuration);
    }

    /**
     * Returns <code>true</code> if the lease has been released.
     *
     * @return <code>true</code> if the lease has been released,
     *         <code>false</code> otherwise
     * @exception AddressAllocationException if an error occurs
     */
    public boolean getReleased()
        throws AddressAllocationException {
        return (released);
    }

    /**
     * Releases this lease. If this method succeeds, the address allocation
     * represented by this lease will no longer be valid. The addresses
     * may be used for other leases, so they should no longer be used in
     * relation to this lease. The <code>getReleased</code> method will
     * return <code>true</code>. Other methods may throw exceptions.
     *
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    public void release()
        throws AddressAllocationException {
        allocator.requestRelease(this);
    }

    /**
     * Sets the released flag for this lease. This method is package local
     * because it should only be called by StaticAllocator.
     *
     * @param released the new value for the released flag
     */
    void setReleased(boolean released) {
        this.released = released;
    }

    /**
     * Returns a string representation of this 
     * <code>StaticAllocatorLease</code>.
     *
     * @return a string representation of this <code>StaticAllocatorLease</code>
     */
    public String toString() {
        String output = "StaticAllocatorLease with lease ID:\n";
        try {
            output = output + new String(BASE64Encoder.encode(leaseID), 
		"UTF8") + "\n";
        } catch (UnsupportedEncodingException e) { }
        output = output + " AddressSet " + addresses + "\n";
        output = output + " Scope " + scope + "\n";
        output = output + " Start Time " + startTime + "\n";
        output = output + " Duration " + duration + "\n";
        output = output + " Released " + released + "\n";
        return (output);
    }

    /** 
     * the lease identifier
     */
    private byte [] leaseID = null;

    /** 
     * the scope
     */
    private Scope scope = null;

    /** 
     * an <code>AddressSet</code> representing the multicast
     *         addresses associated with this lease (null if unknown)
     */
    private AddressSet addresses = null;

    /**
     * The start time of this lease.
     */
    private Date startTime = null;

    /**
     * The duration of this lease.
     */
    private int duration = 0;

    /**
     * The released flag
     */
    private boolean released = false;

    /**
     * The allocator
     */
    private StaticAllocator allocator = null;

}
