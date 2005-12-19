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
 * BrickReceiver
 */
package com.sun.multicast.reliable.transport.bricks;

import java.net.*;
import java.io.*;
import java.awt.Dimension;
import com.sun.multicast.util.TestFailedException;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;

class BrickReceiver {
    InetAddress ia = null;
    byte buffer[];
    int port = 0;
    int length = 0;
    int packetNumber = 0;
    int packetLength = 0;
    int count = 0;
    DatagramPacket dp = null;
    long nextPacket = 1;
    String transportName = "STP";
    static final int HEADER = 12;
    static final int PACKETNUMBER = 0;
    static final int PACKETCOUNT = 4;
    static final int PACKETSIZE = 8;

    BrickReceiver() {}

    void usage() {
        System.out.println("Usage: java BrickReceiver [flags] addr port");
        System.out.println(" where flags may include:");
        System.out.println(
	    "         -transport name to set the transport (default is STP)");
    }

    /* read the address and port from the command line */

    void parseArgs(String[] args) throws Exception {
        int mainArgs = 0;

        try {
            for (int i = 0; i < args.length; i++) {

                // @@@ Should check for duplicate flags

                if (args[i].startsWith("-")) {
                    if (args[i].equals("-transport")) {
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

                    default: 
                        throw new TestFailedException();
                    }

                    mainArgs += 1;
                }
            }

            if (mainArgs != 2) {
                throw new TestFailedException();
            } 
        } catch (Exception e) {
            usage();

            throw e;
        }
    }

    void runTest() throws Exception {
        BrickLayer2 brickLayer = new BrickLayer2(new Dimension(600, 600), 
                                                 "Receiver Brick Layer");
        RMPacketSocket so = null;
        TransportProfile tp = null;

        /*
         * Create a new transport Profile with the multicast address and port
         * specified in the command line. Create a new RMSocket with the
         * TransportProfile.
         */
        if (transportName.equals("STP")) {
            tp = new TRAMTransportProfile(ia, port);
        } else if (transportName.equals("LRMP")) {
            tp = new LRMPTransportProfile(ia, port);
        } else {
            throw new TestFailedException();
        }

        tp.setOrdered(false);

        so = tp.createRMPacketSocket(TransportProfile.RECEIVER);

        /*
         * The following loop reads data from the stream. The
         * read completes after the buffer is filled. The byte
         * stream is converted to a String and printed to standard
         * ouput.
         */
        while (true) {
            dp = so.receive();
            buffer = dp.getData();
            length = dp.getLength();

            if (length >= HEADER) {
                packetNumber = Util.readInt(buffer, PACKETNUMBER);
                count = Util.readInt(buffer, PACKETCOUNT);
                packetLength = Util.readInt(buffer, PACKETSIZE);

                brickLayer.addBrick(packetNumber);
                System.out.println("Received Packet Number " + packetNumber);

                if (packetNumber > count) {
                    System.out.println("Packet beyond count!");
                }
                if (packetNumber != nextPacket) {
                    System.out.println("Packet received out of order");
                    System.out.println("Packet received = " + packetNumber);

                    nextPacket = packetNumber;
                }
                if (packetLength != (length - HEADER)) {
                    System.out.print("Incorrect packet length. ");
                    System.out.print("Received = " + length);
                    System.out.println(" Expected = " + packetLength);
                }

                for (int i = HEADER; i < length; i++) {
                    if (buffer[i] != (byte) 56) {
                        System.out.println("Buffer corrupted");

                        break;
                    }
                }

                nextPacket++;
            } else {
                System.out.println("RUNT packet");
            }

            // Don't wait for all packets if they're out of order.
            // When we see the last one, take off.

            if (packetNumber >= count) {
                break;
            } 
        }

        so.close();
    }

    public static void main(String[] args) {
        try {
            BrickReceiver receiver = new BrickReceiver();

            receiver.parseArgs(args);
            receiver.runTest();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }
}
