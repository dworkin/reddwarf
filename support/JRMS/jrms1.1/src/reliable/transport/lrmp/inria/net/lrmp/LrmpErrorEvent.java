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
 * LrmpErrorEvent.java - error event to be notified to applications.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 5 May 1997.
 * Updated: no.
 */
package inria.net.lrmp;

/**
 * encapsulates the information about an unrecoverable sequence error event.
 * Such an event is generated if one of the following cases happened:
 * <ul>
 * <li>old missing data is not yet recovered while a lot of new data arrived.
 * <li>no response to the error reports from the sender.
 * </ul>
 * In these cases, data reception will be synchronized to the latest data
 * sequence.
 */
public class LrmpErrorEvent {

    /**
     * The error cause: unknown.
     */
    public static final int Unknown = 0;

    /**
     * The error cause: out of buffer error, i.e., no enough buffer space.
     */
    public static final int BufferOverrun = 1;

    /**
     * The error cause: maximum number of repair requests reached.
     */
    public static final int MaxTriesReached = 2;

    /**
     * The error cause: the sender is lost.
     */
    public static final int SenderLost = 3;

    /**
     * The error cause: the sender is gone.
     */
    public static final int SenderGone = 4;

    /**
     * The sender.
     */
    public LrmpSender source;

    /**
     * The loser, generally the local user.
     */
    public LrmpEntity loser;

    /**
     * The sequence number at which the error occured.
     */
    public int seqlost;

    /**
     * The number of lost packets.
     */
    public int losts;

    /**
     * The error cause.
     */
    public int cause;

    /**
     * the time to send last repair request.
     */
    protected long timeoutTime;     /* for send report */

    /*
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("reception failure@");
        sb.append(Integer.toString(seqlost));
        sb.append(": ");

        switch (cause) {

        case BufferOverrun: 
            sb.append("buffer overrun");

            break;

        case MaxTriesReached: 
            sb.append("> max repair tries");

            break;

        case SenderLost: 
            sb.append("sender lost");

            break;

        case SenderGone: 
            sb.append("sender gone");

            break;

        default: 
            sb.append("unknown cause");

            break;
        }

        return sb.toString();
    }

}

