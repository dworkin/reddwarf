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
 * MulticastSenderStream.java
 * 
 * Module Description:
 * 
 * This module implements the sending side of a multicast application.
 * It creates a transport profile for the TRAM reliable Multicast protocol
 * setting the address, port, number of packets, and the data rate fields
 * according to user input. It then sends datagram packets of random length
 * between 0 and 65k bytes. The user may set a fixed packet size as an
 * alternative.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import java.io.*;
import java.util.*;
import java.awt.Dimension;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.TransportProfile;

class MulticastSenderStream {

    public static void main(String[] args) {
        InetAddress ia = null;
        int port = 0;
        int count = 0;
        int memberId = -1;
        int uport = 0;
        long dataRate = 65535;
        long bytesSent = 0;
        long startTime = 0;
        long elapsedTime = 0;
        BrickLayer brickLayer;

        /* read the address and port from the command line */

        if (args.length < 3) {
            System.out.println("Usage: java MulticastSenderPacket addr " +
			       "port packetCount [dataRate]");
            System.exit(1);
        }

        try {
            ia = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
            count = Integer.parseInt(args[2]);

            if (args.length >= 4) {
                dataRate = Integer.parseInt(args[3]);
            }
            if (args.length >= 5) {
                uport = Integer.parseInt(args[4]);
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("Usage: java MulticastSenderPacket addr " +
			       "port packetCount [dataRate]");
            System.exit(1);
        }

        System.out.println("Multicast Address   = " + ia.getHostAddress());
        System.out.println("Multicast Port      = " + port);
        System.out.println("Unicast Port        = " + uport);
        System.out.println("Member Id           = " + memberId);
        System.out.println("Repeat Count        = " + count);
        System.out.println("Data rate           = " + dataRate);

        brickLayer = new BrickLayer(new Dimension(600, 600), count, 
                                    "Sending Bricklayer");

        for (int i = 1; i <= count; i++) {
            brickLayer.addBrick(i);
        }

        /*
         * Obtain a new Transport Profile with the address and
         * port specified. Without making any other modifications,
         * create an RMSocket for this profile. All other fields
         * in the TransportProfile will have default values.
         */
        try {
            TRAMTransportProfile tp = new TRAMTransportProfile(ia, port);

            tp.setMaxDataRate(dataRate);
            tp.setTTL((byte) 1);
            tp.setBeaconRate((long) 1000);
            tp.setOrdered(false);

            if (uport != 0) {
                tp.setUnicastPort(uport);
            } 

            TRAMStreamSocket so = (TRAMStreamSocket)
		tp.createRMStreamSocket(TransportProfile.SENDER);

            OutputStream os = so.getOutputStream();

            try {
                Thread.sleep(2000);
            } catch (Exception e) {}

            startTime = System.currentTimeMillis();

            for (int i = 1; i <= count; i++) {
                brickLayer.getBrick(i, os);
            }

            elapsedTime = System.currentTimeMillis() - startTime;

            /* Miller time */

            os.close();
            so.close();
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }

        // long elapsedTime = System.currentTimeMillis() - startTime;
        // need to implement a getBrickSize method in BrickLayer.java
        // bytesSent = count * brickLayer.getBrickSize();
        // for now approximate it to 700

        bytesSent = count * 700;
        dataRate = (bytesSent * 1000) / elapsedTime;

        System.out.println("Average data rate = " + dataRate);
        System.exit(0);
    }

    public void usage() {}

}

