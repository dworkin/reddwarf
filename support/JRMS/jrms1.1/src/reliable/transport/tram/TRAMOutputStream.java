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
 * TRAMOutputStream.java
 * 
 * Module Description:
 * 
 * This module provides the TRAM output stream interface. As the
 * byte stream is received from the application, the data is stored
 * in a output buffer. The buffer will be sent out when a
 * predetermined buffer size is reached or can be forced out via the
 * flush command.
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.*;
import java.net.*;
import com.sun.multicast.reliable.authentication.*;
import com.sun.multicast.reliable.transport.NoMembersException;
import java.security.*;

class TRAMOutputStream extends OutputStream {
    private TRAMControlBlock tramblk = null;
    private TRAMTransportProfile tp;
    private TRAMInputOutput pktio = null;
    private int maxBuf;
    private DatagramPacket dp = null;
    private byte writebuffer[];
    private TRAMSeqNumber seqNumber = new TRAMSeqNumber();
    private int index = 0;
    private TRAMLogger logger = null;
    private boolean closeDone = false;
    private TRAMRateAdjuster rateAdjuster;
    private int sigLen = 0;     // length of the signature when used.
    private AuthenticationModule authMod = null;

    /**
     * Create a new OutputStream for the TransportProfile specified.
     * The TransportProfile and multicast socket as saved.
     * 
     * @param tramblk - TRAM control block
     * @param TRAMInputOutput Interface to the TRAM database module.
     * 
     */
    public TRAMOutputStream(TRAMControlBlock tramblk, TRAMInputOutput pktio) {
        this.tramblk = tramblk;
        this.tp = tramblk.getTransportProfile();
        this.pktio = pktio;
        sigLen = 0;
        authMod = tramblk.getAuthenticationModule();

        if ((tp.isUsingAuthentication()) && (authMod != null)) {
            sigLen = authMod.getSignatureSize();
        } else {
            authMod = null;
        }

        logger = tramblk.getLogger();
        maxBuf = tp.getMaxBuf() - TRAMDataPacket.TRAMDATAHEADERLENGTH 
                 - TRAMPacket.TRAMHEADERLENGTH - sigLen;
        writebuffer = new byte[maxBuf];
        rateAdjuster = tramblk.getRateAdjuster();
    }

    /**
     * At a minimum, each OutputStream implementation must create a
     * write(int b) method. This implementation loads the byte passed
     * in into the next slot in the transmit buffer. when the buffer is
     * full, it hands it off to the multicast socket for transmission.
     * 
     * @param b an integer containing the next byte to send.
     * @Exception - IOException.
     */
    public void write(int b) throws IOException {
        if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
            throw new IOException("RECEIVE ONLY Transport Mode");
        }

	if (tramblk.getTRAMStats().getReceiverCount() == 0) {
            throw new NoMembersException();
	}

        writebuffer[index] = (byte) b;
        index++;

        if (index >= maxBuf) {
            this.sendbuffer(writebuffer);

            index = 0;
        }
    }

    /**
     * At a minimum, each OutputStream implementation must create a
     * write(int b) method. This implementation loads the byte passed
     * in into the next slot in the transmit buffer. when the buffer is
     * full, it hands it off to the multicast socket for transmission.
     * 
     * @param b[] - a byte array containing the next stream of bytes to send.
     * @Exception - IOException.
     */
    public void write(byte[] b) throws IOException {
        if (tp.getTmode() == TMODE.RECEIVE_ONLY) {
            throw new IOException("RECEIVE ONLY Transport Mode");
        }

	if (tramblk.getTRAMStats().getReceiverCount() == 0) {
            throw new NoMembersException();
	}

        for (int i = 0; i < b.length; i++) {
            writebuffer[index] = b[i];
            index++;

            if (index >= maxBuf) {
                this.sendbuffer(writebuffer);

                index = 0;
            }
        }
    }

    /**
     * This method hands the byte array specified to the multicast
     * socket for transmission.
     * 
     * @param b[] a byte address to be sent on the multicast socket.
     * @Exception IOException
     */
    private void sendbuffer(byte b[]) throws IOException {
        TRAMDataPacket sdp = new TRAMDataPacket(tramblk, b, index, sigLen);

        sdp.setAddress(tp.getAddress());
        sdp.setPort(tp.getPort());

        int sequenceNumber = seqNumber.getSeqNumber();

        sdp.setSequenceNumber(sequenceNumber);
        tramblk.setLastFirstTimeTxmSequenceNumber(sequenceNumber);
        tramblk.getTRAMStats().addBytesSent(sdp.getDataLength());
        tramblk.getTRAMStats().addPacketsSent();

        /*
         * Issue:
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
			mesgLen + " BufferLen is " + buf.length);
		}
                
                sdp.writeBuffer(signature, signature.length, 
                    sdp.getDataLength());

                sdp.setSubType(subMesgType);
            } catch (SignatureException se) {

                // sdp.setSubType(subMesgType);

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_SECURITY)) {

                    logger.putPacketln(this, 
			"Signature Exception!!!");
		}

                throw new IOException("Unable to Sign");
            }
        }

        try {
            pktio.putPacket(sdp);
            seqNumber.incrSeqNumber();

            // Chage the TRAM State to Data Txm.

            if (tramblk.getTRAMState() == TRAM_STATE.PRE_DATA_BEACON) {
                tramblk.setTRAMState(TRAM_STATE.DATA_TXM);
            } 
        } catch (IOException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this,
                    "TRAMOutputStream: IOexception in sendbuffer");
	    }

            throw new IOException();
        }
    }

    /**
     * The flush method is optionally implemented to send any pending data.
     * In this implementation, any data currently sitting in the writeBuffer
     * is transmitted. The buffer index is reset to correctly position
     * the next data input.
     * 
     * @param - IOException.
     */
    public void flush() throws IOException {
        this.sendbuffer(writebuffer);

        index = 0;
    }

    /**
     * The close method shuts down the output stream after flushing the
     * transmit queue. All data previously transmitted will be handed to the
     * network prior to tearing down the connection.
     */
    public void close() throws IOException {

        /*
         * For the Stream interface a close() can be called
         * from the StreamSocket as well as from the TRAMOutputStream.
         * The socket close() inturn invokes this close().
         * It is important that the close() is performed only once.
         * The boolean flag closeDone is used to ensure that the
         * close() happens only once. If close has been already performed
         * the subsequent calls to close() will just return at this
         * point.
         */
        if (closeDone == true) {
            return;
        } 
        if (index != 0) {
            flush();
            index = 0;
        }

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "Closing the Socket");
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
        closeDone = true;

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "TRAMOutputSreamSocket: Exiting");
	}
    }

}

