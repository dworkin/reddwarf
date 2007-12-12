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
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    /** Server state. */
    private static enum State {
        /** The service is initialized */
	INITIALIZED,
        /** The service is ready */
        READY,
        /** The service is shutting down */
        SHUTTING_DOWN,
        /** The service is shut down */
        SHUTDOWN
    }
    
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

    /** The lock for {@code state} and {@code callsInProgress} fields. */
    private final Object lock = new Object();
    
    /** The server port. */
    private final int port;

    /** The renew interval. */
    final long renewInterval;

    /** The node ID for this server. */
    private volatile long localNodeId;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The lock for notifying the {@code NotifyClientsThread}. */
    final Object notifyClientsLock = new Object();

    /** The thread to notify clients of node status changes. */
    private final Thread notifyClientsThread = new NotifyClientsThread();

    /** The queue of nodes whose status has changed. */
    final Queue<NodeImpl> statusChangedNodes =
	new ConcurrentLinkedQueue<NodeImpl>();

    /** The task owner. */
    private final TaskOwner taskOwner;

    /** The data service. */
    final DataService dataService;

    /** The ID block size for the IdGenerator. */
    private final int idBlockSize;
    
    /** The ID generator. */
    private final IdGenerator idGenerator;

    /** Failed nodes that were detected by the constructor. */
    private final Collection<NodeImpl> failedNodes;

    /** The map of registered nodes that are alive, keyed by node ID. */
    private final ConcurrentMap<Long, NodeImpl> aliveNodes =
	new ConcurrentHashMap<Long, NodeImpl>();

    /** The set of alive nodes, sorted by renew expiration time. */
    final SortedSet<NodeImpl> expirationSet =
	Collections.synchronizedSortedSet(new TreeSet<NodeImpl>());

    /** The set of failed nodes that are currently recovering. */
    private final ConcurrentMap<Long, NodeImpl> recoveringNodes =
	new ConcurrentHashMap<Long, NodeImpl>();
    
    /** The thread for checking node expiration times and checking if
     * recovering nodes need backups assigned.. */
    private final Thread checkExpirationThread = new CheckExpirationThread();

    /** The server state. */
    private State state;
    
    /** The count of calls in progress. */
    private int callsInProgress = 0;

    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServerImpl class documentation} for a list
     * of supported properties.
     *
     * @param	properties server properties
     * @param	systemRegistry the system registry
     * @param	txnProxy the transaction proxy
     *
     * @throws	Exception if there is a problem starting the server
     */
    public WatchdogServerImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy)
	throws Exception
    {
	logger.log(Level.CONFIG, "Creating WatchdogServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	if (systemRegistry == null) {
	    throw new NullPointerException("null registry");
	} else if (txnProxy == null) {
	    throw new NullPointerException("null txnProxy");
	}
	synchronized (WatchdogServerImpl.class) {
	    if (WatchdogServerImpl.txnProxy == null) {
		WatchdogServerImpl.txnProxy = txnProxy;
	    } else {
		assert WatchdogServerImpl.txnProxy == txnProxy;
	    }
	}
	
	int requestedPort = wrappedProps.getIntProperty(
 	    PORT_PROPERTY, DEFAULT_PORT, 0, 65535);
	renewInterval = wrappedProps.getLongProperty(
	    RENEW_INTERVAL_PROPERTY, DEFAULT_RENEW_INTERVAL,
	    RENEW_INTERVAL_LOWER_BOUND, RENEW_INTERVAL_UPPER_BOUND);
	idBlockSize = wrappedProps.getIntProperty(
 	    ID_BLOCK_SIZE_PROPERTY, DEFAULT_ID_BLOCK_SIZE,
	    IdGenerator.MIN_BLOCK_SIZE, Integer.MAX_VALUE);
	
	dataService = txnProxy.getService(DataService.class);
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	taskOwner = txnProxy.getCurrentOwner();
	
	idGenerator =
	    new IdGenerator(ID_GENERATOR_NAME,
			    idBlockSize,
			    txnProxy,
			    taskScheduler);
	setState(State.INITIALIZED);
	
	FailedNodesRunnable failedNodesRunnable = new FailedNodesRunnable();
	runTransactionally(failedNodesRunnable);
	failedNodes = failedNodesRunnable.nodes;
	
	exporter = new Exporter<WatchdogServer>(WatchdogServer.class);
	port = exporter.export(this, WATCHDOG_SERVER_NAME, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	
	checkExpirationThread.start();
	notifyClientsThread.start();
    }

    /** Calls NodeImpl.markAllNodesFailed. */
    private class FailedNodesRunnable extends AbstractKernelRunnable {
	Collection<NodeImpl> nodes;
	public void run() {
	    nodes = NodeImpl.markAllNodesFailed(dataService);
	}
    }

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void ready() {
	synchronized (lock) {
	    switch (state) {
		
	    case INITIALIZED:
		setState(State.READY);
		break;
		
	    case READY:
		return;
		
	    case SHUTTING_DOWN:
	    case SHUTDOWN:
		throw new IllegalStateException("service shutting down");
	    }
	}
	localNodeId = txnProxy.getService(WatchdogService.class).
	    getLocalNodeId();
	for (NodeImpl failedNode : failedNodes) {
	    recoveringNodes.put(failedNode.getId(), failedNode);
	}
	if (!failedNodes.isEmpty()) {
	    notifyClients(aliveNodes.values(), failedNodes);
	    failedNodes.clear();
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	// TBD: launch shutdown thread to perform shutdown, and
	// if shutdown is in progress, join thread and return true.
	
	synchronized (lock) {
	    if (shuttingDown()) {
		return false;
	    }
	    setState(State.SHUTTING_DOWN);
	    while (callsInProgress > 0) {
		try {
		    lock.wait();
		} catch (InterruptedException e) {
		    return false;
		}
	    }
	}

	// Unexport server and stop threads.
	exporter.unexport();
	checkExpirationThread.interrupt();
	notifyClientsThread.interrupt();
	try {
	    checkExpirationThread.join();
	    notifyClientsThread.join();
	} catch (InterruptedException e) {
	    return false;
	}
	expirationSet.clear();
	statusChangedNodes.clear();

	// Mark all nodes failed and notify clients of failure.
	final Collection<NodeImpl> failedNodes = aliveNodes.values();
	try {
	    runTransactionally(new AbstractKernelRunnable() {
		public void run() {
		    for (NodeImpl node : failedNodes) {
			node.setFailed(dataService, null);
		    }
		}});
	} catch (Exception e) {
	    logger.logThrow(
		Level.WARNING, e,
		"Failed to update failed nodes during shutdown, throws");
	    return false;
	}
	notifyClients(failedNodes, failedNodes);

	setState(State.SHUTDOWN);
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

	logger.log(Level.FINEST, "registering node for hostname:{0}", hostname);

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
	    assert ! aliveNodes.containsKey(nodeId);
	
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
	    aliveNodes.put(nodeId, node);

	    // TBD: use a ConcurrentSkipListSet?
	    expirationSet.add(node);

	    // Notify clients of new node.
	    statusChangedNodes.add(node);
	    synchronized (notifyClientsLock) {
		notifyClientsLock.notifyAll();
	    }

	    logger.log(Level.INFO, "node:{0} registered", node);
	    return new long[]{nodeId, renewInterval};
	    
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
    public boolean isAlive(long nodeId) {
	callStarted();

	try {
	    NodeImpl node = aliveNodes.get(nodeId);
	    if (node == null || node.isExpired()) {
		return false;
	    } else {
		return node.isAlive();
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
		runTransactionally(new AbstractKernelRunnable() {
		    public void run() {
			NodeImpl.removeNode(dataService,  nodeId);
		}});
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
     * Runs the specified {@code task} within a transaction.
     *
     * @param	task a task
     * @throws	Exception if there is a problem running the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
	try {
            taskScheduler.runTransactionalTask(task, taskOwner);
	} catch (Exception e) {
	    logger.logThrow(Level.WARNING, e, "task failed: {0}", task);
	    throw e;
	}
    }

    /** Sets this server's state to {@code newState}. */
    private void setState(State newState) {
	synchronized (lock) {
	    state = newState;
	}
    }
    
    /**
     * Increments the number of calls in progress.  This method should
     * be invoked by remote methods to both increment in progress call
     * count and to check the state of this server.  When the call has
     * completed processing, the remote method should invoke {@link
     * #callFinished callFinished} before returning.
     *
     * @throws	IllegalStateException if this service is shutting down
     */
    private void callStarted() {
	synchronized (lock) {
	    if (shuttingDown()) {
		throw new IllegalStateException("service is shutting down");
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
	    if (state == State.SHUTTING_DOWN && callsInProgress == 0) {
		lock.notifyAll();
	    }
	}
    }
    
    /**
     * Returns {@code true} if this server is shutting down.
     */
    private boolean shuttingDown() {
	synchronized (lock) {
	    return
		state == State.SHUTTING_DOWN ||
		state == State.SHUTDOWN;
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
	    
	    while (! shuttingDown()) {
		/*
		 * Determine which nodes have failed because they
		 * haven't renewed before their expiration time.
		 */
		long now = System.currentTimeMillis();
		synchronized (expirationSet) {
		    while (! expirationSet.isEmpty()) {
			NodeImpl node = expirationSet.first();
			if (node.getExpiration() > now) {
			    break;
			}
			expiredNodes.add(node);
			expirationSet.remove(node);
		    }
		}
		
		if (! expiredNodes.isEmpty()) {
		    /*
		     * Remove failed nodes from map of "alive" nodes so
		     * that a failed node won't be assigned as a backup.
		     */
		    for (NodeImpl node: expiredNodes) {
			aliveNodes.remove(node.getId());
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
		if (! recoveringNodes.isEmpty()) {
		    for (NodeImpl recoveringNode : recoveringNodes.values()) {
			if (! recoveringNode.hasBackup()) {
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
		if (! statusChangedNodes.isEmpty()) {
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
		try {
		    Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
		    return;
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
	 * map of "alive" nodes.  For now, the choice is the first
	 * node encountered (returned arbitrarily from iterating
	 * through the "alive" nodes).  Before this method is invoked,
	 * the specified {@code node} as well as other currently
	 * detected failed nodes should not be present in the "alive"
	 * nodes map.
	 */
	private NodeImpl chooseBackup(NodeImpl node) {
	    NodeImpl choice = null;
	    synchronized (aliveNodes) {
		if (aliveNodes.size() > 1) {
		    for (NodeImpl backupCandidate : aliveNodes.values()) {
			// TBD: the watchdog server's node should not be
			// assigned as a backup because it may not contain
			// a full stack.  If it is the only "alive" node,
			// then a backup for this node will remain
			// unassigned until a new node is registered.
			// This will delay recovery. -- ann (9/7/07)
			
			// assert backupCandidate.getId() !=  node.getId()
			if (backupCandidate.getId() != localNodeId) {
			    choice = backupCandidate;
			    break;
			}
		    }
		}
	    }
	    return choice;
	}

	/**
	 * Persists node and backup status updates in data service.
	 */
	private void assignBackup(final NodeImpl node, final NodeImpl backup) {
	    try {
		runTransactionally(new AbstractKernelRunnable() {
			public void run() {
			    node.setFailed(dataService, backup);
			    if (backup != null) {
				backup.addPrimary(dataService, node.getId());
			    }
			}});
	    } catch (Exception e) {
		logger.logThrow(
		    Level.SEVERE, e,
		    "Marking node:{0} failed and assigning backup throws",
		    node);
	    }
	}
    }

    /**
     * This thread informs all currently known clients of node status
     * changes (either nodes started or failed) as they occur.  This
     * thread is notified by {@link #registerNode registerNode)} when
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
}
