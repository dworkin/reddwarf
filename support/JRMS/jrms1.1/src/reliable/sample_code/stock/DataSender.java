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
import java.lang.Exception;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.tram.*;

public class DataSender extends Thread {

    private InetAddress channel;
    private int maxBuf = 1500;
    private int headerLen = 152;
    private int sessionTTL = 20;
    private int minDataRate = 1000;
    private int maxDataRate = 200000;
    private int ackWindow = 32;
    private String channelAddr = "224.100.100.101";
    private int senderlogMask = TRAMTransportProfile.LOG_INFO; 
    private int dataPort = 6000;
    private String logFile = "DataSender.log";
    private boolean staticTreeFormation = false;
    private boolean decentralizedPruning = false;
    private PrintStream logStream = null;
    private boolean initDone = false;
    private int sendDataSize = 10000000;
    private int senderDelay = 10;
    private int pass = 1;
    private byte dataValue = 0;
    private boolean quit = false;
    private DataStats dataStats;
    private int numberMembersToWaitFor = 0;
    private int maxConsecutiveCongestionCount = 1;
    private int maxPasses = 0;
    private int cacheSize = 0;
    private double pruningWindow = 0;
    private double rateDecreaseFactor = .875;
    private double rateIncreaseFactor = .15;
    private int timeForAvgRateCalc = 5;
    private int bufSize;
    private byte buf[];

    DataSender(ArgParser argParser) {
        logFile = argParser.getString("XSenderLog", "Xl", logFile);

        try {
            logStream = new PrintStream(new BufferedOutputStream(
                new FileOutputStream(logFile, true)));
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(1);
        }

	this.logStream = logStream;

        dataPort = argParser.getInteger("XDataPort", "Xp", dataPort);

        channelAddr = argParser.getString("XSenderAddress", "Xa", channelAddr);

        try {
            channel = InetAddress.getByName(channelAddr);
        } catch (UnknownHostException e) {
            log(e.toString());
        }

        senderlogMask = argParser.getInteger("XLogMask", "Xm", senderlogMask);
        minDataRate = argParser.getInteger("XMinDataRate", "Xr", minDataRate);
        maxDataRate = argParser.getInteger("XMaxDataRate", "XR", maxDataRate);
        ackWindow = argParser.getInteger("XACKWindow", "Xw", ackWindow);
        sendDataSize = argParser.getInteger("XSendDataSize", "Xs", 
	    sendDataSize);
        senderDelay = argParser.getInteger("XSenderDelay", "Xd", senderDelay);
	staticTreeFormation = argParser.getBoolean("XStaticTreeFormation", 
	    "XST", staticTreeFormation);
	decentralizedPruning = argParser.getBoolean("XdecentralizedPruning", 
	    "XDP", decentralizedPruning);
	numberMembersToWaitFor = argParser.getInteger(
	    "XNumMembersToWaitFor", "XWM", numberMembersToWaitFor);
	maxConsecutiveCongestionCount = argParser.getInteger(
	    "XMaxConsecutiveCongestionCount", "XMCCC", 
	    maxConsecutiveCongestionCount);
	maxPasses = argParser.getInteger("XPasses", "XP", maxPasses);
	cacheSize = argParser.getInteger("XCacheSize", "Xc", cacheSize);
	pruningWindow = argParser.getDouble("XPruningWindow", "XPW", 
	    pruningWindow);

	rateDecreaseFactor = argParser.getDouble(
	    "XSetRateDecreaseFactor", "XRDF", rateDecreaseFactor);
	rateIncreaseFactor = argParser.getDouble(
	    "XSetRateIncreaseFactor", "XRIF", rateIncreaseFactor);
	timeForAvgRateCalc = argParser.getInteger("XSetTimeToAverage", "XTTA", 
	    timeForAvgRateCalc);

        maxBuf = argParser.getInteger("XMaxBuf", "XB", maxBuf);

	bufSize = maxBuf - headerLen;
        buf = new byte[bufSize];
    }

    public void go() {
        setDaemon(true);
        start();
    }
    
    public static void main(String[] args) {
	DataSender dataSender = new DataSender(new ArgParser(args));

	dataSender.run();
	System.exit(0);
    }

    public boolean initDone() {
	return initDone;
    }

    public void run() {    
        try {
	    TRAMPacketSocket ps = setupTRAM();

	    int totalMembers = -1;

	    while (dataStats.getMemberCount(ps) < numberMembersToWaitFor) {
		if (dataStats.getMemberCount(ps) > totalMembers) {
		    System.err.println("Current Member count is " + 
		        dataStats.getMemberCount(ps) + 
			".  Waiting for a total count of " + 
		        numberMembersToWaitFor + " Members");

		    totalMembers = dataStats.getMemberCount(ps);
		}
                Thread.sleep(1000);	// wait for members to join group
	    }

	    long lastTime = 0;

            while (!quit) {
                Thread.sleep(senderDelay * 1000);

		if (sendData(ps) == false)
		    continue;	// sleep then try again

		if (maxPasses != 0 && pass >= maxPasses) {
                    Thread.sleep(senderDelay * 1000);
		    quit = true;
		}

		pass++;
            }
        } catch (Exception e) {
            log(e.toString());
        }
    }

