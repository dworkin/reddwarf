/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Simple example {@link ClientSessionListener} for the Project Darkstar
 * Server.
 * <p>
 * Logs each time a session receives data or logs out, and echoes
 * any data received back to the sender or broadcasts it to the
 * requested channel.
 */
class HelloChannelsSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloChannelsSessionListener.class.getName());

    /** The message encoding. */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The session this {@code ClientSessionListener} is listening to. */
    private final ManagedReference<ClientSession> sessionRef;

    /**
     * Creates a new {@code HelloChannelsSessionListener} for the session.
     *
     * @param session the session this listener is associated with
     */
    public HelloChannelsSessionListener(ClientSession session)
    {
        if (session == null)
            throw new NullPointerException("null session");

        DataManager dataMgr = AppContext.getDataManager();
        sessionRef = dataMgr.createReference(session);
        
        // Join the session to all channels
        for (String channelName : HelloChannels.channelNames) {
            Channel channel = findChannel(channelName);
            channel.join(session);
        }
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
        String sessionName = session.getName();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Message from {0}", sessionName);
        }

        String text = decodeString(message);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                       "{0} sends: {1}",
                       new Object[] { sessionName, text });
        }

        String[] args = text.split(" ", 2);
        
        if (args.length < 2) {
            logger.log(Level.WARNING,
                       "Malformed message from {0}",
                       sessionName);
            return;
        }

        String channelName = args[0];
        String contents = args[1];
        if (channelName.charAt(0) == '*') {
            // Direct message; print it and echo back
            logger.log(Level.FINE, "Server echo to {0}", sessionName);

            // Echo original message back to sender
            message.rewind();
            session.send(message);
        } else {
            // Channel message; broadcast to the correct channel

            try {
                logger.log(Level.FINE,
                           "Server broadcast on {0}", channelName);

                // Find the channel
                Channel channel = findChannel(channelName);

                // Construct the outbound message with
                // the sender and channel names prepended.
                String reply = "[" + sessionName +
                               "@" + channelName +
                               "] " + contents;

                // Broadcast the message
                channel.send(encodeString(reply));

            } catch (NameNotBoundException e) {
                logger.log(Level.WARNING,
                           "Channel '{0}' not found",
                           channelName);
            }
        }
    }
    
    /**
     * Return the channel with the given name.
     * @param channelName the name of the channel
     * 
     * @return the channel with the given name
     * @throws NameNotBoundException if the channel does not exist
     */
    private static Channel findChannel(String channelName) {
        DataManager dataMgr = AppContext.getDataManager();
        return (Channel) dataMgr.getBinding(channelName);
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

    /**
     * Encodes a {@code String} into a {@link ByteBuffer}.
     *
     * @param s the string to encode
     * @return the {@code ByteBuffer} which encodes the given string
     */
    protected static ByteBuffer encodeString(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }

    /**
     * Decodes a {@link ByteBuffer} into a {@code String}.
     *
     * @param buf the {@code ByteBuffer} to decode
     * @return the decoded string
     */
    protected static String decodeString(ByteBuffer buf) {
        try {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }
}
