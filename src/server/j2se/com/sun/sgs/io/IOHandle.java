package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents a connection for data communication, independent of
 * the QoS properties of the connection's transport or protocol.
 * An {@code IOHandle} invokes its associated {@link IOHandler} with
 * asynchonous events from the underlying connection.
 */
public interface IOHandle {

    /**
     * Asynchronously sends data on the underlying connection.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     *
     * @param message the message data to send on the connection
     *
     * @throws IOException if there was a synchronous problem 
     *         sending the message
     */
    void sendBytes(byte[] message) throws IOException;

    /**
     * Asynchronously closes the underlying connection, freeing any
     * resources in use.  The connection should not be considered
     * closed until {@link IOHandler#disconnected} is invoked.
     *
     * @throws IOException if there was a synchronous problem closing
     *         the connection
     */
    void close() throws IOException;

}
