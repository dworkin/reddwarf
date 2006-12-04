
package com.sun.sgs.app;

import com.sun.sgs.impl.kernel.ContextResolver;


/**
 * Provides access to facilities available in the current application context.
 */
public final class AppContext {

    /**
     * Returns the <code>ChannelManager</code> for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object.
     *
     * @return	the <code>ChannelManager</code> for the current application
     */
    public static ChannelManager getChannelManager() {
        return ContextResolver.getChannelManager();
    }

    /**
     * Returns the <code>DataManager</code> for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the <code>DataManager</code> for the current application
     */
    public static DataManager getDataManager() {
        return ContextResolver.getDataManager();
    }

    /**
     * Returns the <code>TaskManager</code> for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the <code>TaskManager</code> for the current application
     */
    public static TaskManager getTaskManager() {
        return ContextResolver.getTaskManager();
    }

    /**
     * Returns a manager of the specified type for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object.
     *
     * @param	<T> the type of the manager
     * @param	type a class representing the type of the manager
     * @return	the manager of the specified type for the current application
     * @throws	ManagerNotFoundException if no manager is found for the
     *		specified type
     */
    public static <T> T getManager(Class<T> type) {
        return ContextResolver.getManager(type);    
    }

}
