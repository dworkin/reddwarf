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
 * LrmpLossEvent.java - loss events.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: May 6, 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;

// import inria.util.*;

/**
 * data related to packet loss events.
 */
final class LrmpLossEvent implements Cloneable {
    public LrmpSender source;
    public LrmpEntity reporter;
    public long rcvSendTime = 0;
    public int scope;
    public int timestamp;
    public long low;
    public int bitmask;
    public long high;
    public int nackCount = 0;
    public long timeoutTime;
    public static final int SendNack = 0;
    public static final int DelayAndStay = 1;
    public static final int DelayAndGoUp = 2;
    public static final int DelayAndGoDown = 3;
    public int nextAction = SendNack;
    public LrmpDomain domain;

    /*
     * Undocumented Class Constructor.
     * 
     * 
     * @param s
     *
     * @see
     */
    public LrmpLossEvent(LrmpSender s) {
        source = s;
    }

    /*
     * set the first seqno lost and succeeding lost in the bitmask.
     * In bitmask, 1 means lost.
     * If no loss exists, the low field is set -1.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    public void computeBitmask() {
        low = source.expected();

        int maxdiff = LrmpImpl.diff32(source.highestSeqnoGot(), low);

        if (maxdiff < 0) {
            low = -1;       /* no loss */

            return;
        } else if (maxdiff > 32) {
            maxdiff = 32;
        } 

        /*
         * set a bit to 1 for a packet lost.
         */
        high = low;
        bitmask = 0;

        for (int i = 1; i <= maxdiff; i++) {
            if (!source.isCached(low + i)) {
                bitmask |= (0x1 << (i - 1));
                high = low + i;
            }
        }
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @return
     *
     * @see
     */
    public boolean equals(LrmpLossEvent ev) {
        if (ev.source == source && ev.low == low && ev.bitmask == bitmask) {
            return true;
        } 

        return false;
    }

    /*
     * returns true if the lost packets reported by this event contains
     * that of the given event.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @return
     *
     * @see
     */
    public boolean contains(LrmpLossEvent ev) {
        int diff = LrmpImpl.diff32(ev.low, low);

        if (diff == 0) {
            return (ev.bitmask & ~bitmask) == 0;
        } else if (diff > 0) {
            diff = bitmask >> (diff - 1);

            if ((diff & 0x01) > 0) {
                diff >>= 1;

                return (ev.bitmask & ~diff) == 0;
            }
        }

        return false;
    }

    /*
     * remove the lost packets reported by the given event from
     * this event.
     */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @param ev
     *
     * @see
     */
    public void remove(LrmpLossEvent ev) {
        int diff = LrmpImpl.diff32(ev.low, low);

        if (diff == 0) {
            bitmask &= ~ev.bitmask;

            if (bitmask == 0) {
                low = -1;
            } else {
                int i = 1;

                for (; i < 32 & (bitmask & 0x1) == 0; i++) {
                    bitmask >>= 1;
                }

                bitmask >>= 1;
                low += i;
            }
        } else if (diff > 0) {
            bitmask &= ~(0x1 << (diff - 1));
            bitmask &= ~(ev.bitmask << diff);
        } else {
            diff = -diff;
            bitmask &= ~(ev.bitmask >> diff);

            if ((ev.bitmask & (0x1 << (diff - 1))) > 0) {
                if (bitmask == 0) {
                    low = -1;
                } else {
                    int i = 1;

                    for (; i < 32 & (bitmask & 0x1) == 0; i++) {
                        bitmask >>= 1;
                    }

                    bitmask >>= 1;
                    low += i;
                }
            }
        }
    }

    /* debug */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public String toString() {
        return reporter + "->" + source + ":" + low + "/" 
               + Integer.toHexString(bitmask) + "@" + scope;
    }

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {

            // this shouldn't happen, since we are Cloneable

            throw new InternalError();
        }
    }

}

