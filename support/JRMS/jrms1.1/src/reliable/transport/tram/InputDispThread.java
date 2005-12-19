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
 * Inputdispthread.java
 * 
 * Module Description: A Daemon thread
 * 
 * This module implements the input dispatcher in TRAM. All multicast
 * packets are receive in this object and dispatched to all packet
 * listeners. There is a different listener interface for all
 * TRAM packets.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import java.util.*;
import com.sun.multicast.reliable.authentication.*;
import java.security.*;

/**
 * This class implements the input dispatcher for TRAM. It is a thread that
 * listens to the multicast channel for packets. When received it peeks into
 * the packet to determine the packet type, creates the TRAMPacket object
 * for that packet type and hands it off to all listeners of that packet.
 * Listeners must register for packets prior to receiving them.
 */
class InputDispThread extends Thread implements TRAMDataPacketListener {
    private TRAMControlBlock tramblk = null;
    private TRAMLogger logger;
    private TRAMStats tramStats = null;
    private TRAMTransportProfile tp;
    private static final String name = "TRAM InputDispThread";
    private Vector dataListeners = new Vector();
    private Vector beaconListeners = new Vector();
    private Vector msListeners = new Vector();
    private Vector haListeners = new Vector();
    private Vector helloListeners = new Vector();
    private boolean done = false;
    private boolean idreset = true;

    /**
     * Create an input dispatcher thread. The tram control block is required
     * as input. It is used later on to obtain the multicast socket object.
     * 
     * @param tramblk The tram control block.
     */
    public InputDispThread(TRAMControlBlock tramblk) {
        super(name);

        this.setDaemon(true);

        this.tramblk = tramblk;
        this.logger = tramblk.getLogger();
        this.tramStats = tramblk.getTRAMStats();
        this.tp = tramblk.getTransportProfile();

        if (tp.getTmode() != TMODE.RECEIVE_ONLY) {
            tramblk.getOutputDispThread().addTRAMDataPacketListener(this);
        } 

        this.start();
    }

