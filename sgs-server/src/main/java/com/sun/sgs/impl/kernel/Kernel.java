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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.internal.InternalContext;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCoordinator;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.kernel.StandardProperties.ServiceNodeTypes;
import com.sun.sgs.impl.kernel.StandardProperties.StandardService;

import com.sun.sgs.impl.kernel.logging.TransactionAwareLogManager;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.profile.ProfileCollectorHandleImpl;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedCollectionsImpl;
import com.sun.sgs.impl.util.Version;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.management.KernelMXBean;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileListener;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.service.WatchdogService;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import java.net.URL;

import java.lang.reflect.Constructor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import javax.management.JMException;

/**
 * This is the core class for the server. It is the first class that is
 * created, and represents the kernel of the runtime. It is responsible
 * for creating and initializing all components of the system and the
 * applications configured to run in this system.
 * <p>
 * The kernel must be configured with certain <a
 * href="../../impl/kernel/doc-files/config-properties.html#RequiredProperties">
 * required properties</a> and supports other <a
 * href="../../impl/kernel/doc-files/config-properties.html#System">public
 * properties</a>.  It can also be configured with any of the properties
 * specified in the {@link StandardProperties} class, and supports
 * the following additional configuration properties:
 * 
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #PROFILE_LEVEL_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>MIN</code>
 *
 * <dd style="padding-top: .5em">By default, the minimal amount of profiling 
 *      which is used internally by the system is enabled.  To enable more 
 *      profiling, this property must be set to a valid level for {@link 
 *      ProfileCollector#setDefaultProfileLevel(ProfileLevel)}. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #PROFILE_LISTENERS}
 *	</b></code> <br>
 *	<i>Default:</i> No Default
 *
 * <dd style="padding-top: .5em">By default, no profile listeners are enabled.
 *      To enable a set of listeners, set this property to a colon-separated
 *      list of fully-qualified class 
 *      names, each of which implements {@link ProfileListener}.  A number
 *      of listeners are provided with the system in the
 *      {@link com.sun.sgs.impl.profile.listener} package.<p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #ACCESS_COORDINATOR_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@link TrackingAccessCoordinator}</code>
 *
 * <dd style="padding-top: .5em">The implementation class used to track
 *      access to shared objects.  The value of this property should be the
 *      name of a public, non-abstract class that implements the
 *      {@link AccessCoordinatorHandle} interface, and that provides a public
 *      constructor with the three parameters {@link Properties},
 *      {@link TransactionProxy}, and {@link ProfileCollectorHandle}.<p>
 *
 * 
 * </dl>
 */
