/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link WatchdogService} implementation. <p>
 *
 * In addition to the properties supported by the {@link DataServiceImpl}
 * class, the {@link #WatchdogServiceImpl constructor} supports the following
 * properties: <p>
 *
 * <ul>
 * <li> <i>Key:</i> {@code com.sun.sgs.app.name} <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the app name. <p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.start} <br>
 *	<i>Default:</i> {@code false} <br>
 *	Specifies whether the watchdog server should be started by this service.
 *	If {@code true}, the watchdog server is started.  If this property value
 *	is {@code true}, then the properties supported by the
 *	{@link WatchdogServerImpl} class must be specified.<p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.host} <br>
 *	<i>Default:</i> the local host name <br>
 *	Specifies the host name for the watchdog server that this service
 *	contacts (and, optionally, starts).<p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.port} <br>
 *	<i>Default:</i> {@code 44533} <br>
 *	Specifies the network port for the watchdog server that this service
 *	contacts (and, optionally, starts).  If the {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.start} property
 *	is {@code true}, then the value must be greater than or equal to
 *	{@code 0} and no greater than {@code 65535}, otherwise the value
 *	must be non-zero, positive, and no greater than	{@code 65535}.<p>
 * </ul> <p>
 */
public class WatchdogServiceImpl implements WatchdogService {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServiceImpl.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    private static final String WATCHDOG_SERVER_CLASSNAME =
	WatchdogServerImpl.class.getName();

    /** The property to specify that the watchdog server should be started. */
    private static final String START_SERVER_PROPERTY =
	WATCHDOG_SERVER_CLASSNAME + ".start";

    /** The property name for the watchdog server host. */
    private static final String HOST_PROPERTY =
	WATCHDOG_SERVER_CLASSNAME +  ".host";

    /** The property name for the watchdog server port. */
    private static final String PORT_PROPERTY =
	WatchdogServerImpl.PORT_PROPERTY;

    /** The default value of the server port. */
    private static final int DEFAULT_PORT = WatchdogServerImpl.DEFAULT_PORT;

    /** The minimum renew interval to ping watchdog server. */
    private static final long MIN_RENEW_INTERVAL = 50;

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock. */
    private final Object lock = new Object();
    
    /** The application name */
    private final String appName;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    private NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The watchdog server impl. */
    private final WatchdogServerImpl serverImpl;

    /** The watchdog server proxy. */
    private final WatchdogServer serverProxy;

    /** The name of the local host. */
    private final String localHost;
    
    /** The thread that pings the watchdog server. */
    private final Thread pingThread = new PingThread();

    /** The set of node listeners for all nodes. */
    private final Set<NodeListener> nodeListeners =
	Collections.synchronizedSet(new HashSet<NodeListener>());

    /** The map of node listeners registered for particular node IDs. */
    private final Map<Long, Set<NodeListener>>  nodeListenerMap =
	new HashMap<Long, Set<NodeListener>>();

    /** The ping interval. */
    private long pingInterval;

    /** The data service. */
    private DataService dataService;

    /** The local nodeId. */
    private long localNodeId;

