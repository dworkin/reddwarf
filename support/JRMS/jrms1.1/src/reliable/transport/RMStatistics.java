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
 * RMStatistics.java
 * 
 * Module Description:
 * 
 * This class defines the interface for a generic 
 * transport statistics block. Transports implement this interface
 * to allow applications query various statistics maintained by the
 * transport.
 */
package com.sun.multicast.reliable.transport;

import java.net.InetAddress;
import com.sun.multicast.util.UnsupportedException;

/**
 * An RMStatistics represents a basic transport layer statistics block.
 * Support of this Statistics block is optional and as a result some
 * transports may not support interface. Those that do must
 * define at least one class that implements the RMStatistics interface.
 * This class may have additional transport specific methods, but must
 * support the minimal methods defined in this interface.
 * 
 * <P>The RMStatistics of a transport is obtained from the RMStreamSocket or
 * RMPacketSocket with the aid of getRMStatistics method.
 * <P>Most transports will want to create a class that implements the
 * RMStatistics interface.
 */
public interface RMStatistics {

    /**
     * Returns the count of senders participating in the multicast session.
     * 
     * @return int count of known senders of the multicast session.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public int getSenderCount() throws UnsupportedException;

    /**
     * Returns the list of senders of the multicast session.
     * 
     * @return InetAddress[] list of known senders of the multicast session.
     * @exception UnsupportedException RMStatistics block if is not supported.
     */
    public InetAddress[] getSenderList() throws UnsupportedException;

    /**
     * Returns the count of receivers participating in the multicast session.
     * 
     * @return int count of known receivers of the multicast session.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public int getReceiverCount() throws UnsupportedException;

    /**
     * Returns the list of receivers of the multicast session.
     * 
     * @return InetAddress[] current list of known receivers of the
     * multicast session.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public InetAddress[] getReceiverList() throws UnsupportedException;

    /**
     * Returns the bytecount of data contributed to the multicast session.
     * 
     * @return int bytecount of data contributed to the session by this
     * node.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public long getTotalDataSent() throws UnsupportedException;

    /**
     * Returns the bytecount of data retransmitted by this node to perform
     * repairs.
     * 
     * @return int bytecount of data retransmitted to the session by this
     * node.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public long getTotalDataReSent() throws UnsupportedException;

    /**
     * Returns the bytecount of data received by this node.
     * 
     * 
     * @return int bytecount of data received by this node sofar.
     * @exception UnsupportedException if RMStatistics block is not supported.
     */
    public long getTotalDataReceive() throws UnsupportedException;
}

