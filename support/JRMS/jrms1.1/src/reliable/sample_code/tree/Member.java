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
 * Member.java
 * 
 * Module Description:
 * 
 * This module implements the Member member for the TreeTest application
 */
package com.sun.multicast.reliable.applications.tree;

import java.awt.*;
import java.lang.*;
import java.util.Vector;
import java.net.*;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMPacketSocket;
import com.sun.multicast.reliable.transport.tram.MROLE;
import com.sun.multicast.reliable.transport.tram.TMODE;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class Member implements Members {
    Color c = Color.green.darker();
    Dimension d = new Dimension(10, 10);
    Point location;
    int port;
    Rectangle r;
    byte headState;
    Point head = null;
    TreeCanvas tc;
    int maxTTL = 300;
    int ttl;
    int msRate;
    int msTTLIncrements;
    int helloInterval;
    private TRAMTransportProfile tp = null;
    private RMPacketSocket ms = null;
    private String address = null;
    MemberSimulator memberSimulator;
    boolean range;
    int rxLevel = 0;
    boolean messageEnqueued = false;
    Members myLanLeader = null;
    boolean lanLeader = false;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param tc
     * @param x
     * @param y
     * @param assignedPort
     * @param ttl
     * @param msRate
     * @param msTTLIncrements
     * @param helloInterval
     *
     * @see
     */
    Member(TreeCanvas tc, int x, int y, int assignedPort, int ttl, 
           int msRate, int msTTLIncrements, int helloInterval) {
        this.tc = tc;
        location = new Point(x, y);
        port = assignedPort;
        this.ttl = ttl;
        this.msRate = msRate;
        this.msTTLIncrements = msTTLIncrements;
        this.helloInterval = helloInterval;
        this.myLanLeader = tc.lanLeader;
        r = new Rectangle(x - d.width / 2, y - d.height / 2, d.width, 
                          d.height);

        try {
            InetAddress mcastAddress = InetAddress.getByName("224.10.10.37");

            tp = new TRAMTransportProfile(mcastAddress, 4321);

            tp.setTTL((byte) ttl);
            tp.setOrdered(true);
            tp.setTmode(TMODE.RECEIVE_ONLY);
            tp.setMrole(MROLE.MEMBER_ONLY);
            tp.setUnicastPort(assignedPort);
            tp.setMsTTLIncrements((byte) msTTLIncrements);
            tp.setMsRate((msRate));
            tp.setHelloRate((helloInterval));
            tp.setLanTreeFormation(true);

            memberSimulator = new MemberSimulator(tc, this, tp);
            ms = tp.createRMPacketSocket(TransportProfile.RECEIVER, 
                memberSimulator);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    /*
     * Draws the member
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param g
     *
     * @see
     */
    public void draw(Graphics g) {
        g.setColor(c);
        g.fillOval(location.x - d.width / 2, location.y - d.height / 2, 
                   d.width, d.height);

        if (head != null) {
            g.setColor(Color.black);
            g.drawLine(location.x, location.y, head.x, head.y);
        } else if (myLanLeader != null) {
            g.setColor(Color.yellow);
            g.drawLine(location.x, location.y, myLanLeader.getLocation().x, 
                       myLanLeader.getLocation().y);
        }
    }

    /*
     * Gets the member's collision location
     * @return the member's collision location
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Point getCollisionLocation() {
        if (myLanLeader != null) {
            return (myLanLeader.getLocation());
        } else {
            return location;
        }
    }

    /*
     * Gets the member's head
     * @return the member's head
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Point getHead() {
        return head;
    }

    /*
     * Gets the member's lan leader, if any
     * @return the member's lan leader or null
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Members getLanLeader() {
        return myLanLeader;
    }

    /*
     * Gets the member's level
     * @return the member's level
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int getLevel() {
        return rxLevel;
    }

    /*
     * Gets the member's location
     * @return the member's location
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Point getLocation() {
        return location;
    }

    /*
     * Gets the member's member count.
     * @return the member's member count
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int getMemberCount() {
        return 0;
    }

    /*
     * Gets the member's packet socket
     * @return the TRAMPacketSocket instance
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public TRAMPacketSocket getPacketSocket() {
        return (TRAMPacketSocket) ms;
    }

    /*
     * Gets the member's port.
     * @return the member's port assignment
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int getPort() {
        return port;
    }

    /*
     * Gets the member's rectangle
     * @return the rectangle surrounding the member
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Rectangle getRectangle() {
        return r;
    }

    /*
     * Gets the member's type.
     * @return the member's type
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int getType() {
        return Members.MEMBER;
    }

    /*
     * determines whether or not this member already has a
     * multicast message on the queue
     * @return <code>true</code> if there is already a msg from this member
     * <code>false</code> otherwise
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public boolean hasAMessageEnqueued() {
        return messageEnqueued;
    }

    /*
     * informs the node of a change in head assignment
     * @param the port identifying the member's head
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param headPort
     *
     * @see
     */
    public void headChange(int headPort) {
        if (headPort != 0) {
            head = tc.findPoint(headPort);

            tc.countAffiliation();
        } else {
            head = null;
        }

    // tc.paint(tc.getGraphics());

    }

    /*
     * Returns whether or not the member is a lan leader
     * @return <code>true</code> if the member is a lan leader
     * <code>false</code> otherwise
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public boolean isLanLeader() {
        return lanLeader;
    }

    /*
     * informs the node of a change in level
     * @param the node's new level
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param level
     *
     * @see
     */
    public void levelChange(int level) {
        rxLevel = level;

        tc.recordTreeDepth();
    }

    /*
     * informs the simulator of a node's member count change
     * @param port the port associated with this node
     * @param count the member count of the node
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param memberCount
     *
     * @see
     */
    public void memberCountChange(int memberCount) {}

    /*
     * informs the member that a multicast message from it is enqueued
     * @param a boolean indicating whether or not a message is enqueued
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param enqueued
     *
     * @see
     */
    public void messageEnqueued(boolean enqueued) {
        messageEnqueued = enqueued;
    }

    /*
     * resets the node
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public synchronized void reset() {
        head = null;

        if (ms != null) {
            ms.close();

            ms = null;
        }
    }

    /*
     * sets whether or not the member is a lan leader
     * @param a boolean indicating whether or not the member is a lan leader
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param leader
     *
     * @see
     */
    public void setLanLeader(boolean leader) {
        lanLeader = leader;
    }

    /*
     * show's the node's range
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param b
     *
     * @see
     */
    public void showRange(boolean b) {
        range = b;
    }

    /*
     * Sends the message to the member for propagation to TRAM
     * @param the multicast message to be sent
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param m
     *
     * @see
     */
    public synchronized void simulateMessage(MulticastMessages m) {

        // got a message, send it to TRAM for this member

        if (ms != null) {
            ((TRAMPacketSocket)ms).simulateMulticastPacketReceive(
		m.getDatagramPacket());
        } 
    }

    /*
     * starts tree formation
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void startTree() {
        if (ms == null) {
            try {
                ms = tp.createRMPacketSocket(TransportProfile.RECEIVER, 
                                             memberSimulator);
            } catch (Exception ex) {
                System.out.println(ex);
                ex.printStackTrace();
            }
        }
    }

    /*
     * informs the node of a state change
     * @param the new state of the member
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param hState
     *
     * @see
     */
    public void stateChange(byte hState) {
        headState = hState;
    }

}

