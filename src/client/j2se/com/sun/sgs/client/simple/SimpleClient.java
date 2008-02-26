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
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * is communicating on.
     */
    private final ClientConnectionListener connListener =
        new SimpleClientConnectionListener();

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
    
    /** Indicates whether this client expects a disconnect message. */
    private volatile boolean expectingDisconnect = false;

    /** Indicates whether this client is logged in. */
    private volatile boolean loggedIn = false;

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
     * Next, if a connection with the server is successfully established and
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

            // TBI implement graceful disconnect.
            // For now, look at the boolean we set when expecting disconnect
            clientListener.disconnected(expectingDisconnect, reason);
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
                clientListener.loginFailed(reason);
                break;
            }

            case SimpleSgsProtocol.LOGIN_REDIRECT: {
                String endpoint = msg.getString();
                logger.log(Level.FINER, "Login redirect: {0}", endpoint);
                // TBI login redirect
                clientListener.loginFailed(
                    "unimplemented, want redirect to " + endpoint);
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
                            "Disconnecting a failed reconnect");
                    }
                    // ignore
                }
                break;

            case SimpleSgsProtocol.LOGOUT_SUCCESS:
                logger.log(Level.FINER, "Logged out gracefully");
                expectingDisconnect = true;
                loggedIn = false;

                // TODO verify that graceful works correctly

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
}
