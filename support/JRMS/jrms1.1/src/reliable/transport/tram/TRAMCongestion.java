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
 * TRAMCongestion.java
 * 
 * Module Description:
 * 
 * This module handles all congestion processing for TRAM. It
 * detects congestion in members and listens for congestion
 * messages from members.
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.Date;
import java.util.Vector;
import java.util.NoSuchElementException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import com.sun.multicast.util.UnsupportedException;

/**
 * This class handles the congestion messages for TRAM. When a congestion
 * message is received, the current data rate is to be cut in half at
 * the sender. Heads simply propegate the messages up to the sender and
 * let it do the work. The class implementes the TRAMCongestionPacketListener
 * interface and is handed packets from the unicast input dispatcher.
 */
class TRAMCongestion implements TRAMCongestionPacketListener {
    TRAMControlBlock tramblk;
    TRAMTransportProfile tp;
    TRAMRateAdjuster rateAdjuster;
    TRAMLogger logger;
    int lastWindow = 0;
    int consecutiveCongestionCount = 0;

    /**
     * Create the TRAMCongestion class. Unlike other thread classes in TRAM,
     * this constructor does not automatically start the thread. All members
     * require the TRAMCongestion class but only heads and the sender need the
     * thread and listener objects.
     * 
     * @param tramblk the TRAMControlBlock for this session.
     */
    public TRAMCongestion(TRAMControlBlock tramblk) {

        /*
         * Initialize the controlling variables in the class.
         */
        this.tramblk = tramblk;
        tp = tramblk.getTransportProfile();
        rateAdjuster = tramblk.getRateAdjuster();
        logger = tramblk.getLogger();

        /*
         * Add this class to the TRAMCongestionPacketListener list.
         */
        tramblk.getUcastInputDispThread().addTRAMCongestionPacketListener(this);
    }

    /**
     * Send a congestion message to the head.
     */
    public void sendCongestion(int ackSequence) {
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        if (gblk.getHeadBlock() == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CONG | TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Head is null. Can't send congestion string");
	    }
	    return;
	}

        InetAddress address = gblk.getHeadBlock().getAddress();
        int port = gblk.getHeadBlock().getPort();

        if (address == null) {
	    
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CONG | TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Head address is null. Can't send congestion message");
	    }

