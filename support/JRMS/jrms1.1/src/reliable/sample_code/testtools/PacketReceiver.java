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
 * PacketReceiver.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

/** 
 * uses rmi to call MCTestQA over the network. 
 */

public class PacketReceiver extends TestReceiver {
    private String channelname;
    private String address;
    private String port;
    private String maxrate;
    private String minrate;
    private String ttl;
    private String applname;
    private String socket;
    private boolean rverbose;
    private boolean sverbose;
    private String v;
    private String g;
    private String bytesent;
    private PropManager props;
    private Properties JRMSProps;
    private boolean graph;

    PacketReceiver(String hostname, String filename) {
	super(hostname, filename);
    	props = PropManager.getPropManager();
    	JRMSProps = props.getProps();
	channelname = JRMSProps.getProperty("channelname", "JRMSTest");
	graph = new Boolean(JRMSProps.getProperty("graph", "true"))
	    .booleanValue();
	address = JRMSProps.getProperty("address", "224.148.74.13");
	port = JRMSProps.getProperty("port", "4321");
	maxrate = JRMSProps.getProperty("maxrate", "64000"); 
	minrate = JRMSProps.getProperty("minrate", "1000"); 
	ttl = JRMSProps.getProperty("ttl", "1");
	applname = JRMSProps.getProperty("applename", "RMTest");
	rverbose = new Boolean(JRMSProps.getProperty("rverbose", "false"))
	    .booleanValue(); 
	sverbose = new Boolean(JRMSProps.getProperty("sverbose", "false"))
	    .booleanValue();
	bytesent = JRMSProps.getProperty("intsent", "100000");
	if (sverbose && rverbose) {
	    v = " -v both";
	} else if (rverbose) {
	    v = " -v receive";
	} else if (sverbose) {
	    v = " -v send";
	} else {
	    v = "";
	}
	if (graph) {
	    g = " -g";
	} else {
	    g = "";
	}

	try {
	    String url = "rmi://" + hostname + "/";
	    System.out.println("This is url: " + url);
	    CallProduct cp1 = (CallProduct)Naming.lookup(url +
		"CallMCTest");
	    String commandline =  javahome +
		" -Djava.security.policy=client.policy" + 
		" -classpath " + classpath + " " + packg + 
		".MCTestQA" + " -c -r " + minrate + " -R " +
		maxrate + " -p " + port + " -a " + address + " -f " +
		userdir + "/" + filename + " -F " + userdir + 
		"/JRMSTest.properties" + v + " " + g; 

	    cp1.callMCTestQA(commandline);

	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (java.rmi.NotBoundException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	}
    }
}
