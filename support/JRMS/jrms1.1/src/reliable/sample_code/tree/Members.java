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
 * Members.java
 * 
 * Module Description:
 * 
 * This module implements the Members interface for the TreeTest application
 */
package com.sun.multicast.reliable.applications.tree;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Point;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMPacketSocket;

/**
 * Undocumented Interface Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
public interface Members {
    public final static int SENDER = 1;
    public final static int HEAD = 2;
    public static final int RELUCTANT_HEAD = 3;
    public final static int MEMBER = 4;

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
    public void draw(Graphics g);

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
    public Point getCollisionLocation();

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
    public Point getHead();

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
    public Members getLanLeader();

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
    public int getLevel();

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
    public Point getLocation();

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
    public int getMemberCount();

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
    public TRAMPacketSocket getPacketSocket();

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
    public int getPort();

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
    public Rectangle getRectangle();

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
    public int getType();

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
    public boolean hasAMessageEnqueued();

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
    public void headChange(int headPort);

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
    public boolean isLanLeader();

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
    public void levelChange(int level);

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
    public void memberCountChange(int memberCount);

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
    public void messageEnqueued(boolean enqueued);

    /*
     * resets the node
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void reset();

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
    public void setLanLeader(boolean leader);

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
    public void showRange(boolean b);

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
    public void simulateMessage(MulticastMessages m);

    /*
     * starts tree formation
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void startTree();

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
    public void stateChange(byte hState);
}

