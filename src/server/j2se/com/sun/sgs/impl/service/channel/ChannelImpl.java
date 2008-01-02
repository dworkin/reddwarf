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
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl.Context;
import com.sun.sgs.impl.service.session.NodeAssignment;
import com.sun.sgs.impl.service.session.IdentityAssignment;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
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
public abstract class ChannelImpl implements Channel, Serializable {

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

    /** The work queue component prefix. */
    private final static String QUEUE_COMPONENT = "eventq.";

    /** The ID from a managed reference to this instance. */
    protected final byte[] channelId;

    /** The delivery requirement for messages sent on this channel. */
    protected final Delivery delivery;

    /** The  ChannelServers that have locally connected sessions
     * that are members of this channel, keyed by node ID.
     */
    private final Set<Long> servers = new HashSet<Long>();

    /**
     * The node ID of the coordinator for this channel.  At first, it
     * is the node that the channel was created on.  If the
     * coordinator node fails, then the coordinator becomes one of the
     * member's nodes or the node performing recovery if there are no
     * members.
     */
    private long coordNodeId;

    /** The transaction. */
    private transient Transaction txn;
    
    /** The data service. */
    private transient DataService dataService;

    /** Flag that is 'true' if this channel is closed. */
    private boolean isClosed = false;

    /**
     * Constructs an instance of this class with the specified
     * {@code delivery} requirement.
     *
     * @param delivery a delivery requirement
     */
    protected ChannelImpl(Delivery delivery) {
	this.delivery = delivery;
	this.txn = ChannelServiceImpl.getTransaction();
	this.dataService = ChannelServiceImpl.getDataService();
	ManagedReference ref = dataService.createReference(this);
	this.channelId = ref.getId().toByteArray();
	this.coordNodeId = getLocalNodeId();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl:{0}",
		       HexDumper.toHexString(channelId));
	}
	dataService.setServiceBinding(
	    getEventQueueKey(), new EventQueue(this));
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
     * the {@code channelId} as a {@code ManagedReference} ID to
     * the channel's state.
     *
     * @param   channelId a channel ID byte array
     * @return  the channel with the specified {@code channelId},
     *		or {@code null} if the channel doesn't exist
     */
    static ChannelImpl getInstance(byte[] channelId) {
	try {
	    ChannelImpl channel = getObjectForId(channelId, ChannelImpl.class);
	    channel.dataService = ChannelServiceImpl.getDataService();
	    return channel;
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
            putLong(0). // this sequence number is bogus
	    putByteArray(message);

        return buf.getBuffer();
    }

    /**
     * Returns the local node's ID.
     */
    static long getLocalNodeId() {
	return ChannelServiceImpl.getLocalNodeId();
    }

    /**
     * Returns a set of node ID for channel servers with sessions that
     * are members of this channel.
     */
    protected Set<Long> getChannelServerNodeIds() {
	return servers;
    }

    private Set<ChannelServer> getChannelServers() {
	Set<ChannelServer> channelServers = new HashSet<ChannelServer>();
	for (Long nodeId : servers) {
	    channelServers.add(getChannelServer(nodeId));
	}
	return channelServers;
    }
    
    /**
     * Returns the channel server for the specified {@code nodeId}.
     */
    static ChannelServer getChannelServer(long nodeId) {
	return ChannelServiceImpl.getChannelServer(nodeId);
    }

    /**
     * Returns a set of client sessions that are members of this
     * channel and are connected to the node with the specified {@code
     * nodeId}.
     */
    protected Set<ClientSession> getSessions(long nodeId) {
	Iterator<ClientSession> iter =
	    new ClientSessionIterator(dataService, getSessionNodePrefix(nodeId));
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
    public Channel join(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }

	    /*
	     * Enqueue join request.
	     */
	    addEvent(new JoinEvent(session));
	    
	    logger.log(Level.FINEST, "join session:{0} returns", session);
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel join(final Set<ClientSession> sessions) {
	try {
	    checkClosed();
	    if (sessions == null) {
		throw new NullPointerException("null sessions");
	    }
	    
	    /*
	     * Enqueue join requests.
	     *
	     * TBD: (optimization) add a single event instead of one for
	     * each session.
	     */
	    for (ClientSession session : sessions) {
		addEvent(new JoinEvent(session));
	    }
	    logger.log(Level.FINEST, "join sessions:{0} returns", sessions);
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
	    throw e;
	}
    }

    /**
     * Adds the specified channel {@code event} to this channel's event queue
     * and schedules a non-durable task (that is performed on transaction
     * commit) to notify the coordinator that it should service the event
     * queue.
     */
    private void addEvent(ChannelEvent event) {

	/*
	 * Enqueue channel event.
	 *
	 * TBD: (optimization) if the coordinator for this channel is
	 * the local node, we could process the event here if the
	 * queue is empty (instead of enqueuing the event and
	 * notifying the coordinator to service it).
	 */
	if (getEventQueue(coordNodeId, channelId).offer(event)) {

	    /*
	     * Schedule task to send a request to this channel's
	     * coordinator to service the event queue.
	     */
	    final ChannelServer coordinator = getChannelServer(coordNodeId);
	    final long nodeId = coordNodeId;
	    ChannelServiceImpl.getTaskService().scheduleNonDurableTask(
		new AbstractKernelRunnable() {
		    public void run() {
		        try {
			    coordinator.serviceEventQueue(channelId);
			} catch (IOException e) {
			    /*
			     * It is likely that the coordinator's node failed
			     * and hasn't recovered yet.  This operation needs
			     * to be retried after a period of time to allow
			     * recovery to complete, so throw a retryable
			     * exception here.
			     *
			     * TBD: It would be nice to indicate in the
			     * exception that this task should be retried
			     * after a specific interval which relates to the
			     * recovery time period.
			     */
			    throw new ResourceUnavailableException(
				"channel coordinator node unavailable: " +
				nodeId);
			}
		    }});
	    
	} else {
	    throw new ResourceUnavailableException(
	   	"not enough resources to join");
	}
    }

    /** {@inheritDoc} */
    public Channel leave(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null client session");
	    }

	    /*
	     * Enqueue leave request.
	     */
	    addEvent(new LeaveEvent(session));
	    logger.log(Level.FINEST, "leave session:{0} returns", session);
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel leave(final Set<ClientSession> sessions) {
	try {
	    checkClosed();
	    if (sessions == null) {
		throw new NullPointerException("null sessions");
	    }

	    /*
	     * Enqueue leave requests.
	     *
	     * TBD: (optimization) add a single event instead of one for
	     * each session.
	     */
	    for (ClientSession session : sessions) {
		addEvent(new LeaveEvent(session));
	    }
	    logger.log(Level.FINEST, "leave sessions:{0} returns", sessions);
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }
    
    /** {@inheritDoc} */
    public Channel leaveAll() {
	try {
	    checkClosed();
	    
	    /*
	     * Enqueue leaveAll request.
	     */
	    addEvent(new LeaveAllEvent());
			    
	    logger.log(Level.FINEST, "leaveAll returns");
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel send(byte[] message) {
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
	    /*
	     * Enqueue send request.
	     */
	    addEvent(new SendEvent(message));

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "send channel:{0} message:{1} returns",
			   this, HexDumper.format(message));
	    }
	    return this;
	    
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
	    /*
	     * Enqueue close event.
	     */
	    addEvent(new CloseEvent());
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
	     Arrays.equals(((ChannelImpl) obj).channelId, channelId));
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return Arrays.hashCode(channelId);
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() +
	    "[" + HexDumper.toHexString(channelId) + "]";
    }

    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	txn = ChannelServiceImpl.getTransaction();
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
	    SESSION_COMPONENT + HexDumper.toHexString(channelId) + ".";
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
	return getSessionKey(getNodeId(session), getSessionIdBytes(session));
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
     * Returns a key for accessing the work queue for this channel's
     * coordinator.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		queue.<coordNodeId>.<channelId>
     */
    private String getEventQueueKey() {
	return getEventQueueKey(coordNodeId, channelId);
    }

    
    /**
     * Returns a key for accessing the work queue for the channel with
     * the specified {@code channelId} and coordinator {@code
     * nodeId}. The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		queue.<nodeId>.<channelId>
     */
    private static String getEventQueueKey(long nodeId, byte[] channelId) {
	return PKG_NAME +
	    QUEUE_COMPONENT + nodeId + "." +
	    HexDumper.toHexString(channelId);
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
	    getNodeId(session), getSessionIdBytes(session));
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
     * Returns a byte array containing the ID for the specified client
     * {@code session}.
     */
    private byte[] getManagedRefBytes(ManagedObject object) {
	ManagedReference ref = dataService.createReference(object);
	return ref.getId().toByteArray();
    }

    /**
     * Returns the ID for the specified {@code session}.
     */
    private static byte[] getSessionIdBytes(ClientSession session) {
	if (session instanceof IdentityAssignment) {
	    return ((IdentityAssignment) session).getIdBytes();
	} else {
	    throw new IllegalArgumentException(
		"session does not implement IdentityAssignment: " +
		session.getClass());
	} 
    }

    /**
     * Returns the node ID for the specified  {@code session}.
     */
    private static long getNodeId(ClientSession session) {
	if (session instanceof NodeAssignment) {
	    return ((NodeAssignment) session).getNodeId();
	} else {
	    throw new IllegalArgumentException(
		"session does not implement NodeAssignment: " +
		session.getClass());
	}
    }
    
    /* -- Other methods -- */

    /**
     * Returns the managed object with the specified {@code id} and
     * {@code type}.
     *
     * @param	<T> the type of the referenced object
     * @param	id the object's identifier (as obtained by
     *		{@link ManagedReference#getId ManagedReference.getId}
     * @param	type a class representing the type of the referenced object
     *
     * @throws	ObjectNotFoundException if the object associated with
     *		the specified {@code id} is not found
     * @throws	ClassCastException if the object associated with the
     *		specified {@code id} is not of the specified type
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    private static <T> T getObjectForId(byte[] id, Class<T> type) {
	BigInteger refId = new BigInteger(1, id);
	DataService dataService = ChannelServiceImpl.getDataService();
	ManagedReference implRef = dataService.createReferenceForId(refId);
	return implRef.get(type);
    }

    /**
     * Checks that this channel's transaction is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	ChannelServiceImpl.checkTransaction(txn);
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
	 * channel, then add server's node ID server list.
	 */
	long nodeId = getNodeId(session);
	if (! hasServerNode(nodeId)) {
	    dataService.markForUpdate(this);
	    servers.add(nodeId);
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
	return removeSession(getNodeId(session), getSessionIdBytes(session));
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
	byte[] sessionIdBytes = getSessionIdBytes(session);
	removeSessionFromAllChannels(nodeId, sessionIdBytes);
    }

    /**
     * Removes the client session with the specified {@code
     * sessionIdBytes} that is connected to the node with the
     * specified {@code nodeId} from all channels that it is currently
     * a member of.  This method is invoked when a session is
     * disconnected from this node, gracefully or otherwise, or if
     * this node is recovering for a failed node whose sessions all
     * became disconnected.
     *
     * This method should be call within a transaction.
     */
    static void removeSessionFromAllChannels(
	long nodeId, byte[] sessionIdBytes)
    {
	Set<byte[]> channelIds = getChannelsForSession(nodeId, sessionIdBytes);
	for (byte[] channelId : channelIds) {
	    try {
		ChannelImpl channel = getInstance(channelId);
		channel.removeSession(nodeId, sessionIdBytes);
	    } catch (NameNotBoundException e) {
		logger.logThrow(Level.FINE, e, "channel already removed:{0}",
				HexDumper.toHexString(channelId));
	    }
	}
    }

    /**
     * Returns a set containing the IDs of each channel that the
     * client session (specified by {@code nodeId} and {@code
     * sessionIdBytes} is a member of.
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
     * Removes all sessions from this channel and then removes the
     * channel object from the data store.  This method should be called
     * when the channel is closed.
     */
    private void removeChannel() {
	removeAllSessions();
	dataService.removeObject(this);
    }

    /**
     * Returns an iterator for the sessions that are joined to this
     * channel.  This method is for testing purposes only.
     */
    public Iterator<ClientSession> getSessions() {
	checkClosed();
	return new ClientSessionIterator(dataService, getSessionPrefix());
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
	return servers.contains(nodeId);
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
	private final ManagedReference sessionRef;
	    

	/**
	 * Constructs an instance of this class with the specified
	 * {@code sessionIdBytes}.
	 */
	ClientSessionInfo(DataService dataService, ClientSession session) {
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    nodeId = getNodeId(session);
	    sessionRef = dataService.createReference(session);
	    sessionIdBytes = sessionRef.getId().toByteArray();
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

	/** The set of channel IDs that the client session is a member of.
	 *
	 * TBD: this could be a set of BigInteger instead, and the IdWrapper
	 * class can be eliminated.
	 */
	private final Set<IdWrapper> set = new HashSet<IdWrapper>();

	ChannelSet(DataService dataService, ClientSession session) {
	    super(dataService, session);
	}

	boolean add(ChannelImpl channel) {
	    return set.add(new IdWrapper(channel.channelId));
	}

	boolean remove(ChannelImpl channel) {
	    return set.remove(new IdWrapper(channel.channelId));
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

    /**
     * Wraps a client session ID byte array so that it can be used as
     * a key in a hashtable.
     */
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

    /**
     * The channel's event queue.
     */
    private static class EventQueue implements ManagedObject, Serializable {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/** The managed reference to the queue's channel. */
	private final ManagedReference channelRef;
	/** The managed reference to the managed queue. */
	private final ManagedReference queueRef;
	/** The sequence number for events on this channel. */
	private long seq = 0;

	/**
	 * Constructs an event queue for the specified {@code channel}.
	 */
	EventQueue(ChannelImpl channel) {
	    channelRef = channel.dataService.createReference(channel);
	    queueRef = channel.dataService.createReference(
		new ManagedQueue<ChannelEvent>());
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.
	 */
	boolean offer(ChannelEvent event) {
	    return getQueue().offer(event);
	}

	/**
	 * Returns the channel for this queue.
	 */
	ChannelImpl getChannel() {
	    return channelRef.get(ChannelImpl.class);
	}

	/**
	 * Returns the channel ID for this queue.
	 */
	BigInteger getChannelRefId() {
	    return channelRef.getId();
	}
	
	/**
	 * Returns the managed queue object.
	 */
	@SuppressWarnings("unchecked")
	ManagedQueue<ChannelEvent> getQueue() {
	    return queueRef.get(ManagedQueue.class);
	}

	/**
	 * Throws a retryable exception if the event queue is not in a
	 * state to process the next event (e.g., it hasn't received
	 * an expected acknowledgment that all channel servers have
	 * received a specified 'send' request).
	 */
	void checkState() {
	    // FIXME: This needs to be implemented in order to
	    // tolerate channel coordinator crash.
	}
	    
	/**
	 * Processes (at least) the first event in the queue.
	 *
	 * TBD: (optimization for all events) if the coordinator for
	 * this channel is the local node, we could short-circuit the
	 * remote invocation on the channel server proxy and instead
	 * invoke the local channel server implementation directly.
	 */
	void serviceEvent(Context context) {
	    checkState();
	    final ChannelEvent event = getQueue().poll();
	    if (event == null) {
		return;
	    }

	    logger.log(Level.FINEST, "processing event:{0}", event);

	    event.serviceEvent(context, this);
	}
    }

    /**
     * Represents an event on a channel.
     */
    private static abstract class ChannelEvent
	implements ManagedObject, Serializable
    {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/**
	 * Services this event, taken from the head of the given {@code
	 * eventQueue}, using the specified {@code context}.
	 */
	public abstract void serviceEvent(
	    Context context, EventQueue eventQueue);

    }

    /**
     * A channel join event.
     */
    private static class JoinEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	private final byte[] sessionId;

	/**
	 * Constructs a join event with the specified {@code session}.
	 */
	JoinEvent(ClientSession session) {
	    sessionId = getSessionIdBytes(session);
	}

	/** {@inheritDoc} */
	public void serviceEvent(Context context, EventQueue eventQueue) {

	    ClientSession session;
	    try {
		session = getObjectForId(sessionId, ClientSession.class);
	    } catch (ObjectNotFoundException e) {
		logger.logThrow(
		    Level.FINE, e,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    channel.addSession(session);
	    final long nodeId = getNodeId(session);
	    final ChannelServer server = getChannelServer(nodeId);
	    context.addChannelTask(eventQueue.getChannelRefId(), new Runnable() {
		public void run() {
		    try {
			server.join(channel.channelId, sessionId);
		    } catch (IOException e) {
			// TBD: what is the right thing to do here?
			logger.logThrow(
			    Level.WARNING, e,
			    "unable to contact channel server:{0} to " +
			    "handle event:{1}", nodeId, this);
			throw new RuntimeException(
			    "unable to contact server", e);
		    }
		}});
	}

	/** {@inheritDoc} */
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionId);
	}
    }

    /**
     * A channel leave event.
     */
    private static class LeaveEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	private final byte[] sessionId;

	/**
	 * Constructs a leave event with the specified {@code session}.
	 */
	LeaveEvent(ClientSession session) {
	    sessionId = getSessionIdBytes(session);
	}

	/** {@inheritDoc} */
	public void serviceEvent(Context context, EventQueue eventQueue) {

	    ClientSession session;
	    try {
		session = getObjectForId(sessionId, ClientSession.class);
	    } catch (ObjectNotFoundException e) {
		logger.logThrow(
		    Level.FINE, e,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    channel.removeSession(session);
	    final ChannelServer server = getChannelServer(getNodeId(session));
	    context.addChannelTask(eventQueue.getChannelRefId(), new Runnable() {
		public void run() {
		    try {
			server.leave(channel.channelId, sessionId);
		    } catch (IOException e) {
			throw new RuntimeException(
			"unable to contact server", e);
		    }
		}});
	}

	/** {@inheritDoc} */
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionId);
	}
    }
    
    /**
     * A channel leaveAll event.
     */
    private static class LeaveAllEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/**
	 * Constructs a leaveAll event.
	 */
	LeaveAllEvent() {
	}

	/** {@inheritDoc} */
	public void serviceEvent(Context context, EventQueue eventQueue) {

	    final ChannelImpl channel = eventQueue.getChannel();
	    channel.removeAllSessions();
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    context.addChannelTask(eventQueue.getChannelRefId(), new Runnable() {
		public void run() {
		    for (ChannelServer server : servers) {
			try {
			    server.leaveAll(channel.channelId);
			} catch (IOException e) {
			    throw new RuntimeException(
				"unable to contact server", e);
			}
		    }
		}});
	}

	/** {@inheritDoc} */
	public String toString() {
	    return getClass().getName();
	}
    }
    
    /**
     * A channel send event.
     */
    private static class SendEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	private final byte[] message;
	/**
	 * Constructs a send event with the given {@code message}.
	 */
	SendEvent(byte[] message) {
	    this.message = message;
	}

	/** {@inheritDoc} */
	public void serviceEvent(Context context, EventQueue eventQueue) {

	    /*
	     * TBD: (optimization) this should handle sending
	     * multiple messages to a given channel.  Here, we
	     * could peek at the next event in the queue, and if
	     * it is a send, that event could be batched with this
	     * send event.  This could be repeated for multiple
	     * send events appearing in the queue.
	     */
	    final ChannelImpl channel = eventQueue.getChannel();
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    final byte[] channelMessage = channel.getChannelMessage(message);
	    context.addChannelTask(eventQueue.getChannelRefId(), new Runnable() {
		public void run() {
		    for (ChannelServer server : servers) {
			try {
			    server.send(channel.channelId, channelMessage);
			} catch (IOException e) {
			    throw new RuntimeException(
				"unable to contact server", e);
			}
			// TBD: need to update queue that all
			// channel servers have been notified of
			// the 'send'.
		    }
		}});
	}

	/** {@inheritDoc} */
	public String toString() {
	    return getClass().getName();
	}
    }
    
    /**
     * A channel close event.
     */
    private static class CloseEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

	/**
	 * Constructs a close event.
	 */
	CloseEvent() {
	}

	/** {@inheritDoc} */
	public void serviceEvent(Context context, EventQueue eventQueue) {

	    final ChannelImpl channel = eventQueue.getChannel();
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    channel.removeChannel();
	    context.addChannelTask(eventQueue.getChannelRefId(), new Runnable() {
		public void run() {
		    for (ChannelServer server : servers) {
			try {
			    server.close(channel.channelId);
			} catch (IOException e) {
			    throw new RuntimeException(
				"unable to contact server", e);
			}
		    }
		}});
	}

	/** {@inheritDoc} */
	public String toString() {
	    return getClass().getName();
	}
    }
    
    /**
     * Returns the event queue for the channel that has the specified
     * {@code channelId} and coordinator {@code nodeId}.
     */
    private static EventQueue getEventQueue(long nodeId, byte[] channelId) {
	String eventQueueKey = getEventQueueKey(nodeId, channelId);
	DataService dataService = ChannelServiceImpl.getDataService();
	try {
	    return
		dataService.getServiceBinding(eventQueueKey, EventQueue.class);
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Event queue binding:{0} does not exist", eventQueueKey);
	    return null;
	} catch (ObjectNotFoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"Event queue binding:{0} exists, but object is removed",
		eventQueueKey);
	    throw e;
	}
    }

    /**
     * Services the event queue for the channel with the specified {@code
     * channelId}.
     */
    static void serviceEventQueue(byte[] channelId, Context context) {
	EventQueue eventQueue = getEventQueue(getLocalNodeId(), channelId);
	if (eventQueue != null) {
	    eventQueue.serviceEvent(context);
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
	 * {@code dataService} and {@code keyPrefix}.
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
	 * {@code dataService} and {@code nodeId}.
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
