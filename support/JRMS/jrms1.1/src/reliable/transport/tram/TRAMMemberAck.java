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
 * TRAMMemberAck.java
 * 
 * Module Description:
 * 
 * This module handles all of the ACK processing for member nodes in the
 * TRAM transport. It is an TRAMDataPacketListener. It detects missing packets,
 * and sends ACK messages to its head.
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.Vector;
import java.util.Date;
import java.net.*;
import java.io.*;
import com.sun.multicast.util.UnsupportedException;

/**
 */
class TRAMMemberAck implements TRAMDataPacketListener, BeaconPacketListener, 
                              TRAMTimerEventHandler, TRAMMembershipListener {
    TRAMControlBlock tramblk;
    TRAMTransportProfile tp;
    TRAMRateAdjuster rateAdjuster;
    TRAMLogger logger;
    TRAMSeqNumber nextPacket = new TRAMSeqNumber();
    public Vector missingPackets = new Vector();
    TRAMSeqNumber nextAck;
    boolean dataEnd = false;
    boolean finalAckSent = false;
    TRAMSimpleTimer tramTimer = null;
    long lastAckTime = 0;
    int lastAckPacket = 0;
    int previousMissing = 0;
    int highestSequenceAllowed;
    int ackWindowSize;
    int windowCongestionMessageSent = 0;
    boolean dataCacheProcessedMembershipEvent = false;
    boolean needToWaitForCacheToProcessMembershipEvent = false;
    long joinTime = 0;

    /* Late join parameters... */

    boolean init = true;
    int lateJoin;
    Vector holdingTank;

    /* Masks to help construct ack bitmask... */

    byte maskBase[] = {
        1, 3, 7, 15, 31, 63, 127, -1
    };
    byte maskOffset[] = {
        -1, -2, -4, -8, -16, -32, -64, -128
    };

    /**
     * Create an TRAMMemberAck object. The TRAMControlBlock is required
     * to maintain statistics, obtain the transport
     * profile, etc. The constructor initializes all of the packet
     * control parameters required.
     * 
     * @param tramblk the TRAMControlBlock for this session.
     */
    public TRAMMemberAck(TRAMControlBlock tramblk) {

        /*
         * Initialize the controlling variables in the class.
         */
        this.tramblk = tramblk;
        tp = tramblk.getTransportProfile();
	rateAdjuster = tramblk.getRateAdjuster();
        logger = tramblk.getLogger();

        /*
         * Add this class to the TRAMDataPacketListener list.
         */
        tramblk.getInputDispThread().addTRAMDataPacketListener(this);

        /*
         * Add a Beacon Packet listener, the received beacon is useful
         * to this module only at the end of data transmission - especially
         * if the DATA_END message is lost.
         */
        tramblk.getInputDispThread().addBeaconPacketListener(this);

        /*
         * Set the next ack message to go out at a random interval
         * in between the next ack interval. Use the unicast port
         * number as a seed.
         */
	ackWindowSize = tp.getAckWindow();

        nextAck = new TRAMSeqNumber((int)(Math.random() * ackWindowSize));

        this.tramblk.getGroupMgmtThread().addTRAMMembershipListener(this);

        /*
         * Only create the holding tank for receivers that have requested
         * late join with limited recovery. The holding tank is used for
         * temporary storage of packets prior to becoming a member. The
         * sender never creates the holding tank.
         * 
         * There are a couple items that control the operation of this code
         * (a good candidate for a state machine!!). The init flag is set
         * to true when this class is started. It is cleared when the
         * appropriate nextPacket is set. For the limited recovery case,
         * this is when the accept membership is received with a base
         * packet number specified. When no recovery is set, a second flag
         * (initNext) is set to true when membership is obtained. This signals
         * the receiveDataPacket code to set the nextPacket to the next data
         * packet received (retransmissions are discarded.) Once this is done
         * the init flag is cleared and everything proceeds normally. In
         * at least one place, the existance of the holdingTank indicates that
         * limited recovery was selected.
         * 
         * NOTE: This same code is implemented in the TRAMGenericDataCache
         * class also.
         */
        if (tramblk.getTransportProfile().getTmode() != TMODE.SEND_ONLY) {
            lateJoin = tp.getLateJoinPreference();

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this,
		    "TRAMMemberAck: late join is " + lateJoin);
	    }

	    holdingTank = null;

	    switch (lateJoin) {
	        case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
	        case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, "Creating holding tank");
		    }
		    holdingTank = new Vector();
		    break;
		
	        case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		        logger.putPacketln(this, "No holding tank necessary");
		    }
		    break;

		default:
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		        logger.putPacketln(this, 
			    "The Late Join Preference is Invalid");
		    }
		    break;
		}
	    
        }
    }

    /*
     * This is the TRAMAckPacketListener interface. The InputDispatcher
     * calls this method when an TRAMAckPacket is received. this method
     * places the packet on the packetList and resumes the ack processing
     * thread.
     * 
     * @param e an TRAMPacketEvent.
     */

    public synchronized void receiveDataPacket(TRAMDataPacketEvent e) {

        /*
         * The following code handles late joining members. There are three
         * options for late joining members:
         * 
         * limited recovery: Will attempt to recover all packets the the
         * head currently has cached.
         * no recovery:      Start receiving when membership is obtained.
         * All packets prior to this are dropped.
         * Full recovery:    Unsupported.
         * 
         * For limited recovery, packets are stuffed into a holding tank until
         * this member gains membership with the TRAM tree. At that point,
         * the first expected packet is set to the lowest that the head has
         * in its cache. The packets already received in the holding tank
         * are then processed. Packets prior to the heads lowest packet
         * are dropped and all others are processed normally. Limited recovery
         * is assumed if there is a holding tank available. The holding tank
         * is not created for senders or other late join options.
         * 
         * For members requesting no recovery, the first packet expected is
         * set to the first data packet received after membership is obtained.
         */
        if (init) {
            if (holdingTank != null) {
                holdingTank.addElement(e);
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
                        "Loading packet " + e.getPacket().getSequenceNumber() +
		        " into the holding tank");
		}
            } 
            return;
        }

	/*
	 * if the data cache has not processed the membership event,
	 * ignore this message.
	 */
	if (needToWaitForCacheToProcessMembershipEvent) {
	    if (!getDataCacheProcessedMembershipEvent())
		return;
	    
	    needToWaitForCacheToProcessMembershipEvent = false;
	}

        /*
         * Get the data packet and the sequence number.
         */
        TRAMDataPacket pk = e.getPacket();
        int packetNumber = pk.getSequenceNumber();
	boolean forceAck = false;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
	        "Received " + SUBMESGTYPE.mcastData[pk.getSubType()] +
	        " packet " + packetNumber + 
	        " Rate " + pk.getDataRate());
	}

	if (ackWindowSize != pk.getAckWindow()) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		
		"ACK Window " + ackWindowSize + 
		" Doesn't match value in data packet of " +
		pk.getAckWindow() + ".  Using value in data packet");
	    }

	    ackWindowSize = pk.getAckWindow();
	    tp.setAckWindow((short)ackWindowSize);
 	}

        /*
         * Calculate the moving average of the data rate.
         */
        if ((packetNumber % tp.getAckWindow()) == 0) {
            rateAdjuster.calculateAverageDataRate();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this,
		    "Average data rate is " +
		    rateAdjuster.getAverageDataRate()); 
	    }
	}

	/*
	 * debugging info
	 */
	if ((packetNumber % 500) == 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Packet " + packetNumber +
	            ", group flow control info " + 
		    rateAdjuster.getGroupFlowControlInfo() +
	            ", highest seq " + highestSequenceAllowed +
	            ",  C win " + tp.getCongestionWindow() +
		    ", rate " + rateAdjuster.getOpenWindowDataRate());
	    }
	}

	/*
	 * Set the data rate to that of the sender.
	 * This is the rate we will use for retransmissions.
	 */
	rateAdjuster.setDataRate((long)(pk.getDataRate()));

        /*
         * If the received packet is the expected packet (i.e. sequence
         * number is in order), simply bump the expected packet number.
         */
        if (nextPacket.isEqualTo(packetNumber)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this,
		    "Got EXPECTED Data packet " + packetNumber);
	    }

            nextPacket.incrSeqNumber();

	    /*
             * Keep count of data we've received.
             */
	    tramblk.getTRAMStats().addBytesRcvd(pk.getLength());
        } else if (nextPacket.isLessThan(packetNumber)) {

	    /*
	     * If the expected packet is less than the packet we received,
	     * a gap in sequence numbers has been detected. Add a missing
	     * packet entry to the list for all the packets from
	     * what we expected up to and including what we got.
	     *
	     * Also update nextPacket so we now expect 1 higher than what
	     * we just got.
	     */
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, 
		    "Got HIGHER than expected Data packet " + 
		    packetNumber + " (expected " + 
		    nextPacket.getSeqNumber() + ")");
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, 
		    "Duplicate pkt count before recovering missing packets " +
		    tramblk.getTRAMStats().getDuplicatePackets());
	    }

            addMissing(packetNumber);
            nextPacket.setSeqNumber(packetNumber + 1);

	    /*
	     * Keep count of data we've received.
	     */
	    tramblk.getTRAMStats().addBytesRcvd(pk.getLength());
        } else {
	    /*
	     * Last but not least, the packet preceeds the expected packet
	     * number. If we're not missing any packets, it's a duplicate
	     * and we can ignore it.
	     * If we're waiting for missing packets, go check to see if this
	     * is one of them.
	     */
	    if (missingPackets.size() != 0 && 
		checkMissing(packetNumber) == true) {

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	            logger.putPacketln(this, 
		        "Got missing packet " + packetNumber);
		}
		
		if (missingPackets.size() == 0) {
		    /*
		     * We just got the last missing packet.  
		     * We need to ACK so that the highest sequence 
		     * allowed will be updated and the
		     * sender can resume at full speed.
		     */
		    forceAck = true;

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	                logger.putPacketln(this, 
			    " got packet " + packetNumber + " Force an ACK...");
		    }
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	                logger.putPacketln(this, 
			    "Duplicate packet count after last " +
			    " missing packet has been recovered:  " +
			    tramblk.getTRAMStats().getDuplicatePackets());
		    }
		}

		/*
                 * Keep count of data we've received.
                 */
	    	tramblk.getTRAMStats().addBytesRcvd(pk.getLength());

        	if (pk.getSubType() == SUBMESGTYPE.DATA_RETXM) {
	    	    tramblk.getTRAMStats().addRetransBytesRcvd(pk.getLength());
	    	    tramblk.getTRAMStats().incRetransRcvd();
		} 

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	            logger.putPacketln(this,
		        "got missing packet " + packetNumber +
		        " nextPacket " + nextPacket.getSeqNumber() +
		        " packets ranges still missing " + 
			missingPackets.size() +
		        " ack seq " + nextAck.getSeqNumber());
		}
	    } else {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	            logger.putPacketln(this, 
			"Duplicate packet " + packetNumber + " ignored.");
		}

	        tramblk.getTRAMStats().addDuplicatePackets();
	        tramblk.getTRAMStats().addDuplicateBytes(pk.getLength());
	    }
	}

	/*
	 * If we're a repair head and the data packet has the PRUNE flag bit 
	 * set, try to find a member to prune.
	 */
	GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

	if (gblk.getDirectMemberCount() != 0 &&
            (pk.getFlags() & TRAMDataPacket.FLAGBIT_PRUNE) != 0) {
	    /*
	     * We are a repair head and this packet says we should
	     * try to prune a direct member.
	     */
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, 
		    "Find member to prune.  FLAGBIT_PRUNE is set. " +
		    " flow " + pk.getFlowControlInfo());
	    }
	    rateAdjuster.findMemberToPrune(pk.getFlowControlInfo());
	}

        /*
         * If the current packet is past the packet we were supposed to
         * ack on, send the ack.  
         */
        HeadBlock hb = gblk.getHeadBlock();

        if (hb != null) {
	    /*
	     * We need to ACK if we've received the last missing packet 
	     * so that the highest sequence allowed will be updated.
	     * Also ACK if this is a data packet (not a retransmission) and 
	     * the packet number is beyond the one at which we should ACK.
	     */
	    if (forceAck) {
		sendAck((byte)0, hb, 2);
	    } else {
		if (nextAck.isLessThanOrEqual(packetNumber) &&
		    pk.getSubType() == SUBMESGTYPE.DATA) {

            	    sendAck((byte)0, hb, 0);
		}
	    }

            /*
             * Updates the head last heard timestamp, if the data
             * message is from the head.
             */
            if (hb.getAddress().equals(pk.getAddress()) == true) {
                hb.setLastheard(System.currentTimeMillis());
            } 
	}

        /*
         * Check if the TRAM state is REAFFILIATED. If so data packet
         * may be a retransmission from the re-affiliated head. Also
         * we need to monitor for the transition from REAFFILIATED to
         * ATTAINED_MEMBERSHIP.
         */
        if (tramblk.getTRAMState() == TRAM_STATE.REAFFILIATED) {
            try {
                HeadBlock reaffilHead = 
                    tramblk.getGroupMgmtThread().getReAffiliationHead();

                if (reaffilHead.getAddress().equals(pk.getAddress()) 
                        == true) {
                    reaffilHead.setLastheard(System.currentTimeMillis());
                } 

                GroupMgmtThread gmt = tramblk.getGroupMgmtThread();

                if (checkPriorMissing(reaffilHead.getStartSeqNumber()) 
                        == true) {

                    // Perform head switch.

                    gmt.makeReAffilHeadToBeMainHead();
                }
            } catch (NullPointerException npe) {}
        }

    }

    /*
     * Add a missing packet entry to the list. The entry identifies
     * all packets from the last expected packet to the last received
     * packet - 1.
     */

    private synchronized void addMissing(int packetNumber) {
        MissingPacket mp = new MissingPacket(nextPacket.getSeqNumber(), 
                                             packetNumber - 1);

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
	    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_DATAMESG)) {

            logger.putPacketln(this, 
	        "CREATED a new missing entry with start = " + 
		nextPacket.getSeqNumber() + " End = " + (packetNumber - 1));
	}

        missingPackets.addElement(mp);
    }

    /*
     * Check to see if the packet just received is one we're waiting
     * for. Each missing packet entry specifies a start and end sequence
     * number of missing packets. If start equals end the missing packet object
     * represents one missing packet. If the packet we're checking is the
     * start or end packet number, that number is incremented or decremented
     * respectively. If it falls in between the start and end, a new missing
     * packet object is created with start equal to the old start number and
     * the end equal to the current packet number minus one. The existing
     * missing packet objects start number is set to the current packet number
     * plus 1.
     * 
     * Missing packet objects are removed when they represent one packet and
     * it is received.
     */

    private synchronized boolean checkMissing(int packetNumber) {
	boolean gotMissing = false;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                "Checking for missing packet " + packetNumber);
            logger.putPacketln(this, 
	        missingPackets.size() + " missing packets");
	}

	int i = 0;

        while (i < missingPackets.size()) {
            MissingPacket pk = (MissingPacket) missingPackets.elementAt(i);
            TRAMSeqNumber end = pk.getEndPacket();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
                    "Checking missing packet end = " + end.getSeqNumber());
	    }

            if (end.isGreaterThan(packetNumber)) {
                TRAMSeqNumber start = pk.getStartPacket();

	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
                        "Checking missing packet start = " + 
			start.getSeqNumber());

                    logger.putPacketln(this,
		        "Got earlier packet " + packetNumber);
		}

                if (start.isLessThan(packetNumber)) {
		    gotMissing = true;

                    MissingPacket p = new MissingPacket(start.getSeqNumber(), 
                                                        packetNumber - 1);

                    start.setSeqNumber(packetNumber + 1);
                    missingPackets.insertElementAt(p, i);

	            if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                        logger.putPacketln(this, 
		        "Got a missing packet " + packetNumber);
		    }

		    break;
                } else if (start.isEqualTo(packetNumber)) {
	            if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                        logger.putPacketln(this, 
		            "Got a missing packet " + packetNumber);
		    }

		    gotMissing = true;

                    if (start.isEqualTo(end.getSeqNumber())) {
                        missingPackets.removeElementAt(i);
			/*
			 * Don't increment index because elements beyond
			 * this one have been shuffled.
			 */
			continue;
                    } else {
	                if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, 
			        "Bumping start seq");
			}
                        start.setSeqNumber(packetNumber + 1);
                    }
		    break;
                }
            } else if (end.isEqualTo(packetNumber)) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this, 
                        "Got missing packet " + packetNumber);
		}

		gotMissing = true;

                if (end.isEqualTo(pk.getStartPacket().getSeqNumber())) {
                    missingPackets.removeElementAt(i);
		    /*
		     * Don't increment index because elements beyond
		     * this one have been shuffled.
		     */
		    continue;
                } else {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, 
			    "Decrementing end seq number");
		    }
                    end.setSeqNumber(packetNumber - 1);
                }

                break;
            }
	    i++;
        }

        if (dataEnd && !finalAckSent && (missingPackets.size() == 0)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
                    "Sending final ACK. All packets received");
	    }

            HeadBlock hb = tramblk.getGroupMgmtBlk().getHeadBlock();

            if (hb != null) {
                sendAck((byte)TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP,
		    hb, 9);
            }

            finalAckSent = true;

            notify();
        }
	return gotMissing;
    }

    private boolean congested() {
	/*
	 * Don't complain about congestion if we just joined the group.
	 */
	if (System.currentTimeMillis() - joinTime < 10000)
	    return false;

	/*
	 * Make sure we only send one congestion message per ACK window.
	 */
	if (windowCongestionMessageSent == 
	    (nextAck.getSeqNumber() / ackWindowSize)) {

	    return false;	
	}

	int currentMissing = countMissing();

	/*
	 * We sometimes miss 1 or two packets and it's not really because
	 * the sender is sending too fast.  At least that's what I think.
	 * So if we're missing less than or equal to the threshold of missing
	 * packets, don't treat it like congestion otherwise the transmission 
         * rate gets very low.
	 */ 
	if (currentMissing < tp.getMissingPacketThreshold() ||
	    currentMissing <= previousMissing) {

	    previousMissing = currentMissing;
    	    return false;
	}

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CONG)) {

            logger.putPacketln(this, 
                "Congestion!  window " + 
		(nextAck.getSeqNumber() / ackWindowSize) +
		", data rate " + rateAdjuster.getOpenWindowDataRate() +
		", group flow info " + 
		rateAdjuster.getGroupFlowControlInfo() +
		", Missing " + currentMissing +
	        ", highest allowed " + highestSequenceAllowed +
	        ", C win " + tp.getCongestionWindow());
	}

	/*
	 * Reduce the congestion window by the number of additional
	 * packets we are missing since we last looked.
	 */
	rateAdjuster.adjustCongestionWindowDown();

	windowCongestionMessageSent = 
	    nextAck.getSeqNumber() / ackWindowSize;
	
	previousMissing = currentMissing;
	return true;
        
    } 

    /*		
     *
     *   sendAckToNonAffiliatedHead .....
     * The head might be responding after this node has already
     * reaffilited or found a different head.... .No point in checking
     * if the headblock exists in the backup list as we might have removed
     * the head from the list..... Just send an ACK with TERM flag set.
     */
    public void sendAckToNonAffiliatedHead(InetAddress addr, int port,
					   byte flags) {

	TRAMAckPacket pk;
	int max = nextPacket.getSeqNumber() - 1;
	pk = new TRAMAckPacket(tramblk, max);
	pk.setAddress(addr);
	pk.setPort(port);
	flags |= (byte)TRAMAckPacket.FLAGBIT_ACK;
	pk.setFlags(flags);
	pk.setHighestSequenceAllowed(getHighestSequenceAllowed());

	DatagramSocket so = tramblk.getUnicastSocket();
	DatagramPacket dp = pk.createDatagramPacket();
	try {
	    if (tramblk.getSimulator() != null) {
		tramblk.getSimulator().simulateUnicastPacket(dp);
	    } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	        } catch (NullPointerException e) {}

		so.send(dp);
	
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, 
			"Sent ACK with TERM to " + addr);
		}
	    }
	} catch (IOException ioe) {
	}
    }



    /*
     * This method checks the missing packets list to see if there are
     * any missing packets prior to the packet number specified.
     */

    public synchronized boolean checkPriorMissing(int packetNumber) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
                "Checking for missing packets before " + packetNumber);
            logger.putPacketln(this, 
	        missingPackets.size() + " missing packets");
	}

        /*
         * For each missing packet entry, check to see if the packet number
         * specified is less than or equal to the end packet number. If it
         * is return true. If the end is greater than the specified packet
         * number, check to see if the start packet number is less than
         * or equal to the specified packet number. If so, return true;
         * otherwise, return false.
         */
        for (int i = 0; i < missingPackets.size(); i++) {
            MissingPacket pk = (MissingPacket) missingPackets.elementAt(i);
            TRAMSeqNumber seqNumber = pk.getEndPacket();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
                    "Checking missing packet end = " + 
		    seqNumber.getSeqNumber());
	    }

            if (seqNumber.isLessThan(packetNumber)) {
                return true;
            } else {
                seqNumber = pk.getStartPacket();

                if (seqNumber.isLessThan(packetNumber)) {
                    return true;
                }

                return false;
            }
        }

        return false;
    }

    /*
     * method to build an ACK message based on the flag info, current reception
     * characteristics to the head specifed as formal argument.
     */

    public synchronized void sendAck(byte flags, HeadBlock headBlk) {
	sendAck(flags, headBlk, 6);
    }
	
    public synchronized void sendAck(byte flags, HeadBlock headBlk, int where) {
        TRAMAckPacket pk;

	abortTimer();

        /*
         * This method assumes that nextPacket is set to the next expected
         * packet number and max is set to nextPacket - 1.
         * 
         * If the missing packets list is empty, send a simple ack for all
         * packets up to max.
         */
	int base = 0;
        int max = nextPacket.getSeqNumber() - 1;

	int logLevel = TRAMLogger.LOG_VERBOSE;

	if (where == 1 || where == 5)
	    logLevel = TRAMLogger.LOG_CONG;

	String what = "Outside";

	switch (where) {
	case 0:
	    what = "OnSchedule";
	    break;
	case 1:
	    what = "Timeout";
	    break;
	case 2:
	    what = "Forced";
	    break;
	case 3:
	    what = "SeekRepairs";
	    break;
	case 4:
	    what = "GrpMgmt";
	    break;
	case 5:
	    what = "Hello";
	    break;
	case 6:
	    what = "Outside";
	    break;
	case 8:
	    what = "FinalAck";
	    break;
	case 9:
	    what = "Terminate";
	    break;
	}

	/* 
	 * Put code here that removes packets with lower than 
	 * forgetBeforeSeqNum value from the missingPackets list.
	 */
	MissingPacket mp;
	int oldStartSeqNum;
	long killedPackets = 0;

	while (missingPackets.size() > 0) {
 	    mp = (MissingPacket)missingPackets.firstElement();
	    if (mp.getEndPacket().getSeqNumber() < 
		tramblk.getLastKnownForgetBeforeSeqNum()) {

		missingPackets.removeElementAt(0);

	        if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	            logger.putPacketln(this, 
		        "TRAMMemberAck sendAck() : Flushed missing packets " + 
		        mp.getStartPacket().getSeqNumber() + 
		        " through " + mp.getEndPacket().getSeqNumber());
		}

		killedPackets = mp.getEndPacket().getSeqNumber() - 
		mp.getStartPacket().getSeqNumber() + 1;
		tramblk.getTRAMStats().addPacketsNotRecovered(killedPackets);
	    } else {
		if (mp.getStartPacket().getSeqNumber() >
		    tramblk.getLastKnownForgetBeforeSeqNum()) {
		    break; /* we need all packets */
		} else { 
		    /* forget is between start and end */
		    oldStartSeqNum = mp.getStartPacket().getSeqNumber();
		    mp.getStartPacket().setSeqNumber(
			tramblk.getLastKnownForgetBeforeSeqNum());

	            if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	                logger.putPacketln(this, 

		        "TRAMMemberAck sendAck() : Flushed some  packets " + 
		        oldStartSeqNum + " through " + 
			(mp.getStartPacket().getSeqNumber() - 1));
		    }
		    killedPackets = 
			mp.getStartPacket().getSeqNumber() - oldStartSeqNum;
		    tramblk.getTRAMStats().
			addPacketsNotRecovered(killedPackets);
		    break;
		}
	    }
	}
	
	if (missingPackets.size() == 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this, 
	            "Sending a simple ack for " + max);
	    }

	    //
	    // Update the window so the sender can send more.
	    //
	    rateAdjuster.adjustCongestionWindowUp();

	    int newHighest = 
		(((max + ackWindowSize) / ackWindowSize) * ackWindowSize) +
		tp.getCongestionWindow();

	    highestSequenceAllowed = 
		Math.max(highestSequenceAllowed, newHighest);

	    what = "ACK(" + what + ")";
	    base = 1;

	    rateAdjuster.setMyFlowControlInfo(0, 0);
            pk = new TRAMAckPacket(tramblk, max);
        } else {

	    /*
	     * Send an ack for the missing packets. This batch of code 
	     * constructs a bit mask.  The base packet number is equal 
	     * to the first missing packet objects start packet number. 
	     * The bitmask represents all the missing packets in the list.
	     */
            TRAMSeqNumber startPacket = new TRAMSeqNumber();
            TRAMSeqNumber endPacket = new TRAMSeqNumber();

	    mp = (MissingPacket) missingPackets.firstElement();
            base = mp.getStartPacket().getSeqNumber();
            byte mask[] = new byte[(max - base + 8) / 8];

            for (int i = 0; i < mask.length; i++) {
                mask[i] = 0;
            }
            for (int i = 0; i < missingPackets.size(); i++) {
                try {       // catch out of bound exeception!
                    mp = (MissingPacket) missingPackets.elementAt(i);

                    startPacket.setSeqNumber(
			mp.getStartPacket().getSeqNumber());
                    endPacket.setSeqNumber(mp.getEndPacket().getSeqNumber());

                    int start = startPacket.getSeqNumber();
                    int end = endPacket.getSeqNumber();

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, 
                            "Processing missing packets element " + 
			    start + " to " + end);
		    }
			
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, 

			"TRAMMemberAck sendAck() : " +
			" Processing missing packets element " +  
			start + " to " + end);
		    }

                    while (startPacket.isLessThanOrEqual(end)) {
                        int offset = Math.abs(start - base) % 8;
                        int index = Math.abs(start - base) / 8;

		        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, 
                                "offset = " + offset + " Index = " + index);
		        }

                        if ((Math.abs(end - base) / 8) == index) {
                            mask[index] |= 
                                ~((maskOffset[offset] 
                                   ^ maskBase[Math.abs(end - base) % 8]));

		            if (logger.requiresLogging(
				TRAMLogger.LOG_VERBOSE)) {

                                logger.putPacketln(this, 
                                    "maskOffset = " + maskOffset[offset] + 
				    "maskBase = " + 
				    maskBase[Math.abs(end - base) % 8]);

                                logger.putPacketln(this, 
                                    "Setting partial byte " + 
				    mask[index] + " for " + index);
			    }
                        } else {
		            if (logger.requiresLogging(
				TRAMLogger.LOG_VERBOSE)) {

                                logger.putPacketln(this,
                                    "Setting full mask " + maskOffset[offset] + 
				    " for " + index);
			    }

                            mask[index] |= maskOffset[offset];
                        }

                        start += 8 - offset;

                        startPacket.setSeqNumber(start);
                    }
                } catch (IndexOutOfBoundsException ie) {
                    break;
                }
            }

	    /*
	     * Since we're sending an ACK, we might as well advance the
	     * window for the sender if we can.  (We never set the size lower
	     * than what it was before.)
	     */
	    int newHighest = 
		(((base - 1 + ackWindowSize) / ackWindowSize) * ackWindowSize) +
		tp.getCongestionWindow();

	    highestSequenceAllowed = 
		Math.max(highestSequenceAllowed, newHighest);

	    rateAdjuster.setMyFlowControlInfo(max - base, countMissing());
            pk = new TRAMAckPacket(tramblk, mask, base, 
		Math.abs(max - base) + 1);

	    what = "NACK(" + what + ")";
	    logLevel = TRAMLogger.LOG_CONG;
        }

        try {

            /*
             * The bitmask has been constructed. Set the other
             * ack fields accordingly and send the packet.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Sending an Ack packet, base " + base + " max " + max);
	    }

	    if ((flags & TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP) != 0) {
	        if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this,
		        "Sending an Ack packet with TERMINATE_MEMBERSHIP to " + 
		        headBlk.getAddress());
		}
	    }

            // GroupMgmtBlk gb = tramblk.getGroupMgmtBlk();

            pk.setAddress(headBlk.getAddress());
            pk.setPort(headBlk.getPort());

            /*
             * Gather direct and indirect member Count and advertising
             * head count information and initialize the appropriate fields.
             * in the Ack packet.
             */
            GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

            pk.setDirectMemberCount(gblk.getDirectMemberCount());
            pk.setIndirectMemberCount(gblk.getIndirectMemberCount());
            pk.setDirectHeadsAdvertising(
		gblk.getDirectAdvertisingMemberCount());
            pk.setIndirectHeadsAdvertising(
		gblk.getIndirectAdvertisingMemberCount());

	    pk.setHighestSequenceAllowed(getHighestSequenceAllowed());
	    pk.setFlowControlInfo(rateAdjuster.getGroupFlowControlInfo());
	    pk.setDataRate(
		(int)tramblk.getRateAdjuster().getOpenWindowDataRate());

	    /*
	     * If I'm a repair head but a member below has worse flow control
	     * information, set a flag to indicate that a member below is the
	     * culprit and not me.
	     */
            if (rateAdjuster.IsSubtreeWorse()) {
                flags |= TRAMAckPacket.FLAGBIT_SUBTREE_FLOW_CONTROL_INFO;
	    }

	    if (congested())
		flags |= TRAMAckPacket.FLAGBIT_CONGESTION;

	    flags |= TRAMAckPacket.FLAGBIT_ACK;
            pk.setFlags(flags);

            DatagramSocket so = tramblk.getUnicastSocket();
            DatagramPacket dp = pk.createDatagramPacket();

            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateUnicastPacket(dp);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	        } catch (NullPointerException e) {}

                so.send(dp);
            }
        } catch (NullPointerException ne) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "No Head address yet.");
	    }
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
	 * Update the sequence at which we should send another ACK.
	 * We only do this if we sent an ACK on schedule.
         */
	int thisAck = nextAck.getSeqNumber();

        if (nextAck.isLessThanOrEqual(nextPacket.getSeqNumber()))
            nextAck.setSeqNumber(max + ackWindowSize);

        /*
         * After an ACK is sent, a timer is posted to predict when the
         * next ack should be sent. New data packets trigger the normal
         * ack processing. If there is a period of time where no new data
         * is seen, the timer will fire and see if we need to send an ACK.
         * The determination is made in the "handleTimeout" routine.
         */
        loadTimer();

	if (logger.requiresLogging(logLevel)) {
            logger.putPacketln(this, 
	        what + " for [" + base + "-" + max + "], missing " + 
	        countMissing() + " thisAck " + thisAck + 
	        " nextAck " + nextAck.getSeqNumber() + " flow " + 
	        rateAdjuster.getGroupFlowControlInfo());
	}

        lastAckTime = System.currentTimeMillis();
        lastAckPacket = max;
    }

    /*
     * This method reloads the tramTimer for ACK processing.
     * The timeout period is equal to the estimated time for one ack
     * window worth of packets to arrive times 2 but not less that
     * 3 seconds.
     */

    public void loadTimer() {
	abortTimer();

        int max = nextPacket.getSeqNumber() - 1;
        int packets;
	long now = System.currentTimeMillis();

        /*
         * If no packets have been received over the last interval, skip the
         * calculation and use the last interval saved in the tramblk. If
         * the lastAckTime is zero, skip this calculation and use the default.
         */
        if (((packets = max - lastAckPacket) >= (ackWindowSize / 2)) 
                && (lastAckTime != 0)) {
            long timeout = 2 * (((now - lastAckTime)*ackWindowSize)/packets);

	    if (timeout > 0) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this, "Set Ack interval to " + timeout);
		}

                tramblk.setAckInterval(timeout);
	    } else {
		if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                    logger.putPacketln(this,
		        "negative timeout value " + timeout + 
		        " using previous value of " + tramblk.getAckInterval());
		}
	    }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Ack timeout " + tramblk.getAckInterval() + 
	        ", " + packets + " packets in " + (now - lastAckTime) + " ms");
	}

        tramTimer = new TRAMSimpleTimer(tramblk.getAckInterval(), this, logger);
    }

    /*
     * Abort the timer.  This is called when session down is discovered
     * so that this thread doesn't try to send.
     */
    public synchronized void abortTimer() {
        if (tramTimer != null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "Killing off old ACK timer");
	    }
            tramTimer.abortTimer();
        }
	tramTimer = null;
    }

    /*
     * This method handles timeouts from the tramTimer. If this method is
     * called, then we haven't sent an ACK for some period of time. If
     * there are missing packets we'd like to get retransmitted, send
     * an ack. If there are no missing packets, just reload the timer.
     */

    public void handleTimeout() {
        HeadBlock hb = tramblk.getGroupMgmtBlk().getHeadBlock();

        if (hb != null) {
	    if (missingPackets.size() != 0) {
                sendAck((byte) 0, hb, 1);  // send ack and reload timer
		return;
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
                    "No ack necessary from handle timeout routine.");
	    }
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this,
	   	    "ack timeout, head block is null");
	    }
        }
        loadTimer();
    }

    public void printMissing(Vector v, String s) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
                "missing packets " + countMissing() + 
	        ", next expected " + nextPacket.getSeqNumber() + 
	        ", Highest allowed " + highestSequenceAllowed +
	        ", C Win " + tp.getCongestionWindow());
	}

        for (int i = 0; i < v.size(); i++) {
            MissingPacket p = (MissingPacket) v.elementAt(i);

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
                    s + " Missing packets " + 
		    p.getStartPacket().getSeqNumber() + " to " + 
		    p.getEndPacket().getSeqNumber());
	    }
        }
    }

    public synchronized int countMissing() {
        int count = 0;

        for (int i = 0; i < missingPackets.size(); i++) {
            MissingPacket mp = (MissingPacket) missingPackets.elementAt(i);

            count += Math.abs(mp.getEndPacket().getSeqNumber() - 
		     mp.getStartPacket().getSeqNumber()) + 1;
        }

        return count;
    }

    /**
     * This method is the interface for BeaconPacketListener. The multicast
     * input dispatcher calls this method when an beacon packet is received.
     * This method then places the packet on the pkts_to_process Queue/vector
     * and resumes the thread.
     */
    public synchronized void receiveBeaconPacket(BeaconPacketEvent e) {
        HeadBlock hb;
        BeaconPacket pk = e.getPacket();
        byte b = (byte) pk.getFlags();

        /*
         * There is a possibility that either the group management
         * block has not been initialized or we don't have a head.
         * Just catch the null pointer exception and ignore it.
         */
        try {

            /*
             * If the head happens to be the sender, update the last
             * heard time stamp.
             */
            hb = tramblk.getGroupMgmtBlk().getHeadBlock();

        } catch (NullPointerException ne) {
            hb = null;
        }

        if ((b & BeaconPacket.FLAGBIT_TXDONE) != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_SESSION | TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this,
		    "Received Data End Beacon. Last Pkt Seq Num is " +
		    pk.getSeqNumber());
	    }

            handleDataTransmissionComplete(hb, pk.getSeqNumber());
            return;
        }

        if ((b & BeaconPacket.FLAGBIT_FILLER) != 0) {
            /*
             * If the beacon is a filler beacon, seek repairs that the
             * node may be waiting.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

		logger.putPacketln(this,
                    "Received Filler Beacon. Last Reported Packet is " + 
		    pk.getSeqNumber());
	    }

            seekRepairsForPendingPkts(hb, pk.getSeqNumber());
        }
    }

    /*
     * Private method to seek repairs incase of receiving a filler beacon.
     * 
     */

    public synchronized void seekRepairsForPendingPkts(HeadBlock hb, 
                                          int reportedLastSeqNumber) {

        if (finalAckSent) {
            return;
        } 

	if (init) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Can't seek repairs 'cause we're still initializing...");
	    }
	    return;	// we're still initializing
	}

        /*
         * first checkout if we are affiliated to a head. If not affiliated
         * then just return.
         */
        if (hb == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Can't seek repairs 'cause we're not bound to a head...");
	    }
            return;
        } 

        /*
         * If the reported sequence number is greater than the expected next
         * sequence number, then we need to add packets to the missing
         * packet list.
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
		"seekRepairs next seq " + nextPacket.getSeqNumber() + 
		" reportLastSeq " + reportedLastSeqNumber);
	}

        if (nextPacket.isLessThanOrEqual(reportedLastSeqNumber)) {

            addMissing(reportedLastSeqNumber + 1);
            nextPacket.setSeqNumber(reportedLastSeqNumber + 1);
        }

        if (missingPackets.size() != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
	            "seekRepairs next seq " + nextPacket.getSeqNumber() + 
	            ", reportLastSeq " + reportedLastSeqNumber + 
	            ", previousMissing " + previousMissing +
	            ", currentMissing " + countMissing());
	    }

            /*
             * If we have missing packets... seek retransmission.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "Sending ack... requesting repairs " +
                    " Reported last " + reportedLastSeqNumber +
                    ". Expected next packet is " + nextPacket.getSeqNumber());
	    }

            sendAck((byte)0, hb, 3);
        }
    }

    public synchronized void handleDataTransmissionComplete(HeadBlock hb, 
            int finalSeqNumber) {
        if (tramblk.isDataTransmissionComplete() == false) {
            tramblk.setDataTransmissionComplete(true);
            tramblk.setLastKnownSequenceNumber(finalSeqNumber);
        }
        if (nextPacket.isLessThanOrEqual(finalSeqNumber)) {
            addMissing(finalSeqNumber + 1);
            nextPacket.setSeqNumber(finalSeqNumber + 1);
        } 
        if (missingPackets.size() == 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_SESSION | TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this,  
                    "Sending final ack.  All packets received");
	    }

            if (hb != null) {
                sendAck((byte)TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP, hb, 
		    8);
            }

            /*
             * Its okay to do the following even if the above attempt
             * send the ACK failed.
             */
            finalAckSent = true;

            notify();
        } else {
            if (hb != null) {
                sendAck((byte)0, hb, 8);
            } 
        }

        dataEnd = true;
    }

    private synchronized void removeUnrecoverableMissingPkts(
	int upToSeqNumber, int bmLength, int[] bitmask, boolean maskValid) {
        
	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
	        "Handling UNRECOVERABLE packets up to (not including) " + 
	        upToSeqNumber);
	}

	int i = 0;

        while (i < missingPackets.size()) {
            MissingPacket pk = (MissingPacket) missingPackets.elementAt(i);

            if (pk.getEndPacket().isLessThan(upToSeqNumber)) {
		/*
		 * Since we're removing element i, all the later elements 
		 * are renumbered.  So the element after the one we removed,
		 * now has the same index as the one we removed.
		 */ 
                missingPackets.removeElementAt(i);
            } else if (pk.getStartPacket().isLessThan(upToSeqNumber)) {
                pk.getStartPacket().setSeqNumber(upToSeqNumber + 1);
		i++;
            } else {
		i++;
	    }
        }
    }

    public synchronized void waitToComplete() {
        while (true) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
		    "Waiting for member task to complete");
	    }

            if (finalAckSent == false) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            } else {
                break;
            }
        }

        return;
    }

    public void printDataCounts() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "MissingPackets = " + missingPackets.size());
	}
    }

    /**
     * public method to the sequence number of the next expected packet.
     * @return the next expected packet sequence number.
     */
    public int getNextPktSeqNumberToReceive() {
        return nextPacket.getSeqNumber();
    }

    /*
     * Membership has been received. Set the start sequence number to that
     * of the lowest packet cached at our new head. Clear the init flag
     * and replay all of the packets previously received while we were
     * not attached to the TRAM tree.
     */

    public synchronized void receiveTRAMMembership(TRAMMembershipEvent e) {
	HeadBlock head = tramblk.getGroupMgmtBlk().getHeadBlock();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
	        "Received membership notification from " + 
		head.getAddress());
	}

	highestSequenceAllowed = head.getStartSeqNumber() +
	    tp.getCongestionWindow();
	    
	joinTime = System.currentTimeMillis();

        if (init) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this,
		    "The init flag is set. carry on");
	    }

	    init = false;
	    clearDataCacheProcessedMembershipEvent();
	    switch (tp.getLateJoinPreference()) {
	    case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
		/*
		 * In this case we need to make missing packet entries
		 * ALL the packets we are missing till the packet #
		 * that is being promised by the head.
		 *
		 */
		if (nextPacket.isLessThan(head.getStartSeqNumber())) {
		    /*
		     * Create Missing Packet entries from 1st packet till
		     * the promised packet number.
		     */
		    addMissing(head.getStartSeqNumber());
		}
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_SESSION | TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
		        "Setting the starting sequence number to " + 
		        head.getStartSeqNumber());
		}

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {
		
		    logger.putPacketln(this,
			"Replaying the " + holdingTank.size() + 
			" packets in the holdingTank");
		}

		nextPacket.setSeqNumber(head.getStartSeqNumber());
		
		/*
		 * replay the captured packets to simulate as if the
		 * packets were just received.
		 */
		for (int i = 0; i < holdingTank.size(); i++) {
		    receiveDataPacket(
			(TRAMDataPacketEvent) holdingTank.elementAt(i));
		}
		holdingTank = null;

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this,
		        "The holding tank has been deleted");
		}
		break;

	    case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
		/*
		 * In this case we need to forget packet before the
		 * head start seq number and then replay the packets
		 * in the holding tank so that we don't go about
		 * asking retransmissions for packets that we have
		 * already received.
		 *
		 */
		nextPacket.setSeqNumber(head.getStartSeqNumber());

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
		        "Setting the starting sequence number to " + 
		        head.getStartSeqNumber());
		}

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Replaying the " + holdingTank.size() + 
			" packets in the holdingTank");
		}

		/*
		 * replay the captured packets to simulate as if the
		 * packets were just received.
		 */
		for (int i = 0; i < holdingTank.size(); i++) {
		    receiveDataPacket(
			(TRAMDataPacketEvent) holdingTank.elementAt(i));
		}
		holdingTank = null;

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this,
		        "The holding tank has been deleted");
		}
		break;

	    case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:
		/*
		 * The treatment for either of these cases is the same -
		 * All packets received before the start sequence # will
		 * dropped.
		 */

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
       		    TRAMLogger.LOG_SESSION | TRAMLogger.LOG_DATACACHE)) {

                    logger.putPacketln(this, 
		        "Setting the starting sequence number to " + 
		        head.getStartSeqNumber());
		}

                nextPacket.setSeqNumber(head.getStartSeqNumber());
		/*
		 * mechanism to synchronize data cache and the member ack
		 * module.
		 */
		needToWaitForCacheToProcessMembershipEvent = true;
		break;

	    default:
		init = true;
		break;
	    }
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this,
	            "The init flag is not Set.  Must be head switch");
	    }

	    switch (tp.getLateJoinPreference()) {
	    case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
	    case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
		/*
		 * In this case we need to make missing packet entries
		 * ALL the packets we are missing till the packet #
		 * that is being promised by the head.
		 *
		 */
		if (nextPacket.isLessThan(head.getStartSeqNumber())) {
		    /*
		     * Create Missing Packet entries from 1st packet till
		     * the promised packet number.
		     */
		    addMissing(head.getStartSeqNumber());
		    nextPacket.setSeqNumber(head.getStartSeqNumber());
		}
		break;

	    case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:

		/*
		 * All packets received before the start sequence # will
		 * be forgotten or removed from the missing list.
		 */

		int upToSeqNumber = head.getStartSeqNumber();
		/*
		 * Now remove all missing entries that are less than the
		 * promised sequence number of the supporting head.
		 */
		removeUnrecoverableMissingPkts(upToSeqNumber, 0, null, false);
		if (nextPacket.isLessThan(upToSeqNumber)) {
		    nextPacket.setSeqNumber(upToSeqNumber);
		}

		break;

	    default:
	        if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, "Invalid LateJoin Recovery Pref");
		}
		break;
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATACACHE)) {

	    	logger.putPacketln(this, 
		    "Next Packet to Receive sequence # " + 
		    nextPacket.getSeqNumber() + " Head StartSeq # " + 
		    head.getStartSeqNumber());
	    }

	} /* Else ends here */
    }

    /*
     * dataCacheProcessedMembershipEvent()
     */

    public synchronized void dataCacheProcessedMembershipEvent() {

	dataCacheProcessedMembershipEvent = true;
    }

    /*
     * clearDataCacheProcessedMembershipEvent()
     */

    public synchronized void clearDataCacheProcessedMembershipEvent() {

	dataCacheProcessedMembershipEvent = false;
    }

    /*
     * getDataCacheProcessedMembershipEvent()
     */

    public boolean getDataCacheProcessedMembershipEvent() {

	return dataCacheProcessedMembershipEvent;
    }

    /*
     * dealWithUnrecoverablePkts()
     */
    public synchronized void dealWithUnrecoverablePkts(int upToSeqNumber) {
	switch (tp.getLateJoinPreference()) {
	  case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
	  case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
		/*
		 * In this case we need to make missing packet entries
		 * ALL the packets we are missing till the packet #
		 * that is being promised by the head.
		 *
		 */
		break;

	  case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:

		/*
		 * All packets received before the upToSeqNumber will
		 * be forgotten or removed from the missing list.
		 */

		removeUnrecoverableMissingPkts(upToSeqNumber, 0, null, false);
		break;

	   default:
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this,
			"Invalid LateJoin Recovery Pref");
		}
		break;
	    }

    }

    /*
     * Get the highest sequence number allowed for the group.
     * If we're a head, then we have to take our own highest seq
     * into consideration.  Return the minimum of the group highest
     * including my highest in the group.
     */
    private int getHighestSequenceAllowed() {
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

	if (gblk.getDirectMemberCount() == 0)
            return highestSequenceAllowed;

	int groupHighestSequenceAllowed = gblk.getHighestSequenceAllowed();

	if (highestSequenceAllowed < groupHighestSequenceAllowed)
	    return highestSequenceAllowed;

	return groupHighestSequenceAllowed;
    }

}

/*
 * This class maintains the information on the missingPacket list
 * for each missing packet. It stores the packet number and the
 * time the packet was detected to be missing.
 */

class MissingPacket {
    private TRAMSeqNumber startPacket;
    private TRAMSeqNumber endPacket;

    /*
     * Create a MissingPacket object.
     * 
     * @param packetNumber the packet sequence number of the missing data
     * packet.
     */

    public MissingPacket(int start, int end) {
        startPacket = new TRAMSeqNumber(start);
        endPacket = new TRAMSeqNumber(end);
    }

    public TRAMSeqNumber getStartPacket() {
        return startPacket;
    }

    public TRAMSeqNumber getEndPacket() {
        return endPacket;
    }

    public void setStartPacket(int value) {
        startPacket.setSeqNumber(value);
    }

    public void setEndPacket(int value) {
        endPacket.setSeqNumber(value);
    }

}
