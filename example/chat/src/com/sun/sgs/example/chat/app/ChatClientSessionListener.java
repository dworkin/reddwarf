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

package com.sun.sgs.example.chat.app;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

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

    /** The name of the global channel. */
    private static final String GLOBAL_CHANNEL_NAME = "-GLOBAL-";

    /** The {@link Charset} encoding for client/server messages. */
    private static final String MESSAGE_CHARSET = "UTF-8";

    /** The command prefix: "{@value #COMMAND_PREFIX}" */
    private static final String COMMAND_PREFIX = "/";

    /** The {@link ClientSession} this listener receives events for. */
    private final ManagedReference<ClientSession> sessionRef;

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
        sessionRef = AppContext.getDataManager().createReference(session);
        addToChannel(GLOBAL_CHANNEL_NAME);
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
        removeFromChannel(GLOBAL_CHANNEL_NAME);
        ChatApp.removeSessionBinding(session());
    }

    /** {@inheritDoc} */
    public void receivedMessage(ByteBuffer message) {
        try {
            String messageString = fromMessageBuffer(message);

            // Check that the command begins with a forward-slash
            if (! messageString.startsWith(COMMAND_PREFIX)) {
                throw new IllegalArgumentException(
                    "Command must start with " + COMMAND_PREFIX);
            }

            // Split at the first run of whitespace, if any
            String[] args = messageString.split("\\s+", 2);

            // Find the ChatCommand enum for this command
            String commandString = args[0].substring(1).toUpperCase();
            ChatCommand command = ChatCommand.valueOf(commandString);

            switch (command) {
            case JOIN:
                addToChannel(args[1]);
                break;

            case LEAVE:
                removeFromChannel(args[1]);
                break;

            case PING:
                echo(args[1]);
                break;

            case PM:
                 pmReceived(args[1]);
                 break;
                 
            case DISCONNECT:
                logger.log(Level.INFO,
                           "Disconnect request from {0}",
                           session());
		disconnect();
                break;

            case SHUTDOWN:
                logger.log(Level.CONFIG,
                           "Shutdown request from {0}",
                           session());
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
            
	    disconnect();
        }
    }

    /** Returns our ClientSession. */
    private ClientSession session() {
        return sessionRef.get();
    }

    /** Disconnect this session. */
    private void disconnect() {
	try {
	    AppContext.getDataManager().removeObject(session());
	} catch (ObjectNotFoundException e) {
	    // already disconnected
	}
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

        ChannelManager channelMgr = AppContext.getChannelManager();
        Channel channel;
        try {
            channel = channelMgr.getChannel(channelName);
        } catch (NameNotBoundException e) {
            // Create the channel, and save a binding to it.
            channel = 
                channelMgr.createChannel(channelName, 
                                         new ChatChannelListener(), 
                                         Delivery.RELIABLE);
        }

        // Send the membership change first, so the new session doesn't
        // receive its own join message.
        StringBuilder changeMsg = new StringBuilder("/joined ");
        changeMsg.append(session().getName());
        channel.send(toMessageBuffer(changeMsg.toString()));

        // Now add the joiner and tell it about all the members on
        // the channel, the joiner included.
        channel.join(session());

        // Schedule a task to send the membership list to the joining session.
        // This is done in a separate task so the channel.join will be complete.
        AppContext.getTaskManager().scheduleTask(
                                       new SendMembersTask(channelName));
    }

    /** 
     * A task which sends a channel membership list to the client.
     */
    private final class SendMembersTask implements Serializable, Task {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The name of the channel who should have its members sent. */ 
	private final String channelName;

	SendMembersTask(String  channelName) {
	    this.channelName = channelName;
	}

	/**
	 * Send the list of channel members to the session.
	 */
	public void run() {
            Channel channel = 
                    AppContext.getChannelManager().getChannel(channelName);
            
            StringBuilder listMessage = new StringBuilder("/members ");
            listMessage.append(channelName);
            Iterator<ClientSession> iter = channel.getSessions();
            while (iter.hasNext()) {
                ClientSession member = iter.next();
                listMessage.append(' ');
                listMessage.append(member.getName());
            }
            session().send(toMessageBuffer(listMessage.toString()));
	}
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

        ChannelManager channelMgr = AppContext.getChannelManager();
        Channel channel;
        
        try {
            channel = channelMgr.getChannel(channelName);
        } catch (NameNotBoundException e) {
            // The channel has been closed, so there's nothing to do.
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Leave {0} from {1}: channel is closed",
                        new Object[] { session(), channelName });
            }
            return;
        }

        if (session().isConnected()) {
            // Remove the member first, so it doesn't get the membership
            // change message for its own departure.
            channel.leave(session());
        }

        // Tell the rest of the channel about the session removal.
        StringBuilder changeMessage = new StringBuilder("/left ");
        changeMessage.append(session().getName());
        channel.send(toMessageBuffer(changeMessage.toString()));
        
        // Schedule a task to check whether the channel is now empty, and if so,
        // close it.  This needs to be done in a separate task to ensure the 
        // membership change has completed before testing to see if it is empty.
        AppContext.getTaskManager().scheduleTask(
                                       new CleanupChannelTask(channelName));
    }

    /**
     *  A private task to check to see if a channel is empty;  if so,
     *  remove it.
     */
    private final class CleanupChannelTask implements Serializable, Task {
        /** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The name of the channel that we'll check. */ 
	private final String channelName;

	CleanupChannelTask(String  channelName) {
	    this.channelName = channelName;
	}

	/**
	 * Check to see if the channel is empty;  if so, close it and
         * remove its binding.
	 */
	public void run() {
            Channel channel = 
                    AppContext.getChannelManager().getChannel(channelName);
            
	    if (!channel.hasSessions()) {
                AppContext.getDataManager().removeObject(channel);
            }
	}
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
        session().send(toMessageBuffer(reply));
    }

    /**
     * Handle a private message.  First, decode it to find the target
     * ClientSession.  Then repackage the message to include the sender's
     * id, and send to the target.
     * 
     * @param message the private message
     */
    private void pmReceived(String message) {
        // Decode the message to get the target id
        String[] args = message.split(" ", 2);
        String targetId = args[0];
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Private message request from {0}, to {1}, contents: {2}",
                new Object[] { session(), targetId, message });
        }
        
        // Need to look up the session from the id
        ClientSession target = ChatApp.getSessionFromIdString(targetId);
        StringBuilder newMsg = new StringBuilder("/pm ");
        newMsg.append(session().getName());
        newMsg.append(' ');
        newMsg.append(message);
        target.send(toMessageBuffer(newMsg.toString()));
    }
    
    /**
     * Decodes the given {@code buffer} into a message string.
     *
     * @param buffer the encoded message
     * @return the decoded message string
     */
    static String fromMessageBuffer(ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Encodes the given message string into a {@link ByteBuffer}.
     *
     * @param s the message string to encode
     * @return the encoded message as a {@code ByteBuffer}
     */
    static ByteBuffer toMessageBuffer(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }
    
    /**
     * A listener for the channels.  If this is a chat message (identified
     * by not having a COMMAND_PREFIX), prepend the sender's user name.
     * While this could be done at the client (the client knows his own name),
     * using this listener demonstrates how to use a ChannelListener.
     */
    private static class ChatChannelListener 
            implements ChannelListener, Serializable 
    {
         /** The version of the serialized form of this class. */
        private static final long serialVersionUID = 1L;
        
        /** {@inheritDoc} */
        public boolean receivedMessage(Channel channel,
                                       ClientSession session,
                                       ByteBuffer msg)
        {
            String message = ChatClientSessionListener.fromMessageBuffer(msg);
            if (message.startsWith(COMMAND_PREFIX)) {      
                // Just send it along
                return true;
            }
            // This is a chat message sent on the channel - need to prepend
            // the sender.  The processing would typically be more complicated
            // here - perhaps checking the content of the message, or 
            // checking permissions of the user.
            ByteBuffer newMsg = ChatClientSessionListener.toMessageBuffer(
                    session.getName() + " " + message);
            channel.send(newMsg);
            return false;
        }
    }
}
