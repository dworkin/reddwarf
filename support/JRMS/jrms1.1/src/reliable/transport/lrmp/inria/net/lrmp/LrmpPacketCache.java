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
 * LrmpPacketCache.java - a pool of buffered lrmp packets.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 16 June 1998.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;

/**
 * a cyclic cache table of buffered lrmp packets, designed for efficient cache.
 */
final class LrmpPacketCache {
    LrmpPacket buffer[];
    int mask;

    /**
     * constructs an LrmpPacketCache. The cache size should be multiple of
     * two (2^n).
     */
    public LrmpPacketCache(int size) {
        for (mask = 1; mask < size; ) {
            mask = mask << 1;
        }

        buffer = new LrmpPacket[mask];

        clear();

        mask--;
    }

    /**
     * returns the maximum size of the cache.
     */
    public int getMaxSize() {
        return mask + 1;
    }

    /**
     * adds the given packet to the queue.
     * @param obj the packet to be added.
     */
    public void addPacket(LrmpPacket obj) {
        int i = (int) (obj.seqno & mask);

        buffer[i] = obj;
    }

    /**
     * contains the packet.
     * @param seqno the packet seqno.
     */
    public boolean containPacket(long seqno) {
        int i = (int) (seqno & mask);

        if (buffer[i] != null) {
            return buffer[i].seqno == seqno;
        } 

        return false;
    }

    /**
     * gets the packet corresponding to the given seqno.
     * @param seqno the packet seqno.
     */
    public LrmpPacket getPacket(long seqno) {
        int i = (int) (seqno & mask);

        if (buffer[i] != null && buffer[i].seqno == seqno) {
            return buffer[i];
        } 

        return null;
    }

    /**
     * remove the given packet from the queue.
     * @param obj the packet to be removed.
     */
    public void removePacket(LrmpPacket obj) {
        int i = (int) (obj.seqno & mask);

        if (buffer[i] != null && buffer[i].seqno == obj.seqno) {
            buffer[i] = null;
        } 
    }

    /**
     * remove the packet with the given seqno from the queue.
     * @param seqno the packet seqno.
     */
    public void removePacket(long seqno) {
        int i = (int) (seqno & mask);

        if (buffer[i] != null && buffer[i].seqno == seqno) {
            buffer[i] = null;
        } 
    }

    /**
     * remove the packet with the given seqno from the queue.
     * @param seqno the packet seqno.
     */
    public void clear() {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = null;
        }
    }

}

