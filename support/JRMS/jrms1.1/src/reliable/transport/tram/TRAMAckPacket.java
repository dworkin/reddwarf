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
 * TRAMAckpacket.java
 * 
 * Module Description:
 * 
 * This module defines the TRAM Ack packet format. When a packet is
 * received, this class is created to retrieve the TRAM header information
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.sun.multicast.util.Util;

/**
 * This class is the definition of the TRAMAckPacket. It is
 * derived from the TRAMPacket class.
 */
class TRAMAckPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_HELLO_NOT_RECVD = (byte) (1 << 0);
    public static final byte FLAGBIT_TERMINATE_MEMBERSHIP = (byte) (1 << 1);
    public static final byte FLAGBIT_ACK = (byte) (1 << 2);
    public static final byte FLAGBIT_CONGESTION = (byte) (1 << 3);
    public static final byte FLAGBIT_SUBTREE_FLOW_CONTROL_INFO = 
							(byte) (1 << 4);
    public static final byte FLAGBIT_DATA_RATE = (byte) (1 << 5);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    public static final int BITMASKLENGTH = 0;
    public static final int ACTUALTTL = BITMASKLENGTH + 2;
    public static final int RESERVED = ACTUALTTL + 1;
    public static final int STARTNUMBER = RESERVED  + 3;
    public static final int DIRECTMEMBERS = STARTNUMBER + 4;
    public static final int INDIRECTMEMBERS = DIRECTMEMBERS + 2;
    public static final int DIRECTHEADSADV = INDIRECTMEMBERS + 2;
    public static final int INDIRECTHEADSADV = DIRECTHEADSADV + 2;
    public static final int HIGHESTSEQUENCEALLOWED = INDIRECTHEADSADV + 2;
    public static final int FLOWCONTROLINFO = HIGHESTSEQUENCEALLOWED + 4;
    public static final int DATARATE = FLOWCONTROLINFO + 4;
    public static final int SOURCEADDRESS = DATARATE + 4;

    // End of static header. The remainder is variable length

    public static final int ACKHEADER =  SOURCEADDRESS + 4; // 4- is a valid
							    // V4 address size.
							    // Needs to be
							    // changed when
                                                            // java supports 
                                                            // V6.
							    //

    /* Base offset for the bitMask. */

    public static final int BITMASK = ACKHEADER;

    /* Define private fields for holding ACK information */

    private InetAddress sourceAddress;
    private InetAddress dataSourceAddress;
    private int port;
    private int actualTTL;
    private long basePacketNumber;
    private int bitMaskLength;
    private int directMemberCount = 0;
    private int indirectMemberCount = 0;
    private int directHeadsAdvertising = 0;
    private int indirectHeadsAdvertising = 0;
    private byte bitmask[];
    private int highestSequenceAllowed;
    private int flowControlInfo;
    private int dataRate;

    /**
     * Create an TRAM Ack packet from an inbound Ack message. The
     * Ack packet is built from the information in the DatagramPacket.
     * All fields are filled in and the DatagramPacket is released.
     * 
     * @param dp the inbound DatagramPacket
     * @exception UnknownHostException is thrown if one of the
     * addresses in the packet can't be converted to an InetAddress
     * object.
     */
    public TRAMAckPacket(DatagramPacket dp) throws UnknownHostException {

        /*
         * The parent constructor is called to retrieve the data buffer
         * and load the TRAM header fields.
         */
        super(dp);

        /*
         * Get the data buffer and pull all the ACK message specific fields
         * out of it.
         */
        sourceAddress = dp.getAddress();
        port = dp.getPort();
        actualTTL = ((int) (super.readByte(ACTUALTTL))) & 0xff;

        int address = super.readInt(SOURCEADDRESS);

        dataSourceAddress = Util.intToInetAddress(address);
        basePacketNumber = super.readInt(STARTNUMBER);
        bitMaskLength = ((int) (super.readShort(BITMASKLENGTH))) & 0xffff;
        directMemberCount = ((int) (super.readShort(DIRECTMEMBERS))) & 0xffff;
        indirectMemberCount = ((int) (super.readShort(INDIRECTMEMBERS))) 
                              & 0xffff;
        directHeadsAdvertising = ((int) (super.readShort(DIRECTHEADSADV))) 
                                 & 0xffff;
        indirectHeadsAdvertising = ((int) (super.readShort(INDIRECTHEADSADV))) 
                                   & 0xffff;
	highestSequenceAllowed = 
	    (int)(super.readInt(HIGHESTSEQUENCEALLOWED));
	flowControlInfo = (int)(super.readInt(FLOWCONTROLINFO));
	dataRate = (int)(super.readInt(DATARATE));
        bitmask = super.readBuffer(BITMASK, (bitMaskLength + 7) / 8);
    }

    /**
     * Construct an Ack packet for transmission. The ack packet is built from
     * information obtained locally and the bitmask representing packets
     * received or that are missing. The bitmask, number of valid bits, and
     * the starting packet number are required.
     * 
     * @param bitMask a byte array containing the bitmask of packets received
     * @param basePacketNumber the packet number that the first bit in the
     * bitMask represents.
     * @param bitMaskLength the last packet the bitMask represents.
     * 
     */
    public TRAMAckPacket(TRAMControlBlock tramblk, byte bitMask[], 
                        long basePacketNumber, int bitMaskLength) {

        super(bitMask, bitMask.length, ACKHEADER, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.ACK);
        setBasePacketNumber(basePacketNumber);
        setBitMaskLength(bitMaskLength);
        setBitMask(bitMask);
        setDataSourceAddress(
                      tramblk.getTransportProfile().getDataSourceAddress());
    }

    public TRAMAckPacket(TRAMControlBlock tramblk, long basePacketNumber) {
        super(ACKHEADER, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.ACK);
        setBasePacketNumber(basePacketNumber);
        setBitMaskLength(0);
        setBitMask(null);
        setDataSourceAddress(
                      tramblk.getTransportProfile().getDataSourceAddress());
    }

    /**
     * Create a DatagramPacket from the existing data in this class. A CRC
     * is computed for the packet including the data. It is added to the
     * DataPacket header. Once calculated we let the TRAMPacket parent
     * actually create the DatagramPacket.
     * 
     * NOTE: In the future we may want to change the CRC32 algoritim to a
     * CRC16 and possibly just checksum our header and not the data.
     * 
     * @return a DatagramPacket with the current TRAMDataPacket contents.
     */
    public DatagramPacket createDatagramPacket() {
        super.writeInt(Util.InetAddressToInt(dataSourceAddress), 
                       SOURCEADDRESS);

        DatagramPacket dp = super.createDatagramPacket();

        return dp;
    }

    /**
     * @return the base packet number that the first bit in the bitmask
     * represents.
     */
    public long getBasePacketNumber() {
        return basePacketNumber;
    }

    /**
     * @return the length in bytes of the bitmask.
     */
    public int getBitMaskLength() {
        return bitMaskLength;
    }

    /**
     * @return the byte array of the bitmask.
     */
    public byte[] getBitMask() {
        return bitmask;
    }

    /**
     * @return the direct member count for this member. The member must be
     * a head for this value to be non-zero.
     */
    public int getDirectMemberCount() {
        return directMemberCount;
    }

    /**
     * @return the indirect member count. The member must be a head for this
     * to be a non-zero value.
     */
    public int getIndirectMemberCount() {
        return indirectMemberCount;
    }

    /**
     * @param count the current number of members reporting to this head.
     */
    public void setDirectMemberCount(int count) {
        directMemberCount = count;

        super.writeShort((short) count, DIRECTMEMBERS);
    }

    /**
     * @param count the current number of members reporting to this head.
     */
    public void setIndirectMemberCount(int count) {
        indirectMemberCount = count;

        super.writeShort((short) count, INDIRECTMEMBERS);
    }

    /**
     * @return the number of heads reporting to this head that are currently
     * advertising.
     */
    public int getDirectHeadsAdvertising() {
        return directHeadsAdvertising;
    }

    /**
     * @return the number of heads reporting indirectly to this head (at outer
     * tree level than this head) that are currently advertising.
     */
    public int getIndirectHeadsAdvertising() {
        return indirectHeadsAdvertising;
    }

    /**
     * @param count the number of heads indirectly reporting to this head that
     * are currently advertising.
     */
    public void setDirectHeadsAdvertising(int count) {
        directHeadsAdvertising = count;

        super.writeShort((short) count, DIRECTHEADSADV);
    }

    /**
     * @param count the number of heads indirectly reporting to this head that
     * are currently advertising.
     */
    public void setIndirectHeadsAdvertising(int count) {
        indirectHeadsAdvertising = count;

        super.writeShort((short) count, INDIRECTHEADSADV);
    }

    /**
     * @return the port that htis packet came in on.
     */
    public int getPort() {
        return port;
    }

    /**
     * @return the source address for the multicast packets being
     * acked. This is the IP address of the Member.
     */
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    /**
     * @return the Senders address
     */
    public InetAddress getDataSourceAddress() {
        return dataSourceAddress;
    }

    /**
     * @param
     */
    public void setDataSourceAddress(InetAddress value) {
        dataSourceAddress = value;
    }

    /**
     * Set the base packet nmuber for this bitmask.
     * @param basePacketNumber the packet number that the first bit in
     * the bitmask represents.
     */
    private void setBasePacketNumber(long basePacketNumber) {
        this.basePacketNumber = basePacketNumber;

        super.writeInt((int) basePacketNumber, STARTNUMBER);
    }

    /**
     * Set the length of the bitmask.
     * 
     * @param bitMaskLength the length of the bitmask in bytes.
     */
    private void setBitMaskLength(int bitMaskLength) {
        this.bitMaskLength = bitMaskLength;

        super.writeShort((short) bitMaskLength, BITMASKLENGTH);
    }

    /**
     * Set the bitmask array.
     * 
     * @param bitmask the byte array for the bitmask.
     */
    public void setBitMask(byte bitMask[]) {
        bitmask = bitMask;
    }

    /**
     * Get the actualTTL value
     * 
     * @return actual TTL value.
     */
    public int getTTL() {
        return actualTTL;
    }

    /**
     * Set the actual TTL
     * 
     * @param Computed TTL from here to the head.
     */
    public void setActualTTL(int value) {
        actualTTL = value;
    }

    /**
     * Set the highest allowable sequence number
     *
     * @param highestSequenceNumberAllowed sequence number.
     */
    public void setHighestSequenceAllowed(int highestSequenceAllowed) {
	this.highestSequenceAllowed = highestSequenceAllowed;

	super.writeInt((int)highestSequenceAllowed, 
		       HIGHESTSEQUENCEALLOWED);
    }

    /**
     * Get the highestSequenceAllowed value in the packet.
     */
    public int getHighestSequenceAllowed() {
	return highestSequenceAllowed;
    }

    /**
     * Set the flow control information in the packet
     *
     * @param 
     */
    public void setFlowControlInfo(int flowControlInfo) {
	this.flowControlInfo = flowControlInfo;
	
        super.writeInt(flowControlInfo, FLOWCONTROLINFO);
    }
    
    /**
     * Get the flow control information from the packet
     */
    public int getFlowControlInfo() {
	return flowControlInfo;
    }
    
    /**
     * Set the data rate information in the packet
     *
     * @param 
     */
    public void setDataRate(int dataRate) {
	this.dataRate = dataRate;
	
        super.writeInt(dataRate, DATARATE);
    }
    
    /**
     * Get the data rate information from the packet
     */
    public int getDataRate() {
	return dataRate;
    }

}
