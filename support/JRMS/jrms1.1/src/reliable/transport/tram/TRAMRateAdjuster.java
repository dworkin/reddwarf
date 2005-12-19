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
 * TRAMRateAdjuster.java
 */
package com.sun.multicast.reliable.transport.tram;

import  java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.sun.multicast.util.UnsupportedException;




class TRAMRateAdjuster {
    private TRAMControlBlock tramblk;
    private TRAMTransportProfile tp;
    private TRAMStats tramStats;
    private TRAMLogger logger;
    private long rateIncrement;
    private boolean doOnce = true;
    private boolean slowStart = true;
    private int outSequenceNumber;	// next packet to send
    private boolean timeToPrune = false;
    private long curDataRate;		// current data rate
    private int myFlowControlInfo;	// member's flow control info
    private PerfMon perfMon = null;
    private long averageDataRate;
    private boolean windowClosed = false;
    private boolean windowClosedSinceLastLog = false; // for logging
    private int lastAverageWindowSize;
    private int averageWindowSize;

    private static final int MIN_RATE_INCREMENT = 2500;
    private static final int CONGESTION_WINDOW_INCREMENT = 2;

    public TRAMRateAdjuster(TRAMControlBlock tramblk) {
        this.tramblk = tramblk;
        tp = tramblk.getTransportProfile();
        logger = tramblk.getLogger();
        tramStats = tramblk.getTRAMStats();

        /*
         * Set the initial data rate and the initial rate increment.
         */
        setDataRate(tp.getMinDataRate() + (2 * MIN_RATE_INCREMENT));

	rateIncrement = MIN_RATE_INCREMENT;

        if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	    logger.putPacketln(this, 
		"JRMS Version 10, 12/5/00");

	    logger.putPacketln(this, 
	        "Min Rate = " + tp.getMinDataRate());

    	    logger.putPacketln(this, 
	        "Max Rate = " + tp.getMaxDataRate());

            if (tp.getTmode() == TMODE.SEND_ONLY) {
	        logger.putPacketln(this, 
	            "Initial Rate = " + curDataRate);
            }
	}

