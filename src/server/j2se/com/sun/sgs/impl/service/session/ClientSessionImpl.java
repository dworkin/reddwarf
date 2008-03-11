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

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import static com.sun.sgs.impl.util.AbstractService.isRetryableException;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a client session.
 *
 * <p>TODO: service bindings should be versioned, and old bindings should be
 * converted to the new scheme (or removed if applicable).
 */
public class ClientSessionImpl
    implements ClientSession, NodeAssignment, Serializable
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The logger name and prefix for the various session keys. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.session.";

    /** The session component in a session key. */
    private static final String SESSION_COMPONENT = "impl.";

    /** The listener component in a session's listener key. */
    private static final String LISTENER_COMPONENT = "listener.";

    /** The event queue component in a session's event queue key. */
    private static final String QUEUE_COMPONENT = "queue.";

    /** The node component in a session's node key. */
    private static final String NODE_COMPONENT = "node.";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + "impl"));

    /** The local ClientSessionService. */
    private transient ClientSessionServiceImpl sessionService;

    /** The session ID. */
    private transient BigInteger id;

    /** The session ID bytes.
     * TODO: this should be a transient field.
     */
    private final byte[] idBytes;
    
    /** The wrapped client session instance. */
    private final ManagedReference<ClientSessionWrapper> wrappedSessionRef;

    /** The identity for this session. */
    private final Identity identity;

    /** The node ID for this session (final because sessions can't move yet). */
    private final long nodeId;

    /** Indicates whether this session is connected. */
    private volatile boolean connected = true;

    /*
     * TBD: Should a managed reference to the ClientSessionListener be
     * cached in the ClientSessionImpl for efficiency?
     */

    /**
     * Constructs an instance of this class with the specified {@code
     * sessionService}, {@code identity}, and the local node ID, and stores
     * this instance with the following bindings:<p>
     *
     * <pre>
     * com.sun.sgs.impl.service.session.impl.&lt;idBytes&gt;
     * com.sun.sgs.impl.service.session.node.&lt;nodeId&gt;.impl.&lt;idBytes&gt;
     *</pre>
     * This method should only be called within a transaction.
     *
     * @param	sessionService a client session service
     * @param	identity the session's identity
     * @throws TransactionException if there is a problem with the
     * 		current transaction
     */
    ClientSessionImpl(ClientSessionServiceImpl sessionService,
		      Identity identity)
    {
	if (sessionService == null) {
	    throw new NullPointerException("null sessionService");
	}
	if (identity == null) {
	    throw new IllegalStateException("session's identity is not set");
	}
	this.sessionService = sessionService;
	this.identity = identity;
	this.nodeId = sessionService.getLocalNodeId();
	DataService dataService = sessionService.getDataService();
	ManagedReference<ClientSessionImpl> sessionRef =
	    dataService.createReference(this);
	id = sessionRef.getId();
	this.wrappedSessionRef =
	    dataService.createReference(new ClientSessionWrapper(sessionRef));
	idBytes = id.toByteArray();
	dataService.setServiceBinding(getSessionKey(), this);
	dataService.setServiceBinding(getSessionNodeKey(), this);
	dataService.setServiceBinding(getEventQueueKey(), new EventQueue(this));
	logger.log(Level.FINEST, "Stored session, identity:{0} id:{1}",
		   identity, id);
    }

    /* -- Implement ClientSession -- */

    /** {@inheritDoc} */
    public String getName() {
	if (identity == null) {
	    throw new IllegalStateException("session identity not initialized");
	}
        String name = identity.getName();
	return name;
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	return connected;
    }

    /** {@inheritDoc} */
    public ClientSession send(ByteBuffer message) {
	try {
            if (message.remaining() > SimpleSgsProtocol.MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.remaining() + " > " +
                        SimpleSgsProtocol.MAX_PAYLOAD_LENGTH);
            } else if (!isConnected()) {
		throw new IllegalStateException("client session not connected");
	    }
            ByteBuffer buf = ByteBuffer.wrap(new byte[1 + message.remaining()]);
            buf.put(SimpleSgsProtocol.SESSION_MESSAGE)
               .put(message)
               .flip();
	    addEvent(new SendEvent(buf.array()));

	    return this;

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
	        logger.logThrow(Level.FINEST, e,
	                        "send message:{0} throws",
	                        HexDumper.format(message, 0x50));
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void disconnect() {
	if (isConnected()) {
	    addEvent(new DisconnectEvent());
	    sessionService.getDataService().markForUpdate(this);
	    connected = false;
	}
	logger.log(Level.FINEST, "disconnect returns");
    }

    /* -- Implement NodeAssignment -- */

    /** {@inheritDoc} */
    public long getNodeId() {
	return nodeId;
    }
    
    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj != null && obj.getClass() == this.getClass()) {
	    ClientSessionImpl session = (ClientSessionImpl) obj;
	    return
		equalsInclNull(identity, session.identity) &&
		equalsInclNull(id, session.id);
	}
	return false;
    }

    /**
     * Returns {@code true} if the given objects are either both
     * null, or both non-null and invoking {@code equals} on the first
     * object passing the second object returns {@code true}.
     */
    private static boolean equalsInclNull(Object obj1, Object obj2) {
	if (obj1 == null) {
	    return obj2 == null;
	} else if (obj2 == null) {
	    return false;
	} else {
	    return obj1.equals(obj2);
	}
    }
    
    /** {@inheritDoc} */
    public int hashCode() {
	return id.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + getName() + "]@[id:" +
	    HexDumper.toHexString(idBytes) + ",node:" + nodeId + "]";
    }
    
    /* -- Serialization methods -- */

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	sessionService = ClientSessionServiceImpl.getInstance();
	this.id = new BigInteger(1, idBytes);
    }

    /* -- Other methods -- */

    /**
     * Returns the ID of this instance as a {@code BigInteger}.
     *
     * @return	the ID of this instance as a {@code BigInteger}
     */
    BigInteger getId() {
        return id;
    }
    
    /**
     * Returns the {@code ClientSession} instance for the given {@code
     * id}, retrieved from the specified {@code dataService}, or
     * {@code null} if the client session isn't bound in the data
     * service.  This method should only be called within a
     * transaction.
     *
     * @param	dataService a data service
     * @param	id a session ID
     * @return	the session for the given session {@code id},
     *		or {@code null}
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static ClientSessionImpl getSession(
	DataService dataService, BigInteger id)
    {
	ClientSessionImpl sessionImpl = null;
	try {
	    ManagedReference<?> sessionRef =
		dataService.createReferenceForId(id);
	    sessionImpl = (ClientSessionImpl) sessionRef.get();
	} catch (ObjectNotFoundException e)  {
	}
	return sessionImpl;
    }

    /**
     * Returns the wrapped client session for this instance.
     */
    ClientSessionWrapper getWrappedClientSession() {
	return wrappedSessionRef.get();
    }

    /**
     * Invokes the {@code disconnected} callback on this session's {@code
     * ClientSessionListener} (if present and {@code notify} is
     * {@code true}), removes the listener and its binding (if present),
     * and then removes this session and its bindings from the specified
     * {@code dataService}.  If the bindings have already been removed from
     * the {@code dataService} this method takes no action.  This method
     * should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	graceful {@code true} if disconnection is graceful,
     *		and {@code false} otherwise
     * @param	notify {@code true} if the {@code disconnected}
     *		callback should be invoked
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void notifyListenerAndRemoveSession(
	final DataService dataService, final boolean graceful, boolean notify)
    {
	String sessionKey = getSessionKey();
	String sessionNodeKey = getSessionNodeKey();
	String listenerKey = getListenerKey();
	String eventQueueKey = getEventQueueKey();

	/*
	 * Get ClientSessionListener, and remove its binding and
	 * wrapper if applicable.  The listener may not be bound
	 * in the data service if the AppListener.loggedIn callback
	 * either threw a non-retryable exception or returned a
	 * null listener.
	 */
	ClientSessionListener listener = null;
	try {
	    ManagedObject obj = dataService.getServiceBinding(listenerKey);
	    dataService.removeServiceBinding(listenerKey);
 	    if (obj instanceof ListenerWrapper) {
		dataService.removeObject(obj);
		listener = ((ListenerWrapper) obj).get();
	    } else {
		listener = (ClientSessionListener) obj;
	    }
	    
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.FINE, e,
		"removing ClientSessionListener for session:{0} throws",
		this);
	}

	/*
	 * Remove event queue and associated binding.
	 */
	try {
	    ManagedObject eventQueue =
		dataService.getServiceBinding(eventQueueKey);
	    dataService.removeServiceBinding(eventQueueKey);
	    dataService.removeObject(eventQueue);
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.FINE, e,
		"removing EventQueue for session:{0} throws",
		this);
	}

	/*
	 * Invoke listener's 'disconnected' callback if 'notify'
	 * is true and a listener exists for this client session.  If the
	 * 'disconnected' callback throws a non-retryable exception,
	 * schedule a task to remove this session and its associated
	 * bindings without invoking the listener, and rethrow the
	 * exception so that the currently executing transaction aborts.
	 */
	if (notify && listener != null) {
	    try {
		listener.disconnected(graceful);
	    } catch (RuntimeException e) {
		if (! isRetryableException(e)) {
		    logger.logThrow(
			Level.WARNING, e,
			"invoking disconnected callback on listener:{0} " +
			" for session:{1} throws",
			listener, this);
		    sessionService.scheduleTask(
			new AbstractKernelRunnable() {
			    public void run() {
				ClientSessionImpl sessionImpl = 
				    ClientSessionImpl.getSession(dataService, id);
				sessionImpl.notifyListenerAndRemoveSession(
				    dataService, graceful, false);
			    }},
			identity);
		}
		throw e;
	    }
	}

	/*
	 * Remove this session's state and bindings.
	 */
	try {
	    dataService.removeServiceBinding(sessionKey);
	    dataService.removeServiceBinding(sessionNodeKey);
	    dataService.removeObject(this);
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.WARNING, e, "session binding already removed:{0}",
		sessionKey);
	}
    }

    /**
     * Returns the {@code ClientSessionServer} for this instance.
     */
    private ClientSessionServer getClientSessionServer() {
	return sessionService.getClientSessionServer(nodeId);
    }
    
    /**
     * Returns the key to access this instance from the data service.
     *
     * @return	a key for accessing this {@code ClientSessionImpl} instance
     */
    private String getSessionKey() {
	return
	    PKG_NAME + SESSION_COMPONENT + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the key to access from the data service the {@code
     * ClientSessionListener} instance for this instance. If the {@code
     * ClientSessionListener} does not implement {@code ManagedObject},
     * then the key will be bound to a {@code ListenerWrapper}.
     *
     * @return	a key for accessing the {@code ClientSessionListener} instance
     */
    private String getListenerKey() {
	return
	    PKG_NAME + LISTENER_COMPONENT + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the key to access the event queue of the session with the
     * specified {@code sessionId}.
     */
    private static String getEventQueueKey(byte[] sessionId) {
	return PKG_NAME + QUEUE_COMPONENT + HexDumper.toHexString(sessionId);
    }
	
    /**
     * Returns the key to access this session's event queue.
     */
    private String getEventQueueKey() {
	return getEventQueueKey(idBytes);
    }

    /**
     * Returns the key to access this instance from the data service (by
     * {@code nodeId} and session {@code idBytes}).
     *
     * @return	a key for accessing the {@code ClientSessionImpl} instance
     */
    private String getSessionNodeKey() {
	return getNodePrefix(nodeId) + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the prefix to access from the data service {@code
     * ClientSessionImpl} instances with the the specified {@code nodeId}.
     */
    static String getNodePrefix(long nodeId) {
	return PKG_NAME + NODE_COMPONENT + nodeId + ".";
    }

    /**
     * Stores the specified client session listener in the specified
     * {@code dataService} with following binding:
     * <pre>
     * com.sun.sgs.impl.service.session.listener.&lt;idBytes&gt;
     * </pre>
     * This method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	listener a client session listener
     * @throws	TransactionException if there is a problem with the
     * 		current transaction
     */
    void putClientSessionListener(
	DataService dataService, ClientSessionListener listener)
    {
	ManagedObject managedObject =
	    (listener instanceof ManagedObject) ?
	    (ManagedObject) listener :
	    new ListenerWrapper(listener);
	String listenerKey = getListenerKey();
	dataService.setServiceBinding(listenerKey, managedObject);
    }

    /**
     * Returns the client session listener, obtained from the
     * specified {@code dataService}, for this session.  This method
     * should only be called within a transaction.
     *
     * @param	dataService a data service
     * @return	the client session listener for this session
     * @throws	TransactionException if there is a problem with the
     * 		current transaction
     */
    ClientSessionListener getClientSessionListener(DataService dataService) {
	String listenerKey = getListenerKey();
	ManagedObject obj = dataService.getServiceBinding(listenerKey);
	return
	    (obj instanceof ListenerWrapper) ?
	    ((ListenerWrapper) obj).get() :
	    (ClientSessionListener) obj;
    }

    /**
     * A {@code ManagedObject} wrapper for a {@code ClientSessionListener}.
     */
    private static class ListenerWrapper
	implements ManagedObject, Serializable
    {
	private final static long serialVersionUID = 1L;
	
	private ClientSessionListener listener;

	ListenerWrapper(ClientSessionListener listener) {
	    assert listener != null && listener instanceof Serializable;
	    this.listener = listener;
	}

	ClientSessionListener get() {
	    return listener;
	}
    }

    /**
     * Returns the event queue for the client session with the specified
     * {@code sessionId}, or null if the event queue is not bound in the
     * data service.
     */
    private static EventQueue getEventQueue(byte[] sessionId) {
	DataService dataService = ClientSessionServiceImpl.getDataService();
	String eventQueueKey = getEventQueueKey(sessionId);
	try {
	    return (EventQueue) dataService.getServiceBinding(eventQueueKey);
	} catch (NameNotBoundException e) {
	    return null;
	}
    }
	
    /**
     * Returns this client session's event queue, or null if the event
     * queue is not bound in the data service.
     */
    private EventQueue getEventQueue() {
	return getEventQueue(idBytes);
    }

    /**
     * Adds the specified session {@code event} to this session's event
     * queue and notifies the client session service on the session's node
     * that there is an event to service.
     */
    private void addEvent(SessionEvent event) {

	EventQueue eventQueue = getEventQueue();

	if (eventQueue == null) {
	    throw new IllegalStateException(
		"event queue removed; session is disconnected");
	}
	    
	if (! eventQueue.offer(event)) {
	    throw new ResourceUnavailableException(
	   	"not enough resources to add client session event");
	}
	
	/*
	 * If this session is connected to the local node, service events
	 * locally; otherwise schedule a task to send a request to this
	 * session's client session server to service this session's event
	 * queue. 
	 */
	if (nodeId == sessionService.getLocalNodeId()) {
	    eventQueue.serviceEvent();
	    
	} else {

	    final ClientSessionServer sessionServer = getClientSessionServer();
	    if (sessionServer == null) {
		/*
		 * If the ClientSessionServer for this session has been
		 * removed, then this session's node has failed and the
		 * session has been disconnected.  The event queue will be
		 * cleaned up eventually, so there is no need to flag an
		 * error here.
		 */
		return;
	    }
	    sessionService.scheduleNonTransactionalTask(
	        new AbstractKernelRunnable() {
		    public void run() {
			try {
			    sessionServer.serviceEventQueue(idBytes);
			} catch (IOException e) {
			    /*
			     * It is likely that the session's node failed.
			     */
			    if (logger.isLoggable(Level.FINEST)) {
				logger.logThrow(
				    Level.FINEST, e,
				    "serviceEventQueue session:{0} node:{1} " +
				    "throws", HexDumper.toHexString(idBytes),
				    nodeId);
			    }
			}
		    }}, identity);
	}
    }

    /**
     * Services the event queue for the session with the specified {@code
     * sessionId}.
     */
    static void serviceEventQueue(byte[] sessionId) {
	EventQueue eventQueue = getEventQueue(sessionId);
	if (eventQueue != null) {
	    eventQueue.serviceEvent();
	}
    }
    
    /**
     * Represents an event for a client session.
     */
    private static abstract class SessionEvent
	implements ManagedObject, Serializable
    {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/**
	 * Services this event, taken from the head of the given {@code
	 * eventQueue}.
	 */
	public abstract void serviceEvent(EventQueue eventQueue);

    }

    private static class SendEvent extends SessionEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	private final byte[] message;
	
	/**
	 * Constructs a send event with the given {@code message}.
	 */
	SendEvent(byte[] message) {
	    this.message = message;
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {
	    ClientSessionImpl sessionImpl = eventQueue.getClientSession();
	    sessionImpl.sessionService.sendProtocolMessage(
		sessionImpl, ByteBuffer.wrap(message), Delivery.RELIABLE);
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName();
	}
    }

    private static class DisconnectEvent extends SessionEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/**
	 * Constructs a disconnect event.
	 */
	DisconnectEvent() {}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {
	    ClientSessionImpl sessionImpl = eventQueue.getClientSession();
	    sessionImpl.sessionService.disconnect(sessionImpl);
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName();
	}
    }
    
    /**
     * The session's event queue.
     */
    private static class EventQueue implements ManagedObject, Serializable {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/** The managed reference to the queue's session. */
	private final ManagedReference<ClientSessionImpl> sessionRef;
	/** The managed reference to the managed queue. */
	private final ManagedReference<ManagedQueue<SessionEvent>> queueRef;

	/**
	 * Constructs an event queue for the specified {@code sessionImpl}.
	 */
	EventQueue(ClientSessionImpl sessionImpl) {
	    DataService dataService = ClientSessionServiceImpl.getDataService();
	    sessionRef = dataService.createReference(sessionImpl);
	    queueRef = dataService.createReference(
		new ManagedQueue<SessionEvent>());
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.
	 */
	boolean offer(SessionEvent event) {
	    return getQueue().offer(event);
	}

	/**
	 * Returns the client session for this queue.
	 */
	ClientSessionImpl getClientSession() {
	    return sessionRef.get();
	}

	/**
	 * Returns the client session ID for this queue.
	 */
	BigInteger getSessionRefId() {
	    return sessionRef.getId();
	}
	
	/**
	 * Returns the managed queue object.
	 */
	ManagedQueue<SessionEvent> getQueue() {
	    return queueRef.get();
	}

	/**
	 * Throws a retryable exception if the event queue is not in a
	 * state to process the next event.
	 */
	void checkState() {
	    // TBD: is there any state to check here?
	}

	/**
	 * Processes (at least) the first event in the queue.
	 */
	void serviceEvent() {
	    checkState();
	    
	    ClientSessionServiceImpl sessionService =
		ClientSessionServiceImpl.getInstance();
	    ManagedQueue<SessionEvent> eventQueue = getQueue();
	    
	    for (int i = 0; i < sessionService.eventsPerTxn; i++) {
		SessionEvent event = eventQueue.poll();
		if (event == null) {
		    // no more events
		    break;
		}

		logger.log(Level.FINEST, "processing event:{0}", event);
		event.serviceEvent(this);
	    }
	}
    }
}
