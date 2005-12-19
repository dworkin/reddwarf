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
 * Beacongenthread.java
 * 
 * Module Description: 
 *
 * The BeaconGenThread is responsible for generating the Beacons.
 * The thread is spawned whenever beacons are to be
 * sent to the members. The beacons are generated at
 * the predeternmined rate specified in the
 * transportprofile. Beacons are generated only by the
 * sender and at the valid TRAM states. When the TRAM_STATE
 * transitions to a state where in beacons are not
 * required, the thread exits.
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.multicast.reliable.authentication.*;
import java.security.*;

class BeaconGenThread extends Thread {
    private TRAMControlBlock tramblk = null;
    private static final String name = "TRAM BeaconGenThread";
    private TRAMLogger logger = null;
    private GroupMgmtThread grpThread = null;
    private boolean done = false;

    /*
     * NOTE:
     * This is a temporary fix and will remain as long as the
     * multicast socket send() with TTL bug exists.
     * When the bug is fixed the following code needs to be removed
     */
    private MulticastSocket ms = null;

    /**
     * BeacoGenThread constructor.
     * 
     * @param TRAMControlBlock - the tram control block.
     */
    public BeaconGenThread(TRAMControlBlock tramblk, 
                           GroupMgmtThread grpThread) {
        super(name);

        this.tramblk = tramblk;
        this.grpThread = grpThread;
        logger = tramblk.getLogger();

        /*
         * NOTE	:
         * This	  is a temporary fix and will remain as long as the
         * multicast socket send() with TTL bug exists.
         * When the bug is fixed the following try and catch needs
         * to be removed.
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

        this.setDaemon(true);
        this.start();
    }

    /**
     * Up on scheduled to run, the thread waits for the TRAM
     * initialization to complete. Completion of initialization is
     * determined with the TRAM state transition from INIT to either
     * AWAITING_BEACON or PREDATA_BEACON.
     */
    public void run() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "starting BeaconGenThread");
	}

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        /*
         * NOTE:
         * This is a temporary fix and will remain commented as long as the
         * multicast socket send() with TTL bug exists.
         * When the bug is fixed the following code needs to be uncommented
         */

        // MulticastSocket ms = tramblk.getMulticastSocket();

        BeaconPacket bpkt = null;
        long lastBeaconSentTime = 0;
        int signatureLength = 0;

        if (tramblk.getAuthenticationModule() != null) {
            signatureLength = 
                tramblk.getAuthenticationModule().getSignatureSize();
        }

        while (!done) {
            byte state = tramblk.getTRAMState();
            long sleep_time = tp.getBeaconRate();

            bpkt = null;

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "In While loop: State " + state);
	    }

            switch (state) {
            case TRAM_STATE.INIT: 

                /*
                 * do nothing - sleep at 100 millsecs intervals and check if
                 * the state has changed  to sendout the beacon.
                 */
                sleep_time = 100;

                break;

            case TRAM_STATE.PRE_DATA_BEACON: 
            case TRAM_STATE.CONGESTION_IN_EFFECT: 

                // Generate Pre data beacon

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, "Generating a Beacon");
		}

                bpkt = new BeaconPacket(tramblk, signatureLength, 
                                        grpThread.getHaInterval(), 
					(byte) 0);

                bpkt.setAddress(tp.getAddress());
                bpkt.setPort(tp.getPort());

                break;

            case TRAM_STATE.DATA_TXM: 

                /* if we haven't sent data for a while, send a beacon */

                TRAMHeadAck headAck = tramblk.getHeadAck();

                if (headAck == null) {
                    break;
                } 

                /*
                 * First let us find out if a data packet has been dispatched
                 * after the last beacon was sent. If so then check if the
                 * time since the last data packet was sent is more than
                 * 3 seconds(was previously set to 30). If so a filler
                 * beacon needs to be sent.
                 * If No data packet was sent after the last beacon, check
                 * if the time since the last beacon exceeds 30 seconds.
                 * If so, send a filler beacon.
                 * 
                 */
                long lastSentTime = 
                    tramblk.getOutputDispThread().getLastPktSentTime();

                if (lastSentTime < lastBeaconSentTime) {
                    lastSentTime = lastBeaconSentTime;
                } 

                /*
                 * Change hard coded 3 secs to something better or
                 * more suitable based on the data rate.
		 *
		 * Added code the set the forgetBefore seq number in
		 * the outgoing beacon message.
                 */
                if ((System.currentTimeMillis() - lastSentTime) > 3000) {
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		        logger.putPacketln(this, 
			    "Sender hasn't sent in 3 seconds, " +
			    " Sending a beacon for seq " +
			    tramblk.getTRAMDataCache().
				getHighestSequenceNumber());
		    }

                    // ship out a beacon

                    bpkt = new BeaconPacket(tramblk, signatureLength, 
                                            grpThread.getDataHaInterval(), 
                                            BeaconPacket.FLAGBIT_FILLER);

                    bpkt.setSeqNumber(
			tramblk.getTRAMDataCache().getHighestSequenceNumber());
		    bpkt.setForgetBeforeSeqNum(
		        tramblk.getLastKnownForgetBeforeSeqNum()); 

                    bpkt.setAddress(tp.getAddress());
                    bpkt.setPort(tp.getPort());
                }

                break;

            case TRAM_STATE.POST_DATA_BEACON: 

                /*
                 * Generate Post Data beacons.
                 */
                bpkt = new BeaconPacket(tramblk, signatureLength, 
                                        grpThread.getDataHaInterval(), 
                                        BeaconPacket.FLAGBIT_TXDONE);

                // bpkt.setSeqNumber(
		//    tramblk.getTRAMDataCache().getHighestSequenceNumber());
                bpkt.setSeqNumber(tramblk.getLastFirstTimeTxmSequenceNumber());
				bpkt.setForgetBeforeSeqNum(
			tramblk.getLastKnownForgetBeforeSeqNum());
                bpkt.setAddress(tp.getAddress());
                bpkt.setPort(tp.getPort());

                break;

            default: 
                done = true;
                sleep_time = 0;

                break;
            }       // switch ends

            // Dispatch the beacon packet if formed.

            if (bpkt != null) {
                AuthenticationModule authMod = 
                    tramblk.getAuthenticationModule();

                if (authMod != null) {
                    int mesgLen = TRAMPacket.TRAMHEADERLENGTH 
                                  + BeaconPacket.HEADERLENGTH;
                    byte[] signature;
                    byte[] buf = bpkt.getBuffer();

                    try {
                        signature = authMod.sign(buf, 0, mesgLen);

			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			    TRAMLogger.LOG_SECURITY)) {

                            logger.putPacketln(this, 
			        "Signature Length is " + signature.length + 
				"Mesg Length is " + mesgLen +
				" BufferLen is " + buf.length);
			}

                        bpkt.writeBuffer(signature, signature.length, 0);
                    } catch (SignatureException se) {
		        if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
			    TRAMLogger.LOG_SECURITY)) {

                           logger.putPacketln(this, 
			       "Could not Sign a Beacon Packet!!");
			}

                        continue;
                    }
                }

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, "Creating the datagram");
		}

                DatagramPacket dp = bpkt.createDatagramPacket();

                try {
                    if (tramblk.getSimulator() != null) {
                        tramblk.getSimulator().simulateMulticastPacket(dp, 
                                SUBMESGTYPE.BEACON, tp.getTTL());
                    } else {
			try {
		    	    tramblk.getTRAMStats().setSendCntlMsgCounters(bpkt);
	        	} catch (NullPointerException e) {}

                        ms.send(dp, tp.getTTL());
                    }
                    lastBeaconSentTime = System.currentTimeMillis();

		    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
			TRAMLogger.LOG_CNTLMESG)) {

                        logger.putPacketln(this, 
			    "Sending a Beacon with TTL " + tp.getTTL());
		    }

                } catch (IOException e) {
		    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
			TRAMLogger.LOG_CNTLMESG)) {

                        logger.putPacketln(this, 
			    "unable to send Beacon packet ");
		    }
                }
            }
            if (!done) {
                try {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this,
                            "Sleeping for " + sleep_time + " Millsecs");
		    }
                    sleep(sleep_time);
                } catch (InterruptedException e) {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this,
                            "BeaconGenThread -Sleep Interpt Exception");
		    }
                }
            }
        }           // while loop ends.

        // Beacon no longer needs to be generated. Clear the thread.

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, "Stopping Beacon GenThread");
	}
        tramblk.setBeaconGenThread((BeaconGenThread) null);

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
    }       // run method ends.

    /*
     * Call this method to terminate the Beacon thread.
     */

    public void terminate() {
        done = true;

        interrupt();
    }

}       // BeaconGenThread class ends.

