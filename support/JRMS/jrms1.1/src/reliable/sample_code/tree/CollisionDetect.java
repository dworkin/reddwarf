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
 * CollisionDetect.java
 * 
 * Module Description:
 * 
 * This module implements the collision detection mechanism of the
 * TreeTest application.  As the ttl of a multicast packet increases,
 * CollisionDetect determines which nodes should receive the packet
 */
package com.sun.multicast.reliable.applications.tree;

import java.util.Vector;
import java.awt.Rectangle;

class CollisionDetect extends Thread {
    TreeCanvas tc;
    int ttlIncrement = 30;

    CollisionDetect(TreeCanvas tc) {
        this.tc = tc;

        setDaemon(true);
        start();
    }

    public void run() {
        while (true) {
            stall();

            Vector members = tc.getMembers();
            Vector messages = tc.getMessages();
            int msgCount = messages.size();
            int newTTL;
            int i = 0;

            tc.paint(tc.getGraphics());

            // check each message for collision with members and expired ttl

            while (msgCount != 0) {
                MulticastMessages message = 
                    (MulticastMessages) messages.elementAt(i++);

                // check for member collision

                checkCollision(message, members);

                // decide if the message needs to stick around

                newTTL = message.getTTL() + ttlIncrement;

                if (newTTL <= message.getMaxTTL()) {
                    message.setTTL(newTTL);
                } else {
                    message.getSource().messageEnqueued(false);
                    tc.removeMessage(message);
                }
                if (i == msgCount) {

                    // we've made it through 1 pass, paint the screen

                    tc.paint(tc.getGraphics());

                    // any new ones, or any left with increased ttls?

                    members = tc.getMembers();
                    messages = tc.getMessages();

                    if ((msgCount = messages.size()) == 0) {
                        stall();

                        messages = tc.getMessages();
                        msgCount = messages.size();
                    }

                    i = 0;
                }
            }
        }
    }

    void setTTLIncrement(int increment) {
        ttlIncrement = increment;
    }

    int getTTLIncrement() {
        return ttlIncrement;
    }

    void checkCollision(MulticastMessages message, Vector members) {
        Rectangle r = message.getRectangle();

        for (int i = 0; i < members.size(); i++) {
            Members member = (Members) members.elementAt(i);

            // if (member.getLocation().equals(message.getLocation())) {
            // don't bother colliding with the sender of the message
            // continue;
            // }

            if ((r.contains(member.getCollisionLocation())) && 
		((message.getLastRectangle() == null) || 
		(!(message.getLastRectangle().contains(
		member.getCollisionLocation()))))) {

                // first collision

                member.simulateMessage(message);
            }
        }

        message.setLastRectangle(r);
    }

    private synchronized void stall() {
        try {
            wait();
        } catch (InterruptedException ie) {}
    }

    public synchronized void wake() {
        notifyAll();
    }

}
