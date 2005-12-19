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
 * TRAMStats.java
 * 
 * Module Description:
 * 
 * The TRAMStats class contains all of the statistics associated with a
 * transport session.
 */

package com.sun.multicast.reliable.transport.tram;

import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMStatistics;
import java.net.*;
import java.util.Vector;

/**
 * TRAMStats defines the TRAM Statistics block. Applications can get a
 * copy of this block via the getRMStatistics method support by RMxxxSocket
 * class.
 */
public class TRAMStats implements RMStatistics, Cloneable {
    TRAMControlBlock tramblk = null;

    // Data Statistics 

    private long bytesSent = 0;		
    private long bytesReSent = 0;	
    private long bytesRcvd = 0;	
    private long retransBytesRcvd = 0;
    private long packetsSent = 0;	
    private long packetsRcvd = 0;
    private long retransSent = 0;	
    private long retransRcvd = 0;
    private long duplicatePackets = 0; 
    private long duplicateBytes = 0;
    private long dataStartTime = 0;
    private long packetsNotRecovered = 0;
    private long packetsNotDelivered = 0;

    // Member Statistics 

    private int prunedMembers = 0;
    private int lostMembers = 0;

    // Control Message Statistics 

    // Multicast 
    private long mcastCntlBytesSent = 0;
    private long mcastCntlBytesRcvd = 0;
    private long mcastBeaconSent = 0;
    private long mcastBeaconRcvd = 0;
    private long mcastHelloSent = 0;
    private long mcastHelloRcvd = 0;
    private long mcastHASent = 0;
    private long mcastHARcvd = 0;
    private long mcastMSSent = 0;
    private long mcastMSRcvd = 0;

    // Unicast 
    private long ucastCntlBytesSent = 0;
    private long ucastCntlBytesRcvd = 0;
    private long ucastAMSent = 0;
    private long ucastAMRcvd = 0;
    private long ucastRMSent = 0;
    private long ucastRMRcvd = 0;
    private long ucastHelloSent = 0;
    private long ucastHelloRcvd = 0;
    private long ucastACKSent = 0;
    private long ucastACKRcvd = 0;
    private long ucastCongSent = 0;
    private long ucastCongRcvd = 0;
    private long ucastHBSent = 0;
    private long ucastHBRcvd = 0;

    /**
     * ************************************************************
     * RMStatistics interface support methods.
     * ***********************************************************
     */
    private Vector senderAddresses = null;
    private Vector receiverAddresses = null;

    /**
     * Constructor.
     * @param tramblk the TRAMControlBlock for this session.
     */
    public TRAMStats(TRAMControlBlock tramblk) {
        this.tramblk = tramblk;
    }

    /**
     * Returns the count of senders participating in the multicast session.
     * 
     * @return int count of known senders of the multicast session.
     * @exception UnsupportedException if Statistics block is not supported.
     */
    public int getSenderCount() throws UnsupportedException {
        if (senderAddresses == null) {
            return 0;
        } 

        return senderAddresses.size();
    }

    /**
     * Returns the list of senders of the multicast session.
     * 
     * @return InetAddress[] list of known senders of the multicast session.
     * A return value of null signifies no known
     * senders.
     * @exception UnsupportedException if Statistics Block is not supported.
     */
    public InetAddress[] getSenderList() throws UnsupportedException {
        InetAddress[] senders = null;

        if ((senderAddresses == null) || (senderAddresses.size() == 0)) {
            return senders;
        } 

        senders = new InetAddress[senderAddresses.size() + 1];

        for (int i = 0; i < senderAddresses.size(); i++) {
            try {
                InetAddress tmp = (InetAddress) senderAddresses.elementAt(i);
                InetAddress Inet = 
                    InetAddress.getByName(tmp.getHostAddress());

                senders[i] = InetAddress.getByName(tmp.getHostAddress());
            } catch (IndexOutOfBoundsException ie) {
                break;
            } catch (UnknownHostException ue) {}
        }

        return senders;
    }

