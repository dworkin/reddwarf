package com.sun.sgs.example.chat.app;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
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
    private final ClientSession session;

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
        this.session = session;
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
                new Object[] { session, grace });
        }
        removeFromChannel(GLOBAL_CHANNEL_NAME);
    }

    /** {@inheritDoc} */
    public void receivedMessage(byte[] message) {
        try {
            String messageString = fromMessageBytes(message);

            // Check that the command begins with a foward-slash
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

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    private static String toHexString(byte[] bytes) {
        StringBuilder buf = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            buf.append(String.format("%02X", b));
        }
        return buf.toString();
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
        changeMsg.append(toHexString(session.getSessionId()));
        if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
            changeMsg.append(':');
            changeMsg.append(session.getName());
        }
        channel.send(toMessageBytes(changeMsg.toString()));

        // Now add the joiner and tell it about all the members on
        // the channel, the joiner included.
        channel.join(session, null);

        // Send the membership list to the joining session.
        StringBuilder listMessage = new StringBuilder("/members");
        for (ClientSession member : channel.getSessions()) {
            listMessage.append(' ');
            listMessage.append(toHexString(member.getSessionId()));
            if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
                listMessage.append(':');
                listMessage.append(member.getName());
            }
        }
        channel.send(session, toMessageBytes(listMessage.toString()));
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
                    new Object[] { session, channelName });
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
                        new Object[] { session, channelName });
            }
            return;
        }

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
            toHexString(session.getSessionId());
        channel.send(toMessageBytes(changeMessage));
    }

    /**
     * Echos the given string back to the sending session on the global
     * chat channel.
     *
     * @param message the message to echo
     */
    private void echo(String message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Echo request from {0}, contents: {1}",
                new Object[] { session, message });
        }

        String reply = "/pong " + message;
        session.send(toMessageBytes(reply));
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
            throw new Error("Required charset UTF-8 not found", e);
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
            throw new Error("Required charset UTF-8 not found", e);
        }
    }
}