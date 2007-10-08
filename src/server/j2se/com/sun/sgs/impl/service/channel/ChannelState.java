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
import com.sun.sgs.app.NameNotBoundException;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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

    private static final String SET_COMPONENT = "set.";

    private static final String SESSION_COMPONENT = "session.";

    private static final String LISTENER_COMPONENT = "listener.";    

    /** The name of this channel. */
    final String name;

    /** The ID from a managed reference to this instance. */
    private final byte[] idBytes;

    /** The channel ID for this instance, constructed from {@code idBytes}. */
    transient CompactId id;

    /** The listener for this channel, or null. */
    private WrappedSerializable<ChannelListener> channelListener;

    /** The delivery requirement for messages sent on this channel. */
    final Delivery delivery;

    /** The  ChannelServers that have locally connected sessions
     * that are members of this channel, keyed by node ID.
     */
    private final Map<Long, ChannelServer> servers =
	new HashMap<Long, ChannelServer>();

    /**
     * Constructs an instance of this class with the specified {@code name},
     * {@code listener}, and {@code delivery} requirement.
     */
    ChannelState(String name, ChannelListener listener, Delivery delivery,
		 DataService dataService)
    {
	this.name = name;
	this.channelListener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	this.delivery = delivery;
	ManagedReference ref = dataService.createReference(this);
	idBytes = ref.getId().toByteArray();
	id = new CompactId(idBytes);
    }

    /**
     * Returns the channel ID in a byte array.
     */
    byte[] getIdBytes() {
	return idBytes;
    }

    /**
     * Returns an iterator for {@code ClientSession}s that are a
     * member of this channel to be retrieved from the specified
     * {@code dataService}.  The returned iterator does not support
     * the {@code remove} operation.  This method should only be
     * called within a transaction, and the returned iterator should
     * only be used within that transaction.
     */
    Iterator<ClientSession> getSessionIterator(DataService dataService) {
	return new ClientSessionIterator(dataService, getSessionPrefix());
    }

    /**
     * Returns a set of client sessions that are members of this
     * channel and are connected to the node with the specified {@code
     * nodeId}.
     */
    Set<ClientSession> getSessions(DataService dataService, long nodeId) {
	Iterator<ClientSession> iter =
	    new ClientSessionIterator(dataService, getSessionNodePrefix(nodeId));
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
     * Returns {@code true} if this channel has at least one channel listener,
     * either a global channel listener or a per-session channel
     * listener for any member session.
     */
    boolean hasChannelListeners() {
	// FIXME: this should apply to per-session listeners as well.
	return channelListener != null;
    }

    /**
     * Returns {@code true} if the specified client {@code session}
     * would be the first session to join this channel on a new node,
     * otherwise returns {@code false}.
     */
    boolean hasSessionsOnNode(long nodeId) {
	return servers.containsKey(nodeId);
    }

    /**
     * Returns a collection of channel servers with sessions that are
     * members of this channel, excluding the channel server for the
     * specified {@code localNodeId}.
     */
    Collection<ChannelServer> getNonLocalChannelServers(long localNodeId) {
	if (! servers.containsKey(localNodeId)) {
	    return servers.values();
	} else {
	    Set<ChannelServer> nonLocalServers = new HashSet<ChannelServer>();
	    for (long nodeId : servers.keySet()) {
		if (nodeId != localNodeId) {
		    nonLocalServers.add(servers.get(nodeId));
		}
	    }
	    return nonLocalServers;
	}
    }

    Set<Long> getNonLocalChannelServerNodes(long localNodeId) {
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
    boolean hasSession(DataService dataService, ClientSession session) {
	String sessionKey = getSessionKey(session);
	try {
	    dataService.getServiceBinding(sessionKey, ClientSession.class);
	    return true;
	} catch (NameNotBoundException e) {
	    return false;
	}
    }

    /**
     * Returns {@code true} if this channel has any members.
     */
    boolean hasSessions(DataService dataService) {
	return dataService.nextServiceBoundName(getSessionPrefix()) != null;
    }

    /**
     * Returns {@code true} if this channel has any members connected
     * to the node with the specified {@code nodeId}.
     */
    boolean hasSessionsOnNode(DataService dataService, long nodeId) {
	String keyPrefix = getSessionNodePrefix(nodeId);
	return dataService.nextServiceBoundName(keyPrefix) != null;
    }

    /**
     * If the specified {@code session} is not already a member of
     * this channel, adds the session with the specified {@code
     * listener} (which can be {@code null}) to this channel and
     * returns {@code true}; otherwise if the specified {@code
     * session} is already a member of this channel, returns {@code
     * false}.
     *
     * @returns	{@code true} if the session was added to the channel,
     *		and {@code false} if the session is already a member
     */
    boolean addSession(DataService dataService,
		       ClientSession session,
		       ChannelListener listener)
    {
	/*
	 * If client session is already a channel member, return false
	 * immediately.
	 */
	String sessionKey = getSessionKey(session);
	try {
	    dataService.getServiceBinding(sessionKey, ClientSession.class);
	    return false;
	} catch (NameNotBoundException e) {
	}

	/*
	 * If client session is first session on a new node for this
	 * channel, then add [node, channel server] pair to servers map.
	 */
	long nodeId = getNodeId(session);
	if (! servers.containsKey(nodeId)) {
	    try {
		String channelServerKey =
		    ChannelServiceImpl.getChannelServerKey(nodeId);
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
	    }
	}

	/*
	 * Add session binding, and listener binding if listener is
	 * non-null.
	 */
	dataService.setServiceBinding(sessionKey, (ManagedObject) session);
	if (listener != null) {
	    ManagedObject managedObject =
		(listener instanceof ManagedObject) ?
		(ManagedObject) listener :
		new ListenerWrapper(listener);
	    dataService.setServiceBinding(
		getListenerKey(session), managedObject);
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
	}
	dataService.markForUpdate(channelSet);
	channelSet.add(name);
	return true;
    }

    /**
     * Note: This method should be invoked on the node that the session is
     * connected to.
     */
    boolean removeSession(DataService dataService, ClientSession session) {
	// Remove session binding.
	String sessionKey = getSessionKey(session);
	try {
	    dataService.removeServiceBinding(sessionKey);
	} catch (NameNotBoundException e) {
	    return false;
	}

	/*
	 * Remove listener binding, if any, and listener's wrapper, if any.
	 */
	try {
	    String listenerKey = getListenerKey(session);
	    ManagedObject obj =
		dataService.getServiceBinding(listenerKey, ManagedObject.class);
	    dataService.removeServiceBinding(listenerKey);
	    if (obj instanceof ListenerWrapper) {
		dataService.removeObject(obj);
	    }
	} catch (NameNotBoundException e) {
	    // Ignore: no per-session channel listener.
	}

	/*
	 * If the specified session is the last one on its node to be
	 * removed from this channel, then remove the session's node
	 * from the server map.
	 */
	long nodeId = getNodeId(session);
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
	    String channelSetKey = getChannelSetKey(session);
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
		"Channel set for session:{0} prematurely removed", session);
	}
	return true;
    }

    /**
     * Removes all sessions from this channel and clears the list of
     * channel servers for this channel.  This method should be called
     * when all sessions leave the channel.
     */
    void removeAllSessions(DataService dataService) {
	Iterator<ClientSession> iter = getSessionIterator(dataService);
	while (iter.hasNext()) {
	    removeSession(dataService, iter.next());
	}
	dataService.markForUpdate(this);
	servers.clear();
    }

    /**
     * Removes all sessions from this channel, clears the list of
     * channel servers for this channel, and removes the per-channel
     * listener (if any).  This method should be called when the
     * channel is closed.
     */
    void removeAll(DataService dataService) {
	removeAllSessions(dataService);
	if (channelListener != null) {
	    channelListener.remove();
	}
	channelListener = null;
    }

    ChannelListener getListener() {
	return
	    channelListener != null  ?
	    channelListener.get(ChannelListener.class) :
	    null;
    }

    ChannelListener getListener(ClientSession session) {
	// FIXME: not implemented
	throw new AssertionError("not implemented");
	/*
	WrappedSerializable<ChannelListener> listener =
	    listeners.get(session);
	return
	    listener != null ?
	    listener.get(ChannelListener.class) :
	    null;
	*/
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
	    SESSION_COMPONENT + HexDumper.toHexString(idBytes) + ".";
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
	return getSessionPrefix() +
	    getNodeId(session) + "." + session.getSessionId();
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
	return PKG_NAME +
	    LISTENER_COMPONENT + HexDumper.toHexString(idBytes) +
	    getNodeId(session) + "." + session.getSessionId();
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
	    SET_COMPONENT + nodeId;
    }
    /**
     * Returns a key for accessing the channel set for the specified
     * {@code session}.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		set.<nodeId>.<sessionId>
     */
    private static String getChannelSetKey(ClientSession session) {
	return PKG_NAME +
	    SET_COMPONENT +
	    getNodeId(session) + "." + session.getSessionId();
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
	id = new CompactId(idBytes);
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
    private static class ChannelSet implements ManagedObject, Serializable {
	private final static long serialVersionUID = 1L;

	private final HashSet<String> set = new HashSet<String>();
	private final ManagedReference sessionRef;

	ChannelSet(DataService dataService, ClientSession session) {
	    sessionRef = dataService.createReference((ManagedObject) session);
	}

	void add(String channelName) {
	    set.add(channelName);
	}

	void remove(String channelName) {
	    set.remove(channelName);
	}

	ClientSession getClientSession() {
	    return sessionRef.get(ClientSession.class);
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
	protected Iterator<String> iterator;

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
	    return iterator.hasNext();
	}

	/** {@inheritDoc} */
	public ClientSession next() {
	    String key = iterator.next();
	    return dataService.getServiceBinding(key, ClientSession.class);
	}

	/** {@inheritDoc} */
	public void remove() {
	    throw new UnsupportedOperationException("remove is not supported");
	}
    }

    private static class ClientSessionOnNodeIterator
	extends ClientSessionIterator
    {

	/**
	 * Constructs an instance of this class with the specified
	 * {@code dataService} and {@code nodeId}.
	 */
	ClientSessionOnNodeIterator(DataService dataService, long nodeId) {
	    super(dataService, getChannelSetPrefix(nodeId));
	}

	/** {@inheritDoc} */
	public ClientSession next() {
	    String key = iterator.next();
	    ChannelSet set =
		dataService.getServiceBinding(key, ChannelSet.class);
	    return set.getClientSession();
	}

    }

    /**
     * Returns a set containing the names of each channel that the
     * specified client session is a member of.
     */
    static Set<String> getChannelsForSession(DataService dataService,
					     ClientSession session)
    {
	ChannelSet channelSet = null;
	
	try {
	    String channelSetKey = getChannelSetKey(session);
	    channelSet =
		dataService.getServiceBinding(channelSetKey, ChannelSet.class);
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Channel set for session:{0} prematurely removed", session);
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
    static Iterator<ClientSession> getSessionsAnyChannel(
	DataService dataService, long nodeId)
    {
	return new ClientSessionOnNodeIterator(dataService, nodeId);
    }
}
