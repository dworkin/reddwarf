
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;

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
        // NOTE: for the multi-stack case, this needs to check that the
        // app's boot method hasn't already been called...this can be done
        // simply by having a flag in the data store that we set when we
        // call the boot method

        // get the listener, and call it
        AppListener listener = null;
        try {
            listener =
                appContext.getDataManager().getBinding(Kernel.LISTENER_BINDING,
                                                       AppListener.class);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.log(Level.SEVERE, "{0}: has no listener", e,
                           appContext);
            throw e;
        }

        listener.startingUp(properties);

        // tell the kernel that this application has now started up
        kernel.applicationReady(appContext);
    }

}
