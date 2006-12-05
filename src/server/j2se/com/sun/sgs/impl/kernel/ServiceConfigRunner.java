package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import java.util.List;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>KernelRunnable</code> is one of two runnables
 * used when an application is starting up. This runnable is resposible for
 * configuring all of the application's <code>Service</code>s, and then
 * scheduling a <code>AppStartupRunner</code> to start the application.
 * <p>
 * This runnable must be run in a transactional context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class ServiceConfigRunner implements KernelRunnable {

    // logger for this class
    private final static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ServiceConfigRunner.
                                           class.getName()));

    // the reference back to the kernel
    private final Kernel kernel;

    // the services that this runnable will configure
    private final List<Service> services;

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
     * @param services the <code>Service</code>s to configure, in order
     *                 of how they will be configured
     * @param proxy the proxy used to access the current transaction
     * @param appName the name of the application being started
     * @param appProperties the <code>Properties</code> provided to the
     *                      application on startup
     */
    ServiceConfigRunner(Kernel kernel, List<Service> services,
                        TransactionProxy proxy, String appName,
                        Properties appProperties) {
        this.kernel = kernel;
        this.services = services;
        this.proxy = proxy;
        this.appName = appName;
        this.appProperties = appProperties;
    }

    /**
     * Configures each of the <code>Service</code>s in order, in preparation
     * for starting up an application. At completion, this schedules an
     * <code>AppStartupRunner</code> to finish application startup.
     *
     * @throws Exception if any failure occurs during service configuration
     */
    public void run() throws Exception {
	logger.log(Level.FINER, "{0}: starting service config", appName);

        // initialize the services in the correct order, adding them to the
        // registry as we go
        ComponentRegistryImpl serviceComponents = new ComponentRegistryImpl();
        for (Service service : services) {
            try {
                service.configure(serviceComponents, proxy);
            } catch (Exception e) {
        	logger.logThrow(Level.SEVERE, e,
        		"{0}: failed to configure service {1}",
        		appName, service.getName());
                throw e;
            }
            serviceComponents.addComponent(service);
        }

        // At this point the services are now configured, so the final step
        // is to try booting the application after setting the services
        // available in our context. Boot the app is done by running a
        // special KernelRunnable in a new transaction
        AppKernelAppContext appContext =
            (AppKernelAppContext)(proxy.getCurrentOwner().getContext());
        AppStartupRunner startupRunner =
            new AppStartupRunner(appContext, appProperties, kernel);
        TransactionRunner transactionRunner =
            new TransactionRunner(startupRunner);
        try {
            appContext.setServices(serviceComponents);
            appContext.getService(TaskService.class).
                scheduleNonDurableTask(transactionRunner);
        } catch (Exception e) {
            logger.logThrow(Level.SEVERE, e,
        	    "{0}: failed to schedule app startup task", appName);
            throw e;
        }

        logger.log(Level.FINER, "{0}: finished service config runner", appName);
    }

}
