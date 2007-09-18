/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.ProtocolMessageListener;
import java.io.IOException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.LoginException;

/**
 * Handles sending/receiving messages to/from a client session and
 * disconnecting a client session.
 */
class ClientSessionHandler {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;
    
    /** Connection state. */
    private static enum State {
        /** A connection is in progress */
	CONNECTING,
        /** Session is connected */
        CONNECTED,
        /** Reconnection is in progress */
        RECONNECTING,
        /** Disconnection is in progress */
        DISCONNECTING, 
        /** Session is disconnected */
        DISCONNECTED
    }

    /** Random number generator for generating session ids. */
    private static final Random random = new Random(getSeed());
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(
	    "com.sun.sgs.impl.service.session.handler"));

    /** Message for indicating login/authentication failure. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";

    /** The LOGIN_FAILURE protocol message. */
    private static final byte[] loginFailureMessage = getLoginFailureMessage();

    /** The client session associated with this session handler,
     * or {@code null} if the session hasn't completed login.
     */
    private final ClientSessionImpl sessionImpl;

    /** The client session service that created this client session. */
    private final ClientSessionServiceImpl sessionService;

    /** The data service. */
    private final DataService dataService;

    /** The Connection for sending messages to the client. */
    private volatile Connection sessionConnection;

    /** The session ID. */
    private final CompactId compactId;

    /** The session ID bytes. */
    private final byte[] idBytes;

    /** The reconnection key. */
    private final CompactId reconnectionKey;

    /** The ConnectionListener for receiving messages from the client. */
    private final ConnectionListener connectionListener;

    /** The identity for this session. */
    private volatile Identity identity;

    /** The lock for accessing the following fields:
     * {@code state}, {@code disconnectHandled}, and {@code shutdown}.
     */
    private final Object lock = new Object();
    
    /** The connection state, accessed. */
    private State state = State.CONNECTING;

    /** Indicates whether session disconnection has been handled. */
    private boolean disconnectHandled = false;

    /** Indicates whether this session is shut down. */
    private boolean shutdown = false;

    /** The queue of tasks for notifying listeners of received messages. */
    private volatile NonDurableTaskQueue taskQueue = null;

    /**
     * Constructs an instance of this class with the specified session
     * {@code id}.
     *
     * @param	id the session ID
     */
    ClientSessionHandler(byte[] id) {
	this.sessionService = ClientSessionServiceImpl.getInstance();
        this.dataService = sessionService.dataService;
	this.connectionListener = new Listener();
	this.compactId = new CompactId(id);
	this.idBytes = compactId.getId();
	this.sessionImpl = new ClientSessionImpl(compactId);
	this.reconnectionKey = compactId; // not used yet

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "creating ClientSessionHandler: id:{0}, nodeId:{1}",
		       compactId, sessionService.getLocalNodeId());
	}
    }

    /* -- Instance methods -- */

    /**
     * Returns the client session.
     *
     * @return	the client session
     */
    ClientSessionImpl getClientSession() {
	return sessionImpl;
    }

    /**
     * Returns the client session ID.
     *
     * @return 	the client session ID
     */
    ClientSessionId getSessionId() {
        return new ClientSessionId(idBytes);
    }

    /**
     * Returns the {@code ConnectionListener} for this session.
     *
     * @return	the {@code ConnectionListener} for this session
     */
    ConnectionListener getConnectionListener() {
	return connectionListener;
    }

    /**
     * Returns {@code true} if this handler is connected, otherwise
     * returns {@code false}.
     *
     * @return	{@code true} if this handler is connected
     */
    boolean isConnected() {

	State currentState = getCurrentState();

	boolean connected =
	    currentState == State.CONNECTING ||
	    currentState == State.CONNECTED ||
	    currentState == State.RECONNECTING;

	logger.log(Level.FINEST, "isConnected returns {0}", connected);
	return connected;
    }

    /**
     * Immediately sends the specified protocol {@code message}
     * according to the specified {@code delivery} requirement.
     */
    void sendProtocolMessage(byte[] message, Delivery delivery) {
	// TBI: ignore delivery for now...
	try {
	    if (getCurrentState() != State.DISCONNECTED) {
		sessionConnection.sendBytes(message);
	    } else {
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(
		        Level.FINER,
			"sendProtocolMessage session:{0} " +
			"session is disconnected", this);
		}
	    }
		    
	} catch (IOException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "sendProtocolMessage session:{0} throws", this);
	    }
	}
	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST,
		"sendProtocolMessage session:{0} message:{1} returns",
		this, HexDumper.format(message));
	}
    }

    /**
     * Handles a disconnect request (if not already handled) by doing
     * the following:
     *
     * a) sending a disconnect acknowledgment (LOGOUT_SUCCESS)
     * if 'graceful' is true
     *
     * b) closing this session's connection
     *
     * c) submitting a transactional task to call the 'disconnected'
     * callback on the listener for this session.
     *
     * @param graceful if the disconnection was graceful (i.e., due to
     * a logout request).
     */
    void handleDisconnect(final boolean graceful) {
	synchronized (lock) {
	    if (disconnectHandled) {
		return;
	    }
	    disconnectHandled = true;
	    if (state != State.DISCONNECTED) {
		state = State.DISCONNECTING;
	    }
	}

	sessionService.disconnected(sessionImpl);
	
	if (identity != null) {
	    // TBD: Due to the scheduler's behavior, this notification
	    // may happen out of order with respect to the
	    // 'notifyLoggedIn' callback.  Also, this notification may
	    // also happen even though 'notifyLoggedIn' was not invoked.
	    // Are these behaviors okay?  -- ann (3/19/07)
	    final Identity thisIdentity = identity;
	    scheduleTask(new AbstractKernelRunnable() {
		    public void run() {
			thisIdentity.notifyLoggedOut();
		    }});
	}

	if (getCurrentState() != State.DISCONNECTED) {
	    if (graceful) {
		MessageBuffer buf = new MessageBuffer(3);
		buf.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		    putByte(SimpleSgsProtocol.LOGOUT_SUCCESS);
	    
		sendProtocolMessage(buf.getBuffer(), Delivery.RELIABLE);
	    }

	    try {
		sessionConnection.close();
	    } catch (IOException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
		    	Level.WARNING, e,
			"handleDisconnect (close) handle:{0} throws",
			sessionConnection);
		}
	    }
	}

	scheduleTask(new AbstractKernelRunnable() {
	    public void run() throws IOException {
		ClientSessionImpl sessionImpl =
		    ClientSessionImpl.getSession(dataService, idBytes);
		if (sessionImpl != null) {
		    sessionImpl.
			notifyListenerAndRemoveSession(dataService, graceful);
		}
	    }});
    }

    /**
     * Flags this session as shut down, and closes the connection.
     */
    void shutdown() {
	synchronized (lock) {
	    if (shutdown == true) {
		return;
	    }
	    shutdown = true;
	    disconnectHandled = true;
	    state = State.DISCONNECTED;
	    if (sessionConnection != null) {
		try {
		    sessionConnection.close();
		} catch (IOException e) {
		    // ignore
		}
	    }
	}
    }
    
    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    ClientSessionHandler session = (ClientSessionHandler) obj;
	    return
		areEqualIdentities(identity, session.identity) &&
		compactId.equals(session.compactId);
	}
	return false;
    }

    /**
     * Returns {@code true} if the given identities are either both
     * null, or both non-null and invoking {@code equals} on the first
     * identity passing the second identity returns {@code true}.
     */
    private static boolean areEqualIdentities(Identity id1, Identity id2) {
	if (id1 == null) {
	    return id2 == null;
	} else if (id2 == null) {
	    return false;
	} else {
	    return id1.equals(id2);
	}
    }
    
    /** {@inheritDoc} */
    public int hashCode() {
	return compactId.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + identity + "]@" + compactId;
    }

    /* -- ConnectionListener implementation -- */

    /**
     * Listener for connection-related events for this session's
     * Connection.
     */
    private class Listener implements ConnectionListener {

	/** {@inheritDoc} */
	public void connected(Connection conn) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "Handler.connected handle:{0}", conn);
	    }

	    synchronized (lock) {
		// check if there is already a handle set
		if (sessionConnection != null) {
		    return;
		}

		sessionConnection = conn;
		
		switch (state) {
		    
		case CONNECTING:
		case RECONNECTING:
		    state = State.CONNECTED;
		    break;
		default:
		    break;
		}
	    }
	}

	/** {@inheritDoc} */
	public void disconnected(Connection conn) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "Handler.disconnected handle:{0}", conn);
	    }

	    synchronized (lock) {
		if (conn != sessionConnection) {
		    return;
		}

		if (!disconnectHandled) {
		    scheduleNonTransactionalTask(new AbstractKernelRunnable() {
			public void run() {
			    handleDisconnect(false);
			}});
		}

		state = State.DISCONNECTED;
	    }
	}

	/** {@inheritDoc} */
	public void exceptionThrown(Connection conn, Throwable exception) {

	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, exception,
		    "Handler.exceptionThrown handle:{0}", conn);
	    }
	}

	/** {@inheritDoc} */
	public void bytesReceived(Connection conn, byte[] buffer) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(
                    Level.FINEST,
                    "Handler.messageReceived handle:{0}, buffer:{1}",
                    conn, buffer);
            }
	    
	    synchronized (lock) {
		if (conn != sessionConnection) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(
                            Level.FINE, 
                            "Handle mismatch: expected: {0}, got: {1}",
                            sessionConnection, conn);
                    }
		    return;
		}
	    }
	    
	    if (buffer.length < 3) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
		        Level.SEVERE,
			"Handler.messageReceived malformed protocol message:{0}",
			buffer);
		}
		// TBD: should the connection be disconnected?
		return;
	    }

	    MessageBuffer msg = new MessageBuffer(buffer);
		
	    /*
	     * Handle version.
	     */
	    byte version = msg.getByte();
	    if (version != SimpleSgsProtocol.VERSION) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"Handler.messageReceived protocol version:{0}, " +
			"expected {1}", version, SimpleSgsProtocol.VERSION);
		}
		    // TBD: should the connection be disconnected?
		return;
	    }

	    /*
	     * Dispatch message to service.
	     */
	    byte serviceId = msg.getByte();

	    if (serviceId == SimpleSgsProtocol.APPLICATION_SERVICE) {
		handleApplicationServiceMessage(msg);
	    } else {
		ProtocolMessageListener serviceListener =
		    sessionService.getProtocolMessageListener(serviceId);
		if (serviceListener != null) {
		    if (identity == null) {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.log(
			        Level.WARNING,
				"session:{0} received message for " +
				"service ID:{1} before successful login",
				this, serviceId);
			    return;
			}
		    }
		    
		    serviceListener.receivedMessage(sessionImpl, buffer);
		    
		} else {
		    if (logger.isLoggable(Level.SEVERE)) {
		    	logger.log(
			    Level.SEVERE,
			    "session:{0} unknown service ID:{1}",
			    this, serviceId);
		    }
		}
	    }
	}

	/**
	 * Handles an APPLICATION_SERVICE message received by the
	 * {@code bytesReceived} method.  When this method is invoked,
	 * the specified message buffer's current position points to
	 * the operation code of the protocol message.  The protocol
	 * version and service ID have already been processed by the
	 * caller.
	 */
	private void handleApplicationServiceMessage(MessageBuffer msg) {
	    byte opcode = msg.getByte();

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
 		    Level.FINEST,
		    "Handler.messageReceived processing opcode:{0}",
		    Integer.toHexString(opcode));
	    }
	    
	    switch (opcode) {
		
	    case SimpleSgsProtocol.LOGIN_REQUEST:

		String name = msg.getString();
		String password = msg.getString();
		handleLoginRequest(name, password);
		break;
		
	    case SimpleSgsProtocol.RECONNECT_REQUEST:
		break;

	    case SimpleSgsProtocol.SESSION_MESSAGE:
		if (identity == null) {
		    logger.log(
		    	Level.WARNING,
			"session message received before login:{0}", this);
		    break;
		}
                msg.getLong(); // TODO Check sequence num
		int size = msg.getUnsignedShort();
		final byte[] clientMessage = msg.getBytes(size);
		taskQueue.addTask(new AbstractKernelRunnable() {
		    public void run() {
			if (isConnected()) {
			    sessionImpl.getClientSessionListener(dataService).
				receivedMessage(clientMessage);
			}
		    }});
		break;

	    case SimpleSgsProtocol.LOGOUT_REQUEST:
	        scheduleNonTransactionalTask(new AbstractKernelRunnable() {
	            public void run() {
	                handleDisconnect(isConnected());
	            }});
		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"Handler.messageReceived unknown operation code:{0}",
			opcode);
		}

		scheduleNonTransactionalTask(new AbstractKernelRunnable() {
		    public void run() {
			handleDisconnect(false);
		    }});
		break;
	    }
	}
	
	/**
	 * Handles a login request for the specified {@code name} and
	 * {@code password}, scheduling the appropriate response to be
	 * sent to the client (either LOGIN_SUCCESS, LOGIN_FAILURE, or
	 * LOGIN_REDIRECT).
	 */
	private void handleLoginRequest(String name, String password) {

	    logger.log(
		Level.FINEST, 
		"handling login request for name:{0}", name);

	    /*
	     * Authenticate identity.
	     */
	    final Identity authenticatedIdentity;
	    try {
		authenticatedIdentity = authenticate(name, password);
	    } catch (Exception e) {
		logger.logThrow(
		    Level.FINEST, e,
		    "login authentication failed for name:{0}", name);
		sendLoginFailureAndDisconnect();
		return;
	    }

	    Node node;
	    try {
		/*
		 * Get node assignment.
		 */
		sessionService.nodeMapService.assignNode(
		    ClientSessionHandler.class, authenticatedIdentity);
		GetNodeTask getNodeTask =
		    new GetNodeTask(authenticatedIdentity);		
		sessionService.runTransactionalTask(
		    getNodeTask, authenticatedIdentity);
		node = getNodeTask.getNode();
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(Level.FINE, "identity:{0} assigned to node:{1}",
			       name, node);
		}

	    } catch (Exception e) {
		logger.logThrow(
		    Level.WARNING, e,
		    "getting node assignment for identity:{0} throws", name);
		sendLoginFailureAndDisconnect();
		return;
	    }

	    long assignedNodeId = node.getId();
	    if (assignedNodeId == sessionService.getLocalNodeId()) {
		/*
		 * Handle login request locally.
		 */
		taskQueue =
		    new NonDurableTaskQueue(
			sessionService.txnProxy,
			sessionService.nonDurableTaskScheduler,
			authenticatedIdentity);
		sessionImpl.setIdentityAndNodeId(
		    authenticatedIdentity, assignedNodeId);
		identity = authenticatedIdentity;
		scheduleTask(new LoginTask());
		
	    } else {
		/*
		 * Redirect login to assigned (non-local) node.
		 */
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(
			Level.FINE,
			"redirecting login for identity:{0} " +
			"from nodeId:{1} to node:{2}",
			name, sessionService.getLocalNodeId(), node);
		}
		final byte[] loginRedirectMessage =
		    getLoginRedirectMessage(node.getHostName());
		scheduleNonTransactionalTask(new AbstractKernelRunnable() {
		    public void run() {
			sendProtocolMessage(
			    loginRedirectMessage, Delivery.RELIABLE);
			handleDisconnect(false);
		    }});

		try {
		    /*
		     * Set identity's status for this class to 'false'.
		     *
		     * TBD: Should this handler wait before invoking
		     * 'setStatus' to ensure that the node assignment
		     * for the identity remains in the node map so the
		     * assingment doesn't change before the login
		     * redirect can be handled by the client? -- ann (8/29/07)
		     */
		    sessionService.nodeMapService.setStatus(
			ClientSessionHandler.class, authenticatedIdentity,
			false);
		    
		} catch (Exception e) {
		    // TBD: Is it OK to assume that node map service
		    // will handle retries of 'setStatus' if they fail, or
		    // does this handler need to retry 'setStatus' until
		    // it succeeds? -- ann (8/29/07)
		    logger.logThrow(
		       Level.WARNING, e,
		       "setting status for identity:{0} throws", name);

		}
	    }
	}

	/**
	 * Sends the LOGIN_FAILURE protocol message to the client and
	 * disconnects the client session.
	 */
	private void sendLoginFailureAndDisconnect() {
	    scheduleNonTransactionalTask(new AbstractKernelRunnable() {
		public void run() {
		    sendProtocolMessage(loginFailureMessage, Delivery.RELIABLE);
		    handleDisconnect(false);
		}});
	}
    }

    /* -- other private methods and classes -- */

    /**
     * Returns the current state.
     */
    private State getCurrentState() {
	State currentState;
	synchronized (lock) {
	    currentState = state;
	}
	return currentState;
    }

    /** Returns a random seed to use in generating session ids. */
    private static long getSeed() {
	byte[] seedArray = SecureRandom.getSeed(8);
	long seed = 0;
	for (long b : seedArray) {
	    seed <<= 8;
	    seed += b & 0xff;
	}
	return seed;
    }

    /**
     * Authenticates the specified username and password, throwing
     * LoginException if authentication fails.
     */
    private Identity authenticate(String username, String password)
	throws LoginException
    {
	return sessionService.identityManager.authenticateIdentity(
	    new NamePasswordCredentials(username, password.toCharArray()));
    }

    /**
     * Schedules a non-durable, transactional task.
     */
    private void scheduleTask(KernelRunnable task) {
	sessionService.scheduleTask(task, identity);
    }

    /**
     * Schedules a non-durable, non-transactional task.
     */
    private void scheduleNonTransactionalTask(KernelRunnable task) {
	sessionService.scheduleNonTransactionalTask(task, identity);
    }

    /**
     * This is a transactional task to obtain the node assignment for
     * a given identity.
     */
    private class GetNodeTask extends AbstractKernelRunnable {

	private final Identity authenticatedIdentity;
	private volatile Node node = null;

	GetNodeTask(Identity authenticatedIdentity) {
	    this.authenticatedIdentity = authenticatedIdentity;
	}

	public void run() throws Exception {
	    node = sessionService.
		nodeMapService.getNode(authenticatedIdentity);
	}

	Node getNode() {
	    return node;
	}
    }
    
    /**
     * This is a transactional task to notify the application's
     * {@code AppListener} that this session has logged in.
     */
    private class LoginTask extends AbstractKernelRunnable {

	/**
	 * Invokes the {@code AppListener}'s {@code loggedIn}
	 * callback, which returns a client session listener.  If the
	 * returned listener is serializable, then this method does
	 * the following:
	 *
	 * a) queues the appropriate acknowledgment to be
	 * sent when this transaction commits, and
	 * b) schedules a task (on transaction commit) to call
	 * {@code notifyLoggedIn} on the identity.
	 *
	 * If the client session needs to be disconnected (if {@code
	 * loggedIn} returns a non-serializable listener (including
	 * {@code null}), or throws a non-retryable {@code
	 * RuntimeException}, then this method submits a
	 * non-transactional task to disconnect the client session.
	 * If {@code loggedIn} throws a retryable {@code
	 * RuntimeException}, then that exception is thrown to the
	 * caller.
	 */
	public void run() {
	    AppListener appListener =
		dataService.getServiceBinding(
		    StandardProperties.APP_LISTENER, AppListener.class);
	    logger.log(
		Level.FINEST,
		"invoking AppListener.loggedIn session:{0}", identity);

	    sessionImpl.putSession(dataService);
	    ClientSessionListener returnedListener = null;
	    RuntimeException ex = null;
	    
	    try {
		returnedListener = appListener.loggedIn(sessionImpl);
	    } catch (RuntimeException e) {
		ex = e;
	    }
		
	    if (returnedListener instanceof Serializable) {
		logger.log(
		    Level.FINEST,
		    "AppListener.loggedIn returned {0}", returnedListener);

		sessionImpl.putClientSessionListener(
		    dataService, returnedListener);
		MessageBuffer ack =
		    new MessageBuffer(
			3 + compactId.getExternalFormByteCount() +
			reconnectionKey.getExternalFormByteCount());
		ack.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		    putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
		    putBytes(compactId.getExternalForm()).
		    putBytes(reconnectionKey.getExternalForm());
		
		sessionService.sendProtocolMessageFirst(
		    sessionImpl, ack.getBuffer(), Delivery.RELIABLE);

		final Identity thisIdentity = identity;
		sessionService.scheduleTaskOnCommit(
		    new AbstractKernelRunnable() {
			public void run() {
			    logger.log(
			        Level.FINE,
				"calling notifyLoggedIn on identity:{0}",
				thisIdentity);
			    // notify that this identity logged in,
			    // whether or not this session is connected at
			    // the time of notification.
			    thisIdentity.notifyLoggedIn();
			}});
		
	    } else {
		if (ex == null) {
		    logger.log(
		        Level.WARNING,
			"AppListener.loggedIn returned non-serializable " +
			"ClientSessionListener:{0}", returnedListener);
		} else if (!(ex instanceof ExceptionRetryStatus) ||
			   ((ExceptionRetryStatus) ex).shouldRetry() == false) {
		    logger.logThrow(
			Level.WARNING, ex,
			"Invoking loggedIn on AppListener:{0} with " +
			"session: {1} throws",
			appListener, ClientSessionHandler.this);
		} else {
		    throw ex;
		}
		sessionService.sendProtocolMessageFirst(
		    sessionImpl, loginFailureMessage, Delivery.RELIABLE);
		sessionService.disconnect(sessionImpl);
	    }
	}
    }

    /**
     * Returns a byte array containing a LOGIN_FAILURE protocol message.
     */
    private static byte[] getLoginFailureMessage() {
        int stringSize = MessageBuffer.getSize(LOGIN_REFUSED_REASON);
        MessageBuffer ack = new MessageBuffer(3 + stringSize);
        ack.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
            putByte(SimpleSgsProtocol.LOGIN_FAILURE).
            putString(LOGIN_REFUSED_REASON);
        return ack.getBuffer();
    }

    /**
     * Returns a byte array containing a LOGIN_REDIRECT protocol
     * message containing the given {@code hostname}.
     */
    private static byte[] getLoginRedirectMessage(String hostname) {
	int hostStringSize = MessageBuffer.getSize(hostname);
	MessageBuffer ack = new MessageBuffer(3 + hostStringSize);
        ack.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
            putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
            putString(hostname);
        return ack.getBuffer();
    }	
}
