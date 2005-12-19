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
 * PacketDb.java
 */
package com.sun.multicast.reliable.transport.tram;

import java.util.*;
import java.io.*;

/**
 * The PacketDb is the TRAM packet database manager. Provides methods
 * add, get, seek and remove packets from the database.
 * 
 */
class PacketDb implements TRAMInputOutput {

    // constants.

    private static final int DB_INCREMENTS = 100;

    // private variables

    private Vector db = null;
    private Vector retxDb = null;  // separate vector for retransmissions
    private long dataSize = 0;
    private int capacity = 0;
    private TRAMControlBlock tramblk;
    boolean inbound = false;
    boolean shutdown = false;
    // private int         packetCount = 0;

    /**
     * Create a packet database with the default initial capacity
     */
    public PacketDb(TRAMControlBlock tramblk) {
	this.tramblk = tramblk;
        db = new Vector();
	retxDb = new Vector();
    }

    /**
     * Create a new packet database with the specified initial capacity.
     */
    public PacketDb(TRAMControlBlock tramblk, int capacity) {
	this.tramblk = tramblk;
        this.capacity = capacity;
        db = new Vector();
	retxDb = new Vector();
    }

    /**
     * Returns the first element in the database. The element
     * is located and removed from the database.
     * 
     * @return the first TRAMPacket in the database.
     * @exception NoSuchElementExcetion if the database is empty.
     */
    public synchronized TRAMPacket getPacket() {
        TRAMPacket pk = null;
        while (shutdown == false) {
	    if (inbound == false) {
		/*
		 * If the db is not for the inbound side, then check the
		 * retxDb first as the node might have put in packets for
		 * retransmission which needs to go out first.
		 * If the Db is for the inbound side, then there is only
		 * one Db which will have the data packets in the ordered
		 * manner in which they were sent by the sender(if the 
		 * ordering option is chosen).
		 */
		if (retxDb.size() > 0) {
		    /*
		     * check the retransmit database first.
		     */
		    pk = (TRAMPacket)retxDb.firstElement();
		    retxDb.removeElementAt(0);
		    dataSize -= pk.getLength();
		    notify();
		    return pk;
		}
	    }

            try {
                pk = (TRAMPacket)db.firstElement();

                db.removeElementAt(0);

                dataSize -= pk.getLength();

                // --packetCount;

                notifyAll();
                return pk;

            } catch (NoSuchElementException e) {
                try {
                    wait();
                } catch (InterruptedException ie) {}
            }
        }
        pk = null;
	return pk;
    }

    /**
     * Returns the first element in the database. The element
     * is located and removed from the database.
     * 
     * @return the first TRAMPacket in the database.
     * @exception NoSuchElementExcetion if the database is empty.
     */
    public synchronized TRAMPacket getPacketWithNoBlocking() 
            throws NoSuchElementException {
	TRAMPacket pk = null;

	/*
	 * Check the retxDb only if the Db is for the outbound side.
	 * No retxDb is used for the inbound side as it messes up the 
	 * ordering performed by a lower module.
	 */
	if (inbound == false) {
	    if (retxDb.size() > 0) {
		pk = (TRAMPacket) retxDb.firstElement();
		retxDb.removeElementAt(0);
	    }
	}
	if (pk == null) {
	    pk = (TRAMPacket) db.firstElement();
	    db.removeElementAt(0);
	}
	dataSize -= pk.getLength();
	
        notifyAll();

        return pk;
    }

    public synchronized void terminate() {

	   shutdown = true;
	   notifyAll();
    }
    /**
     * Returns the element with the specified sequenceNumber in
     * the database. The element is located and removed from the database.
     * 
     * @param sequenceNumber the TRAM sequence number of the TRAMDataPacket
     * @return the TRAMDataPacket in the database with the specified
     * sequenceNumber
     * @exception NoSuchElementExcetion if desired packet is not in the
     * databse.
     */
    public TRAMDataPacket getPacket(long sequenceNumber) 
            throws NoSuchElementException {
        return getPacket(sequenceNumber, false);
    }

