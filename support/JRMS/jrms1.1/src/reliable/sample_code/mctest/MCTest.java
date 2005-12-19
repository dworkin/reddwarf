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

class MCTest {
    static InetAddress channel;
    static InetAddress control;
    static int maxBuf = 1500;
    static int headerLen = 152;
    static int sessionTTL = 20;
    static String sendFileName = "/tmp/mctestSend.txt";
    static String receiveFileName = "/tmp/mctestReceive.txt";
    static int minDataRate = 1000;
    static int maxDataRate = 400000;
    static short ackWindow = 32;
    static String channelAddr = "224.100.100.21";
    static int logMask = TRAMTransportProfile.LOG_VERBOSE;
    static int dataPort = 6000;
    static int controlPort;
    static int serverUnicastPort;
    static int clientUnicastPort;
    static int receiverCount;
    static int repairWaitTime = 10000;
    static boolean useTcp = false;
    static String senderHost;
    static boolean synchronize = false;

    // static byte[] salt = { (byte) 0xc7,
    // (byte) 0x73,
    // (byte) 0x21,
    // (byte) 0x8c,
    // (byte) 0x7e,
    // (byte) 0xc8,
    // (byte) 0xee,
    // (byte) 0x99 };

    private static void usage() {
        System.out.println(
	  "usage:  -c <client_options> <common_options> or\n" +
	  "        -s <sender_options> <common_options> or\n" +
	  "        -g (signal sender to start sending)\n\n" +
	  "    sender_options:\n" +
	  "        -i <sendFile>\n" +
	  "        -r <minDataRate>\n" +
	  "        -t (use TCP)\n" +
	  "        -R <maxDataRate>\n" +
	  "        -S (synchronize with receivers.  Wait for signal.)\n\n" +
	  "    client_options:\n" +
	  "        -o <receiveFile>\n" +
	  "        -h <senderTCPHost>\n" +
	  "        -w <ackWindow>\n" +
	  "        -W <maxCongestoinWindow>\n\n" +
	  "    common_options:\n" +
	  "        -a <multicastAddress>\n" +
	  "        -m <logMask>\n" +
	  "        -p <port>\n" +
	  "        -v (verbose logging)\n" +
	  "        -w <ackWindow>\n");
 
        System.exit(-1);
    }

