
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;

import com.sun.sgs.auth.IdentityAuthenticator;

import com.sun.sgs.impl.app.profile.ProfilingManager;

import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.auth.NamePasswordAuthenticator;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionRunner;

import java.io.File;

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
 * NOTE: there are many TEST comments in the implementation of this class.
 * This is because there is a static set of configuration parameters
 * hard-coded here until we develop an external source of configuration.
 *
 * @since 1.0
 * @author Seth Proctor
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

    // the set of applications that are running in this kernel
    private HashSet<AppKernelAppContext> applications;

    // the default services that are used for channels, data, and tasks
    private static final String DEFAULT_CHANNEL_SERVICE =
        "com.sun.sgs.impl.service.channel.ChannelServiceImpl";
    private static final String DEFAULT_DATA_SERVICE =
        "com.sun.sgs.impl.service.data.DataServiceImpl";
    private static final String DEFAULT_TASK_SERVICE =
        "com.sun.sgs.impl.service.task.TaskServiceImpl";

    // the default managers that are used for channels, data, and tasks
    private static final String DEFAULT_CHANNEL_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingChannelManager";
    private static final String DEFAULT_DATA_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingDataManager";
    private static final String DEFAULT_TASK_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingTaskManager";

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
            // create the transaction proxy and coordinator
            transactionProxy = new TransactionProxyImpl();
            TransactionCoordinatorImpl transactionCoordinator =
                new TransactionCoordinatorImpl(systemProperties);

            // create the resource coordinator
            ResourceCoordinatorImpl resourceCoordinator =
                new ResourceCoordinatorImpl(systemProperties);

            // create the task handler and scheduler
            TaskHandler taskHandler = new TaskHandler(transactionCoordinator);
            MasterTaskScheduler scheduler =
                new MasterTaskScheduler(systemProperties, resourceCoordinator,
                                        taskHandler);

            // make sure to register the system as a user of the scheduler,
            // so that all of our events get grouped together
            scheduler.registerApplication(SystemKernelAppContext.CONTEXT);

            // finally, collect some of the system components to be shared
            // with services as they are created
            systemComponents.add(resourceCoordinator);
            systemComponents.add(scheduler);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Failed on Kernel boot");
            throw e;
        }

        logger.log(Level.CONFIG, "The Kernel is ready");
    }

    /**
     * TEST: This is using a fixed configuration. Eventually, it should be
     * loading its configuration, but for now, we have only fixed services
     * with known properties...when configuration details are decided, that
     * detail will be passed as parameters to this method.
     */
    void startupApplication(Properties properties) throws Exception {
        String appName = properties.getProperty("com.sun.sgs.appName");

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: configuring application", appName);

        // create the authentication manager used for this application
        ArrayList<IdentityAuthenticator> authenticators =
            new ArrayList<IdentityAuthenticator>();
        // TEST: this should get loaded from our configuration
        authenticators.add(new NamePasswordAuthenticator(properties));
        IdentityManagerImpl appIdentityManager =
            new IdentityManagerImpl(authenticators);

        // now that we have the app's authenticators, create a system
        // registry to use in setting up the services
        HashSet<Object> appSystemComponents =
            new HashSet<Object>(systemComponents);
        appSystemComponents.add(appIdentityManager);
        ComponentRegistryImpl systemRegistry =
            new ComponentRegistryImpl(appSystemComponents);

        // create the services and their associated managers...call out
        // the three standard services, because we need to get the
        // ordering right
        ArrayList<Service> serviceList = new ArrayList<Service>();
        HashSet<Object> managerSet = new HashSet<Object>();
        ComponentRegistryImpl managerComponents = new ComponentRegistryImpl();
        try {
            String dataServiceClass =
                properties.getProperty("com.sun.sgs.dataService",
                                       DEFAULT_DATA_SERVICE);
            String taskServiceClass =
                properties.getProperty("com.sun.sgs.taskService",
                                       DEFAULT_DATA_SERVICE);

            setupService(dataServiceClass, serviceList,
                         DEFAULT_DATA_MANAGER, managerSet, properties,
                         systemRegistry);
            setupService(taskServiceClass, serviceList,
                         DEFAULT_TASK_MANAGER, managerSet, properties,
                         systemRegistry);
            // FIXME: add the channel and app services when they're ready
            /*serviceList.add(setupService(Class.forName("app service name"),
              properties, systemRegistry));*/
            /*setupService(DEFAULT_CHANNEL_SERVICE, serviceList,
              DEFAULT_CHANNEL_MANAGER, managerSet, properties,
              systemRegistry);*/
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Could not setup service");
            throw e;
        }

        // NOTE: when we support external services, this is where they
        // get created

        // resolve the scheduler
        MasterTaskScheduler scheduler =
            systemRegistry.getComponent(MasterTaskScheduler.class);

        // register any profiling managers and fill in the manager registry
        for (Object manager : managerSet) {
            if (manager instanceof ProfilingManager)
                scheduler.registerProfilingManager((ProfilingManager)manager);
            managerComponents.addComponent(manager);
        }

        // finally, register the application with the master scheduler
        // and kick off a task to do the transactional configuration step,
        // where the services are configured...this in turn will actually
        // start the application running
        AppKernelAppContext appContext =
            new AppKernelAppContext(appName, managerComponents);
        try {
            scheduler.registerApplication(appContext);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Couldn't setup app scheduler");
            throw e;
        }
        ServiceConfigRunner configRunner =
            new ServiceConfigRunner(this, serviceList, transactionProxy,
                                    appName, properties);
        TransactionRunner transactionRunner =
            new TransactionRunner(configRunner);
        IdentityImpl appIdentity = new IdentityImpl("app:" + appName);
        TaskOwnerImpl owner = new TaskOwnerImpl(appIdentity, appContext);
        try {
            scheduler.scheduleTask(transactionRunner, owner);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Could not start configuration");
            throw e;
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
     * Called when an application has finished loading and has started to
     * run. This is typically called by <code>AppStartupRunner</code>
     * after it has started an application.
     *
     * @param context the application's kernel context
     */
    void applicationReady(AppKernelAppContext context) {
        applications.add(context);
        if (logger.isLoggable(Level.CONFIG))
                logger.log(Level.CONFIG, "{0}: application is ready", context);
    }

    /**
     * Main-line method that starts the <code>Kernel</code>. Note that right
     * now there is no management for the stack, so we accept on the
     * command-line the set of applications to run. Once we have management
     * and configration facilities, this command-line list will be removed.
     * <p>
     * Each arguent on the command-line is considered to be the name of an
     * application to run. For each application, the values of two properties
     * need to be supplied: <code>com.sun.sgs.{AppName}.appListenerClass</code>
     * and <code>com.sun.sgs.{AppName}.rootDir</code>. These will be provided
     * to all components and <code>Service</code>s as the properties
     * <code>com.sun.sgs.appListenerClass</code> (the fully-qualified name of
     * the <code>AppListener</code> implementation used by the app) and
     * <code>com.sun.sgs.rootDir</code> respectively. The name of the
     * application will also be provided as <code>com.sun.sgs.appName</code>.
     * <p>
     * Optionally, the properties <code>com.sun.sgs.channelService</code>,
     * <code>com.sun.sgs.dataService</code>, and
     * <code>com.sun.sgs.taskService</code> may be specified to use the
     * given service implementation for all applications. If any of these
     * properties is not specified, then the respective default service
     * is used.
     *
     * @param args the names of the applications to run
     *
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String [] args) throws Exception {
        // for now, we'll just use the system properties for everything
        Properties systemProperties = System.getProperties();

        // boot the kernel
        Kernel kernel = new Kernel(systemProperties);

        // setup and run each application
        for (int i = 0; i < args.length; i++) {
            Properties appProperties = new Properties(systemProperties);
            appProperties.setProperty("com.sun.sgs.appName", args[i]);

            // set the root property
            String rootDir =
                appProperties.getProperty("com.sun.sgs." + args[i] +
                                          ".rootDir");
            appProperties.setProperty("com.sun.sgs.rootDir", rootDir);

            // get the listener class
            String app =
                appProperties.getProperty("com.sun.sgs." + args[i] +
                                          ".appListenerClass");
            appProperties.setProperty("com.sun.sgs.appListenerClass", app);

            // set the database location
            appProperties.setProperty("com.sun.sgs.impl.service.data.store." +
                                      "DataStoreImpl.directory",
                                      rootDir + File.separator + "dsdb");

            // get the (optional) services
            if (! appProperties.containsKey("com.sun.sgs.channelService"))
                appProperties.setProperty("com.sun.sgs.channelService",
                                          DEFAULT_CHANNEL_SERVICE);
            if (! appProperties.containsKey("com.sun.sgs.dataService"))
                appProperties.setProperty("com.sun.sgs.dataService",
                                          DEFAULT_DATA_SERVICE);
            if (! appProperties.containsKey("com.sun.sgs.taskService"))
                appProperties.setProperty("com.sun.sgs.taskService",
                                          DEFAULT_TASK_SERVICE);
            
            // start the application
            kernel.startupApplication(appProperties);
        }
    }

}
