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
 * LrmpContext.java - LRMP context.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 17 Sept 1998.
 * Updated: no.
 */
package inria.net.lrmp;

import java.net.InetAddress;
import java.util.*;
import inria.util.*;

/**
 * LRMP working context.
 */
final class LrmpContext {
    protected LrmpSender whoami;
    protected LrmpProfile profile;
    protected LrmpStats stats;

    /* control objects */

    protected LrmpImpl lrmp;
    protected LrmpFlow sender;
    protected LrmpRecovery recover;
    protected LrmpEntityManager sm;

    /* flow/congestion control data, rate is in bytes/sec */

    protected int adjust = 
        LrmpFlow.SmallIncrease;         /* scaled by a factor of 8 */
    protected int curRate = 0;          /* dynamically adapted rate */
    protected int actualRate = 0;       /* the actual rate */
    protected int checkInterval;        /* adaptation interval in # of packets */
    protected int sndInterval = 100;

    /* output */

    protected static final int MaxQueueSize = 16;
    protected FIFOQueue sendQueue;
    protected LrmpPacketQueue resendQueue;
    protected int senderReportInterval = 4000;
    public int rcvReportSelInterval = 30000;
    protected static EventManager timer = null;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @see
     */
    protected LrmpContext() {
        sendQueue = new FIFOQueue(MaxQueueSize);
        resendQueue = new LrmpPacketQueue();
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param prof
     *
     * @see
     */
    protected void setProfile(LrmpProfile prof) {

        /* keep a cloned profile to prevent change by upper layer */

        profile = (LrmpProfile) prof.clone();

        if (profile.sendWindowSize >= 32) {
            whoami.cacheSize = profile.sendWindowSize;

            if (whoami.cache != null) {
                whoami.initCache(profile.sendWindowSize);
            } 
        }

        sm.profile = profile;

        if (Logger.debug) {
            Logger.debug(this, 
                         "rcv/snd window:" + profile.rcvWindowSize + "/" 
                         + profile.sendWindowSize);
        } 

        /*
         * check the data rate and converts kilo bits/sec to bytes/sec.
         */
        if (profile.minRate <= 0) {

            /* the rate should be greater than zero */

            profile.minRate = 125;
        } else {
            profile.minRate = (profile.minRate * 1000) >> 3;
        }

        profile.maxRate = (profile.maxRate * 1000) >> 3;

        if (profile.maxRate <= profile.minRate) {
            profile.maxRate = profile.minRate;
        } 

        /* init for the first time only */

        if (curRate == 0) {
            curRate = (profile.minRate + profile.maxRate) >> 1;

            if (curRate < profile.minRate) {
                curRate = profile.minRate;
            } 

            sndInterval = LrmpPacket.MTU * 1000 / curRate;
        }

        checkInterval = profile.sendWindowSize >> 3;

        if (checkInterval < 4) {
            checkInterval = 4;
        } 
        if (Logger.debug) {
            Logger.debug(this, 
                         "min/cur/max rate: " + profile.minRate + "/" 
                         + curRate + "/" + profile.maxRate 
                         + " send/check interval: " + sndInterval + "/" 
                         + checkInterval);
        } 
    }

}

