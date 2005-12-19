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

package com.sun.multicast.test;

import java.util.*;
import java.io.IOException;
import java.net.*;

public class Sender {

    public static void main(String args[]) {
        // join a Multicast group and send the group salutations

        byte[] msg = {0, 0, 0, 0};

	InetAddress group = null;

	if (args.length == 0) {
	    System.out.println("Usage:  java Sender <multicast address> [pps]");
	    System.exit(1);
	}

	try {
	    group = InetAddress.getByName(args[0]);
	} catch (UnknownHostException uh) {
	    System.out.println("Bad multicast address " + args[0]);
	    System.exit(1);
 	}

	int pps = 1;	// default is 1 packet per second

	if (args.length == 2)
	    pps = Integer.parseInt(args[1]);

	MulticastSocket s = null;

	try {
	    s = new MulticastSocket(6666);
	    s.joinGroup(group);
	} catch (IOException ioe) {
	    System.out.println("Can't create multicast socket!");
	    System.exit(1);
	}
	
	long lastTime = System.currentTimeMillis();
	int seq = 1;
	
	while (true) {
	    msg[0] = (byte)((seq >> 24) & 0xff);
	    msg[1] = (byte)((seq >> 16) & 0xff);
	    msg[2] = (byte)((seq >> 8) & 0xff);
	    msg[3] = (byte)(seq & 0xff);

	    DatagramPacket m = new DatagramPacket(msg, msg.length,
       	        group, 6666);

	    try {
	        s.send(m, (byte)20);

                long currentTime = System.currentTimeMillis();

                double secsSinceLastPacket = 
                    (double)(currentTime - lastTime) / 1000.;

                lastTime = currentTime;

                System.out.println(
                    secsSinceLastPacket + " sent packet " + seq);

	        seq++;
	    } catch (IOException ioe) {
	        System.out.println("Can't send on multicast socket!");
	        System.exit(1);
	    }

	    int sleepTime = (int)((double)1000 / pps);

	    try {
		Thread.sleep(sleepTime);
	    } catch (InterruptedException ie) {
            }
	}
    }

}