    private void log(String line) {
        logStream.println(line);
	logStream.flush();
    }

    private TRAMPacketSocket setupTRAM() throws IOException, Exception {
        Date startDate = new Date();
        TRAMTransportProfile tp;
        TRAMPacketSocket ps;

        tp = new TRAMTransportProfile(channel, dataPort);

        tp.setTTL((byte) sessionTTL);
        tp.setOrdered(true);
        tp.setMrole(MROLE.MEMBER_RELUCTANT_HEAD);
        tp.setLogMask(senderlogMask);
        tp.setMinDataRate(minDataRate);
        tp.setMaxDataRate(maxDataRate);
        tp.setAckWindow((short)ackWindow);
        tp.setMaxBuf(maxBuf);
	tp.setLateJoinPreference(
            TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY);

	if (staticTreeFormation == true) {
            tp.setTreeFormationPreference(
		TRAMTransportProfile.TREE_FORM_HAMTHA_STATIC_R);
	}

	tp.setDecentralizedPruning(decentralizedPruning);
	tp.setMaxConsecutiveCongestionCount(maxConsecutiveCongestionCount);

	if (cacheSize != 0)
	    tp.setCacheSize(cacheSize);

	if (pruningWindow != 0)
	    tp.setPruningWindow(pruningWindow);

	tp.setreaffiliateAfterBeingDisowned(false);


        tp.setRateDecreaseFactor(rateDecreaseFactor);
        tp.setRateIncreaseFactor(rateIncreaseFactor);
        tp.setTimeForAvgRateCalc(timeForAvgRateCalc);

        log("\nSession started on: " + startDate.toString());
        log("SenderAddress " + channelAddr);
        log("Data Port " + dataPort);
        log("Min Data Rate " + tp.getMinDataRate());
        log("Max Data Rate " + tp.getMaxDataRate());
        log("Ack Window " + tp.getAckWindow());
        log("Cache Size " + tp.getCacheSize());
	log("SendDataSize = " + sendDataSize);
	log("SenderDelay = " + senderDelay);
	log("DecentralizedPruning = " + decentralizedPruning);
	if (decentralizedPruning)
	    log("Pruning Window is " + tp.getPruningWindow());
	log("Max Consecutive Congestion Reports at Min Data Rate " +
	    "before pruning = " +
	    tp.getMaxConsecutiveCongestionCount());

	log("Rate Decrease Factor = " + tp.getRateDecreaseFactor());
	log("Rate Increase Factor = " + tp.getRateIncreaseFactor());
	log("Time for Average Rate Calculation = " + 
	    tp.getTimeForAvgRateCalc() + " seconds");
	log("Max Buffer Size = " + tp.getMaxBuf());

        /*
         * The TRAMLogger's constructor captures System.out.
         * By changing it here and starting a new TRAM session
         * the logger will log to the newly set standard output file.  
         *
         * This is how we keep separate logs for each TRAM session.
         */
        // PrintStream systemOut = System.out;
        // System.setOut(logStream);
        ps = (TRAMPacketSocket) tp.createRMPacketSocket(TMODE.SEND_ONLY);
        // System.setOut(systemOut);

	dataStats = new DataStats(logStream, true);

	initDone = true;
	return ps;
    }

    private boolean sendData(TRAMPacketSocket ps) {
	boolean firstTime = true;

        long startTime = System.currentTimeMillis();

        try {
	    int bytesSent = 0;

	    while (bytesSent < sendDataSize) {
                int sendSize = Math.min(sendDataSize - bytesSent, bufSize);
		int value = dataValue;

		//
		// Put the number of bytes sent this pass so that late
		// joiners know how much more data to expect for this pass.
		//
		buf[0] = (byte)(bytesSent & 0xff);
		buf[1] = (byte)((bytesSent >> 8) & 0xff);
		buf[2] = (byte)((bytesSent >> 16) & 0xff);
		buf[3] = (byte)((bytesSent >> 24) & 0xff);

		for (int i = 4; i < sendSize; i++)
		    buf[i] = (byte)((value++) % 256);
		
                DatagramPacket dp = new DatagramPacket(buf, sendSize, 
                    channel, dataPort);

                ps.send(dp);

		/*
		 * The send above can fail if there are no members,
		 * so don't increment counters unless we get to here.
		 */
		if (firstTime) {
		    firstTime = false;		    
        	    log("\nSending Data...\n");
		}

		bytesSent += sendSize;
		dataValue += (sendSize - 4);
	    }
        } catch (NoMembersException nme) {
	    return false;	// can't send data now
        } catch (Exception e) {
	    log("Exception!");
            e.printStackTrace();
            System.exit(1);
	}

        printStats(ps, startTime);
	return true;
    }

    private void printStats(TRAMPacketSocket ps, long startTime) {
        dataStats.printStats(ps, startTime);
    }

}
