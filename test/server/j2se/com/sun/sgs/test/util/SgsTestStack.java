/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.auth.IdentityManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.WatchdogService;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect. Method;
import java.util.Properties;

/**
 * Encapsulates an SGS services stack for testing purposes.
 */
public class SgsTestStack {

    /** The app name. */
    private final String appName;

    /** The server stack, or null. */
    private final SgsTestStack serverStack;
    
    /** The service properties. */
    public final Properties serviceProps;

    /** The name of the DB directory. */
    private final String dbDirectory;

    /** Kernel/transaction-related test components. */
    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();
    private DummyAbstractKernelAppContext appContext;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;

    /** Services. */
    private DataServiceImpl dataService;
    private WatchdogServiceImpl watchdogService;
    private NodeMappingServiceImpl nodeMappingService;
    private TaskServiceImpl taskService;
    private ClientSessionServiceImpl sessionService;
    private ChannelServiceImpl channelService;
    private DummyIdentityManager identityManager;

    /** The listen port for the client session service. */
    private int appPort;

    /**
     * Constructs an instance with the specified {@code appName} and
     * {@code serverStack}.  This constructor intializes the {@code
     * serviceProps} for all services.  If {@code serverStack} is
     * null, then the appropriate properties for starting the watchdog
     * server and nodemap server are set to "true".  To set up
     * services, call the {@link #setup setup} method.  If additional
     * service properties need to be set before the services are
     * created, the properties can be added to {@code serviceProps}.
     *
     * @param	appName the app name
     * @param	serverStack the server stack, or null
     */
    public SgsTestStack(String appName, SgsTestStack serverStack)
	throws Exception
    {
	this.appName = appName;
	this.serverStack = serverStack;
	boolean isServerNode = serverStack == null;
	String startServer = String.valueOf(isServerNode);
	
	dbDirectory = "java.io.tmpdir." + appName + ".db";
	int requestedWatchdogPort =
	    isServerNode ?
	    0 :
	    serverStack.getWatchdogService().getServer().getPort();
	int requestedNodeMapPort =
	    isServerNode ?
	    0 :
	    getNodeMapServerPort(serverStack.getNodeMappingService());
	
        serviceProps = createProperties(
	    "com.sun.sgs.app.name", appName,
	    "com.sun.sgs.app.port", "0",
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    	dbDirectory,
            "com.sun.sgs.impl.service.watchdog.server.start", startServer,
	    "com.sun.sgs.impl.service.watchdog.server.port",
			  String.valueOf(requestedWatchdogPort),
	    "com.sun.sgs.impl.service.watchdog.renew.interval", "1000",
	    "com.sun.sgs.impl.service.nodemap.server.start", startServer,
	    "com.sun.sgs.impl.service.nodemap.server.port",
	        String.valueOf(requestedNodeMapPort)
	    );
    }

    /**
     * Sets up all services.  If the {@code serverStack} for this
     * instance is null, then this stack is the "server stack" and
     * this method creates all services, starting up both the watchdog
     * server and nodemap server.  If the {@code serverStack} is non-null,
     * then all services are created (without starting servers) except
     * that the data service in this stack is set to the data service
     * in the {@code serverStack} specified during construction.
     *
     * @param	clean if {@code true}, the data store directory is
     *		removed before the data service is created
     */
    public void setUp(boolean clean) throws Exception {
	appContext = MinimalTestKernel.createContext();
	systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
	serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
	    
	// create data service
	if (clean) {
	    deleteDirectory(dbDirectory);
	}
	dataService =
	    (serverStack != null) ?
	    serverStack.getDataService() :
	    createDataService(systemRegistry);

        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// create watchdog service
	watchdogService =
	    new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy);
	txnProxy.setComponent(WatchdogService.class, watchdogService);
	txnProxy.setComponent(WatchdogServiceImpl.class, watchdogService);
	serviceRegistry.setComponent(WatchdogService.class, watchdogService);
	serviceRegistry.setComponent(
	    WatchdogServiceImpl.class, watchdogService);

	// create node mapping service
        nodeMappingService = new NodeMappingServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
        txnProxy.setComponent(NodeMappingService.class, nodeMappingService);
        txnProxy.setComponent(
	    NodeMappingServiceImpl.class, nodeMappingService);
        serviceRegistry.setComponent(
	    NodeMappingService.class, nodeMappingService);
        serviceRegistry.setComponent(
	    NodeMappingServiceImpl.class, nodeMappingService);
	
