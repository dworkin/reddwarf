/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
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
    private final static long serialVersionUID = 1L;

    /** The logger name and prefix for session keys. */
    private static final String SESSION_PREFIX =
	"com.sun.sgs.impl.service.session.proxy";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(SESSION_PREFIX));

    /** The session ID. */
    private final CompactId compactId;

    /** The session ID bytes. */
    private final byte[] idBytes;

    /** The identity for this session. */
    private volatile Identity identity;
    
    /** The local client session service. */
    private final ClientSessionServiceImpl sessionService;

    /** The client session server (possibly remote) for this client session. */
    private final ClientSessionServer sessionServer;

    /** The sequence number for ordered messages sent from this client. */
    // FIXME: using this here is bogus.
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /** Indicates whether this session is connected. */
    private volatile boolean connected = true;

    /**
     * Constructs an instance of this class with the specified {@code
     * compactId} and {@code identity}.
     *
     * @param	compactId a session ID
     * @param	identity an identity
     */
    ClientSessionImpl(CompactId compactId) {
	if (compactId == null) {
	    throw new NullPointerException("null compactId");
	}
	this.compactId = compactId;
	this.idBytes = compactId.getId();
	this.sessionService = ClientSessionServiceImpl.getInstance();
	this.sessionServer = sessionService.getServerProxy();
    }

    /**
     * Constructs an instance from the specified fields in the
     * external form.
     */
    private ClientSessionImpl(CompactId compactId,
			      Identity identity,
			      ClientSessionServiceImpl sessionService,
			      ClientSessionServer sessionServer,
			      boolean connected)
    {
	this.compactId = compactId;
	this.idBytes = compactId.getId();
	this.identity = identity;
	this.sessionService = sessionService;
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
	return new External(idBytes, identity, sessionServer, connected);
    }

    /**
     * Represents the persistent form of a client session.
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final byte[] idBytes;
        private final Identity identity;
	private final ClientSessionServer sessionServer;
	private final boolean connected;

	External(byte[] idBytes,
		 Identity identity,
		 ClientSessionServer sessionServer,
		 boolean connected)
	{
	    this.idBytes = idBytes;
            this.identity = identity;
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
		sessionService.getLocalClientSession(idBytes);
	    if (sessionImpl == null) {
		CompactId compactId = new CompactId(idBytes);
		sessionImpl = new ClientSessionImpl(
		    compactId, identity, sessionService,
		    sessionServer, connected);
	    }
	    return sessionImpl;
	}
    }
    
    /* -- Other methods -- */

    /**
     * Stores this instance in the specified {@code dataService}.
     * This method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void putSession(DataService dataService) {
	dataService.setServiceBinding(getSessionKey(idBytes), this);
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
    static ClientSession getSession(DataService dataService, byte[] idBytes) {
	String key = getSessionKey(idBytes);
	ClientSessionImpl sessionImpl = null;
	try {
	    sessionImpl =
		dataService.getServiceBinding(key, ClientSessionImpl.class);
	} catch (NameNotBoundException e) {
	}
	return sessionImpl;
    }
    
    /**
     * Removes the {@code ClientSessionImpl} with the specified
     * session {@code idBytes} and its binding from the specified
     * {@code dataService}.  If the binding has already been removed
     * from the {@code dataService} this method takes no action.  This
     * method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	idBytes a session ID
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static void removeSession(DataService dataService, byte[] idBytes) {
	String key = getSessionKey(idBytes);
	ClientSessionImpl sessionImpl;
	try {
	    sessionImpl =
		dataService.getServiceBinding(key, ClientSessionImpl.class);
	    dataService.removeServiceBinding(key);
	    dataService.removeObject(sessionImpl);
	} catch (NameNotBoundException e) {
	}
    }

    /**
     * Returns the client session ID for this client session in {@code
     * CompactId} format.
     *
     * @return the client session ID as a {@code CompactId}
     */
    public CompactId getCompactSessionId() {
	return compactId;
    }

    /**
     * Returns the {@code ClientSessionServer} for this instance.
     */
    ClientSessionServer getClientSessionServer() {
	return sessionServer;
    }
	    
    /**
     * Sets the identity to the specified one.
     */
    void setIdentity(Identity identity) {
	if (identity == null) {
	    throw new NullPointerException("null identity");
	}
	this.identity = identity;
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
	return SESSION_PREFIX + "." + HexDumper.toHexString(idBytes);
    }
}
