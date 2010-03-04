/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedReference;

/**
 * Simple example {@link ClientSessionListener} for the Project Darkstar
 * Server.
 * <p>
 * Logs each time a session receives data or logs out.
 */
class HelloUserSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloUserSessionListener.class.getName());

    /** The session this {@code ClientSessionListener} is listening to. */
    private final ManagedReference<ClientSession> sessionRef;
    
    /** The name of the {@code ClientSession} for this listener. */
    private final String sessionName;

    /**
     * Creates a new {@code HelloUserSessionListener} for the given session.
     *
     * @param session the session this listener is associated with
     */
    public HelloUserSessionListener(ClientSession session) {
        if (session == null) {
            throw new NullPointerException("null session");
        }

        sessionRef = AppContext.getDataManager().createReference(session);
        sessionName = session.getName();
    }

    /**
     * Returns the session for this listener.
     * 
     * @return the session for this listener
     */
    protected ClientSession getSession() {
        // We created the ref with a non-null session, so no need to check it.
        return sessionRef.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when data arrives from the client.
     */
    public void receivedMessage(ByteBuffer message) {
        logger.log(Level.INFO, "Message from {0}", sessionName);
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
                   new Object[] { sessionName, grace }
        );
    }
}
