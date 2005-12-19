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
 * TRAMInputOutput.java
 *
 * Module Description:
 * 
 * This module defines the TRAM packet interface for any object
 * that receives or provides TRAMPackets. Most methods provide
 * access to TRAMDataPackets given a specified sequence number.
 * For access to any TRAMPacket, the getPacket method will return
 * the next TRAMPacket. The putPacket method will place an TRAMPacket
 * in the object.t
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.NoSuchElementException;
import java.io.IOException;

/**
 * The TRAMInputOutput provides a common input/Output interface
 * for various modules within TRAM to interact. All modules
 * supporting this interface MUST implement the methods defined
 * by this interface.
 */
interface TRAMInputOutput {

    /**
     * Give the specified packet to the object.
     * 
     * @param pkt the TRAMPacket
     * 
     * @exception IOException if the TRAMPacket cannot be accepted
     */
    public void putPacket(TRAMPacket pkt) throws IOException;

    /**
     * Give the specified packet to the object and also specify packet
     * prioity. putPacket with priority set to false is equivalent to
     * putPacket(pkt).
     * 
     * @param pkt the TRAMPacket
     * @param boolean packet priority.
     * 
     * @exception IOException if the TRAMPacket cannot be accepted
     */
    public void putPacket(TRAMPacket pkt, boolean priority) throws IOException;

    /**
     * Get the first TRAMPacket from the object. The packet is
     * removed from the object. If the database is empty, the
     * method blocks until a packet is received.
     * 
     * @return The TRAMPacket that is returned.
     * 
     */
    public TRAMPacket getPacket();

    /**
     * Get the TRAMDataPacket from the object with the sequence number
     * specified. The packet is removed from the object.
     * 
     * @return The TRAMDataPacket that is returned.
     * 
     * @exception NoSuchElementException is thrown if the packet doesn't
     * exist.
     */
    public TRAMDataPacket getPacket(long sequenceNumber) 
            throws NoSuchElementException;

    /**
     * Get the first TRAMPacket from the object. The packet is
     * removed from the object. If the database is empty, the
     * method throws NoSuchElementException.
     * 
     * @Exception NoSuchElementException when the input queue is
     * empty.
     * 
     * @return The TRAMPacket that is returned.
     * 
     */
    public TRAMPacket getPacketWithNoBlocking() throws NoSuchElementException;

    /**
     * Remove the packet with the specified sequence number.
     * 
     * @param sequenceNumber the sequence number of the packet to be
     * removed.
     * @exception NoSuchElementException if the packet does not exist.
     */
    public void removePacket(long sequenceNumber) 
            throws NoSuchElementException;
}

