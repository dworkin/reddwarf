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
 * MemberSimulator.java
 * 
 * Module Description:
 * 
 * The class implements the MemberSimulator class.  This class implements
 * the TRAMSimulator interface for the TreeTest application.
 */
package com.sun.multicast.reliable.applications.tree;

import java.net.*;
import java.io.IOException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMSimulator;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class MemberSimulator implements TRAMSimulator {
    Members member;
    TreeCanvas tc;
    TRAMTransportProfile tp;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param tc
     * @param member
     * @param tp
     *
     * @see
     */
    MemberSimulator(TreeCanvas tc, Members member, TRAMTransportProfile tp) {
        this.tc = tc;
        this.member = member;
        this.tp = tp;
    }

    /*
     * informs the simulator of a node's head change
     * @param port the port associated with this node
     * @param headPort the the port of the node's new head
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param port
     * @param headPort
     *
     * @see
     */
    public void headChange(int port, int headPort) {

        // propagate the head change to the member

        member.headChange(headPort);
    }

    /*
     * informs the simulator of a node's rxLevel change
     * @param port the port associated with this node
     * @param level the rx level of the node
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param port
     * @param level
     *
     * @see
     */
    public void levelChange(int port, int level) {

        // propagate the level change to the member

        member.levelChange(level);
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
     * @param port
     * @param memberCount
     *
     * @see
     */
    public void memberCountChange(int port, int memberCount) {

        // propagate the change to the member

        member.memberCountChange(memberCount);
    }

    /*
     * passes the multicast data packet to the simulator
     * @param packet the multicast data packet
     * @param ttl the ttl of the packet
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param dp
     * @param ttl
     *
     * @see
     */
    public void simulateMulticastData(DatagramPacket dp, int ttl) {

        // inject the message into TreeTest

        new MulticastMessages(tc, member, dp, MulticastMessages.DATA, ttl);
    }

    /*
     * passes the multicast packet to the simulator
     * @param packet the multicast datagram packet
     * @param type the TRAM subtype of the packet
     * @param ttl the ttl of the packet
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param dp
     * @param type
     * @param ttl
     *
     * @see
     */
    public void simulateMulticastPacket(DatagramPacket dp, int type, 
                                        int ttl) {

        // inject the message into TreeTest

        new MulticastMessages(tc, member, dp, type, ttl);
    }

    /*
     * passes the unicast packet to the simulator
     * @param packet the unicast datagram packet
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param dp
     *
     * @see
     */
    public void simulateUnicastPacket(DatagramPacket dp) {

        // inject the message into TreeTest

        new UnicastMessages(tc, dp, tp);
    }

    /*
     * informs the simulator of a node's state change
     * @param port the port associated with this node
     * @param state the new state of this node
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param port
     * @param hState
     *
     * @see
     */
    public void stateChange(int port, byte hState) {

        // propagate the state change to the member

        member.stateChange(hState);
    }

}       /* End of MemberSimulator Class definition. */

