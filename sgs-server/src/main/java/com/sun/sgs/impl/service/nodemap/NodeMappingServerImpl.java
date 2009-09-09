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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The remote server portion of the node mapping service.  This
 * portion of the service is used for any global operations, such
 * as selecting a node for an identity.
 * Additionally, all changes to the map are made by the server so it
 * can notify listeners of changes, no matter which node is affected.
 * <p>
 * The {@link #NodeMappingServerImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44535}
 *
 * <dd style="padding-top: .5em">The network port for the {@code
 *	NodeMappingServer}.  This value must be no less than {@code 0} and no
 *	greater than {@code 65535}.  The value {@code 0} can only be specified
 *	if the {@code com.sun.sgs.impl.service.nodemap.start.server}
 *	property is {@code true}, and means that an anonymous port will be
 *	chosen for running the server. <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.policy.class
 *	</b></code> <br>
 *	<i>Default:</i>
 *	<code>com.sun.sgs.impl.service.nodemap.RoundRobinPolicy</code>
 *
 * <dd style="padding-top: .5em">
 *      The name of the class that implements {@link
 *	NodeAssignPolicy}, used for the node assignment policy. The class 
 *      should be public, not abstract, and should provide a public constructor
 *      with {@link Properties} and {@link NodeMappingServerImpl} parameters. 
 *      <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.remove.expire.time
 *	</b></code> <br>
 *      <i>Default:</i> {@code 5000} 
 *
 * <dd style="padding-top: .5em">
 *      The minimum time, in milliseconds, that this server will wait before
 *      removing a potentially inactive identity from the map.   This value
 *      must be greater than {@code 0}.   Shorter expiration times cause the
 *      map to be cleaned up more frequently, potentially causing more
 *      {@link NodeMappingService#assignNode(Class, Identity) assignNode} 
 *      calls;  longer expiration times will increase the chance that an 
 *      identity will become active again before it can be removed. <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.relocation.expire.time
 *	</b></code> <br>
 *      <i>Default:</i> {@code 10000}
 *
 * <dd style="padding-top: .5em">
 *      The time allowed, in milliseconds, for {@code 
 *      IdentityRelocationListener}s to call
 *      {@link SimpleCompletionHandler#completed completed} on the
 *      handler they receive.  If this time has elapsed, this server disregards
 *      the proposed identity relocation.  This value is used to guard against
 *      listeners which never respond they are finished.  During this time
 *      period, the identity is prohibited from moving elsewhere unless the
 *      node has failed. <p>
 *
 * </dl> <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.nodemap.server</code> to log
 * information at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization or test failures
 * <li> {@link Level#CONFIG CONFIG} - Construction information
 * <li> {@link Level#WARNING WARNING} - Errors
 * <li> {@link Level#FINE FINE} - Map entry remove operations
 * <li> {@link Level#FINEST FINEST} - Trace operations
 * </ul> <p>
 *
 * This class is public for testing.
 */
public final class NodeMappingServerImpl 
        extends AbstractService 
        implements NodeMappingServer 
{
    /** Package name for this class. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /** The property name for the server port. */
    static final String SERVER_PORT_PROPERTY = PKG_NAME + ".server.port";

    /** The default value of the server port. */
    // XXX:  does the exporter allow all servers to use the same port?
    static final int DEFAULT_SERVER_PORT = 44535;
    
    /** The name we export ourselves under. */
    static final String SERVER_EXPORT_NAME = "NodeMappingServer";
    
    /**
     * The property that specifies the name of the class that implements
     * DataStore.
     */
    private static final String ASSIGN_POLICY_CLASS_PROPERTY =
            PKG_NAME + ".policy.class";
    
    /** The property name for the amount of time to wait before removing an
     * identity from the node map.
     */
    private static final String REMOVE_EXPIRE_PROPERTY = 
            PKG_NAME + ".remove.expire.time";
    
    /** Default time to wait before removing an identity, in milliseconds. */
    private static final int DEFAULT_REMOVE_EXPIRE_TIME = 5000;
    
    /** The property name for the amount of time allowed for IdentityRelocation
     * listeners to respond that they have completed their work.
     */
    private static final String RELOCATION_EXPIRE_PROPERTY =
            PKG_NAME + ".relocation.expire.time";

    /** Default time allowed for IdentityRelocationListeners to respond that
     * they have completed preparations for an identity move, in milliseconds.
     */
    // TODO:  This expiration must be longer than the timeout for moving
    //        client sessions.  We need that timeout in a common location
    //        (StandardProperties?) so we can ensure this one is larger.
    private static final int DEFAULT_RELOCATION_EXPIRE_TIME = 10000;

    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME + ".server"));
    
    /** The port we've been exported on. */
    private final int port;
    
    /** The exporter for this server */
    private final Exporter<NodeMappingServer> exporter;
    
    /** The watchdog service. */
    final WatchdogService watchdogService;
    
    /** The policy for assigning new nodes.  This will likely morph into
     *  the load balancing policy, as well. */
    private final NodeAssignPolicy assignPolicy;

    /** The thread that removes inactive identities */
    // XXX:  should this be a TaskScheduler.scheduleRecurringTask?
    private final Thread removeThread;
    
     /** Our watchdog node listener. */
    private final NodeListener watchdogNodeListener;
    
    /** Our string representation, used by toString(). */
    private final String fullName;
    
    /** Identities waiting to be removed, with the time they were
     *  entered in the map.
     *
     *  TODO For failover, will need to persist these identities.
     */
    private final Queue<RemoveInfo> removeQueue =
            new ConcurrentLinkedQueue<RemoveInfo>();
    /** 
     * The set of clients of this server who wish to be notified if
     * there's a change in the map.
     *
     * TODO For failover, these need to be persisted if we have a way
     *   to reconnect an existing service to a new server
     */
    private final Map<Long, NotifyClient> notifyMap =
                new ConcurrentHashMap<Long, NotifyClient>();   

    /**
     * The amount of time allowed for id relocation listeners to state they
     * have completed their work.  If this time expires, the move is effectively
     * cancelled, and the identity can be moved elsewhere.
     */
    private final long relocationExpireTime;

    /** The set of identities that are in the process of moving. */
    private final Map<Identity, MoveIdTask> moveMap =
            new ConcurrentHashMap<Identity, MoveIdTask>();
    
    /**
     * Creates a new instance of NodeMappingServerImpl, called from the
     * local NodeMappingService.
     * <p>
     * The application context is resolved at construction time (rather
     * than when {@link NodeMappingServiceImpl#ready} is called), because this 
     * server will never need Managers and will not run application code.  
     * Managers are not available until {@code Service.ready} is called.
     * <p>
     * @param properties service properties
     * @param systemRegistry system registry
     * @param	txnProxy the transaction proxy
     *
     * @throws Exception if an error occurs during creation
     */
    public NodeMappingServerImpl(Properties properties, 
                                 ComponentRegistry systemRegistry,
                                 TransactionProxy txnProxy)  
         throws Exception 
    {     
        super(properties, systemRegistry, txnProxy, logger);

        logger.log(Level.CONFIG, 
                   "Creating NodeMappingServerImpl properties:{0}", 
                   properties); 
        
        watchdogService = txnProxy.getService(WatchdogService.class);
       
 	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        int requestedPort = wrappedProps.getIntProperty(
                SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
        
        String policyClassName = wrappedProps.getProperty(
		ASSIGN_POLICY_CLASS_PROPERTY);	    
        if (policyClassName == null) {
            assignPolicy = new RoundRobinPolicy(properties, this);
        } else {
            assignPolicy = wrappedProps.getClassInstanceProperty(
                ASSIGN_POLICY_CLASS_PROPERTY, NodeAssignPolicy.class,
                new Class[] { Properties.class, NodeMappingServerImpl.class }, 
                properties, this);
        }
        
        /*
         * Check service version.
         */
        transactionScheduler.runTask(
	    new AbstractKernelRunnable("CheckServiceVersion") {
                public void run() {
                    checkServiceVersion(
                        NodeMapUtil.VERSION_KEY, 
                        NodeMapUtil.MAJOR_VERSION, 
                        NodeMapUtil.MINOR_VERSION);
                }
        },  taskOwner);
        
        // Create and start the remove thread, which removes unused identities
        // from the map.
        long removeExpireTime = wrappedProps.getLongProperty(
                REMOVE_EXPIRE_PROPERTY, DEFAULT_REMOVE_EXPIRE_TIME,
                1, Long.MAX_VALUE);
        removeThread = new RemoveThread(removeExpireTime);
        removeThread.start();
        
        // Find how long we'll give listeners to say they've finished move
        // preparations.
        relocationExpireTime = wrappedProps.getLongProperty(
                RELOCATION_EXPIRE_PROPERTY, DEFAULT_RELOCATION_EXPIRE_TIME,
                1, Long.MAX_VALUE);

        // Register our node listener with the watchdog service.
        watchdogNodeListener = new Listener();
        watchdogService.addNodeListener(watchdogNodeListener);   
        
        // Export ourselves.  At this point, this object is public.
        exporter = new Exporter<NodeMappingServer>(NodeMappingServer.class);
        port = exporter.export(this, SERVER_EXPORT_NAME, requestedPort);
        if (requestedPort == 0) {
            logger.log(Level.CONFIG, "Server is using port {0,number,#}", port);
        } 
        
        fullName = "NodeMappingServiceImpl[host:" + 
                   InetAddress.getLocalHost().getHostName() + 
                   ", port:" + port + "]";
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
    protected void doReady() {
        // Do nothing.
    }
    
    /** 
     * {@inheritDoc} 
     * Called from the instantiating service.
     */
    protected void doShutdown() {
        exporter.unexport();
        try {
            if (removeThread != null) {
		synchronized (removeThread) {
		    removeThread.notifyAll();
		}
                removeThread.join();
            }
        } catch (InterruptedException e) {
            // Do nothing
        }
    }

    /**
     * Returns a string representation of this instance.
     *
     * @return	a string representation of this instance
     */
    @Override public String toString() {
	return fullName;
    }

    /* -- Implement NodeMappingServer -- */

    /** {@inheritDoc} */
    public void assignNode(Class service, Identity identity, 
                           long requestingNode)
        throws IOException 
    {
        callStarted();    
        try {
            if (identity == null) {
                throw new NullPointerException("null id");
            }
            Node node = null;   // old node assignment
            final String serviceName = service.getName();

            // Check to see if we already have an assignment.  If so, we just
            // need to update the status.  Otherwise, we need to make our
            // persistent updates and notify listeners.   
            try {
                CheckTask checkTask = new CheckTask(identity, serviceName);
                runTransactionally(checkTask);

                if (checkTask.idFound() && checkTask.isAssignedToLiveNode()) {
                    return;
                } else {
                    // The node is dead.  We need to map to a new node.
                    node = checkTask.getNode();
                }
            } catch (Exception ex) {
                // Log the failure, but continue on - treat it as though the
                // identity wasn't found.
                logger.logThrow(Level.WARNING, ex, 
                                "Lookup of {0} failed", identity);
            }

            try {
                long newNodeId = 
                    mapToNewNode(identity, serviceName, node, requestingNode);
                logger.log(Level.FINEST, 
                           "assignNode id:{0} to {1}", identity, newNodeId);
            } catch (NoNodesAvailableException ex) {
                // This should only occur if no nodes are available, which
                // can only happen if our client shutdown and unregistered
                // while we were in this call.
                // Ignore the error.
                logger.logThrow(Level.FINEST, ex, "Exception ignored");
            }
            
        } finally {
            callFinished();
        }
    }
    
    /**
     * Check for an id, and make the node available if it was
     * assigned to a failed node.   Otherwise, update the status
     * information.
     */
    private class CheckTask extends AbstractKernelRunnable {
        private final String idkey;
        private final String serviceName;
        private final Identity id;
        /** return value, was the identity found? */
        private boolean found = false;
        /** return value, was node alive? */
        private boolean isAlive = false;
        /** return value, node assignment */
        private Node node;
        CheckTask(Identity id, String serviceName) {
	    super(null);
            idkey = NodeMapUtil.getIdentityKey(id);
            this.serviceName = serviceName;
            this.id = id;
        }
        public void run() {
            try {
                IdentityMO idmo = 
		    (IdentityMO) dataService.getServiceBinding(idkey);

                found = true;
                long nodeId = idmo.getNodeId();

                node = watchdogService.getNode(nodeId);
                isAlive = (node != null && node.isAlive());
                if (!isAlive) {
                    return;
                }
                // The identity already has an assignment but we still 
                // need to update the status.  TODO functionality still
                // required?  Should assignNode not set the status?
                final String statuskey = 
                        NodeMapUtil.getStatusKey(id, nodeId, serviceName);
                dataService.setServiceBinding(statuskey, idmo);
                logger.log(Level.FINEST, "assignNode id:{0} already on {1}", 
                           id, nodeId);
            } catch (NameNotBoundException nnbe) {
                // Do nothing.  We expect this exception if the id isn't in
                // the map yet.   Found is already set to false.
                found = false;
            }
        }
        
        public boolean idFound()                { return found;   }
        public boolean isAssignedToLiveNode()   { return isAlive; }
        public Node getNode()                   { return node;    }
    }

    /** {@inheritDoc} */
    public void canMove(Identity id) throws IOException {
        callStarted();
        try {
            MoveIdTask moveTask = moveMap.remove(id);
            moveIdAndNotifyListeners(moveTask);
        } finally {
            callFinished();
        }
    }
    
    /** {@inheritDoc} */
    public void canRemove(Identity id) throws IOException {
        callStarted();
        
        try {
            removeQueue.add(new RemoveInfo(id));
            
        } finally {
            callFinished();
        }
    }
    
    /**
     * The thread that handles removing inactive identities from the map.
     * <p>
     * Candidates for removal are held in the removeQueue.  This thread
     * periodically wakes up and looks at each entry in the removeQueue.
     * If an appropriate amount of time has passed since the entry was
     * put in the removeQueue (which allows the system some settling time, 
     * so we don't thrash removing and adding an identity), the data store
     * is checked to see if the identity can still be removed.  A service
     * could have called {@link #NodeMappingService.setStatus setStatus}
     * during our waiting time, marking the identity as active, during the
     * waiting time.  If it is still appropriate to remove the identity,
     * all traces of it are removed from the data store.
     */
    private class RemoveThread extends Thread {
        private final long expireTime;   // milliseconds
        
        RemoveThread(long expireTime) { 
            super(PKG_NAME + "$RemoveThread");
            this.expireTime = expireTime;
        }
        
        public void run() {
            while (true) {
		synchronized (this) {
		    if (shuttingDown()) {
			break;
		    }
		    try {
			wait(expireTime);
		    } catch (InterruptedException ex) {
			logger.log(Level.FINE, "Remove thread interrupted");
			break;
		    }
		}
                Long time = System.currentTimeMillis() - expireTime;
                
                boolean workToDo = true;
                while (workToDo && !shuttingDown()) {
                    RemoveInfo info = removeQueue.peek();
                    if (info != null && info.getTimeInserted() < time) {
                        // Always remove the item from the list, even if we
                        // get an exception.  Otherwise, we can loop forever.
                        info = removeQueue.poll();
                        Identity id = info.getIdentity();
                        RemoveTask rtask = new RemoveTask(id);
                        try {
                            runTransactionally(rtask);
                            if (rtask.idRemoved()) {
                                notifyListeners(rtask.getNode(), null, id);
                                logger.log(Level.FINE, "Removed {0}", id);
                            }
                        } catch (Exception ex) {
                            logger.logThrow(Level.WARNING, ex, 
                                            "Removing {0} failed", id);
                        }
                    } else {
                        workToDo = false;
                    }
                }
            }
        }
    }
    
    /**
     * Immutable object representing an identity which might be removable.
     */
    private static class RemoveInfo {
        private final Identity id;
        private final long timeInserted;
        
        RemoveInfo(Identity id) {
            this.id = id;
            timeInserted = System.currentTimeMillis();
        }
        Identity getIdentity() { return id; }
        long getTimeInserted() { return timeInserted; }
    }
    
    /** 
     * Task which, under a transaction, checks that it's still appropriate
     * to remove an identity, and, if so, removes the service bindings and
     * object.
     */
    private class RemoveTask extends AbstractKernelRunnable {
        private final Identity id;
        private final String idkey;
        private final String statuskey;
        // return value, identity was found to be dead and was removed
        private boolean dead = false;
        // set if dead == true;  tells us the node the identity was removed from
        private Node node;
        
        RemoveTask(Identity id) {
	    super(null);
            this.id = id;
            idkey = NodeMapUtil.getIdentityKey(id);
            statuskey = NodeMapUtil.getPartialStatusKey(id);
        }
        
        public void run() throws Exception {
            // Check the status, and remove it if still dead.  
            String name = dataService.nextServiceBoundName(statuskey);
            dead = (name == null || !name.startsWith(statuskey));

            if (dead) {
                IdentityMO idmo;
                try {
                    idmo = (IdentityMO) dataService.getServiceBinding(idkey);
                } catch (NameNotBoundException nnbe) {
                    dead = false;
                    logger.log(Level.FINE, "{0} has already been removed", id);
                    return;
                }
                long nodeId = idmo.getNodeId();
                node = watchdogService.getNode(nodeId);
                // Remove the node->id binding.  
                String nodekey = NodeMapUtil.getNodeKey(nodeId, id);
                dataService.removeServiceBinding(nodekey);

                // Remove the id->node binding, and the object.
                dataService.removeServiceBinding(idkey);
                dataService.removeObject(idmo);
            }
        }
        
        /** Returns {@code true} if the identity was removed. */
        boolean idRemoved() {
            return dead;
        }
        /** Returns the node the identity was removed from, which can be
         *  null if the node has failed and been removed from the data store.
         */
        Node getNode() {
            return node;
        } 
    }
    
    /** {@inheritDoc} */
    public void registerNodeListener(NotifyClient client, long nodeId) 
        throws IOException
    {
        callStarted();
        
        try {
            notifyMap.put(nodeId, client);
            assignPolicy.nodeStarted(nodeId);
            logger.log(Level.FINEST, 
                       "Registered node listener for {0} ", nodeId);
        } finally {
            callFinished();
        }
    }
    
    /**
     * {@inheritDoc}
     * Also called internally when we hear a node has died.
     */
    public void unregisterNodeListener(long nodeId) throws IOException {
        callStarted();
        
        try {
            // Tell the assign policy to stop assigning to the node
            assignPolicy.nodeStopped(nodeId);
            notifyMap.remove(nodeId);
            logger.log(Level.FINEST, 
                       "Unregistered node listener for {0} ", nodeId);
        } finally {
            callFinished();
        }
    }    
    
    // TODO Perhaps will want to batch notifications.
    private void notifyListeners(final Node oldNode, final Node newNode,
                                 final Identity id)
    {
        logger.log(Level.FINEST, "In notifyListeners, identity: {0}, " +
                               "oldNode: {1}, newNode: {2}", 
                               id, oldNode, newNode);
        if (oldNode != null) {
            final NotifyClient oldClient = notifyMap.get(oldNode.getId());
            if (oldClient != null) {
                runIoTask(
                    new IoRunnable() {
                        public void run() throws IOException {
                            oldClient.removed(id, newNode);
                        }
                    }, oldNode.getId());
            }
        }
        
        if (newNode != null) {
            final NotifyClient newClient = notifyMap.get(newNode.getId());
            if (newClient != null) {
                runIoTask(
                    new IoRunnable() {
                        public void run() throws IOException {
                            newClient.added(id, oldNode);
                        }
                    }, newNode.getId());
            }
        }
    }
    
    /** {@inheritDoc} */
    public boolean assertValid(Identity identity) throws Exception {
        callStarted();
        
        try {
            AssertTask atask = new AssertTask(identity, dataService);
            runTransactionally(atask);
            return atask.allOK();  
        } finally {
            callFinished();
        }
    }
    
    /**
     * Returns the port being used for this server.
     *
     * @return  the port
     */
    int getPort() {
        return port;
    }
    
    
    /**
     *  Run the given task synchronously, and transactionally, retrying
     *  if the exception is of type <@code ExceptionRetryStatus>.
     * @param task the task
     */
    void runTransactionally(KernelRunnable task) throws Exception {   
        transactionScheduler.runTask(task, taskOwner);
    }
    
    /**
     * Move an identity.  First, choose a new node for the identity
     * (which can take a while) and then update the map to reflect
     * the choice, cleaning up old mappings as appropriate.  If given
     * a {@code serviceName}, the status of the identity is set to active
     * for that service on the new node.  The change in mappings might need
     * to wait for registered {@code IdentityRelocationListener}s to
     * prepare for the move.
     *
     * @param id the identity to map to a new node
     * @param serviceName the name of the requesting service's class, or null
     * @param oldNode the last node the identity was mapped to, or null if there
     *        was no prior mapping
     * @param requestingNode the node making the mapping request
     *
     * @throws NoNodesAvailableException if there are no live nodes to map to
     */
    long mapToNewNode(final Identity id, String serviceName, Node oldNode,
                      long requestingNode) 
        throws NoNodesAvailableException
    {
        assert (id != null);
        
        // First, check to see if we're already trying to move this identity
        // and we haven't gone past the expire time.
        // If so, just return the node we're trying to move it to.
        MoveIdTask moveTask = moveMap.get(id);
        if (moveTask != null) {
            if (System.currentTimeMillis() < moveTask.expireTime) {
                return moveTask.newNodeId;
            } else {
                // We've expired.  Clean up our data structures.  The service
                // side will know of the expiration because it will receive
                // a second request to move of the same identity.
                moveMap.remove(id);
            }
        }
        
        // Choose the node.  This needs to occur outside of a transaction,
        // as it could take a while.  
        final long newNodeId;
        try {
            newNodeId = assignPolicy.chooseNode(id, requestingNode);
        } catch (NoNodesAvailableException ex) {
            logger.logThrow(Level.FINEST, ex, "mapToNewNode: id {0} from {1}" +
                    " failed because no live nodes are available", 
                    id, oldNode);
            throw ex;
        }
        
        if (oldNode != null && newNodeId == oldNode.getId()) {
            // We picked the same node.  This might be OK - the system might
            // only have one node, or the current node might simply be the
            // best one available.
            //
            // TBD - we might want a method on chooseNode which explicitly
            // excludes the current node, and returns something (a negative
            // number?) if there is no other choice.
            return newNodeId;
        }
        
        // Create a new task with the move information.
        moveTask = new MoveIdTask(id, oldNode, newNodeId, serviceName);
        
        if (oldNode != null && oldNode.isAlive()) {
            // Tell the id's old node, so it can tell the id relocation
            // listeners.  We won't actually move the identity until the
            // listeners have all responded, can canMove is called.
            moveMap.put(id, moveTask);
            long oldId = oldNode.getId();
            final NotifyClient oldClient = notifyMap.get(oldId);
            if (oldClient != null) {
                runIoTask(
                    new IoRunnable() {
                        public void run() throws IOException {
                            oldClient.prepareRelocate(id, newNodeId);
                        }
                    }, oldId);
            }
        } else {
            // Go ahead and make the move now.
            moveIdAndNotifyListeners(moveTask);
        }
        return newNodeId;
    }
    
    private void moveIdAndNotifyListeners(MoveIdTask moveTask) {
        if (moveTask == null) {
            // There's nothing to do.
            return;
        }
        Identity id = moveTask.id;
        final Node oldNode = moveTask.oldNode;
        final long newNodeId = moveTask.newNodeId;
        try {
            runTransactionally(moveTask); 
            GetNodeTask atask = new GetNodeTask(newNodeId);
            runTransactionally(atask);

            // Tell our listeners
            notifyListeners(oldNode, atask.getNode(), id);
        } catch (Exception e) {
            // We can get an IllegalStateException if this server shuts
            // down while we're moving identities from failed nodes.
            // TODO - check that those identities are properly removed.
            // Hmmm.  we've probably left some garbage in the data store.
            // The most likely problem is one in our own code.
            logger.logThrow(Level.FINE, e, 
                            "Move {0} mappings from {1} to {2} failed", 
                            id, oldNode, newNodeId);
        }
    }
    
    private class MoveIdTask extends AbstractKernelRunnable {
        final Identity id;
        final Node oldNode;
        final long newNodeId;
        final long expireTime;
        // Calculate the lookup keys for both the old and new nodes.
        // The id key is the same for both old and new.
        private final String idkey;
        
        // The oldNode will be null if this is the first assignment.
        private final String oldNodeKey;
        private final String oldStatusKey;

        private final String newNodekey;
        private final String newStatuskey;
        
        private final IdentityMO newidmo;
        MoveIdTask(Identity id, Node oldNode, long newNodeId, 
                   String serviceName) 
        {
            super(null);
            this.id = id;
            this.oldNode = oldNode;
            this.newNodeId = newNodeId;
            expireTime = System.currentTimeMillis() + relocationExpireTime;
            // Calculate the lookup keys for both the old and new nodes.
            // The id key is the same for both old and new.
            idkey = NodeMapUtil.getIdentityKey(id);

            // The oldNode will be null if this is the first assignment.
            oldNodeKey = (oldNode == null) ? null :
                NodeMapUtil.getNodeKey(oldNode.getId(), id);
            oldStatusKey = (oldNode == null) ? null :
                NodeMapUtil.getPartialStatusKey(id, oldNode.getId());

            newNodekey = NodeMapUtil.getNodeKey(newNodeId, id);
            newStatuskey = (serviceName == null) ? null :
                    NodeMapUtil.getStatusKey(id, newNodeId, serviceName);

            newidmo = new IdentityMO(id, newNodeId);
        }
        
        public void run() {
            // First, we clean up any old mappings.
            if (oldNode != null) {
                try {
                    // Find the old IdentityMO, with the old node info.
                    IdentityMO oldidmo = (IdentityMO)
                        dataService.getServiceBinding(idkey);

                    // Check once more for the assigned node - someone
                    // else could have mapped it before we got here.
                    // If so, just return.
                    if (oldidmo.getNodeId() != oldNode.getId()) {
                        return;
                    }

                    //Remove the old node->id key.
                    dataService.removeServiceBinding(oldNodeKey);

                    // Remove the old status information.  We don't 
                    // retain any info about the old node's status.
                    Iterator<String> iter =
                        BoundNamesUtil.getServiceBoundNamesIterator(
                            dataService, oldStatusKey);
                    while (iter.hasNext()) {
                        iter.next();
                        iter.remove();
                    }
                    // Remove the old IdentityMO with the old node info.
                    dataService.removeObject(oldidmo);
                } catch (NameNotBoundException e) {
                    // The identity was removed before we could
                    // reassign it to a new node.
                    // Simply make the new assignment, as if oldNode
                    // was null to begin with.
                }
            }
            // Add (or update) the id->node mapping. 
            dataService.setServiceBinding(idkey, newidmo);
            // Add the node->id mapping
            dataService.setServiceBinding(newNodekey, newidmo);
            // Reference count
            if (newStatuskey != null) {
                dataService.setServiceBinding(newStatuskey, newidmo);
            } else {
                // This server has started the move, either through
                // a node failure or load balancing.  Add the identity
                // to the remove list so we will notice if the client
                // never logs back in.
                try {
                    canRemove(newidmo.getIdentity());
                } catch (IOException ex) {
                    // won't happen;  this is a local call
                }
            }
        }
    }
    
    private class GetNodeTask extends AbstractKernelRunnable {
        /** Return value, the new node.  Must be obtained under transaction. */
        private Node node = null;
                        
        private final long nodeId;
                            
        GetNodeTask(long nodeId) {
	    super(null);
            this.nodeId = nodeId; 
        }               
                    
        public void run() {
            node = watchdogService.getNode(nodeId);
        }           
                             
        /**             
         * Returns the node found by the watchdog service, or null if
         * this task has not run or the node has failed and been removed
         * from the data store.
         */
        public Node getNode() {
            return node;
        }
    }    
        
    /** 
     * The listener registered with the watchdog service.  These methods
     * will be notified if a node starts or stops.
     */
    private class Listener implements NodeListener {    
        Listener() {
            
        }
        
        /** {@inheritDoc} */
        public void nodeStarted(Node node) {
            // Do nothing.  We find out about nodes being available when
            // our client services register with us.     
        }
        
        /** {@inheritDoc} */
        public void nodeFailed(Node node) {
            long nodeId = node.getId();          
            try {
                // Remove the service node listener for the node and tell
                // the assign policy.
                unregisterNodeListener(nodeId);
            } catch (IOException ex) {
                // won't happen, this is a local call
            }
            
            // Look up each identity on the failed node and move it
            String nodekey = NodeMapUtil.getPartialNodeKey(nodeId);
            GetIdOnNodeTask task = 
                    new GetIdOnNodeTask(dataService, nodekey, logger);
            
            while (true) {
                // Break out of the loop if we're shutting down.
                if (shuttingDown()) {
                    break;
                }
                try {
                    // Find an identity on the node
                    runTransactionally(task);
                
                    // Move it, removing old mapping
                    if (!task.done()) {
                        Identity id = task.getId().getIdentity();
                        try {
                            // If we're already trying to move the identity,
                            // but the old node failed before preparations are
                            // complete, just make the move now.
                            MoveIdTask moveTask = moveMap.remove(id);
                            if (moveTask != null) {
                                moveIdAndNotifyListeners(moveTask);
                            } else {
                                mapToNewNode(id, null, node, 
                                         NodeAssignPolicy.SERVER_NODE);
                            }
                        } catch (NoNodesAvailableException e) {
                            // This can be thrown from mapToNewNode if there are
                            // no live nodes.  Stop our loop.
                            //
                            // TODO - not convinced this is correct.
                            // I think the task service needs a positive
                            // action here.  I think I need to keep a list
                            // somewhere of failed nodes, and have a background
                            // thread that tries to move them.
                            removeQueue.add(new RemoveInfo(id));
                            break;
                        }
                    } else {
                        break;
                    }
                } catch (Exception ex) {
                    logger.logThrow(Level.WARNING, ex, 
                        "Failed to move identity {0} from failed node {1}", 
                        task.getId(), node);
                    break;
                }
            }
        }
    }
    
    /**
     *  Task to support node failure, run under a transaction.
     *  Finds an identity that was on the failed node.  Code outside
     *  the transaction moves the identity to another node and removes
     *  the old id<->failedNode mapping, and any status information.
     */
    private static class GetIdOnNodeTask extends AbstractKernelRunnable {
        /** Set to true when no more identities to be found */
        private boolean done = false;
        /** If !done, the identity we were looking for */
        private IdentityMO idmo = null;

        private final DataService dataService;
        private final String nodekey;
        private final LoggerWrapper logger;
        
        GetIdOnNodeTask(DataService dataService, 
                        String nodekey, LoggerWrapper logger) 
        {
	    super(null);
            this.dataService = dataService;
            this.nodekey = nodekey;
            this.logger = logger;
        }
        
        public void run() {
            try {
                String key = dataService.nextServiceBoundName(nodekey);
                done = (key == null || !key.contains(nodekey));
                if (!done) {
                    idmo = (IdentityMO) dataService.getServiceBinding(key);
                }
            } catch (Exception e) {
                // XXX: this kind of check may need to be applied to more
                // of the exceptions in the class, so all exception handling
                // should be reviewed
                if ((e instanceof ExceptionRetryStatus) &&
                    (((ExceptionRetryStatus) e).shouldRetry())) 
                {
                    return;
                }
                done = true;
                logger.logThrow(Level.WARNING, e, 
                        "Failed to get key or binding for {0}", nodekey);
            }
        }
        
        /**
         * Returns true if there are no more identities to be found.
         * @return {@code true} if no more identities could be found for the 
         *          node, {@code false} otherwise.
         */
        public boolean done() {
            return done;
        }
        
        /**
         *  The identity MO retrieved from the data store, or null if
         *  the task has not yet executed or there was an error while
         *  executing.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
    }   
    
    
    
    /* -- Methods to assist in testing and verification -- */
    
    /**
     * Get the node an identity is mapped to.
     * Used for testing.
     *
     * @param id the identity
     * @return the node the identity is mapped to
     *
     * @throws Exception if any error occurs
     */
    long getNodeForIdentity(Identity id) throws Exception {
        String idkey = NodeMapUtil.getIdentityKey(id);
        GetIdTask idtask = new GetIdTask(dataService, idkey);
        runTransactionally(idtask);
        IdentityMO idmo = idtask.getId();
        return idmo.getNodeId();
    }

    /**
     * Task which gets an IdentityMO from a data service.  This is
     * a separate task so we can retrieve the result.  An exception
     * will be thrown if the IdentityMO is not found or the name
     * binding doesn't exist.
     */
    static class GetIdTask extends AbstractKernelRunnable {
        private IdentityMO idmo = null;
        private final DataService dataService;
        private final String idkey;
        
        /**
         * Create a new instance.
         *
         * @param dataService the data service to retrieve from
         * @param idkey Identitifier key
         */
        GetIdTask(DataService dataService, String idkey) {
	    super(null);
            this.dataService = dataService;
            this.idkey = idkey;
        }
        
        /**
         * {@inheritDoc}
         * Get the IdentityMO. 
         * @throws NameNotBoundException if no object is bound to the id
         * @throws ObjectNotFoundException if the object has been removed
         */
        public void run() {
            idmo = (IdentityMO) dataService.getServiceBinding(idkey);
        }
        
        /**
         *  The identity MO retrieved from the data store, or null if
         *  the task has not yet executed or there was an error while
         *  executing.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
    }

    /**
     * Return the data store keys found for a particular identity.
     * Used for testing.
     *
     * @param identity the identity
     * @return the set of service name bindings found for that identity
     *
     * @throws Exception if any error occurs
     */
    Set<String> reportFoundKeys(Identity identity) throws Exception {
        AssertTask atask = new AssertTask(identity, dataService);
        runTransactionally(atask);
        return atask.found();    
    }

    /**
     * Task to assert some invariants about our use of the data store
     * are true.  Assumes that we are in a transaction.
     */
    private static class AssertTask extends AbstractKernelRunnable {

        private final Identity id;
        private final DataService dataService;
        private final String idkey;
        private final String statuskey;
        private final int statuskeylen;

        // Return values
        private boolean ok = true;
        private Set<String> foundKeys = new HashSet<String>();

        AssertTask(Identity id, DataService dataService) {
	    super(null);
            this.id = id;
            this.dataService = dataService;
            idkey = NodeMapUtil.getIdentityKey(id);
            statuskey = NodeMapUtil.getPartialStatusKey(id);
            statuskeylen = statuskey.length();
        }

        public void run() {
            // Assert that the data store map seems valid for an identity.
            // If we can find the id->node map, be sure that:
            //    there is also a node->id bound name
            //    we cannot find any other node->id names (might be hard/long)
            //    if there are any status records, they are only for the node
            // If we cannot find the id->node map, be sure that:
            //    we cannot find an node->id mapping (might be hard/long)
            //    we cannot find a status record for status.id
            IdentityMO idmo = null;
            try {
                // Look for the identity in the map.
                idmo = (IdentityMO) dataService.getServiceBinding(idkey);
                foundKeys.add(idkey);
            } catch (NameNotBoundException e) {
                // Do nothing: leave idmo as null to indicate not found
            }
            if (idmo != null) {
                long nodeId = idmo.getNodeId();
                final String nodekey = NodeMapUtil.getNodeKey(nodeId, id);

                try {
		    dataService.getServiceBinding(nodekey);
                    foundKeys.add(nodekey);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, 
                            "Did not find expected mapping for {0}", nodekey);
                    ok = false;
                }
                
                // Not yet checking that we can't find any other node->id
                // bindings.
                
                // Check status
                Iterator<String> iter =
                    BoundNamesUtil.getServiceBoundNamesIterator(
                        dataService, statuskey);

                while (iter.hasNext()) {
                    String key = iter.next();
                    foundKeys.add(key);
                    String subkey = key.substring(statuskeylen);
                    if (!subkey.startsWith(String.valueOf(nodeId))) {
                        logger.log(Level.SEVERE, 
                            "Found unexpected mapping for {0}", key);
                        ok = false;
                    }      
                }
            } else {
                // Not checking all nodes to make sure not mapped yet...
                Iterator<String> iter =
                    BoundNamesUtil.getServiceBoundNamesIterator(
                        dataService, statuskey);

                while (iter.hasNext()) {
                    String key = iter.next();
                    foundKeys.add(key);
                    logger.log(Level.SEVERE, 
                            "Found unexpected mapping for {0}", key);
                    ok = false;

                }
            }

        }
        
        boolean allOK() {
            return ok;
        }

        Set<String> found() {
            return foundKeys;
        }
    }
}
    
