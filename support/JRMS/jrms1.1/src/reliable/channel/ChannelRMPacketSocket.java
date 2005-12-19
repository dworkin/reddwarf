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
 * ChannelRMPacketSocket.java
 * 
 * Module Description:
 * 
 * This module implements the RMPacketSocket interface for the
 * Channel layer. Channel can insert other implementations of the
 * RMPacketSocket interface for customization of the data according
 * channel parameters.
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.Vector;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMStatistics;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.SessionDoneException;
import com.sun.multicast.reliable.transport.TransportProfile;






/**
 * An RMPacketSocket for Channel.
 * 
 * @see com.sun.multicast.reliable.transport.RMPacketSocket
 */
class ChannelRMPacketSocket implements RMPacketSocket {
    TransportProfile tp;
    RMPacketSocket bottomSocket;
    Vector fList;
    RMPacketSocket topSocket;

    /**
     * Create a ChannelRMPacketSocket. The socket is obtained from the
     * transport profile. Both the socket and the transport profile
     * are saved here for later use.
     * 
     * @param tp the TransportProfile that we're connecting to
     * @param filters the dynamic filters that should be applied (null if none)
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER,
     * TransportProfile.SEND_RECEIVE, or TransportProfile.REPAIR_NODE.
     * 
     * @exception IOException if an I/O error occurs
     * @exception UnsupportedException if the underlying transport
     * does not support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is invalid
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    ChannelRMPacketSocket(TransportProfile tp, Vector filters, 
                                 int sendReceive) throws IOException, 
                                 UnsupportedException, 
                                 InvalidTransportProfileException, 
                                 RMException {
        this.tp = tp;
        bottomSocket = tp.createRMPacketSocket(sendReceive);

        if (filters == null) {
            topSocket = bottomSocket;
        } else {
            fList = (Vector) Util.deepClone(filters);

            RMPacketSocket lowerSocket = bottomSocket;

            for (int i = fList.size() - 1; i >= 0; i--) {
                DynamicFilter filter = (DynamicFilter) fList.elementAt(i);

                filter.setLowerSocket(lowerSocket);

                lowerSocket = filter;
            }

            topSocket = lowerSocket;
        }
    }

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
        return (topSocket.getInterface());
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
        topSocket.setInterface(ia);
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
        topSocket.send(dp);
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
        return (topSocket.receive());
    }

    /**
     * This is used to leave the multicast transport session as quickly as
     * possible. If called by sender, all packets in the send queue are lost.
     */
    public void abort() {
        topSocket.abort();
    }

    /**
     * This is used to leave the multicast session gracefully.
     * If called by the sender, the socket is closed after flushing the transmit
     * queue. All data previously transmitted will be handed to the network
     * prior to tearing down the connection.
     */
    public void close() {
        topSocket.close();
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
     * Returns the latest snapshot of the maintained Transport
     * statistics block.
     * 
     * @return a clone of the statistics block maintained by this socket.
     * @exception UnsupportedException when transport does not support this.
     */
    public RMStatistics getRMStatistics() throws UnsupportedException {
        return topSocket.getRMStatistics();
    }

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public int getMaxLength() throws RMException {
        return (topSocket.getMaxLength());
    }

    /**
     * This method returns the transport-specific RMPacketSocket on
     * which this ChannelRMPacketSocket is layered. This allows
     * transport-specific methods of the transport-specific RMPacketSocket
     * to be accessed.
     * 
     * @return the transport-specific RMPacketSocket
     */
    public RMPacketSocket getTransportSocket() {
        return (bottomSocket);
    }







}

