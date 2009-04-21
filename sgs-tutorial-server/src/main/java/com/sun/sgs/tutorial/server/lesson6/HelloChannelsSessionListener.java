/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
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
    private final ManagedReference<ClientSession> sessionRef;
    
    /** The name of the {@code ClientSession} for this listener. */
    private final String sessionName;

    /**
     * Creates a new {@code HelloChannelsSessionListener} for the session.
     *
     * @param session the session this listener is associated with
     * @param channel1 a reference to a channel to join
     */
    public HelloChannelsSessionListener(ClientSession session,
                                        ManagedReference<Channel> channel1)
    {
        if (session == null) {
            throw new NullPointerException("null session");
        }

        DataManager dataMgr = AppContext.getDataManager();
        sessionRef = dataMgr.createReference(session);
        sessionName = session.getName();
        
        // Join the session to all channels.  We obtain the channel
        // in two different ways, by reference and by name.
        ChannelManager channelMgr = AppContext.getChannelManager();
        
        // We were passed a reference to the first channel.
        channel1.get().join(session);
        
        // We look up the second channel by name.
        Channel channel2 = channelMgr.getChannel(HelloChannels.CHANNEL_2_NAME);
        channel2.join(session);
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
     * Logs when data arrives from the client, and echoes the message back.
     */
    public void receivedMessage(ByteBuffer message) {
        ClientSession session = getSession();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Message from {0}", sessionName);
        }
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
            new Object[] { sessionName, grace }
        );
    }
}
