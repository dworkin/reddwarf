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

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import static com.sun.sgs.impl.util.AbstractService.isRetryableException;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.LoginException;

/**
 * Handles sending/receiving messages to/from a client session and
 * disconnecting a client session.
 */
class ClientSessionHandler {

    /** Connection state. */
    private static enum State {
        /** A connection is in progress */
	CONNECTING,
        /** Session is connected */
        CONNECTED,
        /** Disconnection is in progress */
        DISCONNECTING, 
        /** Session is disconnected */
        DISCONNECTED
    }

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(
	    "com.sun.sgs.impl.service.session.handler"));

    /** Message for indicating login/authentication failure. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";

    /** The LOGIN_FAILURE protocol message. */
    private static final byte[] loginFailureMessage = getLoginFailureMessage();

    /** The client session service that created this client session. */
    private final ClientSessionServiceImpl sessionService;

    /** The data service. */
    private final DataService dataService;

    /**
     * The I/O channel for sending messages to the client, or null if
     * not connected.
     */
    private AsynchronousMessageChannel sessionConnection = null;

    /** The completion handler for reading from the I/O channel. */
    private ReadHandler readHandler = new ClosedReadHandler();

    /** The completion handler for writing to the I/O channel. */
    private WriteHandler writeHandler = new ClosedWriteHandler();

    /** The session ID as a BigInteger. */
    private volatile BigInteger sessionRefId;

    /** The identity for this session. */
    private volatile Identity identity;

    /** The lock for accessing the following fields:
     * {@code state}, {@code disconnectHandled}, and {@code shutdown}.
     */
    private final Object lock = new Object();
    
    /** The connection state. */
    private State state = State.CONNECTING;

    /** Indicates whether session disconnection has been handled. */
    private boolean disconnectHandled = false;

    /** Indicates whether this session is shut down. */
    private boolean shutdown = false;

    /** The queue of tasks for notifying listeners of received messages. */
    private volatile NonDurableTaskQueue taskQueue = null;

    /**
     * Constructs an instance of this class.
     */
    ClientSessionHandler(ClientSessionServiceImpl sessionService,
			 DataService dataService)
    {
	this.sessionService = sessionService;
        this.dataService = dataService;

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "creating new ClientSessionHandler on nodeId:{0}",
		        sessionService.getLocalNodeId());
	}
    }

    /* -- Instance methods -- */

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
	    currentState == State.CONNECTED;

	return connected;
    }

    /**
     * Immediately sends the specified protocol {@code message}
     * according to the specified {@code delivery} requirement.
     */
    void sendProtocolMessage(byte[] message, Delivery delivery) {
	// TBI: ignore delivery for now...
	try {
	    if (isConnected()) {
	        writeHandler.write(ByteBuffer.wrap(message));
	    } else {
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(
		        Level.FINER,
			"sendProtocolMessage session:{0} " +
			"session is disconnected", this);
		}
	    }
		    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "sendProtocolMessage session:{0} throws", this);
	    }
	}
    }

    /**
     * Handles a disconnect request (if not already handled) by doing
     * the following:
     *
     * a) sending a disconnect acknowledgment (LOGOUT_SUCCESS)
     *    if 'graceful' is true
     *
     * b) closing this session's connection
     *
     * c) submitting a transactional task to call the 'disconnected'
     *    callback on the listener for this session.
     *
     * d) notifying the identity (if non-null) that the session has
     *    logged out.
     *
     * e) notifying the node mapping service that the identity (if
     *    non-null) is no longer active.
     *
     * @param graceful if the disconnection was graceful (i.e., due to
     * a logout request).
     */
    void handleDisconnect(final boolean graceful) {

	logger.log(Level.FINEST, "handleDisconnect handler:{0}", this);
	
	synchronized (lock) {
	    if (disconnectHandled) {
		return;
	    }
	    disconnectHandled = true;
	    if (state != State.DISCONNECTED) {
		state = State.DISCONNECTING;
	    }
	}

	if (sessionRefId != null) {
	    sessionService.removeHandler(sessionRefId);
	}
	
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

	    deactivateIdentity(identity);
	}

	if (getCurrentState() != State.DISCONNECTED) {
	    if (graceful) {
	        byte[] msg = { SimpleSgsProtocol.LOGOUT_SUCCESS };

	        sendProtocolMessage(msg, Delivery.RELIABLE);
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

	readHandler = new ClosedReadHandler();
        writeHandler = new ClosedWriteHandler();

	if (sessionRefId != null) {
	    scheduleTask(new AbstractKernelRunnable() {
		public void run() {
		    ClientSessionImpl sessionImpl = 
			ClientSessionImpl.getSession(dataService, sessionRefId);
		    sessionImpl. notifyListenerAndRemoveSession(
			dataService, graceful, true);
		}
	    });
	}
    }

    /**
     * Schedule a non-transactional task for disconnecting the client.
     *
     * @param	graceful if {@code true}, disconnection is graceful (i.e.,
     * 		a LOGOUT_SUCCESS protocol message is sent before
     * 		disconnecting the client session)
     */
    private void scheduleHandleDisconnect(final boolean graceful) {

        // TODO: Should we set the state to DISCONNECTING? -JM
        /*
        synchronized (lock) {
            if (state != State.DISCONNECTED)
                state = State.DISCONNECTING;
        }
        */
        
	scheduleNonTransactionalTask(new AbstractKernelRunnable() {
	    public void run() {
		handleDisconnect(graceful);
	    }});
    }

    /**
     * Notifies this handler that a new I/O connection has become active.
     * 
     * @param conn the new connection
     */
    void connected(AsynchronousMessageChannel conn) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "connected handle:{0}", conn);
        }

        synchronized (lock) {
            // check if there is already a handle set
            if (sessionConnection != null) {
                logger.log(
		    Level.WARNING,
                    "session already connected to {0}", sessionConnection);
                try {
                    conn.close();
                } catch (IOException e) {
                    // ignore
                }
                return;
            }

            sessionConnection = conn;

            /*
             * TODO: It might be a good idea to implement high- and low-water
             * marks for the buffers, so they don't go into hysteresis when
             * they get full. -JM
             */

            readHandler = new ConnectedReadHandler();
            writeHandler = new ConnectedWriteHandler();

            switch (state) {

            case CONNECTING:
                state = State.CONNECTED;
                break;
            default:
                break;
            }
        }

        readHandler.read();
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
	    /*
	     * XXX: Why does this method set the field without calling
	     * scheduleHandleDisconnect?  -tjb@sun.com (03/05/2008)
	     */
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
    public String toString() {
	return getClass().getName() + "[" + identity + "]@" + sessionRefId;
    }

    /* -- I/O completion handlers -- */

    /** A completion handler for writing to a connection. */
    private abstract class WriteHandler
        implements CompletionHandler<Void, Void>
    {
	/** Writes the specified message. */
        abstract void write(ByteBuffer message);
    }

    /** A completion handler for writing that always fails. */
    private class ClosedWriteHandler extends WriteHandler {

	ClosedWriteHandler() { }

        @Override
        void write(ByteBuffer message) {
            throw new ClosedAsynchronousChannelException();
        }
        
        public void completed(IoFuture<Void, Void> result) {
            throw new AssertionError("should be unreachable");
        }    
    }

    /** A completion handler for writing to the session's channel. */
    private class ConnectedWriteHandler extends WriteHandler {

	/** An unbounded queue of messages waiting to be written. */
        private final LinkedList<ByteBuffer> pendingWrites =
            new LinkedList<ByteBuffer>();

	/** Whether a write is underway. */
        private boolean isWriting = false;

	/** Creates an instance of this class. */
        ConnectedWriteHandler() { }

	/**
	 * Adds the message to the queue, and starts processing the queue if
	 * needed.
	 */
        @Override
        void write(ByteBuffer message) {
            boolean first;
            synchronized (lock) {
                first = pendingWrites.isEmpty();
                pendingWrites.add(message);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
			   "write session:{0} message:{1} first:{2}",
                           ClientSessionHandler.this,
			   HexDumper.format(message, 0x50), first);
            }
            if (first) {
                processQueue();
            }
        }

	/** Start processing the first element of the queue, if present. */
        private void processQueue() {
            ByteBuffer message;
            synchronized (lock) {
                if (isWriting) {
                    return;
		}
                message = pendingWrites.peek();
                if (message != null) {
                    isWriting = true;
                }
            }
            if (logger.isLoggable(Level.FINEST)) {
                pendingWrites.size();
                logger.log(
		    Level.FINEST,
		    "processQueue session:{0} size:{1,number,#} head={2}",
		    ClientSessionHandler.this, pendingWrites.size(),
		    HexDumper.format(message, 0x50));
            }
            if (message == null) {
                return;
            }
            try {
                sessionConnection.write(message, null, this);
            } catch (RuntimeException e) {
                logger.logThrow(Level.SEVERE, e,
				"{0} processing message {1}",
				ClientSessionHandler.this,
				HexDumper.format(message, 0x50));
                throw e;
            }
        }

	/** Done writing the first request in the queue. */
        public void completed(IoFuture<Void, Void> result) {
	    ByteBuffer message;
            synchronized (lock) {
                message = pendingWrites.remove();
                isWriting = false;
            }
            if (logger.isLoggable(Level.FINEST)) {
		ByteBuffer resetMessage = message.duplicate();
		resetMessage.reset();
                logger.log(Level.FINEST,
			   "completed write session:{0} message:{1}",
			   ClientSessionHandler.this,
			   HexDumper.format(resetMessage, 0x50));
            }
            try {
                result.getNow();
                /* Keep writing */
                processQueue();
            } catch (ExecutionException e) {
                /*
		 * TODO: If we're expecting the session to close, don't
                 * complain.
		 */
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
				    "write session:{0} message:{1} throws",
				    ClientSessionHandler.this,
				    HexDumper.format(message, 0x50));
                }
                scheduleHandleDisconnect(false);
            }
        }
    }

    /** A completion handler for reading from a connection. */
    private abstract class ReadHandler
        implements CompletionHandler<ByteBuffer, Void>
    {
	/** Initiates the read request. */
        abstract void read();
    }

    /** A completion handler for reading that always fails. */
    private class ClosedReadHandler extends ReadHandler {

	ClosedReadHandler() { }

        @Override
        void read() {
            throw new ClosedAsynchronousChannelException();
        }

        public void completed(IoFuture<ByteBuffer, Void> result) {
            throw new AssertionError("should be unreachable");
        }
    }

    /** A completion handler for reading from the session's channel. */
    private class ConnectedReadHandler extends ReadHandler {

	/** Whether a read is underway. */
        private boolean isReading = false;

	/** Creates an instance of this class. */
        ConnectedReadHandler() { }

	/** Reads a message from the connection. */
        @Override
        void read() {
            synchronized (lock) {
                if (isReading)
                    throw new ReadPendingException();
                isReading = true;
            }
            sessionConnection.read(null, this);
        }

	/** Handles the completed read operation. */
        public void completed(IoFuture<ByteBuffer, Void> result) {
            synchronized (lock) {
                isReading = false;
            }
            try {
                ByteBuffer message = result.getNow();
                if (message == null) {
                    scheduleHandleDisconnect(false);
                    return;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "completed read session:{0} message:{1}",
                        ClientSessionHandler.this,
			HexDumper.format(message, 0x50));
                }

                byte[] payload = new byte[message.remaining()];
                message.get(payload);

                // Dispatch
                bytesReceived(payload);

            } catch (Exception e) {

                // TODO if we're expecting the channel to close,
                // don't complain.

                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
                        Level.FINE, e,
                        "Read completion exception {0}", sessionConnection);
                }
                scheduleHandleDisconnect(false);
            }
        }

	/** Processes the received message. */
        private void bytesReceived(byte[] buffer) {

	    MessageBuffer msg = new MessageBuffer(buffer);
	    byte opcode = msg.getByte();

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
 		    Level.FINEST,
		    "processing opcode 0x{0}",
		    Integer.toHexString(opcode));
	    }
	    
	    switch (opcode) {
		
	    case SimpleSgsProtocol.LOGIN_REQUEST: {

	        byte version = msg.getByte();
	        if (version != SimpleSgsProtocol.VERSION) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                    "got protocol version:{0}, " +
	                    "expected {1}", version, SimpleSgsProtocol.VERSION);
	            }
	            scheduleHandleDisconnect(false);
	            break;
	        }

		String name = msg.getString();
		String password = msg.getString();
		handleLoginRequest(name, password);

                // Resume reading immediately
		read();

		break;
	    }
		
	    case SimpleSgsProtocol.SESSION_MESSAGE:
		if (identity == null) {
		    logger.log(
		    	Level.WARNING,
			"session message received before login:{0}", this);
		    break;
		}
		final ByteBuffer clientMessage =
		    ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
		taskQueue.addTask(new AbstractKernelRunnable() {
		    public void run() {
			ClientSessionImpl sessionImpl =
			    ClientSessionImpl.getSession(
				dataService, sessionRefId);
			if (sessionImpl != null) {
			    if (isConnected()) {
				sessionImpl.getClientSessionListener(dataService).
				    receivedMessage(clientMessage.asReadOnlyBuffer());
			    }
			} else {
			    scheduleHandleDisconnect(false);
			}
		    }});

		// Wait until processing is complete before resuming reading
		enqueueReadResume();

		break;

	    case SimpleSgsProtocol.LOGOUT_REQUEST:
		scheduleHandleDisconnect(isConnected());

		// Resume reading immediately
                read();

		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"unknown opcode 0x{0}",
			Integer.toHexString(opcode));
		}
		scheduleHandleDisconnect(false);
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
		 * Handle this login request locally: Set the client
		 * session's identity, store the client session in the data
		 * store (which assigns it an ID--the ID of the reference
		 * to the client session object), inform the session
		 * service that this handler is available (by invoking
		 * "addHandler", and schedule a task to perform client
		 * login (call the AppListener.loggedIn method).
		 */
		taskQueue =
		    new NonDurableTaskQueue(
			sessionService.getTransactionProxy(),
			sessionService.nonDurableTaskScheduler,
			authenticatedIdentity);
		identity = authenticatedIdentity;
		CreateClientSessionTask createTask =
		    new CreateClientSessionTask();
		try {
		    sessionService.runTransactionalTask(createTask, identity);
		} catch (Exception e) {
		    logger.logThrow(
			Level.WARNING, e,
			"Storing ClientSession for identity:{0} throws", name);
		    sendLoginFailureAndDisconnect();
		    return;
		}
		sessionService.addHandler(
		    sessionRefId, ClientSessionHandler.this);
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
			try {
			    // FIXME: this is a hack to make sure that
			    // the client receives the login redirect
			    // message before disconnect.
			    Thread.sleep(100);
			} catch (InterruptedException e) {
			}
			handleDisconnect(false);
		    }});
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

    /**
     * Schedule a task to resume reading.  Use this method to delay reading
     * until a task resulting from an earlier read request has been completed.
     */
    void enqueueReadResume() {
        taskQueue.addTask(new AbstractKernelRunnable() {
            public void run() {
                logger.log(Level.FINER, "resuming reads session:{0}", this);
                if (isConnected()) {
                    readHandler.read();
                }
            }
	});
    }

    /* -- other private methods and classes -- */

    /**
     * Invokes the {@code setStatus} method on the node mapping service
     * with the given {@code inactiveIdentity} and {@code false} to mark
     * the identity as inactive.  This method is invoked when a login is
     * redirected and also when a this client session is disconnected.
     */
    private void deactivateIdentity(Identity inactiveIdentity) {
	try {
	    /*
	     * Set identity's status for this class to 'false'.
	     */
	    sessionService.nodeMapService.setStatus(
		ClientSessionHandler.class, inactiveIdentity, false);
	} catch (Exception e) {
	    logger.logThrow(
		Level.WARNING, e,
		"setting status for identity:{0} throws",
		inactiveIdentity.getName());
	}
    }
    
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
     * Constructs the ClientSession.
     */
    private class CreateClientSessionTask extends AbstractKernelRunnable {
	
	public void run() {
	    ClientSessionImpl sessionImpl =
		new ClientSessionImpl(sessionService, identity);
	    sessionRefId = sessionImpl.getId();
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
		(AppListener) dataService.getServiceBinding(
		    StandardProperties.APP_LISTENER);
	    logger.log(
		Level.FINEST,
		"invoking AppListener.loggedIn session:{0}", identity);

	    CompactId compactId = new CompactId(sessionRefId.toByteArray());
	    MessageBuffer ack =
		new MessageBuffer(1 + compactId.getExternalFormByteCount());
	    ack.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
		putBytes(compactId.getExternalForm());
		
	    ClientSessionListener returnedListener = null;
	    RuntimeException ex = null;

	    ClientSessionImpl sessionImpl =
		ClientSessionImpl.getSession(dataService, sessionRefId);
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

		sessionService.sendProtocolMessageFirst(
		    sessionImpl, ack.getBuffer(), Delivery.RELIABLE, false);
		
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
		} else if (! isRetryableException(ex)) {
		    logger.logThrow(
			Level.WARNING, ex,
			"Invoking loggedIn on AppListener:{0} with " +
			"session: {1} throws",
			appListener, ClientSessionHandler.this);
		} else {
		    throw ex;
		}
		sessionService.sendProtocolMessageFirst(
		    sessionImpl, loginFailureMessage, Delivery.RELIABLE, true);
		sessionImpl.disconnect();
	    }
	}
    }

    /**
     * Returns a byte array containing a LOGIN_FAILURE protocol message.
     */
    private static byte[] getLoginFailureMessage() {
        int stringSize = MessageBuffer.getSize(LOGIN_REFUSED_REASON);
        MessageBuffer ack = new MessageBuffer(1 + stringSize);
        ack.putByte(SimpleSgsProtocol.LOGIN_FAILURE).
            putString(LOGIN_REFUSED_REASON);
        return ack.getBuffer();
    }

    /**
     * Returns a byte array containing a LOGIN_REDIRECT protocol
     * message containing the given {@code hostname}.
     */
    private static byte[] getLoginRedirectMessage(String hostname) {
	int hostStringSize = MessageBuffer.getSize(hostname);
	MessageBuffer ack = new MessageBuffer(1 + hostStringSize);
        ack.putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
            putString(hostname);
        return ack.getBuffer();
    }	
}