    /**
     * Returns the count of receivers participating in the multicast session.
     * 
     * @return count of known receivers of the multicast session. 
     * The sender will report the total number of members in the 
     * multicast group.  a head will report the total number of direct
     * and indirect members below the head in the TRAM tree.
     */
    public int getReceiverCount() {

        int recvCount = 0;

        TRAMTransportProfile tp = tramblk.getTransportProfile();

        try {

            GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

            recvCount = gblk.getDirectMemberCount() 
                        + gblk.getIndirectMemberCount();

        } catch (NullPointerException ne) {
        }

        return recvCount;
    }

    /**
     * Return the number of direct members.
     *
     * @return count of members affiliated with this head or sender.
     */
    public int getDirectMemberCount() {
        try {
            GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();
	    
            return (gblk.getDirectMemberCount());

        } catch (NullPointerException ne) {
        }

	return 0;
    }

    /**
     * Return the number of indirect members.
     *
     * @return count of members affiliated with this head or sender.
     */
    public int getIndirectMemberCount() {
	try {
	    GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();
	    
	    return (gblk.getIndirectMemberCount());
	    
        } catch (NullPointerException ne) {
        }

	return 0;
    }

    /**
     * Returns the list of known receivers of the tuned to the multicast
     * session.
     * 
     * @return an array of current list of known receivers of the multicast
     * session. The list of receivers is currently not supported by TRAM.
     * @exception UnsupportedException is thrown if Statistics block is not 
     * supported.
     */
    public InetAddress[] getReceiverList() throws UnsupportedException {
        throw new UnsupportedException();
    }

    /**
     * Returns the bytecount of data contributed to the multicast session.
     * 
     * @return bytecount of data contributed to the session by this node.
     * @exception UnsupportedException if Statistics block is not supported.
     */
    public long getTotalDataSent() throws UnsupportedException {
        return bytesSent;
    }

    /**
     * Returns the bytecount of data retransmitted by this node to perform
     * repairs.
     * 
     * @return bytecount of data retransmitted to the session by this node.
     * @exception UnsupportedException if Statistics block is not supported.
     */
    public long getTotalDataReSent() throws UnsupportedException {
        return bytesReSent;
    }

    /**
     * Returns the bytecount of data received by this node.
     * 
     * @return bytecount of data received by this node so far.
     * @exception UnsupportedException if Statistics block is not supported.
     */
    public long getTotalDataReceive() throws UnsupportedException {
        return bytesRcvd;
    }

    /**
     * Creates a copy of the TRAM Statistics blocks and returns a reference
     * to it.
     * @return the clone of the TRAM Statistics block.
     */
    protected Object clone() {
        TRAMStats stats = null;
        int i = 0;

        try {
            stats = (TRAMStats) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("TRAMStats Not Cloneable");
        }

        if ((senderAddresses != null) && (senderAddresses.size() != 0)) {
            for (i = 0; i < senderAddresses.size(); i++) {
                try {
                    stats.addSender(
			(InetAddress) senderAddresses.elementAt(i));
                } catch (IndexOutOfBoundsException ie) {}
            }
        }
        if ((receiverAddresses != null) && (receiverAddresses.size() != 0)) {
            for (i = 0; i < receiverAddresses.size(); i++) {
                try {
                    stats.addReceiver(
			(InetAddress) receiverAddresses.elementAt(i));
                } catch (IndexOutOfBoundsException ie) {}
            }
        }
		
		stats.setPacketsNotRecovered(packetsNotRecovered);
		stats.setPacketsNotDelivered(packetsNotDelivered);

        return stats;
    }

    /**
     * Adds a sender to the Senders list.
     * 
     * @param address the Address of the sender that is to be added to the
     * sender's list.
     */
    public void addSender(InetAddress address) {
        if (senderAddresses == null) {
            senderAddresses = new Vector();
        } 

        /* TRAM Supports only 1 sender */

        if (senderAddresses.size() != 0) {
            senderAddresses.removeAllElements();
        } 

        senderAddresses.addElement(address);
    }

