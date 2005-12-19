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
 * OutputDispThread.java
 * 
 * Module Description: A Daemon thread
 * 
 * This module implements the multicast output dispatcher for TRAM.
 * This object is created when the output PacketDb is created.
 * It waits for packets to arrive in the output database and sends
 * them at intervals computed from the data rates specified in the
 * transport profile.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.io.*;

/**
 * The OutputDispThread loops forever waiting for the output
 * queue manager to notify it of data to send. When data is ready
 * to send, the dispatcher is notified and begins execution. A
 * packet is retrieved from the output queue and sent on the
 * multicast address. The dispatcher then computes a delay interval
 * based on the size of the packet and the desired data rate.
 * The thread then sleeps for this interval.
 */
class OutputDispThread extends Thread {
    private TRAMControlBlock tramblk;
    private TRAMTransportProfile tp;
    private TRAMStats tramStats;
    private TRAMRateAdjuster rateAdjuster;
    private TRAMLogger logger;
    private MulticastSocket ms = null;
    private static final String name = "TRAM OutputDispThread";
    private PacketDb packetDB;
    private long lastPktSentTime = 0;
    private Vector sentPktListeners = new Vector();
    private long prevData = 0;
    private long prevRetr = 0;
    private long sendStart = 0;
    private boolean done = false;

    /*
     * Constructor for the OutputDispThread class. Set the thread
     * name and the daemon flag. The TRAMControlBlock is saved in
     * local storage for future reference.
     */

    public OutputDispThread(TRAMControlBlock tramblk) {
        super(name);

        this.tramblk = tramblk;
        tp = tramblk.getTransportProfile();
        tramStats = tramblk.getTRAMStats();

        this.logger = tramblk.getLogger();
        rateAdjuster = tramblk.getRateAdjuster();

        /*
         * NOTE	:
         * This	is a temporary fix to work around a bug in the version
         * of DatagramSocket included with JWS 2.0 (BugTraq number 4087067).
         * When the bug is fixed, the following try and catch needs
         * to be removed.
         * 
         * The bug refered to above seems to be that using the same
         * socket in multiple threads can cause one or the other to
         * hang. It seems that if one thread is stalled waiting to
         * receive a packet, it blocks the other thread trying to send a
         * packet. The real change below is the creating a new multicast
         * socket vs obtaining the one in the tram control block.
         * 
         * There also seems to be a bug in NT where setting the TTL
         * to something greater than 127 causes an IOException.
         */
        if (tramblk.getSimulator() == null) {
            try {
                ms = tramblk.newMulticastSocket();

                try {

                    // 
                    // Try the jdk1.2 method
                    // 

                    ms.setTimeToLive(tp.getTTL());
                } catch (NoSuchMethodError e) {

                    // 
                    // jdk1.2 method doesn't exist so try pre-jdk1.2 name
                    // 
                    ms.setTTL(tp.getTTL());
                }
            } catch (java.io.IOException e) {
                e.printStackTrace();

		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                    logger.putPacketln(this, 
			"Unable to open Multicast socket");
		}
            }
        }

        this.packetDB = null;

