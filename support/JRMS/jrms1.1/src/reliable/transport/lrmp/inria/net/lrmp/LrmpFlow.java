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
 * LrmpFlow.java - send data and flow control.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: May 5, 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;
import inria.util.Logger;

/**
 * implements sender flow control mechanism.
 * - in LRMP, one thread can not manage sending and reception.
 * - due to the back-off timer, if resent packets are lost, need longer
 * time to repair.
 */
final class LrmpFlow implements Runnable {
    LrmpContext cxt;

    /* over a factor of 8 */

    protected static final int BigDecrease = 2;         /* 0.25 */
    protected static final int MediumDecrease = 4;      /* 0.5 */
    protected static final int SmallDecrease = 6;       /* 0.75 */
    protected static final int None = 8;                /* 1.0 */
    protected static final int SmallIncrease = 9;       /* 1.125 */
    protected static final int MediumIncrease = 12;     /* 1.5 */
    protected static final int BigIncrease = 16;        /* 2.0 */

    /*
     * flow control parameters, rate is in bytes/sec.
     */
    private int lastPackets = 0;                        /* # of packets sent */
    private int lastBytes = 0;                          /* # of bytes sent */
    private long lastTime = 0;                          /* the rate was measured */
    private Thread thread = null;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param context
     *
     * @see
     */
    LrmpFlow(LrmpContext context) {
        cxt = context;
        lastTime = System.currentTimeMillis();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     *
     * @see
     */
    public void enqueue(LrmpPacket pack) {
        cxt.sendQueue.enqueue(pack);

        if (thread == null) {
            start();
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void flush() {
        cxt.sendQueue.sync();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private synchronized void start() {
        if (thread == null) {
            thread = new Thread(this);

            thread.setName(getClass().getName());
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.start();
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void stop() {

        /*
         * need wait to finish sending and resending.
         */
        thread = null;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public synchronized void run() {
        while (thread != null) {

            /* give priority to resend */

            if (cxt.resendQueue.size() > 0) {
                resend();
            } 
            if (cxt.sendQueue.getSize() == 0) {
                break;
            } 

            /* get one packet from the queue */

            LrmpPacket pack = (LrmpPacket) cxt.sendQueue.dequeue();

            /* send a packet */

            pack.source = cxt.whoami;
            pack.sender = pack.source;

            if (pack.isReliable()) {
                pack.seqno = cxt.whoami.expected();

                cxt.whoami.incExpected();

                /*
                 * as we know the sequence number is incremented by one, we can
                 * safely append the packet to the send window which keeps a pool
                 * of packets sorted by seqno.
                 */
                cxt.whoami.appendPacket(pack);

                if (Logger.debug) {
                    Logger.debug(this, 
                                 "sending #" + pack.seqno + " len=" 
                                 + pack.getDataLength());
                } 
            }

            pack.scope = cxt.lrmp.getTTL();

            cxt.lrmp.sendDataPacket(pack, false);
            flowControl();

            /* wait a transmission interval */

            if (cxt.profile.throughput != LrmpProfile.BestEffort 
                    && cxt.sndInterval > 0) {
                try {
                    wait(cxt.sndInterval);
                } catch (InterruptedException e) {
                    Logger.error(this, "interrupted!");
                }
            }
        }

        thread = null;

        cxt.lrmp.idle();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private void resend() {
        while (true) {
            LrmpPacket pack = (LrmpPacket) cxt.resendQueue.dequeue();

            if (pack == null) {
                break;
            } 
            if (Logger.debug) {
                Logger.debug(this, 
                             "resending #" + pack.seqno + " @" + pack.scope);
            } 

            pack.sender = cxt.whoami;

            cxt.lrmp.sendDataPacket(pack, true);

            if (cxt.resendQueue.size() > 0) {
                flowControl();

                if (cxt.profile.throughput != LrmpProfile.BestEffort 
                        && cxt.sndInterval > 0) {
                    try {
                        wait(cxt.sndInterval);
                    } catch (InterruptedException e) {
                        Logger.error(this, "interrupted!");
                    }
                }
            } else {
                break;
            }
        }
    }

    /*
     * rate adaptation, how much time to wait, etc.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private void flowControl() {
        int pcount = cxt.whoami.packets - lastPackets;

        if (pcount < cxt.checkInterval) {
            return;
        }

        lastPackets = cxt.whoami.packets;

        int bcount = cxt.whoami.bytes - lastBytes;

        lastBytes = cxt.whoami.bytes;

        long cur = System.currentTimeMillis();

        cxt.actualRate = bcount * 1000 / ((int) (cur - lastTime));

        cxt.whoami.setRate(cxt.actualRate);

        lastTime = cur;

        if (cxt.profile.throughput == LrmpProfile.ConstantThroughput) {
            return;
        } 

        cxt.curRate = (cxt.curRate * cxt.adjust) >> 3;

        if (cxt.curRate < cxt.profile.minRate) {
            cxt.curRate = cxt.profile.minRate;
        } else if (cxt.curRate > cxt.profile.maxRate) {
            cxt.curRate = cxt.profile.maxRate;
        } 

        cxt.adjust = SmallIncrease;

        if (cxt.whoami.bytes > 0) {
            cxt.sndInterval = (bcount * 1000 / pcount) / cxt.curRate;
        } 

        /* due to CPU load and break */

        if (cxt.actualRate < ((cxt.curRate * 3) >> 2)) {
            cxt.sndInterval = (cxt.sndInterval * 3) >> 2;
        } 
        if (cxt.sndInterval > 30000) {
            cxt.sndInterval = 30000;
        } 
        if (Logger.debug) {
            Logger.debug(this, 
                         "rate/interval: " + cxt.curRate + "/" 
                         + cxt.sndInterval);
        } 
    }

    /*
     * Adjust data transmission rate. What we do is to measure the effective
     * rate which is the rate at which we send minus loss rate. Rate is
     * adpated to loss.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    private void updateRate() {}

    /*
     * Clear windows.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void reset() {
        cxt.sendQueue.clear();
        cxt.resendQueue.clear();
        cxt.whoami.clearCache(cxt.whoami.expected());
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param pack
     * @param scope
     *
     * @see
     */
    public void enqueueResend(LrmpPacket pack, int scope) {
        if (cxt.resendQueue.contains(pack)) {
            if (pack.scope < scope) {
                pack.scope = scope;
            } 

            return;
        }

        pack.scope = scope;

        cxt.resendQueue.enqueue(pack);

        if (thread == null) {
            start();
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param s
     * @param seqno
     * @param scope
     *
     * @see
     */
    public void cancelResend(LrmpSender s, long seqno, int scope) {
        cxt.resendQueue.remove(s, seqno, scope);
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
    public void cancelResend(LrmpSender s, int id, int scope) {
        cxt.resendQueue.cancel(s, id, scope);
    }

}

