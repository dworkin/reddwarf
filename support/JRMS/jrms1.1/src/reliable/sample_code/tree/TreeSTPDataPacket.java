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
 * TreeSTPDataPacket.java
 * 
 * Module Description:
 * 
 * This module defines the STP data packet format. When a packet is
 * received, this class is created to retrieve the STP header information
 * and move any user data into a new DatagramPacket.
 */
package com.sun.multicast.reliable.applications.tree;

import java.net.DatagramPacket;

/**
 * The STPDataPacket defines the format of data packets in STP. It
 * extends the base class STPPacket which defines the common elements
 * of all STP packets.
 * 
 * A users buffer (byte array) is required to create an outbound data
 * packet. The users buffer is copied into a new buffer large enough to
 * hold the users data and the STP headers. The headers are filled in
 * and the users buffer is released.
 * 
 * A DatagramPacket is required to create an inbound data packet. The
 * buffer in the DatagramPacket is saved and the headers are parsed.
 * The header information is stored in local variables and the DatagramPacket
 * is released.
 */
public class TreeSTPDataPacket extends TreeSTPPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_CONGESTION = (byte) (1 << 0);
    public static final byte FLAGBIT_PRUNE = (byte) (1 << 1);
    public static final int CHECKSUM = 0;
    public static final int SEQUENCENUMBER = CHECKSUM + 2;
    public static final int HAINTERVAL = SEQUENCENUMBER + 4;
    public static final int PAYLOAD = HAINTERVAL + 4;
    public static final int STPDATAHEADERLENGTH = PAYLOAD;
    public static final int STPMAXPKT = 65507 - PAYLOAD 
                                        - TreeSTPPacket.STPHEADERLENGTH;
    private int sequenceNumber = 0;
    private long checksum = 0;
    private int haInterval = 0;
    private byte ttl = 0;

    /**
     * Create an outbound STPDataPacket. The user buffer is handed to the
     * STPPacket class where it is copied into a buffer large enough for
     * both the standard STPPacket header and the STPDataPacket specific
     * header. The STPPacket header information is stored in the buffer
     * before returning.
     * 
     * @param buff[] a byte array of user data to be sent on the network.
     */
    public TreeSTPDataPacket(byte buff[], int length) {
        super(buff, length, PAYLOAD);

        setMessageType(2);
        setSubType(1);
        setChecksum(0);
    }

    /**
     * Set the sequence number for this packet. The STPDataPacket class
     * does not set the sequence number for outbound packets. This is typically
     * done in the transmitter when the packet is actually sent.
     * 
     * @param i the sequence number for this packet. (integer greater than 0)
     */
    public void setSequenceNumber(int i) {
        sequenceNumber = i;

        super.writeInt(i, SEQUENCENUMBER);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param checksum
     *
     * @see
     */
    private void setChecksum(int checksum) {
        super.writeShort((short) checksum, CHECKSUM);
    }

    /**
     * Set the TTL value for this packet. This is the time-to-live
     * value to set when we send this packet. Use this option when
     * the TTL needs to be something other than the default.
     * 
     * @param ttl a byte containing the TTL value for this packet.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * Create a DatagramPacket from the existing data in this class. A CRC
     * is computed for the packet including the data. It is added to the
     * DataPacket header. Once calculated we let the STPPacket parent
     * actually create the DatagramPacket.
     * 
     * NOTE: In the future we may want to change the CRC32 algoritim to a
     * CRC16 and possibly just checksum our header and not the data.
     * 
     * @return a DatagramPacket with the current STPDataPacket contents.
     */
    public DatagramPacket createDatagramPacket() {
        setChecksum(0);
        setChecksum(computeChecksum());

        DatagramPacket dp = super.createDatagramPacket();

        return dp;
    }

}

