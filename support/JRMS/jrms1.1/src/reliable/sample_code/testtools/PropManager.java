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
 * PropManager.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;
import java.lang.*;
import java.util.*;

/** 
 * Manages properties file and stores properties.  Opens 
 * default.properties, checks for some command line stuff for overide
 * info then stores results in the JRMS.properties file.
 */

public class PropManager {
    private static PropManager propmanager;
    private static Properties Props;
    private static String userdir = "";
	private PropManager(String[] args) {
	if (propmanager == null) {
	    try {
		userdir = System.getProperty("user.dir");
		FileInputStream propfile = 
		    new FileInputStream("default.properties");
		Props = new Properties();
		Props.load(propfile);
		propfile.close();
		checkargs(args);
	    } catch (FileNotFoundException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	    } catch (IOException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
		
	    }
	}
    }
    private PropManager() { 
	if (propmanager == null) {
	    try {
		FileInputStream propfile =
		    new FileInputStream("JRMSTest.properties");
		Props = new Properties();
		Props.load(propfile);
		propfile.close();
	    } catch (FileNotFoundException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	    } catch (IOException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	    }
	}
    }
    private PropManager(String filename) { 
	if (propmanager == null) {
	    try {
		FileInputStream propfile =
		    new FileInputStream(filename);
		Props = new Properties();
		Props.load(propfile);
		propfile.close();
	    } catch (FileNotFoundException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	    } catch (IOException e) {
		System.out.println(e);
		e.printStackTrace(System.out);
	    }
	}
    }
    public Properties getProps() {
	return Props;
    }
    public static PropManager getPropManager() {
	propmanager = new PropManager();
	return propmanager;
    }
    public static PropManager getPropManager(String Filename) {
	propmanager = new PropManager(Filename);
	return propmanager;
    }
    public static PropManager getPropManager(String[] args) {
	propmanager = new PropManager(args);	
	return propmanager;
    }
    private void checkargs(String[] args) {
	for (int i = 0; i < args.length; i += 2) {
	    if (args[i].charAt(0) == '-') {
		switch (args[i].charAt(1)) {
		    case 'r':
			Props.put("receivers", args[i+1]);
			break;
		    case 'a':
			Props.put("address", args[i+1]);
			break;
		    case 'c':
			Props.put("channelname", args[i+1]);
			break;
		    case 'p':
			Props.put("port", args[i+1]);
			break;
		    case 's':
			Props.put("applname", args[i+1]);
			break;
		    case 'S':
			Props.put("socket", args[i+1]);
			break;
		    case 't':
			Props.put("ttl", args[i+1]);
			break;
		    case 'i':
			Props.put("intsent", args[i+1]); 
			break;
		    case 'm':
			Props.put("maxrate", args[i+1]);
			break;		
		    case 'n':
			Props.put("minrate", args[i+1]);
			break;
		    case 'v':
			if (args[i+1].equals("receive")) {
			    Props.put("rverbose", "true");
			} else if (args[i+1].equals("send")) {
			    Props.put("sverbose", "true");
			} else if (args[i+1].equals("both")) {
			    Props.put("sverbose", "true");
			    Props.put("rverbose", "true");
			} else {
			    usage();	
			}
			break;
		    case 'w':
			Props.put("ackwindow", args[i+1]);
			break;
		    default:
			usage();
		}
	    } else {
		usage();
	    }
	}
    }
    private void usage() {
	System.out.println("usage: [-r number of receivers] "
	    + "[-c channelname] [-a addr] " 
	    + "[-p port] [-m maxrate] [-t ttl] "
	    + "[-n minrate] "
	    + "[-s applname] "
	    + "[-v receive, send, both] "
	    + "[-w Ack Window size] "
	    + "[-i # of ints to send] "
	    + "[-S stream or packet]");
	    System.exit(-1);
    }
}
