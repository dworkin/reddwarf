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
 * TRAMControlBlock.java
 * 
 * Module Description:
 * 
 * The class defines the TRAM private block. This block
 * is shared by many threads and any write operations
 * will have to be performed Synchronously.
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.FileDescriptor;
import java.net.MulticastSocket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.SocketException;
import com.sun.multicast.reliable.authentication.*;
import java.security.*;
import java.util.Date;

class TRAMControlBlock {
    private TRAMTransportProfile tp = null;
    private MulticastSocket multicastSocket = null;
    private DatagramSocket unicastSocket = null;

    /*
     * unicast socket
     * for control
     * message exchng.
     */
    private long lastRetrans = 0;
    private int unicastPort = 0;                // unicast port in use.
    private long dataTxmStartTime = 0;
    private long dataTxmEndTime = 0;
    private long avgMinRate = 0;
    private long dataRate = 0;
    private long beaconRate = 0;
    private byte tramState = TRAM_STATE.INIT;
    private byte beaconTTL = 1;                 // beacon ttl.
    private byte msTTL = 1;                     // ms TTL
    private GroupMgmtBlk groupMgmtBlk = null;
    private TRAMStats statsBlock = null;

    /*
     * Various threads of TRAM.
     */
    private GroupMgmtThread groupMgmtThread = null;
    private InputDispThread inputDispThread = null;
    private OutputDispThread outputDispThread = null;
    private UcastInputDispThread ucastInputDispThread = null;
    private TRAMMemberAck memberAck = null;
    private TRAMHeadAck headAck = null;
    private HelloThread helloThread = null;     // Hello control thread
    private BeaconGenThread beaconGenThread = null;
    private PacketDbIOManager packetdb;
    private TRAMLogger logger;
    private TRAMRateAdjuster tramRateAdjuster;
    private TRAMSimulator simulator = null;
    private TRAMDataCache dataCache;
    private int lastKnownSequenceNumber;
    private int lastKnownForgetBeforeSeqNum;
    private int lastFirstTimeTxmSequenceNumber;
    private boolean dataTransmissionComplete = false;
    private long lastHeardFromTheSender = 0;
    private long ackInterval = 30000;
    private boolean cacheFull = false;
    private int sessionId = 0;
    private int highestSequenceAllowed;
    private InetAddress myLocalAddress;

    // Authentication Stuff.

    private AuthenticationModule authenticationModule = null;

    /*
     * Constructor
     */

    public TRAMControlBlock(MulticastSocket ms, TRAMTransportProfile tp) 
            throws TRAMControlBlockException {
        this(ms, tp, null);
    }

    public TRAMControlBlock(MulticastSocket ms, TRAMTransportProfile tp, 
                   TRAMSimulator simulator) throws TRAMControlBlockException {
        this.simulator = simulator;
        this.multicastSocket = ms;
        this.tp = tp;
        dataTxmStartTime = System.currentTimeMillis();
        logger = new TRAMLogger(this);
        statsBlock = new TRAMStats(this);
        tramRateAdjuster = new TRAMRateAdjuster(this);

        try {
            startAuthenticationModule(tp);
        } catch (IOException ioe) {
            throw new TRAMControlBlockException(ioe 
                         + " Unable to find/read Authentication Spec file");
        } catch (SignatureException se) {
            throw new TRAMControlBlockException(se 
                                  + " Unable to create Authentication module");
        }

        if (tp.getDataSourceAddress() != null) {
            statsBlock.addSender(tp.getDataSourceAddress());
        } 

        /*
         * Create a unicast socket to exchange control messages. If no port
         * preference is specified in the TRAMTransportProfile, allocate the
         * next available port. If a preferred port is specified and if it
         * happens to be in use at the time of instantiating, then an
         * TRAMControlBlockException is thrown.
         */
        unicastPort = tp.getUnicastPort();

	/*
	 * Make sure that the unicast socket uses the same address as the
         * multicast socket.
	 */
	try {
	    myLocalAddress = ms.getInterface();
	} catch (SocketException e) {
            throw new TRAMControlBlockException(e + 
	        " Unable to set multicast socket interface to " + 
		myLocalAddress);
        }

        if (simulator == null) {
            if (unicastPort == (short) 0) {
                try {
                    unicastSocket = new DatagramSocket(0, myLocalAddress);
                    unicastPort = unicastSocket.getLocalPort();
                } catch (SocketException e) {
                    throw new TRAMControlBlockException(e + 
			" Unable to create Unicast Socket");
                }
            } else {
                try {
                    unicastSocket = 
			new DatagramSocket(unicastPort, myLocalAddress);
                } catch (SocketException e) {
                    throw new TRAMControlBlockException(e + 
			" Unable to create Unicast Socket, Port inuse");
                }
            }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_CONG)) {
            logger.putPacketln(this, 
	        "Listening on " + myLocalAddress + ", Unicast port " + 
		unicastPort);
	}

