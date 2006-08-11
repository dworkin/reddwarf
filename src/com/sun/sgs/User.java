package com.sun.sgs;

import java.nio.ByteBuffer;


/**
 * This interface represents a single connected user or client. It is
 * used for identification and handling communication.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface User
{

    /**
     * FIXME: This needs accessors to the Subject detail, but rather than
     * the previous structure, we probably want to come up with some
     * abstraction a little easier to work with.
     */

    /**
     * Sends a message to the user. This is not sent over a channel,
     * but instead is sent directly to the client.
     *
     * @param data the content of the message
     * @param quality the preferred quality of service paramaters
     */
    public void send(ByteBuffer data, Quality quality);

}
