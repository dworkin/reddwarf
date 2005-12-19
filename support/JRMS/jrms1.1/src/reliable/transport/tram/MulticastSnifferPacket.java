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
 * MulticastSnifferPacket
 * 
 * Module Description:
 * 
 * This module implements the receiving side of a multicast application.
 * It creates a transport profile for the TRAM Multicast protocol
 * setting the address, port, number of packets, and the data rate fields
 * according to user input. It then sends datagram packets of random length
 * between 0 and 65k bytes. The user may set a fixed packet size as an
 * alternative.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import java.io.*;
import java.awt.Dimension;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.SessionDoneException;
import com.sun.multicast.reliable.transport.TransportProfile;

class MulticastSnifferPacket {

    public static void main(String[] args) {
        InetAddress ia = null;
        byte buffer[];
        int port = 0;
        int uport = 0;
        int length = 0;
        int packetNumber = 0;
        int packetLength = 0;
        DatagramPacket dp = null;
        long nextPacket = 1;
        int SEQUENCENUMBER = 0;
        int PACKETLENGTH = 4;
        int HEADER = 8;
        bricks brickLayer = new bricks(new Dimension(600, 600), 
                                       "Receiver Brick Layer");

        /* Read the address and port from the command line */

        try {
            ia = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);

            if (args.length > 2) {
                uport = Integer.parseInt(args[2]);
            } 
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(
		"Usage: java MulticastSniffer address port");
            System.exit(1);
        }

        /*
         * Create a new transport Profile with the multicast address and port
         * specified in the command line. Create a new RMSocket with the
         * TransportProfile.
         */
        TRAMPacketSocket so = null;

        try {
            TRAMTransportProfile tp = new TRAMTransportProfile(ia, port);

            tp.setOrdered(false);

            if (uport != 0) {
                tp.setUnicastPort(uport);
            } 

            so = (TRAMPacketSocket)
		tp.createRMPacketSocket(TransportProfile.RECEIVER);

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
                    packetNumber = Util.readInt(buffer, SEQUENCENUMBER);
                    packetLength = Util.readInt(buffer, PACKETLENGTH);

                    brickLayer.addBrick(packetNumber);
                    System.out.println("Received Packet Number " 
                                       + packetNumber + "Length = " +
				       (length - HEADER));

                    if (packetNumber != nextPacket) {
                        System.out.println("Packet received out of order");
                        System.out.println("Packet received = " 
                                           + packetNumber);

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

            // if (nextPacket == 10)
            // break;

            }
        } catch (SessionDoneException eof) {
            System.out.println(eof);
            System.out.println("Closing the Socket");

            try {
                TRAMStats stat = (TRAMStats) so.getRMStatistics();

                System.out.println("Sender Count " + stat.getSenderCount());

                InetAddress[] addresses = stat.getSenderList();

                if (addresses == null) {
                    System.out.println("No Sender List Available");
                } else {
                    System.out.println("Sender is " + addresses[0]);
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
            } catch (com.sun.multicast.util.UnsupportedException ue) {}
            catch (NullPointerException ne) {}

            so.close();
            System.out.println("Sniffer Exiting");
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e);
            System.exit(1);
        }

        so.close();
    }

}

