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
 * TRAMPacketSocket.java
 *
 * Module Description:
 * 
 * This class defines the TRAM Stream API for the TRAM Transport.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.MulticastSocket;
import java.io.IOException;
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.authentication.*;






import java.security.*;
import java.io.*;

/**
 * The TRAMPacketSocket implements RMPacketSocket Interface. TRAMPacketSocket
 * allows applications to send and receive packets, set the interface over
 * which data is to be sent, and close the socket after completion or
 * to abort the connection before the session ends.
 */
public class TRAMPacketSocket implements RMPacketSocket {
    private TRAMControlBlock tramblk = null;
    private TRAMInputOutput pktio = null;
    private TRAMTransportProfile tp = null;
    private TRAMLogger logger = null;
    private TRAMSeqNumber outSeqNumber = null;  // outgoing packet seq number.
    // private TRAMRateAdjuster rateAdjuster;
    private boolean dataEnd = false;
    private boolean sender;
    private boolean headonly = false;
    private TRAMSimulator simulator = null;


    public TRAMPacketSocket() {}

    public TRAMPacketSocket(TRAMSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * The connect method basically spawns the TRAM Module. This methods
     * creates the multicast socket, joins the multicast group and then
     * spawns various TRAM threads. Also created by this method is the
     * TRAM packet Database where all the incoming(from the net) and outgoing
     * (to the net) TRAM packets are stored.
     * 
     * @param TRAMTransportProfile that will be used to initialize the TRAM 
     * module
     * 
     * @param Inetaddress of the interface to use for the multicast socket.
     *
     * @exception IOException in the event of failing to create a multicast 
     * socket.
     */
    void connect(TRAMTransportProfile profile, InetAddress interfaceAddress) 
	throws IOException {

        tp = (TRAMTransportProfile) profile.clone();
        sender = ((tp.getTmode() & 0xff) == TMODE.SEND_ONLY);
        headonly = tp.getSAhead();

        MulticastSocket ms = null;

        if (simulator == null) {
            ms = new MulticastSocket(tp.getPort());


	    /* 
	     * test code. If receiveBufferSize is specified in tp 
	     * then try to set it
	     */
	    boolean rbSet = false;

	    if (tp.getReceiveBufferSize() != 0) {
		try {
		    ms.setReceiveBufferSize(tp.getReceiveBufferSize());
		    rbSet = true;
		} catch (Exception e) {
		}
	    }
			
	    if (rbSet == false) {
		try {
		    int receiverBufferSize = ms.getReceiveBufferSize();

		    for (int size = 256*1024; size > receiverBufferSize; 
			size -= 1024) {

			try {
			    ms.setReceiveBufferSize(size);
			    break;
			} catch (IllegalArgumentException e) {
			    /* try again with a smaller size */
			} catch (Exception e) {
			    break;	/* something else is wrong */
			}
		    }
		} catch (Exception e) {}
	    }

	    InetAddress ia = interfaceAddress;

	    if (ia == null)
		ia = InetAddress.getLocalHost();

            try {
                ms.setInterface(ia);
            } catch (SocketException e) {
                throw new IOException(e + 
                    " Unable to set multicast interface address to " + ia);
            }

            ms.joinGroup(tp.getAddress());
        }

        try {
            if (simulator == null) {
                tramblk = new TRAMControlBlock(ms, tp);
            } else {
                tramblk = new TRAMControlBlock(ms, tp, simulator);
            }
        } catch (TRAMControlBlockException e) {
            // e.printStackTrace();

            throw new IOException(e + "Unable to Create TRAMControlBlock");
        }

        /*
         * Instantiate the outgoing sequence number class if the
         * Transport mode is set to non RECEIVE_ONLY mode.
         */
        if (tp.getTmode() != TMODE.RECEIVE_ONLY) {
            outSeqNumber = new TRAMSeqNumber(1);
        }

        /*
         * Get the IO interface to the Packet db manager.
         */
        pktio = tramblk.getPacketDb();
        logger = tramblk.getLogger();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this,
		"Receiver buffer size is " + ms.getReceiveBufferSize());
	}
    }

    /**
     * The getInterface method returns the InetAddress of the local port
     * that data is transmitted on if other than the default.
     * 
     * @returns the address of the local port that data is transmitted on.
     */
    public InetAddress getInterface() throws SocketException {
        MulticastSocket ms = tramblk.getMulticastSocket();

        return ms.getInterface();
    }

    /**
     * Set the interface which data will be transmitted on. This is useful on
     * systems with multiple network interfaces.
     * 
     * @param ia the InetAddress of the interface to transmit data on.
     */
    public void setInterface(InetAddress ia) throws SocketException {
        MulticastSocket ms = tramblk.getMulticastSocket();

        ms.setInterface(ia);
    }

    /**
     * The send method transmits the DatagramPacket over the multicast
     * connection.
     * 
     * @param dp the DatagramPacket to be sent.
     * @exception IOException is raised if an error occurs sending the data.
     * We need to add the TRAM header on top of the passed in packet... hence
     * we need to create another datagram packet large enough to hold the
     * TRAM header plus the passed in data and then hand it over to the lower
     * layer to transmit it.
     */
    public void send(DatagramPacket dp) throws IOException {
	send(dp, 0);
    }

    /**
     * This send method transmits the DatagramPacket over the multicast
     * connection. This is the version of send that allows application to
     * specify a forgetBefore sequence number.
     * 
     * @param dp the DatagramPacket to be sent.
     * @param fb the forgetBeforeSeqNum to be included in packet.
     *
     * @return int the assigned sequence number for dp.
     * 
     * @exception IOException is raised if an error occurs sending the data.
     * We need to add the TRAM header on top of the passed in packet... hence
     * we need to create another datagram packet large enough to hold the
     * TRAM header plus the passed in data and then hand it over to the lower
     * layer to transmit it.
     */
    public int send(DatagramPacket dp, int fb) throws IOException {
        if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
            throw new IOException("RECEIVE ONLY Transport Profile");
        }

	if (tramblk.getTRAMStats().getReceiverCount() == 0) {
            throw new NoMembersException();
        }

        int dataLen = dp.getLength();



 

        int sigLen = 0;
        AuthenticationModule authMod = tramblk.getAuthenticationModule();

        if ((tp.isUsingAuthentication()) && (authMod != null)) {
            sigLen = authMod.getSignatureSize();
        }
        if (dataLen > 
	    (tp.getMaxBuf() - TRAMDataPacket.TRAMDATAHEADERLENGTH - 
	    TRAMPacket.TRAMHEADERLENGTH - sigLen)) {

            throw new IOException("DatagramPacket exceeds " 
                                  + "maximum buffer size of " 
                                  + tp.getMaxBuf());
        }

        byte[] eData = null;
        int eDataLen = 0;
	eData = dp.getData();
	eDataLen = dp.getLength();

        TRAMDataPacket sdp = new TRAMDataPacket(tramblk, eData, eDataLen, 
	    sigLen);

        sdp.setAddress(tp.getAddress());
        sdp.setPort(tp.getPort());
        sdp.setSequenceNumber(outSeqNumber.getSeqNumber());
		
	/* 
	 * The forgetBeforeSeqNum should be less than or equal to
	 * the sequenceNumber just set.
	 */
	if (fb != 0) {
	    if (fb > sdp.getSequenceNumber() || 
	        fb < tramblk.getLastKnownForgetBeforeSeqNum()) {

	        throw new IOException("forgetBeforeSeqNum (" + fb + 
		    ") must be less than or equal to the sequence number (" + 
		    sdp.getSequenceNumber() + 
		    ") and greater than or equal to the previous " +
		    "forgetBeforeSeqNum (" + 
		    tramblk.getLastKnownForgetBeforeSeqNum() + ")");
	    } else 
	        sdp.setForgetBeforeSeqNum(fb);

	    tramblk.setLastKnownForgetBeforeSeqNum(fb);
	}

	tramblk.setLastFirstTimeTxmSequenceNumber(outSeqNumber.getSeqNumber());

	/*
         * Issue
         * Currently the HA field is considered to be a mutable field
         * This is because, the packet that is just produced is not
         * guaranteed to be shipped out immediately. The packet dispatch
         * is goverened by the dispatch algorithm. Hence if we load the HA
         * interval now, by the time the packet gets dispatched, the HA
         * interval may be outdated. Making it a mutable field, opens the
         * door for malicious replacements!!! and as a result can cause
         * heads to send at a rate higher than that desired by the sender.
         */
        sdp.setHaInterval((short) 0);

        // Now Sign the Packet

        if (authMod != null) {
            int mesgLen = TRAMPacket.TRAMHEADERLENGTH 
                          + TRAMDataPacket.TRAMDATAHEADERLENGTH 
                          + sdp.getDataLength();
            int jk = TRAMPacket.TRAMHEADERLENGTH 
                     + TRAMDataPacket.TRAMDATAHEADERLENGTH;

            /*
             * Sub message type field is mutable field. Hence set it to
             * 0 and comute the signature and then reset to the loaded value.
             */
            int subMesgType = sdp.getSubType();

            sdp.setSubType(0);

            byte[] signature;
            byte[] buf = sdp.getBuffer();

            try {
                signature = authMod.sign(buf, 0, mesgLen);

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_SECURITY)) {

		    logger.putPacketln(this,
			"Signature Length is " +
			signature.length + "Mesg Length is " +
			mesgLen + " BufferLen is " + buf.length +
			" DataLen is " +  sdp.getDataLength());
		}

                sdp.writeBuffer(signature, signature.length, 
                                sdp.getDataLength());
                sdp.setSubType(subMesgType);
            } catch (SignatureException se) {

                // sdp.setSubType(subMesgType);

		if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
		    logger.putPacketln(this, "Signature Exception!!!");
		}

                throw new IOException("Unable to Sign");
            }
        }

        try {
	    tramblk.getTRAMStats().addBytesSent(eDataLen);
            tramblk.getTRAMStats().addPacketsSent();

            // hmmm .... for debug only ....
	    sdp.setForgetBeforeSeqNum(fb);
	    pktio.putPacket(sdp);
            outSeqNumber.incrSeqNumber();

            // Chage the TRAM State to Data Txm.

            if (tramblk.getTRAMState() == TRAM_STATE.PRE_DATA_BEACON) {
                tramblk.setTRAMState(TRAM_STATE.DATA_TXM);
            } 
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATAMESG)) {

                logger.putPacketln(this, "Unable to send packet");
	    }

            throw new IOException();
        }

	return (sdp.getSequenceNumber());
    }

    /**
     * The receive method returns the next RMDatagramPacket.
     * 
     * @returns the next packet
     * @exception IOException is thrown if an error occurs retrieving the
     * data.
     * @exception SessionDoneException if the end of the data
     * stream is detected.
     * @exception IrrecoverableDataException when a packet is lost
     * in transmission and cannot be recovered.
     * @exception SessionDownException when the sender has left the
     * multicast group.
     */
    public DatagramPacket receive() 
            throws IOException, SessionDoneException, 
                   IrrecoverableDataException, SessionDownException,
		   MemberPrunedException {

        if (headonly) {
            monitor();

            return null;
        } else {

            /*
             * If we've already seen the data end, don't allow anymore reads.
             * They'll just hang anyway.
             */
            if (dataEnd) {
                throw new SessionDoneException();
            } 

            /*
             * Do not allow a sender to receive its own data.
             */
            if (sender) {
                throw new IOException(
				  "Senders may not receive their own data");
            } 

            TRAMDataPacket pk = (TRAMDataPacket) pktio.getPacket();

	    /* 
	     * this is where we do the filterting of packets that are in the 
	     * inputDatabase but are now to be forgetten due to arrival of 
	     *  newer packets.
	     */

	    // THE CODE BELOW PREVENTS DELIVERY OF LATE PACKETS.
	    // For now we are not using FB to control the delivery of late
	    // packets, we just use FB to prevent the recovery of old packets.
            // while (pk.getSequenceNumber() < 
	    //    tramblk.getLastKnownForgetBeforeSeqNum()) {
	    //    pk = (TRAMDataPacket) pktio.getPacket();
	    //    System.out.println("TRAMPacketSocket receive() : " +
	    //        Dropping aged packet : ");
	    //    tramblk.getTRAMStats().addPacketsNotDelivered(1);
	    // }
			
            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_TXDONE) != 0) {
                dataEnd = true;

                throw new SessionDoneException();
            }
            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_SESSION_DOWN) != 0) {
                throw new SessionDownException();
            } 
            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_UNRECOVERABLE) != 0) {
	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATAMESG)) {

	            logger.putPacketln(this,
			"IrrecoverableDataException for sequence number " + 
			pk.getSequenceNumber());
		}
                throw new IrrecoverableDataException();
            } 
            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_MEMBER_PRUNED) != 0) {
                throw new MemberPrunedException();
            } 

            byte[] dataAndSig = pk.getData();
            int dataLen = pk.getDataLength();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATAMESG)) {

	        logger.putPacketln(this,
		    "Passing Packet # " + pk.getSequenceNumber() + 
		    " and forgetBeforeSeq # "+ pk.getForgetBeforeSeqNum() +
		    " of size " + dataLen + " to the application.");
	    }






            DatagramPacket dp;
	    try {
		// Try the jdk1.2 method 
		dp = new DatagramPacket(dataAndSig, 0, dataLen, 
					pk.getAddress(), pk.getPort());
	    } catch (NoSuchMethodError e) {

		// jdk1.2 method doesn't exist so try pre-jdk1.2 name 
		
		dp = new DatagramPacket(dataAndSig, dataLen, 
					pk.getAddress(), pk.getPort());
	    }


            /*
             * 
             * String fname = "/tmp/" + "R" + pk.getSequenceNumber() + ".out";
             * FileOutputStream ostream = new FileOutputStream(fname);
             * ObjectOutputStream p = new ObjectOutputStream(ostream);
             * p.write(dp.getData(),0,dp.getLength());
             * p.flush();
             * ostream.close();
             * 
             */
            return dp;
        }
    }

    /**
     * Lets the application monitor the session without receiving
     * packets.  When the session is done, or aborts, an exception
     * is generated.
     */
    public void monitor() 
            throws IOException, SessionDoneException, 
                   IrrecoverableDataException, SessionDownException {
        while (true) {
            TRAMDataPacket pk = (TRAMDataPacket) pktio.getPacket();

            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_TXDONE) != 0) {
                throw new SessionDoneException();
            } 

            // throw new SessionDownException();

            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_SESSION_DOWN) != 0) {
                throw new SessionDownException();
            } 
            if ((pk.getFlags() & TRAMDataPacket.FLAGBIT_UNRECOVERABLE) != 0) {
                throw new IrrecoverableDataException();
            } 
        }
    }

    /**
     * Abort the current connection. Any data still waiting to be transmitted
     * is dropped. The connection is shutdown and this socket is closed.
     */
    public void abort() {
        if (tramblk != null) {
            tramblk.doTRAMAbort();
        }
    }

    /**
     * The close method shuts down the socket after flushing the transmit
     * queue. All data previously transmitted will be handed to the network
     * prior to tearing down the connection.
     */
    public void close() {
        if (logger != null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_SESSION)) {

                logger.putPacketln(this, 
		    "TRAMPacketSocket: Closing the Socket");
	    }
        } 

        /*
         * If the connect() has been invoked, the tramblk will be
         * valid. If it is equal to null then it implies that
         * connect() has not been invoked. Hence just return.
         */
        if (tramblk == null) {
            return;
        } 

        /*
         * Send the DataEnd Packet if the node is performing the
         * role of a sender.
         */
        if (tp.getTmode() != TMODE.RECEIVE_ONLY) {

            /*
             * Spawn the Beacon Thread to generate the Post beacon
             * messages.
             */
            tramblk.setTRAMState(TRAM_STATE.POST_DATA_BEACON);
        }

        /*
         * Perform the TRAM Close operation
         */
        tramblk.doTRAMClose();
        tramblk = null;

        // No logger support at this point. everything is closed.

    }

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     */
    public int getMaxLength() {
        return (tp.getMaxBuf());
    }

    /**
     * This method returns a clone of the TransportProfile in use in
     * this socket.
     * 
     * @return a cloned TransportProfile
     */
    public TransportProfile getTransportProfile() {
        return (TransportProfile) tp.clone();
    }

    /**
     * This method returns a clone of the Statistics block in use in
     * this socket.
     * 
     * @return a cloned TRAM statistics block
     */
    public RMStatistics getRMStatistics() {
        return (RMStatistics) tramblk.getTRAMStats().clone();
    }

    /**
     * Injects the multicast packet into the input dispatcher
     * @param packet the multicast datagram packet
     */
    public void simulateMulticastPacketReceive(DatagramPacket dp) {
        tramblk.simulateMulticastPacketReceive(dp);
    }

    /**
     * Injects the unicast packet into the input dispatcher
     * @param packet the unicast datagram packet
     */
    public void simulateUnicastPacketReceive(DatagramPacket dp) {
        tramblk.simulateUnicastPacketReceive(dp);
    }


    /** 
     * Sets the Max receiver rate at the transport profile associated with
     * this socket. 
     * @param long max receiver data rate
     */
    public void setReceiverMaxDataRate(long rate) {
	tp.setReceiverMaxDataRate(rate);
    }

    /** 
     * Gets the Max receiver rate at the transport profile associated with
     * this socket.
     * @return long max receiver data rate
     */
    public long getReceiverMaxDataRate() {
	return tp.getReceiverMaxDataRate();
    }







}

