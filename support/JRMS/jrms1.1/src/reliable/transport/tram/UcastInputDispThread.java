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
 * UcastInputDispThread.java
 * 
 * Module Description: A Daemon thread
 * 
 * This module implements the unicast input dispatcher in TRAM. All unicast
 * packets(control messages) are received in this object and dispatched to
 * all packet listeners. There is a different listener interface for all
 * TRAM packets.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import java.util.Vector;
import java.io.IOException;

class UcastInputDispThread extends Thread {
    private static final int MAXCNTL_PKTSIZE = 1350;

    /*
     * enough slop
     * left to make sure
     * that
     * UDP,IP and link
     * headers can be
     * added.
     */
    private TRAMControlBlock tramblk = null;
    private TRAMLogger logger;
    private static final String name = "TRAM UcastInputDispThread";
    private Vector helloUniListeners = new Vector(10, 10);
    private Vector ackListeners = new Vector(10, 10);
    private Vector hbListeners = new Vector(10, 10);
    private Vector amListeners = new Vector(10, 10);
    private Vector rmListeners = new Vector(10, 10);
    private Vector cgListeners = new Vector();
    private boolean done = false;

    /**
     * Constructor.
     * 
     */
    public UcastInputDispThread(TRAMControlBlock tramblk) {
        super(name);

        this.setDaemon(true);

        this.tramblk = tramblk;
        this.logger = tramblk.getLogger();

        this.start();
    }

