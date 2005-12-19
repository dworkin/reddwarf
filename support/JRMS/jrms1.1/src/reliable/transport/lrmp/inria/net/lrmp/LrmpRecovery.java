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
 * LrmpRecovery.java - error recovery scheme.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: May 5 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;
import inria.util.*;

/**
 * a hierarchical doamin for error recovery. A domain is scoped by a ttl value
 * which is the radius of reachable distance.
 */
final class LrmpRecovery implements EventHandler {
    protected static final int MaxTries = 8;
    protected LrmpDomain domain;
    protected LrmpContext cxt;
    protected LrmpPacket dummy = null;
    protected Object event = null;
    protected Random rand;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param ttl
     * @param context
     *
     * @see
     */
    public LrmpRecovery(int ttl, LrmpContext context) {
        this.cxt = context;
        domain = new LrmpDomain(ttl);

        /* the loss table is shared */

        domain.lossTab = new LrmpLossTable(4);
        domain.lossHistory = new Vector();

        if (ttl > 63) {
            domain.child = new LrmpDomain(63);
            domain.child.lossTab = domain.lossTab;
            domain.child.lossHistory = domain.lossHistory;

            domain.setChild(domain.child);

            domain = domain.child;
        }
        if (ttl > 47) {
            domain.child = new LrmpDomain(47);
            domain.child.lossTab = domain.lossTab;
            domain.child.lossHistory = domain.lossHistory;

            domain.setChild(domain.child);

            domain = domain.child;
        }
        if (ttl > 15) {
            domain.child = new LrmpDomain(15);
            domain.child.lossTab = domain.lossTab;
            domain.child.lossHistory = domain.lossHistory;

            domain.setChild(domain.child);

            domain = domain.child;
        }

        rand = new Random();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void stop() {
        if (event != null) {
            cxt.timer.recallTimer(event);

            event = null;

            domain.lossTab.clear();
        }
    }

    /**
     * handles a local loss event detected when receiving data from the
     * given source. Keep only one loss event in the queue for a sender.
     */
    public void handleLoss(LrmpSender s) {
        if (cxt.profile != null && cxt.profile.lossAllowed()) {
            return;
        } 

        int diff = LrmpImpl.diff32(s.highestSeqnoGot(), s.expected());

        if (diff > s.cacheSize) {

            /*
             * lost too many, can't repair.
             */
            cxt.lrmp.handleSyncError(s, LrmpErrorEvent.BufferOverrun);

            return;
        }

        /*
         * find if there is already one for the same source.
         */
        LrmpLossEvent ev = lookup(s, cxt.whoami);

        if (ev == null) {
            LrmpDomain d = getDomain();

            ev = new LrmpLossEvent(s);
            ev.reporter = cxt.whoami;
            ev.scope = d.scope;
            ev.domain = d;

            ev.computeBitmask();
            d.lossTab.add(ev);

            if (Logger.debug) {
                Logger.debug(this, "new loss " + ev);
            } 

            /* schedule a timer */

            nackTimer(ev);
            startTimer();
        } else {

            /*
             * new data is still arriving, that means the sender is active,
             * decrement the timer.
             */
            if (ev.nackCount > 0) {
                ev.nackCount--;
            } 

            ev.nextAction = LrmpLossEvent.SendNack;
        }
    }

    /**
     * go up one level, supposed that the event is already in the loss table.
     */
    public void goUp(LrmpLossEvent ev) {
        if (ev.domain.parent != null && ev.scope < ev.source.distance) {
            ev.scope = ev.domain.parent.scope;
            ev.domain = ev.domain.parent;
            ev.nackCount = 0;
        } else {
            ev.nackCount++;
        }

        nackTimer(ev);
    }

    /**
     * go down one level, supposed that the event is already in the loss table.
     */
    public void goDown(LrmpLossEvent ev) {
        if (ev.domain.child != null && ev.domain.child.isEnabled()) {
            ev.scope = ev.domain.child.scope;
            ev.domain = ev.domain.child;
            ev.nackCount = 1;
        } else {
            ev.nackCount++;
        }

        nackTimer(ev);
    }

    /*
     * only one event for a given source and a reporter is maintained
     * in all domains.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     * @param reporter
     *
     * @return
     *
     * @see
     */
    protected LrmpLossEvent lookup(LrmpSender s, LrmpEntity reporter) {
        LrmpLossEvent[] tab = domain.lossTab.tab;

        for (int i = tab.length - 1; i >= 0; i--) {
            if (tab[i] != null) {
                if (tab[i].source == s && tab[i].reporter == reporter) {
                    return tab[i];
                } 
            }
        }

        return null;
    }

    /*
     * determine the timer value for sending NACK. This is an exponential back-off
     * timer, each time the timeout elapsed, the timeout value T(i) is set to T(i-1)*2
     * until it reaches the upper bound. Three parameters to be taken into account:
     * - mrtt,
     * - nack count,
     * - transmission interval used by the source.
     * For the first nack, we can use the mrtt to quickly report the loss.
     * The tricky part is sending the following nacks. The timer value must be larger
     * than the resend timer and the time in which the source may send repairs. So
     * a transmission interval must be added. The initial mrtt is used as the lower
     * bound since responders may use it to schedule the resend.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    private void nackTimer(LrmpLossEvent ev) {
        int d = (ev.domain.stats.mrtt << ev.nackCount) >> 3;

        /*
         * at the moment we are rather conservative, but at some later time
         * the low bound may be removed after tests for better performance.
         */
        if (ev.nackCount > 0 
                || (ev.domain.child != null && ev.domain.child.isEnabled())) {
            if (d < ev.domain.initialMRTT) {
                d = ev.domain.initialMRTT;
            } 

            d = (int) (d * (1.0 + rand.nextDouble()));

            if (ev.source.interval < 200) {
                d += ev.source.interval;
            } else {
                d += 200;
            }
        } else {
            d = (int) (d * (1.0 + rand.nextDouble()));
        }
        if (Logger.debug) {
            Logger.debug(this, 
                         "NACK timer=" + d + " #" + ev.nackCount + " " 
                         + ev.domain.stats.getRTT() + "/" 
                         + ev.source.interval + "@" + ev.scope);
        }

        ev.timeoutTime = System.currentTimeMillis() + d;
    }

