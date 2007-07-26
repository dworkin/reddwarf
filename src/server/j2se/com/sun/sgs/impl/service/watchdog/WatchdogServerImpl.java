/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
 * <ul>
 * <li> <i>Key:</i> {@code com.sun.sgs.app.name} <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the app name. <p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.port} <br>
 *	<i>Default:</i> {@code 44533} <br>
 *	Specifies the network port for the server.  This value must be greater 
 *	than or equal to {@code 0} and no greater than {@code 65535}.  If the
 *	value specified is {@code 0}, then an anonymous port will be chosen.
 *	The value chosen will be logged, and can also be accessed with the
 *	{@link #getPort getPort} method. <p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.renew.interval} <br>
 *	<i>Default:</i> {@code 1000} (one second)<br>
 *	Specifies the renew interval which is returned by the
 *	{@link #renewNode renewNode} method). The interval must be greater
 *	than or equal to  {@code 5} milliseconds and less than or equal to
 *	{@code 10000} milliseconds (10 seconds).<p>
 * </ul> <p>

 */
public class WatchdogServerImpl implements WatchdogServer, Service {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServerImpl.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    static final String WATCHDOG_SERVER_NAME = "WatchdogServer";

    /** The property name for the server port. */
    static final String PORT_PROPERTY = CLASSNAME + ".port";

    /** The default value of the server port. */
    static final int DEFAULT_PORT = 44533;

    /** The property name for the renew interval. */
    private static final String RENEW_INTERVAL_PROPERTY =
	CLASSNAME + ".renew.interval";

    /** The default value of the renew interval. */
    private static final int DEFAULT_RENEW_INTERVAL = 1000;

    /** The lower bound for the renew interval. */
    private static final int RENEW_INTERVAL_LOWER_BOUND = 5;

    /** The upper bound for the renew interval. */
    private static final int RENEW_INTERVAL_UPPER_BOUND = 10000;

    /** The name of the ID generator. */
    private static final String ID_GENERATOR_NAME =
	CLASSNAME + ".id.generator";

    /** The property name for the ID block size. */
    private static final String ID_BLOCK_SIZE_PROPERTY =
	CLASSNAME + ".id.block.size";

    /** The default ID block size for the ID generator. */
    private static final int DEFAULT_ID_BLOCK_SIZE = 256;
    
    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock. */
    private final Object lock = new Object();
    
    /** The server port. */
    private final int port;

    /** The renew interval. */
    private final long renewInterval;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    private NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The lock for notifying the {@code NotifyClientsThread}. */
    private final Object notifyClientsLock = new Object();

    /** The thread to notify clients of node status changes. */
    private final Thread notifyClientsThread = new NotifyClientsThread();

    /** The queue of nodes whose status has changed. */
    private final Queue<NodeImpl> statusChangedNodes =
	new ConcurrentLinkedQueue<NodeImpl>();

    /** The task owner. */
    private TaskOwner taskOwner;

    /** The data service. */
    private DataService dataService;

    /** The ID block size for the IdGenerator. */
    private int idBlockSize;
    
    /** The ID generator. */
    private IdGenerator idGenerator;

    /** The map of registered nodes, from node ID to node information. */
    private final ConcurrentMap<Long, NodeImpl> nodeMap =
	new ConcurrentHashMap<Long, NodeImpl>();

    /** The set of node information, sorted by renew expiration time. */
    private final SortedSet<NodeImpl> expirationSet = new TreeSet<NodeImpl>();

    /** The thread for checking node expiration times. */
    private final Thread checkExpirationThread = new CheckExpirationThread();

    /** The thread for notifying failed nodes on restart. */
    private Thread notifyClientsOnRestartThread;
    
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
	    PORT_PROPERTY, DEFAULT_PORT);
	if (requestedPort < 0 || requestedPort > 65535) {
	    throw new IllegalArgumentException(
		"The " + PORT_PROPERTY + " property value must be " +
		"greater than or equal to 0 and less than 65535: " +
		requestedPort);
	}
	renewInterval = wrappedProps.getLongProperty(
	    RENEW_INTERVAL_PROPERTY, DEFAULT_RENEW_INTERVAL);
	if (renewInterval < RENEW_INTERVAL_LOWER_BOUND ||
	    renewInterval > RENEW_INTERVAL_UPPER_BOUND)
	{
	    throw new IllegalArgumentException(
		"The " + RENEW_INTERVAL_PROPERTY + " property value must be " +
		"greater than or equal to " + RENEW_INTERVAL_LOWER_BOUND +
		" and less than or equal to " + RENEW_INTERVAL_UPPER_BOUND +
		": " + renewInterval);
	}
	idBlockSize = wrappedProps.getIntProperty(
 	    ID_BLOCK_SIZE_PROPERTY, DEFAULT_ID_BLOCK_SIZE);
	if (idBlockSize < IdGenerator.MIN_BLOCK_SIZE) {
	    throw new IllegalArgumentException(
		"The " + ID_BLOCK_SIZE_PROPERTY + " property value " +
		"must be greater than or equal to " +
		IdGenerator.MIN_BLOCK_SIZE);
	}
	
	exporter = new Exporter<WatchdogServer>(WatchdogServer.class);
	port = exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
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
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, taskOwner,
			registry.getComponent(TaskService.class));
		idGenerator =
		    new IdGenerator(ID_GENERATOR_NAME,
				    idBlockSize,
				    txnProxy,
				    nonDurableTaskScheduler);
		Collection<NodeImpl> failedNodes =
		    NodeImpl.markAllNodesFailed(dataService);
		notifyClientsOnRestartThread =
		    new NotifyClientsOnRestartThread(failedNodes);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure WatchdogServerImpl");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	synchronized (lock) {
	    if (shuttingDown) {
		throw new IllegalStateException("already shutting down");
	    }
	    shuttingDown = true;
	}
	exporter.unexport();
	checkExpirationThread.interrupt();
	notifyClientsThread.interrupt();
	return true;
    }
    
    /* -- Implement WatchdogServer -- */

    /**
     * {@inheritDoc}
     */
    public long[] registerNode(String hostname, WatchdogClient client) {
	checkState();
	
	// Get next node ID, and put new node in transient map.
	long nodeId;
	try {
	    nodeId = idGenerator.next();
	} catch (InterruptedException e) {
	    throw new NodeRegistrationFailedException(
		"interrupted while obtaining node ID");
	}
	final NodeImpl node = new NodeImpl(nodeId, hostname, client, 0);
	assert !nodeMap.containsKey(nodeId);
	nodeMap.put(nodeId,  node);
	
	// Persist node, and back out transient mapping on failure.
	try {
	    runTransactionally(new AbstractKernelRunnable() {
		public void run() {
		    node.putNode(dataService);
		}});
	} catch (Exception e) {
	    nodeMap.remove(nodeId);
	    throw new NodeRegistrationFailedException(
		"registration failed: " + nodeId, e);
	}

	// Put node in set, sorted by expiration.
	node.setExpiration(calculateExpiration());
	synchronized (expirationSet) {
	    expirationSet.add(node);
	}

	// Notify clients of new node.
	statusChangedNodes.add(node);
	synchronized (notifyClientsLock) {
	    notifyClientsLock.notifyAll();
	}
	
	return new long[]{nodeId, renewInterval};
    }

    /**
     * {@inheritDoc}
     */
    public boolean renewNode(long nodeId) throws IOException {
	checkState();
	NodeImpl node = nodeMap.get(nodeId);
	if (node == null || !node.isAlive()) {
	    return false;
	}

	synchronized (expirationSet) {
	    // update expiration time in sorted set.
	    expirationSet.remove(node);
	    node.setExpiration(calculateExpiration());
	    expirationSet.add(node);
	    return true;
	}
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isAlive(long nodeId) throws IOException {
	checkState();
	NodeImpl node = nodeMap.get(nodeId);
	if (node == null) {
	    return false;
	} else {
	    return node.isAlive();
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
     * Throws {@code IllegalStateException} if this service is not
     * configured or is shutting down.
     */
    private void checkState() {
	synchronized (lock) {
	    if (dataService == null) {
		throw new IllegalStateException("service not configured");
	    } else if (shuttingDown) {
		throw new IllegalStateException("service shutting down");
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

	    while (!shuttingDown()) {

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
			// TBD: when should node be removed from nodeMap?
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
	    node.setFailed();

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "Node failed: {0}", node.getId());
	    }

	    try {
		runTransactionally(new AbstractKernelRunnable() {
		    public void run() {
			node.updateNode(dataService);
		    }});
		
	    } catch (Exception e) {
		logger.logThrow(Level.SEVERE, e,
				"Updating node failed: {0}", node.getId());
	    }
	}
    }

    private final class NotifyClientsThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	NotifyClientsThread() {
	    super(CLASSNAME + "$NotifyClientsThread");
	    setDaemon(true);
	}
	
	public void run() {

	    while (!shuttingDown()) {
		while (statusChangedNodes.isEmpty()) {
		    synchronized (notifyClientsLock) {
			try {
			    notifyClientsLock.wait();
			} catch (InterruptedException e) {
			    
			}
		    }
		}
		
		if (shuttingDown()) {
		    return;
		}

		Iterator<NodeImpl> iter = statusChangedNodes.iterator();
		Collection<NodeImpl> changedNodes = new ArrayList<NodeImpl>();
		while (iter.hasNext()) {
		    changedNodes.add(iter.next());
		    iter.remove();
		}
		
		notifyClients(nodeMap.values(), changedNodes);
	    }
	}
    }

    private final class NotifyClientsOnRestartThread extends Thread {

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
	
	public void run() {
	    notifyClients(nodes,  nodes);
	}
    }

    private void notifyClients(Collection<NodeImpl> notifyNodes,
			       Collection<NodeImpl> changedNodes)
    {

	// Notify clients of status changes.
	for (NodeImpl notifyNode : notifyNodes) {
	    WatchdogClient client = notifyNode.getWatchdogClient();
	    try {
		client.nodeStatusChange(changedNodes);
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
	for (NodeImpl changedNode : changedNodes) {
	    if (! changedNode.isAlive()) {
		final NodeImpl node = changedNode;
		try {
		    runTransactionally(new AbstractKernelRunnable() {
			    public void run() {
				node.removeNode(dataService);
			    }});
		} catch (Exception e) {
		    logger.logThrow(
			Level.WARNING, e,
			"Removing failed node {0} throws",
			node.getId());
			    
		}
		nodeMap.remove(node.getId());
	    }
	}
    }
}
