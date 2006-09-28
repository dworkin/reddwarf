package com.sun.sgs.app;

/**
 * Provides access to facilities available in the current application context.
 */
public abstract class AppContext {

    /** Creates an instance of this class. */
    protected AppContext() { }

    /**
     * Returns the <code>ChannelManager</code> for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object.
     *
     * @return	the <code>ChannelManager</code> for the current application
     */
    public static ChannelManager getChannelManager() {
	throw new AssertionError("This method is not implemented");
    }

    /**
     * Returns the <code>DataManager</code> for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the <code>DataManager</code> for the current application
     */
    public static DataManager getDataManager() {
	throw new AssertionError("This method is not implemented");
    }

    /**
     * Returns the <code>TaskManager</code> for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the <code>TaskManager</code> for the current application
     */
    public static TaskManager getTaskManager() {
	throw new AssertionError("This method is not implemented");
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
	throw new AssertionError("This method is not implemented");
    }
}
