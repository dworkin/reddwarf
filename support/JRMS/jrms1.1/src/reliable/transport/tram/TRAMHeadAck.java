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
 * TRAMHeadAck.java
 * 
 * Module Description:
 * 
 * This module handles all of the ACK processing for Head nodes in the
 * TRAM transport. It is an TRAMAckPacketListener and an TRAMDataPacketListener.
 * It fields incoming ACK messages from mebers and retransmits packets
 * when needed. The data packets are received in order to keep track
 * of which packets need to be acked.
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.Vector;
import java.util.Date;
import java.util.NoSuchElementException;
import java.io.IOException;
import java.net.InetAddress;

/**
 * This class implements the ACK mechanisms for HEAD nodes in the
 * TRAM transport. The head ACK class listens for Data packets and
 * maintains the retransmission queue. It also listens for ack
 * reports from its members. When an ack comes in requesting
 * retransmission of a packet, this class retrieves the packet
 * and sends it off the the member via Multicast. When all members
 * have acked a packet, it is removed from the retransmission queue.
 * If a retransmission request is received but the packet doesn't
 * exist in the retransmission queue, the request is ignored. The code
 * assumes that the TRAMMemberAck class is dealing with missing data
 * packets and when it eventually arrives, the next member request
 * for retransmission will be serviced.
 */
class TRAMHeadAck implements TRAMAckPacketListener, BeaconPacketListener {
    TRAMControlBlock tramblk;
    TRAMStats statsBlock;
    TRAMTransportProfile tp;
    TRAMLogger logger;
    Vector controlPacketList;
    GroupMgmtBlk gblk;
    boolean dataEnd = false;
    TRAMDataCache dataCache;
    int lastWindow = 0;

    /**
     * Create an TRAMHeadAck object. The control variables are initialized,
     * the thread is initialized, its name is set, and it is set to be a
     * daemon thread.
     */
    public TRAMHeadAck(TRAMControlBlock tramblk) {

        /*
         * Initialize the controlling variables in the class.
         */
        this.tramblk = tramblk;
        statsBlock = tramblk.getTRAMStats();
        tp = tramblk.getTransportProfile();
        logger = tramblk.getLogger();
        gblk = tramblk.getGroupMgmtBlk();
        dataCache = tramblk.getTRAMDataCache();

        /*
         * Add this class to the TRAMAckPacketListener list and
         * the TRAMDataPacketListener list.
         */
        tramblk.getUcastInputDispThread().addTRAMAckPacketListener(this);
        tramblk.getInputDispThread().addBeaconPacketListener(this);
    }

