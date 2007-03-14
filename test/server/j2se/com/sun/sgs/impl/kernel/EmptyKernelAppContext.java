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


/**  A testing context of the right type for running tasks */
public class EmptyKernelAppContext extends AbstractKernelAppContext {

    /** Creates an instance with the given application name*/
    public EmptyKernelAppContext(String appName) {
        super(appName);
    }

    /**
     * {@inheritDoc}
     */
    ChannelManager getChannelManager() {
        throw new ManagerNotFoundException("this context is empty");
    }

    /**
     * {@inheritDoc}
     */
    DataManager getDataManager() {
        throw new ManagerNotFoundException("this context is empty");
    }

    /**
     * {@inheritDoc}
     */
    TaskManager getTaskManager() {
        throw new ManagerNotFoundException("this context is empty");
    }

    /**
     * {@inheritDoc}
     */
    <T> T getManager(Class<T> type) {
        throw new ManagerNotFoundException("this context is empty");
    }

    /**
     * {@inheritDoc}
     */
    void setServices(ComponentRegistry serviceComponents) {}

    /**
     * {@inheritDoc}
     */
    <T extends Service> T getService(Class<T> type) {
        throw new MissingResourceException("this context is empty",
                                           type.getName(), null);
    }

}
