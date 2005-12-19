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
 * ResetGDManager.java
 */
package com.sun.multicast.reliable.applications.testtools;
import java.util.*;
import java.io.*;

/** 
 * Used to keep track of when the graph should be reset.  This is all 
 * implemented using rmi. A hashtable is created that contains all the 
 * hosts in the hostnames.txt file.  This hash is set to true if the reset
 * has been done for that host.  reset boolean is used as a flag to let
 * each receiver know if the group is in the process of resetting each 
 * receiver. 
 */

public class ResetGDManager {
    private static ResetGDManager resetgdmanager;
    private boolean reset = false;
    private Hashtable hashreset = new Hashtable();
    private Hashtable countreset = new Hashtable();
    private File hostfile;
    Enumeration e;
    Enumeration ecount;

    public ResetGDManager() {
	hostfile = new File("hostnames.txt");
	resetHosts();
    }

    public static ResetGDManager getResetGDManager() {
	if (resetgdmanager == null) {
	    resetgdmanager = new ResetGDManager();
	}
	return resetgdmanager;
    }

    public void resetHosts() {
	try {
	    BufferedReader hostnames =
		new BufferedReader(
		    new FileReader(hostfile));
	    String s;
	    hashreset.clear();
	    countreset.clear();
	    while ((s = hostnames.readLine()) != null) {
		if (!s.startsWith("#")) {
		    hashreset.put(s, new Boolean(false));
		    countreset.put(s, new Integer(0));
		}
	    }
	    hostnames.close();
	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	}
    }

    public void setHost(String hostname) {
	try {
	    hashreset.put(hostname, new Boolean(true));
	    countreset.put(hostname, new Integer(
		((Integer)countreset.get(hostname)).intValue()+1));
	} catch (java.lang.NullPointerException e) {
	    System.out.println("NullPointerException was caught!");
	    System.out.println("Setting countreset to 0");
	    countreset.put(hostname, new Integer(0));
	}
    }

    public boolean getReset() {
	return reset;
    } 

    public synchronized void testResetHash() {
	e = hashreset.elements();
	ecount = countreset.elements();

	while (ecount.hasMoreElements()) {
	    if (((Integer)ecount.nextElement()).intValue() > 5) {
		reset = false;
		resetHosts();
		return;
	    }
	}
	
	while (e.hasMoreElements()) {
	    if (!((Boolean)e.nextElement()).booleanValue()) {
		return;
	    }
	}

	reset = false;
	resetHosts();
    }
    public void setReset(boolean set) {
	reset = set;
    }
}