	// create identity manager
	identityManager = new DummyIdentityManager();
	systemRegistry.setComponent(IdentityManager.class, identityManager);
	serviceRegistry.setComponent(IdentityManager.class, identityManager);
				     
	// create task service
	taskService =
	    new TaskServiceImpl(serviceProps, systemRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);

	// create client session service
	sessionService =
	    new ClientSessionServiceImpl(
 		serviceProps, systemRegistry, txnProxy);
	serviceRegistry.setComponent(
	    ClientSessionService.class, sessionService);
	txnProxy.setComponent(
	    ClientSessionService.class, sessionService);
	appPort = sessionService.getListenPort();
	
	// create channel service
	channelService = new ChannelServiceImpl(
	    serviceProps, systemRegistry, txnProxy);
	txnProxy.setComponent(ChannelServiceImpl.class, channelService);
	serviceRegistry.setComponent(ChannelManager.class, channelService);
	serviceRegistry.setComponent(ChannelServiceImpl.class, channelService);
	
	// TBD: does this need to be done?
	serviceRegistry.registerAppContext();
	
	// services ready
	dataService.ready();
	watchdogService.ready();
	nodeMappingService.ready();
	taskService.ready();
	sessionService.ready();
	channelService.ready();
    }

    /**
     * Shuts down all services.  If {@code clean} is {@code true},
     * removes the data store directory.
     */
    public void tearDown(boolean clean) throws Exception {
        if (txn != null) {
            try {
                txn.abort(null);
            } catch (IllegalStateException e) {
            }
            txn = null;
        }
        if (channelService != null) {
            channelService.shutdown();
            channelService = null;
        }
        if (sessionService != null) {
            sessionService.shutdown();
            sessionService = null;
        }
        if (taskService != null) {
            taskService.shutdown();
            taskService = null;
        }
	if (nodeMappingService != null) {
	    nodeMappingService.shutdown();
	    nodeMappingService = null;
	}
	if (watchdogService != null) {
	    watchdogService.shutdown();
	    watchdogService = null;
	}
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
        if (clean) {
            deleteDirectory(dbDirectory);
        }
        MinimalTestKernel.destroyContext(appContext);
    }

    /**
     * Returns the data service.
     */
    public DataServiceImpl getDataService() {
	return dataService;
    }

    /**
     * Returns the watchdog service.
     */
    public WatchdogServiceImpl getWatchdogService() {
	return watchdogService;
    }

    /**
     * Returns the node mapping service.
     */
    public NodeMappingServiceImpl getNodeMappingService() {
	return nodeMappingService;
    }

    /**
     * Returns the identity manager.
     */
    public DummyIdentityManager getIdentityManager() {
	return identityManager;
    }

    /**
     * Returns the bound app port.
     */
    public int getAppPort() {
	return appPort;
    }
    
    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    public DummyTransaction createTransaction() {
	if (txn == null) {
	    txn = new DummyTransaction();
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    /**
     * Creates a new transaction with the specified timeout, and sets
     * transaction proxy's current transaction
     */
    public DummyTransaction createTransaction(long timeout) {
	if (txn == null) {
	    txn = new DummyTransaction(timeout);
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    /**
     * Aborts the current transaction.
     *
     * @param	e the exception of the aborted transaction, or null
     * @throws	TransactionNotActiveException if no transaction exists
     */
    public void abortTransaction(Exception e) {
	if (txn != null) {
	    txn.abort(e);
	    txn = null;
	    txnProxy.setCurrentTransaction(null);
	} else {
	    throw new TransactionNotActiveException("txn:" + txn);
	}
    }

    /**
     * Commits the current transaction.
     * @throws	TransactionNotActiveException if no transaction exists
     */
    public void commitTransaction() throws Exception {
	if (txn != null) {
	    txn.commit();
	    txn = null;
	    txnProxy.setCurrentTransaction(null);
	} else {
	    throw new TransactionNotActiveException("txn:" + txn);
	}
    }
    
    /**
     * Creates a property list with the specified keys and values.
     *
     * @return the properties
     */
    public static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }
 
    /**
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(serviceProps, registry, txnProxy);
    }

    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }

    /**
     * Returns the port for the node mapping server.
     */
    private static int getNodeMapServerPort(
	NodeMappingServiceImpl nodmapService)
	throws Exception
    {
        Field serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);
        Method getPortMethod = 
                NodeMappingServerImpl.class.getDeclaredMethod("getPort");
        getPortMethod.setAccessible(true);
	NodeMappingServerImpl server =
	    (NodeMappingServerImpl) serverImplField.get(nodmapService);
	return (Integer) getPortMethod.invoke(server);
    }
}
