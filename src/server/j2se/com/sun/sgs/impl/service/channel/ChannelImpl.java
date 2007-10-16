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

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl.Context;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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

    private final static CompactId SERVER_ID = new CompactId(new byte[]{0});

    /** Transaction-related context information. */
    private final Context context;

    /** The data service. */
    private final DataService dataService;

    /** Persistent channel state. */
    final ChannelState state;

    /** Flag that is 'true' if this channel is closed. */
    boolean isClosed = false;

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
	this.dataService = context.getService(DataService.class);
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

	    /*
	     * Add session and listener (if any) to channel state.
	     */
	    if (!state.addSession(dataService, session, listener)) {
		// session already added
		return;
	    }
	    /*
	     * Send 'join' message to client session.
	     */
	    MessageBuffer buf =
		new MessageBuffer(3 + MessageBuffer.getSize(state.name) +
				  state.id.getExternalFormByteCount());
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_JOIN).
		putString(state.name).
		putBytes(state.id.getExternalForm());
	    sendProtocolMessageOnCommit(session, buf.getBuffer());
	    
	    logger.log(Level.FINEST, "join session:{0} returns", session);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
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

	    if (!state.hasSession(dataService, session)) {
		return;
	    }

	    /*
	     * Remove session from channel state.
	     */
	    state.removeSession(dataService, session);
	    
	    /*
	     * Send 'leave' message to client session.
	     */
	    if (session.isConnected()) {
		MessageBuffer buf =
		    new MessageBuffer(3 + state.id.getExternalFormByteCount());
		buf.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		    putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		    putBytes(state.id.getExternalForm());
		sendProtocolMessageOnCommit(session, buf.getBuffer());
	    }
	    
	    logger.log(Level.FINEST, "leave session:{0} returns", session);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void leaveAll() {
	try {
	    checkClosed();
	    if (!state.hasSessions(dataService)) {
		return;
	    }

	    /*
	     * Send 'leave' message to all client sessions connected
	     * to this node.
	     */
	    long localNodeId = context.getLocalNodeId();
	    MessageBuffer buf =
		new MessageBuffer(3 + state.id.getExternalFormByteCount());
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		putBytes(state.id.getExternalForm());
	    final byte[] message = buf.getBuffer();
	    for (ClientSession session : 
		     state.getSessions(dataService, localNodeId))
	    {
		sendProtocolMessageOnCommit(session, message);
	    }

	    /*
	     * Notify all non-local channel servers that all members
	     * have left the channel, and, for a given channel server,
	     * that the member sessions connected to that channel
	     * server's node were removed from the channel and need to
	     * be sent a 'leave' protocol message.
	     */
	    final byte[] channelId = state.getIdBytes();
	    for (long nodeId : state.getChannelServerNodeIds()) {
		if (nodeId != localNodeId) {

		    final ChannelServer server = state.getChannelServer(nodeId);
		    final byte[][] sessions =
			getSessionIds(state.getSessions(dataService, nodeId));
		    runTaskOnCommit(
			null,
			new Runnable() {
			    public void run() {
				try {
				    server.send(channelId, sessions, message,
						state.delivery);
				} catch (Exception e) {
				    // skip unresponsive channel server
				    logger.logThrow(
				        Level.WARNING, e,
					"Contacting channel server:{0} throws",
					server);
				}
			    }});
		}
	    }
	    /*
	     * Remove all client sessions from this channel.
	     */
	    state.removeAllSessions(dataService);
	    logger.log(Level.FINEST, "leaveAll returns");
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /**
     * Returns an array of session IDs for the corresponding client
     * sessions in the specified set.
     */
    private static byte[][] getSessionIds(Set<ClientSession> sessions) {
	byte[][] sessionIds = new byte[sessions.size()][];
	int i = 0;
	for (ClientSession session : sessions) {
	    sessionIds[i++] = session.getSessionId().getBytes();
	}
	return sessionIds;
    }
    
    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	boolean hasSessions = state.hasSessions(dataService);
	logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Iterator<ClientSession> getSessions() {
	checkClosed();
	return state.getSessionIterator(dataService);
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
	    sendToAllMembers(message);
	    logger.log(Level.FINEST, "send message:{0} returns", message);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.FINEST, e, "send message:{0} throws", message);
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
	    sendToMembers(sessions, message);
	    
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
		sendToMembers(recipients, message);
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
	if (!isClosed) {
	    leaveAll();
	    state.removeAll(dataService);
	    isClosed = true;
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
	if (isClosed) {
	    throw new IllegalStateException("channel is closed");
	}
    }

    /**
     * Notifies this channel's global channel listener (if any), and
     * notifies the per-session channel listener (if any) that the
     * specified {@code message} was sent by the client session with
     * the specified {@code senderId}.
     */
    void notifyListeners(ClientSessionId senderId, byte[] message) {
	checkClosed();

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
	    listener = state.getListener(dataService, senderSession);
	    if (listener != null) {
		listener.receivedMessage(this, senderSession, message);
	    }
	}
    }

    /**
     * Send a protocol message to the specified session when the
     * transaction commits, logging (but not throwing) any exception.
     */
    private void sendProtocolMessageOnCommit(
	ClientSession session, byte[] message)
    {
	context.getClientSessionService().sendProtocolMessage(
	    session, message, state.delivery);
    }

    private void runTaskOnCommit(ClientSession session, Runnable task) {
	context.getClientSessionService().runTask(session, task);
    }

    /**
     * When this transaction commits, sends the given {@code
     * channelMessage} from this channel's server to all channel members.
     */
    private void sendToAllMembers(final byte[] channelMessage) {
	long localNodeId = context.getLocalNodeId();
	final byte[] channelIdBytes = state.idBytes;
	final byte[] protocolMessage =
	    ChannelServiceImpl.getChannelMessage(
		state.id, SERVER_ID, channelMessage,
		context.nextSequenceNumber());
	for (final long nodeId : state.getChannelServerNodeIds()) {
	    Set<ClientSession> recipients =
		state.getSessions(dataService, nodeId);
	    if (nodeId == localNodeId) {
		
		/*
		 * Send channel message to local recipients.
		 */
		for (ClientSession session : recipients) {
		    context.getClientSessionService().sendProtocolMessage(
			session, protocolMessage, state.delivery);
		}
		    
	    } else {
		final ChannelServer server = state.getChannelServer(nodeId);
		final byte[][] recipientIds = new byte[recipients.size()][];
		int i = 0;
		for (ClientSession session : recipients) {
		    recipientIds[i++] = session.getSessionId().getBytes();
		}
		    
		context.getClientSessionService().runTask(
		    null,
		    new Runnable() {
			public void run() {
			    try {
				server.send(channelIdBytes, recipientIds,
					    protocolMessage, state.delivery);
			    } catch (Exception e) {
				// skip unresponsive channel server
				logger.logThrow(
				    Level.WARNING, e,
				    "Contacting channel server:{0} on " +
				    " node:{1} throws ", server, nodeId);
			    }
			}});
	    }
	}
    }

    /**
     * When this transaction commits, sends the given {@code
     * channelMessage} from this channel's server to the specified
     * recipient {@code sessions}.
     */
    private void sendToMembers(Set<ClientSession> sessions,
			       final byte[] channelMessage)
    {
	final byte[] channelIdBytes = state.idBytes;
	Map<Long, Set<ClientSession>> recipientsPerNode =
	    new HashMap<Long, Set<ClientSession>>();
	for (ClientSession session : sessions) {
	    long nodeId = ChannelState.getNodeId(session);
	    Set<ClientSession> recipients =
		recipientsPerNode.get(nodeId);
	    if (recipients == null) {
		recipients = new HashSet<ClientSession>();
		recipientsPerNode.put(nodeId, recipients);
	    }
	    recipients.add(session);
	}
	
	long localNodeId = context.getLocalNodeId();
	for (final long nodeId : state.getChannelServerNodeIds()) {
	    Set<ClientSession> recipients = recipientsPerNode.get(nodeId);
	    if (recipients == null) {
		continue;
	    }
	    if (nodeId == localNodeId) {
		
		byte[] protocolMessage =
		    ChannelServiceImpl.getChannelMessage(
			state.id, SERVER_ID, channelMessage,
			context.nextSequenceNumber());
		/*
		 * Send channel message to local recipients.
		 */
		for (ClientSession session : recipients) {
		    context.getClientSessionService().sendProtocolMessage(
			session, protocolMessage, state.delivery);
		}
		    
	    } else {
		final ChannelServer server = state.getChannelServer(nodeId);
		final byte[][] recipientIds = new byte[recipients.size()][];
		int i = 0;
		for (ClientSession session : recipients) {
		    recipientIds[i++] = session.getSessionId().getBytes();
		}
		    
		context.getClientSessionService().runTask(
		    null,
		    new Runnable() {
			public void run() {
			    try {
				server.send(channelIdBytes, recipientIds,
					    channelMessage, state.delivery);
			    } catch (Exception e) {
				// skip unresponsive channel server
				logger.logThrow(
				    Level.WARNING, e,
				    "Contacting channel server:{0} on " +
				    " node:{1} throws ", server, nodeId);
			    }
			}});
	    }
	}
    }
}
