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
 * MulticastSenderPacket.java
 * 
 * Module Description:
 * 
 * This module implements the sending side of a multicast application.
 * It creates a transport profile for the Unreliable Multicast protocol
 * setting the address, port, number of packets, and the data rate fields
 * according to user input. It then sends datagram packets of random length
 * between 0 and 65k bytes. The user may set a fixed packet size as an
 * alternative.
 */
package com.sun.multicast.reliable.transport.um;

import java.lang.*;
import java.net.*;
import java.io.*;
import java.util.*;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.RMStatistics;

class MulticastSenderPacket {

    public static void main(String[] args) {
        final int MAX_PACKET = 65507;
        final int HEADER = 8;
        final int PACKETNUMBER = 0;
        final int PACKETSIZE = 4;
        InetAddress ia = null;
        int port = 0;
        int count = 0;
        int packetSize = MAX_PACKET;
        boolean randomPacket = true;
        long dataRate = 65535;
        boolean test;
        long bytesSent = 0;
        long startTime = 0;

        /* read the address and port from the command line */

        if (args.length < 3) {
            System.out.println(
		"Usage: java MulticastSenderPacket addr port packetCount " +
		"[dataRate] [packetSize]");
            System.exit(1);
        }

        try {
            ia = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
            count = Integer.parseInt(args[2]);

            if (args.length >= 4) {
                dataRate = Integer.parseInt(args[3]);
                randomPacket = true;
            }
            if (args.length >= 5) {
                packetSize = Integer.parseInt(args[4]);
                randomPacket = false;
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("Usage: java MulticastSender address port");
            System.exit(1);
        }

        System.out.println("Multicast Address   = " + ia.getHostAddress());
        System.out.println("Multicast Port      = " + port);
        System.out.println("Repeat Count        = " + count);
        System.out.println("Packet size         = " + packetSize);

        if (randomPacket) {
            System.out.println("Packet Distribution = random");
        } else {
            System.out.println("Packet Distribution = fixed");
        }

        System.out.println("Data rate           = " + dataRate);

        /*
         * Obtain a new Transport Profile with the address and
         * port specified. Without making any other modifications,
         * create an RMSocket for this profile. All other fields
         * in the TransportProfile will have default values.
         */
        try {
            UMTransportProfile tp = new UMTransportProfile(ia, port);

            tp.setDataRate(dataRate);

            test = false;

            try {
                tp.setAddress(InetAddress.getByName("0.0.0.0"));
            } catch (InvalidMulticastAddressException e) {
                test = true;
            }

            if (!test) {
                System.out.println("Failed setAddress invalid test.");
            }

            UMPacketSocket so = (UMPacketSocket) 
		tp.createRMPacketSocket(UMTransportProfile.SENDER);

            /*
             * The following loop obtains a string, converts it to a byte
             * array, writes it to the RMSocket one byte at a time, and
             * issues a flush() call when the entire string has been written.
             * The flush() forces the transport to send the data out.
             */
            startTime = System.currentTimeMillis();

            for (int i = 1; i <= count; i++) {
                if (randomPacket) {
                    packetSize = (int) (Math.random() 
                                        * (MAX_PACKET - HEADER));
                }

                byte data[] = new byte[packetSize + HEADER];

                Util.writeInt(i, data, PACKETNUMBER);
                Util.writeInt(packetSize, data, PACKETSIZE);

                for (int j = HEADER; j < packetSize + HEADER; j++) {
                    data[j] = 56;
                }

                bytesSent += (long) packetSize;

                if (i % 10 == 0) {
                    so.setDataRate(so.getDataRate() * 2);
                    System.out.println("Setting data rate to " 
                                       + so.getDataRate());
                }

                System.out.println("Sending packet " + i);

                DatagramPacket dp = new DatagramPacket(data, data.length);

                so.send(dp);
            }

            /* Miller time */

            System.out.println("GETTING Statistics ");

            try {
                RMStatistics stat = so.getRMStatistics();

                System.out.println("Sender Count " + stat.getSenderCount());

                InetAddress[] addresses = stat.getSenderList();

                if (addresses == null) {
                    System.out.println("No Sender List Available");
                } else {
                    System.out.println("Sender is " 
                                       + addresses[0].toString());
                }

                System.out.println("Receiver Count " 
                                   + stat.getReceiverCount());

                if (stat.getSenderList() == null) {
                    System.out.println("No Receiver List Available");
                } else {
                    System.out.println("Receiver List Available");
                }

                System.out.println("Total Data Sent is " 
                                   + stat.getTotalDataSent());
            } catch (com.sun.multicast.util.UnsupportedException ue) {
                System.out.println("Statistics block Currently Unsupported");
            }

            so.close();
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        dataRate = (bytesSent * 1000) / elapsedTime;

        System.out.println("Average data rate = " + dataRate);
    }

}

