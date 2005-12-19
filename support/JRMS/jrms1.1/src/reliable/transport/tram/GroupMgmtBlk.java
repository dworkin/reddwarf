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
 * GroupMgmtBlk.java
 * 
 * Module Description:
 * The class defines the TRAM Group Management block.
 */
package com.sun.multicast.reliable.transport.tram;

import java.lang.*;
import java.net.*;
import java.util.*;

class GroupMgmtBlk {
    TRAMControlBlock tramblk;
    TRAMLogger logger;
    TRAMTransportProfile tp;

    /*
     * Local details of a member. The fields are maintained
     * by all members.
     */
    private long rttToSender = 0;
    private byte currentMrole = MROLE.MEMBER_EAGER_HEAD;
    private int rxLevel = 0;

    /*
     * level 0 is unknown, level 1 is
     * sender and levels 2 thru 255
     * are valid member levels.
     */

    /* Stats block  needs to be added */

    /*
     * The following fields are details of the affiliated and
     * the backup heads.
     */
    private HeadBlock headBlock = null;

    /*
     * The following fields are used only upon assuming the
     * role of a head.
     * 
     */
    private Vector memberList = null;
    private GroupMembershipMask memberMask = null;
    private byte retransmitTTL = 0;
    private byte cstate = CSTATE.NORMAL;
    private byte hstate = HSTATE.INIT;
    private byte lstate = LSTATE.NA;
    private long helloPeriod = 0;
    private int peakMemberCount;
    private long lastHaSentTime = 0;
    private int lastHaTTLSent = 0;

    /**
     * Create the group management block. The purpose of the group management
     * block is to keep track of members  at the head nodes and the sender.
     * A member list vector is created and is loaded with member blocks when
     * a member joins the tram group. A mask is created an updated each time a
     * member joins the group. This mask is used in the TRAMHeadAck class to
     * determine which members need to ack packets received. The mask of
     * members is obtained for each packet received. When all current
     * members have acked the packet, the packet may be discared.
     * 
     * @param tramblk the tram control block for this session.
     */
    public GroupMgmtBlk(TRAMControlBlock tramblk) {
        this.tramblk = tramblk;
        this.tp = tramblk.getTransportProfile();
        this.logger = tramblk.getLogger();

        if (tp.getMrole() != MROLE.MEMBER_ONLY) {
            int maxMembers = ((int) tp.getMaxMembers()) & 0xffff;

            memberList = new Vector(maxMembers);
            memberMask = new GroupMembershipMask(maxMembers, logger);
        }
    }

