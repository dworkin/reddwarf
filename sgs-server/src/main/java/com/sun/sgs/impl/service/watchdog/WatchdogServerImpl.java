/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService.FailureLevel;

/**
 * The {@link WatchdogServer} implementation.
 * <p>
 * The {@link #WatchdogServerImpl constructor} supports the following
 * properties:
 * <p>
 * <dl style="margin-left: 1em">
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.port
 *	</b></code><br>
 * <i>Default:</i> {@code 44533}
 * <dd style="padding-top: .5em">Specifies the network port for the server.
 * This value must be greater than or equal to {@code 0} and no greater than
 * {@code 65535}. If the value specified is {@code 0}, then an anonymous
 * port will be chosen. The value chosen will be logged, and can also be
 * accessed with the {@link #getPort getPort} method.
 * <p>
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.renew.interval
 *	</b></code><br>
 * <i>Default:</i> {@code 1000} (one second)<br>
 * <dd style="padding-top: .5em"> Specifies the renew interval which is
 * returned by the {@link #renewNode renewNode} method). The interval must be
 * greater than or equal to {@code 5} milliseconds and less than or equal to
 * {@code 10000} milliseconds (10 seconds).
 * <p>
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.id.block.size
 *	</b></code><br>
 * <i>Default:</i> {@code 256}<br>
 * <dd style="padding-top: .5em"> Specifies the block size to use when
 * reserving node IDs. The value must be greater than {@code 8}.
 * <p>
 * </dl>
 * <p>
 * Note that this server caches NodeImpls outside the data service to maintain
 * state.
 */
