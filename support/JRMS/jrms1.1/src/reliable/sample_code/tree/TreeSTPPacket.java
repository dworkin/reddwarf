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
 * TreeSTPPacket.java
 * 
 * Module Description:
 * 
 * This module defines the basic STP packet format. When a packet is
 * received, this class is created to retrieve the STP header information
 * and move any user data into a new DatagramPacket. Specific packet
 * types must extend this class.
 */
package com.sun.multicast.reliable.applications.tree;

import java.net.DatagramPacket;
import java.net.InetAddress;
import sun.misc.CRC16;
import com.sun.multicast.util.Util;

/**
 * The STPPacket class is the parent to all STP packets. It defines and loads
 * or parses the standard header for all packets in the STP system. It defines
 * the version, sets the message type fields, owns the message buffer,
 * and creates the output datagram packet.
 */
class TreeSTPPacket {

    /*
     * Define the standard packet header offsets.
     */
    public static final int STPVERSION = 0;
    public static final int STPMESSAGETYPE = 1;
    public static final int STPSUBTYPE = 2;
    public static final int STPFLAGS = 3;
    public static final int STPLENGTH = 4;
    public static final int STPDATA = 6;
    public static final int STPHEADERLENGTH = 6;
    public static final int STP_VERSION = 1;

    /*
     * Provate variables for this class.
     */
    private int version = STP_VERSION;
    private int messageType;
    private int subType;
    private int flags = 0;
    private int length;
    private byte b[];
    private int port;
    private InetAddress address;
    private boolean transmit;

    /**
     * A outbound STPPacket is created with the user buffer and an offset
     * defining the header length. The offset includes all of the classes
     * headers that extend this class. The STP standard packet header
     * is added to this and a new buffer is created for the entire
     * packet. The user buffer is copied into the buffer at the offset
     * specified and STPPacket header specific fields are filled in.
     * 
     * @param buff a byte array containing the user data to be sent in the
     * packet.
     * @param length the length of the input buffer
     * @param headerLength the length of the headers for packets extending
     * this class.
     */
    public TreeSTPPacket(byte buff[], int length, int headerLength) {
        int bufferOffset = headerLength + STPHEADERLENGTH;

        b = new byte[length + bufferOffset];

        System.arraycopy(buff, 0, b, bufferOffset, length);

        b[STPVERSION] = (byte) version;
        b[STPMESSAGETYPE] = (byte) messageType;
        b[STPSUBTYPE] = (byte) subType;
        b[STPFLAGS] = (byte) flags;
        this.length = b.length;

        Util.writeShort((short) this.length, b, STPLENGTH);

        transmit = true;
    }

    /**
     * Set the message type field.
     * 
     * @param type an integer message type value.
     */
    public void setMessageType(int type) {
        messageType = type;
        b[STPMESSAGETYPE] = (byte) type;
    }

    /**
     * Set the message subtype field.
     * 
     * @param type an integer message subtype value.
     */
    public void setSubType(int type) {
        subType = type;
        b[STPSUBTYPE] = (byte) type;
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
        b[STPFLAGS] = flags;
    }

    /**
     * Create a DatagramPacket from the information already setup in
     * the STPPacket class. The buffer is prebuilt and initialized when
     * this class is created. All headers have been filled in and the
     * byte array is ready to go.
     * 
     * @return a DatagramPacket representing the STPPacket and all headers.
     */
    public DatagramPacket createDatagramPacket() {
        return new DatagramPacket(b, length, address, (port & 0xffff));
    }

    /**
     * Write a byte to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common STPPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeByte(byte value, int offset) {
        offset += STPHEADERLENGTH;
        b[offset] = value;
    }

    /**
     * Write a short to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common STPPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeShort(short value, int offset) {
        offset += STPHEADERLENGTH;

        Util.writeShort(value, b, offset);
    }

    /**
     * Write an int to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common STPPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeInt(int value, int offset) {
        offset += STPHEADERLENGTH;

        Util.writeInt(value, b, offset);
    }

    /**
     * Write a byte array to the buffer. The offset specified is that
     * of the child class and does not include the header in the
     * common STPPacket class. Add in the our offset and put the
     * data at this location.
     * 
     * @param value the data to be written.
     * @param offset the offset into the buffer to write the data.
     * This offset is relative to the childs view of the packet and
     * does not include this layers header.
     */
    public void writeBuffer(byte buffer[], int length, int offset) {
        offset += STPHEADERLENGTH;

        System.arraycopy(buffer, 0, b, offset, length);
    }

    /**
     * The compute checksum method is called from any class that
     * requires a checksum field. The checksum is calculated upon the
     * entire buffer that this class has constructed. The crc is
     * returned to the caller and can then be placed into the buffer
     * or checked against an existing checksum.
     */
    public int computeChecksum() {
        CRC16 crc = new CRC16();

        for (int i = 0; i < length; i++) {
            crc.update(b[i]);
        }

        return crc.value;
    }

}

