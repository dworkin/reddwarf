/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * Simple example {@link ClientSessionListener} for the Sun Game Server.
 * <p>
 * Logs each time a session receives data or logs out, and echoes
 * any data received back to the sender.
 */
class HelloEchoSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloEchoSessionListener.class.getName());

    /** The session this {@code ClientSessionListener} is listening to. */
    private final ClientSession session;

    /**
     * Creates a new {@code HelloEchoSessionListener} for the given session.
     *
     * @param session the session this listener is associated with
     */
    public HelloEchoSessionListener(ClientSession session) {
        this.session = session;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when data arrives from the client, and echoes the message back.
     */
    public void receivedMessage(byte[] message) {
        logger.log(Level.INFO, "Direct message from {0}", session.getName());

        // Echo message back to sender
        session.send(message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when the client disconnects.
     */
    public void disconnected(boolean graceful) {
        String grace = graceful ? "graceful" : "forced";
        logger.log(Level.INFO,
            "User {0} has logged out {1}",
            new Object[] { session.getName(), grace }
        );
    }
}
