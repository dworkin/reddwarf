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
 * TRAMPacket.java
 * 
 * Module Description:
 * 
 * This module defines the basic TRAM packet format. When a packet is
 * received, this class is created to retrieve the TRAM header information
 * and move any user data into a new DatagramPacket. Specific packet
 * types must extend this class.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.DatagramPacket;
import java.net.InetAddress;
import com.sun.multicast.util.Util;

/**
 * The TRAMPacket class is the parent to all TRAM packets. It defines and loads
 * or parses the standard header for all packets in the TRAM system. It defines
 * the version, sets the message type fields, owns the message buffer,
 * and creates the output datagram packet.
 */
class TRAMPacket {

    /*
     * Define the standard packet header offsets.
     */
    public static final int TRAMVERSION = 0;
    public static final int TRAMMESSAGETYPE = TRAMVERSION + 1;
    public static final int TRAMSUBTYPE = TRAMMESSAGETYPE + 1;
    public static final int TRAMFLAGS = TRAMSUBTYPE + 1;
    public static final int TRAMSESSIONID = TRAMFLAGS + 1;
    public static final int TRAMLENGTH = TRAMSESSIONID + 4;
    public static final int TRAMDATA = TRAMLENGTH + 2;
    public static final int TRAMHEADERLENGTH = TRAMDATA;

    public static final int TRAM_VERSION = 2;

    /*
     * Provate variables for this class.
     */
    private int version = TRAM_VERSION;
    private int messageType;
    private int subType;
    private int flags = 0;
    private int sessionId;
    private int length;
    private byte b[];
    private int port;
    private InetAddress address;
    private boolean transmit;
    private boolean transmitPending;
    private long lastTransmitTime;

    /**
     * An inbound packet has been received. The DatagramPacket is parsed
     * filling in all the relevant fields. A new buffer is created that
     * holds the incoming data. The datagram packet received can be discarded
     * at this point.
     * 
     * @param sp a DatagramPacket containing data from an incoming packet.
     */
    public TRAMPacket(DatagramPacket dp) {
        b = dp.getData();
        version = (int) b[TRAMVERSION] & 0xff;
        messageType = (int) b[TRAMMESSAGETYPE] & 0xff;
        subType = (int) b[TRAMSUBTYPE] & 0xff;
        flags = (int) b[TRAMFLAGS] & 0xff;
        length = Util.readUnsignedShort(b, TRAMLENGTH);
        sessionId = Util.readInt(b, TRAMSESSIONID);
        address = dp.getAddress();
        port = dp.getPort();
        transmit = false;
    }

    /**
     * A outbound TRAMPacket is created with the user buffer and an offset
     * defining the header length. The offset includes all of the classes
     * headers that extend this class. The TRAM standard packet header
     * is added to this and a new buffer is created for the entire
     * packet. The user buffer is copied into the buffer at the offset
     * specified and TRAMPacket header specific fields are filled in.
     * 
     * @param buff a byte array containing the user data to be sent in the
     * packet.
     * @param length the length of the input buffer
     * @param headerLength the length of the headers for packets extending
     * this class.
     */
    public TRAMPacket(byte buff[], int bufLength, int headerLength, 
                     int sessionId) {
        buildPacket(buff, bufLength, headerLength, sessionId, 0);
    }

    public TRAMPacket(byte buff[], int bufLength, int headerLength, 
                     int sessionId, int signatureLen) {
        buildPacket(buff, bufLength, headerLength, sessionId, signatureLen);
    }

    public TRAMPacket(int headerLength, int sessionId) {
        this(null, 0, headerLength, sessionId);
    }

    /*
     * private method to build an TRAMPacket
     */

    private void buildPacket(byte[] buff, int bufLen, int headerLen, 
                             int sessionId, int signatureLen) {
        int bufferOffset = headerLen + TRAMHEADERLENGTH;

        b = new byte[bufLen + bufferOffset + signatureLen];

        if (bufLen != 0) {
            System.arraycopy(buff, 0, b, bufferOffset, bufLen);
        }

        b[TRAMVERSION] = (byte) version;
        b[TRAMMESSAGETYPE] = (byte) messageType;
        b[TRAMSUBTYPE] = (byte) subType;
        b[TRAMFLAGS] = (byte) flags;

        Util.writeInt(sessionId, b, TRAMSESSIONID);

        this.sessionId = sessionId;
        this.length = b.length;

        Util.writeShort((short) this.length, b, TRAMLENGTH);

        transmit = true;
    }

    /**
     * Return the sessionId
     */
    public int getSessionId() {
        return sessionId;
    }

    /**
     * The getMessageType static method peeks into a DatagramPacket to identify
     * the packet type. This method is handy for incoming packets with no
     * identity. The incoming packet dispatcher calls this method to identify
     * a packet type to create.
     * 
     * @param dp a DatagramPacket
     * @return the message type field.
     */
    public static int getMessageType(DatagramPacket dp) {
        byte b[] = dp.getData();

        return (b[TRAMMESSAGETYPE] & 0xff);
    }

    /**
     * The getVersionNumber static method peeks into a DatagramPacket to 
     * get the packet version number. This method is handy for incoming 
     * packets with no identity. The incoming packet dispatcher calls 
     * this method to identify a packet type to create.
     * 
     * @param dp a DatagramPacket
     * @return the packet version number field.
     */
    public static int getVersionNumber(DatagramPacket dp) {
        byte b[] = dp.getData();

        return (b[TRAMVERSION] & 0xff);
    }

