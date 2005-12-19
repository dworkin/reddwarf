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
 * PerfMon.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;
import com.sun.multicast.util.UnsupportedException;

class PerfObservable extends Observable {
    public void notifyObservers(Object b) {
	setChanged();
	super.notifyObservers(b);
    }
}

class PerfMonCanvas extends Canvas implements Observer {
    int maxElements = 700;
    Vector rateVector = new Vector();
    // Vector highestAllowedVector = new Vector();
    Vector windowSizeVector = new Vector();
    Graphics g;

    PerfMonCanvas(Observable notifier) {
	notifier.addObserver(this);
    }

    public void paint() {
	paint(getGraphics());
    }

    public void paint(Graphics g) {
	draw(rateVector, g, Color.blue);
	// draw(highestAllowedVector, g, Color.red);
	draw(windowSizeVector, g, Color.green);
    }

    private void draw(Vector v, Graphics g, Color c) {
	g.setColor(c);

	Dimension d = getSize();

	int height = d.height;

	Point previousPoint = null;

	int size = v.size();

	for (int i = 0; i < size; i++) {
            try {
		Point point1 = previousPoint;
                Point point2 = new Point(i, ((Point)v.elementAt(i)).y);

                if (point1 == null)
                    point1 = point2;

                previousPoint = point2;

                g.drawLine(point1.x, height - point1.y,
                           point2.x, height - point2.y);
            } catch (NoSuchElementException e) {
		break;
            }
	}
    }

    public void addElement(Vector v, int y) {
	while (v.size() >= getSize().width) {
	    v.removeElementAt(0);
	}

	v.addElement(new Point(0, y));
    }

    public void update(Observable o, Object arg) {
	paint(g);
    }

    public void erase() {
	g = getGraphics();
	g.clearRect(0, 0, getSize().width, getSize().height);
    }

}

public class PerfMon extends Frame implements Runnable {
    class DWAdapter extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            System.exit(0);
        }
    }

    private PerfMonCanvas pmc;
    private Thread dispThread;
    private TRAMTransportProfile tp;
    private TRAMStats tramStats;
    private TRAMControlBlock tramblk;
    private int height = 500;
    private int width = 700;
    private boolean quit = false;

    public PerfMon(TRAMControlBlock tramblk, String s) {
	super("Performance Monitor:  " + s);

	tp = tramblk.getTransportProfile();

        if (tp.getTmode() != TMODE.SEND_ONLY)
	    return;

	this.tramblk = tramblk;
	tramStats = tramblk.getTRAMStats();

	addWindowListener(new DWAdapter());
	setBackground(Color.white);
	setLayout(new BorderLayout());
	pmc = new PerfMonCanvas(new PerfObservable());
	ScrollPane sp = new ScrollPane();
	sp.add("Center", pmc);
	add(sp);
	setSize(800, 600);
	setVisible(true);
	show();
 

	dispThread = new Thread(this);
	dispThread.setDaemon(true);
	dispThread.start();
    }

    public void stop() {
	quit = true;
    }

    public void newDataRate(long curDataRate) {
	// pmc.addElement(rateVector, (int)(curDataRate / 1000));
    }

    public void run() {
	while (quit == false) {
	    try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
	    }

	    pmc.erase();
	    pmc.addElement(pmc.rateVector, 
		(int)(tramblk.getRateAdjuster().getAverageDataRate() / 1000));

	    int highestAllowed = tramblk.getHighestSequenceAllowed();
	    // pmc.addElement(pmc.highestAllowedVector, highestAllowed);

	    int window = tramblk.getRateAdjuster().getWindow();

	    pmc.addElement(pmc.windowSizeVector, window);

	    // System.err.println(window);
	    
	    pmc.paint();
	}
    }

}
