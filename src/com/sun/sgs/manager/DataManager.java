
package com.sun.sgs.manager;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;


/**
 * This manager provides access to the data-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class DataManager
{

    // the singleton instance of DataManager
    private static DataManager manager = null;

    /**
     * Creates an instance of <code>DataManager</code>. This class enforces
     * a singleton model, so only one instance of <code>DataManager</code>
     * may exist in the system.
     *
     * @throws IllegalStateException if an instance already exists
     */
    protected DataManager() {
        if (manager != null)
            throw new IllegalStateException("DataManager is already " +
                                            "initialized");

        manager = this;
    }

    /**
     * Returns the instance of <code>DataManager</code>.
     *
     * @return the instance of <code>DataManager</code>
     */
    public static DataManager getInstance() {
        return manager;
    }

    /**
     * Tells the manager to manage this object. The object has no name
     * associated with it.
     *
     * @param object the object to manage
     *
     * @return a reference to the newly managed object
     */
    public abstract <T extends ManagedObject>
            ManagedReference<T> manageObject(T object);

    /**
     * Tells the manager to manage this object. The object is associated
     * with the given name for future searching.
     *
     * @param object the object to manage
     * @param objectName the name of the object
     *
     * @return a reference to the newly managed object
     */
    public abstract <T extends ManagedObject>
            ManagedReference<T> manageObject(T object, String objectName);

    /**
     * Tries to find an already managed object based on that object's
     * name. If no object can be found with the given name then null
     * is returned.
     *
     * @param objectName the name of the object
     *
     * @return a reference to the object, or null
     */
    public abstract ManagedReference<? extends ManagedObject>
            findManagedObject(String objectName);

}
