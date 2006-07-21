
package com.sun.sgs.manager;

import com.sun.sgs.ManagedObject;
import com.sun.sgs.ManagedReference;

import com.sun.sgs.service.Transaction;


/**
 * This manager provides access to the data-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class DataManager
{

    /**
     * Returns an instance of <code>DataManager</code>.
     *
     * @return an instance of <code>DataManager</code>
     */
    public static DataManager getInstance() {
        // FIXME: return the instance
        return null;
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
