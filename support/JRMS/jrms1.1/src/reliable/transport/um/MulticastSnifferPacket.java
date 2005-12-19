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
 */
package com.sun.multicast.reliable.transport.um;

import java.net.*;
import java.io.*;
import com.sun.multicast.util.Util;

class MulticastSnifferPacket {

    public static void main(String[] args) {
        InetAddress ia = null;
        byte[] buffer = new byte[80];
        int port = 0;
        long nextPacket = 1;

        /* Read the address and port from the command line */

        try {
            ia = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("Usage: java MulticastSniffer address port");
            System.exit(1);
        }

        /*
         * Create a new transport Profile with the multicast address and port
         * specified in the command line. Create a new RMSocket with the
         * TransportProfile.
         */
        try {
            UMTransportProfile tp = new UMTransportProfile(ia, port);
            UMPacketSocket so = (UMPacketSocket) 
		tp.createRMPacketSocket(UMTransportProfile.RECEIVER);

            /*
             * The following loop reads data from the stream. The
             * read completes after the buffer is filled. The byte
             * stream is converted to a String and printed to standard
             * ouput.
             */
            while (true) {
                DatagramPacket dp = so.receive();
                byte buff[] = dp.getData();
                long packetNumber = Util.readUnsignedInt(buff, 0);

                if (nextPacket != packetNumber) {
                    System.out.println(
			"Packet received out of order or packets missing " 
                        + nextPacket);

                    nextPacket = packetNumber;
                }

                System.out.println("Received packet number " + packetNumber);

                nextPacket++;
            }
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
