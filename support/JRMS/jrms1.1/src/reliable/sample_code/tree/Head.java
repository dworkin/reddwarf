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
 * Head
 * 
 * Module Description:
 * 
 * This module implements the Head member for the TreeTest application
 */
package com.sun.multicast.reliable.applications.tree;

import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMPacketSocket;
import com.sun.multicast.reliable.transport.tram.MROLE;
import com.sun.multicast.reliable.transport.tram.TMODE;
import java.awt.*;
import java.lang.*;
import java.util.Vector;
import java.util.Random;
import java.net.*;

class Head implements Members {
    TreeCanvas tc;
    Color c = Color.black;
    Dimension d = new Dimension(10, 10);
    Point location;
    Rectangle r;
    private TRAMTransportProfile tp = null;
    private RMPacketSocket ms = null;
    private String address = null;
    MemberSimulator memberSimulator;
    int port;
    byte headState;
    Point head = null;
    int maxMembers;
    int ttl;
    int msRate;
    int haTTLIncrements;
    int haTTLLimit;
    int msTTLIncrements;
    int haInterval;
    int helloInterval;
    int rxLevel = 0;
    int repairTTL = 0;
    boolean range = false;
    Color rangeColor = Color.cyan;
    boolean messageEnqueued = false;
    int memberCount = 0;
    Members myLanLeader = null;
    boolean lanLeader = false;

    Head(TreeCanvas tc, int x, int y, int assignedPort, int ttl, int msRate, 
            int haTTLIncrements, int haTTLLimit, int msTTLIncrements, 
            int haInterval, int helloInterval, int maxMembers) {

        // fill in the blanks

        this.tc = tc;
        location = new Point(x, y);
        port = assignedPort;
        this.ttl = ttl;
        this.msRate = msRate;
        this.haTTLIncrements = haTTLIncrements;
        this.haTTLLimit = haTTLLimit;
        this.msTTLIncrements = msTTLIncrements;
        this.maxMembers = maxMembers;
        this.haInterval = haInterval;
        this.helloInterval = helloInterval;
        this.myLanLeader = tc.lanLeader;
        r = new Rectangle(x - d.width / 2, y - d.height / 2, d.width, 
                          d.height);

        // now start up TRAM

        try {
            InetAddress mcastAddress = InetAddress.getByName("224.10.10.37");

            tp = new TRAMTransportProfile(mcastAddress, 4321);

            tp.setTTL((byte)ttl);
            tp.setOrdered(true);
            tp.setTmode(TMODE.RECEIVE_ONLY);
            tp.setMrole(MROLE.MEMBER_EAGER_HEAD);
            tp.setUnicastPort(assignedPort);
            tp.setHaTTLIncrements((byte) haTTLIncrements);
            tp.setHaTTLLimit((byte) haTTLLimit);
            tp.setMsTTLIncrements((byte) msTTLIncrements);
            tp.setHaInterval(haInterval);
            tp.setHelloRate(helloInterval);
            tp.setMaxMembers((byte) maxMembers);
            tp.setMsRate((msRate));
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

    public void draw(Graphics g) {
        g.setColor(c);

        if (range) {
            g.setColor(rangeColor);
            g.fillOval(location.x - repairTTL, location.y - repairTTL, 
                       repairTTL * 2, repairTTL * 2);
            g.setColor(rangeColor.brighter());
        }

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

    public Point getHead() {
        return head;
    }

    /*
     * Gets the member's lan leader, if any
     * @return the member's lan leader or null
     */

    public Members getLanLeader() {
        return myLanLeader;
    }

    /*
     * Gets the member's level
     * @return the member's level
     */

    public int getLevel() {
        return rxLevel;
    }

    /*
     * Gets the member's location
     * @return the member's location
     */

    public Point getLocation() {
        return location;
    }

    /*
     * Gets the member's member count.
     * @return the member's member count
     */

    public int getMemberCount() {
        return memberCount;
    }

    /*
     * Gets the member's packet socket
     * @return the TRAMPacketSocket instance
     */

    public TRAMPacketSocket getPacketSocket() {
        return (TRAMPacketSocket) ms;
    }

    /*
     * Gets the member's port.
     * @return the member's port assignment
     */

    public int getPort() {
        return port;
    }

    /*
     * Gets the member's rectangle
     * @return the rectangle surrounding the member
     */

    public Rectangle getRectangle() {
        return r;
    }

    /*
     * Gets the member's type.
     * @return the member's type
     */

    public int getType() {
        return Members.HEAD;
    }

    /*
     * determines whether or not this member already has a
     * multicast message on the queue
     * @return <code>true</code> if there is already a msg from this member
     * <code>false</code> otherwise
     */

    public boolean hasAMessageEnqueued() {
        return messageEnqueued;
    }

    /*
     * informs the node of a change in head assignment
     * @param the port identifying the member's head
     */

    public void headChange(int headPort) {
        if (headPort != 0) {
            head = tc.findPoint(headPort);

            tc.countAffiliation();

        // tc.paint(tc.getGraphics());

        } else {
            head = null;
        }

        tc.paint(tc.getGraphics());
    }

    /*
     * Returns whether or not the member is a lan leader
     * @return <code>true</code> if the member is a lan leader
     * <code>false</code> otherwise
     */

    public boolean isLanLeader() {
        return lanLeader;
    }

    /*
     * informs the node of a change in level
     * @param the node's new level
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

    public void memberCountChange(int memberCount) {
        this.memberCount = memberCount;

        if (memberCount > 0) {
            c = Color.lightGray;
        } else {
            c = Color.black;
        }
    }

    /*
     * informs the member that a multicast message from it is enqueued
     * @param a boolean indicating whether or not a message is enqueued
     */

    public void messageEnqueued(boolean enqueued) {
        messageEnqueued = enqueued;
    }

    /*
     * resets the node
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

    public void setLanLeader(boolean leader) {
        lanLeader = leader;
    }

    /*
     * show's the node's range
     */

    public void showRange(boolean b) {
        range = b;
    }

    /*
     * Sends the message to the member for propagation to TRAM
     * @param the multicast message to be sent
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

    public void stateChange(byte hState) {
        headState = hState;
    }
}