    /**
     * Returns the element with the specified sequenceNumber in
     * the database. The element left in the database if the keep
     * parameter is set to true.
     * 
     * @param sequenceNumber the tram sequence number of the TRAMDataPacket
     * @param keep if set to true the element remains in the database. If set
     * to false it is removed.
     * @return the TRAMDataPacket in the database with the specified
     * sequenceNumber
     * @exception NoSuchElementExcetion if desired packet is not in
     * the databse.
     */
    public synchronized TRAMDataPacket getPacket(long sequenceNumber, 
	boolean keep) throws NoSuchElementException {

        TRAMDataPacket pk = null;
	/*
	 * Check the retxDb only if the Db is for the outbound side.
	 * No retxDb is used for the inbound side as it messes up the 
	 * ordering performed by a lower module.
	 */
	if (inbound == false) {
	    pk = (TRAMDataPacket) findPacket(retxDb, sequenceNumber);
	}
	if (pk != null) {
            if (!keep)
                retxDb.removeElement(pk);
	} else {
            pk = (TRAMDataPacket) findPacket(db, sequenceNumber);

	    if (pk == null)
                throw new NoSuchElementException();

            if (!keep) {
                db.removeElement(pk);

                dataSize -= pk.getLength();

                // --packetCount;
	    }
	}

        notifyAll();
        return pk;
    }

    /**
     * Add an TRAMPacket to the database. If a thread is waiting
     * for the packet, resume the suspended thread.
     * 
     * @param tramPacket the TRAMPacket to add
     */
    public synchronized void putPacket(TRAMPacket tramPacket) {

	/*
	 * Add to the retxDb only if the Db is for the outbound side.
	 * No retxDb is used for the inbound side as it messes up the 
	 * ordering performed by a lower module.
	 */
	if (inbound == false) {
	    if (tramPacket.getMessageType() == MESGTYPE.MCAST_DATA &&
		tramPacket.getSubType() == SUBMESGTYPE.DATA_RETXM) {
		retxDb.addElement(tramPacket);
        	dataSize += tramPacket.getLength();

		notify();
		return;
	    }
	}

        while (capacity != 0 && db.size() > capacity) {
            try {
                wait();
            } catch (InterruptedException ie) {}
        }

        db.addElement(tramPacket);
        dataSize += tramPacket.getLength();

        // packetCount++;

        notify();
    }

    /**
     * Add an TRAMPacket to the begining of the database. If a thread is waiting
     * for the packet, resume the suspended thread.
     * 
     * @param tramPacket the TRAMPacket to add
     */
    public synchronized void putPacket(TRAMPacket tramPacket, 
                                       boolean priority) {
	/*
	 * Add to the retxDb only if the Db is for the outbound side.
	 * No retxDb is used for the inbound side as it messes up the 
	 * ordering performed by a lower module.
	 */
	if (inbound == false) {
	    if (tramPacket.getMessageType() == MESGTYPE.MCAST_DATA &&
		tramPacket.getSubType() == SUBMESGTYPE.DATA_RETXM) {
		retxDb.addElement(tramPacket);
        	dataSize += tramPacket.getLength();
		notify();
		return;
	    }
	}

        if (priority == true) {
            db.addElement(tramPacket);
        } else {
            if ((capacity != 0) && (db.size() > capacity)) {
                try {
                    wait();
                } catch (InterruptedException ie) {}
            }

            db.addElement(tramPacket);
        }

        dataSize += tramPacket.getLength();

        // packetCount++;

        notify();
    }

    /**
     * Removes a TRAMDataPacket from the database. The datasize of the
     * database is decremented by the length of the TRAMDataPacket. If
     * matching TRAMDataPacket is not found, the method just returns.
     * 
     * @param	TRAMDataPacket sequence number.
     */
    public synchronized void removePacket(long sequenceNumber) 
            throws NoSuchElementException {
        TRAMDataPacket pk = getPacket(sequenceNumber);
    }

    /**
     * Gets the current datasize of the database. This information is
     * used by TRAM modules like the congestion control which monitor
     * the database size.
     * 
     * @return current datasize of the database.
     */
    public long getDataSize() {
        return dataSize;
    }

