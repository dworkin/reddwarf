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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps Identities to Nodes.
 * <p>
 * The {@link #NodeMappingServiceImpl constructor} supports the
 * following properties: 
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.start
 *	</b></code><br>
 *	<i>Default:</i> <code>false</code>
 *
 * <dd style="padding-top: .5em">Whether to run the server by creating an
 *	instance of {@link NodeMappingServerImpl}, using the properties provided
 *	to this instance's constructor. <p>

 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.host
 *	</b></code><br>
 *	<i>Default:</i> the local host name <br>
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
 *	if the {@code com.sun.sgs.impl.service.nodemap.start.server}
 *	property is {@code true}, and means that an anonymous port will be
 *	chosen for running the server. <p>
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
public class NodeMappingServiceImpl implements NodeMappingService 
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
    //  - Perhaps all methods that should NOT be called within a transaction
    //    should be declared to throw an exception if they are called from
    //    within a transaction?  This would used for all service APIs with
    //    non-transactional methods.   Some methods must be called from within
    //    a transaction, some must not, and for some it won't matter (e.g.
    //    equals() or toString().
    //

    /** Package name for this class */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /**
     * The property that specifies whether the server should be instantiated
     * in this stack.  Also used by the unit tests.
     */
    private static final String SERVER_START_PROPERTY = 
            PKG_NAME + ".server.start";
    
    /** The property name for the server host. */
    static final String SERVER_HOST_PROPERTY = PKG_NAME + ".server.host";
    
    /** The property name for the client port. */
    private static final String CLIENT_PORT_PROPERTY = 
            PKG_NAME + ".client.port";

    /** The number of times we should try to contact the backend before
     *  giving up. 
     */
    private final static int MAX_RETRY = 5;
    
    /** The default value of the server port. */
    private static final int DEFAULT_CLIENT_PORT = 0;
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));
    
    /** The task scheduler. */
    private final TaskScheduler taskScheduler;
    
    /** The owner for tasks I initiate. */
    private final Identity taskOwner;
    
    /** The data service. */
    private final DataService dataService;
    
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
    private long localNodeId;
    
    /** Lock object for service state */
    private final Object stateLock = new Object();
    
    /** The possible states of this instance. */
    enum State {
        /** After construction, but before ready call */
        CONSTRUCTED,
	/** After ready call and before shutdown */
	RUNNING,
	/** After start of a call to shutdown and before call finishes */
	SHUTTING_DOWN,
	/** After shutdown has completed successfully */
	SHUTDOWN
    }

    /** The current state of this instance. */
    private State state;
    
    /** Are we running an application?  If not, assume that we don't
     *  have a full stack.
     */
    private final boolean fullStack;

    /** Our string representation, used by toString() and getName(). */
    private final String fullName;

    /**
     * The list of notifications which couldn't be sent because
     * we weren't in State.RUNNING yet.  This list is added to
     * while we're in State.CONSTRUCTED, and emptied in the ready method.
     * Protected by the stateLock.
     */
    private final List<TaskReservation> pendingNotifications =
                new ArrayList<TaskReservation>();
    
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
        logger.log(Level.CONFIG, 
                 "Creating NodeMappingServiceImpl properties:{0}", properties);

        if (systemRegistry == null) {
            throw new NullPointerException("null systemRegistry");
	}
        if (txnProxy == null) {
            throw new NullPointerException("null transaction proxy");
        }
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        
	try {
            taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
            dataService = txnProxy.getService(DataService.class);
            watchdogService = txnProxy.getService(WatchdogService.class);
            taskOwner = txnProxy.getCurrentOwner();
            
            contextFactory = new ContextFactory(txnProxy);
                
            // Find or create our server.   
            boolean instantiateServer =  wrappedProps.getBooleanProperty(
                                                SERVER_START_PROPERTY, false);
            String localHost = InetAddress.getLocalHost().getHostName();            
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
                    wrappedProps.getProperty(SERVER_HOST_PROPERTY, localHost);
                port = wrappedProps.getIntProperty(
                        NodeMappingServerImpl.SERVER_PORT_PROPERTY, 
                        NodeMappingServerImpl.DEFAULT_SERVER_PORT, 0, 65535);   
            }          
          
            // TODO This code assumes that the server has already been started.
            // Perhaps it'd be better to block until the server is available?
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (NodeMappingServer) 
                      registry.lookup(NodeMappingServerImpl.SERVER_EXPORT_NAME);	    
            
            // Set our state before we export ourselves.
            synchronized(stateLock) {
                state = State.CONSTRUCTED;
            }
            
            // Export our client object for server callbacks.
            int clientPort = wrappedProps.getIntProperty(
                                        CLIENT_PORT_PROPERTY, 
                                        DEFAULT_CLIENT_PORT, 0, 65535);
            changeNotifierImpl = new MapChangeNotifier();
            exporter = new Exporter<NotifyClient>(NotifyClient.class);
            clientPort = exporter.export(changeNotifierImpl, clientPort);
            changeNotifier = exporter.getProxy();
            
            // Obtain our node id from the watchdog service.
            localNodeId = watchdogService.getLocalNodeId();
            
            // Check if we're running on a full stack; if we are, register
            // with our server so our node is a candidate for identity
            // assignment.
            String finalService =
                properties.getProperty(StandardProperties.FINAL_SERVICE);
            fullStack = (finalService == null) ? true :
                !(properties.getProperty(StandardProperties.APP_LISTENER)
                    .equals(StandardProperties.APP_LISTENER_NONE));
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
    
   
    /** {@inheritDoc} */
    public void ready() {
        synchronized(stateLock) {
            state = State.RUNNING;
        }
        
        // At this point, we should never be adding to the pendingNotifications
        // list, as our state is RUNNING.
        for (TaskReservation pending: pendingNotifications) {
            pending.use();
        }
    }
    
    /** {@inheritDoc} */
    public boolean shutdown() {
        synchronized(stateLock) {
            if (state == State.SHUTTING_DOWN) {
                return false;
            } else if (state == State.SHUTDOWN) {
                throw new IllegalStateException("Service is already shut down");
            }
            state = State.SHUTTING_DOWN;
        }

        boolean ok = true;

        try {
            exporter.unexport();
            if (dataService != null) {
                // We've been configured
                server.unregisterNodeListener(localNodeId);
            }
        } catch (IOException ex) {
            logger.logThrow(Level.WARNING, ex, 
                    "Problem encountered during shutdown");
            ok = false;
        }
        
        // Ordering counts here.  We need to do whatever we might with
        // the server (say, unregister the node listeners) before we
        // cause it to shut down.
        if (serverImpl != null) {
            ok = serverImpl.shutdown();
        }

        if (ok) {
            synchronized(stateLock) {
                state = State.SHUTDOWN;
            }
        }
        return ok;
    }
    
    /**
     * Throws {@code IllegalStateException} if this service is not running.
     * Code swiped from the data service.
     */
    private void checkState() {
        if (!fullStack) {
            throw 
                new IllegalStateException("No application running");
        }
	synchronized (stateLock) {
	    switch (state) {
            case CONSTRUCTED:
                break;
	    case RUNNING:
		break;
	    case SHUTTING_DOWN:
		break;
	    case SHUTDOWN:
		throw new IllegalStateException("Service is shut down");
	    default:
		throw new AssertionError();
	    }
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
    public void assignNode(Class service, final Identity identity) {
        checkState();
        if (service == null) {
            throw new NullPointerException("null service");
        }
        if (identity == null) {
            throw new NullPointerException("null identity");
        }
        
        // We could check here to see if there's already a mapping, 
        // saving a remote call.  However, it makes the logic here
        // more complicated, and it means we duplicate some of the
        // server's work.  Best to always ask the server to handle it.
        
        int tryCount = 0;
        while (tryCount < MAX_RETRY) {
            try {
                server.assignNode(service, identity);
                tryCount = MAX_RETRY;
                logger.log(Level.FINEST, "assign identity {0}", identity);
            } catch (IOException ioe) {
                tryCount++;
                logger.logThrow(Level.FINEST, ioe, 
                        "Exception encountered on try {0}: {1}",
                        tryCount, ioe);
            }
        } 
    }
    
    /** 
     * {@inheritDoc} 
     * <p>
     * The local node makes the status change, avoiding a remote
     * call where possible.  However, if it appears that an identity
     * might be ready for garbage collection, it tells the server, which
     * will perform the deletion.
     */
    public void setStatus(Class service, Identity identity, boolean active) 
        throws UnknownIdentityException
    {
        checkState();
        if (service == null) {
            throw new NullPointerException("null service");
        }
        if (identity == null) {
            throw new NullPointerException("null identity");
        }       

        SetStatusTask stask = 
                new SetStatusTask(identity, service.getName(), active);
        try {
            runTransactionally(stask);
        } catch (Exception e) {
            logger.logThrow(Level.WARNING, e, 
                                "Setting status for {0} failed", identity);
            throw new UnknownIdentityException("id: " + identity, e);
        }

        if (stask.canRemove()) {
            int tryCount = 0;
            while (tryCount < MAX_RETRY) {
                try {
                    server.canRemove(identity);
                    tryCount = MAX_RETRY;
                } catch (IOException ioe) {
                    tryCount++;
                    logger.logThrow(Level.WARNING, ioe, 
                           "Could not tell server OK to delete {0}", identity);
                }
            }
        }
        logger.log(Level.FINEST, "setStatus key: {0} , active: {1}", 
                stask.statusKey(), active);
    }
    
    /**
     * Task for setting a status and returning information about
     * whether the identity is considered dead by this node.
     */
    private class SetStatusTask extends AbstractKernelRunnable {
        final private boolean active;
        final private String idKey;
        final private String removeKey;
        final private String statusKey;
        
        /** return value, true if reference count goes to zero */
        private boolean canRemove = false;

        SetStatusTask(Identity id, String serviceName, boolean active) {
            this.active = active;
            idKey = NodeMapUtil.getIdentityKey(id);
            removeKey = NodeMapUtil.getPartialStatusKey(id);
            statusKey = NodeMapUtil.getStatusKey(id, localNodeId, serviceName);
        }
        
        public void run() throws UnknownIdentityException { 
            // Exceptions thrown by getServiceBinding are handled by caller.
            IdentityMO idmo = 
                    dataService.getServiceBinding(idKey, IdentityMO.class);
            
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
        // Verify that the nodeId is valid.
        watchdogService.getNode(nodeId);
        IdentityIterator iter = new IdentityIterator(dataService, nodeId);
        if (!iter.hasNext()) {
            throw new UnknownNodeException("node id: " + nodeId);
        }
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
                    dataService.getServiceBinding(key, IdentityMO.class);
            return idmo.getIdentity();
        }
        
        /** {@inheritDoc} */
        public void remove() {
            throw new UnsupportedOperationException("remove is not supported");
        }
    }
    
    /** {@inheritDoc} */
    public void addNodeMappingListener(NodeMappingListener listener) 
    {
        checkState();
        if (listener == null) {
            throw new NullPointerException("null listener");
        }
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
            synchronized(stateLock) {
                if (state == State.CONSTRUCTED) {
                    logger.log(Level.FINEST, 
                               "Queuing remove notification for " +
                               "identity: {0}, " + "newNode: {1}}", 
                               id, newNode);
                    for (NodeMappingListener listener : nodeChangeListeners) {     
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
            synchronized(stateLock) {
                if (state == State.CONSTRUCTED) {
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
        MapRemoveTask(NodeMappingListener listener, Identity id, Node newNode) 
        {
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
        MapAddTask(NodeMappingListener listener, Identity id, Node oldNode) 
        {
            this.listener = listener;
            this.id = id;
            this.oldNode = oldNode;
        }
        public void run() {
            listener.mappingAdded(id, oldNode);
        }
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
     *  Run the given task synchronously, and transactionally.
     * @param task the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {     
        taskScheduler.runTransactionalTask(task, taskOwner);
    }
            
    /* -- Implement transaction participant/context for 'getNode' -- */

    private class ContextFactory
	extends TransactionContextFactory<Context>
    {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
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
                        dataService.getServiceBinding(key, IdentityMO.class);
                node = watchdogService.getNode(idmo.getNodeId());
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
