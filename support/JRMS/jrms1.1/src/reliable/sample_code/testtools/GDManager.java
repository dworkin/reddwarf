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
 * GDManager.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.util.*;
import java.text.*;
import java.rmi.*;
import java.rmi.server.*;

/** 
 * Manages the parsing of bytes and time for Graphing. Stores them in a
 * GraphData object which gets sent to the Graph engine by MCTestQA.
 */

public class GDManager {
    private static GDManager gdmanager;
    private Date date1 = null;
    private Date date2 = null;
    private int time = 0;
    private String currentyear;
    private GraphData gd;
    private int passindex;
    private int sizeindex;
    private int bytes = 0;
    private String noyear = "";
    private String firsthalf = "";
    private String secondhalf = "";
    private String fullyear = "";
    private CallProduct cp1;
    private PropManager props;
    private Properties JRMSProps;
    private String serverhost = "";
    private String url = "";
    private SimpleDateFormat sdf = 
	new SimpleDateFormat("MM/dd/yyyy hh:mm:ss.S");


    private GDManager() {
	currentyear = Integer.toString(new GregorianCalendar().
	    get(Calendar.YEAR));
	props = PropManager.getPropManager();
	JRMSProps = props.getProps();
	serverhost = JRMSProps.getProperty("server");
	url = "rmi://" + serverhost + "/";
	try {
	    cp1 = (CallProduct)Naming.lookup(url + 
		"CallMCTest"); 
	} catch (java.net.MalformedURLException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	} catch (java.rmi.RemoteException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	} catch (java.rmi.NotBoundException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	}
    }

    public static GDManager getGDManager() {
	if (gdmanager == null) {
	    gdmanager = new GDManager();
	}
	return gdmanager;
    }

    public GraphData addData(String logString, String host) {

	if (logString == null || host == null) {
	    gd = new GraphData(time, bytes, host);
	    return gd;
	}

	passindex = logString.lastIndexOf("\n", logString.
	    indexOf("Passing Packet")) + 1;
	sizeindex = logString.indexOf("of size") + 8;
	noyear = logString.substring(passindex, passindex+18);
	firsthalf = noyear.substring(0, 5);
	secondhalf = noyear.substring(6);
	fullyear = firsthalf + "/" + currentyear + " " + 
	    secondhalf;
	bytes = bytes + Integer.valueOf(logString.substring(sizeindex,
	    logString.indexOf(" ", sizeindex))).intValue();
	try {
	    if (date1 == null) {
		date1 = sdf.parse(fullyear);
	    } else {
		date2 = sdf.parse(fullyear);
		time = time + (int)(date2.getTime() - date1.getTime());
		date1 = date2;
	    }
	    if (time > 80500 || bytes > 3750000) {
		cp1.setReset(true);
	    }
	    /* 
	     * code here still needs some work, reset is happening but
	     * notice that all receivers get set to 0.  This was due
	     * to not being able to accurately subtract bytes from
	     * all receivers because of timing problems with RMI.
	     */
	    if (cp1.getReset()) {
		time = 0;
		bytes = 0;
		cp1.setHost(host);
		cp1.testResetHash();
		System.out.println("Setting host to reset.");
		host = "reset";
	    }
	} catch (ParseException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (java.rmi.RemoteException e) {
	    System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
	}	

	gd = new GraphData(time, bytes, host);
	return gd;
    } 
}
