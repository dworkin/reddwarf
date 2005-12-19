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
 * SimpleTester.java
 */
package com.sun.multicast.reliable.simple;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Date;
import java.util.Properties;
import com.sun.multicast.allocation.MulticastAddressManager;
import com.sun.multicast.allocation.StaticAllocator;
import com.sun.multicast.util.TestFailedException;

/**
 * A tester for the simple objects.  Feel free to expand upon it.
 * 
 * This is not part of the public interface of the simple objects. 
 * It's a package-local class.
 */
class SimpleTester {

    /**
     * Create a new SimpleTester object. This should only be called from 
     * SimpleTester.main().
     */
    SimpleTester() {
        mam = MulticastAddressManager.getMulticastAddressManager();
    }

    static final int DOT = 46;

    MulticastAddressManager mam;

    /**
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
	    ss = new SimpleSender(fn);
	} else {
            ss = new SimpleSender(appName, channelName, start, null, 
		null, (byte) 1);
	}
	
        System.out.println("Waiting for one receiver.");
        ss.waitTill(1);
        System.out.println("At least one receiver detected");

        System.out.println("About to wait 10 seconds.");
        ss.waitTill(start);
        System.out.println("About to send data.");

        PrintWriter pw = new PrintWriter(ss.getOutputStream());

        // for (int i = 0; i < 20000; i++)

        pw.print(testString);
        pw.close();
        ss.close();
    }

    /**
     * Test SimpleReceiver. If the test fails, throw an exception.
     * 
     * <P> Create a SimpleReceiver. Receive some text and verify that 
     * it's correct.  Close the SimpleReceiver.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testSimpleReceiver(String fn) throws Exception {
        SimpleReceiver sr;
        System.out.println("About to create SimpleReceiver.");

	if (fn != null) {
	    sr = new SimpleReceiver(fn);
	} else {
            sr = new SimpleReceiver(appName, channelName);
	}
	
        System.out.println("About to get InputStream.");

        InputStreamReader isr = new InputStreamReader(sr.getInputStream());
        StringWriter sw = new StringWriter();
        boolean eof = false;

        while (!eof) {
            int c = isr.read();

            if (c == -1) {
                eof = true;
            } else {
                sw.write(c);
            }
        }

        isr.close();
        sr.close();

        if (!sw.toString().equals(testString)) {
            throw new TestFailedException("String received does not match." + 
		sw.toString());
        } 

        sw.close();
    }

    /**
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

    /**
     * Remove the StaticAllocator.
     * @exception java.lang.Exception if the test fails
     */
    void removeStaticAllocator() {
        try {
            mam.removeAllocator(staticAllocator);

            staticAllocator = null;
        } catch (Exception e) {}
    }

    /**
     * Do any necessary cleanup.
     * @exception java.lang.Exception if the test fails
     */
    void close() {
        if (staticAllocator != null) {
            removeStaticAllocator();
        } 
    }

    StaticAllocator staticAllocator = null;
    String appName = "SimpleTester";
    String channelName = "SimpleTester";
    String testString = "This is a test.";

    /**
     * Perform the Simple Objects Test.
     * @param args command line arguments (-send to test sender, -receive 
     * to test receiver)
     */
    public static void main(String[] args) {
        String channelFileName = null;
        System.out.println("Simple Objects Test starting.");

        System.out.println("Arglen = " + new Integer(args.length).toString());
        if (args.length > 0) {
            System.out.println("Args[0] = " + args[0]);
            if (args.length > 1) {
                System.out.println("channel file name = " + args[1]);
                channelFileName = args[1];
            }
        }

        boolean succeeded = true;

        try {
            SimpleTester st = new SimpleTester();

            st.addStaticAllocator();

            if ((args.length > 0) && (args[0].equals("-receive"))) {
                System.out.println("About to test SimpleReceiver.");
                st.testSimpleReceiver(channelFileName);
            } else if ((args.length > 0) && (args[0].equals("-send"))) {
                System.out.println("About to test SimpleSender.");
                st.testSimpleSender(channelFileName);
            } else {
                throw new TestFailedException("No argument specified. " +
		    "Use -send or -receive.");
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

}