public final class WatchdogServerImpl extends AbstractService implements
	WatchdogServer {
    /** The name of this class. */
    private static final String CLASSNAME =
	    WatchdogServerImpl.class.getName();

    /** The package name. */
    private static final String PKG_NAME =
	    "com.sun.sgs.impl.service.watchdog";

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

    /** The name of the ID generator. */
    private static final String ID_GENERATOR_NAME =
	    SERVER_PROPERTY_PREFIX + ".id.generator";

    /** The property name for the ID block size. */
    private static final String ID_BLOCK_SIZE_PROPERTY =
	    SERVER_PROPERTY_PREFIX + ".id.block.size";

    /** The default ID block size for the ID generator. */
    private static final int DEFAULT_ID_BLOCK_SIZE = 256;

    /** The server port. */
    private final int port;

    /** The renew interval. */
    final long renewInterval;

    /** The node ID for this server. */
    final long localNodeId;

    /**
     * If {@code true}, this stack is a full stack, so the local node can be
     * assigned as a backup node. If {@code false}, this stack is a server
     * stack only, so the local node can not be assigned as a backup node.
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

    /** The ID block size for the IdGenerator. */
    private final int idBlockSize;

    /** The ID generator. */
    private final IdGenerator idGenerator;

    /** The map of registered nodes that are alive, keyed by node ID. */
    private final ConcurrentMap<Long, NodeImpl> aliveNodes =
	    new ConcurrentHashMap<Long, NodeImpl>();

    /** The set of alive nodes, sorted by renew expiration time. */
    final SortedSet<NodeImpl> expirationSet =
	    Collections.synchronizedSortedSet(new TreeSet<NodeImpl>());

    /** The map of alive node ports, keyed by host name. */
    /** TBD: use a ConcurrentHashMap, to improve system start up time? */
    private final HashMap<String, Set<Long>> aliveNodeHostPortMap =
	    new HashMap<String, Set<Long>>();

    /** The set of failed nodes that are currently recovering. */
    private final ConcurrentMap<Long, NodeImpl> recoveringNodes =
	    new ConcurrentHashMap<Long, NodeImpl>();

    /** A random number generator, for choosing backup nodes. */
    private final Random backupChooser = new Random();

    /**
     * The thread for checking node expiration times and checking if
     * recovering nodes need backups assigned..
     */
    private final Thread checkExpirationThread = new CheckExpirationThread();

    /**
     * Constructs an instance of this class with the specified properties. See
     * the {@link WatchdogServerImpl class documentation} for a list of
     * supported properties.
     * 
     * @param properties server properties
     * @param systemRegistry the system registry
     * @param txnProxy the transaction proxy
     * @param host the local host name
     * @param port the port
     * @param client the local watchdog client
     * @param fullStack {@code true} if this server is running on a full stack
     * @throws Exception if there is a problem starting the server
     */
    public WatchdogServerImpl(Properties properties,
	    ComponentRegistry systemRegistry, TransactionProxy txnProxy,
	    String host, int port, WatchdogClient client, boolean fullStack)
	    throws Exception {
	super(properties, systemRegistry, txnProxy, logger);
	logger.log(Level.CONFIG,
		"Creating WatchdogServerImpl properties:{0}", properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	isFullStack = fullStack;
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "WatchdogServerImpl[" + host + ":" +
		    port + "]: detected " +
		    (isFullStack ? "full stack" : "server stack"));
	}

	/*
	 * Check service version.
	 */
	transactionScheduler.runTask(new AbstractKernelRunnable(
		"CheckServiceVersion") {
	    public void run() {
		checkServiceVersion(VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
	    }
	}, taskOwner);

	int requestedPort =
		wrappedProps.getIntProperty(PORT_PROPERTY, DEFAULT_PORT, 0,
			65535);
	boolean noRenewIntervalProperty =
		wrappedProps.getProperty(RENEW_INTERVAL_PROPERTY) == null;
	renewInterval =
		isFullStack && noRenewIntervalProperty
			? RENEW_INTERVAL_UPPER_BOUND : wrappedProps
				.getLongProperty(RENEW_INTERVAL_PROPERTY,
					DEFAULT_RENEW_INTERVAL,
					RENEW_INTERVAL_LOWER_BOUND,
					RENEW_INTERVAL_UPPER_BOUND);
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "WatchdogServerImpl[" + host + ":" +
		    port + "]: renewInterval:" + renewInterval);
	}

	idBlockSize =
		wrappedProps.getIntProperty(ID_BLOCK_SIZE_PROPERTY,
			DEFAULT_ID_BLOCK_SIZE, IdGenerator.MIN_BLOCK_SIZE,
			Integer.MAX_VALUE);

	idGenerator =
		new IdGenerator(ID_GENERATOR_NAME, idBlockSize, txnProxy,
			transactionScheduler);

	FailedNodesRunnable failedNodesRunnable = new FailedNodesRunnable();
	transactionScheduler.runTask(failedNodesRunnable, taskOwner);
	Collection<NodeImpl> failedNodes = failedNodesRunnable.nodes;
	statusChangedNodes.addAll(failedNodes);
	for (NodeImpl failedNode : failedNodes) {
	    recoveringNodes.put(failedNode.getId(), failedNode);
	}

	// register our local id
	long[] values = registerNode(host, port, client);
	localNodeId = values[0];

	exporter = new Exporter<WatchdogServer>(WatchdogServer.class);
	this.port =
		exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}

	checkExpirationThread.start();
    }

    /** Calls NodeImpl.markAllNodesFailed. */
    private class FailedNodesRunnable extends AbstractKernelRunnable {
	Collection<NodeImpl> nodes;

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
    protected void handleServiceVersionMismatch(Version oldVersion,
	    Version currentVersion) {
	throw new IllegalStateException("unable to convert version:" +
		oldVersion + " to current version:" + currentVersion);
    }

    /** {@inheritDoc} */
    protected void doReady() {
	assert !notifyClientsThread.isAlive();
	// Don't notify clients until other services have had a chance
	// to register themselves with the watchdog.
	notifyClientsThread.start();
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
	    transactionScheduler.runTask(new AbstractKernelRunnable(
		    "MarkAllNodesFailed") {
		public void run() {
		    for (NodeImpl node : failedNodes) {
			node.setFailed(dataService, null);
		    }
		}
	    }, taskOwner);
	} catch (Exception e) {
	    logger.logThrow(Level.WARNING, e,
		    "Failed to update failed nodes during shutdown, throws");
	}

	Set<NodeImpl> failedNodesExceptMe =
		new HashSet<NodeImpl>(failedNodes);
	failedNodesExceptMe.remove(aliveNodes.get(localNodeId));
	notifyClients(failedNodesExceptMe, failedNodes);
	aliveNodes.clear();
    }

    /* -- Implement WatchdogServer -- */

    /**
     * Processes the nodes which have failed by
     * calling the failure methods
     * for each node in the collection. They processes
     * are separated into two for-loops so that a failed
     * node is not mistakenly chosen as a backup while
     * this operation is occurring.
     * 
     * @param c the collection of failed nodes
     */
    void processNodeFailures(Collection<NodeImpl> nodesToFail){
	for (NodeImpl node : nodesToFail) {
	    disqualifyAsAlive(node);
	}
	for (NodeImpl node : nodesToFail) {
	    runFailedNodeProcess(node);
	}
    }
    
    /**
     * Executes the node failure process by removing the
     * node from the {@code aliveNodes} map, performing
     * cleanup, and then setting the node to a failed
     * state.
     * 
     * @param node the node that has failed
     */
    void processNodeFailure(NodeImpl node) {
	disqualifyAsAlive(node);	
	runFailedNodeProcess(node);
    }
    
    /**
     * Remove failed nodes from map of "alive" nodes so that a
     * failed node won't be assigned as a backup. Also, clean
     * up the host port map entry.
     * 
     * @param node the node that has failed
     */
    void disqualifyAsAlive(NodeImpl node) {
	aliveNodes.remove(node.getId());
	removeHostPortMapEntry(node);
    }
    
    /**
     * Mark each expired node as failed, assign it a backup,
     * and update the data store. Add each expired node to the
     * list of recovering nodes. The node will be removed from
     * the list and from the data store in the 'recoveredNode'
     * callback.
     * 
     * @param node the node that has failed
     */
    void runFailedNodeProcess(NodeImpl node) {
	setFailed(node);
	recoveringNodes.put(node.getId(), node);
    }
    
    /**
     * {@inheritDoc}
     */
    public long[] registerNode(final String host, final int port,
	    WatchdogClient client) throws NodeRegistrationFailedException {
	callStarted();

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		    "registering node for host:{1} port:{2}", host, port);
	}

	try {
	    if (host == null) {
		throw new IllegalArgumentException("null host");
	    } else if (client == null) {
		throw new IllegalArgumentException("null client");
	    }

	    // Get next node ID, and put new node in transient map.
	    long nodeId;
	    try {
		nodeId = idGenerator.next();
	    } catch (Exception e) {
		logger.logThrow(Level.WARNING, e,
			"Failed to obtain node ID for {0}:{1}, throws", host,
			port);
		throw new NodeRegistrationFailedException(
			"Exception occurred while obtaining node ID", e);
	    }
	    final NodeImpl node = new NodeImpl(nodeId, host, port, client);
	    assert !aliveNodes.containsKey(nodeId);

	    synchronized (aliveNodeHostPortMap) {
		Set<Long> ports = null;
		if (aliveNodeHostPortMap.containsKey(host)) {
		    ports = aliveNodeHostPortMap.get(host);
		} else {
		    // New node, need to set up a new ports set.
		    ports = new HashSet<Long>();
		}
		boolean added = ports.add(Long.valueOf(port));
		if (!added) {
		    throw new IllegalArgumentException(
			    "configuration error: a node at " + host + ":" +
				    port + " already exists");
		}
		aliveNodeHostPortMap.put(host, ports);
	    }

	    // Persist node
	    try {
		transactionScheduler.runTask(new AbstractKernelRunnable(
			"StoreNewNode") {
		    public void run() {
			node.putNode(dataService);
		    }
		}, taskOwner);
	    } catch (Exception e) {
		removeHostPortMapEntry(node);
		throw new NodeRegistrationFailedException(
			"registration failed: " + nodeId, e);
	    }

	    // Put node in set, sorted by expiration.
	    node.setExpiration(calculateExpiration());
	    aliveNodes.put(nodeId, node);

	    // TBD: use a ConcurrentSkipListSet?
	    expirationSet.add(node);

	    // Notify clients of new node.
	    statusChangedNodes.add(node);
	    synchronized (notifyClientsLock) {
		notifyClientsLock.notifyAll();
	    }

	    logger.log(Level.INFO, "node:{0} registered", node);
	    return new long[] { nodeId, renewInterval };

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
		transactionScheduler.runTask(new AbstractKernelRunnable(
			"RemoveRecoveredNode") {
		    public void run() {
			NodeImpl.removeNode(dataService, nodeId);
		    }
		}, taskOwner);
	    } catch (Exception e) {
		logger.logThrow(Level.WARNING, e,
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
    public void setNodeAsFailed(long nodeId, String className,
	    FailureLevel severity, final int maxNumberOfAttempts)
	    throws IOException {

	int count = maxNumberOfAttempts;
	NodeImpl remoteNode = NodeImpl.getNode(dataService, nodeId);
	
	// Run the methods which declare the node as failed
	processNodeFailure(remoteNode);

	// Now that the node is not considered alive, 
	// try to report the failure to the watchdog
	// so that the node can be shutdown. Just in case 
	// we run into an IOException, try a few times.
	while (--count > 0) {
	    try {
		remoteNode.getWatchdogClient().reportFailure(className,
			severity);

	    } catch (IOException ioe) {
		// Try again

	    } catch (Exception e) {
		logger.log(Level.WARNING, "Unexpected exception thrown: {0}" +
			e.getLocalizedMessage(), nodeId);
	    }
	}

	// Report if we weren't able to get the watchdog client.
	// We may want to throw an exception forcing the server
	// to address this issue.
	if (count == 0) {
	    final String msg =
		    "Could not retrieve watchdog client given " +
			    maxNumberOfAttempts + " attempt(s)";
	    logger.log(Level.WARNING, msg);
	    throw new IOException(msg);
	}
    }

    /**
     * {@inheritDoc}
     */
    public void setNodeAsFailed(long nodeId) {
	NodeImpl remoteNode = NodeImpl.getNode(dataService, nodeId);
	processNodeFailure(remoteNode);
    }

    /**
     * Chooses a backup for the failed {@code node}, updates the node's
     * status to failed, assigns the chosen backup for the node, and persists
     * the node state changes in the data service.
     */
    private void setFailed(final NodeImpl node) {

	final long nodeId = node.getId();
	logger.log(Level.FINE, "Node failed: {0}", nodeId);

	/*
	 * First, reassign a backup to each primary for which the failed node
	 * is a backup but hadn't yet completed recovery. If a backup is
	 * reassigned, add to the statusChangedNodes queue so that the change
	 * can be propagated to clients.
	 */
	for (Long primaryId : node.getPrimaries()) {
	    final NodeImpl primary = recoveringNodes.get(primaryId);
	    if (primary != null) {
		assignBackup(primary, chooseBackup(primary));
		statusChangedNodes.add(primary);
	    }
	}

	/*
	 * Choose a backup for the failed node, update the node's status to
	 * failed, update backup's state to include failed node as one that is
	 * being recovered.
	 */
	assignBackup(node, chooseBackup(node));
    }

    /**
     * Chooses a backup for the specified {@code node} from the map of "alive"
     * nodes. The backup is picked randomly. Before this method is invoked,
     * the specified {@code node} as well as other currently detected failed
     * nodes should not be present in the "alive" nodes map.
     */
    private NodeImpl chooseBackup(NodeImpl node) {
	NodeImpl choice = null;
	// Copy of the alive nodes
	NodeImpl[] values;
	final int numAliveNodes;
	synchronized (aliveNodes) {
	    numAliveNodes = aliveNodes.size();
	    values = aliveNodes.values().toArray(new NodeImpl[numAliveNodes]);
	}
	int random =
		numAliveNodes > 0 ? backupChooser.nextInt(numAliveNodes) : 0;
	for (int i = 0; i < numAliveNodes; i++) {
	    // Choose one of the values[] elements randomly. If we
	    // chose the localNodeId, loop again, choosing the next
	    // array element.
	    int tryNode = (random + i) % numAliveNodes;
	    NodeImpl backupCandidate = values[tryNode];
	    /*
	     * The local node can only be assigned as a backup if this stack
	     * is a full stack (meaning that this stack is running a
	     * single-node application). If this node is the only "alive" node
	     * in a server stack, then a backup for the failed node will
	     * remain unassigned until a new node is registered and recovery
	     * for the failed node will be delayed.
	     */
	    // assert backupCandidate.getId() != node.getId()
	    if (isFullStack || backupCandidate.getId() != localNodeId) {
		choice = backupCandidate;
		break;
	    }
	}
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "backup:{0} chosen for node:{1}", choice,
		    node);
	}
	return choice;
    }

    /**
     * Persists node and backup status updates in data service.
     */
    private void assignBackup(final NodeImpl node, final NodeImpl backup) {
	try {
	    transactionScheduler.runTask(new AbstractKernelRunnable(
		    "SetNodeFailed") {
		public void run() {
		    node.setFailed(dataService, backup);
		    if (backup != null) {
			backup.addPrimary(dataService, node.getId());
		    }
		}
	    }, taskOwner);
	} catch (Exception e) {
	    logger.logThrow(Level.SEVERE, e,
		    "Marking node:{0} failed and assigning backup throws",
		    node);
	}
    }

    /* -- other methods -- */

    /**
     * Returns the port being used for this server.
     * 
     * @return the port
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns an expiration time based on the current time.
     */
    private long calculateExpiration() {
	return System.currentTimeMillis() + renewInterval;
    }

    /**
     * Removes an entry in the host port map; called when a node is found to
     * have failed.
     */
    private void removeHostPortMapEntry(NodeImpl node) {
	synchronized (aliveNodeHostPortMap) {
	    String host = node.getHostName();
	    Set<Long> ports = aliveNodeHostPortMap.get(host);
	    if (ports == null) {
		logger.log(Level.WARNING, "Unexpected null ports value");
		return;
	    }
	    ports.remove(Long.valueOf(node.getPort()));
	    if (ports.isEmpty()) {
		// No more ports in use on this host
		aliveNodeHostPortMap.remove(host);
	    }
	}
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
	 * Wakes up periodically to detect failed nodes and update state
	 * accordingly.
	 */
	public void run() {

	    Collection<NodeImpl> expiredNodes = new ArrayList<NodeImpl>();

	    while (!shuttingDown()) {
		/*
		 * Determine which nodes have failed because they haven't
		 * renewed before their expiration time.
		 */
		long now = System.currentTimeMillis();
		synchronized (expirationSet) {
		    while (!expirationSet.isEmpty()) {
			NodeImpl node = expirationSet.first();
			if (node.getExpiration() > now) {
			    break;
			}
			expiredNodes.add(node);
			expirationSet.remove(node);
		    }
		}

		/**
		 * Perform the node failure procedure
		 */
		if (!expiredNodes.isEmpty()) {
		    processNodeFailures(expiredNodes);
		    statusChangedNodes.addAll(expiredNodes);
		    expiredNodes.clear();
		}

		/*
		 * Check each recovering node: if a given recovering node
		 * doesn't have a backup, assign it a backup if an "alive"
		 * node is available to serve as one.
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
		 * Readjust time to sleep before checking for expired nodes.
		 */
		long sleepTime;
		synchronized (expirationSet) {
		    sleepTime =
			    expirationSet.isEmpty() ? renewInterval
				    : expirationSet.first().getExpiration() -
					    now;
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
     * This thread informs all currently known clients of node status changes
     * (either nodes started or failed) as they occur. This thread is notified
     * by {@link #registerNode registerNode} when nodes are registered, or by
     * the {@code CheckExpirationThread} when nodes fail to renew before their
     * expiration time has lapsed.
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
     * Notifies the {@code WatchdogClient} of each node in the collection of
     * {@code notifyNodes} of the node status changes in {@code changedNodes}.
     * 
     * @param notifyNodes nodes whose clients should be notified
     * @param changedNodes nodes with status changes
     */
    private void notifyClients(Collection<NodeImpl> notifyNodes,
	    Collection<NodeImpl> changedNodes) {
	// Assemble node information into arrays.
	int size = changedNodes.size();
	long[] ids = new long[size];
	String[] hosts = new String[size];
	int[] ports = new int[size];
	boolean[] status = new boolean[size];
	long[] backups = new long[size];

	int i = 0;
	for (NodeImpl changedNode : changedNodes) {
	    logger.log(Level.FINEST, "changed node:{0}", changedNode);
	    ids[i] = changedNode.getId();
	    hosts[i] = changedNode.getHostName();
	    ports[i] = changedNode.getPort();
	    status[i] = changedNode.isAlive();
	    backups[i] = changedNode.getBackupId();
	    i++;
	}

	// Notify clients of status changes.
	for (NodeImpl notifyNode : notifyNodes) {
	    WatchdogClient client = notifyNode.getWatchdogClient();
	    try {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			    "notifying client:{0} of status change",
			    notifyNode);
		}
		client.nodeStatusChanges(ids, hosts, ports, status, backups);
	    } catch (Exception e) {
		// TBD: Should it try harder to notify the client in
		// the non-restart case? In the restart case, the
		// client may have failed too.
		logger.logThrow(Level.WARNING, e,
			"Notifying {0} of node status changes failed:",
			notifyNode.getId());
	    }
	}
    }

}
