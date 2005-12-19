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
 * Memberblock.java
 * 
 * Module Description:
 * The class defines the STP Member block. This block
 * is used by those STP's that are performing the
 * role of a head.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;

class MemberBlock {
    private InetAddress address = null;     /* members unicast address */
    private int port = 0;                   /* members unicast port. */
    private byte mrole = MROLE.MEMBER_ONLY;
    private byte cstate = CSTATE.NORMAL;
    private byte hstate = HSTATE.INIT;
    private long lastheard = 0;
    private int dmemCount = 0;
    private int indmemCount = 0;
    private int AdvertmemCount = 0;
    private int memberId;
    private int missedAcks = 0;             /* counter to track ack misses. */
    private long rtt = 0;                   /* computed rtt to the member. */
    private byte ttl = 0;           /* required TTL to reach the member. */  
    private int lastPacketAcked = 0;
    private boolean demandAck = false;
    private int highestSequenceAllowed;
    private int flowControlInfo;	    /* Flow control information */
    private boolean subTreeFlowControlInfo = true; /* info is from below */

    /**
     * Create a member block with the specified address, port, and
     * member id.
     * 
     * @param ia the IP address for the member. This is the address
     * which the member sends control messages on.
     * @param port the IP port number the member sends control
     * messages on
     * @param memberId the id of the member
     */
    public MemberBlock(InetAddress ia, int port, int memberId) {
        address = ia;
        this.port = port;
        this.memberId = memberId;
    }

    /**
     * Create a member block with the specified address and port.
     * 
     * @param ia the IP address for the member. This is the address
     * which the member sends control messages on.
     * @param port the IP port number the member sends control
     * messages on
     */
    public MemberBlock(InetAddress ia, int port) {
        address = ia;
        this.port = port;
    }

    /**
     * @return the IP address for this member.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @return the IP port for this member.
     */
    public int getPort() {
        return port;
    }

    /**
     * gets the Mrole field value.
     * 
     * @return byte - mrole value.
     */
    public byte getMrole() {
        return mrole;
    }

    /**
     * gets the Hstate field value.
     * 
     * @return byte - hstate value.
     */
    public int getHstate() {
        return ((int) (hstate)) & 0xff;
    }

    /**
     * gets the Data Cache usage state field value.
     * 
     * @return byte - cstate value.
     */
    public byte getCstate() {
        return cstate;
    }

    /**
     * gets the last heard member timestamp.
     * 
     * @return long - last heard timestamp (in millisecs).
     */
    public long getLastheard() {
        return lastheard;
    }

    /**
     * gets the direct member count.
     * 
     * @return int - the direct member count.
     * 
     */
    public int getDmemCount() {
        return dmemCount;
    }

    /**
     * gets the advertising member count.
     * 
     * @return int - the advertising member count.
     * 
     */
    public int getAdvertmemCount() {
        return AdvertmemCount;
    }

    /**
     * gets the Indirect member count value.
     * 
     * @return - int - The indirect member count.
     */
    public int getIndmemCount() {
        return indmemCount;
    }

    /**
     * @return the memberId of this member.
     */
    public int getMemberId() {
        return memberId;
    }

    /**
     * gets the Round trip time/latency to the member.
     * 
     * @return long - the round trip time/latency(in millisecs) to the
     * memeber.
     */
    public long getRTT() {
        return rtt;
    }

    /**
     * gets the TTL value to be used to reach the member.
     * 
     * @return byte - the TTL value required to reach the member.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * @return the last packet that this member has acked. All packets
     * with lower sequence numbers have been received.
     */
    public int getLastPacketAcked() {
        return lastPacketAcked;
    }

    /**
     * Set the IP address for this member. This is the address used
     * for all control messages during this session. It identifies
     * the member.
     * 
     * @param address the IP address of this member.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * Set the IP port number for this member. It is used with the IP
     * address to identify the member.
     * 
     * @param port the port number for control messages from this member.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * set the Mrole field for the member.
     * 
     * @param byte - mrole value. see MROLE.java for valid mrole values.
     */
    public void setMrole(byte mrole) {
        this.mrole = mrole;
    }

    /**
     * set the Data Cache usage field for the member.
     * 
     * @param byte - the cstate value. see CSTATE.java for valid values.
     * 
     */
    public void setCstate(byte cstate) {
        this.cstate = cstate;
    }