    /**
     * Get the member with the specified IP address and port. The
     * member list is searched for a member with the specified address
     * and port pair. Selection based on address AND port allows
     * for two or more members on the same node.
     * 
     * @param ia the IP address of the member
     * @param port the port number of the member.
     */
    public MemberBlock getMember(InetAddress ia, 
                                 int port) throws NoSuchElementException {

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
                "Retrieve member with address " + ia.toString() 
                + " and port = " + port);
	}

        MemberBlock mb;

        for (int i = 0; i < memberList.size(); i++) {
            mb = (MemberBlock) memberList.elementAt(i);

	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this,
                    "Member = " + mb.getAddress().toString() 
                    + " and port = " + mb.getPort());
	    }

            if ((ia.equals(mb.getAddress())) && (mb.getPort() == port)) {
                return mb;
            }
        }

        throw new NoSuchElementException();
    }

    /**
     * gets the member block based on the member index(based on the
     * current member count. Example, if there are 10 members, the index
     * 0 will fetch the first element, 1 the the second element and so
     * on.
     * 
     * @param index of the member.
     * @exception throws IndexOutofBoundsException, if an invalid
     * index is provided.
     */
    public MemberBlock getMember(int index) throws IndexOutOfBoundsException {
        MemberBlock mb = (MemberBlock) memberList.elementAt(index);

        return mb;
    }

    /**
     * Add a member to the member list.
     * 
     * @param mb the member block to add to the list.
     */
    public void putMember(MemberBlock mb) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, 
	        "Adding member with address " + 
	        mb.getAddress().toString() + " " + mb.getPort());
	}

        // int memberId = memberList.size() + 1;

        int memberId = memberMask.assignNewMemberBit();

        if (memberId == 0) {
            /*
             * 0 is an invalid member id. This happens ONLY if
             * the bitMask allocated is insufficient
             * Should we increase the bitmask???
             */
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC)) {
                logger.putPacketln(this, 
		    "Unable to add the Member to the MemberList");
	    }

            return;
        }

        mb.setMemberId(memberId);
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this, "Member id = " + memberId);
	}
	/*
	 * New member may be joining late so we need to ensure that
	 * this member doesn't lower the highest sequence we're 
	 * allowed to send.
	 */
	if (mb.getLastPacketAcked() + tp.getCongestionWindow() > 
	    getHighestSequenceAllowed()) {

	    mb.setHighestSequenceAllowed(mb.getLastPacketAcked() + 
		tp.getCongestionWindow());
	} else {
	    mb.setHighestSequenceAllowed(getHighestSequenceAllowed());
	}

        memberList.addElement(mb);
	
	tramblk.setHighestSequenceAllowed(getHighestSequenceAllowed());

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
	    TRAMLogger.LOG_CONG)) {

            logger.putPacketln(this, 
	        "putMember " + mb.getAddress() + " " + mb.getPort() +
	        ", Member id = " + memberId +
	        ", lastAcked " + mb.getLastPacketAcked() +
	        ", mb highest seq " + mb.getHighestSequenceAllowed() +
	        ", highest seq " + tramblk.getHighestSequenceAllowed());
	}

	if (memberList.size() > peakMemberCount)
	    peakMemberCount = memberList.size();

        if (tramblk.getSimulator() != null) {
            tramblk.getSimulator().memberCountChange(
	        tramblk.getTransportProfile().getUnicastPort(), 
		memberList.size());
        } 
    }

    /**
     * remove a member from the member list.
     * 
     * @param mb Memberblock that is to be removed from the memberlist.
     * 
     */
    public void removeMember(MemberBlock mb) {
	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
                "Removing member with address " 
                + mb.getAddress().toString() + " Port " 
                + mb.getPort());
	}

        if (memberList.removeElement(mb) == true) {
            memberMask.clearMemberBit(mb.getMemberId());

            if (tramblk.getSimulator() != null) {
                tramblk.getSimulator().memberCountChange(
		    tramblk.getTransportProfile().getUnicastPort(), 
		    memberList.size());
            } 
        }

        /*
         * Purge the cache back now that we have one less member. If
         * the cache was filling up because of this member, this purge
         * cache call will clean that up.
         */
        tramblk.getTRAMDataCache().purgeCache(-1);
    }

    /**
     * 
     */
    public HeadBlock getHeadBlock() {
        return headBlock;
    }

    /**
     * 
     */
    public void setHeadBlock(HeadBlock headBlock) {
        this.headBlock = headBlock;

        // register head changes with the simulator

        if (tramblk.getSimulator() != null) {
            if (headBlock != null) {
                tramblk.getSimulator().headChange(tp.getUnicastPort(), 
                                                 headBlock.getPort());
            } else {
                tramblk.getSimulator().headChange(tp.getUnicastPort(), 0);
            }
        }
    }

    /**
     * @return the mask of current members.
     */
    public GroupMembershipMask getMemberMask() {
        GroupMembershipMask gMask = null;

        try {
            gMask = (GroupMembershipMask) memberMask.clone();
        } catch (CloneNotSupportedException ce) {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY_BASIC)) {
                logger.putPacketln(this, "Clone not supported ERROR");
	    }
        }

        return gMask;
    }

    /**
     * gets the cstate field. Applicable only while performing the
     * role of a head.
     * 
     * @return byte - the data cache usage state.
     * 
     */
    public byte getCstate() {
        return cstate;
    }

    /**
     * gets the Hstate value.
     * 
     * @param byte - the Hstate value.
     */
    public byte getHstate() {
        return hstate;
    }

    /**
     * sets the Hstate field to the specified value.
     * 
     * @param byte - required hstate value. refer HSTATE.java for
     * valid values.
     */
    public void setHstate(byte hstate) {
        this.hstate = hstate;

        // register state changes with the simulator

        if (tramblk.getSimulator() != null) {
            tramblk.getSimulator().stateChange(tp.getUnicastPort(), hstate);
        } 
    }

    /**
     * gets the Lstate value.
     * 
     * @param byte - the Lstate value.
     */
    public byte getLstate() {
        return lstate;
    }

    /**
     * sets the Lstate field to the specified value.
     * 
     * @param byte - required lstate value. refer LSTATE.java for
     * valid values.
     */
    public void setLstate(byte lstate) {
        if (tp.isLanTreeFormationEnabled()) {
            this.lstate = lstate;
        } else {
            this.lstate = LSTATE.NA;
        }
    }

    /**
     * gets the TTL used to perform repairs to the members
     * 
     * @return byte - the retransmit TTL to perform repairs.
     */
    public byte getRetransmitTTL() {
        return retransmitTTL;
    }

    /**
     * gets the Round trip time to the Sender in milliseconds.
     * 
     * @return long the rtt value.
     */
    public long getRttToSender() {
        return rttToSender;
    }

    /**
     * gets the current Mrole played by the member.
     * 
     * @return byte - the current Mrole.
     */
    public byte getCurrentMrole() {
        return currentMrole;
    }

    /**
     * gets the Rx Level of the member.
     * 
     * @return int - the RxLevel of the member.
     * 0 indicates Level Unknown.
     * 1 is reserved for sender RxLevel.
     * 2 thru 255 are valid RxLevels for non-senders.
     * 
     */
    public int getRxLevel() {
        return rxLevel;
    }

    /**
     * gets the inter Hello period(in millisecs)
     * 
     * @return long - the inter hello period in use.
     */
    public long getHelloPeriod() {
        return helloPeriod;
    }

    /**
     * sets the inter Hello period to the specified value.
     * 
     * @param long - inter hello period.
     */
    public void setHelloPeriod(long helloPeriod) {
        this.helloPeriod = helloPeriod;
    }

    /**
     * sets the data Cache usage state( Cstate) to the specified
     * value. see CSTSTE.java for valid values.
     * 
     * @param byte - cstate value.
     */
    public void setCstate(byte cstate) {
        this.cstate = cstate;
    }

    /**
     * sets the TTL used to perform repairs to the members
     * 
     * @param retransmitTTL - the retransmit TTL to be used to
     * perform repairs.
     */
    public void setRetransmitTTL(byte retransmitTTL) {
        this.retransmitTTL = retransmitTTL;
    }

    /**
     * Sets the Round trip time to the sender field with the specified
     * value.
     * 
     * @param long - the required rtt value in millisecs.
     */
    public void setRttToSender(long rttToSender) {
        this.rttToSender = rttToSender;
    }

    /**
     * Sets the RxLevel of the member to the specified value.
     * 
     * @param int - the required rxLevel value. The following are details
     * of the RxLevel numbers -
     * 0 indicates Level Unknown.
     * 1 is reserved for sender RxLevel.
     * 2 thru 255 are valid RxLevels for non-senders.
     * 
     */
    public void setRxLevel(int rxLevel) {
        this.rxLevel = rxLevel;

        // register state changes with the simulator

        if (tramblk.getSimulator() != null) {
            tramblk.getSimulator().levelChange(tp.getUnicastPort(), rxLevel);
        } 
    }

    /**
     * Method: createMemberList
     * 
     */
    public synchronized void createMemberList(int max_members) {
        if (memberList == null) {
            memberList = new Vector(max_members);
        }
    }

    /**
     * Deletes the member list. Typically called when a head resigns.
     */
    public synchronized void deleteMemberList() {
        if (memberList != null) {
            memberList.removeAllElements();

            memberList = null;
        }
        if (tramblk.getSimulator() != null) {
            tramblk.getSimulator().memberCountChange(
		tramblk.getTransportProfile().getUnicastPort(), 0);
        } 
    }

    /**
     * gets the direct member count. Valid only if performing the role of
     * a Group-head.
     * 
     * @return - int, the count of current members. Valid only if performing
     * the role of a head.
     */
    public int getDirectMemberCount() {
        if (memberList == null) {
            return 0;
        } 

        return memberList.size();
    }

    /**
     * gets the Indirect member count. Valid only if performing the role
     * of a Group-head.
     * 
     * @return - int, the count of current members.
     * 
     */
    public int getIndirectMemberCount() {
        if (memberList == null) {
            return 0;
        } 

        int count = 0;

        synchronized (memberList) {
            for (int i = 0; i < memberList.size(); i++) {
                MemberBlock mb = (MemberBlock) memberList.elementAt(i);

                count += (mb.getDmemCount() + mb.getIndmemCount());
            }
        }

        return count;
    }

    /**
     * gets the peak direct member count. Valid only if performing 
     * the role of a Group-head.
     * 
     * @return - int, the count of peak members. Valid only if performing
     * the role of a head.
     */
    public int getPeakMemberCount() {
        return peakMemberCount;
    }

    /**
     * gets the count of members that have MRole set to MEMBER_ONLY.
     * Valid only if performing the role of a Group-head.
     * 
     * @return - int, the count of current MEMBER_ONLY members.
     * 
     */
    public int getMemberOnlyCount() {
        if (memberList == null) {
            return 0;
        } 

        int count = 0;

        synchronized (memberList) {
            for (int i = 0; i < memberList.size(); i++) {
                MemberBlock mb = (MemberBlock) memberList.elementAt(i);

                if (mb.getMrole() == MROLE.MEMBER_ONLY) {
                    count++;
                } 
            }
        }

        return count;
    }

    /**
     * gets the count of indirect members that are advertizing.
     * 
     * @return - int, the count of indirect members that are advertizing.
     * 
     */
    public int getIndirectAdvertisingMemberCount() {
        if (memberList == null) {
            return 0;
        } 

        int count = 0;

        synchronized (memberList) {
            for (int i = 0; i < memberList.size(); i++) {
                MemberBlock mb = (MemberBlock) memberList.elementAt(i);

                count += mb.getAdvertmemCount();
            }
        }

        return count;
    }

    /**
     * gets the count directly connected members that are currently
     * advertising.
     * @return - the count of directly connected members that are advertising.
     * 
     */
    public int getDirectAdvertisingMemberCount() {
        if (memberList == null) {
            return 0;
        } 

        int count = 0;

        synchronized (memberList) {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Total members is " + memberList.size());
	    }

            for (int i = 0; i < memberList.size(); i++) {

                /*
                 * An advertising member will either have its MROLE set
                 * to either RELUCTANT_HEAD or EAGER_HEAD OR its HSTATE
                 * will be set to either ACCEPTING MEMBERS or ACCEPTING
                 * POTENTIAL HEADS only. The above is tested to determine
                 * the number of advertizing heads that are directly
                 * connected.
                 */
                MemberBlock mb = (MemberBlock) memberList.elementAt(i);
                byte mroleTmp = (byte) mb.getMrole();
                byte hstateTmp = (byte) mb.getHstate();

                if ((hstateTmp == HSTATE.ACCEPTING_MEMBERS) 
                        || (hstateTmp == HSTATE.ACCEPTING_POTENTIALHEADS_ONLY) 
                        || (mroleTmp != MROLE.MEMBER_ONLY)) {
                    count++;
                } 
            }
        }

        return count;
    }

    /*
     * Return the lowest acked sequence number of all the members. If
     * there are no member, throw NoSuchElementException.
     */

    public synchronized int getLowestPacketAcked() 
            throws NoSuchElementException {
        TRAMSeqNumber lowest;
        MemberBlock mb = (MemberBlock) memberList.firstElement();
        MemberBlock lowestMb = null;

        lowest = new TRAMSeqNumber(mb.getLastPacketAcked());

        for (int i = 1; i < memberList.size(); i++) {
            mb = (MemberBlock) memberList.elementAt(i);

            if (lowest.isGreaterThan(mb.getLastPacketAcked())) {
                lowest.setSeqNumber(mb.getLastPacketAcked());
		lowestMb = mb;
            } 
        }

	if (lowestMb != null) {
	    if (logger.requiresLogging(TRAMLogger.LOG_DIAGNOSTICS)) {
	        logger.putPacketln(this,
		    "getLowestPacketAcked:  seq num " + 
		    lowest.getSeqNumber() + " " +
		    lowestMb.getAddress());
	    }
	}

        return lowest.getSeqNumber();
    }

    /*
     * returns the timestamp when the last HA message was sent.
     */
    public long getLastHASentTime() {

	return lastHaSentTime;
    }

    /*
     * returns the timestamp when the last HA message was sent.
     */

    public void setLastHASentTime(long lastHaSentTime) {

	this.lastHaSentTime = lastHaSentTime;
    }

    /*
     * returns the TTL to which the last HA was sent.
     */
    public int getLastHaTTLSent() {

	return lastHaTTLSent;
    }

    /*
     * returns the timestamp when the last HA message was sent.
     */
    public void setLastHaTTLSent(int lastHaTTLSent) {

	this.lastHaTTLSent = lastHaTTLSent;
    }

    /*
     * Return the highest sequence number we're allowed to send.
     * This number is the smallest that's acceptable to all members.
     */
    public synchronized int getHighestSequenceAllowed() {
	if (getDirectMemberCount() == 0)
	    return tramblk.getHighestSequenceAllowed();

        MemberBlock mb = (MemberBlock)memberList.firstElement();
        MemberBlock lowestMb = null;

    	int highestSequenceAllowed = mb.getHighestSequenceAllowed();

        for (int i = 1; i < memberList.size(); i++) {
            mb = (MemberBlock)memberList.elementAt(i);

            if (mb.getHighestSequenceAllowed() < highestSequenceAllowed) {
                highestSequenceAllowed = mb.getHighestSequenceAllowed();
		lowestMb = mb;
	    }
        }

        return highestSequenceAllowed;
    }

}
