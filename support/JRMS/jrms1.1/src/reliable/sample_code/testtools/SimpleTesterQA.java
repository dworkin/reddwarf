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
 * SimpleTesterQA.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.net.InetAddress;
import java.util.Date;
import java.util.Properties;
import com.sun.multicast.allocation.MulticastAddressManager;
import com.sun.multicast.allocation.StaticAllocator;
import com.sun.multicast.util.TestFailedException;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.simple.*;

/*
 * A tester for the simple objects.  Feel free to expand upon it.
 * 
 * This is not part of the public interface of the simple objects. 
 * It's a package-local
 * class.
 * 
 */

/** 
 * Creates a stream socket send or receiver. Does not use rmi and 
 * therefore cannot be used to create real time graphing. The standard
 * logfiles are generated though.
 */

class SimpleTesterQA {
    private static int receivers = 1; 
    private static int intsent = 100000;
    private static String method = "";
    private static String channelFileName = "";
    private static boolean verbose = false;
    SimpleTesterQA() {
        mam = MulticastAddressManager.getMulticastAddressManager();
    }

    static final int DOT = 46;

    MulticastAddressManager mam;

    /*
     * Test SimpleSender. If the test fails, throw an exception.
     * 
     * <P> Create a SimpleSender to send in 30 seconds. Send some text.
     * Close the SimpleSender.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testSimpleSender(String fn) throws Exception {
        SimpleSender ss;
        Date start = new Date(System.currentTimeMillis() + 10000);

        System.out.println("About to create SimpleSender.");

	if (fn != null) {
	    System.out.println("Creating a Simple Sender");
	    ss = new SimpleSender(fn, verbose);
	} else {
	    System.out.println("This is appname: " + appName);
	    System.out.println("This is channelName: " + channelName);
	    System.out.println("This is start: " + start.toString());
            ss = new SimpleSender(appName, channelName, start, null, 
                                           null, (byte) 1);
	}

	if (verbose) {
		System.out.println(Integer.toString(
		    ss.getTRAMTransportProfile().getLogMask()));
		ss.getTRAMTransportProfile().setLogMask(
		    TRAMTransportProfile.LOG_VERBOSE);
		System.out.println(Integer.toString(
		    ss.getTRAMTransportProfile().getLogMask()));
	}

	HostNameManager hostnamemanager = 
	    HostNameManager.getHostNameManager();
	
	if (hostnamemanager.isHostFile()) {
	    receivers = hostnamemanager.getHostCount();
	}
	
        System.out.println("Waiting for " + Integer.toString(receivers) + 
	    " receiver(s).");
        ss.waitTill(receivers);
        System.out.println(Integer.toString(receivers) + 
	    " receiver(s) detected");

        System.out.println("About to wait 10 seconds.");
        ss.waitTill(start);
        System.out.println("About to send data.");

        DataOutputStream dos = new DataOutputStream(ss.getOutputStream());

	int counter = 0;
	int total = 0;

	for (int i = 0; i < intsent; i++) {
            dos.writeInt(counter);
	    counter++;
	    counter = counter % 256;
	}
        dos.close();
	System.out.println("size = " + Integer.toString(dos.size()));
        ss.close();
    }

    /*
     * Test SimpleReceiver. If the test fails, throw an exception.
     * 
     * <P> Create a SimpleReceiver. Receive some text and verify 
     * that it's correct.
     * Close the SimpleReceiver.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testSimpleReceiver(String fn) throws Exception {
        SimpleReceiver sr;
	boolean firstTime = true;
        System.out.println("About to create SimpleReceiver.");

	if (fn != null) {
	    sr = new SimpleReceiver(fn);
	} else {
            sr = new SimpleReceiver(appName, channelName);
	}
	// set verbose logging if requested
	if (verbose) {
	    sr.getTRAMTransportProfile().setLogMask(
	    TRAMTransportProfile.LOG_VERBOSE);
	}
	
        System.out.println("About to get InputStream.");

        DataInputStream dis = new DataInputStream(sr.getInputStream());
	System.out.println("Finished getting InputStream.");

        boolean eof = false;

	int counter = 0;
	int total = 0;

	// To accomodate late joiners, we have to accept the first
	// byte of the first packet as being correct.  From there we
	// can determine the correctness of all the rest of the data.

	int i = 0;

        while (true) {
	    try { 
		if (firstTime) {
		    counter = dis.readInt();
		    firstTime = false;
		} else {
            	    i =  dis.readInt();
		}

            	if (i != counter) {
		    System.out.println("int compare test failed.");
		    System.out.println("String received " + 
			Integer.toString(i) +  
			" does not match." + Integer.toString(counter));	
            	}
	    	total++;
	    	counter++;
	    	if (counter == 255) { 
	     	    System.out.println("Total = " + Integer.toString(total) + 
			" bytes. Test passed.");
	    	}
	    	counter = counter % 256;

	    } catch (EOFException e) {
		System.out.println("int compare test finished after " 
		    + Integer.toString(counter));
		System.out.println("Total ints compared = " + 
		    Integer.toString(total));
		if (total != intsent) {
		    System.out.println("Test failed.");
		    System.out.println("Total ints compared did not equal " + 
			Integer.toString(intsent));
		}
        	dis.close();
        	sr.close();
		break;
	    } catch (Exception e) {
		System.out.println(e);
		e.printStackTrace(System.out);
		dis.close();
		sr.close();
		break;
	    }
	}
    }

    /*
     * Add a StaticAllocator object.
     * @exception java.lang.Exception if an error occurs
     */
	void addStaticAllocator() throws Exception {
	Properties allocProps = new Properties();
	allocProps.put("Scope-1", 
	    "239.255.0.0-239.255.255.255 7 \"Local Scope\" en");
	staticAllocator = new StaticAllocator(allocProps);
	mam.addAllocator(staticAllocator);
    }

