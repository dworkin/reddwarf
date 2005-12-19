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
 * StreamReceiver.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

/** 
 * Kicks off the SimpleTesterQA.class using rsh  
 */

public class StreamReceiver extends TestReceiver {
    private String channelname;
    private boolean verbose;
    private String intsent;
    private String v;

    StreamReceiver(String hostname, String filename) {
	super(hostname, filename);
	PropManager props = PropManager.getPropManager();
	Properties JRMSProps = props.getProps();
	channelname = JRMSProps.getProperty("channelname");
	intsent = JRMSProps.getProperty("intsent");

	verbose = Boolean.valueOf(JRMSProps.getProperty("rverbose")).
	    booleanValue();

	if (verbose) {
	    v = " -v receive";
	} else {
	    v = "";
	}

	try {
	    System.out.println("receiver = runtime.exec(rsh " + 
	    hostname + " " + javahome +
	    " -classpath " + classpath + " " + packg + 
	    ".SimpleTesterQA" + " -m receive -f " + userdir + "/" +
	    channelname + v + "-i " + intsent + ")");	
	    receiver = runtime.exec("rsh " + hostname + " " + javahome +
	    " -classpath " + classpath + " " + packg + 
	    ".SimpleTesterQA" + " -m receive -f " + userdir + "/" +
	    channelname + v + " -i " + intsent);	
	    start();
	} catch (IOException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	}
    }
}
