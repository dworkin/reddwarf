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
 * MulticastMessages.java
 * 
 * Module Description:
 * 
 * This module implements the MulticastMessages class for the
 * TreeTest application.
 */
package com.sun.multicast.reliable.applications.tree;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.Color;
import java.net.*;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class MulticastMessages {
    public static final byte BEACON = 1;
    public static final byte HELLO = 3;
    public static final byte HA = 4;
    public static final byte MS = 5;
    public static final byte DATA = 17;
    InetAddress sourceAddress;
    TreeCanvas tc;
    Members source;
    DatagramPacket dp;
    int messageType;
    Point location;
    int maxTTL;
    Rectangle r;
    Rectangle lastR = null;
    int ttl;
    Color c = Color.green;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param tc
     * @param source
     * @param dp
     * @param messageType
     * @param maxTTL
     *
     * @see
     */
    MulticastMessages(TreeCanvas tc, Members source, DatagramPacket dp, 
                      int messageType, int maxTTL) {
        this.source = source;
        this.location = source.getCollisionLocation();
        this.dp = dp;
        this.messageType = messageType;
        this.maxTTL = maxTTL & 0xFF;
        this.ttl = 1;
        this.tc = tc;

        // make sure the packet has a source address

        try {
            sourceAddress = InetAddress.getLocalHost();
        } catch (java.net.UnknownHostException e) {
            System.out.println(e);
            e.printStackTrace();
        }

        dp.setAddress(sourceAddress);

        switch (messageType) {

        case BEACON: 
            c = Color.green.darker();

            break;

        case HELLO: 
            c = Color.yellow.brighter().brighter();

            break;

        case HA: 
            c = Color.blue;

            break;

        case MS: 
            c = Color.red;

            break;

        case DATA: 
            c = Color.black;

            break;
        }

        tc.addMessage(this);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param g
     *
     * @see
     */
    void draw(Graphics g) {
        g.setColor(c);
        g.drawOval(source.getLocation().x - ttl, 
                   source.getLocation().y - ttl, ttl * 2, ttl * 2);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Rectangle getRectangle() {
        r = new Rectangle(location.x - ttl, location.y - ttl, ttl * 2, 
                          ttl * 2);

        return r;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Rectangle getLastRectangle() {
        return lastR;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Point getLocation() {
        return location;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Members getSource() {
        return source;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    int getType() {
        return messageType;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param r
     *
     * @see
     */
    void setLastRectangle(Rectangle r) {
        lastR = r;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    int getTTL() {
        return ttl;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param newTTL
     *
     * @see
     */
    void setTTL(int newTTL) {
        ttl = newTTL;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    int getMaxTTL() {
        return maxTTL;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    DatagramPacket getDatagramPacket() {
        return dp;
    }

}