    /*
     * determine the timer value for sending repairs.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    private void resendTimer(LrmpLossEvent ev) {
        int d = ev.domain.stats.mrtt >> 3;

        d = (int) (d * (1.0 + rand.nextDouble()));

        /*
         * add a transmission interval since the sender may resend.
         */
        if (ev.source.interval < 200) {
            d += ev.source.interval;
        } else {
            d += 200;
        }
        if (Logger.debug) {
            Logger.debug(this, 
                         "resendTimer=" + d + " " + ev.domain.stats.mrtt 
                         + "/" + ev.source.interval);
        } 

        ev.timeoutTime = System.currentTimeMillis() + d;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private synchronized void startTimer() {
        long future = 0x7fffffffffffffffL;
        LrmpLossEvent[] tab = domain.lossTab.tab;

        for (int i = tab.length - 1; i >= 0; i--) {
            if (tab[i] == null) {
                continue;
            } 

            LrmpLossEvent ev = tab[i];

            if (ev.timeoutTime < future) {
                future = ev.timeoutTime;
            } 
        }

        if (future < 0x7fffffffffffffffL) {
            if (event != null) {
                cxt.timer.recallTimer(event);
            } 

            int millis = (int) (future - System.currentTimeMillis());

            if (millis < 0) {
                millis = 1;
            } 

            event = cxt.timer.registerTimer(millis, this, null);

            if (Logger.debug) {
                Logger.debug(this, 
                             "Next timeout=" + millis + " events: " 
                             + domain.lossTab.size());
            } 
        }
    }

