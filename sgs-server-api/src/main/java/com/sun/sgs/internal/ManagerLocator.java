/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.internal;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.ManagerNotFoundException;

/**
 * Defines the boundary API for accessing managers for use by the current
 * application.  Any implementation of the Project Darkstar API should
 * provide a single implementation of this interface that is to be
 * used by the {@link InternalContext} to locate managers in the system.
 * 
 * @see com.sun.sgs.app.AppContext AppContext
 * @see InternalContext#setManagerLocator InternalContext.setManagerLocator
 */
public interface ManagerLocator {
    
    /**
     * Returns the {@code ChannelManager} for use by the current
     * application.  
     *
     * @return	the {@code ChannelManager} for the current application
     * @throws	ManagerNotFoundException if the {@code ChannelManager} cannot
     *          be located
     */
    ChannelManager getChannelManager();

    /**
     * Returns the {@code DataManager} for use by the current application.
     *
     * @return	the {@code DataManager} for the current application
     * @throws	ManagerNotFoundException if the {@code DataManager} cannot
     *          be located
     */
    DataManager getDataManager();

    /**
     * Returns the {@code TaskManager} for use by the current application.
     *
     * @return	the {@code TaskManager} for the current application
     * @throws	ManagerNotFoundException if the {@code TaskManager} cannot
     *          be located
     */
    TaskManager getTaskManager();
    
    /**
     * Returns a manager of the specified type for use by the current
     * application.
     *
     * @param	<T> the type of the manager
     * @param	type a class representing the type of the manager
     * @return	the manager of the specified type for the current application
     * @throws	ManagerNotFoundException if no manager is found for the
     *		specified type
     */
    <T> T getManager(Class<T> type);
}
