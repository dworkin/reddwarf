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
 * Hellothread.java
 * 
 * Module Description: 
 * 
 * This module is responsible for dispatching Hello
 * messages to the dependent members and also for
 * monitoring the dependent head.
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.*;
import java.net.*;
import java.io.*;

class HelloThread extends Thread implements TRAMTimerEventHandler {

    /*
     * private fields.
     */
    private TRAMControlBlock tramblk = null;
    private static String name = "TRAM HelloThread";
    private Vector pktsToProcess = new Vector(10, 10);
    private boolean sendLowestAvailablePktInfo = false;
    private TRAMTimer timer = null;
    private TRAMLogger logger = null;
    private boolean sendHelloFlag = false;
    private int helloSent = 0;
    private long lastHelloSent = 0;
    private boolean done = false;

    /*
     * NOTE:
     * This is a temporary fix and will remain as long as the
     * multicast socket send() with TTL bug exists.
     * When the bug is fixed the following code needs to be removed
     */
    private MulticastSocket ms = null;

    /*
     * Constructor.
     */

    public HelloThread(TRAMControlBlock tramblk) {
        super(name);

        this.tramblk = tramblk;
        logger = tramblk.getLogger();
        timer = new TRAMTimer(name + " Timer", this, logger);

        tramblk.setHelloThread(this);
        setDaemon(true);

        // ms = tramblk.getMulticastSocket();

        /*
         * NOTE :
         * This   is a temporary fix and will remain as long as the
         * multicast socket send() with TTL bug exists.
         * When the bug is fixed the following code from try thru
         * catch(inclusive) needs to be removed. Further uncomment
         * the above getMulticastSocket() line.
         */
        if (tramblk.getSimulator() == null) {
            try {
                ms = tramblk.newMulticastSocket();
            } catch (IOException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                    logger.putPacketln(this, 
		        "Unable to open Multicast socket");
		}
            }
        }

