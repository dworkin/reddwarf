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
 * LrmpImpl.java - implementation of Light-weight Reliable Multicast Protocol.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.*;
import java.io.*;
import java.util.*;
import inria.net.MulticastSession;
import inria.util.EventHandler;
import inria.util.EventManager;
import inria.util.Logger;
import inria.util.NTP;
import inria.util.Utilities;

/**
 * implements the Light-weight Reliable Multicast Protocol.
 * As a general implementation rule, don't trust anything received from a
 * multicast socket.
 */
final class LrmpImpl extends MulticastSession implements EventHandler {
    public static final int Version = 1;
    public static final long Modulo32 = (1L << 32);

    /* packet types */

    public static final int DATA_PT = 0;
    public static final int R_DATA_PT = 4;
    public static final int U_DATA_PT = 8;
    public static final int F_DATA_PT = 12;
    public static final int NACK_PT = 17;
    public static final int R_NACK_PT = 18;
    public static final int SR_PT = 19;
    public static final int RS_PT = 20;
    public static final int RR_PT = 21;
    protected static final int checkInterval = 10000;
    protected LrmpContext cxt;
    private Object event = null;
    private Vector reports;
    int lastDataBytes = 0;
    int lastCtrlBytes = 0;
    long lastUpdateTime = 0;
    long nextTimeout = 0;
    int idleTime = 1000;
    Random rand;

    /**
     * creates an LRMP multicast session with the specified group, port and TTL.
     * @param addr the destination address.
     * @param port the port to use.
     * @param ttl the time to live value.
     * @param prof the profile to use.
     * @exception LrmpException is raised if there is an error in joining or
     * bad profile.
     */
    public LrmpImpl(InetAddress addr, int port, int ttl, 
                    LrmpProfile prof) throws LrmpException {
        this(addr, port, prof);

        setTTL(ttl);
    }

    /**
     * creates an LRMP unicast session with the specified address and port.
     * @param addr the destination address.
     * @param port the port to use.
     * @param prof the profile to use.
     * @exception LrmpException is raised if there is an error in creating socket or
     * bad profile.
     */
    public LrmpImpl(InetAddress addr, int port, 
                    LrmpProfile prof) throws LrmpException {
        super();

        try {
            initialize(addr, port);
        } catch (IOException e) {
            throw new LrmpException(e.toString());
        }

        cxt = new LrmpContext();
        cxt.lrmp = this;
        cxt.sm = new LrmpEntityManager();
        cxt.whoami = cxt.sm.whoami();
        cxt.stats = new LrmpStats();
        cxt.sender = new LrmpFlow(cxt);
        reports = new Vector();
        MaxPacketSize = LrmpPacket.MTU;

        cxt.setProfile(prof);

        lastUpdateTime = System.currentTimeMillis();
        rand = new Random();
    }

