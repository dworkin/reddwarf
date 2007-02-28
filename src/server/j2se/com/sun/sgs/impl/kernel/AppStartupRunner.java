/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.DataService;

import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>KernelRunnable</code> is one of two runnables
 * used when an application is starting up. This runnable is resposible for
 * calling the application's listener, which actually starts the application
 * running, and then reporting the successful startup to the kernel. This
 * runnable is typically scheduled by <code>ServiceConfigRunner</code>.
 * <p>
 * This runnable must be run in a transactional context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class AppStartupRunner implements KernelRunnable {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(AppStartupRunner.class.getName()));

    // the context in which this will run
    private final AppKernelAppContext appContext;

    // the properties for the application
    private final Properties properties;

    // the kernel that is responsible for the starting application
    private final Kernel kernel;

    /**
     * Creates an instance of <code>AppStartupRunner</code>.
     *
     * @param appContext the context in which the application will run
     * @param properties the <code>Properties</code> to provide to the
     *                   application on startup
     * @param kernel the <code>Kernel</code> that manages the application
     */
    AppStartupRunner(AppKernelAppContext appContext, Properties properties,
                     Kernel kernel) {
        this.appContext = appContext;
        this.properties = properties;
        this.kernel = kernel;
    }

    /**
     * Starts the application and reports success to the kernel.
     *
     * @throws Exception if anything fails in starting the application
     */
    public void run() throws Exception {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "{0}: starting application", appContext);

        DataService dataService = appContext.getService(DataService.class);
        try {
            // test to see if this name if the listener is already bound...
            dataService.getServiceBinding(StandardProperties.APP_LISTENER,
                                          AppListener.class);
        } catch (NameNotBoundException nnbe) {
            // ...if it's not, create and then bind the listener
            try {
                String appClass =
                    properties.getProperty(StandardProperties.APP_LISTENER);
                AppListener listener =
                    (AppListener)(Class.forName(appClass).newInstance());
                dataService.setServiceBinding(StandardProperties.APP_LISTENER,
                                              listener);

                // since we created the listener, we're the first one to
                // start the app, so we also need to start it up
                listener.initialize(properties);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, e,
                                    "{0}: could not start application",
                                    appContext);
                throw e;
            }
        }

        // tell the kernel that this application has now started up
        kernel.applicationReady(appContext);
    }

}
