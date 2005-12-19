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

public class DataStats {

    private PrintStream logStream;
    private boolean isSender;
    private TRAMStats prevStat;
    private int pass = 1;

    public DataStats(PrintStream logStream, boolean isSender) {
	this.logStream = logStream;
	this.isSender = isSender;
    }

    private void log(String line) {
        logStream.println(line);
	logStream.flush();
    }

    public void resetStats(TRAMPacketSocket ps) {
        prevStat = (TRAMStats)ps.getRMStatistics();
    }

    public void printStats(TRAMPacketSocket ps, long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        TRAMStats stat = (TRAMStats)ps.getRMStatistics();

        try {
            InetAddress[] addresses = stat.getSenderList();

	    log("");

            if (addresses == null) {
                log("No Sender List Available");
            } else {
                log("Sender is " + addresses[0]);
            }

            log("Total Group Members " + stat.getReceiverCount());
            log("Direct Member Count " + stat.getDirectMemberCount());
            log("Indirect Member Count " + stat.getIndirectMemberCount());
            log("Peak Members " + stat.getPeakMembers());
            log("Pruned Members " + stat.getPrunedMembers());
            log("Lost Members " + stat.getLostMembers());

	    /*
	     * Since we keep the session open, the following statistics
	     * are computed based on the current values minus the
	     * values the last time we did this computation.
	     */
	    long packetsSent = stat.getPacketsSent();
	    long totalDataSent = stat.getTotalDataSent();
	    long retransmissionsSent = stat.getRetransmissionsSent();
	    long totalDataResent = stat.getTotalDataReSent();
            long packetsRcvd = stat.getPacketsRcvd();
            long retransmissionsRcvd = stat.getRetransmissionsRcvd();
            long retransBytesRcvd = stat.getRetransBytesRcvd();
            long duplicatePackets = stat.getDuplicatePackets();
            long duplicateBytes = stat.getDuplicateBytes();
            long totalDataReceive = stat.getTotalDataReceive();

	    /*
	     * Get control message information
	     */
    	    long mcastControlBytesSent = stat.getMcastControlBytesSent();
	    long mcastControlBytesRcvd = stat.getMcastControlBytesRcvd();
	    long mcastBeaconSent = stat.getMcastBeaconSent();
	    long mcastBeaconRcvd = stat.getMcastBeaconRcvd();
	    long mcastHelloSent = stat.getMcastHelloSent();
	    long mcastHelloRcvd = stat.getMcastHelloRcvd();
	    long mcastHASent = stat.getMcastHASent();
	    long mcastHARcvd = stat.getMcastHARcvd();
    	    long mcastMSSent = stat.getMcastMSSent();
    	    long mcastMSRcvd = stat.getMcastMSRcvd();
    	    long ucastCntlBytesSent = stat.ucastCntlBytesSent();
    	    long ucastCntlBytesRcvd = stat.ucastCntlBytesRcvd();
    	    long ucastAMSent = stat.ucastAMSent();
    	    long ucastAMRcvd = stat.ucastAMRcvd();
    	    long ucastRMSent = stat.ucastRMSent();
    	    long ucastRMRcvd = stat.ucastRMRcvd();
    	    long ucastHelloSent = stat.ucastHelloSent();
    	    long ucastHelloRcvd = stat.ucastHelloRcvd();
    	    long ucastACKSent = stat.ucastACKSent();
    	    long ucastACKRcvd = stat.ucastACKRcvd();
    	    long ucastCongSent = stat.ucastCongSent();
    	    long ucastCongRcvd = stat.ucastCongRcvd();
    	    long ucastHBSent = stat.ucastHBSent();
    	    long ucastHBRcvd = stat.ucastHBRcvd();

	    if (prevStat != null) {
		packetsSent -= prevStat.getPacketsSent();
		totalDataSent -= prevStat.getTotalDataSent();
		retransmissionsSent -= prevStat.getRetransmissionsSent();
		totalDataResent -= prevStat.getTotalDataReSent();
		packetsRcvd -= prevStat.getPacketsRcvd();
                retransmissionsRcvd -= prevStat.getRetransmissionsRcvd();
                retransBytesRcvd -= prevStat.getRetransBytesRcvd();
                duplicatePackets -= prevStat.getDuplicatePackets();
                duplicateBytes -= prevStat.getDuplicateBytes();
                totalDataReceive -= prevStat.getTotalDataReceive();

    	        mcastControlBytesSent -= prevStat.getMcastControlBytesSent();
	        mcastControlBytesRcvd -= prevStat.getMcastControlBytesRcvd();
	        mcastBeaconSent -= prevStat.getMcastBeaconSent();
	        mcastBeaconRcvd -= prevStat.getMcastBeaconRcvd();
	        mcastHelloSent -= prevStat.getMcastHelloSent();
	        mcastHelloRcvd -= prevStat.getMcastHelloRcvd();
	        mcastHASent -= prevStat.getMcastHASent();
	        mcastHARcvd -= prevStat.getMcastHARcvd();
    	        mcastMSSent -= prevStat.getMcastMSSent();
    	        mcastMSRcvd -= prevStat.getMcastMSRcvd();
    	        ucastCntlBytesSent -= prevStat.ucastCntlBytesSent();
    	        ucastCntlBytesRcvd -= prevStat.ucastCntlBytesRcvd();
    	        ucastAMSent -= prevStat.ucastAMSent();
    	        ucastAMRcvd -= prevStat.ucastAMRcvd();
    	        ucastRMSent -= prevStat.ucastRMSent();
    	        ucastRMRcvd -= prevStat.ucastRMRcvd();
    	        ucastHelloSent -= prevStat.ucastHelloSent();
    	        ucastHelloRcvd -= prevStat.ucastHelloRcvd();
    	        ucastACKSent -= prevStat.ucastACKSent();
    	        ucastACKRcvd -= prevStat.ucastACKRcvd();
    	        ucastCongSent -= prevStat.ucastCongSent();
    	        ucastCongRcvd -= prevStat.ucastCongRcvd();
    	        ucastHBSent -= prevStat.ucastHBSent();
    	        ucastHBRcvd -= prevStat.ucastHBRcvd();
	    }

	    prevStat = stat;

	    log("");
            log("Multicast Control Sent");
	    log("    Bytes "  + mcastControlBytesSent);
            log("    Beacons " + mcastBeaconSent);
            log("    Hellos " + mcastHelloSent);
            log("    HA Messages " + mcastHASent);
            log("    MS Messages " + mcastMSSent);

	    log("Multicast Control Received");
            log("    Bytes " + mcastControlBytesRcvd);
            log("    Beacons " + mcastBeaconRcvd);
            log("    Hellos " + mcastHelloRcvd);
            log("    HA Messages " + mcastHARcvd);
            log("    MS Messages " + mcastMSRcvd);

            log("Unicast Control Sent ");
	    log("    Bytes " + ucastCntlBytesSent);
            log("    AM Messages " + ucastAMSent);
            log("    RM Messages " + ucastRMSent);
            log("    Hellos " + ucastHelloSent);
            log("    ACKs " + ucastACKSent);
            log("    Congestion Reports " + ucastCongSent);
            log("    HB Messages " + ucastHBSent);

            log("Unicast Control Received");
            log("    Bytes " + ucastCntlBytesRcvd);
            log("    AM Messages " + ucastAMRcvd);
            log("    RM Messags " + ucastRMRcvd);
            log("    Hellos " + ucastHelloRcvd);
            log("    ACKs " + ucastACKRcvd);
            log("    Congestion Reports " + ucastCongRcvd);
            log("    HB Messagse " + ucastHBRcvd);

	    log("");
            log("Packets Resent " + retransmissionsSent);
            log("Data Resent " + totalDataResent);

	    if (isSender) {
                log("Packets Sent " + packetsSent);
                log("Data Sent " + totalDataSent);
                log(InetAddress.getLocalHost() + " Pass " + pass + 
		    " Average data rate = " + 
		    ((totalDataSent * 1000) / elapsedTime) + 
		    " bytes / second\n");
	    } else {
	        log("Packets Received " + packetsRcvd);
                log("Data Received " + totalDataReceive);
                log("Retransmitted Packets Received " + retransmissionsRcvd);
                log("Retransmitted bytes Received " + retransBytesRcvd);
                log("Duplicate Packets received " + duplicatePackets);
                log("Duplicate Bytes received " + duplicateBytes);
                log("Received " + totalDataReceive +
                   " bytes in " + elapsedTime + " milliseconds");
                log(InetAddress.getLocalHost() + " Pass " + pass + 
		    " Average data rate = " + 
		    ((totalDataReceive * 1000) / elapsedTime) + 
		    " bytes / second\n");
	    }
	    pass++;

        } catch (Exception e) {
            log(e.toString());
            System.exit(1);
        }
    }

    public int getMemberCount(TRAMPacketSocket ps) {
        TRAMStats stat = (TRAMStats)ps.getRMStatistics();

	int receiverCount = 0;

        try {
	    receiverCount = stat.getReceiverCount();
        } catch (Exception e) {
            log(e.toString());
            System.exit(1);
	}

	return receiverCount;
    }

}