        start();
    }

    /*
     * run method
     */

    public void run() {
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        /*
         * We do Hello once for every ACK interval
         */
        long helloInterval = Math.max((long) 3000, tramblk.getAckInterval());

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, "Initial hello Interval is " + 
		helloInterval);
	}

        timer.loadTimer(helloInterval);

        while (!done) {

            // Suspend the thread

            if (getSendHelloFlag() == true) {
                buildAndDispatchHelloPacket();
                setSendHelloFlag(false);
            }

            // logger.putPacketln(this,"Hello Thread Suspending");

            stall();
        }
    }

    /*
     * Call this method to wait for a wake event.
     */

    private synchronized void stall() {
        try {
            wait();
        } catch (InterruptedException ie) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, "Interrupted!");
	    }
        }
    }

    /*
     * Call this method to wake a stalled thread.
     */

    private synchronized void wake() {
        notifyAll();
    }

    /*
     * Determine if there are members which have missed ACK's.
     */
    private boolean memberHasMissedAcks() {
	GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();
	int myMemberCount = gblk.getDirectMemberCount();

        for (int i = 0; i < myMemberCount; i++) {
            try {
                MemberBlock mb = gblk.getMember(i);

		/*
		 * The timeout value is hardcoded presently. This will
		 * have to be tied to the timer that the acks at the
		 * receiver uses. This time is envisioned to be long
		 * enough to receive 1 window's worth of data. This
		 * timer should take into account the current rate
		 * of data transmission.
		 */
		if (mb.getMissedAcks() > 0)
		    return true;
            } catch (IndexOutOfBoundsException e) {
                break;
            }
	}
	return false;
    }
  
    /**
     * This method is a interface for TRAMTimerEventHandler. Handles the
     * timeout event.
     */
    public void handleTimeout() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "In handleTimeout");
	}

        TRAMTransportProfile tp = tramblk.getTransportProfile();
        long helloInterval = Math.max((long) 3000, tramblk.getAckInterval());

        /*
         * First findout if it is performing the role of a head.
         * If so it needs to send out a Hello. If not performing
         * role of a head, Checkout the lastHeard timeStamp for the
         * dependent head. if it is not heard for more than 3
         * Hello intervals, then force an ACK to be sent out with
         * FLAGBIT_HELLO_NOT_RECVD flag set.
         */
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        if (gblk.getDirectMemberCount() != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
	            "Member Count " + gblk.getDirectMemberCount());
	    }

	    if (memberHasMissedAcks()) {
	        if (logger.requiresLogging(TRAMLogger.LOG_CONG))
                    logger.putPacketln(this, "Missed ACk's!");

		if (((TRAMGenericDataCache)
		    (tramblk.getTRAMDataCache())).aboveHighWaterMark() ||
		    tramblk.getRateAdjuster().getOpenWindowDataRate() != 
		    tramblk.getRateAdjuster().getActualDataRate()) {
		   
		    helloInterval = tp.getPruneHelloRate();

		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                        logger.putPacketln(this, "Using Prune Hello Rate...");
		    }
		}
	    }

            setSendHelloFlag(true);
            wake();
        }


        HeadBlock hb = gblk.getHeadBlock();

        if (hb != null) {
            validateHeadsLiveliness(hb, false);
        }

        /*
         * May be the receiver is attempting to re-affiliate. In which
         * case the re-affilaition head also needs to be monitored.
         */
        try {
            hb = tramblk.getGroupMgmtThread().getReAffiliationHead();

            if (hb != null) {
                validateHeadsLiveliness(hb, true);
            }
        } catch (NullPointerException ne) {

        /*
         * This is caught sometimes.... typically when TRAM is being shutdown
         * and the GroupMgmtThread is already shut when the timer
         * goes off before its silenced.
         */

        // Currently no action .... just ignore.

        }

        /*
         * Monitor the sender's Liveliness ONLY if the data transmission
         * is in progress. If the Data transmissions has completed, it is
         * possible for the the sender to exit out of the session as it may have
         * completed its head duties to its immediate members.
         */
        if ((tp.getTmode() == TMODE.RECEIVE_ONLY) && 
	    (tramblk.isDataTransmissionComplete() == false)) {

            tramblk.getGroupMgmtThread().validateSenderLiveliness();
        }

        // Reload the timer.
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Loading Hello interval as " + helloInterval);
	}
	/*
	 * Reload the timer only if the hello thread is still active
	 */
	if ((done == false) && (tramblk.getHelloThread() != null))
	    timer.loadTimer(helloInterval);
	else {
	    // kill the timer.
	    timer.killTimer();
	}
    }

    /**
     * The methods adds the reported packet number to the unavailablePacket
     * list. This list will be relayed to the members to indicate that
     * these packets are no longer available. This list will be populated
     * by the HeadAck module as a result of receiving a retransmission
     * request by a member. The unavailable packet list entries will be
     * released when a Hello is dispatched with the unavailablility
     * information included.
     */
    public synchronized void reportUnavailablePacket(int seqNumber) {
        sendLowestAvailablePktInfo = true;
    }

    /**
     * stops the Hello thread.
     */
    public void terminate() {
	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, "Stopping the Hello thread.");
	}

        if ((timer != null) && (timer.isAlive())) {

            /*
             * stop the time if running.
             */
            timer.stopTimer();
            timer.killTimer();
        }

        tramblk.setHelloThread(null);

        /*
         * NOTE:
         * This is a temporary fix and will remain as long as the
         * multicast socket send() with TTL bug exists.
         * When the bug is fixed the following code needs to be commented
         * out or removed.
         */
        if (tramblk.getSimulator() == null) {
            ms.close();
        } 

        /* Tell the main thread to termintate. */

        done = true;

        interrupt();
    }

    /**
     * private method to check if a hello message needs to be sent.
     * This flag will be set by the timeout handler whenever the Hello
     * timer expires. The hello thread in the run loop uses this
     * method to detect if a hello needs to be dispatched.
     * This is made as a method for reasons relating to mutual exclusion.
     * This field can potentially be set by two threads - by the run loop
     * upon dispatching the Hello and the timeout handler trying the schedule
     * Hello to be sent.
     * 
     * @return true - if a hello message is to be dispatched
     * false - if no Hello message is to be dispatched.
     */
    private synchronized boolean getSendHelloFlag() {
        return sendHelloFlag;
    }

    /**
     * This is a private method to schedule of clear the dispatch
     * of a Hello message. This method is invoked in the run loop and
     * timout handler. This is made a method for mutual exclusion
     * reasons.
     * @param value true to schedule a Hello to be sent.
     * false to clear a scheduled Hello to be sent.
     */
    private synchronized void setSendHelloFlag(boolean value) {
        sendHelloFlag = value;
    }

    /**
     * Private method to build and dispatch a Hello message. This
     * needs to access the the Member block and check which members
     * address need to be included in the hello message. The members
     * address may be included if it is determined that they have been
     * unheard beyond a certain time or if a TTL computation needs to
     * be performed.
     */
    private void buildAndDispatchHelloPacket() {
        GroupMgmtBlk mgmtBlk = tramblk.getGroupMgmtBlk();
        MemberBlock mb = null;
        Vector toAckMembers = new Vector(10, 10);

        /*
         * First shortlist the members that need to be included in the
         * Hello message. If the MemberList is 0, this routine will not be
	 * called as it is already checked in the handleTimeout() routine.
         */
        long currentTimeInMs = System.currentTimeMillis();
        long helloInterval = Math.max((long) 3000, tramblk.getAckInterval());
        GroupMgmtThread gThread = tramblk.getGroupMgmtThread();
	int myMemberCount = mgmtBlk.getDirectMemberCount();
        for (int i = 0; i < myMemberCount; i++) {
            try {
                mb = mgmtBlk.getMember(i);

		/*
		 * The timeout value is hardcoded presently. This will
		 * have to be tied to the timer that the acks at the
		 * receiver uses. This time is envisioned to be long
		 * enough to receive 1 window's worth of data. This
		 * timer should take into account the current rate
		 * of data transmission.
		 */
		if (mb.getMissedAcks() > 0) {
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		        logger.putPacketln(this, 
                            "Hello?  " + mb.getAddress() +
                            ", Missed ACK's " + mb.getMissedAcks() +
                            ", Hello rate " + helloInterval +
                            ", current time " + currentTimeInMs +
                            ", lastheard " + mb.getLastheard());
		    }
		}

		if (((currentTimeInMs - mb.getLastheard()) 
		     > (2 * helloInterval))) {

		    /* Verify the hard coded value chosen */
		    
		    if (mb.getMissedAcks() > 4) {
			
			// Suggest GroupMgmtThread to Disown Member
			
			gThread.handleMemberLoss(mb);
			tramblk.getTRAMStats().addLostMembers();

			/*
			 * since the member will be removed from the list
			 * account for the lost member by decrementing
			 * i by 1
			 */
			i--;
		    } else {
			mb.incrMissedAcks();
			toAckMembers.addElement(mb);
		    }
		}
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }

        /*
         * Find out if a HELLO message needs to be sent.
         */
        switch (tramblk.getTRAMState()) {

        case TRAM_STATE.DATA_TXM: 

        case TRAM_STATE.CONGESTION_IN_EFFECT: 

        case TRAM_STATE.PRE_DATA_BEACON: 

        case TRAM_STATE.POST_DATA_BEACON: 

            // The sender Sends Hello ONLY if an ACK is to be demanded

            if ((toAckMembers.size() == 0) 
                    && (isSendLowestAvailablePktInfo() == false)) {
                return;
            } 

            break;

        default: 
            break;
        }

        TRAMHelloPacket hpkt = null;
        byte flagSetting = 0;

        if (toAckMembers.size() != 0) {

            /* Now make an array of member addresses */

            InetAddress[] addresses = new InetAddress[toAckMembers.size()];

            for (int i = 0; i < toAckMembers.size(); i++) {
                mb = (MemberBlock) toAckMembers.elementAt(i);
                addresses[i] = mb.getAddress();

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

                    logger.putPacketln(this, 
		        "Adding " + mb.getAddress() + " to hello packet");
		}
            }

            // Now build a Hello message and dispatch it.

            int highestPkt = 
		tramblk.getTRAMDataCache().getHighestSequenceNumber();
            hpkt = new TRAMHelloPacket(tramblk, mgmtBlk.getRetransmitTTL(), 
                                      toAckMembers.size(), 
				      highestPkt, addresses);
            flagSetting |= TRAMHelloPacket.FLAGBIT_ACK;

            /*
             * Fall thru and check if Missing packet list needs to
             * sent
             */
        }

        /*
         * finally... if the hello has yet been dispatched in the above
         * two cases, then send a generic hello message.
         */
        if ((hpkt == null) && ((canSuppressHello()) == false)) {
            int highestPkt = 
		tramblk.getTRAMDataCache().getHighestSequenceNumber();

            hpkt = new TRAMHelloPacket(tramblk, mgmtBlk.getRetransmitTTL(), 0, 
                                      highestPkt, null);
        }
        if (hpkt != null) {

            /*
             * find out if Data End if can be included... this is important
             * because there is only 1 seq number field which needs to be
             * shared to report obselete seq number and the Data
             * end final sequence number.
             */
            if (tramblk.isDataTransmissionComplete()) {
                flagSetting |= TRAMHelloPacket.FLAGBIT_TXDONE;

                /* Include the final seq number */

                hpkt.setHighSeqNumber(tramblk.getLastKnownSequenceNumber());
            }

            hpkt.setFlags(flagSetting);
            if (dispatchHelloPacket(hpkt.createDatagramPacket())) {
	        try {
	            tramblk.getTRAMStats().setSendCntlMsgCounters(hpkt);
	        } catch (NullPointerException e) {}
	    }
        }
    }

    /*
     * private method to dispatch a built Hello message
     */

    private boolean dispatchHelloPacket(DatagramPacket dp) {
        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateMulticastPacket(dp, 
                        SUBMESGTYPE.HELLO, 
                        tramblk.getGroupMgmtBlk().getRetransmitTTL());
            } else {
                ms.send(dp, tramblk.getGroupMgmtBlk().getRetransmitTTL());
            }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

                logger.putPacketln(this, 
		    "Sent a Hello Packet to " + dp.getAddress() + " " +
		    dp.getPort() + 
		    ", retransmit ttl is " + 
		    tramblk.getGroupMgmtBlk().getRetransmitTTL());
	    }

            // Update last Hello sent timestamp.
            lastHelloSent = System.currentTimeMillis();

        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "unable to send Hello packet ");
	    }
	    return false;
        }
	return true;
    }

    /**
     * private method to check if a Hello can be suppressed.
     * The Hello can be suppressed if an multicast repair was performed
     * within an interval that is half of the hello interval. Receiving a
     * a data packet from a head accounts for receiving a Hello by the
     * members.
     * 
     * @return true if the Hello message can be suppressed at this time
     * false if the Hello message cannot be suppressed at this time.
     */
    private boolean canSuppressHello() {
        long currentTime = System.currentTimeMillis();
        long lastDataPktSent = 
            tramblk.getOutputDispThread().getLastPktSentTime();
        long helloInterval = Math.max((long) 3000, tramblk.getAckInterval());

        // The following test will fail under rollover condition!!!

        if ((currentTime - lastDataPktSent) > (helloInterval / 2)) {
            return false;
        } 

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CNTLMESG)) {

            logger.putPacketln(this, 
                "Recommending to suppress HELLO " + 
		"Lastsent within " + 
		(currentTime - lastDataPktSent));
	}

        return true;
    }

    /**
     * Private method to validate a head's liveliness. The head can
     * be either an affiliated head or a re-affiliated head.
     * @param hb the head block of the head that is to be validated.
     * @param reAffiliatedHead -- <code>true</code> implies the headBlock
     * is that of the reAffilaited head.
     * <code>false</code> implies that the head
     * is the main head.
     */
    private void validateHeadsLiveliness(HeadBlock hb, 
                                         boolean reAffiliatedHead) {
        TRAMTransportProfile tp = tramblk.getTransportProfile();
        long helloInterval = Math.max((long) 3000, tramblk.getAckInterval());
        long timeSinceLastHeard = System.currentTimeMillis() 
                                  - hb.getLastheard();

        byte tramState = tramblk.getTRAMState();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
	        "HeadsLiveliness:  helloInterval " + helloInterval +
	        " lastHeard " + timeSinceLastHeard + 
	        " state " + tramState);
	}

        if (((tramState == TRAM_STATE.ATTAINED_MEMBERSHIP) || 
	    (tramState == TRAM_STATE.REAFFILIATED)) && 
	    (timeSinceLastHeard > (2 * helloInterval))) {

            if (reAffiliatedHead == true) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

                    logger.putPacketln(this, 
		       "HELLOs are NOT being RECEIVED from ReAffil Head");
		}
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

                    logger.putPacketln(this, 
		        "HELLOs are NOT being RECEIVED");
		}
            }
            if (hb.incrAndGetMissedHellos() > tp.getMaxHelloMisses()) {

                // start the re-affiliation process.

                if (reAffiliatedHead == true) {
                    tramblk.getGroupMgmtThread().handleReAffiliatedHeadLoss();
                } else {
                    tramblk.getGroupMgmtThread().handleHeadLoss();
                }
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

                    logger.putPacketln(this, 
                        "Reporting Hellos are not being received");
		}

                /*
                 * Force an ACK with the Flag set to Not receiving
                 * Hellos
                 */
                TRAMMemberAck mack = tramblk.getMemberAck();

                if (mack != null) {
                    mack.sendAck((byte) TRAMAckPacket.FLAGBIT_HELLO_NOT_RECVD, 
                                 hb, 5);
                }
            }
        } else {
            hb.clearMissedHellos();
        }
    }

    /**
     * private method to test if SeqNumber info needs to be included
     * in the Hello message.
     */
    private synchronized boolean isSendLowestAvailablePktInfo() {
        return sendLowestAvailablePktInfo;
    }

    /**
     * private method to test if SeqNumber info needs to be included
     * in the Hello message.
     */
    private synchronized void setSendLowestAvailablePktInfo(boolean value) {
        sendLowestAvailablePktInfo = value;
    }

    public void sendDemandAck() {
        GroupMgmtBlk gb = tramblk.getGroupMgmtBlk();
        InetAddress addr[] = new InetAddress[gb.getDirectMemberCount()];

        for (int i = 0; i < addr.length; i++) {
            addr[i] = gb.getMember(i).getAddress();
        }

        int lastPkt = tramblk.getTRAMDataCache().getHighestSequenceNumber();
        TRAMHelloPacket pk = new TRAMHelloPacket(
					      tramblk, gb.getRetransmitTTL(), 
                                               addr.length, lastPkt, addr);

        if (tramblk.isDataTransmissionComplete()) {
            pk.setFlags((byte) (TRAMHelloPacket.FLAGBIT_ACK 
                                | TRAMHelloPacket.FLAGBIT_TXDONE));
            pk.setHighSeqNumber(tramblk.getLastKnownSequenceNumber());
        } else {
            pk.setFlags((byte) TRAMHelloPacket.FLAGBIT_ACK);
        }

        if (dispatchHelloPacket(pk.createDatagramPacket())) {
	    try {
	        tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	    } catch (NullPointerException e) {}
	}
    }

    public void sendSimpleHello() {
        GroupMgmtBlk gb = tramblk.getGroupMgmtBlk();

        int lastPkt = tramblk.getTRAMDataCache().getHighestSequenceNumber();
        TRAMHelloPacket pk = new 
	    TRAMHelloPacket(tramblk, gb.getRetransmitTTL(), 
                                               0, lastPkt, null);

        if (dispatchHelloPacket(pk.createDatagramPacket())) {
	    try {
	        tramblk.getTRAMStats().setSendCntlMsgCounters(pk);
	    } catch (NullPointerException e) {}
	}
    }

}

