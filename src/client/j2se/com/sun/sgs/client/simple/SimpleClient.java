/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
import com.sun.sgs.impl.client.simple.SimpleSessionId;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;

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

    /** The server's session ID. */
    private static final CompactId SERVER_ID =
	new CompactId(new byte[] { (byte) 0});
    
    /**
     * The listener for the {@code ClientConnection} the session
     * is communicating on.
     */
    private final ClientConnectionListener connListener =
        new SimpleClientConnectionListener();

    /** The map of channels this client is a member of */
    private final ConcurrentHashMap<CompactId, SimpleClientChannel> channels =
        new ConcurrentHashMap<CompactId, SimpleClientChannel>();

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
    
    /** TODO */
    private volatile boolean expectingDisconnect = false;
    
    /** The current sessionId, if logged in. */
    private SessionId sessionId;

    /** The sequence number for ordered messages sent from this client. */
    private AtomicLong sequenceNumber = new AtomicLong(0);

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
        if (listener == null) {
            throw new NullPointerException(
                "The SimpleClientListener argument must not be null");
        }
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
                MessageBuffer msg = new MessageBuffer(3);
                msg.putByte(SimpleSgsProtocol.VERSION).
                    putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                    putByte(SimpleSgsProtocol.LOGOUT_REQUEST);
                sendRaw(msg.getBuffer());
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
        MessageBuffer msg =
            new MessageBuffer(3 + 8 + 2 + message.length);
        msg.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
            putByte(SimpleSgsProtocol.SESSION_MESSAGE).
            putLong(sequenceNumber.getAndIncrement()).
            putByteArray(message);
        sendRaw(msg.getBuffer());
    }

    private void sendRaw(byte[] data) throws IOException {
        clientConnection.sendMessage(data);
    }
    
    private void checkConnected() {
        if (!isConnected()) {
            RuntimeException re =
                new IllegalStateException("Client not connected");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
    }

    private void checkLoggedIn() {
        if (getSessionId() == null) {
            RuntimeException re =
                new IllegalStateException("Client not logged in");
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

            if (authentication == null) {
                logout(true);
                throw new NullPointerException(
                    "The returned PasswordAuthentication must not be null");
            }

            String user = authentication.getUserName();
            String pass = new String(authentication.getPassword());
            MessageBuffer msg =
                new MessageBuffer(3 +
                    MessageBuffer.getSize(user) +
                    MessageBuffer.getSize(pass));
            msg.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                putByte(SimpleSgsProtocol.LOGIN_REQUEST).
                putString(user).
                putString(pass);
            try {
                sendRaw(msg.getBuffer());
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
                if (clientConnection == null && (! connectionStateChanging)) {
                    // Someone else beat us here
                    return;
                }
                clientConnection = null;
                connectionStateChanging = false;
            }
            sessionId = null;
            String reason = null;
            if (message != null) {
                MessageBuffer msg = new MessageBuffer(message);
                reason = msg.getString();
            }
            
            for (SimpleClientChannel channel : channels.values()) {
                try {
                    channel.left();
                } catch (RuntimeException e) {
                    logger.logThrow(Level.FINE, e,
                        "During leftChannel ({0}) on disconnect:",
                        channel.getName());
                    // ignore the exception
                }
            }
            channels.clear();

            // FIXME ignore graceful from connection for now (not implemented),
            // instead look at the boolean we set when expecting disconnect
            clientListener.disconnected(expectingDisconnect, reason);
            expectingDisconnect = false;
        }

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(byte[] message) {
            try {
                MessageBuffer msg = new MessageBuffer(message);
                byte version = msg.getByte();
                if (version != SimpleSgsProtocol.VERSION) {
                    throw new IOException(
                        String.format("Bad version 0x%02X, wanted: 0x%02X",
                            version,
                            SimpleSgsProtocol.VERSION));
                }
                
                byte service = msg.getByte();
                
                if (logger.isLoggable(Level.FINER)) {
                    String logMessage = String.format(
                        "Message length:%d service:0x%02X",
                        message.length,
                        service);
                    logger.log(Level.FINER, logMessage);
                }
    
                switch (service) {

                // Handle "Application Service" messages
                case SimpleSgsProtocol.APPLICATION_SERVICE:
                    handleApplicationMessage(msg);
                    break;

                // Handle Channel Service messages
                case SimpleSgsProtocol.CHANNEL_SERVICE:
                    handleChannelMessage(msg);
                    break;

                default:
                    throw new IOException(
                        String.format("Unknown service 0x%02X", service));
                }
            } catch (IOException e) {
                logger.logThrow(Level.FINER, e, e.getMessage());
                if (isConnected()) {
                    try {
                        clientConnection.disconnect();
                    } catch (IOException e2) {
                        logger.logThrow(Level.FINEST, e2,
                            "Disconnect failed after {0}", e.getMessage());
                        // Ignore
                    }
                }
            }
        }

        private void handleApplicationMessage(MessageBuffer msg)
            throws IOException
        {
            byte command = msg.getByte();
            switch (command) {
            case SimpleSgsProtocol.LOGIN_SUCCESS:
                logger.log(Level.FINER, "Logged in");
		byte[] idBytes = CompactId.getCompactId(msg).getId();
                sessionId = SessionId.fromBytes(idBytes);
		idBytes = CompactId.getCompactId(msg).getId();
                reconnectKey = idBytes;
                clientListener.loggedIn();
                break;

            case SimpleSgsProtocol.LOGIN_FAILURE:
                logger.log(Level.FINER, "Login failed");
                clientListener.loginFailed(msg.getString());
                break;

            case SimpleSgsProtocol.SESSION_MESSAGE:
                logger.log(Level.FINEST, "Direct receive");
                checkLoggedIn();
                msg.getLong(); // FIXME sequence number
                clientListener.receivedMessage(msg.getByteArray());
                break;

            case SimpleSgsProtocol.RECONNECT_SUCCESS:
                logger.log(Level.FINER, "Reconnected");
                clientListener.reconnected();
                break;

            case SimpleSgsProtocol.RECONNECT_FAILURE:
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

            case SimpleSgsProtocol.LOGOUT_SUCCESS:
                logger.log(Level.FINER, "Logged out gracefully");
                expectingDisconnect = true;
                // Server should disconnect us
                /*
                try {
                    clientConnection.disconnect();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.logThrow(Level.FINE, e,
                            "Disconnecting after graceful logout");
                    }
                    // ignore
                }
                */
                break;

            default:
                throw new IOException(
                    String.format("Unknown session opcode: 0x%02X", command));
            }
        }
        
        private void handleChannelMessage(MessageBuffer msg)
            throws IOException
        {
            byte command = msg.getByte();
            switch (command) {

            case SimpleSgsProtocol.CHANNEL_JOIN: {
                logger.log(Level.FINER, "Channel join");
                checkLoggedIn();
                String channelName = msg.getString();
		CompactId channelId = CompactId.getCompactId(msg);
                SimpleClientChannel channel =
                    new SimpleClientChannel(channelName, channelId);
                if (channels.putIfAbsent(channelId, channel) == null) {
                    channel.joined();
                } else {
                    logger.log(Level.WARNING,
                        "Cannot join channel {0}: already a member",
                        channelName);
                }
                break;
            }

            case SimpleSgsProtocol.CHANNEL_LEAVE: {
                logger.log(Level.FINER, "Channel leave");
                checkLoggedIn();
                CompactId channelId = CompactId.getCompactId(msg);
                SimpleClientChannel channel =
                    channels.remove(channelId);
                if (channel != null) {
                    channel.left();
                } else {
                    logger.log(Level.WARNING,
                        "Cannot leave channel {0}: not a member",
                        channelId);
                }
                break;
            }

            case SimpleSgsProtocol.CHANNEL_MESSAGE:
                logger.log(Level.FINEST, "Channel recv");
                checkLoggedIn();
                CompactId channelId = CompactId.getCompactId(msg);
                SimpleClientChannel channel = channels.get(channelId);
                if (channel == null) {
                    logger.log(Level.FINE,
                        "Ignore message on channel {0}: not a member",
                        channelId);
                    return;
                }

                msg.getLong(); // FIXME sequence number
                
                CompactId compactSessionId = CompactId.getCompactId(msg);
                SessionId sid =
		    compactSessionId.equals(SERVER_ID) ?
		    null :
		    SessionId.fromBytes(compactSessionId.getId());
                
                channel.receivedMessage(sid, msg.getByteArray());
                break;

            default:
                throw new IOException(
                    String.format("Unknown channel opcode: 0x%02X", command));
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

        private final String channelName;
	private final CompactId channelId;
        
        /**
         * The listener for this channel if the client is a member,
         * or null if the client is no longer a member of this channel.
         */
        private volatile ClientChannelListener listener = null;

        SimpleClientChannel(String name, CompactId id) {
            this.channelName = name;
	    this.channelId = id;
        }

        // Implement ClientChannel

        /**
         * {@inheritDoc}
         */
        public String getName() {
            return channelName;
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
            assert (listener == null);

            listener = clientListener.joinedChannel(this);

            if (listener == null) {
                throw new NullPointerException(
                    "The returned ClientChannelListener must not be null");
            }
        }

        void left() {
            assert (listener != null);

            listener.leftChannel(this);
            listener = null;
       }
        
        void receivedMessage(SessionId sid, byte[] message) {
            assert (listener != null);

            listener.receivedMessage(this, sid, message);
        }

        void sendInternal(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            if (listener == null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                        "Cannot send on channel {0}: not a member",
                        channelName);
                }
                return;
            }
            int totalSessionLength = 0;
            if (recipients != null) {
                for (SessionId recipientId : recipients) {
                    totalSessionLength +=
			((SimpleSessionId) recipientId).getCompactId().
			    getExternalFormByteCount();
                }
            }
            
            MessageBuffer msg =
                new MessageBuffer(3 +
		    channelId.getExternalFormByteCount() +
                    8 +
                    2 + totalSessionLength +
                    2 + message.length);
            msg.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                putByte(SimpleSgsProtocol.CHANNEL_SEND_REQUEST).
                putBytes(channelId.getExternalForm()).
                putLong(sequenceNumber.getAndIncrement());
            if (recipients == null) {
                msg.putShort(0);
            } else {
                msg.putShort(recipients.size());
                for (SessionId recipientId : recipients) {
                    msg.putBytes(((SimpleSessionId) recipientId).getCompactId().
				 getExternalForm());
                }
            }
            msg.putByteArray(message);
            sendRaw(msg.getBuffer());
        }
    }
}
