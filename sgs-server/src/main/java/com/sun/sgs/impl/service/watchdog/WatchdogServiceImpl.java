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

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.kernel.ConfigManager;
import com.sun.sgs.impl.kernel.KernelShutdownController;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Iterator;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * TBD: Modify implementation to not accept calls before service is ready.
 * The server should not service incoming remote calls (registerNode, etc.)
 * until it receives the 'ready' invocation (or finishes construction
 * successfully).  Some of the fields used in registerNode aren't initialized
 * until after the server is exported, so it can cause problems if the server
 * receives an incoming request before it has completed initializing.  In
 * practice, this flaw is not a problem so long as the server is started first
 * before starting other nodes.
 */
import javax.management.JMException;

/**
 * The {@link WatchdogService} implementation. <p>
 *
 * The {@link #WatchdogServiceImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.host
 *	</b></code><br>
 *	<i>Default:</i> the value of the {@code com.sun.sgs.server.host}
 *	property, if present, or {@code localhost} if this node is starting the 
 *      server <br> <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the host name for the watchdog server that this service
 *	contacts.  If the {@code
 *	com.sun.sgs.node.type} property is not {@code appNode}, then this
 *	property's default is used (since the watchdog server to contact will 
 *      be the one started on the local host).
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44533} <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the network port for the watchdog server that this service
 *	contacts (and, optionally, starts).  If the {@code 
 *      com.sun.sgs.node.type} property is not {@code singleNode}, then the
 *      value must be greater than or equal to {@code 0} and no greater than 
 *      {@code 65535}, otherwise the value must be greater than {@code 0}, 
 *      and no greater than {@code 65535}.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.client.host
 *	</b></code><br>
 *	<i>Default:</i> the local host name <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the host name for the watchdog client used when
 *	registering the node with the watchdog service.
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.client.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 0} (anonymous port) <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the network port for this watchdog service for receiving
 *	node status change notifications from the watchdog server.  The value
 *	must be greater than or equal to {@code 0} and no greater than
 *	{@code 65535}.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *      com.sun.sgs.impl.service.watchdog.timesync.interval
 *      </b></code><br>
 *      <i>Default:</i> {@code 300000} (five minutes) <br>
 *
 * <dd style="padding-top: .5em">
 *      Specifies the amount of time in milliseconds that this service will
 *      wait between synchronizing its local time with the global time
 *      of the {@code WatchdogServer}.  The value must be greater than or
 *      equal to {@code 1000} and no greater than {@link Long#MAX_VALUE}.
 * 
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.management.jmxremote.port
 *	</b></code><br>
 *	<i>Default:</i> None <br>
 *
 * <dd style="padding-top: .5em">
 *	Enables remote JMX monitoring through the specified port.  By default,
 *      remote monitoring is not enabled. Not that this is a system property,
 *      and must be set on the command line when starting the node.<p>
 *      
 * </dl> <p>
 */
