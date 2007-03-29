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
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.kernel.ComponentRegistry;
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
 * Simple ChannelService implementation.
 */
public class ChannelServiceImpl
    implements ChannelManager, Service, NonDurableTransactionParticipant
{
    /** The state of a transaction in a context. */
    private static enum State { ACTIVE, PREPARED, COMMITTED, ABORTED };
	
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
    
    /** List of contexts that have been prepared (non-readonly) or commited. */
    private final List<Context> contextList = new LinkedList<Context>();

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

    /** A map of channel name to cached channel state, valid as of the
     * last transaction commit. */
    private Map<String, CachedChannelState> channelStateCache =
	Collections.synchronizedMap(new HashMap<String, CachedChannelState>());
    
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
	    removeAllSessionsFromChannels();
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
            boolean readOnly = currentContext.get().prepare();
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
	    currentContext.get().commit();
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
	    currentContext.get().abort();
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
     * Handles a CHANNEL_SEND_REQUEST protocol message (in the given
     * {@code buf} and sent by the given {@code sender}), forwarding
     * the channel message (encapsulated in {@code buf}) to the
     * appropriate recipients.  When this method is invoked, the
     * specified message buffer's current position points to the
     * channel name of the protocol message.  The operation code has
     * already been processed by the caller.
     */
    private void handleChannelSendRequest(
	SgsClientSession sender, MessageBuffer buf)
    {
	String name = buf.getString();
	CachedChannelState cachedState = channelStateCache.get(name);
	if (cachedState == null) {
	    // TBD: is this the right logging level?
	    logger.log(
		Level.WARNING,
		"non-existent channel:{0}, dropping message", name);
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
		byte[] recipientId = buf.getByteArray();
		SgsClientSession recipient =
		    sessionService.getClientSession(recipientId);
		if (recipient != null && cachedState.hasSession(recipient)) {
		    recipients.add(recipient);
		}
	    }
	}

	ClientSessionId senderId = sender.getSessionId();
	byte[] channelMessage = buf.getByteArray();
	byte[] protocolMessage =
	    getChannelMessage(name, senderId.getBytes(), channelMessage, seq);
	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST,
		"name:{0}, message:{1}",
		name, HexDumper.format(channelMessage));
	}

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
	    queue.addTask(new NotifyTask(name, senderId, channelMessage));
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

	/** The transaction state. */
	private State txnState = State.ACTIVE;

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
	    
	    ChannelState channelState =
		new ChannelState(name, listener, delivery);
	    dataService.setServiceBinding(key, channelState);
	    ChannelImpl channel = new ChannelImpl(this, channelState);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code  name}.
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

	/* -- transaction participant methods -- */

	/**
	 * Throws a {@code TransactionNotActiveException} if this
	 * transaction is prepared.
	 */
	private void checkPrepared() {
	    if (txnState == State.PREPARED) {
		throw new TransactionNotActiveException("Already prepared");
	    }
	}
	
	/**
	 * Marks this transaction as prepared, and if there are
	 * pending changes, adds this context to the context list and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        private boolean prepare() {
	    checkPrepared();
	    txnState = State.PREPARED;
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
	private void abort() {
	    txnState = State.ABORTED;
	    synchronized (contextList) {
		contextList.remove(this);
	    }
	    flushContexts();
	}

	/**
	 * Marks this transaction as committed and flushes all
	 * committed contexts preceding prepared ones.
	 */
	private void commit() {
            if (txnState != State.PREPARED) {
                RuntimeException e = 
                    new IllegalStateException("transaction not prepared");
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.FINE, e, "Context.commit: not yet prepared txn:{0}",
			txn);
		}
                throw e;
            }

	    txnState = State.COMMITTED;
	    flushContexts();
        }

	/**
	 * If the context is committed, flushes channel state updates
	 * to the channel state cache and returns true; otherwise
	 * returns false.
	 */
	private boolean flush() {
	    if (txnState == State.COMMITTED) {
		for (Map.Entry<String, ChannelImpl> entry :
			 internalTable.entrySet())
		{
		    String name = entry.getKey();
		    ChannelImpl channel = entry.getValue();
		    if (channel.isClosed) {
			channelStateCache.remove(name);
		    } else {
			channelStateCache.put(
			    name,
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
     * Contains cached channel state, stored in the {@code channelStateCache}
     * map when a committed context is flushed.
     */
    private static class CachedChannelState {

	private final Set<ClientSession> sessions;
	private final boolean hasChannelListeners;
	private final Delivery delivery;

	CachedChannelState(ChannelImpl channelImpl) {
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
	String name, byte[] senderId, byte[] message, long sequenceNumber)
    {
        int nameLen = MessageBuffer.getSize(name);
        MessageBuffer buf =
            new MessageBuffer(15 + nameLen + senderId.length +
                    message.length);
        buf.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putString(name).
            putLong(sequenceNumber).
            putShort(senderId.length).
            putBytes(senderId).
            putShort(message.length).
            putBytes(message);

        return buf.getBuffer();
    }
}
