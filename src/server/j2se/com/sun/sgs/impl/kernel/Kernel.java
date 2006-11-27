
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;

import com.sun.sgs.impl.app.profile.ProfilingManager;

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
    private static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(Kernel.class.getName()));

    // the collection of core system components
    private ComponentRegistryImpl systemComponents;

    // the proxy used by all transactional components
    private TransactionProxyImpl transactionProxy;

    // the set of applications that are running in this kernel
    private HashSet<KernelAppContextImpl> applications;

    // TEST: the name and source for the default application
    private static final String APP_NAME = "TestApplication";
    private static final String APP_CLASS = "TestApplication";

    // the default services that are used for channels, data, and tasks
    private static final String DEFAULT_CHANNEL_SERVICE =
        "com.sun.sgs.impl.service.channel.ChannelServiceImpl";
    private static final String DEFAULT_DATA_SERVICE =
        "com.sun.sgs.impl.service.data.DataServiceImpl";
    private static final String DEFAULT_TASK_SERVICE =
        "com.sun.sgs.impl.service.task.TaskServiceImpl";

    //the default managers that are used for channels, data, and tasks
    private static final String DEFAULT_CHANNEL_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingChannelManager";
    private static final String DEFAULT_DATA_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingDataManager";
    private static final String DEFAULT_TASK_MANAGER =
        "com.sun.sgs.impl.app.profile.ProfilingTaskManager";

    /**
     * FIXME: I'd like to promote this to the AppListener interface
     */
    public static final String LISTENER_BINDING = "appListener";

    /**
     * Creates an instance of <code>Kernel</code>. Once this is created
     * the code components of the system are running and ready. Creating
     * a <code>Kernel</code> will also result in initializing and starting
     * all associated applications and their associated services.
     *
     * @throws Exception if for any reason the kernel cannot be started
     */
    protected Kernel() throws Exception {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Booting the Kernel");

        // initialize our data structures
        systemComponents = new ComponentRegistryImpl();
        applications = new HashSet<KernelAppContextImpl>();

        // TEST: provide some hard-coded properties to all components
        Properties systemProperties = new Properties();

        // setup the system components
        // FIXME: decide which components we want to share
        try {
            transactionProxy = new TransactionProxyImpl();
            TransactionCoordinatorImpl transactionCoordinator =
                new TransactionCoordinatorImpl(systemProperties);
            ResourceCoordinatorImpl resourceCoordinator =
                new ResourceCoordinatorImpl(systemProperties);
            systemComponents.addComponent(resourceCoordinator);
            TaskHandler taskHandler = new TaskHandler(transactionCoordinator);
            MasterTaskScheduler scheduler =
                new MasterTaskScheduler(systemProperties, resourceCoordinator,
                                        taskHandler);
            systemComponents.addComponent(scheduler);
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
    void startupApplication() throws Exception {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: configuring application", APP_NAME);

        // collections for the managers and services
        ArrayList<Service> serviceList = new ArrayList<Service>();
        HashSet<Object> managerSet = new HashSet<Object>();
        ComponentRegistryImpl managerComponents = new ComponentRegistryImpl();

        // TEST: the following variables should be learned from the
        // application's configuration, but because we don't have a full
        // configuration system yet, they're hard-coded for now
        Properties properties = new Properties();
        AppListener appListener = null;
        try {
            appListener =
                (AppListener)(Class.forName(APP_CLASS).newInstance());
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.log(Level.SEVERE, "Couldn't resolve AppListener", e);
            throw e;
        }
        String appName = APP_NAME;

        properties.setProperty("com.sun.sgs.appName", appName);
        properties.setProperty("com.sun.sgs.impl.service.data.store." +
                               "DataStoreImpl.directory",
                               "/tmp/dsdb/" + appName);

        // create the services and their associated managers...call out
        // the three standard services, because we need to get the
        // ordering right
        // TEST: again, the services and their associated managers should
        // come from configuration, but for now they're hard-coded
        try {
            setupService(DEFAULT_DATA_SERVICE, serviceList,
                         DEFAULT_DATA_MANAGER, managerSet, properties);
            setupService(DEFAULT_TASK_SERVICE, serviceList,
                         DEFAULT_TASK_MANAGER, managerSet, properties);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.log(Level.SEVERE, "Couldn't setup service", e);
            throw e;
        }
        // FIXME: add the channel service support when it's ready
        /*setupService(DEFAULT_CHANNEL_SERVICE, serviceList,
          DEFAULT_CHANNEL_MANAGER, managerSet, properties);*/

        // NOTE: when we support external services, this is where they
        // get created

        // resolve the scheduler
        MasterTaskScheduler scheduler =
            systemComponents.getComponent(MasterTaskScheduler.class);

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
        KernelAppContextImpl appContext =
            new KernelAppContextImpl(appName, managerComponents);
        try {
            scheduler.registerApplication(appContext);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.log(Level.SEVERE, "Couldn't setup app scheduler", e);
            throw e;
        }
        ServiceConfigRunner configRunner =
            new ServiceConfigRunner(this, serviceList, transactionProxy,
                                    appListener, appName, properties);
        TransactionRunner transactionRunner =
            new TransactionRunner(configRunner);
        TaskOwnerImpl owner = new TaskOwnerImpl(appName, appContext);
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
                              Properties serviceProperties)
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
                                                     systemComponents));
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
    void applicationReady(KernelAppContextImpl context) {
        applications.add(context);
        if (logger.isLoggable(Level.CONFIG))
                logger.log(Level.CONFIG, "{0}: application is ready", context);
    }

    /**
     * Main-line method that starts the <code>Kernel</code>.
     */
    public static void main(String [] args) throws Exception {
        // TEST: for now, we just boot the kernel and setup the default app
        Kernel kernel = new Kernel();
        kernel.startupApplication();
    }

}
