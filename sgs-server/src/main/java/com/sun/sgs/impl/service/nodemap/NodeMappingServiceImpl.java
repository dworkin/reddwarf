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

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.management.NodeMappingServiceMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.IdentityRelocationListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;

/**
 * Maps Identities to Nodes.
 * <p>
 * The {@link #NodeMappingServiceImpl constructor} supports the
 * following properties: 
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.host
 *	</b></code><br>
 *	<i>Default:</i> the value of the {@code com.sun.sgs.server.host}
 *	property, if present, or {@code localhost} if this node is starting the 
 *      server <br>
 *
 * <dd style="padding-top: .5em">The name of the host running the {@code
 *	NodeMappingServer}. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44535}
 *
 * <dd style="padding-top: .5em">The network port for the {@code
 *	NodeMappingServer}.  This value must be no less than {@code 0} and no
 *	greater than {@code 65535}.  The value {@code 0} can only be specified
 *	if the {@code com.sun.sgs.node.type} property is not {@code appNode},
 *	and means that an anonymous port will be chosen for
 *	running the server. <p>
 *
 *  <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.client.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 0} (anonymous port)
 *
 * <dd style="padding-top: .5em">The network port for the this service for
 *      receiving node mapping changes on this node from the 
 *      {@code NodeMapppingServer}. This value must be no less than {@code 0} 
 *      and no greater than {@code 65535}.   <p>
 * </dl> 
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.class
 *	</b></code> <br>
 *	<i>Default:</i>
 *	<code>com.sun.sgs.impl.service.nodemap.NodemappingServerImpl</code>
 *
 * <dd style="padding-top: .5em">
 *      The name of the class that implements {@link
 *	NodeMappingServer}, the global server for this service. The class 
 *      should be public, not abstract, and should provide a public constructor
 *      with parameters {@link Properties} and {@link ComponentRegistry}. 
 *      Being able to specify this class is useful for testing.  <p>
 * <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.nodemap</code> to log
 * information at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization failures
 * <li> {@link Level#CONFIG CONFIG} - Construction information
 * <li> {@link Level#WARNING WARNING} - Errors
 * <li> {@link Level#FINEST FINEST} - Trace operations
 * </ul> <p>
 */
