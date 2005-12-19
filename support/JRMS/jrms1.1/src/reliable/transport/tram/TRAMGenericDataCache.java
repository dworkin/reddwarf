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
 * TRAMGenericDataCache.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.*;
import java.io.*;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.SessionDownException;

class TRAMGenericDataCache implements TRAMDataCache, TRAMMembershipListener {
    public int cacheSize;
    public int lowWaterMark;
    public int highWaterMark;
    public int defaultHighWaterMark;
    private TRAMVector dataCache;
    private TRAMControlBlock tramblk;
    private TRAMTransportProfile tp;
    private TRAMSeqNumber nextPacket;
    private TRAMLogger logger;
    private Vector dataPacketListeners;
    private boolean dataEnd = false;
    private boolean lastPacketReceived = false;
    private int lastSequenceNumber;
    private TRAMPacketHandler packetHandler;
    /* Late join parameters... */

    private boolean init = false;
    private Vector holdingTank;
    private int lowestKnownMissingPacket = 1;
//    private boolean processedMembershipEvent=false;
    /*
     * Constructor for the Generic data cache. "Generic" means
     * that packets are handed up to the data packet listeners in
     * the order that they arrave. Duplicates are discarded.
     */

    public TRAMGenericDataCache(TRAMControlBlock tramblk) {
        dataCache = new TRAMVector();
        this.tramblk = tramblk;
        this.tp = tramblk.getTransportProfile();

	cacheSize = tp.getCacheSize();
	lowWaterMark = cacheSize / 3;
	defaultHighWaterMark = (cacheSize * 2) / 3;
	highWaterMark = defaultHighWaterMark;
	
        logger = tramblk.getLogger();
        if (tp.isOrdered())
            packetHandler = new OrderedPacketHandler(tramblk, this, 1);
        else
            packetHandler = new UnorderedPacketHandler(tramblk, this, 1);
        /*
         * Only create the holding tank for receivers that have requested
         * late join with limited recovery. The holding tank is used for
         * temporary storage of packets prior to becoming a member.
         */
        byte tmode = tramblk.getTransportProfile().getTmode();

        if (tmode != (byte) TMODE.SEND_ONLY) {
            init = true;
	    holdingTank = null;
	    switch (tp.getLateJoinPreference()) {
		
	        case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
	        case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_DATACACHE)) {

                        logger.putPacketln(this, "Creating holding tank");
		    }
		    holdingTank = new Vector();
		    break;

	        case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_DATACACHE)) {

		        logger.putPacketln(this, "No holding tank necessary");
		    }
		    break;

		default:
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		        logger.putPacketln(this, 
			    "The Late Join Preference is Invalid!");
		    }
		    break;
		}
        }
        this.tramblk.getInputDispThread().addTRAMDataPacketListener(this);
        this.tramblk.getInputDispThread().addBeaconPacketListener(this);
        this.tramblk.getGroupMgmtThread().addTRAMMembershipListener(this);
        nextPacket = new TRAMSeqNumber();
        dataPacketListeners = new Vector();
    }

    /**
     * Unsupported.
     */
    public TRAMPacket getPacket() {
        return null;
    }

    /**
     * Get the first TRAMPacket from the object. The packet is
     * removed from the object. If the database is empty, the
     * method throws NoSuchElementException.
     * 
     * @Exception NoSuchElementException when the input queue is
     * empty.
     * 
     * @return The TRAMPacket that is returned.
     * 
     */
    public TRAMPacket getPacketWithNoBlocking() throws NoSuchElementException {
        throw new NoSuchElementException("Unsupported Method");
    }

    /**
     * Returns the element with the specified sequenceNumber in
     * the database.
     * 
     * @param sequenceNumber the tram sequence number of the TRAMDataPacket
     * @return the TRAMDataPacket in the database with the specified
     * sequenceNumber
     * @exception NoSuchElementExcetion if desired packet is not in the
     * databse.
     */
    public TRAMDataPacket getPacket(long sequenceNumber) 
            throws NoSuchElementException {
        TRAMCacheControl ac = getControlPacket((int) sequenceNumber);
        TRAMDataPacket pk = ac.getTRAMDataPacket();

        if (pk != null) {
            return pk;
        } else {
            throw new NoSuchElementException("Packet not received yet");
        }
    }

    /**
     * Add an TRAMPacket to the database. If a thread is waiting
     * for the packet, resume the suspended thread.
     * 
     * @param tramPacket the TRAMPacket to add
     */
    public void putPacket(TRAMPacket tramPacket) {

    /* Not supported */

    }

    /**
     * Add an TRAMPacket to the begining of the database. If a thread is waiting
     * for the packet, resume the suspended thread.
     * 
     * @param tramPacket the TRAMPacket to add
     */
    public void putPacket(TRAMPacket tramPacket, boolean priority) {

    /* Not Supported */

    }

    /**
     * Removes a TRAMDataPacket from the database. The datasize of the
     * database is decremented by the length of the TRAMDataPacket. If
     * matching TRAMDataPacket is not found, the method just returns.
     * 
     * @param	TRAMDataPacket sequence number.
     */
    public void removePacket(long sequenceNumber) 
            throws NoSuchElementException {

    /* Not Supported */

    }

    /**
     * Gets the current datasize of the database. This information is
     * used by TRAM modules like the congestion control which monitor
     * the database size.
     * 
     * @return current datasize of the database.
     */

    /**
     * @return the number of packets currently in the database.
     */
    public int getPacketCount() {
        return dataCache.size();
    }

    /**
     * This method is the TRAMDataPacketListener interface. The input
     * dispatcher calls this method when a data packet is received.
     * This method creates a new TRAMCacheControl object representing the
     * packet and places it on the dataCache. If an out of
     * sequence packet is seen, all TRAMCacheControl objects are created
     * for each of the missing packets and their received flag is
     * set to false. When a missing packet is received, the TRAMCacheControl
     * object is retrieved and the received flag is set.
     * 
     * @param e an TRAMDataPacketEvent.
     */
    public synchronized void receiveDataPacket(TRAMDataPacketEvent e) {
        /*
         * The following code handles late joining members. There are three
         * options for late joining members:
         * 
         * limited recovery: Will attempt to recover all packets that the
         * head currently has promised in the AM message.
         * no recovery:      Start receiving when membership is obtained.
         * All packets prior to this are dropped.
         * Full recovery:    Unsupported.
         * 
         * For limited recovery, packets are stuffed into a holding tank until
         * this member gains membership with the TRAM tree. At that point,
         * the first expected packet is set to the lowest that the head has
         * in its cache. The packets already received in the holding tank
         * are then processed. Packets prior to the heads lowest packet
         * are dropped and all others are processed normally. Limited recovery
         * is assumed if there is a holding tank available. The holding tank
         * is not created for senders or other late join options.
         * 
         * For members requesting no recovery, the first packet expected is
         * set to the first data packet received after membership is obtained.
         */
        if (init) {
            if (holdingTank != null) {
                holdingTank.addElement(e);
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

                    logger.putPacketln(this, 
                        "Loading another packet into the holding tank");
		}
            } 
            return;
        }

        TRAMDataPacket pk = (TRAMDataPacket) e.getPacket();
        TRAMCacheControl ac;
        int sequenceNumber = pk.getSequenceNumber();

	/*
	 * If the cache is full because of a missing packet at the 
	 * beginning of the cache, and we don't have a repair head,
	 * we can drop this packet and recover it later when we
	 * find a repair head.
	 */
	if (tramblk.isCacheFull() && 
	    tramblk.getGroupMgmtBlk().getHeadBlock() == null) {

	    try {
		TRAMCacheControl cc = (TRAMCacheControl)dataCache.elementAt(0);

		if (cc.getTRAMDataPacket() == null) {
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            	        logger.putPacketln(this, 
			    "Cache full and I have no head.  Dropping packet " +
			    sequenceNumber);
		    }
	            return;
		}
	    } catch (NoSuchElementException ne) {}
	}

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, 
	        "Packet " + sequenceNumber + " from " + 
		pk.getSourceAddress() +
		"(expected " + nextPacket.getSeqNumber() + ")");
	}


        /*
         * For each packet between the expected next packet and
         * the actual packet we just received, create a TRAMCacheControl
         * object and place it on the dataCache. Set the
         * received flag to false indicating that we haven't received
         * this packet yet.
         */
        while (nextPacket.isLessThan(sequenceNumber)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                logger.putPacketln(this, 
		    "Creating cache control for missing packet " +
		    nextPacket.getSeqNumber());
	    }
 
            ac = new TRAMCacheControl(nextPacket.getSeqNumber());

	    byte tmode = tramblk.getTransportProfile().getTmode();

            if (tmode == (byte) TMODE.SEND_ONLY)
		ac.indicatePacketDeliveredToApplication();

	    dataCache.addElement(ac);
            nextPacket.incrSeqNumber();
        }


        /*
         * If the sequenceNumber of the current packet was greater than or
         * equal to the expected nextPacket, then nextPacket = sequenceNumber
         * at this point.  
         * 
         * If the sequenceNumber was prior to the expected next packet,
         * assume it is a packet we previously determined to be missing.
         * Get the TRAMCacheControl object representing this packet and set the
         * received flag to true.
         * 
         * If the packet was already received, assume it is a spurious
         * retransmission request and ignore this packet.
         */
        if (nextPacket.isEqualTo(sequenceNumber)) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
                    "Cache EXPECTED Data packet " + sequenceNumber +
		    " from " + pk.getSourceAddress());
	    }

	    ac = new TRAMCacheControl(pk);

	    byte tmode = tramblk.getTransportProfile().getTmode();

            if (tmode == (byte) TMODE.SEND_ONLY)
		ac.indicatePacketDeliveredToApplication();

            dataCache.addElement(ac);
            nextPacket.incrSeqNumber();
            packetHandler.newPacket(pk);
	    updateLowestKnownMissingPacketNumber();
        } else if (nextPacket.isGreaterThan(sequenceNumber)) {
	    /*
	     * We've received an out-of-sequence packet.
	     */
            try {
                TRAMCacheControl cpk = getControlPacket(sequenceNumber);
                TRAMDataPacket pkt;

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this, 
			"Out-of-sequence packet " + sequenceNumber);
		}

                if (((pkt = cpk.getTRAMDataPacket()) == null) || 
		    ((pkt.getFlags() & TRAMDataPacket.FLAGBIT_UNRECOVERABLE) != 
		    0)) {

                    cpk.setTRAMDataPacket(pk);
                    packetHandler.newPacket(pk);

		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                        logger.putPacketln(this, 
                            "Cache MISSING Data packet " + 
        		    sequenceNumber + " (expected " + 
			    nextPacket.getSeqNumber() + ")" +
			    " from " + pk.getSourceAddress());
		    }
		    updateLowestKnownMissingPacketNumber();
                } else {
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                            logger.putPacketln(this, 
		                "Out-of-sequence packet " + sequenceNumber +
			    " is already in the cache.  flags " + 
			    pkt.getFlags());
		    }
		}
            } catch (NoSuchElementException ne) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
                    logger.putPacketln(this, 
			"Out-of-sequence packet " + sequenceNumber +
			".  There is no TRAMCacheControl for this sequence!  " +
			"Why not?");
		}
	    }
        }

        /*
         * Check to see if we need to purge the cache. If after purging we
         * weren't able to reduce the size below the highWaterMark, send
         * a congestion message up the tree. Increase the highWaterMark
         * to the next ack window boundary.
         * 
         * If we were able to reduce the size of the cache in this round,
         * and it is below the default HIGHWATERMARK, set the new highWaterMark
         * to the default value.
         */
        if (dataCache.size() >= highWaterMark) {
            int size = purgeCache(lowWaterMark);

            if (size >= highWaterMark) {
                if (findAndRemoveBadMembers())
                    size = purgeCache(lowWaterMark);    // try again to purge

                if (size >= highWaterMark) {
                    // tramblk.getTRAMCongestion().sendCongestion(
		    //    nextPacket.getSeqNumber());

                    highWaterMark += 
                        tramblk.getTransportProfile().getAckWindow();

		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
			TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

                        logger.putPacketln(this, 
			    "cacheSize " + cacheSize + 
			    ", lowWaterMark " + lowWaterMark +
			    ", highWaterMark " + highWaterMark);

                        logger.putPacketln(this, 
                            "Increasing cache high water mark to " + 
			    highWaterMark);
		    }	

		    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                        logger.putPacketln(this, 
			    "Congestion!  New high water mark is " + 
			    highWaterMark);
		    }
                }
            }
            if (size < defaultHighWaterMark) {
                highWaterMark = defaultHighWaterMark;
            } 
        }
    }

    /*
     * Method to keep track of the lowest missing packet sequence number.
     * keeping track of this helps in quickly discovering if we have a
     * packet or not. Currently this tracking helps the quick processing
     * of a dealWithUnrecoverable packets routine.
     */
    private synchronized void updateLowestKnownMissingPacketNumber() {

	if (nextPacket.isEqualTo(lowestKnownMissingPacket))
	    return;

	if (nextPacket.isGreaterThan(lowestKnownMissingPacket)) {
	    for (int i = lowestKnownMissingPacket; 
		 nextPacket.isGreaterThan(i); i++) {
		TRAMCacheControl cpk;
		try {
		    cpk = getControlPacket(i);
		} catch (NoSuchElementException ne) {
		    lowestKnownMissingPacket = i + 1;
		    continue;
		}
		if (cpk.getTRAMDataPacket() == null) {
		    lowestKnownMissingPacket = i;
		    return;
		}
	    }
	    /* comes here if no packets are missing till nextPacket */
	    lowestKnownMissingPacket = nextPacket.getSeqNumber();
	} else {
	    /* 
	     * comes here if lowestKnownMissingPacket is greater than
	     * the nextPacket!!! An error condition.
	     */
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this,
		    "lowestKnownMissingPacket seqNumber is > " +
		    "nextPkt to receive!! " + 
		    "lowest is " + lowestKnownMissingPacket +
		    " Next to receive is " + nextPacket.getSeqNumber());
	    }
	    lowestKnownMissingPacket = nextPacket.getSeqNumber();
	}

    }
    /*
     * method to get the lowestknown missing packet number.
     * A separate method so as to not to worry about the monitoring aspects.
     */
    private synchronized int getLowestKnownMissingPacketNumber() {
	return lowestKnownMissingPacket;
    }

    /*
     * The number of elements in the cache has reached the high water mark.
     * Try to find any slow (or non-responsive) members and remove
     * them from our member list so the cache can be flushed.
     */

    private boolean findAndRemoveBadMembers() {

        /* 
	 * Get the lowest sequence number we have in the cache
	 */ 

        int lowestSequenceNumber = getLowestSequenceNumber();

        /* 
	 * Find the member with the lowest value for LastPacketAcked
	 * and disown him as a member.
	 */ 

        GroupMgmtBlk mgmtBlk = tramblk.getGroupMgmtBlk();
        MemberBlock mbDisown = null;
        int iDisown = 0;

        for (int i = 0; i < mgmtBlk.getDirectMemberCount(); i++) {
            try {
                MemberBlock mb;

                mb = mgmtBlk.getMember(i);      // next member

                /*
                 * Only disown members who haven't ack'd up to the lowest seq #
                 */
                if (mb.getLastPacketAcked() < lowestSequenceNumber) {
                    if (mbDisown != null) {
                        if (mbDisown.getLastPacketAcked() 
                                < mb.getLastPacketAcked()) {
                            mbDisown = mb;
                            iDisown = i;
                        }
                    } else {
                        mbDisown = mb;
                        iDisown = i;
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }

        if (mbDisown != null) {
            tramblk.getGroupMgmtThread().handleMemberLoss(mbDisown);
	    tramblk.getTRAMStats().addPrunedMembers();

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

                logger.putPacketln(this, 
                    "Cache is above high water mark of " + highWaterMark + 
		    ".  Disown member " + mbDisown.getAddress());
	    }

            return (true);
        } 

        return (false);
    }

    /*
     * Mark packets that cannot be recovered.
     */

    public synchronized void markUnrecoverablePkts(int upToSeqNum) {

        /* first check that we still care about these packets */

	TRAMSeqNumber upTo = new TRAMSeqNumber(upToSeqNum);
	int lowestMissing = getLowestKnownMissingPacketNumber();
	if (upTo.isLessThanOrEqual(lowestMissing)) {
	    /*
	     * Nothing to Mark...we are in good shape...just return.
	     */
	    return;
	}

	/*
	 * There is a possibility that we are missing some pkts we care.
	 * lets first force a new computation of the lowestMissingPkt
	 * seq number.
	 * Okay now we need to go thru the cache from lastMissing Seq Number
	 * till upToSeqNum and mark any pkts missing as unrecoverable.
	 */
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_DATACACHE)) {

	    logger.putPacketln(this, 
		"marking any missing packets from lowest missing  pkt # " + 
		upToSeqNum + " as unrecoverable. Lowest Pkt in cache is " +
		getLowestSequenceNumber());
	}

	markUnrecoverablePackets(lowestMissing, upToSeqNum);

	if (nextPacket.isLessThan(upToSeqNum)) {
	    nextPacket.setSeqNumber(upToSeqNum);
	}
	updateLowestKnownMissingPacketNumber();

    }

    /*
     * Starting from the lowest missing packet, upto the specified last
     * sequence number, Mark all those packets that don't have data
     * part as unrcoverable.
     */
    private synchronized void markUnrecoverablePackets(int startSeqNum, 
						       int upToSeqNum) {

        /* 
	 * throw dummy packets into the cache for each one that might be 
	 * missing 
	 */

        TRAMDataPacket pk = null;
	TRAMSeqNumber sqn = new TRAMSeqNumber(startSeqNum);
        for (; sqn.isLessThan(upToSeqNum); sqn.incrSeqNumber()) {
	    /*
	     * For each packet below upToSeqNum, make sure there's a 
	     * TRAMCacheControl object with an unrecoverable data packet 
	     * if there's no packet there yet.
	     */
            TRAMCacheControl cpk;

            try {
                cpk = getControlPacket(sqn.getSeqNumber());
            } catch (NoSuchElementException ne) {

                /* need to add one */

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Adding a control block entry for pkt " + 
			sqn.getSeqNumber());
		}

                cpk = new TRAMCacheControl(sqn.getSeqNumber());

	        byte tmode = tramblk.getTransportProfile().getTmode();

                if (tmode == (byte) TMODE.SEND_ONLY)
		    cpk.indicatePacketDeliveredToApplication();

                dataCache.addElement(cpk);
            }

            if (cpk.getTRAMDataPacket() == null) {

                /* haven't got this packet; mark it unrecoverable */

                pk = new TRAMDataPacket(tramblk);

                pk.setSequenceNumber(sqn.getSeqNumber());
                pk.setFlags(TRAMDataPacket.FLAGBIT_UNRECOVERABLE);
                cpk.setTRAMDataPacket(pk);
		packetHandler.newPacket(pk);

		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Marked pkt as Unrecoverable - Seq # " + 
			sqn.getSeqNumber());
		}
	    } 
	}
    }

    /*
     * Handle notification of sender failure.
     */
    public synchronized void handleSessionDown() {
	/*
	 * The sender can go away after sending the data End message/beacons.
	 * So Only signal SessionDown if the Data End is not signalled
	 */
	if (dataEnd != true)   
	    packetHandler.reportException(TRAMDataPacket.FLAGBIT_SESSION_DOWN);
	else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_SESSION)) {

	        logger.putPacketln(this, 
		     "Ignoring SessionDown as the DataEnd has been Signalled");
	    }
	}
    }

    /*
     * Handle notification of member being pruned from the tree for
     * being unable to keep up with the minimum data rate.
     */
    public synchronized void handlePrunedMember() {
	/*
	 * The sender can go away after sending the data End message/beacons.
	 * So Only signal PrunedMember if the Data End is not signalled
	 */
	if (dataEnd != true)   
	    packetHandler.reportException(
				       TRAMDataPacket.FLAGBIT_MEMBER_PRUNED);
	else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		   "Ignoring Member pruned as the DataEnd has been Signalled");
	    }
	}
    }

    /*
     * Find the request control packet. The control packet list represents
     * the data cache. A control packet is added for each data packet expected.
     * the sequence numbers ascend in order. The index of the requested
     * control packet is computed from the requested sequence number minus
     * the first control packet sequence number. Care must be taken to get
     * the sign right because sequence numbers can be both positive and
     * negative numbers.
     * 
     * If the control packet is found and it's sequence number matches (a
     * sanity check of the algorithm), the control packet object is returned;
     * Otherwise, null is returned.
     */

    private synchronized TRAMCacheControl getControlPacket(int sequenceNumber) 
            throws NoSuchElementException {
        int index;

        /*
         * Get the first control packet in the list along with its
         * sequence number. If the sequence number is less than or equal
         * to the requested sequence number, compute the index. Otherwise
         * return null because the requested packet has been flushed from
         * the cache.
         */
        TRAMCacheControl ac = (TRAMCacheControl) dataCache.firstElement();
        TRAMSeqNumber controlNumber = ac.getTRAMSeqNumber();

        if (controlNumber.isLessThanOrEqual(sequenceNumber)) {
            index = Math.abs(sequenceNumber - controlNumber.getSeqNumber());

            if (index >= dataCache.size()) {

		throw new NoSuchElementException("Requested packet beyond " 
                                                 + "end of cache " 
                                                 + dataCache.size());
	    }
        } else {
            throw new NoSuchElementException("Requested packet " 
                                             + controlNumber.getSeqNumber() 
                                             + " already purged from cache " 
                                             + dataCache.size());
        }

        /*
         * Get the control packet at the requested index. Double check that
         * its sequence number matches that of the requested sequence number.
         * If so, return the control packet; otherwise, log an error and
         * return null.
         */
        ac = (TRAMCacheControl) dataCache.elementAt(index);

        if (ac.getSequenceNumber() == sequenceNumber) {
            return ac;
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE)) {

                logger.putPacketln(this, 
		    "Control packet error. Looking for " + 
		    sequenceNumber + " and found " + 
		    ac.getSequenceNumber() + " Index was " + index);
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATACACHE)) {
                    logger.putPacketln(this, 
                        "First Element = " + ((TRAMCacheControl)
			dataCache.firstElement()).getSequenceNumber() + 
		        " Last Element = " + ((TRAMCacheControl)
			dataCache.lastElement()).getSequenceNumber());
	    }

            throw new NoSuchElementException("Data Cache is corrupted " 
                                             + sequenceNumber);
        }
    }

    /*
     * Print out the count of control packets in the cache.
     */

    public void printDataCounts() {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_DATACACHE)) {

            logger.putPacketln(this, 
		"Data Cache contains " + dataCache.size() + " elements");
	}
    }

    /*
     * Add listeners to for data packets.
     */

    public void addTRAMDataPacketListener(TRAMDataPacketListener l) {
        dataPacketListeners.addElement(l);
    }

    /*
     * A new packet has arrived. Send it off to listeners.
     */

    public synchronized void notifyTRAMDataPacketEvent(TRAMDataPacket pk) {
        TRAMDataPacketListener l;

        for (int i = 0; i < dataPacketListeners.size(); i++) {
            l = (TRAMDataPacketListener) dataPacketListeners.elementAt(i);

            l.receiveDataPacket(new TRAMDataPacketEvent(this, pk));
        }

	try {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		   "notifyTRAMDataPacketEvent:  seq " + 
		    pk.getSequenceNumber()); 
	    }

	    TRAMCacheControl cc = getControlPacket(pk.getSequenceNumber());
	    cc.indicatePacketDeliveredToApplication();
	} catch (NoSuchElementException e) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		   "notifyTRAMDataPacketEvent:  no such element for seq " + 
		    pk.getSequenceNumber()); 
	    }
	}

        if (dataEnd) {
            if (pk.getSequenceNumber() >= lastSequenceNumber) {
                lastPacketReceived = true;
            } 

	    purgeCache(0);
        }
    }

    /*
     * A new packet has arrived. Send it off to listeners.
     */

    public synchronized int notifyInSequenceTRAMDataPacketEvents(
			   TRAMDataPacket pk) throws NoSuchElementException {
	TRAMDataPacketListener l;
        TRAMCacheControl lastPacket = null;
	if ((pk.getFlags() &
	    (TRAMDataPacket.FLAGBIT_SESSION_DOWN | 
	    TRAMDataPacket.FLAGBIT_MEMBER_PRUNED)) != 0) {

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Session down or member pruned.  " +
		    "Notify in sequence packet");
	    }

	    /*
	     * Session Down and Member Pruned packets come with no cache 
	     * control part... hence we need to process it here.
	     */
	    for (int i = 0; i < dataPacketListeners.size(); i++) {
		l = (TRAMDataPacketListener) dataPacketListeners.elementAt(i);
		l.receiveDataPacket(new TRAMDataPacketEvent(this, pk));
	    }
	} else {
	    
	    int startIndex = getControlPacketIndex(pk.getSequenceNumber());
	    
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Notify in seq, pk seq " + pk.getSequenceNumber() + 
		    " start ix " + startIndex + 
		    " cache size " + dataCache.size());
	    }

	    for (int i = 0; i < dataPacketListeners.size(); i++) {
		l = (TRAMDataPacketListener) 
		    dataPacketListeners.elementAt(i);
		
		for (int j = startIndex; j < dataCache.size(); j++) {
		    TRAMCacheControl ac = (TRAMCacheControl) 
			dataCache.elementAt(j);
		    
		    pk = ac.getTRAMDataPacket();
		    
		    if (pk != null) {
			l.receiveDataPacket(new TRAMDataPacketEvent(this, pk));

		        ac.indicatePacketDeliveredToApplication();

	                if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, 
			        "notifyInSequenceTRAMDataPacketEvents: seq " +
			        pk.getSequenceNumber());
			}
		    } else {
	                if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE))
                            logger.putPacketln(this, "Stopping at index " + j);
			
			lastPacket = ac;
			break;
		    }
		}
	    }
	}

        if (dataEnd) {
	    if (nextPacket.isGreaterThan(lastSequenceNumber)) {
		lastPacketReceived = true;
	    } 
            purgeCache(0);
        }
        if (lastPacket == null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "return next packet " + nextPacket.getSeqNumber());
	    }
            return nextPacket.getSeqNumber();
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "return last packet " + lastPacket.getSequenceNumber() +
		    " next packet is " + nextPacket.getSeqNumber());
	    }
            return lastPacket.getSequenceNumber();
        }
    }

    private synchronized int getControlPacketIndex(int sequenceNumber) 
            throws NoSuchElementException {
        int index;

        /*
         * Get the first control packet in the list along with its
         * sequence number. If the sequence number is less than or equal
         * to the requested sequence number, compute the index. Otherwise
         * return null because the requested packet has been flushed from
         * the cache.
         */
        TRAMCacheControl ac = (TRAMCacheControl) dataCache.firstElement();
        TRAMSeqNumber controlNumber = ac.getTRAMSeqNumber();

        index = Math.abs(sequenceNumber - controlNumber.getSeqNumber());

        if (index >= dataCache.size()) {
            throw new NoSuchElementException();
        } 

        /*
         * Get the control packet at the requested index. Double check that
         * its sequence number matches that of the requested sequence number.
         * If so, return the control packet; otherwise, log an error and
         * return null.
         */
	try {
	    ac = (TRAMCacheControl) dataCache.elementAt(index);
	} catch (ArrayIndexOutOfBoundsException abe) {
	    TRAMCacheControl ac1 = (TRAMCacheControl) dataCache.firstElement();

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this, 
		    "Index computation error. Index is " + index +
		    "Control Number is " + controlNumber.getSeqNumber() +
		    "Seq number is " + sequenceNumber +
		    "First element is " +
		    ac1.getTRAMSeqNumber().getSeqNumber());
	    }
	    throw new NoSuchElementException();
	}

        if (ac.getSequenceNumber() == sequenceNumber) {
            return index;
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE)) {

                logger.putPacketln(this, 
		    "Control packet error. Looking for " + 
		    sequenceNumber + " and found " + 
		    ac.getSequenceNumber() + " Index was " + index);
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		TRAMLogger.LOG_DATACACHE)) {

                logger.putPacketln(this, 
		    "First Element = " + ((TRAMCacheControl) 
		    dataCache.firstElement()).getSequenceNumber() + 
		    " Last Element = " + ((TRAMCacheControl)
		    dataCache.lastElement()).getSequenceNumber());
	    }

            throw new NoSuchElementException("Data Cache is corrupted " + 
		sequenceNumber);
        }
    }

    /**
     * This method is the interface for BeaconPacketListener. The multicast
     * input dispatcher calls this method when an beacon packet is received.
     * This method then places the packet on the pkts_to_process Queue/vector
     * and resumes the thread.
     */
    public void receiveBeaconPacket(BeaconPacketEvent e) {
        if (!dataEnd) {
            BeaconPacket pk = e.getPacket();
            byte b = (byte) pk.getFlags();

            if ((b & BeaconPacket.FLAGBIT_TXDONE) != 0) {
		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
       		    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_SESSION)) {

                    logger.putPacketln(this, 
			"Got a DATA END beacon packet " + 
			"last pkt is " + pk.getSeqNumber());
		}

                dataEnd = true;
                lastSequenceNumber = pk.getSeqNumber();
                if (nextPacket.isGreaterThan(lastSequenceNumber)) {
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
       		        TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_SESSION)) {

                        logger.putPacketln(this, 
			    "...and the LAST Packet was received");
		    }

                    lastPacketReceived = true;
                }
                purgeCache(0);
            }
        }
    }

    /*
     * Clear out the cache of old entries. The count parameter specifies
     * how many entries to leave in the cache. If this value is zero,
     * this code attempts to flush the entire cache.  If this value is 
     * negative, then the cache is purged to the lowWaterMark.
     * 
     * @param count purge cache to count packets.
     * @return the resulting size of the cache.
     */
    public synchronized int purgeCache(int count) {
        int stopPacket;

	if (count < 0)
	    count = lowWaterMark;

        if (dataCache.size() > count) {
            if (tramblk.getGroupMgmtBlk().getDirectMemberCount() != 0) {
                stopPacket = tramblk.getGroupMgmtBlk().getLowestPacketAcked();

		stopPacket++;	// next one is un-acked

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

		    logger.putPacketln(this, 
		        "Head purging its cache from " +
		        dataCache.size() + " to " + count + " packets.  " +
			"Stop packet " + stopPacket);
		}
            } else {
                stopPacket = nextPacket.getSeqNumber();

		if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

		    logger.putPacketln(this, 
		        "Member purging its cache from " +
		        dataCache.size() + " to " + count + " packets.  " +
			"Stop packet " + stopPacket);
		}
            }

	    int indx = 0;
	    int size = dataCache.size();
	    while ((size - indx) > count) {
		try {
		    TRAMCacheControl cc = 
			(TRAMCacheControl) dataCache.elementAt(indx);
		    if ((cc.getSequenceNumber() >= stopPacket) || 
			!cc.isPacketDeliveredToApplication()) {

			if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
			    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

			    logger.putPacketln(this, 
				"hit a wall at " + cc.getSequenceNumber() + 
				" stopPacket = " + stopPacket);

			    if (cc.getTRAMDataPacket() == null) {
			        logger.putPacketln(this, 
				    " cache control packet is null");
			    } else {
			        logger.putPacketln(this, 
				    " delivered to app is " + 
				    cc.isPacketDeliveredToApplication());
			    }
			}
			break;
		    }
		    indx++;
		} catch (ArrayIndexOutOfBoundsException aie) {
		    break;
		}
	    }
	    if (indx != 0) {

		/* 
		 * remove range removes fromIndex(inclusive) to toIndex
		 * (exclusive). 
		 */
	        dataCache.rmRange(0, indx);
	    }

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    	TRAMLogger.LOG_DATACACHE)) {
            
	        logger.putPacketln(this, 
	    	    "Purged cache size = " + dataCache.size());
	     
                logger.putPacketln(this, 
	            "LastPacketReceived = " + lastPacketReceived + 
	            " " + lastSequenceNumber);
	    }

            if ((dataCache.size() == 0) && lastPacketReceived) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Sending the null data end packet");
		}

                TRAMDataPacket pk = new TRAMDataPacket(tramblk);

                pk.setFlags(TRAMDataPacket.FLAGBIT_TXDONE);
                notifyTRAMDataPacketEvent(pk);
            }

	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
	    	TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {
	    
	        logger.putPacketln(this, "Done with cache purge...");
	    }
        }

        /*
         * Notify the TRAMControlBlock that the cache is full. If this
         * is a head we probably want to notify the sender that we're
         * in trouble. If we are the sender, the cache full flag
         * prohibits new packets from being transmitted.
         */
        if (dataCache.size() >= cacheSize) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
		    "Cache Full!  " + dataCache.size() + " packets");
	    }

            tramblk.setCacheFull(true);
        } else if (tramblk.isCacheFull()) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

	        logger.putPacketln(this, 
	            "Cache no longer full.  " + 
		    dataCache.size() + " packets");
	    }
	     
            PacketDbIOManager io = (PacketDbIOManager) tramblk.getPacketDb();

            tramblk.setCacheFull(false);
            io.wake();
        }

        return dataCache.size();
    }

    /*
     * @return the lowest sequence number in the cache (always the
     * first element. If the cache is empty, return the sequence
     * number of the next expected packet. The cache is only
     * empty prior to transmission or after transmission has ended.
     */

    public synchronized int getLowestSequenceNumber() {
        try {
            return ((TRAMCacheControl)
		dataCache.firstElement()).getSequenceNumber();
        } catch (NoSuchElementException e) {
            return nextPacket.getSeqNumber();
        }
    }

    /**
     * @return the last packet received. This is always the next expected
     * packet - 1
     */
    public synchronized int getHighestSequenceNumber() {
        int pkt = nextPacket.getSeqNumber();

        return --pkt;
    }

    /**
     * @return the sequence number of the highest packet in the cache.  
     * If the cache is empty, return the sequence number of the 
     * next packet to send
     */
    public synchronized int getHighestSequenceNumberinCache() {
        try {
            return ((TRAMCacheControl)
		dataCache.lastElement()).getSequenceNumber();
        } catch (NoSuchElementException e) {
            return nextPacket.getSeqNumber();
        }
    }

    /*
     * Membership has been received. If we're still in the init state,
     * check to see if there is a holding tank. If so then this is a member
     * who requested late join with limited recovery. set the next packet
     * equal to the value set in the heads block. This is the lowest value
     * that the head can retransmit. Then reprocess the entiry holding tank.
     * Packets prior to the next packet will be ignored. All others will
     * be processed as if they were new.
     * 
     * If there is no holding tank the this member requested late join with
     * no recovery. Set the initNext flag. This indicates to the
     * receiveDataPacket method to set the nextPacket equal to the next
     * data packet received (retransmissions are ignored).
     * 
     * Clear the init flag when leaving.
     */

    public synchronized void receiveTRAMMembership(TRAMMembershipEvent e) {
	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
            logger.putPacketln(this, "Received membership notification.");
	}

	HeadBlock head = tramblk.getGroupMgmtBlk().getHeadBlock();
        if (init) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "The init flag is set. carry on");
	    }

	    init = false;

	    switch (tp.getLateJoinPreference()) {
	    case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
	    	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, "The holding tank exists...");
		    logger.putPacketln(this, 
			"Setting the starting sequence " + "number to " + 
			head.getStartSeqNumber());
		    logger.putPacketln(this,  
			"Replaying the " + holdingTank.size() + 
			" packets in the holdingTank");
		}
 
		for (int i = 0; i < holdingTank.size(); i++) {
		    receiveDataPacket((TRAMDataPacketEvent)
			holdingTank.elementAt(i));
		}

		/*
		 * The following need not be done as the data delivery is
		 * sequential(1 thru n) so, processing of the holding tank 
		 * would have done the needful. The packetHandler seq#
		 * continues to be 1 as it is the first packet to be
		 * delivered. How do we deliver it if it is unordered?
		 */
		 // nextPacket.setSeqNumber(head.getStartSeqNumber());
		 // packetHandler.setSeqNumber(head.getStartSeqNumber());

		holdingTank = null;
		    
	    	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"The holding tank has been deleted");
		}
		break;

	    case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
	    	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {
		    
		    logger.putPacketln(this, "The holding tank exists...");
		    logger.putPacketln(this, 
			"Setting the starting sequence " + "number to " + 
			head.getStartSeqNumber());
		    logger.putPacketln(this,  
			"Replaying the " + holdingTank.size() + 
			" packets in the holdingTank");
		}
		int tmpsqn = head.getStartSeqNumber();
		TRAMSeqNumber headSqnNumber = new TRAMSeqNumber(tmpsqn);

		// First set the starting #s.
		nextPacket.setSeqNumber(tmpsqn);
		packetHandler.setSeqNumber(tmpsqn);
		lowestKnownMissingPacket = tmpsqn;
		    
		/*
		 * We need to add ONLY packets that are after the start
		 * seq number promised by the head. Hence we need to
		 * discard earlier packets.
		 */
		for (int i = 0; i < holdingTank.size(); i++) {
		    TRAMDataPacketEvent tramevnt = 
			(TRAMDataPacketEvent) holdingTank.elementAt(i);
		    tmpsqn = tramevnt.getPacket().getSequenceNumber();
		    /* 
		     * ONLY call receive data if pkt seq# is >= promised 
		     * headseq#
		     */
		    if (headSqnNumber.isLessThanOrEqual(tmpsqn) == true) {
			receiveDataPacket(tramevnt);
		    }
		}

		holdingTank = null;
	    	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_DATACACHE)) {
		    
		    logger.putPacketln(this, 
			"The holding tank has been deleted");
		}
		break;

	    case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:
	    	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Setting the starting sequence " + "number to " + 
			head.getStartSeqNumber());
		}

		int tmpsqn1 = head.getStartSeqNumber();
		nextPacket.setSeqNumber(tmpsqn1);
		packetHandler.setSeqNumber(tmpsqn1);
		lowestKnownMissingPacket = tmpsqn1;
		break;

	    default:
		init = true;
	    	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
		    logger.putPacketln(this, "Invalid Late Join Preference"); 
		}
		break;
	    }
        } else {
	    // init not set... Must be called in the event of switching a head.

	    switch (tp.getLateJoinPreference()) {

	        case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
		    /*
		     * Nothing can be done.... All the packets have to be
		     * received.
		     *
		     * Temporary fix... since we don't have a persistance
		     * server, we flag Irrecoverable exception and let the
		     * application deal with the situation.
		     * The following call to Unrecoverable should be removed
		     * once we have a persistance server solution.
		     */
		    markUnrecoverablePkts(head.getStartSeqNumber());

		    break;

	        case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:

		    /*
		     * Nothing can be done... All packets after the membership
		     * was achieved have to be received.
		     */
		
		    /*
		     * Currently falling thru to perform the same behaviour as
		     * NO_RECOVERY.
		     */
		    // break;

	        case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:

		    /*
		     * Now Here we can markup some packets that cannot be
		     * recovered as Irrecoverable Packets.
		     */
		
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
                        TRAMLogger.LOG_DATACACHE)) {

		        logger.putPacketln(this, 
			    "Setting the starting sequence " + "number to " + 
			    head.getStartSeqNumber());
		    }

		    // nextPacket.setSeqNumber(head.getStartSeqNumber());
		    markUnrecoverablePkts(head.getStartSeqNumber());
		    break;

		  default:
		    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
                        TRAMLogger.LOG_DATACACHE)) {

		        logger.putPacketln(this, 
			    "Invalid Late Join Preference"); 
		    }
		    break;
		}
	}

	tramblk.getMemberAck().dataCacheProcessedMembershipEvent();
    }

    /*
     * dealWithUnrecoverablePackets
     *
     * This methods is typically called when the Node receives a Hello
     * message with the LowSeqNumber listed in the packet. The Routine just
     * checks if the LowSeqNumber affects this node at all. If so
     * those packets that are marked(waiting for data) will be marked
     * as irrecoverable packets if the lateJoin preference in the Transport
     * profile is LATE_JOIN_WITH_NO_RECOVERY. If the preference is set to
     * either LATE_JOIN_WITH_FULL_RECOVERY or LATE_JOIN_WITH_LIMITED_RECOVERY
     * then the code will have to find other means to recover these
     * packets. Since we currently do not support Other means of getting
     * the repairs, we are currently marking them to be irrecoverable packets -
     * just like the LATE_JOIN_WITH_NO_RECOVERY case.
     */
    public synchronized void dealWithUnrecoverablePkts(int upToSeqNumber) {

	TRAMTransportProfile tp = tramblk.getTransportProfile();

	switch (tp.getLateJoinPreference()) {

	  case TRAMTransportProfile.LATE_JOIN_WITH_FULL_RECOVERY:
	
	    /*
	     * Nothing can be done.... All the packets have to be
	     * received.
	     * Temporary fix... since we don't have a persistance
	     * server, we flag Irrecoverable exception and let the
	     * application deal with the situation.
	     * The following call to Unrecoverable should be removed
	     * once we have a persistance server solution.
	     */
	    markUnrecoverablePkts(upToSeqNumber);
	    
	    break;

	  case TRAMTransportProfile.LATE_JOIN_WITH_LIMITED_RECOVERY:
	    
	    /*
	     * Nothing can be done... All packets after the membership
	     * was achieved have to be received.
	     */
		
	    /*
	     * Currently falling thru to perform the same behaviour as
	     * NO_RECOVERY.
	     */
	    // break;

	  case TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY:
	    
	    /*
	     * Now Here we can markup some packets that cannot be
	     * recovered as Irrecoverable Packets.
	     */
	    markUnrecoverablePkts(upToSeqNumber);
	    break;
	    
	  default:
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
		TRAMLogger.LOG_DATACACHE)) {

	        logger.putPacketln(this, "Invalid Late Join Preference"); 
	    }
	    break;
	}
    }

    /*
     * Instead of promising the entire cache (AM includes 'left' edge)
     * for limited recovery, promise only part of the cache. 
     * 
     *
     * right edge - (high_water_mark  -  low_water_mark)
     *	
     * In the case of a cache where 400 is low_water_mark, 800 is 
     * high_water_mark, and 1200 is full, the left edge is 500 and the right 
     * edge is 1300, we would promise packets starting at 900 rather than 500.
     */
    public int getLimitedRecoverSequenceNumber() {
	if (dataCache.size() == 0)
	    return 1;

	int index;
	index = dataCache.size() - 1 - (defaultHighWaterMark - lowWaterMark);

	if (index < 0)
	    index = 0;

        TRAMCacheControl ac;
	ac = (TRAMCacheControl)dataCache.elementAt(index);

	if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS |
	    TRAMLogger.LOG_DATACACHE | TRAMLogger.LOG_CONG)) {

	    logger.putPacketln(this, 
	        "Starting Seq " + ac.getSequenceNumber() +
	        " Cache Size " + dataCache.size() +
	        " HighWater " + defaultHighWaterMark +
	        " LowWater " + lowWaterMark);
	}
	
        return ac.getSequenceNumber();
    }

    public boolean aboveHighWaterMark() {
	if (dataCache.size() > highWaterMark)
	    return true;

	return false;
    }

}


