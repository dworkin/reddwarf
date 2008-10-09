/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.app;

import java.util.Map;
import java.util.HashMap;


/**
 * Provides access to facilities available in the current application context.
 * This class should not be instantiated.
 */
public final class AppContext {
    
    // the current locator for this context
    private static ManagerLocator managerLocator;
    
    // directly cache the DataManager since it is used frequently
    private static DataManager dataManager;
    
    // directly cache the TaskManager since it is used frequently
    private static TaskManager taskManager;
    
    // directly cache the ChannelManager since it is used frequently
    private static ChannelManager channelManager;
    
    // cache the remaining managers in a map
    private static volatile Map<Class<?>, Object> managerMap =
            new HashMap<Class<?>, Object>();
    
    
    /** This class should not be instantiated. */
    private AppContext() { }

    /**
     * Returns the {@code ChannelManager} for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object. <p>
     * 
     * Note that this method will
     * always return the same object in between calls to
     * {@link #setManagerLocator setManagerLocator}.
     *
     * @return	the {@code ChannelManager} for the current application
     * @throws	ManagerNotFoundException if the {@code ChannelManager} cannot
     *          be located
     */
    public static ChannelManager getChannelManager() {
        if(channelManager == null)
            throw new ManagerNotFoundException("ChannelManager cannot be located");

        return channelManager;
    }

    /**
     * Returns the {@code DataManager} for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object. <p>
     * 
     * Note that this method will
     * always return the same object in between calls to
     * {@link #setManagerLocator setManagerLocator}.
     *
     * @return	the {@code DataManager} for the current application
     * @throws	ManagerNotFoundException if the {@code DataManager} cannot
     *          be located
     */
    public static DataManager getDataManager() {
        if(dataManager == null)
            throw new ManagerNotFoundException("DataManager cannot be located");

        return dataManager;
    }

    /**
     * Returns the {@code TaskManager} for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object. <p>
     * 
     * Note that this method will
     * always return the same object in between calls to
     * {@link #setManagerLocator setManagerLocator}.
     *
     * @return	the {@code TaskManager} for the current application
     * @throws	ManagerNotFoundException if the {@code TaskManager} cannot
     *          be located
     */
    public static TaskManager getTaskManager() {
        if(taskManager == null)
            throw new ManagerNotFoundException("TaskManager cannot be located");

        return taskManager;
    }

    /**
     * Returns a manager of the specified type for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object. <p>
     * 
     * Note that for any calls with the same type argument, this method will
     * always return the same object in between calls to
     * {@link #setManagerLocator setManagerLocator}.
     *
     * @param	<T> the type of the manager
     * @param	type a class representing the type of the manager
     * @return	the manager of the specified type for the current application
     * @throws	ManagerNotFoundException if no manager is found for the
     *		specified type
     */
    @SuppressWarnings("unchecked")
    public static <T> T getManager(Class<T> type) {
        if(managerMap == null)
            throw new ManagerNotFoundException("manager of type "+
                                               type.getName()+
                                               " cannot be located");
        
        if(!managerMap.containsKey(type)) {
            synchronized(AppContext.class) {
                if(!managerMap.containsKey(type))
                    managerMap.put(type, managerLocator.getManager(type));
            }
        }

        return (T)managerMap.get(type);
    }
    
    /**
     * Sets the {@code ManagerLocator} which is used to retrieve
     * managers for the application.  <p>
     * 
     * Invoking this method will immediately retrieve and cache values for
     * the {@code DataManager}, {@code TaskManager}, and {@code ChannelManager}
     * from the given {@code ManagerLocator}.  If one of these cannot be
     * located, it will be set to null. <p>
     * 
     * In most situations, this method
     * should only be called once throughout the life of the application.
     * By default, if this method is called a second time, it will
     * throw a {@link AppContextException}.  However, if the
     * {@code com.sun.sgs.app.AppContext.resetAllowed} system property is
     * set to {@code true}, calling this method multiple times is allowed.
     * 
     * @param managerLocator the {@code ManagerLocator} that the 
     *        {@code AppContext} should use to retrieve managers
     * @throws AppContextException if this method has already been called
     *         once <em>and</em> the
     *         {@code com.sun.sgs.app.AppContext.resetAllowed} property is
     *         not set to {@code true}.
     */
    public static synchronized void 
            setManagerLocator(ManagerLocator managerLocator) {
        if(managerLocator == null)
            throw new NullPointerException("managerLocator cannot be null");
        if(AppContext.managerLocator != null &&
                !"true".equals(
                System.getProperty("com.sun.sgs.app.AppContext.resetAllowed"))) {
            throw new AppContextException("multiple invocations of "+
                                          "setManagerLocator not allowed");
        }
        
        // set the locator and cache the standard managers
        AppContext.managerLocator = managerLocator;
        try {
            dataManager = managerLocator.getManager(DataManager.class);
        } catch(ManagerNotFoundException m) {
            dataManager = null;
        }
        
        try {
            channelManager = managerLocator.getManager(ChannelManager.class);
        } catch(ManagerNotFoundException m) {
            channelManager = null;
        }
        
        try {
            taskManager = managerLocator.getManager(TaskManager.class);
        } catch(ManagerNotFoundException m) {
            taskManager = null;
        }
        
        // reset the cache for the other managers
        managerMap = new HashMap<Class<?>, Object>();
    }

}
