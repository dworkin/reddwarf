/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.impl.kernel.logging.TransactionAwareLogManager;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;

import com.sun.sgs.impl.profile.ProfileRegistrarImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Version;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileListener;

import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * This is the core class for the server. It is the first class that is
 * created, and represents the kernel of the runtime. It is responsible for
 * creating and initializing all components of the system and the applications
 * configured to run in this system.
 * <p>
 * By default, the minimal amount of profiling which is used internally by the
 * system is enabled. To enable more profiling, the kernel property
 * {@value com.sun.sgs.impl.kernel.Kernel#PROFILE_PROPERTY} must be set to a
 * valid level for {@link 
 * com.sun.sgs.profile.ProfileCollector#setDefaultProfileLevel(ProfileLevel)}.
 * By default, no profile listeners are enabled. Set the
 * {@value com.sun.sgs.impl.kernel.Kernel#PROFILE_LISTENERS} property with a
 * colon-separated list of fully-qualified class names, each of which
 * implements {@link ProfileListener}.
 */
class Kernel {

    // logger for this class
    private static final LoggerWrapper logger =
	    new LoggerWrapper(Logger.getLogger(Kernel.class.getName()));

    // the property for setting profiling levels
    public static final String PROFILE_PROPERTY =
	    "com.sun.sgs.impl.kernel.profile.level";
    // the property for setting the profile listeners
    public static final String PROFILE_LISTENERS =
	    "com.sun.sgs.impl.kernel.profile.listeners";

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

    // the properties used to start the application
    private final Properties appProperties;

    // the schedulers used for transactional and non-transactional tasks
    private final TransactionSchedulerImpl transactionScheduler;
    private final TaskSchedulerImpl taskScheduler;

    // the application that is running in this kernel
    private KernelContext application;

    // The system registry which contains all shared system components
    private final ComponentRegistryImpl systemRegistry;

    // collector and reporter of profile information
    // note that this object should never escape this kernel, as it contains
    // methods that should only be called by objects created by this kernel
    private final ProfileCollectorImpl profileCollector;
    // the registry object for creating profiling content objects
    private final ProfileRegistrar profileRegistrar;

    /**
     * Creates an instance of <code>Kernel</code>. Once this is created the
     * code components of the system are running and ready. Creating a
     * <code>Kernel</code> will also result in initializing and starting the
     * application and its associated services.
     * 
     * @param appProperties application properties
     * @throws Exception if for any reason the kernel cannot be started
     */
    protected Kernel(Properties appProperties) throws Exception {
	logger.log(Level.CONFIG, "Booting the Kernel");

	this.appProperties = appProperties;

	try {
	    // See if we're doing any profiling.
	    String level =
		    appProperties.getProperty(PROFILE_PROPERTY,
			    ProfileLevel.MIN.name());
	    ProfileLevel profileLevel;
	    try {
		profileLevel = ProfileLevel.valueOf(level.toUpperCase());
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

	    profileCollector =
		    new ProfileCollectorImpl(profileLevel, appProperties,
			    systemRegistry);
	    profileRegistrar = new ProfileRegistrarImpl(profileCollector);

	    // create the authenticators and identity coordinator
	    ArrayList<IdentityAuthenticator> authenticators =
		    new ArrayList<IdentityAuthenticator>();
	    String[] authenticatorClassNames =
		    appProperties.getProperty(
			    StandardProperties.AUTHENTICATORS,
			    DEFAULT_IDENTITY_AUTHENTICATOR).split(":");

	    for (String authenticatorClassName : authenticatorClassNames)
		authenticators.add(getAuthenticator(authenticatorClassName,
			appProperties));
	    IdentityCoordinator identityCoordinator =
		    new IdentityCoordinatorImpl(authenticators);

	    // initialize the transaction coordinator
	    TransactionCoordinator transactionCoordinator =
		    new TransactionCoordinatorImpl(appProperties,
			    profileCollector);

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
	    AccessCoordinatorImpl accessCoordinator =
		    new AccessCoordinatorImpl(appProperties, proxy,
			    profileCollector);

	    // create the schedulers, and provide an empty context in case
	    // any profiling components try to do transactional work
	    transactionScheduler =
		    new TransactionSchedulerImpl(appProperties,
			    transactionCoordinator, profileCollector,
			    accessCoordinator);
	    taskScheduler =
		    new TaskSchedulerImpl(appProperties, profileCollector);

	    KernelContext ctx = new StartupKernelContext("Kernel");
	    transactionScheduler.setContext(ctx);
	    taskScheduler.setContext(ctx);

	    // collect the shared system components into a registry
	    systemRegistry.addComponent(accessCoordinator);
	    systemRegistry.addComponent(transactionScheduler);
	    systemRegistry.addComponent(taskScheduler);
	    systemRegistry.addComponent(identityCoordinator);
	    systemRegistry.addComponent(profileRegistrar);

	    // create the profiling listeners. It is important to not
	    // do this until we've finished adding components to the
	    // system registry, as some listeners use those components.
	    loadProfileListeners(profileCollector);

	    if (logger.isLoggable(Level.INFO))
		logger.log(Level.INFO, "The Kernel is ready, version: {0}",
			Version.getVersion());

	    // the core system is ready, so start up the application
	    createAndStartApplication();

	} catch (Exception e) {
	    if (logger.isLoggable(Level.SEVERE))
		logger.logThrow(Level.SEVERE, e, "Failed on Kernel boot");
	    // shut down whatever we've started
	    shutdown();
	    throw e;
	}
    }

    /**
     * Private helper routine that loads all of the requested listeners for
     * profiling data.
     */
    private void loadProfileListeners(ProfileCollector profileCollector) {
	String listenerList = appProperties.getProperty(PROFILE_LISTENERS);

	if (listenerList != null) {
	    for (String listenerClassName : listenerList.split(":")) {
		try {
		    profileCollector.addListener(listenerClassName);
		} catch (InvocationTargetException e) {
		    // Strip off exceptions found via reflection
		    if (logger.isLoggable(Level.WARNING))
			logger
				.logThrow(
					Level.WARNING,
					e.getCause(),
					"Failed to load ProfileListener {0} ... "
						+ "it will not be available for profiling",
					listenerClassName);

		} catch (Exception e) {
		    if (logger.isLoggable(Level.WARNING))
			logger
				.logThrow(
					Level.WARNING,
					e,
					"Failed to load ProfileListener {0} ... "
						+ "it will not be available for profiling",
					listenerClassName);
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
     * Helper that starts an application. This method configures the
     * <code>Service</code>s associated with the application and then
     * starts the application.
     * 
     * @throws Exception if there is any error in startup
     */
    private void createAndStartApplication() throws Exception {
	String appName =
		appProperties.getProperty(StandardProperties.APP_NAME);

	if (logger.isLoggable(Level.CONFIG))
	    logger.log(Level.CONFIG, "{0}: starting application", appName);

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
	    throws Exception {
	if (logger.isLoggable(Level.CONFIG))
	    logger.log(Level.CONFIG, "{0}: starting services", appName);

	// create and install a temporary context to use during startup
	application = new StartupKernelContext(appName);
	transactionScheduler.setContext(application);
	taskScheduler.setContext(application);
	ContextResolver.setTaskState(application, owner);

	try {
	    fetchServices((StartupKernelContext) application);
	} catch (Exception e) {
	    if (logger.isLoggable(Level.SEVERE))
		logger.logThrow(Level.SEVERE, e, "{0}: failed to create "
			+ "services", appName);
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
	    if (logger.isLoggable(Level.SEVERE))
		logger.logThrow(Level.SEVERE, e,
			"{0}: failed when notifying "
				+ "services that application is ready",
			appName);
	    throw e;
	}
    }

    /**
     * Private helper that creates the services and their associated managers,
     * taking care to call out the standard services first, because we need to
     * get the ordering constant and make sure that they're all present.
     */
    private void fetchServices(StartupKernelContext startupContext)
	    throws Exception {
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
		    logger.logThrow(Level.SEVERE, iae, "Invalid final "
			    + "service name: {0}", finalService);
		throw iae;
	    }

	    // make sure we're not running with an application
	    if (!appProperties.getProperty(StandardProperties.APP_LISTENER)
		    .equals(StandardProperties.APP_LISTENER_NONE))
		throw new IllegalArgumentException("Cannot specify an app "
			+ "listener and a final " + "service");
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
	setupService(dataServiceClass, dataManagerClass, startupContext);

	// load the watch-dog service, which has no associated manager

	if (StandardService.WatchdogService.ordinal() > finalStandardService
		.ordinal())
	    return;

	String watchdogServiceClass =
		appProperties.getProperty(
			StandardProperties.WATCHDOG_SERVICE,
			DEFAULT_WATCHDOG_SERVICE);
	setupServiceNoManager(watchdogServiceClass, startupContext);

	// load the node mapping service, which has no associated manager

	if (StandardService.NodeMappingService.ordinal() > finalStandardService
		.ordinal())
	    return;

	String nodemapServiceClass =
		appProperties.getProperty(
			StandardProperties.NODE_MAPPING_SERVICE,
			DEFAULT_NODE_MAPPING_SERVICE);
	setupServiceNoManager(nodemapServiceClass, startupContext);

	// load the task service

	if (StandardService.TaskService.ordinal() > finalStandardService
		.ordinal())
	    return;

	String taskServiceClass =
		appProperties.getProperty(StandardProperties.TASK_SERVICE,
			DEFAULT_TASK_SERVICE);
	String taskManagerClass =
		appProperties.getProperty(StandardProperties.TASK_MANAGER,
			DEFAULT_TASK_MANAGER);
	setupService(taskServiceClass, taskManagerClass, startupContext);

	// load the client session service, which has no associated manager

	if (StandardService.ClientSessionService.ordinal() > finalStandardService
		.ordinal())
	    return;

	String clientSessionServiceClass =
		appProperties.getProperty(
			StandardProperties.CLIENT_SESSION_SERVICE,
			DEFAULT_CLIENT_SESSION_SERVICE);
	setupServiceNoManager(clientSessionServiceClass, startupContext);

	// load the channel service

	if (StandardService.ChannelService.ordinal() > finalStandardService
		.ordinal())
	    return;

	String channelServiceClass =
		appProperties.getProperty(StandardProperties.CHANNEL_SERVICE,
			DEFAULT_CHANNEL_SERVICE);
	String channelManagerClass =
		appProperties.getProperty(StandardProperties.CHANNEL_MANAGER,
			DEFAULT_CHANNEL_MANAGER);
	setupService(channelServiceClass, channelManagerClass, startupContext);

	// finally, load any external services and their associated managers
	if ((externalServices != null) && (externalManagers != null)) {
	    String[] serviceClassNames = externalServices.split(":", -1);
	    String[] managerClassNames = externalManagers.split(":", -1);
	    if (serviceClassNames.length != managerClassNames.length) {
		if (logger.isLoggable(Level.SEVERE))
		    logger.log(Level.SEVERE, "External service count "
			    + "({0}) does not match manager count ({1}).",
			    serviceClassNames.length,
			    managerClassNames.length);
		throw new IllegalArgumentException("Mis-matched service "
			+ "and manager count");
	    }

	    for (int i = 0; i < serviceClassNames.length; i++) {
		if (!managerClassNames[i].equals(""))
		    setupService(serviceClassNames[i], managerClassNames[i],
			    startupContext);
		else
		    setupServiceNoManager(serviceClassNames[i],
			    startupContext);
	    }
	}
    }

    /**
     * Sets up a service with no manager based on fully qualified class name.
     */
    private void setupServiceNoManager(String className,
	    StartupKernelContext startupContext) throws Exception {
	Class<?> serviceClass = Class.forName(className);
	Service service = createService(serviceClass);
	startupContext.addService(service);
    }

    /**
     * Creates a service and its associated manager based on fully qualified
     * class names.
     */
    private void setupService(String serviceName, String managerName,
	    StartupKernelContext startupContext) throws Exception {
	// get the service class and instance
	Class<?> serviceClass = Class.forName(serviceName);
	Service service = createService(serviceClass);

	// resolve the class and the constructor, checking for constructors
	// by type since they likely take a super-type of Service
	Class<?> managerClass = Class.forName(managerName);
	Constructor<?>[] constructors = managerClass.getConstructors();
	Constructor<?> managerConstructor = null;
	for (int i = 0; i < constructors.length; i++) {
	    Class<?>[] types = constructors[i].getParameterTypes();
	    if (types.length == 1) {
		if (types[0].isAssignableFrom(serviceClass)) {
		    managerConstructor = constructors[i];
		    break;
		}
	    }
	}

	// if we didn't find a matching manager constructor, it's an error
	if (managerConstructor == null)
	    throw new NoSuchMethodException("Could not find a constructor "
		    + "that accepted the Service");

	// create the manager and put it and the service in the collections
	// and the temporary startup context
	Object manager = managerConstructor.newInstance(service);
	startupContext.addService(service);
	startupContext.addManager(manager);
    }

    /**
     * Private helper that creates an instance of a <code>service</code>
     * with no manager, based on fully qualified class names.
     */
    private Service createService(Class<?> serviceClass) throws Exception {
	Constructor<?> serviceConstructor;

	// If the service is the watchdog, then attach a special
	// object that enables call-back for node shutdown.
	// Otherwise, construct the service as usual.
	if (serviceClass.equals(appProperties
		.getProperty(StandardProperties.WATCHDOG_SERVICE))) {

	    serviceConstructor =
		    serviceClass.getConstructor(Properties.class,
			    ComponentRegistry.class, TransactionProxy.class,
			    KernelShutdownController.class);

	    // return a new instance using the four-argument constructor
	    KernelShutdownController ctrl =
		    KernelShutdownController.getSingleton(this);
	    return (Service) (serviceConstructor.newInstance(appProperties,
		    systemRegistry, proxy, ctrl));

	} else {

	    // find the appropriate constructor
	    serviceConstructor =
		    serviceClass.getConstructor(Properties.class,
			    ComponentRegistry.class, TransactionProxy.class);

	    // return a new instance
	    return (Service) (serviceConstructor.newInstance(appProperties,
		    systemRegistry, proxy));
	}
    }

    public void shutdownRequest() {

    }

    /** Start the application, throwing an exception if there is a problem. */
    private void startApplication(String appName, Identity owner)
	    throws Exception {
	// at this point the services are ready, so the final step
	// is to initialize the application by running a special
	// KernelRunnable in an unbounded transaction, unless we're
	// running without an application
	if (!appProperties.getProperty(StandardProperties.APP_LISTENER)
		.equals(StandardProperties.APP_LISTENER_NONE)) {
	    try {
		if (logger.isLoggable(Level.CONFIG))
		    logger.log(Level.CONFIG, "{0}: starting application",
			    appName);

		transactionScheduler.runUnboundedTask(new AppStartupRunner(
			appProperties), owner);

		logger.log(Level.INFO, "{0}: application is ready",
			application);
	    } catch (Exception e) {
		if (logger.isLoggable(Level.CONFIG))
		    logger.logThrow(Level.CONFIG, e, "{0}: failed to "
			    + "start application", appName);
		throw e;
	    }
	} else {
	    // we're running without an application, so we're finished
	    logger.log(Level.INFO, "{0}: non-application context is ready",
		    application);
	}
    }

    /**
     * Shut down all services (in reverse order) and the schedulers.
     */
    void shutdown() {
	if (application != null)
	    application.shutdownServices();
	if (profileCollector != null)
	    profileCollector.shutdown();
	// The schedulers must be shut down last.
	if (transactionScheduler != null)
	    transactionScheduler.shutdown();
	if (taskScheduler != null)
	    taskScheduler.shutdown();
    }

    /**
     * Creates a new identity authenticator.
     */
    private IdentityAuthenticator getAuthenticator(String className,
	    Properties properties) throws Exception {
	Class<?> authenticatorClass = Class.forName(className);
	Constructor<?> authenticatorConstructor =
		authenticatorClass.getConstructor(Properties.class);
	return (IdentityAuthenticator) (authenticatorConstructor
		.newInstance(properties));
    }

    /**
     * Helper method for loading properties files with backing properties.
     */
    private static Properties getProperties(String filename,
	    Properties backingProperties) throws Exception {
	FileInputStream inputStream = null;
	try {
	    Properties properties;
	    if (backingProperties == null)
		properties = new Properties();
	    else
		properties = new Properties(backingProperties);
	    inputStream = new FileInputStream(filename);
	    properties.load(inputStream);

	    // Expand properties as needed.
	    String value =
		    properties.getProperty(StandardProperties.NODE_TYPE);
	    if (value == null) {
		// Default is single node
		value = StandardProperties.NodeType.singleNode.name();
	    }

	    StandardProperties.NodeType type;
	    // Throws IllegalArgumentException if not one of the enum types
	    // but let's improve the error message
	    try {
		type = StandardProperties.NodeType.valueOf(value);
	    } catch (IllegalArgumentException e) {
		throw new IllegalArgumentException("Illegal value for " +
			StandardProperties.NODE_TYPE);
	    }

	    switch (type) {
		case singleNode:
		    break; // do nothing, this is the default
		case coreServerNode:
		    // Don't start an application
		    properties.setProperty(StandardProperties.APP_LISTENER,
			    StandardProperties.APP_LISTENER_NONE);
		    // Only run basic services
		    properties.setProperty(StandardProperties.FINAL_SERVICE,
			    "NodeMappingService");
		    // Start servers for services
		    properties.setProperty(StandardProperties.SERVER_START,
			    "true");
		    // Start the network server for the data store
		    properties
			    .setProperty(
				    DataServiceImpl.DATA_STORE_CLASS_PROPERTY,
				    "com.sun.sgs.impl.service.data.store.net.DataStoreClient");
		    break;
		case appNode:
		    // Don't start the servers
		    properties.setProperty(StandardProperties.SERVER_START,
			    "false");
		    break;
	    }

	    return properties;
	} catch (IOException ioe) {
	    if (logger.isLoggable(Level.SEVERE))
		logger.logThrow(Level.SEVERE, ioe, "Unable to load "
			+ "properties file {0}: ", filename);
	    throw ioe;
	} catch (IllegalArgumentException iae) {
	    if (logger.isLoggable(Level.SEVERE))
		logger.logThrow(Level.SEVERE, iae, "Illegal data in "
			+ "properties file {0}: ", filename);
	    throw iae;
	} finally {
	    if (inputStream != null)
		try {
		    inputStream.close();
		} catch (IOException e) {
		    if (logger.isLoggable(Level.CONFIG))
			logger.logThrow(Level.CONFIG, e, "failed to close "
				+ "property file {0}", filename);
		}
	}
    }

    /**
     * Check for obvious errors in the properties file, logging and throwing
     * an {@code IllegalArgumentException} if there is a problem.
     */
    private static void checkProperties(Properties appProperties,
	    String configFile) {
	String appName =
		appProperties.getProperty(StandardProperties.APP_NAME);

	// make sure that at least the required keys are present, and if
	// they are then start the application
	if (appName == null) {
	    logger.log(Level.SEVERE, "Missing required property " +
		    StandardProperties.APP_NAME + " from config file " +
		    configFile);
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

	if (appProperties.getProperty(StandardProperties.APP_LISTENER) == null) {
	    logger.log(Level.SEVERE, "Missing required property " +
		    StandardProperties.APP_LISTENER + "for application: " +
		    appName);
	    throw new IllegalArgumentException("Missing required property " +
		    StandardProperties.APP_LISTENER + "for application: " +
		    appName);
	}

	if (!StandardProperties.APP_LISTENER_NONE.equals(appProperties
		.getProperty(StandardProperties.APP_LISTENER)) &&
		appProperties.getProperty(StandardProperties.APP_PORT) == null) {
	    logger.log(Level.SEVERE, "Missing required property " +
		    StandardProperties.APP_PORT + " for application: " +
		    appName);
	    throw new IllegalArgumentException("Missing required property " +
		    StandardProperties.APP_PORT + " for application: " +
		    appName);
	}
    }

    /**
     * This runnable calls the application's <code>initialize</code> method,
     * if it hasn't been called before, to start the application for the first
     * time. This runnable must be called in the context of an unbounded
     * transaction.
     */
    private static final class AppStartupRunner extends
	    AbstractKernelRunnable {
	// the properties for the application
	private final Properties properties;

	/** Creates an instance of <code>AppStartupRunner</code>. */
	AppStartupRunner(Properties properties) {
	    this.properties = properties;
	}

	/** Starts the application, throwing an exception on failure. */
	public void run() throws Exception {
	    DataService dataService =
		    Kernel.proxy.getService(DataService.class);
	    try {
		// test to see if this name if the listener is already
		// bound...
		dataService
			.getServiceBinding(StandardProperties.APP_LISTENER);
	    } catch (NameNotBoundException nnbe) {
		// ...if it's not, create and then bind the listener
		String appClass =
			properties
				.getProperty(StandardProperties.APP_LISTENER);
		AppListener listener =
			(AppListener) (Class.forName(appClass).newInstance());
		dataService.setServiceBinding(
			StandardProperties.APP_LISTENER, listener);

		// since we created the listener, we're the first one to
		// start the app, so we also need to start it up
		listener.initialize(properties);
	    }
	}
    }

    /**
     * Main-line method that starts the {@code Kernel}. Each kernel instance
     * runs a single application.
     * <p>
     * The argument on the command-line is a {@code Properties} file for the
     * application. Some properties are required to be specified in that file.
     * See {@code StandardProperties} for the required and optional
     * properties.
     * <p>
     * The order of precedence for properties is as follows. If a value is
     * provided for a given property key by the application's configuration,
     * then that value takes precedence over any others. If no value is
     * provided by the application's configuration, then the system property
     * value, if specified (provided on the command-line using a "-D" flag) is
     * used. If no value is specified for a given property in either of these
     * places, then a default is used or an <code>Exception</code> is thrown
     * (depending on whether a default value is available).
     * 
     * @param args filename for <code>Properties</code> file associated with
     * the application to run
     * @throws Exception if there is any problem starting the system
     */
    public static void main(String[] args) throws Exception {
	// make sure we were given an application to run
	if (args.length != 1) {
	    logger.log(Level.SEVERE, "No application was provided: halting");
	    System.out.println("Usage: AppPropertyFile ");
	    System.exit(0);
	}

	// Get the properties, merging properties given on the command line
	// with the first argument, which is the application config file.
	// The config file properties have precedence.
	Properties appProperties =
		getProperties(args[0], System.getProperties());

	// check the standard properties
	checkProperties(appProperties, args[0]);

	// boot the kernel
	new Kernel(appProperties);
    }

}
