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
 * TRAMHaPacket.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMHaPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_ELECT_LAN_HEAD = (byte) (1 << 0);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Packet Offset definitions.
     */
    private static final short TTL = 0;
    private static final short HSTATE = (TTL + 1);
    // MROLE shares the same byte as HSTATE.
    private static final short MROLE = (HSTATE + 0);

    private static final short RXLEVEL = MROLE + 1;
    private static final short LSTATE = RXLEVEL + 1;
    private static final short UCAST_PORT = LSTATE + 1;
    private static final short DIR_MEM = UCAST_PORT + 2;
    private static final short CAPACITY = DIR_MEM + 2;
    private static final short SOURCE_ADDR = CAPACITY  + 2;
    private static final short HADATA = SOURCE_ADDR + 4; // 4 bytes is only
							 // applicable for 
							 // ONLY IPV4. Need to
							 // revisit when Java 
							 // supports IPV6.
							 //
    /*
     * internal variables
     */
    private byte ttl = 0;
    private byte hstate = 0;
    private byte mRole = 0;
    private InetAddress dataSrcAddress = null;
    private byte rxLevel = 0;
    private byte lstate = 0;
    private short unicastPort = 0;
    private short directMemberCount = 0;
    private short capacity = 0;

    /**
     * Decode the various RM fields from an incoming datagram packet.
     * 
     * @param received datagram packet.
     */
    public TRAMHaPacket(DatagramPacket dp) {
        super(dp);

        ttl = super.readByte(TTL);
        hstate = (byte) ((super.readByte(HSTATE) >>> 4) & 0x0f);
        mRole = (byte) ((super.readByte(HSTATE)) & 0x0f);
        rxLevel = super.readByte(RXLEVEL);
        lstate = super.readByte(LSTATE);

        int i = super.readInt(SOURCE_ADDR);

        dataSrcAddress = Util.intToInetAddress(i);
        unicastPort = super.readShort(UCAST_PORT);
        directMemberCount = super.readShort(DIR_MEM);
        capacity = super.readShort(CAPACITY);
    }

    /**
     * Method to build an TRAMHaPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     */
    public TRAMHaPacket(TRAMControlBlock tramblk, byte ttl, 
                       boolean needNewLanVolunteer) {
        super(HADATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.MCAST_CNTL);
        setSubType(SUBMESGTYPE.HA);

        hstate = tramblk.getGroupMgmtBlk().getHstate();
        mRole = tramblk.getTransportProfile().getMrole();

        if (ttl == 1) {
            lstate = tramblk.getGroupMgmtBlk().getLstate();
        } 

        rxLevel = (byte) tramblk.getGroupMgmtBlk().getRxLevel();

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        unicastPort = (short) tramblk.getUnicastPort();

        setAddress(tp.getAddress());
        setPort(tp.getPort());

        dataSrcAddress = tp.getDataSourceAddress();
        this.ttl = ttl;
        directMemberCount = 
            (short) tramblk.getGroupMgmtBlk().getDirectMemberCount();
        capacity = (short) tp.getMaxMembers();

        if (needNewLanVolunteer) {
            setFlags((byte) (FLAGBIT_ELECT_LAN_HEAD));
        } 
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

        short hstateTmp = (short) (hstate << 4);

        hstateTmp = (short) (hstateTmp | ((short) mRole));

        super.writeByte(((byte) hstateTmp), HSTATE);
        super.writeByte(rxLevel, RXLEVEL);
        super.writeByte(lstate, LSTATE);
        super.writeShort(unicastPort, UCAST_PORT);
        super.writeInt(Util.InetAddressToInt(dataSrcAddress), SOURCE_ADDR);
        super.writeShort(directMemberCount, DIR_MEM);
        super.writeShort(capacity, CAPACITY);

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
     * gets the HSTATE field value.
     * 
     * @return byte - the hstate field value.
     */
    public byte getHstate() {
        return hstate;
    }

    /**
     * sets the Hstate field value.
     * 
     * @param byte - the HSTATE field value.
     */
    public void setHstate(byte hstate) {
        this.hstate = hstate;
    }

    /**
     * gets the mRole field value.
     * 
     * @return byte - the mRole field value.
     */
    public byte getMrole() {
        return mRole;
    }

    /**
     * sets the mRole field value.
     * 
     * @param byte - the mRole field value.
     */
    public void setMrole(byte mRole) {
        this.mRole = mRole;
    }

    /**
     * gets the LSTATE field value.
     * 
     * @return byte - the lstate field value.
     */
    public byte getLstate() {
        return lstate;
    }

    /**
     * sets the Lstate field value.
     * 
     * @param byte - the LSTATE field value.
     */
    public void setLstate(byte lstate) {
        this.lstate = lstate;
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
     * gets the RXLEVEL field value.
     * 
     * @return byte - the rxlevel field value.
     */
    public byte getRxLevel() {
        return rxLevel;
    }

    /**
     * sets the RXLEVEL field value.
     * 
     * @param byte - the RXLEVEL field value.
     */
    public void setRxLevel(byte rxLevel) {
        this.rxLevel = rxLevel;
    }

    /**
     * gets the Unicast Port field value.
     * 
     * @return short - the Unicast Port field value.
     */
    public int getUnicastPort() {
        return (int) (unicastPort & 0xffff);
    }

    /**
     * Assigns the specified Unicast port  value to the internal field.
     * 
     * @param byte - the Unicast Port field value.
     */
    public void setUnicastPort(int unicastPort) {
        this.unicastPort = (short) unicastPort;
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
     * gets the capacity field value.
     * 
     * @return short - the capacity field value.
     */
    public short getCapacity() {
        return capacity;
    }

    /**
     * sets the capacity field value.
     * 
     * @param short - the capacity field value.
     */
    public void setCapacity(short value) {
        capacity = value;
    }

}

