/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.session.ClientSessionImpl;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelService implementation. <p>
 * 
 * The {@link #ChannelServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property. <p>
 */
public class ChannelServiceImpl implements ChannelManager, Service {
    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();

    /** The prefix of a session key which maps to its channel membership. */
    private static final String SESSION_PREFIX = CLASSNAME + ".session.";
    
    /** The prefix of a channel key which maps to its channel state. */
    private static final String CHANNEL_PREFIX = CLASSNAME + ".channel.";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /**
     * The transaction context map, or null if configure has not been called.
     */
    private static TransactionContextMap<Context> contextMap = null;

    /** The transaction proxy, or null if configure has not been called. */    
    private static TransactionProxy txnProxy = null;

    /** List of contexts that have been prepared (non-readonly) or commited. */
    private final List<Context> contextList = new LinkedList<Context>();

    /** The name of this application. */
    private final String appName;

    /** The listener that receives incoming channel protocol messages. */
    private final ProtocolMessageListener protocolMessageListener;
    
    /** The data service. */
    private final DataService dataService;

    /** The client session service. */
    private final ClientSessionService sessionService;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** Map (with weak keys) of client sessions to queues, each containing
     * tasks to forward channel messages sent by the session (the key).
     */
    private final WeakHashMap<SgsClientSession, NonDurableTaskQueue>
	taskQueues = new WeakHashMap<SgsClientSession, NonDurableTaskQueue>();
    
    /** The sequence number for channel messages originating from the server. */
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /** A map of channel name to cached channel state, valid as of the
     * last transaction commit. */
    private Map<CompactId, CachedChannelState> channelStateCache =
	Collections.synchronizedMap(
	    new HashMap<CompactId, CachedChannelState>());
    
    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     * @param txnProxy transaction proxy
     * @throws Exception if a problem occurs when creating the service
     */
    public ChannelServiceImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy)
	throws Exception
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
		Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
		properties);
	}
	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    } else if (txnProxy == null) {
		throw new NullPointerException("null txnProxy");
	    }
	    appName = properties.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }	
	    protocolMessageListener = new ChannelProtocolMessageListener();
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	    synchronized (ChannelServiceImpl.class) {
		if (ChannelServiceImpl.txnProxy == null) {
		    ChannelServiceImpl.txnProxy = txnProxy;
		    contextMap = new TransactionContextMap<Context>(txnProxy);
		} else {
		    assert ChannelServiceImpl.txnProxy == txnProxy;
		}
	    }
	    contextFactory = new ContextFactory(contextMap);
	    dataService = txnProxy.getService(DataService.class);
	    sessionService = txnProxy.getService(ClientSessionService.class);

	    taskScheduler.runTask(
		new TransactionRunner(
		    new AbstractKernelRunnable() {
			public void run() {
			    /*
			     * Remove all sessions from all channels since all
			     * previously stored sessions have been
			     * disconnected.
			     */
			    removeAllSessionsFromChannels();
			    removeChannelSets();
			}
		    }),
		txnProxy.getCurrentOwner(), true);
	    sessionService.registerProtocolMessageListener(
		SimpleSgsProtocol.CHANNEL_SERVICE, protocolMessageListener);

	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
	    }
	    throw e;
	}
    }
 
    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }

    /** {@inheritDoc} */
    public void ready() { 
        nonDurableTaskScheduler =
		new NonDurableTaskScheduler(
		    taskScheduler, txnProxy.getCurrentOwner(),
		    txnProxy.getService(TaskService.class));
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
        // FIXME implement this
        return false;
    }

    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(String name,
				 ChannelListener listener,
				 Delivery delivery)
    {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    if (listener != null && !(listener instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "listener is not serializable");
	    }
	    Context context = contextFactory.joinTransaction();
	    Channel channel = context.createChannel(name, listener, delivery);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "createChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel getChannel(String name) {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    Context context = contextFactory.joinTransaction();
	    Channel channel = context.getChannel(name);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "getChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "getChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /* -- Implement TransactionContextFactory -- */
       
    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionContextMap<Context> contextMap) {
	    super(contextMap);
	}
	
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    /**
     * Iterates through the context list, in order, to flush any
     * committed changes.  During iteration, this method invokes
     * {@code flush} on the {@code Context} returned by {@code next}.
     * Iteration ceases when either a context's {@code flush} method
     * returns {@code false} (indicating that the transaction
     * associated with the context has not yet committed) or when
     * there are no more contexts in the context list.
     */
    private void flushContexts() {
	synchronized (contextList) {
	    Iterator<Context> iter = contextList.iterator();
	    while (iter.hasNext()) {
		Context context = iter.next();
		if (context.flush()) {
		    iter.remove();
		} else {
		    break;
		}
	    }
	}
    }
    
    /* -- other methods -- */

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	getContextMap().checkContext(context);
    }

    /**
     * Returns the transaction context map.
     *
     * @return the transaction context map
     */
    private synchronized static TransactionContextMap<Context> getContextMap()
    {
	if (contextMap == null) {
	    throw new IllegalStateException("Service not configured");
	}
	return contextMap;
    }
    
    /* -- Implement ProtocolMessageListener -- */

    private final class ChannelProtocolMessageListener
	implements ProtocolMessageListener
    {
	/** {@inheritDoc} */
	public void receivedMessage(SgsClientSession session, byte[] message) {
	    try {
		MessageBuffer buf = new MessageBuffer(message);
	    
		buf.getByte(); // discard version
		
		/*
		 * Handle service id.
		 */
		byte serviceId = buf.getByte();

		if (serviceId != SimpleSgsProtocol.CHANNEL_SERVICE) {
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
                            Level.SEVERE,
			    "expected channel service ID, got: {0}",
			    serviceId);
		    }
		    return;
		}

		/*
		 * Handle op code.
		 */
		byte opcode = buf.getByte();

		switch (opcode) {
		    
		case SimpleSgsProtocol.CHANNEL_SEND_REQUEST:

		    handleChannelSendRequest(session, buf);
		    break;
		    
		default:
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
			    Level.SEVERE,
			    "receivedMessage session:{0} message:{1} " +
			    "unknown opcode:{2}",
			    session, HexDumper.format(message), opcode);
		    }
		    break;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"receivedMessage session:{0} message:{1} returns",
			session, HexDumper.format(message));
		}
		
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.logThrow(
			Level.SEVERE, e,
			"receivedMessage session:{0} message:{1} throws",
			session, HexDumper.format(message));
		}
	    }
	}

	/** {@inheritDoc} */
	public void disconnected(final SgsClientSession session) {
	    nonDurableTaskScheduler.scheduleTask(
		new AbstractKernelRunnable() {
		    public void run() {
			Context context = contextFactory.joinTransaction();
			Set<Channel> channels = context.removeSession(session);
			for (Channel channel : channels) {
			    channel.leave(session);
			}
		    }},
		session.getIdentity());
	}
    }

    /**
     * Handles a CHANNEL_SEND_REQUEST protocol message (in the given
     * {@code buf} and sent by the given {@code sender}), forwarding
     * the channel message (encapsulated in {@code buf}) to the
     * appropriate recipients.  When this method is invoked, the
     * specified message buffer's current position points to the
     * channel ID in the protocol message.  The operation code has
     * already been processed by the caller.
     */
    private void handleChannelSendRequest(
	SgsClientSession sender, MessageBuffer buf)
    {
	CompactId channelId = CompactId.getCompactId(buf);
	CachedChannelState cachedState = channelStateCache.get(channelId);
	if (cachedState == null) {
	    // TBD: is this the right logging level?
	    logger.log(
		Level.WARNING,
		"non-existent channel:{0}, dropping message", channelId);
	    return;
	}

	// Ensure that sender is a channel member before continuing.
	if (!cachedState.hasSession(sender)) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.log(
		    Level.WARNING,
		    "send attempt on channel:{0} by non-member session:{1}, " +
		    "dropping message", channelId, sender);
	    }
	    return;
	}
	
	long seq = buf.getLong(); // TODO Check sequence num
	short numRecipients = buf.getShort();
	if (numRecipients < 0) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.log(
		    Level.WARNING,
		    "bad CHANNEL_SEND_REQUEST " +
		    "(negative number of recipients) " +
		    "numRecipents:{0} session:{1}",
		    numRecipients, sender);
	    }
	    return;
	}

	Set<ClientSession> recipients = new HashSet<ClientSession>();
	if (numRecipients == 0) {
	    // Recipients are all member sessions
	    recipients = cachedState.sessions;
	} else {
	    // Look up recipient sessions and check for channel membership
	    for (int i = 0; i < numRecipients; i++) {
		CompactId recipientId = CompactId.getCompactId(buf);
		SgsClientSession recipient =
		    sessionService.getClientSession(recipientId.getId());
		if (recipient != null && cachedState.hasSession(recipient)) {
		    recipients.add(recipient);
		}
	    }
	}

	byte[] channelMessage = buf.getByteArray();
	byte[] protocolMessage =
	    getChannelMessage(
		channelId, ((ClientSessionImpl) sender).getCompactSessionId(),
		channelMessage, seq);
	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST,
		"name:{0}, message:{1}",
		cachedState.name, HexDumper.format(channelMessage));
	}

	ClientSessionId senderId = sender.getSessionId();
	for (ClientSession session : recipients) {
	    // Send channel protocol message, skipping the sender
	    if (! senderId.equals(session.getSessionId())) {
		((SgsClientSession) session).sendProtocolMessage(
		    protocolMessage, cachedState.delivery);
	    }
        }

	if (cachedState.hasChannelListeners) {
	    NonDurableTaskQueue queue = getTaskQueue(sender);
	    // Notify listeners in the app in a transaction
	    queue.addTask(
		new NotifyTask(cachedState.name, channelId,
			       senderId, channelMessage));
	}
    }
    
    /**
     * Returns the task queue for the specified {@code session}.
     * If a queue does not already exist, one is created and returned.
     */
    private NonDurableTaskQueue getTaskQueue(SgsClientSession session) {
	synchronized (taskQueues) {
	    NonDurableTaskQueue queue = taskQueues.get(session);
	    if (queue == null) {
		queue =
		    new NonDurableTaskQueue(
			txnProxy, nonDurableTaskScheduler,
			session.getIdentity());
		taskQueues.put(session, queue);
	    }
	    return queue;
	}
    }
    
    /**
     * Stores information relating to a specific transaction operating on
     * channels.
     *
     * <p>This context maintains an internal table that maps (for the
     * channels used in the context's associated transaction) channel name
     * to channel implementation.  To create, obtain, or remove a channel
     * within a transaction, the {@code createChannel},
     * {@code getChannel}, or {@code removeChannel} methods
     * (respectively) must be called on the context so that the proper
     * channel instances are used.
     */
    final class Context extends TransactionContext {

	/**
	 * Map of channel name to transient channel impl (for those
	 * channels used during this context's associated
	 * transaction).
	 */
	private final Map<String, ChannelImpl> internalTable =
	    new HashMap<String, ChannelImpl>();

	/**
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    super(txn);
	}

	/* -- ChannelManager methods -- */

	/**
	 * Creates a channel with the specified {@code name}, {@code
	 * listener}, and {@code delivery} requirement.  The channel's
	 * state is bound to a name composed of the channel service's
	 * class name followed by ".channel." followed by the channel
	 * name.
	 */
	private Channel createChannel(String name,
				      ChannelListener listener,
				      Delivery delivery)
	{
	    assert name != null;
	    String key = getChannelKey(name);
	    try {
		dataService.getServiceBinding(key, ChannelState.class);
		throw new NameExistsException(name);
	    } catch (NameNotBoundException e) {
	    }
	    
	    ChannelState channelState =
		new ChannelState(name, listener, delivery, dataService);
	    dataService.setServiceBinding(key, channelState);
	    ChannelImpl channel = new ChannelImpl(this, channelState);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code name}.  If the
	 * channel is already present in the internal channel table
	 * for this transaction, then the channel is returned;
	 * otherwise, this method gets the channel's state by looking
	 * up the service binding for the channel.
	 */
	private Channel getChannel(String name) {
	    assert name != null;
	    ChannelImpl channel = internalTable.get(name);
	    if (channel == null) {
		ChannelState channelState;
		try {
		    channelState =
		    	dataService.getServiceBinding(
			    getChannelKey(name), ChannelState.class);
		} catch (NameNotBoundException e) {
		    throw new NameNotBoundException(name);
		}
		channel =  new ChannelImpl(this, channelState);
		internalTable.put(name, channel);
	    } else if (channel.isClosed) {
		throw new NameNotBoundException(name);
	    }
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code name} and
	 * {@code channelId}.  If the channel is already present in the
	 * internal channel table for this transaction, then the
	 * channel is returned; otherwise, this method uses the {@code
	 * channelId} as a {@code ManagedReference} ID to the
	 * channel's state.
	 */
	private Channel getChannel(String name, CompactId channelId) {
	    assert channelId != null;
	    ChannelImpl channel = internalTable.get(name);
	    if (channel == null) {
		ChannelState channelState;
		try {
		    BigInteger refId = new BigInteger(1, channelId.getId());
		    ManagedReference stateRef =
			dataService.createReferenceForId(refId);
		    channelState = stateRef.get(ChannelState.class);
		} catch (ObjectNotFoundException e) {
		    throw new NameNotBoundException(name);
		}
		channel = new ChannelImpl(this, channelState);
		internalTable.put(name, channel);
	    } else if (channel.isClosed) {
		throw new NameNotBoundException(name);
	    }
	    return channel;
	}

	/* -- transaction participant methods -- */

	/**
	 * Throws a {@code TransactionNotActiveException} if this
	 * transaction is prepared.
	 */
	private void checkPrepared() {
	    if (isPrepared) {
		throw new TransactionNotActiveException("Already prepared");
	    }
	}
	
	/**
	 * Marks this transaction as prepared, and if there are
	 * pending changes, adds this context to the context list and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = internalTable.isEmpty();
	    if (! readOnly) {
		synchronized (contextList) {
		    contextList.add(this);
		}
	    }
            return readOnly;
        }

	/**
	 * Marks this transaction as aborted, removes the context from
	 * the context list containing pending updates, and flushes
	 * all committed contexts preceding prepared ones.
	 */
	public void abort(boolean retryable) {
	    synchronized (contextList) {
		contextList.remove(this);
	    }
	    flushContexts();
	}

	/**
	 * Marks this transaction as committed and flushes all
	 * committed contexts preceding prepared ones.
	 */
	public void commit() {
	    isCommitted = true;
	    flushContexts();
        }

	/**
	 * If the context is committed, flushes channel state updates
	 * to the channel state cache and returns true; otherwise
	 * returns false.
	 */
	private boolean flush() {
	    if (isCommitted) {
		for (ChannelImpl channel : internalTable.values()) {
		    if (channel.isClosed) {
			channelStateCache.remove(channel.state.id);
		    } else {
			channelStateCache.put(
			    channel.state.id,
			    new CachedChannelState(channel));
		    }
		}
		return true;
	    } else {
		return false;
	    }
	}

	/* -- other methods -- */

	/**
	 * Removes the channel with the specified {@code name}.  This
	 * method is called when the {@code close} method is invoked on a
	 * {@code ChannelImpl}.
	 */
	void removeChannel(String name) {
	    assert name != null;
	    try {
		String key = getChannelKey(name);
		ChannelState channelState =
		    dataService.getServiceBinding(key, ChannelState.class);
		dataService.removeServiceBinding(key);
		dataService.removeObject(channelState);
		
	    } catch (NameNotBoundException e) {
		// already removed
	    }
	}

	/**
	 * Adds {@code channel} to the set of channels for the given
	 * client {@code session}.  The {@code ChannelSet} for a
	 * session is bound to a name composed of the channel
	 * service's class name followed by ".session." followed by
	 * the hex representation of the session's identifier.
	 */
	void joinChannel(ClientSession session, Channel channel) {
	    String key = getSessionKey(session);
	    try {
		ChannelSet set =
		    dataService.getServiceBinding(key, ChannelSet.class);
		dataService.markForUpdate(set);
		set.add(channel);
	    } catch (NameNotBoundException e) {
		ChannelSet set = new ChannelSet();
		set.add(channel);
		dataService.setServiceBinding(key, set);
	    }
	}

	/**
	 * Removes {@code channel} from the set of channels for the
	 * given client {@code session}.
	 */
	void leaveChannel(ClientSession session, Channel channel) {
	    String key = getSessionKey(session);
	    try {
		ChannelSet set =
		    dataService.getServiceBinding(key, ChannelSet.class);
		dataService.markForUpdate(set);
		set.remove(channel);
	    } catch (NameNotBoundException e) {
		// ignore
	    }
	}

	/**
	 * Removes the given {@code session}'s channel set and binding
	 * from the data store, returning a set containing the
	 * channels that the session is still a member of.
	 */
	private Set<Channel> removeSession(ClientSession session) {
	    String key = getSessionKey(session);
	    try {
		ChannelSet set =
		    dataService.getServiceBinding(key, ChannelSet.class);
		Set<Channel> channels = set.removeAll();
		dataService.removeServiceBinding(key);
		dataService.removeObject(set);
		return channels;
	    } catch (NameNotBoundException e) {
		return new HashSet<Channel>();
	    }
	}

	/**
	 * Returns a service of the given {@code type}.
	 */
	<T extends Service> T getService(Class<T> type) {
	    return txnProxy.getService(type);
	}

	/**
	 * Returns the next sequence number for messages originating
	 * from this service.
	 */
	long nextSequenceNumber() {
	    return sequenceNumber.getAndIncrement();
	}
    }

    /**
     * Returns a session key for the given {@code session}.
     */
    private static String getSessionKey(ClientSession session) {
	return SESSION_PREFIX +
            HexDumper.toHexString(session.getSessionId().getBytes());
    }

    /**
     * Returns a channel key for the given channel {@code name}.
     */
    private static String getChannelKey(String name) {
	return CHANNEL_PREFIX + name;
    }

    /**
     * Contains a set of channels (names) that a session is a member of.
     */
    private static class ChannelSet implements ManagedObject, Serializable {
	private final static long serialVersionUID = 1L;

	private final HashSet<String> set = new HashSet<String>();

	ChannelSet() {
	}

	void add(Channel channel) {
	    set.add(channel.getName());
	}

	void remove(Channel channel) {
	    set.remove(channel.getName());
	}
	
	Set<Channel> removeAll() {
	    Set<Channel> channels = new HashSet<Channel>();
	    Context context = getContextMap().getContext();
	    for (String name : set) {
		try {
		    channels.add(context.getChannel(name));
		} catch (NameNotBoundException e) {
		    // ignore
		}
	    }
	    set.clear();
	    return channels;
	}
    }
    
    /**
     * Removes all channel sets from the data store.  This method is
     * invoked when this service is configured to remove any existing
     * channel sets since their corresponding sessions are
     * disconnected.
     */
    private void removeChannelSets() {
	Iterator<String> iter =
	    BoundNamesUtil.getServiceBoundNamesIterator(
		dataService, SESSION_PREFIX);

	while (iter.hasNext()) {
	    String key = iter.next();
	    ChannelSet set =
		dataService.getServiceBinding(key, ChannelSet.class);
	    dataService.removeObject(set);
	    iter.remove();
	}
    }

    /**
     * Removes all sessions from all channels.  This method is invoked
     * when this service is configured to remove all sessions (which
     * are now disconnected) from all channels in the channel table.
     */
    private void removeAllSessionsFromChannels() {
	for (String key : BoundNamesUtil.getServiceBoundNamesIterable(
 				dataService, CHANNEL_PREFIX))
	{
	    ChannelState channelState =
		dataService.getServiceBinding(key, ChannelState.class);
	    dataService.markForUpdate(channelState);
	    channelState.removeAllSessions();
	}
    }
	
    /**
     * Task (transactional) for notifying channel listeners.
     */
    private final class NotifyTask extends AbstractKernelRunnable {

	private final String name;
	private final CompactId channelId;
	private final ClientSessionId senderId;
	private final byte[] message;

        NotifyTask(String name,
		   CompactId channelId,
		   ClientSessionId senderId,
		   byte[] message)
	{
	    this.name = name;
	    this.channelId = channelId;
	    this.senderId = senderId;
	    this.message = message;
	}

        /** {@inheritDoc} */
	public void run() {
	    try {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "NotifyTask.run name:{0}, message:{1}",
                        name, HexDumper.format(message));
                }
		Context context = contextFactory.joinTransaction();
		ChannelImpl channel =
		    (ChannelImpl) context.getChannel(name, channelId);
		channel.notifyListeners(senderId, message);

	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(
			Level.FINER, e,
			"NotifyTask.run name:{0}, message:{1} throws",
			name, HexDumper.format(message));
		}
		throw e;
	    }
	}
    }
    
    /**
     * Contains cached channel state, stored in the {@code channelStateCache}
     * map when a committed context is flushed.
     */
    private static class CachedChannelState {

	private final String name;
	private final Set<ClientSession> sessions;
	private final boolean hasChannelListeners;
	private final Delivery delivery;

	CachedChannelState(ChannelImpl channelImpl) {
	    this.name = channelImpl.state.name;
	    this.sessions = channelImpl.state.getSessions();
	    this.hasChannelListeners = channelImpl.state.hasChannelListeners();
	    this.delivery = channelImpl.state.delivery;
	}

	boolean hasSession(ClientSession session) {
	    return sessions.contains(session);
	}
    }

    /**
     * Returns a MessageBuffer containing a CHANNEL_MESSAGE protocol
     * message with this channel's name, and the specified sender,
     * message, and sequence number.
     */
    static byte[] getChannelMessage(
	CompactId channelId, CompactId senderId,
	byte[] message, long sequenceNumber)
    {
        MessageBuffer buf =
            new MessageBuffer(13 + channelId.getExternalFormByteCount() +
			      senderId.getExternalFormByteCount() +
			      message.length);
        buf.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putBytes(channelId.getExternalForm()).
            putLong(sequenceNumber).
            putBytes(senderId.getExternalForm()).
	    putByteArray(message);

        return buf.getBuffer();
    }
}