        this.setDaemon(true);
        this.start();
    }

    /*
     * The heart of the Output dispatcher lives here. This code runs inside
     * an infinite outer loop. The inner loop will continue to run as long
     * as there are packets on the output queue. Each packet obtained from
     * the output queue is sent over the multicast socket in the
     * TRAMControlBlock. Once sent, an interpacket delay interval is computed
     * based on the packet size and desired data rate. The thread sleeps
     * for this interval.
     */

    public void run() {

        /*
         * Wait till the Output database is available if currently not
         * available.
         */
        if (packetDB == null) {
            stall();
        }

        long excessSleepTime = 0;

        while (!done) {
            boolean reTransmissionPacket = false;

            /* As long as there are packets on the output queue, loop */

            long blockedTime = System.currentTimeMillis();
            TRAMPacket pk = packetDB.getPacket();

	    if (pk == null)
		continue;

		sendStart = System.currentTimeMillis();

            // 
            // At this point we know how long it's taken for us to get
            // the next packet.  If we blocked, that's included in the
            // calculation as well.
            // 

            long t = sendStart - blockedTime;

            if (t >= excessSleepTime) {
                // 
                // The time to get the next packet (including blocked time)
                // is longer than the granularity of the clock so the excess
		// sleep time is due to blocked time.
		// Forget about excess sleep time.
                // 
                excessSleepTime = 0;
            } else {
                // 
                // The time to get the next packet (including blocked time)
                // is shorter than the excess sleep time from the previous
                // sleep.  We still have excess sleep time but we can
                // reduce this by the time it took to get the next packet.
                // 
                excessSleepTime -= t;
            }

            byte subMesgType = (byte) pk.getSubType();
	    int sequenceNumber = ((TRAMDataPacket) pk).getSequenceNumber();

            switch (subMesgType) {
            case SUBMESGTYPE.DATA: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATAMESG)) {

                    logger.putPacketln(this, 
			"Sending Data packet " + 
			((TRAMDataPacket) pk).getSequenceNumber() + 
			" and size = " + pk.getLength());
		}

                /*
                 * The following code is a hack in place to assure that
                 * the sender gets the packets it sends. Originally the
                 * packets sent were received through the multicast listener
                 * on the multicast channel but for some unknown reason,
                 * packets were lost in this process. To avoid this problem
                 * we set up the InputDispThread to be a TRAMDataPacketListener
                 * and call it when we send data. THIS MUST BE DONE PRIOR
                 * TO ACTUALLY SENDING THE DATA. This is because there is
                 * a possibility that an incoming ACK for the packet sent will
                 * be received and processed prior to the sender getting
                 * its data packet. The code deals with this possibility
                 * but lets avoid it.
                 * Further the above can be suppressed if the data packet
                 * is a retransmission.... the asssumption is that the
                 * packet being retransmitted is already in the data base and
                 * no further enqueuing needs to be performed.
                 */
                TRAMDataPacketEvent e = new TRAMDataPacketEvent(this, 
                    (TRAMDataPacket)pk);

                notifyTRAMDataPacketEvent(e);

	        rateAdjuster.adjustRate((TRAMDataPacket)pk);
                break;

            case SUBMESGTYPE.DATA_RETXM: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATAMESG)) {

                    logger.putPacketln(this,
			"Sending Retxm packet " + 
			((TRAMDataPacket) pk).getSequenceNumber() + 
			" and size = " + pk.getLength() + 
			" rate " + rateAdjuster.getOpenWindowDataRate());
		}

                /* Accounting */

            	tramStats.incRetransSent();
            	tramStats.addBytesReSent(pk.getLength());

                reTransmissionPacket = true;
		
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATAMESG)) {

		    logger.putPacketln(this,
		        "rate " + 
			rateAdjuster.getActualDataRate(sequenceNumber) +
		        " retrans rate " + 
			rateAdjuster.getOpenWindowDataRate() +
		        " seq " + sequenceNumber);
		}
                break;

            default: 
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Sending bad packet type " + subMesgType);
		}
                continue;
            }

	    /*
	     * Set the ACK window size in the data packet.
	     * This is current set in the transport profile and not
	     * changed so it isn't really necessary to put this in
	     * each data packet.  However, this could change
	     * in the future so we'll continue to put this in the packet.
	     */
	    ((TRAMDataPacket)pk).setAckWindow((short)tp.getAckWindow());

	    /*
	     * Set the time this packet is actually transmitted so that
	     * retransmissions can be suppressed appropriately.
	     */
	    ((TRAMDataPacket)pk).setLastTransmitTime(
		System.currentTimeMillis());

	    /*
	     * Set the data rate in each packet.  We set the rate in the
	     * packet to be what the sender would like to use regardless
	     * of whether or not the sender is restricted by the congestion
	     * window to the minimum rate.
	     */
	    ((TRAMDataPacket)pk).setDataRate(
		(int)rateAdjuster.getOpenWindowDataRate());

            ((TRAMDataPacket)pk).setHaInterval(tramblk.getGroupMgmtThread().
		getDataHaInterval());

	    if ((((TRAMDataPacket)pk).getFlags() & 
		TRAMDataPacket.FLAGBIT_PRUNE) != 0) {

	        if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
		    logger.putPacketln(this, 
			"Sending packet with FLAGBIT_PRUNE set!");
	        }
	    }

		

            DatagramPacket dp = pk.createDatagramPacket();


            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().simulateMulticastData(
		dp, tramblk.getTransportProfile().getTTL());
            } else {
                try {
                    ms.send(dp);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            pk.setTransmitPending(false);

            /*
             * The lastPktSentTime is updated every time a packet is
             * sent out in the case of a non-sender head. In the case
             * of the sender, the lastPktSentTime is updated ONLY when the
             * data packet being sent is not a retransission. This is to
             * enable the sender to send out beacons if it has not sent
             * out data to the session scope for an extended interval of
             * time. Note that the the retransmissions performed by the
             * sender is not to the session scope and as a result it is not
             * accounted to suppress the beacons. Also if the beacons
             * (more typically the filler beacons) are not sent out, the
             * heads and members that are not in the repair TTL range
             * can decide to signal "LOSS of the SENDER" when the sender
             * is bogged down performing a lot of retransmissions.
             * The following check prevents the above from happening.
             */
            if ((reTransmissionPacket == false) || 
		(tp.getTmode() == TMODE.RECEIVE_ONLY)) {
                lastPktSentTime = System.currentTimeMillis();
            } 

            if (tramStats.getDataStartTime() == 0) {
                tramStats.setDataStartTime(System.currentTimeMillis());
            } 
		    
            /* Compute Transmit interval */

	    long timeToSleep;

	    long currentDataRate;

	    if (reTransmissionPacket == true)
		currentDataRate = rateAdjuster.getOpenWindowDataRate();
	    else {
	        currentDataRate = 
		    rateAdjuster.getActualDataRate(sequenceNumber);
	    }

	    timeToSleep = computeRate(dp.getLength(), currentDataRate);

            if (excessSleepTime > timeToSleep) {
                // 
                // We've already slept too long.  Reduce the excess sleep
                // time by the amount of time we would have slept.
                // But don't sleep, just go send another packet.
                // 
                excessSleepTime -= timeToSleep;
                continue;                       // send another packet
            }

            // 
            // At this point we know that excessSleepTime is less than
            // or equal to the timeToSleep.  Reduce the time to sleep
            // by the excessSleepTime and set excessSleepTime to 0.
            // 
            timeToSleep -= excessSleepTime;     // timeToSleep will be >= 0
            excessSleepTime = 0;

            if (timeToSleep == 0) {
                continue;                       // send another packet
            } 
             
            if (timeToSleep > 1000) {
                /*
                 * The sleep computation came up with a sleep time
                 * greater than 1 second. Adjust this rate to sleep
                 * for at most 1 second. NOTE: This is here
                 * because excessive sleep times are really
                 * unnecessary. It usually indicates that an
                 * incorrect data rate has been computed. This
                 * problem can also be solved with a minimum
                 * data rate. I've left this in as a secondary
                 * precaution.
                 */
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
                    TRAMLogger.LOG_DATAMESG)) {
		    
		    logger.putPacketln(this,
		        "time to sleep is " + timeToSleep + 
			" seq " + sequenceNumber);
		}

                timeToSleep = 1000;
            }

            try {
                Thread.sleep(timeToSleep);
            } catch (InterruptedException ie) {

            }

            // 
            // We really wanted to sleep for timeToSleep milliseconds.
            // Calculate how long it really took from the time to get
            // this packet until we finished sending it.
            // The excess sleep time is reduced by timeToSleep because
            // we wanted to sleep for that amount of time.
            // 
            excessSleepTime = System.currentTimeMillis() - sendStart 
                              - timeToSleep;

	    if (excessSleepTime < 0)
		excessSleepTime = 0;
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "Thread Exit!!");
	}
    }

    /**
     * The computeRate method calculates the next wait interval in an
     * attempt to achieve the desired data rate. The interval is computed
     * as follows:
     * 
     * timeout = (packetsize * 1000) / desiredrate
     * 
     * The resulting timeout is in milliseconds. This accounts for packets
     * that are different sizes or the same size. If the desired rate changes,
     * the new data rate changes also.
     * 
     * @param dp the current datagram packet to send.
     */
    public long computeRate(int packetLength, long desiredRate) {
        if (desiredRate != 0) {
            return ((packetLength * 1000) / desiredRate);
        }

        return 0;
    }

    /**
     * setPacketDB
     * @param - PacketDB output packet database from which the packets
     * are to be dispatched.
     */
    public void setPacketDB(PacketDb outputdb) {
        if (packetDB == null) {
            packetDB = outputdb;

            wake();
        }
    }

    /**
     * gets the time when the last packet was dispatched.
     * This method is useful for modules such as HelloThread that
     * want to suppres dispatching of Hellos if a repair has been
     * performed by it with in a threshold of time.
     * @return the timestamp indicating when the last packet was dispatched
     * by the dispatcher.
     */
    public long getLastPktSentTime() {
        return lastPktSentTime;
    }

    /**
     * Add listeners for sent TRAMDataPackets. All components that need the data
     * packets must call the add**listener method. When a data packet is
     * received, all listener objects on the list are called with a data
     * packet event containing the data packet itself.
     * 
     * @param l the object implementing the TRAMDataPacketListener interface.
     */
    public synchronized void addTRAMDataPacketListener(
						 TRAMDataPacketListener l) {
        sentPktListeners.addElement(l);
    }

    /**
     * Remove an TRAMDataPacketListener.
     * 
     * @param l the TRAMDataPacketListener object to remove.
     */
    public synchronized void removeTRAMDataPacketListener(
						 TRAMDataPacketListener l) {
        sentPktListeners.removeElement(l);
    }

    /**
     * Notify all listeners that an TRAMDataPacket has been received. The
     * listener list is cloned and all listeners currently on it are
     * handed the event.
     * 
     * @param e the TRAMDataPacketEvent to give to all listeners....
     */
    public void notifyTRAMDataPacketEvent(TRAMDataPacketEvent e) {
        synchronized (sentPktListeners) {
            TRAMDataPacket dp = e.getPacket();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Sending Receive notify for Packet " + 
		    dp.getSequenceNumber() + " listeners = " + 
		    sentPktListeners.size());
	    }

            for (int i = 0; i < sentPktListeners.size(); i++) {
                ((TRAMDataPacketListener)
		sentPktListeners.elementAt(i)).receiveDataPacket(e);
            }
        }
    }

    /*
     * These helper methods allow the output dispatcher thread to stall
     * waiting for more work to do. When work comes in, the wake helper
     * method is called to bring us back to life. For the moment, just eat
     * the InterruptedException. No other thread ought to be interrupting
     * this one.
     * 
     * These replace the now obsoleted "suspend" and "resume" methods.
     */

    private synchronized void stall() {
        try {
            wait();
        } catch (InterruptedException e) {
            if (!done) {
                e.printStackTrace();
            } 
        }
    }

    /*
     * Helper method to wake the output dispatchers thread back up when
     * there is work to be done.
     * 
     * These replace the now obsoleted "suspend" and "resume" methods.
     */

    private synchronized void wake() {
        notify();
    }

    /*
     * Call this method to terminate the output dispatcher.
     */

    public synchronized void terminate() {
        done = true;
	wake();
        interrupt();
	if (packetDB != null)
	    packetDB.terminate();
	
    }

}
