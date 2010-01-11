/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */
package com.sun.sgs.test.util;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.impl.kernel.KernelShutdownController;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A node, used for testing.  The node is created using the kernel.
 * Multiple nodes can be created within a single VM.
 *
 */
public class SgsTestNode {
    // Reflective stuff.

    /** Kernel class */
    private static Class<?> kernelClass;

    /** kernel constructor */
    private static Constructor<?> kernelCtor;
    /** kernel shutdown */
    private static Method kernelShutdownMethod;
    /** transaction proxy */
    private static Field kernelProxy;
    /** system registry */
    private static Field kernelReg;
    /** shutdown controller */
    private static Field kernelShutdownCtrl;

    static {
        try {
            kernelClass =
                Class.forName("com.sun.sgs.impl.kernel.Kernel");
            kernelCtor =  
                kernelClass.getDeclaredConstructor(Properties.class);
            kernelCtor.setAccessible(true);

            kernelShutdownMethod = 
                    kernelClass.getDeclaredMethod("shutdown");
            kernelShutdownMethod.setAccessible(true);

            kernelProxy = kernelClass.getDeclaredField("proxy");
            kernelProxy.setAccessible(true);

            kernelReg = kernelClass.getDeclaredField("systemRegistry");
            kernelReg.setAccessible(true);
            
            kernelShutdownCtrl = kernelClass.getDeclaredField("shutdownCtrl");
            kernelShutdownCtrl.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** The default initial unique port for this test suite. */
    private final static int DEFAULT_PORT = 20000;
    
    /** The property that can be used to select an initial port. */
    private final static String PORT_PROPERTY = "test.sgs.port";
    
    /** The next unique port to use for this test suite. */
    private static AtomicInteger nextUniquePort;
    
    static {
        Integer systemPort = Integer.getInteger(PORT_PROPERTY);
        int port = systemPort == null ? DEFAULT_PORT 
                                      : systemPort.intValue();
        nextUniquePort = new AtomicInteger(port);
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
    private final DataService dataService;
    private final WatchdogService watchdogService;
    private final NodeMappingService nodeMappingService;
    private final TaskService taskService;
    private final ClientSessionService sessionService;
    private final ChannelManager channelService;
    
    /** Shutdown controller. */
    private final KernelShutdownController shutdownCtrl;

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
                       Class<?> listenerClass,
                       Properties properties) throws Exception
    {
        this(appName, null, listenerClass, properties, true);
    }


    /**
     * Creates the first SgsTestNode instance in this VM.  This thread's
     * owner will be set to the owner which created this {@code SgsTestNode}.
     *
     * @param appName the application name
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults
     ** @param clean if {@code true}, make sure the data store directory is 
     *                     fresh
     */
    public SgsTestNode(String appName,
                       Class<?> listenerClass,
                       Properties properties,
                       boolean clean) throws Exception
    {
        this(appName, null, listenerClass, properties, clean);
    }

    /**
     * Creates additional SgsTestNode instances in this VM. This node will be
     * part of the same cluster as the node specified in the firstNode parameter.
     *
     * @param firstNode  the first {@code SgsTestNode} created in this VM
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults which will cause an exception to be
     *                     thrown if the standard services have been
     *                     replaced by custom implementations
     */
    public SgsTestNode(SgsTestNode firstNode,
                       Class<?> listenerClass,
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
     *                     defaults which will cause an exception to be
     *                     thrown if the standard services have been
     *                     replaced by custom implementations
     * @param clean if {@code true}, make sure the data store directory is 
     *                     fresh
     */
    public SgsTestNode(String appName, 
                SgsTestNode serverNode,
                Class<?> listenerClass,
                Properties properties,
                boolean clean) 
        throws Exception
    {
        this.appName = appName;
	this.serverNode = serverNode;
	
        if (properties == null) {
	    props = getDefaultProperties(appName, serverNode, listenerClass);
        } else {
            props = properties;
        }

        String createMBeanServer =
           props.getProperty(ProfileCollectorImpl.CREATE_MBEAN_SERVER_PROPERTY);
        if (createMBeanServer == null) {
            // User did not specify whether we should create an MBean server,
            // rather than use the default platform one.  Because this class
            // helps us create multiple nodes in a single VM, by default we'll
            // always create a new MBean server, avoiding problems with
            // MBeans already being registered in a test.
            props.setProperty(ProfileCollectorImpl.CREATE_MBEAN_SERVER_PROPERTY,
                             "true");
        }
	dbDirectory = 
	    props.getProperty("com.sun.sgs.impl.service.data.store.DataStoreImpl.directory");
        assert(dbDirectory != null);
        if (clean) {
            deleteDirectory(dbDirectory);
            createDirectory(dbDirectory);
        }

        kernel = kernelCtor.newInstance(props);
        txnProxy = (TransactionProxy) kernelProxy.get(kernel);
        systemRegistry = (ComponentRegistry) kernelReg.get(kernel);

        dataService = getService(DataService.class);
        watchdogService = getService(WatchdogService.class);
        nodeMappingService = getService(NodeMappingService.class);
        taskService = getService(TaskService.class);
        sessionService = getService(ClientSessionService.class);
        channelService = getService(ChannelServiceImpl.class);

        shutdownCtrl = (KernelShutdownController)
                kernelShutdownCtrl.get(kernel);

        // If an app node, we assume SimpleSgsProtocol and TcpTransport transport
        // for the client IO stack.
        if (sessionService != null) {
            String portProp =
                    props.getProperty(
                       com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY);
            appPort = portProp == null ?
                            com.sun.sgs.impl.transport.tcp.TcpTransport.DEFAULT_PORT :
                            Integer.parseInt(portProp);
        }
    }

    /**
     * Returns a service of the given {@code type}, or null, if no service
     * is configured for this node.
     */
    private <T extends Service> T getService(Class<T> type) {
	try {
	    return txnProxy.getService(type);
	} catch (MissingResourceException e) {
	    return null;
	}
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
    public DataService getDataService() {
	return dataService;
    }

    /**
     * Returns the watchdog service.
     */
    public WatchdogService getWatchdogService() {
	return watchdogService;
    }

    /**
     * Returns the node mapping service.
     */
    public NodeMappingService getNodeMappingService() {
	return nodeMappingService;
    }

    /**
     * Returns the node mapping server.
     */
    NodeMappingServerImpl getNodeMappingServer()
	throws Exception
    {
        Field serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);
	return (NodeMappingServerImpl) serverImplField.get(nodeMappingService);
    }
    
    /**
     * Returns the task service.
     */
    public TaskService getTaskService() {
	return taskService;
    }
    /**
     * Returns the client session service.
     */
    public ClientSessionService getClientSessionService() {
	return sessionService;
    }

    /**
     * Returns the channel service.
     */
    public ChannelManager getChannelService() {
	return channelService;
    }

    /**
     * Returns the service properties used for creating this node.
     */
    public Properties getServiceProperties() {
        return props;
    }

    /**
     * Returns the shutdown controller for this node.
     */
    public KernelShutdownController getShutdownCtrl() {
        return shutdownCtrl;
    }

    /**
     * Returns the default properties for a server node, useful for
     * adding additional properties as required.
     */
    public static Properties getDefaultProperties(String appName, 
                                           SgsTestNode serverNode,
                                           Class<?> listenerClass) 
        throws Exception
    {
        // The SgsTestNode currently starts single node (in a network config
        // for the data store) or an app node.  If a core server node is
        // desired, it's best to set the property explicitly.
        boolean isServerNode = serverNode == null;
        String nodeType = 
            isServerNode ? 
            NodeType.singleNode.toString() : 
            NodeType.appNode.toString();

        int requestedDataPort =
            isServerNode ?
            getNextUniquePort() :
            getDataServerPort((DataServiceImpl) serverNode.getDataService());

        int requestedWatchdogPort =
            isServerNode ?
            getNextUniquePort() :
            ((WatchdogServiceImpl) serverNode.getWatchdogService()).
	    	getServer().getPort();

        int requestedNodeMapPort =
            isServerNode ?
            getNextUniquePort() :
            getNodeMapServerPort(serverNode.getNodeMappingServer());

        String dir = System.getProperty("java.io.tmpdir") +
                                File.separator + appName;

        // The node mapping service requires at least one full stack
        // to run properly (it will not assign identities to a node
        // without an app listener).   Most tests only require a single
        // node, so we provide a simple app listener if the test doesn't
        // care about one.
        if (listenerClass == null) {
            listenerClass = DummyAppListener.class;
        }
        
        Properties retProps = createProperties(
            StandardProperties.APP_NAME, appName,
            StandardProperties.APP_ROOT, dir,
            StandardProperties.NODE_TYPE, nodeType,
            StandardProperties.SERVER_HOST, "localhost",
            com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY,
                String.valueOf(getNextUniquePort()),
            StandardProperties.APP_LISTENER, listenerClass.getName(),
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
                dir + ".db",
            "com.sun.sgs.impl.service.data.store.net.server.port", 
                String.valueOf(requestedDataPort),
            "com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class",
                "com.sun.sgs.impl.service.data.store.net.DataStoreClient",
            "com.sun.sgs.impl.service.watchdog.server.port",
                String.valueOf(requestedWatchdogPort),
	    "com.sun.sgs.impl.service.channel.server.port",
	        String.valueOf(getNextUniquePort()),
	    "com.sun.sgs.impl.service.session.server.port",
	        String.valueOf(getNextUniquePort()),
	    "com.sun.sgs.impl.service.nodemap.client.port",
	        String.valueOf(getNextUniquePort()),
	    "com.sun.sgs.impl.service.watchdog.client.port",
	        String.valueOf(getNextUniquePort()),
            "com.sun.sgs.impl.service.watchdog.server.renew.interval", "1500",
            "com.sun.sgs.impl.service.nodemap.server.port",
                String.valueOf(requestedNodeMapPort),
            LPADriver.GRAPH_CLASS_PROPERTY, "None",
            "com.sun.sgs.impl.service.nodemap.remove.expire.time", "1000",
            "com.sun.sgs.impl.service.task.continue.threshold", "10"
        );

        return retProps;
    }
    
    /**
     * Returns the nodeId for this test node.
     */
    public long getNodeId() {
        return getDataService().getLocalNodeId();
    }

    /**
     * Returns a bound app port.
     */
    public int getAppPort() {
	return appPort;
    }
    
    /**
     * Returns a unique port number.  Note that the ports are only unique
     * within the current process.
     */
    public static int getNextUniquePort() {
        return nextUniquePort.getAndIncrement();
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
    public static int getDataServerPort(DataServiceImpl service) 
        throws Exception
    {
        Field storeField = DataServiceImpl.class.getDeclaredField("store");
        storeField.setAccessible(true);
	DataStoreProfileProducer profileWrapper =
	    (DataStoreProfileProducer) storeField.get(service);
        DataStoreClient dsClient =
	    (DataStoreClient) profileWrapper.getDataStore();
        Field serverPortField =
	    DataStoreClient.class.getDeclaredField("serverPort");
        serverPortField.setAccessible(true);
        return (Integer) serverPortField.get(dsClient);
    }

    /**
     * Returns the bound port for the node mapping server.
     */
    private static int getNodeMapServerPort(NodeMappingServerImpl nodemapServer)
	throws Exception
    {
        Method getPortMethod = 
                NodeMappingServerImpl.class.getDeclaredMethod("getPort");
        getPortMethod.setAccessible(true);
	return (Integer) getPortMethod.invoke(nodemapServer);
    }
}