/*
 * This interface defines the methods of a data cache packet handler. The
 * newPacket method is called whenever a new packet arrives for the cache. The
 * packet handler decided whether to hand this packet up to the listeners
 * or not. The decision is made based on the type of ordering expected
 * in the application.
 */
interface TRAMPacketHandler {
    public void newPacket(TRAMDataPacket pk);
    public void reportException(byte flags);
    public void setSeqNumber(int seqNumber);
}

/*
 * The unordered packet handler sends all new packets to the listeners. 
 */
class UnorderedPacketHandler implements TRAMPacketHandler {
    TRAMDataCache dataCache;
    TRAMControlBlock tramblk = null;

    public void setSeqNumber(int seqNumber) {
	// nothing to do
    }

    public UnorderedPacketHandler(TRAMControlBlock tramblk, 
				  TRAMDataCache cache, int startPacket) {
	dataCache = cache;
	this.tramblk = tramblk;
    }
    
    public void newPacket(TRAMDataPacket pk) {
	dataCache.notifyTRAMDataPacketEvent(pk);
    }

    public void reportException(byte flags) {
	TRAMDataPacket npk = new TRAMDataPacket(tramblk);
	npk.setFlags(flags);
	dataCache.notifyTRAMDataPacketEvent(npk);	
    }
}

