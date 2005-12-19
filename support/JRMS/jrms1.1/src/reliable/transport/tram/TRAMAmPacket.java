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
 * TRAMAmPacket.java
 * 
 * Module Description: defines the AM(Accept Message) packet class
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMAmPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Packet Offset definitions.
     */
    private static final int BITMASKLENGTH = 0;
    private static final int RXLEVEL = BITMASKLENGTH + 1;
    private static final int START_SEQ_NUMBER = RXLEVEL + 1;
    private static final int SOURCE_ADDR =  START_SEQ_NUMBER + 4;
    private static final int AMDATA = SOURCE_ADDR + 4; // 4 bytes is
						       // applicable for
						       // V4 ONLY. Need to
						       // revist when Java
                                                       // supports IPV6.
						       //
    private static final int BITMASK = AMDATA;

    /*
     * internal variables
     */
    private byte rxLevel;
    private InetAddress dataSrcAddress = null;
    private int startSeqNumber;

    /**
     * Decodes the various AM fields from an incoming datagram packet.
     * 
     * @param DatagramPacket - the received packet that needs to be
     * decoded.
     */
    public TRAMAmPacket(DatagramPacket dp) {
        super(dp);

        rxLevel = super.readByte(RXLEVEL);

        int i = super.readInt(SOURCE_ADDR);
        dataSrcAddress = Util.intToInetAddress(i);
        startSeqNumber = super.readInt(START_SEQ_NUMBER);
    }

    /**
     * Method to build an TRAMAmPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     */
    public TRAMAmPacket(TRAMControlBlock tramblk) {
        super(AMDATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.AM);

        rxLevel = (byte) tramblk.getGroupMgmtBlk().getRxLevel();

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        dataSrcAddress = tp.getDataSourceAddress();
    }

    /**
     * Creates a DatagramPacket based on the information already
     * specified in the internal fields.
     * 
     * @return the build Datagram packet.
     */
    public DatagramPacket createDatagramPacket() {
        super.writeByte(rxLevel, RXLEVEL);
        super.writeInt(Util.InetAddressToInt(dataSrcAddress), SOURCE_ADDR);
        super.writeInt(startSeqNumber, START_SEQ_NUMBER);

        return super.createDatagramPacket();
    }

    /**
     * gets the source address of the multicast Data stream that is
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
     * gets the start sequence number specified in the AM packet
     * 
     * 
     * @return - the start sequence number indicated in the AM packet.
     */
    public int getStartSeqNumber() {
        return startSeqNumber;
    }

    /**
     * sets the specified seq number in the internal field.
     * 
     * @param - seqNumber - the required start seq number to be
     * specified in the AM packet.
     */
    public void setStartSeqNumber(int seqNumber) {
        startSeqNumber = seqNumber;
    }

    public byte getRxLevel() {
        return rxLevel;
    }

}

