/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 *
 * --
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
    
    /** The listener for this simple client. */
    private final SimpleClientListener clientListener;

    /**
     * The current {@code ClientConnection}, if connected, or
     * {@code null} if disconnected.
     */
    private volatile ClientConnection clientConnection = null;

    /** A lock for accessing the {@code isSuspended} field and for sending
     * messages. */ 
    private final Object lock = new Object();

    /**
     * Indicates that either a connection or disconnection attempt
     * is in progress.
     */
    private volatile boolean connectionStateChanging = false;

    /** Indicates whether this client is logged in. */
    private volatile boolean loggedIn = false;

    /** Indicates whether this client is temporarily suspended. */
    private boolean isSuspended = false;

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
        this.clientListener = listener;
    }

    /**
     * Initiates a login session with the server. A session is established
     * asynchronously with the server as follows:
     *
     * <p>First, this client attempts to establish a connection with the
     * server.  If the client fails to establish a connection, then the
     * client listener's {@link SimpleClientListener#disconnected
     * disconnected} method is invoked with a {@code String} indicating the
     * reason for the failure.
     *
     * <p>If a connection with the server is successfully established, this
     * client's {@link PasswordAuthentication login credential} 
     * is obtained by invoking its {@link SimpleClientListener listener}'s
     * {@link SimpleClientListener#getPasswordAuthentication
     * getPasswordAuthentication} method with a login prompt.
     * 
     * <p>Next, this client sends a login request to the server.  If the
     * login request is malformed, the client listener's {@link
     * SimpleClientListener#disconnected disconnected} method is invoked
     * with a {@code String} indicating the reason for the failure or
     * {@code null} if no reason can be determined.
     *
     * <p>If the client's login credential (as obtained above) is
     * verified, then the client listener's {@link
     * SimpleClientListener#loggedIn loggedIn} method is invoked. If,
     * however, the login fails due to a login authentication failure or
     * some other failure on the server while processing the login request,
     * the client listener's {@link SimpleClientListener#loginFailed
     * loginFailed} method is invoked with a {@code String} indicating the
     * reason for the failure.
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
        connector.connect(new SimpleClientConnectionListener());
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
                disconnectClientConnection();
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
                    disconnectClientConnection();
                } catch (IOException e2) {
                    logger.logThrow(Level.FINE, e2, "During forced logout:");
                    // ignore
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note: The server does not guarantee delivery of any session
     * message (sent via this method) that is received by the server before
     * the sending client is logged in.  Therefore messages sent before this
     * client is logged in, that is, before the {@link
     * SimpleClientListener#loggedIn SimpleClientListener.loggedIn} method
     * is invoked, may be dropped by the server.
     */
    public void send(ByteBuffer message) throws IOException {
	synchronized (lock) {
	    checkConnected();
	    if (isSuspended) {
		throw new IllegalStateException("client suspended");
	    }
	    ByteBuffer msg = ByteBuffer.allocate(1 + message.remaining());
	    msg.put(SimpleSgsProtocol.SESSION_MESSAGE)
	       .put(message)
	       .flip();
	    sendRaw(msg);
	}
    }

    /**
     * Sends raw data to the underlying connection without checking whether
     * the client is logged in or suspended.
     * 
     * @param buf the data to send
     * @throws IOException if an IO problem occurs
     */
    private void sendRaw(ByteBuffer buf) throws IOException {
	ClientConnection conn = clientConnection;
	if (conn != null) {
	    conn.sendMessage(buf);
	} else {
	    throw new IOException("client disconnected or reconnecting");
	}
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
        if (!loggedIn) {
            RuntimeException re =
                new IllegalStateException("Client not logged in");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
    }

    /**
     * Disconnects the underlying client connection.
     */
    private void disconnectClientConnection() throws IOException {
	ClientConnection conn = clientConnection;
	if (conn != null) {
	    conn.disconnect();
	}
    }

    /**
     * Marks this client as no longer suspended, and invokes the {@code
     * reconnected} method on the associated {@code SimpleClientListener}.
     */
    private void notifyReconnected() {
	synchronized (lock) {
	    isSuspended = false;
	    clientListener.reconnected();
	}
    }

    /**
     * Receives callbacks on the associated {@code ClientConnection}.
     */
    final class SimpleClientConnectionListener
        implements ClientConnectionListener
    {
            
	/** The password authentication used for login.
	 */
	private volatile PasswordAuthentication authentication = null;
    
        /** Indicates whether this listener expects a disconnect message. */
        private volatile boolean expectingDisconnect = false;

	/** Indicates whether the disconnected callback should not be
	 * invoked. */
	private volatile boolean loginFailed = false;

        /** Indicates whether this listener has been disabled because
         *  of an automatic login redirect to another host and port.
         *  We need to disconnect our previous connection, but we don't
         *  want to tell the client listener.  We'll accept no messages
         *  when we're in this state.
         */
        private volatile boolean redirectedOrRelocated = false;
	
        /**
         * Store the login failure message sent by the server. This will be
         * held until the disconnection from the server is confirmed by a call
         * to disconnected, at which point the application's loginFailure
         * callback will be called with this string as the reason.
         */
	private volatile String loginFailureMsg;

	/** The relocation key, or null, if the client is not relocating. */
	private final byte[] relocateKey;


	/** Constructs an instance. */
	SimpleClientConnectionListener() {
	    relocateKey = null;
	}

	/**
	 * Constructs an instance with the specified password {@code
	 * authentication}.  This is used in the redirect case, so the
	 * password authentication doesn't need to be reobtained from the
	 * user.
	 */
	SimpleClientConnectionListener(PasswordAuthentication authentication) {
	    this.authentication = authentication;
	    relocateKey = null;
	}

	/**
	 * Constructs an instance with the specified {@code relocateKey}.
	 * This constructor is used when the client is relocating its
	 * connection to a new node.
	 *
	 */
	SimpleClientConnectionListener(byte[] relocateKey) {
	    this.relocateKey = relocateKey;
	}
        
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
	    
	    if (relocateKey != null) {
		sendRelocateRequest();
	    } else {
		sendLoginRequest();
	    }
        }

	/**
	 * Sends a login request, obtaining a password authentication if
	 * one was not provided upon construction.
	 */
	private void sendLoginRequest() {
	    assert relocateKey == null;
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
	 * Sends a relocate request.
	 */
	private void sendRelocateRequest() {
	    assert relocateKey != null;
	    ByteBuffer buf = ByteBuffer.allocate(2 + relocateKey.length);
	    buf.put(SimpleSgsProtocol.RELOCATE_REQUEST).
		put(SimpleSgsProtocol.VERSION).
		put(relocateKey).
		flip();
	    try {
		sendRaw(buf.asReadOnlyBuffer());
	    } catch (IOException e) {
		logger.logThrow(Level.FINE, e, "During relocate request:");
		logout(true);
	    }
	}

        /**
         * {@inheritDoc}
         */
        public void disconnected(boolean graceful, byte[] message) {
            if (redirectedOrRelocated) {
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
                if (clientConnection == null && (!connectionStateChanging)) {
                    // Someone else beat us here
                    return;
                }
                clientConnection = null;
                connectionStateChanging = false;
		isSuspended = false;
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
            if (loginFailed) {
                loginFailed = false;
                clientListener.loginFailed(loginFailureMsg);
	    } else {
                clientListener.disconnected(expectingDisconnect, reason);
            }
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
                        disconnectClientConnection();
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
                handleLoginSuccess(msg);
                break;

            case SimpleSgsProtocol.LOGIN_FAILURE:
                handleLoginFailure(msg);
                break;

            case SimpleSgsProtocol.LOGIN_REDIRECT:
                handleLoginRedirect(msg);
                break;

	    case SimpleSgsProtocol.SUSPEND_MESSAGES:
		handleSuspendMessages(msg);
		break;

	    case SimpleSgsProtocol.RESUME_MESSAGES:
		handleResumeMessages(msg);
		break;

	    case SimpleSgsProtocol.RELOCATE_NOTIFICATION:
		handleRelocateNotification(msg);
		break;
		
	    case SimpleSgsProtocol.RELOCATE_SUCCESS:
		handleRelocateSuccess(msg);
		break;
		
	    case SimpleSgsProtocol.RELOCATE_FAILURE:
		handleRelocateFailure(msg);
		break;

            case SimpleSgsProtocol.SESSION_MESSAGE:
                handleSessionMessage(msg);
                break;

            case SimpleSgsProtocol.RECONNECT_SUCCESS:
                handleReconnectSuccess(msg);
                break;

            case SimpleSgsProtocol.RECONNECT_FAILURE:
                handleReconnectFailure(msg);
                break;

            case SimpleSgsProtocol.LOGOUT_SUCCESS:
                handleLogoutSuccess(msg);
                break;

            case SimpleSgsProtocol.CHANNEL_JOIN:
                handleChannelJoin(msg);
                break;

            case SimpleSgsProtocol.CHANNEL_LEAVE:
                handleChannelLeave(msg);
                break;

            case SimpleSgsProtocol.CHANNEL_MESSAGE:
                handleChannelMessage(msg);
                break;

            default:
                throw new IOException(
                    String.format("Unknown session opcode: 0x%02X", command));
            }
        }
        
        /**
         * Process a login success message
         * 
         * @param msg the message to process
         */
        private void handleLoginSuccess(MessageBuffer msg) {
            logger.log(Level.FINER, "Logged in");
            reconnectKey = msg.getBytes(msg.limit() - msg.position());
            loggedIn = true;
            clientListener.loggedIn();
        }
        
        /**
         * Process a login failure message
         * 
         * @param msg the message to process
         */
        private void handleLoginFailure(MessageBuffer msg) {
            String reason = msg.getString();
            logger.log(Level.FINER, "Login failed: {0}", reason);
            loginFailed = true;
            try {
                disconnectClientConnection();
            } catch (IOException e) {
                // ignore
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                                    "Disconnecting after login failure throws");
                }
            }
            loginFailureMsg = reason;
        }
        
        /**
         * Process a login redirect message
         * 
         * @param msg the message to process
         * @throws IOException if an IO problem occurs
         */
        private void handleLoginRedirect(MessageBuffer msg) 
	    throws IOException
	{
            String host = msg.getString();
            int port = msg.getInt();
            logger.log(Level.FINER, "Login redirect: {0}:{1}", host, port);

	    redirectOrRelocateToNewNode(
		host, port, new SimpleClientConnectionListener(authentication));
        }

	/**
	 * Process a suspend messages request.
         * 
         * @param msg the message to process
         * @throws IOException if an IO problem occurs
         */
	private void handleSuspendMessages(MessageBuffer msg)
	    throws IOException
	{
	    logger.log(Level.FINER, "Suspend messages");
	    checkLoggedIn();
	    synchronized (lock) {
		clientListener.reconnecting();
		isSuspended = true;
	    }
	    ByteBuffer ack = ByteBuffer.allocate(1);
	    ack.put(SimpleSgsProtocol.SUSPEND_MESSAGES_COMPLETE)
	       .flip();
	    sendRaw(ack);
	}

	/**
	 * Process a resume messages request.
         * 
         * @param msg the message to process
         * @throws IOException if an IO problem occurs
         */
	private void handleResumeMessages(MessageBuffer msg) {
	    logger.log(Level.FINER, "Resume messages");
	    checkLoggedIn();
	    notifyReconnected();
	}

	/**
	 * Processes a relocate notification message.
	 *
	 * @param msg the message to process
	 * @throws IOException if an I/O problem occurs
	 */
	private void handleRelocateNotification(MessageBuffer msg)
	    throws IOException
	{
	    String host = msg.getString();
	    int port = msg.getInt();
	    byte[] relocateKey =
		msg.getBytes(msg.limit() - msg.position());
	    logger.log(Level.FINER, "Relocate notification: {0}:{1}",
		       host, port);
	    checkLoggedIn();
	    synchronized (lock) {
		if (!isSuspended) {
		    // Client messages weren't suspended first, so
		    // disconnect client.
		    try {
			disconnectClientConnection();
		    } catch (IOException e) {
			// ignore
			if (logger.isLoggable(Level.FINE)) {
			    logger.logThrow(
				Level.FINE, e, "Disconnecting (because " +
				"relocate notification received before " +
				"suspend) throws");
			}
		    }
		    throw new IllegalStateException("client not suspended");
		}
	    }
		    
	    redirectOrRelocateToNewNode(
 		host, port, new SimpleClientConnectionListener(relocateKey));
	}

	/**
	 * Disconnects the existing connection and creates a new connection
	 * with the specified connection {@code listener} to the node with
	 * the specified {@code host} and {@code port}.
	 *
	 * @param host the host to connect to
	 * @param port the port to connect to
	 * @param listener the connection listener for the new connection
	 * @throws IOException if an I/O problem occurs
	 */
	private void redirectOrRelocateToNewNode(
	    String host, int port, ClientConnectionListener listener)
	    throws IOException
	{
            // Disconnect our current connection, and connect to the
            // new host and port
            ClientConnection oldConnection = clientConnection;
            synchronized (SimpleClient.this) {
                clientConnection = null;
                connectionStateChanging = true;
            }
            try {
                oldConnection.disconnect();
            } catch (IOException e) {
                // ignore
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                                    "Disconnecting after login redirect " + 
                                    "throws");
                }
            }
            redirectedOrRelocated = true;
            Properties props = new Properties();
            props.setProperty("host", host);
            props.setProperty("port", String.valueOf(port));
            ClientConnector connector = ClientConnector.create(props);
            // This eventually causes connected to be called
            connector.connect(listener);
	}

	/**
	 * Processes a relocate success message.
	 *
         * @param msg the message to process
	 */
	private void handleRelocateSuccess(MessageBuffer msg) {
            logger.log(Level.FINER, "Relocate successful");
            reconnectKey = msg.getBytes(msg.limit() - msg.position());
            loggedIn = true;
	    notifyReconnected();
	}
	
	/**
	 * Processes a relocate failure message.
	 *
         * @param msg the message to process
	 */
	private void handleRelocateFailure(MessageBuffer msg) {
	    // TBD: would be nice to supply msg to the client's
	    // disconnected callback.
            String reason = msg.getString();
            logger.log(Level.FINER, "Relocate failed: {0}", reason);
            try {
                disconnectClientConnection();
            } catch (IOException e) {
                // ignore
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
			Level.FINE, e,
			"Disconnecting after relocate failure throws");
                }
            }
	}
	
        /**
         * Process a session message
         * 
         * @param msg the message to process
         */
        private void handleSessionMessage(MessageBuffer msg) {
            logger.log(Level.FINEST, "Direct receive");
            checkLoggedIn();
            byte[] msgBytes = msg.getBytes(msg.limit() - msg.position());
            ByteBuffer buf = ByteBuffer.wrap(msgBytes);
            try {
                clientListener.receivedMessage(buf.asReadOnlyBuffer());
            } catch (RuntimeException e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.logThrow(
                            Level.WARNING, e,
                            "SimpleClientListener.receivedMessage callback " +
                            "throws");
                }
            }
        }
        
        /**
         * Process a reconnect success message
         * 
         * @param msg the message to process
         */
        private void handleReconnectSuccess(MessageBuffer msg) {
            logger.log(Level.FINER, "Reconnected");
            loggedIn = true;
            reconnectKey = msg.getBytes(msg.limit() - msg.position());
	    notifyReconnected();
        }
        
        /**
         * Process a reconnect failure message
         * 
         * @param msg the message to process
         */
        private void handleReconnectFailure(MessageBuffer msg) {
            try {
                String reason = msg.getString();
                logger.log(Level.FINER, "Reconnect failed: {0}", reason);
                disconnectClientConnection();
            } catch (IOException e) {
                // ignore
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                                    "Disconnecting a failed reconnect throws");
                }
            }
        }
        
        /**
         * Process a logout success message
         * 
         * @param msg the message to process
         */
        private void handleLogoutSuccess(MessageBuffer msg) {
            logger.log(Level.FINER, "Logged out gracefully");
            expectingDisconnect = true;
            loggedIn = false;
            try {
                disconnectClientConnection();
            } catch (IOException e) {
                // ignore
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
                                    "Disconnecting after graceful logout " +
                                    "throws");
                }
            }
        }
        
        /**
         * Process a channel join message
         * 
         * @param msg the message to process
         */
        private void handleChannelJoin(MessageBuffer msg) {
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
        }
        
        /**
         * Process a channel leave message
         * 
         * @param msg the message to process
         */
        private void handleChannelLeave(MessageBuffer msg) {
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
        }
        
        /**
         * Process a channel message message
         * 
         * @param msg the message to process
         */
        private void handleChannelMessage(MessageBuffer msg) {
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
	    synchronized (lock) {
		if (isSuspended) {
		    throw new IllegalStateException("client suspended");
		}
		if (!isJoined.get()) {
		    throw new IllegalStateException(
                        "Cannot send on unjoined channel " + channelName);
		}
		byte[] idBytes = channelId.toByteArray();
		ByteBuffer msg =
		    ByteBuffer.allocate(3 + idBytes.length +
					message.remaining());
		msg.put(SimpleSgsProtocol.CHANNEL_MESSAGE)
		   .putShort((short) idBytes.length)
		   .put(idBytes)
		   .put(message)
		   .flip();
		sendRaw(msg);
	    }
	}

        // Implementation details

        void joined() {
            if (!isJoined.compareAndSet(false, true)) {
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
            if (!isJoined.compareAndSet(true, false)) {
                throw new IllegalStateException(
                    "Cannot leave unjoined channel " + channelName);
            }

            final ClientChannelListener l = this.listener;
            this.listener = null;

            l.leftChannel(this);
       }
        
        void receivedMessage(ByteBuffer message) {
            if (!isJoined.get()) {
                throw new IllegalStateException(
                    "Cannot receive on unjoined channel " + channelName);
            }

            listener.receivedMessage(this, message);
        }
    }
}