/* 
 * The ordered packet handler sends packets up to the listeners
 * when the current packet is the next packet expected.
 * The member variable nextPacket in this class is DIFFERENT from
 * the variable nextPacket in TRAMGenericDataCache!
 * nextPacket is used here to keep track of packets delivered to the
 * application whereas nextPacket above is used to keep of the of the
 * actual next packet we expect to receive regardless of whether or not
 * there are missing packets.
 */
class OrderedPacketHandler implements TRAMPacketHandler {
    TRAMDataCache dataCache;
    TRAMSeqNumber nextPacket;	// Next packet delivered to application
    TRAMControlBlock tramblk = null;
    TRAMLogger logger = null;

    public void setSeqNumber(int seqNumber) {
	nextPacket.setSeqNumber(seqNumber);
    }

    public OrderedPacketHandler(TRAMControlBlock tramblk, TRAMDataCache cache, 
				int startPacket) {
	nextPacket = new TRAMSeqNumber(startPacket);
	dataCache = cache;
	this.tramblk = tramblk;
	logger = tramblk.getLogger();
    }

    public void newPacket(TRAMDataPacket pk) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	    logger.putPacketln(this, 
		"newPacket.  pk seq " + pk.getSequenceNumber() +
		" nextPacket " + nextPacket.getSeqNumber());
	}

	if (nextPacket.isEqualTo(pk.getSequenceNumber())) {
	    int next = dataCache.notifyInSequenceTRAMDataPacketEvents(pk);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
	        logger.putPacketln(this, 
		    "newPacket. next is " + next);
	    }
	
	    nextPacket.setSeqNumber(next);
	} else {
	    if (nextPacket.isGreaterThan(pk.getSequenceNumber())) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
                    TRAMLogger.LOG_DATACACHE)) {

		    logger.putPacketln(this, 
			"Packet " + pk.getSequenceNumber() +
			"Has already been been forwarded!!!");
		}
	    }
	}
	    
    }

    public void reportException(byte flags) {
	TRAMDataPacket npk = new TRAMDataPacket(tramblk);
	npk.setSequenceNumber(nextPacket.getSeqNumber());
	npk.setFlags(flags);
	dataCache.notifyInSequenceTRAMDataPacketEvents(npk);	
    }

}
