/*
 * COPYRIGHT 1995 BY: MASSACHUSETTS INSTITUTE OF TECHNOLOGY (MIT), INRIA
 * 
 * This W3C software is being provided by the copyright holders under the
 * following license. By obtaining, using and/or copying this software, you
 * agree that you have read, understood, and will comply with the following
 * terms and conditions:
 * 
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose and without fee or royalty is hereby granted,
 * provided that the full text of this NOTICE appears on ALL copies of the
 * software and documentation or portions thereof, including modifications,
 * that you make.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO
 * REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT
 * NOT LIMITATION, COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE
 * SOFTWARE OR DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS,
 * COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. COPYRIGHT HOLDERS WILL BEAR NO
 * LIABILITY FOR ANY USE OF THIS SOFTWARE OR DOCUMENTATION.
 * 
 * The name and trademarks of copyright holders may NOT be used in advertising
 * or publicity pertaining to the software without specific, written prior
 * permission. Title to copyright in this software and any associated
 * documentation will at all times remain with copyright holders.
 */

/*
 * LrmpPacketQueue.java - ordered queue of lrmp packets.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 16 June 1998.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;
import inria.util.Logger;

/**
 * ordered queue of lrmp packets. Sequence number may not be unique for
 * resend queue due to many sources in case of local recovery.
 */
final class LrmpPacketQueue {
    static int defalutIncrements = 8;
    int increments;
    LrmpPacket table[];
    int count;

    /**
     * constructs an LrmpPacketQueue.
     */
    public LrmpPacketQueue() {
        this(34, defalutIncrements);
    }

    /**
     * constructs an LrmpPacketQueue.
     * @param initialCapacity the initial capacity.
     * @param increments the increments for expanding the table.
     */
    public LrmpPacketQueue(int initialCapacity, int increments) {
        if (increments <= 0) {
            this.increments = 2;
        } else {
            this.increments = increments;
        }
        if (initialCapacity <= 0) {
            initialCapacity = this.increments;
        } 

        table = new LrmpPacket[initialCapacity];

        clear();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }

        count = 0;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public int size() {
        return count;
    }

    /**
     * adds the given packet to the queue.
     * @param obj the packet to be added.
     */
    public void enqueue(LrmpPacket obj) {
        count++;

        int size = table.length;

        for (int i = 0; i < size; i++) {
            if (table[i] == null) {
                table[i] = obj;

                return;
            }
        }

        table = expand(table);
        table[size] = obj;
    }

    /**
     * contains the packet.
     * @param seqno the packet seqno.
     */
    public boolean contains(long seqno) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null && table[i].seqno == seqno) {
                return true;
            } 
        }

        return false;
    }

    /**
     * contains the packet.
     * @param seqno the packet.
     */
    public boolean contains(LrmpPacket pack) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null && table[i] == pack) {
                return true;
            } 
        }

        return false;
    }

    /**
     * gets the packet.
     */
    public LrmpPacket dequeue() {
        int found = -1;

        for (int i = 0; i < table.length; i++) {
            if (table[i] != null) {
                if (found < 0 || table[i].seqno < table[found].seqno) {
                    found = i;
                } 
            }
        }

        if (found >= 0) {
            LrmpPacket pack = table[found];

            table[found] = null;
            count--;

            return pack;
        }

        return null;
    }

    /**
     * remove the given packet from the queue.
     * @param obj the packet to be removed.
     */
    public void remove(LrmpPacket obj) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null && obj == table[i]) {
                table[i] = null;
                count--;

                return;
            }
        }
    }

    /**
     * remove the packet with the given seqno from the queue.
     * @param seqno the packet seqno.
     */
    public void remove(LrmpSender s, long seqno, int scope) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null) {
                if (s == table[i].source && seqno == table[i].seqno 
                        && scope >= table[i].scope) {
                    table[i] = null;
                    count--;

                    if (Logger.debug) {
                        Logger.debug(this, 
                                     "cancel resend " + seqno + " " + count);
                    } 

                    return;
                }
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     * @param id
     * @param scope
     *
     * @see
     */
    public void cancel(LrmpSender s, int id, int scope) {
        for (int i = 0; i < table.length; i++) {
            if (table[i] != null) {
                if (s == table[i].source && id == table[i].retransmitID 
                        && scope >= table[i].scope) {
                    if (Logger.debug) {
                        Logger.debug(this, 
                                     "cancel resend " + table[i].seqno + " " 
                                     + count);
                    } 

                    table[i] = null;
                    count--;
                }
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param tab
     *
     * @return
     *
     * @see
     */
    protected LrmpPacket[] expand(LrmpPacket[] tab) {
        LrmpPacket[] newtab = new LrmpPacket[tab.length + increments];

        for (int i = 0; i < tab.length; i++) {
            newtab[i] = tab[i];
        }

        return newtab;
    }

}

