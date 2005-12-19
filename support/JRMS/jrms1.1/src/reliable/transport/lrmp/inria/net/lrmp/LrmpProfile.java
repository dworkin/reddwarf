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
 * LrmpProfile.java - Profile for LRMP.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 30 September 1996.
 * Updated: no.
 */
package inria.net.lrmp;

/**
 * LRMP profile is used to configure an LRMP object from an application.
 * An application should use this object to do QoS settings.
 * <p>
 * When the profile is set, Lrmp makes a local copy to prevent erroneous
 * settings on the same profile by the application. Thus when the application
 * modifies the current settings, it should call Lrmp.setProfile() to make it
 * to take effect.
 */
public class LrmpProfile implements Cloneable {

    /**
     * The reliability requirement: loss tolerable.
     */
    public static final int LossAllowed = 1;

    /**
     * The reliability requirement: limited loss.
     */
    public static final int LimitedLoss = 2;

    /**
     * The reliability requirement: no loss.
     */
    public static final int NoLoss = 3;

    /**
     * The flow control: best effort.
     */
    public static final int BestEffort = 1;

    /**
     * The flow control: constant rate.
     */
    public static final int ConstantThroughput = 2;

    /**
     * The flow control: adapted rate.
     */
    public static final int AdaptedThroughput = 3;

    /**
     * The feedback mechanism: no report.
     */
    public static final int NoReceiverReport = 1;

    /**
     * The feedback mechanism: random report.
     */
    public static final int RandomReceiverReport = 2;

    /**
     * The feedback mechanism: periodic report.
     */
    public static final int PeriodicReceiverReport = 3;

    /**
     * The packet ordering. By default, the packet ordering is set to true.
     */
    public boolean ordered = true;

    /**
     * The loss control setting. The default value is NoLoss.
     */
    public int reliability = NoLoss;

    /**
     * The rate control scheme. The default value is AdaptedThroughput.
     */
    public int throughput = AdaptedThroughput;

    /**
     * The bandwidth to use for data transmission, in kbits/sec.
     * @deprecated, replaced by minRate and maxRate.
     */
    public int bandwidth = 64;

    /**
     * The expected minimum data rate, in kbits/sec. The default value is 8 kb/s.
     */
    public int minRate = 8;

    /**
     * The expected maximum data rate, in kbits/sec. The default value is 64 kb/s.
     */
    public int maxRate = 64;

    /**
     * The send window size, in number of packets. The default value is 64.
     */
    public int sendWindowSize = 64;

    /**
     * The reception window size, in number of packets. The default value is 64.
     */
    public int rcvWindowSize = 64;

    /**
     * A flag to enable sending repairs when the local user is a receiver.
     * The default value is true.
     */
    public boolean sendRepair = true;

    /**
     * The receiver report scheme, it only makes sense for data senders.
     */
    public int rcvReportSelection = RandomReceiverReport;

    /**
     * Constructs a LrmpProfile.
     */
    public LrmpProfile() {}

    /* notifications */

    LrmpEventHandler handler;

    /**
     * Sets QoS parameters.
     * @param reliability loss allowed or not.
     * @param ordered ordered packet delivery or not.
     * @param throughput rate control algorithm to use.
     */
    public void setQoS(int reliability, boolean ordered, int throughput) {
        this.reliability = reliability;
        this.ordered = ordered;
        this.throughput = throughput;
    }

    /**
     * Sets the event handler of LRMP.
     * @param handler the event handler.
     */
    public void setEventHandler(LrmpEventHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns true if packet loss is allowed.
     */
    public boolean lossAllowed() {
        return reliability == LossAllowed;
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
            LrmpProfile obj = (LrmpProfile) super.clone();

            return obj;
        } catch (CloneNotSupportedException e) {

            // this shouldn't happen, since we are Cloneable

            throw new InternalError();
        }
    }

}

