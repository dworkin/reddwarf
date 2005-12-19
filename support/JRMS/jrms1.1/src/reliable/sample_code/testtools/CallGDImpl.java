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
 * CallGDImpl.java
 */

package com.sun.multicast.reliable.applications.testtools;

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import java.net.*;
import java.io.*;

/** 
 * Implements all RMI functions. Creates Graphs and keeps track of receiver 
 * information in a central location. Allow Graph to reset bytes/time so 
 * lines can stay on the graph. Also used for starting receivers over the
 * network.
 */

public class CallGDImpl
    extends UnicastRemoteObject 
	implements CallProduct {

    private GraphData gd;
    private GraphManager gm;
    private SelectVM svm;
    private ResetGDManager rgdm;
    private File hostfile;

    /** 
     * Starts GraphManager and ResetGDManager (GraphData)
     * singletons
     */
    public CallGDImpl() 
	throws RemoteException {
	hostfile = new File("hostnames.txt");
	gm = GraphManager.getGraphManager();
	if (hostfile.exists()) {
	    rgdm = ResetGDManager.getResetGDManager();
	}
    }

    /** 
     * Helps decide which VM to use
     */
    public boolean selectVM(String host) 
	throws RemoteException {
	svm = new SelectVM(host);
	return svm.getExactVM();
    }

    /** 
     * Starts the Performance Monitor Graph engine
     */
    public void startPerfMon() 
	throws RemoteException {

	gm.createGraphs();
	gm.resetGData();
	gm.resetRateGData();
	gm.resetHostnames();
	gm.resetRateHostnames();
	gm.showGraph();
	gm.showRateGraph();
    }

    /** 
     * Draws lines on the Graphs using Graph Data
     */
    public void drawGraph(GraphData gd)
	throws RemoteException {
	gm.drawGraph(gd);
	gm.drawRateGraph(gd);
    }

    /** 
     * Sets the flag that lets receivers know whether or not
     * the graph is in the process of being reset.
     */
    public void setReset(boolean set)
	throws RemoteException {
	rgdm.setReset(set);
    }	

    /** 
     * Gets the flag that lets receivers know whether or not
     * the graph is in the process of being reset.
     */
    public boolean getReset() 
	throws RemoteException {
	return rgdm.getReset();
    }

    /** 
     * Tests the hash to see if all receivers have been successfully 
     * reset. If yes, sets all members of the hash to false.
     */
    public void testResetHash() 
	throws RemoteException {
	rgdm.testResetHash();
    }

    /** 
     * Sets a host member of the hash to true. Meaning reset. 
     */
    public void setHost(String hostname) 
	throws RemoteException {
	rgdm.setHost(hostname);
    }

    /** 
     * Used only for debugging
     */
    public void printGraph()
	throws RemoteException {
	gm = GraphManager.getGraphManager();
	gm.printGData();
    }

    /** 
     * Starts receivers
     */
    public void callMCTestQA(String commandline)
        throws RemoteException {

        try {
            Runtime runtime = Runtime.getRuntime();
            System.out.println("runtime.exec(" + commandline + ")");
            runtime.exec(commandline);
        } catch (java.io.IOException e) {
            System.out.println("Error: " + e);
	    e.printStackTrace(System.out);
        }
    }
}
