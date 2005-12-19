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
 * MulticastAddressAllocator.java
 */

package com.sun.multicast.allocation;

import java.util.Date;

/**
 * A multicast address allocator. This interface must be implemented by
 * any allocator that wishes to plug into the Multicast Address Management
 * Service Provider Interface (MAMSPI). Most applications should access
 * multicast address allocators through the MulticastAddressManager class.
 *
 * <P>Several different allocators will be supplied with JRMS 
 * (SASA, SAPA, etc.).
 * 
 * @see                         MulticastAddressManager
 */
public interface MulticastAddressAllocator {
    /**
     * Inform the allocator that the MulticastAddressManager is now managing it.
     * If this method throws an exception, the MulticastAddressManager will not
     * accept it for management.
     * @exception javax.jrms.addralloc.AddressAllocationException 
     *if the allocator does not want to be managed
     */
    void init() throws AddressAllocationException;

    /**
     * Inform the allocator that the MulticastAddressManager is no longer
     * managing it. There is no way for the allocator to reject this.
     */
    void term();

    /**
     * Get the name used to identify the allocator. The application may supply 
     * this name as an argument to MulticastAddressManager.allocAddress() and
     * MulticastAddressManager.findAllocator(). The name may not be null, should
     * be unique, and must remain the same during the lifetime of a
     * MulticastAddressAllocator object.
     *
     * @return the allocator name
     */
    String getAllocatorName();

    // @@@ Add a way to get notification of changes in the scope list
    /**
     * Get the multicast scope list.
     *
     * @return the multicast scope list
     * @exception AddressAllocationException if an exception was encountered
     */
    ScopeList getScopeList(AddressType addressType) throws 
	AddressAllocationException;

    /**
     * Allocate one or more multicast addresses, matching the specified 
     * parameters.
     *
     * <P>This method is used by the MulticastAddressManager when its 
     * allocateAddresses method has been called.
     *
     * @param scope the administrative scope requested
     * @param count the number of multicast addresses requested (usually one)
     * @param ttl the maximum ttl that will be used
     * @param requestedStartTime the requested start time (null if now)
     * @param requiredStartTime the latest acceptable start time (null if now)
     * @param requestedDuration the requested duration in seconds 
     * (-1 if indefinite)
     * @param requiredDuration the required duration in seconds 
     * (-1 if indefinite)
     * @param addressesRequested a requested address set (null if any will do)
     * @return the multicast address lease granted
     * @exception javax.jrms.addralloc.AddressAllocationException 
     * if an error occurred
     * @exception javax.jrms.addralloc.NoAddressAvailableException 
     * if no address was available that met the requirements
     */
    Lease allocateAddresses(Scope scope, int ttl, int count, 
        Date requestedStartTime, Date requiredStartTime, int requestedDuration, 
        int requiredDuration, AddressSet addressesRequested) throws 
        AddressAllocationException, NoAddressAvailableException;

}