    /**
     * sets the profile.
     * @param prof profile to use.
     * @exception LrmpException is raised if this is a bad profile.
     */
    public void setProfile(LrmpProfile prof) throws LrmpException {
        cxt.setProfile(prof);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public LrmpSender whoami() {
        return cxt.whoami;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void startSession() {

        /* setup timer manager */

        if (cxt.timer == null) {
            initTimer();
        }
        if (cxt.recover == null) {
            initRecover();
        } 

        /* start to run */

        start();
        startTimer(checkInterval);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private synchronized static void initTimer() {
        LrmpContext.timer = EventManager.shared();

        if (!LrmpContext.timer.isAlive()) {
            LrmpContext.timer.setDaemon(true);
            LrmpContext.timer.start();
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private void initRecover() {
        if (cxt.recover != null) {
            cxt.recover.stop();
        } 

        cxt.recover = new LrmpRecovery(ttl, cxt);
    }

    /**
     * stops the session.
     */
    public void stopSession() {
        if (event != null) {
            cxt.timer.recallTimer(event);

            event = null;
        }

        cxt.recover.stop();
        cxt.sender.stop();
        super.stop();
    }

    /**
     * sends a data packet to the session.
     * @param pack the packet to send.
     */
    public void send(LrmpPacket pack) {
        if (pack.isReliable() && cxt.whoami.lastTimeForData == 0) {
            sendSenderReport();
            cxt.whoami.initCache(cxt.profile.sendWindowSize);

            cxt.whoami.lastTimeForData = System.currentTimeMillis();
        }

        cxt.sender.enqueue(pack);

        if (idleTime > 0) {
            idleTime = 0;
            cxt.whoami.nextSRTime = System.currentTimeMillis() 
                                    + cxt.senderReportInterval;

            startTimer(cxt.senderReportInterval);
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void flush() {
        cxt.sender.flush();
    }

    /**
     * parses a received data/control packet.
     * @param buff the data buffer.
     * @param total_len the total length of data in the buffer.
     * @param netaddr the sender network address.
     * @retrun true if the buffer can be reused; false otherwise.
     */
    protected boolean parse(byte buff[], int totalLen, InetAddress netaddr) {

        /*
         * validity check.
         */
        if (totalLen < 12) {
            cxt.stats.badLength++;

            if (Logger.debug) {
                Logger.debug(this, 
                             "packet too short (" + totalLen + ") " 
                             + netaddr.getHostAddress());
            } 

            return true;
        }

        int k = (buff[0] & 0xff) >> 6;

        if (k != Version) {
            cxt.stats.badVersion++;

            if (Logger.debug) {
                Logger.debug(this, 
                             "incorrect version (" + k + ") " 
                             + netaddr.getHostAddress());
            } 

            return true;
        }

        /* entity ID */

        k = Utilities.byteToInt(buff, 4);

        /* ignore loopback packets */

        if (cxt.whoami.getID() == k 
                && cxt.whoami.getAddress().equals(netaddr)) {
            return true;
        } 

        LrmpEntity s = cxt.sm.lookup(k, netaddr);

        if (s == null) {

            /*
             * refused...
             */
            Logger.error(this, 
                         "rejected packet from " + Integer.toHexString(k) 
                         + "@" + netaddr.getHostAddress());

            return true;
        }

        /* a rough estimate of distance XXX */

        k = buff[1] & 0xff;

        if (s.distance > k) {
            s.distance = k;
        } 

        /*
         * loop to process all multiplexed packets.
         */
        int offset = 0;
        boolean reuse = true;

        while (offset < totalLen) {
            int len = ((buff[offset + 2] & 0xff) << 8) 
                      | (buff[offset + 3] & 0xff);

            if (len < 12 || (len + offset) > totalLen) {
                cxt.stats.badLength++;

                Logger.error(this, "bad packet length " + len);

                break;
            }

            /* packet type */

            k = buff[offset] & 0x1f;

            if (k >= 16) {
                cxt.stats.ctrlPackets++;
                cxt.stats.ctrlBytes += len;

                switch (k) {

                case NACK_PT: 
                    processNack(s, buff, offset, len);

                    break;

                case R_NACK_PT: 
                    processNackReply(s, buff, offset, len);

                    break;

                case SR_PT: 
                    processSenderReport(s, buff, offset, len);

                    break;

                case RS_PT: 
                    processRRSelection(s, buff, offset, len);

                    break;

                case RR_PT: 
                    processReceiverReport(s, buff, offset, len);

                    break;

                default: 
                    Logger.error(this, "bad control pt " + k);

                    break;
                }
            } else {
                cxt.stats.dataPackets++;
                cxt.stats.dataBytes += len;

                if (k >= DATA_PT && k < R_DATA_PT) {
                    processData(s, buff, offset, len);
                } else if (k >= R_DATA_PT && k < U_DATA_PT) {
                    processRepairData(s, buff, offset, len);
                } else if (k >= U_DATA_PT && k < F_DATA_PT) {
                    processUnreliableData(s, buff, offset, len);
                } else if (k == R_DATA_PT) {
                    processFecData(s, buff, offset, len);
                } else {
                    Logger.error(this, "bad data pt " + k);
                }

                reuse = false;
            }

            offset += len;
        }

        s.setLastTimeHeard(System.currentTimeMillis());

        return reuse;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processNack(LrmpEntity s, byte[] buff, int offset, int len) {
        int scope = buff[offset + 1] & 0xff;

        offset += 8;

        int timestamp = Utilities.byteToInt(buff, offset);

        offset += 4;
        len -= 12;

        for (; len >= 12; len -= 12) {

            /* the destinated source */

            int k = Utilities.byteToInt(buff, offset);
            LrmpEntity e = cxt.sm.get(k);

            if (e == null ||!(e instanceof LrmpSender)) {
                continue;
            } 

            offset += 4;

            LrmpLossEvent ev = new LrmpLossEvent((LrmpSender) e);

            ev.rcvSendTime = System.currentTimeMillis();
            ev.low = getSeqno(buff, offset);
            offset += 4;
            ev.bitmask = Utilities.byteToInt(buff, offset);
            offset += 4;
            ev.scope = scope;
            ev.reporter = s;
            ev.timestamp = timestamp;

            if (Logger.debug) {
                Logger.debug(this, "got NACK " + ev + " @" + ev.rcvSendTime);
            } 

            cxt.recover.processNack(ev);

            /* rate adaptation */

            if (e == cxt.whoami) {
                k = (int) (cxt.whoami.expected - ev.low);

                if (k > (cxt.whoami.cacheSize >> 1)) {
                    cxt.adjust = LrmpFlow.BigDecrease;
                } else if (k > (cxt.whoami.cacheSize / 3)) {
                    cxt.adjust = LrmpFlow.MediumDecrease;
                } else if (k > (cxt.whoami.cacheSize >> 2)) {
                    cxt.adjust = LrmpFlow.SmallDecrease;
                } else {
                    cxt.adjust = LrmpFlow.None;
                }
            }
        }

        if (len > 0) {
            cxt.stats.badLength++;
        } else {
            s.incNack();
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processNackReply(LrmpEntity s, byte[] buff, int offset, 
                                  int len) {
        int scope = buff[offset + 1];

        offset += 8;
        len -= 8;

        for (; len >= 8; len -= 24) {
            int to = Utilities.byteToInt(buff, offset);

            offset += 4;

            int timestamp = Utilities.byteToInt(buff, offset);

            offset += 4;

            int delay = Utilities.byteToInt(buff, offset);

            offset += 4;

            int dataSrc = Utilities.byteToInt(buff, offset);
            LrmpEntity e = cxt.sm.get(dataSrc);

            if (e == null ||!(e instanceof LrmpSender)) {
                offset += 12;

                continue;
            }

            offset += 4;

            LrmpLossEvent ev = new LrmpLossEvent((LrmpSender) e);

            ev.rcvSendTime = System.currentTimeMillis();
            ev.reporter = cxt.sm.get(to);

            if (ev.reporter == null) {
                offset += 8;

                continue;
            }

            ev.low = getSeqno(buff, offset);
            offset += 4;
            ev.bitmask = Utilities.byteToInt(buff, offset);
            offset += 4;
            ev.scope = scope;
            ev.timestamp = timestamp;

            cxt.recover.processNackReply(s, ev, delay);
        }

        if (len > 0) {
            cxt.stats.badLength++;
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processSenderReport(LrmpEntity e, byte[] buff, int offset, 
                                     int len) {
        offset += 8;

        int timestamp = Utilities.byteToInt(buff, offset);

        offset += 4;

        long seqno = getSeqno(buff, offset);

        offset += 4;

        LrmpSender s;

        if (e instanceof LrmpSender) {
            s = (LrmpSender) e;
        } else {
            s = cxt.sm.lookupSender(e.getID(), e.getAddress(), seqno);

            s.setRate((cxt.profile.minRate + cxt.profile.maxRate) >> 1);
        }

        s.srSeqno = seqno;

        int packets = Utilities.byteToInt(buff, offset);

        offset += 4;

        int bytes = Utilities.byteToInt(buff, offset);

        offset += 4;

        if (diff32(seqno, s.expected()) > 0) {
            if (diff32(seqno, s.maxseq) > 1) {
                s.maxseq = seqno - 1;
            } 

            cxt.recover.handleLoss(s);
        }

        /* estimate the rate */

        if (s.srTimestamp != 0) {
            int interval = timestamp - s.srTimestamp;

            interval = ((interval >> 8) * 1000) >> 8;

            if (interval > 0) {
                int diff = bytes - s.srBytes;

                if (diff > 0) {
                    s.setRate(diff * 1000 / interval);
                } 

                diff = packets - s.srPackets;

                if (diff > 0) {

                    /*
                     * this is not the exact interval at which the sender is sending
                     * because of repairs.
                     */
                    s.setInterval(interval / diff);
                }
            }
        }
        if (Logger.debug) {
            Logger.debug(this, 
                         "got SR " + e + " cur/next:" + seqno + "/" 
                         + s.expected + " rate:" + s.rate);
        } 

        s.srTimestamp = timestamp;
        s.srPackets = packets;
        s.srBytes = bytes;
        cxt.stats.senderReports++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processRRSelection(LrmpEntity e, byte[] buff, int offset, 
                                    int len) {
        cxt.stats.rrSelect++;

        if (!(e instanceof LrmpSender)) {

            /*
             * refused...
             */
            if (Logger.debug) {
                Logger.debug(this, "receiver report sel from non sender");
            } 

            return;
        }

        LrmpSender s = (LrmpSender) e;

        offset += 8;
        s.rrTimestamp = Utilities.byteToInt(buff, offset);
        offset += 4;
        s.rrProb = (buff[offset++] & 0xff) << 8;
        s.rrProb |= (buff[offset++] & 0xff);
        s.rrInterval = (buff[offset++] & 0xff) << 8;
        s.rrInterval |= (buff[offset++] & 0xff);
        s.rrInterval = s.rrInterval * 1000;
        len -= 16;

        while (len >= 4) {
            int id = Utilities.byteToInt(buff, offset);

            if (id == 0xffffffff || id == cxt.whoami.getID()) {
                s.rrSelectTime = System.currentTimeMillis();
                s.rrReplies = 0;

                boolean send = true;

                if (s.rrProb > 0) {
                    int i = rand.nextInt();

                    i &= 0xffff;

                    if (i > s.rrProb) {
                        send = false;
                    } 
                } else if (s.rrInterval == 0) {
                    send = false;
                } 
                if (Logger.debug) {
                    Logger.debug(this, 
                                 "RR select prob=" + s.rrProb + " interv=" 
                                 + s.rrInterval + " " + send);
                } 
                if (send) {
                    int delay = randomize(s.rrInterval);

                    s.nextRRTime = System.currentTimeMillis() + delay;

                    startTimer(delay);

                    if (!reports.contains(s)) {
                        reports.addElement(s);
                    } 
                } else {
                    if (reports.contains(s)) {
                        reports.removeElement(s);
                    } 
                }

                break;
            }

            offset += 4;
            len -= 4;
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processReceiverReport(LrmpEntity e, byte[] buff, int offset, 
                                       int len) {
        int scope = buff[offset + 1];

        offset += 8;
        len -= 8;

        long now = System.currentTimeMillis();

        while (len >= 20) {
            cxt.stats.receiverReports++;

            int to = Utilities.byteToInt(buff, offset);

            offset += 4;

            LrmpEntity s = cxt.sm.get(to);

            if (s != null && (s instanceof LrmpSender)) {
                LrmpSender sender = (LrmpSender) s;
                int timestamp = Utilities.byteToInt(buff, offset);

                offset += 4;

                /* maybe we missed RRSelect packet, ignore */

                if (timestamp == sender.rrTimestamp) {
                    sender.rrReplies++;

                    /* suppose the estimation scheme is not changed */

                    if (sender.rrProb > 0) {
                        cxt.stats.populationEstimate = 
                            (sender.rrReplies << 16) / sender.rrProb + 1;
                        cxt.stats.populationEstimateTime = now;
                    }
                } else {
                    if (sender != cxt.whoami) {
                        sender.rrReplies = 0;
                        cxt.stats.populationEstimate = 0;
                    }
                }
                if (s == cxt.whoami) {
                    int delay = Utilities.byteToInt(buff, offset);

                    /* NTP offset is subtracted */

                    int rtt = NTP.ntp32(now) - timestamp - delay;

                    rtt = NTP.fixedPoint32ToMillis(rtt);

                    if (rtt >= 0) {
                        e.rtt = rtt;

                        LrmpDomain d = cxt.recover.lookupDomain(scope);

                        if (d != null) {
                            d.updateMRTT(rtt);
                        } 
                    } else {
                        Logger.error(this, 
                                     "bad rtt " + rtt + " " + e + " " 
                                     + NTP.ntp32(now) + "/" + delay + "/" 
                                     + timestamp);
                    }
                    if (Logger.debug) {
                        Logger.debug(this, "RR from " + e + " rtt=" + rtt);
                    } 
                }

                /* other field ignored */

                offset += 12;
            } else {
                offset += 16;
            }

            len -= 20;
        }
    }

    /* process DATA packet */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param from
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processData(LrmpEntity from, byte buff[], int offset, 
                             int len) {
        long seqno = getSeqno(buff, offset + 12);

        /*
         * check the source.
         */
        LrmpSender source;

        if (!(from instanceof LrmpSender)) {
            source = cxt.sm.lookupSender(from.getID(), from.getAddress(), 
                                         seqno);
        } else {
            source = (LrmpSender) from;
        }

        /*
         * check the seqno. Ignore duplicate.
         */
        int diff = diff32(seqno, source.expected());

        if (diff < 0) {
            source.incDuplicate();

            return;
        }

        LrmpPacket pack = source.getPacket(seqno);

        if (pack != null) {
            source.incDuplicate();

            pack.scope = buff[offset + 1] & 0xff;
            pack.rcvSendTime = System.currentTimeMillis();
        }

        /* pack the data into a packet */

        pack = new LrmpPacket(true, buff, offset, len);
        pack.seqno = seqno;
        pack.retransmit = false;
        pack.sender = from;
        pack.source = source;

        /*
         * update stats.
         */
        source.lastTimeForData = pack.rcvSendTime;

        source.updateJitter(Utilities.byteToInt(buff, offset + 8));

        source.lastseq = seqno;

        source.incPackets();
        source.incBytes(pack.datalen);

        if (Logger.debug) {
            Logger.debug(this, 
                         "data/exp:" + seqno + "/" + source.expected() + " @" 
                         + pack.rcvSendTime + "/" + pack.scope);
        } 
        if (pack.seqno > source.maxseq) {
            source.maxseq = pack.seqno;
        } 

        /*
         * check sequence number.
         */
        if (diff == 0) {

            /*
             * In order, keeps a local copy in cache for local repair.
             */
            if (cxt.profile.sendRepair) {
                source.putPacket(pack);
            } 

            /*
             * deliver all cached packets in sequence.
             */
            while (pack != null) {
                source.incExpected();
                deliverData(pack);

                pack = source.getPacket(source.expected());
            }
        } else if (diff <= source.cacheSize) {

            /*
             * out of order but in range, that is, loss is still recoverable.
             * cache the packet and process loss.
             */
            source.putPacket(pack);
            cxt.recover.handleLoss(source);
        } else {

            /*
             * out of range: diff > maxCacheSize.
             * we have missed too much, maybe the sender is sending data too
             * fast or we have a network trouble.
             * Could not sync with the transmission. Reset the data stream and try
             * to catch up.
             */
            handleSyncError(source, LrmpErrorEvent.BufferOverrun);

            if (cxt.profile.sendRepair) {
                source.putPacket(pack);
            } 
        }
    }

    /* process R_DATA packet */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param from
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processRepairData(LrmpEntity from, byte buff[], int offset, 
                                   int len) {

        /*
         * check the source.
         */
        LrmpEntity e = cxt.sm.get(Utilities.byteToInt(buff, offset + 8));

        if (e == null ||!(e instanceof LrmpSender)) {

            /* have never heard from the sender */

            return;
        }

        LrmpSender source = (LrmpSender) e;

        source.incRepairs();

        /*
         * check the seqno. Ignore duplicate, update reception stats and stop.
         */
        long seqno = getSeqno(buff, offset + 12);
        LrmpPacket pack = source.getPacket(seqno);

        if (pack != null) {
            pack.scope = buff[offset + 1] & 0xff;
            pack.rcvSendTime = System.currentTimeMillis();

            if (source != cxt.whoami) {
                source.incDuplicate();

                /* call this method for possible duplicate repair suppression */

                cxt.recover.heardRepair(pack, true);
            }

            return;
        } else if (source == cxt.whoami) {

            /*
             * ignore repair packets for my own.
             */
            return;
        }

        int diff = diff32(seqno, source.expected());

        if (diff < 0) {
            source.incDuplicate();

            return;
        }

        /*
         * at this point it is really a repair.
         */
        pack = new LrmpPacket(true, buff, offset, len);
        pack.retransmit = true;
        pack.seqno = seqno;
        pack.source = source;
        pack.sender = from;

        /* call this method for update stats */

        cxt.recover.heardRepair(pack, false);

        /*
         * update stats.
         */
        if (pack.sender == source) {
            source.lastTimeForData = pack.rcvSendTime;
            source.lastseq = pack.seqno;
        }

        source.incPackets();
        source.incBytes(pack.datalen);

        if (Logger.debug) {
            Logger.debug(this, 
                         "repair/exp:" + pack.seqno + "/" + source.expected() 
                         + " @" + pack.rcvSendTime + "/" + pack.scope);
        } 
        if (pack.seqno > source.maxseq) {
            source.maxseq = pack.seqno;
        } 

        /*
         * further check.
         */
        if (diff == 0) {

            /*
             * good repair, keeps a local copy in cache for local repair.
             */
            if (cxt.profile.sendRepair) {
                source.putPacket(pack);
            } 

            /*
             * deliver all cached packets in sequence.
             */
            while (pack != null) {
                source.incExpected();
                deliverData(pack);

                pack = source.getPacket(source.expected());
            }
        } else if (diff <= source.cacheSize) {

            /*
             * out of order but in range, that is, loss is still recoverable.
             * cache the packet and process loss.
             */
            source.putPacket(pack);
            cxt.recover.handleLoss(source);
        }

    /*
     * out of range: diff > maxCacheSize. Ignore.
     */
    }

    /* process U_DATA packet */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param from
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processUnreliableData(LrmpEntity from, byte buff[], 
                                       int offset, int len) {
        cxt.stats.outOfBand++;

        /* pack the data into a packet */

        LrmpPacket pack = new LrmpPacket(false, buff, offset, len);

        pack.source = from;

        deliverData(pack);

        return;
    }

    /* process F_DATA packet */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param from
     * @param buff
     * @param offset
     * @param len
     *
     * @see
     */
    private void processFecData(LrmpEntity from, byte buff[], int offset, 
                                int len) {}

    /**
     * handles a reception failure event.
     */
    public void handleSyncError(LrmpSender s, int cause) {
        Logger.error(this, 
                     "reception failure @" + s.expected + "/" + s.maxseq 
                     + " cause=" + cause);

        /* for continuous losses, we should report only one event */

        LrmpErrorEvent ev = null;

        if (s.expected != (s.lastError + 1)) {
            ev = new LrmpErrorEvent();
            ev.source = s;
            ev.loser = cxt.whoami;
            ev.cause = cause;
            ev.seqlost = (int) s.expected;
            cxt.stats.failures++;

            if (cxt.profile.handler != null) {
                cxt.profile.handler.processEvent(
		    LrmpEventHandler.UNRECOVERABLE_SEQUENCE_ERROR, ev);
            }
        }

        s.lastError = s.expected;

        int diff = diff32(s.maxseq, s.expected);

        if (diff < 0) {
            s.clearCache(s.maxseq);
        } else {
            s.incExpected();

            diff--;

            /* deliver in order packets */

            LrmpPacket lastpack = null;

            while (diff > s.cacheSize) {
                LrmpPacket pack = s.getPacket(s.expected);

                if (pack != null) {
                    deliverData(pack);
                } else {
                    if (lastpack != null) {
                        if (cxt.profile.handler != null) {
                            if (ev == null) {
                                ev = new LrmpErrorEvent();
                                ev.source = s;
                                ev.loser = cxt.whoami;
                                ev.cause = cause;
                            }

                            s.lastError = s.expected;
                            ev.seqlost = (int) s.expected;
                            cxt.stats.failures++;

                            cxt.profile.handler.processEvent(
				LrmpEventHandler.UNRECOVERABLE_SEQUENCE_ERROR, 
				ev);
                        }
                    }
                }

                lastpack = pack;

                s.incExpected();

                diff--;
            }
        }

        /* deliver in order packets */

        while (true) {
            LrmpPacket pack = s.getPacket(s.expected());

            if (pack == null) {
                break;
            } 

            deliverData(pack);
            s.incExpected();
        }

        if (Logger.debug) {
            Logger.debug(this, "synced to " + s.expected);
        } 

        LrmpLossEvent ev1 = cxt.recover.lookup(s, cxt.whoami);

        if (ev1 != null) {

            /* update the low seqno to indicate that this is not repaired */

            ev1.low = s.expected;
            ev1.nextAction = LrmpLossEvent.SendNack;

            if (ev1.nackCount > 1) {
                ev1.nackCount--;
            } 
        } else {
            cxt.recover.handleLoss(s);
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     *
     * @see
     */
    private void deliverData(LrmpPacket pack) {
        if (pack.reliable) {
            if (Logger.debug) {
                Logger.debug(this, 
                             "deliver #" + pack.seqno + " len=" 
                             + pack.datalen);
            } 

            /*
             * remove from cache if don't participate in local recovery.
             */
            if (!cxt.profile.sendRepair) {
                ((LrmpSender) pack.source).removePacket(pack);
            } 
        } else if (Logger.debug) {
            Logger.debug(this, 
                         "deliver out-of-band" + " len=" + pack.datalen);
        } 
        if (cxt.profile.handler != null) {
            cxt.profile.handler.processData(pack);
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param buff
     * @param offset
     *
     * @return
     *
     * @see
     */
    private long getSeqno(byte[] buff, int offset) {
        long i;

        i = ((buff[offset] << 24) & 0xff000000L);
        i |= ((buff[offset + 1] << 16) & 0xff0000L);
        i |= ((buff[offset + 2] << 8) & 0xff00L);
        i |= (buff[offset + 3] & 0xffL);

        return i;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     * @param resend
     *
     * @see
     */
    protected void sendDataPacket(LrmpPacket pack, boolean resend) {
        int len = pack.formatDataPacket(resend);

        send(pack.getDataBuffer(), len, pack.scope);

        if (resend) {
            LrmpDomain d = cxt.recover.lookupDomain(pack.scope);

            d.stats.repairPackets++;
            d.stats.repairBytes += len;

            cxt.whoami.incRepairs();
        }

        pack.rcvSendTime = System.currentTimeMillis();

        if (pack.reliable) {        /* XXXXXXXXX */
            cxt.whoami.lastTimeForData = pack.rcvSendTime;
        } 

        cxt.whoami.setLastTimeHeard(pack.rcvSendTime);
        cxt.whoami.incPackets();
        cxt.whoami.incBytes(len);

        cxt.stats.dataPackets++;
        cxt.stats.dataBytes += len;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     * @param ttl
     *
     * @see
     */
    protected void sendControlPacket(LrmpPacket pack, int ttl) {
        cxt.whoami.setLastTimeHeard(System.currentTimeMillis());

        cxt.stats.ctrlPackets++;
        cxt.stats.ctrlBytes += pack.offset;

        send(pack.buff, pack.offset, ttl);
    }

    /**
     * does 32-bit diff of seqno (seq1 - seq2). Handle overflow and underflow.
     * The result will be correct provided that the absolute diff is less than
     * Modulo32/2.
     * @param seq1 first seqno.
     * @param seq2 second seqno.
     */
    public static int diff32(long seq1, long seq2) {
        long diff = seq1 - seq2;

        if (diff > (Modulo32 >> 1)) {
            diff -= Modulo32;
        } else if (diff < -(Modulo32 >> 1)) {
            diff += Modulo32;
        } 

        return (int) diff;
    }

    /* in interval 0.25i to 1.0i */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param i
     *
     * @return
     *
     * @see
     */
    private int randomize(int i) {
        return (int) (i * ((65536 + 3 * (rand.nextInt() & 0xffff)) >> 8) 
                      >> 10);
    }

    /**
     * the state changes from sending to idle.
     */
    protected void idle() {
        long now = System.currentTimeMillis();
        int diff = (int) (cxt.whoami.nextSRTime - now);
        int idleTime = cxt.sndInterval << 4;

        if (idleTime < 1000) {
            idleTime = 1000;
        } else if (idleTime > 4000) {
            idleTime = 4000;
        } 
        if (event != null) {
            cxt.timer.recallTimer(event);

            event = null;
        }

        cxt.whoami.nextSRTime = now + idleTime;

        startTimer(idleTime);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param millis
     *
     * @see
     */
    private void startTimer(int millis) {
        long t1 = System.currentTimeMillis() + millis;

        if (event != null) {
            if (t1 > nextTimeout) {
                return;
            } 

            cxt.timer.recallTimer(event);
        }
        if (Logger.debug) {
            Logger.debug(this, "next timeout in " + millis);
        } 

        event = cxt.timer.registerTimer(millis, this, null);
        nextTimeout = t1;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param data
     * @param time
     *
     * @see
     */
    public void handleTimerEvent(Object data, long time) {
        event = null;

        LrmpPacket p = new LrmpPacket(false, 1024);

        p.scope = ttl;
        p.offset = 0;

        int timeout = checkInterval;

        /*
         * no sender reports if
         * 1. never send data.
         * 2. just sent out-of-band data.
         * 3. has not sent data since the max silence (drop) time.
         * send several sender reports when the transmission is stopped.
         */
        if (cxt.whoami.expected != cxt.whoami.startseq) {
            if ((time - cxt.whoami.lastTimeForData) 
                    < LrmpEntityManager.sndDropTime) {
                int diff = (int) (cxt.whoami.nextSRTime - time);

                if (diff <= 0) {
                    p.appendSenderReport(cxt.whoami);

                    cxt.stats.senderReports++;

                    /* update rate */

                    int octets = cxt.whoami.bytes - cxt.whoami.srBytes;
                    int timestamp = NTP.ntp32(time);
                    int interval = timestamp - cxt.whoami.srTimestamp;

                    interval = ((interval >> 8) * 1000) >> 8;

                    if (interval > 0) {
                        cxt.whoami.setRate(octets * 1000 / interval);
                    } 

                    cxt.whoami.srBytes = cxt.whoami.bytes;
                    cxt.whoami.srPackets = cxt.whoami.packets;
                    cxt.whoami.srSeqno = cxt.whoami.expected;
                    cxt.whoami.srTimestamp = timestamp;

                    if ((time - cxt.whoami.lastTimeForData) > idleTime) {
                        timeout = ((int) (time - cxt.whoami.lastTimeForData));

                        if (timeout < 2000) {
                            timeout = 2000;
                        } 
                    } else {
                        timeout = cxt.senderReportInterval;
                    }

                    cxt.whoami.nextSRTime = time + timeout;
                } else {
                    timeout = diff;
                }
                if (cxt.profile.rcvReportSelection 
                        != LrmpProfile.NoReceiverReport 
                        && (time - cxt.whoami.rrSelectTime) 
                           > cxt.rcvReportSelInterval) {
                    if (cxt.stats.populationEstimate 
                            < cxt.sm.getNumberOfEntities()) {
                        cxt.stats.populationEstimate = 
                            cxt.sm.getNumberOfEntities();
                    } 

                    cxt.whoami.rrInterval = 10;     /* seconds */

                    /*
                     * limit the number of reports to 100, so using the following
                     * formula probability*population < 100.
                     */
                    cxt.whoami.rrProb = (100 << 16) 
                                        / (cxt.stats.populationEstimate + 1);

                    if (cxt.whoami.rrProb > 0xffff) {
                        cxt.whoami.rrProb = 0xffff;
                    } 

                    p.appendRRSelection(cxt.whoami, cxt.whoami.rrProb, 
                                        cxt.whoami.rrInterval);

                    cxt.whoami.rrSelectTime = System.currentTimeMillis();
                    cxt.whoami.rrReplies = 0;
                    cxt.stats.populationEstimate = 0;
                    cxt.stats.rrSelect++;
                }
            }
            if (Logger.debug) {
                Logger.debug(this, "send sender report " + p.offset);
            } 
        }

        /*
         * check if send receiver reports.
         */
        for (int i = reports.size() - 1; i >= 0; i--) {
            LrmpSender s = (LrmpSender) reports.elementAt(i);
            int delay = (int) (s.nextRRTime - time);

            if (delay <= 0) {
                p.appendReceiverReport(s, cxt.whoami);

                cxt.stats.receiverReports++;

                if (s.rrProb > 0) {     /* once */
                    reports.removeElement(s);
                } else {
                    delay = randomize(s.rrInterval);
                    s.nextRRTime = time + delay;
                }
            }
            if (delay > 0 && delay < timeout) {
                timeout = delay;
            } 
        }

        if (p.offset > 0) {
            sendControlPacket(p, ttl);
        } 

        /* prune the list of entities heard */

        cxt.sm.prune();
        startTimer(timeout);
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private void sendSenderReport() {
        LrmpPacket p = new LrmpPacket(false, 64);

        p.scope = ttl;
        p.offset = 0;

        p.appendSenderReport(cxt.whoami);
        sendControlPacket(p, ttl);

        cxt.stats.senderReports++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     *
     * @see
     */
    private void sendReceiverReport(LrmpSender s) {
        LrmpPacket p = new LrmpPacket(false, 64);

        p.scope = ttl;
        p.offset = 0;

        p.appendReceiverReport(s, cxt.whoami);
        sendControlPacket(p, ttl);

        cxt.stats.receiverReports++;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param now
     *
     * @see
     */
    private void update(long now) {
        int elapsed = (int) (now - lastUpdateTime);

        lastUpdateTime = now;
        cxt.stats.dataRate = (cxt.stats.dataBytes - lastDataBytes) * 1000 
                             / elapsed;
        lastDataBytes = cxt.stats.dataBytes;

        if (cxt.stats.dataRate > cxt.stats.maxDataRate) {
            cxt.stats.maxDataRate = cxt.stats.dataRate;
        } 

        cxt.stats.ctlRate = (cxt.stats.ctrlBytes - lastCtrlBytes) * 1000 
                            / elapsed;
        lastCtrlBytes = cxt.stats.ctrlBytes;

        if (cxt.stats.ctlRate > cxt.stats.maxCtlRate) {
            cxt.stats.maxCtlRate = cxt.stats.ctlRate;
        } 
        if ((now - cxt.stats.populationEstimateTime) > 600000 
                || cxt.stats.populationEstimate 
                   < cxt.sm.getNumberOfEntities()) {
            cxt.stats.populationEstimate = cxt.sm.getNumberOfEntities();
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public LrmpStats getLrmpStats() {
        update(System.currentTimeMillis());

        LrmpStats stats = cxt.stats;

        stats.nack = 0;
        stats.dupNack = 0;
        stats.nackReply = 0;
        stats.repairPackets = 0;
        stats.repairBytes = 0;
        stats.dupPackets = 0;
        stats.dupBytes = 0;

        for (LrmpDomain d = cxt.recover.domain; d != null; d = d.parent) {
            LrmpDomainStats s = d.stats;

            stats.nack += s.nack;
            stats.dupNack += s.dupNack;
            stats.nackReply += s.nackReply;
            stats.repairPackets += s.repairPackets;
            stats.repairBytes += s.repairBytes;
            stats.dupPackets += s.dupPackets;
            stats.dupBytes += s.dupBytes;
        }

        return (LrmpStats) stats;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param scope
     *
     * @return
     *
     * @see
     */
    public LrmpDomainStats getDomainStats(int scope) {
        LrmpDomain d = cxt.recover.lookupDomain(scope);

        if (d != null) {
            return d.stats;
        } 

        return null;
    }

    /**
     * gets the destination address.
     */
    public InetAddress getAddress() {
        return inetAddr;
    }

    /**
     * gets the port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * sets the scope value.
     */
    public void setTTL(int i) {
        if (i >= 0 && i < 256 && i != ttl) {
            ttl = i;

            initRecover();
        }
    }

}

