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
 * SimpleChannel.java
 */
package com.sun.multicast.reliable.simple;

import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.allocation.MulticastAddressManager;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * SimpleChannel creates a channel and serializes it to a file for the purpose
 * of testing SimpleSender and SimpleReceiver, and their use of a channel
 * pre-created and stored in a file.
 */

class SimpleChannel {
    static String applicationName = "StaticChannelTester";
    static String channelName = "Test2000";
    static TRAMTransportProfile tp = null;
    static String address = "224.10.10.0";
    static int port = 4321;
    static long speed = 100000;
    static long minrate = 1000;
    static byte ttl = 1;

    public static void main(String[] args) {
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].charAt(0) != '-')
                    usage();
                char command = args[i].charAt(1);
                i++;
                if (i >= args.length)
                    usage();
                switch (command) {
                case 'c':
                    channelName = args[i];
                    break;
                case 'a':
                    address = args[i];
                    break;
		case 'n':
		    minrate = Long.parseLong(args[i]);
		    break;
                case 'p':
                    port = Integer.parseInt(args[i]);
                    break;
                case 'r':
                    speed = Integer.parseInt(args[i]);
                    break;
                case 't':
                    ttl = (byte) Integer.parseInt(args[i]);
                    break;
                case 's':
                    applicationName = args[i];
                    break;
                default:
                    usage();
                }
            }
        }
            
        try {
            PrimaryChannelManager m = (PrimaryChannelManager) 
		ChannelManagerFinder.getPrimaryChannelManager(null);

            Channel c = (Channel) m.createChannel();
            c.setChannelName(channelName);
            c.setApplicationName(applicationName);
            
            InetAddress mcastAddress = InetAddress.getByName(address);
	    tp = new TRAMTransportProfile(mcastAddress, port);
	    tp.setMaxDataRate(speed);
	    tp.setMinDataRate(minrate);
	    tp.setTTL(ttl);
	    tp.setOrdered(true);
	    
	    c.setTransportProfile(tp);
	    c.setEnabled(true);
	    
	    m.fileChannel(c, channelName);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    private static void usage() {
        System.out.println("usage: [-c channelname] [-a addr] " 
                     + "[-p port] [-r maxrate] [-t ttl] "
                     + "[-s applname]");
        System.exit(-1);
    }
}