    /**
     * set the Hstate for the member.
     * 
     * @param int - the hstate value. see HSTATE.java for valid values.
     * 
     */
    public void setHstate(int hstate) {
        this.hstate = (byte) (hstate & 0xff);
    }

    /**
     * set the last heard timestamp value for the member.
     * 
     * @param long - lastheard timestamp value in millisecs.
     */
    public void setLastheard(long lastheard) {
        this.lastheard = lastheard;
    }

    /**
     * set the direct member count to the member.
     * 
     * @param -int the direct member count.
     */
    public void setDmemCount(int dmemCount) {
        this.dmemCount = dmemCount;
    }

    /**
     * set the advertising member count of the member.
     * 
     * @param -int the advertising member count.
     */
    public void setAdvertmemCount(int AdvertmemCount) {
        this.AdvertmemCount = AdvertmemCount;
    }

    /**
     * set the Indirect member count for the member.
     * 
     * @param int indirect member count.
     */
    public void setIndmemCount(int indmemCount) {
        this.indmemCount = indmemCount;
    }

    /**
     * Set the member Id. This ID is used internally to identify
     * the member.
     * 
     * @param memberId a unique member id for this member in the
     * group.
     */
    public void setMemberId(int memberId) {
        this.memberId = memberId;
    }

    /**
     * set round trip time value to the member.
     * @param long - rtt value in millisecs.
     */
    public void setRTT(long rtt) {
        this.rtt = rtt;
    }

    /**
     * set the TTL value required to reach the member.
     * 
     * @param byte - the ttl value to reach the member.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * Set the sequence number of the last packet acked. All packets
     * prior to this packet have been received.
     * 
     * @param value the sequence number of the last packet acked.
     */
    public void setLastPacketAcked(int value) {
        lastPacketAcked = value;
    }

    /**
     * Test to check if the member address needs to be listed in the
     * multicast Hello message.
     * 
     * @return true if an ACK needs to be demanded from the member.
     * false if no ACK needs to  be demanded from the member.
     */
    public synchronized boolean getDemandAck() {
        return demandAck;
    }

    /**
     * set the demand Ack flag.
     * @param boolean true, to demand an ACK, false to demand no ack.
     * 
     */
    public synchronized void setDemandAck(boolean demandAck) {
        this.demandAck = demandAck;
    }

    /**
     * returns the number of ACKs the member has failed to send.
     * The current theory is that th emember should have ack'd
     * atleast once in an Hello interval.
     * If a member is found to have unacknowledged for more than
     * 5 Hello intervals, the member is disowned by the head.
     * 
     * @return the count of ACKs the member has missed.
     */
    public int getMissedAcks() {
        return missedAcks;
    }

    /**
     * clears the missedAcks counter. Typically done when an ACK is
     * received from the member.
     * 
     */
    public synchronized void clearMissedAcks() {
        missedAcks = 0;
    }

    /**
     * increments the missedAcks counter. Typically invoked when an Hello
     * interval passes without receiving an ACK from the member.
     * 
     * @return the incremented value of missedAcks counter.
     */
    public synchronized int incrMissedAcks() {
        missedAcks++;

        return missedAcks;
    }

    /*
     * Set the highest sequence number that this member wants the sender 
     * to send.
     */
    public void setHighestSequenceAllowed(int highestSequenceAllowed) {
	if (highestSequenceAllowed > this.highestSequenceAllowed)
	    this.highestSequenceAllowed = highestSequenceAllowed;
    }

    /*
     * Get the highest sequence number that this member wants the sender 
     * to send.
     */
    public int getHighestSequenceAllowed() {
	return highestSequenceAllowed;
    }

    /*
     * Set flow control information.
     */
    public void setFlowControlInfo(int flowControlInfo) {
	this.flowControlInfo = flowControlInfo;
    }

    /*
     * Get flow control information.
     */
    public int getFlowControlInfo() {
	return flowControlInfo;
    }

    /*
     * Set flag to indicate whether or not flow control information is
     * for this member or for a child of the member.
     */
    public void setSubTreeFlowControlInfo(boolean subTreeFlowControlInfo) {
	this.subTreeFlowControlInfo = subTreeFlowControlInfo;
    }

    /*
     * Get flag which indicates whether or not flow control information is
     * for this member or for a child of the member.
     */
    public boolean getSubTreeFlowControlInfo() {
	return subTreeFlowControlInfo;
    }

}