    /*
     * Remove the StaticAllocator.
     * @exception java.lang.Exception if the test fails
     */
    void removeStaticAllocator() {
        try {
            mam.removeAllocator(staticAllocator);

            staticAllocator = null;
        } catch (Exception e) {}
    }

    /*
     * Do any necessary cleanup.
     * @exception java.lang.Exception if the test fails
     */
    void close() {
        if (staticAllocator != null) {
            removeStaticAllocator();
        } 
    }

    StaticAllocator staticAllocator = null;
    String appName = "SimpleTesterQA";
    String channelName = "SimpleTesterQA";

    /*
     * Perform the Simple Objects Test.
     * @param args command line arguments 
     * (-send to test sender, -receive to test receiver)
     */
    public static void main(String[] args) {
	checkargs(args);
        System.out.println("Simple Objects Test starting.");

        boolean succeeded = true;

        try {
            SimpleTesterQA st = new SimpleTesterQA();

            st.addStaticAllocator();

            if ((args.length > 0) && (method.equals("receive"))) {
                System.out.println("About to test SimpleReceiver.");
                st.testSimpleReceiver(channelFileName);
            } else if ((args.length > 0) && (method.equals("send"))) {
                System.out.println("About to test SimpleSender.");
                st.testSimpleSender(channelFileName);
            } else {
                throw new TestFailedException(
		    "No argument specified. Use -send or -receive.");
            }

            st.close();
        } catch (Exception e) {
            e.printStackTrace();
            succeeded = false;
        }

        if (succeeded) {
            System.out.println("Simple Objects Test succeeded.");
        } else {
            System.out.println("Simple Objects Test failed.");
        }
    }
    private static void checkargs(String args[]) {
	for (int i = 0; i < args.length; i += 2) {
	    if (args[i].charAt(0) == '-') {
		switch (args[i].charAt(1)) {
		    case 'c' :
			receivers = Integer.parseInt(args[i+1]);
			break;
		    case 'm':
			method = args[i+1];
			break;
		    case 'f':
			channelFileName =  args[i+1];
			break;
		    case 'v':
			verbose = true; 
			break;
		    case 'i':
			intsent = Integer.parseInt(args[i+1]);
			break;
		    default:
			usage();
		}
	    } else {
		usage();
	    }
	}
	if (channelFileName.equals("")) {
	    usage();
	}
    }
    private static void usage() {
	System.out.println("usage: [-c number of receivers], " +
		"-f channelFileName -m send or receive " +
		    "[-v send] [-i #of ints to send]");
    }
}
