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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCoordinator;

import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.StandardProperties.StandardService;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.profile.ProfileRegistrarImpl;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;

import com.sun.sgs.impl.util.Version;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.service.DataService;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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
 * default <code>AggregateProfileListener</code> is enabled. To specify that a
 * different set of <code>ProfileListener</code>s should be used,
 * the <code>com.sun.sgs.impl.kernel.Kernel.profile.listeners</code>
 * property must be specified with a colon-separated list of fully-qualified
 * classes, each of which implements <code>ProfileListener</code>.
 */
class Kernel {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(Kernel.class.getName()));

    // the property for setting profiling levels
    private static final String PROFILE_PROPERTY =
        "com.sun.sgs.impl.kernel.Kernel.profile.level";
    // the property for setting the profile listeners
    private static final String PROFILE_LISTENERS =
        "com.sun.sgs.impl.kernel.Kernel.profile.listeners";
    // the default profile listeners
    private static final String DEFAULT_PROFILE_LISTENERS =
        "com.sun.sgs.impl.profile.listener.AggregateProfileListener";

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

    // the proxy used by all transactional components
    private static final TransactionProxy proxy = new TransactionProxyImpl();
    
    // NOTE: this transaction coordinator should not really be static;
    // when we allow external transaction coordinators, we need to
    // create a factory to create not-static instances.  
    private static TransactionCoordinator transactionCoordinator;

    // the properties used to start the application
    private final Properties appProperties;
    
    // the registration point for producers of profiling data
    private final ProfileRegistrar profileRegistrar;

    // the task handler
    private final TaskHandler taskHandler;
    
    // the scheduler
    private final MasterTaskScheduler scheduler;

    // the application that is running in this kernel
    private KernelContext application;

    // The system registry which will, by the time ready() is called,
    // contain all services and components.
    private final ComponentRegistry systemRegistry;
    
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

        this.appProperties = appProperties;

        try {
            // create the resource coordinator
            ResourceCoordinatorImpl resourceCoordinator =
                new ResourceCoordinatorImpl(appProperties);

            // see if we're doing any level of profiling, which for the
            // current version is as simple as "on" or "off"
            ProfileCollectorImpl profileCollector = null;
            String profileLevel = appProperties.getProperty(PROFILE_PROPERTY);
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

            // create the task handler and scheduler
            taskHandler =
                new TaskHandler(
                  getTransactionCoordinator(appProperties, profileCollector), 
                  profileCollector);
            
            scheduler =
                new MasterTaskScheduler(appProperties, resourceCoordinator,
                                        taskHandler, profileCollector);

            // with the scheduler created, if profiling is on then create
            // the listeners for profiling data
            if (profileCollector != null)
                loadProfileListeners(appProperties, profileCollector,
                                     scheduler, resourceCoordinator);

            // create the authentication coordinator used for the application
            ArrayList<IdentityAuthenticator> authenticators =
                new ArrayList<IdentityAuthenticator>();
            String [] authenticatorClassNames =
                appProperties.getProperty(StandardProperties.AUTHENTICATORS,
                                    DEFAULT_IDENTITY_AUTHENTICATOR).split(":");

            for (String authenticatorClassName : authenticatorClassNames) {
                try {
                    authenticators.add(getAuthenticator(authenticatorClassName,
                                                        appProperties));
                } catch (Exception e) {
                    if (logger.isLoggable(Level.SEVERE))
                        logger.logThrow(Level.SEVERE, e, "Failed to load " +
                                        "IdentityAuthenticator: {0}",
                                        authenticatorClassName);
                    throw e;
                }
            }

            IdentityCoordinator appIdentityCoordinator;
            try {
                appIdentityCoordinator = 
                        new IdentityCoordinatorImpl(authenticators);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, e,
                                    "Failed to created Identity Coordinator");
                throw e;
            }
            
            // finally, collect some of the system components to be shared
            // with services as they are created
            HashSet<Object> systemComponents = new HashSet<Object>();
            systemComponents.add(resourceCoordinator);
            systemComponents.add(scheduler);
            systemComponents.add(appIdentityCoordinator);
            
            // create the system registry for use in setting up the services
            systemRegistry = new ComponentRegistryImpl(systemComponents);
            
            // now start up the application
            createAndStartApplication();

        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Failed on Kernel boot");
            // shut down whatever we've started
            shutdown();
            throw e;
        }

	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO, "The Kernel is ready, version: {0}",
		       Version.getVersion());
	}
    }

    /**
     * Private factory for setting up our transaction coordinator. 
     * Note that we only expect to have more than one coordinator created
     * when we're running multiple stacks in a single VM, for testing.
     */
    private TransactionCoordinator getTransactionCoordinator(
                    Properties props, ProfileCollectorImpl profileCollector) 
    {
        synchronized(Kernel.class) {
            if (transactionCoordinator == null) {
                transactionCoordinator = 
                    new TransactionCoordinatorImpl(props, profileCollector);
            }
        }
        return transactionCoordinator;
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
                                                 Identity.class,
                                                 TaskScheduler.class,
                                                 ResourceCoordinator.class);

                // create a new identity for the listener
                IdentityImpl owner = new IdentityImpl(listenerClassName);

                // try to create and register the listener
                Object obj =
                    listenerConstructor.newInstance(systemProperties,
                                                    owner, taskScheduler,
                                                    resourceCoordinator);
                ProfileListener listener = (ProfileListener)obj;
                profileCollector.addListener(listener);
            } catch (InvocationTargetException e) {
                if (logger.isLoggable(Level.WARNING))
                    logger.logThrow(Level.WARNING, e.getCause(), 
                            "Failed to load ProfileListener {0} ... " +
                            "it will not be available for profiling",
                            listenerClassName);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING))
                    logger.logThrow(Level.WARNING, e, "Failed to load " +
                                    "ProfileListener {0} ... it will not " +
                                    "be available for profiling",
                                    listenerClassName);
            }
        }

        // finally, register the scheduler as a listener too
        if (taskScheduler instanceof ProfileListener)
            profileCollector.
                addListener((ProfileListener)taskScheduler);
    }

    /**
     * Helper that starts an application. This method 
     * configures the <code>Service</code>s associated with the
     * application and then starts the application.
     * 
     * @throws Exception if there is any error in startup
     */
    private void createAndStartApplication() throws Exception {
        String appName = appProperties.getProperty(StandardProperties.APP_NAME);

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: starting application", appName);

        // start the service creation 
        IdentityImpl owner = new IdentityImpl("app:" + appName);
        createServices(appName, owner);
        startApplication(appName, owner);
    }

    /**
     * Creates each of the <code>Service</code>s in order, in preparation
     * for starting up an application. At completion, this schedules an
     * <code>AppStartupRunner</code> to finish application startup.
     */
    private void createServices(String appName, Identity owner) 
        throws Exception
    {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: starting services", appName);

        // create an empty context
        ComponentRegistryImpl services = new ComponentRegistryImpl();
        ComponentRegistryImpl managers = new ComponentRegistryImpl();
        KernelContext ctx = 
                new KernelContext(appName, services, managers);

        // set as the current owner   
        ThreadState.setCurrentOwner(owner);
        taskHandler.setContext(ctx);

        // get the managers and services that we're using
        HashSet<Object> managerSet = new HashSet<Object>();
        try {
            fetchServices(services, managerSet);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "{0}: failed to create " +
                                "services", appName);
            // set the application to the ctx so far, so we can shut down
            // the services
            application = ctx;
            throw e;
        }

        // register any profiling managers and fill in the manager registry
        for (Object manager : managerSet) {
            if (profileRegistrar != null) {
                if (manager instanceof ProfileProducer)
                    ((ProfileProducer)manager).
                        setProfileRegistrar(profileRegistrar);
            }
            managers.addComponent(manager);
        }

        // with the managers created, setup the AppContext
        // the thread owner has not changed
        application = new KernelContext(appName, services, managers);
        taskHandler.setContext(application);

        // notify all of the services that the application state is ready
        try {
            for (Object s : services)
                ((Service)s).ready();
        } catch (Exception e) {
            logger.logThrow(
                Level.SEVERE, e,
                "{0}: failed when notifying services that application is " +
                "ready",
                appName);
            throw e;
        }
    }

    /**
     * Private helper that creates the services and their associated managers,
     * taking care to call out the standard services first, because we need
     * to get the ordering constant and make sure that they're all present.
     */
    private void fetchServices(ComponentRegistryImpl services,
                               HashSet<Object> managerSet) 
       throws Exception 
    {
        // before we start, figure out if we're running with only a sub-set
        // of services, in which case there should be no external services
        String finalService =
            appProperties.getProperty(StandardProperties.FINAL_SERVICE);
        StandardService finalStandardService = null;
        String externalServices =
            appProperties.getProperty(StandardProperties.SERVICES);
        String externalManagers =
            appProperties.getProperty(StandardProperties.MANAGERS);
        if (finalService != null) {
            if ((externalServices != null) || (externalManagers != null))
                throw new IllegalArgumentException(
                    "Cannot specify external services and a final service");

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
            if (! appProperties.getProperty(StandardProperties.APP_LISTENER).
                equals(StandardProperties.APP_LISTENER_NONE))
                throw new IllegalArgumentException("Cannot specify an app " +
                                                   "listener and a final " +
                                                   "service");
        } else {
            finalStandardService = StandardService.LAST_SERVICE;
        }

        // load the data service

        String dataServiceClass =
            appProperties.getProperty(StandardProperties.DATA_SERVICE,
                                      DEFAULT_DATA_SERVICE);
        String dataManagerClass =
            appProperties.getProperty(StandardProperties.DATA_MANAGER,
                                      DEFAULT_DATA_MANAGER);
        services.addComponent(setupService(dataServiceClass,
                                           dataManagerClass, managerSet));

        // load the watch-dog service, which has no associated manager

        if (StandardService.WatchdogService.ordinal() >
            finalStandardService.ordinal())
            return;

        String watchdogServiceClass =
            appProperties.getProperty(StandardProperties.WATCHDOG_SERVICE,
                                      DEFAULT_WATCHDOG_SERVICE);
        Service watchdogService =
            setupServiceNoManager(watchdogServiceClass);
        services.addComponent(watchdogService);

        // load the node mapping service, which has no associated manager

        if (StandardService.NodeMappingService.ordinal() >
            finalStandardService.ordinal())
            return;

        String nodemapServiceClass =
            appProperties.getProperty(StandardProperties.NODE_MAPPING_SERVICE,
                                      DEFAULT_NODE_MAPPING_SERVICE);
        Service nodemapService =
                setupServiceNoManager(nodemapServiceClass);
        services.addComponent(nodemapService);

        // load the task service

        if (StandardService.TaskService.ordinal() >
            finalStandardService.ordinal())
            return;

        String taskServiceClass =
            appProperties.getProperty(StandardProperties.TASK_SERVICE,
                                      DEFAULT_TASK_SERVICE);
        String taskManagerClass =
            appProperties.getProperty(StandardProperties.TASK_MANAGER,
                                      DEFAULT_TASK_MANAGER);
        services.addComponent(setupService(taskServiceClass,
                                           taskManagerClass, managerSet));

        // load the client session service, which has no associated manager

        if (StandardService.ClientSessionService.ordinal() >
            finalStandardService.ordinal())
            return;

        String clientSessionServiceClass =
            appProperties.getProperty(StandardProperties.
                                      CLIENT_SESSION_SERVICE,
                                      DEFAULT_CLIENT_SESSION_SERVICE);
        Service clientSessionService =
                setupServiceNoManager(clientSessionServiceClass);
        services.addComponent(clientSessionService);

        // load the channel service

        if (StandardService.ChannelService.ordinal() >
            finalStandardService.ordinal())
            return;

        String channelServiceClass =
            appProperties.getProperty(StandardProperties.CHANNEL_SERVICE,
                                      DEFAULT_CHANNEL_SERVICE);
        String channelManagerClass =
            appProperties.getProperty(StandardProperties.CHANNEL_MANAGER,
                                      DEFAULT_CHANNEL_MANAGER);
        services.addComponent(setupService(channelServiceClass,
                                           channelManagerClass, managerSet));

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
                    services.addComponent(setupService(serviceClassNames[i],
                                                       managerClassNames[i],
                                                       managerSet));
                } else {
                    Service service = 
                        setupServiceNoManager(serviceClassNames[i]);
                    services.addComponent(service);
                }
            }
        }
    }

    /**
     * Sets up a service with no manager based on fully qualified class name.
     */
    private Service setupServiceNoManager(String className) throws Exception {
        Class<?> serviceClass = Class.forName(className);
        Service service = createService(serviceClass);
        if ((profileRegistrar != null) &&
            (service instanceof ProfileProducer))
            ((ProfileProducer)service).
                setProfileRegistrar(profileRegistrar);
        return service;
    }
    /**
     * Creates a service with no manager based on fully qualified class names.
     */
    private Service createService(Class<?> serviceClass)
        throws Exception
    {
        // find the appropriate constructor
        Constructor<?> serviceConstructor =
            serviceClass.getConstructor(Properties.class,
                                        ComponentRegistry.class,
                                        TransactionProxy.class);

        // return a new instance
        return (Service)(serviceConstructor.
                         newInstance(appProperties, systemRegistry, proxy));
    }

    /**
     * Creates a service and its associated manager based on fully qualified
     * class names.
     */
    private Service setupService(String serviceName, String managerName,
                                 HashSet<Object> managerSet)
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
        if (managerConstructor == null)
            throw new NoSuchMethodException("Could not find a constructor " +
                                            "that accepted the Service");

        // create the manager and put it in the collection
        managerSet.add(managerConstructor.newInstance(service));

        return service;
    }
    
    /** Start the application, throwing an exception if there is a problem. */
    private void startApplication(String appName, Identity owner) 
        throws Exception
    {
        // At this point the services are now created, so the final step
        // is to try booting the application by running a special
        // KernelRunnable in an unbounded transaction. Note that if we're
        // running as a "server" then we don't actually start an app
        if (! appProperties.getProperty(StandardProperties.APP_LISTENER).
            equals(StandardProperties.APP_LISTENER_NONE)) {
            AppStartupRunner startupRunner =
                new AppStartupRunner(application, appProperties);
            UnboundedTransactionRunner unboundedTransactionRunner =
                new UnboundedTransactionRunner(startupRunner);
            try {
                if (logger.isLoggable(Level.CONFIG))
                    logger.log(Level.CONFIG, "{0}: starting application",
                               appName);
                // run the startup task, notifying the kernel on success
                scheduler.runTask(unboundedTransactionRunner, owner, true);
                logger.log(Level.INFO, 
                           "{0}: application is ready", application);
            } catch (Exception e) {
                if (logger.isLoggable(Level.CONFIG))
                    logger.logThrow(Level.CONFIG, e, "{0}: failed to " +
                                    "start application", appName);
                throw e;
            }
        } else {
            // we're running without an application, so we're finished
            logger.log(Level.INFO, "{0}: non-application context is ready",
                       application);
        }
    }
        
    /**
     * Shut down all services in this kernel in reverse
     * order of how they were started, and also shutdown the
     * task scheduler.
     */
    void shutdown() {
        if (application != null) {
            application.shutdownServices();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
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
     * Check for obvious errors in the properties file, logging and
     * throwing an {@code IllegalArgumentException} if there is a problem.
     */
    private static void checkProperties(Properties appProperties, 
                                        String configFile) 
    {
        String appName =
                appProperties.getProperty(StandardProperties.APP_NAME);

        // make sure that at least the required keys are present, and if
        // they are then start the application
        if (appName == null) {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_NAME + " from config file "
                       + configFile);
            throw new IllegalArgumentException("Missing required property " +
                    StandardProperties.APP_NAME + " from config file " +
                    configFile);
        }
        
        
        if (appProperties.getProperty(StandardProperties.APP_ROOT) == null) {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_ROOT + " for application: " +
                       appName);
            throw new IllegalArgumentException("Missing required property " +
                       StandardProperties.APP_ROOT + " for application: " +
                       appName);
        }
        
        if (appProperties.getProperty(StandardProperties.APP_LISTENER) == null) 
        {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_LISTENER +
                       "for application: " + appName);
            throw new IllegalArgumentException("Missing required property " +
                       StandardProperties.APP_LISTENER +
                       "for application: " + appName);
        }
        
        if (appProperties.getProperty(StandardProperties.APP_PORT) == null) {
            logger.log(Level.SEVERE, "Missing required property " +
                       StandardProperties.APP_PORT + " for application: " +
                       appName);
            throw new IllegalArgumentException("Missing required property " +
                       StandardProperties.APP_PORT + " for application: " +
                       appName);
        }
    }
    
    /**
     * This runnable is responsible for
     * calling the application's listener, which actually starts the application
     * running, and then reporting the successful startup to the kernel.
     * <p>
     * This runnable must be run in a transactional context.
     */
    private static final class AppStartupRunner extends AbstractKernelRunnable {
        // the context in which this will run
        private final KernelContext appContext;

        // the properties for the application
        private final Properties properties;

        /**
         * Creates an instance of <code>AppStartupRunner</code>.
         *
         * @param appContext the context in which the application will run
         * @param properties the <code>Properties</code> to provide to the
         *                   application on startup
         */
        AppStartupRunner(KernelContext appContext, Properties properties) 
        {
            this.appContext = appContext;
            this.properties = properties;
        }

        /**
         * Starts the application.
         *
         * @throws Exception if anything fails in starting the application
         */
        public void run() throws Exception {
            DataService dataService = appContext.getService(DataService.class);
            try {
                // test to see if this name if the listener is already bound...
                dataService.getServiceBinding(StandardProperties.APP_LISTENER,
                                              AppListener.class);
            } catch (NameNotBoundException nnbe) {
                // ...if it's not, create and then bind the listener
                String appClass =
                    properties.getProperty(StandardProperties.APP_LISTENER);
                AppListener listener =
                    (AppListener)(Class.forName(appClass).newInstance());
                dataService.setServiceBinding(StandardProperties.APP_LISTENER,
                                              listener);

                // since we created the listener, we're the first one to
                // start the app, so we also need to start it up
                listener.initialize(properties);
            }
        }

    }
    
        /**
     * Main-line method that starts the {@code Kernel}. Each kernel
     * instance runs a single application.
     * <p>
     * The argument on the command-line is a {@code Properties} file for the
     * application. Some properties are required to be specified in that file.
     * See {@code StandardProperties} for the required and optional properties.
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
     * @param args filename for <code>Properties</code> file associated with
     *             the application to run
     *
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String [] args) throws Exception {
        // make sure we were given an application to run
        if (args.length != 1) {
            logger.log(Level.SEVERE, "No application was provided: halting");
            System.out.println("Usage: AppPropertyFile ");
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
        
        Properties appProperties = getProperties(args[0], systemProperties);
        
        // check the standard properties
        checkProperties(appProperties, args[0]);
        
        // boot the kernel
        new Kernel(appProperties);
    }
}
