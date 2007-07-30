/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
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
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.UnknownNodeException;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps Identities to Nodes.
 * <p>
 * In addition to the properties supported by the {@link DataServiceImpl}
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
 * <p>
 */
public class NodeMappingServiceImpl implements NodeMappingService 
{
    /** The name of this class */
    private static final String CLASSNAME = 
            NodeMappingServiceImpl.class.getName();
    
    /** Package name for this class */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /**
     * The property that specifies whether the server should be instantiated
     * in this stack.  Also used by the unit tests.
     */
    public static final String START_SERVER_PROPERTY = 
            PKG_NAME + ".start.server";
    
    /** The property name for the client port. */
    private static final String CLIENT_PORT_PROPERTY = 
            PKG_NAME + ".client.port";

    /** The default value of the server port. */
    private static final int DEFAULT_CLIENT_PORT = 0;
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The transaction proxy, or null if configure has not been called. */    
    private static TransactionProxy txnProxy;

    /** The data service. */
    private DataService dataService;
    
    /** The watchdog service. */
//    private WatchdogService watchdogService;
    
    /** The context factory for map change transactions. */
    private ContextFactory contextFactory;    

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;
    
    /** The task scheduler for non-durable tasks. */
//    private NonDurableTaskScheduler nonDurableTaskScheduler;
    
    /** The owner for tasks I initiate. */
    private TaskOwner proxyOwner;
    
    /** The registered registry change listeners. There is no need
     *  to persist these:  these are all local services, and if the
     *  node goes down, they'll need to re-register.
     */
    private final Set<NodeMappingListener> nodeChangeListeners =
	new HashSet<NodeMappingListener>();
    
    /** Lock for the change listeners.  We assume there will be many
     *  more reads to the nodeChangeListeners than writes.
     */
    private final ReadWriteLock nodeChangeListenersLock =
            new ReentrantReadWriteLock();
    private final Lock listenersRead = nodeChangeListenersLock.readLock();
    private final Lock listenersWrite = nodeChangeListenersLock.writeLock();
    
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
    private final MapChangeNotifier changeNotifier;

    /** The local node id, as determined from the watchdog */
    private long localNodeId;
    
    /** Lock object for service state */
    private final Object stateLock = new Object();
    
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
                host = wrappedProps.getProperty(
                        NodeMappingServerImpl.SERVER_HOST_PROPERTY, localHost);
                port = wrappedProps.getIntProperty(
                        NodeMappingServerImpl.SERVER_PORT_PROPERTY, 
                        NodeMappingServerImpl.DEFAULT_SERVER_PORT);
                if (port < 0 || port > 65535) {
                    throw new IllegalArgumentException(
                        "The " + NodeMappingServerImpl.SERVER_PORT_PROPERTY + 
                        " property value must be greater than or equal" +
                        " to 0 and less than 65535: " + port);
                }
            }          
          
       // JANE JANE
            // This code assumes that the server has already been started.
            // Perhaps it'd be better to block until the server is available?
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (NodeMappingServer) 
                      registry.lookup(NodeMappingServerImpl.SERVER_EXPORT_NAME);	    
            
            final int clientPort = wrappedProps.getIntProperty(
                                        CLIENT_PORT_PROPERTY, 
                                        DEFAULT_CLIENT_PORT);
            if (clientPort < 0 || clientPort > 65535) {
                    throw new IllegalArgumentException(
                        "The " + CLIENT_PORT_PROPERTY + 
                        " property value must be greater than or equal" +
                        " to 0 and less than 65535: " + clientPort);
                }
            
            changeNotifier = new MapChangeNotifier();
            try {
                // JANE JANE
                // Is this the best way to do this?  Ask Tim & Ann.
                UnicastRemoteObject.exportObject(changeNotifier, clientPort);
            } catch (RemoteException re) {
                re.printStackTrace();
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
                proxyOwner = txnProxy.getCurrentOwner();
            }
            
//          watchdogService = registry.getComponent(WatchdogService.class); 
            localNodeId = (new NodeImpl()).getId();
