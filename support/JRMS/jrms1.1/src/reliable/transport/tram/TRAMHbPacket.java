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
 * TRAMHbPacket.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMHbPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */

    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Packet Offset definitions.
     */
    private static final short TTL = 0;
    private static final short MROLE = TTL + 1;

    private static final short DIR_MEM = MROLE + 1;
    private static final short INDIR_MEM = DIR_MEM + 2;
    private static final short SOURCE_ADDR = INDIR_MEM + 2;
    private static final short HBDATA =  SOURCE_ADDR + 4; // 4 - is valid for
							  // only V4 address.
							  // Needs to fix for
							  // V6 when java
							  // supports it.
							  //


    /*
     * internal variables
     */
    private byte ttl = 0;
    private byte mrole = 0;
    private InetAddress dataSrcAddress = null;
    private short directMemberCount = 0;
    private short indirectMemberCount = 0;

    /**
     * Decode the various RM fields from an incoming datagram packet.
     * 
     * @param received datagram packet.
     */
    public TRAMHbPacket(DatagramPacket dp) {
        super(dp);

        ttl = super.readByte(TTL);
        mrole = (byte) ((super.readByte(MROLE) >>> 4) & 0x0f);

        int i = super.readInt(SOURCE_ADDR);

        dataSrcAddress = Util.intToInetAddress(i);
        directMemberCount = super.readShort(DIR_MEM);
        indirectMemberCount = super.readShort(INDIR_MEM);
    }

    /**
     * Method to build an TRAMHbPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     */
    public TRAMHbPacket(TRAMControlBlock tramblk, byte ttl) {
        super(HBDATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.HB);

        mrole = tramblk.getTransportProfile().getMrole();

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        dataSrcAddress = tp.getDataSourceAddress();
        this.ttl = ttl;
        directMemberCount = 
            (short) tramblk.getGroupMgmtBlk().getDirectMemberCount();
        indirectMemberCount = 
            (short) tramblk.getGroupMgmtBlk().getIndirectMemberCount();
    }

    /**
     * Creates a DatagramPacket based on the information already
     * specified in the internal fields.
     * 
     * @return the build Datagram packet.
     * 
     */
    public DatagramPacket createDatagramPacket() {
        super.writeByte(ttl, TTL);

        short mroleTmp = (short) (mrole << 4);

        mroleTmp = (short) (mroleTmp & ((short) 0x00f0));

        super.writeByte(((byte) mroleTmp), MROLE);
        super.writeInt(Util.InetAddressToInt(dataSrcAddress), SOURCE_ADDR);
        super.writeShort(directMemberCount, DIR_MEM);
        super.writeShort(indirectMemberCount, INDIR_MEM);

        return super.createDatagramPacket();
    }

    /**
     * gets the TTL field value.
     * 
     * @return byte - the ttl field value.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * sets the TTL field value.
     * 
     * @param byte - the ttl field value.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * gets the MROLE field value.
     * 
     * @return byte - the mrole field value.
     */
    public byte getMrole() {
        return mrole;
    }

    /**
     * sets the MROLE field value.
     * 
     * @param byte - the MROLE field value.
     */
    public void setMrole(byte mrole) {
        this.mrole = mrole;
    }

    /**
     * gets the address of the multicast Data stream that is
     * specified in the internal field.
     * 
     * @return - InetAddress of the multicast data source.
     */
    public InetAddress getDataSrcAddress() {
        return dataSrcAddress;
    }

    /**
     * sets the specified address as the souce of the multicast data
     * stream in the internal field.
     * 
     * @param -InetAddress - Address of the souce of the multicast
     * data stream.
     */
    public void setDataSrcAddress(InetAddress address) {
        dataSrcAddress = address;
    }

    /**
     * gets the DirectMemberCount field value.
     * 
     * @return short - the DirectMemberCount field value.
     */
    public short getDirectMemberCount() {
        return directMemberCount;
    }

    /**
     * sets the DirectMemberCount field value.
     * 
     * @param byte - the DirectMemberCount field value.
     */
    public void setDirectMemberCount(short directMemberCount) {
        this.directMemberCount = directMemberCount;
    }

    /**
     * gets the IndirectMemberCount field value.
     * 
     * @return short - the IndirectMemberCount field value.
     */
    public short getIndirectMemberCount() {
        return indirectMemberCount;
    }

    /**
     * sets the IndirectMemberCount field value.
     * 
     * @param byte - the IndirectMemberCount field value.
     */
    public void setIndirectMemberCount(short indirectMemberCount) {
        this.indirectMemberCount = indirectMemberCount;
    }

}

