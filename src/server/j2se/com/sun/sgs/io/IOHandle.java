package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents a handle for sending and receiving byte data. {@code IOHandle}s
 * have an associated {@link IOHandler} for receiving events on the handle.
 * A handle may be a reliable connection, such as a TCP connection, or
 * unreliable, such as UDP.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOHandle {

    /**
     * Sends a byte message on this handle. The caller should relinquish
     * ownship of the given byte array, and should not attempt to manipulate
     * its contents after calling this method.
     * 
     * @param message the message to send
     * 
     * @throws IOException if there was a problem sending the message
     */
    void sendBytes(byte[] message) throws IOException;

    /**
     * Attempts to close the underlying connection. Note that the connection
     * should not be considered closed until {@link IOHandler#disconnected}
     * is called.
     * 
     * @throws IOException if there was a problem closing the handle
     */
    void close() throws IOException;

}