    /**
     * The run method is the heart of the input dispatcher. It is the thread
     * routine that runs continuously receiving datagram packets from the
     * multicast socket. When a packet is received, it is handed off to the
     * dispatcher.
     */
    public void run() {
        if (tramblk.getSimulator() == null) {
            MulticastSocket ms = tramblk.getMulticastSocket();

            while (!done) {
                try {
                    byte data[] = new byte[tp.getMaxBuf()];
                    DatagramPacket dp = new DatagramPacket(data, 
                                                           tp.getMaxBuf());
		    if (dp == null)
			continue;
                    ms.receive(dp);
                    dispatchPacket(dp);
                } catch (OutOfMemoryError om) {
                    om.printStackTrace();
                    printDataCounts();
                } catch (Exception e) {
                    if (!done) {
                        e.printStackTrace();
                    } 
                }
            }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
		"Input Dispatcher exiting !");
	}
    }

    /**
     * For every DatagramPacket received, create the appropriate TRAMPacket
     * type and hand it off to all listeners of these packets. Listeners
     * must register in advance for the packet type they wish to receive.
     * 
     * @param dp a DatagramPacket received from the multicast socket.
     */
    void dispatchPacket(DatagramPacket dp) {

	/*
	 * First check if the Version number of the packet is the right one.
	 * If an older/unsupported version number of thepacket is received,
	 * drop the packet and return. Currently no backwards
	 * compatibility support is provided. This needs to be added
	 * later on. 
	 *
	 */
	if (TRAMPacket.getVersionNumber(dp) != 
	    (int)TRAMPacket.TRAM_VERSION) {

	    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC |
		TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_DATAMESG |
		TRAMLogger.LOG_SESSION | TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Dropping packet due to Version# mismatch. Expect " + 
		    TRAMPacket.TRAM_VERSION +" Got " +
		    TRAMPacket.getVersionNumber(dp));
	    }
	    return;
	}

        /*
         * If the session's sessionId is set, then check if this packet's
         * sessionId matches.
         */
        int sessionId = tramblk.getSessionId();

        if (sessionId == 0) {
            idreset = false;
        } else {
            int pktid = TRAMPacket.getId(dp);

            if (pktid != sessionId) {
		/*
		 * Check if the packet is a TRAMLoggingOptionPacket. If so
		 * allow the packet to be processed. If not, return as no 
		 * further processing of the packet is required.
		 */
                if ((TRAMPacket.getMessageType(dp) == MESGTYPE.MCAST_CNTL) &&
                    (TRAMPacket.getSubType(dp) ==
                     ((int)SUBMESGTYPE.CHANGE_LOGGING & 0x000000ff))) {
                    // nothing to do
                } else {
		    if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		        logger.putPacketln(this, 
			    "Dropping packet from " + dp.getAddress() +
			    " " + SUBMESGTYPE.toString(
			    TRAMPacket.getMessageType(dp),
			    TRAMPacket.getSubType(dp)) + " with id " + pktid +
			    " actual session id is " + sessionId);
		    }
		    
		    return;
		}
            } else {
		if (idreset == false) {
		    idreset = true;

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		        logger.putPacketln(this, "sessionId = " + sessionId);
		    }
		}
	    }
        }

        /*
         * Call the static method getMessageType to peek into the packet and
         * determine this packets message type. Then call the getSubType
         * static method to obtain the subtype.
         */

        // logger.putPacketln(this,"Got a packet of size "+dp.getLength());

        switch (TRAMPacket.getMessageType(dp)) {
	    
	  case MESGTYPE.MCAST_CNTL:

	    // logger.putPacketln(this, "Got a control packet" + 
	    // dp.getLength());
	    
            switch (TRAMPacket.getSubType(dp))  {
		
	        case SUBMESGTYPE.BEACON: 
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_CNTLMESG)) {

                        logger.putPacketln(this, 
			    "Got a Beacon Packet from " + dp.getAddress());
		    }
		    
		    try {
			BeaconPacket pk = new BeaconPacket(dp);
		    
			if (signatureVerifies(pk) == true) {
			    BeaconPacketEvent e = new BeaconPacketEvent(this, 
				pk);
			
			    notifyBeaconPacketEvent(e);
			    updateSenderLiveliness(pk);
			    tramblk.setLastKnownForgetBeforeSeqNum(
			        pk.getForgetBeforeSeqNum());

			    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE | 
				TRAMLogger.LOG_DATAMESG)) {

				logger.putPacketln(this,
				    "Got Beacon message: Sequence# " + 
				    pk.getSeqNumber() + 
				    " ForgetBeforeSeq# " + 
				    pk.getForgetBeforeSeqNum());
			    }
			} else {
			    if (logger.requiresLogging(
				TRAMLogger.LOG_DIAGNOSTICS |
				TRAMLogger.LOG_CNTLMESG |
				TRAMLogger.LOG_SECURITY)) {

			        logger.putPacketln(this, 
			            "Discarding Beacon: " +
				    "Signature Does not verify!");
			    }
			}
		    } catch (Exception e) {
			e.printStackTrace();
			if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			    TRAMLogger.LOG_CNTLMESG)) {

			    logger.putPacketln(this, 
				"Error in parsing the received Beacon packet");
			}
		    
			break;
		    }
		
		    break;

	        case SUBMESGTYPE.MS: 
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_CNTLMESG)) {

		        logger.putPacketln(this, 
			    "Got an MS packet from " + dp.getAddress());
		    }
		
		    try {
			TRAMMsPacket pk = new TRAMMsPacket(dp);
			TRAMMsPacketEvent e = new TRAMMsPacketEvent(this, pk);
		    
			notifyTRAMMsPacketEvent(e);
		    } catch (Exception e) {
			e.printStackTrace();

			if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			    TRAMLogger.LOG_CNTLMESG)) {

			    logger.putPacketln(this, e.toString());
			}
		    
			break;
		    }
		
		    break;
		
	        case SUBMESGTYPE.HA: 
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_CNTLMESG)) {

		        logger.putPacketln(this, 
			    "Got an HA packet from " + dp.getAddress());
		    }
		
		    try {
			TRAMHaPacket pk = new TRAMHaPacket(dp);
			TRAMHaPacketEvent e = new TRAMHaPacketEvent(this, pk);
		    
			notifyTRAMHaPacketEvent(e);
		    } catch (Exception e) {
			e.printStackTrace();

			if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			    TRAMLogger.LOG_CNTLMESG)) {

			    logger.putPacketln(this, e.toString());
			}
		    
			break;
		    }
		
		    break;
		
	        case SUBMESGTYPE.HELLO: 
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_CONG)) {

		        logger.putPacketln(this, 
			    "Got a Hello packet from " + dp.getAddress());
		    }
		
		    try {
			TRAMHelloPacket pk = new TRAMHelloPacket(dp);
			TRAMHelloPacketEvent e = new TRAMHelloPacketEvent(this,
									  pk);

			notifyTRAMHelloPacketEvent(e);
			
		    } catch (Exception e) {
			e.printStackTrace();

			if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			    TRAMLogger.LOG_CNTLMESG)) {

			    logger.putPacketln(this, e.toString());
			}
		    
			break;
		    }
		    break;
		
	        case SUBMESGTYPE.CHANGE_LOGGING:
		    TRAMLoggingOptionPacket pk = 
			new TRAMLoggingOptionPacket(dp);

		    if ((pk.getFlags() & ((int)
		        TRAMLoggingOptionPacket.FLAGBIT_IGNORE_ID_ADDRESS)) == 
			0) {

			/*
			 * Need to verify the Session Id and the the data 
			 * source address.
			 */
			InetAddress senderAddr = 
			    tramblk.getTransportProfile().
			    getDataSourceAddress();

			if ((tramblk.getSessionId() != TRAMPacket.getId(dp)) ||
			    (senderAddr.equals(pk.getAddress()) != true)) {

			    return;
			}
		    }
		    /*
		     * Now check if the logging option is to be adopted by 
		     * every node or only those listed in the packet.
		     */
		    if ((pk.getFlags() & 
			((int)TRAMLoggingOptionPacket.FLAGBIT_ALLNODES)) == 0) {
			/*
			 * Looks like only few nodes are to adopt this option.
			 * The following code checks if this node is required
			 * to adopt by scanning to see if its address is 
			 * listed in the packet.
			 */
			InetAddress[] ia = pk.getAddressList();
			if (ia == null)
			    return;
			InetAddress myAddress = null;
			myAddress = tramblk.getLocalHost();
			boolean addressListed = false;
			for (int i = 0; i < ia.length; i++) {
			    if (ia[i].equals(myAddress) == true) {
				addressListed = true;
				break;
			    }
			}
			if (addressListed == false)
			    return;
		    }
		    /*
		     * If the code has made it to this point, then it means 
		     * that this node has to adopt the log option specified 
		     * in the packet.
		     */
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			TRAMLogger.LOG_CNTLMESG)) {
		
		        logger.putPacketln(this, 
			    "Got a LoggingOptionPacket. From " +
			    pk.getAddress() + 
			    " New LogOption value is " + 
			    pk.getLogOption());
		    }

		    if ((pk.getLogOption() & TRAMLogger.LOG_ABORT_TRAM) != 0) {
		        logger.putPacketln(this, 
			    "Abort requested by CHANGE_LOG_OPTION " + 
			    "LOG_ABORT_TRAM");

	                tramblk.getTRAMDataCache().handleSessionDown();
            	        new AbortTRAM("LOG_ABORT_TRAM", tramblk);
		    }

		    tp.setLogMask(pk.getLogOption());

		    if ((pk.getLogOption() & TRAMLogger.LOG_PERFMON) != 0)
			tramblk.getRateAdjuster().startPerfMon();
		    else
			tramblk.getRateAdjuster().stopPerfMon();

		    break;
		}

            break;

        case MESGTYPE.MCAST_DATA:

            switch (TRAMPacket.getSubType(dp)) {

            case SUBMESGTYPE.DATA: 

            case SUBMESGTYPE.DATA_RETXM:

                /*
                 * First check is the authentication is being used. If so
                 * accept only those that verify the signature.
                 */
                TRAMDataPacket pk = new TRAMDataPacket(dp);

                if (tramStats.getDataStartTime() == 0) {
                    tramStats.setDataStartTime(System.currentTimeMillis());
                } 

		if (TRAMPacket.getSubType(dp) == SUBMESGTYPE.DATA) {
                    tramStats.incPacketsRcvd();
		} 

                if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
                    try {
                        if (signatureVerifies(pk) == false) {
			    if (logger.requiresLogging(
				TRAMLogger.LOG_DIAGNOSTICS |
				TRAMLogger.LOG_DATAMESG)) {
				
                                logger.putPacketln(this, 
				    "Signature Verification Fails");
			    }

                            break;
                        }

                        // TRAMDataPacket pk = new TRAMDataPacket(dp);

			String s = "";

			if (TRAMPacket.getSubType(dp) != SUBMESGTYPE.DATA)
			    s = "RETXM ";

			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			    TRAMLogger.LOG_DATAMESG)) {

			    logger.putPacketln(this, 
                                "Got " + s + "data packet with Seq# " + 
				pk.getSequenceNumber() + " forgetBeforeSeq# " + 
				pk.getForgetBeforeSeqNum() +", Rate " + 
				    pk.getDataRate());
			}

                        TRAMDataPacketEvent e = new TRAMDataPacketEvent(this, 
			    pk);
						
			/* 
			 * Originally, the
			 * tramblk.setLastKnownForgetBeforeSeqNum() was set here
			 * using pk.getForgetBeforeSeqNum, but now moved to
			 * notifyTRAMDataPacketEvent because in that function 
			 * we may randomly drop packets, in which case the FB 
			 * num should not be updated.
			 */
                        notifyTRAMDataPacketEvent(e);

                        if (SUBMESGTYPE.DATA_RETXM != (byte)pk.getSubType()) {
                            updateSenderLiveliness(pk);
                        } 
                    } catch (Exception e) {
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			    TRAMLogger.LOG_DATAMESG | TRAMLogger.LOG_CONG)) {
			
                            logger.putPacketln(this, 
				"InputDispatcher: Dropping Data Packet " + 
				pk.getSequenceNumber());
			}
                        e.printStackTrace();

                        break;
                    }
                }
            }

            break;

        default: 
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_DATAMESG)) {

                logger.putPacketln(this, 
                    "Got an Unsupported message Type" + 
		    TRAMPacket.getMessageType(dp));
	    }

            break;
        }
    }

    /**
     * This method is the TRAMDataPacketListener interface.
     * The Output Dispatcher calls this method when it sends a packet
     * off the wire.
     * 
     * @param e an TRAMDataPacketEvent.
     */
    public synchronized void receiveDataPacket(TRAMDataPacketEvent e) {
        TRAMDataPacket pk = (TRAMDataPacket) e.getPacket();
        TRAMDataPacketEvent ea = new TRAMDataPacketEvent(this, pk);

        if (signatureVerifies(pk) == true) {
	    /* Record the forgetBefore Seq Num for this packet. */
	    tramblk.setLastKnownForgetBeforeSeqNum(pk.getForgetBeforeSeqNum());
            notifyTRAMDataPacketEvent(ea);
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
	        logger.putPacketln(this, "My own Signature Fails!!");
	    }
        }
    }

    /**
     * Add listeners for TRAMDataPackets. All components that need the data
     * packets must call the add**listener method. When a data packet is
     * received, all listener objects on the list are called with a data
     * packet event containing the data packet itself.
     * 
     * @param l the object implementing the TRAMDataPacketListener interface.
     */
    public synchronized void addTRAMDataPacketListener(
	TRAMDataPacketListener l) {

        dataListeners.addElement(l);
    }

    /**
     * Remove an TRAMDataPacketListener.
     * 
     * @param l the TRAMDataPacketListener object to remove.
     */
    public synchronized void removeTRAMDataPacketListener(
					   TRAMDataPacketListener l) {
        dataListeners.removeElement(l);
    }

    public void printDataCounts() {
        for (int i = 0; i < dataListeners.size(); i++) {
            TRAMDataPacketListener l = 
                (TRAMDataPacketListener) dataListeners.elementAt(i);

            l.printDataCounts();
        }
    }

    /**
     * Notify all listeners that a TRAMDataPacket has been received. The
     * listener list is cloned and all listeners currently on it are
     * handed the event.
     * 
     * @param e the TRAMDataPacketEvent to give to all listeners....
     */
    public void notifyTRAMDataPacketEvent(TRAMDataPacketEvent e) {
	/*
         * This is for testing to slow down receivers
         * to make some receivers look bad.
	 *
	 * Drop packets at random, with a 30% probably of 
	 * dropping a packet.
	 */
	if (tp.getReceiverMaxDataRate() > 0 &&
            tramblk.getRateAdjuster().getAverageDataRate() > 
            (long)(1.07 * tp.getReceiverMaxDataRate()) && 
            Math.random() < .3) {

 	    tramblk.getRateAdjuster().calculateAverageDataRate();

	    /*
	     * Just drop this packet.  That should effectively
	     * lower the average data rate.
	     */
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE | 
		TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Dropping packet " + 
		    e.getPacket().getSequenceNumber() + 
		    " to lower average rate of " + 
		    tramblk.getRateAdjuster().getAverageDataRate() + 
		    " to " + tp.getReceiverMaxDataRate());
	    }

	    return;
        } 

	// Now that the packet has not been dropped, set the FB

	tramblk.setLastKnownForgetBeforeSeqNum(
	    e.getPacket().getForgetBeforeSeqNum());
	tramblk.setLastKnownSequenceNumber(e.getPacket().getSequenceNumber());

        TRAMDataPacketListener l = null;

        synchronized (dataListeners) {
            for (int i = 0; i < dataListeners.size(); i++) {
                l = (TRAMDataPacketListener) dataListeners.elementAt(i);

                l.receiveDataPacket(e);
            }
        }
    }

    /**
     * Add all HA packet listener objects to the list. Whenever an HA
     * packet is received, each object on the HA packet listener list will
     * get an HA event containing the HA packet.
     * 
     * @param l the TRAMHaPacketListener object.
     */
    public synchronized void addTRAMHaPacketListener(TRAMHaPacketListener l) {
        haListeners.addElement(l);
    }

    /**
     * Remove an HA packet listener object from the list.
     * 
     * @param l the ha packet listener object to remove.
     */
    public synchronized void removeTRAMHaPacketListener(
						   TRAMHaPacketListener l) {
        haListeners.removeElement(l);
    }

    /*
     * Notify all HA packet listeners of the ha event.
     * 
     * @param e the HA packet event.
     */

    public void notifyTRAMHaPacketEvent(TRAMHaPacketEvent e) {
        Vector l;

        synchronized (this) {
            l = (Vector) haListeners.clone();
        }

        for (int i = 0; i < l.size(); i++) {
            ((TRAMHaPacketListener) l.elementAt(i)).receiveTRAMHaPacket(e);
        }
    }

    /**
     * adds a Beacon packet listener to the list of listeners.
     * 
     * @param l the BeaconPacketListener that is to be added.
     * 
     */
    public void addBeaconPacketListener(BeaconPacketListener l) {
        synchronized (beaconListeners) {
            beaconListeners.addElement(l);
        }
    }

    /**
     * removes a Beacon packet listener from the maintained list of listeners.
     * 
     * @param l the BeaconPacketListener that is to be removed.
     * 
     */
    public void removeBeaconPacketListener(BeaconPacketListener l) {
        synchronized (beaconListeners) {
            beaconListeners.removeElement(l);
        }
    }

    /**
     * notifies a Beacon packet receive event to all registered listeners
     * 
     * @param e the BeaconPacketEvent that is to be reported.
     * 
     */
    public void notifyBeaconPacketEvent(BeaconPacketEvent e) {
        Vector l;

        synchronized (beaconListeners) {
            l = (Vector) beaconListeners;

            for (int i = 0; i < l.size(); i++) {
                ((BeaconPacketListener) l.elementAt(i)).
		    receiveBeaconPacket(e);
            }
        }
    }

    /**
     * adds a Hello packet listener to the list of listeners.
     * 
     * @param l the TRAMHelloPacketListener that is to be added.
     * 
     */
    public void addTRAMHelloPacketListener(TRAMHelloPacketListener l) {
        synchronized (helloListeners) {
            helloListeners.addElement(l);
        }
    }

    /**
     * removes a Hello packet listener from the maintained list of listeners.
     * 
     * @param l the TRAMHelloPacketListener that is to be removed.
     * 
     */
    public void removeTRAMHelloPacketListener(TRAMHelloPacketListener l) {
        synchronized (helloListeners) {
            helloListeners.removeElement(l);
        }
    }

    /**
     * notifies a Hello packet receive event to all registered listeners
     * 
     * @param e the TRAMHelloPacketEvent that is to be reported.
     * 
     */
    public void notifyTRAMHelloPacketEvent(TRAMHelloPacketEvent e) {
        Vector l;

        synchronized (helloListeners) {
            l = (Vector) helloListeners;

            for (int i = 0; i < l.size(); i++) {
                ((TRAMHelloPacketListener) l.elementAt(i)).
		    receiveTRAMHelloPacket(e);
            }
        }
    }

    /**
     * adds a MS packet listener to the list of listeners.
     * 
     * @param l the TRAMMsPacketListener that is to be added.
     */
    public void addTRAMMsPacketListener(TRAMMsPacketListener l) {
        synchronized (msListeners) {
            msListeners.addElement(l);
        }
    }

    /**
     * removes a MS packet listener from the maintained list of listeners.
     * 
     * @param l the TRAMMsPacketListener that is to be removed.
     */
    public void removeTRAMMsPacketListener(TRAMMsPacketListener l) {
        synchronized (msListeners) {
            msListeners.removeElement(l);
        }
    }

    /**
     * notifies a MS packet receive event to all registered listeners
     * 
     * @param e the TRAMMsPacketEvent that is to be reported.
     */
    public void notifyTRAMMsPacketEvent(TRAMMsPacketEvent e) {
        Vector l;

        synchronized (msListeners) {
            l = (Vector) msListeners;

            for (int i = 0; i < l.size(); i++) {
                ((TRAMMsPacketListener) l.elementAt(i)).receiveTRAMMsPacket(e);
            }
        }
    }

    /**
     * Checks if the packet was sourced by the sender and updates the
     * sender lastheard timestamp if the packet was indeed from the sender.
     * 
     */
    private void updateSenderLiveliness(TRAMPacket pkt) {
        InetAddress senderAddr = 
            tramblk.getTransportProfile().getDataSourceAddress();
        if (senderAddr != null) {
            if (senderAddr.equals(pkt.getAddress()) == true) {
                tramblk.setLastHeardFromTheSender(System.currentTimeMillis());
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_CNTLMESG | TRAMLogger.LOG_DATAMESG)) {

                    logger.putPacketln(this, "Packet not from the sender");
		}
            }
        }
    }
	    
    /*
     * Method to facilitate the termination of this thread.
     */
    public void terminate() {
        done = true;
    }

    /*
     * A generic method to perform signature verification on an incoming
     * packet. Currently ONLY Beacon and Data packets are signed. If
     * someother packet requires signature support, then the details
     * of verifying the packet needs to be added to this method. Currently
     * if some other message is passed, false is returned.
     * 
     * @param tramPacket whose signature is to be verified.
     * @return false if signature verification fails.
     * true if signature verifies.
     */

    private synchronized boolean signatureVerifies(TRAMPacket pk) {
        AuthenticationModule authMod = tramblk.getAuthenticationModule();

        if (authMod == null) {
            return true;
        } 

        /*
         * Now Check if the packet is a TRAMDataPacket or a BeaconPacket.
         * These are the only two packets that are currently signed.
         */
        int tramHdrLen, sigOffset, sigLen;
        short haI = 0;
        byte[] buf = pk.getBuffer();
        byte subMesgType = (byte) 0;
        InetAddress iaddr = null;
        TRAMDataPacket sdp = null;

        if (pk instanceof TRAMDataPacket) {
            sdp = (TRAMDataPacket) pk;

            /*
             * Now perform the verification.
             * Currently, the HAI field is one of the mutable fields as a
             * result, the value is set to zero then the verification is
             * performed. After verification the value is restored.
             * This is a serious security problem and we need to address
             * this to avoid malicious exploits. This problem aroses because
             * of the fact that the output dispatcher tries to add the
             * latest HAI interval just before dispatching the packet.
             * We some how have to address this.... till then HAI is considered
             * a mutable field.
             */
            haI = sdp.getHaInterval();

            sdp.setHaInterval((short) 0);

            /*
             * The subMessage type field is a mutable field. This is because
             * a head while retransmitting sets the subMessaget type to be
             * DATA_RETXM. Hence this field is set to 0 while computing
             * the signature and also while verifying. Hence, do the
             * save, test and reset opertation.
             */
            subMesgType = (byte) sdp.getSubType();

            sdp.setSubType(0);

            tramHdrLen = TRAMDataPacket.TRAMDATAHEADERLENGTH 
                        + TRAMPacket.TRAMHEADERLENGTH;
            sigOffset = tramHdrLen + sdp.getDataLength();
            sigLen = sdp.getLength() - sdp.getDataLength();
            iaddr = sdp.getSourceAddress();
        } else {
            if (pk instanceof BeaconPacket) {
                BeaconPacket bpkt = (BeaconPacket) pk;

                tramHdrLen = TRAMPacket.TRAMHEADERLENGTH 
                            + BeaconPacket.HEADERLENGTH;
                sigOffset = tramHdrLen;
                sigLen = bpkt.getLength();
                iaddr = bpkt.getSrcAddress();
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
                    TRAMLogger.LOG_DATAMESG | TRAMLogger.LOG_CNTLMESG)) {

                    logger.putPacketln(this, 
			"ERROR: Only Beacon and Data PAckets are signed. Not " +
		        pk.getMessageType() + "SubMesg Type " + 
			    pk.getSubType());
		}

                return false;
            }
        }
        if (sigLen <= 0) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATAMESG)) {

                logger.putPacketln(this, 
                    "Malformed packet??? No Signature Found");
	    }

            return false;
        }

        byte[] signature = new byte[sigLen];

        System.arraycopy(buf, sigOffset, signature, 0, sigLen);

        try {
            boolean result = authMod.verify(buf, 0, sigOffset, signature, 
                                            iaddr.getHostAddress());

            if (sdp != null) {
                sdp.setHaInterval(haI);
                sdp.setSubType((int) subMesgType);
            }

            return result;
        } catch (KeyException ke) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                logger.putPacketln(this, 
                    "Invalid Signature verification Key");
	    }
        } catch (SignatureException se) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
	        logger.putPacketln(this, 
		    "Error During Signature verification");
	    }
        }

        return false;
    }

}