    public static int getId(DatagramPacket dp) {
        byte b[] = dp.getData();

        return (Util.readInt(b, TRAMSESSIONID));
    }

    /**
     * The getSubType static method peeks into a DatagramPacket to identify
     * the packet subtype. This method is handy for incoming packets with no
     * identity. The incoming packet dispatcher calls this method to identify
     * a packet subtype to create.
     * 
     * @param dp a DatagramPacket
     * @return the message type field.
     */
    public static int getSubType(DatagramPacket dp) {
        byte b[] = dp.getData();

        return (b[TRAMSUBTYPE] & 0xff);
    }

    /**
     * Retrirve the packet version number.
     * 
     * @return the packet version number
     */
    public int getVersion() {
        return version;
    }

    /**
     * Retrieve the packet message type.
     * 
     * @return the packet message type
     */
    public int getMessageType() {
        return messageType;
    }

    /**
     * Retrieve the packet subtype field.
     * 
     * @return the packet subtype field.
     */
    public int getSubType() {
        return subType;
    }

    /**
     * Retrieve the packet flags field.
     * 
     * @return the packet flags field.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Retrieve the length of the packet.
     * 
     * @return the length of the packet.
     */
    public int getLength() {
        return length - TRAMHEADERLENGTH;
    }

    /**
     * Return the buffer for this packet. This buffer represents the whole
     * packet including the headers. This buffer can be sent on the network
     * as is.
     * 
     * @return a byte array representing the packet.
     */
    public byte[] getBuffer() {
        return b;
    }

    /**
     * @return the IP address for this packet.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @return the port number for this packet.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns a boolean indicating whether this is a packet created for
     * transmission or whether it was received over the network. This
     * determiniation is made based on the constructor called to create
     * the packet. A transmit packet simply supplies the user buffer. A
     * received packet is created with a DatagramPacket.
     * 
     * @return true or false indicating whether this packet was created
     * locally for transmission or received over the network.
     */
    public boolean isTransmit() {
        return transmit;
    }

    /**
     * Set the message type field.
     * 
     * @param type an integer message type value.
     */
    public void setMessageType(int type) {
        messageType = type;
        b[TRAMMESSAGETYPE] = (byte) type;
    }

    /**
     * Set the message subtype field.
     * 
     * @param type an integer message subtype value.
     */
    public void setSubType(int type) {
        subType = type;
        b[TRAMSUBTYPE] = (byte) type;
    }

    /**
     * The the address for this packet.
     * 
     * @param address the InetAddress for this packet.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * Set the port for this packet.
     * 
     * @param port the port number for this packet.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Set the packet flags field.
     * 
     * @param the packet flags field.
     */
    public void setFlags(byte flags) {
        this.flags = (((int) flags) & 0xff); // suppress the -ve bit carryover
        b[TRAMFLAGS] = flags;
    }

    /**
     * Create a DatagramPacket from the information already setup in
     * the TRAMPacket class. The buffer is prebuilt and initialized when
     * this class is created. All headers have been filled in and the
     * byte array is ready to go.
     * 
     * @return a DatagramPacket representing the TRAMPacket and all headers.
     */
    public DatagramPacket createDatagramPacket() {
        return new DatagramPacket(b, length, address, (port & 0xffff));
    }

    /**
     * Write a byte to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeByte(byte value, int offset) {
        offset += TRAMHEADERLENGTH;
        b[offset] = value;
    }

    /**
     * Write a short to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeShort(short value, int offset) {
        offset += TRAMHEADERLENGTH;

        Util.writeShort(value, b, offset);
    }

    /**
     * Write an int to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeInt(int value, int offset) {
        offset += TRAMHEADERLENGTH;

        Util.writeInt(value, b, offset);
    }

    /**
     * Write a byte array to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeBuffer(byte buffer[], int length, int offset) {
        offset += TRAMHEADERLENGTH;

        System.arraycopy(buffer, 0, b, offset, length);
    }

    /**
     * Read a byte from the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     * @return the data read from the buffer.
     */
    public byte readByte(int offset) {
        offset += TRAMHEADERLENGTH;

        return b[offset];
    }

    /**
     * Read a short from the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     * @return the data read from the buffer.
     */
    public short readShort(int offset) {
        offset += TRAMHEADERLENGTH;

        return (short) Util.readUnsignedShort(b, offset);
    }

    /**
     * Read an int from the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     * @return the data read from the buffer.
     */
    public int readInt(int offset) {
        offset += TRAMHEADERLENGTH;

        return (int) Util.readInt(b, offset);
    }

    /**
     * Read a byte array from the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     * @param length the number of bytes to read
     * @return the data read from the buffer.
     */
    public byte[] readBuffer(int offset, int length) {
        offset += TRAMHEADERLENGTH;

        byte buff[] = new byte[length];

        System.arraycopy(b, offset, buff, 0, length);

        return buff;
    }

    /**
     * Read a byte array from the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common TRAMPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     * @return the data read from the buffer. The size of the data is
     * the offset to the end of the buffer.
     */
    public byte[] readBuffer(int offset) {
        offset += TRAMHEADERLENGTH;

        byte buff[] = new byte[length - offset];

        System.arraycopy(b, offset, buff, 0, buff.length);

        return buff;
    }

    public void setTransmitPending(boolean value) {
        transmitPending = value;
    }

    public boolean isTransmitPending() {
        return transmitPending;
    }

    public long getLastTransmitTime() {
        return lastTransmitTime;
    }

    public void setLastTransmitTime(long time) {
        lastTransmitTime = time;
    }

}