    /** If true, this node is alive; initially, true. */
    private boolean isAlive = true;

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;
    
    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServiceImpl class documentation} for a list
     * of supported properties.
     *
     * @param	properties service (and server) properties
     * @param	systemRegistry system registry
     * @throws	Exception if a problem occurs constructing the service/server
     */
    public WatchdogServiceImpl(
	Properties properties, ComponentRegistry systemRegistry)
	throws Exception
    {
	logger.log(Level.CONFIG, "Creating WatchdogServiceImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    }
	    appName = wrappedProps.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }
	    boolean startServer = wrappedProps.getBooleanProperty(
 		START_SERVER_PROPERTY, false);
	    localHost = InetAddress.getLocalHost().getHostName();
	    String host;
	    int port;
	    if (startServer) {
		serverImpl = new WatchdogServerImpl(properties, systemRegistry);
		host = localHost;
		port = serverImpl.getPort();
	    } else {
		serverImpl = null;
		host = wrappedProps.getProperty(HOST_PROPERTY, localHost);
		port = wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT);
		if (port <= 0 || port > 65535) {
		    throw new IllegalArgumentException(
			"The " + PORT_PROPERTY + " property value must be " +
			"greater than 0 and less than 65535: " + port);
		}
	    }
	    
	    Registry rmiRegistry = LocateRegistry.getRegistry(host, port);
	    serverProxy = (WatchdogServer)
		rmiRegistry.lookup(WatchdogServerImpl.WATCHDOG_SERVER_NAME);
	    
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	    
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create WatchdogServiceImpl");
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
	    logger.log(Level.CONFIG, "Configuring WatchdogServiceImpl");
	}
	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    
	    synchronized (WatchdogServiceImpl.class) {
		if (WatchdogServiceImpl.txnProxy == null) {
		    WatchdogServiceImpl.txnProxy = proxy;
		} else {
		    assert WatchdogServiceImpl.txnProxy == proxy;
		}
	    }
	    
	    synchronized (lock) {
		if (dataService != null) {
		    throw new IllegalStateException("already configured");
		}
		(new ConfigureServiceContextFactory(txnProxy)).
		    joinTransaction();
		dataService = registry.getComponent(DataService.class);
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, proxy.getCurrentOwner(),
			registry.getComponent(TaskService.class));
		localNodeId = NodeId.nextNodeId(dataService);
	    }

	    if (serverImpl != null) {
		serverImpl.configure(registry, proxy);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure WatchdogServiceImpl");
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
	pingThread.interrupt();
	if (serverImpl != null) {
	    serverImpl.shutdown();
	}
	return true;
    }
	
    /* -- Implement WatchdogService -- */

    /** {@inheritDoc} */
    public long getLocalNodeId() {
	checkState();
	return localNodeId;
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAlive(boolean checkTransactionally) {
	checkState();
	boolean aliveStatus = isAlive();
	if (aliveStatus == false || !checkTransactionally) {
	    return aliveStatus;
	} else {
	    Node node = NodeImpl.getNode(dataService, localNodeId);
	    if (node == null || node.isAlive() == false) {
		setFailed();
		return false;
	    } else {
		return true;
	    }
	}
    }

    /** {@inheritDoc} */
    public Iterator<Node> getNodes() {
	checkState();
	return NodeImpl.getNodes(dataService);
    }

    /** {@inheritDoc} */
    public Node getNode(long nodeId) {
	checkState();
	Node node = NodeImpl.getNode(dataService, nodeId);
	return
	    node != null ?
	    node :
	    new NodeImpl(nodeId, null, false, 0);
    }

    /** {@inheritDoc} */
    public void addNodeListener(NodeListener listener) {
	checkState();
	nodeListeners.add(listener);
    }

    /** {@inheritDoc} */
    public void addNodeListener(long nodeId, NodeListener listener) {
	checkState();
	synchronized (nodeListenerMap) {
	    Set<NodeListener> listeners = nodeListenerMap.get(nodeId);
	    if (listeners == null) {
		listeners = new HashSet<NodeListener>();
		nodeListenerMap.put(nodeId, listeners);
	    }
	    listeners.add(listener);
	}
    }

    /* -- Implement transaction participant/context for 'configure' -- */

    private class ConfigureServiceContextFactory
	extends TransactionContextFactory
		<ConfigureServiceTransactionContext>
    {
	ConfigureServiceContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
	}
	
	/** {@inheritDoc} */
	public ConfigureServiceTransactionContext
	    createContext(Transaction txn)
	{
	    return new ConfigureServiceTransactionContext(txn);
	}
    }

    private final class ConfigureServiceTransactionContext
	extends TransactionContext
    {
	/**
	 * Constructs a context with the specified transaction.
	 */
        private ConfigureServiceTransactionContext(Transaction txn) {
	    super(txn);
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * Performs cleanup in the case that the transaction invoking
	 * the service's {@link #configure configure} method aborts.
	 */
	public void abort(boolean retryable) {
	    synchronized (lock) {
		dataService = null;
	    }

	    if (serverImpl != null) {
		serverImpl.reset();
	    }
	}

	/**
	 * {@inheritDoc}
	 *
	 * Starts the thread that registers this node with the
	 * watchdog server, and then continuously pings the watchdog
	 * server at the appropriate ping interval.
	 */
	public void commit() {
	    isCommitted = true;
	    pingThread.start();
        }
    }


    /**
     * This thread registers this node with the watchdog server, and
     * then continuously pings the watchdog server before the ping
     * interval (returned when registering the node) expires.
     */
    private final class PingThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	PingThread() {
	    super(CLASSNAME + "$PingThread");
	    setDaemon(true);
	}

	/**
	 * Registers the node with the watchdog server, and sends
	 * periodic pings.  This thread terminates if the node is no
	 * longer considered alive or if the watchdog service is
	 * shutdown.
	 */
	public void run() {

	    try {
		pingInterval = serverProxy.registerNode(localNodeId, localHost);
	    } catch (IOException e) {

		/*
		 * Unable to register node with watchdog server, so
		 * set this node as failed, and exit thread.
		 */
		logger.logThrow(
		    Level.SEVERE, e, "registering with watchdog server throws");
		setFailed();
		return;
	    }
	    long normalRenewInterval = pingInterval / 2;
	    long renewInterval = normalRenewInterval;
	    long lastPing = System.currentTimeMillis();

	    while (isAlive()) {

		if (shuttingDown()) {
		    // should this call setFailed()?
		    return;
		}

		try {
		    Thread.currentThread().sleep(renewInterval);
		} catch (InterruptedException e) {
		    // should this call setFailed()?
		    return;
		}
		
		try {
		    if (!serverProxy.ping(localNodeId)) {
			setFailed();
		    }
		    long now = System.currentTimeMillis();
		    if (now - lastPing > pingInterval) {
			setFailed();
		    }
		    lastPing = now;
		    renewInterval = normalRenewInterval;
		    
		} catch (IOException e) {
		    /*
		     * Adjust renew interval in order to ping server again
		     * before ping interval expires.
		     */
		    logger.logThrow(
			Level.WARNING, e, "pinging watchdog server throws");
		    renewInterval =
			Math.max(renewInterval / 2, MIN_RENEW_INTERVAL);
		}
	    }

	}
    }

    /* -- other methods -- */

    public WatchdogServerImpl getServer() {
	return serverImpl;
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
     * Returns {@code true} if this service is shutting down.
     */
    private boolean shuttingDown() {
	synchronized (lock) {
	    return shuttingDown;
	}
    }
    
    /**
     * Returns the local alive status: {@code true} if this node is
     * considered alive.
     */
    private boolean isAlive() {
	synchronized (lock) {
	    return isAlive;
	}
    }

    /**
     * Sets the local alive status of this node to {@code false}, and
     * notifies any registered node listeners of this node's failure.
     * This method is called when this node is no longer considered
     * alive.  Subsequent calls to {@link #isAlive isAlive} will
     * return {@code false}.  If this node's local alive status was
     * already set to {@code false}, then this method does nothing.
     */
    private void setFailed() {
	synchronized (lock) {
	    if (!isAlive) {
		return;
	    }
	    isAlive = false;
	}

	Set<NodeListener> listenersToNotify;
	synchronized (nodeListenerMap) {
	    Set<NodeListener> listenersForLocalNode =
		nodeListenerMap.get(localNodeId);
	    if (listenersForLocalNode == null) {
		listenersToNotify =  nodeListeners;
	    } else {
		listenersToNotify =
		    new HashSet<NodeListener>(listenersForLocalNode);
		listenersToNotify.addAll(nodeListeners);
	    }
	}

	final Node node = new NodeImpl(localNodeId, localHost, false, 0);
	for (NodeListener listener : listenersToNotify) {
	    final NodeListener nodeListener = listener;
	    nonDurableTaskScheduler.scheduleNonTransactionalTask(
		new AbstractKernelRunnable() {
		    public void run() {
			nodeListener.nodeFailed(node);
		    }
		});
	}
    }
}
