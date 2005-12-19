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
 * ChannelRMStreamSocket.java
 * 
 * Module Description:
 * 
 * This class defines the Stream interface for channel.
 * This stream is connected to a transport RMStreamSocket interface.
 * Most of these methods simply pass the call through to the transport.
 * Channel may catch the data and customize it before handing it off to
 * transport when necessary.
 */
package com.sun.multicast.reliable.channel;

import java.net.InetAddress;
import java.net.SocketException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMStatistics;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;

/**
 * An RMStreamSocket for Channel.
 * 
 * @see com.sun.multicast.reliable.transport.RMStreamSocket
 */
class ChannelRMStreamSocket implements RMStreamSocket {
    TransportProfile tp;
    RMStreamSocket so;

    /**
     * Create a ChannelRMStreamSocket with the supplied TransportProfile
     * object.
     * 
     * @param tp the TransportProfile of the transport in use.
     * @param sendReceive indicates whether this socket is for
     * sending, receiving, both, or repair_node. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER,
     * TransportProfile.SEND_RECEIVE, or TransportProfile.REPAIR_NODE.
     * @exception IOException if an I/O error occurs
     * @exception UnsupportedException if the underlying transport
     * does not support a stream interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is invalid
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public ChannelRMStreamSocket(TransportProfile tp, int sendReceive) 
            throws IOException, UnsupportedException, 
                   InvalidTransportProfileException, RMException {
        this.tp = (TransportProfile) tp.clone();
        this.so = tp.createRMStreamSocket(sendReceive);
    }

    /**
     * Returns an InputStream object for receiving data from this
     * multicast transport session.
     * 
     * @returns an InputStream object.
     * @exception UnsupportedException if the operation is not supported
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception IOException if this is the sender in send only mode,
     * in which case the sender may not receive its own data.
     */
    public InputStream getInputStream() 
            throws UnsupportedException, RMException, IOException {
        InputStream is = so.getInputStream();

        return new ChannelInputStream(is);
    }

    /**
     * Returns an OutputStream object for sending data over this
     * multicast transport session.
     * 
     * @return an OutputStream object.
     * @exception UnsupportedException if the operation is not supported
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public OutputStream getOutputStream() 
            throws UnsupportedException, RMException {
        OutputStream os = so.getOutputStream();

        return new ChannelOutputStream(os);
    }

    /**
     * Leaves the multicast transport session as quickly as possible.
     * Pending transmissions and outgoing repairs may be dropped.
     */
    public void abort() {
        so.abort();
    }

    /**
     * Leaves the multicast transport session gracefully.
     * Pending transmissions and outgoing repairs are handled properly.
     * This method may take some time to return.
     */
    public void close() {
        so.close();
    }

    /**
     * Returns the address of the network interface used for sending
     * data on this multicast transport session.
     * 
     * @return the address of the network interface for outgoing data.
     * @exception SocketException if a socket-related error occurs
     * @exception UnsupportedException if the operation is not supported
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public InetAddress getInterface() 
            throws SocketException, UnsupportedException, RMException {
        return so.getInterface();
    }

    /**
     * Sets the address of the network interface used for sending
     * data on this multicast transport session. This is only useful
     * on multihomed hosts.
     * 
     * @param ia the address of the network interface for outgoing data.
     * @exception SocketException if a socket-related error occurs
     * @exception UnsupportedException if the operation is not supported
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public void setInterface(InetAddress ia) 
            throws SocketException, UnsupportedException, RMException {
        so.setInterface(ia);
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
     * Return the latest snapshot of the maintained Transport
     * statistics block.
     * 
     * @return a clone of the statistics block maintained by this socket.
     * @exception UnsupportedException when transport does not support this.
     */
    public RMStatistics getRMStatistics() throws UnsupportedException {
        return so.getRMStatistics();
    }

    /**
     * This method returns the transport-specific RMStreamSocket on
     * which this ChannelRMStreamSocket is layered. This allows
     * transport-specific methods of the transport-specific RMStreamSocket
     * to be accessed.
     * 
     * @return the transport-specific RMStreamSocket
     */
    public RMStreamSocket getTransportSocket() {
        return so;
    }

}