    /**
     * Removes a sender from the Sender's list.
     * 
     * @param address The address of the sender which is to be removed from
     * the sender's list.
     */
    public void removeSender(InetAddress address) {
        if ((senderAddresses == null) || (senderAddresses.size() == 0)) {
            return;
        } 

        for (int i = 0; i < senderAddresses.size(); i++) {
            try {
                InetAddress tmpAddr = 
                    (InetAddress) senderAddresses.elementAt(i);

                if (address.equals(tmpAddr)) {
                    senderAddresses.removeElement(tmpAddr);

                    return;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }
    }

    /**
     * Adds a receiver to the Receivers list.
     * 
     * @param address The address of the receiver that is to be added to the
     * receiver list.
     */
    public void addReceiver(InetAddress address) {
        if (receiverAddresses == null) {
            receiverAddresses = new Vector();
        } 

        receiverAddresses.addElement(address);
    }

    /**
     * Removes a receiver from the Receiver's list.
     * 
     * @param address The address of the receiver that is to be removed from
     * the list.
     */
    public void removeReceiver(InetAddress address) {
        if ((receiverAddresses == null) || (receiverAddresses.size() == 0)) {
            return;
        } 

        for (int i = 0; i < receiverAddresses.size(); i++) {
            try {
                InetAddress tmpAddr = 
                    (InetAddress) receiverAddresses.elementAt(i);

                if (address.equals(tmpAddr)) {
                    receiverAddresses.removeElement(tmpAddr);

                    return;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }
    }

    /**
     * ***************************************************************
     * 
     * Data Packet Accessor Methods Begin Here
     * 
     * **************************************************************
     */

    /**
     * Gets the number of packets sent.
     * @return the number of packets sent
     */
    public long getPacketsSent() {
        return packetsSent;
    }

    /**
     * Gets the number of data packets received.
     * @return the number of packets received
     */
    public long getPacketsRcvd() {
        return packetsRcvd;
    }

    /**
     * Gets the number of duplicate(redundant) packets received.
     * @return the number of duplicate data packets received.
     */
    public long getDuplicatePackets() {
        return duplicatePackets;
    }

    /**
     * Gets the number of bytes of redundant data received.
     * @return the total number of bytes received in duplicate packets.
     */
    public long getDuplicateBytes() {
        return duplicateBytes;
    }


	/**
	 * Gets the number of packets that were not recovered because they were
	 * before the current forgerBefore value.
	 * @return number of packets that were not recovered.
	 **/
	public long getPacketsNotRecovered() {
		return packetsNotRecovered;
	}

	/**
	 *  Used only by clone() 
	 **/
	public void setPacketsNotRecovered(long notRecovered) {
		packetsNotRecovered = notRecovered;
	}

	/** 
	 * Gets the number of packets that were not delivered to 
	 * the application because they were before the most current 
	 * forgetBefore value.
	 * @return number of packets received but not delivered
	 **/
	public long getPacketsNotDelivered() {
		return packetsNotDelivered;
	}

	/** 
	 * Used only by clone() 
	 **/
	public void setPacketsNotDelivered(long notDelivered) {
		packetsNotDelivered = notDelivered;
	}



    /**
     * Method to augment the number of bytes sent.
     * @param count bytes to add to the total bytes sent count
     */
    public void addBytesSent(long count) {
        bytesSent += count;
    }

    /**
     * Method to augment the number of bytes resent.
     * @param count bytes to add to the total bytes resent count
     */
    public void addBytesReSent(long count) {
        bytesReSent += count;
    }

    /**
     * Increments the number of data packets sent counter.
     * 
     */
    public void addPacketsSent() {
        packetsSent++;
    }

    /**
     * Adds a specified number of packets to the number of packet sent
     * counter.
     * @param count packets to be added to the total packets sent count
     */
    public void addPacketsSent(long count) {
        packetsSent += count;
    }

    /**
     * Adds a specified count of bytes to the bytes received counter.
     * @param count bytes to add to the total bytes received count
     */
    public void addBytesRcvd(long count) {
        bytesRcvd += count;
    }

    /**
     * Adds a packet to the total packets received counter.
     */
    protected void addPacketsRcvd() {
        packetsRcvd++;
    }

    /**
     * Increments the number of packets received counter by 1.
     */
    protected void incPacketsRcvd() {
        packetsRcvd++;
    }

    /**
     * Adds the specified number of packets to the number of received packet
     * counter.
     * @param count packets to add to the total packets received count
     */
    protected void addPacketsRcvd(long count) {
        packetsRcvd += count;
    }

    /**
     * Increments the duplicate packets received counter.
     * 
     */
    protected void addDuplicatePackets() {
        duplicatePackets++;
    }

    /**
     * Adds a specified number of packets to the duplicate packets received
     * counter.
     * @param count the number of packets to add to the total duplicate
     * packet count
     */
    protected void addDuplicatePackets(long count) {
        duplicatePackets += count;
    }

    /**
     * Adds a specified number of bytes to the duplicate bytes received
     * counter.
     * @param count the number of bytes to add to the total duplicate
     * packet byte count
     */
    protected void addDuplicateBytes(long count) {
        duplicateBytes += count;
    }

	/** 
	 * Adds a specified number of packets to the number of packets that were
	 * not recovered because they were before the most current forgetBefore 
	 * value
	 * @param count the number of packets to add to packetsNotRecovered
	 **/
	protected void addPacketsNotRecovered(long count) {
		packetsNotRecovered += count;
	}

	/** 
	 * Adds a specified number of packets to the number of packets that were
	 * received but not delivered to the application.
	 * @param count the number of acplets to add to packetsNotDelivered
	 **/
	protected void addPacketsNotDelivered(long count) {
		packetsNotDelivered += count;
	}


    /**
     * ***************************************************************
     * 
     * Member Statistics Accessor Methods Begin Here
     * 
     * **************************************************************
     */

    /**
     * Returns the maximum number of members tuned to the multicast session.
     * @return the peak number of members for this session.
     * The system must be a head member for this value to be nonZero.
     */
    public int getPeakMembers() {
        GroupMgmtBlk gblk = tramblk.getGroupMgmtBlk();

        return (gblk.getPeakMemberCount());
    }

    /**
     * Returns the number of members that were pruned during the session.
     * @return the number of members pruned during this session.
     * The system must be a head member for this value to be nonZero.
     */
    public int getPrunedMembers() {
        return prunedMembers;
    }

    /**
     * Returns the number of members that were disowned as a result of 
     * inactiveness.
     * @return the number of members who disappeared during the session.
     * The system must be a head member for this value to be nonZero.
     */
    public int getLostMembers() {
        return lostMembers;
    }

    /**
     * Adds one to the pruned member count.
     */
    protected void addPrunedMembers() {
        prunedMembers++;
    }

    /**
     * Adds a specified count of members to the pruned member count.
     * 
     * @param count add count members to the pruned member count.
     */
    protected void addPrunedMembers(int count) {
        prunedMembers += count;
    }

    /**
     * Adds one to the lost member count.
     */
    protected void addLostMembers() {
        lostMembers++;
    }

    /**
     * Adds a specified count of members to the lost member count.
     * 
     * @param count add count members to the lost member count.
     */
    protected void addLostMembers(int count) {
        lostMembers += count;
    }

    /**
     * Adds a specified count of bytes to the retransmitted bytes received 
     * counter.
     * @param count bytes to add to the retransmitted bytes received count
     */
    protected void addRetransBytesRcvd(int count) {
	retransBytesRcvd += count;
    }

    /**
     * Increments the number of retransmission packets sent.
     */
    protected void incRetransSent() {
        retransSent++;
    }

    /**
     * Gets the count of retransmission packets sent.
     * 
     * @return count of retransmission packets sent.
     */
    public long getRetransmissionsSent() {
        return retransSent;
    }

    /**
     * Increments retransmission packets received.
     */
    protected void incRetransRcvd() {
        retransRcvd++;
    }

    /**
     * Gets the count of retransmission packets received.
     * @return count of retransmission packets received.
     */
    public long getRetransmissionsRcvd() {
        return retransRcvd;
    }

    /**
     * Gets the number of retransmitted data bytes received.
     * @return number of retransmitted data bytes received.
     */
    public long getRetransBytesRcvd() {
        return retransBytesRcvd;
    }

    /**
     * Gets the time at which the first data packet was received.
     * @return the time at which the first data packet was received.
     */
    public long getDataStartTime() {
        return dataStartTime;
    }

    /**
     * Sets the time at which the first data packet was received.
     * @param time - the time that is to be set.
     */
    protected void setDataStartTime(long time) {
        dataStartTime = time;
    }

    /**
     * Set the appropriate counters for control messages
     * which are received.
     */
    public void setRcvdCntlMsgCounters(TRAMPacket pkt) {
	switch (pkt.getMessageType()) {
	case MESGTYPE.MCAST_CNTL:
	    mcastCntlBytesRcvd += pkt.getLength();

	    switch (pkt.getSubType()) {
	    case SUBMESGTYPE.BEACON:
		mcastBeaconRcvd++;
		break;

	    case SUBMESGTYPE.HELLO:
		mcastHelloRcvd++;
		break;

	    case SUBMESGTYPE.HA:
		mcastHARcvd++;
		break;

	    case SUBMESGTYPE.MS:
		mcastMSRcvd++;
		break;
	    }

	    break;

	case MESGTYPE.UCAST_CNTL: 
	    ucastCntlBytesRcvd += pkt.getLength();

	    switch (pkt.getSubType()) {
	    case SUBMESGTYPE.AM:
		ucastAMRcvd++;
		break;

	    case SUBMESGTYPE.RM:
		ucastRMRcvd++;
		break;

	    case SUBMESGTYPE.HELLO_Uni:
		ucastHelloRcvd++;
		break;

	    case SUBMESGTYPE.ACK:
	        if ((pkt.getFlags() & TRAMAckPacket.FLAGBIT_ACK) != 0)
		    ucastACKRcvd++;
		
	        if ((pkt.getFlags() & TRAMAckPacket.FLAGBIT_CONGESTION) != 0)
		    ucastCongRcvd++;

		break;

	    case SUBMESGTYPE.HB:
		ucastHBRcvd++;
		break;
	    }

	    break;

	default:
	    break;
	}
    }

    /**
     * Set the appropriate counters for control messages
     * which are sent.
     */
    public void setSendCntlMsgCounters(TRAMPacket pkt) {
	switch (pkt.getMessageType()) {
	case MESGTYPE.MCAST_CNTL:
	    mcastCntlBytesSent += pkt.getLength();

	    switch (pkt.getSubType()) {
	    case SUBMESGTYPE.BEACON:
		mcastBeaconSent++;
		break;

	    case SUBMESGTYPE.HELLO:
		mcastHelloSent++;
		break;

	    case SUBMESGTYPE.HA:
		mcastHASent++;
		break;

	    case SUBMESGTYPE.MS:
		mcastMSSent++;
		break;
	    }

	    break;

	case MESGTYPE.UCAST_CNTL:
	    ucastCntlBytesSent += pkt.getLength();

	    switch (pkt.getSubType()) {
	    case SUBMESGTYPE.AM:
		ucastAMSent++;
		break;

	    case SUBMESGTYPE.RM:
		ucastRMSent++;
		break;

	    case SUBMESGTYPE.HELLO_Uni:
		ucastHelloSent++;
		break;

	    case SUBMESGTYPE.ACK:
	        if ((pkt.getFlags() & TRAMAckPacket.FLAGBIT_ACK) != 0)
		    ucastACKSent++;

	        if ((pkt.getFlags() & TRAMAckPacket.FLAGBIT_CONGESTION) != 0)
		    ucastCongSent++;

		break;

	    case SUBMESGTYPE.HB:
		ucastHBSent++;
		break;
	    }

	    break;

	default:
	    break;
	}
    }

    /**
     * Get the number of multicast control bytes sent.
     */
    public long getMcastControlBytesSent() {
	return mcastCntlBytesSent;
    }

    /**
     * Get the number of multicast control bytes received.
     */
    public long getMcastControlBytesRcvd() {
	return mcastCntlBytesRcvd;
    }

    /**
     * Get the number of multicast beacons sent.
     */
    public long getMcastBeaconSent() {
	return mcastBeaconSent;
    }

    /**
     * Get the number of multicast beacons received.
     */
    public long getMcastBeaconRcvd() {
	return mcastBeaconRcvd;
    }

    /**
     * Get the number of multicast hellos sent.
     */
    public long getMcastHelloSent() {
	return mcastHelloSent;
    }

    /**
     * Get the number of multicast hellos received.
     */
    public long getMcastHelloRcvd() {
	return mcastHelloRcvd;
    }

    /**
     * Get the number of multicast HA messages sent.
     */
    public long getMcastHASent() {
	return mcastHASent;
    }

    /**
     * Get the number of multicast HA messages received.
     */
    public long getMcastHARcvd() {
	return mcastHARcvd;
    }

    /**
     * Get the number of multicast MS messages sent.
     */
    public long getMcastMSSent() {
	return mcastMSSent;
    }

    /**
     * Get the number of multicast MS messages received.
     */
    public long getMcastMSRcvd() {
	return mcastMSRcvd;
    }

    /**
     * Get the number of unicast control bytes sent.
     */
    public long ucastCntlBytesSent() {
	return ucastCntlBytesSent;
    }

    /**
     * Get the number of unicast control bytes received.
     */
    public long ucastCntlBytesRcvd() {
	return ucastCntlBytesRcvd;
    }

    /**
     * Get the number of unicast AM messages sent.
     */
    public long ucastAMSent() {
	return ucastAMSent;
    }

    /**
     * Get the number of unicast AM messages received.
     */
    public long ucastAMRcvd() {
	return ucastAMRcvd;
    }

    /**
     * Get the number of unicast RM Messages sent.
     */
    public long ucastRMSent() {
	return ucastRMSent;
    }

    /**
     * Get the number of unicast RM Messages received.
     */
    public long ucastRMRcvd() {
	return ucastRMRcvd;
    }

    /**
     * Get the number of unicast Hello's sent.
     */
    public long ucastHelloSent() {
	return ucastHelloSent;
    }

    /**
     * Get the number of unicast Hello's received.
     */
    public long ucastHelloRcvd() {
	return ucastHelloRcvd;
    }

    /**
     * Get the number of unicast ACK's sent.
     */
    public long ucastACKSent() {
	return ucastACKSent;
    }

    /**
     * Get the number of unicast ACK's received.
     */
    public long ucastACKRcvd() {
	return ucastACKRcvd;
    }

    /**
     * Get the number of unicast congestion messages sent.
     */
    public long ucastCongSent() {
	return ucastCongSent;
    }

    /**
     * Get the number of unicast congestion messages received.
     */
    public long ucastCongRcvd() {
	return ucastCongRcvd;
    }

    /**
     * Get the number of unicast HB messages sent.
     */
    public long ucastHBSent() {
	return ucastHBSent;
    }

    /**
     * Get the number of unicast HB messages received.
     */
    public long ucastHBRcvd() {
	return ucastHBRcvd;
    }

}
