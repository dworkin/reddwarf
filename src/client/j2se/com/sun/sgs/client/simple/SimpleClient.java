package com.sun.sgs.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.simple.ProtocolMessage;
import com.sun.sgs.impl.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.impl.client.simple.ProtocolMessageEncoder;
import com.sun.sgs.impl.util.LoggerWrapper;

/**
 * An implementation of {@link ServerSession} that clients can use to manage
 * logging in and communicating with the server. A {@code SimpleClient}
 * is used to establish (or re-establish) a login session with the server,
 * send messages to the server, and log out.
 * <p>
 * A {@code SimpleClient} is constructed with a {@link
 * SimpleClientListener} which receives connection-related events, receives
 * messages from the server, and also receives notification of each channel
 * the client is joined to.
 * <p>
 * If the server session associated with a simple client becomes
 * disconnected, then its {@link #send send} and {@link #getSessionId
 * getSessionId} methods will throw {@code IllegalStateException}.
 * Additionally, when a client is disconnected, the server removes that
 * client from the channels that it had been joined to. A disconnected
 * client can use the {@link #login login} method to log in again.
 * <p>
 * Note that the session identifier of a client changes with each login
 * session; so if a server session is disconnected and then logs in again,
 * the {@link #getSessionId getSessionId} method will return a new
 * {@code SessionId}.
 */
