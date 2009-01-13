/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.util.ManagedObjectValueMap;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.service.session.NodeAssignment;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import static com.sun.sgs.impl.util.AbstractService.uncheckedCast;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction.
 */
abstract class ChannelImpl implements ManagedObject, Serializable {

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    protected static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel.";

    /** The channels map key. */
    private static final String CHANNELS_MAP_KEY =
	PKG_NAME + "channelsMap";

    /** The channels map key. */
    private static final String EVENT_QUEUES_MAP_KEY =
	PKG_NAME + "eventQueuesMap";

    /** The channel name component prefix. */
    private static final String NAME_COMPONENT = "name.";

    /** The channel set component prefix. */
    private static final String SET_COMPONENT = "set.";

    /** The member session component prefix. */
    private static final String SESSION_COMPONENT = "session.";

    /** The work queue component prefix. */
    private static final String QUEUE_COMPONENT = "eventq.";

    /** The random number generator for choosing a new coordinator. */
    private static final Random random = new Random();

    /** The channel name. */
    protected final String name;

    /** The ID from a managed reference to this instance. */
    protected final BigInteger channelRefId;

    /** The wrapped channel instance. */
    private final ManagedReference<ChannelWrapper> wrappedChannelRef;

    /** The reference to this channel's listener. */
    private final ManagedReference<ChannelListener> listenerRef;

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

    /** Flag that is 'true' if this channel is closed. */
    private boolean isClosed = false;

    /**
     * The maximum number of message bytes that can be queued for delivery on
     * this channel.
     */
    private final int writeBufferCapacity;

    /** The event queue. */
    private final ManagedReference<EventQueue> eventQueueRef;

