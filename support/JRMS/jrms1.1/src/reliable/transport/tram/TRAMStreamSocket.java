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
 * TRAMStreamSocket.java
 * 
 * Module Description:
 * 
 * This class defines the TRAM Stream API for the TRAM Transport.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.MulticastSocket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.RMStatistics;

/**
 * The TRAMStreamSocket implements RMStreamSocket Interface. TRAMStreamSocket
 * allows applications to send and receive data, set the interface over
 * which data is to be sent, and close the socket after completion or
 * to abort the connection before the session ends.
 */
public class TRAMStreamSocket implements RMStreamSocket {
    private TRAMOutputStream outputStream = null;
    private TRAMInputStream inputStream = null;
    private TRAMControlBlock tramblk = null;
    private TRAMTransportProfile tp = null;
    private TRAMInputOutput pktio = null;

    /**
     * The TRAMStreamSocket Constructor.
     */
    public TRAMStreamSocket() {
        super();
    }

    /**
     * The connection is established here in the connect routine.
     * The multicast socket is created an the group is joined. A
     * call back to the transport profile disables future setting
     * of the parameters there.
     */
    void connect(TRAMTransportProfile profile, InetAddress interfaceAddress) 
	throws IOException {

        tp = (TRAMTransportProfile) profile.clone();

        tp.setOrdered(true);

        MulticastSocket ms = new MulticastSocket(tp.getPort());

        try {
            int receiverBufferSize = ms.getReceiveBufferSize();

            for (int size = 256*1024; size > receiverBufferSize; size -= 1024) {
                try {
                    ms.setReceiveBufferSize(size);
		    break;
                } catch (IllegalArgumentException e) {
                    /* try again with a smaller size */
                } catch (Exception e) {
                    break;  /* something else is wrong */
                }   
            }
        } catch (Exception e) {}

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

        try {
            tramblk = new TRAMControlBlock(ms, tp);
        } catch (TRAMControlBlockException e) {
            // e.printStackTrace();

            throw new IOException(e + "Unable create TRAM ControlBlock");
        }

        /*
         * Get the packet database.
         */
        pktio = tramblk.getPacketDb();
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
     * The getInputStream method returns an InputStream object for this
     * transport. The transport must implement the input stream and provide
     * the application with the read functions for obtaining incoming data.
     * 
     * @returns an InputStream object.
     */
    public InputStream getInputStream() throws IOException {
        if ((tp.getTmode() & 0xff) == TMODE.SEND_ONLY) {
            throw new IOException("Sender may not receive its own data");
        } 
        if (inputStream == null) {
            inputStream = new TRAMInputStream(tramblk, pktio);
        }

        return (InputStream) inputStream;
    }

    /**
     * The getOutputStream method returns an OutputStream object for sending
     * data over the multicast connection.
     * 
     * @returns the OutputStream object.
     */
    public OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new TRAMOutputStream(tramblk, pktio);
        }

        return (OutputStream) outputStream;
    }

    /**
     * Sets the interface on which the data will be transmitted on. This is
     * useful on systems with multiple network interfaces.
     * 
     * @param ia the InetAddress of the interface to transmit data on.
     */
    public void setInterface(InetAddress ia) throws SocketException {
        MulticastSocket ms = tramblk.getMulticastSocket();

        ms.setInterface(ia);
    }

    /**
     * This method returns a clone(copy) of the TransportProfile in use in
     * this socket.
     * 
     * @return a cloned TransportProfile
     */
    public TransportProfile getTransportProfile() {
        return (TransportProfile) tp.clone();
    }

    /**
     * This method returns a clone(copy) of the Statistics block in use in
     * this socket.
     * 
     * @return a cloned TRAM statistics block
     */
    public RMStatistics getRMStatistics() {
        return (RMStatistics) tramblk.getTRAMStats().clone();
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
        if ((tramblk != null) && (outputStream != null)) {
            try {
                outputStream.close();
            } catch (IOException e) {

            // Ignoring currently.

            }
        }
    }

}

