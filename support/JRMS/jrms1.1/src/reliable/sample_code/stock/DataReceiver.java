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

package com.sun.multicast.reliable.applications.stock;

import java.net.*;
import java.io.*;
import java.util.*;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.tram.*;

public class DataReceiver extends Thread {

    private int maxBuf = 1500;
    private int sessionTTL = 20;
    private int ackWindow = 32;
    private String channelAddr = "224.100.100.101";
    private InetAddress channel;
    private int dataPort = 6000;
    private int receiverLogMask = TRAMTransportProfile.LOG_INFO;
    private int sendDataSize = 10000000;
    private String logFile = "DataReceiver.log";
    private boolean staticTreeFormation = false;
    private PrintStream logStream = null;
    private boolean initDone = false;
    private int pass = 1;
    private boolean quit = false;
    private int senderDelay = 10;
    private DataStats dataStats;
    private boolean decentralizedPruning = false;
    private int receiverMaxDataRate = 0;
    private int maxDataRate = 200000;
    private int maxPasses = 0;
    private String receiverKillIndicator = "/tmp/KillDataReceiver";
    private int cacheSize = 0;
    private double pruningWindow = 0;

    DataReceiver(ArgParser argParser) {
        logFile = argParser.getString("ReceiverLog", "Rl", logFile);

        try {
            logStream = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(logFile, true)));
        } catch (Exception e) {
            System.out.println(e.toString());
            exit(1);
        }

	this.logStream = logStream;

        dataPort = argParser.getInteger("XDataPort", "Xp", dataPort);
        receiverLogMask = argParser.getInteger("RLogMask", "Rm", 
	    receiverLogMask);
	cacheSize = argParser.getInteger("RCacheSize", "Rc", cacheSize);
        channelAddr = argParser.getString("DataSenderAddress", "Xa", 
	    channelAddr);
        sendDataSize = argParser.getInteger("XSendDataSize", "Xs", 
	    sendDataSize);
        senderDelay = argParser.getInteger("XSenderDelay", "Xd", senderDelay);
        staticTreeFormation = argParser.getBoolean("XStaticTreeFormation", 
	    "XST", staticTreeFormation);
        decentralizedPruning = argParser.getBoolean("XdecentralizedPruning", 
	    "XDP", decentralizedPruning);
        maxDataRate = argParser.getInteger("XMaxDataRate", "XR", maxDataRate);
	ackWindow = argParser.getInteger("XACKWindow", "Xw", ackWindow);
        receiverMaxDataRate = argParser.getInteger("XMaxReceiveDataRate",
	    "RMR", receiverMaxDataRate);
	maxPasses = argParser.getInteger("XPasses", "XP", maxPasses);
        pruningWindow = 
	    argParser.getDouble("XPruningWindow", "XPW", pruningWindow);

	maxBuf = argParser.getInteger("XMaxBuf", "XB", maxBuf);

        try {
            channel = InetAddress.getByName(channelAddr);
        } catch (UnknownHostException e) {
            log(e.toString());
	    exit(1);
        }
    }

    public void go() {
	setDaemon(true);
        start();
    }

    public static void main(String[] args) {
	String s = null;

	try {
	    s = InetAddress.getLocalHost().getHostAddress();
	} catch (Exception e) {
	    System.err.println("Exception getting my host address!");
	    System.exit(0);
	}

	String net = s.substring(0, 7);

	/*
	 * For now, only start data receivers on the east coast.
         * XXX
	 */
	if (!net.equals("129.148"))
	    System.exit(0);

	DataReceiver dataReceiver = new DataReceiver(new ArgParser(args));

	dataReceiver.start();

	long lastTime = 0;

	while (dataReceiver.quit == false) {
	    if (System.currentTimeMillis() - lastTime >= 10000) {
		lastTime = System.currentTimeMillis();

                try {
		    Thread.sleep(10000);
                    FileInputStream in = 
                        new FileInputStream(dataReceiver.receiverKillIndicator);

		    in.close();
		    dataReceiver.quit = true;
		    dataReceiver.interrupt();
                } catch (Exception e) {
		}
	    }
	}
    }

    public boolean initDone() {
	return initDone;
    }

    public void run() {    
        try {
            invokeReceiver();
        } catch (Exception e) {
            log(e.toString());
        } 
    }

    private void log(String line) {
        logStream.println(line);
	logStream.flush();
    }

    public void quit() {
	quit = true;
    }

    private void exit(int exitStatus) {
	System.exit(exitStatus);
    }
	
    private void invokeReceiver() throws IOException, Exception {
        Date startDate = new Date();
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;

        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
        tp.setLogMask(receiverLogMask);
        tp.setAckWindow((short)ackWindow);
        tp.setMaxBuf(maxBuf);
        tp.setMaxDataRate(maxDataRate);
        tp.setLateJoinPreference(
	    TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY);

	if (staticTreeFormation == true) {
	    tp.setTreeFormationPreference(
		TRAMTransportProfile.TREE_FORM_HAMTHA_STATIC_R);
	}

	tp.setDecentralizedPruning(decentralizedPruning);
	tp.setReceiverMaxDataRate(receiverMaxDataRate);

	if (cacheSize != 0)
	    tp.setCacheSize(cacheSize);
	
        if (pruningWindow != 0)
	    tp.setPruningWindow(pruningWindow);

	tp.setreaffiliateAfterBeingDisowned(false);

        log("\nSession Started at: " + startDate.toString());
        log("Address " + channelAddr);
        log("Data Port " + dataPort);
        log("Ack Window " + tp.getAckWindow());
	log("Cache Size " + tp.getCacheSize());
	log("SendDataSize " + sendDataSize);
        log("SenderDelay = " + senderDelay);
        log("DecentralizedPruning = " + decentralizedPruning);
        if (decentralizedPruning)
            log("Pruning Window is " + tp.getPruningWindow());

	if (tp.getReceiverMaxDataRate() != 0)
	    log("Max Receive Data Rate = " + tp.getReceiverMaxDataRate());

        log("Max Buffer Size = " + tp.getMaxBuf());

	/*
	 * The TRAMLogger's constructor captures System.out.
	 * By changing it here and starting a new TRAM session
	 * (done by FileSender), the logger will log to the newly
	 * set standard output file.  After we return from
	 * creating the socket we restore System.out.
	 *
	 * This is how we keep separate logs for each TRAM session.
         */
	// PrintStream systemOut = System.out;
	// System.setOut(logStream);
        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.RECEIVE_ONLY);
	// System.setOut(systemOut);

	dataStats = new DataStats(logStream, false);

	initDone = true;

        log("\nReady to receive data.\n");

        DatagramPacket dp;

        long startTime = 0;

	int totalBytesReceived = 0;
	int timeToPrintStats = 0;
	boolean firstTime = true;
	byte dataValue = 0;
	boolean printLateJoinMessage = true;

        while (quit == false) {
            try {
                dp = ps.receive();

		if (quit)
		    break;

        	byte data[] = dp.getData();

		//
		// To accomodate late joiners, we use the first 4 bytes of
		// the data packet to determine how much data has been sent
		// already during this pass so we know how much more to expect.
		// 
		// Also, we have to accept the 5th byte of data of the
		// first packet we receive as being correct.  From there we
		// can determine the correctness of all the rest of the data.
		// 
		if (firstTime) {
		    totalBytesReceived = 
			(data[0] & 0xff) +
		        ((data[1] << 8) & 0xff00) +
		        ((data[2] << 16) & 0xff0000) +
		        ((data[3] << 24) & 0xff000000);

		    //
		    // Wait for next pass to start
		    //
		    if (totalBytesReceived != 0) {
			if (printLateJoinMessage) {
			    printLateJoinMessage = false;
			    log("Late join.  Waiting for next pass to begin.");
			}
			continue;
		    }

		    resetStats(ps);

		    dataValue = data[4];
		    firstTime = false;
		}

                if (startTime == 0)
                    startTime = System.currentTimeMillis();
                
                int length = dp.getLength();
                
                for (int i = 4; i < length; i++) {
                    if (data[i] != (byte)(dataValue % 256)) {
                        log("Test Failed.  Bytes miscompare at " + 
			    (totalBytesReceived + i) + ".  Expected " +
			    (dataValue % 256) + " Got " + data[i]);
			log("totalBytesReceived " + totalBytesReceived +
			    " length " + length);    

			exit(3);
                    }
		    dataValue++;
                }

		totalBytesReceived += length;
		timeToPrintStats += length;

		if (timeToPrintStats >= sendDataSize) {
		    timeToPrintStats = 0;

                    printStats(ps, startTime);

		    startTime = 0;

		    if (maxPasses != 0 && pass >= maxPasses) 
			quit = true;

		    pass++;
		}
            } catch (SessionDoneException se) {
                printStats(ps, startTime);
                ps.abort();
                log(new Date() + " Session done.");
		exit(0);
            } catch (SessionDownException sd) {
                ps.abort();
                log(new Date() + "Session Down, the sender stopped sending!");
                exit(2);
            } catch (MemberPrunedException mp) {
                log(new Date() + " Member pruned from the tree\n");
                ps.abort();
                exit(4);
            } catch (Exception e) {
		e.printStackTrace(System.out);
		exit(5);
	    }	
        }
        log("Exiting...");
	ps.abort();
        exit(2);
    }

    private void printStats(TRAMPacketSocket ps, long startTime) {
        dataStats.printStats(ps, startTime);
    }

    private void resetStats(TRAMPacketSocket ps) {
	dataStats.resetStats(ps);
    }

}
