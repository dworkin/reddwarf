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
 * UMPacketSocket.java
 * 
 * Module Description:
 * 
 * This module implements the unreliable multicast transport packet
 * interface. Applications can use this class to send and receive
 * datagram packets to the multicast address.
 */
package com.sun.multicast.reliable.transport.um;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.io.IOException;
import com.sun.multicast.util.Util;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMStatistics;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;






/**
 * An RMPacketSocket for the Unreliable Multicast transport.
 * 
 * <P>The only transport-specific public methods that are added
 * by this class are getSoTimeout and setSoTimeout, which allow a
 * timeout to be specified for the receive method.
 * 
 * @see com.sun.multicast.reliable.transport.RMPacketSocket
 */
public class UMPacketSocket implements RMPacketSocket {
    private static final int HEADER = 4;
    private static final int PACKETNUMBER = 0;
    private int packetNumber = 0;
    private UMTransportProfile tp = null;
    private MulticastSocket ms = null;
    private UMSender sender = null;
    private boolean firstTime = true;

    UMPacketSocket() {}

    /**
     * The connect method initializes the multicast session. The
     * MulticastSocket is obtained with the port number specified in the
     * transport profile. The multicast group is joined and the ttl value
     * is set.
     * 
     * @param TransportProfile The transport profile for this transport.
     * 
     * @exception IOException if a problem is encountered creating the
     * multicast socket.
     */
    void connect(UMTransportProfile tp) throws IOException {
        ms = new MulticastSocket(tp.getPort());

        ms.joinGroup(tp.getAddress());

        try {

            // 
            // Try the jdk1.2 method
            // 

            ms.setTimeToLive(tp.getTTL());
        } catch (NoSuchMethodError e) {

            // 
            // jdk1.2 method doesn't exist so try pre-jdk1.2 name
            // 

            if (firstTime) {
                firstTime = false;

                System.out.println("jdk1.2 method setTimeToLive " +
		    "doesn't exist.  Trying setTTL()");
            }

            ms.setTTL(tp.getTTL());
        }

        this.tp = (UMTransportProfile) tp.clone();
        this.sender = new UMSender(ms, tp.getDataRate());
    }

    /**
     * The getInterface method returns the InetAddress of the local port
     * that data is transmitted on if other than the default.
     * 
     * @returns the address of the local port that data is transmitted on.
     */
    public InetAddress getInterface() throws SocketException {
        return ms.getInterface();
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
     * @return a clone of the statistics block maintained by this
     * socket.
     * @exception UnsupportedException this method is not supported.
     */
    public RMStatistics getRMStatistics() throws UnsupportedException {
        throw new UnsupportedException();
    }

    /**
     * Set the interface which data will be transmitted on. This is useful on
     * systems with multiple network interfaces.
     * 
     * @param ia the InetAddress of the interface to transmit data on.
     */
    public void setInterface(InetAddress ia) throws SocketException {
        ms.setInterface(ia);
    }

    /**
     * The send method transmits a DatagramPacket over the multicast
     * connection.
     * 
     * @param dp the DatagramPacket to be sent.
     * @exception IOException is raised if an error occurs sending the data.
     */
    public void send(DatagramPacket dp) throws IOException {
        sender.insqueue(copyDP(dp));
    }

    /**
     * The send method transmits a DatagramPacket over the multicast
     * connection. This method allows for setting the TTL on this transmission.
     * If the desired TTL is different from the default set in the Transport
     * Profile, this send method can be used. This does not alter the default
     * ttl.
     * 
     * @param dp the DatagramPacket to be sent.
     * @param ttl optional time to live the the multicast packet.
     * @exception IOException is raised if an error occurs sending the data.
     */
    public void send(DatagramPacket dp, byte ttl) throws IOException {
        sender.insqueue(copyDP(dp), ttl);
    }

    /**
     * The receive method returns the next RMDatagramPacket.
     * 
     * @returns the next packet
     * @exception IOException is thrown if an error occurs retrieving the
     * data.
     */
    public DatagramPacket receive() throws IOException {
        byte data[] = new byte[65535];
        DatagramPacket dp = new DatagramPacket(data, data.length);

        ms.receive(dp);

        byte udata[] = new byte[dp.getLength() - HEADER];

        System.arraycopy(dp.getData(), HEADER, udata, 0, udata.length);

        DatagramPacket udp = new DatagramPacket(udata, udata.length, 
                                                dp.getAddress(), 
                                                dp.getPort());

        return udp;
    }

    /**
     * Abort the current connection. All packets in the send queue are lost.
     */
    public void abort() {
        ms.close();
    }

    /**
     * The close method shuts down the socket after flushing the transmit
     * queue. All data previously transmitted will be handed to the network
     * prior to tearing down the connection.
     */
    public void close() {
        try {
            while (!sender.isQueueEmpty()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {}

        ms.close();
    }

    /**
     * Return the SO_TIMEOUT setting in milliseconds. A value of zero
     * indicates an infinite timeout.
     * 
     * @return the SO_TIMEOUT value.
     * @exception SocketException if the multicast socket is invalid.
     */
    public int getSoTimeout() throws SocketException {
        return ms.getSoTimeout();
    }

    /**
     * Set the SO_TIMEOUT value. A non-zero value for this allows a call to
     * receive to wait timeout milliseconds before returning with an
     * InterruptedIOException. The socket is still valid.
     * 
     * @param timeout the time in milliseconds to wait for data to be returned.
     * 
     * @exception SocketException if the multicast socket is invalid.
     */
    public void setSoTimeout(int timeout) throws SocketException {
        ms.setSoTimeout(timeout);
    }

    /**
     * @return the current value for the data rate in bytes/second
     */
    public long getDataRate() {
        return tp.getDataRate();
    }

    /**
     * Change the current data rate value. This changes the active value and
     * the value in the current transport profile.
     * 
     * @param dataRate the rate in bytes/second to transmit data.
     */
    public void setDataRate(long dataRate) {
        tp.setDataRate(dataRate);
        sender.setDataRate(dataRate);
    }

    /**
     * Gets the maximum amount of data that can be sent in a DatagramPacket
     * over this socket.
     * 
     * @return the maximum allowed value for DatagramPacket.getLength()
     */
    public int getMaxLength() {
        return (65507);
    }

    /**
     * This method copies the user DatagramPacket to a JRMS Datagram
     * Packet. This allows the application to reuse their buffers and
     * packets while it sits on the output queue.
     */
    private DatagramPacket copyDP(DatagramPacket udp) {
        byte b[] = new byte[HEADER + udp.getLength()];

        System.arraycopy(udp.getData(), 0, b, HEADER, b.length - HEADER);
        Util.writeInt(++packetNumber, b, PACKETNUMBER);

        DatagramPacket dp = new DatagramPacket(b, b.length, tp.getAddress(), 
                                               tp.getPort());

        return dp;
    }







}

