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
     *
     * @throws MissingResourceException if the <code>ChannelManager</code>,
     *                                  <code>DataManager</code>, or
     *                                  <code>TaskManager</code> is missing
     *                                  from the provided components
     */
    AppKernelAppContext (String applicationName,
                         ComponentRegistry managerComponents) {
        super(applicationName);

        this.managerComponents = managerComponents;

        // pre-fetch the three standard managers
        channelManager = managerComponents.getComponent(ChannelManager.class);
        dataManager = managerComponents.getComponent(DataManager.class);
        taskManager = managerComponents.getComponent(TaskManager.class);
    }

    /**
     * {@inheritDoc}
     */
    ChannelManager getChannelManager() {
        return channelManager;
    }

    /**
     * {@inheritDoc}
     */
    DataManager getDataManager() {
        return dataManager;
    }

    /**
     * {@inheritDoc}
     */
    TaskManager getTaskManager() {
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
