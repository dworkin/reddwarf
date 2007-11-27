/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.TransactionProxy;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * A node, used for testing.  The node is created using the kernel.
 * Multiple nodes can be created within a single VM.
 *
 */
public class SgsTestNode {
    // Reflective stuff.
    
    /** Kernel class */
    private static Class kernelClass;
    
    /** kernel constructor */
    private static Constructor kernelCtor;
    /** application startup method */
    private static Method kernelStartupMethod;
    /** kernel shutdown */
    private static Method kernelShutdownMethod;
    /** transaction proxy */
    private static Field kernelProxy;
    /** system registry */
    private static Field kernelReg;
    
    static {
        try {
            kernelClass =
                Class.forName("com.sun.sgs.impl.kernel.Kernel");
            kernelCtor =  
                kernelClass.getDeclaredConstructor(Properties.class);
            kernelCtor.setAccessible(true);

            kernelStartupMethod = 
                    kernelClass.getDeclaredMethod("startupApplication", 
                                                  Properties.class);
            kernelStartupMethod.setAccessible(true);

            kernelShutdownMethod = 
                    kernelClass.getDeclaredMethod("shutdown");
            kernelShutdownMethod.setAccessible(true);
            
            kernelProxy = kernelClass.getDeclaredField("transactionProxy");
            kernelProxy.setAccessible(true);

            kernelReg = kernelClass.getDeclaredField("systemRegistry");
            kernelReg.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /** The app name. */
    private final String appName;

    /** The server node, or null. */
    private final SgsTestNode serverNode;
    
    /** The service properties. */
    public final Properties props;

    /** The name of the DB directory. */
    private final String dbDirectory;
    
    private final Object kernel;
    private final TransactionProxy txnProxy;
    private final ComponentRegistry systemRegistry;
    
    /** Services. */
    private final DataServiceImpl dataService;
    private final WatchdogServiceImpl watchdogService;
    private final NodeMappingServiceImpl nodeMappingService;
    private final TaskServiceImpl taskService;
    private final ClientSessionServiceImpl sessionService;
    private final ChannelServiceImpl channelService;
    
    /** The listen port for the client session service. */
    private int appPort;
    
    /**
     * Creates the first SgsTestNode instance in this VM.  This thread's
     * owner will be set to the owner which created this {@code SgsTestNode}.
     *
     * @param appName the application name
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults
     */
    public SgsTestNode(String appName,
                       Class listenerClass,
                       Properties properties) throws Exception
    {
        this(appName, null, listenerClass, properties, true);
    }
    
    /**
     * Creates additional SgsTestNode instances in this VM.
     *
     * @param firstNode  the first {@code SgsTestNode} created in this VM
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults
     */
    public SgsTestNode(SgsTestNode firstNode,
                       Class listenerClass,
                       Properties properties) throws Exception
    {
        this (firstNode.appName, firstNode, listenerClass, properties, false);
    }
    
    /**
     * Creates a new instance of SgsTestNode.
     *   
     * 
     * @param appName the application name
     * @param serverNode  the instance which created the servers,
     *                    {@code null} if this instance should create them.
     *                    If {@code null}, this thread's owner is set to the
     *                    owner which creates this {@code SgsTestNode}
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults
     * @param clean if {@code true}, make sure the data store directory is 
     *                     fresh
     */
    protected SgsTestNode(String appName, 
                SgsTestNode serverNode,
                Class listenerClass,
                Properties properties,
                boolean clean) 
        throws Exception
    {
        this.appName = appName;
	this.serverNode = serverNode;
	
        // The node mapping service requires at least one full stack
        // to run properly (it will not assign identities to a node
        // without an app listener).   Most tests only require a single
        // node, so we provide a simple app listener if the test doesn't
        // care about one.
        if (listenerClass == null) {
            listenerClass = DummyAppListener.class;
        }
	
        boolean isServerNode = serverNode == null;
        if (properties == null) {      
            String startServer = String.valueOf(isServerNode);

            int requestedDataPort =
                isServerNode ?
                0 :
                getDataServerPort(serverNode.getDataService());

            int requestedWatchdogPort =
                isServerNode ?
                0 :
                serverNode.getWatchdogService().getServer().getPort();

            int requestedNodeMapPort =
                isServerNode ?
                0 :
                getNodeMapServerPort(serverNode.getNodeMappingService());
            
            dbDirectory = System.getProperty("java.io.tmpdir") +
                                    File.separator +  appName + ".db";
            
            props = createProperties(
                "com.sun.sgs.app.name", appName,
                "com.sun.sgs.app.port", "0",
                "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
                    dbDirectory,
                "com.sun.sgs.impl.service.data.store.net.server.run", 
                    startServer,
                "com.sun.sgs.impl.service.data.store.net.server.port", 
                    String.valueOf(requestedDataPort),
                "com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class",
                    "com.sun.sgs.impl.service.data.store.net.DataStoreClient",
                "com.sun.sgs.impl.service.data.store.net.server.host", 
                    "localhost",
                "com.sun.sgs.impl.service.watchdog.server.start", startServer,
                "com.sun.sgs.impl.service.watchdog.server.port",
                    String.valueOf(requestedWatchdogPort),
                "com.sun.sgs.impl.service.watchdog.renew.interval", "500",
                "com.sun.sgs.impl.service.nodemap.server.start", startServer,
                "com.sun.sgs.impl.service.nodemap.server.port",
                    String.valueOf(requestedNodeMapPort),
                "com.sun.sgs.impl.service.nodemap.remove.expire.time", "250",
                StandardProperties.APP_LISTENER, listenerClass.getName()
                );
        } else {
            props = properties;
            dbDirectory = 
                props.getProperty("com.sun.sgs.impl.service.data.store.DataStoreImpl.directory");
        }
        
        assert(dbDirectory != null);
        if (clean) {
            deleteDirectory(dbDirectory);
            createDirectory(dbDirectory);
        }
        
        kernel = kernelCtor.newInstance(props);
        kernelStartupMethod.invoke(kernel, props);

        txnProxy = (TransactionProxy) kernelProxy.get(kernel);
        systemRegistry = (ComponentRegistry) kernelReg.get(kernel);
        
        dataService = txnProxy.getService(DataServiceImpl.class);
        watchdogService = txnProxy.getService(WatchdogServiceImpl.class);
        nodeMappingService = txnProxy.getService(NodeMappingServiceImpl.class);
        taskService = txnProxy.getService(TaskServiceImpl.class);
        sessionService = txnProxy.getService(ClientSessionServiceImpl.class);
        channelService = txnProxy.getService(ChannelServiceImpl.class);
                
        appPort = sessionService.getListenPort();
    }
    
    /**
     * Shut down this SgsTestNode.
     *
     * @param clean if {@code true}, also delete the data store directory
     */
    public void shutdown(boolean clean) throws Exception {
        kernelShutdownMethod.invoke(kernel);
        if (clean) {
            deleteDirectory(dbDirectory);
        }
    }
    
    /**
     * A simple application listener, used one is not specified when this
     * SgsTestNode is constructed.  Note that the node mapping service
     * notes only "full" stacks as being available for node assignment, so
     * we need to include an application listener.
     */
    public static class DummyAppListener implements AppListener, Serializable {

	private final static long serialVersionUID = 1L;

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {
            return null;
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}
    }
    
    /**
     * Returns the transaction proxy.
     */
    public TransactionProxy getProxy() {
        return txnProxy;
    }

    /**
     * Returns the system registry.
     */
    public ComponentRegistry getSystemRegistry() {
        return systemRegistry;
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
     * Returns the task service.
     */
    public TaskServiceImpl getTaskService() {
	return taskService;
    }
    /**
     * Returns the client session service.
     */
    public ClientSessionServiceImpl getClientSessionService() {
	return sessionService;
    }
    
    /**
     * Returns the channel service.
     */
    public ChannelServiceImpl getChannelService() {
	return channelService;
    }
    
    /**
     * Returns the service properties used for creating this node.
     */
    public Properties getServiceProperties() {
        return props;
    }
    
    /**
     * Returns the bound app port.
     */
    public int getAppPort() {
	return appPort;
    }
    
    /** Creates the specified directory, if it does not already exist. */
    private static void createDirectory(String directory) {
        File dir = new File(directory);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new RuntimeException(
                    "Problem creating directory: " + dir);
            }
        }
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
     * Returns the bound port for the data server.
     */
    private static int getDataServerPort(DataServiceImpl service) 
        throws Exception
    {
        Field storeField = DataServiceImpl.class.getDeclaredField("store");
        storeField.setAccessible(true);
        DataStoreClient dsClient = (DataStoreClient) storeField.get(service);
        
        Field serverPortField = DataStoreClient.class.getDeclaredField("serverPort");
        serverPortField.setAccessible(true);
        return (Integer) serverPortField.get(dsClient);
        
    }
    
    /**
     * Returns the bound port for the node mapping server.
     */
    private static int getNodeMapServerPort(
        NodeMappingServiceImpl nodemapService)
	throws Exception
    {
        Field serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);
        Method getPortMethod = 
                NodeMappingServerImpl.class.getDeclaredMethod("getPort");
        getPortMethod.setAccessible(true);
	NodeMappingServerImpl server =
	    (NodeMappingServerImpl) serverImplField.get(nodemapService);
	return (Integer) getPortMethod.invoke(server);
    }
}
