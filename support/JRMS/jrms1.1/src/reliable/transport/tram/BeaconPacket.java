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
 * BeaconPkt.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class BeaconPacket extends TRAMPacket {

    /*
     * Flag field Bit Definitions.
     */
    public static final byte FLAGBIT_PRUNE = (byte) (1 << 0);
    public static final byte FLAGBIT_TXDONE = (byte) (1 << 1);
    public static final byte FLAGBIT_FILLER = (byte) (1 << 2);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Field Offset Definitions.
     */
    private static final byte HA_INTERVAL = 0;
    private static final byte SEQ_NUM = (HA_INTERVAL + 2);
    private static final byte FORGETBEFORESEQNUM = SEQ_NUM + 4;
    private static final byte SOURCE_ADDR = (FORGETBEFORESEQNUM + 4);
    private static final byte PKT_LENGTH = (SOURCE_ADDR + 4);
    /* 
     * currently the packet length assumes IPV4 address. When Java supports
     * IPV6, this will have to be revisted.
     */
    public static final int HEADERLENGTH = (int) PKT_LENGTH;

    /*
     * Private fields
     */
    private InetAddress srcAddress = null;
    private int seqNumber = 0;
    private int forgetBeforeSeqNum = 1; /* true seq numbers start from 1 */
    private short haInterval = 0;

    /**
     * Decode the various beacon fields from an incoming datagram packet.
     */
    public BeaconPacket(DatagramPacket dp) {
        super(dp);

        seqNumber = super.readInt(SEQ_NUM);
        haInterval = super.readShort(HA_INTERVAL);

	/* 
	 * When Java supports IPV6, we will have to alter the following 
	 * part.
	 */
	int i = super.readInt(SOURCE_ADDR);

        srcAddress = Util.intToInetAddress(i);
    }

    /**
     * Builds a BeaconPacket class with the default values.
     * 
     * @param TRAMControlBlock
     * @param byte - flag bit settings.
     */
    public BeaconPacket(TRAMControlBlock tramblk, int signatureSize, 
                        short haInterval, 
			byte flagData) {
        super((HEADERLENGTH + signatureSize), tramblk.getSessionId());

        setMessageType(MESGTYPE.MCAST_CNTL);
        setSubType(SUBMESGTYPE.BEACON);

        srcAddress = tramblk.getLocalHost();

        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);

        setFlags(flagData);

        this.haInterval = haInterval;
        super.writeShort(haInterval, HA_INTERVAL);
    }

    /**
     * Creates a datagram packet using the internal field values.
     * Typically this method is called to create a BeaconPacket that
     * is to be sent. The internal fields are to be initialized to
     * appropriate values before calling this method.
     * 
     * @return DatagramPacket - the created Datagram packet.
     */
    public DatagramPacket createDatagramPacket() {
        return super.createDatagramPacket();
    }

    /**
     * gets the Source Address from the internal field.
     * 
     * @return InetAddress - the source address.
     */
    public InetAddress getSrcAddress() {
        return srcAddress;
    }

    /**
     * sets the specified Source Address value to the internal field.
     * 
     * @param InetAddress - Source address to be assigned.
     */
    public void setSrcAddress(InetAddress address) {
        srcAddress = address;

        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);
    }

    /**
     * gets the beacon Packet sequence number.
     * 
     * @return int - the sequence number  of the beacon packet.
     */
    public int getSeqNumber() {
        return seqNumber;
    }

    /**
     * sets the sequence number.
     * 
     * @param int - the required sequence number value.
     */
    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;

        super.writeInt(this.seqNumber, SEQ_NUM);
    }

    /** 
     * get the Beacon Packet forgetBeforeSeqNum
     *
     * @return int - the forgetBeforeSeqNum of the packet.
     */
    public int getForgetBeforeSeqNum() {
	return forgetBeforeSeqNum;
    }

    /** 
     * sets the forgetBeforeSeqNUm
     *
     * @param int - the forgetBeforeSeqNum value
     */
    public void setForgetBeforeSeqNum(int fb) {
	this.forgetBeforeSeqNum = fb;

	super.writeInt(this.forgetBeforeSeqNum, FORGETBEFORESEQNUM);
    }

    /**
     * gets the beacon packet HAInterval value.
     * 
     * @return int - the haInterval value.
     */
    public short getHaInterval() {
        return haInterval;
    }

    /**
     * sets the haInterval value
     * 
     * @param haInterval - the required HA Interval value in ms.
     */
    public void setHaInterval(short haInterval) {
        this.haInterval = haInterval;

        super.writeShort(this.haInterval, HA_INTERVAL);
    }

    /**
     * This routine is provided to all other classes to extend the
     * BeaconPacket class. This method adds in the size of the header
     * at this layer to the total and passes the write command off
     * to the parent class.
     * 
     * @param value the byte array to write to the buffer.
     * @param offset the byte offset in the childs header to place the
     * byte array.
     */
    public void writeBuffer(byte buffer[], int length, int offset) {
        offset += HEADERLENGTH;

        super.writeBuffer(buffer, length, offset);
    }

    /**
     * @return the length of the data portion of the packet. TRAM headers
     * are not included in this length. Typically used to determine the
     * signature length.
     */
    public int getLength() {
        return (super.getLength() - HEADERLENGTH);
    }

}       // End of BeaconPacket Class.