    /**
     * Run method
     */
    public void run() {
        if (tramblk.getSimulator() == null) {
            DatagramSocket us = tramblk.getUnicastSocket();

            while (!done) {
                try {
                    byte data[] = new byte[MAXCNTL_PKTSIZE];
                    DatagramPacket dp = new DatagramPacket(data, 
                                                           MAXCNTL_PKTSIZE);
		    if (dp == null)
			continue;
                    us.receive(dp);
                    dispatchPacket(dp);
                } catch (IOException e) {
                    if (!done) {
                        e.printStackTrace();
                    } 
                } catch (Exception e) {
		    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                        logger.putPacketln(this,
			    "Got an error in the ucast run loop");
		    }
                    e.printStackTrace();
                }
            }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, "Thread exit!!");
	}
    }

    /**
     * Dispatches unicast messages based on the Message and Sub message type
     * fields.
     * 
     * @param  incoming unicast datagram packet.
     * 
     */
    void dispatchPacket(DatagramPacket dp) {

	/*
	 * First check if the Version number of the packet is the right one.
	 * If an older/unsupported version number of thepacket is received,
	 * drop the packet and return
	 * Currently no backwards compatibility support is provided. This 
	 * needs to be added later on. 
	 */
	if (TRAMPacket.getVersionNumber(dp) != (int)TRAMPacket.TRAM_VERSION) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
	        logger.putPacketln(this,
		    "Dropping packet due to Version# mismatch. Expect " +
		    TRAMPacket.TRAM_VERSION +" Got " +
		    TRAMPacket.getVersionNumber(dp));
	    }
	    return;
	}
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
		"Got a packet from " + dp.getAddress() +
		" Type    = " + TRAMPacket.getMessageType(dp) +
		" SubType = " + TRAMPacket.getSubType(dp));
	}
	/*
	 * Checkout if the Session Id matches. If the session Id is not set
	 * (during the init phase) let the packet be processed. If sessionId
	 * is set and if the the packet session Id does not match, then
	 * discard the packet.
	 */

	int sessionId = tramblk.getSessionId();
	if (sessionId != 0) {
	    if (TRAMPacket.getId(dp) != sessionId) {
		// Discard the packet and Return.
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this, 
			"Discarding a UCast packet from " + 
			dp.getAddress() + " for SessionId Mismatch");
		}
		return;
	    }
	}

        /*
         * Drop all non unicast control messages.
         */
        if (TRAMPacket.getMessageType(dp) == MESGTYPE.UCAST_CNTL) {
            switch (TRAMPacket.getSubType(dp)) {
	    case SUBMESGTYPE.HELLO_Uni: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

		    logger.putPacketln(this, 
			"Got a Hello uni message from " + 
			dp.getAddress());
		}

		TRAMHelloUniPacket hpk = new TRAMHelloUniPacket(dp);

		try {
		    TRAMHelloUniPacketEvent e = 
			new TRAMHelloUniPacketEvent(this, hpk);

		    notifyTRAMHelloUniPacketEvent(e);
		} catch (Exception e) {
		    e.printStackTrace();

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		        TRAMLogger.LOG_CNTLMESG)) {

		        logger.putPacketln(this, e.getMessage());
		    }
	        }

		break;

	    case SUBMESGTYPE.HB: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this,
			"Got a HB message from " + dp.getAddress());
		}

		TRAMHbPacket hb_pkt = new TRAMHbPacket(dp);

		try {
		    TRAMHbPacketEvent e = 
			new TRAMHbPacketEvent(this, hb_pkt);
			
		    notifyTRAMHbPacketEvent(e);
		} catch (Exception e) {
		    e.printStackTrace();

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		        TRAMLogger.LOG_CNTLMESG)) {

			logger.putPacketln(this, e.getMessage()); 
		    }
		}

		break;

	    case SUBMESGTYPE.AM: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this,
			"Got a AM message from " + dp.getAddress());
		}

		TRAMAmPacket am_pkt = new TRAMAmPacket(dp);
		    
		try {
		    TRAMAmPacketEvent e = new TRAMAmPacketEvent(this, am_pkt);

		    notifyTRAMAmPacketEvent(e);
		} catch (Exception e) {
		    e.printStackTrace();

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		        TRAMLogger.LOG_CNTLMESG)) {

			logger.putPacketln(this, e.getMessage());
		    }
		}
		    
		break;
		
	    case SUBMESGTYPE.RM: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this,
			"Got an RM message from " + dp.getAddress());
		}

		TRAMRmPacket rm_pkt = new TRAMRmPacket(dp);
		    
		try {
		    TRAMRmPacketEvent e = new TRAMRmPacketEvent(this, rm_pkt);

		    notifyTRAMRmPacketEvent(e);
		} catch (Exception e) {
		    e.printStackTrace();

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		        TRAMLogger.LOG_CNTLMESG)) {

			logger.putPacketln(this, e.getMessage());
		    }
		}
		    
		break;

	    case SUBMESGTYPE.ACK: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_CNTLMESG)) {

		    logger.putPacketln(this,
			"Got an ACK message from " + dp.getAddress());
		}

		try {
		    TRAMAckPacket ap = new TRAMAckPacket(dp);
		    TRAMAckPacketEvent e = new TRAMAckPacketEvent(this, ap);

		    notifyTRAMAckPacketEvent(e);
		} catch (Exception e) {
		    e.printStackTrace();
		    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
			logger.putPacketln(this, e.toString());
		    }
		}

		break;

	    }
        }
    }

    /**
     * Method to add an AckPacketListener to the Unicast input dispatcher.
     * 
     * @param TRAMAckPacketListener that is requesting notifications of Ack
     * message arrivals.
     */
    public synchronized void addTRAMAckPacketListener(
						 TRAMAckPacketListener l) {
        synchronized (ackListeners) {
            ackListeners.addElement(l);
        }
    }

    /**
     * Method to remove an already registered AckPacketListener.
     * 
     * @param reference to the registered TRAMAckPacketListener.
     */
    public void removeTRAMAckPacketListener(TRAMAckPacketListener l) {
        synchronized (ackListeners) {
            ackListeners.removeElement(l);
        }
    }

    /**
     * Method to notify the arriaval of an TRAMAckPacket to all the registered
     * TRAMAckPacketListeners.
     * 
     * @param reference to the TRAMAckPacketEvent block that has the 
     * received Ack message.
     */
    public void notifyTRAMAckPacketEvent(TRAMAckPacketEvent e) {
        synchronized (ackListeners) {
            for (int i = 0; i < ackListeners.size(); i++) {
                ((TRAMAckPacketListener)
		    ackListeners.elementAt(i)).receiveAckPacket(e);
            }
        }
    }

    /**
     * Method to add an TRAMAmPacketListener to the Unicast input dispatcher.
     * 
     * @param TRAMAmPacketListener that is requesting notifications of Am
     * packet arrivals.
     */
    public synchronized void addTRAMAmPacketListener(TRAMAmPacketListener l) {
        synchronized (amListeners) {
            amListeners.addElement(l);
        }
    }

    /**
     * Method to remove an already registered AmPacketListener.
     * 
     * @param reference to the registered TRAMAmPacketListener.
     */
    public void removeTRAMAmPacketListener(TRAMAmPacketListener l) {
        synchronized (amListeners) {
            amListeners.removeElement(l);
        }
    }

    /**
     * Method to notify the arrival of an TRAMAmPacket to all the registered
     * TRAMAmPacketListeners.
     * 
     * @param reference to the TRAMAmPacketEvent block that has the received Am
     * packet.
     */
    public void notifyTRAMAmPacketEvent(TRAMAmPacketEvent e) {
        synchronized (amListeners) {
            for (int i = 0; i < amListeners.size(); i++) {

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, "found an AM listener");
		}
                ((TRAMAmPacketListener)
		    amListeners.elementAt(i)).receiveTRAMAmPacket(e);
            }
        }
    }

    /**
     * Method to add an TRAMRmPacketListener to the Unicast input dispatcher.
     * 
     * @param TRAMRmPacketListener that is requesting notifications of Rm
     * packet arrivals.
     */
    public synchronized void addTRAMRmPacketListener(TRAMRmPacketListener l) {
        synchronized (rmListeners) {
            rmListeners.addElement(l);
        }
    }

    /**
     * Method to remove an already registered RmPacketListener.
     * 
     * @param reference to the registered TRAMRmPacketListener.
     */
    public void removeTRAMRmPacketListener(TRAMRmPacketListener l) {
        synchronized (rmListeners) {
            rmListeners.removeElement(l);
        }
    }

    /**
     * Method to notify the arrival of an TRAMRmPacket to all the registered
     * TRAMRmPacketListeners.
     * 
     * @param reference to the TRAMRmPacketEvent block that has the received Rm
     * packet.
     */
    public void notifyTRAMRmPacketEvent(TRAMRmPacketEvent e) {
        synchronized (rmListeners) {
            for (int i = 0; i < rmListeners.size(); i++) {
                ((TRAMRmPacketListener)
		    rmListeners.elementAt(i)).receiveTRAMRmPacket(e);
            }
        }
    }

    /**
     * Method to add an TRAMHbPacketListener to the Unicast input dispatcher.
     * 
     * @param TRAMHbPacketListener that is requesting notifications of Hb
     * packet arrivals.
     */
    public synchronized void addTRAMHbPacketListener(TRAMHbPacketListener l) {
        synchronized (hbListeners) {
            hbListeners.addElement(l);
        }
    }

    /**
     * Method to remove an already registered HbPacketListener.
     * 
     * @param reference to the registered TRAMHbPacketListener.
     */
    public void removeTRAMHbPacketListener(TRAMHbPacketListener l) {
        synchronized (hbListeners) {
            hbListeners.removeElement(l);
        }
    }

    /**
     * Method to notify the arrival of an TRAMHbPacket to all the registered
     * TRAMHbPacketListeners.
     * 
     * @param reference to the TRAMHbPacketEvent block that has the received
     * Hb packet.
     */
    public void notifyTRAMHbPacketEvent(TRAMHbPacketEvent e) {
        synchronized (hbListeners) {
            for (int i = 0; i < hbListeners.size(); i++) {
                ((TRAMHbPacketListener)
		    hbListeners.elementAt(i)).receiveTRAMHbPacket(e);
            }
        }
    }

    /**
     * Method to add an TRAMHelloUniPacketListener to the Unicast input
     * dispatcher.
     * 
     * @param TRAMHelloUniPacketListener that is requesting notifications of
     * Hello_Uni packet arrivals.
     */
    public synchronized void addTRAMHelloUniPacketListener(
					    TRAMHelloUniPacketListener l) {
        synchronized (helloUniListeners) {
            helloUniListeners.addElement(l);
        }
    }

    /**
     * Method to remove an already registered TRAMHelloUniPacketListener.
     * 
     * @param reference to the registered TRAMHelloUniPacketListener.
     */
    public void removeTRAMHelloUniPacketListener(
					     TRAMHelloUniPacketListener l) {
        synchronized (helloUniListeners) {
            helloUniListeners.removeElement(l);
        }
    }

    /**
     * Method to notify the arrival of an TRAMHelloUniPacket to all the 
     * registered TRAMHelloUniPacketListeners.
     * 
     * @param reference to the TRAMHelloUniPacketEvent block that has the
     * received Hello_Uni packet.
     */
    public void notifyTRAMHelloUniPacketEvent(TRAMHelloUniPacketEvent e) {
        synchronized (helloUniListeners) {
            for (int i = 0; i < helloUniListeners.size(); i++) {
                ((TRAMHelloUniPacketListener)
		    helloUniListeners.elementAt(i)).
		    receiveTRAMHelloUniPacket(e);
            }
        }
    }

    /*
     * Call this method to terminate the unicast input dispatcher.
     */

    public void terminate() {
        done = true;
    }

}






