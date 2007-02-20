package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
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

    /** The prefix of each per-session key for its channel membership list. */
    private static final String SESSION_PREFIX = CLASSNAME + ".session.";
    
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
     * channel messages sent by the session.
     */
    private final WeakHashMap<SgsClientSession, MessageQueue> messageQueues =
	new WeakHashMap<SgsClientSession, MessageQueue>();
    
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
	     * Create and store new channel table if one does not
	     * already exist in the data store.  If one already
	     * exists, then remove all sessions from all channels
	     * since all previously stored sessions have been
	     * disconnected.
	     */
	    try {
		dataService.getServiceBinding(
		    ChannelTable.NAME, ChannelTable.class);
		Context context = checkContext();
		context.removeAllSessionsFromChannels();
		removeChannelSets();
	    } catch (NameNotBoundException e) {
		dataService.setServiceBinding(
		    ChannelTable.NAME, new ChannelTable());
	    }
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
	    context.initialize();
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
		    
		    short msgSize = buf.getShort();
		    byte[] channelMessage = buf.getBytes(msgSize);

		    MessageQueue queue = getMessageQueue(session);
		    queue.addMessage(name, sessions, channelMessage, seq);

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
		    }});
	}
    }

    /**
     * Returns the message queue for the specified {@code session}.
     * If a queue does not already exist, one is created and returned.
     */
    private MessageQueue getMessageQueue(SgsClientSession session) {
	synchronized (messageQueues) {
	    MessageQueue queue = messageQueues.get(session);
	    if (queue == null) {
		queue = new MessageQueue(session.getSessionId());
		messageQueues.put(session, queue);
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

	/** The channel service. */
	final ChannelServiceImpl channelService;

	/** Table of all channels, obtained from the data service. */
	private ChannelTable table = null;

	/**
	 * Map of channel name to transient channel impl (for those
	 * channels used during this context's associated
	 * transaction).
	 */
	private final Map<String,Channel> internalTable =
	    new HashMap<String,Channel>();

	/**
	 * List of message queues being processed during this
	 * transaction.  These queues need to participate in the
	 * transaction commit or abort.
	 */
	private final List<MessageQueue> queuesBeingProcessed =
	    new ArrayList<MessageQueue>();

	/**
	 * Constructs a context with the specified transaction.  The
	 * {@code initialize} method must be invoked on this context
	 * before invoking any other methods.
	 */
	private Context(Transaction txn) {
	    assert txn != null;
	    this.txn = txn;
	    this.channelService = ChannelServiceImpl.this;
	}

	/**
	 * Initializes this context's channel table to the table
	 * retrieved from the data store.
	 */
	private void initialize() {
	    this.table = dataService.getServiceBinding(
		ChannelTable.NAME, ChannelTable.class);
	}

	/* -- ChannelManager methods -- */

	/**
	 * Creates a channel with the specified name, listener, and
	 * delivery requirement.
	 */
	private Channel createChannel(String name,
				      ChannelListener listener,
				      Delivery delivery)
	{
	    assert name != null;
	    checkInitialized();
	    if (table.get(name) != null) {
		throw new NameExistsException(name);
	    }

	    ChannelState channelState =
		new ChannelState(name, listener, delivery);
	    ManagedReference ref =
		dataService.createReference(channelState);
	    dataService.markForUpdate(table);
	    table.put(name, ref);
	    Channel channel = new ChannelImpl(this, channelState);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified name.
	 */
	private Channel getChannel(String name) {
	    assert name != null;
	    checkInitialized();
	    Channel channel = internalTable.get(name);
	    if (channel == null) {
		ManagedReference ref = table.get(name);
		if (ref == null) {
		    throw new NameNotBoundException(name);
		}
		ChannelState channelState = ref.get(ChannelState.class);
		channel = new ChannelImpl(this, channelState);
		internalTable.put(name, channel);
	    }
	    return channel;
	}

	/* -- other methods -- */

	private void checkInitialized() {
	    if (table == null) {
		throw new IllegalStateException("context not initialized");
	    }
	}
	
	/**
	 * Removes the channel with the specified name.  This method is
	 * called when the 'close' method is invoked on a 'ChannelImpl'.
	 */
	void removeChannel(String name) {
	    assert name != null;
	    checkInitialized();
	    if (table.get(name) != null) {
		dataService.markForUpdate(table);
		table.remove(name);
		internalTable.remove(name);
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
	    checkInitialized();
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
	    checkInitialized();
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
	    checkInitialized();
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
	    checkInitialized();
	    for (ManagedReference ref : table.getAll()) {
		ChannelState channelState = ref.get(ChannelState.class);
		dataService.markForUpdate(channelState);
		channelState.removeAllSessions();
	    }
	}

	private boolean prepare() {
	    return queuesBeingProcessed.isEmpty();
	}

	private void commit() {
	    for (MessageQueue queue : queuesBeingProcessed) {
		queue.commit();
	    }
	}

	private void abort() {
	    for (MessageQueue queue : queuesBeingProcessed) {
		queue.abort();
	    }
	}

	private void processingQueue(MessageQueue queue) {
	    queuesBeingProcessed.add(queue);
	}
	
	/**
	 * Returns a service of the given {@code type}.
	 */
	<T extends Service> T getService(Class<T> type) {
	    checkInitialized();
	    return txnProxy.getService(type);
	}

	/**
	 * Returns the next sequence number for messages originating
	 * from this service.
	 */
	long nextSequenceNumber() {
	    checkInitialized();
	    return sequenceNumber.getAndIncrement();
	}
    }

    /**
     * Returns a session key for the given {@code session}.
     */
    private static String getSessionKey(ClientSession session) {
	byte[] sessionId = session.getSessionId();
	return SESSION_PREFIX + HexDumper.toHexString(sessionId);
    }

    /**
     * Returns true if the specified {@code key} has the prefix of a
     * session key.
     */
    private static boolean isSessionKey(String key) {
	return key.regionMatches(
	    0, SESSION_PREFIX, 0, SESSION_PREFIX.length());
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
	String key = SESSION_PREFIX;
	
	for (;;) {
	    key = dataService.nextServiceBoundName(key);
	    
	    if (key == null || ! isSessionKey(key)) {
		break;
	    }
	    
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
     * Contains a queue of channel messages, in order, for a specific
     * sending client session.  This class also serves as a task to
     * process enqueued messages, by forwarding each message to the
     * appropriate channel to send.
     *
     * When the transaction associated with the executing task
     * commits, a new task is scheduled if there are more messages to
     * process.
     */
    private class MessageQueue implements KernelRunnable {

	/** The sending session's ID (for the messages enqueued). */
	private final byte[] senderId;
	
	/** List of messages to send. */
	private List<MessageInfo> messages =
	    new ArrayList<MessageInfo>();
	
	/** List of messages being processed. */
	private List<MessageInfo> processingMessages = null;
	
	/** Tracks whether a task is scheduled to process messages. */
	private boolean scheduledTask = false;

	/**
	 * Constructs an instance with the specified {@code senderId}.
	 */
	MessageQueue(byte[] senderId) {
	    this.senderId = senderId;
	}

	/**
	 * Adds the specified {@code message} to be sent to the
	 * channel {@code name} to this message queue.
	 */
	synchronized void addMessage(String name,
				     Set<byte[]> recipientIds,
				     byte[] message,
				     long seq)
	{
	    messages.add(new MessageInfo(name, recipientIds, message, seq));
	    if (! scheduledTask) {
		nonDurableTaskScheduler.scheduleTask(this);
		scheduledTask = true;
	    }
	}

	/**
	 * When transaction commits, resets the list of messages that
	 * have been processed, and if there are more messages to
	 * process, schedules a task to process those messages.
	 */
	synchronized void commit() {
	    processingMessages = null;
	    if (messages.isEmpty()) {
		scheduledTask = false;
	    } else {
		nonDurableTaskScheduler.scheduleTask(this);
	    }
	}

	/**
	 * If transaction aborts, all messages that were being
	 * processed are moved back to the head of the message queue.
	 */
	synchronized void abort() {
	    if (processingMessages == null) {
		return;
	    } else if (! messages.isEmpty()) {
		processingMessages.addAll(messages);
	    }
	    messages = processingMessages;
	    processingMessages = null;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Processes any messages that have been enqueued by
	 * forwarding each message to the appropriate channel for
	 * distribution.
	 */
	public void run() {
	    Context context = checkContext();
	    synchronized (this) {
		if (messages.isEmpty()) {
		    return;
		}
		processingMessages = messages;
		messages = new ArrayList<MessageInfo>();
	    }
	    context.processingQueue(this);
	    
	    for (MessageInfo info : processingMessages) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
		    	Level.FINEST,
			"processing name:{0}, message:{1}",
			info.name, HexDumper.format(info.message));
		}

		try {
		    ChannelImpl channel =
			(ChannelImpl) context.getChannel(info.name);
		    channel.forwardMessageAndNotifyListeners(
			senderId, info.recipientIds, info.message, info.seq);
		} catch (NameNotBoundException e) {
		    // skip channel if it no longer exists...
		    if (logger.isLoggable(Level.FINER)) {
			logger.logThrow(
                            Level.FINER, e,
			    "nonexistent channel name:{0}, message:{1} throws",
			    info.name, HexDumper.format(info.message));
		    }
		    
		} catch (RuntimeException e) {
		    if (logger.isLoggable(Level.FINER)) {
			logger.logThrow(
                            Level.FINER, e,
			    "processing name:{0}, message:{1} throws",
			    info.name, HexDumper.format(info.message));
		    }
		    throw e;
		}
            }
	}
    }

    /**
     * Contains information about a channel message to be sent.
     */
    private static class MessageInfo {

	/** Channel name. */
	final String name;
	/** Recipients. */
	Set<byte[]> recipientIds;
	/** Message content. */
	final byte[] message;
	/** Sequence number. */
	final long seq;

	MessageInfo(String name,
		    Set<byte[]> recipientIds,
		    byte[] message,
		    long seq)
        {
            this.name = name;
            this.recipientIds = recipientIds;
            this.message = message;
            this.seq = seq;
        }
    }
}