    /**
     * @return the number of packets currently in the database.
     */
    public int getPacketCount() {
	if (inbound == false) {
	    return db.size() + retxDb.size();
	}
	return db.size();
    }

    /**
     * Locate a packet in the database with the specified sequenceNumber.Return
     * the index if found and throw an exception if not. Since TRAMDataPackets
     * are the only packets with sequence numbers, this method ignores all
     * packets that are not TRAMDataPackets in the database.
     * 
     * @param sequenceNumber the TRAMDataPacket sequence number of the
     * desired packet.
     * @return the index of the desired packet
     * @exception NoSuchElementException if the packet does not exist in the
     * database.
     */
    private TRAMPacket findPacket(Vector v, long sequenceNumber) {
        TRAMPacket pk = null;

        for (int i = 0; i < v.size(); i++) {
            try {
                pk = (TRAMPacket) v.elementAt(i);

                if ((pk.getMessageType() == MESGTYPE.MCAST_DATA) 
                        && (((TRAMDataPacket) pk).getSequenceNumber() 
                            == sequenceNumber)) {
                    return pk;
                }
            } catch (IndexOutOfBoundsException ie) {}
        }

	return null;
    }

    /**
     * Returns the lowest sequence number found in the database.
     * @return 0 if the database is empty or the lowest sequence
     * number currently found in the database
     */
    public int getLowestSeqNumber() {
        TRAMSeqNumber lowSeqNumber = null;
        TRAMPacket pkt = null;
        boolean validLowSeqNumber = false;

        for (int i = 0; i < db.size(); i++) {
            try {
                pkt = (TRAMPacket) db.elementAt(i);

                if ((pkt.getMessageType() == MESGTYPE.MCAST_DATA)) {
                    int seqNumTmp = ((TRAMDataPacket) pkt).getSequenceNumber();

                    if (validLowSeqNumber == false) {
                        lowSeqNumber = new TRAMSeqNumber(seqNumTmp);
                        validLowSeqNumber = true;
                    } else {
                        if (lowSeqNumber.compareSeqNumber(seqNumTmp) > 0) {
                            lowSeqNumber = new TRAMSeqNumber(seqNumTmp);
                        }
                    }
                }
            } catch (IndexOutOfBoundsException ie) {}
        }

        if (validLowSeqNumber == false) {
            return 0;
        } 

        return lowSeqNumber.getSeqNumber();
    }

    /**
     * Returns the highest sequence number found in the database.
     * @return 0 if the database is empty or the highest sequence
     * number currently found in the database
     */
    public int getHighestSeqNumber() {
        TRAMSeqNumber highSeqNumber = null;
        TRAMPacket pkt = null;
        boolean validHighSeqNumber = false;

        for (int i = 0; i < db.size(); i++) {
            try {
                pkt = (TRAMPacket) db.elementAt(i);

                if ((pkt.getMessageType() == MESGTYPE.MCAST_DATA)) {
                    int seqNumTmp = ((TRAMDataPacket) pkt).getSequenceNumber();

                    if (validHighSeqNumber == false) {
                        highSeqNumber = new TRAMSeqNumber(seqNumTmp);
                        validHighSeqNumber = true;
                    } else {
                        if (highSeqNumber.compareSeqNumber(seqNumTmp) < 0) {
                            highSeqNumber = new TRAMSeqNumber(seqNumTmp);
                        }
                    }
                }
            } catch (IndexOutOfBoundsException ie) {}
        }

        if (validHighSeqNumber == false) {
            return 0;
        } 

        return highSeqNumber.getSeqNumber();
    }

    /**
     * gets the inbound DB flag value.
     */
    public boolean getInbound() {
	return inbound;
    }

    /**
     * sets the inbound DB flag to the required value.
     */
    public void setInbound(boolean val) {
	inbound = val;
    }

    /**
     * gets the number of packets in the retransmit queue
     */
    public int getRetxDbSize() {
	return retxDb.size();
    }

}



