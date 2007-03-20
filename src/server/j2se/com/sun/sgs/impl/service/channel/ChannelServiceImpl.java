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
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelService implementation.
 */
public class ChannelServiceImpl
    implements ChannelManager, Service, NonDurableTransactionParticipant
{
    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();

    /** The prefix of a session key which maps to its channel membership. */
    private static final String SESSION_PREFIX = CLASSNAME + ".session.";
    
    /** The prefix of a channel key which maps to its channel state. */
    private static final String CHANNEL_PREFIX = CLASSNAME + ".channel.";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
	new ThreadLocal<Context>();
    
    /** The name of this application. */
    private final String appName;

    /** Synchronize on this object before accessing the txnProxy. */
    private final Object lock = new Object();

    /** The listener that receives incoming channel protocol messages. */
    private final ProtocolMessageListener protocolMessageListener;
    
    /** The transaction proxy, or null if configure has not been called. */    
    private TransactionProxy txnProxy;

    /** The data service. */
    private DataService dataService;

    /** The client session service. */
    private ClientSessionService sessionService;

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    NonDurableTaskScheduler nonDurableTaskScheduler;

    /** Map (with weak keys) of client sessions to queues, each containing
     * tasks to forward channel messages sent by the session (the key).
     */
    private final WeakHashMap<SgsClientSession, NonDurableTaskQueue>
	taskQueues = new WeakHashMap<SgsClientSession, NonDurableTaskQueue>();
    
    /** The sequence number for channel messages originating from the server. */
    private AtomicLong sequenceNumber = new AtomicLong(0);

    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     */
    public ChannelServiceImpl(
	Properties properties, ComponentRegistry systemRegistry)
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
		Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
		properties);
	}
	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    }
	    appName = properties.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }	
	    protocolMessageListener = new ChannelProtocolMessageListener();
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	} catch (RuntimeException e) {
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
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {

	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring ChannelServiceImpl");
	}

	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    synchronized (lock) {
		if (txnProxy != null) {
		    throw new IllegalStateException("Already configured");
		}
		txnProxy = proxy;
		dataService = registry.getComponent(DataService.class);
		sessionService =
		    registry.getComponent(ClientSessionService.class);
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, proxy.getCurrentOwner(),
			registry.getComponent(TaskService.class));
	    }

	    /*
	     * Remove all sessions from all channels since all
	     * previously stored sessions have been disconnected.
	     */
	    Context context = checkContext();
	    context.removeAllSessionsFromChannels();
	    removeChannelSets();
	    
	    sessionService.registerProtocolMessageListener(
		SimpleSgsProtocol.CHANNEL_SERVICE, protocolMessageListener);
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure ChannelServiceImpl");
	    }
	    throw e;
	}
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
	    Context context = checkContext();
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
	    Context context = checkContext();
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

    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    checkTransaction(txn);
            boolean readOnly = true;
	    if (readOnly) {
		currentContext.set(null);
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, readOnly);
	    }
	    
	    return readOnly;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    checkTransaction(txn);
	    currentContext.set(null);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "commit txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "commit txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
        if (!prepare(txn)) {
            commit(txn);
        }
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    checkTransaction(txn);
	    currentContext.set(null);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "abort txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "abort txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /* -- other methods -- */

    /**
     * Checks the specified transaction, throwing {@code
     * IllegalStateException} if the current context is {@code null}
     * or if the specified transaction is not equal to the transaction
     * in the current context.  If the specified transaction does not
     * match the current context's transaction, then sets the current
     * context to (@code null}.
     */
    private void checkTransaction(Transaction txn) {
        if (txn == null) {
            throw new NullPointerException("null transaction");
        }
        Context context = currentContext.get();
        if (context == null) {
            throw new IllegalStateException("null context");
        }
        if (!txn.equals(context.txn)) {
            currentContext.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + context.txn + ", found " + txn);
        }
    }

   /**
     * Obtains information associated with the current transaction,
     * throwing a TransactionNotActiveException exception if there is
     * no current transaction, and throwing IllegalStateException if
     * there is a problem with the state of the transaction or if this
     * service has not been configured with a transaction proxy.
     */
    private Context checkContext() {
	Transaction txn;
	synchronized (lock) {
	    if (txnProxy == null) {
		throw new IllegalStateException("Not configured");
	    }
	    txn = txnProxy.getCurrentTransaction();
	}
	if (txn == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	Context context = currentContext.get();
	if (context == null) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "join txn:{0}", txn);
	    }
	    txn.join(this);
	    context = new Context(txn);
	    currentContext.set(context);
	} else if (!txn.equals(context.txn)) {
	    currentContext.set(null);
	    throw new IllegalStateException(
		"Wrong transaction: Expected " + context.txn +
		", found " + txn);
	}
	return context;
    }

    /**
     * Returns the context associated with the current transaction in
     * this thread.
     */
    static Context getContext() {
	return currentContext.get();
    }

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
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
		    String name = buf.getString();
                    long seq = buf.getLong(); // TODO Check sequence num
		    short numRecipients = buf.getShort();
		    if (numRecipients < 0) {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.log(
			    	Level.WARNING,
				"receivedMessage: bad CHANNEL_SEND_REQUEST " +
				"(negative number of recipients) " +
				"numRecipents:{0} session:{1}",
				numRecipients, session);
			}
			return;
		    }

		    Set<byte[]> sessions = new HashSet<byte[]>();
		    if (numRecipients > 0) {
			for (int i = 0; i < numRecipients; i++) {
			    short idLength = buf.getShort();
			    byte[] sessionId = buf.getBytes(idLength);
			    sessions.add(sessionId);
			}
		    }
		    
		    ClientSessionId senderId = session.getSessionId();
		    short msgSize = buf.getShort();
		    byte[] channelMessage = buf.getBytes(msgSize);

		    NonDurableTaskQueue queue = getTaskQueue(session);
                    // Forward message to receiving clients
		    queue.addTask(
                        new ForwardingTask(
                            name, senderId, sessions, channelMessage, seq));
                    
                    // Notify listeners in the app in a transaction
		    queue.addTask(
			new NotifyTask(
			    name, senderId, channelMessage));
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
		new KernelRunnable() {
		    public void run() {
			Context context = checkContext();
			Set<Channel> channels = context.removeSession(session);
			for (Channel channel : channels) {
			    channel.leave(session);
			}
		    }},
		session.getIdentity());
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
    final class Context {

	/** The transaction. */
	final Transaction txn;

	/**
	 * Map of channel name to transient channel impl (for those
	 * channels used during this context's associated
	 * transaction).
	 */
	private final Map<String,Channel> internalTable =
	    new HashMap<String,Channel>();

	/**
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    assert txn != null;
	    this.txn = txn;
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
	    
	    ChannelState state = new ChannelState(name, listener, delivery);
	    dataService.setServiceBinding(key, state);
	    Channel channel = new ChannelImpl(this, state);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code  name}.
	 */
	private Channel getChannel(String name) {
	    assert name != null;
	    Channel channel = internalTable.get(name);
	    if (channel == null) {
		ChannelState state;
		try {
		    state =
		    	dataService.getServiceBinding(
			    getChannelKey(name), ChannelState.class);
		} catch (NameNotBoundException e) {
		    throw new NameNotBoundException(name);
		}
		
		channel = new ChannelImpl(this, state);
		internalTable.put(name, channel);
	    }
	    return channel;
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
		ChannelState state =
		    dataService.getServiceBinding(key, ChannelState.class);
		dataService.removeServiceBinding(key);
		dataService.removeObject(state);
		internalTable.remove(name);
		
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
	 * Removes all sessions from all channels.  This method is
	 * invoked when this service is configured (if a previous
	 * channel table exists) to remove all sessions (which are now
	 * disconnected) from all channels in the channel table.
	 */
	private void removeAllSessionsFromChannels() {
	    Set<String> keys =
		BoundNamesUtil.getServiceBoundNames(
 		    dataService, CHANNEL_PREFIX);

	    for (String key : keys) {
		ChannelState channelState =
		    dataService.getServiceBinding(key, ChannelState.class);
		dataService.markForUpdate(channelState);
		channelState.removeAllSessions();
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
	    for (String name : set) {
		try {
		    channels.add(getContext().getChannel(name));
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
     * invoked when this service is configured to schedule a task to
     * remove any existing channel sets since their corresponding
     * sessions are disconnected.
     */
    private void removeChannelSets() {
	Set<String> keys =
	    BoundNamesUtil.getServiceBoundNames(dataService, SESSION_PREFIX);

	for (String key : keys) {
	    logger.log(Level.FINEST, "removeChannelSets key: {0}", key);

	    nonDurableTaskScheduler.scheduleTaskOnCommit(
		new RemoveChannelSetsTask(key));
	}
    }

    /**
     * Task (transactional) for removing all channel sets from data store.
     */
    private class RemoveChannelSetsTask implements KernelRunnable {

	private final String key;

	RemoveChannelSetsTask(String key) {
	    this.key = key;
	}

	/** {@inheritDoc} */
	public void run() throws Exception {
	    try {
		logger.log(
		    Level.FINEST, "RemoveChannelSetsTask.run key: {0}", key);
		ChannelSet set =
		    dataService.getServiceBinding(key, ChannelSet.class);
		dataService.removeServiceBinding(key);
		dataService.removeObject(set);
		logger.log(
		    Level.FINEST,
		    "RemoveChannelSetsTask.run key: {0} returns", key);
	    } catch (Exception e) {
		logger.logThrow(
		    Level.FINEST, e,
		    "RemoveChannelSetsTask.run key: {0} throws", key);
		throw e;
	    }
	}
    }

    /**
     * Task (transactional) for notifying channel listeners.
     */
    private final class NotifyTask implements KernelRunnable {

	private final String name;
	private final ClientSessionId senderId;
	private final byte[] message;

        NotifyTask(String name,
		   ClientSessionId senderId,
		   byte[] message)
	{
	    this.name = name;
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
		Context context = checkContext();
		ChannelImpl channel = (ChannelImpl) context.getChannel(name);
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
     * Task (transactional) for computing the membership info needed
     * to forward a message to a channel.
     */
    private final class ForwardingTask implements KernelRunnable {

        private final String name;
        private final ClientSessionId senderId;
        private final Set<byte[]> recipientIds;
        private final byte[] message;
        private final long seq;

        ForwardingTask(String name,
                ClientSessionId senderId,
                Set<byte[]> recipientIds,
                byte[] message,
                long seq)
        {
            this.name = name;
            this.senderId = senderId;
            this.recipientIds = recipientIds;
            this.message = message;
            this.seq = seq;
        }

        /** {@inheritDoc} */
        public void run() {
            try {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "name:{0}, message:{1}",
                        name, HexDumper.format(message));
                }
                Context context = checkContext();
                ChannelImpl channel = (ChannelImpl) context.getChannel(name);
                channel.forwardMessage(senderId, recipientIds, message, seq);

            } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.logThrow(
                        Level.FINER, e,
                        "name:{0}, message:{1} throws",
                        name, HexDumper.format(message));
                }
                throw e;
            }
        }
    }
}
