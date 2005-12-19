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
 * LRMPPacketSocket.java
 */
package com.sun.multicast.reliable.transport.lrmp;

import inria.net.lrmp.Lrmp;
import inria.net.lrmp.LrmpEventHandler;
import inria.net.lrmp.LrmpException;
import inria.net.lrmp.LrmpPacket;
import inria.net.lrmp.LrmpProfile;
import java.io.EOFException;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Vector;
import com.sun.multicast.util.ImpossibleException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.*;






/**
 * An RMPacketSocket for the LRMP transport.
 * 
 * @see com.sun.multicast.reliable.transport.RMPacketSocket
 */
public class LRMPPacketSocket implements RMPacketSocket, LrmpEventHandler {
    private LRMPTransportProfile tp;
    private Lrmp lrmp;
    private Vector inputQ = new Vector();
    private boolean first = true;
    private int port;
    private boolean eof = false;
    private InetAddress iface = 
        null;       // Interface (can't be changed from system default)
    private static int maxLength = 0;

    /**
     * Create a new LRMPPacketSocket for the specified TransportProfile.
     * 
     * <P>This constructor is package local because it should only be
     * used by LRMPTransportProfile.createRMPacketSocket.
     * 
     * @param tProfile the transport profile to use
     */
    LRMPPacketSocket(LRMPTransportProfile tProfile) {
        tp = (LRMPTransportProfile) tProfile.clone();
        port = tp.getPort();

        LrmpProfile profile = new LrmpProfile();

        profile.setEventHandler(this);

        profile.bandwidth = (int) ((tp.getMaxDataRate() * 8) / 1000);

        try {
            lrmp = new Lrmp(tp.getAddress().getHostAddress(), port, 
                            tp.getTTL(), profile);

            lrmp.startSession();
        } catch (LrmpException e) {
            throw new ImpossibleException(e);
        }
    }

    /**
     * The getInterface method returns the InetAddress of the local port
     * that data is transmitted on if other than the default.
     * 
     * @returns the address of the local port that data is transmitted on.
     */
    public InetAddress getInterface() throws SocketException {
        if (iface == null) {
            try {
                iface = new MulticastSocket().getInterface();
            } catch (IOException e) {

                // @@@ Should wrap this in an InternalException or something.

                throw new SocketException("unable to getInterface");
            }
        }

        return (iface);
    }

    /**
     * Return a copy of the transport profile this socket is using.
     * 
     * @return a clone of the transport profile this socket is using.
     */
    public TransportProfile getTransportProfile() {
        return (TransportProfile) tp.clone();
    }

    /**
     * Returns the latest snapshot of the maintained Transport
     * statistics block.
     * 
     * @return a clone of the statistics block maintained by this socket.
     * @exception UnsupportedException when transport does not support this.
     * 
     */
    public RMStatistics getRMStatistics() throws UnsupportedException {
        throw new UnsupportedException();
    }

    /**
     * Set the interface on which the data will be transmitted on. This is
     * useful on systems with multiple network interfaces.
     * 
     * @param ia the InetAddress of the interface to transmit data on.
     * @exception UnsupportedException always, since LRMP does not support this operation
     */
    public void setInterface(InetAddress ia) throws UnsupportedException {
        throw new UnsupportedException("can't change interface on an LRMPRMPacketSocket");
    }

    /**
     * The send method transmits a DatagramPacket over the multicast
     * connection.
     * 
     * @param dp the DatagramPacket to be sent.
     * @exception IOException is raised if an error occurs sending the data.
     */
    public void send(DatagramPacket dp) throws IOException {
        int len1 = dp.getLength();

        // @@@ Should add fragmentation code to handle this.

        if (getMaxLength() < len1) {
            throw new IOException("packet too long to send via LRMP");
        } 

        LrmpPacket pack = new LrmpPacket(len1);

        // Copy in data and set length

        System.arraycopy(dp.getData(), 0, pack.getDataBuffer(), 
                         pack.getOffset(), len1);
        pack.setDataLength(len1);
        pack.setFirst(first);

        first = false;

        try {
            lrmp.send(pack);
        } catch (LrmpException e) {

            // @@@ Should wrap this in an InternalException or something.

            throw new IOException("unable to send packet due to LrmpException");
        }
    }

