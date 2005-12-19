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
 * PerfMonCanvas.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.rmi.*;
import java.rmi.server.*;

/*  
 * This is the first pass at the Graphing Engine.  Lot's of things in
 * here can be simplified but I ran out of time.  For example: the switch
 * is not necessary, another, reset should probably be a boolean  
 * inside the GraphData object...
 */

/** 
 * PerfMonCanvas receives the GraphData objects from all the receivers, 
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
 * Again I ran out of time implementing this, but you can see some of my 
 * efforts in the testfiles directory on apple.
 */
public class PerfMonCanvas extends Canvas implements Observer {
    private Observable notifier;
    private PropManager props;
    private Properties JRMSProps;
    private String serverhost = "";
    private String skew = "";
    private String url = "";
    private CallProduct cp1;
    private int i = 0;
    private Vector GData = new Vector();
    private int VCounter = 0;
    private int VSetup = 0;
    private int lVCounter = 0;
    private Enumeration e;
    private Enumeration htkeys;
    private GraphData gd;
    private GraphData prevgd;
    private HostNameManager hnm;
    private Hashtable ht;
    private Color colors[] = {Color.blue, Color.red, Color.green,
	Color.magenta, Color.orange, Color.black};


    /** 
     * Observable used to repaint graph after another window covers it.
     * HostNameManager is used to keep track of what lines in the graph
     * need to be reset and what lines need to be painted white - blank 
     * rmi for this class is also setup in this constructor
     */
    PerfMonCanvas(Observable notifier) {
	notifier.addObserver(this);
	this.notifier = notifier;
	hnm = HostNameManager.getHostNameManager();
	ht = hnm.getHashHosts();
	props = PropManager.getPropManager();
	JRMSProps = props.getProps();
	serverhost = JRMSProps.getProperty("server");
	skew = JRMSProps.getProperty("skew");
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

    /** 
     * This is the paint that is called by the observer pattern.  It is
     * also used whenever the graph is redrawn by reset or when starting
     * up. 
     */
    public void paint(Graphics g) {
	htkeys = ht.keys();
	GraphData paintgd;
	Dimension d = getSize();
	int cy = d.height;
	String hostname = "";
	int value = 0;
	int pVCounter = 0;

	// draws the legend

	while (htkeys.hasMoreElements()) {
	    hostname = (String)htkeys.nextElement();
	    value = ((Integer)ht.get(hostname)).intValue();

	    if (value < 99) {
		g.setColor(colors[value]);
		g.fillOval(10, 10 + (15*value), 6, 6);
		g.drawString(hostname, 25, 17 + (15*value));
	    }
	}

	// draws the rectangle around the legend	

	g.setColor(Color.black);
 	g.drawRect(3, 3, 125, 10 + (5 + (15*(ht.size()-2))));

	// VCounter is where we are currently adding data to the graph

	pVCounter = VCounter;


	// makes sure that any GraphData hanging around that has been
	// set to reset is now set to a more benign blank. We are just
	// repainting here and do not want the whole graph to reset.

	for (int i = 0; i < pVCounter; i++) {
	    paintgd = (GraphData)GData.elementAt(i);
	    if (paintgd.getHost().equals("reset")) {
		paintgd.setHost("blank");
	    }
	    if (skew.equals("true")) {
		drawlines(g, paintgd, cy, true);
	    } else {
		drawlinesontop(g, paintgd, cy, true);
	    }
		
	}

	// Make sure that the rest the graph gets painted.
	// Again making sure that reset does not happen yet.

	for (int i = VSetup + VCounter; i < lVCounter; i++) {
	    paintgd = (GraphData)GData.elementAt(i);
	    if (paintgd.getHost().equals("reset")) {
		paintgd.setHost("blank");
	    }
	    if (skew.equals("true")) {
	        drawlines(g, paintgd, cy, true);
	    } else {
		drawlinesontop(g, paintgd, cy, true);
	    }
	}
    }

    /** 
     * Used for debugging only
     */
    public void printGData() {
	e = GData.elements();
	Dimension d = getSize();
	int counter = 0;
	GraphData printgd;

	while (e.hasMoreElements()) {
	    printgd = (GraphData)e.nextElement();
	    System.out.println(counter++);
	    System.out.println("Bytes: " + printgd.getBytes());
	    System.out.println("Time: " + printgd.getTime());
	    System.out.println("Host: " + printgd.getHost());
	}
    }

    /** 
     * This is the paint that draws the lines in real time.
     */
    public synchronized void customPaint() {
	Dimension d = getSize();
	int cy = d.height;
	Integer line;
	Graphics g = this.getGraphics();
	GraphData gd1;

	try {
	 
	    if (g != null) {	

		// Clearing the graph ahead of what's going to be
		// displayed.

	    	gd1 = (GraphData)GData.elementAt(VCounter + VSetup); 
		if (gd1.getHost().equals("reset")) {
		    gd1.setHost("blank");
		}

		/* 
		 * skewing is handled with these two different functions
		 * drawlines and drawlinesontop
		 *
		 * false means remove color by drawing line color white
		 * true means to add color by drawing line according to the
		 * host recorded in the GraphData object gd or gd1
		 */

		if (skew.equals("true")) {
		    drawlines(g, gd1, cy, false);
		    drawlines(g, gd, cy, true);
		} else {
		    drawlinesontop(g, gd1, cy, false);
		    drawlinesontop(g, gd, cy, true);
		}
	    }

	// added more for debugging, not yet sure how to prevent the
	// Exception here

	} catch (java.lang.ArrayIndexOutOfBoundsException e) {
	    if (skew.equals("true")) {
	        drawlines(g, gd, cy, true);
	    } else {
	        drawlinesontop(g, gd, cy, true);
	    }

	}     
    }

    /** 
     * Sets the GraphData in the Graph Engine Vector. Replace, unless
     * Vector is too small, in which case add it to the Vector.
     */
    public synchronized void setGData(GraphData gd) {
	try {
	    this.gd = gd;
	    GData.setElementAt(gd, VCounter);
	    VCounter++;
	} catch (ArrayIndexOutOfBoundsException e) {
	    this.gd = gd;
	    GData.add(gd);
	    VCounter++;
	}
    }

    /** 
     * Restart the whole vector, this is not used currently used i
     * for PerfMon
     */
    public void resetGData() {
	GData.removeAllElements();
	repaint();
    }

    /** 
     * Time to clear hash because it has been set to its original state.
     */
    public void resetHostnames() {
	hnm = HostNameManager.getHostNameManager();
	ht = hnm.getHashHosts();
    }

    /** 
     * Draws all graph lines in real time.  add is used to tell function
     * whether to draw the lines in color or to remove the data point by
     * painting with the color white.
     * 
     * Lines are drawn on top of each other without skewing them.
     */
    private void drawlinesontop(Graphics g, GraphData gd, int cy, 
	boolean add) {
	GraphData gd1;
        try {
	    Integer line;

            if (gd != null) {

		/* 
		 * line tells switch where to draw the line and 
		 * and what color to use. 
		 *
		 * add is used to tell whether to erase or draw the line. 
		 * the -5 +5 stuff was added to cleanup pixel dust that was
		 * not getting cleaned up.
		 */

                line = (Integer)ht.get(gd.getHost());
                switch (line.intValue()) {
                    case 0:
			if (add) {
                   	    g.setColor(Color.blue);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
			    	
                        break;
                    case 1:
			if (add) {
                   	    g.setColor(Color.red);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 2:
			if (add) {
                   	    g.setColor(Color.green);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150), 
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 3:
			if (add) {
                   	    g.setColor(Color.magenta);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 4:
			if (add) {
                   	    g.setColor(Color.orange);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 5:
			if (add) {
                   	    g.setColor(Color.black);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150),
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150),
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150),
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
		    case 99:  // This is reserved for resetting the Vector
			try {
			    if (VCounter > 100) {
				lVCounter = VCounter;

				// in case there is data left over that has
				// not yet been cleared.  Clear it off.

				for (int i = VCounter; 
				    i < GData.size(); i++) {
				    gd1 = (GraphData)GData.elementAt(i);
				    if (gd1.getHost().equals("reset")) {
					gd1.setHost("blank");	
				    }
				    drawlinesontop(g, gd1, cy, false);
				}

				// Sets the distance of blank space between
				// where line is drawn and erased.

		 		VSetup = VCounter/8;
			    }

			    // Cleans up that distance mention in comment 
			    // above.

			    for (int i = 0; i < VSetup; i++) {
				gd1 = (GraphData)GData.elementAt(i);
				if (gd1.getHost().equals("reset")) {
				    gd1.setHost("blank");
				}
				drawlinesontop(g, gd1, cy, false);
			    }

			    VCounter = 0;

			} catch (ArrayIndexOutOfBoundsException e) {
			}
			break;
                    default:
                        break;
                }
            }

        } catch (NoSuchElementException e) {
        }

    }

    /** 
     * Draws all graph lines in real time.  add is used to tell function
     * whether to draw the lines in color or to remove the data point by
     * painting with the color white.
     * 
     * Lines are drawn next to each other they are skewed.
     */
    private void drawlines(Graphics g, GraphData gd, int cy, 
	boolean add) {
	GraphData gd1;
        try {
	    Integer line;

            if (gd != null) {

		/* 
		 * line tells switch where to draw the line and 
		 * what color to use.
		 * 
		 * add is used to tell whether to erase or draw the line.
		 * the -5 +5 stuff was added to cleanup pixel dust that
		 * was not getting cleaned up
		 */

                line = (Integer)ht.get(gd.getHost());
                switch (line.intValue()) {
                    case 0:
			if (add) {
                   	    g.setColor(Color.blue);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150)+5,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150)+7,
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+5,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+7,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
			    	
                        break;
                    case 1:
			if (add) {
                   	    g.setColor(Color.red);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150) + 25,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150)+27,
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+25,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+27,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 2:
			if (add) {
                   	    g.setColor(Color.green);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150) + 45,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150) + 47, 
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+45,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+47,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 3:
			if (add) {
                   	    g.setColor(Color.magenta);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150) + 65,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150) + 67,
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+65,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+67,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 4:
			if (add) {
                   	    g.setColor(Color.orange);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150) + 85,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150) + 87,
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+85,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+87,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
                    case 5:
			if (add) {
                   	    g.setColor(Color.black);
			} else {
			    g.setColor(Color.white);	
			}
                        g.drawLine((gd.getTime()/150) + 105,
                            (int)((float)cy-gd.getBytes()/10000),
                            (gd.getTime()/150) + 107,
                            (int)((float)cy-gd.getBytes()/10000));
			if (!add) {
                            g.drawLine((gd.getTime()/150)+105,
                        	(int)((float)cy-gd.getBytes()/10000)-5,
                        	(gd.getTime()/150)+107,
				(int)((float)cy-gd.getBytes()/10000)+5);
			}
                        break;
		    case 99:  // This is reserved for resetting the Vector
			try {
			    if (VCounter > 100) {
				lVCounter = VCounter;

				// in case there is data left over that has
				// not yet been cleared. Clear it off

				for (int i = VCounter; 
				    i < GData.size(); i++) {
				    gd1 = (GraphData)GData.elementAt(i);
				    if (gd1.getHost().equals("reset")) {
					gd1.setHost("blank");	
				    }
				    drawlines(g, gd1, cy, false);
				}

				// Sets the distance of blank space between
				// where line is drawn and erased.

		 		VSetup = VCounter/8;
			    }

			    for (int i = 0; i < VSetup; i++) {
				gd1 = (GraphData)GData.elementAt(i);
				if (gd1.getHost().equals("reset")) {
				    gd1.setHost("blank");
				}
				drawlines(g, gd1, cy, false);
			    }

			    // Cleans up that distance mentioned in comment.

			    VCounter = 0;

			} catch (ArrayIndexOutOfBoundsException e) {
			}
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