    /**
     * Constructs an instance of this class with the specified
     * {@code name}, {@code listener}, {@code delivery} requirement,
     * and write buffer capacity.
     *
     * @param name a channel name
     * @param listener a channel listener
     * @param delivery a delivery requirement
     * @param writeBufferCapacity the capacity of the write buffer, in bytes
     */
    protected ChannelImpl(String name, ChannelListener listener,
			  Delivery delivery, int writeBufferCapacity)
    {
	if (name == null) {
	    throw new NullPointerException("null name");
	}
	this.name = name;
	DataService dataService = getDataService();
	if (listener != null) {
	    if (!(listener instanceof Serializable)) {
		throw new IllegalArgumentException("non-serializable listener");
	    } else if (!(listener instanceof ManagedObject)) {
		listener = new ManagedSerializableChannelListener(listener);
	    }
	    this.listenerRef = dataService.createReference(listener);
	} else {
	    this.listenerRef = null;
	}
	this.delivery = delivery;
	this.writeBufferCapacity = writeBufferCapacity;
	this.txn = ChannelServiceImpl.getTransaction();
	ManagedReference<ChannelImpl> ref = dataService.createReference(this);
	this.wrappedChannelRef =
	    dataService.createReference(new ChannelWrapper(ref));
	this.channelRefId = ref.getId();
	this.coordNodeId = getLocalNodeId();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl:{0}",
		       HexDumper.toHexString(channelRefId.toByteArray()));
	}
	getChannelsMap().put(name, this);
	EventQueue eventQueue = new EventQueue(this);
	eventQueueRef = dataService.createReference(eventQueue);
	getEventQueuesMap(coordNodeId).put(channelRefId, eventQueue);
    }

    /** Returns the data service. */
    private static DataService getDataService() {
	return ChannelServiceImpl.getDataService();
    }

    /**
     * Returns the channels map, keyed by channel name.  Creates and
     * stores the map if it doesn't already exist.
     */
    static ManagedObjectValueMap<String, ChannelImpl> getChannelsMap() {
	DataService dataService = getDataService();
	ManagedObjectValueMap<String, ChannelImpl> channelsMap;
	try {
	    channelsMap = uncheckedCast(
		dataService.getServiceBinding(CHANNELS_MAP_KEY));
	} catch (NameNotBoundException e) {
	    channelsMap =
		new BindingKeyedHashMap<String, ChannelImpl>();
	    dataService.setServiceBinding(CHANNELS_MAP_KEY, channelsMap);
	}
	return channelsMap;
    }

    /**
     * Returns the event queues map, keyed by node ID. Creates and stores the
     * top-level map if it doesn't already exist.
     */
    static ManagedObjectValueMap
	<Long, ManagedObjectValueMap<BigInteger, EventQueue>>
	getNodeToEventQueuesMap()
    {
	DataService dataService = getDataService();
	ManagedObjectValueMap
	    <Long, ManagedObjectValueMap<BigInteger, EventQueue>>
	    nodeIdToEventQueuesMap;
	try {
	    nodeIdToEventQueuesMap = uncheckedCast(
 		dataService.getServiceBinding(EVENT_QUEUES_MAP_KEY));
	} catch (NameNotBoundException e) {
	    nodeIdToEventQueuesMap =
		new BindingKeyedHashMap
		    <Long, ManagedObjectValueMap<BigInteger, EventQueue>>();
	    dataService.setServiceBinding(
		EVENT_QUEUES_MAP_KEY, nodeIdToEventQueuesMap);
	}
	return nodeIdToEventQueuesMap;
    }

    /**
     * Returns the event queues map for the specified {@code nodeId}.
     */
    private static ManagedObjectValueMap<BigInteger, EventQueue>
	getEventQueuesMap(long nodeId)
    {
	ManagedObjectValueMap<BigInteger, EventQueue> eventQueuesMap =
	    getNodeToEventQueuesMap().get(nodeId);
	if (eventQueuesMap == null) {
	    eventQueuesMap =
		new BindingKeyedHashMap<BigInteger, EventQueue>();
	    getNodeToEventQueuesMap().put(nodeId, eventQueuesMap);
	}
	return eventQueuesMap;
    }

    /* -- Factory methods -- */

    /**
     * Constructs a new {@code Channel} with the given {@code name}, {@code
     * listener}, {@code delivery} requirement and write-buffer capacity.
     */
    static Channel newInstance(String name,
			       ChannelListener listener,
			       Delivery delivery,
			       int writeBufferCapacity)
    {
	// TBD: create other channel types depending on delivery.
	return new OrderedUnreliableChannelImpl(
	    name, listener, delivery, writeBufferCapacity).getWrappedChannel();
    }

    /**
     * Returns a channel with the given {@code name}.
     */
    static Channel getInstance(String name) {
	ChannelImpl channelImpl = getChannelsMap().get(name);
	if (channelImpl != null) {
	    return channelImpl.getWrappedChannel();
	} else {
	    throw new NameNotBoundException("channel not found: " + name);
	}
    }

    /* -- Implement Channel -- */

    /** Implements {@link Channel#getName}. */
    String getName() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", name);
	}
	return name;
    }

    /** Implements {@link Channel#getDeliveryRequirement}. */
    Delivery getDeliveryRequirement() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDeliveryRequirement returns {0}", delivery);
	}
	return delivery;
    }

    /** Implements {@link Channel#hasSessions}. */
    boolean hasSessions() {
	checkClosed();
	String prefix = getSessionPrefix();
	String name = getDataService().nextServiceBoundName(prefix);
	return name != null && name.startsWith(prefix);
    }

    /** Implements {@link Channel#getSessions}. */
    Iterator<ClientSession> getSessions() {
	checkClosed();
	return new ClientSessionIterator(getSessionPrefix());
    }

    /** Implements {@link Channel#join(ClientSession)}. */
    void join(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    /*
	     * Enqueue join request with underlying (unwrapped) client
	     * session object.
	     */
	    addEvent(new JoinEvent(unwrapSession(session)));

	    logger.log(Level.FINEST, "join session:{0} returns", session);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
	    throw e;
	}
    }

    /** Implements {@link Channel#join(Set)}.
     *
     * Enqueues a join event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    void join(final Set<ClientSession> sessions) {
	try {
	    checkClosed();
	    if (sessions == null) {
		throw new NullPointerException("null sessions");
	    }

	    /*
	     * Enqueue join requests, each with underlying (unwrapped)
	     * client session object.
	     *
	     * TBD: (optimization) add a single event instead of one for
	     * each session.
	     */
	    for (ClientSession session : sessions) {
		addEvent(new JoinEvent(unwrapSession(session)));
	    }
	    logger.log(Level.FINEST, "join sessions:{0} returns", sessions);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
	    throw e;
	}
    }

    /**
     * Returns the underlying {@code ClientSession} for the specified
     * {@code session}.  Note: The client session service wraps each client
     * session object that it hands out to the application.  The channel
     * service implementation relies on the assumption that a client
     * session's {@code ManagedObject} ID is the client session's ID (used
     * for identifiying the client session, e.g. for sending messages to
     * the client session).  This method is invoked by the {@code join} and
     * {@code leave} methods in order to access the underlying {@code
     * ClientSession} so that the correct client session ID can be obtained.
     */
    private ClientSession unwrapSession(ClientSession session) {
	assert session instanceof ClientSessionWrapper;
	return ((ClientSessionWrapper) session).getClientSession();
    }

    /**
     * Adds the specified channel {@code event} to this channel's event queue
     * and notifies the coordinator that there is an event to service.  As
     * an optimization, if the local node is the coordinator for this channel
     * and the event queue is empty, then service the event immediately
     * without adding it to the event queue.
     */
    private void addEvent(ChannelEvent event) {

	EventQueue eventQueue = eventQueueRef.get();

	if (isCoordinator() && eventQueue.isEmpty()) {
	    event.serviceEvent(eventQueue);
	} else if (eventQueue.offer(event)) {
	    notifyServiceEventQueue(eventQueue);
	} else {
	    throw new ResourceUnavailableException(
	   	"not enough resources to add channel event");
	}
    }

    /**
     * If the coordinator is the local node, services events locally;
     * otherwise, schedules a task to send a request to this channel's
     * coordinator to service the event queue.
     *
     * @param	eventQueue this channel's event queue
     */
    private void notifyServiceEventQueue(EventQueue eventQueue) {

	if (isCoordinator()) {
	    eventQueue.serviceEvent();

	} else {

	    final ChannelServer coordinator = getChannelServer(coordNodeId);
	    if (coordinator == null) {
		/*
		 * If the ChannelServer for the coordinator's node has been
		 * removed, then the coordinator's node has failed and will
		 * be reassigned during recovery.  When recovery for the
		 * failed node completes, the newly chosen coordinator will
		 * restart the processing of channel events.
		 */
		return;
	    }
	    final long coord = coordNodeId;
	    final ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    final BigInteger channelRefId = eventQueue.getChannelRefId();
	    channelService.getTaskService().scheduleNonDurableTask(
	        new AbstractKernelRunnable("SendServiceEventQueue") {
		  public void run() {
		      channelService.runIoTask(
 			new IoRunnable() {
			  public void run() throws IOException {
			      coordinator.serviceEventQueue(channelRefId);
			  } }, coord);
		  }
		}, false);
	}
    }

    /** Implements {@link Channel#leave(ClientSession)}.
     *
     * Enqueues a leave event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    void leave(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null client session");
	    }

	    /*
	     * Enqueue leave request with underlying (unwrapped) client
	     * session object.
	     */
	    addEvent(new LeaveEvent(unwrapSession(session)));
	    logger.log(Level.FINEST, "leave session:{0} returns", session);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** Implements {@link Channel#leave(Set)}.
     *
     * Enqueues leave event(s) to this channel's event queue and notifies
     * this channel's coordinator to service the event(s).
     */
    void leave(final Set<ClientSession> sessions) {
	try {
	    checkClosed();
	    if (sessions == null) {
		throw new NullPointerException("null sessions");
	    }

	    /*
	     * Enqueue leave requests, each with underlying (unwrapped)
	     * client session object.
	     *
	     * TBD: (optimization) add a single event instead of one for
	     * each session.
	     */
	    for (ClientSession session : sessions) {
		addEvent(new LeaveEvent(unwrapSession(session)));
	    }
	    logger.log(Level.FINEST, "leave sessions:{0} returns", sessions);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** Implements {@link Channel#leaveAll}.
     *
     * Enqueues a leaveAll event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    void leaveAll() {
	try {
	    checkClosed();

	    /*
	     * Enqueue leaveAll request.
	     */
	    addEvent(new LeaveAllEvent());
	    logger.log(Level.FINEST, "leaveAll returns");

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** Implements {@link Channel#send}.
     *
     * Enqueues a send event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    void send(ClientSession sender, ByteBuffer message) {
	try {
	    checkClosed();
	    if (message == null) {
		throw new NullPointerException("null message");
	    }
	    message = message.asReadOnlyBuffer();
            if (message.remaining() > SimpleSgsProtocol.MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.remaining() + " > " +
                        SimpleSgsProtocol.MAX_PAYLOAD_LENGTH);
            }
	    /*
	     * Enqueue send request.
	     */
            byte[] msgBytes = new byte[message.remaining()];
            message.get(msgBytes);
	    BigInteger senderRefId =
		sender != null ?
		getSessionRefId(unwrapSession(sender)) :
		null;
	    addEvent(new SendEvent(senderRefId, msgBytes));

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "send channel:{0} message:{1} returns",
			   this, HexDumper.format(msgBytes, 0x50));
	    }

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "send channel:{0} message:{1} throws",
		    this, HexDumper.format(message, 0x50));
	    }
	    throw e;
	}
    }

    /**
     * If this channel has a null {@code ChannelListener}, forwards the
     * specified {@code message} to the channel by invoking this channel's
     * {@code send} method with the specified {@code sender} and {@code
     * message}; otherwise, invokes the {@code ChannelListener}'s {@code
     * receivedMessage} method with this channel, the specified {@code sender},
     * and {@code message}.
     */
    private void receivedMessage(ClientSession sender, ByteBuffer message) {

	assert sender instanceof ClientSessionWrapper;

	if (listenerRef == null) {
	    send(sender, message);
	} else {
	    // TBD: exception handling?
	    listenerRef.get().receivedMessage(
		getWrappedChannel(), sender, message.asReadOnlyBuffer());
	}
    }

    /**
     * Enqueues a close event to this channel's event queue and notifies
     * this channel's coordinator to service the event.  This method
     * is invoked by this channel's {@code ChannelWrapper} when the
     * application removes the wrapper object.
     */
    void close() {
	checkContext();
	if (!isClosed) {
	    /*
	     * Enqueue close event.
	     */
	    addEvent(new CloseEvent());
	    isClosed = true;
	}
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
	// TBD: Because this is a managed object, does an "==" check
	// suffice here?
	return
	    (this == obj) ||
	    (obj != null && obj.getClass() == this.getClass() &&
	     channelRefId.equals(((ChannelImpl) obj).channelRefId));
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	return channelRefId.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	return getClass().getName() +
	    "[" + HexDumper.toHexString(channelRefId.toByteArray()) + "]";
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
    }

    /* -- Binding prefix/key methods -- */

    /**
     * Returns the prefix for accessing all client sessions on this
     * channel.  The prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelRefId>.
     */
    private static String getSessionPrefix(BigInteger channelRefId) {
	return PKG_NAME +
	    SESSION_COMPONENT + HexDumper.toHexString(channelRefId.toByteArray()) + ".";
    }

    /**
     * Returns the prefix for accessing all client sessions on this
     * channel.  The prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelRefId>.
     */
    private String getSessionPrefix() {
	return getSessionPrefix(channelRefId);
    }
    
    /**
     * Returns the prefix for accessing all the client sessions on
     * this channel that are connected to the node with the specified
     * {@code nodeId}.  The prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelRefId>.<nodeId>.
     */
    private static String getSessionNodePrefix(BigInteger channelRefId, long nodeId) {
	return getSessionPrefix(channelRefId) + nodeId + ".";
    }

    /**
     * Returns a key for accessing the specified {@code session} as a
     * member of this channel.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelRefId>.<nodeId>.<sessionId>
     */
    private String getSessionKey(ClientSession session) {
	return getSessionKey(getNodeId(session), getSessionRefId(session));
    }

    /**
     * Returns a key for accessing the member session with the
     * specified {@code sessionRefId} and is connected to the node
     * with the specified {@code nodeId}.  The key has the following
     * form:
     *
     * com.sun.sgs.impl.service.channel.
     *		session.<channelRefId>.<nodeId>.<sessionId>
     */
    private String getSessionKey(long nodeId, BigInteger sessionRefId) {
	return getSessionNodePrefix(channelRefId, nodeId) +
	    HexDumper.toHexString(sessionRefId.toByteArray());
    }

    /* -- Other methods -- */

    /**
     * Returns the ID for the specified {@code session}.
     */
    private static BigInteger getSessionRefId(ClientSession session) {
	return getDataService().createReference(session).getId();
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

    /**
     * Returns the wrapped channel for this instance.
     */
    protected ChannelWrapper getWrappedChannel() {
	return wrappedChannelRef.get();
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
     * Returns {@code true} if this node is the coordinator for this
     * channel, otherwise returns {@code false}.
     */
    private boolean isCoordinator() {
	return coordNodeId == getLocalNodeId();
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
	 * channel, then add server's node ID to server list.
	 */
	long nodeId = getNodeId(session);
	if (!hasServerNode(nodeId)) {
	    getDataService().markForUpdate(this);
	    servers.add(nodeId);
	}

	/*
	 * Add session binding.
	 */
	String sessionKey = getSessionKey(session);
	DataService dataService = getDataService();
	dataService.setServiceBinding(
	    sessionKey, new ClientSessionInfo(dataService, session));

	return true;
    }

    /**
     * Removes the specified member {@code session} from this channel,
     * and returns {@code true} if the session was a member of this
     * channel when this method was invoked and {@code false} otherwise.
     */
    private boolean removeSession(ClientSession session) {
	return removeSession(getNodeId(session), getSessionRefId(session));
    }

    /**
     * Removes the binding with the given {@code sessionKey} (and the
     * associated object) and throws {@code NameNotBoundException} if the
     * key is not bound.
     */
    private void removeSessionBinding(String sessionKey) {
	DataService dataService = getDataService();
	ClientSessionInfo sessionInfo = (ClientSessionInfo)
	    dataService.getServiceBinding(sessionKey);
	dataService.removeServiceBinding(sessionKey);
	dataService.removeObject(sessionInfo);
    }

    /**
     * Removes from this channel the member session with the specified
     * {@code sessionRefId} that is connected to the node with the
     * specified {@code nodeId}, notifies the session's server that the
     * session left the channel, and returns {@code true} if the
     * session was a member of this channel when this method was
     * invoked.  If the session is not a member of this channel, then no
     * action is taken and {@code false} is returned.
     */
    private boolean removeSession(
	final long nodeId, final BigInteger sessionRefId)
    {
	// Remove session binding.
	String sessionKey = getSessionKey(nodeId, sessionRefId);
	try {
	    removeSessionBinding(sessionKey);
	} catch (NameNotBoundException e) {
	    return false;
	}

	/*
	 * If the specified session is the last one on its node to be
	 * removed from this channel, then remove the session's node
	 * from the server map.
	 */
	if (!hasSessionsOnNode(nodeId)) {
	    getDataService().markForUpdate(this);
	    servers.remove(nodeId);
	}

	final ChannelServer server = getChannelServer(nodeId);
	/*
	 * If there is no channel server for the session's node,
	 * then the session's node has failed and the session is
	 * now disconnected.  There is no need to send a 'leave'
	 * notification (to update the channel membership cache) to
	 * the failed server.
	 */
	if (server != null) {
	    ChannelServiceImpl.getChannelService().addChannelTask(
		channelRefId,
		new IoRunnable() {
		    public void run() throws IOException {
			server.leave(channelRefId, sessionRefId);
		    } },
		nodeId);
	}
	return true;
    }

    /**
     * Reassigns the channel coordinator as follows:
     *
     * 1) Reassigns the channel's coordinator from the node specified by
     * the {@code failedCoordNodeId} to another server node (if there are
     * channel members), or the local node (if there are no channel
     * members) and rebinds the event queue to the new coordinator's key.
     *
     * 2) Marks the event queue to indicate that a refresh request for this
     * channel must be sent this channel's channel servers to notify each
     * server to reread this channel's membership list (for the given
     * channel server's local node) before servicing any more events.
     *
     * 3} Finally, sends out a 'serviceEventQueue' request to the new
     * coordinator to restart this channel's event processing.
     */
    private void reassignCoordinator(long failedCoordNodeId) {
	DataService dataService = getDataService();
	dataService.markForUpdate(this);
	if (coordNodeId != failedCoordNodeId) {
	    logger.log(
		Level.SEVERE,
		"attempt to reassign coordinator:{0} for channel:{1} " +
		"that is not the failed node:{2}",
		coordNodeId, failedCoordNodeId, this);
	    return;
	}
	servers.remove(failedCoordNodeId);
	/*
	 * Assign a new coordinator, and store event queue in new
	 * coordinator's event queue map.
	 */
	coordNodeId = chooseCoordinatorNode();
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE,
		"channel:{0} reassigning coordinator from:{1} to:{2}",
		HexDumper.toHexString(channelRefId.toByteArray()),
		failedCoordNodeId,
		coordNodeId);
	}
	EventQueue eventQueue = eventQueueRef.get();
	getEventQueuesMap(coordNodeId). put(channelRefId, eventQueueRef.get());

	/*
	 * Mark the event queue to indicate that a refresh must be sent to
	 * all channel servers for this channel before servicing any events
	 * in the queue, and then send a 'serviceEventQueue' notification
	 * to the new coordinator.
	 */
	eventQueue.setSendRefresh();
	notifyServiceEventQueue(eventQueue);
    }

    /**
     * Chooses a node to be the new coordinator for this channel, and
     * returns the ID for the chosen node.  If there is one or more
     * channel server(s) for this channel that are currently alive, this
     * method chooses one of those server nodes at random to be the new
     * coordinator.  If there are no live channel servers for this channel,
     * then the local node is chosen to be the coordinator.
     *
     * This method should be called within a transaction.
     */
    private long chooseCoordinatorNode() {
	if (!servers.isEmpty()) {
	    int numServers = servers.size();
	    Long[] serverIds = servers.toArray(new Long[numServers]);
	    int startIndex = random.nextInt(numServers);
	    WatchdogService watchdogService =
		ChannelServiceImpl.getWatchdogService();
	    for (int i = 0; i < numServers; i++) {
		int tryIndex = (startIndex + i) % numServers;
		long candidateId = serverIds[tryIndex];
		Node coordCandidate = watchdogService.getNode(candidateId);
		if (coordCandidate != null && coordCandidate.isAlive()) {
		    return candidateId;
		}
	    }
	}
	return getLocalNodeId();
    }

    /**
     * Removes the client session with the specified {@code sessionRefId}
     * from the channel with the specified {@code channelRefId}.  This
     * method is invoked when a session is disconnected from this node
     * (with the specified {@code nodeId}) gracefully or otherwise, or if
     * this node is recovering for a failed node whose sessions all became
     * disconnected.  The {@code nodeId} specifies the node that the
     * session was previously connected to, which is not necessarily
     * the local node's ID.
     *
     * This method should be called within a transaction.
     */
    static void removeSessionFromChannel(
	long nodeId, BigInteger sessionRefId, BigInteger channelRefId)
    {
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    channel.removeSession(nodeId, sessionRefId);
	} else {
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "channel already removed:{0}",
		    HexDumper.toHexString(channelRefId.toByteArray()));
	    }
	}
    }

    /**
     * Returns the local node's ID.
     */
    private static long getLocalNodeId() {
	return ChannelServiceImpl.getLocalNodeId();
    }

    /**
     * Returns the channel server for the specified {@code nodeId}.
     */
    private static ChannelServer getChannelServer(long nodeId) {
	return ChannelServiceImpl.getChannelService().getChannelServer(nodeId);
    }

    /**
     * Returns the managed object with the specified {@code refId}, or {@code
     * null} if there is no object with the specified {@code refId}.
     *
     * @param	refId the object's identifier as obtained by
     *		{@link ManagedReference#getId ManagedReference.getId}
     *
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    private static Object getObjectForId(BigInteger refId) {
	try {
	    return getDataService().createReferenceForId(refId).get();
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /**
     * Returns a list containing the node IDs of the channel servers for
     * this channel. 
     */
    private List<Long> getChannelServerNodeIds() {
	return new ArrayList<Long>(servers);
    }
    
    /**
     * Removes all sessions from this channel and clears the list of
     * channel servers for this channel.  This method should be called
     * when all sessions leave the channel.
     */
    private void removeAllSessions() {
	DataService dataService = getDataService();
	for (String sessionKey :
		 BoundNamesUtil.getServiceBoundNamesIterable(
		    dataService, getSessionPrefix()))
	{
	    ClientSessionInfo sessionInfo =
		(ClientSessionInfo) dataService.getServiceBinding(sessionKey);
	    removeSession(sessionInfo.nodeId, sessionInfo.sessionRef.getId());
	}
	dataService.markForUpdate(this);
	servers.clear();
    }

    /**
     * Removes all sessions from this channel, removes the channel
     * object and its binding, the channel listener wrapper (if we
     * created a wrapper for it), and the event queue and associated
     * binding from the data store.  This method should be called when
     * the channel is closed.
     */
    private void removeChannel() {
	DataService dataService = getDataService();
	removeAllSessions();
	getChannelsMap().removeOverride(name);
	dataService.removeObject(this);
	if (listenerRef != null) {
	    ChannelListener maybeWrappedListener = listenerRef.get();
	    if (maybeWrappedListener instanceof ManagedSerializable) {
		dataService.removeObject(maybeWrappedListener);
	    }
	}
	ManagedObjectValueMap<BigInteger, EventQueue> eventQueuesMap =
	    getNodeToEventQueuesMap().get(coordNodeId);
	if (eventQueuesMap != null) {
	    eventQueuesMap.removeOverride(channelRefId);
	    if (eventQueuesMap.isEmpty()) {
		getNodeToEventQueuesMap().remove(coordNodeId);
		dataService.removeObject(eventQueuesMap);
	    }
	}
	EventQueue eventQueue = eventQueueRef.get();
	dataService.removeObject(eventQueue);
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
	String keyPrefix = getSessionNodePrefix(channelRefId, nodeId);
	return getDataService().nextServiceBoundName(keyPrefix).
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
     * Returns the {@code ClientSessionInfo} object for the specified
     * client {@code session}.
     */
    private ClientSessionInfo getClientSessionInfo(ClientSession session) {
	String sessionKey = getSessionKey(session);
	ClientSessionInfo info = null;
	try {
	    info = (ClientSessionInfo) getDataService().getServiceBinding(
		sessionKey);
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
     * An iterator for {@code ClientSessions} of a given channel.
     */
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
	ClientSessionIterator(String keyPrefix) {
	    this.dataService = getDataService();
	    this.iterator =
		BoundNamesUtil.getServiceBoundNamesIterator(
		    dataService, keyPrefix);
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (!iterator.hasNext()) {
		return false;
	    }
	    if (nextSession != null) {
		return true;
	    }
	    String key = iterator.next();
	    ChannelImpl.ClientSessionInfo info =
		(ChannelImpl.ClientSessionInfo)
		    dataService.getServiceBinding(key);
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
		if (nextSession == null && !hasNext()) {
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

    /**
     * A wrapper for a {@code ChannelListener} that is serializable,
     * but not managed.
     */
    private static class ManagedSerializableChannelListener
	extends ManagedSerializable<ChannelListener>
	implements ChannelListener
    {
	private static final long serialVersionUID = 1L;

	/** Constructs an instance with the specified {@code listener}. */
	ManagedSerializableChannelListener(ChannelListener listener) {
	    super(listener);
	}

	/** {@inheritDoc} */
	public void receivedMessage(
	    Channel channel, ClientSession sender, ByteBuffer message)
	{
	    assert sender instanceof ClientSessionWrapper;
	    get().receivedMessage(channel, sender, message);
	}
    }

    /**
     * A {@code ManagedObject} wrapper for a {@code ClientSession}'s
     * ID.  An instance of this class also provides a means of
     * obtaining the corresponding client session if the client
     * session still exists.
     *
     * Note: this class is package accessible to allow test access.
     */
    static class ClientSessionInfo
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1L;
	final long nodeId;
	final ManagedReference<ClientSession> sessionRef;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code session}.
	 */
	ClientSessionInfo(DataService dataService, ClientSession session) {
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    assert session instanceof ClientSessionImpl;
	    nodeId = getNodeId(session);
	    sessionRef = dataService.createReference(session);
	}

	/**
	 * Returns the {@code ClientSession} or {@code null} if the
	 * session has been removed.
	 */
	ClientSession getClientSession() {
	    try {
		return ((ClientSessionImpl) sessionRef.get()).
		    getWrappedClientSession();
	    } catch (ObjectNotFoundException e) {
		return null;
	    }
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
	    return getClass().getName() +
		"[" + HexDumper.toHexString(sessionRef.getId().toByteArray()) +
		"]";
	}
    }
    
    /**
     * The channel's event queue.
     */
    private static class EventQueue
	implements ManagedObjectRemoval, Serializable
    {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The managed reference to the queue's channel. */
	private final ManagedReference<ChannelImpl> channelRef;
	/** The managed reference to the managed queue. */
	private final ManagedReference<ManagedQueue<ChannelEvent>> queueRef;
	/** The sequence number for events on this channel. */
	private long seq = 0;
	/** If {@code true}, a refresh should be sent to all channel's
	 * servers before servicing the next event.
	 */
	private boolean sendRefresh = false;

	/**
	 * The number of bytes of the write buffer that are currently
	 * available.
	 */
	private int writeBufferAvailable;

	/**
	 * Constructs an event queue for the specified {@code channel}.
	 */
	EventQueue(ChannelImpl channel) {
	    DataService dataService = getDataService();
	    channelRef = dataService.createReference(channel);
	    queueRef = dataService.createReference(
		new ManagedQueue<ChannelEvent>());
	    writeBufferAvailable = channel.getWriteBufferCapacity();
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.
	 *
	 * @param event the event
	 * @return {@code true} if successful, and {@code false} otherwise
	 * @throws MessageRejectedException if the cost of the event
	 *         exceeds the available buffer space in the queue
	 */
	boolean offer(ChannelEvent event) {
	    int cost = event.getCost();
	    if (cost > writeBufferAvailable) {
	        throw new MessageRejectedException(
	            "Not enough queue space: " + writeBufferAvailable +
		    " bytes available, " + cost + " requested");
	    }
	    boolean success = getQueue().offer(event);
	    if (success && (cost > 0)) {
		getDataService().markForUpdate(this);
                writeBufferAvailable -= cost;
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                        "{0} reserved {1,number,#} leaving {2,number,#}",
                        this, cost, writeBufferAvailable);
                }
	    }
	    return success;
	}

	/**
	 * Returns the channel for this queue.
	 */
	ChannelImpl getChannel() {
	    return channelRef.get();
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
	ManagedQueue<ChannelEvent> getQueue() {
	    return queueRef.get();
	}

	/**
	 * Returns {@code true} if the event queue is empty.
	 */
	boolean isEmpty() {
	    return getQueue().isEmpty();
	}

	/**
	 * Throws a retryable exception if the event queue is not in a
	 * state to process the next event (e.g., it hasn't received
	 * an expected acknowledgment that all channel servers have
	 * received a specified 'send' request).
	 */
	void checkState() {
	    // FIXME: This needs to be implemented in order to support
	    // reliable messages in the face of channel coordinator crash.
	}

	/**
	 * Marks this queue to indicate that a 'refresh' notification needs
	 * to be sent to the associated channel's servers before servicing
	 * any more events.  This action is taken when the associated
	 * channel's coordinator is reassigned during the previous channel
	 * coordinator's recovery.
	 */
	void setSendRefresh() {
	    getDataService().markForUpdate(this);
	    sendRefresh = true;
	}

	/**
	 * Processes (at least) the first event in the queue.
	 */
	void serviceEvent() {
	    checkState();
	    ChannelImpl channel = getChannel();
	    if (!channel.isCoordinator()) {
		// TBD: should a serviceEventQueue request be forwarded to
		// the true channel coordinator?
		logger.log(
		    Level.WARNING,
		    "Attempt at node:{0} channel:{1} to service events; " +
		    "instead of current coordinator:{2}",
		    getLocalNodeId(),
		    HexDumper.toHexString(channel.channelRefId.toByteArray()),
		    channel.coordNodeId);
		return;
	    }
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    DataService dataService = getDataService();
	    
	    /*
	     * If a new coordinator has taken over (i.e., 'sendRefresh' is
	     * true), then all pending events should to be serviced, since
	     * a 'serviceEventQueue' request may have been missed when the
	     * former channel coordinator failed and was in the process of
	     * being reassigned.  So, assign the 'serviceAllEvents' flag
	     * to indicate whether or not all pending events should be
	     * processed below.
	     */
	    boolean serviceAllEvents = sendRefresh;
	    if (sendRefresh) {
		final BigInteger channelRefId = getChannelRefId();
		final String channelName = channel.name;
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "sending refresh, channel:{0}",
			       HexDumper.toHexString(channelRefId.toByteArray()));
		}
		for (final long nodeId : channel.getChannelServerNodeIds()) {
		    channelService.addChannelTask(
		    	channelRefId,
			new IoRunnable() {
			    public void run() throws IOException {
				ChannelServer server = getChannelServer(nodeId);
				if (server != null) {
				    server.refresh(channelName, channelRefId);
				}
			    } },
			nodeId);
		}
		dataService.markForUpdate(this);
		sendRefresh = false;
	    }

	    /*
	     * Process channel events.  If the 'serviceAllEvents' flag is
	     * true, then service all pending events.
	     */
	    int eventsToService = channelService.eventsPerTxn;
	    ManagedQueue<ChannelEvent> eventQueue = getQueue();
	    do {
		ChannelEvent event = eventQueue.poll();
		if (event == null) {
		    return;
		}

                logger.log(Level.FINEST, "processing event:{0}", event);
                int cost = event.getCost();
		if (cost > 0) {
		    dataService.markForUpdate(this);
		    writeBufferAvailable += cost;

		    if (logger.isLoggable(Level.FINEST)) {
		        logger.log(Level.FINEST,
				   "{0} cleared reservation of " +
				   "{1,number,#} bytes, leaving {2,number,#}",
				   this, cost, writeBufferAvailable);
		    }
		}
		event.serviceEvent(this);

	    } while (serviceAllEvents || --eventsToService > 0);
	}

	/* -- Implement ManagedObjectRemoval -- */

	/** {@inheritDoc} */
	public void removingObject() {
	    try {
		getDataService().removeObject(queueRef.get());
	    } catch (ObjectNotFoundException e) {
		// already removed.
	    }
	}
    }

    /**
     * Represents an event on a channel.
     */
    private abstract static class ChannelEvent
	implements ManagedObject, Serializable
    {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Services this event, taken from the head of the given {@code
	 * eventQueue}.
	 */
	abstract void serviceEvent(EventQueue eventQueue);

	/**
	 * Returns the cost of this event, which the {@code EventQueue}
	 * may use to reject events when the total cost is too large.
	 * The default implementation returns a cost of zero.
	 *
	 * @return the cost of this event
	 */
	int getCost() {
	    return 0;
	}
    }

    /**
     * A channel join event.
     */
    private static class JoinEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final BigInteger sessionRefId;

	/**
	 * Constructs a join event with the specified {@code session}.
	 */
	JoinEvent(ClientSession session) {
	    sessionRefId = getSessionRefId(session);
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    ClientSession session =
		(ClientSession) getObjectForId(sessionRefId);
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    if (!channel.addSession(session)) {
		return;
	    }
	    long nodeId = getNodeId(session);
	    final ChannelServer server = getChannelServer(nodeId);
	    if (server == null) {
		/*
		 * If there is no channel server for the session's node,
		 * then the session's node has failed and the session is
		 * now disconnected.  There is no need to send a 'join'
		 * notification (to update the channel membership cache) to
		 * the failed server.
		 */
		return;
	    }
	    final String channelName = channel.name;
	    final BigInteger channelRefId = eventQueue.getChannelRefId();
	    ChannelServiceImpl.getChannelService().addChannelTask(
		eventQueue.getChannelRefId(),
		new IoRunnable() {
		    public void run() throws IOException {
			server.join(channelName, channelRefId, sessionRefId);
		    } },
		nodeId);
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionRefId.toByteArray());
	}
    }

    /**
     * A channel leave event.
     */
    private static class LeaveEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final BigInteger sessionRefId;

	/**
	 * Constructs a leave event with the specified {@code session}.
	 */
	LeaveEvent(ClientSession session) {
	    sessionRefId = getSessionRefId(session);
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    ClientSession session =
		(ClientSession) getObjectForId(sessionRefId);
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    if (!channel.removeSession(session)) {
		return;
	    }
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionRefId.toByteArray());
	}
    }

    /**
     * A channel leaveAll event.
     */
    private static class LeaveAllEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a leaveAll event.
	 */
	LeaveAllEvent() {
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    ChannelImpl channel = eventQueue.getChannel();
	    channel.removeAllSessions();
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    final BigInteger channelRefId = eventQueue.getChannelRefId();
	    for (final long nodeId : channel.getChannelServerNodeIds()) {
		channelService.addChannelTask(
		    eventQueue.getChannelRefId(),
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.leaveAll(channelRefId);
			    }
			} },
		    nodeId);
	    }
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName();
	}
    }

    /**
     * A channel send event.
     */
    private static class SendEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final byte[] message;
	/** The sender's session ID, or null. */
	private final BigInteger senderRefId;

	/**
	 * Constructs a send event with the given {@code senderRefId} and
	 * {@code message}.
	 *
	 * @param senderRefId a sender's session ID, or {@code null}
	 * @param message a message
	 */
	SendEvent(BigInteger senderRefId, byte[] message) {
	    this.senderRefId = senderRefId;
	    this.message = message;
	}

	/** {@inheritDoc}
	 *
	 * TBD: (optimization) this should handle sending
	 * multiple messages to a given channel.  Here, we
	 * could peek at the next event in the queue, and if
	 * it is a send, that event could be batched with this
	 * send event.  This could be repeated for multiple
	 * send events appearing in the queue.
	 */
	public void serviceEvent(EventQueue eventQueue) {

	    /*
	     * Verfiy that the sending session (if any) is a member of this
	     * channel.
	     */
	    ChannelImpl channel = eventQueue.getChannel();
	    if (senderRefId != null) {
		ClientSession sender =
		    (ClientSession) getObjectForId(senderRefId);
		if (sender == null || !channel.hasSession(sender)) {
		    return;
		}
	    }
	    /*
	     * Enqueue a channel task to forward the message to the
	     * channel's servers for delivery.
	     */
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    final BigInteger channelRefId = eventQueue.getChannelRefId();
	    for (final long nodeId : channel.getChannelServerNodeIds()) {
		channelService.addChannelTask(
		    eventQueue.getChannelRefId(),
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.send(channelRefId, message);
			    }
			} },
		    nodeId);
	    }

	    // TBD: need to add a task to update queue that all
	    // channel servers have been notified of
	    // the 'send'.
	}

	/** Use the message length as the cost for sending messages. */
	@Override
	int getCost() {
	    return message.length;
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName();
	}
    }

    /**
     * Returns the write buffer capacity for this channel.
     *
     * @return the write buffer capacity
     */
    int getWriteBufferCapacity() {
        return writeBufferCapacity;
    }

    /**
     * A channel close event.
     */
    private static class CloseEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a close event.
	 */
	CloseEvent() {
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    ChannelImpl channel = eventQueue.getChannel();
	    final BigInteger channelRefId = eventQueue.getChannelRefId();
	    channel.removeChannel();
	    final ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    for (final long nodeId : channel.getChannelServerNodeIds()) {
		channelService.addChannelTask(
		    channelRefId,
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.close(channelRefId);
			    }
			} },
		    nodeId);
	    }
	    
	    channelService.addChannelTask(
		channelRefId,
		new AbstractKernelRunnable("NotifyChannelClosed") {
		    public void run() {
			channelService.closedChannel(channelRefId);
		    } });
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
	    return getClass().getName();
	}
    }

    /**
     * Returns the event queue for the channel that has the specified
     * {@code channelRefId} and coordinator {@code nodeId}.
     */
    private static EventQueue getEventQueue(long nodeId, BigInteger channelRefId) {

	ManagedObjectValueMap<BigInteger, EventQueue> eventQueueMap =
	    getNodeToEventQueuesMap().get(nodeId);
	EventQueue eventQueue = null;
	if (eventQueueMap != null) {
	    eventQueue = eventQueueMap.get(channelRefId);
	}
	if (eventQueue == null) {
	    logger.log(
		Level.WARNING,
		"Event queue for channel:{0} does not exist",
		HexDumper.toHexString(channelRefId.toByteArray()));
	}
	return eventQueue;
    }

    /* -- Static method invoked by ChannelServiceImpl -- */

    /**
     * Handles a channel {@code message} that the specified {@code session}
     * is sending on the channel with the specified {@code channelRefId}.
     *
     * @param	channelRefId the channel ID, as a {@code BigInteger}
     * @param	session the client session sending the channel message
     * @param	message the channel message
     */
    static void handleChannelMessage(
	BigInteger channelRefId, ClientSession session, ByteBuffer message)
    {
	assert session instanceof ClientSessionWrapper;
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    channel.receivedMessage(session, message);
	} else {
	    // Ignore message received for unknown channel.
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
 		    Level.FINE,
		    "Dropping message:{0}: from:{1} for unknown channel: {2}",
		    HexDumper.format(message), session,
		    HexDumper.toHexString(channelRefId.toByteArray()));
	    }
	}
    }

    /**
     * Services the event queue for the channel with the specified {@code
     * channelRefId}.
     */
    static void serviceEventQueue(BigInteger channelRefId) {
	EventQueue eventQueue = getEventQueue(getLocalNodeId(), channelRefId);
	if (eventQueue != null) {
	    eventQueue.serviceEvent();
	}
    }

    /**
     * Returns the next service bound name after the given {@code key} that
     * starts with the given {@code prefix}, or {@code null} if there is none.
     */
    private static String nextServiceBoundNameWithPrefix(
 	DataService dataService, String key, String prefix)
    {
	String name = getDataService().nextServiceBoundName(key);
	return
	    (name != null && name.startsWith(prefix)) ? name : null;
    }

    /**
     * Returns an iterator for channels that are coordinated on the node with
     * the specified {@code nodeId}.
     */
    private static Iterator<BigInteger> getChannelsIterator(long nodeId) {

	ManagedObjectValueMap<BigInteger, EventQueue>
	    eventQueuesMap = getNodeToEventQueuesMap().get(nodeId);
	return
	    eventQueuesMap != null ?
	    eventQueuesMap.keySet().iterator() :
	    null;
    }
    
    /**
     * A persistent task to reassign channel coordinators on a failed node
     * to another node. In a single task, only one failed coordinator is
     * reassigned.  A task for one coordinator schedules a task for the
     * next reassignment, if there are coordinators on the failed node left
     * to be reassigned.
     */
    static class ReassignCoordinatorsTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The node ID of the failed node. */
	private final long nodeId;

	/** The iterator for channels on the failed node. */
	private final Iterator<BigInteger> iter;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code nodeId} of the failed node.
	 */
	ReassignCoordinatorsTask(long nodeId) {
	    this.nodeId = nodeId;
	    this.iter = getChannelsIterator(nodeId);
	}

	/**
	 * Reassigns the next coordinator for the {@code nodeId} to another
	 * node with member sessions (or the local node if there are no
	 * member sessions), schedules a task to remove failed sessions for
	 * the channel, and then reschedules this task to reassign the next
	 * coordinator.  If there are no more coordinators for the
	 * specified {@code nodeId}, then no action is taken.
	 */
	public void run() {
	    if (iter == null || !iter.hasNext()) {
		return;
	    }

	    WatchdogService watchdogService =
		ChannelServiceImpl.getWatchdogService();
	    TaskService taskService = ChannelServiceImpl.getTaskService();
	    
	    BigInteger channelRefId = iter.next();
	    iter.remove();
	    ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	    if (channel != null) {
		channel.reassignCoordinator(nodeId);
		
		// Schedule a task to remove failed sessions for channel.
		taskService.scheduleTask(
		    new RemoveFailedSessionsFromChannelTask(
			nodeId, channelRefId));
		/*
		 * If other channel servers have failed, remove their
		 * sessions too.  This covers the case where a channel
		 * coordinator (informed of a node failure) fails before it
		 * has a chance to schedule a task to remove member
		 * sessions for another failed node (cascading failure
		 * during recovery).
		 */
		for (long serverNodeId : channel.servers) {
		    if (serverNodeId != nodeId) {
			Node serverNode = watchdogService.getNode(serverNodeId);
			if (serverNode == null || !serverNode.isAlive()) {
			    taskService.scheduleTask(
			        new RemoveFailedSessionsFromChannelTask(
				    serverNodeId, channelRefId));
			}
		    }
		}
	    }

	    /*
	     * Schedule a task to reassign the next channel coordinator, or
	     * if done with coordinator reassignment, remove the recovered
	     * node's  mapping from the node-to-event-queues map.
	     */
	    if (iter.hasNext()) {
		taskService.scheduleTask(this);
	    } else {
		ManagedObjectValueMap<BigInteger, EventQueue> eventQueuesMap =
		    getNodeToEventQueuesMap().remove(nodeId);
		if (eventQueuesMap != null) {
		    getDataService().removeObject(eventQueuesMap);
		}
	    }
	}
    }

    /**
     * A persistent task to remove sessions, connected to a failed node,
     * from locally coordinated channels.  This task handles a single
     * locally-coordinated channel, and then schedules another task to
     * handle the next one.
     */
    static class RemoveFailedSessionsFromLocalChannelsTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	/** The failed node. */
	private final long failedNodeId;
	
	/** The iterator for locally-coordinated channels.*/
	private final Iterator<BigInteger> iter;
	
	/**
	 * Constructs an instance with the specified {@code localNodeId}
	 * and {@code failedNodeId}.
	 */
	RemoveFailedSessionsFromLocalChannelsTask(
	    long localNodeId, long failedNodeId)
	{
	    this.failedNodeId = failedNodeId;
	    this.iter = getChannelsIterator(localNodeId);
	}

	/**
	 * Finds the next locally-coordinated channel and schedules a
	 * task to remove the failed sessions from that channel, and
	 * reschedules this task to handle the next locally-coordinated
	 * channel. If there are no more locally-coordinated channels,
	 * then this task takes no action.
	 */
	public void run() {
	    if (iter == null || !iter.hasNext()) {
		return;
	    }
	    
	    BigInteger channelRefId = iter.next();
	    TaskService taskService = ChannelServiceImpl.getTaskService();
	    taskService.scheduleTask(
		new RemoveFailedSessionsFromChannelTask(
		    failedNodeId, channelRefId));
		    
	    // Schedule a task to remove failed sessions from next
	    // locally coordinated channel.
	    if (iter.hasNext()) {
		taskService.scheduleTask(this);
	    }
	}
    }

    /**
     * A persistent task to remove all failed sessions (specified by the
     * {@code sessionPrefix} on a given node for a channel.  In a single
     * task, only one failed session is removed from the channel.  A task
     * for one session schedules a task for the next session to be removed
     * if there are sessions left to be removed.
     */
    private static class RemoveFailedSessionsFromChannelTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	private final String sessionPrefix;
	private String sessionKey;
	private final BigInteger channelRefId;
	private final long failedNodeId;
	
	/** Constructs an instance. */
	RemoveFailedSessionsFromChannelTask(
 	    long failedNodeId, BigInteger channelRefId)
	{
	    this.sessionPrefix = getSessionNodePrefix(channelRefId, failedNodeId);
	    this.sessionKey = sessionPrefix;
	    this.channelRefId = channelRefId;
	    this.failedNodeId = failedNodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    DataService dataService = getDataService();
	    sessionKey =
		nextServiceBoundNameWithPrefix(
		    dataService, sessionKey, sessionPrefix);
	    if (sessionKey != null) {
		ChannelImpl channel =
		    (ChannelImpl) getObjectForId(channelRefId);
		if (channel != null) {
		    BigInteger sessionRefId =
			getLastComponentAsBigInteger(sessionKey);
		    channel.removeSession(failedNodeId, sessionRefId);
		} else {
		    // This shouldn't happen, but remove the binding anyway.
		    channel.removeSessionBinding(sessionKey);
		}	
		ChannelServiceImpl.getTaskService().scheduleTask(this);
	    }
	}
    }

    /**
     * Returns the prefix for accessing channel sets for all sessions
     * connected to the node with the specified {@code nodeId}.  The
     * prefix has the following form:
     *
     * com.sun.sgs.impl.service.channel.
     *		set.<nodeId>.
     *
     * This method is only included to locate obsolete channel sets to be
     * removed. 
     */
    private static String getChannelSetPrefix(long nodeId) {
	return PKG_NAME +
	    SET_COMPONENT + nodeId + ".";
    }

    /**
     * Obsolete {@code ChannelSet} representation.  The serialized form is
     * here just so that obsolete channel sets can be removed by 
     * {@code ChannelServiceImpl} upon recovery.
     */
    private static class ChannelSet extends ClientSessionInfo {
	
	private static final long serialVersionUID = 1L;

	/** The set of channel IDs that the client session is a member of. */
	private final Set<BigInteger> set = new HashSet<BigInteger>();

	/**
	 * Constructs an instance.  This constructor is only present for
	 * testing purposes.
	 */
	public ChannelSet(DataService dataService, ClientSession session) {
	    super(dataService, session);
	}
    }
    

    /**
     * A task to remove any obsolete channel sets left over from previous
     * ChannelServiceImpl version.
     */
    static class RemoveObsoleteChannelSetsTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	private final long failedNodeId;

	/**
	 * Constructs an instance.
	 * @param failedNodeId the ID of the failed node
	 */
	RemoveObsoleteChannelSetsTask(long failedNodeId) {
	    this.failedNodeId = failedNodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    DataService dataService = getDataService();
	    String prefix = getChannelSetPrefix(failedNodeId);
	    String key =
		nextServiceBoundNameWithPrefix(dataService, prefix, prefix);
	    if (key != null) {
		try {
		    ManagedObject channelSet =
			dataService.getServiceBinding(key);
		    dataService.removeObject(channelSet);
		} catch (ObjectNotFoundException e) {
		    logger.logThrow(
			Level.WARNING, e,
			"Cleaning up obsolete channel set:{0} throws",
			key);
		}
		dataService.removeServiceBinding(key);
		ChannelServiceImpl.getTaskService().scheduleTask(this);
	    }
	}
    }
    
    /**
     * Returns a set containing session identifiers (as obtained by
     * {@link ManagedReference#getId ManagedReference.getId}) for all
     * sessions that are a member of the channel with the specified
     * {@code channelRefId} and are last known to have been connected
     * to node {@code nodeId}.
     */
    static Set<BigInteger> getSessionRefIdsForNode(
	 DataService dataService, BigInteger channelRefId, long nodeId)
    {
	Set<BigInteger> members = new HashSet<BigInteger>();
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    for (String sessionKey :
		     BoundNamesUtil.getServiceBoundNamesIterable(
			dataService,
			channel.getSessionNodePrefix(
			    channel.channelRefId, nodeId)))
	    {
		BigInteger sessionRefId =
		    getLastComponentAsBigInteger(sessionKey);
		members.add(sessionRefId);
	    }
	}
	return members;
    }

    /**
     * Returns the last component of the given {@code key} as a BigInteger.
     */
    private static BigInteger getLastComponentAsBigInteger(String key) {
	int index = key.lastIndexOf('.');
	return new BigInteger(key.substring(index + 1), 16);
    }
}
