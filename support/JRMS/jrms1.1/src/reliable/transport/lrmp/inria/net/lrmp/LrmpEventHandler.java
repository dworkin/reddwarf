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
 * LrmpEventHandler.java - Application Interface used by Light-Weight Reliable
 * Multicast Protocol.
 * Author:  Tie Liao (Tie.Liao@inria.fr).
 * Created: 23 April 1997.
 * Updated: no.
 */
package inria.net.lrmp;

/**
 * this is the event interface that an application should implement to process data
 * and control events received from the LRMP session. The event handler is set through
 * LrmpProfile when creating an Lrmp object.
 */
public interface LrmpEventHandler {

    /**
     * the event type: unrecoverable reception error. This event is generated
     * when a part of data is missing in the received data stream, generally
     * due to serious network problems.
     */
    public static final int UNRECOVERABLE_SEQUENCE_ERROR = 1;

    /**
     * the event type: end of sequence. This event is generated when a data sender
     * is lost or gone. It allows upper layer to clean-up incomplete data object.
     */
    public static final int END_OF_SEQUENCE = 2;

    /**
     * Processes a data packet received from LRMP. This method is called each time
     * an in-order data packet is received. If some data is missing due to unrecoverable
     * reception error, an error event will be first notified.
     * @param pack the received data packet.
     */
    public void processData(LrmpPacket pack);

    /**
     * Processes an event received from LRMP. This method is called to notify
     * control events. The type of the data argument is LrmpErrorEvent for
     * UNRECOVERABLE_SEQUENCE_ERROR or LrmpEntity for END_OF_SEQUENCE.
     * @param event the event type.
     * @param data the event-dependent data.
     */
    public void processEvent(int event, Object data);
}

