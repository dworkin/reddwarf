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
 * JRMSTest.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

/** 
 * Main class that runs the JRMSTest harness.  Reads default.properties 
 * then command line to set up environment then saves properties to 
 * JRMSTest.properties creates a channel file.  Opens hostnames.txt
 * to create MCTestQA thread on each receiver.  Starts server based on host 
 * found in server property of default.properties.  Waits for server to 
 * finish printing logs.  Runs a log parse test to make sure that each log
 * file, either log or logbak succeeded or finished, and every test passed.
 */

public class JRMSTest {
    private int receivers;
    private String channelname;
    private String address; 
    private String port; 
    private String maxrate;
    private String minrate;
    private String ttl;
    private String applname;
    private String socket;
    private String runtesttool;
    private String runsimple;
    private String url = "";
    private boolean rverbose;
    private boolean sverbose;
    private String v;
    private String intsent;
    private Process server;
    private Process receiver;
    private InetAddress inetaddress; 
    private String hostname;
    private File hostfile;
    private Vector hosts;
    private Enumeration ehosts;
    private PropManager props;
    private Properties JRMSProps;
    private String ackWindow;
    private CallProduct cp1;
    private String javahome = "";
    private boolean graph;

    JRMSTest(String[] args) {
    	props = PropManager.getPropManager(args);
    	JRMSProps = props.getProps();
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
	receivers = Integer.parseInt(JRMSProps.
	    getProperty("receivers", "1"));
	channelname = JRMSProps.getProperty("channelname", "JRMSTest");
	address = JRMSProps.getProperty("address", "224.148.74.13");
	port = JRMSProps.getProperty("port", "4321");
	maxrate = JRMSProps.getProperty("maxrate", "64000"); 
	minrate = JRMSProps.getProperty("minrate", "1000"); 
	ackWindow = JRMSProps.getProperty("ackwindow", "32");
	ttl = JRMSProps.getProperty("ttl", "1");
	applname = JRMSProps.getProperty("applename", "RMTest");
	socket = JRMSProps.getProperty("socket", "stream"); 
	graph = new Boolean(JRMSProps.getProperty("graph", "true"))
	    .booleanValue();
	runtesttool = javahome + " -Djava.security.policy=client.policy " 
	    + JRMSProps.getProperty("testtools", "com." +
	    "sun.multicast.reliable.applications.testtools"); 
	runsimple = javahome + " " + 
	    JRMSProps.getProperty("simple", "com." +
	    "sun.multicast.reliable.simple");
	rverbose = new Boolean(JRMSProps.getProperty("rverbose", "false"))
	    .booleanValue(); 
	sverbose = new Boolean(JRMSProps.getProperty("sverbose", "false"))
	    .booleanValue();
	intsent = JRMSProps.getProperty("intsent", "100000");
	JRMSProps.setProperty("userdir", System.getProperty("user.dir"));
	try {
	    FileOutputStream out =
		new FileOutputStream("JRMSTest.properties");
	    JRMSProps.store(out, "JRMSTest Properties");
	    out.close();
	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	}
    }

    public static void main(String args[]) {
	JRMSTest jrmstest = new JRMSTest(args);
	jrmstest.run();
    }

