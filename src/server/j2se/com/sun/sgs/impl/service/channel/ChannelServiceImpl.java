/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.NonDurableTaskQueue;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.ClientSessionDisconnectListener;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.WatchdogService;
import java.math.BigInteger;
import java.nio.BufferOverflowException;
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

/**
 * ChannelService implementation. <p>
 * 
 * <p>The {@link #ChannelServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property. <p>
 *
 * <p>TODO: add summary comment about how the implementation works.
 *
 * <p>TODO: service bindings should be versioned, and old bindings should be
 * converted to the new scheme (or removed if applicable).
 */
public class ChannelServiceImpl
    extends AbstractService implements ChannelManager
{
    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port. */
    private static final int DEFAULT_SERVER_PORT = 0;
    
    /** The transaction context map. */
    private static TransactionContextMap<Context> contextMap = null;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** List of contexts that have been prepared (non-readonly) or committed. */
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

    /** The lists of channel tasks, keyed by channel ID. */
    private final Map<BigInteger, List<Runnable>> channelTasksMap =
	Collections.synchronizedMap(new HashMap<BigInteger, List<Runnable>>());

    /** The thread to process tasks in the {@code channelTasksMap}. */
    private final Thread taskHandlerThread = new TaskHandlerThread();

    /** The lock for notifying the {@code taskHandlerThread} . */
    private final Object taskHandlerLock = new Object();

    /** The local channel membership lists, keyed by channel ID. */
    private final ConcurrentHashMap<BigInteger, Set<BigInteger>>
	localChannelMembersMap =
	    new ConcurrentHashMap<BigInteger, Set<BigInteger>>();

    /** The map of channel coordinator task queues, keyed by channel ID. */
    private final ConcurrentHashMap<BigInteger, NonDurableTaskQueue>
	coordinatorTaskQueues =
	    new ConcurrentHashMap<BigInteger, NonDurableTaskQueue>();

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
	    }
	    contextFactory = new ContextFactory(contextMap);
	    WatchdogService watchdogService =
		txnProxy.getService(WatchdogService.class);
	    sessionService = txnProxy.getService(ClientSessionService.class);
	    localNodeId = watchdogService.getLocalNodeId();
	    
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
	     * Store the ChannelServer proxy in the data store.
	     */
	    taskScheduler.runTransactionalTask(
		new AbstractKernelRunnable() {
		    public void run() {
			dataService.setServiceBinding(
			    getChannelServerKey(localNodeId),
			    new ChannelServerWrapper(serverProxy));
		    }},
		taskOwner);

	    /*
	     * Add listeners for handling recovery and for receiving
	     * notification of client session disconnection.
	     */
	    watchdogService.addRecoveryListener(
		new ChannelServiceRecoveryListener());

            sessionService.registerSessionDisconnectListener(
                new ChannelSessionDisconnectListener());

            taskHandlerThread.start();

	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
	    }
	    throw e;
	}
    }
 
    /* -- Implement AbstractService methods -- */

    /** {@inheritDoc} */
    protected void doReady() {
    }

    /** {@inheritDoc} */
    protected void doShutdown() {
	logger.log(Level.FINEST, "shutdown");
	
	try {
	    exporter.unexport();
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "unexport server throws");
	    // swallow exception
	}

	taskHandlerThread.interrupt();
    }
    
    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(Delivery delivery) {
	try {
	    Channel channel = ChannelImpl.newInstance(delivery);
	    return channel;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "createChannel:{0} throws");
	    throw e;
	}
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
	public void serviceEventQueue(final byte[] channelId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "serviceEventQueue channelId:{0}",
			       HexDumper.toHexString(channelId));
		}

		BigInteger channelIdRef = new BigInteger(1, channelId);
		NonDurableTaskQueue taskQueue =
		    coordinatorTaskQueues.get(channelIdRef);
		if (taskQueue == null) {
		    NonDurableTaskQueue newTaskQueue =
			new NonDurableTaskQueue(txnProxy, taskScheduler,
						taskOwner);
		    taskQueue = coordinatorTaskQueues.
			putIfAbsent(channelIdRef, newTaskQueue);
		    if (taskQueue == null) {
			taskQueue = newTaskQueue;
		    }
		}
		taskQueue.addTask(new AbstractKernelRunnable() {
		    public void run() {
			Context context = contextFactory.joinTransaction();
			ChannelImpl.serviceEventQueue(channelId, context);
		    }});
					  
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc}
	 *
	 * TBD: This method is not yet implemented, but will be when
	 * channel coordinator recovery is implemented.
	 */
	public void refresh(byte[] channelId) {
	    callStarted();
	    try {
		throw new AssertionError("not implemented");
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Adds the specified {@code sessionId} to the per-channel cache
	 * for the given channel's local member sessions.
	 */
	public void join(byte[] channelId, byte[] sessionId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "join channelId:{0} sessionId:{1}",
			       HexDumper.toHexString(channelId),
			       HexDumper.toHexString(sessionId));
		}
		BigInteger channelRefId = new BigInteger(1, channelId);
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
		localMembers.add(new BigInteger(1, sessionId));

	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc}
	 *
	 * Removes the specified {@code sessionId} from the per-channel
	 * cache for the given channel's local member sessions.
	 */
	public void leave(byte[] channelId, byte[] sessionId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST, "leave channelId:{0} sessionId:{1}",
			HexDumper.toHexString(channelId),
			HexDumper.toHexString(sessionId));
		}
		BigInteger channelRefId = new BigInteger(1, channelId);
		Set<BigInteger> localMembers;
		localMembers = localChannelMembersMap.get(channelRefId);
		if (localMembers == null) {
		    return;
		}
		localMembers.remove(new BigInteger(1, sessionId));
		
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc}
	 *
	 * Removes all session IDs from the per-channel cache for the given
	 * channel's local member sessions.
	 */
	public void leaveAll(byte[] channelId) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "leaveAll channelId:{0}",
			       HexDumper.toHexString(channelId));
		}
		BigInteger channelRefId = new BigInteger(1, channelId);
		Set<BigInteger> localMembers;
		localMembers = localChannelMembersMap.get(channelRefId);
		if (localMembers == null) {
		    return;
		}
		// TBD: remove the entry instead of clearing the membership?
		localMembers.clear();
		
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
	public void send(byte[] channelId, byte[] message) {
	    callStarted();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "send channelId:{0} message:{1}",
			       HexDumper.toHexString(channelId),
			       HexDumper.format(message));
		}
		/*
		 * TBD: (optimization) this should enqueue the send
		 * request and return immediately so that the
		 * coordinator can receive the acknowledgment and
		 * continue processing of the event queue.  Right now,
		 * process the send request inline here.
		 */
		BigInteger channelRefId = new BigInteger(1, channelId);
		Set<BigInteger> localMembers =
		    localChannelMembersMap.get(channelRefId);
		if (localMembers == null) {
		    // TBD: there should be local channel members.
		    // What error should be reported here?
		    return;
		}

		for (BigInteger sessionRefId : localMembers) {
		    sessionService.sendProtocolMessageNonTransactional(
 			sessionRefId, ByteBuffer.wrap(message), Delivery.RELIABLE);
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
	public void close(byte[] channelId) {
	    callStarted();
	    try {
		BigInteger channelRefId = new BigInteger(1, channelId);
		localChannelMembersMap.remove(channelRefId);
		coordinatorTaskQueues.remove(channelRefId);

	    } finally {
		callFinished();
	    }
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

	private final Map<BigInteger, List<Runnable>> internalTaskLists =
	    new HashMap<BigInteger, List<Runnable>>();

	/**
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    super(txn);
	}

	/**
	 * Adds the specified {@code task} to the task list of the given
	 * {@code channelId}.  If the transaction commits, the task will be
	 * added to the task handler's per-channel map of tasks to service.
	 * The tasks are serviced by the TaskHandlerThread.
	 */
	public void addChannelTask(BigInteger channelId, Runnable task) {
	    List<Runnable> taskList = internalTaskLists.get(channelId);
	    if (taskList == null) {
		taskList = new LinkedList<Runnable>();
		internalTaskLists.put(channelId, taskList);
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
	    if (! readOnly) {
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
	    if (isCommitted) {
		for (BigInteger channelId : internalTaskLists.keySet()) {
		    addChannelTasks(
			channelId, internalTaskLists.get(channelId));
		}
		synchronized (taskHandlerLock) {
		    taskHandlerLock.notifyAll();
		}
		return true;
	    } else {
		return false;
	    }
	}
    }

    /**
     * Adds the tasks in the specified {@code taskList} to the
     * task handler's per-channel map of tasks to process.  The tasks will
     * be serviced by the TaskHandlerThread.  This method is invoked when a
     * context is flushed during transaction commit.
     */
    private void addChannelTasks(
	BigInteger channelId, List<Runnable> taskList)
	
    {
	synchronized (channelTasksMap) {
	    List<Runnable> prevTaskList = channelTasksMap.get(channelId);
	    if (prevTaskList != null) {
		prevTaskList.addAll(taskList);
	    } else {
		channelTasksMap.put(channelId, taskList);
	    }
	}
    }

    /* -- Implement TaskHandlerThread -- */

    /**
     * Thread for processing channel tasks. A channel task is enqueued
     * when a channel processes a channel event.  A typical channel
     * task sends a message to one or more channel servers to notify
     * the server(s) of a channel state change or that a message needs
     * to be sent to the channel members connected to that channel
     * server's node.
     *
     * Currently, the table is serviced by a single thread, but in the
     * future, it could be serviced by more than one thread.
     */
    private final class TaskHandlerThread extends Thread {
	
	/** Constructs an instance of this class as a daemon thread. */
	TaskHandlerThread() {
	    super(CLASSNAME + "$TaskHandlerThread");
	    setDaemon(true);
	}

	/** {@inheritDoc} */
	public void run() {

	    while (! shuttingDown()) {
		/*
		 * Wait for channel tasks to show up.
		 */
		synchronized (taskHandlerLock) {
		    while (channelTasksMap.isEmpty()) {
			try {
			    taskHandlerLock.wait();
			} catch (InterruptedException e) {
			    return;
			}
		    }
		}

		/*
		 * Get snapshot of current channel IDs with tasks.
		 */
		Set<BigInteger> channelIds = new HashSet<BigInteger>();
		synchronized (channelTasksMap) {
		    channelIds.addAll(channelTasksMap.keySet());
		}
		
		/*
		 * Process tasks for each channel, removing task list
		 * from the table before processing.
		 */
		for (BigInteger channelId : channelIds) {
		    List<Runnable> taskList =
			channelTasksMap.remove(channelId);
		    if (taskList == null) continue;
		    for (Runnable task : taskList) {
			try {
			    task.run();
			} catch (Exception e) {
			    logger.logThrow(
				Level.WARNING, e, "processing task:{0} throws",
				task);
			    // TBD: abandon the rest of the tasks here?
			}
		    }
		}
	    }
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
	    /*
	     * Schedule a transactional task to remove the
	     * disconnected session from all channels that it is
	     * currently a member of.
	     */
	    taskScheduler.scheduleTask(
 		 new TransactionRunner(
		    new AbstractKernelRunnable() {
			public void run() {
			    ChannelImpl.removeSessionFromAllChannels(
				localNodeId, sessionRefId.toByteArray());
			    }
		    }),
		 taskOwner);
	}
    }

    /* -- Other methods and classes -- */
    
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
     * Returns the local node ID.
     */
    static long getLocalNodeId() {
	return txnProxy.getService(WatchdogService.class).getLocalNodeId();
    }

    /**
     * Returns the key for accessing the {@code ChannelServer}
     * instance (which is wrapped in a {@code ChannelServerWrapper})
     * for the specified {@code nodeId}.
     */
    private static String getChannelServerKey(long nodeId) {
	return PKG_NAME + ".server." + nodeId;
    }

    /**
     * Returns the {@code ChannelServer} for the given {@code nodeId}.
     */
    static ChannelServer getChannelServer(long nodeId) {
	// TBD: if the nodeId is the local nodeId, then this method
	// should return a reference to the local channel server impl,
	// rather than the channel server proxy. -- ann (12/11/07)
	String channelServerKey =
	    ChannelServiceImpl.getChannelServerKey(nodeId);
	try {
	    return getDataService().getServiceBinding(
		channelServerKey, ChannelServerWrapper.class).get();
	} catch (NameNotBoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"ChannelServerWrapper binding:{0} doesn't exist",
		channelServerKey);
	    throw e;
	} catch (ObjectNotFoundException e) {
	    logger.logThrow(
		Level.SEVERE, e,
		"ChannelServerWrapper binding:{0} exists, " +
		"but object removed", channelServerKey);
	    throw e;
	}
    }

    /**
     * Removes channel server proxy and binding for the specified node.
     */
    private void removeChannelServerProxy(long nodeId) {
	String channelServerKey = getChannelServerKey(nodeId);
	try {
	    ChannelServerWrapper proxyWrapper =
		dataService.getServiceBinding(
		    channelServerKey, ChannelServerWrapper.class);
	    dataService.removeObject(proxyWrapper);
	} catch (NameNotBoundException e) {
	    // already removed
	    return;
	} catch (ObjectNotFoundException e) {
	}
	dataService.removeServiceBinding(channelServerKey);
    }

    /**
     * The {@code RecoveryListener} for handling requests to recover
     * for a failed {@code ChannelService}.
     *
     * TBD: recovery also needs to re-assign channel coordinators assigned
     * to the failed node.
     */
    private class ChannelServiceRecoveryListener
	implements RecoveryListener
    {
	/** {@inheritDoc}
	 *
	 * TBD: Recovery (due to being possibly-lengthy) should not be
	 * performed in this remote method.  Recovery operations should
	 * be performed in a separate thread.
	 *
	 * TODO: use persistent tasks instead?
	 */
	public void recover(Node node, RecoveryCompleteFuture future) {
	    final long nodeId = node.getId();
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Node:{0} recovering for node:{0}",
			       localNodeId, nodeId);
		}
		/*
		 * For each session on the failed node, remove the
		 * given session from all channels it is a member of.
		 *
		 * TODO:  This transactional task may take too long.  It
		 * probably should be broken up into more tasks.
		 */
		GetNodeSessionIdsTask task = new GetNodeSessionIdsTask(nodeId);
		taskScheduler.runTransactionalTask(task, taskOwner);
		
		for (final byte[] sessionId : task.getSessionIds()) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "Removing session:{0} from all channels",
			    HexDumper.toHexString(sessionId));
		    }

		    /*
		     * TODO:  This transactional task may take too long.  It
		     * probably should be broken up into more tasks.
		     */
		    taskScheduler.runTransactionalTask(
			new AbstractKernelRunnable() {
			    public void run() {
				ChannelImpl.removeSessionFromAllChannels(
				    nodeId, sessionId);
			    }
			},
			taskOwner);
		}
		/*
		 * Remove binding to channel server proxy for failed
		 * node, and remove proxy's wrapper.
		 */
		taskScheduler.runTransactionalTask(
		    new AbstractKernelRunnable() {
			public void run() {
			    removeChannelServerProxy(nodeId);
			}
		    },
		    taskOwner);
		
		future.done();

	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "Recovering for failed node:{0} throws", nodeId);
		// TBD: what should it do if it can't recover?
	    }
	}
    }

    /**
     * Task to obtain all sessions on a given node that are members
     * of any channel.  This is used by the {@code
     * ChannelServiceRecoveryListener} for obtaining all failed
     * sessions that need to be removed from channels.
     */
    private class GetNodeSessionIdsTask extends AbstractKernelRunnable {

	private final long nodeId;
	private Set<byte[]> sessionIds = new HashSet<byte[]>();
	
	GetNodeSessionIdsTask(long nodeId) {
	    this.nodeId = nodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    Iterator<byte[]> iter =
		ChannelImpl.getSessionIdsAnyChannel(dataService, nodeId);
	    while (iter.hasNext()) {
		sessionIds.add(iter.next());
	    }
	}

	Set<byte[]> getSessionIds() {
	    return sessionIds;
	}
    }
}
