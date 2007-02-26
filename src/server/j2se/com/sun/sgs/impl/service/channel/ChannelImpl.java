/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl.Context;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.service.SgsClientSession;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction
 * specified by the context passed during construction.
 */
final class ChannelImpl implements Channel, Serializable {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;
    
    /** The logger for this class. */
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    private final static byte[] EMPTY_ID = new byte[0];

    /** Transaction-related context information. */
    private final Context context;

    /** Persistent channel state. */
    private final ChannelState state;

    /** Flag that is 'true' if this channel is closed. */
    private boolean channelClosed = false;

    /**
     * Constructs an instance of this class with the specified context
     * and channel state.
     *
     * @param context a context
     * @param state a channel state
     */
    ChannelImpl(Context context, ChannelState state) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl context:{0} state:{1}",
		       context, state);
	}
	this.state =  state;
	this.context = context;
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public String getName() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", state.name);
	}
	return state.name;
    }

    /** {@inheritDoc} */
    public Delivery getDeliveryRequirement() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDeliveryRequirement returns {0}", state.delivery);
	}
	return state.delivery;
    }

    /** {@inheritDoc} */
    public void join(final ClientSession session, ChannelListener listener) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    if (listener != null && !(listener instanceof Serializable)) {
		throw new IllegalArgumentException("listener not serializable");
	    }
	    if (!(session instanceof SgsClientSession)) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"join: session does not implement" +
			"SgsClientSession:{0}", session);
		}
		throw new IllegalArgumentException(
		    "unexpected ClientSession type: " + session);
	    }
	    if (state.hasSession(session)) {
		return;
	    }
	    
	    context.getService(DataService.class).markForUpdate(state);
	    state.addSession(session, listener);
	    context.joinChannel(session, this);
	    int nameSize = MessageBuffer.getSize(state.name);
	    MessageBuffer buf = new MessageBuffer(3 + nameSize);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_JOIN).
		putString(state.name);
	    sendProtocolMessageOnCommit(session, buf.getBuffer());
	    
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "join session:{0} returns", session);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "leave throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void leave(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null client session");
	    }
	    if (!(session instanceof SgsClientSession)) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"join: session does not implement " +
			"SgsClientSession:{0}", session);
		}
		throw new IllegalArgumentException(
		    "unexpected ClientSession type: " + session);
	    }

	    if (!state.hasSession(session)) {
		return;
	    }
	    
	    context.getService(DataService.class).markForUpdate(state);
	    context.leaveChannel(session, this);
	    state.removeSession(session);
	    if (session.isConnected()) {
		int nameSize = MessageBuffer.getSize(state.name);
		MessageBuffer buf = new MessageBuffer(3 + nameSize);
		buf.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		    putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		    putString(state.name);
		sendProtocolMessageOnCommit(session, buf.getBuffer());
	    }
	    
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "leave session:{0} returns", session);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(Level.FINEST, e, "leave throws");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void leaveAll() {
	try {
	    checkClosed();
	    if (!state.hasSessions()) {
		return;
	    }
	    context.getService(DataService.class).markForUpdate(state);
	    final Set<ClientSession> sessions = getSessions();
	    for (ClientSession session : sessions) {
		context.leaveChannel(session, this);
	    }
	    state.removeAllSessions();

	    int nameSize = MessageBuffer.getSize(state.name);
	    MessageBuffer buf = new MessageBuffer(3 + nameSize);
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		putString(state.name);
	    byte[] message = buf.getBuffer();
		    
	    for (ClientSession session : sessions) {
		sendProtocolMessageOnCommit(session, message);
	    }
	    logger.log(Level.FINEST, "leaveAll returns");
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }
    
    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	boolean hasSessions = state.hasSessions();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	}
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Set<ClientSession> getSessions() {
	checkClosed();
	Set<ClientSession> sessions =
	    Collections.unmodifiableSet(state.getSessions());
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getSessions returns {0}", sessions);
	}
	return sessions;
    }

    /** {@inheritDoc} */
    public void send(byte[] message) {
	try {
	    checkClosed();
	    if (message == null) {
		throw new NullPointerException("null message");
	    }
            if (message.length > SimpleSgsProtocol.MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.length + " > " +
                        SimpleSgsProtocol.MAX_MESSAGE_LENGTH);
            }
	    sendToClients(state.getSessions(), message);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "send message:{0} returns", message);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send message:{0} throws", message);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void send(ClientSession recipient, byte[] message) {
	try {
	    checkClosed();
	    if (recipient == null) {
		throw new NullPointerException("null recipient");
	    } else if (message == null) {
		throw new NullPointerException("null message");
	    }
            if (message.length > SimpleSgsProtocol.MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.length + " > " +
                        SimpleSgsProtocol.MAX_MESSAGE_LENGTH);
            }
	
	    Set<ClientSession> sessions = new HashSet<ClientSession>();
	    sessions.add(recipient);
	    sendToClients(sessions, message);
	    
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "send recipient: {0} message:{1} returns",
		    recipient, message);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send recipient: {0} message:{1} throws",
		    recipient, message);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void send(Set<ClientSession> recipients,
		     byte[] message)
    {
	try {
	    checkClosed();
	    if (recipients == null) {
		throw new NullPointerException("null recipients");
	    } else if (message == null) {
		throw new NullPointerException("null message");
	    }
            if (message.length > SimpleSgsProtocol.MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.length + " > " +
                        SimpleSgsProtocol.MAX_MESSAGE_LENGTH);
            }

	    if (!recipients.isEmpty()) {
		sendToClients(recipients, message);
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "send recipients: {0} message:{1} returns",
		    recipients, message);
	    }
	
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send recipients: {0} message:{1} throws",
		    recipients, message);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void close() {
	checkContext();
	if (!channelClosed) {
	    leaveAll();
	    state.removeAll();
	    context.removeChannel(state.name);
	    channelClosed = true;
	}
	
	logger.log(Level.FINEST, "close returns");
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	return
	    (this == obj) ||
	    (obj.getClass() == this.getClass() &&
	     state.equals(((ChannelImpl) obj).state));
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return state.name.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + state.name + "]";
    }

    /* -- Serialization methods -- */

    private Object writeReplace() {
	return new External(state.name);
    }

    /**
     * Represents the persistent representation for a channel (just its name).
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final String name;

	External(String name) {
	    this.name = name;
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
	    ChannelManager cm = AppContext.getChannelManager();
	    Channel channel = cm.getChannel(name);
	    return channel;
	}
    }

    /* -- other methods and classes -- */

    /**
     * Checks that this channel's context is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	ChannelServiceImpl.checkContext(context);
    }

    /**
     * Checks the context, and then checks that this channel is not
     * closed, throwing an IllegalStateException if the channel is
     * closed.
     */
    private void checkClosed() {
	checkContext();
	if (channelClosed) {
	    throw new IllegalStateException("channel is closed");
	}
    }

    /**
     * Forwards the specified {@code message} from the session with
     * {@code senderId} to the sessions with {@code recipientId}s, and
     * then notifies this channel's global channel listener (if any),
     * and notifies the per-session channel listener (if any).
     */
    void forwardMessageAndNotifyListeners(
	    final ClientSessionId senderId,
            final Set<byte[]> recipientIds,
            final byte[] message, final long seq)
    {
	checkClosed();
	
	// TBD: if the sending session has disconnected, do we still
	// want to send the message?
	
	/*
	 * Build list of recipients.
	 */
        Set<ClientSession> recipients;
        if (recipientIds.size() == 0) {
            recipients = state.getSessionsExcludingId(senderId);
        } else {
            recipients = new HashSet<ClientSession>();
            for (byte[] sessionId : recipientIds) {
                ClientSession session =
                    context.getService(ClientSessionService.class).
		        getClientSession(sessionId);
                // Skip the sender and any disconnected or non-member sessions
                if ((session != null) &&
                    (!senderId.equals(session.getSessionId())) &&
		    (state.hasSession(session)))
                {
                    recipients.add(session);
                }
            }
        }

	/*
	 * Schedule messages to be sent upon transaction commit.
	 */
	if (! recipients.isEmpty()) {
	    byte[] protocolMessage =
                getChannelMessage(senderId.getBytes(), message, seq);

	    for (ClientSession session : recipients) {
		((SgsClientSession) session).sendProtocolMessageOnCommit(
                    protocolMessage, state.delivery);
	    }
	}

	/*
	 * Notify channel listeners of channel message.
	 */
	ClientSession senderSession = senderId.getClientSession();
	if (senderSession != null) {
	    // Notify per-channel listener.
	    ChannelListener listener = state.getListener();
	    if (listener != null) {
		listener.receivedMessage(this, senderSession, message);
	    }

	    // Notify per-session listener.
	    listener = state.getListener(senderSession);
	    if (listener != null) {
		listener.receivedMessage(this, senderSession, message);
	    }
	}
    }

    /**
     * Returns a MessageBuffer containing a CHANNEL_MESSAGE protocol
     * message with this channel's name, and the specified sender,
     * message, and sequence number.
     */
    private byte[] getChannelMessage(
	byte[] senderId, byte[] message, long sequenceNumber)
    {
        int nameLen = MessageBuffer.getSize(state.name);
        MessageBuffer buf =
            new MessageBuffer(15 + nameLen + senderId.length +
                    message.length);
        buf.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putString(state.name).
            putLong(sequenceNumber).
            putShort(senderId.length).
            putBytes(senderId).
            putShort(message.length).
            putBytes(message);

        return buf.getBuffer();
    }
    
    /**
     * Send a protocol message to the specified session when the
     * transaction commits, logging (but not throwing) any exception.
     */
    private void sendProtocolMessageOnCommit(
	ClientSession session, byte[] message)
    {
        try {
            ((SgsClientSession) session).
		sendProtocolMessageOnCommit(message, state.delivery);
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.logThrow(
                    Level.FINEST, e,
                    "sendProtcolMessageOnCommit session:{0} message:{1} throws",
                    session, message);
            }
            // eat exception
        }
    }

    /**
     * When this transaction commits, sends the given {@code message}
     * from this channel's server to the specified set of client
     * {@code sessions}.
     */
    private void sendToClients(Set<ClientSession> sessions, byte[] message) {

	Set<byte[]> clients = new HashSet<byte[]>();
	for (ClientSession session : sessions) {
	    clients.add(session.getSessionId().getBytes());
	}
	byte[] protocolMessage =
	    getChannelMessage(EMPTY_ID, message, context.nextSequenceNumber());
	    
	for (byte[] sessionId : clients) {
	    SgsClientSession session = 
		context.getService(ClientSessionService.class).
		    getClientSession(sessionId);
	    // skip disconnected and non-member sessions
	    if (session != null &&
		state.hasSession(session) &&
		session.isConnected())
	    {
		session.sendProtocolMessageOnCommit(
		    protocolMessage, state.delivery);
	    }
	}
    }
}
