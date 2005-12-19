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
 * MCTestQA.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.net.*;
import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.tram.*;
import com.sun.multicast.reliable.applications.stock.*;

/** 
 * MCTestQA.java is used to create a PacketSocket server and receiver.
 * The invokeReceiver method also creates the GraphData object and sends it
 * via rmi to the Graph Engine. This allows the Graph engine to know how
 * many bytes where received and at what time. 
 *  
 * MCTestQA.java itself is called from the QA test machine via rmi through 
 * the rmiregistry that gets loaded on each host machine listed in 
 * hostnames.txt. This list is used by restart.pl to rsh receiver.sh 
 * on each machine. receiver.sh loads the registry and all methods contained
 * in CallGDImpl.class 
 *
 * MCTestQA is basically MCTest.java with a few command line, rmi
 * enhancements, and byte compare testing. 
 */

class MCTestQA {
    private InetAddress channel;
    private InetAddress control;
    private int maxBuf = 1500;
    private int headerLen = 152;
    private int sessionTTL = 20;
    private String sendFileName = "/tmp/mctestSend.txt";
    private String receiveFileName = "/tmp/mctestReceive.txt";
    private int minDataRate = 1000;
    private int maxDataRate = 400000;
    private int receiverMaxDataRate = 0;
    private short ackWindow = 32;
    private String channelAddr;
    private int sLogMask = TRAMTransportProfile.LOG_INFO;
    private int rLogMask = TRAMTransportProfile.LOG_INFO;
    private int dataPort = 6000;
    private int controlPort;
    private int serverUnicastPort;
    private int clientUnicastPort;
    private int receiverCount;
    private int repairWaitTime = 10000;
    private int sendDataSize = 1000000;
    private boolean useTcp = false;
    private String senderHost;
    private boolean synchronize = false;
    private byte dataValue = 0;
    private File logFile;
    private String slogFile = "";
    private boolean staticTreeFormation = false;
    private PrintStream logStream = null;
    private PrintStream byteStream = null;
    private ByteArrayOutputStream out = null;
    private boolean initDone = false;
    private int senderDelay = 10;
    private DataStats dataStats;
    private boolean decentralizedPruning = false;
    private boolean quit = false;
    private int pass = 1;
    private int maxConsecutiveCongestionCount = 1;
    private PropManager props = PropManager.getPropManager();
    private Properties JRMSProps;
    private String PropFilename = "";
    private String logString = "";
    private String url;
    private InetAddress inetaddress;
    private String host = "";
    private CallProduct cp1;
    private String serverhost = "";
    private GraphData gd;
    private boolean graph = false;

    private void usage() {
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
	  " 	   -g <graph>\n" +
	  "        -o <receiveFile>\n" +
	  "        -h <senderTCPHost>\n" +
	  "        -w <ackWindow>\n" +
	  "        -W <maxCongestionWindow>\n\n" +
	  "    common_options:\n" +
	  "        -a <multicastAddress>\n" +
	  "        -m <logMask>\n" +
	  "        -p <port>\n" +
	  "        -v (verbose logging)\n" +
	  "        -w <ackWindow>\n");
 
