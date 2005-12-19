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
 * TestReceiver.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

/** 
 * Parent Class for PacketReceiver and StreamReceiver.  Used to set up 
 * directory information, to create an rmi connection, and to open up
 * a log file. 
 */

public class TestReceiver extends Thread {
    private PrintStream receiverlog;
    protected Runtime runtime = Runtime.getRuntime();
    protected Process receiver;
    private String filename;
    protected String userdir;
    protected String javahome;
    protected String classpath;
    protected String packg;
    private PropManager props = PropManager.getPropManager();
    private Properties JRMSProps = props.getProps();
    private CallProduct cp;

    TestReceiver(String hostname, String filename) {
	classpath = JRMSProps.getProperty("CLASSPATH");
	packg = JRMSProps.getProperty("testtools");
	userdir = System.getProperty("user.dir");
	this.filename = filename;
	try {
	    cp = (CallProduct)Naming.lookup("rmi://" +
		hostname + "/CallMCTest");
	
	    if (cp.selectVM(hostname)) {
		javahome = System.getProperty("java.home");
		javahome = JRMSProps.getProperty("javahome", javahome);
		if (!(javahome.charAt(1) == '/')) {
		    javahome = "/" + javahome;
		}
		if (javahome.endsWith("/jre")) {
		    javahome = (javahome.substring(
			1, javahome.lastIndexOf("/jre"))+"/bin/java");
		} else {
		    javahome = javahome + "/java";
		}
	    } else {
		javahome = JRMSProps.getProperty("java1.2fcs"); 
	    }
	} catch (java.rmi.RemoteException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	} catch (java.rmi.NotBoundException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	} catch (java.net.MalformedURLException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	}
    }
	 
	
    public void run() {
	try {
	    receiverlog = 
		new PrintStream(
		    new BufferedOutputStream(
			new FileOutputStream(
			    filename, true)));
	    BufferedReader bufreceiver = 
		new BufferedReader(
		    new InputStreamReader(receiver.getInputStream()));
	    String s;
	    while ((s = bufreceiver.readLine()) != null) {
		receiverlog.println(s);
	    }
	    receiverlog.close();
	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} 
    }
    protected Process getReceiver() {
	return receiver;
    }

}
