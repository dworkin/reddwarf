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
 * RMPacketSocket.java
 * 
 * Module Description:
 * 
 * This class defines the interface for a transport supporting
 * packet I/O. Transports implement this interface to allow applications
 * access to data on a multicast address via a DatagramPacket.
 */
package com.sun.multicast.reliable.transport;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.io.IOException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.IrrecoverableDataException;






/**
 * An RMPacketSocket represents a packet-oriented connection to a multicast
 * transport session. Some transports may not support packet-oriented
 * connections. Those that do must  at least one class that implements
 * the RMPacketSocket interface. This class may have additional transport
 * specific methods, but must support the minimal methods defined in this
 * interface.
 * 
 * <P>The most common way to use an RMPacketSocket is to receive a
 * TransportProfile describing a multicast transport session and call its
 * <code>createRMPacketSocket</code> method. This will return a transport
 * specific object that implements the RMPacketSocket interface. The methods
 * of this interface can be used to send and receive data or change or monitor
 * the multicast transport session.
 * 
 * <P>Most transports will want to create a class that implements the
 * RMPacketSocket interface. Simple applications may use the RMStreamSocket
 * interface, but more sophisticated ones will want the more advanced
 * capabilities that the RMPacketSocket interface offers (out of order
 * delivery and support for multiple senders, for instance).
 */
public interface RMPacketSocket {

    /**
     * Sends a DatagramPacket over the multicast transport session.
     * 
     * @param dp the DatagramPacket to be sent.
     * @exception IOException if an I/O error occurs
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void send(DatagramPacket dp) throws IOException, RMException;

    /**
     * Returns the next DatagramPacket available. This method may block
     * indefinitely.
     * 
     * @return the next DatagramPacket
     * @exception IOException if an I/O error occurs
     * @exception SessionDoneException if the session is done
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    DatagramPacket receive() 
            throws IOException, SessionDoneException, RMException, 
                   IrrecoverableDataException;

    /**
     * Leaves the multicast transport session as quickly as possible.
     * Pending transmissions and outgoing repairs may be dropped.
     */
    void abort();

    /**
     * Leaves the multicast transport session gracefully.
     * Pending transmissions and outgoing repairs are handled properly.
     * This method may take some time to return.
     */
    void close();

    /**
     * Returns the address of the network interface used for sending
     * data for this multicast transport session.
     * 
     * @return the address of the network interface for outgoing data.
     * @exception SocketException if a socket-related error occurs
     * @exception UnsupportedException if the transport does not
     * support getting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    InetAddress getInterface() 
            throws SocketException, UnsupportedException, RMException;

    /**
     * Sets the address of the network interface used for sending
     * data for this multicast transport session. This is only useful
     * on multihomed hosts.
     * 
     * @param ia the address of the network interface for outgoing data.
     * @exception SocketException if a socket-related error occurs
     * @exception UnsupportedException if the transport does not
     * support setting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setInterface(InetAddress ia) 
            throws SocketException, UnsupportedException, RMException;

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    int getMaxLength() throws RMException;

    /**
     * Retrieve the transport profile this socket is currently using.
     * A clone of the transport profile used to create the socket will be
     * returned. Applications wishing to view a active copy of the Transport
     * profile must call the <code> getTransportProfile </code>method.
     * 
     * @return a copy of the current TransportProfile in use.
     */
    TransportProfile getTransportProfile();

    /**
     * Retrieve the RMStatistics block of this socket.
     * The socket clones and returns the current snap shot of the
     * statistics maintained by the transport.
     * Applications wishing to view the current RM transport statistics
     * must call the getStatBlock method.
     * 
     * @return a copy of the current snapshot of the statistics maintained.
     * @exception UnsupportedException when transport does not support this.
     */
    RMStatistics getRMStatistics() throws UnsupportedException;







}

