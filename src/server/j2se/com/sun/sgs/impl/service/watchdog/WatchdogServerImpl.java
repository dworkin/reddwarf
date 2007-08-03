/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link WatchdogServer} implementation. <p>
 *
 * The {@link #WatchdogServerImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44533}
 *
 * <dd style="padding-top: .5em">Specifies the network port for the server.
 *	This value must be greater than or equal to {@code 0} and no greater
 *	than {@code 65535}.  If the value specified is {@code 0}, then an
 *	anonymous port will be chosen.	The value chosen will be logged, and
 *	can also be accessed with the {@link #getPort getPort} method. <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.renew.interval
 *	</b></code><br>
 *	<i>Default:</i> {@code 1000} (one second)<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the renew interval which is returned by the
 *	{@link #renewNode renewNode} method). The interval must be greater
 *	than or equal to  {@code 5} milliseconds and less than or equal to
 *	{@code 10000} milliseconds (10 seconds).<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.id.block.size
 *	</b></code><br>
 *	<i>Default:</i> {@code 256}<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the block size to use when reserving node IDs.  The value
 *	must be greater than {@code 8}.<p>
 * </dl> <p>

 */
public class WatchdogServerImpl implements WatchdogServer, Service {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServerImpl.class.getName();

    /** The prefix for server properties. */
    private static final String SERVER_PROPERTY_PREFIX =
	"com.sun.sgs.impl.service.watchdog.server";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(SERVER_PROPERTY_PREFIX));

    /** The server name in the registry. */
    static final String WATCHDOG_SERVER_NAME = "WatchdogServer";

    /** The property name for the server port. */
    static final String PORT_PROPERTY = SERVER_PROPERTY_PREFIX + ".port";

    /** The default value of the server port. */
    static final int DEFAULT_PORT = 44533;

    /** The property name for the renew interval. */
    private static final String RENEW_INTERVAL_PROPERTY =
	SERVER_PROPERTY_PREFIX + ".renew.interval";

    /** The default value of the renew interval. */
    private static final int DEFAULT_RENEW_INTERVAL = 1000;

    /** The lower bound for the renew interval. */
    private static final int RENEW_INTERVAL_LOWER_BOUND = 5;

    /** The upper bound for the renew interval. */
    private static final int RENEW_INTERVAL_UPPER_BOUND = 10000;

    /** The name of the ID generator. */
    private static final String ID_GENERATOR_NAME =
	SERVER_PROPERTY_PREFIX + ".id.generator";

    /** The property name for the ID block size. */
    private static final String ID_BLOCK_SIZE_PROPERTY =
	SERVER_PROPERTY_PREFIX + ".id.block.size";

    /** The default ID block size for the ID generator. */
    private static final int DEFAULT_ID_BLOCK_SIZE = 256;
    
    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock for {@code dataService}, {@code shuttingDown}, and
     * {@code callsInProgress} fields. */
    private final Object lock = new Object();
    
    /** The server port. */
    private final int port;

    /** The renew interval. */
    final long renewInterval;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The lock for notifying the {@code NotifyClientsThread}. */
    final Object notifyClientsLock = new Object();

    /** The thread to notify clients of node status changes. */
    private final Thread notifyClientsThread = new NotifyClientsThread();

    /** The queue of nodes whose status has changed. */
    final Queue<Node> statusChangedNodes =
	new ConcurrentLinkedQueue<Node>();

    /** The task owner. */
    private TaskOwner taskOwner;

    /** The data service. */
    DataService dataService;

    /** The ID block size for the IdGenerator. */
    private int idBlockSize;
    
    /** The ID generator. */
    private IdGenerator idGenerator;

    /** The map of registered nodes, from node ID to node information. */
    private final ConcurrentMap<Long, NodeImpl> nodeMap =
	new ConcurrentHashMap<Long, NodeImpl>();

    /** The set of node information, sorted by renew expiration time. */
    final SortedSet<NodeImpl> expirationSet = new TreeSet<NodeImpl>();

    /** The thread for checking node expiration times. */
    private final Thread checkExpirationThread = new CheckExpirationThread();

    /** The thread for notifying failed nodes on restart. */
    private Thread notifyClientsOnRestartThread;

    /** The count of calls in progress. */
    private int callsInProgress = 0;
    
    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;

    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServerImpl class documentation} for a list
     * of supported properties.
     *
     * @param	properties server properties
     * @param	systemRegistry the system registry
     *
     * @throws	IOException if there is a problem exporting the server
     */
    public WatchdogServerImpl(Properties properties,
			      ComponentRegistry systemRegistry)
	throws IOException
    {
	logger.log(Level.CONFIG, "Creating WatchdogServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	if (systemRegistry == null) {
	    throw new NullPointerException("null registry");
	}
	int requestedPort = wrappedProps.getIntProperty(
 	    PORT_PROPERTY, DEFAULT_PORT, 0, 65535);
	renewInterval = wrappedProps.getLongProperty(
	    RENEW_INTERVAL_PROPERTY, DEFAULT_RENEW_INTERVAL,
	    RENEW_INTERVAL_LOWER_BOUND, RENEW_INTERVAL_UPPER_BOUND);
	idBlockSize = wrappedProps.getIntProperty(
 	    ID_BLOCK_SIZE_PROPERTY, DEFAULT_ID_BLOCK_SIZE,
	    IdGenerator.MIN_BLOCK_SIZE, Integer.MAX_VALUE);
	
	exporter = new Exporter<WatchdogServer>(WatchdogServer.class);
	port = exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	// TBD:  use ResourceCoordinator.startTask?
	checkExpirationThread.start();
	notifyClientsThread.start();
    }

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring WatchdogServerImpl");
	}
	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    
	    synchronized (WatchdogServerImpl.class) {
		if (WatchdogServerImpl.txnProxy == null) {
		    WatchdogServerImpl.txnProxy = proxy;
		} else {
		    assert WatchdogServerImpl.txnProxy == proxy;
		}
	    }
	    
	    synchronized (lock) {
		if (dataService != null) {
		    throw new IllegalStateException("already configured");
		}
		dataService = registry.getComponent(DataService.class);
		taskOwner = proxy.getCurrentOwner();
		idGenerator =
		    new IdGenerator(ID_GENERATOR_NAME,
				    idBlockSize,
				    txnProxy,
				    taskScheduler);
		Collection<NodeImpl> failedNodes =
		    NodeImpl.markAllNodesFailed(dataService);
		notifyClientsOnRestartThread =
		    new NotifyClientsOnRestartThread(failedNodes);
	    }
	    
	} catch (RuntimeException e) {
	    logger.logThrow(
		Level.CONFIG, e,
		"Failed to configure WatchdogServerImpl");
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	synchronized (lock) {
	    if (shuttingDown) {
		return false;
	    }
	    shuttingDown = true;
	    while (callsInProgress > 0) {
		try {
		    lock.wait();
		} catch (InterruptedException e) {
		    return false;
		}
	    }
	}

	// Unexport server and stop threads expiration and notify threads.
	exporter.unexport();
	checkExpirationThread.interrupt();
	notifyClientsThread.interrupt();
	try {
	    checkExpirationThread.join();
	    notifyClientsThread.join();
	} catch (InterruptedException e) {
	    return false;
	}
	synchronized (expirationSet) {
	    expirationSet.clear();
	}
	statusChangedNodes.clear();

	final Collection<NodeImpl> failedNodes = nodeMap.values();
	try {
	    runTransactionally(new AbstractKernelRunnable() {
		public void run() {
		    for (NodeImpl node : failedNodes) {
			node.setFailed(dataService);
		    }
		}});
	} catch (Exception e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Failed to update failed nodes during shutdown, throws");
	    return false;
	}
	notifyClients(failedNodes, failedNodes);
	
	return true;
    }
    
    /* -- Implement WatchdogServer -- */

    /**
     * {@inheritDoc}
     */
    public long[] registerNode(String hostname, WatchdogClient client)
	throws NodeRegistrationFailedException
    {
	callStarted();

	try {
	    if (hostname == null) {
		throw new IllegalArgumentException("null hostname");
	    } else if (client == null) {
		throw new IllegalArgumentException("null client");
	    }
	
	    // Get next node ID, and put new node in transient map.
	    long nodeId;
	    try {
		nodeId = idGenerator.next();
	    } catch (Exception e) {
		logger.logThrow(
		    Level.WARNING, e,
		    "Failed to obtain node ID for host:{0}, throws", hostname);
		throw new NodeRegistrationFailedException(
		    "Exception occurred while obtaining node ID", e);
	    }
	    final NodeImpl node = new NodeImpl(nodeId, hostname, client);
	    assert ! nodeMap.containsKey(nodeId);
	
	    // Persist node
	    try {
		runTransactionally(new AbstractKernelRunnable() {
		    public void run() {
			node.putNode(dataService);
		    }});
	    } catch (Exception e) {
		throw new NodeRegistrationFailedException(
		    "registration failed: " + nodeId, e);
	    }

	    // Put node in set, sorted by expiration.
	    node.setExpiration(calculateExpiration());
	    nodeMap.put(nodeId,  node);

	    // TBD: use a ConcurrentSkipListSet?
	    synchronized (expirationSet) {
		expirationSet.add(node);
	    }

	    // Notify clients of new node.
	    statusChangedNodes.add(node);
	    synchronized (notifyClientsLock) {
		notifyClientsLock.notifyAll();
	    }
	
	    return new long[]{nodeId, renewInterval};
	    
	} finally {
	    callFinished();
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean renewNode(long nodeId) throws IOException {
	callStarted();

	try {
	    NodeImpl node = nodeMap.get(nodeId);
	    if (node == null || ! node.isAlive() || node.isExpired()) {
		return false;
	    }

	    synchronized (expirationSet) {
		// update expiration time in sorted set.
		expirationSet.remove(node);
		node.setExpiration(calculateExpiration());
		expirationSet.add(node);
		return true;
	    }

	} finally {
	    callFinished();
	}
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isAlive(long nodeId) throws IOException {
	callStarted();

	try {
	    NodeImpl node = nodeMap.get(nodeId);
	    if (node == null || node.isExpired()) {
		return false;
	    } else {
		return node.isAlive();
	    }
	} finally {
	    callFinished();
	}
    }

    /* -- other methods -- */

    /**
     * Returns the port being used for this server.
     *
     * @return	the port
     */
    public int getPort() {
	return port;
    }
    
    /**
     * Resets the server to an uninitialized state so that configure
     * can be reinvoked.  This method is invoked by the {@link
     * WatchdogServiceImpl} if its {@link
     * WatchdogServiceImpl#configure configure} method aborts.
     */
    void abortConfigure() {
	dataService = null;
    }

    /**
     * Performs actions necessary when {@link #configure configure}
     * method commits.  This method is invoked by the {@link
     * WatchdogServiceImpl} if its {@link
     * WatchdogServiceImpl#configure configure} method commits.
     */
    void commitConfigure() {
	notifyClientsOnRestartThread.start();
    }

    /**
     * Runs the specified {@code task} within a transaction.
     *
     * @param	task a task
     * @throws	Exception if there is a problem running the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
	try {
	    taskScheduler.runTask(new TransactionRunner(task), taskOwner, true);
	} catch (Exception e) {
	    logger.logThrow(Level.WARNING, e, "task failed: {0}", task);
	    throw e;
	}
    }

    /**
     * Increments the number of calls in progress.  This method should
     * be invoked by remote methods to both increment in progress call
     * count and to check the state of this server.  When the call has
     * completed processing, the remote method should invoke {@link
     * #callFinished callFinished} before returning.
     *
     * @throws	IllegalStateException if this service is not configured
     *		or is shutting down
     */
    private void callStarted() {
	synchronized (lock) {
	    if (dataService == null) {
		throw new IllegalStateException("service not configured");
	    } else if (shuttingDown) {
		throw new IllegalStateException("service shutting down");
	    }
	    callsInProgress++;
	}
    }

    /**
     * Decrements the in progress call count, and if this server is
     * shutting down and the count reaches 0, then notify the waiting
     * shutdown thread that it is safe to continue.  A remote method
     * should invoke this method when it has completed processing.
     */
    private void callFinished() {
	synchronized (lock) {
	    callsInProgress--;
	    if (shuttingDown && callsInProgress == 0) {
		lock.notifyAll();
	    }
	}
    }
    
    /**
     * Returns {@code true} if this server is shutting down.
     */
    private boolean shuttingDown() {
	synchronized (lock) {
	    return shuttingDown;
	}
    }

    /**
     * Returns an expiration time based on the current time.
     */
    private long calculateExpiration() {
	return System.currentTimeMillis() + renewInterval;
    }

    /**
     * This thread checks the node map, sorted by expiration times to
     * determine if any nodes have failed and updates their state
     * accordingly.
     */
    private final class CheckExpirationThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	CheckExpirationThread() {
	    super(CLASSNAME + "$CheckExpirationThread");
	    setDaemon(true);
	}

	/**
	 * Wakes up periodically to detect failed nodes and update
	 * state accordingly.
	 */
	public void run() {

	    while (! shuttingDown()) {

		long now = System.currentTimeMillis();
		boolean notifyClients = false;
		synchronized (expirationSet) {
		    while (! expirationSet.isEmpty()) {
			NodeImpl node = expirationSet.first();
			if (node.getExpiration() > now) {
			    break;
			}
			setFailed(node);
			statusChangedNodes.add(node);
			notifyClients = true;
			expirationSet.remove(node);
		    }
		}
		
		if (notifyClients) {
		    synchronized (notifyClientsLock) {
			notifyClientsLock.notifyAll();
		    }
		}

		long sleepTime =
		    expirationSet.isEmpty() ?
		    renewInterval :
		    expirationSet.first().getExpiration() - now;
		try {
		    Thread.currentThread().sleep(sleepTime);
		} catch (InterruptedException e) {
		}
	    }
	}

	/**
	 * Sets the node's status to failed, and persists the node
	 * status change.
	 */
	private void setFailed(final NodeImpl node) {

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "Node failed: {0}", node.getId());
	    }

	    try {
		runTransactionally(new AbstractKernelRunnable() {
		    public void run() {
			node.setFailed(dataService);
		    }});
		
	    } catch (Exception e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "Updating node failed: {0}", node.getId());
	    }
	}
    }

    /**
     * This thread informs all currently known clients of node status
     * changes (either nodes started or failed) as they occur.  This
     * thread is notified by {@link #registerNode registerNode) when
     * nodes are registered, or by the {@code CheckExpirationThread}
     * when nodes fail to renew before their expiration time has
     * lapsed.
     */
    private final class NotifyClientsThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	NotifyClientsThread() {
	    super(CLASSNAME + "$NotifyClientsThread");
	    setDaemon(true);
	}

	/** {@inheritDoc} */
	public void run() {

	    while (! shuttingDown()) {
		while (statusChangedNodes.isEmpty()) {
		    synchronized (notifyClientsLock) {
			try {
			    notifyClientsLock.wait();
			} catch (InterruptedException e) {
			    return;
			}
		    }
		}

		// TBD: possibly wait for more updates to batch?
		
		Iterator<Node> iter = statusChangedNodes.iterator();
		Collection<Node> changedNodes = new ArrayList<Node>();
		while (iter.hasNext()) {
		    changedNodes.add(iter.next());
		    iter.remove();
		}
		
		notifyClients(nodeMap.values(), changedNodes);
	    }
	}
    }

    /**
     * This thread is created when the server (re)starts, and notifies
     * all previous clients (whose node information is present in the
     * data service) that all previous nodes have failed.
     */
    private final class NotifyClientsOnRestartThread extends Thread {

	/** A collection of nodes. */
	private final Collection<NodeImpl> nodes;
	
	/**
	 * Constructs an instance of this class with the specified
	 * collection of {@code nodes}, and sets this thread as a
	 * daemon thread.
	 *
	 * @param  nodes the collection of nodes to notify about each other
	 */
	NotifyClientsOnRestartThread(Collection<NodeImpl> nodes) {
	    super(CLASSNAME + "$NotifyClientsOnRestartThread");
	    setDaemon(true);
	    this.nodes = nodes;
	}

	/** {@inheritDoc} */
	public void run() {
	    notifyClients(nodes,  nodes);
	}
    }

    /**
     * Notifies the {@code WatchdogClient} of each node in the
     * collection of {@code notifyNodes} of the node status changes in
     * {@code changedNodes}.  After all clients are notified, all
     * failed nodes in {@code changedNodes} are removed from transient
     * and persistent storage.
     *
     * @param	notifyNodes nodes whose clients should be notified
     * @param	changedNodes nodes with status changes
     */
    private void notifyClients(Collection<NodeImpl> notifyNodes,
			       Collection<? extends Node> changedNodes)
    {
	// Assemble node information into arrays.
	int size = changedNodes.size();
	long[] ids = new long[size];
	String[] hosts = new String[size];
	boolean[] status = new boolean[size];

	int i = 0;
	for (Node changedNode : changedNodes) {
	    ids[i] = changedNode.getId();
	    hosts[i] = changedNode.getHostName();
	    status[i] = changedNode.isAlive();
	    i++;
	}
	
	// Notify clients of status changes.
	for (NodeImpl notifyNode : notifyNodes) {
	    WatchdogClient client = notifyNode.getWatchdogClient();
	    try {
		client.nodeStatusChanges(ids, hosts, status);
	    } catch (Exception e) {
		// TBD: Should it try harder to notify the client in
		// the non-restart case?  In the restart case, the
		// client may have failed too.
		logger.logThrow(
		    Level.WARNING, e,
		    "Notifying {0} of node status changes failed:",
		    notifyNode.getId());
	    }
	}

	// Remove failed nodes from transient map and persistent store.
	for (int n = 0; n < ids.length; n++) {
	    if (status[n] == false) {
		final long nodeId = ids[n];
		try {
		    runTransactionally(new AbstractKernelRunnable() {
			    public void run() {
				NodeImpl.removeNode(dataService,  nodeId);
			    }});
		} catch (Exception e) {
		    logger.logThrow(
			Level.WARNING, e,
			"Removing failed node {0} throws", nodeId);
			    
		}
		nodeMap.remove(nodeId);
	    }
	}
    }
}
