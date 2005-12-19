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
 * TRAMRmPacket.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;
import com.sun.multicast.util.Util;

class TRAMRmPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    /*
     * Packet Offset definitions.
     */
    private static final byte REASON_CODE = 0;
    private static final byte RESERVED = (REASON_CODE + 1);
    private static final byte SOURCE_ADDR = (RESERVED + 1);
    private static final byte RMDATA = (SOURCE_ADDR + 4); // 4 - is valid
							  // only for V4 
							  // addresses...
							  // need to change
							  // when java supports
							  // V6. Need to revist
							  // when Java supports
							  // IPV6.
							  //

    /*
     * Reason Codes
     */
    public static final int RCODE_ACCEPTING_POTENTIAL_HEADS = 1;
    public static final int RCODE_MEMBERSHIP_FULL = 2;
    public static final int RCODE_TTL_OUT_OF_LIMITS = 3;
    public static final int RCODE_RESIGNING = 4;
    public static final int RCODE_NOT_A_LANHEAD = 5;

    /*
     * internal variables
     */
    private byte reasonCode = 0;
    private InetAddress srcAddress = null;

    /**
     * Decode the various RM fields from an incoming datagram packet.
     * 
     * @param received datagram packet.
     */
    public TRAMRmPacket(DatagramPacket dp) {
        super(dp);

        int i = super.readInt(SOURCE_ADDR);

        srcAddress = Util.intToInetAddress(i);
        reasonCode = super.readByte(REASON_CODE);
    }

    /**
     * Method to build an TRAMRmPacket to transmit.
     * 
     * @param tramblk - TRAMControlBlock - typically required if some 
     * information from the control block needs to be included in the
     * message.
     */
    public TRAMRmPacket(TRAMControlBlock tramblk, int reasonCode) {
        super(RMDATA, tramblk.getSessionId());

        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.RM);

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        srcAddress = tp.getDataSourceAddress();
        this.reasonCode = (byte) reasonCode;
    }

    /**
     * Creates a DatagramPacket based on the information already
     * specified in the internal fields.
     * 
     * @return the build Datagram packet.
     * 
     */
    public DatagramPacket createDatagramPacket() {
        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);
        super.writeByte(reasonCode, REASON_CODE);

        return super.createDatagramPacket();
    }

    /**
     * gets the address of the multicast Data stream that is
     * specified in the internal field.
     * 
     * @return - InetAddress of the multicast data source.
     */
    public InetAddress getSrcAddress() {
        return srcAddress;
    }

    /**
     * sets the specified address as the souce of the multicast data
     * stream in the internal field.
     * 
     * @param -InetAddress - Address of the souce of the multicast
     * data stream.
     */
    public void setSrcAddress(InetAddress address) {
        srcAddress = address;
    }

    /**
     * gets the RM message ReasonCode from the internal variable.
     * 
     * @return - int, the reason code of the RM message.
     */
    public int getReasonCode() {
        return ((int) reasonCode) & 0xff;
    }

    /**
     * sets the specified reasoncode for the RM packet.
     * 
     * @param -int, the reasoncode of the RM packet.
     */
    public void setReasonCode(int reasonCode) {
        this.reasonCode = (byte) reasonCode;
    }

}

