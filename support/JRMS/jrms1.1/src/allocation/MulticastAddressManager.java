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
 * MulticastAddressManager.java
 */

package com.sun.multicast.allocation;

import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import com.sun.multicast.util.Util;
import java.util.ResourceBundle;

/**
 * A multicast address manager. The MulticastAddressManager class is the primary
 * interface for multicast address management.
 *
 * <P>There is only one MulticastAddressManager object per Java VM. To get it,
 * use the getMulticastAddressManager static method.
 *
 */
public class MulticastAddressManager {
    private static MulticastAddressManager theMAM = null;
    private Hashtable allocatorList;
    private Thread allocatorWriter;
    private Vector allocatorReaders;
    private ScopeList scopes;
    private boolean needScopeUpdate;
    private static ResourceBundle myResources;

    /**
     * Get the MulticastAddressManager object for this Java VM.
     * @return the MulticastAddressManager object for this Java VM
     */
    public static synchronized MulticastAddressManager 
	getMulticastAddressManager() {

	if (theMAM == null)
	    theMAM = new MulticastAddressManager();
	return (theMAM);
    }

    /**
     * Create a new MulticastAddressManager. This constructor is protected
     * because it should only be used by getMulticastAddressManager() and
     * possible future subclasses of MulticastAddressManager.
     */
    protected MulticastAddressManager() {
	allocatorList = new Hashtable();
	allocatorReaders = new Vector();
	scopes = null;
	needScopeUpdate = true;
	ResourceBundle myResources = ResourceBundle.
        getBundle("com.sun.multicast.allocation.resources.AllocationResources");
    }

    // @@@ Add a way to get notification of changes in the scope list
    /**
     * Get the multicast scope list. If an address type is specified, 
     * only scopes with that address type will be included. 
     * Otherwise, all available scopes will be included.
     *
     * @param addressType the <code>AddressType</code> requested (null if none)
     * @exception AddressAllocationException if an exception occurs
     * @return the multicast scope list
     */
    public ScopeList getScopeList(AddressType addressType) throws 
	AddressAllocationException {

	// Update scope list, if necessary
        if (needScopeUpdate) {
            grabWriter();
	    try {
          	scopes = new ScopeList();
          	MulticastAddressAllocator allocator;
	    	Enumeration allocators = allocatorList.elements();
	    	while (allocators.hasMoreElements()) {
		    allocator = (MulticastAddressAllocator)
		    allocators.nextElement();
            	    scopes = scopes.merge(
		    allocator.getScopeList(addressType));
          	}
                needScopeUpdate = false;
	    } finally {
	        ungrabWriter();
	    }
        }
        
        return (scopes);
    }

    /**
     * Allocate one or more multicast addresses, matching the specified 
     * parameters. Each allocator is asked to meet the request until one does.
     * If no allocator can meet the request, NoAddressAvailableException is 
     * thrown. No provision is made for a single request being satisfied 
     * by several different allocators.
     * @param allocatorName the name of a specific allocator requested; 
     * null if none (recommended)
     * @param scope the administrative scope requested
     * @param ttl the maximum ttl that will be used
     * @param count the number of multicast addresses requested (usually one)
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
    public Lease allocateAddresses(String allocatorName, Scope scope, int ttl,
	int count, Date requestedStartTime, Date requiredStartTime,
	int requestedDuration, int requiredDuration,
	AddressSet addressesRequested)
	throws AddressAllocationException, NoAddressAvailableException {
	Lease lease = null;
	

    	// Check arguments
	if (count < 1) {
	    throw new IllegalArgumentException(myResources.getString(
		"countLTone"));
	}
	grabReader();
	try {
	    MulticastAddressAllocator allocator = null;

    	    if (allocatorName != null) {
		allocator = findAllocator(allocatorName);
		lease = allocator.allocateAddresses(scope, ttl, count,
		  requestedStartTime, requiredStartTime, requestedDuration,
		  requiredDuration, addressesRequested);
	    } else {
		Enumeration allocators = allocatorList.elements();
		while ((lease == null) && allocators.hasMoreElements()) {
		    allocator = (MulticastAddressAllocator)
			allocators.nextElement();
		    try {
			lease = allocator.allocateAddresses(scope, ttl, count, 
			    requestedStartTime, requiredStartTime, 
			    requestedDuration, requiredDuration,
			    addressesRequested);
		    } catch (AddressAllocationException e) {}
		}

    		if (lease == null)
		    throw new NoAddressAvailableException();
	    }
	} finally {
	    ungrabReader();
	}
	return (lease);
    }

    /**
     * Add a MulticastAddressAllocator to the list of active allocators. 
     * If there is already an allocator with the same name in the list, 
     * it is removed first.
     * @param allocator the new allocator
     * @exception javax.jrms.addralloc.AddressAllocationException if 
     * the request could not be satisfied
     */
    public void addAllocator(MulticastAddressAllocator allocator)
	throws AddressAllocationException {
	String allocatorName = allocator.getAllocatorName();
	grabWriter();
	try {
	    MulticastAddressAllocator oldAllocator;
	    oldAllocator = (MulticastAddressAllocator)
		allocatorList.get(allocatorName);
	    if (oldAllocator != allocator) {
		if (oldAllocator != null)
		internalRemoveAllocator(oldAllocator);
		allocator.init();
		allocatorList.put(allocatorName, allocator);
            needScopeUpdate = true;
	    }
	} finally {
	    ungrabWriter();
	}
    }

