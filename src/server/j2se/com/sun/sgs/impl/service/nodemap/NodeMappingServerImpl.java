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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.NodeMappingService;
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
 *      The mimimum time, in milliseconds, that this server will wait before
 *      removing a potentially inactive identity from the map.   This value
 *      must be greater than {@code 0}.   Shorter expiration times cause the
 *      map to be cleaned up more frequently, potentially causing more
 *      {@link NodeMappingService#assignNode(Class, Identity) assignNode} 
 *      calls;  longer expiration times will increase the chance that an 
 *      identity will become active again before it can be removed. <p>
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
public class NodeMappingServerImpl implements NodeMappingServer {
    /** Package name for this class. */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /** The property name for the server port. */
    static final String SERVER_PORT_PROPERTY = PKG_NAME + ".server.port";

    /** The default value of the server port. */
    // TODO:  does the exporter allow all servers to use the same port?
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
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME + ".server"));
    
    /** The port we've been exported on. */
    private final int port;
    
    /** The exporter for this server */
    private final Exporter<NodeMappingServer> exporter;

    /** The task scheduler, for our transactional, synchronous tasks. */
    private final TaskScheduler taskScheduler;
    
    /** The proxy owner for our transactional, synchronous tasks. */
    private final TaskOwner taskOwner;
    
    /** The data service. */
    final DataService dataService;
    
    /** The watchdog service. */
    final WatchdogService watchdogService;
    
    /** The policy for assigning new nodes.  This will likely morph into
     *  the load balancing policy, as well. */
    private final NodeAssignPolicy assignPolicy;

    /** The thread that removes inactive identities */
    // TODO:  should this be a TaskScheduler.scheduleRecurringTask? Or
    // maybe called via ResourceCoordinator.startTask?
    private final Thread removeThread;
    
     /** Our watchdog node listener. */
    private final NodeListener watchdogNodeListener;
    
    /** Lock object for service state */
    private final Object stateLock = new Object();
    
    /** The possible states of this instance. */
    enum State {
	/** After ready call and before shutdown */
	RUNNING,
	/** After start of a call to shutdown and before call finishes */
	SHUTTING_DOWN,
	/** After shutdown has completed successfully */
	SHUTDOWN
    }

    /** The current state of this instance. */
    private State state;
    
    /** The count of calls in progress, protected by stateLock. */
    private int callsInProgress = 0;
    
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
        if (systemRegistry == null) {
            throw new NullPointerException("null system registry");
        } else if (txnProxy == null) {
            throw new NullPointerException("null transaction proxy");
        }
        
        logger.log(Level.CONFIG, 
                   "Creating NodeMappingServerImpl properties:{0}", properties); 
        
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        dataService = txnProxy.getService(DataService.class);
        watchdogService = txnProxy.getService(WatchdogService.class);
        taskOwner = txnProxy.getCurrentOwner();
       
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
        
        // Restore any old data from the data service.
        runTransactionally(
                new AbstractKernelRunnable() {
                    public void run() {
                        NodeMapUtil.handleDataVersion(dataService, logger);
                    }
                });
        
        // Create and start the remove thread, which removes unused identities
        // from the map.
        long removeExpireTime = wrappedProps.getLongProperty(
                REMOVE_EXPIRE_PROPERTY, DEFAULT_REMOVE_EXPIRE_TIME,
                1, Long.MAX_VALUE);
        removeThread = new RemoveThread(removeExpireTime);
        removeThread.start();
        
        // Register our node listener with the watchdog service.
        watchdogNodeListener = new Listener();
        watchdogService.addNodeListener(watchdogNodeListener);   
        
        synchronized(stateLock) {
            state = State.RUNNING;
        }
        
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
    
    /*- service like methods, which support lifecycle activities -*/
    
    /** 
     * Shut down this server.  Called from the instantiating service.
     * 
     * @return {@code true} if everything shut down cleanly
     */
    boolean shutdown() {
        logger.log(Level.FINEST, "Shutting down");
        synchronized(stateLock) {
            state = State.SHUTTING_DOWN;
            while (callsInProgress > 0) {
		try {
		    stateLock.wait();
		} catch (InterruptedException e) {
		    return false;
		}
	    }
        }

        boolean ok = exporter.unexport();
        try {
        if (removeThread != null) {
            removeThread.interrupt();
            removeThread.join();
        }
        } catch (InterruptedException e) {
            logger.logThrow(Level.WARNING, e, "Failure while shutting down");
            ok = false;
        }

        synchronized(stateLock) {
            state = State.SHUTDOWN;
        }

        return ok;
    }

    /**
     * Returns a string representation of this instance.
     *
     * @return	a string representation of this instance
     */
    @Override
    public String toString() {
	return fullName;
    }
    
    /**
     * Increments the number of calls in progress.  This method should
     * be invoked by remote methods to both increment in progress call
     * count and to check the state of this server.  When the call has
     * completed processing, the remote method should invoke {@link
     * #callFinished callFinished} before returning.
     *
     * @param check {@code true} if we should check the state
     * @throws	IllegalStateException if this service is not configured
     *		or is shutting down
     */
    private void callStarted(boolean check) {
	synchronized (stateLock) {
            if (check && state != State.RUNNING) {
		throw new IllegalStateException("service not running");
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
	synchronized (stateLock) {
	    callsInProgress--;
	    if (state == State.SHUTTING_DOWN && callsInProgress == 0) {
		stateLock.notifyAll();
	    }
	}
    }
    
    /* -- Implement NodeMappingServer -- */

    /** {@inheritDoc} */
    public void assignNode(Class service, Identity identity) throws IOException 
    {
        callStarted(true);    
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
                long newNodeId = mapToNewNode(identity, serviceName, node);
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
            idkey = NodeMapUtil.getIdentityKey(id);
            this.serviceName = serviceName;
            this.id = id;
        }
        public void run() {
            try {
                IdentityMO idmo = 
                    dataService.getServiceBinding(idkey, IdentityMO.class);

                found = true;
                long nodeId = idmo.getNodeId();

                isAlive = watchdogService.getNode(nodeId).isAlive();
                if (!isAlive) {
                    // Caller can get result from getNodeId()
                    node = watchdogService.getNode(nodeId);
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
    public void canRemove(Identity id) throws IOException {
        callStarted(true);
        
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
     * <p>
     * NOTE: this thread is still not correct in the face of interrupts:
     * the logging code is known to swallow the interrupted exception
     * sometimes.  InterruptedException clears the interrupt status,
     * so checking isInterrupted() doesn't tell us if the thread has 
     * <b>ever</b> been interrupted.
     */
    private class RemoveThread extends Thread {
        private final long expireTime;   // milliseconds
        
        RemoveThread(long expireTime) { 
            super(PKG_NAME + "$RemoveThread");
            this.expireTime = expireTime;
        }
        
        public void run() {
            while (!isInterrupted()) {
                try {
                    sleep(expireTime);
                } catch (InterruptedException ex) {
                    logger.log(Level.FINE, "Remove thread interrupted");
                    break;
                }
                
                Long time = System.currentTimeMillis() - expireTime;
                
                boolean workToDo = true;
                while (workToDo && !isInterrupted()) {
                    RemoveInfo info = removeQueue.peek();
                    if (info != null && info.getTimeInserted() < time) {
                        // Always remove the item from the list, even if we
                        // get an exception.  Otherwise, we can loop forever.
                        info = removeQueue.poll();
                        Identity id = info.getIdentity();
                        RemoveTask rtask = new RemoveTask(id);
                        try {
                            runTransactionally(rtask);
                            if (rtask.isDead()) {
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
            this.id = id;
            idkey = NodeMapUtil.getIdentityKey(id);
            statuskey = NodeMapUtil.getPartialStatusKey(id);
        }
        
        public void run() throws Exception {
            // Check the status, and remove it if still dead.  
            String name = dataService.nextServiceBoundName(statuskey);
            dead = (name == null || !name.startsWith(statuskey));

            if (dead) {
                IdentityMO idmo = 
                        dataService.getServiceBinding(idkey, IdentityMO.class);
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
        boolean isDead() {
            return dead;
        }
        /** Returns the node id for the node the identity was removed from. */
        Node getNode() {
            return node;
        } 
    }
    
    /** {@inheritDoc} */
    public void registerNodeListener(NotifyClient client, long nodeId) 
        throws IOException
    {
        // OK to call any time
        callStarted(false);
        
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
        // OK to call at any time
        callStarted(false);
        
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
    
    // TODO Perhaps need to have a separate thread do this work.
    // TODO Perhaps will want to batch notifications.
    private void notifyListeners(Node oldNode, Node newNode, Identity id) {
        logger.log(Level.FINEST, "In notifyListeners, identity: {0}, " +
                               "oldNode: {1}, newNode: {2}", 
                               id, oldNode, newNode);
        if (oldNode != null) {
            NotifyClient oldClient = notifyMap.get(oldNode.getId());
            if (oldClient != null) {
                try {
                    oldClient.removed(id, newNode);
                } catch (IOException ex) {
                    logger.logThrow(Level.WARNING, ex, 
                            "A communication error occured while notifying" +
                            " node {0} that {1} has been removed", 
                            oldClient, id);
                }
            }
        }
        
        if (newNode != null) {
            NotifyClient newClient = notifyMap.get(newNode.getId());
            if (newClient != null) {
                try {
                    newClient.added(id, oldNode);
                } catch (IOException ex) {
                    logger.logThrow(Level.WARNING, ex, 
                            "A communication error occured while notifying" +
                            " node {0} that {1} has been added", newClient, id);
                }
            }
        }
    }
    
    /** {@inheritDoc} */
    public boolean assertValid(Identity identity) throws Exception {
        callStarted(true);
        
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
        taskScheduler.runTransactionalTask(task, taskOwner);
    }
    
    /**
     * Move an identity.  First, choose a new node for the identity
     * (which can take a while) and then update the map to reflect
     * the choice, cleaning up old mappings as appropriate.  If given
     * a {@code serviceName}, the status of the identity is set to active
     * for that service on the new node.
     *
     * @param id the identity to map to a new node
     * @param serviceName the name of the requesting service's class, or null
     * @param old the last node the identity was mapped to, or null if there
     *        was no prior mapping
     *
     * @throws NoNodesAvailableException if there are no live nodes to map to
     */
    long mapToNewNode(Identity id, String serviceName, Node old) 
        throws NoNodesAvailableException
    {
        assert(id != null);
        
        // Choose the node.  This needs to occur outside of a transaction,
        // as it could take a while.  
        final Node oldNode = old;
        final long newNodeId;
        try {
            newNodeId = assignPolicy.chooseNode(id);
        } catch (NoNodesAvailableException ex) {
            logger.logThrow(Level.WARNING, ex, "mapToNewNode: id {0} from {1}"
                    + " failed because no live nodes are available", 
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
        
        // Calculate the lookup keys for both the old and new nodes.
        // The id key is the same for both old and new.
        final String idkey = NodeMapUtil.getIdentityKey(id);
        
        // The oldNode will be null if this is the first assignment.
        final String oldNodeKey = (oldNode == null) ? null :
            NodeMapUtil.getNodeKey(oldNode.getId(), id);
        final String oldStatusKey = (oldNode == null) ? null :
            NodeMapUtil.getPartialStatusKey(id, oldNode.getId());

        final String newNodekey = NodeMapUtil.getNodeKey(newNodeId, id);
        final String newStatuskey = (serviceName == null) ? null :
                NodeMapUtil.getStatusKey(id, newNodeId, serviceName);
        
        final IdentityMO newidmo = new IdentityMO(id, newNodeId);
        
        try {
            runTransactionally(new AbstractKernelRunnable() {
                public void run() {                   
                    // First, we clean up any old mappings.
                    if (oldNode != null) {
                        // Find the old IdentityMO, with the old node info.
                        IdentityMO oldidmo = 
                            dataService.getServiceBinding(idkey, 
                                                          IdentityMO.class);
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
                    
                }});
                
            GetNodeTask atask = new GetNodeTask(newNodeId);
            runTransactionally(atask);

            // Tell our listeners
            notifyListeners(oldNode, atask.getNode(), id);
        } catch (Exception e) {
            // Hmmm.  we've probably left some cruft in the data store.
            // The most likely problem is one in our own code.
            logger.logThrow(Level.WARNING, e, 
                            "Move {0} mappings from {1} to {2} failed", 
                            id, oldNode.getId(), newNodeId);
        }

        return newNodeId;
    }
    
    private class GetNodeTask extends AbstractKernelRunnable {
        /** Return value, the new node.  Must be obtained under transaction. */
        private Node node = null;
                        
        private final long nodeId;
                            
        GetNodeTask(long nodeId) {
            this.nodeId = nodeId; 
        }               
                    
        public void run() {
            node = watchdogService.getNode(nodeId);
        }           
                             
        /**             
         * Returns the node found by the watchdog service, or null if
         * this task has not run.
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
        public void nodeStarted(Node node){
            // Do nothing.  We find out about nodes being available when
            // our client services register with us.     
        }
        
        /** {@inheritDoc} */
        public void nodeFailed(Node node){
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
            
            boolean done = false;
            while (!done) {
                synchronized(stateLock) {
                    // Break out of the loop if we're shutting down.
                    if (state != State.RUNNING) {
                        done = true;
                        break;
                    }
                }
                try {
                    // Find an identity on the node
                    runTransactionally(task);
                    done = task.done();
                
                    // Move it, removing old mapping
                    if (!done) {
                        Identity id = task.getId().getIdentity();
                        try {
                            mapToNewNode(id, null, node);
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
                            done = true;
                        }
                    }
                
                } catch (Exception ex) {
                    done = true;
                    logger.logThrow(Level.WARNING, ex, 
                        "Failed to move identity {0} from failed node {1}", 
                        task.getId(), node);
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
            this.dataService = dataService;
            this.nodekey = nodekey;
            this.logger = logger;
        }
        
        public void run() {
            try {
                String key = dataService.nextServiceBoundName(nodekey);
                done = (key == null || !key.contains(nodekey));
                if (!done) {
                    idmo = dataService.getServiceBinding(key, IdentityMO.class);
                }
            } catch (Exception e) {
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
     * Add a node.  This is useful for server testing, when we
     * haven't instantiated a service.
     * <p>
     * TODO:  remove this, using a NodeAssignPolicy instead?
     *
     * @param nodeId the node id of the fake node
     */
    void addDummyNode(long nodeId)  {
        assignPolicy.nodeStarted(nodeId);
    }
    
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
            idmo = dataService.getServiceBinding(idkey, IdentityMO.class);
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
                idmo = dataService.getServiceBinding(idkey, IdentityMO.class);
                foundKeys.add(idkey);
            } catch (Exception e) {
                // Do nothing: leave idmo as null to indicate not found
            }
            if (idmo != null) {
                long nodeId = idmo.getNodeId();
                final String nodekey = NodeMapUtil.getNodeKey(nodeId, id);

                try {
                    dataService.getServiceBinding(nodekey, IdentityMO.class);
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
    
