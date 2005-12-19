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
 * GroupMgmtThread.java
 *
 * Module Description: 
 *
 * GroupMgmtThread is a daemon thread. Basically this
 * thread is responsible for
 * 1. Dequeuing and processing of control messages
 * queued up by the input dispatcher.
 * 2. Initiating and maintaining the Group membership
 * process, That is
 * a. Starting WTBM or sender beacon messages.
 * b. Processing and dispatching of WTBH
 * messages.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import java.util.*;
import java.io.*;
import com.sun.multicast.util.UnsupportedException;

class GroupMgmtThread extends Thread implements TRAMDataPacketListener, 
        BeaconPacketListener, TRAMHelloPacketListener, TRAMAckPacketListener, 
        TRAMHelloUniPacketListener, TRAMMsPacketListener, TRAMHaPacketListener, 
        TRAMAmPacketListener, TRAMRmPacketListener, TRAMHbPacketListener, 
        TRAMTimerEventHandler {

    /*
     * Constants to indicate which timer is currently loaded.
     */
    private static final byte FREE = 0;
    private static final byte HA_POLLING = 1;
    private static final byte MS_XMIT = 2;
    private static final byte MAX_HB_RETXMIT = 3;
    private static final long HB_TIMEOUT = 2000;        // 2secs Timeout
    private TRAMControlBlock tramblk = null;
    private TRAMLogger logger;
    private static final String name = "TRAM GroupMgmtThread";
    private Vector pktsToProcess = null;   /* incoming pkts to process */
    private GroupMgmtBlk gblk    = null;
    private Vector toBeMembers = null;
    private HeadBlock toBeHead = null;
    private boolean done = false;
    private int haPacketsToProcess = 0;
    private int msPacketsToProcess = 0;

    /* 
     * indicates whether or not we think we are the best lan 
     * volunteer so far 
     */

    private boolean weAreBestLanVolunteer = false;
    private boolean weAreCurrentLanHead = false;

    /* indicates whether or not we are electing an additional lan head */

    private boolean needNewLanHead = false;

    /* 
     * indicates whether or not we have heard about a root lan head 
     * (rxlevel = 0) 
     */

    private boolean rootLanHeadExists = false;

    /* 
     * indicates whether or not the root lan head is affiliated to the rest 
     * of the tree 
     */

    private boolean rootLanHeadIsAffiliated = false;
    private Vector backUpHeads = new Vector(10, 10);
    private TRAMTimer timer = null;
    private byte timerState = FREE;
    private byte hbReTxmitCount = 0;
    private MulticastSocket ms = null;
    private int haTTL = 0;
    private short haInterval = 1000; // start with the minimum of .5 seconds
    private int haTimeoutCount = 0;
    private int msTTL = 0;
    private InetAddress myAddr = null;
    private int haIncrementSuppressionCounter = 0;
    private Vector membershipListeners;
    private boolean dataTxmStarted = false;
    /*
     * The following variable is the count of ALL the advertising
     * heads in the system including the sender.
     * This variable is used ONLY by the sender.
     */
    private int advertisingHeads = 1;       // to account the sender.

    /*
     * The following variable holds the sum total count of ALL the heads
     * that are advertising as reported by the head's direct members BUT
     * does not include the count of head's direct members that are
     * advertising.
     * This variable is used by all the heads in the system including
     * sender.
     */
    private int indirectAdvertisingHeads = 0;
        
    /*
     * Static tree config variables
     */
    private String cfgfile = null;
    private boolean staticConfig = false;
    private boolean iAmStaticHead = false;
    private Vector staticHeads = new Vector();
    private int sessionUPort = 0;  

    /**
     * Constructor.
     */
    public GroupMgmtThread(TRAMControlBlock tramblk) {
        super(name);

        this.tramblk = tramblk;
        this.logger = tramblk.getLogger();
        gblk = tramblk.getGroupMgmtBlk();
        pktsToProcess = new Vector(100, 10);
        membershipListeners = new Vector();

        if (tramblk.getTransportProfile().getMrole() != MROLE.MEMBER_ONLY) {
            toBeMembers = new Vector(10, 10);
        }

        timer = new TRAMTimer("GroupMgmtThread Timer", this, logger);

        this.setDaemon(true);

        /*
         * NOTE :
         * This   is a temporary fix and will remain as long as the
         * multicast socket send() with TTL bug exists.
         * When the bug is fixed the following try and catch needs
         * to be removed.
         */
        if (tramblk.getSimulator() == null) {
            try {
                ms = tramblk.newMulticastSocket();
            } catch (IOException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC)) {
                    logger.putPacketln(this, 
			"Unable to open Multicast socket");
		}
            }
        }

        // set up our address for comparisons

        myAddr = tramblk.getLocalHost();

	cfgfile = "jrmstree.cfg." + myAddr.getHostName();

        this.start();
    }

    /*
     * Run method
     */

    public void run() {

        /*
         * Based on the configuration in the Transport profile,
         * perform appropriate operations - like starting the
         * beacons or listening for a beacon etc.,.
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
		"Starting group management thread");
	}

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        switch (tp.getTmode()) {
        case TMODE.SEND_ONLY: 
        case TMODE.SEND_RECEIVE: 

            /*
             * Need to change the TRAM state to PRE_DATA_BEACON and
             * take appropriate steps to start the beacon process.
             */
            tramblk.setTRAMState(TRAM_STATE.PRE_DATA_BEACON);
            tramblk.getInputDispThread().addTRAMMsPacketListener(this);
            tramblk.getInputDispThread().addTRAMHaPacketListener(this);
            tramblk.getUcastInputDispThread().addTRAMHbPacketListener(this);
            tramblk.getUcastInputDispThread().addTRAMAckPacketListener(this);

            /*
             * Initialize the RxLevel. level1 is reserved for the sender.
             * levels 2 thru 255 are valid levels for non senders.
             * level 0 indicates Unknown level.
             * Since this is a sender, set the RxLevel to be 1
             */
            gblk.setRxLevel(1);
            gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);

            TRAMHeadAck hack = new TRAMHeadAck(tramblk);

            tramblk.setHeadAck(hack);

            // Spawn off BeaconGenThread to start generation of beacons.

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "Spawning the Beacon Gen Thread");
	    }

            BeaconGenThread beacon_thrd = new BeaconGenThread(tramblk, this);

            tramblk.setBeaconGenThread(beacon_thrd);

            try {
                InetAddress tmpSrcAddr = tramblk.getLocalHost();

                tp.setDataSourceAddress(tmpSrcAddr);
                tramblk.getTRAMStats().addSender(tmpSrcAddr);
            } catch (NullPointerException ne) {}

            // senders always think they are the best
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this,
		    "LAN flag is " + tp.isLanTreeFormationEnabled()); 
	    }
			       
            if (tp.isLanTreeFormationEnabled()) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"LAN Tree Formation is Enabled");
		}
                weAreBestLanVolunteer = true;

                gblk.setLstate(LSTATE.LAN_HEAD);

                weAreCurrentLanHead = true;
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"Can accept member max of " + tp.getMaxMembers());
		}
            }

            /* Start the timer to dispatch the HA messages */

            /* for now, just use what's in the transport profile */

            if (tp.getTreeFormationPreference(true) 
                    == TRAMTransportProfile.TREE_FORM_MTHA) {
                haInterval = 0;
            } else {
                haInterval = (short) tp.getHaInterval();

                loadTimerToSendHaPkt();
            }

            haTTL = tp.getHaTTLIncrements();

            break;

        case TMODE.RECEIVE_ONLY:
        
            /**
             * Read static tree configuration file, it there is one.
             */
             sessionUPort = tp.getUnicastPort();
            if (tp.getTreeFormationPreference(false)
            	>= TRAMTransportProfile.TREE_FORM_STATIC_R) {
                readTreeConfigFile();
            }

            /*
             * Need to wait for beacon messages to start the membership
             * process.
             */
            tramblk.setTRAMState(TRAM_STATE.AWAITING_BEACON);

            /*
             * Initialize the RxLevel. level1 is reserved for the sender.
             * levels 2 thru 255 are valid levels for non senders.
             * level 0 indicates Unknown level.
             * Since this is a sender, set the RxLevel to be 1
             */
            gblk.setRxLevel(0);

            if (tp.getMrole() != MROLE.MEMBER_ONLY) {
                gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);
            } 

            // register to receive beacon messages.

            tramblk.getInputDispThread().addBeaconPacketListener(this);

            // register to receive Data message.

            tramblk.getInputDispThread().addTRAMDataPacketListener(this);

            // register to receive Hello mesages

            tramblk.getInputDispThread().addTRAMHelloPacketListener(this);
            tramblk.getInputDispThread().addTRAMHaPacketListener(this);
            tramblk.getUcastInputDispThread().addTRAMHbPacketListener(this);
            tramblk.getUcastInputDispThread().addTRAMAmPacketListener(this);
            tramblk.getUcastInputDispThread().addTRAMRmPacketListener(this);
            tramblk.getUcastInputDispThread().
		addTRAMHelloUniPacketListener(this);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_SESSION)) {

                logger.putPacketln(this, 
		    "Waiting  for Beacon/data message");
	    }

            msTTL = tp.getMsTTLIncrements();

            break;

        default: 

            // throw new TRAMInvalidConfigException("Invalid TMode");

            break;
        }

        // Main loop.

        while (!done) {

            if (pktsToProcess.size() != 0) {
                while (true) {
                    try {
                        TRAMPacket pkt = 
                            (TRAMPacket) pktsToProcess.firstElement();

                        // Process based on the message type.

                        processIncomingPacket(pkt);
                        pktsToProcess.removeElement(pkt);
                    } catch (NoSuchElementException e) {
                        break;
                    }
                }
            }

            /*
             * Do Some housekeeping operations like monitoring who is not
             * acking, who's RTT needs to be computed etc.
             * Do the suspend() operation if the performing the role of a
             * member only. If performing the role of a head, use
             * sleep() to wakeup periodically to perform the housekeeping
             * operation.
             */
            stall();
        }
    }
    
    /**
     * readTreeConfigFile - private method to read static tree building
     * config file, if it exists.  And launch tree building based on
     * the parameters read from the config file.
     */
    private void readTreeConfigFile() {
        Reader in = null;
        int c;
        int count = 0;
        int pos = 0;
        InetAddress ia = null;
        byte ttl = 1;
        HeadBlock hb = null;
        if (cfgfile != null) {
            try {
                in = new BufferedReader(new FileReader(cfgfile));

		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                    logger.putPacketln(this, "Reading tree config file " +
			cfgfile);
		}
            } catch (FileNotFoundException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
		    logger.putPacketln(this, "Tree Config file " +
			cfgfile + " not found");
		}
		return;
	    }
	    try {
	        StreamTokenizer st = new StreamTokenizer(in);
	        /**
	         * each line has two entries separated by space
	         * first entry is a net address, second a ttl value
	         * e.g. 129.146.59.74 3
	         * when the ttl is 0, it is not a potential head, but
	         * the addr should be the local address, if so, local
	         * host should be static head.
	         * net addr in a.b.c.d format, treat digits as part of word.
	         **/
	        st.ordinaryChars('.', '9');
	        st.wordChars('.', '9');
	        st.eolIsSignificant(true);
	        c = st.nextToken();
	        while (c != st.TT_EOF) {
	            if (st.ttype == st.TT_EOL) {
	                if (pos == 2) {
	                    if (ttl != 0) {
				if (logger.requiresLogging(
				    TRAMLogger.LOG_ANY)) {

				    logger.putPacketln(this, 
					"Setting static head to " +
					ia + " ttl is " + ttl);
				}
	                        hb = new HeadBlock(ia, sessionUPort);
	                        hb.setTTL(ttl);
	                        // only take the first 10, arbitrary
	                        if (count < 10) {
	                            staticHeads.addElement(hb);
	                            staticConfig = true;
	                        }	                    
			    } else {  // ttl = 0
			        if (myAddr.equals(ia) == true) {
			            iAmStaticHead = true;
			        }
			    }        			    
	                } else if (pos > 0) {
			    // ignore bad record
	                } else if (pos == 0) {
	                    // break if blank line
	                    break;
	                }
	                pos = 0;
	            } else {
	                // need to add some type checking later
	                if (pos == 0) {
	                    ia = InetAddress.getByName(st.sval);
	                    count++;
	                } else if (pos == 1) {
	                    ttl = (byte) Integer.parseInt(st.sval);
	                }
	                pos++;
	            }
	            c = st.nextToken();	               
	        }
	        in.close();
	    } catch (IOException e) {
	    } 
	}
    }
    
    /**
     * Check if given inetAddress is one of those on static head list.
     * If not, return false.
     */
    private boolean checkAgainstStaticList(InetAddress ia) {
        HeadBlock hb = null;
        for (int i = 0; i < staticHeads.size(); i++) {
            try {
                hb = (HeadBlock) staticHeads.elementAt(i);

	        if (hb.getAddress().equals(ia) == true) {
		    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
		        logger.putPacketln(this, 
			    "Received message from static head " + ia);
			   
		    }
                    return true;
		}
	    } catch (IndexOutOfBoundsException ie) {
	    }
        }
        return false;
    }

    /**
     * processIncomingPacket - private method to process the incoming
     * control or data message to perform appropriate Group Management
     * operations.
     * 
     * @param TRAMPacket - the incoming packet that needs to be processed.
     */
    private void processIncomingPacket(TRAMPacket pkt) {
        int t = pkt.getSubType();
	TRAMTransportProfile tp = tramblk.getTransportProfile();
	TRAMStats statBlk = tramblk.getTRAMStats();

	if (statBlk != null)
	    statBlk.setRcvdCntlMsgCounters(pkt);

        switch (pkt.getMessageType()) {
        case MESGTYPE.MCAST_CNTL: 
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "Incoming pkt from " + pkt.getAddress() + " " + 
		    pkt.getPort() + ", type MCAST_CNTL, subtype " + 
		    SUBMESGTYPE.mcastControl[t]);
	    }

            switch (t) {
            case SUBMESGTYPE.BEACON: 
                processBeaconPacket((BeaconPacket) pkt);

                break;

            case SUBMESGTYPE.MS: 

		decrementMsPacketsToProcess();
                processMsPacket((TRAMMsPacket) pkt);

                break;

            case SUBMESGTYPE.HELLO: 
                processHelloPacket((TRAMHelloPacket) pkt);

                break;

            case SUBMESGTYPE.HA: 
		decrementHaPacketsToProcess();
                processHaPacket((TRAMHaPacket) pkt);

                break;

            default: 
                return;
            }

            break;

        case MESGTYPE.MCAST_DATA: 
	    if (tp.getTmode() != TMODE.SEND_ONLY) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_SESSION)) {

		    logger.putPacketln(this, 
			"Incoming pkt from " + pkt.getAddress() +
			" " + pkt.getPort() +
			", type MCAST_DATA, subtype " +
			SUBMESGTYPE.mcastData[t]);
		}
	    }

            processDataPacket((TRAMDataPacket) pkt);

            break;

        case MESGTYPE.UCAST_CNTL: 
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_SESSION)) {

	        logger.putPacketln(this, 
		    "Incoming pkt from " + pkt.getAddress() +
		    " " + pkt.getPort() +
		    ", type UCAST_CNTL, subtype " +
		    SUBMESGTYPE.ucastControl[t] + "");
	    }

            switch (t) {
            case SUBMESGTYPE.AM: 
                processAmPacket((TRAMAmPacket) pkt);

                break;

            case SUBMESGTYPE.RM: 
                processRmPacket((TRAMRmPacket) pkt);

                break;

            case SUBMESGTYPE.HB: 
                processHbPacket((TRAMHbPacket) pkt);

                break;

            case SUBMESGTYPE.ACK: 
                processAckPacket((TRAMAckPacket) pkt);

                break;

            case SUBMESGTYPE.HELLO_Uni: 
                processHelloUniPacket((TRAMHelloUniPacket) pkt);

                break;

            default: 
                return;
            }

            break;

        default: 
            return;
        }
    }

    /**
     * processBeaconPacket - private method to process the incoming Beacon
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param BeaconPacket - the incoming Beacon packet that needs to be
     * processed.
     */
    private void processBeaconPacket(BeaconPacket pkt) {
        TRAMTransportProfile tp = tramblk.getTransportProfile();

        if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
            haInterval = pkt.getHaInterval();
        } 

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
	        "Got Beacon... state " + 
		TRAM_STATE.TRAMStateNames[tramblk.getTRAMState()] + 
		" msttl " + msTTL + " haTTL " + haTTL);
	}

        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.AWAITING_BEACON: 

            // Validate beacon message to verify the data source.

            if ((isAPacketFromTheDataSource(pkt) == false)) {
                return;
            } 
            if ((tramblk.getTransportProfile().
		getMrole() != MROLE.MEMBER_ONLY) && 
		(tramblk.getTransportProfile().
		isLanTreeFormationEnabled())) {

                // start out thinking we are the best
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"LAN Tree Formation is Enabled. Volunteering.");
		}
                weAreBestLanVolunteer = true;

                gblk.setLstate(LSTATE.VOLUNTEERING);
            }

            /*
             * decide whether to start MTHA or HA
             */
            if (!tryStartingMTHA(tp)) {
                // start HA instead

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_SESSION)) {

                    logger.putPacketln(this, 
			"Changing state to SEEKING_HA_MEMBERSHIP");
		}

                tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);
                loadTimerToReceiveHaPacket();
            }

            break;

        case TRAM_STATE.ATTAINED_MEMBERSHIP: 

            /*
             * Checkout if the node is affiliated to the Sender. If
             * so No hellos will be coming and as a result, the
             * head's lastHeard needs to be updated.
             */
            HeadBlock hb = gblk.getHeadBlock();
	    tramblk.setLastKnownSequenceNumber(pkt.getSeqNumber());
            if ((hb.getAddress().equals(pkt.getAddress()) == true) 
                    && ((tramblk.getSimulator() == null) 
                        || (hb.getPort() == 4322))) {
                hb.setLastheard(System.currentTimeMillis());
            }

            /*
             * Checkout if the beacon is related to congestion & to carry
             * appropriate actions.
             */
            break;

        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 

            /*
             * decide whether to start MTHA
             */
            tryStartingMTHA(tp);

            break;

        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 

            /* Check for switch to HA */

            if (haInterval != 0) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Changing state to SEEKING_HA_MEMBERSHIP");
		}
                tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);
                loadTimerToReceiveHaPacket();
            }

            break;

        // All other cases ignore.

        default: 
            break;
        }
    }

    /**
     * private method to update the HAI, then decide whether or not to
     * start MTHA
     */
    private boolean tryStartingMTHA(TRAMTransportProfile tp) {
        if (haInterval == 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Transitioning to MTHA Membership");
	    }
            tramblk.setTRAMState(TRAM_STATE.SEEKING_MTHA_MEMBERSHIP);

            if (weAreBestLanVolunteer == true) {

                // give us a little time to do lan tree formation

                lanVolunteer();
                timer.reloadTimer(
		 (long) ((tramblk.getTransportProfile().getMsRate()) & 
			 0xffffffffL) / 2);
            } else {
                sendMsPacket();
                timer.reloadTimer((long) (
		      tramblk.getTransportProfile().getMsRate()) & 
				  0xffffffffL);
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * private method to initiate the HA Timer operation.
     * This method just starts the Ha timer.
     */
    private void loadTimerToSendHaPkt() {

        // Start the HA Timer.

        if (haInterval != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "Starting the HA Timer");
	    }
            timer.loadTimer(((long) (haInterval)) & 0xffffffffL);
        } else if (gblk.getLstate() != 0) {

            /* keep doing lan tree formation even if HA interval is 0 */

            timer.loadTimer(tramblk.getTransportProfile().getHaInterval());
        } 
    }

    /**
     * private method to load HA timer to receive HA messages.
     * This method just starts the Ha timer.
     */
    private void loadTimerToReceiveHaPacket() {

        // Start the HA Timer.

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Starting Timer to Receive HA Pkt");
	}

        long timerValue = ((long) (haInterval)) & 0xffffffffL;

        /* Make sure that the receiver waits for no more than 60 secs. */

        if (timerValue > 20000) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
                   "HA Wait time is more than 60secs. Adjusting to 60 secs");
	    }

            timerValue = 20000;
        }

        timer.loadTimer(timerValue);

        if (haTimeoutCount == 0) {
            haTimeoutCount = 3;     // wait for three intervals
        } 
    }

    /**
     * processMsPacket - private method to process the incoming Ms
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMMsPacket - the incoming Ms packet that needs to be
     * processed.
     */
    private void processMsPacket(TRAMMsPacket pkt) {
        TRAMTransportProfile tp = tramblk.getTransportProfile();
        byte ttl = 0;

        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.PRE_DATA_BEACON: 

            /* only look at the MS if we're the sender */

            if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Ignoring a Pre Data MS message");
		}

                return;
            }

            break;

        case TRAM_STATE.ATTAINED_MEMBERSHIP: 

            /*
             * Received some other node's MS message.
             * Check if the MROLE allows receiver to become a head. If
             * so attempt to become one by sending HA message. If
             * already performing the role of a head and if additional
             * members can be accepted, dispatch a HA message. For
             * any other condition just ignore the message.
             * 
             * If the MROLE does not permit or if there are no free slots
             * available to accomodate a member, the Ms message is ignored.
             * 
             */

            // fall thru for further processesing.

            break;

        case TRAM_STATE.DATA_TXM: 

        case TRAM_STATE.CONGESTION_IN_EFFECT: 

            /*
             * by default can accept members hence fall thru for further
             * processesing.
             */
            break;

        // All other cases  - Ignore the Ms message.

        default: 
            return;
        }

        /*
         * If the head is resigning or not accepting members,
         * ignore the MS message
         */
        if ((gblk.getHstate() == HSTATE.RESIGNING) 
                || (gblk.getHstate() == HSTATE.NOT_ACCEPTING_MEMBERS) 
                || (tp.getMrole() == MROLE.MEMBER_ONLY) 
                || ((pkt.getMrole() == MROLE.MEMBER_ONLY) 
                    && (gblk.getHstate() 
                        == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY))) {

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
                    "Ignoring MS message as HSTATE is " 
                    + gblk.getHstate() + " and mRole is " 
                    + tp.getMrole());
	    }

            return;
        }
	
	/*
	 * If LANTree mode is enabled, then respond with an HA message
	 * ONLY if we are a LANHead. if not ignore the message.
	 * Verify the addition of the following test. XXX
	 */
	if ((tramblk.getTransportProfile().isLanTreeFormationEnabled() == 
	    true) && (weAreCurrentLanHead == false)) {

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
		   "Not a LANHead. Ignoring the received MS message");
	    }
	    return;
	}
	
        if ((pkt.getMrole() == MROLE.MEMBER_ONLY) 
	    && (gblk.getMemberOnlyCount() >= tp.getMaxNonHeads())) {
            return;
        }
	
        /*
         * Compute the maximum members that can be accomodated.
         * This should take into account the 'to_be-members' that
         * have been sent a HA in the past.
         */
        if (gblk.getDirectMemberCount() < tp.getMaxMembers()) {

            /*
             * Yes we can accomodate a member!. Build and send a
             * HA message and add the member to the 'toBeMembers'
             * list. Upon receiving an HB message from the member,
             * the member will be removed from the to_be_list and
             * added to the member list.
             * What TTL to use to dispatch the HA message? One
             * option is determine the TTL from the incoming message.
             * Since the actual TTL cannot be computed at this
             * time(limitation as a result of using Java), the TTL
             * Original TTL loaded by the source of the MS message
             * is used to dispatch the HA message.
             */
            sendHaPacket(pkt.getTTL());

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Got MS Packet from " + pkt.getAddress().getHostName() + 
		    "Port " + pkt.getUnicastPort() + 
		    "TTL as " + pkt.getTTL());
	    }
        }
    }

    /**
     * processHelloPacket - private method to process the incoming Hello
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMHelloPacket - the incoming Hello packet that needs to be
     * processed.
     */
    private void processHelloPacket(TRAMHelloPacket pkt) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CNTLMESG)) {

            logger.putPacketln(this, 
	        "Hello from " + pkt.getAddress() + " " + pkt.getUnicastPort());
	}

        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.ATTAINED_MEMBERSHIP: 
        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 
        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 
        case TRAM_STATE.HEAD_BINDING: 
        case TRAM_STATE.REAFFIL_HEAD_BINDING: 
        case TRAM_STATE.SEEKING_REAFFIL_HEAD: 
        case TRAM_STATE.REAFFILIATED: 

            /*
             * Avoid Processing my Own Hello messages.
             */
            if ((myAddr.equals(pkt.getAddress()) == true) 
                    && (tramblk.getUnicastPort() == pkt.getUnicastPort())) {
                return;
            } 

            break;

        default: 
            return;
        }

        /*
         * Check if the Hello message is from the dependent head.
         * If so update the time stamp. Check if an ACK needs to
         * be sent to compute RTT.
         * If the Hello is from a different head, then check if it
         * is accepting members and add it to the back up heads
         * list.
         */
        HeadBlock headBlk = gblk.getHeadBlock();

        if ((headBlk != null) && 
	    (headBlk.getAddress().equals(pkt.getAddress()) == true) && 
	    (headBlk.getPort() == pkt.getUnicastPort())) {

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this,
	            "My Head's address is " + headBlk.getAddress() +
                    " " + headBlk.getPort());
	    }

            processHeadsHelloPacket(pkt, headBlk, false);
        } else {

            /*
             * check if the Hello is from the re-affiliated head. If so
             * send an ACK.
             */
            if ((tramblk.getTRAMState() == TRAM_STATE.REAFFILIATED) && 
		(toBeHead != null) && (toBeHead.getAddress().
		equals(pkt.getAddress()) == true) && 
		(toBeHead.getPort() == pkt.getUnicastPort())) {

                // Hello from the re-affiliated head.

                processHeadsHelloPacket(pkt, toBeHead, true);
            } else {

                // The hello message must be from a neighboring head.

                processOtherHeadsHelloPacket(pkt);
            }
        }
    }

    /**
     * private method to process Hello packet from the affiliated head.
     */
    private void processHeadsHelloPacket(TRAMHelloPacket pkt, 
                                         HeadBlock headBlk, 
                                         boolean reAffiliatedHead) {

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CNTLMESG)) {

            logger.putPacketln(this, 
	        "Received Hello from head " + headBlk.getAddress() + 
	        " " + pkt.getUnicastPort());
	}

	boolean dispatchAnAck = false;
        headBlk.setLastheard(System.currentTimeMillis());

        /*
         * Currently Since we cannot get the exact TTL, the TTL
         * is updated ONLy if the new TTL is lower than the
         * earlier reported TTL (also could be that the earlier
         * stored TTL is based on the TTL value extracted from
         * a HA message..... in which case the HA reported TTL
         * would obviously be larger than the Hello TTL.)
         */
        byte newTTL = pkt.getTTL();

        if (newTTL < headBlk.getTTL()) {
            headBlk.setTTL(newTTL);
        } 
        if (headBlk.getHstate() != pkt.getHstate()) {
            headBlk.setHstate(pkt.getHstate());

            /*
             * Check if the head is resigning and take
             * appropriate action i.e., initialize
             * re-affiliation. Don't go thru the following
             * if already attempting to re-affiliate.
             */
            if ((pkt.getHstate() == HSTATE.RESIGNING) 
                    && (tramblk.getTRAMState() 
                        == TRAM_STATE.ATTAINED_MEMBERSHIP)) {

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    	    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

            	    logger.putPacketln(this, 
			"Head RESIGNING " + headBlk.getAddress());
		}

                tramblk.setTRAMState(TRAM_STATE.SEEKING_REAFFIL_HEAD);
                performHeadSelection();

                switch (tramblk.getTRAMState()) {
                case TRAM_STATE.SEEKING_REAFFIL_HEAD: 
                    if (haInterval == 0) {
                        sendMsPacket();
                        timer.reloadTimer((long) 
			 (tramblk.getTransportProfile().getMsRate()) 
					  & 0xffffffffL);
                    } else {
                        loadTimerToReceiveHaPacket();
                    }
                    break;

                case TRAM_STATE.REAFFIL_HEAD_BINDING: 
                    timer.reloadTimer(HB_TIMEOUT);

                    break;
                }
            }
        }


	if (headBlk.getRxLevel() != pkt.getRxLevel()) {
	    headBlk.setRxLevel(pkt.getRxLevel());
	    
	    if (reAffiliatedHead == false) {
		/* 
		 * What should be done if the RxLevel is found to be
		 * higher than this node's RxLevel? possibility
		 * of a loop? 
		 */
		gblk.setRxLevel(pkt.getRxLevel() + 1);
		
		/* 
		 * if they're changing our level, the root lan head 
		 *  must be affiliated 
		 */
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, "Root LAN is affiliated");
		}
		rootLanHeadIsAffiliated = true;
	    }
	}
   
	if (reAffiliatedHead == false) {
	    /*
	     * Make the Member Ack module deal with the highest and
	     * Lowest sequence number reported in the Hello packet.
	     * (Typically if the Hello is reporting of a higher
	     * packet number than what the receiver is currently
	     * thinks). The following operations do not cause an
	     * ACK to be sent and should not be carried out if the
	     * hello is from a re-affiliated head.
	     */
	    TRAMMemberAck mack = tramblk.getMemberAck();
	    
	    if (mack != null) {
		mack.dealWithUnrecoverablePkts(pkt.getLowSeqNumber());
		/*
		 * Now Check if the Highseq# is being reported for the
		 * first time.
		 */

	    }
	    tramblk.getTRAMDataCache().dealWithUnrecoverablePkts(
		pkt.getLowSeqNumber());
	}

        /*
         * Check if an ACK is being demanded? if so set the dispatchAnAck
	 * flag to dispatch an ACK at the end of this method.
         */
        if ((pkt.getFlags() & ((int) (TRAMHelloPacket.FLAGBIT_ACK))) != 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	        logger.putPacketln(this, "Demand ACK from " + pkt.getAddress() +
		    " " + pkt.getAckMemberCount() + " addrs " +
		    "check for me " + tramblk.getLocalHost());
	    }

            /*
             * first findout if this receiver is listed among the
             * members to send ACK.
             */
            InetAddress[] ia = pkt.getAddressList();
	    InetAddress myAddress = tramblk.getLocalHost();

	    if (myAddress != null) {
		for (int i = 0; i < pkt.getAckMemberCount(); i++) {
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	    	        logger.putPacketln(this, 
		            "my address " + myAddress +
		            ", address in hello pkt " + ia[i]);
		    }

                    if (ia[i].equals(myAddress) == true) {
			if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                            logger.putPacketln(this, 
				"Need to respond to Hello");
			}

                        TRAMMemberAck mack = tramblk.getMemberAck();

			/*
			 * Answer to Hello ONLY if re-affiliated or
			 * in attained membership State.
			 */
                        if (mack != null) {
			    if ((tramblk.getTRAMState() ==
				 TRAM_STATE.REAFFILIATED) ||
			         (tramblk.getTRAMState() ==
				  TRAM_STATE.ATTAINED_MEMBERSHIP)) {

			        if (logger.requiresLogging(
				    TRAMLogger.LOG_CONG)) {
                                    logger.putPacketln(this, 
					"Dispatching ACK...");
				}

				dispatchAnAck = true;
				// mack.sendAck((byte) 0, headBlk);
				if (reAffiliatedHead == true) {
				    /* 
				     * ONLY print the message if responding
				     * to a re-affiliated Head
				     */
				    if (logger.requiresLogging(
					TRAMLogger.LOG_CONG)) {

				    	logger.putPacketln(this, 
				            "Ack'ing Hello from " +
					    " ReAffiliated Head" + 
					    headBlk.getAddress());
				    }
				}
			    }
                            break;
                        }
                    }
		}
	    }
	}
	TRAMMemberAck memack = tramblk.getMemberAck();
	if (memack != null) {
	    if ((pkt.getFlags() & ((int) 
				   (TRAMHelloPacket.FLAGBIT_TXDONE))) != 0) {
		/*
		 * An ACK will be dispatched in the following routine...
		 * Hence exit after the function call.
		 */
		memack.handleDataTransmissionComplete(headBlk, 
                                                      pkt.getHighSeqNumber());
		return;
	    } 
	    /* 
	     * Now Check if a hello is required to be sent out based on the
	     * earlier checks performed above.
	     */
	    if (dispatchAnAck == true) {
		memack.sendAck((byte) 0, headBlk, 4);
	    }
	}
	
    }

    /**
     * Process Hello from an unaffiliated head.
     */
    private synchronized void processOtherHeadsHelloPacket(
	TRAMHelloPacket pkt) {

        /*
         * The hello is from someother head. Lets add it to the
         * backup list if it is still accepting members.
         * If the head is already in the list and the head
         * is nolonger accepting members, the head needs to
         * be removed from the backup list.
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
		"received Hello from a local head " + 
		pkt.getAddress());
	}
        
        /*
         * If static tree configuration is in effect, check if allowed
         * by configuration
         */
        if (staticConfig) {
            if (!checkAgainstStaticList(pkt.getAddress()))
                return;
        }

        HeadBlock hb = null;
        HeadBlock headBlk = null;
        boolean update = false;

        for (int i = 0; i < backUpHeads.size(); i++) {
            try {
                hb = (HeadBlock) backUpHeads.elementAt(i);

                if ((hb.getAddress().equals(pkt.getAddress()) == true) 
                        && (hb.getPort() == pkt.getUnicastPort())) {
                    if ((isASuitableBackupHstate(pkt.getHstate()) == false) 
                            || ((gblk.getLstate() == LSTATE.LAN_HEAD) 
                                && (hb.getLstate() != 0))) {

                        /* 
			 * for lan heads to be suitable, we need to hear 
			 * the info from the HA 
			 */

			  //  Unsuitable head. remove it 

                        backUpHeads.removeElement(hb);

                        return;
                    }

                    // set the flag to update various fields

                    update = true;

                    break;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }       // for ends here.

        if (update == false) {

            /*
             * Must be a new head's Hello message. Add it to the
             * backup head list
             */
            if ((isASuitableBackupHstate(pkt.getHstate()) == false) 
                    || (gblk.getLstate() == LSTATE.LAN_HEAD)) {

                /* 
		 * for lan heads to be suitable, we need to hear the info 
		 * from the HA 
		 */

                // Unsuitable head IGNORE.

                return;
            }

            /*
             * check if this chosen to be the toBeHead. If so
             * no need to add to the backupHeadList
             */
            if ((toBeHead != null) && (toBeHead.getAddress().
		equals(pkt.getAddress()) == true) && 
		(toBeHead.getPort() == pkt.getUnicastPort())) {

                toBeHead.setLastheard(System.currentTimeMillis());

                /*
                 * Update TTL only if its lower than the stored one.
                 */
                byte newTTL = pkt.getTTL();

                if (newTTL < toBeHead.getTTL()) {
                    toBeHead.setTTL(newTTL);
                } 

                toBeHead.setRxLevel(pkt.getRxLevel());

                return;
            }

            /*
             * Since its a potential head, add to the backupList
             * first and then update the fields.
             */
            hb = new HeadBlock(pkt.getAddress(), pkt.getUnicastPort());

            hb.setTTL(pkt.getTTL());
	    hb.setHstate(pkt.getHstate());
	    hb.setLastheard(System.currentTimeMillis());
	    hb.setRxLevel(pkt.getRxLevel());

	    if (addedToTheBackupHeadList(hb) == false)
		return;

	    // backUpHeads.addElement(hb);
        } 
	else 
        {
	    hb.setHstate(pkt.getHstate());
	    hb.setLastheard(System.currentTimeMillis());
	    hb.setRxLevel(pkt.getRxLevel());
	    /*
	     * Update TTL only if its lower than the stored one.
	     */
	    byte newTTL = pkt.getTTL();
	    
	    if (newTTL < hb.getTTL()) {
		hb.setTTL(newTTL);
	    } 
	}

        headBlk = gblk.getHeadBlock();

        if (headBlk != null) {

            /*
             * Now Check if the backup head is better than the
             * currently affiliated head. If so, initiate the
             * re-afffiliation process.
             */
            if (hb.getTTL() < headBlk.getTTL()) {

                /*
                 * Yes, the member can affiliate with a
                 * closer head.
                 */
                switch (tramblk.getTRAMState()) {
                case TRAM_STATE.REAFFILIATED: 
                case TRAM_STATE.REAFFIL_HEAD_BINDING: 

                    /*
                     * Should we findout if we have a better option?
                     * If so make the member discard the earlier HB
                     * and make this member to be the toBeHead?
                     * ISSUE:... the code prefers to do one thing at
                     * a time - like affiliates with the selected head
                     * eventhough there might be a better head(learnt
                     * about the better head after dispatching the
                     * HB message).
                     */
                    break;

                case TRAM_STATE.ATTAINED_MEMBERSHIP: 

                    /*
                     * Send HB and change the make an reaffilHead
                     * entry and change the TRAM State to
                     * TRAM_STATE.REAFFIL_HEAD_BINDING.
                     */
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                        logger.putPacketln(this, 
			    "ReAffiliating with a new head" +
                            "CurrentHead TTL = " +
                            headBlk.getTTL() + "New Heads TTL = " + 
			    hb.getTTL());
		    }

                    if (hb.getRxLevel() >= gblk.getRxLevel()) {
                        return;
                    } 

                    toBeHead = hb;

                    // remove from the toBeHeadsList 

                    backUpHeads.removeElement(hb);
                    sendHbPacket();

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, 
			    "Reaffiliating with " +
			    hb.getAddress() + hb.getPort());
		    }
                    hbReTxmitCount = 0;

                    tramblk.setTRAMState(TRAM_STATE.REAFFIL_HEAD_BINDING);

                    /*
                     * start timer. In Reaffiliation state
                     * the HAs are suppressed. No new members
                     * are accepted when the head is in the midst
                     * of re-affiliation. Hence the current timer
                     * can be safetly stopped and reloaded with
                     * HEAD_BIND_TIMEOUT value.
                     */
                    timer.reloadTimer(HB_TIMEOUT);

                    break;

                default: 
                    break;
                }
            }
        }
    }

    /**
     * processHbPacket - private method to process the incoming Head
     * Bind packet.
     * 
     * 
     * @param TRAMHbPacket - the incoming Head Bind packet that needs to be
     * processed.
     */
    private void processHbPacket(TRAMHbPacket pkt) {
        TRAMTransportProfile tp = tramblk.getTransportProfile();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Got HB Pkt state " + 
	        TRAM_STATE.TRAMStateNames[tramblk.getTRAMState()] + 
	        " from " + pkt.getAddress() + " " + pkt.getPort() +
	        " msttl " + msTTL + " haTTL " + haTTL);
	}

	boolean lanTree = tp.isLanTreeFormationEnabled();
        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 
        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP:

	    if ((lanTree == false) || 
		(gblk.getLstate() != LSTATE.LAN_HEAD)) {
		return;
	    }
	    // all other cases ... just fall thru no 'break;' here.
        case TRAM_STATE.ATTAINED_MEMBERSHIP: 

            if (tp.getMrole() == MROLE.MEMBER_ONLY || 
		 tramblk.getMemberAck() == null) {

                // Ignore the message.

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, "Ignoring the HB message");
		}

                return;
            }

	    if ((lanTree == true) && (gblk.getLstate() != LSTATE.LAN_HEAD)) {
		/*
		 * send an RM Message
		 */
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"Not a LAN head. Ignoring HB message");
		}
		sendRmPacket(pkt.getAddress(), pkt.getPort(),
			     TRAMRmPacket.RCODE_NOT_A_LANHEAD);
		return;
	    }
            break;

        case TRAM_STATE.CONGESTION_IN_EFFECT: 

        case TRAM_STATE.DATA_TXM: 

        case TRAM_STATE.PRE_DATA_BEACON: 
            break;

        default: 

            // Ignore the message for other states.

            return;
        }

        /*
         * If the membership is initiated with a MS message, then
         * the member block will be in the tobeMemberList. If the
         * node to attempting to become a member in response to
         * hearing a HA message, a new member block is to be
         * created and added to the memberList.
         * If assuming the role of a head for the first time
         * start the Hello thread. Add this member to the list of
         * members who need to respond to the hello messages to compute
         * RTT. If this node happens to be the sender too, then Hello
         * thread need not be spawned as the sender does not do hello.
         */
        MemberBlock mb = null;

        try {
            mb = gblk.getMember(pkt.getAddress(), pkt.getPort());

            mb.setLastheard(System.currentTimeMillis());

	    TRAMSeqNumber s = new TRAMSeqNumber(computeStartPacketForMember());
            mb.setLastPacketAcked(s.getPreviousSeqNumber());

            // Maybe the AM got lost...resend a AM message

            sendAmPacket(pkt.getAddress(), pkt.getPort());

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "Already a member Ignoring");
	    }

            // member is already in the list. Ignore the message.

            return;
        } catch (NoSuchElementException e) {

            /*
             * Find and get the memberblock from the to_be_members list.
             * 
             */
            try {
                mb = getToBeMember(pkt.getAddress(), pkt.getPort());

                /*
                 * found a match. now remove from the toBeMembers
                 * list and add it to the members list.
                 */
                toBeMembers.removeElement(mb);

                // use the TTL reported in the HB message

                mb.setTTL(pkt.getTTL());

	        TRAMSeqNumber s = new 
		    TRAMSeqNumber(computeStartPacketForMember());
                mb.setLastPacketAcked(s.getPreviousSeqNumber());

                sendAmPacket(pkt.getAddress(), pkt.getPort());
            } catch (NoSuchElementException e1) {

                /*
                 * May be HB message in response to HA message and MS
                 * message did not trigger the membership process.
                 * 
                 * First find out if the node can accomodate new
                 * member(s).
                 */

                /*
                 * Compute the maximum members that can be accomodated.
                 * This should take into account the 'to_be-members' that
                 * have been sent a wtbh in the past.
                 */
                if (gblk.getHstate() == HSTATE.RESIGNING) {
                    sendRmPacket(pkt.getAddress(), pkt.getPort(), 
                                 TRAMRmPacket.RCODE_RESIGNING);

                    return;
                }
                if ((pkt.getMrole() == MROLE.MEMBER_ONLY) 
                        && (gblk.getMemberOnlyCount() 
                            >= tp.getMaxNonHeads())) {
                    sendRmPacket(pkt.getAddress(), pkt.getPort(), 
                                 TRAMRmPacket.RCODE_ACCEPTING_POTENTIAL_HEADS);

                    return;
                }
                if (gblk.getDirectMemberCount() < tp.getMaxMembers()) {

                    /*
                     * Yes we can accomodate a member!. Build and send a
                     * AM message or Send an RM message
                     */
                    mb = new MemberBlock(pkt.getAddress(), pkt.getPort());

                    mb.setTTL(pkt.getTTL());

		    TRAMSeqNumber seqNum = 
			new TRAMSeqNumber(computeStartPacketForMember());

                    mb.setLastPacketAcked(seqNum.getPreviousSeqNumber());

                    sendAmPacket(pkt.getAddress(), pkt.getPort());

                    /*
                     * if the member is a potential Head,  Suppress
                     * ha TTL increments for some time so that the
                     * new Head's HA can also start sending out
                     * HA messages.
                     */
                    if (pkt.getMrole() != MROLE.MEMBER_ONLY) {
                        loadHaIncrementSuppressionCounter();
                    } 
                } else {

                    /*
                     * Send an RM packet!
                     */
                    sendRmPacket(pkt.getAddress(), pkt.getPort(), 
                                 TRAMRmPacket.RCODE_MEMBERSHIP_FULL);

                    return;
                }
            }       // Inner catch ends here

            mb.setLastheard(System.currentTimeMillis());
            mb.setMrole(pkt.getMrole());

            /*
             * set the flag to demand ACK from the member. This will
             * compute the rtt for the member.
             */
            // Spawn the HeadAckThread if not already started.

            if (tramblk.getHeadAck() == null) {
                TRAMHeadAck hack = new TRAMHeadAck(tramblk);

                tramblk.setHeadAck(hack);
            }

            /*
             * Spawn the Hello Thrd if not already started.
             */
            if (tramblk.getHelloThread() == null) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, "Spawned the Hello thread");
		}

                HelloThread ht = new HelloThread(tramblk);
            }

            /*
             * Change Head state to AcceptingMembers if it is still
             * in INIT state.
             */
            if (gblk.getHstate() == HSTATE.INIT) {
                gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);
            }

            gblk.putMember(mb);

            if (gblk.getDirectMemberCount() >= tp.getMaxMembers()) {
                gblk.setHstate(HSTATE.NOT_ACCEPTING_MEMBERS);
            } else if ((gblk.getMemberOnlyCount() + 
		      getMembersOnlyInToBeMembers()) >= tp.getMaxNonHeads()) {
                gblk.setHstate(HSTATE.ACCEPTING_POTENTIALHEADS_ONLY);
            } 

            updateRepairTTL();
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
                    "Added a member. Total mem cnt = " + 
		    gblk.getDirectMemberCount() +
                    " Member details : Address " + mb.getAddress() + 
		    " " + mb.getPort());
	    }
        }           // Outer catch ends here.

    }

    /**
     * processDataPacket - private method to process the incoming Data
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMDataPacket - the incoming Data packet that needs to be
     * processed.
     */
    private void processDataPacket(TRAMDataPacket pkt) {

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.AWAITING_BEACON: 
        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 

            // Validate data message to verify the data source.

            if (isAPacketFromTheDataSource(pkt) == false) {
                return;
            } 
            if (pkt.getSubType() == SUBMESGTYPE.DATA) {

                /* save the haInterval from the source */

                if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
                    haInterval = pkt.getHaInterval();
                }
		
                if (!tryStartingMTHA(tp)) {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this, 
                            "Changing state to SEEKING_HA_MEMBERSHIP");
		    }
                    tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);
                    loadTimerToReceiveHaPacket();
                }

                // remove the data packet listener as it is no longer needed.

                tramblk.getInputDispThread().removeTRAMDataPacketListener(this);
		dataTxmStarted = true;
            }

            break;

        // All other cases

        case TRAM_STATE.ATTAINED_MEMBERSHIP: 

        case TRAM_STATE.DATA_TXM: 
            if (pkt.getSubType() == SUBMESGTYPE.DATA) {

                /* save the haInterval from the source */

                haInterval = pkt.getHaInterval();
		dataTxmStarted = true;
            }

        /* fall through */

        default: 

            // remove the data packet listener as it no is longer needed.

            tramblk.getInputDispThread().removeTRAMDataPacketListener(this);

            break;
        }
    }

    /**
     * private method to validate if the multicast packet was sent by
     * the actual/expected data source.
     */
    private boolean isAPacketFromTheDataSource(TRAMPacket pkt) {
        InetAddress src_addr = pkt.getAddress();
        int sessionId = pkt.getSessionId();
        TRAMTransportProfile tp = tramblk.getTransportProfile();

        /*
         * If the data source address is not in the transport profile,
         * we have no choice but to accept the source of this session.
         * Grab the source address and session Id and load them into the
         * appropriate locations.
         */
        if (tp.getDataSourceAddress() == null) {
	    /*
	     * if the packet is a retransmitted packet, ignore it!.
	     * and Don't set the data source address. We SHOULD
	     * probably rely upon the source address listed in the
	     * Transport profile ONLY!!!!!
	     */

	    if ((pkt.getMessageType() == MESGTYPE.MCAST_DATA) &&
		(pkt.getSubType() == SUBMESGTYPE.DATA_RETXM)) {
		return false;
	    }

            tp.setDataSourceAddress(src_addr);
            tramblk.setSessionId(sessionId);
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
		    "Changed the data source address!!!");
	    }

            try {
                TRAMStats statBlk = tramblk.getTRAMStats();
                statBlk.addSender(src_addr);
            } catch (NullPointerException ne) {}
            return true;
        } else {
            /*
             * If the source address in the packet equals the transport
             * profile source address AND the session Id in the packet
             * equals the  one in the TRAM ControlBlock, return true, otherwise
             * return false.
             */
            if ((src_addr.equals(tp.getDataSourceAddress())) 
                    && (sessionId == tramblk.getSessionId())) {
                return true;
            } 
        }

        return false;
    }

    /**
     * processHaPacket - private method to process the incoming Wtbh
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMHaPacket - the incoming HA packet that needs to be
     * processed.
     */
    private void processHaPacket(TRAMHaPacket pkt) {

        /*
         * process the message only if the state is Seeking membership.
         * For all other cases, ignore the message.
         */


        /*
         * If static tree configuration is in effect, check if allowed
         * by configuration
         */
        if (staticConfig) {
            if (!checkAgainstStaticList(pkt.getAddress()))
                return;
        }

	// Ignore HAs from itself 

	try {
	    if ((myAddr.equals(pkt.getAddress()) == true) && 
		(tramblk.getUnicastPort() == pkt.getUnicastPort())) {

		return;
	    }
	} catch (NullPointerException ne) {
	    return;
	}

        switch (tramblk.getTRAMState()) {
	case TRAM_STATE.PRE_DATA_BEACON:
	case TRAM_STATE.DATA_TXM:
	case TRAM_STATE.CONGESTION_IN_EFFECT:
	    if ((pkt.getLstate() == LSTATE.LAN_HEAD) &&
		(gblk.getHstate() == HSTATE.ACCEPTING_MEMBERS) &&
		(pkt.getDirectMemberCount() == 0)) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			pkt.getAddress() + 
			" is claiming to be a LAN HEAD. " +
			"Need to send a LAN Volunteer message");
		}
		lanVolunteer();
	    }

	    break;

	case TRAM_STATE.ATTAINED_MEMBERSHIP: 

            // see if a lan head is starting a new election 

            if ((pkt.getFlags() 
                    & ((int) (TRAMHaPacket.FLAGBIT_ELECT_LAN_HEAD))) != 0) {

                /* 
		 * If we are already on the lan tree, see if we should 
		 * volunteer 
		 */
                if ((gblk.getLstate() == LSTATE.LAN_MEMBER) && 
		    ((gblk.getHstate() == HSTATE.ACCEPTING_MEMBERS) || 
		    (gblk.getHstate() == 
		    HSTATE.ACCEPTING_POTENTIALHEADS_ONLY))) {

                    // we are a candidate
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		        logger.putPacketln(this,
			    "Re-election requested.  " +
			    "Volunteering to be LAN head");
		    }

                    weAreBestLanVolunteer = true;
                    lanVolunteer();
		    loadTimerToReceiveHaPacket();
                }
            }
          // No break here.... just fall thru.
         
        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 

        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 

        case TRAM_STATE.HEAD_BINDING: 

        case TRAM_STATE.REAFFIL_HEAD_BINDING: 

        case TRAM_STATE.SEEKING_REAFFIL_HEAD: 

        case TRAM_STATE.REAFFILIATED: 

            /*
             * If the HA is from the affiliated Head, just update
             * the time stamp and return.
             */
            HeadBlock ahb = gblk.getHeadBlock();

            if (ahb != null) {
                if ((ahb.getAddress().equals(pkt.getAddress()) == true) && 
		    (ahb.getPort() == pkt.getUnicastPort())) {
                    ahb.setLastheard(System.currentTimeMillis());

                    if ((tramblk.getTRAMState() == 
			TRAM_STATE.ATTAINED_MEMBERSHIP) && 
			(pkt.getLstate() == LSTATE.LAN_HEAD) && 
			(pkt.getRxLevel() != 0)) {

                        /*
                         * notice if the root lan head is affiliated so we 
			 * can send our own HAs.
                         */
                        if (ahb.isRootLanHead()) {
                            rootLanHeadIsAffiliated = true;

			    if (logger.requiresLogging(
				TRAMLogger.LOG_DIAGNOSTICS)) {

			    	logger.putPacketln(this, 
				    "Root LAN Head is affiliated");
			    }
                        }
                    }
		    return;
                } else {
		    /*
		     * Comes here if the HA is not from the affiliated head.
		     * Check if the HA is from the re-affiliated Head. If so
		     * update the timstamp and return.
		     */
		    HeadBlock rahb = getReAffiliationHead();

		    if (rahb != null) {
			if ((rahb.getAddress().equals(pkt.getAddress()) == 
			    true) && (rahb.getPort() == 
			    pkt.getUnicastPort())) {

			    rahb.setLastheard(System.currentTimeMillis());
			    return;
			}
		    }
		}

		/* 
		 * Not from a head or from a reaffiliated head either.... 
		 * just continue. 
		 */
	    }
	    /*
	     * The code comes here only if is not affiliated or if the HA 
	     * message is from a head that is other than the affiliated
	     * head or the re-affiliated head.
	     */
	    HeadBlock hb = null;

            if ((pkt.getLstate() == LSTATE.VOLUNTEERING) || 
		(pkt.getLstate() == LSTATE.LAN_MEMBER)) {

                /* 
		 * If we are already the head and there is no need for a 
		 * new head, inform the volunteer.
		 */
		
                if ((gblk.getLstate() == LSTATE.LAN_HEAD) && 
		    (!needNewLanHead)) {

                    lanVolunteer();

		    /* 
		     * just volunteering, not a suitable head yet, but someone
		     * else may replace me.
		     */
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		        logger.putPacketln(this, 
		            "Informing the volunteers that " +
			    "I am a better LAN head");
		    }
		    /*
		     * No more interest on this head... but if it is in
		     * the back up head list, remove it.
		     */ 
                } else {
		    /*
		     * Since this is not a LAN_HEAD, check if is attempting
		     * to become one. If so, force it to give up if the 
		     * heard node is better.
		     */
		    if ((weAreBestLanVolunteer == true) 
			&& (isBetterLanHead(pkt))) {

			// I have been replaced!
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
			    logger.putPacketln(this, 
				"Giving up... BETTER Lan head FOUND in " +
				pkt.getAddress());
			}

			weAreBestLanVolunteer = false;
			gblk.setLstate(LSTATE.LAN_MEMBER); 
		    }

		}
		/*
		 * Follow thru to see if this node needs to be added to the
		 * headlist.
		 */
            } else {

		// if this is a Lan head, see if it's helping us out

		if (pkt.getLstate() == LSTATE.LAN_HEAD) {
		    if (weAreCurrentLanHead) {
			needNewLanHead = false;
			weAreCurrentLanHead = false;
			// Change the head state if we have no members.
			if (gblk.getDirectMemberCount() == 0)
			    gblk.setLstate(LSTATE.LAN_MEMBER);
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
			    logger.putPacketln(this, 
				"Giving up...In favor of " + 
				pkt.getAddress() +
				" as a LAN head");
			}
		    }
		}
	    }
	    /*
	     * Now that the big test is done, check out if the heard node
	     * needs to be added or removed from the backup head list.
	     * If hosen to be added, update the head block with the
	     * latest values and return.
	     */
            boolean suitableHead = isASuitableBackupHstate(pkt.getHstate());

            if ((gblk.getLstate() == LSTATE.LAN_HEAD) && 
		(pkt.getLstate() != 0)) {

                /* 
		 * existing Lan heads can't affiliate with other lan members 
		 */
                suitableHead = false;
            }

            try {
                hb = getBackupHead(pkt.getAddress(), pkt.getUnicastPort());

                /* 
		 * if we are a Lan head, can't reaffiliate with another lan 
		 * head - can cause loops if rxlevels haven't settled down yet.
		 */

                if ((gblk.getLstate() == LSTATE.LAN_HEAD) && 
		    (hb.getLstate() != 0)) {

		    /* 
		     * Why is Lstate in the pkt being used? This is because
		     * a regular HA message will have no LSTATE... the LSTATE
		     * is available ONLY in the lanVolunteer message.
		     */
                    suitableHead = false;
                }
                if (suitableHead == false) {
                    backUpHeads.removeElement(hb);
                    return;
                }

                /*
                 * if the head is still suitable, update info.
                 * NOTE that the TTL field is not being updated
                 * This is because currently we are unable to
                 * get the received TTL of a multicast message in
                 * java. Since the HA messages are sent using
                 * ERS, the TTL of the earlier HA message happens to
                 * be the shortest TTL to the head.
                 */
                hb.setLastheard(System.currentTimeMillis());
                hb.setDirectMemberCount(pkt.getDirectMemberCount());
                hb.setRxLevel(pkt.getRxLevel());
                hb.setHstate(pkt.getHstate());

                if (pkt.getLstate() != LSTATE.NA) {

                    // don't write over interesting LSTATEs with NA

                    hb.setLstate(pkt.getLstate());
                } 
            } catch (NoSuchElementException ne) {

                // Not listed in the current backUpHeads list.

                if (suitableHead == true) {
                    hb = new HeadBlock(pkt.getAddress(), 
			pkt.getUnicastPort());

                    hb.setTTL(pkt.getTTL());
                    hb.setRxLevel(pkt.getRxLevel());
                    hb.setHstate(pkt.getHstate());
                    hb.setDirectMemberCount(pkt.getDirectMemberCount());
                    hb.setLastheard(System.currentTimeMillis());

                    if (pkt.getLstate() != LSTATE.NA) {

                        // don't write over interesting LSTATEs with NA

                        hb.setLstate(pkt.getLstate());
                    } 
		    if (addedToTheBackupHeadList(hb) == false)
			return;
                    // backUpHeads.addElement(hb);
                } else {
                    return;
                }
            }

            /*
             * if this head's TTL is smaller than the current Head's
             * TTL, initiate Re-Affiliation process.
             */
            int dmemCountTmp = gblk.getDirectMemberCount();

            if ((tramblk.getTRAMState() == TRAM_STATE.ATTAINED_MEMBERSHIP) && 
		(gblk.getLstate() != LSTATE.LAN_HEAD) && 
		(ahb.getTTL() > hb.getTTL()) && 
		((dmemCountTmp == 0) || ((dmemCountTmp != 0) && 
		(gblk.getRxLevel() > hb.getRxLevel())))) {

                /*
                 * There is a possibility to re-affiliate. But there
                 * could be other heads whose HAs might not have been
                 * received as yet. Why not wait for some time and
                 * then perform the head seletion?.... This is what
                 * is being done below
                 */
                tramblk.setTRAMState(TRAM_STATE.SEEKING_REAFFIL_HEAD);
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"Changing the TRAMState to Seeking Affil Head");
		}
                loadTimerToReceiveHaPacket();
            }

            // if this is a Lan head, see if it's the root

            if ((pkt.getLstate() == LSTATE.LAN_HEAD) &&
		(weAreBestLanVolunteer == true)) {

                weAreBestLanVolunteer = false;
		gblk.setLstate(LSTATE.LAN_MEMBER); 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"Giving up..Better LAN Head discovered in " +  
		    pkt.getAddress());
		}

                if (pkt.getRxLevel() == 0) {

                    // this is the root Lan head, and it's not yet affiliated 

                    hb.setRootLanHead(true);

                    rootLanHeadExists = true;
                } else {

                    // if this is the root Lan head, it is affiliated

                    if (hb.isRootLanHead()) {
			rootLanHeadIsAffiliated = true;
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
			    logger.putPacketln(this, 
				"Root LAN head is affiliated");
			}
                    }
                }

                break;
            }

            break;

        default: 
            break;
        }
    }

    /**
     * private method to test is a Head state is suitable to be a
     * backup head.
     * 
     * @return true if the Hstate is suitable to be considered
     * for a backup head.
     * false if found unsuitable.
     */
    private boolean isASuitableBackupHstate(byte headHstate) {

        /*
         * First validate if the head is a suitable candiate
         * to be considered as an head.
         * 
         * the following if statement is basically checking
         * and validating for the following valid combinations
         * NOTE: an HA cannot be sent if the head is in
         * HSTATE.NOT_ACCEPTING_MEMBERS state.
         * The following are valid combinations to retain
         * the head as a potential backup head.
         * 
         * a. myMrole is MEMBER_ONLY and the headState is
         * HSTATE.ACCEPTING_MEMBERS.
         * 
         * b. myMrole is Eager or Reluctant head and the
         * headHstate is accepting ONLY
         * ACCEPTING_POTENTIALHEADS_ONLY.
         * Anything other than the above combination will
         * cause the head to be removed from the backUphead list.
         */
        TRAMTransportProfile tp = tramblk.getTransportProfile();
        byte myMrole = tp.getMrole();

        if (((myMrole == MROLE.MEMBER_ONLY) && (headHstate == 
		HSTATE.ACCEPTING_MEMBERS)) || ((myMrole != MROLE.MEMBER_ONLY) 
                    && ((headHstate == HSTATE.ACCEPTING_MEMBERS) 
                  || (headHstate == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY)))) {
            return true;
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                "HeadState " + headHstate + " found Unsuitable");
	}
        return false;
    }

    /**
     * processAmPacket - private method to process the incoming Am
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMAmPacket - the incoming Am packet that needs to be
     * processed.
     */
    private void processAmPacket(TRAMAmPacket pkt) {

        /*
         * Accept an AM message only in valid TRAM states and ignore
         * the message under invalid TRAM states.
         */
        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.HEAD_BINDING: 
            performHeadBinding(pkt);

            break;

        case TRAM_STATE.REAFFIL_HEAD_BINDING: 
            performReaffilHeadBinding(pkt);

            break;


        case TRAM_STATE.ATTAINED_MEMBERSHIP:

            HeadBlock hb = gblk.getHeadBlock();
            if ((hb.getAddress().equals(pkt.getAddress()) == true) &&
		  (hb.getPort() == pkt.getPort()))
	    {
		/*
		 * must be a redundant transmission of an AM message. Just
		 * Ignore it.
		 */
		return;
	    }
	    // Just fall thru .... No break here. 

	case TRAM_STATE.REAFFILIATED:
	    if ((toBeHead != null) && 
		((toBeHead.getAddress().equals(pkt.getAddress()) == true) &&
		 (toBeHead.getPort() == pkt.getPort())))
	    {
		/*
		 * must be a redundant transmission of an AM message. Just
		 * Ignore it.
		 */
		return;
	    }

	    // Just fall thru .... No break here. 

        default: 
            TRAMMemberAck mack = tramblk.getMemberAck();

            if (mack != null) {
                mack.sendAckToNonAffiliatedHead(pkt.getAddress(),
					      pkt.getPort(),
			       TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP);
            }
            // logger.putPacketln(this, "ignoring AM");
	    
            // Ignore the message for other states.

            break;
        }
    }

    /**
     * performHeadBinding - private method to perform the headbinding
     * operation.
     */
    private void performHeadBinding(TRAMAmPacket pkt) {

        /*
         * Check if the response is from the chosen head. If
         * so make chosen head to be the actual head. Start the
         * Head monitoring thread.
         */
        if ((pkt.getAddress().equals(toBeHead.getAddress()) == false) 
                || (pkt.getPort() != toBeHead.getPort())) {
	    return;
	}

	// First extract and set the start seq number 

	toBeHead.setStartSeqNumber(pkt.getStartSeqNumber());

	// Make the TobeHead to actually be the HEAD.

	gblk.setHeadBlock(toBeHead);

	// Update this node's RxLevel.

	gblk.setRxLevel(toBeHead.getRxLevel() + 1);

	// if we attached to a Lan head, set our lan state

	if (toBeHead.getLstate() == LSTATE.LAN_HEAD) {
	    gblk.setLstate(LSTATE.LAN_MEMBER);
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this, 
	            "Assuming the role of a member - head " + 
		    toBeHead.getAddress() + " is a LAN HEAD");
	    }
	}

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CONG)) {

	    logger.putPacketln(this, 
		"Binding to head " + pkt.getAddress() + 
		" " + pkt.getPort() +
		", starting seq " + pkt.getStartSeqNumber() + 
		", My Address is: " + myAddr + ", TTL is: " +
		toBeHead.getTTL());
	}

	// wipe out toBeHead reference.

	toBeHead = null;
	hbReTxmitCount = 0;

	tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);

	if (gblk.getHstate() == HSTATE.INIT) {

	    // if we're on a lan, we may already have had a valid Hstate 
	    
	    if (tramblk.getTransportProfile().getMrole() 
		!= MROLE.MEMBER_ONLY) {
		gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);
	    } 
	}

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	    logger.putPacketln(this, 
		"Changing State to ATTAINED_MEMBERSHIP");
	}

	// Stop the HB polling timer.

	timer.stopTimer();

	// Start the member Ack Thread if not already running.
	TRAMMemberAck memberAck = tramblk.getMemberAck();
	if (memberAck == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "Starting the MemberAckThread");
	    }
	    memberAck = new TRAMMemberAck(tramblk);
	    if (memberAck == null)
		return;
	    tramblk.setMemberAck(memberAck);
	}
	
	/*
	 * Spawn the Hello Thrd if not already started.
	 */
	if (tramblk.getHelloThread() == null) {
	    HelloThread ht = new HelloThread(tramblk);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "Spawned the Hello thread");
	    }
	}
	if (tramblk.getTransportProfile().getMrole() != MROLE.MEMBER_ONLY) {
	    loadTimerToSendHaPkt();
	    tramblk.getInputDispThread().addTRAMMsPacketListener(this);
	    tramblk.getUcastInputDispThread().addTRAMAckPacketListener(this);
	}
	/* 
	 * memberAck.handleUnrecoverablePktsCondition(pkt.getStartSeqNumber(),
         *                  0, null, false); 
	 *  tramblk.getTRAMDataCache().handleUnrecoverablePktsCondition(
	 * pkt.getStartSeqNumber()); 
	 */

	// now tell everybody interested about attaining membership 
	notifyMembershipListeners(new TRAMMembershipEvent(this));
    }

    /**
     * performReaffilHeadBinding - private method to perform the
     * reaffilaition Head Binding operation
     */
    private void performReaffilHeadBinding(TRAMAmPacket pkt) {

        /*
         * Check if the response is from the chosen reaffil_head.
         * if so make the chosen reaffil_head to be actual head,
         * dispatch a member resigning message to the old head.
         * restart the head monitoring timer.
         */
        if ((pkt.getAddress().equals(toBeHead.getAddress()) == true) 
                && (pkt.getPort() == toBeHead.getPort())) {

            /* First extract and set the start seq number */

            toBeHead.setStartSeqNumber(pkt.getStartSeqNumber());

            TRAMSeqNumber reAffilHeadStartSeqNumber = 
                new TRAMSeqNumber(pkt.getStartSeqNumber());
            TRAMMemberAck memAck = tramblk.getMemberAck();

	    /*
	     * If we don't have missing packets or the dependent head is DEAD
	     * ... make the re-affiliated head to be the dependent head 
	     * right away.
	     */
            if ((memAck != null) &&
		 ((memAck.checkPriorMissing(pkt.getStartSeqNumber()) == false)
		  || (gblk.getHeadBlock() == null))) {

                /*
                 * Hooray, we can change the head right away.
                 * This node does not have to depend on the old head anymore.
                 */
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CONG)) {

                    logger.putPacketln(this, 
		        "Reaffiliating now to head " + pkt.getAddress() + 
		        " " + pkt.getPort() +
		        ", starting seq " + pkt.getStartSeqNumber() + 
			", My Address is: " + myAddr + ", TTL is: " +
			toBeHead.getTTL());
		}

                makeReAffilHeadToBeMainHead();
            } else {

                /*
                 * If our old head is still alive and well then
                 * the member has to deal with dual membership
                 * till it is appropriate to reliquish the
                 * membership at the old head.
                 * 
                 * But if we lost our old head, then we need to mark missing 
		 * packets as unrecoverable so that when the application
		 * requests them, an unrecoverable exception will be thrown.
                 */
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, 
			"Changing State to REAFFILIATED");
		}
		tramblk.setTRAMState(TRAM_STATE.REAFFILIATED);

            }

            hbReTxmitCount = 0;

            // Stop the HB polling timer.

            timer.stopTimer();

            TRAMTransportProfile tp = tramblk.getTransportProfile();

	    /*
	     * If the Head State was transitioned to RESIGNING due to the 
	     * Loss of the dependent head, we may need to re-transition
	     * to a head that is accepting members.
	     * Perform the above check and transition appropriately.
	     */
            if (tp.getMrole() != MROLE.MEMBER_ONLY) {
                if (gblk.getHstate() == HSTATE.RESIGNING) {

                    /*
                     * Determine and initialize the HSTATE to the
                     * Appropriate HSTATE value.
		     * First lets make it that it default case - accepts 
		     * members and then as per the condition, we shall 
		     * transition to whatever case may be.
                     */

                    gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);

                    if (gblk.getDirectMemberCount() >= tp.getMaxMembers()) {
                        gblk.setHstate(HSTATE.NOT_ACCEPTING_MEMBERS);
                    } else {
                        if ((gblk.getMemberOnlyCount() + 
			     getMembersOnlyInToBeMembers()) 
			    >= tp.getMaxNonHeads()) {
                            gblk.setHstate(
				       HSTATE.ACCEPTING_POTENTIALHEADS_ONLY);
                        } 
                    }
                }

                loadTimerToSendHaPkt();
            }
        }
    }

    /**
     * processRmPacket - private method to process the incoming Rm
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMRmPacket - the incoming Rm packet that needs to be
     * processed.
     */
    private void processRmPacket(TRAMRmPacket pkt) {
        if ((tramblk.getTRAMState() != TRAM_STATE.HEAD_BINDING) 
             && (tramblk.getTRAMState() != TRAM_STATE.REAFFIL_HEAD_BINDING)) {
            return;
        } 

        /*
         * first check if the Rm is from the selected toBe Head
         * If not from the toBeHead, Ignore the message.
         */
        if ((toBeHead.getAddress().equals(pkt.getAddress()) == true) 
                && (toBeHead.getPort() == pkt.getPort())) {

            /*
             * Too bad, the head rejected!... lets do the selection
             * all over again
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
		    " Received RM from the ToBeHead" + 
		    toBeHead.getAddress() + " " + 
		    toBeHead.getPort());
	    }

            hbReTxmitCount = 0;

            if (tramblk.getTRAMState() == TRAM_STATE.REAFFIL_HEAD_BINDING) {

                /*
                 * may be the chosen head is not suitable. Lets
                 * currently transition the state back to
                 * ATTAINED_MEMBERSHIP state and return
                 */
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
			"Changing to Attained membership. " +
			"Re-Affil head sent RM message");
		}
		
                tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);

                toBeHead = null;

                return;
            }
            if (haInterval == 0) {
                tramblk.setTRAMState(TRAM_STATE.SEEKING_MTHA_MEMBERSHIP);
            } else {
                tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);
            }

            toBeHead = null;

            performHeadSelection();

            switch (tramblk.getTRAMState()) {
            case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 
                loadTimerToReceiveHaPacket();

                return;

            case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 
                sendMsPacket();
                timer.reloadTimer(((long) 
		  (tramblk.getTransportProfile().getMsRate())) & 0xffffffffL);

                break;

            case TRAM_STATE.HEAD_BINDING: 

                /*
                 * yes a head has been chosen. Load a timer
                 * to receive an AM message from the head.
                 */
                timer.reloadTimer(HB_TIMEOUT);

                break;

            default: 
		if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC)) {
                    logger.putPacketln(this, 
			"Invalid TRAM STATE. ERROR");
		}

                return;
            }
        }
    }

    /**
     * processAckPacket - private method to process the incoming Ack
     * packet. The processing is dependent on the current TRAM_STATE.
     * 
     * 
     * @param TRAMAckPacket - the incoming Ack packet that needs to be
     * processed.
     */
    private void processAckPacket(TRAMAckPacket pkt) {

        /*
         * Maintain time stamps on a per member basis. Major Ack processing
         * is done by the AckProcessor. Also handeled here are computing
         * of RTT.
         */
        if (gblk.getHstate() == HSTATE.INIT) {

            // Ignore the spurious ACK message

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
		    "Received ACK in INIT state");
	    }

            return;
        }

        MemberBlock mb = null;

        try {
            mb = gblk.getMember(pkt.getAddress(), pkt.getPort());
        } catch (NoSuchElementException ne) {

            // Ignore the spurious ACK message.

            return;
        }

        mb.setLastheard(System.currentTimeMillis());
        mb.clearMissedAcks();

        byte ackFlag = (byte) pkt.getFlags();

        if ((ackFlag & TRAMAckPacket.FLAGBIT_HELLO_NOT_RECVD) != 0) {

            /*
             * Hellos are not reaching the member.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
		    "Hellos are not reaching member " + mb.getAddress());
	    }
            sendHelloUniPacket(mb, /* flag */ (byte) 0);

	    /*
	     * We may to increase the TTL value but since we cannot get
	     * the actual TTL value from the packets in Java, we currently
	     * don't support it.... That is, for reasons wherein the
	     * the TTL value only increases and we have no means of
	     * recognizing that we need to reduce it.
	     */

        }

        // update the direct and indirect member counts.

        mb.setDmemCount(pkt.getDirectMemberCount());
        mb.setIndmemCount(pkt.getIndirectMemberCount());

        // collect advertising head count if we need to calculate HAI 

        if (haInterval != 0) {

            /*
             * Determine the # of advt heads reported by the
             * member. The count will be non zero only if
             * playing the role of a head.
             */
            int membersAdvertisingHeads = pkt.getDirectHeadsAdvertising() 
                                          + pkt.getIndirectHeadsAdvertising();
            boolean memberAdvtCountChanged = false;

            // see if the count has changed

            if (membersAdvertisingHeads != mb.getAdvertmemCount()) {

                /*
                 * adjust advertising head count ......
                 * Note that the "indirectAdvertisingHeads" has
                 * sum total of all the advertising heads reported
                 * by ALL the members of this head.
                 */
                indirectAdvertisingHeads += membersAdvertisingHeads;
                indirectAdvertisingHeads -= mb.getAdvertmemCount();

                // save the new count 

                mb.setAdvertmemCount(membersAdvertisingHeads);

                memberAdvtCountChanged = true;
            }

            TRAMTransportProfile tp = tramblk.getTransportProfile();

            /*
             * Only sender does the HAI calculations ...
             * The sender will have to change the HAI rate if
             * either of the following conditions are true
             * a. if the member reported count is different from the
             * previously reported count.
             * b. if the memebr's Hstate is different from the
             * previously reported state.
             */
            if ((tp.getTmode() != TMODE.RECEIVE_ONLY) 
                    && ((memberAdvtCountChanged == true))) {
                int totalAdvtHeads = indirectAdvertisingHeads 
                                     + gblk.getDirectAdvertisingMemberCount();

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this,
			"Sender Advt Count " + totalAdvtHeads + 
			"indirect " + indirectAdvertisingHeads);
		}

                /*
                 * Check if the sender is still Advertizing
                 * Count itself if it is still soliciting members
                 */
                byte tmpHstate = gblk.getHstate();

                if ((tmpHstate == HSTATE.ACCEPTING_MEMBERS) || 
		    (tmpHstate == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY)) {
                    totalAdvtHeads++;
                } 
                if (totalAdvtHeads != advertisingHeads) {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this,  
			    "Previous Headcount " + advertisingHeads + 
			    " New ADVERTISING HEAD COUNT " + totalAdvtHeads);
		    }

                    advertisingHeads = totalAdvtHeads;

                    /*
                     * recalculate the HAI
                     */
                    long newHaInterval = 0;
                    long maxHaBw = tp.getMaxHABWWhileDataTransfer();

                    if ((maxHaBw != 0)) {

                        /*
                         * HA formula is as follows -
                         * new HAInterval = MAX( 1/2 second,
                         * (#advtHeadCount*HASize)/maxHaBw,
                         * (#advtHeadCount/MaxPacketRate) )
                         * MaxPacketRate is currently constant
                         * and is equal to 30. HASize is 320 bits.
                         */
                        long val2 = ((advertisingHeads * 320) / maxHaBw);
                        long val3 = (advertisingHeads) / 30;

                        newHaInterval = 1000;       // 1 second.

                        if (newHaInterval < val2) {
                            newHaInterval = val2;
                        } 
                        if (newHaInterval < val3) {
                            newHaInterval = val3;
                        } 

                        haInterval = (short) newHaInterval;

			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, 
                                "Adjusted HA Interval to " + haInterval);
			}
                    }
                }
            }
        }
        if ((ackFlag & TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP) != 0) {

            /*
             * Member is reaffiliated. remove the member from the
             * member list, turn off the waiting for ack flag
             * for all the messages in the cache, Update the
             * new member mask for ack messages, check if
             * this member was the last member. If the
             * member happens to be the last member, then
             * stop the HeadAck module, transition the
             * Hstate to INIT, update the direct and indirect
             * member counts, update the cstate, Stop the
             * Hello module.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
                logger.putPacketln(this, 
		    "Member " + mb.getAddress() + " " + mb.getPort() + 
		    " has requested TERMINATION of membership");
	    }
            disownMember(mb, false);
            tramblk.getTRAMDataCache().purgeCache(0);
        }
    }

    /**
     * processHelloUniPacket - private method to process the incoming
     * HelloUni packet. The processing is dependent on the current
     * TRAM_STATE.
     * 
     * 
     * @param TRAMHelloUniPacket - the incoming HelloUni packet that
     * needs to be processed.
     */
    private void processHelloUniPacket(TRAMHelloUniPacket pkt) {

        /*
         * Update the head's last heard and update the TTL to source
         * field. Reset the missed Hello count to 0;
         */
        HeadBlock headBlk = null;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CNTLMESG)) {

            logger.putPacketln(this, 
	        "got a Hello Uni packet from " + pkt.getAddress() + 
		" TRAM State is " + tramblk.getTRAMState());
	}

        headBlk = gblk.getHeadBlock();
	if (headBlk == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "headblk is null...");
	    }
	} else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "head addr " + headBlk.getAddress() + 
		    ", pkt addr " + pkt.getAddress() +
		    ", head port " + headBlk.getPort() + 
		    ", pkt port " + pkt.getPort());
	    }
	}

        switch (tramblk.getTRAMState()) {
        case TRAM_STATE.ATTAINED_MEMBERSHIP: 
        case TRAM_STATE.REAFFILIATED: 
        case TRAM_STATE.SEEKING_REAFFIL_HEAD: 
        case TRAM_STATE.REAFFIL_HEAD_BINDING: 
            break;

        default: 

            // Ignore

            return;
        }

        headBlk = gblk.getHeadBlock();

        if ((headBlk != null) && 
	    (headBlk.getAddress().equals(pkt.getAddress()) == true)  &&
	    (headBlk.getPort() == pkt.getPort())) {
            if (((pkt.getFlags() & 
		((int) (TRAMHelloUniPacket.FLAGBIT_DISOWNED))) != 0)) {

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this,
			"Head is Disowning membership");
		}

		/* 
		 * This is the where the receiver first realises that 
		 * it is being pruned by its head.
		 *
		 * For purposes of the FB experiments we EXIT right away. 
		 * We do not attempt to reaffiliate or try to notify children 
		 * (only on level tree)
		 */
		if (tramblk.getTransportProfile().
		    reaffiliateAfterBeingDisowned() == false) {

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                        logger.putPacketln(this,
			    "Head is Disowning membership, exit now.");
		    }

		    System.exit(4);
		}

                /*
                 * set the reAffil Head block(toBeHead) to null so that
                 * an ACK is not sent.
                 */
                gblk.setHeadBlock(null);
                handleHeadLoss();

		if (gblk.getDirectMemberCount() != 0) {
		    gblk.setHstate(HSTATE.RESIGNING);
		    tramblk.getHelloThread().sendSimpleHello();
		}

		tramblk.getTRAMDataCache().handlePrunedMember();
                return;
            }

            headBlk.setLastheard(System.currentTimeMillis());
        } else {

            // Just do the following ONLY if re-Affiliated. 

            if (tramblk.getTRAMState() == TRAM_STATE.REAFFILIATED) {
                headBlk = getReAffiliationHead();

                if ((headBlk != null) && (headBlk.getAddress().
		    equals(pkt.getAddress()) == true) && 
		    (headBlk.getPort() == pkt.getPort())) {

                    // First Check if the head is disowning me...

                    if (((pkt.getFlags() & ((int) (TRAMHelloUniPacket.
			FLAGBIT_DISOWNED))) != 0)) {

			if (logger.requiresLogging(
			    TRAMLogger.LOG_DIAGNOSTICS)) {

                            logger.putPacketln(this, 
				"Reaffiliated Head is Disowning membership");
			}

                        /*
                         * set the head block to null so that an ACK is not
                         * sent.
                         */
                        toBeHead = null;

                        handleReAffiliatedHeadLoss();

                        return;
                    }

                    headBlk.setLastheard(System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * This method is the interface for BeaconPacketListener. The multicast
     * input dispatcher calls this method when an beacon packet is received.
     * This method then places the packet on the pktsToProcess Queue/vector
     * and resumes the thread.
     * 
     * @param BeaconPacketEvent.
     */
    public void receiveBeaconPacket(BeaconPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread.. hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMHelloPacketListener. The
     * multicast input dispatcher calls this method when a Hello packet
     * is received.
     * This method then places the packet on the pktsToProcess Queue/vector
     * and resumes the thread.
     * 
     * @param TRAMHelloPacketEvent.
     */
    public void receiveTRAMHelloPacket(TRAMHelloPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to
         * check if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMDataPacketListener. The multicast
     * input dispatcher calls this method when a Data packet is received.
     * This method then places the packet on the pktsToProcess Queue/vector
     * and resumes the thread.
     * 
     * @param TRAMDataPacketEvent.
     */
    public void receiveDataPacket(TRAMDataPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to
         * check if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMHelloUniPacketListener. The
     * Unicast input dispatcher calls this method when an
     * TRAMHelloUnipacket is received. This method then places the packet
     * on the pktsToProcess Queue/Vector and resumes the thread.
     * 
     * @param TRAMHelloUniPacketEvent.
     */
    public void receiveTRAMHelloUniPacket(TRAMHelloUniPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread.. hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMAckPacketListener. The Unicast
     * input dispatcher calls this method when an TRAMAckpacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMAckPacketEvent.
     */
    public void receiveAckPacket(TRAMAckPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMAmPacketListener. The Unicast
     * input dispatcher calls this method when an TRAMAmpacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMAmPacketEvent.
     */
    public void receiveTRAMAmPacket(TRAMAmPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMRmPacketListener. The Unicast
     * input dispatcher calls this method when an TRAMRmpacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMRmPacketEvent.
     */
    public void receiveTRAMRmPacket(TRAMRmPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * This method is the interface for TRAMHaPacketListener. The Unicast
     * input dispatcher calls this method when an TRAMHapacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMHaPacketEvent.
     */
    public void receiveTRAMHaPacket(TRAMHaPacketEvent e) {
	if (getHaPacketsToProcess() < 25) {
	    pktsToProcess.addElement(e.getPacket());
	    incrementHaPacketsToProcess();
	    /*
	     * No harm in resuming a running thread... hence no need to check
	     * if suspended before resuming.
	     */
	    wake();
	} else {
	    /*
	     * Discard HA packets..... if we already a member we don't care.
	     */
	}
    }

    /**
     * This method is the interface for TRAMMsPacketListener. The Multicast
     * input dispatcher calls this method when an TRAMMspacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMMsPacketEvent.
     */
    public void receiveTRAMMsPacket(TRAMMsPacketEvent e) {
	if (getMsPacketsToProcess() < 31) {
	    pktsToProcess.addElement(e.getPacket());
	    incrementMsPacketsToProcess();
	    /*
	     * No harm in resuming a running thread... hence no need to check
	     * if suspended before resuming.
	     */
	    wake();
	} else {
	    /*
	     * Discard MS packets..... Max that can be accepetd is 31
	     */
	}
    }

    /**
     * This method is the interface for TRAMHbPacketListener. The Multicast
     * input dispatcher calls this method when an TRAMHbpacket is received.
     * This method then places the packet on the pktsToProcess Queue/Vector
     * and resumes the thread.
     * 
     * @param TRAMHbPacketEvent.
     */
    public void receiveTRAMHbPacket(TRAMHbPacketEvent e) {
        pktsToProcess.addElement(e.getPacket());

        /*
         * No harm in resuming a running thread... hence no need to check
         * if suspended before resuming.
         */
        wake();
    }

    /**
     * Method to get the number HaMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized int getHaPacketsToProcess() {

	return haPacketsToProcess;
    }

    /**
     * Method to increment the number HaMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized void incrementHaPacketsToProcess() {

	haPacketsToProcess++;
    }

    /**
     * Method to decrement the number HaMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized void decrementHaPacketsToProcess() {

	if (haPacketsToProcess > 0)
	    haPacketsToProcess--;
    }

    /**
     * Method to get the number MsMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized int getMsPacketsToProcess() {

	return msPacketsToProcess;
    }

    /**
     * Method to increment the number MsMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized void incrementMsPacketsToProcess() {

	msPacketsToProcess++;
    }

    /**
     * Method to decrement the number MsMessages in the queue. This is made a
     * method to help overcome synchronization problems.
     */

    private synchronized void decrementMsPacketsToProcess() {

	if (msPacketsToProcess > 0)
	    msPacketsToProcess--;
    }

    /**
     * Looks for a specific member block in the to_be_member list.
     * if found the MemberBlock is returned else and exception is
     * thrown.
     * 
     * @param InetAddress - InetAddress of the member being saught
     * @param int - port of the member being saught.
     * 
     * @Exception - NoSuchElementException if no matching entry
     * is found.
     * 
     */
    private MemberBlock getToBeMember(InetAddress ia, int port) 
            throws NoSuchElementException {
        MemberBlock mb = null;

        for (int i = 0; i < toBeMembers.size(); i++) {
            mb = (MemberBlock) toBeMembers.elementAt(i);

            if ((ia.equals(mb.getAddress())) && (mb.getPort() == port)) {
                return mb;
            } 
        }

        throw new NoSuchElementException();
    }

    /**
     * 
     * handleTimeout method to support TRAMTimerEventHandler
     * 
     */
    public void handleTimeout() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "In handleTimeout routine");
	}

        /*
         * First find out the current TRAM state. Based on the
         * TRAM State, perform relavent operations.
         */
        TRAMTransportProfile tp = tramblk.getTransportProfile();

        switch (tramblk.getTRAMState()) {

        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 
            if (--haTimeoutCount == 0) {
                if (backUpHeads.size() != 0) {

                    /*
                     * performHeadSelection() chooses a potential head
                     * sends an HeadBind message and changes the
                     * state to HEAD_BINDING.
                     */
                    performHeadSelection();

                    if (tramblk.getTRAMState() == TRAM_STATE.HEAD_BINDING) {

                        /*
                         * yes a head has been chosen. Load a timer
                         * to receive an AM message from the head.
                         * 
                         * 
                         */
                        timer.loadTimer(HB_TIMEOUT);
                    }
                }
            }

            // wait for another HA interval

            if (tramblk.getTRAMState() != TRAM_STATE.HEAD_BINDING) {
                loadTimerToReceiveHaPacket();
            } 
            if (weAreBestLanVolunteer == true) {
                if (haTimeoutCount == 1) {

                    // time to declare ourselves the lan head 

                    becomeLanHead();
                } else if (haTimeoutCount == 2) {

                    // send out one more volunteer 

                    lanVolunteer();
                } 
            }

            break;

        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 
            if (weAreBestLanVolunteer == true) {

                // time to declare ourselves the lan head 

                becomeLanHead();
            } 

            /*
             * Process the received HA messages. If no messages were
             * received, resend the MS message to a larger TTL scope.
             * If a potential head is chosen, load the Head binding
             * timer.
             */
            if (backUpHeads.size() != 0) {
                performHeadSelection();

                if (tramblk.getTRAMState() == TRAM_STATE.HEAD_BINDING) {

                    /*
                     * yes a head has been chosen. Load a timer
                     * to receive an AM message from the head.
                     * 
                     * 
                     */
                    timer.loadTimer(HB_TIMEOUT);

                    return;
                }

            // else send another MS message.... fall thru

            }

            sendMsPacket();
            timer.loadTimer(((long) (tp.getMsRate())) & 0xffffffffL);

            break;

        case TRAM_STATE.HEAD_BINDING: 

            /*
             * Response did not come!. retransmit the HB message.
             * The retransmission is done for MAX_HB_RETXMIT times.
             * If HB has already been retransmitted the maximum
             * number of times then, the TRAM state is restored back to
             * either SEEKING_HA_MEMBERSHIP or SEEKING_MTHA_MEMBERSHIP.
             * The state is changed to SEEKING_HA_MEMBERSHIP if
             * the data transmission is yet to start. The state is
             * changed to SEEKING_MTHA_MEMBERSHIP if the data
             * transmission is in progress.
             */
            hbReTxmitCount++;

            if (hbReTxmitCount > MAX_HB_RETXMIT) {
                hbReTxmitCount = 0;

                /*
                 * Change TRAM_STATE to the appropriate state and
                 * start the affiliation process all over again.
                 */
                if (!tryStartingMTHA(tp)) {
                    tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this,
                            "Changing to SEEKING_HA_MEMBERSHIP");
		    }

                    loadTimerToReceiveHaPacket();
                }
            } else {

                sendHbPacket();
                timer.loadTimer(HB_TIMEOUT);
            }

            break;

        case TRAM_STATE.SEEKING_REAFFIL_HEAD: 
            if (backUpHeads.size() != 0) {
                performHeadSelection();

                if (tramblk.getTRAMState() == TRAM_STATE.REAFFIL_HEAD_BINDING) {

                    /*
                     * yes a head has been chosen. Load a timer
                     * to receive an AM message from the head.
                     * 
                     */
                    timer.loadTimer(HB_TIMEOUT);

                    return;
                }

            // else ... fall thru

            }
            if (haInterval == 0) {
                sendMsPacket();
                timer.loadTimer(((long) (tp.getMsRate())) & 0xffffffffL);
            } else {
                loadTimerToReceiveHaPacket();
            }

            break;

        case TRAM_STATE.REAFFIL_HEAD_BINDING: 

            /*
             * Response did not come!. retransmit the HB message.
             * The retransmission is done for MAX_HB_RETXMIT times.
             * If HB has already been retransmitted the maximum
             * number of times then, the TRAM state is restored back to
             * ATTAINED_MEMBERSHIP.
             */
            hbReTxmitCount++;

            if (hbReTxmitCount > MAX_HB_RETXMIT) {
                hbReTxmitCount = 0;

                tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
		    logger.putPacketln(this, 
		        "Changing to Attained Mebership from " +
			"Reaff head binding");
		}
                /*
                 * Change TRAM_STATE to the appropriate state and
                 * start the affiliation process all over again.
                 */
                if ((tramblk.getTransportProfile().getMrole() 
                        == MROLE.MEMBER_ONLY)) {
                    return;     // no need to load a timer!
                }

                loadTimerToSendHaPkt();
            } else {

                sendHbPacket();
                timer.loadTimer(HB_TIMEOUT);
            }

            break;

        case TRAM_STATE.PRE_DATA_BEACON: 

            /*
             * Dispatch a HA message with ERS TTL Scope.
             */
            if ((haInterval != 0) 
                    && ((gblk.getHstate() == HSTATE.ACCEPTING_MEMBERS) 
                        || (gblk.getHstate() 
                            == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY))) {
                sendHaPacket();
                loadTimerToSendHaPkt();
            }

            break;

        case TRAM_STATE.DATA_TXM: 

            /*
             * Dispatch a HA message with ERS TTL Scope.
             */
            if (tp.getTreeFormationPreference(true) 
                    != TRAMTransportProfile.TREE_FORM_HA) {
                haInterval = 0;
            } 
            if ((gblk.getHstate() == HSTATE.ACCEPTING_MEMBERS) 
                    || (gblk.getHstate() 
                        == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY)) {
                if (tp.getTreeFormationPreference(true) 
                        == TRAMTransportProfile.TREE_FORM_HA) {
                    sendHaPacket();
                } else {
                    lanVolunteer();
                }

                loadTimerToSendHaPkt();
            }

            break;

        case TRAM_STATE.ATTAINED_MEMBERSHIP: 

        case TRAM_STATE.REAFFILIATED: 

            /* Dispatch HA messages with ERS TTL scope. */

            if ((tp.getMrole() != MROLE.MEMBER_ONLY) 
                    && ((gblk.getHstate() == HSTATE.ACCEPTING_MEMBERS) 
                        || (gblk.getHstate() 
                            == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY))) {
                if (haInterval != 0) {
                    sendHaPacket();
                } else {
                    if (gblk.getLstate() == LSTATE.LAN_HEAD) {

                        // even if we're MTHA, volunteer

                        lanVolunteer();
                    }
                }

                loadTimerToSendHaPkt();

                /* maintain the timeout counts for lan head election */

                if (haTimeoutCount == 0) {
                    haTimeoutCount = 3;
                } else {
                    --haTimeoutCount;
                }

                // see if it's time for us to become a head

                if (weAreBestLanVolunteer == true) {
                    if (haTimeoutCount == 1) {

                        /* time to declare ourselves the lan head */

                        becomeLanHead();
                    } else if (haTimeoutCount == 2) {

                        /* send another volunteer */

                        lanVolunteer();
                    } 
                }
            }

            break;

        default: 
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
		    "No Timer Action for the TRAM State " + 
		    tramblk.getTRAMState());
	    }

            break;
        }
    }

    /**
     * private method to send an AM message to a Potential member.
     */
    private void sendAmPacket(InetAddress addr, int port) {

        TRAMAmPacket ampkt = new TRAMAmPacket(tramblk);

        ampkt.setAddress(addr);
        ampkt.setPort(port);
        ampkt.setStartSeqNumber(computeStartPacketForMember());

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
	    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

            logger.putPacketln(this, 
		"Sending Am Packet to " + addr + " " + port + 
		" Starting sequence number is " + 
		ampkt.getStartSeqNumber());
	}

        /* 
	 * // ampkt.setStartSeqNumber(
	 * tramblk.getTRAMDataCache().getLowestSequenceNumber());
	 */

        DatagramPacket dp = ampkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateUnicastPacket(dp);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(ampkt);
	        } catch (NullPointerException e) {}

                tramblk.getUnicastSocket().send(dp);
            }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Unable to dispatch an Am Packet");
	    }
        }
    }

    /**
     * private method to send an RM message to a member.
     */
    private void sendRmPacket(InetAddress addr, int port, int reasonCode) {
        TRAMRmPacket rmpkt = new TRAMRmPacket(tramblk, reasonCode);

        rmpkt.setAddress(addr);
        rmpkt.setPort(port);

        DatagramPacket dp = rmpkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateUnicastPacket(dp);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(rmpkt);
	        } catch (NullPointerException e) {}

                tramblk.getUnicastSocket().send(dp);
            }
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

	        logger.putPacketln(this, "Sent an Rm Packet");
	    }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Unable to dispatch an Rm Packet");
	    }
        }

        /*
         * if we are the current Lan head, urge the affiliated
         * potential lan heads to elect a new lan head
         */
        if ((weAreCurrentLanHead) && (!needNewLanHead)) {
            needNewLanHead = true;

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "!!!Informing members to elect a new LAN HEAD");
	    }

            lanVolunteer();
        }
    }

    /**
     * private method to send a Hello Unicast message to a member.
     */
    private void sendHelloUniPacket(MemberBlock mb, byte flag) {
        long distToSource = tramblk.getGroupMgmtBlk().getRttToSender();
        long distFromSrcToMember = distToSource + ((long) mb.getRTT()) 
                                   & 0xffffffffL;
        TRAMHelloUniPacket helloUniPkt = new TRAMHelloUniPacket(tramblk);

        helloUniPkt.setAddress(mb.getAddress());
        helloUniPkt.setPort(mb.getPort());
        helloUniPkt.setFlags(flag);

        DatagramPacket dp = helloUniPkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateUnicastPacket(dp);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(helloUniPkt);
	        } catch (NullPointerException e) {}

                tramblk.getUnicastSocket().send(dp);
            }
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

	    	logger.putPacketln(this, 
		    "Sent a Hello Uni Packet to " + mb.getAddress() + 
		    " "+ mb.getPort());
	    }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Unable to dispatch an HelloUni Packet to " +
		    mb.getAddress() + " " + mb.getPort());
	    }
        }
    }

    /**
     * private method to perform Head Selection operation.
     */
    private void performHeadSelection() {

        /*
         * First find out if this node is attempting get Affiliated
         * with a head or is attemting to be Re-Affiliated.
         */
        HeadBlock bestHead = null;
        HeadBlock currentHead = gblk.getHeadBlock();

        if ((tramblk.getTRAMState() == TRAM_STATE.SEEKING_REAFFIL_HEAD) 
                && (gblk.getDirectMemberCount() != 0)) {
            try {
                bestHead = getBestSuitedHeadOnRxLevel(gblk.getRxLevel());

            // Fall Thru

            } catch (NoSuchElementException ne) {

                /*
                 * If no head is suitable, then check if the current head
                 * is resigning. If so, let the state continue to be
                 * in SEEKING_REAFFIL_HEAD, else change it back to
                 * AFFILATED.
                 */
                if ((currentHead != null)) {
                    if ((currentHead.getHstate() != HSTATE.RESIGNING)) {
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
			    logger.putPacketln(this, 
		                "Reverting to ATTAINED membership " +
				" from seeking reaffil head as head " +
				" is not resigning");
			}
                        tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);
                    }

                    /*
                     * If my Head is resigning.... let the node
                     * keep trying to re-affiliate
                     */
                    return;
                } else {

                    /*
                     * I have lost my head and can't seem to reaffilaite...
                     * This node should probably resign.
                     */

                    /*
                     * The head NEEDS to Wait or keep trying for 2 minutues....
                     */
                    gblk.setHstate(HSTATE.RESIGNING);

                    return;
                }
            }
        }

        /*
         * The following is the generic case like when the TRAMState is
         * SEEKING_HA or SEEKING_MTHA state or could be the case of
         * RE_AFFILIATION state with no direct member count.
         */
        if ((bestHead == null) && (currentHead != null)) {

            /*
             * May be the node has detected a better head and it does not have
             * members of its own. If this node is performing the role of
             * a head, then the code should not be coming here.... it
             * should return earlier on!!!
             */
            try {
                bestHead = getBestSuitedHeadOnTTL(currentHead.getTTL());
            } catch (NoSuchElementException ne) {

                /*
                 * No suitable head to re-affiliate, change the TRAM state
                 * to Affiliated if the current state is SEEKING_REAFFILHEAD.
                 * and return - take care of reloading the timer to send
                 * HA messages.
                 */
                if ((tramblk.getTRAMState() 
                        == TRAM_STATE.SEEKING_REAFFIL_HEAD)) {
                    if ((currentHead.getHstate() != HSTATE.RESIGNING)) {

			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
			    logger.putPacketln(this, 
				"CHANGING BACK to ATTAINED!!");
			}
			TRAMTransportProfile tp = 
			    tramblk.getTransportProfile();
                        tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);
			if (tp.getTreeFormationPreference(true) ==
			    TRAMTransportProfile.TREE_FORM_HA) {
			    loadTimerToSendHaPkt();
			} else {
			    if ((tp.getTreeFormationPreference(true) ==
				 TRAMTransportProfile.TREE_FORM_MTHA) &&
				(dataTxmStarted == false)) {
				loadTimerToSendHaPkt();	
			    }
			}
                        return;
                    }
                }
            }
        }

        /*
         * if the bestHead is still null, one last chance to see if
         * an head can be picked. Typically, if the bestHead is still
         * null it means that this node is either attempting to affiliate
         * with a head for the first time; or its affiliated head is resigning
         * and this node has no members of its own; or this node has found
         * a better head and has no members of its own, hence its
         * re-affiliating.
         */
        if (bestHead == null) {
            try {
                bestHead = getBestSuitedHeadOnTTL();
            } catch (NoSuchElementException ne) {

                /*
                 * No suitable head to affiliate. Remain in the same
                 * State and try after a while.
                 */
                return;
            }
        }

        /*
         * Now the bestHead should indeed have the reference to the best
         * suited head
         */
        if (tramblk.getTRAMState() == TRAM_STATE.SEEKING_REAFFIL_HEAD) {
            tramblk.setTRAMState(TRAM_STATE.REAFFIL_HEAD_BINDING);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Changing to REAFFIL HEAD BINDING" + 
                    " Selected " + bestHead.getAddress() + 
		    " " + bestHead.getPort() +
		    " As Reaffiliation Head");
	    }
        } else {
            tramblk.setTRAMState(TRAM_STATE.HEAD_BINDING);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
	    	    "Changing to HEAD BINDING" +
                    " Selected " + bestHead.getAddress() + 
		    " " + bestHead.getPort() +
		    " As Head");
	    }
        }

        toBeHead = bestHead;

        backUpHeads.removeElement(bestHead);

        hbReTxmitCount = 0;

        sendHbPacket();
    }

    /**
     * method to select best suited head from the backupHead list based
     * on TTL
     */
    private synchronized HeadBlock getBestSuitedHeadOnTTL() 
            throws NoSuchElementException {

	/*
         * get the best Head in the list based on TTL and return a
         * reference to the block.
         */
        HeadBlock hbPreferred = (HeadBlock) (backUpHeads.firstElement());
        int preferredRxLevel = hbPreferred.getRxLevel();
        int preferredTTL = ((int) (hbPreferred.getTTL())) & 0xff;
        int tmpTTL = 0, tmpRxLevel = 0;

        for (int i = 1; i < backUpHeads.size(); i++) {
            try {
                HeadBlock hbTmp = (HeadBlock) backUpHeads.elementAt(i);
                tmpTTL = ((int) (hbTmp.getTTL())) & 0xff;
                tmpRxLevel = hbTmp.getRxLevel();

                if (tmpTTL < preferredTTL) {
                    preferredTTL = tmpTTL;
                    hbPreferred = hbTmp;
                    preferredRxLevel = tmpRxLevel;
                } else {

                    /*
                     * check if the TTLs are equal.... prefer the head
                     * block with lower RXLevel.
                     */
                    if ((tmpTTL == preferredTTL) && 
			(tmpRxLevel < preferredRxLevel)) {
                        hbPreferred = hbTmp;
                        preferredRxLevel = tmpRxLevel;
                    }
                }
            } catch (IndexOutOfBoundsException ie) {
                break;
            }
        }       // for loop ends here.

        /*
         * return the best head in the list
         */
        return hbPreferred;
    }

    /**
     * method to select best suited head from the backupHead list based
     * on TTL
     */
    private synchronized HeadBlock getBestSuitedHeadOnTTL(byte refTTL) 
            throws NoSuchElementException {
        int refTTLInInt = ((int) (refTTL)) & 0xff;
        HeadBlock hb = getBestSuitedHeadOnTTL();
        int bestHeadTTL = ((int) (hb.getTTL())) & 0xff;

        if (bestHeadTTL < refTTLInInt) {
            return hb;
        } 

        throw new NoSuchElementException();
    }

    /**
     * method to select best suited head from the backupHead list based
     * on RxLevel
     */
    private synchronized HeadBlock getBestSuitedHeadOnRxLevel() 
            throws NoSuchElementException {
        /*
         * get the best Head in the list based on RxLevel and return a
         * reference to the block.
         */
        HeadBlock hbPreferred = (HeadBlock) (backUpHeads.firstElement());
        int preferredRxLevel = hbPreferred.getRxLevel();
        int preferredTTL = ((int) (hbPreferred.getTTL())) & 0xff;
        int tmpTTL = 0, tmpRxLevel = 0;

        for (int i = 1; i < backUpHeads.size(); i++) {
            try {
                HeadBlock hbTmp = (HeadBlock) backUpHeads.elementAt(i);
                tmpTTL = ((int) (hbTmp.getTTL())) & 0xff;
                tmpRxLevel = hbTmp.getRxLevel();

                if (tmpRxLevel < preferredRxLevel) {
                    preferredTTL = tmpTTL;
                    hbPreferred = hbTmp;
                    preferredRxLevel = tmpRxLevel;
                } else {

                    /*
                     * check if the RxLevels are equal.... prefer the head
                     * block with lower TTL value.
                     */
                    if ((tmpRxLevel == preferredRxLevel) && 
			(tmpTTL < preferredTTL)) {
                        hbPreferred = hbTmp;
                        preferredTTL = tmpTTL;
                    }
                }
            } catch (IndexOutOfBoundsException ie) {
                break;
            }
        }       // for loop ends here.

        /*
         * return the best head in the list
         */
        return hbPreferred;
 
    }
    /**
     * method to select best suited head from the backupHead list based
     * on TTL
     */
    private synchronized HeadBlock getBestSuitedHeadOnRxLevel(int refRxLevel) 
            throws NoSuchElementException {
        HeadBlock hb = getBestSuitedHeadOnRxLevel();

        if (hb.getRxLevel() < refRxLevel) {
            return hb;
        } 

        throw new NoSuchElementException();
    }

    /**
     * private method to send an HA Packet
     */
    private void sendHaPacket() {
        if (gblk.getLstate() == LSTATE.LAN_MEMBER) {

	    /* 
	     * don't send HA if we're a LAN Member; this supresses extra 
	     * HAs on the lan
	     */

            return;
        } 
        if (gblk.getLstate() != 0) {
	    /*
	     * for other Lan states, don't send HAs unless the root lan 
	     * head is affiliated 
	     */
	    
            if ((rootLanHeadExists) && (!rootLanHeadIsAffiliated)) {
                return;
            } 
        }

	/*
	 * Check and suppress sending this HA if the previously sent one
	 * HA was with in 500ms and also to the same TTL range
	 */
	long timeSinceLastHa = System.currentTimeMillis() -
	                       gblk.getLastHASentTime();

//        int saveHaTTL = haTTL;

        augmentHaTTL();
	if ((timeSinceLastHa < 300) && (gblk.getLastHaTTLSent() >= haTTL)) {

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this, 
		    "Suppressing a HA. One was sent within 300ms");
	    }
	    return;

	}

        TRAMHaPacket hapkt = new TRAMHaPacket(tramblk, (byte)haTTL, false);
        DatagramPacket dp = hapkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateMulticastPacket(dp, 
                        SUBMESGTYPE.HA, haTTL);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(hapkt);
	        } catch (NullPointerException e) {}

                ms.send(dp, (byte) haTTL);
		gblk.setLastHASentTime(System.currentTimeMillis());
		gblk.setLastHaTTLSent(haTTL);

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this, "Dispatched a HA message");
		}
            }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Unable to dispatch an HA Packet");
	    }
        }

        /* also make a lan volunteer if there's been no one better */

        if (haTTL > 1) {
            lanVolunteer();
        } 
    }

    /**
     * private method to send an HA Packet, typically in response to
     * receiving an MS message.
     */
    private void sendHaPacket(byte ttl) {
        if (gblk.getLstate() == LSTATE.LAN_MEMBER) {

            // don't send HA if we're a LAN Member; this supresses extra HAs

            return;
        } 
        if (gblk.getLstate() != 0) {
	    /*
	     * for other Lan states, don't send HAs unless the root lan head 
	     * is affiliated 
	     */

            if ((rootLanHeadExists) && (!rootLanHeadIsAffiliated)) {
                return;
            } 
        }


	/*
	 * Check and suppress sending this HA if the previously sent one
	 * HA was with in 500ms and also to the same TTL range
	 */
	long timeSinceLastHa = System.currentTimeMillis() -
	                       gblk.getLastHASentTime();

	if ((timeSinceLastHa < 300) && (gblk.getLastHaTTLSent() >= ttl)) {

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) {

	        logger.putPacketln(this, 
		    "Suppressing a HA. Last to the TTL is within 300ms");
	    }
	    return;
	}

        TRAMHaPacket hapkt = new TRAMHaPacket(tramblk, ttl, false);
        DatagramPacket dp = hapkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateMulticastPacket(dp, 
                        SUBMESGTYPE.HA, ttl);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(hapkt);
	        } catch (NullPointerException e) {}

                ms.send(dp, ttl);
		gblk.setLastHASentTime(System.currentTimeMillis());
		gblk.setLastHaTTLSent(ttl);

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this, "Dispatched a HA message");
		}
            }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) {

                logger.putPacketln(this, 
		    "Unable to dispatch an HA Packet");
	    }
        }
    }

    /**
     * Private method to dispatch out LAN volunteer messages.
     *
     */

    private void lanVolunteer() {
        if ((tramblk.getTransportProfile().isLanTreeFormationEnabled()) 
                && (weAreBestLanVolunteer == true)) {

            /* Lan HAs always use a TTL of 1 */

            TRAMHaPacket lanHapkt = new TRAMHaPacket(tramblk, (byte) 1, 
                                                   needNewLanHead);
            DatagramPacket lanDp = lanHapkt.createDatagramPacket();

            try {
                if (tramblk.getSimulator() != null) {
                    tramblk.getSimulator().simulateMulticastPacket(lanDp, 
                            SUBMESGTYPE.HA, 1);
                } else {
		    try {
		        tramblk.getTRAMStats().setSendCntlMsgCounters(lanHapkt);
	            } catch (NullPointerException e) {}

                    ms.send(lanDp, (byte) 1);

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_CNTLMESG)) {

		        logger.putPacketln(this, 
			    "Sent a Lan Volunteer (HA) Packet");
		    }
                }
            } catch (IOException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG)) {

                    logger.putPacketln(this, 
			"Unable to dispatch a Lan Volunteer (HA) Packet");
		}
            }
        }
    }

    /**
     * Private method to attain the LAN head role.
     *
     */

    private void becomeLanHead() {
        if ((tramblk.getTransportProfile().isLanTreeFormationEnabled()) 
                && (gblk.getLstate() != LSTATE.LAN_HEAD)) {

            // we won the election 

            gblk.setLstate(LSTATE.LAN_HEAD);

            weAreCurrentLanHead = true;

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this, 
		    "Assumed the role of a LAN HEAD");
	    }

            lanVolunteer();
        }
    }

    /**
     * Private method to querry for a better LAN head.
     *
     */

    private boolean isBetterLanHead(TRAMHaPacket pkt) {



	/* Sender is always the best. */
	if (isAPacketFromTheDataSource(pkt) == true) {
	    return true;
	}
        /* first look for best mRole */
        if (pkt.getMrole() == MROLE.MEMBER_EAGER_HEAD) {
            if (tramblk.getTransportProfile().getMrole() 
                    == MROLE.MEMBER_RELUCTANT_HEAD) {
                return true;
            }
        } else if (tramblk.getTransportProfile().getMrole() 
                   == MROLE.MEMBER_EAGER_HEAD) {
            return false;
        }

        /* next, look for highest rxLevel */

        if (pkt.getRxLevel() < gblk.getRxLevel()) {
            return true;
        } 
        if (pkt.getRxLevel() > gblk.getRxLevel()) {
            return false;
        } 

        /* since rxLevels are the same, try addresses */

        int addressDiff;

        addressDiff = 
            pkt.getAddress().getHostAddress().
	    compareTo(myAddr.getHostAddress());

        if (addressDiff < 0) {
            return true;
        } 
        if (addressDiff > 0) {
            return false;
        } 

        /* since addresses are the ame, look at the ports */

        if (pkt.getUnicastPort() < tramblk.getUnicastPort()) {
            return true;
        } 

        return false;
    }

    /**
     * Method to validate a sender's liveliness.
     */
    public void validateSenderLiveliness() {
        long lastHeard = tramblk.getLastHeardFromTheSender();

        if (lastHeard != 0) {
            long timeSinceLastHeard = System.currentTimeMillis() - lastHeard;

            /*
             * Check if unheard from the sender for more than 250secs, then a
             * error event needs to be generated to the application.
             */
            if (timeSinceLastHeard < 250000) {
                return;
            } 

            /*
             * If not heard for more than 250 secs(4+ minutes), find out
             * if the data transmission has started. If the Data transmission
             * has started and has not heard from the sender for more than
             * 250 secs, give up waiting for the head to showup!.
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
                TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_SESSION)) {

                logger.putPacketln(this, 
		    "Sender is OFFLINE.. reporting to the Application");
	    }

            // send an event to the application.

            tramblk.getTRAMDataCache().handleSessionDown();

            AbortTRAM abtram = new AbortTRAM("AbortTRAM", tramblk);
        }
    }

    /**
     * private method to send an MS Packet
     */
    private void sendMsPacket() {
	validateSenderLiveliness(); // make sure the sender is still alive.

        augmentMsTTL();

        TRAMMsPacket mspkt = new TRAMMsPacket(tramblk, 
                                            (int) System.currentTimeMillis(), 
                                            (byte) msTTL);
        DatagramPacket dp = mspkt.createDatagramPacket();

        try {
            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateMulticastPacket(dp, 
                        SUBMESGTYPE.MS, msTTL);
            } else {
		try {
		    tramblk.getTRAMStats().setSendCntlMsgCounters(mspkt);
	        } catch (NullPointerException e) {}

                ms.send(dp, (byte) msTTL);
            }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_CNTLMESG)) {

	        logger.putPacketln(this, "Dispatched a MS Packet");
	    }
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) { 

                logger.putPacketln(this, 
		    "Unable to dispatch an MS Packet");
	    }
        }
    }

    /**
     * private method to send an HB Packet
     */
    private void sendHbPacket() {
        try {
            TRAMHbPacket hbpkt = new TRAMHbPacket(tramblk, toBeHead.getTTL());
            hbpkt.setAddress(toBeHead.getAddress());
            hbpkt.setPort(toBeHead.getPort());

            DatagramPacket dp = hbpkt.createDatagramPacket();
            DatagramSocket so = tramblk.getUnicastSocket();

            try {
                if (tramblk.getSimulator() != null) {
                    tramblk.getSimulator().simulateUnicastPacket(dp);
                } else {
		    try {
		        tramblk.getTRAMStats().setSendCntlMsgCounters(hbpkt);
	            } catch (NullPointerException e) {}

                    so.send(dp);
                }

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this, 
		        " Sending a head bind packet to " + 
		        toBeHead.getAddress() + " " + 
		        toBeHead.getPort());
		}
            } catch (IOException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
		    TRAMLogger.LOG_CNTLMESG)) {

                    logger.putPacketln(this, 
		        "Unable to dispatch an HB Packet");
		}
            }
        } catch (NullPointerException ne) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG)) {

	        logger.putPacketln(this, 
		    " Unable to send HB message. No Head Block");
	    }
	}
    }

    /*
     * private methods to augmentHaTTL value.
     */

    private synchronized void augmentHaTTL() {
        TRAMTransportProfile tp = tramblk.getTransportProfile();

        /*
         * Compare against the session TTL. If the value is already
         * equal to session TTL, then no need to augment.
         */
        if (haTTL == (((int) tp.getTTL()) & 0xff)) {
            return;
        } 
        if (haTTL == (((int) tp.getHaTTLLimit()) & 0xff)) {
            return;
        } 

        /*
         * First checkout if HA increments are required to be
         * suppressed. If so DON'T augment the HA TTL
         * Just return.
         */
        if (isHaIncrementSuppressionRequired() == true) {
            decrementHaIncrementSuppressionCounter();

            return;
        }
        if (haTTL < (((int) tp.getTTL()) & 0xff)) {
            haTTL += (((int) (tp.getHaTTLIncrements())) & 0xff);
        } 
        if (haTTL > (((int) tp.getTTL()) & 0xff)) {
            haTTL = (((int) tp.getTTL()) & 0xff);
        } 
        if (haTTL > (((int) tp.getHaTTLLimit()) & 0xff)) {
            haTTL = (((int) tp.getHaTTLLimit()) & 0xff);
        } 
    }

    /*
     * private methods to augmentMsTTL value.
     */

    private synchronized void augmentMsTTL() {
        TRAMTransportProfile tp = tramblk.getTransportProfile();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, "msTTL is " + msTTL +
		" session TTL is " + (((int) tp.getTTL()) & 0xff));
	}

        /*
         * Compare against the session TTL. If the value is already
         * equal to session TTL, then no need to augment.
         */
        if (msTTL == (((int) tp.getTTL()) & 0xff)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "msTTL is at max value of " + msTTL);
	    }

            return;
        } 
        if (msTTL < (((int) tp.getTTL()) & 0xff)) {
            msTTL += (((int) (tp.getMsTTLIncrements())) & 0xff);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "msTTL incremented by " + 
		    (((int) (tp.getMsTTLIncrements())) & 0xff) +
		    " to " + msTTL);
	    }

        } 
        if (msTTL > (((int) tp.getTTL()) & 0xff)) {
            msTTL = (((int) tp.getTTL()) & 0xff);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "msTTL reset to " +
		    (((int) tp.getTTL()) & 0xff));
	    }
        } 
    }

    /*
     * private method to update the Repair ttl value
     * Chooses the largest TTL value to be the repair TTL value.
     */

    private synchronized void updateRepairTTL() {
        MemberBlock mb = null;
        int ttl = 0;

        try {
            mb = gblk.getMember(0);
            ttl = ((int) mb.getTTL()) & 0xff;
        } catch (IndexOutOfBoundsException ie) {
            if (gblk.getRetransmitTTL() != 0) {
                gblk.setRetransmitTTL((byte) 0);
            } 
            return;
        }

        int compTTL = 0;

        for (int i = 1; i < gblk.getDirectMemberCount(); i++) {
            try {
                mb = gblk.getMember(i);
                compTTL = ((int) mb.getTTL()) & 0xff;

                if (compTTL > ttl) {
                    ttl = compTTL;
                } 
            } catch (IndexOutOfBoundsException ie1) {
                break;
            }
        }

        compTTL = ((int) gblk.getRetransmitTTL()) & 0xff;

        if (ttl > compTTL) {
            gblk.setRetransmitTTL((byte) ttl);
        } 
    }

    /**
     * Test method to check if HaincrementSuppression is required.
     */
    private synchronized boolean isHaIncrementSuppressionRequired() {
        if (haIncrementSuppressionCounter == 0) {
            return false;
        } 

        return true;
    }

    /**
     * Method to decrement the HAincrement suppression counter.
     * A method has been defined as against directly writing to
     * variable for reasons related to synchronization.
     */
    private synchronized void decrementHaIncrementSuppressionCounter() {
        if (haIncrementSuppressionCounter != 0) {
            haIncrementSuppressionCounter--;
        } 
    }

    /**
     * Method to load the default HA increment suppression counter.
     * A method has been defined as against directly writing to
     * variable for reasons related to synchronization.
     */
    private synchronized void loadHaIncrementSuppressionCounter() {
        haIncrementSuppressionCounter = 7;      // HardCode Value.
    }

    /**
     * public method to handle head loss condition. This method will
     * be invoked by the head monitoring process upon detecting
     * that the dependent head is being unresponsive.
     * 
     * 
     */
    public synchronized void handleHeadLoss() {
        long timerValue = 0;


	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
		"Head Loss DETECTED, Re-affiliating");
	}

        /*
         * If this node is currently re-affiliated, should it just make the
         * re-affiliated head to be the primary head?
         * I think it is a good thing to do .... this is what
         * code is currently made to do.
         */
        if (tramblk.getTRAMState() == TRAM_STATE.REAFFILIATED) {
            makeReAffilHeadToBeMainHead();

            return;
        }

        /*
         * Just to make an graceful exit incase the path from the head is
         * broken but the path from member to head is still okay.
         */
        sendTerminateMembershipMessage(gblk.getHeadBlock());

        /*
         * If the head has not successfully re-affiliated, do the following.
         */
        if (gblk.getDirectMemberCount() == 0) {
            if (haInterval == 0) {

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Transitioning to MTHA membership");
		}
                tramblk.setTRAMState(TRAM_STATE.SEEKING_MTHA_MEMBERSHIP);
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
		        "Transitioning to HA Membership");
		}
                tramblk.setTRAMState(TRAM_STATE.SEEKING_HA_MEMBERSHIP);
            }
        } else {

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Transitioning to SEEKING RE_AFFIL HEAD.Hd loss");
	    }

            tramblk.setTRAMState(TRAM_STATE.SEEKING_REAFFIL_HEAD);
        }

        /*
         * clear the dependent head.
         */
        gblk.setHeadBlock(null);

        /*
         * Checkout if there are any backUpHead entries... If so
         * invoke Head selection process.
         */
        if (backUpHeads.size() != 0) {
            performHeadSelection();
        }

        switch (tramblk.getTRAMState()) {

        case TRAM_STATE.HEAD_BINDING: 

            // load a timer to wait for AM message.

            timer.reloadTimer(HB_TIMEOUT);

            break;

        case TRAM_STATE.SEEKING_HA_MEMBERSHIP: 
            loadTimerToReceiveHaPacket();

            break;

        case TRAM_STATE.SEEKING_MTHA_MEMBERSHIP: 
            sendMsPacket();
            timer.reloadTimer(tramblk.getTransportProfile().getMsRate());

            break;

        default: 
            break;
        }
    }

    /**
     * public method to handle affiliated head loss condition. This
     * method will be invoked by the head monitoring process upon
     * detecting that the re-affiliated head is being unresponsive.
     * 
     */
    public synchronized void handleReAffiliatedHeadLoss() {
        long timerValue = 0;

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
		"Re-AffiliatedHead Loss DETECTED. " + 
		"Transitioning to Attained membership");
	}
        tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);

        /*
         * Just to make an graceful exit incase the path from the head is
         * broken but the path from member to head is still okay.
         */
        sendTerminateMembershipMessage(toBeHead);

        toBeHead = null;
    }

    /**
     * Method to switch reAffilHead to be the Primary Head.
     */
    public void makeReAffilHeadToBeMainHead() {

        /*
         * make a graceful switchover...
         */
        sendTerminateMembershipMessage(gblk.getHeadBlock());

        /* Make the TobeHead to actually be the HEAD. */

        gblk.setHeadBlock(toBeHead);

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
		"Changing State to ATTAINED MEMBERSHIP");
	}
        tramblk.setTRAMState(TRAM_STATE.ATTAINED_MEMBERSHIP);

        // Update this node's RxLevel.

        gblk.setRxLevel(toBeHead.getRxLevel() + 1);

        // wipe out toBeHead reference.
        toBeHead = null;

	/* now tell everybody interested about attaining membership */
	notifyMembershipListeners(new TRAMMembershipEvent(this));

    }

    /**
     * Sends Membership Termination message to the head specified.
     */
    private void sendTerminateMembershipMessage(HeadBlock hb) {
        TRAMMemberAck mack = tramblk.getMemberAck();

        if ((mack != null) && (hb != null)) {
            mack.sendAck((byte) TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP, 
                         hb);
        }
    }

    /**
     * public method to handle member loss condition. This method will
     * be invoked by the member monitoring process(Hello Thread) upon
     * detecting that a member has stopped reporting ACKs.
     * Dispatches a Hello-Uni with a termination bit in the flag set,
     * frees up ALL the packets that waiting to be ACK'd by this member
     * and finally removes the member from the member list. If the
     * state of the head was previously NOT_ACCEPTING members or ACCEPTING
     * only heads then the state needs to be appropriatly updated.
     * 
     */
    public synchronized void handleMemberLoss(MemberBlock mb) {
        long timerValue = 0;

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
		"Member Loss DETECTED. Disowning Member " + 
		mb.getAddress() + " " + mb.getPort());
	}

        sendHelloUniPacket(mb, TRAMHelloUniPacket.FLAGBIT_DISOWNED);
        disownMember(mb, false);
    }

    /**
     *  Method to display Member details.
     */
    public void showMemberInfo() {
        MemberBlock mb = null;

        for (int i = 0; i < gblk.getDirectMemberCount(); i++) {
            try {
                mb = gblk.getMember(i);      // next member

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_SESSION)) {
                    logger.putPacketln(this, 
		        mb.getAddress() + 
		        " Direct Mem " + mb.getDmemCount() + 
		        ", Indir Mem " + mb.getIndmemCount() + 
		        ", lastAck " + mb.getLastPacketAcked() + 
		        ", High seq " + mb.getHighestSequenceAllowed() + 
		        ", Flow control info " + mb.getFlowControlInfo() +
		        ", Group flow control info " + 
		        tramblk.getRateAdjuster().getGroupFlowControlInfo());
		}
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
    }

    /**
     * private method to disown a member.
     * frees up ALL the packets that waiting to be ACK'd by this member
     * and finally removes the member from the member list. If the
     * state of the head was previously NOT_ACCEPTING members or ACCEPTING
     * only heads then the state needs to be appropriatly updated.
     */
    private synchronized void disownMember(MemberBlock mb,
	boolean reaffiliated) {

	showMemberInfo();

        gblk.removeMember(mb);

	if (!reaffiliated)
	    tramblk.getTRAMStats().addPrunedMembers();

	tramblk.setHighestSequenceAllowed(gblk.getHighestSequenceAllowed());

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION | TRAMLogger.LOG_CONG)) {

	    String s = "";

	    if (reaffiliated)
		s = "(REAFFILIATED) ";

            logger.putPacketln(this, 
                "Disown Member " + s + mb.getAddress() + 
		" " + mb.getPort() + 
		" highest allowed before disown " + 
		tramblk.getHighestSequenceAllowed() +
		" highest allowed after disown " + 
		tramblk.getHighestSequenceAllowed());
	}

        /*
         * Remove the member from the list
         * check if HSTATE needs to be changed to accepting
         * Accepting members.
         */
        int directMemberCount = gblk.getDirectMemberCount();
        TRAMTransportProfile tp = tramblk.getTransportProfile();

        switch (gblk.getHstate()) {

        case HSTATE.ACCEPTING_POTENTIALHEADS_ONLY: 
            if ((directMemberCount + getMembersOnlyInToBeMembers()) 
                    < tp.getMaxNonHeads()) {
                gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);
            }

            break;

        case HSTATE.NOT_ACCEPTING_MEMBERS: 
            if ((directMemberCount + getMembersOnlyInToBeMembers()) 
                    < tp.getMaxNonHeads()) {
                gblk.setHstate(HSTATE.ACCEPTING_MEMBERS);
            } else {
                if (directMemberCount < tp.getMaxMembers()) {
                    gblk.setHstate(HSTATE.ACCEPTING_POTENTIALHEADS_ONLY);
                }
            }

            break;

        case HSTATE.RESIGNING: 

            /*
             * Maybe the node lost its head and could not re-affiliate
             * due to the having members and the usual RxLevel catch....
             * Now that the members are gone, let see if the node can
             * re-affiliate.
             */
            if ((directMemberCount == 0)) {
                if ((gblk.getHeadBlock() == null)) {
                    gblk.setHstate(HSTATE.INIT);
                } else {

                /*
                 * Maybe the node was resigning for optimization
                 * reasons - like the condition where it was
                 * discovered that there were too many heads and
                 * this node is resigning infavor of some other
                 * head. Lets just currently leave the HSTATE
                 * at resigning currently - may be we will have to
                 * add a new HSTATE that will specifically indicate that
                 * the node should be a second preference in the region.
                 * If we revert the HSTATE back to ACCEPTING Members
                 * the HAs will start flowing out and may infact defeat
                 * the whole purpose triggering the resignation!.
                 * 
                 */
                }
            }

            break;

        case HSTATE.ACCEPTING_MEMBERS: 

        default: 

            // No operation.

            break;
        }
    }

    /**
     * Public method to terminate the Group Management process.
     */
    public void terminate() {
	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
		"Terminating Group Management thread");
	}
        
        PrintWriter out = null;
        InetAddress ia = null;
        String sa = null;
        InetAddress head_ia = null;
        TRAMTransportProfile tp = tramblk.getTransportProfile();
        
        // print tree info to a file for future use
        if (tp.getTreeFormationPreference(false)
            >= TRAMTransportProfile.TREE_FORM_STATIC_RW) {        	
            try {
                out = new PrintWriter(new FileWriter(cfgfile));

		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
		    logger.putPacketln(this, "Writing tree config file");
		}
            } catch (IOException e) {
		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
		    logger.putPacketln(this, e.toString());
		}
            }
            HeadBlock hb = gblk.getHeadBlock();
            if (gblk.getDirectMemberCount() > 0) {
                // I am a head, so write down my own addr, with ttl = 0
                out.println(myAddr + " 0");
            }
            if (hb != null) {
                head_ia = hb.getAddress();
                sa = head_ia.getHostAddress();
                out.println(sa + " " + hb.getTTL());
            }
            for (int i = 0; i < backUpHeads.size(); i++) {
                try {
                    hb = (HeadBlock) backUpHeads.elementAt(i);
                    ia = hb.getAddress();
                    if (ia.equals(head_ia) != true) {
                        sa = ia.getHostAddress();
                        out.println(sa + " " + hb.getTTL());
                    }
                } catch (IndexOutOfBoundsException ie) {}        
            }

            if (out != null)
                out.close();
        }
	
	/*
	 * Make the HelloThread to send a simpleHello stating that
	 * it is resigning.
	 */
	if (gblk.getDirectMemberCount() != 0) {
	    gblk.setHstate(HSTATE.RESIGNING);
	    try {
		tramblk.getHelloThread().sendSimpleHello();
	    } catch (NullPointerException ne) {}
	}
        if ((timer != null) && (timer.isAlive())) {
            timer.stopTimer();
            timer.killTimer();
        }

        tramblk.setGroupMgmtThread(null);

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

        done = true;

        interrupt();
    }

    /**
     * private method to add a HeadBlock to the backupHead list.
     * 
     * @param hb The head block that is to be added
     * @return false if not added to the headlist, true is added to the
     *          backup head list.
     */
    private synchronized boolean addedToTheBackupHeadList(HeadBlock hb) {
        HeadBlock hb_tmp = null;

	if (backUpHeads.size() < 10) {
	    backUpHeads.addElement(hb);
	    return true;
	}
	/* 
	 * first clean up old entries to make room.
	 */
	int i = 0;
	while (i < backUpHeads.size()) {
	    try {
		hb_tmp = (HeadBlock) backUpHeads.elementAt(i);
		long heardSince = System.currentTimeMillis() - 
		                  hb_tmp.getLastheard();
		if (heardSince > 10000) {
		    /*
		     * if not heard since 10 seconds, discard the head.
		     */
		    backUpHeads.removeElementAt(i);
		    backUpHeads.addElement(hb);
		    return true;
		}
	    } catch (IndexOutOfBoundsException ie) {
		break;
	    }
	    i++;
        }
	/*
	 * If there is still no room, then we need to replace a headBlock
	 * lets first find the worst head in the backupList.
	 * 
	 */
	HeadBlock hb_remove = null;
	try {
	    hb_remove = (HeadBlock) backUpHeads.firstElement();
	}catch (NoSuchElementException ne) {
	    backUpHeads.addElement(hb);
	    return true;
	}
        for (i = 1; i < backUpHeads.size(); i++) {
            try {
                hb_tmp = (HeadBlock) backUpHeads.elementAt(i);
		/*
		 * Check if the new heads TTL is better than what we have.
		 */
                if (hb_remove.getTTL() < hb_tmp.getTTL()) {
		    hb_remove = hb_tmp;
                } else {
		    if ((hb_remove.getTTL() == hb_tmp.getTTL()) &&
			(hb_remove.getRxLevel() < hb_tmp.getRxLevel())) {
			hb_remove = hb_tmp;
		    }
		}
            } catch (IndexOutOfBoundsException ie) {}
        }
	/*
	 * Now Check if hb_remove is better than the headblock to be added.
	 * If true, remove the hb_remove and add the hb. If not return false.
	 * Currently only TTL is used. RxLevel Can also be used... This needs
	 * to be fixed.
	 */
	if ((hb_remove.getTTL() > hb.getTTL()) || 
	    ((hb_remove.getTTL() == hb.getTTL()) && 
	    (hb_remove.getRxLevel() > hb.getRxLevel()))) {
	    backUpHeads.removeElement(hb_remove);
	    backUpHeads.addElement(hb);
	    return true;
	}
	return false;
    }

    /**
     * private method to fetch a matching HeadBlock from the backupHead
     * list.
     * 
     * @param ia Inet address of the required HeadBlock
     * @param port port number of the matching HeadBlock.
     * @return The matching HeadBlock.
     * @Exception throws NoSuchElementException if no matching entry is
     * found.
     */
    private HeadBlock getBackupHead(InetAddress ia, 
                                    int port) throws NoSuchElementException {
        HeadBlock hb = null;

        for (int i = 0; i < backUpHeads.size(); i++) {
            try {
                hb = (HeadBlock) backUpHeads.elementAt(i);

                if ((ia.equals(hb.getAddress())) && (port == hb.getPort())) {
                    return hb;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }

        throw new NoSuchElementException();
    }

    /*
     * private method to count the number of potential members with
     * Member Only Mrole in the toBeMember List
     * 
     * @return count of Members that of MEMEBR_ONLY Mrole in the toBeMember
     * list.
     */

    private int getMembersOnlyInToBeMembers() {
        int memberOnlyCount = 0;

        for (int k = 0; k < toBeMembers.size(); k++) {
            MemberBlock mbTmp = null;

            try {
                mbTmp = (MemberBlock) toBeMembers.elementAt(k);

                if (mbTmp.getMrole() == MROLE.MEMBER_ONLY) {
                    memberOnlyCount++;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }

        return memberOnlyCount;
    }

    /**
     * public method to get ReAffiliation head block
     */
    public HeadBlock getReAffiliationHead() {
        return toBeHead;
    }

    public void printDataCounts() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
                "Packets to process = " + pktsToProcess.size());
            logger.putPacketln(this, 
                "To be members      = " + toBeMembers.size());
            logger.putPacketln(this, 
                "Back up Heads       = " + backUpHeads.size());
	}
    }

    /**
     * public method to get the current HA interval (used in the beacon message)
     */
    short getHaInterval() {
        return haInterval;
    }

    /**
     * public method to get the current HA interval (used in the data message)
     */
    short getDataHaInterval() {
        if (tramblk.getTransportProfile().getTreeFormationPreference(true) 
                == TRAMTransportProfile.TREE_FORM_HA) {
            return haInterval;
        } 

        return 0;
    }

    /*
     * Add a new membership listener.
     */

    public void addTRAMMembershipListener(TRAMMembershipListener l) {
        membershipListeners.addElement(l);
    }

    /*
     * Remove a membership listener.
     */

    public void removeTRAMMembershipListener(TRAMMembershipListener l) {
        membershipListeners.removeElement(l);
    }

    /**
     * Notify all listeners when we've obtained membership with a head.
     */
    public void notifyMembershipListeners(TRAMMembershipEvent e) {
        for (int i = 0; i < membershipListeners.size(); i++) {
            ((TRAMMembershipListener) membershipListeners.elementAt(i)).
	        receiveTRAMMembership(e);
        }
    }

    /*
     * Method to stall until a notify is called.
     */

    private synchronized void stall() {
        try {
            wait();
        } catch (InterruptedException ie) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "Interrupted!");
	    }
        }
    }

    /*
     * Wakeup a stalled thread.
     */

    private synchronized void wake() {
        notifyAll();
    }

    /*
     * Method to compute the starting message that needs to be committed
     * to the member. 
     *
     * If the Late join preference is no data recovery, then the head will 
     * compute the starting message to be the next packet to be received. 
     *
     * If the Late join preference is limited data recovery, 
     * instead of promising the entire cache (AM includes 'left' edge)
     * promise only part of the cache.  AM would now contain
     *
     *	  right edge - (high_water_mark  -  low_water_mark)
     *	
     * In the case of a cache where 400 is low_water_mark, 800 is 
     * high_water_mark, and 1200 is full, the left edge is 500 and the right 
     * edge is 1300, we would promise packets starting at 900 rather than 500.
     *
     * If the late join preference is FULL data recovery, then the
     * head will compute the starting message to be the very first data 
     * message sent by the sender. The FULL data recovery is currently
     * not supported.
     */
    private int computeStartPacketForMember() {
	int n;

        TRAMTransportProfile tp = tramblk.getTransportProfile();

	if (tp.getLateJoinPreference() == 
	    TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY) {
	    /*
	     * Join with no recovery.  The sender does not have a
	     * TRAMMemberAck module.  Hence this special case.
	     */
	    if (tp.getTmode() == TMODE.SEND_ONLY) {
		/*
		 * Since there is no recovery of data before the join,
		 * we can set the sequence number to next packet to be sent.
		 */
                n = tramblk.getTRAMDataCache().
		    getHighestSequenceNumberinCache();
		
		if (n == 0)
		    n = 1;	// if no data has been sent yet, start at 1
	    } else {
		/*
		 * A non-sender head has a TRAMMemberAck module.
		 * Use the next sequence number to be received as
		 * the first packet that will be reliably delivered.
		 */
	        TRAMMemberAck memack = tramblk.getMemberAck();

	        TRAMSeqNumber seqNum = 
		    new TRAMSeqNumber(memack.getNextPktSeqNumberToReceive());

		n = seqNum.getSeqNumber();
	    }
	} else {

	    /*
	     * Limited recovery, set the sequence number to the 
	     *
	     * right edge - (high_water_mark  -  low_water_mark)
	     *
	     * from our cache.
	     */
	    n = ((TRAMGenericDataCache)
            (tramblk.getTRAMDataCache())).getLimitedRecoverSequenceNumber();
	}

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
	    logger.putPacketln(this, "starting seq number is " + n);
	}

	return n;
    }

}

