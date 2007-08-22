/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.kernel.StandardProperties.StandardService;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;

import java.lang.reflect.Constructor;

import java.util.HashSet;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>KernelRunnable</code> is one of two runnables
 * used when an application is starting up. This runnable is resposible for
 * creating all of the application's <code>Service</code>s, and then
 * scheduling a <code>AppStartupRunner</code> to start the application.
 */
class ServiceConfigRunner implements Runnable {

    // the base type of this class
    private static final String BASE_TYPE =
        ServiceConfigRunner.class.getName();

    // logger for this class
    private final static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(BASE_TYPE));

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

    // the reference back to the kernel
    private final Kernel kernel;

    // the system components available for services
    private final ComponentRegistry systemRegistry;

    // the optional registrar for profiling data
    private final ProfileRegistrar profileRegistrar;

    // the proxy that provides transaction state
    private final TransactionProxy proxy;

    // the name of the application
    private final String appName;

    // the properties that are passed to the app on startup
    private final Properties appProperties;

    /**
     * Creates an instance of <code>ServiceConfigRunner</code>.
     *
     * @param kernel the kernel that started this runnable
     * @param systemRegistry the system components available to services
     * @param profileRegistrar the registrar used for profiling, or
     *                         {@code null} if profiling is disabled
     * @param proxy the proxy used to access the current transaction
     * @param appName the name of the application being started
     * @param appProperties the <code>Properties</code> provided to the
     *                      application on startup
     */
    ServiceConfigRunner(Kernel kernel, ComponentRegistry systemRegistry,
                        ProfileRegistrar profileRegistrar,
                        TransactionProxy proxy, String appName,
                        Properties appProperties) {
        this.kernel = kernel;
        this.systemRegistry = systemRegistry;
        this.profileRegistrar = profileRegistrar;
        this.proxy = proxy;
        this.appName = appName;
        this.appProperties = appProperties;
    }

    /**
     * Creates each of the <code>Service</code>s in order, in preparation
     * for starting up an application. At completion, this schedules an
     * <code>AppStartupRunner</code> to finish application startup.
     */
    public void run() {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: starting services", appName);

        // create an empty context and register with the scheduler
        ComponentRegistryImpl services = new ComponentRegistryImpl();
        AppKernelAppContext ctx = 
                new AppKernelAppContext(appName, services, services);
        MasterTaskScheduler scheduler =
            systemRegistry.getComponent(MasterTaskScheduler.class);
        try {
            scheduler.registerApplication(ctx, appProperties);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "{0}: failed app scheduler " +
                                "setup", appName);
            return;
        }

        // create the application's identity, and set as the current owner
        IdentityImpl id = new IdentityImpl("app:" + appName);
        TaskOwnerImpl owner = new TaskOwnerImpl(id, ctx);
        ThreadState.setCurrentOwner(owner);

        // get the managers and services that we're using
        HashSet<Object> managerSet = new HashSet<Object>();
        try {
            fetchServices(services, managerSet);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "{0}: failed to create " +
                                "services", appName);
            // shutdown each of the created services
            for (Object s : services)
                ((Service)s).shutdown();
            return;
        }

        // register any profiling managers and fill in the manager registry
        ComponentRegistryImpl managers = new ComponentRegistryImpl();
        for (Object manager : managerSet) {
            if (profileRegistrar != null) {
                if (manager instanceof ProfileProducer)
                    ((ProfileProducer)manager).
                        setProfileRegistrar(profileRegistrar);
            }
            managers.addComponent(manager);
        }

        // with the managers created, setup the final context and owner
        ctx = new AppKernelAppContext(appName, services, managers);
        owner = new TaskOwnerImpl(id, ctx);
        ThreadState.setCurrentOwner(owner);

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
	    // shutdown all of the services
	    for (Object s : services)
		((Service)s).shutdown();
	    return;
	}

        // At this point the services are now created, so the final step
        // is to try booting the application by running a special
        // KernelRunnable in an unbounded transaction. Note that if we're
        // running as a "server" then we don't actually start an app
        if (! appProperties.getProperty(StandardProperties.APP_LISTENER).
            equals(StandardProperties.APP_LISTENER_NONE)) {
            AppStartupRunner startupRunner =
                new AppStartupRunner(ctx, appProperties);
            UnboundedTransactionRunner unboundedTransactionRunner =
                new UnboundedTransactionRunner(startupRunner);
            try {
                if (logger.isLoggable(Level.CONFIG))
                    logger.log(Level.CONFIG, "{0}: starting application",
                               appName);
                // run the startup task, notifying the kernel on success
                scheduler.runTask(unboundedTransactionRunner, owner, true);
                kernel.contextReady(ctx, true);
            } catch (Exception e) {
                if (logger.isLoggable(Level.CONFIG))
                    logger.logThrow(Level.CONFIG, e, "{0}: failed to " +
                                    "start application", appName);
                return;
            }
        } else {
            // we're running without an application, so we're finished
            kernel.contextReady(ctx, false);
        }

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: finished service config runner",
                       appName);
    }

    /**
     * Private helper that creates the services and their associated managers,
     * taking care to call out the standard services first, because we need
     * to get the ordering constant and make sure that they're all present.
     */
    private void fetchServices(ComponentRegistryImpl services,
                               HashSet<Object> managerSet) throws Exception {
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
            createService(Class.forName(watchdogServiceClass));
        services.addComponent(watchdogService);
        if (watchdogService instanceof ProfileProducer) {
            if (profileRegistrar != null)
                ((ProfileProducer) watchdogService).
                    setProfileRegistrar(profileRegistrar);
        }

        // load the node mapping service, which has no associated manager

        if (StandardService.NodeMappingService.ordinal() >
            finalStandardService.ordinal())
            return;
	
        String nodemapServiceClass =
            appProperties.getProperty(StandardProperties.NODE_MAPPING_SERVICE,
                                      DEFAULT_NODE_MAPPING_SERVICE);
        Service nodemapService =
            createService(Class.forName(nodemapServiceClass));
        services.addComponent(nodemapService);
        if (nodemapService instanceof ProfileProducer) {
            if (profileRegistrar != null)
                ((ProfileProducer) nodemapService).
                    setProfileRegistrar(profileRegistrar);
        }
        
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
            createService(Class.forName(clientSessionServiceClass));
        services.addComponent(clientSessionService);
        if (clientSessionService instanceof ProfileProducer) {
            if (profileRegistrar != null)
                ((ProfileProducer)clientSessionService).
                    setProfileRegistrar(profileRegistrar);
        }

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
                    Class<?> serviceClass = Class.forName(serviceClassNames[i]);
                    Service service = createService(serviceClass);
                    services.addComponent(service);
                    if ((profileRegistrar != null) &&
                        (service instanceof ProfileProducer))
                        ((ProfileProducer)service).
                            setProfileRegistrar(profileRegistrar);
                }
            }
        }
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

}
