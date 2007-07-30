/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.IdentityAuthenticator;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.kernel.StandardProperties.StandardService;

import com.sun.sgs.impl.kernel.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.kernel.profile.ProfileRegistrarImpl;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.impl.util.Version;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.ProfileCollector;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.Service;

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the core class for the server. It is the first class that is
 * created, and represents the kernel of the runtime. It is responsible
 * for creating and initializing all components of the system and the
 * applications configured to run in this system.
 * <p>
 * By default, profiling is not turned on. To enable profiling, the kernel
 * property <code>com.sun.sgs.impl.kernel.Kernel.profile.level</code> must
 * be given the value "on". If no profile listeners are specified, then the
 * default <code>AggregateProfileOpListener</code> and
 * <code>SnapshotProfileOpListener</code> are enabled. To specify that a
 * different set of <code>ProfileOperationListener</code>s should be used,
 * the <code>com.sun.sgs.impl.kernel.Kernel.profile.listeners</code>
 * property must be specified with a colon-separated list of fully-qualified
 * classes, each of which implements <code>ProfileOperationListener</code>.
 */
class Kernel {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(Kernel.class.getName()));

    /**
     * A utility owner for all system tasks.
     */
    static final TaskOwnerImpl TASK_OWNER =
        new TaskOwnerImpl(SystemIdentity.IDENTITY,
                          SystemKernelAppContext.CONTEXT);

    // the collection of core system components
    private final HashSet<Object> systemComponents;

    // the proxy used by all transactional components
    private final TransactionProxyImpl transactionProxy;

    // the registration point for producers of profiling data
    private final ProfileRegistrarImpl profileRegistrar;

    // the set of applications that are running in this kernel
    private HashSet<AppKernelAppContext> applications;

    // the property for setting profiling levels
    private static final String PROFILE_PROPERTY =
        "com.sun.sgs.impl.kernel.Kernel.profile.level";
    // the property for setting the profile listeners
    private static final String PROFILE_LISTENERS =
        "com.sun.sgs.impl.kernel.Kernel.profile.listeners";
    // the default profile listeners
    private static final String DEFAULT_PROFILE_LISTENERS =
        "com.sun.sgs.impl.kernel.profile.AggregateProfileOpListener:" +
        "com.sun.sgs.impl.kernel.profile.SnapshotProfileOpListener:" +
	"com.sun.sgs.impl.kernel.profile.SnapshotParticipantListener";

    // the default services
    private static final String DEFAULT_CHANNEL_SERVICE =
        "com.sun.sgs.impl.service.channel.ChannelServiceImpl";
    private static final String DEFAULT_CLIENT_SESSION_SERVICE =
        "com.sun.sgs.impl.service.session.ClientSessionServiceImpl";
    private static final String DEFAULT_DATA_SERVICE =
        "com.sun.sgs.impl.service.data.DataServiceImpl";
    private static final String DEFAULT_NODE_MAPPING_SERVICE =
        "com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl";
    private static final String DEFAULT_TASK_SERVICE =
        "com.sun.sgs.impl.service.task.TaskServiceImpl";

    // the default managers
    private static final String DEFAULT_CHANNEL_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileChannelManager";
    private static final String DEFAULT_DATA_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileDataManager";
    private static final String DEFAULT_TASK_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfileTaskManager";

    // the default authenticator
    private static final String DEFAULT_IDENTITY_AUTHENTICATOR =
        "com.sun.sgs.impl.auth.NullAuthenticator";

    /**
     * Creates an instance of <code>Kernel</code>. Once this is created
     * the code components of the system are running and ready. Creating
     * a <code>Kernel</code> will also result in initializing and starting
     * all associated applications and their associated services.
     *
     * @param systemProperties system <code>Properties</code> for all
     *                         system-level components
     *
     * @throws Exception if for any reason the kernel cannot be started
     */
    protected Kernel(Properties systemProperties) throws Exception {
        logger.log(Level.CONFIG, "Booting the Kernel");

        // initialize our data structures
        systemComponents = new HashSet<Object>();
        applications = new HashSet<AppKernelAppContext>();

        // setup the system components
        try {
            // create the resource coordinator
            ResourceCoordinatorImpl resourceCoordinator =
                new ResourceCoordinatorImpl(systemProperties);

            // see if we're doing any level of profiling, which for the
            // current version is as simple as "on" or "off"
            ProfileCollectorImpl profileCollector = null;
            String profileLevel =
                systemProperties.getProperty(PROFILE_PROPERTY);
            if (profileLevel != null) {
                if (profileLevel.equals("on")) {
                    logger.log(Level.CONFIG, "System profiling is on");
                    profileCollector =
                        new ProfileCollectorImpl(resourceCoordinator);
                    profileRegistrar =
                        new ProfileRegistrarImpl(profileCollector);
                } else {
                    profileRegistrar = null;
                    if (! profileLevel.equals("off")) {
                        if (logger.isLoggable(Level.WARNING))
                            logger.log(Level.WARNING, "Unknown profile " +
                                       "level {0} ... all profiling will be " +
                                       "turned off", profileLevel);
                    }
                }
            } else {
                profileRegistrar = null;
            }

            // create the transaction proxy and coordinator
            transactionProxy = new TransactionProxyImpl();
            TransactionCoordinatorImpl transactionCoordinator =
                new TransactionCoordinatorImpl(systemProperties,
                                               profileCollector);

            // create the task handler and scheduler
            TaskHandler taskHandler =
                new TaskHandler(transactionCoordinator, profileCollector);
            MasterTaskScheduler scheduler =
                new MasterTaskScheduler(systemProperties, resourceCoordinator,
                                        taskHandler, profileCollector,
                                        SystemKernelAppContext.CONTEXT);

            // with the scheduler created, if profiling is on then create
            // the listeners for profiling data
            if (profileCollector != null)
                loadProfileListeners(systemProperties, profileCollector,
                                     scheduler, resourceCoordinator);

            // finally, collect some of the system components to be shared
            // with services as they are created
            systemComponents.add(resourceCoordinator);
            systemComponents.add(scheduler);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Failed on Kernel boot");
            throw e;
        }

	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO, "The Kernel is ready, version: {0}",
		       Version.getVersion());
	}
    }

    /**
     * Private helper routine that loads all of the requested listeners
     * for profiling data.
     */
    private void loadProfileListeners(Properties systemProperties,
                                      ProfileCollector profileCollector,
                                      TaskScheduler taskScheduler,
                                      ResourceCoordinator resourceCoordinator)
    {
        String listenerList =
            systemProperties.getProperty(PROFILE_LISTENERS,
                                         DEFAULT_PROFILE_LISTENERS);

        for (String listenerClassName : listenerList.split(":")) {
            try {
                // make sure we can resolve the listener
                Class<?> listenerClass = Class.forName(listenerClassName);
                Constructor<?> listenerConstructor =
                    listenerClass.getConstructor(Properties.class,
                                                 TaskOwner.class,
                                                 TaskScheduler.class,
                                                 ResourceCoordinator.class);

                // create a new identity for the listener
                TaskOwnerImpl owner =
                    new TaskOwnerImpl(new IdentityImpl(listenerClassName),
                                      SystemKernelAppContext.CONTEXT);

                // try to create and register the listener
                Object obj =
                    listenerConstructor.newInstance(systemProperties,
                                                    owner, taskScheduler,
                                                    resourceCoordinator);
                ProfileOperationListener listener =
                    (ProfileOperationListener)obj;
                profileCollector.addListener(listener);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING))
                    logger.logThrow(Level.WARNING, e, "Failed to load " +
                                    "ProfileOperationListener {0} ... " +
                                    "it will not be available for profiling",
                                    listenerClassName);
            }
        }

        // finally, register the scheduler as a listener too
        if (taskScheduler instanceof ProfileOperationListener)
            profileCollector.
                addListener((ProfileOperationListener)taskScheduler);
    }

    /**
     * Package-private helper that starts an application. This method will
     * ensure that all the components are availabe, create them, and then
     * start a separate task that will run in a transactional context to
     * actually configure the <code>Service</code>s associated with the
     * application.
     *
     * @param properties the <code>Properties</code> for the application
     *
     * @throws Exception if there is any error in startup
     */
    void startupApplication(Properties properties) throws Exception {
        String appName = properties.getProperty(StandardProperties.APP_NAME);

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: configuring application", appName);

        // create the authentication manager used for this application
        ArrayList<IdentityAuthenticator> authenticators =
            new ArrayList<IdentityAuthenticator>();
        String [] authenticatorClassNames =
            properties.getProperty(StandardProperties.AUTHENTICATORS,
                                   DEFAULT_IDENTITY_AUTHENTICATOR).split(":");

        for (String authenticatorClassName : authenticatorClassNames) {
            try {
                authenticators.add(getAuthenticator(authenticatorClassName,
                                                    properties));
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, e, "Failed to load " +
                                    "IdentityAuthenticator: {0}",
                                    authenticatorClassName);
                throw e;
            }
        }

        IdentityManagerImpl appIdentityManager;
        try {
            appIdentityManager = new IdentityManagerImpl(authenticators);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Failed to created IdentityManager");
            throw e;
        }

        // now that we have the app's authenticators, create a system
        // registry to use in setting up the services
        HashSet<Object> appSystemComponents =
            new HashSet<Object>(systemComponents);
        appSystemComponents.add(appIdentityManager);
        ComponentRegistryImpl systemRegistry =
            new ComponentRegistryImpl(appSystemComponents);

        // resolve the scheduler
        MasterTaskScheduler scheduler =
            systemRegistry.getComponent(MasterTaskScheduler.class);

        // get the managers and services that we're using
        ArrayList<Service> serviceList = new ArrayList<Service>();
        HashSet<Object> managerSet = new HashSet<Object>();
        ComponentRegistryImpl managerComponents = new ComponentRegistryImpl();
        try {
            fetchServices(serviceList, managerSet, systemRegistry, properties);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Could not setup services");
            throw e;
        }

        // register any profiling managers and fill in the manager registry
        for (Object manager : managerSet) {
            if (profileRegistrar != null) {
                if (manager instanceof ProfileProducer)
                    ((ProfileProducer)manager).
                        setProfileRegistrar(profileRegistrar);
            }
            managerComponents.addComponent(manager);
        }

        // finally, register the application with the master scheduler
        // and kick off a task to do the transactional configuration step,
        // where the services are configured...this in turn will actually
        // start the application running
        AppKernelAppContext appContext =
            new AppKernelAppContext(appName, managerComponents);
        try {
            scheduler.registerApplication(appContext, properties);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Couldn't setup app scheduler");
            throw e;
        }
        ServiceConfigRunner configRunner =
            new ServiceConfigRunner(this, serviceList, transactionProxy,
                                    appName, properties);
        UnboundedTransactionRunner unboundedTransactionRunner =
            new UnboundedTransactionRunner(configRunner);
        IdentityImpl appIdentity = new IdentityImpl("app:" + appName);
        TaskOwnerImpl owner = new TaskOwnerImpl(appIdentity, appContext);
        try {
            scheduler.scheduleTask(unboundedTransactionRunner, owner);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Could not start configuration");
            throw e;
        }
    }

    /**
     * Private helper that creates the services and their associated managers,
     * taking care to call out the standard services first, because we need
     * to get the ordering constant and make sure that they're all present.
     */
    private void fetchServices(ArrayList<Service> serviceList,
                               HashSet<Object> managerSet,
                               ComponentRegistryImpl systemRegistry,
                               Properties properties) throws Exception {
        // before we start, figure out if we're running with only a sub-set
        // of services, in which case there should be no external services
        String finalService =
            properties.getProperty(StandardProperties.FINAL_SERVICE);
        StandardService finalStandardService = null;
        String externalServices =
            properties.getProperty(StandardProperties.SERVICES);
        String externalManagers =
            properties.getProperty(StandardProperties.MANAGERS);
        if (finalService != null) {
            if ((externalServices != null) || (externalManagers != null))
                throw new IllegalArgumentException("Cannot specify external " +
                                                   "services and a final " +
                                                   "service");

            // validate the final service
            try {
                finalStandardService =
                    Enum.valueOf(StandardService.class, finalService);
            } catch (IllegalArgumentException iae) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, iae, "Invalid final " +
                                    "service name: {0}", finalService);
                throw iae;
            }

            // make sure we're not running with an application
            if (! properties.getProperty(StandardProperties.APP_LISTENER).
                equals(StandardProperties.APP_LISTENER_NONE))
                throw new IllegalArgumentException("Cannot specify an app " +
                                                   "listener and a final " +
                                                   "service");
        } else {
            finalStandardService = StandardService.LAST_SERVICE;
        }

        String dataServiceClass =
            properties.getProperty(StandardProperties.DATA_SERVICE,
                                   DEFAULT_DATA_SERVICE);
        String dataManagerClass =
            properties.getProperty(StandardProperties.DATA_MANAGER,
                                   DEFAULT_DATA_MANAGER);
        setupService(dataServiceClass, serviceList,
                     dataManagerClass, managerSet, properties,
                     systemRegistry);

        if (StandardService.ClientSessionService.ordinal() >
            finalStandardService.ordinal())
            return;

        // the ClientSessionService is a special case, since it has no
        // manager, so when created it's also registered for profiling,
        // if appropriate
        String nodeMappingServiceClass =
            properties.getProperty(StandardProperties.
                                   NODE_MAPPING_SERVICE,
                                   DEFAULT_NODE_MAPPING_SERVICE);
        Service nodeMappingService =
            createService(Class.forName(nodeMappingServiceClass),
                          properties, systemRegistry);
        serviceList.add(nodeMappingService);
        if (nodeMappingService instanceof ProfileProducer) {
            if (profileRegistrar != null)
                ((ProfileProducer)nodeMappingService).
                    setProfileRegistrar(profileRegistrar);
        }
        
        if (StandardService.TaskService.ordinal() >
            finalStandardService.ordinal())
            return;

        String taskServiceClass =
            properties.getProperty(StandardProperties.TASK_SERVICE,
                                   DEFAULT_TASK_SERVICE);
        String taskManagerClass =
            properties.getProperty(StandardProperties.TASK_MANAGER,
                                   DEFAULT_TASK_MANAGER);
        setupService(taskServiceClass, serviceList,
                     taskManagerClass, managerSet, properties,
                     systemRegistry);

        if (StandardService.ClientSessionService.ordinal() >
            finalStandardService.ordinal())
            return;

        // the ClientSessionService is a special case, since it has no
        // manager, so when created it's also registered for profiling,
        // if appropriate
        String clientSessionServiceClass =
            properties.getProperty(StandardProperties.
                                   CLIENT_SESSION_SERVICE,
                                   DEFAULT_CLIENT_SESSION_SERVICE);
        Service clientSessionService =
            createService(Class.forName(clientSessionServiceClass),
                          properties, systemRegistry);
        serviceList.add(clientSessionService);
        if (clientSessionService instanceof ProfileProducer) {
            if (profileRegistrar != null)
                ((ProfileProducer)clientSessionService).
                    setProfileRegistrar(profileRegistrar);
        }

        if (StandardService.ChannelService.ordinal() >
            finalStandardService.ordinal())
            return;

        String channelServiceClass =
            properties.getProperty(StandardProperties.CHANNEL_SERVICE,
                                   DEFAULT_CHANNEL_SERVICE);
        String channelManagerClass =
            properties.getProperty(StandardProperties.CHANNEL_MANAGER,
                                   DEFAULT_CHANNEL_MANAGER);
        setupService(channelServiceClass, serviceList,
                     channelManagerClass, managerSet, properties,
                     systemRegistry);

        // finally, load any external services and their associated managers
        if ((externalServices != null) && (externalManagers != null)) {
            String [] serviceClassNames = externalServices.split(":", -1);
            String [] managerClassNames = externalManagers.split(":", -1);
            if (serviceClassNames.length != managerClassNames.length) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.log(Level.SEVERE, "External service count " +
                               "({0}) does not match manager count ({1}).",
                               serviceClassNames.length,
                               managerClassNames.length);
                throw new IllegalArgumentException("Mis-matched service " +
                                                   "and manager count");
            }
            
            for (int i = 0; i < serviceClassNames.length; i++) {
                if (! managerClassNames[i].equals("")) {
                    setupService(serviceClassNames[i], serviceList,
                                 managerClassNames[i], managerSet,
                                 properties, systemRegistry);
                } else {
                    Class<?> serviceClass =
                        Class.forName(serviceClassNames[i]);
                    Service service =
                        createService(serviceClass, properties, systemRegistry);
                    // since this Service has no Manager, configure it
                    // for profiling now (if applicable)
                    if ((profileRegistrar != null) &&
                        (service instanceof ProfileProducer))
                        ((ProfileProducer)service).
                            setProfileRegistrar(profileRegistrar);
                    serviceList.add(service);
                }
            }
        }
    }

    /**
     * Creates a service with no manager based on fully qualified class names.
     */
    private Service createService(Class<?> serviceClass,
                                  Properties serviceProperties,
                                  ComponentRegistryImpl systemRegistry)
        throws Exception
    {
        // find the class and constructor
        Constructor<?> serviceConstructor =
            serviceClass.getConstructor(Properties.class,
                                        ComponentRegistry.class);

        // return a new instance
        return (Service)(serviceConstructor.newInstance(serviceProperties,
                                                        systemRegistry));
    }

    /**
     * Creates a service and its associated manager based on fully qualified
     * class names.
     */
    private void setupService(String serviceName,
                              ArrayList<Service> serviceList,
                              String managerName, HashSet<Object> managerSet,
                              Properties serviceProperties,
                              ComponentRegistryImpl systemRegistry)
        throws Exception
    {
        // get the service class and instance
        Class<?> serviceClass = Class.forName(serviceName);
        Service service =
            createService(serviceClass, serviceProperties, systemRegistry);

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
        if (managerConstructor == null)
            throw new NoSuchMethodException("Could not find a constructor " +
                                            "that accepted the Service");

        // create the manager, and put both service and manager in their
        // respective collections
        managerSet.add(managerConstructor.newInstance(service));
        serviceList.add(service);
    }

    /**
     * Creates a new identity authenticator.
     */
    private IdentityAuthenticator getAuthenticator(String className,
                                                   Properties properties)
        throws Exception
    {
        Class<?> authenticatorClass = Class.forName(className);
        Constructor<?> authenticatorConstructor =
            authenticatorClass.getConstructor(Properties.class);
        return (IdentityAuthenticator)(authenticatorConstructor.
                                       newInstance(properties));
    }

    /**
     * Called when a context has finished loading and, if there is an
     * associated application, the application has started to run. This
     * is typically called by <code>AppStartupRunner</code> after it has
     * started an application or <code>ServiceConfigRunner</code> when
     * a context with no application is ready.
     *
     * @param context the application's kernel context
     * @param hasApplication <code>true</code> if the context is associated
     *                       with a running application, <code>false</code>
     *                       otherwise 
     */
    void contextReady(AppKernelAppContext context, boolean hasApplication) {
        applications.add(context);
        if (logger.isLoggable(Level.INFO)) {
            if (hasApplication)
                logger.log(Level.INFO, "{0}: application is ready", context);
            else
                logger.log(Level.INFO, "{0}: non-application context is ready",
                           context);
        }
    }

    /**
     * Helper method for loading properties files.
     */
    private static Properties getProperties(String filename) throws Exception {
        return getProperties(filename, null);
    }

    /**
     * Helper method for loading properties files with backing properties.
     */
    private static Properties getProperties(String filename,
                                            Properties backingProperties)
        throws Exception
    {
        FileInputStream inputStream = null;
        try {
            Properties properties;
            if (backingProperties == null)
                properties = new Properties();
            else
                properties = new Properties(backingProperties);
            inputStream = new FileInputStream(filename);
            properties.load(inputStream);
            return properties;
        } catch (IOException ioe) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, ioe, "Unable to load " +
                                "properties file {0}: ", filename);
            throw ioe;
        } catch (IllegalArgumentException iae) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, iae, "Illegal data in " +
                                "properties file {0}: ", filename);
            throw iae;
        } finally {
            if (inputStream != null)
                try {
                    inputStream.close();
                } catch (IOException e) {
                    if (logger.isLoggable(Level.CONFIG))
                        logger.logThrow(Level.CONFIG, e, "failed to close "+
                                        "property file {0}", filename);
                }
        }
    }

    /**
     * Main-line method that starts the <code>Kernel</code>. Note that right
     * now there is no management for the stack, so we accept on the
     * command-line the set of applications to run. Once we have management
     * and configration facilities, this command-line list will be removed.
     * <p>
     * Each argument on the command-line is a <code>Properties</code> file for
     * an application to run. For each application some properties are
     * required to be specified in that file. For required and optional
     * properties see <code>StandardProperties</code>.
     * <p>
     * The order of precedence for properties is as follows. If a value is
     * provided for a given property key by the application's configuration,
     * then that value takes precedence over any others. If no value is
     * provided by the application's configuration, then the system
     * property value, if specified (typically provided on the command-line
     * using a "-D" flag) is used. Failing this, the value from the system
     * config file (if a file is specified) is used. If no value is specified
     * for a given property in any of these places, then a default is used
     * or an <code>Exception</code> is thrown (depending on whether a default
     * value is available).
     * 
     * @param args filenames for <code>Properties</code> files associated with
     *             each application to run
     *
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String [] args) throws Exception {
        // make sure we were given an application to run
        if (args.length < 1) {
            logger.log(Level.SEVERE, "No applications were provided: halting");
            System.out.println("Usage: AppPropertyFile [AppPropertyFile ...]");
            System.exit(0);
        }

        // start by loading from a config file (if one was provided), and
        // then merge in the system properties
        Properties systemProperties = null;
        String propertiesFile =
            System.getProperty(StandardProperties.CONFIG_FILE);
        if (propertiesFile != null)
            systemProperties = getProperties(propertiesFile);
        else
            systemProperties = new Properties();
        systemProperties.putAll(System.getProperties());

        // make sure that no application name was specified yet
        if (systemProperties.containsKey(StandardProperties.APP_NAME)) {
            logger.log(Level.SEVERE, "Key" + StandardProperties.APP_NAME +
                       " may not be specified in the system properties ");
            throw new IllegalArgumentException("Application name was " +
                                               "specified in system " +
                                               "properties");
        }
        
        // boot the kernel
        Kernel kernel = new Kernel(systemProperties);

        // setup and run each application
        for (String appPropertyFile : args) {
            Properties appProperties =
                getProperties(appPropertyFile, systemProperties);
            String appName =
                appProperties.getProperty(StandardProperties.APP_NAME);

            // make sure that at least the required keys are present, and if
            // they are then start the application
            if (appName == null) {
                logger.log(Level.SEVERE, "Missing required property " +
                           StandardProperties.APP_NAME + " from config: " +
                           appPropertyFile + " ... skipping startup");
            } else if (appProperties.
                getProperty(StandardProperties.APP_ROOT) == null) {
                logger.log(Level.SEVERE, "Missing required property " +
                           StandardProperties.APP_ROOT + " for application: " +
                           appName + " ... skipping startup");
            }
            else if (appProperties.
                getProperty(StandardProperties.APP_LISTENER) == null) {
                logger.log(Level.SEVERE, "Missing required property " +
                           StandardProperties.APP_LISTENER +
                           "for application: " + appName +
                           " ... skipping startup");
            }
            else if (appProperties.
                getProperty(StandardProperties.APP_PORT) == null) {
                logger.log(Level.SEVERE, "Missing required property " +
                           StandardProperties.APP_PORT + " for application: " +
                           appName + " ... skipping startup");
            } else {
                // the properties are in order, so startup the application
                if (logger.isLoggable(Level.CONFIG))
                    logger.log(Level.CONFIG, "Starting up application: {0}",
                               appName);
                try {
                    kernel.startupApplication(appProperties);
                } catch (Exception e) {
                    if (logger.isLoggable(Level.SEVERE))
                        logger.logThrow(Level.SEVERE, e, "{0}: startup failed",
                                        appName);
                }
            }
        }
    }

}
