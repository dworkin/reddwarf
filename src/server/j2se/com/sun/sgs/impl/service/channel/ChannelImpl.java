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

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
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

    /** The package name. */
    private final static String PKG_NAME = "com.sun.sgs.impl.service.channel.";

    /** The channel set component prefix. */
    private final static String SET_COMPONENT = "set.";

    /** The member session component prefix. */
    private final static String SESSION_COMPONENT = "session.";

    /** The ID from a managed reference to this instance. */
    protected final byte[] channelIdBytes;

    /** The sequence number for messages from the server on this channel. */
    private long seq = 0;

    /** The delivery requirement for messages sent on this channel. */
    protected final Delivery delivery;

    /** The  ChannelServers that have locally connected sessions
     * that are members of this channel, keyed by node ID.
     */
    private final Map<Long, ChannelServer> servers =
	new HashMap<Long, ChannelServer>();

    /** The data service. */
    private transient DataService dataService;

    /** Flag that is 'true' if this channel is closed. */
    private boolean isClosed = false;

    /**
     * Constructs an instance of this class with the specified channel state.
     *
     * @param state a channel state
     */
    protected ChannelImpl(Delivery delivery) {
	this.delivery = delivery;
	this.dataService = ChannelServiceImpl.getDataService();
	ManagedReference ref = dataService.createReference(this);
	this.channelIdBytes = ref.getId().toByteArray();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl:{0}",
		       HexDumper.toHexString(channelIdBytes));
	}
    }

    /* -- Factory methods -- */

    /**
     * Constructs a new {@code ChannelImpl} with the given {@code
     * delivery} requirement.
     */
    static ChannelImpl newInstance(Delivery delivery) {
	// TBD: create other channel types depending on delivery.
	return new OrderedUnreliableChannelImpl(delivery);
    }

    /**
     * Returns a channel with the specified {@code channelIdByes}, or
     * {@code null} if the channel doesn't exist.  This method uses
     * the {@code channelIdBytes} as a {@code ManagedReference} ID to
     * the channel's state.
     *
     * @param   channelIdBytes a channel ID byte array
     * @return  the channel with the specified {@code channelIdBytes},
     *		or {@code null} if the channel doesn't exist
     */
    static ChannelImpl getInstance(byte[] channelIdBytes) {
	try {
	    BigInteger refId = new BigInteger(1, channelIdBytes);
	    DataService dataService = ChannelServiceImpl.getDataService();
	    ManagedReference implRef = dataService.createReferenceForId(refId);
	    return implRef.get(ChannelImpl.class);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /* -- Protected methods -- */
    
    /**
     * When this transaction commits, sends the given {@code
     * channelMessage} from this channel's server to all channel
     * members according to this channel's delivery requirement.
     *
     * @param	channelMessage a channel message
     */
    protected abstract void sendToAllMembers(final byte[] channelMessage);

    /**
     * Send a protocol message to the specified session when the
     * transaction commits.
     */
    protected void sendProtocolMessageOnCommit(
	ClientSession session, byte[] message)
    {
	ChannelServiceImpl.getClientSessionService().sendProtocolMessage(
	    session, message, delivery);
    }

    /**
     * Runs the specified task on transaction commit.
     */
    protected void runTaskOnCommit(ClientSession session, Runnable task) {
	ChannelServiceImpl.getClientSessionService().runTask(session, task);
    }

    /**
     * Returns a MessageBuffer containing a SESSION_MESSAGE protocol
     * with the specified message context.
     */
    protected byte[] getChannelMessage(byte[] message) {

        MessageBuffer buf = new MessageBuffer(13 + message.length);
        buf.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
            putByte(SimpleSgsProtocol.SESSION_MESSAGE).
            putLong(nextSequenceNumber()). // this sequence number is bogus
	    putByteArray(message);

        return buf.getBuffer();
    }

    /**
     * Returns the local node's ID.
     */
    protected long getLocalNodeId() {
	return ChannelServiceImpl.getLocalNodeId();
    }

    /**
     * Returns a set of node ID for channel servers with sessions that
     * are members of this channel.
     */
    protected Set<Long> getChannelServerNodeIds() {
	return servers.keySet();
    }
    
    /**
     * Returns the channel server for the specified {@code nodeId} or
     * throws {@code IllegalArgumentException} of this channel has no
     * members connected to the specified node.
     */
    protected ChannelServer getChannelServer(long nodeId) {
	ChannelServer server = servers.get(nodeId);
	if (server == null) {
	    throw new IllegalArgumentException(
		"node has no channel members: " + nodeId);
	}
	return server;
    }

    /**
     * Returns a set of client sessions that are members of this
     * channel and are connected to the node with the specified {@code
     * nodeId}.
     */
    protected Set<ClientSession> getSessions(long nodeId) {
	Iterator<ClientSession> iter =
	    new ClientSessionIterator(dataService,getSessionNodePrefix(nodeId));
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	while (iter.hasNext()) {
	    sessions.add(iter.next());
	}
	return sessions;
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public Delivery getDeliveryRequirement() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDeliveryRequirement returns {0}", delivery);
	}
	return delivery;
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
	    if (!addSession(session)) {
		// session already added
		return;
	    }

	    // TBD: enqueue join request.
	    
	    logger.log(Level.FINEST, "join session:{0} returns", session);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
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

	    if (!hasSession(session)) {
		return;
	    }

	    /*
	     * Remove session from channel state.
	     */
	    removeSession(session);
	    
	    // TBD: enqueue leave reequest.
	    
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
	    if (!hasSessions()) {
		return;
	    }

	    /*
	     * Send 'leave' message to all client sessions connected
	     * to this node.
	     */
	    long localNodeId = getLocalNodeId();
	    // TBD: enqueue leaveAll request
	    /*
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
	    */
	    /*
	     * Notify all non-local channel servers that all members
	     * have left the channel, and, for a given channel server,
	     * that the member sessions connected to that channel
	     * server's node were removed from the channel and need to
	     * be sent a 'leave' protocol message.
	     */
	    /*
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
	    */
	    /*
	     * Remove all client sessions from this channel.
	     */
	    removeAllSessions();
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
    /*
    private static byte[][] getSessionIds(Set<ClientSession> sessions) {
	byte[][] sessionIds = new byte[sessions.size()][];
	int i = 0;
	for (ClientSession session : sessions) {
	    sessionIds[i++] = session.getSessionId().getBytes();
	}
	return sessionIds;
    }
    */
    
    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	String sessionPrefix = getSessionPrefix();
	boolean hasSessions =
	    dataService.nextServiceBoundName(getSessionPrefix()).
	        startsWith(sessionPrefix);
	logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Iterator<ClientSession> getSessions() {
	checkClosed();
	return new ClientSessionIterator(dataService, getSessionPrefix());
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
			   this, HexDumper.format(message));
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send channel:{0} message:{1} throws",
		    this, HexDumper.format(message));
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void close() {
	checkContext();
	if (!isClosed) {
	    leaveAll();
	    dataService.removeObject(this);
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
	     Arrays.equals(((ChannelImpl) obj).channelIdBytes, channelIdBytes));
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return Arrays.hashCode(channelIdBytes);
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() +
	    "[" + HexDumper.toHexString(channelIdBytes) + "]";
    }

    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	dataService = ChannelServiceImpl.getDataService();
    }

    /* -- Binding prefix/key methods -- */

    /**
     * Returns the prefix for accessing all client sessions on this
     * channel.  The prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelId>.
     */
    private String getSessionPrefix() {
	return PKG_NAME +
	    SESSION_COMPONENT + HexDumper.toHexString(channelIdBytes) + ".";
    }

    /**
     * Returns the prefix for accessing all the client sessions on
     * this channel that are connected to the node with the specified
     * {@code nodeId}.  The prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelId>.<nodeId>.
     */
    private String getSessionNodePrefix(long nodeId) {
	return getSessionPrefix() + nodeId + ".";
    }

    /**
     * Returns a key for accessing the specified {@code session} as a
     * member of this channel.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelId>.<nodeId>.<sessionId>
     */
    private String getSessionKey(ClientSession session) {
	return getSessionKey(
	    getNodeId(session), session.getSessionId().getBytes());
    }

    /**
     * Returns a key for accessing the member session with the
     * specified {@code sessionIdBytes} and is connected to the node
     * with the specified {@code nodeId}.  The key has the following
     * form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelId>.<nodeId>.<sessionId>
     */
    private String getSessionKey(long nodeId, byte[] sessionIdBytes) {
	return getSessionNodePrefix(nodeId) +
	    HexDumper.toHexString(sessionIdBytes);
    }

    /**
     * Returns the prefix for accessing channel sets for all sessions
     * connected to the node with the specified {@code nodeId}.  The
     * prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		set.<nodeId>.
     */
    private static String getChannelSetPrefix(long nodeId) {
	return PKG_NAME +
	    SET_COMPONENT + nodeId + ".";
    }
    
    /**
     * Returns a key for accessing the channel set for the specified
     * {@code session}.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		set.<nodeId>.<sessionId>
     */
    private static String getChannelSetKey(ClientSession session) {
	return getChannelSetKey(
	    getNodeId(session), session.getSessionId().getBytes());
    }

    /**
     * Returns a key for accessing the channel set for the session
     * with the specified {@code sessionIdBytes} that is connected to
     * the node with the specified {@code nodeId}.  The key has the
     * following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		set.<nodeId>.<sessionId>
     */
    private static String getChannelSetKey(long nodeId, byte[] sessionIdBytes) {
	return getChannelSetPrefix(nodeId) +
	    HexDumper.toHexString(sessionIdBytes);
    }
    
    /**
     * Returns the node ID for the specified  {@code session}.
     */
    static long getNodeId(ClientSession session) {
	if (session instanceof ClientSessionImpl) {
	    return ((ClientSessionImpl) session).getNodeId();
	} else {
	    throw new IllegalArgumentException(
		"unknown session type: " + session.getClass());
	}
    }
    
    /* -- Other methods -- */

    /**
     * Checks that this channel's context is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	ChannelServiceImpl.checkContext();
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
     * If the specified {@code session} is not already a member of
     * this channel, adds the session to this channel and
     * returns {@code true}; otherwise if the specified {@code
     * session} is already a member of this channel, returns {@code
     * false}.
     *
     * @return	{@code true} if the session was added to the channel,
     *		and {@code false} if the session is already a member
     */
    private boolean addSession(ClientSession session) {
	/*
	 * If client session is already a channel member, return false
	 * immediately.
	 */
	if (hasSession(session)) {
	    return false;
	}

	/*
	 * If client session is first session on a new node for this
	 * channel, then add [node, channel server] pair to servers map.
	 */
	long nodeId = getNodeId(session);
	if (! hasServerNode(nodeId)) {
	    String channelServerKey =
		ChannelServiceImpl.getChannelServerKey(nodeId);
	    try {
		ChannelServer server =
		    dataService.getServiceBinding(
			channelServerKey, ChannelServerWrapper.class).get();
		dataService.markForUpdate(this);
		servers.put(nodeId, server);
		    
	    } catch (NameNotBoundException e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "Channel server for node:{0} not bound", nodeId);
		throw e;
	    } catch (ObjectNotFoundException e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "ChannelServerWrapper binding:{0} exists, " +
		    "but object removed", channelServerKey);
		throw e;
	    }
	}

	/*
	 * Add session binding.
	 */
	String sessionKey = getSessionKey(session);
	dataService.setServiceBinding(
	    sessionKey, new ClientSessionInfo(dataService, session));
	
	/*
	 * Add channel to session's channel set.
	 */
	String channelSetKey = getChannelSetKey(session);
	ChannelSet channelSet;
	try {
	    channelSet =
		dataService.getServiceBinding(channelSetKey, ChannelSet.class);
	} catch (NameNotBoundException e) {
	    channelSet = new ChannelSet(dataService, session);
	    dataService.setServiceBinding(channelSetKey, channelSet);
	} catch (ObjectNotFoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"ChannelSet binding:{0} exists, but object removed",
		channelSetKey);
	    throw e;
	}
	if (channelSet.add(this)) {
	    dataService.markForUpdate(channelSet);
	}
	return true;
    }

    /**
     * Removes the specified member {@code session} from this channel,
     * and returns {@code true} if the session was a member of this
     * channel when this method was invoked and {@code false} otherwise.
     */
    private boolean removeSession(ClientSession session) {
	return removeSession(
	    getNodeId(session), session.getSessionId().getBytes());
    }

    /**
     * Removes from this channel the member session with the specified
     * {@code sessionIdBytes} that is connected to the node with the
     * specified {@code nodeId}, and returns {@code true} if the
     * session was a member of this channel when this method was
     * invoked and {@code false} otherwise.
     */
    private boolean removeSession(long nodeId, byte[] sessionIdBytes) {
	// Remove session binding.
	String sessionKey = getSessionKey(nodeId, sessionIdBytes);
	try {
	    dataService.removeServiceBinding(sessionKey);
	} catch (NameNotBoundException e) {
	    return false;
	}

	/*
	 * If the specified session is the last one on its node to be
	 * removed from this channel, then remove the session's node
	 * from the server map.
	 */
	if (! hasSessionsOnNode(nodeId)) {
	    dataService.markForUpdate(this);
	    servers.remove(nodeId);
	}

	/*
	 * Remove channel from session's channel set.  If the channel
	 * is the last one to be removed from the session's channel
	 * set, then remove the channel set object and binding.
	 */
	try {
	    String channelSetKey = getChannelSetKey(nodeId, sessionIdBytes);
	    ChannelSet channelSet =
		dataService.getServiceBinding(channelSetKey, ChannelSet.class);
	    boolean removed = channelSet.remove(this);
	    if (channelSet.isEmpty()) {
		dataService.removeServiceBinding(channelSetKey);
		dataService.removeObject(channelSet);
	    } else if (removed) {
		dataService.markForUpdate(channelSet);
	    }
		  
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Channel set for session:{0} prematurely removed",
		HexDumper.toHexString(sessionIdBytes));
	}
	return true;
    }

    /**
     * Removes the specified client {@code session} from all channels
     * that it is currently a member of.  This method is invoked when
     * a session is disconnected from this node, gracefully or
     * otherwise, or if this node is recovering for a failed node
     * whose sessions all became disconnected.
     *
     * This method should be call within a transaction.
     */
    static void removeSessionFromAllChannels(ClientSession session) {
	long nodeId = getNodeId(session);
	byte[] sessionIdBytes = session.getSessionId().getBytes();
	removeSessionFromAllChannels(nodeId, sessionIdBytes);
    }

    static void removeSessionFromAllChannels(
	long nodeId, byte[] sessionIdBytes)
    {
	Set<byte[]> channelIds = getChannelsForSession(nodeId, sessionIdBytes);
	for (byte[] channelIdBytes : channelIds) {
	    try {
		ChannelImpl channel = getInstance(channelIdBytes);
		channel.removeSession(nodeId, sessionIdBytes);
	    } catch (NameNotBoundException e) {
		logger.logThrow(Level.FINE, e, "channel already removed:{0}",
				HexDumper.toHexString(channelIdBytes));
	    }
	}
    }

    /**
     * Returns a set containing the IDs of each channel that the
     * specified client session is a member of.
     */
    private static Set<byte[]> getChannelsForSession(
		long nodeId, byte[] sessionIdBytes)
    {
	DataService dataService = ChannelServiceImpl.getDataService();
	ChannelSet channelSet = null;
	
	try {
	    String channelSetKey = getChannelSetKey(nodeId, sessionIdBytes);
	    channelSet =
		dataService.getServiceBinding(channelSetKey, ChannelSet.class);
	} catch (NameNotBoundException e) {
	    // ignore; session may not be a member of any channel
	}

	return
	    (channelSet != null) ?
	    channelSet.getChannelIds() :
	    new HashSet<byte[]>();
    }

    /**
     * Removes all sessions from this channel and clears the list of
     * channel servers for this channel.  This method should be called
     * when all sessions leave the channel.
     */
    private void removeAllSessions() {
	for (String sessionKey : 
		 BoundNamesUtil.getServiceBoundNamesIterable(
		    dataService, getSessionPrefix()))
	{
	    ClientSessionInfo sessionInfo =
		dataService.getServiceBinding(
		    sessionKey, ClientSessionInfo.class);
	    removeSession(sessionInfo.nodeId, sessionInfo.sessionIdBytes);
	}
	dataService.markForUpdate(this);
	servers.clear();
    }

    /**
     * Returns the next sequence number and marks this channel for
     * update.  This method must be called within a transaction.
     */
    private long nextSequenceNumber() {
	dataService.markForUpdate(this);
	return seq++;
    }

    /**
     * Returns {@code true} if the specified client {@code session} is
     * a member of this channel.
     */
    private boolean hasSession(ClientSession session) {
	return getClientSessionInfo(session) != null;
    }

    /**
     * Returns {@code true} if this channel has any members connected
     * to the node with the specified {@code nodeId}.
     */
    private boolean hasSessionsOnNode(long nodeId) {
	String keyPrefix = getSessionNodePrefix(nodeId);
	return dataService.nextServiceBoundName(keyPrefix).
	    startsWith(keyPrefix);
    }

    /**
     * Returns {@code true} if the specified client {@code session}
     * would be the first session to join this channel on a new node,
     * otherwise returns {@code false}.
     */
    private boolean hasServerNode(long nodeId) {
	return servers.containsKey(nodeId);
    }

    /**
     * Returns and iterator for the clients sessions connected to the
     * specified node that are a member of any channel.
     */
    static Iterator<byte[]> getSessionIdsAnyChannel(
	DataService dataService, long nodeId)
    {
	return new ClientSessionIdsOnNodeIterator(dataService, nodeId);
    }
    
    /**
     * Returns the {@code ClientSessionInfo} object for the specified
     * client {@code session}.
     */
    private ClientSessionInfo getClientSessionInfo(ClientSession session) {
	String sessionKey = getSessionKey(session);
	ClientSessionInfo info = null;
	try {
	    info = dataService.getServiceBinding(
		sessionKey, ClientSessionInfo.class);
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"ClientSessionInfo binding:{0} exists, but object removed",
		sessionKey);
	}
	return info;
    }

    /* -- Other classes -- */
    
    /**
     * A {@code ManagedObject} wrapper for a {@code ClientSession}'s
     * ID.  An instance of this class also provides a means of
     * obtaining the corresponding client session if the client
     * session still exists.
     */
    private static class ClientSessionInfo
	implements ManagedObject, Serializable
    {
	private final static long serialVersionUID = 1L;
	final long nodeId;
	final byte[] sessionIdBytes;
	private long seq = 0;
	private final ManagedReference sessionRef;
	    

	/**
	 * Constructs an instance of this class with the specified
	 * {@code sessionIdBytes}.
	 */
	ClientSessionInfo(DataService dataService, ClientSession session) {
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    this.nodeId = getNodeId(session);
	    this.sessionIdBytes = session.getSessionId().getBytes();
	    this.sessionRef = dataService.createReference(session);
	}

	/**
	 * Returns the {@code ClientSession} or {@code null} if the
	 * session has been removed.
	 */
	ClientSession getClientSession() {
	    try {
		return sessionRef.get(ClientSession.class);
	    } catch (ObjectNotFoundException e) {
		return null;
	    }
	}
    }

    /**
     * Contains a set of channels (by ID) that a session is a member of.
     */
    private static class ChannelSet extends ClientSessionInfo {
	
	private final static long serialVersionUID = 1L;

	private final HashSet<IdWrapper> set = new HashSet<IdWrapper>();

	ChannelSet(DataService dataService, ClientSession session) {
	    super(dataService, session);
	}

	boolean add(ChannelImpl channel) {
	    return set.add(new IdWrapper(channel.channelIdBytes));
	}

	boolean remove(ChannelImpl channel) {
	    return set.remove(new IdWrapper(channel.channelIdBytes));
	}

	Set<byte[]> getChannelIds() {
	    HashSet<byte[]> ids = new HashSet<byte[]>();
	    for (IdWrapper idWrapper : set) {
		ids.add(idWrapper.get());
	    }
	    return ids;
	}

	boolean isEmpty() {
	    return set.isEmpty();
	}
    }

    private static class IdWrapper implements Serializable {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;
	
	private final byte[] idBytes;

	IdWrapper(byte[] idBytes) {
	    this.idBytes = idBytes;
	}

	public boolean equals(Object obj) {
	    return Arrays.equals(((IdWrapper) obj).idBytes, idBytes);
	}

	public int hashCode() {
	    return Arrays.hashCode(idBytes);
	}

	byte[] get() {
	    return idBytes;
	}
    }
    
    private static class ClientSessionIterator
	implements Iterator<ClientSession>
    {

	/** The data service. */
	protected final DataService dataService;

	/** The underlying iterator for service bound names. */
	protected final Iterator<String> iterator;

	/** The client session to be returned by {@code next}. */
	private ClientSession nextSession = null;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code dataService}.
	 */
	ClientSessionIterator(DataService dataService, String keyPrefix) {
	    this.dataService = dataService;
	    this.iterator =
		BoundNamesUtil.getServiceBoundNamesIterator(
		    dataService, keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (! iterator.hasNext()) {
		return false;
	    }
	    if (nextSession != null) {
		return true;
	    }
	    String key = iterator.next();
	    ClientSessionInfo info =
		dataService.getServiceBinding(key, ClientSessionInfo.class);
	    ClientSession session = info.getClientSession();
	    if (session == null) {
		return hasNext();
	    } else {
		nextSession = session;
		return true;
	    }
	}

	/** {@inheritDoc} */
	public ClientSession next() {
	    try {
		if (nextSession == null && ! hasNext()) {
		    throw new NoSuchElementException();
		}
		return nextSession;
	    } finally {
		nextSession = null;
	    }
	}

	/** {@inheritDoc} */
	public void remove() {
	    throw new UnsupportedOperationException("remove is not supported");
	}
    }

    private static class ClientSessionIdsOnNodeIterator
	implements Iterator<byte[]>
    {

	/** The data service. */
	protected final DataService dataService;

	/** The underlying iterator for service bound names. */
	protected final Iterator<String> iterator;

	/** The client session to be returned by {@code next}. */
	private ClientSession nextSession = null;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code dataService}.
	 */
	ClientSessionIdsOnNodeIterator(DataService dataService, long nodeId) {
	    this.dataService = dataService;
	    this.iterator =
		BoundNamesUtil.getServiceBoundNamesIterator(
 		    dataService, getChannelSetPrefix(nodeId));
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    return iterator.hasNext();
	}

	/** {@inheritDoc} */
	public byte[] next() {
	    ChannelSet channelSet =
		dataService.getServiceBinding(iterator.next(), ChannelSet.class);
	    return channelSet.sessionIdBytes;
	}

	/** {@inheritDoc} */
	public void remove() {
	    throw new UnsupportedOperationException("remove is not supported");
	}
    }
}
