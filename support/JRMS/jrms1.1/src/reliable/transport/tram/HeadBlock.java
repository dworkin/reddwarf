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
 * HeadBlock.java
 * 
 * Module Description:
 * The class defines the TRAM Head block. This block
 * is used by those TRAM's that are performing the
 * role of a head.
 */
package com.sun.multicast.reliable.transport.tram;

import java.net.*;

class HeadBlock {
    private InetAddress address = null;     /* heads unicast address */
    private int port = 0;                   /* heads unicast port. */
    private byte hstate = HSTATE.INIT;
    private byte cstate = CSTATE.NORMAL;
    private byte lstate = LSTATE.NA;
    private long lastheard = 0;
    private int directMemberCount = 0;
    private int indirectMemberCount = 0;
    private byte ttl = 0;             /* required TTL to reach the head. */
    private int missedHellos = 0;
    TRAMSeqNumber startSeqNumber = null;
    private int rxLevel = 0;

    /*
     * level 0 implies unknown
     * RxLevel, level 1 is
     * sender level and levels
     * 2 thru 255 are valid member
     * levels.
     */
    private boolean rootLanHead = false;

    /**
     * Create a head block with the specified address, port, and
     * head id.
     * 
     * @param ia the IP address for the head. This is the address
     * which the head sends control messages on.
     * @param port the IP port number the head sends control
     * messages on
     * @param headId the id of the head
     */
    public HeadBlock(InetAddress ia, int port) {
        address = ia;
        this.port = port;
    }

    /**
     * @return the IP address for this head.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @return the IP port for this head.
     */
    public int getPort() {
        return port;
    }

    /**
     * gets the Hstate field value.
     * 
     * @return byte - hstate value.
     */
    public byte getHstate() {
        return hstate;
    }

    /**
     * gets the Lstate field value.
     * 
     * @return byte - lstate value.
     */
    public byte getLstate() {
        return lstate;
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
     * gets the last heard head timestamp.
     * 
     * @return long - last heard timestamp (in millisecs).
     */
    public synchronized long getLastheard() {
        return lastheard;
    }

    /**
     * gets the direct head count.
     * 
     * @return int - the direct member count.
     * 
     */
    public int getDirectMemberCount() {
        return directMemberCount;
    }

    /**
     * gets the Indirect member count value.
     * 
     * @return - int - The indirect member count.
     */
    public int getIndirectMemberCount() {
        return indirectMemberCount;
    }

    /**
     * gets the TTL value to be used to reach the head.
     * 
     * @return byte - the TTL value required to reach the head.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * gets the RxLevel of the head.
     * 
     * @return int - the RxLevel ofthe head.
     */
    public int getRxLevel() {
        return rxLevel;
    }

    /**
     * gets the missedHellos counter of the head.
     * 
     * @return int - the count of missedHellos for this head.
     */
    public int getMissedHellos() {
        return missedHellos;
    }

    /**
     * increments the missedHello counter and returns the updated missed Hello
     * counter value.
     * 
     * @return int - the count of missedHellos(post incremented value).
     */
    public int incrAndGetMissedHellos() {
        missedHellos++;

        return missedHellos;
    }

    /**
     * Set the IP address of the head. This is the address used
     * for all control messages during this session. It identifies
     * the head.
     * 
     * @param address the IP address of this head.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * Set the IP port number for this head. It is used with the IP
     * address to identify the head.
     * 
     * @param port the port number for control messages from this head.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * set the Hstate field for the head.
     * 
     * @param byte - hstate value. see HSTATE.java for valid hstate values.
     */
    public void setHstate(byte hstate) {
        this.hstate = hstate;
    }

    /**
     * set the Lstate field for the head.
     * 
     * @param byte - lstate value. see LSTATE.java for valid lstate values.
     */
    public void setLstate(byte lstate) {
        this.lstate = lstate;
    }

    /**
     * set the Data Cache usage field for the head.
     * 
     * @param byte - the cstate value. see CSTATE.java for valid values.
     * 
     */
    public void setCstate(byte cstate) {
        this.cstate = cstate;
    }

    /**
     * set the last heard timestamp value for the head.
     * 
     * @param long - lastheard timestamp value in millisecs.
     */
    public synchronized void setLastheard(long lastheard) {
        this.lastheard = lastheard;
    }

    /**
     * set the direct head count to the member.
     * 
     * @param -int the direct member count.
     */
    public void setDirectMemberCount(int directMemberCount) {
        this.directMemberCount = directMemberCount;
    }

    /**
     * set the Indirect member count for the member.
     * 
     * @param int indirect member count.
     */
    public void setIndirectMemberCount(int indirectMemberCount) {
        this.indirectMemberCount = indirectMemberCount;
    }

    /**
     * set the TTL value required to reach the head.
     * 
     * @param byte - the ttl value to reach the head.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * sets the RxLevel of the head.
     * 
     * @param rxLevel the RxLevel ofthe head.
     */
    public void setRxLevel(int rxLevel) {
        this.rxLevel = rxLevel;
    }

    /**
     * clears the missedHellos counter.
     * 
     */
    public void clearMissedHellos() {
        missedHellos = 0;
    }

    public int getStartSeqNumber() {
        return startSeqNumber.getSeqNumber();
    }

    public void setStartSeqNumber(int startSeqNumber) {
        this.startSeqNumber = new TRAMSeqNumber(startSeqNumber);
    }

    public boolean isRootLanHead() {
        return rootLanHead;
    }

    public void setRootLanHead(boolean rootLanHead) {
        this.rootLanHead = rootLanHead;
    }

}

