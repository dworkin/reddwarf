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
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a client session (proxy).
 */
public class ClientSessionImpl
    implements ClientSession, ManagedObject, Serializable
{

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The logger name and prefix for session keys and session node keys. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.session";

    /** The prefix to add before a client session ID in a session key. */
    private static final String SESSION_PREFIX = "proxy";

    /** The prefix to add before a client session listener in a listener key. */
    private static final String LISTENER_PREFIX = "listener";

    /** The prefix to add before a node ID in session and session node keys. */
    private static final String NODE_PREFIX = "node";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The session ID. */
    private transient CompactId compactId;

    /** The local ClientSessionService. */
    private transient ClientSessionServiceImpl sessionService;

    /** The session ID bytes. */
    private final byte[] idBytes;

    /** The identity for this session. */
    private volatile Identity identity;

    /** The node ID for this session. */
    private volatile long nodeId;

    /** The client session server (possibly remote) for this client session. */
    private final ClientSessionServer sessionServer;

    /** The sequence number for ordered messages sent from this client. */
    // FIXME: using this here is bogus.
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /** Indicates whether this session is connected. */
    private volatile boolean connected = true;

    /**
     * Constructs an instance of this class with the specified {@code
     * sessionService} and {@code compactId}.
     *
     * @param	sessionService a client session service
     * @param	compactId a session ID
     */
    ClientSessionImpl(ClientSessionServiceImpl sessionService, CompactId compactId) {
	if (sessionService == null || compactId == null) {
	    throw new NullPointerException("null argument");
	}
	this.sessionService = sessionService;
	this.compactId = compactId;
	this.idBytes = compactId.getId();
	this.sessionServer = sessionService.getServerProxy();
    }

    /**
     * Constructs an instance from the specified fields in the
     * external form.
     */
    private ClientSessionImpl(ClientSessionServiceImpl sessionService,
			      CompactId compactId,
			      Identity identity,
			      long nodeId,
			      ClientSessionServer sessionServer,
			      boolean connected)
    {
	this.sessionService = sessionService;
	this.compactId = compactId;
	this.idBytes = compactId.getId();
	this.identity = identity;
	this.nodeId = nodeId;
	this.sessionServer = sessionServer;
	this.connected = connected;
    }
    
    /* -- Implement ClientSession -- */

    /** {@inheritDoc} */
    public String getName() {
        String name = identity.getName();
	logger.log(Level.FINEST, "getName returns {0}", name);
	return name;
    }
    
    /** {@inheritDoc} */
    public Identity getIdentity() {
        logger.log(Level.FINEST, "getIdentity returns {0}", identity);
	return identity;
    }

    /** {@inheritDoc} */
    public ClientSessionId getSessionId() {
	logger.log(Level.FINEST, "getSessionId returns {0}", compactId);
        return new ClientSessionId(idBytes);
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	logger.log(Level.FINEST, "isConnected returns {0}", connected);
	return connected;
    }

    /** {@inheritDoc} */
    public void send(final byte[] message) {
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

	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "send message:{0} throws", message);
	    throw e;
	}
	
	logger.log(Level.FINEST, "send message:{0} returns", message);
    }

    /** {@inheritDoc} */
    public void disconnect() {
	if (isConnected()) {
	    sessionService.disconnect(this);
	}
	logger.log(Level.FINEST, "disconnect returns");
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    ClientSessionImpl session = (ClientSessionImpl) obj;
	    return
		areEqualIdentities(getIdentity(), session.getIdentity()) &&
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
	return getClass().getName() + "[" + getName() + "]@" + compactId;
    }
    
    /* -- Serialization methods -- */

    private Object writeReplace() {
	return
	    new External(
		idBytes, identity, nodeId, sessionServer, connected);
    }

    /**
     * Represents the persistent form of a client session.
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final byte[] idBytes;
        private final Identity identity;
	private final long nodeId;
	private final ClientSessionServer sessionServer;
	private final boolean connected;

	External(byte[] idBytes,
		 Identity identity,
		 long nodeId,
		 ClientSessionServer sessionServer,
		 boolean connected)
	{
	    this.idBytes = idBytes;
            this.identity = identity;
	    this.nodeId = nodeId;
	    this.sessionServer = sessionServer;
	    this.connected = connected;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	}

	private Object readResolve() throws ObjectStreamException {
	    ClientSessionServiceImpl sessionService =
		ClientSessionServiceImpl.getInstance();
	    ClientSessionImpl sessionImpl =
		sessionService.getLocalClientSessionImpl(idBytes);
	    if (sessionImpl == null) {
		CompactId compactId = new CompactId(idBytes);
		sessionImpl = new ClientSessionImpl(
		    sessionService, compactId, identity, nodeId,
		    sessionServer, connected);
	    }
	    return sessionImpl;
	}
    }

    /* -- Public instance methods -- */
    
    /**
     * Returns the client session ID for this client session in {@code
     * CompactId} format.
     *
     * @return	the client session ID as a {@code CompactId}
     */
    public CompactId getCompactSessionId() {
	return compactId;
    }

    /**
     * Returns the node ID for this client session.
     *
     * @return	the node ID for this client session
     */
    public long getNodeId() {
	return nodeId;
    }

    /* -- Other methods -- */

    /**
     * Stores the state associated with this instance in the specified
     * {@code dataService} with the following bindings:<p>
     *
     * <pre>
     * com.sun.sgs.impl.service.session.proxy.<idBytes>
     * com.sun.sgs.impl.service.session.node.<nodeId>.proxy.<idBytes>
     *</pre>
     * This method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @throws TransactionException if there is a problem with the
     * 		current transaction
     */
    void putSession(DataService dataService) {
	if (identity == null) {
	    throw new IllegalStateException("session's identity is not set");
	}
	dataService.setServiceBinding(getSessionKey(idBytes), this);
	dataService.setServiceBinding(getSessionNodeKey(nodeId, idBytes), this);
    }

    /**
     * Returns the {@code ClientSession} instance for the given {@code
     * idBytes}, retrieved from the specified {@code dataService}, or
     * {@code null} if the client session isn't bound in the data
     * service.  This method should only be called within a
     * transaction.
     *
     * @param	dataService a data service
     * @param	idBytes a sessionID
     * @return	the session for the given session {@code idBytes},
     *		or {@code null}
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static ClientSessionImpl getSession(DataService dataService, byte[] idBytes) {
	String key = getSessionKey(idBytes);
	ClientSessionImpl sessionImpl = null;
	try {
	    sessionImpl =
		dataService.getServiceBinding(key, ClientSessionImpl.class);
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"ClientSessionImpl binding:{0} exists, but object removed",
		key);
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
     * @param	idBytes a session ID
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
		"removing ClientSessionListener for  session:{0} throws",
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
     * Sets the identity to the specified one.  This method should be
     * called outside of a transaction to set the non-final fields of
     * this instance before it is stored in the data service.
     */
    void setIdentityAndNodeId(Identity identity, long nodeId) {
	if (identity == null) {
	    throw new NullPointerException("null identity");
	}
	this.identity = identity;
	this.nodeId = nodeId;
    }

    /**
     * Sets this session's state to disconnected.
     */
    void setDisconnected() {
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
	    PKG_NAME + "." +
	    SESSION_PREFIX + "." + HexDumper.toHexString(idBytes);
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
	    PKG_NAME + "." +
	    LISTENER_PREFIX + "." + HexDumper.toHexString(idBytes);
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
	return
	    getNodePrefix(nodeId) + "." +
	    SESSION_PREFIX + "." + HexDumper.toHexString(idBytes);
    }

    /**
     * Returns the prefix to access from the data service {@code
     * ClientSessionImpl} instances with the the specified {@code nodeId}.
     */
    static String getNodePrefix(long nodeId) {
	return
	    PKG_NAME + "." +
	    NODE_PREFIX + "." + nodeId;
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
