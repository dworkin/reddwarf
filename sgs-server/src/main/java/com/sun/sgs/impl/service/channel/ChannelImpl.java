/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
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
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.service.channel.ChannelServer.MembershipStatus;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl.MembershipEventType;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedMap;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.KernelCallable;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.kernel.KernelRunnable;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.sun.sgs.impl.service.channel.ChannelServiceImpl.
    getObjectForId;

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
 * <dt> <i>Map:</i> <b><code>savedMessagesMap</code></b> <br>
 *	<i>Prefix:</i> <code>{@value
 *	#SAVED_MESSAGES_MAP_PREFIX}<i>channelId.</i></code> <br>
 *	<i>Key:</i> <i>{@code timestamp}</i> (as string form of
 *	{@code Long})<br>
 *	<i>Value:</i> <b>{@code ChannelMessageInfo}</b>
 *
 * <dd style="padding-top: .5em">Map for accessing saved messages for a
 *	given channel by timestamp.  The map is used when a client
 *	session relocates to a new node and the channel service discovers
 *	that the session missed one or more channel messages during
 *	relocation.<p> 
 * </dl> <p>
 */
final class ChannelImpl implements ManagedObject, Serializable {

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel.";

    /** The channels map prefix. */
    static final String CHANNELS_MAP_PREFIX = PKG_NAME + "name.";

    /** An event queue map prefix. */
    static final String EVENT_QUEUE_MAP_PREFIX = PKG_NAME + "eventQueue.";

    /** The saved messages map prefix. */
    static final String SAVED_MESSAGES_MAP_PREFIX = PKG_NAME + "message.";

    /** The empty channel membership set. */
    static final Set<BigInteger> EMPTY_CHANNEL_MEMBERSHIP =
	Collections.emptySet();

    /** The random number generator for choosing a new coordinator. */
    private static final Random random = new Random();

    /** The map of channels, keyed by name. */
    private static BindingKeyedMap<ChannelImpl> channelsMap = null;

    /** The channel name. */
    private final String name;

    /** The ID from a managed reference to this instance. */
    final BigInteger channelRefId;

    /** The wrapped channel instance. */
    private final ManagedReference<ChannelWrapper> wrappedChannelRef;

    /** The reference to this channel's listener. */
    private final ManagedReference<ChannelListener> listenerRef;

    /** The delivery requirement for messages sent on this channel. */
    private final Delivery delivery;

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

    /** Indicates whether this coordinator is reassigned so that some
     *  actions can be performed by the new coordinator. */
    private boolean isCoordinatorReassigned;

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
     * {@code name}, {@code listener}, {@code delivery} guarantee,
     * write buffer capacity, and channel wrapper.
     *
     * @param name a channel name
     * @param listener a channel listener
     * @param delivery a delivery guarantee
     * @param writeBufferCapacity the capacity of the write buffer, in bytes
     * @param channelWrapper the previous channel wrapper, or {@code null} if
     *	      the channel is being created for the first time
     */
    private ChannelImpl(String name, ChannelListener listener,
			  Delivery delivery, int writeBufferCapacity,
			  ChannelWrapper channelWrapper)
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
	if (channelWrapper == null) {
	    channelWrapper = new ChannelWrapper(ref);
	} else {
	    channelWrapper.setChannelRef(ref);
	}
	this.wrappedChannelRef = dataService.createReference(channelWrapper);
	this.channelRefId = ref.getId();
	this.coordNodeId = getLocalNodeId();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl:{0}", channelRefId);
	}
	getChannelsMap().putOverride(name, this);
	EventQueue eventQueue = new EventQueue(this);
	eventQueueRef = dataService.createReference(eventQueue);
	getEventQueuesMap(coordNodeId).
	    put(channelRefId.toString(), eventQueue);
    }
    
    /** Returns the data service. */
    private static DataService getDataService() {
	return ChannelServiceImpl.getInstance().getDataService();
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
	return newInstance(name, listener, delivery, writeBufferCapacity, null);
    }
    
    /**
     * Constructs a new {@code Channel} with the given {@code name}, {@code
     * listener}, {@code delivery} guarantee, write-buffer capacity and
     * {@code channelWrapper}.  If the {@code channelWrapper} is null,
     * then this is a newly-created channel.  Otherwise, the channel is
     * being "recreated" due to a "leaveAll" event.
     */
    private static Channel newInstance(String name,
				       ChannelListener listener,
				       Delivery delivery,
				       int writeBufferCapacity,
				       ChannelWrapper channelWrapper)
    {
	ChannelImpl channel =
	    new ChannelImpl(name, listener, delivery,
			    writeBufferCapacity, channelWrapper);
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
	
	if (!servers.isEmpty()) {
	    channelMembers =
		ChannelServiceImpl.getInstance().collectChannelMembership(
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
	    updateMaxMessageLength(session);
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
    void join(final Set<? extends ClientSession> sessions) {
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
	     */
	    EventQueue eventQueue = eventQueueRef.get();
	    for (ClientSession session : sessions) {
		updateMaxMessageLength(session);
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
     * Sets the channel's maximum message length to the client session's
     * maximum message length if the session's maximum is lower than the
     * existing maximum for the channel.
     *
     * @param	session a client session joining the channel
     */
    private void updateMaxMessageLength(ClientSession session) {
	int sessionMaxMessageLength = session.getMaxMessageLength();
	if (maxMessageLength > sessionMaxMessageLength) {
	    getDataService().markForUpdate(this);
	    maxMessageLength = sessionMaxMessageLength;
	}
    }

    /**
     * Returns the message timestamp of the last message processed by
     * this channel.
     *
     * @return the current message timestamp processed by this
     * channel's event queue
     */
    long getCurrentMessageTimestamp() {
	return eventQueueRef.get().currentTimestamp;
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
    private void addEvent(ChannelEvent event) {

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
		ChannelServiceImpl.getInstance();
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
    void leave(final Set<? extends ClientSession> sessions) {
	try {
	    checkClosed();
	    if (sessions == null) {
		throw new NullPointerException("null sessions");
	    }

	    /*
	     * Enqueue leave requests, each with underlying (unwrapped)
	     * client session object.
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
     * Closes the current channel, and creates a new channel (with a new
     * channel ID) that uses the existing channel's wrapper.  Since the
     * application still uses the same channel wrapper for this channel,
     * the application is not effected.
     */
    void leaveAll() {
	try {
	    checkClosed();
	    close(false);
	    ChannelListener listener =
		listenerRef != null ? listenerRef.get() : null;
	    newInstance(name, listener, delivery, writeBufferCapacity,
			getWrappedChannel());
	    logger.log(Level.FINEST, "leaveAll returns");
	
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e, "leaveAll throws");
	    throw e;
	}
    }
    
    /** Implements {@link Channel#send(ClientSession,ByteBuffer)}.
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
		ChannelServiceImpl.getInstance().
		    isLocalChannelMember(channelRefId, senderRefId) :
		true;
	    addEvent(
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
	    listenerRef.get().receivedMessage(
		getWrappedChannel(), sender, message.asReadOnlyBuffer());
	}
    }

    /**
     * Enqueues a close event to this channel's event queue and notifies
     * this channel's coordinator to service the event.  This method is
     * invoked with {@code true} by this channel's {@code ChannelWrapper}
     * when the application removes the wrapper object, and is invoked with
     * {@code false} by this channel when the application invokes the
     * {@link #leaveAll} method.
     *
     * @param	removeName if {@code true}, the channel's name binding
     *		is removed when the channel's persistent structures
     *		are cleaned up, otherwise, the channel's name binding is
     *		not removed
     */
    void close(boolean removeName) {
	checkContext();
	getDataService().markForUpdate(this);
	if (!isClosed) {
	    /*
	     * Enqueue close event.
	     */
	    addEvent(new CloseEvent(removeName, eventQueueRef.get()));
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
	return getClass().getName() + "[" + channelRefId + "]";
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
     * Returns the write buffer capacity for this channel.
     *
     * @return the write buffer capacity
     */
    private int getWriteBufferCapacity() {
        return writeBufferCapacity;
    }

    /**
     * Returns the ID for the specified {@code session}.
     */
    private static BigInteger getSessionRefId(ClientSession session) {
	return getDataService().createReference(session).getId();
    }

    /**
     * Returns the node ID for the specified {@code session}.  If the
     * session is relocating, this method returns the node ID that
     * the session is relocating to.
     */
    private static long getNodeId(ClientSessionImpl session) {
	return
	    session.isRelocating() ?
	    session.getRelocatingToNodeId() :
	    session.getNodeId();
    }

    /**
     * Returns the wrapped channel for this instance.
     */
    private ChannelWrapper getWrappedChannel() {
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
     * Returns {@code true} if the channel is closed, and {@code false}
     * otherwise.
     */
    boolean isClosed() {
	return isClosed;
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
    boolean isCoordinator() {
	return coordNodeId == getLocalNodeId();
    }

    /**
     * Returns {@code true} if the local node is the coordinator for this
     * channel, and returns {@code false} otherwise.  If the coordinator
     * was just reassigned to this node (i.e., the {@code
     * isCoordinatorReassigned} field is {@code true}) and this channel
     * supports reliable message delivery, then schedule a new task to reap
     * saved messages.
     */
    private boolean checkCoordinator() {
	if (!isCoordinator()) {
	    return false;
	} else {
	    if (isCoordinatorReassigned) {
	        getDataService().markForUpdate(this);
		if (isReliable()) {
		    SavedMessageReaper.scheduleNewTask(channelRefId, false);
		}
		isCoordinatorReassigned = false;
	    }
	    return true;
	}
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
	checkClosed();
	if (servers.add(nodeId)) {
	    getDataService().markForUpdate(this);
	}
    }
    
    /**
     * Removes the specified {@code nodeId} from the set of server nodes
     * for this channel.
     *
     * @param	nodeId a server node's ID
     */
    void removeServerNodeId(long nodeId) {
	if (servers.remove(nodeId)) {
	    getDataService().markForUpdate(this);
	}
    }

    /**
     * Returns the map of saved messages for this channel, for looking up
     * messages by timestamp.
     */
    private static BindingKeyedMap<ChannelMessageInfo>
	getSavedMessagesMap(BigInteger channelRefId)
    {
	return newMap(SAVED_MESSAGES_MAP_PREFIX + channelRefId + ".");
    }

    /**
     * Saves the specified channel {@code message} with the specified
     * {@code timestamp}. Reliable messages are saved for a period of time
     * (the length of time it takes to move a client session) so that
     * channel messages missed during relocation can be obtained and
     * delivered to a relocated client session.
     */
    private void saveMessage(byte[] message, long timestamp) {
	assert isReliable();
	BindingKeyedMap<ChannelMessageInfo> savedMessagesMap =
	    getSavedMessagesMap(channelRefId);
	ChannelMessageInfo messageInfo =
	    new ChannelMessageInfo(message, timestamp);
	savedMessagesMap.put(getTimestampEncoding(timestamp), messageInfo);
    }

    /**
     * Returns a list containing saved channel messages (if any) with
     * timestamps between {@code fromTimestamp} and {@code toTimestamp}
     * inclusive.  If {@code fromTimestamp} is greater than {@code
     * toTimestamp} this method returns {@code null}.
     */
    List<ChannelMessageInfo> getChannelMessages(
	long fromTimestamp, long toTimestamp)
    {
	assert isReliable();
	List<ChannelMessageInfo> messages = null;
	if (fromTimestamp <= toTimestamp) {
	    messages = new ArrayList<ChannelMessageInfo>(
			    (int) (toTimestamp - fromTimestamp + 1));
	    BindingKeyedMap<ChannelMessageInfo> savedMessagesMap =
		getSavedMessagesMap(channelRefId);
	    for (long ts = fromTimestamp; ts <= toTimestamp; ts++) {
		ChannelMessageInfo messageInfo =
		    savedMessagesMap.get(getTimestampEncoding(ts));
		if (messageInfo != null) {
		    messages.add(messageInfo);
		}
	    }
	}
	return messages;
    }

    /**
     * Returns an encoding for the specified {@code timestamp}
     * that preserves ascending timestamp order with
     * lexicographically-ordered keys.  The encoding consists
     * of the following:
     * 
     * <ul>
     * <li> a single hex digit whose value is one less than the number of
     *      hex digits (leading zero digits are not included),
     * <li> a hyphen,
     * <li> the hex encoding of the timestamp with leading zero digits
     *      stripped.
     * </ul>
     */
    private static String getTimestampEncoding(long timestamp) {
	String hexString = Long.toHexString(timestamp);
	int numHexits =  hexString.length();
	StringBuilder builder = new StringBuilder(2 + numHexits);
	builder.
	    append(Character.forDigit(numHexits - 1, 16)).
	    append('-').
	    append(hexString);
	return builder.toString();
    }
    
    /**
     * Contains a saved channel message with its associated timestamp
     * and expiration time.
     */
    static class ChannelMessageInfo
	implements ManagedObject, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	/** The channel message. */
	final byte[] message;
	/** The message timestamp. */
	final long timestamp;
	/** The message's expiration time. */
	private final long expiration;

	/**
	 * Constructs an instance with the specified {@code message}
	 * and {@code timestamp}.
	 */
	ChannelMessageInfo(byte[] message, long timestamp) {
	    this.message = message;
	    this.timestamp = timestamp;
	    this.expiration =
		System.currentTimeMillis() +
		ChannelServiceImpl.getInstance().sessionRelocationTimeout;
	}

	/**
	 * Returns {@code true} if the channel message has expired
	 * (that is, its expiration time has passed).
	 */
	boolean isExpired() {
	    return expiration <= System.currentTimeMillis();
	}
    }

    /**
     * A (periodic) task to reap messages saved past their expiration time.
     */
    private static final class SavedMessageReaper
	implements KernelRunnable, Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;
	
	/** The channel's ID. */
	private final BigInteger channelRefId;
	/** Indicates whether this task is durable. */
	private final boolean isDurable;

	/**
	 * Constructs an instance with the specified {@code channelRefId}.
	 * Use the {@code scheduleNewTask} method to construct an instance and
	 * schedule the instance as a periodic task.
	 */
	private SavedMessageReaper(BigInteger channelRefId, boolean isDurable) {
	    this.channelRefId = channelRefId;
	    this.isDurable = isDurable;
	}

	/** {@inheritDoc} */
	public String getBaseTaskType() {
	    return getClass().getName();
	}
	
	/**
	 * Iterates through the saved message map, removing messages
	 * saved past their expiration time.  Iteration over a {@code
	 * BindingKeyedMap} returns bindings in lexicographical order.
	 * Timestamps are encoded to preserve ascending timestamp order
	 * with lexicographically-ordered keys, so the messages will be
	 * returned in ascending timestamp order.
	 */
	public void run() {
	    BindingKeyedMap<ChannelMessageInfo> savedMessages =
		getSavedMessagesMap(channelRefId);
	    if (savedMessages.isEmpty()) {
		// Saved messages no longer exist, so "cancel" periodic task.
		return;
	    } else {
		// Remove messages saved past their expiration time.
		DataService dataService = getDataService();
		TaskManager taskManager = AppContext.getTaskManager();
		Iterator<ChannelMessageInfo> iter =
		    savedMessages.values().iterator();
		while (taskManager.shouldContinue() && iter.hasNext()) {
		    ChannelMessageInfo messageInfo = iter.next();
		    if (messageInfo.isExpired()) {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(
				Level.FINEST,
				"Removing saved message, channel:{0} " +
				"timestamp:{1}", channelRefId,
				messageInfo.timestamp);
			}
			iter.remove();
			dataService.removeObject(messageInfo);
		    } else {
			break;
		    }
		}
		if (!savedMessages.isEmpty()) {
		    scheduleTask();
		}
	    }
	}

	private void scheduleTask() {
	    TaskService taskService = 
		ChannelServiceImpl.getTaskService();
	    if (isDurable) {
		taskService.scheduleTask(this, 1000L);
	    } else {
		taskService.scheduleNonDurableTask(this, 1000L, true);
	    }
	}

	/**
	 * Creates a new message reaper and schedules it to run.
	 *
	 * @param channelRefId a channel ID
	 * @param isDurable if {@code true} the task should be
	 *	  scheduled as a durable task, otherwise it should be
	 *	  scheduled as a non-durable task
	 */
	static void scheduleNewTask(
	    BigInteger channelRefId, boolean isDurable)
	{
	    (new SavedMessageReaper(channelRefId, isDurable)).scheduleTask();
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
     * 2) Sends out a 'serviceEventQueue' request to the new
     * coordinator to restart this channel's event processing.
     */
    private void reassignCoordinator(long failedCoordNodeId) {
	DataService dataService = getDataService();
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
	dataService.markForUpdate(this);
	coordNodeId = chooseCoordinatorNode();
	isCoordinatorReassigned = true;
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(
		Level.FINER,
		"channel:{0} reassigning coordinator from:{1} to:{2}",
		channelRefId, failedCoordNodeId, coordNodeId);
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
	return ChannelServiceImpl.getInstance().getChannelServer(nodeId);
    }

    /**
     * Returns a set containing the node IDs of the channel servers for
     * this channel. 
     */
    private Set<Long> getServerNodeIds() {
	return new HashSet<Long>(servers);
    }
    
    /**
     * Removes the channel object, the channel listener wrapper (if we
     * created a wrapper for it), and the event queue and associated
     * binding from the data store.  If {@code removeName} is {@code
     * true}, the channel's name binding is also removed.  This method
     * is called when {@code leaveAll} is invoked on the channel (in
     * which case the channel's name binding is not removed) and is
     * called when the channel is closed (in which case the channel's
     * name binding is removed).
     */
    private void removeChannel(boolean removeName) {
	DataService dataService = getDataService();
	if (removeName) {
	    getChannelsMap().removeOverride(name);
	}
	dataService.removeObject(this);
	if (listenerRef != null) {
	    ChannelListener maybeWrappedListener = null;
	    try {
		maybeWrappedListener = listenerRef.get();
	    } catch (ObjectNotFoundException ignore) {
		// listener already removed
	    }
	    if (maybeWrappedListener instanceof ManagedSerializable) {
		dataService.removeObject(maybeWrappedListener);
	    }
	}
	BindingKeyedMap<EventQueue> eventQueuesMap =
	    getEventQueuesMap(coordNodeId);
	eventQueuesMap.removeOverride(channelRefId.toString());
	EventQueue eventQueue = eventQueueRef.get();
	dataService.removeObject(eventQueue);
	if (isReliable()) {
	    SavedMessageReaper.scheduleNewTask(channelRefId, true);
	}
    }

    /**
     * Returns {@code true} if this channel supports reliable message
     * delivery, otherwise returns {@code false}.
     */
    private boolean isReliable() {
	return
	    delivery.equals(Delivery.RELIABLE) ||
	    delivery.equals(Delivery.UNORDERED_RELIABLE);
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
     * The channel's event queue.<p>
     *
     * Each channel event (join, leave, send, close) is assigned a
     * <i>timestamp</i> when the event is added to the channel's event
     * queue.  The current timestamp records the timestamp of the latest
     * send event that the queue has started to process. The initial event
     * timestamp is <code>1</code>.  A send event increments the next
     * timestamp, so all future join and leave events will have a later
     * timestamp.  Any join and leave events that are immediately prior to
     * the latest send event will share the send event's timestamp. <p>
     *
     * In order to implement reliable join, leave, send, and close events,
     * the event queue doesn't process one event until it has completed the
     * processing of all previous events, which means sucessfully
     * delivering those events to all nodes that need to be notified (for
     * example, in the case of a send event, that would be notifying all
     * nodes with sessions joined to the channel). When the event queue
     * assigns a timestamp to the next send event, the event queue has
     * already processed all previous join and leave events, up to and
     * including ones with that send event's timestamp.
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
	/** The next timestamp to assign to an event. */
	private long nextTimestamp = 1;
	/** The timestamp beyond which membership does not have to be
	 * verified with the node to which a session is connected. */
	private long coordinatorAssignmentTimestamp = 0;
	/** The timestamp for the last event processed. */
	private long currentTimestamp = 0;

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
	 * Notifies this event queue that its channel's coordinator has
	 * been reassigned and is considered to be recovering.  This event
	 * queue notes the "next timestamp" (to be assigned to an event) so
	 * that the event queue can perform specific recovery actions for
	 * events until it processes all events (if any) with timestamps
	 * less than the timestamp at the moment the coordinator was
	 * reassigned.
	 */
	void coordinatorReassigned() {
	    getDataService().markForUpdate(this);
	    coordinatorAssignmentTimestamp =
		isEmpty() ? 0 : nextTimestamp;
	}

	/**
	 * Returns {@code true} if the coordinator is considered recovering
	 * for the specified {@code timestamp}, and returns {@code false}
	 * otherwise.
	 *
	 * @param timestamp an event timestamp
	 * @return {@code true} if the coordinator is considered recovering
	 * for the specified {@code timestamp}
	 */
	boolean isCoordinatorRecovering(long timestamp) {
	    return timestamp < coordinatorAssignmentTimestamp;
	}

	/**
	 * Attempts to enqueue the specified {@code event}, and returns
	 * {@code true} if successful, and {@code false} otherwise.  If
	 * this node coordinates the channel and the channel's event queue
	 * is empty, then start processing the event immediately.  If the
	 * event is successfully added to the queue, then sent a
	 * notification to the channel's coordinator to service the event
	 * queue.
	 *
	 * @param event the event
	 * @param channel the channel instance
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
		if (startProcessingEvent(channel, event)) {
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
	
	/** Returns the timestamp to assign to the next event. */
	long getNextTimestamp() {
	    return nextTimestamp;
	}

	/**
	 * Returns the timestamp to assign to the next event,
	 * then increments it.
	 */
	long getNextTimestampAndIncrement() {
	    getDataService().markForUpdate(this);
	    return nextTimestamp++;
	}

	long getCurrentTimestamp() {
	    return currentTimestamp;
	}

	/**
	 * Services the channel event queue.  The coordinator processes
	 * events as follows: <ul>
	 *
	 * <li> If the event at the head of the queue has not started
	 * processing, then it marks the event's state as 'processing' by
	 * invoking the event's {@code processing} method, and then
	 * initiates processing by invoking the event's {@code
	 * serviceEvent} method passing the channel instance.
	 *
	 * <li> If the event at the head of the queue has completed
	 * processing (its {@code serviceEvent} method or {@code
	 * isCompleted} method returns {@code true}), the event is removed
	 * from the queue, and the next event can be serviced.
	 * </ul>
	 * 
	 * An event remains in the queue until it has completed processing.
	 * Reliable events (such as join, leave, and reliable send events)
	 * require a delivery acknowledgment in order to be reliable in the
	 * face of coordinator crash.  Therefore the event at the head of
	 * the queue starts processing inside a transaction, performing any
	 * necessary persistent updates, then performs non-transactional
	 * actions after the transaction commits (such as delivering a
	 * notification), and then marks the event itself as 'completed'
	 * once the non-transactional actions have completed (such as a
	 * notification being acknowledged). At this point, the event can
	 * be removed from the queue, and the next event can be
	 * serviced. <p>
	 *
	 * If the channel coordinator's node crashes while the event at the
	 * head of the queue is being processed, the channel's new
	 * coordinator restarts event processing.
	 */
	void serviceEventQueue() {
	    ChannelImpl channel = getChannel();
	    if (!channel.checkCoordinator()) {
		logger.log(
		    Level.WARNING,
		    "Attempt at node:{0} channel:{1} to service events; " +
		    "instead of current coordinator:{2}",
		    getLocalNodeId(), channel.channelRefId,
		    channel.coordNodeId);
		return;
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "coordinator:{0} channelId:{1}",
			   getLocalNodeId(), channel.channelRefId);
	    }
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getInstance();

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
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "coordinator:{0} channelId:{1} " +
				   "no more events",
				   getLocalNodeId(), channel.channelRefId);
		    }
		    return;
		} else if (event.isCompleted()) {
		    // Remove completed event and get next event to
		    // process. Return if there are no more events.
		    removeCompletedEvent(channel, eventQueue, event);
		    event = eventQueue.peek();
		    if (event == null) {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.log(Level.FINEST,
				       "coordinator:{0} channelId:{1} " +
				       "no more events",
				       getLocalNodeId(), channel.channelRefId);
			}
			return;
		    }
		} else if (event.isProcessing()) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   "coordinator:{0} channelId:{1} " +
				   "event:{2} is already processing",
				   getLocalNodeId(), channel.channelRefId,
				   event);
		    }
		    return;
		} 

		// Mark event as "processing", and then service event.
		completed = startProcessingEvent(channel, event);
		if (completed) {
		    removeCompletedEvent(channel, eventQueue, event);
		    
		}
		
	    } while (completed && --eventsPerTxn > 0);
	
	    if (eventQueue.peek() != null) {
		channelService.
		    addServiceEventQueueTaskOnCommit(channel.channelRefId);
	    }
	}

	/**
	 * Starts processing the specified {@code event}, updating the
	 * current message timestamp if the event is a channel "send" event.
	 */
	private boolean startProcessingEvent(ChannelImpl channel,
					     ChannelEvent event)
	{
	    event.processing();
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "coordinator:{0} channelId:{1} " +
			   "processing event:{2}",
			   getLocalNodeId(), channel.channelRefId,
			   event);
	    }
	    if (event instanceof SendEvent) {
		getDataService().markForUpdate(this);
		currentTimestamp = event.timestamp;
	    }
	    return event.serviceEvent(channel);
	}

	/**
	 * Removes completed event from the event queue.
	 */
	private void removeCompletedEvent(
 	    ChannelImpl channel, ManagedQueue<ChannelEvent> eventQueue,
	    ChannelEvent event)
	{
	    assert eventQueue.peek() == event && event.isCompleted();
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "coordinator:{0} channelId:{1} " +
			   "removing completed event:{2}",
			   getLocalNodeId(), channel.channelRefId, event);
	    }
	    eventQueue.poll();
	    int cost = event.getCost();
	    if (cost > 0) {
		getDataService().markForUpdate(this);
		writeBufferAvailable += cost;
		
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"{0} cleared reservation of {1,number,#} bytes, " +
			"leaving {2,number,#}",
			this, cost, writeBufferAvailable);
		}
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

	/** {@inheritDoc} */
	public String toString() {
	    try {
		ChannelImpl channel = getChannel();
		return "EventQueue[" +
		    "channelId:" + channel.channelRefId +
		    ", name:" + channel.name +
		    "]";
	    } catch (ObjectNotFoundException e) {
		return super.toString();
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
	 * processed. -1 indicates that event hasn't ever started
	 * processing. */
	private long processingOnNodeId = -1;

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
	 * Marks this event as being processed on the local node.
	 */
	void processing() {
	    logger.log(Level.FINEST, "processing event:{0}", this);
	    getDataService().markForUpdate(this);
	    processingOnNodeId = getLocalNodeId();
	}
	
	/**
	 * Marks this event as completed.
	 */
	boolean completed() {
	    logger.log(Level.FINEST, "completed event:{0}", this);
	    try {
		getDataService().markForUpdate(this);
	    } catch (ObjectNotFoundException e) {
		// markForUpdate can throw ONFE if this event has been
		// removed.
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"Marking event:{0} completed throws", this);
		}
	    }
	    completed = true;
	    return completed;
	}

	/**
	 * Returns {@code true} if this event is being processed on this
	 * node, and {@code false} otherwise.  Events are marked as
	 * processing on a given node.  Therefore, if this event is marked
	 * as processing on a given coordinator node, and the channel's
	 * coordinator is reassigned to another node and the coordinator
	 * subsequently invokes this method on this event, the method will
	 * return {@code false}.  This indicates that the event has not yet
	 * started processing on the new coordinator's node, and the
	 * coordinator should (re)start this event's processing on the new
	 * node. 
	 */
	boolean isProcessing() {
	    return processingOnNodeId == getLocalNodeId();
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
	 * Returns a string representation of field:value pairs, separated
	 * by commas.
	 */
	protected String toStringFieldsOnly() {
	    return
		"timestamp:" + timestamp +
		(processingOnNodeId == -1 ? "" : 
		 ", processingOnNodeId:" + processingOnNodeId) +
		", completed: " + isCompleted();
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
	    super(eventQueue.getNextTimestamp());
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

	    JoinNotifyTask task =
		new JoinNotifyTask(channel, this, session, sessionRefId);
	    ChannelServiceImpl.getInstance().addChannelTaskOnCommit(
		    channel.channelRefId, task);
	    return isCompleted();
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return "JoinEvent[" +
		"sessionRefId:" + sessionRefId + ", " +
		toStringFieldsOnly() +
		"]";
		
	}
    }

    private abstract static class NotifyTask extends AbstractKernelRunnable {
	
	protected final ChannelServiceImpl channelService;
	protected final BigInteger channelRefId;
	protected final long timestamp;
	private final BigInteger eventRefId;
	
	/**
	 * Constructs an instance.  This constructor must be called within a
	 * transaction.
	 */
	NotifyTask(ChannelImpl channel, ChannelEvent channelEvent) {
	    super(null);
	    this.channelService = ChannelServiceImpl.getInstance();
	    this.channelRefId = channel.channelRefId;
	    this.eventRefId =
		getDataService().createReference(channelEvent).getId();
	    this.timestamp = channelEvent.timestamp;
	}
	
	/**
	 * Returns the channel associated with this task, or null if the channel
	 * no longer exists. This method must be invoked within a transaction.
	 */
	protected ChannelImpl getChannel() {
	    return (ChannelImpl) getObjectForId(channelRefId);
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
				"channel:{0}: event removed before completed",
				channelRefId);
			}
		    }
		}); 
	    } catch (Exception e) {
		// Transaction schedule will print out warning.
	    } finally {
		channelService.addServiceEventQueueTask(channelRefId);
	    }
	}

	/**
	 * Removes the specified {@code nodeId} from the associated
	 * channel.
	 *
	 * @param nodeId a node ID
	 */
	protected void removeNodeIdFromChannel(final long nodeId) {
	    
	    try {
		channelService.runTransactionalTask(
 		  new AbstractKernelRunnable("removeNodeIdFromChannel") {
		    public void run() {
			ChannelImpl channel = getChannel();
			if (channel != null) {
			    channel.removeServerNodeId(nodeId);
			}
		    }
		  });
		
	    } catch (Exception e) {
		// Transaction scheduler will print out warning.
	    }
	}
    }

    /**
     * A non-transactional task to send a notification to a session's
     * node, allowing for the possibility of the session relocating while
     * the notification is in transit.
     */
    private abstract static class SessionNotifyTask extends NotifyTask {
	
	protected final String name;
	protected final Delivery delivery;
	protected final BigInteger sessionRefId;
	private final BigInteger eventQueueRefId;

	/** The session's node ID.  Initialized during construction
	 * and modified by calls to {@code removeMembershipIfNodeUnchanged}
	 * and {@code remapMembershipIfRelocating} methods.
	 */
	protected volatile long sessionNodeId;

	/**
	 * Constructs an instance.  This constructor must be called within a
	 * transaction.
	 */
	SessionNotifyTask(ChannelImpl channel,
			  ChannelEvent channelEvent,
			  ClientSessionImpl session,
			  BigInteger sessionRefId)
	{
	    super(channel, channelEvent);
	    this.name = channel.name;
	    this.delivery = channel.delivery;
	    this.eventQueueRefId = channel.eventQueueRef.getId();
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
			// Notification was successful
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
			removeNodeIdFromChannel(sessionNodeId);
			if (updateSessionNodeId()) {
			    continue; // relocating
			} else {
			    break; // disconnected
			}
		    }
		    // Wait for transient situation to resolve.
		    try {
			// TBD: make sleep time configurable?
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
	 * Returns {@code true} if when the session's node ID changes, it
	 * should be added to the channel's set of node IDs, and returns
	 * {@code false} otherwise.  This method is invoked inside of a
	 * transaction.
	 *
	 * @return {@code true} if when the session's node ID changes, it
	 *	   should be added to the channel's set of node IDs, and
	 *	   {@code false} otherwise
	 */
	protected abstract boolean addChangedSessionNodeId();
	
	/**
	 * Returns the client session associated with this task, or null if
	 * the session no longer exists.  This method must be invoked
	 * within a transaction.
	 */
	protected ClientSessionImpl getSession() {
	    return (ClientSessionImpl) getObjectForId(sessionRefId);
	}

	/**
	 * Updates this task's {@code sessionNodeId} and returns {@code true}
	 * if the session's node ID has changed, and returns {@code false} if
	 * the session's node ID is unchanged or the session no longer
	 * exists.  If the session's node ID has changed, this method invokes
	 * the {@code addChangedSessionNodeId} method, and if that method
	 * returns {@code true}, the session's new node ID is added to the
	 * channel's set of node IDs.  This method may be invoked outside of a
	 * transaction. 
	 *
	 * @return {@code true} if the session's node ID is updated, otherwise
	 *	   {@code false}
	 */
	protected final boolean updateSessionNodeId() {
	    
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
			if (updated && addChangedSessionNodeId()) {
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
		// Transaction scheduler will print out warning.
		return false;
	    }
	}

	/**
	 * Returns the event queue's latest timestamp.  This method may be
	 * invoked outside of a transaction.
	 */
	protected long getEventQueueTimestamp() {
	    
	    try {
		return channelService.runTransactionalCallable(
 		  new KernelCallable<Long>("getEventQueueTimestamp") {
		      public Long call() {
			  EventQueue eventQueue = (EventQueue)
			      getObjectForId(eventQueueRefId);
			  if (eventQueue != null) {
			      return eventQueue.getNextTimestamp();
			  } else {
			      return -1L;
			  }
		    }
		});
	    } catch (Exception e) {
		return -1L;
	    }
	}
    }

    /**
     * A non-transactional task to send a join notification to a session's
     * node, allowing for the possibility of the session relocating while
     * the join message is in transit.
     */
    private static class JoinNotifyTask extends SessionNotifyTask {

	/**
	 * Constructs an instance.  This constructor must be called within a
	 * transaction.
	 */
	JoinNotifyTask(ChannelImpl channel, JoinEvent joinEvent,
		       ClientSessionImpl session, BigInteger sessionRefId)
	{
	    super(channel, joinEvent, session, sessionRefId);
	}

	/** {@inheritDoc} <p> Sends a join notification. */
	protected boolean sendNotification(ChannelServer server)
	    throws IOException
	{
	    /*
	     * Send "join" notification to session's server with a
	     * timestamp of the last message sent on the channel (which may
	     * be zero if no messages have been sent on the channel).  The
	     * timestamp of the last message sent on the channel is one
	     * less than the event's timestamp.
	     */
	    boolean success =
		server.join(name, channelRefId, (byte) delivery.ordinal(),
			    timestamp - 1, sessionRefId);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "Sent join, name:{0} channel:{1} session:{2} " +
		    "coordinator:{3} returned {4}", name,
		    channelRefId, sessionRefId,
		    getLocalNodeId(), success);
	    }
	    return success;
	}

	/** {@inheritDoc} <p>
	 *
	 * A join event requires that a changed session's node ID be added to
	 * the channel's set of server node IDs.
	 */
	protected boolean addChangedSessionNodeId() {
	    return true;
	}

	/** {@inheritDoc} */
	protected void completed() {
	    long eventQueueTimestamp = getEventQueueTimestamp();
	    if (eventQueueTimestamp > timestamp) {
		channelService.cacheMembershipEvent(
		    MembershipEventType.JOIN, channelRefId, sessionRefId,
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
	    super(eventQueue.getNextTimestamp());
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
	
	    LeaveNotifyTask task =
		new LeaveNotifyTask(channel, this, session, sessionRefId);
	    ChannelServiceImpl.getInstance().addChannelTaskOnCommit(
		channel.channelRefId, task);
	    return isCompleted();
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return "LeaveEvent[" +
		"sessionRefId:" + sessionRefId + ", " +
		toStringFieldsOnly() +
		"]";
	}
    }

    /**
     * A non-transactional task to send a join notification to a session's
     * node, allowing for the possibility of the session relocating while
     * the join message is in transit.
     */
    private static class LeaveNotifyTask extends SessionNotifyTask {

	/**
	 * Constructs an instance.  This constructor must be called within a
	 * transaction.
	 */
	LeaveNotifyTask(ChannelImpl channel, LeaveEvent leaveEvent,
			ClientSessionImpl session, BigInteger sessionRefId)
	{
	    super(channel, leaveEvent, session, sessionRefId);
	}

	/** {@inheritDoc} <p> Sends a leave notification. */
	protected boolean sendNotification(ChannelServer server)
	    throws IOException
	{
	    boolean success =
		server.leave(channelRefId, timestamp, sessionRefId);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "Sent leave, channel:{0} session:{1} " +
		    "coordinator:{2} returned {3}",
		    channelRefId, sessionRefId,
		    getLocalNodeId(), success);
	    }
	    return success;
	}
	
	/** {@inheritDoc} <p>
	 *
	 * A leave event should not have a changed session's node ID
	 * added to the channel's set of server node IDs.
	 */
	protected boolean addChangedSessionNodeId() {
	    return false;
	}

	/** {@inheritDoc} */
	protected void completed() {
	    long eventQueueTimestamp = getEventQueueTimestamp();
	    if (eventQueueTimestamp > timestamp) {
		channelService.cacheMembershipEvent(
		    MembershipEventType.LEAVE, channelRefId, sessionRefId,
		    timestamp, eventQueueTimestamp);
	    }
	    super.completed();
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
	    super(eventQueue.getNextTimestampAndIncrement());
	    this.senderRefId = senderRefId;
	    this.message = message;
	    this.isChannelMember = isChannelMember;
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(ChannelImpl channel) {
	    assert isProcessing() && !isCompleted();
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getInstance();
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
			    "to channel:{1}", senderRefId, channel);
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
				"to channel:{1}", senderRefId, channel);
			}
			return completed();
		    }
		} 
	    }

	    /*
	     * Enqueue a channel task to forward the message to the
	     * channel's servers for delivery.
	     */
	    SendNotifyTask task = new SendNotifyTask(channel, this);
	    ChannelServiceImpl.getInstance().addChannelTaskOnCommit(
		channel.channelRefId, task);

	    /*
	     * If the message is reliable, store message for a period of
	     * time so that relocating client sessions belonging to this
	     * channel can obtain messages sent while those sessions are
	     * relocating, otherwise, mark this event as completed.
	     */
	    if (channel.isReliable()) {
		channel.saveMessage(message, timestamp);
	    } else {
		completed();
	    }
	    
	    return isCompleted();
	}

	/** Use the message length as the cost for sending messages. */
	@Override
	int getCost() {
	    return message.length;
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return "SendEvent[" +
		"senderRefId:" + senderRefId +
		", message:byte[" + message.length + "], " +
		toStringFieldsOnly() +
		"]";
		
	}
    }

    /**
     * A non-transactional task to transmit a "send" notification to
     * each of the channel's server nodes so that each server node
     * can deliver a channel message to the channel's respective
     * members.  When all the appropriate channel servers have been
     * notified, this task marks the associated ChannelEvent complete
     * (within a transaction).
     */
    private static class SendNotifyTask extends NotifyTask {

	private final Set<Long> serverNodeIds;
	private final byte[] message;
	private final boolean isReliable;

	/**
	 * Constructs an instance with the specified {@code channel}
	 * and {@code sendEvent}.
	 */
	SendNotifyTask(ChannelImpl channel, SendEvent sendEvent) {
	    super(channel, sendEvent);
	    this.serverNodeIds = channel.servers;
	    this.message = sendEvent.message;
	    this.isReliable = channel.isReliable();
	}

	/** {@inheritDoc} */
	public void run() {
	    try {
		/*
		 * Send "send" notification to channel's servers.
		 */ 
		for (final long nodeId : serverNodeIds) {
		    boolean success = channelService.runIoTask(
		      new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.send(channelRefId, message, timestamp);
			    }
			} },
		      nodeId);
		    if (!success) {
			// Server node has failed, so remove it from
			// channel's server list.
			removeNodeIdFromChannel(nodeId);
		    }
		}
	    } finally {
		if (isReliable) {
		    // Only a reliable send event need to be marked
		    // completed. An unreliable send is marked completed
		    // when it is processed by the event queue (before its
		    // corresponding SendNotifyEvent is run).
		    completed();
		}
	    }
	}
    }
    
    /**
     * A channel close event, used for closing a channel or removing
     * all members from the channel (as a result of a "leaveAll"
     * request).  If a channel is being closed permanently, its name
     * binding is removed from the data service.  If the channel is
     * being cleared of its membership (as a result of "leaveAll"),
     * then its name binding is being used to refer to a
     * ChannelWrapper with a new channel instance, so the name
     * binding is retained.
     */
    private static class CloseEvent extends ChannelEvent {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** A flag to indicate whether the channel's name binding
	 * should be removed. */
	private final boolean removeName;
	
	/**
	 * Constructs a close event.  If {@code removeName} is {@code true},
	 * the channel is truly being closed, and its channel name
	 * binding will be removed.  If {@code removeName} is {@code
	 * false}, then {@code leaveAll} was invoked on the channel, so
	 * the channel's old persistent structures will be removed, but the
	 * channel's name binding will remain, still referring to the
	 * original {@code ChannelWrapper} whose underlying reference
	 * was modified to refer to a newly created channel.
	 *
	 * @param removeName {@code true} if the channel's name binding
	 *	  should be removed when the channel is closed
	 */
	CloseEvent(boolean removeName, EventQueue eventQueue) {
	    super(eventQueue.getNextTimestamp());
	    this.removeName = removeName;
	}

	/** {@inheritDoc} */
	public boolean serviceEvent(ChannelImpl channel) {
	    assert isProcessing() && !isCompleted();
	    CloseNotifyTask task =
		new CloseNotifyTask(channel, removeName);
	    ChannelServiceImpl.getInstance().addChannelTaskOnCommit(
		    channel.channelRefId, task);
	    return false;
	}

	/** {@inheritDoc} */
        @Override
	public String toString() {
	    return "CloseEvent[" +
		"removeName:" + removeName + ", " +
		toStringFieldsOnly() +
		"]";
	}
    }

    /**
     * A non-transactional task (with transactional components) to send a
     * "close" notification to each of the channel's server nodes so that
     * each server node can send the channel's respective members a
     * leave notification.  This task also removes the channel's
     * persistent data when the notifications have been delivered.  If
     * {@code removeName}, specified during construction, is {@code true}
     * the channel's name binding is removed along with the channel's
     * persistent data, otherwise the channel's name binding is not
     * removed. 
     */
    private static class CloseNotifyTask extends AbstractKernelRunnable {

	private final BigInteger channelRefId;
	private final Set<Long> serverNodeIds;
	private final boolean removeName;
	// FIXME: This is a kludge for now.
	private static final long timestamp = Long.MAX_VALUE;

	/**
	 * Constructs an instance with the specified {@code channel}.  If
	 * {@code removeName} is {@code true}, the channel's name binding
	 * is removed along with its persistent data.
	 */
	CloseNotifyTask(ChannelImpl channel, boolean removeName) {
	    super(null);
	    this.channelRefId = channel.channelRefId;
	    this.serverNodeIds = channel.servers;
	    this.removeName = removeName;
	}

	public void run() {
	    final ChannelServiceImpl channelService =
		ChannelServiceImpl.getInstance();
	    /*
	     * Send "close" notification to channel's servers.
	     */ 
	    for (final long nodeId : serverNodeIds) {
		channelService.runIoTask(
		    new IoRunnable() {
			public void run() throws IOException {
			    ChannelServer server = getChannelServer(nodeId);
			    if (server != null) {
				server.close(channelRefId, timestamp);
			    }
			} },
		    nodeId);
	    }

	    /*
	     * Notify local channel service to clean up this channel's
	     * coordinator's transient data.
	     */
	    channelService.closedChannel(channelRefId);

	    /*
	     * Remove channel's persistent data and, optionally, the
	     * channel's name binding.
	     */
	    try {
		channelService.runTransactionalTask(
		  new AbstractKernelRunnable("RemoveClosedChannel") {
		    public void run() {
			ChannelImpl channel = (ChannelImpl)
			    getObjectForId(channelRefId);
			if (channel != null) {
			    channel.removeChannel(removeName);
			} else {
			    // This shouldn't happen
			    logger.log(
				Level.SEVERE,
				"channel:{0} removed before closed",
				channelRefId);
			}
		    }
		});
	    } catch (Exception e) {
		// Transaction scheduler will print out warning.
	    }
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
		channelRefId);
	}
	return eventQueue;
    }

    /* -- Static method invoked by ChannelServiceImpl -- */

    /**
     * Handles a channel {@code message} that the specified {@code sender}
     * is sending on the channel with the specified {@code channelRefId}.
     *
     * @param	channelRefId the channel ID, as a {@code BigInteger}
     * @param	sender the client session sending the channel message
     * @param	message the channel message
     */
    static void handleChannelMessage(
	BigInteger channelRefId, ClientSession sender, ByteBuffer message)
    {
	assert sender instanceof ClientSessionWrapper;
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    channel.receivedMessage(sender, message);
	} else {
	    // Ignore message received for unknown channel.
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
 		    Level.FINE,
		    "Dropping message:{0}: from:{1} for unknown channel: {2}",
		    HexDumper.format(message), sender, channelRefId);
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

	/**
	 * The node ID of the failed node.
	 * @serial
	 */
	private final long failedNodeId;

	/**
	 * The iterator for channels on the failed node.
	 * @serial
	 */
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
	 * Gets the next locally-coordinated channel, removes the failed
	 * node from that channel, and reschedules this task to handle the
	 * next locally-coordinated channel. If there are no more
	 * locally-coordinated channels, then this task takes no action.
	 */
	public void run() {
	    if (iter == null || !iter.hasNext()) {
		return;
	    }
	    
	    BigInteger channelRefId = new BigInteger(iter.next());
	    ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	    if (channel != null) {
		channel.removeServerNodeId(failedNodeId);
	    }
	    // Schedule a task to remove failed node from next
	    // locally coordinated channel.
	    if (iter.hasNext()) {
		ChannelServiceImpl.getTaskService().scheduleTask(this);
	    }
	}
    }

    /**
     * Checks with the {@code ChannelServer} on the node with the specified
     * {@code nodeId} whether the session with the specified {@code
     * sessionRefId} is a member of the channel with the specified {@code
     * channelRefId}, and returns {@code true} if the session is a member
     * and returns {@code false} otherwise.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @param	nodeId the session's node ID
     * @param	timestamp the requesting event's timestamp
     */
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
		
	    case UNKNOWN:
		// The return value for UNKNOWN is correct only if the
		// session is obtained in a transaction while this
		// method is invoked (which would mean the session's
		// node can't change due to relocation).  Otherwise, we
		// would need to resample the session's node ID to see
		// if it changed.  If it hasn't changed, then it is
		// disconnected, otherwise, need to check membership
		// with the new session's new node.
		return false;

	    default:
		throw new AssertionError();
		
	    }
	} catch (IOException e) {
	    // The session's node can't be contacted, so the session
	    // must be disconnected.
	}
	return false;
    }
}
