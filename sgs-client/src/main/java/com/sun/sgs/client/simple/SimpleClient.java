/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
import java.math.BigInteger;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
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
 * SimpleClientListener} which receives connection-related events as well
 * as messages from the server application.
 * <p>
 * If the server session associated with a simple client becomes
 * disconnected, then its {@link #send send} method will throw
 * {@code IllegalStateException}.  A disconnected
 * client can use the {@link #login login} method to log in again.
 */
public class SimpleClient implements ServerSession {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SimpleClient.class.getName()));
    
    /**
     * The listener for the {@code ClientConnection} the session
     * is communicating on.  If our login attempt is redirected to
     * another host, we will use a different listener for the connection
     * to the new host.
     */
    private ClientConnectionListener connListener;
    
    /** The listener for this simple client. */
    private final SimpleClientListener clientListener;

    /**
     * The current {@code ClientConnection}, if connected, or
     * {@code} null if disconnected.
     */
    private volatile ClientConnection clientConnection = null;

    /** The password authentication used for the initial client
     *  login attempt.  If the client receives a LOGIN_REDIRECT
     *  message, we don't want the user (typing at a keyboard)
     *  to have to supply their login information again.
     */
    PasswordAuthentication authentication = null;
    
    /**
     * Indicates that either a connection or disconnection attempt
     * is in progress.
     */
    private volatile boolean connectionStateChanging = false;

    /** Indicates whether this client is logged in. */
    private volatile boolean loggedIn = false;

    /** Reconnection key.  TODO reconnect not implemented */
    @SuppressWarnings("unused")
    private byte[] reconnectKey;

    /** The map of channels this client is a member of, keyed by channel ID */
    private final ConcurrentHashMap<BigInteger, SimpleClientChannel> channels =
        new ConcurrentHashMap<BigInteger, SimpleClientChannel>();

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
        connListener = new SimpleClientConnectionListener();
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
     * 
     * <p>Next, if a connection with the server is successfully established
     * and the client's login credential (as obtained above) is verified,
     * then the client listener's {@link SimpleClientListener#loggedIn
     * loggedIn} method is invoked. If, however, the login fails due to a
     * connection failure with the server or a malformed login message to
     * the server, the client listener's {@link
     * SimpleClientListener#disconnected disconnected} method is invoked
     * with a {@code String} indicating the reason for the failure.  If the
     * login fails due to a login authentication failure or some other
     * failure on the server while processing the login request, the client
     * listener's {@link SimpleClientListener#loginFailed loginFailed}
     * method is invoked with a {@code String} indicating the reason for
     * the failure.
     * 
     * <p>If this client is disconnected for any reason (including login
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

    /* -- Implement ServerSession -- */
    
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
                loggedIn = false;
                clientConnection.disconnect();
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During forced logout:");
                // ignore
            }
        } else {
            try {
                ByteBuffer msg = 
                    ByteBuffer.wrap(
                        new byte[] { SimpleSgsProtocol.LOGOUT_REQUEST });
                sendRaw(msg.asReadOnlyBuffer());
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During graceful logout:");
                try {
                    loggedIn = false;
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
    public void send(ByteBuffer message) throws IOException {
        checkConnected();
        ByteBuffer msg = ByteBuffer.allocate(1 + message.remaining());
        msg.put(SimpleSgsProtocol.SESSION_MESSAGE)
           .put(message)
           .flip();
        sendRaw(msg);
    }

    /**
     * Sends raw data to the underlying connection.
     * 
     * @param buf the data to send
     * @throws IOException if an IO problem occurs
     */
    private void sendRaw(ByteBuffer buf) throws IOException {
        clientConnection.sendMessage(buf);
    }

    /**
     * Throws an exception if this client is not connected.
     * 
     * @throws IllegalStateException if this client is not connected
     */
    private void checkConnected() {
        if (!isConnected()) {
            RuntimeException re =
                new IllegalStateException("Client not connected");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
    }

    /**
     * Throws an exception if this client is not logged in.
     * 
     * @throws IllegalStateException if this client is not logged in
     */
    private void checkLoggedIn() {
        if (! loggedIn) {
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
            
        /** Indicates whether this listener expects a disconnect message. */
        private volatile boolean expectingDisconnect = false;

	/** Indicates whether the disconnected callback should not be
	 * invoked. */
	private volatile boolean suppressDisconnectedCallback = false;

        /** Indicates whether this listener has been disabled because
         *  of an automatic login redirect to another host and port.
         *  We need to disconnect our previous connection, but we don't
         *  want to tell the client listener.  We'll accept no messages
         *  when we're in this state.
         */
        private volatile boolean redirect = false;
        
        /* -- Implement ClientConnectionListener -- */
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

            // First time through, we haven't authenticated yet.
            // We don't want to have to reauthenticate for each login
            // redirect.
            if (authentication == null) {
                authentication = clientListener.getPasswordAuthentication();
            }

            if (authentication == null) {
                logout(true);
                throw new NullPointerException(
                    "The returned PasswordAuthentication must not be null");
            }

            String user = authentication.getUserName();
            String pass = new String(authentication.getPassword());
            MessageBuffer msg =
                new MessageBuffer(2 +
                    MessageBuffer.getSize(user) +
                    MessageBuffer.getSize(pass));
            msg.putByte(SimpleSgsProtocol.LOGIN_REQUEST).
                putByte(SimpleSgsProtocol.VERSION).
                putString(user).
                putString(pass);
            try {
                sendRaw(ByteBuffer.wrap(msg.getBuffer()).asReadOnlyBuffer());
            } catch (IOException e) {
                logger.logThrow(Level.FINE, e, "During login request:");
                logout(true);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void disconnected(boolean graceful, byte[] message) {
            if (redirect) {
                // This listener has been redirected, and this callback 
                // should be ignored.  In particular, we don't want to
                // change the clientConnection state (this could be a 
                // real problem if the disconnected callback was delayed
                // to after the connected callback from the automatic
                // login redirect), and we don't want to notify the
                // client listener of the redirect.
                return;
            }
            synchronized (SimpleClient.this) {
                if (clientConnection == null && (! connectionStateChanging)) {
                    // Someone else beat us here
                    return;
                }
                clientConnection = null;
                connectionStateChanging = false;
            }
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

            // TBI implement graceful disconnect.
            // For now, look at the boolean we set when expecting
            // disconnect
	    if (! suppressDisconnectedCallback) {
		clientListener.disconnected(expectingDisconnect, reason);
	    }
	    suppressDisconnectedCallback = false;
            expectingDisconnect = false;
        }

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(byte[] message) {
            try {
                MessageBuffer msg = new MessageBuffer(message);
                
                if (logger.isLoggable(Level.FINER)) {
                    String logMessage = String.format(
                        "Message length:%d", message.length);
                    logger.log(Level.FINER, logMessage);
                }

                handleApplicationMessage(msg);

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
        
        /**
         * Processes an application message.
         * 
         * @param msg the message to process
         * @throws IOException if an IO problem occurs
         */
        private void handleApplicationMessage(MessageBuffer msg)
            throws IOException
        {
            byte command = msg.getByte();
            switch (command) {
            case SimpleSgsProtocol.LOGIN_SUCCESS:
                logger.log(Level.FINER, "Logged in");
                reconnectKey = msg.getBytes(msg.limit() - msg.position());
                loggedIn = true;
                clientListener.loggedIn();
                break;

            case SimpleSgsProtocol.LOGIN_FAILURE: {
                String reason = msg.getString();
                logger.log(Level.FINER, "Login failed: {0}", reason);
		suppressDisconnectedCallback = true;
                try {
                    clientConnection.disconnect();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.logThrow(Level.FINE, e,
                            "Disconnecting after login failure throws");
                    }
                    // ignore
                }
                clientListener.loginFailed(reason);
                break;
            }

            case SimpleSgsProtocol.LOGIN_REDIRECT: {
                String host = msg.getString();
                int port = msg.getInt();
                logger.log(Level.FINER, "Login redirect: {0}:{1}", host, port);
                
                // Disconnect our current connection, and connect to the
                // new host and port
                ClientConnection oldConnection = clientConnection;
                synchronized (SimpleClient.this) {
                    clientConnection = null;
                    connectionStateChanging = true;
                }
		try {
		    oldConnection.disconnect();
		}  catch (IOException e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.logThrow(Level.FINE, e,
                            "Disconnecting after login redirect throws");
                    }
                    // ignore
                }
                redirect = true;
                Properties props = new Properties();
                props.setProperty("host", host);
                props.setProperty("port", String.valueOf(port));
                ClientConnector connector = ClientConnector.create(props);
                // We use a new listener so we don't have to worry about
                // "redirect" being incorrect.
                connListener = new SimpleClientConnectionListener();
                // This eventually causes connected to be called
                connector.connect(connListener);
                break;
            }

            case SimpleSgsProtocol.SESSION_MESSAGE: {
                logger.log(Level.FINEST, "Direct receive");
                checkLoggedIn();
                byte[] msgBytes = msg.getBytes(msg.limit() - msg.position());
                ByteBuffer buf = ByteBuffer.wrap(msgBytes);
                clientListener.receivedMessage(buf.asReadOnlyBuffer());
                break;
            }

            case SimpleSgsProtocol.RECONNECT_SUCCESS:
                logger.log(Level.FINER, "Reconnected");
                loggedIn = true;
                reconnectKey = msg.getBytes(msg.limit() - msg.position());
                clientListener.reconnected();
                break;

            case SimpleSgsProtocol.RECONNECT_FAILURE:
                try {
                    String reason = msg.getString();
                    logger.log(Level.FINER, "Reconnect failed: {0}", reason);
                    clientConnection.disconnect();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.logThrow(Level.FINE, e,
                            "Disconnecting a failed reconnect throws");
                    }
                    // ignore
                }
                break;

            case SimpleSgsProtocol.LOGOUT_SUCCESS:
                logger.log(Level.FINER, "Logged out gracefully");
                expectingDisconnect = true;
                loggedIn = false;
                try {
                    clientConnection.disconnect();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.logThrow(Level.FINE, e,
                            "Disconnecting after graceful logout throws");
                    } 
                    // ignore
                }
                break;

            case SimpleSgsProtocol.CHANNEL_JOIN: {
                logger.log(Level.FINER, "Channel join");
                checkLoggedIn();
                String channelName = msg.getString();
		byte[] channelIdBytes =
		    msg.getBytes(msg.limit() - msg.position());
		BigInteger channelId = new BigInteger(1, channelIdBytes);
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
		byte[] channelIdBytes =
		    msg.getBytes(msg.limit() - msg.position());
		BigInteger channelId = new BigInteger(1, channelIdBytes);
                SimpleClientChannel channel = channels.remove(channelId);
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
                BigInteger channelId =
		    new BigInteger(1, msg.getBytes(msg.getShort()));
                SimpleClientChannel channel = channels.get(channelId);
                if (channel == null) {
                    logger.log(Level.WARNING,
                        "Ignore message on channel {0}: not a member",
                        channelId);
                    return;
                }
		byte[] msgBytes = msg.getBytes(msg.limit() - msg.position());
		ByteBuffer buf = ByteBuffer.wrap(msgBytes);
                channel.receivedMessage(buf.asReadOnlyBuffer());
                break;

            default:
                throw new IOException(
                    String.format("Unknown session opcode: 0x%02X", command));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void reconnected(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.WARNING, re, re.getMessage());
            throw re;
        }

        /**
         * {@inheritDoc}
         */
        public void reconnecting(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.WARNING, re, re.getMessage());
            throw re;
        }

        /**
         * {@inheritDoc}
         */
        public ServerSessionListener sessionStarted(byte[] message) {
            RuntimeException re =
                new UnsupportedOperationException(
                        "Not supported by SimpleClient");
            logger.logThrow(Level.WARNING, re, re.getMessage());
            throw re;
        }
    }

    /**
     * Simple ClientChannel implementation
     */
    final class SimpleClientChannel implements ClientChannel {

        private final String channelName;
	private final BigInteger channelId;
        
        /**
         * The listener for this channel if the client is a member,
         * or null if the client is no longer a member of this channel.
         */
        private volatile ClientChannelListener listener = null;

        private final AtomicBoolean isJoined = new AtomicBoolean(false);

        SimpleClientChannel(String name, BigInteger id) {
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
        public void send(ByteBuffer message) throws IOException {
            if (! isJoined.get()) {
                throw new IllegalStateException(
                    "Cannot send on unjoined channel " + channelName);
            }
	    byte[] idBytes = channelId.toByteArray();
	    ByteBuffer msg =
		ByteBuffer.allocate(3 + idBytes.length + message.remaining());
	    msg.put(SimpleSgsProtocol.CHANNEL_MESSAGE)
	       .putShort((short) idBytes.length)
	       .put(idBytes)
	       .put(message)
	       .flip();
            sendRaw(msg);
        }

        // Implementation details

        void joined() {
            if (! isJoined.compareAndSet(false, true)) {
                throw new IllegalStateException(
                    "Already joined to channel " + channelName);
            }

            assert listener == null;

            try {
                listener = clientListener.joinedChannel(this);

                if (listener == null) {
		    // FIXME: print a warning?
                    throw new NullPointerException(
                        "The returned ClientChannelListener must not be null");
                }
            } catch (RuntimeException ex) {
                isJoined.set(false);
                throw ex;
            }
        }

        void left() {
            if (! isJoined.compareAndSet(true, false)) {
                throw new IllegalStateException(
                    "Cannot leave unjoined channel " + channelName);
            }

            final ClientChannelListener l = this.listener;
            this.listener = null;

            l.leftChannel(this);
       }
        
        void receivedMessage(ByteBuffer message) {
            if (!  isJoined.get()) {
                throw new IllegalStateException(
                    "Cannot receive on unjoined channel " + channelName);
            }

            listener.receivedMessage(this, message);
        }
    }
}