        System.exit(-1);
    }
 
    MCTestQA() {
	try {
	    System.setSecurityManager(new RMISecurityManager());
	    channel = InetAddress.getByName(channelAddr);
	} catch (java.net.UnknownHostException e) {
	    System.out.println(e.toString());
	    e.printStackTrace(System.out);
	    System.exit(1);
	} catch (Exception e) {
	    System.out.println("Error " + e);
	    e.printStackTrace(System.out);
	}
    }

    public static void main(String args[]) {
	MCTestQA mctestqa = new MCTestQA();	
	mctestqa.run(args);
    }

    private void run(String[] args) {
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

		case 'f':
		    i++;
		    
		    if (i >= args.length) {
			usage();
		    }
		    logFile = new File(args[i]);
		    slogFile = args[i];
		    break;

		case 'F':
		    i++;
		    if (i >= args.length) {
			usage();
		    }
		    PropFilename = args[i];
		    break;

		case 'g':
		    if (i >= args.length) {
			usage();
		    }
		    System.out.println("graph is being set to true");
		    graph = true;
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

                    sLogMask = Integer.parseInt(args[i], 16);
		    rLogMask = Integer.parseInt(args[i], 16); 
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
		    i++;
		    if (i >= args.length) {
			usage();
		    }
		    if (args[i].equals("receive")) {
			rLogMask = TRAMTransportProfile.LOG_VERBOSE;
		    } else if (args[i].equals("send")) {
			sLogMask = TRAMTransportProfile.LOG_VERBOSE;
		    } else if (args[i].equals("both")) {
			rLogMask = TRAMTransportProfile.LOG_VERBOSE;
			sLogMask = TRAMTransportProfile.LOG_VERBOSE;
		    }
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

		if (useTcp) {
		    System.out.println("MCTestQA does not " +
			"currently support Tcp");
		} else {
                    invokeServer();
		}

                break;

            case 'c': 

		if (useTcp) {
		    System.out.println("MCtestQA does not " +
			"currently support Tcp");
                } else {
                    invokeReceiver();
		}

                break;

            default: 
                usage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void invokeServer() throws IOException, Exception {
	inetaddress = InetAddress.getLocalHost();
  	host = inetaddress.getHostName();
	url = "rmi://" + host + "/"; 
	props = PropManager.getPropManager();
	JRMSProps = props.getProps();
	serverhost = JRMSProps.getProperty("server");
	Date startDate = new Date();
	sendDataSize = Integer.parseInt(
	    JRMSProps.getProperty("intsent", "100000"));
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;
        MulticastSocket admin;
	
        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
        tp.setMrole(MROLE.MEMBER_RELUCTANT_HEAD);
	tp.setLogMask(sLogMask);
        tp.setMinDataRate(minDataRate);
        tp.setMaxDataRate(maxDataRate);
        tp.setAckWindow((short)ackWindow);
        tp.setMaxBuf(maxBuf);

	if (staticTreeFormation == true) {
	    tp.setTreeFormationPreference(
		TRAMTransportProfile.TREE_FORM_HAMTHA_STATIC_R);
	}
	    
	System.out.println("\nSession started on: " + startDate.toString());
        System.out.println("Address " + channelAddr);
        System.out.println("Data Port " + dataPort);
        System.out.println("Min Data Rate " + tp.getMinDataRate());
        System.out.println("Max Data Rate " + tp.getMaxDataRate());
        System.out.println("Ack Window " + tp.getAckWindow());
	System.out.println("SendDataSize = " + sendDataSize);
	System.out.println("SenderDelay = " + senderDelay);

        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.SEND_ONLY);
	dataStats = new DataStats(System.out, false);
	
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
	long startTime = System.currentTimeMillis();

        try {
	    int bufSize = maxBuf - headerLen;
	
	    byte[] buf = new byte[bufSize];
	    int bytesSent = 0;

	    if (sendDataSize != 0) { 
	    
	        while (bytesSent < sendDataSize) {
		    int sendSize = Math.min(sendDataSize - bytesSent, bufSize);
		
		    for (int i = 0; i < sendSize; i++) {
		        buf[i] = (byte)((dataValue++) % 256);
		    }
		
		    DatagramPacket dp = new DatagramPacket(buf, sendSize,
		        channel, dataPort);
	
		    while (true) {
		        try {
			    ps.send(dp);
			    break;
		        } catch (NoMembersException e) {
			    System.out.println(e); 
			    System.out.println(
				"The NoMembersException is used " +
			        "for letting the application know that no " +
			        "members have been detected yet.");
			    try {
			        Thread.sleep(1000);
			    }catch (InterruptedException ie) {}
			    continue;
		        }
		    }
		    bytesSent += sendSize;
		    System.out.println("bytesSent = " + bytesSent);
		    System.out.println("sendDataSize = " + sendDataSize);
	        }
	    } else {
		// if sendDataSize == 0 just send forever.	
	        while (true) {
		
		    for (int i = 0; i < bufSize; i++) {
		        buf[i] = (byte)((dataValue++) % 256);
		    }
		
		    DatagramPacket dp = new DatagramPacket(buf, bufSize,
		        channel, dataPort);
	
		    while (true) {
		        try {
			    ps.send(dp);
			    break;
		        } catch (NoMembersException e) {
			    System.out.println(e); 
			    System.out.println(
				"The NoMembersException is used " +
			        "for letting the application know that no " +
			        "members have been detected yet.");
			    try {
			        Thread.sleep(1000);
			    }catch (InterruptedException ie) {}
			    continue;
		        }
		    }
	        }
	    }
		
	} catch (Exception e) {
	    System.out.println(e);
	    e.printStackTrace();
	    System.exit(1);
	}

	printStats(ps, startTime);

        int bytesRead = 0;

        long elapsedTime = System.currentTimeMillis() - startTime;
	
	System.out.println("Starting the 10 second sleep.");

	try {
		Thread.sleep(repairWaitTime);	// wait for repairs to complete!
	} catch (InterruptedException e) {
	    System.out.println(e);
	}
	
	System.out.println("Starting the TRAMStat stuff.");

        TRAMStats stat = (TRAMStats) ps.getRMStatistics();
	System.out.println("Closing ps.  Test succeeded.");
        ps.close();
    }

    private void invokeReceiver() throws IOException, Exception {
	int counter = 0;
	boolean backup = true;
	inetaddress = InetAddress.getLocalHost();
  	host = inetaddress.getHostName();
	JRMSProps = props.getProps();
	System.out.println("Getting host");
	serverhost = JRMSProps.getProperty("server");
	System.out.println("Host is now: " + serverhost);
	url = "rmi://" + serverhost + "/"; 
	try {
	    logStream = new PrintStream(
		new BufferedOutputStream(
		   new FileOutputStream(logFile)));
	    out = new ByteArrayOutputStream();
	    byteStream = new PrintStream(out);
	} catch (FileNotFoundException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	}

	this.logStream = logStream;
	Date startDate = new Date();
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;

        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
	tp.setLogMask(rLogMask);
        tp.setAckWindow(ackWindow);
        tp.setMaxBuf(maxBuf);
        tp.setLateJoinPreference(
	    TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY);
	
	if (staticTreeFormation == true) {
	    tp.setTreeFormationPreference(
		TRAMTransportProfile.TREE_FORM_HAMTHA_STATIC_R);
	} else {
	}
	
	log("\nSession Started at: " + startDate.toString());
        log("Address " + channelAddr);
        log("Data Port " + dataPort);
        log("Ack Window " + tp.getAckWindow());
        log("Congestion Window " + tp.getCongestionWindow());
	log("SendDataSize " + sendDataSize);
	log("SenderDelay = " + senderDelay);

	PrintStream systemOut = System.out;
	System.setOut(byteStream);
        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.RECEIVE_ONLY);

	dataStats = new DataStats(logStream, false);
	initDone = true;

        log("\nReady to receive data.\n");

        DatagramPacket dp;

        long startTime = 0;

	int totalBytesReceived = 0;
	int timeToPrintStats = 0;
	boolean firstTime = true;
	byte dataValue = 0;

	GDManager gdm = GDManager.getGDManager();
	if (graph) {
	    cp1 = (CallProduct)Naming.lookup(url +
		"CallMCTest");
	}

	while (quit == false) {
	    try {
		dp = ps.receive();
		if (quit) {
		    log("breaking because quit is true.");
		    break;
		}
		    
		if (startTime == 0) { 
		    startTime = System.currentTimeMillis();
		}

		byte data[] = dp.getData();
		logString = out.toString();
		out.reset();
		logStream.print(logString);
		if (graph) {
		    gd = gdm.addData(logString, host);
		    if (gd != null) {
			cp1.drawGraph(gd);
		    }
		}
		
		//
		// To accomodate late joiners, we have to accept the first
		// byte of the first packet as being correct.  From there we
		// can determine the correctness of all the rest of the data.

		if (firstTime) {
		    dataValue = data[0];
		    firstTime = false;
		}
		
		int length = dp.getLength();
		
		for (int i = 0; i < length; i++) {
		    if (data[i] != (byte)(dataValue % 256)) {
			log("Test Failed. " + 
			    "Bytes miscompare at " +
			    (totalBytesReceived + i) + ". Expected " +
			    (dataValue % 256) + " Got " + data[i]);
			System.exit(3);
		    } 
		    dataValue++;
		}
		
		totalBytesReceived += length;
		timeToPrintStats += length;

		if (timeToPrintStats >= sendDataSize) {
		    timeToPrintStats = 0;
		    printStats(ps, startTime);
		    startTime = 0;
		    pass++;
		}

	    } catch (java.net.UnknownHostException e) {
		System.out.println("Error " + e); 
		e.printStackTrace(System.out);
	    } catch (SessionDoneException se) {
		printStats(ps, startTime);
		ps.close();
		Date enddate = new Date();
		log("Session done at " + enddate.toString());
		log("Test succeeded.");
		logStream.close();
		out.close();
		System.exit(0);
	    } catch (SessionDownException sd) {
		ps.close();
		log("Session Down. The Sender stopped sending!\n");
		logStream.close();
		System.exit(2);
	    } catch (MemberPrunedException mp) {
		log("Member pruned from the tree\n");
		ps.close();
		logStream.close();
		System.exit(4);
	    } catch (Exception e) {
		log(e.toString());
		e.printStackTrace(logStream);
		logStream.close();
		break;
	    }
	    log("Test passed.");    
	    counter++;
	    if (counter % 1320 == 0) {
		counter = 0;
		logStream.close();
		if (backup) {
	    	    File logFile = new File(slogFile + "bak");
		    try {
			logStream = new PrintStream(
			    new BufferedOutputStream(
				new FileOutputStream(logFile)));
		    } catch (FileNotFoundException e) {
			System.out.println(e);
			e.printStackTrace(System.out);
		    }
		    backup = false;
	        } else {
		    File logFile = new File(slogFile);
		    try {
			logStream = new PrintStream(
			    new BufferedOutputStream(
				new FileOutputStream(logFile)));
		    } catch (FileNotFoundException e) {
			System.out.println(e);
			e.printStackTrace(System.out);
		    }
		    backup = true;
		}
	    }
	}
    }

    private void printStats(char role, TRAMStats stat, long elapsedTime) {
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
	
	    System.out.println("Getting dataRate");
	    long dataRate = stat.getTotalDataSent();

            if (role == 's') {
                dataRate = (dataRate * 1000) / elapsedTime;

                System.out.println("Average data rate = " + dataRate);

                if (receiverCount != 0) {
                    System.out.println("Effective rate for group = " + 
		        ((dataRate * receiverCount * 1000) 
                            / elapsedTime));
		}
            } else {
	        dataRate = (dataRate * 1000) / elapsedTime;

                System.out.println("Received " + stat.getTotalDataReceive() + 
		    " bytes in " + elapsedTime + " milliseconds");
                System.out.println("Average data rate = " + dataRate + 
		    " bytes / second");
            }
        } catch (Exception e) {
            System.out.println(e);
	    e.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private void printStats(TRAMPacketSocket ps, long startTime) {
	dataStats.printStats(ps, startTime);
    }

    private void invokeAdminClient() throws IOException, Exception {
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
    private void log(String line) {
	logStream.println(line);
	logStream.flush();
    }
}
