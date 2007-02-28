/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.client.comm;

import java.io.IOException;

/**
 * Represents an abstract network connection with the Sun Game Server.
 */
public interface ClientConnection {

    /**
     * Asynchronously sends data to the server.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     *
     * @param message the message data to send
     *
     * @throws IOException if there was a synchronous problem sending
     *         the message
     */
    void sendMessage(byte[] message) throws IOException;

    /**
     * Asynchronously closes the connection, freeing any resources in use.
     * The connection should not be considered closed until
     * {@link ClientConnectionListener#disconnected} is invoked.
     *
     * @throws IOException if there was a synchronous problem closing
     *         the connection
     */
    void disconnect() throws IOException;

}
