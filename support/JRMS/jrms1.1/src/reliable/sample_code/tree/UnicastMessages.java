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
 * UnicastMessages.java
 * 
 * Module Description:
 * 
 * This module implements the UnicastMessages class of the TreeTest application.
 */
package com.sun.multicast.reliable.applications.tree;

import java.net.*;
import java.util.Vector;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMPacketSocket;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class UnicastMessages {
    InetAddress sourceAddress;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param tc
     * @param dp
     * @param tp
     *
     * @see
     */
    UnicastMessages(TreeCanvas tc, DatagramPacket dp, 
                    TRAMTransportProfile tp) {
        Vector members = tc.getMembers();

        // just deliver this to the unicast destination

        for (int i = 0; i < members.size(); i++) {
            Members member = (Members) members.elementAt(i);

            if (member.getPort() == dp.getPort()) {

                // found our destination
                // make sure the packet has a source address

                try {
                    sourceAddress = InetAddress.getLocalHost();
                } catch (java.net.UnknownHostException e) {
                    System.out.println(e);
                    e.printStackTrace();
                }

                dp.setAddress(sourceAddress);

                // switch the port to ours

                dp.setPort(tp.getUnicastPort());
                (member.getPacketSocket()).simulateUnicastPacketReceive(dp);
                tc.incUnicastMessageCount();

                return;
            }
        }

        byte b[] = dp.getData();

        System.out.println("couldn't find unicast destination.  Source: " 
                           + tp.getUnicastPort() + "  Dest: " + dp.getPort() 
                           + "  Type: " + (b[2] & 0xFF));
    }

}