//          localNodeId = watchdogservice.getLocalNodeId();
            
            if (serverImpl != null) {
                serverImpl.configure(registry, txnProxy);
            } else {
                // Register myself with my server using local node id.
                // We don't register if we created the server because
                // we might not be running a full stack.
                try {
                    server.registerNodeListener(changeNotifier, localNodeId);
                } catch (IOException ex) {
                    // JANE will need to retry
                    ex.printStackTrace();
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
        // JANE need to get shutdown correct
        boolean ok = true;

        try {
            UnicastRemoteObject.unexportObject(changeNotifier, true);
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
        // cause it to shut down).
        if (serverImpl != null) {
            ok = serverImpl.shutdown();
        }
        return ok;
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
        
        if (server != null) {
            int tryCount = 0;
            
            while (!done && tryCount < MAX_RETRY) {
                try {
                    server.assignNode(service, identity);
                    System.out.println("assignnode worked on try " + tryCount);
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
                changeNotifier.added(identity, null);
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
            String statuskey = 
                    NodeMapUtil.getStatusKey(identity, localNodeId, 
                                             service.getName());
            SetStatusTask stask = 
                    new SetStatusTask(statuskey, idmo, active);
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
        final private IdentityMO idmo;
        
        /** return value, true if reference count goes to zero */
        private boolean canRemove = false;

        SetStatusTask(String statuskey, IdentityMO idmo, boolean active) {
            this.statuskey = statuskey;
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
                String name = dataService.nextServiceBoundName(statuskey);
                canRemove = (name == null || !name.startsWith(statuskey));
            }
        }
        
        boolean canRemove() { return canRemove; }
    }
    
    /** {@inheritDoc} */
    public Node getNode(Identity id) throws UnknownIdentityException {   
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
        String key = NodeMapUtil.getPartialNodeKey(nodeId);
        if (dataService.nextServiceBoundName(key) == null) {
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
        listenersWrite.lock();
        try {
            nodeChangeListeners.add(listener);
        } finally {
            listenersWrite.unlock();
        }
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
            System.out.println("LISTENER IN REMOVED");
            Set<NodeMappingListener> listeners;
            // Grab a snapshot
            listenersRead.lock();
            try {
                listeners = 
                        new HashSet<NodeMappingListener>(nodeChangeListeners);
            } finally {
                listenersRead.unlock();
            }
            
            for (final NodeMappingListener listener : listeners) {
                Runnable service = new Runnable() {
                    public void run() {
                        System.out.println("LISTENER removed: " + listener + " id: " + id + "node: " + newNode);
                        listener.mappingRemoved(id, newNode);
                    }
                };
                
                // Mark these as daemon threads, in case the callback is taking
                // a very long time and we need to shut down.
                Thread thread = new Thread(service);
                thread.setDaemon(true);
                thread.start();
                
//                This can't work, I cannot use the task service.
//                nonDurableTaskScheduler.scheduleNonTransactionalTask(new AbstractKernelRunnable() {
//                    public void run() {
//                        System.out.println("LISTENER removed: " + listener + " id: " + id + "node: " + newNode);
//                        listener.mappingRemoved(id, newNode);
//                    }
//                });
            }
        }

        public void added(final Identity id, final Node oldNode) {
            System.out.println("LISTENER IN ADDED");
            Set<NodeMappingListener> listeners;
            // Grab a snapshot
            listenersRead.lock();
            try {
                listeners = 
                        new HashSet<NodeMappingListener>(nodeChangeListeners);
            } finally {
                listenersRead.unlock();
            }
            
            for (final NodeMappingListener listener : listeners) {
                Runnable service = new Runnable() {
                    public void run() {
                        System.out.println("LISTENER added: " + listener + " id: " + id + "node: " + oldNode);
                        listener.mappingAdded(id, oldNode);
                    }
                }; 
                Thread thread = new Thread(service);
                thread.setDaemon(true);
                thread.start();
            }
        }
        
    }
    
        
    /**
     *  Run the given task synchronously, and transactionally.
     * @param task the task
     */
    private void runTransactionally(KernelRunnable task) throws Exception {
        taskScheduler.runTask(new TransactionRunner(task), proxyOwner, true);
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
                serverImpl.unconfigure();
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
                System.out.println("GET:   key is " + key + "  idmo is " + idmo);
                node = new NodeImpl(idmo.getNodeId());
//                node = watchdogService.getNode(nodeId);
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
    public boolean assertValid(Identity identity) throws Exception {
        return server.assertValid(identity);        
    }
}
