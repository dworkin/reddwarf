/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a client session (proxy).
 */
public class ClientSessionImpl
    implements ClientSession, NodeAssignment, Serializable
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The logger name and prefix for session keys and session node keys. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.session.";

    /** The prefix to add before a client session ID in a session key. */
    private static final String SESSION_COMPONENT = "impl.";

    /** The prefix to add before a client session listener in a listener key. */
    private static final String LISTENER_COMPONENT = "listener.";

    /** The node component session node keys. */
    private static final String NODE_COMPONENT = "node.";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + "impl"));

    /** The local ClientSessionService. */
    private transient ClientSessionServiceImpl sessionService;

    /** The session ID. */
    private transient BigInteger id;

    /** The session ID bytes. */
    private final byte[] idBytes;
    
    /** The identity for this session. */
    private final Identity identity;

    /** The node ID for this session (final because sessions can't move yet). */
    private final long nodeId;

    /** The client session server (possibly remote) for this client session. */
    private final ClientSessionServer sessionServer;

    /** The sequence number for ordered messages sent from this client. */
    // FIXME: using this here is bogus.
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /** Indicates whether this session is connected. */
    private volatile boolean connected = true;

    /**
     * Constructs an instance of this class with the specified {@code
     * sessionService}, {@code identity}. and the local node ID, and stores
     * this instance with the following bindings:<p>
     *
     * <pre>
     * com.sun.sgs.impl.service.session.impl.<idBytes>
     * com.sun.sgs.impl.service.session.node.<nodeId>.impl.<idBytes>
     *</pre>
     * This method should only be called within a transaction.
     *
     * @param	sessionService a client session service
     * @param	identity tbe session's identity
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
	this.sessionServer = sessionService.getServerProxy();
	this.identity = identity;
	this.nodeId = sessionService.getLocalNodeId();
	DataService dataService = sessionService.getDataService();
	ManagedReference sessionRef = dataService.createReference(this);
	id = sessionRef.getId();
	idBytes = id.toByteArray();
	dataService.setServiceBinding(getSessionKey(idBytes), this);
	dataService.setServiceBinding(getSessionNodeKey(nodeId, idBytes), this);
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
	logger.log(Level.FINEST, "getName returns {0}", name);
	return name;
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	logger.log(Level.FINEST, "isConnected returns {0}", connected);
	return connected;
    }

    /** {@inheritDoc} */
    public ClientSession send(final byte[] message) {
	try {
            if (message.length > SimpleSgsProtocol.MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.length + " > " +
                        SimpleSgsProtocol.MAX_MESSAGE_LENGTH);
            } else if (!isConnected()) {
		throw new IllegalStateException("client session not connected");
	    }
	    MessageBuffer buf =
		new MessageBuffer(3 + 8 + 2 + message.length);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
		putByte(SimpleSgsProtocol.SESSION_MESSAGE).
		putLong(sequenceNumber.getAndIncrement()).
		putShort(message.length).
		putBytes(message);
	    // FIXME: The protocol message should be assembled at the
	    // session server and the sequence number should be assigned there.
	    sessionService.sendProtocolMessage(
		this, buf.getBuffer(), Delivery.RELIABLE);
	
	    logger.log(Level.FINEST, "send message:{0} returns", message);
	    return this;

	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "send message:{0} throws", message);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void disconnect() {
	if (isConnected()) {
	    sessionService.disconnect(this);
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
	} else if (obj.getClass() == this.getClass()) {
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
	return getClass().getName() + "[" + getName() + "]@" + id;
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
	logger.log(Level.FINEST, "getSessionId returns {0}", id);
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
	    ManagedReference sessionRef = dataService.createReferenceForId(id);
	    sessionImpl = sessionRef.get(ClientSessionImpl.class);
	} catch (ObjectNotFoundException e)  {
	}
	return sessionImpl;
    }

    /**
     * Invokes the {@code disconnected} callback on this session's
     * {@code ClientSessionListener} (if present), removes the
     * listener and its binding (if present), and then removes this
     * session and its bindings from the specified {@code
     * dataService}.  If the bindings have already been removed from
     * the {@code dataService} this method takes no action.  This
     * method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	graceful {@code true} if disconnection is graceful,
     *		and {@code false} otherwise
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void notifyListenerAndRemoveSession(
	DataService dataService, boolean graceful)
    {
	String sessionKey = getSessionKey(idBytes);
	String sessionNodeKey = getSessionNodeKey(nodeId, idBytes);
	String listenerKey = getListenerKey(idBytes);

	/*
	 * Get ClientSessionListener, and remove its binding and
	 * wrapper if applicable.  The listener may not be bound
	 * in the data service if the AppListener.loggedIn callback
	 * either threw a non-retryable exception or returned a
	 * null listener.
	 *
	 */
	ClientSessionListener listener = null;
	try {
	    ManagedObject obj =
		dataService.getServiceBinding(listenerKey, ManagedObject.class);
	    dataService.removeServiceBinding(listenerKey);
 	    if (obj instanceof ListenerWrapper) {
		dataService.removeObject(obj);
		listener = ((ListenerWrapper) obj).get();
	    } else {
		listener = (ClientSessionListener) obj;
	    }
	    // TBD: should the listener be removed too?
	    
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.FINE, e,
		"removing ClientSessionListener for session:{0} throws",
		this);
	}

	/*
	 * Invoke listener's disconnected callback.
	 */
	if (listener != null) {
	    listener.disconnected(graceful);
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
    ClientSessionServer getClientSessionServer() {
	return sessionServer;
    }
	    
    /**
     * Sets this session's state to disconnected.
     */
    void setDisconnected() {
	sessionService.getDataService().markForUpdate(this);
	connected = false;
    }
	
    /**
     * Returns the key to access from the data service the {@code
     * ClientSessionImpl} instance with the specified session {@code
     * idBytes}.
     *
     * @param	idBytes a session ID
     * @return	a key for acessing the {@code ClientSessionImpl} instance
     */
    private static String getSessionKey(byte[] idBytes) {
	return
	    PKG_NAME + SESSION_COMPONENT + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the key to access from the data service the {@code
     * ClientSessionListener} instance for the specified session
     * {@code idBytes}. If the {@code ClientSessionListener} does not
     * implement {@code ManagedObject}, then the key will be bound to
     * a {@code ListenerWrapper}.
     *
     * @param	idBytes a session ID
     * @return	a key for acessing the {@code ClientSessionListener} instance
     */
    private static String getListenerKey(byte[] idBytes) {
	return
	    PKG_NAME + LISTENER_COMPONENT + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the key to access from the data service the {@code
     * ClientSessionImpl} instance with the specified {@code nodeId} and
     * session {@code idBytes}.
     *
     * @param	idBytes a session ID
     * @return	a key for acessing the {@code ClientSessionImpl} instance
     */
    private static String getSessionNodeKey(long nodeId, byte[] idBytes) {
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
     * com.sun.sgs.impl.service.session.listener.<idBytes>
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
	String listenerKey = getListenerKey(idBytes);
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
	String listenerKey = getListenerKey(idBytes);
	ManagedObject obj =
	    dataService.getServiceBinding(
		listenerKey, ManagedObject.class);
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
}