public class NodeMappingServiceImpl 
        extends AbstractService
        implements NodeMappingService 
{
    // Design notes:  
    //
    // The node mapping service maps identities to nodes.  When an identity
    // is mapped to a node, resources required for that identity will be
    // used on the node (for example, a client would log into that node, and
    // tasks run on behalf of the client would run on that node).  
    //
    // Instances of the node mapping service run on each node, and are backed 
    // by a single global node mapping server.
    // The service maps identities to available nodes in the system.
    //
    // The server is responsible, generally, for adding and removing
    // map entries, as well as modifying map entries by moving identities
    // to another node.  These moves would occur because of node failure
    // or for load balancing (load balancing is not yet designed or 
    // implemented - but the part of the system that assigns identities
    // to a node must be certain to work in concert with the part of the
    // system that is making load balancing decisions).
    // 
    // Identities are added to the map through an assignNode call.  This
    // method causes the server to find an optimal node assignment, or does
    // nothing if the identity is already assigned to a node.
    // 
    // Identities are removed from the map through a simple reference counting
    // scheme:  when a service knows they are using an entry, they call
    // setStatus with active=true, and when they are done with it, they
    // call setStatus with active=false.  Each service on each node is 
    // tracked;  when all services on a node who expressed interest in
    // an identity say they believe the identity is not active, the identity
    // is considered inactive on the node.  When an identity is inactive
    // on all nodes, it is eligible to be removed from the map.  The actual
    // removal from the map occurs at some point in the future (controlled
    // by a property), giving time for any service to say they are interested
    // in the identity again.
    // 
    // AssignNode has the side effect of calling setStatus for the 
    // calling service on the assigned node.  If we did not have this 
    // behavior, we could persist less information about per-node status:
    // the global server would only need to know if *any* service on a node
    // believes an identity is active, and the local nodes could transiently
    // maintain a set of services voting active for a particular identity.
    // We might want to look into having the server communicate with the
    // client nodes about setStatus calls it performs on the client's behalf,
    // as we expect the setStatus calls to be fairly frequent.
    //
    // Because both assignNode and setStatus can make remote calls to the
    // global server, they should not be called from within a transaction.
    //
    // Reads of the map, getNode and getIdentities, must be called from within
    // a transaction.  All map information is stored through the data service,
    // and is (of course) changed within a transaction;  services can rely
    // on mappings not changing during one of their transactions.
    //
    // Note that whether an identity is currently assigned to a node (mapped
    // in this service) or not is an optimization.  All services use getNode
    // to ensure there's an assignment if they will be consuming resources
    // on behalf of an identity;  if there is no current assignment, assignNode
    // needs to be called.
    // 
    // The data store is used to persist information about the mapping
    // (both from id->node and node->ids).   An immutible object, an
    // IdentityMO, which holds the identity and assigned node id, is stored 
    // under various service bindings:
    //
    // <PREFIX>.identity.<identity>              the id->node mapping
    // <PREFIX>.node.<nodeId>.<identity>         the node->id mapping
    //      note: iterating on all <PREFIX>.node.nodeId entries gives us the
    //            set of all identities on a node
    // <PREFIX>.status.<identity>.<nodeId>.<serviceName>  
    //          the services which believe <identity> is active on <nodeId>
    //      note: when there are no <PREFIX>.status.<identity> bindings,
    //            the identity can be removed
    //
    // The first two bindings, .identity and .node, are always created and 
    // removed at the same time.  When the .identity binding is removed, the
    // IdentityMO object is deleted.  These modifications are typically done
    // by the global server, although we may support locally creating them
    // in the future (removes will always be at the server).
    //
    // The .status bindings are created and removed by the local services.
    // The global server is contacted only when we detect there are no more
    // bindings for an identity.
    //
    // Additionally, there is a version object stored by the server.  When
    // the server starts up, it checks that the current version (in NodeMapUtil)
    // matches the stored version.  If the stored version is not the same,
    // persisted data is converted, as necessary, to the new version format.
    // The version is stored as:
    //
    // <PREFIX>.version       with a NodeMapUtil.VersionMO 
    //
    //
    // XXX TODO XXX
    //  - Need to figure out how assignNode will work if we have identities
    //    that are to be assigned locally, from within a transaction (example:
    //    creating an AI identity from game code).  Because we'd be called
    //    while we're in a transaction, we cannot make a remote call to the
    //    server.  Also, we'll probably want a transactional version of
    //    assignNode (another method) for this case, rather than allowing
    //    assignNode to be called in both a transactional and non-transactional
    //    manner.
    //  - AssignNode will probably want to take location hints (say, assign
    //    the identity close by a set of other identities).  This will be
    //    decided when we work on the load balancer.
    //  - The server doesn't persist everything it needs to in case it crashes
    //    (the goal is that we be able to have a hot backup standing by).
    //    It needs to persist entries which are candidates for removal and
    //    the client node mapping service listeners which have registered
    //    with it.   For the client listeners, it's currently unclear whether
    //    they will ever need to be persisted:  if the server goes down, does
    //    this imply all services have failed?
    //  - API issue:  setStatus is currently NOT transactional.  It can be
    //    implemented to be run under a transaction if that makes things
    //    easier for the clients of this method (see note below for how).
    //    I waffle on this issue because it seems better for the API to have
    //    anything that modfies the node map potentially go through the server.
    //  - Look into simplifying setStatus persisted information, as discussed
    //    above.  The issue is the server, through assignNode, atomically
    //    sets the status for a service on a node.
    //  - Potential issue:  when the server moves identities (because of 
    //    load balancing or node failure), it does NOT retain any setStatus
    //    settings for the old node.  This is clearly correct for node failure,
    //    but is it correct for load balancing?  Or should load balancing cause
    //    the information to be carried to the new node?  I chose to not carry
    //    the information forward because then node failure looks the same
    //    as a load balancing move to the services using the node mapping
    //    service.
    //  - This service assumes the server will be up before services attempt
    //    to connect to it.  Is that reasonable?
    //  - Add logging for all Errors thrown?  Would be useful for debugging
    //    things like OutOfMemoryErrors.
    //  - If the server is not available (we get IOException errors when
    //    we try to contact it), we retry a few times and then give up.
    //    We're discussing adding a method to the watchdog service to allow
    //    services to tell it that we need to shut down the stack.
    //  - This service currently cannot operate if the backing server isn't
    //    available (see note above).  It's probably very possible to make it
    //    work correctly and locally if the server isn't available, if we 
    //    choose to not give up on server disconnects.

    /** Package name for this class */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /** Class name. */
    private static final String CLASSNAME = 
            NodeMappingServiceImpl.class.getName();
    
    /** The property name for the server host. */
    static final String SERVER_HOST_PROPERTY = PKG_NAME + ".server.host";
    
    /** The property name for the client port. */
    private static final String CLIENT_PORT_PROPERTY = 
            PKG_NAME + ".client.port";
    
    /** The default value of the server port. */
    private static final int DEFAULT_CLIENT_PORT = 0;
    
    /** The watchdog service. */
    private final WatchdogService watchdogService;
    
    /** The context factory for map change transactions. */
    private final ContextFactory contextFactory;    
    
    /** The registered node change listeners. There is no need
     *  to persist these:  these are all local services, and if the
     *  node goes down, they'll need to re-register.
     */
    private final Set<NodeMappingListener> nodeChangeListeners =
            new CopyOnWriteArraySet<NodeMappingListener>();
    
    /** The remote backend to this service. */
    private final NodeMappingServer server;
    
    /** The implementation of the server, if we're on the special node.
     *  Only one server should be created in a system, but this is not
     *  enforced.
     */
    private final NodeMappingServerImpl serverImpl;

    /** The object we send to our global server for callbacks when there
     *  are map changes on this node.
     */
    private final NotifyClient changeNotifier; 
        
    /** Our instantiated change notifier object.  This reference
     *  is held so the object is not garbage collected;  it is also
     *  used for local calls.
     */
    private final MapChangeNotifier changeNotifierImpl;

    /** The exporter for the client */
    private final Exporter<NotifyClient> exporter;
    
    /** The local node id, as determined from the watchdog */
    private final long localNodeId;

    /** Our string representation, used by toString() and getName(). */
    private final String fullName;

    /** Lock object for pending notifications */
    private final Object lock = new Object();
    
    /**
     * The list of notifications which couldn't be sent because
     * we weren't in ready yet.  This list is added to while we're
     * in the initialized state, and emptied in the ready method.
     * Protected by the lock.
     */
    private final List<TaskReservation> pendingNotifications =
                new ArrayList<TaskReservation>();
    
    /** Our service statistics */
    private final NodeMappingServiceStats serviceStats;
    
    /** The set of identity relocation listeners for this node. */
    private final Set<IdentityRelocationListener> idRelocationListeners =
            new CopyOnWriteArraySet<IdentityRelocationListener>();

    /** A map of identities to outstanding idRelocation handlers. */
    private final ConcurrentMap<Identity, Queue<SimpleCompletionHandler>>
        relocationHandlers =
            new ConcurrentHashMap<Identity, Queue<SimpleCompletionHandler>>();
    /**
     * Constructs an instance of this class with the specified properties.
     * <p>
     * The application context is resolved at construction time (rather
     * than when {@link #ready} is called), because this service
     * does not use Managers and will not run application code.  Managers 
     * are not available until {@code ready} is called.
     * <p>
     * @param	properties the properties for configuring this service
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     *
     * @throws Exception if an error occurs during creation
     */
    public NodeMappingServiceImpl(Properties properties, 
                                  ComponentRegistry systemRegistry,
                                  TransactionProxy txnProxy)
        throws Exception
    {
        super(properties, systemRegistry, txnProxy, 
              new LoggerWrapper(Logger.getLogger(PKG_NAME)));
        
        logger.log(Level.CONFIG, 
                 "Creating NodeMappingServiceImpl properties:{0}", properties);
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        
	try {
            watchdogService = txnProxy.getService(WatchdogService.class);
            
            contextFactory = new ContextFactory(txnProxy);
                
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
		    } },  taskOwner);
                    
            // Find or create our server.   
            String localHost = 
                    InetAddress.getLocalHost().getHostName(); 
            NodeType nodeType = 
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE, 
                                             NodeType.class, 
                                             NodeType.singleNode);
            boolean instantiateServer = nodeType != NodeType.appNode;
            
            String host;
            int port;
            
            if (instantiateServer) {
                serverImpl = 
                    new NodeMappingServerImpl(properties, 
                                              systemRegistry, txnProxy);
                // Use the port actually used by our server instance
                host = localHost;
                port = serverImpl.getPort();
            } else {
                serverImpl = null;
                host = 
                    wrappedProps.getProperty(
			SERVER_HOST_PROPERTY,
			wrappedProps.getProperty(
			    StandardProperties.SERVER_HOST));
                if (host == null) {
                    throw new IllegalArgumentException(
                                           "A server host must be specified");
                }
                port = wrappedProps.getIntProperty(
                        NodeMappingServerImpl.SERVER_PORT_PROPERTY, 
                        NodeMappingServerImpl.DEFAULT_SERVER_PORT, 0, 65535);   
            }          
          
            // TODO This code assumes that the server has already been started.
            // Perhaps it'd be better to block until the server is available?
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (NodeMappingServer) registry.lookup(
                         NodeMappingServerImpl.SERVER_EXPORT_NAME);	    
            
            // Export our client object for server callbacks.
            int clientPort = wrappedProps.getIntProperty(
                                        CLIENT_PORT_PROPERTY, 
                                        DEFAULT_CLIENT_PORT, 0, 65535);
            changeNotifierImpl = new MapChangeNotifier();
            exporter = new Exporter<NotifyClient>(NotifyClient.class);
            clientPort = exporter.export(changeNotifierImpl, clientPort);
            changeNotifier = exporter.getProxy();
            
            // Obtain our node id from the watchdog service.
            localNodeId = dataService.getLocalNodeId();
            
            // Check if we're running on a full stack; if we are, register
            // with our server so our node is a candidate for identity
            // assignment.
            boolean fullStack = nodeType != NodeType.coreServerNode;
            if (fullStack) {
                try {
                    server.registerNodeListener(changeNotifier, localNodeId);
                } catch (IOException ex) {
                    // This is very bad.
                    logger.logThrow(Level.CONFIG, ex,
                            "Failed to contact server");
                    throw new RuntimeException(ex);
                }
            }
            
            fullName = "NodeMappingServiceImpl[host:" + localHost + 
                       ", clientPort:" + clientPort + 
                       ", fullStack:" + fullStack + "]";
            
            // create our profiling info and register our MBean
            ProfileCollector collector =
                systemRegistry.getComponent(ProfileCollector.class);
            serviceStats = new NodeMappingServiceStats(collector);
            try {
                collector.registerMBean(serviceStats, 
                                        NodeMappingServiceMXBean.MXBEAN_NAME);
            } catch (JMException e) {
                logger.logThrow(Level.CONFIG, e, "Could not register MBean");
            }

	} catch (Exception e) {
            logger.logThrow(Level.SEVERE, e, 
                            "Failed to create NodeMappingServiceImpl");
	    throw e;
	}
    }
 
    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
        return toString();
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
        // At this point, we should never be adding to the pendingNotifications
        // list, as our state is RUNNING.
        synchronized (lock) {
            for (TaskReservation pending : pendingNotifications) {
                pending.use();
            }
        }
    }
    
    /** {@inheritDoc} */
    protected void doShutdown() {
        try {
            exporter.unexport();
            if (dataService != null) {
                // We've been configured
                server.unregisterNodeListener(localNodeId);
            }
        } catch (IOException ex) {
            logger.logThrow(Level.WARNING, ex, 
                    "Problem encountered during shutdown");
        }
        
        // Ordering counts here.  We need to do whatever we might with
        // the server (say, unregister the node listeners) before we
        // cause it to shut down.
        if (serverImpl != null) {
            serverImpl.shutdown();
        }
    }
    
    /**
     * Throws {@code IllegalStateException} if this service is not running.
     * Code swiped from the data service.
     */
    private void checkState() {
        if (shuttingDown()) {
	    throw new IllegalStateException("service shutting down");
        }
    }
    
    /* -- Implement NodeMappingService -- */

    /** 
     * {@inheritDoc} 
     * <p>
     *  If the identity is not associated with a client (i.e., if it is
     *  an AI object), it will be assigned to the local node.  Otherwise,
     *  a remote call will be made to determine a node assignment.
     */
    public void assignNode(final Class service, final Identity identity) {
        checkState();
        if (service == null) {
            throw new NullPointerException("null service");
        }
        if (identity == null) {
            throw new NullPointerException("null identity");
        }
        
        // Cannot call within a transaction
        checkNonTransactionalContext();
        
        serviceStats.assignNodeOp.report();
        
        // We could check here to see if there's already a mapping, 
        // saving a remote call.  However, it makes the logic here
        // more complicated, and it means we duplicate some of the
        // server's work.  Best to always ask the server to handle it.
        //
        // Note for all uses of runIoTask in this class:  if we cannot
        // contact the server, we ask that the local node be shut down.
        // This is because "server" is the core server, which contains
        // the data store.  If it is shutdown, the entire cluster is
        // shut down.  If we have a loss of connectivity with the server,
        // we assume the problem is with the local node.  If the core server
        // is disconnected from all nodes, the watchdog server will eventually
        // detect that and declare all nodes dead.
        runIoTask(
            new IoRunnable() {
                public void run() throws IOException {
                    server.assignNode(service, identity, localNodeId);       
                }
            }, localNodeId);
        logger.log(Level.FINEST, "assign identity {0}", identity);
    }
    
    /** 
     * {@inheritDoc} 
     * <p>
     * The local node makes the status change, avoiding a remote
     * call where possible.  However, if it appears that an identity
     * might be ready for garbage collection, it tells the server, which
     * will perform the deletion.
     */
    public void setStatus(Class service, final Identity identity,
                          boolean active)
        throws UnknownIdentityException
    {
        checkState();
        if (service == null) {
            throw new NullPointerException("null service");
        }
        if (identity == null) {
            throw new NullPointerException("null identity");
        }       

        // Cannot call within a transaction
        checkNonTransactionalContext();
        
        serviceStats.setStatusOp.report();
        
        SetStatusTask stask = 
                new SetStatusTask(identity, service.getName(), active);
        try {
            transactionScheduler.runTask(stask, taskOwner);
        } catch (Exception e) {
            logger.logThrow(Level.WARNING, e, 
                                "Setting status for {0} failed", identity);
            throw new UnknownIdentityException("id: " + identity, e);
        }

        if (stask.canRemove()) {
            runIoTask(
                new IoRunnable() {
                    public void run() throws IOException {
                        server.canRemove(identity);
                    }
                }, localNodeId);
        }
        logger.log(Level.FINEST, "setStatus key: {0} , active: {1}", 
                stask.statusKey(), active);
    }
    
    /**
     * Task for setting a status and returning information about
     * whether the identity is considered dead by this node.
     */
    private class SetStatusTask extends AbstractKernelRunnable {
        private final boolean active;
        private final String idKey;
        private final String removeKey;
        private final String statusKey;
        
        /** return value, true if reference count goes to zero */
        private boolean canRemove = false;

        SetStatusTask(Identity id, String serviceName, boolean active) {
	    super(null);
            this.active = active;
            idKey = NodeMapUtil.getIdentityKey(id);
            removeKey = NodeMapUtil.getPartialStatusKey(id);
            statusKey = NodeMapUtil.getStatusKey(id, localNodeId, serviceName);
        }
        
        public void run() throws UnknownIdentityException { 
            // Exceptions thrown by getServiceBinding are handled by caller.
            IdentityMO idmo = 
		(IdentityMO) dataService.getServiceBinding(idKey);
            
            if (active) {
                dataService.setServiceBinding(statusKey, idmo);
            } else {
                // Note that NameNotBoundException can be thrown
                // if this is our second time calling this method.
                try {
                    dataService.removeServiceBinding(statusKey);
                } catch (NameNotBoundException ex) {
                    // This is OK - it can be thrown if this is our second
                    // time calling this method.
                    return;
                }
                String name = dataService.nextServiceBoundName(removeKey);
                canRemove = (name == null || !name.startsWith(removeKey));
            }
        }
        
        boolean canRemove() { return canRemove; }
        String statusKey()  { return statusKey; }  // used for logging
    }
    
    /** {@inheritDoc} */
    public Node getNode(Identity id) throws UnknownIdentityException {   
        checkState();
        if (id == null) {
            throw new NullPointerException("null identity");
        }
        
        serviceStats.getNodeOp.report();

        Context context = contextFactory.joinTransaction();
        Node node = context.get(id);
        logger.log(Level.FINEST, "getNode id:{0} returns {1}", id, node);
        return node;
    }
    
    /** {@inheritDoc} */
    public Iterator<Identity> getIdentities(long nodeId) 
        throws UnknownNodeException 
    {
        checkState();
        serviceStats.getIdentitiesOp.report();

        // Verify that the nodeId is valid.
        Node node = watchdogService.getNode(nodeId);
        if (node == null) {
            throw new UnknownNodeException("node id: " + nodeId);
        }
        IdentityIterator iter = new IdentityIterator(dataService, nodeId);
        logger.log(Level.FINEST, "getIdentities successful");
        return iter;
    }
    
    private static class IdentityIterator implements Iterator<Identity> {
        private DataService dataService;
        private Iterator<String> iterator;
        
        IdentityIterator(DataService dataService, long nodeId) {
            this.dataService = dataService;
            iterator = 
                BoundNamesUtil.getServiceBoundNamesIterator(dataService, 
                        NodeMapUtil.getPartialNodeKey(nodeId));
        }
        
        /** {@inheritDoc} */
        public boolean hasNext() {
            return iterator.hasNext();
        }
        
        /** {@inheritDoc} */
        public Identity next() {
            String key = iterator.next();
            // We look up the identity in the data service. Most applications
            // will use a customized Identity object.
            IdentityMO idmo = 
		(IdentityMO) dataService.getServiceBinding(key);
            return idmo.getIdentity();
        }
        
        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException("remove is not supported");
        }
    }
    
    
    /** {@inheritDoc} */
    public void addIdentityRelocationListener(
                                         IdentityRelocationListener listener) 
    {
        checkState();
        if (listener == null) {
            throw new NullPointerException("null listener");
        }
        serviceStats.addIdentityRelocationListenerOp.report();

        idRelocationListeners.add(listener);
        logger.log(Level.FINEST, "addIdentityRelocationListener successful");
    }
    
    /** {@inheritDoc} */
    public void addNodeMappingListener(NodeMappingListener listener) {
        checkState();
        if (listener == null) {
            throw new NullPointerException("null listener");
        }
        serviceStats.addNodeMappingListenerOp.report();

        nodeChangeListeners.add(listener);
        logger.log(Level.FINEST, "addNodeMappingListener successful");
    }
    
    /**
     * Class responsible for notifying local listeners of changes to the node
     * mapping.  An instance of this class is registered with our global
     * server;  methods of {@link NotifyClient} are called when an identity
     * is added to or removed from a node.
     */
    private class MapChangeNotifier implements NotifyClient {
        MapChangeNotifier() { }
        
        public void removed(Identity id, Node newNode) {

            // Check to see if we've been constructed but are not yet
            // completely running.  We reserve tasks for the notifications
            // in this case, and will use them when ready() has been called.
            synchronized (lock) {
                if (isInInitializedState()) {
                    logger.log(Level.FINEST, 
                               "Queuing remove notification for " +
                               "identity: {0}, " + 
                               "newNode: {1}}", 
                               id, newNode);
                    for (NodeMappingListener listener : 
                         nodeChangeListeners) 
                    {     
                        TaskReservation res =
                            taskScheduler.reserveTask(
                                new MapRemoveTask(listener, id, newNode),
                                taskOwner);
                        pendingNotifications.add(res);
                    }
                    return;
                }
            }
            
            // The normal case.
            for (NodeMappingListener listener : nodeChangeListeners) {
                taskScheduler.scheduleTask(
                    new MapRemoveTask(listener, id, newNode), taskOwner);
            }
        }

        public void added(Identity id, Node oldNode) {
            // Check to see if we've been constructed but are not yet
            // completely running.  We reserve tasks for the notifications
            // in this case, and will use them when ready() has been called.
            synchronized (lock) {
                if (isInInitializedState()) {
                    logger.log(Level.FINEST, 
                               "Queuing added notification for " +
                               "identity: {0}, " + "oldNode: {1}}", 
                               id, oldNode);
                    for (NodeMappingListener listener : 
                         nodeChangeListeners) 
                    {
                        TaskReservation res =
                            taskScheduler.reserveTask(
                                new MapAddTask(listener, id, oldNode),
                                taskOwner);
                        pendingNotifications.add(res);
                    }
                    return;
                }
            }
            
            // The normal case.
            for (final NodeMappingListener listener : nodeChangeListeners) {
                taskScheduler.scheduleTask(
                    new MapAddTask(listener, id, oldNode), taskOwner);
            }
        }
        
        public void prepareRelocate(Identity id, long newNodeId) {
            if (idRelocationListeners.isEmpty()) {
                // There's no work to do.
                tellServerCanMove(id);
            }
            Queue<SimpleCompletionHandler> handlerQueue =
                new ConcurrentLinkedQueue<SimpleCompletionHandler>();
            // If there is already an entry for this id, it means that attempt
            // to move has expired and the server is trying again.
            relocationHandlers.put(id, handlerQueue);
            
            // Check to see if we've been constructed but are not yet
            // completely running.  We reserve tasks for the notifications
            // in this case, and will use them when ready() has been called.
            synchronized (lock) {
                if (isInInitializedState()) {
                    logger.log(Level.FINEST, 
                               "Queuing added notification for " +
                               "identity: {0}, " + "newNode: {1}}", 
                               id, newNodeId);
                    for (IdentityRelocationListener listener : 
                         idRelocationListeners) 
                    {
                        final SimpleCompletionHandler handler =
                            new PrepareMoveCompletionHandler(id);
                        handlerQueue.add(handler);
                        TaskReservation res =
                            taskScheduler.reserveTask(
                                new MapRelocateTask(listener, id, newNodeId,
                                                    handler),
                                taskOwner);
                        pendingNotifications.add(res);
                    }
                    return;
                }
            }
            
            // The normal case.
            for (final IdentityRelocationListener listener : 
                 idRelocationListeners) 
            {
                final SimpleCompletionHandler handler =
                        new PrepareMoveCompletionHandler(id);
                handlerQueue.add(handler);
                taskScheduler.scheduleTask(
                    new MapRelocateTask(listener, id, newNodeId, handler),
                    taskOwner);
            }
        }
    }
     
    /**
     * Let a listener know that the mapping for an id to this node has
     * been removed, and what the new node mapping is (or null if there
     * is no new mapping).
     */
    private static final class MapRemoveTask extends AbstractKernelRunnable {
        final NodeMappingListener listener;
        final Identity id;
        final Node newNode;
        MapRemoveTask(NodeMappingListener listener, Identity id, Node newNode) {
	    super(null);
            this.listener = listener;
            this.id = id;
            this.newNode = newNode;
        }
        public void run() {
            listener.mappingRemoved(id, newNode);
        }
    }
    
    /**
     * Let a listener know that the mapping for an id to this node has
     * been added, and what the old node mapping was (or null if this is
     * a brand new mapping).
     */
    private static final class MapAddTask extends AbstractKernelRunnable {
        final NodeMappingListener listener;
        final Identity id;
        final Node oldNode;
        MapAddTask(NodeMappingListener listener, Identity id, Node oldNode) {
	    super(null);
            this.listener = listener;
            this.id = id;
            this.oldNode = oldNode;
        }
        public void run() {
            listener.mappingAdded(id, oldNode);
        }
    }
    
    /**
     * Let a listener know that the an identity will be relocated from this
     * node.
     */
    private static final class MapRelocateTask extends AbstractKernelRunnable {
        final IdentityRelocationListener listener;
        final Identity id;
        final long newNodeId;
        final SimpleCompletionHandler handler;
        MapRelocateTask(IdentityRelocationListener listener, 
                        Identity id, long newNodeId,
                        SimpleCompletionHandler handler)
        {
	    super(null);
            this.listener = listener;
            this.id = id;
            this.newNodeId = newNodeId;
            this.handler = handler;
        }
        public void run() {
            listener.prepareToRelocate(id, newNodeId, handler);
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
 
    /* -- Implement transaction participant/context for 'getNode' -- */

    private class ContextFactory
	extends TransactionContextFactory<Context>
    {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy, CLASSNAME);
	}
	
	/** {@inheritDoc} */
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    private final class Context extends TransactionContext {
        // Cache looked up nodes for identities within this transaction
        Map<Identity, Node> idcache = new HashMap<Identity, Node>();
        
	/**
	 * Constructs a context with the specified transaction.
	 */
        private Context(Transaction txn) {
	    super(txn);
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * Performs cleanup in the case that the transaction aborts.
	 */
	public void abort(boolean retryable) {
	    // Does nothing
	}

	/** {@inheritDoc} */
	public void commit() {
	    isCommitted = true;
        }
        
        public Node get(Identity identity) throws UnknownIdentityException {
            assert identity != null;
            // Check the cache
            Node node = idcache.get(identity);
            if (node != null) {
                return node;
            }
            
	    String key = NodeMapUtil.getIdentityKey(identity);
	    try {                
		IdentityMO idmo = 
		    (IdentityMO) dataService.getServiceBinding(key);
                node = watchdogService.getNode(idmo.getNodeId());
                if (node == null) {
                    // The identity is on a failed node, where the node has
                    // been removed from the data store but the identity hasn't
                    // yet.
                    throw new UnknownIdentityException("id: " + identity);
                }
                Node old = idcache.put(identity, node);
                assert (old == null);
                return node;
	    } catch (NameNotBoundException e) {
                throw new UnknownIdentityException("id: " + identity);
	    } catch (ObjectNotFoundException e1) {
                throw new UnknownIdentityException("id: " + identity);
            }
 
        }  
    }

    /**
     * A {@code SimpleCompletionHandler} implementation for identity
     * relocation listeners.  When {@code completed} is invoked, the handler
     * instance is removed from the relocation handler queue for the associated
     * identity.  If a given handler is the last one to be removed from an
     * identity's queue, then relocation preparations are complete for that
     * identity, and the node mapping service can actually move the identity
     * to its new node.
     */
    private final class PrepareMoveCompletionHandler
	implements SimpleCompletionHandler
    {
	/** The identity. */
	private final Identity id;
	/** Indicates whether relocation preparation is done for {@code id}. */
	private boolean isDone = false;

	/**
	 * Constructs an instance with the specified {@code node} and
	 * recovery {@code listener}.
	 */
	PrepareMoveCompletionHandler(Identity id) {
	    this.id = id;
	}

	/** {@inheritDoc} */
	public void completed() {
	    synchronized (this) {
		if (isDone) {
		    return;
		}
		isDone = true;
	    }

	    Queue<SimpleCompletionHandler> handlerQueue =
                    relocationHandlers.get(id);
	    assert handlerQueue != null;
            
            // If the queue did not change, this object wasn't on the queue.
            // This could happen if the move preparation has failed
            // previously (due to handlers not calling completed in a timely
            // manner).
            if (handlerQueue.remove(this)) {
                if (handlerQueue.isEmpty()) {
                    if (relocationHandlers.remove(id) != null) {
                        // Tell the server we're good to go if someone else
                        // hasn't already done so.
                        tellServerCanMove(id);
                    }
                }
            }
        }
    }
    
    /**
     * Tell the server that it's OK to move identity, all listeners
     * have been notified and have finished.
     * @param id the id to move
     */
    private void tellServerCanMove(final Identity id) {
        runIoTask(
            new IoRunnable() {
                public void run() throws IOException {
                    server.canMove(id);   
                }
            }, localNodeId);
        logger.log(Level.FINEST, "can move identity {0}", id);
    }
    
    /* -- For testing. -- */
    
    /**
     * Check the validity of the data store for a particular identity.
     * Used for testing.
     *
     * @param identity the identity
     * @return {@code true} if all is well, {@code false} if there is a problem
     *
     * @throws Exception if any error occurs
     */
    boolean assertValid(Identity identity) throws Exception {
        return server.assertValid(identity);        
    }
}
