/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import com.sun.sgs.app.DeliveryNotSupportedException;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.IoRunnable;
import static com.sun.sgs.impl.util.AbstractService.isRetryableException;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a client session.
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
     * TBD: this should be a transient field.
     */
    private final byte[] idBytes;

    /** The wrapped client session instance. */
    private final ManagedReference<ClientSessionWrapper> wrappedSessionRef;

    /** The identity for this session. */
    private final Identity identity;

    /** The set of delivery requirements for this session. */
    private final Set<Delivery> deliveries;

    /** The node ID for this session (final because sessions can't move yet). */
    private final long nodeId;

    /** Indicates whether this session is connected. */
    private volatile boolean connected = true;

    /** Maximum message length for session messages. */
    private final int maxMessageLength;
    
    /** The capacity of the write buffer, in bytes. */
    private final int writeBufferCapacity;

    /*
     * TBD: Should a managed reference to the ClientSessionListener be
     * cached in the ClientSessionImpl for efficiency?
     */

    /**
     * Constructs an instance of this class with the specified {@code
     * sessionService}, {@code identity}, and supported {@code deliveries},
     * and stores this instance with the following bindings:<p>
     *
     * <pre>
     * com.sun.sgs.impl.service.session.impl.&lt;idBytes&gt;
     * com.sun.sgs.impl.service.session.node.&lt;nodeId&gt;.impl.&lt;idBytes&gt;
     *</pre>
     * This method should only be called within a transaction.
     *
     * @param	sessionService a client session service
     * @param	identity the session's identity
     * @param	deliveries the session's supported delivery requirements
     * @param	maxMessageLength the maximum session message length
     * @throws	TransactionException if there is a problem with the
     * 		current transaction
     */
    ClientSessionImpl(ClientSessionServiceImpl sessionService,
		      Identity identity, Set<Delivery> deliveries,
                      int maxMessageLength)
    {
	if (sessionService == null) {
	    throw new NullPointerException("null sessionService");
	} else if (identity == null) {
	    throw new NullPointerException("null identity");
	} else if (deliveries == null) {
	    throw new NullPointerException("null deliveries");
	}
	this.sessionService = sessionService;
	this.identity = identity;
	this.deliveries = deliveries;
	this.nodeId = sessionService.getLocalNodeId();
        this.maxMessageLength = maxMessageLength;
	writeBufferCapacity = sessionService.getWriteBufferSize();
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
	if (!isConnected()) {
	    throw new IllegalStateException("client session is not connected");
	}
        String name = identity.getName();
	return name;
    }

    /** {@inheritDoc} */
    public Set<Delivery> supportedDeliveries() {
	return deliveries;
    }
    
    /** {@inheritDoc} */
    public int getMaxMessageLength() {
        return maxMessageLength;
    }
    
    /** {@inheritDoc} */
    public boolean isConnected() {
	return connected;
    }

    /** {@inheritDoc}
     *
     * Enqueues a send event to this client session's event queue for servicing.
     */
    public ClientSession send(ByteBuffer message) {
	return send(message, Delivery.RELIABLE);
    }

    /** {@inheritDoc}
     *
     * Enqueues a send event to this client session's event queue for servicing.
     */
    public ClientSession send(ByteBuffer message, final Delivery delivery) {
	try {
            if (!isConnected()) {
		throw new IllegalStateException("client session not connected");
            } else if (message == null) {
		throw new NullPointerException("null message");
	    } else if (message.remaining() > maxMessageLength) {
                throw new IllegalArgumentException(
                    "message too long: " + message.remaining() + " > " +
                    maxMessageLength);
            } else {
		checkDelivery(delivery);
	    }
            
            /*
             * TBD: Possible optimization: if we have passed our own special
             * buffer to the app, we can detect that here and possibly avoid a
             * copy.  Our special buffer could be one we passed to the
             * receivedMessage callback, or we could add a special API to
             * pre-allocate buffers. -JM
             */
	    final byte[] msgBytes = new byte[message.remaining()];
	    message.asReadOnlyBuffer().get(msgBytes);
	    if (delivery.equals(Delivery.UNRELIABLE)) {
		// Forward unreliable message directly to client session's
		// server node.
		final ClientSessionServer server =
		    sessionService.getClientSessionServer(nodeId);
		sessionService.taskService.scheduleNonDurableTask(
		    new AbstractKernelRunnable("SendUnreliableMessage") {
		        public void run() {
			    try {
				server.send(idBytes, msgBytes, (byte)
					    delivery.ordinal());
			    } catch (IOException e) {
				if (logger.isLoggable(Level.FINE)) {
				    logger.logThrow(
					Level.FINE, e,
					"send message:{0} throws",
					HexDumper.format(msgBytes, 0x50));
				}
			    }
			}
		    }, false);
		
	    } else {
		// Enqueue reliable message for ordered delivery by the
		// client session's server node.
		addEvent(new SendEvent(msgBytes, delivery));
	    }

	    return getWrappedClientSession();

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
	        logger.logThrow(Level.FINEST, e,
	                        "send message:{0} throws",
	                        HexDumper.format(message, 0x50));
	    }
	    throw e;
	}
	
    }

    /**
     * Throws {@link DeliveryNotSupportedException} if the specified
     * {@code delivery} guarantee is not supported by any of this session's
     * delivery guarantees.
     *
     * @param	delivery a delivery guarantee
     * @throws	DeliveryNotSupportedException if the specified {@code
     *		delivery} guarantee is not supported by any of this
     *		session's delivery guarantees
     */
    private void checkDelivery(Delivery delivery) {
	if (delivery == null) {
	    throw new NullPointerException("null delivery");
	}
	if (deliveries.contains(delivery)) {
	    return;
	}
	
	for (Delivery d : deliveries) {
	    if (d.supportsDelivery(delivery)) {
		return;
	    }
	}
	throw new DeliveryNotSupportedException(
	    "client session:" + this +
	    " does not support the delivery guarantee",
	    delivery);
    }

    /**
     * If the session is connected, enqueues a disconnect event to this
     * client session's event queue, and marks this session as disconnected.
     */
    void disconnect() {
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
    @Override
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
    @Override
    public int hashCode() {
	return id.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	return getClass().getName() + "[" + identity.getName() + "]@[id:0x" +
	    id.toString(16) + ",node:" + nodeId + "]";
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
     * @return the wrapped client session
     */
    public ClientSessionWrapper getWrappedClientSession() {
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

	// Mark this session as disconnected.
	dataService.markForUpdate(this);
	connected = false;

	/*
	 * Get ClientSessionListener, and remove its binding and
	 * wrapper if applicable.  The listener may not be bound
	 * in the data service if: the AppListener.loggedIn callback
	 * either threw a non-retryable exception or returned a
	 * null listener, or the application removed the
	 * ClientSessionListener object from the data service.
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
		if (!isRetryableException(e)) {
		    logger.logThrow(
			Level.WARNING, e,
			"invoking disconnected callback on listener:{0} " +
			"for session:{1} throws",
			listener, this);
		    sessionService.scheduleTask(
			new AbstractKernelRunnable(
			    "NotifyListenerAndRemoveSession")
			{
			    public void run() {
				ClientSessionImpl sessionImpl =
				    ClientSessionImpl.getSession(
					dataService, id);
				sessionImpl.notifyListenerAndRemoveSession(
				    dataService, graceful, false);
			    }
			}, identity);
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

	/*
	 * Remove this session's wrapper object, if it still exists.
	 */
	try {
	    dataService.removeObject(wrappedSessionRef.get());
	} catch (ObjectNotFoundException e) {
	    // already removed
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
    private static String getNodePrefix(long nodeId) {
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
	private static final long serialVersionUID = 1L;

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

	boolean isLocalSession = nodeId == sessionService.getLocalNodeId();

	/*
	 * If this session is connected to the local node and the event
	 * queue is empty, service the event immediately; otherwise, add
	 * the event to the event queue.  If the session is connected
	 * locally, service the head of the event queue; otherwise schedule
	 * a task to send a request to this session's client session server
	 * to service this session's event queue.
	 */
	if (isLocalSession && eventQueue.isEmpty()) {
	    event.serviceEvent(eventQueue);

	} else if (!eventQueue.offer(event)) {
	    throw new ResourceUnavailableException(
	   	"not enough resources to add client session event");

	} else if (isLocalSession) {
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
	        new AbstractKernelRunnable("ServiceEventQueue") {
		    public void run() {
			sessionService.runIoTask(
			    new IoRunnable() {
				public void run() throws IOException {
				    sessionServer.serviceEventQueue(idBytes);
				} },
			    nodeId);
		    }
		}, identity);
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
     * Returns the write buffer capacity for this session.
     *
     * @return the write buffer capacity
     */
    int getWriteBufferCapacity() {
        return writeBufferCapacity;
    }

    /**
     * Represents an event for a client session.
     */
    private abstract static class SessionEvent
	implements ManagedObject, Serializable
    {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Services this event, taken from the head of the given {@code
	 * eventQueue}.
	 */
	abstract void serviceEvent(EventQueue eventQueue);

	/**
	 * Returns the cost of this event, which the {@code EventQueue}
	 * may use to reject events when the total cost is too large.
	 * The default implementation returns a cost of zero.
	 *
	 * @return the cost of this event
	 */
	int getCost() {
	    return 0;
	}
    }

    static class SendEvent extends SessionEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	final byte[] message;
	final Delivery delivery;

	/**
	 * Constructs a send event with the given {@code message}.
	 */
	SendEvent(byte[] message, Delivery delivery) {
	    this.message = message;
	    this.delivery = delivery;
	}

	/** {@inheritDoc} */
	void serviceEvent(EventQueue eventQueue) {
	    ClientSessionImpl sessionImpl = eventQueue.getClientSession();
	    sessionImpl.sessionService.
		addSessionMessage(sessionImpl, this);
	}

	/** Use the message length as the cost for sending messages. */
	@Override
	int getCost() {
	    return message.length;
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName();
	}
    }

    private static class DisconnectEvent extends SessionEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** Constructs a disconnect event. */
	DisconnectEvent() { }

	/** {@inheritDoc} */
	void serviceEvent(EventQueue eventQueue) {
	    ClientSessionImpl sessionImpl = eventQueue.getClientSession();
	    sessionImpl.sessionService.addDisconnectRequest(sessionImpl);
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
    private static class EventQueue
	implements ManagedObjectRemoval, Serializable
    {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The managed reference to the queue's session. */
	private final ManagedReference<ClientSessionImpl> sessionRef;
	/** The managed reference to the managed queue. */
	private final ManagedReference<ManagedQueue<SessionEvent>> queueRef;

	/** The number of bytes of the write buffer currently available. */
	private int writeBufferAvailable;

	/**
	 * Constructs an event queue for the specified {@code sessionImpl}.
	 */
	EventQueue(ClientSessionImpl sessionImpl) {
	    DataService dataService = ClientSessionServiceImpl.getDataService();
	    sessionRef = dataService.createReference(sessionImpl);
	    queueRef = dataService.createReference(
		new ManagedQueue<SessionEvent>());
	    writeBufferAvailable = sessionImpl.writeBufferCapacity;
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.
	 *
	 * @param event the event
	 * @return {@code true} if successful, and {@code false} otherwise
	 * @throws MessageRejectedException if the cost of the event
	 *         exceeds the available buffer space in the queue
	 */
	boolean offer(SessionEvent event) {
	    int cost = event.getCost();
	    if (cost > writeBufferAvailable) {
	        throw new MessageRejectedException(
	            "Not enough queue space: " + writeBufferAvailable +
		    " bytes available, " + cost + " requested");
	    }
	    boolean success = getQueue().offer(event);
	    if (success && cost > 0) {
		ClientSessionServiceImpl.getDataService().markForUpdate(this);
                writeBufferAvailable -= cost;
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                        "{0} reserved {1,number,#} leaving {2,number,#}",
                        this, cost, writeBufferAvailable);
                }
	    }
	    return success;
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
	 * Returns {@code true} if the event queue is empty.
	 */
	boolean isEmpty() {
	    return getQueue().isEmpty();
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
	    DataService dataService =
		ClientSessionServiceImpl.getDataService();

	    for (int i = 0; i < sessionService.eventsPerTxn; i++) {
		SessionEvent event = eventQueue.poll();
		if (event == null) {
		    // no more events
		    break;
		}

		logger.log(Level.FINEST, "processing event:{0}", event);

                int cost = event.getCost();
		if (cost > 0) {
		    dataService.markForUpdate(this);
		    writeBufferAvailable += cost;
		    if (logger.isLoggable(Level.FINEST)) {
		        logger.log(Level.FINEST,
				   "{0} cleared reservation of " +
				   "{1,number,#} bytes, leaving {2,number,#}",
				   this, cost, writeBufferAvailable);
		    }
		}

		event.serviceEvent(this);
	    }
	}

	/* -- Implement ManagedObjectRemoval -- */

	/** {@inheritDoc} */
	public void removingObject() {
	    try {
		DataService dataService =
		    ClientSessionServiceImpl.getDataService();
		dataService.removeObject(queueRef.get());
	    } catch (ObjectNotFoundException e) {
		// already removed.
	    }
	}
    }

    /**
     * A persistent task to schedule tasks to notify (in succession) the
     * client session listener of each disconnected session on a given
     * failed node and to clean up the persistent data and bindings of
     * those client sessions.  In a single task, one disconnected session
     * is scheduled to be handled, and then this task is rescheduled to
     * schedule the handling of the next disconnected client session (if
     * one exists).
     */
    static class HandleNextDisconnectedSessionTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The prefix for client sessions on the failed node. */
	private final String nodePrefix;

	/** The last session key handled, initially the {@code nodePrefix}. */
	private String lastKey;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code nodeId}.
	 */
	HandleNextDisconnectedSessionTask(long nodeId) {
	    nodePrefix = getNodePrefix(nodeId);
	    lastKey = nodePrefix;
	}

	/** {@inheritDoc} */
	public void run() {
	    DataService dataService =
		ClientSessionServiceImpl.getDataService();
	    String key = dataService.nextServiceBoundName(lastKey);
	    if (key != null && key.startsWith(nodePrefix)) {
		TaskService taskService =
		    ClientSessionServiceImpl.getTaskService();
		taskService.scheduleTask(
		    new CleanupDisconnectedSessionTask(key));
		lastKey = key;
		taskService.scheduleTask(this);
	    }
	}
    }

    /**
     * A persistent task to clean up a client session bound to a
     * given {@code key} (specified during construction), by
     * invoking the {@code notifyListenerAndRemoveSession} method
     * on that client session.
     */
    private static class CleanupDisconnectedSessionTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The key for the client session. */
	private final String key;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code key}.
	 */
	CleanupDisconnectedSessionTask(String key) {
	    this.key = key;
	}

	/** {@inheritDoc} */
	public void run() {
	    DataService dataService =
		ClientSessionServiceImpl.getDataService();
	    ClientSessionImpl sessionImpl =
		(ClientSessionImpl) dataService.getServiceBinding(key);
	    sessionImpl.notifyListenerAndRemoveSession(
		dataService, false, true);
	}
    }
}