    public static void main(String args[]) {
        try {
            if (args.length < 1) {
                usage();
            } 
            if (args[0].charAt(0) != '-') {
                usage();
            } 

            char command = args[0].charAt(1);

            for (int i = 1; i < args.length; i++) {
		if (args[i].charAt(0) != '-') {
                    usage();
                }

                switch (args[i].charAt(1)) {
 	        case 'a': 
                    i++;

                    if (i >= args.length) {
                        usage();
                    } 

                    channelAddr = args[i];
                    break;

	        case 'h':
                    if (command != 'c') {
                        usage();
                    } 
                    i++;

                    if (i >= args.length) {
                        usage();
                    } 
	   	    senderHost = args[i];
		    useTcp = true;
		    break;

		case 'i': 
                    if (command != 's') {
                        usage();
                    } 

                    i++;

                    if (i >= args.length) {
                        usage();
                    } 

                    sendFileName = args[i];
                    break;

                case 'm': 
                    i++;

                    if (i >= args.length) {
                        usage();
                    } 

                    logMask = Integer.parseInt(args[i], 16);
                    break;

                case 'o': 
                    if (command != 'c') {
                        usage();
                    } 

                    i++;
    
                    if (i >= args.length) {
                        usage();
                    } 

                    receiveFileName = args[i];
                    break;

                case 'p': 
                    i++;
    
                    if (i >= args.length) {
                        usage();
                    } 

                    dataPort = Integer.parseInt(args[i]);
                    break;

                case 'r': 
                    i++;
    
                    if (i >= args.length) {
                        usage();
                    } 

                    minDataRate = Integer.parseInt(args[i]);
                    break;

                case 'R': 
                    i++;
    
                    if (i >= args.length) {
                        usage();
                    } 

                    maxDataRate = Integer.parseInt(args[i]);
                    break;
    
		case 'S':
    	            synchronize = true;
    		    break;

   		case 't':
                    if (command != 's') {
                        usage();
	            } 
		    useTcp = true;
		    break;

		case 'v': 
                    logMask = TRAMTransportProfile.LOG_VERBOSE;
                    break;

		case 'w': 
		    i++;

		    if (i >= args.length) {
                        usage();
                    } 

                    ackWindow = (short)(Integer.parseInt(args[i]) & 0xffff);
                    break;

		default: 
		    usage();
                }
	    }

            channel = InetAddress.getByName(channelAddr); 
            controlPort = dataPort + 1;
            serverUnicastPort = dataPort + 2;
            clientUnicastPort = dataPort + 3;

            switch (command) {
            case 'g': 
            case 'G': 
                System.out.println("Address " + channelAddr);
	        System.out.println("Control Port " + controlPort);
                invokeAdminClient();

                break;

            case 's': 
                System.out.println("Send File " + sendFileName);

		if (useTcp) {
		    System.out.println("Using TCP port " + dataPort);
		    invokeTcpServer();
		} else {
                    invokeServer();
		}

                break;

            case 'c': 
                System.out.println("Receive File " + receiveFileName);

		if (useTcp) {
		    System.out.println("Using TCP port " + dataPort);
		    invokeTcpClient();
                } else {
                    invokeClient();
		}

                break;

            default: 
                usage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void invokeServer() throws IOException, Exception {
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;
        MulticastSocket admin;

        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
        tp.setUnicastPort(serverUnicastPort);
        tp.setMrole(MROLE.MEMBER_RELUCTANT_HEAD);
	tp.setLogMask(logMask);
        tp.setMinDataRate(minDataRate);
        tp.setMaxDataRate(maxDataRate);
        tp.setAckWindow(ackWindow);
        tp.setMaxBuf(maxBuf);

        System.out.println("Address " + channelAddr);
        System.out.println("Data Port " + dataPort);
        System.out.println("Control Port " + controlPort);
        System.out.println("Server Unicast Port " + serverUnicastPort);
        System.out.println("Min Data Rate " + tp.getMinDataRate());
        System.out.println("Max Data Rate " + tp.getMaxDataRate());
        System.out.println("Ack Window " + tp.getAckWindow());

        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.SEND_ONLY);

        int PACKET_SIZE = 1400;

	if (synchronize) {
            admin = new MulticastSocket(controlPort);

            admin.joinGroup(channel);

	    // Wait for signal to start sending

            System.out.println("\n\nWaiting for signal to start.\n\n");
            admin.receive(new DatagramPacket(new byte[10], 1));
	}

	try {
		Thread.sleep(1000);
	} catch (InterruptedException e) {
		;
	}
        System.out.println("\nStart to send data.\n");

        FileInputStream in = null;

        try {
            in = new FileInputStream(sendFileName);
        } catch (FileNotFoundException e) {
            System.out.println(sendFileName + "file not found.");
            System.exit(1);
        }

        int bytesRead = 0;
        long startTime = System.currentTimeMillis();

        System.out.println("Sending Data .....\n");

        int i = 0;

        try {
            byte[] dat = new byte[maxBuf - headerLen];

            while ((bytesRead = in.read(dat)) != -1) {
                DatagramPacket dp = new DatagramPacket(dat, bytesRead, 
                                               channel, dataPort);

		/*
		 * The following while loop is added to take of 
		 * the 'NoMembersException' condition. The current suggested 
		 * policy for the application is to try to send the same 
		 * packet after a while until the packet finally gets sent. 
		 */ 
		while (true) {
		    try {
			ps.send(dp);
			break;
		    } catch (NoMembersException nme) {
			/*
			 * Sleep for sometime and try to send the
			 * packet again.
			 */
			try {
			    System.out.println("Receiver member Count is 0. " +
					  "Will try to send after 1 Second");
			    Thread.sleep(1000);
			} catch (InterruptedException ie) { 
			}
			continue;
		    }
		}
		i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

	try {
		Thread.sleep(repairWaitTime);	// wait for repairs to complete!
	} catch (InterruptedException e) {
		;
	}

        TRAMStats stat = (TRAMStats) ps.getRMStatistics();
        printStats('s', stat, elapsedTime);
        ps.close();
    }

    static void invokeClient() throws IOException, Exception {
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;
        // MulticastSocket admin;

        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
        tp.setUnicastPort(clientUnicastPort);
	tp.setLogMask(logMask);
        tp.setMinDataRate(minDataRate);
        tp.setMaxDataRate(maxDataRate);
        tp.setAckWindow(ackWindow);
        tp.setMaxBuf(maxBuf);
        tp.setLateJoinPreference(
	    TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY);
        System.out.println("Address " + channelAddr);
        System.out.println("Data Port " + dataPort);
        System.out.println("Control Port " + controlPort);
        System.out.println("Client Unicast Port " + clientUnicastPort);
        System.out.println("Min Data Rate " + tp.getMinDataRate());
        System.out.println("Max Data Rate " + tp.getMaxDataRate());
        System.out.println("Ack Window " + tp.getAckWindow());
        System.out.println("Congestion Window " + tp.getCongestionWindow());

        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.RECEIVE_ONLY);

        System.out.println("\nReady to receive data.\n");

        FileOutputStream out = null;

        try {
            out = new FileOutputStream(new File(receiveFileName));
        } catch (IOException e) {
            System.out.println("Cannot create output file ");
        }

        DatagramPacket dp;

        // OutputStream os = new HexOutputStream(new WindowOutputStream());

        long startTime = 0;

	//
	// Need to get this now!  By the time we get the SessionDoneException,
	// the members are gone.
	//
        TRAMStats stat = (TRAMStats) ps.getRMStatistics();
	receiverCount = stat.getReceiverCount();

        for (int i = 1; ; i++) {
            try {
		// System.out.println("waiting to receive...");
                dp = ps.receive();
		// System.out.println("got packet " + i);

		if (startTime == 0)
 		    startTime = System.currentTimeMillis();

                out.write(dp.getData());
            } catch (SessionDoneException se) {
                long elapsedTime = 
		    System.currentTimeMillis() - startTime - repairWaitTime;

		stat = (TRAMStats) ps.getRMStatistics();
                printStats('c', stat, elapsedTime);
                ps.close();
                out.close();

                System.exit(2);		// tell script to restart
                break;
            } catch (SessionDownException sd) {
                ps.close();

		System.out.println("The Sender stopped sending!");
                System.exit(2);		// tell script to restart
                break;
            } catch (MemberPrunedException mp) {
		System.out.println("Member pruned from the tree!");
                ps.abort();

                System.exit(1);
                break;
	    }
        }
    }

    static void invokeTcpServer() throws IOException, Exception {
        FileInputStream in = null;

        try {
            in = new FileInputStream(sendFileName);
        } catch (FileNotFoundException e) {
            System.out.println(sendFileName + "file not found.");
            System.exit(1);
        }

	ServerSocket serverSocket = new ServerSocket(dataPort);
	Socket socket = serverSocket.accept();
	
	System.out.println("Connected to " + socket.getInetAddress() +
	    ", sending data...");

	OutputStream outputStream = socket.getOutputStream();

	long totalDataSent = 0;
        long startTime = 0;

        try {
            byte[] dat = new byte[maxBuf - headerLen];

            int bytesRead = 0;
            startTime = System.currentTimeMillis();

            while ((bytesRead = in.read(dat)) > 0) {
                outputStream.write(dat, 0, bytesRead);
		totalDataSent += bytesRead;
            }
        } catch (Exception e) {
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
	System.out.println(
	    "Sent " + totalDataSent + " in " + (elapsedTime/1000) +
	    " seconds, " + ((totalDataSent * 1000) / elapsedTime) + 
	    " bytes/sec");

	serverSocket.close();
	socket.close();
        outputStream.close();
	in.close();
    }

    static void invokeTcpClient() throws IOException, Exception {
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(new File(receiveFileName));
        } catch (IOException e) {
            System.out.println("Cannot create output file ");
        }

	Socket socket = new Socket(senderHost, dataPort);
	InputStream socketInputStream = socket.getInputStream();

        long startTime = 0;
	long totalDataReceived = 0;

        byte[] dat = new byte[maxBuf - headerLen];

        try {
            int bytesWritten;

	    while ((bytesWritten = socketInputStream.read(dat)) > 0) {
	        if (startTime == 0) {
 		    startTime = System.currentTimeMillis();
		    System.out.println("Connected, receiving data...");
		}

                out.write(dat, 0, bytesWritten);
	        totalDataReceived += bytesWritten;
	    }
        } catch (Exception e) {
        }

	long elapsedTime = System.currentTimeMillis() - startTime;
	System.out.println("Received " + totalDataReceived + " in " + 
	    (elapsedTime/1000) + " seconds, " + 
	    ((totalDataReceived * 1000) / elapsedTime) + " bytes/sec");

        socket.close();
	socketInputStream.close();
        out.close();
    }

    static void printStats(char role, TRAMStats stat, long elapsedTime) {
        try {
            System.out.println("Sender Count " + stat.getSenderCount());

            InetAddress[] addresses = stat.getSenderList();

            if (addresses == null) {
                System.out.println("No Sender List Available");
            } else {
                System.out.println("Sender is " + addresses[0]);
            }

            if (role == 's') {
                System.out.println("Total Group Members " +
                    stat.getReceiverCount());
	    }

            System.out.println("Direct Member Count " +
                stat.getDirectMemberCount());

            System.out.println("Indirect Member Count " +
                stat.getIndirectMemberCount());

            System.out.println("Peak Members " + stat.getPeakMembers());
            System.out.println("Pruned Members " + stat.getPrunedMembers());
            System.out.println("Lost Members " + stat.getLostMembers());

            System.out.println("Packets Sent " + stat.getPacketsSent());
            System.out.println("Data Sent " + stat.getTotalDataSent());
            System.out.println(
		"Packets Resent " + stat.getRetransmissionsSent());
            System.out.println("Data Resent " + stat.getTotalDataReSent());
            System.out.println("Packets Received " + stat.getPacketsRcvd());
            System.out.println("Data Received " + stat.getTotalDataReceive());
            System.out.println("Retransmissed Packets Received " + 
		stat.getRetransmissionsRcvd());
            System.out.println("Retransmissed bytes Received " + 
		stat.getRetransBytesRcvd());
            System.out.println("Duplicate Packets received " + 
		stat.getDuplicatePackets());
            System.out.println("Duplicate Bytes received " + 
		stat.getDuplicateBytes());

            if (role == 's') {
                long dataRate = (stat.getTotalDataSent() * 1000) / elapsedTime;

                System.out.println("Average data rate = " + dataRate);

                if (receiverCount != 0) {
                    System.out.println("Effective rate for group = " + 
			((stat.getTotalDataSent() * receiverCount * 1000) 
                        / elapsedTime));
                }
            } else {
                long dataRate = 
		    (stat.getTotalDataReceive() * 1000) / elapsedTime;

                System.out.println("Received " + stat.getTotalDataReceive() + 
		    " bytes in " + elapsedTime + " milliseconds");
                System.out.println("Average data rate = " + dataRate + 
		    " bytes / second");
            }
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    static void invokeAdminClient() throws IOException, Exception {
        MulticastSocket admin = new MulticastSocket(controlPort);

        admin.setTTL((byte)sessionTTL);
        admin.joinGroup(channel);

        // Send start "signal"

        DatagramPacket p = new DatagramPacket(new byte[10], 10, channel, 
                                              controlPort);

        admin.send(p);
        admin.send(p);
        admin.send(p);
        admin.send(p);
        System.out.println("\n\nStart signal sent to " + channelAddr + ":" 
                           + controlPort + ".\n\n");
    }

}
