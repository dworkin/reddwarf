
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;

import com.sun.sgs.auth.IdentityAuthenticator;

import com.sun.sgs.impl.app.profile.ProfilingManager;

import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.auth.IdentityManagerImpl;
import com.sun.sgs.impl.auth.NamePasswordAuthenticator;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionRunner;

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
        if (logger.isLoggable(Level.CONFIG))
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
                logger.log(Level.SEVERE, "Failed on Kernel boot", e);
            throw e;
        }

        if (logger.isLoggable(Level.CONFIG))
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
        HashSet<IdentityAuthenticator> authenticators =
            new HashSet<IdentityAuthenticator>();
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
            // FIXME: add the channel service support when it's ready
            /*setupService(DEFAULT_CHANNEL_SERVICE, serviceList,
              DEFAULT_CHANNEL_MANAGER, managerSet, properties,
              systemRegistry);*/
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.log(Level.SEVERE, "Couldn't setup service", e);
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
                logger.log(Level.SEVERE, "Couldn't setup app scheduler", e);
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
                logger.log(Level.SEVERE, "Couldn't start configuration", e);
            throw e;
        }
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
        // make sure we can resolve the two classes
        Class<?> serviceClass = Class.forName(serviceName);
        Class<?> managerClass = Class.forName(managerName);

        // find the appropriate constructors for both service and manager,
        // which is easy for the service...
        Constructor<?> serviceConstructor =
            serviceClass.getConstructor(Properties.class,
                                        ComponentRegistry.class);
        // ...but requires more work for the manager, because its constructor
        // is probably taking a super-type of the service
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

        // create both instances, putting them into their collections
        Service service =
            (Service)(serviceConstructor.newInstance(serviceProperties,
                                                     systemRegistry));
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
     * com.sun.sgs.{AppName}.appListenerClass
     * com.sun.sgs.{AppName}.rootDir
     *
     * com.sun.sgs.channelService
     * com.sun.sgs.dataService
     * com.sun.sgs.taskService 
     *
     * @param args the applications to run
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

            // get the listener class
            String app =
                appProperties.getProperty("com.sun.sgs." + args[i] +
                                          ".appListenerClass");
            appProperties.setProperty("com.sun.sgs.appListenerClass", app);

            // get the database location
            String rootDir =
                appProperties.getProperty("com.sun.sgs." + args[i] +
                                          ".rootDir");
            appProperties.setProperty("com.sun.sgs.impl.service.data.store." +
                                      "DataStoreImpl.directory",
                                      rootDir + "/dsdb");

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
