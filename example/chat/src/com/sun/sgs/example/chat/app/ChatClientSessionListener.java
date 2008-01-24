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

package com.sun.sgs.example.chat.app;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Listener for events from a particular {@link ClientSession} logged into a
 * {@code ChatApp} application.
 * <p>
 * Note that this {@link ClientSessionListener} has no mutable state, so it
 * doesn't need to implement {@link com.sun.sgs.app.ManagedObject}.  This
 * means it will be garbage-collected once its associated session is
 * {@linkplain ChatClientSessionListener#disconnected disconnected}, so we
 * don't explicitly have to remove it from the data store.
 */
public class ChatClientSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(ChatClientSessionListener.class.getName());

    /** The {@link Charset} encoding for client/server messages. */
    private static final String MESSAGE_CHARSET = "UTF-8";

    /** The command prefix: "{@value #COMMAND_PREFIX}" */
    private static final String COMMAND_PREFIX = "/";

    /** The channel prefix: "{@value #CHANNEL_PREFIX}" */
    private static final String CHANNEL_PREFIX = "#";

    /** The session prefix: "{@value #SESSION_PREFIX}" */
    private static final String SESSION_PREFIX = "@";

    /** The {@link ClientSession} this listener receives events for. */
    private final ManagedReference sessionRef;

    /**
     * Creates a new listener for the given {@code ClientSession}.
     * Immediately joins the session to the global notification channel,
     * and sends membership change notifications as appropriate.
     *
     * @param session this listener's {@code ClientSession}
     *
     * @see #addToChannel
     */
    public ChatClientSessionListener(ClientSession session) {
        if (session == null)
            throw new NullPointerException("null session");

        DataManager dataMgr = AppContext.getDataManager();
        sessionRef = dataMgr.createReference(session);

        dataMgr.setBinding(sessionKey(getSessionIdString()), session);

        String sessionIdAndName =
            getSessionIdString() + " " + session.getName();

        ChatApp.globalChannel().join(session);

        String loginMessage = "/login " + sessionIdAndName;
        ChatApp.globalChannel().send(toMessageBytes(loginMessage));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation sends a broadcast notification to other
     * sessions to inform them of the disconnected session.
     */
    public void disconnected(boolean graceful) {
        if (logger.isLoggable(Level.FINE)) {
            String grace = graceful ? "graceful" : "forced";
            logger.log(Level.FINE,
                "{0} {1} disconnect",
                new Object[] { session(), grace });
        }

        String disconnectMessage = "/disconnected " + getSessionIdString();
        ChatApp.globalChannel().send(toMessageBytes(disconnectMessage));

        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.removeBinding(sessionKey(getSessionIdString()));
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
        try {
            String messageString = fromMessageBytes(message);

            // Split at the first run of whitespace, if any
            String[] args = messageString.split("\\s+", 2);

            if (messageString.startsWith(CHANNEL_PREFIX)) {
                broadcastOnChannel(args[0].substring(1), args[1]);
                return;
            }

            if (messageString.startsWith(SESSION_PREFIX)) {
                sendToSession(args[0].substring(1), args[1]);
            }

            // Check that the command begins with a forward-slash
            if (! messageString.startsWith(COMMAND_PREFIX)) {
                throw new IllegalArgumentException(
                    "Command must start with " + COMMAND_PREFIX);
            }

            // Find the ChatCommand for this command
            String commandString = args[0].substring(1).toUpperCase();
            ChatCommand command = ChatCommand.valueOf(commandString);

            switch (command) {
            case JOIN:
                addToChannel(args[1].substring(1));
                break;

            case LEAVE:
                removeFromChannel(args[1].substring(1));
                break;

            case PING:
                echo(args[1]);
                break;

            case DISCONNECT:
                logger.log(Level.INFO, "Disconnect {0}", session());
                session().disconnect();
                break;

            case SHUTDOWN:
                logger.log(Level.CONFIG, "Shutdown from {0}", session());
                System.exit(0);
                break;
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.INFO)) {
                LogRecord rec = new LogRecord(Level.INFO,
                    "While processing command from {0}, disconnecting:");
                rec.setThrown(e);
                rec.setParameters(new Object[] { session() });
                logger.log(rec);
            }
            
            session().disconnect();
        }
    }

    /**
     * TODO
     * 
     * @param channelName
     * @param message
     */
    private void broadcastOnChannel(String channelName, String message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Broadcast request from {0} on {1}, contents: {2}",
                new Object[] { session(), channelName, message });
        }

        String bcastMsg = "#" + channelName +
                          " @" + getSessionIdString() + " " +
                          message;
        ChatChannel.find(channelName).send(toMessageBytes(bcastMsg));
    }

    /**
     * TODO
     * 
     * @param recipientId
     * @param message
     */
    private void sendToSession(String recipientId, String message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Unicast request from {0} to {1}, contents: {2}",
                new Object[] { session(), recipientId, message });
        }

        ClientSession recipient = findSession(recipientId);

        String privMsg = "@" + getSessionIdString() + " " + message;
        recipient.send(toMessageBytes(privMsg));
    }

    /**
     * TODO
     * 
     * @param sessionId
     * @return
     */
    private static ClientSession findSession(String sessionId) {
        DataManager dataMgr = AppContext.getDataManager();
        String key = sessionKey(sessionId);
        return dataMgr.getBinding(key, ClientSession.class);
    }

    /**
     * TODO
     * 
     * @param channelName
     * @return
     */
    private static String sessionKey(String sessionId) {
        return "ChatApp.Session." + sessionId;
    }


    /**
     * Echos the given string back to the sending session as a direct message.
     *
     * @param message the message to echo
     */
    private void echo(String message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Echo request from {0}, contents: {1}",
                new Object[] { session(), message });
        }

        String reply = "/pong " + message;
        session().send(toMessageBytes(reply));
    }

    /**
     * Joins a session to a channel and sends membership change
     * notifications as appropriate. If the channel doesn't exist,
     * it is created.
     * 
     * @param channelName the name of the channel to add this session to
     */
    private void addToChannel(String channelName) {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Join " + session() + " to " + channelName);
        }
        
        if (channelName.matches("\\s+")) {
            throw new IllegalArgumentException(
                "channel name must not contain whitespace");
        }

        ChatChannel channel = ChatChannel.findOrCreate(channelName);

        channel.join(session());

        String changeMessage = "/joined " +
                               channelName + " " +
                               getSessionIdString();
        channel.send(toMessageBytes(changeMessage));

        // Send the membership list to the joining session.
        StringBuilder listMessage = new StringBuilder("/members ");
        listMessage.append(channelName);
        for (ManagedReference memberRef : channel.memberRefs()) {
            ClientSession member = memberRef.get(ClientSession.class);
            listMessage.append(" ");
            listMessage.append(getIdString(memberRef));
        }

        session().send(toMessageBytes(listMessage.toString()));
    }

    /**
     * Removes this session from the channel and sends membership change
     * notifications as appropriate. Closes the channel if this session was
     * its last member.
     * 
     * @param channelName the name of the channel to remove this session from
     */
    private void removeFromChannel(String channelName) {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Leave {0} from {1}",
                    new Object[] { session(), channelName });
        }

        ChatChannel channel;
        try {
            channel = ChatChannel.find(channelName);
        } catch (NameNotBoundException e) {
            // The channel has been closed, so there's nothing to do.
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Leave {0} from {1}: channel is closed",
                        new Object[] { session(), channelName });
            }
            return;
        }

        channel.leave(session());

        // If the channel has no more sessions, close it.
        if (! channel.hasMembers()) {
            channel.close();
            return;
        }

        // Tell the rest of the channel about the removal.
        String changeMessage = "/left " +
                               channelName + " " +
                               getSessionIdString();
        channel.send(toMessageBytes(changeMessage));
    }


    /**
     * Returns the session this listener receives events for.
     * 
     * @return the session this listener receives events for
     */
    private ClientSession session() {
        // No null-check needed
        return sessionRef.get(ClientSession.class);
    }

    /**
     * TODO doc
     * 
     * @return
     */
    private String getSessionIdString() {
        return getIdString(sessionRef);
    }

    /**
     * TODO doc
     * 
     * @param ref
     * @return
     */
    private static String getIdString(ManagedReference ref) {
        return ref.getId().toString(16);
    }

    /**
     * Decodes the given {@code bytes} into a message string.
     *
     * @param bytes the encoded message
     * @return the decoded message string
     */
    static String fromMessageBytes(byte[] bytes) {
        try {
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Encodes the given message string into a byte array.
     *
     * @param s the message string to encode
     * @return the encoded message as a byte array
     */
    static byte[] toMessageBytes(String s) {
        try {
            return s.getBytes(MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }
}
