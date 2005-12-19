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
 * LrmpStats.java - statistic information/attendee.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 25 November 1996.
 * Updated: no.
 */
package inria.net.lrmp;

/**
 * LRMP statistic information.
 */
public final class LrmpStats implements Cloneable {

    /**
     * The number of bad-version packets.
     */
    public int badVersion = 0;

    /**
     * The number of bad-length packets.
     */
    public int badLength = 0;

    /**
     * The number of bad-type packets.
     */
    public int badPT = 0;

    /**
     * The current total data rate in bytes/sec.
     */
    public int dataRate = 0;

    /**
     * The maximum total data rate reached in bytes/sec.
     */
    public int maxDataRate = 0;

    /**
     * The current total control data rate in bytes/sec.
     */
    public int ctlRate = 0;

    /**
     * The maximum total control data rate reached in bytes/sec.
     */
    public int maxCtlRate = 0;

    /**
     * The total number of data packets, including retransmitted packets.
     */
    public int dataPackets = 0;

    /**
     * The total data bytes, including retransmitted packets.
     */
    public int dataBytes = 0;

    /**
     * The number of out-of-band packets.
     */
    public int outOfBand = 0;

    /**
     * The number of retransmitted data packets, including duplicate packets.
     */
    public int repairPackets = 0;

    /**
     * The retransmitted data bytes, including duplicate packets.
     */
    public int repairBytes = 0;

    /**
     * The number of duplicate data packets.
     */
    public int dupPackets = 0;

    /**
     * The duplicate data bytes.
     */
    public int dupBytes = 0;

    /**
     * The total number of control packets.
     */
    public int ctrlPackets = 0;

    /**
     * The total control bytes.
     */
    public int ctrlBytes = 0;

    /**
     * The total number of NACK packets, including duplicate NACK.
     */
    public int nack = 0;

    /**
     * The total number of NACK reply packets.
     */
    public int nackReply = 0;

    /**
     * The number of duplicate NACK packets.
     */
    public int dupNack = 0;

    /**
     * The number of sender report packets.
     */
    public int senderReports = 0;

    /**
     * The number of receiver report selection packets.
     */
    public int rrSelect = 0;

    /**
     * The number of receiver report packets.
     */
    public int receiverReports = 0;

    /**
     * The number of local reception failures.
     */
    public int failures = 0;

    /**
     * The population estimate.
     */
    public int populationEstimate;

    /**
     * The population estimate time.
     */
    public long populationEstimateTime;

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
            LrmpStats st = (LrmpStats) super.clone();

            return st;
        } catch (CloneNotSupportedException e) {

            // this shouldn't happen, since we are Cloneable

            throw new InternalError();
        }
    }

}

