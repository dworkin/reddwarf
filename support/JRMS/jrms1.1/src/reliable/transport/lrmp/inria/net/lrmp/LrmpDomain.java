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
 * LrmpDomain.java - context data for an error recovery domain.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 21 Sept 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;
import inria.util.EventHandler;
import inria.util.EventManager;
import inria.util.Logger;
import inria.util.NTP;

/**
 * domain context data.
 */
final class LrmpDomain {
    protected static final int MaxDisableTime = 180000;     /* millis */
    protected static final int DisableTries = 5;
    protected static final int MinRTTValue = 2;
    protected static final int MaxRTTValue = 16000;
    protected LrmpDomain parent = null;
    protected LrmpDomain child = null;
    protected int scope;
    protected int initialMRTT;
    protected LrmpDomainStats stats;

    /* the state management */

    protected long lastTimeToggle = 0;
    protected int failedNack = 0;

    /* the current loss events including local and remote */

    protected LrmpLossTable lossTab;

    /* recently received events */

    protected Vector lossHistory;
    protected static int historySize = 16;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param ttl
     *
     * @see
     */
    public LrmpDomain(int ttl) {
        stats = new LrmpDomainStats();
        stats.enabled = true;
        stats.scope = ttl;
        stats.parentScope = 0;
        stats.childScope = 0;
        scope = ttl;

        /* 200*(scope/63)^2 */

        initialMRTT = getInitialRTT(scope);
        stats.mrtt = initialMRTT << 3;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param c
     *
     * @see
     */
    protected void setParent(LrmpDomain c) {
        parent = c;
        parent.child = this;
        stats.parentScope = parent.scope;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param c
     *
     * @see
     */
    protected void setChild(LrmpDomain c) {
        child = c;
        child.parent = this;
        stats.childScope = child.scope;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ttl
     *
     * @return
     *
     * @see
     */
    public static int getInitialRTT(int ttl) {
        if (ttl <= 15) {
            return 12;
        } else if (ttl >= 126) {
            return 800;
        } 

        return (200 * ttl * ttl + 1984) / 3969;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void enable() {
        if (stats.enabled) {
            return;
        } 

        stats.enabled = true;
        failedNack = 0;
        lastTimeToggle = System.currentTimeMillis();

        if (parent != null) {
            parent.enable();
        } 
        if (Logger.debug) {
            Logger.debug(this, "ENABLE scope=" + scope);
        } 
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void disable() {
        if (parent == null ||!stats.enabled) {
            return;
        } 

        stats.enabled = false;
        lastTimeToggle = System.currentTimeMillis();

        if (child != null) {
            child.disable();
        } 
        if (Logger.debug) {
            Logger.debug(this, "DISABLE " + scope + " fails=" + failedNack);
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
    protected boolean isEnabled() {
        return stats.enabled;
    }

    /*
     * implements the disable conditions.
     */

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    protected void checkState() {

        /* top level domain always enabled */

        if (parent == null) {
            return;
        } 
        if (stats.enabled) {
            if (failedNack > DisableTries) {
                disable();
            } 
        } else {
            if ((System.currentTimeMillis() - lastTimeToggle) 
                    > MaxDisableTime) {
                enable();
            } 
        }
    }

    /**
     * MRTT = 0.875*MRTT + 0.125*rtt = MRTT + 0.125*(rtt - MRTT).
     */
    protected void updateMRTT(int rtt) {
        if (rtt > MinRTTValue && rtt < MaxRTTValue) {
            rtt -= (stats.mrtt >> 3);
            stats.mrtt += rtt;

            if (child != null && child.stats.mrtt > stats.mrtt) {
                child.setMRTT(stats.mrtt);
            } 
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param rtt
     *
     * @see
     */
    protected void setMRTT(int rtt) {
        stats.mrtt = rtt;

        if (child != null && child.stats.mrtt > stats.mrtt) {
            child.setMRTT(rtt);
        } 
    }

    /*
     * check if the received NACK is duplicate. History holds a list of recently
     * received NACK events.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param event
     *
     * @return
     *
     * @see
     */
    protected boolean isDuplicate(LrmpLossEvent event) {
        boolean dup = false;
        int slice = stats.mrtt >> 3;

        if (event.source.interval < 200) {
            slice += event.source.interval;
        } else {
            slice += 200;
        }

        for (int i = lossHistory.size() - 1; i >= 0; i--) {
            LrmpLossEvent ev1 = (LrmpLossEvent) lossHistory.elementAt(i);

            if (ev1.source != event.source) {
                continue;
            } 

            int diff = (int) (event.rcvSendTime - ev1.rcvSendTime);

            if (diff < slice) {
                if (ev1.contains(event)) {
                    dup = true;

                    if (Logger.debug) {
                        Logger.debug(this, "Dup NACK: " + diff + "<" + slice);
                    } 

                    break;
                }
            } else {

                /* keep the most recent */

                if (event.contains(ev1)) {
                    if (Logger.debug) {
                        Logger.debug(this, "Repeated nack in " + diff);
                    } 

                    lossHistory.removeElement(ev1);
                }
            }
        }

        if (!lossHistory.contains(event)) {
            if (lossHistory.size() > historySize) {
                lossHistory.removeElementAt(0);
            } 

            lossHistory.addElement(event);
        }

        return dup;
    }

}

