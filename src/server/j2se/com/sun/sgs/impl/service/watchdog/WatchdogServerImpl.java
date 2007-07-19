/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
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
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.ping.interval} <br>
 *	<i>Default:</i> {@code 1000} (one second)<br>
 *	Specifies the ping interval which is returned by the {@link #ping ping}
 *	method). The interval must be greater than or equal to  {@code 5}
 *	milliseconds and less than or equal to {@code 10000} milliseconds
 *	(10 seconds).<p>
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

    /** The property name for the ping interval. */
    private static final String PING_INTERVAL_PROPERTY =
	CLASSNAME + ".ping.interval";

    /** The default value of the ping interval. */
    private static final int DEFAULT_PING_INTERVAL = 1000;

    /** The lower bound for the ping interval. */
    private static final int PING_INTERVAL_LOWER_BOUND = 5;

    /** The upper bound for the ping interval. */
    private static final int PING_INTERVAL_UPPER_BOUND = 10000;

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock. */
    private final Object lock = new Object();
    
    /** The server port. */
    private final int port;

    /** The ping interval. */
    private final long pingInterval;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task owner. */
    private TaskOwner taskOwner;

    /** The data service. */
    private DataService dataService;

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;

    /** The map of registered nodes, from node ID to node information. */
    private final Map<Long, NodeImpl> nodeMap = new HashMap<Long, NodeImpl>();

    /** The set of node information, sorted by ping expiration time. */
    private final SortedSet<NodeImpl> expirationSet = new TreeSet<NodeImpl>();

    /** The thread for checking node expiration times. */
    private final Thread checkExpirationThread = new CheckExpirationThread();
    
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
	pingInterval = wrappedProps.getLongProperty(
	    PING_INTERVAL_PROPERTY, DEFAULT_PING_INTERVAL);
	if (pingInterval < PING_INTERVAL_LOWER_BOUND ||
	    pingInterval > PING_INTERVAL_UPPER_BOUND)
	{
	    throw new IllegalArgumentException(
		"The " + PING_INTERVAL_PROPERTY + " property value must be " +
		"greater than or equal to " + PING_INTERVAL_LOWER_BOUND +
		" and less than or equal to " + PING_INTERVAL_UPPER_BOUND +
		": " + pingInterval);
	}
	exporter = new Exporter<WatchdogServer>();
	port = exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	checkExpirationThread.start();
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
	return true;
    }
    
    /* -- Implement WatchdogServer -- */

    /**
     * {@inheritDoc}
     *
     * <p>This implementation assumes that it will not receive a ping
     * for the same node while it is registering that node.
     */
    public long registerNode(long nodeId, String hostname) throws IOException {
	checkState();
	NodeImpl node;
	synchronized (nodeMap) {
	    node = nodeMap.get(nodeId);
	    if (node != null) {
		throw new NodeExistsException(
		    "node already registered: " + nodeId);
	    }
	    node = new NodeImpl(nodeId, hostname, true, calculateExpiration());
	    nodeMap.put(nodeId, node);
	}
	synchronized (expirationSet) {
	    expirationSet.add(node);
	}

	/*
	 * Persist node, and back out registration if storing the node fails.
	 */
	try {
	    final NodeImpl newNode = node;
	    runTransactionally(new AbstractKernelRunnable() {
		public void run() {
		    newNode.putNode(dataService);
		}});
	} catch (Exception e) {
	    synchronized (nodeMap) {
		nodeMap.remove(nodeId);
	    }
	    synchronized (expirationSet) {
		expirationSet.remove(node);
	    }
	    throw new NodeRegistrationFailedException(
		"registration failed: " + nodeId, e);
	}
	
	return pingInterval;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation assumes that it will not receive a ping
     * for the same node while it is processing a ping for that node.
     */
    public boolean ping(long nodeId) throws IOException {
	checkState();
	NodeImpl node;
	synchronized (nodeMap) {
	    node = nodeMap.get(nodeId);
	    if (node == null) {
		return false;
	    }
	}

	synchronized (expirationSet) {
	    if (!node.isAlive()) {
		return false;
	    } else {
		// update ping expiration time in sorted set...
		expirationSet.remove(node);
		node.setExpiration(calculateExpiration());
		expirationSet.add(node);
	    }
	    return true;
	}
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean isAlive(long nodeId) throws IOException {
	checkState();
	synchronized (nodeMap) {
	    NodeImpl node = nodeMap.get(nodeId);
	    if (node == null) {
		return false;
	    } else {
		return node.isAlive();
	    }
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
    void reset() {
	dataService = null;
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
	return System.currentTimeMillis() + pingInterval;
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

		NodeImpl expirationNode =
		    new NodeImpl(0, null, false, System.currentTimeMillis());
		synchronized (expirationSet) {
		    SortedSet<NodeImpl> expiredNodes =
			expirationSet.headSet(expirationNode);
		    if (! expiredNodes.isEmpty()) {
			for (NodeImpl node : expiredNodes) {
			    setFailed(node);
			}
			expiredNodes.clear();
		    }
		}

		long sleepTime =
		    expirationSet.isEmpty() ?
		    pingInterval :
		    expirationSet.first().getExpiration();
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
}
