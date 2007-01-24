package com.sun.sgs.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private final ClientConnectionListener connListener =
        new SimpleClientConnectionListener();

    private final ConcurrentHashMap<String, SimpleClientChannel> channels =
        new ConcurrentHashMap<String, SimpleClientChannel>();

    /** The listener for this simple client. */
    private final SimpleClientListener clientListener;

    private volatile ClientConnection clientConnection = null;
    private volatile boolean connectionStateChanging = false;
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
                throw new IllegalStateException(
                    "Session already connected or connecting");
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
                throw new IllegalStateException("Client not connected");
            }
            connectionStateChanging = true;
        }
        if (force) {
            try {
                clientConnection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                ProtocolMessageEncoder m =
                    new ProtocolMessageEncoder(
                        ProtocolMessage.APPLICATION_SERVICE,
                        ProtocolMessage.LOGOUT_REQUEST);
                sendRaw(m.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    clientConnection.disconnect();
                } catch (IOException e2) {
                    e2.printStackTrace();
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
                ProtocolMessage.MESSAGE_SEND);
        m.writeBytes(message);
        sendRaw(m.getMessage());
    }

    private void sendRaw(byte[] data) throws IOException {
        clientConnection.sendMessage(data);
    }
    
    void checkConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Client not connected");
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
            System.out.println("SimpleClient: connected");
            synchronized (SimpleClient.this) {
                connectionStateChanging = false;
                clientConnection = connection;
            }

            PasswordAuthentication authentication =
                clientListener.getPasswordAuthentication(
                    "Enter Username and Password");

            ProtocolMessageEncoder m =
                new ProtocolMessageEncoder(
                    ProtocolMessage.APPLICATION_SERVICE,
                    ProtocolMessage.LOGIN_REQUEST);
            m.writeString(authentication.getUserName());
            m.writeString(new String(authentication.getPassword()));
            try {
                sendRaw(m.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
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
                System.err.println("Bad version, got: 0x"
                                   + Integer.toHexString(versionNumber)
                                   + " wanted: 0x" + ProtocolMessage.VERSION);
                return;
            }
            int service = decoder.readServiceNumber();
            int command = decoder.readCommand();
            /*
            System.out.println("SimpleClient messageReceived: "
                               + message.length + " command 0x"
                               + Integer.toHexString(command));
                               */
            switch (service) {

            // Handle "Application Service" messages
            case ProtocolMessage.APPLICATION_SERVICE:
                switch (command) {
                case ProtocolMessage.LOGIN_SUCCESS:
                    System.out.println("logging in");
                    sessionId = SessionId.fromBytes(decoder.readBytes());
                    reconnectKey = decoder.readBytes();
                    clientListener.loggedIn();
                    break;

                case ProtocolMessage.LOGIN_FAILURE:
                    clientListener.loginFailed(decoder.readString());
                    break;

                case ProtocolMessage.MESSAGE_SEND:
                    clientListener.receivedMessage(decoder.readBytes());
                    break;

                case ProtocolMessage.RECONNECT_SUCCESS:
                    clientListener.reconnected();
                    break;

                case ProtocolMessage.RECONNECT_FAILURE:
                    try {
                        clientConnection.disconnect();
                    } catch (IOException e) {
                        // TODO
                        e.printStackTrace();
                    }
                    break;

                case ProtocolMessage.LOGOUT_SUCCESS:
                    try {
                        clientConnection.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;

                default:
                    System.err.println("Unknown opcode: 0x"
                            + Integer.toHexString(command));
                    break;
                }
                break;

            // Handle Channel Service messages
            case ProtocolMessage.CHANNEL_SERVICE:
                switch (command) {

                case ProtocolMessage.CHANNEL_JOIN: {
                    String channelName = decoder.readString();
                    System.err.println("joining channel " + channelName);
                    SimpleClientChannel channel =
                        new SimpleClientChannel(channelName);
                    if (channels.putIfAbsent(channelName, channel) == null) {
                        channel.joined();
                    } else {
                        // TODO log that we were already a member
                    }
                    break;
                }

                case ProtocolMessage.CHANNEL_LEAVE: {
                    String channelName = decoder.readString();
                    SimpleClientChannel channel =
                        channels.remove(channelName);
                    if (channel != null) {
                        channel.left();
                    } else {
                        // TODO log that we were not a member
                    }
                    break;
                }

                case ProtocolMessage.CHANNEL_MESSAGE:
                    String channelName = decoder.readString();
                    SimpleClientChannel channel = channels.get(channelName);
                    if (channel == null) {
                        System.err.println("No channel found for '"
                                           + channelName + "'");
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
                    System.err.println("Unknown opcode: 0x"
                            + Integer.toHexString(command));
                    break;
                }
                break;

            default:
                System.err.println("Unknown service: 0x"
                        + Integer.toHexString(service));
                break;
            }
        }

        /**
         * {@inheritDoc}
         */
        public void reconnected(byte[] message) {
            throw new UnsupportedOperationException(
                "Not supported by SimpleClient");
        }

        /**
         * {@inheritDoc}
         */
        public void reconnecting(byte[] message) {
            throw new UnsupportedOperationException(
                "Not supported by SimpleClient");
        }

        /**
         * {@inheritDoc}
         */
        public ServerSessionListener sessionStarted(byte[] message) {
            throw new UnsupportedOperationException(
                "Not supported by SimpleClient");
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
                System.err.println("Already left channel " + name);
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
                System.err.println("Not a member of channel " + name);
                return;
            }
            listener.receivedMessage(this, sid, message);
        }

        void sendInternal(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            if (! joined) {
                System.err.println("Not a member of channel " + name);
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
