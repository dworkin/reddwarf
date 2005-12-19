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
 * TRAMHelloPacket.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMHelloPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_ACK = (byte) (1 << 0);
    public static final byte FLAGBIT_TXDONE = (byte) (1 << 1);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Offset definitions
     */
    private static final short V4_ADDR_SIZE = 4;
    private static final short TTL = 0;
    private static final short HSTATE = (TTL + 1);
    private static final short RESERVED = (HSTATE + 0); // HSTATE & RESERVED 
							// share the same byte
							// hence the offset is
							// 0.
							//
    private static final short RESERVED1 = (RESERVED + 1);
    private static final short ACKMEM_COUNT = (RESERVED1 + 2);
    private static final short RXLEVEL = (ACKMEM_COUNT + 1);
    private static final short UCAST_PORT = (RXLEVEL + 1);
    private static final short MEMBER_COUNT = (UCAST_PORT + 2);
    private static final short RESERVED2 = (MEMBER_COUNT + 1);
    private static final short LOW_SEQ_NUMBER = (RESERVED2 + 1);
    private static final short HIGH_SEQ_NUMBER = (LOW_SEQ_NUMBER + 4);
    private static final short SOURCE_ADDR = (HIGH_SEQ_NUMBER + 4);
    private static final short MEMBER_ADDR = (SOURCE_ADDR + V4_ADDR_SIZE);

    /*
     * Note Hello Data is not actually the end of the Hello packet
     * as the length is dependent on the number address entries included.
     */
    private static final short HELLODATA = MEMBER_ADDR;

    /*
     * Private fields
     */
    private byte ttl = 0;
    private byte rxLevel = 0;
    private byte hstate = 0;
    private int ackMemberCount = 0;
    private int unicastPort = 0;
    private int memberCount = 0;
    private byte reserv1 = 0;
    private InetAddress srcAddress = null;
    private int timeStamp = 0;
    private int lowSeqNumber = 0;
    private int highSeqNumber = 0;
    private InetAddress[] addressList = null;

    /**
     * Decode the various Hello fields from an incoming datagram packet.
     * 
     * @param -DatagramPacket - received from the net.
     */
    public TRAMHelloPacket(DatagramPacket dp) {
        super(dp);

        ttl = super.readByte(TTL);
        rxLevel = super.readByte(RXLEVEL);
        hstate = (byte) (((super.readByte(HSTATE)) >>> 4) & 0x0f);
        ackMemberCount = ((int) super.readByte(ACKMEM_COUNT)) & 0xff;
        unicastPort = ((int) super.readShort(UCAST_PORT)) & 0xffff;

        memberCount = ((int) (super.readByte(MEMBER_COUNT))) & 0xff;

        int i = super.readInt(SOURCE_ADDR);

        srcAddress = Util.intToInetAddress(i);

        byte fg = (byte) getFlags();
	lowSeqNumber = super.readInt(LOW_SEQ_NUMBER);
        highSeqNumber = super.readInt(HIGH_SEQ_NUMBER);

        if ((fg & FLAGBIT_ACK) != 0) {

            /*
             * Hello message with addresses of members to ACK
             * load the addresses.
             */
            addressList = new InetAddress[ackMemberCount];

            int a = 0;
            short offset = 0;

            for (int j = 0; j < ackMemberCount; j++) {
                InetAddress addr = null;

                offset = (short) (MEMBER_ADDR 
                                  + (((short) j) * V4_ADDR_SIZE));
                a = super.readInt(offset);
                addressList[j] = Util.intToInetAddress(a);
            }
        }
    }

    /**
     * Method to build a TRAMHelloPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     * @param byte - TTL currently in use.
     * @param int - seqnum, the sequence number to be specified in the Hello
     * message.
     * 
     */
    public TRAMHelloPacket(TRAMControlBlock tramblk, byte ttl, 
			   int highseqnum) {
        super(HELLODATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.MCAST_CNTL);
        setSubType(SUBMESGTYPE.HELLO);

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        setAddress(tp.getAddress());
        setPort(tp.getPort());

        this.ttl = ttl;
        srcAddress = tp.getDataSourceAddress();
        rxLevel = (byte) tramblk.getGroupMgmtBlk().getRxLevel();
        hstate = (byte) (tramblk.getGroupMgmtBlk().getHstate());
        unicastPort = tramblk.getUnicastPort();

        try {
            memberCount = tramblk.getGroupMgmtBlk().getDirectMemberCount();
        } catch (NullPointerException ne) {
            memberCount = 0;
        }

        highSeqNumber = highseqnum;
	lowSeqNumber = tramblk.getTRAMDataCache().getLowestSequenceNumber();
        ackMemberCount = 0;
    }

    /**
     * Method to build a TRAMHelloPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     * @param byte - TTL currently in use.
     * @param int - Num of members whose ACK is saught.
     * 
     * @param InetAddress[] - The array of InetAddresses that needs to be
     * included in the message.
     * 
     */
    public TRAMHelloPacket(TRAMControlBlock tramblk, byte ttl, int num_mem, 
                          int highSeqNum, InetAddress[] addrs) {
        super(((HELLODATA) + (num_mem * V4_ADDR_SIZE)), 
              tramblk.getSessionId());

        setMessageType(MESGTYPE.MCAST_CNTL);
        setSubType(SUBMESGTYPE.HELLO);

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        setAddress(tp.getAddress());
        setPort(tp.getPort());

        this.ttl = ttl;
        srcAddress = tp.getDataSourceAddress();
        rxLevel = (byte) tramblk.getGroupMgmtBlk().getRxLevel();
        hstate = (byte) (tramblk.getGroupMgmtBlk().getHstate());
        unicastPort = tramblk.getUnicastPort();

        highSeqNumber = highSeqNum;
	lowSeqNumber = tramblk.getTRAMDataCache().getLowestSequenceNumber();

        try {
            memberCount = tramblk.getGroupMgmtBlk().getDirectMemberCount();
        } catch (NullPointerException ne) {
            memberCount = 0;
        }

        this.ackMemberCount = num_mem & 0xff;
        this.addressList = addrs;
    }

    /**
     * Creates a DatagramPacket based on the information already
     * specified in the internal fields.
     * 
     * @return the built DatagramPacket.
     */
    public DatagramPacket createDatagramPacket() {
        super.writeByte(ttl, TTL);

        byte ch_state = (byte) ((hstate << 4) & 0xf0);

        super.writeByte(ch_state, HSTATE);
        super.writeByte(rxLevel, RXLEVEL);
        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);
        super.writeByte((byte) ackMemberCount, ACKMEM_COUNT);
        super.writeShort((short) unicastPort, UCAST_PORT);
        super.writeByte((byte) memberCount, MEMBER_COUNT);
	super.writeInt(lowSeqNumber, LOW_SEQ_NUMBER);
        super.writeInt(highSeqNumber, HIGH_SEQ_NUMBER);

        int i = MEMBER_ADDR;

        for (int j = 0; j < ackMemberCount; j++) {
            super.writeInt((Util.InetAddressToInt(addressList[j])), i);

            i += V4_ADDR_SIZE;     // go to the next entry.
        }

        // COMPUTE CHECKSUM.

        return super.createDatagramPacket();
    }

    /**
     * gets the TTL stored in the internal field.
     * 
     * @return -byte, the TTL stored in the internal field.
     * 
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * sets the specified ttl value to the internal variable.
     * 
     * @param byte, required ttl value.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * gets the RxLevel stored in the internal field.
     * 
     * @return -byte, the rxLevel stored in the internal field.
     * 
     */
    public byte getRxLevel() {
        return rxLevel;
    }

    /**
     * sets the specified rxLevel value to the internal variable.
     * 
     * @param byte, required rxLevel value.
     */
    public void setRxLevel(byte rxLevel) {
        this.rxLevel = rxLevel;
    }

    /**
     * gets the Hstate stored in the internal field.
     * 
     * @return -byte, the hstate stored in the internal field.
     * 
     */
    public byte getHstate() {
        return hstate;
    }

    /**
     * sets the specified Hstate value to the internal variable.
     * 
     * @param byte, required Head State value.
     */
    public void setHstate(byte hstate) {
        this.hstate = hstate;
    }

    /**
     * gets the Unicast Port stored in the internal field.
     * 
     * @return -int, the  stored Unicast Port in the internal field.
     * 
     */
    public int getUnicastPort() {
        return ((int) unicastPort) & 0xffff;
    }

    /**
     * sets the specified Unicast Port value to the internal variable.
     * 
     * @param unicastPort, required Unicast Port value.
     */
    public void setUnicastPort(int unicastPort) {
        this.unicastPort = (short) unicastPort;
    }

    /**
     * gets the total member count stored in the internal field.
     * 
     * @return the  total number of members supported by this head.
     * 
     */
    public int getMemberCount() {
        return memberCount;
    }

    /**
     * sets the total member count field to the specified value
     * 
     * @param memberCount - required value to initialize the total member
     * count field.
     */
    public void setMemberCount(int memberCount) {
        this.memberCount = (byte) memberCount;
    }

    /**
     * gets the bitmask Length or the ACK member count field
     * of the TRAMHelloPacket.
     * 
     * @return - BitMask Length or ACK Member Count field value.
     */
    public int getAckMemberCount() {
        return ((int) ackMemberCount) & 0xff;
    }

    /**
     * sets the specified value to the bitmask Length or
     * the ACK member count field of the Hello packet.
     * 
     * @param int, value to be assigned to bitmask Length or the
     * ACK member count field.
     */
    public void setAckMemberCount(int ackMemberCount) {
        this.ackMemberCount = (byte) ackMemberCount;
    }

    /**
     * gets the SeqNumber field value that is
     * specified in the internal field.
     * 
     * @return - int, Sequence Number field value.
     */
    public int getLowSeqNumber() {
        return lowSeqNumber;
    }

    /**
     * sets the specified value to the SeqNum field of
     * the Hello packet.
     * 
     * @param int, value to be assigned to  SeqNum field.
     */
    public void setLowSeqNumber(int seqNumber) {
        lowSeqNumber = seqNumber;
    }

    /**
     * gets the SeqNumber field value that is
     * specified in the internal field.
     * 
     * @return - int, Sequence Number field value.
     */
    public int getHighSeqNumber() {
        return highSeqNumber;
    }

    /**
     * sets the specified value to the SeqNum field of
     * the Hello packet.
     * 
     * @param int, value to be assigned to  SeqNum field.
     */
    public void setHighSeqNumber(int seqNumber) {
        highSeqNumber = seqNumber;
    }

    /**
     * gets the Inet address stored in the Source address field.
     * 
     * @return InetAddress - source address of the Hello packet.
     */
    public InetAddress getSrcAddress() {
        return srcAddress;
    }

    /**
     * set the specified address to be the source address of the
     * hello packet.
     * 
     * @param InetAddress - source address to be set.
     */
    public void setSrcAddress(InetAddress address) {
        srcAddress = address;
    }

    /**
     * gets the list of member addresses stored in the Hello Packet.
     * 
     * @return InetAddress[] - List of member addresses.
     */
    public InetAddress[] getAddressList() {
        return addressList;
    }

    /**
     * set specified array of member addresses to be the address list
     * of the Hello Packet.
     * 
     * @param - InetAddress[] - List of member addresses.
     */
    public void setAddressList(InetAddress[] address) {
        addressList = address;
    }

}

