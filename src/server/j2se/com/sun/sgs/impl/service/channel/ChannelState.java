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

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.WrappedSerializable;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent state of a channel.
 */
final class ChannelState implements ManagedObject, Serializable {
    
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The logger for this class. */
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelState.class.getName()));

    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel.";

    private static final String STATE_COMPONENT = "state.";

    private static final String SET_COMPONENT = "set.";

    private static final String SESSION_COMPONENT = "session.";

    private static final String LISTENER_COMPONENT = "listener.";    

    /** The name of this channel. */
    final String name;

    /** The ID from a managed reference to this instance. */
    final byte[] channelIdBytes;

    /** The channel ID for this instance, constructed from {@code channelIdBytes}. */
    transient CompactId id;

    /** The listener for this channel, or null. */
    private WrappedSerializable<ChannelListener> channelListener;

    /** The sequence number for messages from the server on this channel. */
    private long seq = 0;

    /** The delivery requirement for messages sent on this channel. */
    final Delivery delivery;

    /** The  ChannelServers that have locally connected sessions
     * that are members of this channel, keyed by node ID.
     */
    private final Map<Long, ChannelServer> servers =
	new HashMap<Long, ChannelServer>();

    /** The data service. */
    private transient DataService dataService;

    /**
     * Constructs an instance of this class with the specified {@code name},
     * {@code listener}, and {@code delivery} requirement.
     */
    private ChannelState(
		String name, ChannelListener listener, Delivery delivery)
    {
	if (name == null) {
	    throw new NullPointerException("null name");
	}
	this.name = name;
	this.channelListener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	this.delivery = delivery;
	dataService = ChannelServiceImpl.getDataService();
	ManagedReference ref = dataService.createReference(this);
	channelIdBytes = ref.getId().toByteArray();
	id = new CompactId(channelIdBytes);
    }

    /**
     * @throws	NameExistsException if a channel with the specified
     * 		{@code name} already exists
     */
    static ChannelState newInstance(DataService dataService,
		String name, ChannelListener listener, Delivery delivery)
    {
	String channelStateKey = getChannelStateKey(name);
	try {
	    dataService.getServiceBinding(channelStateKey, ChannelState.class);
	    throw new NameExistsException(name);
	} catch (NameNotBoundException e) {
	}
	
	ChannelState channelState =
	    new ChannelState(name, listener, delivery);
	dataService.setServiceBinding(channelStateKey, channelState);
	return channelState;
    }

    /**
     * @throws	NameNotBoundException if the channel doesn't exist
     */
    static ChannelState getInstance(DataService dataService, String name) {
	ChannelState channelState;
	try {
	    return
		dataService.getServiceBinding(
		    getChannelStateKey(name), ChannelState.class);
	} catch (NameNotBoundException e) {
	    throw new NameNotBoundException(name);
	}
    }

    /**
     * Returns a channel state for the channel with the specified
     * {@code channelIdByes}, or {@code null} if the channel doesn't
     * exist.  This method uses the {@code channelIdBytes} as a {@code
     * ManagedReference} ID to the channel's state.
     *
     * @param   channelIdBytes a channel ID byte array
     * @return  the channel state for the channel with the specified
     *	    {@code channelIdBytes}, or {@code null} if the channel
     *	    doesn't exist
     */
    static ChannelState getInstance(
	DataService dataService, byte[] channelIdBytes)
    {
	try {
	    BigInteger refId = new BigInteger(1, channelIdBytes);
	    ManagedReference stateRef =
		dataService.createReferenceForId(refId);
	    return stateRef.get(ChannelState.class);
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /**
     * Returns the channel ID in a byte array.
     */
    byte[] getIdBytes() {
	return channelIdBytes;
    }

    /**
     * Returns an iterator for {@code ClientSession}s that are a
     * member of this channel.  The returned iterator does not support
     * the {@code remove} operation.  This method should only be
     * called within a transaction, and the returned iterator should
     * only be used within that transaction.
     */
    Iterator<ClientSession> getSessionIterator() {
	return new ClientSessionIterator(dataService, getSessionPrefix());
    }

    /**
     * Returns a set of client sessions that are members of this
     * channel and are connected to the node with the specified {@code
     * nodeId}.
     */
    Set<ClientSession> getSessions(long nodeId) {
	Iterator<ClientSession> iter =
	    new ClientSessionIterator(dataService,getSessionNodePrefix(nodeId));
	Set<ClientSession> sessions = new HashSet<ClientSession>();
	while (iter.hasNext()) {
	    sessions.add(iter.next());
	}
	return sessions;
    }

    /**
     * Returns the channel server for the specified {@code nodeId} or
     * throws {@code IllegalArgumentException} of this channel has no
     * members connected to the specified node.
     */
    ChannelServer getChannelServer(long nodeId) {
	ChannelServer server = servers.get(nodeId);
	if (server == null) {
	    throw new IllegalArgumentException(
		"node has no channel members: " + nodeId);
	}
	return server;
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
     * Returns a set of node ID for channel servers with sessions that
     * are members of this channel.
     */
    Set<Long> getChannelServerNodeIds() {
	return servers.keySet();
    }
    
    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    ChannelState state = (ChannelState) obj;
	    return name.equals(state.name);
	}
	return false;
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return name.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + name + "]";
    }

    /* -- other methods -- */

    /**
     * Returns {@code true} if the specified client {@code session} is
     * a member of this channel.
     */
    boolean hasSession(ClientSession session) {
	return getClientSessionInfo(session) != null;
    }

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

    long nextSequenceNumber(ClientSession session) {
	assert session == null || hasSession(session);
	return
	    (session != null) ?
	    getClientSessionInfo(session).nextSequenceNumber(dataService) :
	    nextSequenceNumber();
    }

    private long nextSequenceNumber() {
	dataService.markForUpdate(this);
	return seq++;
    }

    /**
     * Returns {@code true} if this channel has any members.
     */
    boolean hasSessions() {
	String sessionPrefix = getSessionPrefix();
	return dataService.nextServiceBoundName(getSessionPrefix()).
	    startsWith(sessionPrefix);
    }

    /**
     * Returns {@code true} if this channel has any members connected
     * to the node with the specified {@code nodeId}.
     */
    boolean hasSessionsOnNode(long nodeId) {
	String keyPrefix = getSessionNodePrefix(nodeId);
	return dataService.nextServiceBoundName(keyPrefix).
	    startsWith(keyPrefix);
    }

    /**
     * If the specified {@code session} is not already a member of
     * this channel, adds the session with the specified {@code
     * listener} (which can be {@code null}) to this channel and
     * returns {@code true}; otherwise if the specified {@code
     * session} is already a member of this channel, returns {@code
     * false}.
     *
     * @return	{@code true} if the session was added to the channel,
     *		and {@code false} if the session is already a member
     */
    boolean addSession(ClientSession session, ChannelListener listener) {
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
	 * Add session binding, and listener binding if listener is
	 * non-null.
	 */
	String sessionKey = getSessionKey(session);
	dataService.setServiceBinding(
	    sessionKey, new ClientSessionInfo(dataService, session));
	if (listener != null) {
	    ManagedObject maybeWrappedListener =
		(listener instanceof ManagedObject) ?
		(ManagedObject) listener :
		new ListenerWrapper(listener);
	    dataService.setServiceBinding(
		getListenerKey(session), maybeWrappedListener);
	}
	
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
	dataService.markForUpdate(channelSet);
	channelSet.add(name);
	return true;
    }

    boolean removeSession(ClientSession session) {
	return removeSession(
	    getNodeId(session), session.getSessionId().getBytes());
    }
    
    boolean removeSession(long nodeId, byte[] sessionIdBytes) {
	// Remove session binding.
	String sessionKey = getSessionKey(nodeId, sessionIdBytes);
	try {
	    dataService.removeServiceBinding(sessionKey);
	} catch (NameNotBoundException e) {
	    return false;
	}

	/*
	 * Remove listener binding, if any, and listener's wrapper, if any.
	 */
	try {
	    String listenerKey = getListenerKey(nodeId, sessionIdBytes);
	    ManagedObject maybeWrappedListener =
		dataService.getServiceBinding(listenerKey, ManagedObject.class);
	    dataService.removeServiceBinding(listenerKey);
	    if (maybeWrappedListener instanceof ListenerWrapper) {
		dataService.removeObject(maybeWrappedListener);
	    }
	} catch (NameNotBoundException e) {
	    // Ignore: no per-session channel listener.
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
	    channelSet.remove(name);
	    if (channelSet.isEmpty()) {
		dataService.removeServiceBinding(channelSetKey);
		dataService.removeObject(channelSet);
	    } else {
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
     * Removes all sessions from this channel and clears the list of
     * channel servers for this channel.  This method should be called
     * when all sessions leave the channel.
     */
    void removeAllSessions() {
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
     * Removes all sessions from this channel, clears the list of
     * channel servers for this channel, removes the per-channel
     * listener (if any). and removes the channel state from the data
     * service.  This method should be called when the channel is
     * closed.
     */
    void closeAndRemoveState() {
	removeAllSessions();
	if (channelListener != null) {
	    channelListener.remove();
	}
	channelListener = null;

	dataService.removeServiceBinding(getChannelStateKey(name));
	dataService.removeObject(this);
    }

    ChannelListener getListener() {
	return
	    channelListener != null  ?
	    channelListener.get(ChannelListener.class) :
	    null;
    }

    ChannelListener getListener(ClientSession session) {
	String listenerKey = getListenerKey(session);
	try {
	    ManagedObject obj =
		dataService.getServiceBinding(listenerKey, ManagedObject.class);
	    return 
		(obj instanceof ListenerWrapper) ?
		((ListenerWrapper) obj).get() :
		(ChannelListener) obj;
	    
	} catch (NameNotBoundException e) {
	    return null;
	}
    }

    /**
     * Returns the key for accessing the {@code ChannelState} instance
     * for the channel with the specified {@code channelName}.
     */
    private static String getChannelStateKey(String channelName) {
	return PKG_NAME + STATE_COMPONENT + channelName;
    }

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

    private String getSessionKey(long nodeId, byte[] sessionIdBytes) {
	return getSessionNodePrefix(nodeId) +
	    HexDumper.toHexString(sessionIdBytes);
    }

    /**
     * Returns a key for accessing the channel listener for the
     * specified {@code session} as a member of this channel.
     * The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		listener.<channelId>.<nodeId>.<sessionId>
     */
    private String getListenerKey(ClientSession session) {
	return getListenerKey(
	    getNodeId(session), session.getSessionId().getBytes());
    }

    private String getListenerKey(long nodeId, byte[] sessionIdBytes) {
	return PKG_NAME +
	    LISTENER_COMPONENT + HexDumper.toHexString(channelIdBytes) +
	    nodeId + "." + HexDumper.toHexString(sessionIdBytes);
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

    private static String getChannelSetKey(long nodeId, byte[] sessionIdBytes) {
	return getChannelSetPrefix(nodeId) +
	    HexDumper.toHexString(sessionIdBytes);
    }
    
    /**
     * Returns the node ID for the specified session.
     */
    static long getNodeId(ClientSession session) {
	if (session instanceof ClientSessionImpl) {
	    return ((ClientSessionImpl) session).getNodeId();
	} else {
	    throw new IllegalArgumentException(
		"unknown session type: " + session.getClass());
	}
    }
    
    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out)
	throws IOException
    {
	out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	id = new CompactId(channelIdBytes);
	dataService = ChannelServiceImpl.getDataService();
    }

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
	    this.sessionRef =
		dataService.createReference((ManagedObject) session);
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

	long nextSequenceNumber(DataService dataService) {
	    dataService.markForUpdate(this);
	    return seq++;
	}
    }

    /**
     * A {@code ManagedObject} wrapper for a {@code ChannelListener}.
     */
    private static class ListenerWrapper
	implements ManagedObject, Serializable
    {
	private final static long serialVersionUID = 1L;
	
	private ChannelListener listener;

	ListenerWrapper(ChannelListener listener) {
	    assert listener != null && listener instanceof Serializable;
	    this.listener = listener;
	}

	ChannelListener get() {
	    return listener;
	}
    }

    /**
     * Contains a set of channels (names) that a session is a member of.
     */
    private static class ChannelSet extends ClientSessionInfo {
	
	private final static long serialVersionUID = 1L;

	private final HashSet<String> set = new HashSet<String>();

	ChannelSet(DataService dataService, ClientSession session) {
	    super(dataService, session);
	}

	void add(String channelName) {
	    set.add(channelName);
	}

	void remove(String channelName) {
	    set.remove(channelName);
	}

	Set<String> getChannelNames() {
	    return set;
	}

	boolean isEmpty() {
	    return set.isEmpty();
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

    /**
     * Returns a set containing the names of each channel that the
     * specified client session is a member of.
     */
    static Set<String> getChannelsForSession(DataService dataService,
					     long nodeId,
					     byte[] sessionIdBytes)
    {
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
	    channelSet.getChannelNames() :
	    new HashSet<String>();
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

}
