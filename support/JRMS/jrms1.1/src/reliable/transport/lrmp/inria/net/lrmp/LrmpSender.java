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
 * LrmpSender.java - LRMP sender.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.InetAddress;
import java.util.*;
import inria.util.NTP;

/**
 * An LRMP sender is an LRMP entity that sends data packets. It is created and
 * managed internally by Lrmp.
 */
public final class LrmpSender extends LrmpEntity {
    protected long startseq;                /* initial seqno received */
    protected long maxseq;                  /* the highest seqno seen */
    protected long expected;        /* the expected seqno for the next packet */
    protected long lastseq;                 /* last seqno received */
    protected long lastError;       /* seqno at which last reception failure occurred */
    protected LrmpPacketCache cache = null;
    protected int cacheSize = 128;
    protected boolean lost = false;
    protected long lastTimeForData;

    /* sender report */

    protected int srPackets;
    protected int srBytes;
    protected long srSeqno;
    protected int srTimestamp;
    protected long nextSRTime;

    /* receiver report selection */

    protected int rrProb = 65536 / 10;      /* in fractions of 1/65536 */
    protected int rrInterval = 10;
    protected int rrTimestamp;
    protected long rrSelectTime;
    protected int rrReplies;
    protected long nextRRTime;

    /* receiver report (local info) */

    protected int rrAbsLost;
    protected long rrMaxSeqno;

    /* stats */

    protected int packets;                  /* packets received */
    protected int bytes;                    /* bytes received */
    protected int duplicates;               /* duplicate packets */
    protected int repairs;                  /* repair packets */
    protected int drops;                    /* (bad) packets dropped */
    protected int rate;                     /* bytes/sec */
    protected int interval;                 /* mean packet interval in millis */
    protected int transit;                  /* transit time */
    protected int jitter;                   /* interarrival jitter */

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param id
     * @param netaddr
     * @param start
     *
     * @see
     */
    protected LrmpSender(int id, InetAddress netaddr, long start) {
        super(id, netaddr);

        reset(start);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param initialSeqno
     *
     * @see
     */
    protected void reset(long initialSeqno) {
        super.reset();

        lastError = 0;
        packets = 0;
        bytes = 0;
        rate = 0;

        /* default to 1 kilo byte packets at 128 kbps */

        interval = 64;
        transit = 0;
        jitter = 0;
        lastTimeForData = 0L;
        srTimestamp = 0;
        nextSRTime = 0;
        nextRRTime = 0;
        duplicates = 0;
        repairs = 0;
        drops = 0;

        clearCache(initialSeqno);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param cacheSize
     *
     * @see
     */
    protected void initCache(int cacheSize) {
        cache = new LrmpPacketCache(cacheSize);
        this.cacheSize = cache.getMaxSize();
    }

    /**
     * Returns the number of packets sent or received.
     */
    public int getPacketCount() {
        return packets;
    }

    /**
     * Returns the number of duplicate packets received.
     */
    public int getDuplicateCount() {
        return duplicates;
    }

    /**
     * Returns the number of repair packets sent or received.
     */
    public int getRepairCount() {
        return repairs;
    }

    /**
     * Returns the number of bad packets received.
     */
    public int getBadPacketCount() {
        return drops;
    }

    /**
     * Returns the number of bytes sent or received.
     */
    public int getByteCount() {
        return bytes;
    }

    /**
     * Returns the current date rate.
     */
    public int getDataRate() {
        return rate;
    }

    /**
     * Returns the last time sent data.
     */
    public long getTimeSentData() {
        return lastTimeForData;
    }

    /**
     * Returns the packet interarrival jitter in milliseconds.
     */
    public int getJitter() {
        return jitter;
    }

    /**
     * Returns true if the sender is lost.
     */
    public boolean isLost() {
        return lost;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param initialSeqno
     *
     * @see
     */
    protected void clearCache(long initialSeqno) {
        startseq = initialSeqno;
        maxseq = initialSeqno - 1;
        expected = initialSeqno;
        lastseq = maxseq;
        rrAbsLost = 0;
        rrMaxSeqno = maxseq;

        if (cache != null) {
            cache.clear();
        } 
    }

    /**
     * Returns the highest sequence number seen from the sender.
     */
    public long highestSeqnoGot() {
        return maxseq;
    }

    /**
     * Returns the next sequence number that will be used in transmission or
     * reception.
     */
    public long expected() {
        return expected;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param n
     *
     * @see
     */
    protected void highestSeqnoGot(long n) {
        maxseq = n;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void incPackets() {
        packets++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void incDuplicate() {
        duplicates++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void incRepairs() {
        repairs++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void incDrops() {
        drops++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param n
     *
     * @see
     */
    protected void incBytes(int n) {
        bytes += n;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void incExpected() {
        expected++;
        expected = expected % LrmpImpl.Modulo32;
    }

    /**
     * sets the actual data rate in bytes/sec.
     * @param r the data rate.
     */
    protected void setRate(int r) {
        rate = r;
    }

    /**
     * sets the actual packet interval.
     * @param i the interval.
     */
    protected void setInterval(int i) {

        /*
         * avoid the idle effect, i.e., restart after a long idle time.
         */
        if (i < 1000) {

            /* i = 0.75i + 0.25new */

            interval = (3 * interval + i) >> 2;
        }
    }

    /**
     * appends the packet at the end of cache.
     * @param pack the packet to cache.
     */
    protected void appendPacket(LrmpPacket pack) {
        cache.addPacket(pack);
    }

    /**
     * puts the packet into cache. XXXX must take into account the 32 bit round off.
     * @param pack packet to cache.
     */
    protected void putPacket(LrmpPacket pack) {
        cache.addPacket(pack);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     *
     * @see
     */
    protected void removePacket(LrmpPacket pack) {
        cache.removePacket(pack);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param seqno
     *
     * @return
     *
     * @see
     */
    protected LrmpPacket getPacket(long seqno) {
        return cache.getPacket(seqno);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param seqno
     *
     * @return
     *
     * @see
     */
    protected boolean isCached(long seqno) {
        return cache.containPacket(seqno);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param timestamp
     *
     * @see
     */
    protected void updateJitter(int timestamp) {
        int elapsed = NTP.ntp32(lastTimeForData - timestamp);

        elapsed = NTP.fixedPoint32ToMillis(elapsed);

        int d;

        if (transit != 0) {
            d = elapsed - transit;
        } else {
            d = 0;
        }

        transit = elapsed;

        if (d < 0) {
            d = -d;
        } 

        jitter += d - ((jitter + 8) >> 4);
    }

}