    /**
     * This is the main method for processing ack packets. The
     * heart of the ack packet is the bitmask indicating which
     * packets need retransmission. If an ack packet has no bitmask,
     * then the base packet number indicates the last packet receive
     * in order successfully.
     * 
     * @param pk the TRAMAckPacket.
     */
    public void receiveAckPacket(TRAMAckPacketEvent e) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE))
            logger.putPacketln(this, "Got Ack...");

        TRAMAckPacket pk = e.getPacket();
        MemberBlock mb = null;

        /*
         * Get the membership information. If this request is not from a
         * member, ignore the accounting mechanics.
         */
        InetAddress ia = pk.getAddress();
        int port = pk.getPort();

        try {
            mb = gblk.getMember(ia, port);
        } catch (NoSuchElementException ne) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "No Member for " + ia + " port " + port);
	    }
	    return;
        }

	int flags = pk.getFlags();

        /*
         * If the member is terminating. Attempt to clear out the data
         * cache. Ignore processing the ack.
	 * REMOVING THE MEMBER FROM THE MEMBERLIST IS DONE BY THE
	 * GROUP MANAGEMENT THREAD.
         */
        if ((flags & TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP) != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
	            "Got a terminate membership ack packet");
	    }

            return;
        }

	/*
	 * Remember the flow control info from this ACK
	 * so we can decide who to prune if we ever need to.
	 */
	mb.setFlowControlInfo(pk.getFlowControlInfo());

	/*
	 * We need to keep track of whether the flow control information
	 * is from the sender of the ACK from from a node below the sender
	 * so that we won't inadvertently prune the sender.
	 */
	mb.setSubTreeFlowControlInfo((flags & 
	    TRAMAckPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) != 0);

	if ((flags & TRAMAckPacket.FLAGBIT_CONGESTION) != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                logger.putPacketln(this,
	
		"Congestion flag set in ACK from " + ia +
		" port " + port);
	    }
	
	    //	    dealWithCongestion(mb, pk);
	    // see if we need to adjust the rate

	    int window = (int)pk.getBasePacketNumber() / tp.getAckWindow();

	    if (window > lastWindow) {
	        lastWindow = window;

	        TRAMRateAdjuster rateAdjuster = tramblk.getRateAdjuster();
	        TRAMMemberAck memack = tramblk.getMemberAck();

	        if (memack == null) {
	    	    /*
		     * We're the sender.  Deal with congestion.
		     */
		    rateAdjuster.congestion(pk.getAddress());
	        } else {
		    memack.sendAck((byte)TRAMAckPacket.FLAGBIT_CONGESTION, 
			           gblk.getHeadBlock(), 11);
	        }
	    }
	}

	/*
	 * If the ACK flag isn't set, we're done.
	 */
	if ((flags & TRAMAckPacket.FLAGBIT_ACK) == 0)
	    return;
	
        /*
         * Check each valid bit in the mask. For each bit set,
         * retransmit the packet. For each bit clear, indicate that
         * this receiver has seen the packet.
         */
        byte bitMask[] = pk.getBitMask();
        int bitMaskLength = pk.getBitMaskLength();

        if (bitMask.length < ((bitMaskLength + 7) / 8)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,  
                    "Bitmask too short. Specified length " + 
		    (bitMaskLength + 7) / 8 + " Actual length " + 
		    bitMask.length);
	    }

            return;
        }

        int base = (int)pk.getBasePacketNumber();

	int missingPackets = 0;

        for (int i = 0; i < bitMaskLength; i++) {
            int index = i / 8;
            int offset = i % 8;

            if (((byte) (1 << offset) & bitMask[index]) != 0) {
		retransmitPacket(base + (8 * index) + offset, mb);
		missingPackets++;		// current packets missing
            }
        }

	/*
	 * set the last acked only if the reported value is greater than
	 * the already set ones. The bug that is being fixed now
	 * takes care of the an overwrite in the following scenerio 
	 * This node reports all packet after a certain seq num say 40
	 * are guaranteed but the member's ACK may report that it rquires
	 * packet 14 as the starting or base packet number. This is not
	 * error as the the member sends the same ack to a head and an
	 * re-affiliated head. Hence in a case where this head is a 
	 * re-affiliated head, we should not overwrite the value set at
	 * the time of sending an AM message.
	 */
        if (bitMaskLength != 0)
	    base--;

	if (base > mb.getLastPacketAcked())
	    mb.setLastPacketAcked(base);

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                "Found member " + mb.getMemberId() + 
		" Set last packet acked to " + base);
	}

        /*
         * If the cache is full, try to reduce its size now that
         * we've received and processed an ack from a member.
         */
        if (tramblk.isCacheFull()) {
            if (dataCache == null) {
                dataCache = tramblk.getTRAMDataCache();
            } 

            dataCache.purgeCache(-1);
        }

	/*
	 * Set member's highest sequence allowed and update
	 * the highest allowed for the group.
	 */
	int oldHighestAllowed = gblk.getHighestSequenceAllowed();

	mb.setHighestSequenceAllowed(pk.getHighestSequenceAllowed());

	int newHighestAllowed = gblk.getHighestSequenceAllowed();
	tramblk.setHighestSequenceAllowed(newHighestAllowed);

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                "Found member " + mb.getMemberId() + 
	        " Set last packet acked to " + mb.getLastPacketAcked());
	}

	/*
	 * If the new highest allowed is greater than what it was, we need
	 * to send our ACK now so the sender can get this information.
	 */
	if (newHighestAllowed - oldHighestAllowed > tp.getAckWindow() / 2) {
	    TRAMMemberAck memack = tramblk.getMemberAck();

            if (memack != null) {
	        if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                    logger.putPacketln(this, 
		
		    "Sending ACK now.  oldHighest " + oldHighestAllowed +
		    " newHighest " + newHighestAllowed);
	        }

                memack.sendAck((byte)0, gblk.getHeadBlock(), 10);
	    }
	}

	int start;
	int end;

	if (bitMaskLength == 0) {
	    start = 1;
	    end = base;
	} else {
	    start = base + 1;
	    end = base + bitMaskLength;
	}

	int logLevel = TRAMLogger.LOG_VERBOSE | TRAMLogger.LOG_CNTLMESG |
	    TRAMLogger.LOG_CONG;

	String type = "ACK";

	if (missingPackets > 0) {
	    logLevel |= TRAMLogger.LOG_CONG;
	    type = "NACK";
	}

	if (logger.requiresLogging(logLevel)) {
            logger.putPacketln(this, 
                type + " from " + mb.getAddress() + " [" + start + "-" + 
	        end + "], missing " + missingPackets +
	        ", highest wanted " + pk.getHighestSequenceAllowed() +
	        ", allowed " + tramblk.getHighestSequenceAllowed() +
	        ", next " + tramblk.getLastKnownSequenceNumber() +
	        ", flow " + mb.getFlowControlInfo() +
	        (mb.getSubTreeFlowControlInfo() ? ", subtree" : "") +
	        ", flags " + flags);
	} 

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE))
            logger.putPacketln(this, "Done with ACK Packet...");
    }

    /**
     * Get the packet with the specified sequence number from the cache and
     * place it in the outputDatabase. If the packet isn't in the cache,
     * assume that the TRAMMemberAck thread is handling the request for
     * retransmission from our head. Wait for another request for
     * retransmission to send it out.
     * 
     * @param packetNumber the packet sequence number of the packet to
     * retransmit.
     */
    private void retransmitPacket(int packetNumber, MemberBlock mb) {
        TRAMDataPacket pk = null;

        /*
         * There is a possibility that the cache was not started when we
         * obtained it back in the constructor. If the cache is null, attempt
         * to reload it and if that fails, return. We'll have to try again
         * later.
         */
        if (dataCache == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATACACHE)) {

                logger.putPacketln(this, 
		    "Cache not loaded. Try loading it now");
	    }

            if ((dataCache = tramblk.getTRAMDataCache()) == null) {
	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

                    logger.putPacketln(this, 
		        "Cache is still null, skip retrans");
		}

                return;
            }
        }

        try {
            pk = dataCache.getPacket(packetNumber);
        } catch (NoSuchElementException ne) {

            /*
             * If there is a valid control packet but no data packet attached,
             * then the head has not yet received the data packet. Log the
             * event and ignore this request. Assume that the member will
             * request it again soon.
             */
            TRAMSeqNumber seqNum = new TRAMSeqNumber(packetNumber);

            try {
                if (seqNum.isLessThan(
		      tramblk.getTRAMDataCache().getLowestSequenceNumber())) {

                    tramblk.getHelloThread().reportUnavailablePacket(
			packetNumber);

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			TRAMLogger.LOG_DATACACHE)) {

                        logger.putPacketln(this, 
                            "Reporting packet unavailable " + packetNumber);
		    }
                } else {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_DATACACHE)) {

                        logger.putPacketln(this, 
                            "Packet " + packetNumber + 
			    " has not been recevied yet. Try again later");
		    }

                    return;
                }
            } catch (NullPointerException npe) {}

            return;
        }

        /*
         * To avoid sending the same packet multiple times, check to see if
         * the packet is already in the output dispatchers queue. If it is,
         * ignore this request. If it isn't, check to make sure it hasn't
         * been retransmitted recently. If it has, ignore this request.
         */
        if (pk.isTransmitPending()) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Packet " + packetNumber + " for " + mb.getAddress() +
		    " is already in retran queue");
	    }

            return;
        }

	/*
	 * Retransmission suppression is based on the data rate and
	 * the ACK window.  If an ACK window of packets have been sent
	 * beyond what we want to retransmit, then we should retransmit
	 * this packet, because the receiver has probably missed it.
	 *
	 * We'd like to wait no longer than the time it takes
	 * to send an ACK window worth of packets, but we'd need the
	 * size of those packets to determine a wait time.
	 * We can assume a maximum packet size (xb in the transport profile) but
	 * we also have to take into account that the actual send data
	 * rate may be less than the desired send rate due to cpu limitations.
	 *
	 * XXX we divide by two because ACK's are staggered.  Let's use
	 * half an ACK window as an average for when an ACK should arrive.
	 */
	long expectedTimeout = 100;

	if (pk.getDataRate() != 0) {
	    expectedTimeout = (long)
		((tp.getAckWindow() * tp.getMaxBuf() * 1000 / 2) / 
		pk.getDataRate());
	}

	/*
	 * XXX resend if it's been more than 100ms.  What's the right value?
	 * It depends on the data rate but 100ms seems like a long time...
	 */
	if (expectedTimeout > 100)
	    expectedTimeout = 100;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
		"expected time is " + expectedTimeout +
		" rate is " + pk.getDataRate());
	}

	if (System.currentTimeMillis() - pk.getLastTransmitTime() < 
	    expectedTimeout) {

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CONG)) {

                logger.putPacketln(this, 
                    "Packet " + packetNumber + " for " + mb.getAddress() +
		    " retransmitted within " + 
		    expectedTimeout + " milliseconds.  Data rate " + 
		    pk.getDataRate());
	    }
	
	    return;
	}

        /*
         * All set. Retransmit the packet. Set the multicast address and port,
         * set the data type, indicate the this packet is pending transmit,
         * save some stats, and send the packet.
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Retransmitting packet " + packetNumber +
	        " for " + mb.getAddress() +
	        " next seq " + tramblk.getLastKnownSequenceNumber());
	}

        pk.setAddress(tp.getAddress());
        pk.setPort(tp.getPort());
        pk.setSubType(SUBMESGTYPE.DATA_RETXM);
        pk.setTransmitPending(true);
	/*
	 * Time is set in the packet by the output dispatcher when the
	 * packet is actually sent.
	 */
        // pk.setLastTransmitTime(System.currentTimeMillis());
	// pk.setDataRate((int)tramblk.getRateAdjuster().
	//    getOpenWindowDataRate());

        try {
            tramblk.getPacketDb().putPacket((TRAMPacket) pk, true);
        } catch (IOException e) {
            e.printStackTrace();
            pk.setTransmitPending(false);
        }

	return;
    }

    /**
     * Test method to check if the HeadAck module has completed its
     * task. Typically used by other modules such as the Socket layer
     * to determine if the close operation or some synchronization
     * can be performed.
     * 
     * @param boolean - true if the task is complete. False otherwise.
     * 
     */
    public boolean isHeadTaskDone() {

        /*
         * Head ack can complete if all the control packets have been
         * acknowledged OR if there are no more members to acknowledge
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                " Members = " + gblk.getDirectMemberCount() + 
	        " dataEnd = " + dataEnd);
	}

        if ((gblk.getDirectMemberCount() == 0) && (dataEnd == true)) {
            return true;
        } 

        return (false);
    }

    /**
     * Waits for the HeadAck module to complete its task.
     */
    public void waitToComplete() {
        while (true) {
            if (isHeadTaskDone() == false) {
	        if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, 
		        "Waiting for Head to complete");
		}

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
            } else {
                break;
            }
        }

        return;
    }

    /**
     * This method is the interface for BeaconPacketListener. The multicast
     * input dispatcher calls this method when an beacon packet is received.
     * This method then places the packet on the pkts_to_process Queue/vector
     * and resumes the thread.
     */
    public synchronized void receiveBeaconPacket(BeaconPacketEvent e) {
        BeaconPacket pk = e.getPacket();
        byte b = (byte) pk.getFlags();

        if ((b & BeaconPacket.FLAGBIT_TXDONE) != 0) {
            dataEnd = true;
        }
    }

    private void dealWithCongestion(MemberBlock mb, TRAMAckPacket pk) {
	TRAMRateAdjuster rateAdjuster = tramblk.getRateAdjuster();

	int window = (int)pk.getBasePacketNumber() / tp.getAckWindow();

	if (window <= lastWindow)
	    return;

        lastWindow = window;

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
            logger.putPacketln(this, 
	        "congestion!  window " + window + 
	        ", " + pk.getAddress() + 
	        ", groupFlowControlInfo " + 
		rateAdjuster.getGroupFlowControlInfo() +
		", packet flow " + pk.getFlowControlInfo() +
	        ", data rate " + rateAdjuster.getOpenWindowDataRate());
	}
	    
	/*
	 * If the sender is sending at the minimum data rate, 
	 * then we need to prune if we're using decentralizedPruning.
	 */
	if (rateAdjuster.getOpenWindowDataRate() <= tp.getMinDataRate()) {
            /*
             * Let's not prematurely prune members.
             * Only prune if the average rate is also low.
             */
	    if (tp.decentralizedPruning() == true) {
		if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                    logger.putPacketln(this, 
		        "Member " + pk.getAddress() + 
			" sent congestion message.  " +
		        "Sender at min rate." + 
		        " Average Rate " + rateAdjuster.getAverageDataRate() +
		        " threshold " + (tp.getMinDataRate() +
                        ((tp.getMaxDataRate() - tp.getMinDataRate()) / 4)));
		}
		
		if (rateAdjuster.getAverageDataRate() < 
			tp.getMinDataRate()) {

		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                        logger.putPacketln(this, 
		            "Member " + pk.getAddress() + " PRUNED because\n" + 
		            "\t\tit sent " +
			    " a congestion message while sender is " +
			    "sending at the minimum data rate!");
		    }

		    if ((pk.getFlags() &
			TRAMAckPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) == 
			0) {
			
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
		if ((rateAdjuster.getAverageDataRate() < 
		    tp.getMinDataRate()) &&
		    ((pk.getFlags() &
                    TRAMAckPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO) == 0)) {

		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                        logger.putPacketln(this, 
	                    "Member " + pk.getAddress() + 
		            " would be pruned if decentralizedPruning " +
			    " were enabled");
		    }
	        }
	    }
	}

	TRAMMemberAck memack = tramblk.getMemberAck();

        if (memack == null) {
            /*
             * We're the sender.  Deal with congestion.
             */
            rateAdjuster.congestion(pk.getAddress());
	} else {
            memack.sendAck((byte)TRAMAckPacket.FLAGBIT_CONGESTION, 
		gblk.getHeadBlock(), 11);
	}
    }
}
