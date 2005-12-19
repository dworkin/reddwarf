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
 * TRAMHelloUniPacket.java
 * 
 * Module Description: Defines the TRAM Unicast Hello Packet class.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMHelloUniPacket extends TRAMPacket {

    /*
     * Flag field Bit Definitions.
     */
    public static final byte FLAGBIT_DISOWNED = (byte) (1 << 0);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);	

    private static final short RXLEVEL = 0;
    private static final short RESERVED = (RXLEVEL + 1);
    private static final short SOURCE_ADDR = (RESERVED + 1);
    private static final short HELLO_UNIDATA = (SOURCE_ADDR + 4);
    private byte rxLevel = 0;
    private InetAddress srcAddress = null;

    /**
     * Decode the various Hello-Uni fields from an incoming datagram packet.
     * 
     * @param -DatagramPacket - received from the net.
     */
    public TRAMHelloUniPacket(DatagramPacket dp) {
        super(dp);

        rxLevel = super.readByte(RXLEVEL);

        int i = super.readInt(SOURCE_ADDR);

        srcAddress = Util.intToInetAddress(i);
    }

    /**
     * Method to build an TRAMHelloUniPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     * @param int - member distance in ms from the data source.
     * 
     */
    public TRAMHelloUniPacket(TRAMControlBlock tramblk) {
        super(HELLO_UNIDATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.HELLO_Uni);

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        srcAddress = tp.getDataSourceAddress();
        rxLevel = (byte) tramblk.getGroupMgmtBlk().getRxLevel();
    }

    /**
     * Creates a DatagramPacket based on the information already
     * specified in the internal fields.
     * 
     * @return the built DatagramPacket.
     */
    public DatagramPacket createDatagramPacket() {
        super.writeByte(rxLevel, RXLEVEL);
        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);

        return super.createDatagramPacket();
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
     * get the source/sender address of the multicast data stream
     * 
     * @return InetAddress- Source address of the multicast data Stream.
     */
    public InetAddress getSrcAddress() {
        return srcAddress;
    }

    /**
     * set the source address of the multicast data stream of the hello packet
     * 
     * @param InetAddress - the required multicast stream data source address.
     */
    public void setSrcAddress(InetAddress address) {
        srcAddress = address;
    }

}

