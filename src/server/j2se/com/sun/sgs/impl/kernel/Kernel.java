
package com.sun.sgs.impl.kernel;

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

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the core class for the server. It is the first class that is
 * created, and represents the kernel of the runtime. It is responsible
 * for creating and initializing all components of the system and the
 * applications configured to run in this system.
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

    // the default services
    private static final String DEFAULT_CHANNEL_SERVICE =
        "com.sun.sgs.impl.service.channel.ChannelServiceImpl";
    private static final String DEFAULT_CLIENT_SESSION_SERVICE =
        "com.sun.sgs.impl.service.session.ClientSessionServiceImpl";
    private static final String DEFAULT_DATA_SERVICE =
        "com.sun.sgs.impl.service.data.DataServiceImpl";
    private static final String DEFAULT_TASK_SERVICE =
        "com.sun.sgs.impl.service.task.TaskServiceImpl";

    // the default managers
    private static final String DEFAULT_CHANNEL_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingChannelManager";
    private static final String DEFAULT_DATA_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingDataManager";
    private static final String DEFAULT_TASK_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingTaskManager";

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
            scheduler.registerApplication(SystemKernelAppContext.CONTEXT,
                                          systemProperties);

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

        // create the services and their associated managers...call out
        // the standard services, first, because we need to get the
        // ordering constant and make sure that they're all present, and
        // then handle any external services
        ArrayList<Service> serviceList = new ArrayList<Service>();
        HashSet<Object> managerSet = new HashSet<Object>();
        ComponentRegistryImpl managerComponents = new ComponentRegistryImpl();
        try {
            String dataServiceClass =
                properties.getProperty(StandardProperties.DATA_SERVICE,
                                       DEFAULT_DATA_SERVICE);
            String dataManagerClass =
                properties.getProperty(StandardProperties.DATA_MANAGER,
                                       DEFAULT_DATA_MANAGER);
            String taskServiceClass =
                properties.getProperty(StandardProperties.TASK_SERVICE,
                                       DEFAULT_TASK_SERVICE);
            String taskManagerClass =
                properties.getProperty(StandardProperties.TASK_MANAGER,
                                       DEFAULT_TASK_MANAGER);
            String clientSessionServiceClass =
                properties.getProperty(StandardProperties.
                                       CLIENT_SESSION_SERVICE,
                                       DEFAULT_CLIENT_SESSION_SERVICE);
            String channelServiceClass =
                properties.getProperty(StandardProperties.CHANNEL_SERVICE,
                                       DEFAULT_CHANNEL_SERVICE);
            String channelManagerClass =
                properties.getProperty(StandardProperties.CHANNEL_MANAGER,
                                       DEFAULT_CHANNEL_MANAGER);

            setupService(dataServiceClass, serviceList,
                         dataManagerClass, managerSet, properties,
                         systemRegistry);
            setupService(taskServiceClass, serviceList,
                         taskManagerClass, managerSet, properties,
                         systemRegistry);
            serviceList.
                add(createService(Class.forName(clientSessionServiceClass),
                                  properties, systemRegistry));
            setupService(channelServiceClass, serviceList,
                         channelManagerClass, managerSet, properties,
                         systemRegistry);

            // now load any external services and their associated managers
            String externalServices =
                properties.getProperty(StandardProperties.SERVICES);
            String externalManagers =
                properties.getProperty(StandardProperties.MANAGERS);
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
                        serviceList.add(createService(serviceClass, properties,
                                                      systemRegistry));
                    }
                }
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e, "Could not setup service");
            throw e;
        }

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
     * application to run. For each application some properties are required.
     * For required and optional properties, and the correct command-line
     * formatting, see <code>StandardProperties</code>.
     * 
     * @param args the names of the applications to run
     *
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String [] args) throws Exception {
        // make sure we were given an application to run
        if (args.length < 1) {
            logger.log(Level.SEVERE, "No applications were provided: halting");
            System.out.println("Usage: AppName [AppName ...]");
            System.exit(0);
        }

        // start by loading from a config file (if one was provided), and
        // then merge in the system properties
        Properties systemProperties = new Properties();
        String propertiesFile =
            System.getProperty(StandardProperties.CONFIG_FILE);
        if (propertiesFile != null) {
            try {
                systemProperties.load(new FileInputStream(propertiesFile));
            } catch (IOException ioe) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, ioe, "Unable to load " +
                                    "properties file {0}: ", propertiesFile);
                throw ioe;
            } catch (IllegalArgumentException iae) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, iae, "Illegal data in " +
                                    "properties file {0}: ", propertiesFile);
                throw iae;
            }
        }
        systemProperties.putAll(System.getProperties());
        
        // boot the kernel
        Kernel kernel = new Kernel(systemProperties);

        // setup and run each application
        for (String appName : args) {
            Properties appProperties = new Properties(systemProperties);
            appProperties.setProperty(StandardProperties.APP_NAME, appName);
            
            // find all properties just for this app
            final String appPrefix = "com.sun.sgs." + appName + ".";
            final int len = appPrefix.length();
            for (Object propObj : systemProperties.keySet()) {
                String prop = (String)propObj;
                if (prop.startsWith(appPrefix))
                    appProperties.
                        setProperty("com.sun.sgs.app." + prop.substring(len),
                                    systemProperties.getProperty(prop));
            }

            // make sure that at least the required keys are present, and if
            // they are then start the application
            if (appProperties.
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
                // start the application
                kernel.startupApplication(appProperties);
            }
        }
    }

}
