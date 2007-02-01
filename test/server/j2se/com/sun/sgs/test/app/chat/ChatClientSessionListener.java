package com.sun.sgs.test.app.chat;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Listener for events from a particular {@link ClientSession} logged into a
 * {@code ChatApp} application.
 * <p>
 * Note that this {@link ClientSessionListener} has no mutable state, so it
 * doesn't need to implement {@link com.sun.sgs.app.ManagedObject}.
 */
class ChatClientSessionListener
    implements Serializable, ClientSessionListener
{
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(ChatClientSessionListener.class.getName());

    /** The name of the global channel. */
    public static final String GLOBAL_CHANNEL_NAME = "Global";

    /** The {@link Charset} encoding for client/server messages. */
    private static final Charset CHARSET_UTF8 = Charset.forName("UTF8");

    /** The prefix commands must start with, "{@value #COMMAND_PREFIX}" */
    private static final String COMMAND_PREFIX = "/";

    /** The {@link ClientSession} this listener receives events for. */
    private final ClientSession session;

    /**
     * Creates a new listener for the given {@code ClientSession}.
     * Immediately joins the session to the global notification channel,
     * and sends membership change notifications as appropriate.
     *
     * @param app the {@code ChatApp} for this session
     * @param session this listener's {@code ClientSession}
     *
     * @see #addToChannel
     */
    ChatClientSessionListener(ClientSession session) {
        this.session = session;
        addToChannel(GLOBAL_CHANNEL_NAME);
    }

    /** {@inheritDoc} */
    public void disconnected(boolean graceful) {
        if (logger.isLoggable(Level.FINE)) {
            String grace = graceful ? "graceful" : "forced";
            logger.log(Level.FINE,
                "{0} {1} disconnect",
                new Object[] { session, grace });
        }
        removeFromChannel(GLOBAL_CHANNEL_NAME);
    }

    /** {@inheritDoc} */
    public void receivedMessage(byte[] message) {
        try {
            String messageString = new String(message, CHARSET_UTF8);
            
            // Check for initial slash
            if (! messageString.startsWith(COMMAND_PREFIX)) {
                throw new IllegalArgumentException(
                    "Command must start with " + COMMAND_PREFIX);
            }

            // Split at the first space
            String[] args = messageString.split(" +", 2);
            
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

            case DISCONNECT:
                logger.log(Level.INFO, "Disconnect request from {0}", session);
                session.disconnect();
                break;

            case SHUTDOWN:
                logger.log(Level.CONFIG, "Shutdown request from {0}", session);
                System.exit(0);
                break;
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.INFO)) {
                LogRecord rec = new LogRecord(Level.INFO,
                    "While processing command from {0}, disconnecting:");
                rec.setThrown(e);
                rec.setParameters(new Object[] { session });
                logger.log(rec);
            }
            
            session.disconnect();
        }
    }

    /** TODO docs */
    static String formatHexBytes(byte[] bytes) {
        StringBuilder s = new StringBuilder(2 + (2 * bytes.length));
        s.append('[');
        for (byte b : bytes) {
            s.append(String.format("%02X", b));
        }
        s.append(']');
        return s.toString();
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
                    "Join " + session + " to " + channelName);
        }

        ChannelManager channelMgr = AppContext.getChannelManager();
        Channel channel;
        try {
            channel = channelMgr.getChannel(channelName);
        } catch (NameNotBoundException e) {
            channel = channelMgr.createChannel(
                    channelName, null, Delivery.RELIABLE);
        }

        // Send the membership change first, so the new session doesn't
        // receive its own join message.
        StringBuilder changeMsg = new StringBuilder("/joined ");
        changeMsg.append(formatHexBytes(session.getSessionId()));
        if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
            changeMsg.append(':');
            changeMsg.append(session.getName());
        }
        channel.send(changeMsg.toString().getBytes(CHARSET_UTF8));

        // Now add the joiner and tell it about all the members on
        // the channel, the joiner included.
        channel.join(session, null);

        // Send the membership list to the joining session.
        StringBuilder listMessage = new StringBuilder("/members");
        for (ClientSession member : channel.getSessions()) {
            listMessage.append(' ');
            listMessage.append(formatHexBytes(member.getSessionId()));
            if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
                listMessage.append(':');
                listMessage.append(member.getName());
            }
        }
        channel.send(session, listMessage.toString().getBytes(CHARSET_UTF8));
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
                "Leave " + session + " from " + channelName);
        }

        ChannelManager channelMgr = AppContext.getChannelManager();
        Channel channel = channelMgr.getChannel(channelName);

        if (session.isConnected()) {
            // Remove the member first, so it doesn't get the membership
            // change message for its own departure.
            channel.leave(session);
        }

        // If the channel has no more sessions, close it.
        if (! channel.hasSessions()) {
            channel.close();
            return;
        }

        // Tell the rest of the channel about the removal.
        String changeMessage = "/left " +
            formatHexBytes(session.getSessionId());
        channel.send(changeMessage.getBytes(CHARSET_UTF8));
    }

    /**
     * Echos the given string back to the sending session on the global
     * chat channel.
     *
     * @param contents the contents to echo
     */
    private void echo(String contents) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Echo request from {0}, contents: {1}",
                new Object[] { session, contents });
        }

        String reply = "/pong " + contents;
        session.send(reply.getBytes(CHARSET_UTF8));
    }
}