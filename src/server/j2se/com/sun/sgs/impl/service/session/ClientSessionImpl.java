/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
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
public class ClientSessionImpl implements ClientSession, Serializable {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(
	    "com.sun.sgs.impl.service.session.proxy"));

    /** The identity for this session. */
    private volatile Identity identity;
    
    /** The session id. */
    private final CompactId sessionId;

    /** The local client session service. */
    private transient ClientSessionServiceImpl sessionService;

    /** The client session server (possibly remote) for this client session. */
    private final ClientSessionServer sessionServer;

    /** The sequence number for ordered messages sent from this client. */
    // FIXME: using this here is bogus.
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /** Indicates whether this session is connected. */
    private boolean connected = true;

    /**
     * Constructs an instance of this class with the specified {@code
     * sessionId} and {@code identity}.
     *
     * @param	sessionId a session ID
     * @param	identity an identity
     */
    ClientSessionImpl(CompactId sessionId) {
	if (sessionId == null) {
	    throw new NullPointerException("null sessionId");
	}
	this.sessionId = sessionId;
	this.sessionService = ClientSessionServiceImpl.getInstance();
	this.sessionServer = sessionService.getServerProxy();
    }

    /**
     * Constructs an instance from the specified fields in the
     * external form.
     */
    private ClientSessionImpl(CompactId sessionId,
			      Identity identity,
			      ClientSessionServer sessionServer,
			      boolean connected)
    {
	this.sessionId = sessionId;
	this.identity = identity;
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
	logger.log(Level.FINEST, "getSessionId returns {0}", sessionId);
        return new ClientSessionId(sessionId.getId());
    }

    /** {@inheritDoc} */
    public synchronized boolean isConnected() {
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
		sessionId.equals(session.sessionId);
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
	return sessionId.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + getName() + "]@" + sessionId;
    }
    
    /* -- Serialization methods -- */

    private Object writeReplace() {
	return new External(sessionId, identity, sessionServer, connected);
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

	External(CompactId sessionId,
		 Identity identity,
		 ClientSessionServer sessionServer,
		 boolean connected)
	{
	    this.idBytes = sessionId.getId();
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
	    ClientSession session = sessionService.getClientSession(idBytes);
	    if (session == null) {
		CompactId sessionId = new CompactId(idBytes);
		session = new ClientSessionImpl(
		    sessionId, identity, sessionServer, connected);
	    }
	    return session;
	}
    }
    
    /* -- Other methods -- */

    /**
     * Returns the client session ID for this client session in {@code
     * CompactId} format.
     *
     * @return the client session ID as a {@code CompactId}
     */
    public CompactId getCompactSessionId() {
	return sessionId;
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
    synchronized void setDisconnected() {
	connected = false;
    }
	

}
