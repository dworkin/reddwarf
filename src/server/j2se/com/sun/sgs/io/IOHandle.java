package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents a connection for data communication, independent of
 * the QoS properties of the connection's transport or protocol.
 * An {@code IOHandle} invokes its associated {@link IOHandler} with
 * asynchonous events from the underlying connection.  An
 * {@link IOFilter} associated with each handle may perform additional
 * processing on message data before it is sent or delivered.
 *
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOHandle {

    /**
     * Asynchronously sends data on the underlying connection after
     * filtering it through this handle's associated {@link IOFilter}.
     * <p>
     * Note: the filter may modify the data in any way, and may send it
     * on the underlying connection whole, in pieces, or not at all. 
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     *
     * @param message the message data to send on the connection.
     *
     * @throws IOException if there was a synchronous problem 
     *         sending the message.
     */
    void sendBytes(byte[] message) throws IOException;

    /**
     * Asynchronously closes the underlying connection, freeing any
     * resources in use.  The connection should not be considered
     * closed until {@link IOHandler#disconnected} is invoked.
     *
     * @throws IOException if there was a synchronous problem closing
     *         the connection.
     */
    void close() throws IOException;
}
