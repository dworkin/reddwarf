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
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.impl.service.session.ClientSessionWrapper;
import com.sun.sgs.impl.service.session.NodeAssignment;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.ManagedQueue;
import com.sun.sgs.impl.util.ManagedSerializable;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
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

    /** The channel name component prefix. */
    private final static String NAME_COMPONENT = "name.";

    /** The channel set component prefix. */
    private final static String SET_COMPONENT = "set.";

    /** The member session component prefix. */
    private final static String SESSION_COMPONENT = "session.";

    /** The work queue component prefix. */
    private final static String QUEUE_COMPONENT = "eventq.";

    /** The random number generator for choosing a new coordinator. */
    private final static Random random = new Random();

    /** The channel name. */
    protected final String name;
    
    /** The ID from a managed reference to this instance. */
    protected final byte[] channelId;

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
    
    /** The data service. */
    private transient DataService dataService;

    /** Flag that is 'true' if this channel is closed. */
    private boolean isClosed = false;

    /**
     * The maximum number of message bytes that can be queued for delivery on
     * this channel.
     */
    private final int writeBufferCapacity;

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
	this.dataService = ChannelServiceImpl.getDataService();
	if (listener != null) {
	    if (! (listener instanceof Serializable)) {
		throw new IllegalArgumentException("non-serializable listener");
	    } else if (! (listener instanceof ManagedObject)) {
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
	this.channelId = ref.getId().toByteArray();
	this.coordNodeId = getLocalNodeId();
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl:{0}",
		       HexDumper.toHexString(channelId));
	}
	dataService.setServiceBinding(getChannelKey(), this);
	dataService.setServiceBinding(getEventQueueKey(), new EventQueue(this));
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
	try {
	    return ((ChannelImpl) ChannelServiceImpl.getDataService().
		getServiceBinding(getChannelKey(name))).getWrappedChannel();
	} catch (ObjectNotFoundException e) {
	    // TBD: This shouldn't happen, so log at SEVERE?
	    throw new NameNotBoundException("channel not found!", e);
	}
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public String getName() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", name);
	}
	return name;
    }
    
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
    public boolean hasSessions() {
	checkClosed();
	String prefix = getSessionPrefix();
	String name = dataService.nextServiceBoundName(prefix);
	return name != null && name.startsWith(prefix);
    }

    /** {@inheritDoc} */
    public Iterator<ClientSession> getSessions() {
	checkClosed();
	return new ClientSessionIterator(dataService, getSessionPrefix());
    }

    /** {@inheritDoc} */
    public Channel join(final ClientSession session) {
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
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "join throws");
	    throw e;
	}
    }

    /** {@inheritDoc}
     *
     * Enqueues a join event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    public Channel join(final Set<ClientSession> sessions) {
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
	    return this;
	    
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
	if (session instanceof ClientSessionWrapper) {
	    return ((ClientSessionWrapper) session).getClientSession();
	} else {
	    return session;
	}
    }

    /**
     * Adds the specified channel {@code event} to this channel's event queue
     * and notifies the coordinator that there is an event to service.
     */
    private void addEvent(ChannelEvent event) {

	EventQueue eventQueue = getEventQueue(coordNodeId, channelId);
	    
	if (eventQueue.offer(event)) {
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
	    ChannelServiceImpl.getTaskService().scheduleNonDurableTask(
	        new AbstractKernelRunnable() {
		    public void run() {
			try {
			    coordinator.serviceEventQueue(channelId);
			} catch (IOException e) {
			    /*
			     * It is likely that the coordinator's node failed
			     * and hasn't recovered yet.   When the
			     * coordinator recovers, it will resume
			     * servicing events, so ignore this exception.
			     */
			    if (logger.isLoggable(Level.FINEST)) {
				logger.logThrow(
				    Level.FINEST, e,
				    "serviceEventQueue channel:{0} coord:{1} " +
				    "throws", HexDumper.toHexString(channelId),
				    coord);
			    }
			}
		    }}, false);
	}
    }
    
    /** {@inheritDoc}
     *
     * Enqueues a leave event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    public Channel leave(final ClientSession session) {
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
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }

    /** {@inheritDoc}
     *
     * Enqueues leave event(s) to this channel's event queue and notifies
     * this channel's coordinator to service the event(s).
     */
    public Channel leave(final Set<ClientSession> sessions) {
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
	    return this;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "leave throws");
	    throw e;
	}
    }
    
    /** {@inheritDoc}
     *
     * Enqueues a leaveAll event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
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

    /** {@inheritDoc}
     *
     * Enqueues a send event to this channel's event queue and notifies
     * this channel's coordinator to service the event.
     */
    public Channel send(ByteBuffer message) {
	doSend(null, message);
	return this;
    }

    /**
     * If the specified {@code sender} is a member of this channel, and
     * either, this channel has a null {@code ChannelListener} or has a
     * non-null {@code ChannelListener} and invoking that listener's
     * {@code receivedMessage} method with this channel, the specified
     * {@code sender}, and {@code message} returns {@code true}, then
     * this method forwards the {@code message} to the channel for
     * delivery; otherwise, no action is taken.
     */
    private void send(ClientSession sender, ByteBuffer message) {
	// TBD: exception handling?

	if (listenerRef == null ||
	    listenerRef.get().receivedMessage(getWrappedChannel(), sender,
					      message.asReadOnlyBuffer()))
	{
	    doSend(sender, message);
	}
    }

    private void doSend(ClientSession sender, ByteBuffer message) {
	try {
	    checkClosed();
	    if (message == null) {
		throw new NullPointerException("null message");
	    }
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
	    byte[] senderIdBytes =
		sender != null ?
		getSessionIdBytes(unwrapSession(sender)) :
		null;
	    addEvent(new SendEvent(senderIdBytes, msgBytes));

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
	     Arrays.equals(((ChannelImpl) obj).channelId, channelId));
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	return Arrays.hashCode(channelId);
    }

    /** {@inheritDoc} */
    @Override
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
     * Return a key for accessing the channel with the specified
     * {@code name}.  The key has the following form:
     *
     * com.sun.sgs.impl.service.channel.name.<channelName>
     */
    private static String getChannelKey(String name) {
	return PKG_NAME + NAME_COMPONENT + name;
    }

    /**
     * Returns a key for accessing this channel.  The key has the
     * following form:
     *
     * com.sun.sgs.impl.service.channel.name.<channelName>
     */
    private String getChannelKey() {
	return getChannelKey(name);
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

    private static String getEventQueuePrefix(long nodeId) {
	return PKG_NAME + QUEUE_COMPONENT + nodeId + ".";
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
	return getEventQueuePrefix(nodeId) +
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

    /* -- Other methods -- */
    
    /**
     * Returns the ID for the specified {@code session}.
     */
    private static byte[] getSessionIdBytes(ClientSession session) {
	return ChannelServiceImpl.getDataService().
	    createReference(session).getId().toByteArray();
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
		(ChannelSet) dataService.getServiceBinding(channelSetKey);
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
     * specified {@code nodeId}, notifies the session's server that the
     * session left the channel, and returns {@code true} if the
     * session was a member of this channel when this method was
     * invoked.  If the session is not a member of this channel, then no
     * action is taken and {@code false} is returned.
     */
    private boolean removeSession(long nodeId, final byte[] sessionIdBytes) {
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
		(ChannelSet) dataService.getServiceBinding(channelSetKey);
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
	
	final ChannelServer server = getChannelServer(nodeId);
	/*
	 * If there is no channel server for the session's node,
	 * then the session's node has failed and the session is
	 * now disconnected.  There is no need to send a 'leave'
	 * notification (to update the channel membership cache) to
	 * the failed server.
	 */
	if (server != null) {
	    ChannelServiceImpl.addChannelTask(
		new BigInteger(1, channelId),
		new Runnable() {
		    public void run() {
			try {
			    server.leave(channelId, sessionIdBytes);
			} catch (IOException e) {
			    /*
			     * If the channel server can't be contacted, it
			     * has failed or is shut down, so there is no
			     * need to contact it to update its membership
			     * cache, so ignore this exception.
			     */
			    logger.logThrow(
			        Level.FINE, e,
				"unable to contact channel server:{0} to " +
				"handle event:{1}", server, this);	
			}
		    }});
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
	EventQueue eventQueue = getEventQueue(coordNodeId, channelId);
	if (eventQueue == null) {
	    logger.log(
		Level.SEVERE,
		"event queue for channel:{0} and coordinator:{1} " +
		"prematurely removed", this, coordNodeId);
	    return;
	}
	/*
	 * Remove previous coordinator's event queue binding, assign a new
	 * coordinator, and set new coordinator's event queue binding.
	 */
	dataService.removeServiceBinding(getEventQueueKey());
	coordNodeId = chooseCoordinatorNode();
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE,
		"channel:{0} reassigning coordinator from:{1} to:{2}",
		HexDumper.toHexString(channelId), failedCoordNodeId,
		coordNodeId);
	}
	dataService.setServiceBinding(getEventQueueKey(), eventQueue);
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
	if (! servers.isEmpty()) {
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
     * Removes the client session with the specified {@code
     * sessionIdBytes} that is connected to the node with the
     * specified {@code nodeId} from all channels that it is currently
     * a member of.  This method is invoked when a session is
     * disconnected from this node, gracefully or otherwise, or if
     * this node is recovering for a failed node whose sessions all
     * became disconnected.
     *
     * This method should be called within a transaction.
     */
    static void removeSessionFromAllChannels(
	long nodeId, byte[] sessionIdBytes)
    {
	Set<byte[]> channelIds = getChannelsForSession(nodeId, sessionIdBytes);
	for (byte[] channelId : channelIds) {
	    ChannelImpl channel = (ChannelImpl) getObjectForId(
		new BigInteger(1, channelId));
	    if (channel != null) {
		channel.removeSession(nodeId, sessionIdBytes);
	    } else {
		logger.log(Level.FINE, "channel already removed:{0}",
			   HexDumper.toHexString(channelId));
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
	DataService dataService = ChannelServiceImpl.getDataService();
	try {
	    return dataService.createReferenceForId(refId).get();
	} catch (ObjectNotFoundException e) {
	    return null;
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
		(ChannelSet) dataService.getServiceBinding(channelSetKey);
	} catch (NameNotBoundException e) {
	    // ignore; session may not be a member of any channel
	}

	if (channelSet != null) {
	    return channelSet.getChannelIds();
	} else {
	    Set<byte[]> emptySet = Collections.emptySet();
	    return emptySet;
	}
    }

    /**
     * Returns a set containing all the channel servers for this channel.
     */
    private Set<ChannelServer> getChannelServers() {
	Set<ChannelServer> channelServers = new HashSet<ChannelServer>();
	for (Long nodeId : servers) {
	    ChannelServer channelServer = getChannelServer(nodeId);
	    if (channelServer != null) {
		channelServers.add(channelServer);
	    }
	}
	return channelServers;
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
		(ClientSessionInfo) dataService.getServiceBinding(sessionKey);
	    removeSession(sessionInfo.nodeId, sessionInfo.sessionIdBytes);
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
	removeAllSessions();
	dataService.removeServiceBinding(getChannelKey());
	dataService.removeObject(this);
	if (listenerRef != null) {
	    ChannelListener maybeWrappedListener = listenerRef.get();
	    if (maybeWrappedListener instanceof ManagedSerializable) {
		dataService.removeObject(maybeWrappedListener);
	    }
	}
	EventQueue eventQueue = getEventQueue(coordNodeId, channelId);
	dataService.removeServiceBinding(getEventQueueKey());
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
     * Returns the {@code ClientSessionInfo} object for the specified
     * client {@code session}.
     */
    private ClientSessionInfo getClientSessionInfo(ClientSession session) {
	String sessionKey = getSessionKey(session);
	ClientSessionInfo info = null;
	try {
	    info = (ClientSessionInfo) dataService.getServiceBinding(
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
    
    /**
     * A wrapper for a {@code ChannelListener} that is serializable,
     * but not managed.
     */
    private static class ManagedSerializableChannelListener
	extends ManagedSerializable<ChannelListener>
	implements ChannelListener
    {
	private final static long serialVersionUID = 1L;
	
	/** Constructs an instance with the specified {@code listener}. */
	ManagedSerializableChannelListener(ChannelListener listener) {
	    super(listener);
	}

	/** {@inheritDoc} */
	public boolean receivedMessage(
	    Channel channel, ClientSession sender, ByteBuffer message)
	{
	    return get().receivedMessage(channel, sender, message);
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
	private final static long serialVersionUID = 1L;
	final long nodeId;
	final byte[] sessionIdBytes;
	private final ManagedReference<ClientSession> sessionRef;
	    

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
		return sessionRef.get();
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

	/** The set of channel IDs that the client session is a member of. */
	private final Set<BigInteger> set = new HashSet<BigInteger>();

	ChannelSet(DataService dataService, ClientSession session) {
	    super(dataService, session);
	}

	boolean add(ChannelImpl channel) {
	    return set.add(new BigInteger(1,channel.channelId));
	}

	boolean remove(ChannelImpl channel) {
	    return set.remove(new BigInteger(1, channel.channelId));
	}

	Set<byte[]> getChannelIds() {
	    HashSet<byte[]> ids = new HashSet<byte[]>();
	    for (BigInteger refId : set) {
		ids.add(refId.toByteArray());
	    }
	    return ids;
	}

	boolean isEmpty() {
	    return set.isEmpty();
	}
    }

    /**
     * The channel's event queue.
     */
    private static class EventQueue implements ManagedObject, Serializable {

	/** The serialVersionUID for this class. */
	private final static long serialVersionUID = 1L;

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
	    channelRef = channel.dataService.createReference(channel);
	    queueRef = channel.dataService.createReference(
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
		ChannelServiceImpl.getDataService().markForUpdate(this);
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
	    ChannelServiceImpl.getDataService().markForUpdate(this);
	    sendRefresh = true;
	}
	    
	/**
	 * Processes (at least) the first event in the queue.
	 */
	void serviceEvent() {
	    checkState();
	    ChannelImpl channel = getChannel();
	    if (! channel.isCoordinator()) {
		// TBD: should a serviceEventQueue request be forwarded to
		// the true channel coordinator?
		logger.log(
		    Level.WARNING,
		    "Attempt at node:{0} channel:{1} to service events; " +
		    "instead of current coordinator:{2}",
		    getLocalNodeId(),
		    HexDumper.toHexString(channel.channelId),
		    channel.coordNodeId);
		return;
	    }
	    ChannelServiceImpl channelService =
		ChannelServiceImpl.getChannelService();
	    DataService dataService =
		ChannelServiceImpl.getDataService();
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
		final Set<ChannelServer> channelServers =
		    channel.getChannelServers();
		final BigInteger channelRefId = getChannelRefId();
		final byte[] channelIdBytes = channel.channelId;
		final String channelName = channel.name;
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "sending refresh, channel:{0}",
			       HexDumper.toHexString(channelIdBytes));
		}
		ChannelServiceImpl.addChannelTask(
		    channelRefId,
		    new Runnable() {
			public void run() {
			    for (ChannelServer server : channelServers) {
				try {
				    server.refresh(channelName, channelIdBytes);
				} catch (IOException e) {
				    /*
				     * It is possible that the channel server's
				     * node is no longer running (because of
				     * shutdown/crash), so ignore this exception
				     * and continue.
				     */
				    logger.log(
					Level.FINE,
					"unable to contact server:{0}", server);
				}
			    }
			}
		    });
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
	private final static long serialVersionUID = 1L;

	private final byte[] sessionId;

	/**
	 * Constructs a join event with the specified {@code session}.
	 */
	JoinEvent(ClientSession session) {
	    sessionId = getSessionIdBytes(session);
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    ClientSession session = (ClientSession) getObjectForId(
		new BigInteger(1, sessionId));
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    if (! channel.addSession(session)) {
		return;
	    }
	    final long nodeId = getNodeId(session);
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
	    ChannelServiceImpl.addChannelTask(
		eventQueue.getChannelRefId(),
		new Runnable() {
		    public void run() {
		        try {
			    server.join(channel.name, channel.channelId,
					sessionId);
			} catch (IOException e) {
			    /*
			     * If the channel server can't be contacted, it
			     * has failed or is shut down, so there is no
			     * need to contact it to update its membership
			     * cache, so ignore this exception.
			     */
			    logger.logThrow(
			        Level.FINE, e,
				"unable to contact channel server:{0} to " +
				"handle event:{1}", server, this);
			}
		    }});
	}

	/** {@inheritDoc} */
        @Override
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
	public void serviceEvent(EventQueue eventQueue) {

	    ClientSession session = (ClientSession) getObjectForId(
		new BigInteger(1, sessionId));
	    if (session == null) {
		logger.log(
		    Level.FINE,
		    "unable to obtain client session for ID:{0}", this);
		return;
	    }
	    final ChannelImpl channel = eventQueue.getChannel();
	    if (! channel.removeSession(session)) {
		return;
	    }
	}

	/** {@inheritDoc} */
        @Override
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
	public void serviceEvent(EventQueue eventQueue) {

	    final ChannelImpl channel = eventQueue.getChannel();
	    channel.removeAllSessions();
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    ChannelServiceImpl.addChannelTask(
		eventQueue.getChannelRefId(),
		new Runnable() {
		    public void run() {
			for (ChannelServer server : servers) {
			    try {
				server.leaveAll(channel.channelId);
			    } catch (IOException e) {
				/*
				 * If a channel server can't be contacted,
				 * it has failed or is shut down, so there
				 * is no need to contact it to update its
				 * membership cache, so ignore this
				 * exception and continue.
				 */
				logger.logThrow(
				    Level.FINE, e,
				    "unable to contact channel server:{0} " +
				    "to handle event:{1}", server, this);
			    }
			}
		    }});
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
	private final static long serialVersionUID = 1L;

	private final byte[] message;
	/** The sender's session ID, or null. */
	private final byte[] senderId;
	
	/**
	 * Constructs a send event with the given {@code senderId} and
	 * {@code message}.
	 *
	 * @param senderId a sender's session ID, or {@code null}
	 * @param message a message
	 */
	SendEvent(byte[] senderId, byte[] message) {
	    this.senderId = senderId;
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
	    final ChannelImpl channel = eventQueue.getChannel();
	    if (senderId != null) {
		ClientSession sender =
		    (ClientSession) getObjectForId(new BigInteger(1, senderId));
		if (sender == null || ! channel.hasSession(sender)) {
		    return;
		}
	    }
	    /*
	     * Enqueue a channel task to forward the message to the
	     * channel's servers for delivery.
	     */
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    ChannelServiceImpl.addChannelTask(
		eventQueue.getChannelRefId(),
		new Runnable() {
		    public void run() {
			for (ChannelServer server : servers) {
			    try {
				server.send(channel.channelId, message);
			    } catch (IOException e) {
				/*
				 * If a channel server can't be contacted, it
				 * has failed or is shut down and the sessions
				 * connected to that node have been
				 * disconnected, so there is no need to contact
				 * it to forward the message to its local
				 * member sessions, so ignore this exception
				 * and continue.
				 */
				logger.logThrow(
				    Level.FINE, e,
				    "unable to contact channel server:{0} " +
				    "to handle event:{1}", server, this);
			    }
			    // TBD: need to update queue that all
			    // channel servers have been notified of
			    // the 'send'.
			}
		    }});
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
	private final static long serialVersionUID = 1L;

	/**
	 * Constructs a close event.
	 */
	CloseEvent() {
	}

	/** {@inheritDoc} */
	public void serviceEvent(EventQueue eventQueue) {

	    final ChannelImpl channel = eventQueue.getChannel();
	    final Set<ChannelServer> servers = channel.getChannelServers();
	    channel.removeChannel();
	    ChannelServiceImpl.addChannelTask(
		eventQueue.getChannelRefId(),
		new Runnable() {
		    public void run() {
			for (ChannelServer server : servers) {
			    try {
				server.close(channel.channelId);
			    } catch (IOException e) {
				/*
				 * If a channel server can't be contacted,
				 * it has failed or is shut down, so there
				 * is no need to contact it to update its
				 * membership cache, so ignore this
				 * exception and continue.
				 */
				logger.logThrow(
			            Level.FINE, e,
				    "unable to contact channel server:{0} " +
				    "to handle event:{1}", server, this);
			    }
			}
		    }});
	}

	/** {@inheritDoc} */
	@Override
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
		(EventQueue) dataService.getServiceBinding(eventQueueKey);
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
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    channel.send(session, message);
	} else {
	    // message received for unknown channel
	    // TBD: log at what level?
	}
    }

    /**
     * Services the event queue for the channel with the specified {@code
     * channelId}.
     */
    static void serviceEventQueue(byte[] channelId) {
	EventQueue eventQueue = getEventQueue(getLocalNodeId(), channelId);
	if (eventQueue != null) {
	    eventQueue.serviceEvent();
	}
    }
    
    /**
     * Returns the next service bound name that starts with the given
     * {@code prefix}, or {@code null} if there is none.
     */
    private static String nextServiceBoundNameWithPrefix(
	DataService dataService, String prefix)
    {
	String name = dataService.nextServiceBoundName(prefix);
	return
	    (name != null && name.startsWith(prefix)) ? name : null;
    }

    /**
     * Removes the next client session for the specified {@code nodeId}
     * from all channels that it is currently a member of and returns
     * {@code true}.  If there is no client session for the specified
     * {@code nodeId}, then {@code false} is returned.
     */
    static boolean removeNextSessionFromAllChannels(
	DataService dataService, long nodeId)
    {
	String key = nextServiceBoundNameWithPrefix(
	    dataService, getChannelSetPrefix(nodeId));
	if (key == null) {
	    return false;
	}
	ChannelSet channelSet =
	    (ChannelSet) dataService.getServiceBinding(key);
	removeSessionFromAllChannels(nodeId, channelSet.sessionIdBytes);
	return true;
    }

    /**
     * Reassigns the next coordinator for the specified {@code nodeId} to
     * another node with member sessions, or the local node if there are no
     * member sessions and returns {@code true}.  If there is no
     * coordinator for the specified {@code nodeId}, then {@code false} is
     * returned. 
     */
    static boolean reassignNextCoordinator(
	DataService dataService, long nodeId)
    {
	String key = nextServiceBoundNameWithPrefix(
	    dataService, getEventQueuePrefix(nodeId));
	if (key == null) {
	    return false;
	}
	EventQueue eventQueue =
	    (EventQueue) dataService.getServiceBinding(key);
	BigInteger channelRefId = eventQueue.getChannelRefId();
	ChannelImpl channel = (ChannelImpl) getObjectForId(channelRefId);
	if (channel != null) {
	    channel.reassignCoordinator(nodeId);
	} else {
	    // channel removed, so just remove the service binding.
	    dataService.removeServiceBinding(key);
	}
	return true;
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
			dataService, channel.getSessionNodePrefix(nodeId)))
	    {
		int index = sessionKey.lastIndexOf('.');
		sessionKey = sessionKey.substring(index + 1);
		// convert to BigInteger
		BigInteger sessionRefId = new BigInteger(sessionKey, 16);
		members.add(sessionRefId);
	    }
	}
	return members;
    }
}
