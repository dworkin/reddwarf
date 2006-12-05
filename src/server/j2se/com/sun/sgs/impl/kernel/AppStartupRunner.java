
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.DataService;

import java.io.Serializable;
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
     * FIXME: I'd like to promote this to the AppListener interface
     */
    public static final String LISTENER_BINDING = "appListener";
    
    /**
     * A system binding which, if bound, indicates the app is running
     * (i.e., it has completed a startup but no corresponding shutdown).
     */
    public static final String APP_IS_RUNNING_BINDING =
	"com.sun.sgs.impl.kernel.isAppRunning";

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
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "{0}: starting application", appContext);

        // FIXME
        DataService dataService = appContext.getService(DataService.class);
        AppListener listener;
        try {
            // test to see if this name if the listener is already bound...
            listener =
        	dataService.getBinding(LISTENER_BINDING, AppListener.class);
        } catch (NameNotBoundException nnbe) {
            // ...if it's not, create and then bind the listener
            try {
                String appClass =
                    properties.getProperty("com.sun.sgs.appListenerClass");
                listener =
                    (AppListener)(Class.forName(appClass).newInstance());
                dataService.setBinding(LISTENER_BINDING, listener);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, e,
                	       "Couldn't instantiate application {0}: ",
                               appContext);
                throw e;
            }
        }
        
        // if the app has shut down cleanly, or was never run, we
        // need to send it the startingUp callback
        try {
            // Is the app already running?
            dataService.getServiceBinding(
        	    APP_IS_RUNNING_BINDING, ManagedObject.class);
	} catch (NameNotBoundException nnbe) {
	    // ...no, so we should tell it we're starting up
	    try {
	        dataService.setServiceBinding(
	        	APP_IS_RUNNING_BINDING, new AppRunningIndicator());

		listener.startingUp(properties);
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.logThrow(Level.SEVERE, e,
             	       	       "Couldn't startup application {0}: ",
                               appContext);
                throw e;
            }
	}

        // tell the kernel that this application has now started up
        kernel.applicationReady(appContext);
    }

    // A flag indicating whether the application is running
    static class AppRunningIndicator implements Serializable, ManagedObject {
        private static final long serialVersionUID = 1L;
	public AppRunningIndicator() { /* empty */ }
    }
}
