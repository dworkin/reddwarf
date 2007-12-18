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

import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCoordinator;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.profile.ProfileRegistrarImpl;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.impl.util.Version;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileListener;

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

    /**
     * A utility owner for all system tasks.
     */
    static final TaskOwnerImpl TASK_OWNER =
        new TaskOwnerImpl(SystemIdentity.IDENTITY,
                          SystemKernelAppContext.CONTEXT);

    // the collection of core system components
    private final HashSet<Object> systemComponents;

    // the proxy used by all transactional components
    private static final TransactionProxyImpl transactionProxy =
        new TransactionProxyImpl();
    
    // NOTE: this transaction coordinator should not really be static;
    // when we allow external transaction coordinators, we need to
    // create a factory to create not-static instances.  
    private static TransactionCoordinator transactionCoordinator;

    // the scheduler used by this kernel
    private final MasterTaskScheduler scheduler;

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
        "com.sun.sgs.impl.profile.listener.AggregateProfileListener";

    // the default authenticator
    private static final String DEFAULT_IDENTITY_AUTHENTICATOR =
        "com.sun.sgs.impl.auth.NullAuthenticator";

    // The last system registry used by this kernel, for testing only.
    // Note that each time an application is started, a new registry will
    // be created, which reassigned lastSystemRegistry.
    private ComponentRegistry lastSystemRegistry = null;

    // the last task owner created by this kernel, for testing only
    @SuppressWarnings("unused")
    private TaskOwner lastOwner = null;
    
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

            // create the task handler and scheduler
            TaskHandler taskHandler =
                new TaskHandler(
                  getTransactionCoordinator(systemProperties, profileCollector), 
                  profileCollector);
            
            scheduler =
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
     * Private factory for setting up our transaction coordinator. 
     * Note that we only expect to have more than one coordinator created
     * when we're running multiple stacks in a single VM, for testing.
     */
    private TransactionCoordinator getTransactionCoordinator(
                    Properties props, ProfileCollectorImpl profileCollector) 
    {
        if (transactionCoordinator == null) {
            transactionCoordinator = 
                new TransactionCoordinatorImpl(props, profileCollector);
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
                ProfileListener listener = (ProfileListener)obj;
                profileCollector.addListener(listener);
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
        
        // create the authentication coordinator used for this application
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

        IdentityCoordinator appIdentityCoordinator;
        try {
            appIdentityCoordinator = new IdentityCoordinatorImpl(authenticators);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE, e,
                                "Failed to created Identity Coordinator");
            throw e;
        }

        // now that we have the app's authenticators, create a system
        // registry to use in setting up the services
        HashSet<Object> appSystemComponents =
            new HashSet<Object>(systemComponents);
        appSystemComponents.add(appIdentityCoordinator);
        lastSystemRegistry =
            new ComponentRegistryImpl(appSystemComponents);

        // startup the service creation in a separate thread
        ServiceConfigRunner configRunner =
            new ServiceConfigRunner(this, lastSystemRegistry, profileRegistrar,
                                    transactionProxy, appName, properties);
        lastSystemRegistry.getComponent(ResourceCoordinator.class).
            startTask(configRunner, null);
    }

    /**
     * Shut down all applications in this kernel in reverse
     * order of how they were started, and also shutdown the
     * task scheduler.
     */
    void shutdown() {
        for (AppKernelAppContext ctx: applications) {
            ctx.shutdownServices();
        }
        scheduler.shutdown();
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
     * is called by <code>ServiceConfigRunner</code>.
     *
     * @param owner the TaskOwner containing the context
     * @param hasApplication <code>true</code> if the context is associated
     *                       with a running application, <code>false</code>
     *                       otherwise 
     */
    void contextReady(TaskOwner owner, boolean hasApplication) {
        AppKernelAppContext context = (AppKernelAppContext) owner.getContext();
        applications.add(context);
        if (logger.isLoggable(Level.INFO)) {
            if (hasApplication)
                logger.log(Level.INFO, "{0}: application is ready", context);
            else
                logger.log(Level.INFO, "{0}: non-application context is ready",
                           context);
        }
        lastOwner = owner;
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
