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
 * The {@link #NodeMappingServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property. 
 * <p>
 */
public class NodeMappingServiceImpl implements NodeMappingService 
{
    /** The name of this class */
    private static final String CLASSNAME = 
            NodeMappingServiceImpl.class.getName();
    
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
    private MapContextFactory contextFactory; 
    

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;
    
    /** The task scheduler for non-durable tasks. */
//    private NonDurableTaskScheduler nonDurableTaskScheduler;
    
    /** The owner for tasks I initiate. */
    private TaskOwner proxyOwner;
    
    /** The registered registry change listeners. */
    private final Set<NodeMappingListener> nodeChangeListeners =
	new HashSet<NodeMappingListener>();
    
    private final ReadWriteLock nodeChangeListenersLock =
            new ReentrantReadWriteLock();
    private final Lock listenersRead = nodeChangeListenersLock.readLock();
    private final Lock listenersWrite = nodeChangeListenersLock.writeLock();
    
    private final MapChangeNotifier changeNotifier;
    
    /** The remote backend to this service. */
    private final NodeMappingServer server;
    
    /** The implementation of the server, if we're on the special node. */
    private final NodeMappingServerImpl serverImpl;
    
    /** The local node id, as determined from the watchdog */
    private long localNodeId;
    
    /** Lock object for service state */
    private final Object lock = new Object();
    
    /** The number of times we should try to contact the backend before
     *  giving up.  This needs to be a property.  See what the data store
     *  does.
     *   JANE JANE
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
                                  NodeMapUtil.getStartServerProperty(), false);
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
                        NodeMapUtil.getServerHostProperty(), localHost);
                port = wrappedProps.getIntProperty(
                        NodeMapUtil.getServerPortProperty(), 
                        NodeMapUtil.getDefaultServerPort());
                if (port < 0 || port > 65535) {
                    throw new IllegalArgumentException(
                        "The " + NodeMapUtil.getServerPortProperty() + 
                        " property value must be greater than or equal" +
                        " to 0 and less than 65535: " + port);
                }
            }          
          
            // This code assumes that the server has already been started.
            // Perhaps it'd be better to block until the server is available?
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (NodeMappingServer) 
                        registry.lookup(NodeMapUtil.getServerExportName());
		    
            // Need a property for my port
            final int clientport = port + 1;
            changeNotifier = new MapChangeNotifier();
            try {
                // Is this the best way to do this?  Ask Tim & Ann.
                UnicastRemoteObject.exportObject(changeNotifier, clientport);
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

	    synchronized (lock) {
		if (dataService != null) {
		    throw new IllegalStateException("Already configured");
		}
                (new ConfigureServiceContextFactory(txnProxy)).
 	                    joinTransaction();
                dataService = registry.getComponent(DataService.class);
                
                contextFactory = new MapContextFactory(txnProxy);
                proxyOwner = txnProxy.getCurrentOwner();
//                nonDurableTaskScheduler = 
//                        new NonDurableTaskScheduler(taskScheduler, proxyOwner, 
//                                registry.getComponent(TaskService.class));
            }
            
            if (serverImpl != null) {
                serverImpl.configure(registry, txnProxy);
            }

            // JANE is there not an issue with the server perhaps pumping
            // out notifications before I've registered?  I don't think
            // there's much to do about that, so some add/drop stuff might
            // get lost.  Likewise, when we're coming up, services will register
            // with me during their configuration - but I shouldn't pump
            // out any of my notifications until after the commit of this
            // transaction.  My server should really use the transaction,
            // then, and wait until commit before performing any updates.
            // Same thinking applies to the watchdog?

//                watchdogService = registry.getComponent(WatchdogService.class); 
            localNodeId = 0;
//                localNodeId = watchdogService.getLocalNodeId();

            // Register myself with my server using local node id
            try {
                server.registerNodeListener(changeNotifier, localNodeId);
            } catch (IOException ex) {
                // JANE will need to retry
                ex.printStackTrace();
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
        
        // See if there's already a mapping - if so, service wants this
        // to be marked active, and return.
        
        
        // JANE I'm not sure this is really useful.  Are there deadlock problems
        // lurking?  Is the setStatus part going to be too complicated?
        
        
//        final String name = service.getName();   
//        final String idkey = NodeMapUtil.getIdentityKey(identity);
//        boolean found = true;
//        
//        try {
//            runTransactionally(new AbstractKernelRunnable() {
//                public void run() {  
//                
//                    IdentityMO idmo = dataService.getServiceBinding(idkey, IdentityMO.class);
//
//                    System.out.println("assignNode found in db already " + idkey);
//                    String statuskey = 
//                        NodeMapUtil.getStatusKey(idmo.getIdentity(),
//                                                 idmo.getNodeId(), name);
//                    dataService.setServiceBinding(statuskey, idmo);
//            }});
//            
//
//        } catch (NameNotBoundException nnbe) {
//            found = false;
//        } catch (ObjectNotFoundException onfe) {
//            found = false;
//        } catch (Exception e) {
//            // JANE?
//        }
//        
//        boolean done = found;
        boolean done = false;
        
        if (server != null) {
            int tryCount = 0;
            
            while (!done && tryCount < MAX_RETRY) {
                System.out.println("JANE TRYING TO ASSIGN");
                try {
                    server.assignNode(service, identity);
                    System.out.println("assignnode worked on try " + tryCount);
                    done = true;
                    tryCount = MAX_RETRY;
                } catch (IOException ioe) {
                    tryCount++;
                    logger.logThrow(Level.FINEST, ioe, 
                            "Exception encountered on try {0}: {1}",
                            tryCount, ioe);
                }
            }
        }
        
        if (!done) {
            logger.log(Level.FINEST, "Assigning {0} to this node", identity);

            AssignNodeLocallyRunnable localTask = 
                    new AssignNodeLocallyRunnable(service.getName(), 
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
            
            // JANE need to devise a way to let the server know that
            // we changed things behind its back.
            // How do we connect to a new server, once we've disconnected?
        }
    }
    
//    private class AssignNodeRunnable implements KernelRunnable {
//        private final String key;
//        private final String name;
//        /** return value, true assignment already in the map */
//        private boolean found = false;
//        
//        AssignNodeRunnable(String name, Identity id) {
//            key = NodeMapUtil.getIdentityKey(id);
//            this.name = name;
//        }
//        
//        public String getBaseTaskType() {
//            return this.getClass().getName();
//        }
//        
//        public void run() {  
//            try {
//                IdentityMO idmo = dataService.getServiceBinding(key, IdentityMO.class);
//
//                System.out.println("assignNode found in db already " + key);
//                String statuskey = 
//                    NodeMapUtil.getStatusKey(idmo.getIdentity(),
//                                             idmo.getNodeId(), name);
//                dataService.setServiceBinding(statuskey, idmo);
//                found = true;  
//            } catch (NameNotBoundException e) {
//                // do nothing
//            }
//        }
//
//        boolean found() { return found; }
//    }
    
    /**
     * Task for assigning an identity to this local node. 
     * Currently, this is only used when we cannot contact the server.
     */
    private class AssignNodeLocallyRunnable implements KernelRunnable {
        private final String idkey;
        private final String nodekey;
        private final String statuskey;
        private IdentityMO idmo;
        /** return value, true if id added to map */
        private boolean added = true;
        
        AssignNodeLocallyRunnable(String name, Identity id, Long nodeId) {
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
            // Update reference count no matter what
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
        
        if (idmo != null) {
            String statuskey = 
                    NodeMapUtil.getStatusKey(identity, localNodeId, 
                                             service.getName());
            SetStatusRunnable stask = 
                    new SetStatusRunnable(statuskey, idmo, active);
            try {
                runTransactionally(stask);
            } catch (NameNotBoundException nnbe) {
                // Ignore.  This can be thrown if active = false, and this is
                // our second call to this method.  JANE TEST THIS
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
                    // Put something in data store?  In case I can't contact
                    // the server?
                    server.canRemove(identity);
                } catch (IOException ioe) {
                    // JANE?
                }
            }
            logger.log(Level.FINEST, "setStatus key:{0}", statuskey);
        }
    }
    
    /**
     * Task for setting a status and returning information about
     * whether the identity is considered dead by this node.
     */
    private class SetStatusRunnable implements KernelRunnable {
        final private boolean active;
        final private String statuskey;
        final private IdentityMO idmo;
        
        /** return value, true if reference count goes to zero */
        private boolean canRemove = false;

        SetStatusRunnable(String statuskey, IdentityMO idmo, boolean active) {
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
                // if this is our second time calling this method
                // but the server hasn't deleted the identity yet.
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
            synchronized (lock) {
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

    private class MapContextFactory
	extends TransactionContextFactory<Context>
    {
	MapContextFactory(TransactionProxy txnProxy) {
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
    

}
