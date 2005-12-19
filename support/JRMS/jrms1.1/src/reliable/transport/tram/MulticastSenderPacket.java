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
package com.sun.multicast.reliable.transport.tram;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.awt.Dimension;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.*;

class MulticastSenderPacket {

    public static void main(String[] args) {

        /*
         * Note: The largest packet we can send in java UDP is 65507 bytes.
         * We need to settle on a header max size and advertise the max an
         * application can use.
         */
        final int MAX_PACKET = 65400;
        final int HEADER = 8;
        final int PACKETNUMBER = 0;
        final int PACKETSIZE = 4;
        InetAddress ia = null;
        int port = 0;
        int uport = 0;
        int count = 0;
        int packetSize = MAX_PACKET;
        boolean randomPacket = true;
        long dataRate = 65535;
        boolean test;
        long bytesSent = 0;
        long startTime = 0;
        bricks brickLayer;
        int packetNumber = 1;

        /* read the address and port from the command line */

        if (args.length < 3) {
            System.out.println("Usage: java MulticastSenderPacket addr " +
			       "port packetCount [dataRate] [packetSize]");
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
            if (args.length >= 6) {
                uport = Integer.parseInt(args[5]);
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("Usage: java MulticastSender address port");
            System.exit(1);
        }

        System.out.println("Multicast Address   = " + ia.getHostAddress());
        System.out.println("Multicast Port      = " + port);

        if (uport != 0) {
            System.out.println("Unicast Port        = " + uport);
        } 

        System.out.println("Repeat Count        = " + count);
        System.out.println("Packet size         = " + packetSize);

        if (randomPacket) {
            System.out.println("Packet Distribution = random");
        } else {
            System.out.println("Packet Distribution = fixed");
        }

        System.out.println("Data rate           = " + dataRate);

        brickLayer = new bricks(new Dimension(600, 600), 
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

            test = false;

            try {
                tp.setAddress(InetAddress.getByName("0.0.0.0"));
            } catch (InvalidMulticastAddressException e) {
                test = true;
            }

            if (!test) {
                System.out.println("Failed setAddress invalid test.");
            }

            TRAMPacketSocket so = (TRAMPacketSocket)
		tp.createRMPacketSocket(TransportProfile.SENDER);

            /*
             * The following loop obtains a string, converts it to a byte
             * array, writes it to the RMSocket one byte at a time, and
             * issues a flush() call when the entire string has been written.
             * The flush() forces the transport to send the data out.
             */
            startTime = System.currentTimeMillis();

            boolean retransmit = false;

            Thread.sleep(1000);

            for (int i = count; i > 0; i--) {
                brickLayer.removeBrick(i);

                if (randomPacket) {
                    packetSize = (int) (Math.random() 
                                        * (MAX_PACKET - HEADER));
                }

                byte data[] = new byte[packetSize + HEADER];

                Util.writeInt(packetNumber++, data, PACKETNUMBER);
                Util.writeInt(packetSize, data, PACKETSIZE);

                for (int j = HEADER; j < packetSize + HEADER; j++) {
                    data[j] = 56;
                }

                bytesSent += (long) packetSize;

                DatagramPacket dp = new DatagramPacket(data, data.length, ia, 
                                                       port);

                System.out.println("Sending packet " + i + "Size " 
                                   + packetSize);
		while (true) {
		    try {
			so.send(dp);
			break;
		    } catch (NoMembersException ne) {
			try {
			    System.out.println("Waiting for membership");
			    Thread.sleep(1000);
			} catch (InterruptedException ie) {}
		    }
		}
            }

            /* Miller time */

            System.out.println("GETTING Statistics ");

            TRAMStats stat = (TRAMStats) so.getRMStatistics();

            System.out.println("Sender Count " + stat.getSenderCount());

            InetAddress[] addresses = stat.getSenderList();

            if (addresses == null) {
                System.out.println("No Sender List Available");
            } else {
                System.out.println("Sender is " + addresses[0].toString());
            }

            System.out.println("Receiver Count " + stat.getReceiverCount());

            if (stat.getSenderList() == null) {
                System.out.println("No Receiver List Available");
            } else {
                System.out.println("Receiver List Available");
            }

            System.out.println("Total Data Sent is " 
                               + stat.getTotalDataSent());
            so.close();

        // Thread.sleep(20000);
        // so.abort();

        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        dataRate = (bytesSent * 1000) / elapsedTime;

        System.out.println("Average data rate = " + dataRate);
        System.exit(0);
    }

}

