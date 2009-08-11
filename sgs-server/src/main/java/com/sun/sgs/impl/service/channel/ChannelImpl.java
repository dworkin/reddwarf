/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import com.sun.sgs.app.DeliveryNotSupportedException;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.service.channel.ChannelServer.MembershipStatus;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl.ChannelEventType;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.service.session.NodeAssignment;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedMap;
import com.sun.sgs.impl.util.BindingKeyedSet;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.ManagedQueue;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction.
 *
 * <p>This implementation uses several {@link BindingKeyedCollections}
 * as follows:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Map:</i> <b><code>channelsMap</code></b> <br>
 *	<i>Prefix:</i> <code>{@value #CHANNELS_MAP_PREFIX}</code> <br>
 *	<i>Key:</i> <i>{@code name}</i><br>
 *	<i>Value:</i> <b>{@link ChannelImpl}</b>
 *
 * <dd style="padding-top: .5em">Map for accessing a {@code ChannelImpl}
 *	 by name. <p>
 *
 * <dt> <i>Map:</i> <b><code>eventQueuesMap</code></b> <br>
 *	<i>Prefix:</i> <code>{@value
 *	#EVENT_QUEUE_MAP_PREFIX}<i>coordinatorNodeId.</i></code> <br>
 *	<i>Key:</i> <i>{@code channelId}</i> (as string form of
 *	{@code BigInteger})<br>
 *	<i>Value:</i> <b>{@code EventQueue}</b>
 *
 * <dd style="padding-top: .5em">Map for accessing an event queue for a
 * 	channel on a given node.  The map is also used during recovery to
 * 	determine which channels are coordinated on a failed node so that
 * 	each channel can be reassigned a new coordinator.<p>
 *
 * </dl> <p>
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

    /** The channels map prefix. */
    static final String CHANNELS_MAP_PREFIX = PKG_NAME + "name.";

    /** An event queue map prefix. */
    static final String EVENT_QUEUE_MAP_PREFIX = PKG_NAME + "eventQueue.";

    /** The empty channel membership set. */
    @SuppressWarnings("unchecked")
    static final Set<BigInteger> EMPTY_CHANNEL_MEMBERSHIP =
	(Set<BigInteger>) Collections.EMPTY_SET;

    /** The random number generator for choosing a new coordinator. */
    private static final Random random = new Random();

    /** The map of channels, keyed by name. */
    private static BindingKeyedMap<ChannelImpl> channelsMap = null;

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

    /** The node IDs of ChannelServers that have locally connected
     * members of this channel. */
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
     * The maximum channel message length supported by sessions joined to this
     * channel.
     */
    private int maxMessageLength = Integer.MAX_VALUE;
    
    /**
     * The maximum number of message bytes that can be queued for delivery on
     * this channel.
     */
    private final int writeBufferCapacity;

    /** The event queue reference. */
    private final ManagedReference<EventQueue> eventQueueRef;

    /**
     * Constructs an instance of this class with the specified
     * {@code name}, {@code listener}, {@code delivery} guarantee.
     * and write buffer capacity.
     *
     * @param name a channel name
     * @param listener a channel listener
     * @param delivery a delivery guarantee
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
	getEventQueuesMap(coordNodeId).
	    put(channelRefId.toString(), eventQueue);
    }

    /** Returns the data service. */
    private static DataService getDataService() {
	return ChannelServiceImpl.getDataService();
    }

    /**
     * Returns the channels map, keyed by channel name.
     */
    private static synchronized  BindingKeyedMap<ChannelImpl> getChannelsMap() {
	if (channelsMap == null) {
	    channelsMap = newMap(CHANNELS_MAP_PREFIX);

	}
	return channelsMap;
    }

    /**
     * Returns the event queues map for the specified coordinator {@code
     * nodeId}, keyed by channel ID.  In the returned map, each key is a
     * channel ID (as a BigInteger converted to string form) and is mapped
     * to its corresponding {@code EventQueue}.
     */
    private static BindingKeyedMap<EventQueue>
	getEventQueuesMap(long nodeId)
    {
	return newMap(EVENT_QUEUE_MAP_PREFIX + Long.toString(nodeId) + ".");
    }

    /* -- Factory methods -- */

    /**
     * Constructs a new {@code Channel} with the given {@code name}, {@code
     * listener}, {@code delivery} guarantee and write-buffer capacity.
     */
    static Channel newInstance(String name,
			       ChannelListener listener,
			       Delivery delivery,
                               int writeBufferCapacity)
    {
	ChannelImpl channel =
	    delivery.equals(Delivery.UNRELIABLE) ?
	    new UnreliableChannel(name, listener, delivery,
				  writeBufferCapacity) :
	    new OrderedChannel(name, listener, delivery,
				writeBufferCapacity);
	return channel.getWrappedChannel();
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

    /** Implements {@link Channel#getDelivery}. */
    Delivery getDelivery() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDelivery returns {0}", delivery);
	}
	return delivery;
    }

    /** Implements {@link Channel#hasSessions}. */
    boolean hasSessions() {
	checkClosed();
	return !servers.isEmpty();
    }

    /** Implements {@link Channel#getSessions}. */
    Iterator<ClientSession> getSessions() {
	checkClosed();

	Set<BigInteger> channelMembers = null;
	ChannelServiceImpl channelService =
	    ChannelServiceImpl.getChannelService();
	
	if (!servers.isEmpty()) {
	    channelMembers =
		channelService.collectChannelMembership(
		    txn, channelRefId, servers);
	}

	return
	    new ClientSessionIterator(
		channelMembers != null ?
		channelMembers :
		EMPTY_CHANNEL_MEMBERSHIP);
    }

    /** Implements {@link Channel#join(ClientSession)}. */
    void join(final ClientSession session) {
	try {
	    checkClosed();
	    if (session == null) {
		throw new NullPointerException("null session");
	    }
	    checkDelivery(session);
	    
	    /*
	     * Enqueue join request with underlying (unwrapped) client
	     * session object.
	     */
	    addEvent(
		new JoinEvent(unwrapSession(session), eventQueueRef.get()));

	    logger.log(Level.FINEST, "join session:{0} returns", session);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "join throws");
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
	     * Check for null elements, and check that sessions support
	     * this channel's delivery guarantee.
	     */
	    for (ClientSession session : sessions) {
		if (session == null) {
		    throw new NullPointerException(
			"sessions contains a null element");
		}
		checkDelivery(session);
	    }
	    
	    /*
	     * Enqueue join requests, each with underlying (unwrapped)
	     * client session object.
	     *
	     * TBD: (optimization) add a single event instead of one for
	     * each session.
	     */
	    EventQueue eventQueue = eventQueueRef.get();
	    for (ClientSession session : sessions) {
		addEvent(new JoinEvent(unwrapSession(session), eventQueue));
	    }
	    logger.log(Level.FINEST, "join sessions:{0} returns", sessions);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "join throws");
	    throw e;
	}
    }

    /**
     * Throws {@code DeliveryNotSupportedException} if the specified {@code
     * session} does not support this channel's delivery guarantee.
     *
     * @param	session a client session
     * @throws	DeliveryNotSupportedException if the specified {@code session}
     *		does not support this channel's delivery guarantee
     */
    private void checkDelivery(ClientSession session) {
	for (Delivery d : session.supportedDeliveries()) {
	    if (d.supportsDelivery(delivery)) {
		return;
	    }
	}
	throw new DeliveryNotSupportedException(
	    "client session:" + session +
	    " does not support delivery guarantee",
	    delivery);
    }
    
    /**
     * Returns the underlying {@code ClientSession} for the specified
     * {@code session}.  Note: The client session service wraps each client
     * session object that it hands out to the application.  The channel
     * service implementation relies on the assumption that a client
     * session's {@code ManagedObject} ID is the client session's ID (used
     * for identifying the client session, e.g. for sending messages to
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
     * and the event queue is empty, then service the event immediately.
     */
    protected void addEvent(ChannelEvent event) {

	EventQueue eventQueue = eventQueueRef.get();

	if (!eventQueue.offer(event, this)) {
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
	    eventQueue.serviceEventQueue();

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
	    addEvent(
		new LeaveEvent(unwrapSession(session), eventQueueRef.get()));
	    logger.log(Level.FINEST, "leave session:{0} returns", session);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "leave throws");
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
		addEvent(new LeaveEvent(unwrapSession(session),
					eventQueueRef.get()));
	    }
	    logger.log(Level.FINEST, "leave sessions:{0} returns", sessions);

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "leave throws");
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
	    addEvent(new LeaveAllEvent(eventQueueRef.get()));
	    logger.log(Level.FINEST, "leaveAll returns");

	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "leave throws");
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
            
            if (message.remaining() > maxMessageLength) {
                throw new IllegalArgumentException(
                    "message too long: " + message.remaining() + " > " +
                    maxMessageLength);
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
	    boolean isChannelMember =
		senderRefId != null ?
		ChannelServiceImpl.getChannelService().
		    isLocalChannelMember(channelRefId, senderRefId) :
		true;
	    handleSendEvent(
		new SendEvent(senderRefId, msgBytes, eventQueueRef.get(),
			      isChannelMember));


	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "send channel:{0} message:{1} returns",
			   this, HexDumper.format(msgBytes, 0x50));
	    }

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINE)) {
		logger.logThrow(
		    Level.FINE, e, "send channel:{0} message:{1} throws",
		    this, HexDumper.format(message, 0x50));
	    }
	    throw e;
	}
    }

    /**
     * Handles a send event containing a message and a sender (which may be
     * {@code null}.  A subclass should handle the send event according to
     * the channel's delivery guarantee.
     *
     * @param	sendEvent a send event
     */
    protected abstract void handleSendEvent(SendEvent sendEvent);

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
	getDataService().markForUpdate(this);
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
    private static long getNodeId(ClientSessionImpl session) {
	long relocatingToNodeId =
	    session.getRelocatingToNodeId();
	boolean relocating = relocatingToNodeId != -1;
	return relocating ? relocatingToNodeId : session.getNodeId();
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
     * Returns a new {@code BindingKeyedSet} with the specified {@code
     * keyPrefix}.
     *
     * @param	<V> the value type
     * @param	keyPrefix a key prefix for the set's service bindings
     * @return	a new {@code BindingKeyedSet}
     */
    private static <V> BindingKeyedSet<V> newSet(String keyPrefix) {
	return ChannelServiceImpl.getCollectionsFactory().
	    newSet(keyPrefix);
    }

    /**
     * Returns a new {@code BindingKeyedMap} with the specified {@code
     * keyPrefix}.
     *
     * @param	<V> the value type
     * @param	keyPrefix a key prefix for the map's service bindings
     * @return	a new {@code BindingKeyedMap}
     */
    private static <V> BindingKeyedMap<V> newMap(String keyPrefix) {
	return ChannelServiceImpl.getCollectionsFactory().
	    newMap(keyPrefix);
    }

    /**
     * Adds the specified {@code nodeId} to the set of server nodes for
     * this channel.
     *
     * @param	nodeId a server node's ID
     */
    void addServerNodeId(long nodeId) {
	if (servers.add(nodeId)) {
	    getDataService().markForUpdate(this);
	}
    }
    
    /**
     * Removes the specified {@code nodeId} from the set of server nodes
     * for this channel.
     *
     * @param	a server node's ID
     */
    private void removeServerNodeId(long nodeId) {
	if (servers.remove(nodeId)) {
	    getDataService().markForUpdate(this);
	}
    }

    /**
     * Reassigns the channel coordinator as follows:
     *
     * 1) Reassigns the channel's coordinator from the node specified by
     * the {@code failedCoordNodeId} to another server node (if there are
     * channel members), or the local node (if there are no channel
     * members) and rebinds the event queue to the new coordinator's key.
     *
     * 2} Sends out a 'serviceEventQueue' request to the new
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

	/*
	 * Assign a new coordinator, and store event queue in new
	 * coordinator's event queue map.
	 */
	coordNodeId = chooseCoordinatorNode();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(
		Level.FINER,
		"channel:{0} reassigning coordinator from:{1} to:{2}",
		HexDumper.toHexString(channelRefId.toByteArray()),
		failedCoordNodeId,
		coordNodeId);
	}
	EventQueue eventQueue = eventQueueRef.get();
	getEventQueuesMap(coordNodeId).
	    put(channelRefId.toString(), eventQueue);
	eventQueue.coordinatorReassigned();

	/*
	 * Send a 'serviceEventQueue' notification to the new coordinator.
	 */
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
     * Returns a set containing the node IDs of the channel servers for
     * this channel. 
     */
    private Set<Long> getServerNodeIds() {
	return new HashSet<Long>(servers);
    }
    
    /**
     * Removes all sessions from this channel and clears the list of
     * channel servers for this channel.  This method should be called
     * when all sessions leave the channel.
     */
    private void clearServerNodeIds() {
	getDataService().markForUpdate(this);
	servers.clear();
    }

    /**
     * Sends a leave notification for the session with the specified {@code
     * sessionRefId} to the channel server with the specified {@code
     * nodeId}.
     */
    private void sendLeaveNotification(long nodeId,
				       final BigInteger sessionRefId)
    {
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
	clearServerNodeIds();
	getChannelsMap().removeOverride(name);
	dataService.removeObject(this);
	if (listenerRef != null) {
	    ChannelListener maybeWrappedListener = listenerRef.get();
	    if (maybeWrappedListener instanceof ManagedSerializable) {
		dataService.removeObject(maybeWrappedListener);
	    }
	}
	BindingKeyedMap<EventQueue> eventQueuesMap =
	    getEventQueuesMap(coordNodeId);
	eventQueuesMap.removeOverride(channelRefId.toString());
	EventQueue eventQueue = eventQueueRef.get();
	dataService.removeObject(eventQueue);
    }
    
    /* -- Other classes -- */

    /**
     * An iterator for {@code ClientSession}s of a given channel.
     */
    private static class ClientSessionIterator
	implements Iterator<ClientSession>
    {
	/** The iterator for sessions. */
	private Iterator<BigInteger> iterator;

	/** The client session to be returned by {@code next}. */
	private ClientSession nextSession = null;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code channelRefId}.
	 */
	ClientSessionIterator(Set<BigInteger> channelMembers) {
	    iterator = channelMembers.iterator();
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (!iterator.hasNext()) {
		return false;
	    }
	    if (nextSession != null) {
		return true;
	    }
	    ClientSessionImpl session = (ClientSessionImpl)
		getObjectForId(iterator.next());
	    if (session == null) {
		return hasNext();
	    } else {
		nextSession = session.getWrappedClientSession();
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
	/** The next timestamp. */
	private long timestamp = 1;
	/** The timestamp beyond which membership does not have to be
	 * verified with the node to which a session is connected. */
	private long coordinatorAssignmentTimestamp = 0;

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
	 * Notifies this event queue that its coordinator has been
	 * reassigned.
	 */
	void coordinatorReassigned() {
	    coordinatorAssignmentTimestamp =
		isEmpty() ? 0 : timestamp;
	}

	boolean isCoordinatorRecovering(long timestamp) {
	    return timestamp < coordinatorAssignmentTimestamp;
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.
	 *
	 * @param event the event
	 * @param isCoordinator {@code true} if the caller is the
	 *	  channel's coordinator
	 * @return {@code true} if successful, and {@code false} otherwise
	 * @throws MessageRejectedException if the cost of the event
	 *         exceeds the available buffer space in the queue
	 */
	boolean offer(ChannelEvent event, ChannelImpl channel) {

	    boolean notifyServiceEventQueue = true;
	    
	    int cost = event.getCost();
	    if (cost > writeBufferAvailable) {
	        throw new MessageRejectedException(
	            "Not enough queue space: " + writeBufferAvailable +
		    " bytes available, " + cost + " requested");
	    }
	    
	    if (channel.isCoordinator() && isEmpty()) {
		// The event queue is empty, and this node is the
		// coordinator for the channel's event queue, so process
		// the event now.
		event.processing();
		if (event.serviceEvent(channel)) {
		    // Event completed processing, so return success.
		    return true;
		}
		// The event needs to be added to the head of the queue
		// (below) because it is not yet complete.  There is no
		// need to notify the event queue because it will be
		// notified when the event has completed processing.
		notifyServiceEventQueue = false;
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
	    if (notifyServiceEventQueue) {
		channel.notifyServiceEventQueue(this);
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
	
	/** Returns the current timestamp. */
	long getTimestamp() {
	    return timestamp;
	}

	/** Returns the current timestamp, then increments it. */
	long getTimestampAndIncrement() {
	    getDataService().markForUpdate(this);
	    return timestamp++;
	}

	/**
	 * Processes (at least) the first event in the queue and
	 * returns {@code true} if more events need to be serviced and
	 * {@code false} otherwise.
	 */
	void serviceEventQueue() {
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
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "coordinator:{0} channelId:{1}",
			   getLocalNodeId(),
			   HexDumper.toHexString(
				channel.channelRefId.toByteArray()));
	    }
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    DataService dataService = getDataService();
	    
	    /*
	     * Process channel events
	     */
	    int eventsPerTxn = channelService.eventsPerTxn;
	    ManagedQueue<ChannelEvent> eventQueue = getQueue();
	    
	    boolean completed = false;
	    do {
		ChannelEvent event = eventQueue.peek();
		if (event == null) {
		    //  No more events to process, so return.
		    return;
		} else if (event.isCompleted()) {
		    // Remove completed event and get next event to
		    // process. Return if there are no more events.
		    eventQueue.poll();
		    event = eventQueue.peek();
		    if (event == null) {
			return;
		    }
		}
		if (event.isProcessing()) {
		    return;
		} else {
		    int cost = event.getCost();
		    if (cost > 0) {
			dataService.markForUpdate(this);
			writeBufferAvailable += cost;
			
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "{0} cleared reservation of " +
				       "{1,number,#} bytes, leaving " +
				       "{2,number,#}",
				       this, cost, writeBufferAvailable);
			}
		    }
		    // Mark event as processing.
		    event.processing();
		}
		completed = event.serviceEvent(getChannel());
		if (completed) {
		    eventQueue.poll();
		}

	    } while (completed && --eventsPerTxn > 0);

	    if (eventQueue.peek() != null) {
		channelService.addChannelToService(channel.channelRefId);
	    }
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
    abstract static class ChannelEvent
	implements ManagedObject, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** This event's timestamp. */
	protected final long timestamp;
	
	/**
	 * The event's completed status, indicating whether this event has
	 * been completely processed and it is safe to service the next
	 * event in the queue.
	 */
	private boolean completed = false;

	/** The ID of the coordinator node on which this event is being
	 * processed. */
	long processingOnNodeId = -1;

	/** Constructs an instance with the specified {@code timestamp} */
	ChannelEvent(long timestamp) {
	    this.timestamp = timestamp;
	}

	/**
	 * Services this event (taken from the head of the event queue) for
	 * the specified {@code channel}, and returns {@code true} if the
	 * event has completed processing and can be removed from the event
	 * queue.
	 */
	abstract boolean serviceEvent(ChannelImpl channel);

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

	/**
	 * Marks this event as completed.
	 */
	boolean completed() {
	    logger.log(Level.FINEST, "completed event:{0}", this);
	    // TBD: markForUpdate can throw ONFE if this event has been
	    // removed.
	    getDataService().markForUpdate(this);
	    completed = true;
	    return completed;
	}

	/**
	 * Marks this event as being processed.
	 */
	void processing() {
	    logger.log(Level.FINEST, "processing event:{0}", this);
	    getDataService().markForUpdate(this);
	    processingOnNodeId = getLocalNodeId();
	}
	
	/**
	 * Returns {@code true} if this event is completed and it is safe
	 * to service the next event in the queue, otherwise returns {@code
	 * false}.
	 */
	boolean isCompleted() {
	    return completed;
	}

	/**
	 * Returns {@code true} if this event is being processed, and
	 * {@code false} otherwise.
	 */
	boolean isProcessing() {
	    return processingOnNodeId == getLocalNodeId();
	}
    }

    /**
     * A channel join event.
     */
    private static class JoinEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The session ID for the session to join the channel. */
	private final BigInteger sessionRefId;

	/**
	 * Constructs a join event with the specified {@code session}.
	 */
	JoinEvent(ClientSession session, EventQueue eventQueue) {
	    super(eventQueue.getTimestamp());
	    sessionRefId = getSessionRefId(session);
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(final ChannelImpl channel) {
	    assert isProcessing() && !isCompleted();
	    ClientSessionImpl session =
		(ClientSessionImpl) getObjectForId(sessionRefId);
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return completed();
	    }
	    channel.addServerNodeId(getNodeId(session));

	    ProcessJoinTask task =
		new ProcessJoinTask(channel, channel.eventQueueRef.getId(),
				    this, session, sessionRefId);
	    ChannelServiceImpl.getChannelService().addChannelTask(
		    channel.channelRefId, task);
	    return isCompleted();
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionRefId.toByteArray());
	}
    }

    /**
     * A non-transactional task to send a join notification to a session's
     * node, allowing for the possibility of the session relocating while
     * the join message is in transit.
     */
    private abstract static class ProcessChannelEventTask
	extends AbstractKernelRunnable
    {
	protected final ChannelServiceImpl channelService;
	protected String name;
	protected final Delivery delivery;
	protected final BigInteger channelRefId;
	protected final BigInteger sessionRefId;
	private final BigInteger eventQueueRefId;
	private final BigInteger eventRefId;
	protected final long timestamp;

	/** The session's node ID.  Initialized during construction
	 * and modified by calls to {@code removeMembershipIfNodeUnchanged}
	 * and {@code remapMembershipIfRelocating} methods.
	 */
	protected volatile long sessionNodeId;

	/**
	 * Constructs an instance.  This contructor must be called within a
	 * transaction.
	 */
	ProcessChannelEventTask(ChannelImpl channel,
				BigInteger eventQueueRefId,
				ChannelEvent channelEvent,
				ClientSessionImpl session,
				BigInteger sessionRefId)
	{
	    super(null);
	    this.channelService = ChannelServiceImpl.getChannelService();
	    this.name = channel.name;
	    this.delivery = channel.delivery;
	    this.channelRefId = channel.channelRefId;
	    this.eventRefId =
		getDataService().createReference(channelEvent).getId();
	    this.eventQueueRefId = eventQueueRefId;
	    this.timestamp = channelEvent.timestamp;
	    this.sessionRefId = sessionRefId;
	    this.sessionNodeId = getNodeId(session);
	}

	/**
	 * This must be called outside of a transaction.
	 */
	public void run() {
	    channelService.checkNonTransactionalContext();
	    while (!channelService.shuttingDown()) {
		ChannelServer server = getChannelServer(sessionNodeId);
		if (server == null) {
		    // If session's node hasn't changed, then it is
		    // disconnected because its channel server is gone.
		    if (updateSessionNodeId()) {
			continue; // relocating
		    } else {
			break;	// disconnected
		    }
		}
		try {
		    if (sendNotification(server)) {
			// Join was successful
			break;
		    } else {
			// If session's node hasn't changed, then it is
			// disconnected because it wasn't connected to the
			// node.
			if (updateSessionNodeId()) {
			    continue; // relocating
			} else {
			    break; // disconnected
			}
		    }
		    
		} catch (IOException e) {
		    if (!channelService.isAlive(sessionNodeId)) {
			// If the session's node hasn't changed, then it is
			// disconnected because its node crashed.
			if (updateSessionNodeId()) {
			    continue; // relocating
			} else {
			    break; // disconnected
			}
		    }
		    // Wait for transient situation to resolve.
		    try {
			// TBD: make sleep time configurable.
			Thread.sleep(200);
		    } catch (InterruptedException ie) {
		    }
		}
	    }

	    // Mark event as completed and add task to resume
	    // servicing the event queue.
	    completed();
	}

	/**
	 * Sends a notification message to the specified channel {@code
	 * server} and returns the result of the notification.  This method is
	 * invoked outside of a transaction.
	 *
	 * @return the result of the notification
	 * @throws IOException if a communication problem occurs while sending
	 * 	   the notification
	 */
	protected abstract boolean sendNotification(ChannelServer server)
	    throws IOException;

	/**
	 * Updates this task's {@code sessionNodeId} and returns {@code true}
	 * if the session's node ID has changed, and returns {@code false} if
	 * the session's node ID is unchanged or the session no longer exists.
	 * If the session's node ID changes, a subclass may need to add the
	 * updated node ID to the channel's set of server node IDs.  This
	 * method is invoked outside of a transaction.
	 *
	 * @return {@code true} if the session's node ID is updated, otherwise
	 *	   {@code false}
	 */
	protected abstract boolean updateSessionNodeId();
	
	/**
	 * Returns the client session associated with this task, or null if
	 * the session no longer exists.  This method must be invoked
	 * within a transaction.
	 */
	protected ClientSessionImpl getSession() {
	    return (ClientSessionImpl) getObjectForId(sessionRefId);
	}

	/**
	 * Returns the channel associated with this task, or null if the channel
	 * no longer exists. This method must be invoked within a transaction.
	 */
	protected ChannelImpl getChannel() {
	    return (ChannelImpl) getObjectForId(channelRefId);
	}

	/**
	 * Updates this task's {@code sessionNodeId} and returns {@code true}
	 * if the session's node ID has changed, and returns {@code false} if
	 * the session's node ID is unchanged or the session no longer
	 * exists.  This method must be invoked within a transaction.
	 *
	 * @param addNodeId if {@code true}, adds the specified
	 *	  {@code nodeId} to the channel's set of server node IDs
	 * @return {@code true} if the session's node ID is updated, otherwise
	 *	   {@code false}
	 */
	protected boolean updateSessionNodeId(final boolean addNodeId) {
	    
	    final long oldSessionNodeId = sessionNodeId;
	    try {
		
	      return channelService.runTransactionalCallable(
 		new KernelCallable<Boolean>("updateSessionNodeId") {
		    public Boolean call() {
			ClientSessionImpl session = getSession();
			if (session != null) {
			    sessionNodeId = getNodeId(session);
			}
			boolean updated = sessionNodeId != oldSessionNodeId;
			if (updated && addNodeId) {
			    ChannelImpl channel = getChannel();
			    if (channel != null) {
				channel.addServerNodeId(sessionNodeId);
			    } else {
				updated = false;
			    }
			}
			return updated;
		    }
		});
	    
	    } catch (Exception e) {
		// TBD: This shouldn't happen, so log message?
		return false;
	    }
	}

	/**
	 * Returns the event queue's latest timestamp.
	 */
	protected long getEventQueueTimestamp() {
	    
	    try {
		return channelService.runTransactionalCallable(
 		  new KernelCallable<Long>("getEventQueueTimestamp") {
		      public Long call() {
			  EventQueue eventQueue = (EventQueue)
			      getObjectForId(eventQueueRefId);
			  if (eventQueue != null) {
			      return eventQueue.getTimestamp();
			  } else {
			      return -1L;
			  }
		    }
		});
	    } catch (Exception e) {
		return -1L;
	    }
	}
	
	/**
	 * Marks the associated event complete and adds a task to
	 * resume servicing the channel's event queue.  This must be
	 * called outside of a transaction.
	 */
	protected void completed() {
	    
	    try {
	      channelService.runTransactionalTask(
		new AbstractKernelRunnable("MarkChannelEventCompleted") {
		    public void run() {
			ChannelEvent event = (ChannelEvent)
			    getObjectForId(eventRefId);
			if (event != null) {
			    event.completed();
			} else {
			    // This shouldn't happen
			    logger.log(
				Level.SEVERE,
				"channel name:{0} session:{1} channel event " +
				"removed before completed", name,
				HexDumper.toHexString(
				    sessionRefId.toByteArray()));
			}
		    }
		} ); 
	    } catch (Exception e) {
		// TBD: This shouldn't happen, so log message?
	    } finally {
		channelService.addServiceEventQueueTask(channelRefId);
	    }
	}
    }

    /**
     * A non-transactional task to send a join notification to a session's
     * node, allowing for the possibility of the session relocating while
     * the join message is in transit.
     */
    private static class ProcessJoinTask extends ProcessChannelEventTask {

	/**
	 * Constructs an instance.  This contructor must be called within a
	 * transaction.
	 */
	ProcessJoinTask(ChannelImpl channel, BigInteger eventQueueRefId,
			JoinEvent joinEvent, ClientSessionImpl session,
			BigInteger sessionRefId)
	{
	    super(channel, eventQueueRefId, joinEvent, session, sessionRefId);
	}

	/** {@inheritDoc} <p> Sends a join notification. */
	protected boolean sendNotification(ChannelServer server)
	    throws IOException
	{
	    boolean success =
		server.join(name, channelRefId, delivery, sessionRefId);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "Sent join, name:{0} channel:{1} session:{2} " +
		    "coordinator:{3} returned {4}", name,
		    HexDumper.toHexString(channelRefId.toByteArray()),
		    HexDumper.toHexString(sessionRefId.toByteArray()),
		    getLocalNodeId(), success);
	    }
	    return success;
	}

	/** {@inheritDoc} <p>
	 *
	 * This implementation also adds the session's node ID
	 * (if changed) to the channel's set of server node IDs.
	 */
	protected boolean updateSessionNodeId() {
	    return updateSessionNodeId(true);
	}

	/** {@inheritDoc} */
	protected void completed() {
	    long eventQueueTimestamp = getEventQueueTimestamp();
	    if (eventQueueTimestamp > timestamp) {
		ChannelServiceImpl.getChannelService().cacheEvent(
		    ChannelEventType.JOIN, channelRefId, sessionRefId,
		    timestamp, eventQueueTimestamp);
	    }
	    super.completed();
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
	LeaveEvent(ClientSession session, EventQueue eventQueue) {
	    super(eventQueue.getTimestamp());
	    sessionRefId = getSessionRefId(session);
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(ChannelImpl channel) {
	    assert isProcessing() && !isCompleted();
	    ClientSessionImpl session =
		(ClientSessionImpl) getObjectForId(sessionRefId);
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return completed();
	    }
	
	    ProcessLeaveTask task =
		new ProcessLeaveTask(channel, channel.eventQueueRef.getId(),
				     this, session, sessionRefId);
	    ChannelServiceImpl.getChannelService().addChannelTask(
		channel.channelRefId, task);
	    return isCompleted();
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return getClass().getName() + ": " +
		HexDumper.toHexString(sessionRefId.toByteArray());
	}
    }

    private static class ProcessLeaveTask extends ProcessChannelEventTask {

	/**
	 * Constructs an instance.  This contructor must be called within a
	 * transaction.
	 */
	ProcessLeaveTask(ChannelImpl channel, BigInteger eventQueueRefId,
			 LeaveEvent leaveEvent, ClientSessionImpl session,
			 BigInteger sessionRefId)
	{
	    super(channel, eventQueueRefId, leaveEvent, session, sessionRefId);
	}

	/** {@inheritDoc} <p> Sends a leave notification. */
	protected boolean sendNotification(ChannelServer server)
	    throws IOException
	{
	    boolean success = server.leave(channelRefId, sessionRefId);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "Sent leave, channel:{0} session:{1} " +
		    "coordinator:{2} returned {3}",
		    HexDumper.toHexString(channelRefId.toByteArray()),
		    HexDumper.toHexString(sessionRefId.toByteArray()),
		    getLocalNodeId(), success);
	    }
	    return success;
	}
	
	/** {@inheritDoc} */
	protected boolean updateSessionNodeId() {
	    return updateSessionNodeId(false);
	}

	/** {@inheritDoc} */
	protected void completed() {
	    long eventQueueTimestamp = getEventQueueTimestamp();
	    if (eventQueueTimestamp > timestamp) {
		ChannelServiceImpl.getChannelService().cacheEvent(
		    ChannelEventType.LEAVE, channelRefId, sessionRefId,
		    timestamp, eventQueueTimestamp);
	    }
	    super.completed();
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
	LeaveAllEvent(EventQueue eventQueue) {
	    super(eventQueue.getTimestamp());
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(ChannelImpl channel) {

	    Set<Long> serverNodeIds = channel.getServerNodeIds();
	    channel.clearServerNodeIds();
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    final BigInteger channelRefId = channel.channelRefId;
	    for (final long nodeId : serverNodeIds) {
		channelService.addChannelTask(
		    channelRefId,					      
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.leaveAll(channelRefId);
			    }
			} },
		    nodeId);
	    }
	    return completed();
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
    static class SendEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	/** The channel message. */
	private final byte[] message;
	/** The sender's session ID, or null. */
	private final BigInteger senderRefId;
	/** Indicates whether the sender is known to be a channel member
	 * when this event was constructed. */
	private boolean isChannelMember;

	/**
	 * Constructs a send event with the given {@code senderRefId} and
	 * {@code message}.
	 *
	 * @param senderRefId a sender's session ID, or {@code null}
	 * @param message a message
	 * @param eventQueue the channel's event queue
	 * @param isChannelMember {@code true} if the sender is currently
	 *	  known to be a member of the associated channel
	 */
	SendEvent(
	    BigInteger senderRefId, byte[] message, EventQueue eventQueue,
	    boolean isChannelMember)
	{
	    super(eventQueue.getTimestampAndIncrement());
	    this.senderRefId = senderRefId;
	    this.message = message;
	    this.isChannelMember = isChannelMember;
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
	public boolean serviceEvent(ChannelImpl channel) {

	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    if (senderRefId != null) {
		/*
		 * Sender is a client, so verify that the sending session
		 * is a member of the channel.
		 */
		ClientSessionImpl sender =
		    (ClientSessionImpl) getObjectForId(senderRefId);
		if (sender == null) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "send attempt by disconnected session:{0} " +
			    "to channel:{1}",
			    HexDumper.toHexString(senderRefId.toByteArray()),
			    channel);
		    }
		    return completed();
		    
		} else {
		    EventQueue queue = channel.eventQueueRef.get();
		    if (queue.isCoordinatorRecovering(timestamp)) {
			getDataService().markForUpdate(this);
			isChannelMember =
			    isChannelMemberRemoteCheck(
 				channel.channelRefId, senderRefId,
				getNodeId(sender), timestamp);
		    }

		    if (!channelService.isChannelMember(
			    channel.channelRefId, senderRefId,
			    isChannelMember, timestamp))
		    {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(
				Level.FINEST,
				"send attempt by non-member session:{0} " +
				"to channel:{1}",
				HexDumper.toHexString(
				    senderRefId.toByteArray()),
				channel);
			}
			return completed();
		    }
		} 
	    }
	    
	    /*
	     * Enqueue a channel task to forward the message to the
	     * channel's servers for delivery.
	     */
	    final BigInteger channelRefId = channel.channelRefId;
	    final byte deliveryOrdinal = (byte) channel.delivery.ordinal();
	    for (final long nodeId : channel.servers) {
		channelService.addChannelTask(
		    channelRefId,
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.send(channelRefId, message,
					    deliveryOrdinal);
			    }
			} },
		    nodeId);
	    }

	    return completed();
	    // TBD: need to add a task to update queue that all
	    // channel servers have been notified of
	    // the 'send'.
	}

	/** Use the message length as the cost for sending messages.
	 * 
	 * Only return a non-zero cost if the message hasn't started
	 * processing yet so that the cost calculation can be
	 * idempotent.
	 */
	@Override
	int getCost() {
	    return processingOnNodeId == -1 ? message.length : 0;
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
	    super(0);
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(ChannelImpl channel) {

	    final BigInteger channelRefId = channel.channelRefId;
	    Set<Long> serverNodeIds = channel.getServerNodeIds();
	    channel.removeChannel();
	    final ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    for (final long nodeId : serverNodeIds) {
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
	    return completed();
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
    private static EventQueue getEventQueue(
 	long nodeId, BigInteger channelRefId)
    {
	EventQueue eventQueue =
	    getEventQueuesMap(nodeId).get(channelRefId.toString());
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
	    eventQueue.serviceEventQueue();
	}
    }

    /**
     * Returns an iterator for channels that are coordinated on the node with
     * the specified {@code nodeId}.
     */
    private static Iterator<String> getChannelsIterator(long nodeId) {
	return getEventQueuesMap(nodeId).keySet().iterator();
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
	private final long failedNodeId;

	/** The iterator for channels on the failed node. */
	private final Iterator<String> channelIter;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code failedNodeId}.
	 */
	ReassignCoordinatorsTask(long failedNodeId) {
	    this.failedNodeId = failedNodeId;
	    this.channelIter = getChannelsIterator(failedNodeId);
	}

	/**
	 * Reassigns the next coordinator for the {@code failedNodeId} to
	 * another node with member sessions (or the local node if there
	 * are no member sessions), and then reschedules this task to
	 * reassign the next coordinator.  If there are no more
	 * coordinators for the specified {@code failedNodeId}, then no
	 * action is taken.
	 */
	public void run() {
	    if (!channelIter.hasNext()) {
		return;
	    }

	    WatchdogService watchdogService =
		ChannelServiceImpl.getWatchdogService();
	    TaskService taskService = ChannelServiceImpl.getTaskService();
	    BigInteger channelRefId = new BigInteger(channelIter.next());
	    channelIter.remove();
	    ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	    if (channel != null) {
		channel.reassignCoordinator(failedNodeId);
		
		/*
		 * If other channel servers have failed, remove the failed
		 * server node ID from the channel too.  This covers the
		 * case where a channel coordinator (informed of a node
		 * failure) fails before it has a chance to schedule a task
		 * to remove the server node ID for another failed node
		 * (cascading failure during recovery).
		 */
		for (long serverNodeId : channel.getServerNodeIds()) {
		    Node serverNode = watchdogService.getNode(serverNodeId);
		    if (serverNode == null || !serverNode.isAlive()) {
			channel.removeServerNodeId(serverNodeId);
		    }
		}
	    }

	    /*
	     * Schedule a task to reassign the next channel coordinator, or
	     * if done with coordinator reassignment, remove the recovered
	     * node's  mapping from the node-to-event-queues map.
	     */
	    if (channelIter.hasNext()) {
		taskService.scheduleTask(this);
	    }
	}
    }

    /**
     * A persistent task to remove a failed node, from locally coordinated
     * channels.  This task handles a single locally-coordinated channel,
     * and then schedules another task to handle the next one.
     */
    static class RemoveFailedNodeFromLocalChannelsTask
	implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	/** The failed node. */
	private final long failedNodeId;
	
	/** The iterator for locally-coordinated channels.*/
	private final Iterator<String> iter;
	
	/**
	 * Constructs an instance with the specified {@code localNodeId}
	 * and {@code failedNodeId}.
	 */
	RemoveFailedNodeFromLocalChannelsTask(
	    long localNodeId, long failedNodeId)
	{
	    this.failedNodeId = failedNodeId;
	    this.iter = getChannelsIterator(localNodeId);
	}

	/**
	 * Finds the next locally-coordinated channel, removes the failed
	 * node from that channel, and reschedules this task to handle the
	 * next locally-coordinated channel. If there are no more
	 * locally-coordinated channels, then this task takes no action.
	 */
	public void run() {
	    if (iter == null || !iter.hasNext()) {
		return;
	    }
	    
	    BigInteger channelRefId = new BigInteger(iter.next());
	    TaskService taskService = ChannelServiceImpl.getTaskService();
	    ChannelImpl channel =
		(ChannelImpl) getObjectForId(channelRefId);
	    if (channel != null) {
		channel.removeServerNodeId(failedNodeId);
	    }
	    // Schedule a task to remove failed node from next
	    // locally coordinated channel.
	    if (iter.hasNext()) {
		taskService.scheduleTask(this);
	    }
	}
    }


    private static boolean isChannelMemberRemoteCheck(
	BigInteger channelRefId, BigInteger sessionRefId,
	long nodeId, long timestamp)
    {
	try {
	    ChannelServer server = getChannelServer(nodeId);
	    MembershipStatus status =
		server.isMember(channelRefId, sessionRefId);
	    switch (status) {
		
	    case MEMBER:
		return true;
		
	    case NON_MEMBER:
		return false;
		
	    case UNKNOWN:
		// FIXME: this is wrong; need to resample the session's
		// node ID to see if it changed.  If it hasn't changed,
		// then it is disconnected, otherwise, need to check
		// membership with the new session's new node.
		return false;
	    }
	} catch (IOException e) {
	    // FIXME: this is wrong; need to resample the session's node
	    // ID to see if it changed.  If it hasn't changed, then it is
	    // disconnected, otherwise, need to check membership with the
	    // new session's new node.
	}
	return false;
    }
}
