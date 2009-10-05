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

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.Objects;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.management.NodesMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import java.io.IOException;
import java.util.Arrays;

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
 *	com.sun.sgs.impl.service.watchdog.server.timeflush.interval
 *	</b></code><br>
 *	<i>Default:</i> {@code 5000} (five seconds)
 *
 * <dd style="padding-top: .5em">Represents the amount of time in milliseconds
 *      that the server will wait between updates to the global application time
 *      stored in the data store.  A larger value will take less system
 *      resources but will allow the possibility of the global application clock
 *      to drift by at least the given value if the system crashes.  The
 *      interval must be greater than or equal to {@code 100} milliseconds and
 *      less than or equal to {@code 300000} milliseconds.<p>
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
 * 
 * Note that this server caches NodeImpls outside the data service to
 * maintain state.
 */
public final class WatchdogServerImpl
    extends AbstractService
    implements WatchdogServer
{
    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServerImpl.class.getName();

    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.watchdog";

    /** The prefix for server properties. */
    private static final String SERVER_PROPERTY_PREFIX = PKG_NAME + ".server";

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(SERVER_PROPERTY_PREFIX));

    /** The name of the version key. */
    private static final String VERSION_KEY =
	SERVER_PROPERTY_PREFIX + ".version";

    /** The major version. */
    private static final int MAJOR_VERSION = 1;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;
    
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
    private static final int RENEW_INTERVAL_LOWER_BOUND = 100;

    /** The upper bound for the renew interval. */
    private static final int RENEW_INTERVAL_UPPER_BOUND = Integer.MAX_VALUE;

    /** The property name for the timeflush interval. */
    private static final String TIMEFLUSH_INTERVAL_PROPERTY =
            SERVER_PROPERTY_PREFIX + ".timeflush.interval";

    /** The default time in milliseconds to wait between timeflushes. */
    private static final long DEFAULT_TIMEFLUSH_INTERVAL = 5000L;

    /**
     * The name binding used to store the current global time in the data store.
     */
    private static final String APP_TIME_BINDING = PKG_NAME +
                                                       ".appTime";

    /**
     * The name binding used to store the most recent timestamp interval
     * that was being used when the application time was updated in the
     * data store.
     */
    private static final String APP_TIME_DRIFT_BINDING = PKG_NAME +
                                                         ".appTimeDrift";

    /** The server port. */
    private final int serverPort;

    /** The renew interval. */
    final long renewInterval;

    /** The timeflush interval. */
    final long timeflushInterval;

    /** The node ID for this server. */
    final long localNodeId;

    /**
     * If {@code true}, this stack is a full stack, so the local node
     * can be assigned as a backup node.  If {@code false}, this stack
     * is a server stack only, so the local node can not be assigned as
     * a backup node.
     */
    private final boolean isFullStack;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The lock for notifying the {@code NotifyClientsThread}. */
    final Object notifyClientsLock = new Object();

    /** The thread to notify clients of node status changes. */
    private final Thread notifyClientsThread = new NotifyClientsThread();

    /** The queue of nodes whose status has changed. */
    final Queue<NodeImpl> statusChangedNodes =
	new ConcurrentLinkedQueue<NodeImpl>();

    /** The map of registered nodes that are alive, keyed by node ID. */
    private final ConcurrentMap<Long, NodeImpl> aliveNodes =
	new ConcurrentHashMap<Long, NodeImpl>();

    // TBD: use a ConcurrentSkipListSet?
    /** The set of alive nodes, sorted by renew expiration time. */
    final SortedSet<NodeImpl> expirationSet =
	Collections.synchronizedSortedSet(new TreeSet<NodeImpl>());
    
    /** The set of failed nodes that are currently recovering. */
    private final ConcurrentMap<Long, NodeImpl> recoveringNodes =
	new ConcurrentHashMap<Long, NodeImpl>();
    
    /** A random number generator, for choosing backup nodes. */
    private final Random backupChooser = new Random();
    
    /** The thread for checking node expiration times and checking if
     * recovering nodes need backups assigned.. */
    private final Thread checkExpirationThread = new CheckExpirationThread();
    
    /** The JMX MXBean to expose nodes in the system. */
    private final NodeManager nodeMgr;

    /** The offset to use when reporting the global application time. */
    private long timeOffset;

    /** a handle to the periodic global time flush task */
    private RecurringTaskHandle timeflushTaskHandle = null;

    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServerImpl class documentation} for a list
     * of supported properties.
     *
     * @param	properties server properties
     * @param	systemRegistry the system registry
     * @param	txnProxy the transaction proxy
     * @param	host the local host name
     * @param	client the local watchdog client
     * @param   fullStack {@code true} if this server is running on a full
     *            stack
     *
     * @throws	Exception if there is a problem starting the server
     */
    public WatchdogServerImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy,
                              String host, 
                              WatchdogClient client,
                              boolean fullStack)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	logger.log(Level.CONFIG, "Creating WatchdogServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	
	isFullStack = fullStack;
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "WatchdogServerImpl[" + host +
		       "]: detected " +
		       (isFullStack ? "full stack" : "server stack"));
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
	
	int requestedPort = wrappedProps.getIntProperty(
 	    PORT_PROPERTY, DEFAULT_PORT, 0, 65535);
	boolean noRenewIntervalProperty = 
	    wrappedProps.getProperty(RENEW_INTERVAL_PROPERTY) == null;
	renewInterval =
	    isFullStack && noRenewIntervalProperty ?
	    RENEW_INTERVAL_UPPER_BOUND :
	    wrappedProps.getLongProperty(
		RENEW_INTERVAL_PROPERTY, DEFAULT_RENEW_INTERVAL,
		RENEW_INTERVAL_LOWER_BOUND, RENEW_INTERVAL_UPPER_BOUND);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "WatchdogServerImpl[" + host +
		       "]: renewInterval:" + renewInterval);
	}

        timeflushInterval = wrappedProps.getLongProperty(
                TIMEFLUSH_INTERVAL_PROPERTY, DEFAULT_TIMEFLUSH_INTERVAL,
                100, 300000);

	FailedNodesRunnable failedNodesRunnable = new FailedNodesRunnable();
	transactionScheduler.runTask(failedNodesRunnable, taskOwner);
	Collection<NodeImpl> failedNodes = failedNodesRunnable.nodes;
        statusChangedNodes.addAll(failedNodes);
        for (NodeImpl failedNode : failedNodes) {
	    recoveringNodes.put(failedNode.getId(), failedNode);
	}
 
                
        // Create the node manager MBean and register it.  This must be
        // done before regiseterNode is called.
        ProfileCollector collector = 
            systemRegistry.getComponent(ProfileCollector.class);
        nodeMgr = new NodeManager(this);
        try {
            collector.registerMBean(nodeMgr, NodeManager.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
        
        // register our local id
        int jmxPort = wrappedProps.getIntProperty(
                    StandardProperties.SYSTEM_JMX_REMOTE_PORT, -1);
	localNodeId = dataService.getLocalNodeId();
        registerNode(localNodeId, host, client, jmxPort);

	exporter = new Exporter<WatchdogServer>(WatchdogServer.class);
	serverPort = exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(
		Level.INFO, "Server is using port {0,number,#}", serverPort);
	}
	
	checkExpirationThread.start();
    }

    /** Calls NodeImpl.markAllNodesFailed. */
    private class FailedNodesRunnable extends AbstractKernelRunnable {
	Collection<NodeImpl> nodes = null;

	/** Constructs an instance. */
	FailedNodesRunnable() {
	    super(null);
	}

	/** {@inheritDoc} */
	public void run() {
	    nodes = NodeImpl.markAllNodesFailed(dataService);
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
        assert !notifyClientsThread.isAlive();
        // Don't notify clients until other services have had a chance
        // to register themselves with the watchdog.
        notifyClientsThread.start();

        // If this is the first time booting up, bind the current global
        // time and set now as time 0.  Also establish the global timeOffset
        // for the server
        try {
            transactionScheduler.runTask(new TimestampBindingRunner(),
                                         taskOwner);
        } catch (Exception e) {
            throw new AssertionError("Failed to initiate global time");
        }

        // kick off a periodic time flush task
        timeflushTaskHandle = transactionScheduler.scheduleRecurringTask(
                new TimeflushRunner(timeflushInterval),
                taskOwner, System.currentTimeMillis(),
                timeflushInterval);
        timeflushTaskHandle.start();
    }

    /** {@inheritDoc} */
    protected void doShutdown() {
	// Unexport server and stop threads.
	exporter.unexport();
	synchronized (checkExpirationThread) {
	    checkExpirationThread.notifyAll();
	}
	synchronized (notifyClientsLock) {
	    notifyClientsLock.notifyAll();
	}
	try {
	    checkExpirationThread.join();
	    notifyClientsThread.join();
	} catch (InterruptedException e) {
	}
	expirationSet.clear();
	statusChangedNodes.clear();

	// Mark all nodes failed and notify all clients (except local one)
	// of failure.
	final Collection<NodeImpl> failedNodes = aliveNodes.values();
	try {
	    transactionScheduler.runTask(
	      new AbstractKernelRunnable("MarkAllNodesFailed") {
		public void run() {
		    for (NodeImpl node : failedNodes) {
			node.setFailed(dataService, null);
		    }
		} }, taskOwner);
	} catch (Exception e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Failed to update failed nodes during shutdown, throws");
	}

	Set<NodeImpl> failedNodesExceptMe =
	    new HashSet<NodeImpl>(failedNodes);
	failedNodesExceptMe.remove(aliveNodes.get(localNodeId));
	notifyClients(failedNodesExceptMe, failedNodes);
        
        for (long nodeId : aliveNodes.keySet()) {
            nodeMgr.notifyNodeFailed(nodeId);
        }
	aliveNodes.clear();

        // stop the time flush task and take the final timestamp
        if (timeflushTaskHandle != null) {
            timeflushTaskHandle.cancel();
        }
        try {
            transactionScheduler.runTask(new TimeflushRunner(0), taskOwner);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.logThrow(Level.FINE, e,
                                "Unable to store latest application time");
            }
        }
    }

    /* -- Implement WatchdogServer -- */

    /**
     * {@inheritDoc}
     */
    public long registerNode(long nodeId,
			     final String host,
			     WatchdogClient client,
			     int jmxPort)
	throws NodeRegistrationFailedException
    {
	callStarted();

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "registering node {0} on host {1}", nodeId, host);
	}

	try {
	    if (host == null) {
		throw new IllegalArgumentException("null host");
	    } else if (client == null) {
		throw new IllegalArgumentException("null client");
	    }
   
	    // Put new node in transient map.
	    final NodeImpl node = new NodeImpl(nodeId, host, jmxPort, client);
            
            if (aliveNodes.putIfAbsent(nodeId, node) != null) {
                logger.log(Level.SEVERE,
                           "Duplicate node ID generated for node on {0}",
                           host);
                throw new NodeRegistrationFailedException(
		    "Duplicate node ID generated");
            }
            
	    // Persist node
	    try {
		transactionScheduler.runTask(
		  new AbstractKernelRunnable("StoreNewNode") {
		    public void run() {
			node.putNode(dataService);
		    } }, taskOwner);
	    } catch (Exception e) {
                aliveNodes.remove(nodeId);
		throw new NodeRegistrationFailedException(
		    "registration failed: " + nodeId, e);
	    }
	    
	    // Put node in set, sorted by expiration.
	    node.setExpiration(calculateExpiration());
            nodeMgr.notifyNodeStarted(nodeId);
	    expirationSet.add(node);

	    // Notify clients of new node.
	    statusChangedNodes.add(node);
	    synchronized (notifyClientsLock) {
		notifyClientsLock.notifyAll();
	    }

	    logger.log(Level.INFO, "node:{0} registered", node);
	    return renewInterval;
	    
	} finally {
	    callFinished();
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean renewNode(long nodeId) {
	callStarted();

	try {
	    NodeImpl node = aliveNodes.get(nodeId);
	    if (node == null || !node.isAlive() || node.isExpired()) {
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
    public void recoveredNode(final long nodeId, long backupId) {
	callStarted();

	try {
	    try {
		// TBD: should the node be removed if the current
		// backup ID for the node with the given node ID
		// is not the given backup ID?
		transactionScheduler.runTask(
		  new AbstractKernelRunnable("RemoveRecoveredNode") {
		    public void run() {
			NodeImpl.removeNode(dataService,  nodeId);
		    } }, taskOwner);
	    } catch (Exception e) {
		logger.logThrow(
		    Level.WARNING, e,
		    "Removing recovered node {0} throws", nodeId);
	    }
	    recoveringNodes.remove(nodeId);
	    
	} finally {
	    callFinished();
	}
    }
    
    /**
     * {@inheritDoc}
     */
    public void setNodeAsFailed(long nodeId, boolean isLocal, String className,
            int maxNumberOfAttempts)
    {
        NodeImpl remoteNode = aliveNodes.get(nodeId);
        if (remoteNode == null) {
            logger.log(Level.FINEST, "Node with ID '" + nodeId +
                    "' is already reported as failed");
            return;
        }

        if (!isLocal) {
            // Try to report the failure to the watchdog so that the node can 
            // be shutdown. Try a few times if we run into an IOException.
            int retries = maxNumberOfAttempts;
            while (retries-- > 0) {
                try {
                    remoteNode.getWatchdogClient().reportFailure(className);
                    break;
                } catch (IOException ioe) {
                    if (retries == 0) {
                        logger.log(Level.WARNING, "Could not retrieve " +
                                "watchdog client given " +
                                maxNumberOfAttempts + " attempt(s)");
                    }
                }
            }
        }
        processNodeFailures(Arrays.asList(remoteNode));
    }

    /**
     * {@inheritDoc}
     */
    public long currentAppTimeMillis() {
        return System.currentTimeMillis() - timeOffset;
    }

    /* -- other methods -- */

    /**
     * Returns the offset being used by this server to report global
     * application time with the {@link #currentAppTimeMillis()} method.
     *
     * @return the time offset
     */
    long getTimeOffset() {
        return timeOffset;
    }

    /**
     * Processes the nodes which have failed by calling the failure methods
     * for each node in the collection. The processes are separated into two
     * for-loops so that a failed node is not mistakenly chosen as a backup
     * while this operation is occurring.
     *
     * @param nodesToFail the collection of failed nodes
     * @return a subset of {@code nodesToFail} that were marked as failed from
     * this method
     */
    Collection<NodeImpl> processNodeFailures(Collection<NodeImpl> nodesToFail) {
        Collection<NodeImpl> aliveNodesToFail = new ArrayList<NodeImpl>();

	// Declare the nodes as failed only if it has not be reported to be
        // be failed already to prevent a failed node from being assigned as a
        // backup. It should be noted that nodes that are removed from the set
        // of {@code aliveNodes} are never added back.
	for (NodeImpl node : nodesToFail) {
            if (aliveNodes.remove(node.getId()) != null) {
                aliveNodesToFail.add(node);
            }
	}

	// Iterate through the nodes known to still be alive
	for (NodeImpl node : aliveNodesToFail) {
            /**
             * Mark the node as failed, assign it a backup, and update the
             * data store. Add the node to the list of recovering nodes. The
             * node will be removed from the list and from the data store in
             * the 'recoveredNode' callback.
             */
            setFailed(node);
            recoveringNodes.put(node.getId(), node);
	}
	return aliveNodesToFail;
    }

    /**
     * Returns the port being used for this server.
     *
     * @return	the server port
     */
    public int getPort() {
	return serverPort;
    }
    
    /**
     * Returns an expiration time based on the current time.
     */
    private long calculateExpiration() {
	return System.currentTimeMillis() + renewInterval;
    }
    
    /**
     * This thread checks the node map, sorted by expiration times to
     * determine if any nodes have failed and updates their state accordingly.
     * It also checks the recovered nodes to see if a given recovering node
     * has no backup assigned, and assigns a backup if one is available.
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

	    Collection<NodeImpl> expiredNodes = new ArrayList<NodeImpl>();
	    
	    while (!shuttingDown()) {
		/*
		 * Determine which nodes have failed because they
		 * haven't renewed before their expiration time.
		 */
		long now = System.currentTimeMillis();
		synchronized (expirationSet) {
		    while (!expirationSet.isEmpty()) {
			NodeImpl node = expirationSet.first();

			// We are done aggregating from the sorted
			// set once the expiration exceeds "now"
			if (node.getExpiration() > now) {
			    break;
			}

			// Only report the node as expired if it
			// is still alive. Otherwise we assume it
			// is already being reported as failed.
			if (aliveNodes.containsKey(node.getId())) {
			    expiredNodes.add(node);
			}
			expirationSet.remove(node);
		    }
		}
		
		/**
		 * Perform the node failure procedure
		 */
		if (!expiredNodes.isEmpty()) {
                    processNodeFailures(expiredNodes);
		    /*
		     * Remove failed nodes from map of "alive" nodes so
		     * that a failed node won't be assigned as a backup.
                     * Also, clean up the host port map entry.
		     */
		    for (NodeImpl node : expiredNodes) {
			aliveNodes.remove(node.getId());
                        nodeMgr.notifyNodeFailed(node.getId());
		    }
                    
		    /*
		     * Mark each expired node as failed, assign it a
		     * backup, and update the data store.  Add each
		     * expired node to the list of recovering nodes.
		     * The node will be removed from the list and from
		     * the data store in the 'recoveredNode' callback.
		     */
		    for (NodeImpl node : expiredNodes) {
			setFailed(node);
			recoveringNodes.put(node.getId(), node);
		    }
		    statusChangedNodes.addAll(expiredNodes);
		    expiredNodes.clear();
		}

		/*
		 * Check each recovering node: if a given recovering
		 * node doesn't have a backup, assign it a backup if
		 * an "alive" node is available to serve as one.
		 */
		if (!recoveringNodes.isEmpty()) {
		    for (NodeImpl recoveringNode : recoveringNodes.values()) {
			if (!recoveringNode.hasBackup()) {
			    NodeImpl backup = chooseBackup(recoveringNode);
			    if (backup != null) {
				assignBackup(recoveringNode, backup);
				statusChangedNodes.add(recoveringNode);
			    }
			}
		    }
		}

		// TBD: should reminder notifications be sent to
		// nodes that haven't recovered yet?

		/*
		 * Notify thread to send out node status change notifications.
		 */
		if (!statusChangedNodes.isEmpty()) {
		    synchronized (notifyClientsLock) {
			notifyClientsLock.notifyAll();
		    }
		}

		/*
		 * Readjust time to sleep before checking for expired
		 * nodes.
		 */
		long sleepTime;
		synchronized (expirationSet) {
		    sleepTime =
			expirationSet.isEmpty() ?
			renewInterval :
			expirationSet.first().getExpiration() - now;
		}
		synchronized (this) {
		    if (shuttingDown()) {
			return;
		    }
		    try {
			wait(sleepTime);
		    } catch (InterruptedException e) {
			return;
		    }
		}
	    }
	}
    }

	/**
	 * Chooses a backup for the failed {@code node}, updates the
	 * node's status to failed, assigns the chosen backup for the
	 * node, and persists the node state changes in the data
	 * service.
	 */
	private void setFailed(final NodeImpl node) {

	    final long nodeId = node.getId();
	    logger.log(Level.FINE, "Node failed: {0}", nodeId);

	    /*
	     * First, reassign a backup to each primary for which the
	     * failed node is a backup but hadn't yet completed
	     * recovery.  If a backup is reassigned, add to the
	     * statusChangedNodes queue so that the change can be
	     * propagated to clients.
	     */
	    for (Long primaryId : node.getPrimaries()) {
		final NodeImpl primary = recoveringNodes.get(primaryId);
		if (primary != null) {
		    assignBackup(primary, chooseBackup(primary));
		    statusChangedNodes.add(primary);
		}
	    }

	    /*
	     * Choose a backup for the failed node, update the node's
	     * status to failed, update backup's state to include
	     * failed node as one that is being recovered.
	     */
	    assignBackup(node, chooseBackup(node));
	}
	
	/**
	 * Chooses a backup for the specified {@code node} from the
	 * map of "alive" nodes.  The backup is picked randomly. Before this 
         * method is invoked, the specified {@code node} as well as other 
         * currently detected failed nodes should not be present in the "alive"
	 * nodes map.
	 */
	private NodeImpl chooseBackup(NodeImpl node) {
	    NodeImpl choice = null;
            // Copy of the alive nodes
            NodeImpl[] values;
            final int numAliveNodes;
	    synchronized (aliveNodes) {
		numAliveNodes = aliveNodes.size();
                values =
		    aliveNodes.values().toArray(new NodeImpl[numAliveNodes]);
            }
	    int random = numAliveNodes > 0
		? backupChooser.nextInt(numAliveNodes) : 0;
	    for (int i = 0; i < numAliveNodes; i++) {
		// Choose one of the values[] elements randomly. If we
		// chose the localNodeId, loop again, choosing the next
		// array element.
		int tryNode = (random + i) % numAliveNodes;
		NodeImpl backupCandidate = values[tryNode];
		/*
		 * The local node can only be assigned as a backup
		 * if this stack is a full stack (meaning that
		 * this stack is running a single-node application).
		 * If this node is the only "alive" node in a server
		 * stack, then a backup for the failed node will remain
		 * unassigned until a new node is registered and
		 * recovery for the failed node will be delayed.
		 */
		// assert backupCandidate.getId() !=  node.getId()
		if (isFullStack ||
		    backupCandidate.getId() != localNodeId)
		    {
                        choice = backupCandidate;
                        break;
                    }
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "backup:{0} chosen for node:{1}",
			   choice, node);
	    }
	    return choice;
	}

	/**
	 * Persists node and backup status updates in data service.
	 */
	private void assignBackup(final NodeImpl node, final NodeImpl backup) {
	    try {
		transactionScheduler.runTask(
		    new AbstractKernelRunnable("SetNodeFailed") {
			public void run() {
			    node.setFailed(dataService, backup);
			    if (backup != null) {
				backup.addPrimary(dataService, node.getId());
			    }
			} }, taskOwner);
	    } catch (Exception e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "Marking node:{0} failed and assigning backup throws",
		    node);
	    }
	}


    /**
     * This thread informs all currently known clients of node status
     * changes (either nodes started or failed) as they occur.  This
     * thread is notified by {@link #registerNode registerNode} when
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

	    while (true) {
		synchronized (notifyClientsLock) {
		    while (statusChangedNodes.isEmpty()) {
			if (shuttingDown()) {
			    return;
			}
			try {
			    notifyClientsLock.wait();
			} catch (InterruptedException e) {
			    return;
			}
		    }
		}

		if (shuttingDown()) {
		    break;
		}

		// TBD: possibly wait for more updates to batch?
		
		Iterator<NodeImpl> iter = statusChangedNodes.iterator();
		Collection<NodeImpl> changedNodes = new ArrayList<NodeImpl>();
		while (iter.hasNext()) {
		    changedNodes.add(iter.next());
		    iter.remove();
		}

		notifyClients(aliveNodes.values(), changedNodes);
	    }
	}
    }

    /**
     * Notifies the {@code WatchdogClient} of each node in the
     * collection of {@code notifyNodes} of the node status changes in
     * {@code changedNodes}.
     *
     * @param	notifyNodes nodes whose clients should be notified
     * @param	changedNodes nodes with status changes
     */
    private void notifyClients(Collection<NodeImpl> notifyNodes,
			       Collection<NodeImpl> changedNodes)
    {
	// Assemble node information into arrays.
	int size = changedNodes.size();
	long[] ids = new long[size];
	String[] hosts = new String[size];
	boolean[] status = new boolean[size];
	long[] backups = new long[size];

	int i = 0;
	for (NodeImpl changedNode : changedNodes) {
	    logger.log(Level.FINEST, "changed node:{0}", changedNode);
	    ids[i] = changedNode.getId();
	    hosts[i] = changedNode.getHostName();
	    status[i] = changedNode.isAlive();
	    backups[i] = changedNode.getBackupId();
	    i++;
	}

	// Notify clients of status changes.
	for (NodeImpl notifyNode : notifyNodes) {
	    WatchdogClient client = notifyNode.getWatchdogClient();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"notifying client:{0} of status change", notifyNode);
		}
		client.nodeStatusChanges(ids, hosts, status, backups);
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
    }
    
    // Management support
    private NodeInfo[] getAllNodeInfo() {
        final Set<NodeInfo> nodes = new HashSet<NodeInfo>();
        try {
            transactionScheduler.runTask(
                new AbstractKernelRunnable("GetNodeInfo") {
                    public void run() {
                        Iterator<Node> iter = NodeImpl.getNodes(dataService);
                        while (iter.hasNext()) {
                            NodeImpl node = (NodeImpl) iter.next();
                            nodes.add(node.getNodeInfo());
                        }
		}
            },  taskOwner);
        } catch (Exception e) {
            logger.logThrow(Level.INFO, e,
                    "Could not retrieve node information");
            return new NodeInfo[0];
        }
        return nodes.toArray(new NodeInfo[nodes.size()]);
    }
    
    /**
     * Private class for JMX information.
     */
    private static class NodeManager extends NotificationBroadcasterSupport
            implements NodesMXBean 
    {
        /** The watchdog server we'll use to get the node info. */
        private WatchdogServerImpl watchdog;
        
        private AtomicLong seqNumber = new AtomicLong();
        
        /** Description of the notifications. */
        private static MBeanNotificationInfo[] notificationInfo =
            new MBeanNotificationInfo[] {
                new MBeanNotificationInfo(
                        new String[] {NODE_STARTED_NOTIFICATION, 
                                      NODE_FAILED_NOTIFICATION }, 
                        Notification.class.getName(), 
                        "A node has started or failed") };
        /**
         * Creates an instance of the manager.
         * @param watchdog  the watchdog server
         */
        NodeManager(WatchdogServerImpl watchdog) {
            super(notificationInfo);
            this.watchdog = watchdog;
        }

        /** {@inheritDoc} */
        public NodeInfo[] getNodes() {
            return watchdog.getAllNodeInfo();
        }
 
        /*
         * Package private methods.
         */
               
        /**
         * Sends JMX notification that a node started.
         * @param nodeId the identifier of the newly started node
         */
        void notifyNodeStarted(long nodeId) {
            sendNotification(
                    new Notification(NODE_STARTED_NOTIFICATION,
                                     this.MXBEAN_NAME,
                                     seqNumber.incrementAndGet(),
                                     System.currentTimeMillis(),
                                     "Node started: " + nodeId));
        }
        
        /**
         * Sends JMX notification that a node failed.
         * @param nodeId the identifier of the failed node
         */
        void notifyNodeFailed(long nodeId) {
            sendNotification(
                    new Notification(NODE_FAILED_NOTIFICATION,
                                     this.MXBEAN_NAME,
                                     seqNumber.incrementAndGet(),
                                     System.currentTimeMillis(),
                                     "Node failed:  " + nodeId));
        }
    }

    /**
     * Private runnable that is used to setup the initial binding of the
     * current global time in the data store.  This task also establishes the
     * server's global time offset value.
     */
    private final class TimestampBindingRunner implements KernelRunnable {
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return TimestampBindingRunner.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            ManagedSerializable<Long> time = null;
            ManagedSerializable<Long> drift = null;
            try {
                time = Objects.uncheckedCast(dataService.getServiceBinding(
                        APP_TIME_BINDING));
                drift = Objects.uncheckedCast(dataService.getServiceBinding(
                        APP_TIME_DRIFT_BINDING));

                // add a small amount of time when recovering to keep the
                // global time from gradually drifting behind due to
                // system crashes
                time.set(time.get() + drift.get() / 2);
            } catch (NameNotBoundException nnbe) {
                time = new ManagedSerializable<Long>(Long.valueOf(0));
                drift = new ManagedSerializable<Long>(timeflushInterval);
                dataService.setServiceBinding(APP_TIME_BINDING, time);
                dataService.setServiceBinding(APP_TIME_DRIFT_BINDING, drift);
            }

            timeOffset = System.currentTimeMillis() - time.get();
        }
    }

    /**
     * Private runnable that periodically records the current global time
     * in the data store
     */
    private final class TimeflushRunner implements KernelRunnable {
        private final long drift;
        public TimeflushRunner(long drift) {
            this.drift = drift;
        }
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return TimeflushRunner.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            ManagedSerializable<Long> time = Objects.uncheckedCast(
                    dataService.getServiceBinding(APP_TIME_BINDING));
            ManagedSerializable<Long> drift = Objects.uncheckedCast(
                    dataService.getServiceBinding(APP_TIME_DRIFT_BINDING));
            time.set(currentAppTimeMillis());
            drift.set(this.drift);
        }
    }
}
