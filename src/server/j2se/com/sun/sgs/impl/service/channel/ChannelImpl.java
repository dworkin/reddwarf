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
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
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
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction.
 */
abstract class ChannelImpl implements Channel, Serializable {

    /** The serialVersionUID for this class. */
    private final static long serialVersionUID = 1L;
    
    /** The logger for this class. */
    protected final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    /** The compact ID for the server. */
    private final static CompactId SERVER_ID = new CompactId(new byte[]{0});

    /** Persistent channel state. */
    protected final ChannelState state;

    /** Flag that is 'true' if this channel is closed. */
    boolean isClosed = false;

    /**
     * Constructs an instance of this class with the specified channel state.
     *
     * @param state a channel state
     */
    protected ChannelImpl(ChannelState state) {
	if (state == null) {
	    throw new NullPointerException("null state");
	}
	logger.log(Level.FINER, "Created ChannelImpl state:{0}", state);
	this.state =  state;
    }

    /**
     * Constructs a new {@code ChannelImpl} with the given {@code
     * name}, {@code delivery} requirement, and transaction {@code
     * context}.
     */
    static ChannelImpl newInstance(String name, Delivery delivery) {
	ChannelState channelState =
	    ChannelState.newInstance(name, delivery);
	return newInstance(channelState);
    }

    /**
     * Constructs a {@code ChannelImpl} with the given {@code channelState}.
     */
    static ChannelImpl newInstance(ChannelState channelState) {
	// TBD: create other channel types depending on delivery.
	return new OrderedUnreliableChannelImpl(channelState);
    }

    /**
     * TBD: return null instead?
     * @throws NameNotBoundException if channel doesn't exist
     */
    static ChannelImpl getInstance(String name) {
	ChannelState channelState = ChannelState.getInstance(name);
	return newInstance(channelState);
	
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
    public void join(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    
	    /*
	     * Add session to channel state.
	     */
	    if (!state.addSession(session)) {
		// session already added
		return;
	    }
	    /*
	     * Send 'join' message to client session.
	     */
	    MessageBuffer buf =
		new MessageBuffer(
		    3 + MessageBuffer.getSize(state.name) +
		    state.compactChannelId.getExternalFormByteCount());
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_JOIN).
		putString(state.name).
		putBytes(state.compactChannelId.getExternalForm());
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

	    if (!state.hasSession(session)) {
		return;
	    }

	    /*
	     * Remove session from channel state.
	     */
	    state.removeSession(session);
	    
	    /*
	     * Send 'leave' message to client session.
	     */
	    if (session.isConnected()) {
		MessageBuffer buf =
		    new MessageBuffer(
			3 + state.compactChannelId.getExternalFormByteCount());
		buf.putByte(SimpleSgsProtocol.VERSION).
		    putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		    putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		    putBytes(state.compactChannelId.getExternalForm());
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
	    if (!state.hasSessions()) {
		return;
	    }

	    /*
	     * Send 'leave' message to all client sessions connected
	     * to this node.
	     */
	    long localNodeId = getLocalNodeId();
	    MessageBuffer buf =
		new MessageBuffer(
		    3 + state.compactChannelId.getExternalFormByteCount());
	    buf.putByte(SimpleSgsProtocol.VERSION).
		putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
		putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		putBytes(state.compactChannelId.getExternalForm());
	    final byte[] message = buf.getBuffer();
	    for (ClientSession session : state.getSessions(localNodeId)) {
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
			getSessionIds(state.getSessions(nodeId));
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
	    state.removeAllSessions();
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
	boolean hasSessions = state.hasSessions();
	logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Iterator<ClientSession> getSessions() {
	checkClosed();
	return state.getSessionIterator();
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
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "send channel:{0} message:{1} returns",
			   state.name, HexDumper.format(message));
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send channel:{0} message:{1} throws",
		    state.name, HexDumper.format(message));
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void close() {
	checkContext();
	if (!isClosed) {
	    leaveAll();
	    state.closeAndRemoveState();
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

    /**
     * Returns an object that represents this channel's external form.
     *
     * @return	an object that represents this channel's external form
     */
    protected final Object writeReplace() {
	return new External(state.channelIdBytes);
    }

    /**
     * Represents the persistent representation for a channel (just its channel ID).
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final byte[] channelIdBytes;

	External(byte[] channelIdBytes) {
	    this.channelIdBytes = channelIdBytes;
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
	    ChannelState channelState = ChannelState.getInstance(channelIdBytes);
	    return newInstance(channelState);
	}
    }

    /* -- other methods and classes -- */

    /**
     * Checks that this channel's context is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	// FIXME: should check to see that a transaction is active.
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
     * Send a protocol message to the specified session when the
     * transaction commits.
     */
    protected void sendProtocolMessageOnCommit(
	ClientSession session, byte[] message)
    {
	ChannelServiceImpl.getClientSessionService().sendProtocolMessage(
	    session, message, state.delivery);
    }

    protected void runTaskOnCommit(ClientSession session, Runnable task) {
	ChannelServiceImpl.getClientSessionService().runTask(session, task);
    }

    /**
     * When this transaction commits, sends the given {@code
     * channelMessage} from this channel's server to all channel members.
     */
    protected abstract void sendToAllMembers(final byte[] channelMessage);

    /**
     * Returns a MessageBuffer containing a CHANNEL_MESSAGE protocol
     * message with this channel's name, and the specified sender,
     * message, and sequence number.
     */
    protected byte[] getChannelMessage(byte[] message) {

	CompactId senderId = SERVER_ID;
        MessageBuffer buf =
            new MessageBuffer(
		13 + state.compactChannelId.getExternalFormByteCount() +
		senderId.getExternalFormByteCount() + message.length);
        buf.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putBytes(state.compactChannelId.getExternalForm()).
            putLong(state.nextSequenceNumber()).
            putBytes(senderId.getExternalForm()).
	    putByteArray(message);

        return buf.getBuffer();
    }

    /**
     * Returns the local node's ID.
     */
    protected long getLocalNodeId() {
	return ChannelServiceImpl.getLocalNodeId();
    }

    private static CompactId getCompactId(ClientSession session) {
	return ((ClientSessionImpl) session).getCompactSessionId();
    }
    
}
