
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.Service;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class represents the context of the system. It contains no managers
 * nor services, since neither are available in the system context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class SystemKernelAppContext extends AbstractKernelAppContext
{

    // logger for this class
    private static LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SystemKernelAppContext.
                                           class.getName()));

    /**
     * The single instance of the system context.
     */
    static final SystemKernelAppContext CONTEXT =
        new SystemKernelAppContext();

    /**
     * Creates an instance of <code>SystemKernelAppContext</code>.
     */
    private SystemKernelAppContext() {
        super("system");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no managers
     *                               available in the system context
     */
    ChannelManager getChannelManager() {
        logger.log(Level.SEVERE, "Trying to resolve ChannelManager from " +
                   "within the system context");
        throw new IllegalStateException("System context has no managers");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no managers
     *                               available in the system context
     */
    DataManager getDataManager() {
        logger.log(Level.SEVERE, "Trying to resolve DataManager from " +
                   "within the system context");
        throw new IllegalStateException("System context has no managers");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no managers
     *                               available in the system context
     */
    TaskManager getTaskManager() {
        logger.log(Level.SEVERE, "Trying to resolve TaskManager from " +
                   "within the system context");
        throw new IllegalStateException("System context has no managers");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no managers
     *                               available in the system context
     */
    <T> T getManager(Class<T> type) {
        logger.log(Level.SEVERE, "Trying to resolve a manager from " +
                   "within the system context");
        throw new IllegalStateException("System context has no managers");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no services
     *                               available in the system context
     */
    void setServices(ComponentRegistry serviceComponents) {
        logger.log(Level.SEVERE, "Trying to set the services for the " +
                   "system context");
        throw new IllegalStateException("System context has no services");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException always, since there are no services
     *                               available in the system context
     */
    <T extends Service> T getService(Class<T> type) {
        logger.log(Level.SEVERE, "Trying to resolve a service from " +
                   "within the system context");
        throw new IllegalStateException("System context has no services");
    }

}
