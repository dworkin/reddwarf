package com.sun.sgs.io;

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
 * Some xample applications of {@code IOFilter} implementations include
 * fragment reassembly, packet logging, profanity filters, and filter
 * chaining.
 */
public interface IOFilter {

    /**
     * Invoked after a caller has requested data be sent on the associated
     * {@code IOHandle}. The filter may modify the data in any way, and may
     * send it on the underlying connection whole, in pieces, or not at all.
     *
     * @param handle the {@link IOHandle} on which to send the data
     * @param message the data to filter and optionally send on the
     *        connection represented by the given {@code handle}
     */
    void filterSend(IOHandle handle, byte[] message);

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
    void filterReceive(IOHandle handle, byte[] message);

}
