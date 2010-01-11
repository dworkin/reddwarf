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

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelImpl.ChannelMessageInfo;
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
import com.sun.sgs.impl.util.KernelCallable;
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
import com.sun.sgs.service.DataService;
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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * ChannelService implementation. <p>
 * 
 * <p>The {@link #ChannelServiceImpl constructor} requires the <a
 * href="../../../impl/kernel/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property and also supports
 * the following additional properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #EVENTS_PER_TXN_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_EVENTS_PER_TXN}
 *
 * <dd style="padding-top: .5em">Specifies the maximum number of events to
 * process in a single transaction.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #SERVER_PORT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_SERVER_PORT}
 *
 * <dd style="padding-top: .5em">Specifies the port to use for the
 * {@code ChannelServer} of the local node.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #WRITE_BUFFER_SIZE_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_WRITE_BUFFER_SIZE}
 *
 * <dd style="padding-top: .5em">Specifies the approximate write buffer
 *      capacity per channel.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value
 * com.sun.sgs.impl.kernel.StandardProperties#SESSION_RELOCATION_TIMEOUT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i>
 *	{@value
 * com.sun.sgs.impl.kernel.StandardProperties#DEFAULT_SESSION_RELOCATION_TIMEOUT}
 *
 * <dd style="padding-top: .5em">Specifies the timeout, in milliseconds,
 *	for client session relocation. This also specifies the amount of
 *	time to save reliable channel messages so that relocating client
 *	sessions can obtain channel messages that were missed during
 *	relocation. <p>
 * 
 * </dl> <p>
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
    static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port: {@value #DEFAULT_SERVER_PORT}. */
    static final int DEFAULT_SERVER_PORT = 0;

    /** The property name for the maximum number of events to process in a
     * single transaction.
     */
    static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction: {@value #DEFAULT_EVENTS_PER_TXN}. */
    static final int DEFAULT_EVENTS_PER_TXN = 1;
    
    /** The name of the write buffer size property. */
    static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".write.buffer.size";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE}. */
    static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

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

    /** The map of channel coordinator information, keyed by channel ID. */
    private final ConcurrentHashMap<BigInteger, Coordinator>
	coordinatorMap = new ConcurrentHashMap<BigInteger, Coordinator>();
    
    /** The cache of channel membership snapshots, keyed by channel ID.
     * The cache entry timeout is one second.
     */
    private final CacheMap<BigInteger, Set<BigInteger>>
	channelMembershipCache =
	    new CacheMap<BigInteger, Set<BigInteger>>(1000);

    /**
     * The local channel membership info, keyed by channel ID.  This map is
     * synchronized, but should only be locked long enough to obtain a value
     * (a LocalChannelInfo instance) and lock that instance; i.e., map
     * values should be obtained and locked while synchronized on the map
     * instance.  The locking idiom for this map is:
     *
     * <pre>
     *     LocalChannelInfo channelInfo = lockChannel(channelRefId);
     *     if (channelInfo != null) {
     *         try {
     *              // perform operations using channelInfo
     *         } finally {
     *              unlockChannel(channelInfo);
     *         }
     *    }
     * </pre>
     */
    private final Map<BigInteger, LocalChannelInfo>
	localChannelMembersMap = Collections.synchronizedMap(
 	    new HashMap<BigInteger, LocalChannelInfo>());

    /** The local per-session channel maps (key: channel ID, value: message
     * timestamp), keyed by session ID. */
    private final ConcurrentHashMap<BigInteger,
				    Map<BigInteger, LocalMemberInfo>>
	localPerSessionChannelMap =
	    new ConcurrentHashMap<BigInteger,
				  Map<BigInteger, LocalMemberInfo>>();

    /** The map of per-session locks, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, Lock> sessionLocks =
	new ConcurrentHashMap<BigInteger, Lock>();

    /** Map of relocation information (new node ID and completion
     * handler) for client sessions relocating from this node, keyed by
     * the relocating session's ID. */
    private final Map<BigInteger, RelocationInfo>
	outgoingSessionRelocationInfo = Collections.synchronizedMap(
	    new HashMap<BigInteger, RelocationInfo>());

    /** Map for storing pending requests for client sessions relocating to
     * this node, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger,
				    SortedMap<Long, PendingRequests>>
	incomingSessionPendingRequests =
	    new ConcurrentHashMap<BigInteger,
				  SortedMap<Long, PendingRequests>>();

    /** The write buffer size for new channels. */
    private final int writeBufferSize;
    
    /** The maximum number of channel events to service per transaction. */
    final int eventsPerTxn;

    /** The timeout expiration, in milliseconds, for a client session to
     * relocate. */
    final long sessionRelocationTimeout;
    
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
        logger.log(Level.CONFIG, "Creating ChannelServiceImpl");
	
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
	    localNodeId =
		txnProxy.getService(DataService.class).getLocalNodeId();

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
	    sessionRelocationTimeout = wrappedProps.getLongProperty(
		StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY,
		StandardProperties.DEFAULT_SESSION_RELOCATION_TIMEOUT,
		500, Long.MAX_VALUE);
	    
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

            logger.log(Level.CONFIG,
                       "Created ChannelServiceImpl with properties:" +
                       "\n  " + EVENTS_PER_TXN_PROPERTY + "=" + eventsPerTxn +
                       "\n  " + SERVER_PORT_PROPERTY + "=" + serverPort +
                       "\n  " + WRITE_BUFFER_SIZE_PROPERTY + "=" + 
                       writeBufferSize +
		       "\n  " +
		       StandardProperties.SESSION_RELOCATION_TIMEOUT_PROPERTY +
		       "=" + sessionRelocationTimeout);

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
	    callStarted();
	    try {
		if (sessionService.getSessionProtocol(sessionRefId) == null) {
		    return MembershipStatus.UNKNOWN;
		} else {
		    return
			isLocalChannelMember(channelRefId, sessionRefId) ?
			MembershipStatus.MEMBER :
			MembershipStatus.NON_MEMBER;
		}
		
	    } finally {
		callFinished();
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
			    byte deliveryOrdinal, long timestamp,
			    BigInteger sessionRefId)

	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "join name:{0} channelId:{1} " +
			"sessionId:{2} localNodeId:{3}",
			name, HexDumper.toHexString(channelRefId),
			HexDumper.toHexString(sessionRefId), localNodeId);
		}

		return handleNotification(
		    sessionRefId, timestamp,
		    new ChannelJoinTask(
			name, channelRefId,
			Delivery.values()[deliveryOrdinal]));
		
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
	public boolean leave(BigInteger channelRefId, long msgTimestamp,
			     BigInteger sessionRefId)
	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "leave channelId:{0} sessionId:{1}",
			HexDumper.toHexString(channelRefId),
			HexDumper.toHexString(sessionRefId));
		}

		ChannelLeaveTask task = new ChannelLeaveTask(channelRefId);
		boolean success = handleNotification(
		    sessionRefId, msgTimestamp, task);
		task.cleanupIfNoLocalChannelMembership();
		return success;
		
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc} */
	public BigInteger[] getSessions(BigInteger channelRefId) {
	    callStarted();
	    BigInteger[] localMembers = null;
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"getSessions channelId:{0} localNodeId:{1}",
			HexDumper.toHexString(channelRefId), localNodeId);
		}

		LocalChannelInfo channelInfo = lockChannel(channelRefId);
		if (channelInfo == null) {
		    localMembers = new BigInteger[0];
		    return localMembers;
		}
		try {
		    localMembers =  channelInfo.members.toArray(
 			new BigInteger[channelInfo.members.size()]);
		    return localMembers;
		    
		} finally {
		    unlockChannel(channelInfo);
		}
		
	    } finally {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"getSessions channelId:{0} localNodeId:{1} returns:{2}",
			HexDumper.toHexString(channelRefId), localNodeId,
			localMembers);
		}
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Sends the given {@code message} to all local members of the
	 * specified channel.
	 */
	public void send(BigInteger channelRefId, byte[] message,
			 long timestamp)
	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"send channelId:{0} message:{1} timestamp:{2} " +
			"localNodeId:{3}", HexDumper.toHexString(channelRefId),
			HexDumper.format(message, 0x50),
			timestamp, localNodeId);
		}
		LocalChannelInfo channelInfo = lockChannel(channelRefId);
		if (channelInfo == null) {
		    return;
		}

		try {
		    // This check only needs to be made for reliable
		    // channels.  Channel messages for ordered-unreliable
		    // channels are not sent to channel servers more than
		    // once.
		    if (isReliable(channelInfo.delivery) &&
			timestamp <= channelInfo.msgTimestamp)
		    {
			// Reliable messages may be retransmitted on
			// coordinator recovery, so don't deliver messages
			// with a timestamp that is less than or equal to
			// the channel's timestamp of the last delivered
			// message.
			if (logger.isLoggable(Level.FINE)) {
			    logger.log(
				Level.FINE,
				"Dropping message with old timestamp, " +
				"channelId:{0} message:{1} timestamp:{2} " +
				"current timestamp:{3} localNodeId:{4}",
				HexDumper.toHexString(channelRefId),
				HexDumper.format(message, 0x50), timestamp,
				channelInfo.msgTimestamp, localNodeId);
			}
			return;
		    }
		    ChannelSendTask task =
		        new ChannelSendTask(channelRefId, channelInfo.delivery,
					    message);
		    // Note: the message timestamp may not be consecutive
		    // because a non-member sender's message can get dropped.
		    channelInfo.msgTimestamp = timestamp;
		    for (BigInteger sessionRefId : channelInfo.members) {
			// Deliver send request or enqueue for delivery if
			// the session is relocating to this node.  It is
			// safe to ignore the return value. The return
			// value will be false if: the session is no
			// longer locally connected or the session is in
			// the process of moving from this node. In either
			// case, the transient structures associated with
			// the session will be cleaned up.
			handleNotification(sessionRefId, timestamp, task);
		    }
		} finally {
		    unlockChannel(channelInfo);
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
	    BigInteger[] channelRefIds, byte[] deliveryOrdinals,
	    long[] msgTimestamps)
	{
	    callStarted();
	    try {
		/*
		 * Schedule task to add the session's new node (the local
		 * node) to each of its channels if not already present.
		 */
		taskScheduler.scheduleTask(
		    new AddRelocatingSessionNodeToChannels(
			sessionRefId, oldNodeId,
			channelRefIds, deliveryOrdinals, msgTimestamps),
		    taskOwner);
		
	    } finally {
		callFinished();
	    }
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void relocateChannelMembershipsCompleted(
	    BigInteger sessionRefId, long newNodeId)
	{
	    callStarted();
	    try {
		removeLocalSessionFromAllChannels(sessionRefId);
		// Notify completion handler that relocation preparation is
		// done. 
		RelocationInfo relocationInfo =
		    outgoingSessionRelocationInfo.get(sessionRefId);
		if (relocationInfo != null) {
		    relocationInfo.handler.completed();
		}
		
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified channel from the map of local channels,
	 * removes the channel from each of its local member's channel maps and
	 * sends a leave notification to each member.
	 */
	public void close(BigInteger channelRefId, long timestamp) {
	    callStarted();
	    try {
		LocalChannelInfo channelInfo = lockChannel(channelRefId);
		if (channelInfo == null) {
		    return;
		}
		ChannelCloseTask task = new ChannelCloseTask(channelRefId);
		try {
		    for (BigInteger sessionRefId : channelInfo.members) {

			if (!handleNotification(
				sessionRefId, timestamp, task))
			{
			    // Session is not connected, and is not relocating
			    // to this node, so remove it from the channel's
			    // local membership list.
			    removeLocalPerSessionChannel(
				channelRefId, sessionRefId);
			}
			    
		    }
		    localChannelMembersMap.remove(channelRefId);
		} finally {
		    unlockChannel(channelInfo);
		}

	    } finally {
		callFinished();
	    }
	}
	
	/**
	 * Handles the channel request {@code task} for the specified
	 * session and timestamp.  <p>
	 *
	 * If the session is connected to this node, then the specified
	 * {@code task} is run with the specified {@code sessionRefId} and
	 * {@code timestamp}, and {@code true} is returned.  <p>
	 *
	 * If the session is relocating to this node, then the specified
	 * {@code task} is enqueued for execution when the session
	 * completes relocation, and {@code true} is returned. <p>
	 *
	 * If the session is not connected to this node or is relocating
	 * from this node, then {@code false} is returned.
	 */
	private boolean handleNotification(
	    BigInteger sessionRefId, long timestamp, ChannelRequestTask task)
	{
	  try {
	    // Lock the specified session to prevent preparing the
	    // session to relocate from this node while the session's
	    // channel request is being handled.  A client session
	    // relocating from a node should not have any concurrent or
	    // future channel requests processed for it.
	    lockSession(sessionRefId);
	    
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol == null) {
		if (!sessionService.isRelocatingToLocalNode(sessionRefId)) {
		    // The session is not locally-connected and is not
		    // known to be relocating to the local node.
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE, "Dropping channel request for " +
			    "non-local session:{0} channel:{1} timestamp:{2} " +
			    "localNodeId:{3}",
			    HexDumper.toHexString(sessionRefId),
			    HexDumper.toHexString(task.channelRefId),
			    timestamp, localNodeId);
		    }
		    return false;
		}
		    
		// The session is relocating to this node, but the
		// session hasn't been established yet, so enqueue the
		// request until relocation is complete.
		SortedMap<Long, PendingRequests> pendingRequestsMap =
		    incomingSessionPendingRequests.get(sessionRefId);
		if (pendingRequestsMap == null) {
		    SortedMap<Long, PendingRequests> newMap =
			Collections.synchronizedSortedMap(
			    new TreeMap<Long, PendingRequests>());
		    pendingRequestsMap = incomingSessionPendingRequests.
			putIfAbsent(sessionRefId, newMap);
		    if (pendingRequestsMap == null) {
			pendingRequestsMap = newMap;
		    }
		}

		synchronized (pendingRequestsMap) {
		    PendingRequests pendingRequests =
			pendingRequestsMap.get(timestamp);
		    if (pendingRequests == null) {
			pendingRequests = new PendingRequests(timestamp);
			pendingRequestsMap.put(timestamp, pendingRequests);
		    }
		    pendingRequests.addTask(task);
		}

		protocol = sessionService.getSessionProtocol(sessionRefId);
		if (protocol != null) {
		    // Client relocated to this node the specified task was
		    // added to the pending requests map.  The task could
		    // have been added after the 'relocated' notification,
		    // so it wouldn't have been processed.  Therefore
		    // notify the channel service that the session has been
		    // relocated so that it can process the task queue.
		    sessionStatusListener.relocated(sessionRefId);
		}
		
		return true;
		
	    } else {
		// The session is locally-connected, so process the
		// request locally.  If the session just relocated, make
		// sure the pending requests (if any) are processed first.
		SortedMap<Long, PendingRequests> pendingRequestsMap =
		    incomingSessionPendingRequests.get(sessionRefId);
		if (pendingRequestsMap != null) {
		    synchronized (pendingRequestsMap) {
			// If the specified session is relocating to this
			// node, wait for all channel requests enqueued
			// during relocation to be processed.
			while (!pendingRequestsMap.isEmpty()) {
			    try {
				pendingRequestsMap.wait(500);
			    } catch (InterruptedException e) {
			    }
			    protocol = 
				sessionService.getSessionProtocol(sessionRefId);
			    if (protocol == null) {
				// Session disconnected while processing
				// pending requests.
				return false;
			    }
			}
		    }
		}
		
		RelocationInfo info =
		    outgoingSessionRelocationInfo.get(sessionRefId);
		if (info != null) {
		    // Session is relocating from this node.
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE, "Dropping channel request for " +
			    "relocating session:{0} channel:{1} " +
			    "timestamp:{2} localNodeId:{3} newNodeId:{4}",
			    HexDumper.toHexString(sessionRefId),
			    HexDumper.toHexString(task.channelRefId),
			    timestamp, localNodeId, info.newNodeId);
		    }
		    return false;
		}

		try {
		    task.run(sessionRefId, timestamp);
		    return true;
		    
		} catch (Exception e) {
		    if (logger.isLoggable(Level.WARNING)) {
			logger.logThrow(
			    Level.WARNING, e,
			    "Running channel request task:{0} for " +
			    "session:{1} and timestamp:{2} on node:{3} " +
			    "throws", task,
			    HexDumper.toHexString(sessionRefId),
			    timestamp, localNodeId);
		    }
		    return false;
		}
	    }
	    
	  } finally {
	      unlockSession(sessionRefId);
	  }
	}
    }

    /**
     * Updates the local channel members set by adding the specified {@code
     * sessionRefId} as a local member of the channel with the specified
     * {@code channelRefId}.  ALSO adds the {@code channelRefId} to the the
     * local per-session channel map for {@code sessionRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	delivery the channel's delivery guarantee
     * @param	sessionRefId a session ID
     * @param	timestamp the session's timestamp--if no messages
     *		received yet, then timestamp of when session joined
     *		channel, otherwise the timestamp of the last message
     *		received
     * @param	isRelocating if session is relocating to this node
     */
    private void addLocalChannelMember(final BigInteger channelRefId,
				       Delivery delivery,
				       final BigInteger sessionRefId,
				       long timestamp,
				       boolean isRelocating)
    {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST,
		"Adding local member session:{0} to channel:{1} " +
		"timestamp:{2} isRelocating:{3} " +
		"localNodeId:{4}", HexDumper.toHexString(sessionRefId),
		HexDumper.toHexString(channelRefId), timestamp,
		isRelocating, localNodeId);
	}

	// Lock local channel info; create and store if absent.
	LocalChannelInfo channelInfo = null;
	boolean addedChannelInfo = false;
	synchronized (localChannelMembersMap) {
	    channelInfo = localChannelMembersMap.get(channelRefId);
	    if (channelInfo == null) {
		channelInfo = new LocalChannelInfo(delivery, timestamp);
		localChannelMembersMap.put(channelRefId, channelInfo);
		addedChannelInfo = true;
	    }
	    channelInfo.lock.lock();
	}

	try {
	    if (addedChannelInfo && isRelocating) {
		// If the relocating session establishes the
		// channel info on the local node, then the local
		// node needs to be added to the channel's list.
		long currentTimestamp =
		    addLocalNodeToChannel(channelRefId);
		if (currentTimestamp == -1L) {
		    // The channel is closed, so send leave message.
		    // If invocation returns false, the session
		    // probably disconnected.
		    serverImpl.handleNotification(
			sessionRefId, timestamp,
			new ChannelLeaveTask(channelRefId));
		    return;
		}
		// Message timestamp may be out of date with
		// channel's current timestamp, so update
		// if necessary.
		if (currentTimestamp > timestamp) {
		    channelInfo.msgTimestamp = currentTimestamp;
		}
	    }

	    // Update channel's local membership set.
	    channelInfo.members.add(sessionRefId);

	    // Update per-session channel set.
	    Map<BigInteger, LocalMemberInfo> channelMap =
		localPerSessionChannelMap.get(sessionRefId);
	    if (channelMap == null) {
		Map<BigInteger, LocalMemberInfo> newChannelMap =
		    Collections.synchronizedMap(
		        new HashMap<BigInteger, LocalMemberInfo>());
		channelMap = localPerSessionChannelMap.
		    putIfAbsent(sessionRefId, newChannelMap);
		if (channelMap == null) {
		    channelMap = newChannelMap;
		}
	    }
	    channelMap.put(channelRefId,
			   new LocalMemberInfo(channelInfo, timestamp));
	    
	    if (isRelocating && isReliable(delivery) &&
		channelInfo.msgTimestamp > timestamp)
	    {
		/*
		 * If session is relocating, then it may have missed some
		 * channel messages.  If the channel is reliable and the
		 * session's message timestamp for the channel is less than the
		 * channel's current timestamp, then retrieve missing messages.
		 * TBD: (performance) Cache saved messages at the local node?
		 */
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"Retrieving missed messages for session:{0} " +
			"channel:{1} fromTimestamp:{2} toTimestamp:{3} " +
			"localNodeId:{4}", HexDumper.toHexString(sessionRefId),
			HexDumper.toHexString(channelRefId), timestamp + 1,
			channelInfo.msgTimestamp, localNodeId);
		}

		List<ChannelMessageInfo> missingMessages =
		    getChannelMessages(
			channelRefId, timestamp + 1, channelInfo.msgTimestamp);
		if (missingMessages != null) {
		    for (ChannelMessageInfo messageInfo : missingMessages) {
			serverImpl.handleNotification(
 			    sessionRefId, messageInfo.timestamp,
			    new ChannelSendTask(
 				channelRefId, channelInfo.delivery,
				messageInfo.message));
		    }
		}
	    }
	} finally {
	    channelInfo.lock.unlock();
	}
    }

    /**
     * Adds the local node ID to the server node list for the specified
     * {@code channelRefId} and returns the current message timestamp for
     * the channel, or returns {@code -1L} if the channel is nonexistent or
     * closed. This method should be invoked outside of a transaction.
     */
    private long addLocalNodeToChannel(final BigInteger channelRefId) {
	try {
	    return runTransactionalCallable(
		new KernelCallable<Long>("addLocalNodeIdToChannel") {
		    public Long call() {
			ChannelImpl channelImpl = (ChannelImpl)
			    getObjectForId(channelRefId);
			
			if (channelImpl == null || channelImpl.isClosed()) {
			    if (logger.isLoggable(Level.FINE)) {
				logger.log(
				    Level.FINE,
				    "Unable to add localNodeId:{0} to " +
				    "closed channel:{1}", localNodeId,
				    HexDumper.toHexString(channelRefId));
			    }
			    return -1L;
			} else {
			    channelImpl.addServerNodeId(localNodeId);
			    return channelImpl.getCurrentMessageTimestamp();
			}
		    }
		});
	} catch (Exception e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "Attempting to add localNodeId:{0} to channel:{1} throws",
		    localNodeId, HexDumper.toHexString(channelRefId));
	    }
	    return -1L;
	}
    }

    /**
     * Returns a list containing saved channel messages for the channel
     * with the specified {@code channelRefId} with timestamps between
     * {@code fromTimestamp} and {@code toTimestamp} inclusive or
     * {@code null} if the channel no longer exists.
     */
    private List<ChannelMessageInfo> getChannelMessages(
 	final BigInteger channelRefId, final long fromTimestamp,
	final long toTimestamp)
    {
	try {
	    return runTransactionalCallable(
		new KernelCallable<List<ChannelMessageInfo>>(
		    "getChannelMessagesFromTimestamp")
		{
		    public List<ChannelMessageInfo> call() {
			ChannelImpl channelImpl = (ChannelImpl)
			    getObjectForId(channelRefId);
			
			if (channelImpl == null || channelImpl.isClosed()) {
			    if (logger.isLoggable(Level.FINE)) {
				logger.log(
				    Level.FINE,
				    "Unable to obtain messages for" +
				    "closed channel:{0}",
				    HexDumper.toHexString(channelRefId));
			    }
			    return null;
			} else {
			    return channelImpl.
				getChannelMessages(fromTimestamp, toTimestamp);
			}
		    }
		});
	} catch (Exception e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "Obtaining messages for channel:{0} throws",
		    localNodeId, HexDumper.toHexString(channelRefId));
	    }
	    return null;
	}
    }
	
    /**
     * Removes the specified {@code sessionRefId} ONLY from the local channel
     * members set for the specified {@code channelRefId}.
     */
    private void removeLocalChannelMember(
	BigInteger channelRefId, BigInteger sessionRefId)
    {
	LocalChannelInfo channelInfo = lockChannel(channelRefId);
	if (channelInfo != null) {
	    try {
		channelInfo.members.remove(sessionRefId);
	    } finally {
		unlockChannel(channelInfo);
	    }
	}
    }

    /**
     * Removes the specified {@code channelRefId} ONLY from the the per-session
     * local channel map for the specified {@code sessionRefId}. This method
     * does not remove the {@code sessionRefId} from the local channel members
     * set for the channel.
     */
    private void removeLocalPerSessionChannel(
	BigInteger channelRefId, BigInteger sessionRefId)
    {
	Map<BigInteger, LocalMemberInfo> channelMap =
	    localPerSessionChannelMap.get(sessionRefId);
	if (channelMap != null) {
	    channelMap.remove(channelRefId);
	}
    }

    /**
     * Removes the local session from each of its channel's membership sets,
     * and removes the local session from the local per-session channel map.
     * This method is invoked when a session is relocated or disconnected to
     * clean up the session's transient information.
     */
    private void removeLocalSessionFromAllChannels(BigInteger sessionRefId) {

	/*
	 * Remove the per-session channel map for the session.
	 */
	Map<BigInteger, LocalMemberInfo> channelMap =
	    localPerSessionChannelMap.remove(sessionRefId);		
	
	/*
	 * Remove the session from the local membership set of each channel
	 * that it is currently a member of.
	 */
	if (channelMap != null) {
	    synchronized (channelMap) {
		for (BigInteger channelRefId : channelMap.keySet()) {
		    removeLocalChannelMember(channelRefId, sessionRefId);
		}
	    }
	}
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
	LocalChannelInfo channelInfo = lockChannel(channelRefId);
	if (channelInfo != null) {
	    try {
		return channelInfo.members.contains(sessionRefId);
	    } finally {
		unlockChannel(channelInfo);
	    }
	} else {
	    return false;
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
	    LocalChannelInfo channelInfo = lockChannel(channelRefId);
	    if (channelInfo != null) {
		try {
		    return Collections.unmodifiableSet(channelInfo.members);
		} finally {
		    unlockChannel(channelInfo);
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
			    "members:{2}", HexDumper.toHexString(channelRefId),
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
     * channelRefId} (when the current transaction commits).
     */
    void addChannelTaskOnCommit(
	BigInteger channelRefId, final IoRunnable ioTask, final long nodeId)
    {
	addChannelTaskOnCommit(
	    channelRefId,
	    new AbstractKernelRunnable("RunIoTask") {
		public void run() {
		    runIoTask(ioTask, nodeId);
		} });
    }

    /**
     * Adds the specified non-transactional {@code task} to the task list 
     * of the given {@code channelRefId} (when the current transaction
     * commits). 
     *
     * @param	channelRefId a channel ID
     * @param	task a non-transactional task
     */
    void addChannelTaskOnCommit(BigInteger channelRefId, KernelRunnable task) {
	Context context = contextFactory.joinTransaction();
	context.addTask(channelRefId, task);
    }

    /**
     * Adds the specified {@code channelRefId} to the list of
     * locally-coordinated channels that need servicing after the current
     * transaction commits.
     *
     * @param	channelRefId a channel ID for a locally-coordinated channel
     */
    void addServiceEventQueueTaskOnCommit(BigInteger channelRefId) {
	Context context = contextFactory.joinTransaction();
	context.addChannelToService(channelRefId);
    }

    /* -- Implement TransactionContext -- */

    /**
     * This transaction context maintains a per-channel list of
     * non-transactional tasks to perform when the transaction commits. A
     * task is added to the context by a {@code ChannelImpl} via the {@code
     * addChannelTaskOnCommit} method.  Such non-transactional tasks include
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
	    boolean readOnly =
		internalTaskLists.isEmpty() && channelsToService.isEmpty();
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
	 * during this transaction) to their respective coordinator's
	 * channel server task queues, then (for all channels needing
	 * servicing) notifies their respective coordinators that their
	 * event queues need servicing, and returns true; otherwise
	 * returns false.
	 */
	private boolean flush() {
	    assert Thread.holdsLock(contextList);
	    if (isCommitted) {
		for (BigInteger channelRefId : internalTaskLists.keySet()) {
		    getCoordinator(channelRefId).
			addChannelNotificationTasks(
			    internalTaskLists.get(channelRefId));
		}
		for (BigInteger channelRefId : channelsToService) {
		    addServiceEventQueueTask(channelRefId);
		}
	    }
	    return isCommitted;
	}
    }

    /**
     * Notifies this service that the channel with the specified {@code
     * channelRefId} is closed so that this service can clean up any
     * per-channel data structures (relating to the channel coordinator).
     */
    void closedChannel(BigInteger channelRefId) {
	synchronized (contextList) {
	    coordinatorMap.remove(channelRefId);
	}
    }
    /* -- Implement ClientSessionStatusListener -- */

    private final class SessionStatusListener
	implements ClientSessionStatusListener
    {
        /**
         * {@inheritDoc}
	 */
	public void disconnected(
	    BigInteger sessionRefId, boolean isRelocating)
	{
	    removeLocalSessionFromAllChannels(sessionRefId);
	    if (isRelocating) {
		outgoingSessionRelocationInfo.remove(sessionRefId);
	    } else {
		// Clean up pending requests, if any.
		SortedMap<Long, PendingRequests> pendingRequestsMap =
		    incomingSessionPendingRequests.remove(sessionRefId);
		if (pendingRequestsMap != null) {
		    synchronized (pendingRequestsMap) {
			pendingRequestsMap.clear();
			pendingRequestsMap.notifyAll();
		    }
		}
	    }
	    sessionLocks.remove(sessionRefId);
	}

	/**
	 * {@inheritDoc}
	 */
	public void prepareToRelocate(final BigInteger sessionRefId,
				      final long newNodeId,
				      SimpleCompletionHandler handler)
	{
	    try {
		// Put session in the map of (outgoing) sessions relocating
		// from this node, locking the session during the operation
		// to prevent any channel requests from being processed
		// during or after the session is marked for relocation.
		lockSession(sessionRefId);
		outgoingSessionRelocationInfo.put(
		    sessionRefId, new RelocationInfo(newNodeId, handler));
	    } finally {
		unlockSession(sessionRefId);
	    }
	    
	    Map<BigInteger, LocalMemberInfo> channelMap =
		localPerSessionChannelMap.get(sessionRefId);		
	    if (channelMap == null) {
		// The session is not a member of any channel, so preparation
		// is complete.
		handler.completed();
	    } else {
		// Transfer the session's channel membership set to new node.
		int size = channelMap.size();
		final BigInteger[] channelRefIds = new BigInteger[size];
		final byte[] deliveryOrdinals = new byte[size];
		final long[] msgTimestamps = new long[size];
		int i = 0;
		synchronized (channelMap) {
		    for (Map.Entry<BigInteger, LocalMemberInfo> entry :
			     channelMap.entrySet())
		    {
			channelRefIds[i] = entry.getKey();
			LocalMemberInfo memberInfo = entry.getValue();
			deliveryOrdinals[i] = (byte)
			    memberInfo.channelInfo.delivery.ordinal();
			msgTimestamps[i] = memberInfo.msgTimestamp;
			i++;
		    }
		}
		taskScheduler.scheduleTask(
		    new AbstractKernelRunnable("relocateMemberships") {
			public void run() {
			    runIoTask(new IoRunnable() {
			        public void run() throws IOException {
				    getChannelServer(newNodeId).
					relocateChannelMemberships(
 					    sessionRefId, localNodeId,
					    channelRefIds, deliveryOrdinals,
					    msgTimestamps);
				} }, newNodeId);
			} }, taskOwner);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void relocated(BigInteger sessionRefId) {
	    // Flush any enqueued channel joins/leaves/message
	    // to the client session.

	    SortedMap<Long, PendingRequests> pendingRequestsMap =
		incomingSessionPendingRequests.get(sessionRefId);
	    if (pendingRequestsMap != null) {
		synchronized (pendingRequestsMap) {
		    for (PendingRequests pendingRequests :
			     pendingRequestsMap.values())
		    {
			pendingRequests.processRequests(sessionRefId);
		    }
		    pendingRequestsMap.clear();
		    pendingRequestsMap.notifyAll();
		}
		incomingSessionPendingRequests.remove(sessionRefId);
	    }
	}
    }

    /* -- Other methods and classes -- */

    /**
     * Returns the channel service.
     */
    static synchronized ChannelServiceImpl getInstance() {
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
	return txnProxy.getService(DataService.class).getLocalNodeId();
    }

    /**
     * Returns {@code true} if this specified {@code delivery} guarantee
     * supports reliable message delivery, otherwise returns {@code false}.
     */
    private static boolean isReliable(Delivery delivery) {
	return
	    delivery.equals(Delivery.RELIABLE) ||
	    delivery.equals(Delivery.UNORDERED_RELIABLE);
    }
    
    /**
     * Obtains the lock associated with the specified {@code sessionRefId}.
     * If the lock is not currently available, this method waits until the
     * lock is freed.
     *
     * @param	sessionRefId a session ID
     */
    private void lockSession(BigInteger sessionRefId) {
	Lock lock = sessionLocks.get(sessionRefId);
	if (lock == null) {
	    Lock newLock = new ReentrantLock();
	    lock = sessionLocks.putIfAbsent(sessionRefId, newLock);
	    if (lock == null) {
		lock = newLock;
	    }
	}
	lock.lock();
    }

    /**
     * Frees the lock associated with the specified (@code sessionRefId}.
     * This method must be invoked to free the lock held by invoking the
     * {@code lockSession} method.
     *
     * @param	sessionRefId a session ID
     */
    private void unlockSession(BigInteger sessionRefId) {
	Lock lock = sessionLocks.get(sessionRefId);
	if (lock != null) {
	    lock.unlock();
	} else {
	    logger.log(Level.WARNING,
		       "Atttempt to unlock missing lock for session:{0}",
		       HexDumper.toHexString(sessionRefId));
	}
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

    /**
     * Runs the specified non-durable, transactional {@code callable} using
     * this service's task owner, and returns the result.
     *
     * @param	callable a callable to call
     * @throws	Exception the exception thrown while calliing {@code callable}
     */
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
	public void nodeHealthUpdate(Node node) {
            // Only worry about node failures
            if (node.isAlive()) {
                return;
            }

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
    static Object getObjectForId(BigInteger refId) {
	try {
	    return getInstance().getDataService().
		createReferenceForId(refId).get();
	} catch (ObjectNotFoundException e) {
	    return null;
	}
    }

    /**
     * A task that adds a relocating session's node to the channels in the
     * {@code channelRefId}'s array (specified during construction).  When
     * this task is complete, it notifies the old node that relocation
     * preparation is complete by invoking the {@code
     * relocateChannelMembershipsCompleted} method.
     */
    private class AddRelocatingSessionNodeToChannels
	extends AbstractKernelRunnable
    {
	private final BigInteger sessionRefId;
	private final long oldNodeId;
	private final BigInteger[] channelRefIds;
	private final byte[] deliveryOrdinals;
	private final long[] msgTimestamps;

	/** Constructs an instance. */
	AddRelocatingSessionNodeToChannels(
	    BigInteger sessionRefId, long oldNodeId,
	    BigInteger[] channelRefIds, byte[] deliveryOrdinals,
	    long[] msgTimestamps)
	{
	    super(null);
	    this.sessionRefId = sessionRefId;
	    this.oldNodeId = oldNodeId;
	    this.channelRefIds = channelRefIds;
	    this.deliveryOrdinals = deliveryOrdinals;
	    this.msgTimestamps = msgTimestamps;
	}

	/**
	 * Adds the local node ID to each channel and notifies the old node's
	 * server that preparation is complete.
	 */
	public void run() {
	    
	    for (int i = 0; i < channelRefIds.length; i++) {
		BigInteger channelRefId = channelRefIds[i];
		Delivery delivery = Delivery.values()[deliveryOrdinals[i]];
		long msgTimestamp = msgTimestamps[i];
		addLocalChannelMember(
		    channelRefId, delivery, sessionRefId, msgTimestamp, true);
	    }

	    /*
	     * Finished adding relocating session to channels, so notify
	     * old node that we are done.
	     */
	    final ChannelServer server = getChannelServer(oldNodeId);
	    runIoTask(
		new IoRunnable() {
		    public void run() throws IOException {
			server.relocateChannelMembershipsCompleted(
			    sessionRefId, localNodeId);
		    } },
		oldNodeId);
	}
    }

    /**
     * An abstract class for processing a channel request (sent by the
     * channel's coordinator) for the channel specified during
     * construction.
     */
    private abstract static class ChannelRequestTask {

	protected final BigInteger channelRefId;

	ChannelRequestTask(BigInteger channelRefId) {
	    this.channelRefId = channelRefId;
	}

	/**
	 * Processes the channel request for the specified {@code
	 * sessionRefId} and message {@code timestamp} which may update
	 * local, transient structures, and then may deliver the
	 * appropriate notification to the client session for the given
	 * {@code sessionRefId}.
	 *
	 * @param sessionRefId a client session ID
	 * @param timestamp a message timestamp
	 */
	public abstract void run(BigInteger sessionRefId, long timestamp);
    }
	
    /**
     * A task to update local channel membership set and send a join
     * request to a client session.
     */
    private class ChannelJoinTask extends ChannelRequestTask {

	private final String name;
	private final Delivery delivery;

	ChannelJoinTask(String name, BigInteger channelRefId,
			Delivery delivery)
	{
	    super(channelRefId);
	    this.name = name;
	    this.delivery = delivery;
	}

	public void run(BigInteger sessionRefId, long timestamp) {
	    // Update local channel membership set.
	    addLocalChannelMember(
 		channelRefId, delivery, sessionRefId, timestamp, false);

	    // Send channel join protocol message.
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		try {
		    protocol.channelJoin(name, channelRefId, delivery);
		} catch (IOException e) {
		    logger.logThrow(Level.WARNING, e, "channelJoin throws");
		}
	    }
	}
    }
    
    /**
     * A task to update local channel membership set and send a leave
     * request to a client session.
     */
    private class ChannelLeaveTask extends ChannelRequestTask {

	volatile boolean isCompleted = false;

	ChannelLeaveTask(BigInteger channelRefId) {
	    super(channelRefId);
	}

	public void run(BigInteger sessionRefId, long timestamp) {

	    // Remove channel from per-session channel set, and remove session
	    // from local channel membership set.
	    removeLocalPerSessionChannel(channelRefId, sessionRefId);
	    removeLocalChannelMember(channelRefId, sessionRefId);
	    
	    // Send channel leave protocol message.
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		try {
		    protocol.channelLeave(channelRefId);
		} catch (IOException e) {
		    logger.logThrow(Level.WARNING, e,
				    "channelLeave throws");
		}
	    }
	    isCompleted = true;
	}

	/**
	 * If this task removed the last local channel member, then remove
	 * the local node ID from the channel's server list, and remove the
	 * channel from the map of local channel members.
	 */
	public void cleanupIfNoLocalChannelMembership() {
	    if (isCompleted) {
		synchronized (localChannelMembersMap) {
		    // Check if there are no more channel members on this
		    // node and, if so, remove the node from the channel's
		    // server list.
		    LocalChannelInfo channelInfo = lockChannel(channelRefId);
		    if (channelInfo == null) {
			return;
		    }
		    try {
			if (channelInfo.members.isEmpty()) {
			    try {
			      runTransactionalTask(
			          new AbstractKernelRunnable(
				      "removeNodeIdFromChannel") {
				    public void run() {
					ChannelImpl channel = (ChannelImpl)
					    getObjectForId(channelRefId);
					if (channel != null) {
					    channel.removeServerNodeId(
						localNodeId);
					}
				    } });
				    
			    } catch (Exception e) {
				// Transaction scheduler will print out warning.
			    }
			    localChannelMembersMap.remove(channelRefId);
			}
		    } finally {
			unlockChannel(channelInfo);
		    }
		}
	    }
	}
    }

    /**
     * A task to update local channel membership set and send a leave
     * request to a client session.
     */
    private class ChannelSendTask extends ChannelRequestTask {

	private final Delivery delivery;
	private final byte[] message;

	ChannelSendTask(BigInteger channelRefId, Delivery delivery,
			byte[] message)
	{
	    super(channelRefId);
	    this.delivery = delivery;
	    this.message = message;
	}
	
	public void run(BigInteger sessionRefId, long timestamp) {

	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		Map<BigInteger, LocalMemberInfo> channelMap =
		    localPerSessionChannelMap.get(sessionRefId);
		if (channelMap == null) {
		    // The session doesn't belong to any channels.
		    return;
		}
		LocalMemberInfo memberInfo = channelMap.get(channelRefId);
		if (memberInfo == null) {
		    // The session is no longer a member.
		    return;
		}
		if (memberInfo.msgTimestamp > timestamp) {
		    // If session's message timestamp for this channel is
		    // greater than the timestamp of the message to be
		    // delivered, then this is an earlier message sent
		    // before the session joined the channel.  Therefore
		    // don't deliver the message.
		    return;
		}
		memberInfo.msgTimestamp = timestamp;
		try {
		    protocol.channelMessage(
			channelRefId, ByteBuffer.wrap(message), delivery);
		} catch (IOException e) {
		    logger.logThrow(Level.WARNING, e,  "channelMessage " +
			"session:{0} channel:{0} throws",
			HexDumper.toHexString(sessionRefId),
			HexDumper.toHexString(channelRefId));
		}
	    }
	}
    }
    
    /**
     * A task to update local channel membership set and send a close
     * request to a client session.
     */
    private class ChannelCloseTask extends ChannelRequestTask {

	ChannelCloseTask(BigInteger channelRefId) {
	    super(channelRefId);
	}

	public void run(BigInteger sessionRefId, long timestamp) {
	    SessionProtocol protocol =
		sessionService.getSessionProtocol(sessionRefId);
	    if (protocol != null) {
		try {
		    protocol.channelLeave(channelRefId);
		} catch (IOException e) {
		    logger.logThrow(
			Level.WARNING, e, "channelLeave " +
			"session:{0} channel:{0} throws",
			HexDumper.toHexString(sessionRefId),
			HexDumper.toHexString(channelRefId));
		}
		removeLocalPerSessionChannel(channelRefId, sessionRefId);
	    }
	}
    }
    
    /**
     * A task to collect a snapshot of the channel membership for a given
     * channel.
     */
    private class CollectChannelMembershipTask extends AbstractKernelRunnable {

	private final BigInteger channelRefId;
	private final Set<Long> nodeIds;
	private final Set<BigInteger> allMembers = new HashSet<BigInteger>();
	private boolean completed = false;

	/** Constructs an instance. */
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

    /** Channel membership event types. */
    static enum MembershipEventType { JOIN, LEAVE };

    /**
     * Channel event information for join/leave requests.  If a channel is
     * coordinated locally, channel join/leave requests should to be
     * saved until the channel's timestamp reaches the specified {@code
     * expirationTimestamp} so that a sender's channel membership status
     * can be quickly and correctly determined without having to verify
     * the membership with the session's (potentially remote) node.
     */
    private static class MembershipEventInfo {

	final MembershipEventType eventType;
	final BigInteger sessionRefId;
	final long eventTimestamp;
	final long expirationTimestamp;
	
	MembershipEventInfo(MembershipEventType eventType,
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

    /**
     * Returns the locked {@code LocalChannelInfo} instance for the
     * specified {@code channelRefId}, or {@code null} if no such instance
     * exists.  If this method returns a non-null value, the caller is
     * responsible for unlocking the returned instance by invoking {@code
     * unlockChannel} passing the instance.
     */
    private LocalChannelInfo lockChannel(BigInteger channelRefId) {
	synchronized (localChannelMembersMap) {
	    LocalChannelInfo channelInfo =
		localChannelMembersMap.get(channelRefId);
	    if (channelInfo != null) {
		channelInfo.lock.lock();
	    }
	    return channelInfo;
	}
    }
    
    /**
     * Unlocks the specified {@code channelInfo} instance.
     */
    private void unlockChannel(LocalChannelInfo channelInfo) {
	assert channelInfo != null;
	channelInfo.lock.unlock();
    }

    /**
     * Information for a channel with local members.  An instance's 'lock' 
     * must be locked before accessing other fields of the instance.
     */
    private static class LocalChannelInfo {
	/** A lock for this instance. */
	final Lock lock = new ReentrantLock();
	/** The channel's delivery guarantee. */
	final Delivery delivery;
	/** The channel's membership set. */
	final Set<BigInteger> members = new HashSet<BigInteger>();
	/** The last message delivered to the channel. */
	long msgTimestamp;

	/** Constructs an instance. */
	LocalChannelInfo(Delivery delivery, long msgTimestamp) {
	    this.delivery = delivery;
	    this.msgTimestamp = msgTimestamp;
	}
    }

    /**
     * Information for a local channel member.
     */
    private static class LocalMemberInfo {
	/** The channel info for this member. */
	final LocalChannelInfo channelInfo;
	/** The member's msgTimestamp--if no messages received yet, then
	 * the timestamp of when session joined channel, otherwise the
	 * timestamp of the last message received. Updated upon message
	 * send.  */
	long msgTimestamp;

	/** Constructs an instance. */
	LocalMemberInfo(LocalChannelInfo channelInfo, long msgTimestamp) {
	    this.channelInfo = channelInfo;
	    this.msgTimestamp = msgTimestamp;
	}
    }

    /**
     * Information pertaining to a client session relocating from this node.
     */
    private static class RelocationInfo {
	/** The session's new node ID. */
	final long newNodeId;
	/** The handler to notify when relocation preparation is complete. */
	final SimpleCompletionHandler handler;

	/** Constructs an instance. */
	RelocationInfo(long newNodeId, SimpleCompletionHandler handler) {
	    this.newNodeId = newNodeId;
	    this.handler = handler;
	}
    }

    /**
     * The pending channel requests for an associated timestamp that are
     * enqueued for a given session relocating to this node.
     */
    private static class PendingRequests {
	/** The queue of join/leave tasks. */
	final List<ChannelRequestTask> membershipUpdates =
	    new LinkedList<ChannelRequestTask>();
	/** The queue of send request tasks. */
	final List<ChannelRequestTask> sendRequests =
	    new LinkedList<ChannelRequestTask>();
	/** The timestamp for this instance's pending requests. */
	final long timestamp;

	/** Constructs an instance with the specified {@code timestamp}. */
	PendingRequests(long timestamp) {
	    this.timestamp = timestamp;
	}

	void addTask(ChannelRequestTask task) {
	    if (task instanceof ChannelSendTask) {
		sendRequests.add(task);
	    } else {
		membershipUpdates.add(task);
	    }
	}

	/**
	 * Processes pending requests for the associated timestamp.
	 */
	void processRequests(BigInteger sessionRefId) {

	    // process membership updates (join/leave/close)
	    for (ChannelRequestTask task : membershipUpdates) {
		try {
		    task.run(sessionRefId, timestamp);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(
			    Level.FINE, e,
			    "Running task:{0} for relocated " +
			    "session:{1} throws", task,
			    HexDumper.toHexString(sessionRefId));
		    }
		}
	    }

	    // process send requests
	    for (ChannelRequestTask task : sendRequests) {
		try {
		    task.run(sessionRefId, timestamp);
		} catch (Exception e) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(
			    Level.FINE, e,
			    "Running task:{0} for relocated " +
			    "session:{1} throws", task,
			    HexDumper.toHexString(sessionRefId));
		    }
		}
	    }
	    
	}
    }

    /* -- Implement Coordinator -- */

    /**
     * Adds a task to service the event queue of the channel with the
     * specified {@code channelRefId}.  This method is only invoked on the
     * channel's coordinator node.<p>  
     *
     * @param	channelRefId a channel ID
     */
    void addServiceEventQueueTask(final BigInteger channelRefId) {
	checkNonTransactionalContext();
	getCoordinator(channelRefId).addServiceEventQueueTask();

    }

    /**
     * Caches the channel membership event with the specified {@code
     * eventType}, {@code channelRefId}, {@code sessionRefId}, and {@code
     * eventTimestamp}.  This method is only invoked on the channel's
     * coordinator node.
     *
     * @param	eventType an membership event type
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID, or {@code null}
     * @param	eventTimestamp the event's timestamp
     * @param	expirationTimestamp the event queue's timestamp
     */
    void cacheMembershipEvent(MembershipEventType eventType,
			      BigInteger channelRefId,
			      BigInteger sessionRefId,
			      long eventTimestamp,
			      long expirationTimestamp)
    {
	getCoordinator(channelRefId).
	    cacheMembershipEvent(eventType, sessionRefId,
				 eventTimestamp, expirationTimestamp);
    }
    
    /**
     * Returns {@code true} if the session with the specified {@code
     * sessionRefId} is a member of the channel with the specified {@code
     * channelRefId}, and {@code false} otherwise. This method is only
     * invoked on the channel's coordinator node.
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
		       "timestamp:{3}", HexDumper.toHexString(channelRefId),
		       HexDumper.toHexString(sessionRefId), isChannelMember,
		       timestamp);
	}
	Coordinator coordinator = coordinatorMap.get(channelRefId);

	if (coordinator != null) {
	    isChannelMember = coordinator.
		isChannelMember(sessionRefId, isChannelMember, timestamp);
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "isChannelMember returns: {0}",
		       isChannelMember);
	}
	
	return isChannelMember;
    }

    /**
     * Returns the {@code Coordinator} instance for the specified {@code
     * channelRefId}, constructing a new instance if absent.
     */
    private Coordinator getCoordinator(BigInteger channelRefId) {
	
	Coordinator coord = coordinatorMap.get(channelRefId);
	if (coord == null) {
	    Coordinator newCoord = new Coordinator(channelRefId);
	    coord = coordinatorMap.putIfAbsent(channelRefId, newCoord);
	    if (coord == null) {
		coord = newCoord;
	    }
	}
	return coord;
    }

    /**
     * Channel coordinator transient data and operations.  A coordinator
     * instance manages incoming 'serviceEventQueue' requests for the
     * coordinator, manages the ordering of outgoing channel server
     * notifications, and maintains a cache of recent membership events
     * for determining a sending client session's membership.
     */
    private class Coordinator {

	/** The channel ID. */
	private final BigInteger channelRefId;
	
	/** A transactional task queue for ordering the delivery of
	 * 'serviceEventQueue' requests for this coordinator so the
	 * coordinator is not overwhelmed by concurrent requests to
	 * service its event queue.
	 */
	private final TaskQueue coordinatorNotifications;
	
	/** A non-transactional task queue for ordering the execution
	 * of channel server notifications (join, leave, send, etc.) 
	 * send by this coordinator to one or more channel servers for
	 * the channel.
	 */
	private TaskQueue channelServerNotifications;

	/** A cache of channel membership events. */
	private final Queue<MembershipEventInfo> membershipEventsQueue;
	
	/** Constructs an instance with the specified {@code channelRefId}. */
	Coordinator(BigInteger channelRefId) {
	    this.channelRefId = channelRefId;
	    membershipEventsQueue = new LinkedList<MembershipEventInfo>();
	    coordinatorNotifications =
		transactionScheduler.createTaskQueue();
	}

	/**
	 * Adds the tasks in the specified {@code taskList} to this
	 * coordinator's channel server notification task queue. This
	 * method is invoked when a context is flushed during
	 * transaction commit.
	 */
	void addChannelNotificationTasks(List<KernelRunnable> taskList) {
	    assert Thread.holdsLock(contextList);
	    if (channelServerNotifications == null) {
		channelServerNotifications = taskScheduler.createTaskQueue();
	    }
	    for (KernelRunnable task : taskList) {
		channelServerNotifications.addTask(task, taskOwner);
	    }
	}

	/**
	 * Adds a task to service the event queue associated with this
	 * coordinator.<p>
	 *
	 * The service event queue request is enqueued in the coordinator
	 * notification task queue so that the requests can be performed
	 * serially, rather than concurrently.  If tasks to service a
	 * channel's event queue were processed concurrently, there would
	 * be many transaction conflicts because servicing a channel event
	 * accesses a single per-channel data structure (the channel's
	 * event queue).
	 */
	void addServiceEventQueueTask() {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "add task to service event queue, channelId:{0}",
		    HexDumper.toHexString(channelRefId));
	    }

	    coordinatorNotifications.addTask(
	        new AbstractKernelRunnable("ServiceEventQueue") {
		    public void run() {
			ChannelImpl.serviceEventQueue(channelRefId);
		    } }, taskOwner);
	}

	/**
	 * Caches the channel membership event with the specified {@code
	 * eventType}, {@code sessionRefId}, and {@code eventTimestamp}.
	 * The event will remain cached until its corresponding event
	 * queue reaches the specified {@code expirationTimestamp}. <p>
	 *
	 * Note: this method removes any expired events (those with an {@code
	 * expirationTimestamp} less than the specified {@code eventTimestamp}
	 * from the queue of cached events.
	 *
	 * @param	eventType an membership event type
	 * @param	sessionRefId a session ID, or {@code null}
	 * @param	eventTimestamp the event's timestamp
	 * @param	expirationTimestamp the event queue's timestamp
	 */
	void cacheMembershipEvent(MembershipEventType eventType,
				  BigInteger sessionRefId,
				  long eventTimestamp,
				  long expirationTimestamp)
	{
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "CACHE eventType:{0}, channelRefId:{1}, " +
		    "sessionRefId:{2}, eventTimestamp:{3}, " +
		    "expirationTimestamp:{4}", eventType,
		    HexDumper.toHexString(channelRefId),
		    HexDumper.toHexString(sessionRefId),
		    eventTimestamp, expirationTimestamp);
	    }
	    synchronized (membershipEventsQueue) {
		// remove events with expirationTimestamp <= eventTimestamp.
		removeExpiredMembershipEvents(eventTimestamp);
		// cache event.
		membershipEventsQueue.offer(
		    new MembershipEventInfo(eventType, sessionRefId,
					    eventTimestamp,
					    expirationTimestamp));
	    }
	}

	/**
	 * Returns {@code true} if the session with the specified {@code
	 * sessionRefId} is a member of this coordinator's channel, and
	 * {@code false} otherwise. This method is only invoked on the
	 * channel's coordinator node.  Membership is determined as
	 * follows:<p>
	 *
	 * The {@code isChannelMember} argument indicates whether the
	 * specified session was a member of the channel at the time the
	 * event being processed (that is now checking membership) was added
	 * to the event queue.<p>
	 *
	 * In order to determine channel membership, this method considers
	 * the initial known membership status, {@code isChannelMember}, and
	 * then checks the queue of cached events for join/leave requests
	 * for the specified session with timestamps less than or equal to
	 * the specified timestamp. <p>
	 *
	 * Note: this method removes any expired events (those with an {@code
	 * expirationTimestamp} less than the specified {@code eventTimestamp}
	 * from the queue of cached events.
	 *
	 * @param sessionRefId a session ID
	 * @param isChannelMember if {@code true}, the specified session is
	 *	  considered to be a member when current event was added to
	 *	  the event queue
	 * @param timestamp the timestamp of the currently executing event,
	 *        beyond which join/leave requests should not be considered
	 *        in determining channel membership
	 * @return  {@code true} if the session with the specified {@code
	 *          sessionRefId} is a member of the channel with the specified
	 *          {@code channelRefId}, and {@code false} otherwise
	 */
	boolean isChannelMember(BigInteger sessionRefId,
				boolean isChannelMember,
				long timestamp)
	{
	    synchronized (membershipEventsQueue) {
		// remove events with timestamp <= eventTimestamp.
		removeExpiredMembershipEvents(timestamp);
		for (MembershipEventInfo info : membershipEventsQueue) {
		    if (info.eventTimestamp > timestamp) {
			break;
		    }
		    
		    switch (info.eventType) {
			    
			case JOIN:
			    if (logger.isLoggable(Level.FINEST)) {
				logger.log(
				    Level.FINEST, "join:{0}",
				    HexDumper.toHexString(info.sessionRefId));
			    }
			    if (!isChannelMember &&
				info.sessionRefId.equals(sessionRefId))
			    {
				isChannelMember = true;
			    }
			    break;
			    
			case LEAVE:
			    if (logger.isLoggable(Level.FINEST)) {
				logger.log(
				    Level.FINEST, "leave:{0}",
				    HexDumper.toHexString(info.sessionRefId));
			    }
			    if (isChannelMember &&
				info.sessionRefId.equals(sessionRefId))
			    {
				isChannelMember = false;
			    }
			    break;

			default:
			    break;
		    }
		}
	    }
	
	    return isChannelMember;
	}

	/**
	 * Removes from the membership event queue, each cached channel
	 * membership event with an {@code expirationTimestamp} less than
	 * the specified {@code timestamp}.  This method must be invoked
	 * while synchronized on {@code membershipEventsQueue}.
	 */
	private void removeExpiredMembershipEvents(long timestamp) {
	    assert Thread.holdsLock(membershipEventsQueue);
	    
	    while (!membershipEventsQueue.isEmpty()) {
		MembershipEventInfo info = membershipEventsQueue.peek();
		if (info.expirationTimestamp >= timestamp) {
		    return;
		}
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"REMOVE eventType:{0}, sessionRefId:{1}, " +
			"eventTimestamp:{2}, expirationTimestamp:{3}",
			info.eventType,
			(info.sessionRefId != null ?
			 HexDumper.toHexString(info.sessionRefId) :
			 "null"), 
			info.eventTimestamp, info.expirationTimestamp);
		}
		membershipEventsQueue.poll();
	    }
	}
    }
}