public final class WatchdogServiceImpl
    extends AbstractService
    implements WatchdogService
{

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServiceImpl.class.getName();

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.watchdog";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(PKG_NAME + ".service"));

    /** The name of the version key. */
    private static final String VERSION_KEY = PKG_NAME + ".service.version";

    /** The major version. */
    private static final int MAJOR_VERSION = 1;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;
    
    /** The prefix for server properties. */
    private static final String SERVER_PROPERTY_PREFIX = PKG_NAME + ".server";

    /** The prefix for client properties. */
    private static final String CLIENT_PROPERTY_PREFIX = PKG_NAME + ".client";

    /** The property name for the watchdog server host. */
    private static final String HOST_PROPERTY =
	SERVER_PROPERTY_PREFIX +  ".host";

    /** The property name for the watchdog server port. */
    private static final String SERVER_PORT_PROPERTY =
	WatchdogServerImpl.PORT_PROPERTY;

    /** The default value of the server port. */
    private static final int DEFAULT_SERVER_PORT =
	WatchdogServerImpl.DEFAULT_PORT;

    /** The property name for the watchdog client host. */
    private static final String CLIENT_HOST_PROPERTY =
	CLIENT_PROPERTY_PREFIX + ".host";
    
    /** The property name for the watchdog client port. */
    private static final String CLIENT_PORT_PROPERTY =
	CLIENT_PROPERTY_PREFIX + ".port";

    /** The default value of the client port. */
    private static final int DEFAULT_CLIENT_PORT = 0;

    /** The property name for the timesync interval. */
    private static final String TIMESYNC_INTERVAL_PROPERTY =
            PKG_NAME + ".timesync.interval";

    /** The default time in milliseconds to wait between timesync. */
    private static final long DEFAULT_TIMESYNC_INTERVAL = 300000L;

    /** The minimum renew interval. */
    private static final long MIN_RENEW_INTERVAL = 25;

    /** The exporter for this server or {@code null}. */
    private Exporter<WatchdogClient> exporter = null;

    /** The watchdog server impl, or {@code null}. */
    private WatchdogServerImpl serverImpl = null;

    /** The watchdog server proxy, or {@code null}. */
    final WatchdogServer serverProxy;

    /** The watchdog client impl. */
    private final WatchdogClientImpl clientImpl;

    /** The watchdog client proxy. */
    final WatchdogClient clientProxy;

    /** The name of the local host. */
    final String localHost;
    
    /** The controller which enables node shutdown */
    private final KernelShutdownController shutdownController;

    /** The thread that renews the node with the watchdog server. */
    final Thread renewThread = new RenewThread();

    /** The local nodeId. */
    final long localNodeId;

    /** The interval for renewals with the watchdog server. */
    private final long renewInterval;

    /** The set of node listeners for all nodes. */
    private final ConcurrentMap<NodeListener, NodeListener> nodeListeners =
	new ConcurrentHashMap<NodeListener, NodeListener>();

    /** The set of recovery listeners for this node. */
    private final ConcurrentMap<RecoveryListener, RecoveryListener>
	recoveryListeners =
	    new ConcurrentHashMap<RecoveryListener, RecoveryListener>();

    /** The queues of SimpleCompletionHandlers, keyed by node being
     * recovered. */
    private final ConcurrentMap<Node, Queue<SimpleCompletionHandler>>
	recoveryQueues =
	    new ConcurrentHashMap<Node, Queue<SimpleCompletionHandler>>();

    /** The lock for {@code isAlive} field. */
    private final Object lock = new Object();
    
    /** If {@code true}, this node is alive; initially, the field is {@code
     * true}. Accesses to this field should be protected by {@code lock}.
     */
    private boolean isAlive = true;
    
    /** Our profiled data */
    private final WatchdogServiceStats serviceStats;

    /** The interval between synchronizations of global time with the server. */
    private final long timesyncInterval;

    /** The local offset to use when reporting the application time. */
    private volatile long timeOffset;

     /** a handle to the periodic time sync task */
    private RecurringTaskHandle timesyncTaskHandle = null;
    
    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServiceImpl class documentation} for a list
     * of supported properties. The Watchdog service is given the ability to
     * shutdown a node with the {@link KernelShutdownController}.
     *
     * @param	properties service (and server) properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     * @param   ctrl shutdown controller
     * @throws	Exception if a problem occurs constructing the service/server
     */
    public WatchdogServiceImpl(Properties properties,
	    ComponentRegistry systemRegistry, TransactionProxy txnProxy,
	    KernelShutdownController ctrl) 
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	logger.log(Level.CONFIG, "Creating WatchdogServiceImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	// Setup the KernelShutdownController object
        if (ctrl == null) {
            throw new NullPointerException("null shutdown controller");
        }
	shutdownController = ctrl;
	
	try {
	    localHost = InetAddress.getLocalHost().getHostName();
                
            NodeType nodeType = 
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE, 
                                             NodeType.class, 
                                             NodeType.singleNode);
            boolean startServer = nodeType != NodeType.appNode;
            boolean isFullStack = nodeType != NodeType.coreServerNode;
            
	    int clientPort = wrappedProps.getIntProperty(
		CLIENT_PORT_PROPERTY, DEFAULT_CLIENT_PORT, 0, 65535);
            
	    String clientHost = wrappedProps.getProperty(
		CLIENT_HOST_PROPERTY, localHost);

	    /*
	     * Check service version.
	     */
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("CheckServiceVersion") {
		    public void run() {
			checkServiceVersion(
			    VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
		    } },  taskOwner);

	    clientImpl = new WatchdogClientImpl();
	    exporter = new Exporter<WatchdogClient>(WatchdogClient.class);
	    exporter.export(clientImpl, clientPort);
	    clientProxy = exporter.getProxy();
            
	    String host;
	    int serverPort;
	    if (startServer) {
		serverImpl = new WatchdogServerImpl(
		    properties, systemRegistry, txnProxy, 
		    clientHost, clientProxy, isFullStack);
		host = localHost;
		serverPort = serverImpl.getPort();
	    } else {
		host = wrappedProps.getProperty(
		    HOST_PROPERTY,
		    wrappedProps.getProperty(
			StandardProperties.SERVER_HOST));
                if (host == null) {
                    throw new IllegalArgumentException(
                                           "A server host must be specified");
                }
		serverPort = wrappedProps.getIntProperty(
		    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 1, 65535);
	    }

	    Registry rmiRegistry = LocateRegistry.getRegistry(host, serverPort);
	    serverProxy = (WatchdogServer)
		rmiRegistry.lookup(WatchdogServerImpl.WATCHDOG_SERVER_NAME);

            int jmxPort = wrappedProps.getIntProperty(
                    StandardProperties.SYSTEM_JMX_REMOTE_PORT, -1);
	    localNodeId = dataService.getLocalNodeId();
            if (startServer) {
                renewInterval = serverImpl.renewInterval;
            } else {
                renewInterval = serverProxy.registerNode(
		    localNodeId, clientHost, clientProxy, jmxPort);
            }
            renewThread.start();

            timesyncInterval = wrappedProps.getLongProperty(
                    TIMESYNC_INTERVAL_PROPERTY, DEFAULT_TIMESYNC_INTERVAL,
                    1000, Long.MAX_VALUE);
            
            // create our profiling info and register our MBean
            ProfileCollector collector = 
                systemRegistry.getComponent(ProfileCollector.class);
            serviceStats = new WatchdogServiceStats(collector, this);
            try {
                collector.registerMBean(serviceStats, 
                                        WatchdogServiceStats.MXBEAN_NAME);
            } catch (JMException e) {
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }
            // set our data in the ConfigMXBean
            ConfigManager config = (ConfigManager)
                    collector.getRegisteredMBean(ConfigManager.MXBEAN_NAME);
            if (config == null) {
                logger.log(Level.CONFIG, "Could not find ConfigMXBean");
            } else {
                config.setJmxPort(jmxPort);
            }
            
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG,
			   "node registered, host:{0}, localNodeId:{1}",
			   clientHost, localNodeId);
	    }
	    
	} catch (Exception e) {
	    logger.logThrow(
		Level.CONFIG, e,
		"Failed to create WatchdogServiceImpl");
	    doShutdown();
	    throw e;
	}
    }

    /* -- Implement AbstractService -- */

    /** {@inheritDoc} */
    protected void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion)
    {
	throw new IllegalStateException(
	    "unable to convert version:" + oldVersion +
	    " to current version:" + currentVersion);
    }
    
    /** {@inheritDoc} */
    protected void doReady() throws Exception {
	// TBD: the client shouldn't accept incoming calls until this
	// service is ready which would give all RecoveryListeners a
	// chance to register.
        if (serverImpl != null) {
            serverImpl.ready();
            this.timeOffset = serverImpl.getTimeOffset();
        } else {
            TimeSyncRunner timeSyncRunner = new TimeSyncRunner();
            timeSyncRunner.run();
            timesyncTaskHandle = taskScheduler.scheduleRecurringTask(
                    timeSyncRunner, taskOwner,
                    System.currentTimeMillis() + timesyncInterval,
                    timesyncInterval);
            timesyncTaskHandle.start();
        }
    }

    /** {@inheritDoc} */
    protected void doShutdown() {
	synchronized (renewThread) {
	    renewThread.notifyAll();
	}
        if (timesyncTaskHandle != null) {
            timesyncTaskHandle.cancel();
        }
	try {
	    // The following 'join' call relies on an undocumented feature:
	    // 'join' can also be invoked on a thread that isn't started.
	    // If the server can't be exported, the renewThread won't be
	    // started when 'doShutdown' is invoked.
	    renewThread.join();
	} catch (InterruptedException e) {
	}
	if (exporter != null) {
	    exporter.unexport();
	}
	if (serverImpl != null) {
	    serverImpl.shutdown();
	}
    }
	
    /* -- Implement WatchdogService -- */

    /** {@inheritDoc} */
    public boolean isLocalNodeAlive() {
	checkState();
        serviceStats.isLocalNodeAliveOp.report();
	if (!getIsAlive()) {
	    return false;
	} else {
	    Node node = NodeImpl.getNode(dataService, localNodeId);
	    if (node == null || !node.isAlive()) {
		// this will call setFailedThenNotify(true)
                reportFailure(localNodeId, CLASSNAME);
		return false;
	    } else {
		return true;
	    }
	}
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAliveNonTransactional() {
	checkState();
        serviceStats.isLocalNodeAliveNonTransOp.report();
	return getIsAlive();
    }
    
    /** {@inheritDoc} */
    public Iterator<Node> getNodes() {
	checkState();
        serviceStats.getNodesOp.report();
	txnProxy.getCurrentTransaction();
	return NodeImpl.getNodes(dataService);
    }

    /** {@inheritDoc} */
    public Node getNode(long nodeId) {
	checkState();
	if (nodeId < 0) {
	    throw new IllegalArgumentException("invalid nodeId: " + nodeId);
	}
        serviceStats.getNodeOp.report();
	return NodeImpl.getNode(dataService, nodeId);
    }

    /** {@inheritDoc} */
    public void addNodeListener(NodeListener listener) {
	checkState();
	checkNonTransactionalContext();
	checkNull("listener", listener);
        serviceStats.addNodeListenerOp.report();
	nodeListeners.putIfAbsent(listener, listener);
    }

    /** {@inheritDoc} */
    public Node getBackup(long nodeId) {
	checkState();
        serviceStats.getBackupOp.report();
	NodeImpl node = (NodeImpl) getNode(nodeId);
	return
	    (node != null && node.hasBackup()) ?
	    getNode(node.getBackupId()) :
	    null;
    }

    /** {@inheritDoc} */
    public void addRecoveryListener(RecoveryListener listener) {
	checkState();
	checkNonTransactionalContext();
	checkNull("listener", listener);
        serviceStats.addRecoveryListenerOp.report();
	recoveryListeners.putIfAbsent(listener, listener);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void reportFailure(long nodeId, String className)
    {
	checkNull("className", className);
	checkNonTransactionalContext();
        if (shuttingDown() || !getIsAlive()) {
            return;
        }

        boolean isLocal = (nodeId == localNodeId);
        if (isLocal) {
            logger.log(Level.WARNING, "{1} reported failure in local " +
                    "node with id: {0}", nodeId, className);
        } else {
            logger.log(Level.WARNING, "{1} reported failure in remote" +
                    " node with id {0}", nodeId, className);
        }

        /*
         * Try to report the failure to the watchdog server. If we cannot 
         * contact the Watchdog server while reporting a remote failure, then
         * set the failure as local.
         */
        int retries = maxIoAttempts;
        while (retries-- > 0) {
            try {
                serverProxy.setNodeAsFailed(nodeId, isLocal, className,
                        maxIoAttempts);
                break;
            } catch (IOException ioe) {
                if (retries == 0) {
                    logger.log(Level.SEVERE, "Cannot report failure to " +
                            "Watchdog server");
                    isLocal = true;
                }
            }
        }
        
        if (isLocal) {
            setFailedThenNotify(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    public long currentAppTimeMillis() {
        return System.currentTimeMillis() - timeOffset;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppTimeMillis(long systemTimeMillis) {
        if (systemTimeMillis < timeOffset) {
            throw new IllegalArgumentException(
                    "System time : " + systemTimeMillis +
                    " is before the start time of this application.");
        }

        return systemTimeMillis - timeOffset;
    }

    /**
     * {@inheritDoc}
     */
    public long getSystemTimeMillis(long appTimeMillis) {
        return appTimeMillis + timeOffset;
    }

    /**
     * This thread continuously renews this node with the watchdog server
     * before the renew interval (returned when registering the node) expires.
     */
    private final class RenewThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	RenewThread() {
	    super(CLASSNAME + "$RenewThread");
	    setDaemon(true);
	}

	/**
	 * Registers the node with the watchdog server, and sends
	 * periodic renew requests.  This thread terminates if the
	 * node is no longer considered alive or if the watchdog
	 * service is shutdown.
	 */
	public void run() {
	    long startRenewInterval = renewInterval / 2;
	    long nextRenewInterval = startRenewInterval;
	    long lastRenewTime = System.currentTimeMillis();

	    while (getIsAlive()) {

		synchronized (this) {
		    if (shuttingDown()) {
			return;
		    }
		    try {
			wait(nextRenewInterval);
		    } catch (InterruptedException e) {
			return;
		    }
		}

		if (shuttingDown()) {
		    return;
		}

		boolean renewed = false;
		try {
    		    if (!serverProxy.renewNode(localNodeId)) {
                        // server has already marked node as failed, so we can
                        // go directly to removing this node
                        setFailedThenNotify(true);
			return;
		    }
		    renewed = true;
		    nextRenewInterval = startRenewInterval;
		    
		} catch (IOException e) {
		    /*
		     * Adjust renew interval in order to renew with
		     * server again before the renew interval expires.
		     */
		    logger.logThrow(
			Level.INFO, e,
			"renewing with watchdog server throws");
		    nextRenewInterval =
			Math.max(nextRenewInterval / 2, MIN_RENEW_INTERVAL);
		}
		long now = System.currentTimeMillis();
		if (now - lastRenewTime > renewInterval) {
                    // server has already marked node as failed, so we can
                    // go directly to removing this node
                    setFailedThenNotify(true);
                    return;
		}
		if (renewed) {
		    lastRenewTime = now;
		}
	    }
	}
    }

    /* -- other methods -- */

    /**
     * Returns the server.  This method is used for testing.
     *
     * @return	the server
     */
    public WatchdogServerImpl getServer() {
	return serverImpl;
    }
    
    /**
     * Throws {@code IllegalStateException} if this service is shutting down.
     */
    private void checkState() {
	if (shuttingDown()) {
	    throw new IllegalStateException("service shutting down");
	}
    }

    /**
     * Returns the local alive status: {@code true} if this node is
     * considered alive.
     */
    private boolean getIsAlive() {
	synchronized (lock) {
	    return isAlive;
	}
    }

    /**
     * Sets the local alive status of this node to {@code false}, and
     * if {@code notify} is {@code true}, notifies appropriate
     * registered node listeners of this node's failure.  This method
     * is called when this node is no longer considered alive.
     * Subsequent calls to {@link #isAlive isAlive} will return {@code
     * false}.  If this node's local alive status was already set to
     * {@code false}, then this method does nothing.
     *
     * @param	notify	if {@code true}, notifies appropriate registered
     *		node listeners of this node's failure
     */
    private void setFailedThenNotify(boolean notify) {
	synchronized (lock) {
	    if (!isAlive) {
		return;
	    }
	    isAlive = false;
	}

	if (notify) {
	    Node node = new NodeImpl(localNodeId, localHost, false);
	    notifyNodeListeners(node);
	}

        logger.log(
	    Level.SEVERE,
	    "Node:{0} forced to shutdown due to service failure", localNodeId);

        shutdownController.shutdownNode(this);
    }

    /**
     * Notifies the appropriate registered node listeners of the
     * status change of the specified {@code node}.  If invoking
     * {@link Node#isAlive isAlive} on the {@code node} returns
     * {@code false}, the {@code NodeListener#nodeFailed nodeFailed}
     * method is invoked on each node listener, otherwise the {@code
     * NodeListener#nodeStarted nodeStarted} method is invoked on each
     * node listener.
     *
     * @param	node a node
     * @throws  IllegalStateException if this service is shutting down
     */
    private void notifyNodeListeners(final Node node) {

	for (NodeListener listener : nodeListeners.keySet()) {
	    final NodeListener nodeListener = listener;
	    taskScheduler.scheduleTask(
		new AbstractKernelRunnable("NotifyNodeListeners") {
		    public void run() {
			if (!shuttingDown() &&
                            isLocalNodeAliveNonTransactional()) 
			{
			    if (node.isAlive()) {
				nodeListener.nodeStarted(node);
			    } else {
				nodeListener.nodeFailed(node);
			    }
			}
		    }
		}, taskOwner);
	}
    }

    /**
     * Notifies the registered recovery listeners that the specified
     * {@code node} needs to be recovered.
     *
     * @param	node a node	
     */
    private void notifyRecoveryListeners(final Node node) {
	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO, "Node:{0} recovering for node:{1}",
		       localNodeId, node.getId());
	}
	Queue<SimpleCompletionHandler> handlers =
	    new ConcurrentLinkedQueue<SimpleCompletionHandler>();
	if (recoveryQueues.putIfAbsent(node, handlers) != null) {
	    // recovery for node already being handled
	    return;
	}
	
	for (RecoveryListener listener : recoveryListeners.keySet()) {
	    final RecoveryListener recoveryListener = listener;
	    final SimpleCompletionHandler handler =
		new RecoveryCompletionHandler(node, listener);
	    handlers.add(handler);
	    taskScheduler.scheduleTask(
		new AbstractKernelRunnable("NotifyRecoveryListeners") {
		    public void run() {
			try {
			    if (!shuttingDown() &&
				isLocalNodeAliveNonTransactional())
			    {
				recoveryListener.recover(node, handler);
			    }
			} catch (Exception e) {
			    logger.logThrow(
			        Level.WARNING, e,
				"Notifying recovery listener on node:{0} " +
				"with node:{1}, handler:{2} throws",
				localNodeId, node, handler);
			}
		    }
		}, taskOwner);
	}
    }

    // Management methods
    /**
     * Retrieves information about the current node.
     * @return information about the current node
     */
    NodeInfo getNodeStatusInfo() {
        GetNodeStatusTask task = new GetNodeStatusTask();
        try {
            transactionScheduler.runTask(task, taskOwner);
        } catch (Exception e) {
            logger.logThrow(Level.INFO, e, "Could not retrive node info");
        }
        return task.info;
    }
    
    private final class GetNodeStatusTask extends AbstractKernelRunnable {
        NodeInfo info;
        GetNodeStatusTask() {
            super(null);
        }
        public void run() {
            NodeImpl node = NodeImpl.getNode(dataService, localNodeId);
            info = node.getNodeInfo();
        }
    }
    /**
     * Implements the WatchdogClient that receives callbacks from the
     * WatchdogServer.
     */
    private final class WatchdogClientImpl implements WatchdogClient {

	/** {@inheritDoc} */
        @Override
	public void nodeStatusChanges(long[] ids,
                                      String[] hosts,
                                      boolean[] status,
                                      long[] backups)
	{
	    if (ids.length != hosts.length || hosts.length != status.length ||
		status.length != backups.length)
	    {
		throw new IllegalArgumentException("array lengths don't match");
	    }
	    for (int i = 0; i < ids.length; i++) {
		if (ids[i] == localNodeId && status[i]) {
		    /* Don't notify the local node that it is alive. */
		    continue;
		}
		Node node =
                        new NodeImpl(ids[i], hosts[i], status[i], backups[i]);
		notifyNodeListeners(node);
		if (!status[i] && backups[i] == localNodeId) {
		    notifyRecoveryListeners(node);
		}
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void reportFailure(String className) {
	    setFailedThenNotify(true);
	}
    }

    /**
     * The {@code SimpleCompletionHandler} implementation for recovery.
     * When {@code completed} is invoked, the handler instance is removed
     * from the recovery completion handler queue for the associated node.
     * If a given handler is the last one to be removed from a node's
     * queue, then recovery is complete for that node, and the data store
     * is updated to clean up recovery information for that node.
     */
    private final class RecoveryCompletionHandler
	implements SimpleCompletionHandler
    {
	/** The failed node. */
	private final Node node;
	/** The recovery listener for this handler (currently unused). */
	private final RecoveryListener listener;
	/** Indicates whether recovery is done. */
	private boolean isDone = false;

	/**
	 * Constructs an instance with the specified {@code node} and
	 * recovery {@code listener}.
	 */
	RecoveryCompletionHandler(Node node, RecoveryListener listener) {
	    this.node = node;
	    this.listener = listener;
	}

	/** {@inheritDoc} */
	public void completed() {
	    synchronized (this) {
		if (isDone) {
		    return;
		}
		isDone = true;
	    }

	    Queue<SimpleCompletionHandler> handlers =
		recoveryQueues.get(node);
	    assert handlers != null;
	    handlers.remove(this);
	    if (handlers.isEmpty()) {
		// recovery for the node is complete, so remove node
		// from table of recovery queues.
		if (recoveryQueues.remove(node) != null) {
		    try {
			if (isLocalNodeAliveNonTransactional()) {
			    serverProxy.recoveredNode(
				node.getId(), localNodeId);
			}
		    } catch (Exception e) {
			logger.logThrow(
			    Level.WARNING, e,
			    "Problem invoking WatchdogServer.recoveredNode " +
			    "for node:{0} backup:{1}",  node, localNodeId);
		    }
		}
	    }
	}
    }

    /**
     * Private runnable that is used to periodically synchronize the local
     * time offset with that maintained in the global {@code WatchdogServer}.
     */
    private final class TimeSyncRunner implements KernelRunnable {
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return TimeSyncRunner.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            long before = System.currentTimeMillis();
            long appTime = serverProxy.currentAppTimeMillis();
            long after = System.currentTimeMillis();

            // calculate local offset value based on round trip time
            timeOffset = after - (appTime + (after - before) / 2L);
        }
    }
}
