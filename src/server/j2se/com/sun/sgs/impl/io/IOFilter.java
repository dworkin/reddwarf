package com.sun.sgs.impl.io;

import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

// TODO move this functionality into protocol decode; we should
// do framing in the protocol, not the transport. -JM

/**
 * Filter hooks that allow {@link IOHandle} messages to be modified before
 * being sent on the connection and/or received by an {@link IOHandler}.
 * <p>
 * The {@code filterSend} method is invoked after a caller has
 * requested data be sent on the associated {@code IOHandle}.  The filter
 * may modify the data in any way, and may send it on the underlying
 * connection whole, in pieces, or not at all. 
 * <p>
 * The {@code filterReceived} method is invoked as messages are received
 * by the {@link IOHandle} before dispatching to the handle's
 * {@code IOHandler}.  The filter may modify the incoming data in any way,
 * and may invoke the handler once, multiple times, or not at all.
 * <p>
 * Some example applications of {@code IOFilter} implementations include
 * fragment reassembly, packet logging, profanity filters, and filter
 * chaining.
 * <p>
 *
 * Includes common functionality for reading a length in a message and
 * maintaining an index into the buffer array.
 */
abstract class IOFilter {

    /**
     * Invoked after a caller has requested data be sent on the associated
     * {@code IOHandle}. The filter may modify the data in any way, and may
     * send it on the underlying connection whole, in pieces, or not at all.
     *
     * @param handle the {@link IOHandle} on which to send the data
     * @param message the data to filter and optionally send on the
     *        connection represented by the given {@code handle}
     */
    public abstract void filterSend(IOHandle handle, byte[] message);

    /**
     * Invoked as messages are received by the {@code IOHandle} before
     * dispatching to the handle's {@code IOHandler}. The filter
     * is free to modify the incoming data in any way, and to invoke
     * {@link IOHandler#bytesReceived} once, multiple times, or not at all.
     *
     * @param handle the {@link IOHandle} which received the data
     * @param message the data to filter and optionally deliver to the
     *        {@code IOHandler} for the given {@code handle}
     */
    public abstract void filterReceive(IOHandle handle, byte[] message);

    /** The offset of the array that is currently being processed. */
    protected int index;

    /**
     * Reads the next four bytes of the given array starting at the current
     * index and assembles them into a network byte-ordered int. It will
     * increment the index by four if the read was successful. It will
     * return zero if not enough bytes remain in the array.
     *
     * @param array the array from which to read bytes
     *
     * @return the next four bytes as an int, or zero if there aren't enough
     *         bytes remaining in the array
     */
    protected int readInt(byte[] array) {
        if (array.length < (index + 4)) {
            return 0;
        }
        int num =
              ((array[index]     & 0xFF) << 24) |
              ((array[index + 1] & 0xFF) << 16) |
              ((array[index + 2] & 0xFF) <<  8) |
               (array[index + 3] & 0xFF);
        
        index += 4;
        
        return num;
    }
}
