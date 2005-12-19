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
 * BasicDynamicFilter.java
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMStatistics;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.SessionDoneException;
import com.sun.multicast.reliable.transport.TransportProfile;






/**
 * A basic dynamic filter that simply passes through calls to the lower
 * socket. The easiest way to build a dynamic filter is to extend this
 * class and override the methods you need to customize.
 */
class BasicDynamicFilter implements DynamicFilter, Serializable {
    transient protected RMPacketSocket lowerSocket = null;

    /**
     * Create a BasicDynamicFilter. Very little is done at this point,
     * since the filter will probably be created when the channel is created,
     * then cloned and passed around to all receivers and senders. There, the
     * dynamic filter will be connected to a lower socket and used.
     */
    public BasicDynamicFilter() {}

    /**
     * The getInterface method returns the InetAddress of the local port
     * that data is transmitted on if other than the default.
     * 
     * @returns the address of the local port that data is transmitted on.
     * 
     * @exception SocketException if an error occurs obtaining the interface.
     * @exception UnsupportedException if the transport does not
     * support getting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public InetAddress getInterface() 
            throws SocketException, UnsupportedException, RMException {
        return (lowerSocket.getInterface());
    }

    /**
     * Set the interface which data will be transmitted on. This is useful on
     * systems with multiple network interfaces.
     * 
     * @param ia the InetAddress of the interface to transmit data on.
     * @exception SocketException if an error occurs setting the interface.
     * @exception UnsupportedException if the transport does not
     * support setting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public void setInterface(InetAddress ia) 
            throws SocketException, UnsupportedException, RMException {
        lowerSocket.setInterface(ia);
    }

    /**
     * Gets the lower level RMPacketSocket. This is the socket to which
     * the DynamicFilter sends data after transformation and from which
     * it gets data before transformation. This socket may or may not
     * be a DynamicFilter.
     * 
     * @return the lower socket (null if none)
     */
    public RMPacketSocket getLowerSocket() {
        return (lowerSocket);
    }

    /**
     * Sets the lower level RMPacketSocket. This is the socket to which
     * the DynamicFilter sends data after transformation and from which
     * it gets data before transformation. This socket may or may not
     * be a DynamicFilter.
     * 
     * @param lower the lower socket (null if none)
     */
    public void setLowerSocket(RMPacketSocket lower) {
        lowerSocket = lower;
    }

    /**
     * The send method transmits a DatagramPacket over the multicast
     * connection.
     * 
     * @param dp the DatagramPacket to be sent.
     * @exception IOException is raised if an error occurs sending the data.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public void send(DatagramPacket dp) throws IOException, RMException {
        lowerSocket.send(dp);
    }

    /**
     * The receive method returns the next DatagramPacket.
     * 
     * @returns the next packet
     * @exception IOException is thrown if an I/O error occurs
     * @exception SessionDoneException if the session is done
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public DatagramPacket receive() 
            throws IOException, SessionDoneException, RMException {
        return (lowerSocket.receive());
    }

    /**
     * Abort the current connection. All packets in the send queue are lost.
     */
    public void abort() {
        lowerSocket.abort();
    }

    /**
     * The close method shuts down the socket after flushing the transmit
     * queue. All data previously transmitted will be handed to the network
     * prior to tearing down the connection.
     */
    public void close() {
        lowerSocket.close();
    }

    /**
     * This method returns a clone of the TransportProfile in use in
     * this socket.
     * 
     * @return a cloned TransportProfile
     */
    public TransportProfile getTransportProfile() {
        return (lowerSocket.getTransportProfile());
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
        return lowerSocket.getRMStatistics();
    }

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public int getMaxLength() throws RMException {
        return (lowerSocket.getMaxLength());
    }






}