    /**
     * Remove a MulticastAddressAllocator from the list of active allocators. 
     * If the allocator was not on the list, nothing is done.
     * @param allocator the allocator to be removed
     * @exception javax.jrms.addralloc.AddressAllocationException 
     * if the request could not be satisfied
     */
    public void removeAllocator(MulticastAddressAllocator allocator)
	throws AddressAllocationException {

	// Get exclusive access to the allocatorList.
	grabWriter();
	try {
	    // Use private method to do removal.
	    internalRemoveAllocator(allocator);
          needScopeUpdate = true;
	} finally {
	    // Make sure we *always* give back our exclusive access.
	    ungrabWriter();
	}
    }

    /**
     * Private method used to remove a MulticastAddressAllocator from 
     * the list of active allocators.
     * If the allocator was not on the list, nothing is done. 
     * This method assumes that the write lock has already been grabbed.
     *
     * <P>This method is separate from removeAllocator so that it can be 
     * shared between removeAllocator and addAllocator.
     * @param allocator the allocator to be removed
     * @exception javax.jrms.addralloc.AddressAllocationException 
     * if the request could not be satisfied
     */
    private void internalRemoveAllocator(MulticastAddressAllocator allocator)
	throws AddressAllocationException {
	String allocatorName = allocator.getAllocatorName();

	// If allocator isn't in list, throw an exception
	if (allocatorList.get(allocatorName) != allocator)
	    throw new AddressAllocationException();

	// Tell allocator that we're killing it. Don't take no for an answer.
	try {
	    allocator.term();
	} catch (Exception e) {}

	// Remove allocator from allocator list.
	allocatorList.remove(allocatorName);
    }

    /**
     * Get the list of active allocators. The Enumeration returned is a snapshot
     * of the allocator list. It will not be updated if allocators are added or
     * removed.
     * @return an Enumeration of the list of allocators
     * @exception javax.jrms.addralloc.AddressAllocationException 
     * if the request could not be satisfied
     */
    public Enumeration getAllocators()
	throws AddressAllocationException {
	Enumeration result = null;

	grabReader();
	try {
	    result = ((Hashtable) allocatorList.clone()).elements();
	} finally {
	    ungrabReader();
	}
	return (result);
    }

    /**
     * Find the allocator with the specified name.
     * @param name name to be found
     * @return the allocator with the specified name (null if not found)
     * @exception javax.jrms.addralloc.AddressAllocationException 
     * if the request could not be satisfied
     */
    public MulticastAddressAllocator findAllocator(String name)
	throws AddressAllocationException {
	MulticastAddressAllocator result = null;

	grabReader();
	try {
	    result = (MulticastAddressAllocator) allocatorList.get(name);
	} finally {
	    ungrabReader();
	}
	return (result);
    }

    private void grabReader() throws AddressAllocationException {
	Thread thisThread = Thread.currentThread();
	synchronized (allocatorList) {
	    while (allocatorWriter != null) {
		if (!allocatorReaders.isEmpty() || 
		    (allocatorWriter == thisThread)) {

		    throw new AddressAllocationException();
		}
		try {
		    allocatorList.wait();
		} catch (InterruptedException e) {}
	    }
	    allocatorReaders.addElement(thisThread);
	}
    }

    private void ungrabReader() throws AddressAllocationException {
	Thread thisThread = Thread.currentThread();
	synchronized (allocatorList) {
	    if (!allocatorReaders.contains(thisThread) || 
		(allocatorWriter != null)) {
		throw new AddressAllocationException();
	    }
	    allocatorReaders.removeElement(thisThread);
	    if (allocatorReaders.isEmpty())
	        allocatorList.notifyAll();
	}
    }

    private void grabWriter() throws AddressAllocationException {
	Thread thisThread = Thread.currentThread();
	synchronized (allocatorList) {
	    while ((allocatorWriter != null) || !allocatorReaders.isEmpty()) {
		if ((allocatorWriter == thisThread) || 
		    allocatorReaders.contains(thisThread)) {

		    throw new AddressAllocationException();
		}
		try {
		    allocatorList.wait();
		} catch (InterruptedException e) {}
	    }
	    allocatorWriter = thisThread;
	}
    }

    private void ungrabWriter() throws AddressAllocationException {
	Thread thisThread = Thread.currentThread();
	synchronized (allocatorList) {
	    if ((allocatorWriter != thisThread) || !allocatorReaders.isEmpty())
		throw new AddressAllocationException();
	    allocatorWriter = null;
	    allocatorList.notifyAll();
	}
    }
}
