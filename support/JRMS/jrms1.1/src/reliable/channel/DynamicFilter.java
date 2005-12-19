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
 * DynamicFilter.java
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.SessionDoneException;

/**
 * An object that filters or otherwise transforms data on a packet-based
 * channel. In general, a dynamic filter stands before an
 * RMPacketSocket and transforms data as it passes through.
 * 
 * <P>Dynamic filters may be used for many purposes. For instance, they may
 * compress or decompress data, encrypt or decrypt it, or filter it based
 * on access rights or other criteria. They are installed at the receiver
 * by using channel's setDynamicFilter method.  Since sometimes a filter
 * may need be distributed to the receivers from a central location, such
 * as at the Channel Manager, dynamic filters should implement Serializable
 * so for example it can be easily stored in a file.
 * 
 * @see                         Channel
 * @see                         ChannelManager
 */
public interface DynamicFilter extends RMPacketSocket {

    /**
     * Gets the lower level RMPacketSocket. This is the socket to which
     * the DynamicFilter sends data after transformation and from which
     * it gets data before transformation. This socket may or may not
     * be a DynamicFilter.
     * 
     * @return the lower socket (null if none)
     */
    RMPacketSocket getLowerSocket();

    /**
     * Sets the lower level RMPacketSocket. This is the socket to which
     * the DynamicFilter sends data after transformation and from which
     * it gets data before transformation. This socket may or may not
     * be a DynamicFilter.
     * 
     * @param lower the lower socket (null if none)
     * @exception UnsupportedException if the lower socket cannot be set
     */
    void setLowerSocket(RMPacketSocket lower) throws UnsupportedException;
}