	/*
	 * Start graphical performance monitor
	 */
        if (tp.getTmode() == TMODE.SEND_ONLY) {
	    if ((tp.getLogMask() & 
	        TRAMTransportProfile.LOG_PERFORMANCE_MONITOR) != 0) {
	    	    startPerfMon();
	    }
	}
    }

    public void startPerfMon() {
	if (perfMon != null)
	    return;	// already running

	String s = "Sender "; 

	try {
	    s += InetAddress.getLocalHost();
	} catch (UnknownHostException e) {
	}

	String s1 = tp.getAddress().toString();

	int i;

        if ((i = s1.indexOf("/")) > 0)
	    s1 = s1.substring(0, i);

	s += "  mc address " + s1;  // show multicast address as well

        perfMon = new PerfMon(tramblk, s);
    }

    public void stopPerfMon() {
	if (perfMon != null) {
	    perfMon.stop();
	    perfMon = null;
	}
    }

    /*
     * Set the current data rate in bytes per second.
     * Make sure that the new rate is within specified limits.
     * curDataRate is the sender's rate if this rateAdjuster is
     * for the sender otherwise, it's the retransmission rate for
     * a head.
     * 
     * The data rate is allowed to drop below the minium specified
     * so as to not allow users to thwart congestion congestion.
     * So the minium data rate in the transport profile is not a 
     * minimum but rather the point at which pruning will be considered.
     */
    public void setDataRate(long dataRate) {
        if (dataRate > tp.getMaxDataRate())
            curDataRate = tp.getMaxDataRate();
        else if (dataRate <= 0)
            curDataRate = 1;	// set minimum data rate to 1 byte per sec.
        else
            curDataRate = dataRate;
    }

    /*
     * This routine is called by the output dispatcher for every data and 
     * retransmitted packet sent.
     *
     * If this is a data packet with a sequence number less than 
     * the highest allowed (i.e., the window is open), 
     * then return the current data rate.
     * Otherwise, set the rate to linearly decrease as the window starts 
     * to close.
     *
     * The intent is to reduce the rate to prevent the window from actually 
     * closing.  This gives some time for ACK's to come in and for the window
     * to open.
     */
    public long getActualDataRate(int outSequenceNumber) {
        int window = tramblk.getHighestSequenceAllowed() - outSequenceNumber;

	if (window >= tp.getAckWindow())
	    return (curDataRate);

	if (window < 1)
	    return 1;	// lowest possible rate

	return tp.getMinDataRate() + 
	    (long)((double)((double)window / (double)tp.getAckWindow()) *
	    (curDataRate - tp.getMinDataRate()));
    }

    public long getActualDataRate() {
	long rate = getActualDataRate(outSequenceNumber);
	//if (rate < 1000) {
	//    System.err.println(
	//	"low rate! " + rate + " seq " + 
	//	outSequenceNumber + " allowed " +
	//	tramblk.getHighestSequenceAllowed());
	//}
	return getActualDataRate(outSequenceNumber);
    }

    /*
     * This routine gets the data rate that the sender would prefer 
     * to use if the "window" is open.
     */
    public long getOpenWindowDataRate() {
	return curDataRate;
    }

    /*
     * Get the window size.
     */
    public int getWindow() {
	int window = tramblk.getHighestSequenceAllowed() - outSequenceNumber;

	if (window < 0)
	    window = 0;

	return window;
    }

    /*
     * The output dispatcher calls this method to adjust the rate based
     * on network congestion and the sequence number of the next packet 
     * being sent.  After each congestion window of packets, 
     * we determine how things are going and how to adjust the rate.
     */
    public void adjustRate(TRAMDataPacket pk) {
	outSequenceNumber = pk.getSequenceNumber();  // keep for logging info

	/*
	 * Calculate the "average" window size.  It's actually the cumulative
	 * window size for this ACK window of packets.  We use this to determine
	 * whether or not the window has increased since the last ACK window.
	 */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
		"averageWindowSize = " + averageWindowSize + 
		" last avg " + lastAverageWindowSize + 
	        " window " + getWindow());
	}

        averageWindowSize += getWindow();

	if ((outSequenceNumber % tp.getAckWindow()) == 0)
	    calculateAverageDataRate();

	/*
	 * During the startup phase we increase the data rate 
	 * for each packet sent.  Once congestion is reported, we  
	 * are no longer in the startup phase and we increase only 
	 * after each ACK window of packets if no congestion 
	 * has been reported.
	 *
	 * For each ACK window of packets, if the window has not closed
	 * increase the data rate a little bit.  If it has closed, when
	 * it opens, calculate the average data rate and set the current 
	 * data rate to that.
	 */
	if (slowStart) {
	    if (outSequenceNumber > tramblk.getHighestSequenceAllowed()) {
	        slowStart = false;

	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE))
	            logger.putPacketln(this, "slowStart being set to false");
	    }

	    if (curDataRate == tp.getMaxDataRate())
		slowStart = false;
	}

	if (slowStart) {
	    setDataRate(curDataRate + rateIncrement);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "Setting rate to " + curDataRate +
		    " increment " + rateIncrement);
	    }
		
	    if (rateIncrement < 
		(tp.getMaxDataRate() - tp.getMinDataRate()) / 4) {

		rateIncrement += 1000;
	    } 

	    if (perfMon != null) 
	        perfMon.newDataRate(curDataRate);

	    logRateInfo(null);
	} else {
	    if (outSequenceNumber <= tramblk.getHighestSequenceAllowed()) {
		/*
		 * The window is open
		 */
		if (windowClosed) {
		    /*
		     * The window had previously closed but is now open.
		     */
		    windowClosed = false;

		    long oldRate = curDataRate;

		    /*
		     * reset the data rate.
		     */
		    setDataRate((long)getAverageDataRate());

		    rateIncrement = Math.max(MIN_RATE_INCREMENT, 
			(long)(curDataRate * tp.getRateIncreaseFactor()));

	            if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	                logger.putPacketln(this, 
			    "Window reopened (" + (getWindow() + 1) +
			    ").  Old rate " + oldRate + 
			    " new rate " + curDataRate + 
			    " rate inc " + rateIncrement);
		    }

		    logRateInfo(null);
		} else {
		    if ((outSequenceNumber % tp.getAckWindow()) == 0) {
			/*
			 * Only increase the rate if the average window size
			 * is increasing.  Otherwise, set it to the average.
			 */
			if (averageWindowSize > lastAverageWindowSize ||
			    (averageWindowSize == lastAverageWindowSize &&
			    averageWindowSize >= 
			    (tp.getAckWindow() * tp.getMaxCongestionWindowMultiple()) - 1)) {

		            setDataRate(getAverageDataRate() + rateIncrement);
			} else {
			    setDataRate(getAverageDataRate());
			}

		        logRateInfo(null);
		    }
		    rateIncrement = Math.max(MIN_RATE_INCREMENT, 
			(long)(curDataRate * tp.getRateIncreaseFactor()));
		}
		if (perfMon != null)
	            perfMon.newDataRate(curDataRate);
	    } else {
		if (windowClosed == false) {
	            if (logger.requiresLogging(TRAMLogger.LOG_CONG))
	                logger.putPacketln(this, "Window closed.");

		    windowClosed = true;
		    windowClosedSinceLastLog = true;
		}
	    }
	}
	if ((outSequenceNumber % tp.getAckWindow()) == 0) {
	    lastAverageWindowSize = averageWindowSize;
	    averageWindowSize = 0;
	}
    }

    /*
     * There is congestion.  Decide whether or not to prune members.
     */
    public void congestion(InetAddress from) {
	slowStart = false;

	logRateInfo(from);
	
	// The line below in effect halves the senders rate whenever it receives
	// a congestion message. This behaviour is not what was on the original 
	// code but is put in to make pruning more explicit so that 
	// I can test FB
	// setDataRate((long)(getAvgDatarat * .5));  // lower data rate

	/*
	 * If we were already sending at the minimum rate and we're getting
	 * congestion reports, it's time to see if we want to prune someone.
	 */
	// The code below has been modified so that the sender prunes a 
	// receiver as soon as it goes below the minDataRate. In the earlier 
	// there was a grace "rate" that allowed a grace rate equal 
	// (maxRate - minRate) / 4 .


	if (outSequenceNumber >= 5 * tp.getAckWindow() && 
	    getAverageDataRate() <= tp.getMinDataRate()) {

	    timeToPrune = true;
	} else
	    timeToPrune = false;

	if (timeToPrune == true) {
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, 
	            "Time to prune!  Current data rate " + curDataRate + 
		    " averageRate " + averageDataRate);

	        logger.putPacketln(this, "Ack Window is " + tp.getAckWindow() +
	            " outSequenceNumber is " + outSequenceNumber);
	    }
        }
    }

    TRAMVector rateInfo = new TRAMVector();

    class RateInfo {
        long startTime;
        long bytesTransferred;

        RateInfo(long startTime, long bytesTransferred) {
            this.startTime = startTime;
            this.bytesTransferred = bytesTransferred;
        }
    };

    /*
     * Limit the size of the rate information vector
     */
    private static final int MAX_RATE_INFO_VECTOR = 1000;

    private long totalBytesTransferred = 0;

    public long getAverageDataRate() {
	return averageDataRate;
    }

    public void calculateAverageDataRate() {
	long now = System.currentTimeMillis();
	
	try {
            if (tp.getTmode() == TMODE.SEND_ONLY) {
                totalBytesTransferred = tramStats.getTotalDataSent();
	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	            logger.putPacketln(this, 
		        "totalBytesSent " + totalBytesTransferred);
		}
            } else {
                totalBytesTransferred = tramStats.getTotalDataReceive();
	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	            logger.putPacketln(this, 
		        "totalBytesReceived " + totalBytesTransferred);
		}
	    }
	} catch (UnsupportedException u) {
	}

	rateInfo.addElement(new RateInfo(now, totalBytesTransferred));

	if (rateInfo.size() > MAX_RATE_INFO_VECTOR)
	    rateInfo.removeElementAt(0);
 
	if (tp.getTimeForAvgRateCalc() != 0) {
	    int indx = 0;

	    try {
	        for (indx = 0; indx < rateInfo.size(); indx++) {
	    	    RateInfo r = (RateInfo)rateInfo.elementAt(indx);
            
	    	    if (now - r.startTime < tp.getTimeForAvgRateCalc() * 1000)
	    		break;
	        }
	    
                if (indx > 0 && rateInfo.size() > 2) {
                    /* 
    	             * remove range removes fromIndex(inclusive) to toIndex 
	             * (exclusive).
                     */
                    rateInfo.rmRange(0, indx);

	            if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	                logger.putPacketln(this, 
			    "removing up to " + indx +
			    " keeping " + rateInfo.size());
		    }
	        }
            } catch (ArrayIndexOutOfBoundsException aie) {
            }
	}

	RateInfo firstInfo = (RateInfo)rateInfo.elementAt(0);

	long elapsedTime = now - firstInfo.startTime;

	if (elapsedTime == 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, "Elapsed time is 0!");
	    }
	    return;
        }

	averageDataRate = 
	    ((totalBytesTransferred - firstInfo.bytesTransferred) * 1000) / 
	    elapsedTime;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this,
	        "avg " + averageDataRate + " total " + totalBytesTransferred +
	        " first " + firstInfo.bytesTransferred + 
	        " now " + now + " start " + firstInfo.startTime +
	        " elapsed " + elapsedTime);
	}

	//
	// Fix rounding errors
	//
        if (tp.getTmode() == TMODE.SEND_ONLY) {
	    if (averageDataRate > tp.getMaxDataRate())
	        averageDataRate = tp.getMaxDataRate();
	}
    }

    public void adjustCongestionWindowUp() {
	tp.setCongestionWindow(
	    tp.getCongestionWindow() + CONGESTION_WINDOW_INCREMENT);

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
	        "increasing congestion window by " + 
		CONGESTION_WINDOW_INCREMENT + 
		", New value is " + tp.getCongestionWindow());
	}
    }

    public void adjustCongestionWindowDown() {
	if (tp.getCongestionWindow() > tp.getAckWindow()) {
	    int oldCongestionWindow = tp.getCongestionWindow();

	    tp.setCongestionWindow((int)(.75 * tp.getCongestionWindow()));

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		logger.putPacketln(this, 
	            "Reducing congestion window, old " + oldCongestionWindow +
		    " new " + tp.getCongestionWindow());
            }
	}
    }

    /*
     * With decentralized pruning, heads prune members which send congestion
     * messages even though the sender is sending at the lowest rate.
     * 
     * With centralized pruning, the sender decides when it's time to prune
     * and sets the PRUNE bit in the data packet.
     *
     * This method is called by the PacketDbIOManager to determine
     * whether or note to set the PRUNE flag in the data packet about
     * to be sent.
     */
    public boolean timeToPrune() {
	if (timeToPrune == false)
	    return false;
	
 	timeToPrune = false;
	return true;
    }

    /*
     * DEBUGGING INFO
     */
    private void logRateInfo(InetAddress congestedHost) {
	String c;

	if (doOnce) {
	    doOnce = false;

	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		logger.putPacketln(this, 
	            "\tNextOut\tWindow\tCongestion    rateIncr\tRate\tInfo");
            }
	}

	if (congestedHost != null) {
	    c = congestedHost.toString();
	    
	    int i = c.indexOf("/");
	    c = " " + c.substring(0, i) + "               ";

	    c = c.substring(0, 15);
	} else {
            c = " NC        ";
	}

	int windowSize = 
	    tramblk.getHighestSequenceAllowed() - outSequenceNumber + 1;

	if (windowSize < 0)
	    windowSize = 0;

	String pad = "";

	if (windowSize < 10)
	    pad = "  ";

	if (windowSize < 100)
	    pad = " ";

	String star = "";

	if (windowClosedSinceLastLog) {
	    windowClosedSinceLastLog = false;
	    star = " * ";
	}

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	    logger.putPacketln(this, 
	        "\t" + outSequenceNumber + "\t" +
                windowSize + "\t" +
    	        c + "\t" +
    	        rateIncrement + "\t" +
	        getActualDataRate(outSequenceNumber) + "\t" + 
	        "avg " + getAverageDataRate() + " flow " + 
	        getGroupFlowControlInfo() + star);
        }
    }

    /*
     * FLOW CONTROL 
     *
     * The following methods are for flow control.
     * The hope is that the infrastructure is in place
     * to pass flow control information from members to heads
     * to the sender and back to heads.
     *
     * Flow control information is an "int" and can be anything you like.
     * For example, the member with the most retransmissions may be considered
     * the "worst" one so the number of retransmissions may be used as the
     * flow control information.  In this case a bigger number means "worse".
     *
     * When getting the group flow control information,  this head's
     * information in the calculation of the worse.
     */
    public int getGroupFlowControlInfo() {
	GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

	int groupFlowControlInfo = myFlowControlInfo;

        for (int i = 0; i < gblk.getDirectMemberCount(); i++) {
            try {
        	MemberBlock mb = gblk.getMember(i);      // next member

		if (isWorse(mb.getFlowControlInfo(), groupFlowControlInfo))
		    groupFlowControlInfo = mb.getFlowControlInfo();
            } catch (IndexOutOfBoundsException e) {
                break;
	    }
        }

	return groupFlowControlInfo;
    }

    public boolean IsSubtreeWorse() {
	GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        if (gblk.getDirectMemberCount() == 0 ||
	    isWorse(myFlowControlInfo, getGroupFlowControlInfo())) {

	    return false;
	}
	
	return true;
    }

    public int getMyFlowControlInfo() {
	return myFlowControlInfo;
    }

    public void setMyFlowControlInfo(int packets, int missing) {
	int newFlowControlInfo;

	if (packets == 0 || missing == 0) 
	    newFlowControlInfo = 0;
	else {
	    /*
	     * Calculate percentage packets lost.
	     * Multiply by 100 to make it an integer.
	     */
	    newFlowControlInfo = (int)((100 * missing) / packets);

	    if (newFlowControlInfo > 100) {
                if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		    logger.putPacketln(this, 
	                "new flow info > 100: " + newFlowControlInfo +
		        " packets " + packets + " missing " + missing);
    	        }
	    }
	}

	int savemyflow = myFlowControlInfo;

	/*
	 * Calculate the moving average of percent packets lost.
	 */
	myFlowControlInfo = (int)((.75 * myFlowControlInfo) +
	    (.25 * newFlowControlInfo));

        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
	        "packets " + packets +
	        " missing " + missing +
	        " oldflow " + savemyflow +
	        " newFlow " + newFlowControlInfo +
	        " .25new + .75old " + myFlowControlInfo + 
	        " group flow " + getGroupFlowControlInfo());
    	}
    }

    private boolean isWorse(int flowControlInfo1, int FlowControlInfo2) {
	return (flowControlInfo1 > FlowControlInfo2);
    }

    private boolean isBadEnough(int flowControlInfo1, int FlowControlInfo2) {
	return (1.05 * flowControlInfo1 >= FlowControlInfo2);
    }

    public synchronized void findMemberToPrune(int worstFlowControlInfo) {
	GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

	if (tp.decentralizedPruning() == true) {
	    int highestSeqNum = tramblk.getLastKnownSequenceNumber();
	    int maxDifferenceAllowed = 
	    (int)(tp.getPruningWindow() * tp.getAckWindow());

	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		logger.putPacketln(this, 
		    "decentralized: looking for member to prune.  " +
		    "highestSeq = " + highestSeqNum +
		    " worstHCR = " + gblk.getLowestPacketAcked() +
		    " diff = " + 
		    (highestSeqNum - gblk.getLowestPacketAcked()));
	    }

	    int lowAllowedHCR = highestSeqNum - maxDifferenceAllowed;

	    if (gblk.getLowestPacketAcked() >= lowAllowedHCR)
		return;		// we don't need to prune anybody

	    for (int i = 0; i < gblk.getDirectMemberCount(); i++) {
		// Find members that have HCR indicating they 
		// are about the worst in the group.
		try {
		    MemberBlock mb = gblk.getMember(i);      // next member

		    if (mb == null)
		        break;

		    if (mb.getSubTreeFlowControlInfo() == true)
		        continue;

		    int memberHCR = mb.getLastPacketAcked();
			
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) 
		        logger.putPacketln(this, mb.getAddress() + 
			" HCR " + memberHCR);

		    if (memberHCR < lowAllowedHCR) {
		        if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
			    logger.putPacketln(this, "pruneMember!!! " + 
			    mb.getAddress());
			}
			    
			tramblk.getGroupMgmtThread().handleMemberLoss(mb);
		    }
		} catch (IndexOutOfBoundsException e) {
		    break;
		}
	    }
	} else {
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		logger.putPacketln(this, 
		    "Searching for member to prune.  " +
		    "worstFlowControlInfo is " + worstFlowControlInfo);
	    }

	    for (int i = 0; i < gblk.getDirectMemberCount(); i++) {
	        try {
		    MemberBlock mb = gblk.getMember(i);      // next member
		    int memberFlowControlInfo = mb.getFlowControlInfo();  
			
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
			logger.putPacketln(this, 
			    mb.getAddress() + " group flow control info " + 
			    getGroupFlowControlInfo() +
			    " member flow control info " + 
			    memberFlowControlInfo + " subtree flag is " + 
			    mb.getSubTreeFlowControlInfo());
		    }
	    
		    /*
		     * Find members that have flow control information 
		     * indicating they are about the worst in the group.
		     */
		    if (mb.getSubTreeFlowControlInfo() == false &&
			isBadEnough(memberFlowControlInfo, 
			worstFlowControlInfo)) { 
			    
			if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
			    logger.putPacketln(this, 
				"pruneMember!!! " + mb.getAddress() + 
				", member control flow info " + 
				memberFlowControlInfo +
				", group flow control info " + 
				getGroupFlowControlInfo());
			}
			    
			tramblk.getGroupMgmtThread().handleMemberLoss(mb);    
			    
			if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
			    logger.putPacketln(this, 
				"group flow control info " + 
				getGroupFlowControlInfo());
			}
		    } 
		} catch (IndexOutOfBoundsException e) {
		    break;
		}
	    }
	}
    }

}
