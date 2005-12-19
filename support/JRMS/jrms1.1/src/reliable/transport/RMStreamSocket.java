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
 * RMStreamSocket.java
 * 
 * Module Description:
 * 
 * This class defines the Stream interface. Transports
 * wishing to implement a stream interface must implement the
 * RMStreamSocket class.
 */
package com.sun.multicast.reliable.transport;

import java.net.InetAddress;
import java.net.SocketException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;

/**
 * An RMStreamSocket represents a stream-oriented connection to a multicast
 * transport session. Some transports may not support stream-oriented
 * connections. Those that do must define at least one class that implements
 * the RMStreamSocket interface. This class may have additional transport
 * specific methods, but must support the minimal methods defined in this
 * interface.
 * 
 * <P>The most common way to use an RMStreamSocket is to receive a
 * TransportProfile describing a multicast transport session and call its
 * <code>createRMStreamSocket</code> method. This will return a transport
 * specific object that implements the RMStreamSocket interface. The methods
 * of this interface can be used to send and receive data or change or
 * monitor the multicast transport session.
 * 
 * <P>To send data, use the <code>getOutputStream</code> method to get an
 * OutputStream object. Then use the methods of that class (or other classes
 * like PrintWriter) to write data to the multicast transport session.
 * To receive data, use the <code>getInputStream </code> method to get
 * an InputStream object. Then use the methods of that class (or other
 * classes like StringReader) to read data from the multicast transport
 * session.
 * 
 * <P>Most transports will want to create a class that implements the
 * RMStreamSocket interface. This interface provides a simple way
 * to access multicast transports. However, it does require that
 * the transport handle ordering and repair of data in order to
 * deliver data to receivers in the same order that it was sent.
 * 
 * <P>More sophisticated applications may want to use the RMPacketSocket
 * interface to gain access to the more advanced capabilities that it
 * offers (out of order delivery and support for multiple senders, for 
 * instance).
 */
public interface RMStreamSocket {

    /**
     * Returns an InputStream object for receiving data from this
     * multicast transport session.
     * 
     * @returns an InputStream object.
     * @exception UnsupportedException if the transport does not
     * support getting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    InputStream getInputStream() 
            throws UnsupportedException, RMException, IOException;

    /**
     * Returns an OutputStream object for sending data over this
     * multicast transport session.
     * 
     * @return an OutputStream object.
     * @exception UnsupportedException if the transport does not
     * support getting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    OutputStream getOutputStream() throws UnsupportedException, RMException;

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
     * data over the multicast transport session.
     * 
     * @return the address of the network interface for outgoing data.
     * @exception SocketException if an error occurs
     * @exception UnsupportedException if the transport does not
     * support getting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    InetAddress getInterface() 
            throws SocketException, UnsupportedException, RMException;

    /**
     * Sets the address of the network interface used for sending
     * data over the multicast transport session. This is only useful
     * on multihomed hosts.
     * 
     * @param ia the address of the network interface for outgoing data.
     * @exception SocketException if an error occurs
     * @exception UnsupportedException if the transport does not
     * support setting the interface
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setInterface(InetAddress ia) 
            throws SocketException, UnsupportedException, RMException;

    /**
     * Retrieve the transport profile this socket is currently using.
     * A clone of the transport profile used to create the socket is
     * returned. Applications wishing to view the active Transport
     * profile must call the <code>getTransportProfile</code> method.
     * 
     * @return a copy of the current TransportProfile in use.
     */
    TransportProfile getTransportProfile();

    /**
     * Retrieve the RMStatistics Block of this socket.
     * The socket clones and returns the current snap shot of the
     * statistics maintained by the transport.
     * Applications wishing to view the current RM transport statistics
     * must call the getStatBlock method.
     * 
     * @return a copy of the current snapshot of the maintained statistics.
     * @exception UnsupportedException when transport does not support this.
     */
    RMStatistics getRMStatistics() throws UnsupportedException;
}

