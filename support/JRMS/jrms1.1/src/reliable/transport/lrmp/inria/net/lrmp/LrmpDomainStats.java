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
 * LrmpDomainStats.java - error recovery domain stats.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: May 5 1997.
 * Updated: no.
 */
package inria.net.lrmp;

import java.util.*;

// import inria.util.*;

/**
 * statistic data about local error recovery.
 */
public final class LrmpDomainStats implements Cloneable {

    /**
     * the scope value.
     */
    public int scope;

    /**
     * the scope of the parent.
     */
    public int parentScope;

    /**
     * the scope of the child.
     */
    public int childScope;

    /**
     * the mean round-trip time in units of 1/8 milliseconds.
     */
    public int mrtt;

    /**
     * the state.
     */
    public boolean enabled = false;

    /**
     * the number of NACK packets.
     */
    public int nack = 0;

    /**
     * the number of duplicate NACK packets.
     */
    public int dupNack = 0;

    /**
     * the number of retransmitted data packets.
     */
    public int repairPackets = 0;

    /**
     * the number of retransmitted bytes.
     */
    public int repairBytes = 0;

    /**
     * the number of duplicate data packets.
     */
    public int dupPackets = 0;

    /**
     * the number of duplicate bytes.
     */
    public int dupBytes = 0;

    /**
     * the number of NACK reply packets.
     */
    public int nackReply = 0;

    /**
     * the number of repair packets sent by a third party, not the
     * original sender.
     */
    public int thirdPartyRepairs = 0;

    /**
     * the number of duplicate packets sent by a third party, not the
     * original sender.
     */
    public int thirdPartyDuplicates = 0;

    /**
     * returns the mean round trip time in milliseconds.
     */
    public int getRTT() {
        return (mrtt >> 3);
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