    /**
     * The receive method returns the next DatagramPacket.
     * 
     * @returns the next packet
     * @exception IOException is thrown if an I/O error occurs
     */
    public DatagramPacket receive() throws IOException, SessionDoneException {
        LrmpPacket pack = null;

        if (eof)
	    // previous packet was the last one	
            throw new SessionDoneException();

        // Fetch a packet off the input queue.

        synchronized (inputQ) {
            while (pack == null) {
                if (inputQ.size() > 0) {
                    pack = (LrmpPacket) inputQ.firstElement();

                    inputQ.removeElementAt(0);
                } else {
                    try {
                        inputQ.wait(30000);
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                }
            }
        }

	if (pack.isLast()) {
	    if (pack.getDataLength() == 0)
		// tell now
                throw new SessionDoneException();
	    else	
		// wait until next time
                eof = true;
	}

        // Copy data into a new DatagramPacket
        // @@@ Ignoring first flag for now

        byte data[] = new byte[pack.getDataLength()];

        System.arraycopy(pack.getDataBuffer(), pack.getOffset(), data, 0, 
                         data.length);

        DatagramPacket dp = new DatagramPacket(data, data.length, 
                                               pack.getAddress(), port);

        return (dp);
    }

    /**
     * Abort the current connection. All packets in the send queue are lost.
     */
    public void abort() {
        lrmp.stopSession();
    }

    /**
     * The close method shuts down the socket after flushing the transmit
     * queue. All data previously transmitted will be handed to the network
     * prior to tearing down the connection.
     */
    public void close() {

        // Sign off with an EOF packet

        LrmpPacket pack = new LrmpPacket();

        pack.setFirst(first);
        pack.setLast(true);

        try {
            lrmp.send(pack);
        } catch (LrmpException e) {}

        // Allow a while for repairs.

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {}

        lrmp.stopSession();
    }

    /**
     * Gets the current current value of maximum rate of data transmission in
     * bytes/second.
     * @return the current value for the maximum data rate in bytes/second
     */
    public long getMaxDataRate() {
        return tp.getMaxDataRate();
    }

    /**
     * Change the current data rate value. This changes the active value and
     * the value in the current transport profile.
     * 
     * @param maxDataRate the rate in bytes/second to transmit data.
     * @exception RMException if a JRMS-related exception occurs
     */
    public void setMaxDataRate(long maxDataRate) throws RMException {
        if (maxDataRate == tp.getMaxDataRate()) {
            return;
        } 

        tp.setMaxDataRate(maxDataRate);

        LrmpProfile profile = new LrmpProfile();

        profile.setEventHandler(this);

        profile.bandwidth = (int) ((maxDataRate * 8) / 1000);

        // @@@ Should throw InternalException instead

        try {
            lrmp.setProfile(profile);
        } catch (LrmpException e) {
            throw new RMException("couldn't set data rate");
        }
    }

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     */
    public int getMaxLength() {
        if (maxLength == 0) {

            // @@@ Should add fragmentation code to handle this.

            maxLength = new LrmpPacket().getMaxDataLength();
        }

        return (maxLength);
    }

    /**
     * This method should only be used by LRMP.
     */
    public void processData(LrmpPacket pack) {
        synchronized (inputQ) {
            inputQ.addElement(pack);
            inputQ.notify();
        }
    }

    /**
     * This method should only be used by LRMP.
     */
    public void processEvent(int event, Object obj) {
        switch (event) {

        case LrmpEventHandler.UNRECOVERABLE_SEQUENCE_ERROR: 

            // @@@ Should set a flag set we can throw an exception next time they read.

            System.out.println("reception failure!");

            break;

        default: 
            break;
        }
    }







}

