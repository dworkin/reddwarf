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

package com.sun.multicast.reliable.applications.mctest;

import java.net.*;
import java.io.*;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.tram.*;
import com.sun.multicast.reliable.authentication.*;
import java.security.*;

class MCTestWithSecurity {
    static InetAddress channel, control;
    static int maxBuf = 400;
    static int header = 152;

    // static byte[] salt = { (byte) 0xc7,
    // (byte) 0x73,
    // (byte) 0x21,
    // (byte) 0x8c,
    // (byte) 0x7e,
    // (byte) 0xc8,
    // (byte) 0xee,
    // (byte) 0x99 };

    private static void usage() {
        System.out.println("bad configuration");
        System.exit(-1);
    }

    public static void main(String args[]) {
        try {
            if (args.length < 1) {
                usage();
            } 

            control = InetAddress.getByName("224.100.100.20");
            channel = InetAddress.getByName("224.100.100.21");

            int port = 4323, connections = 1;
            String host = "localhost";
            int logMask = TRAMTransportProfile.LOG_NONE;

            switch (args[0].charAt(0)) {

            case 'S': 

            case 's': 
                switch (args.length) {

                case 3: 
                    if (args[2].charAt(0) == 't') {
                        logMask = TRAMTransportProfile.LOG_DIAGNOSTICS;
                    } 

                case 2: 
                    port = Integer.parseInt(args[1]);

                case 1: 
                    (new MCTestWithSecurity()).invokeServer(port, logMask);

                    break;
                }

            case 'C': 

            case 'c': 
                switch (args.length) {

                case 3: 
                    if (args[2].charAt(0) == 't') {
                        logMask = TRAMTransportProfile.LOG_DIAGNOSTICS;
                    } 

                case 2: 
                    port = Integer.parseInt(args[1]);

                case 1: 
                    (new MCTestWithSecurity()).invokeClient(port, logMask);

                    return;
                }

            case 'A': 

            case 'a': 
                switch (args.length) {

                case 3: 
                    port = Integer.parseInt(args[2]);

                case 2: 
                    host = args[1];

                case 1: 
                    (new MCTestWithSecurity()).invokeAdminClient();

                    return;
                }
            }

            usage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void invokeServer(int port, 
                      int logMask) throws IOException, Exception {
        TRAMTransportProfile tp;
        RMPacketSocket ps;
        MulticastSocket admin;

        tp = new TRAMTransportProfile(channel, 4321);

        tp.setTTL((byte) 5);
        tp.setOrdered(false);
        tp.setUnicastPort(port);
        tp.setMrole(MROLE.MEMBER_RELUCTANT_HEAD);
        tp.setLogMask(logMask);
        tp.setMaxDataRate(200000);
        tp.setMaxBuf(maxBuf);
        tp.enableAuthentication();
        tp.setAuthenticationSpecFileName("s1.spec");

        ps = tp.createRMPacketSocket(TMODE.SEND_ONLY);
        admin = new MulticastSocket(4000);

        admin.joinGroup(control);

        char[] chars = 
            ("The quick fox jumps over the lazy brown dog.").toCharArray();

        // Wait for signal to start

        System.out.println(
	    "\n\n\n\n\n\n\n\nWaiting for signal to start.\n\n\n\n\n\n\n\n");
        admin.receive(new DatagramPacket(new byte[10], 1));
        System.out.println(
	    "\n\n\n\n\n\n\n\nStart to send data.\n\n\n\n\n\n\n\n");

        byte[] dat = new byte[maxBuf - header];

        for (int i = 0; i < dat.length; i++) {
            dat[i] = 9;
        }

        System.out.println("Data packet length = " + dat.length);

        DatagramPacket dp = new DatagramPacket(dat, dat.length, channel, 
                                               4321);
        long startTime = System.currentTimeMillis();

        for (int i = 1; ; i++) {
            dat[0] = (byte) (i >>> 24);
            dat[1] = (byte) ((i >>> 16) & 0xff);
            dat[2] = (byte) ((i >>> 8) & 0xff);
            dat[3] = (byte) (i & 0xff);

            ps.send(dp);
        }

    // ps.close();
    // long deltaTime = System.currentTimeMillis() - startTime;
    // System.out.println("Time to send 10,000 packets = "+deltaTime);

    }

    void invokeClient(int port, 
                      int logMask) throws IOException, Exception {
        TRAMTransportProfile tp;
        RMPacketSocket ps;
        MulticastSocket admin;

        tp = new TRAMTransportProfile(channel, 4321);

        // tp.setTTL((byte)5);

        tp.setOrdered(false);
        tp.setUnicastPort(port);
        tp.setLogMask(logMask);
        tp.setMaxBuf(maxBuf);
        tp.setLateJoinPreference(
	    TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY);
        tp.enableAuthentication();
        tp.setAuthenticationSpecFileName("r1.spec");

        ps = tp.createRMPacketSocket(TMODE.RECEIVE_ONLY);
        admin = new MulticastSocket(4000);

        admin.joinGroup(control);

        // Wait for signal to start

        System.out.println(
	    "\n\n\n\n\n\n\n\nWaiting for signal to start.\n\n\n\n\n\n\n\n");
        admin.receive(new DatagramPacket(new byte[10], 1));
        System.out.println(
	    "\n\n\n\n\n\n\n\nStart to receive data.\n\n\n\n\n\n\n\n");

        DatagramPacket dp;

        // OutputStream os = new HexOutputStream(new WindowOutputStream());

        long startTime = 0;

        for (int i = 1; ; i++) {
            try {
                dp = ps.receive();

                if (i == 1) {
                    startTime = System.currentTimeMillis();
                } 

                byte[] dat = dp.getData();
                int len = dat.length;
                int pkNumber = ((dat[0] & 0xff) << 24) 
                               + ((dat[1] & 0xff) << 16) 
                               + ((dat[2] & 0xff) << 8) + (dat[3] & 0xff);

                System.out.println("<" + len + ">     " + pkNumber + " " + i);
            } catch (SessionDoneException se) {
                ps.close();

                long deltaTime = System.currentTimeMillis() - startTime;

                System.out.println("Received " + i + " packets in " 
                                   + deltaTime + "ms");

                break;
            } catch (SessionDownException sd) {
                ps.close();

                long deltaTime = System.currentTimeMillis() - startTime;

                System.out.println("Received " + i + " packets in " 
                                   + deltaTime + "ms");

                break;
            }
        }
    }

    void invokeAdminClient() throws IOException, Exception {
        MulticastSocket admin = new MulticastSocket(4000);

        admin.joinGroup(control);

        // Send start signal

        DatagramPacket p = new DatagramPacket(new byte[10], 10, control, 
                                              4000);

        admin.send(p);
        admin.send(p);
        admin.send(p);
        admin.send(p);
        System.out.println(
	    "\n\n\n\n\n\n\n\nStart signal sent.\n\n\n\n\n\n\n\n");
    }

}
