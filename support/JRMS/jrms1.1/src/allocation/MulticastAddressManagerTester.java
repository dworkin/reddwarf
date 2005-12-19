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
 * MulticastAddressManagerTester.java
 */

package com.sun.multicast.allocation;

import java.io.File;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import com.sun.multicast.util.TestFailedException;
import com.sun.multicast.util.Util;

/**
 * A tester for the address allocation code.  Feel free to expand upon it.
 *
 * This is not part of the public interface of the address allocation code.
 * It's a package-local class.
 */
class MulticastAddressManagerTester {
    /**
     * Create a new MulticastAddressManagerTester object. This should only be
     * called from MulticastAddressManagerTester.main().
     * @exception java.lang.Exception if an error occurs
     */
    MulticastAddressManagerTester() throws Exception {
	mam = MulticastAddressManager.getMulticastAddressManager();
	Properties allocProps = new Properties();
	allocProps.put("Scope-1", 
	    "239.255.0.0-239.255.255.255 7 \"Local Scope\" en");
	mam.addAllocator(new StaticAllocator(allocProps));
    }

    /**
     * Test fetching a scope list.
     * @exception java.lang.Exception if the test fails
     */
    void testScopeList() throws Exception {
	ScopeList sl = mam.getScopeList(IPv4AddressType.getAddressType());
	//	System.out.println("Scope list:" + sl);
    }

    /**
     * Test allocating a few addresses.
     * @exception java.lang.Exception if the test fails
     */
    void testAllocate() throws Exception {
	ScopeList sl = mam.getScopeList(IPv4AddressType.getAddressType());
	Scope scope = (Scope) sl.getScopes().nextElement();
	Lease lease = mam.allocateAddresses(null, scope, 1, 1, null, null,
                        -1, -1, null);
	//	System.out.println("Lease: " + lease);
    }

    /**
     * Do any necessary cleanup.
     * @exception java.lang.Exception if the test fails
     */
    void close() {
    }

    MulticastAddressManager mam;

    /**
     * Perform the Address Allocation Test.
     * @param args command line arguments (ignored for now).
     */
    public static void main(String[] args) {
	System.out.println("Address Allocation Test starting.");
	boolean succeeded = true;
	MulticastAddressManagerTester ct = null;
	try {
	    ct = new MulticastAddressManagerTester();
	    ct.testScopeList();
	    ct.testAllocate();
	} catch (Throwable t) {
	    try {
		ct.close();
	    } catch (Exception e2) { }
	    t.printStackTrace();
	    succeeded = false;
	}
        if (succeeded)
	    System.out.println("Address Allocation Test succeeded.");
	else
	    System.out.println("Address Allocation Test failed.");
    }
}

