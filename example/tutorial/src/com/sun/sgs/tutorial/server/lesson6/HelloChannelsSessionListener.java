/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

/**
 * Simple example {@link ClientSessionListener} for the Project Darkstar
 * Server.
 * <p>
 * Logs each time a session receives data or logs out, and echoes
 * any data received back to the sender.
 */
class HelloChannelsSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloChannelsSessionListener.class.getName());

    /** The session this {@code ClientSessionListener} is listening to. */
    private final ManagedReference sessionRef;

    /**
     * Creates a new {@code HelloChannelsSessionListener} for the given
     * session, and joins it to the given channels.
     *
     * @param session the session this listener is associated with
     * @param channel1 a channel to join
     */
    public HelloChannelsSessionListener(ClientSession session,
        Channel channel1)
    {
        if (session == null)
            throw new NullPointerException("null session");

        if (channel1 == null)
            throw new NullPointerException("null channel1");

        DataManager dataMgr = AppContext.getDataManager();

        sessionRef = dataMgr.createReference(session);

        // Join to channel1
        channel1.join(session);

        // Lookup channel2 by name
        Channel channel2 =
            dataMgr.getBinding(HelloChannels.CHANNEL_2_NAME, Channel.class);

        // Join to channel2
        channel2.join(session);
    }

    /**
     * Returns the session for this listener.
     * 
     * @return the session for this listener
     */
    protected ClientSession getSession() {
        // We created the ref with a non-null session, so no need to check it.
        return sessionRef.get(ClientSession.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when data arrives from the client, and echoes the message back.
     */
    public void receivedMessage(byte[] message) {
        ClientSession session = getSession();

        logger.log(Level.INFO, "Message from {0}", session.getName());

        // Echo message back to sender
        session.send(message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when the client disconnects.
     */
    public void disconnected(boolean graceful) {
        ClientSession session = getSession();
        String grace = graceful ? "graceful" : "forced";
        logger.log(Level.INFO,
            "User {0} has logged out {1}",
            new Object[] { session.getName(), grace }
        );
    }
}
