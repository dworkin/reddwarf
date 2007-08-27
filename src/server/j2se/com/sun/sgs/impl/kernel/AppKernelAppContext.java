/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.Service;

import java.util.MissingResourceException;


/**
 * This is the implementation of <code>KernelAppContext</code> used by
 * the kernel to manage the context of a single application. It knows
 * the name of an application, its available manages, and its backing
 * services.
 */
class AppKernelAppContext extends AbstractKernelAppContext {

    // the managers available in this context
    private final ComponentRegistry managerComponents;

    // the services used in this context
    private ComponentRegistry serviceComponents = null;

    // the three standard managers, which are cached since they are used
    // extremely frequently
    private final ChannelManager channelManager;
    private final DataManager dataManager;
    private final TaskManager taskManager;

    /**
     * Creates an instance of <code>AppKernelAppContext</code>.
     *
     * @param applicationName the name of the application represented by
     *                        this context
     * @param managerComponents the managers available in this context
     */
    AppKernelAppContext (String applicationName,
                         ComponentRegistry managerComponents) {
        super(applicationName);

        this.managerComponents = managerComponents;

        // pre-fetch the three standard managers...if any of them isn't
        // present then we're running without an application and with a
        // sub-set of services, so just that manager to null

        ChannelManager cm;
        try {
            cm = managerComponents.getComponent(ChannelManager.class);
        } catch (MissingResourceException mre) {
            cm = null;
        }
        channelManager = cm;

        DataManager dm;
        try {
            dm = managerComponents.getComponent(DataManager.class);
        } catch (MissingResourceException mre) {
            dm = null;
        }
        dataManager = dm;
        
        TaskManager tm;
        try {
            tm = managerComponents.getComponent(TaskManager.class);
        } catch (MissingResourceException mre) {
            tm = null;
        }
        taskManager = tm;
    }

    /**
     * {@inheritDoc}
     */
    ChannelManager getChannelManager() {
        if (channelManager == null)
            throw new ManagerNotFoundException("this application is running " +
                                               "without a ChannelManager");
        return channelManager;
    }

    /**
     * {@inheritDoc}
     */
    DataManager getDataManager() {
        if (dataManager == null)
            throw new ManagerNotFoundException("this application is running " +
                                               "without a DataManager");
        return dataManager;
    }

    /**
     * {@inheritDoc}
     */
    TaskManager getTaskManager() {
        if (taskManager == null)
            throw new ManagerNotFoundException("this application is running " +
                                               "without a TaskManager");
        return taskManager;
    }

    /**
     * {@inheritDoc}
     */
    <T> T getManager(Class<T> type) {
        try {
            return managerComponents.getComponent(type);
        } catch (MissingResourceException mre) {
            throw new ManagerNotFoundException("couldn't find manager: " +
                                               type.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    void setServices(ComponentRegistry serviceComponents) {
        if (this.serviceComponents != null)
            throw new IllegalStateException("Services have already been set");
        this.serviceComponents = serviceComponents;
    }

    /**
     * {@inheritDoc}
     */
    <T extends Service> T getService(Class<T> type) {
        return serviceComponents.getComponent(type);
    }

}
