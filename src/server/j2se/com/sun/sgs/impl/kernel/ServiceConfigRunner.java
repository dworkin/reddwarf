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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;

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
 */
class ServiceConfigRunner implements KernelRunnable {

    // the base type of this class
    private static final String BASE_TYPE =
        ServiceConfigRunner.class.getName();

    // logger for this class
    private final static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(BASE_TYPE));

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

    // the registry that will be used to provide services to the context
    private ComponentRegistryImpl serviceComponents = null;

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
     * {@inheritDoc}
     */
    public String getBaseTaskType() {
        return BASE_TYPE;
    }

    /**
     * Configures each of the <code>Service</code>s in order, in preparation
     * for starting up an application. At completion, this schedules an
     * <code>AppStartupRunner</code> to finish application startup.
     *
     * @throws Exception if any failure occurs during service configuration
     */
    public void run() throws Exception {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: starting service config", appName);

        AppKernelAppContext appContext =
            (AppKernelAppContext)(proxy.getCurrentOwner().getContext());

        // if we haven't run before then setup the registry that will be
        // used for services, otherwise we were aborted in the past so
        // just clear the registry
        if (serviceComponents == null) {
            serviceComponents = new ComponentRegistryImpl();
            appContext.setServices(serviceComponents);
        } else {
            serviceComponents.clearComponents();
        }

        // initialize the services in the correct order, adding them to the
        // registry as we go
        for (Service service : services) {
            try {
                service.configure(serviceComponents, proxy);
            } catch (Exception e) {
                if (logger.isLoggable(Level.CONFIG))
                    logger.logThrow(Level.CONFIG, e, "{1}: failed to " +
                                    "configure service {2}",
                                    appName, service.getName());
                throw e;
            }
            serviceComponents.addComponent(service);
        }

        // At this point the services are now configured, so the final step
        // is to try booting the application after setting the services
        // available in our context. Boot the app is done by running a
        // special KernelRunnable in a new transaction
        AppStartupRunner startupRunner =
            new AppStartupRunner(appContext, appProperties, kernel);
        UnboundedTransactionRunner unboundedTransactionRunner =
            new UnboundedTransactionRunner(startupRunner);
        try {
            appContext.getService(TaskService.class).
                scheduleNonDurableTask(unboundedTransactionRunner);
        } catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "{0}: failed to schedule " +
                                "app startup task", appName);
            throw e;
        }

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: finished service config runner",
                       appName);
    }

}
