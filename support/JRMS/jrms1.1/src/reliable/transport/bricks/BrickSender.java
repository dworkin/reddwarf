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
 * BrickSender.java
 */
package com.sun.multicast.reliable.transport.bricks;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.awt.Dimension;
import com.sun.multicast.util.TestFailedException;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;

class BrickSender {

    /*
     * Note: The largest packet we can send in java UDP is 65507 bytes.
     * We need to settle on a header max size and advertise the max an
     * application can use.
     */
    static final int MAX_PACKET = 65400;
    static final int HEADER = 12;
    static final int PACKETNUMBER = 0;
    static final int PACKETCOUNT = 4;
    static final int PACKETSIZE = 8;
    InetAddress ia;
    int port = 0;
    int count = 0;
    int packetSize = MAX_PACKET;
    boolean randomPacket = true;
    long dataRate = 65535;
    boolean test;
    long bytesSent = 0;
    long startTime = 0;
    BrickLayer2 brickLayer;
    String transportName = "STP";
    int packetNumber = 1;

    BrickSender() {}

    void usage() {
        System.out.println(
	    "Usage: java BrickSender [flags] addr port packetCount");
        System.out.println(" where flags may include:");
        System.out.println(
	    "         -rate n         to set the maximum data rate " +
	    "in bits per second");
        System.out.println("         -packetSize n   to set the packet size");
        System.out.println("         -transport name to set the transport " +
	    "(default is STP)");
    }

    /* read the address and port from the command line */

    void parseArgs(String[] args) throws Exception {
        int mainArgs = 0;

        try {
            for (int i = 0; i < args.length; i++) {

                // @@@ Should check for duplicate flags

                if (args[i].startsWith("-")) {
                    if (args[i].equals("-rate")) {
                        if (i + 1 >= args.length) {
                            throw new TestFailedException();
                        } 

                        i += 1;
                        dataRate = Integer.parseInt(args[i]);
                    } else if (args[i].equals("-packetSize")) {
                        if (i + 1 >= args.length) {
                            throw new TestFailedException();
                        } 

                        i += 1;
                        packetSize = Integer.parseInt(args[i]);
                        randomPacket = false;
                    } else if (args[i].equals("-transport")) {
                        if (i + 1 >= args.length) {
                            throw new TestFailedException();
                        } 

                        i += 1;
                        transportName = args[i];
                    } else {
                        throw new TestFailedException();
                    }
                } else {
                    switch (mainArgs) {

                    case 0: 
                        ia = InetAddress.getByName(args[i]);

                        break;

                    case 1: 
                        port = Integer.parseInt(args[i]);

                        break;

                    case 2: 
                        count = Integer.parseInt(args[i]);

                        break;

                    default: 
                        throw new TestFailedException();
                    }

                    mainArgs += 1;
                }
            }

            if (mainArgs != 3) {
                throw new TestFailedException();
            } 

            System.out.println("Multicast Address   = " 
                               + ia.getHostAddress());
            System.out.println("Multicast Port      = " + port);
            System.out.println("Repeat Count        = " + count);
            System.out.println("Packet size         = " + packetSize);

            if (randomPacket) {
                System.out.println("Packet Distribution = random");
            } else {
                System.out.println("Packet Distribution = fixed");
            }

            System.out.println("Data rate           = " + dataRate);
            System.out.println("Transport name      = " + transportName);
        } catch (Exception e) {
            usage();

            throw e;
        }
    }

    void runTest() throws Exception {
        brickLayer = new BrickLayer2(new Dimension(600, 600), 
                                     "Sending Bricklayer");

        for (int i = 1; i <= count; i++) {
            brickLayer.addBrick(i);
        }

        TransportProfile tp = null;

        if (transportName.equals("STP")) {

            /*
             * Obtain a new Transport Profile with the address and
             * port specified. Without making any other modifications,
             * create an RMSocket for this profile. All other fields
             * in the TransportProfile will have default values.
             */
            TRAMTransportProfile stptp = new TRAMTransportProfile(ia, port);

            tp = stptp;

            stptp.setMaxDataRate(dataRate);
            stptp.setTTL((byte) 1);
            stptp.setBeaconRate((long) 1000);
            stptp.setOrdered(false);
        }
        if (transportName.equals("LRMP")) {

            /*
             * Obtain a new Transport Profile with the address and
             * port specified. Without making any other modifications,
             * create an RMSocket for this profile. All other fields
             * in the TransportProfile will have default values.
             */
            LRMPTransportProfile lrmptp = new LRMPTransportProfile(ia, port);

            tp = lrmptp;

            lrmptp.setMaxDataRate(dataRate);
            lrmptp.setTTL((byte) 1);
            lrmptp.setOrdered(false);
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

        RMPacketSocket so = tp.createRMPacketSocket(TransportProfile.SENDER);

        // Create a byte array with a header and send it. Loop.

        startTime = System.currentTimeMillis();

        Thread.sleep(1000);

        for (int i = count; i > 0; i--) {
            brickLayer.removeBrick(i);

            if (randomPacket) {
                packetSize = (int) (Math.random() * (MAX_PACKET - HEADER));
            }

            byte data[] = new byte[packetSize + HEADER];

            Util.writeInt(packetNumber++, data, PACKETNUMBER);
            Util.writeInt(count, data, PACKETCOUNT);
            Util.writeInt(packetSize, data, PACKETSIZE);

            for (int j = HEADER; j < packetSize + HEADER; j++) {
                data[j] = 56;
            }

            bytesSent += (long) packetSize;

            DatagramPacket dp = new DatagramPacket(data, data.length, ia, 
                                                   port);

            System.out.println("Sending packet " + i + "Size " + packetSize);
            so.send(dp);
        }

        /* Miller time */

        so.close();

        long elapsedTime = System.currentTimeMillis() - startTime;

        dataRate = (bytesSent * 1000) / elapsedTime;

        System.out.println("Average data rate = " + dataRate);
    }

    public static void main(String[] args) {
        try {
            BrickSender sender = new BrickSender();

            sender.parseArgs(args);
            sender.runTest();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }
}
