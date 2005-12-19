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
 * TRAMDataPacket.java
 * 
 * Module Description:
 * 
 * This module defines the TRAM data packet format. When a packet is
 * received, this class is created to retrieve the TRAM header information
 * and move any user data into a new DatagramPacket.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.InetAddress;
import java.net.DatagramPacket;
import com.sun.multicast.util.Util;

/**
 * The TRAMDataPacket defines the format of data packets in TRAM. It
 * extends the base class TRAMPacket which defines the common elements
 * of all TRAM packets.
 * 
 * A users buffer (byte array) is required to create an outbound data
 * packet. The users buffer is copied into a new buffer large enough to
 * hold the users data and the TRAM headers. The headers are filled in
 * and the users buffer is released.
 * 
 * A DatagramPacket is required to create an inbound data packet. The
 * buffer in the DatagramPacket is saved and the headers are parsed.
 * The header information is stored in local variables and the DatagramPacket
 * is released.
 */
class TRAMDataPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_PRUNE = (byte) (1 << 0);
    public static final byte FLAGBIT_TXDONE = (byte) (1 << 1);
    public static final byte FLAGBIT_RESET_FLOW_CONTROL_INFO = (byte) (1 << 2);
    /*
     * These bits are only sent up from transport to the application. They are
     * not sent out in a data packet.
     */
    public static final byte FLAGBIT_UNRECOVERABLE = (byte) (1 << 4);
    public static final byte FLAGBIT_SESSION_DOWN = (byte) (1 << 5);
    public static final byte FLAGBIT_MEMBER_PRUNED = (byte) (1 << 6);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7); 

    public static final int HAINTERVAL = 0;
    public static final int SEQUENCENUMBER = HAINTERVAL + 2;
    public static final int FORGETBEFORESEQNUM = SEQUENCENUMBER + 4;
    public static final int DATALENGTH = FORGETBEFORESEQNUM + 4;
    public static final int ACK_WINDOW = DATALENGTH + 2;
    public static final int RESERVED = ACK_WINDOW + 2;
    public static final int FLOW_CONTROL_INFO = RESERVED + 2;
    public static final int DATA_RATE = FLOW_CONTROL_INFO + 4;
    public static final int SOURCEADDRESS = DATA_RATE + 4;
    public static final int PAYLOAD =  SOURCEADDRESS + 4; // 4 is valid for
							  // only V4 address.
							  // Need to change it
							  // when java supports
							  // V6.
							  //
    public static final int TRAMDATAHEADERLENGTH = PAYLOAD;
    public static final int TRAMMAXPKT = 65507 - PAYLOAD 
                                        - TRAMPacket.TRAMHEADERLENGTH;
    private InetAddress sourceAddress;
    private int sequenceNumber = 0;
    private int forgetBeforeSeqNum = 1; // sets default behaviour, 
					// sequence numbers begin from 1. 
    private short haInterval = 0;
    private byte ttl = 0;
    private short dataLength = 0;
    private short ackWindow = 0;
    private int flowControlInfo = 0;
    private int dataRate = 0;

    /**
     * Create an inbound TRAMDataPacket. The incoming DatagramPacket is
     * passed to the TRAMPacket class where it is disassembled and released.
     * The sequence number is then obtained from the buffer. If the checksum
     * check fails, throw the InvalidChecksumException. The calling method
     * must drop the packet as it is corrupt.
     * 
     * @param dp a DatagramPacket received from the network.
     */
    public TRAMDataPacket(DatagramPacket dp) {
        super(dp);

        sequenceNumber = super.readInt(SEQUENCENUMBER);
	forgetBeforeSeqNum = super.readInt(FORGETBEFORESEQNUM);

        int i = super.readInt(SOURCEADDRESS);

        sourceAddress = Util.intToInetAddress(i);
        haInterval = super.readShort(HAINTERVAL);
        dataLength = super.readShort(DATALENGTH);
	ackWindow = super.readShort(ACK_WINDOW);
	flowControlInfo = super.readInt(FLOW_CONTROL_INFO);
	dataRate = super.readInt(DATA_RATE);
    }

    /**
     * Create an outbound TRAMDataPacket. The user buffer is handed to the
     * TRAMPacket class where it is copied into a buffer large enough for
     * both the standard TRAMPacket header and the TRAMDataPacket specific
     * header. The TRAMPacket header information is stored in the buffer
     * before returning.
     * 
     * @param buff[] a byte array of user data to be sent on the network.
     */
    public TRAMDataPacket(TRAMControlBlock tramblk, byte buff[], 
			  int bufLength) {
        super(buff, bufLength, PAYLOAD, tramblk.getSessionId());

        dataLength = (short) bufLength;

        super.writeShort(dataLength, DATALENGTH);

        setMessageType(MESGTYPE.MCAST_DATA);
        setSubType(SUBMESGTYPE.DATA);

        sourceAddress = tramblk.getLocalHost();

        super.writeInt(Util.InetAddressToInt(sourceAddress), SOURCEADDRESS);
	super.writeInt(0, RESERVED); // This line needs to be changed when
				     // we decide to use RESERVED field
				     // to something else.
				     //
    }

    public TRAMDataPacket(TRAMControlBlock tramblk, byte buff[], int bufLength, 
                         int signatureLen) {
        super(buff, bufLength, PAYLOAD, tramblk.getSessionId(), signatureLen);

        dataLength = (short) bufLength;

        super.writeShort(dataLength, DATALENGTH);

        setMessageType(MESGTYPE.MCAST_DATA);
        setSubType(SUBMESGTYPE.DATA);

        sourceAddress = tramblk.getLocalHost();

        super.writeInt(Util.InetAddressToInt(sourceAddress), SOURCEADDRESS);
	super.writeInt(0, RESERVED); // This line needs to be changed when
				     // we decide to use RESERVED field
				     // to something else.
				     //
    }

    public TRAMDataPacket(TRAMControlBlock tramblk) {
        super(PAYLOAD, tramblk.getSessionId());

        setMessageType(MESGTYPE.MCAST_DATA);
        setSubType(SUBMESGTYPE.DATA);

        sourceAddress = tramblk.getLocalHost();
	super.writeInt(0, RESERVED); // This line needs to be changed when
				     // we decide to use RESERVED field
				     // to something else.
				     //
    }

    /**
     * Return the sequence number for this data packet. A value of zero is
     * returned if the sequence number hans't been set yet.
     * 
     * @return the sequence number of this packet.
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }


    /**
     * Return the forgetBeforeSeqNum of this packet. All packets before this
     * sequence number are no longer to be recovered/delivered.
     *
     * @return the forgetBeforeSeqNum of this packet.
     */
    public int getForgetBeforeSeqNum() {
	return forgetBeforeSeqNum;
    }

    /**
     * @return the TTL value for this packet.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * @return the length of the data portion of the packet. TRAM headers
     * are not included in this length.
     */
    public int getLength() {
        return (super.getLength() - PAYLOAD);
    }

    /**
     * Return the HA Interval specified for this packet.
     * 
     * 
     * @return the HA spcified for this packet.
     */
    public short getHaInterval() {
        return haInterval;
    }

    /**
     * Return the user data for this packet. The method strips off
     * the transport headers and returns the user data portion.
     * 
     * @return a byte array containing the user data.
     */
    public byte[] getData() {
        return super.readBuffer(PAYLOAD);
    }

    /**
     * Get the Ack Window value from the packet.
     * 
     * @return ackWindow The ackWindow value from the packet.
     */
    public int getAckWindow() {
	return ((int)ackWindow) & 0x0000ffff;
    }

    /**
     * Set the Ack Window value for this packet.
     * 
     * 
     * @param ackWindow The ackWindow value that is to be set for this packet.
     */
    public void setAckWindow(short ackWindow) {
	this.ackWindow = ackWindow;
	super.writeShort(ackWindow, ACK_WINDOW);
    }

    /**
     * Get the flow control information from the packet.
     * 
     * @return flow control information from the packet.
     */
    public int getFlowControlInfo() {
	return (flowControlInfo);
    }

    /**
     * Set the flow control information in this packet.
     * 
     * @param flow control information to set in this packet.
     */
    public void setFlowControlInfo(int flowControlInfo) {
	this.flowControlInfo = flowControlInfo;
	super.writeInt(flowControlInfo, FLOW_CONTROL_INFO);
    }

    /**
     * Get the current data rate from the packet.
     * 
     * @return current data rate from the packet.
     */
    public int getDataRate() {
	return (dataRate);
    }

    /**
     * Set the current data rate in this packet.
     * 
     * @param current data rate to set in this packet.
     */
    public void setDataRate(int dataRate) {
	this.dataRate = dataRate;
	super.writeInt(dataRate, DATA_RATE);
    }

    /**
     * Set the HA Interval for this packet.
     * 
     * 
     * @param haInterval The HA interval that is to be set for this packet.
     */
    public void setHaInterval(short haInterval) {
        if (isTransmit()) {
            this.haInterval = haInterval;

            super.writeShort(haInterval, HAINTERVAL);
        }
    }

    /**
     * Set the sequence number for this packet. The TRAMDataPacket class
     * does not set the sequence number for outbound packets. This is typically
     * done in the transmitter when the packet is actually sent.
     * 
     * @param i the sequence number for this packet. (integer greater than 0)
     */
    public void setSequenceNumber(int i) {
        if (isTransmit()) {
            sequenceNumber = i;

            super.writeInt(i, SEQUENCENUMBER);
        }
    }

    /** 
     * Set the forgetBeforeSeqNum for this packet. The TRAMDataPacket class 
     * does not usually set the forgetBeforeSeqNum for the outbound packets.
     * This is typically done in the transmitter when the packet is actually
     * sent.
     *
     * @param i the forgetBeforeSeqNum for this packet (integer greater than 0)
     */
    public void setForgetBeforeSeqNum(int i) {
	if (isTransmit()) {
	    forgetBeforeSeqNum = i;
			
	    super.writeInt(i, FORGETBEFORESEQNUM);
	}
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
     * DataPacket header. Once calculated we let the TRAMPacket parent
     * actually create the DatagramPacket.
     * 
     * NOTE: In the future we may want to change the CRC32 algoritim to a
     * CRC16 and possibly just checksum our header and not the data.
     * 
     * @return a DatagramPacket with the current TRAMDataPacket contents.
     */
    public DatagramPacket createDatagramPacket() {

        // super.writeInt(Util.InetAddressToInt(sourceAddress),SOURCEADDRESS);

        DatagramPacket dp = super.createDatagramPacket();

        return dp;
    }

    /**
     * gets the Source Address from the internal field.
     * 
     * @return InetAddress - the source address.
     */
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    /**
     * gets the Source Address from the internal field.
     * 
     * @return InetAddress - the source address.
     */
    public int getDataLength() {
        return dataLength;
    }

    /**
     * sets the specified Source Address value to the internal field.
     * 
     * @param InetAddress - Source address to be assigned.
     */
    public void setSourceAddress(InetAddress address) {
        sourceAddress = address;

        super.writeInt(Util.InetAddressToInt(sourceAddress), SOURCEADDRESS);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the write command off
     * to the parent class.
     * 
     * @param value the byte to write to the buffer.
     * @param offset the byte offset in the childs header to place the
     * byte.
     */
    public void writeByte(byte value, int offset) {
        offset += TRAMDATAHEADERLENGTH;

        super.writeByte(value, offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the write command off
     * to the parent class.
     * 
     * @param value the short to write to the buffer.
     * @param offset the byte offset in the childs header to place the
     * short.
     */
    public void writeShort(short value, int offset) {
        offset += TRAMDATAHEADERLENGTH;

        super.writeShort(value, offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the write command off
     * to the parent class.
     * 
     * @param value the int to write to the buffer.
     * @param offset the byte offset in the childs header to place the
     * int.
     */
    public void writeInt(int value, int offset) {
        offset += TRAMDATAHEADERLENGTH;

        super.writeInt(value, offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the write command off
     * to the parent class.
     * 
     * @param value the byte array to write to the buffer.
     * @param offset the byte offset in the childs header to place the
     * byte array.
     */
    public void writeBuffer(byte buffer[], int length, int offset) {
        offset += TRAMDATAHEADERLENGTH;

        super.writeBuffer(buffer, length, offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the read command off
     * to the parent class.
     * 
     * @param offset the byte offset in the childs header to read the
     * byte.
     * @return the byte read from the buffer.
     */
    public byte readByte(int offset) {
        offset += TRAMDATAHEADERLENGTH;

        return super.readByte(offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the read command off
     * to the parent class.
     * 
     * @param offset the byte offset in the childs header to read the
     * short.
     * @return the short read from the buffer.
     */
    public short readShort(int offset) {
        offset += TRAMDATAHEADERLENGTH;

        return super.readShort(offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the read command off
     * to the parent class.
     * 
     * @param offset the byte offset in the childs header to read the
     * int.
     * @return the int read from the buffer.
     */
    public int readInt(int offset) {
        offset += TRAMDATAHEADERLENGTH;

        return super.readInt(offset);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the read command off
     * to the parent class.
     * 
     * @param offset the byte offset in the childs header to read the
     * byte array.
     * @param length the number of bytes to read from the buffer.
     * @param value the byte array to read from the buffer.
     */
    public byte[] readBuffer(int offset, int length) {
        offset += TRAMDATAHEADERLENGTH;

        return super.readBuffer(offset, length);
    }

    /**
     * This routine is provided to all other classes to extend the
     * TRAMDataPacket class. This method adds in the size of the header
     * at this layer to the total and passes the read command off
     * to the parent class.
     * 
     * @param offset the byte offset in the childs header to read the
     * byte array.
     * @return the byte array read from the buffer.
     */
    public byte[] readBuffer(int offset) {
        offset += TRAMDATAHEADERLENGTH;

        return super.readBuffer(offset);
    }

}




