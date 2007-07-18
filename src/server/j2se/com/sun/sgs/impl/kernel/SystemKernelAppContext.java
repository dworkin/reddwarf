/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.Service;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class represents the context of the system. It contains no managers
 * nor services, since neither are available in the system context.
 */
final class SystemKernelAppContext extends AbstractKernelAppContext
{

    // logger for this class
    private static final LoggerWrapper logger =
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