    /*
     * the timer has expired, send NACK or repair in need.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param data
     * @param now
     *
     * @see
     */
    public void handleTimerEvent(Object data, long now) {
        event = null;

        if (Logger.debug) {
            Logger.debug(this, "handle timeout: " + domain.lossTab.size());
        } 

        /*
         * check which event is timeout in the loss event queue.
         */
        LrmpLossEvent[] tab = domain.lossTab.tab;

        for (int i = tab.length - 1; i >= 0; i--) {
            if (tab[i] == null) {
                continue;
            } 

            LrmpLossEvent ev = tab[i];

            if (ev.timeoutTime > now) {
                continue;
            }

            /*
             * first process loss events reported by remote sites.
             */
            if (ev.reporter != cxt.whoami) {
                domain.lossTab.remove(ev);
                resend(ev);

                continue;
            }

            /*
             * process loss events occurred at local site,
             * check if need to send NACK.
             */
            LrmpSender s = (LrmpSender) ev.source;

            /* update loss event */

            ev.computeBitmask();

            /* check for stop */

            if (ev.low < 0) {

                /*
                 * all lost packets repaired.
                 */
                if (Logger.debug) {
                    Logger.debug(this, "Bravo!");
                } 

                domain.lossTab.remove(ev);

                continue;
            } else if (s.lost) {
                cxt.lrmp.handleSyncError(ev.source, 
                                         LrmpErrorEvent.SenderGone);
                domain.lossTab.remove(ev);

                continue;
            } else if (ev.nackCount >= MaxTries) {
                cxt.lrmp.handleSyncError(ev.source, 
                                         LrmpErrorEvent.MaxTriesReached);
                domain.lossTab.remove(ev);

                continue;
            }

            /*
             * If one of the following events happended,
             * 1. a NACK has been received.
             * 2. a NACK reply has been received.
             * 3. one or more repair packets have been received.
             * Don't send NACK and don't delete the event since the reporter
             * or responder may leave. Schedule another timer to keep the
             * repair process active.
             */
            switch (ev.nextAction) {

            case LrmpLossEvent.DelayAndStay: 

                /* just schedule the next timer */

                ev.nackCount++;

                nackTimer(ev);

                ev.nextAction = LrmpLossEvent.SendNack;

                break;

            case LrmpLossEvent.DelayAndGoDown: 
                goDown(ev);

                ev.nextAction = LrmpLossEvent.SendNack;

                break;

            case LrmpLossEvent.SendNack: 

                /*
                 * This is the case where nothing has happened during the timer
                 * schedule period for this loss. So send a NACK.
                 * One try for lower domains and MaxTries for all domains.
                 */
                if (dummy == null) {
                    dummy = new LrmpPacket(false, 64);
                    dummy.sender = cxt.whoami;
                }

                dummy.scope = ev.scope;
                dummy.offset = 0;

                dummy.appendNack(ev);

                if (Logger.debug) {
                    Logger.debug(this, "send NACK " + ev);
                } 

                cxt.lrmp.sendControlPacket(dummy, ev.scope);

                ev.domain.stats.nack++;
                ev.domain.failedNack++;
                ev.rcvSendTime = now;

                cxt.whoami.incNack();
                goUp(ev);

                break;

            case LrmpLossEvent.DelayAndGoUp: 
                goUp(ev);

                ev.nextAction = LrmpLossEvent.SendNack;

                break;
            }
        }

        startTimer();
    }

    /**
     * returns the lowest enabled domain.
     */
    protected LrmpDomain getDomain() {
        for (LrmpDomain d = domain; d != null; d = d.parent) {
            d.checkState();

            if (d.stats.enabled) {
                return d;
            } 
        }

        return null;
    }

    /**
     * lookups a domain which matches the given scope, provided that the lookup
     * begins from the lowest domain.
     */
    public LrmpDomain lookupDomain(int ttl) {
        for (LrmpDomain d = domain; d != null; d = d.parent) {
            if (d.parent == null || ttl <= d.scope) {
                return d;
            } 
        }

        return null;
    }

