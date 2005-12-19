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
 * TRAMCacheControl.java
 */
package com.sun.multicast.reliable.transport.tram;

/**
 * This is the control block identifying the status of all data packets
 * received. When a data packet is received, an TRAMCacheControl block is 
 * created and added to the cache. A reference to the data packet is kept for
 * retransmitting.
 */
class TRAMCacheControl {
    private TRAMSeqNumber packetNumber;
    private int retransmitCount = 0;
    TRAMDataPacket pk = null;
    private boolean packetDeliveredToApplication = false;

    /*
     * The following three constructors all create a new TRAMCacheControl
     * packet with a sequence number. If a data packet is not supplied,
     * its reference remains null until it is received.
     */

    public TRAMCacheControl(TRAMSeqNumber packetNumber) {
        this.packetNumber = packetNumber;
    }

    public TRAMCacheControl(int packetNumber) {
        this(new TRAMSeqNumber(packetNumber));
    }

    public TRAMCacheControl(TRAMDataPacket pk) {
        this(new TRAMSeqNumber(pk.getSequenceNumber()));

        this.pk = pk;
    }

    /*
     * The following methods are accessor methods for all of the
     * fields contained in this object.
     */

    public TRAMSeqNumber getTRAMSeqNumber() {
        return packetNumber;
    }

    public void setTRAMSeqNumber(TRAMSeqNumber packetNumber) {
        this.packetNumber = packetNumber;
    }

    public int getSequenceNumber() {
        return packetNumber.getSeqNumber();
    }

    public void setSequenceNumber(int value) {
        packetNumber.setSeqNumber(value);
    }

    public int getRetransmitCount() {
        return retransmitCount;
    }

    public void setRetransmitCount(int value) {
        retransmitCount = value;
    }

    public TRAMDataPacket getTRAMDataPacket() {
        return pk;
    }

    public void setTRAMDataPacket(TRAMDataPacket pk) {
        this.pk = pk;
    }

    public void indicatePacketDeliveredToApplication() {
	packetDeliveredToApplication = true;
    }

    public boolean isPacketDeliveredToApplication() {
	return packetDeliveredToApplication;
    }
	
}