    private void run() {
	try {
	    Runtime runtime = Runtime.getRuntime();
	    System.out.println("Number of receivers = " + 
		Integer.toString(receivers));
	    System.out.println("channelname = " + channelname);
	    System.out.println("address = " + address);
	    System.out.println("maxrate = " + maxrate);
	    System.out.println("minrate = " + minrate);
	    System.out.println("applname = " + applname);
	    System.out.println("ttl = " + ttl);
	    System.out.println("port = " + port);
	    System.out.println("Socket type: " + socket);
	    System.out.println("java: " + runtesttool);
	    System.out.println("Verbose logging for receivers? " + rverbose);
	    System.out.println("Verbose logging for server? " + sverbose); 
	    System.out.println("Number of ints or bytes to send = " + intsent); 
	    System.out.println("Creating a JRMSTest Channel file.");
	    if (socket.equals("packet")) {
		System.out.println("AckWindow = " + ackWindow);
	    }
	    
	    Process createChannel = runtime.exec(runsimple + 
		".SimpleChannel " + " -c " + channelname +  " -a " + 
		address + " -r " + maxrate + " -s " + applname + 
		" -t " + ttl + " -p " + port);
	    inetaddress = InetAddress.getLocalHost();
	    hostname = inetaddress.getHostName();
	    // System.setProperty("DISPLAY",hostname + ":0.0");
	    // Process.waitFor() was not working on some machines
	    // So I wrote my own. 
	    while (waitFor(createChannel)) {
		Thread.sleep(1000);
	    }
	    // Still had to add a sleep for the file to close.
	    Thread.sleep(5000);
	    hostfile = new File("hostnames.txt");
	    if (hostfile.exists()) {
		HostNameManager hostnamemanager = 
		    HostNameManager.getHostNameManager(); 
		hostnamemanager.resetHosts();
	        hosts = hostnamemanager.getHosts();
	        ehosts = hosts.elements();
		System.out.println("Starting " + Integer.toString(
		    hosts.size()) + " JRMSTest receiver(s).");
		int counter = 0;
		while (ehosts.hasMoreElements()) {
		    counter++; 
		    hostname = (String)ehosts.nextElement();
		    System.out.println("Hostname is: " + hostname);
		    if (socket.equals("stream")) {
			StreamReceiver streamreceiver = 
			    new StreamReceiver(hostname, 
				hostname + 
			        Integer.toString(counter) + ".log");
		    } else {
			PacketReceiver packetreceiver =
			    new PacketReceiver(hostname,
				hostname +
				Integer.toString(counter) + ".log");
		    }
		}
	    } else {
		inetaddress = InetAddress.getLocalHost();
		hostname = inetaddress.getHostName();
		System.out.println("Starting " + Integer.toString(receivers) + 
		    " JRMSTest receiver(s).");
		for (int i = 1; i <= receivers; i++) {
		    if (socket.equals("stream")) {
			StreamReceiver streamreceiver = 
			    new StreamReceiver(hostname, 
				"receiver" + Integer.toString(i) + ".log");
		    } else {
			PacketReceiver packetreceiver =
			    new PacketReceiver(hostname,
				"receiver" + Integer.toString(i) + ".log");
		    }
		}
	    }
	    System.out.println("Starting the JRMSTest Server.");
	    inetaddress = InetAddress.getLocalHost();
	    hostname = inetaddress.getHostName();
	    System.out.println("Hostname is: " + hostname);
	    if (sverbose) {
	        v = " -v send";
	    } else {
	        v = "";
	    }

	    if (socket.equals("stream")) {
		System.out.println("receiver = runtime.exec(" + runtesttool +
		    ".SimpleTesterQA -m " + 
		    "send -f " + channelname + " -i " + intsent + v + ")");
		server = runtime.exec(runtesttool +
		    ".SimpleTesterQA -m " + 
		    "send -f " + channelname + " -i " + intsent + v);
	    } else {
	 	System.out.println("server = runtime.exec(" + runtesttool +
		    ".MCTestQA -s -r " + minrate + " -R " + maxrate +
		    " -a " + address + " -p " + port + " -w " + 
		    ackWindow + v + ")");
	 	server = runtime.exec(runtesttool +
		    ".MCTestQA -s -r " + minrate + " -R " + maxrate +
		    " -a " + address + " -p " + port + " -w " + 
		    ackWindow + v);
		if (graph) {
		    url = "rmi://" + hostname + "/";
		    cp1 = (CallProduct)Naming.lookup(url +
			"CallMCTest");
		    cp1.startPerfMon();
		}
	    }

	    while (waitFor(server)) {
		Thread.sleep(1000);
	    }

	    PrintStream serverlog = 
		new PrintStream(
		    new BufferedOutputStream(
			new FileOutputStream(
			   "server.log", true)));
	    BufferedReader bufserver = 
		new BufferedReader(
		    new InputStreamReader(server.getInputStream()));
	    String s;
 	    int counter = 0;
	    boolean backup = true;
	    while ((s = bufserver.readLine()) != null) {
		serverlog.println(s);
		counter++;
		if (counter % 5 == 0) {
 		    System.out.print(".");
		}
		if (counter % 1320 == 0) {
		    counter = 0;
		    serverlog.close();
		    if (backup) {
			File logFile = new File("server.logbak");
			try {
			    serverlog = new PrintStream(
				new BufferedOutputStream(
				    new FileOutputStream(logFile)));
			} catch (FileNotFoundException e) {
			    System.out.println(e);
			    e.printStackTrace(System.out);
			}
			backup = false;
		    } else {
			File logFile = new File("server.log");
			try {
			    serverlog = new PrintStream(
				new BufferedOutputStream(
				    new FileOutputStream(logFile)));
			} catch (FileNotFoundException e) {
			    System.out.println(e);
			    e.printStackTrace(System.out);
			}
			backup = true;
		    }
		} 
	    }
	    serverlog.close();
	    System.out.println("Done");
	    System.out.println("Waiting 30 seconds for logs to finish...");
	    Thread.sleep(30000);
	    counter = 0;
	    if (hostfile.exists()) {
	        ehosts = hosts.elements();
		while (ehosts.hasMoreElements()) {
		    counter++;
		    LogFileManager.parseFile(
			(String)ehosts.nextElement() + 
			Integer.toString(counter) + ".log"); 
		}
	    } else {
		for (int i = 1; i <= receivers; i++) {
		    LogFileManager.parseFile("receiver" + 
			Integer.toString(i) + ".log");
		}
	    }
	    LogFileManager.parseFile("server.log");
	    
	} catch (FileNotFoundException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (InterruptedException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (NotBoundException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	}
    }
    private boolean waitFor(Process process) {
	try {
	    process.exitValue();
	    return true;
	} catch (IllegalThreadStateException e) {
	    System.out.print(".");
	    return false;
	}
    }
	
}