    /**
     * a repair packet is heard from the network within this domain, so update stats
     * and enable the domain.
     */
    public void heardRepair(LrmpPacket p, boolean dup) {
        LrmpDomain dc = lookupDomain(p.scope);

        dc.stats.repairPackets++;
        dc.stats.repairBytes += p.datalen;

        if (p.sender != p.source) {
            dc.stats.thirdPartyRepairs++;
        } 

        LrmpSender source = (LrmpSender) p.source;

        if (dup) {
            dc.stats.dupPackets++;
            dc.stats.dupBytes += p.datalen;

            if (p.sender != p.source) {
                dc.stats.thirdPartyDuplicates++;
            } 
            if (Logger.debug) {
                if (p.sender == source) {
                    Logger.debug(this, 
                                 "duplicate #" + p.seqno + " from source");
                } else {
                    Logger.debug(this, 
                                 "duplicate #" + p.seqno 
                                 + " from third party");
                }
            } 

            /* unconditionally cancel resend the same seqno */

            cxt.sender.cancelResend(source, p.seqno, p.scope);

            /* unconditionally cancel resend if not started reply XXXX */

            /* conditionall cancel if local id is higher */

            if (source != cxt.whoami) {
                LrmpPacket p1 = source.getPacket(p.seqno);

                if (p1 != null) {
                    long r = (p.sender.getID() & 0xffffffffL);
                    long me = (cxt.whoami.getID() & 0xffffffffL);

                    if (me > r || p.sender == source) {
                        cxt.sender.cancelResend(source, p1.retransmitID, 
                                                p.scope);
                    } 
                }
            }
        } else {
            LrmpLossEvent event = lookup(source, cxt.whoami);

            /*
             * as the responder is sending repairs, delay the next NACK since
             * the following repairs may be in the send queue of the responder.
             */
            if (event != null) {
                if (event.high <= event.source.expected) {

                    /* the loss has been repaired */

                    domain.lossTab.remove(event);
                } else if ((p.seqno - event.low) < 33) {
                    event.nextAction = LrmpLossEvent.DelayAndGoDown;
                } else {
                    event.nextAction = LrmpLossEvent.DelayAndStay;
                }
            }
            if (!dc.stats.enabled) {
                dc.enable();
            } else {
                dc.failedNack = 0;
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param responder
     * @param ev
     * @param delay
     *
     * @see
     */
    public void processNackReply(LrmpEntity responder, LrmpLossEvent ev, 
                                 int delay) {
        LrmpDomain dc = lookupDomain(ev.scope);

        dc.stats.nackReply++;

        /*
         * if we are the original sender, nothing to do.
         */
        if (cxt.whoami == ev.source) {
            return;
        } 
        if (Logger.debug) {
            Logger.debug(this, "got R_NACK " + ev);
        } 

        /*
         * if the local lost seqno is greater than or equal to the one heard,
         * delay the next NACK.
         */
        LrmpLossEvent ev1 = lookup(ev.source, cxt.whoami);

        if (ev1 != null) {
            if (ev1.low >= ev.low) {
                ev1.nextAction = LrmpLossEvent.DelayAndStay;
            } 
        }

        /*
         * if it is for us, update mean round trip time.
         */
        if (ev.reporter == cxt.whoami) {

            /* NTP offset is subtracted */

            int rtt = NTP.ntp32(System.currentTimeMillis()) - ev.timestamp 
                      - delay;

            rtt = NTP.fixedPoint32ToMillis(rtt);
            responder.rtt = rtt;

            dc.updateMRTT(rtt);

            return;
        }

        /*
         * unconditionally cancel resend if we have not yet sent reply.
         * allow partial repair.
         */
        ev1 = lookup(ev.source, ev.reporter);

        if (ev1 != null) {
            ev1.remove(ev);

            if (ev1.low < 0) {
                dc.lossTab.remove(ev1);

                if (Logger.debug) {
                    Logger.debug(this, "great! cancel resend: " + ev1);
                } 
            }

            return;
        }

        /*
         * conditionally cancel resend (queued) if the local id is higher
         * than the responder id.
         */
        long r = (responder.getID() & 0xffffffffL);
        long me = (cxt.whoami.getID() & 0xffffffffL);

        if (me > r || responder == ev.source) {
            cxt.sender.cancelResend(ev.source, ev.low, ev.scope);

            for (int i = 0; i < 32; i++) {
                if (((ev.bitmask >> i) & 0x01) > 0) {
                    long seqno = ev.low + i + 1;

                    cxt.sender.cancelResend(ev.source, seqno, ev.scope);
                }
            }
        }
    }

    /**
     * a NACK packet is heard from the network within this domain.
     */
    public void processNack(LrmpLossEvent received) {
        LrmpDomain dc = lookupDomain(received.scope);

        received.domain = dc;
        dc.stats.nack++;

        /*
         * there are three cases:
         * 1. we'r a receiver having the same loss, cancel the next NACK
         * and schedule another timer.
         * 2. we'r the sender, send repairs immediately.
         * 3. we'r a receiver without the same loss, schedule the
         * sending of repairs.
         */
        LrmpLossEvent event = lookup(received.source, cxt.whoami);

        if (event != null) {

            /*
             * if the received NACK contains the local NACK, cancel the current
             * timer and delay next NACK if the local id is greater.
             */
            if (received.contains(event)) {
                if (event.nextAction == LrmpLossEvent.SendNack) {
                    goUp(event);

                    long r = (received.reporter.getID() & 0xffffffffL);
                    long me = (cxt.whoami.getID() & 0xffffffffL);

                    if (me > r) {
                        event.nextAction = LrmpLossEvent.DelayAndStay;
                    } 
                }
            }
            if (event.contains(received)) {
                int slice = dc.stats.mrtt >> 2;

                if (received.source.interval < 200) {
                    slice += received.source.interval;
                } else {
                    slice += 200;
                }
                if ((received.rcvSendTime - event.rcvSendTime) <= slice) {
                    dc.stats.dupNack++;

                    if (Logger.debug) {
                        Logger.debug(this, 
                                     "Dup nack: " + slice + "/" 
                                     + dc.stats.mrtt);
                    } 
                }

                return;
            }
        }

        /* ignore duplicates */

        if (dc.isDuplicate(received)) {
            dc.stats.dupNack++;

            return;
        }

        /*
         * if I'm the sender, send repair immediately.
         * otherwise schedule resend.
         */
        if (cxt.whoami == received.source) {

            // if (received.domain.parent == null)			/* test XXXXXXXXX */

            resend(received);
        } else {

            /*
             * at the top level, don't send repair if we are not the original sender.
             * check cache now, since we may send recently received repairs.
             */
            if (dc.parent != null && cxt.profile.sendRepair) {

                /* make a copy to keep the original intact (kept in history) */

                received = (LrmpLossEvent) received.clone();

                long firstSent = 0;
                int bitsSent = 0;

                if (received.source.isCached(received.low)) {
                    firstSent = received.low;
                } 

                for (int i = 0; i < 32; i++) {
                    if (((received.bitmask >> i) & 0x01) > 0) {
                        long seqno = received.low + i + 1;

                        if (received.source.isCached(seqno)) {
                            if (firstSent == 0) {
                                firstSent = seqno;
                            } else {
                                bitsSent |= (0x1 << (seqno - firstSent - 1));
                            }
                        }
                    }
                }

                if (firstSent > 0) {
                    received.low = firstSent;
                    received.bitmask = bitsSent;

                    dc.lossTab.add(received);
                    resendTimer(received);
                    startTimer();
                }
            }
        }
    }

    /*
     * allow partial resend.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    private void resend(LrmpLossEvent ev) {
        long firstSent = 0;
        int bitsSent = 0;

        if (resend(ev.low, ev)) {
            firstSent = ev.low;
        } 

        for (int i = 0; i < 32; i++) {
            if (((ev.bitmask >> i) & 0x01) > 0) {
                long seqno = ev.low + i + 1;

                if (resend(seqno, ev)) {
                    if (firstSent == 0) {
                        firstSent = seqno;
                    } else {
                        bitsSent |= (0x1 << (seqno - firstSent - 1));
                    }
                }
            }
        }

        if (Logger.debug) {
            Logger.debug(this, "send R_NACK " + ev.reporter);
        } 

        /* send NACK reply if did resend */

        if (firstSent > 0) {
            LrmpPacket reply = new LrmpPacket(false, 64);

            reply.scope = ev.scope;
            reply.offset = 0;

            reply.appendNackReply(ev, cxt.whoami, (int) firstSent, bitsSent);
            cxt.lrmp.sendControlPacket(reply, ev.scope);

            ev.domain.stats.nackReply++;
        }
    }

    /*
     * really resend repairs if
     * o the packet is cached and
     * o last send is in a scope smaller than reported scope or
     * o no repair received after the reception time of the event.
     * return true if the packet is resent.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param seqno
     * @param ev
     *
     * @return
     *
     * @see
     */
    private boolean resend(long seqno, LrmpLossEvent ev) {
        LrmpPacket p = ev.source.getPacket(seqno);

        if (p == null) {
            if (Logger.debug) {
                Logger.debug(this, "unable resend #" + seqno);
            } 

            return false;
        }

        p.retransmitID = (int) ev.low;

        cxt.sender.enqueueResend(p, ev.scope);

        return true;
    }

}

