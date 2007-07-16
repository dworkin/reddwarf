/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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