        outputDispThread = new OutputDispThread(this);
        inputDispThread = new InputDispThread(this);
        ucastInputDispThread = new UcastInputDispThread(this);
        groupMgmtBlk = new GroupMgmtBlk(this);

        // Start the GroupManagement Thread.

        groupMgmtThread = new GroupMgmtThread(this);

        if (tp.getTmode() != (byte) TMODE.SEND_ONLY) {
            memberAck = new TRAMMemberAck(this);
        } else {

            /* Compute a random sessionId value excluding 0 */

            while (sessionId == 0) {
                sessionId = (int) (Math.random() * 2147483647);
            }

            tp.setSessionId(sessionId);
            this.tp.setSessionId(sessionId);

	    ackInterval = 5 * tp.getPruneHelloRate();
        }

        dataCache = new TRAMGenericDataCache(this);
        packetdb = new PacketDbIOManager(this);
    }

    /**
     * gets the TRAM transport Profile
     * @return - TRAMTransportProfile - the TRAMTransportProfile
     */
    public TRAMTransportProfile getTransportProfile() {
        return tp;
    }

    /**
     * set the TRAMTransportProfile
     * @param  TRAMTransportProfile - the  TRAMTransportProfile to be set
     */
    public void setTransportProfile(TRAMTransportProfile tp) {
        this.tp = tp;
    }

    /**
     * gets the Multicast address in use.
     * @return InetAddress - theMulticast Address in Use.
     */
    public MulticastSocket getMulticastSocket() {
        return multicastSocket;
    }

    /**
     * set the Multicast Address to be used.
     * @param InetAddress - the Multicast Address to be used.
     */
    public void setMulticastSocket(MulticastSocket ms) {
        this.multicastSocket = ms;
    }

    /**
     * gets the UnicastSocket in use
     * @return DatagramSocket - the Unicast Socket in use.
     */
    public DatagramSocket getUnicastSocket() {
        return unicastSocket;
    }

    /**
     * set the UnicastSocket to be used for exchange of control messages.
     * @param DatagramSocket - the Unicast Socket to be used.
     */
    public void setUnicastSocket(DatagramSocket us) {
        this.unicastSocket = us;
    }

    /**
     * gets the UnicastPort in use.
     * @return int - the Unicast port in use.
     */
    public int getUnicastPort() {
        return unicastPort;
    }

    /**
     * sets the Unicast Port in use.
     * @param the Unicast port to be used.
     */
    public void setUnicastPort(int unicastPort) {
        this.unicastPort = unicastPort;
    }

    /**
     * gets the Data Transmission Start time.
     * @return long - the configured Data transmission start time.
     */
    public void getDataTxmStartTime(long start_time) {
        dataTxmStartTime = start_time;
    }

    /**
     * set the Datat Transmission Start time
     * @param Long The data transmission start time in secs.
     */
    public void setDataTxmStartTime(long start_time) {
        dataTxmStartTime = start_time;
    }

    /**
     * gets the Data transmission End time
     * @return Long Data transmission emd time in secs.
     */
    public long getDataTxmStartTime() {
        return dataTxmStartTime;
    }

    /**
     * Set the Data transmission End Time
     * @param  Long Data transmission End time to be set.
     */
    public void setDataTxmEndTime(long end_time) {
        dataTxmEndTime = end_time;
    }

    /**
     * gets the configured Average Minimum transmission rate.
     * @return long the Average minimum transmission rate in bps
     */
    public long getAvgMinRate() {
        return avgMinRate;
    }

    /**
     * set the Average minimum transmission rate
     * @param long the Average minimum transmission rate.
     */
    public void setAvgMinRate(long avgMinRate) {
        this.avgMinRate = avgMinRate;
    }

    /**
     * @return the current Beacon rate in ms
     */
    public long getBeaconRate() {
        return beaconRate;
    }

    /**
     * @param beaconRate The beacon rate to be set in ms
     */
    public void setBeaconRate(long beaconRate) {
        this.beaconRate = beaconRate;
    }

    /**
     * @return the current TRAMState
     */
    public byte getTRAMState() {
        return tramState;
    }

    /**
     * @param the current TRAMState.
     */
    public void setTRAMState(byte tramState) {
        this.tramState = tramState;
    }

    /**
     * @return the BeaconTTL in use
     */
    public byte getBeaconTTL() {
        return beaconTTL;
    }

    /**
     * @param the Beacon TTL value that is to be set
     */
    public void setBeaconTTL(byte beaconTTL) {
        this.beaconTTL = beaconTTL;
    }

    /**
     * @return the MsTTL in use
     */
    public byte getMsTTL() {
        return msTTL;
    }

    /**
     * @param MsTTL the msTTL value that is to be set
     */
    public void setMsTTL(byte msTTL) {
        this.msTTL = msTTL;
    }

    /**
     * @return the  GroupManagementBlock in use
     */
    public GroupMgmtBlk getGroupMgmtBlk() {
        return groupMgmtBlk;
    }

    /**
     * @param the GroupManagementBlock that is to be set
     */
    public void setGroupMgmtBlk(GroupMgmtBlk groupMgmtBlk) {
        this.groupMgmtBlk = groupMgmtBlk;
    }

    /**
     * @return the TRAM Statistics block.
     */
    public TRAMStats getTRAMStats() {
        return statsBlock;
    }

    /**
     * @param statsBlock an TRAM Statistics block
     */
    public void setTRAMStats(TRAMStats statsBlock) {
        this.statsBlock = statsBlock;
    }

    /**
     * @return the GroupMgmtThread object in use.
     */
    public synchronized GroupMgmtThread getGroupMgmtThread() {
        return groupMgmtThread;
    }

    /**
     * @param the GroupMgmtThread to be set
     */
    public synchronized void setGroupMgmtThread(GroupMgmtThread gmgmt_thrd) {
        groupMgmtThread = gmgmt_thrd;
    }

    /**
     * @return the InputDispThread object in use.
     */
    public synchronized InputDispThread getInputDispThread() {
        return inputDispThread;
    }

    /**
     * @param the InputDispThread object that is to be set
     */
    public synchronized void setInputDispThread(
				       InputDispThread inputDispThread) {
        this.inputDispThread = inputDispThread;
    }

    /**
     * Method: getUcastInputDispThread
     */
    public synchronized UcastInputDispThread getUcastInputDispThread() {
        return ucastInputDispThread;
    }

    /**
     * Method: setUcastInputDispThread
     */
    public synchronized void setUcastInputDispThread(
				 UcastInputDispThread ucastInputDispThread) {
        this.ucastInputDispThread = ucastInputDispThread;
    }

    /**
     * @return The BeaconGenThread in use
     */
    public synchronized BeaconGenThread getBeaconGenThread() {
        return beaconGenThread;
    }

    /**
     * @param the BeaconGenThread object that needs to be set
     */
    public synchronized void setBeaconGenThread(
				       BeaconGenThread beaconGenThread) {
        this.beaconGenThread = beaconGenThread;
    }

    /**
     * @return HelloThread object in use.
     */
    public synchronized HelloThread getHelloThread() {
        return helloThread;
    }

    /**
     * Method: setHelloThread
     */
    public synchronized void setHelloThread(HelloThread helloThread) {
        this.helloThread = helloThread;
    }

    /**
     * get the TRAMInputOutput object used by the PacketDBIoManager.
     * @return TRAMInputOutput - the PacketDb object.
     */
    public TRAMInputOutput getPacketDb() {
        return packetdb;
    }

    /**
     * @return the TRAM logging object.
     */
    public TRAMLogger getLogger() {
        return logger;
    }

    /**
     * @return the HeadAck thread reference.
     */
    public TRAMHeadAck getHeadAck() {
        return headAck;
    }

    /**
     * @param set the HeadAck reference.
     */
    public void setHeadAck(TRAMHeadAck headack) {
        this.headAck = headack;
    }

    /**
     * @return the MemberAck thread reference.
     */
    public TRAMMemberAck getMemberAck() {
        return memberAck;
    }

    /**
     * @param set the MemberAckThread reference.
     */
    public void setMemberAck(TRAMMemberAck memberack) {
        this.memberAck = memberack;
    }

    /**
     * @return the OutputDisp thread reference.
     */
    public OutputDispThread getOutputDispThread() {
        return outputDispThread;
    }

    /**
     * @param set the outputDispThread reference.
     */
    public void setOutputDispThread(OutputDispThread opdisp) {
        outputDispThread = opdisp;
    }

    /**
     * Performs TRAMClose operation. Waits for the HeadAck and MemberAck
     * threads to complete on their own and forces other threads that
     * may be running to stop.
     * Whenever a new thread is added to the TRAM control block, this
     * method needs to be updated to stop the new thread when the TRAM
     * operation is complete.
     */
    public final void doTRAMClose() {
        close(false);
    }

    /**
     * Performs TRAMAbort operation. Waits for the HeadAck and MemberAck
     * threads and other threads are forced to stop.
     */
    public final void doTRAMAbort() {
        close(true);
    }

    /**
     * A private method to perform the TRAM close operation.
     * Whenever a new thread is added to the TRAM control block, this
     * method needs to be updated to stop the new thread when the TRAM
     * operation is complete.
     * 
     * @param abortFlag is true if abort needs to be performed.
     * is false if graceful exit is desired.
     */
    private void close(boolean abortFlag) {
	if (multicastSocket == null || unicastSocket == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_INFO)) {
	        logger.putPacketln(this, 
		    "close called when sockets already closed!  ignored.");
	    }

	    return;
	}

        /*
         * Wait for the HeadAck thread to complete on its own.
         */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {
	    
	    logger.putPacketln(this, 
		"Attemting to close HeadAck Module");
	}

        if (headAck != null) {
            if (abortFlag == false) {
                headAck.waitToComplete();
            } else {
		/*
		 * Tell the members that you are signing off.
		 */
	    }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
		"Attemting to close MemberAck Module");
	}

        /*
         * Wait for the MemberAck thread to complete on its own.
         */
        if (memberAck != null) {
            if (abortFlag == false) {
                memberAck.waitToComplete();
            } else {
	        HeadBlock hb = groupMgmtBlk.getHeadBlock();
		if (hb != null) {
		    /*
		     * Tell the head you are signing off 
		     */
	            memberAck.sendAck((byte) 
			TRAMAckPacket.FLAGBIT_TERMINATE_MEMBERSHIP, hb, 9);

		    groupMgmtBlk.setHeadBlock(null);
		}
		memberAck.abortTimer();
            }
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
		"Attemting to close GroupMgmtThread Module");
	}

        /*
         * Once the HeadAck and The MemberAck threads are done, force ALL
         * other threads, if running, to stop.
         */
        if ((groupMgmtThread != null) && 
	    (groupMgmtThread.isAlive() == true)) {

            groupMgmtThread.terminate();
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
		"Attemting to close BeaconGenThread Module");
	}

        if ((beaconGenThread != null) && 
	    (beaconGenThread.isAlive() == true)) {

            beaconGenThread.terminate();
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
	        "Attemting to close Hello Thread Module");
	}

        if ((helloThread != null) && (helloThread.isAlive() == true)) {
            helloThread.terminate();
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
	        "Attemting to close InputDispatcher Module");
	}

        if ((inputDispThread != null) 
                && (inputDispThread.isAlive() == true)) {
            inputDispThread.terminate();
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
		"Attemting to close OutputDispatcher Module");
	}

        if ((outputDispThread != null) 
                && (outputDispThread.isAlive() == true)) {
            outputDispThread.terminate();
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SESSION)) {

	    logger.putPacketln(this, 
		"Attemting to close UnicastOutputDisp Module");
	}

        if ((ucastInputDispThread != null) 
                && (ucastInputDispThread.isAlive() == true)) {
            ucastInputDispThread.terminate();
        }

        /*
         * Now Close the open sockets.
         */
        if (simulator == null) {
            multicastSocket.close();
            unicastSocket.close();

	    multicastSocket = null;
	    unicastSocket = null;
        }
    }

    /*
     * returns the simulator object
     * @return the simulator object
     */

    TRAMSimulator getSimulator() {
        return simulator;
    }

    /*
     * injects the multicast packet into the input dispatcher
     * @param packet the multicast datagram packet
     */

    void simulateMulticastPacketReceive(DatagramPacket dp) {
        getInputDispThread().dispatchPacket(dp);
    }

    /*
     * injects the unicast packet into the input dispatcher
     * @param packet the unicast datagram packet
     */

    void simulateUnicastPacketReceive(DatagramPacket dp) {
        getUcastInputDispThread().dispatchPacket(dp);
    }

    /*
     * @return the rate adjuster object.
     */

    public TRAMRateAdjuster getRateAdjuster() {
        return tramRateAdjuster;
    }

    /*
     * Set the sequence number of the packet last known to have been 
     * sent by the sender.
     * This field is set when Group mgmt thread or the MemberAck or
     * HeadAck modules when they detect via the Hello message or 
     * Ack or beacon messages that a packet has been sent.
     */

    public void setLastKnownSequenceNumber(int value) {
        lastKnownSequenceNumber = value;
    }

    /*
     * @return the sequence number of the last packet sent
     */

    public int getLastKnownSequenceNumber() {
        return lastKnownSequenceNumber;
    }

    /** 
     * Set the lastest forgetBeforeSeqNum heard. This could be from the
     * data messages, beacon message etc.
     *
     * @param int - last known forgetBeforeSeqNum
     */
    public void setLastKnownForgetBeforeSeqNum(int lkfb) {
	if (lastKnownForgetBeforeSeqNum > lkfb) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATAMESG)) {

		logger.putPacketln(this,
		    "Cannot decrease value of forgetBeforeSeqNum from " + 
		    lastKnownForgetBeforeSeqNum + " to " + lkfb);
	    }
	} else 
	    lastKnownForgetBeforeSeqNum = lkfb;
    }

    /** 
     * get the last known forgetBeforeSeqNum
     *
     * @return int - the value of forgetBeforeSeqNum
     */
    public int getLastKnownForgetBeforeSeqNum() {
	return lastKnownForgetBeforeSeqNum;
    }

    /*
     * Set the sequence number of the last First time transmission
     * packet sent. This field is set by the socket classes when a 
     * packet is handed down to the next level. It does not indicate 
     * that the packet has actually been sent.
     */

    public void setLastFirstTimeTxmSequenceNumber(int value) {
        lastFirstTimeTxmSequenceNumber = value;
    }

    /*
     * @return the sequence number of the last packet sent
     */

    public int getLastFirstTimeTxmSequenceNumber() {
        return lastFirstTimeTxmSequenceNumber;
    }


    /*
     * @return the current data cache object.
     */

    public TRAMDataCache getTRAMDataCache() {
        return dataCache;
    }

    /*
     * @return true - if the sender has completed the dataTransmission
     * false - if the sender is yet to start data transmission or
     * is in the middle of data transmission.
     */

    public boolean isDataTransmissionComplete() {
        return dataTransmissionComplete;
    }

    /*
     * Set the dataTransmissionComplete status flag.
     * 
     * @param dataTransmissionStatus value - true if sender has completed
     * data transmission.
     * - false if the sneder is yet to
     * start or is in the middle of
     * data transmission.
     * 
     */

    public synchronized void setDataTransmissionComplete(
					boolean dataTransmissionStatus) {
        dataTransmissionComplete = dataTransmissionStatus;
    }

    /*
     * @return the lastHeard timestamp maintained for the sender.
     */

    public long getLastHeardFromTheSender() {
        return lastHeardFromTheSender;
    }

    /*
     * Set the lastHeard timestamp maintained for the sender with the
     * specified value.
     * 
     * @param the timestamp to be assigned to the lastHeardFromTheSender
     * variable.
     */

    public synchronized void setLastHeardFromTheSender(long timeStamp) {
        lastHeardFromTheSender = timeStamp;
    }

    /*
     * Set the expected time to transmit or receive an ack window's worth
     * of new data packets. This value is in milliseconds.
     */

    public void setAckInterval(long time) {
        ackInterval = time;
    }

    /*
     * Return the expected time to transmit or receive and ack windows worth
     * of new data packets. This value is in milliseconds.
     */

    public long getAckInterval() {
        return ackInterval;
    }

    /*
     * The data cache has filled up. If we're the sender, set the
     * cacheFull flag to true and set the data rate to zero. This
     * gives the members some time to catch up. When the cache clears
     * up the cache full flag will be cleared, the dataRate will be set
     * to its initial value, and the outputDispatcher will be notifed
     * to start up again.
     */

    public void setCacheFull(boolean flag) {
        cacheFull = flag;
    }

    public boolean isCacheFull() {
        return cacheFull;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int value) {
        sessionId = value;
    }

    public AuthenticationModule getAuthenticationModule() {
        return authenticationModule;
    }

    public void setAuthenticationModule(AuthenticationModule authM) {
        authenticationModule = authM;
    }

    /**
     * Start Authentication Module.
     */
    private void startAuthenticationModule(TRAMTransportProfile tp) 
            throws IOException, SignatureException {
        authenticationModule = null;

        if (tp.isUsingAuthentication() == false) {
            return;
        } 

        AuthenticationSpec spec = null;
        String fname = tp.getAuthenticationSpecFileName();

        if (fname == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
	        logger.putPacketln(this, 
		    "No authentication FileName Specified. " +
                    "Using Default file - " +
                    AuthenticationSpec.DEFAULT_SPEC_FILENAME);
	    }

            fname = AuthenticationSpec.DEFAULT_SPEC_FILENAME;

            tp.setAuthenticationSpecFileName(fname);
        }

        // Start the Authentication module if needed

        spec = AuthenticationSpec.readFromFile(fname);

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_SECURITY)) {

            logger.putPacketln(this, 
		"Starting Authentication Module");
	}

        authenticationModule = new AuthenticationModule(spec, 
            tp.getAuthenticationSpecPassword());

        return;
    }

    /*
     * Set the highest sequence number that the sender is allowed to send
     * at full speed.  This number is the minimum value of the highest number
     * that each member in the group would like the sender to send.
     */
    public void setHighestSequenceAllowed(int highestSequenceAllowed) {
	if (highestSequenceAllowed > this.highestSequenceAllowed) {

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
	            "current highest " + this.highestSequenceAllowed +
		    " new highest " + highestSequenceAllowed +
		    " C Win " + tp.getCongestionWindow());
	    }

	    this.highestSequenceAllowed = highestSequenceAllowed;
	} else if (highestSequenceAllowed < this.highestSequenceAllowed) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
	    	    "current " + this.highestSequenceAllowed +
		    " is greater than new " + highestSequenceAllowed +
		    " C Win " + tp.getCongestionWindow());
	    }
	}
    }

    /*
     * Get the highest sequence number that the sender is allowed to send.
     */
    public int getHighestSequenceAllowed() {
	return highestSequenceAllowed;
    }

    /*
     * Get the local IP address used for the multicast and unicast sockets.
     */
    public InetAddress getLocalHost() {
	return myLocalAddress;
    }

    /*
     * Get a new multicast socket and set the interface address 
     * to myLocalAddress.
     */
    public MulticastSocket newMulticastSocket() throws IOException {
	MulticastSocket ms = new MulticastSocket();

	ms.setInterface(myLocalAddress);

	return ms;
    }

}
