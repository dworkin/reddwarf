/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
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
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps Identities to Nodes.
 * <p>
 * In addition to the properties supported by the {@link DataService}
 * class, the {@link #NodeMappingServiceImpl constructor} supports the
 * following properties: 
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.start.server
 *	</b></code><br>
 *	<i>Default:</i> <code>false</code>
 *
 * <dd style="padding-top: .5em">Whether to run the server by creating an
 *	instance of {@link NodeMappingServerImpl}, using the properties provided
 *	to this instance's constructor. <p>

 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.host
 *	</b></code><br>
 *	<i>Required</i>
 *
 * <dd style="padding-top: .5em">The name of the host running the {@code
 *	NodeMappingServer}. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44533}
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
    //  - Identities currently don't have a uuid associated with them!
    //    This code assumes they ARE unique and there is a method which
    //    will return a unique string for them.
    //  - Need to figure out how assignNode will work when we have identities
    //    that are to be assigned locally, from within a transaction (example:
    //    creating an AI identity from game code).
    //  - AssignNode will probably want to take location hints (say, assign
    //    the identity close by a set of other identities).  This will be
    //    decided when we work on the load balancer.
    //  - The server doesn't persist everything it needs to in case it crashes
    //    (the goal is that we be able to have a hot backup standing by).
    //  - API issue:  setStatus is currently NOT transactional.  It can be
    //    implemented to be run under a transaction if that makes things
    //    easier for the clients of this method (see note below for how).
    //    I waffle on this issue because it seems better for the API to have
    //    anything that modfies the node map potentially go through the server.
    //  - This service almost runs correctly if the server is unavailable,
    //    (say, if there's a transient network partition), except:
    //       - remove:  any identity that we detected could be removed
    //           during the partition won't be removed.  There's a unit
    //           test that demonstrates this.   The remove list needs to
    //           be persisted anyway, so perhaps the best thing is to have
    //           the client store the potentially removable identity in
    //           the data service, and have the server check for new additions
    //           periodically.
    //  - Look into simplifying setStatus persisted information, as discussed
    //    above.  The issue is the server, through assignNode, atomically
    //    sets the status for a service on a node.
    //  - Potential issue:  when the server moves identities (because of 
    //    load balancing or node failure), it does NOT retain any setStatus
    //    settings for the old node.  This is clearly correct for node failure,
    //    but is it correct for load balancing?  Or should load balancing cause
    //    the information to be carried to the new node?  
    //  - This service assumes the server will be up before services attempt
    //    to connect to it.  Is that reasonable?
    //    
    //

    /** Package name for this class */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /**
     * The property that specifies whether the server should be instantiated
     * in this stack.  Also used by the unit tests.
     */
    private static final String START_SERVER_PROPERTY = 
            PKG_NAME + ".start.server";
    
    /** The property name for the server host. */
    static final String SERVER_HOST_PROPERTY = PKG_NAME + ".server.host";
    
    /** The property name for the client port. */
    private static final String CLIENT_PORT_PROPERTY = 
            PKG_NAME + ".client.port";

    /** The default value of the server port. */
    private static final int DEFAULT_CLIENT_PORT = 0;
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(
                Logger.getLogger(NodeMappingServiceImpl.class.getName()));
    
    /** The transaction proxy, or null if configure has not been called. */    
    private static TransactionProxy txnProxy;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;
    
    /** The owner for tasks I initiate. */
    private TaskOwner taskOwner;
    
    /** The data service. */
    private DataService dataService;
    
    /** The watchdog service. */
    private WatchdogService watchdogService;
    
    /** The context factory for map change transactions. */
    private ContextFactory contextFactory;    


    /** The registered registry change listeners. There is no need
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

    /** The exporter for the service */
    private final Exporter<NotifyClient> exporter;
    
    /** The local node id, as determined from the watchdog */
    private long localNodeId;
    
    /** Lock object for service state */
    private final Object stateLock = new Object();
    
    /** The possible states of this instance. */
    enum State {
	/** Before configure has been called */
	UNINITIALIZED,
	/** After configure and before shutdown */
	RUNNING,
	/** After start of a call to shutdown and before call finishes */
	SHUTTING_DOWN,
	/** After shutdown has completed successfully */
	SHUTDOWN
    }

    /** The current state of this instance. */
    private State state = State.UNINITIALIZED;
    
    /** Are we running an application?  If not, assume that we don't
     *  have a full stack.
     */
    private final boolean fullStack;

    /** The number of times we should try to contact the backend before
     *  giving up. 
     */
    private final static int MAX_RETRY = 5;
    
    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     *
     * @throws Exception if an error occurs during creation
     */
    public NodeMappingServiceImpl(
            Properties properties, ComponentRegistry systemRegistry)
            throws Exception
    {
        logger.log(Level.CONFIG, 
                 "Creating NodeMappingServiceImpl properties:{0}", properties);

        if (systemRegistry == null) {
            throw new NullPointerException("null systemRegistry");
	}
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        
	try {

            boolean instantiateServer =  wrappedProps.getBooleanProperty(
                                  START_SERVER_PROPERTY, false);
            String localHost = InetAddress.getLocalHost().getHostName();           
            
            taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
            
            String host;
            int port;
            
            if (instantiateServer) {
                serverImpl = 
                    new NodeMappingServerImpl(properties, systemRegistry);
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
          
            // This code assumes that the server has already been started.
            // Perhaps it'd be better to block until the server is available?
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (NodeMappingServer) 
                      registry.lookup(NodeMappingServerImpl.SERVER_EXPORT_NAME);	    
            
            final int clientPort = wrappedProps.getIntProperty(
                                        CLIENT_PORT_PROPERTY, 
                                        DEFAULT_CLIENT_PORT, 0, 65535);
            
            changeNotifierImpl = new MapChangeNotifier();
            exporter = new Exporter<NotifyClient>(NotifyClient.class);
            exporter.export(changeNotifierImpl, clientPort);
            changeNotifier = exporter.getProxy();

            // Check if we're running on a full stack. 
            String finalService =
                properties.getProperty(StandardProperties.FINAL_SERVICE);
            fullStack = (finalService == null) ? true :
                !(properties.getProperty(StandardProperties.APP_LISTENER)
                    .equals(StandardProperties.APP_LISTENER_NONE));
            
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
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
        logger.log(Level.CONFIG, "Configuring NodeMappingServiceImpl");
        if (registry == null) {
            throw new NullPointerException("null registry");
        } else if (proxy == null) {
            throw new NullPointerException("null transaction proxy");
        }
        
	try {    
	    synchronized (NodeMappingServiceImpl.class) {
		if (NodeMappingServiceImpl.txnProxy == null) {
		    NodeMappingServiceImpl.txnProxy = proxy;
		} else {
		    assert NodeMappingServiceImpl.txnProxy == proxy;
		}                        
	    }

	    synchronized (stateLock) {
		if (dataService != null) {
		    throw new IllegalStateException("Already configured");
		}
                (new ConfigureServiceContextFactory(txnProxy)).
 	                    joinTransaction();
                dataService = registry.getComponent(DataService.class);
                
                contextFactory = new ContextFactory(txnProxy);
                taskOwner = txnProxy.getCurrentOwner();
            }
            
            watchdogService = registry.getComponent(WatchdogService.class); 
            localNodeId = watchdogService.getLocalNodeId();

            if (serverImpl != null) {
                serverImpl.configure(registry, txnProxy);
            }
            
            // Don't register ourselves if we're not running an application.
            if (fullStack) {
                // Register myself with my server using local node id.
                // We don't register if we created the server because
                // we might not be running a full stack.
                try {
                    server.registerNodeListener(changeNotifier, localNodeId);
                } catch (IOException ex) {
                    // This is very bad.
                    logger.logThrow(Level.CONFIG, ex,
                            "Failed to contact server");
                    throw new RuntimeException(ex);
                }
            }

	} catch (RuntimeException e) {
            logger.logThrow(Level.CONFIG, e,
                            "Failed to configure NodeMappingServiceImpl");
	    throw e;
	}
    }
    
   
    /** {@inheritDoc} */
    public boolean shutdown() {
        synchronized(stateLock) {
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
            ex.printStackTrace();
            ok = false;
        }
        
        // Ordering counts here.  We need to do whatever we might with
        // the server (say, unregister the node listeners) before we
        // cause it to shut down.
        if (serverImpl != null) {
            ok = serverImpl.shutdown();
        }

        synchronized(stateLock) {
            state = State.SHUTDOWN;
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
	    case UNINITIALIZED:
		throw new IllegalStateException("Service is not configured");
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
        
        boolean done = false;
        int tryCount = 0;

        while (!done && tryCount < MAX_RETRY) {
            try {
                server.assignNode(service, identity);
                done = true;
                tryCount = MAX_RETRY;
            } catch (IllegalStateException ise) {
                // Maybe we just need some nodes to come on line?
                tryCount++;
                logger.logThrow(Level.FINEST, ise, 
                        "Exception encountered on try {0}: {1}",
                        tryCount, ise);
            } catch (IOException ioe) {
                tryCount++;
                logger.logThrow(Level.FINEST, ioe, 
                        "Exception encountered on try {0}: {1}",
                        tryCount, ioe);
            }
        }
 
        if (!done) {
            // If we cannot talk to our server, we don't want the system
            // to stop functioning.  We'll just assign to our local node
            // and hope things will right themselves.
            //
            // TODO
            // This logic is half-baked, at best.  If we cannot contact
            // the server and want to assign locally, we'll need to also
            // have a way to let the server know we changed things behind
            // its back (probably a generation number?),  as well as a
            // way to eventually reconnect to the server.
            //
            // After discussions with Tim:  we should just assume that the
            // server is always available.  This code could be useful, though,
            // if we eventually have a transactional assignNode method that
            // only assigns for AIs - or other things we want to assign locally.
            logger.log(Level.FINEST, "Assigning {0} to this node", identity);

            AssignNodeLocallyTask localTask = 
                    new AssignNodeLocallyTask(service.getName(), 
                                              identity, localNodeId);
            try {              
                runTransactionally(localTask);
            } catch (Exception ex) {
                logger.logThrow(Level.WARNING, ex, 
                                "Local assignment for {0} failed", identity);
            }
            if (localTask.added()) {
                changeNotifierImpl.added(identity, null);
            }
        }
    }
    
    /**
     * Task for assigning an identity to this local node. 
     * Currently, this is only used when we cannot contact the server,
     * but might also be used in the future for non-client identity assignments.
     */
    private class AssignNodeLocallyTask implements KernelRunnable {
        private final String idkey;
        private final String nodekey;
        private final String statuskey;
        private IdentityMO idmo;
        /** return value, true if id added to map */
        private boolean added = true;
        
        AssignNodeLocallyTask(String name, Identity id, Long nodeId) {
            idkey = NodeMapUtil.getIdentityKey(id);
            nodekey = NodeMapUtil.getNodeKey(nodeId, id);
            statuskey = NodeMapUtil.getStatusKey(id, nodeId, name);
            
            idmo = new IdentityMO(id, nodeId);
        }
        
        public String getBaseTaskType() {
            return this.getClass().getName();
        }
        
        public void run() {  
            try {
                idmo = dataService.getServiceBinding(idkey, IdentityMO.class);
            } catch (NameNotBoundException e) {     
                added = true;
                // Add the id->node mapping.
                dataService.setServiceBinding(idkey, idmo);
                // Add the node->id mapping
                dataService.setServiceBinding(nodekey, idmo);
            }
            // Update reference count whether we just added or not.
            dataService.setServiceBinding(statuskey, idmo);
            
        }

        boolean added() { return added; }
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
        
        String idkey = NodeMapUtil.getIdentityKey(identity);
        NodeMapUtil.GetIdTask idtask = 
                new NodeMapUtil.GetIdTask(dataService, idkey);
        try {
            runTransactionally(idtask);
        } catch (NameNotBoundException nnbe) {
            throw new UnknownIdentityException("id: " + identity);
        } catch (ObjectNotFoundException onfe) {
            throw new UnknownIdentityException("id: " + identity);
        } catch (Exception ex) {
            logger.logThrow(Level.WARNING, ex, 
                                "Setting status for {0} failed", identity);
        }
        
        IdentityMO idmo = idtask.getId();
        
        if (idmo == null) {
            // We got an exception above.
            throw new UnknownIdentityException("id: " + identity);
        } else {
            String removekey = NodeMapUtil.getPartialStatusKey(identity);
            String statuskey = 
                    NodeMapUtil.getStatusKey(identity, localNodeId, 
                                             service.getName());
            SetStatusTask stask = 
                    new SetStatusTask(statuskey, removekey, idmo, active);
            try {
                runTransactionally(stask);
            } catch (NameNotBoundException nnbe) {
                // Ignore.  This can be thrown if active = false, and this is
                // our second call to this method.  
            } catch (ObjectNotFoundException onfe) {
                // If we're racing with the server's delete thread, we might
                // get an ObjectNotFoundException (suppose we're trying to
                // add active for an deletable identity:  we could get the 
                // idmo and have the server delete the object before we 
                // reached this code).
                // We treat this as though the idmo was deleted before we
                // entered this method.
                throw new UnknownIdentityException("id: " + identity);               
            } catch (Exception e) {
                logger.logThrow(Level.WARNING, e, 
                                "Setting status for {0} failed", statuskey);
            }
            
            if (stask.canRemove()) {
                try {
                    server.canRemove(identity);
                } catch (IOException ioe) {
                    // TODO need to create a scheme for handling not being
                    // able to contact the server.
                    logger.logThrow(Level.WARNING, ioe, 
                                "Could not tell server OK to delete {0}", idmo);
                }
            }
            logger.log(Level.FINEST, "setStatus key: {0}", statuskey);
        }
    }
    
    /**
     * Task for setting a status and returning information about
     * whether the identity is considered dead by this node.
     */
    private class SetStatusTask implements KernelRunnable {
        final private boolean active;
        final private String statuskey;
        final private String removekey;
        final private IdentityMO idmo;
        
        /** return value, true if reference count goes to zero */
        private boolean canRemove = false;

        SetStatusTask(String statuskey, String removekey, 
                      IdentityMO idmo, boolean active) {
            this.statuskey = statuskey;
            this.removekey = removekey;
            this.idmo = idmo;
            this.active = active;         
        }
        
        public String getBaseTaskType() {
            return this.getClass().getName();
        }
        
        public void run() {         
            if (active) {
                // Note that ObjectNotFoundException can be thrown
                // if we're racing with the server's delete thread
                dataService.setServiceBinding(statuskey, idmo);
            } else {
                // Note that NameNotBoundException can be thrown
                // if this is our second time calling this method.
                dataService.removeServiceBinding(statuskey);
                String name = dataService.nextServiceBoundName(removekey);
                canRemove = (name == null || !name.startsWith(removekey));
            }
        }
        
        boolean canRemove() { return canRemove; }
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
        String key = NodeMapUtil.getPartialNodeKey(nodeId);
        String next = dataService.nextServiceBoundName(key);
        if (next == null || !next.startsWith(key)) {
            throw new UnknownNodeException("node id: " + nodeId);
        }
        logger.log(Level.FINEST, "getIdentities successful");
        return new IdentityIterator(dataService, nodeId);
    }
    
    private static class IdentityIterator implements Iterator<Identity> {
        private DataService dataService;
        private long nodeId;
        private Iterator<String> iterator;
        
        IdentityIterator(DataService dataService, long nodeId) {
            this.dataService = dataService;
            this.nodeId = nodeId;
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
        
        public void removed(final Identity id, final Node newNode) {
            for (final NodeMappingListener listener : nodeChangeListeners) {
                taskScheduler.scheduleTask( 
                        new AbstractKernelRunnable() {
                            public void run() {
                                listener.mappingRemoved(id, newNode);
                            }                    
                        }, taskOwner);
            }
        }

        public void added(final Identity id, final Node oldNode) {
            for (final NodeMappingListener listener : nodeChangeListeners) {
                taskScheduler.scheduleTask(
                        new AbstractKernelRunnable() {
                            public void run() {
                                listener.mappingAdded(id, oldNode);
                            }                    
                        }, taskOwner);
            }
        }
    }
    
        
    /**
     *  Run the given task synchronously, and transactionally.
     * @param task the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
        taskScheduler.runTask(new TransactionRunner(task), taskOwner, true);
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
            synchronized (stateLock) {
                dataService = null;
            }
            if (serverImpl != null) {
                serverImpl.abortConfigure();
            }
            
            if (server != null) {
                try {
                    server.unregisterNodeListener(localNodeId);
                } catch (IOException ex) {
                    // We probably didn't get far enough into the configure
                    // code to actually register.  All is well.
                }
            }
        }

        /** {@inheritDoc} */
        public void commit() {
            isCommitted = true;          
            if (serverImpl != null) {
                serverImpl.commitConfigure();
            }
            synchronized(stateLock) {
                state = State.RUNNING;
            }
        }
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
