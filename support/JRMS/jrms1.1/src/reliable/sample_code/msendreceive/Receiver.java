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

public class Receiver {

    public static void main(String args[]) {
	InetAddress group = null;

        if (args.length < 1) {
            System.out.println(
		"Usage:  java Receiver <multicast address> [<interface>]");
            System.exit(1);
        }

        try {
            group = InetAddress.getByName(args[0]);
	} catch (UnknownHostException uh) {
	    System.out.println("Bad multicast address " + args[0]);
	    System.exit(1);
 	}

	MulticastSocket s = null;

	try {
	    s = new MulticastSocket(6666);

	    if (args.length >= 2) {
	        System.out.println("interface " + args[1]);
	        s.setInterface(InetAddress.getByName(args[1]));
	    }

	    s.joinGroup(group);
	} catch (IOException ioe) {
	    System.out.println("Can't create multicast socket!");
	    System.exit(1);
	}
	
	byte[] buf = new byte[1000];
	DatagramPacket recv = new DatagramPacket(buf, buf.length);

	long lastTime = 0;
	int seq = 1;
	int missedPackets = 0;

	while (true) {
	    try {
 		s.receive(recv);

		int actualSeq = (int)
		    (((buf[0] << 24) & 0xff000000) +
		    ((buf[1] << 16) & 0xffff00) +
		    ((buf[2] << 8) & 0xff00) + 
		    ((buf[3] & 0xff)));

		if (lastTime != 0 && actualSeq != seq) {
		    System.out.println("Out of sequence packet.  Expected " +
			seq + " Got " + actualSeq);

		    if (actualSeq < seq) {
			System.out.println("Received earlier packet " + 
			    actualSeq);
		    } else
		        missedPackets += (actualSeq - seq);
		}
		
		seq = actualSeq + 1;

		long currentTime = System.currentTimeMillis();

		if (lastTime == 0)
		    lastTime = currentTime;

		double secsSinceLastPacket = 
		    (double)(currentTime - lastTime) / 1000.;

		lastTime = currentTime;

		System.out.println(
		    secsSinceLastPacket + " received packet " + actualSeq +
		    " missed " + missedPackets);
	    } catch (IOException ioe) {
	        System.out.println("Can't receiver on multicast socket!");
	        System.exit(1);
	    }
	}
    }

}