public class SimpleClient implements ServerSession {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SimpleClient.class.getName()));

    /**
     * The listener for the {@code ClientConnection} the session
     * is communicating on.
     */
    private final ClientConnectionListener connListener =
        new SimpleClientConnectionListener();

    /** The map of channels this client is a member of */
    private final ConcurrentHashMap<String, SimpleClientChannel> channels =
        new ConcurrentHashMap<String, SimpleClientChannel>();

    /** The listener for this simple client. */
    private final SimpleClientListener clientListener;

    /**
     * The current {@code ClientConnection}, if connected, or
     * {@code} null if disconnected.
     */
    private volatile ClientConnection clientConnection = null;

    /**
     * Indicates that either a connection or disconnection attempt
     * is in progress.
     */
    private volatile boolean connectionStateChanging = false;
    
    /** The current sessionId, if logged in. */
    private SessionId sessionId;
    
    /** Reconnection key.  TODO reconnect not implemented */
    @SuppressWarnings("unused")
    private byte[] reconnectKey;

    /**
     * Creates an instance of this class with the specified listener. Once
     * this client is logged in (by using the {@link #login login} method),
     * the specified listener receives connection-related events, receives
     * messages from the server, and also receives notification of each
     * channel the client is joined to. If this client becomes disconnected
     * for any reason, it may use the {@code login} method to log in
     * again.
     * 
     * @param listener a listener that will receive events for this client
     */
    public SimpleClient(SimpleClientListener listener) {
        this.clientListener = listener;
    }

    /**
     * Initiates a login session with the server. A session is established
     * asynchronously with the server as follows:
     * <p>
     * First, this client's {@link PasswordAuthentication login credential}
     * is obtained by invoking its {@link SimpleClientListener listener}'s
     * {@link SimpleClientListener#getPasswordAuthentication
     * getPasswordAuthentication} method with a login prompt.
     * <p>
     * Next, if a connection with the server is successfuly established and
     * the client's login credential (as obtained above) is verified, then
     * the client listener's {@link SimpleClientListener#loggedIn loggedIn}
     * method is invoked. If, however, the login fails due to a connection
     * failure with the server, a login authentication failure, or some
     * other failure, the client listener's
     * {@link SimpleClientListener#loginFailed loginFailed} method is
     * invoked with a {@code String} indicating the reason for the
     * failure.
     * <p>
     * If this client is disconnected for any reason (including login
     * failure), this method may be used again to log in.
     * <p>
     * The supported connection properties are:
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td>{@code host}</td>
     *     <td>SGS host address <b>(required)</b></td></tr>
     * <tr><td>{@code port}</td>
     *     <td>SGS port <b>(required)</b></td></tr>
     * </table>
     *
     * @param props the connection properties to use in creating the
     *        client's session
     *
     * @throws IOException if a synchronous IO error occurs
     * @throws IllegalStateException if this session is already connected
     *         or connecting
     * @throws SecurityException if the caller does not have permission
     *         to connect to the remote endpoint
     */
    public void login(Properties props) throws IOException {
        synchronized (this) {
            if (connectionStateChanging || clientConnection != null) {
                RuntimeException re =
                    new IllegalStateException(
                        "Session already connected or connecting");
                logger.logThrow(Level.FINE, re, re.getMessage());
                throw re;
            }
            connectionStateChanging = true;
        }
        ClientConnector connector = ClientConnector.create(props);
        connector.connect(connListener);
    }

    /**
     * {@inheritDoc}
     */
    public SessionId getSessionId() {
        checkConnected();
        return sessionId;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConnected() {
        return (clientConnection != null);
    }

    /**
     * {@inheritDoc}
     */
    public void logout(boolean force) {
        synchronized (this) {
            if (connectionStateChanging || clientConnection == null) {
                RuntimeException re =
                    new IllegalStateException("Client not connected");
                logger.logThrow(Level.FINE, re, re.getMessage());
                throw re;
            }
            connectionStateChanging = true;
        }
        if (force) {
            try {
                clientConnection.disconnect();
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During forced logout:");
                // ignore
            }
        } else {
            try {
                ProtocolMessageEncoder m =
                    new ProtocolMessageEncoder(
                        ProtocolMessage.APPLICATION_SERVICE,
                        ProtocolMessage.LOGOUT_REQUEST);
                sendRaw(m.getMessage());
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During graceful logout:");
                try {
                    clientConnection.disconnect();
                } catch (IOException e2) {
                    logger.logThrow(Level.FINE, e2, "During forced logout:");
                    // ignore
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void send(byte[] message) throws IOException {
        checkConnected();
        ProtocolMessageEncoder m =
            new ProtocolMessageEncoder(ProtocolMessage.APPLICATION_SERVICE,
                ProtocolMessage.SESSION_MESSAGE);
        m.writeBytes(message);
        sendRaw(m.getMessage());
    }

    private void sendRaw(byte[] data) throws IOException {
        clientConnection.sendMessage(data);
    }
    
    void checkConnected() {
        if (!isConnected()) {
            RuntimeException re =
                new IllegalStateException("Client not connected");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
    }

    /**
     * Receives callbacks on the associated {@code ClientConnection}.
     */
    final class SimpleClientConnectionListener
        implements ClientConnectionListener
    {
        // Implement ClientConnectionListener

        /**
         * {@inheritDoc}
         */
        public void connected(ClientConnection connection)
        {
            logger.log(Level.FINER, "Connected");
            synchronized (SimpleClient.this) {
                connectionStateChanging = false;
                clientConnection = connection;
            }

            PasswordAuthentication authentication =
                clientListener.getPasswordAuthentication();

            ProtocolMessageEncoder m =
                new ProtocolMessageEncoder(
                    ProtocolMessage.APPLICATION_SERVICE,
                    ProtocolMessage.LOGIN_REQUEST);
            m.writeString(authentication.getUserName());
            m.writeString(new String(authentication.getPassword()));
            try {
                sendRaw(m.getMessage());
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During login request:");
                logout(true);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void disconnected(boolean graceful, byte[] message) {
            synchronized (SimpleClient.this) {
                clientConnection = null;
                connectionStateChanging = false;
            }
            sessionId = null;
            clientListener.disconnected(graceful);
        }

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(byte[] message) {
            ProtocolMessageDecoder decoder =
                new ProtocolMessageDecoder(message);
            int versionNumber = decoder.readVersionNumber();
            if (versionNumber != ProtocolMessage.VERSION) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Bad version 0x{0}, wanted: 0x{1}",
                        Integer.toHexString(versionNumber),
                        Integer.toHexString(ProtocolMessage.VERSION));
                }
                return;
            }
            int service = decoder.readServiceNumber();
            int command = decoder.readCommand();
            
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                    "Received {0,number,#} byte message, command 0x{1}",
                    message.length,
                    Integer.toHexString(command));
            }

            switch (service) {

            // Handle "Application Service" messages
            case ProtocolMessage.APPLICATION_SERVICE:
                switch (command) {
                case ProtocolMessage.LOGIN_SUCCESS:
                    logger.log(Level.FINER, "Logged in");
                    sessionId = SessionId.fromBytes(decoder.readBytes());
                    reconnectKey = decoder.readBytes();
                    clientListener.loggedIn();
                    break;

                case ProtocolMessage.LOGIN_FAILURE:
                    logger.log(Level.FINER, "Login failed");
                    clientListener.loginFailed(decoder.readString());
                    break;

                case ProtocolMessage.SESSION_MESSAGE:
                    logger.log(Level.FINEST, "Direct receive");
                    clientListener.receivedMessage(decoder.readBytes());
                    break;

                case ProtocolMessage.RECONNECT_SUCCESS:
                    logger.log(Level.FINER, "Reconnected");
                    clientListener.reconnected();
                    break;

                case ProtocolMessage.RECONNECT_FAILURE:
                    try {
                        logger.log(Level.FINER, "Reconnect failed");
                        clientConnection.disconnect();
                    } catch (IOException e) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.logThrow(Level.FINE, e,
                                "Disconnecting a failed reconnect");
                        }
                        // ignore
                    }
                    break;

                case ProtocolMessage.LOGOUT_SUCCESS:
                    logger.log(Level.FINER, "Logged out gracefully");
                    try {
                        clientConnection.disconnect();
                    } catch (IOException e) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.logThrow(Level.FINE, e,
                                "Disconnecting after graceful logout");
                        }
                        // ignore
                    }
                    break;

                default:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                            "Unknown opcode: 0x{0}",
                            Integer.toHexString(command));
                    }
                    // TODO disconnect?
                    break;
                }
                break;

            // Handle Channel Service messages
            case ProtocolMessage.CHANNEL_SERVICE:
                switch (command) {

                case ProtocolMessage.CHANNEL_JOIN: {
                    logger.log(Level.FINER, "Channel join");
                    String channelName = decoder.readString();
                    SimpleClientChannel channel =
                        new SimpleClientChannel(channelName);
                    if (channels.putIfAbsent(channelName, channel) == null) {
                        channel.joined();
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE,
                                "Cannot leave channel {0}: already a member",
                                channelName);
                        }
                    }
                    break;
                }

                case ProtocolMessage.CHANNEL_LEAVE: {
                    logger.log(Level.FINER, "Channel leave");
                    String channelName = decoder.readString();
                    SimpleClientChannel channel =
                        channels.remove(channelName);
                    if (channel != null) {
                        channel.left();
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE,
                                "Cannot leave channel {0}: not a member",
                                channelName);
                        }
                    }
                    break;
                }

                case ProtocolMessage.CHANNEL_MESSAGE:
                    logger.log(Level.FINEST, "Channel recv");
                    String channelName = decoder.readString();
                    SimpleClientChannel channel = channels.get(channelName);
                    if (channel == null) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE,
                                "Ignore message on channel {0}: not a member",
                                channelName);
                        }
                        return;
                    }
                    // TODO: discard sequence number for now, we're always
                    // on a reliable transport.
                    /* long seq = */ decoder.readLong();
                    
                    byte[] sidBytes = decoder.readBytes();
                    SessionId sid = (sidBytes == null) ?
                            null : SessionId.fromBytes(sidBytes);
                    
                    channel.receivedMessage(sid, decoder.readBytes());
                    break;

                default:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                            "Unknown opcode: 0x{0}",
                            Integer.toHexString(command));
                    }
                    // TODO disconnect?
                    break;
                }
                break;

            default:
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Unknown service: 0x{0}",
                        Integer.toHexString(service));
                }
                // TODO disconnect?
                break;
            }
        }

        /**
         * {@inheritDoc}
         */
        public void reconnected(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }

        /**
         * {@inheritDoc}
         */
        public void reconnecting(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }

        /**
         * {@inheritDoc}
         */
        public ServerSessionListener sessionStarted(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
    }

    /**
     * Simple ClientChannel implementation
     */
    final class SimpleClientChannel implements ClientChannel {

        private final String name;
        private volatile boolean joined;
        private ClientChannelListener listener;

        SimpleClientChannel(String name) {
            this.name = name;
        }

        // Implement ClientChannel

        /**
         * {@inheritDoc}
         */
        public String getName() {
            return name;
        }

        /**
         * {@inheritDoc}
         */
        public void send(byte[] message) throws IOException {
            sendInternal(null, message);
        }

        /**
         * {@inheritDoc}
         */
        public void send(SessionId recipient, byte[] message)
            throws IOException
        {
            sendInternal(Collections.singleton(recipient), message);
        }

        /**
         * {@inheritDoc}
         */
        public void send(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            sendInternal(recipients, message);
        }

        // Implementation details

        void joined() {
            joined = true;            
            listener = clientListener.joinedChannel(this);
        }

        void left() {
            if (! joined) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Cannot leave channel {0}: not a member",
                        name);
                }
                return;
            }
            joined = false;
            if (listener != null) {
                listener.leftChannel(this);
                listener = null;
            }
       }
        
        void receivedMessage(SessionId sid, byte[] message) {
            if (! joined) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Ignore message on channel {0}: not a member",
                        name);
                }
                return;
            }
            listener.receivedMessage(this, sid, message);
        }

        void sendInternal(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            if (! joined) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Cannot send on channel {0}: not a member",
                        name);
                }
                return;
            }
            ProtocolMessageEncoder m =
                new ProtocolMessageEncoder(ProtocolMessage.CHANNEL_SERVICE,
                    ProtocolMessage.CHANNEL_SEND_REQUEST);
            m.writeString(name);
            if (recipients == null) {
                m.writeShort(Short.valueOf((short) 0));
            } else {
                m.writeShort(Short.valueOf((short) recipients.size()));
                for (SessionId id : recipients) {
                    m.writeSessionId(id);
                }
            }
            m.writeBytes(message);
            sendRaw(m.getMessage());
        }
    }
}
