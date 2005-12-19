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
 * TRAMCongestionPacket.java
 * 
 * Module Description:
 * 
 * This module defines the TRAM Congestion packet format. This
 * packet notifies the head that the member is experiencing
 * congestion.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.InetAddress;
import java.net.DatagramPacket;
import com.sun.multicast.util.Util;

/**
 * This class is the definition of the TRAMCongestionPacket. It is
 * derived from the TRAMPacket class.
 */
class TRAMCongestionPacket extends TRAMPacket {

    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_SUBTREE_FLOW_CONTROL_INFO = 
	(byte) (1 << 0);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    private static final int RESERVED = 0;
    private static final int SEQUENCENUMBER = RESERVED + 2;
    private static final int FLOW_CONTROL_INFO = SEQUENCENUMBER + 4;
    private static final int DATA_RATE = FLOW_CONTROL_INFO + 4;
    private static final int SOURCE_ADDR = DATA_RATE + 4;
    private static final int HEADER = SOURCE_ADDR  + 4; // 4  is a valid V4
							// address length.
							// Needs to be changed
							// when Java supports
							// V6.
							//
    private int sequenceNumber;
    private int flowControlInfo;
    private int dataRate;
    private InetAddress srcAddress = null;

    /**
     */
    public TRAMCongestionPacket(DatagramPacket dp) {

        /*
         * The parent constructor is called to retrieve the data buffer
         * and load the TRAM header fields.
         */
        super(dp);
	int i = super.readInt(SOURCE_ADDR);
	srcAddress = Util.intToInetAddress(i);
        setSequenceNumber(super.readInt(SEQUENCENUMBER));
	flowControlInfo = super.readInt(FLOW_CONTROL_INFO);
	dataRate = super.readInt(DATA_RATE);
    }

    /**
     * Create a congestion packet for transmission. Set the target address
     * and port.
     * 
     * @param ia the IP address of the head.
     * @param port the IP port number of the head.
     */
    public TRAMCongestionPacket(TRAMControlBlock tramblk, InetAddress ia, 
                               int port, int sequenceNumber) {
        super(HEADER, tramblk.getSessionId());

        setAddress(ia);
        setPort(port);
        setSequenceNumber(sequenceNumber);
	srcAddress = tramblk.getTransportProfile().getDataSourceAddress();
        setMessageType(MESGTYPE.UCAST_CNTL);
        setSubType(SUBMESGTYPE.CONGESTION);
    }

    /**
     * Create a DatagramPacket from the existing data in this class.
     * 
     * @return a DatagramPacket with the current TRAMDataPacket contents.
     */
    public DatagramPacket createDatagramPacket() {
	super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);
        super.writeInt(sequenceNumber, SEQUENCENUMBER);
        super.writeInt(flowControlInfo, FLOW_CONTROL_INFO);
        super.writeInt(dataRate, DATA_RATE);
        super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);
        return super.createDatagramPacket();
    }

    /**
     * Get the packet sequence where congestion was detected.
     * 
     * @return the congestion sequence number.
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Set the sequence number where congestion was detected.
     * 
     * @param the sequence number where congestion was detected.
     */
    public void setSequenceNumber(int value) {
        sequenceNumber = value;
    }

    /**
     * Set flow control information
     *
     * @param the flow control information
     */
    public void setFlowControlInfo(int flowControlInfo) {
	this.flowControlInfo = flowControlInfo;
    }

    /**
     * Get flow control information
     *
     * @return the flow control information
     */
    public int getFlowControlInfo() {
	return flowControlInfo;
    }

    /**
     * Set data rate
     *
     * @param the current data rate
     */
    public void setDataRate(int dataRate) {
	this.dataRate = dataRate;
    }

    /**
     * Get data rate
     *
     * @return the current data rate
     */
    public int getDataRate() {
	return dataRate;
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

}
