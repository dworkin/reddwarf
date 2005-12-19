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
 * RatePerfMonCanvas.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.awt.*;
import java.util.*;
import java.awt.event.*;

/*  
 * This is the first pass at the Graphing Engine.  Lot's of things in
 * here can be simplified but I ran out of time.  There is no need to
 * do case statements everywhere.  reset should probably be a boolean  
 * inside the GraphData object...
 */

/** 
 * RateGraph Engine receives the GraphData objects from all the receivers, 
 * throws it into a GraphData vector and paints the Graph. The vector
 * is used, so I can repaint the graph when the observer pattern notifies
 * the engine that it needs repainting. 
 *
 * Skewing is an option to allow the lines representing each receiver to
 * be either next to each other (skewed) or on top of each other.  
 */

/*  
 * Tech note on reseting the Data on the Graph.  When I do a reset, I am
 * starting each receiver back to 0 bytes 0 time.  This means at the end
 * of one screen, all receivers appear to be coming out of the gate at the
 * same time.  
 *
 * Trying to reset the data accurately, by subtracting the proper number
 * of bytes, proved problematic.  Some GraphData objects were slipping through
 * and some receivers were getting subtracted from twice.  
 *
 *  Again I ran out of time implementing this, but you can see some of my 
 *  efforts in the testfiles directory on apple.
 */
public class RatePerfMonCanvas extends Canvas implements Observer {
    private Observable notifier;
    private PropManager props;
    private Properties JRMSProps;
    private String skew = "";
    private int i = 0;
    private Vector GData = new Vector();
    private Enumeration e;
    private Enumeration htkeys;
    private GraphData gd;
    private HostNameManager hnm;
    private Hashtable ht;
    private Color colors[] = {Color.blue, Color.red, Color.green,
	Color.magenta, Color.orange, Color.black};

    RatePerfMonCanvas(Observable notifier) {
	notifier.addObserver(this);
	this.notifier = notifier;
	hnm = HostNameManager.getHostNameManager();
	ht = hnm.getHashHosts();
	props = PropManager.getPropManager();
	JRMSProps = props.getProps();
	skew = JRMSProps.getProperty("skew");
    }

    public void paint(Graphics g) {
	htkeys = ht.keys();
	GraphData paintgd;
	e = GData.elements();
	Dimension d = getSize();
	int cx = d.width;
	int cy = d.height;
	String hostname = "";
	int value = 0;

	while (htkeys.hasMoreElements()) {
	    hostname = (String)htkeys.nextElement();
	    value = ((Integer)ht.get(hostname)).intValue();

	    if (value < 99) {
		g.setColor(colors[value]);
		g.fillOval(10, 10 + (15*value), 6, 6);
		g.drawString(hostname, 25, 17 + (15*value));
	    }
	}
	g.setColor(Color.black);
 	g.drawRect(3, 3, 125, 10 + (5 + (15*(ht.size()-2))));

	while (e.hasMoreElements()) {
	    paintgd = (GraphData)e.nextElement();
	    if (skew.equals("true")) {
	        drawlines(g, paintgd, cy);
	    } else {
		drawlinesontop(g, paintgd, cy);
	    }
	}
    }

    public void printGData() {
	e = GData.elements();
	Dimension d = getSize();
	int counter = 0;
	GraphData printgd;

	while (e.hasMoreElements()) {
	    printgd = (GraphData)e.nextElement();
	    System.out.println(counter++);
	    System.out.println("Bytes: " + printgd.getRate());
	    System.out.println("Time: " + printgd.getTime());
	    System.out.println("Host: " + printgd.getHost());
	}
    }

    public synchronized void customPaint() {
	    Dimension d = getSize();
	    int cx = d.width;
	    int cy = d.height;
	    Integer line;
	    Graphics g = this.getGraphics();
	    
	    if (g != null) {	
		if (skew.equals("true")) {
		    drawlines(g, gd, cy);
		} else {
		    drawlinesontop(g, gd, cy);
	 	}
	    }
    }

    public void setGData(GraphData gd) {
	this.gd = gd;
	GData.addElement(gd);
    }

    public void resetGData() {
	GData.removeAllElements();
	repaint();
    }
    public void resetHostnames() {
	hnm = HostNameManager.getHostNameManager();
	ht = hnm.getHashHosts();
    }

    private void drawlinesontop(Graphics g, GraphData gd, int cy) {
        try {
	    Integer line;

            if (gd != null) {
                line = (Integer)ht.get(gd.getHost());
                switch (line.intValue()) {
                    case 0:
                        g.setColor(Color.blue);
                        g.drawLine((gd.getTime()/150)+5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
                    case 1:
                        g.setColor(Color.red); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
                    case 2:
                        g.setColor(Color.green);
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
                    case 3:
                        g.setColor(Color.magenta); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
                    case 4:
                        g.setColor(Color.orange); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
                    case 5:
                        g.setColor(Color.black); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 150);
                        break;
		    case 99:  // This is reserved for resetting the Vector
			GData.removeAllElements();
			repaint();
			break;
		    case 100: 
			GData.removeAllElements();
			repaint();
			break;
                    default:
                        break;
                }
            }

        } catch (NoSuchElementException e) {
        }
    }

    private void drawlines(Graphics g, GraphData gd, int cy) {
        try {
	    Integer line;

            if (gd != null) {
                line = (Integer)ht.get(gd.getHost());
                switch (line.intValue()) {
                    case 0:
                        g.setColor(Color.blue);
                        g.drawLine((gd.getTime()/150)+5,
                            cy-gd.getRate() - 250,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 250);
                        break;
                    case 1:
                        g.setColor(Color.red); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 200,
                            (gd.getTime()/150)+7,
                            cy-gd.getRate() - 200);
                        break;
                    case 2:
                        g.setColor(Color.green);
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 150,
                            (gd.getTime()/150) + 7, 
                            cy-gd.getRate() - 150);
                        break;
                    case 3:
                        g.setColor(Color.magenta); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() - 100,
                            (gd.getTime()/150) + 7,
                            cy-gd.getRate() -100);
                        break;
                    case 4:
                        g.setColor(Color.orange); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate() -50,
                            (gd.getTime()/150) + 7,
                            cy-gd.getRate() - 50);
                        break;
                    case 5:
                        g.setColor(Color.black); 
                        g.drawLine((gd.getTime()/150) + 5,
                            cy-gd.getRate(),
                            (gd.getTime()/150) + 7,
                            cy-gd.getRate());
                        break;
		    case 99:  // This is reserved for resetting the Vector
			GData.removeAllElements();
			repaint();
			break;
		    case 100: 
			GData.removeAllElements();
			repaint();
			break;
                    default:
                        break;
                }
            }

        } catch (NoSuchElementException e) {
        }

    }

    public void update(Observable o, Object arg) {
	paint(this.getGraphics());
    }
}
