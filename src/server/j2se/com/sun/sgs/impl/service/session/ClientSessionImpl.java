package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.ServiceListener;
import com.sun.sgs.service.SgsClientSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Implements a client session.
 */
class ClientSessionImpl implements SgsClientSession {

    /** Connection state. */
    private static enum State {
	CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, DISCONNECTED };

    /** Random number generator for generating session ids. */
    private static final Random random = new Random(getSeed());

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(ClientSessionImpl.class.getName()));

    /** Message for indicating login/authentication failure. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";

    /** The client session service that created this client session. */
    private final ClientSessionServiceImpl sessionService;
    
    /** The IOHandle for sending messages to the client. */
    private IOHandle sessionHandle;

    /** The session id. */
    private final byte[] sessionId;

    /** The reconnection key. */
    private final byte[] reconnectionKey;

    /** The IOHandler for receiving messages from the client. */
    private final IOHandler handler;

    /** The authenticated name for this session. */
    private String name;

    /** The lock for accessing the connection state and sending messages. */
    private final Object lock = new Object();
    
    /** The connection state. */
    private State state = State.CONNECTING;

    /** The listener for this client session.*/
    private ClientSessionListener listener;

    private boolean disconnectHandled = false;

    private AtomicLong sequenceNumber = new AtomicLong(0);

    /**
     * Constructs an instance of this class with the specified handle.
     */
    ClientSessionImpl(
	ClientSessionServiceImpl sessionService,
	IOHandle handle)
    {
	this.sessionService = sessionService;
	this.sessionHandle = handle;
	this.handler = new Handler();
	this.sessionId = generateId();
	this.reconnectionKey = generateId();
    }

    /* -- Implement ClientSession -- */

    /** {@inheritDoc} */
    public String getName() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", name);
	}
	return name;
    }
    
    /** {@inheritDoc} */
    public byte[] getSessionId() {
	try {
	    if (!isConnected()) {
		throw new IllegalStateException("client session not connected");
	    }
			     
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "getSessionId returns {0}", sessionId);
	    }
	    return sessionId;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "getSessionId throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean isConnected() {

	State currentState = getCurrentState();

	boolean connected =
	    currentState == State.CONNECTING ||
	    currentState == State.CONNECTED ||
	    currentState == State.RECONNECTING;

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "isConnected returns {0}", connected);
	}
	return connected;
    }

    /** {@inheritDoc} */
    public void send(final byte[] message) {
	try {	
	    switch (getCurrentState()) {

	    case CONNECTING:
	    case CONNECTED:
	    case RECONNECTING:
		submitNonTransactionalTask(new KernelRunnable() {
	            public void run() throws IOException {
			MessageBuffer buf =
			    new MessageBuffer(5 + message.length);
			buf.putByte(SgsProtocol.VERSION).
			    putByte(SgsProtocol.APPLICATION_SERVICE).
			    putByte(SgsProtocol.MESSAGE_SEND).
			    putShort(message.length).
			    putBytes(message);
			sendProtocolMessage(buf);
		    }});
		break;
	    
	    default:
		throw new IllegalStateException("client session not connected");
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send message:{0} throws", message);
	    }
	    throw e;
	}
	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "send message:{0} returns", message);
	}
    }

    /** {@inheritDoc} */
    public void disconnect() {
	if (getCurrentState() != State.DISCONNECTED) {
	    submitNonTransactionalTask(new KernelRunnable() {
		public void run() {
		    handleDisconnect(false);
		}});
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "disconnect returns");
	}
    }

    /* -- Implement SgsClientSession -- */

    /** {@inheritDoc} */
    public long nextSequenceNumber() {
	return sequenceNumber.getAndIncrement();
    }
    
    public void sendMessage(byte[] message, Delivery delivery) {
	// TBI: ignore delivery for now...
	try {
	    sessionHandle.sendMessage(message);
	} catch (IOException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "sendMessage handle:{0} throws", sessionHandle);
	    }
	}
    }
    
    /* -- other methods -- */

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
     * Sends the protocol message in the specified buffer to this
     * session's client, logging any exception that occurs.
     */
    private void sendProtocolMessage(MessageBuffer buf) {
	// TBI: specify reliable delivery for now
	sendMessage(buf.getBuffer(), Delivery.RELIABLE);
    }

    /**
     * Handles a disconnect request (if not already handlede) by doing
     * the following:
     *
     * a) sending a disconnect acknowledgement (either LOGOUT_SUCCESS
     * if 'graceful' is true, or SESSION_DISCONNECT if 'graceful' is
     * false)
     *
     * b) closing this session's connection
     *
     * c) submitting a transactional task to call the 'disconnected'
     * callback on the listener for this session.
     *
     * @param graceful if the disconnection was graceful (i.e., due to
     * a logout request).
     */
    private void handleDisconnect(final boolean graceful) {
	synchronized (lock) {
	    if (disconnectHandled) {
		return;
	    }
	    disconnectHandled = true;
	    if (state != State.DISCONNECTED) {
		state = State.DISCONNECTING;
	    }
	}

	sessionService.disconnected(this);
	MessageBuffer disconnectMsg = new MessageBuffer(3);
	disconnectMsg.
	    putByte(SgsProtocol.VERSION).
	    putByte(SgsProtocol.APPLICATION_SERVICE).
	    putByte(graceful ?
		    SgsProtocol.LOGOUT_SUCCESS :
		    SgsProtocol.SESSION_DISCONNECT);

	sendProtocolMessage(disconnectMsg);
	try {
	    sessionHandle.close();
	} catch (IOException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "handleDisconnect (close) handle:{0} throws",
		    sessionHandle);
	    }
	}
	submitTransactionalTask(new KernelRunnable() {
	    public void run() throws IOException {
		listener.disconnected(graceful);
	    }});
    }
    
    /** Returns the IOHandler for this session. */
    IOHandler getHandler() {
	return handler;
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

    /** Returns an 8-byte random id. */
    private static byte[] generateId() {
	byte[] id = new byte[8];
	random.nextBytes(id);
	return id;
    }

    /* -- IOHandler implementation -- */

    /**
     * Handler for connection-related events for this session's
     * IOHandle.
     */
    class Handler implements IOHandler {

	/** {@inheritDoc} */
	public void connected(IOHandle handle) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "Handler.connected handle:{0}", handle);
	    }

	    synchronized (lock) {
		// check if handle is current
		if (handle != sessionHandle) {
		    return;
		}
		
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
	public void disconnected(IOHandle handle) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "Handler.disconnected handle:{0}", handle);
	    }

	    synchronized (lock) {
		if (handle != sessionHandle) {
		    return;
		}

		if (!disconnectHandled) {
		    submitNonTransactionalTask(new KernelRunnable() {
			public void run() {
			    handleDisconnect(false);
			}});
		}
		    
		state = State.DISCONNECTED;
	    }
	}

	/** {@inheritDoc} */
	public void exceptionThrown(Throwable exception, IOHandle handle) {

	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, exception,
		    "Handler.exceptionThrown handle:{0}", handle);
	    }
	}

	/** {@inheritDoc} */
	public void messageReceived(byte[] buffer, IOHandle handle) {
	    synchronized (lock) {
		if (handle != sessionHandle) {
		    return;
		}
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "Handler.messageReceived handle:{0}, buffer:{1}",
		    handle, buffer);
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
	    if (version != SgsProtocol.VERSION) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"Handler.messageReceived protocol version:{0}, " +
			"expected {1}", version, SgsProtocol.VERSION);
		}
		    // TBD: should the connection be disconnected?
		return;
	    }

	    /*
	     * Handle service id.
	     */
	    byte serviceId = msg.getByte();

	    if (serviceId != SgsProtocol.APPLICATION_SERVICE) {
		ServiceListener serviceListener =
		    sessionService.getServiceListener(serviceId);
		if (serviceListener != null) {
		    serviceListener.receivedMessage(
			ClientSessionImpl.this, buffer);
		} else {
		    if (logger.isLoggable(Level.SEVERE)) {
		    	logger.log(
			    Level.SEVERE,
			    "Handler.messageReceived unknown service ID:{0}",
			    serviceId);
		    }
		    return;
		}
	    }

	    /*
	     * Handle op code.
	     */
	    byte opcode = msg.getByte();
	    
	    switch (opcode) {
		
	    case SgsProtocol.LOGIN_REQUEST:
		name = msg.getString();
		String password = msg.getString();

		// TBD: need to athenticate here...
		submitTransactionalTask(new LoginTask());
		break;
		
	    case SgsProtocol.RECONNECTION_REQUEST:
		break;

	    case SgsProtocol.MESSAGE_SEND:
		int size = msg.getShort();
		final byte[] clientMessage = msg.getBytes(size);
		submitTransactionalTask(new KernelRunnable() {
		    public void run() {
			if (isConnected()) {
			    listener.receivedMessage(clientMessage);
			}
		    }});
		break;

	    case SgsProtocol.LOGOUT_REQUEST:
		if (isConnected()) {
		    submitNonTransactionalTask(new KernelRunnable() {
			public void run() {
			    handleDisconnect(true);
			}});
		} else {
		    MessageBuffer ack = new MessageBuffer(3);
		    ack.putByte(SgsProtocol.VERSION).
			putByte(SgsProtocol.APPLICATION_SERVICE).
			putByte(SgsProtocol.SESSION_DISCONNECT);
		    sendProtocolMessage(ack);
		}
		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"Handler.messageReceived unknown operation code:{0}",
			opcode);
		}
		// TBD: should the connection be disconnected, or
		// should this send an "unknown opcode" message to the
		// client?

		MessageBuffer ack = new MessageBuffer(4);
		ack.putByte(SgsProtocol.VERSION).
		    putByte(SgsProtocol.APPLICATION_SERVICE).
		    putByte(SgsProtocol.OPCODE_UNKNOWN).
		    putByte(opcode);
		sendProtocolMessage(ack);
		break;
	    }
	}
    }

    /**
     * Submits a non-durable, transactional task.
     */
    private void submitTransactionalTask(KernelRunnable task) {
	// TBD...
	/*
	sessionService.taskScheduler.scheduleTask(
	    new TransactionRunner(task),
	    new TaskOwnerImpl(name, sessionService.kernelAppContext));
	*/
	// Do this for now...
	try {
	    task.run();
	} catch (Exception e) {
	    throw (RuntimeException) new RuntimeException().initCause(e);
	}
    }

    /**
     * Submits a non-durable, non-transactional task.
     */
    private void submitNonTransactionalTask(KernelRunnable task) {
	// TBD...
	// sessionService.taskService.scheduleNonDurableTask(task);
	// Do this for now...
	try {
	    task.run();
	} catch (Exception e) {
	    throw (RuntimeException) new RuntimeException().initCause(e);
	}
    }

    /**
     * This is a transactional task to notify the application's
     * AppListener that this session has logged in.
     */
    private class LoginTask implements KernelRunnable {

	/**
	 * Invokes the AppListener's 'loggedIn' callback which returns
	 * a client session listener, and then submits a
	 * non-transactional task to send an acknowledgment to the
	 * client.
	 */
	public void run() {
	    AppListener appListener =
		sessionService.dataService.getServiceBinding(
		    "com.sun.sgs.app.AppListener", AppListener.class);
	    listener = appListener.loggedIn(ClientSessionImpl.this);
	    submitNonTransactionalTask(new LoginAckTask());
	}
    }

    /**
     * This is a non-transactional task to handle acknowledging a
     * login request after the application's AppListener has been
     * notified that this session has been logged in.
     */
    private class LoginAckTask implements KernelRunnable {

	/**
	 * If the listener is null, sends a LOGIN_FAILURE protocol
	 * message to the client; otherwise sends a LOGIN_SUCCESS
	 * protocol message with the sessionId and reconnectionKey.
	 */
	public void run() {
	    if (listener == null) {
		int stringSize = MessageBuffer.getSize(LOGIN_REFUSED_REASON);
		MessageBuffer ack =
		    new MessageBuffer(3 + stringSize);
		ack.putByte(SgsProtocol.VERSION).
		    putByte(SgsProtocol.APPLICATION_SERVICE).
		    putByte(SgsProtocol.LOGIN_FAILURE).
		    putString(LOGIN_REFUSED_REASON);
		sendProtocolMessage(ack);

		// Disconnect session
		handleDisconnect(false);
		
	    } else {
		MessageBuffer ack =
		    new MessageBuffer(
			7 + sessionId.length + reconnectionKey.length);
		ack.putByte(SgsProtocol.VERSION).
		    putByte(SgsProtocol.APPLICATION_SERVICE).
		    putByte(SgsProtocol.LOGIN_SUCCESS).
		    putShort(sessionId.length). putBytes(sessionId).
		    putShort(reconnectionKey.length).putBytes(reconnectionKey);
		sendProtocolMessage(ack);
	    }
	}
    }
}