class Kernel {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(Kernel.class.getName()));

    // the property for setting profiling levels
    public static final String PROFILE_LEVEL_PROPERTY =
        "com.sun.sgs.impl.kernel.profile.level";
    // the property for setting the profile listeners
    public static final String PROFILE_LISTENERS =
        "com.sun.sgs.impl.kernel.profile.listeners";
    // The property for specifying the access coordinator
    public static final String ACCESS_COORDINATOR_PROPERTY =
	"com.sun.sgs.impl.kernel.access.coordinator";

    // the default authenticator
    private static final String DEFAULT_IDENTITY_AUTHENTICATOR =
        "com.sun.sgs.impl.auth.NullAuthenticator";

    // the default services
    private static final String DEFAULT_CHANNEL_SERVICE =
        "com.sun.sgs.impl.service.channel.ChannelServiceImpl";
    private static final String DEFAULT_CLIENT_SESSION_SERVICE =
        "com.sun.sgs.impl.service.session.ClientSessionServiceImpl";
    private static final String DEFAULT_DATA_SERVICE =
        "com.sun.sgs.impl.service.data.DataServiceImpl";
    private static final String DEFAULT_TASK_SERVICE =
        "com.sun.sgs.impl.service.task.TaskServiceImpl";
    private static final String DEFAULT_WATCHDOG_SERVICE =
        "com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl";
    private static final String DEFAULT_NODE_MAPPING_SERVICE =
        "com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl";

    // the default managers
    private static final String DEFAULT_CHANNEL_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileChannelManager";
    private static final String DEFAULT_DATA_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileDataManager";
    private static final String DEFAULT_TASK_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileTaskManager";
    
    // default timeout the kernel's shutdown method (15 minutes)
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 15 * 60000;
    
    // the proxy used by all transactional components
    private static final TransactionProxy proxy = new TransactionProxyImpl();
    
    // the properties used to start the application
    private final PropertiesWrapper wrappedProperties;

    // the schedulers used for transactional and non-transactional tasks
    private final TransactionSchedulerImpl transactionScheduler;
    private final TaskSchedulerImpl taskScheduler;

    // the application that is running in this kernel
    private KernelContext application;
    
    // The system registry which contains all shared system components
    private final ComponentRegistryImpl systemRegistry;
    
    // collector of profile information, and an associated handle
    private final ProfileCollectorImpl profileCollector;
    private final ProfileCollectorHandleImpl profileCollectorHandle;
    
    // shutdown controller that can be passed to components who need to be able 
    // to issue a kernel shutdown. the watchdog also constains a reference for
    // services to call shutdown.
    private final KernelShutdownControllerImpl shutdownCtrl = 
            new KernelShutdownControllerImpl();
    
    // specifies whether this node has already been shutdown
    private boolean isShutdown = false;
    
    /**
     * Creates an instance of <code>Kernel</code>. Once this is created
     * the code components of the system are running and ready. Creating
     * a <code>Kernel</code> will also result in initializing and starting
     * the application and its associated services.
     *
     * @param appProperties application properties
     *
     * @throws Exception if for any reason the kernel cannot be started
     */
    protected Kernel(Properties appProperties)
        throws Exception 
    {
        logger.log(Level.CONFIG, "Booting the Kernel");
                
        // filter the properties with appropriate defaults
        filterProperties(appProperties);
        
        // check the standard properties
        checkProperties(appProperties);
        this.wrappedProperties = new PropertiesWrapper(appProperties);

        try {
            // See if we're doing any profiling.
            String level = wrappedProperties.getProperty(PROFILE_LEVEL_PROPERTY,
                    ProfileLevel.MIN.name());
            ProfileLevel profileLevel;
            try {
                profileLevel = 
                        ProfileLevel.valueOf(level.toUpperCase());
                if (logger.isLoggable(Level.CONFIG)) {
                    logger.log(Level.CONFIG, "Profiling level is {0}", level);
                }
            } catch (IllegalArgumentException iae) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Unknown profile level {0}", 
                               level);
                }
                throw iae;
            }
            
            // Create the system registry
            systemRegistry = new ComponentRegistryImpl();
            
            profileCollector = new ProfileCollectorImpl(profileLevel, 
                                                        appProperties,
                                                        systemRegistry);
            profileCollectorHandle = 
                new ProfileCollectorHandleImpl(profileCollector);

            // with profiling setup, register all MXBeans
            registerMXBeans(appProperties);

            // create the authenticators and identity coordinator
            IdentityCoordinator identityCoordinator =
                    createIdentityCoordinator();

            // initialize the transaction coordinator
            TransactionCoordinator transactionCoordinator =
                new TransactionCoordinatorImpl(appProperties,
                                               profileCollectorHandle);

	    // possibly upgrade loggers to be transactional
	    LogManager logManager = LogManager.getLogManager();

	    // if the logging system has been configured to be
	    // transactional, the LogManager installed should be an
	    // instance of TransactionAwareLogManager
	    if (logManager instanceof TransactionAwareLogManager) {
		TransactionAwareLogManager txnAwareLogManager =
		    (TransactionAwareLogManager) logManager;
		txnAwareLogManager.configure(appProperties, proxy);
	    }

            // create the access coordinator
            AccessCoordinatorHandle accessCoordinator =
		wrappedProperties.getClassInstanceProperty(
		    ACCESS_COORDINATOR_PROPERTY,
		    AccessCoordinatorHandle.class,
		    new Class[] {
			Properties.class,
			TransactionProxy.class,
			ProfileCollectorHandle.class
		    },
		    appProperties, proxy, profileCollectorHandle);
	    if (accessCoordinator == null) {
		accessCoordinator = new TrackingAccessCoordinator(
		    appProperties, proxy, profileCollectorHandle);
	    }

            // create the schedulers, and provide an empty context in case
            // any profiling components try to do transactional work
            transactionScheduler =
                new TransactionSchedulerImpl(appProperties,
                                             transactionCoordinator,
                                             profileCollectorHandle,
                                             accessCoordinator);
            taskScheduler =
                new TaskSchedulerImpl(appProperties, profileCollectorHandle);

	    BindingKeyedCollections collectionsFactory =
		new BindingKeyedCollectionsImpl(proxy);
                        
            KernelContext ctx = new StartupKernelContext("Kernel");
            transactionScheduler.setContext(ctx);
            taskScheduler.setContext(ctx);

            // collect the shared system components into a registry
            systemRegistry.addComponent(accessCoordinator);
            systemRegistry.addComponent(transactionScheduler);
            systemRegistry.addComponent(taskScheduler);
            systemRegistry.addComponent(identityCoordinator);
            systemRegistry.addComponent(profileCollector);
	    systemRegistry.addComponent(collectionsFactory);

            // create the profiling listeners.  It is important to not
            // do this until we've finished adding components to the
            // system registry, as some listeners use those components.
            loadProfileListeners(profileCollector);
            
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "The Kernel is ready, version: {0}",
                        Version.getVersion());
            }

            // the core system is ready, so start up the application
            createAndStartApplication();

        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.logThrow(Level.SEVERE, e, "Failed on Kernel boot");
            }
            // shut down whatever we've started
            shutdown();
            throw e;
        }
    }

    /** Private helper that registers MXBeans for management. */
    private void registerMXBeans(Properties p) throws Exception {
        // Create the configuration MBean and register it.  This is
        // used during the construction of later components.
        ConfigManager config = new ConfigManager(p);
        try {
            profileCollector.registerMBean(config, ConfigManager.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.WARNING, e, "Could not register MBean");
            // Stop bringing up the kernel - the ConfigManager is used
            // by other parts of the system, who rely on it being 
            // successfully registered.
            throw e;
        }

        // install the MXBean that exports a shutdown interface
        KernelManager kernelManager = new KernelManager();
        try {
            profileCollector.registerMBean(kernelManager,
                                           KernelManager.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.WARNING, e, "Could not register MBean");
            throw e;
        }
    }

    /**
     * Private helper routine that loads all of the requested listeners
     * for profiling data.
     */
    private void loadProfileListeners(ProfileCollector profileCollector) {
        String listenerList = wrappedProperties.getProperty(PROFILE_LISTENERS);

        if (listenerList != null) {
            for (String listenerClassName : listenerList.split(":")) {
                try {
                    profileCollector.addListener(listenerClassName);
                } catch (InvocationTargetException e) {
                    // Strip off exceptions found via reflection
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.logThrow(Level.WARNING, e.getCause(), 
                                "Failed to load ProfileListener {0} ... " +
                                "it will not be available for profiling",
                                listenerClassName);
                    }
              
                } catch (Exception e) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.logThrow(Level.WARNING, e, 
                                "Failed to load ProfileListener {0} ... " +
                                "it will not be available for profiling",
                                 listenerClassName);
                    }
                }
            }
        }

        // finally, register the scheduler as a listener too
        // NOTE: if we make the schedulers pluggable, or add other components
        // that are listeners, then we should scan through all of the system
        // components and check if they are listeners
        profileCollector.addListener(transactionScheduler, false);
    }

    /**
     * Helper that starts an application. This method 
     * configures the <code>Service</code>s associated with the
     * application and then starts the application.
     * 
     * @throws Exception if there is any error in startup
     */
    private void createAndStartApplication() throws Exception {
        String appName = wrappedProperties.getProperty(
                StandardProperties.APP_NAME);

        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "{0}: starting application", appName);
        }

        // start the service creation 
        IdentityImpl owner = new IdentityImpl("app:" + appName);
        createServices(appName, owner);
        startApplication(appName, owner);
    }

    /**
     * Creates each of the <code>Service</code>s and their corresponding
     * <code>Manager</code>s (if any) in order, in preparation for starting
     * up an application.
     */
    private void createServices(String appName, Identity owner) 
        throws Exception
    {
        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "{0}: starting services", appName);
        }

        // create and install a temporary context to use during startup
        application = new StartupKernelContext(appName);
        transactionScheduler.setContext(application);
        taskScheduler.setContext(application);
        ContextResolver.setTaskState(application, owner);

        // tell the AppContext how to find the managers
        InternalContext.setManagerLocator(new ManagerLocatorImpl());
        
        try {
            fetchServices((StartupKernelContext) application);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.logThrow(Level.SEVERE, e, "{0}: failed to create " +
                                "services", appName);
            }
            throw e;
        }

        // with the managers fully created, swap in a permanent context
        application = new KernelContext(application);
        transactionScheduler.setContext(application);
        taskScheduler.setContext(application);
        ContextResolver.setTaskState(application, owner);
        
        // notify all of the services that the application state is ready
        try {
            application.notifyReady();
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.logThrow(Level.SEVERE, e, "{0}: failed when notifying " +
                                "services that application is ready", appName);
            }
            throw e;
        }
        
        // enable the shutdown controller once the components and services
        // are setup to allow a node shutdown call from either of them.
        shutdownCtrl.setReady();
    }

    /**
     * Private helper that creates the services and their associated managers,
     * taking care to call out the standard services first, because we need
     * to get the ordering constant and make sure that they're all present.
     */
    private void fetchServices(StartupKernelContext startupContext) 
       throws Exception 
    {
        // retrieve any specified external services
        // and fold in any extension library services
        List<String> externalServices = wrappedProperties.getListProperty(
                StandardProperties.SERVICES, String.class, "");
        List<String> extensionServices = wrappedProperties.getListProperty(
                BootProperties.EXTENSION_SERVICES_PROPERTY, String.class, "");

        List<String> externalManagers = wrappedProperties.getListProperty(
                StandardProperties.MANAGERS, String.class, "");
        List<String> extensionManagers = wrappedProperties.getListProperty(
                BootProperties.EXTENSION_MANAGERS_PROPERTY, String.class, "");

        List<ServiceNodeTypes> externalNodeTypes =
                wrappedProperties.getEnumListProperty(
                StandardProperties.SERVICE_NODE_TYPES,
                ServiceNodeTypes.class,
                ServiceNodeTypes.ALL);
        List<ServiceNodeTypes> extensionNodeTypes =
                wrappedProperties.getEnumListProperty(
                BootProperties.EXTENSION_SERVICE_NODE_TYPES_PROPERTY,
                ServiceNodeTypes.class,
                ServiceNodeTypes.ALL);

        if (externalServices.size() == 1 && externalManagers.size() == 0) {
            externalManagers.add("");
        }
        if (extensionServices.size() == 1 && extensionManagers.size() == 0) {
            extensionManagers.add("");
        }
        if (externalNodeTypes.size() == 0) {
            for (int i = 0; i < externalServices.size(); i++) {
                externalNodeTypes.add(ServiceNodeTypes.ALL);
            }
        }
        if (extensionNodeTypes.size() == 0) {
            for (int i = 0; i < extensionServices.size(); i++) {
                extensionNodeTypes.add(ServiceNodeTypes.ALL);
            }
        }

        List<String> allServices = extensionServices;
        allServices.addAll(externalServices);
        List<String> allManagers = extensionManagers;
        allManagers.addAll(externalManagers);
        List<ServiceNodeTypes> allNodeTypes = extensionNodeTypes;
        allNodeTypes.addAll(externalNodeTypes);

        NodeType type =
            NodeType.valueOf(
                wrappedProperties.getProperty(StandardProperties.NODE_TYPE));

        loadCoreServices(type, startupContext);
        loadExternalServices(allServices, 
                             allManagers,
                             allNodeTypes,
                             type,
                             startupContext);
    }

    /** Private helper used to load all core services and managers. */
    private void loadCoreServices(NodeType type,
                                  StartupKernelContext startupContext)
            throws Exception {
        StandardService finalStandardService = null;
        switch (type) {
            case appNode:
                finalStandardService = StandardService.LAST_APP_SERVICE;
                break;
            case singleNode:
                finalStandardService = StandardService.LAST_SINGLE_SERVICE;
                break;
            case coreServerNode:
                finalStandardService = StandardService.LAST_CORE_SERVICE;
                break;
            default:
                throw new IllegalArgumentException("Invalid node type : " +
                                                   type);
        }

        final int finalServiceOrdinal = finalStandardService.ordinal();

        // load the data service

        String dataServiceClass = wrappedProperties.getProperty(
                StandardProperties.DATA_SERVICE, DEFAULT_DATA_SERVICE);
        String dataManagerClass = wrappedProperties.getProperty(
                StandardProperties.DATA_MANAGER, DEFAULT_DATA_MANAGER);
        setupService(dataServiceClass, dataManagerClass, startupContext);
        // provide the node id to the shutdown controller and profile collector
        long nodeId =
            startupContext.getService(DataService.class).getLocalNodeId();
	shutdownCtrl.setNodeId(nodeId);
        profileCollectorHandle.notifyNodeIdAssigned(nodeId);

        // load the watch-dog service, which has no associated manager

        if (StandardService.WatchdogService.ordinal() > finalServiceOrdinal) {
            return;
        }

        String watchdogServiceClass = wrappedProperties.getProperty(
                StandardProperties.WATCHDOG_SERVICE, DEFAULT_WATCHDOG_SERVICE);
        setupServiceNoManager(watchdogServiceClass, startupContext);

        // provide a handle to the watchdog service for the shutdown controller
        shutdownCtrl.setWatchdogHandle(
                startupContext.getService(WatchdogService.class));

        // load the node mapping service, which has no associated manager

        if (StandardService.NodeMappingService.ordinal() > finalServiceOrdinal)
        {
            return;
        }

        String nodemapServiceClass = wrappedProperties.getProperty(
                StandardProperties.NODE_MAPPING_SERVICE,
                DEFAULT_NODE_MAPPING_SERVICE);
        setupServiceNoManager(nodemapServiceClass, startupContext);

        // load the task service

        if (StandardService.TaskService.ordinal() > finalServiceOrdinal) {
            return;
        }

        String taskServiceClass = wrappedProperties.getProperty(
                StandardProperties.TASK_SERVICE, DEFAULT_TASK_SERVICE);
        String taskManagerClass = wrappedProperties.getProperty(
                StandardProperties.TASK_MANAGER, DEFAULT_TASK_MANAGER);
        setupService(taskServiceClass, taskManagerClass, startupContext);

        // load the client session service, which has no associated manager

        if (StandardService.ClientSessionService.ordinal() >
                finalServiceOrdinal)
        {
            return;
        }

        String clientSessionServiceClass = wrappedProperties.getProperty(
                StandardProperties.CLIENT_SESSION_SERVICE,
                DEFAULT_CLIENT_SESSION_SERVICE);
        setupServiceNoManager(clientSessionServiceClass, startupContext);

        // load the channel service

        if (StandardService.ChannelService.ordinal() > finalServiceOrdinal) {
            return;
        }

        String channelServiceClass = wrappedProperties.getProperty(
                StandardProperties.CHANNEL_SERVICE, DEFAULT_CHANNEL_SERVICE);
        String channelManagerClass = wrappedProperties.getProperty(
                StandardProperties.CHANNEL_MANAGER, DEFAULT_CHANNEL_MANAGER);
        setupService(channelServiceClass, channelManagerClass, startupContext);
    }

    /** Private helper used to load all external services and managers. */
    private void loadExternalServices(List<String> externalServices,
                                      List<String> externalManagers,
                                      List<ServiceNodeTypes> externalNodeTypes,
                                      NodeType type,
                                      StartupKernelContext startupContext)
        throws Exception
    {
        if (externalServices.size() != externalManagers.size() ||
            externalServices.size() != externalNodeTypes.size()) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "External service count " +
                           "({0}), manager count ({1}), and node type count " +
                           "({2}) do not match.",
                           externalServices.size(),
                           externalManagers.size(),
                           externalNodeTypes.size());
            }
            throw new IllegalArgumentException("Mis-matched service, manager " +
                                               "and node type count");
        }

        for (int i = 0; i < externalServices.size(); i++) {
            // skip this service if it should not be started on this node type
            if (!externalNodeTypes.get(i).shouldStart(type)) {
                continue;
            }

            if (!externalManagers.get(i).equals("")) {
                setupService(externalServices.get(i), externalManagers.get(i),
                             startupContext);
            } else {
                setupServiceNoManager(externalServices.get(i), startupContext);
            }
        }
    }

    /**
     * Sets up a service with no manager based on fully qualified class name.
     */
    private void setupServiceNoManager(String className,
                                       StartupKernelContext startupContext) 
        throws Exception
    {
        Class<?> serviceClass = Class.forName(className);
        Service service = createService(serviceClass);
        startupContext.addService(service);
    }

    /**
     * Creates a service and its associated manager based on fully qualified
     * class names.
     */
    private void setupService(String serviceName, String managerName,
                              StartupKernelContext startupContext)
        throws Exception
    {
        // get the service class and instance
        Class<?> serviceClass = Class.forName(serviceName);
        Service service = createService(serviceClass);

        // resolve the class and the constructor, checking for constructors
        // by type since they likely take a super-type of Service
        Class<?> managerClass = Class.forName(managerName);
        Constructor<?> [] constructors = managerClass.getConstructors();
        Constructor<?> managerConstructor = null;
        for (int i = 0; i < constructors.length; i++) {
            Class<?> [] types = constructors[i].getParameterTypes();
            if (types.length == 1) {
                if (types[0].isAssignableFrom(serviceClass)) {
                    managerConstructor = constructors[i];
                    break;
                }
            }
        }

        // if we didn't find a matching manager constructor, it's an error
        if (managerConstructor == null) {
            throw new NoSuchMethodException("Could not find a constructor " +
                                            "that accepted the Service");
        }

        // create the manager and put it and the service in the collections
        // and the temporary startup context
        Object manager = managerConstructor.newInstance(service);
        startupContext.addService(service);
        startupContext.addManager(manager);
    }

    /**
     * Private helper that creates an instance of a <code>service</code> with
     * no manager, based on fully qualified class names.
     */
    private Service createService(Class<?> serviceClass) throws Exception {
        Constructor<?> serviceConstructor;
        try {
            // find the appropriate constructor
            serviceConstructor =
                    serviceClass.getConstructor(Properties.class,
                    ComponentRegistry.class, TransactionProxy.class);
            // return a new instance
            return (Service) (serviceConstructor.newInstance(
                    wrappedProperties.getProperties(), systemRegistry, proxy));
        } catch (NoSuchMethodException e) {
            // instead, look for a constructor with 4 parameters which is for 
            // services with shutdown privileges.
            serviceConstructor =
                    serviceClass.getConstructor(Properties.class,
                    ComponentRegistry.class, TransactionProxy.class,
                    KernelShutdownController.class);
            // return a new instance
            return (Service) (serviceConstructor.newInstance(
                    wrappedProperties.getProperties(), systemRegistry,
                    proxy, shutdownCtrl));
        }
    }

    /** Start the application, throwing an exception if there is a problem. */
    private void startApplication(String appName, Identity owner) 
        throws Exception
    {
        // at this point the services are ready, so the final step
        // is to initialize the application by running a special
        // KernelRunnable in an unbounded transaction, unless we're
        // running without an application
        NodeType type = 
            NodeType.valueOf(
                wrappedProperties.getProperty(StandardProperties.NODE_TYPE));
        if (!type.equals(NodeType.coreServerNode)) {
            try {
                if (logger.isLoggable(Level.CONFIG)) {
                    logger.log(Level.CONFIG, "{0}: starting application",
                               appName);
                }

                transactionScheduler.
                    runUnboundedTask(
                        new AppStartupRunner(wrappedProperties.getProperties()),
                        owner);

                logger.log(Level.INFO, 
                           "{0}: application is ready", application);
            } catch (Exception e) {
                if (logger.isLoggable(Level.CONFIG)) {
                    logger.logThrow(Level.CONFIG, e, "{0}: failed to " +
                                    "start application", appName);
                }
                throw e;
            }
        } else {
            // we're running without an application, so we're finished
            logger.log(Level.INFO, "{0}: non-application context is ready",
                       application);
        }
    }

    /**
     * Timer that will call {@link System#exit System.exit} after a timeout
     * period to force the process to quit if the node shutdown process takes
     * too long. The timer is started as a daemon so the task won't be run if
     * a shutdown completes successfully.
     */
    private void startShutdownTimeout(final int timeout) {
        new Timer(true).schedule(new TimerTask() {
            public void run() {
                System.exit(1);
            }
        }, timeout);
    }
    
    /**
     * Shut down all services (in reverse order) and the schedulers.
     */
    synchronized void shutdown() {
        if (isShutdown) { 
            return;
        }
        startShutdownTimeout(DEFAULT_SHUTDOWN_TIMEOUT);

        logger.log(Level.FINE, "Kernel.shutdown() called.");
        if (application != null) {
            application.shutdownServices();
        }
        if (profileCollector != null) {
            profileCollector.shutdown();
        }
        // The schedulers must be shut down last.
        if (transactionScheduler != null) {
            transactionScheduler.shutdown();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        
        logger.log(Level.FINE, "Node is shut down.");
        isShutdown = true;
    }

    /**
     * Creates the {@code IdentityCoordinator} system component used to
     * authenticate identities.  This method builds a collection of
     * {@link IdentityAuthenticator}s specified in the application's properties
     * with the {@link StandardProperties#AUTHENTICATORS} and
     * {@link BootProperties#EXTENSION_AUTHENTICATORS_PROPERTY} properties.
     * The returned {@code IdentityCoordinator} is then generated with this
     * as a backing collection of authenticators.
     */
    private IdentityCoordinator createIdentityCoordinator()
            throws Exception {
        List<String> authClassNameList =
                wrappedProperties.getListProperty(
                StandardProperties.AUTHENTICATORS, String.class, "");
        List<String> extAuthClassNameList =
                wrappedProperties.getListProperty(
                BootProperties.EXTENSION_AUTHENTICATORS_PROPERTY,
                String.class, "");

        List<String> allAuthClassNames = extAuthClassNameList;
        allAuthClassNames.addAll(authClassNameList);
        if (allAuthClassNames.isEmpty()) {
            allAuthClassNames.add(DEFAULT_IDENTITY_AUTHENTICATOR);
        }

        ArrayList<IdentityAuthenticator> authenticators =
                                         new ArrayList<IdentityAuthenticator>();
        for (String authClassName : allAuthClassNames) {
            authenticators.add(getAuthenticator(
                    authClassName,
                    wrappedProperties.getProperties()));
        }

        return new IdentityCoordinatorImpl(authenticators);
    }
    
    /**
     * Creates a new identity authenticator.
     */
    private IdentityAuthenticator getAuthenticator(String authClassName,
                                                   Properties properties)
        throws Exception
    {
        Class<?> authenticatorClass = Class.forName(authClassName);
        Constructor<?> authenticatorConstructor =
            authenticatorClass.getConstructor(Properties.class);
        return (IdentityAuthenticator) (authenticatorConstructor.
                                        newInstance(properties));
    }

    /**
     * Helper method for loading properties files with backing properties.
     */
    private static Properties loadProperties(URL resource,
                                             Properties backingProperties) 
            throws Exception
    {
        InputStream in = null;
        try {
            Properties properties;
            if (backingProperties == null) {
                properties = new Properties();
            } else {
                properties = new Properties(backingProperties);
            }
            in = resource.openStream();
            properties.load(in);
            
            return properties;
        } catch (IOException ioe) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.logThrow(Level.SEVERE, ioe, "Unable to load " +
                                "from resource {0}: ", resource);
            }
            throw ioe;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.CONFIG)) {
                        logger.logThrow(Level.CONFIG, e, "failed to close " +
                                        "resource {0}", resource);
                    }
                }
            }
        }
    }
    
    /**
     * Helper method that filters properties, loading appropriate defaults
     * if necessary.
     */
    private static Properties filterProperties(Properties properties)
        throws Exception
    {
        try {
            // Expand properties as needed.
            String value = properties.getProperty(StandardProperties.NODE_TYPE);
            if (value == null) {
                // Default is single node
                value = NodeType.singleNode.name();
                properties.setProperty(StandardProperties.NODE_TYPE, value);
            }

            // Throws IllegalArgumentException if not one of the enum types
            // but let's improve the error message
            try {
                NodeType.valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Illegal value for " +
                        StandardProperties.NODE_TYPE);
            }

            return properties;
        } catch (IllegalArgumentException iae) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.logThrow(Level.SEVERE, iae, "Illegal data in " +
                                "properties");
            }
            throw iae;
        } 
    }

    /**
     * Check for obvious errors in the properties file, logging and
     * throwing an {@code IllegalArgumentException} if there is a problem.
     */
    private static void checkProperties(Properties appProperties) 
    {
        String appName =
                appProperties.getProperty(StandardProperties.APP_NAME);

        // make sure that at least the required keys are present, and if
        // they are then start the application
        if (appName == null) {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_NAME);
            throw new IllegalArgumentException("Missing required property " +
                    StandardProperties.APP_NAME);
        }
        
        if (appProperties.getProperty(StandardProperties.APP_ROOT) == null) {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_ROOT + " for application: " +
                       appName);
            throw new IllegalArgumentException("Missing required property " +
                       StandardProperties.APP_ROOT + " for application: " +
                       appName);
        }
        
        NodeType type = 
            NodeType.valueOf(
                appProperties.getProperty(StandardProperties.NODE_TYPE));
        if (!type.equals(NodeType.coreServerNode)) {
            if (appProperties.getProperty(StandardProperties.APP_LISTENER) == 
                null)
            {
                logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_LISTENER +
                       " for application: " + appName);
                throw new IllegalArgumentException(
                       "Missing required property " +
                       StandardProperties.APP_LISTENER +
                       " for application: " + appName);
                
            }
        }
    }
    
    /**
     * This runnable calls the application's <code>initialize</code> method,
     * if it hasn't been called before, to start the application for the
     * first time. This runnable must be called in the context of an
     * unbounded transaction.
     */
    private static final class AppStartupRunner extends AbstractKernelRunnable {
        // the properties for the application
        private final Properties properties;

        /** Creates an instance of <code>AppStartupRunner</code>. */
        AppStartupRunner(Properties properties) {
	    super(null);
            this.properties = properties;
        }

        /** Starts the application, throwing an exception on failure. */
        public void run() throws Exception {
            DataService dataService =
                Kernel.proxy.getService(DataService.class);
            try {
                // test to see if this name if the listener is already bound...
                dataService.getServiceBinding(StandardProperties.APP_LISTENER);
            } catch (NameNotBoundException nnbe) {
                // ...if it's not, create and then bind the listener
                AppListener listener =
                    (new PropertiesWrapper(properties)).
                    getClassInstanceProperty(StandardProperties.APP_LISTENER,
                                             AppListener.class, new Class[] {});
                if (listener instanceof ManagedObject) {
                    dataService.setServiceBinding(
                            StandardProperties.APP_LISTENER, listener);
                } else {
                    dataService.setServiceBinding(
                            StandardProperties.APP_LISTENER,
                            new ManagedSerializable<AppListener>(listener));
                }

                // since we created the listener, we're the first one to
                // start the app, so we also need to start it up
                listener.initialize(properties);
            }
        }
    }
    
    /**
     * This is an object created by the {@code Kernel} and passed to the 
     * services and components which are given shutdown privileges. This object 
     * allows the {@code Kernel} to be referenced when a shutdown of the node is
     * necessary, such as when a service on the node has failed or has become
     * inconsistent. This class can only be instantiated by the {@code Kernel}.
     */
    private final class KernelShutdownControllerImpl implements
            KernelShutdownController 
    {
	private volatile long nodeId = -1;
        private WatchdogService watchdogSvc = null;
        private boolean shutdownQueued = false;
        private boolean isReady = false;
        private final Object shutdownQueueLock = new Object();

        /** Provides the shutdown controller with the local node id. */
        public void setNodeId(long id) {
	    nodeId = id;
        }

        /**
         * This method gives the shutdown controller a handle to the
         * {@code WatchdogService}. Components will use this handle to report a
         * failure to the watchdog service instead of shutting down directly.
         * This which ensures that the server is properly notified when a node
         * needs to be shut down. This handle can only be set once, any call
         * after that will be ignored.
         */
        public void setWatchdogHandle(WatchdogService watchdogSvc) {
            if (this.watchdogSvc != null) {
                return; // do not allow overwriting the watchdog once it's set
            }
            this.watchdogSvc = watchdogSvc;
        }
        
        /**
         * This method flags the controller as being ready to issue shutdowns.
         * If a shutdown was previously queued, then shutdown the node now.
         */
        public void setReady() {
            synchronized (shutdownQueueLock) {
                isReady = true;
                if (shutdownQueued) {
                    shutdownNode(this);
                }
            }
        }
        
        /**
         * {@inheritDoc}
         */
        public void shutdownNode(Object caller) {
            synchronized (shutdownQueueLock) {
                if (isReady) {
                    // service shutdown; we have already gone through notifying
                    // the server, so shutdown the node right now
                    if (caller instanceof WatchdogService) {
                        runShutdown();
                    } else {
                        // component shutdown; we go through the watchdog to
                        // cleanup and notify the server first
                        if (nodeId != -1 && watchdogSvc != null) {
                            watchdogSvc.
                                reportFailure(nodeId,
                                              caller.getClass().toString());
                        } else {
                            // shutdown directly if data service and watchdog
                            // have not been setup
                            runShutdown();
                        }
                    }
                } else {
                    // queue the request if the Kernel is not ready
                    shutdownQueued = true;
                }
            }
        }
        
        /**
         * Shutdown the node. This is run in a different thread to prevent a 
         * possible deadlock due to a service or component's doShutdown()
         * method waiting for the thread it was issued from to shutdown.
         * For example, the watchdog service's shutdown method would block if
         * a Kernel shutdown was called from RenewThread.
         */
        private void runShutdown() {
            logger.log(Level.WARNING, "Controller issued node shutdown.");

            new Thread(new Runnable() {
                public void run() {
                    shutdown();
                }
            }).start();
        }
    }

    /** Basic implementation of the kernel management interface. */
    public class KernelManager implements KernelMXBean {
        /** {@inheritDoc} */
        public void requestShutdown() {
            shutdownCtrl.shutdownNode(this);
        }
    }
    
    /**
     * This method is used to automatically determine an application's set
     * of configuration properties.
     */
    private static Properties findProperties(String propLoc) throws Exception {
        // load the extension configuration file as the default set of
        // properties if it exists
        Properties baseProperties = new Properties();
        String extPropFile =
            System.getProperty(BootProperties.EXTENSION_FILE_PROPERTY);
        if (extPropFile != null) {
            File extFile = new File(extPropFile);
            if (extFile.isFile() && extFile.canRead()) {
                baseProperties = loadProperties(extFile.toURI().toURL(), null);
            } else {
                logger.log(Level.SEVERE, "can't access file: " + extPropFile);
                throw new IllegalArgumentException("can't access file " +
                                                   extPropFile);
            }
        }

        // next load the application specific property file, if this exists
        URL propsIn = ClassLoader.getSystemResource(
                BootProperties.DEFAULT_APP_PROPERTIES);
        Properties appProperties = baseProperties;
        if (propsIn != null) {
            appProperties = loadProperties(propsIn, baseProperties);
        }
        
        // load the overriding set of configuration properties from the
        // file indicated by the filename argument
        Properties fileProperties = appProperties;
        if (propLoc != null && !propLoc.equals("")) {
            File propFile = new File(propLoc);
            if (!propFile.isFile() || !propFile.canRead()) {
                logger.log(Level.SEVERE, "can't access file : " + propFile);
                throw new IllegalArgumentException("can't access file " + 
                                                   propFile);
            }
            fileProperties = loadProperties(propFile.toURI().toURL(),
                                            appProperties);
        }
        
        // if a properties file exists in the user's home directory, use
        // it to override any properties
        Properties homeProperties = fileProperties;
        File homeConfig = new File(System.getProperty("user.home") +
                                   File.separator + 
                                   BootProperties.DEFAULT_HOME_CONFIG_FILE);
        if (homeConfig.isFile() && homeConfig.canRead()) {
            homeProperties = loadProperties(homeConfig.toURI().toURL(),
                                            fileProperties);
        } else if (homeConfig.isFile() && !homeConfig.canRead()) {
            logger.log(Level.WARNING, "can't access file : " + homeConfig);
        }
        
        // override any properties with the values from the System properties
        Properties finalProperties = new Properties(homeProperties);
        finalProperties.putAll(System.getProperties());

        return finalProperties;
    }
    
    /**
     * Main-line method that starts the {@code Kernel}. Each kernel
     * instance runs a single application.
     * <p>
     * If a single argument is given, the value of the argument is assumed 
     * to be a filename.  This file
     * is used in combination with additional configuration settings to 
     * determine an application's configuration properties.
     * <p>
     * The order of precedence for properties is as follows (from highest
     * to lowest):
     * <ol>
     * <li>System properties specified on the command line using a 
     * "-D" flag</li>
     * <li>Properties specified in the file from the user's home directory
     * with the name specified by
     * {@link BootProperties#DEFAULT_HOME_CONFIG_FILE}.</li>
     * <li>Properties specified in the file given as a command line 
     * argument.</li>
     * <li>Properties specified in the resource with the name 
     * {@link BootProperties#DEFAULT_APP_PROPERTIES}
     * This file is typically included as part of the application jar file.</li>
     * <li>Properties specified in the file named by the optional
     * {@link BootProperties#EXTENSION_FILE_PROPERTY} system property.</li>
     * </ol>
     * 
     * If no value is specified for a given property in any of these places,
     * then a default is used or an <code>Exception</code> is thrown
     * (depending on whether a default value is available). Certain
     * properties are required to be specified somewhere in the application's
     * configuration.
     * <p>
     * See {@link StandardProperties} for the required and optional properties.
     * 
     * @param args optional filename for <code>Properties</code> file 
     *             associated with the application to run
     *
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String [] args) throws Exception {
        // ensure we don't have too many arguments
        if (args.length > 1) {
            logger.log(Level.SEVERE, "Invalid number of arguments: halting");
            System.exit(1);
        }
        
        // if an argument is specified on the command line, use it
        // for the value of the filename
        Properties appProperties;
        if (args.length == 1) {
            appProperties = findProperties(args[0]);
        } else {
            appProperties = findProperties(null);
        }
        
        // boot the kernel
        new Kernel(appProperties);
    }

}
