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
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.channel.ChannelServer.MembershipStatus;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedMap;
import com.sun.sgs.impl.util.CacheMap;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * ChannelService implementation. <p>
 * 
 * <p>The {@link #ChannelServiceImpl constructor} requires the <a
 * href="../../../impl/kernel/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property.
 *
 * <p>TBD: add summary comment about how the implementation works.
 */
public final class ChannelServiceImpl
    extends AbstractService implements ChannelManager
{
    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the version key. */
    private static final String VERSION_KEY = PKG_NAME + ".service.version";

    /** The major version. */
    private static final int MAJOR_VERSION = 2;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;

    /** The channel server map prefix. */
    private static final String CHANNEL_SERVER_MAP_PREFIX =
	PKG_NAME + ".server.";
    
    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port: {@value #DEFAULT_SERVER_PORT}. */
    private static final int DEFAULT_SERVER_PORT = 0;

    /** The property name for the maximum number of events to process in a
     * single transaction.
     */
    private static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction: {@value #DEFAULT_EVENTS_PER_TXN}. */
    private static final int DEFAULT_EVENTS_PER_TXN = 1;
    
    /** The name of the write buffer size property. */
    private static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".write.buffer.size";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE}. */
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

    /** The name of the session relocation timeout property. */
    private static final String SESSION_RELOCATION_TIMEOUT_PROPERTY =
	PKG_NAME + ".session.relocation.timeout";

    /** The default session relocation timeout:
     * {@value #DEFAULT_SESSION_RELOCATION_TIMEOUT}.
     */
    private static final int DEFAULT_SESSION_RELOCATION_TIMEOUT = 5000;

    /** The transaction context map. */
    private static TransactionContextMap<Context> contextMap = null;

    /** The factory for creating BindingKeyedCollections. */
    private static BindingKeyedCollections collectionsFactory = null;

    /** The map of node ID (string) to ChannelServer proxy. */
    private static BindingKeyedMap<ChannelServer> channelServerMap = null;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** List of contexts that have been prepared (non-readonly) or
     * committed.  The {@code contextList} is locked when contexts are
     * added (during prepare), removed (during abort or flushed during
     * commit), and when adding or removing task queues from the {@code
     * channelTaskQueues} map.
     */
    private final List<Context> contextList = new LinkedList<Context>();

    /** The client session service. */
    private final ClientSessionService sessionService;

    /** The exporter for the ChannelServer. */
    private final Exporter<ChannelServer> exporter;

    /** The ChannelServer remote interface implementation. */
    private final ChannelServerImpl serverImpl;
	
    /** The proxy for the ChannelServer. */
    private final ChannelServer serverProxy;

    /** The listener for client session status updates (relocation or
     * disconnection).
     */
    private final ClientSessionStatusListener sessionStatusListener;

    /** The ID for the local node. */
    private final long localNodeId;

    /** The cache of channel server proxies, keyed by the server's node ID. */
    private final ConcurrentHashMap<Long, ChannelServer>
	channelServerCache = new ConcurrentHashMap<Long, ChannelServer>();

    /** The cache of channel membership snapshots, keyed by channel ID.
     * The cache entry timeout is one second.
     */
    private final CacheMap<BigInteger, Set<BigInteger>>
	channelMembershipCache =
	    new CacheMap<BigInteger, Set<BigInteger>>(1000);

    /** The cache of channel event queues, keyed by channel ID. */ 
    private final ConcurrentHashMap<BigInteger, Queue<ChannelEventInfo>>
	eventQueueCache =
	    new ConcurrentHashMap<BigInteger, Queue<ChannelEventInfo>>();

    /** The local channel membership lists, keyed by channel ID. */
    private final ConcurrentHashMap<BigInteger, Set<BigInteger>>
	localChannelMembersMap =
	    new ConcurrentHashMap<BigInteger, Set<BigInteger>>();

    /** The local per-session channel sets, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, Set<BigInteger>>
	localPerSessionChannelsMap =
	    new ConcurrentHashMap<BigInteger, Set<BigInteger>>();

    /** Map of relocation information (new node IDs and completion
     * handlers) for client sessions relocating from this node, keyed by
     * the relocating session's ID. */
    private final ConcurrentHashMap<BigInteger, RelocationInfo>
	relocatingSessions =
	    new ConcurrentHashMap<BigInteger, RelocationInfo>();

    /** Map of task queues for client sessions relocating to this node,
     * keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, List<KernelRunnable>>
	relocatedSessionTaskQueues =
	    new ConcurrentHashMap<BigInteger, List<KernelRunnable>>();

    /** The map of channel coordinator task queues, keyed by channel ID.
     * A coordinator task queue orders the delivery of incoming
     * 'serviceEventQueue' requests so that a given coordinator is not
     * overwhelmed by concurrent requests to service its event queue.
     * The tasks in these queues execute within a transaction.
     */
    private final ConcurrentHashMap<BigInteger, TaskQueue>
	coordinatorTaskQueues =
	    new ConcurrentHashMap<BigInteger, TaskQueue>();

    /** The map of channel task queues, keyed by channel ID.  A channel's
     * task queue orders the execution of tasks in which the channel's
     * coordinator sends notifications (join, leave, send, etc.) to the
     * channel servers for the channel.  The tasks in these queues execute
     * outside of a transaction.  This map must be accessed while
     * synchronized on {@code contextList}. A task queue is added when the
     * first committed context having to do with the channel is flushed,
     * and is removed when the channel is closed.
     */
    private final Map<BigInteger, TaskQueue> channelTaskQueues =
	new HashMap<BigInteger, TaskQueue>();

    /** The write buffer size for new channels. */
    private final int writeBufferSize;
    
    /** The maximum number of channel events to service per transaction. */
    final int eventsPerTxn;

    /** The timeout expiration for a client session relocating to this
     * node, in milliseconds.
     */
    private final int sessionRelocationTimeout;
    
    /** Our JMX exposed statistics. */
    final ChannelServiceStats serviceStats;

    /**
     * Constructs an instance of this class with the specified {@code
     * properties}, {@code systemRegistry}, and {@code txnProxy}.
     *
     * @param	properties service properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     *
     * @throws Exception if a problem occurs when creating the service
     */
    public ChannelServiceImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	
	logger.log(
	    Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
	    properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	try {
	    synchronized (ChannelServiceImpl.class) {
		if (contextMap == null) {
		    contextMap = new TransactionContextMap<Context>(txnProxy);
		}
		if (collectionsFactory == null) {
		    collectionsFactory =
			systemRegistry.getComponent(
			    BindingKeyedCollections.class);
		}
		if (channelServerMap == null) {
		    channelServerMap =
			collectionsFactory.newMap(CHANNEL_SERVER_MAP_PREFIX);
		}
	    }
	    contextFactory = new ContextFactory(contextMap);
	    WatchdogService watchdogService =
		txnProxy.getService(WatchdogService.class);
	    sessionService = txnProxy.getService(ClientSessionService.class);
	    localNodeId = watchdogService.getLocalNodeId();

	    /*
	     * Get the properties for controlling write buffer size,
	     * channel event processing, and session relocation timeout
	     */
            writeBufferSize = wrappedProps.getIntProperty(
                WRITE_BUFFER_SIZE_PROPERTY, DEFAULT_WRITE_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);
	    eventsPerTxn = wrappedProps.getIntProperty(
		EVENTS_PER_TXN_PROPERTY, DEFAULT_EVENTS_PER_TXN,
		1, Integer.MAX_VALUE);
	    sessionRelocationTimeout = wrappedProps.getIntProperty(
		SESSION_RELOCATION_TIMEOUT_PROPERTY,
		DEFAULT_SESSION_RELOCATION_TIMEOUT,
		500, Integer.MAX_VALUE);
	    
	    /*
	     * Export the ChannelServer.
	     */
	    int serverPort = wrappedProps.getIntProperty(
		SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	    serverImpl = new ChannelServerImpl();
	    exporter = new Exporter<ChannelServer>(ChannelServer.class);
	    try {
		int port = exporter.export(serverImpl, serverPort);
		serverProxy = exporter.getProxy();
		logger.log(
		    Level.CONFIG,
		    "ChannelServer export successful. port:{0,number,#}", port);
	    } catch (Exception e) {
		try {
		    exporter.unexport();
		} catch (RuntimeException re) {
		}
		throw e;
	    }

	    /*
	     * Check service version.
	     */
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("CheckServiceVersion") {
		    public void run() {
			checkServiceVersion(
			    VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
		    } },  taskOwner);
	    
	    /*
	     * Create channel server map, keyed by node ID.  Then store
	     * channel server in the channel server map.
	     */
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("StoreChannelServerProxy") {
		    public void run() {
			getChannelServerMap().put(
			    Long.toString(localNodeId), serverProxy);
		    } },
		taskOwner);

	    /*
	     * Add listeners for handling recovery and for receiving
	     * notification of client session relocation and disconnection.
	     */
	    watchdogService.addRecoveryListener(
		new ChannelServiceRecoveryListener());

	    watchdogService.addNodeListener(new ChannelServiceNodeListener());

	    sessionStatusListener = new SessionStatusListener();
            sessionService.addSessionStatusListener(sessionStatusListener);

            /* Create our service profiling info and register our MBean. */
            ProfileCollector collector = 
		systemRegistry.getComponent(ProfileCollector.class);
            serviceStats = new ChannelServiceStats(collector);
            try {
                collector.registerMBean(serviceStats, 
                                        ChannelServiceStats.MXBEAN_NAME);
            } catch (JMException e) {
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }

	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
	    }
	    doShutdown();
	    throw e;
	}
    }
 
    /* -- Implement AbstractService methods -- */

    /** {@inheritDoc} */
    protected void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion)
    {
	throw new IllegalStateException(
	    "unable to convert version:" + oldVersion +
	    " to current version:" + currentVersion);
    }
    
     /** {@inheritDoc} */
    protected void doReady() {
    }

    /** {@inheritDoc} */
    protected void doShutdown() {
	logger.log(Level.FINEST, "shutdown");
	
	try {
	    if (exporter != null) {
		exporter.unexport();
	    }
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "unexport server throws");
	    // swallow exception
	}
    }
    
    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(String name,
				 ChannelListener listener,
				 Delivery delivery)
    {
        serviceStats.createChannelOp.report();
	try {
	    Channel channel = ChannelImpl.newInstance(
		name, listener, delivery, writeBufferSize);
	    return channel;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "createChannel:{0} throws");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel getChannel(String name) {
        serviceStats.getChannelOp.report();
	try {
	    return ChannelImpl.getInstance(name);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "getChannel:{0} throws");
	    throw e;
	}
    }

    /* -- Public methods -- */

    /**
     * Handles a channel {@code message} that the specified {@code session}
     * is sending on the channel with the specified {@code channelRefId}.
     * This method is invoked from the {@code ClientSessionHandler} of the
     * given session, when it receives a channel
     * message.  This method must be called from within a transaction. <p>
     *
     * @param	channelRefId the channel ID, as a {@code BigInteger}
     * @param	session the client session sending the channel message
     * @param	message the channel message
     */
    public void handleChannelMessage(
	BigInteger channelRefId, ClientSession session, ByteBuffer message)
    {
	ChannelImpl.handleChannelMessage(channelRefId, session, message);
    }

    /* -- Implement ChannelServer -- */

    private final class ChannelServerImpl implements ChannelServer {

	/** {@inheritDoc}
	 *
	 * Add a task to service the specified channel's event queue.
	 */
	public void serviceEventQueue(BigInteger channelRefId) {
	    callStarted();
	    try {
		addServiceEventQueueTask(channelRefId);
					  
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc} */
	public MembershipStatus isMember(
	    BigInteger channelRefId, BigInteger sessionRefId)
	{
	    if (!sessionService.isConnected(sessionRefId)) {
		return MembershipStatus.UNKNOWN;
	    } else {
		return
		    isLocalChannelMember(channelRefId, sessionRefId) ?
		    MembershipStatus.MEMBER :
		    MembershipStatus.NON_MEMBER;
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * If the session is locally-connected and not relocating, this
	 * method adds the specified {@code sessionRefId} to the
	 * per-channel local membership set for the given channel, and
	 * sends a channel join message to the session with the
	 * corresponding {@code sessionId}.
	 */
	public boolean join(String name, BigInteger channelRefId,
			    Delivery delivery, BigInteger sessionRefId)

	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "join name:{0} channelId:{1} " +
			"sessionId:{2} localNodeId:{3}",
			name,
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.toHexString(sessionRefId.toByteArray()),
			localNodeId);
		}

		return handleChannelRequest(
		    sessionRefId,
		    new ChannelJoinTask(
			name, channelRefId, sessionRefId, delivery));
		
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified {@code sessionRefId} from the per-channel
	 * local membership set for the given channel, and sends a channel
	 * leave message to the session with the corresponding {@code
	 * sessionRefId}.
	 */
	public boolean leave(BigInteger channelRefId, BigInteger sessionRefId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "leave channelId:{0} sessionId:{1}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.toHexString(sessionRefId.toByteArray()));
		}
		
		return handleChannelRequest(
		    sessionRefId,
		    new ChannelLeaveTask(channelRefId, sessionRefId));
		
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc} */
	public BigInteger[] getSessions(BigInteger channelRefId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"getSessions channelId:{0} localNodeId:{1}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			localNodeId);
		}
		
		Set<BigInteger> localMembers =
		    localChannelMembersMap.get(channelRefId);
		
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"getSessions channelId:{0} localNodeId:{1} returns:{2}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			localNodeId,
			localMembers);
		}
		
		return
		    localMembers != null ?
		    localMembers.toArray(new BigInteger[localMembers.size()]) :
		    new BigInteger[0];
		
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Sends the given {@code message} to all local members of the
	 * specified channel.
	 *
	 * TBD: (optimization) this method should handle sending multiple
	 * messages to a given channel.
	 */
	public void send(BigInteger channelRefId, byte[] message,
			 byte deliveryOrdinal)
	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"send channelId:{0} message:{1} localNodeId:{2}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.format(message, 0x50),
			localNodeId);
		}
		/*
		 * TBD: (optimization) this should enqueue the send
		 * request and return immediately so that the
		 * coordinator can receive the acknowledgment and
		 * continue processing of the event queue.  Right now,
		 * process the send request inline here.
		 */
		Set<BigInteger> localMembers =
		    localChannelMembersMap.get(channelRefId);
		if (localMembers == null) {
		    // TBD: there should be local channel members.
		    // What error should be reported here?
		    return;
		}

		Delivery delivery = Delivery.values()[deliveryOrdinal];
		for (BigInteger sessionRefId : localMembers) {
		    SessionProtocol protocol =
			sessionService.getSessionProtocol(sessionRefId);
		    if (protocol != null) {
                        try {
                            protocol.channelMessage(channelRefId,
                                                    ByteBuffer.wrap(message),
                                                    delivery);
                        } catch (IOException e) {
                            logger.logThrow(Level.WARNING, e,
                                            "channelMessage throws");
                        } catch (RuntimeException e) {
			    // TBD
                            logger.logThrow(Level.WARNING, e,
                                            "channelMessage throws");
                        }
		    }
		}

	    } finally {
		callFinished();
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void relocateChannelMemberships(
	    final BigInteger sessionRefId, long oldNodeId,
	    BigInteger[] channelRefIds)
	{
	    /*
	     * Update the local per-session channel set and local channel
	     * membership with the session's channel memberships.
	     */
	    Set<BigInteger> channelSet =
		Collections.synchronizedSet(new HashSet<BigInteger>());
	    localPerSessionChannelsMap.put(sessionRefId, channelSet);
	    for (BigInteger channelRefId : channelRefIds) {
		channelSet.add(channelRefId);
		addLocalChannelMember(channelRefId, sessionRefId);
	    }

	    /*
	     * Schedule task to add the session's new node (the local
	     * node) to each of its channels.
	     */
	    taskScheduler.scheduleTask(
		new AddRelocatingSessionNodeToChannels(
		    sessionRefId, oldNodeId, channelSet),
		taskOwner);

	    /*
	     * Schedule a task to check whether the session has relocated
	     * to the local node (within the timeout period), and if not,
	     * it assumes that the session is disconnected and invokes the
	     * 'disconnected' method of this channel service's
	     * ClientSessionStatusListener so that the session's channel
	     * memberships (persistent and transient) can be cleaned up.
	     */
	    taskScheduler.scheduleTask(
 		new AbstractKernelRunnable("CheckSessionRelocation") {
		    public void run() {
			SessionProtocol protocol =
			    sessionService.getSessionProtocol(sessionRefId);
			if (protocol == null) {
			    sessionStatusListener.disconnected(sessionRefId);
			}
		    } },
		taskOwner,
		System.currentTimeMillis() + sessionRelocationTimeout);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void channelMembershipsUpdated(
	    BigInteger sessionRefId, long newNodeId)
	{
	    // Remove session's channel membership set.
	    // TBD: should this always return a non-null value?
	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.remove(sessionRefId);
	    
	    // Remove session from local channel membership lists
	    if (channelSet != null) {
		synchronized (channelSet) {
		    for (BigInteger channelRefId : channelSet) {
			Set<BigInteger> localMembers =
			    localChannelMembersMap.get(channelRefId);
			if (localMembers != null) {
			    localMembers.remove(sessionRefId);
			}
		    }
		}
	    }
	    
	    // Notify completion handler that relocation preparation is done.
	    RelocationInfo info =
		relocatingSessions.get(sessionRefId);
	    if (info != null) {
		info.handler.completed();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified channel from the per-channel set of
	 * local members.
	 */
	public void close(BigInteger channelRefId) {
	    callStarted();
	    try {
		localChannelMembersMap.remove(channelRefId);

	    } finally {
		callFinished();
	    }
	}

	private boolean handleChannelRequest(
	    BigInteger sessionRefId, KernelRunnable task)
	{
	    RelocationInfo info = relocatingSessions.get(sessionRefId);
	    if (info != null) {
		// Session is relocating from this node, so return the new
		// node's ID, so that the request can be sent to the new
		// node.
		return false;
	    }

	    // TBD: The following may need to check the data store to
	    // see which node the session is assigned to.
	    boolean relocatingToLocalNode =
		sessionService.isRelocatingToLocalNode(sessionRefId);
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);

	    if (protocol == null) {
		if (!relocatingToLocalNode) {
		    // The session is not locally-connected and is not
		    // known to be relocating to the local node, so return
		    // -1 (unknown status).
		    return false;
		}
		    
		// The session is relocating to this node, but the
		// session hasn't been established yet, so enqueue the
		// request until relocation is complete.
		List<KernelRunnable> taskQueue =
		    relocatedSessionTaskQueues.get(sessionRefId);
		if (taskQueue == null) {
		    List<KernelRunnable> newTaskQueue =
			Collections.synchronizedList(
			    new LinkedList<KernelRunnable>());
		    taskQueue = relocatedSessionTaskQueues.
			putIfAbsent(sessionRefId, newTaskQueue);
		    if (taskQueue == null) {
			taskQueue = newTaskQueue;
			// TBD: schedule task to clean up task queue if
			// session doesn't relocate to this node in a
			// timely manner.
		    }
		}
		    
		taskQueue.add(task);
		// TBD: there still may be a race condition in here...
		protocol = sessionService.getSessionProtocol(sessionRefId);
		if (protocol != null) {
		    // Client relocated while adding a task to the queue.
		    // The task could have been added to the queue after
		    // the 'relocated' notification, so it wouldn't have
		    // been processed.  Therefore notify the channel
		    // service that the session has been relocated so that
		    // it can process the task queue.
		    sessionStatusListener.relocated(sessionRefId);
		}
		
		return true;
		
	    } else {
		// The session is locally-connected, so process the
		// request locally.
		List<KernelRunnable> taskQueue =
		    relocatedSessionTaskQueues.get(sessionRefId);
		if (taskQueue != null) {
		    synchronized (taskQueue) {
			// If this session is relocating to this node,
			// wait for all channel requests enqueued during
			// relocation to be processed.
			if (!taskQueue.isEmpty()) {
			    try {
				// TBD: bounded timeout?
				taskQueue.wait();
			    } catch (InterruptedException e) {
			    }
			}
		    }
		}
		try {
		    task.run();
		    return true;
		    
		} catch (Exception e) {
		    // Session is relocating to another node, but its
		    // destination unknown.
		    // TBD: log exception?
		    return false;
		}
	    }
	}
    }

    /**
     * Adds a task to service the event queue for the channel with the
     * specified {@code channelRefId}.<p>
     *
     * The service event queue request is enqueued in the given
     * channel's coordinator task queue so that the requests can be
     * performed serially, rather than concurrently.  If tasks to
     * service a given channel's event queue were processed
     * concurrently, there would be many transaction conflicts because
     * servicing a channel event accesses a single per-channel data
     * structure (the channel's event queue).
     *
     * @param	channelRefId a channel ID
     */
    void addServiceEventQueueTask(final BigInteger channelRefId) {
	checkNonTransactionalContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST,
		"add task to service event queue, channelId:{0}",
		HexDumper.toHexString(channelRefId.toByteArray()));
	}

	TaskQueue taskQueue = coordinatorTaskQueues.get(channelRefId);
	if (taskQueue == null) {
	    TaskQueue newTaskQueue =
		transactionScheduler.createTaskQueue();
	    taskQueue = coordinatorTaskQueues.
		putIfAbsent(channelRefId, newTaskQueue);
	    if (taskQueue == null) {
		taskQueue = newTaskQueue;
	    }
	}

	taskQueue.addTask(
	    new AbstractKernelRunnable("ServiceEventQueue") {
		public void run() {
		    ChannelImpl.serviceEventQueue(channelRefId);
		} }, taskOwner);
    }

    /**
     * Updates the local channel members set by adding the specified
     * {@code sessionRefId} as a local member of the channel with the
     * specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     */
    private void addLocalChannelMember(BigInteger channelRefId,
				       BigInteger sessionRefId)
    {
	Set<BigInteger> localMembers =
	    localChannelMembersMap.get(channelRefId);
	if (localMembers == null) {
	    Set<BigInteger> newLocalMembers =
		Collections.synchronizedSet(new HashSet<BigInteger>());
	    localMembers = localChannelMembersMap.
		putIfAbsent(channelRefId, newLocalMembers);
	    if (localMembers == null) {
		localMembers = newLocalMembers;
	    }
	}
	localMembers.add(sessionRefId);
    }

    /**
     * Returns {@code true} if the session with the specified
     * {@code sessionRefId} is a local member of the channel with
     * the specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @return	{@code true} if the session with the specified
     *		{@code sessionRefId} is a local member of the
     *		channel with the specified {@code channelRefId}
     */
    boolean isLocalChannelMember(BigInteger channelRefId,
				 BigInteger sessionRefId)
    {
	Set<BigInteger> localMembers =
	    localChannelMembersMap.get(channelRefId);
	return localMembers != null && localMembers.contains(sessionRefId);
    }

    /**
     * Caches the channel event with the specified {@code eventType}, {@code
     * channelRefId}, {@code sessionRefId}, and {@code eventTimestamp}.  The
     * event will remain cached until its corresponding event queue reaches
     * the specified {@code expirationTimestamp}. <p>
     *
     * Note: this method removes any expired events (those with an {@code
     * expirationTimestamp} less than the specified {@code eventTimestamp}
     * from the specified channel's queue of cached events.
     *
     * @param	eventType an event type
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID, or {@code null}
     * @param	eventTimestamp the event's timestamp
     * @param	expirationTimestamp the event queue's timestamp
     */
    void cacheEvent(ChannelEventType eventType,
		    BigInteger channelRefId,
		    BigInteger sessionRefId,
		    long eventTimestamp,
		    long expirationTimestamp)
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "CACHE eventType:{0}, channelRefId:{1}, " +
		"sessionRefId:{2}, eventTimestamp:{3}, expirationTimestamp:{4}",
		eventType, HexDumper.toHexString(channelRefId.toByteArray()),
		HexDumper.toHexString(sessionRefId.toByteArray()),
		eventTimestamp, expirationTimestamp);
	}
	Queue<ChannelEventInfo> queue = eventQueueCache.get(channelRefId);
	if (queue == null) {
	    Queue<ChannelEventInfo> newQueue =
	       new LinkedList<ChannelEventInfo>();
	    queue = eventQueueCache.putIfAbsent(channelRefId, newQueue);
	    if (queue == null) {
		queue = newQueue;
	    }
	}
	synchronized (queue) {
	    // remove events with expirationTimestamp <= eventTimestamp.
	    removeExpiredChannelEvents(queue, eventTimestamp);
	    // cache event.
	    queue.offer(
		new ChannelEventInfo(eventType, sessionRefId,
				     eventTimestamp,
				     expirationTimestamp));
	}
    }
    
    /**
     * Returns {@code true} if the session with the specified {@code
     * sessionRefId} is a member of the channel with the specified {@code
     * channelRefId}, and {@code false} otherwise. Membership is determined
     * as follows:<p>
     *
     * The {@code isChannelMember} argument indicates whether the specified
     * session was a member of the channel at the time the event being
     * processed (that is now checking membership) was added to the event
     * queue.
     *
     * In order to determine channel membership, this method considers the
     * initial known membership status, {@code isChannelMember}, and then
     * checks the specified channel's queue of cached events for join/leave
     * requests for the specified session with timestamps less than or equal
     * to the specified timestamp. <p>
     *
     * Note: this method removes any expired events (those with an {@code
     * expirationTimestamp} less than the specified {@code eventTimestamp}
     * from the specified channel's queue of cached events.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @param	isChannelMember if {@code true}, the specified session is
     *		considered to be a member when current event was added to
     *		the event queue
     * @param	timestamp the timestamp of the currently executing event,
     *		beyond which join/leave requests should not be considered
     *		in determining channel membership
     * @return	{@code true} if the session with the specified {@code
     *		sessionRefId} is a member of the channel with the specified
     *		{@code channelRefId}, and {@code false} otherwise
     */
    boolean isChannelMember(BigInteger channelRefId,
			    BigInteger sessionRefId,
			    boolean isChannelMember,
			    long timestamp)
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "channel:{0}, session:{1}, isChannelMember:{2}, " +
		       "timestamp:{3}",
		       HexDumper.toHexString(channelRefId.toByteArray()),
		       HexDumper.toHexString(sessionRefId.toByteArray()),
		       isChannelMember, timestamp);
	}
	Queue<ChannelEventInfo> queue = eventQueueCache.get(channelRefId);
	if (queue != null) {
	    synchronized (queue) {
		// remove events with timestamp <= eventTimestamp.
		removeExpiredChannelEvents(queue, timestamp);
		for (ChannelEventInfo info : queue) {
		    if (info.eventTimestamp > timestamp) {
			break;
		    }
		    
		    switch (info.eventType) {
			    
			case JOIN:
			    if (logger.isLoggable(Level.FINEST)) {
				logger.log(
				    Level.FINEST, "join:{0}",
				    HexDumper.toHexString(
					info.sessionRefId.toByteArray()));
			    }
			    isChannelMember =
				isChannelMember ||
				(info.sessionRefId.equals(sessionRefId));
			    break;
			    
			case LEAVE:
			    if (logger.isLoggable(Level.FINEST)) {
				logger.log(
				    Level.FINEST, "leave:{0}",
				    HexDumper.toHexString(
					info.sessionRefId.toByteArray()));
			    }
			    isChannelMember =
				isChannelMember &&
				(!info.sessionRefId.equals(sessionRefId));

			default:
			    break;
		    }
		}
	    }
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "isChannelMember returns: {0}",
		       isChannelMember);
	}
	
	return isChannelMember;
    }

    /**
     * Removes from the specified channel event {@code queue}, each cached
     * channel event with an {@code expirationTimestamp} less than or equal
     * to the specified {@code timestamp}.
     */
    private void removeExpiredChannelEvents(
	Queue<ChannelEventInfo> queue, long timestamp)
    {
	assert Thread.holdsLock(queue);
	
	while (!queue.isEmpty()) {
	    ChannelEventInfo info = queue.peek();
	    if (info.expirationTimestamp > timestamp) {
		return;
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "REMOVE eventType:{0}, sessionRefId:{1}, " +
		    "eventTimestamp:{2}, expirationTimestamp:{3}",
		    info.eventType,
		    (info.sessionRefId != null ?
		     HexDumper.toHexString(info.sessionRefId.toByteArray()) :
		     "null"), 
		    info.eventTimestamp, info.expirationTimestamp);
	    }
	    queue.poll();
	}
    }
    
    /**
     * Collects a snapshot of the channel membership for the channel with
     * the specified {@code channelRefId} and set of member {@code
     * nodeIds} and returns an unmodifiable set containing the channel
     * membership. 
     *
     * @return	an unmodifiable set containing the channel membership
     */
    Set<BigInteger> collectChannelMembership(
	Transaction txn, BigInteger channelRefId, Set<Long> nodeIds)
    {
	if (nodeIds.size() == 1 && nodeIds.contains(getLocalNodeId())) {
	    Set<BigInteger> localMembers =
		localChannelMembersMap.get(channelRefId);
	    
	    if (localMembers != null) {
		synchronized (localMembers) {
		    return Collections.unmodifiableSet(localMembers);
		}
	    } else {
		return ChannelImpl.EMPTY_CHANNEL_MEMBERSHIP;
	    }

	} else {
	    synchronized (channelMembershipCache) {
		Set<BigInteger> members =
		    channelMembershipCache.get(channelRefId);
		if (members != null) {
		    return members;
		}
	    }

	    long stopTime = txn.getCreationTime() + txn.getTimeout();
	    long timeLeft = stopTime - System.currentTimeMillis();
	    CollectChannelMembershipTask task =
		new CollectChannelMembershipTask(channelRefId, nodeIds);
	    taskScheduler.scheduleTask(task, taskOwner);
	    synchronized (task) {
		if (!task.completed && timeLeft > 0) {
		    try {
			task.wait(timeLeft);
		    } catch (InterruptedException e) {
		    }
		}
		if (task.completed) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST, "channelId:{0} nodeIds:{1} " +
			    "members:{2}",
			    HexDumper.toHexString(channelRefId.toByteArray()),
			    nodeIds, task.getMembers());
		    }
		    return task.getMembers();
		} else {
		    throw new TransactionTimeoutException(
			"transaction timeout: " + txn.getTimeout());
		}

	    }
	}
    }

    /* -- Implement TransactionContextFactory -- */
       
    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionContextMap<Context> contextMap) {
	    super(contextMap, CLASSNAME);
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

    /**
     * Returns the currently active transaction, or throws {@code
     * TransactionNotActiveException} if no transaction is active.
     */
    static Transaction getTransaction() {
	return txnProxy.getCurrentTransaction();
    }

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkTransaction(Transaction txn) {
	Transaction currentTxn = txnProxy.getCurrentTransaction();
	if (currentTxn != txn) {
	    throw new TransactionNotActiveException(
 		"mismatched transaction; expected " + currentTxn + ", got " +
		txn);
	}
    }

    /**
     * Adds the specified {@code ioTask} (in a wrapper that runs the task by
     * invoking {@link AbstractService#runIoTask runIoTask} with the {@code
     * ioTask} and {@code nodeId}) to the task list of the given {@code
     * channelRefId}.
     */
    void addChannelTask(
	BigInteger channelRefId, final IoRunnable ioTask, final long nodeId)
    {
	addChannelTask(
	    channelRefId,
	    new AbstractKernelRunnable("RunIoTask") {
		public void run() {
		    runIoTask(ioTask, nodeId);
		} });
    }

    /**
     * Adds the specified non-transactional {@code task} to the task list
     * of the given {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	task a non-transactional task
     */
    void addChannelTask(BigInteger channelRefId, KernelRunnable task) {
	Context context = contextFactory.joinTransaction();
	context.addTask(channelRefId, task);
    }

    /**
     * Adds the specified {@code channelRefId} to the list of
     * locally-coordinated channels that need servicing.
     *
     * @param	channelRefId a channel ID for a locally-coordinated channel
     */
    void addChannelToService(BigInteger channelRefId) {
	Context context = contextFactory.joinTransaction();
	context.addChannelToService(channelRefId);
    }

    /* -- Implement TransactionContext -- */

    /**
     * This transaction context maintains a per-channel list of
     * non-transactional tasks to perform when the transaction commits. A
     * task is added to the context by a {@code ChannelImpl} via the {@code
     * addChannelTask} method.  Such non-transactional tasks include
     * sending a notification to a channel server to modify the channel
     * membership list, or forwarding a send request to a set of channel
     * servers.
     */
    final class Context extends TransactionContext {

	private final Map<BigInteger, List<KernelRunnable>> internalTaskLists =
	    new HashMap<BigInteger, List<KernelRunnable>>();

	/**
	 * Locally-coordinated channels that need servicing as a result of
	 * operations during this context's associated transaction.  When
	 * this context is flushed, a task to service the event queue will
	 * be added to the channel coordinator's task queue.
	 */
	private final Set<BigInteger> channelsToService =
	    new HashSet<BigInteger>();

	/**
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    super(txn);
	}

	/**
	 * Adds the specified non-transactional {@code task} to the task
	 * list of the given {@code channelRefId}.  If the transaction
	 * commits, the task will be added to the channel's tasks queue.
	 */
	public void addTask(BigInteger channelRefId, KernelRunnable task) {
	    List<KernelRunnable> taskList = internalTaskLists.get(channelRefId);
	    if (taskList == null) {
		taskList = new LinkedList<KernelRunnable>();
		internalTaskLists.put(channelRefId, taskList);
	    }
	    taskList.add(task);
	}

	/**
	 * Adds the specified {@code channelRefId} to the set of
	 * locally-coordinated channels whose event queues need to be
	 * serviced as a result of operations executed during this
	 * context's associated transaction. <p>
	 *
	 * When this context is flushed, for each channel that needs to be
	 * serviced, a task to service the channel's event queue will be
	 * added to that channel coordinator's task queue.
	 *
	 * @param channelRefId a locally-coordinated channel that needs to
	 *	  be serviced
	 */
	public void addChannelToService(BigInteger channelRefId) {
	    channelsToService.add(channelRefId);
	}

	/* -- transaction participant methods -- */

	/**
	 * Marks this transaction as prepared, and if there are
	 * pending changes, adds this context to the context list and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = internalTaskLists.isEmpty();
	    if (!readOnly) {
		synchronized (contextList) {
		    contextList.add(this);
		}
	    } else {
		isCommitted = true;
	    }
            return readOnly;
        }

	/**
	 * Removes the context from the context list containing pending
	 * updates, and flushes all committed contexts preceding prepared
	 * ones.
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
	 * If the context is committed, flushes channel tasks (enqueued
	 * during this transaction) to the task handler's map, notifies
	 * the task handler that there are tasks to process, and
	 * returns true; otherwise returns false.
	 */
	private boolean flush() {
	    assert Thread.holdsLock(contextList);
	    if (isCommitted) {
		for (BigInteger channelRefId : internalTaskLists.keySet()) {
		    flushTasks(
			channelRefId, internalTaskLists.get(channelRefId));
		}
		for (BigInteger channelRefId : channelsToService) {
		    addServiceEventQueueTask(channelRefId);
		}
	    }
	    return isCommitted;
	}
    }

    /**
     * Adds the tasks in the specified {@code taskList} to the specified
     * channel's task queue. This method is invoked when a context is
     * flushed during transaction commit.
     */
    private void flushTasks(
	BigInteger channelRefId, List<KernelRunnable> taskList)
	
    {
        assert Thread.holdsLock(contextList);
	TaskQueue taskQueue = channelTaskQueues.get(channelRefId);
	if (taskQueue == null) {
	    taskQueue = taskScheduler.createTaskQueue();
	    channelTaskQueues.put(channelRefId, taskQueue);
	}
	for (KernelRunnable task : taskList) {
	    taskQueue.addTask(task, taskOwner);
	}
    }

    /**
     * Notifies this service that the channel with the specified {@code
     * channelRefId} is closed so that this service can clean up any
     * per-channel data structures (relating to the channel coordinator).
     */
    void closedChannel(BigInteger channelRefId) {
	coordinatorTaskQueues.remove(channelRefId);
	eventQueueCache.remove(channelRefId);
	synchronized (contextList) {
	    channelTaskQueues.remove(channelRefId);
	}
    }
    /* -- Implement ClientSessionStatusListener -- */

    private final class SessionStatusListener
	implements ClientSessionStatusListener
    {
        /**
         * {@inheritDoc}
	 */
	public void disconnected(final BigInteger sessionRefId) {

	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.remove(sessionRefId);		

	    /*
	     * Remove the disconnected session from each channel that it is
	     * currently a member of.
	     */
	    if (channelSet != null) {
		synchronized (channelSet) {
		    for (final BigInteger channelRefId : channelSet) {
			// Remove session from local channel membership set
			Set<BigInteger> localMembers =
			    localChannelMembersMap.get(channelRefId);
			if (localMembers != null) {
			    localMembers.remove(sessionRefId);
			}
		    }
		}
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void prepareToRelocate(BigInteger sessionRefId, long newNodeId,
				      SimpleCompletionHandler handler)
	{
	    // TBD: can't relocate until previous relocation is complete,
	    // i.e., all enqueued requests (if any) need to be delivered to
	    // session first.
	    
	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.get(sessionRefId);		
	    if (channelSet == null) {
		// The session is not a member of any channel.
		handler.completed();
	    } else {
		// Transfer the session's channel membership set to new node.
		relocatingSessions.put(sessionRefId,
				       new RelocationInfo(newNodeId, handler));
		try {
		    // TBD:IoTask?
		    // TBD: Does the channelSet need to be locked for the
		    // toArray call?
		    getChannelServer(newNodeId).
			relocateChannelMemberships(
			    sessionRefId, localNodeId,
			    channelSet.toArray(
				new BigInteger[channelSet.size()]));
		    // TBD: Schedule task to disconnect session if the
		    // channel memberships updated message hasn't been received
		    // by a certain period of time.
		} catch (IOException e) {
		    // TBD: probably want to disconnect the session...
		}
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void relocated(BigInteger sessionRefId) {
	    // Flush any enqueued channel joins/leaves/message
	    // to the client session.
	    List<KernelRunnable> taskQueue =
		relocatedSessionTaskQueues.get(sessionRefId);
	    if (taskQueue != null) {
		synchronized (taskQueue) {
		    for (KernelRunnable task : taskQueue) {
			try {
			    task.run();
			} catch (Exception e) {
			    if (logger.isLoggable(Level.FINE)) {
				logger.logThrow(
				    Level.FINE, e,
				    "Running task:{0} for relocated " +
				    "session:{1} throws", task,
				    HexDumper.toHexString(
					sessionRefId.toByteArray()));
			    }
			}
		    }
		    taskQueue.clear();
		    taskQueue.notify();
		}
		relocatedSessionTaskQueues.remove(sessionRefId);
	    }
	}
    }

    /* -- Other methods and classes -- */

    /**
     * Returns the channel service.
     */
    static ChannelServiceImpl getChannelService() {
	return txnProxy.getService(ChannelServiceImpl.class);
    }
    
    /**
     * Returns the client session service.
     */
    static ClientSessionService getClientSessionService() {
	return txnProxy.getService(ClientSessionService.class);
    }

    /**
     * Returns the task service.
     */
    static TaskService getTaskService() {
	return txnProxy.getService(TaskService.class);
    }

    /**
     * Returns the watchdog service.
     */
    static WatchdogService getWatchdogService() {
	return txnProxy.getService(WatchdogService.class);
    }

    /**
     * Returns the BindingKeyedCollections instance.
     */
    static synchronized BindingKeyedCollections getCollectionsFactory() {
	return collectionsFactory;
    }

    /**
     * Returns the local node ID.
     */
    static long getLocalNodeId() {
	return txnProxy.getService(WatchdogService.class).getLocalNodeId();
    }

    /**
     * Returns the {@code ChannelServer} for the given {@code nodeId},
     * or {@code null} if no channel server exists for the given
     * {@code nodeId}.  If the specified {@code nodeId} is the local
     * node's ID, then this method returns a reference to the server
     * implementation object, rather than the proxy.
     *
     */
    ChannelServer getChannelServer(long nodeId) {
	if (nodeId == localNodeId) {
	    return serverImpl;
	} else {
	    ChannelServer channelServer = channelServerCache.get(nodeId);
	    if (channelServer != null) {
		return channelServer;
	    } else {
		GetChannelServerTask task =
		    new GetChannelServerTask(nodeId);
		try {
		    transactionScheduler.runTask(task, taskOwner);
		    channelServer = task.channelServer;
		    if (channelServer != null) {
			channelServerCache.put(nodeId, channelServer);
		    }
		    return channelServer;
		} catch (RuntimeException e) {
		    throw e;
		} catch (Exception e) {
		    return null;
		}
	    }
	}
    }

    /**
     * Runs the specified non-durable, transactional {@code task} using this
     * service's task owner.
     *
     * @param	task a transactional task
     * @throws	Exception the exception thrown while running {@code task}
     */
    void runTransactionalTask(KernelRunnable task) throws Exception {
	transactionScheduler.runTask(task, taskOwner);
    }

    <R> R runTransactionalCallable(KernelCallable<R> callable)
	throws Exception
    {
	return KernelCallable.call(callable, transactionScheduler, taskOwner);
    }

    /**
     * The {@code RecoveryListener} for handling requests to recover
     * for a failed {@code ChannelService}.
     */
    private class ChannelServiceRecoveryListener
	implements RecoveryListener
    {
	/** {@inheritDoc} */
	public void recover(Node node, SimpleCompletionHandler handler) {
	    final long nodeId = node.getId();
	    final TaskService taskService = getTaskService();
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Node:{0} recovering for node:{1}",
			       localNodeId, nodeId);
		}

		/*
		 * Schedule persistent tasks to perform recovery.
		 */
		transactionScheduler.runTask(
		    new AbstractKernelRunnable("ScheduleRecoveryTasks") {
			public void run() {
			    /*
			     * Reassign each failed coordinator to a new node.
			     */
			    taskService.scheduleTask(
				new ChannelImpl.ReassignCoordinatorsTask(
				    nodeId));
			    /*
			     * Remove binding to channel server proxy for
			     * failed node, and remove proxy's wrapper.
			     */
			    taskService.scheduleTask(
				new RemoveChannelServerProxyTask(nodeId));
			}
		    },
		    taskOwner);
		
		handler.completed();

	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "Recovering for failed node:{0} throws", nodeId);
		// TBD: what should it do if it can't recover?
	    }
	}
    }

    /**
     * The {@code NodeListener} for handling failed node
     * notifications.  When a node's fails, {@code ChannelService}
     * recovery is distributed.  One node recovers for all the
     * failed coordinators (via the {@code RecoveryListener}),
     * and each node removes the failed server node IDs
     * from all the channels for which the node coordinates.
     */
    private class ChannelServiceNodeListener
	implements NodeListener
    {
	/** {@inheritDoc} */
	public void nodeStarted(Node node) {
	    // TBD: cache channel server for node?
	}

	/** {@inheritDoc} */
	public void nodeFailed(Node node) {
	    final long nodeId = node.getId();
	    channelServerCache.remove(nodeId);
	    final TaskService taskService = getTaskService();
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO,
			       "Node:{0} handling nodeFailed:{1}",
			       localNodeId, nodeId);
		}

		/*
		 * Schedule persistent task to remove the failed server's node
		 * ID from locally coordinated channels.
		 */
		transactionScheduler.runTask(
		    new AbstractKernelRunnable(
			"ScheduleRemoveFailedNodeFromLocalChannelsTask")
		    {
			public void run() {
			    taskService.scheduleTask(
				new ChannelImpl.
				    RemoveFailedNodeFromLocalChannelsTask(
					localNodeId, nodeId));
			}
		    }, taskOwner);
		
	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "Node:{0} handling nodeFailed:{1} throws",
		    localNodeId, nodeId);
	    }
	}
    }

    /**
     * Returns the global channel server map, keyed by node ID string.
     */
    private static synchronized BindingKeyedMap<ChannelServer>
	getChannelServerMap()
    {
	return channelServerMap;
    }

    /**
     * A persistent task to remove the channel server proxy for a specified
     * node.
     */
    private static class RemoveChannelServerProxyTask
	 implements Task, Serializable
    {
	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The node ID. */
	private final long nodeId;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code nodeId}.
	 */
	RemoveChannelServerProxyTask(long nodeId) {
	    this.nodeId = nodeId;
	}

	/**
	 * Removes the channel server proxy for the node ID
	 * specified during construction.
	 */
	public void run() {
	    getChannelServerMap().removeOverride(Long.toString(nodeId));
	}
    }

    /**
     * A task to obtain the channel server for a given node.
     */
    private static class GetChannelServerTask extends AbstractKernelRunnable {
	private final long nodeId;
	volatile ChannelServer channelServer = null;

	/** Constructs an instance with the specified {@code nodeId}. */
	GetChannelServerTask(long nodeId) {
	    super(null);
	    this.nodeId = nodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    channelServer = getChannelServerMap().get(Long.toString(nodeId));
	}
    }

    private static Object getObjectForId(BigInteger refId) {
	try {
	    return getDataService().createReferenceForId(refId).get();
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /**
     * A task that adds a relocating session's node to the next channel
     * in the channel membership set (specified during construction) and
     * then reschedules itself to handle the next channel.  If there are
     * no more channels to handle, this task notifies the old node that
     * preparation is completed.
     */
    private class AddRelocatingSessionNodeToChannels
	extends AbstractKernelRunnable
    {
	private final BigInteger sessionRefId;
	private final long oldNodeId;
	private final Queue<BigInteger> channels =
	    new LinkedList<BigInteger>();

	/** Constructs an instance. */
	AddRelocatingSessionNodeToChannels(
	    BigInteger sessionRefId, long oldNodeId, Set<BigInteger> channelSet)
	{
	    super(null);
	    this.sessionRefId = sessionRefId;
	    this.oldNodeId = oldNodeId;
	    this.channels.addAll(channelSet);
	}

	/**
	 * Adds the local node ID to the next channel (if any) in the
	 * queue, or if the queue is empty, notifies the old node's
	 * server that preparation is complete.
	 */
	public void run() {
	    final BigInteger channelRefId = channels.poll();
	    if (channelRefId != null) {
		try {
		    // Add relocating client session to channel
		    transactionScheduler.runTask(
		      new AbstractKernelRunnable(
			    "addRelocatingSessionToChannel")
		      {
			public void run() {
			    ChannelImpl channelImpl = (ChannelImpl)
				getObjectForId(channelRefId);
			    if (channelImpl != null) {
				channelImpl.addServerNodeId(localNodeId);
			    } else {
				// TBD: throw Exception to indicate that
				// channel has been closed so session
				// should be notified (when relocated)
				// via "leave" message?
			    }
			}
		    }, taskOwner);
		} catch (Exception e) {
		    // TBD: exception handling...
		}
		taskScheduler.scheduleTask(this, taskOwner);
		
	    } else {
		// Finished adding relocating session to channels, so notify
		// old node that we are done.
		ChannelServer server = getChannelServer(oldNodeId);
		// TBD: ioTask?
		try {
		    server.channelMembershipsUpdated(sessionRefId, localNodeId);
		} catch (IOException e) {
		    // TBD: exception handling...
		}
	    }
	}	
    }

    /**
     * A task to update local channel membership set and send a join
     * request to a client session.
     */
    private class ChannelJoinTask extends AbstractKernelRunnable {

	private final String name;
	private final BigInteger channelRefId;
	private final BigInteger sessionRefId;
	private final Delivery delivery;

	ChannelJoinTask(String name, BigInteger channelRefId,
			BigInteger sessionRefId, Delivery delivery)

	{
	    super(null);
	    this.name = name;
	    this.channelRefId = channelRefId;
	    this.sessionRefId = sessionRefId;
	    this.delivery = delivery;
	}

	public void run() {
	    // Update local channel membership set.
	    addLocalChannelMember(channelRefId, sessionRefId);

	    // Update per-session channel set.
	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.get(sessionRefId);
	    if (channelSet == null) {
		Set<BigInteger> newChannelSet =
		    Collections.synchronizedSet(new HashSet<BigInteger>());
		channelSet = localPerSessionChannelsMap.
		    putIfAbsent(sessionRefId, newChannelSet);
		if (channelSet == null) {
		    channelSet = newChannelSet;
		}
	    }
	    channelSet.add(channelRefId);
	    
	    // TBD: If the session is disconnecting, then session needs
	    // to be removed from channel's membership list, and the
	    // channel needs to be removed from the channelSet.
	    
	    // Send channel join protocol message.
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		try {
		    protocol.channelJoin(name, channelRefId, delivery);
		} catch (IOException e) {
		    // TBD: session disconnecting?
		    logger.logThrow(Level.WARNING, e,
				    "channelJoin throws");
		} catch (RuntimeException e) {
		    // TBD
		    logger.logThrow(Level.WARNING, e,
				    "channelJoin throws");
		}
	    }
	}
    }

    /**
     * A task to update local channel membership set and send a leave
     * request to a client session.
     */
    private class ChannelLeaveTask extends AbstractKernelRunnable {

	private final BigInteger channelRefId;
	private final BigInteger sessionRefId;

	ChannelLeaveTask(BigInteger channelRefId, BigInteger sessionRefId) {
	    super(null);
	    this.channelRefId = channelRefId;
	    this.sessionRefId = sessionRefId;
	}

	public void run() {

	    // Update local channel membership set.
	    Set<BigInteger> localMembers;
	    localMembers = localChannelMembersMap.get(channelRefId);
	    if (localMembers == null) {
		return;
	    }
	    localMembers.remove(sessionRefId);
	    
	    // Update per-session channel set.
	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.get(sessionRefId);
	    if (channelSet != null) {
		channelSet.remove(channelRefId);
	    }

	    // Send channel leave protocol message.
	    // TBD: does this need to be sent if the channelSet == null?
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		try {
		    protocol.channelLeave(channelRefId);
		} catch (IOException e) {
		    logger.logThrow(Level.WARNING, e,
				    "channelLeave throws");
		} catch (RuntimeException e) {
		    // TBD
		    logger.logThrow(Level.WARNING, e,
				    "channelLeave throws");
		}
	    }
	}
    }
    
    /**
     * Information pertaining to a client session relocating from this node.
     */
    private static class RelocationInfo {
	final long newNodeId;
	final SimpleCompletionHandler handler;

	/** Constructs an instance. */
	RelocationInfo(long newNodeId, SimpleCompletionHandler handler) {
	    this.newNodeId = newNodeId;
	    this.handler = handler;
	}
    }

    /**
     * A task to collect a snapshot of the channel membership for a given channel.
     */
    private class CollectChannelMembershipTask extends AbstractKernelRunnable {

	private final BigInteger channelRefId;
	private final Set<Long> nodeIds;
	private final Set<BigInteger> allMembers = new HashSet<BigInteger>();
	private boolean completed = false;

	CollectChannelMembershipTask(
	    BigInteger channelRefId, Set<Long> nodeIds)
	{
	    super(null);
	    this.channelRefId = channelRefId;
	    this.nodeIds = nodeIds;
	}

	/** {@inheritDoc} */
	public void run() {

	    for (long nodeId : nodeIds) {
		try {
		    ChannelServer server = getChannelServer(nodeId);
		    if (server != null) {
			BigInteger[] nodeMembers =
			    server.getSessions(channelRefId);
			for (BigInteger member : nodeMembers) {
			    allMembers.add(member);
			}
		    }
		} catch (Exception e) {
		    // problem contacting server; continue
		    // TBD: log exception?
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(
			    Level.FINE, e,
			    "getSessions nodeId:{0} channelId:{1} throws",
			    nodeId, channelRefId);
		    }
		}
	    }
	    synchronized (channelMembershipCache) {
		channelMembershipCache.put(channelRefId, allMembers);
	    }
	    synchronized (this) {
		completed = true;
		notifyAll();
	    }
	}

	/**
	 * Returns an unmodifiable set containing a snapshot of all the
	 * members of the channel specified during construction.
	 *
	 * @throws IllegalStateException if the task to collect the the
	 *	   channel membership has not completed
	 */
	synchronized Set<BigInteger> getMembers() {
	    if (!completed) {
		throw new IllegalStateException("not completed");
	    }
	    return Collections.unmodifiableSet(allMembers);
	}
    }

    enum ChannelEventType { JOIN, LEAVE };
    
    private class ChannelEventInfo {

	final ChannelEventType eventType;
	final BigInteger sessionRefId;
	final long eventTimestamp;
	final long expirationTimestamp;
	
	ChannelEventInfo(ChannelEventType eventType,
			 BigInteger sessionRefId,
			 long eventTimestamp,
			 long expirationTimestamp)
	{
	    this.eventType = eventType;
	    this.sessionRefId = sessionRefId;
	    this.eventTimestamp = eventTimestamp;
	    this.expirationTimestamp = expirationTimestamp;
	}
    }
}