            return;
        }

	TRAMCongestionPacket pk = new 
	    TRAMCongestionPacket(tramblk, address, port, ackSequence);

	pk.setDataRate((int)rateAdjuster.getPreferredDataRate());
	pk.setFlowControlInfo(rateAdjuster.getGroupFlowControlInfo());
	pk.setFlags((byte)(pk.getFlags() &
	    ~TRAMCongestionPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO));

        DatagramPacket dp = pk.createDatagramPacket();
        DatagramSocket so = tramblk.getUnicastSocket();

        try {
	    try {
	        tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	    } catch (NullPointerException e) {}

            so.send(dp);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Sent a congestion Packet for ACK Sequence " + 
		    ackSequence + " group flow " + pk.getFlowControlInfo() +
		    " my flow " + rateAdjuster.getMyFlowControlInfo());
	    }
        } catch (IOException e) {
            e.printStackTrace();

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Unable to send a congestion Packet" + 
		    "for ACK Sequence " + ackSequence);
	    }
        }
    }

    /**
     * Send a congestion message to the head.
     */
    private void sendCongestion(TRAMCongestionPacket pk) {
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        if (gblk.getHeadBlock() == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Head is null. Can't send congestion string");
	    }
	    return;
	}

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CNTLMESG)) {

            logger.putPacketln(this, 
	        "Forwarding a congestion Packet");
	}

        InetAddress address = gblk.getHeadBlock().getAddress();
        int port = gblk.getHeadBlock().getPort();

        if (address == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
                TRAMLogger.LOG_CONG | TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Head address is null. Can't send congestion string");
	    }

            return;
        }

        pk.setAddress(address);
        pk.setPort(port);
	pk.setFlags((byte)(pk.getFlags() | 
	    TRAMCongestionPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO));

        DatagramPacket dp = pk.createDatagramPacket();
        DatagramSocket so = tramblk.getUnicastSocket();

        try {
	    try {
		tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	    } catch (NullPointerException e) {}

            so.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This is the TRAMCongestionPacketListener interface. All congestion
     * messages are received through this method and placed on the
     * packetList vector. The listener thread is resumed.
     */
    public void receiveCongestionPacket(TRAMCongestionPacketEvent e) {
        TRAMCongestionPacket pk = e.getPacket();

	MemberBlock mb = null;

	try {
	    mb = tramblk.getGroupMgmtBlk().getMember(pk.getAddress(), 
		pk.getPort());
	} catch (NoSuchElementException nsee) {
	    /*
	     * This can happen if we've disowned a member which is a head
	     * but it hasn't realized it's been disowned and forwards a
	     * congestion message.
	     */
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CONG)) {

		logger.putPacketln(this, 
		    "Can't find member block for " + pk.getAddress() +
		    ", " + pk.getPort());
	    }
	    return;
	}

	if (pk.getFlowControlInfo() > mb.getFlowControlInfo()) {
	    mb.setFlowControlInfo(pk.getFlowControlInfo());

	    if ((pk.getFlags() & 
		TRAMCongestionPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) != 0) {

		mb.setSubTreeFlowControlInfo(true);
	    } else {
		mb.setSubTreeFlowControlInfo(false);
	    }
	}

        int window = Math.abs(pk.getSequenceNumber()) / tp.getAckWindow();

        if (window <= lastWindow) {
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, 
	    	    "Duplicate congestion message for window " + window + 
		    ", from " + pk.getAddress() +
		    ", groupFlowControlInfo " + 
		    rateAdjuster.getGroupFlowControlInfo());
	    }

	    return;
        }

        lastWindow = window;

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
            logger.putPacketln(this, 
	        "congestion!  window " + window + 
	        ", " + pk.getAddress() + 
	        ", groupFlowControlInfo " + 
		rateAdjuster.getGroupFlowControlInfo() +
		", packet flow " + pk.getFlowControlInfo() +
	        ", data rate " + rateAdjuster.getPreferredDataRate());
	}
	    
	/*
	 * If we're the sender or a head and the sender is sending at
	 * the minimum data rate, then we need to prune if we're using
	 * decentralizedPruning.
	 */
	if (rateAdjuster.getPreferredDataRate() <= tp.getMinDataRate()) {
	    consecutiveCongestionCount++;	// consecutive congestion msgs

            /*
             * Let's not prematurely prune members.
             * Only prune if the average rate is also low.
             */
	    if (tp.decentralizedPruning() == true) {
		if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                    logger.putPacketln(this, 
		        "Member " + pk.getAddress() + 
			" sent congestion message.  " +
		        "Sender at min rate.  count " + 
			consecutiveCongestionCount +
		        " Average Rate " + rateAdjuster.getAverageDataRate() +
		        " threshold " + (tp.getMinDataRate() +
                        ((tp.getMaxDataRate() - tp.getMinDataRate()) / 4)));
		}
		
	        if (consecutiveCongestionCount >= 
		    tp.getMaxConsecutiveCongestionCount() &&
                    rateAdjuster.getAverageDataRate() < 
		    tp.getMinDataRate() + 
		    ((tp.getMaxDataRate() - tp.getMinDataRate()) / 4)) {

		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                        logger.putPacketln(this, 
		            "Member " + pk.getAddress() + " PRUNED because\n" + 
		            "\t\tit sent " + consecutiveCongestionCount +
			    " consecutive congestion message while sender is " +
			    "sending at the minimum data rate!");
		    }

		    if ((pk.getFlags() &
                        TRAMCongestionPacket.
			    FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) == 0) {

		        if (mb != null) {
			    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                   	        logger.putPacketln(this, 
                                    "pruneMember!!! " + mb.getAddress() + 
			            " Decentralized pruning was used");
			    }

		            tramblk.getGroupMgmtThread().handleMemberLoss(mb);
			}
		    }
	        } 
	    } else {
	        if (consecutiveCongestionCount >= 
		    tp.getMaxConsecutiveCongestionCount() &&
                    rateAdjuster.getAverageDataRate() < 
		    tp.getMinDataRate() + 
		    ((tp.getMaxDataRate() - tp.getMinDataRate()) / 4) &&
		    ((pk.getFlags() &
                    TRAMCongestionPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) == 
		        0)) {

		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                        logger.putPacketln(this, 
	                    "Member " + pk.getAddress() + 
		            " would be pruned if decentralizedPruning " +
			    " were enabled");
		    }
	        }
	    }
	} else {
	    consecutiveCongestionCount = 0;
	}

        if ((tp.getTmode() & 0xff) == TMODE.SEND_ONLY) {
	    /*
	     * Adjust rate down if we can
	     */
	    if (rateAdjuster.getPreferredDataRate() < (long)pk.getDataRate()) {
	        if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                    logger.putPacketln(this, 
			"Rate in congestion message is " + pk.getDataRate() +
			" is already lower then current data rate of " +
			rateAdjuster.getPreferredDataRate());
		}
	    } else {
                rateAdjuster.adjustRateDown(pk.getAddress());
	    }

            return;
	}

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
            logger.putPacketln(this, 
	        "Forwarding congestion msg from " + pk.getAddress());
	}

        sendCongestion(pk);
    }

}
