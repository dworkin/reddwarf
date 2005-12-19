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
 * GraphManager.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.util.*;

/** 
 * This is a wrapper for PerfMon and RatePerfMon.  It is used to 
 * extend Observable for each class. PerfMon and RatePerfMon already 
 * extend Canvas. Observable is used to redraw the Graphs when another
 * window is placed on top of them. Note the use of notifier.
 */

public class GraphManager extends Observable {
    private static GraphManager graphmanager;
    private Observable notifier = new PerfObservable();
    private PerfMon Graph;
    private RatePerfMon RateGraph;

    private GraphManager() {
    }

    public void createGraphs() {
	Graph = new PerfMon(notifier, 0, 0);
	RateGraph = new RatePerfMon(notifier, 0, 500);
    }
	
    public static GraphManager getGraphManager() {
	if (graphmanager == null) {
	    graphmanager = new GraphManager();
	}
	return graphmanager;
    }
    
    public void drawGraph(GraphData gd) {
	Graph.setData(gd);
	Graph.customPaint();
    }

    public void drawRateGraph(GraphData gd) {
	RateGraph.setData(gd);
	RateGraph.customPaint();
    }

    public void printGData() {
	Graph.printGData();
    }

    public void showGraph() {
	Graph.show();
    }

    public void showRateGraph() {
	RateGraph.show();
    }

    public void resetGData() {
	Graph.resetGData();
    }
    
    public void resetRateGData() {
	RateGraph.resetGData();
    }

    public void resetHostnames() {
	Graph.resetHostnames();
    }

    public void resetRateHostnames() {
	RateGraph.resetHostnames();
    }
}
