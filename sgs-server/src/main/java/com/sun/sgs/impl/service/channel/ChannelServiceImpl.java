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
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedMap;
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
import com.sun.sgs.service.ClientSessionDisconnectListener;
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
	PKG_NAME + "server.";
    
    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port. */
    private static final int DEFAULT_SERVER_PORT = 0;

    /** The property name for the maximum number of events to process in a
     * single transaction.
     */
    private static final String EVENTS_PER_TXN_PROPERTY =
	PKG_NAME + ".events.per.txn";

    /** The default events per transaction. */
    private static final int DEFAULT_EVENTS_PER_TXN = 1;
    
    /** The name of the write buffer size property. */
    private static final String WRITE_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".write.buffer.size";

    /** The default write buffer size: {@value #DEFAULT_WRITE_BUFFER_SIZE} */
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 128 * 1024;

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

    /** The ID for the local node. */
    private final long localNodeId;

    /** The cache of channel server proxies, keyed by the server's node ID. */
    private final ConcurrentHashMap<Long, ChannelServer>
	channelServerCache = new ConcurrentHashMap<Long, ChannelServer>();

    /** The cache of local channel membership lists, keyed by channel ID. */
    private final ConcurrentHashMap<BigInteger, Set<BigInteger>>
	localChannelMembersMap =
	    new ConcurrentHashMap<BigInteger, Set<BigInteger>>();

    /** The cache of local per-session channel sets, keyed by session ID. */
    private final ConcurrentHashMap<BigInteger, Set<BigInteger>>
	localPerSessionChannelsMap =
	    new ConcurrentHashMap<BigInteger, Set<BigInteger>>();

    /** The map of channel coordinator task queues, keyed by channel ID.
     * A coordinator task queue orders the delivery of incoming
     * 'serviceEventQueue' requests so that a given coordinator is not
     * overwhelmed by concurrent requests to service its event queue.
     * The tasks in these queues execute within a transaction.
     */
    private final ConcurrentHashMap<BigInteger, TaskQueue>
	coordinatorTaskQueues =
	    new ConcurrentHashMap<BigInteger, TaskQueue>();

    /** The map of channel task queues, keyed by channel ID.  A
     * channel's task queue orders the execution of tasks in which the
     * channel's coordinator sends notifications (join, leave, send,
     * refresh, etc.) to the channel servers for the channel.  The tasks
     * in these queues execute outside of a transaction.  This map must
     * be accessed while synchronized on {@code contextList}. A task
     * queue is added when the first committed context having to do with
     * the channel is flushed, and is removed when the channel is
     * closed.
     */
    private final Map<BigInteger, TaskQueue> channelTaskQueues =
	new HashMap<BigInteger, TaskQueue>();

    /** The write buffer size for new channels. */
    private final int writeBufferSize;
    
    /** The maximum number of channel events to service per transaction. */
    final int eventsPerTxn;

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
	    localNodeId =
		txnProxy.getService(DataService.class).getLocalNodeId();

            writeBufferSize = wrappedProps.getIntProperty(
                WRITE_BUFFER_SIZE_PROPERTY, DEFAULT_WRITE_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);
	    /*
	     * Get the property for controlling channel event processing.
	     */
	    eventsPerTxn = wrappedProps.getIntProperty(
		EVENTS_PER_TXN_PROPERTY, DEFAULT_EVENTS_PER_TXN,
		1, Integer.MAX_VALUE);
	    
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
	     * notification of client session disconnection.
	     */
	    watchdogService.addRecoveryListener(
		new ChannelServiceRecoveryListener());

	    watchdogService.addNodeListener(new ChannelServiceNodeListener());

            sessionService.registerSessionDisconnectListener(
                new ChannelSessionDisconnectListener());

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
	 * The service event queue request is enqueued in the given
	 * channel's coordinator task queue so that the requests can be
	 * performed serially, rather than concurrently.  If tasks to
	 * service a given channel's event queue were processed
	 * concurrently, there would be many transaction conflicts because
	 * servicing a channel event accesses a single per-channel data
	 * structure (the channel's event queue).
	 */
	public void serviceEventQueue(final BigInteger channelRefId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "serviceEventQueue channelId:{0}",
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
					  
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc}
	 *
	 * Reads the local membership list for the specified
	 * {@code channelRefId}, and updates the local membership cache
	 * for that channel.  If any join or leave notifications were
	 * missed, then send the appropriate channel join or channel leave
	 * message to the effected session(s).
	 */
	public void refresh(String name, final BigInteger channelRefId,
			    Delivery delivery)
        {
	    callStarted();
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "refreshing channelId:{0}",
			   HexDumper.toHexString(channelRefId.toByteArray()));
	    }
	    try {
		/*
		 * Read list of local members of the channel.
		 */
		final Set<BigInteger> newLocalMembers =
		    Collections.synchronizedSet(new HashSet<BigInteger>());
		try {
		    transactionScheduler.runTask(
			new AbstractKernelRunnable("getNewLocalMembers") {
			    public void run() {
				newLocalMembers.addAll(
				    ChannelImpl.getSessionRefIdsForNode(
					channelRefId, localNodeId));
			    } }, taskOwner);

		} catch (Exception e) {
		    // FIXME: what is the right thing to do here?
		    logger.logThrow(
 			Level.WARNING, e,
			"obtaining members of channel:{0} throws",
			HexDumper.toHexString(channelRefId.toByteArray()));
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "newLocalMembers for channel:{0}",
			HexDumper.toHexString(channelRefId.toByteArray()));
		    for (BigInteger sessionRefId : newLocalMembers) {
			logger.log(
			   Level.FINEST, "member:{0}",
			   HexDumper.toHexString(sessionRefId.toByteArray()));
		    }
		}

		/*
		 * Determine which join and leave events were missed and
		 * send protocol messages to clients accordingly.
		 */
		Set<BigInteger> oldLocalMembers =
		    localChannelMembersMap.put(channelRefId, newLocalMembers);
		Set<BigInteger> joiners = null;
		Set<BigInteger> leavers = null;
		if (oldLocalMembers == null) {
		    joiners = newLocalMembers;
		} else {
		    for (BigInteger sessionRefId : newLocalMembers) {
			if (oldLocalMembers.contains(sessionRefId)) {
			    oldLocalMembers.remove(sessionRefId);
			} else {
			    if (joiners == null) {
				joiners = new HashSet<BigInteger>();
			    }
			    joiners.add(sessionRefId);
			}
		    }
		    if (!oldLocalMembers.isEmpty()) {
			leavers = oldLocalMembers;
		    }
		}
		if (joiners != null) {
		    for (BigInteger sessionRefId : joiners) {
			SessionProtocol protocol =
			    sessionService.getSessionProtocol(sessionRefId);
			if (protocol != null) {
                            try {
                                protocol.channelJoin(name, channelRefId,
                                                     delivery);
                            } catch (IOException ioe) {
                                logger.logThrow(Level.WARNING, ioe,
                                                "channelJoin throws");
                            }
			}
		    }
		}
		if (leavers != null) {
		    for (BigInteger sessionRefId : leavers) {
			SessionProtocol protocol =
			    sessionService.getSessionProtocol(sessionRefId);
			if (protocol != null) {
                            try {
                                protocol.channelLeave(channelRefId);
                            } catch (IOException ioe) {
                                logger.logThrow(Level.WARNING, ioe,
                                                "channelLeave throws");
                            }
			}
		    }
		}

	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Adds the specified {@code sessionRefId} to the per-channel cache
	 * for the given channel's local member sessions, and sends a
	 * channel join message to the session with the corresponding
	 * {@code sessionId}.
	 */
	public void join(String name, BigInteger channelRefId,
			 Delivery delivery, BigInteger sessionRefId)

	{
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "join channelId:{0} sessionId:{1}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.toHexString(sessionRefId.toByteArray()));
		}

		// Update local channel membership cache.
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

		// Update per-session channel set cache.
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

		// Send channel join protocol message.
		SessionProtocol protocol =
		    sessionService.getSessionProtocol(sessionRefId);
		if (protocol != null) {
                    try {
                        protocol.channelJoin(name, channelRefId, delivery);
                    } catch (IOException ioe) {
                        logger.logThrow(Level.WARNING, ioe,
                                        "channelJoin throws");
                    }
		}

	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified {@code sessionRefId} from the per-channel
	 * cache for the given channel's local member sessions, and sends a
	 * channel leave message to the session with the corresponding
	 * {@code sessionRefId}.
	 */
	public void leave(BigInteger channelRefId, BigInteger sessionRefId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "leave channelId:{0} sessionId:{1}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.toHexString(sessionRefId.toByteArray()));
		}

		// Update local channel membership cache.
		Set<BigInteger> localMembers;
		localMembers = localChannelMembersMap.get(channelRefId);
		if (localMembers == null) {
		    return;
		}
		localMembers.remove(sessionRefId);

		// Update per-session channel set cache.
		Set<BigInteger> channelSet =
		    localPerSessionChannelsMap.get(sessionRefId);
		if (channelSet != null) {
		    channelSet.remove(channelRefId);
		}

		// Send channel leave protocol message.
		SessionProtocol protocol =
		    sessionService.getSessionProtocol(sessionRefId);
		if (protocol != null) {
                    try {
                        protocol.channelLeave(channelRefId);
                    } catch (IOException ioe) {
                        logger.logThrow(Level.WARNING, ioe,
                                        "channelLeave throws");
                    }
		}
		
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc}
	 *
	 * Removes the channel from the per-channel cache of local member
	 * sessions, and sends a channel leave message to the
	 * channel's local member sessions.
	 */
	public void leaveAll(BigInteger channelRefId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "leaveAll channelId:{0}",
			HexDumper.toHexString(channelRefId.toByteArray()));
		}
		Set<BigInteger> localMembers;
		localMembers = localChannelMembersMap.remove(channelRefId);
		if (localMembers != null) {
		    for (BigInteger sessionRefId : localMembers) {
			SessionProtocol protocol =
			    sessionService.getSessionProtocol(sessionRefId);
			if (protocol != null) {
                            try {
                                protocol.channelLeave(channelRefId);
                            } catch (IOException ioe) {
                                logger.logThrow(Level.WARNING, ioe,
                                                "channelLeave throws");
                            }
			}
		    }
		}
		
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
			Level.FINEST, "send channelId:{0} message:{1}",
			HexDumper.toHexString(channelRefId.toByteArray()),
			HexDumper.format(message, 0x50));
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
                        } catch (IOException ioe) {
                            logger.logThrow(Level.WARNING, ioe,
                                            "channelMessage throws");
                        }
		    }
		}

	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified channel from the per-channel cache of
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
     * Adds the specified {@code task} to the task list of the given {@code
     * channelRefId}.
     */
    void addChannelTask(BigInteger channelRefId, KernelRunnable task) {
	Context context = contextFactory.joinTransaction();
	context.addTask(channelRefId, task);
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
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    super(txn);
	}

	/**
	 * Adds the specified {@code task} to the task list of the given
	 * {@code channelRefId}.  If the transaction commits, the task will be
	 * added to the channel's tasks queue.
	 */
	public void addTask(BigInteger channelRefId, KernelRunnable task) {
	    List<KernelRunnable> taskList = internalTaskLists.get(channelRefId);
	    if (taskList == null) {
		taskList = new LinkedList<KernelRunnable>();
		internalTaskLists.put(channelRefId, taskList);
	    }
	    taskList.add(task);
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
	synchronized (contextList) {
	    channelTaskQueues.remove(channelRefId);
	}
    }
    /* -- Implement ClientSessionDisconnectListener -- */

    private final class ChannelSessionDisconnectListener
	implements ClientSessionDisconnectListener
    {
        /**
         * {@inheritDoc}
	 */
	public void disconnected(final BigInteger sessionRefId) {

	    Set<BigInteger> channelSet =
		localPerSessionChannelsMap.remove(sessionRefId);		

	    /*
	     * Schedule transactional task(s) to remove the
	     * disconnected session from each channel that it is
	     * currently a member of.
	     */
	    if (channelSet != null) {
		for (final BigInteger channelRefId : channelSet) {
		    transactionScheduler.scheduleTask(
			new AbstractKernelRunnable("RemoveSessionFromChannel") {
			    public void run() {
				ChannelImpl.removeSessionFromChannel(
				    localNodeId, sessionRefId, channelRefId);
			    }
			}, taskOwner);
		}
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
	return txnProxy.getService(DataService.class).getLocalNodeId();
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
     * and each node removes sessions, disconnected from the
     * failed node, from all the channels for which the node
     * coordinates.
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
		 * Schedule persistent task to remove the failed sessions of
		 * locally coordinated channels.
		 */
		transactionScheduler.runTask(
		    new AbstractKernelRunnable(
			"ScheduleRemoveFailedSessionsFromLocalChannelsTask")
		    {
			public void run() {
			    taskService.scheduleTask(
				new ChannelImpl.
				    RemoveFailedSessionsFromLocalChannelsTask(
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
}
